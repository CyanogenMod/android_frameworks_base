package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {

    ContentResolver mContentResolver;
    private LocationController mLocationController;
    private int mCurrentMode;

    public GPSTile(Context context, QuickSettingsController qsc, LocationController lc) {
        super(context, qsc);

        mContentResolver = mContext.getContentResolver();
        mLocationController = lc;
        mLocationController.addSettingsChangedCallback(this);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                changeLocationMode();
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
    }

    private void changeLocationMode(){
        int newMode;

        switch(mCurrentMode){
        case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
            newMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
            break;
        case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
            newMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
            break;
        case Settings.Secure.LOCATION_MODE_OFF:
            newMode = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
            break;
        case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
            newMode = Settings.Secure.LOCATION_MODE_OFF;
            break;
        default:
            newMode = Settings.Secure.LOCATION_MODE_OFF;
            break;
        }

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE,
                newMode);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLocationController.removeSettingsChangedCallback(this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        int textResId;
        switch(mCurrentMode) {
        case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
            textResId = R.string.location_mode_sensors_only_title;
            mDrawable = R.drawable.ic_qs_location_on;
            break;
        case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
            textResId = R.string.location_mode_battery_saving_title;
            mDrawable = R.drawable.ic_qs_location_lowpower;
            break;
        case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
            textResId = R.string.location_mode_high_accuracy_title;
            mDrawable = R.drawable.ic_qs_location_on;
            break;
        default:
            textResId = R.string.quick_settings_location_off_label;
            mDrawable = R.drawable.ic_qs_location_off;
            break;
        }
        mLabel = mContext.getText(textResId).toString();
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        mCurrentMode = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        updateResources();
    }
}
