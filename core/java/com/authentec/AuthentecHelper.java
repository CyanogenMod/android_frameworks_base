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
    private static AuthentecHelper instance = null;
    private DexClassLoader sAuthentecClassLoader = null;
    private Class AM_STATUS = null;
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
        String authentecJarLocation = SystemProperties.get("ro.authentec.fingerprint.jar","");
        String authentecSoLocation = SystemProperties.get("ro.authentec.fingerprint.so","");

        if ("".equals(authentecJarLocation) || "".equals(authentecSoLocation))
            return;

        sAuthentecClassLoader =  new DexClassLoader(authentecJarLocation,
                new ContextWrapper(ctx).getCacheDir().getAbsolutePath(),
                authentecSoLocation,ClassLoader.getSystemClassLoader());

        try {
            AM_STATUS = sAuthentecClassLoader.loadClass("com.authentec.amjni.AM_STATUS");
            AuthentecMobile = sAuthentecClassLoader.loadClass("com.authentec.amjni.AuthentecMobile");
            TSM = sAuthentecClassLoader.loadClass("com.authentec.amjni.TSM");
            Constructor ctor = AuthentecMobile.getDeclaredConstructors()[0];
            am = ctor.newInstance(ctx);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }

    public Class getAMStatus()
    {
        return AM_STATUS;
    }

    public Class getAuthentecMobile()
    {
        return AuthentecMobile;
    }

    public Class getTSM()
    {
        return TSM;
    }

    public Object getAuthentecMobileInstance()
    {
        return am;
    }

    public int fingerprintUnlockFromLPU(String sScreen, Context ctx) {
        int iResult = 0;

        try {
            if (! (Boolean) AuthentecMobile.getMethod("AM2ClientLibraryLoaded").invoke(am)) {
                return AM_STATUS.getDeclaredField("eAM_STATUS_LIBRARY_NOT_AVAILABLE").getInt(AM_STATUS);
            }

            if(null == ctx){
                return AM_STATUS.getDeclaredField("eAM_STATUS_INVALID_PARAMETER").getInt(AM_STATUS);
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
        } catch (Exception e){e.printStackTrace();}

        return iResult;
    }

    public static AuthentecHelper getInstance(Context ctx)
    {
        if (null == instance)
            instance = new AuthentecHelper(ctx);

        return instance;
    }
}
