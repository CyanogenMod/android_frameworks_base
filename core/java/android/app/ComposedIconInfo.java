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

import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class ComposedIconInfo implements Parcelable {
    public int iconUpon, iconMask;
    public int[] iconBacks;
    public float iconScale;
    public float iconRotation;
    // value used to provide some randomization to the angle of rotation
    public float iconRotationVariance;
    public float iconTranslationX;
    public float iconTranslationY;
    public int iconDensity;
    public int iconSize;
    public float[] colorFilter;

    // Palettized background items
    public int iconPaletteBack;
    public SwatchType swatchType;
    public int[] defaultSwatchColors;

    public ComposedIconInfo() {
        super();
        iconPaletteBack = 0;
        swatchType = SwatchType.None;
        iconRotation = 0;
        iconTranslationX = 0;
        iconTranslationY = 0;
    }

    private ComposedIconInfo(Parcel source) {
        iconScale = source.readFloat();
        iconRotation = source.readFloat();
        iconRotationVariance = source.readFloat();
        iconTranslationX = source.readFloat();
        iconTranslationY = source.readFloat();
        iconDensity = source.readInt();
        iconSize = source.readInt();
        int backCount = source.readInt();
        if (backCount > 0) {
            iconBacks = new int[backCount];
            for (int i = 0; i < backCount; i++) {
                iconBacks[i] = source.readInt();
            }
        }
        iconMask = source.readInt();
        iconUpon = source.readInt();
        int colorFilterSize = source.readInt();
        if (colorFilterSize > 0) {
            colorFilter = new float[colorFilterSize];
            for (int i = 0; i < colorFilterSize; i++) {
                colorFilter[i] = source.readFloat();
            }
        }
        iconPaletteBack = source.readInt();
        swatchType = SwatchType.values()[source.readInt()];
        int numDefaultColors = source.readInt();
        if (numDefaultColors > 0) {
            defaultSwatchColors = new int[numDefaultColors];
            for (int i = 0; i < numDefaultColors; i++) {
                defaultSwatchColors[i] = source.readInt();
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(iconScale);
        dest.writeFloat(iconRotation);
        dest.writeFloat(iconRotationVariance);
        dest.writeFloat(iconTranslationX);
        dest.writeFloat(iconTranslationY);
        dest.writeInt(iconDensity);
        dest.writeInt(iconSize);
        dest.writeInt(iconBacks != null ? iconBacks.length : 0);
        if (iconBacks != null) {
            for (int resId : iconBacks) {
                dest.writeInt(resId);
            }
        }
        dest.writeInt(iconMask);
        dest.writeInt(iconUpon);
        if (colorFilter != null) {
            dest.writeInt(colorFilter.length);
            for (float val : colorFilter) {
                dest.writeFloat(val);
            }
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(iconPaletteBack);
        dest.writeInt(swatchType.ordinal());
        if (defaultSwatchColors != null) {
            dest.writeInt(defaultSwatchColors.length);
            for (int color : defaultSwatchColors) {
                dest.writeInt(color);
            }
        } else {
            dest.writeInt(0);
        }
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

    public enum SwatchType {
        None,
        Vibrant,
        VibrantLight,
        VibrantDark,
        Muted,
        MutedLight,
        MutedDark
    }
}
