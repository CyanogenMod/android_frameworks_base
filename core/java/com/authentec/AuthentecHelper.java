/*
 * Copyright (C) 2011 The CyanogenMOD Project
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

package com.authentec;

import dalvik.system.DexClassLoader;

import android.content.Context;
import android.content.ContextWrapper;

import android.os.SystemProperties;

import java.lang.reflect.Constructor;

public class AuthentecHelper
{
    private static DexClassLoader sAuthentecClassLoader = null;
    private Class TSM = null;
    private Class AuthentecMobile = null;
    private Object am = null;

    /* AM_STATUS codes */
    public static final int eAM_STATUS_ACCESS_ERROR = 6;
    public static final int eAM_STATUS_APPLICATION_IO_ERROR = 11;
    public static final int eAM_STATUS_CAPTURE_FAILED = 15;
    public static final int eAM_STATUS_CLIENT_CANCELED = 17;
    public static final int eAM_STATUS_CLIENT_NOT_PERMITTED = 18;
    public static final int eAM_STATUS_CREDENTIAL_LOCKED = 3;
    public static final int eAM_STATUS_CREDENTIAL_TOO_LARGE = 5;
    public static final int eAM_STATUS_DATABASE_FULL = 7;
    public static final int eAM_STATUS_FINGERS_NOT_PROVISIONED = 19;
    public static final int eAM_STATUS_FOREIGN_DATABASE = 8;
    public static final int eAM_STATUS_GUI_IS_OFFLINE = 21;
    public static final int eAM_STATUS_INVALID_APP_CONTEXT = 10;
    public static final int eAM_STATUS_INVALID_PARAMETER = 4;
    public static final int eAM_STATUS_INVALID_USER_ID = 22;
    public static final int eAM_STATUS_LIBRARY_NOT_AVAILABLE = 14;
    public static final int eAM_STATUS_NO_ACTIVE_USER = 20;
    public static final int eAM_STATUS_NO_STORED_CREDENTIAL = 2;
    public static final int eAM_STATUS_OK = 0;
    public static final int eAM_STATUS_TIMEOUT = 12;
    public static final int eAM_STATUS_UI_TIMEOUT = 16;
    public static final int eAM_STATUS_UNKNOWN_COMMAND = 1;
    public static final int eAM_STATUS_UNKNOWN_ERROR = 99;
    public static final int eAM_STATUS_USER_CANCELED = 9;
    public static final int eAM_STATUS_USURP_FAILURE = 13;

    private AuthentecHelper(Context ctx)
    {
        try {
            AuthentecMobile = sAuthentecClassLoader.loadClass("com.authentec.amjni.AuthentecMobile");
            TSM = sAuthentecClassLoader.loadClass("com.authentec.amjni.TSM");
            Constructor ctor = AuthentecMobile.getDeclaredConstructors()[0];
            am = ctor.newInstance(ctx);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }

    public int fingerprintUnlock(String sScreen, Context ctx) {
        int iResult = 0;

        try {
            if (! (Boolean) AuthentecMobile.getMethod("AM2ClientLibraryLoaded").invoke(am)) {
                return eAM_STATUS_LIBRARY_NOT_AVAILABLE;
            }

            if(null == ctx) {
                return eAM_STATUS_INVALID_PARAMETER;
            }

            //int iResult = TSM.LAP(ctx).verify().viaGfxScreen(sScreen).exec();
            Class partTypes[] = new Class[1];
            Object argList[] = new Object[1];

            partTypes[0] = Context.class;
            argList[0] = ctx;
            Object TSMi = TSM.getMethod("LAP", partTypes).invoke(null, argList);

            TSM.getMethod("verify").invoke(TSMi);

            partTypes[0] = String.class;
            argList[0] = sScreen;
            TSM.getMethod("viaGfxScreen", partTypes).invoke(TSMi, argList);

            iResult = (Integer) TSM.getMethod("exec").invoke(TSMi);
            TSMi = null;

            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return iResult;
    }

    public int startEnrollmentWizard(Context choosefinger, String msTempPasscode)
    {
        int miResult = eAM_STATUS_LIBRARY_NOT_AVAILABLE;

        try {
            Class partTypes[] = new Class[1];
            Object argList[] = new Object[1];

            partTypes[0] = Context.class;
            argList[0] = choosefinger;
            Object TSMi = TSM.getMethod("LAP", partTypes).invoke(null, argList);

            if (msTempPasscode != null) {
                partTypes[0] = String.class;
                argList[0] = msTempPasscode;
                argList[0] = (String) TSM.getMethod("Hexify", partTypes).invoke(null, argList);

                TSM.getMethod("usingPasscode", partTypes).invoke(TSMi, argList);

                TSM.getMethod("enroll").invoke(TSMi);
                //miResult = TSM.LAP(ChooseLockFinger.this).usingPasscode(TSM.Hexify(msTempPasscode)).enroll().exec();
            } else {
                partTypes[0] = String.class;
                argList[0] = "_classicEnroll";
                TSM.getMethod("addFunction", partTypes).invoke(TSMi, argList);
                //miResult = TSM.LAP(ChooseLockFinger.this).addFunction("_classicEnroll").exec();
            }

            miResult = (Integer) TSM.getMethod("exec").invoke(TSMi);
            TSMi = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return miResult;
    }

    public int startVerification(Context choosefinger)
    {
        int miResult = eAM_STATUS_LIBRARY_NOT_AVAILABLE;

        try {
            //miResult = TSM.LAP(ChooseLockFinger.this).verify().viaGfxScreen("lap-verify").exec();
            Class partTypes[] = new Class[1];
            Object argList[] = new Object[1];

            partTypes[0] = Context.class;
            argList[0] = choosefinger;
            Object TSMi = TSM.getMethod("LAP", partTypes).invoke(null, argList);

            TSM.getMethod("verify").invoke(TSMi);

            partTypes[0] = String.class;
            argList[0] = "lap-verify";
            TSM.getMethod("viaGfxScreen", partTypes).invoke(TSMi, argList);

            miResult = (Integer) TSM.getMethod("exec").invoke(TSMi);
            TSMi = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return miResult;
    }

    public int verifyPolicy(Context ctx)
    {
        int iResult = eAM_STATUS_LIBRARY_NOT_AVAILABLE;

        try {
            //iResult = TSM.LAP(m_Context).waitForUI().verify().exec();
            Class partTypes[] = new Class[1];
            Object argList[] = new Object[1];

            partTypes[0] = Context.class;
            argList[0] = ctx;
            Object TSMi = TSM.getMethod("LAP", partTypes).invoke(null, argList);

            TSM.getMethod("waitForUI").invoke(TSMi, (Object[]) null);

            TSM.getMethod("verify").invoke(TSMi, (Object[]) null);

            iResult = (Integer) TSM.getMethod("exec").invoke(TSMi, (Object[]) null);
            TSMi = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return iResult;
    }

    public static AuthentecHelper getInstance(Context ctx)
    {
        ensureClassLoader(ctx);
        return new AuthentecHelper(ctx);
    }

    public static void ensureClassLoader(Context ctx)
    {
        if (sAuthentecClassLoader != null) {
            return;
        }

        String authentecJarLocation = SystemProperties.get("ro.authentec.fingerprint.jar","");
        String authentecSoLocation = SystemProperties.get("ro.authentec.fingerprint.so","");

        if ("".equals(authentecJarLocation) || "".equals(authentecSoLocation))
            return;

        sAuthentecClassLoader =  new DexClassLoader(authentecJarLocation,
                new ContextWrapper(ctx).getCacheDir().getAbsolutePath(),
                authentecSoLocation,ClassLoader.getSystemClassLoader());
    }
}
