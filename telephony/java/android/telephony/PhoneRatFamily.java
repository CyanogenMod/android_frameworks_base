/*
 * Copyright (C) 2014 MediaTek Inc.
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

package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Object to indicate the phone RAT family.
 *
 * @hide
 */
public class PhoneRatFamily implements Parcelable {
    /* Phone ID of phone */
    private int mPhoneId;
    /* New phone rat family */
    private int mRatFamily;

    public static final int PHONE_RAT_FAMILY_NONE = 0x00;
    public static final int PHONE_RAT_FAMILY_2G = 0x01;
    public static final int PHONE_RAT_FAMILY_3G = 0x02;
    public static final int PHONE_RAT_FAMILY_4G = 0x04;

    /**
     * Constructor.
     *
     * @param phoneId the phone ID
     * @param ratFamily the new RAT family
     */
    public PhoneRatFamily(int phoneId, int ratFamily) {
        mPhoneId = phoneId;
        mRatFamily = ratFamily;
    }

    /**
     * Get phone ID.
     *
     * @return phone ID
     */
    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Get RAT family.
     *
     * @return phone RAT family
     */
    public int getRatFamily() {
        return mRatFamily;
    }

    @Override
    public String toString() {
        String ret = "{ mPhoneId = " + mPhoneId
                + ", mRatFamily = " + mRatFamily
                + "}";
        return ret;
    }

    /**
     * Implement the Parcelable interface.
     *
     * @return describe content
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface.
     *
     * @param outParcel The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     */
    public void writeToParcel(Parcel outParcel, int flags) {
        outParcel.writeInt(mPhoneId);
        outParcel.writeInt(mRatFamily);
    }

    /**
     * Implement the Parcelable interface.
     */
    public static final Creator<PhoneRatFamily> CREATOR = new Creator<PhoneRatFamily>() {
        @Override
        public PhoneRatFamily createFromParcel(Parcel in) {
            int phoneId = in.readInt();
            int ratFamily = in.readInt();

            return new PhoneRatFamily(phoneId, ratFamily);
        }

        @Override
        public PhoneRatFamily[] newArray(int size) {
            return new PhoneRatFamily[size];
        }
    };
}

