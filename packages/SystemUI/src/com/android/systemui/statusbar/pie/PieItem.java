/*
 * Copyright (C) 2013 The CyanogenMod Project
 * This code is loosely based on portions of the ParanoidAndroid Project source, Copyright (C) 2012.
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.pie.PieLayout.PieDrawable;
import com.android.systemui.statusbar.policy.PieController.Position;

/**
 * A clickable pie menu item.
 * <p>
 * This is the actual end point for user interaction.<br>
 * ( == This is what a user clicks on.)
 */
public class PieItem extends PieLayout.PieDrawable {

    private PieLayout mPieLayout;

    private Paint mBackgroundPaint = new Paint();
    private Paint mSelectedPaint = new Paint();
    private Paint mLongPressPaint = new Paint();
    private Paint mOutlinePaint = new Paint();

    private View mView;
    private Path mPath;

    public final int width;
    public final Object tag;

    /**
     * The gap between two pie items. This more like a padding on both sides of the item.
     */
    protected float mGap = 0.0f;

    /**
     * Does what it says ;)
     */
    public interface PieOnClickListener {
        /**
         * @param item is the item that was "clicked" by the user.
         */
        public void onClick(PieItem item);
    }
    private PieOnClickListener mOnClickListener = null;

    public interface PieOnLongClickListener {
        public void onLongClick(PieItem item);
    }
    private PieOnLongClickListener mOnLongClickListener = null;

    /**
     * The item is selected / has the focus from the gesture.
     */
    public final static int SELECTED = 0x100;
    /**
     * The item was long pressed.
     */
    public final static int LONG_PRESSED = 0x200;
    /**
     * The item can be long pressed.
     */
    public final static int CAN_LONG_PRESS = 0x400;

    public PieItem(Context context, PieLayout parent, int flags, int width, Object tag, View view) {
        mView = view;
        mPieLayout = parent;
        this.tag = tag;
        this.width = width;
        this.flags = flags | PieDrawable.VISIBLE | PieDrawable.DISPLAY_ALL;

        final Resources res = context.getResources();

        mBackgroundPaint.setColor(res.getColor(R.color.pie_background_color));
        mBackgroundPaint.setAntiAlias(true);
        mSelectedPaint.setColor(res.getColor(R.color.pie_selected_color));
        mSelectedPaint.setAntiAlias(true);
        mLongPressPaint.setColor(res.getColor(R.color.pie_long_pressed_color));
        mLongPressPaint.setAntiAlias(true);
        mOutlinePaint.setColor(res.getColor(R.color.pie_outline_color));
        mOutlinePaint.setAntiAlias(true);
        mOutlinePaint.setStyle(Style.STROKE);
        mOutlinePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.pie_outline));

        setColor(res.getColor(R.color.pie_foreground_color));
    }

    public void setGap(float gap) {
        mGap = gap;
    }

    public void setOnClickListener(PieOnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    public void setOnLongClickListener(PieOnLongClickListener onLongClickListener) {
        mOnLongClickListener = onLongClickListener;
        if (onLongClickListener != null) {
            flags |= CAN_LONG_PRESS;
        } else {
            flags &= ~CAN_LONG_PRESS;
        }
    }

    public void show(boolean show) {
        if (show) {
            flags |= PieLayout.PieDrawable.VISIBLE;
        } else {
            flags &= ~PieLayout.PieDrawable.VISIBLE;
        }
    }

    public void setSelected(boolean selected, boolean longPressed) {
        mPieLayout.postInvalidate();
        longPressed = longPressed & (flags & CAN_LONG_PRESS) != 0;
        if (selected) {
            flags |= longPressed ? SELECTED | LONG_PRESSED : SELECTED;
        } else {
            flags &= ~SELECTED & ~LONG_PRESSED;
        }
    }

    public void setAlpha(float alpha) {
        if (mView != null) {
            mView.setAlpha(alpha);
        }
    }

    public void setImageDrawable(Drawable drawable) {
        if (mView instanceof ImageView) {
            ImageView imageView = (ImageView)mView;
            imageView.setImageDrawable(drawable);
        }
    }

    public void setColor(int color) {
        if (mView instanceof ImageView) {
            ImageView imageView = (ImageView)mView;
            Drawable drawable = imageView.getDrawable();
            drawable.setColorFilter(color, Mode.SRC_ATOP);
            imageView.setImageDrawable(drawable);
        }
    }

    @Override
    public void prepare(Position position, float scale) {
        mPath = getOutline(scale);
        if (mView != null) {
            mView.measure(mView.getLayoutParams().width, mView.getLayoutParams().height);
            final int w = mView.getMeasuredWidth();
            final int h = mView.getMeasuredHeight();

            final float radius = (h * 1.3f < mOuter - mInner)
                    ? mInner + (mOuter - mInner) * 2.0f / 3.0f
                    : (mInner + mOuter) / 2.0f;

            double rad = Math.toRadians(mStart + mSweep / 2);
            int l = (int) (Math.cos(rad) * radius * scale);
            int t = (int) (Math.sin(rad) * radius * scale);

            mView.layout(l, t, l + w, t + h);
        }
    }

    @Override
    public void draw(Canvas canvas, Position position) {
        if ((flags & SELECTED) != 0) {
            Paint paint = (flags & LONG_PRESSED) == 0
                    ? mSelectedPaint : mLongPressPaint;
            canvas.drawPath(mPath, paint);
        } else {
            canvas.drawPath(mPath, mBackgroundPaint);
            canvas.drawPath(mPath, mOutlinePaint);
        }

        if (mView != null) {
            int state = canvas.save();
            canvas.translate(mView.getLeft(), mView.getTop());
            // keep icons "upright" if we get displayed on TOP position
            if (position != Position.TOP) {
                canvas.rotate(mStart + mSweep / 2 - 270);
            } else {
                canvas.rotate(mStart + mSweep / 2 - 90);
            }
            canvas.translate(-mView.getWidth() / 2, -mView.getHeight() / 2);

            mView.draw(canvas);
            canvas.restoreToCount(state);
        }
    }

    @Override
    public PieItem interact(float alpha, int radius) {
        if (hit(alpha, radius)) {
            return this;
        }
        return null;
    }

    /* package */ void onClickCall(boolean longPressed) {
        if (!longPressed) {
            if (mOnClickListener != null) {
                mOnClickListener.onClick(this);
            }
        } else {
            if (mOnLongClickListener != null) {
                mOnLongClickListener.onLongClick(this);
            }
        }
    }

    private boolean hit(float alpha, int radius) {
        return (alpha > mStart) && (alpha < mStart + mSweep)
                && (radius > mInner && radius < mOuter);
    }

    private Path getOutline(float scale) {
        RectF outerBB = new RectF(-mOuter * scale, -mOuter * scale, mOuter * scale, mOuter * scale);
        RectF innerBB = new RectF(-mInner * scale, -mInner * scale, mInner * scale, mInner * scale);

        double gamma = (mInner + mOuter) * Math.sin(Math.toRadians(mGap / 2.0f));
        float alphaOuter = (float) Math.toDegrees(Math.asin( gamma / (mOuter * 2.0f)));
        float alphaInner = (float) Math.toDegrees(Math.asin( gamma / (mInner * 2.0f)));

        Path path = new Path();
        path.arcTo(outerBB, mStart + alphaOuter, mSweep - 2 * alphaOuter, true);
        path.arcTo(innerBB, mStart + mSweep - alphaInner, 2 * alphaInner - mSweep);
        path.close();

        return path;
    }
}
