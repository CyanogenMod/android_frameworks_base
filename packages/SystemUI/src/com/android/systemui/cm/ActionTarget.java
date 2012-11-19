/*
 * Copyright 2011 AOKP by Mike Wilson - Zaphod-Beeblebrox
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
import java.util.HashMap;

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
import com.android.systemui.R;

/*
 * Helper classes for managing custom actions
 */

public class ActionTarget {

    final String TAG = "ActionTarget";

    private AudioManager am;

    public final static String STRING_ACTION_HOME = "**home**";
    public final static String STRING_ACTION_BACK = "**back**";
    public final static String STRING_ACTION_SCREENSHOT = "**screenshot**";
    public final static String STRING_ACTION_MENU = "**menu**";
    public final static String STRING_ACTION_POWER = "**power**";
    public final static String STRING_ACTION_NOTIFICATIONS = "**notifications**";
    public final static String STRING_ACTION_RECENTS = "**recents**";
    public final static String STRING_ACTION_IME = "**ime**";
    public final static String STRING_ACTION_KILL = "**kill**";
    public final static String STRING_ACTION_ASSIST = "**assist**";
    public final static String STRING_ACTION_CUSTOM = "**custom**";
    public final static String STRING_ACTION_SILENT = "**ring_silent**";
    public final static String STRING_ACTION_VIB = "**ring_vib**";
    public final static String STRING_ACTION_SILENT_VIB = "**ring_vib_silent**";
    public final static String STRING_ACTION_NULL = "**null**";

    public final static int ACTION_HOME = 0;
    public final static int ACTION_BACK = 1;
    public final static int ACTION_SCREENSHOT = 2;
    public final static int ACTION_MENU = 3;
    public final static int ACTION_POWER = 4;
    public final static int ACTION_NOTIFICATIONS = 5;
    public final static int ACTION_RECENTS = 6;
    public final static int ACTION_IME = 7;
    public final static int ACTION_KILL = 8;
    public final static int ACTION_ASSIST = 9;
    public final static int ACTION_CUSTOM = 10;
    public final static int ACTION_SILENT = 11;
    public final static int ACTION_VIB = 12;
    public final static int ACTION_SILENT_VIB = 13;
    public final static int ACTION_NULL = 14;

    private HashMap<String, Integer> actionMap;

    private HashMap<String, Integer> getActionMap() {
        if (actionMap == null) {
            actionMap = new HashMap<String, Integer>();
            actionMap.put(STRING_ACTION_HOME, ACTION_HOME);
            actionMap.put(STRING_ACTION_BACK, ACTION_BACK);
            actionMap.put(STRING_ACTION_SCREENSHOT, ACTION_SCREENSHOT);
            actionMap.put(STRING_ACTION_MENU, ACTION_MENU);
            actionMap.put(STRING_ACTION_POWER, ACTION_POWER);
            actionMap.put(STRING_ACTION_NOTIFICATIONS, ACTION_NOTIFICATIONS);
            actionMap.put(STRING_ACTION_RECENTS, ACTION_RECENTS);
            actionMap.put(STRING_ACTION_IME, ACTION_IME);
            actionMap.put(STRING_ACTION_KILL, ACTION_KILL);
            actionMap.put(STRING_ACTION_ASSIST, ACTION_ASSIST);
            actionMap.put(STRING_ACTION_CUSTOM, ACTION_CUSTOM);
            actionMap.put(STRING_ACTION_SILENT, ACTION_SILENT);
            actionMap.put(STRING_ACTION_VIB, ACTION_VIB);
            actionMap.put(STRING_ACTION_SILENT_VIB, ACTION_SILENT_VIB);
            actionMap.put(STRING_ACTION_NULL, ACTION_NULL);
        }
        return actionMap;
    }

    private int mInjectKeyCode;
    private Context mContext;
    private Handler mHandler;

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    public ActionTarget (Context context){
        mContext = context;
        mHandler = new Handler();
        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean launchAction (String action){

        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }

        if (getActionMap().containsKey(action)) {
            switch(getActionMap().get(action)) {

            case ACTION_NULL:
                break;
            case ACTION_ASSIST:
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                break;
            case ACTION_KILL:
                mHandler.post(mKillTask);
                break;
            case ACTION_VIB:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_SILENT:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_SILENT_VIB:
                if(am != null){
                    if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    } else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case ACTION_POWER:
                injectKeyDelayed(KeyEvent.KEYCODE_POWER);
                break;
            case ACTION_IME:
                mContext.sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
                break;
            case ACTION_SCREENSHOT:
                takeScreenshot();
                break;
            case ACTION_HOME:
                injectKeyDelayed(KeyEvent.KEYCODE_HOME);
                break;
            case ACTION_BACK:
                injectKeyDelayed(KeyEvent.KEYCODE_BACK);
                break;
            case ACTION_MENU:
                injectKeyDelayed(KeyEvent.KEYCODE_MENU);
                break;
            case ACTION_RECENTS:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).toggleRecentApps();
                } catch (RemoteException e) {
                    // let it go.
                }
                break;
            case ACTION_NOTIFICATIONS:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
                } catch (RemoteException e) {
                    // A RemoteException is like a cold
                    // Let's hope we don't catch one!
                }
                break;
            default:
            break;
            }
            return true;
        } else {
            try {
                Intent intent = Intent.parseUri(action, 0);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (URISyntaxException e) {
                Log.e(TAG, "URISyntaxException: [" + action + "]");
            } catch (ActivityNotFoundException e){
                Log.e(TAG, "ActivityNotFound: [" + action + "]");
            }
            return true;
        }
    }



    private void injectKeyDelayed(int keycode){
        mInjectKeyCode = keycode;
        mHandler.removeCallbacks(onInjectKey_Down);
        mHandler.removeCallbacks(onInjectKey_Up);
        mHandler.post(onInjectKey_Down);
        mHandler.postDelayed(onInjectKey_Up,10); // introduce small delay to handle key press
    }

    final Runnable onInjectKey_Down = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable onInjectKey_Up = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager am = (ActivityManager) mContext.getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (!defaultHomePackage.equals(packageName)) {
                    am.forceStopPackage(packageName);
                    Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
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

                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
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