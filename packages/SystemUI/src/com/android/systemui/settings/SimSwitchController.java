/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above
 *        copyright notice, this list of conditions and the following
 *        disclaimer in the documentation and/or other materials provided
 *        with the distribution.
 *      * Neither the name of The Linux Foundation nor the names of its
 *        contributors may be used to endorse or promote products derived
 *        from this software without specific prior written permission.
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

package com.android.systemui.settings;


import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.View;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import android.content.ActivityNotFoundException;
import android.os.RemoteException;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.qs.QSPanel;


import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import android.util.Log;

import com.android.systemui.R;

import org.codeaurora.internal.IExtTelephony;
import android.os.ServiceManager;

public class SimSwitchController implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "SimSwitchController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private LinearLayout mSlot1Layout, mSlot2Layout;
    private  TextView mSlot1Name, mSlot2Name;
    private ImageView mSimSlot1Icon, mSimSlot2Icon;
    private final Context mContext;
    private final View mSimSwitcherView;
    private SubscriptionManager mSubscriptionManager;
    private final int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private QSPanel mQSPanel = null;
    private List<SubscriptionInfo> mSubInfoList = null;
    private int[] mUiccProvisionStatus = new int[mPhoneCount];

    private static final int PROVISIONED = 1;
    private static final String ACTION_OUTGOING_PHONE_ACCOUNT_CHANGED =
            "codeaurora.intent.action.DEFAULT_PHONE_ACCOUNT_CHANGED";


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Intent received: " + action);
            if (ACTION_OUTGOING_PHONE_ACCOUNT_CHANGED.equals(action)) {
                int voicePrefSlot = getVoicePrefSlot();
                updateViews(voicePrefSlot);
            }
        }
    };

    public SimSwitchController(Context context, View simSwitcherView, QSPanel qsPanel) {
        mContext = context;
        mSimSwitcherView = simSwitcherView;
        mQSPanel = qsPanel;
        mSlot1Name = (TextView)mSimSwitcherView.findViewById(R.id.slot1name);
        mSlot2Name = (TextView)mSimSwitcherView.findViewById(R.id.slot2name);
        mSimSlot1Icon = (ImageView)mSimSwitcherView.findViewById(R.id.sim_switch_slot1_icon);
        mSimSlot2Icon = (ImageView)mSimSwitcherView.findViewById(R.id.sim_switch_slot2_icon);
        mSlot1Layout = (LinearLayout)mSimSwitcherView.findViewById(R.id.sim_switch_slot1_layout);
        mSlot2Layout = (LinearLayout)mSimSwitcherView.findViewById(R.id.sim_switch_slot2_layout);
        mSlot1Layout.setOnClickListener(this);
        mSlot2Layout.setOnClickListener(this);
        mSlot1Layout.setOnLongClickListener(this);
        mSlot2Layout.setOnLongClickListener(this);

        mSubscriptionManager = SubscriptionManager.from(mContext);
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);

        logd(" In constructor ");
        updateSubscriptions();

        IntentFilter intentFilter = new IntentFilter(ACTION_OUTGOING_PHONE_ACCOUNT_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    synchronized public void updateViews(int voiceSlotId) {
        logd("voice preferred slot " + voiceSlotId);
        // Is there are no valid subscription info list present (or)
        // if there is only one SIM present on device, do not display
        // voice preference selection option in quick settings panel.
        if (mSubInfoList == null || mSubInfoList.size() <= 1 || getProvisionCount() <= 1) {
            logd("There are not subscription present or only one present ");

            mSimSwitcherView.setVisibility(View.GONE);
            return;
        }
        mSimSwitcherView.setVisibility(View.VISIBLE);

        for (SubscriptionInfo sir : mSubInfoList) {
             if (sir != null) {
                 int color = sir.getIconTint();
                 switch(sir.getSimSlotIndex()) {
                  case 0:
                     mSlot1Name.setText(sir.getDisplayName().toString());
                     mSimSlot1Icon.setImageBitmap(sir.createIconBitmap(mContext));
                     GradientDrawable drawable = (GradientDrawable)mSlot1Layout.getBackground();
                     if (voiceSlotId == 0) {
                         drawable.setColor(Color.argb(180,
                                 Color.red(color), Color.green(color), Color.blue(color)));
                     } else {
                         drawable.setColor(Color.GRAY);
                     }
                  break;
                  case 1:
                     mSlot2Name.setText(sir.getDisplayName().toString());
                     mSimSlot2Icon.setImageBitmap(sir.createIconBitmap(mContext));
                     GradientDrawable drawable1 = (GradientDrawable)mSlot2Layout.getBackground();
                     if (voiceSlotId == 1) {
                         drawable1.setColor(Color.argb(180,
                                 Color.red(color), Color.green(color), Color.blue(color)));
                     } else {
                         drawable1.setColor(Color.GRAY);
                     }
                  break;
                  default:
                  break;
                 }
                 logd("Update, slotId " + sir.getSimSlotIndex()
                         + " display name " + sir.getDisplayName());
             }
        }
    }


    public void onClick(View v) {
        int viewId = v.getId();
        logd(" OnClick slotId id = " + viewId);

        switch(viewId) {
            case R.id.sim_switch_slot1_layout:
                setVoicePref(0);
            break;

            case R.id.sim_switch_slot2_layout:
                setVoicePref(1);
            break;

            default:
                Log.w(TAG, "Invalid switch case " + viewId);
            break;
        }
    }

    public boolean onLongClick(View v) {
        QSTileHost tileHost = mQSPanel.getHost();

        logd("launching SimSettings activity " + tileHost);
        if (tileHost != null) {
            // Launch SimSettings activity
            Intent intent = new Intent("com.android.settings.sim.SIM_SUB_INFO_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

            tileHost.startActivityDismissingKeyguard(intent);
        }
        return true;
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
            = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            logd("onSubscriptionsChanged:");
            updateSubscriptions();
        }
    };

    private void updateSubscriptions() {
        int voicePrefSlot = -1;
        IExtTelephony extTelephony =
                IExtTelephony.Stub.asInterface(ServiceManager.getService("extphone"));

        mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (mSubInfoList != null) {
            for (SubscriptionInfo subInfo : mSubInfoList) {
                int slotId = subInfo.getSimSlotIndex();
                if (SubscriptionManager.isValidSlotId(slotId)) {
                    try {
                        mUiccProvisionStatus[slotId] =
                                extTelephony.getCurrentUiccCardProvisioningStatus(slotId);
                    } catch (RemoteException ex) {
                        logd("Activate  sub failed  phoneId " + subInfo.getSimSlotIndex());
                    } catch (NullPointerException ex) {
                        logd("Failed to activate sub Exception: " + ex);
                    }
                }
            }
            voicePrefSlot = getVoicePrefSlot();
        }

        updateViews(voicePrefSlot);
    }

    private int getVoicePrefSlot() {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        final PhoneAccountHandle phoneAccount =
            telecomManager.getUserSelectedOutgoingPhoneAccount();
        final TelephonyManager telephonyManager = TelephonyManager.from(mContext);
        int slotId = -1;

        if (phoneAccount == null) {
            logd("Get voice pref slotId " + slotId);
            return slotId;
        }

        PhoneAccount phoneaccount = telecomManager.getPhoneAccount(phoneAccount);
        if (phoneaccount != null && (mSubInfoList != null)) {
            int subId = telephonyManager.getSubIdForPhoneAccount(phoneaccount);
            int subInfoLength = mSubInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = mSubInfoList.get(i);
                if (sir != null && sir.getSubscriptionId() == subId) {
                    slotId = sir.getSimSlotIndex();
                    break;
                }
            }
        }
        logd("Get voice pref slotId " + slotId);
        return slotId;
    }

    private void setVoicePref(int slotId) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        int subId = -1;

        logd("Setting voice pref slotId " + slotId);
        if (mSubInfoList != null) {
            int subInfoLength = mSubInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = mSubInfoList.get(i);
                if (sir != null && sir.getSimSlotIndex() == slotId) {
                    subId = sir.getSubscriptionId();
                    logd("Setting voice pref slotId " + slotId + " subId " + subId);
                    PhoneAccountHandle phoneAccountHandle =
                            subscriptionIdToPhoneAccountHandle(subId);
                    telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
                    break;
                }
            }
        }

        updateViews(slotId);
    }

    private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        final TelephonyManager telephonyManager = TelephonyManager.from(mContext);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            if (subId == telephonyManager.getSubIdForPhoneAccount(phoneAccount)) {
                return phoneAccountHandle;
            }
        }

        return null;
    }

    // Internal utility, returns true if Uicc card
    // corresponds to given slotId is provisioned.
    private boolean isSubProvisioned(int slotId) {
        boolean retVal = false;

        if (mUiccProvisionStatus[slotId] == PROVISIONED) retVal = true;
        return retVal;
    }

    private int getProvisionCount() {
        int count = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            if (isSubProvisioned(i)) {
                count++;
            }
        }
        return count;
    }

    private void logd(String msg) {
        Log.d(TAG, msg);
    }
}
