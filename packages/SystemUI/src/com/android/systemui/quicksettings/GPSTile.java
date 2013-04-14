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
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationGpsStateChangeCallback {

    private boolean enabled = false;
    private boolean working = false;

    private String mDescription = null;

    ContentResolver mContentResolver;

    public GPSTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContentResolver = mContext.getContentResolver();
        LocationController controller = new LocationController(mContext);
        controller.addStateChangedCallback(this);

        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.setLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER, !enabled);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
        qsc.registerAction(LocationManager.PROVIDERS_CHANGED_ACTION, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);
        updateResources();
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if (enabled && working) {
            mDrawable = R.drawable.ic_qs_location;
        } else if (enabled) {
            mDrawable = R.drawable.ic_qs_gps_on;
        } else {
            mDrawable = R.drawable.ic_qs_gps_off;
        }
        setGenericLabel();
    }

    @Override
    public void onLocationGpsStateChanged(boolean inUse, String description) {
        working = inUse;
        mDescription = description;
        updateResources();
    }

    private void setGenericLabel() {
        if (mDescription != null) {
            mLabel = mDescription;
        } else {
            mLabel = (enabled ? mContext.getString(R.string.quick_settings_gps) : mContext.getString(R.string.quick_settings_gps_off));
        }
    }
}
