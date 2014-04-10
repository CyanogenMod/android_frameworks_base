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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
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
            Integer icon = mApnIconMap.get(apn);
            if (icon != null) {
                iconId = icon;
            }

            //if there is no records in apn settings, show ctwap icon as default.
            if (getSelectedApnKey() == null) {
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
                //          so if the selectedkey is null,
                //          We'd like to set it as the default apn.
                if (currentApn == null) {
                    if (selectedKey == null) {
                        for (int i = 0; i < apnList.size(); i++) {
                            Apn apn = apnList.get(i);
                            if (apn.type != null && apn.type.contains(DEFAULT)
                                    && apn.apn != null && apn.apn.contains(WAP)) {
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
            cr.registerContentObserver(Telephony.Carriers.CONTENT_URI, true, this);;
        }
    };

    private final ApnObserver mApnObserver;
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
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (intent.getBooleanExtra("state", false)) {
                    // The airplane mode is on, set the view as gone.
                    mApnState.enabled = false;
                    refreshApnTile();
                }
            }
        }
    }

    private boolean hasIccCard() {
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
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
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
        mApnObserver = new ApnObserver(mHandler);

        // APN switcher
        if (mContext.getResources().getBoolean(R.bool.config_showApnSwitch)) {
            mApnObserver.startObserving();
            // Register the receiver to handle the sim state changed event.
            // And caused by if we open the airplane mode, we couldn't receive the sim state
            // changed immediately, so we will also listen the airplane mode changed event.
            // If the new state is on, we need set the views as gone.
            IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
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
    }

    void updateResources() {
        super.updateResources();

        refreshApnTile();
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
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(!mRSSIState.enabled);
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
}
