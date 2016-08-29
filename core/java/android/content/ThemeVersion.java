/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package android.content;

/**
 * Warning: Careful moving/refactoring this class as our SDK references it.
 * ThemeVersion 1 = CM11
 * ThemeVersion 2 = CM12/CM12.1 First Release
 * ThemeVersion 3 = CM12.1 W/ Wallpaper Packs
 * @hide
 */
public class ThemeVersion {
    /**
     *  Increment this anytime changes are made to:
     *  1) Changes to ThemesContract
     *  2) Changes to ThemeService API
     *  3) Changes to ThemeManager API
     */
    public static int THEME_VERSION = 3;

    /**
     * Change this if a change to the contract or service would break compatibility.
     * Example: A client app like chooser might be outdated from the framework.
     * It could then query the FW for this value and determine whether its safe to proceed.
     */
    public static int MIN_SUPPORTED_THEME_VERSION = 2;

    /**
     * Do not change the order of this. See SDK.
     * Increment the minSupportedVersion when the fw can no longer support a theme's apk structure
     * Increment currentVersion when a change to the theme's apk structure is changed
     * For example, CM11 to CM12 introduces new resources to overlay, so the overlays
     * version should change. Because the changes are not compatible with CM11, the minVersion
     * must change as well.
     *
     * If a new feature is added to a component (ex rotations in icon packs), the current version
     * for the ICON component would be incremented. If a new component is created, then add it
     * to the enum list.
     *
     * Wallpaper Version 2: Multi wallpaper ability
     *
     */
    public static enum ComponentVersion {
        OVERLAY(0, 2, 2),
        BOOT_ANIM(1, 1, 1),
        WALLPAPER(2, 1, 2),
        LOCKSCREEN(3, 1, 1),
        FONT(4, 1, 2),
        ICON(5, 1, 1),
        SOUNDS(6, 1, 1);

        public int id;
        public int minSupportedVersion;
        public int currentVersion;

        private ComponentVersion(int id, int minSupportedVersion, int currentVersion) {
            this.id = id;
            this.minSupportedVersion = minSupportedVersion;
            this.currentVersion = currentVersion;
        }
    }
}
