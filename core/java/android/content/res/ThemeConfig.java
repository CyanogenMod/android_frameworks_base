/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Portions copyright (C) 2014, T-Mobile USA, Inc.
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
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

/**
 * The Theme Configuration allows lookup of a theme element (fonts, icon, overlay) for a given
 * application. If there isn't a particular theme designated to an app, it will fallback on the
 * default theme. If there isn't a default theme then it will simply fallback to holo.
 *
 * @hide
 */
public class ThemeConfig implements Cloneable, Parcelable, Comparable<ThemeConfig> {
    public static final String TAG = ThemeConfig.class.getCanonicalName();
    public static final String SYSTEM_DEFAULT = "system";

    /**
     * Special package name for theming the navbar separate from the rest of SystemUI
     */
    public static final String SYSTEMUI_NAVBAR_PKG = "com.android.systemui.navbar";
    public static final String SYSTEMUI_STATUS_BAR_PKG = "com.android.systemui";

    // Key for any app which does not have a specific theme applied
    private static final String KEY_DEFAULT_PKG = "default";
    private static final SystemConfig mSystemConfig = new SystemConfig();
    private static final SystemAppTheme mSystemAppTheme = new SystemAppTheme();

	// Deprecated constants for upgrade
    private static final String THEME_PACKAGE_NAME_PERSISTENCE_PROPERTY
            = "persist.sys.themePackageName";

    private static final String THEME_ICONPACK_PACKAGE_NAME_PERSISTENCE_PROPERTY
            = "themeIconPackPkgName";

    private static final String THEME_FONT_PACKAGE_NAME_PERSISTENCE_PROPERTY
            = "themeFontPackPkgName";

    /**
     * @hide
     * Serialized json structure mapping app pkgnames to their set theme.
     *
     * {
     *  "default":{
     *"     stylePkgName":"com.jasonevil.theme.miuiv5dark",
     *      "iconPkgName":"com.cyngn.hexo",
     *      "fontPkgName":"com.cyngn.hexo"
     *   }
     * }

     * If an app does not have a specific theme set then it will use the 'default' theme+
     * example: 'default' -> overlayPkgName: 'org.blue.theme'
     *          'com.android.phone' -> 'com.red.theme'
     *          'com.google.vending' -> 'com.white.theme'
     */
    public static final String THEME_PKG_CONFIGURATION_PERSISTENCE_PROPERTY = "themeConfig";

    // Maps pkgname to theme (ex com.angry.birds -> red theme)
    protected final Map<String, AppTheme> mThemes = new ArrayMap<>();

    public ThemeConfig(Map<String, AppTheme> appThemes) {
        mThemes.putAll(appThemes);
    }

    public String getOverlayPkgName() {
        AppTheme theme = getDefaultTheme();
        return theme.mOverlayPkgName;
    }

    public String getOverlayForStatusBar() {
        return getOverlayPkgNameForApp(SYSTEMUI_STATUS_BAR_PKG);
    }

    public String getOverlayForNavBar() {
        return getOverlayPkgNameForApp(SYSTEMUI_NAVBAR_PKG);
    }

    public String getOverlayPkgNameForApp(String appPkgName) {
        AppTheme theme = getThemeFor(appPkgName);
        return theme.mOverlayPkgName;
    }

    public String getIconPackPkgName() {
        AppTheme theme = getDefaultTheme();
        return theme.mIconPkgName;
    }

    public String getIconPackPkgNameForApp(String appPkgName) {
        AppTheme theme = getThemeFor(appPkgName);
        return theme.mIconPkgName;
    }

    public String getFontPkgName() {
        AppTheme defaultTheme = getDefaultTheme();
        return defaultTheme.mFontPkgName;
    }

    public String getFontPkgNameForApp(String appPkgName) {
        AppTheme theme = getThemeFor(appPkgName);
        return theme.mFontPkgName;
    }

    public Map<String, AppTheme> getAppThemes() {
        return Collections.unmodifiableMap(mThemes);
    }

    private AppTheme getThemeFor(String pkgName) {
        AppTheme theme = mThemes.get(pkgName);
        if (theme == null) theme = getDefaultTheme();
        return theme;
    }

