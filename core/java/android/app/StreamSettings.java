
package android.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

/** @hide */
public class StreamSettings implements Parcelable{

    int streamId;

    int value;

    boolean override;
    
    /** @hide */
    public static final Parcelable.Creator<StreamSettings> CREATOR = new Parcelable.Creator<StreamSettings>() {
        public StreamSettings createFromParcel(Parcel in) {
            return new StreamSettings(in);
        }

        @Override
        public StreamSettings[] newArray(int size) {
            return new StreamSettings[size];
        }
    };


    public StreamSettings(Parcel parcel){
        readFromParcel(parcel);
    }

    public StreamSettings(int streamId) {
        this.streamId = streamId;
        this.value = 0;
        this.override = false;
    }

    public StreamSettings(int streamId, int value, boolean override) {
        this.streamId = streamId;
        this.value = value;
        this.override = override;
    }

    public int getStreamId() {
        return streamId;
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
    public static StreamSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        StreamSettings streamDescriptor = new StreamSettings(0);
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("streamDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("streamId")) {
                    streamDescriptor.streamId = Integer.parseInt(xpp.nextText());
                } else if (name.equals("value")) {
                    streamDescriptor.value = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    streamDescriptor.override = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return streamDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder) {
        builder.append("<streamDescriptor>\n");
        builder.append("<streamId>" + streamId + "</streamId>\n");
        builder.append("<value>" + value + "</value>\n");
        builder.append("<override>" + override + "</override>\n");
        builder.append("</streamDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(streamId);
        dest.writeValue(override);
        dest.writeInt(value);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        streamId = in.readInt();
        override = (Boolean)in.readValue(null);
        value = in.readInt();
    }


}
