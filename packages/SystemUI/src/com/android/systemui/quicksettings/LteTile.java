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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class LteTile extends QuickSettingsTile {
    public LteTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLteState();
                updateResources();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DATA_ROAMING_SETTINGS);
                return true;
            }
        };

        qsc.registerObservedContent(
                Settings.Global.getUriFor(Settings.Global.PREFERRED_NETWORK_MODE), this);
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

    private void updateTile() {
        switch (getCurrentPreferredNetworkMode()) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
            case Phone.NT_MODE_TD_SCDMA_GSM_WCDMA_LTE:
            case Phone.NT_MODE_TD_SCDMA_WCDMA_LTE:
                mDrawable = R.drawable.ic_qs_lte_on;
                mLabel = mContext.getString(R.string.quick_settings_lte);
                break;
            default:
                mDrawable = R.drawable.ic_qs_lte_off;
                mLabel = mContext.getString(R.string.quick_settings_lte_off);
                break;
        }
    }

    private void toggleLteState() {
        TelephonyManager tm = (TelephonyManager)
            mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggleLTE(true);
    }

    private int getCurrentPreferredNetworkMode() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE, -1);
    }
}
