
package com.android.systemui.recent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;

public class LinearColorBar extends LinearLayout {
    static final int LEFT_COLOR = 0xff0099cc;
    static final int MIDDLE_COLOR = 0xff0099cc;
    static final int RIGHT_COLOR = 0xff888888;

    private float mRedRatio;
    private float mYellowRatio;
    private float mGreenRatio;

    private boolean mShowingGreen;

    final Rect mRect = new Rect();
    final Paint mPaint = new Paint();

    int mLastInterestingLeft, mLastInterestingRight;
    int mLineWidth;

    final Path mColorPath = new Path();
    final Path mEdgePath = new Path();
    final Paint mColorGradientPaint = new Paint();
    final Paint mEdgeGradientPaint = new Paint();

    public LinearColorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setAntiAlias(true);
        mEdgeGradientPaint.setStyle(Paint.Style.STROKE);
        mLineWidth = getResources().getDisplayMetrics().densityDpi >= DisplayMetrics.DENSITY_HIGH
                ? 2 : 1;
        mEdgeGradientPaint.setStrokeWidth(mLineWidth);
        mEdgeGradientPaint.setAntiAlias(true);
        
    }

    public void setRatios(float red, float yellow, float green) {
        mRedRatio = red;
        mYellowRatio = yellow;
        mGreenRatio = green;
        invalidate();
    }

    public void setShowingGreen(boolean showingGreen) {
        if (mShowingGreen != showingGreen) {
            mShowingGreen = showingGreen;
            updateIndicator();
            invalidate();
        }
    }

    private void updateIndicator() {
        int off = getPaddingTop() - getPaddingBottom();
        if (off < 0) off = 0;
        mRect.top = off;
        mRect.bottom = getHeight();
        if (mShowingGreen) {
            mColorGradientPaint.setShader(new LinearGradient(
                    0, 0, 0, off-2, RIGHT_COLOR&0xffffff, RIGHT_COLOR, Shader.TileMode.CLAMP));
        } else {
            mColorGradientPaint.setShader(new LinearGradient(
                    0, 0, 0, off-2, MIDDLE_COLOR&0xffffff, MIDDLE_COLOR, Shader.TileMode.CLAMP));
        }
        mEdgeGradientPaint.setShader(new LinearGradient(
                0, 0, 0, off/2, 0x00a0a0a0, 0xffa0a0a0, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateIndicator();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();

        int left = 0;

        int right = left + (int)(width*mRedRatio);
        int right2 = right + (int)(width*mYellowRatio);
        int right3 = right2 + (int)(width*mGreenRatio);

        int indicatorLeft, indicatorRight;
        if (mShowingGreen) {
            indicatorLeft = right2;
            indicatorRight = right3;
        } else {
            indicatorLeft = right;
            indicatorRight = right2;
        }

        if (mLastInterestingLeft != indicatorLeft || mLastInterestingRight != indicatorRight) {
            mColorPath.reset();
            mEdgePath.reset();
            if (indicatorLeft < indicatorRight) {
                final int midTopY = mRect.top;
                final int midBottomY = 0;
                final int xoff = 2;
                mColorPath.moveTo(indicatorLeft, mRect.top);
                mColorPath.cubicTo(indicatorLeft, midBottomY,
                        -xoff, midTopY,
                        -xoff, 0);
                mColorPath.lineTo(width+xoff-1, 0);
                mColorPath.cubicTo(width+xoff-1, midTopY,
                        indicatorRight, midBottomY,
                        indicatorRight, mRect.top);
                mColorPath.close();
                final float lineOffset = mLineWidth+.5f;
                mEdgePath.moveTo(-xoff+lineOffset, 0);
                mEdgePath.cubicTo(-xoff+lineOffset, midTopY,
                        indicatorLeft+lineOffset, midBottomY,
                        indicatorLeft+lineOffset, mRect.top);
                mEdgePath.moveTo(width+xoff-1-lineOffset, 0);
                mEdgePath.cubicTo(width+xoff-1-lineOffset, midTopY,
                        indicatorRight-lineOffset, midBottomY,
                        indicatorRight-lineOffset, mRect.top);
            }
            mLastInterestingLeft = indicatorLeft;
            mLastInterestingRight = indicatorRight;
        }

        if (!mEdgePath.isEmpty()) {
            canvas.drawPath(mEdgePath, mEdgeGradientPaint);
            canvas.drawPath(mColorPath, mColorGradientPaint);
        }

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(LEFT_COLOR);
            canvas.drawRect(mRect, mPaint);
            width -= (right-left);
            left = right;
        }

        right = right2;

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(MIDDLE_COLOR);
            canvas.drawRect(mRect, mPaint);
            width -= (right-left);
            left = right;
        }


        right = left + width;
        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(RIGHT_COLOR);
            canvas.drawRect(mRect, mPaint);
        }
    }
}