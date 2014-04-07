/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.internal.telephony.Phone;

public class NetworkModeChanger extends ModeChanger {

    private Resources mResources;
    private int mDefaultMode;
    private ConnectivityManager mCM;
    private TelephonyManager mTM;

    public NetworkModeChanger(Context context) {
        super(context);
        mResources = context.getResources();
    }

    public void setServices(ConnectivityManager cm, TelephonyManager tm) {
        mCM = cm;
        mTM = tm;
        mDefaultMode = getMode();
    }

    private String getNetworkType(int state, Resources r) {
        switch (state) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
                return r.getString(R.string.network_4G);
            case Phone.NT_MODE_GSM_UMTS:
                return r.getString(R.string.network_3G_auto);
            case Phone.NT_MODE_WCDMA_ONLY:
                return r.getString(R.string.network_3G_only);
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_GSM_ONLY:
                return r.getString(R.string.network_2G);
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_WCDMA_PREF:
                return r.getString(R.string.network_3G);
        }
        return r.getString(R.string.quick_settings_network_unknown);
    }

    private void setMode(int network) {
        if (!isSupported()) return;
        if (isShowToast()) {
            Toast.makeText(mContext,
                  mResources.getString(R.string.battery_saver_change) + " "
                  + getNetworkType(network, mResources), Toast.LENGTH_SHORT).show();
        }
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GLOBAL);
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mTM.toggleMobileNetwork(Phone.NT_MODE_CDMA_NO_EVDO);
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_EVDO_NO_CDMA);
                break;
            case Phone.NT_MODE_CDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_CDMA);
                break;
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                mTM.toggleMobileNetwork(Phone.NT_MODE_LTE_CDMA_AND_EVDO);
                break;
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA);
                break;
            case Phone.NT_MODE_GSM_UMTS:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GSM_UMTS);
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mTM.toggleMobileNetwork(Phone.NT_MODE_WCDMA_ONLY);
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GSM_ONLY);
                break;
            case Phone.NT_MODE_WCDMA_PREF:
                mTM.toggleMobileNetwork(Phone.NT_MODE_WCDMA_PREF);
                break;
            case Phone.NT_MODE_LTE_GSM_WCDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_LTE_GSM_WCDMA);
                break;
            case Phone.NT_MODE_LTE_ONLY:
                mTM.toggleMobileNetwork(Phone.NT_MODE_LTE_ONLY);
                break;
            case Phone.NT_MODE_LTE_WCDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_LTE_WCDMA);
                break;
        }
    }

    @Override
    public boolean isStateEnabled() {
        return true;
    }

    @Override
    public boolean isSupported() {
        boolean isSupport = (mCM != null) ? mCM.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) : false;
        return isModeEnabled() && isSupport;
    }

    @Override
    public int getMode() {
        if (!isSupported()) return 0;
        return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, Phone.PREFERRED_NT_MODE);
    }

    @Override
    public void stateNormal() {
    }

    @Override
    public void stateSaving() {
    }

    @Override
    public boolean checkModes() {
        if (isDelayChanges()) {
            // download/upload progress detected, delay changing mode
            changeModes(getNextMode(), true, false);
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " delayed network changing because traffic full ");
            }
            return false;
        }
        return true;
    }

    @Override
    public void setModes() {
        setMode(getNextMode());
        super.setModes();
    }

    @Override
    public boolean restoreState() {
        if (isSupported() && (getMode() != mDefaultMode)) {
            setMode(mDefaultMode);
            return true;
        }
        return false;
    }
}
