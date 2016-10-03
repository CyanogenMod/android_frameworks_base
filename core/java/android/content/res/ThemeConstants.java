/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package android.content.res;

/**
 * Theme specific constants used throughout the framework
 * @hide
 */
public class ThemeConstants {
    /**
     * Package IDs for themes. Aapt will compile the res table with this id.
     */
    public static final int THEME_FRAMEWORK_PKG_ID = 0x60;
    public static final int THEME_APP_PKG_ID = 0x61;
    public static final int THEME_ICON_PKG_ID = 0x62;
    public static final int THEME_CM_PKG_ID = 0x63;

    /**
     * The common resource pkg id needs to be less than the THEME_FRAMEWORK_PKG_ID
     * otherwise aapt will complain and fail
     * @hide
     */
    public static final int THEME_COMMON_PKG_ID = THEME_FRAMEWORK_PKG_ID - 1;

}
