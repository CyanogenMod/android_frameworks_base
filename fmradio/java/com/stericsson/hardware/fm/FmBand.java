/*
 * Copyright (C) ST-Ericsson SA 2010
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
 *
 * Author: Bjorn Pileryd (bjorn.pileryd@sonyericsson.com)
 * Author: Markus Grape (markus.grape@stericsson.com) for ST-Ericsson
 */

package com.stericsson.hardware.fm;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes the properties of the FM frequency band. The frequency band range
 * and the channel offset vary in different regions. The unit for all
 * frequencies in this class is kHz.
 */
public class FmBand implements Parcelable {

    /**
     * Default band for US 87.9MHz - 107.9MHz, 200kHz channel offset.
     */
    public static final int BAND_US = 0;

    /**
     * Default band for EU 87.5MHz - 108MHz, 100kHz channel offset.
     */
    public static final int BAND_EU = 1;

    /**
     * Default band for Japan 76MHz - 90MHz, 100kHz channel offset.
     */
    public static final int BAND_JAPAN = 2;

    /**
     * Default band for China 70MHz - 108MHz, 50kHz channel offset.
     */
    public static final int BAND_CHINA = 3;

    /**
     * Default band for EU 87.5MHz - 108MHz, 50kHz channel offset.
     */
    public static final int BAND_EU_50K_OFFSET = 4;

    /**
     * Unknown frequency.
     */
    public static final int FM_FREQUENCY_UNKNOWN = -1;

    /**
     * The lowest frequency of the band.
     */
    private int mMinFrequency;

    /**
     * The highest frequency of the band.
     */
    private int mMaxFrequency;

    /**
     * The default frequency of the band.
     */
    private int mDefaultFrequency;

    /**
     * The offset between the channels in the band.
     */
    private int mChannelOffset;

    /**
     * Creates a band representation.
     *
     * @param minFrequency
     *            the lowest frequency of the band in kHz
     * @param maxFrequency
     *            the highest frequency of the band in kHz
     * @param channelOffset
     *            the offset between the channels in the band in kHz
     * @param defaultFrequency
     *            the default frequency that the hardware will tune to at
     *            startup
     * @throws IllegalArgumentException
     *             if the minFrequency is equal or higher then maxFrequency
     * @throws IllegalArgumentException
     *             if the defaultFrequency is not within the limits of
     *             minFrequency and maxFrequency
     * @throws IllegalArgumentException
     *             if the minFrequency or maxFrequency is not a multiplier of channelOffset
     */
    public FmBand(int minFrequency, int maxFrequency, int channelOffset, int defaultFrequency) {
        if (minFrequency >= maxFrequency) {
            throw new IllegalArgumentException(
                    "Minimum frequency can not be equal or higher than maximum frequency");
        }
        if (defaultFrequency < minFrequency) {
            throw new IllegalArgumentException(
                    "Default frequency can not be less than minFrequency");
        }
        if (defaultFrequency > maxFrequency) {
            throw new IllegalArgumentException(
                    "Default frequency can not be higher than maxFrequency");
        }
        if ((maxFrequency - minFrequency) % channelOffset != 0
                || (defaultFrequency - minFrequency) % channelOffset != 0) {
            throw new IllegalArgumentException(
                    "Frequency has invalid offset");
        }
        this.mMinFrequency = minFrequency;
        this.mMaxFrequency = maxFrequency;
        this.mDefaultFrequency = defaultFrequency;
        this.mChannelOffset = channelOffset;
    }

    /**
     * Creates a standard band representation. The default frequency will be the
     * lowest frequency for the specified band.
     *
     * @param band
     *            one of {@link #BAND_US}, {@link #BAND_EU}, {@link #BAND_JAPAN}
     *            , {@link #BAND_CHINA}, {@link #BAND_EU_50K_OFFSET}
     * @throws IllegalArgumentException
     *             if the band is not one of {@link #BAND_US}, {@link #BAND_EU},
     *             {@link #BAND_JAPAN}, {@link #BAND_CHINA}, {@link #BAND_EU_50K_OFFSET}
     */
    public FmBand(int band) {
        switch (band) {
        case BAND_US:
            this.mMinFrequency = 87900;
            this.mMaxFrequency = 107900;
            this.mDefaultFrequency = 87900;
            this.mChannelOffset = 200;
            break;
        case BAND_EU:
            this.mMinFrequency = 87500;
            this.mMaxFrequency = 108000;
            this.mDefaultFrequency = 87500;
            this.mChannelOffset = 100;
            break;
        case BAND_JAPAN:
            this.mMinFrequency = 76000;
            this.mMaxFrequency = 90000;
            this.mDefaultFrequency = 76000;
            this.mChannelOffset = 100;
            break;
        case BAND_CHINA:
            this.mMinFrequency = 70000;
            this.mMaxFrequency = 108000;
            this.mDefaultFrequency = 70000;
            this.mChannelOffset = 50;
            break;
        case BAND_EU_50K_OFFSET:
            this.mMinFrequency = 87500;
            this.mMaxFrequency = 108000;
            this.mDefaultFrequency = 87500;
            this.mChannelOffset = 50;
            break;
        default:
            throw new IllegalArgumentException("Wrong band identifier");
        }
    }

    /**
     * Checks if a frequency is valid to the band. To be valid it must be within
     * the frequency range and on a frequency with correct channel offset.
     *
     * @param frequency
     *            the frequency to validate
     * @return true if the frequency is valid for this band
     */
    public boolean isFrequencyValid(int frequency) {
        if (frequency < mMinFrequency || frequency > mMaxFrequency) {
            return false;
        }
        if ((frequency - mMinFrequency) % mChannelOffset != 0) {
            return false;
        }
        return true;
    }

    /**
     * Return the lowest frequency of the band.
     *
     * @return the lowest frequency of the band in kHz
     */
    public int getMinFrequency() {
        return mMinFrequency;
    }

    /**
     * Returns the highest frequency of the band.
     *
     * @return the highest frequency of the band in kHz
     */
    public int getMaxFrequency() {
        return mMaxFrequency;
    }

    /**
     * Returns the default frequency of the band that the hardware will tune to
     * at startup.
     *
     * @return the default frequency of the band in kHz
     */
    public int getDefaultFrequency() {
        return mDefaultFrequency;
    }

    /**
     * Returns the offset between the channels in the band.
     *
     * @return the offset between the channels in the band in kHz
     */
    public int getChannelOffset() {
        return mChannelOffset;
    }

    /** Implement the Parcelable interface {@hide} */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMinFrequency);
        dest.writeInt(mMaxFrequency);
        dest.writeInt(mChannelOffset);
        dest.writeInt(mDefaultFrequency);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<FmBand> CREATOR = new Creator<FmBand>() {
        public FmBand createFromParcel(Parcel in) {
            int minfreq = in.readInt();
            int maxfreq = in.readInt();
            int offset = in.readInt();
            int defaultFreq = in.readInt();
            FmBand band = new FmBand(minfreq, maxfreq, offset, defaultFreq);
            return band;
        }

        public FmBand[] newArray(int size) {
            return new FmBand[size];
        }
    };
}
