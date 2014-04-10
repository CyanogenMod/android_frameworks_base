/*
 * Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkController.SignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterView";

    private final int STATUS_BAR_STYLE_ANDROID_DEFAULT = 0;
    private final int STATUS_BAR_STYLE_CDMA_1X_COMBINED = 1;
    private final int STATUS_BAR_STYLE_DEFAULT_DATA = 2;
    private final int STATUS_BAR_STYLE_DATA_VOICE = 3;

    private int mStyle = 0;

    NetworkController mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private int mWifiActivityId = 0;
    private ImageView mWifiActivity;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileActivityId = 0;
    private int mMobileTypeId = 0, mNoSimIconId = 0;

    //cdma and 1x
    private boolean mMobileCdmaVisible = false;
    private boolean mMobileCdma1xOnlyVisible = false;
    private int mMobileCdma3gId = 0;
    private int mMobileCdma1xId = 0;
    private int mMobileCdma1xOnlyId = 0;
    private ViewGroup mMobileCdmaGroup;
    private ImageView mMobileCdma3g, mMobileCdma1x, mMobileCdma1xOnly;

    //data & voice
    private boolean mMobileDataVoiceVisible = false;
    private int mMobileSignalDataId = 0;
    private int mMobileSignalVoiceId = 0;
    private ViewGroup mMobileDataVoiceGroup;
    private ImageView mMobileSignalData, mMobileSignalVoice;

    //data
    private boolean mDataVisible = false;
    private int mDataActivityId = 0;
    private ViewGroup mDataGroup;
    private ImageView mDataActivity;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mWifi, mMobile, mMobileType, mAirplane, mNoSimSlot;
    ImageView mMobileActivity;
    View mSpacer;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mStyle = context.getResources().getInteger(R.integer.status_bar_style);
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity       = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
        mMobileActivity     = (ImageView) findViewById(R.id.mobile_inout);
        mNoSimSlot      = (ImageView) findViewById(R.id.no_sim);
        //cdma and 1x
        mMobileCdmaGroup    = (ViewGroup) findViewById(R.id.mobile_signal_cdma);
        mMobileCdma3g       = (ImageView) findViewById(R.id.mobile_signal_3g);
        mMobileCdma1x       = (ImageView) findViewById(R.id.mobile_signal_1x);
        mMobileCdma1xOnly   = (ImageView) findViewById(R.id.mobile_signal_1x_only);

        //data & voice
        mMobileDataVoiceGroup = (ViewGroup) findViewById(R.id.mobile_data_voice);
        mMobileSignalData     = (ImageView) findViewById(R.id.mobile_signal_data);
        mMobileSignalVoice    = (ImageView) findViewById(R.id.mobile_signal_voice);

        //data
        mDataGroup          = (ViewGroup) findViewById(R.id.data_combo);
        mDataActivity       = (ImageView) findViewById(R.id.data_inout);
        mAirplane       = (ImageView) findViewById(R.id.airplane);

        //spacer
        mSpacer             = findViewById(R.id.spacer);

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity       = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileType     = null;
        mMobileActivity     = null;
        mNoSimSlot      = null;
        mMobileCdmaGroup    = null;
        mMobileCdma3g       = null;
        mMobileCdma1x       = null;
        mMobileCdma1xOnly   = null;
        mDataGroup          = null;
        mDataActivity       = null;
        mAirplane           = null;
        mSpacer             = null;

        mMobileDataVoiceGroup = null;
        mMobileSignalData     = null;
        mMobileSignalVoice    = null;

        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiActivityId = activityIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
            int typeIcon, String contentDescription, String typeContentDescription,
            int noSimIcon) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileActivityId = activityIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;
        mNoSimIconId = noSimIcon;

        if (showMobileActivity()) {
            mDataActivityId = 0;
            mDataVisible = false;
        } else {
            mMobileActivityId = 0;
            mDataActivityId = activityIcon;
            mDataVisible = (activityIcon != 0) ? true : false;
        }

        if (mStyle == STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            if (showBoth3gAnd1x() || getMobileCdma3gId(strengthIcon) != 0) {
                mMobileCdmaVisible = true;
                mMobileCdma1xOnlyVisible = false;
                mMobileStrengthId = 0;

                mMobileCdma1xId = strengthIcon;
                mMobileCdma3gId = getMobileCdma3gId(mMobileCdma1xId);
            } else if (show1xOnly() || isRoaming()) {
                mMobileCdmaVisible = false;
                mMobileCdma1xOnlyVisible = true;
                mMobileStrengthId = 0;

                mMobileCdma1xOnlyId = strengthIcon;
            } else {
                mMobileCdmaVisible = false;
                mMobileCdma1xOnlyVisible = false;
            }
        } else if (mStyle == STATUS_BAR_STYLE_DATA_VOICE) {
            if (showBothDataAndVoice() || getMobileVoiceId(strengthIcon) != 0) {
                mMobileStrengthId = 0;
                mMobileDataVoiceVisible = true;
                mMobileSignalDataId = strengthIcon;
                mMobileSignalVoiceId = getMobileVoiceId(mMobileSignalDataId);
            } else {
                mMobileDataVoiceVisible = false;
            }
        } else {
            mMobileCdmaVisible = false;
            mMobileCdma1xOnlyVisible = false;
            mMobileDataVoiceVisible = false;
        }

        apply();
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup != null && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }

        if (mMobile != null) {
            mMobile.setImageDrawable(null);
        }

        if (mMobileType != null) {
            mMobileType.setImageDrawable(null);
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);

            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible && !mIsAirplaneMode) {
            updateMobile();
            updateCdma();
            updateData();
            updateDataVoice();
            mMobileGroup.setVisibility(View.VISIBLE);
        } else {
            mMobileGroup.setVisibility(View.GONE);
            mMobileCdmaGroup.setVisibility(View.GONE);
            mMobileCdma1xOnly.setVisibility(View.GONE);
            mDataGroup.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mMobileVisible && mWifiVisible &&
                ((mIsAirplaneMode) || (mNoSimIconId != 0))) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("mobile: %s sig=%d type=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileTypeId));

        if (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT) {
            mMobileType.setVisibility(
                    !mWifiVisible ? View.VISIBLE : View.GONE);
        } else {
            mMobileType.setVisibility(View.GONE);
        }

        if (mStyle != STATUS_BAR_STYLE_ANDROID_DEFAULT
                && mNoSimIconId != 0) {
            mNoSimSlot.setVisibility(View.VISIBLE);
            mMobile.setVisibility(View.GONE);
        }
    }

    private void updateMobile() {
        mMobile.setImageResource(mMobileStrengthId);
        mMobileType.setImageResource(mMobileTypeId);
        mMobileActivity.setImageResource(mMobileActivityId);
        mNoSimSlot.setImageResource(mNoSimIconId);
        mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
    }

    private void updateCdma() {
        if (mMobileCdmaVisible) {
            mMobileCdma3g.setImageResource(mMobileCdma3gId);
            mMobileCdma1x.setImageResource(mMobileCdma1xId);
            mMobileCdmaGroup.setVisibility(View.VISIBLE);
        } else {
            mMobileCdmaGroup.setVisibility(View.GONE);
        }

        if (mMobileCdma1xOnlyVisible) {
            mMobileCdma1xOnly.setImageResource(mMobileCdma1xOnlyId);
            mMobileCdma1xOnly.setVisibility(View.VISIBLE);
        } else {
            mMobileCdma1xOnly.setVisibility(View.GONE);
        }
    }

    private void updateData() {
        if (mDataVisible) {
            mDataActivity.setImageResource(mDataActivityId);
            mDataGroup.setVisibility(View.VISIBLE);
        } else {
            mDataGroup.setVisibility(View.GONE);
        }
    }

    private void updateDataVoice() {
        if (mMobileDataVoiceVisible) {
            mMobileSignalData.setImageResource(mMobileSignalDataId);
            mMobileSignalVoice.setImageResource(mMobileSignalVoiceId);
            mMobileDataVoiceGroup.setVisibility(View.VISIBLE);
        } else {
            mMobileDataVoiceGroup.setVisibility(View.GONE);
        }
    }

    private boolean showBothDataAndVoice() {
        return mStyle == STATUS_BAR_STYLE_DATA_VOICE
            &&((mMobileTypeId == R.drawable.stat_sys_data_connected_3g)
                || (mMobileTypeId == R.drawable.stat_sys_data_connected_4g)
                || (mMobileTypeId == R.drawable.stat_sys_data_fully_connected_3g)
                || (mMobileTypeId == R.drawable.stat_sys_data_fully_connected_4g));
    }

    private boolean showBoth3gAnd1x() {
        return mStyle == STATUS_BAR_STYLE_CDMA_1X_COMBINED
            &&((mMobileTypeId == R.drawable.stat_sys_data_connected_3g)
                ||(mMobileTypeId == R.drawable.stat_sys_data_fully_connected_3g));
    }

    private boolean show1xOnly() {
        return mStyle == STATUS_BAR_STYLE_CDMA_1X_COMBINED
            &&((mMobileTypeId == R.drawable.stat_sys_data_connected_1x)
                ||(mMobileTypeId == R.drawable.stat_sys_data_fully_connected_1x));
    }

    private boolean showMobileActivity() {
        return (mStyle == STATUS_BAR_STYLE_DEFAULT_DATA)
                || (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT);
    }

    private boolean isRoaming() {
        return mMobileTypeId == R.drawable.stat_sys_data_fully_connected_roam;
    }

    private int getMobileVoiceId(int icon) {
        int returnVal = 0;
        switch(icon){
            case R.drawable.stat_sys_signal_0_3g:
            case R.drawable.stat_sys_signal_0_4g:
                returnVal = R.drawable.stat_sys_signal_0_gsm;
                break;
            case R.drawable.stat_sys_signal_1_3g:
            case R.drawable.stat_sys_signal_1_4g:
                returnVal = R.drawable.stat_sys_signal_1_gsm;
                break;
            case R.drawable.stat_sys_signal_2_3g:
            case R.drawable.stat_sys_signal_2_4g:
                returnVal = R.drawable.stat_sys_signal_2_gsm;
                break;
            case R.drawable.stat_sys_signal_3_3g:
            case R.drawable.stat_sys_signal_3_4g:
                returnVal = R.drawable.stat_sys_signal_3_gsm;
                break;
            case R.drawable.stat_sys_signal_4_3g:
            case R.drawable.stat_sys_signal_4_4g:
                returnVal = R.drawable.stat_sys_signal_4_gsm;
                break;
            case R.drawable.stat_sys_signal_0_3g_fully:
            case R.drawable.stat_sys_signal_0_4g_fully:
                returnVal = R.drawable.stat_sys_signal_0_gsm_fully;
                break;
            case R.drawable.stat_sys_signal_1_3g_fully:
            case R.drawable.stat_sys_signal_1_4g_fully:
                returnVal = R.drawable.stat_sys_signal_1_gsm_fully;
                break;
            case R.drawable.stat_sys_signal_2_3g_fully:
            case R.drawable.stat_sys_signal_2_4g_fully:
                returnVal = R.drawable.stat_sys_signal_2_gsm_fully;
                break;
            case R.drawable.stat_sys_signal_3_3g_fully:
            case R.drawable.stat_sys_signal_3_4g_fully:
                returnVal = R.drawable.stat_sys_signal_3_gsm_fully;
                break;
            case R.drawable.stat_sys_signal_4_3g_fully:
            case R.drawable.stat_sys_signal_4_4g_fully:
                returnVal = R.drawable.stat_sys_signal_4_gsm_fully;
                break;
            default:
                break;
        }
        return returnVal;
    }

    private int getMobileCdma3gId(int icon){
        int returnVal = 0;
        switch(icon){
            case R.drawable.stat_sys_signal_0_1x:
                returnVal = R.drawable.stat_sys_signal_0_3g;
                break;
            case R.drawable.stat_sys_signal_1_1x:
                returnVal = R.drawable.stat_sys_signal_1_3g;
                break;
            case R.drawable.stat_sys_signal_2_1x:
                returnVal = R.drawable.stat_sys_signal_2_3g;
                break;
            case R.drawable.stat_sys_signal_3_1x:
                returnVal = R.drawable.stat_sys_signal_3_3g;
                break;
            case R.drawable.stat_sys_signal_4_1x:
                returnVal = R.drawable.stat_sys_signal_4_3g;
                break;
            case R.drawable.stat_sys_signal_0_1x_fully:
                returnVal = R.drawable.stat_sys_signal_0_3g_fully;
                break;
            case R.drawable.stat_sys_signal_1_1x_fully:
                returnVal = R.drawable.stat_sys_signal_1_3g_fully;
                break;
            case R.drawable.stat_sys_signal_2_1x_fully:
                returnVal = R.drawable.stat_sys_signal_2_3g_fully;
                break;
            case R.drawable.stat_sys_signal_3_1x_fully:
                returnVal = R.drawable.stat_sys_signal_3_3g_fully;
                break;
            case R.drawable.stat_sys_signal_4_1x_fully:
                returnVal = R.drawable.stat_sys_signal_4_3g_fully;
                break;
            default:
                break;
        }
        return returnVal;
    }
}

