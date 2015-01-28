/*
 * Copyright (C) 2015 The CyanogenMod Project
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

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.TorchManager;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import com.android.systemui.R;

import java.net.URISyntaxException;

import static com.android.systemui.cm.NavigationRingConstants.*;

public class NavigationRingHelpers {
    public static final int MAX_ACTIONS = 3;

    private NavigationRingHelpers() {
        // Do nothing here
    }

    public static String[] getTargetActions(Context context) {
        final ContentResolver cr = context.getContentResolver();
        final String[] result = new String[MAX_ACTIONS];
        boolean isDefault = true;

        for (int i = 0; i < MAX_ACTIONS; i++) {
            result[i] = Settings.Secure.getString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[i]);
            if (result[i] != null) {
                isDefault = false;
            }
        }

        if (isDefault) {
            resetActionsToDefaults(context);
            result[1] = ACTION_ASSIST;
        }

        filterAction(result, ACTION_ASSIST, isAssistantAvailable(context));
        filterAction(result, ACTION_TORCH, isTorchAvailable(context));

        return result;
    }

    private static void filterAction(String[] result, String action, boolean available) {
        if (available) {
            return;
        }

        for (int i = 0; i < result.length; i++) {
            if (TextUtils.equals(result[i], action)) {
                result[i] = null;
            }
        }
    }

    public static void resetActionsToDefaults(Context context) {
        final ContentResolver cr = context.getContentResolver();
        Settings.Secure.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[0], null);
        Settings.Secure.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[1], ACTION_ASSIST);
        Settings.Secure.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[2], null);
    }

    public static boolean isAssistantAvailable(Context context) {
        return ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(context, true, UserHandle.USER_CURRENT) != null;
    }

    public static boolean isTorchAvailable(Context context) {
        TorchManager torchManager = (TorchManager) context.getSystemService(Context.TORCH_SERVICE);
        return torchManager.isTorchSupported();
    }

    public static Drawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NONE)) {
            resourceId = R.drawable.ic_navigation_ring_empty;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            resourceId = R.drawable.ic_navigation_ring_screenshot;
        } else if (action.equals(ACTION_IME_SWITCHER)) {
            resourceId = R.drawable.ic_navigation_ring_ime_switcher;
        } else if (action.equals(ACTION_VIBRATE)) {
            resourceId = getVibrateDrawableResId(context);
        } else if (action.equals(ACTION_SILENT)) {
            resourceId = getSilentDrawableResId(context);
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            resourceId = getRingerDrawableResId(context);
        } else if (action.equals(ACTION_KILL_TASK)) {
            resourceId = R.drawable.ic_navigation_ring_killtask;
        } else if (action.equals(ACTION_STANDBY)) {
            resourceId = R.drawable.ic_navigation_ring_standby;
        } else if (action.equals(ACTION_TORCH)) {
            resourceId = getTorchDrawableResId(context);
        } else if (action.equals(ACTION_ASSIST)) {
            resourceId = R.drawable.ic_navigation_ring_search;
        }

        if (resourceId < 0) {
            // No pre-defined action, try to resolve URI
            try {
                Intent intent = Intent.parseUri(action, 0);
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                if (info != null) {
                    return info.loadIcon(pm);
                }
            } catch (URISyntaxException e) {
                // Treat as empty
            }
            return null;
        }

        return context.getResources().getDrawable(resourceId);
    }

    private static int getVibrateDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
            return R.drawable.ic_navigation_ring_vibrate;
        } else {
            return R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getSilentDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            return R.drawable.ic_navigation_ring_silent;
        } else {
            return R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getRingerDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = am.getRingerMode();

        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            return R.drawable.ic_navigation_ring_vibrate;
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return R.drawable.ic_navigation_ring_silent;
        } else {
            return R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getTorchDrawableResId(Context context) {
        TorchManager torchManager = (TorchManager) context.getSystemService(Context.TORCH_SERVICE);
        boolean active = torchManager.isTorchOn();
        if (active) {
            return R.drawable.ic_navigation_ring_torch_on;
        }

        return R.drawable.ic_navigation_ring_torch_off;
    }
}
