/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 CyanogenMod Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.internal.util.slim.ButtonsConstants;
import com.android.internal.util.slim.SlimActions;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class SleepScreenTile extends QuickSettingsTile {

    private PowerManager pm;

    public SleepScreenTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);
        pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                qsc.mBar.collapseAllPanels(true);
                pm.goToSleep(SystemClock.uptimeMillis());
            }
        };
        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                SlimActions.processAction(mContext, ButtonsConstants.ACTION_POWER_MENU, true);
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

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_sleep;
        mLabel = mContext.getString(R.string.quick_settings_screen_sleep);
    }

}
