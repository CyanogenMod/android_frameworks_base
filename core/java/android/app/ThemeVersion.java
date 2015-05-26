/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.app;

/**
 * @hide
 */
public class ThemeVersion {
    /**
     *  Increment this anytime changes are made to:
     *  1) Changes to capability versioning
     *  2) Changes to ThemesContract
     *  3) Changes to ThemeService API
     */
    public static int THEME_VERSION = 2;

    public enum Capabilities {
        OVERLAY(2),
        BOOT_ANIM(1),
        WALLPAPER(1),
        LOCK_WALLPAPER(1),
        FONT(2),
        ICON(3),
        SOUND_RINGTONE(1),
        SOUND_ALARM(1),
        SOUND_NOTIFICATION(1),
        PER_APP_OVERLAY(1),
        NAV_BAR(1),
        STATUS_BAR(1);

        int version;
        Capabilities(int version) {
            this.version = version;
        }
    }
}
