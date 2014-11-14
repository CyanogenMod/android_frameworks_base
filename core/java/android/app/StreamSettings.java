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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

/** @hide */
public final class StreamSettings implements Parcelable{

    private int mStreamId;
    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

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


    public StreamSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public StreamSettings(int streamId) {
        this(streamId, 0, false);
    }

    public StreamSettings(int streamId, int value, boolean override) {
        mStreamId = streamId;
        mValue = value;
        mOverride = override;
        mDirty = false;
    }

    public int getStreamId() {
        return mStreamId;
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
    public static StreamSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        StreamSettings streamDescriptor = new StreamSettings(0);
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("streamDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("streamId")) {
                    streamDescriptor.mStreamId = Integer.parseInt(xpp.nextText());
                } else if (name.equals("value")) {
                    streamDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    streamDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return streamDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<streamDescriptor>\n<streamId>");
        builder.append(mStreamId);
        builder.append("</streamId>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</streamDescriptor>\n");
        mDirty = false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStreamId);
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mStreamId = in.readInt();
        mOverride = in.readInt() != 0;
        mValue = in.readInt();
        mDirty = in.readInt() != 0;
    }
}
