/*
 * Copyright (C) 2012 Sven Dawitz for the CyanogenMod Project
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

import android.app.ProfileManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import com.android.server.ProfileManagerService;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ProfileTile extends QuickSettingsTile {
    private ProfileManager mProfileManager;

    public ProfileTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        qsc.registerAction(ProfileManagerService.INTENT_ACTION_PROFILE_SELECTED, this);

        mDrawable = R.drawable.ic_qs_profiles;

        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);
        mLabel = mProfileManager.getActiveProfile().getName();

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStatusbarService.animateCollapsePanels();
                Intent intent=new Intent(Intent.ACTION_POWERMENU_PROFILE);
                mContext.sendBroadcast(intent);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent("android.settings.PROFILES_SETTINGS");
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mLabel = mProfileManager.getActiveProfile().getName();
        updateQuickSettings();
    }
}
