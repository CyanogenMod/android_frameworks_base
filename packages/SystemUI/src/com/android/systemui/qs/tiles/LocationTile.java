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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

/** Quick settings tile: Location **/
public class LocationTile extends QSTile<QSTile.BooleanState> {

    private final Intent LOCATION_SETTINGS = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    private final AnimationIcon mEnable =
            new AnimationIcon(R.drawable.ic_signal_location_enable_animation);
    private final AnimationIcon mDisable =
            new AnimationIcon(R.drawable.ic_signal_location_disable_animation);
    private final Icon mBatterySaving = ResourceIcon.get(R.drawable.ic_qs_location_battery_saving);

    private final LocationController mController;
    private final LocationDetailAdapter mDetailAdapter;
    private final KeyguardMonitor mKeyguard;
    private final Callback mCallback = new Callback();

    public LocationTile(Host host) {
        super(host);
        mController = host.getLocationController();
        mDetailAdapter = new LocationDetailAdapter();
        mKeyguard = host.getKeyguardMonitor();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addSettingsChangedCallback(mCallback);
            mKeyguard.addCallback(mCallback);
        } else {
            mController.removeSettingsChangedCallback(mCallback);
            mKeyguard.removeCallback(mCallback);
        }
    }

    @Override
    protected void handleClick() {
        if(mController.isAdvancedSettingsEnabled()) {
            showDetail(true);
        } else {
            final boolean wasEnabled = (Boolean) mState.value;
            MetricsLogger.action(mContext, getMetricsCategory(), !wasEnabled);
            mController.setLocationEnabled(!wasEnabled);
        }
        mEnable.setAllowAnimation(true);
        mDisable.setAllowAnimation(true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int currentState = mController.getLocationCurrentState();
        final boolean locationEnabled = currentState != Settings.Secure.LOCATION_MODE_OFF;

        // Work around for bug 15916487: don't show location tile on top of lock screen. After the
        // bug is fixed, this should be reverted to only hiding it on secure lock screens:
        // state.visible = !(mKeyguard.isSecure() && mKeyguard.isShowing());
        state.visible = !mKeyguard.isShowing();
        state.value = locationEnabled;

        switch (currentState) {
            case Settings.Secure.LOCATION_MODE_OFF:
                state.label = mContext.getString(R.string.quick_settings_location_off_label);
                state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_location_off);
                state.icon = mDisable;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                state.label = mContext.getString(R.string.quick_settings_location_battery_saving_label);
                state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_location_battery_saving);
                state.icon = mBatterySaving;
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                state.label = mContext.getString(R.string.quick_settings_location_gps_only_label);
                state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_location_gps_only);
                state.icon = mEnable;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                state.label = mContext.getString(R.string.quick_settings_location_high_accuracy_label);
                state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_location_high_accuracy);
                state.icon = mEnable;
                break;
            default:
                state.label = mContext.getString(R.string.quick_settings_location_label);
                state.contentDescription = mContext.getString(R.string.accessibility_quick_settings_location_on);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_LOCATION;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_location_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_location_changed_off);
        }
    }

    private final class Callback implements LocationSettingsChangeCallback,
            KeyguardMonitor.Callback {
        @Override
        public void onLocationSettingsChanged(boolean enabled) {
            mDetailAdapter.setLocationEnabled(enabled);
            mDetailAdapter.setLocationMode(mController.getLocationCurrentState());
            refreshState();
        }

        @Override
        public void onKeyguardChanged() {
            refreshState();
        }
    };

    private class LocationDetailAdapter implements DetailAdapter {

        private LocationDetailView mLocationDetailView;

        @Override
        public int getTitle() {
            return R.string.quick_settings_location_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            return mController.getLocationCurrentState() != Settings.Secure.LOCATION_MODE_OFF;
        }

        @Override
        public Intent getSettingsIntent() {
            return LOCATION_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            mController.setLocationEnabled(state);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.QS_LOCATION;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final LocationDetailView v = (LocationDetailView) (LayoutInflater.from(mContext).inflate(R.layout.location, parent, false));
            mLocationDetailView = v;
            mLocationDetailView.setLocationController(mController);
            setLocationMode(mController.getLocationCurrentState());
            return v;
        }

        public void setLocationEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }

        public void setLocationMode(int mode) {
            if(mLocationDetailView != null)
                mLocationDetailView.setLocationMode(mode);
        }
    }
}
