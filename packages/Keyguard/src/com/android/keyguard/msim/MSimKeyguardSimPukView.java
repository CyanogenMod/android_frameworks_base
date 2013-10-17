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

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.msim.ITelephonyMSim;

/**
 * Displays a PIN pad for entering a PUK (Pin Unlock Kode) provided by a carrier.
 */
public class MSimKeyguardSimPukView extends KeyguardSimPukView {
    private final static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static String TAG = "MSimKeyguardSimPukView";

    protected class MSimStateMachine extends KeyguardSimPukView.StateMachine {
        public void next() {
            int msg = 0;
            if (state == ENTER_PUK) {
                if (checkPuk()) {
                    state = ENTER_PIN;
                    msg = R.string.kg_puk_enter_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_puk_hint;
                }
            } else if (state == ENTER_PIN) {
                if (checkPin()) {
                    state = CONFIRM_PIN;
                    msg = R.string.kg_enter_confirm_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_pin_hint;
                }
            } else if (state == CONFIRM_PIN) {
                if (confirmPin()) {
                    state = DONE;
                    msg = R.string.keyguard_sim_unlock_progress_dialog_message;
                    updateSim();
                } else {
                    state = ENTER_PIN; // try again?
                    msg = R.string.kg_invalid_confirm_pin_hint;
                }
            }
            mPasswordEntry.setText(null);
            if (msg != 0) {
                mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay(msg), true);
            }
        }

        void reset() {
            String  displayMessage = "";
            try {
                int attemptsRemaining = ITelephonyMSim.Stub.asInterface(ServiceManager
                        .checkService("phone_msim")).getIccPin1RetryCount(KeyguardUpdateMonitor
                        .getInstance(mContext).getPukLockedSubscription());
                if (attemptsRemaining >= 0) {
                    displayMessage = getContext().getString(
                            R.string.keyguard_password_wrong_puk_code)
                            + getContext().getString(R.string.pinpuk_attempts)
                            + attemptsRemaining + ". ";
                }
            } catch (RemoteException ex) {
                displayMessage = getContext().getString(
                        R.string.keyguard_password_puk_failed);
            }
            displayMessage = getSecurityMessageDisplay(R.string.kg_puk_enter_puk_hint)
                    + displayMessage;
            mPinText="";
            mPukText="";
            state = ENTER_PUK;
            mSecurityMessageDisplay.setMessage(displayMessage, true);
            mPasswordEntry.requestFocus();
        }
    }

    public MSimKeyguardSimPukView(Context context) {
        this(context, null);
    }

    public MSimKeyguardSimPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStateMachine = new MSimStateMachine();
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class MSimCheckSimPuk extends Thread {
        private final String mPin, mPuk;
        protected final int mSubscription;

        protected MSimCheckSimPuk(String puk, String pin, int sub) {
            mPuk = puk;
            mPin = pin;
            mSubscription = sub;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "MSimCheckSimPuk:run(), mPuk = " + mPuk
                    + " mPin = " + mPin + " mSubscription = " + mSubscription);
            try {
                final boolean result = ITelephonyMSim.Stub.asInterface(ServiceManager
                        .checkService("phone_msim")).supplyPuk(mPuk, mPin, mSubscription);

                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }

    @Override
    protected void updateSim() {
        getSimUnlockProgressDialog().show();

        if (DEBUG) Log.d(TAG, "updateSim()");

        if (!mCheckInProgress) {
            mCheckInProgress = true;

            new MSimCheckSimPuk(mPukText, mPinText,
                    KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription()) {
                void onSimLockChangedResponse(final boolean success) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                mCallback.dismiss(true);
                            } else {
                                mStateMachine.reset();
                                mSecurityMessageDisplay.setMessage(
                                    getSecurityMessageDisplay(R.string.kg_invalid_puk), true);
                            }
                            mCheckInProgress = false;
                        }
                    });
                }
            }.start();
        }
    }

    protected CharSequence getSecurityMessageDisplay(int resId) {
        // Returns the String in the format
        // "SUB:%d : %s", sub, msg
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format,
                KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription()+1,
                getContext().getResources().getText(resId));
    }
}

