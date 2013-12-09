package com.android.internal.util.slim;

import android.os.SystemProperties;

import java.io.FileInputStream;
import java.util.Properties;

public class DensityUtils {

    public static final String DENSITY_PERSIST_PROP = "persist.sys.lcd_density";
    public static final String DENSITY_SYSTEM_PROP = "ro.sf.lcd_density";

    private static final int DENSITY_DEFAULT = 160;

    private static int mCurrentDensity;
    private static int mMaxDensity = getSystemDensity();

    public static void setCurrentDensity(int density) {
        mCurrentDensity = density;
    }

    public static int getCurrentDensity() {
        if (mCurrentDensity == 0) {
            return getDensityFromProp();
        }
        return mCurrentDensity;
    }

    public static int getDensityFromProp() {
        return SystemProperties.getInt(DENSITY_PERSIST_PROP,
                SystemProperties.getInt(DENSITY_SYSTEM_PROP, DENSITY_DEFAULT));
    }

    public static int getSystemDensity() {
        return SystemProperties.getInt(DENSITY_SYSTEM_PROP, getDensityFromProp());
    }

    public static int getSlimDefaultDensity() {
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("/system/build.prop");
            prop.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
            }
        }
        return Integer.parseInt(prop.getProperty(DENSITY_PERSIST_PROP, "-1"));
    }

    public static int getMinimumDensity() {
        int min = -1;
        int[] densities = { 91, 121, 161, 241, 321, 481 };
        for (int density : densities) {
            if (density < mMaxDensity) {
                min = density;
            }
        }
        return min;
    }
}
