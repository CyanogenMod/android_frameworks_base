/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.widget;
import android.provider.Settings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import com.android.internal.R;

/**
 * A special widget containing two (or three) Rings.  Moving either ring beyond
 * the threshold will cause the registered OnRingTriggerListener.onTrigger() to be called with
 * whichRing being {@link OnRingTriggerListener#LEFT_RING}, {@link OnRingTriggerListener#RIGHT_RING},
 * or {@link OnRingTriggerListener#MIDDLE_RING}.
 * Equivalently, selecting a ring will result in a call to
 * {@link OnRingTriggerListener#onGrabbedStateChange(View, int)} with one of these three states. Releasing
 * the ring will result in whichRing being {@link OnRingTriggerListener#NO_RING}.
 */
public class RingSelector extends ViewGroup {
    private static final String LOG_TAG = "RingSelector";
    private static final boolean DBG = false;
    private static final int HORIZONTAL = 0; // as defined in attrs.xml
    private static final int VERTICAL = 1;

    private final float mThresholdRadiusDIP;
    private final float mThresholdRadius;
    private final float mThresholdRadiusSq;

    private static final int ANIM_CENTER_FADE_TIME = 250; //fade time for center ring (ms)
    private static final int ANIM_DURATION = 250; // Time for most animations (in ms)
    private static final int ANIM_TARGET_TIME = 500; // Time to show targets (in ms)

    private OnRingTriggerListener mOnRingTriggerListener;
    private int mGrabbedState = OnRingTriggerListener.NO_RING;
    private boolean mTriggered = false;
    private Vibrator mVibrator;

    private float mDensity; // used to scale dimensions for bitmaps.
    private float mDensityScaleFactor=1;

    private final int mBottomOffsetDIP;
    private final int mCenterOffsetDIP;
    private final int mSecRingBottomOffsetDIP;
    private final int mSecRingCenterOffsetDIP;
    private final int mBottomOffset;
    private final int mCenterOffset;
    private final int mSecRingBottomOffset;
    private final int mSecRingCenterOffset;

    private boolean mUseMiddleRing = true;
    private boolean mMiddlePrimary = false;
    private boolean mUseRingMinimal = false;

    /**
     * Either {@link #HORIZONTAL} or {@link #VERTICAL}.
     */
    private int mOrientation;
    private int mSelectedRingId;
    private Ring mLeftRing;
    private Ring mRightRing;
    private Ring mMiddleRing;

    private Ring mCurrentRing;
    private Ring mOtherRing1;
    private Ring mOtherRing2;
    private boolean mTracking;
    private boolean mAnimating;
    private boolean mPrevTriggered;

    private SecRing[] mSecRings;

    /**
     * Listener used to reset the view when the current animation completes.
     */
    private final AnimationListener mAnimationDoneListener = new AnimationListener() {
        public void onAnimationStart(Animation animation) {

        }

        public void onAnimationRepeat(Animation animation) {

        }

        public void onAnimationEnd(Animation animation) {
            onAnimationDone();
        }
    };

    /**
     * Interface definition for a callback to be invoked when a ring is triggered
     * by moving it beyond a threshold.
     */
    public interface OnRingTriggerListener {
        /**
         * The interface was triggered because the user let go of the ring without reaching the
         * threshold.
         */
        public static final int NO_RING = 0;

        /**
         * The interface was triggered because the user grabbed the left ring and moved it past
         * the threshold.
         */
        public static final int LEFT_RING = 1;

        /**
         * The interface was triggered because the user grabbed the right ring and moved it past
         * the threshold.
         */
        public static final int RIGHT_RING = 2;

        /**
         * The interface was triggered because the user grabbed the middle ring and moved it to
         * a custom secondary ring.
         */
        public static final int MIDDLE_RING = 3;

        /**
         * Called when the user moves a ring beyond the threshold.
         *
         * @param v The view that was triggered.
         * @param whichRing  Which ring the user grabbed,
         *        either {@link #LEFT_RING}, {@link #RIGHT_RING}, or {@link MIDDLE_RING}.
         * @param whichSecRing Which secondary ring (0-3) the user triggered with the middle ring.
         *        -1 if ring wasn't the middle one.
         */
        void onRingTrigger(View v, int whichRing, int whichSecRing);

        /**
         * Called when the "grabbed state" changes (i.e. when the user either grabs or releases
         * one of the rings.)
         *
         * @param v the view that was triggered
         * @param grabbedState the new state: {@link #NO_RING}, {@link #LEFT_RING},
         * {@link #RIGHT_RING}, or {@link MIDDLE_RING}.
         */
        void onGrabbedStateChange(View v, int grabbedState);
    }

    /**
     * Simple container class for all things pertinent to a ring.
     * A slider consists of 2 Views:
     *
     * {@link #ring} is the ring shown on the screen in the default state.
     * {@link #target} is the target the user must drag the ring away from to trigger the ring.
     *
     */
    private class Ring {
        /**
         * Ring alignment - determines where the ring should be drawn
         */
        public static final int ALIGN_LEFT = 0;
        public static final int ALIGN_RIGHT = 1;
        public static final int ALIGN_TOP = 2;
        public static final int ALIGN_BOTTOM = 3;
        public static final int ALIGN_CENTER = 4; //horizontal
        public static final int ALIGN_MIDDLE = 5; //vertical
        public static final int ALIGN_UNKNOWN = 6;

