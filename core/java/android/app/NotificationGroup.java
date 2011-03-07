/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NotificationGroup implements Parcelable {

    private String mName;

    private Set<String> mPackages = new HashSet<String>();

    public static final Parcelable.Creator<NotificationGroup> CREATOR = new Parcelable.Creator<NotificationGroup>() {
        public NotificationGroup createFromParcel(Parcel in) {
            return new NotificationGroup(in);
        }

        @Override
        public NotificationGroup[] newArray(int size) {
            return new NotificationGroup[size];
        }
    };

    public NotificationGroup(String name) {
        this.mName = name;
    }

    private NotificationGroup(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return mName;
    }

    public void addPackage(String pkg) {
        mPackages.add(pkg);
    }

    public String[] getPackages() {
        return mPackages.toArray(new String[mPackages.size()]);
    }

    public void removePackage(String pkg) {
        mPackages.remove(pkg);
    }

    public boolean hasPackage(String pkg) {
        boolean result = mPackages.contains(pkg);
        Log.i("PROFILE", "Group: " + mName + " containing : " + pkg + " : " + result);
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeStringArray(getPackages());
    }

    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mPackages.addAll(Arrays.asList(in.readStringArray()));
    }

    public String getXmlString() {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    public void getXmlString(StringBuilder builder) {
        builder.append("<notificationGroup name=\"" + TextUtils.htmlEncode(getName()) + "\">\n");
        for (String pkg : mPackages) {
            builder.append("<package>" + TextUtils.htmlEncode(pkg) + "</package>\n");
        }
        builder.append("</notificationGroup>\n");
    }

    public static NotificationGroup fromXml(XmlPullParser xpp) throws XmlPullParserException,
            IOException {
        return fromXml(xpp, null);
    }
    public static NotificationGroup fromXml(XmlPullParser xpp, Context context) throws XmlPullParserException,
            IOException {
        String attr = Profile.getAttrResString(xpp, context);
        NotificationGroup notificationGroup = new NotificationGroup(attr);
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("notificationGroup")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("package")) {
                    String pkg = xpp.nextText();
                    notificationGroup.addPackage(pkg);
                }
            }
            event = xpp.next();
        }
        return notificationGroup;
    }
}
