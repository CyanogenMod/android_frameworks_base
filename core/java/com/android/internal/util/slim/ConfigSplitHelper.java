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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import java.util.ArrayList;

public class ConfigSplitHelper {

    private static final String SETTINGS_METADATA_NAME = "com.android.settings";

    public static ArrayList<ButtonConfig> getButtonsConfigValues(Context context, String config,
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
                            AppHelper.getProperSummary(context, pm, settingsResources,
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
                            AppHelper.getProperSummary(context, pm, settingsResources,
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

    public static String setButtonsConfig(
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

}