        /**
         * States for the view.
         */
        private static final int STATE_NORMAL = 0;
        private static final int STATE_PRESSED = 1;
        private static final int STATE_ACTIVE = 2;

        private boolean isHidden = false;

        private final ImageView ring;
        private final ImageView target;
        private int currentState = STATE_NORMAL;
        private int alignment = ALIGN_UNKNOWN;
        private int alignCenterX;
        private int alignCenterY;

        /**
         * Constructor
         *
         * @param parent the container view of this one
         * @param ringId drawable for the ring
         * @param targetId drawable for the target
         */
        Ring(ViewGroup parent, int ringId, int targetId) {
            // Create ring
            ring = new ImageView(parent.getContext());
            ring.setBackgroundResource(ringId);
            ring.setScaleType(ScaleType.CENTER);
            ring.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));

            // Create target
            target = new ImageView(parent.getContext());
            target.setImageResource(targetId);
            target.setScaleType(ScaleType.CENTER);
            target.setLayoutParams(
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            target.setVisibility(View.INVISIBLE);

            parent.addView(target); // this needs to be first - relies on painter's algorithm
            parent.addView(ring);
        }

        void setHiddenState(boolean hidden) {
            isHidden = hidden;
            if (isHidden) {
                ring.setVisibility(View.GONE);
            } else {
                reset(false);
            }
        }

        void setIcon(int iconId) {
            ring.setImageResource(iconId);
        }

        void setIcon(Bitmap icon) {
            ring.setImageBitmap(icon);
        }

        void setRingBackgroundResource(int ringId) {
            ring.setBackgroundResource(ringId);
        }

        void hide() {
            if (isHidden) return;
            if (ring.getVisibility() == View.INVISIBLE) return;

            if (alignment == ALIGN_CENTER || alignment == ALIGN_MIDDLE) {
                AlphaAnimation alphaAnim = new AlphaAnimation(1.0f, 0.0f);
                alphaAnim.setDuration(ANIM_CENTER_FADE_TIME);
                ring.startAnimation(alphaAnim);
                ring.setVisibility(View.INVISIBLE);
            }
            else {
                int centerX = (ring.getLeft() + ring.getRight()) / 2;
                int centerY = (ring.getTop() + ring.getBottom()) / 2;
                int targetX = alignCenterX;
                int targetY = alignCenterY;

                if (alignment == ALIGN_LEFT) {
                    targetX -= 2 * ring.getWidth() / 3;
                } else if (alignment == ALIGN_RIGHT) {
                    targetX += 2 * ring.getWidth() / 3;
                } else if (alignment == ALIGN_TOP) {
                    targetY -= 2 * ring.getHeight() / 3;
                } else if (alignment == ALIGN_BOTTOM) {
                    targetY += 2 * ring.getHeight() / 3;
                }

                int dx = targetX - centerX;
                int dy = targetY - centerY;

                ring.offsetLeftAndRight(dx);
                ring.offsetTopAndBottom(dy);

                Animation trans = new TranslateAnimation(-dx, 0, -dy, 0);
                trans.setDuration(ANIM_DURATION);
                trans.setFillAfter(true);
                ring.startAnimation(trans);
                ring.setVisibility(View.INVISIBLE);
                target.setVisibility(View.INVISIBLE);
            }
        }

        void show(boolean animate) {
            if (isHidden) return;

            int centerX = (ring.getLeft() + ring.getRight()) / 2;
            int centerY = (ring.getTop() + ring.getBottom()) / 2;
            int dx = alignCenterX - centerX;
            int dy = alignCenterY - centerY;

            ring.offsetLeftAndRight(dx);
            ring.offsetTopAndBottom(dy);

            if (ring.getVisibility() == View.VISIBLE) return;

            ring.setVisibility(View.VISIBLE);
            if (animate) {
                if (alignment == ALIGN_CENTER || alignment == ALIGN_MIDDLE) {
                    AlphaAnimation alphaAnim = new AlphaAnimation(0.0f, 1.0f);
                    alphaAnim.setDuration(ANIM_CENTER_FADE_TIME);
                    ring.startAnimation(alphaAnim);
                }
                else {
                    Animation trans = new TranslateAnimation(-dx, 0, -dy, 0);
                    trans.setFillAfter(true);
                    trans.setDuration(ANIM_DURATION);
                    ring.startAnimation(trans);
                }
            }
        }

        void setState(int state) {
            ring.setPressed(state == STATE_PRESSED);
            if (state == STATE_ACTIVE) {
                final int[] activeState = new int[] {com.android.internal.R.attr.state_active};
                if (ring.getBackground().isStateful()) {
                    ring.getBackground().setState(activeState);
                }
            }
            currentState = state;
        }

        void showTarget() {
            AlphaAnimation alphaAnim = new AlphaAnimation(0.0f, 1.0f);
            alphaAnim.setDuration(ANIM_TARGET_TIME);
            target.startAnimation(alphaAnim);
            target.setVisibility(View.VISIBLE);
        }

