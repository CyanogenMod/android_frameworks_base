/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Portions copyright (C) 2014, T-Mobile USA, Inc.
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

import android.content.ContentResolver;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * The Theme Configuration allows lookup of a theme element (fonts, icon, style) for a given
 * application. If there isn't a particular theme designated to an app, it will fallback on the
 * default theme. If there isn't a default theme then it will simply fallback to holo.
 *
 * @hide
 */
public class ThemeConfiguration implements Cloneable, Parcelable, Comparable<ThemeConfiguration> {
    public static final String TAG = ThemeConfiguration.class.getCanonicalName();
    public static final String HOLO_DEFAULT = "holo";
    // Key for any app which does not have a specific theme applied
    private static final String KEY_DEFAULT_PKG = "default";
    private static final HoloConfiguration mHoloConfig = new HoloConfiguration();
    private static final HoloTheme mHoloAppTheme = new HoloTheme();

    // Maps pkgname to theme (ex com.angry.birds -> red theme)
    protected final Map<String, AppTheme> mThemes = new HashMap<String, AppTheme>();

    public ThemeConfiguration(Map<String, AppTheme> appThemes) {
        mThemes.putAll(appThemes);
    }

    public String getStylePkgName() {
        AppTheme theme = getDefaultTheme();
        return theme.mStylePkgName;
    }

