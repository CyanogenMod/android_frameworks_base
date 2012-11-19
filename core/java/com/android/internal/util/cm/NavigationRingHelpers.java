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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.cm.NavigationRingConstants.*;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.net.URISyntaxException;

public class NavigationRingHelpers {
    public static final int MAX_ACTIONS = 3;

    private NavigationRingHelpers() {
    }

    public static String[] getTargetActions(Context context) {
        final ContentResolver cr = context.getContentResolver();
        final String[] result = new String[MAX_ACTIONS];

        for (int i = 0; i < MAX_ACTIONS; i++) {
            result[i] = Settings.System.getString(cr, Settings.System.NAVIGATION_RING_TARGETS[i]);
        }
        if (TextUtils.isEmpty(result[1])) {
            resetActionsToDefaults(context);
            result[1] = ACTION_ASSIST;
        }

        return result;
    }

    public static void resetActionsToDefaults(Context context) {
        final ContentResolver cr = context.getContentResolver();
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[0], null);
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[1], ACTION_ASSIST);
        Settings.System.putString(cr, Settings.System.NAVIGATION_RING_TARGETS[2], null);
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NONE)) {
            resourceId = com.android.internal.R.drawable.ic_navigation_ring_blank;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            resourceId = com.android.internal.R.drawable.ic_action_screenshot;
        } else if (action.equals(ACTION_IME)) {
            resourceId = com.android.internal.R.drawable.ic_action_ime_switcher;
        } else if (action.equals(ACTION_VIBRATE)) {
            resourceId = com.android.internal.R.drawable.ic_action_vibrate;
        } else if (action.equals(ACTION_SILENT)) {
            resourceId = com.android.internal.R.drawable.ic_action_silent;
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            resourceId = com.android.internal.R.drawable.ic_action_ring_vibrate_silent;
        } else if (action.equals(ACTION_KILL)) {
            resourceId = com.android.internal.R.drawable.ic_action_killtask;
        } else if (action.equals(ACTION_POWER)) {
            resourceId = com.android.internal.R.drawable.ic_action_power;
        } else if (action.equals(ACTION_ASSIST)) {
            resourceId = com.android.internal.R.drawable.ic_action_assist_generic;
        }

        if (resourceId < 0) {
            // no pre-defined action, try to resolve URI
            try {
                Intent intent = Intent.parseUri(action, 0);
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                Drawable activityIcon = info.loadIcon(pm);
                Drawable iconBg = res.getDrawable(
                        com.android.internal.R.drawable.ic_navigation_ring_blank);
                Drawable iconBgActivated = res.getDrawable(
                        com.android.internal.R.drawable.ic_navigation_ring_blank_activated);

                int margin = (int)(iconBg.getIntrinsicHeight() / 3);
                LayerDrawable icon = new LayerDrawable (new Drawable[] {iconBg, activityIcon});
                LayerDrawable iconActivated = new LayerDrawable (new Drawable[] {iconBgActivated, activityIcon});

                icon.setLayerInset(1, margin, margin, margin, margin);
                iconActivated.setLayerInset(1, margin, margin, margin, margin);

                StateListDrawable selector = new StateListDrawable();
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        -android.R.attr.state_focused
                    }, icon);
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        android.R.attr.state_active,
                        -android.R.attr.state_focused
                    }, iconActivated);
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        android.R.attr.state_focused
                    }, iconActivated);
                return new TargetDrawable(res, selector);
            } catch (URISyntaxException e) {
                resourceId = com.android.internal.R.drawable.ic_navigation_ring_blank;
            }
        }

        TargetDrawable drawable = new TargetDrawable(res, res.getDrawable(resourceId));
        if (resourceId == com.android.internal.R.drawable.ic_navigation_ring_blank) {
            drawable.setEnabled(false);
        }

        return drawable;
    }
}
