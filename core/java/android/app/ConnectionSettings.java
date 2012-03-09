package android.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxHelper;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** @hide */
public final class ConnectionSettings implements Parcelable {

    private int mConnectionId;
    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

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
        Boolean state;

        switch (getConnectionId()) {
            case PROFILE_CONNECTION_BLUETOOTH:
                state = bta.isEnabled();
                if (getValue() == 1) {
                    if (!state) {
                        bta.enable();
                    }
                } else {
                    if (state) {
                        bta.disable();
                    }
                }
                break;
            case PROFILE_CONNECTION_GPS:
                state = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (getValue() == 1) {
                    if (!state) {
                        Settings.Secure.setLocationProviderEnabled(context.getContentResolver(), LocationManager.GPS_PROVIDER, true);
                    }
                } else {
                    if (state) {
                        Settings.Secure.setLocationProviderEnabled(context.getContentResolver(), LocationManager.GPS_PROVIDER, false);
                    }
                }
                break;
            case PROFILE_CONNECTION_WIFI:
                int wifiApState = wm.getWifiApState();
                state = wm.isWifiEnabled();
                if (getValue() == 1) {
                    if ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) || (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED)) {
                        wm.setWifiApEnabled(null, false);
                    }
                    if (!state) {
                        wm.setWifiEnabled(true);
                    }
                } else {
                    if (state) {
                        wm.setWifiEnabled(false);
                    }
                }
                break;
            case PROFILE_CONNECTION_WIFIAP:
                int wifiState = wm.getWifiState();
                state = wm.isWifiApEnabled();
                if (getValue() == 1) {
                    if ((wifiState == WifiManager.WIFI_STATE_ENABLING) || (wifiState == WifiManager.WIFI_STATE_ENABLED)) {
                        wm.setWifiEnabled(false);
                    }
                    if (!state) {
                        wm.setWifiApEnabled(null, true);
                    }
                } else {
                    if (state) {
                        wm.setWifiApEnabled(null, false);
                    }
                }
                break;
            case PROFILE_CONNECTION_WIMAX:
                if (WimaxHelper.isWimaxSupported(context)) {
                    state = WimaxHelper.isWimaxEnabled(context);
                    if (getValue() == 1) {
                        if (!state) {
                            WimaxHelper.setWimaxEnabled(context, true);
                        }
                    } else {
                        if (state) {
                            WimaxHelper.setWimaxEnabled(context, false);
                        }
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
