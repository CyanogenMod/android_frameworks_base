/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.ITorchCallback;
import android.hardware.ITorchService;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

/**
 * @hide
 */
public class TorchService extends ITorchService.Stub {
    private static final String TAG = TorchService.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int DISPATCH_ERROR = 0;
    private static final int DISPATCH_STATE_CHANGE = 1;
    private static final int DISPATCH_AVAILABILITY_CHANGED = 2;

    private final Context mContext;

    private final SparseArray<CameraUserRecord> mCamerasInUse;

    /** Call {@link #ensureHandler()} before using */
    private Handler mHandler;

    /** Lock on mListeners when accessing */
    private RemoteCallbackList<ITorchCallback> mListeners = new RemoteCallbackList<>();

    /** Lock on {@code this} when accessing */
    private boolean mTorchEnabled;

    /** Whether the camera is available **/
    private boolean mTorchAvailable;

    private int mTorchAppUid = 0;
    private int mTorchCameraId = -1;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private boolean mOpeningCamera;
    private CaptureRequest mFlashlightRequest;
    private CameraCaptureSession mSession;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;

    private static class CameraUserRecord {
        IBinder token;
        int pid;
        int uid;

        CameraUserRecord(IBinder token) {
            this.token = token;
            this.pid = Binder.getCallingPid();
            this.uid = Binder.getCallingUid();
        }
    }

    public TorchService(Context context) {
        mContext = context;
        mCamerasInUse = new SparseArray<CameraUserRecord>();
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        initialize();
    }

