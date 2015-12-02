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
package android.service.gesture;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.gesture.IEdgeGestureActivationListener;
import android.service.gesture.IEdgeGestureHostCallback;
import android.service.gesture.IEdgeGestureService;
import android.util.Slog;

import com.android.internal.util.gesture.EdgeGesturePosition;

/**
 * This is a simple Manager class for edge gesture service on the application side. The application need
 * {@code INJECT_EVENTS} permission to register {@code EdgeGestureActivationListener}s.<br>
 * See {@link android.service.gesture.IEdgeGestureService} for more information.
 *
 * @see android.service.gesture.IEdgeGestureService
 * @hide
 */
public class EdgeGestureManager {
    public static final String TAG = "EdgeGestureManager";
    public static final boolean DEBUG = false;

    private static EdgeGestureManager sInstance;

    private final IEdgeGestureService mPs;

    public static abstract class EdgeGestureActivationListener {
        private Handler mHandler;
        private IEdgeGestureHostCallback mCallback;

        private class Delegator extends IEdgeGestureActivationListener.Stub {
            public void onEdgeGestureActivation(final int touchX, final int touchY, final int positionIndex, final int flags)
                    throws RemoteException {
                mHandler.post(new Runnable() {
                    public void run() {
                        EdgeGestureActivationListener.this.onEdgeGestureActivation(touchX, touchY, EdgeGesturePosition.values()[positionIndex], flags);
                    }
                });
            }
        }
        private Delegator mDelegator;

        public EdgeGestureActivationListener() {
            this(Looper.getMainLooper());
        }

        public EdgeGestureActivationListener(Looper looper) {
            mHandler = new Handler(looper);
            mDelegator = new Delegator();
        }

        /* package */ void setHostCallback(IEdgeGestureHostCallback hostCallback) {
            mCallback = hostCallback;
        }

        /**
         * Override this to receive activations from the edge gesture service.
         *
         * @param touchX the last X position a touch event was registered.
         * @param touchY the last Y position a touch event was registered.
         * @param position the position of the activation.
         * @param flags currently 0.
         * @see IEdgeGestureActivationListener#onEdgeGestureActivation(int, int, int, int)
         */
        public abstract void onEdgeGestureActivation(int touchX, int touchY, EdgeGesturePosition position, int flags);

        /**
         * After being activated, this allows the edge gesture control to steal focus from the current
         * window.
         *
         * @see IEdgeGestureHostCallback#gainTouchFocus(IBinder)
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

        public boolean dropEventsUntilLift() {
            try {
                return mCallback.dropEventsUntilLift();
            } catch (RemoteException e) {
                Slog.w(TAG, "dropNextEvents failed: " + e.getMessage());
                /* fall through */
            }
            return false;
        }

        /**
         * Turns listening for edge gesture activation gestures on again, after it was disabled during
         * the call to the listener.
         *
         * @see IEdgeGestureHostCallback#restoreListenerState()
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

    private EdgeGestureManager(IEdgeGestureService ps) {
        mPs = ps;
    }

    /**
     * Gets an instance of the edge gesture manager.
     *
     * @return The edge gesture manager instance.
     * @hide
     */
    public static EdgeGestureManager getInstance() {
        synchronized (EdgeGestureManager.class) {
            if (sInstance == null) {
                IBinder b = ServiceManager.getService("edgegestureservice");
                sInstance = new EdgeGestureManager(IEdgeGestureService.Stub.asInterface(b));
            }
            return sInstance;
        }
    }

    /**
     * Checks if the edge gesture service is present.
     * <p>
     * Since the service is only started at boot time and is bound to the system server, this
     * is constant for the devices up time.
     *
     * @return {@code true} when the edge gesture service is running on this device.
     * @hide
     */
    public boolean isPresent() {
        return mPs != null;
    }

    /**
     * Register a listener for edge gesture activation gestures. Initially the listener
     * is set to listen for no position. Use updateedge gestureActivationListener() to
     * bind the listener to positions.
     *
     * @param listener is the activation listener.
     * @return {@code true} if the registration was successful.
     * @hide
     */
    public boolean setEdgeGestureActivationListener(EdgeGestureActivationListener listener) {
        if (DEBUG) {
            Slog.d(TAG, "Set edge gesture activation listener");
        }
        try {
            IEdgeGestureHostCallback callback = mPs.registerEdgeGestureActivationListener(listener.mDelegator);
            listener.setHostCallback(callback);
            return true;
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set edge gesture activation listener: " + e.getMessage());
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
    public void updateEdgeGestureActivationListener(EdgeGestureActivationListener listener, int positions) {
        if (DEBUG) {
            Slog.d(TAG, "Update edge gesture activation listener: 0x" + Integer.toHexString(positions));
        }
        try {
            mPs.updateEdgeGestureActivationListener(listener.mDelegator.asBinder(), positions);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to update edge gesture activation listener: " + e.getMessage());
        }
    }

}
