/*
 * Copyright (c) 2012-2013 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo.DetailedState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.provider.Settings;


import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.AsyncChannel;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Track the state of mobile data connectivity. This is done by
 * receiving broadcast intents from the Phone process whenever
 * the state of data connectivity changes.
 *
 * {@hide}
 */
public class MobileDataStateTracker extends BaseNetworkStateTracker {

    private static final String TAG = "MobileDataStateTracker";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private int mPhoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
    private PhoneConstants.DataState[] mMobileDataState
            = new  PhoneConstants.DataState[mPhoneCount];
    private ITelephony mPhoneService;
    private ITelephonyMSim mMSimPhoneService;

    private String mApnType;
    private NetworkInfo[] mNetworkInfo = new NetworkInfo[mPhoneCount];
    private boolean[] mTeardownRequested = new boolean[mPhoneCount];
    private Handler mTarget;
    private Context mContext;
    private LinkProperties[] mLinkProperties = new LinkProperties[mPhoneCount];
    private LinkCapabilities[] mLinkCapabilities = new LinkCapabilities[mPhoneCount];
    private boolean[] mPrivateDnsRouteSet = new boolean[mPhoneCount];
    private boolean[] mDefaultRouteSet = new boolean[mPhoneCount];

    // NOTE: these are only kept for debugging output; actual values are
    // maintained in DataConnectionTracker.
    protected boolean[] mUserDataEnabled = new boolean[mPhoneCount];
    protected boolean[] mPolicyDataEnabled = new boolean[mPhoneCount];

    private Handler mHandler;
    private AsyncChannel[] mDataConnectionTrackerAc = new AsyncChannel[mPhoneCount];

    private AtomicBoolean mIsCaptivePortal = new AtomicBoolean(false);

    private SignalStrength[] mSignalStrength = new SignalStrength[mPhoneCount];

    private SamplingDataTracker[] mSamplingDataTracker = new SamplingDataTracker[mPhoneCount];

    private static final int UNKNOWN = LinkQualityInfo.UNKNOWN_INT;

    private static int mSubscription;
    private final PhoneStateListener[] mPhoneStateListener = new PhoneStateListener[mPhoneCount];

    ContentResolver contentResolver = null;


    private Messenger[] mMessengerList = new Messenger[mPhoneCount];

    /**
     * Create a new MobileDataStateTracker
     * @param netType the ConnectivityManager network type
     * @param tag the name of this network
     */
    public MobileDataStateTracker(int netType, String tag) {

        for (int i = 0; i < mPhoneCount; i++) {
            mNetworkInfo[i] = new NetworkInfo(netType,
                    MSimTelephonyManager.getDefault().getNetworkType(i), tag,
                    MSimTelephonyManager.getDefault().getNetworkTypeName(i), i);

            mTeardownRequested[i] = false;
            mPrivateDnsRouteSet[i] = false;
            mDefaultRouteSet[i] = false;
            mUserDataEnabled[i] = true;
            mPolicyDataEnabled[i] = true;
            mSamplingDataTracker[i] = new SamplingDataTracker();
        }

        mApnType = networkTypeToApnType(netType);

    }

    /**
     * Begin monitoring data connectivity.
     *
     * @param context is the current Android context
     * @param target is the Hander to which to return the events.
     */
    public void startMonitoring(Context context, Handler target) {
        mTarget = target;
        mContext = context;

        mHandler = new MdstHandler(target.getLooper(), this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);

        mContext.registerReceiver(new MobileDataStateReceiver(), filter);

        MSimTelephonyManager tm = (MSimTelephonyManager)mContext.getSystemService(
                Context.MSIM_TELEPHONY_SERVICE);

        for(int i = 0; i < mPhoneCount; i++) {
            mMobileDataState[i] = PhoneConstants.DataState.DISCONNECTED;

            mPhoneStateListener[i] = getPhoneStateListner(i);
            tm.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }

        if (contentResolver == null && mApnType == PhoneConstants.APN_TYPE_DEFAULT) {
            log("Register contentobserver");
            contentResolver = mContext.getContentResolver();
            Uri defaultDataUri = Settings.Global
                .getUriFor(Settings.Global.MULTI_SIM_DEFAULT_DATA_CALL_SUBSCRIPTION);
            Uri tempDataUri = Settings.Global
                .getUriFor(Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION);
            contentResolver.registerContentObserver(defaultDataUri, false,
                    new MultiSimObserver(mHandler));
            contentResolver.registerContentObserver(tempDataUri, false,
                    new MultiSimObserver(mHandler));
        }

    }


