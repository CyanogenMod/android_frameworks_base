/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class SleepScreenTile extends QuickSettingsTile {
    private PowerManager mPm;

    public SleepScreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPm.goToSleep(SystemClock.uptimeMillis());
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity("android.settings.DISPLAY_SETTINGS");
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private void updateTile() {
        mDrawable = R.drawable.ic_qs_sleep;
        mLabel = mContext.getString(R.string.quick_settings_screen_sleep);
    }
}
