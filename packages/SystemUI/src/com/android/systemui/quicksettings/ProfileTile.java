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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.android.server.ProfileManagerService;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.UUID;

public class ProfileTile extends QuickSettingsTile {
    private Profile mChosenProfile;
    private ProfileManager mProfileManager;
    private ProfileReceiver mProfileReceiver;

    public ProfileTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);
        mProfileReceiver = new ProfileReceiver();
        mProfileReceiver.registerSelf();
        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);
        updateTileState();
        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createProfileDialog();
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS);
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    private void updateTileState() {
        mDrawable = R.drawable.ic_qs_flashlight_on;
        mLabel = mProfileManager.getActiveProfile().getName();
    }

    private class ProfileReceiver extends BroadcastReceiver {
        private boolean mIsRegistered;

        public ProfileReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTileState();
        }

        private void registerSelf() {
            if (!mIsRegistered) {
                mIsRegistered = true;

                IntentFilter filter = new IntentFilter();
                filter.addAction(ProfileManagerService.INTENT_ACTION_PROFILE_SELECTED);
                mContext.registerReceiver(mProfileReceiver, filter);
            }
        }

        private void unregisterSelf() {
            if (mIsRegistered) {
                mIsRegistered = false;
                mContext.unregisterReceiver(this);
            }
        }
    }

    // copied from com.android.internal.policy.impl.GlobalActions
    private void createProfileDialog() {
        final ProfileManager profileManager = (ProfileManager) mContext
                .getSystemService(Context.PROFILE_SERVICE);

        final Profile[] profiles = profileManager.getProfiles();
        UUID activeProfile = profileManager.getActiveProfile().getUuid();
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

        AlertDialog dialog = ab
                .setTitle(R.string.quick_settings_profile_label)
                .setSingleChoiceItems(names, checkedItem, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0)
                            return;
                        mChosenProfile = profiles[which];
                    }
                })
                .setPositiveButton(com.android.internal.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                profileManager.setActiveProfile(mChosenProfile.getUuid());
                            }
                        })
                .setNegativeButton(com.android.internal.R.string.no,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        }).create();
        mStatusbarService.animateCollapsePanels();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        dialog.show();
    }

}
