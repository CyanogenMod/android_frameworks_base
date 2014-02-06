/*
 * Copyright (C) 2013 AOKP by Mike Wilson - Zaphod-Beeblebrox && Steve Spear - Stevespear426
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

public class NavigationRingConstants {
    public static final String ACTION_HOME                 = "**home**";
    public static final String ACTION_BACK                 = "**back**";
    public static final String ACTION_SEARCH               = "**search**";
    public static final String ACTION_MENU                 = "**menu**";
    public static final String ACTION_MENU_BIG             = "**menu_big**";
    public static final String ACTION_POWER                = "**power**";
    public static final String ACTION_NOTIFICATIONS        = "**notifications**";
    public static final String ACTION_RECENTS              = "**recents**";
    public static final String ACTION_SCREENSHOT           = "**screenshot**";
    public static final String ACTION_IME                  = "**ime**";
    public static final String ACTION_KILL                 = "**kill**";
    public static final String ACTION_ASSIST               = "**assist**";
    public static final String ACTION_VIBRATE              = "**ringVibrate**";
    public static final String ACTION_SILENT               = "**ringSilent**";
    public static final String ACTION_RING_SILENT_VIBRATE  = "**ringVibrateSilent**";
    public static final String ACTION_TORCH                = "**torch**";
    public static final String ACTION_EXPANDED_DESKTOP     = "**expanded_desktop**";
    public static final String ACTION_KEYGUARD_SEARCH      = "**keyguard_search**";
    public static final String ACTION_NONE                 = "**none**";


    // no action
    public static final String ACTION_NULL            = "**null**";

    // this shorcut constant is only used to identify if the user
    // selected in settings a custom app...after it is choosed intent uri
    // is saved in the ButtonConfig object
    public static final String ACTION_APP          = "**app**";

    public static final String ICON_EMPTY = "empty";
    public static final String SYSTEM_ICON_IDENTIFIER = "system_shortcut=";
    public static final String ACTION_DELIMITER = "|";

    public static final String NAVIGATION_CONFIG_DEFAULT =
          ACTION_BACK    + ACTION_DELIMITER
        + ACTION_NULL    + ACTION_DELIMITER
        + ICON_EMPTY     + ACTION_DELIMITER
        + ACTION_HOME    + ACTION_DELIMITER
        + ACTION_NULL    + ACTION_DELIMITER
        + ICON_EMPTY     + ACTION_DELIMITER
        + ACTION_RECENTS + ACTION_DELIMITER
        + ACTION_NULL    + ACTION_DELIMITER
        + ICON_EMPTY;

    public static final String PIE_SECOND_LAYER_CONFIG_DEFAULT =
          ACTION_POWER         + ACTION_DELIMITER
        + ACTION_NULL          + ACTION_DELIMITER
        + ICON_EMPTY           + ACTION_DELIMITER
        + ACTION_NOTIFICATIONS + ACTION_DELIMITER
        + ACTION_NULL          + ACTION_DELIMITER
        + ICON_EMPTY           + ACTION_DELIMITER
        + ACTION_SEARCH        + ACTION_DELIMITER
        + ACTION_NULL          + ACTION_DELIMITER
        + ICON_EMPTY           + ACTION_DELIMITER
        + ACTION_SCREENSHOT    + ACTION_DELIMITER
        + ACTION_NULL          + ACTION_DELIMITER
        + ICON_EMPTY           + ACTION_DELIMITER
        + ACTION_IME           + ACTION_DELIMITER
        + ACTION_NULL          + ACTION_DELIMITER
        + ICON_EMPTY;
}
