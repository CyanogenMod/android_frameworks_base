/*
 * Copyright (C) 2011-2014 The CyanogenMod Project
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

package android.net.wimax;

import dalvik.system.DexClassLoader;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;
import android.provider.Settings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * {@hide}
 */
public class WimaxHelper {

    private static final String TAG = "WimaxHelper";

    private static final String WIMAX_CONTROLLER_CLASSNAME = "com.htc.net.wimax.WimaxController";
    private static final String WIMAX_MANAGER_CLASSNAME    = "android.net.fourG.wimax.Wimax4GManager";

    private static DexClassLoader sWimaxClassLoader;
    private static String sWimaxManagerClassname, sIsWimaxEnabledMethodname,
                          sSetWimaxEnabledMethodname, sGetWimaxStateMethodname;

    public static boolean isWimaxSupported(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
    }

    public static DexClassLoader getWimaxClassLoader(Context context) {
        if (isWimaxSupported(context)) {
            if (sWimaxClassLoader == null) {
                sWimaxManagerClassname = context.getResources().getString(
                    com.android.internal.R.string.config_wimaxManagerClassname);

                // WimaxController::getWimaxState == Wimax4GManager::get4GState.
                // However, Wimax4GManager also implements a different getWimaxState
                // method, which returns a WimaxState object describing the connection
                // state, not the enabled state.  Other methods are similarly renamed.
                if (sWimaxManagerClassname.equals(WIMAX_CONTROLLER_CLASSNAME)) {
                    sIsWimaxEnabledMethodname  = "isWimaxEnabled";
                    sSetWimaxEnabledMethodname = "setWimaxEnabled";
                    sGetWimaxStateMethodname   = "getWimaxState";
                } else if (sWimaxManagerClassname.equals(WIMAX_MANAGER_CLASSNAME)) {
                    sIsWimaxEnabledMethodname  = "is4GEnabled";
                    sSetWimaxEnabledMethodname = "set4GEnabled";
                    sGetWimaxStateMethodname   = "get4GState";
                }

                String wimaxJarLocation = context.getResources().getString(
                        com.android.internal.R.string.config_wimaxServiceJarLocation);
                String wimaxLibLocation = context.getResources().getString(
                        com.android.internal.R.string.config_wimaxNativeLibLocation);
                sWimaxClassLoader =  new DexClassLoader(wimaxJarLocation,
                        new ContextWrapper(context).getCacheDir().getAbsolutePath(),
                        wimaxLibLocation,ClassLoader.getSystemClassLoader());
            }
            return sWimaxClassLoader;
        }
        return null;
    }

    public static Object createWimaxService(Context context, Handler handler) {
        Object controller = null;

        try {
            DexClassLoader wimaxClassLoader = getWimaxClassLoader(context);
            if (sWimaxManagerClassname.equals(WIMAX_CONTROLLER_CLASSNAME)) {
                // Load supersonic's and speedy's WimaxController.
                IBinder b = ServiceManager.getService(WimaxManagerConstants.WIMAX_SERVICE);
                if (b != null) {
                    Class<?> klass = wimaxClassLoader.loadClass("com.htc.net.wimax.IWimaxController$Stub");
                    if (klass != null) {
                        Method asInterface = klass.getMethod("asInterface", IBinder.class);
                        Object wc = asInterface.invoke(null, b);
                        if (wc != null) {
                            klass = wimaxClassLoader.loadClass(WIMAX_CONTROLLER_CLASSNAME);
                            if (klass != null) {
                                Constructor<?> ctor = klass.getDeclaredConstructors()[1];
                                controller = ctor.newInstance(wc, handler);
                            }
                        }
                    }
                }
            } else if (sWimaxManagerClassname.equals(WIMAX_MANAGER_CLASSNAME)) {
                // Load crespo4g's (and epicmtd's) Wimax4GManager.
                // Note that crespo4g's implementation grabs WIMAX_SERVICE internally, so
                // it doesn't need to be passed in.  Other implementations (may) require
                // WIMAX_SERVICE to be grabbed externally, so check Wimax4GManager::<init>.
                Class<?> klass = wimaxClassLoader.loadClass(WIMAX_MANAGER_CLASSNAME);
                if (klass != null) {
                    Constructor<?> ctor = klass.getDeclaredConstructors()[0];
                    controller = ctor.newInstance();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to create WimaxController instance", e);
        }

        return controller;
    }

    public static boolean isWimaxEnabled(Context context) {
        boolean ret = false;
        try {
            Object wimaxService = context.getSystemService(WimaxManagerConstants.WIMAX_SERVICE);
            Method m = wimaxService.getClass().getMethod(sIsWimaxEnabledMethodname);
            ret = (Boolean) m.invoke(wimaxService);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get WiMAX enabled state!", e);
        }
        return ret;
    }

    public static boolean setWimaxEnabled(Context context, boolean enabled) {
        boolean ret = false;
        try {
            Object wimaxService = context.getSystemService(WimaxManagerConstants.WIMAX_SERVICE);
            Method m = wimaxService.getClass().getMethod(sSetWimaxEnabledMethodname, boolean.class);
            ret = (Boolean) m.invoke(wimaxService, enabled);
            if (ret)
                Settings.Secure.putInt(context.getContentResolver(),
                        Settings.Secure.WIMAX_ON, (Boolean) enabled ? 1 : 0);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set WiMAX state!", e);
        }
        return ret;
    }

    public static int getWimaxState(Context context) {
        int ret = 0;
        try {
            Object wimaxService = context.getSystemService(WimaxManagerConstants.WIMAX_SERVICE);
            Method m = wimaxService.getClass().getMethod(sGetWimaxStateMethodname);
            ret = (Integer) m.invoke(wimaxService);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get WiMAX state!", e);
        }
        return ret;
    }

    public static boolean wimaxRescan(Context context) {
        boolean ret = false;
        try {
            Object wimaxService = context.getSystemService(WimaxManagerConstants.WIMAX_SERVICE);
            Method wimaxRescan = wimaxService.getClass().getMethod("wimaxRescan");
            if (wimaxRescan != null) {
                wimaxRescan.invoke(wimaxService);
                ret = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to perform WiMAX rescan!", e);
        }
        return ret;
    }

    private static Object getWimaxInfo(Context context) {
        Object wimaxInfo = null;
        try {
            Object wimaxService = context.getSystemService(WimaxManagerConstants.WIMAX_SERVICE);
            Method getConnectionInfo = wimaxService.getClass().getMethod("getConnectionInfo");
            wimaxInfo = getConnectionInfo.invoke(wimaxService);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get a WimaxInfo object!", e);
        }
        return wimaxInfo;
    }
}
