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

public class PolicyHelper {

    private static final String SYSTEM_METADATA_NAME = "android";

    // get @ButtonConfig with description if needed and other then an app description
    public static ArrayList<ButtonConfig> getPowerMenuConfigWithDescription(
            Context context, String values, String entries) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.POWER_MENU_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = PolicyConstants.POWER_MENU_CONFIG_DEFAULT;
        }
        return (ConfigSplitHelper.getButtonsConfigValues(context, config, values, entries, true));
    }

    public static void setPowerMenuConfig(Context context,
            ArrayList<ButtonConfig> buttonsConfig, boolean reset) {
        String config;
        if (reset) {
            config = PolicyConstants.POWER_MENU_CONFIG_DEFAULT;
            Settings.System.putInt(context.getContentResolver(),
                Settings.System.POWER_MENU_TEXT_COLOR, -2);
            Settings.System.putInt(context.getContentResolver(),
                Settings.System.POWER_MENU_ICON_COLOR, -2);
            Settings.System.putInt(context.getContentResolver(),
                Settings.System.POWER_MENU_ICON_COLOR_MODE, 0);
        } else {
            config = ConfigSplitHelper.setButtonsConfig(buttonsConfig, true);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.POWER_MENU_CONFIG,
                    config);
    }

    public static Drawable getPowerMenuIconImage(Context context,
            String clickAction, String customIcon, boolean colorize) {
        int resId = -1;
        int iconColor = -2;
        int colorMode = 0;
        Drawable d = null;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        if (colorize) {
            iconColor = Settings.System.getIntForUser(
                    context.getContentResolver(),
                    Settings.System.POWER_MENU_ICON_COLOR, -2,
                    UserHandle.USER_CURRENT);
            colorMode = Settings.System.getIntForUser(
                    context.getContentResolver(),
                    Settings.System.POWER_MENU_ICON_COLOR_MODE, 0,
                    UserHandle.USER_CURRENT);

            if (iconColor == -2) {
                iconColor = context.getResources().getColor(
                    com.android.internal.R.color.power_menu_icon_default_color);
            }
        }

        if (customIcon != null && customIcon.startsWith(PolicyConstants.SYSTEM_ICON_IDENTIFIER)) {
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
                d = systemResources.getDrawable(resId);
                if (colorMode != 3 && colorize) {
                    d = new BitmapDrawable(ColorHelper.getColoredBitmap(d, iconColor));
                }
            }
        } else if (customIcon != null && !customIcon.equals(ButtonsConstants.ICON_EMPTY)) {
            File f = new File(Uri.parse(customIcon).getPath());
            if (f.exists()) {
                d = new BitmapDrawable(context.getResources(), f.getAbsolutePath());
                if (colorMode != 3 && colorMode != 1 && colorize) {
                    d = new BitmapDrawable(ColorHelper.getColoredBitmap(d, iconColor));
                }
            } else {
                Log.e("ButtonsHelper:", "can't access custom icon image");
                return null;
            }
        } else if (clickAction.startsWith("**")) {
            d = getPowerMenuSystemIcon(context, clickAction);
            if (colorMode != 3 && colorize) {
                d = new BitmapDrawable(ColorHelper.getColoredBitmap(d, iconColor));
            }
        } else {
            try {
                d = pm.getActivityIcon(Intent.parseUri(clickAction, 0));
                if (colorMode != 3 && colorMode == 0 && colorize) {
                    d = new BitmapDrawable(ColorHelper.getColoredBitmap(d, iconColor));
                }
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return ColorHelper.resize(context, d, 35);
    }

    private static Drawable getPowerMenuSystemIcon(Context context, String clickAction) {
        if (clickAction.equals(PolicyConstants.ACTION_POWER_OFF)) {
            return context.getResources().getDrawable(
                com.android.internal.R.drawable.ic_lock_power_off);
        } else if (clickAction.equals(PolicyConstants.ACTION_REBOOT)) {
            return context.getResources().getDrawable(
                com.android.internal.R.drawable.ic_lock_reboot);
        } else if (clickAction.equals(PolicyConstants.ACTION_SCREENSHOT)) {
            return context.getResources().getDrawable(
                com.android.internal.R.drawable.ic_lock_screenshot);
        } else if (clickAction.equals(PolicyConstants.ACTION_AIRPLANE)) {
            return context.getResources().getDrawable(
                com.android.internal.R.drawable.ic_lock_airplane_mode_off);
        }
        return null;
    }

}
