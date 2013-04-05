/*
 * Copyright (C) 2013 The CyanogenMod Project (Jens Doll)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.server.pie;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.service.pie.IPieActivationListener;
import android.service.pie.IPieHostCallback;
import android.service.pie.IPieService;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputDevice;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.android.server.input.InputManagerService;
import com.android.server.wm.WindowManagerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A system service to track and handle pie activations gestures. This service interacts with
 * the {@link InputManagerService} to do all the dirty work for pie controls:
 * <li>Installing an input filter and listen for pie activation gestures</li>
 * <li>Removing those gestures from the input stream</li>
 * <li>Transferring touch focus to the pie controls when shown</li>
 */
public class PieService extends IPieService.Stub {
    public static final String TAG = "PieService";
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_INPUT = false;

    public static final int MSG_PIE_ACTIVATION = 32023;
    public static final int MSG_PIE_DEACTIVATION = 32024;
    public static final int MSG_UPDATE_POSITIONS = 32025;

    /**
     * Sibling class to the application side {@code Position} class in the SystemUI package.
     * Keep the indices in sync with {@code packages/app/SystemUI}!
     */
    public static enum Position {
        LEFT(0), BOTTOM(1), RIGHT(2), TOP(3);

        Position(int index) {
            INDEX = index;
            FLAG = (0x01<<index);
        }

        public final int INDEX;
        public final int FLAG;
    }

    private final Context mContext;
    private final InputManagerService mInputManager;
    private final WindowManagerService mWindowManager;

    // Lock for thread, handler, mInputFilter related variables
    private final Object mHandlerLock = new Object();
    private HandlerThread mHandlerThread = new HandlerThread("Pie");
    private Handler mHandler;
    private PieInputFilter mInputFilter;

    // Lock for activation, listener related variables
    private final Object mListenerLock = new Object();
    private int mGlobalPositions = 0;
    private boolean mIsMonitoring = false;

    private final class PieActivationListenerRecord extends IPieHostCallback.Stub implements DeathRecipient {
        private boolean mActive;

        public PieActivationListenerRecord(IPieActivationListener listener) {
            this.listener = listener;
            this.positions = 0;
        }

        public void binderDied() {
            removeListenerRecord(this);
        }

        public void updatePositions(int positions) {
            this.positions = positions;
        }

        public boolean eligibleForActivation(int positionFlag) {
            return (positions & positionFlag) != 0;
        }

        public boolean notifyPieActivation(int touchX, int touchY, Position position) {
            if ((positions & position.FLAG) != 0) {
                try {
                    listener.onPieActivation(touchX, touchY, position.INDEX, 0);
                    mActive = true;
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to notify process, assuming it died.", e);
                    binderDied();
                }
            }
            return mActive;
        }

        // called through Binder
        public boolean gainTouchFocus(IBinder windowToken) {
            if (DEBUG) {
                Slog.d(TAG, "Gain touch focus for " + windowToken);
            }
            if (mActive) {
                return mInputFilter.unlockFilter();
            }
            return false;
        }

        // called through Binder
        public void restoreListenerState() throws RemoteException {
            if (DEBUG) {
                Slog.d(TAG, "Restore listener state");
            }
            if (mActive) {
                mActive = false;
                synchronized (mListenerLock) {
                    mActiveRecord = null;
                    mHandler.obtainMessage(MSG_PIE_DEACTIVATION, mGlobalPositions, 0).sendToTarget();
                }
            }
        }

        public void dump(PrintWriter pw, String prefix) {
            pw.print(prefix);
            pw.print("mPositions=0x" + Integer.toHexString(positions));
            pw.println(" mActive=" + mActive);
            pw.print(prefix);
            pw.println("mBinder=" + listener);
        }

        public int positions;
        public final IPieActivationListener listener;
    }
    private final List<PieActivationListenerRecord> mPieActivationListener =
            new ArrayList<PieActivationListenerRecord>();
    private PieActivationListenerRecord mActiveRecord = null;

    private DisplayObserver mDisplayObserver;

    // called by system server
    public PieService(Context context, WindowManagerService windowManager, InputManagerService inputManager) {
        mContext = context;
        mWindowManager = windowManager;
        mInputManager = inputManager;
    }

