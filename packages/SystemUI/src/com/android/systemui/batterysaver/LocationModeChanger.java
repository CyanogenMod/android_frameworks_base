/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.content.pm.PackageManager;
import android.content.Context;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController;

public class LocationModeChanger extends ModeChanger {

    private int mLocationMode;
    private int mLocationModeByUser = 0;
    private LocationController mLocationController;

    public LocationModeChanger(Context context) {
        super(context);
    }

    public void setLocationModeByUser(int mode) {
        mLocationModeByUser = mode;
    }

    public int getLocationModeByUser() {
        return mLocationModeByUser;
    }

    public void setController(LocationController controller) {
        mLocationController = controller;
        mLocationMode = isSupported() ? controller.getLocationMode() : 0;
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    public void setLocationMode() {
        if (mLocationModeByUser != mLocationMode) {
            mLocationController.setLocationMode(mLocationModeByUser);
        }
    }

    @Override
    public boolean isDelayChanges() {
        if (!isSupported()) return false;
        return mLocationController.areActiveHighPowerLocationRequests();
    }

    @Override
    public boolean isStateEnabled() {
        if (!isSupported()) return false;
        return mLocationController.isLocationEnabled();
    }

    @Override
    public boolean isSupported() {
        boolean isSupport = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        return isModeEnabled() && isSupport;
    }

    @Override
    public int getMode() {
        return 0;
    }

    @Override
    public void stateNormal() {
        if (!isStateEnabled()) {
            mLocationController.setLocationEnabled(true);
        }
    }

    @Override
    public void stateSaving() {
        if (isStateEnabled()) {
            mLocationController.setLocationEnabled(false);
        }
    }

    @Override
    public boolean checkModes() {
        if (isDelayChanges()) {
            // high request location in progress detected, delay changing mode
            changeMode(true, false);
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " delayed location changing because high request ");
            }
            return false;
        }
        return true;
    }

    @Override
    public void setModes() {
        super.setModes();
    }
}
