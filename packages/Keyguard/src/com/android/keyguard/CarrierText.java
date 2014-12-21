/*
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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.text.method.SingleLineTransformationMethod;
import android.text.TextUtils;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

import java.util.Locale;
import java.util.HashMap;
import android.util.Log;

public class CarrierText extends LinearLayout {
    private static final String TAG = "CarrierText";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private static CharSequence mSeparator;

    private LockPatternUtils mLockPatternUtils;

    private boolean mShowAPM;

    private KeyguardUpdateMonitor mUpdateMonitor;
    private TextView mOperatorName[];
    private TextView mOperatorSeparator[];
    private TextView mAirplaneModeText;

    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onRefreshCarrierInfo(int subId, CharSequence plmn, CharSequence spn) {
            updateCarrierText(mUpdateMonitor.getSimState(subId), plmn, spn, subId);
        }

        @Override
        public void onSimStateChanged(int subId, IccCardConstants.State simState) {
            updateCarrierText(simState, mUpdateMonitor.getTelephonyPlmn(subId),
                mUpdateMonitor.getTelephonySpn(subId), subId);
        }

        @Override
        void onAirplaneModeChanged(boolean on) {
            if (on && mShowAPM) {
                for (int i = 0; i < mNumPhones; i++) {
                    mOperatorName[i].setVisibility(View.GONE);
                    if (i < mNumPhones-1) {
                        mOperatorSeparator[i].setVisibility(View.GONE);
                    }
                }
                if (mAirplaneModeText != null) {
                    mAirplaneModeText.setVisibility(View.VISIBLE);
                }
            } else {
                for (int i = 0; i < mNumPhones; i++) {
                    mOperatorName[i].setVisibility(View.VISIBLE);
                    if (i < mNumPhones-1) {
                        mOperatorSeparator[i].setVisibility(View.VISIBLE);
                    }
                }
                if (mAirplaneModeText != null) {
                    mAirplaneModeText.setVisibility(View.GONE);
                }
            }
        }

        public void onScreenTurnedOff(int why) {
            for (int i = 0; i < mNumPhones; i++) {
                mOperatorName[i].setSelected(false);
            }
        };

        public void onScreenTurnedOn() {
            for (int i = 0; i < mNumPhones; i++) {
                mOperatorName[i].setSelected(true);
            }
        };
    };
    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        PersoLocked, // SIM card is 'perso locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.
        SimIoError; //The sim card is faulty
    }

    public CarrierText(Context context) {
        this(context, null);
    }

    public CarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.keyguard_carrier_text_view, this, true);

        mLockPatternUtils = new LockPatternUtils(mContext);
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);

        mOperatorName = new TextView[mNumPhones];
        mOperatorSeparator = new TextView[mNumPhones-1];

        mShowAPM = context.getResources().getBoolean(R.bool.config_display_APM);
    }

    protected void updateCarrierText(State simState, CharSequence plmn, CharSequence spn,
            int subId) {
        if(DEBUG) Log.d(TAG, "updateCarrierText, simState=" + simState + " plmn=" + plmn
            + " spn=" + spn +" subId=" + subId);
        int phoneId = mUpdateMonitor.getPhoneIdBySubId(subId);
        if (!mUpdateMonitor.isValidPhoneId(phoneId)) {
            if(DEBUG) Log.d(TAG, "updateCarrierText, invalidate phoneId=" + phoneId);
            return;
        }

        String airplaneMode = getResources().getString(
                com.android.internal.R.string.lockscreen_airplane_mode_on);
        CharSequence text = getCarrierTextForSimState(simState, plmn, spn);
        TextView updateCarrierView = mOperatorName[phoneId];
        if (mAirplaneModeText != null && mShowAPM) {
            mAirplaneModeText.setText(airplaneMode);
        }
        updateCarrierView.setText(text != null ? text.toString() : null);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final int[] carrierTextViewId = { R.id.airplane_mode, R.id.carrier1, R.id.carrier2,
                R.id.carrier3, R.id.carrier_divider1, R.id.carrier_divider2 };
        for (int i = 0; i < carrierTextViewId.length; i++) {
            TextView carrierTextView = (TextView)findViewById(carrierTextViewId[i]);
            if (carrierTextView != null) {
                carrierTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(
                                com.android.internal.R.dimen.text_size_small_material));
           }
       }
   }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(com.android.internal.R.string.kg_text_message_separator);
        int[] operatorNameId = {R.id.carrier1, R.id.carrier2, R.id.carrier3};
        int[] operatorSepId = {R.id.carrier_divider1, R.id.carrier_divider2};
        final boolean screenOn = KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        setSelected(screenOn); // Allow marquee to work.

        for (int i = 0; i < mNumPhones; i++) {
            mOperatorName[i] = (TextView) findViewById(operatorNameId[i]);
            mOperatorName[i].setVisibility(View.VISIBLE);
            mOperatorName[i].setSelected(true);
            if (i < mNumPhones-1) {
                mOperatorSeparator[i] = (TextView) findViewById(operatorSepId[i]);
                mOperatorSeparator[i].setVisibility(View.VISIBLE);
                mOperatorSeparator[i].setText("|");
            }
        }
        mAirplaneModeText = (TextView) findViewById(R.id.airplane_mode);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mCallback);
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param plmn
     * @param spn
     * @return
     */
    private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence plmn, CharSequence spn) {
        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        if (DEBUG) Log.d(TAG, "getCarrierTextForSimState: status=" + status +
                " plmn=" + plmn + " spn=" + spn);
        switch (status) {
            case Normal:
                carrierText = concatenate(plmn, spn);
                break;

            case SimNotReady:
                carrierText = null; // nothing to display yet.
                break;

            case PersoLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_perso_locked_message), plmn);
                break;

            case SimMissing:
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        plmn);
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(
                        R.string.keyguard_permanent_disabled_sim_message_short);
                break;

            case SimMissingLocked:
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        plmn);
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        plmn);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        plmn);
                break;
            case SimIoError:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.lockscreen_sim_error_message_short),
                        plmn);
                break;
        }

        if (DEBUG) Log.d(TAG, "getCarrierTextForSimState: carrierText=" + carrierText);
        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return concatenate(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);

        // Assume we're PERSO_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? IccCardConstants.State.PERSO_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case PERSO_LOCKED:
                return StatusMode.PersoLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimMissing;
            case CARD_IO_ERROR:
                return StatusMode.SimIoError;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            if (plmn.equals(spn)) {
                return plmn;
            } else {
                return new StringBuilder().append(plmn).append(mSeparator).append(spn).toString();
            }
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    private CharSequence getCarrierHelpTextForSimState(IccCardConstants.State simState,
            String plmn, String spn) {
        int carrierHelpTextId = 0;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case PersoLocked:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;

            case SimMissingLocked:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;

            case Normal:
            case SimLocked:
            case SimPukLocked:
                break;
        }

        return mContext.getText(carrierHelpTextId);
    }

    private class CarrierTextTransformationMethod extends SingleLineTransformationMethod {
        private final Locale mLocale;
        private final boolean mAllCaps;

        public CarrierTextTransformationMethod(Context context, boolean allCaps) {
            mLocale = context.getResources().getConfiguration().locale;
            mAllCaps = allCaps;
        }

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);

            if (mAllCaps && source != null) {
                source = source.toString().toUpperCase(mLocale);
            }

            return source;
        }
    }
}
