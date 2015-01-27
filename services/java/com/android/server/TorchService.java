package com.android.server;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.ITorchService;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class TorchService extends ITorchService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = TorchService.class.getSimpleName();

    private final Context mContext;
    private int mTorchAppUid = 0;
    private int mTorchAppCameraId = -1;
    private final SparseArray<CameraUserRecord> mCamerasInUse;
    private final Object mStopTorchLock = new Object();
    private boolean mTorchUsingSysfs;

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

    private BroadcastReceiver mStopTorchDoneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Torch shutdown broadcast completed");
            synchronized (mStopTorchLock) {
                mStopTorchLock.notify();
            }
        }
    };

    public TorchService(Context context) {
        mContext = context;
        mCamerasInUse = new SparseArray<CameraUserRecord>();
    }

    @Override
    public void onCameraOpened(final IBinder token, final int cameraId) {
        if (DEBUG) Log.d(TAG, "onCameraOpened(token= " + token + ", cameraId=" + cameraId + ")");
        boolean needTorchShutdown = false;

        synchronized (mCamerasInUse) {
            if (mTorchAppUid != 0 && Binder.getCallingUid() == mTorchAppUid) {
                if (DEBUG) Log.d(TAG, "Camera was opened by torch app");
                mTorchAppCameraId = cameraId;
            } else {
                // As a synchronous broadcast is an expensive operation, only
                // attempt to kill torch if it actually grabbed the camera before
                if (cameraId == mTorchAppCameraId) {
                    if (mTorchUsingSysfs || mCamerasInUse.get(cameraId) != null) {
                        if (DEBUG) Log.d(TAG, "Need to kill torch");
                        needTorchShutdown = true;
                    }
                }
            }
        }

        // Shutdown torch outside of lock - torch shutdown will call into onCameraClosed()
        if (needTorchShutdown) {
            shutdownTorch();
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
            if (cameraId == mTorchAppCameraId && Binder.getCallingUid() == mTorchAppUid) {
                mTorchAppCameraId = -1;
            }
        }
    }

    @Override
    public boolean onStartingTorch(int cameraId) {
        if (DEBUG) Log.d(TAG, "onStartingTorch(cameraId=" + cameraId + ")");
        synchronized (mCamerasInUse) {
            mTorchAppUid = Binder.getCallingUid();
            if (cameraId >= 0) {
                if (cameraId == mTorchAppCameraId) {
                    return true;
                }
                return mCamerasInUse.get(cameraId) == null;
            }

            // cameraId < 0 means torch is using sysfs
            cameraId = getBackFacingCameraId();
            if (mCamerasInUse.get(cameraId) != null) {
                return false;
            }
            mTorchUsingSysfs = true;
            mTorchAppCameraId = cameraId;
            return true;
        }
    }

    @Override
    public void onStopTorch() {
        if (DEBUG) Log.d(TAG, "onStopTorch()");
        synchronized (mCamerasInUse) {
            if (mTorchUsingSysfs) {
                mTorchAppCameraId = -1;
            }
            mTorchUsingSysfs = false;
        }
    }

    private int getBackFacingCameraId() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return 0;
    }

    private void removeCameraUserLocked(IBinder token, int cameraId) {
        CameraUserRecord record = mCamerasInUse.get(cameraId);
        if (record != null && record.token == token) {
            if (DEBUG) Log.d(TAG, "Removing camera user " + token);
            mCamerasInUse.delete(cameraId);
        }
    }

    private void shutdownTorch() {
        if (DEBUG) Log.d(TAG, "shutdownTorch()");
        // Ordered broadcasts are asynchronous (they only guarantee the order between
        // receivers), so make them synchronous manually by executing the broadcast in a
        // background thread and blocking the calling thread until the broadcast is done
        HandlerThread stopTorchThread = new HandlerThread("StopTorch");
        stopTorchThread.start();
        Handler handler = new Handler(stopTorchThread.getLooper());

        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("stop", true);
        i.addFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_RECEIVER_FOREGROUND);

        synchronized (mStopTorchLock) {
            if (DEBUG) Log.v(TAG, "Sending torch shutdown broadcast");
            mContext.sendOrderedBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF, null,
                    mStopTorchDoneReceiver, handler, Activity.RESULT_OK, null, null);

            try {
                mStopTorchLock.wait(2000);
            } catch (InterruptedException e) {
                // shouldn't happen, ignore
            }
        }
        stopTorchThread.quit();
        if (DEBUG) Log.v(TAG, "Torch shutdown completed");
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump torch service from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("Current torch service state:");

        pw.println("  Active cameras:");
        for (int i = 0; i < mCamerasInUse.size(); i++) {
            int cameraId = mCamerasInUse.keyAt(i);
            CameraUserRecord record = mCamerasInUse.valueAt(i);
            boolean isTorch = cameraId == mTorchAppCameraId;
            String[] packages = mContext.getPackageManager().getPackagesForUid(record.uid);

            pw.print("    Camera " + cameraId + " (" + (isTorch ? "torch" : "camera"));
            pw.println("): pid=" + record.pid + "; package=" + TextUtils.join(",", packages));
        }
        pw.println("  mTorchAppUid=" + mTorchAppUid);
        pw.println("  mTorchAppCameraId=" + mTorchAppCameraId);
        pw.println("  mTorchUsingSysfs=" + mTorchUsingSysfs);
    }
}
