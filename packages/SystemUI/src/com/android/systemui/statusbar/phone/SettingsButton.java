/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.view.animation.DecelerateInterpolator;
import com.android.keyguard.AlphaOptimizedImageButton;

public class SettingsButton extends AlphaOptimizedImageButton {

    private static final long LONG_PRESS_LENGTH = 1000;
    private static final long ACCEL_LENGTH = 750;
    private static final long FULL_SPEED_LENGTH = 375;
    private static final long RUN_DURATION = 350;

    private boolean mUpToSpeed;
    private ObjectAnimator mAnimator;

    private float mSlop;

    public SettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public boolean isAnimating() {
        return mAnimator != null && mAnimator.isRunning();
    }

    public boolean isTunerClick() {
        return mUpToSpeed;
    }

    public void consumeClick() {
        mUpToSpeed = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                postDelayed(mLongPressCallback, LONG_PRESS_LENGTH);
                break;
            case MotionEvent.ACTION_UP:
                if (mUpToSpeed) {
                    startExitAnimation();
                } else {
                    cancelLongClick();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelLongClick();
                break;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                if ((x < -mSlop) || (y < -mSlop) || (x > getWidth() + mSlop)
                        || (y > getHeight() + mSlop)) {
                    cancelLongClick();
                    startExitAnimation();
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void cancelLongClick() {
        cancelAnimation();
        mUpToSpeed = false;
        removeCallbacks(mLongPressCallback);
    }

    private void cancelAnimation() {
        if (mAnimator != null) {
            mAnimator.removeAllListeners();
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    private void startExitAnimation() {
        cancelAnimation();
        animate()
                .rotation(0)
                .setDuration(ACCEL_LENGTH)
                .setInterpolator(new DecelerateInterpolator(4))
                .start();
    }

    protected void startAccelSpin() {
        cancelAnimation();
        mAnimator = ObjectAnimator.ofFloat(this, View.ROTATION, 0, 360);
        mAnimator.setInterpolator(AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.accelerate_quad));
        mAnimator.setDuration(ACCEL_LENGTH);
        mAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                startContinuousSpin();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }
        });
        mAnimator.start();
    }

    protected void startContinuousSpin() {
        cancelAnimation();
        mUpToSpeed = true;
        mAnimator = ObjectAnimator.ofFloat(this, View.ROTATION, 0, 360);
        mAnimator.setInterpolator(AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.linear));
        mAnimator.setDuration(FULL_SPEED_LENGTH);
        mAnimator.setRepeatCount(Animation.INFINITE);
        mAnimator.start();

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private final Runnable mLongPressCallback = new Runnable() {
        @Override
        public void run() {
            startAccelSpin();
        }
    };
}