        void reset(boolean animate) {
            if (isHidden) return;

            setState(STATE_NORMAL);
            target.setVisibility(View.INVISIBLE);

            int centerX = (ring.getLeft() + ring.getRight()) / 2;
            int centerY = (ring.getTop() + ring.getBottom()) / 2;
            int dx = alignCenterX - centerX;
            int dy = alignCenterY - centerY;

            ring.offsetLeftAndRight(dx);
            ring.offsetTopAndBottom(dy);

            /*
             * setScaleX/Y were introducted in 3.0, so can't use them directly.
             * Instead, have a ScaleAnimation rescale the ring (with 0 duration)
             */
            ScaleAnimation scaleAnim = new ScaleAnimation(1.0f, 1.0f, 1.0f, 1.0f);
            scaleAnim.setDuration(0);
            scaleAnim.setFillAfter(true);
            ring.startAnimation(scaleAnim);

            ring.setVisibility(View.VISIBLE);
            if (animate) {
                TranslateAnimation trans = new TranslateAnimation(-dx, 0, -dy, 0);
                trans.setDuration(ANIM_DURATION);
                trans.setInterpolator(new OvershootInterpolator());
                trans.setFillAfter(false);
                ring.startAnimation(trans);
            } else {
                ring.clearAnimation();
                target.clearAnimation();
            }
        }

        void setTarget(int targetId) {
            target.setImageResource(targetId);
        }

        /**
         * Layout the given widgets within the parent.
         *
         * @param l the parent's left border
         * @param t the parent's top border
         * @param r the parent's right border
         * @param b the parent's bottom border
         * @param alignment which side to align the widget to
         */
        void layout(int l, int t, int r, int b, int alignment) {
            this.alignment = alignment;

            final int parentWidth = r - l;
            final int parentHeight = b - t;

            final Drawable ringBackground = ring.getBackground();
            final int ringWidth = ringBackground.getIntrinsicWidth();
            final int ringHeight = ringBackground.getIntrinsicHeight();
            final int hRingWidth = ringWidth / 2;
            final int hRingHeight = ringHeight / 2;

            final Drawable targetDrawable = target.getDrawable();
            final int targetWidth = targetDrawable.getIntrinsicWidth();
            final int targetHeight = targetDrawable.getIntrinsicHeight();
            final int hTargetWidth = targetWidth / 2;
            final int hTargetHeight = targetHeight / 2;

            if (alignment == ALIGN_LEFT || alignment == ALIGN_RIGHT || alignment == ALIGN_CENTER) {
                // horizontal
                alignCenterX = alignment == ALIGN_CENTER ? parentWidth / 2 : ringWidth / 6;
                alignCenterY = parentHeight - mBottomOffset;

                if (alignment == ALIGN_RIGHT) {
                    alignCenterX = parentWidth - alignCenterX;
                }
                if (alignment == ALIGN_CENTER) {
                    alignCenterY += mCenterOffset;
                }
            } else {
                // vertical
                alignCenterX = parentWidth - mBottomOffset;
                alignCenterY = alignment == ALIGN_MIDDLE ? parentHeight / 2 : ringHeight / 6;

                if (alignment == ALIGN_BOTTOM) {
                    alignCenterY = parentHeight - alignCenterY;
                }
                if (alignment == ALIGN_MIDDLE) {
                    alignCenterX += mCenterOffset;
                }
            }

            ring.layout(alignCenterX - hRingWidth, alignCenterY - hRingHeight,
                    alignCenterX + hRingWidth, alignCenterY + hRingHeight);
            target.layout(alignCenterX - hTargetWidth, alignCenterY - hTargetHeight,
                    alignCenterX + hTargetWidth, alignCenterY + hTargetHeight);
        }

        public void updateDrawableStates() {
            setState(currentState);
        }

        /**
         * Ensure all the dependent widgets are measured.
         */
        public void measure() {
            ring.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }

        /**
         * Get the measured ring width. Must be called after {@link Ring#measure()}.
         * @return width of the ring
         */
        public int getRingWidth() {
            return ring.getMeasuredWidth();
        }

        /**
         * Get the measured ring height. Must be called after {@link Ring#measure()}.
         * @return height of the ring.
         */
        public int getRingHeight() {
            return ring.getMeasuredHeight();
        }

        /**
         * Start animating the ring.
         *
         * @param anim1
         */
        public void startAnimation(Animation anim1) {
            ring.startAnimation(anim1);
        }

        public void hideTarget() {
            target.clearAnimation();
            target.setVisibility(View.INVISIBLE);
        }

