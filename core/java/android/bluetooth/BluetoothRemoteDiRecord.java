/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.bluetooth;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a remote Bluetooth device identification record
 *{@link BluetoothRemoteDiRecord} If remote device supports DI profile,
 * then you can query for the bluetooth remote device information with the respective
 * the particular remote device, Such as the vendorId, vendorIdSource, productId,
 * productVersion & specificationId, which are important in order to make best use of
 * the features on the device identified. Eg. A cellular phone may use this information
 * to identify associated accessories or download Java apps from another device
 * that advertises its availability.
 *
 * <p>To get a {@link BluetoothRemoteDiRecord}, use
 * {@link BluetoothDevice#getRemoteDiRecord()
 * BluetoothDevice.getRemoteDiRecord()}
 *
 * Requires the {@link android.Manifest.permission#BLUETOOTH} permission.
 */

/** @hide */
public final class BluetoothRemoteDiRecord implements Parcelable {
    private int       mVendorId;
    private int       mVendorIdSource;
    private int       mProductId;
    private int       mProductVersion;
    private int       mSpecificationId;
    private Object mObject = new Object();

    public BluetoothRemoteDiRecord(int vendorId,int vendorIdSource, int productId,
            int productVersion, int specificationId) {
        mVendorId= vendorId;
        mVendorIdSource= vendorIdSource;
        mProductId= productId;
        mProductVersion= productVersion;
        mSpecificationId= specificationId;
    }

    /**
    * @return the vendorId
    */
    public int getVendorId() {
        synchronized (mObject) {
            return mVendorId;
        }
    }

    /**
    * @return the vendorIdSource
    */
    public int getVendorIdSource() {
        synchronized (mObject) {
            return mVendorIdSource;
        }
    }

    /**
    * @return the productId
    */
    public int getProductId() {
        synchronized (mObject) {
            return mProductId;
        }
    }

    /**
    * @return the productVersion
    */
    public int getProductVersion() {
        synchronized (mObject) {
            return mProductVersion;
        }
    }

    /**
    * @return the specificationId
    */
    public int getSpecificationId() {
        synchronized (mObject) {
            return mSpecificationId;
        }
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mVendorId);
        out.writeInt(mVendorIdSource);
        out.writeInt(mProductId);
        out.writeInt(mProductVersion);
        out.writeInt(mSpecificationId);
    }

    public static final Parcelable.Creator<BluetoothRemoteDiRecord> CREATOR =
        new Parcelable.Creator<BluetoothRemoteDiRecord>() {

        @Override
        public BluetoothRemoteDiRecord createFromParcel(Parcel in) {

            return new BluetoothRemoteDiRecord(in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt());
        }

        @Override
        public BluetoothRemoteDiRecord[] newArray(int size) {
            return new BluetoothRemoteDiRecord[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

}

