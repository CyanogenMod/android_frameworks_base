/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.systemui.statusbar.powerwidget;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProfileManager;
import android.content.Context;
import android.content.Intent;

import com.android.systemui.R;

/**
 * Creates Power Widget button.
 * Toggles through profiles; long-click opens profile chooser fragment.
 * @author marius@volkhart.com
 */
public class ProfileButton extends PowerButton {

    public ProfileButton() { mType = BUTTON_PROFILE; }

    @Override
    protected void updateState() {

        //icon comes from settings app: ic_settings_profiles
        mIcon = R.drawable.stat_profile;
        mState = STATE_DISABLED;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void toggleState() {
        final ProfileManager mProfileManager = (ProfileManager) mView.getContext()
                .getSystemService(Context.PROFILE_SERVICE);
        final String[] mProfileNames = mProfileManager.getProfileNames();
        String selectedProfileName = mProfileManager.getActiveProfile().getName();

        //default; no profile selected
        int selectedProfileIndex = -1;
        for (int i = 0; i < mProfileNames.length; i++) {
            if (mProfileNames[i].equals(selectedProfileName)) {
                selectedProfileIndex = i;
                break;
            }
        }
        if (selectedProfileIndex == mProfileNames.length-1) {
            mProfileManager.setActiveProfile(mProfileNames[0]);
        } else {
            mProfileManager.setActiveProfile(mProfileNames[selectedProfileIndex+1]);
        }
        selectedProfileName = mProfileManager.getActiveProfile().getName();

        //display feedback for selected profile
        Notification.Builder mNotificationBuilder = new Notification.Builder(mView.getContext());
        mNotificationBuilder.setSmallIcon(mIcon);
        mNotificationBuilder.setTicker(selectedProfileName);
        mNotificationBuilder.setContentTitle(selectedProfileName);
        mNotificationBuilder.setAutoCancel(true);
        mNotificationBuilder.setOngoing(false);
        final NotificationManager mNotificationManager = (NotificationManager) mView.getContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(TAG.hashCode() + selectedProfileName.hashCode(), mNotificationBuilder.getNotification());
        mNotificationManager.cancel(TAG.hashCode() + selectedProfileName.hashCode());
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.PROFILE_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

}
