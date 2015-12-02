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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG_SIM_STATES;
    public static final String TAG = "KeyguardSimPinView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;
    private boolean mShowDefaultMessage = true;
    private int mRemainingAttempts = -1;
    private AlertDialog mRemainingAttemptsDialog;
    private int mSubId;
    private ImageView mSimImageView;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
           if (DEBUG) Log.v(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
           resetState();
       };
    };

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void resetState() {
        super.resetState();
        if (DEBUG) Log.v(TAG, "Resetting state");
        if (mShowDefaultMessage) {
            showDefaultMessage();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromtReasonStringRes(int reason) {
        // No message on SIM Pin
        return 0;
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining, boolean isDefault) {
        String displayMessage;
        int msgId;
        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            msgId = isDefault ? R.plurals.kg_password_default_pin_message :
                     R.plurals.kg_password_wrong_pin_code;
            displayMessage = getContext().getResources()
                    .getQuantityString(msgId, attemptsRemaining, attemptsRemaining);
        } else {
            msgId = isDefault ? R.string.kg_sim_pin_instructions : R.string.kg_password_pin_failed;
            displayMessage = getContext().getString(msgId);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simPinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        mSimImageView = (ImageView) findViewById(R.id.keyguard_sim);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mShowDefaultMessage) {
            showDefaultMessage();
        }
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        private int mSubId;

        protected CheckSimPin(String pin, int subId) {
            mPin = pin;
            mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.v(TAG, "call supplyPinReportResultForSubscriber(subid=" + mSubId + ")");
                }
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                if (DEBUG) {
                    Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining, false);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();

        if (entry.length() < 4) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            resetPasswordText(true);
            mCallback.userActivity();
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText(), mSubId) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            mRemainingAttempts = attemptsRemaining;
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            resetPasswordText(true /* animate */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                KeyguardUpdateMonitor.getInstance(getContext())
                                        .reportSimUnlocked(mSubId);
                                mRemainingAttempts = -1;
                                if (mCallback != null) {
                                    mCallback.dismiss(true);
                                }
                            } else {
                                mShowDefaultMessage = false;
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getPinPasswordErrorMessage(
                                                attemptsRemaining, false), true);
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    mSecurityMessageDisplay.setMessage(getContext().getString(
                                            R.string.kg_password_pin_failed), true);
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                            }
                            mCallback.userActivity();
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    private void showDefaultMessage() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        mSubId = monitor.getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED);
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
            return;
        }
        if (mRemainingAttempts >= 0) {
            mSecurityMessageDisplay.setMessage(getPinPasswordErrorMessage(
                    mRemainingAttempts, true), true);
            return;
        }

        int count = TelephonyManager.getDefault().getSimCount();
        Resources rez = getResources();
        final String msg;
        int color = Color.WHITE;
        if (count < 2) {
            msg = rez.getString(R.string.kg_sim_pin_instructions);
        } else {
            SubscriptionInfo info = monitor.getSubscriptionInfoForSubId(mSubId);
            CharSequence displayName = info != null ? info.getDisplayName() : ""; // don't crash
            msg = rez.getString(R.string.kg_sim_pin_instructions_multi, displayName);
            if (info != null) {
                color = info.getIconTint();
            }
        }
        mSecurityMessageDisplay.setMessage(msg, true);
        mSimImageView.setImageTintList(ColorStateList.valueOf(color));

        new CheckSimPin("", mSubId) {
            void onSimCheckResponse(final int result, final int attemptsRemaining) {
                Log.d(LOG_TAG, "onSimCheckResponse " + " dummy One result" + result +
                        " attemptsRemaining=" + attemptsRemaining);
                if (attemptsRemaining >= 0) {
                    mRemainingAttempts = attemptsRemaining;
                    mSecurityMessageDisplay.setMessage(
                            getPinPasswordErrorMessage(attemptsRemaining, true), true);
                }
            }
        }.start();
    }
}