    public String getStylePkgNameForApp(String appPkgName) {
        AppTheme theme = getThemeFor(appPkgName);
        return theme.mStylePkgName;
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

    private AppTheme getThemeFor(String pkgName) {
        AppTheme theme = mThemes.get(pkgName);
        if (theme == null) theme = getDefaultTheme();
        return theme;
    }

    private AppTheme getDefaultTheme() {
        AppTheme theme = mThemes.get(KEY_DEFAULT_PKG);
        if (theme == null) theme = mHoloAppTheme;
        return theme;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof ThemeConfiguration) {
            ThemeConfiguration o = (ThemeConfiguration) object;

            Map<String, AppTheme> currThemes = (mThemes == null) ?
                    new HashMap<String, AppTheme>() : mThemes;
            Map<String, AppTheme> newThemes = (o.mThemes == null) ?
                    new HashMap<String, AppTheme>() : o.mThemes;

            return (currThemes.equals(newThemes));
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

    public String toJson() {
        return JsonSerializer.toJson(this);
    }

    public static ThemeConfiguration fromJson(String json) {
        return JsonSerializer.fromJson(json);
    }

    /**
     * Represents the theme that the device booted into. This is used to
     * simulate a "default" configuration based on the user's last known
     * preference until the theme is switched at runtime.
     */
    public static ThemeConfiguration getBootTheme(ContentResolver resolver) {
        ThemeConfiguration bootTheme = mHoloConfig;
        try {
            String json = Settings.Secure.getString(resolver,
                    Configuration.THEME_PKG_CONFIGURATION_PERSISTENCE_PROPERTY);
            bootTheme = ThemeConfiguration.fromJson(json);

            // Handle upgrade Case: Previously the theme configuration was in separate fields
            if (bootTheme == null) {
                String stylePkgName =  Settings.Secure.getString(resolver,
                        Configuration.THEME_PACKAGE_NAME_PERSISTENCE_PROPERTY);
                String iconPackPkgName = Settings.Secure.getString(resolver,
                        Configuration.THEME_ICONPACK_PACKAGE_NAME_PERSISTENCE_PROPERTY);
                String fontPkgName = Settings.Secure.getString(resolver,
                        Configuration.THEME_FONT_PACKAGE_NAME_PERSISTENCE_PROPERTY);

                Builder builder = new Builder();
                builder.defaultOverlay(stylePkgName);
                builder.defaultIcon(iconPackPkgName);
                builder.defaultFont(fontPkgName);
                bootTheme = builder.build();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Could not get boot theme", e);
        }
        return bootTheme;
    }

    /**
     * Represents the system framework theme, perceived by the system as there
     * being no theme applied.
     */
    public static ThemeConfiguration getSystemTheme() {
        return mHoloConfig;
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

    public static final Parcelable.Creator<ThemeConfiguration> CREATOR =
            new Parcelable.Creator<ThemeConfiguration>() {
        public ThemeConfiguration createFromParcel(Parcel source) {
            String json = source.readString();
            return JsonSerializer.fromJson(json);
        }

        public ThemeConfiguration[] newArray(int size) {
            return new ThemeConfiguration[size];
        }
    };

    @Override
    public int compareTo(ThemeConfiguration o) {
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
        String mStylePkgName;
        String mIconPkgName;
        String mFontPkgName;

        public AppTheme(String stylePkgName, String iconPkgName, String fontPkgName) {
            mStylePkgName = stylePkgName;
            mIconPkgName = iconPkgName;
            mFontPkgName = fontPkgName;
        }

        public String getIconPackPkgName() {
            return mIconPkgName;
        }

        public String getStylePkgName() {
            return mStylePkgName;
        }

        public String getFontPackPkgName() {
            return mFontPkgName;
        }

        @Override
        public synchronized int hashCode() {
            int hash = 17;
            hash = 31 * hash + (mStylePkgName == null ? 0 : mStylePkgName.hashCode());
            hash = 31 * hash + (mIconPkgName == null ? 0 : mIconPkgName.hashCode());
            hash = 31 * hash + (mFontPkgName == null ? 0 : mIconPkgName.hashCode());
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
            n = mStylePkgName.equals(o.mStylePkgName) ? 0 : 1;
            return n;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }
            if (object instanceof AppTheme) {
                AppTheme o = (AppTheme) object;
                String currentStylePkgName = (mStylePkgName == null)? "" : mStylePkgName;
                String newStylePkgName = (o.mStylePkgName == null)? "" : o.mStylePkgName;
                String currentIconPkgName = (mIconPkgName == null)? "" : mIconPkgName;
                String newIconPkgName = (o.mIconPkgName == null)? "" : o.mIconPkgName;
                String currentFontPkgName = (mFontPkgName == null)? "" : mFontPkgName;
                String newFontPkgName = (o.mFontPkgName == null)? "" : o.mFontPkgName;


                return (currentIconPkgName.equals(newIconPkgName) &&
                        currentFontPkgName.equals(newFontPkgName) &&
                        currentStylePkgName.equals(newStylePkgName));
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (mStylePkgName != null) {
                result.append("style:");
                result.append(mStylePkgName);
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
        private HashMap<String, String> mOverlays = new HashMap<String, String>();
        private HashMap<String, String> mIcons = new HashMap<String, String>();
        private HashMap<String, String> mFonts = new HashMap<String, String>();

        public Builder() {}

        public Builder(ThemeConfiguration theme) {
            for(Map.Entry<String, AppTheme> entry : theme.mThemes.entrySet()) {
                String key = entry.getKey();
                AppTheme appTheme = entry.getValue();
                mFonts.put(key, appTheme.getFontPackPkgName());
                mIcons.put(key, appTheme.getIconPackPkgName());
                mOverlays.put(key, appTheme.getStylePkgName());
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

        public ThemeConfiguration build() {
            HashSet<String> appPkgSet = new HashSet<String>();
            appPkgSet.addAll(mOverlays.keySet());
            appPkgSet.addAll(mIcons.keySet());
            appPkgSet.addAll(mFonts.keySet());

            HashMap<String, AppTheme> appThemes = new HashMap<String, AppTheme>();
            for(String appPkgName : appPkgSet) {
                String icon = mIcons.get(appPkgName);
                String overlay = mOverlays.get(appPkgName);
                String font = mFonts.get(appPkgName);

                AppTheme appTheme = new AppTheme(overlay, icon, font);
                appThemes.put(appPkgName, appTheme);
            }
            return new ThemeConfiguration(appThemes);
        }
    }


    public static class JsonSerializer {
        private static final String NAME_STYLE_PKG = "stylePkgName";
        private static final String NAME_ICON_PKG = "iconPkgName";
        private static final String NAME_FONT_PKG = "fontPkgName";

        public static String toJson(ThemeConfiguration theme) {
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
                closeQuiet(writer);
                closeQuiet(jsonWriter);
            }
            return json;
        }

        private static void writeTheme(JsonWriter writer, ThemeConfiguration theme)
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
            writer.name(NAME_STYLE_PKG).value(appTheme.mStylePkgName);
            writer.name(NAME_ICON_PKG).value(appTheme.mIconPkgName);
            writer.name(NAME_FONT_PKG).value(appTheme.mFontPkgName);
            writer.endObject();
        }

        public static ThemeConfiguration fromJson(String json) {
            if (json == null) return null;
            HashMap<String, AppTheme> map = new HashMap<String, AppTheme>();
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
                Log.e(TAG, "Could not parse CustomTheme from: " + json, e);
            } finally {
                closeQuiet(reader);
                closeQuiet(jsonReader);
            }
            return new ThemeConfiguration(map);
        }

        private static AppTheme readAppTheme(JsonReader reader) throws IOException {
            String style = null;
            String icon = null;
            String font = null;

            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                if (NAME_STYLE_PKG.equals(name)) {
                    style = reader.nextString();
                } else if (NAME_ICON_PKG.equals(name)) {
                    icon = reader.nextString();
                } else if (NAME_FONT_PKG.equals(name)) {
                    font = reader.nextString();
                }
            }
            reader.endObject();

            return new AppTheme(style, icon, font);
        }

        private static void closeQuiet(Reader reader) {
            try {
                if (reader != null) reader.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuiet(JsonReader reader) {
            try {
                if (reader != null) reader.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuiet(Writer writer) {
            try {
                if (writer != null) writer.close();
            } catch(IOException e) {
            }
        }

        private static void closeQuiet(JsonWriter writer) {
            try {
                if (writer != null) writer.close();
            } catch(IOException e) {
            }
        }
    }

    public static class HoloConfiguration extends ThemeConfiguration {

        public HoloConfiguration() {
            super(new HashMap<String, AppTheme>());
        }
    }

    public static class HoloTheme extends AppTheme {
        public HoloTheme() {
            super(HOLO_DEFAULT, HOLO_DEFAULT, HOLO_DEFAULT);
        }

        @Override
        public String toString() {
            return "No Theme Applied (Holo)";
        }
    }
}
