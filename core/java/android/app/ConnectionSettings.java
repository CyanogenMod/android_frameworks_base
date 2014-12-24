/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.app;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxHelper;
import android.nfc.NfcAdapter;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import com.android.internal.telephony.RILConstants;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** @hide */
public final class ConnectionSettings implements Parcelable {

    private int mConnectionId;
    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

    public static final int PROFILE_CONNECTION_MOBILEDATA = 0;
    public static final int PROFILE_CONNECTION_WIFI = 1;
    public static final int PROFILE_CONNECTION_WIFIAP = 2;
    public static final int PROFILE_CONNECTION_WIMAX = 3;
    public static final int PROFILE_CONNECTION_GPS = 4;
    public static final int PROFILE_CONNECTION_SYNC = 5;
    public static final int PROFILE_CONNECTION_BLUETOOTH = 7;
    public static final int PROFILE_CONNECTION_NFC = 8;
    public static final int PROFILE_CONNECTION_2G3G = 9;

    // retrieved from Phone.apk
    private static final String ACTION_MODIFY_NETWORK_MODE = "com.android.internal.telephony.MODIFY_NETWORK_MODE";
    private static final String EXTRA_NETWORK_MODE = "networkMode";

    private static final int CM_MODE_2G = 0;
    private static final int CM_MODE_3G = 1;
    private static final int CM_MODE_BOTH = 2;

    /** @hide */
    public static final Parcelable.Creator<ConnectionSettings> CREATOR = new Parcelable.Creator<ConnectionSettings>() {
        public ConnectionSettings createFromParcel(Parcel in) {
            return new ConnectionSettings(in);
        }

        @Override
        public ConnectionSettings[] newArray(int size) {
            return new ConnectionSettings[size];
        }
    };


    public ConnectionSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public ConnectionSettings(int connectionId) {
        this(connectionId, 0, false);
    }

    public ConnectionSettings(int connectionId, int value, boolean override) {
        mConnectionId = connectionId;
        mValue = value;
        mOverride = override;
        mDirty = false;
    }

    public int getConnectionId() {
        return mConnectionId;
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        mValue = value;
        mDirty = true;
    }

    public void setOverride(boolean override) {
        mOverride = override;
        mDirty = true;
    }

    public boolean isOverride() {
        return mOverride;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    public void processOverride(Context context) {
        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NfcAdapter nfcAdapter = null;
        try {
            nfcAdapter = NfcAdapter.getNfcAdapter(context);
        } catch (UnsupportedOperationException e) {
            //Nfc not available
        }

        boolean forcedState = getValue() == 1;
        boolean currentState;

        switch (getConnectionId()) {
            case PROFILE_CONNECTION_MOBILEDATA:
                currentState = cm.getMobileDataEnabled();
                if (forcedState != currentState) {
// hharte                    cm.setMobileDataEnabled(forcedState);
                }
                break;
            case PROFILE_CONNECTION_2G3G:
                Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
                switch(getValue()) {
                    case CM_MODE_2G:
                        intent.putExtra(EXTRA_NETWORK_MODE, RILConstants.NETWORK_MODE_GSM_ONLY);
                        break;
                    case CM_MODE_3G:
                        intent.putExtra(EXTRA_NETWORK_MODE, RILConstants.NETWORK_MODE_WCDMA_ONLY);
                        break;
                    case CM_MODE_BOTH:
                        intent.putExtra(EXTRA_NETWORK_MODE, RILConstants.NETWORK_MODE_WCDMA_PREF);
                        break;
                    default:
                        return;
                }
                context.sendBroadcast(intent);
                break;
            case PROFILE_CONNECTION_BLUETOOTH:
                int btstate = bta.getState();
                if (forcedState && (btstate == BluetoothAdapter.STATE_OFF
                        || btstate == BluetoothAdapter.STATE_TURNING_OFF)) {
                    bta.enable();
                } else if (!forcedState && (btstate == BluetoothAdapter.STATE_ON
                        || btstate == BluetoothAdapter.STATE_TURNING_ON)) {
                    bta.disable();
                }
                break;
            case PROFILE_CONNECTION_GPS:
                currentState = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (currentState != forcedState) {
                    Settings.Secure.setLocationProviderEnabled(context.getContentResolver(),
                            LocationManager.GPS_PROVIDER, forcedState);
                }
                break;
            case PROFILE_CONNECTION_SYNC:
                currentState = ContentResolver.getMasterSyncAutomatically();
                if (forcedState != currentState) {
                    ContentResolver.setMasterSyncAutomatically(forcedState);
                }
                break;
            case PROFILE_CONNECTION_WIFI:
                int wifiApState = wm.getWifiApState();
                currentState = wm.isWifiEnabled();
                if (currentState != forcedState) {
                    // Disable wifi tether
                    if (forcedState && (wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                            (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED)) {
                        wm.setWifiApEnabled(null, false);
                    }
                    wm.setWifiEnabled(forcedState);
                }
                break;
            case PROFILE_CONNECTION_WIFIAP:
                int wifiState = wm.getWifiState();
                currentState = wm.isWifiApEnabled();
                if (currentState != forcedState) {
                    // Disable wifi
                    if (forcedState && (wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                            (wifiState == WifiManager.WIFI_STATE_ENABLED)) {
                        wm.setWifiEnabled(false);
                    }
                    wm.setWifiApEnabled(null, forcedState);
                }
                break;
            case PROFILE_CONNECTION_WIMAX:
                if (WimaxHelper.isWimaxSupported(context)) {
                    currentState = WimaxHelper.isWimaxEnabled(context);
                    if (currentState != forcedState) {
                        WimaxHelper.setWimaxEnabled(context, forcedState);
                    }
                }
                break;
            case PROFILE_CONNECTION_NFC:
                if (nfcAdapter != null) {
                    int adapterState = nfcAdapter.getAdapterState();
                    currentState = (adapterState == NfcAdapter.STATE_ON ||
                            adapterState == NfcAdapter.STATE_TURNING_ON);
                    if (currentState != forcedState) {
                        if (forcedState) {
                            nfcAdapter.enable();
                        } else if (!forcedState && adapterState != NfcAdapter.STATE_TURNING_OFF) {
                            nfcAdapter.disable();
                        }
                    }
                }
                break;
        }
    }

    /** @hide */
    public static ConnectionSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        ConnectionSettings connectionDescriptor = new ConnectionSettings(0);
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("connectionDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("connectionId")) {
                    connectionDescriptor.mConnectionId = Integer.parseInt(xpp.nextText());
                } else if (name.equals("value")) {
                    connectionDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    connectionDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return connectionDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<connectionDescriptor>\n<connectionId>");
        builder.append(mConnectionId);
        builder.append("</connectionId>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</connectionDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mConnectionId);
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mConnectionId = in.readInt();
        mOverride = in.readInt() != 0;
        mValue = in.readInt();
        mDirty = in.readInt() != 0;
    }


}
