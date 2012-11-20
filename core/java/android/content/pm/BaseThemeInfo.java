/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

package android.content.pm;

import android.os.Parcelable;
import android.os.Parcel;
import android.util.Log;
import android.util.AttributeSet;
import android.content.res.Resources;

/**
 * @hide
 */
public class BaseThemeInfo implements Parcelable {

    /**
     * Wallpaper drawable.
     *
     * @see wallpaperImage attribute
     */
    public int wallpaperResourceId;

    /**
     * The resource id of theme thumbnail.
     * Specifies a theme thumbnail image resource as @drawable/foo.
     *
     * @see thumbnail attribute
     *
     */
    public int thumbnailResourceId;

    /**
     * The theme id, which does not change when the theme is modified.
     * Specifies an Android UI Style using style name.
     *
     * @see themeId attribute
     *
     */
    public String themeId;

    /**
     * The style resource id of Android UI Style, supplied by the resource commpiler.
     * Specifies an Android UI Style id.
     *
     * @see styleId attribute
     *
     */
    public int styleResourceId = 0;

    /**
     * The name of the theme (as displayed by UI).
     *
     * @see name attribute
     *
     */
    public String name;

    /**
     * The name of the call ringtone audio file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see ringtoneFileName attribute
     *
     */
    public String ringtoneFileName;

    /**
     * The name of the call ringtone as shown to user.
     *
     * @see ringtoneName attribute
     *
     */
    public String ringtoneName;

    /**
     * The name of the notification ringtone audio file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see notificationRingtoneFileName attribute
     *
     */
    public String notificationRingtoneFileName;

    /**
     * The name of the notification ringtone as shown to user.
     *
     * @see notificationRingtoneName attribute
     *
     */
    public String notificationRingtoneName;

    /**
     * The author name of the theme package.
     *
     * @see author attribute
     *
     */
    public String author;

    /**
     * The copyright text.
     *
     * @see copyright attribute
     *
     */
    public String copyright;

    /**
     * {@hide}
     */
    // There is no corresposponding flag in manifest file
    // This flag is set to true iff any media resource is DRM protected
    public boolean isDrmProtected = false;

    /**
     * The name of the "main" theme style (as displayed by UI).
     *
     * @see themeStyleName attribute
     *
     */
    public String themeStyleName;

    /**
     * Preview image drawable.
     *
     * @see preview attribute
     */
    public int previewResourceId;

    /**
     * The name of a sound pack.
     *
     * @see soundpack attribute
     *
     */
    public String soundPackName;


    private static final String LOCKED_NAME = "locked/";

    /*
     * Describe the kinds of special objects contained in this Parcelable's
     * marshalled representation.
     *
     * @return a bitmask indicating the set of special object types marshalled
     * by the Parcelable.
     *
     * @see android.os.Parcelable#describeContents()
     */
    public int describeContents() {
        return 0;
    }

    /*
     * Flatten this object in to a Parcel.
     *
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     * May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     *
     * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(wallpaperResourceId);
        dest.writeInt(thumbnailResourceId);
        dest.writeString(themeId);
        dest.writeInt(styleResourceId);
        dest.writeString(name);
        dest.writeString(ringtoneFileName);
        dest.writeString(notificationRingtoneFileName);
        dest.writeString(ringtoneName);
        dest.writeString(notificationRingtoneName);
        dest.writeString(author);
        dest.writeString(copyright);
        dest.writeInt(isDrmProtected? 1 : 0);
        dest.writeString(soundPackName);
        dest.writeString(themeStyleName);
        dest.writeInt(previewResourceId);
    }

    /** @hide */
    public static final Parcelable.Creator<BaseThemeInfo> CREATOR
            = new Parcelable.Creator<BaseThemeInfo>() {
        public BaseThemeInfo createFromParcel(Parcel source) {
            return new BaseThemeInfo(source);
        }

        public BaseThemeInfo[] newArray(int size) {
            return new BaseThemeInfo[size];
        }
    };

    /** @hide */
    public final String getResolvedString(Resources res, AttributeSet attrs, int index) {
        int resId = attrs.getAttributeResourceValue(index, 0);
        if (resId !=0 ) {
            return res.getString(resId);
        }
        return attrs.getAttributeValue(index);
    }

    protected BaseThemeInfo() {
    }

    protected BaseThemeInfo(Parcel source) {
        wallpaperResourceId = source.readInt();
        thumbnailResourceId = source.readInt();
        themeId = source.readString();
        styleResourceId = source.readInt();
        name = source.readString();
        ringtoneFileName = source.readString();
        notificationRingtoneFileName = source.readString();
        ringtoneName = source.readString();
        notificationRingtoneName = source.readString();
        author = source.readString();
        copyright = source.readString();
        isDrmProtected = (source.readInt() != 0);
        soundPackName = source.readString();
        themeStyleName = source.readString();
        previewResourceId = source.readInt();
    }

    protected void changeDrmFlagIfNeeded(String resourcePath) {
        if (resourcePath != null && resourcePath.contains(LOCKED_NAME)) {
            isDrmProtected = true;
        }
    }
}
