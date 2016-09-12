/*
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

import android.annotation.DrawableRes;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.util.ArrayList;
import java.util.List;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCallbackExtended,
        SecurityController.SecurityControllerCallback, Tunable {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String SLOT_AIRPLANE = "airplane";
    private static final String SLOT_MOBILE = "mobile";
    private static final String SLOT_WIFI = "wifi";
    private static final String SLOT_ETHERNET = "ethernet";

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mNoSimsVisible = false;
    private boolean mVpnVisible = false;
    private boolean mEthernetVisible = false;
    private int mEthernetIconId = 0;
    private int mLastEthernetIconId = -1;
    private boolean mWifiVisible = false;
    private boolean mImsOverWifi = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private int mLastWifiStrengthId = -1, mLastWifiActivityId = -1;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mLastAirplaneIconId = -1;
    private String mAirplaneContentDescription;
    private String mWifiDescription;
    private String mEthernetDescription;
    private ArrayList<PhoneState> mPhoneStates = new ArrayList<PhoneState>();
    private int mIconTint = Color.WHITE;
    private float mDarkIntensity;
    private final Rect mTintArea = new Rect();
    private int mNoSimsIcon;

    ViewGroup mEthernetGroup, mWifiGroup;
    View mNoSimsCombo;
    ImageView mVpn, mEthernet, mWifi, mAirplane,
                mNoSims, mEthernetDark, mWifiDark, mNoSimsDark, mImsOverWifiImageView;
    ImageView mWifiActivity;
    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;
    LinearLayout mMobileSignalGroup;

    private final int mMobileSignalGroupEndPadding;
    private final int mMobileDataIconStartPadding;
    private final int mWideTypeIconStartPadding;
    private final int mSecondaryTelephonyPadding;
    private final int mEndPadding;
    private final int mEndPaddingNothingVisible;
    private final float mIconScaleFactor;

    private boolean mBlockAirplane;
    private boolean mBlockMobile;
    private boolean mBlockWifi;
    private boolean mBlockEthernet;
    private TelephonyManager mTelephonyManager;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = getResources();
        mMobileSignalGroupEndPadding =
                res.getDimensionPixelSize(R.dimen.mobile_signal_group_end_padding);
        mMobileDataIconStartPadding =
                res.getDimensionPixelSize(R.dimen.mobile_data_icon_start_padding);
        mWideTypeIconStartPadding = res.getDimensionPixelSize(R.dimen.wide_type_icon_start_padding);
        mSecondaryTelephonyPadding = res.getDimensionPixelSize(R.dimen.secondary_telephony_padding);
        mEndPadding = res.getDimensionPixelSize(R.dimen.signal_cluster_battery_padding);
        mEndPaddingNothingVisible = res.getDimensionPixelSize(
                R.dimen.no_signal_cluster_battery_padding);

        TypedValue typedValue = new TypedValue();
        res.getValue(R.dimen.status_bar_icon_scale_factor, typedValue, true);
        mIconScaleFactor = typedValue.getFloat();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!StatusBarIconController.ICON_BLACKLIST.equals(key)) {
            return;
        }
        ArraySet<String> blockList = StatusBarIconController.getIconBlacklist(newValue);
        boolean blockAirplane = blockList.contains(SLOT_AIRPLANE);
        boolean blockMobile = blockList.contains(SLOT_MOBILE);
        boolean blockWifi = blockList.contains(SLOT_WIFI);
        boolean blockEthernet = blockList.contains(SLOT_ETHERNET);

        if (blockAirplane != mBlockAirplane || blockMobile != mBlockMobile
                || blockEthernet != mBlockEthernet || blockWifi != mBlockWifi) {
            mBlockAirplane = blockAirplane;
            mBlockMobile = blockMobile;
            mBlockEthernet = blockEthernet;
            mBlockWifi = blockWifi;
            // Re-register to get new callbacks.
            mNC.removeSignalCallback(this);
            mNC.addSignalCallback(this);
        }
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

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mEthernetGroup  = (ViewGroup) findViewById(R.id.ethernet_combo);
        mEthernet       = (ImageView) findViewById(R.id.ethernet);
        mEthernetDark   = (ImageView) findViewById(R.id.ethernet_dark);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiDark       = (ImageView) findViewById(R.id.wifi_signal_dark);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mNoSims         = (ImageView) findViewById(R.id.no_sims);
        mNoSimsDark     = (ImageView) findViewById(R.id.no_sims_dark);
        mImsOverWifiImageView    = (ImageView) findViewById(R.id.ims_over_wifi);
        mNoSimsCombo    =             findViewById(R.id.no_sims_combo);
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);
        mMobileSignalGroup = (LinearLayout) findViewById(R.id.mobile_signal_group);

        maybeScaleVpnAndNoSimsIcons();
    }

    /**
     * Extracts the icon off of the VPN and no sims views and maybe scale them by
     * {@link #mIconScaleFactor}. Note that the other icons are not scaled here because they are
     * dynamic. As such, they need to be scaled each time the icon changes in {@link #apply()}.
     */
    private void maybeScaleVpnAndNoSimsIcons() {
        if (mIconScaleFactor == 1.f) {
            return;
        }

        mVpn.setImageDrawable(new ScalingDrawableWrapper(mVpn.getDrawable(), mIconScaleFactor));

        mNoSims.setImageDrawable(
                new ScalingDrawableWrapper(mNoSims.getDrawable(), mIconScaleFactor));
        mNoSimsDark.setImageDrawable(
                new ScalingDrawableWrapper(mNoSimsDark.getDrawable(), mIconScaleFactor));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        for (PhoneState state : mPhoneStates) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }

        int endPadding = mMobileSignalGroup.getChildCount() > 0 ? mMobileSignalGroupEndPadding : 0;
        mMobileSignalGroup.setPaddingRelative(0, 0, endPadding, 0);

        TunerService.get(mContext).addTunable(this, StatusBarIconController.ICON_BLACKLIST);

        apply();
        applyIconTint();
        mNC.addSignalCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mEthernetGroup  = null;
        mEthernet       = null;
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mAirplane       = null;
        mImsOverWifiImageView    = null;
        mMobileSignalGroup.removeAllViews();
        TunerService.get(mContext).removeTunable(this);
        mSC.removeCallback(this);
        mNC.removeSignalCallback(this);

        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // Re-run all checks against the tint area for all icons
        applyIconTint();
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
    public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
            boolean activityIn, boolean activityOut, String description) {
        mWifiVisible = statusIcon.visible && !mBlockWifi;
        mWifiStrengthId = statusIcon.icon;
        mWifiActivityId = getWifiActivityId(activityIn, activityOut);
        mWifiDescription = statusIcon.contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, int dataActivityId,
            int mobileActivityId, int stackedDataId, int stackedVoiceId,
            String typeContentDescription, String description, boolean isWide, int subId) {
        PhoneState state = getState(subId);
        if (state == null) {
            return;
        }
        state.mMobileVisible = statusIcon.visible && !mBlockMobile;
        state.mMobileStrengthId = statusIcon.icon;
        state.mMobileTypeId = statusType;
        state.mMobileDescription = statusIcon.contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = statusType != 0 && isWide;
        state.mDataActivityId = dataActivityId;
        state.mMobileActivityId = mobileActivityId;
        state.mStackedDataId = stackedDataId;
        state.mStackedVoiceId = stackedVoiceId;

        apply();
    }

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, int dataActivityId,
            int mobileActivityId, int stackedDataId, int stackedVoiceId,
            String typeContentDescription, String description, boolean isWide, int subId,
            int dataNetworkTypeInRoamingId, int embmsIconId, int imsIconId, boolean isImsOverWifi) {
        PhoneState state = getState(subId);
        if (state == null) {
            return;
        }
        state.mDataNetworkTypeInRoamingId = dataNetworkTypeInRoamingId;
        state.mMobileEmbmsId = embmsIconId;
        state.mMobileImsId = imsIconId;
        mImsOverWifi = isImsOverWifi;
        this.setMobileDataIndicators(statusIcon, qsIcon, statusType, qsType, activityIn,
                activityOut, dataActivityId, mobileActivityId, stackedDataId,
                stackedVoiceId, typeContentDescription, description, isWide, subId);
    }

    @Override
    public void setEthernetIndicators(IconState state) {
        mEthernetVisible = state.visible && !mBlockEthernet;
        mEthernetIconId = state.icon;
        mEthernetDescription = state.contentDescription;

        apply();
    }

    private boolean getImsOverWifiStatus(int subId) {
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null
                && (tm.isVoWifiCallingAvailableForSubscriber(subId)
                || tm.isVideoTelephonyWifiCallingAvailableForSubscriber(subId))) {
            return true;
        }
        return false;
    }

    @Override
    public void setNoSims(boolean show) {
        mNoSimsVisible = show && !mBlockMobile;
        apply();
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        if (hasCorrectSubs(subs)) {
            return;
        }
        // Clear out all old subIds.
        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.maybeStopAnimatableDrawable(state.mMobile);
            }
            if (state.mMobileDark != null) {
                state.maybeStopAnimatableDrawable(state.mMobileDark);
            }
        }
        mPhoneStates.clear();
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        final int n = subs.size();
        boolean imsOverWiFi = false;
        for (int i = 0; i < n; i++) {
            inflatePhoneState(subs.get(i).getSubscriptionId());
            imsOverWiFi |= getImsOverWifiStatus(subs.get(i).getSubscriptionId());
        }
        mImsOverWifi = imsOverWiFi;
        if (isAttachedToWindow()) {
            applyIconTint();
        }
    }

    private boolean hasCorrectSubs(List<SubscriptionInfo> subs) {
        final int N = subs.size();
        if (N != mPhoneStates.size()) {
            return false;
        }
        for (int i = 0; i < N; i++) {
            if (mPhoneStates.get(i).mSubId != subs.get(i).getSubscriptionId()) {
                return false;
            }
        }
        return true;
    }

    private PhoneState getState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        Log.e(TAG, "Unexpected subscription " + subId);
        return null;
    }


    private int getWifiActivityId(boolean activityIn, boolean activityOut) {
        if (!getContext().getResources().getBoolean(R.bool.config_showWifiActivity)) {
            return 0;
        }
        int activityId = 0;
        if (activityIn && activityOut) {
            activityId = R.drawable.stat_sys_wifi_inout;
        } else if (activityIn) {
            activityId = R.drawable.stat_sys_wifi_in;
        } else if (activityOut) {
            activityId = R.drawable.stat_sys_wifi_out;
        }
        return activityId;
    }

    private int getNoSimIcon() {
        int resId = 0;
        final String[] noSimArray;
        Resources res = getContext().getResources();

        if (!res.getBoolean(R.bool.config_read_icons_from_xml)) return resId;

        try {
            noSimArray = res.getStringArray(R.array.multi_no_sim);
        } catch (android.content.res.Resources.NotFoundException e) {
            return resId;
        }

        if (noSimArray == null) return resId;

        String resName = noSimArray[0];
        resId = res.getIdentifier(resName, null, getContext().getPackageName());
        if (DEBUG) Log.d(TAG, "getNoSimIcon resId = " + resId + " resName = " + resName);
        return resId;
    }

    private PhoneState inflatePhoneState(int subId) {
        PhoneState state = new PhoneState(subId, mContext);
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        mPhoneStates.add(state);
        return state;
    }

    @Override
    public void setIsAirplaneMode(IconState icon) {
        mIsAirplaneMode = icon.visible && !mBlockAirplane;
        mAirplaneIconId = icon.icon;
        mAirplaneContentDescription = icon.contentDescription;

        apply();
    }

    @Override
    public void setMobileDataEnabled(boolean enabled) {
        // Don't care.
    }

    @Override
    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mEthernetVisible && mEthernetGroup != null &&
                mEthernetGroup.getContentDescription() != null)
            event.getText().add(mEthernetGroup.getContentDescription());
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        for (PhoneState state : mPhoneStates) {
            state.populateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEventInternal(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mEthernet != null) {
            mEthernet.setImageDrawable(null);
            mEthernetDark.setImageDrawable(null);
            mLastEthernetIconId = -1;
        }

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
            mWifiDark.setImageDrawable(null);
            mLastWifiStrengthId = -1;
        }

        if (mWifiActivity != null) {
            mWifiActivity.setImageDrawable(null);
            mLastWifiActivityId = -1;
        }

        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.maybeStopAnimatableDrawable(state.mMobile);
                state.mMobile.setImageDrawable(null);
                state.mLastMobileStrengthId = -1;
            }
            if (state.mMobileDark != null) {
                state.maybeStopAnimatableDrawable(state.mMobileDark);
                state.mMobileDark.setImageDrawable(null);
                state.mLastMobileStrengthId = -1;
            }
            if (state.mMobileType != null) {
                state.mMobileType.setImageDrawable(null);
                state.mLastMobileTypeId = -1;
            }
        }

        if (mAirplane != null) {
            mAirplane.setImageDrawable(null);
            mLastAirplaneIconId = -1;
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

        if (mEthernetVisible) {
            if (mLastEthernetIconId != mEthernetIconId) {
                setIconForView(mEthernet, mEthernetIconId);
                setIconForView(mEthernetDark, mEthernetIconId);
                mLastEthernetIconId = mEthernetIconId;
            }
            mEthernetGroup.setContentDescription(mEthernetDescription);
            mEthernetGroup.setVisibility(View.VISIBLE);
        } else {
            mEthernetGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("ethernet: %s",
                    (mEthernetVisible ? "VISIBLE" : "GONE")));

        if (mWifiVisible) {
            if (mWifiStrengthId != mLastWifiStrengthId) {
                setIconForView(mWifi, mWifiStrengthId);
                setIconForView(mWifiDark, mWifiStrengthId);
                mLastWifiStrengthId = mWifiStrengthId;
            }
            if (mWifiActivityId != mLastWifiActivityId) {
                mWifiActivity.setImageResource(mWifiActivityId);
                mLastWifiActivityId = mWifiActivityId;
            }
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        boolean anyMobileVisible = false;
        int firstMobileTypeId = 0;
        for (PhoneState state : mPhoneStates) {
            if (state.apply(anyMobileVisible)) {
                if (!anyMobileVisible) {
                    firstMobileTypeId = state.mMobileTypeId;
                    anyMobileVisible = true;
                }
            }
        }

        if (mIsAirplaneMode) {
            if (mLastAirplaneIconId != mAirplaneIconId) {
                setIconForView(mAirplane, mAirplaneIconId);
                mLastAirplaneIconId = mAirplaneIconId;
            }
            mAirplane.setContentDescription(mAirplaneContentDescription);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mImsOverWifi){
            mImsOverWifiImageView.setVisibility(View.VISIBLE);
        } else {
            mImsOverWifiImageView.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode && mWifiVisible) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (((anyMobileVisible && firstMobileTypeId != 0) || mNoSimsVisible) && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        if (mNoSimsVisible && mNoSims != null && mNoSimsDark != null) {
            if (mNoSimsIcon == 0) mNoSimsIcon = getNoSimIcon();
            if (mNoSimsIcon != 0) {
                mNoSims.setImageResource(mNoSimsIcon);
                mNoSimsDark.setImageResource(mNoSimsIcon);
            }
        }
        mNoSimsCombo.setVisibility(mNoSimsVisible ? View.VISIBLE : View.GONE);

        boolean anythingVisible = mNoSimsVisible || mWifiVisible || mIsAirplaneMode
                || anyMobileVisible || mVpnVisible || mEthernetVisible;
        setPaddingRelative(0, 0, anythingVisible ? mEndPadding : mEndPaddingNothingVisible, 0);
    }

    /**
     * Sets the given drawable id on the view. This method will also scale the icon by
     * {@link #mIconScaleFactor} if appropriate.
     */
    private void setIconForView(ImageView imageView, @DrawableRes int iconId) {
        // Using the imageView's context to retrieve the Drawable so that theme is preserved.
        Drawable icon = imageView.getContext().getDrawable(iconId);

        if (mIconScaleFactor == 1.f) {
            imageView.setImageDrawable(icon);
        } else {
            imageView.setImageDrawable(new ScalingDrawableWrapper(icon, mIconScaleFactor));
        }
    }

    public void setIconTint(int tint, float darkIntensity, Rect tintArea) {
        boolean changed = tint != mIconTint || darkIntensity != mDarkIntensity
                || !mTintArea.equals(tintArea);
        mIconTint = tint;
        mDarkIntensity = darkIntensity;
        mTintArea.set(tintArea);
        if (changed && isAttachedToWindow()) {
            applyIconTint();
        }
    }

    private void applyIconTint() {
        setTint(mVpn, StatusBarIconController.getTint(mTintArea, mVpn, mIconTint));
        setTint(mAirplane, StatusBarIconController.getTint(mTintArea, mAirplane, mIconTint));
        applyDarkIntensity(
                StatusBarIconController.getDarkIntensity(mTintArea, mNoSims, mDarkIntensity),
                mNoSims, mNoSimsDark);
        applyDarkIntensity(
                StatusBarIconController.getDarkIntensity(mTintArea, mWifi, mDarkIntensity),
                mWifi, mWifiDark);
        applyDarkIntensity(
                StatusBarIconController.getDarkIntensity(mTintArea, mEthernet, mDarkIntensity),
                mEthernet, mEthernetDark);
        for (int i = 0; i < mPhoneStates.size(); i++) {
            mPhoneStates.get(i).setIconTint(mIconTint, mDarkIntensity, mTintArea);
        }
    }

    private void applyDarkIntensity(float darkIntensity, View lightIcon, View darkIcon) {
        lightIcon.setAlpha(1 - darkIntensity);
        darkIcon.setAlpha(darkIntensity);
    }

    private void setTint(ImageView v, int tint) {
        v.setImageTintList(ColorStateList.valueOf(tint));
    }

    private class PhoneState {
        private final int mSubId;
        private boolean mMobileVisible = false;
        private int mMobileStrengthId = 0, mMobileTypeId = 0;
        private int mLastMobileStrengthId = -1;
        private int mLastMobileTypeId = -1;
        private boolean mIsMobileTypeIconWide;
        private String mMobileDescription, mMobileTypeDescription;

        private ViewGroup mMobileGroup;
        private ImageView mMobile, mMobileDark, mMobileType, mRoaming, mDataNetworkTypeInRoaming,
                mMobileEmbms, mMobileIms;

        private int mDataActivityId = 0, mMobileActivityId = 0, mDataNetworkTypeInRoamingId =0,
                mMobileEmbmsId = 0, mMobileImsId = 0;

        private int mStackedDataId = 0, mStackedVoiceId = 0;
        private ImageView mDataActivity, mMobileActivity, mStackedData, mStackedVoice;
        private ViewGroup mMobileSingleGroup, mMobileStackedGroup;

        public PhoneState(int subId, Context context) {
            ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                    .inflate(R.layout.mobile_signal_group, null);
            setViews(root);
            mSubId = subId;
        }

        public void setViews(ViewGroup root) {
            mMobileGroup    = root;
            mMobile         = (ImageView) root.findViewById(R.id.mobile_signal);
            mMobileDark     = (ImageView) root.findViewById(R.id.mobile_signal_dark);
            mMobileType     = (ImageView) root.findViewById(R.id.mobile_type);
            mMobileActivity = (ImageView) root.findViewById(R.id.mobile_inout);
            mMobileIms      = (ImageView) root.findViewById(R.id.volte_vowifi);

            mDataNetworkTypeInRoaming = (ImageView) root
                    .findViewById(R.id.dataNetwork_type_in_roaming);
            mMobileEmbms    = (ImageView) root.findViewById(R.id.embms);
            mDataActivity   = (ImageView) root.findViewById(R.id.data_inout);
            mStackedData    = (ImageView) root.findViewById(R.id.mobile_signal_data);
            mStackedVoice   = (ImageView) root.findViewById(R.id.mobile_signal_voice);

            mMobileSingleGroup = (ViewGroup) root.findViewById(R.id.mobile_signal_single);
            mMobileStackedGroup = (ViewGroup) root.findViewById(R.id.mobile_signal_stacked);
            mRoaming        = (ImageView) root.findViewById(R.id.mobile_roaming);
        }

        public boolean apply(boolean isSecondaryIcon) {
            if (mMobileVisible && !mIsAirplaneMode) {
                if (mLastMobileStrengthId != mMobileStrengthId) {
                    updateAnimatableIcon(mMobile, mMobileStrengthId);
                    updateAnimatableIcon(mMobileDark, mMobileStrengthId);
                    mLastMobileStrengthId = mMobileStrengthId;
                }

                if (mLastMobileTypeId != mMobileTypeId) {
                    mMobileType.setImageResource(mMobileTypeId);
                    mLastMobileTypeId = mMobileTypeId;
                }

                mDataActivity.setImageResource(mDataActivityId);
                Drawable dataActivityDrawable = mDataActivity.getDrawable();
                if (dataActivityDrawable instanceof Animatable) {
                    Animatable ad = (Animatable) dataActivityDrawable;
                    if (!ad.isRunning()) {
                        ad.start();
                    }
                }

                mMobileActivity.setImageResource(mMobileActivityId);
                Drawable mobileActivityDrawable = mMobileActivity.getDrawable();
                if (mobileActivityDrawable instanceof Animatable) {
                    Animatable ad = (Animatable) mobileActivityDrawable;
                    if (!ad.isRunning()) {
                        ad.start();
                    }
                }

                mMobileEmbms.setImageResource(mMobileEmbmsId);

                mDataNetworkTypeInRoaming.setImageResource(mDataNetworkTypeInRoamingId);
                mMobileIms.setImageResource(mMobileImsId);

                if (mStackedDataId != 0 && mStackedVoiceId != 0) {
                    mStackedData.setImageResource(mStackedDataId);
                    mStackedVoice.setImageResource(mStackedVoiceId);
                    mMobileSingleGroup.setVisibility(View.GONE);
                    mMobileStackedGroup.setVisibility(View.VISIBLE);
                } else {
                    mStackedData.setImageResource(0);
                    mStackedVoice.setImageResource(0);
                    mMobileSingleGroup.setVisibility(View.VISIBLE);
                    mMobileStackedGroup.setVisibility(View.GONE);
                }

                if (mContext.getResources().getBoolean(R.bool.show_roaming_and_network_icons)
                        &&(mTelephonyManager != null
                        && mTelephonyManager.isNetworkRoaming(mSubId))) {
                    mRoaming.setImageDrawable(getContext().getResources().getDrawable(
                            R.drawable.stat_sys_data_fully_connected_roam));
                } else {
                    mRoaming.setImageDrawable(null);
                }
                mMobileGroup.setContentDescription(mMobileTypeDescription
                        + " " + mMobileDescription);
                mMobileGroup.setVisibility(View.VISIBLE);
            } else {
                mMobileGroup.setVisibility(View.GONE);
            }

            // When this isn't next to wifi, give it some extra padding between the signals.
            mMobileGroup.setPaddingRelative(isSecondaryIcon ? mSecondaryTelephonyPadding : 0,
                    0, 0, 0);
            mMobile.setPaddingRelative(
                    mIsMobileTypeIconWide ? mWideTypeIconStartPadding : mMobileDataIconStartPadding,
                    0, 0, 0);
            mMobileDark.setPaddingRelative(
                    mIsMobileTypeIconWide ? mWideTypeIconStartPadding : mMobileDataIconStartPadding,
                    0, 0, 0);

            if (DEBUG) Log.d(TAG, String.format("mobile: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"), mMobileStrengthId, mMobileTypeId));

            mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);
            mDataActivity.setVisibility(mDataActivityId != 0 ? View.VISIBLE : View.GONE);
            mMobileActivity.setVisibility(mMobileActivityId != 0 ? View.VISIBLE : View.GONE);
            mDataNetworkTypeInRoaming.setVisibility(mDataNetworkTypeInRoamingId != 0 ? View.VISIBLE
                    : View.GONE);
            mMobileEmbms.setVisibility(mMobileEmbmsId != 0 ? View.VISIBLE : View.GONE);
            mMobileIms.setVisibility(mMobileImsId != 0 ? View.VISIBLE : View.GONE);

            return mMobileVisible;
        }

        private void updateAnimatableIcon(ImageView view, int resId) {
            maybeStopAnimatableDrawable(view);
            setIconForView(view, resId);
            maybeStartAnimatableDrawable(view);
        }

        private void maybeStopAnimatableDrawable(ImageView view) {
            Drawable drawable = view.getDrawable();

            // Check if the icon has been scaled. If it has retrieve the actual drawable out of the
            // wrapper.
            if (drawable instanceof ScalingDrawableWrapper) {
                drawable = ((ScalingDrawableWrapper) drawable).getDrawable();
            }

            if (drawable instanceof Animatable) {
                Animatable ad = (Animatable) drawable;
                if (ad.isRunning()) {
                    ad.stop();
                }
            }
        }

        private void maybeStartAnimatableDrawable(ImageView view) {
            Drawable drawable = view.getDrawable();

            // Check if the icon has been scaled. If it has retrieve the actual drawable out of the
            // wrapper.
            if (drawable instanceof ScalingDrawableWrapper) {
                drawable = ((ScalingDrawableWrapper) drawable).getDrawable();
            }

            if (drawable instanceof Animatable) {
                Animatable ad = (Animatable) drawable;
                if (ad instanceof AnimatedVectorDrawable) {
                    ((AnimatedVectorDrawable) ad).forceAnimationOnUI();
                }
                if (!ad.isRunning()) {
                    ad.start();
                }
            }
        }

        public void populateAccessibilityEvent(AccessibilityEvent event) {
            if (mMobileVisible && mMobileGroup != null
                    && mMobileGroup.getContentDescription() != null) {
                event.getText().add(mMobileGroup.getContentDescription());
            }
        }

        public void setIconTint(int tint, float darkIntensity, Rect tintArea) {
            applyDarkIntensity(
                    StatusBarIconController.getDarkIntensity(tintArea, mMobile, darkIntensity),
                    mMobile, mMobileDark);
            setTint(mMobileType, StatusBarIconController.getTint(tintArea, mMobileType, tint));
        }
    }
}

