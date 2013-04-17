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
package android.service.pie;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.pie.IPieService;
import android.util.Slog;

import com.android.internal.util.pie.PiePosition;

/**
 * This is a simple Manager class for pie service on the application side. The application need
 * {@code INJECT_EVENTS} permission to register {@code PieActivationListener}s.<br>
 * See {@link IPieService} for more information.
 *
 * @see IPieService
 * @hide
 */
public class PieManager {
    public static final String TAG = "PieManager";
    public static final boolean DEBUG = false;

    private static PieManager sInstance;

    private final IPieService mPs;

    public static abstract class PieActivationListener {
        private Handler mHandler;
        private IPieHostCallback mCallback;

        private class Delegator extends IPieActivationListener.Stub {
            public void onPieActivation(final int touchX, final int touchY, final int positionIndex, final int flags)
                    throws RemoteException {
                mHandler.post(new Runnable() {
                    public void run() {
                        PieActivationListener.this.onPieActivation(touchX, touchY, PiePosition.values()[positionIndex], flags);
                    }
                });
            }
        }
        private Delegator mDelegator;

        public PieActivationListener() {
            mHandler = new Handler(Looper.getMainLooper());
        }

        public PieActivationListener(Looper looper) {
            mHandler = new Handler(looper);
            mDelegator = new Delegator();
        }

        /* package */ void setHostCallback(IPieHostCallback hostCallback) {
            mCallback = hostCallback;
        }

        /**
         * Override this to receive activations from the pie service.
         *
         * @param touchX the last X position a touch event was registered.
         * @param touchY the last Y position a touch event was registered.
         * @param position the position of the activation.
         * @param flags currently 0.
         * @see IPieActivationListener#onPieActivation(int, int, int, int)
         */
        public abstract void onPieActivation(int touchX, int touchY, PiePosition position, int flags);

        /**
         * After being activated, this allows the pie control to steal focus from the current
         * window.
         *
         * @see IPieHostCallback#gainTouchFocus(IBinder)
         */
        public boolean gainTouchFocus(IBinder applicationWindowToken) {
            try {
                return mCallback.gainTouchFocus(applicationWindowToken);
            } catch (RemoteException e) {
                Slog.w(TAG, "gainTouchFocus failed: " + e.getMessage());
                /* fall through */
            }
            return false;
        }

        /**
         * Turns listening for pie activation gestures on again, after it was disabled during
         * the call to the listener.
         *
         * @see IPieHostCallback#restoreListenerState()
         */
        public void restoreListenerState() {
            if (DEBUG) {
                Slog.d(TAG, "restore listener state: " + Thread.currentThread().getName());
            }
            try {
                mCallback.restoreListenerState();
            } catch (RemoteException e) {
                Slog.w(TAG, "restoreListenerState failed: " + e.getMessage());
                /* fall through */
            }
        }
    }

    private PieManager(IPieService ps) {
        mPs = ps;
    }

    /**
     * Gets an instance of the pie manager.
     *
     * @return The pie manager instance.
     * @hide
     */
    public static PieManager getInstance() {
        synchronized (PieManager.class) {
            if (sInstance == null) {
                IBinder b = ServiceManager.getService("pieservice");
                sInstance = new PieManager(IPieService.Stub.asInterface(b));
            }
            return sInstance;
        }
    }

    /**
     * Checks if the pie service is present.
     * <p>
     * Since the service is only started at boot time and is bound to the system server, this
     * is constant for the devices up time.
     *
     * @return {@code true} when the pie service is running on this device.
     * @hide
     */
    public boolean isPresent() {
        return mPs != null;
    }

    /**
     * Register a listener for pie activation gestures. Initially the listener
     * is set to listen for no position. Use updatePieActivationListener() to
     * bind the listener to positions.
     *
     * @param listener is the activation listener.
     * @return {@code true} if the registration was successful.
     * @hide
     */
    public boolean setPieActivationListener(PieActivationListener listener) {
        if (DEBUG) {
            Slog.d(TAG, "Set pie activation listener");
        }
        try {
            IPieHostCallback callback = mPs.registerPieActivationListener(listener.mDelegator);
            listener.setHostCallback(callback);
            return true;
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set pie activation listener: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update the listener to react on gestures in the given positions.
     *
     * @param listener is a already registered listener.
     * @param positions is a bit mask describing the positions to listen to.
     * @hide
     */
    public void updatePieActivationListener(PieActivationListener listener, int positions) {
        if (DEBUG) {
            Slog.d(TAG, "Update pie activation listener: 0x" + Integer.toHexString(positions));
        }
        try {
            mPs.updatePieActivationListener(listener.mDelegator.asBinder(), positions);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to update pie activation listener: " + e.getMessage());
        }
    }

}
