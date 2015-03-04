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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.accessibility.AccessibilityEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.telephony.PhoneConstants;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.MSimNetworkControllerImpl;

import com.android.systemui.R;


// Intimately tied to the design of res/layout/msim_signal_cluster_view.xml
public class MSimSignalClusterView
        extends LinearLayout
        implements MSimNetworkControllerImpl.MSimSignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "MSimSignalClusterView";

    private final int STATUS_BAR_STYLE_ANDROID_DEFAULT = 0;
    private final int STATUS_BAR_STYLE_CDMA_1X_COMBINED = 1;
    private final int STATUS_BAR_STYLE_DEFAULT_DATA = 2;
    private final int STATUS_BAR_STYLE_DATA_VOICE = 3;

    private int mStyle = 0;
    private int[] mShowTwoBars;

    MSimNetworkControllerImpl mMSimNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int[] mMobileStrengthId;
    private int[] mMobileActivityId;
    private int[] mMobileTypeId;
    private int[] mMobileRoamId;
    private int[] mNoSimIconId;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileTypeDescription;
    private String[] mMobileDescription;

    ViewGroup mWifiGroup;
    ViewGroup[] mMobileGroup;
    ImageView mWifi, mWifiActivity, mAirplane;
    ImageView[] mNoSimSlot;
    ImageView[] mMobile;
    ImageView[] mMobileRoam;
    ImageView[] mMobileActivity;
    ImageView[] mMobileType;

    //cdma and 1x
    private boolean mMobileCdmaVisible = false;
    private boolean mMobileCdma1xOnlyVisible = false;
    private int mMobileCdma3gId = 0;
    private int mMobileCdma1xId = 0;
    private int mMobileCdma1xOnlyId = 0;
    private ViewGroup mMobileCdmaGroup;
    private ImageView mMobileCdma3g, mMobileCdma1x, mMobileCdma1xOnly;

    //data & voice
    private boolean[] mMobileDataVoiceVisible;
    private int[] mMobileSignalDataId;
    private int[] mMobileSignalVoiceId;
    private ViewGroup[] mMobileDataVoiceGroup;
    private ImageView[] mMobileSignalData, mMobileSignalVoice;

    //data
    private boolean mDataVisible[];
    private int mDataActivityId[];
    private ViewGroup mDataGroup[];
    private ImageView mDataActivity[];

    //spacer
    private View mSpacer;

    private int[] mMobileGroupResourceId = {R.id.mobile_combo, R.id.mobile_combo_sub2,
                                          R.id.mobile_combo_sub3};
    private int[] mMobileRoamResourceId = {R.id.mobile_roaming, R.id.mobile_roaming_sub2,
                                              R.id.mobile_roaming_sub3 };
    private int[] mMobileResourceId = {R.id.mobile_signal, R.id.mobile_signal_sub2,
                                     R.id.mobile_signal_sub3};
    private int[] mMobileActResourceId = {R.id.mobile_inout, R.id.mobile_inout_sub2,
                                        R.id.mobile_inout_sub3};
    private int[] mMobileTypeResourceId = {R.id.mobile_type, R.id.mobile_type_sub2,
                                         R.id.mobile_type_sub3};
    private int[] mNoSimSlotResourceId = {R.id.no_sim, R.id.no_sim_slot2, R.id.no_sim_slot3};
    private int[] mDataGroupResourceId = {R.id.data_combo, R.id.data_combo_sub2,
                                        R.id.data_combo_sub3};
    private int[] mDataActResourceId = {R.id.data_inout, R.id.data_inout_sub2,
                                        R.id.data_inout_sub3};
    private int[] mMobileDataVoiceGroupResourceId = {R.id.mobile_data_voice,
            R.id.mobile_data_voice_sub2, R.id.mobile_data_voice_sub3};
    private int[] mMobileSignalDataResourceId = {R.id.mobile_signal_data,
            R.id.mobile_signal_data_sub2, R.id.mobile_signal_data_sub3};
    private int[] mMobileSignalVoiceResourceId = {R.id.mobile_signal_voice,
            R.id.mobile_signal_voice_sub2, R.id.mobile_signal_voice_sub3};
    private final int mNumPhones = TelephonyManager.getDefault().getPhoneCount();

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
        mMobileRoamId = new int[mNumPhones];
        mMobileActivityId = new int[mNumPhones];
        mNoSimIconId = new int[mNumPhones];
        mMobileGroup = new ViewGroup[mNumPhones];
        mNoSimSlot = new ImageView[mNumPhones];
        mMobile = new ImageView[mNumPhones];
        mMobileActivity = new ImageView[mNumPhones];
        mMobileRoam = new ImageView[mNumPhones];
        mMobileType = new ImageView[mNumPhones];
        mDataVisible = new boolean[mNumPhones];
        mDataActivityId = new int[mNumPhones];
        mDataGroup = new ViewGroup[mNumPhones];
        mDataActivity = new ImageView[mNumPhones];
        mMobileDataVoiceVisible = new boolean[mNumPhones];
        mMobileSignalDataId = new int[mNumPhones];
        mMobileSignalVoiceId = new int[mNumPhones];
        mMobileDataVoiceGroup = new ViewGroup[mNumPhones];
        mMobileSignalData = new ImageView[mNumPhones];
        mMobileSignalVoice = new ImageView[mNumPhones];
        for (int i=0; i < mNumPhones; i++) {
            mMobileStrengthId[i] = 0;
            mMobileTypeId[i] = 0;
            mMobileRoamId[i] = 0;
            mMobileActivityId[i] = 0;
            mNoSimIconId[i] = 0;

            mDataVisible[i] = false;
            mMobileDataVoiceVisible[i] = false;
            mDataActivityId[i] = 0;
            mMobileSignalDataId[i] = 0;
            mMobileSignalVoiceId[i] = 0;
        }

        mStyle = context.getResources().getInteger(R.integer.status_bar_style);
        mShowTwoBars = context.getResources().getIntArray(
                R.array.config_showVoiceAndDataForSub);
    }

    public void setNetworkController(MSimNetworkControllerImpl nc) {
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
            mMobile[i]         = (ImageView) findViewById(mMobileResourceId[i]);
            mMobileRoam[i]     = (ImageView) findViewById(mMobileRoamResourceId[i]);
            mMobileActivity[i] = (ImageView) findViewById(mMobileActResourceId[i]);
            mMobileType[i]     = (ImageView) findViewById(mMobileTypeResourceId[i]);
            mNoSimSlot[i]      = (ImageView) findViewById(mNoSimSlotResourceId[i]);

            mDataGroup[i]      = (ViewGroup) findViewById(mDataGroupResourceId[i]);
            mDataActivity[i]   = (ImageView) findViewById(mDataActResourceId[i]);

            mMobileDataVoiceGroup[i] =
                    (ViewGroup) findViewById(mMobileDataVoiceGroupResourceId[i]);
            mMobileSignalData[i] =
                    (ImageView) findViewById(mMobileSignalDataResourceId[i]);
            mMobileSignalVoice[i] =
                    (ImageView) findViewById(mMobileSignalVoiceResourceId[i]);
        }

        mMobileCdmaGroup    = (ViewGroup) findViewById(R.id.mobile_signal_cdma);
        mMobileCdma3g       = (ImageView) findViewById(R.id.mobile_signal_3g);
        mMobileCdma1x       = (ImageView) findViewById(R.id.mobile_signal_1x);
        mMobileCdma1xOnly   = (ImageView) findViewById(R.id.mobile_signal_1x_only);

        for (int i = 0; i < mNumPhones; i++) {
            apply(i);
        }
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
            mMobile[i]         = null;
            mMobileActivity[i] = null;
            mMobileType[i]     = null;
            mNoSimSlot[i]      = null;
            mDataGroup[i]      = null;
            mDataActivity[i]   = null;
            mMobileDataVoiceGroup[i] = null;
            mMobileSignalData[i] = null;
            mMobileSignalVoice[i] = null;
        }
        mMobileCdmaGroup    = null;
        mMobileCdma3g       = null;
        mMobileCdma1x       = null;
        mMobileCdma1xOnly   = null;

        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiActivityId = activityIcon;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;
        for (int i = 0; i < mNumPhones; i++) {
            apply(i);
        }
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
            int typeIcon, int roamingIcon, String contentDescription, String typeContentDescription,
            int phoneId, int noSimIcon) {
        mMobileVisible = visible;
        mMobileStrengthId[phoneId] = strengthIcon;
        mMobileTypeId[phoneId] = typeIcon;
        mMobileActivityId[phoneId] = activityIcon;
        mMobileRoamId[phoneId] = roamingIcon;
        mMobileDescription[phoneId] = contentDescription;
        mMobileTypeDescription = typeContentDescription;
        mNoSimIconId[phoneId] = noSimIcon;

        if (showMobileActivity()) {
            mDataActivityId[phoneId] = 0;
            mDataVisible[phoneId] = false;
        } else {
            mMobileActivityId[phoneId] = 0;
            mDataActivityId[phoneId] = activityIcon;
            mDataVisible[phoneId] = (activityIcon != 0) ? true : false;
        }
        if (mStyle == STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            if (phoneId == PhoneConstants.PHONE_ID1) {
                if (!isRoaming() && showDataAndVoice()) {
                    mMobileCdmaVisible = true;
                    mMobileCdma1xOnlyVisible = false;
                    mMobileStrengthId[0] = 0;

                    mMobileCdma3gId = strengthIcon;
                    mMobileCdma1xId = getCdma2gId(mMobileCdma3gId);

                    if (isCdmaDataOnlyMode(phoneId)) {
                        mMobileCdmaVisible = false;
                        mMobileCdma1xOnlyVisible = false;
                        mMobileStrengthId[phoneId] = convertMobileStrengthIcon(strengthIcon);
                    }
                } else if (show1xOnly() || isRoaming()) {
                    //when it is roaming, just show one icon, rather than two icons for CT.
                    mMobileCdmaVisible = false;
                    mMobileCdma1xOnlyVisible = true;
                    mMobileStrengthId[0] = 0;

                    if (mDataVisible[0] && getCdmaRoamId(strengthIcon) != 0) {
                        mMobileCdma1xOnlyId = getCdmaRoamId(strengthIcon);
                    } else {
                        mMobileCdma1xOnlyId = strengthIcon;
                    }
                } else {
                    mMobileCdmaVisible = false;
                    mMobileCdma1xOnlyVisible = false;

                    mMobileStrengthId[phoneId] = convertMobileStrengthIcon(strengthIcon);
                }
            } else {
                if (mDataVisible[phoneId]
                        && getCdmaRoamId(mMobileStrengthId[phoneId]) != 0) {
                    mMobileStrengthId[phoneId] =
                            getCdmaRoamId(mMobileStrengthId[phoneId]);
                }
            }
        } else if (mStyle == STATUS_BAR_STYLE_DATA_VOICE) {
            if (showBothDataAndVoice(phoneId)
                    && getMobileVoiceId(phoneId) != 0) {
                mMobileStrengthId[phoneId] = 0;
                mMobileDataVoiceVisible[phoneId] = true;
                mMobileSignalDataId[phoneId] = strengthIcon;
                mMobileSignalVoiceId[phoneId]
                        = getMobileVoiceId(phoneId);
            } else {
                mMobileStrengthId[phoneId] = convertMobileStrengthIcon(mMobileStrengthId[phoneId]);
                mMobileDataVoiceVisible[phoneId] = false;
            }
        } else {
            mMobileCdmaVisible = false;
            mMobileCdma1xOnlyVisible = false;

            mMobileDataVoiceVisible[phoneId] = false;
        }

        apply(phoneId);
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;
        for (int i = 0; i < mNumPhones; i++) {
            apply(i);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        int defaultPhoneId = getDefaultPhoneId();
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null &&
                mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup[defaultPhoneId] != null
                && mMobileGroup[defaultPhoneId]
                        .getContentDescription() != null)
            event.getText().add(mMobileGroup[defaultPhoneId].
                    getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private int getDefaultPhoneId() {
        int phoneId;
        phoneId = getPhoneId(SubscriptionManager.getDefaultSubId());
        if ( phoneId < 0 || phoneId >= mNumPhones) {
            phoneId = 0;
        }
        return phoneId;
    }


    private int getPhoneId(int subId) {
        int phoneId;
        phoneId = SubscriptionManager.getPhoneId(subId);
        return phoneId;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        int count = TelephonyManager.getDefault().getPhoneCount();

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }
        if (mWifiActivity != null) {
            mWifiActivity.setImageDrawable(null);
        }
        if (mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }
        for (int i = 0; i < count; i++) {
            if (mMobile[i] != null) {
                mMobile[i].setImageDrawable(null);
            }
            if (mMobileActivity[i] != null) {
                mMobileActivity[i].setImageDrawable(null);
            }
            if (mMobileType[i] != null) {
                mMobileType[i].setImageDrawable(null);
            }
            if (mNoSimSlot[i] != null) {
                mNoSimSlot[i].setImageDrawable(null);
            }

            apply(i);
        }
    }

    // Run after each indicator change.
    private void apply(int phoneId) {
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
            updateMobile(phoneId);
            updateCdma();
            updateData(phoneId);
            updateDataVoice(phoneId);
            mMobileGroup[phoneId].setVisibility(View.VISIBLE);
            if (SystemProperties.getBoolean("ro.config.always_show_roaming", false)) {
                mMobileRoam[phoneId].setVisibility(View.VISIBLE);
            }
        } else {
            mMobileGroup[phoneId].setVisibility(View.GONE);
            mMobileCdmaGroup.setVisibility(View.GONE);
            mMobileCdma1xOnly.setVisibility(View.GONE);
            mDataGroup[phoneId].setVisibility(View.GONE);
            if (SystemProperties.getBoolean("ro.config.always_show_roaming", false)) {
                mMobileRoam[phoneId].setVisibility(View.GONE);
            }
        }

        if (mIsAirplaneMode) {
            mAirplane.setVisibility(View.VISIBLE);
            mAirplane.setImageResource(mAirplaneIconId);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile[%d]: %s sig=%d type=%d", phoneId,
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId[phoneId], mMobileTypeId[phoneId]));

        if (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT) {
            mMobileType[phoneId].setVisibility(
                    (mMobileTypeId[phoneId] != 0) ? View.VISIBLE : View.GONE);
        } else {
            mMobileType[phoneId].setVisibility(View.GONE);
        }

        if (mStyle != STATUS_BAR_STYLE_ANDROID_DEFAULT) {
            if (mNoSimIconId[phoneId] != 0) {
                mNoSimSlot[phoneId].setVisibility(View.VISIBLE);
                mMobile[phoneId].setVisibility(View.GONE);
            } else {
                mNoSimSlot[phoneId].setVisibility(View.GONE);
                mMobile[phoneId].setVisibility(View.VISIBLE);
            }
        }

        if (phoneId != 0) {
            if (mMobileVisible && mWifiVisible && ((mIsAirplaneMode) ||
                    (mStyle != STATUS_BAR_STYLE_ANDROID_DEFAULT))) {
                mSpacer.setVisibility(View.INVISIBLE);
            } else {
                mSpacer.setVisibility(View.GONE);
            }
        }
    }

    private void updateMobile(int phoneId) {
        mMobile[phoneId].setImageResource(mMobileStrengthId[phoneId]);
        mMobileGroup[phoneId].setContentDescription(mMobileTypeDescription + " "
            + mMobileDescription[phoneId]);
        mMobileActivity[phoneId].setImageResource(mMobileActivityId[phoneId]);
        mMobileType[phoneId].setImageResource(mMobileTypeId[phoneId]);
        mNoSimSlot[phoneId].setImageResource(mNoSimIconId[phoneId]);
        mMobileRoam[phoneId].setImageResource(mMobileRoamId[phoneId]);
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

    private void updateData(int phoneId) {
        if (mDataVisible[phoneId]) {
            mDataActivity[phoneId].setImageResource(mDataActivityId[phoneId]);
            mDataGroup[phoneId].setVisibility(View.VISIBLE);
        } else {
            mDataGroup[phoneId].setVisibility(View.GONE);
        }
    }

    private void updateDataVoice(int phoneId) {
        if (mMobileDataVoiceVisible[phoneId]) {
            mMobileSignalData[phoneId].setImageResource(mMobileSignalDataId[phoneId]);
            mMobileSignalVoice[phoneId].setImageResource(mMobileSignalVoiceId[phoneId]);
            mMobileDataVoiceGroup[phoneId].setVisibility(View.VISIBLE);
        } else {
            mMobileDataVoiceGroup[phoneId].setVisibility(View.GONE);
        }
    }

    private boolean showBothDataAndVoice(int phoneId) {
        if (mStyle != STATUS_BAR_STYLE_DATA_VOICE) {
            return false;
        }

        if (mShowTwoBars[phoneId] == 0) {
            return false;
        }

        if (mMSimNC == null) {
            return false;
        }

        boolean ret = false;
        int dataType = mMSimNC.getDataNetworkType(phoneId);
        int voiceType = mMSimNC.getVoiceNetworkType(phoneId);
        if ((dataType == TelephonyManager.NETWORK_TYPE_TD_SCDMA
                || dataType == TelephonyManager.NETWORK_TYPE_LTE)
            && voiceType == TelephonyManager.NETWORK_TYPE_GSM) {
            ret = true;
        }
        return ret;
    }

    private boolean isCdmaDataOnlyMode(int phoneId) {
        if (mStyle != STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            return false;
        }
        if (mMSimNC == null) {
            return false;
        }
        int dataType = mMSimNC.getDataNetworkType(phoneId);
        int voiceType = mMSimNC.getVoiceNetworkType(phoneId);
        return ((dataType == TelephonyManager.NETWORK_TYPE_LTE)
                || (dataType == TelephonyManager.NETWORK_TYPE_EVDO_0)
                || (dataType == TelephonyManager.NETWORK_TYPE_EVDO_A))
                && voiceType == TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    private boolean showDataAndVoice() {
        if (mStyle != STATUS_BAR_STYLE_CDMA_1X_COMBINED) {
            return false;
        }
        if (mMSimNC == null) {
            return false;
        }
        int dataType = mMSimNC.getDataNetworkType(0);
        int voiceType = mMSimNC.getVoiceNetworkType(0);
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
        if (mMSimNC == null) {
            return false;
        }
        int dataType = mMSimNC.getDataNetworkType(0);
        int voiceType = mMSimNC.getVoiceNetworkType(0);
        boolean ret = false;
        if (dataType == TelephonyManager.NETWORK_TYPE_1xRTT
                || dataType == TelephonyManager.NETWORK_TYPE_CDMA) {
            ret = true;
        }
        return ret;
    }

    private boolean showMobileActivity() {
        return mStyle == STATUS_BAR_STYLE_DEFAULT_DATA
                || (mStyle == STATUS_BAR_STYLE_ANDROID_DEFAULT);
    }

    private boolean isRoaming() {
        return mMobileTypeId[0] == R.drawable.stat_sys_data_fully_connected_roam;
    }

    private int getMobileVoiceId(int phoneId) {
        if (mMSimNC == null) {
            return 0;
        }

        int retValue = 0;
        int level = mMSimNC.getGsmSignalLevel(phoneId);
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
        if (mMSimNC == null) {
            return 0;
        }
        int retValue = 0;
        int level = mMSimNC.getGsmSignalLevel(0);
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
