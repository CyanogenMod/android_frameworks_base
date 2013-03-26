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

import com.android.internal.telephony.msim.ITelephonyMSim;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.MSimTelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;

/**
 * Displays a PIN pad for unlocking.
 */
public class MSimKeyguardSimPinView extends KeyguardSimPinView {
    private final static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static String TAG = "MSimKeyguardSimPinView";
    private static int sCancelledCount = 0;
    private int mSubscription = -1;

    public MSimKeyguardSimPinView(Context context) {
        this(context, null);
    }

    public MSimKeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void showCancelButton() {
        final View cancel = findViewById(R.id.key_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    closeKeyGuard(false);
                }
            });
        }
    }

    /*
    * CloseKeyGuard: Method used to Close the keyguard.
    * @param bAuthenticated:
    *               true:  user entered correct PIN and pressed OK.
    *               false: user pressed cancel.
    *
    * Description: used to close keyguard when user presses cancel or
    * user enters correct PIN and presses OK
    *
    * Logic:
    * 1. Calculate number of SIMs configured i.e. selected from MultiSIM settings
    * 2. Calculate number of SIMs which are PIN Locked
    * 3. sCancelledCount will tell us number of keyguards got cancelled. If user tries to cancel
    *    all Keyguard screens, do not allow him to close Keyguard as atleast one SIM has to
    *    be authenticated before allowing user to access HomeScreen.
    * 4. If present keyguard screen is last one we are about to show HomeScreen after closing this
    *    keyguard so reset the sCancelledCount.
    * 5. Report SIM unlocked to KGUpdateMonitor so that keyguard is closed right away without
    *    waiting for indication from framework.
    */
    private void closeKeyGuard(boolean bAuthenticated) {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
        int numCardsConfigured = 0;
        int numPinLocked = 0;
        IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;

        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            simState = updateMonitor.getSimState(i);
            if (simState == IccCardConstants.State.PIN_REQUIRED) {
                numPinLocked++;
            }

            //Get number of cards that are present and configured.
            //Exclude cards with DETECTED state as they are not configured and are not used.
            if (simState == IccCardConstants.State.READY ||
                simState == IccCardConstants.State.PIN_REQUIRED ||
                simState == IccCardConstants.State.PUK_REQUIRED ||
                simState == IccCardConstants.State.PERSO_LOCKED) {
                numCardsConfigured++;
            }
        }

        //If Cancel is pressed for last configured sub return without incrementing sCancelledCount
        // else increment sCancelledCount.
        if (!bAuthenticated) {
            if (MSimKeyguardSimPinView.sCancelledCount >= (numCardsConfigured - 1)) {
                return;
            } else {
                MSimKeyguardSimPinView.sCancelledCount++;
            }
        }

        //If this is last PIN lock screen, we will show HomeScreen now.
        //Hence reset the static sCancelledCount variable.
        if (numPinLocked <= 1) {
            MSimKeyguardSimPinView.sCancelledCount = 0;
        }

        //If Cancel is pressed get current locked sub,
        //In case user presses OK we anyways have locked sub, no need to get again.
        if (!bAuthenticated) mSubscription = updateMonitor.getPinLockedSubscription();

        //Before closing the keyguard, report back that the sim is unlocked
        //so it knows right away.
        if (mSubscription >= 0) updateMonitor.reportSimUnlocked(mSubscription);
        mCallback.dismiss(true);
    }

    public void resetState() {
        String  displayMessage = "";
        try {
            mSubscription = KeyguardUpdateMonitor.getInstance(mContext)
                    .getPinLockedSubscription();
            int attemptsRemaining = ITelephonyMSim.Stub.asInterface(ServiceManager
                    .checkService("phone_msim")).getIccPin1RetryCount(mSubscription);

            if (attemptsRemaining >= 0) {
                displayMessage = getContext().getString(R.string.keyguard_password_wrong_pin_code)
                        + getContext().getString(R.string.pinpuk_attempts)
                        + attemptsRemaining + ". ";
            }
        } catch (RemoteException ex) {
            displayMessage = getContext().getString(R.string.keyguard_password_pin_failed);
        }
        displayMessage = getSecurityMessageDisplay(R.string.kg_sim_pin_instructions)
                + displayMessage;
        mSecurityMessageDisplay.setMessage(displayMessage, true);
        mPasswordEntry.setEnabled(true);
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class MSimCheckSimPin extends Thread {
        private final String mPin;

        protected MSimCheckSimPin(String pin, int sub) {
            mPin = pin;
            mSubscription = sub;
        }

        abstract void onSimCheckResponse(boolean success);

        @Override
        public void run() {
            try {
                if (DEBUG) Log.d(TAG, "MSimCheckSimPin:run(), mPin = " + mPin
                        + " mSubscription = " + mSubscription);
                final boolean result = ITelephonyMSim.Stub.asInterface(ServiceManager
                        .checkService("phone_msim")).supplyPin(mPin, mSubscription);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(false);
                    }
                });
            }
        }
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();
        if (DEBUG) Log.d(TAG, "verifyPasswordAndUnlock(): entry = " + entry);

        if (entry.length() < 4) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(
                    getSecurityMessageDisplay(R.string.kg_invalid_sim_pin_hint), true);
            mPasswordEntry.setText("");
            mCallback.userActivity(0);
            return;
        }

        getSimUnlockProgressDialog().show();

        if (!mSimCheckInProgress) {
            mSimCheckInProgress = true; // there should be only one

            if (DEBUG) Log.d(TAG, "startCheckSimPin(), Multi SIM enabled");
            new MSimCheckSimPin(mPasswordEntry.getText().toString(),
                    KeyguardUpdateMonitor.getInstance(mContext).getPinLockedSubscription()) {
                void onSimCheckResponse(final boolean success) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                // before closing the keyguard, report back that the sim is unlocked
                                // so it knows right away.
                                closeKeyGuard(success);
                            } else {
                                mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay
                                        (R.string.kg_password_wrong_pin_code), true);
                                mPasswordEntry.setText("");
                            }
                            mCallback.userActivity(0);
                            mSimCheckInProgress = false;
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
                KeyguardUpdateMonitor.getInstance(mContext).getPinLockedSubscription()+1,
                getContext().getResources().getText(resId));
    }
}

