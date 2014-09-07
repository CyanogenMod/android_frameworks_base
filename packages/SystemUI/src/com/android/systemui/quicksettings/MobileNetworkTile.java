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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;

import com.android.internal.util.cm.QSUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.NetworkController;

public class MobileNetworkTile extends NetworkTile {
    private static final int NO_OVERLAY = 0;
    private static final int DISABLED_OVERLAY = -1;

    private boolean mEnabled;
    private String mDescription;
    private int mDataTypeIconId = NO_OVERLAY;
    private String mDataContentDescription;
    private String mSignalContentDescription;
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
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
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
        mDataContentDescription = mEnabled && mDataTypeIconId > 0 && !mWifiOn
                ? mDataContentDescription
                : r.getString(R.string.accessibility_no_data);
        mLabel = mEnabled
                ? mDescription
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
        if (!QSUtils.deviceSupportsMobileData(mContext)) {
            return;
        }

        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mDrawable = enabled && mobileSignalIconId > 0
                ? mobileSignalIconId
                : R.drawable.ic_qs_signal_no_signal;
        mSignalContentDescription = enabled && mobileSignalIconId > 0
                ? mSignalContentDescription
                : r.getString(R.string.accessibility_no_signal);

        // Determine the overlay image
        if (enabled && dataTypeIconId > 0 && !mWifiOn) {
            mDataTypeIconId = dataTypeIconId;
        } else if (!mCm.getMobileDataEnabled()) {
            mDataTypeIconId = DISABLED_OVERLAY;
        } else {
            mDataTypeIconId = NO_OVERLAY;
        }

        mEnabled = enabled;
        mDescription = removeTrailingPeriod(description);

        setActivity(activityIn, activityOut);
        updateResources();
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
    }

    @Override
    protected View getImageView() {
        return mTile.findViewById(R.id.rssi_image);
    }

    @Override
    void updateQuickSettings() {
        super.updateQuickSettings();

        updateOverlayImage(mDataTypeIconId);
        mTile.setContentDescription(mContext.getResources().getString(
                R.string.accessibility_quick_settings_mobile,
                mSignalContentDescription, mDataContentDescription,
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
    private static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final String aux = string.trim();
        if (aux.endsWith(".")) {
            return aux.substring(0, aux.length() - 1);
        }
        return aux;
    }

    // MobileNetworkTile use an internal frame, so we need to restrict frame margins
    // instead of image margin
    @Override
    public void setImageMargins(int margin) {
        View image = mTile.findViewById(R.id.image);
        if (image != null) {
            MarginLayoutParams params = (MarginLayoutParams) image.getLayoutParams();
            params.topMargin = params.bottomMargin = margin;
            image.setLayoutParams(params);
        }
    }
}
