/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.accessibility.CaptioningManager.CaptionStyle;

public class SubtitleView extends View {
    // Ratio of inner padding to font size.
    private static final float INNER_PADDING_RATIO = 0.125f;

    // Styled dimensions.
    private final float mCornerRadius;
    private final float mOutlineWidth;
    private final float mShadowRadius;
    private final float mShadowOffsetX;
    private final float mShadowOffsetY;

    /** Temporary rectangle used for computing line bounds. */
    private final RectF mLineBounds = new RectF();

    /** Reusable string builder used for holding text. */
    private final StringBuilder mText = new StringBuilder();

    private Alignment mAlignment;
    private TextPaint mTextPaint;
    private Paint mPaint;

    private int mForegroundColor;
    private int mBackgroundColor;
    private int mEdgeColor;
    private int mEdgeType;

    private boolean mHasMeasurements;
    private int mLastMeasuredWidth;
    private StaticLayout mLayout;

    private float mSpacingMult = 1;
    private float mSpacingAdd = 0;
    private int mInnerPaddingX = 0;

    public SubtitleView(Context context) {
        this(context, null);
    }

    public SubtitleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtitleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        final Theme theme = context.getTheme();
        final TypedArray a = theme.obtainStyledAttributes(
                    attrs, android.R.styleable.TextView, defStyle, 0);

