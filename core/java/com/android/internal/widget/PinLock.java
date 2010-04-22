/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.internal.R;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Debug;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.os.Vibrator;

import android.util.Log;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across 9 regions of the screen.
 *
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */
public class PinLock extends View implements LockPattern {
    private final static String TAG = "PinLock";
    private static final int STATUS_BAR_HEIGHT = 25;
    private static final int REPLAY_INCREMENT_DURATION_MS = 600;
    private static final boolean DEBUG = false;
    private static final long[] DEFAULT_VIBE_PATTERN = {0, 1, 40, 41};

    private EventListener mEventListener;
    private Handler mHandler = new Handler();
    private Paint mPaint = new Paint();
    private LockPatternUtils mLockPatternUtils;
    private ArrayList<Cell> mPattern = new ArrayList<Cell>(9);
    private int mReplayIndex = 0;
    private State mState = State.Record;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private Cell mCurrent = null, mDown = null;
    private float mHitFactor = 1.0f;

    private Vibrator mVibe;
    private long[] mVibePattern;
    private boolean mTactileFeedbackEnabled = true;

    private float mSquareWidth;
    private float mSquareHeight;
    private Bitmap mBitmapBtnDefault;
    private Bitmap mBitmapBtnTouched;
    private Bitmap mBitmapCircleDefault;
    private Bitmap mBitmapCircleGreen;
    private Bitmap mBitmapCircleRed;
    private int mBitmapWidth;
    private int mBitmapHeight;

    public PinLock(Context context) {
        this(context, null);
    }

    public PinLock(Context context, AttributeSet attrs) {
        super(context, attrs);

        setClickable(true);

        // lots of bitmaps!
        mBitmapBtnDefault = getBitmapFor(R.drawable.btn_code_lock_default);
        mBitmapBtnTouched = getBitmapFor(R.drawable.btn_code_lock_touched);
        mBitmapCircleDefault = getBitmapFor(R.drawable.indicator_code_lock_point_area_default);
        mBitmapCircleGreen = getBitmapFor(R.drawable.indicator_code_lock_point_area_green);
        mBitmapCircleRed = getBitmapFor(R.drawable.indicator_code_lock_point_area_red);

        // we assume all bitmaps have the same size
        mBitmapWidth = mBitmapBtnDefault.getWidth();
        mBitmapHeight = mBitmapBtnDefault.getHeight();

        mLockPatternUtils = new LockPatternUtils(context.getContentResolver());
        
        mVibe = new Vibrator();
        mVibePattern = loadVibratePattern(com.android.internal.R.array.config_virtualKeyVibePattern);
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mTactileFeedbackEnabled;
    }

    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mTactileFeedbackEnabled = tactileFeedbackEnabled;
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * Set whether the view is in stealth mode.  If true, there will be no
     * visible feedback as the user enters the pattern.
     *
     * @param inStealthMode Whether in stealth mode.
     */
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    /**
     * Set the call back for pattern detection.
     * @param onPatternListener The call back.
     */
    public void setEventListener(
            EventListener eventListener) {
        mEventListener = eventListener;
    }

    /**
     * Set the pattern explicitly (rather than waiting for the user to input
     * a pattern).
     * @param displayMode How to display the pattern.
     * @param pattern The pattern.
     */
    public void setPattern(State state, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        mReplayIndex = 0;
        setState(state);
    }

    /**
     * Set the display mode of the current pattern.  This can be useful, for
     * instance, after detecting a pattern to tell this view whether change the
     * in progress result to correct or wrong.
     * @param displayMode The display mode.
     */
    public void setState(State state) {
        if (state == mState) {
            if (DEBUG) Log.i(TAG, "setState: state==mState, returning.");
            return;
        }

        if (DEBUG) Log.i(TAG, "state: " + state.toString());
        mState = state;
        disableInput();

        if (state == State.Record) {
            enableInput();
            clearPattern();
        }
        else if (state == State.Replay) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException("you must have a pattern to "
                    + "animate if you want to set the display mode to animate");
            }

