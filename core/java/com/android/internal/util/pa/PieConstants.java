/*
* Copyright (C) 2015 Brett Rogers (CyanideL)
*
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

public class PieConstants {

    public static final String BACK_BUTTON = "##back##";
    public static final String HOME_BUTTON = "##home##";
    public static final String MENU_BUTTON = "##menu##";
    public static final String MENU_BIG_BUTTON = "**menu_big**";
    public static final String RECENT_BUTTON = "##recent##";
    public static final String LAST_APP_BUTTON = "##lastapp##";
    public static final String KILL_TASK_BUTTON = "##killtask##";
    public static final String NOTIFICATIONS_BUTTON = "##notifications##";
    public static final String SETTINGS_PANEL_BUTTON = "##settings_panel##";
    public static final String SCREENSHOT_BUTTON = "##screenshot##";
    public static final String THEME_SWITCH_BUTTON = "##theme_switch##";

    // no action
    public static final String NULL_BUTTON            = "**null**";

    // this shorcut constant is only used to identify if the user
    // selected in settings a custom app...after it is chosen intent uri
    // is saved in the ActionConfig object
    public static final String APP_BUTTON          = "**app**";

    public static final String ICON_EMPTY = "empty";
    public static final String SYSTEM_ICON_IDENTIFIER = "system_shortcut=";
    public static final String DELIMITER_BUTTON = "|";

    public static final String PA_PIE_CONFIG_DEFAULT =
          BACK_BUTTON + DELIMITER_BUTTON
        + HOME_BUTTON + DELIMITER_BUTTON
        + RECENT_BUTTON + DELIMITER_BUTTON
        + NULL_BUTTON + DELIMITER_BUTTON
        + NULL_BUTTON + DELIMITER_BUTTON
        + NULL_BUTTON + DELIMITER_BUTTON
        + ICON_EMPTY;

}
