/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011,2012, The Linux Foundation. All rights reserved.
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

package com.android.server.location;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.location.Criteria;
import android.location.IGpsStatusListener;
import android.location.IGpsStatusProvider;
import android.location.ILocationManager;
import android.location.INetInitiatedListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.SntpClient;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.util.NtpTrustedTime;
import com.android.internal.app.IBatteryStats;
import com.android.internal.location.GpsNetInitiatedHandler;
import com.android.internal.location.ProviderProperties;
import com.android.internal.location.ProviderRequest;
import com.android.internal.location.GpsNetInitiatedHandler.GpsNiNotification;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Collection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Inet6Address;

/**
 * A GPS implementation of LocationProvider used by LocationManager.
 *
 * {@hide}
 */
public class GpsLocationProvider implements LocationProviderInterface {

    private static final String TAG = "GpsLocationProvider";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final ProviderProperties PROPERTIES = new ProviderProperties(
            true, true, false, false, true, true, true,
            Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);

    // these need to match GpsPositionMode enum in gps.h
    private static final int GPS_POSITION_MODE_STANDALONE = 0;
    private static final int GPS_POSITION_MODE_MS_BASED = 1;
    private static final int GPS_POSITION_MODE_MS_ASSISTED = 2;

    // these need to match GpsPositionRecurrence enum in gps.h
    private static final int GPS_POSITION_RECURRENCE_PERIODIC = 0;
    private static final int GPS_POSITION_RECURRENCE_SINGLE = 1;

    // these need to match GpsStatusValue defines in gps.h
    private static final int GPS_STATUS_NONE = 0;
    private static final int GPS_STATUS_SESSION_BEGIN = 1;
    private static final int GPS_STATUS_SESSION_END = 2;
    private static final int GPS_STATUS_ENGINE_ON = 3;
    private static final int GPS_STATUS_ENGINE_OFF = 4;

    // these need to match GpsApgsStatusValue defines in gps.h
    /** AGPS status event values. */
    private static final int GPS_REQUEST_AGPS_DATA_CONN = 1;
    private static final int GPS_RELEASE_AGPS_DATA_CONN = 2;
    private static final int GPS_AGPS_DATA_CONNECTED = 3;
    private static final int GPS_AGPS_DATA_CONN_DONE = 4;
    private static final int GPS_AGPS_DATA_CONN_FAILED = 5;

    // these need to match GpsLocationFlags enum in gps.h
    private static final int LOCATION_INVALID = 0;
    private static final int LOCATION_HAS_LAT_LONG = 1;
    private static final int LOCATION_HAS_ALTITUDE = 2;
    private static final int LOCATION_HAS_SPEED = 4;
    private static final int LOCATION_HAS_BEARING = 8;
    private static final int LOCATION_HAS_ACCURACY = 16;

// IMPORTANT - the GPS_DELETE_* symbols here must match constants in gps.h
    private static final int GPS_DELETE_EPHEMERIS = 0x00000001;
    private static final int GPS_DELETE_ALMANAC = 0x00000002;
    private static final int GPS_DELETE_POSITION = 0x00000004;
    private static final int GPS_DELETE_TIME = 0x00000008;
    private static final int GPS_DELETE_IONO = 0x00000010;
    private static final int GPS_DELETE_UTC = 0x00000020;
    private static final int GPS_DELETE_HEALTH = 0x00000040;
    private static final int GPS_DELETE_SVDIR = 0x00000080;
    private static final int GPS_DELETE_SVSTEER = 0x00000100;
    private static final int GPS_DELETE_SADATA = 0x00000200;
    private static final int GPS_DELETE_RTI = 0x00000400;
    private static final int GPS_DELETE_CELLDB_INFO = 0x00000800;
    private static final int GPS_DELETE_ALMANAC_CORR = 0x00001000;
    private static final int GPS_DELETE_FREQ_BIAS_EST = 0x00002000;
    private static final int GPS_DELETE_EPHEMERIS_GLO = 0x00004000;
    private static final int GPS_DELETE_ALMANAC_GLO = 0x00008000;
    private static final int GPS_DELETE_SVDIR_GLO = 0x00010000;
    private static final int GPS_DELETE_SVSTEER_GLO = 0x00020000;
    private static final int GPS_DELETE_ALMANAC_CORR_GLO = 0x00040000;
    private static final int GPS_DELETE_TIME_GPS = 0x00080000;
    private static final int GPS_DELETE_TIME_GLO = 0x00100000;
    private static final int GPS_DELETE_ALL = 0xFFFFFFFF;

    // The GPS_CAPABILITY_* flags must match the values in gps.h
    private static final int GPS_CAPABILITY_SCHEDULING = 0x0000001;
    private static final int GPS_CAPABILITY_MSB = 0x0000002;
    private static final int GPS_CAPABILITY_MSA = 0x0000004;
    private static final int GPS_CAPABILITY_SINGLE_SHOT = 0x0000008;
    private static final int GPS_CAPABILITY_ON_DEMAND_TIME = 0x0000010;

    // Handler messages
    private static final int CHECK_LOCATION = 1;
    private static final int ENABLE = 2;
    private static final int SET_REQUEST = 3;
    private static final int UPDATE_NETWORK_STATE = 4;
    private static final int INJECT_NTP_TIME = 5;
    private static final int DOWNLOAD_XTRA_DATA = 6;
    private static final int UPDATE_LOCATION = 7;
    private static final int ADD_LISTENER = 8;
    private static final int REMOVE_LISTENER = 9;
    private static final int INJECT_NTP_TIME_FINISHED = 10;
    private static final int DOWNLOAD_XTRA_DATA_FINISHED = 11;
    private static final int REPORT_AGPS_STATUS = 12;

    // Request setid
    private static final int AGPS_RIL_REQUEST_SETID_IMSI = 1;
    private static final int AGPS_RIL_REQUEST_SETID_MSISDN = 2;

    // Request ref location
    private static final int AGPS_RIL_REQUEST_REFLOC_CELLID = 1;
    private static final int AGPS_RIL_REQUEST_REFLOC_MAC = 2;

    // ref. location info
    private static final int AGPS_REF_LOCATION_TYPE_GSM_CELLID = 1;
    private static final int AGPS_REF_LOCATION_TYPE_UMTS_CELLID = 2;
    private static final int AGPS_REG_LOCATION_TYPE_MAC        = 3;

    // set id info
    private static final int AGPS_SETID_TYPE_NONE = 0;
    private static final int AGPS_SETID_TYPE_IMSI = 1;
    private static final int AGPS_SETID_TYPE_MSISDN = 2;

    private static final String PROPERTIES_FILE = "/etc/gps.conf";

    /** simpler wrapper for ProviderRequest + Worksource */
    private static class GpsRequest {
        public ProviderRequest request;
        public WorkSource source;
        public GpsRequest(ProviderRequest request, WorkSource source) {
            this.request = request;
            this.source = source;
        }
    }

    private Object mLock = new Object();

    private int mLocationFlags = LOCATION_INVALID;

    // current status
    private int mStatus = LocationProvider.TEMPORARILY_UNAVAILABLE;

    // time for last status update
    private long mStatusUpdateTime = SystemClock.elapsedRealtime();

    // turn off GPS fix icon if we haven't received a fix in 10 seconds
    private static final long RECENT_FIX_TIMEOUT = 10 * 1000;

    // stop trying if we do not receive a fix within 60 seconds
    private static final int NO_FIX_TIMEOUT = 60 * 1000;

    // if the fix interval is below this we leave GPS on,
    // if above then we cycle the GPS driver.
    // Typical hot TTTF is ~5 seconds, so 10 seconds seems sane.
    private static final int GPS_POLLING_THRESHOLD_INTERVAL = 10 * 1000;

    // how often to request NTP time, in milliseconds
    // current setting 24 hours
    private static final long NTP_INTERVAL = 24*60*60*1000;
    // how long to wait if we have a network error in NTP or XTRA downloading
    // current setting - 5 minutes
    private static final long RETRY_INTERVAL = 5*60*1000;

    // true if we are enabled, protected by this
    private boolean mEnabled;

    // true if we have network connectivity
    private boolean mNetworkAvailable;

    // states for injecting ntp and downloading xtra data
    private static final int STATE_PENDING_NETWORK = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_IDLE = 2;

    // flags to trigger NTP or XTRA data download when network becomes available
    // initialized to true so we do NTP and XTRA when the network comes up after booting
    private int mInjectNtpTimePending = STATE_PENDING_NETWORK;
    private int mDownloadXtraDataPending = STATE_PENDING_NETWORK;

    // set to true if the GPS engine does not do on-demand NTP time requests
    private boolean mPeriodicTimeInjection;

    // true if GPS is navigating
    private boolean mNavigating;

    // true if GPS engine is on
    private boolean mEngineOn;

    // requested frequency of fixes, in milliseconds
    private int mFixInterval = 1000;

    // true if we started navigation
    private boolean mStarted;

    // capabilities of the GPS engine
    private int mEngineCapabilities;

    // true if XTRA is supported
    private boolean mSupportsXtra;

    // for calculating time to first fix
    private long mFixRequestTime = 0;
    // time to first fix for most recent session
    private int mTimeToFirstFix = 0;
    // time we received our last fix
    private long mLastFixTime;

    private int mPositionMode;

    // properties loaded from PROPERTIES_FILE
    private Properties mProperties;
    private String mSuplServerHost;
    private int mSuplServerPort;
    private String mC2KServerHost;
    private int mC2KServerPort;

    private final Context mContext;
    private final NtpTrustedTime mNtpTime;
    private final ILocationManager mILocationManager;
    private Location mLocation = new Location(LocationManager.GPS_PROVIDER);
    private Bundle mLocationExtras = new Bundle();
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();

    // Handler for processing events
    private Handler mHandler;

    private final ConnectivityManager mConnMgr;
    private final GpsNetInitiatedHandler mNIHandler;

    // Wakelocks
    private final static String WAKELOCK_KEY = "GpsLocationProvider";
    private final PowerManager.WakeLock mWakeLock;

    // Alarms
    private final static String ALARM_WAKEUP = "com.android.internal.location.ALARM_WAKEUP";
    private final static String ALARM_TIMEOUT = "com.android.internal.location.ALARM_TIMEOUT";
    private final AlarmManager mAlarmManager;
    private final PendingIntent mWakeupIntent;
    private final PendingIntent mTimeoutIntent;

    private final IBatteryStats mBatteryStats;

    // only modified on handler thread
    private int[] mClientUids = new int[0];
    private WifiState mWifiState = null;

