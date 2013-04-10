package com.android.internal.util.pie;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.provider.Settings;
import java.util.HashMap;
import java.util.Map;

/**
 * Small utility class to retrieve colors from the settings provider as well as receive default
 * colors from the resource file.
 */
public class PieColorUtils {

    public static final PieColor COLOR_NORMAL =
            new PieColor("com.android.systemui:color/pie_background_color");
    public static final PieColor COLOR_SELECTED =
            new PieColor("com.android.systemui:color/pie_selected_color");
    public static final PieColor COLOR_LONG_PRESSED =
            new PieColor("com.android.systemui:color/pie_long_pressed_color");
    public static final PieColor COLOR_OUTLINE =
            new PieColor("com.android.systemui:color/pie_outline_color");
    public static final PieColor COLOR_ICON =
            new PieColor("com.android.systemui:color/pie_foreground_color");

    public static final PieColor[] ALL_COLOR_SETTINGS = {
        COLOR_NORMAL, COLOR_SELECTED, COLOR_LONG_PRESSED, COLOR_OUTLINE, COLOR_ICON
    };

    public static final class PieColorSettings {
        private final Map<String, Integer> mColors = new HashMap<String, Integer> ();

        private PieColorSettings() {
        }

        public int getColor(PieColor color) {
            if (mColors.containsKey(color.getKey())) {
                return mColors.get(color.getKey());
            }
            return 0xff000000;
        }

        public void setColor(PieColor color, int colorValue) {
            mColors.put(color.getKey(), colorValue);
        }

        public void setColorIfNotPresent(PieColor color, int colorValue) {
            if (!mColors.containsKey(color.getKey())) {
                mColors.put(color.getKey(), colorValue);
            }
        }

        public void removeColor(PieColor color) {
            mColors.remove(color.getKey());
        }

        public boolean isColorPresent(PieColor color) {
            return mColors.containsKey(color.getKey());
        }

        private void insertColor(String string) {
            String[] parts = string.split("=");
            if (parts.length == 2) {
                try {
                    mColors.put(parts[0], (int) (Long.parseLong(parts[1], 16) & 0xffffffffL));
                } catch (NumberFormatException e) {
                    /* ignore, if we can't parse it we don't add it */
                }
            }
        }
    }

    /**
     * Loads the default color scheme from the system UI package, if possible. If the
     * system UI package is not found, an empty {@link PieColorSettings} may be returned.
     */
    public static PieColorSettings loadDefaultPieColors(Context context) {
        final PieColorSettings colorSettings = new PieColorSettings();
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            try {
                applyDefaultPieColors(colorSettings,
                        pm.getResourcesForApplication("com.android.systemui"));
            } catch (NameNotFoundException e) {
                /* ignore, if we can't access the packed no defaults are present */
            }
        }
        return colorSettings;
    }

    private static void applyDefaultPieColors(PieColorSettings colorSettings, Resources systemUiResources) {
        for (PieColor color : ALL_COLOR_SETTINGS) {
            int resId = systemUiResources.getIdentifier(color.getFallbackResId(), null, null);
            if (resId != 0) {
                colorSettings.setColorIfNotPresent(color, systemUiResources.getColor(resId));
            }
        }
    }

    /**
     * Load the current {@link PieColorSettings}. If {@code forceLoad} is set to {@code true}
     * the returned color settings contains all colors, otherwise only colors stored within
     * the setting provider will be returned.
     */
    public static PieColorSettings loadPieColors(Context context, boolean forceLoad) {
        Resources systemUiResources = null;
        if (forceLoad) {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                try {
                    systemUiResources = pm.getResourcesForApplication("com.android.systemui");
                } catch (Exception e) {
                    /* ignore */
                }
            }
        }
        return loadPieColors(context, systemUiResources);
    }

    /**
     * Load the current {@link PieColorSettings}. It is recommended to call this, if you are
     * inside the system UI package, or you have already loaded the system UI resources.
     * All color settings will be available if {@code systemUiResources} is specified.
     */
    public static PieColorSettings loadPieColors(Context context, Resources systemUiResources) {
        final PieColorSettings colorSettings = new PieColorSettings();
        String settings = Settings.System.getString(context.getContentResolver(),
                Settings.System.PIE_COLORS);

        if (settings != null) {
            String[] singleColors = settings.split("\\|");
            for (String color : singleColors) {
                colorSettings.insertColor(color);
            }
        }
        if (systemUiResources != null) {
            applyDefaultPieColors(colorSettings, systemUiResources);
        }

        return colorSettings;
    }

    /**
     * Stores the given {@link PieColorSettings} in the settings provider.
     * Note, this only stores colors that are present in given color settings.
     */
    public static void storePieColors(Context context, PieColorSettings colorSettings) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : colorSettings.mColors.keySet()) {
            if (!first) {
                sb.append("|");
            }
            sb.append(key).append("=").append(Integer.toHexString(colorSettings.mColors.get(key)));
            first = false;
        }

        Settings.System.putString(context.getContentResolver(),
                Settings.System.PIE_COLORS, sb.toString());
    }


    public static final class PieColor {
        private final String mKey;
        private final String mFallbackResId;

        private PieColor(String fallbackResId) {
            mFallbackResId = fallbackResId;
            mKey = fallbackResId.substring(fallbackResId.lastIndexOf("/") + 1);
        }

        public String getKey() {
            return mKey;
        }

        public String getFallbackResId() {
            return mFallbackResId;
        }
    }
}
