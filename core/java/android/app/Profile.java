/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

public class Profile implements Parcelable {

    private String name;

    private Map<String, ProfileGroup> profileGroups = new HashMap<String, ProfileGroup>();

    public static final Parcelable.Creator<Profile> CREATOR = new Parcelable.Creator<Profile>() {
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    public Profile(String name) {
        this.name = name;
    }

    private Profile(Parcel in) {
        readFromParcel(in);
    }

    public void ensureProfleGroup(String groupName) {
        if (!profileGroups.containsKey(groupName)) {
            profileGroups.put(groupName, new ProfileGroup(groupName));
        }
    }

    public void removeProfileGroup(String name) {
        profileGroups.remove(name);
    }

    public ProfileGroup[] getProfileGroups() {
        return profileGroups.values().toArray(new ProfileGroup[profileGroups.size()]);
    }

    public ProfileGroup getProfileGroup(String name) {
        return profileGroups.get(name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeParcelableArray(
                profileGroups.values().toArray(new Parcelable[profileGroups.size()]), flags);
    }

    public void readFromParcel(Parcel in) {
        name = in.readString();
        for (Parcelable group : in.readParcelableArray(null)) {
            ProfileGroup grp = (ProfileGroup) group;
            profileGroups.put(grp.getName(), grp);
        }
    }

    public String getName() {
        return name;
    }

    public Notification processNotification(String groupName, Notification notification) {
        ProfileGroup profileGroupSettings = profileGroups.get(groupName);
        notification = profileGroupSettings.processNotification(notification);
        return notification;
    }

    public String getXmlString() {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    public void getXmlString(StringBuilder builder) {
        builder.append("<profile name=\"" + getName() + "\">\n");
        for (ProfileGroup pGroup : profileGroups.values()) {
            pGroup.getXmlString(builder);
        }
        builder.append("</profile>\n");
    }

    public static Profile fromXml(XmlPullParser xpp) throws XmlPullParserException, IOException {
        Profile profile = new Profile(xpp.getAttributeValue(null, "name"));
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("profileGroup")) {
                    ProfileGroup pg = ProfileGroup.fromXml(xpp);
                    profile.profileGroups.put(pg.getName(), pg);
                }
            }
            event = xpp.next();
        }
        return profile;
    }

}