    private final IGpsStatusProvider mGpsStatusProvider = new IGpsStatusProvider.Stub() {
        @Override
        public void addGpsStatusListener(IGpsStatusListener listener) throws RemoteException {
            if (listener == null) {
                throw new NullPointerException("listener is null in addGpsStatusListener");
            }

            synchronized (mListeners) {
                IBinder binder = listener.asBinder();
                int size = mListeners.size();
                for (int i = 0; i < size; i++) {
                    Listener test = mListeners.get(i);
                    if (binder.equals(test.mListener.asBinder())) {
                        // listener already added
                        return;
                    }
                }

                Listener l = new Listener(listener);
                binder.linkToDeath(l, 0);
                mListeners.add(l);
            }
        }

        @Override
        public void removeGpsStatusListener(IGpsStatusListener listener) {
            if (listener == null) {
                throw new NullPointerException("listener is null in addGpsStatusListener");
            }

            synchronized (mListeners) {
                IBinder binder = listener.asBinder();
                Listener l = null;
                int size = mListeners.size();
                for (int i = 0; i < size && l == null; i++) {
                    Listener test = mListeners.get(i);
                    if (binder.equals(test.mListener.asBinder())) {
                        l = test;
                    }
                }

                if (l != null) {
                    mListeners.remove(l);
                    binder.unlinkToDeath(l, 0);
                }
            }
        }
    };

    public IGpsStatusProvider getGpsStatusProvider() {
        return mGpsStatusProvider;
    }

