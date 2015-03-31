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
public final class BrightnessSettings implements Parcelable {

    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<BrightnessSettings> CREATOR
            = new Parcelable.Creator<BrightnessSettings>() {
        public BrightnessSettings createFromParcel(Parcel in) {
            return new BrightnessSettings(in);
        }

        @Override
        public BrightnessSettings[] newArray(int size) {
            return new BrightnessSettings[size];
        }
    };


    public BrightnessSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public BrightnessSettings() {
        this(0, false);
    }

    public BrightnessSettings(int value, boolean override) {
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
            final boolean automatic = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            if (automatic) {
                final float current = Settings.System.getFloat(context.getContentResolver(),
                        Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, -2f);
                // Convert from [0, 255] to [-1, 1] for SCREEN_AUTO_BRIGHTNESS_ADJ
                final float adj = mValue / (255 / 2f) - 1;
                if (current != adj) {
                    Settings.System.putFloat(context.getContentResolver(),
                            Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, adj);
                }
            } else {
                final int current = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, -1);
                if (current != mValue) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, mValue);
                }
            }
        }
    }

    /** @hide */
    public static BrightnessSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        BrightnessSettings brightnessDescriptor = new BrightnessSettings();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("brightnessDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("value")) {
                    brightnessDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    brightnessDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return brightnessDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<brightnessDescriptor>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</brightnessDescriptor>\n");
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
