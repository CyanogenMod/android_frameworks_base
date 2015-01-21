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
import android.content.pm.PackageManager;
import android.hardware.ICmhwService;
import android.util.Log;

import org.cyanogenmod.hardware.SerialNumber;
import org.cyanogenmod.hardware.TapToWake;

public class CmhwService extends ICmhwService.Stub {
    private static final boolean DEBUG = true;
    private static final String TAG = CmhwService.class.getSimpleName();

    private static native long halOpen();

    private final Context mContext;
    private final CmhwInterface mCmHwImpl;

    private interface CmhwInterface {

        public boolean isTapToWakeSupported();
        public boolean getTapToWake();
        public void setTapToWake(boolean enable);
    }

    private class LegacyCmhw implements CmhwInterface {

        public boolean isTapToWakeSupported() {
            return TapToWake.isSupported();
        }
        
        public boolean getTapToWake() {
            return TapToWake.isEnabled();
        }

        public void setTapToWake(boolean enable) {
            TapToWake.setEnabled(enable);
        }
    }

    private CmhwInterface getImpl(Context context) {
        //TODO: Add new HAL stuff
        return new LegacyCmhw();
    }
    
    public CmhwService(Context context) {
        mContext = context;
        mCmHwImpl = getImpl(context);
    }

    //TODO: Error checking, etc
    @Override
    public boolean isTapToWakeSupported() {
        return mCmHwImpl.isTapToWakeSupported();
    }
    
    @Override
    public boolean getTapToWake() {
        return mCmHwImpl.getTapToWake();
    }

    @Override
    public void setTapToWake(boolean enable) {
        mCmHwImpl.setTapToWake(enable);
    }
}
