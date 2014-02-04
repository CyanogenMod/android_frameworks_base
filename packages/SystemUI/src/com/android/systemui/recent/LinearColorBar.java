/**
 *
 */
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
    private static final int LEFT_COLOR = 0xffbebebe;
    private static final int MIDDLE_COLOR = 0xffbebebe;
    private static final int RIGHT_COLOR = 0xff888888;
    private static final int GRAY_COLOR = 0xff555555;
    private static final int WHITE_COLOR = 0xffffffff;

    private float mRedRatio;
    private float mYellowRatio;

    private int mLeftColor = LEFT_COLOR;
    private int mMiddleColor = MIDDLE_COLOR;
    private int mRightColor = RIGHT_COLOR;

    private int mColoredRegions = REGION_RED | REGION_YELLOW | REGION_GREEN;

    private final Rect mRect = new Rect();
    private final Paint mPaint = new Paint();

    private int mLastInterestingLeft, mLastInterestingRight;
    private int mLineWidth;

    private final Path mColorPath = new Path();
    private final Path mEdgePath = new Path();
    private final Paint mColorGradientPaint = new Paint();
    private final Paint mEdgeGradientPaint = new Paint();

    public static final int REGION_RED = 1 << 0;
    public static final int REGION_YELLOW = 1 << 1;
    public static final int REGION_GREEN = 1 << 2;

    public LinearColorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setAntiAlias(true);
        mEdgeGradientPaint.setStyle(Paint.Style.STROKE);
        mLineWidth = (getResources().getDisplayMetrics().densityDpi >= DisplayMetrics.DENSITY_HIGH)
                ? 2 : 1;
        mEdgeGradientPaint.setStrokeWidth(mLineWidth);
        mEdgeGradientPaint.setAntiAlias(true);

    }

    public void setRatios(float red, float yellow, float green) {
        mRedRatio = red;
        mYellowRatio = yellow;
        invalidate();
    }

    private void updateIndicator() {
        int off = getPaddingTop() - getPaddingBottom();
        if (off < 0) off = 0;
        mRect.top = off;
        mRect.bottom = getHeight();
        mEdgeGradientPaint.setShader(new LinearGradient(
                0, 0, 0, off / 2, 0x00a0a0a0, 0xffa0a0a0, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateIndicator();
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        invalidate();
    }

    private int pickColor(int color, int region) {
        if (isPressed()) {
            return WHITE_COLOR;
        }
        if ((mColoredRegions & region) == 0) {
            return GRAY_COLOR;
        }
        return color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();

        int left = 0;

        int right = left + (int) (width * mRedRatio);
        int right2 = right + (int) (width * mYellowRatio);

        if (mLastInterestingLeft != right || mLastInterestingRight != right2) {
            mColorPath.reset();
            mEdgePath.reset();
            if (right < right2) {
                final int midTopY = mRect.top;
                final int midBottomY = 0;
                final int xoff = 2;
                mColorPath.moveTo(right, mRect.top);
                mColorPath.cubicTo(right, midBottomY,
                        -xoff, midTopY,
                        -xoff, 0);
                mColorPath.lineTo(width + xoff - 1, 0);
                mColorPath.cubicTo(width + xoff - 1, midTopY,
                        right2, midBottomY,
                        right2, mRect.top);
                mColorPath.close();
                final float lineOffset = mLineWidth + .5f;
                mEdgePath.moveTo(-xoff + lineOffset, 0);
                mEdgePath.cubicTo(-xoff + lineOffset, midTopY,
                        right + lineOffset, midBottomY,
                        right + lineOffset, mRect.top);
                mEdgePath.moveTo(width + xoff - 1 - lineOffset, 0);
                mEdgePath.cubicTo(width + xoff - 1 - lineOffset, midTopY,
                        right2 - lineOffset, midBottomY,
                        right2 - lineOffset, mRect.top);
            }
            mLastInterestingLeft = right;
            mLastInterestingRight = right2;
        }

        if (!mEdgePath.isEmpty()) {
            canvas.drawPath(mEdgePath, mEdgeGradientPaint);
            canvas.drawPath(mColorPath, mColorGradientPaint);
        }

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(pickColor(mLeftColor, REGION_RED));
            canvas.drawRect(mRect, mPaint);
            width -= (right - left);
            left = right;
        }

        right = right2;

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(pickColor(mMiddleColor, REGION_YELLOW));
            canvas.drawRect(mRect, mPaint);
            width -= (right - left);
            left = right;
        }

        right = left + width;
        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(pickColor(mRightColor, REGION_GREEN));
            canvas.drawRect(mRect, mPaint);
        }
    }
}
