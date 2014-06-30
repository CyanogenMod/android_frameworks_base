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
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class QuietHoursTile extends QuickSettingsTile {

    private int mMode;
    private int mTaps = 0;

    private Handler mHandler = new Handler();

    public QuietHoursTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacks(checkDouble);
                if (mTaps > 0) {
                    // Set to timed mode and get update from controller to
                    // put us in the correct visual state
                    Settings.System.putIntForUser(mContext.getContentResolver(),
                            Settings.System.QUIET_HOURS_ENABLED,
                            1, UserHandle.USER_CURRENT);
                    mTaps = 0;
                } else {
                    mTaps += 1;
                    mHandler.postDelayed(checkDouble,
                            ViewConfiguration.getDoubleTapTimeout());
                }
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings",
                    "com.android.settings.Settings$QuietHoursSettingsActivity");
                startSettingsActivity(intent);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.QUIET_HOURS_ENABLED), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    final Runnable checkDouble = new Runnable () {
        public void run() {
            mTaps = 0;
            int newVal = 0;
            switch (mMode) {
                case 0: // Quiet hours disabled completely set to on
                    newVal = 2;
                    break;
                case 1: // Quiet hours timer enabled but not active - set to on
                    newVal = 2;
                    break;
                case 2: // Quiet hours timer disabled and forced active - set to off
                    newVal = 0;
                    break;
                case 3: // Quiet hours timer enabled and active - set to off
                    newVal = 0;
                    break;
                case 4: // Quiet hours timer enabled and waiting - set to on
                    newVal = 2;
                    break;
            }
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_ENABLED,
                    newVal, UserHandle.USER_CURRENT);
        }
    };

    private synchronized void updateTile() {
        mMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT);

        switch (mMode) {
            case 0: // Quiet hours disabled completely
                mDrawable = R.drawable.ic_qs_quiet_hours_off;
                mLabel = mContext.getString(R.string.quick_settings_quiethours_off);
                break;
            case 1: // Quiet hours timer enabled but not active
                mDrawable = R.drawable.ic_qs_quiet_hours_timed_off;
                mLabel = mContext.getString(R.string.quick_settings_quiethours_off);
                break;
            case 2: // Quiet hours timer disabled and forced active
                mDrawable = R.drawable.ic_qs_quiet_hours_on;
                mLabel = mContext.getString(R.string.quick_settings_quiethours);
                break;
            case 3: // Quiet hours timer enabled and active
                mDrawable = R.drawable.ic_qs_quiet_hours_timed_on;
                mLabel = mContext.getString(R.string.quick_settings_quiethours);
                break;
            case 4: // Quiet hours timer enabled and active - but waiting on requirements
                mDrawable = R.drawable.ic_qs_quiet_hours_waiting;
                mLabel = mContext.getString(R.string.quick_settings_quiethours_off);
                break;
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

}
