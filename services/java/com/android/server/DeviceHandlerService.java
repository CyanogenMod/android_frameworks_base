/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.server;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Binder;
import android.util.Log;

import com.android.internal.os.DeviceDockBatteryHandler;
import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.os.IDeviceHandler;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Constructor;

/**
 * DeviceHandlerService exposed device specific handlers to other services.
 * All specific device, not implemented by aosp, should be included here.
 * @see DeviceKeyHandler
 * @see DeviceDockBatteryHandler
 * @hide
 */
public final class DeviceHandlerService extends Binder implements IDeviceHandler {
    private static final String TAG = DeviceHandlerService.class.getSimpleName();

    private static final boolean DEBUG = true;

    private final Context mContext;

    private DeviceKeyHandler mDeviceKeyHandler = null;
    private DeviceDockBatteryHandler mDeviceDockBatteryHandler = null;

    public DeviceHandlerService(Context context) {
        mContext = context;
        registerHandlers();
    }

    private void registerHandlers() {
        // For register device specific handlers at least a library with the
        // implementations is required.
        Resources res = mContext.getResources();
        String deviceHandlersLib =
                res.getString(
                        com.android.internal.R.string.config_deviceHandlersLib);
        if (deviceHandlersLib.isEmpty()) {
            Log.i(TAG, "No device specific handler lib was defined.");
            return;
        }
        ClassLoader classLoader =
                new DexClassLoader(
                        deviceHandlersLib,
                        new ContextWrapper(mContext).getCacheDir().getAbsolutePath(),
                        null,
                        ClassLoader.getSystemClassLoader());

        // ------------------
        // DeviceKeyHandler
        // ------------------
        String handlerClass =
                res.getString(com.android.internal.R.string.config_deviceKeyHandlerClass);
        if (!handlerClass.isEmpty()) {
            mDeviceKeyHandler = (DeviceKeyHandler)getHandler(classLoader, "key", handlerClass);
        }

        // ------------------
        // DeviceDockBatteryHandler
        // ------------------
        // Has dock battery? and device specific handler?
        boolean hasDockBattery = res.getBoolean(com.android.internal.R.bool.config_hasDockBattery);
        if (hasDockBattery) {
            handlerClass =
                    res.getString(
                            com.android.internal.R.string.config_deviceDockBatteryHandlerClass);
            if (!handlerClass.isEmpty()) {
                mDeviceDockBatteryHandler =
                        (DeviceDockBatteryHandler)getHandler(
                                classLoader, "dock battery", handlerClass);
            }
        }
    }

    private Object getHandler(final ClassLoader classLoader,
            final String type, final String handlerClass) {
        try {
            Class<?> clazz = classLoader.loadClass(handlerClass);
            Constructor<?> constructor = clazz.getConstructor(Context.class);
            Object handler = constructor.newInstance(mContext);
            if(DEBUG) {
                Log.d(TAG, String.format("Instantiated %s handler class %s", type, handlerClass));
            }
            return handler;

        } catch (Exception e) {
            Log.w(TAG,
                   String.format(
                           "Could not instantiate %s handler class %s", type, handlerClass), e);
        }
        return null;
    }

    public DeviceKeyHandler getDeviceKeyHandler() {
        return mDeviceKeyHandler;
    }

    public DeviceDockBatteryHandler getDeviceDockBatteryHandler() {
        return mDeviceDockBatteryHandler;
    }
}
