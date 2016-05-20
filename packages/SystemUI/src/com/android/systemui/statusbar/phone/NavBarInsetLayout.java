/*
 * Copyright (C) 2016 The CyanogenMod Project
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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.BaseStatusBar;
import cyanogenmod.providers.CMSettings;

public class NavBarInsetLayout extends FrameLayout {
    public static final String TAG = "NavBarInsetLayout";
    public static final boolean DEBUG = BaseStatusBar.DEBUG;

    boolean mLeftInsetMode = false;

    private int mLeftInset = 0;
    private int mRightInset = 0;

    private final Paint mTransparentSrcPaint = new Paint();

    private SettingsObserver mSettingsObserver;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    public NavBarInsetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMotionEventSplittingEnabled(false);
        mTransparentSrcPaint.setColor(0);
        mTransparentSrcPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        if (getFitsSystemWindows()) {
            boolean paddingChanged;

            if (mLeftInsetMode) {
                paddingChanged = insets.right != getPaddingRight()
                        || insets.top != getPaddingTop()
                        || insets.bottom != getPaddingBottom();

                if (insets.left != mLeftInset) {
                    mLeftInset = insets.left;
                    applyMargins();
                }
            } else {
                paddingChanged = insets.left != getPaddingLeft()
                        || insets.top != getPaddingTop()
                        || insets.bottom != getPaddingBottom();

                if (insets.right != mRightInset) {
                    mRightInset = insets.right;
                    applyMargins();
                }
            }

            // Drop top inset, apply left inset and pass through bottom inset.
            if (paddingChanged) {
                setPadding(mLeftInsetMode ? 0 : insets.left,
                        0,
                        mLeftInsetMode ? insets.right : 0,
                        0);
            }
            insets.left = 0;
            insets.top = 0;
            insets.right = 0;
        } else {
            boolean applyMargins = false;
            if (mLeftInset != 0) {
                mLeftInset = 0;
                applyMargins = true;
            }
            if (mRightInset != 0) {
                mRightInset = 0;
                applyMargins = true;
            }
            if (applyMargins) {
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
            if (child.getLayoutParams() instanceof InsetLayoutParams) {
                InsetLayoutParams lp = (InsetLayoutParams) child.getLayoutParams();
                if (!lp.ignoreRightInset) {
                    if (mLeftInsetMode && lp.leftMargin != mLeftInset) {
                        lp.leftMargin = mLeftInset;
                        if (lp.rightMargin != 0) {
                            lp.rightMargin = 0;
                        }
                    } else if (lp.rightMargin != mRightInset) {
                        lp.rightMargin = mRightInset;
                        if (lp.leftMargin != 0) {
                            lp.leftMargin = 0;
                        }
                    }
                    child.requestLayout();
                }
            }
        }
    }

    @Override
    public FrameLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new InsetLayoutParams(getContext(), attrs);
    }

    @Override
    protected FrameLayout.LayoutParams generateDefaultLayoutParams() {
        return new InsetLayoutParams(InsetLayoutParams.MATCH_PARENT,
                InsetLayoutParams.MATCH_PARENT);
    }

    private class SettingsObserver extends UserContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            mContext.getContentResolver().registerContentObserver(
                    CMSettings.System.getUriFor(CMSettings.System.NAVBAR_LEFT_IN_LANDSCAPE), false,
                    this, UserHandle.USER_CURRENT);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            boolean before = mLeftInsetMode;
            mLeftInsetMode = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                    CMSettings.System.NAVBAR_LEFT_IN_LANDSCAPE, 0, UserHandle.USER_CURRENT) == 1;
            if (mLeftInsetMode != before) {
                applyMargins();
            }
        }
    }

    public static class InsetLayoutParams extends FrameLayout.LayoutParams {

        public boolean ignoreRightInset;

        public InsetLayoutParams(int width, int height) {
            super(width, height);
        }

        public InsetLayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.StatusBarWindowView_Layout);
            ignoreRightInset = a.getBoolean(
                    R.styleable.StatusBarWindowView_Layout_ignoreRightInset, false);
            a.recycle();
        }
    }
}

