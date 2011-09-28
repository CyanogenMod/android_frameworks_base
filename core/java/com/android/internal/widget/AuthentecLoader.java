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

package com.android.internal.widget;

import dalvik.system.DexClassLoader;

import android.content.Context;
import android.content.ContextWrapper;

import android.os.SystemProperties;

public class AuthentecLoader
{
    private static AuthentecLoader instance = null;
    private DexClassLoader sAuthentecClassLoader = null;
    private Class AM_STATUS = null;
    private Class TSM = null;
    private Class AuthentecMobile = null;

    private AuthentecLoader(Context ctx)
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
        } catch (ClassNotFoundException ex) {
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

    public static AuthentecLoader getInstance(Context ctx)
    {
        if (null == instance)
            instance = new AuthentecLoader(ctx);

        return instance;
    }
}
