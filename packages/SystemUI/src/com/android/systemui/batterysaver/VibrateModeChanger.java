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

import android.content.Context;
import android.provider.Settings;

import com.android.systemui.R;

public class VibrateModeChanger extends ModeChanger {

    public VibrateModeChanger(Context context) {
        super(context);
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    @Override
    public boolean isStateEnabled() {
        return isModeEnabled();
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
        Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.BATTERY_SAVER_VIBRATE_DISABLE, 0);
    }

    @Override
    public void stateSaving() {
        Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.BATTERY_SAVER_VIBRATE_DISABLE, 1);
    }

    @Override
    public boolean checkModes() {
        return isModeEnabled();
    }

    @Override
    public void setModes() {
        super.setModes();
    }
}
