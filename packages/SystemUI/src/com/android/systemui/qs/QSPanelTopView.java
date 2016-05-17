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
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.settings.ToggleSlider;

import cyanogenmod.providers.CMSettings;

public class QSPanelTopView extends FrameLayout {

    private static final String TAG = "QSPanelTopView";

    public static final int TOAST_DURATION = 2000;

    protected View mEditTileInstructionView;
    protected View mDropTarget;
    protected View mBrightnessView;
    protected TextView mToastView;
    protected View mAddTarget;
    protected TextView mEditInstructionText;

    private boolean mEditing = false;
    private boolean mDisplayingInstructions = false;
    private boolean mDisplayingTrash = false;
    private boolean mDisplayingToast = false;
    public boolean mHasBrightnessSliderToDisplay = true;

    private AnimatorSet mAnimator;
    private ImageView mDropTargetIcon;

    private SettingsObserver mSettingsObserver;
    private boolean mListening;
    private boolean mSkipAnimations;

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(true);
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mEditing;
    }

    public View getDropTarget() {
        return mDropTarget;
    }

    public ImageView getDropTargetIcon() {
        return mDropTargetIcon;
    }

    public View getBrightnessView() {
        return mBrightnessView;
    }

    public View getAddTarget() {
        return mAddTarget;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTarget = findViewById(R.id.delete_container);
        mDropTargetIcon = (ImageView) findViewById(R.id.delete_target);
        mEditTileInstructionView = findViewById(R.id.edit_container);
        mBrightnessView = findViewById(R.id.brightness_container);
        mToastView = (TextView) findViewById(R.id.qs_toast);
        mAddTarget = findViewById(R.id.add_target);
        mEditInstructionText = (TextView) findViewById(R.id.edit_text_instruction);
        updateResources();
    }

    public void updateResources() {
        if (mEditInstructionText != null) {
            mEditInstructionText.setText(R.string.qs_tile_edit_header_instruction);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        mBrightnessView.measure(QSDragPanel.exactly(width), MeasureSpec.UNSPECIFIED);
        mEditTileInstructionView.measure(QSDragPanel.exactly(width), MeasureSpec.UNSPECIFIED);
        mToastView.measure(QSDragPanel.exactly(width), MeasureSpec.UNSPECIFIED);

        // if we are showing a brightness slider, always fit to that, otherwise only
        // declare a height when editing.
        int dh = mHasBrightnessSliderToDisplay ? mBrightnessView.getMeasuredHeight()
                : mEditing ? mEditTileInstructionView.getMeasuredHeight() : 0;

        mDropTarget.measure(QSDragPanel.exactly(width), QSDragPanel.atMost(dh));
        mEditTileInstructionView.measure(QSDragPanel.exactly(width), QSDragPanel.atMost(dh));
        mToastView.measure(QSDragPanel.exactly(width), QSDragPanel.atMost(dh));

        setMeasuredDimension(width, dh);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean animateToState = !isLaidOut();
        super.onLayout(changed, left, top, right, bottom);
        if (animateToState) {
            goToState();
        }
    }

    public void setEditing(boolean editing, boolean skipAnim) {
        mEditing = editing;
        if (editing) {
            mDisplayingInstructions = true;
            mDisplayingTrash = false;
        } else {
            mDisplayingInstructions = false;
            mDisplayingTrash = false;
        }
        if (skipAnim) {
            goToState();
        } else {
            animateToState();
        }
    }

    public void onStopDrag() {
        mDisplayingTrash = false;
        animateToState();
    }

    public void onStartDrag() {
        mDisplayingTrash = true;
        animateToState();
    }

    public void setDropIcon(int resourceId, int colorResourceId) {
        mDropTargetIcon.setImageResource(resourceId);
        final Drawable drawable = mDropTargetIcon.getDrawable();

        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_ATOP);
        DrawableCompat.setTint(drawable, mContext.getColor(colorResourceId));

        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
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
                    // if the view is already visible, keep it visible on animation start
                    // to animate it out, otherwise set it as invisible (to not affect view height)
                    mEditTileInstructionView.setVisibility(
                            getVisibilityForAnimation(mEditTileInstructionView, showInstructions));
                    mDropTarget.setVisibility(
                            getVisibilityForAnimation(mDropTarget, showTrash));
                    mToastView.setVisibility(
                            getVisibilityForAnimation(mToastView, showToast));
                    if (mHasBrightnessSliderToDisplay) {
                        mBrightnessView.setVisibility(
                                getVisibilityForAnimation(mBrightnessView, showBrightness));
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mToastView.setVisibility(showToast ? View.VISIBLE : View.GONE);
                    mEditTileInstructionView.setVisibility(showInstructions
                            ? View.VISIBLE : View.GONE);
                    mDropTarget.setVisibility(showTrash ? View.VISIBLE : View.GONE);
                    if (mHasBrightnessSliderToDisplay) {
                        mBrightnessView.setVisibility(showBrightness ? View.VISIBLE : View.GONE);
                    }

                    mAnimator = null;

                    requestLayout();

                    if (showToast) {
                        mToastView.bringToFront();
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

            mAnimator.setDuration(mSkipAnimations ? 0 : 500);
            mAnimator.setInterpolator(new FastOutSlowInInterpolator());
            mAnimator.setStartDelay(mSkipAnimations ? 0 : 100);
            mAnimator.playTogether(instructionAnimator, trashAnimator,
                    brightnessAnimator, toastAnimator);
            mAnimator.start();
        }
    };

    private int getVisibilityForAnimation(View view, boolean show) {
        if (show || view.getVisibility() != View.GONE) {
            return View.VISIBLE;
        }
        return View.INVISIBLE;
    }

    private void animateToState() {
        mSkipAnimations = false;
        post(mAnimateRunnable);
    }

    private void goToState() {
        mSkipAnimations = true;
        post(mAnimateRunnable);
    }

    private Animator animateView(View v, boolean show) {
        return ObjectAnimator.ofFloat(v, "translationY", show ? 0 : -getMeasuredHeight());
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

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mListening) {
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }

    }

    class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.QS_SHOW_BRIGHTNESS_SLIDER), false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int currentUserId = ActivityManager.getCurrentUser();
            boolean showSlider = CMSettings.System.getIntForUser(resolver,
                    CMSettings.System.QS_SHOW_BRIGHTNESS_SLIDER, 1, currentUserId) == 1;
            if (showSlider != mHasBrightnessSliderToDisplay) {
                if (mAnimator != null) {
                    mAnimator.cancel(); // cancel everything we're animating
                    mAnimator = null;
                }
                mHasBrightnessSliderToDisplay = showSlider;
                if (mBrightnessView != null) {
                    mBrightnessView.setVisibility(showSlider ? View.VISIBLE : View.GONE);

                    // as per showBrightnessSlider() in QSPanel.java, we look it up on-the-go
                    ToggleSlider brightnessSlider = (ToggleSlider) findViewById(R.id.brightness_slider);
                    if (brightnessSlider != null) {
                        brightnessSlider.setVisibility(showSlider ? View.VISIBLE : View.GONE);
                    }

                }
                getParent().requestLayout();
                animateToState();
            }
        }
    }
}
