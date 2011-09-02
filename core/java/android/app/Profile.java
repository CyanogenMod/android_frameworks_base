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
import android.media.AudioManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Profile implements Parcelable {

    private String mName;
    private int mNameResId;

    private UUID mUuid;

    private Map<UUID, ProfileGroup> profileGroups = new HashMap<UUID, ProfileGroup>();

    private ProfileGroup mDefaultGroup;

    private boolean mStatusBarIndicator = false;
    private boolean mDirty;

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
        this(name, -1, UUID.randomUUID());
    }

    private Profile(String name, int nameResId, UUID uuid) {
        mName = name;
        mNameResId = nameResId;
        mUuid = uuid;
        mDirty = false;
    }

    private Profile(Parcel in) {
        readFromParcel(in);
    }

    /** @hide */
    public void addProfileGroup(ProfileGroup value) {
        profileGroups.put(value.getUuid(), value);
        if (value.isDefaultGroup()) {
            mDefaultGroup = value;
        }
    }

    /** @hide */
    public void removeProfileGroup(UUID uuid) {
        if (!profileGroups.get(uuid).isDefaultGroup()) {
            profileGroups.remove(uuid);
        } else {
            Log.e(TAG, "Cannot remove default group: " + uuid);
        }
    }

    public ProfileGroup[] getProfileGroups() {
        return profileGroups.values().toArray(new ProfileGroup[profileGroups.size()]);
    }

    public ProfileGroup getProfileGroup(UUID uuid) {
        return profileGroups.get(uuid);
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
        dest.writeInt(mNameResId);
        new ParcelUuid(mUuid).writeToParcel(dest, 0);
        dest.writeInt(mStatusBarIndicator ? 1 : 0);
        dest.writeInt(mDirty ? 1 : 0);
        dest.writeParcelableArray(
                profileGroups.values().toArray(new Parcelable[profileGroups.size()]), flags);
        dest.writeParcelableArray(
                streams.values().toArray(new Parcelable[streams.size()]), flags);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mNameResId = in.readInt();
        mUuid = ParcelUuid.CREATOR.createFromParcel(in).getUuid();
        mStatusBarIndicator = (in.readInt() == 1);
        mDirty = (in.readInt() == 1);
        for (Parcelable group : in.readParcelableArray(null)) {
            ProfileGroup grp = (ProfileGroup) group;
            profileGroups.put(grp.getUuid(), grp);
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
        mName = name;
        mNameResId = -1;
        mDirty = true;
    }

    public UUID getUuid() {
        if (this.mUuid == null) this.mUuid = UUID.randomUUID();
        return this.mUuid;
    }

    public boolean getStatusBarIndicator() {
        return mStatusBarIndicator;
    }

    public void setStatusBarIndicator(boolean newStatusBarIndicator) {
        mStatusBarIndicator = newStatusBarIndicator;
        mDirty = true;
    }

    /** @hide */
    public boolean isDirty() {
        if (mDirty) {
            return true;
        }
        for (ProfileGroup group : profileGroups.values()) {
            if (group.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<profile ");
        if (mNameResId > 0) {
            builder.append("nameres=\"");
            builder.append(context.getResources().getResourceEntryName(mNameResId));
        } else {
            builder.append("name=\"");
            builder.append(TextUtils.htmlEncode(getName()));
        }
        builder.append("\" uuid=\"");
        builder.append(TextUtils.htmlEncode(getUuid().toString()));
        builder.append("\">\n");

        builder.append("<statusbar>");
        builder.append(getStatusBarIndicator() ? "yes" : "no");
        builder.append("</statusbar>\n");

        for (ProfileGroup pGroup : profileGroups.values()) {
            pGroup.getXmlString(builder, context);
        }
        for (StreamSettings sd : streams.values()) {
            sd.getXmlString(builder, context);
        }
        builder.append("</profile>\n");
        mDirty = false;
    }

    /** @hide */
    public static Profile fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        String value = xpp.getAttributeValue(null, "nameres");
        int profileNameResId = -1;
        String profileName = null;

        if (value != null) {
            profileNameResId = context.getResources().getIdentifier(value, "string", "android");
            if (profileNameResId > 0) {
                profileName = context.getResources().getString(profileNameResId);
            }
        }

        if (profileName == null) {
            profileName = xpp.getAttributeValue(null, "name");
        }

        UUID profileUuid = UUID.randomUUID();
        try {
            profileUuid = UUID.fromString(xpp.getAttributeValue(null, "uuid"));
        } catch (NullPointerException e) {
            Log.w(TAG,
                    "Null Pointer - UUID not found for "
                    + profileName
                    + ".  New UUID generated: "
                    + profileUuid.toString()
                    );
        } catch (IllegalArgumentException e) {
            Log.w(TAG,
                    "UUID not recognized for "
                    + profileName
                    + ".  New UUID generated: "
                    + profileUuid.toString()
                    );
        }

        Profile profile = new Profile(profileName, profileNameResId, profileUuid);
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("statusbar")) {
                    profile.setStatusBarIndicator(xpp.nextText() == "yes");
                }
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
