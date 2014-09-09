/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class UsbTetherTile extends QuickSettingsTile {
    private ConnectivityManager mCm;
    private boolean mUsbTethered = false;
    private boolean mUsbConnected = false;
    private boolean mMassStorageActive = false;

    public UsbTetherTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUsbConnected) {
                    mCm.setUsbTethering(!mUsbTethered);
                }
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED, this);
        qsc.registerAction(UsbManager.ACTION_USB_STATE, this);
        qsc.registerAction(Intent.ACTION_MEDIA_SHARED, this);
        qsc.registerAction(Intent.ACTION_MEDIA_UNSHARED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(UsbManager.ACTION_USB_STATE)) {
            mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
        } else if (action.equals(Intent.ACTION_MEDIA_SHARED)) {
            mMassStorageActive = true;
        } else if (action.equals(Intent.ACTION_MEDIA_UNSHARED)) {
            mMassStorageActive = false;
        }

        updateResources();
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(mUsbConnected ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private void updateTile() {
        updateState();

        if (mUsbConnected && !mMassStorageActive) {
            if (mUsbTethered) {
                mDrawable = R.drawable.ic_qs_usb_tether_on;
                mLabel = mContext.getString(R.string.quick_settings_usb_tether_on_label);
            } else {
                mDrawable = R.drawable.ic_qs_usb_tether_connected;
                mLabel = mContext.getString(R.string.quick_settings_usb_tether_connected_label);
            }
        } else {
            mDrawable = R.drawable.ic_qs_usb_tether_off;
            mLabel = mContext.getString(R.string.quick_settings_usb_tether_off_label);
        }
    }

    private void updateState() {
        String[] usbRegexes = mCm.getTetherableUsbRegexs();
        String[] tethered = mCm.getTetheredIfaces();

        mUsbTethered = false;
        for (String s : tethered) {
            for (String regex : usbRegexes) {
                if (s.matches(regex)) {
                    mUsbTethered = true;
                    break;
                }
            }
        }
    }
}
