/*
 * Copyright (C) 2012 The CyanogenMod Project
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
package com.android.internal.policy.impl;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * This helper class handles long press button actions in various lock screens
 */
class ButtonActionsHelper {

    private static final int ACTION_RESULT_RUN = 0;
    private static final int ACTION_RESULT_NOTRUN = 1;

    private int mLongPressKeyCode = 0;

    boolean handleKeyDown(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_MENU;
    }

    boolean handleKeyUp(int keyCode) {
        boolean ret = (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_MENU)
                && mLongPressKeyCode == keyCode;
        mLongPressKeyCode = 0;
        return ret;
    }

    boolean handleKeyLongPress(Context context, int keyCode) {
        String action = null;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                action = Settings.System.LOCKSCREEN_LONG_BACK_ACTION;
                mLongPressKeyCode = keyCode;
                break;
            case KeyEvent.KEYCODE_HOME:
                action = Settings.System.LOCKSCREEN_LONG_HOME_ACTION;
                mLongPressKeyCode = keyCode;
                break;
            case KeyEvent.KEYCODE_MENU:
                action = Settings.System.LOCKSCREEN_LONG_MENU_ACTION;
                mLongPressKeyCode = keyCode;
                break;
            default:
                mLongPressKeyCode = 0;
        }

        if (action != null) {
            String uri = Settings.System.getString(context.getContentResolver(), action);
            if (uri != null && runAction(context, uri) != ACTION_RESULT_NOTRUN) {
                long[] pattern = getLongPressVibePattern(context);
                if (pattern != null) {
                    Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (pattern.length == 1) {
                        v.vibrate(pattern[0]);
                    } else {
                        v.vibrate(pattern, -1);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private int runAction(Context context, String uri) {
        if ("FLASHLIGHT".equals(uri)) {
            context.sendBroadcast(new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT"));
            return ACTION_RESULT_RUN;
        } else if ("NEXT".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT);
            return ACTION_RESULT_RUN;
        } else if ("PREVIOUS".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return ACTION_RESULT_RUN;
        } else if ("PLAYPAUSE".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return ACTION_RESULT_RUN;
        } else if ("SOUND".equals(uri)) {
            toggleSilentMode(context);
            return ACTION_RESULT_RUN;
        }

        return ACTION_RESULT_NOTRUN;
    }

    private static void sendMediaButtonEvent(Context context, int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        context.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        context.sendOrderedBroadcast(upIntent, null);
    }

    private static void toggleSilentMode(Context context) {
        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        final boolean hasVib = vib == null ? false : vib.hasVibrator();
        if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            am.setRingerMode(hasVib
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private static long[] getLongPressVibePattern(Context context) {
        if (Settings.System.getInt(context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0) {
            return null;
        }

        int[] defaultPattern = context.getResources().getIntArray(
                com.android.internal.R.array.config_longPressVibePattern);
        if (defaultPattern == null) {
            return null;
        }

        long[] pattern = new long[defaultPattern.length];
        for (int i = 0; i < defaultPattern.length; i++) {
            pattern[i] = defaultPattern[i];
        }

        return pattern;
    }

}