    private final BroadcastReceiver mBroadcastReciever = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(ALARM_WAKEUP)) {
                if (DEBUG) Log.d(TAG, "ALARM_WAKEUP");
                startNavigating();
            } else if (action.equals(ALARM_TIMEOUT)) {
                if (DEBUG) Log.d(TAG, "ALARM_TIMEOUT");
                hibernate();
            } else if (action.equals(Intents.DATA_SMS_RECEIVED_ACTION)) {
                checkSmsSuplInit(intent);
            } else if (action.equals(Intents.WAP_PUSH_RECEIVED_ACTION)) {
                checkWapSuplInit(intent);
             } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                 int networkState;
                 if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                     networkState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                 } else {
                     networkState = LocationProvider.AVAILABLE;
                 }

                 // retrieve NetworkInfo result for this UID
                 NetworkInfo info =
                         intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                 ConnectivityManager connManager = (ConnectivityManager)
                         mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                 info = connManager.getNetworkInfo(info.getType());

                 updateNetworkState(networkState, info);
             }
        }
    };

    private void checkSmsSuplInit(Intent intent) {
        SmsMessage[] messages = Intents.getMessagesFromIntent(intent);
        for (int i=0; i <messages.length; i++) {
            byte[] supl_init = messages[i].getUserData();
            native_agps_ni_message(supl_init,supl_init.length);
        }
    }

    private void checkWapSuplInit(Intent intent) {
        byte[] supl_init = (byte[]) intent.getExtra("data");
        native_agps_ni_message(supl_init,supl_init.length);
    }

    public static boolean isSupported() {
        return native_is_supported();
    }

    public GpsLocationProvider(Context context, ILocationManager ilocationManager) {
        mContext = context;
        mNtpTime = NtpTrustedTime.getInstance(context);
        mILocationManager = ilocationManager;
        mNIHandler = new GpsNetInitiatedHandler(context);

        mLocation.setExtras(mLocationExtras);

        // Create a wake lock
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);
        mWakeLock.setReferenceCounted(true);

        mWifiState = new WifiState();

        mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        mWakeupIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ALARM_WAKEUP), 0);
        mTimeoutIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ALARM_TIMEOUT), 0);

        mConnMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Battery statistics service to be notified when GPS turns on or off
        mBatteryStats = IBatteryStats.Stub.asInterface(ServiceManager.getService("batteryinfo"));

        mProperties = new Properties();
        try {
            File file = new File(PROPERTIES_FILE);
            FileInputStream stream = new FileInputStream(file);
            mProperties.load(stream);
            stream.close();

            mSuplServerHost = mProperties.getProperty("SUPL_HOST");
            String portString = mProperties.getProperty("SUPL_PORT");
            if (mSuplServerHost != null && portString != null) {
                try {
                    mSuplServerPort = Integer.parseInt(portString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "unable to parse SUPL_PORT: " + portString);
                }
            }

            mC2KServerHost = mProperties.getProperty("C2K_HOST");
            portString = mProperties.getProperty("C2K_PORT");
            if (mC2KServerHost != null && portString != null) {
                try {
                    mC2KServerPort = Integer.parseInt(portString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "unable to parse C2K_PORT: " + portString);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not open GPS configuration file " + PROPERTIES_FILE);
        }

        // construct handler, listen for events
        mHandler = new ProviderHandler();
        listenForBroadcasts();

        // also listen for PASSIVE_PROVIDER updates
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                LocationManager locManager =
                        (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
                locManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                        0, 0, new NetworkLocationListener(), mHandler.getLooper());                
            }
        });
    }

    /**
    * When a client wants to connect to a specific SSID, we don't want
    * them to block forever in the case that this SSID disappears.
    * If we notice that's gone, we will return failure right away to
    * the client.
    */
    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String ssid = null;
            String action = intent.getAction();

            if (mWifiState.state == mWifiState.WIFI_STATE_OPENING) {
              ssid = mWifiState.currentSSID;
            } else if (mWifiState.state == mWifiState.WIFI_STATE_CLOSING) {
              ssid = mWifiState.originalSSID;
            } else {
              //do nothing
              return;
            }

            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
              List<ScanResult> results = mWifiState.mWifiManager.getScanResults();
              for (ScanResult result : results) {
                  if (result.SSID.equals(ssid)) {
                    return;
                  }
              }
              //What we were waiting on is no longer here. Fail.
              mWifiState.handleFailure();
            }
        }
    };

    private void listenForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intents.DATA_SMS_RECEIVED_ACTION);
        intentFilter.addDataScheme("sms");
        intentFilter.addDataAuthority("localhost","7275");
        mContext.registerReceiver(mBroadcastReciever, intentFilter, null, mHandler);

        intentFilter = new IntentFilter();
        intentFilter.addAction(Intents.WAP_PUSH_RECEIVED_ACTION);
        try {
            intentFilter.addDataType("application/vnd.omaloc-supl-init");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.w(TAG, "Malformed SUPL init mime type");
        }
        mContext.registerReceiver(mBroadcastReciever, intentFilter, null, mHandler);

        intentFilter = new IntentFilter();
        intentFilter.addAction(ALARM_WAKEUP);
        intentFilter.addAction(ALARM_TIMEOUT);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mBroadcastReciever, intentFilter, null, mHandler);

        IntentFilter intentFilter1 = new IntentFilter();
        intentFilter1.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.registerReceiver(mWifiScanReceiver, intentFilter1);
    }

    /**
     * Returns the name of this provider.
     */
    @Override
    public String getName() {
        return LocationManager.GPS_PROVIDER;
    }

    @Override
    public ProviderProperties getProperties() {
        return PROPERTIES;
    }

    public void updateNetworkState(int state, NetworkInfo info) {
        sendMessage(UPDATE_NETWORK_STATE, state, info);
    }

    private void handleUpdateNetworkState(int state, NetworkInfo info) {
        mNetworkAvailable = (state == LocationProvider.AVAILABLE);

        if (DEBUG) {
            Log.d(TAG, "updateNetworkState " + (mNetworkAvailable ? "available" : "unavailable")
                + " info: " + info);
        }

        if (info != null) {
            boolean dataEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                                                         Settings.Global.MOBILE_DATA, 1) == 1;
            boolean networkAvailable = info.isAvailable() && dataEnabled;
            String defaultApn = getDefaultApn();

            native_update_network_state(info.isConnected(), info.getType(),
                                        info.isRoaming(), networkAvailable,
                                        info.getExtraInfo(), defaultApn);

            int connType;
            switch (info.getType()) {
              case ConnectivityManager.TYPE_MOBILE_SUPL: {
                  connType = AGpsConnectionInfo.CONNECTION_TYPE_SUPL;
                  break;
              }
              case ConnectivityManager.TYPE_WIFI: {
                  connType = AGpsConnectionInfo.CONNECTION_TYPE_WIFI;
                  break;
              }
              default: {
                  connType = AGpsConnectionInfo.CONNECTION_TYPE_WWAN_ANY;
                  break;
              }
            }
            AGpsConnectionInfo agpsConnInfo = getAGpsConnectionInfo(connType);

            if (null != agpsConnInfo &&
                agpsConnInfo.mState == AGpsConnectionInfo.STATE_OPENING &&
                connType != AGpsConnectionInfo.CONNECTION_TYPE_WIFI) {
                if (mNetworkAvailable) {
                    if (agpsConnInfo.getIpAddr() != null) {
                        Log.d(TAG, "agpsConnInfo.mIpAddr " + agpsConnInfo.getIpAddr().toString());
                        if (false == mConnMgr.requestRouteToHostAddress(agpsConnInfo.getCMConnType(), agpsConnInfo.getIpAddr())) {
                            Log.d(TAG, "call requestRouteToHostAddress failed");
                        }
                    }
                    if (DEBUG) Log.d(TAG, "call native_agps_data_conn_open");
                    native_agps_data_conn_open(agpsConnInfo.getAgpsType(),
                                               agpsConnInfo.getApn(info, defaultApn),
                                               agpsConnInfo.getBearerType(info));
                    agpsConnInfo.mState = AGpsConnectionInfo.STATE_OPEN;
                }
            } else if (null != agpsConnInfo &&
                       connType == AGpsConnectionInfo.CONNECTION_TYPE_WIFI) {
                if (mWifiState.state == mWifiState.WIFI_STATE_OPENING ||
                          mWifiState.state == mWifiState.WIFI_STATE_CLOSING) {
                  NetworkInfo.State networkState = info.getState();
                  NetworkInfo.DetailedState detailedState = info.getDetailedState();
                  if (DEBUG) Log.d(TAG, "handleUpdateNetworkState for TYPE_WIFI");
                  if (DEBUG) Log.d(TAG, "handleUpdateNetworkState detailedstate = " + detailedState+", and state = "+networkState);
                  if (!info.isAvailable()) {
                    if (DEBUG) Log.e(TAG, "ERROR: handleUpdateNetworkState connect to wifi failed!!");
                    mWifiState.handleFailure();
                    return;
                  }
                  if (detailedState != NetworkInfo.DetailedState.CONNECTED) {
                    //note: disconnected means: IP traffic not available.
                    //kind of misleading. idle means disconnected but possible available
                    if (DEBUG) Log.d(TAG, "handleUpdateNetworkState neither connected nor disconnected... return until it is ready");
                    return;
                  }
                  String ssid = (mWifiState.state == mWifiState.WIFI_STATE_OPENING) ? mWifiState.currentSSID : mWifiState.originalSSID;
                  if (isWifiConnectedToSSID(info, ssid)) {
                      if (DEBUG) Log.d(TAG, "handleUpdateNetworkState succeeded! wifi connected, and ssid matches expected!");
                      mWifiState.handleSuccess();
                  } else {
                      Log.e(TAG, "isWifiConnectedToSSID returned false!");
                      mWifiState.handleFailure();
                  }
                } else {
                    if (DEBUG) Log.d(TAG, "ignore wifi update if we are not in OPENING or CLOSING");
                }
            } else {
                if (DEBUG) Log.d(TAG, "call native_agps_data_conn_failed");
                agpsConnInfo.mAPN = null;
                agpsConnInfo.mState = AGpsConnectionInfo.STATE_CLOSED;
                native_agps_data_conn_failed(agpsConnInfo.mAgpsType);
            }
        }

        if (mNetworkAvailable) {
            if (mInjectNtpTimePending == STATE_PENDING_NETWORK) {
                sendMessage(INJECT_NTP_TIME, 0, null);
            }
            if (mDownloadXtraDataPending == STATE_PENDING_NETWORK) {
                sendMessage(DOWNLOAD_XTRA_DATA, 0, null);
            }
        }
    }

    private void handleInjectNtpTime() {
        if (mInjectNtpTimePending == STATE_DOWNLOADING) {
            // already downloading data
            return;
        }
        if (!mNetworkAvailable) {
            // try again when network is up
            mInjectNtpTimePending = STATE_PENDING_NETWORK;
            return;
        }
        mInjectNtpTimePending = STATE_DOWNLOADING;

        // hold wake lock while task runs
        mWakeLock.acquire();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                long delay;

                // force refresh NTP cache when outdated
                if (mNtpTime.getCacheAge() >= NTP_INTERVAL) {
                    mNtpTime.forceRefresh();
                }

                // only update when NTP time is fresh
                if (mNtpTime.getCacheAge() < NTP_INTERVAL) {
                    long time = mNtpTime.getCachedNtpTime();
                    long timeReference = mNtpTime.getCachedNtpTimeReference();
                    long certainty = mNtpTime.getCacheCertainty();
                    long now = System.currentTimeMillis();

                    Log.d(TAG, "NTP server returned: "
                            + time + " (" + new Date(time)
                            + ") reference: " + timeReference
                            + " certainty: " + certainty
                            + " system time offset: " + (time - now));

                    native_inject_time(time, timeReference, (int) certainty);
                    delay = NTP_INTERVAL;
                } else {
                    if (DEBUG) Log.d(TAG, "requestTime failed");
                    delay = RETRY_INTERVAL;
                }

                sendMessage(INJECT_NTP_TIME_FINISHED, 0, null);

                if (mPeriodicTimeInjection) {
                    // send delayed message for next NTP injection
                    // since this is delayed and not urgent we do not hold a wake lock here
                    mHandler.sendEmptyMessageDelayed(INJECT_NTP_TIME, delay);
                }

                // release wake lock held by task
                mWakeLock.release();
            }
        });
    }

    private void handleDownloadXtraData() {
        if (mDownloadXtraDataPending == STATE_DOWNLOADING) {
            // already downloading data
            return;
        }
        if (!mNetworkAvailable) {
            // try again when network is up
            mDownloadXtraDataPending = STATE_PENDING_NETWORK;
            return;
        }
        mDownloadXtraDataPending = STATE_DOWNLOADING;

        // hold wake lock while task runs
        mWakeLock.acquire();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                GpsXtraDownloader xtraDownloader = new GpsXtraDownloader(mContext, mProperties);
                byte[] data = xtraDownloader.downloadXtraData();
                if (data != null) {
                    if (DEBUG) {
                        Log.d(TAG, "calling native_inject_xtra_data");
                    }
                    native_inject_xtra_data(data, data.length);
                }

                sendMessage(DOWNLOAD_XTRA_DATA_FINISHED, 0, null);

                if (data == null) {
                    // try again later
                    // since this is delayed and not urgent we do not hold a wake lock here
                    mHandler.sendEmptyMessageDelayed(DOWNLOAD_XTRA_DATA, RETRY_INTERVAL);
                }

                // release wake lock held by task
                mWakeLock.release();
            }
        });
    }

    private void handleUpdateLocation(Location location) {
        if (location.hasAccuracy()) {
            native_inject_location(location.getLatitude(), location.getLongitude(),
                    location.getAccuracy());
        }
    }

    /**
     * Enables this provider.  When enabled, calls to getStatus()
     * must be handled.  Hardware may be started up
     * when the provider is enabled.
     */
    @Override
    public void enable() {
        sendMessage(ENABLE, 1, null);
    }

    private void handleEnable() {
        if (DEBUG) Log.d(TAG, "handleEnable");

        synchronized (mLock) {
            if (mEnabled) return;
            mEnabled = true;
        }

        boolean enabled = native_init();

        if (enabled) {
            mSupportsXtra = native_supports_xtra();
            if (mSuplServerHost != null) {
                native_set_agps_server(AGpsConnectionInfo.CONNECTION_TYPE_SUPL, mSuplServerHost, mSuplServerPort);
            }
            if (mC2KServerHost != null) {
                native_set_agps_server(AGpsConnectionInfo.CONNECTION_TYPE_C2K, mC2KServerHost, mC2KServerPort);
            }
        } else {
            synchronized (mLock) {
                mEnabled = false;
            }
            Log.w(TAG, "Failed to enable location provider");
        }
    }

    /**
     * Disables this provider.  When disabled, calls to getStatus()
     * need not be handled.  Hardware may be shut
     * down while the provider is disabled.
     */
    @Override
    public void disable() {
        sendMessage(ENABLE, 0, null);
    }

    private void handleDisable() {
        if (DEBUG) Log.d(TAG, "handleDisable");

        synchronized (mLock) {
            if (!mEnabled) return;
            mEnabled = false;
        }

        stopNavigating();
        mAlarmManager.cancel(mWakeupIntent);
        mAlarmManager.cancel(mTimeoutIntent);

        // do this before releasing wakelock
        native_cleanup();
    }

    @Override
    public boolean isEnabled() {
        synchronized (mLock) {
            return mEnabled;
        }
    }

    @Override
    public int getStatus(Bundle extras) {
        if (extras != null) {
            extras.putInt("satellites", mSvCount);
        }
        return mStatus;
    }

    private void updateStatus(int status, int svCount) {
        if (status != mStatus || svCount != mSvCount) {
            mStatus = status;
            mSvCount = svCount;
            mLocationExtras.putInt("satellites", svCount);
            mStatusUpdateTime = SystemClock.elapsedRealtime();
        }
    }

    @Override
    public long getStatusUpdateTime() {
        return mStatusUpdateTime;
    }

    @Override
    public void setRequest(ProviderRequest request, WorkSource source) {
        sendMessage(SET_REQUEST, 0, new GpsRequest(request, source));
    }

    @Override
    public void switchUser(int userId) {
        // nothing to do here
    }

    private void handleSetRequest(ProviderRequest request, WorkSource source) {
        if (DEBUG) Log.d(TAG, "setRequest " + request);



        if (request.reportLocation) {
            // update client uids
            int[] uids = new int[source.size()];
            for (int i=0; i < source.size(); i++) {
                uids[i] = source.get(i);
            }
            updateClientUids(uids);

            mFixInterval = (int) request.interval;

            // check for overflow
            if (mFixInterval != request.interval) {
                Log.w(TAG, "interval overflow: " + request.interval);
                mFixInterval = Integer.MAX_VALUE;
            }

            // apply request to GPS engine
            if (mStarted && hasCapability(GPS_CAPABILITY_SCHEDULING)) {
                // change period
                if (!native_set_position_mode(mPositionMode, GPS_POSITION_RECURRENCE_PERIODIC,
                        mFixInterval, 0, 0)) {
                    Log.e(TAG, "set_position_mode failed in setMinTime()");
                }
            } else if (!mStarted) {
                // start GPS
                startNavigating();
            }
        } else {
            updateClientUids(new int[0]);

            stopNavigating();
            mAlarmManager.cancel(mWakeupIntent);
            mAlarmManager.cancel(mTimeoutIntent);
        }
    }

    private final class Listener implements IBinder.DeathRecipient {
        final IGpsStatusListener mListener;

        Listener(IGpsStatusListener listener) {
            mListener = listener;
        }

        @Override
        public void binderDied() {
            if (DEBUG) Log.d(TAG, "GPS status listener died");

            synchronized (mListeners) {
                mListeners.remove(this);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private void updateClientUids(int[] uids) {
        // Find uid's that were not previously tracked
        for (int uid1 : uids) {
            boolean newUid = true;
            for (int uid2 : mClientUids) {
                if (uid1 == uid2) {
                    newUid = false;
                    break;
                }
            }
            if (newUid) {
                try {
                    mBatteryStats.noteStartGps(uid1);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException", e);
                }
            }
        }

        // Find uid'd that were tracked but have now disappeared
        for (int uid1 : mClientUids) {
            boolean oldUid = true;
            for (int uid2 : uids) {
                if (uid1 == uid2) {
                    oldUid = false;
                    break;
                }
            }
            if (oldUid) {
                try {
                    mBatteryStats.noteStopGps(uid1);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException", e);
                }
            }
        }

        // save current uids
        mClientUids = uids;
    }

    @Override
    public boolean sendExtraCommand(String command, Bundle extras) {

        long identity = Binder.clearCallingIdentity();
        boolean result = false;

        if ("delete_aiding_data".equals(command)) {
            result = deleteAidingData(extras);
        } else if ("force_time_injection".equals(command)) {
            sendMessage(INJECT_NTP_TIME, 0, null);
            result = true;
        } else if ("force_xtra_injection".equals(command)) {
            if (mSupportsXtra) {
                xtraDownloadRequest();
                result = true;
            }
        } else {
            Log.w(TAG, "sendExtraCommand: unknown command " + command);
        }

        Binder.restoreCallingIdentity(identity);
        return result;
    }

    private boolean deleteAidingData(Bundle extras) {
        int flags;

        if (extras == null) {
            flags = GPS_DELETE_ALL;
        } else {
            flags = 0;
            if (extras.getBoolean("ephemeris")) flags |= GPS_DELETE_EPHEMERIS;
            if (extras.getBoolean("almanac")) flags |= GPS_DELETE_ALMANAC;
            if (extras.getBoolean("position")) flags |= GPS_DELETE_POSITION;
            if (extras.getBoolean("time")) flags |= GPS_DELETE_TIME;
            if (extras.getBoolean("iono")) flags |= GPS_DELETE_IONO;
            if (extras.getBoolean("utc")) flags |= GPS_DELETE_UTC;
            if (extras.getBoolean("health")) flags |= GPS_DELETE_HEALTH;
            if (extras.getBoolean("svdir")) flags |= GPS_DELETE_SVDIR;
            if (extras.getBoolean("svsteer")) flags |= GPS_DELETE_SVSTEER;
            if (extras.getBoolean("sadata")) flags |= GPS_DELETE_SADATA;
            if (extras.getBoolean("rti")) flags |= GPS_DELETE_RTI;
            if (extras.getBoolean("celldb-info")) flags |= GPS_DELETE_CELLDB_INFO;
            if (extras.getBoolean("almanac-corr")) flags |= GPS_DELETE_ALMANAC_CORR;
            if (extras.getBoolean("freq-bias-est")) flags |= GPS_DELETE_FREQ_BIAS_EST;
            if (extras.getBoolean("ephemeris-GLO")) flags |= GPS_DELETE_EPHEMERIS_GLO;
            if (extras.getBoolean("almanac-GLO")) flags |= GPS_DELETE_ALMANAC_GLO;
            if (extras.getBoolean("svdir-GLO")) flags |= GPS_DELETE_SVDIR_GLO;
            if (extras.getBoolean("svsteer-GLO")) flags |= GPS_DELETE_SVSTEER_GLO;
            if (extras.getBoolean("almanac-corr-GLO")) flags |= GPS_DELETE_ALMANAC_CORR_GLO;
            if (extras.getBoolean("time-gps")) flags |= GPS_DELETE_TIME_GPS;
            if (extras.getBoolean("time-GLO")) flags |= GPS_DELETE_TIME_GLO;
            if (extras.getBoolean("all")) flags |= GPS_DELETE_ALL;
        }

        if (flags != 0) {
            native_delete_aiding_data(flags);
            return true;
        }

        return false;
    }

    private void startNavigating() {
        if (!mStarted) {
            if (DEBUG) Log.d(TAG, "startNavigating");
            mTimeToFirstFix = 0;
            mLastFixTime = 0;
            mStarted = true;
            mPositionMode = GPS_POSITION_MODE_STANDALONE;

             if (Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ASSISTED_GPS_ENABLED, 1) != 0) {
                if (hasCapability(GPS_CAPABILITY_MSB)) {
                    mPositionMode = GPS_POSITION_MODE_MS_BASED;
                }
            }

            int interval = (hasCapability(GPS_CAPABILITY_SCHEDULING) ? mFixInterval : 1000);
            if (!native_set_position_mode(mPositionMode, GPS_POSITION_RECURRENCE_PERIODIC,
                    interval, 0, 0)) {
                mStarted = false;
                Log.e(TAG, "set_position_mode failed in startNavigating()");
                return;
            }
            if (!native_start()) {
                mStarted = false;
                Log.e(TAG, "native_start failed in startNavigating()");
                return;
            }

            // reset SV count to zero
            updateStatus(LocationProvider.TEMPORARILY_UNAVAILABLE, 0);
            mFixRequestTime = System.currentTimeMillis();
            if (!hasCapability(GPS_CAPABILITY_SCHEDULING)) {
                // set timer to give up if we do not receive a fix within NO_FIX_TIMEOUT
                // and our fix interval is not short
                if (mFixInterval >= NO_FIX_TIMEOUT) {
                    mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + NO_FIX_TIMEOUT, mTimeoutIntent);
                }
            }
        }
    }

    private void stopNavigating() {
        if (DEBUG) Log.d(TAG, "stopNavigating");
        if (mStarted) {
            mStarted = false;
            native_stop();
            mTimeToFirstFix = 0;
            mLastFixTime = 0;
            mLocationFlags = LOCATION_INVALID;

            // reset SV count to zero
            updateStatus(LocationProvider.TEMPORARILY_UNAVAILABLE, 0);
        }
    }

    private void hibernate() {
        // stop GPS until our next fix interval arrives
        stopNavigating();
        mAlarmManager.cancel(mTimeoutIntent);
        mAlarmManager.cancel(mWakeupIntent);
        long now = SystemClock.elapsedRealtime();
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + mFixInterval, mWakeupIntent);
    }

    private boolean hasCapability(int capability) {
        return ((mEngineCapabilities & capability) != 0);
    }

    /**
     * called from native code to update our position.
     */
    private void reportLocation(int flags, double latitude, double longitude, double altitude,
            float speed, float bearing, float accuracy, long timestamp) {
        if (VERBOSE) Log.v(TAG, "reportLocation lat: " + latitude + " long: " + longitude +
                " timestamp: " + timestamp);

        synchronized (mLocation) {
            mLocationFlags = flags;
            if ((flags & LOCATION_HAS_LAT_LONG) == LOCATION_HAS_LAT_LONG) {
                mLocation.setLatitude(latitude);
                mLocation.setLongitude(longitude);
                mLocation.setTime(timestamp);
                // It would be nice to push the elapsed real-time timestamp
                // further down the stack, but this is still useful
                mLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }
            if ((flags & LOCATION_HAS_ALTITUDE) == LOCATION_HAS_ALTITUDE) {
                mLocation.setAltitude(altitude);
            } else {
                mLocation.removeAltitude();
            }
            if ((flags & LOCATION_HAS_SPEED) == LOCATION_HAS_SPEED) {
                mLocation.setSpeed(speed);
            } else {
                mLocation.removeSpeed();
            }
            if ((flags & LOCATION_HAS_BEARING) == LOCATION_HAS_BEARING) {
                mLocation.setBearing(bearing);
            } else {
                mLocation.removeBearing();
            }
            if ((flags & LOCATION_HAS_ACCURACY) == LOCATION_HAS_ACCURACY) {
                mLocation.setAccuracy(accuracy);
            } else {
                mLocation.removeAccuracy();
            }
            mLocation.setExtras(mLocationExtras);

            try {
                mILocationManager.reportLocation(mLocation, false);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException calling reportLocation");
            }
        }

        mLastFixTime = System.currentTimeMillis();
        // report time to first fix
        if (mTimeToFirstFix == 0 && (flags & LOCATION_HAS_LAT_LONG) == LOCATION_HAS_LAT_LONG) {
            mTimeToFirstFix = (int)(mLastFixTime - mFixRequestTime);
            if (DEBUG) Log.d(TAG, "TTFF: " + mTimeToFirstFix);

            // notify status listeners
            synchronized (mListeners) {
                int size = mListeners.size();
                for (int i = 0; i < size; i++) {
                    Listener listener = mListeners.get(i);
                    try {
                        listener.mListener.onFirstFix(mTimeToFirstFix);
                    } catch (RemoteException e) {
                        Log.w(TAG, "RemoteException in stopNavigating");
                        mListeners.remove(listener);
                        // adjust for size of list changing
                        size--;
                    }
                }
            }
        }

        if (mStarted && mStatus != LocationProvider.AVAILABLE) {
            // we want to time out if we do not receive a fix
            // within the time out and we are requesting infrequent fixes
            if (!hasCapability(GPS_CAPABILITY_SCHEDULING) && mFixInterval < NO_FIX_TIMEOUT) {
                mAlarmManager.cancel(mTimeoutIntent);
            }

            // send an intent to notify that the GPS is receiving fixes.
            Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
            intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, true);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            updateStatus(LocationProvider.AVAILABLE, mSvCount);
        }

       if (!hasCapability(GPS_CAPABILITY_SCHEDULING) && mStarted &&
               mFixInterval > GPS_POLLING_THRESHOLD_INTERVAL) {
            if (DEBUG) Log.d(TAG, "got fix, hibernating");
            hibernate();
        }
   }

    /**
     * called from native code to update our status
     */
    private void reportStatus(int status) {
        if (DEBUG) Log.v(TAG, "reportStatus status: " + status);

        synchronized (mListeners) {
            boolean wasNavigating = mNavigating;

            switch (status) {
                case GPS_STATUS_SESSION_BEGIN:
                    mNavigating = true;
                    mEngineOn = true;
                    break;
                case GPS_STATUS_SESSION_END:
                    mNavigating = false;
                    break;
                case GPS_STATUS_ENGINE_ON:
                    mEngineOn = true;
                    break;
                case GPS_STATUS_ENGINE_OFF:
                    mEngineOn = false;
                    mNavigating = false;
                    break;
            }

            if (wasNavigating != mNavigating) {
                int size = mListeners.size();
                for (int i = 0; i < size; i++) {
                    Listener listener = mListeners.get(i);
                    try {
                        if (mNavigating) {
                            listener.mListener.onGpsStarted();
                        } else {
                            listener.mListener.onGpsStopped();
                        }
                    } catch (RemoteException e) {
                        Log.w(TAG, "RemoteException in reportStatus");
                        mListeners.remove(listener);
                        // adjust for size of list changing
                        size--;
                    }
                }

                // send an intent to notify that the GPS has been enabled or disabled.
                Intent intent = new Intent(LocationManager.GPS_ENABLED_CHANGE_ACTION);
                intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, mNavigating);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        }
    }

    /**
     * called from native code to update SV info
     */
    private void reportSvStatus() {

        int svCount = native_read_sv_status(mSvs, mSnrs, mSvElevations, mSvAzimuths, mSvMasks);

        synchronized (mListeners) {
            int size = mListeners.size();
            for (int i = 0; i < size; i++) {
                Listener listener = mListeners.get(i);
                try {
                    listener.mListener.onSvStatusChanged(svCount, mSvs, mSnrs,
                            mSvElevations, mSvAzimuths, mSvMasks[EPHEMERIS_MASK],
                            mSvMasks[ALMANAC_MASK], mSvMasks[USED_FOR_FIX_MASK]);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException in reportSvInfo");
                    mListeners.remove(listener);
                    // adjust for size of list changing
                    size--;
                }
            }
        }

        if (VERBOSE) {
            Log.v(TAG, "SV count: " + svCount +
                    " ephemerisMask: " + Integer.toHexString(mSvMasks[EPHEMERIS_MASK]) +
                    " almanacMask: " + Integer.toHexString(mSvMasks[ALMANAC_MASK]));
            for (int i = 0; i < svCount; i++) {
                Log.v(TAG, "sv: " + mSvs[i] +
                        " snr: " + mSnrs[i]/10 +
                        " elev: " + mSvElevations[i] +
                        " azimuth: " + mSvAzimuths[i] +
                        ((mSvMasks[EPHEMERIS_MASK] & (1 << (mSvs[i] - 1))) == 0 ? "  " : " E") +
                        ((mSvMasks[ALMANAC_MASK] & (1 << (mSvs[i] - 1))) == 0 ? "  " : " A") +
                        ((mSvMasks[USED_FOR_FIX_MASK] & (1 << (mSvs[i] - 1))) == 0 ? "" : "U"));
            }
        }

        // return number of sets used in fix instead of total
        updateStatus(mStatus, Integer.bitCount(mSvMasks[USED_FOR_FIX_MASK]));

        if (mNavigating && mStatus == LocationProvider.AVAILABLE && mLastFixTime > 0 &&
            System.currentTimeMillis() - mLastFixTime > RECENT_FIX_TIMEOUT) {
            // send an intent to notify that the GPS is no longer receiving fixes.
            Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
            intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, false);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            updateStatus(LocationProvider.TEMPORARILY_UNAVAILABLE, mSvCount);
        }
    }


    private void associateToNetwork(String ssid, String password) {
        boolean b;
        Log.d(TAG, "associateToNetwork begin ssid ="+ssid+", password="+password);

        mWifiState.currentSSID = ssid;

        //0) save current state info so we can restore it on if release
        WifiInfo originalNetworkInfo = mWifiState.mWifiManager.getConnectionInfo();
        mWifiState.originalNetId = originalNetworkInfo.getNetworkId();
        mWifiState.originalSSID = originalNetworkInfo.getSSID();
        mWifiState.originalNetworkPreference = mConnMgr.getNetworkPreference();

        if (DEBUG) Log.d(TAG, "saved original wifi info. originalNetId  = " + mWifiState.originalNetId + ", originalNetworkPreference = "+ mWifiState.originalNetworkPreference );

        //2) set network pref to wifi
        mConnMgr.setNetworkPreference(ConnectivityManager.TYPE_WIFI);
        if (DEBUG) Log.d(TAG, "network prefence changed to wifi");
        if (DEBUG) Log.d(TAG, "get WPA wifi config for ssid="+ssid+", password="+password);
        WifiConfiguration wc;
        if (password == null) {
          wc = getWifiConfigurationForOpen(ssid);
        } else {
          wc = getWifiConfigurationForWPA(ssid, password);
        }
        if (DEBUG) Log.d(TAG, "wifi configuration is : "+wc);
        mWifiState.currentNetId = mWifiState.mWifiManager.addNetwork(wc);

        if (mWifiState.currentNetId < 0) {
          if (DEBUG) Log.e(TAG, "ERROR: "+ssid+" add Network returned " + mWifiState.currentNetId);
          mWifiState.handleFailure();
        } else {
          if (DEBUG) Log.d(TAG, ssid+" add Network returned " + mWifiState.currentNetId);

          //4) assoc to network
          b = mWifiState.mWifiManager.enableNetwork(mWifiState.currentNetId, true);
          //mWifiState.mWifiManager.reassociate();
          if (b) {
            if (DEBUG) Log.d(TAG, "enableNetwork returned " + b);
          } else {
            if (DEBUG) Log.e(TAG, "ERROR: enableNetwork returned " + b);
            mWifiState.handleFailure();
          }
        }
    }

    private void restoreOriginalNetworkPreference() {
      mConnMgr.setNetworkPreference(mWifiState.originalNetworkPreference);
      mWifiState.originalNetworkPreference = -1;
    }

    private int getCurrentNetId() {
        WifiInfo info = mWifiState.mWifiManager.getConnectionInfo();
        return info.getNetworkId();
    }

    /**
     * If the string does not have "", convert it to quoted string. For an
     * example, if the string is <abcdef>, the return string will be <"abcdef">.
     * If the string has "", return it.
     *
     * @param string
     *            To be converted to quoted string
     * @return string with quote
     */
    private String convertToQuotedString(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }

        final int lastPos = string.length() - 1;
        if (lastPos < 0 || (string.charAt(0) == '"' && string.charAt(lastPos) == '"')) {
            return string;
        }

        return "\"" + string + "\"";
    }

    private WifiConfiguration getWifiConfigurationForNoAuth(String SSID) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.allowedAuthAlgorithms.clear();
        conf.allowedGroupCiphers.clear();
        conf.allowedKeyManagement.clear();
        conf.allowedPairwiseCiphers.clear();
        conf.allowedProtocols.clear();
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        conf.hiddenSSID = false;
        conf.SSID = convertToQuotedString(SSID);

        conf.status = WifiConfiguration.Status.ENABLED;

        return conf;
    }

    private WifiConfiguration getWifiConfigurationForOpen(String SSID)
    {
        WifiConfiguration conf = new WifiConfiguration();
        conf.allowedAuthAlgorithms.clear();
        conf.allowedGroupCiphers.clear();
        conf.allowedKeyManagement.clear();
        conf.allowedPairwiseCiphers.clear();
        conf.allowedProtocols.clear();
        conf.hiddenSSID = false;
        conf.SSID = convertToQuotedString(SSID);
        conf.priority = 1;

        conf.status = WifiConfiguration.Status.DISABLED;
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        return conf;
    }

    private WifiConfiguration getWifiConfigurationForWPA(String SSID, String password) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.allowedAuthAlgorithms.clear();
        conf.allowedGroupCiphers.clear();
        conf.allowedKeyManagement.clear();
        conf.allowedPairwiseCiphers.clear();
        conf.allowedProtocols.clear();
        conf.hiddenSSID = false;
        conf.SSID = convertToQuotedString(SSID);
        conf.priority = 1;

      if (password.matches("[0-9A-Fa-f]{64}")) {
        Log.d(TAG, "A 64 bit hex password entered.");
        conf.preSharedKey = password;
      } else {
        Log.d(TAG, "A normal password entered: I am quoting it.");
        conf.preSharedKey = convertToQuotedString(password);
      }

      conf.status = WifiConfiguration.Status.DISABLED;
      //this mighth not be necessary:
        //conf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

        return conf;
    }

    private WifiConfiguration getWifiConfigurationForWEP(String SSID, String password) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.allowedAuthAlgorithms.clear();
        conf.allowedGroupCiphers.clear();
        conf.allowedKeyManagement.clear();
        conf.allowedPairwiseCiphers.clear();
        conf.allowedProtocols.clear();
        conf.hiddenSSID = false;
        conf.SSID = convertToQuotedString(SSID);
        conf.priority = 40;

      conf.status = WifiConfiguration.Status.DISABLED;
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        conf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        conf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

        conf.wepKeys[0] = convertToQuotedString(password);
        conf.wepTxKeyIndex = 0;

        return conf;
    }

    public boolean isWifiConnectedToSSID(NetworkInfo info, String ssid) {
        NetworkInfo.DetailedState networkState = info.getDetailedState();
        if (networkState == NetworkInfo.DetailedState.CONNECTED) {
              WifiInfo wifiInfo = mWifiState.mWifiManager.getConnectionInfo();
              if (DEBUG) Log.d(TAG, "wifiInfo  = " + wifiInfo);
              if ((ssid != null) && ssid.equals(wifiInfo.getSSID())) {
                if (DEBUG) Log.d(TAG, "wifi connected, and ssid matches expected!");
                return true;
              } else {
                if (DEBUG) Log.e(TAG, "ssid="+ssid+" doesn't match wifiInfo.getSSID()="+wifiInfo.getSSID());
                return false;
              }
          } else {
            if (DEBUG) Log.e(TAG, "not connected");
            return false;
          }
    }

    /**
     * called from native code to update AGPS status
     */
    private void reportAGpsStatus(int type,
                                  int status,
                                  byte[] ipAddr,
                                  String ssid,
                                  String password) {
        if (DEBUG) Log.d(TAG, "reportAGpsStatus with type = " + type +
                              "status = " + status +
                              "ipAddr = " + ipAddr +
                              "ssid = " + ssid +
                              "password = " + password);
        ReportAgpsStatusMessage rasm = new ReportAgpsStatusMessage(type,
                                                                   status,
                                                                   ipAddr,
                                                                   ssid,
                                                                   password);

        Message msg = new Message();
        msg.what = REPORT_AGPS_STATUS;
        msg.obj = rasm;

        mHandler.sendMessage(msg);
    }

    private void handleReportAgpsStatus(ReportAgpsStatusMessage rasm) {
        int type = rasm.type;
        int status = rasm.status;
        byte[] ipAddr = rasm.ipAddr;
        String ssid = rasm.ssid;
        String password = rasm.password;

        if (DEBUG) Log.d(TAG, "handleReportAgpsStatus with type = " + type +
                              "status = " + status +
                              "ipAddr = " + ipAddr +
                              "ssid = " + ssid +
                              "password = " + password);

        AGpsConnectionInfo agpsConnInfo = getAGpsConnectionInfo(type);
        if (agpsConnInfo == null) {
            if (DEBUG) Log.d(TAG, "reportAGpsStatus agpsConnInfo is null for type "+type);
            // we do not handle this type of connection
            return;
        }

        switch (status) {
            case GPS_REQUEST_AGPS_DATA_CONN:
                if (DEBUG) Log.d(TAG, "GPS_REQUEST_AGPS_DATA_CONN");

                switch (type) {
                case AGpsConnectionInfo.CONNECTION_TYPE_SUPL:
                case AGpsConnectionInfo.CONNECTION_TYPE_WWAN_ANY: {
                    NetworkInfo info = mConnMgr.getNetworkInfo(agpsConnInfo.getCMConnType());
                    agpsConnInfo.connect(info, ipAddr);
                    if (agpsConnInfo.mState == AGpsConnectionInfo.STATE_OPEN ||
                        agpsConnInfo.mState == AGpsConnectionInfo.STATE_KEEP_OPEN) {
                        if (agpsConnInfo.getIpAddr() != null) {
                            Log.d(TAG, "agpsConnInfo.mIpAddr " + agpsConnInfo.getIpAddr().toString());
                            if (false == mConnMgr.requestRouteToHostAddress(agpsConnInfo.getCMConnType(), agpsConnInfo.getIpAddr())) {
                                Log.d(TAG, "call requestRouteToHostAddress failed");
                            }
                        }
                        native_agps_data_conn_open(agpsConnInfo.getAgpsType(),
                                                   agpsConnInfo.getApn(info),
                                                   agpsConnInfo.getBearerType(info));
                    } else if (agpsConnInfo.mState == AGpsConnectionInfo.STATE_OPENING) {
                        // wait for handleUpdateNetworkState before calling native_agps_data_conn_*
                    } else  {
                        native_agps_data_conn_failed(agpsConnInfo.getAgpsType());
                    }
                    break;
                }

                case AGpsConnectionInfo.CONNECTION_TYPE_WIFI: {
                    if (DEBUG) Log.d(TAG, "type == AGpsConnectionInfo.CONNECTION_TYPE_WIFI");
                    if (mWifiState.state != WifiState.WIFI_STATE_CLOSED) {
                        if (DEBUG) Log.e(TAG, "Error: request Wifi but WifiState is not WIFI_STATE_CLOSED");
                        native_agps_data_conn_failed(agpsConnInfo.getAgpsType());
                    }
                    if (mWifiState.mWifiManager.isWifiEnabled()) {
                        if (DEBUG) Log.d(TAG, "wifi enabled");
                        NetworkInfo info = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (isWifiConnectedToSSID(info, ssid)) {
                            if (DEBUG) Log.d(TAG, "already connected to this SSID. not associating to it...");
                            mWifiState.originalNetworkPreference = mConnMgr.getNetworkPreference();
                            if (mWifiState.originalNetworkPreference == ConnectivityManager.TYPE_WIFI) {
                                if (DEBUG) Log.d(TAG, "network Preference already TYPE_mWifiState.mWifiManager. do nothing");
                            } else {
                                if (DEBUG) Log.d(TAG, "network Preference not already TYPE_mWifiState.mWifiManager. change it.");
                                mConnMgr.setNetworkPreference(ConnectivityManager.TYPE_WIFI);
                            }
                            if (DEBUG) Log.d(TAG, "wifi connected, and ssid matches expected!");
                            mWifiState.originalNetId = getCurrentNetId();
                            mWifiState.currentNetId = mWifiState.originalNetId;
                            mWifiState.originalSSID = mWifiState.mWifiManager.getConnectionInfo().getSSID();

                            mWifiState.state = WifiState.WIFI_STATE_OPEN;
                            agpsConnInfo.mState = AGpsConnectionInfo.STATE_OPEN;

                            native_agps_data_conn_open(AGpsConnectionInfo.CONNECTION_TYPE_WIFI, "dummy-apn", AGpsConnectionInfo.BEARER_IPV4);
                        } else {
                            if (DEBUG) Log.d(TAG, "not already connected to this SSID. associating to it...");
                            mWifiState.state = WifiState.WIFI_STATE_OPENING;
                            agpsConnInfo.mState = AGpsConnectionInfo.STATE_OPENING;
                            associateToNetwork(ssid, password);
                        }
                    } else {
                        if (DEBUG) Log.e(TAG, "ERROR: wifi not enabled.. (we assume it is enabled)");
                        native_agps_data_conn_failed(agpsConnInfo.getAgpsType());
                    }
                    break;
                }

                default:
                    if (DEBUG) Log.e(TAG, "type == unknown");
                    break;
                }
                break;
            case GPS_RELEASE_AGPS_DATA_CONN: {
                if (DEBUG) Log.d(TAG, "GPS_RELEASE_AGPS_DATA_CONN");

                switch (type) {
                case AGpsConnectionInfo.CONNECTION_TYPE_SUPL:
                case AGpsConnectionInfo.CONNECTION_TYPE_WWAN_ANY:
                {
                    if (agpsConnInfo.mState != AGpsConnectionInfo.STATE_CLOSED) {
                        if (agpsConnInfo.mState != AGpsConnectionInfo.STATE_KEEP_OPEN) {
                            mConnMgr.stopUsingNetworkFeature(
                                ConnectivityManager.TYPE_MOBILE, agpsConnInfo.mPHConnFeatureStr);
                        }
                        native_agps_data_conn_closed(agpsConnInfo.getAgpsType());
                        agpsConnInfo.mState = AGpsConnectionInfo.STATE_CLOSED;
                    }
                    break;
                }
                case AGpsConnectionInfo.CONNECTION_TYPE_WIFI: {
                    if (DEBUG) Log.v(TAG, "case AGpsConnectionInfo.CONNECTION_TYPE_WIFI");
                    mWifiState.restoreOriginalWifiSettings(false);
                    return;
                }
                default:
                    if (DEBUG) Log.e(TAG, "GPS_RELEASE_AGPS_DATA_CONN but current network state is unknown!");
                    return;
                }
                break;
            }
            case GPS_AGPS_DATA_CONNECTED:
                if (DEBUG) Log.d(TAG, "GPS_AGPS_DATA_CONNECTED");
                break;
            case GPS_AGPS_DATA_CONN_DONE:
                if (DEBUG) Log.d(TAG, "GPS_AGPS_DATA_CONN_DONE");
                break;
            case GPS_AGPS_DATA_CONN_FAILED:
                if (DEBUG) Log.d(TAG, "GPS_AGPS_DATA_CONN_FAILED");
                break;
        }
    }

    /**
     * called from native code to report NMEA data received
     */
    private void reportNmea(long timestamp) {
        synchronized (mListeners) {
            int size = mListeners.size();
            if (size > 0) {
                // don't bother creating the String if we have no listeners
                int length = native_read_nmea(mNmeaBuffer, mNmeaBuffer.length);
                String nmea = new String(mNmeaBuffer, 0, length);

                for (int i = 0; i < size; i++) {
                    Listener listener = mListeners.get(i);
                    try {
                        listener.mListener.onNmeaReceived(timestamp, nmea);
                    } catch (RemoteException e) {
                        Log.w(TAG, "RemoteException in reportNmea");
                        mListeners.remove(listener);
                        // adjust for size of list changing
                        size--;
                    }
                }
            }
        }
    }

    /**
     * called from native code to inform us what the GPS engine capabilities are
     */
    private void setEngineCapabilities(int capabilities) {
        mEngineCapabilities = capabilities;

        if (!hasCapability(GPS_CAPABILITY_ON_DEMAND_TIME) && !mPeriodicTimeInjection) {
            mPeriodicTimeInjection = true;
            requestUtcTime();
        }
    }

    /**
     * called from native code to request XTRA data
     */
    private void xtraDownloadRequest() {
        if (DEBUG) Log.d(TAG, "xtraDownloadRequest");
        sendMessage(DOWNLOAD_XTRA_DATA, 0, null);
    }

    //=============================================================
    // NI Client support
    //=============================================================
    private final INetInitiatedListener mNetInitiatedListener = new INetInitiatedListener.Stub() {
        // Sends a response for an NI reqeust to HAL.
        @Override
        public boolean sendNiResponse(int notificationId, int userResponse)
        {
            // TODO Add Permission check

            if (DEBUG) Log.d(TAG, "sendNiResponse, notifId: " + notificationId +
                    ", response: " + userResponse);
            native_send_ni_response(notificationId, userResponse);
            return true;
        }
    };

    public INetInitiatedListener getNetInitiatedListener() {
        return mNetInitiatedListener;
    }

    // Called by JNI function to report an NI request.
    public void reportNiNotification(
            int notificationId,
            int niType,
            int notifyFlags,
            int timeout,
            int defaultResponse,
            String requestorId,
            String text,
            int requestorIdEncoding,
            int textEncoding,
            String extras  // Encoded extra data
        )
    {
        Log.i(TAG, "reportNiNotification: entered");
        Log.i(TAG, "notificationId: " + notificationId +
                ", niType: " + niType +
                ", notifyFlags: " + notifyFlags +
                ", timeout: " + timeout +
                ", defaultResponse: " + defaultResponse);

        Log.i(TAG, "requestorId: " + requestorId +
                ", text: " + text +
                ", requestorIdEncoding: " + requestorIdEncoding +
                ", textEncoding: " + textEncoding);

        GpsNiNotification notification = new GpsNiNotification();

        notification.notificationId = notificationId;
        notification.niType = niType;
        notification.needNotify = (notifyFlags & GpsNetInitiatedHandler.GPS_NI_NEED_NOTIFY) != 0;
        notification.needVerify = (notifyFlags & GpsNetInitiatedHandler.GPS_NI_NEED_VERIFY) != 0;
        notification.privacyOverride = (notifyFlags & GpsNetInitiatedHandler.GPS_NI_PRIVACY_OVERRIDE) != 0;
        notification.timeout = timeout;
        notification.defaultResponse = defaultResponse;
        notification.requestorId = requestorId;
        notification.text = text;
        notification.requestorIdEncoding = requestorIdEncoding;
        notification.textEncoding = textEncoding;

        // Process extras, assuming the format is
        // one of more lines of "key = value"
        Bundle bundle = new Bundle();

        if (extras == null) extras = "";
        Properties extraProp = new Properties();

        try {
            extraProp.load(new StringReader(extras));
        }
        catch (IOException e)
        {
            Log.e(TAG, "reportNiNotification cannot parse extras data: " + extras);
        }

        for (Entry<Object, Object> ent : extraProp.entrySet())
        {
            bundle.putString((String) ent.getKey(), (String) ent.getValue());
        }

        notification.extras = bundle;

        mNIHandler.handleNiNotification(notification);
    }

    /**
     * Called from native code to request set id info.
     * We should be careful about receiving null string from the TelephonyManager,
     * because sending null String to JNI function would cause a crash.
     */

    private void requestSetID(int flags) {
        TelephonyManager phone = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        int    type = AGPS_SETID_TYPE_NONE;
        String data = "";

        if ((flags & AGPS_RIL_REQUEST_SETID_IMSI) == AGPS_RIL_REQUEST_SETID_IMSI) {
            String data_temp = phone.getSubscriberId();
            if (data_temp == null) {
                // This means the framework does not have the SIM card ready.
            } else {
                // This means the framework has the SIM card.
                data = data_temp;
                type = AGPS_SETID_TYPE_IMSI;
            }
        }
        else if ((flags & AGPS_RIL_REQUEST_SETID_MSISDN) == AGPS_RIL_REQUEST_SETID_MSISDN) {
            String data_temp = phone.getLine1Number();
            if (data_temp == null) {
                // This means the framework does not have the SIM card ready.
            } else {
                // This means the framework has the SIM card.
                data = data_temp;
                type = AGPS_SETID_TYPE_MSISDN;
            }
        }
        native_agps_set_id(type, data);
    }

    /**
     * Called from native code to request utc time info
     */

    private void requestUtcTime() {
        sendMessage(INJECT_NTP_TIME, 0, null);
    }

    /**
     * Called from native code to request reference location info
     */

    private void requestRefLocation(int flags) {
        TelephonyManager phone = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            GsmCellLocation gsm_cell = (GsmCellLocation) phone.getCellLocation();
            if ((gsm_cell != null) && (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) &&
                    (phone.getNetworkOperator() != null) &&
                        (phone.getNetworkOperator().length() > 3)) {
                int type;
                int mcc = Integer.parseInt(phone.getNetworkOperator().substring(0,3));
                int mnc = Integer.parseInt(phone.getNetworkOperator().substring(3));
                int networkType = phone.getNetworkType();
                if (networkType == TelephonyManager.NETWORK_TYPE_UMTS
                    || networkType == TelephonyManager.NETWORK_TYPE_HSDPA
                    || networkType == TelephonyManager.NETWORK_TYPE_HSUPA
                    || networkType == TelephonyManager.NETWORK_TYPE_HSPA) {
                    type = AGPS_REF_LOCATION_TYPE_UMTS_CELLID;
                } else {
                    type = AGPS_REF_LOCATION_TYPE_GSM_CELLID;
                }
                native_agps_set_ref_location_cellid(type, mcc, mnc,
                        gsm_cell.getLac(), gsm_cell.getCid());
            } else {
                Log.e(TAG,"Error getting cell location info.");
            }
        }
        else {
            Log.e(TAG,"CDMA not supported.");
        }
    }

    private void sendMessage(int message, int arg, Object obj) {
        // hold a wake lock until this message is delivered
        // note that this assumes the message will not be removed from the queue before
        // it is handled (otherwise the wake lock would be leaked).
        mWakeLock.acquire();
        mHandler.obtainMessage(message, arg, 1, obj).sendToTarget();
    }

    private final class ProviderHandler extends Handler {
        public ProviderHandler() {
            super(true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            int message = msg.what;
            switch (message) {
                case ENABLE:
                    if (msg.arg1 == 1) {
                        handleEnable();
                    } else {
                        handleDisable();
                    }
                    break;
                case SET_REQUEST:
                    GpsRequest gpsRequest = (GpsRequest) msg.obj;
                    handleSetRequest(gpsRequest.request, gpsRequest.source);
                    break;
                case UPDATE_NETWORK_STATE:
                    handleUpdateNetworkState(msg.arg1, (NetworkInfo)msg.obj);
                    break;
                case INJECT_NTP_TIME:
                    handleInjectNtpTime();
                    break;
                case DOWNLOAD_XTRA_DATA:
                    if (mSupportsXtra) {
                        handleDownloadXtraData();
                    }
                    break;
                case INJECT_NTP_TIME_FINISHED:
                    mInjectNtpTimePending = STATE_IDLE;
                    break;
                case DOWNLOAD_XTRA_DATA_FINISHED:
                    mDownloadXtraDataPending = STATE_IDLE;
                    break;
                case UPDATE_LOCATION:
                    handleUpdateLocation((Location)msg.obj);
                    break;
                case REPORT_AGPS_STATUS:
                    handleReportAgpsStatus((ReportAgpsStatusMessage)msg.obj);
                    break;
            }
            if (msg.arg2 == 1) {
                // wakelock was taken for this message, release it
                mWakeLock.release();
            }
        }
    };

    private final class NetworkLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            // this callback happens on mHandler looper
            if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
                handleUpdateLocation(location);
            }
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override
        public void onProviderEnabled(String provider) { }
        @Override
        public void onProviderDisabled(String provider) { }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        StringBuilder s = new StringBuilder();
        s.append("  mFixInterval=").append(mFixInterval).append("\n");
        s.append("  mEngineCapabilities=0x").append(Integer.toHexString(mEngineCapabilities)).append(" (");
        if (hasCapability(GPS_CAPABILITY_SCHEDULING)) s.append("SCHED ");
        if (hasCapability(GPS_CAPABILITY_MSB)) s.append("MSB ");
        if (hasCapability(GPS_CAPABILITY_MSA)) s.append("MSA ");
        if (hasCapability(GPS_CAPABILITY_SINGLE_SHOT)) s.append("SINGLE_SHOT ");
        if (hasCapability(GPS_CAPABILITY_ON_DEMAND_TIME)) s.append("ON_DEMAND_TIME ");
        s.append(")\n");

        s.append(native_get_internal_state());
        pw.append(s);
    }

    private String getDefaultApn() {
        Uri uri = Uri.parse("content://telephony/carriers/preferapn");
        String apn = null;

        Cursor cursor = mContext.getContentResolver().query(uri, new String[] {"apn"},
                null, null, Carriers.DEFAULT_SORT_ORDER);

        if (null != cursor) {
            try {
                if (cursor.moveToFirst()) {
                    apn = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }

        if (apn == null) {
            apn = "dummy-apn";
        }

        return apn;
    }

    // for GPS SV statistics
    private static final int MAX_SVS = 32;
    private static final int EPHEMERIS_MASK = 0;
    private static final int ALMANAC_MASK = 1;
    private static final int USED_FOR_FIX_MASK = 2;

    // preallocated arrays, to avoid memory allocation in reportStatus()
    private int mSvs[] = new int[MAX_SVS];
    private float mSnrs[] = new float[MAX_SVS];
    private float mSvElevations[] = new float[MAX_SVS];
    private float mSvAzimuths[] = new float[MAX_SVS];
    private int mSvMasks[] = new int[3];
    private int mSvCount;
    // preallocated to avoid memory allocation in reportNmea()
    private byte[] mNmeaBuffer = new byte[120];

    static { class_init_native(); }
    private static native void class_init_native();
    private static native boolean native_is_supported();

    private native boolean native_init();
    private native void native_cleanup();
    private native boolean native_set_position_mode(int mode, int recurrence, int min_interval,
            int preferred_accuracy, int preferred_time);
    private native boolean native_start();
    private native boolean native_stop();
    private native void native_delete_aiding_data(int flags);
    // returns number of SVs
    // mask[0] is ephemeris mask and mask[1] is almanac mask
    private native int native_read_sv_status(int[] svs, float[] snrs,
            float[] elevations, float[] azimuths, int[] masks);
    private native int native_read_nmea(byte[] buffer, int bufferSize);
    private native void native_inject_location(double latitude, double longitude, float accuracy);

    // XTRA Support
    private native void native_inject_time(long time, long timeReference, int uncertainty);
    private native boolean native_supports_xtra();
    private native void native_inject_xtra_data(byte[] data, int length);

    // DEBUG Support
    private native String native_get_internal_state();

    // AGPS Support
    private native void native_agps_data_conn_open(int agpsType, String apn, int bearerType);
    private native void native_agps_data_conn_closed(int agpsType);
    private native void native_agps_data_conn_failed(int agpsType);
    private native void native_agps_ni_message(byte [] msg, int length);
    private native void native_set_agps_server(int type, String hostname, int port);

    // Network-initiated (NI) Support
    private native void native_send_ni_response(int notificationId, int userResponse);

    // AGPS ril suport
    private native void native_agps_set_ref_location_cellid(int type, int mcc, int mnc,
            int lac, int cid);
    private native void native_agps_set_id(int type, String setid);

    private native void native_update_network_state(boolean connected, int type,
            boolean roaming, boolean available, String extraInfo, String defaultAPN);

    private static AGpsConnectionInfo[] mAGpsConnections = new AGpsConnectionInfo[3];
    private AGpsConnectionInfo getAGpsConnectionInfo(int connType) {
        if (DEBUG) Log.d(TAG, "getAGpsConnectionInfo connType - "+connType);
        switch (connType)
        {
        case AGpsConnectionInfo.CONNECTION_TYPE_WWAN_ANY:
        case AGpsConnectionInfo.CONNECTION_TYPE_C2K:
            if (null == mAGpsConnections[0])
                mAGpsConnections[0] = new AGpsConnectionInfo(ConnectivityManager.TYPE_MOBILE, connType);
            return mAGpsConnections[0];
        case AGpsConnectionInfo.CONNECTION_TYPE_SUPL:
            if (null == mAGpsConnections[1])
                mAGpsConnections[1] = new AGpsConnectionInfo(ConnectivityManager.TYPE_MOBILE_SUPL, connType);
            return mAGpsConnections[1];
        case AGpsConnectionInfo.CONNECTION_TYPE_WIFI:
            if (null == mAGpsConnections[2])
                mAGpsConnections[2] = new AGpsConnectionInfo(ConnectivityManager.TYPE_WIFI, connType);
            return mAGpsConnections[2];
        default:
            return null;
        }
    }

    private class AGpsConnectionInfo {
        // these need to match AGpsType enum in gps.h
        private static final int CONNECTION_TYPE_ANY = 0;
        private static final int CONNECTION_TYPE_SUPL = 1;
        private static final int CONNECTION_TYPE_C2K = 2;
        private static final int CONNECTION_TYPE_WWAN_ANY = 3;
        private static final int CONNECTION_TYPE_WIFI = 4;

        // this must match the definition of gps.h
        private static final int BEARER_INVALID = -1;
        private static final int BEARER_IPV4 = 0;
        private static final int BEARER_IPV6 = 1;
        private static final int BEARER_IPV4V6 = 2;

        // for mState
        private static final int STATE_CLOSED = 0;
        private static final int STATE_OPENING = 1;
        private static final int STATE_OPEN = 2;
        private static final int STATE_KEEP_OPEN = 3;

        // SUPL vs ANY (which really is non-SUPL)
        private final int mCMConnType;
        private final int mAgpsType;
        private final String mPHConnFeatureStr;
        private String mAPN;
        private int mIPvVerType;
        private int mState;
        private InetAddress mIpAddr;
        private int mBearerType;

        private AGpsConnectionInfo(int connMgrConnType, int agpsType) {
            mCMConnType = connMgrConnType;
            mAgpsType = agpsType;
            if (ConnectivityManager.TYPE_MOBILE_SUPL == connMgrConnType) {
                mPHConnFeatureStr = Phone.FEATURE_ENABLE_SUPL;
            } else {
                mPHConnFeatureStr = Phone.FEATURE_ENABLE_MMS;
            }
            mAPN = null;
            mState = STATE_CLOSED;
            mIpAddr = null;
            mBearerType = BEARER_INVALID;
        }
        private int getAgpsType() {
            return mAgpsType;
        }
        private int getCMConnType() {
            return mCMConnType;
        }
        private InetAddress getIpAddr() {
            return mIpAddr;
        }
        private String getApn(NetworkInfo info) {
            return getApn(info, getDefaultApn());
        }
        private String getApn(NetworkInfo info, String defaultApn) {

            if (info != null) {
                mAPN = info.getExtraInfo();
            }
            if (mAPN == null) {
                /* We use the value we read out from the database. That value itself
                   is default to "dummy-apn" if no value from database. */
                mAPN = defaultApn;
            }

            return mAPN;
        }
        private int getBearerType(NetworkInfo info) {
            if (mAPN == null) {
                mAPN = getApn(info);
            }
            String ipProtocol = null;
            TelephonyManager phone = (TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);

            // if using the existing default data connection
            if (mState == AGpsConnectionInfo.STATE_KEEP_OPEN) {
                int networkType = phone.getNetworkType();
                if (TelephonyManager.NETWORK_TYPE_CDMA == networkType ||
                    TelephonyManager.NETWORK_TYPE_EVDO_0 == networkType ||
                    TelephonyManager.NETWORK_TYPE_EVDO_A == networkType ||
                    TelephonyManager.NETWORK_TYPE_1xRTT == networkType ||
                    TelephonyManager.NETWORK_TYPE_EVDO_B == networkType) {
                        ipProtocol = SystemProperties.get("persist.telephony.cdma.protocol", "IP");
                }
            }

            if (ipProtocol == null) {
                String selection = "current = 1";
                selection += " and apn = '" + mAPN + "'";
                selection += " and carrier_enabled = 1";

                Cursor cursor = mContext.getContentResolver().query(Carriers.CONTENT_URI,
                        new String[] {Carriers.PROTOCOL}, selection, null, Carriers.DEFAULT_SORT_ORDER);

                if (null != cursor) {
                    try {
                        if (cursor.moveToFirst()) {
                            ipProtocol = cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
            Log.d(TAG, "ipProtocol: " + ipProtocol + " apn: " + mAPN +
                       " networkType: " + phone.getNetworkTypeName() + " state: " + mState);

            if (null == ipProtocol) {
                mBearerType = BEARER_IPV4;
            } else if (ipProtocol.equals("IPV6")) {
                mBearerType = BEARER_IPV6;
            } else if (ipProtocol.equals("IPV4V6")) {
                mBearerType = BEARER_IPV4V6;
            } else {
                mBearerType = BEARER_IPV4;
            }

            return mBearerType;
        }

        private void connect(NetworkInfo info, byte[] ipAddr) {
            int result = -1;
            if (mCMConnType == ConnectivityManager.TYPE_MOBILE &&
                info != null && info.isConnected() && !info.isRoaming()) {
                mState = AGpsConnectionInfo.STATE_KEEP_OPEN;
                result = PhoneConstants.APN_ALREADY_ACTIVE;
            } else {
                // Set agpsConnInfo.mState before calling startUsingNetworkFeature
                //  to avoid a race condition with handleUpdateNetworkState()
                mState = AGpsConnectionInfo.STATE_OPENING;
                result = mConnMgr.startUsingNetworkFeature(
                      ConnectivityManager.TYPE_MOBILE, mPHConnFeatureStr);
                if ( ipAddr != null) {
                    try {
                        mIpAddr = InetAddress.getByAddress(ipAddr);
                    } catch(UnknownHostException uhe) {
                        if (DEBUG) Log.d(TAG, "bad ipaddress");
                    }
                }
            }
            if (result == PhoneConstants.APN_ALREADY_ACTIVE) {
                if (DEBUG) Log.d(TAG, "Phone.APN_ALREADY_ACTIVE");
                if (mState != AGpsConnectionInfo.STATE_KEEP_OPEN) {
                    mState = AGpsConnectionInfo.STATE_OPEN;
                }
            } else if (result == PhoneConstants.APN_REQUEST_STARTED) {
                if (DEBUG) Log.d(TAG, "Phone.APN_REQUEST_STARTED");
                // Nothing to do here
            } else {
                if (DEBUG) Log.d(TAG, "startUsingNetworkFeature failed with "+result);
                mState = AGpsConnectionInfo.STATE_CLOSED;
            }
        }
    }


    private class WifiState {
        private static final int WIFI_STATE_CLOSED = 0;
        private static final int WIFI_STATE_OPENING = 1;
        private static final int WIFI_STATE_OPEN = 2;
        private static final int WIFI_STATE_CLOSING = 3;

        private int state = WIFI_STATE_CLOSED;
        private String currentSSID = null;
        private int currentNetId = -1;
        private int originalNetId = -1;
        private String originalSSID = null;
        private int originalNetworkPreference = -1;

        private boolean reportFailOnClosed = false;

        private WifiManager mWifiManager = null;

        public WifiState() {
            mWifiManager = (WifiManager) GpsLocationProvider.this.mContext.getSystemService(Context.WIFI_SERVICE);
        }

        private void restoreOriginalNetworkPreference() {
          mConnMgr.setNetworkPreference(originalNetworkPreference);
          originalNetworkPreference = -1;
        }

        /*
         * Restore Wifi and network settings to original
         * The restore is started here, and could completed here,
         * or by an asynchronous broadcast event saying network restore complete.
         */
        private void restoreOriginalWifiSettings(boolean ReportFailOnClosed) {
          if (DEBUG) Log.v(TAG, "restoreOriginalWifiSettings");

          if (DEBUG) Log.v(TAG, "originalNetId = "+ originalNetId );
          if (DEBUG) Log.v(TAG, "currentNetId = "+ currentNetId );
          if (DEBUG) Log.v(TAG, "reportFailOnClosed = "+ reportFailOnClosed);


          this.reportFailOnClosed = ReportFailOnClosed;
          boolean b;
          if (mConnMgr.getNetworkPreference() == originalNetworkPreference) {
              if (DEBUG) Log.v(TAG, "current network preference same as original. do nothing.");
          } else {
              if (DEBUG) Log.v(TAG, "restoring original Network Preference...");
              restoreOriginalNetworkPreference();
          }

          /*
          * Do not restore original network settings if original network connection was "none"
          * Side effect: you will be connected to something after it's oven even though originally
          * you were not connected to anything.
          */
          if ((currentNetId == originalNetId) ||
              (originalNetId == -1)) {
              if (DEBUG) Log.v(TAG, "currentNetId == originalNetId or original was not connected. don't touch netId.");

              state = WIFI_STATE_CLOSED;
              if (reportFailOnClosed) {
                  /*
                  * We added a network but it failed, let's remove it
                  */
                  if (currentNetId >= 0) {
                    if (DEBUG) Log.v(TAG, "removing currentNetId = " + currentNetId);
                    b = mWifiManager.removeNetwork(currentNetId);
                    if (b) {
                      if (DEBUG) Log.e(TAG, "successfully removed current AP");
                    } else {
                      if (DEBUG) Log.e(TAG, "ERROR: removeNetwork returned " + b);
                    }
                  }
                  native_agps_data_conn_failed(AGpsConnectionInfo.CONNECTION_TYPE_WIFI);
              } else {
                  native_agps_data_conn_closed(AGpsConnectionInfo.CONNECTION_TYPE_WIFI);
              }
          } else {
              state = WIFI_STATE_CLOSING;
              if (currentNetId >= 0) {
                /*
                if (DEBUG) Log.v(TAG, "There is a current NetId. disconnecting...");
                b = mWifiManager.disconnect();
                if (b) {
                  if (DEBUG) Log.e(TAG, "successfully disconnected from current AP");
                } else {
                  if (DEBUG) Log.e(TAG, "ERROR: disconnect returned " + b);
                }
                */

                b = mWifiManager.removeNetwork(currentNetId);
                if (b) {
                  if (DEBUG) Log.e(TAG, "successfully removed current AP");
                } else {
                  if (DEBUG) Log.e(TAG, "ERROR: removeNetwork returned " + b);
                }
              }

              if (DEBUG) Log.v(TAG, "restoring original network...");
              b = mWifiManager.enableNetwork(originalNetId, true);
              if (b) {
                if (DEBUG) Log.d(TAG, "enableNetwork returned " + b);
              } else {
                if (DEBUG) Log.e(TAG, "ERROR: enableNetwork returned " + b);
              }
          }
          if (DEBUG) Log.v(TAG, "restoreOriginalWifiSettings end");
        }


        /*
        * Waiting is now done, state has finished changing
        * to be what we were waiting for
        */
        private void handleSuccess() {
          if (state == WIFI_STATE_OPENING) {
            if (DEBUG) Log.v(TAG, "handleSuccess for WIFI_STATE_OPENING");
            native_agps_data_conn_open(AGpsConnectionInfo.CONNECTION_TYPE_WIFI, "dummy-apn", AGpsConnectionInfo.BEARER_IPV4);

            state = WIFI_STATE_OPEN;
            getAGpsConnectionInfo(AGpsConnectionInfo.CONNECTION_TYPE_WIFI).mState = AGpsConnectionInfo.STATE_OPEN;
          } else if (state == WIFI_STATE_CLOSING) {
            if (DEBUG) Log.v(TAG, "handleSuccess for WIFI_STATE_CLOSING");
            if (reportFailOnClosed) {
              reportFailOnClosed = false;
              native_agps_data_conn_failed(AGpsConnectionInfo.CONNECTION_TYPE_WIFI);
            } else {
              native_agps_data_conn_closed(AGpsConnectionInfo.CONNECTION_TYPE_WIFI);
            }
            state = WIFI_STATE_CLOSED;
            getAGpsConnectionInfo(AGpsConnectionInfo.CONNECTION_TYPE_WIFI).mState = AGpsConnectionInfo.STATE_CLOSED;
            currentNetId = -1;
            currentSSID = null;
            originalNetId = -1;
            originalSSID = null;
            originalNetworkPreference = -1;
          } else {
            if (DEBUG) Log.e(TAG, "handleSuccess invalid case");
          }
        }

        /*
        * We have noticed at this point that we can no longer succeed
        * in whatever we were waiting to do.
        */
        private void handleFailure() {
          if (state == WIFI_STATE_OPENING) {
            if (DEBUG) Log.v(TAG, "handleFailure for WIFI_STATE_OPENING");
            restoreOriginalWifiSettings(true);
          } else if (state == WIFI_STATE_CLOSING) {
            if (DEBUG) Log.v(TAG, "handleFailure for WIFI_STATE_CLOSING");
            state = WIFI_STATE_CLOSED;
            native_agps_data_conn_failed(AGpsConnectionInfo.CONNECTION_TYPE_WIFI);
          } else {
            if (DEBUG) Log.e(TAG, "handleFailure invalid case");
          }
        }
    }

    private class ReportAgpsStatusMessage {
      int type;
      int status;
      byte[] ipAddr;
      String ssid;
      String password;

      public ReportAgpsStatusMessage(int type,
                                     int status,
                                     byte[] ipAddr,
                                     String ssid,
                                     String password) {
        this.type = type;
        this.status = status;
        this.ipAddr = ipAddr;
        this.ssid = ssid;
        this.password = password;
      }
    }
}
