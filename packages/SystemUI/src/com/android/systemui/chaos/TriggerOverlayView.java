/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
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

package com.android.systemui.chaos;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.android.systemui.R;

public abstract class TriggerOverlayView extends FrameLayout {
    final protected WindowManager mWM;

    protected WindowManager.LayoutParams mLayoutParams;
    protected int mTriggerWidth;
    protected int mTriggerTop;
    protected int mTriggerBottom;
    protected int mViewHeight;

    public TriggerOverlayView(Context context) {
        this(context, null);
    }

    public TriggerOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TriggerOverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mWM = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        mViewHeight = getWindowHeight();
    }

    protected int getWindowHeight() {
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        return r.bottom - r.top;
    }

    protected int enableKeyEvents() {
        return (0
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
    }

    protected int disableKeyEvents() {
        return (0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
    }

    protected void setPosition(int gravity) {
        mLayoutParams = (WindowManager.LayoutParams) getLayoutParams();
        mLayoutParams.gravity = Gravity.TOP | gravity;
        mWM.updateViewLayout(this, mLayoutParams);
        invalidate();
    }

    protected void showTriggerRegion() {
        setBackgroundResource(R.drawable.trigger_region);
    }

    protected void hideTriggerRegion() {
        setBackgroundColor(0);
    }

    protected void setTopPercentage(float value) {
        mLayoutParams = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerTop = (int)(mViewHeight * value);
        mLayoutParams.y = mTriggerTop;
        mLayoutParams.height = mTriggerBottom;
        try {
            mWM.updateViewLayout(this, mLayoutParams);
        } catch (Exception e) {
        }
    }

    protected void setBottomPercentage(float value) {
        mLayoutParams = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerBottom = (int)(mViewHeight * value);
        mLayoutParams.height = mTriggerBottom;
        try {
            mWM.updateViewLayout(this, mLayoutParams);
        } catch (Exception e) {
        }
    }

    protected void setTriggerWidth(int value) {
        mLayoutParams = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerWidth = value;
        mLayoutParams.width = mTriggerWidth;
        try {
            mWM.updateViewLayout(this, mLayoutParams);
        } catch (Exception e) {
        }
    }

    protected void expandFromTriggerRegion() {
        mLayoutParams = (WindowManager.LayoutParams) getLayoutParams();
        mLayoutParams.y = 0;
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        mViewHeight = r.bottom - r.top;
        mLayoutParams.height = mViewHeight;
        mLayoutParams.width = LayoutParams.MATCH_PARENT;
        mLayoutParams.flags = enableKeyEvents();
        mWM.updateViewLayout(this, mLayoutParams);
    }

    protected void reduceToTriggerRegion() {
        mLayoutParams = (WindowManager.LayoutParams) getLayoutParams();
        mLayoutParams.y = mTriggerTop;
        mLayoutParams.height = mTriggerBottom;
        mLayoutParams.width = mTriggerWidth;
        mLayoutParams.flags = disableKeyEvents();
        mWM.updateViewLayout(this, mLayoutParams);
    }

    protected boolean isKeyguardEnabled() {
        KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.inKeyguardRestrictedInputMode();
    }
}
