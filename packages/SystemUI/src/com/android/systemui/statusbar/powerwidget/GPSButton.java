package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class GPSButton extends PowerButton {

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.Secure.getUriFor(Settings.Secure.LOCATION_PROVIDERS_ALLOWED));
    }

    public GPSButton() { mType = BUTTON_GPS; }

    @Override
    protected void updateState() {
        if(getGpsState(mView.getContext())) {
            mIcon = R.drawable.stat_gps_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_gps_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        ContentResolver resolver = context.getContentResolver();
        boolean enabled = getGpsState(context);
        Settings.Secure.setLocationProviderEnabled(resolver,
                LocationManager.GPS_PROVIDER, !enabled);
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.LOCATION_SOURCE_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private static boolean getGpsState(Context context) {
        ContentResolver resolver = context.getContentResolver();
        return Settings.Secure.isLocationProviderEnabled(resolver,
                LocationManager.GPS_PROVIDER);
    }
}
