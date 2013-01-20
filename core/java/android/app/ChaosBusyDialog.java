/*
 * Copyright (C) 2013 The ChameleonOS Project
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

package android.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.R;

/**
 * This is a custom dialog to be displayed when the OS is doing
 * work, such as during boot when apps are being optimized.
 *
 * {@hide}
 */
public class ChaosBusyDialog extends Dialog {
    private TextView mMessage;

    public ChaosBusyDialog(Context context) {
        this(context, android.R.style.Theme_Holo_Dialog);
    }

    public ChaosBusyDialog(Context context, int i) {
        super(context, i);
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.busy_dlg, null);
        mMessage = (TextView)v.findViewById(R.id.busy_dlg_title);
        ImageView img = (ImageView)v.findViewById(R.id.busy_dlg_animation);
        AnimationDrawable frameAnimation = (AnimationDrawable)img.getDrawable();
        frameAnimation.setCallback(img);
        frameAnimation.setVisible(true, true);
        frameAnimation.start();
        this.setContentView(v);
    }

    public void setMessage(final CharSequence msg) {
        mMessage.setText(msg);
    }

    // This dialog will consume all events coming in to
    // it, to avoide it trying to do things too early in boot.
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        return true;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent keyEvent) {
        return true;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent motionEvent) {
        return true;
    }
}

