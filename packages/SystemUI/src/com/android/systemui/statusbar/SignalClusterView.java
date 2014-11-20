/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCluster,
        SecurityController.SecurityControllerCallback {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final int STATUS_BAR_STYLE_ANDROID_DEFAULT = 0;
    private final int STATUS_BAR_STYLE_CDMA_1X_COMBINED = 1;
    private final int STATUS_BAR_STYLE_DEFAULT_DATA = 2;
    private final int STATUS_BAR_STYLE_DATA_VOICE = 3;

    private int mStyle = 0;
    private int[] mShowTwoBars;

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mVpnVisible = false;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileTypeId = 0, mMobileActivityId = 0;
    private int mNoSimIconId = 0;

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
    private boolean mRoaming;
    private boolean mIsMobileTypeIconWide;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mVpn, mWifi, mMobile, mMobileType, mAirplane, mNoSimSlot;
    ImageView mWifiActivity, mMobileActivity;
    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;

    private int mWideTypeIconStartPadding;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mStyle = context.getResources().getInteger(R.integer.status_bar_style);
        mShowTwoBars = context.getResources().getIntArray(
                R.array.config_showVoiceAndDataForSub);
    }

    public void setNetworkController(NetworkControllerImpl nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    public void setSecurityController(SecurityController sc) {
        if (DEBUG) Log.d(TAG, "SecurityController=" + sc);
        mSC = sc;
        mSC.addCallback(this);
        mVpnVisible = mSC.isVpnEnabled();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWideTypeIconStartPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
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
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileActivity = null;
        mMobileType     = null;
        mNoSimSlot      = null;
        mMobileCdmaGroup    = null;
        mMobileCdma3g       = null;
        mMobileCdma1x       = null;
        mMobileCdma1xOnly   = null;
        mDataGroup          = null;
        mDataActivity       = null;
        mAirplane           = null;

        mMobileDataVoiceGroup = null;
        mMobileSignalData     = null;
        mMobileSignalVoice    = null;

        super.onDetachedFromWindow();
    }

    // From SecurityController.
    @Override
    public void onStateChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                mVpnVisible = mSC.isVpnEnabled();
                apply();
            }
        });
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
    public void setMobileDataIndicators(boolean visible, int strengthIcon,
            int activityIcon, int typeIcon, String contentDescription,
            String typeContentDescription, boolean roaming,
            boolean isTypeIconWide, int noSimIcon) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileActivityId = activityIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;
        mRoaming = roaming;
        mIsMobileTypeIconWide = isTypeIconWide;
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
            if (!isRoaming() && showDataAndVoice()) {
                mMobileCdmaVisible = true;
                mMobileCdma1xOnlyVisible = false;
                mMobileStrengthId = 0;

                mMobileCdma3gId = strengthIcon;
                mMobileCdma1xId = getCdma2gId(mMobileCdma3gId);
                if (isCdmaDataOnlyMode()) {
                    mMobileCdmaVisible = false;
                    mMobileCdma1xOnlyVisible = false;
                    mMobileStrengthId = convertMobileStrengthIcon(strengthIcon);
                }
            } else if (show1xOnly() || isRoaming()) {
                mMobileCdmaVisible = false;
                mMobileCdma1xOnlyVisible = true;
                mMobileStrengthId = 0;

                if (mDataVisible && getCdmaRoamId(strengthIcon) != 0) {
                    mMobileCdma1xOnlyId = getCdmaRoamId(strengthIcon);
                } else {
                    mMobileCdma1xOnlyId = strengthIcon;
                }
            } else {
                mMobileCdmaVisible = false;
                mMobileCdma1xOnlyVisible = false;

                mMobileStrengthId = convertMobileStrengthIcon(strengthIcon);
            }
        } else if (mStyle == STATUS_BAR_STYLE_DATA_VOICE) {
            if (showBothDataAndVoice() && getMobileVoiceId() != 0) {
                mMobileStrengthId = 0;
                mMobileDataVoiceVisible = true;
                mMobileSignalDataId = strengthIcon;
                mMobileSignalVoiceId = getMobileVoiceId();
            } else {
                mMobileStrengthId = convertMobileStrengthIcon(mMobileStrengthId);
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

        if (mWifiActivity != null) {
            mWifiActivity.setImageDrawable(null);
        }

        if (mMobile != null) {
            mMobile.setImageDrawable(null);
        }

        if (mMobileActivity != null) {
            mMobileActivity.setImageDrawable(null);
        }

        if (mMobileType != null) {
            mMobileType.setImageDrawable(null);
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        mVpn.setVisibility(mVpnVisible ? View.VISIBLE : View.GONE);
        if (DEBUG) Log.d(TAG, String.format("vpn: %s", mVpnVisible ? "VISIBLE" : "GONE"));
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

        if (mWifiVisible && ((mIsAirplaneMode) || (mNoSimIconId != 0))) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (mRoaming && mMobileVisible && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        mMobile.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0, 0, 0, 0);

        if (DEBUG) Log.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId,  mMobileActivityId, mMobileTypeId));

        if (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT) {
            mMobileType.setVisibility(
                    ((mRoaming || mMobileTypeId != 0) && !mWifiVisible) ?
                    View.VISIBLE : View.GONE);
        } else {
            mMobileType.setVisibility(View.GONE);
        }

        if (mStyle != STATUS_BAR_STYLE_ANDROID_DEFAULT) {
            if (mNoSimIconId != 0) {
                mNoSimSlot.setVisibility(View.VISIBLE);
                mMobile.setVisibility(View.GONE);
            } else {
                mNoSimSlot.setVisibility(View.GONE);
                mMobile.setVisibility(View.VISIBLE);
            }
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
        if (mStyle != STATUS_BAR_STYLE_DATA_VOICE) {
            return false;
        }

        if (mShowTwoBars[0] == 0) {
            return false;
        }

        if (mNC == null) {
            return false;
        }

        boolean ret = false;
        int dataType = mNC.getDataNetworkType();
        int voiceType = mNC.getVoiceNetworkType();
        if ((dataType == TelephonyManager.NETWORK_TYPE_TD_SCDMA
                || dataType == TelephonyManager.NETWORK_TYPE_LTE)
            && voiceType == TelephonyManager.NETWORK_TYPE_GSM) {
            ret = true;
        }
        return ret;
    }

    private boolean isCdmaDataOnlyMode() {
        if (mStyle != STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            return false;
        }
        if (mNC == null) {
            return false;
        }
        int dataType = mNC.getDataNetworkType();
        int voiceType = mNC.getVoiceNetworkType();
        return ((dataType == TelephonyManager.NETWORK_TYPE_LTE)
                || (dataType == TelephonyManager.NETWORK_TYPE_EVDO_0)
                || (dataType == TelephonyManager.NETWORK_TYPE_EVDO_A))
                && voiceType == TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    private boolean showDataAndVoice() {
        if (mStyle != STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            return false;
        }
        if (mNC == null) {
            return false;
        }
        int dataType = mNC.getDataNetworkType();
        int voiceType = mNC.getVoiceNetworkType();
        boolean ret = false;
        if ((dataType == TelephonyManager.NETWORK_TYPE_EVDO_0
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_0
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_A
                || dataType == TelephonyManager.NETWORK_TYPE_EVDO_B
                || dataType == TelephonyManager.NETWORK_TYPE_EHRPD
                || dataType == TelephonyManager.NETWORK_TYPE_LTE)
                && (voiceType == TelephonyManager.NETWORK_TYPE_GSM
                    || voiceType == TelephonyManager.NETWORK_TYPE_1xRTT)) {
            ret = true;
        }
        return ret;
    }

    private boolean show1xOnly() {
        if (mStyle != STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            return false;
        }
        if (mNC == null) {
            return false;
        }
        int dataType = mNC.getDataNetworkType();
        int voiceType = mNC.getVoiceNetworkType();
        boolean ret = false;
        if (dataType == TelephonyManager.NETWORK_TYPE_1xRTT
                || dataType == TelephonyManager.NETWORK_TYPE_CDMA) {
            ret = true;
        }
        return ret;
    }

    private boolean showMobileActivity() {
        return (mStyle == STATUS_BAR_STYLE_DEFAULT_DATA)
                || (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT);
    }

    private boolean isRoaming() {
        return mMobileTypeId == R.drawable.stat_sys_data_fully_connected_roam;
    }

    private int getMobileVoiceId() {
        if (mNC == null) {
            return 0;
        }
        int retValue = 0;
        int level = mNC.getGsmSignalLevel();
        switch(level){
            case SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                retValue = R.drawable.stat_sys_signal_0_gsm;
                break;
            case SignalStrength.SIGNAL_STRENGTH_POOR:
                retValue = R.drawable.stat_sys_signal_1_gsm;
                break;
            case SignalStrength.SIGNAL_STRENGTH_MODERATE:
                retValue = R.drawable.stat_sys_signal_2_gsm;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GOOD:
                retValue = R.drawable.stat_sys_signal_3_gsm;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GREAT:
                retValue = R.drawable.stat_sys_signal_4_gsm;
                break;
            default:
                break;
        }
        return retValue;
    }

    private int convertMobileStrengthIcon(int icon) {
        int returnVal = icon;
        switch(icon){
            case R.drawable.stat_sys_signal_0_3g:
                returnVal = R.drawable.stat_sys_signal_0_3g_default;
                break;
            case R.drawable.stat_sys_signal_0_4g:
                returnVal = R.drawable.stat_sys_signal_0_4g_default;
                break;
            case R.drawable.stat_sys_signal_1_3g:
                returnVal = R.drawable.stat_sys_signal_1_3g_default;
                break;
            case R.drawable.stat_sys_signal_1_4g:
                returnVal = R.drawable.stat_sys_signal_1_4g_default;
                break;
            case R.drawable.stat_sys_signal_2_3g:
                returnVal = R.drawable.stat_sys_signal_2_3g_default;
                break;
            case R.drawable.stat_sys_signal_2_4g:
                returnVal = R.drawable.stat_sys_signal_2_4g_default;
                break;
            case R.drawable.stat_sys_signal_3_3g:
                returnVal = R.drawable.stat_sys_signal_3_3g_default;
                break;
            case R.drawable.stat_sys_signal_3_4g:
                returnVal = R.drawable.stat_sys_signal_3_4g_default;
                break;
            case R.drawable.stat_sys_signal_4_3g:
                returnVal = R.drawable.stat_sys_signal_4_3g_default;
                break;
            case R.drawable.stat_sys_signal_4_4g:
                returnVal = R.drawable.stat_sys_signal_4_4g_default;
                break;
            case R.drawable.stat_sys_signal_0_3g_fully:
                returnVal = R.drawable.stat_sys_signal_0_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_0_4g_fully:
                returnVal = R.drawable.stat_sys_signal_0_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_1_3g_fully:
                returnVal = R.drawable.stat_sys_signal_1_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_1_4g_fully:
                returnVal = R.drawable.stat_sys_signal_1_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_2_3g_fully:
                returnVal = R.drawable.stat_sys_signal_2_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_2_4g_fully:
                returnVal = R.drawable.stat_sys_signal_2_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_3_3g_fully:
                returnVal = R.drawable.stat_sys_signal_3_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_3_4g_fully:
                returnVal = R.drawable.stat_sys_signal_3_4g_default_fully;
                break;
            case R.drawable.stat_sys_signal_4_3g_fully:
                returnVal = R.drawable.stat_sys_signal_4_3g_default_fully;
                break;
            case R.drawable.stat_sys_signal_4_4g_fully:
                returnVal = R.drawable.stat_sys_signal_4_4g_default_fully;
                break;
            default:
                break;
        }
        return returnVal;
    }

    private int getCdma2gId(int icon) {
        if (mNC == null) {
            return 0;
        }
        int retValue = 0;
        int level = mNC.getGsmSignalLevel();
        switch(level){
            case SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                retValue = R.drawable.stat_sys_signal_0_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_POOR:
                retValue = R.drawable.stat_sys_signal_1_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_MODERATE:
                retValue = R.drawable.stat_sys_signal_2_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GOOD:
                retValue = R.drawable.stat_sys_signal_3_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GREAT:
                retValue = R.drawable.stat_sys_signal_4_2g;
                break;
            default:
                break;
        }
        return retValue;
    }

    private int getCdmaRoamId(int icon){
        int returnVal = 0;
        switch(icon){
            case R.drawable.stat_sys_signal_0_2g_default_roam:
            case R.drawable.stat_sys_signal_0_3g_default_roam:
            case R.drawable.stat_sys_signal_0_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_0_default_roam;
                break;
            case R.drawable.stat_sys_signal_1_2g_default_roam:
            case R.drawable.stat_sys_signal_1_3g_default_roam:
            case R.drawable.stat_sys_signal_1_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_1_default_roam;
                break;
            case R.drawable.stat_sys_signal_2_2g_default_roam:
            case R.drawable.stat_sys_signal_2_3g_default_roam:
            case R.drawable.stat_sys_signal_2_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_2_default_roam;
                break;
            case R.drawable.stat_sys_signal_3_2g_default_roam:
            case R.drawable.stat_sys_signal_3_3g_default_roam:
            case R.drawable.stat_sys_signal_3_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_3_default_roam;
                break;
            case R.drawable.stat_sys_signal_4_2g_default_roam:
            case R.drawable.stat_sys_signal_4_3g_default_roam:
            case R.drawable.stat_sys_signal_4_4g_default_roam:
                returnVal = R.drawable.stat_sys_signal_4_default_roam;
                break;
            case R.drawable.stat_sys_signal_0_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_0_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_0_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_0_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_1_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_1_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_1_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_1_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_2_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_2_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_2_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_2_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_3_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_3_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_3_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_3_default_fully_roam;
                break;
            case R.drawable.stat_sys_signal_4_2g_default_fully_roam:
            case R.drawable.stat_sys_signal_4_3g_default_fully_roam:
            case R.drawable.stat_sys_signal_4_4g_default_fully_roam:
                returnVal = R.drawable.stat_sys_signal_4_default_fully_roam;
                break;
            default:
                break;
        }
        return returnVal;
    }
}
