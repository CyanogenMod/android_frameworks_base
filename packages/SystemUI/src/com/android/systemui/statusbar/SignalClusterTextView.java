/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;

public class SignalClusterTextView extends LinearLayout implements
        NetworkController.NetworkSignalChangedCallback,
        NetworkController.SignalStrengthChangedCallback {

    private boolean mAirplaneMode;
    private int mDBm = 0;
    private int mSignalClusterStyle = SignalClusterView.STYLE_NORMAL;

    private ViewGroup mMobileGroup;
    private TextView mMobileSignalText;

    public SignalClusterTextView(Context context) {
        this(context, null);
    }

    public SignalClusterTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mMobileGroup = (ViewGroup) findViewById(R.id.mobile_signal_text_combo);
        mMobileSignalText = (TextView) findViewById(R.id.mobile_signal_text);
    }

    public void setStyle(int style) {
        mSignalClusterStyle = style;
        updateSignalText();
    }

    private String getSignalLevelString(int dBm) {
        if (dBm == 0) {
            return "-\u221e"; // -oo ('minus infinity')
        }
        return Integer.toString(dBm);
    }

    private void updateSignalText() {
        if (mMobileGroup == null) {
            return;
        }
        if (mAirplaneMode || mDBm == 0) {
            mMobileGroup.setVisibility(View.GONE);
        } else if (mSignalClusterStyle == SignalClusterView.STYLE_TEXT) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobileSignalText.setText(getSignalLevelString(mDBm));
        } else {
            mMobileGroup.setVisibility(View.GONE);
        }
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            boolean activityIn, boolean activityOut,
            String wifiSignalContentDescriptionId, String description) {
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            String mobileSignalContentDescriptionId, int dataTypeIconId,
            boolean activityIn, boolean activityOut,
            String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        mAirplaneMode = enabled;
        updateSignalText();
    }

    @Override
    public void onPhoneSignalStrengthChanged(int dbm) {
        mDBm = dbm;
        updateSignalText();
    }
}
