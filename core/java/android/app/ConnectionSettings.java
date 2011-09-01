package android.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** @hide */
public class ConnectionSettings implements Parcelable {

    int connectionId;
    int value;
    boolean override;

    public static final int PROFILE_CONNECTION_WIFI = 1;
    public static final int PROFILE_CONNECTION_WIFIAP = 2;
    public static final int PROFILE_CONNECTION_WIMAX = 3;
    public static final int PROFILE_CONNECTION_GPS = 4;
    public static final int PROFILE_CONNECTION_BLUETOOTH = 7;

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
        this.connectionId = connectionId;
        this.value = value;
        this.override = override;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    public boolean isOverride() {
        return override;
    }

    public void processOverride(Context context) {
        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        switch (getConnectionId()) {
            case PROFILE_CONNECTION_BLUETOOTH:
                Boolean state_BT = bta.isEnabled();
                if (getValue() == 1) {
                    if (!state_BT) {
                        bta.enable();
                    }
                } else {
                    if (state_BT){
                        bta.disable();
                    }
                }
                break;
            case PROFILE_CONNECTION_GPS:
                Boolean state_GPS = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (getValue() == 1) {
                    if (!state_GPS) {
                        Settings.Secure.setLocationProviderEnabled(context.getContentResolver(), LocationManager.GPS_PROVIDER, true);
                    }
                } else {
                    if (state_GPS) {
                        Settings.Secure.setLocationProviderEnabled(context.getContentResolver(), LocationManager.GPS_PROVIDER, false);
                    }
                }
                break;
            case PROFILE_CONNECTION_WIFI:
                Boolean state_WIFI = wm.isWifiEnabled();
                int wifiApState = wm.getWifiApState();
                if (getValue() == 1) {
                    if ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) || (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED)) {
                        wm.setWifiApEnabled(null, false);
                    }
                    if (!state_WIFI) {
                        wm.setWifiEnabled(true);
                    }
                } else {
                    if (state_WIFI) {
                        wm.setWifiEnabled(false);
                    }
                }
                break;
            case PROFILE_CONNECTION_WIFIAP:
                Boolean state_WIFI_AP = wm.isWifiApEnabled();
                int wifiState = wm.getWifiState();
                if (getValue() == 1) {
                    if ((wifiState == WifiManager.WIFI_STATE_ENABLING) || (wifiState == WifiManager.WIFI_STATE_ENABLED)) {
                        wm.setWifiEnabled(false);
                    }
                    if (!state_WIFI_AP) {
                        wm.setWifiApEnabled(null, true);
                    }
                } else {
                    if (!state_WIFI_AP) {
                        wm.setWifiApEnabled(null, false);
                    }
                }
                break;
            default: break;
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
                    connectionDescriptor.connectionId = Integer.parseInt(xpp.nextText());
                } else if (name.equals("value")) {
                    connectionDescriptor.value = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    connectionDescriptor.override = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return connectionDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder) {
        builder.append("<connectionDescriptor>\n<connectionId>");
        builder.append(connectionId);
        builder.append("</connectionId>\n<value>");
        builder.append(value);
        builder.append("</value>\n<override>");
        builder.append(override);
        builder.append("</override>\n</connectionDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(connectionId);
        dest.writeInt(override ? 1 : 0);
        dest.writeInt(value);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        connectionId = in.readInt();
        override = in.readInt() != 0;
        value = in.readInt();
    }


}
