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


package com.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.ExpandableView;
import com.android.systemui.statusbar.FlingAnimationUtils;
import com.android.systemui.statusbar.policy.ScrollAdapter;

public class ExpandHelper implements Gefingerpoken {
    public interface Callback {
        ExpandableView getChildAtRawPosition(float x, float y);
        ExpandableView getChildAtPosition(float x, float y);
        boolean canChildBeExpanded(View v);
        void setUserExpandedChild(View v, boolean userExpanded);
        void setUserLockedChild(View v, boolean userLocked);
        void expansionStateChanged(boolean isExpanding);
    }

    private static final String TAG = "ExpandHelper";
    protected static final boolean DEBUG = false;
    protected static final boolean DEBUG_SCALE = false;
    private static final float EXPAND_DURATION = 0.3f;

    // Set to false to disable focus-based gestures (spread-finger vertical pull).
    private static final boolean USE_DRAG = true;
    // Set to false to disable scale-based gestures (both horizontal and vertical).
    private static final boolean USE_SPAN = true;
    // Both gestures types may be active at the same time.
    // At least one gesture type should be active.
    // A variant of the screwdriver gesture will emerge from either gesture type.

    // amount of overstretch for maximum brightness expressed in U
    // 2f: maximum brightness is stretching a 1U to 3U, or a 4U to 6U
    private static final float STRETCH_INTERVAL = 2f;

    @SuppressWarnings("unused")
    private Context mContext;

    private boolean mExpanding;
    private static final int NONE    = 0;
    private static final int BLINDS  = 1<<0;
    private static final int PULL    = 1<<1;
    private static final int STRETCH = 1<<2;
    private int mExpansionStyle = NONE;
    private boolean mWatchingForPull;
    private boolean mHasPopped;
    private View mEventSource;
    private float mOldHeight;
    private float mNaturalHeight;
    private float mInitialTouchFocusY;
    private float mInitialTouchY;
    private float mInitialTouchSpan;
    private float mLastFocusY;
    private float mLastSpanY;
    private int mTouchSlop;
    private float mLastMotionY;
    private float mPullGestureMinXSpan;
    private Callback mCallback;
    private ScaleGestureDetector mSGD;
    private ViewScaler mScaler;
    private ObjectAnimator mScaleAnimation;
    private boolean mEnabled = true;
    private ExpandableView mResizedView;
    private float mCurrentHeight;

    private int mSmallSize;
    private int mLargeSize;
    private float mMaximumStretch;
    private boolean mOnlyMovements;

    private int mGravity;

    private ScrollAdapter mScrollAdapter;
    private FlingAnimationUtils mFlingAnimationUtils;
    private VelocityTracker mVelocityTracker;

    private OnScaleGestureListener mScaleGestureListener
            = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (DEBUG_SCALE) Log.v(TAG, "onscalebegin()");

