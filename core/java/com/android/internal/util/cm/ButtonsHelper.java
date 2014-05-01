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

package com.android.internal.util.cm;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class ButtonsHelper {

    private static final String SYSTEM_METADATA_NAME = "android";
    private static final String SYSTEMUI_METADATA_NAME = "com.android.systemui";
    private static final String SETTINGS_METADATA_NAME = "com.android.settings";

    // Get and set the pie configs from provider and return proper arraylist objects
    // @ButtonConfig
    public static ArrayList<ButtonConfig> getPieConfig(Context context) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getPieProvider(context), null, null, false));
    }

    public static ArrayList<ButtonConfig> getPieConfigWithDescription(
            Context context, String values, String entries) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getPieProvider(context), values, entries, false));
    }

    private static String getPieProvider(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.PIE_BUTTONS_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = NavigationRingConstants.NAVIGATION_CONFIG_DEFAULT;
        }
        return config;
    }

    public static void setPieConfig(Context context,
            ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = NavigationRingConstants.NAVIGATION_CONFIG_DEFAULT;
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, false);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.PIE_BUTTONS_CONFIG,
                    config);
    }

    public static ArrayList<ButtonConfig> getPieSecondLayerConfig(Context context) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getPieSecondLayerProvider(context), null, null, false));
    }

    public static ArrayList<ButtonConfig> getPieSecondLayerConfigWithDescription(
            Context context, String values, String entries) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getPieSecondLayerProvider(context), values, entries, false));
    }

    private static String getPieSecondLayerProvider(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.PIE_BUTTONS_CONFIG_SECOND_LAYER,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = NavigationRingConstants.PIE_SECOND_LAYER_CONFIG_DEFAULT;
        }
        return config;
    }

    public static void setPieSecondLayerConfig(Context context,
            ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = NavigationRingConstants.PIE_SECOND_LAYER_CONFIG_DEFAULT;
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, false);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.PIE_BUTTONS_CONFIG_SECOND_LAYER,
                    config);
    }

    public static Drawable getButtonIconImage(Context context,
            String clickAction, String customIcon) {
        int resId = -1;
        Drawable d = null;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        Resources systemUiResources;
        try {
            systemUiResources = pm.getResourcesForApplication(SYSTEMUI_METADATA_NAME);
        } catch (Exception e) {
            Log.e("ButtonsHelper:", "can't access systemui resources",e);
            return null;
        }

        if (!clickAction.startsWith("**")) {
            try {
                d = pm.getActivityIcon(Intent.parseUri(clickAction, 0));
            } catch (NameNotFoundException e) {
                resId = systemUiResources.getIdentifier(
                    SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_null", null, null);
                if (resId > 0) {
                    d = systemUiResources.getDrawable(resId);
                    return d;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        if (customIcon != null && customIcon.startsWith(NavigationRingConstants.SYSTEM_ICON_IDENTIFIER)) {
            resId = systemUiResources.getIdentifier(customIcon.substring(
                        NavigationRingConstants.SYSTEM_ICON_IDENTIFIER.length()), "drawable", "android");
            if (resId > 0) {
                return systemUiResources.getDrawable(resId);
            }
        } else if (customIcon != null && !customIcon.equals(NavigationRingConstants.ICON_EMPTY)) {
            File f = new File(Uri.parse(customIcon).getPath());
            if (f.exists()) {
                return new BitmapDrawable(context.getResources(),
                    ImageHelper.getRoundedCornerBitmap(
                        new BitmapDrawable(context.getResources(),
                        f.getAbsolutePath()).getBitmap()));
            } else {
                Log.e("ButtonsHelper:", "can't access custom icon image");
                return null;
            }
        } else if (clickAction.startsWith("**")) {
            resId = getButtonsSystemIcon(systemUiResources, clickAction);

            if (resId > 0) {
                return systemUiResources.getDrawable(resId);
            }
        }
        return d;
    }

    private static int getButtonsSystemIcon(Resources systemUiResources, String clickAction) {
        int resId = -1;

        if (clickAction.equals(NavigationRingConstants.ACTION_HOME)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_home", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_BACK)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_back", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_RECENTS)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_recent", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_SEARCH)
                || clickAction.equals(NavigationRingConstants.ACTION_ASSIST)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_search", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_KEYGUARD_SEARCH)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/search_light", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_SCREENSHOT)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_screenshot", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_MENU)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_menu", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_MENU_BIG)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_menu_big", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_IME)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_ime_switcher", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_EXPANDED_DESKTOP)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_expanded_desktop", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_KILL)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_killtask", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_POWER)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_power", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_NOTIFICATIONS)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_notifications", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_VIBRATE)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_vib", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_SILENT)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_silent", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_RING_SILENT_VIBRATE)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_ring_vib_silent", null, null);
        } else if (clickAction.equals(NavigationRingConstants.ACTION_TORCH)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_torch", null, null);
        } else {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_null", null, null);
        }
        return resId;
    }

}
