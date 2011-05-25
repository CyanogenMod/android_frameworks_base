/*
 * Copyright (C) 2007 The Android Open Source Project
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
package com.android.internal.telephony;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.security.Md5MessageDigest;
import android.security.MessageDigest;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class PhoneSubInfo extends IPhoneSubInfo.Stub {
    static final String LOG_TAG = "PHONE";
    private Phone mPhone;
    private Context mContext;
    private static final String READ_PHONE_STATE =
        android.Manifest.permission.READ_PHONE_STATE;
    private static final String CALL_PRIVILEGED =
        // TODO Add core/res/AndriodManifest.xml#READ_PRIVILEGED_PHONE_STATE
        android.Manifest.permission.CALL_PRIVILEGED;

    public PhoneSubInfo(Phone phone) {
        mPhone = phone;
        mContext = phone.getContext();
    }

    public void dispose() {
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        Log.d(LOG_TAG, "PhoneSubInfo finalized");
    }

    /**
     * Retrieves the unique device ID, e.g., IMEI for GSM phones and MEID for CDMA phones.
     */
    public String getDeviceId() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return mPhone.getDeviceId();
        case PackageManager.PERMISSION_SPOOFED:
            return createNumericSpoof(mPhone.getDeviceId().length(), 6, 3, null);
        default:
            return "";
        }
    }

    /**
     * Retrieves the software version number for the device, e.g., IMEI/SV
     * for GSM phones.
     */
    public String getDeviceSvn() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return mPhone.getDeviceSvn();
        case PackageManager.PERMISSION_SPOOFED:
            return createNumericSpoof(mPhone.getDeviceSvn().length(), 5, 2, null);
        default:
            return "";
        }
    }

    /**
     * Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
     */
    public String getSubscriberId() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return mPhone.getSubscriberId();
        case PackageManager.PERMISSION_SPOOFED:
            return createNumericSpoof(mPhone.getSubscriberId().length(), 0, 3, null);
        default:
            return "";
        }
    }

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    public String getIccSerialNumber() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return mPhone.getIccSerialNumber();
        case PackageManager.PERMISSION_SPOOFED:
            String real = mPhone.getIccSerialNumber();
            return createNumericSpoof(real.length(), 2, 5, null);
        default:
            return "";
        }
    }

    private String createNumericSpoof(final int len, int begin, int step, String prefix) {
        byte[] data = getMD5Sum();
        StringBuilder spoof = new StringBuilder();
        if (prefix != null) {
            spoof.append(prefix);
        }
        int j = begin;
        while (spoof.length() < len) {
            spoof.append(0xff & data[j]);
            j += step;
            if (j >= data.length) {
                j -= data.length;
            }
        }
        spoof.setLength(len);
        return spoof.toString();
    }

    /**
     * Retrieves the phone number string for line 1.
     */
    public String getLine1Number() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return mPhone.getLine1Number();
        case PackageManager.PERMISSION_SPOOFED:
            byte[] data = getMD5Sum();
            StringBuilder spoof = new StringBuilder("+11");
            for(int i = 0; i < 4; i++) {
                spoof.append(0x0f & data[i+4]);
                spoof.append(data[i] >> 8);
            }
            return spoof.toString();
         default:
            return "";
        }
    }

    /**
     * Retrieves the alpha identifier for line 1.
     */
    public String getLine1AlphaTag() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return (String) mPhone.getLine1AlphaTag();
        case PackageManager.PERMISSION_SPOOFED:
            return "Line1";
        default:
            return "";
        }
    }

    /**
     * Retrieves the voice mail number.
     */
    public String getVoiceMailNumber() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
            String number = PhoneNumberUtils.extractNetworkPortion(mPhone.getVoiceMailNumber());
            Log.d(LOG_TAG, "VM: PhoneSubInfo.getVoiceMailNUmber: "); // + number);
            return number;
        case PackageManager.PERMISSION_SPOOFED:
            byte[] data = getMD5Sum();
            StringBuilder spoof = new StringBuilder("+11");
            for(int i = 0; i < 4; i++) {
                spoof.append(0x0f & data[i]);
                spoof.append(data[i] >> 8);
            }
            return spoof.toString();
        default:
            return "";
        }
    }

    /**
     * Retrieves the complete voice mail number.
     *
     * @hide
     */
    public String getCompleteVoiceMailNumber() {
        mContext.enforceCallingOrSelfPermission(CALL_PRIVILEGED,
                "Requires CALL_PRIVILEGED");
        String number = mPhone.getVoiceMailNumber();
        Log.d(LOG_TAG, "VM: PhoneSubInfo.getCompleteVoiceMailNUmber: "); // + number);
        return number;
    }

    /**
     * Retrieves the alpha identifier associated with the voice mail number.
     */
    public String getVoiceMailAlphaTag() {
        int res = mContext.pffEnforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        switch (res) {
        case PackageManager.PERMISSION_GRANTED:
            return (String) mPhone.getVoiceMailAlphaTag();
        case PackageManager.PERMISSION_SPOOFED:
            return "Voicemail";
        default:
            return "";
        }
    }

    private byte[] getMD5Sum() {
        byte[] data = null;
        try {
            int uid = Binder.getCallingUid();
            String name = mContext.getPackageManager().getNameForUid(uid);
            MessageDigest md = MessageDigest.getInstance("MD5");
            data = md.digest(name.getBytes());
        } catch (NoSuchAlgorithmException e) {
        }
        return data;
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump PhoneSubInfo from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("Phone Subscriber Info:");
        pw.println("  Phone Type = " + mPhone.getPhoneName());
        pw.println("  Device ID = " + mPhone.getDeviceId());
    }

}
