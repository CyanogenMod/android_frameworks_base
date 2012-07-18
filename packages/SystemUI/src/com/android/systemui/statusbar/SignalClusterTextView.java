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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.statusbar.SignalClusterView.SettingsObserver;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.R;

// Intimately tied to the design of res/layout/signal_cluster_text_view.xml
public class SignalClusterTextView
    extends LinearLayout {

    private static final int SIGNAL_CLUSTER_STYLE_NORMAL   = 0;
    private static final int SIGNAL_CLUSTER_STYLE_TEXT     = 1;
    private static final int SIGNAL_CLUSTER_STYLE_HIDDEN   = 2;

    private boolean mAttached;
    private boolean mAirplaneMode;
    private int mSignalClusterStyle;
    private int mPhoneState;

    private SignalStrength signalStrength;

    ViewGroup mMobileGroup;
    TextView mMobileSignalText;

    Handler mHandler;

    int dBm = 0;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SIGNAL_TEXT), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public SignalClusterTextView(Context context) {
        this(context, null);
    }

    public SignalClusterTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mMobileGroup      = (ViewGroup) findViewById(R.id.mobile_signal_text_combo);
        mMobileSignalText = (TextView) findViewById(R.id.mobile_signal_text);

        if (!mAttached) {
            mAttached = true;
            ((TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE)).listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

            updateSettings();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAttached) {
            mAttached = false;
        }
        super.onDetachedFromWindow();
    }

    private String getSignalLevelString(int dBm) {
        if (dBm == 0) {
            return "-\u221e"; // -oo ('minus infinity')
        }
        return Integer.toString(dBm);
    }

    final void updateSignalText() {

        if (mAirplaneMode || dBm == 0) {
            mMobileGroup.setVisibility(View.GONE);
            return;
        } else if (mSignalClusterStyle == SIGNAL_CLUSTER_STYLE_TEXT) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobileSignalText.setText(getSignalLevelString(dBm));
        } else {
            mMobileGroup.setVisibility(View.GONE);
        }
    }

    /*
     * Phone listener to update signal information
     */
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength != null) {
                dBm = signalStrength.getDbm();
            } else {
                dBm = 0;
            }

            // update text if it's visible
            if (mAttached) {
                updateSettings();
            }
        }

        public void onServiceStateChanged(ServiceState serviceState) {
            mAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            updateSettings();
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mSignalClusterStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_SIGNAL_TEXT, SIGNAL_CLUSTER_STYLE_NORMAL));
        updateSignalText();
    }
}
