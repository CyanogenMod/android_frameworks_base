/*
* <!--
*    Copyright (C) 2014 The NamelessROM Project
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
* -->
*/

package com.android.systemui.nameless.onthego;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.internal.util.nameless.NamelessUtils;
import com.android.internal.util.nameless.constants.FlashLightConstants;
import com.android.internal.util.nameless.listeners.ShakeDetector;
import com.android.systemui.R;

import java.io.IOException;

public class OnTheGoService extends Service implements ShakeDetector.Listener {

    private static final String  TAG   = "OnTheGoService";
    private static final boolean DEBUG = false;

    private static final int ONTHEGO_NOTIFICATION_ID = 81333378;

    public static final String ACTION_START          = "start";
    public static final String ACTION_STOP           = "stop";
    public static final String ACTION_TOGGLE_ALPHA   = "toggle_alpha";
    public static final String ACTION_TOGGLE_CAMERA  = "toggle_camera";
    public static final String ACTION_TOGGLE_OPTIONS = "toggle_options";
    public static final String EXTRA_ALPHA           = "extra_alpha";

    private static final int CAMERA_BACK  = 0;
    private static final int CAMERA_FRONT = 1;

    private static final int NOTIFICATION_STARTED = 0;
    private static final int NOTIFICATION_RESTART = 1;
    private static final int NOTIFICATION_ERROR   = 2;

    private final Handler mHandler       = new Handler();
    private final Object  mRestartObject = new Object();

