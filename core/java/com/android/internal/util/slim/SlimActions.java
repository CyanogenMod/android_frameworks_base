/*
* Copyright (C) 2013 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.slim;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.app.IUiModeManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.nameless.NamelessActions;
import com.android.internal.util.omni.OmniSwitchConstants;

import java.net.URISyntaxException;

public class SlimActions {

    private static final int MSG_INJECT_KEY_DOWN = 1066;
    private static final int MSG_INJECT_KEY_UP = 1067;

    private Context mContext;
    private KeyguardManager mKeyguardManager;

    public static void processAction(Context context, String action, boolean isLongpress) {
        processActionWithOptions(context, action, isLongpress, true);
    }

    public static void processActionWithOptions(Context context,
            String action, boolean isLongpress, boolean collapseShade) {

            if (action == null || action.equals(ButtonsConstants.ACTION_NULL)) {
                return;
            }

            final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));

            final IWindowManager windowManagerService = IWindowManager.Stub.asInterface(
                    ServiceManager.getService(Context.WINDOW_SERVICE));

            boolean isKeyguardShowing = false;
            try {
                isKeyguardShowing = windowManagerService.isKeyguardLocked();
            } catch (RemoteException e) {
            }

            boolean isKeyguardSecure = false;
            try {
                isKeyguardSecure = windowManagerService.isKeyguardSecure();
            } catch (RemoteException e) {
            }

            if (collapseShade) {
                    if (!action.equals(ButtonsConstants.ACTION_QS)
                            && !action.equals(ButtonsConstants.ACTION_NOTIFICATIONS)
                            && !action.equals(ButtonsConstants.ACTION_SMART_PULLDOWN)
                            && !action.equals(ButtonsConstants.ACTION_TORCH)) {
                        try {
                            barService.collapsePanels();
                        } catch (RemoteException ex) {
                        }
                    }
            }

            // process the actions
            if (action.equals(ButtonsConstants.ACTION_HOME)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_HOME, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_VOL_UP)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_VOLUME_UP, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_VOL_DOWN)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_VOLUME_DOWN, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_BACK)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_BACK, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_SEARCH)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_MENU)
                    || action.equals(ButtonsConstants.ACTION_MENU_BIG)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_MENU, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_LEFT)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_LEFT, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_RIGHT)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_RIGHT, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_UP)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_UP, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_DOWN)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_DOWN, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_HOME)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_MOVE_HOME, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME_NAVIGATION_END)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_MOVE_END, isLongpress);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_POWER_MENU)) {
                KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                boolean locked = km.inKeyguardRestrictedInputMode() && km.isKeyguardSecure();
                boolean globalActionsOnLockScreen = Settings.System.getInt(
                        context.getContentResolver(), Settings.System.LOCKSCREEN_ENABLE_POWER_MENU, 0) == 1;
                if (locked && !globalActionsOnLockScreen) {
                    return;
                } else {
                    try {
                        windowManagerService.toggleGlobalMenu();
                    } catch (RemoteException e) {
                    }
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_POWER)) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis());
                return;
            } else if (action.equals(ButtonsConstants.ACTION_TORCH)) {
                Intent i = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                context.sendBroadcastAsUser(i, new UserHandle(UserHandle.USER_CURRENT));
                return;
            } else if (action.equals(ButtonsConstants.ACTION_GESTURE)) {
                Intent t = new Intent(Intent.TOGGLE_GESTURE_ACTIONS);
                context.sendBroadcastAsUser(t, new UserHandle(UserHandle.USER_CURRENT));
                return;
            } else if (action.equals(ButtonsConstants.ACTION_IME)) {
                if (isKeyguardShowing) {
                    return;
                }
                context.sendBroadcastAsUser(
                        new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"),
                        new UserHandle(UserHandle.USER_CURRENT));
                return;
            } else if (action.equals(ButtonsConstants.ACTION_PIE)) {
                boolean pieState = isPieEnabled(context);
                /*if (pieState && !isNavBarEnabled(context) && isNavBarDefault(context)) {
                    Toast.makeText(context,
                            com.android.internal.R.string.disable_pie_navigation_error,
                            Toast.LENGTH_LONG).show();
                    return;
                }*/
                Settings.System.putIntForUser(
                        context.getContentResolver(),
                        Settings.System.SPIE_CONTROLS,
                        pieState ? 0 : 1, UserHandle.USER_CURRENT);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_PAPIE)) {
                boolean papieState = isPAPieEnabled(context);
                Settings.System.putIntForUser(
                        context.getContentResolver(),
                        Settings.System.PIE_CONTROLS,
                        papieState ? 0 : 1, UserHandle.USER_CURRENT);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_NAVBAR)) {
                boolean navBarState = isNavBarEnabled(context);
                /*if (navBarState && !isPieEnabled(context) && isNavBarDefault(context)) {
                    Toast.makeText(context,
                            com.android.internal.R.string.disable_navigation_pie_error,
                            Toast.LENGTH_LONG).show();
                    return;
                }*/
                Settings.System.putIntForUser(
                        context.getContentResolver(),
                        Settings.System.NAVIGATION_BAR_SHOW,
                        navBarState ? 0 : 1, UserHandle.USER_CURRENT);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_EXPANDED_DESKTOP)) {
                boolean expandDesktopModeOn = Settings.System.getIntForUser(
                        context.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STATE,
                        0, UserHandle.USER_CURRENT) == 1;
                Settings.System.putIntForUser(
                        context.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STATE,
                        expandDesktopModeOn ? 0 : 1, UserHandle.USER_CURRENT);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_THEME_SWITCH)) {
                boolean enabled = Settings.Secure.getIntForUser(
                        context.getContentResolver(),
                        Settings.Secure.UI_THEME_AUTO_MODE, 0,
                        UserHandle.USER_CURRENT) != 1;
                boolean state = context.getResources().getConfiguration().uiThemeMode
                        == Configuration.UI_THEME_MODE_HOLO_DARK;
                if (!enabled) {
                    Toast.makeText(context,
                            com.android.internal.R.string.theme_auto_switch_mode_error,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                // Handle a switch change
                // we currently switch between holodark and hololight till either
                // theme engine is ready or lightheme is ready. Currently due of
                // missing light themeing hololight = system base theme
                final IUiModeManager uiModeManagerService = IUiModeManager.Stub.asInterface(
                        ServiceManager.getService(Context.UI_MODE_SERVICE));
                try {
                    uiModeManagerService.setUiThemeMode(state
                            ? Configuration.UI_THEME_MODE_HOLO_LIGHT
                            : Configuration.UI_THEME_MODE_HOLO_DARK);
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_KILL)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    barService.toggleKillApp();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_LAST_APP)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    barService.toggleLastApp();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_RECENTS)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    // Perform all related with recent
                    // in a attempt to fix blank slimroms
                    // recent panel sometimes preloading
                    // with PIE
                    barService.cancelPreloadRecentApps();
                    barService.preloadRecentApps();
                    barService.toggleRecentApps();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_SCREENSHOT)) {
                try {
                    barService.toggleScreenshot();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_NOTIFICATIONS)) {
                if (isKeyguardShowing && isKeyguardSecure) {
                    return;
                }
                try {
                    barService.toggleNotificationShade();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_QS)) {
                if (isKeyguardShowing && isKeyguardSecure) {
                    return;
                }
                try {
                    barService.toggleQSShade();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_SMART_PULLDOWN)) {
            if (isKeyguardShowing && isKeyguardSecure) {
                return;
            }
    			try {
                    barService.toggleSmartPulldown();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_ASSIST)
                    || action.equals(ButtonsConstants.ACTION_KEYGUARD_SEARCH)) {
                Intent intent = ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                  .getAssistIntent(context, true, UserHandle.USER_CURRENT);
                if (intent == null) {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
                }
                startActivity(context, windowManagerService, isKeyguardShowing, intent);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_VOICE_SEARCH)) {
                // launch the search activity
                Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    // TODO: This only stops the factory-installed search manager.
                    // Need to formalize an API to handle others
                    SearchManager searchManager =
                            (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
                    if (searchManager != null) {
                        searchManager.stopSearch();
                    }
                startActivity(context, windowManagerService, isKeyguardShowing, intent);
                } catch (ActivityNotFoundException e) {
                    Log.e("SlimActions:", "No activity to handle assist long press action.", e);
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_VIB)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if(am != null && ActivityManagerNative.isSystemReady()) {
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_SILENT)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if(am != null && ActivityManagerNative.isSystemReady()) {
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_VIB_SILENT)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if(am != null && ActivityManagerNative.isSystemReady()) {
                    if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    } else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                return;
            } else if (action.equals(ButtonsConstants.ACTION_OMNISWITCH)) {
                Intent intent = new Intent(OmniSwitchConstants.ACTION_TOGGLE_OVERLAY);
                context.sendBroadcast(intent);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_ONTHEGO)) {
                Intent startIntent = new Intent();
                startIntent.setComponent(new ComponentName(
                        "com.android.systemui",
                        "com.android.systemui.nameless.onthego.OnTheGoService"));
                startIntent.setAction("start");
                context.startService(startIntent);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_CAMERA)) {
                // ToDo: Send for secure keyguard secure camera intent.
                // We need to add support for it first.
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, null);
                startActivity(context, windowManagerService, isKeyguardShowing, intent);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_MEDIA_PREVIOUS)) {
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_MEDIA_NEXT)) {
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_NEXT);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_MEDIA_PLAY_PAUSE)) {
                dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return;
            } else if (action.equals(ButtonsConstants.ACTION_WAKE_DEVICE)) {
                PowerManager powerManager =
                        (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (!powerManager.isScreenOn()) {
                    powerManager.wakeUpWithProximityCheck(SystemClock.uptimeMillis());
                }
                return;
            } else {
                // we must have a custom uri
                Intent intent = null;
                try {
                    intent = Intent.parseUri(action, 0);
                } catch (URISyntaxException e) {
                    Log.e("SlimActions:", "URISyntaxException: [" + action + "]");
                    return;
                }
                startActivity(context, windowManagerService, isKeyguardShowing, intent);
                return;
            }

    }

    public static boolean isPieEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.SPIE_CONTROLS,
                0, UserHandle.USER_CURRENT) == 1;
    }

    public static boolean isPAPieEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.PIE_CONTROLS,
                0, UserHandle.USER_CURRENT) == 1;
    }

    public static boolean isNavBarEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW,
                isNavBarDefault(context) ? 1 : 0, UserHandle.USER_CURRENT) == 1;
    }

    public static boolean isNavBarDefault(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
    }

    public static boolean isActionKeyEvent(String action) {
        if (action.equals(ButtonsConstants.ACTION_HOME)
                || action.equals(ButtonsConstants.ACTION_BACK)
                || action.equals(ButtonsConstants.ACTION_SEARCH)
                || action.equals(ButtonsConstants.ACTION_MENU)
                || action.equals(ButtonsConstants.ACTION_MENU_BIG)
                || action.equals(ButtonsConstants.ACTION_NULL)) {
            return true;
        }
        return false;
    }

    private static void startActivity(Context context, IWindowManager windowManagerService,
                boolean isKeyguardShowing, Intent intent) {
        if (intent == null) {
            return;
        }
        if (isKeyguardShowing) {
            // Have keyguard show the bouncer and launch the activity if the user succeeds.
            try {
                windowManagerService.showCustomIntentOnKeyguard(intent);
            } catch (RemoteException e) {
            }
        } else {
            // otherwise let us do it here
            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
                // too bad, so sad...
            }
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivityAsUser(intent,
                    new UserHandle(UserHandle.USER_CURRENT));
        }
    }

    public static void startIntent(Context context, Intent intent, boolean collapseShade) {
        if (intent == null) {
            return;
        }
        final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final IWindowManager windowManagerService = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));

        boolean isKeyguardShowing = false;
        try {
            isKeyguardShowing = windowManagerService.isKeyguardLocked();
        } catch (RemoteException e) {
        }

        if (collapseShade) {
            try {
                barService.collapsePanels();
            } catch (RemoteException ex) {
            }
        }
        startActivity(context, windowManagerService, isKeyguardShowing, intent);
    }

    private static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(
                ServiceManager.checkService(Context.AUDIO_SERVICE));
        return audioService;
    }

    private static void dispatchMediaKeyWithWakeLockToAudioService(int keycode) {
        if (ActivityManagerNative.isSystemReady()) {
            IAudioService audioService = getAudioService();
            if (audioService != null) {
                try {
                    KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
                    audioService.dispatchMediaKeyEventUnderWakelock(event);
                    event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
                    audioService.dispatchMediaKeyEventUnderWakelock(event);
                } catch (RemoteException e) {
                }
            }
        }
    }

    public static void triggerVirtualKeypress(final int keyCode, boolean longpress) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();
        int downflags = 0;
        int upflags = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            || keyCode == KeyEvent.KEYCODE_MOVE_HOME
            || keyCode == KeyEvent.KEYCODE_MOVE_END
            || keyCode == KeyEvent.KEYCODE_VOLUME_UP
            || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            downflags = upflags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        } else {
            downflags = upflags = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        }
        if (longpress) {
            downflags |= KeyEvent.FLAG_LONG_PRESS;
        }

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                downflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

        final KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                upflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

}

