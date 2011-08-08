/*
 * Copyright (C) 2006 The Android Open Source Project
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
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.CmSystem;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.AttributeSet;
import android.util.Slog;
import android.widget.TextView;

import com.android.internal.R;
import com.android.internal.telephony.TelephonyProperties;

/**
 * This widget display the current network status or registered PLMN, and/or
 * SPN if available.
 */
public class CarrierLabel extends TextView {
    private boolean mAttached;

    private boolean mShowSpn;
    private String mSpn;
    private boolean mShowPlmn;
    private String mPlmn;

    private int mCarrierLabelType;
    private String mCarrierLabelCustom;

    private static final int TYPE_DEFAULT = 0;

    private static final int TYPE_SPN = 1;

    private static final int TYPE_PLMN = 2;

    private static final int TYPE_CUSTOM = 3;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.CARRIER_LABEL_TYPE),
                    false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.CARRIER_LABEL_CUSTOM_STRING),
                    false, this);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            updateNetworkName(mShowSpn, mSpn, mShowPlmn, mPlmn);
        }
    }

    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        updateSettings();
        updateNetworkName(false, null, false, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Telephony.Intents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                updateNetworkName(intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_SPN),
                        intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(Telephony.Intents.EXTRA_PLMN));
            }
        }
    };

    void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mCarrierLabelType = Settings.System.getInt(resolver,
                Settings.System.CARRIER_LABEL_TYPE, TYPE_DEFAULT);
        mCarrierLabelCustom = Settings.System.getString(resolver,
                Settings.System.CARRIER_LABEL_CUSTOM_STRING);
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (false) {
            Slog.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }

        mShowSpn = showSpn;
        mSpn = spn;
        mShowPlmn = showPlmn;
        mPlmn = plmn;

        boolean haveSignal = false;
        haveSignal = (showPlmn && plmn != null) || (showSpn && spn != null);
        String realPlmn = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        int carrierLabelType = mCarrierLabelType;

        if (plmn != null && !(plmn.equals(realPlmn))) {
            carrierLabelType = TYPE_DEFAULT;
        }

        String label = "";

        switch (carrierLabelType) {
            default:
            case TYPE_DEFAULT:
                StringBuilder str = new StringBuilder();
                if (showPlmn) {
                    if (plmn != null) {
                        str.append(plmn);
                    } else {
                        str.append(mContext.getText(R.string.lockscreen_carrier_default));
                    }
                }
                if (showSpn && spn != null) {
                    if (showPlmn) {
                        str.append('\n');
                    }
                    str.append(spn);
	        }

                label = str.toString();
                break;

            case TYPE_SPN:
                if (spn != null)
                    label = spn;
                break;

            case TYPE_PLMN:
                if (plmn != null)
                    label = plmn;
                break;

            case TYPE_CUSTOM:
                if (mCarrierLabelCustom != null)
                    label = mCarrierLabelCustom;
                break;
        }

        if (haveSignal) {
            setText(label);
        } else {
            setText(com.android.internal.R.string.lockscreen_carrier_default);
        }
    }

}
