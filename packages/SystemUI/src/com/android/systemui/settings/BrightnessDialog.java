/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.settings;

import com.android.systemui.R;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import java.lang.Runnable;

/** A dialog that provides controls for adjusting the screen brightness. */
public class BrightnessDialog extends Dialog implements
        BrightnessController.BrightnessStateChangeCallback {

    private static final String TAG = "BrightnessDialog";
    private static final boolean DEBUG = false;

    protected Handler mHandler = new Handler();

    private BrightnessController mBrightnessController;
    private ToggleSlider mSlider;
    private View mSetupButtonPlaceholder;
    private View mSetupButtonDivider;
    private ImageView mSetupButton;
    private final int mBrightnessDialogLongTimeout;
    private final int mBrightnessDialogShortTimeout;

    private final Runnable mDismissDialogRunnable = new Runnable() {
        public void run() {
            if (BrightnessDialog.this.isShowing()) {
                BrightnessDialog.this.dismiss();
            }
        };
    };


    public BrightnessDialog(Context ctx) {
        super(ctx);
        Resources r = ctx.getResources();
        mBrightnessDialogLongTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_long_timeout);
        mBrightnessDialogShortTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_short_timeout);
    }


    /**
     * Create the brightness dialog and any resources that are used for the
     * entire lifetime of the dialog.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        window.getAttributes().privateFlags |=
                WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.quick_settings_brightness_dialog);
        setCanceledOnTouchOutside(true);

        mSlider = (ToggleSlider) findViewById(R.id.brightness_slider);
        mSetupButtonPlaceholder = findViewById(R.id.brightness_setup_button_placeholder);
        mSetupButtonDivider = findViewById(R.id.brightness_setup_button_divider);
        mSetupButton = (ImageView) findViewById(R.id.brightness_setup_button);
        mSetupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        "com.android.settings.cyanogenmod.AutoBrightnessSetup");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                getContext().startActivity(intent);
                dismissBrightnessDialog(0);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrightnessController = new BrightnessController(getContext(),
                (ImageView) findViewById(R.id.brightness_icon), mSlider);
        dismissBrightnessDialog(mBrightnessDialogLongTimeout);
        mBrightnessController.addStateChangedCallback(this);
        updateSetupButtonVisibility();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBrightnessController.unregisterCallbacks();
        removeAllBrightnessDialogCallbacks();
    }

    public void onBrightnessLevelChanged() {
        dismissBrightnessDialog(mBrightnessDialogShortTimeout);
        updateSetupButtonVisibility();
    }

    private void updateSetupButtonVisibility() {
        boolean isAuto = mSlider.isChecked();
        mSetupButtonPlaceholder.setVisibility(isAuto ? View.GONE : View.VISIBLE);
        mSetupButtonDivider.setVisibility(isAuto ? View.VISIBLE : View.GONE);
        mSetupButton.setVisibility(isAuto ? View.VISIBLE : View.GONE);
    }

    private void dismissBrightnessDialog(int timeout) {
        removeAllBrightnessDialogCallbacks();
        mHandler.postDelayed(mDismissDialogRunnable, timeout);
    }

    private void removeAllBrightnessDialogCallbacks() {
        mHandler.removeCallbacks(mDismissDialogRunnable);
    }

}
