/*
* <!--
*    Copyright (C) 2014 The NamelessROM Project
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
* -->
*/

package com.android.systemui.nameless.onthego;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;

import com.android.internal.util.nameless.NamelessUtils;
import com.android.systemui.R;

public class OnTheGoDialog extends Dialog {

    protected final Context mContext;
    protected final Handler mHandler = new Handler();

    private final int mOnTheGoDialogLongTimeout;
    private final int mOnTheGoDialogShortTimeout;

    private final Runnable mDismissDialogRunnable = new Runnable() {
        public void run() {
            if (OnTheGoDialog.this.isShowing()) {
                OnTheGoDialog.this.dismiss();
            }
        }
    };

    public OnTheGoDialog(Context ctx) {
        super(ctx);
        mContext = ctx;
        final Resources r = mContext.getResources();
        mOnTheGoDialogLongTimeout =
                r.getInteger(R.integer.quick_settings_onthego_dialog_long_timeout);
        mOnTheGoDialogShortTimeout =
                r.getInteger(R.integer.quick_settings_onthego_dialog_short_timeout);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        window.getAttributes().privateFlags |=
                WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.quick_settings_onthego_dialog);
        setCanceledOnTouchOutside(true);

        final ContentResolver resolver = mContext.getContentResolver();

        final SeekBar mSlider = (SeekBar) findViewById(R.id.alpha_slider);
        final float value = Settings.Nameless.getFloatForUser(resolver,
                Settings.Nameless.ON_THE_GO_ALPHA, 0.5f, UserHandle.USER_CURRENT);
        final int progress = ((int) (value * 100));
        mSlider.setProgress(progress);
        mSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                sendAlphaBroadcast(String.valueOf(i + 10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                removeAllOnTheGoDialogCallbacks();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                dismissOnTheGoDialog(mOnTheGoDialogShortTimeout);
            }
        });

        if (!NamelessUtils.hasFrontCamera(getContext())) {
            findViewById(R.id.onthego_category_1).setVisibility(View.GONE);
        } else {
            final Switch mServiceToggle = (Switch) findViewById(R.id.onthego_service_toggle);
            final boolean restartService = Settings.Nameless.getBooleanForUser(resolver,
                    Settings.Nameless.ON_THE_GO_SERVICE_RESTART, false, UserHandle.USER_CURRENT);
            mServiceToggle.setChecked(restartService);
            mServiceToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    Settings.Nameless.putBooleanForUser(resolver,
                            Settings.Nameless.ON_THE_GO_SERVICE_RESTART, b,
                            UserHandle.USER_CURRENT);
                    dismissOnTheGoDialog(mOnTheGoDialogShortTimeout);
                }
            });

            final Switch mCamSwitch = (Switch) findViewById(R.id.onthego_camera_toggle);
            final boolean useFrontCam = (Settings.Nameless.getIntForUser(resolver,
                    Settings.Nameless.ON_THE_GO_CAMERA, 0, UserHandle.USER_CURRENT) == 1);
            mCamSwitch.setChecked(useFrontCam);
            mCamSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    Settings.Nameless.putIntForUser(resolver,
                            Settings.Nameless.ON_THE_GO_CAMERA, (b ? 1 : 0),
                            UserHandle.USER_CURRENT);
                    sendCameraBroadcast();
                    dismissOnTheGoDialog(mOnTheGoDialogShortTimeout);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        dismissOnTheGoDialog(mOnTheGoDialogLongTimeout);
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeAllOnTheGoDialogCallbacks();
    }

    private void dismissOnTheGoDialog(int timeout) {
        removeAllOnTheGoDialogCallbacks();
        mHandler.postDelayed(mDismissDialogRunnable, timeout);
    }

    private void removeAllOnTheGoDialogCallbacks() {
        mHandler.removeCallbacks(mDismissDialogRunnable);
    }

    private void sendAlphaBroadcast(String i) {
        final float value = (Float.parseFloat(i) / 100);
        final Intent alphaBroadcast = new Intent();
        alphaBroadcast.setAction(OnTheGoService.ACTION_TOGGLE_ALPHA);
        alphaBroadcast.putExtra(OnTheGoService.EXTRA_ALPHA, value);
        mContext.sendBroadcast(alphaBroadcast);
    }

    private void sendCameraBroadcast() {
        final Intent cameraBroadcast = new Intent();
        cameraBroadcast.setAction(OnTheGoService.ACTION_TOGGLE_CAMERA);
        mContext.sendBroadcast(cameraBroadcast);
    }

}
