/*
 * Copyright (C) 2013 AOKP by Mike Wilson - Zaphod-Beeblebrox && Steve Spear - Stevespear426
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.systemui.cm;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;
import static com.android.internal.util.cm.NavigationRingConstants.*;
import com.android.systemui.screenshot.TakeScreenshotService;

import java.net.URISyntaxException;

/*
 * Helper classes for managing custom actions
 */

public class ActionTarget {
    private static final String TAG = "ActionTarget";

    private AudioManager mAm;
    private Context mContext;
    private Handler mHandler;

    private int mInjectKeyCode;

    private final Object mScreenshotLock = new Object();
    private ServiceConnection mScreenshotConnection = null;

    public ActionTarget (Context context) {
        mContext = context;
        mHandler = new Handler();
        mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean launchAction(String action) {
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ignored
        }

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NONE)) {
            return false;
        } else if (action.equals(ACTION_RECENTS)) {
            try {
                getStatusBarService().toggleRecentApps();
            } catch (RemoteException e) {
                // ignored
            }
            return true;
        } else if (action.equals(ACTION_HOME)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
            return true;
        } else if (action.equals(ACTION_BACK)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
            return true;
        } else if (action.equals(ACTION_MENU)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
            return true;
        } else if (action.equals(ACTION_POWER)) {
            injectKeyDelayed(KeyEvent.KEYCODE_POWER);
            return true;
        } else if (action.equals(ACTION_IME)) {
            mContext.sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
            return true;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            takeScreenshot();
            return true;
        } else if (action.equals(ACTION_ASSIST)) {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        } else if (action.equals(ACTION_KILL)) {
            mHandler.post(mKillRunnable);
            return true;
        } else if (action.equals(ACTION_VIBRATE)) {
            if (mAm.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                switchToVibrateMode();
            } else {
                switchToNormalRingerMode();
            }
            return true;
        } else if (action.equals(ACTION_SILENT)) {
            if (mAm.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                switchToSilentMode();
            } else {
                switchToNormalRingerMode();
            }
            return true;
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            int ringerMode = mAm.getRingerMode();
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                switchToVibrateMode();
            } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                switchToSilentMode();
            } else {
                switchToNormalRingerMode();
            }
            return true;
        } else if (action.equals(ACTION_NOTIFICATIONS)) {
            try {
                getStatusBarService().expandNotificationsPanel();
            } catch (RemoteException e) {
                // ignored
            }
            return true;
        } else if (action.equals(ACTION_TORCH)) {
            Intent intent = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
            mContext.sendBroadcast(intent);
            return true;
        } else {
            try {
                Intent intent = Intent.parseUri(action, 0);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                return true;
            } catch (URISyntaxException e) {
                Log.e(TAG, "URISyntaxException: [" + action + "]");
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "ActivityNotFound: [" + action + "]");
            }
            return false;
        }
    }

    private IStatusBarService getStatusBarService() {
        return IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    private void switchToSilentMode() {
        mAm.setRingerMode(AudioManager.RINGER_MODE_SILENT);
    }

    private void switchToVibrateMode() {
        mAm.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);

        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(50);
    }

    private void switchToNormalRingerMode() {
        mAm.setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,
                (int) (ToneGenerator.MAX_VOLUME * 0.85));
        tg.startTone(ToneGenerator.TONE_PROP_BEEP);
    }

    private void injectKeyDelayed(int keycode){
        mInjectKeyCode = keycode;
        mHandler.removeCallbacks(mInjectKeyDownRunnable);
        mHandler.removeCallbacks(mInjectKeyUpRunnable);
        mHandler.post(mInjectKeyDownRunnable);
        mHandler.postDelayed(mInjectKeyUpRunnable,10); // introduce small delay to handle key press
    }

    final Runnable mInjectKeyDownRunnable = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable mInjectKeyUpRunnable = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable mKillRunnable = new Runnable() {
        public void run() {
            final ActivityManager am =
                    (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);

            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            final String homePackage;

            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                homePackage = res.activityInfo.packageName;
            } else {
                // use default launcher package if we couldn't resolve it
                homePackage = "com.android.launcher";
            }

            final String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (!homePackage.equals(packageName)) {
                am.forceStopPackage(packageName);
                Toast.makeText(mContext,
                        com.android.internal.R.string.app_killed_message,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            Intent intent = new Intent(mContext, TakeScreenshotService.class);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        // wait for the dialog box to close
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        // Do nothing here
                        }

                        // take the screenshot
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        // Do nothing here
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };

            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                mHandler.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }
}
