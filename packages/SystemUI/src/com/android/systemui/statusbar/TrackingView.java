/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;


public class TrackingView extends LinearLayout {
    final Display mDisplay;
    StatusBarService mService;
    boolean mTracking;
    int mStartX, mStartY;
    boolean mIsAttachedToWindow;

    public TrackingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mService.updateExpandedHeight();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_BACK:
            if (down) {
                //mService.deactivate();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mService.onTrackingViewAttached();
        mIsAttachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mService.onTrackingViewDetached();
        mIsAttachedToWindow = false;
    }
}
