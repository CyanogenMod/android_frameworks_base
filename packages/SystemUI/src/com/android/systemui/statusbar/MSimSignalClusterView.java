/*
 * Copyright (c) 2012-2013 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.accessibility.AccessibilityEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.MSimConstants;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.MSimNetworkController;

import com.android.systemui.R;

// Intimately tied to the design of res/layout/msim_signal_cluster_view.xml
public class MSimSignalClusterView
        extends LinearLayout
        implements MSimNetworkController.MSimSignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "MSimSignalClusterView";

    MSimNetworkController mMSimNC;

    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_TEXT = 1;
    public static final int STYLE_HIDDEN = 2;

    private int mSignalClusterStyle = STYLE_NORMAL;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int[] mMobileStrengthId;
    private int[] mMobileActivityId;
    private int[] mMobileTypeId;
    private int[] mNoSimIconId;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileTypeDescription;
    private String[] mMobileDescription;

    ViewGroup mWifiGroup;
    ViewGroup[] mMobileGroup;
    ImageView mWifi, mWifiActivity, mAirplane;
    ImageView[] mNoSimSlot;
    TextView[] mMobileSlot;
    ImageView[] mMobile;
    ImageView[] mMobileActivity;
    ImageView[] mMobileType;
    View mSpacer;
    private int[] mMobileGroupResourceId = {R.id.mobile_combo, R.id.mobile_combo_sub2,
                                          R.id.mobile_combo_sub3};
    private int[] mMobileSlotResourceId = {R.id.mobile_slot_label, R.id.mobile_slot_label_2,
                                          R.id.mobile_slot_label_3};
    private int[] mMobileSlotString = {R.string.sim_slot, R.string.sim_slot_2,
                                          R.string.sim_slot_3};
    private int[] mMobileResourceId = {R.id.mobile_signal, R.id.mobile_signal_sub2,
                                     R.id.mobile_signal_sub3};
    private int[] mMobileActResourceId = {R.id.mobile_inout, R.id.mobile_inout_sub2,
                                        R.id.mobile_inout_sub3};
    private int[] mMobileTypeResourceId = {R.id.mobile_type, R.id.mobile_type_sub2,
                                         R.id.mobile_type_sub3};
    private int[] mNoSimSlotResourceId = {R.id.no_sim, R.id.no_sim_slot2, R.id.no_sim_slot3};
    private int mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();

    public MSimSignalClusterView(Context context) {
        this(context, null);
    }

    public MSimSignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MSimSignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mMobileStrengthId = new int[mNumPhones];
        mMobileDescription = new String[mNumPhones];
        mMobileTypeId = new int[mNumPhones];
        mMobileActivityId = new int[mNumPhones];
        mNoSimIconId = new int[mNumPhones];
        mMobileGroup = new ViewGroup[mNumPhones];
        mNoSimSlot = new ImageView[mNumPhones];
        mMobileSlot = new TextView[mNumPhones];
        mMobile = new ImageView[mNumPhones];
        mMobileActivity = new ImageView[mNumPhones];
        mMobileType = new ImageView[mNumPhones];
        for(int i=0; i < mNumPhones; i++) {
            mMobileStrengthId[i] = 0;
            mMobileTypeId[i] = 0;
            mMobileActivityId[i] = 0;
            mNoSimIconId[i] = 0;
        }
    }

    public void setNetworkController(MSimNetworkController nc) {
        if (DEBUG) Slog.d(TAG, "MSimNetworkController=" + nc);
        mMSimNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mSpacer         =             findViewById(R.id.spacer);
        mAirplane       = (ImageView) findViewById(R.id.airplane);

        for (int i = 0; i < mNumPhones; i++) {
            mMobileGroup[i]    = (ViewGroup) findViewById(mMobileGroupResourceId[i]);
            mMobileSlot[i]     = (TextView) findViewById(mMobileSlotResourceId[i]);
            mMobile[i]         = (ImageView) findViewById(mMobileResourceId[i]);
            mMobileActivity[i] = (ImageView) findViewById(mMobileActResourceId[i]);
            mMobileType[i]     = (ImageView) findViewById(mMobileTypeResourceId[i]);
            mNoSimSlot[i]      = (ImageView) findViewById(mNoSimSlotResourceId[i]);
        }
        applySubscription(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mSpacer         = null;
        mAirplane       = null;
        for (int i = 0; i < mNumPhones; i++) {
            mMobileGroup[i]    = null;
            mMobileSlot[i]     = null;
            mMobile[i]         = null;
            mMobileActivity[i] = null;
            mMobileType[i]     = null;
            mNoSimSlot[i]      = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;

        applySubscription(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon,
            int typeIcon, String contentDescription, String typeContentDescription,
            int noSimIcon, int subscription) {
        mMobileVisible = visible;
        mMobileStrengthId[subscription] = strengthIcon;
        mMobileTypeId[subscription] = typeIcon;
        mMobileDescription[subscription] = contentDescription;
        mMobileTypeDescription = typeContentDescription;
        mNoSimIconId[subscription] = noSimIcon;

        applySubscription(subscription);
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        applySubscription(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup[MSimConstants.DEFAULT_SUBSCRIPTION].
                getContentDescription() != null)
            event.getText().add(mMobileGroup[MSimConstants.DEFAULT_SUBSCRIPTION].
                    getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    // Run after each indicator change.
    private void applySubscription(int subscription) {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);
            mWifiGroup.setContentDescription(mWifiDescription);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                (mWifiVisible ? "VISIBLE" : "GONE"), mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible && !mIsAirplaneMode) {
            mMobileGroup[subscription].setVisibility(View.VISIBLE);
            mMobileSlot[subscription].setText(mMobileSlotString[subscription]);
            mMobile[subscription].setImageResource(mMobileStrengthId[subscription]);
            mMobileGroup[subscription].setContentDescription(mMobileTypeDescription + " "
                + mMobileDescription[subscription]);
            mMobileActivity[subscription].setImageResource(mMobileActivityId[subscription]);
            mMobileType[subscription].setImageResource(mMobileTypeId[subscription]);
            mMobileType[subscription].setVisibility(
                !mWifiVisible ? View.VISIBLE : View.GONE);
            mNoSimSlot[subscription].setImageResource(mNoSimIconId[subscription]);
        } else {
            mMobileGroup[subscription].setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setVisibility(View.VISIBLE);
            mAirplane.setImageResource(mAirplaneIconId);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (subscription != 0) {
            if (mMobileVisible && mWifiVisible && ((mIsAirplaneMode) ||
                    (mNoSimIconId[subscription] != 0))) {
                mSpacer.setVisibility(View.INVISIBLE);
            } else {
                mSpacer.setVisibility(View.GONE);
            }
        }
    }

    public void setStyle(int style) {
        mSignalClusterStyle = style;
    }
}

