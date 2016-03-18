/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.systemui.qs;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ScrollView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.phone.SystemUIDialog;

public class QSSettings extends ScrollView {

    private static final String RESULT_RECEIVER_EXTRA = "result_receiver";
    private static final String LOCK_CLOCK_PACKAGENAME = "com.cyanogenmod.lockclock";
    private static final String LOCK_CLOCK_PERM_CLASS = LOCK_CLOCK_PACKAGENAME
            + ".weather.PermissionRequestActivity";

    private QSTileHost mHost;

    private boolean mAdapterEditingState;
    private QSBooleanSettingRow mShowWeather;
    private ResultReceiver mResultReceiver;

    public QSSettings(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFillViewport(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        findViewById(R.id.reset_tiles).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateTileReset();
            }
        });

        mShowWeather = (QSBooleanSettingRow) findViewById(R.id.show_weather);
        mShowWeather.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    PackageManager packageManager = getContext().getPackageManager();
                    if (packageManager.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION,
                            LOCK_CLOCK_PACKAGENAME) != PackageManager.PERMISSION_GRANTED) {
                        mShowWeather.setChecked(false);
                        requestPermission();
                        mHost.collapsePanels();
                    }
                }
            }
        });
    }

    public Parcelable getResultReceiverForSending() {
        if (mResultReceiver == null) {
            mResultReceiver = new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    super.onReceiveResult(resultCode, resultData);
                    if (resultCode == Activity.RESULT_OK) {
                        mShowWeather.setChecked(true);
                    }
                    mResultReceiver = null;
                }
            };
        }
        Parcel parcel = Parcel.obtain();
        mResultReceiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    private void requestPermission() {
        Intent i = new Intent();
        i.setClassName(LOCK_CLOCK_PACKAGENAME, LOCK_CLOCK_PERM_CLASS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(RESULT_RECEIVER_EXTRA, getResultReceiverForSending());
        getContext().startActivity(i);
    }

    private void initiateTileReset() {
        final AlertDialog d = new AlertDialog.Builder(mContext)
                .setMessage(R.string.qs_tiles_reset_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(com.android.internal.R.string.reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mHost.initiateReset();
                            }
                        }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    public void setHost(QSTileHost host) {
        mHost = host;
    }

    public boolean getAdapterEditingState() {
        return mAdapterEditingState;
    }

    public void setAdapterEditingState(boolean editing) {
        this.mAdapterEditingState = editing;
    }
}
