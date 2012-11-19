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

import java.net.URISyntaxException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;
import static com.android.internal.util.cm.ActionConstants.*;

/*
 * Helper classes for managing custom actions
 */

public class ActionTarget {

    final String TAG = "ActionTarget";

    private AudioManager mAm;
    private int mInjectKeyCode;
    private Context mContext;
    private Handler mHandler;

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    public ActionTarget (Context context) {
        mContext = context;
        mHandler = new Handler();
        mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean launchAction (String action){
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        // Do nothing here
        }

        if (action == null || action.equals(ACTION_NONE)) {
            return false;
        } else if (action.equals(ACTION_RECENTS)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE))
                        .toggleRecentApps();
            } catch (RemoteException e) {
            // Do nothing here
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
        } else if (action.equals(ACTION_GOOGLE_NOW)) {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        } else if (action.equals(ACTION_KILL)) {
            mHandler.post(mKillRunnable);
            return true;
        } else if (action.equals(ACTION_VIBRATE)) {
            AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (mAm != null) {
                if (mAm.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                    switchToVibrateMode();
                } else {
                    switchToNormalRingerMode();
                }
            }
            return true;
        } else if (action.equals(ACTION_SILENT)) {
            AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (mAm != null) {
                if (mAm.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    switchToSilentMode();
                } else {
                    switchToNormalRingerMode();
                }
            }
            return true;
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (mAm != null) {
                if (mAm.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                    switchToVibrateMode();
                } else if (mAm.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                    switchToSilentMode();
                } else {
                    switchToNormalRingerMode();
                }
            }
            return true;
        } else if (action.equals(ACTION_NOTIFICATIONS)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE))
                        .expandNotificationsPanel();
            } catch (RemoteException e) {

            }
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

    private boolean switchToSilentMode() {
        AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAm.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        return true;
    }

    private boolean switchToVibrateMode() {
        AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAm.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                vib.vibrate(50);
            }
    return true;
    }

    private boolean switchToNormalRingerMode() {
        AudioManager mAm = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAm.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,
            (int) (ToneGenerator.MAX_VOLUME * 0.85));
        if (tg != null) {
            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
        }
        return true;
    }

    public boolean restoreDefaults() {
        Settings.System.putString(mContext.getContentResolver(), Settings.System.NAVIGATION_RING_TARGETS[0], null);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.NAVIGATION_RING_TARGETS[1], ACTION_GOOGLE_NOW);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.NAVIGATION_RING_TARGETS[2], null);
        return true;
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
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable mInjectKeyUpRunnable = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable mKillRunnable = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager mAm = (ActivityManager) mContext.getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = mAm.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (!defaultHomePackage.equals(packageName)) {
                    mAm.forceStopPackage(packageName);
                    Toast.makeText(mContext, com.android.internal.R.string.app_killed_message, Toast.LENGTH_SHORT).show();
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
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
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
                        Handler h = new Handler(H.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        H.removeCallbacks(mScreenshotTimeout);
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
                H.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private Handler H = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };
}