
package android.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.media.AudioManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

/** @hide */
public final class VibratorSettings implements Parcelable{

    public static final int OFF = 0;
    public static final int SILENT = 1;
    public static final int ON = 2;

    private int mVibratorId;
    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<VibratorSettings> CREATOR = new Parcelable.Creator<VibratorSettings>() {
        public VibratorSettings createFromParcel(Parcel in) {
            return new VibratorSettings(in);
        }

        @Override
        public VibratorSettings[] newArray(int size) {
            return new VibratorSettings[size];
        }
    };


    public VibratorSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public VibratorSettings(int vibratorId) {
        this(vibratorId, 0, false);
    }

    public VibratorSettings(int vibratorId, int value, boolean override) {
        mVibratorId = vibratorId;
        mValue = value;
        mOverride = override;
        mDirty = false;
    }

    public int getVibratorId() {
        return mVibratorId;
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

    /** @hide */
    public static VibratorSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        VibratorSettings vibratorDescriptor = new VibratorSettings(0);
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("vibratorDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("vibratorId")) {
                    vibratorDescriptor.mVibratorId = Integer.parseInt(xpp.nextText());
                } else if (name.equals("value")) {
                    vibratorDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    vibratorDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return vibratorDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<vibratorDescriptor>\n<vibratorId>");
        builder.append(mVibratorId);
        builder.append("</vibratorId>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</vibratorDescriptor>\n");
        mDirty = false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVibratorId);
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mVibratorId = in.readInt();
        mOverride = in.readInt() != 0;
        mValue = in.readInt();
        mDirty = in.readInt() != 0;
    }

    /** @hide */
    public void processOverride(Context context) {
        AudioManager amgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        switch (mValue) {
        case OFF:
            amgr.setVibrateSetting(mVibratorId, AudioManager.VIBRATE_SETTING_OFF);
            break;
        case SILENT:
            amgr.setVibrateSetting(mVibratorId, AudioManager.VIBRATE_SETTING_ONLY_SILENT);
        default:
            amgr.setVibrateSetting(mVibratorId, AudioManager.VIBRATE_SETTING_ON);
            break;
        }
    }
}
