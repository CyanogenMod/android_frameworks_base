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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import static com.android.internal.util.slim.DeviceUtils.deviceSupportsMobileData;

public class MobileNetworkTile extends NetworkTile {
    private static final int NO_OVERLAY = 0;
    private static final int DISABLED_OVERLAY = -1;

    private NetworkController mController;
    private boolean mEnabled;
    private String mDescription;
    private int mDataTypeIconId = NO_OVERLAY;
    private String dataContentDescription;
    private String signalContentDescription;
    private boolean mWifiOn = false;

    private ConnectivityManager mCm;

    public MobileNetworkTile(Context context, QuickSettingsController qsc,
                NetworkController controller) {
        super(context, qsc, controller, R.layout.quick_settings_tile_rssi);

        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCm.getMobileDataEnabled()) {
                    // None, onMobileDataSignalChanged will set final overlay image
                    updateOverlayImage(NO_OVERLAY);
                    mCm.setMobileDataEnabled(true);
                } else {
                    updateOverlayImage(DISABLED_OVERLAY);
                    mCm.setMobileDataEnabled(false);
                }
                if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$DataUsageSummaryActivity"));
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    @Override
    protected void updateTile() {
        Resources r = mContext.getResources();
        dataContentDescription = mEnabled && (mDataTypeIconId > 0) && !mWifiOn
                ? dataContentDescription
                : r.getString(R.string.accessibility_no_data);
        mLabel = mEnabled
                ? removeTrailingPeriod(mDescription)
                : r.getString(R.string.quick_settings_rssi_emergency_only);
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            boolean activityIn, boolean activityOut,
            String wifiSignalContentDescriptionId, String description) {
        mWifiOn = enabled;
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled,
            int mobileSignalIconId, String mobileSignalContentDescriptionId,
            int dataTypeIconId, boolean activityIn, boolean activityOut,
            String dataTypeContentDescriptionId, String description) {
        if (deviceSupportsMobileData(mContext)) {
            // TODO: If view is in awaiting state, disable
            Resources r = mContext.getResources();
            mDrawable = enabled && (mobileSignalIconId > 0)
                    ? mobileSignalIconId
                    : R.drawable.ic_qs_signal_no_signal;
            signalContentDescription = enabled && (mobileSignalIconId > 0)
                    ? signalContentDescription
                    : r.getString(R.string.accessibility_no_signal);

            // Determine the overlay image
            if (enabled && (dataTypeIconId > 0) && !mWifiOn) {
                mDataTypeIconId = dataTypeIconId;
            } else if (!mCm.getMobileDataEnabled()) {
                mDataTypeIconId = DISABLED_OVERLAY;
            } else {
                mDataTypeIconId = NO_OVERLAY;
            }

            mEnabled = enabled;
            mDescription = description;

            setActivity(activityIn, activityOut);
            updateResources();
        }
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        ImageView iv = (ImageView) mTile.findViewById(R.id.rssi_image);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
            if (mTileTextColor != -2) {
                tv.setTextColor(mTileTextColor);
            }
        }
        iv.setImageResource(mDrawable);
        updateOverlayImage(mDataTypeIconId);
        mTile.setContentDescription(mContext.getResources().getString(
                R.string.accessibility_quick_settings_mobile,
                signalContentDescription, dataContentDescription,
                mLabel));
    }

    void updateOverlayImage(int dataTypeIconId) {
        ImageView iov = (ImageView) mTile.findViewById(R.id.rssi_overlay_image);
        if (dataTypeIconId > 0) {
            iov.setImageResource(dataTypeIconId);
        } else if (dataTypeIconId == DISABLED_OVERLAY) {
            iov.setImageResource(R.drawable.ic_qs_signal_data_off);
        } else {
            iov.setImageDrawable(null);
        }
    }

    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            string.substring(0, length - 1);
        }
        return string;
    }

}