    private AppTheme getDefaultTheme() {
        AppTheme theme = mThemes.get(KEY_DEFAULT_PKG);
        if (theme == null) theme = mSystemAppTheme;
        return theme;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof ThemeConfig) {
            ThemeConfig o = (ThemeConfig) object;

            Map<String, AppTheme> currThemes = (mThemes == null) ?
                    new ArrayMap<String, AppTheme>() : mThemes;
            Map<String, AppTheme> newThemes = (o.mThemes == null) ?
                    new ArrayMap<String, AppTheme>() : o.mThemes;

            return currThemes.equals(newThemes);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (mThemes != null) {
            result.append("themes:");
            result.append(mThemes);
        }
        return result.toString();
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mThemes.hashCode();
        return hash;
    }

    public String toJson() {
        return JsonSerializer.toJson(this);
    }

    public static ThemeConfig fromJson(String json) {
        return JsonSerializer.fromJson(json);
    }

    /**
     * Represents the theme that the device booted into. This is used to
     * simulate a "default" configuration based on the user's last known
     * preference until the theme is switched at runtime.
     */
    public static ThemeConfig getBootTheme(ContentResolver resolver) {
        return getBootThemeForUser(resolver, UserHandle.USER_OWNER);
    }

    public static ThemeConfig getBootThemeForUser(ContentResolver resolver, int userHandle) {
        ThemeConfig bootTheme = mSystemConfig;
        try {
            String json = Settings.Secure.getStringForUser(resolver,
                    THEME_PKG_CONFIGURATION_PERSISTENCE_PROPERTY, userHandle);
            bootTheme = ThemeConfig.fromJson(json);

            // Handle upgrade Case: Previously the theme configuration was in separate fields
            if (bootTheme == null) {
                String overlayPkgName =  Settings.Secure.getStringForUser(resolver,
                        THEME_PACKAGE_NAME_PERSISTENCE_PROPERTY, userHandle);
                String iconPackPkgName = Settings.Secure.getStringForUser(resolver,
                        THEME_ICONPACK_PACKAGE_NAME_PERSISTENCE_PROPERTY, userHandle);
                String fontPkgName = Settings.Secure.getStringForUser(resolver,
                        THEME_FONT_PACKAGE_NAME_PERSISTENCE_PROPERTY, userHandle);

                Builder builder = new Builder();
                builder.defaultOverlay(overlayPkgName);
                builder.defaultIcon(iconPackPkgName);
                builder.defaultFont(fontPkgName);
                bootTheme = builder.build();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Could not get boot theme");
        }
        return bootTheme;
    }

    /**
     * Represents the system framework theme, perceived by the system as there
     * being no theme applied.
     */
    public static ThemeConfig getSystemTheme() {
        return mSystemConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        String json = JsonSerializer.toJson(this);
        dest.writeString(json);
    }

    public static final Parcelable.Creator<ThemeConfig> CREATOR =
            new Parcelable.Creator<ThemeConfig>() {
        public ThemeConfig createFromParcel(Parcel source) {
            String json = source.readString();
            ThemeConfig themeConfig = JsonSerializer.fromJson(json);
            return themeConfig;
        }

        public ThemeConfig[] newArray(int size) {
            return new ThemeConfig[size];
        }
    };

    @Override
    public int compareTo(ThemeConfig o) {
        if (o == null) return -1;
        int n = 0;
        n = mThemes.equals(o.mThemes) ? 0 : 1;
        return n;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            Log.d(TAG, "clone not supported", e);
            return null;
        }
    }

    public static class AppTheme implements Cloneable, Comparable<AppTheme> {
        // If any field is modified or added here be sure to change the serializer accordingly
        String mOverlayPkgName;
        String mIconPkgName;
        String mFontPkgName;

        public AppTheme(String overlayPkgName, String iconPkgName, String fontPkgName) {
            mOverlayPkgName = overlayPkgName;
            mIconPkgName = iconPkgName;
            mFontPkgName = fontPkgName;
        }

        public String getIconPackPkgName() {
            return mIconPkgName;
        }

        public String getOverlayPkgName() {
            return mOverlayPkgName;
        }

        public String getFontPackPkgName() {
            return mFontPkgName;
        }

        @Override
        public synchronized int hashCode() {
            int hash = 17;
            hash = 31 * hash + (mOverlayPkgName == null ? 0 : mOverlayPkgName.hashCode());
            hash = 31 * hash + (mIconPkgName == null ? 0 : mIconPkgName.hashCode());
            hash = 31 * hash + (mFontPkgName == null ? 0 : mFontPkgName.hashCode());
            return hash;
        }

