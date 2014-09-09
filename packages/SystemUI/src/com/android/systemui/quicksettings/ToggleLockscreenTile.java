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
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ToggleLockscreenTile extends QuickSettingsTile implements
        LockscreenStateChanger.LockStateChangeListener {

    private LockscreenStateChanger mLockscreenChanger;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mLockscreenChanger = LockscreenStateChanger.getInstance(context);
        mLockscreenChanger.addListener(this);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLockscreenChanger.toggleState();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity("android.settings.SECURITY_SETTINGS");
                return true;
            }
        };
        updateResources();
    }

    @Override
    public void onDestroy() {
        mLockscreenChanger.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        mLabel = mContext.getString(R.string.quick_settings_lockscreen);
        mDrawable = mLockscreenChanger.isDisabled() ?
                R.drawable.ic_qs_lock_screen_off : R.drawable.ic_qs_lock_screen_on;
        super.updateResources();
    }

    @Override
    public void onLockStateChange(boolean enabled) {
        updateResources();
    }
}
