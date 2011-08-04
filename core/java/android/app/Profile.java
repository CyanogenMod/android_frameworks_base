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
import android.content.res.XmlResourceParser;
import android.media.AudioManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Profile implements Parcelable {

    private String mName;

    private Map<String, ProfileGroup> profileGroups = new HashMap<String, ProfileGroup>();

    private ProfileGroup mDefaultGroup;

    private static final String TAG = "Profile";

    private Map<Integer, StreamSettings> streams = new HashMap<Integer, StreamSettings>();

    /** @hide */
    public static final Parcelable.Creator<Profile> CREATOR = new Parcelable.Creator<Profile>() {
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    /** @hide */
    public Profile(String name) {
        this.mName = name;
    }

    private Profile(Parcel in) {
        readFromParcel(in);
    }

    /** @hide */
    public void ensureProfileGroup(String groupName) {
        ensureProfileGroup(groupName, false);
    }

    /** @hide */
    public void ensureProfileGroup(String groupName, boolean defaultGroup) {
        if (!profileGroups.containsKey(groupName)) {
            ProfileGroup value = new ProfileGroup(groupName, defaultGroup);
            addProfileGroup(value);
        }
    }

    /** @hide */
    private void addProfileGroup(ProfileGroup value) {
        profileGroups.put(value.getName(), value);
        if (value.isDefaultGroup()) {
            mDefaultGroup = value;
        }
    }

    /** @hide */
    public void removeProfileGroup(String name) {
        if (!profileGroups.get(name).isDefaultGroup()) {
            profileGroups.remove(name);
        } else {
            Log.e(TAG, "Cannot remove default group: " + name);
        }
    }

    public ProfileGroup[] getProfileGroups() {
        return profileGroups.values().toArray(new ProfileGroup[profileGroups.size()]);
    }

    public ProfileGroup getProfileGroup(String name) {
        return profileGroups.get(name);
    }

    public ProfileGroup getDefaultGroup() {
        return mDefaultGroup;
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeParcelableArray(
                profileGroups.values().toArray(new Parcelable[profileGroups.size()]), flags);
        dest.writeParcelableArray(
                streams.values().toArray(new Parcelable[streams.size()]), flags);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mName = in.readString();
        for (Parcelable group : in.readParcelableArray(null)) {
            ProfileGroup grp = (ProfileGroup) group;
            profileGroups.put(grp.getName(), grp);
            if (grp.isDefaultGroup()) {
                mDefaultGroup = grp;
            }
        }
        for (Parcelable parcel : in.readParcelableArray(null)) {
            StreamSettings stream = (StreamSettings) parcel;
            streams.put(stream.streamId, stream);
        }
    }

    public String getName() {
        return mName;
    }

    /** @hide */
    public void setName(String name) {
        this.mName = name;
    }

    /** @hide */
    public Notification processNotification(String groupName, Notification notification) {
        ProfileGroup profileGroupSettings = groupName == null ? mDefaultGroup : profileGroups
                .get(groupName);
        notification = profileGroupSettings.processNotification(notification);
        return notification;
    }

    /** @hide */
    public String getXmlString() {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    /** @hide */
    public void getXmlString(StringBuilder builder) {
        builder.append("<profile name=\"" + TextUtils.htmlEncode(getName()) + "\">\n");
        for (ProfileGroup pGroup : profileGroups.values()) {
            pGroup.getXmlString(builder);
        }
        for (StreamSettings sd : streams.values()) {
            sd.getXmlString(builder);
        }
        builder.append("</profile>\n");
    }

    /** @hide */
    public static String getAttrResString(XmlPullParser xpp, Context context) {
        String attr = null;
        if (xpp instanceof XmlResourceParser && context != null) {
            XmlResourceParser xrp = (XmlResourceParser) xpp;
            int resId = xrp.getAttributeResourceValue(null, "name", 0);
            if (resId != 0) {
                attr = context.getResources().getString(resId);
            } else {
                attr = xrp.getAttributeValue(null, "name");
            }
        } else {
            attr = xpp.getAttributeValue(null, "name");
        }
        return attr;
    }

    /** @hide */
    public static Profile fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        String attr = getAttrResString(xpp, context);
        Profile profile = new Profile(attr);
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("profileGroup")) {
                    ProfileGroup pg = ProfileGroup.fromXml(xpp, context);
                    profile.addProfileGroup(pg);
                }
                if (name.equals("streamDescriptor")) {
                    StreamSettings sd = StreamSettings.fromXml(xpp, context);
                    profile.streams.put(sd.streamId, sd);
                }
            }
            event = xpp.next();
        }
        return profile;
    }

    /** @hide */
    public void doSelect(Context context) {
        // Set stream volumes
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        for (StreamSettings sd : streams.values()) {
            if (sd.override) {
                am.setStreamVolume(sd.streamId, sd.value, 0);
            }
        }
    }

    /** @hide */
    public StreamSettings getSettingsForStream(int streamId){
        return streams.get(streamId);
    }

    /** @hide */
    public void setStreamSettings(StreamSettings descriptor){
        streams.put(descriptor.streamId, descriptor);
    }

    /** @hide */
    public Collection<StreamSettings> getStreamSettings(){
        return streams.values();
    }


}
