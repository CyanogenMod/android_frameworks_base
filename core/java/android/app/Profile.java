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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @hide
 */
public final class Profile implements Parcelable, Comparable {

    private String mName;

    private int mNameResId;

    private UUID mUuid;

    private ArrayList<UUID> mSecondaryUuids = new ArrayList<UUID>();

    private Map<UUID, ProfileGroup> profileGroups = new HashMap<UUID, ProfileGroup>();

    private ProfileGroup mDefaultGroup;

    private boolean mStatusBarIndicator = false;

    private boolean mDirty;

    private static final String TAG = "Profile";

    private int mProfileType;

    private static final int CONDITIONAL_TYPE = 1;

    private static final int TOGGLE_TYPE = 0;

    private Map<Integer, StreamSettings> streams = new HashMap<Integer, StreamSettings>();

    private Map<String, Integer> mWifiTriggers = new HashMap<String, Integer>();

    private Map<Integer, ConnectionSettings> connections = new HashMap<Integer, ConnectionSettings>();

    private RingModeSettings mRingMode = new RingModeSettings();

    private AirplaneModeSettings mAirplaneMode = new AirplaneModeSettings();

    private int mScreenLockMode = LockMode.DEFAULT;

    /** @hide */
    public static class LockMode {
        public static final int DEFAULT = 0;
        public static final int INSECURE = 1;
        public static final int DISABLE = 2;
    }

