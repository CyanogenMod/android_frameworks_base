/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.internal.telephony;

 import android.os.Parcel;
 import android.os.Parcelable;
 /**
 * Represents the response information of BTSimap operation, including
 * Received protocal type and APDU data.
 * @hide
 */
public class BtSimapOperResponse implements Parcelable {
    public static final int SUCCESS = 0;
    public static final int ERR_NO_REASON_DEFINED = 1;    //CME ERROR: 611
    public static final int ERR_CARD_NOT_ACCESSIBLE = 2;   //CME ERROR: 612
    public static final int ERR_CARD_POWERED_OFF = 3;
    public static final int ERR_CARD_REMOVED = 4;              // CME ERROR:613
    public static final int ERR_CARD_POWERED_ON = 5;   // already power on
    public static final int ERR_DATA_NOT_AVAILABLE = 6;
    public static final int ERR_NOT_SUPPORTED = 7;

    private static final byte CURTYPE_MASK = 0x01;
    private static final byte SUPPORTTYPE_MASK = 0x02;
    private static final byte ATR_MASK = 0x04;
    private static final byte APDU_RESPONSE_MASK = 0x08;

    static final int UNKNOWN_PROTOCOL_TYPE = -1;

    private int mParams;
    private int mCurType;
    private int mSupportType;
    private String mStrATR;
    private String mStrAPDU;

    public BtSimapOperResponse() {
        mParams = 0;
        mCurType = UNKNOWN_PROTOCOL_TYPE;
        mSupportType = UNKNOWN_PROTOCOL_TYPE;
        mStrATR = null;
        mStrAPDU = null;
    }

    /**
     * Initialize the object from a parcel.
     */
    public BtSimapOperResponse(Parcel in) {
        mParams = in.readInt();
        mCurType = in.readInt();
        mSupportType = in.readInt();
        mStrATR = in.readString();
        mStrAPDU = in.readString();
    }

    public boolean isCurTypeExist() {
        if ((mParams & CURTYPE_MASK) > 0) {
            return true;
        } else {
           return false;
        }
    }

    public boolean isSupportTypeExist() {
        if ((mParams & SUPPORTTYPE_MASK) > 0) {
            return true;
        } else {
           return false;
        }
    }

    public boolean isAtrExist() {
        if ((mParams & ATR_MASK) > 0) {
            return true;
        } else {
           return false;
        }
    }

    public boolean isApduExist() {
        if ((mParams & APDU_RESPONSE_MASK) > 0) {
            return true;
        } else {
           return false;
        }
    }

    /**
     * Get Current type.
     *
     * @return Current type
     *
     * @internal
     */
    public int getCurType() {
        if (isCurTypeExist()) {
            return mCurType;
        } else {
            return UNKNOWN_PROTOCOL_TYPE;
        }
    }

    /**
     * Get support type.
     *
     * @return support type
     *
     * @internal
     */
    public int getSupportType() {
        if (isSupportTypeExist()) {
            return mSupportType;
        } else {
            return UNKNOWN_PROTOCOL_TYPE;
        }
    }

    /**
     * Get ATR String.
     *
     * @return ATR String
     *
     * @internal
     */
    public String getAtrString() {
        if (isAtrExist()) {
            return mStrATR;
        } else {
            return null;
        }
    }

    /**
     * Get APDU String.
     *
     * @return APDU String
     *
     * @internal
     */
    public String getApduString() {
        if (isApduExist()) {
            return mStrAPDU;
        } else {
            return null;
        }
    }

    /**
     * Set cuurent type.
     *
     * @param nType current protool type.
     *
     * @internal
     */
    public void setCurType(int nType) {
        if (nType == 0 || nType == 1) {
            mCurType = nType;
            mParams |= CURTYPE_MASK;
        }
    }

    /**
     * Set support type.
     *
     * @param nType protool type.
     *
     * @internal
     */
    public void setSupportType(int nType) {
        if (nType == 0 || nType == 1 || nType == 2) {
            mSupportType = nType;
            mParams |= SUPPORTTYPE_MASK;
        }
    }

    /**
     * Set ATR String.
     *
     * @param strVal ATR string
     *
     * @internal
     */
    public void setAtrString(String strVal) {
        if (strVal != null) {
            mStrATR = strVal;
            mParams |= ATR_MASK;
        }
    }

    /**
     * Set APDU String.
     *
     * @param strVal APDU string
     *
     * @internal
     */
    public void setApduString(String strVal) {
        if (strVal != null) {
            mStrAPDU = strVal;
            mParams |= APDU_RESPONSE_MASK;
        }
    }

    public void readFromParcel(Parcel source) {
        mParams = source.readInt();
        mCurType = source.readInt();
        mSupportType = source.readInt();
        mStrATR = source.readString();
        mStrAPDU = source.readString();
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable.Creator}
     *
     * @hide
     */
    public static final Parcelable.Creator<BtSimapOperResponse> CREATOR = new Parcelable.Creator() {
        public BtSimapOperResponse createFromParcel(Parcel in) {
            return new BtSimapOperResponse(in);
        }

        public BtSimapOperResponse[] newArray(int size) {
            return new BtSimapOperResponse[size];
        }
    };

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mParams);
        dest.writeInt(mCurType);
        dest.writeInt(mSupportType);
        dest.writeString(mStrATR);
        dest.writeString(mStrAPDU);
    }
}
