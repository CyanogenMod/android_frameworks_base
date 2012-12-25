/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.GestureRecorder;

public class NotificationPanelView extends PanelView {

    private static final float STATUS_BAR_SETTINGS_LEFT_PERCENTAGE = 0.8f;
    private static final float STATUS_BAR_SETTINGS_RIGHT_PERCENTAGE = 0.2f;
    private static final float STATUS_BAR_SWIPE_HORIZONTAL_MIN_PERCENTAGE = 0.2f;
    private static final float STATUS_BAR_SWIPE_VERTICAL_MAX_PERCENTAGE = 0.1f;

    private Drawable mHandleBar;
    private float mHandleBarHeight;
    private View mHandleView;
    private int mFingers;
    private PhoneStatusBar mStatusBar;
    private boolean mOkToFlip;

    private float mGestureStartX;
    private float mGestureStartY;
    private boolean mTrackingSwipe;

    public NotificationPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mStatusBar = bar;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources resources = getContext().getResources();
        mHandleBar = resources.getDrawable(R.drawable.status_bar_close);
        mHandleBarHeight = resources.getDimension(R.dimen.close_handle_height);
        mHandleView = findViewById(R.id.handle);

        setContentDescription(resources.getString(R.string.accessibility_desc_notification_shade));
    }

    @Override
    public void fling(float vel, boolean always) {
        GestureRecorder gr = ((PhoneStatusBarView) mBar).mBar.getGestureRecorder();
        if (gr != null) {
            gr.tag(
                "fling " + ((vel > 0) ? "open" : "closed"),
                "notifications,v=" + vel);
        }
        super.fling(vel, always);
    }

    // We draw the handle ourselves so that it's always glued to the bottom of the window.
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            final int pl = getPaddingLeft();
            final int pr = getPaddingRight();
            mHandleBar.setBounds(pl, 0, getWidth() - pr, (int) mHandleBarHeight);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        final int off = (int) (getHeight() - mHandleBarHeight - getPaddingBottom());
        canvas.translate(0, off);
        mHandleBar.setState(mHandleView.getDrawableState());
        mHandleBar.draw(canvas);
        canvas.translate(0, -off);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (PhoneStatusBar.SETTINGS_DRAG_SHORTCUT && mStatusBar.mHasFlipSettings) {
            boolean flip = false;
            boolean swipeFlip = false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mGestureStartX = event.getX(0);
                    mGestureStartY = event.getY(0);
                    mTrackingSwipe = isFullyExpanded();
                    mOkToFlip = getExpandedHeight() == 0;
                    if (event.getX(0) > getWidth() * (1.0f - STATUS_BAR_SETTINGS_RIGHT_PERCENTAGE) &&
                            Settings.System.getInt(getContext().getContentResolver(),
                                    Settings.System.QS_QUICK_PULLDOWN, 0) == 1) {
                        flip = true;
                    } else if (event.getX(0) < getWidth() * (1.0f - STATUS_BAR_SETTINGS_LEFT_PERCENTAGE) &&
                            Settings.System.getInt(getContext().getContentResolver(),
                                    Settings.System.QS_QUICK_PULLDOWN, 0) == 2) {
                        flip = true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    final float deltaX = Math.abs(event.getX(0) - mGestureStartX);
                    final float deltaY = Math.abs(event.getY(0) - mGestureStartY);
                    final float maxDeltaY = getHeight() * STATUS_BAR_SWIPE_VERTICAL_MAX_PERCENTAGE;
                    final float minDeltaX = getWidth() * STATUS_BAR_SWIPE_HORIZONTAL_MIN_PERCENTAGE;
                    if (mTrackingSwipe && deltaX > deltaY && deltaY < maxDeltaY && deltaX > minDeltaX) {
                        mTrackingSwipe = false;
                        swipeFlip = true;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    flip = true;
                    break;
                case MotionEvent.ACTION_UP:
                    mTrackingSwipe = false;
                    break;
            }
            if (mOkToFlip && flip) {
                float miny = event.getY(0);
                float maxy = miny;
                for (int i=1; i<event.getPointerCount(); i++) {
                    final float y = event.getY(i);
                    if (y < miny) miny = y;
                    if (y > maxy) maxy = y;
                }
                if (maxy - miny < mHandleBarHeight) {
                    if (getMeasuredHeight() < mHandleBarHeight) {
                        mStatusBar.switchToSettings();
                    } else {
                        mStatusBar.flipToSettings();
                    }
                    mOkToFlip = false;
                }
            }
            else if (swipeFlip) {
                mStatusBar.flipPanels();
            }
        }
        return mHandleView.dispatchTouchEvent(event);
    }
}
