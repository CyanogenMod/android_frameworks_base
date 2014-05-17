/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
 * This code has been modified.
 * Portions copyright (C) 2014, The CyanogenMod Project
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

import android.content.ContentResolver;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * @hide
 */
public final class CustomTheme implements Cloneable, Parcelable, Comparable<CustomTheme> {
    private final String SYSTEMUI_PKG_NAME = "com.android.systemui";

    public static final String HOLO_DEFAULT = "holo";

    protected final String mThemePackageName;
    protected final String mIconPackPkgName;
    protected final String mSystemUiThemePkgName;
    protected final String mFontPkgName;

    private static final CustomTheme sSystemTheme = new CustomTheme("","","","");

    private CustomTheme(Parcel src) {
        mThemePackageName = src.readString();
        mSystemUiThemePkgName = src.readString();
        mIconPackPkgName = src.readString();
        mFontPkgName = src.readString();
    }

    /**
     * Constructor used in legacy theme manager
     */
    public CustomTheme(String pkgName, String systemUiThemePkgName, String iconPackPkgName) {
        this(pkgName, systemUiThemePkgName, iconPackPkgName, "");
    }

    public CustomTheme(String pkgName, String systemUiThemePkgName, String iconPackPkgName, String fontPkgName) {
        mThemePackageName = pkgName;
        mSystemUiThemePkgName = systemUiThemePkgName;
        mIconPackPkgName = iconPackPkgName;
        mFontPkgName = fontPkgName;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof CustomTheme) {
            CustomTheme o = (CustomTheme) object;

            String currentSystemUi = (mSystemUiThemePkgName == null)? "" : mSystemUiThemePkgName;
            String newSystemUi = (o.mSystemUiThemePkgName == null)? "" : o.mSystemUiThemePkgName;

            String currentPackageName = (mThemePackageName == null)? "" : mThemePackageName;
            String newPackageName = (o.mThemePackageName == null)? "" : o.mThemePackageName;

            String currentIconPkgName = (mIconPackPkgName == null)? "" : mIconPackPkgName;
            String newIconPkgName = (o.mIconPackPkgName == null)? "" : o.mIconPackPkgName;

            String currentFontPkgName = (mFontPkgName == null)? "" : mFontPkgName;
            String newFontPkgName = (o.mFontPkgName == null)? "" : o.mFontPkgName;

            return (currentPackageName.equals(newPackageName) &&
                    currentSystemUi.equals(newSystemUi) &&
                    currentIconPkgName.equals(newIconPkgName) &&
                    currentFontPkgName.equals(newFontPkgName));
        }
        return false;
    }

    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder();
        if (!TextUtils.isEmpty(mThemePackageName) && !TextUtils.isEmpty(mSystemUiThemePkgName)) {
            result.append(mThemePackageName);
            result.append('(');
            result.append(mSystemUiThemePkgName);
            result.append(')');

            if (!TextUtils.isEmpty(mIconPackPkgName)) {
                result.append(", iconPack:");
                result.append(mIconPackPkgName);
            }

            if (!TextUtils.isEmpty(mFontPkgName)) {
                result.append(", fontPkg:");
                result.append(mFontPkgName);
            }
        } else {
            result.append("system");
        }
        return result.toString();
    }

    @Override
    public synchronized int hashCode() {
        return (mSystemUiThemePkgName + mThemePackageName + mIconPackPkgName + mFontPkgName).hashCode();
    }

    public String getThemePackageName() {
        return mThemePackageName;
    }

    public String getIconPackPkgName() {
        return mIconPackPkgName;
    }

    public String getFontPackPkgName() {
        return mFontPkgName;
    }

    public String getThemePackageNameForApp(String appPkgName) {
        if (SYSTEMUI_PKG_NAME.equals(appPkgName) && mSystemUiThemePkgName != null && !mSystemUiThemePkgName.equals("")) {
            return mSystemUiThemePkgName;
        }
        return mThemePackageName;
    }

    public String getSystemUiPackageName() {
        return mSystemUiThemePkgName;
    }

    /**
     * Represents the theme that the device booted into. This is used to
     * simulate a "default" configuration based on the user's last known
     * preference until the theme is switched at runtime.
     */
    public static CustomTheme getBootTheme(ContentResolver resolver) {
        try {
            String themePkgName = Settings.Secure.getString(resolver, Configuration.THEME_PACKAGE_NAME_PERSISTENCE_PROPERTY);
            if (themePkgName == null) themePkgName = "";

            String systemUiPkgName = Settings.Secure.getString(resolver, Configuration.THEME_SYSTEMUI_PACKAGE_NAME_PERSISTENCE_PROPERTY);
            if (systemUiPkgName == null) systemUiPkgName = "";

            String iconPackPkgName = Settings.Secure.getString(resolver, Configuration.THEME_ICONPACK_PACKAGE_NAME_PERSISTENCE_PROPERTY);
            if (iconPackPkgName == null) iconPackPkgName = "";

            String fontPkgName = Settings.Secure.getString(resolver, Configuration.THEME_FONT_PACKAGE_NAME_PERSISTENCE_PROPERTY);
            if (fontPkgName == null) fontPkgName = "";
            return new CustomTheme(themePkgName, systemUiPkgName, iconPackPkgName, fontPkgName);
        } catch (SecurityException e) {
            return sSystemTheme;
        }
    }

    /**
     * Represents the system framework theme, perceived by the system as there
     * being no theme applied.
     */
    public static CustomTheme getSystemTheme() {
        return sSystemTheme;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mThemePackageName);
        dest.writeString(mSystemUiThemePkgName);
        dest.writeString(mIconPackPkgName);
        dest.writeString(mFontPkgName);
    }

    public static final Parcelable.Creator<CustomTheme> CREATOR = new Parcelable.Creator<CustomTheme>() {
        public CustomTheme createFromParcel(Parcel source) {
            return new CustomTheme(source);
        }

        public CustomTheme[] newArray(int size) {
            return new CustomTheme[size];
        }
    };

    @Override
    public int compareTo(CustomTheme o) {
        if (o == null) return -1;
        int n = 0;
        n = mSystemUiThemePkgName.compareTo(o.mSystemUiThemePkgName);
        if (n != 0) return n;
        n = mThemePackageName.compareTo(o.mThemePackageName);
        if (n != 0) return n;
        n = mIconPackPkgName.compareTo(o.mIconPackPkgName);
        if (n != 0) return n;
        n = mFontPkgName.compareTo(o.mFontPkgName);
        return n;
    }

    public static class Builder {
        private String mThemePkgName;
        private String mSystemUiPkgName;
        private String mIconPkgName;
        private String mFontPkgName;

        public Builder() {
        }

        public Builder(CustomTheme theme) {
            if (theme == null) return;
            mIconPkgName = theme.mIconPackPkgName;
            mThemePkgName = theme.mThemePackageName;
            mSystemUiPkgName = theme.mThemePackageName;
            mFontPkgName = theme.mFontPkgName;
        }

        public Builder overlay(String pkgName) {
            this.mThemePkgName = pkgName.equals(HOLO_DEFAULT) ? "" : pkgName;
            return this;
        }

        public Builder systemUi(String pkgName) {
            this.mSystemUiPkgName = pkgName.equals(HOLO_DEFAULT) ? "" : pkgName;
            return this;
        }

        public Builder icons(String pkgName) {
            this.mIconPkgName = pkgName.equals(HOLO_DEFAULT) ? "" : pkgName;
            return this;
        }

        public Builder fonts(String pkgName) {
            this.mFontPkgName = pkgName.equals(HOLO_DEFAULT) ? "" : pkgName;
            return this;
        }

        public CustomTheme build() {
            return new CustomTheme(mThemePkgName, mSystemUiPkgName, mIconPkgName, mFontPkgName);
        }
    }
}
