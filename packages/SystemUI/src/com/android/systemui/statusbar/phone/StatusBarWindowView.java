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

import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DragDownHelper;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import cyanogenmod.providers.CMSettings;


public class StatusBarWindowView extends FrameLayout {
    public static final String TAG = "StatusBarWindowView";
    public static final boolean DEBUG = BaseStatusBar.DEBUG;

    private DragDownHelper mDragDownHelper;
    private NotificationStackScrollLayout mStackScrollLayout;
    private NotificationPanelView mNotificationPanel;
    private View mBrightnessMirror;

    private int mRightInset = 0;

    private PhoneStatusBar mService;
    private final Paint mTransparentSrcPaint = new Paint();

    private int mStatusBarHeaderHeight;

    private boolean mDoubleTapToSleepEnabled;
    private GestureDetector mDoubleTapGesture;
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;

    public StatusBarWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMotionEventSplittingEnabled(false);
        mTransparentSrcPaint.setColor(0);
        mTransparentSrcPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mStatusBarHeaderHeight = context
                .getResources().getDimensionPixelSize(R.dimen.status_bar_header_height);
        mSettingsObserver = new SettingsObserver(mHandler);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        if (getFitsSystemWindows()) {
            boolean paddingChanged = insets.left != getPaddingLeft()
                    || insets.top != getPaddingTop()
                    || insets.bottom != getPaddingBottom();

            // Super-special right inset handling, because scrims and backdrop need to ignore it.
            if (insets.right != mRightInset) {
                mRightInset = insets.right;
                applyMargins();
            }
            // Drop top inset, apply left inset and pass through bottom inset.
            if (paddingChanged) {
                setPadding(insets.left, 0, 0, 0);
            }
            insets.left = 0;
            insets.top = 0;
            insets.right = 0;
        } else {
            if (mRightInset != 0) {
                mRightInset = 0;
                applyMargins();
            }
            boolean changed = getPaddingLeft() != 0
                    || getPaddingRight() != 0
                    || getPaddingTop() != 0
                    || getPaddingBottom() != 0;
            if (changed) {
                setPadding(0, 0, 0, 0);
            }
            insets.top = 0;
        }
        return false;
    }

    private void applyMargins() {
        final int N = getChildCount();
        for (int i = 0; i < N; i++) {
            View child = getChildAt(i);
            if (child.getLayoutParams() instanceof LayoutParams) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!lp.ignoreRightInset && lp.rightMargin != mRightInset) {
                    lp.rightMargin = mRightInset;
                    child.requestLayout();
                }
            }
        }
    }

    @Override
    public FrameLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected FrameLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStackScrollLayout = (NotificationStackScrollLayout) findViewById(
                R.id.notification_stack_scroller);
        mNotificationPanel = (NotificationPanelView) findViewById(R.id.notification_panel);
        mBrightnessMirror = findViewById(R.id.brightness_mirror);
    }

    public void setService(PhoneStatusBar service) {
        mService = service;
        mDragDownHelper = new DragDownHelper(getContext(), this, mStackScrollLayout, mService);
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();

        mSettingsObserver.observe();
        mDoubleTapGesture = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                Log.d(TAG, "Gesture!!");
                if(pm != null)
                    pm.goToSleep(e.getEventTime());
                else
                    Log.d(TAG, "getSystemService returned null PowerManager");

                return true;
            }
        });

        // We really need to be able to animate while window animations are going on
        // so that activities may be started asynchronously from panel animations
        final ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }

        // We need to ensure that our window doesn't suffer from overdraw which would normally
        // occur if our window is translucent. Since we are drawing the whole window anyway with
        // the scrim, we don't need the window to be cleared in the beginning.
        if (mService.isScrimSrcModeEnabled()) {
            IBinder windowToken = getWindowToken();
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            lp.token = windowToken;
            setLayoutParams(lp);
            WindowManagerGlobal.getInstance().changeCanvasOpacity(windowToken, true);
            setWillNotDraw(false);
        } else {
            setWillNotDraw(!DEBUG);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    mService.onBackPressed();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!down) {
                    return mService.onMenuPressed();
                }
            case KeyEvent.KEYCODE_SPACE:
                if (!down) {
                    return mService.onSpacePressed();
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (mService.isDozing()) {
                    MediaSessionLegacyHelper.getHelper(mContext).sendVolumeKeyEvent(event, true);
                    return true;
                }
                break;
        }
        if (mService.interceptMediaKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mBrightnessMirror != null && mBrightnessMirror.getVisibility() == VISIBLE) {
            // Disallow new pointers while the brightness mirror is visible. This is so that you
            // can't touch anything other than the brightness slider while the mirror is showing
            // and the rest of the panel is transparent.
            if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = false;
        if (mDoubleTapToSleepEnabled
                && ev.getY() < mStatusBarHeaderHeight) {
            if (DEBUG) Log.w(TAG, "logging double tap gesture");
            mDoubleTapGesture.onTouchEvent(ev);
        }
        if (mNotificationPanel.isFullyExpanded()
                && mStackScrollLayout.getVisibility() == View.VISIBLE
                && mService.getBarState() == StatusBarState.KEYGUARD
                && !mService.isBouncerShowing()) {
            intercept = mDragDownHelper.onInterceptTouchEvent(ev);
            // wake up on a touch down event, if dozing
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mService.wakeUpIfDozing(ev.getEventTime(), ev);
            }
        }
        if (!intercept) {
            super.onInterceptTouchEvent(ev);
        }
        if (intercept) {
            MotionEvent cancellation = MotionEvent.obtain(ev);
            cancellation.setAction(MotionEvent.ACTION_CANCEL);
            mStackScrollLayout.onInterceptTouchEvent(cancellation);
            mNotificationPanel.onInterceptTouchEvent(cancellation);
            cancellation.recycle();
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = false;
        if (mService.getBarState() == StatusBarState.KEYGUARD) {
            handled = mDragDownHelper.onTouchEvent(ev);
        }
        if (!handled) {
            handled = super.onTouchEvent(ev);
        }
        final int action = ev.getAction();
        if (!handled && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            mService.setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
        }
        return handled;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mService.isScrimSrcModeEnabled()) {
            // We need to ensure that our window is always drawn fully even when we have paddings,
            // since we simulate it to be opaque.
            int paddedBottom = getHeight() - getPaddingBottom();
            int paddedRight = getWidth() - getPaddingRight();
            if (getPaddingTop() != 0) {
                canvas.drawRect(0, 0, getWidth(), getPaddingTop(), mTransparentSrcPaint);
            }
            if (getPaddingBottom() != 0) {
                canvas.drawRect(0, paddedBottom, getWidth(), getHeight(), mTransparentSrcPaint);
            }
            if (getPaddingLeft() != 0) {
                canvas.drawRect(0, getPaddingTop(), getPaddingLeft(), paddedBottom,
                        mTransparentSrcPaint);
            }
            if (getPaddingRight() != 0) {
                canvas.drawRect(paddedRight, getPaddingTop(), getWidth(), paddedBottom,
                        mTransparentSrcPaint);
            }
        }
        if (DEBUG) {
            Paint pt = new Paint();
            pt.setColor(0x80FFFF00);
            pt.setStrokeWidth(12.0f);
            pt.setStyle(Paint.Style.STROKE);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), pt);
        }
    }

    public void cancelExpandHelper() {
        if (mStackScrollLayout != null) {
            mStackScrollLayout.cancelExpandHelper();
        }
    }

    public void addContent(View content) {
        addView(content);
        mStackScrollLayout = (NotificationStackScrollLayout) content.findViewById(
                R.id.notification_stack_scroller);
        mNotificationPanel = (NotificationPanelView) content.findViewById(R.id.notification_panel);
        mDragDownHelper = new DragDownHelper(getContext(), this, mStackScrollLayout, mService);
        mBrightnessMirror = content.findViewById(R.id.brightness_mirror);

    }

    public void removeContent(View content) {
        removeView(content);
    }

    public class LayoutParams extends FrameLayout.LayoutParams {

        public boolean ignoreRightInset;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.StatusBarWindowView_Layout);
            ignoreRightInset = a.getBoolean(
                    R.styleable.StatusBarWindowView_Layout_ignoreRightInset, false);
            a.recycle();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                            CMSettings.System.DOUBLE_TAP_SLEEP_GESTURE), false, this);
            update();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mDoubleTapToSleepEnabled = CMSettings.System
                    .getInt(resolver, CMSettings.System.DOUBLE_TAP_SLEEP_GESTURE, 1) == 1;
        }
    }
}

