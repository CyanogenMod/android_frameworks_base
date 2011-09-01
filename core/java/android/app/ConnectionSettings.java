
package android.app;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class ConnectionSettings implements Parcelable{

    int connectionId;

    int value;

    boolean override;

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


    public ConnectionSettings(Parcel parcel){
        readFromParcel(parcel);
    }

    public ConnectionSettings(int connectionId) {
        this.connectionId = connectionId;
        this.value = 0;
        this.override = false;
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
        builder.append("<connectionDescriptor>\n");
        builder.append("<connectionId>" + connectionId + "</connectionId>\n");
        builder.append("<value>" + value + "</value>\n");
        builder.append("<override>" + override + "</override>\n");
        builder.append("</connectionDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(connectionId);
        dest.writeValue(override);
        dest.writeInt(value);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        connectionId = in.readInt();
        override = (Boolean)in.readValue(null);
        value = in.readInt();
    }


}
