/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.vanir.util.Helpers;

public class ExpandedDesktopTile extends QuickSettingsTile {

    private boolean mEnabled = false;
    private int mExpandDesktopMode = 2;

    public ExpandedDesktopTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Change the system setting
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.EXPANDED_DESKTOP_STATE,
                    !mEnabled ? 1 : 0);
                Helpers.restartSystemUI();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int expanded = getExpandDesktopMode();
                if (expanded == 1) { 
                    Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STYLE, mExpandDesktopMode);
                } else {
                    mExpandDesktopMode = mExpandDesktopMode == 2 ? 1 : 2;
                    Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STYLE, mExpandDesktopMode);
                }
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STYLE), this);
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

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private int getExpandDesktopMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXPANDED_DESKTOP_STYLE, 2);
     }

    private synchronized void updateTile() {
        mEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE, 0,
                UserHandle.USER_CURRENT) == 1;
        if (mEnabled) {
            mDrawable = R.drawable.ic_lock_immersive_mode_on;
            mLabel = mContext.getString(R.string.quick_settings_expanded_desktop);
        } else {
            mDrawable = R.drawable.ic_lock_immersive_mode_off;
            mLabel = mContext.getString(R.string.quick_settings_expanded_desktop_off);
        }
    }

}
