/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar.policy;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import java.security.Policy;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Manages the flashlight.
 */

public class FlashlightController {
    private static final String TAG = "FlashlightController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int DISPATCH_ERROR = 0;
    private static final int DISPATCH_CHANGED = 1;
    private static final int DISPATCH_AVAILABILITY_CHANGED = 2;
	private Camera camera;
	private boolean isFlashOn;
	private boolean hasFlash;
	Parameters params;
    private final CameraManager mCameraManager;
    /** Call {@link #ensureHandler()} before using */
    private Handler mHandler;

    /** Lock on mListeners when accessing */
    private final ArrayList<WeakReference<FlashlightListener>> mListeners = new ArrayList<>(1);

    /** Lock on {@code this} when accessing */
    private boolean mFlashlightEnabled;
	private boolean flashlightAvailable;
    private final String mCameraId="0";
    private boolean mTorchAvailable;

    public FlashlightController(Context mContext) {
	    
		mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    	ensureHandler();
    	mCameraManager.registerTorchCallback(mTorchCallback, mHandler);

    }

    public void setFlashlight(boolean enabled) {
        boolean pendingError = false;
        synchronized (this) {
			mFlashlightEnabled = enabled;
	
			if(enabled==true){
				turnOnFlash();
			}
			else{
				turnOffFlash();
			}

            if (mFlashlightEnabled != enabled) {
                mFlashlightEnabled = enabled;
            }
        }

        dispatchModeChanged(mFlashlightEnabled);

        if (pendingError) {
            dispatchError();
        }
    }
	
    public void getCamera(){
		if (camera == null) {
	        try {
              camera = Camera.open();
              params = camera.getParameters();
            }catch (Exception e) {
				Log.e(TAG, "Couldn't open camera. Error " + e);
            }
		}
	}
 
	public void turnOnFlash(){
		if(!isFlashOn) {
	        if(camera == null || params == null) {
	            getCamera();
            }
			params = camera.getParameters();
            params.setFlashMode("torch");
            camera.setParameters(params);
            camera.startPreview();
            isFlashOn = true;
        }
   }

	public void turnOffFlash(){
		if (isFlashOn) {
			if (camera == null || params == null) {
				return;
            }
            params = camera.getParameters();
			params.setFlashMode("off");
			camera.setParameters(params);
			camera.stopPreview();
			camera.release();
			camera = null;
			isFlashOn = false;
        }
	}
	
	public synchronized boolean isEnabled() {
        return mFlashlightEnabled;
    }

    public synchronized boolean isAvailable() {
		return mTorchAvailable;
    }

    public void addListener(FlashlightListener l) {
        synchronized (mListeners) {
            cleanUpListenersLocked(l);
            mListeners.add(new WeakReference<>(l));
        }
    }

    public void removeListener(FlashlightListener l) {
        synchronized (mListeners) {
            cleanUpListenersLocked(l);
        }
    }

    private synchronized void ensureHandler() {
        if (mHandler == null) {
            HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            mHandler = new Handler(thread.getLooper());
        }
    }

    private void dispatchModeChanged(boolean enabled) {
        dispatchListeners(DISPATCH_CHANGED, enabled);
    }

    private void dispatchError() {
        dispatchListeners(DISPATCH_CHANGED, false /* argument (ignored) */);
    }

    private void dispatchAvailabilityChanged(boolean available) {
        dispatchListeners(DISPATCH_AVAILABILITY_CHANGED, available);
    }

    private void dispatchListeners(int message, boolean argument) {
        synchronized (mListeners) {
            final int N = mListeners.size();
            boolean cleanup = false;
            for (int i = 0; i < N; i++) {
                FlashlightListener l = mListeners.get(i).get();
                if (l != null) {
                    if (message == DISPATCH_ERROR) {
                        l.onFlashlightError();
                    } else if (message == DISPATCH_CHANGED) {
                        l.onFlashlightChanged(argument);
                    } else if (message == DISPATCH_AVAILABILITY_CHANGED) {
                        l.onFlashlightAvailabilityChanged(argument);
                    }
                } else {
                    cleanup = true;
                }
            }
            if (cleanup) {
                cleanUpListenersLocked(null);
            }
        }
    }

    private void cleanUpListenersLocked(FlashlightListener listener) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            FlashlightListener found = mListeners.get(i).get();
            if (found == null || found == listener) {
                mListeners.remove(i);
            }
        }
    }

    private final CameraManager.TorchCallback mTorchCallback =
            new CameraManager.TorchCallback() {

        @Override
        public void onTorchModeUnavailable(String cameraId) {}

        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (TextUtils.equals(cameraId, mCameraId)) {
                setCameraAvailable(true);
                setTorchMode(enabled);
            }
        }

        private void setCameraAvailable(boolean available) {
            boolean changed;
            synchronized (FlashlightController.this) {
                changed = mTorchAvailable != available;
                mTorchAvailable = available;
            }
            if (changed) {
                if (DEBUG) Log.d(TAG, "dispatchAvailabilityChanged(" + available + ")");
                dispatchAvailabilityChanged(available);
            }
        }

        private void setTorchMode(boolean enabled) {
			boolean changed;
            synchronized (FlashlightController.this) {
                changed = mFlashlightEnabled != enabled;
                mFlashlightEnabled = enabled;
            }
            if (changed) {
                if (DEBUG) Log.d(TAG, "dispatchModeChanged(" + enabled + ")");
                dispatchModeChanged(enabled);
            }
        }
    };

    public interface FlashlightListener {
        /**
         * Called when the flashlight was turned off or on.
         * @param enabled true if the flashlight is currently turned on.
         */
        void onFlashlightChanged(boolean enabled);
        
        /**
         * Called when there is an error that turns the flashlight off.
         */
        void onFlashlightError();
        
        /**
         * Called when there is a change in availability of the flashlight functionality
         * @param available true if the flashlight is currently available.
         */
        void onFlashlightAvailabilityChanged(boolean available);
    }
}
