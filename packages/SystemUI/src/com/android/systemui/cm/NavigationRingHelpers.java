/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import com.android.systemui.R;

import java.net.URISyntaxException;

import static com.android.systemui.cm.NavigationRingConstants.*;

public class NavigationRingHelpers {
    public static final int MAX_ACTIONS = 3;

    private static final String ASSIST_ICON_METADATA_NAME =
            "com.android.systemui.action_assist_icon";

    private static final IntentFilter TORCH_STATE_FILTER =
            new IntentFilter(TorchConstants.ACTION_STATE_CHANGED);

    private NavigationRingHelpers() {
        // Do nothing here
    }

    public static String[] getTargetActions(Context context) {
        final ContentResolver cr = context.getContentResolver();
        final String[] result = new String[MAX_ACTIONS];
        boolean isDefault = true;

        for (int i = 0; i < MAX_ACTIONS; i++) {
            result[i] = Settings.System.getString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[i]);
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
        Settings.System.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[0], null);
        Settings.System.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[1], ACTION_ASSIST);
        Settings.System.putString(cr, Settings.Secure.NAVIGATION_RING_TARGETS[2], null);
    }

    public static boolean isAssistantAvailable(Context context) {
        return ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(context, true, UserHandle.USER_CURRENT) != null;
    }

    // TODO WAIT TILL ROMANS STUFF IS MERGED
    public static boolean isTorchAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(TorchConstants.APP_PACKAGE_NAME, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            // Ignored, just catched so we can return false below
        }

        return false;
    }

    public static Drawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NONE)) {
            return null;
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
//
//    private static TargetDrawable createDrawableForActivity(Resources res, Drawable activityIcon) {
//        Drawable iconBg = res.getDrawable(
//                com.android.internal.R.drawable.ic_navigation_ring_blank_normal);
//        Drawable iconBgActivated = res.getDrawable(
//                com.android.internal.R.drawable.ic_navigation_ring_blank_activated);
//
//        // Get the size for the base icon and the activity icon
//        int iconSize = (int) res.getDimension(
//                com.android.internal.R.dimen.navigation_ring_icon_size);
//        int activityIconSize = (int) res.getDimension(
//                com.android.internal.R.dimen.navigation_ring_activity_icon_size);
//        int margin = (iconSize - activityIconSize) / 2;
//
//        // Create the grayscale color filter
//        ColorMatrix colorMatrix = new ColorMatrix();
//        colorMatrix.setSaturation(0);
//        ColorMatrixColorFilter grayscaleFilter = new ColorMatrixColorFilter(colorMatrix);
//
//        // Create the final bitmap for the icon
//        Bitmap scaledActivityIconBitmap =
//                Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
//        Canvas canvas = new Canvas(scaledActivityIconBitmap);
//        activityIcon.setBounds(margin, margin, iconSize - margin, iconSize - margin);
//        activityIcon.setColorFilter(grayscaleFilter);
//        activityIcon.draw(canvas);
//
//        // Create the final drawable from the new generated bitmap
//        BitmapDrawable scaledActivityIcon = new BitmapDrawable(res, scaledActivityIconBitmap);
//
//        LayerDrawable icon = new LayerDrawable(
//                new Drawable[] { iconBg, scaledActivityIcon });
//        LayerDrawable iconActivated = new LayerDrawable(
//                new Drawable[] { iconBgActivated, scaledActivityIcon });
//
//        StateListDrawable selector = new StateListDrawable();
//        selector.addState(TargetDrawable.STATE_INACTIVE, icon);
//        selector.addState(TargetDrawable.STATE_ACTIVE, iconActivated);
//        selector.addState(TargetDrawable.STATE_FOCUSED, iconActivated);
//
//        return new TargetDrawable(res, selector);
//    }

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
        Intent stateIntent = context.registerReceiver(null, TORCH_STATE_FILTER);
        boolean active = stateIntent != null
                && stateIntent.getIntExtra(TorchConstants.EXTRA_CURRENT_STATE, 0) != 0;

        if (active) {
            return R.drawable.ic_navigation_ring_torch_on;
        }

        return R.drawable.ic_navigation_ring_torch_off;
    }

//    public static boolean hasLockscreenTargets(Context context) {
//        final String[] result = new String[NavigationRingHelpers.MAX_ACTIONS];
//        for (int i = 0; i < result.length; i++) {
//            String targetValue = Settings.System.getString(context.getContentResolver(),
//                    Settings.System.NAVIGATION_RING_TARGETS[i]);
//            if (!TextUtils.equals(targetValue, NavigationRingConstants.ACTION_NONE)) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    public static void updateDynamicIconIfNeeded(Context context,
//                                                 GlowPadView view, String action, int position) {
//        int resourceId = -1;
//
//        if (TextUtils.equals(action, ACTION_VIBRATE)) {
//            resourceId = getVibrateDrawableResId(context);
//        } else if (TextUtils.equals(action, ACTION_SILENT)) {
//            resourceId = getSilentDrawableResId(context);
//        } else if (TextUtils.equals(action, ACTION_RING_SILENT_VIBRATE)) {
//            resourceId = getRingerDrawableResId(context);
//        } else if (TextUtils.equals(action, ACTION_TORCH)) {
//            resourceId = getTorchDrawableResId(context);
//        }
//
//        if (resourceId > 0) {
//            final TargetDrawable drawable = view.getTargetDrawables().get(position);
//            drawable.setDrawable(context.getResources(), resourceId);
//        }
//    }
//
//    public static void swapSearchIconIfNeeded(Context context, GlowPadView view) {
//        Intent intent = ((SearchManager) context.getSystemService(Context.SEARCH_SERVICE))
//                .getAssistIntent(context, true, UserHandle.USER_CURRENT);
//        if (intent != null) {
//            ComponentName component = intent.getComponent();
//            if (component != null) {
//                boolean replaced = view.replaceTargetDrawablesIfPresent(component,
//                        ASSIST_ICON_METADATA_NAME,
//                        com.android.internal.R.drawable.ic_action_assist_generic);
//                if (replaced) {
//                    addFocusedStateToSearchIconIfMissing(context, view);
//                }
//            }
//        }
//    }
//
//    private static void addFocusedStateToSearchIconIfMissing(Context context, GlowPadView view) {
//        for (TargetDrawable target : view.getTargetDrawables()) {
//            if (target != null && target.getResourceId() ==
//                    com.android.internal.R.drawable.ic_action_assist_generic) {
//                Drawable drawable = target.getDrawable();
//                if (drawable instanceof StateListDrawable) {
//                    StateListDrawable d = (StateListDrawable) drawable;
//                    int inActiveIndex = d.getStateDrawableIndex(TargetDrawable.STATE_INACTIVE);
//                    int activeIndex = d.getStateDrawableIndex(TargetDrawable.STATE_ACTIVE);
//                    if (inActiveIndex != -1 && activeIndex != -1) {
//                        StateListDrawable selector = new StateListDrawable();
//                        selector.addState(TargetDrawable.STATE_INACTIVE,
//                                d.getStateDrawable(inActiveIndex));
//                        selector.addState(TargetDrawable.STATE_ACTIVE,
//                                d.getStateDrawable(activeIndex));
//                        selector.addState(TargetDrawable.STATE_FOCUSED,
//                                d.getStateDrawable(activeIndex));
//                        target.setDrawable(selector);
//                    }
//                }
//                break;
//            }
//        }
//    }
}