    /** @hide */
    public static class TriggerState {
        public static final int ON_CONNECT = 0;
        public static final int ON_DISCONNECT = 1;
        public static final int DISABLED = 2;
    }

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
        mProfileType = TOGGLE_TYPE;  //Default to toggle type
        mDirty = false;
    }

    private Profile(Parcel in) {
        readFromParcel(in);
    }

    public int getWifiTrigger(String ssid) {
        if (ssid != null && mWifiTriggers.containsKey(ssid)) {
            return mWifiTriggers.get(ssid);
        }
        return TriggerState.DISABLED;
    }

    public void setWifiTrigger(String ssid, int value) {
        if (ssid == null || value < TriggerState.ON_CONNECT || value > TriggerState.DISABLED) {
            return;
        }
        if (value == TriggerState.DISABLED && mWifiTriggers.containsKey(ssid)) {
            mWifiTriggers.remove(ssid);
        } else {
            mWifiTriggers.put(ssid, value);
        }
        mDirty = true;
    }

    public int compareTo(Object obj) {
        Profile tmp = (Profile) obj;
        if (mName.compareTo(tmp.mName) < 0) {
            return -1;
        } else if (mName.compareTo(tmp.mName) > 0) {
            return 1;
        }
        return 0;
    }

    /** @hide */
    public void addProfileGroup(ProfileGroup value) {
        if (value.isDefaultGroup()) {
            /* we must not have more than one default group */
            if (mDefaultGroup != null) {
                return;
            }
            mDefaultGroup = value;
        }
        profileGroups.put(value.getUuid(), value);
        mDirty = true;
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
        ArrayList<ParcelUuid> uuids = new ArrayList<ParcelUuid>(mSecondaryUuids.size());
        for (UUID u : mSecondaryUuids) {
            uuids.add(new ParcelUuid(u));
        }
        dest.writeParcelableArray(uuids.toArray(new Parcelable[uuids.size()]), flags);
        dest.writeInt(mStatusBarIndicator ? 1 : 0);
        dest.writeInt(mProfileType);
        dest.writeInt(mDirty ? 1 : 0);
        dest.writeParcelableArray(
                profileGroups.values().toArray(new Parcelable[profileGroups.size()]), flags);
        dest.writeParcelableArray(
                streams.values().toArray(new Parcelable[streams.size()]), flags);
        dest.writeParcelableArray(
                connections.values().toArray(new Parcelable[connections.size()]), flags);
        dest.writeParcelable(mRingMode, flags);
        dest.writeParcelable(mAirplaneMode, flags);
        dest.writeInt(mScreenLockMode);
        dest.writeMap(mWifiTriggers);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mNameResId = in.readInt();
        mUuid = ParcelUuid.CREATOR.createFromParcel(in).getUuid();
        for (Parcelable parcel : in.readParcelableArray(null)) {
            ParcelUuid u = (ParcelUuid) parcel;
            mSecondaryUuids.add(u.getUuid());
        }
        mStatusBarIndicator = (in.readInt() == 1);
        mProfileType = in.readInt();
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
            streams.put(stream.getStreamId(), stream);
        }
        for (Parcelable parcel : in.readParcelableArray(null)) {
            ConnectionSettings connection = (ConnectionSettings) parcel;
            connections.put(connection.getConnectionId(), connection);
        }
        mRingMode = (RingModeSettings) in.readParcelable(null);
        mAirplaneMode = (AirplaneModeSettings) in.readParcelable(null);
        mScreenLockMode = in.readInt();
        in.readMap(mWifiTriggers, null);
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

    public int getProfileType() {
        return mProfileType;
    }

    /** @hide */
    public void setProfileType(int type) {
        mProfileType = type;
        mDirty = true;
    }

    public UUID getUuid() {
        if (this.mUuid == null) this.mUuid = UUID.randomUUID();
        return this.mUuid;
    }

    public UUID[] getSecondaryUuids() {
        return mSecondaryUuids.toArray(new UUID[mSecondaryUuids.size()]);
    }

    public void setSecondaryUuids(List<UUID> uuids) {
        mSecondaryUuids.clear();
        if (uuids != null) {
            mSecondaryUuids.addAll(uuids);
            mDirty = true;
        }
    }

    public void addSecondaryUuid(UUID uuid) {
        if (uuid != null) {
            mSecondaryUuids.add(uuid);
            mDirty = true;
        }
    }

    public boolean getStatusBarIndicator() {
        return mStatusBarIndicator;
    }

    public void setStatusBarIndicator(boolean newStatusBarIndicator) {
        mStatusBarIndicator = newStatusBarIndicator;
        mDirty = true;
    }

    public boolean isConditionalType() {
        return(mProfileType == CONDITIONAL_TYPE ? true : false);
    }

    public void setConditionalType() {
        mProfileType = CONDITIONAL_TYPE;
        mDirty = true;
    }

    public RingModeSettings getRingMode() {
        return mRingMode;
    }

    public void setRingMode(RingModeSettings descriptor) {
        mRingMode = descriptor;
        mDirty = true;
    }

    public int getScreenLockModeWithDPM(Context context) {
        // Check device policy
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (dpm.requireSecureKeyguard()) {
            // Always enforce lock screen
            return LockMode.DEFAULT;
        }

        return mScreenLockMode;
    }

    public int getScreenLockMode() {
        return mScreenLockMode;
    }

    public void setScreenLockMode(int screenLockMode) {
        if (screenLockMode < LockMode.DEFAULT || screenLockMode > LockMode.DISABLE) {
            mScreenLockMode = LockMode.DEFAULT;
        } else {
            mScreenLockMode = screenLockMode;
        }
        mDirty = true;
    }

    public AirplaneModeSettings getAirplaneMode() {
        return mAirplaneMode;
    }

    public void setAirplaneMode(AirplaneModeSettings descriptor) {
        mAirplaneMode = descriptor;
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
        for (StreamSettings stream : streams.values()) {
            if (stream.isDirty()) {
                return true;
            }
        }
        for (ConnectionSettings conn : connections.values()) {
            if (conn.isDirty()) {
                return true;
            }
        }
        if (mRingMode.isDirty()) {
            return true;
        }
        if (mAirplaneMode.isDirty()) {
            return true;
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

        builder.append("<uuids>");
        for (UUID u : mSecondaryUuids) {
            builder.append("<uuid>");
            builder.append(TextUtils.htmlEncode(u.toString()));
            builder.append("</uuid>");
        }
        builder.append("</uuids>\n");

        builder.append("<profiletype>");
        builder.append(getProfileType() == TOGGLE_TYPE ? "toggle" : "conditional");
        builder.append("</profiletype>\n");

        builder.append("<statusbar>");
        builder.append(getStatusBarIndicator() ? "yes" : "no");
        builder.append("</statusbar>\n");

        builder.append("<screen-lock-mode>");
        builder.append(mScreenLockMode);
        builder.append("</screen-lock-mode>\n");

        mAirplaneMode.getXmlString(builder, context);

        mRingMode.getXmlString(builder, context);

        for (ProfileGroup pGroup : profileGroups.values()) {
            pGroup.getXmlString(builder, context);
        }
        for (StreamSettings sd : streams.values()) {
            sd.getXmlString(builder, context);
        }
        for (ConnectionSettings cs : connections.values()) {
            cs.getXmlString(builder, context);
        }
        if (!mWifiTriggers.isEmpty()) {
            builder.append("<triggers>\n");
            for (Map.Entry<String,Integer> e : mWifiTriggers.entrySet()) {
                builder.append("<wifiAP ssid=\"");
                builder.append(e.getKey());
                builder.append("\" state=\"");
                builder.append(e.getValue());
                builder.append("\"></wifiAP>\n");
            }
            builder.append("</triggers>\n");
        }

        builder.append("</profile>\n");
        mDirty = false;
    }

    private static List<UUID> readSecondaryUuidsFromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException,
            IOException {
        ArrayList<UUID> uuids = new ArrayList<UUID>();
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("uuids")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("uuid")) {
                    try {
                        uuids.add(UUID.fromString(xpp.nextText()));
                    } catch (NullPointerException e) {
                        Log.w(TAG, "Null Pointer - invalid UUID");
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "UUID not recognized");
                    }
                }
            }
            event = xpp.next();
        }
        return uuids;
    }

    private static HashMap<String, Integer> readWifiTriggersFromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException,
            IOException {
        int event = xpp.next();
        HashMap<String, Integer> triggers = new HashMap<String, Integer>();
        while (event != XmlPullParser.END_TAG || xpp.getName().equals("wifiAP")) {
            if (event == XmlPullParser.START_TAG){
                String ssid = xpp.getAttributeValue(null, "ssid");
                String state = xpp.getAttributeValue(null, "state");
                triggers.put(ssid, Integer.valueOf(state));
            }
            event = xpp.next();
        }
        return triggers;
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
                if (name.equals("uuids")) {
                    profile.setSecondaryUuids(readSecondaryUuidsFromXml(xpp, context));
                }
                if (name.equals("statusbar")) {
                    profile.setStatusBarIndicator(xpp.nextText().equals("yes"));
                }
                if (name.equals("profiletype")) {
                    profile.setProfileType(xpp.nextText().equals("toggle") ? TOGGLE_TYPE : CONDITIONAL_TYPE);
                }
                if (name.equals("ringModeDescriptor")) {
                    RingModeSettings smd = RingModeSettings.fromXml(xpp, context);
                    profile.setRingMode(smd);
                }
                if (name.equals("airplaneModeDescriptor")) {
                    AirplaneModeSettings amd = AirplaneModeSettings.fromXml(xpp, context);
                    profile.setAirplaneMode(amd);
                }
                if (name.equals("screen-lock-mode")) {
                    profile.setScreenLockMode(Integer.valueOf(xpp.nextText()));
                }
                if (name.equals("profileGroup")) {
                    ProfileGroup pg = ProfileGroup.fromXml(xpp, context);
                    profile.addProfileGroup(pg);
                }
                if (name.equals("streamDescriptor")) {
                    StreamSettings sd = StreamSettings.fromXml(xpp, context);
                    profile.setStreamSettings(sd);
                }
                if (name.equals("connectionDescriptor")) {
                    ConnectionSettings cs = ConnectionSettings.fromXml(xpp, context);
                    profile.connections.put(cs.getConnectionId(), cs);
                }
                if (name.equals("triggers")) {
                    profile.mWifiTriggers = readWifiTriggersFromXml(xpp, context);
                }
            }
            event = xpp.next();
        }

        /* we just loaded from XML, so nothing needs saving */
        profile.mDirty = false;

        return profile;
    }

    /** @hide */
    public void doSelect(Context context) {
        // Set stream volumes
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        for (StreamSettings sd : streams.values()) {
            if (sd.isOverride()) {
                am.setStreamVolume(sd.getStreamId(), sd.getValue(), 0);
            }
        }
        // Set connections
        for (ConnectionSettings cs : connections.values()) {
            if (cs.isOverride()) {
                cs.processOverride(context);
            }
        }
        // Set ring mode
        mRingMode.processOverride(context);
        // Set airplane mode
        mAirplaneMode.processOverride(context);
    }

    /** @hide */
    public StreamSettings getSettingsForStream(int streamId){
        return streams.get(streamId);
    }

    /** @hide */
    public void setStreamSettings(StreamSettings descriptor){
        streams.put(descriptor.getStreamId(), descriptor);
        mDirty = true;
    }

    /** @hide */
    public Collection<StreamSettings> getStreamSettings(){
        return streams.values();
    }

    /** @hide */
    public ConnectionSettings getSettingsForConnection(int connectionId){
        return connections.get(connectionId);
    }

    /** @hide */
    public void setConnectionSettings(ConnectionSettings descriptor){
        connections.put(descriptor.getConnectionId(), descriptor);
    }

    /** @hide */
    public Collection<ConnectionSettings> getConnectionSettings(){
        return connections.values();
    }

}
