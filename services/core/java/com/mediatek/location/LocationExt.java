/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.location;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;

import com.android.server.location.GpsLocationProvider;

public class LocationExt {
    private static final String TAG = "MtkLocationExt";

    private static final boolean DEBUG = true; //Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VERBOSE = true; //Log.isLoggable(TAG, Log.VERBOSE);

    // these need to match GpsLocationFlags enum in gps.h
    //private static final int LOCATION_INVALID = 0;
    private static final int LOCATION_HAS_LAT_LONG = 1;
    //private static final int LOCATION_HAS_ALTITUDE = 2;
    //private static final int LOCATION_HAS_SPEED = 4;
    //private static final int LOCATION_HAS_BEARING = 8;
    //private static final int LOCATION_HAS_ACCURACY = 16;

    private static LocationExt sSingleton;

    private final GpsLocationProvider mGpsProvider;
    private final Context mContext;
    private final Handler mGpsHandler;
    private final ConnectivityManager mConnMgr;

    /*mGpsTimeSyncFlag : true: need to check the time sync, false: no need to check the time sync*/
    private boolean mGpsTimeSyncFlag = true;
    /*isEmergencyCallDialed: [true] IMS emergency call is dialed,
    [false] IMS emergency call is ended*/
    private boolean mIsEmergencyCallDialed;
    private int mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;
    private GnssSvStatusHolder mGnssSvStatusHolder = new GnssSvStatusHolder();

    //============================================================================================
    // APIs for GpsLocationProvider

    public static synchronized LocationExt getInstance(
            GpsLocationProvider gpsProvider,
            Context context,
            Handler gpsHandler,
            ConnectivityManager connMgr) {
        if (null == sSingleton && null != gpsProvider) {
            sSingleton = new LocationExt(gpsProvider, context, gpsHandler, connMgr);
        }
        return sSingleton;
    }

    public static boolean isEnabled() {
        return (null != sSingleton);
    }

    // Return true to allow sending SuplInit to the native
    public static boolean checkWapSuplInit(Intent intent) {
        if (!isEnabled()) return true;

        boolean ret = sSingleton.isWapPushLegal(intent);
        if (DEBUG) Log.d(TAG, "[agps] WARNING: checkWapSuplInit ret=" + ret);
        return ret;
    }

    public static void deleteAidingData(Bundle extras, int flags) {
        if (!isEnabled()) return;
        if (DEBUG) Log.d(TAG, "deleteAidingData extras:" + extras + "flags:" + flags);
    }

    public static boolean setGpsTimeSyncFlag(boolean flag) {
        if (!isEnabled()) return false;
        sSingleton.mGpsTimeSyncFlag = flag;
        if (DEBUG) Log.d(TAG, "setGpsTimeSyncFlag: " + flag);
        return flag;
    }

    public static void startNavigating(boolean singleShot) {
        setGpsTimeSyncFlag(true);
    }

    public static void doSystemTimeSyncByGps(int flags, long timestamp) {
        if (!isEnabled()) return;
        if (sSingleton.mGpsTimeSyncFlag &&
                (flags & LOCATION_HAS_LAT_LONG) == LOCATION_HAS_LAT_LONG) {
            if (sSingleton.getAutoGpsState()) {
                sSingleton.mGpsTimeSyncFlag = false;
                Log.d(TAG, "GPS time sync is enabled");
                Log.d(TAG, " ########## Auto-sync time with GPS: timestamp = " + timestamp
                        + " ########## ");
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(timestamp);
                long when = c.getTimeInMillis();
                if (when / 1000 < Integer.MAX_VALUE) {
                    SystemClock.setCurrentTimeMillis(when);
                }
            } else {
                Log.d(TAG, "Auto-sync time with GPS is disabled by user settings!");
                Log.d(TAG, "GPS time sync is disabled");
            }
        }
    }

    public static GnssSvStatusHolder getGnssSvStatusHolder() {
        if (!isEnabled()) return null;
        return sSingleton.mGnssSvStatusHolder;
    }

    public static int getRouteNetworkType() {
        if (!isEnabled()) return ConnectivityManager.TYPE_MOBILE_SUPL;
        return sSingleton.mRouteNetworkType;
    }

    public static int doStartUsingNetwork(ConnectivityManager connMgr, int networkType,
            String feature) {
        if (!isEnabled()) {
            return -1;
        } else {
            return sSingleton.doMtkStartUsingNetwork();
        }
    }

    //============================================================================================
    // Utilties

    public static boolean isFileExists(String path) {
        File f = new File(path);
        return f.exists();
    }

    public static boolean isESUPL() {
        return isFileExists("/data/agps_supl/isESUPL");
    }

    //============================================================================================
    // Internal implementation

    private LocationExt(GpsLocationProvider gpsProvider, Context context,
            Handler gpsHandler, ConnectivityManager connMgr) {
        mGpsProvider = gpsProvider;
        mContext = context;
        mGpsHandler = gpsHandler;
        mConnMgr = connMgr;
        listenForBroadcasts();
    }

