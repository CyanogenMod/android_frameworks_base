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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across 9 regions of the screen.
 *
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */
public class DragLock extends View implements LockPattern {
    // Vibrator pattern for creating a tactile bump
    private static final long[] DEFAULT_VIBE_PATTERN = {0, 1, 40, 41};

    private static final boolean PROFILE_DRAWING = false;
    private boolean mDrawingProfilingStarted = false;

    private Paint mPaint = new Paint();
    private Paint mPathPaint = new Paint();

    // TODO: make this common with PhoneWindow
    static final int STATUS_BAR_HEIGHT = 25;

    /**
     * How many milliseconds we spend animating each circle of a lock pattern
     * if the animating mode is set.  The entire animation should take this
     * constant * the length of the pattern to complete.
     */
    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;

    private EventListener mEventListener;
    private ArrayList<Cell> mPattern = new ArrayList<Cell>(9);

    /**
     * Lookup table for the circles of the pattern we are currently drawing.
     * This will be the cells of the complete pattern unless we are animating,
     * in which case we use this to hold the cells we are drawing for the in
     * progress animation.
     */
    private boolean[][] mPatternDrawLookup = new boolean[3][3];

    /**
     * the in progress point:
     * - during interaction: where the user's finger is
     * - during animation: the current tip of the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;

    private long mAnimatingPeriodStart;

    private State mState = State.Record;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mTactileFeedbackEnabled = true;

    private float mDiameterFactor = 0.5f;
    private float mHitFactor = 0.6f;

    private float mSquareWidth;
    private float mSquareHeight;

    private Bitmap mBitmapBtnDefault;
    private Bitmap mBitmapBtnTouched;
    private Bitmap mBitmapCircleDefault;
    private Bitmap mBitmapCircleGreen;
    private Bitmap mBitmapCircleRed;

    private Bitmap mBitmapArrowGreenUp;
    private Bitmap mBitmapArrowRedUp;

    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();

    private int mBitmapWidth;
    private int mBitmapHeight;


    private Vibrator vibe; // Vibrator for creating tactile feedback

    private long[] mVibePattern;

    public DragLock(Context context) {
        this(context, null);
    }

    public DragLock(Context context, AttributeSet attrs) {
        super(context, attrs);
        vibe = new Vibrator();

        setClickable(true);

        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);
        mPathPaint.setColor(Color.WHITE);   // TODO this should be from the style
        mPathPaint.setAlpha(128);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        // lot's of bitmaps!
        mBitmapBtnDefault = getBitmapFor(R.drawable.btn_code_lock_default);
        mBitmapBtnTouched = getBitmapFor(R.drawable.btn_code_lock_touched);
        mBitmapCircleDefault = getBitmapFor(R.drawable.indicator_code_lock_point_area_default);
        mBitmapCircleGreen = getBitmapFor(R.drawable.indicator_code_lock_point_area_green);
        mBitmapCircleRed = getBitmapFor(R.drawable.indicator_code_lock_point_area_red);

        mBitmapArrowGreenUp = getBitmapFor(R.drawable.indicator_code_lock_drag_direction_green_up);
        mBitmapArrowRedUp = getBitmapFor(R.drawable.indicator_code_lock_drag_direction_red_up);

        // we assume all bitmaps have the same size
        mBitmapWidth = mBitmapBtnDefault.getWidth();
        mBitmapHeight = mBitmapBtnDefault.getHeight();

        // allow vibration pattern to be customized
        mVibePattern = loadVibratePattern(com.android.internal.R.array.config_virtualKeyVibePattern);
    }

    private long[] loadVibratePattern(int id) {
        int[] pattern = null;
        try {
            pattern = getResources().getIntArray(id);
        } catch (Resources.NotFoundException e) {
            Log.e("LockPatternView", "Vibrate pattern missing, using default", e);
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

    public View getView() {
        return this;
    }

    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mTactileFeedbackEnabled;
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
     * Set whether the view will use tactile feedback.  If true, there will be
     * tactile feedback as the user enters the pattern.
     *
     * @param tactileFeedbackEnabled Whether tactile feedback is enabled
     */
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mTactileFeedbackEnabled = tactileFeedbackEnabled;
    }

    /**
     * Set the call back for pattern detection.
     * @param eventeListener The call back.
     */
    public void setEventListener(EventListener eventeListener) {
        mEventListener = eventeListener;
    }

    /**
     * Set the pattern explicitely (rather than waiting for the user to input
     * a pattern).
     * @param state How to display the pattern.
     * @param pattern The pattern.
     */
    public void setPattern(State state, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
        }

        setState(state);
    }

    /**
     * Set the display mode of the current pattern.  This can be useful, for
     * instance, after detecting a pattern to tell this view whether change the
     * in progress result to correct or wrong.
     * @param displayMode The display mode.
     */
    public void setState(State state) {
        mState = state;
        if (state == State.Replay) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException("you must have a pattern to "
                        + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Cell first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.getColumn());
            mInProgressY = getCenterYForRow(first.getRow());
            clearPatternDrawLookup();
        }
        else if (state == State.Record) {
            resetPattern();
        }
        invalidate();
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mState = State.Record;
        invalidate();
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Disable input (for instance when displaying a message that will
     * timeout so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    public int getCorrectDelay() {
        return 0;
    }

    public int getIncorrectDelay() {
        return 1500;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - mPaddingLeft - mPaddingRight;
        mSquareWidth = width / 3.0f;

        final int height = h - mPaddingTop - mPaddingBottom;
        mSquareHeight = height / 3.0f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int squareSide = Math.min(width, height);
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
        if (cell != null) {

            // check for gaps in existing pattern
            Cell fillInGapCell = null;
            final ArrayList<Cell> pattern = mPattern;
            if (!pattern.isEmpty()) {
                final Cell lastCell = pattern.get(pattern.size() - 1);
                int dRow = cell.row - lastCell.row;
                int dColumn = cell.column - lastCell.column;

                int fillInRow = lastCell.row;
                int fillInColumn = lastCell.column;

                if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                    fillInRow = lastCell.row + ((dRow > 0) ? 1 : -1);
                }

                if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                    fillInColumn = lastCell.column + ((dColumn > 0) ? 1 : -1);
                }

                fillInGapCell = Cell.of(fillInRow, fillInColumn);
            }

            if (fillInGapCell != null &&
                    !mPatternDrawLookup[fillInGapCell.row][fillInGapCell.column]) {
                addCellToPattern(fillInGapCell);
            }
            addCellToPattern(cell);
            if (mTactileFeedbackEnabled){
                vibe.vibrate(mVibePattern, -1); // Generate tactile feedback
            }
            return cell;
        }
        return null;
    }

    private void addCellToPattern(Cell newCell) {
        mPatternDrawLookup[newCell.getRow()][newCell.getColumn()] = true;
        mPattern.add(newCell);
        if (mEventListener != null) {
            mEventListener.onUserInteraction();
            //mEventListener.onPatternCellAdded(mPattern);
        }
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

        if (mPatternDrawLookup[rowHit][columnHit]) {
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

        float offset = mPaddingTop + (squareHeight - hitSize) / 2f;
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

        float offset = mPaddingLeft + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        if (mState != State.Record) {
            return false;
        }

        final float x = motionEvent.getX();
        final float y = motionEvent.getY();
        Cell hitCell;
        switch(motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                resetPattern();
                hitCell = detectAndAddHit(x, y);
                if (hitCell != null && mEventListener != null) {
                    mEventListener.onPatternStart();
                } else if (mEventListener != null) {
                    mEventListener.onPatternCleared();
                }
                if (hitCell != null) {
                    final float startX = getCenterXForColumn(hitCell.column);
                    final float startY = getCenterYForRow(hitCell.row);

                    final float widthOffset = mSquareWidth / 2f;
                    final float heightOffset = mSquareHeight / 2f;

                    invalidate((int) (startX - widthOffset), (int) (startY - heightOffset),
                            (int) (startX + widthOffset), (int) (startY + heightOffset));
                }
                mInProgressX = x;
                mInProgressY = y;
                if (PROFILE_DRAWING) {
                    if (!mDrawingProfilingStarted) {
                        Debug.startMethodTracing("LockPatternDrawing");
                        mDrawingProfilingStarted = true;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                // report pattern detected
                if (!mPattern.isEmpty() && mEventListener != null) {
                    mEventListener.onPatternDetected(mPattern);
                    invalidate();
                }
                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                final int patternSizePreHitDetect = mPattern.size();
                hitCell = detectAndAddHit(x, y);
                final int patternSize = mPattern.size();
                if (hitCell != null && (mEventListener != null) && (patternSize == 1)) {
                    mEventListener.onPatternStart();
                }
                // note current x and y for rubber banding of in progress
                // patterns
                final float dx = Math.abs(x - mInProgressX);
                final float dy = Math.abs(y - mInProgressY);
                if (dx + dy > mSquareWidth * 0.01f) {
                    float oldX = mInProgressX;
                    float oldY = mInProgressY;

                    mInProgressX = x;
                    mInProgressY = y;

                    if (mState == State.Record && patternSize > 0) {
                        final ArrayList<Cell> pattern = mPattern;
                        final float radius = mSquareWidth * mDiameterFactor * 0.5f;

                        final Cell lastCell = pattern.get(patternSize - 1);

                        float startX = getCenterXForColumn(lastCell.column);
                        float startY = getCenterYForRow(lastCell.row);

                        float left;
                        float top;
                        float right;
                        float bottom;

                        final Rect invalidateRect = mInvalidate;

                        if (startX < x) {
                            left = startX;
                            right = x;
                        } else {
                            left = x;
                            right = startX;
                        }

                        if (startY < y) {
                            top = startY;
                            bottom = y;
                        } else {
                            top = y;
                            bottom = startY;
                        }

                        // Invalidate between the pattern's last cell and the current location
                        invalidateRect.set((int) (left - radius), (int) (top - radius),
                                (int) (right + radius), (int) (bottom + radius));

                        if (startX < oldX) {
                            left = startX;
                            right = oldX;
                        } else {
                            left = oldX;
                            right = startX;
                        }

                        if (startY < oldY) {
                            top = startY;
                            bottom = oldY;
                        } else {
                            top = oldY;
                            bottom = startY;
                        }

                        // Invalidate between the pattern's last cell and the previous location
                        invalidateRect.union((int) (left - radius), (int) (top - radius),
                                (int) (right + radius), (int) (bottom + radius));

                        // Invalidate between the pattern's new cell and the pattern's previous cell
                        if (hitCell != null) {
                            startX = getCenterXForColumn(hitCell.column);
                            startY = getCenterYForRow(hitCell.row);

                            if (patternSize >= 2) {
                                // (re-using hitcell for old cell)
                                hitCell = pattern.get(patternSize - 1 - (patternSize - patternSizePreHitDetect));
                                oldX = getCenterXForColumn(hitCell.column);
                                oldY = getCenterYForRow(hitCell.row);

                                if (startX < oldX) {
                                    left = startX;
                                    right = oldX;
                                } else {
                                    left = oldX;
                                    right = startX;
                                }

                                if (startY < oldY) {
                                    top = startY;
                                    bottom = oldY;
                                } else {
                                    top = oldY;
                                    bottom = startY;
                                }
                            } else {
                                left = right = startX;
                                top = bottom = startY;
                            }

                            final float widthOffset = mSquareWidth / 2f;
                            final float heightOffset = mSquareHeight / 2f;

                            invalidateRect.set((int) (left - widthOffset),
                                    (int) (top - heightOffset), (int) (right + widthOffset),
                                    (int) (bottom + heightOffset));
                        }

                        invalidate(invalidateRect);
                    } else {
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetPattern();
                if (mEventListener != null) {
                    mEventListener.onPatternCleared();
                }
                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    private float getCenterXForColumn(int column) {
        return mPaddingLeft + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return mPaddingTop + row * mSquareHeight + mSquareHeight / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;

        if (mState == State.Replay) {

            // figure out which circles to draw

            // + 1 so we pause on complete pattern
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() -
                    mAnimatingPeriodStart) % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Cell cell = pattern.get(i);
                drawLookup[cell.getRow()][cell.getColumn()] = true;
            }

            // figure out in progress portion of ghosting line

            final boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < count;

            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle =
                        ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING)) /
                                MILLIS_PER_CIRCLE_ANIMATING;

                final Cell currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.column);
                final float centerY = getCenterYForRow(currentCell.row);

                final Cell nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle *
                        (getCenterXForColumn(nextCell.column) - centerX);
                final float dy = percentageOfNextCircle *
                        (getCenterYForRow(nextCell.row) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            // TODO: Infinite loop here...
            invalidate();
        }

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        float radius = (squareWidth * mDiameterFactor * 0.5f);
        mPathPaint.setStrokeWidth(radius);

        final Path currentPath = mCurrentPath;
        currentPath.rewind();

        // TODO: the path should be created and cached every time we hit-detect a cell
        // only the last segment of the path should be computed here
        // draw the path of the pattern (unless the user is in progress, and
        // we are in stealth mode)
        final boolean drawPath = (!mInStealthMode || mState == State.Incorrect);
        if (drawPath) {
            boolean anyCircles = false;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);

                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[cell.row][cell.column]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(cell.column);
                float centerY = getCenterYForRow(cell.row);
                if (i == 0) {
                    currentPath.moveTo(centerX, centerY);
                } else {
                    currentPath.lineTo(centerX, centerY);
                }
            }

            // add last in progress section
            if ((mState == State.Record || mState == State.Replay)
                    && anyCircles) {
                currentPath.lineTo(mInProgressX, mInProgressY);
            }
            canvas.drawPath(currentPath, mPathPaint);
        }

        // draw the circles
        final int paddingTop = mPaddingTop;
        final int paddingLeft = mPaddingLeft;

        for (int i = 0; i < 3; i++) {
            float topY = paddingTop + i * squareHeight;
            //float centerY = mPaddingTop + i * mSquareHeight + (mSquareHeight / 2);
            for (int j = 0; j < 3; j++) {
                float leftX = paddingLeft + j * squareWidth;
                drawCircle(canvas, (int) leftX, (int) topY, drawLookup[i][j]);
            }
        }

        // draw the arrows associated with the path (unless the user is in progress, and
        // we are in stealth mode)
        boolean oldFlag = (mPaint.getFlags() & Paint.FILTER_BITMAP_FLAG) != 0;
        mPaint.setFilterBitmap(true); // draw with higher quality since we render with transforms
        if (drawPath) {
            for (int i = 0; i < count - 1; i++) {
                Cell cell = pattern.get(i);
                Cell next = pattern.get(i + 1);

                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[next.row][next.column]) {
                    break;
                }

                float leftX = paddingLeft + cell.column * squareWidth;
                float topY = paddingTop + cell.row * squareHeight;

                drawArrow(canvas, leftX, topY, cell, next);
            }
        }
        mPaint.setFilterBitmap(oldFlag); // restore default flag
    }

    private void drawArrow(Canvas canvas, float leftX, float topY, Cell start, Cell end) {
        boolean green = mState != State.Incorrect;

        final int endRow = end.row;
        final int startRow = start.row;
        final int endColumn = end.column;
        final int startColumn = start.column;

        // offsets for centering the bitmap in the cell
        final int offsetX = ((int) mSquareWidth - mBitmapWidth) / 2;
        final int offsetY = ((int) mSquareHeight - mBitmapHeight) / 2;

        // compute transform to place arrow bitmaps at correct angle inside circle.
        // This assumes that the arrow image is drawn at 12:00 with it's top edge
        // coincident with the circle bitmap's top edge.
        Bitmap arrow = green ? mBitmapArrowGreenUp : mBitmapArrowRedUp;
        Matrix matrix = new Matrix();
        final int cellWidth = mBitmapCircleDefault.getWidth();
        final int cellHeight = mBitmapCircleDefault.getHeight();

        // the up arrow bitmap is at 12:00, so find the rotation from x axis and add 90 degrees.
        final float theta = (float) Math.atan2(
                (double) (endRow - startRow), (double) (endColumn - startColumn));
        final float angle = (float) Math.toDegrees(theta) + 90.0f;

        // compose matrix
        matrix.setTranslate(leftX + offsetX, topY + offsetY); // transform to cell position
        matrix.preRotate(angle, cellWidth / 2.0f, cellHeight / 2.0f);  // rotate about cell center
        matrix.preTranslate((cellWidth - arrow.getWidth()) / 2.0f, 0.0f); // translate to 12:00 pos
        canvas.drawBitmap(arrow, matrix, mPaint);
    }

    /**
     * @param canvas
     * @param leftX
     * @param topY
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawCircle(Canvas canvas, int leftX, int topY, boolean partOfPattern) {
        Bitmap outerCircle;
        Bitmap innerCircle;

        if (!partOfPattern || (mInStealthMode && mState != State.Incorrect)) {
            // unselected circle
            outerCircle = mBitmapCircleDefault;
            innerCircle = mBitmapBtnDefault;
        } else if (mState == State.Record) {
            // user is in middle of drawing a pattern
            outerCircle = mBitmapCircleGreen;
            innerCircle = mBitmapBtnTouched;
        } else if (mState == State.Incorrect) {
            // the pattern is wrong
            outerCircle = mBitmapCircleRed;
            innerCircle = mBitmapBtnDefault;
        } else if (mState == State.Correct ||
                mState == State.Replay) {
            // the pattern is correct
            outerCircle = mBitmapCircleGreen;
            innerCircle = mBitmapBtnDefault;
        } else {
            throw new IllegalStateException("unknown display mode " + mState);
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

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LockPatternUtils.patternToString(mPattern),
                mState.ordinal(),
                mInputEnabled, mInStealthMode, mTactileFeedbackEnabled);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        DragLock.this.setPattern(
                State.Correct,
                LockPatternUtils.stringToPattern(ss.getSerializedPattern()));
        mState = State.values()[ss.getState()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mTactileFeedbackEnabled = ss.isTactileFeedbackEnabled();
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mState;
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
            mState = displayMode;
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
            mState = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getState() {
            return mState;
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

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mState);
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