        public boolean contains(int x, int y) {
            final Drawable ringBackground = ring.getBackground();
            final int ringWidth = ringBackground.getIntrinsicWidth();
            final int ringHeight = ringBackground.getIntrinsicHeight();
            final int hRingWidth = ringWidth / 2;
            final int hRingHeight = ringHeight / 2;
            final int r = (hRingWidth + hRingHeight) / 2;

            final int centerX = ring.getLeft() + (ring.getWidth() / 2);
            final int centerY = ring.getTop() + (ring.getHeight() / 2);

            final int dx = x - centerX;
            final int dy = y - centerY;

            return (dx * dx + dy * dy < r * r);
        }
    }

    private class SecRing {

        private final ImageView ring;

        private int alignCenterX;
        private int alignCenterY;

        private boolean isHidden = false;
        private boolean isActive = false;

        public SecRing(ViewGroup parent, int ringId) {
            ring = new ImageView(parent.getContext());
            ring.setBackgroundResource(ringId);
            ring.setScaleType(ScaleType.CENTER);
            ring.setVisibility(View.INVISIBLE);
            ring.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));

            parent.addView(ring);
        }

        void setHiddenState(boolean hidden) {
            isHidden = hidden;
            if (hidden) hide();
        }

        boolean isHidden() {
            return isHidden;
        }

        void hide() {
            if (ring.getVisibility() == View.INVISIBLE) return;
            AlphaAnimation alphaAnim = new AlphaAnimation(1.0f, 0.0f);
            alphaAnim.setDuration(ANIM_CENTER_FADE_TIME);
            alphaAnim.setInterpolator(new DecelerateInterpolator());
            ring.startAnimation(alphaAnim);
            ring.setVisibility(View.INVISIBLE);
        }

        void show() {
            if (ring.getVisibility() == View.VISIBLE || isHidden) return;
            AlphaAnimation alphaAnim = new AlphaAnimation(0.0f, 1.0f);
            alphaAnim.setDuration(ANIM_CENTER_FADE_TIME);
            ring.startAnimation(alphaAnim);
            ring.setVisibility(View.VISIBLE);
        }

        void activate() {
            if (isActive) return;
            isActive = true;

            ScaleAnimation scaleAnim = new ScaleAnimation(1.0f, 1.5f, 1.0f, 1.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setInterpolator(new DecelerateInterpolator());
            scaleAnim.setDuration(ANIM_CENTER_FADE_TIME);
            scaleAnim.setFillAfter(true);
            ring.startAnimation(scaleAnim);
        }

        void deactivate() {
            if (!isActive) return;
            isActive = false;

            ScaleAnimation scaleAnim = new ScaleAnimation(1.5f, 1.0f, 1.5f, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setInterpolator(new DecelerateInterpolator());
            scaleAnim.setDuration(ANIM_CENTER_FADE_TIME);
            scaleAnim.setFillAfter(true);
            ring.startAnimation(scaleAnim);
        }

        void reset(boolean animate) {
            if (animate) {
                hide();
            }
            ring.setVisibility(View.INVISIBLE);
        }

        void setIcon(int iconId) {
            ring.setImageResource(iconId);
        }

        void setIcon(Bitmap icon) {
            ring.setImageBitmap(icon);
        }

        void setRingBackgroundResource(int ringId) {
            ring.setBackgroundResource(ringId);
        }

        /**
         * Layout the given widgets within the parent.
         *
         * @param l the parent's left border
         * @param t the parent's top border
         * @param r the parent's right border
         * @param b the parent's bottom border
         * @param orientation orientation of screen (HORIZONTAL or VERTICAL)
         * @param ringNum ring number (0-[totalRings-1])
         * @param totalRings the total number of secondary rings (1-4)
         */
        void layout(int l, int t, int r, int b, int orientation, int ringNum, int totalRings) {
            final int parentWidth = r - l;
            final int parentHeight = b - t;

            final Drawable ringBackground = ring.getBackground();
            final int ringWidth = ringBackground.getIntrinsicWidth();
            final int ringHeight = ringBackground.getIntrinsicHeight();
            final int hRingWidth = ringWidth / 2;
            final int hRingHeight = ringHeight / 2;

            //perhaps make this formula-based, but i can't think of a good one for now
            final boolean shift = totalRings < 3 ||
                    (totalRings == 3 && ringNum == 1) ||
                    (totalRings == 4 && (ringNum == 1 || ringNum == 2));
            //Log.d("RingSelector::SecRing::layout", "ring " + ringNum + " out of " + totalRings + "; shifting?=" + shift);

            if (orientation == HORIZONTAL) {
                int spacing = parentWidth / totalRings;

                alignCenterX = spacing / 2 + ringNum * spacing; //align on evenly-spaced verticals
                alignCenterY = parentHeight - mSecRingBottomOffset - mBottomOffset;

                if (shift) {
                    alignCenterY -= mSecRingCenterOffset;
                }
            } else if (orientation == VERTICAL) {
                int spacing = parentHeight / totalRings;
                alignCenterX = parentWidth - mSecRingBottomOffset - mBottomOffset;
                alignCenterY = parentHeight - (spacing / 2 + ringNum * spacing); //align on evenly-spaced horizontals

                if (shift) {
                    alignCenterX -= mSecRingCenterOffset;
                }
            }

            ring.layout(alignCenterX - hRingWidth, alignCenterY - hRingHeight,
                    alignCenterX + hRingWidth, alignCenterY + hRingHeight);
        }

        public boolean contains(int x, int y) {
            final Drawable ringBackground = ring.getBackground();
            final int ringWidth = ringBackground.getIntrinsicWidth();
            final int ringHeight = ringBackground.getIntrinsicHeight();
            final int hRingWidth = ringWidth / 2;
            final int hRingHeight = ringHeight / 2;
            final int r = (hRingWidth + hRingHeight) / 2;

            final int centerX = ring.getLeft() + (ring.getWidth() / 2);
            final int centerY = ring.getTop() + (ring.getHeight() / 2);

            final int dx = x - centerX;
            final int dy = y - centerY;

            return (dx * dx + dy * dy < r * r);
        }
    }

    public RingSelector(Context context) {
        this(context, null);
    }

    /**
     * Constructor used when this widget is created from a layout file.
     */
    public RingSelector(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlidingTab);
        mOrientation = a.getInt(R.styleable.SlidingTab_orientation, HORIZONTAL);
        a.recycle();

        Resources r = getResources();
        mDensity = r.getDisplayMetrics().density;
        if (DBG) log("- Density: " + mDensity);

        int densityDpi = r.getDisplayMetrics().densityDpi;

        /* --copied from RotarySelector.java ;)
         *
         * this hack assumes people change build.prop for increasing
         * the virtual size of their screen by decreasing dpi in
         * build.prop file. this is often done especially for hd
         * phones. keep in mind changing build.prop and density
         * isnt officially supported, but this should do for most cases
         */
        if (densityDpi < 240 && densityDpi > 180)
            mDensityScaleFactor = (float) (240.0 / densityDpi);
        if (densityDpi < 160 && densityDpi > 120)
            mDensityScaleFactor = (float) (160.0 / densityDpi);

        mThresholdRadiusDIP = context.getResources().getInteger(R.integer.config_ringThresholdDIP);
        mThresholdRadius = mDensity * mDensityScaleFactor * mThresholdRadiusDIP;
        mThresholdRadiusSq = mThresholdRadius * mThresholdRadius;

        mBottomOffsetDIP = context.getResources().getInteger(R.integer.config_ringBaselineBottomDIP);
        mCenterOffsetDIP = context.getResources().getInteger(R.integer.config_ringCenterOffsetDIP);
        mSecRingBottomOffsetDIP = context.getResources().getInteger(R.integer.config_ringSecBaselineOffsetDIP);
        mSecRingCenterOffsetDIP = context.getResources().getInteger(R.integer.config_ringSecCenterOffsetDIP);
        mBottomOffset = (int) (mDensity * mDensityScaleFactor * mBottomOffsetDIP);
        mCenterOffset = (int) (mDensity * mDensityScaleFactor * mCenterOffsetDIP);
        mSecRingBottomOffset = (int) (mDensity * mDensityScaleFactor * mSecRingBottomOffsetDIP);
        mSecRingCenterOffset = (int) (mDensity * mDensityScaleFactor * mSecRingCenterOffsetDIP);

        mSecRings = new SecRing[] {
                new SecRing(this, R.drawable.jog_ring_secback_normal),
                new SecRing(this, R.drawable.jog_ring_secback_normal),
                new SecRing(this, R.drawable.jog_ring_secback_normal),
                new SecRing(this, R.drawable.jog_ring_secback_normal)
        };

        mLeftRing = new Ring(this,
                R.drawable.jog_ring_ring_gray,
                R.drawable.jog_tab_target_gray);
        mRightRing = new Ring(this,
                R.drawable.jog_ring_ring_gray,
                R.drawable.jog_tab_target_gray);
        mMiddleRing = new Ring(this,
                R.drawable.jog_ring_ring_gray,
                R.drawable.jog_tab_target_gray);

        mVibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize =  MeasureSpec.getSize(widthMeasureSpec);

        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);

        if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            Log.e("RingSelector", "RingSelector cannot have UNSPECIFIED MeasureSpec"
                    +"(wspec=" + widthSpecMode + ", hspec=" + heightSpecMode + ")",
                    new RuntimeException(LOG_TAG + "stack:"));
        }

        mLeftRing.measure();
        mRightRing.measure();
        mMiddleRing.measure();
        final int leftRingWidth = mLeftRing.getRingWidth();
        final int rightRingWidth = mRightRing.getRingWidth();
        final int middleRingWidth = mMiddleRing.getRingWidth();
        final int leftRingHeight = mLeftRing.getRingHeight();
        final int rightRingHeight = mRightRing.getRingHeight();
        final int middleRingHeight = mMiddleRing.getRingHeight();
        final int width;
        final int height;

        if (isHorizontal()) {
            width = Math.max(widthSpecSize, leftRingWidth * 2 / 3 + rightRingWidth * 2 / 3 + middleRingWidth);
            height = Math.max(Math.max(leftRingHeight, rightRingHeight), Math.max(heightSpecSize, middleRingHeight + mCenterOffset));
        } else {
            width = Math.max(Math.max(leftRingWidth, rightRingWidth), Math.max(widthSpecSize, middleRingWidth + mCenterOffset));
            height = Math.max(heightSpecSize, leftRingHeight * 2 / 3 + rightRingHeight * 2 / 3 + middleRingHeight);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        if (mAnimating) {
            return false;
        }

        boolean leftHit = !mUseRingMinimal ? mLeftRing.contains((int) x, (int) y) : false;
        boolean rightHit = !mUseRingMinimal ? mRightRing.contains((int) x, (int) y) : false;
        boolean middleHit = mUseMiddleRing ? mMiddleRing.contains((int) x, (int) y) : false;

        if (!mTracking && !(leftHit || rightHit || middleHit)) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mTracking = true;
                mTriggered = false;
                vibrate();
                if (leftHit) {
                    mCurrentRing = mLeftRing;
                    mOtherRing1 = mRightRing;
                    mOtherRing2 = mMiddleRing;
                    if (mMiddlePrimary) {
                        for (SecRing secRing : mSecRings) {
                            secRing.show();
                        }
                    }
                    setGrabbedState(OnRingTriggerListener.LEFT_RING);
                } else if (rightHit) {
                    mCurrentRing = mRightRing;
                    mOtherRing1 = mLeftRing;
                    mOtherRing2 = mMiddleRing;
                    setGrabbedState(OnRingTriggerListener.RIGHT_RING);
                } else {
                    mCurrentRing = mMiddleRing;
                    mOtherRing1 = mLeftRing;
                    mOtherRing2 = mRightRing;
                    if (!mMiddlePrimary) {
                        for (SecRing secRing : mSecRings) {
                            secRing.show();
                        }
                    }
                    setGrabbedState(OnRingTriggerListener.MIDDLE_RING);
                }
                mCurrentRing.setState(Ring.STATE_PRESSED);
                mCurrentRing.showTarget();
                mOtherRing1.hide();
                mOtherRing2.hide();

                setKeepScreenOn(true);

                break;
            }
        }

        return true;
    }

    /**
     * Reset the rings to their original state and stop any existing animation.
     * Animate them back into place if animate is true.
     *
     * @param animate
     */
    public void reset(boolean animate) {
        mLeftRing.reset(animate);
        mRightRing.reset(animate);
        mMiddleRing.reset(animate);
        if (!animate) {
            mAnimating = false;
        }
    }

    @Override
    public void setVisibility(int visibility) {
        // Clear animations so sliders don't continue to animate when we show the widget again.
        if (visibility != getVisibility()) {
           reset(false);
           for (SecRing secRing : mSecRings) {
               secRing.reset(true);
           }
           super.setVisibility(visibility);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTracking) {
            final int action = event.getAction();
            final float x = event.getX();
            final float y = event.getY();

            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    moveRing(x, y);
                    if (!mMiddlePrimary && mUseMiddleRing && mCurrentRing == mMiddleRing) {
                        for (int q = 0; q < 4; q++) {
                            if (!mSecRings[q].isHidden() && mSecRings[q].contains((int) x, (int) y)) {
                                mSecRings[q].activate();
                            } else {
                                mSecRings[q].deactivate();
                            }
                        }
                    } else if (mMiddlePrimary && mUseMiddleRing && mCurrentRing == mLeftRing) {
                        for (int q = 0; q < 4; q++) {
                            if (!mSecRings[q].isHidden() && mSecRings[q].contains((int) x, (int) y)) {
                                mSecRings[q].activate();
                            } else {
                                mSecRings[q].deactivate();
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mSelectedRingId = -1;
                    boolean thresholdReached = false;

                    if (mMiddlePrimary) {
                        if (mCurrentRing != mLeftRing) {
                            int dx = (int) x - mCurrentRing.alignCenterX;
                            int dy = (int) y - mCurrentRing.alignCenterY;
                            thresholdReached = (dx * dx + dy * dy) > mThresholdRadiusSq;
                        } else if (mUseMiddleRing) {
                            for (int q = 0; q < 4; q++) {
                                if (!mSecRings[q].isHidden() && mSecRings[q].contains((int) x, (int) y)) {
                                    thresholdReached = true;
                                    mSelectedRingId = q;
                                    break;
                                }
                            }
                        }
                    } else if (mCurrentRing != mMiddleRing) {
                        int dx = (int) x - mCurrentRing.alignCenterX;
                        int dy = (int) y - mCurrentRing.alignCenterY;
                        thresholdReached = (dx * dx + dy * dy) > mThresholdRadiusSq;
                    }
                    else if (mUseMiddleRing) {
                        for (int q = 0; q < 4; q++) {
                            if (!mSecRings[q].isHidden() && mSecRings[q].contains((int) x, (int) y)) {
                                thresholdReached = true;
                                mSelectedRingId = q;
                                break;
                            }
                        }
                    }

                    if (!mTriggered && thresholdReached) {
                        mTriggered = true;
                        mTracking = false;
                        mCurrentRing.setState(Ring.STATE_ACTIVE);
                        startAnimating();
                        setGrabbedState(OnRingTriggerListener.NO_RING);
                        setKeepScreenOn(false);
                        break;
                    }
                    //fall through -- released ring without triggerring
                case MotionEvent.ACTION_CANCEL:
                    mTracking = false;
                    mTriggered = false;
                    mOtherRing1.show(true);
                    mOtherRing2.show(true);
                    mCurrentRing.reset(true);
                    mCurrentRing.hideTarget();
                    mCurrentRing = null;
                    mOtherRing1 = null;
                    mOtherRing2 = null;

                    for (SecRing secRing : mSecRings) {
                        secRing.hide();
                    }

                    setGrabbedState(OnRingTriggerListener.NO_RING);
                    setKeepScreenOn(false);
                    break;
            }
        }

        return mTracking || super.onTouchEvent(event);
    }

    void startAnimating() {
        mAnimating = true;
        final Animation trans1, trans2;
        final AnimationSet transSet;
        final Ring ring = mCurrentRing;

        trans1 = new ScaleAnimation(1.0f, 7.5f, 1.0f, 7.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        trans1.setDuration(ANIM_DURATION);
        trans1.setInterpolator(new AccelerateInterpolator());

        trans2 = new AlphaAnimation(1.0f, 0.2f);
        trans2.setDuration(ANIM_DURATION);
        trans2.setInterpolator(new AccelerateInterpolator());

        transSet = new AnimationSet(false);
        transSet.setDuration(ANIM_DURATION);
        transSet.setAnimationListener(mAnimationDoneListener);
        transSet.addAnimation(trans1);
        transSet.addAnimation(trans2);
        transSet.setFillAfter(true);

        ring.hideTarget();
        ring.startAnimation(transSet);
    }

    private void onAnimationDone() {
        boolean isLeft = mCurrentRing == mLeftRing;
        boolean isRight = mCurrentRing == mRightRing;
        dispatchTriggerEvent(isLeft ?
                OnRingTriggerListener.LEFT_RING : (isRight ? OnRingTriggerListener.RIGHT_RING :
                    OnRingTriggerListener.MIDDLE_RING), mSelectedRingId);
        if (isRight) {
            reset(false);
        } else {
            super.setVisibility(View.INVISIBLE);
            if ((mMiddlePrimary && isLeft) || (!mMiddlePrimary && !isRight && !isLeft)) {
                if (mPrevTriggered) {
                    mCurrentRing.setRingBackgroundResource(R.drawable.jog_ring_ring_green);
                }
                mSecRings[mSelectedRingId].deactivate();
            }
        }
        mAnimating = false;
    }

    private boolean isHorizontal() {
        return mOrientation == HORIZONTAL;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!changed) return;

        // Realign the rings
        mLeftRing.layout(l, t, r, b, isHorizontal() ? Ring.ALIGN_LEFT : Ring.ALIGN_BOTTOM);
        mRightRing.layout(l, t, r, b, isHorizontal() ? Ring.ALIGN_RIGHT : Ring.ALIGN_TOP);
        mMiddleRing.layout(l, t, r, b, isHorizontal() ? Ring.ALIGN_CENTER : Ring.ALIGN_MIDDLE);

        int nSecRings = 0;
        for (SecRing secRing : mSecRings) {
            if (!secRing.isHidden()) nSecRings++;
        }

        if (nSecRings != 0) {
            for (int q = 0; q < 4; q++) {
                mSecRings[q].layout(l, t, r, b, isHorizontal() ? HORIZONTAL : VERTICAL, q, nSecRings);
            }
        }
    }

    private void moveRing(float x, float y) {
        final View ring = mCurrentRing.ring;
        int deltaX = (int) x - ring.getLeft() - (ring.getWidth() / 2);
        int deltaY = (int) y - ring.getTop() - (ring.getHeight() / 2);
        ring.offsetLeftAndRight(deltaX);
        ring.offsetTopAndBottom(deltaY);
        setHoverBackLight(x,y);
        invalidate();
    }

    private void setHoverBackLight(float x, float y) {
        if (mMiddlePrimary && mCurrentRing != mLeftRing) {
            return;
        } else if (!mMiddlePrimary && mCurrentRing != mMiddleRing) {
            return;
        }
        boolean ringsTouched = false;
        for (SecRing q : mSecRings) {
            if (!q.isHidden() && q.contains((int) x,(int) y)) {
                ringsTouched = true;
                break;
            }
        }
        if (ringsTouched && !mPrevTriggered) {
            mCurrentRing.setRingBackgroundResource(R.drawable.jog_ring_ring_pressed_red);
            mPrevTriggered = true;
        } else if (!ringsTouched && mPrevTriggered) {
            mCurrentRing.setRingBackgroundResource(R.drawable.jog_ring_ring_green);
            mPrevTriggered = false;
        }
    }

    /**
     * Sets the left ring icon to a given resource.
     *
     * The resource should refer to a Drawable object, or use 0 to remove
     * the icon.
     *
     * @param iconId the resource ID of the icon drawable
     * @param targetId the resource of the target drawable
     * @param ringId the resource of the ring drawable
     */
    public void setLeftRingResources(int iconId, int targetId, int ringId) {
        mLeftRing.setIcon(iconId);
        mLeftRing.setTarget(targetId);
        mLeftRing.setRingBackgroundResource(ringId);
        mLeftRing.updateDrawableStates();
    }

    public void setLeftRingResources(Bitmap icon, int targetId, int ringId) {
        mLeftRing.setIcon(icon);
        mLeftRing.setTarget(targetId);
        mLeftRing.setRingBackgroundResource(ringId);
        mLeftRing.updateDrawableStates();
    }

    /**
     * Sets the right ring icon to a given resource.
     *
     * The resource should refer to a Drawable object, or use 0 to remove
     * the icon.
     *
     * @param iconId the resource ID of the icon drawable
     * @param targetId the resource of the target drawable
     * @param ringId the resource of the ring drawable
     */
    public void setRightRingResources(int iconId, int targetId, int ringId) {
        mRightRing.setIcon(iconId);
        mRightRing.setTarget(targetId);
        mRightRing.setRingBackgroundResource(ringId);
        mRightRing.updateDrawableStates();
    }

    public void setRightRingResources(Bitmap icon, int targetId, int ringId) {
        mRightRing.setIcon(icon);
        mRightRing.setTarget(targetId);
        mRightRing.setRingBackgroundResource(ringId);
        mRightRing.updateDrawableStates();
    }

    /**
     * Sets the right ring icon to a given resource.
     *
     * The resource should refer to a Drawable object, or use 0 to remove
     * the icon.
     *
     * @param iconId the resource ID of the icon drawable
     * @param targetId the resource of the target drawable
     * @param ringId the resource of the ring drawable
     */
    public void setMiddleRingResources(int iconId, int targetId, int ringId) {
        mMiddleRing.setIcon(iconId);
        mMiddleRing.setTarget(targetId);
        mMiddleRing.setRingBackgroundResource(ringId);
        mMiddleRing.updateDrawableStates();
    }

    public void setMiddleRingResources(Bitmap icon, int targetId, int ringId) {
        mMiddleRing.setIcon(icon);
        mMiddleRing.setTarget(targetId);
        mMiddleRing.setRingBackgroundResource(ringId);
        mMiddleRing.updateDrawableStates();
    }

    /**
     * Sets a certain secondary ring icon to a given resource.
     *
     * The resource should refer to a Drawable object, or use 0 to remove
     * the icon.
     *
     * @param ringNum which secondary ring to change (0-3)
     * @param iconId the resource ID of the icon drawable
     * @param ringId the resource of the ring drawable
     */
    public void setSecRingResources(int ringNum, int iconId, int ringId) {
        if (ringNum < 0 || ringNum > 3) return;
        mSecRings[ringNum].setIcon(iconId);
        mSecRings[ringNum].setRingBackgroundResource(ringId);
    }

    public void setSecRingResources(int ringNum, Bitmap icon, int ringId) {
        if (ringNum < 0 || ringNum > 3) return;
        mSecRings[ringNum].setIcon(icon);
        mSecRings[ringNum].setRingBackgroundResource(ringId);
    }

    public void hideSecRing(int ringNum) {
        mSecRings[ringNum].setHiddenState(true);

        boolean allHidden = true;
        for (SecRing ring : mSecRings) {
            if (!ring.isHidden()) {
                allHidden = false;
                break;
            }
        }

        if (allHidden) {
            enableMiddleRing(false);
        }

        requestLayout();
    }

    public void showSecRing(int ringNum) {
        mSecRings[ringNum].setHiddenState(false);
        enableMiddleRing(true);
        requestLayout();
    }

    public void enableMiddleRing(boolean enable) {
        mUseMiddleRing = enable;
        mMiddleRing.setHiddenState(!enable);
    }

    public void enableMiddlePrimary(boolean enable) {
        mMiddlePrimary = enable;
        enableMiddleRing(enable);
    }

    public void enableRingMinimal(boolean enable) {
        mUseRingMinimal = enable;
        enableMiddlePrimary(enable);
        mRightRing.setHiddenState(enable);
        mLeftRing.setHiddenState(enable);
    }

    /**
     * Triggers haptic feedback.
     */
    private synchronized void vibrate() {
        ContentResolver cr = mContext.getContentResolver();
        final boolean hapticsEnabled = Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 1;
        if (hapticsEnabled) {
            long[] hapFeedback = Settings.System.getLongArray(cr, Settings.System.HAPTIC_DOWN_ARRAY, new long[] { 0 });
            mVibrator.vibrate(hapFeedback, -1);
        }
    }

    /**
     * Registers a callback to be invoked when the user triggers an event.
     *
     * @param listener the OnDialTriggerListener to attach to this view
     */
    public void setOnRingTriggerListener(OnRingTriggerListener listener) {
        mOnRingTriggerListener = listener;
    }

    /**
     * Dispatches a trigger event to listener. Ignored if a listener is not set.
     * @param whichRing the handle that triggered the event.
     */
    private void dispatchTriggerEvent(int whichRing, int whichSecRing) {
        vibrate();
        if (mOnRingTriggerListener != null) {
            mOnRingTriggerListener.onRingTrigger(this, whichRing, whichSecRing);
        }
    }

    /**
     * Sets the current grabbed state, and dispatches a grabbed state change
     * event to our listener.
     */
    private void setGrabbedState(int newState) {
        if (newState != mGrabbedState) {
            mGrabbedState = newState;
            if (mOnRingTriggerListener != null) {
                mOnRingTriggerListener.onGrabbedStateChange(this, mGrabbedState);
            }
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
