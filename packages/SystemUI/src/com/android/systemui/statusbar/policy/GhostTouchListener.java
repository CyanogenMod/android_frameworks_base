/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.systemui.statusbar.policy;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import com.android.systemui.R;

public class GhostTouchListener implements View.OnTouchListener {

    private static final String TAG = GhostTouchListener.class.getSimpleName();

    private KeyButtonView mButton;
    private ImageView mCursor;
    private WindowManager.LayoutParams mCursorParams;

    private PowerManager mPm;
    private WindowManager mWindowManager;

    private float mFinalX, mFinalY;
    private float mInitialRawX;
    private float mInitialRawY;
    private long mDownTime, mUpTime;

    private AnimatorSet mAnimators = new AnimatorSet();
    private Rect mViewRect = new Rect();

    VelocityTracker mVelocityTracker;

    private boolean mViewAttached;

    private Runnable mStartCursorAnimation = new Runnable() {
        @Override
        public void run() {
            mCursor.animate()
                    .withLayer()
                    .scaleX(.7f)
                    .scaleY(.7f)
                    .setDuration(100)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(mFinishCursorAnimation)
                    .start();
        }
    };

    private Runnable mFinishCursorAnimation = new Runnable() {
        @Override
        public void run() {
            mCursor.animate()
                    .withLayer()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(0)
                    .setDuration(200)
                    .withStartAction(mSendClick)
                    .withEndAction(mResetCursorPosition)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    };

    private Runnable mSendClick = new Runnable() {
        @Override
        public void run() {
            long start = SystemClock.uptimeMillis();
            sendMotionEvent(MotionEvent.ACTION_DOWN, start, start, mFinalX, mFinalY);
            sendMotionEvent(MotionEvent.ACTION_UP, start, start,
                    mFinalX, mFinalY);
            mButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    };

    private Runnable mResetCursorPosition = new Runnable() {
        @Override
        public void run() {
            mCursorParams.x = (int) mInitialRawX;
            mCursorParams.y = (int) mInitialRawY;
            mWindowManager.updateViewLayout(mCursor, mCursorParams);
        }
    };
    private int mTouchSlop;

    public GhostTouchListener(KeyButtonView button) {
        mButton = button;
        mPm = (PowerManager) button.getContext().getSystemService(Context.POWER_SERVICE);
        mWindowManager = (WindowManager) button.getContext()
                .getSystemService(Context.WINDOW_SERVICE);

        mCursor = new ImageView(button.getContext());
        mCursor.setImageResource(R.drawable.gggf_cursor);
        mCursor.setVisibility(View.GONE);

        mCursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        mCursorParams.gravity = Gravity.TOP | Gravity.LEFT;

        mTouchSlop = ViewConfiguration.get(button.getContext()).getScaledTouchSlop();
    }

    private void switchIcon(boolean animate, boolean closedState) {
        mButton.setImageResource(closedState
                ? R.drawable.gggf_avd_to_icon : R.drawable.gggf_avd_to_cancel);
        if (animate) {
            Drawable d = mButton.getDrawable();
            if (d instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) d).start();
            }
        }
    }

