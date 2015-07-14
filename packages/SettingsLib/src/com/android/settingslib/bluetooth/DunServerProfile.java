/*
*Copyright (c) 2013, 2015, The Linux Foundation. All rights reserved.
*
*Redistribution and use in source and binary forms, with or without
*modification, are permitted provided that the following conditions are
*met:
*    * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*    * Redistributions in binary form must reproduce the above
*     copyright notice, this list of conditions and the following
*      disclaimer in the documentation and/or other materials provided
*      with the distribution.
*    * Neither the name of The Linux Foundation nor the names of its
*      contributors may be used to endorse or promote products derived
*      from this software without specific prior written permission.
*
*THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
*WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
*MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
*ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
*BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
*CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
*SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
*BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
*WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
*OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
*IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDun;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.List;

import com.android.settingslib.R;

/**
 * DunServerProfile handles Bluetooth DUN server profile.
 */
final class DunServerProfile implements LocalBluetoothProfile {
    private static final String TAG = "DunServerProfile";
    private static boolean V = true;

    private BluetoothDun mService;
    private boolean mIsProfileReady;

    static final String NAME = "DUN Server";

    // Order of this profile in device profiles list
    private static final int ORDINAL = 11;

    // These callbacks run on the main thread.
    private final class DunServiceListener
            implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (V) Log.d(TAG,"Bluetooth service connected");
            mService = (BluetoothDun) proxy;
            mIsProfileReady = true;
        }

        public void onServiceDisconnected(int profile) {
            if (V) Log.d(TAG,"Bluetooth service disconnected");
            mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return mIsProfileReady;
    }

    DunServerProfile(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.getProfileProxy(context, new DunServiceListener(),
                BluetoothProfile.DUN);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        if (mService == null) return false;
        return mService.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (mService == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mService.getConnectionState(device);
    }

    public boolean isPreferred(BluetoothDevice device) {
        return true;
    }

    public int getPreferred(BluetoothDevice device) {
        return -1;
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
        // ignore: isPreferred is always true for DUN
    }

    public String toString() {
        return NAME;
    }

    public int getOrdinal() {
        return ORDINAL;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_dun;
    }

    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return R.string.bluetooth_dun_profile_summary_use_for;

            case BluetoothProfile.STATE_CONNECTED:
                return R.string.bluetooth_dun_profile_summary_connected;
            default:
                return Utils.getConnectionStateSummary(state);
        }
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_network_pan;
    }

    protected void finalize() {
        if (V) Log.d(TAG, "finalize()");
        if (mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy
                                    (BluetoothProfile.DUN, mService);
                mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up DUN proxy", t);
            }
        }
    }
}
