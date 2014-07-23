/*
 * Copyright (C) 2014 The NamelessRom Project
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
 * limitations under the License.
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
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;

import java.io.IOException;

public class OnTheGoService extends Service {

    private static final int ONTHEGO_NOTIFICATION_ID = 81333378;

    public static final String ACTION_START          = "start";
    public static final String ACTION_STOP           = "stop";
    public static final String ACTION_TOGGLE_ALPHA   = "toggle_alpha";
    public static final String ACTION_TOGGLE_OPTIONS = "toggle_options";
    public static final String EXTRA_ALPHA           = "extra_alpha";

    private static final int CAMERA_BACK  = 0;
    private static final int CAMERA_FRONT = 1;

    private static final int NOTIFICATION_STARTED = 0;
    private static final int NOTIFICATION_RESTART = 1;
    private static final int NOTIFICATION_ERROR   = 2;

    private final Handler mHandler = new Handler();

    private FrameLayout         mOverlay;
    private Camera              mCamera;
    private NotificationManager mNotificationManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterAlphaReceiver();
        resetViews();
    }

    private void registerAlphaReceiver() {
        final IntentFilter alphaFilter = new IntentFilter(ACTION_TOGGLE_ALPHA);
        registerReceiver(mAlphaReceiver, alphaFilter);
    }

    private void unregisterAlphaReceiver() {
        try {
            unregisterReceiver(mAlphaReceiver);
        } catch (Exception ignored) {
            // ignored
        }
    }

    private final BroadcastReceiver mAlphaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final float intentAlpha = intent.getFloatExtra(EXTRA_ALPHA, 0.5f);
            toggleOnTheGoAlpha(intentAlpha);
        }
    };

    private class SettingsObserver extends ContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ON_THE_GO_CAMERA), false, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            // Stop and restart
            stopOnTheGo(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();

        if (action != null && !action.isEmpty()) {
            if (action.equals(ACTION_START)) {
                startOnTheGo();
            } else if (action.equals(ACTION_STOP)) {
                stopOnTheGo(false);
            } else if (action.equals(ACTION_TOGGLE_OPTIONS)) {
                new OnTheGoDialog(this).show();
            }
        } else {
            stopSelf();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void startOnTheGo() {
        if (mNotificationManager != null) {
            stopOnTheGo(false);
            return;
        }

        resetViews();
        registerAlphaReceiver();
        setupViews();
        final SettingsObserver mObserver = new SettingsObserver(mHandler);
        mObserver.observe();

        createNotification(NOTIFICATION_STARTED);
    }

    private void stopOnTheGo(boolean shouldRestart) {
        unregisterAlphaReceiver();
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

    private void toggleOnTheGoAlpha() {
        final float alpha = Settings.System.getFloat(getContentResolver(),
                Settings.System.ON_THE_GO_ALPHA,
                0.5f);
        toggleOnTheGoAlpha(alpha);
    }

    private void toggleOnTheGoAlpha(float alpha) {
        Settings.System.putFloat(getContentResolver(),
                Settings.System.ON_THE_GO_ALPHA,
                alpha);

        if (mOverlay != null) {
            mOverlay.setAlpha(alpha);
        }
    }

    private void getCameraInstance(int type) throws RuntimeException {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
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

    private void setupViews() {
        final int cameraType = Settings.System.getInt(getContentResolver(),
                Settings.System.ON_THE_GO_CAMERA,
                0);
        try {
            getCameraInstance(cameraType);
        } catch (RuntimeException exc) {
            // Well, you cant have all in this life..
            createNotification(NOTIFICATION_ERROR);
            stopOnTheGo(true);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                PixelFormat.TRANSLUCENT
        );

        final TextureView mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
                try {
                    if (mCamera != null) {
                        mCamera.setDisplayOrientation(90);
                        mCamera.setPreviewTexture(surfaceTexture);
                        mCamera.startPreview();
                    }
                } catch (IOException ignored) {
                    // ignored
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                if (mCamera != null) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                }
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

        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(mOverlay, params);

        toggleOnTheGoAlpha();
    }

    private void resetViews() {
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mOverlay != null) {
            mOverlay.removeAllViews();
            wm.removeView(mOverlay);
            mOverlay = null;
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
}
