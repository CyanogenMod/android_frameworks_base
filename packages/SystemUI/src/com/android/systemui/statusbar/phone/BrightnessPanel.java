/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import java.lang.Math;
import android.view.Gravity;
import android.app.Dialog;
import android.content.DialogInterface.OnDismissListener;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;


import com.android.systemui.R;

/**
 * Displays a dialog showing the current brightness
 *
 * @hide
 */
public class BrightnessPanel extends Handler
{
    private static final String TAG = "BrightnessPanel";
    private static boolean LOGD = false;

    private static final int TIMEOUT_DELAY = 500;

    private static final int MSG_BRIGHTNESS_CHANGED = 0;
    private static final int MSG_TIMEOUT = 1;

    protected Context mContext;

    /** Dialog containing the brightness info */
    private final Dialog mDialog;
    /** Dialog's content view */
    private final View mView;

    /** View displaying the current brightness */
    private TextView mText;

    public BrightnessPanel(final Context context) {
        mContext = context;
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = mView = inflater.inflate(R.layout.brightness_adjust, null);
        mView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                resetTimeout();
                return false;
            }
        });
        mText = (TextView) mView.findViewById(R.id.brightness_text);

        mDialog = new Dialog(context, R.style.BrightnessPanel) {
            public boolean onTouchEvent(MotionEvent event) {
                if (isShowing() && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    forceTimeout();
                    return true;
                }
                return false;
            }
        };
        mDialog.setTitle("Brightness change"); // No need to localize
        mDialog.setContentView(mView);
        mDialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {

            }
        });
        // Change some window properties
        Window window = mDialog.getWindow();
        window.setGravity(Gravity.TOP);
        LayoutParams lp = window.getAttributes();
        lp.token = null;
        lp.dimAmount = 0f;
        // Offset from the top
        lp.y = mContext.getResources().getDimensionPixelOffset(R.dimen.brightness_panel_top);
        lp.type = LayoutParams.TYPE_SYSTEM_OVERLAY;
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        window.setAttributes(lp);
        window.addFlags(LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL
                | LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

    }
    public void postBrightnessChanged(final int value, final int max) {
        if (hasMessages(MSG_BRIGHTNESS_CHANGED)) return;
        obtainMessage(MSG_BRIGHTNESS_CHANGED, value, max).sendToTarget();
    }

    /**
     * Override this if you have other work to do when the volume changes (for
     * example, vibrating, playing a sound, etc.). Make sure to call through to
     * the superclass implementation.
     */
    protected void onBrightnessChanged(final int value, final int max) {
        mText.setText(Integer.toString(Math.round(value * 100.0f / max)) + "%");
        if (!mDialog.isShowing()) {
            mDialog.show();
        }
        resetTimeout();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_BRIGHTNESS_CHANGED: {
                onBrightnessChanged(msg.arg1, msg.arg2);
                break;
            }
            case MSG_TIMEOUT: {
                if (mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                break;
            }
        }
    }

    private void resetTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessageDelayed(obtainMessage(MSG_TIMEOUT), TIMEOUT_DELAY);
    }

    private void forceTimeout() {
        removeMessages(MSG_TIMEOUT);
        sendMessage(obtainMessage(MSG_TIMEOUT));
    }

}
