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

    // get and set the navbar configs from provider and return propper arraylist objects
    // @ButtonConfig
    public static ArrayList<ButtonConfig> getNavBarConfig(Context context) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getNavBarProvider(context), null, null, false));
    }

    // get @ButtonConfig with description if needed and other then an app description
    public static ArrayList<ButtonConfig> getNavBarConfigWithDescription(
            Context context, String values, String entries) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getNavBarProvider(context), values, entries, false));
    }

    private static String getNavBarProvider(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = ButtonsConstants.NAVIGATION_CONFIG_DEFAULT;
        }
        return config;
    }

    public static void setNavBarConfig(Context context,
            ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = ButtonsConstants.NAVIGATION_CONFIG_DEFAULT;
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, false);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_CONFIG,
                    config);
    }

    // get and set the navring configs from provider and return propper arraylist objects
    // @ButtonConfig
    public static ArrayList<ButtonConfig> getNavRingConfig(Context context) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getNavRingProvider(context), null, null, false));
    }

    public static ArrayList<ButtonConfig> getNavRingConfigWithDescription(
            Context context, String values, String entries) {
        return (ConfigSplitHelper.getButtonsConfigValues(context,
            getNavRingProvider(context), values, entries, false));
    }

    private static String getNavRingProvider(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.NAVRING_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = ButtonsConstants.NAV_RING_CONFIG_DEFAULT;
        }
        return config;
    }

    public static void setNavRingConfig(Context context,
            ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = ButtonsConstants.NAV_RING_CONFIG_DEFAULT;
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, false);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.NAVRING_CONFIG,
                    config);
    }

    // get and set the notification shortcut configs
    // from provider and return propper arraylist objects
    // @ButtonConfig
    public static ArrayList<ButtonConfig> getNotificationsShortcutConfig(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.NOTIFICATION_SHORTCUTS_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = "";
        }

        return (ConfigSplitHelper.getButtonsConfigValues(context, config, null, null, true));
    }

    public static void setNotificationShortcutConfig(
            Context context, ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = "";
            Settings.System.putInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_SHORTCUTS_COLOR, -2);
            Settings.System.putInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_SHORTCUTS_COLOR_MODE, 0);
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, true);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.NOTIFICATION_SHORTCUTS_CONFIG,
                    config);
    }

    public static Drawable getButtonIconImage(Context context,
            String clickAction, String customIcon) {
        int resId = -1;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        if (customIcon != null && customIcon.startsWith(ButtonsConstants.SYSTEM_ICON_IDENTIFIER)) {
            Resources systemResources;
            try {
                systemResources = pm.getResourcesForApplication(SYSTEM_METADATA_NAME);
            } catch (Exception e) {
                Log.e("ButtonsHelper:", "can't access system resources",e);
                return null;
            }

            resId = systemResources.getIdentifier(customIcon.substring(
                        ButtonsConstants.SYSTEM_ICON_IDENTIFIER.length()), "drawable", "android");
            if (resId > 0) {
                return systemResources.getDrawable(resId);
            }
        } else if (customIcon != null && !customIcon.equals(ButtonsConstants.ICON_EMPTY)) {
            File f = new File(Uri.parse(customIcon).getPath());
            if (f.exists()) {
                return new BitmapDrawable(context.getResources(), f.getAbsolutePath());
            } else {
                Log.e("ButtonsHelper:", "can't access custom icon image");
                return null;
            }
        } else if (clickAction.startsWith("**")) {
            Resources systemUiResources;
            try {
                systemUiResources = pm.getResourcesForApplication(SYSTEMUI_METADATA_NAME);
            } catch (Exception e) {
                Log.e("ButtonsHelper:", "can't access systemui resources",e);
                return null;
            }

            resId = getButtonsSystemIcon(systemUiResources, clickAction);

            if (resId > 0) {
                try {
                    return systemUiResources.getDrawable(resId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                return pm.getActivityIcon(Intent.parseUri(clickAction, 0));
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static int getButtonsSystemIcon(Resources systemUiResources, String clickAction) {
        int resId = -1;

        if (clickAction.equals(ButtonsConstants.ACTION_HOME)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_home", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_BACK)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_back", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_RECENTS)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_recent", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_SEARCH)
                || clickAction.equals(ButtonsConstants.ACTION_ASSIST)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_search", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_SCREENSHOT)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_screenshot", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_MENU)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_menu", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_IME)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_ime_switcher", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_KILL)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_killtask", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_POWER)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_power", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_POWER_MENU)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_power_menu", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_NOTIFICATIONS)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_notifications", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_LAST_APP)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_lastapp", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_QS)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_qs", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_VIB)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_vib", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_SILENT)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_silent", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_VIB_SILENT)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_ring_vib_silent", null, null);
        } else if (clickAction.equals(ButtonsConstants.ACTION_TORCH)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_torch", null, null);
        } else {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_null", null, null);
        }
        return resId;
    }

}
