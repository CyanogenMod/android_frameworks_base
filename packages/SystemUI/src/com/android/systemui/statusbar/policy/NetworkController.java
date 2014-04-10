/*
 * Copyright (c) 2011-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.DemoMode;
import com.android.systemui.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NetworkController extends BroadcastReceiver implements DemoMode {
    // debug
    static final String TAG = "StatusBar.NetworkController";
    static final boolean DEBUG = true;
    static final boolean CHATTY = true; // additional diagnostics, but not logspew

    private static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_signal_flightmode;

    boolean mAppOpsStrictEnabled = false;

    // telephony
    boolean mHspaDataDistinguishable;
    private TelephonyManager mPhone;
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
    int mPhoneSignalIconId;
    int mQSPhoneSignalIconId;
    int mDataDirectionIconId; // data + data direction on phones
    int mMobileActivityIconId;
    int mDataSignalIconId;
    int mDataTypeIconId;
    int mQSDataTypeIconId;
    int mAirplaneIconId;
    int mNoSimIconId;
    int mLastSimIconId;
    int mLastMobileActivityIconId;
    boolean mDataActive;
    int mLastSignalLevel;
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mAlwaysShowCdmaRssi = false;
    boolean mShow4GforLTE = false;

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
    int mWifiActivityIconId = 0;
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
    protected int mInetCondition = 0;
    protected static final int INET_CONDITION_THRESHOLD = 50;

    protected boolean mAirplaneMode = false;
    protected boolean mLastAirplaneMode = true;

    private Locale mLocale = null;
    private Locale mLastLocale = null;

    // our ui
    protected Context mContext;
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mEmergencyLabelViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();
    int mLastPhoneSignalIconId = -1;
    int mLastDataDirectionIconId = -1;
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;
    int mLastDataTypeIconId = -1;
    String mLastCombinedLabel = "";

    protected boolean mHasMobileDataFeature;

    boolean mDataAndWifiStacked = false;

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
                String contentDescription);
        void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
                int typeIcon, String contentDescription, String typeContentDescription,
                int noSimIcon);
        void setIsAirplaneMode(boolean is, int airplaneIcon);
    }

    public interface NetworkSignalChangedCallback {
        void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description);
        void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description);
        void onAirplaneModeChanged(boolean enabled);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkController(Context context) {
        mContext = context;
        final Resources res = context.getResources();

        TelephonyIcons.initAll(context);
        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        mShowPhoneRSSIForData = res.getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = res.getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = res.getBoolean(
                com.android.internal.R.bool.config_alwaysUseCdmaRssi);
        mShow4GforLTE = mContext.getResources().getBoolean(
                R.bool.config_show4GForLTE);
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

        createWifiHandler();

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
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

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addEmergencyLabelView(TextView v) {
        mEmergencyLabelViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        if (mDemoMode) return;
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature || mAppOpsStrictEnabled),
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
                    mNoSimIconId);
            Log.d(TAG, "refreshSignalCluster - setMobileDataIndicators: "
                    + " mHasMobileDataFeature = " + String.valueOf(mHasMobileDataFeature)
                    + " mPhoneSignalIconId = " + getResourceName(mPhoneSignalIconId)
                    + " mDataSignalIconId = " + getResourceName(mDataSignalIconId)
                    + " mMobileActivityIconId = " + getResourceName(mMobileActivityIconId)
                    + " mDataTypeIconId = " + getResourceName(mDataTypeIconId)
                    + " mNoSimIconId = " + getResourceName(mNoSimIconId));
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
        cb.onWifiSignalChanged(wifiEnabled, mQSWifiIconId, wifiIn, wifiOut,
                mContentDescriptionWifi, wifiDesc);

        boolean mobileIn = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_IN);
        boolean mobileOut = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_OUT);
        if (isEmergencyOnly()) {
            cb.onMobileDataSignalChanged(false, mQSPhoneSignalIconId,
                    mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                    mContentDescriptionDataType, null);
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(true, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mNetworkName);
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(mHasMobileDataFeature, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mNetworkName);
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
            updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            refreshViews();
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshLocale();
            if (mContext.getResources().getBoolean(R.bool.config_monitor_locale_change)) {
                // parse the string to current language string in public resources
                updateNetworkName(mNetworkName);
            }
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
        int voiceNetworkType = mServiceState.getVoiceNetworkType();
        int dataNetworkType =  mServiceState.getDataNetworkType();

        if (DEBUG) {
            Log.d(TAG, "updateIconSet, voice network type is: " + voiceNetworkType
                + "/" + TelephonyManager.getNetworkTypeName(voiceNetworkType)
                + ", data network type is: " + dataNetworkType
                + "/" + TelephonyManager.getNetworkTypeName(dataNetworkType));
        }

        int chosenNetworkType =
            ((dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
            ? voiceNetworkType : dataNetworkType);

        Log.d(TAG, " chosenNetworkType=" + chosenNetworkType
            + " hspaDataDistinguishable=" + String.valueOf(mHspaDataDistinguishable)
            + " hspapDistinguishable=" + "false"
            + " showAtLeastThreeGees=" + String.valueOf(mShowAtLeastThreeGees));

        TelephonyIcons.updateDataType(chosenNetworkType, mShowAtLeastThreeGees,
            mShow4GforLTE, mHspaDataDistinguishable, mInetCondition);
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
        updateSimIcon();
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    private boolean hasService() {
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch(mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    protected void updateAirplaneMode() {
        mAirplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    private void refreshLocale() {
        mLocale = mContext.getResources().getConfiguration().locale;
    }

    private final void updateTelephonySignalStrength() {
        if (!hasService() &&
                (mDataServiceState != ServiceState.STATE_IN_SERVICE)) {
            if (DEBUG) Log.d(TAG, " No service");
            mPhoneSignalIconId = TelephonyIcons.getSignalNullIcon();
            mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
            mDataSignalIconId = mPhoneSignalIconId;
        } else {
            if ((mSignalStrength == null) || (mServiceState == null)) {
                if (DEBUG) {
                    Log.d(TAG, " Null object, mSignalStrength= " + mSignalStrength
                            + " mServiceState " + mServiceState);

                }
                mPhoneSignalIconId = TelephonyIcons.getSignalNullIcon();
                mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
                mDataSignalIconId = mPhoneSignalIconId;

                mContentDescriptionPhoneSignal = TelephonyIcons.getSignalStrengthDes(0);
            } else {
                int iconLevel;
                if (isCdma() && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel = iconLevel = mSignalStrength.getCdmaLevel();
                    if(DEBUG) Log.d(TAG, "mAlwaysShowCdmaRssi=" + mAlwaysShowCdmaRssi
                            + " set to cdmaLevel=" + mSignalStrength.getCdmaLevel()
                            + " instead of level=" + mSignalStrength.getLevel());
                } else {
                    mLastSignalLevel = iconLevel = mSignalStrength.getLevel();
                }

                mPhoneSignalIconId = TelephonyIcons.getSignalStrengthIcon(mInetCondition,
                        iconLevel, isRoaming());
                mDataSignalIconId = mPhoneSignalIconId;
                mQSPhoneSignalIconId =
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH[mInetCondition][iconLevel];
                mContentDescriptionPhoneSignal = TelephonyIcons.getSignalStrengthDes(iconLevel);
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

    private boolean isRoaming() {
        return (isCdma() && isCdmaEri()) || mPhone.isNetworkRoaming();
    }

    private final void updateDataNetType() {
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is a special 4g network not handled by telephony
            mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_4g;
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[mInetCondition];
            mContentDescriptionDataType = mContext.getString(
                    R.string.accessibility_data_connection_4g);
        } else {
            mDataTypeIconId = TelephonyIcons.getDataTypeIcon();
            mContentDescriptionDataType = TelephonyIcons.getDataTypeDesc();
            mQSDataTypeIconId = TelephonyIcons.getQSDataTypeIcon();
        }

        if (isCdma()) {
            if (isCdmaEri()) {
                mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
        } else if (mPhone.isNetworkRoaming()) {
                mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
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

    private final void updateSimIcon() {
        Log.d(TAG,"In updateSimIcon simState= " + mSimState);
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
                if (mDataState == TelephonyManager.DATA_CONNECTED) {
                    iconId = TelephonyIcons.getDataActivity(mDataActivity);
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                iconId = TelephonyIcons.getNoSimIcon();
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
            Log.d(TAG, "updateNetworkName, showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
            something = true;
        }
        if (something) {
            mNetworkName = str.toString();
        } else {
            mNetworkName = mNetworkNameDefault;
        }

        if (mContext.getResources().getBoolean(R.bool.config_monitor_locale_change)) {
            // parse the string to current language string in public resources
            updateNetworkName(mNetworkName);
        }
    }

    protected void updateNetworkName(String networkName) {
        if (networkName != null) {
            mNetworkName = android.util.NativeTextHelper.getInternalLocalString(mContext,
                    networkName,
                    R.array.origin_carrier_names,
                    R.array.locale_carrier_names);
        }
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
            // If we just connected, grab the inintial signal strength and ssid
            if (mWifiConnected && !wasConnected) {
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
        mAppOpsStrictEnabled = AppOpsManager.isStrictEnable();
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mQSWifiIconId = WifiIcons.QS_WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
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

        if (DEBUG) {
            Log.d(TAG, "updateWifiIcons, mWifiIconId = " + getResourceName(mWifiIconId)
                + " mQSWifiIconId = " + getResourceName(mQSWifiIconId));
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
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mInetCondition][mWimaxSignal];
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
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_in;
                        break;
                    case WifiManager.DATA_ACTIVITY_OUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_out;
                        break;
                    case WifiManager.DATA_ACTIVITY_INOUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_inout;
                        break;
                    case WifiManager.DATA_ACTIVITY_NONE:
                        mWifiActivityIconId = 0;
                        break;
                }
            }

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
            mAirplaneIconId = FLIGHT_MODE_ICON;
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
            Log.d(TAG, "refreshViews: Data not connected!! Set no data type icon / Roaming");
            mDataTypeIconId = 0;
            mQSDataTypeIconId = 0;
            if (isCdma()) {
                if (isCdmaEri()) {
                    mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                    mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
                }
            } else if (mPhone.isNetworkRoaming()) {
                mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
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
         || mLastWifiIconId                 != mWifiIconId
         || mLastWimaxIconId                != mWimaxIconId
         || mLastDataTypeIconId             != mDataTypeIconId
         || mLastAirplaneMode               != mAirplaneMode
         || mLastLocale                     != mLocale
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
        }
        if (mLastMobileActivityIconId != mMobileActivityIconId) {
            mLastMobileActivityIconId = mMobileActivityIconId;
        }

        // the data icon on phones
        if (mLastDataDirectionIconId != mDataDirectionIconId) {
            mLastDataDirectionIconId = mDataDirectionIconId;
        }

        if (mLastSimIconId != mNoSimIconId) {
            mLastSimIconId = mNoSimIconId;
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
        }

        // the data network type overlay
        if (mLastDataTypeIconId != mDataTypeIconId) {
            mLastDataTypeIconId = mDataTypeIconId;
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
        N = mEmergencyLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mEmergencyLabelViews.get(i);
            if (!emergencyOnly) {
                v.setVisibility(View.GONE);
            } else {
                v.setText(mobileLabel); // comes from the telephony stack
                v.setVisibility(View.VISIBLE);
            }
        }

        setCarrierText();
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
    private int mDemoDataTypeIconId;
    private int mDemoMobileLevel;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoWifiLevel = mWifiLevel;
            mDemoInetCondition = mInetCondition;
            mDemoDataTypeIconId = mDataTypeIconId;
            mDemoMobileLevel = mLastSignalLevel;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        } else if (mDemoMode && command.equals(COMMAND_NETWORK)) {
            String airplane = args.getString("airplane");
            if (airplane != null) {
                boolean show = airplane.equals("show");
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setIsAirplaneMode(show, FLIGHT_MODE_ICON);
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
                            0,
                            iconId,
                            "Demo");
                }
            }
            String mobile = args.getString("mobile");
            if (mobile != null) {
                boolean show = mobile.equals("show");
                String datatype = args.getString("datatype");
                if (datatype != null) {
                    mDemoDataTypeIconId =
                            datatype.equals("1x") ? R.drawable.stat_sys_data_fully_connected_1x :
                            datatype.equals("3g") ? R.drawable.stat_sys_data_fully_connected_3g :
                            datatype.equals("4g") ? R.drawable.stat_sys_data_fully_connected_4g :
                            datatype.equals("e") ? R.drawable.stat_sys_data_fully_connected_e :
                            datatype.equals("g") ? R.drawable.stat_sys_data_fully_connected_g :
                            datatype.equals("h") ? R.drawable.stat_sys_data_fully_connected_h :
                            datatype.equals("lte") ? R.drawable.stat_sys_data_fully_connected_lte :
                            datatype.equals("roam")
                                    ? R.drawable.stat_sys_data_fully_connected_roam :
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
                            0,
                            mDemoDataTypeIconId,
                            "Demo",
                            "Demo",
                            mNoSimIconId);
                }
            }
        }
    }
}
