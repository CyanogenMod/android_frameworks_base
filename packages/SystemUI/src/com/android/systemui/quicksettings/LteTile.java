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
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.List;

public class LteTile extends QuickSettingsTile {

    private Context mContext;

    public LteTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContext = context;

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLteState();
                updateResources();
                if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.phone", "com.android.phone.Settings");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.Global.getUriFor(
                Settings.Global.PREFERRED_NETWORK_MODE), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        TelephonyManager tm = (TelephonyManager)
            mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getLteState() == 1) {
            mDrawable = R.drawable.ic_qs_lte_on;
            mLabel = mContext.getString(R.string.quick_settings_lte);
        } else {
            mDrawable = R.drawable.ic_qs_lte_off;
            mLabel = mContext.getString(R.string.quick_settings_lte_off);
        }
    }

    private void toggleLteState() {
        TelephonyManager tm = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggleLTE();
    }

}
