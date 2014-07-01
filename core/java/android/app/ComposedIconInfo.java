/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class ComposedIconInfo implements Parcelable {
    public BitmapDrawable iconUpon, iconMask;
    public BitmapDrawable[] iconBacks;
    public float iconScale;
    public int iconDensity;
    public int iconSize;

    public ComposedIconInfo() {
        super();
    }

    private ComposedIconInfo(Parcel source) {
        ClassLoader bmpClassLoader = Bitmap.class.getClassLoader();
        iconScale = source.readFloat();
        iconDensity = source.readInt();
        iconSize = source.readInt();
        int backCount = source.readInt();
        if (backCount > 0) {
            iconBacks = new BitmapDrawable[backCount];
            for (int i = 0; i < backCount; i++) {
                iconBacks[i] = new BitmapDrawable((Bitmap) source.readParcelable(bmpClassLoader));
            }
        }
        iconMask = new BitmapDrawable((Bitmap) source.readParcelable(bmpClassLoader));
        iconUpon = new BitmapDrawable((Bitmap) source.readParcelable(bmpClassLoader));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(iconScale);
        dest.writeInt(iconDensity);
        dest.writeInt(iconSize);
        dest.writeInt(iconBacks != null ? iconBacks.length : 0);
        if (iconBacks != null) {
            for (BitmapDrawable d : iconBacks) {
                dest.writeParcelable(d != null ? d.getBitmap() : null, flags);
            }
        }
        dest.writeParcelable(iconMask != null ? iconMask.getBitmap() : null, flags);
        dest.writeParcelable(iconUpon != null ? iconUpon.getBitmap() : null, flags);
    }

    public static final Creator<ComposedIconInfo> CREATOR
            = new Creator<ComposedIconInfo>() {
        @Override
        public ComposedIconInfo createFromParcel(Parcel source) {
            return new ComposedIconInfo(source);
        }

        @Override
        public ComposedIconInfo[] newArray(int size) {
            return new ComposedIconInfo[0];
        }
    };
}
