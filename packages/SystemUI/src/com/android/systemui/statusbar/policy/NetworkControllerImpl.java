/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.DemoMode;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarHeaderView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Platform implementation of the network controller. **/
public class NetworkControllerImpl extends BroadcastReceiver
        implements NetworkController, DemoMode {
    // debug
    static final String TAG = "StatusBar.NetworkController";
    static final boolean DEBUG = false;
    static final boolean CHATTY = false; // additional diagnostics, but not logspew

    // telephony
    boolean mHspaDataDistinguishable;
    protected TelephonyManager mPhone;
    boolean mDataConnected;
    IccCardConstants.State mSimState = IccCardConstants.State.READY;
    int mPhoneState = TelephonyManager.CALL_STATE_IDLE;
    int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    int mDataState = TelephonyManager.DATA_DISCONNECTED;
    int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
    ServiceState mServiceState;
    SignalStrength mSignalStrength;
    int[] mDataIconList = TelephonyIcons.DATA_G[0];
    String mNetworkName;
    String mNetworkNameDefault;
    String mNetworkNameSeparator;
    String mSpn;
    String mPlmn;
    String mOriginalTelephonyPlmn;
    String mOriginalTelephonySpn;
    int mPhoneSignalIconId;
    int mQSPhoneSignalIconId;
    int mDataDirectionIconId; // data + data direction on phones
    int mDataSignalIconId;
    int mDataTypeIconId;
    int mQSDataTypeIconId;
    int mAirplaneIconId;
    int mNoSimIconId;
    int mLastSimIconId;
    int mMobileActivityIconId; // overlay arrows for data direction
    int mLastMobileActivityIconId;
    boolean mDataActive;
    boolean mNoSim;
    int mLastSignalLevel;
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mShowSpn = false;
    boolean mShowPlmn = false;
    boolean mAlwaysShowCdmaRssi = false;
    boolean mShow4GforLTE = false;
    boolean mShowRsrpSignalLevelforLTE = false;

    private String mCarrierText = "";

    String mContentDescriptionPhoneSignal;
    String mContentDescriptionWifi;
    String mContentDescriptionWimax;
    String mContentDescriptionCombinedSignal;
    String mContentDescriptionDataType;

    // wifi
    protected WifiManager mWifiManager;
    protected AsyncChannel mWifiChannel;
    boolean mWifiEnabled, mWifiConnected;
    int mWifiRssi, mWifiLevel;
    String mWifiSsid;
    int mWifiIconId = 0;
    int mQSWifiIconId = 0;
    int mWifiActivityIconId = 0; // overlay arrows for wifi direction
    int mWifiActivity = WifiManager.DATA_ACTIVITY_NONE;

    // bluetooth
    protected boolean mBluetoothTethered = false;
    protected int mBluetoothTetherIconId =
        com.android.internal.R.drawable.stat_sys_tether_bluetooth;

    //wimax
    protected boolean mWimaxSupported = false;
    protected boolean mIsWimaxEnabled = false;
    protected boolean mWimaxConnected = false;
    protected boolean mWimaxIdle = false;
    protected int mWimaxIconId = 0;
    protected int mWimaxSignal = 0;
    protected int mWimaxState = 0;
    protected int mWimaxExtraState = 0;
    protected int mDataServiceState = ServiceState.STATE_OUT_OF_SERVICE;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    protected boolean mConnected = false;
    protected int mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
    protected String mConnectedNetworkTypeName;
    protected int mLastConnectedNetworkType = ConnectivityManager.TYPE_NONE;

    protected int mInetCondition = 0;
    protected int mLastInetCondition = 0;
    protected static final int INET_CONDITION_THRESHOLD = 50;

    protected boolean mAirplaneMode = false;
    protected boolean mLastAirplaneMode = true;

    private Locale mLocale = null;
    private Locale mLastLocale = null;

    // our ui
    protected Context mContext;
    ArrayList<ImageView> mPhoneSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataDirectionIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataDirectionOverlayIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWifiIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mWimaxIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mCombinedSignalIconViews = new ArrayList<ImageView>();
    ArrayList<ImageView> mDataTypeIconViews = new ArrayList<ImageView>();
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<StatusBarHeaderView> mEmergencyViews = new ArrayList<StatusBarHeaderView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();
    int mLastPhoneSignalIconId = -1;
    int mLastDataDirectionIconId = -1;
    int mLastDataDirectionOverlayIconId = -1;
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;
    int mLastDataTypeIconId = -1;
    String mLastCombinedLabel = "";

    protected boolean mHasMobileDataFeature;

    boolean mDataAndWifiStacked = false;

    protected static boolean mAppopsStrictEnabled = false;

    // The current user ID.
    private int mCurrentUserId;

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon,
		int activityIcon, String contentDescription);
        void setMobileDataIndicators(boolean visible, int strengthIcon,
	        int activityIcon, int typeIcon, String contentDescription,
		String typeContentDescription, boolean roaming,
                boolean isTypeIconWide, int noSimIcon);
        void setIsAirplaneMode(boolean is, int airplaneIcon);
    }

    private final AccessPointControllerImpl mAccessPoints;
    private final MobileDataController mMobileDataController;

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkControllerImpl(Context context) {
        mContext = context;
        final Resources res = context.getResources();

        TelephonyIcons.initAll(context);
        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        mAppopsStrictEnabled = AppOpsManager.isStrictEnable();

        mShowPhoneRSSIForData = res.getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = res.getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = res.getBoolean(
                com.android.internal.R.bool.config_alwaysUseCdmaRssi);
        mShow4GforLTE = mContext.getResources().getBoolean(
                R.bool.config_show4GForLTE);
        mShowRsrpSignalLevelforLTE = mContext.getResources().getBoolean(
                R.bool.config_showRsrpSignalLevelforLTE);
        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();
        updateWimaxIcons();

        // telephony
        registerPhoneStateListener(context);
        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);
        mNetworkNameSeparator = mContext.getString(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = mContext.getString(
                com.android.internal.R.string.lockscreen_carrier_default);
        mNetworkName = mNetworkNameDefault;

        // wifi
        createWifiHandler();

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);

        mWimaxSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if(mWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }
        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        mLastLocale = mContext.getResources().getConfiguration().locale;
        mAccessPoints = new AccessPointControllerImpl(mContext);
        mAccessPoints.setNetworkController(this);
        mMobileDataController = new MobileDataController(mContext);
        mMobileDataController.setCallback(new MobileDataController.Callback() {
            @Override
            public void onMobileDataEnabled(boolean enabled) {
                notifyMobileDataEnabled(enabled);
            }
        });
    }

    public int getConnectedWifiLevel() {
        //return mWifiSignalController.getState().level;
        return mWifiLevel;
    }

    @Override
    public AccessPointController getAccessPointController() {
        return mAccessPoints;
    }

    private void notifyMobileDataEnabled(boolean enabled) {
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            cb.onMobileDataEnabled(enabled);
        }
    }

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    public boolean hasVoiceCallingFeature() {
        return mPhone.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public boolean isEmergencyOnly() {
        return (mServiceState != null && mServiceState.isEmergencyOnly());
    }

    protected void createWifiHandler() {
        // wifi
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }
    }

    protected void registerPhoneStateListener(Context context) {
        // telephony
        mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone.listen(mPhoneStateListener,
                          PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
    }

    public void addPhoneSignalIconView(ImageView v) {
        mPhoneSignalIconViews.add(v);
    }

    public void addDataDirectionIconView(ImageView v) {
        mDataDirectionIconViews.add(v);
    }

    public void addDataDirectionOverlayIconView(ImageView v) {
        mDataDirectionOverlayIconViews.add(v);
    }

    public void addWifiIconView(ImageView v) {
        mWifiIconViews.add(v);
    }

    public void addWimaxIconView(ImageView v) {
        mWimaxIconViews.add(v);
    }

    public void addCombinedSignalIconView(ImageView v) {
        mCombinedSignalIconViews.add(v);
    }

    public void addDataTypeIconView(ImageView v) {
        mDataTypeIconViews.add(v);
    }

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addEmergencyLabelView(StatusBarHeaderView v) {
        mEmergencyViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    public void removeNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.remove(cb);
    }

    @Override
    public void setWifiEnabled(final boolean enabled) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... args) {
                // Disable tethering if enabling Wifi
                final int wifiApState = mWifiManager.getWifiApState();
                if (enabled && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                               (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                    mWifiManager.setWifiApEnabled(null, false);
                }

                mWifiManager.setWifiEnabled(enabled);
                return null;
            }
        }.execute();
    }

    @Override
    public void onUserSwitched(int newUserId) {
        mCurrentUserId = newUserId;
        mAccessPoints.onUserSwitched(newUserId);
        //updateConnectivity();
        //refreshCarrierLabel();
    }

    @Override
    public DataUsageInfo getDataUsageInfo() {
        final DataUsageInfo info =  mMobileDataController.getDataUsageInfo();
        if (info != null) {
            info.carrier = mNetworkName;
        }
        return info;
    }

    @Override
    public boolean isMobileDataSupported() {
        return mMobileDataController.isMobileDataSupported();
    }

    @Override
    public boolean isMobileDataEnabled() {
        return mMobileDataController.isMobileDataEnabled();
    }

    @Override
    public void setMobileDataEnabled(boolean enabled) {
        mMobileDataController.setMobileDataEnabled(enabled);
    }

    private boolean isTypeIconWide(int iconId) {
        return TelephonyIcons.ICON_LTE == iconId || TelephonyIcons.ICON_1X == iconId
                || TelephonyIcons.ICON_3G == iconId || TelephonyIcons.ICON_4G == iconId;
    }

    private boolean isQsTypeIconWide(int iconId) {
        return TelephonyIcons.QS_ICON_LTE == iconId || TelephonyIcons.QS_ICON_1X == iconId
                || TelephonyIcons.QS_ICON_3G == iconId || TelephonyIcons.QS_ICON_4G == iconId;
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        if (mDemoMode) return;
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature || mAppopsStrictEnabled),
                mWifiIconId,
                mWifiActivityIconId,
                mContentDescriptionWifi);

        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mAlwaysShowCdmaRssi ? mPhoneSignalIconId : mWimaxIconId,
                    mMobileActivityIconId,
                    mDataTypeIconId,
                    mContentDescriptionWimax,
                    mContentDescriptionDataType,
                    mDataTypeIconId == TelephonyIcons.ROAMING_ICON,
                    false /* isTypeIconWide */,
                    mNoSimIconId);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators(
                    mHasMobileDataFeature,
                    mShowPhoneRSSIForData ? mPhoneSignalIconId : mDataSignalIconId,
                    mMobileActivityIconId,
                    mDataTypeIconId,
                    mContentDescriptionPhoneSignal,
                    mContentDescriptionDataType,
                    mDataTypeIconId == TelephonyIcons.ROAMING_ICON,
                    isTypeIconWide(mDataTypeIconId),
                    mNoSimIconId);
            if (DEBUG) {
                Log.d(TAG, "refreshSignalCluster - setMobileDataIndicators: "
                        + " mHasMobileDataFeature = " + String.valueOf(mHasMobileDataFeature)
                        + " mPhoneSignalIconId = " + getResourceName(mPhoneSignalIconId)
                        + " mDataSignalIconId = " + getResourceName(mDataSignalIconId)
                        + " mMobileActivityIconId = " + getResourceName(mMobileActivityIconId)
                        + " mDataTypeIconId = " + getResourceName(mDataTypeIconId)
                        + " mNoSimIconId = " + getResourceName(mNoSimIconId));
            }
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mAirplaneIconId);
    }

    void notifySignalsChangedCallbacks(NetworkSignalChangedCallback cb) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean wifiEnabled = mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature);
        String wifiDesc = wifiEnabled ?
                mWifiSsid : null;
        boolean wifiIn = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_IN);
        boolean wifiOut = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_OUT);
        cb.onWifiSignalChanged(mWifiEnabled, mWifiConnected, mQSWifiIconId, wifiIn, wifiOut,
                mContentDescriptionWifi, wifiDesc);

        boolean mobileIn = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_IN);
        boolean mobileOut = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_OUT);
        if (isEmergencyOnly()) {
            cb.onMobileDataSignalChanged(false, mQSPhoneSignalIconId,
                    mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                    mContentDescriptionDataType, null, mNoSim, isQsTypeIconWide(mQSDataTypeIconId));
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(true, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mNetworkName, mNoSim,
                        isQsTypeIconWide(mQSDataTypeIconId));
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(mHasMobileDataFeature, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mNetworkName, mNoSim,
                        isQsTypeIconWide(mQSDataTypeIconId));
            }
        }
        cb.onAirplaneModeChanged(mAirplaneMode);
    }

    public void setStackedMode(boolean stacked) {
        mDataAndWifiStacked = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            updateSimState(intent);
            updateDataIcon();
            refreshViews();
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            mShowSpn = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
            mShowPlmn = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false);
            mSpn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            mPlmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            mOriginalTelephonySpn = mSpn;
            mOriginalTelephonyPlmn = mPlmn;
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_monitor_locale_change)) {
                if (mShowSpn && mSpn != null) {
                    mSpn = getLocaleString(mOriginalTelephonySpn);
                }
                if (mShowPlmn && mPlmn != null) {
                    mPlmn = getLocaleString(mOriginalTelephonyPlmn);
                }
            }
            updateNetworkName(mShowSpn, mSpn , mShowPlmn , mPlmn);
            refreshViews();
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            //parse the string to current language string in public resources
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_monitor_locale_change)) {
                if (mShowSpn && mSpn != null) {
                    mSpn = getLocaleString(mOriginalTelephonySpn);
                }
                if (mShowPlmn && mPlmn != null) {
                    mPlmn = getLocaleString(mOriginalTelephonyPlmn);
                }
            }
            updateNetworkName( mShowSpn, mSpn , mShowPlmn , mPlmn);
            refreshLocale();
            refreshViews();
        } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
            updateNetworkName(false, null, false, null);
            refreshViews();
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            refreshLocale();
            updateAirplaneMode();
            updateSimIcon();
            refreshViews();
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews();
        }
    }


    // ===== Telephony ==============================================================
    protected String getLocaleString(String originalCarrier) {
        String localeCarrier = android.util.NativeTextHelper.getLocalString(mContext,
                originalCarrier,
                com.android.internal.R.array.origin_carrier_names,
                com.android.internal.R.array.locale_carrier_names);
        return localeCarrier;
    }

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (DEBUG) {
                Log.d(TAG, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                    ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            mSignalStrength = signalStrength;
            updateIconSet();
            updateTelephonySignalStrength();
            refreshViews();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (DEBUG) {
                Log.d(TAG, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                        + " dataState=" + state.getDataRegState());
            }
            mServiceState = state;
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_combined_signal)) {
                /*
                 * if combined_signal is set to true only then consider data
                 * service state for signal display
                 */
                mDataServiceState = mServiceState.getDataRegState();
                if (DEBUG) {
                    Log.d(TAG, "Combining data service state " + mDataServiceState + " for signal");
                }
            }
            updateIconSet();
            updateTelephonySignalStrength();
            updateDataNetType();
            updateDataIcon();
            updateNetworkName(mShowSpn, mSpn , mShowPlmn , mPlmn);
            refreshViews();
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DEBUG) {
                Log.d(TAG, "onCallStateChanged state=" + state);
            }
            // In cdma, if a voice call is made, RSSI should switch to 1x.
            if (isCdma()) {
                updateTelephonySignalStrength();
                refreshViews();
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(TAG, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState = state;
            mDataNetType = networkType;
            updateIconSet();
            updateDataNetType();
            updateDataIcon();
            refreshViews();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(TAG, "onDataActivity: direction=" + direction);
            }
            mDataActivity = direction;
            updateDataIcon();
            refreshViews();
        }
    };

    private void updateCarrierText() {
        int textResId = 0;
        if (mAirplaneMode) {
            textResId = com.android.internal.R.string.lockscreen_airplane_mode_on;
        } else {
            switch (mSimState) {
                case ABSENT:
                case UNKNOWN:
                case NOT_READY:
                    textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                    break;
                case PIN_REQUIRED:
                    textResId = com.android.internal.R.string.lockscreen_sim_locked_message;
                    break;
                case PUK_REQUIRED:
                    textResId = com.android.internal.R.string.lockscreen_sim_puk_locked_message;
                    break;
                case READY:
                    // If the state is ready, set the text as network name.
                    mCarrierText = mNetworkName;
                    break;
                case PERM_DISABLED:
                    textResId = com.android.internal.
                            R.string.lockscreen_permanent_disabled_sim_message_short;
                    break;
                case CARD_IO_ERROR:
                    textResId = com.android.internal.R.string.lockscreen_sim_error_message_short;
                    break;
                default:
                    textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                    break;
            }
        }

        if (textResId != 0) {
            mCarrierText = mContext.getString(textResId);
        }
    }

    private void setCarrierText() {
        updateCarrierText();

        for (TextView v : mMobileLabelViews) {
            v.setText(mCarrierText);
            v.setVisibility(View.VISIBLE);
        }
    }

    private void updateIconSet() {
        int voiceNetworkType = getVoiceNetworkType();
        int dataNetworkType =  getDataNetworkType();

        if (DEBUG) {
            Log.d(TAG, "updateIconSet, voice network type is: " + voiceNetworkType
                + "/" + TelephonyManager.getNetworkTypeName(voiceNetworkType)
                + ", data network type is: " + dataNetworkType
                + "/" + TelephonyManager.getNetworkTypeName(dataNetworkType));
        }

        int chosenNetworkType =
            ((dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
            ? voiceNetworkType : dataNetworkType);

        if (DEBUG) {
            Log.d(TAG, " chosenNetworkType=" + chosenNetworkType
                + " hspaDataDistinguishable=" + String.valueOf(mHspaDataDistinguishable)
                + " hspapDistinguishable=" + "false"
                + " showAtLeastThreeGees=" + String.valueOf(mShowAtLeastThreeGees));
        }

        int inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_MOBILE);
        TelephonyIcons.updateDataType(chosenNetworkType, mShowAtLeastThreeGees,
            mShow4GforLTE, mHspaDataDistinguishable, inetCondition);
    }

    protected void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (DEBUG) {
            Log.d(TAG, "updateSimState, sim state is: " + stateExtra);
        }

        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            mSimState = IccCardConstants.State.CARD_IO_ERROR;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)
                || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)
                || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                mSimState = IccCardConstants.State.PERSO_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }

        if (DEBUG) Log.d(TAG, "updateSimState: mSimState=" + mSimState);
        updateSimIcon();
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    private boolean hasService() {
        boolean retVal;
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch(mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    retVal = false;
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    retVal = mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                    break;
                default:
                    retVal = true;
            }
        } else {
            retVal = false;
        }
        if (DEBUG) Log.d(TAG, "hasService: mServiceState=" + mServiceState + " retVal=" + retVal);
        return retVal;
    }

    protected void updateAirplaneMode() {
        mAirplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    private void refreshLocale() {
        mLocale = mContext.getResources().getConfiguration().locale;
    }

    private final void updateTelephonySignalStrength() {
        if (DEBUG) {
            Log.d(TAG, "updateTelephonySignalStrength: hasService=" + hasService()
                    + " ss=" + mSignalStrength);
        }
        if (!hasService() &&
              (mDataServiceState != ServiceState.STATE_IN_SERVICE)) {
            if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: No Service");
            mPhoneSignalIconId = TelephonyIcons.getSignalNullIcon();
            mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
            mDataSignalIconId = mPhoneSignalIconId;
            mContentDescriptionPhoneSignal = TelephonyIcons.getSignalStrengthDes(0);
        } else {
            if (mSignalStrength == null || (mServiceState == null)) {
                if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: mSignalStrength == null mServiceState == null");
                mPhoneSignalIconId = TelephonyIcons.getSignalNullIcon();
                mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
                mDataSignalIconId = mPhoneSignalIconId;
                mContentDescriptionPhoneSignal = TelephonyIcons.getSignalStrengthDes(0);
            } else {
                int iconLevel;
                if (isCdma() && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel = iconLevel = mSignalStrength.getCdmaLevel();
                    if (DEBUG) {
                        Log.d(TAG, "updateTelephonySignalStrength:"
                            + " mAlwaysShowCdmaRssi=" + mAlwaysShowCdmaRssi
                            + " set to cdmaLevel=" + mSignalStrength.getCdmaLevel()
                            + " instead of level=" + mSignalStrength.getLevel());
                    }
                } else {
                    mLastSignalLevel = iconLevel = mSignalStrength.getLevel();
                    if (mShowRsrpSignalLevelforLTE) {
                        if (mServiceState.getDataNetworkType() ==
                                TelephonyManager.NETWORK_TYPE_LTE) {
                            int level = mSignalStrength.getAlternateLteLevel();
                            mLastSignalLevel = iconLevel = (level == -1 ? 0 : level);
                            Log.d(TAG, "updateTelephonySignalStrength, data type is lte, level = "
                                + level + " | " + mSignalStrength);
                        }
                    }
                }

                mPhoneSignalIconId = TelephonyIcons.getSignalStrengthIcon(mInetCondition,
                        iconLevel, isRoaming());
                mDataSignalIconId = mPhoneSignalIconId;
                mQSPhoneSignalIconId =
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH[mInetCondition][iconLevel];
                mContentDescriptionPhoneSignal = TelephonyIcons.getSignalStrengthDes(iconLevel);
                if (DEBUG) Log.d(TAG, "updateTelephonySignalStrength: iconLevel=" + iconLevel);
            }
        }

        if (DEBUG) {
            Log.d(TAG, "updateTelephonySignalStrength, No signal level."
                + " mPhoneSignalIconId = " + getResourceName(mPhoneSignalIconId)
                + " mDataSignalIconId = " + getResourceName(mDataSignalIconId)
                + " mQSPhoneSignalIconId = " + getResourceName(mQSPhoneSignalIconId)
                + " mContentDescriptionPhoneSignal = " + mContentDescriptionPhoneSignal);
        }
    }

    protected int inetConditionForNetwork(int networkType) {
        return (mInetCondition == 1 && mConnectedNetworkType == networkType) ? 1 : 0;
    }

    private final void updateDataNetType() {
        int inetCondition;
        mDataTypeIconId = mQSDataTypeIconId = 0;
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is a special 4g network not handled by telephony
            inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_WIMAX);
            mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_4g;
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[inetCondition];
            mContentDescriptionDataType = mContext.getString(
                    R.string.accessibility_data_connection_4g);
        } else {
            mDataTypeIconId = TelephonyIcons.getDataTypeIcon();
            mContentDescriptionDataType = TelephonyIcons.getDataTypeDesc();
            mQSDataTypeIconId = TelephonyIcons.getQSDataTypeIcon();
        }
        if (isCdma()) {
            if (isCdmaEri()) {
                mDataTypeIconId = TelephonyIcons.ROAMING_ICON;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
        } else if (mPhone.isNetworkRoaming()) {
            mDataTypeIconId = TelephonyIcons.ROAMING_ICON;
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
        }

        if(DEBUG) {
            Log.d(TAG, "updateDataNetType, mDataTypeIconId = " + getResourceName(mDataTypeIconId)
                + " mQSDataTypeIconId = " + getResourceName(mQSDataTypeIconId)
                + " mContentDescriptionDataType = " + mContentDescriptionDataType);
        }
    }

    boolean isCdmaEri() {
        if ((mServiceState != null)
                && (hasService() || (mDataServiceState == ServiceState.STATE_IN_SERVICE))) {
            final int iconIndex = mServiceState.getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mServiceState.getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRoaming() {
        if (isCdma()) {
            return isCdmaEri();
        } else {
            return mServiceState != null && mServiceState.getRoaming();
        }
    }

    private final void updateSimIcon() {
        if (DEBUG) Log.d(TAG,"In updateSimIcon simState= " + mSimState);
        if (mSimState ==  IccCardConstants.State.ABSENT) {
            mNoSimIconId = TelephonyIcons.getNoSimIcon();
        } else {
            mNoSimIconId = 0;
        }
        refreshViews();
    }

    private final void updateDataIcon() {
        int iconId = 0;
        boolean visible = true;
        if (mDataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            // If data network type is unknown do not display data icon
            visible = false;
        } else if (!isCdma()) {
            // GSM case, we have to check also the sim state
            if (mSimState == IccCardConstants.State.READY ||
                    mSimState == IccCardConstants.State.UNKNOWN) {
                mNoSim = false;
                if (mDataState == TelephonyManager.DATA_CONNECTED) {
                    iconId = TelephonyIcons.getDataActivity(mDataActivity);
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                iconId = TelephonyIcons.getNoSimIcon();
                mNoSim = true;
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
            if (mDataState == TelephonyManager.DATA_CONNECTED) {
                iconId = TelephonyIcons.getDataActivity(mDataActivity);
            } else {
                iconId = 0;
                visible = false;
            }
        }

        mDataDirectionIconId = iconId;
        mMobileActivityIconId = iconId;
        mDataConnected = visible;

        if(DEBUG) {
            Log.d(TAG, "updateDataIcon, mDataDirectionIconId = "
                + getResourceName(mDataDirectionIconId)
                + " mDataConnected = " + mDataConnected);
        }
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (DEBUG) {
            Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            if(mContext.getResources().getBoolean(com.android.internal.R.bool.config_display_rat) &&
                    mServiceState != null) {
                plmn = appendRatToNetworkName(plmn, mServiceState);
            }
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if(mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_spn_display_control)
                    && something){
                Log.d(TAG,"Do not display spn string when showPlmn and showSpn are both true"
                       + "and plmn string is not null");
            } else {
                if (something) {
                    str.append(mNetworkNameSeparator);
                }
                if(mContext.getResources().getBoolean(com.android.internal.R.bool.
                        config_display_rat) && mServiceState != null) {
                    spn = appendRatToNetworkName(spn, mServiceState);
                }
                str.append(spn);
                something = true;
            }
        }
        if (something) {
            mNetworkName = str.toString();
        } else {
            mNetworkName = mNetworkNameDefault;
        }
    }

    public String appendRatToNetworkName(String operator, ServiceState state) {
        String opeartorName = "";
        if (state.getDataRegState() == ServiceState.STATE_IN_SERVICE ||
                state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            int voiceNetType = state.getVoiceNetworkType();
            int dataNetType =  state.getDataNetworkType();
            int chosenNetType =
                    ((dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetType : dataNetType);
            TelephonyManager tm = (TelephonyManager)mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
            String ratString = tm.networkTypeToString(chosenNetType);
            opeartorName = new StringBuilder().append(operator).append(" ").append(ratString).
                    toString();
        } else {
            opeartorName = operator;
        }
        return opeartorName;
    }
    // ===== Wifi ===================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Log.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews();
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    protected void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiConnected;
            mWifiConnected = networkInfo != null && networkInfo.isConnected();
            // If Connected grab the signal strength and ssid
            if (mWifiConnected) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if (!mWifiConnected) {
                mWifiSsid = null;
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            mWifiLevel = WifiManager.calculateSignalLevel(
                    mWifiRssi, WifiIcons.WIFI_LEVEL_COUNT);
        }

        updateWifiIcons();
    }

    protected void updateWifiIcons() {
        int inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_WIFI);
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[inetCondition][mWifiLevel];
            mQSWifiIconId = WifiIcons.QS_WIFI_SIGNAL_STRENGTH[inetCondition][mWifiLevel];
            mContentDescriptionWifi = mContext.getString(
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]);
        } else {
            if (mDataAndWifiStacked) {
                mWifiIconId = 0;
                mQSWifiIconId = 0;
            } else {
                mWifiIconId = mWifiEnabled ? R.drawable.stat_sys_wifi_signal_null : 0;
                mQSWifiIconId = mWifiEnabled ? R.drawable.ic_qs_wifi_no_network : 0;
            }
            mContentDescriptionWifi = mContext.getString(R.string.accessibility_no_wifi);
        }
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }
        // OK, it's not in the connectionInfo; we have to go hunting for it
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }


    // ===== Wimax ===================================================================
    protected final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {
            int wimaxStatus = intent.getIntExtra(WimaxManagerConstants.EXTRA_4G_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mIsWimaxEnabled = (wimaxStatus ==
                    WimaxManagerConstants.NET_4G_STATE_ENABLED);
        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {
            mWimaxSignal = intent.getIntExtra(WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL, 0);
        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            mWimaxState = intent.getIntExtra(WimaxManagerConstants.EXTRA_WIMAX_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxExtraState = intent.getIntExtra(
                    WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxConnected = (mWimaxState ==
                    WimaxManagerConstants.WIMAX_STATE_CONNECTED);
            mWimaxIdle = (mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE);
        }
        updateDataNetType();
        updateWimaxIcons();
    }

    protected void updateWimaxIcons() {
        if (mIsWimaxEnabled) {
            if (mWimaxConnected) {
                int inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_WIMAX);
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[inetCondition][mWimaxSignal];
                mContentDescriptionWimax = mContext.getString(
                        AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignal]);
            } else {
                mWimaxIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mContentDescriptionWimax = mContext.getString(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxIconId = 0;
        }
    }

    // ===== Full or limited Internet connectivity ==================================

    protected void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: networkInfo=" + info);
            Log.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateIconSet();
        updateDataNetType();
        updateWimaxIcons();
        updateDataIcon();
        updateTelephonySignalStrength();
        updateWifiIcons();
    }


    // ===== Update the views =======================================================

    void refreshViews() {
        Context context = mContext;

        int combinedSignalIconId = 0;
        int combinedActivityIconId = 0;
        String combinedLabel = "";
        String wifiLabel = "";
        String mobileLabel = "";
        int N;
        final boolean emergencyOnly = isEmergencyOnly();

        if (!mHasMobileDataFeature) {
            mDataSignalIconId = mPhoneSignalIconId = 0;
            mQSPhoneSignalIconId = 0;
            mobileLabel = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mDataConnected) {
                mobileLabel = mNetworkName;
            } else if (mConnected || emergencyOnly) {
                if (hasService() || emergencyOnly) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel = mNetworkName;
                } else {
                    // Tablets, basically
                    mobileLabel = "";
                }
            } else {
                mobileLabel
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mDataConnected) {
                combinedSignalIconId = mDataSignalIconId;

                combinedLabel = mobileLabel;
                combinedSignalIconId = mDataSignalIconId; // set by updateDataIcon()
                mContentDescriptionCombinedSignal = mContentDescriptionDataType;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
                mWifiActivityIconId = 0; // no wifis, no bits
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
                switch (mWifiActivity) {
                case WifiManager.DATA_ACTIVITY_IN:
                    mWifiActivityIconId = R.drawable.stat_sys_signal_in;
                    break;
                case WifiManager.DATA_ACTIVITY_OUT:
                    mWifiActivityIconId = R.drawable.stat_sys_signal_out;
                    break;
                case WifiManager.DATA_ACTIVITY_INOUT:
                    mWifiActivityIconId = R.drawable.stat_sys_signal_inout;
                    break;
                case WifiManager.DATA_ACTIVITY_NONE:
                    mWifiActivityIconId = R.drawable.stat_sys_signal_none;
                    break;
                }
            }

            combinedActivityIconId = mWifiActivityIconId;
            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiIconId; // set by updateWifiIcons()
            mContentDescriptionCombinedSignal = mContentDescriptionWifi;
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mContentDescriptionCombinedSignal = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            combinedLabel = context.getString(R.string.ethernet_label);
        }

        if (mAirplaneMode &&
                (mServiceState == null || (!hasService() && !mServiceState.isEmergencyOnly()))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mContentDescriptionPhoneSignal = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = TelephonyIcons.FLIGHT_MODE_ICON;
            mPhoneSignalIconId = mDataSignalIconId = mDataTypeIconId = mQSDataTypeIconId = 0;
            mQSPhoneSignalIconId = 0;
            mNoSimIconId = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mContentDescriptionCombinedSignal = mContentDescriptionPhoneSignal;
                combinedSignalIconId = mDataSignalIconId;
            }
        }
        else if (!mDataConnected && !mWifiConnected && !mBluetoothTethered && !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId =
                mHasMobileDataFeature ? mDataSignalIconId : mWifiIconId;
            mContentDescriptionCombinedSignal = mHasMobileDataFeature
                ? mContentDescriptionDataType : mContentDescriptionWifi;
        }

        if (!mDataConnected) {
            int inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_MOBILE);
            if (DEBUG) Log.d(TAG, "refreshViews: Data not connected!! Set no data type icon / Roaming");
            mDataTypeIconId = 0;
            mQSDataTypeIconId = 0;
            if (isRoaming()) {
                mDataTypeIconId = TelephonyIcons.ROAMING_ICON;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
        }

        if (mDemoMode) {
            mQSWifiIconId = mDemoWifiLevel < 0 ? R.drawable.ic_qs_wifi_no_network
                    : WifiIcons.QS_WIFI_SIGNAL_STRENGTH[mDemoInetCondition][mDemoWifiLevel];
            mQSPhoneSignalIconId = mDemoMobileLevel < 0 ? R.drawable.ic_qs_signal_no_signal :
                    TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH[mDemoInetCondition][mDemoMobileLevel];
            mQSDataTypeIconId = mDemoQSDataTypeIconId;
        }

        if (!mAirplaneMode && mSimState == IccCardConstants.State.ABSENT) {
            // look again; your radios are now sim cards
            mPhoneSignalIconId = mDataSignalIconId = mDataTypeIconId = mQSDataTypeIconId = 0;
            mQSPhoneSignalIconId = 0;
        }

        if (DEBUG) {
            Log.d(TAG, "refreshViews connected={"
                    + (mWifiConnected?" wifi":"")
                    + (mDataConnected?" data":"")
                    + " } level="
                    + ((mSignalStrength == null)?"??":Integer.toString(mSignalStrength.getLevel()))
                    + " combinedSignalIconId=0x"
                    + Integer.toHexString(combinedSignalIconId)
                    + "/" + getResourceName(combinedSignalIconId)
                    + " combinedActivityIconId=0x" + Integer.toHexString(combinedActivityIconId)
                    + " mobileLabel=" + mobileLabel
                    + " wifiLabel=" + wifiLabel
                    + " emergencyOnly=" + emergencyOnly
                    + " combinedLabel=" + combinedLabel
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mDataActivity=" + mDataActivity
                    + " mPhoneSignalIconId=0x"
                    + Integer.toHexString(mPhoneSignalIconId)
                    + "/" + getResourceName(mPhoneSignalIconId)
                    + " mQSPhoneSignalIconId=0x"
                    + Integer.toHexString(mQSPhoneSignalIconId)
                    + "/" + getResourceName(mQSPhoneSignalIconId)
                    + " mDataDirectionIconId=0x"
                    + Integer.toHexString(mDataDirectionIconId)
                    + "/" + getResourceName(mDataDirectionIconId)
                    + " mDataSignalIconId=0x"
                    + Integer.toHexString(mDataSignalIconId)
                    + "/" + getResourceName(mDataSignalIconId)
                    + " mDataTypeIconId=0x"
                    + Integer.toHexString(mDataTypeIconId)
                    + "/" + getResourceName(mDataTypeIconId)
                    + " mQSDataTypeIconId=0x"
                    + Integer.toHexString(mQSDataTypeIconId)
                    + "/" + getResourceName(mQSDataTypeIconId)
                    + " mNoSimIconId=0x"
                    + Integer.toHexString(mNoSimIconId)
                    + "/" + getResourceName(mNoSimIconId)
                    + " mWifiIconId=0x"
                    + Integer.toHexString(mWifiIconId)
                    + "/" + getResourceName(mWifiIconId)
                    + " mQSWifiIconId=0x"
                    + Integer.toHexString(mQSWifiIconId)
                    + "/" + getResourceName(mQSWifiIconId)
                    + " mWifiActivityIconId=0x"
                    + Integer.toHexString(mWifiActivityIconId)
                    + "/" + getResourceName(mWifiActivityIconId)
                    + " mBluetoothTetherIconId=0x"
                    + Integer.toHexString(mBluetoothTetherIconId)
                    + "/" + getResourceName(mBluetoothTetherIconId));
        }

        // update QS
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        if (mLastPhoneSignalIconId          != mPhoneSignalIconId
         || mLastDataDirectionOverlayIconId != combinedActivityIconId
         || mLastWifiIconId                 != mWifiIconId
         || mLastInetCondition              != mInetCondition
         || mLastWimaxIconId                != mWimaxIconId
         || mLastDataTypeIconId             != mDataTypeIconId
         || mLastAirplaneMode               != mAirplaneMode
         || mLastLocale                     != mLocale
         || mLastConnectedNetworkType       != mConnectedNetworkType
         || mLastSimIconId                  != mNoSimIconId
         || mLastMobileActivityIconId       != mMobileActivityIconId)
        {
            // NB: the mLast*s will be updated later
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        if (mLastLocale != mLocale) {
            mLastLocale = mLocale;
        }

        // the phone icon on phones
        if (mLastPhoneSignalIconId != mPhoneSignalIconId) {
            mLastPhoneSignalIconId = mPhoneSignalIconId;
            N = mPhoneSignalIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mPhoneSignalIconViews.get(i);
                if (mPhoneSignalIconId == 0) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mPhoneSignalIconId);
                    v.setContentDescription(mContentDescriptionPhoneSignal);
                }
            }
        }

        if (mLastMobileActivityIconId != mMobileActivityIconId) {
            mLastMobileActivityIconId = mMobileActivityIconId;
        }

        // the data icon on phones
        if (mLastDataDirectionIconId != mDataDirectionIconId) {
            mLastDataDirectionIconId = mDataDirectionIconId;
            N = mDataDirectionIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mDataDirectionIconViews.get(i);
                v.setImageResource(mDataDirectionIconId);
                v.setContentDescription(mContentDescriptionDataType);
            }
        }

        if (mLastSimIconId != mNoSimIconId) {
            mLastSimIconId = mNoSimIconId;
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
            N = mWifiIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mWifiIconViews.get(i);
                if (mWifiIconId == 0) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mWifiIconId);
                    v.setContentDescription(mContentDescriptionWifi);
                }
            }
        }

        if (mLastInetCondition != mInetCondition) {
            mLastInetCondition = mInetCondition;
        }

        if (mLastConnectedNetworkType != mConnectedNetworkType) {
            mLastConnectedNetworkType = mConnectedNetworkType;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
            N = mWimaxIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mWimaxIconViews.get(i);
                if (mWimaxIconId == 0) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mWimaxIconId);
                    v.setContentDescription(mContentDescriptionWimax);
                }
            }
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
            N = mCombinedSignalIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mCombinedSignalIconViews.get(i);
                v.setImageResource(combinedSignalIconId);
                v.setContentDescription(mContentDescriptionCombinedSignal);
            }
        }

        // the data network type overlay
        if (mLastDataTypeIconId != mDataTypeIconId) {
            mLastDataTypeIconId = mDataTypeIconId;
            N = mDataTypeIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mDataTypeIconViews.get(i);
                if (mDataTypeIconId == 0) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(mDataTypeIconId);
                    v.setContentDescription(mContentDescriptionDataType);
                }
            }
        }

        // the data direction overlay
        if (mLastDataDirectionOverlayIconId != combinedActivityIconId) {
            if (DEBUG) {
                Log.d(TAG, "changing data overlay icon id to "
                        + combinedActivityIconId);
            }
            mLastDataDirectionOverlayIconId = combinedActivityIconId;
            N = mDataDirectionOverlayIconViews.size();
            for (int i = 0; i < N; i++) {
                final ImageView v = mDataDirectionOverlayIconViews.get(i);
                if (combinedActivityIconId == 0) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                    v.setImageResource(combinedActivityIconId);
                    v.setContentDescription(mContentDescriptionDataType);
                }
            }
        }

        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // mobile label
        N = mMobileLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(mobileLabel);
            if ("".equals(mobileLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // e-call label
        N = mEmergencyViews.size();
        for (int i=0; i<N; i++) {
            StatusBarHeaderView v = mEmergencyViews.get(i);
            v.setShowEmergencyCallsOnly(emergencyOnly);
        }

        setCarrierText();
    }

    public int getVoiceNetworkType() {
        if (mServiceState == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mServiceState.getVoiceNetworkType();
    }

    public int getDataNetworkType() {
        if (mServiceState == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mServiceState.getDataNetworkType();
    }

    public int getGsmSignalLevel() {
        if (mSignalStrength == null) {
            return SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
        return mSignalStrength.getGsmLevel();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NetworkController state:");
        pw.println(String.format("  %s network type %d (%s)",
                mConnected?"CONNECTED":"DISCONNECTED",
                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        pw.print("  hasVoiceCallingFeature()=");
        pw.println(hasVoiceCallingFeature());
        pw.print("  hasService()=");
        pw.println(hasService());
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected=");
        pw.println(mDataConnected);
        pw.print("  mSimState=");
        pw.println(mSimState);
        pw.print("  mPhoneState=");
        pw.println(mPhoneState);
        pw.print("  mDataState=");
        pw.println(mDataState);
        pw.print("  mDataActivity=");
        pw.println(mDataActivity);
        pw.print("  mDataNetType=");
        pw.print(mDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mDataNetType));
        pw.print("  mServiceState=");
        pw.println(mServiceState);
        pw.print("  mSignalStrength=");
        pw.println(mSignalStrength);
        pw.print("  mLastSignalLevel=");
        pw.println(mLastSignalLevel);
        pw.print("  mNetworkName=");
        pw.println(mNetworkName);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mPhoneSignalIconId));
        pw.print("/");
        pw.print("  mQSPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mQSPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mPhoneSignalIconId));
        pw.print("  mDataDirectionIconId=");
        pw.print(Integer.toHexString(mDataDirectionIconId));
        pw.print("/");
        pw.println(getResourceName(mDataDirectionIconId));
        pw.print("  mDataSignalIconId=");
        pw.print(Integer.toHexString(mDataSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mDataSignalIconId));
        pw.print("  mDataTypeIconId=");
        pw.print(Integer.toHexString(mDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mDataTypeIconId));
        pw.print("  mQSDataTypeIconId=");
        pw.print(Integer.toHexString(mQSDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mQSDataTypeIconId));

        pw.println("  - wifi ------");
        pw.print("  mWifiEnabled=");
        pw.println(mWifiEnabled);
        pw.print("  mWifiConnected=");
        pw.println(mWifiConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiIconId=0x%08x/%s",
                    mWifiIconId, getResourceName(mWifiIconId)));
        pw.println(String.format("  mQSWifiIconId=0x%08x/%s",
                    mQSWifiIconId, getResourceName(mQSWifiIconId)));
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled="); pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected="); pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle="); pw.println(mWimaxIdle);
            pw.println(String.format("  mWimaxIconId=0x%08x/%s",
                        mWimaxIconId, getResourceName(mWimaxIconId)));
            pw.println(String.format("  mWimaxSignal=%d", mWimaxSignal));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);

        pw.println("  - icons ------");
        pw.print("  mLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mLastPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastPhoneSignalIconId));
        pw.print("  mLastDataDirectionIconId=0x");
        pw.print(Integer.toHexString(mLastDataDirectionIconId));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionIconId));
        pw.print("  mLastDataDirectionOverlayIconId=0x");
        pw.print(Integer.toHexString(mLastDataDirectionOverlayIconId));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionOverlayIconId));
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mLastCombinedSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastCombinedSignalIconId));
        pw.print("  mLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mLastDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mLastDataTypeIconId));
        pw.print("  mLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
    }

    protected String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private boolean mDemoMode;
    private int mDemoInetCondition;
    private int mDemoWifiLevel;
    private int mDemoWifiActivityId;
    private int mDemoDataTypeIconId;
    private int mDemoQSDataTypeIconId;
    private int mDemoMobileLevel;
    private int mDemoMobileActivityId;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoWifiLevel = mWifiLevel;
            mDemoWifiActivityId = mWifiActivityIconId;
            mDemoInetCondition = mInetCondition;
            mDemoDataTypeIconId = mDataTypeIconId;
            mDemoQSDataTypeIconId = mQSDataTypeIconId;
            mDemoMobileLevel = mLastSignalLevel;
            mDemoMobileActivityId = mMobileActivityIconId;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
            refreshViews();
        } else if (mDemoMode && command.equals(COMMAND_NETWORK)) {
            String airplane = args.getString("airplane");
            if (airplane != null) {
                boolean show = airplane.equals("show");
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setIsAirplaneMode(show, TelephonyIcons.FLIGHT_MODE_ICON);
                }
            }
            String fully = args.getString("fully");
            if (fully != null) {
                mDemoInetCondition = Boolean.parseBoolean(fully) ? 1 : 0;
            }
            String wifi = args.getString("wifi");
            if (wifi != null) {
                boolean show = wifi.equals("show");
                String level = args.getString("level");
                if (level != null) {
                    mDemoWifiLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), WifiIcons.WIFI_LEVEL_COUNT - 1);
                }
                int iconId = mDemoWifiLevel < 0 ? R.drawable.stat_sys_wifi_signal_null
                        : WifiIcons.WIFI_SIGNAL_STRENGTH[mDemoInetCondition][mDemoWifiLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setWifiIndicators(
                            show,
                            iconId,
                            mDemoWifiActivityId,
                            "Demo");
                }
                refreshViews();
            }
            String mobile = args.getString("mobile");
            if (mobile != null) {
                boolean show = mobile.equals("show");
                String datatype = args.getString("datatype");
                if (datatype != null) {
                    mDemoDataTypeIconId =
                            datatype.equals("1x") ? TelephonyIcons.ICON_1X :
                            datatype.equals("3g") ? TelephonyIcons.ICON_3G :
                            datatype.equals("4g") ? TelephonyIcons.ICON_4G :
                            datatype.equals("e") ? R.drawable.stat_sys_data_fully_connected_e :
                            datatype.equals("g") ? R.drawable.stat_sys_data_fully_connected_g :
                            datatype.equals("h") ? R.drawable.stat_sys_data_fully_connected_h :
                            datatype.equals("lte") ? TelephonyIcons.ICON_LTE :
                            datatype.equals("roam") ? TelephonyIcons.ROAMING_ICON :
                            0;
                    mDemoQSDataTypeIconId =
                            datatype.equals("1x") ? TelephonyIcons.QS_ICON_1X :
                            datatype.equals("3g") ? TelephonyIcons.QS_ICON_3G :
                            datatype.equals("4g") ? TelephonyIcons.QS_ICON_4G :
                            datatype.equals("e") ? R.drawable.ic_qs_signal_e :
                            datatype.equals("g") ? R.drawable.ic_qs_signal_g :
                            datatype.equals("h") ? R.drawable.ic_qs_signal_h :
                            datatype.equals("lte") ? TelephonyIcons.QS_ICON_LTE :
                            datatype.equals("roam") ? R.drawable.ic_qs_signal_r :
                            0;
                }
                int[][] icons = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH;
                String level = args.getString("level");
                if (level != null) {
                    mDemoMobileLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), icons[0].length - 1);
                }
                int iconId = mDemoMobileLevel < 0 ? R.drawable.stat_sys_signal_null :
                        icons[mDemoInetCondition][mDemoMobileLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setMobileDataIndicators(
                            show,
                            iconId,
                            mDemoMobileActivityId,
                            mDemoDataTypeIconId,
                            "Demo",
                            "Demo",
                            mDemoDataTypeIconId == TelephonyIcons.ROAMING_ICON,
                            isTypeIconWide(mDemoDataTypeIconId),
                            mNoSimIconId);
                }
                refreshViews();
            }
        }
    }
}
