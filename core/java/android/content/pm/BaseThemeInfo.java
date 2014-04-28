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
     * The theme id, which does not change when the theme is modified.
     * Specifies an Android UI Style using style name.
     *
     * @see themeId attribute
     *
     */
    public String themeId;

    /**
     * The name of the theme (as displayed by UI).
     *
     * @see name attribute
     *
     */
    public String name;

    /**
     * The author name of the theme package.
     *
     * @see author attribute
     *
     */
    public String author;

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
        dest.writeString(themeId);
        dest.writeString(name);
        dest.writeString(author);
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
        themeId = source.readString();
        name = source.readString();
        author = source.readString();
    }
}
