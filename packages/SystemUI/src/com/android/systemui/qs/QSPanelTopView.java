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
package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.systemui.R;

public class QSPanelTopView extends FrameLayout {

    private static final String TAG = "QSPanelTopView";

    public static final int TOAST_DURATION = 2000;

    protected View mEditTileInstructionView;
    protected View mDropTarget;
    protected View mBrightnessView;
    protected TextView mToastView;

    private boolean mEditing = false;
    private boolean mDisplayingInstructions = false;
    private boolean mDisplayingTrash = false;
    private boolean mDisplayingToast = false;

    private AnimatorSet mAnimator;

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mEditing;
    }

    public View getDropTarget() {
        return mDropTarget;
    }

    public View getBrightnessView() {
        return mBrightnessView;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTarget = findViewById(R.id.delete_container);
        mEditTileInstructionView = findViewById(R.id.edit_container);
        mBrightnessView = findViewById(R.id.brightness_container);
        mToastView = (TextView) findViewById(R.id.qs_toast);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        mBrightnessView.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        int dh = mBrightnessView.getMeasuredHeight();

        mDropTarget.measure(exactly(width), atMost(dh));
        mEditTileInstructionView.measure(exactly(width), atMost(dh));
        mToastView.measure(exactly(width), atMost(dh));

        setMeasuredDimension(width, mBrightnessView.getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean animateToState = !isLaidOut();
        super.onLayout(changed, left, top, right, bottom);
        if (animateToState) {
            Log.e(TAG, "first layout animating to state!");
            animateToState();
        }
    }

    private static int atMost(int height) {
        return MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    public void setEditing(boolean editing) {
        mEditing = editing;
        if (editing) {
            mDisplayingInstructions = true;
            mDisplayingTrash = false;
        } else {
            mDisplayingInstructions = false;
            mDisplayingTrash = false;
        }
        animateToState();
    }

    public void onStopDrag() {
        mDisplayingTrash = false;
        animateToState();
    }

    public void onStartDrag() {
        mDisplayingTrash = true;
        animateToState();
    }

    public void toast(int textStrResId) {
        mDisplayingToast = true;
        mToastView.setText(textStrResId);
        animateToState();
    }

    private Runnable mAnimateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAnimator != null) {
                mAnimator.cancel();
            }
            mAnimator = new AnimatorSet();

            final boolean showToast = mDisplayingToast;
            final boolean showTrash = mDisplayingTrash && !mDisplayingToast;
            final boolean showBrightness = !mEditing && !mDisplayingToast;
            final boolean showInstructions = mEditing
                    && mDisplayingInstructions
                    && !mDisplayingTrash
                    && !mDisplayingToast;

            /*Log.d(TAG, "animating to state: "
                    + " showBrightness: " + showBrightness
                    + " showInstructions: " + showInstructions
                    + " showTrash: " + showTrash
                    + " showToast: " + showToast
            );*/

            final Animator brightnessAnimator = showBrightnessSlider(showBrightness);
            final Animator instructionAnimator = showInstructions(showInstructions);
            final Animator trashAnimator = showTrash(showTrash);
            final Animator toastAnimator = showToast(showToast);

            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setLayerType(LAYER_TYPE_HARDWARE, null);

                    mDropTarget.setLayerType(LAYER_TYPE_HARDWARE, null);
                    mEditTileInstructionView.setLayerType(LAYER_TYPE_HARDWARE, null);
                    mBrightnessView.setLayerType(LAYER_TYPE_HARDWARE, null);
                    mToastView.setLayerType(LAYER_TYPE_HARDWARE, null);

                    mDropTarget.setVisibility(View.VISIBLE);
                    mEditTileInstructionView.setVisibility(View.VISIBLE);
                    mBrightnessView.setVisibility(View.VISIBLE);
                    mToastView.setVisibility(View.VISIBLE);

                    if (showToast) {
                        mToastView.bringToFront();
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mToastView.setVisibility(showToast ? View.VISIBLE : View.GONE);
                    mEditTileInstructionView.setVisibility(showInstructions ? View.VISIBLE : View.GONE);
                    mDropTarget.setVisibility(showTrash ? View.VISIBLE : View.GONE);
                    mBrightnessView.setVisibility(showBrightness ? View.VISIBLE : View.GONE);

                    setLayerType(LAYER_TYPE_NONE, null);

                    mDropTarget.setLayerType(LAYER_TYPE_NONE, null);
                    mEditTileInstructionView.setLayerType(LAYER_TYPE_NONE, null);
                    mBrightnessView.setLayerType(LAYER_TYPE_NONE, null);
                    mToastView.setLayerType(LAYER_TYPE_NONE, null);

                    if (showToast) {
                        mToastView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mDisplayingToast = false;
                                animateToState();
                            }
                        }, TOAST_DURATION);
                    }
                }
            });

            mAnimator.setDuration(500);
            mAnimator.setInterpolator(new FastOutSlowInInterpolator());
            mAnimator.setStartDelay(100);
            mAnimator.playTogether(instructionAnimator, trashAnimator,
                    brightnessAnimator, toastAnimator);
            mAnimator.start();
        }
    };

    private void animateToState() {
        post(mAnimateRunnable);
    }
    private Animator animateView(View v, boolean show) {
        return ObjectAnimator.ofFloat(v, "translationY",
                show ? 0 : -mBrightnessView.getMeasuredHeight());
    }

    private Animator showBrightnessSlider(boolean show) {
        return animateView(mBrightnessView, show);
    }

    private Animator showInstructions(boolean show) {
        return animateView(mEditTileInstructionView, show);
    }

    private Animator showTrash(boolean show) {
        return animateView(mDropTarget, show);
    }

    private Animator showToast(boolean show) {
        return animateView(mToastView, show);
    }
}
