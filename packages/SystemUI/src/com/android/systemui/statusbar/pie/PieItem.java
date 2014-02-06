/*
 * Copyright (C) 2014 SlimRoms Project
 * This code is loosely based on portions of the CyanogenMod Project (Jens Doll) Copyright (C) 2013
 * and the ParanoidAndroid Project source, Copyright (C) 2012.
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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;

import com.android.internal.util.gesture.EdgeGesturePosition;
import com.android.internal.util.slim.ImageHelper;

import com.android.systemui.R;
import com.android.systemui.statusbar.pie.PieView.PieDrawable;

/**
 * A clickable pie menu item.
 * <p>
 * This is the actual end point for user interaction.<br>
 * ( == This is what a user clicks on.)
 */
public class PieItem extends PieView.PieDrawable {

    private PieView mPieView;
    private Context mContext;

    private Paint mBackgroundPaint = new Paint();
    private Paint mSelectedPaint = new Paint();
    private Paint mOutlinePaint = new Paint();
    private Paint mLongPressPaint = new Paint();

    private View mView;
    private Path mPath;
    private int mPieIconType;

    public final int width;
    public final String tag;
    public final String longTag;

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

    public PieItem(Context context, PieView parent, int flags, int width, String tag,
                String longTag, View view, int iconType) {
        mContext = context;
        mView = view;
        mPieView = parent;
        mPieIconType = iconType;
        this.tag = tag;
        this.longTag = longTag;
        this.width = width;
        this.flags = flags | PieDrawable.VISIBLE | PieDrawable.DISPLAY_ALL;

        final Resources res = context.getResources();

        float backgroundAlpha = Settings.System.getFloatForUser(context.getContentResolver(),
                Settings.System.PIE_BUTTON_ALPHA, 0.3f,
                UserHandle.USER_CURRENT);
        float backgroundSelectedAlpha = Settings.System.getFloatForUser(
                context.getContentResolver(),
                Settings.System.PIE_BUTTON_PRESSED_ALPHA, 0.0f,
                UserHandle.USER_CURRENT);

        int backgroundPaintColor = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.PIE_BUTTON_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (backgroundPaintColor == -2) {
            backgroundPaintColor = res.getColor(R.color.pie_background_color);
        }
        mBackgroundPaint.setColor(stripAlpha(backgroundPaintColor));
        mBackgroundPaint.setAlpha((int) ((1-backgroundAlpha) * 255));
        mBackgroundPaint.setAntiAlias(true);

        int selectedPaintColor = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.PIE_BUTTON_PRESSED_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (selectedPaintColor == -2) {
            selectedPaintColor = res.getColor(R.color.pie_selected_color);
        }
        mSelectedPaint.setColor(stripAlpha(selectedPaintColor));
        mSelectedPaint.setAlpha((int) ((1-backgroundSelectedAlpha) * 255));
        mSelectedPaint.setAntiAlias(true);

        int longPressPaintColor = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.PIE_BUTTON_LONG_PRESSED_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (longPressPaintColor == -2) {
            longPressPaintColor = res.getColor(R.color.pie_long_pressed_color);
        }
        mLongPressPaint.setColor(stripAlpha(longPressPaintColor));
        mLongPressPaint.setAlpha((int) ((1-backgroundSelectedAlpha) * 255));
        mLongPressPaint.setAntiAlias(true);

        int outlinePaintColor = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.PIE_BUTTON_OUTLINE_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (outlinePaintColor == -2) {
            outlinePaintColor = res.getColor(R.color.pie_outline_color);
        }
        mOutlinePaint.setColor(outlinePaintColor);
        mOutlinePaint.setAlpha((int) ((1-backgroundAlpha) * 255));
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
            flags |= PieView.PieDrawable.VISIBLE;
        } else {
            flags &= ~PieView.PieDrawable.VISIBLE;
        }
    }

    public void setSelected(boolean selected, boolean longPressed) {
        mPieView.postInvalidate();
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
            ImageView imageView = (ImageView) mView;
            imageView.setImageDrawable(drawable);
        }
    }

    public void setColor(int color) {
        if (mView instanceof ImageView) {
            ImageView imageView = (ImageView) mView;
            Drawable drawable = imageView.getDrawable();
            if (drawable == null) {
                return;
            }

            int drawableColorMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.PIE_ICON_COLOR_MODE, 0,
                    UserHandle.USER_CURRENT);
            int drawableColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.PIE_ICON_COLOR, -2,
                    UserHandle.USER_CURRENT);
            if (drawableColor == -2) {
                drawableColor = color;
            }

            boolean colorize = true;
            if (mPieIconType == 2 && drawableColorMode == 1
                    || mPieIconType == 1 && drawableColorMode != 0) {
                colorize = false;
            }

            if (colorize && drawableColorMode != 3) {
                imageView.setImageBitmap(
                        ImageHelper.getColoredBitmap(drawable, drawableColor));
            } else {
                imageView.setImageDrawable(drawable);
            }
        }
    }

    @Override
    public void prepare(EdgeGesturePosition position, float scale, boolean mirrorRightPie) {
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
    public void draw(Canvas canvas, EdgeGesturePosition position) {
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
            // Keep icons "upright" if we get displayed on TOP EdgeGesturePosition
            if (position != EdgeGesturePosition.TOP) {
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

    private static int stripAlpha(int color){
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }
}
