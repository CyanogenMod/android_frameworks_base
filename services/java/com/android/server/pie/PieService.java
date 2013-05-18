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
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.pie.IPieActivationListener;
import android.service.pie.IPieHostCallback;
import android.service.pie.IPieService;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.View;
import android.view.WindowManager;

import com.android.internal.util.pie.PiePosition;
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

    private final Context mContext;
    private final InputManagerService mInputManager;
    private final WindowManagerService mWindowManager;

    private HandlerThread mHandlerThread = new HandlerThread("Pie");

    // Lock for thread, handler, mInputFilter, activations and listener related variables
    private final Object mLock = new Object();
    private Handler mHandler;
    private PieInputFilter mInputFilter;

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

        public boolean notifyPieActivation(int touchX, int touchY, PiePosition position) {
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
                mWindowManager.resetStatusBarVisibilityMask();
                mInputFilter.unlockFilter();
                mActive = false;
                synchronized (mLock) {
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
    // end of lock guarded variables

    private DisplayObserver mDisplayObserver;

    // called by system server
    public PieService(Context context, WindowManagerService windowManager, InputManagerService inputManager) {
        mContext = context;
        mInputManager = inputManager;
        mWindowManager = windowManager;
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
        // check if anyone registered during startup
        mHandler.obtainMessage(MSG_UPDATE_POSITIONS, mGlobalPositions, 0).sendToTarget();
        updateMonitoring();
    }

    private void enforceMonitoringLocked() {
        if (DEBUG) {
            Slog.d(TAG, "Attempting to start monitoring input events ...");
        }
        if (mInputFilter == null) {
            mInputFilter = new PieInputFilter(mContext, mHandler);
            mInputManager.registerSecondaryInputFilter(mInputFilter);
        }
        mDisplayObserver.observe();
    }

    private void shutdownMonitoringLocked() {
        if (DEBUG) {
            Slog.d(TAG, "Shutting down monitoring input events ...");
        }
        mDisplayObserver.unobserve();
        if (mInputFilter != null) {
            mInputManager.unregisterSecondaryInputFilter(mInputFilter);
            mInputFilter = null;
        }
    }

    private void updateMonitoring() {
        synchronized(mLock) {
            if (!mIsMonitoring && mGlobalPositions != 0) {
                enforceMonitoringLocked();
            } else if (mIsMonitoring && mGlobalPositions == 0) {
                shutdownMonitoringLocked();
            }
            mIsMonitoring = mGlobalPositions != 0;
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
        synchronized(mLock) {
            record = findListenerRecordLocked(listener.asBinder());
            if (record == null) {
                record = new PieActivationListenerRecord(listener);
                mPieActivationListener.add(record);
            }
        }
        return record;
    }

    // called through Binder
    public void updatePieActivationListener(IBinder listener, int positionFlags) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized(mLock) {
            PieActivationListenerRecord record = findListenerRecordLocked(listener);
            if (record == null) {
                Slog.w(TAG, "Unknown listener on update listener. Register first?");
                throw new IllegalStateException("listener not registered");
            }
            record.updatePositions(positionFlags);
            updatePositionsLocked();
            if (mActiveRecord == null && mHandler != null) {
                mHandler.obtainMessage(MSG_UPDATE_POSITIONS, mGlobalPositions, 0).sendToTarget();
            }
        }
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
        synchronized(mLock) {
            mPieActivationListener.remove(record);
            updatePositionsLocked();
            // check if the record was the active one
            if (record == mActiveRecord) {
                mHandler.obtainMessage(MSG_PIE_DEACTIVATION, mGlobalPositions, 0).sendToTarget();
            }
        }
        updateMonitoring();
    }

    // called by handler thread
    private boolean propagateActivation(int touchX, int touchY, PiePosition position) {
        if (mActiveRecord != null) {
            Slog.w(TAG, "Handing activition while another activition is still in progress");
        }
        if (!mWindowManager.updateStatusBarVisibilityMask(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)) {
            return false;
        }
        synchronized(mLock) {
            PieActivationListenerRecord target = null;
            for (PieActivationListenerRecord record : mPieActivationListener) {
                if (record.eligibleForActivation(position.FLAG)) {
                    target = record;
                    break;
                }
            }
            // NOTE: We can do this here because the #onPieActivation() is a oneway
            // Binder call. This means we do not block with holding the mListenerLock!!!
            // If this ever change, this needs to be adjusted and if you don't know what
            // this means, you should probably not mess around with this code, anyway.
            if (target != null && target.notifyPieActivation(touchX, touchY, position)) {
                mActiveRecord = target;
            }
        }
        if (mActiveRecord != null) {
            mWindowManager.reevaluateStatusBarVisibility();
        } else {
            mWindowManager.resetStatusBarVisibilityMask();
        }
        return mActiveRecord != null;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            // let's log all exceptions we do not know about.
            if (!(e instanceof IllegalArgumentException || e instanceof IllegalStateException)) {
                Slog.e(TAG, "PieService crashed: ", e);
            }
            throw e;
        }
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
        synchronized(mLock) {
            pw.println("  mIsMonitoring=" + mIsMonitoring);
            pw.println("  mInputFilter=" + mInputFilter);
            if (mInputFilter != null) {
                mInputFilter.dump(pw, "    ");
            }
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
                    if (DEBUG) {
                        Slog.d(TAG, "Activating pie on " + m.obj.toString());
                    }
                    // Since input filter runs asynchronously to us, double activation may happen
                    // theoretically. Take the safe route here.
                    removeMessages(MSG_PIE_ACTIVATION);
                    if (propagateActivation(m.arg1, m.arg2, (PiePosition) m.obj)) {
                        // switch off all positions for the time of activation
                        updatePositionsHandler(0);
                    }
                    break;
                case MSG_PIE_DEACTIVATION:
                    if (DEBUG) {
                        Slog.d(TAG, "Deactivating pie with positions 0x" + Integer.toHexString(m.arg1));
                    }
                    // switch back on the positions we need
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
            synchronized (mLock) {
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
                synchronized (mLock) {
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
