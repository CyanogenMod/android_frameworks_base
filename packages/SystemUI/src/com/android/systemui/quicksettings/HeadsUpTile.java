/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class HeadsUpTile extends QuickSettingsTile {
    public HeadsUpTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.HEADS_UP_NOTIFICATION, !getEnabled() ? 1 : 0);
                updateTile();
            }
        };

        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.HEADS_UP_NOTIFICATION), this);
        updateResources();
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

    private synchronized void updateTile() {
        if (getEnabled()) {
            mDrawable = R.drawable.ic_qs_heads_up_on;
            mLabel = mContext.getString(R.string.quick_settings_heads_up_on);
        } else {
            mDrawable = R.drawable.ic_qs_heads_up_off;
            mLabel = mContext.getString(R.string.quick_settings_heads_up_off);
        }
    }

    private boolean getEnabled() {
        return Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 0, UserHandle.USER_CURRENT) == 1;
    }
}
