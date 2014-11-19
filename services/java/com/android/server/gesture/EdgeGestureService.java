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
package com.android.server.gesture;

import static com.android.internal.util.gesture.EdgeServiceConstants.POSITION_MASK;
import static com.android.internal.util.gesture.EdgeServiceConstants.SENSITIVITY_DEFAULT;
import static com.android.internal.util.gesture.EdgeServiceConstants.SENSITIVITY_MASK;
import static com.android.internal.util.gesture.EdgeServiceConstants.SENSITIVITY_NONE;
import static com.android.internal.util.gesture.EdgeServiceConstants.SENSITIVITY_SHIFT;
import static com.android.internal.util.gesture.EdgeServiceConstants.LONG_LIVING;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.gesture.IEdgeGestureActivationListener;
import android.service.gesture.IEdgeGestureHostCallback;
import android.service.gesture.IEdgeGestureService;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.WindowManager;

import com.android.internal.util.gesture.EdgeGesturePosition;
import com.android.server.gesture.EdgeGestureInputFilter;
import com.android.server.input.InputManagerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A system service to track and handle edge swipe gestures. This service interacts with
 * the {@link InputManagerService} to do all the dirty work for listeners:
 * <li>Installing an input filter and listen for edge swipe gestures</li>
 * <li>Removing those gestures from the input stream</li>
 * <li>Transferring touch focus to new recipient</li>
 */
public class EdgeGestureService extends IEdgeGestureService.Stub {
    public static final String TAG = "EdgeGestureService";
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_INPUT = false;

    public static final int MSG_EDGE_GESTURE_ACTIVATION = 32023;
    public static final int MSG_UPDATE_SERVICE = 32025;

    private final Context mContext;
    private final InputManagerService mInputManager;

    private final HandlerThread mHandlerThread = new HandlerThread("EdgeGestureHandler");
    private Handler mHandler;

    // Lock for mInputFilter, activations and listener related variables
    private final Object mLock = new Object();
    private EdgeGestureInputFilter mInputFilter;

    private int mGlobalPositions = 0;
    private int mGlobalSensitivity = 3;

    private final class EdgeGestureActivationListenerRecord extends IEdgeGestureHostCallback.Stub implements DeathRecipient {
        private boolean mActive;

        public EdgeGestureActivationListenerRecord(IEdgeGestureActivationListener listener) {
            this.listener = listener;
            this.positions = 0;
        }

        public void binderDied() {
            removeListenerRecord(this);
        }

        private void updateFlags(int flags) {
            this.positions = flags & POSITION_MASK;
            this.sensitivity = (flags & SENSITIVITY_MASK) >> SENSITIVITY_SHIFT;
            this.longLiving = (flags & LONG_LIVING) != 0;
        }

        private boolean eligibleForActivation(int positionFlag) {
            return (positions & positionFlag) != 0;
        }

        private boolean notifyEdgeGestureActivation(int touchX, int touchY, EdgeGesturePosition position) {
            if ((positions & position.FLAG) != 0) {
                try {
                    mActive = true;
                    listener.onEdgeGestureActivation(touchX, touchY, position.INDEX, 0);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to notify process, assuming it died.", e);
                    mActive = false;
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

        public boolean dropEventsUntilLift() {
            if (DEBUG) {
                Slog.d(TAG, "Will drop all next events till touch up");
            }
            if (mActive) {
                return mInputFilter.dropSequence();
            }
            return false;
        }

        // called through Binder
        public void restoreListenerState() throws RemoteException {
            if (DEBUG) {
                Slog.d(TAG, "Restore listener state");
            }
            if (mActive) {
                mInputFilter.unlockFilter();
                mActive = false;
                synchronized (mLock) {
                    // restore input filter state by updating
                    mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
                }
            }
        }

        public boolean isActive() {
            return mActive;
        }

        public void dump(PrintWriter pw, String prefix) {
            pw.print(prefix);
            pw.print("mPositions=0x" + Integer.toHexString(positions));
            pw.println(" mActive=" + mActive);
            pw.print(prefix);
            pw.println("mBinder=" + listener);
        }

        public int positions;
        public int sensitivity;
        public final IEdgeGestureActivationListener listener;
        public boolean longLiving = false;
    }
    private final List<EdgeGestureActivationListenerRecord> mEdgeGestureActivationListener =
            new ArrayList<EdgeGestureActivationListenerRecord>();
    // end of lock guarded variables

    private DisplayObserver mDisplayObserver;

    // called by system server
    public EdgeGestureService(Context context, InputManagerService inputManager) {
        mContext = context;
        mInputManager = inputManager;
    }

    // called by system server
    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "Starting the edge gesture capture thread ...");

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
        mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
    }


