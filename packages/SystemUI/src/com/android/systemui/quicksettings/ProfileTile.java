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

import android.app.AlertDialog;
import android.app.Profile;
import android.app.ProfileManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.RemoteException;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.UUID;

public class ProfileTile extends QuickSettingsTile {
    private Profile mChosenProfile;
    private ProfileManager mProfileManager;

    public ProfileTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createProfileDialog();
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

        qsc.registerAction(ProfileManager.INTENT_ACTION_PROFILE_SELECTED, this);
        qsc.registerAction(ProfileManager.INTENT_ACTION_PROFILE_UPDATED, this);
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
        mDrawable = R.drawable.ic_qs_profiles;
        mLabel = mProfileManager.getActiveProfile().getName();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        updateResources();
    }

    // copied from com.android.internal.policy.impl.GlobalActions
    private void createProfileDialog() {
        final Profile[] profiles = mProfileManager.getProfiles();
        UUID activeProfile = mProfileManager.getActiveProfile().getUuid();
        final CharSequence[] names = new CharSequence[profiles.length];

        int i = 0;
        int checkedItem = 0;

        for (Profile profile : profiles) {
            if (profile.getUuid().equals(activeProfile)) {
                checkedItem = i;
                mChosenProfile = profile;
            }
            names[i++] = profile.getName();
        }

        final AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
        ab.setSingleChoiceItems(names, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which < 0) {
                    return;
                }
                mChosenProfile = profiles[which];
                mProfileManager.setActiveProfile(mChosenProfile.getUuid());
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = ab.create();
        mStatusbarService.animateCollapsePanels();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

}
