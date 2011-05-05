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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import com.android.systemui.R;

/**
 * If the attached USB accessory has a URL associated with it, and that URL is valid,
 * show this dialog to the user to allow them to optionally visit that URL for more
 * information or software downloads.
 * Otherwise (no valid URL) this activity does nothing at all, finishing immediately.
 */
public class UsbAccessoryUriActivity extends AlertActivity
        implements DialogInterface.OnClickListener {

    private static final String TAG = "UsbAccessoryUriActivity";

    private UsbAccessory mAccessory;
    private Uri mUri;

    @Override
    public void onCreate(Bundle icicle) {
       super.onCreate(icicle);

       Intent intent = getIntent();
        mAccessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        String uriString = intent.getStringExtra("uri");
        mUri = (uriString == null ? null : Uri.parse(uriString));

        // sanity check before displaying dialog
        if (mUri == null) {
            Log.e(TAG, "could not parse Uri " + uriString);
            finish();
            return;
        }
        String scheme = mUri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            Log.e(TAG, "Uri not http or https: " + mUri);
            finish();
            return;
        }

        final AlertController.AlertParams ap = mAlertParams;
        ap.mTitle = mAccessory.getDescription();
        if (ap.mTitle == null || ap.mTitle.length() == 0) {
            ap.mTitle = getString(R.string.title_usb_accessory);
        }
        ap.mMessage = getString(R.string.usb_accessory_uri_prompt, mUri);
        ap.mPositiveButtonText = getString(R.string.label_view);
        ap.mNegativeButtonText = getString(android.R.string.cancel);
        ap.mPositiveButtonListener = this;
        ap.mNegativeButtonListener = this;

        setupAlert();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            // launch the browser
            Intent intent = new Intent(Intent.ACTION_VIEW, mUri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity failed for " + mUri);
            }
        }
        finish();
    }
}
