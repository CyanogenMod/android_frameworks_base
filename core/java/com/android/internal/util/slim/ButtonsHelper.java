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
import android.content.pm.ActivityInfo;
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

    private ButtonsHelper() {
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

        return (getButtonsConfigValues(context, config, null, null, true));
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
            config = setButtonsConfig(buttonsConfig, true);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.NOTIFICATION_SHORTCUTS_CONFIG,
                    config);
    }

    private static ArrayList<ButtonConfig> getButtonsConfigValues(Context context, String config,
                String values, String entries, boolean isShortcut) {
        // init vars to fill with them later the config values
        int counter = 0;
        ArrayList<ButtonConfig> buttonConfigList = new ArrayList<ButtonConfig>();
        ButtonConfig buttonConfig = null;

        PackageManager pm = context.getPackageManager();
        Resources settingsResources = null;
        try {
            settingsResources = pm.getResourcesForApplication(SETTINGS_METADATA_NAME);
        } catch (Exception e) {
            Log.e("ButtonsHelper:", "can't access settings resources",e);
        }

        // Split out the config to work with and add to the list
        for (String configValue : config.split("\\" + ButtonsConstants.ACTION_DELIMITER)) {
            counter++;
            if (counter == 1) {
                buttonConfig = new ButtonConfig(configValue,
                            getProperSummary(pm, settingsResources,
                            configValue, values, entries), null, null, null);
            }
            if (counter == 2) {
                if (isShortcut) {
                    buttonConfig.setIcon(configValue);
                    buttonConfigList.add(buttonConfig);
                    //reset counter due that shortcut iteration of one button is finished
                    counter = 0;
                } else {
                    buttonConfig.setLongpressAction(configValue);
                    buttonConfig.setLongpressActionDescription(
                            getProperSummary(pm, settingsResources,
                            configValue, values, entries));
                }
            }
            if (counter == 3) {
                buttonConfig.setIcon(configValue);
                buttonConfigList.add(buttonConfig);
                //reset counter due that iteration of full config button is finished
                counter = 0;
            }
        }

        return buttonConfigList;
    }

    private static String setButtonsConfig(
            ArrayList<ButtonConfig> buttonsConfig, boolean isShortcut) {
        String finalConfig = "";
        ButtonConfig buttonConfig;

        for (int i = 0; i < buttonsConfig.size(); i++) {
            if (i != 0) {
                finalConfig += ButtonsConstants.ACTION_DELIMITER;
            }
            buttonConfig = buttonsConfig.get(i);
            finalConfig += buttonConfig.getClickAction() + ButtonsConstants.ACTION_DELIMITER;
            if (!isShortcut) {
                finalConfig += buttonConfig.getLongpressAction()
                    + ButtonsConstants.ACTION_DELIMITER;
            }
            finalConfig += buttonConfig.getIcon();
        }

        return finalConfig;
    }

    public static Drawable getButtonIconImage(
            Context context, String clickAction, String customIcon) {
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

    private static String getProperSummary(PackageManager pm,
            Resources settingsResources, String action, String values, String entries) {

        if (pm == null || settingsResources == null || action == null) {
            return null;
        }

        if (values != null && entries != null) {
            int resIdEntries = -1;
            int resIdValues = -1;

            resIdEntries = settingsResources.getIdentifier(
                        SETTINGS_METADATA_NAME + ":array/" + entries, null, null);

            resIdValues = settingsResources.getIdentifier(
                        SETTINGS_METADATA_NAME + ":array/" + values, null, null);

            if (resIdEntries > 0 && resIdValues > 0) {
                try {
                    String[] entriesArray = settingsResources.getStringArray(resIdEntries);
                    String[] valuesArray = settingsResources.getStringArray(resIdValues);
                    for (int i = 0; i < valuesArray.length; i++) {
                        if (action.equals(valuesArray[i])) {
                            return entriesArray[i];
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return getFriendlyNameForUri(pm, action);
    }

    private static String getFriendlyActivityName(
            PackageManager pm, Intent intent, boolean labelOnly) {
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;

        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null && !labelOnly) {
                friendlyName = ai.name;
            }
        }

        return friendlyName != null || labelOnly ? friendlyName : intent.toUri(0);
    }

    private static String getFriendlyShortcutName(PackageManager pm, Intent intent) {
        String activityName = getFriendlyActivityName(pm, intent, true);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    private static String getFriendlyNameForUri(PackageManager pm, String uri) {
        if (uri == null || uri.startsWith("**")) {
            return null;
        }

        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(pm, intent, false);
            }
            return getFriendlyShortcutName(pm, intent);
        } catch (URISyntaxException e) {
        }

        return uri;
    }

}
