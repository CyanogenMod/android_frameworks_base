/*
 * Copyright (c) 2014 The Linux Foundation. All rights reserved.
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
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants;


/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    public static final String TAG = "KeyguardSimPinView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;
    private boolean mShowDefaultMessage = true;
    private int mRemainingAttempts = -1;
    private AlertDialog mRemainingAttemptsDialog;
    KeyguardUpdateMonitor mKgUpdateMonitor;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private TextView mSubNameView;
    private ImageView mSimImageView;

    private KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSubIdUpdated(int oldSubId, int newSubId) {
            if (mSubId == oldSubId) {
                mSubId = newSubId;
                //subId updated, handle sub info changed.
                handleSubInfoChange();
            }
        }

        @Override
        public void onSubInfoContentChanged(int subId, String column,
                                String sValue, int iValue) {
            if (column != null && column.equals(SubscriptionManager.DISPLAY_NAME)
                    && mSubId == subId) {
                //display name changed, handle sub info changed.
                handleSubInfoChange();
            }
        }

        @Override
        public void onSimStateChanged(int subId, IccCardConstants.State simState) {
            if (DEBUG) Log.d(TAG, "onSimStateChangedUsingSubId: " + simState + ", subId=" + subId);
            if (subId != mSubId) return;
            switch (simState) {
                case NOT_READY:
                case ABSENT:
                        closeKeyGuard();
                    break;
            }
        }
    };

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mKgUpdateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
    }

    public void resetState() {
        super.resetState();
        handleSubInfoChangeIfNeeded();
        if (mShowDefaultMessage) {
            showDefaultMessage();
        }
        mPasswordEntry.setEnabled(true);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining, boolean isDefault) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            int msgId = isDefault ? R.plurals.kg_password_default_pin_message :
                    R.plurals.kg_password_wrong_pin_code;
            displayMessage = getContext().getResources()
                    .getQuantityString(msgId, attemptsRemaining, attemptsRemaining);
        } else {
            int msgId = isDefault ? R.string.kg_sim_pin_instructions :
                    R.string.kg_password_pin_failed;
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

        mSubNameView = (TextView) findViewById(R.id.sim_name);
        mSimImageView = (ImageView) findViewById(R.id.keyguard_sim);
        mSubId = mKgUpdateMonitor.getSimPinLockSubId();
        if (mKgUpdateMonitor.getNumPhones() > 1) {
            mSubNameView.setVisibility(View.VISIBLE);
            handleSubInfoChange();
        }

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }

        mPasswordEntry.setQuickUnlockListener(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mShowDefaultMessage) {
            showDefaultMessage();
        }
        mKgUpdateMonitor.registerCallback(mUpdateCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mKgUpdateMonitor.removeCallback(mUpdateCallback);
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
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

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                Log.v(TAG, "call supplyPinReportResultUsingSubId() mSubId = " + mSubId);
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                    .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                Log.v(TAG, "supplyPinReportResultUsingSubId returned: " + result[0] +
                        " " + result[1]);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResultUsingSubId:", e);
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
                    getContext().getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getPinRemainingAttemptsDialog(int remaining) {
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

    private void closeKeyGuard() {
        if (DEBUG) Log.d(TAG, "closeKeyGuard: Verification Completed, closing Keyguard.");
        mRemainingAttempts = -1;
        mKgUpdateMonitor.reportSimUnlocked(mSubId);
        mCallback.dismiss(true);
        mShowDefaultMessage = true;
        reset();
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
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText()) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            mRemainingAttempts = attemptsRemaining;
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                closeKeyGuard();
                            } else {
                                mShowDefaultMessage = false;
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    // show message
                                    mSecurityMessageDisplay.setMessage(getPinPasswordErrorMessage(
                                            attemptsRemaining, false), true);
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getPinRemainingAttemptsDialog(attemptsRemaining).show();
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
                                resetPasswordText(true /* animate */);
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

    private void handleSubInfoChangeIfNeeded() {
        int subId = mKgUpdateMonitor.getSimPinLockSubId();
        if (subId != mSubId && SubscriptionManager.isValidSubscriptionId(subId)) {
            mSubId = subId;
            handleSubInfoChange();
            mRemainingAttempts = -1;
            mShowDefaultMessage = true;
        }
    }

    private void handleSubInfoChange() {
        SubscriptionInfo info =
            SubscriptionManager.from(mContext).getActiveSubscriptionInfo(mSubId);
        CharSequence displayName = null;

        if (info != null) {
            displayName = info.getDisplayName();
        }
        if (displayName == null) {
            displayName = mContext.getString(R.string.kg_slot_name,
                    SubscriptionManager.getSlotId(mSubId) + 1);
        }

        if (DEBUG) Log.i(TAG, "handleSubInfoChange, mSubId=" + mSubId +
                ", displayName=" + displayName);
        mSubNameView.setText(displayName);

        if (mKgUpdateMonitor.getNumPhones() > 1) {
            final int color = info != null && info.getIconTint() != 0
                    ? info.getIconTint() : Color.WHITE;
            mSimImageView.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    private void showDefaultMessage() {
        if (mRemainingAttempts >= 0) {
            mSecurityMessageDisplay.setMessage(getPinPasswordErrorMessage(
                    mRemainingAttempts, true), true);
            return;
        } else {
            mSecurityMessageDisplay.setMessage(R.string.kg_sim_pin_instructions, true);
        }
    }
}

