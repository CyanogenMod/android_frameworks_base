/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.hardware;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * @hide
 */
public final class CmhwManager {
    private static final String TAG = "CmhwManager";

    private final String mPackageName;
    private final ICmhwService mService;

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    public CmhwManager(Context context) {
        mPackageName = context.getPackageName();
        mService = ICmhwService.Stub.asInterface(
                ServiceManager.getService(Context.CMHW_SERVICE));
    }

    public boolean isTapToWakeSupported() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.isTapToWakeSupported();
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean getTapToWake() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.getTapToWake();
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean setTapToWake(boolean enabled) {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            mService.setTapToWake(enabled);
            return true;
        } catch (RemoteException e) {
        }
        return false;
    }
}
