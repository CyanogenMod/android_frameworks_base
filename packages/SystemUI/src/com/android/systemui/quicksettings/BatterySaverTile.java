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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class BatterySaverTile extends QuickSettingsTile {

    private boolean mEnabled;

    public BatterySaverTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.BATTERY_SAVER_OPTION,
                        mEnabled ? 0 : 1);
                Intent scheduleSaver = new Intent();
    			scheduleSaver.setAction(Intent.ACTION_BATTERY_SERVICES);
                mContext.sendBroadcast(scheduleSaver);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.Global.getUriFor(
                Settings.Global.BATTERY_SAVER_OPTION), this);
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
        mEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BATTERY_SAVER_OPTION, 0) == 1;
        if (mEnabled) {
            mDrawable = R.drawable.ic_qs_battery_saver_on;
            mLabel = mContext.getString(R.string.quick_settings_battery_saver_label);
        } else {
            mDrawable = R.drawable.ic_qs_battery_saver_off;
            mLabel = mContext.getString(R.string.quick_settings_battery_saver_off_label);
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

}
