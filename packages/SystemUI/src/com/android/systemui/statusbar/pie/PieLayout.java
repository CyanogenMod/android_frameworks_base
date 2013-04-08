/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.pie;

import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.android.systemui.statusbar.policy.PieController.Position;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the pie layout container.
 * <p>
 * This class is responsible for displaying the content of a pie control. And
 * processing the input events from the user.<br>
 * (It handles the events for the snap points, too.)
 */
public class PieLayout extends FrameLayout implements View.OnTouchListener {
    public static final String TAG = "PieLayout";
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_INPUT = false;

    /* DEBUG */
    private long mActivateStartDebug = 0;

    private static final int TIME_FADEIN = 300;
    private static final int TIME_FADEIN_DELAY = 400;

    private static final int COLOR_BACKGROUND = 0xee000000;

    private Paint mBackgroundPaint = new Paint();
    private float mBackgroundFraction;
    private int mBackgroundTargetAlpha;
    private Paint mSnapPaint = new Paint();
    private Paint mSnapActivePaint = new Paint();

    private float mSnapRadius;
    private float mSnapRadiusSqr;
    private float mSnapThreshold;
    private float mSnapThresholdSqr;

    private float mPieScale = 1.0f;
    private int mPadding;

    private boolean mActive = false;
    private int mPointerId;
    private Point mCenter = new Point(0, 0);
    private Position mPosition = Position.BOTTOM;
    private Position mLayoutDoneForPosition;

