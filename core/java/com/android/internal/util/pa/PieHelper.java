/*
* Copyright (C) 2013-2015 ParanoidAndroid Project
* Portions Copyright (C) 2015 Brett Rogers (CyanideL)
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

package com.android.internal.util.pa;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class PieHelper {

    private static final String SYSTEM_METADATA_NAME = "android";
    private static final String SYSTEMUI_METADATA_NAME = "com.android.systemui";
    private static final String SETTINGS_METADATA_NAME = "com.android.settings";

    public static ArrayList<ActionConfig> getPaPieConfigWithDescription(
            Context context, String values, String entries) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.PA_PIE_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = PieConstants.PA_PIE_CONFIG_DEFAULT;
        }
        return ConfigSplitHelper.getActionConfigValues(context, config, values, entries, true);
    }

    public static void setPaPieConfig(Context context,
            ArrayList<ActionConfig> actionConfig, boolean reset) {
        String config;
        if (reset) {
            config = PieConstants.PA_PIE_CONFIG_DEFAULT;
        } else {
            config = ConfigSplitHelper.setActionConfig(actionConfig, true);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.PA_PIE_CONFIG,
                    config);
    }

    // General methods to retrieve the correct icon for the respective action.
    public static Drawable getActionIconImage(Context context,
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
                String extraIconPath = clickAction.replaceAll(".*?hasExtraIcon=", "");
                if (extraIconPath != null && !extraIconPath.isEmpty()) {
                    File f = new File(Uri.parse(extraIconPath).getPath());
                    if (f.exists()) {
                        d = new BitmapDrawable(context.getResources(),
                                f.getAbsolutePath());
                    }
                }
                if (d == null) {
                    d = pm.getActivityIcon(Intent.parseUri(clickAction, 0));
                }
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

        if (customIcon != null && customIcon.startsWith(PieConstants.SYSTEM_ICON_IDENTIFIER)) {
            resId = systemUiResources.getIdentifier(customIcon.substring(
                        PieConstants.SYSTEM_ICON_IDENTIFIER.length()), "drawable", "android");
            if (resId > 0) {
                return systemUiResources.getDrawable(resId);
            }
        } else if (customIcon != null && !customIcon.equals(PieConstants.ICON_EMPTY)) {
            File f = new File(Uri.parse(customIcon).getPath());
            if (f.exists()) {
                return new BitmapDrawable(context.getResources(),
                    ImageHelper.getRoundedCornerBitmap(
                        new BitmapDrawable(context.getResources(),
                        f.getAbsolutePath()).getBitmap()));
            } else {
                Log.e("ActionHelper:", "can't access custom icon image");
                return null;
            }
        } else if (clickAction.startsWith("**")) {
            resId = getActionSystemIcon(systemUiResources, clickAction);

            if (resId > 0) {
                return systemUiResources.getDrawable(resId);
            }
        }
        return d;
    }

    public static int getActionIconUri(Context context,
            String clickAction, String customIcon) {
        int resId = -1;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return resId;
        }

        Resources systemUiResources;
        try {
            systemUiResources = pm.getResourcesForApplication(SYSTEMUI_METADATA_NAME);
        } catch (Exception e) {
            Log.e("ButtonsHelper:", "can't access systemui resources",e);
            return resId;
        }

        if (customIcon != null && customIcon.startsWith(PieConstants.SYSTEM_ICON_IDENTIFIER)) {
            resId = systemUiResources.getIdentifier(customIcon.substring(
                        PieConstants.SYSTEM_ICON_IDENTIFIER.length()), "drawable", "android");
        } else if (clickAction.startsWith("**")) {
            resId = getActionSystemIcon(systemUiResources, clickAction);
        }

        return resId;
    }

    private static int getActionSystemIcon(Resources systemUiResources, String clickAction) {
        int resId = -1;

        // ToDo: Add the resources to SystemUI.
        if (clickAction.equals(PieConstants.HOME_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_home", null, null);
        } else if (clickAction.equals(PieConstants.BACK_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_back", null, null);
        } else if (clickAction.equals(PieConstants.RECENT_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_recent", null, null);
        } else if (clickAction.equals(PieConstants.MENU_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_menu", null, null);
        } else if (clickAction.equals(PieConstants.MENU_BIG_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_menu_big", null, null);
        } else if (clickAction.equals(PieConstants.LAST_APP_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_lastapp", null, null);
        } else if (clickAction.equals(PieConstants.KILL_TASK_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_killtask", null, null);
        } else if (clickAction.equals(PieConstants.NOTIFICATIONS_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_notifications", null, null);
        } else if (clickAction.equals(PieConstants.SETTINGS_PANEL_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_quicksettings", null, null);
        } else if (clickAction.equals(PieConstants.SCREENSHOT_BUTTON)) {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_screenshot", null, null);
        } else {
            resId = systemUiResources.getIdentifier(
                        SYSTEMUI_METADATA_NAME + ":drawable/ic_sysbar_null", null, null);
        }
        return resId;
    }

}
