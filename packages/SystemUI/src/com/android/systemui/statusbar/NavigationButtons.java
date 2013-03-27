/*
 * Copyright (C) 2013 The CyanogenMod Project
 * This code is loosely based on portions of the ParanoidAndroid Project source, Copyright (C) 2012.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar;

import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;

import com.android.systemui.R;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class that holds the global information about supported navigation buttons
 * in CyanogenMod.
 */
public class NavigationButtons {

    /**
     * Defines how many keys / key slots there may be for navigation.
     * <b>WARNING</b> If you ever change this, don't forget check the source in
     * {@code /phone/NavBarEdit.java} and {/pie/PieController.java}
     */
    public static final int BUTTON_COUNT = 6;
    /**
     * Defines the weight of each of the 6 keys. For most implementations this is
     * hard-coded within a resource file.
     */
    public static final boolean BUTTON_IS_SMALL[] = { true, false, false, false, false, true };

    /**
     * Class to store the information about supported buttons
     */
    public static final class ButtonInfo {
        public final int displayId;
        public final int contentDescription;
        public final int keyCode;
        public final int portResource;
        public final int landResource;
        public final int sideResource;
        private final String key;

        /**
         * Constructor for new button type
         * @param rId - resource id of text shown to user in choose dialog
         * @param cD  - accessibility information regarding button
         * @param mC  - keyCode to execute on button press
         * @param pR  - portrait resource used to display button
         * @param lR  - landscape resource used to display button
         * @param sR  - smaller scaled resource for side buttons
         */
        public ButtonInfo(int rId, int cD, int mC, int pR, int lR, int sR) {
            displayId = rId;
            contentDescription = cD;
            keyCode = mC;
            portResource = pR;
            landResource = lR;
            sideResource = sR;
            key = "";
        }
        /**
         * Constructor for new button type
         * @param rId - resource id of text shown to user in choose dialog
         * @param cD  - accessibility information regarding button
         * @param mC  - keyCode to execute on button press
         * @param pR  - portrait resource used to display button
         * @param lR  - landscape resource used to display button
         * @param sR  - smaller scaled resource for side buttons
         * @param key - the internal key of the button
         */
        public ButtonInfo(int rId, int cD, int mC, int pR, int lR, int sR, String key) {
            displayId = rId;
            contentDescription = cD;
            keyCode = mC;
            portResource = pR;
            landResource = lR;
            sideResource = sR;
            this.key = key;
        }
    }

    // Available buttons string constants
    private static final String KEY_EMPTY = "empty";
    private static final String KEY_HOME = "home";
    private static final String KEY_BACK = "back";
    private static final String KEY_SEARCH = "search";
    private static final String KEY_RECENT = "recent";
    private static final String KEY_CONDITIONAL_MENU = "menu0";
    private static final String KEY_ALWAYS_MENU = "menu1";
    private static final String KEY_MENU_BIG = "menu2";

    private static final String KEY_DEFAULT_SETTING = "empty|back|home|recent|empty|menu0";

    // All navigation button information CyanogenMod needs
    public static final ButtonInfo HOME = new ButtonInfo(
            R.string.navbar_home_button,
            R.string.accessibility_home, KeyEvent.KEYCODE_HOME, R.drawable.ic_sysbar_home,
            R.drawable.ic_sysbar_home_land, R.drawable.ic_sysbar_home, KEY_HOME);
    public static final ButtonInfo CONDITIONAL_MENU = new ButtonInfo(
            R.string.navbar_menu_conditional_button,
            R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
            R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu, KEY_CONDITIONAL_MENU);
    public static final ButtonInfo ALWAYS_MENU = new ButtonInfo(
            R.string.navbar_menu_always_button,
            R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
            R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu, KEY_ALWAYS_MENU);
    public static final ButtonInfo MENU_BIG = new ButtonInfo(
            R.string.navbar_menu_big_button,
            R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu_big,
            R.drawable.ic_sysbar_menu_big_land, 0, KEY_MENU_BIG);
    public static final ButtonInfo BACK =  new ButtonInfo(
            R.string.navbar_back_button, R.string.accessibility_back,
            KeyEvent.KEYCODE_BACK, R.drawable.ic_sysbar_back,
            R.drawable.ic_sysbar_back_land, R.drawable.ic_sysbar_back_side, KEY_BACK);
    public static final ButtonInfo SEARCH = new ButtonInfo(
            R.string.navbar_search_button,
            R.string.accessibility_back, KeyEvent.KEYCODE_SEARCH, R.drawable.ic_sysbar_search,
            R.drawable.ic_sysbar_search_land, R.drawable.ic_sysbar_search_side, KEY_SEARCH);
    public static final ButtonInfo RECENT =  new ButtonInfo(
            R.string.navbar_recent_button,
            R.string.accessibility_recent, 0, R.drawable.ic_sysbar_recent,
            R.drawable.ic_sysbar_recent_land, R.drawable.ic_sysbar_recent_side, KEY_RECENT);
    public static final ButtonInfo EMPTY = new ButtonInfo(
            R.string.navbar_empty_button,
            R.string.accessibility_clear_all, 0, R.drawable.ic_sysbar_add,
            R.drawable.ic_sysbar_add_land, R.drawable.ic_sysbar_add_side, KEY_EMPTY);


    /**
     * Map which holds references to supported/available buttons. This is a unmodifiable map.
     */
    public static final Map<String, ButtonInfo> BUTTON_MAP;

    static {
        Map<String, ButtonInfo> temp = new LinkedHashMap<String,ButtonInfo>();
        temp.put(KEY_HOME, HOME);
        temp.put(KEY_CONDITIONAL_MENU, CONDITIONAL_MENU);
        temp.put(KEY_ALWAYS_MENU, ALWAYS_MENU);
        temp.put(KEY_MENU_BIG, MENU_BIG);
        temp.put(KEY_BACK, BACK);
        temp.put(KEY_SEARCH, SEARCH);
        temp.put(KEY_RECENT, RECENT);
        temp.put(KEY_EMPTY, EMPTY);
        BUTTON_MAP = Collections.unmodifiableMap(temp);
    }

    /**
     * Retrieves the button configuration from the settings.
     * @return the current button map, or the default button map.
     */
    public static ButtonInfo[] loadButtonMap(Context context) {
        String saved = Settings.System.getString(context.getContentResolver(),
                Settings.System.NAV_BUTTONS);
        if (saved == null) {
            saved = NavigationButtons.KEY_DEFAULT_SETTING;
        }
        String[] buttons = saved.split("\\|");
        if (buttons.length < BUTTON_COUNT) {
            buttons = NavigationButtons.KEY_DEFAULT_SETTING.split("\\|");
        }

        ButtonInfo[] result = new ButtonInfo[6];
        for (int i = 0; i < result.length; i++) {
            result[i] = BUTTON_MAP.get(buttons[i]);
            if (result[i] == null) {
                result[i] = EMPTY;
            }
        }
        return result;
    }

    public static void storeButtonMap(Context context, ButtonInfo[] map) {
        if (map.length != BUTTON_COUNT) {
            throw new IllegalArgumentException("Navigation button count does not match! Is: "
                    + map.length + " expected: " + BUTTON_COUNT);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (i != 0) sb.append("|");
            sb.append(map[i].key);
        }
        Settings.System.putString(context.getContentResolver(),
                Settings.System.NAV_BUTTONS, sb.toString());
    }
}
