/*
 * Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.util.SparseLongArray;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class MSimNetworkControllerImpl extends NetworkControllerImpl {
    // debug
    static final String TAG = "StatusBar.MSimNetworkController";
    static final boolean DEBUG = false;
    static final boolean CHATTY = true; // additional diagnostics, but not logspew

    private int mUserId;
    private SettingsObserver mSettingsObserver;

    // telephony
    boolean[] mMSimDataConnected;
    IccCardConstants.State[] mMSimState;
    int[] mMSimDataActivity;
    int[] mMSimDataServiceState;
    ServiceState[] mMSimServiceState;
    SignalStrength[] mMSimSignalStrength;
    private PhoneStateListener[] mMSimPhoneStateListener;
    private String[] mCarrierTextSub;

    String[] mMSimNetworkName;
    String[] mOriginalSpn;
    String[] mOriginalPlmn;
    int[] mMSimPhoneSignalIconId;
    int[] mMSimLastPhoneSignalIconId;
    private int[] mMSimIconId;
    int[] mMSimDataDirectionIconId; // data + data direction on phones
    int[] mMSimDataSignalIconId;
    int[] mMSimDataTypeIconId;
    int[] mMSimDataRoamIconId;
    int[] mNoMSimIconId;
    int[] mMSimMobileActivityIconId; // overlay arrows for data direction

    String[] mMSimContentDescriptionPhoneSignal;
    String[] mMSimContentDescriptionCombinedSignal;
    String[] mMSimContentDescriptionDataType;

    int[] mMSimLastDataDirectionIconId;
    int[] mMSimLastCombinedSignalIconId;
    int[] mMSimLastDataTypeIconId;
    int[] mMSimcombinedSignalIconId;
    int[] mMSimcombinedActivityIconId;
    int[] mMSimLastcombinedActivityIconId;
    int[] mMSimLastSimIconId;
    private int mDefaultPhoneId;
    boolean[] mShowSpn;
    boolean[] mShowPlmn;
    String[] mSpn;
    String[] mPlmn;
    int mPhoneCount = 0;
    private SparseLongArray mPhoneIdSubIdMapping;
    ArrayList<MSimSignalCluster> mSimSignalClusters = new ArrayList<MSimSignalCluster>();

    public interface MSimSignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
                String contentDescription);

        void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
                int typeIcon, int roamingIcon, String contentDescription, String typeContentDescription,
                int phoneId, int noSimIcon);

        void setIsAirplaneMode(boolean is, int airplaneIcon);
        void setShowEmptySimSlots(boolean show);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public MSimNetworkControllerImpl(Context context, Handler handler) {
        super(context);
        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        Slog.d(TAG, "registerPhoneStateListener numPhones: " + numPhones);
        mMSimSignalStrength = new SignalStrength[numPhones];
        mMSimDataServiceState = new int[numPhones];
        mMSimServiceState = new ServiceState[numPhones];
        mMSimState = new IccCardConstants.State[numPhones];
        mMSimIconId = new int[numPhones];
        mMSimPhoneSignalIconId = new int[numPhones];
        mMSimDataTypeIconId = new int[numPhones];
        mMSimDataRoamIconId = new int[numPhones];
        mNoMSimIconId = new int[numPhones];
        mMSimMobileActivityIconId = new int[numPhones];
        mMSimContentDescriptionPhoneSignal = new String[numPhones];
        mMSimLastPhoneSignalIconId = new int[numPhones];
        mMSimNetworkName = new String[numPhones];
        mOriginalSpn = new String[numPhones];
        mOriginalPlmn = new String[numPhones];
        mMSimLastDataTypeIconId = new int[numPhones];
        mMSimDataConnected = new boolean[numPhones];
        mMSimDataSignalIconId = new int[numPhones];
        mMSimDataDirectionIconId = new int[numPhones];
        mMSimLastDataDirectionIconId = new int[numPhones];
        mMSimLastCombinedSignalIconId = new int[numPhones];
        mMSimcombinedSignalIconId = new int[numPhones];
        mMSimcombinedActivityIconId = new int[numPhones];
        mMSimLastcombinedActivityIconId = new int[numPhones];
        mMSimDataActivity = new int[numPhones];
        mMSimContentDescriptionCombinedSignal = new String[numPhones];
        mMSimContentDescriptionDataType = new String[numPhones];
        mMSimLastSimIconId = new int[numPhones];
        mCarrierTextSub = new String[numPhones];
        mShowSpn = new boolean[numPhones];
        mShowPlmn = new boolean[numPhones];
        mSpn = new String[numPhones];
        mPlmn = new String[numPhones];


        for (int i = 0; i < numPhones; i++) {
            mMSimSignalStrength[i] = new SignalStrength();
            mMSimServiceState[i] = new ServiceState();
            mMSimState[i] = IccCardConstants.State.READY;
            // phone_signal
            mMSimPhoneSignalIconId[i] = 0;
            mMSimDataSignalIconId[i] = 0;
            mMSimLastPhoneSignalIconId[i] = -1;
            mMSimLastDataTypeIconId[i] = -1;
            mMSimDataConnected[i] = false;
            mMSimLastDataDirectionIconId[i] = -1;
            mMSimLastCombinedSignalIconId[i] = -1;
            mMSimcombinedSignalIconId[i] = 0;
            mMSimcombinedActivityIconId[i] = 0;
            mMSimLastcombinedActivityIconId[i] = 0;
            mMSimDataActivity[i] = TelephonyManager.DATA_ACTIVITY_NONE;
            mMSimLastSimIconId[i] = 0;
            mMSimNetworkName[i] = mNetworkNameDefault;
            mMSimDataServiceState[i] = ServiceState.STATE_OUT_OF_SERVICE;
        }

        mDefaultPhoneId = getDefaultPhoneId();
        mDataConnected = mMSimDataConnected[mDefaultPhoneId];
        mSimState = mMSimState[mDefaultPhoneId];
        mDataActivity = mMSimDataActivity[mDefaultPhoneId];
        mDataServiceState = mMSimDataServiceState[mDefaultPhoneId];
        mServiceState = mMSimServiceState[mDefaultPhoneId];
        mSignalStrength = mMSimSignalStrength[mDefaultPhoneId];
        mPhoneStateListener = mMSimPhoneStateListener[mDefaultPhoneId];

        mNetworkName = mMSimNetworkName[mDefaultPhoneId];
        mPhoneSignalIconId = mMSimPhoneSignalIconId[mDefaultPhoneId];
        mLastPhoneSignalIconId = mMSimLastPhoneSignalIconId[mDefaultPhoneId];
        // data + data direction on phones
        mDataDirectionIconId = mMSimDataDirectionIconId[mDefaultPhoneId];
        mDataSignalIconId = mMSimDataSignalIconId[mDefaultPhoneId];
        mDataTypeIconId = mMSimDataTypeIconId[mDefaultPhoneId];
        mNoSimIconId = mNoMSimIconId[mDefaultPhoneId];

        mContentDescriptionPhoneSignal = mMSimContentDescriptionPhoneSignal[mDefaultPhoneId];
        mContentDescriptionCombinedSignal = mMSimContentDescriptionCombinedSignal[
                mDefaultPhoneId];
        mContentDescriptionDataType = mMSimContentDescriptionDataType[mDefaultPhoneId];

        mLastDataDirectionIconId = mMSimLastDataDirectionIconId[mDefaultPhoneId];
        mLastCombinedSignalIconId = mMSimLastCombinedSignalIconId[mDefaultPhoneId];
        mLastDataTypeIconId = mMSimLastDataTypeIconId[mDefaultPhoneId];
        mLastSimIconId = mMSimLastSimIconId[mDefaultPhoneId];

        mSettingsObserver = new SettingsObserver(handler);
        mSettingsObserver.register();
    }

    @Override
    protected void createWifiHandler() {
        // wifi
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new MSimWifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }
    }

    @Override
    protected void registerPhoneStateListener(Context context) {
        // telephony
        mPhone = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneIdSubIdMapping = new SparseLongArray();
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        Slog.d(TAG, "registerPhoneStateListener: " + mPhoneCount);
        mMSimPhoneStateListener = new PhoneStateListener[mPhoneCount];
        for (int i = 0; i < mPhoneCount; i++) {
            long[] subIdtemp = SubscriptionManager.getSubId(i);
            if (subIdtemp != null) {
                long subId = subIdtemp[0];
                Slog.d(TAG, "registerPhoneStateListener subId: " + subId);
                Slog.d(TAG, "registerPhoneStateListener slotId: " + i);
                if (subId > 0) {
                    mPhoneIdSubIdMapping.put(i, subId);
                    mMSimPhoneStateListener[i] = getPhoneStateListener(subId, i);
                    mPhone.listen(mMSimPhoneStateListener[i],
                            PhoneStateListener.LISTEN_SERVICE_STATE
                                    | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                                    | PhoneStateListener.LISTEN_CALL_STATE
                                    | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                                    | PhoneStateListener.LISTEN_DATA_ACTIVITY);
                } else {
                    mMSimPhoneStateListener[i] = null;
                }
            }
        }
    }

    private int getDefaultPhoneId() {
        int phoneId;
        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        phoneId = getPhoneId(SubscriptionManager.getDefaultSubId());
        if (phoneId < 0 || phoneId >= numPhones) {
            phoneId = 0;
        }
        return phoneId;
    }

    private int getPhoneId(long subId) {
        int phoneId;
        phoneId = SubscriptionManager.getPhoneId(subId);
        if (DEBUG) Slog.d(TAG, "getPhoneId phoneId: " + phoneId);
        return phoneId;
    }

    private void unregisterPhoneStateListener() {
        for (int i = 0; i < mPhoneCount; i++) {
            if (mMSimPhoneStateListener[i] != null) {
                mPhone.listen(mMSimPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
            }
        }
    }

    public void setUserId(int userId) {
        mUserId = userId;
        mSettingsObserver.register();
    }

    public void addSignalCluster(MSimSignalCluster cluster, int phoneId) {
        mSimSignalClusters.add(cluster);
        refreshSignalCluster(cluster, phoneId);
    }

    @Override
    public void removeAllSignalClusters() {
        mSimSignalClusters.clear();
    }

    public void refreshSignalCluster(MSimSignalCluster cluster, int phoneId) {
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature || mAppopsStrictEnabled),
                mWifiIconId,
                mWifiActivityIconId,
                mContentDescriptionWifi);
        cluster.setMobileDataIndicators(
                mHasMobileDataFeature,
                mMSimPhoneSignalIconId[phoneId],
                mMSimMobileActivityIconId[phoneId],
                mMSimDataTypeIconId[phoneId],
                mMSimDataRoamIconId[phoneId],
                mMSimContentDescriptionPhoneSignal[phoneId],
                mMSimContentDescriptionDataType[phoneId],
                phoneId,
                mNoMSimIconId[phoneId]);
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mAlwaysShowCdmaRssi ? mPhoneSignalIconId : mWimaxIconId,
                    mMSimMobileActivityIconId[phoneId],
                    mMSimDataTypeIconId[phoneId],
                    mMSimDataRoamIconId[phoneId],
                    mContentDescriptionWimax,
                    mMSimContentDescriptionDataType[phoneId],
                    phoneId,
                    mNoMSimIconId[phoneId]);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators(
                    mHasMobileDataFeature,
                    mShowPhoneRSSIForData ? mMSimPhoneSignalIconId[phoneId]
                            : mMSimDataSignalIconId[phoneId],
                    mMSimMobileActivityIconId[phoneId],
                    mMSimDataTypeIconId[phoneId],
                    mMSimDataRoamIconId[phoneId],
                    mMSimContentDescriptionPhoneSignal[phoneId],
                    mMSimContentDescriptionDataType[phoneId],
                    phoneId,
                    mNoMSimIconId[phoneId]);
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mAirplaneIconId);
        cluster.setShowEmptySimSlots(mSettingsObserver.showEmptySimIcons());

        if (DEBUG) {
            Slog.d(TAG, "refreshSignalCluster, mMSimPhoneSignalIconId[" + phoneId + "]="
                    + getResourceName(mMSimPhoneSignalIconId[phoneId])
                    + " mMSimDataSignalIconId[" + phoneId + "]="
                    + getResourceName(mMSimDataSignalIconId[phoneId])
                    + " mMSimDataTypeIconId[" + phoneId + "]="
                    + getResourceName(mMSimDataTypeIconId[phoneId])
                    + " mMSimMobileActivityIconId[" + phoneId + "]="
                    + getResourceName(mMSimMobileActivityIconId[phoneId])
                    + " mNoMSimIconId[" + phoneId + "]="
                    + getResourceName(mNoMSimIconId[phoneId]));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews(mDefaultPhoneId);
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            updateSimState(intent);
            for (int sub = 0; sub < TelephonyManager.getDefault().getPhoneCount(); sub++) {
                updateDataIcon(sub);
                refreshViews(sub);
            }
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            final long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
            Slog.d(TAG, "Received SPN update on subId :" + subId);
            Integer phoneId = getPhoneId(subId);
            Slog.d(TAG, "Received SPN update on phoneId :" + phoneId);
            mShowSpn[phoneId] = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
            mSpn[phoneId] = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            mShowPlmn[phoneId] = intent.getBooleanExtra(
                    TelephonyIntents.EXTRA_SHOW_PLMN, false);
            mPlmn[phoneId] = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            mOriginalSpn[phoneId] = mSpn[phoneId];
            mOriginalPlmn[phoneId] = mPlmn[phoneId];
            if (mContext.getResources().getBoolean(com.android.internal.R.bool.
                    config_monitor_locale_change)) {
                if (mShowSpn[phoneId] && mSpn[phoneId] != null) {
                    mSpn[phoneId] = getLocaleString(mOriginalSpn[phoneId]);
                }
                if (mShowPlmn[phoneId] && mPlmn[phoneId] != null) {
                    mPlmn[phoneId] = getLocaleString(mOriginalPlmn[phoneId]);
                }
            }

            updateNetworkName(mShowSpn[phoneId], mSpn[phoneId], mShowPlmn[phoneId],
                    mPlmn[phoneId], phoneId);
            updateCarrierText(phoneId);
            refreshViews(phoneId);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE) ||
                action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews(mDefaultPhoneId);
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            //parse the string to current language string in public resources
            if (mContext.getResources().getBoolean(com.android.internal.R.
                    bool.config_monitor_locale_change)) {
                for (int i = 0; i < mPhoneCount; i++) {
                    if (mShowSpn[i] && mSpn[i] != null) {
                        mSpn[i] = getLocaleString(mOriginalSpn[i]);
                    }
                    if (mShowPlmn[i] && mPlmn[i] != null) {
                        mPlmn[i] = getLocaleString(mOriginalPlmn[i]);
                    }

                    updateNetworkName(mShowSpn[i], mSpn[i], mShowPlmn[i], mPlmn[i], i);
                    updateCarrierText(i);
                    refreshViews(i);
                }
            } else {
                refreshViews(mDefaultPhoneId);
            }
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            updateAirplaneMode();
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                updateSimIcon(i);
                updateCarrierText(i);
            }
            refreshViews(mDefaultPhoneId);
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews(mDefaultPhoneId);
        } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
            unregisterPhoneStateListener();
            registerPhoneStateListener(mContext);
            mDefaultPhoneId = getDefaultPhoneId();
            for (int i = 0; i < mPhoneCount; i++) {
                updateIconSet(i);
                updateCarrierText(i);
                updateTelephonySignalStrength(i);
                updateDataNetType(i);
                updateDataIcon(i);
                refreshViews(i);
            }
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
            // Update data in QS
            long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);

            if (subId == -1) {
                Slog.e(TAG, "No subId in ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
                return;
            }

            int phoneId = getPhoneId(subId);
            updateTelephonySignalStrength(phoneId);
            updateDataNetType(phoneId);
            refreshViews(phoneId);
        }
    }

    private void updateCarrierText(int sub) {
        int textResId = 0;
        if (mAirplaneMode) {
            textResId = com.android.internal.R.string.lockscreen_airplane_mode_on;
        } else {
            if (DEBUG) {
                Slog.d(TAG, "updateCarrierText for sub:" + sub + " simState =" + mMSimState[sub]);
            }

            switch (mMSimState[sub]) {
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
                    mCarrierTextSub[sub] = mMSimNetworkName[sub];
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
            mCarrierTextSub[sub] = mContext.getString(textResId);
        }
    }

    private void setCarrierText() {
        String carrierName = mCarrierTextSub[PhoneConstants.PHONE_ID1];
        for (int i = 1; i < mPhoneCount; i++) {
            carrierName = carrierName + "    " + mCarrierTextSub[i];
        }

        for (int i = 0; i < mMobileLabelViews.size(); i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(carrierName);
            v.setVisibility(View.VISIBLE);
        }
    }


    // ===== Telephony ==============================================================

    private PhoneStateListener getPhoneStateListener(long subId, int slotId) {
        return new PhoneStateListener(subId) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (DEBUG) {
                    Slog.d(TAG, "onSignalStrengthsChanged received on phoneId :"
                            + getPhoneId(mSubId) + "signalStrength=" + signalStrength +
                            ((signalStrength == null) ? "" :
                            (" level=" + signalStrength.getLevel())));
                }
                mMSimSignalStrength[getPhoneId(mSubId)] = signalStrength;
                updateIconSet(getPhoneId(mSubId));
                updateTelephonySignalStrength(getPhoneId(mSubId));
                refreshViews(getPhoneId(mSubId));
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                int phoneId = getPhoneId(mSubId);
                if (DEBUG) {
                    Slog.d(TAG, "onServiceStateChanged received on phoneId :"
                            + phoneId + "state=" + state.getState());
                }
                mMSimServiceState[phoneId] = state;
                mServiceState = mMSimServiceState[mDefaultPhoneId];
                if (SystemProperties.getBoolean("ro.config.combined_signal", true)) {
                    /*
                     * if combined_signal is set to true only then consider data
                     * service state for signal display
                     */
                    mMSimDataServiceState[phoneId] =
                            mMSimServiceState[phoneId].getDataRegState();
                    if (DEBUG) {
                        Slog.d(TAG, "Combining data service state " +
                                mMSimDataServiceState[phoneId] + " for signal");
                    }
                }
                updateIconSet(phoneId);
                updateTelephonySignalStrength(phoneId);
                updateDataNetType(phoneId);
                updateDataIcon(phoneId);
                updateNetworkName(mShowSpn[phoneId], mSpn[phoneId],
                        mShowPlmn[phoneId], mPlmn[phoneId], phoneId);
                updateCarrierText(phoneId);

                refreshViews(phoneId);
            }

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                int phoneId = getPhoneId(mSubId);
                if (DEBUG) {
                    Slog.d(TAG, "onCallStateChanged received on phoneId :"
                            + phoneId + "state=" + state);
                }
                // In cdma, if a voice call is made, RSSI should switch to 1x.
                if (isCdma(phoneId)) {
                    updateTelephonySignalStrength(phoneId);
                    refreshViews(phoneId);
                }
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                int phoneId = getPhoneId(mSubId);
                if (DEBUG) {
                    Slog.d(TAG, "onDataConnectionStateChanged received on phoneId :"
                            + phoneId + " subid: " + mSubId + "state=" + state
                            + " type=" + networkType);
                }

                // DSDS case: Data is active only on DDS. Ignore the Data Connection
                // State changed notifications of the other NON-DDS.
                Slog.d(TAG, "onDataConnectionStateChanged getDefaultDataSubId :" +
                        SubscriptionManager.getDefaultDataSubId());
                if (mSubId == SubscriptionManager.getDefaultDataSubId()) {
                    mDataState = state;
                    mDataNetType = networkType;
                }

                updateIconSet(phoneId);
                updateDataNetType(phoneId);
                updateDataIcon(phoneId);
                refreshViews(phoneId);
            }

            @Override
            public void onDataActivity(int direction) {
                int phoneId = getPhoneId(mSubId);
                if (DEBUG) {
                    Slog.d(TAG, "onDataActivity received on phoneId :"
                            + phoneId + "direction=" + direction);
                }
                mMSimDataActivity[phoneId] = direction;
                mDataActivity = direction;
                updateDataIcon(phoneId);
                refreshViews(phoneId);
            }
        };
    }

    // ===== Wifi ===================================================================

    class MSimWifiHandler extends WifiHandler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        int dataSub = SubscriptionManager.getPhoneId(
                                SubscriptionManager.getDefaultDataSubId());
                        if (!SubscriptionManager.isValidPhoneId(dataSub)) {
                            dataSub = 0;
                        }
                        refreshViews(dataSub);
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    protected void updateSimState(Intent intent) {
        IccCardConstants.State simState;
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        // Obtain the phoneId info from intent.
        int phoneId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
        Slog.d(TAG, "updateSimState for phoneId :" + phoneId);
        if (phoneId >= 0) {
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                simState = IccCardConstants.State.ABSENT;
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)
                    || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)
                    || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
                simState = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent.getStringExtra(IccCardConstants.
                        INTENT_KEY_LOCKED_REASON);
                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    simState = IccCardConstants.State.PIN_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    simState = IccCardConstants.State.PUK_REQUIRED;
                } else {
                    simState = IccCardConstants.State.PERSO_LOCKED;
                }
            } else {
                simState = IccCardConstants.State.UNKNOWN;
            }
            // Update the sim state and carrier text.
            if (simState != IccCardConstants.State.UNKNOWN && simState != mMSimState[phoneId]) {
                mMSimState[phoneId] = simState;
                updateCarrierText(phoneId);
                if (DEBUG) Slog.d(TAG, "updateSimState simState =" + mMSimState[phoneId]);
            }
            updateIconSet(phoneId);
            updateDataIcon(phoneId);
            updateTelephonySignalStrength(phoneId);
            updateSimIcon(phoneId);
        }
    }

    private boolean isCdma(int phoneId) {
        return (mMSimSignalStrength[phoneId] != null) &&
                !mMSimSignalStrength[phoneId].isGsm();
    }

    private boolean hasService(int phoneId) {
        ServiceState ss = mMSimServiceState[phoneId];
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private void updateTelephonySignalStrength(int phoneId) {
        if (DEBUG) Slog.d(TAG, "updateTelephonySignalStrength: phoneId =" + phoneId);
        int dataSub = SubscriptionManager.getPhoneId(
                SubscriptionManager.getDefaultDataSubId());
        if ((!hasService(phoneId) &&
                (mMSimDataServiceState[phoneId] != ServiceState.STATE_IN_SERVICE))
                || mMSimState[phoneId] == IccCardConstants.State.ABSENT) {
            if (DEBUG) Slog.d(TAG, " No service");
            mMSimPhoneSignalIconId[phoneId] =
                    TelephonyIcons.getSignalNullIcon(phoneId);
            mMSimDataSignalIconId[phoneId] =
                    mMSimPhoneSignalIconId[phoneId];
            if (phoneId == dataSub) {
                mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
            }
        } else {
            if (mMSimSignalStrength[phoneId] == null || (mMSimServiceState == null)) {
                if (DEBUG) {
                    Slog.d(TAG, " Null object, mMSimSignalStrength= "
                            + mMSimSignalStrength[phoneId]
                            + " mMSimServiceState " + mMSimServiceState[phoneId]);
                }
                mMSimPhoneSignalIconId[phoneId] =
                        TelephonyIcons.getSignalNullIcon(phoneId);
                mMSimDataSignalIconId[phoneId] = mMSimPhoneSignalIconId[phoneId];
                mMSimContentDescriptionPhoneSignal[phoneId] =
                        TelephonyIcons.getSignalStrengthDes(phoneId, 0);
                if (phoneId == dataSub) {
                    mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
                }
            } else {
                int iconLevel;
                if (isCdma(phoneId) && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel = iconLevel = mMSimSignalStrength[phoneId].getCdmaLevel();
                    if (DEBUG) Slog.d(TAG, "mAlwaysShowCdmaRssi= " + mAlwaysShowCdmaRssi
                            + " set to cdmaLevel= "
                            + mMSimSignalStrength[phoneId].getCdmaLevel()
                            + " instead of level= " + mMSimSignalStrength[phoneId].getLevel());
                } else {
                    mLastSignalLevel = iconLevel = mMSimSignalStrength[phoneId].getLevel();
                    if (mShowRsrpSignalLevelforLTE) {
                        if (mMSimServiceState[phoneId].getDataNetworkType() ==
                                TelephonyManager.NETWORK_TYPE_LTE) {
                            int level = mMSimSignalStrength[phoneId].getAlternateLteLevel();
                            mLastSignalLevel = iconLevel = (level == -1 ? 0 : level);
                            Slog.d(TAG, "updateTelephonySignalStrength, data type is lte, level = "
                                    + level + " | " + mMSimSignalStrength[phoneId]);
                        }
                    }
                }

                mMSimPhoneSignalIconId[phoneId] =
                        TelephonyIcons.getSignalStrengthIcon(phoneId, mInetCondition,
                                iconLevel, isRoaming(phoneId));

                mMSimContentDescriptionPhoneSignal[phoneId] =
                        TelephonyIcons.getSignalStrengthDes(phoneId, iconLevel);

                mMSimDataSignalIconId[phoneId] = mMSimPhoneSignalIconId[phoneId];

                if (phoneId == dataSub) {
                    mQSPhoneSignalIconId = TelephonyIcons
                            .QS_TELEPHONY_SIGNAL_STRENGTH[mInetCondition][iconLevel];
                }

                if (DEBUG) {
                    Slog.d(TAG, "updateTelephonySignalStrength, sub: " + phoneId
                            + " level=" + iconLevel
                            + " mInetCondition=" + mInetCondition
                            + " mMSimPhoneSignalIconId[" + phoneId + "]="
                            + mMSimPhoneSignalIconId[phoneId]
                            + "/" + getResourceName(mMSimPhoneSignalIconId[phoneId]));
                }
            }
        }
    }

    private boolean isRoaming(int phoneId) {
        return (isCdma(phoneId) ? isCdmaEri(phoneId)
                : mMSimServiceState[phoneId] != null && mMSimServiceState[phoneId].getRoaming());
    }

    private void updateDataNetType(int phoneId) {
        // DSDS case: Data is active only on DDS. Clear the icon for NON-DDS
        int dataSub = SubscriptionManager.getPhoneId(
                SubscriptionManager.getDefaultDataSubId());
        if (phoneId != dataSub) {
            Slog.d(TAG, "updateDataNetType: phoneId" + phoneId
                    + " is not DDS(=SUB" + dataSub + ")!");
            mMSimDataTypeIconId[phoneId] = 0;
        } else {
            mNetworkName = mMSimNetworkName[phoneId];
            if (mIsWimaxEnabled && mWimaxConnected) {
                // wimax is a special 4g network not handled by telephony
                mMSimDataTypeIconId[phoneId] = R.drawable.stat_sys_data_fully_connected_4g;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[mInetCondition];
                mMSimContentDescriptionDataType[phoneId] = mContext.getString(
                        R.string.accessibility_data_connection_4g);
            } else {
                Slog.d(TAG, "updateDataNetType sub = " + phoneId
                        + " mDataNetType = " + mDataNetType);
                mMSimDataTypeIconId[phoneId] =
                        TelephonyIcons.getDataTypeIcon(phoneId);
                mMSimContentDescriptionDataType[phoneId] =
                        TelephonyIcons.getDataTypeDesc(phoneId);
                mQSDataTypeIconId =
                        TelephonyIcons.getQSDataTypeIcon(phoneId);
            }
        }

        boolean setQSDataTypeIcon = false;
        if (isCdma(phoneId)) {
            if (isCdmaEri(phoneId)) {
                mMSimDataTypeIconId[phoneId] = R.drawable.stat_sys_data_fully_connected_roam;
                setQSDataTypeIcon = true;
                if (phoneId == dataSub) {
                    mQSDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                }
            }
        } else if (isRoaming(phoneId)) {
            if (SystemProperties.getBoolean("ro.config.always_show_roaming", false)) {
                mMSimDataRoamIconId[phoneId] = R.drawable.stat_sys_data_msim_roam;
            } else {
                mMSimDataTypeIconId[phoneId] = R.drawable.stat_sys_data_fully_connected_roam;
            }
            setQSDataTypeIcon = true;
            if (phoneId == dataSub) {
                mQSDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
            }
        } else if (!isRoaming(phoneId)) {
            mMSimDataRoamIconId[phoneId] = 0;
        }

        if (setQSDataTypeIcon && phoneId == dataSub) {
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
        }
    }

    boolean isCdmaEri(int phoneId) {
        if ((mMSimServiceState[phoneId] != null)
                && (hasService(phoneId) || (mMSimDataServiceState[phoneId]
                == ServiceState.STATE_IN_SERVICE))) {
            final int iconIndex = mMSimServiceState[phoneId].getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mMSimServiceState[phoneId].getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateSimIcon(int phoneId) {
        Slog.d(TAG, "In updateSimIcon card =" + phoneId + ", simState= " + mMSimState[phoneId]);
        if (mMSimState[phoneId] == IccCardConstants.State.ABSENT) {
            mNoMSimIconId[phoneId] = TelephonyIcons.getNoSimIcon(phoneId);
        } else {
            mNoMSimIconId[phoneId] = 0;
        }
    }

    private void updateIconSet(int phoneId) {
        if (DEBUG) Slog.d(TAG, "updateIconSet, phoneId = " + phoneId);
        int voiceNetorkType = mMSimServiceState[phoneId].getVoiceNetworkType();
        int dataNetorkType = mMSimServiceState[phoneId].getDataNetworkType();

        if (DEBUG) Slog.d(TAG, "updateIconSet, voice network type is: " + voiceNetorkType
                + "/" + TelephonyManager.getNetworkTypeName(voiceNetorkType)
                + ", data network type is: " + dataNetorkType
                + "/" + TelephonyManager.getNetworkTypeName(dataNetorkType));
        int chosenNetworkType = ((dataNetorkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                ? voiceNetorkType : dataNetorkType);

        if (DEBUG) Slog.d(TAG, "updateIconSet, chosenNetworkType=" + chosenNetworkType
                + " hspaDataDistinguishable=" + String.valueOf(mHspaDataDistinguishable)
                + " hspapDistinguishable=" + "false"
                + " showAtLeastThreeGees=" + String.valueOf(mShowAtLeastThreeGees));

        int inetCondition = inetConditionForNetwork(ConnectivityManager.TYPE_MOBILE);
        TelephonyIcons.updateDataType(phoneId, chosenNetworkType, mShowAtLeastThreeGees,
                mShow4GforLTE, mHspaDataDistinguishable, inetCondition);
    }

    private void updateDataIcon(int phoneId) {
        if (DEBUG) Slog.d(TAG, "updateDataIcon phoneId =" + phoneId);
        int iconId = 0;
        boolean visible = true;
        int dataSub = SubscriptionManager.getPhoneId(
                SubscriptionManager.getDefaultDataSubId());

        if (DEBUG) Slog.d(TAG, "updateDataIcon dataSub =" + dataSub);
        // DSDS case: Data is active only on DDS. Clear the icon for NON-DDS
        if (phoneId != dataSub) {
            mMSimDataConnected[phoneId] = false;
            if (DEBUG) Slog.d(TAG, "updateDataIconi: phoneId" + phoneId
                    + " is not DDS.  Clear the mMSimDataConnected Flag and return");
            return;
        }

        if (DEBUG) Slog.d(TAG, "updateDataIcon  when SimState =" + mMSimState[phoneId]);
        if (mDataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            // If data network type is unknown do not display data icon
            visible = false;
        } else if (!isCdma(phoneId)) {
            if (DEBUG) Slog.d(TAG, "updateDataIcon  when gsm mMSimState =" + mMSimState[phoneId]);
            // GSM case, we have to check also the sim state
            if (mMSimState[phoneId] == IccCardConstants.State.READY ||
                    mMSimState[phoneId] == IccCardConstants.State.UNKNOWN) {
                mNoSim = false;
                if (mDataState == TelephonyManager.DATA_CONNECTED) {
                    iconId = TelephonyIcons.getDataActivity(phoneId, mDataActivity);
                    mMSimDataDirectionIconId[phoneId] = iconId;
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                if (DEBUG) Slog.d(TAG, "updateDataIcon when no sim");
                mNoSim = true;
                iconId = TelephonyIcons.getNoSimIcon();
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mMSimDataActivity can be also DATA_ACTIVITY_DORMANT
            if (mDataState == TelephonyManager.DATA_CONNECTED) {
                iconId = TelephonyIcons.getDataActivity(phoneId, mDataActivity);
            } else {
                iconId = 0;
                visible = false;
            }
        }

        mMSimDataDirectionIconId[phoneId] = iconId;
        mMSimMobileActivityIconId[phoneId] = iconId;
        mMSimDataConnected[phoneId] = visible;
        mDataConnected = visible;

        if (DEBUG) Slog.d(TAG, "updateDataIcon when mMSimDataConnected[" + phoneId + "] ="
                + mMSimDataConnected[phoneId]
                + " mMSimMobileActivityIconId[" + phoneId + "] = "
                + mMSimMobileActivityIconId[phoneId]);
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn,
                           int phoneId) {
        if (DEBUG) {
            Slog.d(TAG, "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            if (mContext.getResources().getBoolean(com.android.internal.R.bool.config_display_rat)
                    && mMSimServiceState[phoneId] != null) {
                plmn = appendRatToNetworkName(plmn, mMSimServiceState[phoneId]);
            }
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_spn_display_control)
                    && something) {
                Slog.d(TAG, "Do not display spn string when showPlmn and showSpn are both true"
                        + "and plmn string is not null");
            } else {
                if (something) {
                    str.append(mNetworkNameSeparator);
                }
                if (mContext.getResources().getBoolean(com.android.internal.R.bool.
                        config_display_rat) && mMSimServiceState[phoneId] != null) {
                    spn = appendRatToNetworkName(spn, mMSimServiceState[phoneId]);
                }
                str.append(spn);
                something = true;
            }
        }
        if (mPhoneIdSubIdMapping.indexOfKey(phoneId) >= 0) {
            long sub = mPhoneIdSubIdMapping.get(phoneId);
            SubInfoRecord sir = SubscriptionManager.getSubInfoForSubscriber(sub);
            mMSimNetworkName[phoneId] = sir.displayName;
        } else if (something) {
            mMSimNetworkName[phoneId] = str.toString();
        } else {
            mMSimNetworkName[phoneId] = mNetworkNameDefault;
        }
        Slog.d(TAG, "mMSimNetworkName[phoneId] " + mMSimNetworkName[phoneId]
                + "phoneId " + phoneId);
    }

    // ===== Full or limited Internet connectivity ==================================
    @Override
    protected void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Slog.d(TAG, "updateConnectivity: intent=" + intent);
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
            Slog.d(TAG, "updateConnectivity: networkInfo=" + info);
            Slog.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);
        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateWimaxIcons();
        for (int sub = 0; sub < TelephonyManager.getDefault().getPhoneCount(); sub++) {
            updateIconSet(sub);
            updateDataNetType(sub);
            updateDataIcon(sub);
            updateTelephonySignalStrength(sub);
        }
        updateWifiIcons();
    }

    // ===== Update the views =======================================================

    protected void refreshViews(int phoneId) {
        Context context = mContext;

        String combinedLabel = "";
        String mobileLabel = "";
        String wifiLabel = "";
        int N;
        if (DEBUG) {
            Slog.d(TAG, "refreshViews phoneId =" + phoneId + "mMSimDataConnected ="
                    + mMSimDataConnected[phoneId]);
            Slog.d(TAG, "refreshViews mMSimDataActivity =" + mMSimDataActivity[phoneId]);
        }
        int dataSub = SubscriptionManager.getPhoneId(
                SubscriptionManager.getDefaultDataSubId());
        if (!mHasMobileDataFeature) {
            mMSimDataSignalIconId[phoneId] = mMSimPhoneSignalIconId[phoneId] = 0;
            mobileLabel = "";
            mQSPhoneSignalIconId = 0;
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mMSimDataConnected[phoneId]) {
                mobileLabel = mMSimNetworkName[phoneId];
            } else if (mConnected) {
                if (hasService(phoneId)) {
                    mobileLabel = mMSimNetworkName[phoneId];
                } else {
                    mobileLabel = "";
                }
            } else {
                mobileLabel
                        = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mMSimDataConnected[phoneId]) {
                mMSimcombinedSignalIconId[phoneId] = mMSimDataSignalIconId[phoneId];

                combinedLabel = mobileLabel;
                mMSimcombinedActivityIconId[phoneId] = mMSimMobileActivityIconId[phoneId];
                // set by updateDataIcon()
                mMSimcombinedSignalIconId[phoneId] = mMSimDataSignalIconId[phoneId];
                mMSimContentDescriptionCombinedSignal[phoneId] =
                        mMSimContentDescriptionDataType[phoneId];
            } else {
                mMSimMobileActivityIconId[phoneId] = 0;
                mMSimcombinedActivityIconId[phoneId] = 0;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(
                        R.string.status_bar_settings_signal_meter_wifi_nossid);
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

            mMSimcombinedActivityIconId[phoneId] = mWifiActivityIconId;
            combinedLabel = wifiLabel;
            mMSimcombinedSignalIconId[phoneId] = mWifiIconId; // set by updateWifiIcons()
            mMSimContentDescriptionCombinedSignal[phoneId] = mContentDescriptionWifi;
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(
                        R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            mMSimcombinedSignalIconId[phoneId] = mBluetoothTetherIconId;
            mMSimContentDescriptionCombinedSignal[phoneId] = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType ==
                ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            // TODO: icons and strings for Ethernet connectivity
            combinedLabel = mConnectedNetworkTypeName;
        }

        final boolean displayAirplaneModeIcon;
        if (!mAirplaneMode) {
            // airplane mode disabled
            displayAirplaneModeIcon = false;
        } else if (mMSimServiceState[phoneId] == null) {
            // airplane mode enabled, but no state yet - display icon to be on the safe side
            displayAirplaneModeIcon = true;
        } else if (hasService(phoneId) || mMSimServiceState[phoneId].isEmergencyOnly()) {
            // don't display airplane mode icon in emergency calls mode
            displayAirplaneModeIcon = false;
        } else {
            displayAirplaneModeIcon = true;
        }

        if (displayAirplaneModeIcon) {

            // look again; your radios are now airplanes
            mMSimContentDescriptionPhoneSignal[phoneId] = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = R.drawable.stat_sys_airplane_mode;
            mMSimPhoneSignalIconId[phoneId] = mMSimDataSignalIconId[phoneId]
                    = mMSimDataTypeIconId[phoneId] = 0;
            mNoMSimIconId[phoneId] = 0;
            if (phoneId == dataSub) {
                mQSDataTypeIconId = 0;
                mNetworkName = mNetworkNameDefault;
            }

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(
                            R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mMSimContentDescriptionCombinedSignal[phoneId] =
                        mContentDescriptionPhoneSignal;
                mMSimcombinedSignalIconId[phoneId] = mMSimDataSignalIconId[phoneId];
            }
            mMSimDataTypeIconId[phoneId] = 0;
            if (phoneId == dataSub) {
                mQSDataTypeIconId = 0;
            }

            mMSimcombinedSignalIconId[phoneId] = mMSimDataSignalIconId[phoneId];
        } else if (!mMSimDataConnected[phoneId] && !mWifiConnected && !mBluetoothTethered &&
                !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(
                    R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            mMSimcombinedSignalIconId[phoneId] =
                    mHasMobileDataFeature ? mMSimDataSignalIconId[phoneId] : mWifiIconId;
            mMSimContentDescriptionCombinedSignal[phoneId] = mHasMobileDataFeature
                    ? mMSimContentDescriptionDataType[phoneId] : mContentDescriptionWifi;
        }

        if (!mMSimDataConnected[phoneId]) {
            if (DEBUG) Slog.d(TAG, "refreshViews: Data not connected!!"
                    + " Set no data type icon / Roaming for phoneId: " + phoneId);
            mMSimDataTypeIconId[phoneId] = 0;
            if (phoneId == dataSub) {
                mQSDataTypeIconId = 0;
            }
            if (isCdma(phoneId)) {
                if (isCdmaEri(phoneId)) {
                    mMSimDataTypeIconId[phoneId] =
                            R.drawable.stat_sys_data_fully_connected_roam;
                    if (phoneId == dataSub) {
                        mQSDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                    }
                }
            } else if (isRoaming(phoneId)) {
                if (SystemProperties.getBoolean("ro.config.always_show_roaming", false)) {
                    mMSimDataRoamIconId[phoneId] = R.drawable.stat_sys_data_msim_roam;
                } else {
                    mMSimDataTypeIconId[phoneId] = R.drawable.stat_sys_data_fully_connected_roam;
                }
                if (phoneId == dataSub) {
                    mQSDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                }
            } else if (!isRoaming(phoneId)) {
                mMSimDataRoamIconId[phoneId] = 0;
            }
        }

        if (!mAirplaneMode && mMSimState[phoneId] == IccCardConstants.State.ABSENT) {
            mMSimPhoneSignalIconId[phoneId] = 0;
            mMSimDataSignalIconId[phoneId] = 0;
            mMSimDataTypeIconId[phoneId] = 0;
        }

        if (DEBUG) {
            Slog.d(TAG, "refreshViews connected={"
                    + (mWifiConnected ? " wifi" : "")
                    + (mMSimDataConnected[phoneId] ? " data" : "")
                    + " } level="
                    + ((mMSimSignalStrength[phoneId] == null) ? "??" : Integer.toString
                    (mMSimSignalStrength[phoneId].getLevel()))
                    + " mMSimcombinedSignalIconId=0x"
                    + Integer.toHexString(mMSimcombinedSignalIconId[phoneId])
                    + "/" + getResourceName(mMSimcombinedSignalIconId[phoneId])
                    + " mMSimcombinedActivityIconId=0x" + Integer.toHexString
                    (mMSimcombinedActivityIconId[phoneId])
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mMSimDataActivity=" + mMSimDataActivity[phoneId]
                    + " mMSimPhoneSignalIconId=0x" + Integer.toHexString
                    (mMSimPhoneSignalIconId[phoneId])
                    + "/" + getResourceName(mMSimPhoneSignalIconId[phoneId])
                    + " mMSimDataDirectionIconId=0x" + Integer.toHexString
                    (mMSimDataDirectionIconId[phoneId])
                    + " mMSimDataSignalIconId=0x" + Integer.toHexString
                    (mMSimDataSignalIconId[phoneId])
                    + " mMSimDataTypeIconId=0x" + Integer.toHexString
                    (mMSimDataTypeIconId[phoneId])
                    + "/" + getResourceName(mMSimDataTypeIconId[phoneId])
                    + " mNoMSimIconId=0x" + Integer.toHexString(mNoMSimIconId[phoneId])
                    + "/" + getResourceName(mNoMSimIconId[phoneId])
                    + " mMSimMobileActivityIconId=0x"
                    + Integer.toHexString(mMSimMobileActivityIconId[phoneId])
                    + "/" + getResourceName(mMSimMobileActivityIconId[phoneId])
                    + " mWifiIconId=0x" + Integer.toHexString(mWifiIconId)
                    + " mBluetoothTetherIconId=0x" + Integer.toHexString(mBluetoothTetherIconId));
        }

        // update QS
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        if (mMSimLastPhoneSignalIconId[phoneId] != mMSimPhoneSignalIconId[phoneId]
                || mLastWifiIconId != mWifiIconId
                || mLastWimaxIconId != mWimaxIconId
                || mMSimLastDataTypeIconId[phoneId] != mMSimDataTypeIconId[phoneId]
                || mLastAirplaneMode != mAirplaneMode
                || mMSimLastSimIconId[phoneId] != mNoMSimIconId[phoneId]
                || mMSimLastcombinedActivityIconId[phoneId]
                != mMSimcombinedActivityIconId[phoneId]) {
            // NB: the mLast*s will be updated later
            for (MSimSignalCluster cluster : mSimSignalClusters) {
                refreshSignalCluster(cluster, phoneId);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        // the phone icon on phones
        if (mMSimLastPhoneSignalIconId[phoneId] != mMSimPhoneSignalIconId[phoneId]) {
            mMSimLastPhoneSignalIconId[phoneId] = mMSimPhoneSignalIconId[phoneId];
        }

        // the data icon on phones
        if (mMSimLastDataDirectionIconId[phoneId] != mMSimDataDirectionIconId[phoneId]) {
            mMSimLastDataDirectionIconId[phoneId] = mMSimDataDirectionIconId[phoneId];
        }

        if (mMSimLastSimIconId[phoneId] != mNoMSimIconId[phoneId]) {
            mMSimLastSimIconId[phoneId] = mNoMSimIconId[phoneId];
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
        if (mMSimLastCombinedSignalIconId[phoneId] !=
                mMSimcombinedSignalIconId[phoneId]) {
            mMSimLastCombinedSignalIconId[phoneId] = mMSimcombinedSignalIconId[phoneId];
        }
        // the combined data activity icon
        if (mMSimLastcombinedActivityIconId[phoneId] !=
                mMSimcombinedActivityIconId[phoneId]) {
            mMSimLastcombinedActivityIconId[phoneId]
                    = mMSimcombinedActivityIconId[phoneId];
        }
        // the data network type overlay
        if (mMSimLastDataTypeIconId[phoneId] != mMSimDataTypeIconId[phoneId]) {
            mMSimLastDataTypeIconId[phoneId] = mMSimDataTypeIconId[phoneId];
        }

        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i = 0; i < N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i = 0; i < N; i++) {
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
        for (int i = 0; i < N; i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(mobileLabel);
            if ("".equals(mobileLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }
        setCarrierText();
    }

    public int getVoiceNetworkType(int sub) {
        if (mMSimServiceState[sub] == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mMSimServiceState[sub].getVoiceNetworkType();
    }

    public int getDataNetworkType(int sub) {
        if (mMSimServiceState[sub] == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mMSimServiceState[sub].getDataNetworkType();
    }

    public int getGsmSignalLevel(int sub) {
        if (mMSimSignalStrength[sub] == null) {
            return mMSimSignalStrength[sub].SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
        return mMSimSignalStrength[sub].getGsmLevel();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args, int phoneId) {
        pw.println("NetworkController for SUB : " + phoneId + " state:");
        pw.println(String.format("  %s network type %d (%s)",
                mConnected ? "CONNECTED" : "DISCONNECTED",
                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        pw.print("  hasService()=");
        pw.println(hasService(phoneId));
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mMSimDataConnected=");
        pw.println(mMSimDataConnected[phoneId]);
        pw.print("  mMSimState=");
        pw.println(mMSimState[phoneId]);
        pw.print("  mPhoneState=");
        pw.println(mPhoneState);
        pw.print("  mDataState=");
        pw.println(mDataState);
        pw.print("  mMSimDataActivity=");
        pw.println(mMSimDataActivity[phoneId]);
        pw.print("  mDataNetType=");
        pw.print(mDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mDataNetType));
        pw.print("  mMSimServiceState=");
        pw.println(mMSimServiceState[phoneId]);
        pw.print("  mMSimSignalStrength=");
        pw.println(mMSimSignalStrength[phoneId]);
        pw.print("  mLastSignalLevel");
        pw.println(mLastSignalLevel);
        pw.print("  mMSimNetworkName=");
        pw.println(mMSimNetworkName[phoneId]);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n", "\\n"));
        pw.print("  mMSimPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimPhoneSignalIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimPhoneSignalIconId[phoneId]));
        pw.print("  mMSimDataDirectionIconId=");
        pw.print(Integer.toHexString(mMSimDataDirectionIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataDirectionIconId[phoneId]));
        pw.print("  mMSimDataSignalIconId=");
        pw.print(Integer.toHexString(mMSimDataSignalIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataSignalIconId[phoneId]));
        pw.print("  mMSimDataTypeIconId=");
        pw.print(Integer.toHexString(mMSimDataTypeIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataTypeIconId[phoneId]));

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
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled=");
            pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected=");
            pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle=");
            pw.println(mWimaxIdle);
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
        pw.print("  mMSimLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimLastPhoneSignalIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastPhoneSignalIconId[phoneId]));
        pw.print("  mMSimLastDataDirectionIconId=0x");
        pw.print(Integer.toHexString(mMSimLastDataDirectionIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastDataDirectionIconId[phoneId]));
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mMSimLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimLastCombinedSignalIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastCombinedSignalIconId[phoneId]));
        pw.print("  mMSimLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mMSimLastDataTypeIconId[phoneId]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastDataTypeIconId[phoneId]));
        pw.print("  mMSimLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
    }

    private final class SettingsObserver extends ContentObserver {
        private final ContentResolver mResolver;
        private boolean mRegistered;
        private final Uri EMPTY_ICONS_URI = Settings.System.getUriFor(
                Settings.System.STATUS_BAR_MSIM_SHOW_EMPTY_ICONS);

        public SettingsObserver(Handler handler) {
            super(handler);
            mResolver = mContext.getContentResolver();
        }

        public boolean showEmptySimIcons() {
            return Settings.System.getIntForUser(mResolver,
                    Settings.System.STATUS_BAR_MSIM_SHOW_EMPTY_ICONS, 1, mUserId) != 0;
        }

        public void register() {
            if (mRegistered) {
                mResolver.unregisterContentObserver(this);
            }
            mResolver.registerContentObserver(EMPTY_ICONS_URI, false, this, mUserId);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (EMPTY_ICONS_URI.equals(uri)) {
                updateSettings();
            }
        }

        private void updateSettings() {
            boolean showEmptySlots = showEmptySimIcons();
            for (MSimSignalCluster cluster : mSimSignalClusters) {
                cluster.setShowEmptySimSlots(showEmptySlots);
            }
        }
    }
}
