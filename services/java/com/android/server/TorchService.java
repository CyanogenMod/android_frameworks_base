package com.android.server;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private SparseArray<CameraUserRecord> mCamerasInUse;
    private Object mStopTorchLock = new Object();

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
        if (DEBUG) Log.d(TAG, "onCameraOpened()");
        if (mTorchAppUid != 0 && Binder.getCallingUid() == mTorchAppUid) {
            if (DEBUG) Log.d(TAG, "camera was opened by torch app");
            mTorchAppCameraId = cameraId;
        } else {
            if (DEBUG) Log.d(TAG, "killing torch");
            // As a synchronous broadcast is an expensive operation, only
            // attempt to kill torch if it actually grabbed the camera before
            if (cameraId == mTorchAppCameraId && mCamerasInUse.get(cameraId) != null) {
                shutdownTorch();
            }
        }
        try {
            token.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    CameraUserRecord record = mCamerasInUse.get(cameraId);
                    if (record != null && record.token == token) {
                        if (DEBUG) Log.d(TAG, "Camera " + cameraId + " client died");
                        mCamerasInUse.delete(cameraId);
                    }
                }
            }, 0);
            mCamerasInUse.put(cameraId, new CameraUserRecord(token));
        } catch (RemoteException e) {
            // ignore, already dead
        }
    }

    @Override
    public void onCameraClosed(int cameraId) {
        mCamerasInUse.delete(cameraId);
        if (cameraId == mTorchAppCameraId) {
            mTorchAppCameraId = -1;
        }
    }

    @Override
    public boolean onStartingTorch(int cameraId) {
        if (DEBUG) Log.d(TAG, "onStartingTorch()");
        mTorchAppUid = Binder.getCallingUid();
        if (cameraId == mTorchAppCameraId) {
            return true;
        }
        return mCamerasInUse.get(cameraId) == null;
    }

    private void shutdownTorch() {
        // Ordered broadcasts are asynchronous (they only guarantee the order between
        // receivers), so make them synchronous manually by executing the broadcast in a
        // background thread and blocking the calling thread until the broadcast is done
        HandlerThread stopTorchThread = new HandlerThread("StopTorch");
        stopTorchThread.start();
        Handler handler = new Handler(stopTorchThread.getLooper());

        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("stop", true);

        synchronized (mStopTorchLock) {
            if (DEBUG) Log.v(TAG, "sending torch shutdown broadcast");
            mContext.sendOrderedBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF, null,
                    mStopTorchDoneReceiver, handler, Activity.RESULT_OK, null, null);

            try {
                mStopTorchLock.wait(2000);
            } catch (InterruptedException e) {
                // shouldn't happen, ignore
            }
        }
        stopTorchThread.quit();
        if (DEBUG) Log.v(TAG, "torch shutdown completed");
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
    }
}