    public void initialize() {
        try {
            mTorchCameraId = Integer.valueOf(getCameraId());
        } catch (Throwable e) {
            Log.e(TAG, "Couldn't initialize.", e);
            return;
        }

        if (mTorchCameraId != -1) {
            ensureHandler();
            mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mHandler);
        }
    }

    @Override
    public void onCameraOpened(final IBinder token, final int cameraId) {
        if (DEBUG) Log.d(TAG, "onCameraOpened(token= " + token + ", cameraId=" + cameraId + ")");
        boolean needTorchShutdown = false;

        synchronized (mCamerasInUse) {
            if (mTorchAppUid != -1 && Binder.getCallingUid() == mTorchAppUid) {
                if (DEBUG) Log.d(TAG, "Camera was opened by torch app");
                mTorchCameraId = cameraId;
            } else {
                // As a synchronous broadcast is an expensive operation, only
                // attempt to kill torch if it actually grabbed the camera before
                if (cameraId == mTorchCameraId) {
                    if (mCamerasInUse.get(cameraId) != null) {
                        if (DEBUG) Log.d(TAG, "Need to kill torch");
                        needTorchShutdown = true;
                    }
                }
            }
        }

        // Shutdown torch outside of lock - torch shutdown will call into onCameraClosed()
        if (needTorchShutdown) {
            mKillFlashlightRunnable.run();
        }

        try {
            token.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    synchronized (mCamerasInUse) {
                        if (DEBUG) Log.d(TAG, "Camera " + cameraId + " client died");
                        removeCameraUserLocked(token, cameraId);
                    }
                }
            }, 0);
            synchronized (mCamerasInUse) {
                mCamerasInUse.put(cameraId, new CameraUserRecord(token));
            }
        } catch (RemoteException e) {
            // ignore, already dead
        }
    }

    @Override
    public void onCameraClosed(final IBinder token, int cameraId) {
        if (DEBUG) Log.d(TAG, "onCameraClosed(token=" + token + ", cameraId=" + cameraId + ")");
        synchronized (mCamerasInUse) {
            removeCameraUserLocked(token, cameraId);
        }
    }

    @Override
    public synchronized void setTorchEnabled(boolean enabled) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        if (mTorchEnabled != enabled) {
            mTorchEnabled = enabled;
            postUpdateFlashlight();
        }
    }

    @Override
    public void toggleTorch() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        setTorchEnabled(!mTorchEnabled);
    }

    @Override
    public synchronized boolean isAvailable() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        return mTorchAvailable;
    }

    @Override
    public boolean isTorchOn() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        return mTorchEnabled;
    }

    @Override
    public void addListener(ITorchCallback l) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        mListeners.register(l);
    }

    @Override
    public void removeListener(ITorchCallback l) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_TORCH_SERVICE, null);
        mListeners.unregister(l);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump torch service from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Current torch service state:");
        pw.println(" Active cameras:");
        for (int i = 0; i < mCamerasInUse.size(); i++) {
            int cameraId = mCamerasInUse.keyAt(i);
            CameraUserRecord record = mCamerasInUse.valueAt(i);
            boolean isTorch = cameraId == mTorchCameraId;
            pw.print(" Camera " + cameraId + " (" + (isTorch ? "torch" : "camera"));
            pw.println("): pid=" + record.pid + "; uid=" + record.uid);
        }
        pw.println(" mTorchEnabled=" + mTorchEnabled);
        pw.println(" mTorchAvailable=" + mTorchAvailable);
        pw.println(" mTorchAppUid=" + mTorchAppUid);
        pw.println(" mTorchCameraId=" + mTorchCameraId);
        pw.println(" mCameraDevice=" + mCameraDevice);
        pw.println(" mOpeningCamera=" + mOpeningCamera);
    }

    private synchronized void ensureHandler() {
        if (mHandler == null) {
            HandlerThread thread = new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND);
            thread.start();
            mHandler = new Handler(thread.getLooper());
        }
    }

    private void startDevice() throws CameraAccessException {
        mTorchAppUid = Binder.getCallingUid();
        final String cameraId = getCameraId();
        if (DEBUG) Log.d(TAG, "startDevice(), cameraID: " + cameraId);
        mTorchCameraId = Integer.valueOf(cameraId);
        mOpeningCamera = true;
        mCameraManager.openCamera(cameraId, mTorchCameraListener, mHandler);
    }

    private void startSession() throws CameraAccessException {
        mSurfaceTexture = new SurfaceTexture(false);
        Size size = getSmallestSize(mCameraDevice.getId());
        mSurfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
        mSurface = new Surface(mSurfaceTexture);
        ArrayList<Surface> outputs = new ArrayList<>(1);
        outputs.add(mSurface);
        mCameraDevice.createCaptureSession(outputs, mTorchSessionListener, mHandler);
    }

    private Size getSmallestSize(String cameraId) throws CameraAccessException {
        Size[] outputSizes = mCameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(SurfaceTexture.class);
        if (outputSizes == null || outputSizes.length == 0) {
            throw new IllegalStateException(
                    "Camera " + cameraId + "doesn't support any outputSize.");
        }
        Size chosen = outputSizes[0];
        for (Size s : outputSizes) {
            if (chosen.getWidth() >= s.getWidth() && chosen.getHeight() >= s.getHeight()) {
                chosen = s;
            }
        }
        return chosen;
    }

    private void postUpdateFlashlight() {
        ensureHandler();
        mHandler.post(mUpdateFlashlightRunnable);
    }

    private String getCameraId() throws CameraAccessException {
        String[] ids = mCameraManager.getCameraIdList();
        for (String id : ids) {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(id);
            Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
            if (flashAvailable != null && flashAvailable
                    && lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return null;
    }

    private void updateFlashlight(boolean forceDisable) {
        try {
            boolean enabled;
            synchronized (this) {
                enabled = mTorchEnabled && !forceDisable;
            }
            if (enabled) {
                if (mCameraDevice == null) {
                    if (!mOpeningCamera) {
                        startDevice();
                    }
                    return;
                }
                if (mSession == null) {
                    startSession();
                    return;
                }
                if (mFlashlightRequest == null) {
                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    builder.addTarget(mSurface);
                    CaptureRequest request = builder.build();
                    mSession.capture(request, null, mHandler);
                    mFlashlightRequest = request;
                    dispatchStateChange(true);
                }
            } else {
                teardownTorch();
            }

        } catch (CameraAccessException|IllegalStateException|UnsupportedOperationException e) {
            Log.e(TAG, "Error in updateFlashlight", e);
            handleError();
        }
    }

    private void removeCameraUserLocked(IBinder token, int cameraId) {
        CameraUserRecord record = mCamerasInUse.get(cameraId);
        if (record != null && record.token == token) {
            if (DEBUG) Log.d(TAG, "Removing camera user " + token);
            mCamerasInUse.delete(cameraId);
        }
    }

    private void teardownTorch() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mOpeningCamera = false;
        mSession = null;
        mFlashlightRequest = null;
        if (mSurface != null) {
            mSurface.release();
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;
    }

    private void handleError() {
        synchronized (this) {
            mTorchEnabled = false;
        }
        dispatchError();
        dispatchStateChange(false);
        updateFlashlight(true /* forceDisable */);
    }


    private final Runnable mUpdateFlashlightRunnable = new Runnable() {
        @Override
        public void run() {
            updateFlashlight(false /* forceDisable */);
        }
    };

    private final Runnable mKillFlashlightRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                mTorchEnabled = false;
            }
            updateFlashlight(true /* forceDisable */);
            dispatchStateChange(false);
        }
    };

    private void dispatchStateChange(boolean on) {
        dispatchListeners(DISPATCH_STATE_CHANGE, on);
    }

    private void dispatchError() {
        dispatchListeners(DISPATCH_ERROR, false /* argument (ignored) */);
    }

    private void dispatchAvailabilityChanged(boolean available) {
        dispatchListeners(DISPATCH_AVAILABILITY_CHANGED, available);
    }

    private void dispatchListeners(int message, boolean argument) {
        synchronized (mListeners) {
            int N = mListeners.beginBroadcast();
            for(int i=0; i < N; i++) {
                ITorchCallback l = mListeners.getBroadcastItem(i);
                try {
                    if (message == DISPATCH_ERROR) {
                        l.onTorchError();
                    } else if (message == DISPATCH_STATE_CHANGE) {
                        l.onTorchStateChanged(argument);
                    } else if (message == DISPATCH_AVAILABILITY_CHANGED) {
                        l.onTorchAvailabilityChanged(argument);
                    }
                } catch(RemoteException e) {
                    Log.w(TAG, "Unable to post progress to client listener", e);
                }
            }
            mListeners.finishBroadcast();
        }
    }

    private final CameraDevice.StateListener mTorchCameraListener =
            new CameraDevice.StateListener() {
        @Override
        public void onOpened(CameraDevice camera) {
            if (mOpeningCamera) {
                mCameraDevice = camera;
                mOpeningCamera = false;
                postUpdateFlashlight();
            } else {
                teardownTorch();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (mCameraDevice == camera) {
                dispatchStateChange(false);
                teardownTorch();
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: camera=" + camera + " error=" + error);
            if (camera == mCameraDevice || mCameraDevice == null) {
                handleError();
            }
        }
    };

    private final CameraCaptureSession.StateListener mTorchSessionListener =
            new CameraCaptureSession.StateListener() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (mSurface != null) {
                        mSession = session;
                        postUpdateFlashlight();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Configure failed.");
                    if (mSession == null || mSession == session) {
                        handleError();
                    }
                }
            };

    private final CameraManager.AvailabilityCallback mAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(String cameraId) {
                    if (DEBUG) Log.d(TAG, "onCameraAvailable(" + cameraId + ")");
                    if (cameraId.equals(String.valueOf(mTorchCameraId))) {
                        setTorchAvailable(true);
                    }
                }

                @Override
                public void onCameraUnavailable(String cameraId) {
                    if (DEBUG) Log.d(TAG, "onCameraUnavailable(" + cameraId + ")");
                    boolean openedOurselves = mOpeningCamera || mCameraDevice != null;
                    if (!openedOurselves && cameraId.equals(String.valueOf(mTorchCameraId))) {
                        setTorchAvailable(false);
                    }
                }

                private void setTorchAvailable(boolean available) {
                    boolean oldAvailable;
                    synchronized (TorchService.this) {
                        oldAvailable = mTorchAvailable;
                        mTorchAvailable = available;
                    }
                    if (oldAvailable != available) {
                        if (DEBUG) Log.d(TAG, "dispatchAvailabilityChanged(" + available + ")");
                        dispatchAvailabilityChanged(available);
                    }
                }
            };
}
