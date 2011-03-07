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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.IOException;

public class ProfileGroup implements Parcelable {

    private String mName;

    private Uri mSoundOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    private Uri mRingerOverride = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

    private Mode mSoundMode = Mode.DEFAULT;

    private Mode mRingerMode = Mode.DEFAULT;

    private Mode mVibrateMode = Mode.DEFAULT;

    private Mode mLightsMode = Mode.DEFAULT;

    private boolean mDefaultGroup = false;

    public static final Parcelable.Creator<ProfileGroup> CREATOR = new Parcelable.Creator<ProfileGroup>() {
        public ProfileGroup createFromParcel(Parcel in) {
            return new ProfileGroup(in);
        }

        @Override
        public ProfileGroup[] newArray(int size) {
            return new ProfileGroup[size];
        }
    };

    public ProfileGroup(String name) {
        this(name, false);
    }

    ProfileGroup(String name, boolean defaultGroup) {
        mName = name;
        mDefaultGroup = defaultGroup;
    }

    private ProfileGroup(Parcel in) {
        readFromParcel(in);
    }

    public String getName() {
        return mName;
    }

    public boolean isDefaultGroup() {
        return mDefaultGroup;
    }

    public void setSoundOverride(Uri sound) {
        this.mSoundOverride = sound;
    }

    public Uri getSoundOverride() {
        return mSoundOverride;
    }

    public void setRingerOverride(Uri ringer) {
        this.mRingerOverride = ringer;
    }

    public Uri getRingerOverride() {
        return mRingerOverride;
    }

    public void setSoundMode(Mode soundMode) {
        this.mSoundMode = soundMode;
    }

    public Mode getSoundMode() {
        return mSoundMode;
    }

    public void setRingerMode(Mode ringerMode) {
        this.mRingerMode = ringerMode;
    }

    public Mode getRingerMode() {
        return mRingerMode;
    }

    public void setVibrateMode(Mode vibrateMode) {
        this.mVibrateMode = vibrateMode;
    }

    public Mode getVibrateMode() {
        return mVibrateMode;
    }

    public void setLightsMode(Mode lightsMode) {
        this.mLightsMode = lightsMode;
    }

    public Mode getLightsMode() {
        return mLightsMode;
    }

    // TODO : add support for LEDs / screen etc.

    /* package */Notification processNotification(Notification notification) {

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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeValue(mDefaultGroup);
        dest.writeParcelable(mSoundOverride, flags);
        dest.writeParcelable(mRingerOverride, flags);

        dest.writeString(mSoundMode.name());
        dest.writeString(mRingerMode.name());
        dest.writeString(mVibrateMode.name());
        dest.writeString(mLightsMode.name());
    }

    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mDefaultGroup = (Boolean)in.readValue(null);
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

    public String getXmlString() {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    public void getXmlString(StringBuilder builder) {
        builder.append("<profileGroup name=\"" + TextUtils.htmlEncode(getName()) + "\" default=\""
                + isDefaultGroup() + "\">\n");
        builder.append("<sound>" + TextUtils.htmlEncode(mSoundOverride.toString()) + "</sound>\n");
        builder.append("<ringer>" + TextUtils.htmlEncode(mRingerOverride.toString())
                + "</ringer>\n");
        builder.append("<soundMode>" + mSoundMode + "</soundMode>\n");
        builder.append("<ringerMode>" + mRingerMode + "</ringerMode>\n");
        builder.append("<vibrateMode>" + mVibrateMode + "</vibrateMode>\n");
        builder.append("<lightsMode>" + mLightsMode + "</lightsMode>\n");
        builder.append("</profileGroup>\n");
    }

    public static ProfileGroup fromXml(XmlPullParser xpp) throws XmlPullParserException,
            IOException {
        return fromXml(xpp, null);
    }
    public static ProfileGroup fromXml(XmlPullParser xpp, Context context) throws XmlPullParserException,
            IOException {
        String defaultGroup = xpp.getAttributeValue(null, "default");
        defaultGroup = defaultGroup == null ? "false" : defaultGroup;
        String attr = Profile.getAttrResString(xpp, context);
        ProfileGroup profileGroup = new ProfileGroup(attr, defaultGroup.equals("true"));
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("profileGroup")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
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
        return profileGroup;
    }

}
