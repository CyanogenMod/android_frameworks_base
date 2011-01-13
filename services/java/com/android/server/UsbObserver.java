/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.hardware.Usb;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.UEventObserver;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * <p>UsbObserver monitors for changes to USB state.
 */
class UsbObserver extends UEventObserver {
    private static final String TAG = UsbObserver.class.getSimpleName();
    private static final boolean LOG = false;

    private static final String USB_CONFIGURATION_MATCH = "DEVPATH=/devices/virtual/switch/usb_configuration";
    private static final String USB_FUNCTIONS_MATCH = "DEVPATH=/devices/virtual/usb_composite/";
    private static final String USB_CONFIGURATION_PATH = "/sys/class/switch/usb_configuration/state";
    private static final String USB_COMPOSITE_CLASS_PATH = "/sys/class/usb_composite";
    private static final String USB_CONFIGURATION_MATCH_LEGACY = "DEVPATH=/devices/virtual/switch/usb_mass_storage";

    private static final int MSG_UPDATE = 0;

    private int mUsbConfig = 0;
    private int mPreviousUsbConfig = 0;

    // lists of enabled and disabled USB functions
    private final ArrayList<String> mEnabledFunctions = new ArrayList<String>();
    private final ArrayList<String> mDisabledFunctions = new ArrayList<String>();

    private boolean mSystemReady;

    private final Context mContext;

    private PowerManagerService mPowerManager;

    public UsbObserver(Context context) {
        mContext = context;
        init();  // set initial status

        startObserving(USB_CONFIGURATION_MATCH);
        startObserving(USB_FUNCTIONS_MATCH);
        startObserving(USB_CONFIGURATION_MATCH_LEGACY);
    }

    @Override
    public void onUEvent(UEventObserver.UEvent event) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Slog.v(TAG, "USB UEVENT: " + event.toString());
        }

        synchronized (this) {
            String switchState = event.get("SWITCH_STATE");
            if (switchState != null) {
                try {
                    int newConfig = Integer.parseInt(switchState);
                    if (newConfig != mUsbConfig) {
                        mPreviousUsbConfig = mUsbConfig;
                        mUsbConfig = newConfig;
                        // trigger an Intent broadcast
                        if (mSystemReady) {
                            update();
                        }
                    }
                } catch (NumberFormatException e) {
                    Slog.e(TAG, "Could not parse switch state from event " + event);
                    int newConfig = 0;
                    if (switchState.equals("offline")) {
                        newConfig = 0;
                    } else if (switchState.equals("online")) {
                        newConfig = 1;
                    }
                    if (newConfig != mUsbConfig) {
                        mPreviousUsbConfig = mUsbConfig;
                        mUsbConfig = newConfig;
                        // trigger an Intent broadcast
                        if (mSystemReady) {
                            update();
                        }
                    }
                }
            } else {
                String function = event.get("FUNCTION");
                String enabledStr = event.get("ENABLED");
                if (function != null && enabledStr != null) {
                    // Note: we do not broadcast a change when a function is enabled or disabled.
                    // We just record the state change for the next broadcast.
                    boolean enabled = "1".equals(enabledStr);
                    if (enabled) {
                        if (!mEnabledFunctions.contains(function)) {
                            mEnabledFunctions.add(function);
                        }
                        mDisabledFunctions.remove(function);
                    } else {
                        if (!mDisabledFunctions.contains(function)) {
                            mDisabledFunctions.add(function);
                        }
                        mEnabledFunctions.remove(function);
                    }
                }
            }
        }
    }
    private final void init() {
        char[] buffer = new char[1024];

        try {
            FileReader file = new FileReader(USB_CONFIGURATION_PATH);
            int len = file.read(buffer, 0, 1024);
            mPreviousUsbConfig = mUsbConfig = Integer.valueOf((new String(buffer, 0, len)).trim());

        } catch (FileNotFoundException e) {
            Slog.w(TAG, "This kernel does not have USB configuration switch support");
        } catch (Exception e) {
            Slog.e(TAG, "" , e);
        }

        try {
            File[] files = new File(USB_COMPOSITE_CLASS_PATH).listFiles();
            if(files != null) {
                for (int i = 0; i < files.length; i++) {
                    File file = new File(files[i], "enable");
                    FileReader reader = new FileReader(file);
                    int len = reader.read(buffer, 0, 1024);
                    int value = Integer.valueOf((new String(buffer, 0, len)).trim());
                    String functionName = files[i].getName();
                    if (value == 1) {
                        mEnabledFunctions.add(functionName);
                    } else {
                        mDisabledFunctions.add(functionName);
                    }
                }
            } else {
                Slog.w(TAG, "This kernel has not created USB composite class support");
            }
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "This kernel does not have USB composite class support");
        } catch (Exception e) {
            Slog.e(TAG, "" , e);
        }
    }

    void systemReady() {
        synchronized (this) {
            update();
            mSystemReady = true;
        }
    }

    private final void update() {
        mHandler.sendEmptyMessage(MSG_UPDATE);
    }

    private final Handler mHandler = new Handler() {
        private void addEnabledFunctions(Intent intent) {
            // include state of all USB functions in our extras
            for (int i = 0; i < mEnabledFunctions.size(); i++) {
                intent.putExtra(mEnabledFunctions.get(i), Usb.USB_FUNCTION_ENABLED);
            }
            for (int i = 0; i < mDisabledFunctions.size(); i++) {
                intent.putExtra(mDisabledFunctions.get(i), Usb.USB_FUNCTION_DISABLED);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    synchronized (this) {
                        final ContentResolver cr = mContext.getContentResolver();

                        if (Settings.Secure.getInt(cr,
                                Settings.Secure.DEVICE_PROVISIONED, 0) == 0) {
                            Slog.i(TAG, "Device not provisioned, skipping USB broadcast");
                            return;
                        }
                        // Send an Intent containing connected/disconnected state
                        // and the enabled/disabled state of all USB functions
                        Intent intent;
                        boolean usbConnected = (mUsbConfig != 0);
                        if (usbConnected) {
                            intent = new Intent(Usb.ACTION_USB_CONNECTED);
                            addEnabledFunctions(intent);
                        } else {
                            intent = new Intent(Usb.ACTION_USB_DISCONNECTED);
                        }
                        mContext.sendBroadcast(intent);

                        // send a sticky broadcast for clients interested in both connect and disconnect
                        intent = new Intent(Usb.ACTION_USB_STATE);
                        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                        intent.putExtra(Usb.USB_CONNECTED, usbConnected);
                        addEnabledFunctions(intent);
                        mContext.sendStickyBroadcast(intent);
                    }
                    break;
            }
        }
    };
}
