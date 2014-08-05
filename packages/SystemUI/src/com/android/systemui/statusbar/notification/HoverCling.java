/*
 * Copyright (C) 2014 ParanoidAndroid
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

package com.android.systemui.statusbar.notification;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;

import com.android.systemui.R;

/**
 * Helper to manage showing/hiding a confirmation prompt when hover is activated
 * for first time.
 */
public class HoverCling {

    private static final String TAG = "HoverConfirmation";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SHOW_EVERY_TIME = false;

    private final Context mContext;
    private final H mHandler;
    private final long mShowDelayMs;

    private ClingWindowView mClingWindow;
    private WindowManager mWindowManager;
    private boolean mFirstRun;

    public HoverCling(Context context) {
        mContext = context;
        mHandler = new H();
        mShowDelayMs = getNavBarExitDuration() * 3;
        mWindowManager = (WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    private long getNavBarExitDuration() {
        Animation exit = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.dock_bottom_exit);
        return exit != null ? exit.getDuration() : 0;
    }

    public boolean loadSetting() {
        if (DEBUG) Slog.d(TAG, "loadSetting()");
        mFirstRun = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.HOVER_FIRST_TIME, 0,
                UserHandle.USER_CURRENT) == 0;
        if (DEBUG) Slog.d(TAG, "Loaded mFirstRun=" + mFirstRun);
        return mFirstRun;
    }

    private void saveSetting() {
        if (DEBUG) Slog.d(TAG, "saveSetting()");
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.HOVER_FIRST_TIME,
                mFirstRun ? 0 : 1,
                UserHandle.USER_CURRENT);
        if (DEBUG) Slog.d(TAG, "Saved firsttime=" + mFirstRun);
    }

    public void hoverChanged(boolean isHoverActive) {
        mHandler.removeMessages(H.SHOW);
        if (isHoverActive) {
            if (DEBUG_SHOW_EVERY_TIME || mFirstRun) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(H.SHOW), mShowDelayMs);
            }
        } else {
            mHandler.sendEmptyMessage(H.HIDE);
        }
    }

    public void confirmCurrentPrompt() {
        mHandler.post(confirmAction());
    }

    private void handleHide() {
        if (mClingWindow != null) {
            if (DEBUG) Slog.d(TAG, "Hiding hover confirmation");
            mWindowManager.removeView(mClingWindow);
            mClingWindow = null;
            mFirstRun = false;
            saveSetting();
        }
    }

    public WindowManager.LayoutParams getClingWindowLayoutParams() {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_TOAST,
                0
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                ,
                PixelFormat.TRANSLUCENT
        );
        lp.setTitle("HoverConfirmation");
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.gravity = Gravity.FILL;
        return lp;
    }

    public FrameLayout.LayoutParams getBubbleLayoutParams() {
        int gravity = Gravity.CENTER_HORIZONTAL;
        gravity |= Gravity.TOP;
        return new FrameLayout.LayoutParams(
                mContext.getResources().getDimensionPixelSize(
                        R.dimen.hover_cling_width),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                gravity
        );
    }

    private void handleShow() {
        if (DEBUG) Slog.d(TAG, "Showing hover confirmation");

        mClingWindow = new ClingWindowView(mContext, confirmAction());

        // we will be hiding the nav bar, so layout as if it's already hidden
        mClingWindow.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        // show the confirmation
        WindowManager.LayoutParams lp = getClingWindowLayoutParams();
        mWindowManager.addView(mClingWindow, lp);
    }

    private Runnable confirmAction() {
        return new Runnable() {
            @Override
            public void run() {
                if (!mFirstRun) {
                    if (DEBUG) Slog.d(TAG, "Exitting hover confirmation");
                    saveSetting();
                }
                handleHide();
            }
        };
    }

    private class ClingWindowView extends FrameLayout {
        private static final int BGCOLOR = 0x80000000;
        private static final int OFFSET_DP = 48;

        private final Runnable mConfirm;
        private final ColorDrawable mColor = new ColorDrawable(0);
        private ValueAnimator mColorAnim;
        private ViewGroup mClingLayout;

        private Runnable mUpdateLayoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (mClingLayout != null && mClingLayout.getParent() != null) {
                    mClingLayout.setLayoutParams(getBubbleLayoutParams());
                }
            }
        };

        private BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                    post(mUpdateLayoutRunnable);
                }
            }
        };

        public ClingWindowView(Context context, Runnable confirm) {
            super(context);
            mConfirm = confirm;
            setClickable(true);
            setBackground(mColor);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();

            DisplayMetrics metrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
            float density = metrics.density;

            mClingLayout = (ViewGroup)
                    View.inflate(getContext(), R.layout.hover_cling, null);

            final Button ok = (Button) mClingLayout.findViewById(R.id.ok);
            ok.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConfirm.run();
                }
            });
            addView(mClingLayout, getBubbleLayoutParams());

            if (ActivityManager.isHighEndGfx()) {
                final View bubble = mClingLayout.findViewById(R.id.text);
                bubble.setAlpha(0f);
                bubble.setTranslationY(-OFFSET_DP * density);
                bubble.animate()
                        .alpha(1f)
                        .translationY(0)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                ok.setAlpha(0f);
                ok.setTranslationY(-OFFSET_DP * density);
                ok.animate().alpha(1f)
                        .translationY(0)
                        .setDuration(300)
                        .setStartDelay(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                mColorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), 0, BGCOLOR);
                mColorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        final int c = (Integer) animation.getAnimatedValue();
                        mColor.setColor(c);
                    }
                });
                mColorAnim.setDuration(1000);
                mColorAnim.start();
            } else {
                mColor.setColor(BGCOLOR);
            }

            mContext.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        }

        @Override
        public void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mContext.unregisterReceiver(mReceiver);
        }

        @Override
        public boolean onTouchEvent(MotionEvent motion) {
            Slog.v(TAG, "ClingWindowView.onTouchEvent");
            return true;
        }
    }

    private final class H extends Handler {
        private static final int SHOW = 0;
        private static final int HIDE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW:
                    handleShow();
                    break;
                case HIDE:
                    handleHide();
                    break;
            }
        }
    }
}