            if (!mOnlyMovements) {
                startExpanding(mResizedView, STRETCH);
            }
            return mExpanding;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (DEBUG_SCALE) Log.v(TAG, "onscale() on " + mResizedView);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    };

    private class ViewScaler {
        ExpandableView mView;

        public ViewScaler() {}
        public void setView(ExpandableView v) {
            mView = v;
        }
        public void setHeight(float h) {
            if (DEBUG_SCALE) Log.v(TAG, "SetHeight: setting to " + h);
            mView.setContentHeight((int) h);
            mCurrentHeight = h;
        }
        public float getHeight() {
            return mView.getContentHeight();
        }
        public int getNaturalHeight(int maximum) {
            return Math.min(maximum, mView.getMaxContentHeight());
        }
    }

    /**
     * Handle expansion gestures to expand and contract children of the callback.
     *
     * @param context application context
     * @param callback the container that holds the items to be manipulated
     * @param small the smallest allowable size for the manuipulated items.
     * @param large the largest allowable size for the manuipulated items.
     */
    public ExpandHelper(Context context, Callback callback, int small, int large) {
        mSmallSize = small;
        mMaximumStretch = mSmallSize * STRETCH_INTERVAL;
        mLargeSize = large;
        mContext = context;
        mCallback = callback;
        mScaler = new ViewScaler();
        mGravity = Gravity.TOP;
        mScaleAnimation = ObjectAnimator.ofFloat(mScaler, "height", 0f);
        mPullGestureMinXSpan = mContext.getResources().getDimension(R.dimen.pull_span_min);

        final ViewConfiguration configuration = ViewConfiguration.get(mContext);
        mTouchSlop = configuration.getScaledTouchSlop();

        mSGD = new ScaleGestureDetector(context, mScaleGestureListener);
        mFlingAnimationUtils = new FlingAnimationUtils(context, EXPAND_DURATION);
    }

    private void updateExpansion() {
        if (DEBUG_SCALE) Log.v(TAG, "updateExpansion()");
        // are we scaling or dragging?
        float span = mSGD.getCurrentSpan() - mInitialTouchSpan;
        span *= USE_SPAN ? 1f : 0f;
        float drag = mSGD.getFocusY() - mInitialTouchFocusY;
        drag *= USE_DRAG ? 1f : 0f;
        drag *= mGravity == Gravity.BOTTOM ? -1f : 1f;
        float pull = Math.abs(drag) + Math.abs(span) + 1f;
        float hand = drag * Math.abs(drag) / pull + span * Math.abs(span) / pull;
        float target = hand + mOldHeight;
        float newHeight = clamp(target);
        mScaler.setHeight(newHeight);
        mLastFocusY = mSGD.getFocusY();
        mLastSpanY = mSGD.getCurrentSpan();
    }

    private float clamp(float target) {
        float out = target;
        out = out < mSmallSize ? mSmallSize : (out > mLargeSize ? mLargeSize : out);
        out = out > mNaturalHeight ? mNaturalHeight : out;
        return out;
    }

    private ExpandableView findView(float x, float y) {
        ExpandableView v;
        if (mEventSource != null) {
            int[] location = new int[2];
            mEventSource.getLocationOnScreen(location);
            x += location[0];
            y += location[1];
            v = mCallback.getChildAtRawPosition(x, y);
        } else {
            v = mCallback.getChildAtPosition(x, y);
        }
        return v;
    }

    private boolean isInside(View v, float x, float y) {
        if (DEBUG) Log.d(TAG, "isinside (" + x + ", " + y + ")");

        if (v == null) {
            if (DEBUG) Log.d(TAG, "isinside null subject");
            return false;
        }
        if (mEventSource != null) {
            int[] location = new int[2];
            mEventSource.getLocationOnScreen(location);
            x += location[0];
            y += location[1];
            if (DEBUG) Log.d(TAG, "  to global (" + x + ", " + y + ")");
        }
        int[] location = new int[2];
        v.getLocationOnScreen(location);
        x -= location[0];
        y -= location[1];
        if (DEBUG) Log.d(TAG, "  to local (" + x + ", " + y + ")");
        if (DEBUG) Log.d(TAG, "  inside (" + v.getWidth() + ", " + v.getHeight() + ")");
        boolean inside = (x > 0f && y > 0f && x < v.getWidth() & y < v.getHeight());
        return inside;
    }

    public void setEventSource(View eventSource) {
        mEventSource = eventSource;
    }

    public void setGravity(int gravity) {
        mGravity = gravity;
    }

    public void setScrollAdapter(ScrollAdapter adapter) {
        mScrollAdapter = adapter;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        trackVelocity(ev);
        final int action = ev.getAction();
        if (DEBUG_SCALE) Log.d(TAG, "intercept: act=" + MotionEvent.actionToString(action) +
                         " expanding=" + mExpanding +
                         (0 != (mExpansionStyle & BLINDS) ? " (blinds)" : "") +
                         (0 != (mExpansionStyle & PULL) ? " (pull)" : "") +
                         (0 != (mExpansionStyle & STRETCH) ? " (stretch)" : ""));
        // check for a spread-finger vertical pull gesture
        mSGD.onTouchEvent(ev);
        final int x = (int) mSGD.getFocusX();
        final int y = (int) mSGD.getFocusY();

        mInitialTouchFocusY = y;
        mInitialTouchSpan = mSGD.getCurrentSpan();
        mLastFocusY = mInitialTouchFocusY;
        mLastSpanY = mInitialTouchSpan;
        if (DEBUG_SCALE) Log.d(TAG, "set initial span: " + mInitialTouchSpan);

        if (mExpanding) {
            mLastMotionY = ev.getRawY();
            maybeRecycleVelocityTracker(ev);
            return true;
        } else {
            if ((action == MotionEvent.ACTION_MOVE) && 0 != (mExpansionStyle & BLINDS)) {
                // we've begun Venetian blinds style expansion
                return true;
            }
            switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                final float xspan = mSGD.getCurrentSpanX();
                if (xspan > mPullGestureMinXSpan &&
                        xspan > mSGD.getCurrentSpanY() && !mExpanding) {
                    // detect a vertical pulling gesture with fingers somewhat separated
                    if (DEBUG_SCALE) Log.v(TAG, "got pull gesture (xspan=" + xspan + "px)");
                    startExpanding(mResizedView, PULL);
                    mWatchingForPull = false;
                }
                if (mWatchingForPull) {
                    final float yDiff = ev.getRawY() - mInitialTouchY;
                    if (yDiff > mTouchSlop) {
                        if (DEBUG) Log.v(TAG, "got venetian gesture (dy=" + yDiff + "px)");
                        mWatchingForPull = false;
                        if (mResizedView != null && !isFullyExpanded(mResizedView)) {
                            if (startExpanding(mResizedView, BLINDS)) {
                                mLastMotionY = ev.getRawY();
                                mInitialTouchY = ev.getRawY();
                                mHasPopped = false;
                            }
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN:
                mWatchingForPull = mScrollAdapter != null &&
                        isInside(mScrollAdapter.getHostView(), x, y)
                        && mScrollAdapter.isScrolledToTop();
                mResizedView = findView(x, y);
                if (mResizedView != null && !mCallback.canChildBeExpanded(mResizedView)) {
                    mResizedView = null;
                    mWatchingForPull = false;
                }
                mInitialTouchY = ev.getY();
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (DEBUG) Log.d(TAG, "up/cancel");
                finishExpanding(false, getCurrentVelocity());
                clearView();
                break;
            }
            mLastMotionY = ev.getRawY();
            maybeRecycleVelocityTracker(ev);
            return mExpanding;
        }
    }

    private void trackVelocity(MotionEvent event) {
        int action = event.getActionMasked();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(event);
                break;
            default:
                break;
        }
    }

    private void maybeRecycleVelocityTracker(MotionEvent event) {
        if (mVelocityTracker != null && (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_UP)) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private float getCurrentVelocity() {
        if (mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000);
            return mVelocityTracker.getYVelocity();
        } else {
            return 0f;
        }
    }

    public void setEnabled(boolean enable) {
        mEnabled = enable;
    }

    private boolean isEnabled() {
        return mEnabled;
    }

    private boolean isFullyExpanded(ExpandableView underFocus) {
        return underFocus.areChildrenExpanded() || underFocus.getIntrinsicHeight()
                - underFocus.getBottomDecorHeight() == underFocus.getMaxContentHeight();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        trackVelocity(ev);
        final int action = ev.getActionMasked();
        if (DEBUG_SCALE) Log.d(TAG, "touch: act=" + MotionEvent.actionToString(action) +
                " expanding=" + mExpanding +
                (0 != (mExpansionStyle & BLINDS) ? " (blinds)" : "") +
                (0 != (mExpansionStyle & PULL) ? " (pull)" : "") +
                (0 != (mExpansionStyle & STRETCH) ? " (stretch)" : ""));

        mSGD.onTouchEvent(ev);
        final int x = (int) mSGD.getFocusX();
        final int y = (int) mSGD.getFocusY();

        if (mOnlyMovements) {
            mLastMotionY = ev.getRawY();
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mWatchingForPull = mScrollAdapter != null &&
                        isInside(mScrollAdapter.getHostView(), x, y);
                mResizedView = findView(x, y);
                mInitialTouchY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                if (mWatchingForPull) {
                    final float yDiff = ev.getRawY() - mInitialTouchY;
                    if (yDiff > mTouchSlop) {
                        if (DEBUG) Log.v(TAG, "got venetian gesture (dy=" + yDiff + "px)");
                        mWatchingForPull = false;
                        if (mResizedView != null && !isFullyExpanded(mResizedView)) {
                            if (startExpanding(mResizedView, BLINDS)) {
                                mInitialTouchY = ev.getRawY();
                                mLastMotionY = ev.getRawY();
                                mHasPopped = false;
                            }
                        }
                    }
                }
                if (mExpanding && 0 != (mExpansionStyle & BLINDS)) {
                    final float rawHeight = ev.getRawY() - mLastMotionY + mCurrentHeight;
                    final float newHeight = clamp(rawHeight);
                    boolean isFinished = false;
                    boolean expanded = false;
                    if (rawHeight > mNaturalHeight) {
                        isFinished = true;
                        expanded = true;
                    }
                    if (rawHeight < mSmallSize) {
                        isFinished = true;
                        expanded = false;
                    }

                    if (!mHasPopped) {
                        if (mEventSource != null) {
                            mEventSource.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        }
                        mHasPopped = true;
                    }

                    mScaler.setHeight(newHeight);
                    mLastMotionY = ev.getRawY();
                    if (isFinished) {
                        mCallback.setUserExpandedChild(mResizedView, expanded);
                        mCallback.expansionStateChanged(false);
                        return false;
                    } else {
                        mCallback.expansionStateChanged(true);
                    }
                    return true;
                }

                if (mExpanding) {

                    // Gestural expansion is running
                    updateExpansion();
                    mLastMotionY = ev.getRawY();
                    return true;
                }

                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (DEBUG) Log.d(TAG, "pointer change");
                mInitialTouchY += mSGD.getFocusY() - mLastFocusY;
                mInitialTouchSpan += mSGD.getCurrentSpan() - mLastSpanY;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG) Log.d(TAG, "up/cancel");
                finishExpanding(false, getCurrentVelocity());
                clearView();
                break;
        }
        mLastMotionY = ev.getRawY();
        maybeRecycleVelocityTracker(ev);
        return mResizedView != null;
    }

    /**
     * @return True if the view is expandable, false otherwise.
     */
    private boolean startExpanding(ExpandableView v, int expandType) {
        if (!(v instanceof ExpandableNotificationRow)) {
            return false;
        }
        mExpansionStyle = expandType;
        if (mExpanding && v == mResizedView) {
            return true;
        }
        mExpanding = true;
        mCallback.expansionStateChanged(true);
        if (DEBUG) Log.d(TAG, "scale type " + expandType + " beginning on view: " + v);
        mCallback.setUserLockedChild(v, true);
        mScaler.setView(v);
        mOldHeight = mScaler.getHeight();
        mCurrentHeight = mOldHeight;
        if (mCallback.canChildBeExpanded(v)) {
            if (DEBUG) Log.d(TAG, "working on an expandable child");
            mNaturalHeight = mScaler.getNaturalHeight(mLargeSize);
        } else {
            if (DEBUG) Log.d(TAG, "working on a non-expandable child");
            mNaturalHeight = mOldHeight;
        }
        if (DEBUG) Log.d(TAG, "got mOldHeight: " + mOldHeight +
                    " mNaturalHeight: " + mNaturalHeight);
        return true;
    }

    private void finishExpanding(boolean force, float velocity) {
        if (!mExpanding) return;

        if (DEBUG) Log.d(TAG, "scale in finishing on view: " + mResizedView);

        float currentHeight = mScaler.getHeight();
        float targetHeight = mSmallSize;
        float h = mScaler.getHeight();
        final boolean wasClosed = (mOldHeight == mSmallSize);
        if (wasClosed) {
            targetHeight = (force || currentHeight > mSmallSize) ? mNaturalHeight : mSmallSize;
        } else {
            targetHeight = (force || currentHeight < mNaturalHeight) ? mSmallSize : mNaturalHeight;
        }
        if (mScaleAnimation.isRunning()) {
            mScaleAnimation.cancel();
        }
        mCallback.setUserExpandedChild(mResizedView, targetHeight == mNaturalHeight);
        mCallback.expansionStateChanged(false);
        if (targetHeight != currentHeight) {
            mScaleAnimation.setFloatValues(targetHeight);
            mScaleAnimation.setupStartValues();
            final View scaledView = mResizedView;
            mScaleAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCallback.setUserLockedChild(scaledView, false);
                    mScaleAnimation.removeListener(this);
                }
            });
            mFlingAnimationUtils.apply(mScaleAnimation, currentHeight, targetHeight, velocity);
            mScaleAnimation.start();
        } else {
            mCallback.setUserLockedChild(mResizedView, false);
        }

        mExpanding = false;
        mExpansionStyle = NONE;

        if (DEBUG) Log.d(TAG, "wasClosed is: " + wasClosed);
        if (DEBUG) Log.d(TAG, "currentHeight is: " + currentHeight);
        if (DEBUG) Log.d(TAG, "mSmallSize is: " + mSmallSize);
        if (DEBUG) Log.d(TAG, "targetHeight is: " + targetHeight);
        if (DEBUG) Log.d(TAG, "scale was finished on view: " + mResizedView);
    }

    private void clearView() {
        mResizedView = null;
    }

    /**
     * Use this to abort any pending expansions in progress.
     */
    public void cancel() {
        finishExpanding(true, 0f /* velocity */);
        clearView();

        // reset the gesture detector
        mSGD = new ScaleGestureDetector(mContext, mScaleGestureListener);
    }

    /**
     * Change the expansion mode to only observe movements and don't perform any resizing.
     * This is needed when the expanding is finished and the scroller kicks in,
     * performing an overscroll motion. We only want to shrink it again when we are not
     * overscrolled.
     *
     * @param onlyMovements Should only movements be observed?
     */
    public void onlyObserveMovements(boolean onlyMovements) {
        mOnlyMovements = onlyMovements;
    }
}

