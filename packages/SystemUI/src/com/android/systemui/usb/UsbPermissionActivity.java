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
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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

public class UsbPermissionActivity extends AlertActivity
        implements DialogInterface.OnClickListener, CheckBox.OnCheckedChangeListener {

    private static final String TAG = "UsbPermissionActivity";

    private CheckBox mAlwaysUse;
    private TextView mClearDefaultHint;
    private UsbAccessory mAccessory;
    private PendingIntent mPendingIntent;
    private String mPackageName;
    private int mUid;
    private boolean mPermissionGranted;
    private UsbDisconnectedReceiver mDisconnectedReceiver;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        mAccessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        mDisconnectedReceiver = new UsbDisconnectedReceiver(this, mAccessory);
        mPendingIntent = (PendingIntent)intent.getParcelableExtra(Intent.EXTRA_INTENT);
        mUid = intent.getIntExtra("uid", 0);
        mPackageName = intent.getStringExtra("package");

        PackageManager packageManager = getPackageManager();
        ApplicationInfo aInfo;
        try {
            aInfo = packageManager.getApplicationInfo(mPackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "unable to look up package name", e);
            finish();
            return;
        }
        String appName = aInfo.loadLabel(packageManager).toString();

        final AlertController.AlertParams ap = mAlertParams;
        ap.mIcon = aInfo.loadIcon(packageManager);
        ap.mTitle = appName;
        ap.mMessage = getString(R.string.usb_accessory_permission_prompt, appName);
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
    public void onDestroy() {
        IBinder b = ServiceManager.getService(USB_SERVICE);
        IUsbManager service = IUsbManager.Stub.asInterface(b);

        // send response via pending intent
        Intent intent = new Intent();
        try {
            if (mAccessory != null) {
                intent.putExtra(UsbManager.EXTRA_ACCESSORY, mAccessory);
                if (mPermissionGranted) {
                    service.grantAccessoryPermission(mAccessory, mUid);
                    if (mAlwaysUse.isChecked()) {
                        service.setAccessoryPackage(mAccessory, mPackageName);
                    }
                }
            }
            intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, mPermissionGranted);
            mPendingIntent.send(this, 0, intent);
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "PendingIntent was cancelled");
        } catch (RemoteException e) {
            Log.e(TAG, "IUsbService connection failed", e);
        }

        if (mDisconnectedReceiver != null) {
            unregisterReceiver(mDisconnectedReceiver);
        }
        super.onDestroy();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            mPermissionGranted = true;
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
