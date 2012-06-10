/*
 * Copyright (C) 2011 The CyanogenMod Project
 * This code has been modified. Portions copyright (C) 2012 ParanoidAndroid Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import com.android.internal.telephony.Phone;

import com.android.systemui.R;

public class NetworkModeController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.NetworkModeController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mNetworkMode;

    public NetworkModeController(Context context, CompoundButton checkbox) {
        mContext = context;
        mNetworkMode = getNetworkMode();
        mCheckBox = checkbox;
        checkbox.setChecked(mNetworkMode);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        int networkType = checked ? Phone.NT_MODE_WCDMA_PREF : Phone.NT_MODE_GSM_ONLY;
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.PREFERRED_NETWORK_MODE, networkType);
    }

    private boolean getNetworkMode() {
        int state = 99;
        try {
            state = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (Exception e) {}
        return networkModeToState(state);
    }

    private static boolean networkModeToState(int state) {
        switch(state) {
            case Phone.NT_MODE_WCDMA_PREF:
                return true;
            case Phone.NT_MODE_GSM_ONLY:
                return false;
        }
        return false;
    }
}

