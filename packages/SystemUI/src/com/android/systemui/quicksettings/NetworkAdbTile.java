package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.NetworkUtils;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.net.InetAddress;

public class NetworkAdbTile extends QuickSettingsTile {
    private static final String TAG = "NetworkAdbTile";

    public NetworkAdbTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.ADB_PORT, !getEnabled() ? 5555 : -1);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.Secure.getUriFor(Settings.Secure.ADB_PORT), this);
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private synchronized void updateTile() {
        if (getEnabled()) {
            WifiManager wifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            if (wifiInfo != null) {
                // if wifiInfo is not null, set the label to "hostAddress"
                InetAddress address = NetworkUtils.intToInetAddress(wifiInfo.getIpAddress());
                mLabel = address.getHostAddress();
            } else {
                //if wifiInfo is null, set the enabled label without host address
                mLabel = mContext.getString(R.string.quick_settings_network_adb_enabled_label);
            }
            mDrawable = R.drawable.ic_qs_network_adb_on;
        } else {
            // Otherwise set the disabled label and icon
            mLabel = mContext.getString(R.string.quick_settings_network_adb_disabled_label);
            mDrawable = R.drawable.ic_qs_network_adb_off;
        }
    }

    private boolean getEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ADB_PORT, 0) > 0;
    }
}
