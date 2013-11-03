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
import android.os.UserHandle;
import android.provider.Settings;

import java.util.ArrayList;

public class PolicyHelper {

    private PolicyHelper() {
    }

    public static String[] getPowerMenuConfig(Context context) {
        return (getPowerMenuValues(context, getPowerMenuProvider(context)));
    }

    private static String getPowerMenuProvider(Context context) {
        String config = Settings.System.getStringForUser(
                    context.getContentResolver(),
                    Settings.System.POWER_MENU_CONFIG,
                    UserHandle.USER_CURRENT);
        if (config == null) {
            config = PolicyConstants.POWER_MENU_CONFIG_DEFAULT;
        }
        return config;
    }

    public static void setNavBarConfig(Context context,
            ArrayList<String> powerMenuConfig, boolean reset) {
        String config;
        if (reset) {
            config = PolicyConstants.POWER_MENU_CONFIG_DEFAULT;
        } else {
            config = getPowerMenuProvider(context);
        }
        Settings.System.putString(context.getContentResolver(),
                    Settings.System.POWER_MENU_CONFIG, config);
    }

    public static String[] getPowerMenuValues(Context context, String config) {
        return config.split("\\" + PolicyConstants.ACTION_DELIMITER);
    }
}
