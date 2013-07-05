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
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * @hide
 */
public final class ProfileGroup implements Parcelable {
    private static final String TAG = "ProfileGroup";

    private String mName;
    private int mNameResId;

    private UUID mUuid;

    private Uri mSoundOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    private Uri mRingerOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

    private Mode mSoundMode = Mode.DEFAULT;
    private Mode mRingerMode = Mode.DEFAULT;
    private Mode mVibrateMode = Mode.DEFAULT;
    private Mode mLightsMode = Mode.DEFAULT;

    private boolean mDefaultGroup = false;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<ProfileGroup> CREATOR = new Parcelable.Creator<ProfileGroup>() {
        public ProfileGroup createFromParcel(Parcel in) {
            return new ProfileGroup(in);
        }

        @Override
        public ProfileGroup[] newArray(int size) {
            return new ProfileGroup[size];
        }
    };

    /** @hide */
    public ProfileGroup(UUID uuid, boolean defaultGroup) {
        this(null, uuid, defaultGroup);
    }

    private ProfileGroup(String name, UUID uuid, boolean defaultGroup) {
        mName = name;
        mUuid = (uuid != null) ? uuid : UUID.randomUUID();
        mDefaultGroup = defaultGroup;
        mDirty = uuid == null;
    }

    /** @hide */
    private ProfileGroup(Parcel in) {
        readFromParcel(in);
    }

    /** @hide */
    public boolean matches(NotificationGroup group, boolean defaultGroup) {
        if (mUuid.equals(group.getUuid())) {
            return true;
        }

        /* fallback matches for backwards compatibility */
        boolean matches = false;

        /* fallback attempt 1: match name */
        if (mName != null && mName.equals(group.getName())) {
            matches = true;
        /* fallback attempt 2: match for the 'defaultGroup' flag to match the wildcard group */
        } else if (mDefaultGroup && defaultGroup) {
            matches = true;
        }

        if (!matches) {
            return false;
        }

        mName = null;
        mUuid = group.getUuid();
        mDirty = true;

        return true;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public boolean isDefaultGroup() {
        return mDefaultGroup;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    /** @hide */
    public void setSoundOverride(Uri sound) {
        mSoundOverride = sound;
        mDirty = true;
    }

    public Uri getSoundOverride() {
        return mSoundOverride;
    }

    /** @hide */
    public void setRingerOverride(Uri ringer) {
        mRingerOverride = ringer;
        mDirty = true;
    }

    public Uri getRingerOverride() {
        return mRingerOverride;
    }

    /** @hide */
    public void setSoundMode(Mode soundMode) {
        mSoundMode = soundMode;
        mDirty = true;
    }

    public Mode getSoundMode() {
        return mSoundMode;
    }

    /** @hide */
    public void setRingerMode(Mode ringerMode) {
        mRingerMode = ringerMode;
        mDirty = true;
    }

    public Mode getRingerMode() {
        return mRingerMode;
    }

    /** @hide */
    public void setVibrateMode(Mode vibrateMode) {
        mVibrateMode = vibrateMode;
        mDirty = true;
    }

    public Mode getVibrateMode() {
        return mVibrateMode;
    }

    /** @hide */
    public void setLightsMode(Mode lightsMode) {
        mLightsMode = lightsMode;
        mDirty = true;
    }

    public Mode getLightsMode() {
        return mLightsMode;
    }

    // TODO : add support for LEDs / screen etc.

    /** @hide */
    public Notification processNotification(Notification notification) {

        switch (mSoundMode) {
            case OVERRIDE:
                notification.sound = mSoundOverride;
                break;
            case SUPPRESS:
                silenceNotification(notification);
                break;
            case DEFAULT:
        }
        switch (mVibrateMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_VIBRATE;
                break;
            case SUPPRESS:
                suppressVibrate(notification);
                break;
            case DEFAULT:
        }
        switch (mLightsMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_LIGHTS;
                break;
            case SUPPRESS:
                suppressLights(notification);
                break;
            case DEFAULT:
        }
        return notification;
    }

    private boolean validateOverrideUri(Context context, Uri uri) {
        if (RingtoneManager.isDefault(uri)) {
            return true;
        }
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        boolean valid = false;

        if (cursor != null) {
            valid = cursor.moveToFirst();
            cursor.close();
        }
        return valid;
    }

    void validateOverrideUris(Context context) {
        if (!validateOverrideUri(context, mSoundOverride)) {
            mSoundOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mSoundMode = Mode.DEFAULT;
            mDirty = true;
        }
        if (!validateOverrideUri(context, mRingerOverride)) {
            mRingerOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mRingerMode = Mode.DEFAULT;
            mDirty = true;
        }
    }

    private void silenceNotification(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_SOUND);
        notification.sound = null;
    }

    private void suppressVibrate(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_VIBRATE);
        notification.vibrate = null;
    }

