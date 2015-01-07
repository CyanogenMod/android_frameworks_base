/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.provider.Settings;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController;

/**
 * Layout for the location detail in quick settings.
 */
public class LocationDetailView extends LinearLayout {

    private RadioGroup mRadioGroup;
    private RadioButton mHighAccuracy;
    private RadioButton mBatterySaving;
    private RadioButton mSensorsOnly;

    //private Context mContext;
    private LocationController mController;

    public LocationDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRadioGroup = (RadioGroup) findViewById(R.id.radiogroup_location);
        mHighAccuracy = (RadioButton) findViewById(R.id.radio_high_accuracy);
        mBatterySaving = (RadioButton) findViewById(R.id.radio_battery_saving);
        mSensorsOnly = (RadioButton) findViewById(R.id.radio_sensors_only);
        
        mRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                onRadioButtonClicked(checkedId);
            }
        });
    }

    public void setLocationController(LocationController controller) {
        mController = controller;
    }

    public void onRadioButtonClicked(int checkedId) {
        switch(checkedId) {
            case R.id.radio_high_accuracy:
                mController.setLocationMode(Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                break;
            case R.id.radio_battery_saving:
                mController.setLocationMode(Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
                break;
            case R.id.radio_sensors_only:
                mController.setLocationMode(Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
                break;
            default: break;
        }
    }

    public void setLocationMode(int mode) {
        boolean enabled = mode != Settings.Secure.LOCATION_MODE_OFF;

        switch(mode) {
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                mRadioGroup.check(mHighAccuracy.getId());
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                mRadioGroup.check(mBatterySaving.getId());
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                mRadioGroup.check(mSensorsOnly.getId());
                break;
            default: break;
        }

        mHighAccuracy.setEnabled(enabled);
        mBatterySaving.setEnabled(enabled);
        mSensorsOnly.setEnabled(enabled);
    }
}
