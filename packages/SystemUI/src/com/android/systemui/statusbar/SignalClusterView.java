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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.R;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkController.SignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterView";

    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 200;

    NetworkController mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileActivityId = 0, mMobileTypeId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;
    private static final int STYLE_HIDE = 0;
    private static final int STYLE_SHOW = 1;
    private static final int STYLE_SHOW_DBM = 2;

    private boolean showingSignalText = false;
    private boolean showingWiFiText = false;
    private boolean showingAltCluster = false;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mWifi, mMobile, mWifiActivity, mMobileActivity, mMobileType, mAirplane;
    TextView mMobileText,mWiFiText;
    View mSpacer;

    Handler mHandler;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Slog.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
        mMobileText     = (TextView)  findViewById(R.id.signal_text);
        mWiFiText       = (TextView)  findViewById(R.id.wifi_signal_text);
        mSpacer         =             findViewById(R.id.spacer);
        mAirplane       = (ImageView) findViewById(R.id.airplane);

        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileActivity = null;
        mMobileType     = null;
        mMobileText     = null;
        mWiFiText       = null;
        mSpacer         = null;
        mAirplane       = null;

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
            int typeIcon, String contentDescription, String typeContentDescription) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileActivityId = activityIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

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
        if (mWifiVisible && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);
            mWifiActivity.setImageResource(mWifiActivityId);
            mWifiGroup.setContentDescription(mWifiDescription);
            if (showingWiFiText){
                mWifi.setVisibility(View.GONE);
                mWifiActivity.setVisibility(View.GONE);
                mWiFiText.setVisibility(View.VISIBLE);
            } else {
                mWifi.setVisibility(View.VISIBLE);
                mWifiActivity.setVisibility(View.VISIBLE);
                mWiFiText.setVisibility(View.GONE);
            }
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));

        if (mMobileVisible && !mIsAirplaneMode) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobile.setImageResource(mMobileStrengthId);
            mMobileActivity.setImageResource(mMobileActivityId);
            mMobileType.setImageResource(mMobileTypeId);
            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
            if (showingSignalText && !mIsAirplaneMode) {
                mMobile.setVisibility(View.GONE);
                mMobileText.setVisibility(View.VISIBLE);
            } else{
                mMobile.setVisibility(View.VISIBLE);
                mMobileText.setVisibility(View.GONE);
            }
        } else {
            mMobileGroup.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setVisibility(View.VISIBLE);
            mAirplane.setImageResource(mAirplaneIconId);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mMobileVisible && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileActivityId, mMobileTypeId));

        mMobileType.setVisibility(
                !mWifiVisible ? View.VISIBLE : View.GONE);
        if (showingAltCluster) {
            this.setVisibility((this.getId() == R.id.signal_cluster) ? View.GONE : View.VISIBLE);
        } else {
            this.setVisibility((this.getId() == R.id.signal_cluster) ? View.VISIBLE : View.GONE);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SIGNAL_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_WIFI_SIGNAL_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SIGNAL_CLUSTER_ALT), false, this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        boolean clustdefault = getResources().getBoolean(R.bool.status_bar_alt_signal_layout);
        showingSignalText = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_SIGNAL_TEXT,STYLE_HIDE) > 0);
        showingWiFiText = Settings.System.getBoolean(resolver,
                Settings.System.STATUS_BAR_WIFI_SIGNAL_TEXT, false);
        showingAltCluster = Settings.System.getBoolean(resolver,
                Settings.System.STATUS_BAR_SIGNAL_CLUSTER_ALT, clustdefault);
        apply();
    }
}