    private PhoneStateListener getPhoneStateListner(final int i) {
        return (new PhoneStateListener(i) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                mSignalStrength[i] = signalStrength;
            }
        });
    }

    private class MultiSimObserver extends ContentObserver{

        public MultiSimObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            onChange(selfChange,null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            //super.onChange(selfChange, uri);
            try {
                log("onChange hit, uri = "+uri);
                log("onChange hit, new Default DDS = "+Settings .Global
                        .getInt(mContext.getContentResolver()
                            , Settings.Global.MULTI_SIM_DEFAULT_DATA_CALL_SUBSCRIPTION));
                log("onChange hit, new temp DDS = "+Settings.Global
                        .getInt(mContext.getContentResolver()
                            , Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION));
            } catch (Exception e) {
                log("Exception = "+e);
                e.printStackTrace();
            }
        }

    }

    class MdstHandler extends Handler {
        private MobileDataStateTracker mMdst;

        MdstHandler(Looper looper, MobileDataStateTracker mdst) {
            super(looper);
            mMdst = mdst;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        if (VDBG) {
                            mMdst.log("MdstHandler connected");
                        }
                        AsyncChannel tracker = (AsyncChannel) msg.obj;
                        int subId = 0;
                        mMdst.log("MDST AsyncChannel="+tracker);

                        for(int i =0; i < mPhoneCount; i++) {
                            if( mMdst.mMessengerList[i] == msg.replyTo) {
                                mMdst.log("This tracker is connected to sub=" + i);
                                mMdst.mDataConnectionTrackerAc[i] = tracker;
                            }
                        }

                    } else {
                        if (VDBG) {
                            mMdst.log("MdstHandler %s NOT connected error=" + msg.arg1);
                        }
                    }
                    break;

                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    if (VDBG) mMdst.log("Disconnected from DataStateTracker");
                    mMdst.log("CMD_CHANNEL_DISCONNECTED = " + msg);

                    for(int i = 0; i < mPhoneCount; i++) {
                        if (msg.obj == mMdst.mDataConnectionTrackerAc[i]) {
                            mMdst.mDataConnectionTrackerAc[i] = null;
                            mMdst.log("CMD_CHANNEL_DISCONNECTED for subId = " + i);
                        }
                    }
                    break;
                default: {
                    if (VDBG) mMdst.log("Ignorning unknown message=" + msg);
                    break;
                }
            }
        }
    }

    public boolean isPrivateDnsRouteSet() {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        return mPrivateDnsRouteSet[defaultDataSub];
    }

    public boolean isPrivateDnsRouteSet(int subId) {
        return mPrivateDnsRouteSet[subId];
    }

    public void privateDnsRouteSet(boolean enabled) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        mPrivateDnsRouteSet[defaultDataSub] = enabled;
    }

    public void privateDnsRouteSet(boolean enabled, int subId) {
        mPrivateDnsRouteSet[subId] = enabled;
    }

    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo[MSimTelephonyManager.getDefault().getDefaultDataSubscription()];
    }

    public NetworkInfo getNetworkInfo(int subId) {
        return mNetworkInfo[subId];
    }

    public boolean isDefaultRouteSet() {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        return mDefaultRouteSet[defaultDataSub];
    }

    public boolean isDefaultRouteSet(int subId) {
        return mDefaultRouteSet[subId];
    }

    public void defaultRouteSet(boolean enabled) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        mDefaultRouteSet[defaultDataSub] = enabled;
    }

    public void defaultRouteSet(boolean enabled, int subId) {
        mDefaultRouteSet[subId] = enabled;
    }

    /**
     * This is not implemented.
     */
    public void releaseWakeLock() {
    }

    private void updateLinkProperitesAndCapatilities(Intent intent) {
        int index = MSimTelephonyManager.getDefault().getDefaultDataSubscription();

        mLinkProperties[index] = intent.getParcelableExtra(
                PhoneConstants.DATA_LINK_PROPERTIES_KEY);
        if (mLinkProperties[index] == null) {
            loge("CONNECTED event did not supply link properties.");
            mLinkProperties[index] = new LinkProperties();
        }
        mLinkProperties[index].setMtu(mContext.getResources().getInteger(
                com.android.internal.R.integer.config_mobile_mtu));
        mLinkCapabilities[index] = intent.getParcelableExtra(
                PhoneConstants.DATA_LINK_CAPABILITIES_KEY);
        if (mLinkCapabilities[index] == null) {
            loge("CONNECTED event did not supply link capabilities.");
            mLinkCapabilities[index] = new LinkCapabilities();
        }
    }

    private class MobileDataStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();

            if (intent.getAction().equals(TelephonyIntents.
                    ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN)) {
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (!TextUtils.equals(mApnType, apnType)) {
                    return;
                }
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() + " apnType=" + apnType
                            + " apnName=" + apnName);
                }

                // Make us in the connecting state until we make a new TYPE_MOBILE_PROVISIONING
                mMobileDataState[defaultDataSub] = PhoneConstants.DataState.CONNECTING;
                updateLinkProperitesAndCapatilities(intent);
                mNetworkInfo[defaultDataSub].setIsConnectedToProvisioningNetwork(true);

                // Change state to SUSPENDED so setDetailedState
                // sends EVENT_STATE_CHANGED to connectivityService
                setDetailedState(DetailedState.SUSPENDED, "", apnName, 0);
            } else if (intent.getAction().equals(TelephonyIntents.
                    ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                final int subscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY,
                        MSimConstants.DEFAULT_SUBSCRIPTION);
                String reason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                if (VDBG) {
                    log(String.format("Broadcast received: ACTION_ANY_DATA_CONNECTION_STATE_CHANGED"
                        + "mApnType=%s %s received apnType=%s", mApnType,
                        TextUtils.equals(apnType, mApnType) ? "==" : "!=", apnType));
                }
                if (!TextUtils.equals(apnType, mApnType)) {
                    return;
                }
                // Assume this isn't a provisioning network.
                mNetworkInfo[MSimTelephonyManager.getDefault()
                    .getDefaultDataSubscription()].setIsConnectedToProvisioningNetwork(false);
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() + " apnType=" + apnType);
                }

                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    int dds = 0;
                    getPhoneService(false);

                   /*
                    * If the phone process has crashed in the past, we'll get a
                    * RemoteException and need to re-reference the service.
                    */
                    for (int retry = 0; retry < 2; retry++) {
                        if (mMSimPhoneService == null) {
                            loge("Ignoring get dds request because "
                                    + "MSim Phone Service is not available");
                            break;
                        }

                        try {
                            dds = mMSimPhoneService.getPreferredDataSubscription();
                        } catch (RemoteException e) {
                            if (retry == 0) getPhoneService(true);
                        }
                    }
                    if (VDBG) {
                        log(String.format(
                                "ACTION_ANY_DATA_CONNECTION_STATE_CHANGED"
                                + ", subscription=%s, dds=%s, reason=%s", subscription
                                , dds, reason));
                    }

                }

                int oldSubtype = mNetworkInfo[subscription].getSubtype();
                int newSubType = MSimTelephonyManager.getDefault().getNetworkType(subscription);
                String subTypeName = TelephonyManager.getDefault().getNetworkTypeName();
                mNetworkInfo[subscription].setSubtype(newSubType, subTypeName);
                if (newSubType != oldSubtype && mNetworkInfo[subscription].isConnected()) {
                    Message msg = mTarget.obtainMessage(EVENT_NETWORK_SUBTYPE_CHANGED,
                            oldSubtype, subscription,
                            mNetworkInfo[subscription]);
                    msg.sendToTarget();
                }

                PhoneConstants.DataState state = Enum.valueOf(PhoneConstants.DataState.class,
                        intent.getStringExtra(PhoneConstants.STATE_KEY));

                mNetworkInfo[subscription].setRoaming(intent.getBooleanExtra(
                        PhoneConstants.DATA_NETWORK_ROAMING_KEY, false));
                if (VDBG) {
                    log(mApnType + " setting isAvailable to " +
                            intent.getBooleanExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY,false));
                }
                mNetworkInfo[subscription].setIsAvailable(!intent.getBooleanExtra(
                        PhoneConstants.NETWORK_UNAVAILABLE_KEY, false));

                if (VDBG) {
                    log("Received state=" + state + ", old=" + mMobileDataState[subscription] +
                        ", reason=" + (reason == null ? "(unspecified)" : reason));
                }
                if (mMobileDataState[subscription] != state) {
                    mMobileDataState[subscription] = state;
                    switch (state) {
                        case DISCONNECTED:
                            if(isTeardownRequested()) {
                                setTeardownRequested(false);
                            }

                            setDetailedState(DetailedState.DISCONNECTED, reason, apnName,
                                    subscription);
                            // can't do this here - ConnectivityService needs it to clear stuff
                            // it's ok though - just leave it to be refreshed next time
                            // we connect.
                            //if (DBG) log("clearing mInterfaceName for "+ mApnType +
                            //        " as it DISCONNECTED");
                            //mInterfaceName = null;
                            break;
                        case CONNECTING:
                            setDetailedState(DetailedState.CONNECTING, reason, apnName,
                                    subscription);
                            break;
                        case SUSPENDED:
                            setDetailedState(DetailedState.SUSPENDED, reason, apnName,
                                    subscription);
                            break;
                        case CONNECTED:
                            mSubscription = subscription;
                            updateLinkProperitesAndCapatilities(intent);
                            setDetailedState(DetailedState.CONNECTED, reason, apnName,
                                    subscription);
                            break;
                    }

                    if (VDBG) {
                        Slog.d(TAG, "TelephonyMgr.DataConnectionStateChanged");
                        if (mNetworkInfo[subscription] != null) {
                            Slog.d(TAG, "NetworkInfo["+subscription+"] = "
                                    + mNetworkInfo[subscription].toString());
                            Slog.d(TAG, "subType = "
                                    + String.valueOf(mNetworkInfo[subscription].getSubtype()));
                            Slog.d(TAG, "subType = "
                                    + mNetworkInfo[subscription].getSubtypeName());
                        }
                        if (mLinkProperties[subscription] != null) {
                            Slog.d(TAG, "LinkProperties = "
                                    + mLinkProperties[subscription].toString());
                        } else {
                            Slog.d(TAG, "LinkProperties = " );
                        }

                        if (mLinkCapabilities[subscription] != null) {
                            Slog.d(TAG, "LinkCapabilities = "
                                    + mLinkCapabilities[subscription].toString());
                        } else {
                            Slog.d(TAG, "LinkCapabilities = " );
                        }
                    }


                    /* lets not sample traffic data across state changes */
                    mSamplingDataTracker[subscription].resetSamplingData();
                } else {
                    // There was no state change. Check if LinkProperties has been updated.
                    if (TextUtils.equals(reason, PhoneConstants.REASON_LINK_PROPERTIES_CHANGED)) {
                        mLinkProperties[subscription] = intent.getParcelableExtra(
                                PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                        if (mLinkProperties[subscription] == null) {
                            loge("No link property in LINK_PROPERTIES change event.");
                            mLinkProperties[subscription] = new LinkProperties();
                        }
                        // Just update reason field in this NetworkInfo
                        mNetworkInfo[subscription]
                            .setDetailedState(mNetworkInfo[subscription].getDetailedState(), reason,
                                                      mNetworkInfo[subscription].getExtraInfo());
                        Message msg = mTarget.obtainMessage(EVENT_CONFIGURATION_CHANGED,
                                                            0, subscription,
                                                            mNetworkInfo[subscription]);
                        msg.sendToTarget();
                    }
                }
            } else if (intent.getAction().
                    equals(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                final int subscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY,
                        MSimConstants.DEFAULT_SUBSCRIPTION);

                if (!TextUtils.equals(apnType, mApnType)) {
                    if (DBG) {
                        log(String.format(
                                "Broadcast received: ACTION_ANY_DATA_CONNECTION_FAILED ignore, " +
                                "mApnType=%s != received apnType=%s", mApnType, apnType));
                    }
                    return;
                }
                // Assume this isn't a provisioning network.
                mNetworkInfo[subscription].setIsConnectedToProvisioningNetwork(false);
                String reason = intent.getStringExtra(PhoneConstants.FAILURE_REASON_KEY);
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() +
                                " reason=" + reason == null ? "null" : reason);
                }
                setDetailedState(DetailedState.FAILED, reason, apnName, subscription);
            } else {
                if (DBG) log("Broadcast received: ignore " + intent.getAction());
            }
        }
    }

    private void getPhoneService(boolean forceRefresh) {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (mMSimPhoneService == null || forceRefresh) {
                mMSimPhoneService = ITelephonyMSim.Stub.asInterface(
                        ServiceManager.getService("phone_msim"));
            }
            return;
        }
        if ((mPhoneService == null) || forceRefresh) {
            mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
        }
    }

    /**
     * Report whether data connectivity is possible.
     */
    public boolean isAvailable() {
        return mNetworkInfo[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()].isAvailable();
    }

    /**
     * Report whether data connectivity is possible.
     */
    public boolean isAvailable(int subId) {
        return mNetworkInfo[subId].isAvailable();
    }
    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        String networkTypeStr = "unknown";
        int dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager mSimTm = new MSimTelephonyManager(mContext);
            dataNetworkType = mSimTm.getNetworkType(mSubscription);
        } else {
            TelephonyManager tm = new TelephonyManager(mContext);
            dataNetworkType = tm.getNetworkType();
        }

        //TODO We have to edit the parameter for getNetworkType regarding CDMA
        switch(dataNetworkType) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
            networkTypeStr = "gprs";
            break;
        case TelephonyManager.NETWORK_TYPE_EDGE:
            networkTypeStr = "edge";
            break;
        case TelephonyManager.NETWORK_TYPE_UMTS:
        case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            networkTypeStr = "umts";
            break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:
            networkTypeStr = "hsdpa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSUPA:
            networkTypeStr = "hsupa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSPA:
            networkTypeStr = "hspa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSPAP:
            networkTypeStr = "hspap";
            break;
        case TelephonyManager.NETWORK_TYPE_CDMA:
            networkTypeStr = "cdma";
            break;
        case TelephonyManager.NETWORK_TYPE_1xRTT:
            networkTypeStr = "1xrtt";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_IDEN:
            networkTypeStr = "iden";
            break;
        case TelephonyManager.NETWORK_TYPE_LTE:
        case TelephonyManager.NETWORK_TYPE_IWLAN:
            networkTypeStr = "lte";
            break;
        case TelephonyManager.NETWORK_TYPE_EHRPD:
            networkTypeStr = "ehrpd";
            break;
        default:
            loge("unknown network type: " + dataNetworkType);
        }
        return "net.tcp.buffersize." + networkTypeStr;
    }

    /**
     * Return the system properties name associated with the tcp delayed ack settings
     * for this network.
     */
    @Override
    public String getTcpDelayedAckPropName() {
        String networkTypeStr = "default";
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(
                         Context.TELEPHONY_SERVICE);
        if (tm != null) {
            switch(tm.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_LTE:
                    networkTypeStr = "lte";
                    break;
                default:
                    break;
            }
        }
        return "net.tcp.delack." + networkTypeStr;
    }

    /**
     * Return the system properties name associated with the tcp user config flag
     * for this network.
     */
    @Override
    public String getTcpUserConfigPropName() {
        String networkTypeStr = "default";
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(
                         Context.TELEPHONY_SERVICE);
        if (tm != null) {
            switch(tm.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_LTE:
                    networkTypeStr = "lte";
                    break;
                default:
                    break;
            }
        }
        return "net.tcp.usercfg." + networkTypeStr;
    }

    /**
     * Tear down mobile data connectivity, i.e., disable the ability to create
     * mobile data connections.
     * TODO - make async and return nothing?
     */
    public boolean teardown() {
        log("teardown on "+this);
        setTeardownRequested(true);
        return (setEnableApn(mApnType, false) != PhoneConstants.APN_REQUEST_FAILED);
    }

    public boolean teardown(int subId) {
        log("teardown on "+this +" subId="+subId);
        setTeardownRequested(true);
        return (setEnableApn(mApnType, false, subId) != PhoneConstants.APN_REQUEST_FAILED);
    }
    /**
     * @return true if this is ready to operate
     */
    public boolean isReady() {
        return mDataConnectionTrackerAc[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()] != null;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    @Override
    public void captivePortalCheckCompleted(boolean isCaptivePortal) {
        if (mIsCaptivePortal.getAndSet(isCaptivePortal) != isCaptivePortal) {
            // Captive portal change enable/disable failing fast
            setEnableFailFastMobileData(
                    isCaptivePortal ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
    }

    /**
     * Record the detailed state of a network, and if it is a
     * change from the previous state, send a notification to
     * any listeners.
     * @param state the new {@code DetailedState}
     * @param reason a {@code String} indicating a reason for the state change,
     * if one was supplied. May be {@code null}.
     * @param extraInfo optional {@code String} providing extra information about the state change
     */
    private void setDetailedState(NetworkInfo.DetailedState state, String reason,
            String extraInfo, int subId) {
        if (DBG) log("setDetailed state, old ="
                + mNetworkInfo[subId].getDetailedState() + " and new state=" + state);
        if (state != mNetworkInfo[subId].getDetailedState()) {
            boolean wasConnecting = (mNetworkInfo[subId].getState()
                    == NetworkInfo.State.CONNECTING);
            String lastReason = mNetworkInfo[subId].getReason();
            /*
             * If a reason was supplied when the CONNECTING state was entered, and no
             * reason was supplied for entering the CONNECTED state, then retain the
             * reason that was supplied when going to CONNECTING.
             */
            if (wasConnecting && state == NetworkInfo.DetailedState.CONNECTED && reason == null
                    && lastReason != null)
                reason = lastReason;
            mNetworkInfo[subId].setDetailedState(state, reason, extraInfo);
            Message msg = mTarget.obtainMessage(EVENT_STATE_CHANGED
                    , 0, subId, new NetworkInfo(mNetworkInfo[subId]));
            msg.sendToTarget();
        }
    }

    public void setTeardownRequested(boolean isRequested) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        mTeardownRequested[defaultDataSub] = isRequested;
    }

    public void setTeardownRequested(boolean isRequested, int subId) {
        mTeardownRequested[subId] = isRequested;
    }

    public boolean isTeardownRequested() {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        return mTeardownRequested[defaultDataSub];
    }

    public boolean isTeardownRequested(int subId) {
        return mTeardownRequested[subId];
    }
    /**
     * Re-enable mobile data connectivity after a {@link #teardown()}.
     * TODO - make async and always get a notification?
     */
    public boolean reconnect() {
        log("reconnect on "+this);
        boolean retValue = false; //connected or expect to be?
        setTeardownRequested(false);
        switch (setEnableApn(mApnType, true)) {
            case PhoneConstants.APN_ALREADY_ACTIVE:
                // need to set self to CONNECTING so the below message is handled.
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_STARTED:
                // set IDLE here , avoid the following second FAILED not sent out
                mNetworkInfo[MSimTelephonyManager .getDefault().getDefaultDataSubscription()]
                    .setDetailedState(DetailedState.IDLE, null, null);
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_FAILED:
            case PhoneConstants.APN_TYPE_NOT_AVAILABLE:
                break;
            default:
                loge("Error in reconnect - unexpected response.");
                break;
        }
        return retValue;
    }

    public boolean reconnect(int subId) {
        log("reconnect on "+this+" subId="+subId);
        boolean retValue = false; //connected or expect to be?
        setTeardownRequested(false);
        switch (setEnableApn(mApnType, true, subId)) {
            case PhoneConstants.APN_ALREADY_ACTIVE:
                // need to set self to CONNECTING so the below message is handled.
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_STARTED:
                // set IDLE here , avoid the following second FAILED not sent out
                mNetworkInfo[subId].setDetailedState(DetailedState.IDLE, null, null);
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_FAILED:
            case PhoneConstants.APN_TYPE_NOT_AVAILABLE:
                break;
            default:
                loge("Error in reconnect - unexpected response.");
                break;
        }
        return retValue;
    }
    /**
     * Turn on or off the mobile radio. No connectivity will be possible while the
     * radio is off. The operation is a no-op if the radio is already in the desired state.
     * @param turnOn {@code true} if the radio should be turned on, {@code false} if
     */
    public boolean setRadio(boolean turnOn) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                if (mMSimPhoneService == null) {
                    loge("Ignoring mobile radio request because "
                            + "could not acquire MSim Phone Service");
                    break;
                }

                try {
                    boolean result = true;
                    for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                        result = result && mMSimPhoneService.setRadio(turnOn, i);
                    }
                    return result;
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            } else {
                if (mPhoneService == null) {
                    loge("Ignoring mobile radio request because could not acquire PhoneService");
                    break;
                }

                try {
                    return mPhoneService.setRadio(turnOn);
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            }
        }

        loge("Could not set radio power to " + (turnOn ? "on" : "off"));
        return false;
    }


    public void setInternalDataEnable(boolean enabled) {
        if (DBG) log("setInternalDataEnable: E enabled=" + enabled);
        int index = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        final AsyncChannel channel = mDataConnectionTrackerAc[index];
        if (channel != null) {
            channel.sendMessage(DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
        if (VDBG) log("setInternalDataEnable: X enabled=" + enabled);
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        if (DBG) log("setUserDataEnable: E enabled=" + enabled);
        int index = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        final AsyncChannel channel = mDataConnectionTrackerAc[index];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_USER_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
            mUserDataEnabled[index] = enabled;
        }
        if (VDBG) log("setUserDataEnable: X enabled=" + enabled);
    }

    @Override
    public void setUserDataEnable(boolean enabled, int subId) {
        if (DBG) log("setUserDataEnable: E enabled=" + enabled + "subId = "+subId);
        final AsyncChannel channel = mDataConnectionTrackerAc[subId];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_USER_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
            mUserDataEnabled[subId] = enabled;
        }
        if (VDBG) log("setUserDataEnable: X enabled=" + enabled);
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        if (DBG) log("setPolicyDataEnable(enabled=" + enabled + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[defaultDataSub];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_POLICY_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
            mPolicyDataEnabled[defaultDataSub] = enabled;
        }
    }

    @Override
    public void setPolicyDataEnable(boolean enabled, int subId) {
        if (DBG) log("setPolicyDataEnable(enabled=" + enabled + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[subId];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_POLICY_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
            mPolicyDataEnabled[subId] = enabled;
        }
    }
    /**
     * Eanble/disable FailFast
     *
     * @param enabled is DctConstants.ENABLED/DISABLED
     */
    public void setEnableFailFastMobileData(int enabled) {
        if (DBG) log("setEnableFailFastMobileData(enabled=" + enabled + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA, enabled);
        }
    }

    /**
     * carrier dependency is met/unmet
     * @param met
     */
    public void setDependencyMet(boolean met) {
        Bundle bundle = Bundle.forPair(DctConstants.APN_TYPE_KEY, mApnType);
        try {
            if (DBG) log("setDependencyMet: E met=" + met);
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_SET_DEPENDENCY_MET;
            msg.arg1 = (met ? DctConstants.ENABLED : DctConstants.DISABLED);
            msg.setData(bundle);
            mDataConnectionTrackerAc[MSimTelephonyManager
                .getDefault().getDefaultDataSubscription()].sendMessage(msg);
            if (VDBG) log("setDependencyMet: X met=" + met);
        } catch (NullPointerException e) {
            loge("setDependencyMet: X mAc was null" + e);
        }
    }

    /**
     * carrier dependency is met/unmet
     * @param met
     */
    public void setDependencyMet(boolean met, int subId) {
        Bundle bundle = Bundle.forPair(DctConstants.APN_TYPE_KEY, mApnType);
        try {
            if (DBG) log("setDependencyMet: E met=" + met);
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_SET_DEPENDENCY_MET;
            msg.arg1 = (met ? DctConstants.ENABLED : DctConstants.DISABLED);
            msg.setData(bundle);
            mDataConnectionTrackerAc[subId].sendMessage(msg);
            if (VDBG) log("setDependencyMet: X met=" + met);
        } catch (NullPointerException e) {
            loge("setDependencyMet: X mAc was null" + e);
        }
    }

    /**
     *  Inform DCT mobile provisioning has started, it ends when provisioning completes.
     */
    public void enableMobileProvisioning(String url) {
        if (DBG) log("enableMobileProvisioning(url=" + url + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()];
        if (channel != null) {
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_ENABLE_MOBILE_PROVISIONING;
            msg.setData(Bundle.forPair(DctConstants.PROVISIONING_URL_KEY, url));
            channel.sendMessage(msg);
        }
    }

    /**
     * Return if this network is the provisioning network. Valid only if connected.
     * @param met
     */
    public boolean isProvisioningNetwork() {
        boolean retVal;
        try {
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_IS_PROVISIONING_APN;
            msg.setData(Bundle.forPair(DctConstants.APN_TYPE_KEY, mApnType));
            Message result = mDataConnectionTrackerAc[MSimTelephonyManager
                .getDefault().getDefaultDataSubscription()].sendMessageSynchronously(msg);
            retVal = result.arg1 == DctConstants.ENABLED;
        } catch (NullPointerException e) {
            loge("isProvisioningNetwork: X " + e);
            retVal = false;
        }
        if (DBG) log("isProvisioningNetwork: retVal=" + retVal);
        return retVal;
    }

    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()].addStackedLink(link);
    }

    @Override
    public void addStackedLink(LinkProperties link, int subId) {
        mLinkProperties[subId].addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties[MSimTelephonyManager
            .getDefault().getDefaultDataSubscription()].removeStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link, int subId) {
        mLinkProperties[subId].removeStackedLink(link);
    }

    @Override
    public String toString() {
        final CharArrayWriter writer = new CharArrayWriter();
        final PrintWriter pw = new PrintWriter(writer);
        for( int i = 0; i < mPhoneCount; i++) {
            pw.print("Mobile data state, sub" + i + ": "); pw.println(mMobileDataState[i]);
            pw.print("Data enabled sub" + i + ": user="); pw.print(mUserDataEnabled[i]);
            pw.print(", policy, sub" + i + "= "); pw.println(mPolicyDataEnabled[0]);
        }
        return writer.toString();
    }

    /**
     * Internal method supporting the ENABLE_MMS feature.
     * @param apnType the type of APN to be enabled or disabled (e.g., mms)
     * @param enable {@code true} to enable the specified APN type,
     * {@code false} to disable it.
     * @return an integer value representing the outcome of the request.
     */
    private int setEnableApn(String apnType, boolean enable) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                if (mMSimPhoneService == null) {
                    loge("Ignoring feature request because could not acquire MSim Phone Service");
                    break;
                }

                try {
                    if (enable) {
                        return mMSimPhoneService.enableApnType(apnType);
                    } else {
                        return mMSimPhoneService.disableApnType(apnType);
                    }
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            } else {
                if (mPhoneService == null) {
                    loge("Ignoring feature request because could not acquire PhoneService");
                    break;
                }

                try {
                    if (enable) {
                        return mPhoneService.enableApnType(apnType);
                    } else {
                        return mPhoneService.disableApnType(apnType);
                    }
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            }
        }

        loge("Could not " + (enable ? "enable" : "disable") + " APN type \"" + apnType + "\"");
        return PhoneConstants.APN_REQUEST_FAILED;
    }
    /**
     * Internal method supporting the ENABLE_MMS feature.
     * @param apnType the type of APN to be enabled or disabled (e.g., mms)
     * @param enable {@code true} to enable the specified APN type,
     * {@code false} to disable it.
     * @return an integer value representing the outcome of the request.
     */
    private int setEnableApn(String apnType, boolean enable, int subId) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                if (mMSimPhoneService == null) {
                    loge("Ignoring feature request because could not acquire MSim Phone Service");
                    break;
                }

                try {
                    if (enable) {
                        return mMSimPhoneService.enableApnTypeOnSubscription(apnType, subId);
                    } else {
                        return mMSimPhoneService.disableApnTypeOnSubscription(apnType, subId);
                    }
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            } else {
                if (mPhoneService == null) {
                    loge("Ignoring feature request because could not acquire PhoneService");
                    break;
                }

                try {
                    if (enable) {
                        return mPhoneService.enableApnType(apnType);
                    } else {
                        return mPhoneService.disableApnType(apnType);
                    }
                } catch (RemoteException e) {
                    if (retry == 0) getPhoneService(true);
                }
            }
        }

        loge("Could not " + (enable ? "enable" : "disable") + " APN type \"" + apnType + "\"");
        return PhoneConstants.APN_REQUEST_FAILED;
    }

    public static String networkTypeToApnType(int netType) {
        switch(netType) {
            case ConnectivityManager.TYPE_MOBILE:
                return PhoneConstants.APN_TYPE_DEFAULT;  // TODO - use just one of these
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return PhoneConstants.APN_TYPE_MMS;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return PhoneConstants.APN_TYPE_SUPL;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return PhoneConstants.APN_TYPE_DUN;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return PhoneConstants.APN_TYPE_HIPRI;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                return PhoneConstants.APN_TYPE_FOTA;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                return PhoneConstants.APN_TYPE_IMS;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                return PhoneConstants.APN_TYPE_CBS;
            case ConnectivityManager.TYPE_MOBILE_IA:
                return PhoneConstants.APN_TYPE_IA;
            default:
                sloge("Error mapping networkType " + netType + " to apnType.");
                return null;
        }
    }


    /**
     * @see android.net.NetworkStateTracker#getLinkProperties()
     */
    @Override
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties[MSimTelephonyManager
                .getDefault().getDefaultDataSubscription()]);
    }

    /**
     * @see android.net.NetworkStateTracker#getLinkCapabilities()
     */
    @Override
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities[MSimTelephonyManager
                .getDefault().getDefaultDataSubscription()]);
    }

    @Override
    public LinkCapabilities getLinkCapabilities(int subId) {
        return new LinkCapabilities(mLinkCapabilities[subId]);
    }

    public void supplyMessenger(Messenger messenger) {
        if (VDBG) log(mApnType + " got supplyMessenger");
        AsyncChannel ac = new AsyncChannel();
        ac.connect(mContext, MobileDataStateTracker.this.mHandler, messenger);
    }

    public void supplyMessengerForSubscription(Messenger messenger, int subId) {
        if (VDBG) log(mApnType + " got supplyMessenger for subId="+subId);
        mMessengerList[subId]= messenger;

        AsyncChannel ac = new AsyncChannel();
        ac.connect(mContext, MobileDataStateTracker.this.mHandler, messenger);
    }

    private void log(String s) {
        Slog.d(TAG, mApnType + ": " + s);
    }

    private void loge(String s) {
        Slog.e(TAG, mApnType + ": " + s);
    }

    static private void sloge(String s) {
        Slog.e(TAG, s);
    }

    @Override
    public LinkQualityInfo getLinkQualityInfo() {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();

        if (mNetworkInfo[defaultDataSub] == null
                || mNetworkInfo[defaultDataSub].getType() == ConnectivityManager.TYPE_NONE) {
            // no data available yet; just return
            return null;
        }

        MobileLinkQualityInfo li = new MobileLinkQualityInfo();

        li.setNetworkType(mNetworkInfo[defaultDataSub].getType());

        mSamplingDataTracker[defaultDataSub].setCommonLinkQualityInfoFields(li);

        if (mNetworkInfo[defaultDataSub].getSubtype() != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            li.setMobileNetworkType(mNetworkInfo[defaultDataSub].getSubtype());

            NetworkDataEntry entry = getNetworkDataEntry(
                    mNetworkInfo[defaultDataSub].getSubtype());
            if (entry != null) {
                li.setTheoreticalRxBandwidth(entry.downloadBandwidth);
                li.setTheoreticalRxBandwidth(entry.uploadBandwidth);
                li.setTheoreticalLatency(entry.latency);
            }

            if (mSignalStrength[defaultDataSub] != null) {
                li.setNormalizedSignalStrength(getNormalizedSignalStrength(
                        li.getMobileNetworkType(), mSignalStrength[defaultDataSub]));
            }
        }

        SignalStrength ss = mSignalStrength[defaultDataSub];
        if (ss != null) {

            li.setRssi(ss.getGsmSignalStrength());
            li.setGsmErrorRate(ss.getGsmBitErrorRate());
            li.setCdmaDbm(ss.getCdmaDbm());
            li.setCdmaEcio(ss.getCdmaEcio());
            li.setEvdoDbm(ss.getEvdoDbm());
            li.setEvdoEcio(ss.getEvdoEcio());
            li.setEvdoSnr(ss.getEvdoSnr());
            li.setLteSignalStrength(ss.getLteSignalStrength());
            li.setLteRsrp(ss.getLteRsrp());
            li.setLteRsrq(ss.getLteRsrq());
            li.setLteRssnr(ss.getLteRssnr());
            li.setLteCqi(ss.getLteCqi());
        }

        if (VDBG) {
            Slog.d(TAG, "Returning LinkQualityInfo with"
                    + " MobileNetworkType = " + String.valueOf(li.getMobileNetworkType())
                    + " Theoretical Rx BW = " + String.valueOf(li.getTheoreticalRxBandwidth())
                    + " gsm Signal Strength = " + String.valueOf(li.getRssi())
                    + " cdma Signal Strength = " + String.valueOf(li.getCdmaDbm())
                    + " evdo Signal Strength = " + String.valueOf(li.getEvdoDbm())
                    + " Lte Signal Strength = " + String.valueOf(li.getLteSignalStrength()));
        }

        return li;
    }

    @Override
    public LinkQualityInfo getLinkQualityInfo(int subId) {

        if (mNetworkInfo[subId] == null
                || mNetworkInfo[subId].getType() == ConnectivityManager.TYPE_NONE) {
            // no data available yet; just return
            return null;
        }

        MobileLinkQualityInfo li = new MobileLinkQualityInfo();

        li.setNetworkType(mNetworkInfo[subId].getType());

        mSamplingDataTracker[subId].setCommonLinkQualityInfoFields(li);

        if (mNetworkInfo[subId].getSubtype() != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            li.setMobileNetworkType(mNetworkInfo[subId].getSubtype());

            NetworkDataEntry entry = getNetworkDataEntry(mNetworkInfo[subId].getSubtype());
            if (entry != null) {
                li.setTheoreticalRxBandwidth(entry.downloadBandwidth);
                li.setTheoreticalRxBandwidth(entry.uploadBandwidth);
                li.setTheoreticalLatency(entry.latency);
            }

            if (mSignalStrength[subId] != null) {
                li.setNormalizedSignalStrength(getNormalizedSignalStrength(
                        li.getMobileNetworkType(), mSignalStrength[subId]));
            }
        }

        SignalStrength ss = mSignalStrength[subId];
        if (ss != null) {

            li.setRssi(ss.getGsmSignalStrength());
            li.setGsmErrorRate(ss.getGsmBitErrorRate());
            li.setCdmaDbm(ss.getCdmaDbm());
            li.setCdmaEcio(ss.getCdmaEcio());
            li.setEvdoDbm(ss.getEvdoDbm());
            li.setEvdoEcio(ss.getEvdoEcio());
            li.setEvdoSnr(ss.getEvdoSnr());
            li.setLteSignalStrength(ss.getLteSignalStrength());
            li.setLteRsrp(ss.getLteRsrp());
            li.setLteRsrq(ss.getLteRsrq());
            li.setLteRssnr(ss.getLteRssnr());
            li.setLteCqi(ss.getLteCqi());
        }

        if (VDBG) {
            Slog.d(TAG, "Returning LinkQualityInfo with"
                    + " MobileNetworkType = " + String.valueOf(li.getMobileNetworkType())
                    + " Theoretical Rx BW = " + String.valueOf(li.getTheoreticalRxBandwidth())
                    + " gsm Signal Strength = " + String.valueOf(li.getRssi())
                    + " cdma Signal Strength = " + String.valueOf(li.getCdmaDbm())
                    + " evdo Signal Strength = " + String.valueOf(li.getEvdoDbm())
                    + " Lte Signal Strength = " + String.valueOf(li.getLteSignalStrength()));
        }

        return li;
    }

    static class NetworkDataEntry {
        public int networkType;
        public int downloadBandwidth;               // in kbps
        public int uploadBandwidth;                 // in kbps
        public int latency;                         // in millisecond

        NetworkDataEntry(int i1, int i2, int i3, int i4) {
            networkType = i1;
            downloadBandwidth = i2;
            uploadBandwidth = i3;
            latency = i4;
        }
    }

    private static NetworkDataEntry [] mTheoreticalBWTable = new NetworkDataEntry[] {
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EDGE,      237,     118, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_GPRS,       48,      40, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_UMTS,      384,      64, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSDPA,   14400, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSUPA,   14400,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSPA,    14400,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSPAP,   21000,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_CDMA,  UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_1xRTT, UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_0,   2468,     153, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_A,   3072,    1800, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_B,  14700,    1800, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_IDEN,  UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_LTE,    100000,   50000, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EHRPD, UNKNOWN, UNKNOWN, UNKNOWN),
    };

    private static NetworkDataEntry getNetworkDataEntry(int networkType) {
        for (NetworkDataEntry entry : mTheoreticalBWTable) {
            if (entry.networkType == networkType) {
                return entry;
            }
        }

        Slog.e(TAG, "Could not find Theoretical BW entry for " + String.valueOf(networkType));
        return null;
    }

    private static int getNormalizedSignalStrength(int networkType, SignalStrength ss) {

        int level;

        switch(networkType) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                level = ss.getGsmLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                level = ss.getCdmaLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                level = ss.getEvdoLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                level = ss.getLteLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            default:
                return UNKNOWN;
        }

        return (level * LinkQualityInfo.NORMALIZED_SIGNAL_STRENGTH_RANGE) /
                SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        mSamplingDataTracker[defaultDataSub].startSampling(s);
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s, int subId) {
        mSamplingDataTracker[subId].startSampling(s);
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s) {
        int defaultDataSub = MSimTelephonyManager.getDefault().getDefaultDataSubscription();
        mSamplingDataTracker[defaultDataSub].stopSampling(s);
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s, int subId) {
        mSamplingDataTracker[subId].stopSampling(s);
    }

    @Override
    public String getNetworkInterfaceName(int subId) {
        if (mLinkProperties[subId] != null) {
            return mLinkProperties[subId].getInterfaceName();
        } else {
            return null;
        }
    }


}
