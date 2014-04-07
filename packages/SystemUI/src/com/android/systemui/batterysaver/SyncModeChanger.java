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

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import com.android.systemui.R;

public class SyncModeChanger extends ModeChanger {

    public SyncModeChanger(Context context) {
        super(context);
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    @Override
    public boolean isStateEnabled() {
        return ContentResolver.getMasterSyncAutomatically();
    }

    @Override
    public boolean isSupported() {
        return isModeEnabled();
    }

    @Override
    public int getMode() {
        return 0;
    }

    @Override
    public void stateNormal() {
        if (!isStateEnabled()) {
            ContentResolver.setMasterSyncAutomatically(true);
        }
    }

    @Override
    public void stateSaving() {
        if (isStateEnabled()) {
            ContentResolver.setMasterSyncAutomatically(false);
        }
    }

    @Override
    public boolean checkModes() {
        if (isDelayChanges()) {
            // download/upload progress detected, delay changing mode
            changeMode(true, false);
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " delayed sync changing because traffic full ");
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
