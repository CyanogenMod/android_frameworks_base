/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 *
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UEventObserver;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * <p>RegulatoryObserver monitors for country code change.
 */
class RegulatoryObserver extends UEventObserver {
    private static final String TAG = RegulatoryObserver.class.getSimpleName();

    private static final String REGULATORY_UEVENT_MATCH = "MODALIAS=platform:regulatory";
    private static final String UEVENT_FILE = "/sys/devices/platform/regulatory.0/uevent";

    private String mCountryKeyword = "COUNTRY=";
    private String mCountryCode;
    private static final int MSG_COUNTRY_CODE = 0;

    private final Context mContext;

    public RegulatoryObserver(Context context) {
        mContext = context;
        init();
        startObserving(REGULATORY_UEVENT_MATCH);
    }

    @Override
    public void onUEvent(UEventObserver.UEvent event) {
        Slog.v(TAG, "uevent:\n" + event.toString());
        synchronized (this) {
            try {
                mCountryCode = event.get("COUNTRY");
                Slog.v(TAG, "Regulatory Country Code:" + mCountryCode);
                run_crda();
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Could not parse country code from event " + event);
            }
        }
    }

    private final void init() {
        try {
            Slog.v(TAG, "RegulatoryObserver init.");
            // Read the regulatory uevent file to check if any pending
            // uevent before we start observing
            BufferedReader uevent_buf = new BufferedReader(new FileReader(UEVENT_FILE));
            String line;
            while (((line = uevent_buf.readLine()) != null)
                 && (line.length() != 0)) {
                String[] event_string = line.split("=");
                String key = event_string[0];
                String value = event_string[1];
                if (key.equals("COUNTRY")) {
                    // If it has COUNTRY code, it's a pending request before
                    // RegulatoryObserver started. Very likely it's from
                    // CFG80211 which is built in to kernel.
                    mCountryCode = value;
                    run_crda();
                }
            }
            if (uevent_buf != null) {
                uevent_buf.close();
            }
        } catch (Exception e) {
            Slog.e(TAG, "This kernel may not have CRDA support." , e);
        }
    }

    private final void run_crda() {
        try {
            if (mCountryCode != null) {
                // setprop wlan.crda.country then start wifi-crda service
                SystemProperties.set("wlan.crda.country", mCountryCode);
                SystemProperties.set("ctl.start", "wifi-crda");
                Slog.v(TAG, "Start wifi-crda service to run crda.");
                Slog.v(TAG, "Country Code is " + mCountryCode);
                // Broadcast intent is allowed after boot is completed
                if ("1".equals(SystemProperties.get("sys.boot_completed"))) {
                    mHandler.sendEmptyMessage(MSG_COUNTRY_CODE);
                    Slog.v(TAG, "Send broadcast country code message.");
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to start wifi-crda service to run crda." , e);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_COUNTRY_CODE:
                    synchronized (this) {
                        Slog.i(TAG, "Broadcast intent for crda country code: " + mCountryCode);
                        Intent broadcastIntent = new Intent();
                        broadcastIntent.setAction("crda.custom.intent.action.COUNTRY_CODE");
                        mContext.sendBroadcast(broadcastIntent);
                    }
                    break;
            }
        }
    };
}
