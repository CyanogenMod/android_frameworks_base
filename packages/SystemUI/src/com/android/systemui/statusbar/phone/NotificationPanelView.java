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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.EventLog;
import android.view.MotionEvent;
import android.view.View;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.systemui.EventLogTags;
import com.android.systemui.R;
import com.android.systemui.statusbar.GestureRecorder;

import java.io.File;

public class NotificationPanelView extends PanelView {
    public static final boolean DEBUG_GESTURES = false;

    private static final float STATUS_BAR_SWIPE_TRIGGER_PERCENTAGE = 0.05f;
    private static final float STATUS_BAR_SWIPE_VERTICAL_MAX_PERCENTAGE = 0.025f;
    private static final float STATUS_BAR_SWIPE_MOVE_PERCENTAGE = 0.2f;
    private static final float STATUS_BAR_LEFT_PERCENTAGE = 0.7f;
    private static final float STATUS_BAR_RIGHT_PERCENTAGE = 0.3f;

    Drawable mHandleBar;
    Drawable mBackgroundDrawable;
    Drawable mBackgroundDrawableLandscape;
    int mHandleBarHeight;
    View mHandleView;
    ImageView mBackground;
    int mFingers;
    PhoneStatusBar mStatusBar;
    boolean mOkToFlip;

    private float mGestureStartX;
    private float mGestureStartY;
    private float mFlipOffset;
    private float mSwipeDirection;
    private boolean mTrackingSwipe;
    private boolean mSwipeTriggered;

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
        mHandleBarHeight = resources.getDimensionPixelSize(R.dimen.close_handle_height);
        mHandleView = findViewById(R.id.handle);
        mBackground = (ImageView) findViewById(R.id.notification_wallpaper);
        setBackgroundDrawables();
    }

    @Override
    public void fling(float vel, boolean always) {
        if (DEBUG_GESTURES) {
            GestureRecorder gr = ((PhoneStatusBarView) mBar).mBar.getGestureRecorder();
            if (gr != null ) {
                gr.tag(
                    "fling " + ((vel > 0) ? "open" : "closed"),
                    "notifications,v=" + vel);
            }
        }
        super.fling(vel, always);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.getText()
                    .add(getContext().getString(R.string.accessibility_desc_notification_shade));
            return true;
        }

        return super.dispatchPopulateAccessibilityEvent(event);
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
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_NOTIFICATIONPANEL_TOUCH,
                       event.getActionMasked(), (int) event.getX(), (int) event.getY());
            }
        }
        boolean shouldRecycleEvent = false;
        if (PhoneStatusBar.SETTINGS_DRAG_SHORTCUT && mStatusBar.mHasFlipSettings) {
            boolean swipeFlipJustFinished = false;
            boolean swipeFlipJustStarted = false;
            boolean flip = false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mGestureStartX = event.getX(0);
                    mGestureStartY = event.getY(0);
                    mTrackingSwipe = isFullyExpanded();
                    mOkToFlip = getExpandedHeight() == 0;
                    int quickPulldownMode = Settings.System.getIntForUser(
                            getContext().getContentResolver(), Settings.System.QS_QUICK_PULLDOWN,
                            0, UserHandle.USER_CURRENT);
                    // Reminder: Smart PullDown linked in PhoneStatusBar via SlimActions as well
                    int smartPulldownMode = Settings.System.getIntForUser(
                            getContext().getContentResolver(), Settings.System.QS_SMART_PULLDOWN,
                            0, UserHandle.USER_CURRENT);
                    if (smartPulldownMode == 1 && !mStatusBar.hasClearableNotifications()) {
                        flip = true;
                    } else if (smartPulldownMode == 2 && !mStatusBar.hasVisibleNotifications()) {
                        flip = true;
                    } else if (smartPulldownMode == 3 && (!mStatusBar.hasVisibleNotifications()
                            || !mStatusBar.hasClearableNotifications())) {
                        flip = true;
                    } else if (quickPulldownMode == 1
                            && mGestureStartX > getWidth() * (1.0f - STATUS_BAR_RIGHT_PERCENTAGE)) {
                        flip = true;
                    } else if (quickPulldownMode == 2
                            && mGestureStartX < getWidth() * (1.0f - STATUS_BAR_LEFT_PERCENTAGE)) {
                        flip = true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    final float deltaX = Math.abs(event.getX(0) - mGestureStartX);
                    final float deltaY = Math.abs(event.getY(0) - mGestureStartY);
                    final float maxDeltaY = getHeight() * STATUS_BAR_SWIPE_VERTICAL_MAX_PERCENTAGE;
                    final float minDeltaX = getWidth() * STATUS_BAR_SWIPE_TRIGGER_PERCENTAGE;

                    if (mTrackingSwipe && deltaY > maxDeltaY) {
                        mTrackingSwipe = false;
                    }
                    if (mTrackingSwipe && deltaX > deltaY && deltaX > minDeltaX) {

                        mSwipeDirection = event.getX(0) - mGestureStartX;

                        // The value below can be used to adjust deltaX to always increase,
                        // if the user keeps swiping in the same direction as she started the
                        // gesture. If she, however, moves her finger the other way, deltaX will
                        // decrease.
                        //
                        // This allows for a horizontal, in any direction, to always flip the
                        // views.
                        mSwipeDirection = mSwipeDirection < 0f ? -1f : 1f;

                        if (mStatusBar.isShowingSettings()) {
                            mFlipOffset = 1f;
                            // in this case, however, we need deltaX to decrease
                            mSwipeDirection = -mSwipeDirection;
                        } else {
                            mFlipOffset = -1f;
                        }
                        mGestureStartX = event.getX(0);
                        mTrackingSwipe = false;
                        mSwipeTriggered = true;
                        swipeFlipJustStarted = true;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    flip = true;
                    break;
                case MotionEvent.ACTION_UP:
                    swipeFlipJustFinished = mSwipeTriggered;
                    mSwipeTriggered = false;
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
                    if (mJustPeeked || getExpandedHeight() < mHandleBarHeight) {
                        mStatusBar.switchToSettings();
                    } else {
                        mStatusBar.flipToSettings();
                    }
                    mOkToFlip = false;
                }
            }

            if (mSwipeTriggered) {
                final float deltaX = (event.getX(0) - mGestureStartX) * mSwipeDirection;
                mStatusBar.partialFlip(mFlipOffset +
                                       deltaX / (getWidth() * STATUS_BAR_SWIPE_MOVE_PERCENTAGE));
                if (!swipeFlipJustStarted) {
                    return true; // Consume the event.
                }
            } else if (swipeFlipJustFinished) {
                mStatusBar.completePartialFlip();
            }

            if (swipeFlipJustStarted || swipeFlipJustFinished) {
                // Made up event: finger at the middle bottom of the view.
                MotionEvent original = event;
                event = MotionEvent.obtain(original.getDownTime(), original.getEventTime(),
                    original.getAction(), getWidth()/2, getHeight(),
                    original.getPressure(0), original.getSize(0), original.getMetaState(),
                    original.getXPrecision(), original.getYPrecision(), original.getDeviceId(),
                    original.getEdgeFlags());
                shouldRecycleEvent = true;
            }

        }

        final boolean result = mHandleView.dispatchTouchEvent(event);
        if (shouldRecycleEvent) {
            event.recycle();
        }
        return result;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
            setNotificationWallpaper();
    }

    private void setNotificationWallpaper() {
        if (mBackgroundDrawable == null) {
            return;
        }
        boolean isLandscape = false;
        Display display = ((WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = display.getRotation();
        switch(orientation) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                isLandscape = true;
                break;
        }

        if (mBackgroundDrawableLandscape != null && isLandscape) {
            mBackground.setImageDrawable(mBackgroundDrawableLandscape);
        } else {
            mBackground.setImageDrawable(mBackgroundDrawable);
        }
    }

    private void setDefaultBackground(int resource, int color, int alpha) {
        setBackgroundResource(resource);
        if (color != -2) {
            getBackground().setColorFilter(color, Mode.SRC_ATOP);
        } else {
            getBackground().setColorFilter(null);
        }
        getBackground().setAlpha(alpha);
        mBackgroundDrawableLandscape = null;
        mBackgroundDrawable = null;
        mBackground.setImageDrawable(null);
    }

    protected void setBackgroundDrawables() {
        float alpha = Settings.System.getFloatForUser(
                mContext.getContentResolver(),
                Settings.System.NOTIFICATION_BACKGROUND_ALPHA, 0.1f,
                UserHandle.USER_CURRENT);
        int backgroundAlpha = (int) ((1 - alpha) * 255);

        String notifiBack = Settings.System.getStringForUser(
                mContext.getContentResolver(),
                Settings.System.NOTIFICATION_BACKGROUND,
                UserHandle.USER_CURRENT);

        if (notifiBack == null) {
            setDefaultBackground(R.drawable.notification_panel_bg, -2, backgroundAlpha);
            return;
        }

        if (notifiBack.startsWith("color=")) {
            notifiBack = notifiBack.substring("color=".length());
            try {
                setDefaultBackground(R.drawable.notification_panel_bg,
                        Color.parseColor(notifiBack), backgroundAlpha);
            } catch(NumberFormatException e) {
            }
        } else {
            File f = new File(Uri.parse(notifiBack).getPath());
            if (f != null) {
                Bitmap backgroundBitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
                mBackgroundDrawable =
                        new BitmapDrawable(getContext().getResources(), backgroundBitmap);
            }
        }
        if (mBackgroundDrawable != null) {
            setBackgroundResource(com.android.internal.R.color.transparent);
            mBackgroundDrawable.setAlpha(backgroundAlpha);
        }

        notifiBack = Settings.System.getStringForUser(
                mContext.getContentResolver(),
                Settings.System.NOTIFICATION_BACKGROUND_LANDSCAPE,
                UserHandle.USER_CURRENT);

        mBackgroundDrawableLandscape = null;
        if (notifiBack != null) {
            File f = new File(Uri.parse(notifiBack).getPath());
            if (f != null) {
                Bitmap backgroundBitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
                mBackgroundDrawableLandscape =
                        new BitmapDrawable(getContext().getResources(), backgroundBitmap);
            }
        }
        if (mBackgroundDrawableLandscape != null) {
            mBackgroundDrawableLandscape.setAlpha(backgroundAlpha);
        }

        setNotificationWallpaper();
    }

}
