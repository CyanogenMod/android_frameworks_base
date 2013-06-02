/*
 * Copyright (C) 2012 ParanoidAndroid Project
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

package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Display;

public class ExtendedPropertiesUtils {

    private static final String TAG = "ExtendedPropertiesUtils";

    /**
     * Public variables
     */
    public static final String BEERBONG_MAINCONF = "properties.conf";
    public static final String BEERBONG_BACKUPCONF = "backup.conf";
    public static final String BEERBONG_PROPERTIES = "/system/etc/beerbong/" + BEERBONG_MAINCONF;
    public static final String BEERBONG_DIR = "/system/etc/beerbong/";
    public static final String BEERBONG_PREFIX = "%";
    public static final String BEERBONG_SEPARATOR = ".";
    public static final String BEERBONG_STRING_DELIMITER = "\\|";
    public static final String BEERBONG_DPI_SUFFIX = ".dpi";
    public static final String BEERBONG_LAYOUT_SUFFIX = ".layout";
    public static final String BEERBONG_DENSITY_SUFFIX = ".den";
    public static final String BEERBONG_SCALEDDENSITY_SUFFIX = ".sden";

    public static HashMap<String, String> mPropertyMap = new HashMap<String, String>();
    public static ActivityThread mMainThread;
    public static Context mContext;
    public static PackageManager mPackageManager;
    public static Display mDisplay;
    public static List<PackageInfo> mPackageList;

    public static BeerbongAppInfo mGlobalHook = new BeerbongAppInfo();
    public BeerbongAppInfo mLocalHook = new BeerbongAppInfo();

    public static boolean sIsHybridModeEnabled;

    public static int mRomLcdDensity = DisplayMetrics.DENSITY_DEFAULT;

    // Native methods
    public static native String readFile(String s);

    /**
     * Contains all the details for an application
     */
    public static class BeerbongAppInfo {

        public String name = "";
        public String path = "";
        public boolean active;
        public int pid;
        public ApplicationInfo info;
        public int dpi;
        public int layout;
        public int firstRun;
        public float scaledDensity;
        public float density;
    }

    /**
     * Enum interface to allow different override modes
     */
    public static enum OverrideMode {
        ExtendedProperties, AppInfo, FullName, FullNameExclude, PackageName
    }

    /**
     * Set app configuration for the input argument <code>info</code>. This is
     * done by fetching properties.conf or our stored {@link HashMap}.
     * 
     * @param info
     *            instance containing app details
     */
    public static void setAppConfiguration(BeerbongAppInfo info) {
        if (sIsHybridModeEnabled) {
            // Load default values to be used in case that property is
            // missing from configuration.
            boolean isSystemApp = info.path.contains("system/app");
            int defaultDpi = getActualProperty(BEERBONG_PREFIX + (isSystemApp ?
                    "system_default_dpi" : (info.path.length() == 0 ? "0" : "user_default_dpi")));
            int defaultLayout = getActualProperty(BEERBONG_PREFIX + "user_default_layout");

            if (defaultLayout == 0) {
                defaultLayout = getActualProperty("com.android.systemui.layout");
            }
            if (defaultDpi == 0) {
                defaultDpi = getActualProperty("com.android.systemui.dpi");
            }

            // Layout fetching.
            info.layout = Integer.parseInt(getProperty(info.name + BEERBONG_LAYOUT_SUFFIX, String.valueOf(defaultLayout)));

            // DPI fetching.
            info.dpi = Integer.parseInt(getProperty(info.name + BEERBONG_DPI_SUFFIX, String.valueOf(defaultDpi)));
            if (info.dpi == 0) {
                info.dpi = defaultDpi;
            }

            // Extra density fetching.
            info.density = Float.parseFloat(getProperty(info.name + BEERBONG_DENSITY_SUFFIX));
            info.scaledDensity = Float.parseFloat(getProperty(info.name + BEERBONG_SCALEDDENSITY_SUFFIX));

            // In case that densities aren't determined in previous step
            // we calculate it by dividing DPI by default density (160).
            if (info.dpi != 0) {
                info.density = info.density == 0 ? info.dpi / (float) DisplayMetrics.DENSITY_DEFAULT : info.density;
                info.scaledDensity = info.scaledDensity == 0 ? info.dpi / (float) DisplayMetrics.DENSITY_DEFAULT
                        : info.scaledDensity;
            }

            info.firstRun = 0;

            // If everything went nice, stop parsing.
            info.active = true;
        }
    }

    /**
     * Overrides current hook with input parameter <code>mode</code>, wich is an
     * enum interface that stores basic override possibilities.
     * 
     * @param input
     *            object to be overriden
     * @param mode
     *            enum interface
     */
    public void overrideHook(Object input, OverrideMode mode) {
        if (isInitialized() && input != null) {

            ApplicationInfo tempInfo;
            ExtendedPropertiesUtils tempProps;

            switch (mode) {
                case ExtendedProperties:
                    tempProps = (ExtendedPropertiesUtils) input;
                    if (tempProps.mLocalHook.active) {
                        mLocalHook.active = tempProps.mLocalHook.active;
                        mLocalHook.pid = tempProps.mLocalHook.pid;
                        mLocalHook.info = tempProps.mLocalHook.info;
                        mLocalHook.name = tempProps.mLocalHook.name;
                        mLocalHook.path = tempProps.mLocalHook.path;
                        mLocalHook.dpi = tempProps.mLocalHook.dpi;
                        mLocalHook.layout = tempProps.mLocalHook.layout;
                        mLocalHook.scaledDensity = tempProps.mLocalHook.scaledDensity;
                        mLocalHook.density = tempProps.mLocalHook.density;
                    }
                    return;
                case AppInfo:
                    mLocalHook.info = (ApplicationInfo) input;
                    break;
                case FullName:
                    mLocalHook.info = getAppInfoFromPath((String) input);
                    break;
                case FullNameExclude:
                    tempInfo = getAppInfoFromPath((String) input);
                    if (tempInfo != null && !isHooked()) {
                        mLocalHook.info = tempInfo;
                    }
                    break;
                case PackageName:
                    mLocalHook.info = getAppInfoFromPackageName((String) input);
                    break;
            }

            if (mLocalHook.info != null) {
                mLocalHook.pid = android.os.Process.myPid();
                mLocalHook.name = mLocalHook.info.packageName;
                mLocalHook.path = mLocalHook.info.sourceDir.substring(0,
                        mLocalHook.info.sourceDir.lastIndexOf("/"));

                setAppConfiguration(mLocalHook);
            }
        }
    }

    /**
     * This methods are used to retrieve specific information for a hook.
     */
    public static boolean isInitialized() {
        return (mContext != null);
    }

    public static boolean isHooked() {
        return (isInitialized() && !mGlobalHook.name.equals("android") && !mGlobalHook.name.equals(""));
    }

    public boolean getActive() {
        return mLocalHook.active ? mLocalHook.active : mGlobalHook.active;
    }

    public int getPid() {
        return mLocalHook.active ? mLocalHook.pid : mGlobalHook.pid;
    }

    public ApplicationInfo getInfo() {
        return mLocalHook.active ? mLocalHook.info : mGlobalHook.info;
    }

    public String getName() {
        return mLocalHook.active ? mLocalHook.name : mGlobalHook.name;
    }

    public String getPath() {
        return mLocalHook.active ? mLocalHook.path : mGlobalHook.path;
    }

    public int getDpi() {
        return mLocalHook.active ? mLocalHook.dpi : mGlobalHook.dpi;
    }

    public int getLayout() {
        return mLocalHook.active ? mLocalHook.layout : mGlobalHook.layout;
    }

    public float getScaledDensity() {
        return mLocalHook.active ? mLocalHook.scaledDensity : mGlobalHook.scaledDensity;
    }

    public float getDensity() {
        return mLocalHook.active ? mLocalHook.density : mGlobalHook.density;
    }

    /**
     * Returns whether if device is running hybrid mode
     * 
     * @return hybrid mode enabled
     */
    public static boolean isHybridModeEnabled() {
        return sIsHybridModeEnabled;
    }

    /**
     * Returns whether if device is on tablet UI or not
     * 
     * @return device is tablet
     */
    public static boolean isTablet() {
        int dpi;
        String prop = readProperty("com.android.systemui.dpi", "0");
        if (isParsableToInt(prop)) {
            dpi = Integer.parseInt(prop);
        } else {
            dpi = getActualProperty(prop);
        }
        return dpi < 213;
    }

    /**
     * Returns an {@link ApplicationInfo}, with the given path.
     * 
     * @param path
     *            the apk path
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPath(String path) {
        if (isInitialized()) {
            for (int i = 0; mPackageList != null && i < mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo.sourceDir.equals(path)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns an {@link ApplicationInfo}, with the given package name.
     * 
     * @param packageName
     *            the application package name
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPackageName(String packageName) {
        if (isInitialized()) {
            for (int i = 0; mPackageList != null && i < mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo.packageName.equals(packageName)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns an {@link ApplicationInfo}, with the given PID.
     * 
     * @param pid
     *            the application PID
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPID(int pid) {
        if (isInitialized()) {
            List<RunningAppProcessInfo> mProcessList = ((ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE))
                    .getRunningAppProcesses();
            Iterator<RunningAppProcessInfo> mProcessListIt = mProcessList.iterator();
            while (mProcessListIt.hasNext()) {
                ActivityManager.RunningAppProcessInfo mAppInfo = (ActivityManager.RunningAppProcessInfo) (mProcessListIt
                        .next());
                if (mAppInfo.pid == pid) {
                    return getAppInfoFromPackageName(mAppInfo.processName);
                }
            }
        }
        return null;
    }

    /**
     * Traces the input argument <code>msg</code> as a log. Used for debugging.
     * Should not be used on public classes.
     * 
     * @param msg
     *            the message to log
     */
    public static void traceMsg(String msg) {
        StringWriter sw = new StringWriter();
        new Throwable("").printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        Log.i(TAG + ":" + msg, "Trace=" + stackTrace);
    }

    /**
     * Updates the {@link HashMap} that contains all the properties.
     */
    public static void refreshProperties() {
        mPropertyMap.clear();
        String[] props = readFile(BEERBONG_PROPERTIES).split("\n");
        for (int i = 0; i < props.length; i++) {
            if (!props[i].startsWith("#")) {
                String[] pair = props[i].split("=");
                if (pair.length == 2) {
                    mPropertyMap.put(pair[0].trim(), pair[1].trim());
                }
            }
        }
    }

    /**
     * Returns a {@link String}, containing the result of the configuration for
     * the input argument <code>prop</code>. If the property is not found it
     * returns zero.
     * 
     * @param prop
     *            a string containing the property to checkout
     * @return current stored value of property
     */
    public static String getProperty(String prop) {
        return getProperty(prop, String.valueOf(0));
    }

    /**
     * Returns a {@link String}, containing the result of the configuration for
     * the input argument <code>prop</code>. If the property is not found it
     * returns the input argument <code>def</code>.
     * 
     * @param prop
     *            a string containing the property to checkout
     * @param def
     *            default value to be returned in case that property is missing
     * @return current stored value of property
     */
    public static String getProperty(String prop, String def) {
        try {
            if (isInitialized()) {
                String result = mPropertyMap.get(prop);
                if (result == null)
                    return def;
                if (result.startsWith(BEERBONG_PREFIX)) {
                    result = getProperty(result, def);
                }
                return result;
            } else {
                return readProperty(prop, def);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return def;
    }

    /**
     * Returns a {@link String}, containing the result of the configuration for
     * the input argument <code>prop</code>. If the property is not found it
     * returns the input argument <code>def</code>. This property is directly
     * read from the configuration file.
     * 
     * @param prop
     *            a string containing the property to checkout
     * @param def
     *            default value to be returned in case that property is missing
     * @return current stored value of property
     */
    public static String readProperty(String prop, String def) {
        String[] props = readFile(BEERBONG_PROPERTIES).split("\n");
        for (int i = 0; i < props.length; i++) {
            if (props[i].contains("=")) {
                if (props[i].substring(0, props[i].lastIndexOf("=")).equals(prop)) {
                    String result = props[i].replace(prop + "=", "").trim();
                    if (result.startsWith(BEERBONG_PREFIX)) {
                        result = getProperty(result, def);
                    }
                    return result;
                }
            }
        }
        return def;
    }

    /**
     * Returns an {@link Integer}, equivalent to what other classes will
     * actually load for the input argument <code>property</code>. it differs
     * from {@link #getProperty(String, String) getProperty}, because the values
     * returned will never be zero.
     * 
     * @param property
     *            a string containing the property to checkout
     * @return the actual integer value of the selected property
     * @see getProperty
     */
    public static int getActualProperty(String property) {
        int result = 0;
        boolean getProp = false;

        if (property.endsWith(BEERBONG_DPI_SUFFIX)) {
            ApplicationInfo appInfo = getAppInfoFromPackageName(property.substring(0, property.length()
                    - BEERBONG_DPI_SUFFIX.length()));
            if (appInfo != null) {
                boolean isSystemApp =
                        appInfo.sourceDir.substring(0, appInfo.sourceDir.lastIndexOf("/")).contains("system/app");
                result = Integer.parseInt(getProperty(property, getProperty(BEERBONG_PREFIX + (isSystemApp ?
                        "system_default_dpi" : "user_default_dpi"))));
            } else {
                getProp = true;
            }
        } else if (property.endsWith(BEERBONG_LAYOUT_SUFFIX)) {
            ApplicationInfo appInfo = getAppInfoFromPackageName(property.substring(0, property.length()
                    - BEERBONG_LAYOUT_SUFFIX.length()));
            if(appInfo != null) {
                boolean isSystemApp =
                        appInfo.sourceDir.substring(0, appInfo.sourceDir.lastIndexOf("/")).contains("system/app");
                result = Integer.parseInt(getProperty(property, getProperty(BEERBONG_PREFIX + (isSystemApp ? 
                        "system_default_layout" : "user_default_layout"))));
            } else {
                getProp = true;
            }
        } else if (property.endsWith("_dpi") || property.endsWith("_layout")) {
            getProp = true;
        }

        if (getProp)
            result = Integer.parseInt(getProperty(property));

        if (result == 0) {
            result = Integer.parseInt(property.endsWith("dpi") ? getProperty(BEERBONG_PREFIX + "rom_default_dpi")
                    : getProperty(BEERBONG_PREFIX + "rom_default_layout"));
        }

        return result;
    }

    /**
     * Returns a {@link Boolean}, meaning if the input argument is an integer
     * number.
     * 
     * @param str
     *            the string to be tested
     * @return the string is an integer number
     */
    public static boolean isParsableToInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    public void debugOut(String msg) {
        Log.i(TAG + ":" + msg, "Init=" + (mMainThread != null && mContext != null &&
                mPackageManager != null) + " App=" + getName() + " Dpi=" + getDpi());
    }
}