    private void updateMonitoring() {
        synchronized(mLock) {
            mGlobalPositions = 0;
            mGlobalSensitivity = SENSITIVITY_NONE;
            boolean someLongLiving = false;
            int activePositions = 0;
            for (EdgeGestureActivationListenerRecord temp : mEdgeGestureActivationListener) {
                mGlobalPositions |= temp.positions;
                if (temp.isActive()) {
                    activePositions |= temp.positions;
                }
                if (temp.sensitivity != SENSITIVITY_NONE) {
                    mGlobalSensitivity = Math.max(mGlobalSensitivity, temp.sensitivity);
                }
                someLongLiving |= temp.longLiving;
            }
            boolean havePositions = mGlobalPositions != 0;
            mGlobalPositions &= ~activePositions;
            // if no special sensitivity is requested, we settle on DEFAULT
            if (mGlobalSensitivity == SENSITIVITY_NONE) {
                mGlobalSensitivity = SENSITIVITY_DEFAULT;
            }

            if (mInputFilter == null && havePositions) {
                enforceMonitoringLocked();
            } else if (mInputFilter != null && !havePositions && !someLongLiving) {
                shutdownMonitoringLocked();
            }
        }
    }

    private void enforceMonitoringLocked() {
        if (DEBUG) {
            Slog.d(TAG, "Attempting to start monitoring input events ...");
        }
        mInputFilter = new EdgeGestureInputFilter(mContext, mHandler);
        mInputManager.registerSecondaryInputFilter(mInputFilter);
        mDisplayObserver.observe();
    }

    private void shutdownMonitoringLocked() {
        if (DEBUG) {
            Slog.d(TAG, "Shutting down monitoring input events ...");
        }
        mDisplayObserver.unobserve();
        mInputManager.unregisterSecondaryInputFilter(mInputFilter);
        mInputFilter = null;
    }