        @Override
        public int compareTo(AppTheme o) {
            if (o == null) return -1;
            int n = 0;
            n = mIconPkgName.compareTo(o.mIconPkgName);
            if (n != 0) return n;
            n = mFontPkgName.compareTo(o.mFontPkgName);
            if (n != 0) return n;
            n = mOverlayPkgName.equals(o.mOverlayPkgName) ? 0 : 1;
            return n;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }
            if (object instanceof AppTheme) {
                AppTheme o = (AppTheme) object;
                String currentOverlayPkgName = (mOverlayPkgName == null)? "" : mOverlayPkgName;
                String newOverlayPkgName = (o.mOverlayPkgName == null)? "" : o.mOverlayPkgName;
                String currentIconPkgName = (mIconPkgName == null)? "" : mIconPkgName;
                String newIconPkgName = (o.mIconPkgName == null)? "" : o.mIconPkgName;
                String currentFontPkgName = (mFontPkgName == null)? "" : mFontPkgName;
                String newFontPkgName = (o.mFontPkgName == null)? "" : o.mFontPkgName;


                return (currentIconPkgName.equals(newIconPkgName) &&
                        currentFontPkgName.equals(newFontPkgName) &&
                        currentOverlayPkgName.equals(newOverlayPkgName));
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (mOverlayPkgName != null) {
                result.append("overlay:");
                result.append(mOverlayPkgName);
            }

            if (!TextUtils.isEmpty(mIconPkgName)) {
                result.append(", iconPack:");
                result.append(mIconPkgName);
            }

            if (!TextUtils.isEmpty(mFontPkgName)) {
                result.append(", fontPkg:");
                result.append(mFontPkgName);
            }
            return result.toString();
        }
    }


    public static class Builder {
        private Map<String, String> mOverlays = new ArrayMap<>();
        private Map<String, String> mIcons = new ArrayMap<>();
        private Map<String, String> mFonts = new ArrayMap<>();

        public Builder() {}

        public Builder(ThemeConfig theme) {
            for(Map.Entry<String, AppTheme> entry : theme.mThemes.entrySet()) {
                String key = entry.getKey();
                AppTheme appTheme = entry.getValue();
                mFonts.put(key, appTheme.getFontPackPkgName());
                mIcons.put(key, appTheme.getIconPackPkgName());
                mOverlays.put(key, appTheme.getOverlayPkgName());
            }
        }

        /**
         * For uniquely theming a specific app. ex. "Dialer gets red theme,
         * Calculator gets blue theme"
         */
        public Builder defaultOverlay(String themePkgName) {
            if (themePkgName != null) {
                mOverlays.put(KEY_DEFAULT_PKG, themePkgName);
            } else {
                mOverlays.remove(KEY_DEFAULT_PKG);
            }
            return this;
        }

        public Builder defaultFont(String themePkgName) {
            if (themePkgName != null) {
                mFonts.put(KEY_DEFAULT_PKG, themePkgName);
            } else {
                mFonts.remove(KEY_DEFAULT_PKG);
            }
            return this;
        }

        public Builder defaultIcon(String themePkgName) {
            if (themePkgName != null) {
                mIcons.put(KEY_DEFAULT_PKG, themePkgName);
            } else {
                mIcons.remove(KEY_DEFAULT_PKG);
            }
            return this;
        }

        public Builder icon(String appPkgName, String themePkgName) {
            if (themePkgName != null) {
                mIcons.put(appPkgName, themePkgName);
            } else {
                mIcons.remove(appPkgName);
            }
            return this;
        }

        public Builder overlay(String appPkgName, String themePkgName) {
            if (themePkgName != null) {
                mOverlays.put(appPkgName, themePkgName);
            } else {
                mOverlays.remove(appPkgName);
            }
            return this;
        }

        public Builder font(String appPkgName, String themePkgName) {
            if (themePkgName != null) {
                mFonts.put(appPkgName, themePkgName);
            } else {
                mFonts.remove(appPkgName);
            }
            return this;
        }

