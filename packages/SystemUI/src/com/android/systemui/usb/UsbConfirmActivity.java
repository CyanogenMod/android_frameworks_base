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

package com.android.systemui.usb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.IUsbManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import com.android.systemui.R;

public class UsbConfirmActivity extends AlertActivity
        implements DialogInterface.OnClickListener, CheckBox.OnCheckedChangeListener {

    private static final String TAG = "UsbConfirmActivity";

    private CheckBox mAlwaysUse;
    private TextView mClearDefaultHint;
    private UsbAccessory mAccessory;
    private ResolveInfo mResolveInfo;
    private boolean mPermissionGranted;
    private UsbDisconnectedReceiver mDisconnectedReceiver;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        mAccessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        mDisconnectedReceiver = new UsbDisconnectedReceiver(this, mAccessory);
        mResolveInfo = (ResolveInfo)intent.getParcelableExtra("rinfo");

        PackageManager packageManager = getPackageManager();
        String appName = mResolveInfo.loadLabel(packageManager).toString();

        final AlertController.AlertParams ap = mAlertParams;
        ap.mIcon = mResolveInfo.loadIcon(packageManager);
        ap.mTitle = appName;
        ap.mMessage = getString(R.string.usb_accessory_confirm_prompt, appName);
        ap.mPositiveButtonText = getString(android.R.string.ok);
        ap.mNegativeButtonText = getString(android.R.string.cancel);
        ap.mPositiveButtonListener = this;
        ap.mNegativeButtonListener = this;

        // add "always use" checkbox
        LayoutInflater inflater = (LayoutInflater)getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ap.mView = inflater.inflate(com.android.internal.R.layout.always_use_checkbox, null);
        mAlwaysUse = (CheckBox)ap.mView.findViewById(com.android.internal.R.id.alwaysUse);
        mAlwaysUse.setText(R.string.always_use_accessory);
        mAlwaysUse.setOnCheckedChangeListener(this);
        mClearDefaultHint = (TextView)ap.mView.findViewById(
                                                    com.android.internal.R.id.clearDefaultHint);
        mClearDefaultHint.setVisibility(View.GONE);

        setupAlert();

    }

    @Override
    protected void onDestroy() {
        if (mDisconnectedReceiver != null) {
            unregisterReceiver(mDisconnectedReceiver);
        }
        super.onDestroy();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            try {
                IBinder b = ServiceManager.getService(USB_SERVICE);
                IUsbManager service = IUsbManager.Stub.asInterface(b);
                int uid = mResolveInfo.activityInfo.applicationInfo.uid;
                boolean alwaysUse = mAlwaysUse.isChecked();
                Intent intent = new Intent(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
                intent.putExtra(UsbManager.EXTRA_ACCESSORY, mAccessory);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setComponent(
                    new ComponentName(mResolveInfo.activityInfo.packageName,
                            mResolveInfo.activityInfo.name));

                // grant permission for the accessory
                service.grantAccessoryPermission(mAccessory, uid);
                // set or clear default setting
                if (alwaysUse) {
                    service.setAccessoryPackage(mAccessory,
                            mResolveInfo.activityInfo.packageName);
                } else {
                    service.setAccessoryPackage(mAccessory, null);
                }
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Unable to start activity", e);
            }
        }
        finish();
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mClearDefaultHint == null) return;

        if(isChecked) {
            mClearDefaultHint.setVisibility(View.VISIBLE);
        } else {
            mClearDefaultHint.setVisibility(View.GONE);
        }
    }
}
