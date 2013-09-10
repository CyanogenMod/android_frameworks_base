
package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.NetworkUtils;
import android.net.Uri;
import android.net.wifi.IWifiManager;
import android.net.wifi.WifiInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class WiFiADBTile extends QuickSettingsTile {
    private static final String TAG = "WiFiAdbTile";

    public WiFiADBTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.ADB_PORT,
                        !getEnabled() ? 5555 : -1);
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
            mDrawable = R.drawable.ic_qs_wifi_adb_on;
            WifiInfo wifiInfo = null;
            IWifiManager wifiManager = IWifiManager.Stub.asInterface(ServiceManager
                    .getService(Context.WIFI_SERVICE));
            try {
                wifiInfo = wifiManager.getConnectionInfo();
            } catch (RemoteException e) {
                Log.e(TAG, "wifiManager, getConnectionInfo()", e);
            }
            if (wifiInfo != null) {
                // if wifiInfo is not null set the label to "hostAddress:port"
                String hostAddress = NetworkUtils.intToInetAddress(wifiInfo.getIpAddress())
                        .getHostAddress();
                mLabel = hostAddress + ":5555";
            } else {
                //if wifiInfo is null, set the enabled label without host and port
                mLabel = mContext.getString(R.string.quick_settings_wifiadb_enabled_label);
            }
        } else {
            // Otherwise set the disabled label and icon
            mLabel = mContext.getString(R.string.quick_settings_wifiadb_disabled_label);
            mDrawable = R.drawable.ic_qs_wifi_adb_off;
        }
    }

    private boolean getEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ADB_PORT, 0) > 0;
    }

}
