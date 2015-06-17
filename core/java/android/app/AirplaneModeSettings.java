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

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.Parcel;
import android.os.Parcelable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


import java.io.IOException;

/** @hide */
public final class AirplaneModeSettings implements Parcelable {

    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<AirplaneModeSettings> CREATOR = new Parcelable.Creator<AirplaneModeSettings>() {
        public AirplaneModeSettings createFromParcel(Parcel in) {
            return new AirplaneModeSettings(in);
        }

        @Override
        public AirplaneModeSettings[] newArray(int size) {
            return new AirplaneModeSettings[size];
        }
    };


    public AirplaneModeSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public AirplaneModeSettings() {
        this(0, false);
    }

    public AirplaneModeSettings(int value, boolean override) {
        mValue = value;
        mOverride = override;
        mDirty = false;
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
        if (isOverride()) {
            int current = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
            if (current != mValue) {
                Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, mValue);
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", mValue == 1);
                context.sendBroadcast(intent);
            }
        }
    }

    /** @hide */
    public static AirplaneModeSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        AirplaneModeSettings airplaneModeDescriptor = new AirplaneModeSettings();
        while ((event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) ||
                !xpp.getName().equals("airplaneModeDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("value")) {
                    airplaneModeDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    airplaneModeDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                throw new IOException("Premature end of file while parsing airplane mode settings");
            }
            event = xpp.next();
        }
        return airplaneModeDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<airplaneModeDescriptor>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</airplaneModeDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mOverride = in.readInt() != 0;
        mValue = in.readInt();
        mDirty = in.readInt() != 0;
    }


}
