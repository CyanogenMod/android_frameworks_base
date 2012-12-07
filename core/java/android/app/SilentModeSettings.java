package android.app;

import android.content.Context;
import android.media.AudioManager;
import android.os.Parcel;
import android.os.Parcelable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** @hide */
public final class SilentModeSettings implements Parcelable {
    private static final String SILENT_MODE_OFF = "off";
    private static final String SILENT_MODE_VIBRATE = "vibrate";
    private static final String SILENT_MODE_MUTE = "mute";

    private String mValue;
    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<SilentModeSettings> CREATOR = new Parcelable.Creator<SilentModeSettings>() {
        public SilentModeSettings createFromParcel(Parcel in) {
            return new SilentModeSettings(in);
        }

        @Override
        public SilentModeSettings[] newArray(int size) {
            return new SilentModeSettings[size];
        }
    };


    public SilentModeSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public SilentModeSettings() {
        this(SILENT_MODE_OFF, false);
    }

    public SilentModeSettings(String value, boolean override) {
        mValue = value;
        mOverride = override;
        mDirty = false;
    }

    public String getValue() {
        return mValue;
    }

    public void setValue(String value) {
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
        if (isOverride()) {
            int ringerMode = AudioManager.RINGER_MODE_NORMAL;
            if (mValue.equals(SILENT_MODE_MUTE)) {
                ringerMode = AudioManager.RINGER_MODE_SILENT;
            } else if (mValue.equals(SILENT_MODE_VIBRATE)) {
                ringerMode = AudioManager.RINGER_MODE_VIBRATE;
            }
            AudioManager amgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            amgr.setRingerMode(ringerMode);
        }
    }

    /** @hide */
    public static SilentModeSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        SilentModeSettings connectionDescriptor = new SilentModeSettings();
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("value")) {
                    connectionDescriptor.mValue = xpp.nextText();
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
        builder.append("<silentModeDescriptor>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</silentModeDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeString(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mOverride = in.readInt() != 0;
        mValue = in.readString();
        mDirty = in.readInt() != 0;
    }


}
