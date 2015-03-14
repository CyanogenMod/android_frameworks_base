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
package android.content.res;

import android.content.pm.ThemeUtils;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


import static android.provider.ThemesContract.ThemesColumns.*;

/** @hide */
public final class ThemeChangeRequest implements Parcelable {
    private final Map<String, String> mThemeComponents = new HashMap<String, String>();
    private final Map<String, String> mPerAppOverlays = new HashMap<String, String>();
    private RequestType mRequestType;

    public String getOverlayThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_OVERLAYS);
    }

    public String getStatusBarThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_STATUS_BAR);
    }

    public String getNavBarThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_NAVIGATION_BAR);
    }

    public String getFontThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_FONTS);
    }

    public String getIconsThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_ICONS);
    }

    public String getBootanimationThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_BOOT_ANIM);
    }

    public String getWallpaperThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_LAUNCHER);
    }

    public String getLockWallpaperThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_LOCKSCREEN);
    }

    public String getAlarmThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_ALARMS);
    }

    public String getNotificationThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_NOTIFICATIONS);
    }

    public String getRingtoneThemePackageName() {
        return getThemePackageNameForComponent(MODIFIES_RINGTONES);
    }

    public final Map<String, String> getThemeComponentsMap() {
        return Collections.unmodifiableMap(mThemeComponents);
    }

    /**
     * Get the mapping for per app themes
     * @return A mapping of apps and the theme to apply for each one. or null if none set.
     */
    public final Map<String, String> getPerAppOverlays() {
        return Collections.unmodifiableMap(mPerAppOverlays);
    }

    public int getNumChangesRequested() {
        return mThemeComponents.size() + mPerAppOverlays.size();
    }

    public RequestType getReqeustType() {
        return mRequestType;
    }

    private String getThemePackageNameForComponent(String componentName) {
        return mThemeComponents.get(componentName);
    }

    private ThemeChangeRequest(Map<String, String> components, Map<String, String> perAppThemes,
            RequestType requestType) {
        if (components != null) {
            mThemeComponents.putAll(components);
        }
        if (perAppThemes != null) {
            mPerAppOverlays.putAll(perAppThemes);
        }
        mRequestType = requestType;
    }

    private ThemeChangeRequest(Parcel source) {
        int numComponents = source.readInt();
        for (int i = 0; i < numComponents; i++) {
            mThemeComponents.put(source.readString(), source.readString());
        }

        numComponents = source.readInt();
        for (int i = 0 ; i < numComponents; i++) {
            mPerAppOverlays.put(source.readString(), source.readString());
        }
        mRequestType = RequestType.values()[source.readInt()];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mThemeComponents.size());
        for (String component : mThemeComponents.keySet()) {
            dest.writeString(component);
            dest.writeString(mThemeComponents.get(component));
        }
        dest.writeInt((mPerAppOverlays.size()));
        for (String appPkgName : mPerAppOverlays.keySet()) {
            dest.writeString(appPkgName);
            dest.writeString(mPerAppOverlays.get(appPkgName));
        }
        dest.writeInt(mRequestType.ordinal());
    }

    public static final Parcelable.Creator<ThemeChangeRequest> CREATOR =
            new Parcelable.Creator<ThemeChangeRequest>() {
                @Override
                public ThemeChangeRequest createFromParcel(Parcel source) {
                    return new ThemeChangeRequest(source);
                }

                @Override
                public ThemeChangeRequest[] newArray(int size) {
                    return new ThemeChangeRequest[size];
                }
            };

    public enum RequestType {
        USER_REQUEST,
        USER_REQUEST_MIXNMATCH,
        THEME_UPDATED,
        THEME_REMOVED,
        THEME_RESET;
    }

    public static class Builder {
        Map<String, String> mThemeComponents = new HashMap<String, String>();
        Map<String, String> mPerAppOverlays = new HashMap<String, String>();
        RequestType mRequestType = RequestType.USER_REQUEST;

        public Builder() {}

        public Builder(ThemeConfig themeConfig) {
            if (themeConfig != null) {
                buildChangeRequestFromThemeConfig(themeConfig);
            }
        }

        public Builder setOverlay(String pkgName) {
            return setComponent(MODIFIES_OVERLAYS, pkgName);
        }

        public Builder setStatusBar(String pkgName) {
            return setComponent(MODIFIES_STATUS_BAR, pkgName);
        }

        public Builder setNavBar(String pkgName) {
            return setComponent(MODIFIES_NAVIGATION_BAR, pkgName);
        }

        public Builder setFont(String pkgName) {
            return setComponent(MODIFIES_FONTS, pkgName);
        }

        public Builder setIcons(String pkgName) {
            return setComponent(MODIFIES_ICONS, pkgName);
        }

        public Builder setBootanimation(String pkgName) {
            return setComponent(MODIFIES_BOOT_ANIM, pkgName);
        }

        public Builder setWallpaper(String pkgName) {
            return setComponent(MODIFIES_LAUNCHER, pkgName);
        }

        public Builder setLockWallpaper(String pkgName) {
            return setComponent(MODIFIES_LOCKSCREEN, pkgName);
        }

        public Builder setAlarm(String pkgName) {
            return setComponent(MODIFIES_ALARMS, pkgName);
        }

        public Builder setNotification(String pkgName) {
            return setComponent(MODIFIES_NOTIFICATIONS, pkgName);
        }

        public Builder setRingtone(String pkgName) {
            return setComponent(MODIFIES_RINGTONES, pkgName);
        }

        public Builder setComponent(String component, String pkgName) {
            if (pkgName != null) {
                mThemeComponents.put(component, pkgName);
            } else {
                mThemeComponents.remove(component);
            }
            return this;
        }

        public Builder setAppOverlay(String appPkgName, String themePkgName) {
            if (appPkgName != null) {
                if (themePkgName != null) {
                    mPerAppOverlays.put(appPkgName, themePkgName);
                } else {
                    mPerAppOverlays.remove(appPkgName);
                }
            }

            return this;
        }

        public Builder setRequestType(RequestType requestType) {
            mRequestType = requestType != null ? requestType : RequestType.USER_REQUEST;
            return this;
        }

        public ThemeChangeRequest build() {
            return new ThemeChangeRequest(mThemeComponents, mPerAppOverlays, mRequestType);
        }

        private void buildChangeRequestFromThemeConfig(ThemeConfig themeConfig) {
            if (themeConfig.getFontPkgName() != null) {
                this.setFont(themeConfig.getFontPkgName());
            }
            if (themeConfig.getIconPackPkgName() != null) {
                this.setIcons(themeConfig.getIconPackPkgName());
            }
            if (themeConfig.getOverlayPkgName() != null) {
                this.setOverlay(themeConfig.getOverlayPkgName());
            }
            if (themeConfig.getOverlayForStatusBar() != null) {
                this.setStatusBar(themeConfig.getOverlayForStatusBar());
            }
            if (themeConfig.getOverlayForNavBar() != null) {
                this.setNavBar(themeConfig.getOverlayForNavBar());
            }

            // Check if there are any per-app overlays using this theme
            final Map<String, ThemeConfig.AppTheme> themes = themeConfig.getAppThemes();
            for (String appPkgName : themes.keySet()) {
                if (ThemeUtils.isPerAppThemeComponent(appPkgName)) {
                    this.setAppOverlay(appPkgName, themes.get(appPkgName).getOverlayPkgName());
                }
            }
        }
    }
}
