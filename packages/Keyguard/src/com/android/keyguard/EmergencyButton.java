/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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

package com.android.keyguard;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

import java.util.HashMap;


/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {

    private static final int EMERGENCY_CALL_TIMEOUT = 10000; // screen timeout after starting e.d.
    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";
    private HashMap<Integer, ServiceState> mServiceState = new HashMap<Integer, ServiceState>();

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(int subId, State simState) {
            int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
            updateEmergencyCallButton(phoneState);
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton(phoneState);
        }

        void onServiceStateChanged(ServiceState state, int sub) {
            mServiceState.put(sub, state);
            int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
            updateEmergencyCallButton(phoneState);
        }
    };
    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeEmergencyCallAction();
            }
        });
        int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
        mServiceState = KeyguardUpdateMonitor.getInstance(mContext).getServiceStates();
        updateEmergencyCallButton(phoneState);
    }

    private boolean canMakeEmergencyCall() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        for (int i = 0; i < monitor.getNumPhones(); i++) {
            ServiceState state = mServiceState.get(monitor.getSubIdByPhoneId(i));
            if ((state != null) && (state.isEmergencyOnly() ||
                    state.getVoiceRegState() != ServiceState.STATE_OUT_OF_SERVICE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        if (mLockPatternUtils.isInCall()) {
            mLockPatternUtils.resumeCall();
        } else {
            final boolean bypassHandler = true;
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(bypassHandler);
            Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            getContext().startActivityAsUser(intent,
                    new UserHandle(mLockPatternUtils.getCurrentUser()));
        }
    }

    private void updateEmergencyCallButton(int phoneState) {
        boolean enabled = false;
        if (mLockPatternUtils.isInCall()) {
            enabled = true; // always show "return to call" if phone is off-hook
        } else if (mLockPatternUtils.isEmergencyCallCapable()) {
            boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext).isSimLocked();
            if (simLocked) {
                // Some countries can't handle emergency calls while SIM is locked.
                enabled = mLockPatternUtils.isEmergencyCallEnabledWhileSimLocked();
            } else {
                // True if we need to show a secure screen (pin/pattern/SIM pin/SIM puk);
                // hides emergency button on "Slide" screen if device is not secure.
                enabled = mLockPatternUtils.isSecure() ||
                        mContext.getResources().getBoolean(R.bool.config_showEmergencyButton);
            }
        }
        if (mContext.getResources().getBoolean(R.bool.config_hideEmergencyButtonInOOS)) {
            enabled = enabled && canMakeEmergencyCall();
        }
        mLockPatternUtils.updateEmergencyCallButtonState(this, enabled, false);
    }

}