    // called by system server
    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "Starting the pie gesture capture thread ...");

        mHandlerThread.start();
        mHandler = new H(mHandlerThread.getLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(
                        android.os.Process.THREAD_PRIORITY_FOREGROUND);
                android.os.Process.setCanSelfBackground(false);
            }
        });
        mDisplayObserver = new DisplayObserver(mContext, mHandler);
    }

    private void enforceMonitoring() {
        if (DEBUG) {
            Slog.d(TAG, "Attempting to start monitoring input events ...");
        }
        synchronized(mHandlerLock) {
            if (mInputFilter == null) {
                mInputFilter = new PieInputFilter(mContext, mHandler);
                mInputManager.registerSecondaryInputFilter(mInputFilter);
            }
            mDisplayObserver.observe();
        }
    }

    private void shutdownMonitoring() {
        if (DEBUG) {
            Slog.d(TAG, "Shutting down monitoring input events ...");
        }
        synchronized(mHandlerLock) {
            mDisplayObserver.unobserve();
            if (mInputFilter != null) {
                mInputManager.unregisterSecondaryInputFilter(mInputFilter);
                mInputFilter = null;
            }
        }
    }

    private void updateMonitoring() {
        boolean needStart = false, needStop = false;

        synchronized(mListenerLock) {
            needStart = !mIsMonitoring && mGlobalPositions != 0;
            needStop = mIsMonitoring && mGlobalPositions == 0;
            mIsMonitoring = mGlobalPositions != 0;
        }

        // do this outside of the mListenerLock
        if (needStart) {
            enforceMonitoring();
        } else if (needStop) {
            shutdownMonitoring();
        }
    }

    // called through Binder
    public IPieHostCallback registerPieActivationListener(IPieActivationListener listener) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.INJECT_EVENTS)
                != PackageManager.PERMISSION_GRANTED) {
            Slog.w(TAG, "Permission Denial: can't register from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return null;
        }

        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        PieActivationListenerRecord record = null;
        synchronized(mListenerLock) {
            record = findListenerRecordLocked(listener.asBinder());
            if (record == null) {
                record = new PieActivationListenerRecord(listener);
                mPieActivationListener.add(record);
            }
        }

        // do this outside of the mListenerLock
        updateMonitoring();

        return record;
    }

    // called through Binder
    public void updatePieActivationListener(IBinder listener, int positionFlags) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized(mListenerLock) {
            PieActivationListenerRecord record = findListenerRecordLocked(listener);
            if (record == null) {
                Slog.w(TAG, "Unknown listener on update listener. Register first?");
                throw new IllegalStateException("listener not registered");
            }
            record.updatePositions(positionFlags);
            updatePositionsLocked();
            if (mActiveRecord == null) {
                mHandler.obtainMessage(MSG_UPDATE_POSITIONS, mGlobalPositions, 0).sendToTarget();
            }
        }

        // do this outside of the mListenerLock
        updateMonitoring();
    }

    private PieActivationListenerRecord findListenerRecordLocked(IBinder listener) {
        for (PieActivationListenerRecord record : mPieActivationListener) {
            if (record.listener.asBinder().equals(listener)) {
                return record;
            }
        }
        return null;
    }

    private void updatePositionsLocked() {
        mGlobalPositions = 0;
        for (PieActivationListenerRecord temp : mPieActivationListener) {
            mGlobalPositions |= temp.positions;
        }
    }

    private void removeListenerRecord(PieActivationListenerRecord record) {
        synchronized(mListenerLock) {
            // check if the record was the active one
            if (record == mActiveRecord) {
                mHandler.obtainMessage(MSG_PIE_DEACTIVATION, mGlobalPositions, 0).sendToTarget();
            }
            updatePositionsLocked();
            mPieActivationListener.remove(record);
        }

        // do this outside of the mListenerLock
        updateMonitoring();
    }

    // called by handler thread
    private boolean propagateActivation(int touchX, int touchY, Position position) {
        if (mActiveRecord != null) {
            Slog.w(TAG, "Handing activition while another activition is still in progress");
        }
        synchronized(mListenerLock) {
            for (PieActivationListenerRecord record : mPieActivationListener) {
                if (record.eligibleForActivation(position.FLAG)) {
                    // NOTE: We can do this here because the #onPieActivation() is a oneway
                    // Binder call. This means we do not block with holding the mListenerLock!!!
                    // If this ever change, this needs to be adjusted and if you don't know what
                    // this means, you should probably not mess around with this code, anyway.
                    if (record.notifyPieActivation(touchX, touchY, position)) {
                        mActiveRecord = record;
                        break;
                    }
                }
            }
        }
        return mActiveRecord != null;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump PieService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("PIE SERVICE (dumpsys pieservice)\n");
        synchronized(mHandlerLock) {
            pw.println("  mInputFilter=" + mInputFilter);
            if (mInputFilter != null) {
                mInputFilter.dump(pw, "    ");
            }
        }
        synchronized(mListenerLock) {
            pw.println("  mGlobalPositions=0x" + Integer.toHexString(mGlobalPositions));
            int i = 0;
            for (PieActivationListenerRecord record : mPieActivationListener) {
                if (record == mActiveRecord) break;
                i++;
            }
            pw.println("  mActiveRecord=" + (mActiveRecord != null ? ("#" + i) : "null"));
            i = 0;
            for (PieActivationListenerRecord record : mPieActivationListener) {
                pw.println("  Listener #" + i + ":");
                record.dump(pw, "    ");
                i++;
            }
        }
    }

    private final class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_PIE_ACTIVATION:
                    mHandler.removeMessages(MSG_PIE_ACTIVATION);
                    if (DEBUG) {
                        Slog.d(TAG, "Activating pie on " + m.obj.toString());
                    }
                    if (propagateActivation(m.arg1, m.arg2, (Position) m.obj)) {
                        // switch off all positions for the time of activation
                        updatePositionsHandler(0);
                    }
                    break;
                case MSG_PIE_DEACTIVATION:
                    if (DEBUG) {
                        Slog.d(TAG, "Deactivating pie with positions 0x" + Integer.toHexString(m.arg1));
                    }
                    // switch back on
                    updatePositionsHandler(m.arg1);
                    break;
                case MSG_UPDATE_POSITIONS:
                    if (DEBUG) {
                        Slog.d(TAG, "Updating positions 0x" + Integer.toHexString(m.arg1));
                    }
                    updatePositionsHandler(m.arg1);
            }
        }

        private void updatePositionsHandler(int positions) {
            synchronized (mHandlerLock) {
                if (mInputFilter != null) {
                    mInputFilter.updatePositions(positions);
                }
            }
        }
    }

    private final class DisplayObserver implements DisplayListener {
        private final Handler mHandler;
        private final DisplayManager mDisplayManager;

        private final Display mDefaultDisplay;
        private final DisplayInfo mDefaultDisplayInfo = new DisplayInfo();

        public DisplayObserver(Context context, Handler handler) {
            mHandler = handler;
            mDisplayManager = (DisplayManager) context.getSystemService(
                    Context.DISPLAY_SERVICE);
            final WindowManager windowManager = (WindowManager) context.getSystemService(
                    Context.WINDOW_SERVICE);

            mDefaultDisplay = windowManager.getDefaultDisplay();
            updateDisplayInfo();
        }

        private void updateDisplayInfo() {
            if (DEBUG) {
                Slog.d(TAG, "Updating display information ...");
            }
            if (mDefaultDisplay.getDisplayInfo(mDefaultDisplayInfo)) {
                synchronized (mHandlerLock) {
                    if (mInputFilter != null) {
                        mInputFilter.updateDisplay(mDefaultDisplay, mDefaultDisplayInfo);
                    }
                }
            } else {
                Slog.e(TAG, "Default display is not valid.");
            }
        }

        public void observe() {
            mDisplayManager.registerDisplayListener(this, mHandler);
            updateDisplayInfo();
        }

        public void unobserve() {
            mDisplayManager.unregisterDisplayListener(this);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            /* do noting */
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            /* do nothing */
        }

        @Override
        public void onDisplayChanged(int displayId) {
            updateDisplayInfo();
        }
    }
}
