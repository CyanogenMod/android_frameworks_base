/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;

import android.telephony.MSimTelephonyManager;

public class MSimCarrierText extends CarrierText {
    private static final boolean DEBUG = false;
    private static final String TAG = MSimCarrierText.class.getSimpleName();
    private String []mPlmn;
    private String []mSpn;
    private boolean[] mShowSpn;
    private boolean[] mShowPlmn;
    private State []mSimState;
    private String[] mMSimNetworkName;
    private CharSequence[] mCarrierTextSub;
    private TextView[] mTextViewLabels;

    private String mNetworkNameDefault;
    private String mEmergencyCallOnlyLabel;
    private String mNetworkNameSeparator;

    private KeyguardUpdateMonitorCallback mMSimCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int sub) {
            // we have our own broadcast receiver to handle these
        }

        @Override
        public void onSimStateChanged(IccCardConstants.State simState, int sub) {
            mSimState[sub] = simState;
            updateCarrierText(sub);
            setCarrierText();
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextViewLabels = new TextView[3];
        mTextViewLabels[0] = (TextView) findViewById(R.id.sub1_label);
        mTextViewLabels[1] = (TextView) findViewById(R.id.sub2_label);
        mTextViewLabels[2] = (TextView) findViewById(R.id.sub3_label);

        if (MSimTelephonyManager.getDefault().getPhoneCount() == 3) {
            findViewById(R.id.sub2_separator).setVisibility(View.VISIBLE);
            findViewById(R.id.sub3_label).setVisibility(View.VISIBLE);
        }

    }

    private void initialize() {
        mNetworkNameDefault = mContext.getString(
                com.android.internal.R.string.lockscreen_carrier_default);
        mEmergencyCallOnlyLabel = mContext.getString(com.android.internal.R.string
                .emergency_calls_only);
        mNetworkNameSeparator = mContext.getString(R.string.network_name_separator);
        final int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        mMSimNetworkName = new String[numPhones];
        mPlmn = new String[numPhones];
        mSpn = new String[numPhones];
        mSimState = new State[numPhones];
        for (int i = 0; i < mSimState.length; i++) {
            mSimState[i] = State.UNKNOWN;
        }
        mShowSpn = new boolean[numPhones];
        mShowPlmn = new boolean[numPhones];
        mCarrierTextSub = new CharSequence[numPhones];

        MSimTelephonyManager tm = MSimTelephonyManager.getDefault();
        for (int i=0; i < tm.getPhoneCount(); i++) {
            mSpn[i] = tm.getSimOperatorName(i);
            mPlmn[i] = tm.getNetworkOperatorName(i);
            updateNetworkName(true, mSpn[i],  true, mPlmn[i], i);
        }
    }

    public MSimCarrierText(Context context) {
        this(context, null);
    }

    public MSimCarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void updateCarrierText(int sub) {
        int textResId = 0;
// do we care about airplane mode when card is locked on lockscreen?
//        if (mAirplaneMode) {
//            textResId = com.android.internal.R.string.lockscreen_airplane_mode_on;
//        } else {
        if (DEBUG) {
            Log.d(TAG, "updateCarrierText for sub:" + sub + " simState =" + mSimState[sub]);
        }

        switch (mSimState[sub]) {
            case ABSENT:
            case UNKNOWN:
            case NOT_READY:
                textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                break;
            case PIN_REQUIRED:
                textResId = R.string.keyguard_sim_locked_message;
                break;
            case PUK_REQUIRED:
                textResId = R.string.keyguard_sim_puk_locked_message;
                break;
            case READY:
                // If the state is ready, set the text as network name.
                mCarrierTextSub[sub] = mMSimNetworkName[sub];
                break;
            case PERM_DISABLED:
                textResId = R.string.keyguard_permanent_disabled_sim_message_short;
                break;
            case CARD_IO_ERROR:
                textResId = R.string.lockscreen_sim_error_message_short;
                break;
            default:
                textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                break;
        }
//        }

        if (textResId != 0) {
            mCarrierTextSub[sub] = mContext.getString(textResId);
        }
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn,
                           int subscription) {
        if (DEBUG) {
            Log.d(TAG, "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            plmn = maybeStripPeriod(plmn);
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something && showPlmn
                    && !spn.equals(plmn) &&
                    !mEmergencyCallOnlyLabel.equals(plmn)) {
                str.append("  ");
                str.append(mNetworkNameSeparator);
                str.append("  ");
                str.append(spn);
            } else if (!showPlmn) {
                str.append(spn);
                something = true;
            }
        }
        if (something) {
            mMSimNetworkName[subscription] = str.toString();
        } else {
            mMSimNetworkName[subscription] = mNetworkNameDefault;
        }
        mMSimNetworkName[subscription] = maybeStripPeriod(mMSimNetworkName[subscription]);
        if (DEBUG) {
            Log.d(TAG, "mMSimNetworkName[subscription] " + mMSimNetworkName[subscription]
                    + "subscription " + subscription);
        }
    }

    protected String maybeStripPeriod(String name) {
        if (!TextUtils.isEmpty(name)) {
            return name.equals(mNetworkNameDefault) ?
                    name.replace(".", "") :
                    name;
        }
        return name;
    }

    private void setCarrierText() {
        if (mTextViewLabels != null) {
            if (mTextViewLabels[MSimConstants.SUB1] != null) {
                mTextViewLabels[MSimConstants.SUB1].setText(mCarrierTextSub[MSimConstants.SUB1]);
            }
            if (mTextViewLabels[MSimConstants.SUB2] != null) {
                mTextViewLabels[MSimConstants.SUB2].setText(mCarrierTextSub[MSimConstants.SUB2]);
            }
            if (mCarrierTextSub.length == 3 && mTextViewLabels[MSimConstants.SUB3] != null) {
                mTextViewLabels[MSimConstants.SUB3].setText(mCarrierTextSub[MSimConstants.SUB3]);
            }
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
                final int subscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY, 0);
                if (DEBUG) Log.d(TAG, "Received SPN update on sub :" + subscription);
                mShowSpn[subscription] = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
                mSpn[subscription] = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
                mShowPlmn[subscription] = intent.getBooleanExtra(
                        TelephonyIntents.EXTRA_SHOW_PLMN, false);
                mPlmn[subscription] = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);

                updateNetworkName(mShowSpn[subscription], mSpn[subscription], mShowPlmn[subscription],
                        mPlmn[subscription], subscription);
                updateCarrierText(subscription);
                setCarrierText();
            }
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mMSimCallback);
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
//        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mBroadcastReceiver);
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mMSimCallback);
    }
}

