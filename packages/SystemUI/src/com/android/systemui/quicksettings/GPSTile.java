package com.android.systemui.quicksettings;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {

    ContentResolver mContentResolver;
    private LocationController mLocationController;
    private boolean mLocationEnabled;
    private boolean mGmsInstalled;

    public GPSTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContentResolver = mContext.getContentResolver();
        try {
            mGmsInstalled =
                    mContext.getPackageManager().getPackageInfo("com.google.android.gms", 0)
                            != null;
        } catch (PackageManager.NameNotFoundException e) {
            mGmsInstalled = false;
        }
        mLocationController = new LocationController(mContext);
        mLocationController.addSettingsChangedCallback(this);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean newLocationEnabledState = !mLocationEnabled;
                if (mLocationController.setLocationEnabled(newLocationEnabledState)
                        && newLocationEnabledState && mGmsInstalled) {
                    // If we've successfully switched from location off to on, close the
                    // notifications tray to show the network location provider consent dialog.
                    Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                    mContext.sendBroadcast(closeDialog);

                    try {
                        ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    } catch (RemoteException e) {
                        // Do nothing
                    }
                }
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
        int textResId = mLocationEnabled ? R.string.quick_settings_location_label
                : R.string.quick_settings_location_off_label;
        mLabel = mContext.getText(textResId).toString();
        mDrawable = mLocationEnabled
                ? R.drawable.ic_qs_location_on : R.drawable.ic_qs_location_off;
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        mLocationEnabled = locationEnabled;
        updateResources();
    }
}