    private void suppressLights(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_LIGHTS);
        notification.flags &= (~Notification.FLAG_SHOW_LIGHTS);
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
        new ParcelUuid(mUuid).writeToParcel(dest, 0);
        dest.writeInt(mDefaultGroup ? 1 : 0);
        dest.writeInt(mDirty ? 1 : 0);
        dest.writeParcelable(mSoundOverride, flags);
        dest.writeParcelable(mRingerOverride, flags);

        dest.writeString(mSoundMode.name());
        dest.writeString(mRingerMode.name());
        dest.writeString(mVibrateMode.name());
        dest.writeString(mLightsMode.name());
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mUuid = ParcelUuid.CREATOR.createFromParcel(in).getUuid();
        mDefaultGroup = in.readInt() != 0;
        mDirty = in.readInt() != 0;
        mSoundOverride = in.readParcelable(null);
        mRingerOverride = in.readParcelable(null);

        mSoundMode = Mode.valueOf(Mode.class, in.readString());
        mRingerMode = Mode.valueOf(Mode.class, in.readString());
        mVibrateMode = Mode.valueOf(Mode.class, in.readString());
        mLightsMode = Mode.valueOf(Mode.class, in.readString());
    }

    public enum Mode {
        SUPPRESS, DEFAULT, OVERRIDE;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<profileGroup uuid=\"");
        builder.append(TextUtils.htmlEncode(mUuid.toString()));
        if (mName != null) {
            builder.append("\" name=\"");
            builder.append(mName);
        }
        builder.append("\" default=\"");
        builder.append(isDefaultGroup());
        builder.append("\">\n<sound>");
        builder.append(TextUtils.htmlEncode(mSoundOverride.toString()));
        builder.append("</sound>\n<ringer>");
        builder.append(TextUtils.htmlEncode(mRingerOverride.toString()));
        builder.append("</ringer>\n<soundMode>");
        builder.append(mSoundMode);
        builder.append("</soundMode>\n<ringerMode>");
        builder.append(mRingerMode);
        builder.append("</ringerMode>\n<vibrateMode>");
        builder.append(mVibrateMode);
        builder.append("</vibrateMode>\n<lightsMode>");
        builder.append(mLightsMode);
        builder.append("</lightsMode>\n</profileGroup>\n");
        mDirty = false;
    }

    /** @hide */
    public static ProfileGroup fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        String name = xpp.getAttributeValue(null, "name");
        UUID uuid = null;
        String value = xpp.getAttributeValue(null, "uuid");

        if (value != null) {
            try {
                uuid = UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "UUID not recognized for " + name + ", using new one.");
            }
        }

        value = xpp.getAttributeValue(null, "default");
        boolean defaultGroup = TextUtils.equals(value, "true");

        ProfileGroup profileGroup = new ProfileGroup(name, uuid, defaultGroup);
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("profileGroup")) {
            if (event == XmlPullParser.START_TAG) {
                name = xpp.getName();
                if (name.equals("sound")) {
                    profileGroup.setSoundOverride(Uri.parse(xpp.nextText()));
                } else if (name.equals("ringer")) {
                    profileGroup.setRingerOverride(Uri.parse(xpp.nextText()));
                } else if (name.equals("soundMode")) {
                    profileGroup.setSoundMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("ringerMode")) {
                    profileGroup.setRingerMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("vibrateMode")) {
                    profileGroup.setVibrateMode(Mode.valueOf(xpp.nextText()));
                } else if (name.equals("lightsMode")) {
                    profileGroup.setLightsMode(Mode.valueOf(xpp.nextText()));
                }
            }
            event = xpp.next();
        }

        /* we just loaded from XML, no need to save */
        profileGroup.mDirty = false;

        return profileGroup;
    }
}
