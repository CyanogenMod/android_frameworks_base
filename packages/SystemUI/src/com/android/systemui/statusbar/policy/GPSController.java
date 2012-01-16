/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.provider.Settings;
import android.widget.CompoundButton;

public class GPSController extends BroadcastReceiver
        implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.GPSController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mGPS;

    public GPSController(Context context, CompoundButton checkbox) {
        mContext = context;
        mGPS = getGPS();
        mCheckBox = checkbox;
        checkbox.setChecked(mGPS);
        checkbox.setOnCheckedChangeListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.GPS_ENABLED_CHANGE_ACTION);
        context.registerReceiver(this, filter);
    }

    public void release() {
        mContext.unregisterReceiver(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        if (checked != mGPS) {
            mGPS = checked;
            Context context = view.getContext();
            ContentResolver cr = context.getContentResolver();
            Settings.Secure.setLocationProviderEnabled(cr, LocationManager.GPS_PROVIDER, mGPS);
        }
    }

    public void onReceive(Context context, Intent intent) {
        if (LocationManager.GPS_ENABLED_CHANGE_ACTION.equals(intent.getAction())) {
            final boolean enabled = getGPS();
            if (enabled != mGPS) {
                mGPS = enabled;
                mCheckBox.setChecked(enabled);
            }
        }
    }

    private boolean getGPS() {
        ContentResolver cr = mContext.getContentResolver();
        return Settings.Secure.isLocationProviderEnabled(cr, LocationManager.GPS_PROVIDER);
    }
}