            mReplayIndex = 0;
            mHandler.post(mIncrementReplayRunnable);
        }
        else if (state == LockPattern.State.Correct
              || state == LockPattern.State.Incorrect) {

        }

        mCurrent = null;
        invalidate();
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Disable input (for instance when displaying a message that will
     * timeout so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    public View getView() {
        return this;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    public int getCorrectDelay() {
        return 750;
    }

    public int getIncorrectDelay() {
        return 1500;
    }

    private long[] loadVibratePattern(int id) {
        int[] pattern = null;
        try {
            pattern = getResources().getIntArray(id);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Vibrate pattern missing, using default", e);
        }
        if (pattern == null) {
            return DEFAULT_VIBE_PATTERN;
        }

        long[] tmpPattern = new long[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            tmpPattern[i] = pattern[i];
        }
        return tmpPattern;
    }

    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    private Runnable mIncrementReplayRunnable = new Runnable() {
        public void run() {
            if (mState == State.Replay) {
                int delay = REPLAY_INCREMENT_DURATION_MS;

                if (mReplayIndex >= mPattern.size()) {
                    mReplayIndex = 0;
                    mCurrent = null;
                    delay = REPLAY_INCREMENT_DURATION_MS * 2;
                }
                else {
                    mCurrent = mPattern.get(mReplayIndex++);
                }

                mHandler.postDelayed(mClearCurrentRunnable, delay - 100);
                mHandler.postDelayed(this, delay);

                invalidate();
            }
        }
    };

    private Runnable mPatternEntryFinishedRunnable = new Runnable() {
        public void run() {
            if (mEventListener != null) {
                mEventListener.onPatternDetected(mPattern);
            }

            onUserInteraction();
        }
    };

    private Runnable mClearCurrentRunnable = new Runnable() {
        public void run() {
            mCurrent = null;
            invalidate();
        }
    };

    private Runnable mFlashCompleteRunnable = new Runnable() {
        public void run() {
            setState(State.Record);
        }
    };

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        if (DEBUG) Log.i(TAG, "reset");
        mPattern.clear();
        mReplayIndex = 0;
        mCurrent = null;
        setState(State.Record);
        invalidate();

        if (mEventListener != null) {
            mEventListener.onPatternCleared();
        }
    }

    private void onUserInteraction() {
        if (mEventListener != null) {
            mEventListener.onUserInteraction();
        }
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - this.getPaddingLeft() - this.getPaddingRight();
        mSquareWidth = width / 3.0f;

        final int height = h - this.getPaddingTop() - this.getPaddingBottom();
        mSquareHeight = height / 3.0f;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final WindowManager wm = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        final int width = wm.getDefaultDisplay().getWidth();
        final int height = wm.getDefaultDisplay().getHeight();
        int squareSide = Math.min(width, height);

        // if in landscape...
        if (width > height) {
            squareSide -= STATUS_BAR_HEIGHT;
        }

        setMeasuredDimension(squareSide, squareSide);
    }

    /**
     * Determines whether the point x, y will add a new point to the current
     * pattern (in addition to finding the cell, also makes heuristic choices
     * such as filling in gaps based on current pattern).
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    private Cell detectAndAddHit(float x, float y) {
        final Cell cell = checkForNewHit(x, y);

        if ((mDown != null) && (cell == mDown)) {
        	mPattern.add(cell);

            if (mTactileFeedbackEnabled){
                mVibe.vibrate(mVibePattern, -1);
            }

            return cell;
        }

        return null;
    }

    private void stopWaitingForTimeout() {
    	
    }
    
    void cancelPatternEntryFinishedTimeout() {
        mHandler.removeCallbacks(mPatternEntryFinishedRunnable);
    }

    void schedulePatternEntryFinishedTimeout() {
        mHandler.postDelayed(
            mPatternEntryFinishedRunnable,
            mLockPatternUtils.getPinCheckTimeout());
    }

    // helper method to find which cell a point maps to
    private Cell checkForNewHit(float x, float y) {
        final int rowHit = getRowHit(y);
        if (rowHit < 0) {
            return null;
        }
        final int columnHit = getColumnHit(x);
        if (columnHit < 0) {
            return null;
        }

        return Cell.of(rowHit, columnHit);
    }

    /**
     * Helper method to find the row that y falls into.
     * @param y The y coordinate
     * @return The row that y falls in, or -1 if it falls in no row.
     */
    private int getRowHit(float y) {

        final float squareHeight = mSquareHeight;
        float hitSize = squareHeight * mHitFactor;

        float offset = this.getPaddingTop() + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {

            final float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to find the column x fallis into.
     * @param x The x coordinate.
     * @return The column that x falls in, or -1 if it falls in no column.
     */
    private int getColumnHit(float x) {
        final float squareWidth = mSquareWidth;
        float hitSize = squareWidth * mHitFactor;

        float offset = this.getPaddingLeft() + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        final float x = motionEvent.getX();
        final float y = motionEvent.getY();
        Cell hitCell;
        switch(motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
            	if (DEBUG) Log.i(TAG, "down");
        		cancelPatternEntryFinishedTimeout();
                mCurrent = mDown = checkForNewHit(x, y);
                invalidate();
                onUserInteraction();
                return true;

            case MotionEvent.ACTION_UP:
            	if (DEBUG) Log.i(TAG, "up");
                hitCell = detectAndAddHit(x, y);
                if (hitCell != null) {
                    if (mPattern.size() == 1) {
                        setState(State.Record);

                        if (mEventListener != null) {
                            mEventListener.onPatternStart();
                        }
                    }
                }
                mCurrent = mDown = null;
                invalidate();
                onUserInteraction();
                schedulePatternEntryFinishedTimeout();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mDown != null) {
                    hitCell = checkForNewHit(x, y);
                    if (hitCell == mDown) {
                        // if mCurrent is null that means the user "dragged" away from
                        // the cell he initially touched. now he's back over it, so
                        // highlight it again
                        if (mCurrent == null) {
                            mCurrent = hitCell;
                            invalidate();
                        }
                    }
                    else {
                        // user touched a cell initially, but "dragged" away from it.
                        mCurrent = null;
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
        if (DEBUG) Log.i(TAG, "cancel");
                clearPattern();
                onUserInteraction();
                return true;
        }
        return false;
    }

    private float getCenterXForColumn(int column) {
        return this.getPaddingLeft() + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return this.getPaddingTop() + row * mSquareHeight + mSquareHeight / 2f;
    }

    protected void onDraw(Canvas canvas) {
        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        // draw the circles
        final int paddingTop = this.getPaddingTop();
        final int paddingLeft = this.getPaddingLeft();

        for (int i = 0; i < 3; i++) {
            float topY = paddingTop + i * squareHeight;

            for (int j = 0; j < 3; j++) {
                float leftX = paddingLeft + j * squareWidth;
                drawCircle(canvas, (int) leftX, (int) topY, Cell.sCells[i][j]);
            }
        }
    }

    /**
     * @param canvas
     * @param leftX
     * @param topY
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawCircle(Canvas canvas, int leftX, int topY, Cell cell) {
        Bitmap outerCircle;
        Bitmap innerCircle;

        if (mCurrent != null && cell == mCurrent) {
            if (mState == State.Replay) {
                outerCircle = mBitmapCircleGreen;
                innerCircle = mBitmapBtnDefault;

            }
            else {
                if (mInStealthMode) {
                    outerCircle = mBitmapCircleDefault;
                    innerCircle = mBitmapBtnDefault;
                }
                else {
                    outerCircle = mBitmapCircleGreen;
                    innerCircle = mBitmapBtnTouched;
                }
            }
        }
        else {
            if (mState == LockPattern.State.Incorrect) {
                outerCircle = mBitmapCircleRed;
                innerCircle = mBitmapBtnTouched;
            }
            else if (mState == LockPattern.State.Correct) {
                outerCircle = mBitmapCircleGreen;
                innerCircle = mBitmapBtnTouched;
            }
            else {
                outerCircle = mBitmapCircleDefault;
                innerCircle = mBitmapBtnDefault;
            }
        }

        final int width = mBitmapWidth;
        final int height = mBitmapHeight;

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        int offsetX = (int) ((squareWidth - width) / 2f);
        int offsetY = (int) ((squareHeight - height) / 2f);

        canvas.drawBitmap(outerCircle, leftX + offsetX, topY + offsetY, mPaint);
        canvas.drawBitmap(innerCircle, leftX + offsetX, topY + offsetY, mPaint);
    }

    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LockPatternUtils.patternToString(mPattern),
                mState.ordinal(),
                mInputEnabled, mInStealthMode, mTactileFeedbackEnabled);
    }

    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(
                State.Record,
                LockPatternUtils.stringToPattern(ss.getSerializedPattern()));
        mState = State.values()[ss.getDisplayMode()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mTactileFeedbackEnabled = ss.isTactileFeedbackEnabled();
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;

        /**
         * Constructor called from {@link LockPatternView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern, int displayMode,
                boolean inputEnabled, boolean inStealthMode, boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled(){
            return mTactileFeedbackEnabled;
        }

        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}

