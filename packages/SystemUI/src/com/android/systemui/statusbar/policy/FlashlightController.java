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
import android.provider.Settings;
import android.widget.CompoundButton;

import com.android.systemui.R;

public class FlashlightController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.FlashlightController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mFlashLight;

    public FlashlightController(Context context, CompoundButton checkbox) {
        mContext = context;
        mFlashLight = getFlashLight();
        mCheckBox = checkbox;
        checkbox.setChecked(mFlashLight);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("bright", !checked);
        mContext.sendBroadcast(i);
    }

    private boolean getFlashLight() {
        ContentResolver cr = mContext.getContentResolver();
        return Settings.System.getInt(cr, Settings.System.TORCH_STATE, 0) == 1;
    }
}
