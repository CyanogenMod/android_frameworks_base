/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Slog;
import com.android.internal.R;
import com.android.internal.os.IKillSwitch;
import com.android.internal.os.IKillSwitchService;
import dalvik.system.DexClassLoader;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @hide
 */
public class KillSwitchService extends IKillSwitchService.Stub {

    private static final String TAG = KillSwitchService.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private Context mContext;

    private IKillSwitch mKillSwitchImpl;
    private List<String> mPackagesAllowedToWrite = new ArrayList<String>();

    public KillSwitchService(Context context) {
        mContext = context;

        String deviceKillSwitchLib = mContext.getResources().getString(
                com.android.internal.R.string.config_killSwitchLib);

        String deviceKillSwitchClass = mContext.getResources().getString(
                com.android.internal.R.string.config_killSwitchClass);

        if (!deviceKillSwitchLib.isEmpty() && !deviceKillSwitchClass.isEmpty()) {
            DexClassLoader loader = new DexClassLoader(deviceKillSwitchLib,
                    new ContextWrapper(mContext).getCacheDir().getAbsolutePath(),
                    null,
                    ClassLoader.getSystemClassLoader());
            try {
                Class<?> klass = loader.loadClass(deviceKillSwitchClass);
                Constructor<?> constructor = klass.getConstructor();
                mKillSwitchImpl = (IKillSwitch) constructor.newInstance();
                if (DEBUG) Slog.d(TAG, "KillSwitch class loaded");

                String[] stringArray = mContext.getResources().getStringArray(
                        R.array.config_packagesAllowedAccessToKillSwitch);
                mPackagesAllowedToWrite.addAll(Arrays.asList(stringArray));
            } catch (Exception e) {
                mKillSwitchImpl = null;
                mPackagesAllowedToWrite.clear();
                Slog.w(TAG, "Could not instantiate KillSwitch "
                        + deviceKillSwitchClass + " from class "
                        + deviceKillSwitchLib, e);
            }
        }
    }

    private boolean verifyWritePermission() {
        String[] packagesForUid = mContext.getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        if (packagesForUid != null) {
            for (int i = 0; i < packagesForUid.length; i++) {
                if (mPackagesAllowedToWrite.contains(packagesForUid[i])) {
                    return true;
                }
            }
        }
        throw new SecurityException("not in security access list");
    }

    @Override
    public boolean hasKillSwitch() {
        return mKillSwitchImpl != null;
    }

    @Override
    public void setDeviceUuid(String uuid) throws RemoteException {
        verifyWritePermission();
        if (mKillSwitchImpl != null) {
            mKillSwitchImpl.setDeviceUuid(uuid);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String getDeviceUuid() throws RemoteException {
        verifyWritePermission();
        if (mKillSwitchImpl != null) {
            return mKillSwitchImpl.getDeviceUuid();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isDeviceLocked() throws RemoteException {
        if (mKillSwitchImpl != null) {
            return mKillSwitchImpl.isDeviceLocked();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setDeviceLocked(boolean locked) throws RemoteException {
        verifyWritePermission();
        if (mKillSwitchImpl != null) {
            mKillSwitchImpl.setDeviceLocked(locked);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setAccountId(String value) throws RemoteException {
        verifyWritePermission();
        if (mKillSwitchImpl != null) {
            mKillSwitchImpl.setAccountId(value);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String getAccountId() throws RemoteException {
        verifyWritePermission();
        if (mKillSwitchImpl != null) {
            return mKillSwitchImpl.getAccountId();
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
