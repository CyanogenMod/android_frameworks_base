 /*
  * Copyright (C) 2015 NXP Semiconductors
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

package com.vzw.nfc;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Represents the routing info for single AID.
 */
public final class RouteEntry implements Parcelable {

    byte[] mAid;
    int mPowerState;
    int mLocation;
    boolean mAllowed;

    public RouteEntry() {

    }

    public RouteEntry(byte[] Aid, int PowerState, int Location, boolean allowed) {
        super();
        mAid = Aid;
        mPowerState = PowerState;
        mLocation = Location;
        mAllowed = allowed;
        Log.d("RouteEntry", "constructor mPowerState" + PowerState);
        Log.d("RouteEntry", "constructor mPowerState" + mPowerState);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeInt((true == mAllowed) ? 0x01 : 0x00);
        dest.writeInt(mLocation);
        dest.writeInt(mPowerState);
        dest.writeInt(mAid.length);
        dest.writeByteArray(mAid);
    }

    public static final Parcelable.Creator<RouteEntry> CREATOR = new Parcelable.Creator<RouteEntry>() {
        public RouteEntry createFromParcel(Parcel in) {
            boolean allowed = (((byte) in.readInt()) == 0x01) ? true : false;
            int location = in.readInt();
            int powerState = in.readInt();
            int aidLength = in.readInt();
            byte[] aid = new byte[aidLength];
            in.readByteArray(aid);

            return new RouteEntry(aid, powerState, location, allowed);
        }

        public RouteEntry[] newArray(int size) {
            return new RouteEntry[size];
        }
    };

    public byte[] getAid() {
        return mAid;
    }

    public int getPowerState() {
        return mPowerState;
    }

    public int getLocation() {
        return mLocation;
    }

    public boolean isAllowed() {
        return mAllowed;
    }

}