    private void sendMotionEvent(int action, long downTime, long when, float x, float y) {
        MotionEvent.PointerProperties[] pointerPropertieses = {
            new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()
        };
        pointerPropertieses[0].id = 0;
        pointerPropertieses[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        pointerPropertieses[1].id = 1;
        pointerPropertieses[1].toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords[] pointerCoords = {
                new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()
        };
        pointerCoords[0].x = x;
        pointerCoords[0].y = y;
        pointerCoords[1].x = x;
        pointerCoords[1].y = y;

        MotionEvent e = MotionEvent.obtain(downTime, when, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        InputManager.getInstance().injectInputEvent(e,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        e.recycle();
    }

    private void animateBack() {
        if (!mViewAttached) {
            return;
        }
        mAnimators.cancel();

        ValueAnimator x = ValueAnimator.ofInt(mCursorParams.x, (int) mInitialRawX
                - (mCursor.getWidth() / 2));
        x.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCursor.setScaleX(1 - animation.getAnimatedFraction());
                mCursorParams.x = (int) animation.getAnimatedValue();
                if (mViewAttached) {
                    mWindowManager.updateViewLayout(mCursor, mCursorParams);
                }
            }
        });
        ValueAnimator y = ValueAnimator.ofInt(mCursorParams.y, (int) mInitialRawY
                - (mCursor.getHeight() / 2));
        y.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCursor.setScaleY(1 - animation.getAnimatedFraction());
                mCursorParams.y = (int) animation.getAnimatedValue();
                if (mViewAttached) {
                    mWindowManager.updateViewLayout(mCursor, mCursorParams);
                }
            }
        });
        mAnimators.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mViewAttached) {
                    mWindowManager.removeView(mCursor);
                    mViewAttached = false;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mAnimators.setInterpolator(new AccelerateInterpolator(1.3f));
        mAnimators.playTogether(x, y);
        mAnimators.start();
    }

    private void initVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = VelocityTracker.obtain();
    }

    private void trackMovement(MotionEvent event) {
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(event);
        }
    }

    private void resetCursor(boolean visible) {
        mCursor.animate().cancel();
        mCursor.setAlpha(1.f);
        mCursor.setScaleX(1.f);
        mCursor.setScaleY(1.f);
        mCursor.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void doClick() {
        mStartCursorAnimation.run();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mPm.cpuBoost(70000);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();

                initVelocityTracker();
                trackMovement(event);

                mInitialRawX = event.getRawX();
                mInitialRawY = event.getRawY();

                resetCursor(true);
                if (!mViewAttached) {
                    int[] locationOnScreen = mButton.getLocationOnScreen();
                    mViewRect.set(locationOnScreen[0],
                            locationOnScreen[1],
                            locationOnScreen[0] + mButton.getWidth(),
                            locationOnScreen[1] + mButton.getHeight());
                    mWindowManager.addView(mCursor, mCursorParams);
                    mViewAttached = true;
                }

                mCursorParams.x = (int) mInitialRawX - (mCursor.getWidth() / 2);
                mCursorParams.y = (int) mInitialRawY - (mCursor.getHeight() / 2);
                mWindowManager.updateViewLayout(mCursor, mCursorParams);
                mButton.setPressed(true);

                switchIcon(true, false);

                mButton.getParent().requestDisallowInterceptTouchEvent(true);

                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mUpTime = System.currentTimeMillis();
                trackMovement(event);

                //raw touch position of the up action
                float upTouchX = event.getRawX();
                float upTouchY = event.getRawY();

                //get the abs value of movement to check for dragging vs tapping
                float touchMoveX = Math.abs(upTouchX - mInitialRawX);
                float touchMoveY = Math.abs(upTouchY - mInitialRawY);
                final boolean quickUpDown = mUpTime - mDownTime < 200;
                final boolean motionDetected = touchMoveX > mTouchSlop
                        && touchMoveY > mTouchSlop;

                //check to see if we have dragged or tapped the button
                if (!mButton.isPressed() && (!quickUpDown && motionDetected)) {
                    // not pressed (moved out of button) or we detect a motion
                    doClick();
                } else {
                    animateBack();
                }

                switchIcon(true, true);
                mButton.setPressed(false);

                break;
            case MotionEvent.ACTION_MOVE:

                // initial coords + (actual distance moved * 3)
                int moverX = ((int) mInitialRawX + ((int) (event.getRawX() - mInitialRawX) * 3))
                        - (mCursor.getWidth() / 2);
                int moverY = ((int) mInitialRawY + ((int) (event.getRawY() - mInitialRawY) * 3))
                        - (mCursor.getHeight() / 2);
                mCursorParams.x = moverX;
                mCursorParams.y = moverY;
                if (mCursor.isAttachedToWindow()) {
                    mWindowManager.updateViewLayout(mCursor, mCursorParams);
                }

                // adjust final hit coords to be exactly in the center
                mFinalX = moverX + (mCursor.getWidth() / 2);
                mFinalY = (moverY) + (mCursor.getHeight() / 2);

                boolean touchInsideButton = mViewRect.contains((int) event.getRawX(),
                        (int) event.getRawY());
                if (mButton.isPressed() != touchInsideButton) {
                    mButton.setPressed(touchInsideButton);
                }

                trackMovement(event);

                break;

        }
        return true;
    }
}
