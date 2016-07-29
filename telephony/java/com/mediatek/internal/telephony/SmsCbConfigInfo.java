/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


package com.mediatek.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 *  A parcelable holder class of byte[] for ISms aidl implementation
 */
public class SmsCbConfigInfo implements Parcelable {
    public int mFromServiceId;
    public int mToServiceId;
    public int mFromCodeScheme;
    public int mToCodeScheme;
    public boolean mSelected;

    public SmsCbConfigInfo(int fromId, int toId, int fromScheme,
            int toScheme, boolean selected) {
        this.mFromServiceId = fromId;
        this.mToServiceId = toId;
        this.mFromCodeScheme = fromScheme;
        this.mToCodeScheme = toScheme;
        this.mSelected = selected;
    }

    public static final Parcelable.Creator<SmsCbConfigInfo> CREATOR = new Parcelable.Creator<SmsCbConfigInfo>() {
        public SmsCbConfigInfo createFromParcel(Parcel source) {
            int mFromServiceId = source.readInt();
            int mToServiceId = source.readInt();
            int mFromCodeScheme = source.readInt();
            int mToCodeScheme = source.readInt();
            boolean mSelected = source.readByte() != 0;

            return new SmsCbConfigInfo(mFromServiceId, mToServiceId, mFromCodeScheme, mToCodeScheme, mSelected);
        }

        public SmsCbConfigInfo[] newArray(int size) {
            return new SmsCbConfigInfo[size];
        }
    };

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFromServiceId);
        dest.writeInt(mToServiceId);
        dest.writeInt(mFromCodeScheme);
        dest.writeInt(mToCodeScheme);
        dest.writeByte((byte) (mSelected ? 1 : 0));
    }

    public int describeContents() {
        return 0;
    }

}
