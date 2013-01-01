/*
 * Copyright (C) 2007 Google Inc.
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

package com.android.internal.app;

import com.android.internal.os.storage.ExternalStorageFormatter;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

/**
 * This activity is shown to the user to confirm formatting of external media.
 * It uses the alert dialog style. It will be launched from a notification, or from settings
 */
public class ExternalMediaFormatActivity extends AlertActivity implements DialogInterface.OnClickListener {

    private static final int POSITIVE_BUTTON = AlertDialog.BUTTON_POSITIVE;
    
    public static final String FORMAT_PATH = "format_path";

    private StorageManager mStorageManager;
    private StorageVolume mStorageVolume = null;

    /** Used to detect when the media state changes, in case we need to call finish() */
    private BroadcastReceiver mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("ExternalMediaFormatActivity", "got action " + action);

            if (action == Intent.ACTION_MEDIA_REMOVED ||
                action == Intent.ACTION_MEDIA_CHECKING ||
                action == Intent.ACTION_MEDIA_MOUNTED ||
                action == Intent.ACTION_MEDIA_SHARED) {
                finish();
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        }

        if (savedInstanceState == null) {
            String path = getIntent().getStringExtra(FORMAT_PATH);
            StorageVolume[] volumes = mStorageManager.getVolumeList();

            // I don't know how else to work out which mount point needs to be
            // formatted... but that's what this does
            for (int i = 0; i < volumes.length && mStorageVolume == null; i++) {
                if (path.equals(volumes[i].getPath())) {
                    mStorageVolume = volumes[i];
                }
            }
        }

        Log.d("ExternalMediaFormatActivity", "The storage volume to be formatted is : " + mStorageVolume.getPath());

        Log.d("ExternalMediaFormatActivity", "onCreate!");
        // Set up the "dialog"
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = com.android.internal.R.drawable.stat_sys_warning;
        p.mTitle = getString(com.android.internal.R.string.extmedia_format_title);
        // TODO: This needs a proper message to tell the user which device will
        // be formatted
        // e.g., "Make sure you don't need anything on /mnt/usbdisk0", or
        // something
        p.mMessage = getString(com.android.internal.R.string.extmedia_format_message)
                + "\nEverything at this path will be deleted: " + mStorageVolume.getPath();
        p.mPositiveButtonText = getString(com.android.internal.R.string.extmedia_format_button_format);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(com.android.internal.R.string.cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_CHECKING);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        registerReceiver(mStorageReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        unregisterReceiver(mStorageReceiver);
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(DialogInterface dialog, int which) {

        if (which == POSITIVE_BUTTON) {
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_ONLY);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            // I think this StorageVolume is supposed to come from the calling
            // intent... which it doesn't
            // See earlier hack in onCreate
            intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, mStorageVolume);
            startService(intent);
        }

        // No matter what, finish the activity
        finish();
    }
}