/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.util.ArrayList;

public class ScreenPinningRequest implements View.OnClickListener {
    private final Context mContext;

    private final AccessibilityManager mAccessibilityService;
    private final WindowManager mWindowManager;
    private final IWindowManager mWindowManagerService;

    private RequestWindowView mRequestWindow;

    public ScreenPinningRequest(Context context) {
        mContext = context;
        mAccessibilityService = (AccessibilityManager)
                mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mWindowManager = (WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
    }

    public void clearPrompt() {
        if (mRequestWindow != null) {
            mWindowManager.removeView(mRequestWindow);
            mRequestWindow = null;
        }
    }

    public void showPrompt(boolean allowCancel) {
        clearPrompt();

        mRequestWindow = new RequestWindowView(mContext, allowCancel);

        mRequestWindow.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // show the confirmation
        WindowManager.LayoutParams lp = getWindowLayoutParams();
        mWindowManager.addView(mRequestWindow, lp);
    }

    public void onConfigurationChanged() {
        if (mRequestWindow != null) {
            mRequestWindow.onConfigurationChanged();
        }
    }

    private WindowManager.LayoutParams getWindowLayoutParams() {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                0
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                ,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        lp.setTitle("ScreenPinningConfirmation");
        lp.gravity = Gravity.FILL;
        return lp;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.screen_pinning_ok_button || mRequestWindow == v) {
            try {
                ActivityManagerNative.getDefault().startLockTaskModeOnCurrent();
            } catch (RemoteException e) {}
        }
        clearPrompt();
    }

    public FrameLayout.LayoutParams getRequestLayoutParams(boolean isLandscape) {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                isLandscape ? (Gravity.CENTER_VERTICAL | Gravity.RIGHT)
                            : (Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
    }

    private class RequestWindowView extends FrameLayout {
        private static final int OFFSET_DP = 96;

        private final ColorDrawable mColor = new ColorDrawable(0);
        private ValueAnimator mColorAnim;
        private ViewGroup mLayout;
        private boolean mShowCancel;

        public RequestWindowView(Context context, boolean showCancel) {
            super(context);
            setClickable(true);
            setOnClickListener(ScreenPinningRequest.this);
            setBackground(mColor);
            mShowCancel = showCancel;
        }

        @Override
        public void onAttachedToWindow() {
            DisplayMetrics metrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
            float density = metrics.density;
            boolean isLandscape = isLandscapePhone(mContext);

            inflateView(isLandscape);
            int bgColor = mContext.getColor(
                    R.color.screen_pinning_request_window_bg);
            if (ActivityManager.isHighEndGfx()) {
                mLayout.setAlpha(0f);
                if (isLandscape) {
                    mLayout.setTranslationX(OFFSET_DP * density);
                } else {
                    mLayout.setTranslationY(OFFSET_DP * density);
                }
                mLayout.animate()
                        .alpha(1f)
                        .translationX(0)
                        .translationY(0)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                mColorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), 0, bgColor);
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
                mColor.setColor(bgColor);
            }

            IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(mReceiver, filter);
        }

        private boolean isLandscapePhone(Context context) {
            Configuration config = mContext.getResources().getConfiguration();
            return config.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && config.smallestScreenWidthDp < 600;
        }

        private void inflateView(boolean isLandscape) {
            // We only want this landscape orientation on <600dp, so rather than handle
            // resource overlay for -land and -sw600dp-land, just inflate this
            // other view for this single case.
            mLayout = (ViewGroup) View.inflate(getContext(), isLandscape
                    ? R.layout.screen_pinning_request_land_phone : R.layout.screen_pinning_request,
                    null);
            // Catch touches so they don't trigger cancel/activate, like outside does.
            mLayout.setClickable(true);
            // Status bar is always on the right.
            mLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            // Buttons and text do switch sides though.
            View buttons = mLayout.findViewById(R.id.screen_pinning_buttons);
            buttons.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            mLayout.findViewById(R.id.screen_pinning_text_area)
                    .setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            swapChildrenIfRtlAndVertical(buttons);

            ((Button) mLayout.findViewById(R.id.screen_pinning_ok_button))
                    .setOnClickListener(ScreenPinningRequest.this);
            if (mShowCancel) {
                ((Button) mLayout.findViewById(R.id.screen_pinning_cancel_button))
                        .setOnClickListener(ScreenPinningRequest.this);
            } else {
                ((Button) mLayout.findViewById(R.id.screen_pinning_cancel_button))
                        .setVisibility(View.INVISIBLE);
            }

            final int description;
            if (hasNavigationBar()) {
                description = mAccessibilityService.isEnabled()
                    ? R.string.screen_pinning_description_accessible
                    : R.string.screen_pinning_description;
                final int backBgVis = 
                    mAccessibilityService.isEnabled() ? View.INVISIBLE : View.VISIBLE;
                mLayout.findViewById(R.id.screen_pinning_back_bg).setVisibility(backBgVis);
                mLayout.findViewById(R.id.screen_pinning_back_bg_light).setVisibility(backBgVis);
            } else {
                description = R.string.screen_pinning_description_no_navbar;
                ((ViewGroup) buttons.getParent()).removeView(buttons);
            }
            ((TextView) mLayout.findViewById(R.id.screen_pinning_description))
                    .setText(description);
            
            addView(mLayout, getRequestLayoutParams(isLandscape));
        }

        private boolean hasNavigationBar() {
            try {
                return mWindowManagerService.hasNavigationBar();
            } catch (RemoteException e) {
                //ignore
            }
            return false;
        }
        private void swapChildrenIfRtlAndVertical(View group) {
            if (mContext.getResources().getConfiguration().getLayoutDirection()
                    != View.LAYOUT_DIRECTION_RTL) {
                return;
            }
            LinearLayout linearLayout = (LinearLayout) group;
            if (linearLayout.getOrientation() == LinearLayout.VERTICAL) {
                int childCount = linearLayout.getChildCount();
                ArrayList<View> childList = new ArrayList<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    childList.add(linearLayout.getChildAt(i));
                }
                linearLayout.removeAllViews();
                for (int i = childCount - 1; i >= 0; i--) {
                    linearLayout.addView(childList.get(i));
                }
            }
        }

        @Override
        public void onDetachedFromWindow() {
            mContext.unregisterReceiver(mReceiver);
        }

        protected void onConfigurationChanged() {
            removeAllViews();
            inflateView(isLandscapePhone(mContext));
        }

        private final Runnable mUpdateLayoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (mLayout != null && mLayout.getParent() != null) {
                    mLayout.setLayoutParams(getRequestLayoutParams(isLandscapePhone(mContext)));
                }
            }
        };

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                    post(mUpdateLayoutRunnable);
                } else if (intent.getAction().equals(Intent.ACTION_USER_SWITCHED)
                        || intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    clearPrompt();
                }
            }
        };
    }

}
