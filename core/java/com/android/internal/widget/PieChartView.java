/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 * Pie chart with multiple items.
 */
public class PieChartView extends View {
    public static final String TAG = "PieChartView";
    public static final boolean LOGD = false;

    private static final boolean FILL_GRADIENT = false;

    private ArrayList<Slice> mSlices = Lists.newArrayList();

    private int mOriginAngle;

    private Paint mPaintOutline = new Paint();

    private Path mPathOutline = new Path();

    public class Slice {
        public long value;

        public Path path = new Path();
        public Path pathOutline = new Path();
        public Paint paint;

        public Slice(long value, int color) {
            this.value = value;
            this.paint = buildFillPaint(color, getResources());
        }
    }

    public PieChartView(Context context) {
        this(context, null);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPaintOutline.setColor(Color.BLACK);
        mPaintOutline.setStyle(Style.STROKE);
        mPaintOutline.setStrokeWidth(1f * getResources().getDisplayMetrics().density);
        mPaintOutline.setAntiAlias(true);

        setWillNotDraw(false);
    }

    private static Paint buildFillPaint(int color, Resources res) {
        final Paint paint = new Paint();

        paint.setColor(color);
        paint.setStyle(Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        if (FILL_GRADIENT) {
            final int width = (int) (280 * res.getDisplayMetrics().density);
            paint.setShader(new RadialGradient(0, 0, width, color, darken(color), TileMode.MIRROR));
        }

        return paint;
    }

    public void setOriginAngle(int originAngle) {
        mOriginAngle = originAngle;
    }

    public void addSlice(long value, int color) {
        mSlices.add(new Slice(value, color));
    }

    public void removeAllSlices() {
        mSlices.clear();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        generatePath();
    }

    public void generatePath() {
        if (LOGD) Log.d(TAG, "generatePath()");

        long total = 0;
        for (Slice slice : mSlices) {
            slice.path.reset();
            slice.pathOutline.reset();
            total += slice.value;
        }

        mPathOutline.reset();

        // bail when not enough stats to render
        if (total == 0) {
            invalidate();
            return;
        }

        final int width = getWidth();
        final int height = getHeight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        final int paddingStart = getPaddingStart();
        final int paddingEnd = getPaddingEnd();
        final int actualWidth = width - (paddingStart + paddingEnd);
        final int actualHeight = height - (paddingTop + paddingBottom);

        // NOTE: Is it really right to use paddingStart as the "left" parameter?
        final RectF rect = new RectF(paddingStart, paddingTop, actualWidth, actualHeight);
        mPathOutline.addOval(rect, Direction.CW);

        int startAngle = mOriginAngle;
        for (Slice slice : mSlices) {
            final int sweepAngle = (int) (slice.value * 360 / total);

            // draw slice
            slice.path.moveTo(rect.centerX(), rect.centerY());
            slice.path.arcTo(rect, startAngle, sweepAngle);
            slice.path.lineTo(rect.centerX(), rect.centerY());

            // draw slice outline
            slice.pathOutline.moveTo(rect.centerX(), rect.centerY());
            slice.pathOutline.arcTo(rect, startAngle + sweepAngle, 0);
            slice.pathOutline.lineTo(rect.centerX(), rect.centerY());

            startAngle += sweepAngle;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        for (Slice slice : mSlices) {
            canvas.drawPath(slice.path, slice.paint);
            canvas.drawPath(slice.pathOutline, mPaintOutline);
        }
        canvas.drawPath(mPathOutline, mPaintOutline);
    }

    public static int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] /= 2;
        hsv[1] /= 2;
        return Color.HSVToColor(hsv);
    }

}
