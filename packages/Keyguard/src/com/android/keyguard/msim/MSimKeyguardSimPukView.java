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
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Message;
import android.telephony.MSimTelephonyManager;
import android.view.View;
import android.view.WindowManager;
import com.android.internal.telephony.PhoneConstants;

import com.android.internal.telephony.msim.ITelephonyMSim;

/**
 * Displays a PIN pad for entering a PUK (Pin Unlock Kode) provided by a carrier.
 */
public class MSimKeyguardSimPukView extends KeyguardSimPukView {
    private final static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static String TAG = "MSimKeyguardSimPukView";
    private MSimCheckSimPuk mCheckSimPukThread;

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
            mPinText="";
            mPukText="";
            state = ENTER_PUK;
            if (mShowDefaultMessage) {
                showDefaultMessage();
            }
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

        abstract void onSimLockChangedResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "MSimCheckSimPuk:run(), mPuk = " + mPuk
                    + " mPin = " + mPin + " mSubscription = " + mSubscription);
            try {
                final int[] result = ITelephonyMSim.Stub.asInterface(ServiceManager.checkService
                        ("phone_msim")).supplyPukReportResult(mPuk, mPin, mSubscription);
                Log.v(TAG, "supplyPukReportResult returned: " + result[0] + " " + result[1]);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPukReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    @Override
    protected Dialog getPukRemainingAttemptsDialog(int remaining) {
        String msg = getPukPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(getSecurityMessageDisplay(msg));
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(getSecurityMessageDisplay(msg));
        }
        return mRemainingAttemptsDialog;
    }
    @Override
    protected void updateSim() {
        getSimUnlockProgressDialog().show();

        if (DEBUG) Log.d(TAG, "updateSim()");

        if (mCheckSimPukThread == null) {

            mCheckSimPukThread = new MSimCheckSimPuk(mPukText, mPinText,
                    KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription()) {
                void onSimLockChangedResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked();
                                mCallback.dismiss(true);
                            } else {
                                mShowDefaultMessage = false;
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    mRemainingAttempts = attemptsRemaining;
                                    // show message
                                    mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                                            (getPukPasswordErrorMessage(attemptsRemaining)), true);
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getPukRemainingAttemptsDialog(attemptsRemaining).show();
                                    }
                                } else {
                                    mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                                            (R.string.kg_password_puk_failed), true);
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " UpdateSim.onSimCheckResponse: "
                                        + " attemptsRemaining=" + attemptsRemaining);
                                mStateMachine.reset();
                            }
                            mCheckSimPukThread = null;
                        }
                    });
                }
            };
            mCheckSimPukThread.start();
        }
    }

    protected CharSequence getSecurityMessageDisplay(int resId) {
        // Returns the String in the format
        // "SUB:%d : %s", sub, msg
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format,
                KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription()+1,
                getContext().getResources().getText(resId));
    }
    protected CharSequence getSecurityMessageDisplay(String msg) {
        // Returns the String in the format
        // "SUB:%d : %s", sub, msg
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format,
                KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription()+1,msg);
    }

    @Override
    protected void showDefaultMessage() {
        if (mRemainingAttempts >= 0) {
            mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                    (getPukDefaultMessage(mRemainingAttempts)), true);
            return;
        }
        int sub = KeyguardUpdateMonitor.getInstance(mContext).getPukLockedSubscription();
        new MSimCheckSimPuk("", "", sub) {
            void onSimLockChangedResponse(final int result, final int attemptsRemaining) {
                 if (attemptsRemaining >= 0) {
                    mRemainingAttempts = attemptsRemaining;
                    mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                            (getPukDefaultMessage(attemptsRemaining)), true);
                }
            }
        }.start();
    }
}