    // called through Binder
    public IEdgeGestureHostCallback registerEdgeGestureActivationListener(IEdgeGestureActivationListener listener) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.INJECT_EVENTS)
                != PackageManager.PERMISSION_GRANTED) {
            Slog.w(TAG, "Permission Denial: can't register from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return null;
        }

        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        EdgeGestureActivationListenerRecord record = null;
        synchronized(mLock) {
            record = findListenerRecordLocked(listener.asBinder());
            if (record == null) {
                record = new EdgeGestureActivationListenerRecord(listener);
                try {
                    listener.asBinder().linkToDeath(record, 0);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Recipient died during registration pid=" + Binder.getCallingPid());
                    return null;
                }
                mEdgeGestureActivationListener.add(record);
            }
        }
        return record;
    }

    // called through Binder
    public void updateEdgeGestureActivationListener(IBinder listener, int positionFlags) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized(mLock) {
            EdgeGestureActivationListenerRecord record = findListenerRecordLocked(listener);
            if (record == null) {
                Slog.w(TAG, "Unknown listener on update listener. Register first?");
                throw new IllegalStateException("listener not registered");
            }
            record.updateFlags(positionFlags);
            // update input filter only when #systemReady() was called
            if (mHandler != null) {
                mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
            }
        }
    }

    private EdgeGestureActivationListenerRecord findListenerRecordLocked(IBinder listener) {
        for (EdgeGestureActivationListenerRecord record : mEdgeGestureActivationListener) {
            if (record.listener.asBinder().equals(listener)) {
                return record;
            }
        }
        return null;
    }

    private void removeListenerRecord(EdgeGestureActivationListenerRecord record) {
        synchronized(mLock) {
            mEdgeGestureActivationListener.remove(record);
            // restore input filter state by updating
            mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
        }
    }

    // called by handler thread
    private boolean propagateActivation(int touchX, int touchY, EdgeGesturePosition position) {
        synchronized(mLock) {
            EdgeGestureActivationListenerRecord target = null;
            for (EdgeGestureActivationListenerRecord record : mEdgeGestureActivationListener) {
                if (record.eligibleForActivation(position.FLAG)) {
                    target = record;
                    break;
                }
            }
            // NOTE: We can do this here because the #onGestureActivation() is a oneway
            // Binder call. This means we do not block with holding the mListenerLock!!!
            // If this ever change, this needs to be adjusted and if you don't know what
            // this means, you should probably not mess around with this code, anyway.
            if (target != null && !target.notifyEdgeGestureActivation(touchX, touchY, position)) {
                target = null;
            }
            return target != null;
        }
    }

    private final class H extends Handler {
        public H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_EDGE_GESTURE_ACTIVATION:
                    if (DEBUG) {
                        Slog.d(TAG, "Activating edge gesture on " + m.obj.toString());
                    }
                    // Since input filter runs asynchronously to us, double activation may happen
                    // theoretically. Take the safe route here.
                    removeMessages(MSG_EDGE_GESTURE_ACTIVATION);
                    if (propagateActivation(m.arg1, m.arg2, (EdgeGesturePosition) m.obj)) {
                        // switch off activated positions
                        updateMonitoring();
                        updateServiceHandler(mGlobalPositions, mGlobalSensitivity);
                    }
                    break;
                case MSG_UPDATE_SERVICE:
                    updateMonitoring();
                    if (DEBUG) {
                        Slog.d(TAG, "Updating positions 0x" + Integer.toHexString(mGlobalPositions)
                                + " sensitivity: " + mGlobalSensitivity);
                    }
                    updateServiceHandler(mGlobalPositions, mGlobalSensitivity);
                    break;
                }
        }

        private void updateServiceHandler(int positions, int sensitivity) {
            synchronized (mLock) {
                if (mInputFilter != null) {
                    mInputFilter.updatePositions(positions);
                    mInputFilter.updateSensitivity(sensitivity);
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

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            // let's log all exceptions we do not know about.
            if (!(e instanceof IllegalArgumentException || e instanceof IllegalStateException)) {
                Slog.e(TAG, "EdgeGestureService crashed: ", e);
            }
            throw e;
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump EdgeGestureService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("EDGE GESTURE SERVICE (dumpsys edgegestureservice)\n");
        synchronized(mLock) {
            pw.println("  mInputFilter=" + mInputFilter);
            if (mInputFilter != null) {
                mInputFilter.dump(pw, "    ");
            }
            pw.println("  mGlobalPositions=0x" + Integer.toHexString(mGlobalPositions));
            pw.println("  mGlobalSensitivity=" + mGlobalSensitivity);
            int i = 0;
            for (EdgeGestureActivationListenerRecord record : mEdgeGestureActivationListener) {
                if (record.isActive()) {
                    pw.println("  Active record: #" + (i + 1));
                }
            }
            i = 0;
            for (EdgeGestureActivationListenerRecord record : mEdgeGestureActivationListener) {
                pw.println("  Listener #" + i + ":");
                record.dump(pw, "    ");
                i++;
            }
        }
    }
}
