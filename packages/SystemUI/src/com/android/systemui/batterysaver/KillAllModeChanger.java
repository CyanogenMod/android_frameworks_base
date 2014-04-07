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
import android.util.Log;
import com.android.internal.util.omni.TaskUtils;

import com.android.systemui.R;

public class KillAllModeChanger extends ModeChanger {

    public KillAllModeChanger(Context context) {
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
    }

    @Override
    public void stateSaving() {
        if (TaskUtils.killActiveTask(mContext)) {
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " kill all task");
            }
        }
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
