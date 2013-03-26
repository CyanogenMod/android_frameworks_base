/*
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

package com.android.internal.util.cm;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.cm.NavigationRingConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.net.URISyntaxException;

public class NavigationRingHelpers {
    public static final int MAX_ACTIONS = 3;

    private static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";

    private NavigationRingHelpers() {
    }

    public static String[] getTargetActions(Context context) {
        final ContentResolver cr = context.getContentResolver();
        final String[] result = new String[MAX_ACTIONS];
        boolean isDefault = true;

        for (int i = 0; i < MAX_ACTIONS; i++) {
            result[i] = Settings.System.getString(cr, Settings.System.NAVIGATION_RING_TARGETS[i]);
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
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[0], null);
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[1], ACTION_ASSIST);
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[2], null);
    }

    public static boolean isAssistantAvailable(Context context) {
        return ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(context, UserHandle.USER_CURRENT) != null;
    }

    public static boolean isTorchAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo("net.cactii.flash2", 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            // ignored, just catched so we can return false below
        }
        return false;
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NONE)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_empty;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_screenshot;
        } else if (action.equals(ACTION_IME)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_ime_switcher;
        } else if (action.equals(ACTION_VIBRATE)) {
            resourceId = getVibrateDrawableResId(context);
        } else if (action.equals(ACTION_SILENT)) {
            resourceId = getSilentDrawableResId(context);
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            resourceId = getRingerDrawableResId(context);
        } else if (action.equals(ACTION_KILL)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_killtask;
        } else if (action.equals(ACTION_POWER)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_power;
        } else if (action.equals(ACTION_TORCH)) {
            resourceId = getTorchDrawableResId(context);
        } else if (action.equals(ACTION_ASSIST)) {
            resourceId = com.android.internal.R.drawable.ic_action_assist_generic;
        }

        if (resourceId < 0) {
            // no pre-defined action, try to resolve URI
            try {
                Intent intent = Intent.parseUri(action, 0);
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                if (info != null) {
                    return createDrawableForActivity(res, info.loadIcon(pm));
                }
            } catch (URISyntaxException e) {
                // treat as empty
            }

            resourceId = com.android.internal.R.drawable.ic_navigation_ring_empty;
        }

        TargetDrawable drawable = new TargetDrawable(res, resourceId);
        if (resourceId == com.android.internal.R.drawable.ic_navigation_ring_empty) {
            drawable.setEnabled(false);
        }
        return drawable;
    }

    private static TargetDrawable createDrawableForActivity(Resources res, Drawable activityIcon) {
        Drawable iconBg = res.getDrawable(
                com.android.internal.R.drawable.ic_navigation_ring_blank_normal);
        Drawable iconBgActivated = res.getDrawable(
                com.android.internal.R.drawable.ic_navigation_ring_blank_activated);

        int margin = (int)(iconBg.getIntrinsicHeight() / 3);
        LayerDrawable icon = new LayerDrawable (new Drawable[] { iconBg, activityIcon });
        LayerDrawable iconActivated = new LayerDrawable (new Drawable[] { iconBgActivated, activityIcon });

        icon.setLayerInset(1, margin, margin, margin, margin);
        iconActivated.setLayerInset(1, margin, margin, margin, margin);

        StateListDrawable selector = new StateListDrawable();
        selector.addState(TargetDrawable.STATE_INACTIVE, icon);
        selector.addState(TargetDrawable.STATE_ACTIVE, iconActivated);
        selector.addState(TargetDrawable.STATE_FOCUSED, iconActivated);

        return new TargetDrawable(res, selector);
    }

    private static int getVibrateDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
            return com.android.internal.R.drawable.ic_navigation_ring_vibrate;
        } else {
            return com.android.internal.R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getSilentDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            return com.android.internal.R.drawable.ic_navigation_ring_silent;
        } else {
            return com.android.internal.R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getRingerDrawableResId(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = am.getRingerMode();

        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            return com.android.internal.R.drawable.ic_navigation_ring_vibrate;
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return com.android.internal.R.drawable.ic_navigation_ring_silent;
        } else {
            return com.android.internal.R.drawable.ic_navigation_ring_sound_on;
        }
    }

    private static int getTorchDrawableResId(Context context) {
        boolean active = Settings.System.getInt(context.getContentResolver(),
                Settings.System.TORCH_STATE, 0) != 0;

        if (active) {
            return com.android.internal.R.drawable.ic_navigation_ring_torch_on;
        }
        return com.android.internal.R.drawable.ic_navigation_ring_torch_off;
    }

    public static void updateDynamicIconIfNeeded(Context context,
            GlowPadView view, String action, int position) {
        int resourceId = -1;

        if (TextUtils.equals(action, ACTION_VIBRATE)) {
            resourceId = getVibrateDrawableResId(context);
        } else if (TextUtils.equals(action, ACTION_SILENT)) {
            resourceId = getSilentDrawableResId(context);
        } else if (TextUtils.equals(action, ACTION_RING_SILENT_VIBRATE)) {
            resourceId = getRingerDrawableResId(context);
        } else if (TextUtils.equals(action, ACTION_TORCH)) {
            resourceId = getTorchDrawableResId(context);
        }

        if (resourceId > 0) {
            final TargetDrawable drawable = view.getTargetDrawables().get(position);
            drawable.setDrawable(context.getResources(), resourceId);
        }
    }

    public static void swapSearchIconIfNeeded(Context context, GlowPadView view) {
        Intent intent = ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(context, UserHandle.USER_CURRENT);
        if (intent != null) {
            ComponentName component = intent.getComponent();
            if (component != null) {
                view.replaceTargetDrawablesIfPresent(component,
                        ASSIST_ICON_METADATA_NAME,
                        com.android.internal.R.drawable.ic_action_assist_generic);
            }
        }
    }
}