        CharSequence text = "";
        int textSize = 15;

        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
                case android.R.styleable.TextView_text:
                    text = a.getText(attr);
                    break;
                case android.R.styleable.TextView_lineSpacingExtra:
                    mSpacingAdd = a.getDimensionPixelSize(attr, (int) mSpacingAdd);
                    break;
                case android.R.styleable.TextView_lineSpacingMultiplier:
                    mSpacingMult = a.getFloat(attr, mSpacingMult);
                    break;
                case android.R.styleable.TextAppearance_textSize:
                    textSize = a.getDimensionPixelSize(attr, textSize);
                    break;
            }
        }

        // Set up density-dependent properties.
        // TODO: Move these to a default style.
        final Resources res = getContext().getResources();
        final DisplayMetrics m = res.getDisplayMetrics();
        mCornerRadius = res.getDimensionPixelSize(com.android.internal.R.dimen.subtitle_corner_radius);
        mOutlineWidth = res.getDimensionPixelSize(com.android.internal.R.dimen.subtitle_outline_width);
        mShadowRadius = res.getDimensionPixelSize(com.android.internal.R.dimen.subtitle_shadow_radius);
        mShadowOffsetX = res.getDimensionPixelSize(com.android.internal.R.dimen.subtitle_shadow_offset);
        mShadowOffsetY = mShadowOffsetX;

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setSubpixelText(true);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        setText(text);
        setTextSize(textSize);
    }

    public void setText(int resId) {
        final CharSequence text = getContext().getText(resId);
        setText(text);
    }

    public void setText(CharSequence text) {
        mText.setLength(0);
        mText.append(text);

        mHasMeasurements = false;

        requestLayout();
    }

    public void setForegroundColor(int color) {
        mForegroundColor = color;

        invalidate();
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;

        invalidate();
    }

    public void setEdgeType(int edgeType) {
        mEdgeType = edgeType;

        invalidate();
    }

    public void setEdgeColor(int color) {
        mEdgeColor = color;

        invalidate();
    }

    /**
     * Sets the text size in pixels.
     *
     * @param size the text size in pixels
     */
    public void setTextSize(float size) {
        if (mTextPaint.getTextSize() != size) {
            mTextPaint.setTextSize(size);
            mInnerPaddingX = (int) (size * INNER_PADDING_RATIO + 0.5f);

            mHasMeasurements = false;

            requestLayout();
            invalidate();
        }
    }

    public void setTypeface(Typeface typeface) {
        if (mTextPaint.getTypeface() != typeface) {
            mTextPaint.setTypeface(typeface);

            mHasMeasurements = false;

            requestLayout();
            invalidate();
        }
    }

    public void setAlignment(Alignment textAlignment) {
        if (mAlignment != textAlignment) {
            mAlignment = textAlignment;

            mHasMeasurements = false;

            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSpec = MeasureSpec.getSize(widthMeasureSpec);

        if (computeMeasurements(widthSpec)) {
            final StaticLayout layout = mLayout;

            // Account for padding.
            final int paddingX = mPaddingLeft + mPaddingRight + mInnerPaddingX * 2;
            final int width = layout.getWidth() + paddingX;
            final int height = layout.getHeight() + mPaddingTop + mPaddingBottom;
            setMeasuredDimension(width, height);
        } else {
            setMeasuredDimension(MEASURED_STATE_TOO_SMALL, MEASURED_STATE_TOO_SMALL);
        }
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;

        computeMeasurements(width);
    }

    private boolean computeMeasurements(int maxWidth) {
        if (mHasMeasurements && maxWidth == mLastMeasuredWidth) {
            return true;
        }

        // Account for padding.
        final int paddingX = mPaddingLeft + mPaddingRight + mInnerPaddingX * 2;
        maxWidth -= paddingX;
        if (maxWidth <= 0) {
            return false;
        }

        // TODO: Implement minimum-difference line wrapping. Adding the results
        // of Paint.getTextWidths() seems to return different values than
        // StaticLayout.getWidth(), so this is non-trivial.
        mHasMeasurements = true;
        mLastMeasuredWidth = maxWidth;
        mLayout = new StaticLayout(
                mText, mTextPaint, maxWidth, mAlignment, mSpacingMult, mSpacingAdd, true);

        return true;
    }

    public void setStyle(int styleId) {
        final Context context = mContext;
        final ContentResolver cr = context.getContentResolver();
        final CaptionStyle style;
        if (styleId == CaptionStyle.PRESET_CUSTOM) {
            style = CaptionStyle.getCustomStyle(cr);
        } else {
            style = CaptionStyle.PRESETS[styleId];
        }

        mForegroundColor = style.foregroundColor;
        mBackgroundColor = style.backgroundColor;
        mEdgeType = style.edgeType;
        mEdgeColor = style.edgeColor;
        mHasMeasurements = false;

        final Typeface typeface = style.getTypeface();
        setTypeface(typeface);

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas c) {
        final StaticLayout layout = mLayout;
        if (layout == null) {
            return;
        }

        final int saveCount = c.save();
        final int innerPaddingX = mInnerPaddingX;
        c.translate(mPaddingLeft + innerPaddingX, mPaddingTop);

        final int lineCount = layout.getLineCount();
        final Paint textPaint = mTextPaint;
        final Paint paint = mPaint;
        final RectF bounds = mLineBounds;

        if (Color.alpha(mBackgroundColor) > 0) {
            final float cornerRadius = mCornerRadius;
            float previousBottom = layout.getLineTop(0);

            paint.setColor(mBackgroundColor);
            paint.setStyle(Style.FILL);

            for (int i = 0; i < lineCount; i++) {
                bounds.left = layout.getLineLeft(i) -innerPaddingX;
                bounds.right = layout.getLineRight(i) + innerPaddingX;
                bounds.top = previousBottom;
                bounds.bottom = layout.getLineBottom(i);
                previousBottom = bounds.bottom;

                c.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
            }
        }

        if (mEdgeType == CaptionStyle.EDGE_TYPE_OUTLINE) {
            textPaint.setStrokeJoin(Join.ROUND);
            textPaint.setStrokeWidth(mOutlineWidth);
            textPaint.setColor(mEdgeColor);
            textPaint.setStyle(Style.FILL_AND_STROKE);

            for (int i = 0; i < lineCount; i++) {
                layout.drawText(c, i, i);
            }
        } else if (mEdgeType == CaptionStyle.EDGE_TYPE_DROP_SHADOW) {
            textPaint.setShadowLayer(mShadowRadius, mShadowOffsetX, mShadowOffsetY, mEdgeColor);
        }

        textPaint.setColor(mForegroundColor);
        textPaint.setStyle(Style.FILL);

        for (int i = 0; i < lineCount; i++) {
            layout.drawText(c, i, i);
        }

        textPaint.setShadowLayer(0, 0, 0, 0);
        c.restoreToCount(saveCount);
    }
}
