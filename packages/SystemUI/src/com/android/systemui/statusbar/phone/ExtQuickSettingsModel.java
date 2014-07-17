/**
 * Copyright (C) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.view.RotationPolicy;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.HashMap;

class ExtQuickSettingsModel extends QuickSettingsModel {

    private static final String DDS_TAG = "DdsSwitch";
    private final Context mContext;

    // Roaming Data
    private int mPhoneCount;
    private boolean mIsForeignState = false;
    private QuickSettingsBasicTile mRoamingTile;
    private RefreshCallback mRoamingCallback;
    private State mRoamingState = new State();

    public class ApnState extends State {
        static final String TAG = "ApnState";
        static final boolean DEBUG = true;

        private final String DEFAULT = "default";
        private final String WAP = "wap";
        private final String LTE = "lte";

        private final Uri PREFERAPN_URI = Uri.parse("content://telephony/carriers/preferapn");
        private final String APN_ID = "apn_id";

        private final String[] PROJECTION = new String[] {
                "_id", "name", "apn", "type"
        };
        private final int INDEX_ID = 0;
        private final int INDEX_NAME = 1;
        private final int INDEX_APN = 2;
        private final int INDEX_TYPE = 3;

        private HashMap<String, Integer> mApnIconMap = new HashMap<String, Integer>();
        private Apn mCurrentApn;
        private Apn mNextApn;

        public ApnState() {
            mApnIconMap.put("ctwap", R.drawable.ic_qs_apn_ctwap);
            mApnIconMap.put("ctnet", R.drawable.ic_qs_apn_ctnet);
            mApnIconMap.put("ctlte", R.drawable.ic_qs_apn_ctlte);
            mApnIconMap.put("cmnet", R.drawable.ic_qs_apn_cmnet);
            mApnIconMap.put("cmwap", R.drawable.ic_qs_apn_cmwap);
        }

        /**
         * Switch the current apn to next apn.
         *
         * @param apn The next apn you want to switch.
         *            If the value is null, will use the saved next apn to switch.
         */
        public void switchToNextApn(Apn apn) {
            // if the given next apn is null, we will use the saved next apn to switch.
            if (apn == null) {
                apn = mNextApn;
            }

            // We will only set the prefer apn to the next apn, and then the data base will be
            // changed, so we will update the view when we receive the content changed message.
            if (apn != null) {
                if (DEBUG) Log.i(TAG, "switch to the next apn, and it's id: " + apn.id);
                ContentValues values = new ContentValues();
                values.put(APN_ID, apn.id);
                mContext.getContentResolver().update(PREFERAPN_URI, values, null, null);
            }
        }

        /**
         * To get the current apn name
         * @return the current apn name saved as apn's apn
         */
        public String getCurrentApnName() {
            String apn = "";
            if (mCurrentApn != null && mCurrentApn.apn != null) {
                apn = mCurrentApn.apn;
            }
            return apn;
        }

        void updateIconId() {
            String apn = getCurrentApnName();
            Integer icon = mApnIconMap.get(apn.toLowerCase());
            if (icon != null) {
                iconId = icon;
            } else {
                iconId = R.drawable.ic_qs_apn_ctwap;
            }
        }

        /**
         * To update the apn list, and it will update the current apn and next apn.
         */
        public void updateApnList() {
            if (DEBUG) Log.i(TAG, "Try to update the apn list.");
            Apn currentApn = null;
            Apn nextApn = null;

            ArrayList<Apn> apnList = new ArrayList<Apn>();

            Cursor cursor = null;
            try {
                String where = getOperatorNumericSelection();
                if (TextUtils.isEmpty(where)) {
                    Log.d(TAG, "getOperatorNumericSelection is empty ");
                    return;
                }
                String selectedKey = getSelectedApnKey();

                cursor = mContext.getContentResolver().query(Telephony.Carriers.CONTENT_URI,
                        PROJECTION, where, null,
                        Telephony.Carriers.DEFAULT_SORT_ORDER);
                if (cursor == null) {
                    Log.e(TAG, "When update the apn list, the cursor is null.");
                    return;
                }

                boolean getNextApn = false;
                while (cursor.moveToNext()) {
                    Apn apn = new Apn();
                    apn.id = cursor.getString(INDEX_ID);
                    apn.name = cursor.getString(INDEX_NAME);
                    apn.apn = cursor.getString(INDEX_APN);
                    apn.type = cursor.getString(INDEX_TYPE);

                    boolean selectable = ((apn.type == null) || !apn.type.equals("mms"));
                    if (!selectable) {
                        // if the type is mms, we needn't to handle it.
                        continue;
                    }

                    apnList.add(apn);
                    if (getNextApn) {
                        nextApn = apn;
                        getNextApn = false;
                        break;
                    }

                    // As android default, the DUT will not set the default selected apn,
                    //so it maybe null.  And we will deal with this case after.
                    if (selectedKey != null && selectedKey.equals(apn.id)) {
                        currentApn = apn;
                        if (cursor.isLast()) {
                            // If the current apn is the last cursor,
                            //we need set the next apn as the first in the apn list.
                            nextApn = apnList.get(0);
                            getNextApn = false;
                            break;
                        } else {
                            // We need try to get the next apn if next cursor is not mms.
                            getNextApn = true;
                        }
                    }
                }
                if (getNextApn) {
                    // We have read all the cursor, but we also need get the next apn,
                    //it means the next apn is the first in the apn list.
                    nextApn = apnList.get(0);
                }

                // Sometimes we didn't find the default apn for example, after we reset the DUT,
                // the selectedkey will be null.
                // Note: As ct spec, we need set the ctwap as the default apn,
                //          so if the selectedkey is null or the selected apn is not in current
                //          available apn list, we'd like to set it as the default apn.
                if (currentApn == null) {
                    if (selectedKey == null) {
                        for (int i = 0; i < apnList.size(); i++) {
                            Apn apn = apnList.get(i);
                            if (apn.type != null && apn.type.contains(DEFAULT)
                                    && apn.apn != null && (apn.apn.toLowerCase().contains(WAP)
                                    || apn.apn.toLowerCase().contains(LTE))) {
                                switchToNextApn(apn);
                            }
                        }
                    } else {
                        // If the selectedkey is not null, but we didn't find the current apn.
                        // it maybe the data has been set to slot2.
                        // TODO: if we need add some support for the use set the data to slot2?
                        // Now, we will leave it as last value.
                        Log.w(TAG, "The selected key is: " + selectedKey
                            + ", but we didn't matched the current apn.");
                    }
                } else {
                    mCurrentApn = currentApn;
                    mNextApn = nextApn;
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
        }

        private String getOperatorNumericSelection() {
            String[] mccmncs = getOperatorNumeric();
            String where;
            where = (mccmncs[0] != null) ? "numeric=\"" + mccmncs[0] + "\"" : "";
            where = where + ((mccmncs[1] != null) ? " or numeric=\"" + mccmncs[1] + "\"" : "");

            int netType = 0;
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                int dataSub = MSimTelephonyManager.getDefault().getPreferredDataSubscription();
                netType = MSimTelephonyManager.getDefault().getNetworkType(dataSub);
            } else {
                netType = TelephonyManager.getDefault().getNetworkType();
            }

            //UI should filter APN by bearer and enable status
            int radioType = convertNetworkTypeToRilRadioType(netType);
            Log.d(TAG, "Current RAT type is " + radioType);
            where += "and (bearer=\"" + radioType + "\" or bearer =\"" + 0 + "\")";
            where += " and carrier_enabled = 1";

            if (DEBUG) Log.d(TAG, "getOperatorNumericSelection: " + where);
            return where;
        }

        private String[] getOperatorNumeric() {
            ArrayList<String> result = new ArrayList<String>();
            if (SystemProperties.getBoolean("persist.radio.use_nv_for_ehrpd", false)) {
                String mccMncForEhrpd = SystemProperties.get("ro.cdma.home.operator.numeric", null);
                if (mccMncForEhrpd != null && mccMncForEhrpd.length() > 0) {
                    result.add(mccMncForEhrpd);
                }
            }

            int dataSub = 0;
            String property = null;
            String mccMncFromSim = null;
            int activePhone = 0;

            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                MSimTelephonyManager msimTM = (MSimTelephonyManager)
                        mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
                dataSub = msimTM.getPreferredDataSubscription();
                activePhone = MSimTelephonyManager.getDefault().getPhoneType(dataSub);
            } else {
                dataSub = TelephonyManager.getDefaultSubscription();
                activePhone = TelephonyManager.getDefault().getPhoneType(dataSub);
            }

            if (activePhone == PhoneConstants.PHONE_TYPE_CDMA) {
                property = TelephonyProperties.PROPERTY_APN_RUIM_OPERATOR_NUMERIC;
            } else {
                property = TelephonyProperties.PROPERTY_APN_SIM_OPERATOR_NUMERIC;
            }

            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                mccMncFromSim = MSimTelephonyManager
                        .getTelephonyProperty(property, dataSub, null);
            } else {
                mccMncFromSim = TelephonyManager
                        .getTelephonyProperty(property, dataSub, null);
            }

            if (mccMncFromSim != null && mccMncFromSim.length() > 0) {
                result.add(mccMncFromSim);
            }
            return result.toArray(new String[2]);
        }

        private String getSelectedApnKey() {
            String key = null;

            Cursor cursor = null;
            try {
                cursor = mContext.getContentResolver().query(PREFERAPN_URI, new String[] {"_id"},
                        null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    key = cursor.getString(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return key;
        }

        public class Apn {
            String id;
            String name;
            String apn;
            String type;
        }
    }

    /** ContentObserver to watch apn switch **/
    private class ApnObserver extends ContentObserver {
        static final String TAG = "ApnState";
        static final boolean DEBUG = true;

        public ApnObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (DEBUG) Log.i(TAG, "ApnObserver, will try to update the apn views.");
            refreshApnTile();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(Telephony.Carriers.CONTENT_URI, true, this);
            cr.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.MULTI_SIM_DEFAULT_DATA_CALL_SUBSCRIPTION),
                    false, this, mUserTracker.getCurrentUserId());
        }
    };

    private ApnObserver mApnObserver;
    private QuickSettingsTileView mApnTile;
    private RefreshCallback mApnCallback;
    private ApnState mApnState = new ApnState();

    /** Broadcast receive to determine if there is an apn state change. */
    private class SimStateChangedReceiver extends BroadcastReceiver {
        static final String TAG = "ApnState";
        static final boolean DEBUG = true;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG) Log.i(TAG, "SimStateChangedReceiver, receive the action: " + action);

            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                mApnState.enabled = hasIccCard();
                if (mApnState.enabled) {
                    // we need make sure the icc state has been loaded complete and then
                    // update the apn view. If not we will maybe got the error numeric.
                    String iccState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(iccState)) {
                        refreshApnTile();
                    }
                }
            } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                if (mApnState.enabled) {
                    refreshApnTile();
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (intent.getBooleanExtra("state", false)) {
                    // The airplane mode is on, set the view as gone.
                    mApnState.enabled = false;
                    refreshApnTile();
                }
            }
        }
    }

    public boolean hasIccCard() {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager msimTM =
                (MSimTelephonyManager) mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
            int prfDataSub = msimTM.getPreferredDataSubscription();
            int simState = msimTM.getSimState(prfDataSub);
            boolean active = simState != TelephonyManager.SIM_STATE_ABSENT
                    && simState != TelephonyManager.SIM_STATE_UNKNOWN;
            return active && msimTM.hasIccCard(prfDataSub);
        } else {
            TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            return tm.hasIccCard();
        }
    }

    /** ContentObserver to watch mobile data on/off **/
    private class DataSwitchObserver extends ContentObserver {
        public DataSwitchObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onMobileDataSwitchChanged();
            updateDds();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                    false, this, mUserTracker.getCurrentUserId());

            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                    false, this, mUserTracker.getCurrentUserId());
        }
    }

    /** ContentObserver to roaming data **/
    private class RoamingDataObserver extends ContentObserver {
        public RoamingDataObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onRoamingDataStateChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    Settings.Global.CONTENT_URI, true, this);
        }
    }

    public ExtQuickSettingsModel(Context context) {
        super(context);
        mContext = context;

        // APN switcher
        if (mContext.getResources().getBoolean(R.bool.config_showApnSwitch)) {
            mApnObserver = new ApnObserver(mHandler);
            mApnObserver.startObserving();
            // Register the receiver to handle the sim state changed event.
            // And caused by if we open the airplane mode, we couldn't receive the sim state
            // changed immediately, so we will also listen the airplane mode changed event.
            // If the new state is on, we need set the views as gone.
            IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
            context.registerReceiver(new SimStateChangedReceiver(), filter);
        }

        if (dataSwitchEnabled()) {
            new DataSwitchObserver(mHandler).startObserving();
        }

        if (mContext.getResources().getBoolean(R.bool.config_showRoamingSetting)) {
            Handler handler = new Handler();
            RoamingDataObserver roamingDataObserver = new RoamingDataObserver(handler);
            roamingDataObserver.startObserving();
        }

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (mContext.getResources().getBoolean(R.bool.config_showDdsSwitch)) {
                new DdsSwitchObserver(mHandler).startObserving();
            }
        }

        if (mContext.getResources().getBoolean(R.bool.config_showRingerModeSwitch)) {
            IntentFilter ringerIntentFilter = new IntentFilter();
            ringerIntentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            context.registerReceiver(mRingerModeReceiver, ringerIntentFilter);
        }
    }

    void updateResources() {
        super.updateResources();

        refreshApnTile();
        updateDds();
        refreshRingerModeTile();
    }

    public QuickSettingsBasicTile addRoamingTile() {
        mRoamingTile = new QuickSettingsBasicTile(mContext);
        mRoamingTile.setText(mContext.getResources()
                    .getString(R.string.accessibility_data_connection_roaming));
        mRoamingTile.setVisibility(View.VISIBLE);
        mRoamingTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enable = getDataRoaming(MSimConstants.SUB1);
                setDataRoaming(!enable, MSimConstants.SUB1);
            }
        });

        mRoamingCallback = new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView unused, State state) {
                if (state.enabled) {
                    mRoamingTile.setImageResource(R.drawable.roam_on);
                } else {
                    mRoamingTile.setImageResource(R.drawable.roam_off);
                }
            }
        };

        // Get phone count
        mPhoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        // Set RoamingTile as GONE
        mRoamingTile.setVisibility(View.GONE);

        return mRoamingTile;
    }

    private boolean isValidNumeric(String numeric) {
        if (TextUtils.isEmpty(numeric)
                || numeric.equals("null") || numeric.equals("00000")) {
            return false;
        }

        return true;
    }

    public void onRoamingVisibleChanged() {
        final String CHINA_MCC = "460";
        final String MACAO_MCC = "455";

        String numeric;
        boolean isForeign;

        if (mPhoneCount > 1) {
            numeric = MSimTelephonyManager.getDefault()
                    .getNetworkOperator(MSimConstants.SUB1);
        } else {
            numeric = TelephonyManager.getDefault().getNetworkOperator();
        }

        // Return if invaild values
        if (!isValidNumeric(numeric)) {
            return;
        }

        if (numeric.startsWith(CHINA_MCC) || numeric.startsWith(MACAO_MCC)) {
            isForeign = false;
        } else {
            isForeign = true;
        }

        if (isForeign != mIsForeignState) {
            if (isForeign) {
                mRoamingTile.setVisibility(View.VISIBLE);
                mRoamingState.enabled = getDataRoaming(MSimConstants.SUB1);
                mRoamingCallback.refreshView(mRoamingTile, mRoamingState);
            } else {
                mRoamingTile.setVisibility(View.GONE);
            }
        }
        mIsForeignState = isForeign;
    }

    // APN switch
    void addApnTile(QuickSettingsTileView view, RefreshCallback cb) {
        mApnTile = view;
        mApnCallback = cb;
        refreshApnTile();
    }
    void refreshApnTile() {
        int dataSub = MSimConstants.DEFAULT_SUBSCRIPTION;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager msimTM = (MSimTelephonyManager)
                    mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
            dataSub = msimTM.getDefaultDataSubscription();
        }
        // Hide APN switch button if DDS is set to non default subscription
        if (dataSub != MSimConstants.DEFAULT_SUBSCRIPTION) {
            mApnState.enabled = false;
        } else {
            mApnState.enabled = hasIccCard() && !isAirplaneModeOn();
        }

        if (mApnTile != null && mApnCallback != null) {
            Resources res = mContext.getResources();
            if (mApnState.enabled) {
                mApnState.updateApnList();
            }
            mApnState.label = res.getString(R.string.quick_settings_apn_switch_label);
            mApnState.updateIconId();
            mApnCallback.refreshView(mApnTile, mApnState);
        }
    }
    public ApnState getApnState() {
        return mApnState;
    }

    void switchMobileDataState() {
        // Do not make mobile data on/off if airplane mode on.
        if (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0 || !hasIccCard()) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(!mRSSIState.enabled);
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.MOBILE_DATA + i, (!mRSSIState.enabled) ? 1 : 0);
        }
    }

    private void onRoamingDataStateChanged() {
        // Update Roaming Data State
        mRoamingState.enabled = getDataRoaming(MSimConstants.SUB1);
        mRoamingCallback.refreshView(mRoamingTile, mRoamingState);
    }

    // Get Data roaming flag, from DB, as per SUB.
    private boolean getDataRoaming(int sub) {
        String val = mPhoneCount > 1 ? String.valueOf(sub) : "";
        boolean enabled = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING + val, 0) != 0;
        return enabled;
    }

    // Set Data roaming flag, in DB, as per SUB.
    private void setDataRoaming(boolean enabled, int sub) {
        if (mPhoneCount > 1) {
            // as per SUB, set the individual flag
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING + sub, enabled ? 1 : 0);

            if (sub == MSimTelephonyManager.getDefault().getPreferredDataSubscription()) {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
            }
        } else {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
        }
    }

    private class DdsSwitchObserver extends ContentObserver {
        public DdsSwitchObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(DDS_TAG, "mobile data or data sub is changed.");
            updateDds();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                    false, this, mUserTracker.getCurrentUserId());
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(
                    Settings.Global.MULTI_SIM_DEFAULT_DATA_CALL_SUBSCRIPTION),
                    false, this, mUserTracker.getCurrentUserId());
        }
    }

    private QuickSettingsTileView mDdsTile;
    private RefreshCallback mDdsCallback;
    private State mDdsState = new State();
    private AsyncTask switchDdsAsyncTask = null;
    protected void addDdsTile(QuickSettingsTileView view, RefreshCallback cb) {
        mDdsTile = view;
        mDdsCallback = cb;

        mDdsState.enabled = false;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager msimTM = (MSimTelephonyManager)
                    mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
            MSimTelephonyManager.MultiSimVariants subConfig
                    = msimTM.getMultiSimConfiguration();
            if (subConfig == MSimTelephonyManager.MultiSimVariants.DSDA) {
                mDdsState.enabled = true;
            }
        }

        updateDds();
    }

    void refreshDdsTile() {
        if (mDdsTile != null && mDdsCallback != null) {
            mDdsCallback.refreshView(mDdsTile, mDdsState);
        }
    }

    void switchDdsToNext() {
        Log.d(DDS_TAG, "switchDdsToNext");
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            return;
        }
        if (switchDdsAsyncTask != null &&
                switchDdsAsyncTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.d(DDS_TAG, "Dds switch in progress!");
            return;
        }
        switchDdsAsyncTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                // Make DDS switch grayed out and while changing subscription
                if (mDdsTile != null) {
                    mDdsTile.setAlpha(0.5f);
                    mDdsTile.setEnabled(false);
                }
            }
            @Override
            protected Void doInBackground(Void... params) {
                MSimTelephonyManager msimTM = (MSimTelephonyManager)
                        mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
                int dataSub = msimTM.getDefaultDataSubscription();
                int phoneCount = msimTM.getPhoneCount();
                msimTM.setDefaultDataSubscription((dataSub+1)%phoneCount);
                return null;
            }
            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                if (mDdsTile != null) {
                    mDdsTile.setAlpha(1f);
                    mDdsTile.setEnabled(true);
                }
            }
        }.execute();
    }

    private void updateDds() {
        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);

        int dataSub = MSimConstants.SUB1;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager msimTM = (MSimTelephonyManager)
                    mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
            dataSub = msimTM.getDefaultDataSubscription();
        }

        if (cm != null && cm.getMobileDataEnabled()) {
            Log.d(DDS_TAG, "mobile data is on.");
            switch(dataSub) {
                case MSimConstants.SUB1:
                    mDdsState.iconId = R.drawable.ic_qs_data_on_1;
                    break;
                case MSimConstants.SUB2:
                    mDdsState.iconId = R.drawable.ic_qs_data_on_2;
                    break;
                case MSimConstants.SUB3:
                default:
                    mDdsState.iconId = 0;
                    break;
            }
            mDdsState.label = mContext.getString(R.string.quick_settings_dds_sub, dataSub + 1);
        } else {
            Log.d(DDS_TAG, "mobile data is off.");
            switch(dataSub) {
                case MSimConstants.SUB1:
                    mDdsState.iconId = R.drawable.ic_qs_data_off_1;
                    break;
                case MSimConstants.SUB2:
                    mDdsState.iconId = R.drawable.ic_qs_data_off_2;
                    break;
                case MSimConstants.SUB3:
                default:
                    mDdsState.iconId = 0;
                    break;
            }
            mDdsState.label = mContext.getString(R.string.quick_settings_data_off);
        }

        refreshDdsTile();
    }

    private int convertNetworkTypeToRilRadioType(int networkType) {
        int ret = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT;
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0;
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A;
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B;
                break;
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD;
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                ret = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            default:
                ret = networkType;
                break;
        }
        return ret;
    }

    /** Broadcast receive to get ringer mode. */
    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                onRingerModeChanged();
            }
        }
    };

    //Ringer Mode
    private QuickSettingsTileView mRingerModeTile;
    private RefreshCallback mRingerModeCallback;
    private State mRingerModeState = new State();
    private ArrayList<Integer> mRingerModes = new ArrayList<Integer>();

    void addRingerModeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mRingerModeTile = view;
        mRingerModeCallback = cb;

        // Load ringer modes
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mRingerModes.clear();
        mRingerModes.add(AudioManager.RINGER_MODE_SILENT);
        if (vibrator != null && vibrator.hasVibrator()) {
            mRingerModes.add(AudioManager.RINGER_MODE_VIBRATE);
        }
        mRingerModes.add(AudioManager.RINGER_MODE_NORMAL);

        onRingerModeChanged();
    }

    public void onRingerModeChanged() {
        AudioManager mAudioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        final int ringerMode = mAudioManager.getRingerMode();
        Resources r = mContext.getResources();
        TypedArray iconsTypedArray = r.obtainTypedArray(R.array.ringer_mode_icon);
        mRingerModeState.iconId = iconsTypedArray.getResourceId(ringerMode, 0);
        TypedArray labelsTypedArray = r.obtainTypedArray(R.array.ringer_mode_label);
        mRingerModeState.label = labelsTypedArray.getString(ringerMode);

        if (mRingerModeCallback != null) {
            mRingerModeCallback.refreshView(mRingerModeTile, mRingerModeState);
        }
    }

    public void refreshRingerModeTile() {
        if (mRingerModeTile != null) {
            onRingerModeChanged();
        }
    }

    public void switchNextRingerMode() {
        AudioManager mAudioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        final int currentIndex = mRingerModes.indexOf(mAudioManager.getRingerMode());
        final int nextRingerMode =
                mRingerModes.get((currentIndex + 1) % mRingerModes.size());
        mAudioManager.setRingerMode(nextRingerMode);
    }
}