    private FrameLayout         mOverlay;
    private Camera              mCamera;
    private NotificationManager mNotificationManager;
    private SensorManager       mSensorManager;
    private ShakeDetector       mShakeDetector;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceivers(false);
        resetViews();
    }

    private void registerReceivers(boolean isScreenOn) {
        final IntentFilter alphaFilter = new IntentFilter(ACTION_TOGGLE_ALPHA);
        registerReceiver(mAlphaReceiver, alphaFilter);
        final IntentFilter cameraFilter = new IntentFilter(ACTION_TOGGLE_CAMERA);
        registerReceiver(mCameraReceiver, cameraFilter);

        if (!isScreenOn) {
            final IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            screenFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(mScreenReceiver, screenFilter);
        }

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mShakeDetector = new ShakeDetector(this);
        mShakeDetector.start(mSensorManager);
    }

    private void unregisterReceivers(boolean isScreenOff) {
        try {
            unregisterReceiver(mAlphaReceiver);
        } catch (Exception ignored) { }
        try {
            unregisterReceiver(mCameraReceiver);
        } catch (Exception ignored) { }

        if (!isScreenOff) {
            try {
                unregisterReceiver(mScreenReceiver);
            } catch (Exception ignored) { }
        }

        if (mShakeDetector != null) {
            mShakeDetector.stop();
            mShakeDetector = null;
            mSensorManager = null;
        }
    }

    private final BroadcastReceiver mAlphaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final float intentAlpha = intent.getFloatExtra(EXTRA_ALPHA, 0.5f);
            toggleOnTheGoAlpha(intentAlpha);
        }
    };

    private final BroadcastReceiver mCameraReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mRestartObject) {
                final ContentResolver resolver = getContentResolver();
                final boolean restartService = Settings.Nameless.getBooleanForUser(resolver,
                        Settings.Nameless.ON_THE_GO_SERVICE_RESTART, false,
                        UserHandle.USER_CURRENT);
                if (restartService) {
                    restartOnTheGo();
                } else {
                    stopOnTheGo(true);
                }
            }
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            synchronized (mRestartObject) {
                final String action = intent.getAction();
                if (action != null && !action.isEmpty()) {
                    logDebug("mScreenReceiver: " + action);
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        setupViews(true);
                        registerReceivers(true);
                    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        unregisterReceivers(true);
                        resetViews();
                    }
                }
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logDebug("onStartCommand called");

        if (intent == null || !NamelessUtils.hasCamera(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();

        if (action != null && !action.isEmpty()) {
            logDebug("Action: " + action);
            if (action.equals(ACTION_START)) {
                startOnTheGo();
            } else if (action.equals(ACTION_STOP)) {
                stopOnTheGo(false);
            } else if (action.equals(ACTION_TOGGLE_OPTIONS)) {
                new OnTheGoDialog(this).show();
            }
        } else {
            logDebug("Action is NULL or EMPTY!");
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startOnTheGo() {
        if (mNotificationManager != null) {
            logDebug("Starting while active, stopping.");
            stopOnTheGo(false);
            return;
        }

        resetViews();
        registerReceivers(false);
        setupViews(false);

        createNotification(NOTIFICATION_STARTED);
    }

    private void stopOnTheGo(boolean shouldRestart) {
        unregisterReceivers(false);
        resetViews();

        // Cancel notification
        if (mNotificationManager != null) {
            mNotificationManager.cancel(ONTHEGO_NOTIFICATION_ID);
            mNotificationManager = null;
        }

        if (shouldRestart) {
            createNotification(NOTIFICATION_RESTART);
        }

        stopSelf();
    }

    private void restartOnTheGo() {
        resetViews();
        mHandler.removeCallbacks(mRestartRunnable);
        mHandler.postDelayed(mRestartRunnable, 750);
    }

    private final Runnable mRestartRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mRestartObject) {
                setupViews(true);
            }
        }
    };

    private void toggleOnTheGoAlpha() {
        final float alpha = Settings.Nameless.getFloatForUser(getContentResolver(),
                Settings.Nameless.ON_THE_GO_ALPHA, 0.5f, UserHandle.USER_CURRENT);
        toggleOnTheGoAlpha(alpha);
    }

    private void toggleOnTheGoAlpha(float alpha) {
        Settings.Nameless.putFloatForUser(getContentResolver(),
                Settings.Nameless.ON_THE_GO_ALPHA, alpha, UserHandle.USER_CURRENT);

        if (mOverlay != null) {
            mOverlay.setAlpha(alpha);
        }
    }

    private void getCameraInstance(int type) throws RuntimeException, IOException {
        releaseCamera();

        if (!NamelessUtils.hasFrontCamera(this)) {
            mCamera = Camera.open();
            return;
        }

        switch (type) {
            // Get hold of the back facing camera
            default:
            case CAMERA_BACK:
                mCamera = Camera.open(0);
                break;
            // Get hold of the front facing camera
            case CAMERA_FRONT:
                final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                final int cameraCount = Camera.getNumberOfCameras();

                for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                    Camera.getCameraInfo(camIdx, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        mCamera = Camera.open(camIdx);
                    }
                }
                break;
        }
    }

    private void setupViews(final boolean isRestarting) {
        logDebug("Setup Views, restarting: " + (isRestarting ? "true" : "false"));

        final int cameraType = Settings.Nameless.getIntForUser(getContentResolver(),
                Settings.Nameless.ON_THE_GO_CAMERA, 0, UserHandle.USER_CURRENT);

        try {
            getCameraInstance(cameraType);
        } catch (Exception exc) {
            // Well, you cant have all in this life..
            logDebug("Exception: " + exc.getMessage());
            createNotification(NOTIFICATION_ERROR);
            stopOnTheGo(true);
        }

        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final TextureView mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
                try {
                    if (mCamera != null) {
                        mCamera.setDisplayOrientation(wm.getDefaultDisplay().getRotation() + 90);
                        mCamera.setPreviewTexture(surfaceTexture);
                        mCamera.startPreview();
                    }
                } catch (IOException io) {
                    logDebug("IOException: " + io.getMessage());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
                setCameraDisplayOrientation();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) { }
        });

        mOverlay = new FrameLayout(this);
        mOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
        );
        mOverlay.addView(mTextureView);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                PixelFormat.TRANSLUCENT
        );
        wm.addView(mOverlay, params);

        toggleOnTheGoAlpha();
    }

    private void resetViews() {
        releaseCamera();
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mOverlay != null) {
            mOverlay.removeAllViews();
            wm.removeView(mOverlay);
            mOverlay = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void createNotification(final int type) {
        final Resources r = getResources();
        final Notification.Builder builder = new Notification.Builder(this)
                .setTicker(r.getString(
                        (type == 1 ? R.string.onthego_notif_camera_changed :
                                (type == 2 ? R.string.onthego_notif_error
                                        : R.string.onthego_notif_ticker))
                ))
                .setContentTitle(r.getString(
                        (type == 1 ? R.string.onthego_notif_camera_changed :
                                (type == 2 ? R.string.onthego_notif_error
                                        : R.string.onthego_notif_title))
                ))
                .setSmallIcon(com.android.internal.R.drawable.ic_lock_onthego)
                .setWhen(System.currentTimeMillis())
                .setOngoing(!(type == 1 || type == 2));

        if (type == 1 || type == 2) {
            final ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.nameless.onthego.OnTheGoService");
            final Intent startIntent = new Intent();
            startIntent.setComponent(cn);
            startIntent.setAction(ACTION_START);
            final PendingIntent startPendIntent = PendingIntent.getService(this, 0, startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(com.android.internal.R.drawable.ic_media_play,
                    r.getString(R.string.onthego_notif_restart), startPendIntent);
        } else {
            final Intent stopIntent = new Intent(this, OnTheGoService.class)
                    .setAction(OnTheGoService.ACTION_STOP);
            final PendingIntent stopPendIntent = PendingIntent.getService(this, 0, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            final Intent optionsIntent = new Intent(this, OnTheGoService.class)
                    .setAction(OnTheGoService.ACTION_TOGGLE_OPTIONS);
            final PendingIntent optionsPendIntent = PendingIntent.getService(this, 0, optionsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            builder
                    .addAction(com.android.internal.R.drawable.ic_media_stop,
                            r.getString(R.string.onthego_notif_stop), stopPendIntent)
                    .addAction(com.android.internal.R.drawable.ic_text_dot,
                            r.getString(R.string.onthego_notif_options), optionsPendIntent);
        }

        final Notification notif = builder.build();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(ONTHEGO_NOTIFICATION_ID, notif);
    }

    private void logDebug(String msg) {
        if (DEBUG) {
            Log.e(TAG, msg);
        }
    }

    private void setCameraDisplayOrientation() {
        if (mCamera == null) return;

        final Camera.CameraInfo info = new Camera.CameraInfo();
        final int cameraType = Settings.Nameless.getIntForUser(getContentResolver(),
                Settings.Nameless.ON_THE_GO_CAMERA, 0, UserHandle.USER_CURRENT);
        Camera.getCameraInfo(cameraType, info);
        final int rotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }

    private final        Object  mShakeLock     = new Object();
    private final static int     SHAKE_TIMEOUT  = 1000;
    private              boolean mIsShakeLocked = false;

    @Override
    public void hearShake() {
        synchronized (mShakeLock) {
            if (!mIsShakeLocked) {
                final Intent intent = new Intent(FlashLightConstants.ACTION_TOGGLE_STATE);
                sendBroadcastAsUser(intent, UserHandle.CURRENT);
                mIsShakeLocked = true;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mIsShakeLocked = false;
                    }
                }, SHAKE_TIMEOUT);
            }
        }
    }
}