    private Handler mHandler;
    private Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            updateActiveItem(mActiveItem, true);
        }
    };

    private ValueAnimator mBackgroundAnimator
            = ValueAnimator.ofFloat(0.0f, 1.0f).setDuration(TIME_FADEIN);
    private List<ValueAnimator.AnimatorUpdateListener> mAnimationListenerCache
            = new ArrayList<ValueAnimator.AnimatorUpdateListener>();

    private ValueAnimator.AnimatorUpdateListener mUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (DEBUG) {
                Slog.d(TAG, "Animation update "
                        + animation.getAnimatedFraction() + " on: "
                        + Thread.currentThread().getName());
            }

            mBackgroundFraction = animation.getAnimatedFraction();

            // propagate the animation event to all listeners
            for (ValueAnimator.AnimatorUpdateListener listener : mAnimationListenerCache) {
                listener.onAnimationUpdate(animation);
            }

            // animation updates occur on the main thread. it is save to call invalidate here.
            PieLayout.this.invalidate();
        }

    };

    /**
     * A {@code PieDrawable} is everything that can get displayed on the pie control.
     * <p>
     * This defines the basic geometry of a pie thing and provides the
     * interface to trigger positioning and draw preparations
     * ({@link #prepare(Position, float)}), drawing
     * ({@link #draw(Canvas, Position)}) as well as user interaction
     * ({@link #interact(float, int)}).
     */
    public abstract static class PieDrawable {
        protected float mStart;
        protected float mSweep;
        protected int mInner;
        protected int mOuter;

        abstract public void prepare(Position position, float scale);

        abstract public void draw(Canvas canvas, Position position);

        abstract public PieItem interact(float alpha, int radius);

        public void setGeometry(float start, float sweep, int inner, int outer) {
            mStart = start;
            mSweep = sweep;
            mInner = inner;
            mOuter = outer;
        }

        // Display on all positions
        public final static int DISPLAY_ALL = Position.LEFT.FLAG
                | Position.BOTTOM.FLAG
                | Position.RIGHT.FLAG
                | Position.TOP.FLAG;
        // Display on all except the TOP position
        public final static int DISPLAY_NOT_AT_TOP = Position.LEFT.FLAG
                | Position.BOTTOM.FLAG
                | Position.RIGHT.FLAG;
        // The PieDrawable is visible, note that slice visibility overrides item visibility
        public final static int VISIBLE = 0x10;

        public int flags;
    };

    /**
     * A slice can contain drawable content, or can contain {@link PieItem}s which are the
     * actual end point for user interaction.
     */
    public abstract static class PieSlice extends PieDrawable {
        /**
         * This is the padding between items within a slice.
         */
        public final static float GAP = 3.0f;

        /**
         * The slice will be considerer as important - {@link PieLayout} will try to keep
         * these slices on screen, when placing the pie control.
         * @see PieDrawable#flags
         */
        public final static int IMPORTANT = 0x80;

        public float estimateWidth() {
            return (float) (Math.abs(Math.cos(Math.toRadians(mStart + mSweep))
                    - Math.cos(Math.toRadians(mStart))) * mOuter);
        }

    };

    private List<PieSlice> mSlices = new ArrayList<PieSlice>();
    private List<PieDrawable> mDrawableCache = new ArrayList<PieDrawable>();
    private PieItem mActiveItem;
    private boolean mLongPressed = false;

    private class SnapPoint {
        private final int mX;
        private final int mY;
        private float mActivity;

        public SnapPoint(int x, int y, Position gravity) {
            mX = x;
            mY = y;
            mActivity = 0.0f;
            this.position = gravity;
        }

        public void reset() {
            mActivity = 0.0f;
        }

        public void draw(Canvas canvas) {
            if (mActiveSnap == this) {
                canvas.drawCircle(mX, mY, mSnapRadius, mSnapActivePaint);
            }

            mSnapPaint.setAlpha((int) ((0x40 + 0xbf * mActivity) * mBackgroundFraction));
            canvas.drawCircle(mX, mY, mSnapRadius, mSnapPaint);
        }

        public boolean interact(float x, float y) {
            float distanceSqr = (x - mX) * (x - mX) + (y - mY) * (y - mY);
            if (distanceSqr - mSnapRadiusSqr < mSnapThresholdSqr) {
                PieLayout.this.invalidate();

                if (distanceSqr < mSnapRadiusSqr) {
                    if (DEBUG) {
                        Slog.d(TAG, "Snap point " + position.name()
                                + " activated with (" + x + ","+ y + ")");
                    }
                    mActivity = 1.0f;
                    return true;
                } else {
                    mActivity = 1.0f - ((distanceSqr - mSnapRadiusSqr) / mSnapThresholdSqr);
                    return false;
                }
            }
            return false;
        }

        public final Position position;
    }

    private int mTriggerSlots;
    private SnapPoint[] mSnapPoints = new SnapPoint[Position.values().length];
    private SnapPoint mActiveSnap = null;

    /**
     * Listener interface for snap events on {@link SnapPoint}s.
     */
    public interface OnSnapListener {
        void onSnap(Position position);
    }
    private OnSnapListener mOnSnapListener = null;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_SIZE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_POSITIONS), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            getDimensions();
            setupSnapPoints(getWidth(), getHeight(), true);
        }
    }
    private SettingsObserver mSettingsObserver;

    public PieLayout(Context context) {
        super(context);

        mHandler = new Handler();
        mBackgroundAnimator.addUpdateListener(mUpdateListener);

        setDrawingCacheEnabled(false);
        setVisibility(View.GONE);

        getDimensions();
        getColors();

        mTriggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_POSITIONS, Position.BOTTOM.FLAG);
    }

    public void setOnSnapListener(OnSnapListener onSnapListener) {
        mOnSnapListener = onSnapListener;
    }

    private void getDimensions() {
        mPieScale = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PIE_SIZE, 1f);

        final Resources res = mContext.getResources();

        mSnapRadius = res.getDimensionPixelSize(R.dimen.pie_snap_radius) * mPieScale;
        mSnapRadiusSqr = mSnapRadius * mSnapRadius;
        mSnapThreshold = Math.min(res.getDisplayMetrics().heightPixels,
                res.getDisplayMetrics().widthPixels) * 0.3f;
        mSnapThresholdSqr = mSnapThreshold * mSnapThreshold;

        mPadding = res.getDimensionPixelSize(R.dimen.pie_padding);
    }

    private void getColors() {
        final Resources res = mContext.getResources();

        mSnapPaint.setColor(res.getColor(R.color.pie_snap_color));
        mSnapPaint.setStyle(Style.STROKE);
        mSnapPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.pie_snap_outline));
        mSnapPaint.setAntiAlias(true);
        mSnapActivePaint.setColor(res.getColor(R.color.pie_snap_color));

        mBackgroundPaint.setColor(res.getColor(R.color.pie_overlay_color));
        mBackgroundTargetAlpha = mBackgroundPaint.getAlpha();
    }

    private void setupSnapPoints(int width, int height, boolean force) {
        if (force) {
            mTriggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_POSITIONS, Position.BOTTOM.FLAG);
        }

        mActiveSnap = null;
        for (Position g : Position.values()) {
            if ((mTriggerSlots & g.FLAG) == 0) {
                if (g == Position.LEFT || g == Position.RIGHT) {
                    mSnapPoints[g.INDEX] = new SnapPoint(g.FACTOR * width, height / 2, g);
                } else {
                    mSnapPoints[g.INDEX] = new SnapPoint(width / 2, g.FACTOR * height, g);
                }
            } else {
                mSnapPoints[g.INDEX] = null;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        setWillNotDraw(false);
        setFocusable(true);
        setOnTouchListener(this);

        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mActive) {
            if (DEBUG) {
                Slog.d(TAG, "onDraw: (" + canvas.getWidth() + "," + canvas.getHeight() + ")");
            }
            if (mActivateStartDebug != 0) {
                Slog.d(TAG,  "First draw within "
                        + (SystemClock.uptimeMillis() - mActivateStartDebug) + " ms");
            }
            mActivateStartDebug = 0;


            mBackgroundPaint.setAlpha((int) (mBackgroundFraction * mBackgroundTargetAlpha));
            canvas.drawPaint(mBackgroundPaint);

            for (int i = 0; i < mSnapPoints.length; i++) {
                if (mSnapPoints[i] != null) {
                    mSnapPoints[i].draw(canvas);
                }
            }

            // At the pie's internal view the center is always at (0,0) compensate for that!
            int state = canvas.save();
            switch (mPosition) {
                case LEFT:
                    canvas.rotate(90);
                    canvas.translate(mCenter.y, -mCenter.x);
                    break;
                case BOTTOM:
                    canvas.translate(mCenter.x, mCenter.y);
                    break;
                case RIGHT:
                    canvas.rotate(270);
                    canvas.translate(-mCenter.y, mCenter.x);
                    break;
                case TOP:
                    canvas.rotate(180);
                    canvas.translate(-mCenter.x, -mCenter.y);
            }

            for (PieDrawable drawable : mDrawableCache) {
                drawable.draw(canvas, mPosition);
            }

            canvas.restoreToCount(state);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getAction();
        final int index = event.findPointerIndex(mPointerId);

        // We can not read out raw values from any other index than 0
        if (action != MotionEvent.ACTION_DOWN && index != 0) {
            return false;
        }

        final float x = event.getRawX();
        final float y = event.getRawY();

        if (DEBUG_INPUT) {
            Slog.d(TAG, "onTouchListener received event: " + action + " at (" + x + "," + y + ")");
        }

        if (mActive) {
            if (action == MotionEvent.ACTION_DOWN) {
                mPointerId = event.getPointerId(0);
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                mActiveSnap = null;
                for (int i = 0; i < mSnapPoints.length; i++) {
                    if (mSnapPoints[i] != null) {
                        if (mSnapPoints[i].interact(x, y)) {
                            mActiveSnap = mSnapPoints[i];
                        }
                    }
                }

                double distance = Math.sqrt(Math.pow(mCenter.x - x, 2) + Math.pow(mCenter.y - y, 2));

                float alpha = (float) ((distance > 1.0f)
                        ? Math.toDegrees(Math.atan2(y - mCenter.y, x - mCenter.x)) : 0.0f);
                if (alpha < 0.0f) {
                    alpha = 360 + alpha;
                }
                alpha = (360 + alpha + (mPosition.INDEX - 1) * 90) % 360;

                // since everything is drawn with mPieScale we need to take this into account,
                // since the PieDrawables expect normalized coordinates.
                int radius = (int) (distance / mPieScale);

                if (DEBUG_INPUT) {
                    Slog.d(TAG, "interact on: (" + alpha + "," + distance + ")");
                }

                PieItem newItem = null;
                for (PieDrawable drawable : mDrawableCache) {
                    PieItem tmp = drawable.interact(alpha, radius);
                    if (tmp != null) {
                        newItem = tmp;
                    }
                }
                updateActiveItem(newItem, mLongPressed);
            }

            if (action == MotionEvent.ACTION_UP) {
                // check if anything was active
                if (mActiveSnap != null) {
                    if (mOnSnapListener != null) {
                        mOnSnapListener.onSnap(mActiveSnap.position);
                    }
                } else {
                    if (mActiveItem != null) {
                        mActiveItem.onClickCall(mLongPressed);
                    }
                }
                PieLayout.this.exit();
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                PieLayout.this.exit();
            }
        }
        return true;
    }

    private void updateActiveItem(PieItem newActiveItem, boolean newLongPressed) {
        if (mActiveItem != newActiveItem || mLongPressed != newLongPressed) {
            mHandler.removeCallbacks(mLongPressRunnable);
            if (mActiveItem != null) {
                mActiveItem.setSelected(false, false);
            }
            if (newActiveItem != null) {
                boolean canLongPressed = (newActiveItem.flags & PieItem.CAN_LONG_PRESS) != 0;
                newLongPressed = newLongPressed && canLongPressed;
                newActiveItem.setSelected(true, newLongPressed);
                if (canLongPressed && !newLongPressed) {
                    mHandler.postDelayed(mLongPressRunnable,
                            (int) (ViewConfiguration.getLongPressTimeout() * 1.5f));
                }
                // if the fade in hasn't started yet, restart again
                if (!mBackgroundAnimator.isRunning() && mBackgroundFraction == 0.0f) {
                    mBackgroundAnimator.cancel();
                    mBackgroundAnimator.start();
                }
            } else {
                newLongPressed = false;
            }
        }
        mActiveItem = newActiveItem;
        mLongPressed = newLongPressed;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (DEBUG) {
            Slog.d(TAG, "onLayout: " + changed + " (" + left + "," + top
                    + "." + right + "," + bottom + ")");
        }

        long start = SystemClock.uptimeMillis();
        if (mActivateStartDebug != 0) {
            Slog.d(TAG,  "Layout within " + (start - mActivateStartDebug) + " ms");
        }

        int viewMask = PieDrawable.VISIBLE | mPosition.FLAG;
        if (changed) {
            setupSnapPoints(right - left, bottom - top, false);
        }

        // we are only doing this, when the layout changed or
        // our position changed
        if (changed || mPosition != mLayoutDoneForPosition) {
            mDrawableCache.clear();
            for (PieSlice slice : mSlices) {
                slice.prepare(mPosition, mPieScale);
                if ((slice.flags & viewMask) == viewMask) {
                    mDrawableCache.add(slice);
                    // This is not nice, but it will help to keep the PieSlice abstract
                    // class more clutter free.
                    if (slice instanceof PieSliceContainer) {
                        for (PieItem item : ((PieSliceContainer)slice).getItems()) {
                            if ((item.flags & viewMask) == viewMask) {
                                item.prepare(mPosition, mPieScale);
                                mDrawableCache.add(item);
                            }
                        }
                    }
                }
            }
            mLayoutDoneForPosition = mPosition;
        }

        float estimatedWidth = 0.0f;

        for (PieSlice slice : mSlices) {
            if ((slice.flags & viewMask) == viewMask && (slice.flags & PieSlice.IMPORTANT) != 0) {
                estimatedWidth = Math.max(estimatedWidth, slice.estimateWidth());
            }
        }

        if (mPosition == Position.LEFT || mPosition == Position.RIGHT) {
            mCenter.x = mPadding + (int) ((getWidth() - 2 * mPadding) * mPosition.FACTOR);
            if (estimatedWidth * 1.3f > getHeight()) {
                mCenter.y = getHeight() / 2;
            } else {
                mCenter.y = (int) (Math.min(Math.max(estimatedWidth / 2, mCenter.y), getHeight()
                        - estimatedWidth / 2));
            }
        } else { /* Position.BOTTOM | Position.TOP */
            if (estimatedWidth * 1.3f > getWidth()) {
                mCenter.x = getWidth() / 2;
            } else {
                mCenter.x = (int) (Math.min(Math.max(estimatedWidth / 2, mCenter.x), getWidth()
                        - estimatedWidth / 2));
            }
            mCenter.y = mPadding + (int) ((getHeight() - 2 * mPadding) * mPosition.FACTOR);
        }

        Slog.d(TAG,  "Layout finished within " + (SystemClock.uptimeMillis() - start) + " ms");
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (DEBUG) {
            Slog.d(TAG, "onConfigurationChange: Updating dimensions");
        }

        getDimensions();
    }

    public void activate(Point center, Position position) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Slog.w(TAG, "Activation not on main thread: " + Thread.currentThread().getName());
        }

        mActivateStartDebug = SystemClock.uptimeMillis();

        mPosition = position;
        mLayoutDoneForPosition = null;
        mActive = true;

        // Set the activation center as center of the pie
        // This will be corrected by the #onLayout call.
        mCenter = center;

        mAnimationListenerCache.clear();
        for (PieSlice slice : mSlices) {
            if (slice instanceof ValueAnimator.AnimatorUpdateListener) {
                mAnimationListenerCache.add((ValueAnimator.AnimatorUpdateListener)slice);
            }
        }

        mBackgroundFraction = 0.0f;
        mBackgroundAnimator.setStartDelay(ViewConfiguration.getLongPressTimeout() * 2);
        mBackgroundAnimator.start();

        setVisibility(View.VISIBLE);


        Slog.d(TAG, "activate finished within "
                + (SystemClock.uptimeMillis() - mActivateStartDebug) + " ms");
    }

    public void exit() {
        setVisibility(View.GONE);
        mBackgroundAnimator.cancel();

        mActiveSnap = null;
        for (int i = 0; i < mSnapPoints.length; i++) {
            if (mSnapPoints[i] != null) {
                mSnapPoints[i].reset();
            }
        }

        updateActiveItem(null, false);

        mActive = false;
    }

    public void clearSlices() {
        mSlices.clear();
        // empty draw cache only if we are not active, otherwise this is postponed to the
        // next #onLayout() call
        if (!mActive) {
            mAnimationListenerCache.clear();
            mDrawableCache.clear();
            updateActiveItem(null, false);
        }
    }

    public void addSlice(PieSlice slice) {
        mSlices.add(slice);
    }

    public boolean isShowing() {
        return mActive;
    }
}