    private void listenForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.location.agps.EMERGENCY_CALL");
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, null, mGpsHandler);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (DEBUG) Log.d(TAG, "receive broadcast intent, action: " + action);
            if ("android.location.agps.EMERGENCY_CALL".equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    int state = bundle.getInt("EM_Call_State");
                    if (1 == state) {
                        if (DEBUG) Log.d(TAG, "E911 dialed");
                        mIsEmergencyCallDialed = true;
                    } else {
                        if (DEBUG) Log.d(TAG, "E911 ended");
                        mIsEmergencyCallDialed = false;
                    }
                } else {
                    Log.e(TAG, "E911 null bundle");
                }
            }
        }
    };

    private boolean isWapPushLegal(Intent intent) {
        try {
            String type = intent.getType();
            if (type == null || !type.equals("application/vnd.omaloc-supl-init")) {
                Log.e(TAG, "[agps] ERR: content type is [" + type
                        + "], but we expect [application/vnd.omaloc-supl-init]");
                return false;
            }
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                Log.e(TAG, "[agps] ERR: wspBundle is null");
                return false;
            }
            HashMap<String, String> wspHeaders = (HashMap<String, String>) bundle.get("wspHeaders");
            if (wspHeaders == null) {
                Log.e(TAG, "[agps] ERR: wspHeader is null");
                return false;
            }
            String appId = wspHeaders.get("X-Wap-Application-Id");
            if (appId == null) {
                Log.e(TAG, "[agps] ERR: appId(X-Wap-Application-Id) is null");
                return false;
            }
            if (!appId.equals("x-oma-application:ulp.ua")) {
                Log.e(TAG, "[agps] ERR: appId is [" + appId
                        + "], but we expect [x-oma-application:ulp.ua]");
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean getAutoGpsState() {
        /*try {
            return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.AUTO_TIME_GPS) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }*/
        return false;
    }

    public class GnssSvStatusHolder {
        // preallocated arrays, to avoid memory allocation in reportStatus()
        public static final int MAX_GNSS_SVS = 256;
        public int mGnssSvs[] = new int[MAX_GNSS_SVS];
        public float mGnssSnrs[] = new float[MAX_GNSS_SVS];
        public float mGnssSvElevations[] = new float[MAX_GNSS_SVS];
        public float mGnssSvAzimuths[] = new float[MAX_GNSS_SVS];
        public boolean mGnssSvEphemeris[] = new boolean[MAX_GNSS_SVS];
        public boolean mGnssSvAlmanac[] = new boolean[MAX_GNSS_SVS];
        public boolean mGnssSvInFix[] = new boolean[MAX_GNSS_SVS];

        public int reportGnssSvStatusStep2(int svCount) {
            if (VERBOSE) {
                Log.v(TAG, "GNSS SV count: " + svCount);
                for (int i = 0; i < svCount; i++) {
                    Log.v(TAG, "sv: " + mGnssSvs[i] +
                            " snr: " + mGnssSnrs[i] / 10 +
                            " elev: " + mGnssSvElevations[i] +
                            " azimuth: " + mGnssSvAzimuths[i] +
                            ((mGnssSvEphemeris[i]) ? " E" : " ") +
                            ((mGnssSvAlmanac[i]) ? " A" : " ") +
                            ((mGnssSvInFix[i]) ? " U" : " "));
                }
            }
            int svFixCount = 0;
            for (boolean value : mGnssSvInFix) {
                if (value) {
                    svFixCount++;
                }
            }
            return svFixCount;
        }

        public boolean reportGnssSvStatusStep3(boolean navigating, int gpsStatus,
                long lastFixTime, long recentFixTimeout) {
            if (navigating && gpsStatus == LocationProvider.AVAILABLE && lastFixTime > 0 &&
                System.currentTimeMillis() - lastFixTime > recentFixTimeout) {
                // send an intent to notify that the GPS is no longer receiving fixes.
                Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
                intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, false);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                return true;
            }
            return false;
        }
    }

    private boolean isEmergencyCallDialed() {
        return (isFileExists("/data/agps_supl/isEmergencyCallDialed") ||
                mIsEmergencyCallDialed);
    }

    private boolean hasIccCard() {
        TelephonyManager tpMgr = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tpMgr != null) {
            return tpMgr.hasIccCard();
        }
        return false;
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    private int doMtkStartUsingNetwork() {
        int networkType = ConnectivityManager.TYPE_MOBILE;
        String feature = Phone.FEATURE_ENABLE_SUPL;
        mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;

        TelephonyManager phone = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);

        //IR92 requirements for emergency location
        int phoneNetwokrType = phone.getNetworkType();

        Log.d(TAG, "[agps] WARNING: GpsLocationProvider  phoneNetwokrType=[" +
            phoneNetwokrType + "] isESUPL=[" + isESUPL() + "] isEmergencyCallDialed=["
            + isEmergencyCallDialed() + "]");
        if (phoneNetwokrType == TelephonyManager.NETWORK_TYPE_LTE && isESUPL()) {
            if (isEmergencyCallDialed()) {
                networkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
                feature = Phone.FEATURE_ENABLE_EMERGENCY;
                mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
            } else {
                networkType = ConnectivityManager.TYPE_MOBILE_IMS;
                feature = Phone.FEATURE_ENABLE_IMS;
                mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_IMS;
            }
        }

        if ((networkType == ConnectivityManager.TYPE_MOBILE && hasIccCard() == false)
            || isAirplaneModeOn()) {
            Log.d(TAG, "[agps] APN_REQUEST_FAILED: hasIccCard=" +
                    hasIccCard() + " isAirplaneModeOn="
                    + isAirplaneModeOn());
            return PhoneConstants.APN_REQUEST_FAILED;
        }

        return mConnMgr.startUsingNetworkFeature(networkType, feature);
    }
}