        public ThemeConfig build() {
            ArraySet<String> appPkgSet = new ArraySet<>();
            appPkgSet.addAll(mOverlays.keySet());
            appPkgSet.addAll(mIcons.keySet());
            appPkgSet.addAll(mFonts.keySet());

            Map<String, AppTheme> appThemes = new ArrayMap<>();
            for(String appPkgName : appPkgSet) {
                String icon = mIcons.get(appPkgName);
                String overlay = mOverlays.get(appPkgName);
                String font = mFonts.get(appPkgName);

                // Remove app theme if all items are null
                if (overlay == null && icon == null && font == null) {
                    if (appThemes.containsKey(appPkgName)) {
                        appThemes.remove(appPkgName);
                    }
                } else {
                    AppTheme appTheme = new AppTheme(overlay, icon, font);
                    appThemes.put(appPkgName, appTheme);
                }
            }
            ThemeConfig themeConfig = new ThemeConfig(appThemes);
            return themeConfig;
        }
    }


    public static class JsonSerializer {
        private static final String NAME_OVERLAY_PKG = "mOverlayPkgName";
        private static final String NAME_ICON_PKG = "mIconPkgName";
        private static final String NAME_FONT_PKG = "mFontPkgName";

        public static String toJson(ThemeConfig theme) {
            String json = null;
            Writer writer = null;
            JsonWriter jsonWriter = null;
            try {
                writer = new StringWriter();
                jsonWriter = new JsonWriter(writer);
                writeTheme(jsonWriter, theme);
                json = writer.toString();
            } catch(IOException e) {
                Log.e(TAG, "Could not write theme mapping", e);
            } finally {
                closeQuietly(writer);
                closeQuietly(jsonWriter);
            }
            return json;
        }

        private static void writeTheme(JsonWriter writer, ThemeConfig theme)
                throws IOException {
            writer.beginObject();
            for(Map.Entry<String, AppTheme> entry : theme.mThemes.entrySet()) {
                String appPkgName = entry.getKey();
                AppTheme appTheme = entry.getValue();
                writer.name(appPkgName);
                writeAppTheme(writer, appTheme);
            }
            writer.endObject();
        }

        private static void writeAppTheme(JsonWriter writer, AppTheme appTheme) throws IOException {
            writer.beginObject();
            writer.name(NAME_OVERLAY_PKG).value(appTheme.mOverlayPkgName);
            writer.name(NAME_ICON_PKG).value(appTheme.mIconPkgName);
            writer.name(NAME_FONT_PKG).value(appTheme.mFontPkgName);
            writer.endObject();
        }

        public static ThemeConfig fromJson(String json) {
            if (json == null) return null;
            Map<String, AppTheme> map = new ArrayMap<>();
            StringReader reader = null;
            JsonReader jsonReader = null;
            try {
                reader = new StringReader(json);
                jsonReader = new JsonReader(reader);
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String appPkgName = jsonReader.nextName();
                    AppTheme appTheme = readAppTheme(jsonReader);
                    map.put(appPkgName, appTheme);
                }
                jsonReader.endObject();
            } catch(Exception e) {
                Log.e(TAG, "Could not parse ThemeConfig from: " + json, e);
            } finally {
                closeQuietly(reader);
                closeQuietly(jsonReader);
            }
            return new ThemeConfig(map);
        }

        private static AppTheme readAppTheme(JsonReader reader) throws IOException {
            String overlay = null;
            String icon = null;
            String font = null;

            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                if (NAME_OVERLAY_PKG.equals(name) && reader.peek() != JsonToken.NULL) {
                    overlay = reader.nextString();
                } else if (NAME_ICON_PKG.equals(name) && reader.peek() != JsonToken.NULL) {
                    icon = reader.nextString();
                } else if (NAME_FONT_PKG.equals(name) && reader.peek() != JsonToken.NULL) {
                    font = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            return new AppTheme(overlay, icon, font);
        }

        private static void closeQuietly(Reader reader) {
            try {
                if (reader != null) reader.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuietly(JsonReader reader) {
            try {
                if (reader != null) reader.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuietly(Writer writer) {
            try {
                if (writer != null) writer.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuietly(JsonWriter writer) {
            try {
                if (writer != null) writer.close();
            } catch(IOException e) {
            }
        }
    }

    public static class SystemConfig extends ThemeConfig {
        public SystemConfig() {
            super(new ArrayMap<String, AppTheme>());
        }
    }

    public static class SystemAppTheme extends AppTheme {
        public SystemAppTheme() {
            super(SYSTEM_DEFAULT, SYSTEM_DEFAULT, SYSTEM_DEFAULT);
        }

        @Override
        public String toString() {
            return "No Theme Applied (System)";
        }
    }
}
