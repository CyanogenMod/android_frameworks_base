/*
 * Copyright (C) 2014 The OmniRom Project
 * This code has been modified. Portions copyright (C) 2014, OmniRom Project.
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
package com.android.systemui.statusbar.policy.activedisplay;

import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.renderscript.Allocation;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class ActiveDisplayHost extends FrameLayout {
    private static final int BACKGROUND_COLOR = 0x70000000;

    private WallpaperManager mWallpaperManager;
    private Context mContext;
    private Drawable mCustomBackground;
    private int mScreenWidth;
    private int mScreenHeight;

    // This is a faster way to draw the background on devices without hardware acceleration
    private final Drawable mBackgroundDrawable = new Drawable() {
        @Override
        public void draw(Canvas canvas) {
            if (mCustomBackground != null) {
                final Rect bounds = mCustomBackground.getBounds();
                final int vWidth = mScreenWidth;
                final int vHeight = mScreenHeight;
                final int restore = canvas.save();
                canvas.translate(-(bounds.width() - vWidth) / 2,
                            -(bounds.height() - vHeight) / 2);
                mCustomBackground.draw(canvas);
                canvas.restoreToCount(restore);
            } else {
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    };

    private TransitionDrawable mTransitionBackground = null;

    public ActiveDisplayHost(Context context) {
        this(context, null);
    }

    public ActiveDisplayHost(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActiveDisplayHost(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
        mScreenWidth = context.getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = context.getResources().getDisplayMetrics().heightPixels;
        setBackground(mBackgroundDrawable);
    }

    public void updateCustomBackground(boolean isBlur) {
        Bitmap bmp = getBitmapForBackground();
        if ((bmp != null) && isBlur) {
            setCustomBackground(new BitmapDrawable(mContext.getResources(),
                bmp));
        } else {
            setCustomBackground(null);
        }
    }

    public void setCustomBackground(Drawable d) {
        if (!ActivityManager.isHighEndGfx()) {
            mCustomBackground = d;
            if (d != null) {
                d.setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.SRC_OVER);
            }
            computeCustomBackgroundBounds(mCustomBackground);
            invalidate();
        } else {
            if (d == null) {
                mCustomBackground = null;
                setBackground(mBackgroundDrawable);
                return;
            }
            Drawable old = mCustomBackground;
            if (old == null) {
                old = new ColorDrawable(0);
                computeCustomBackgroundBounds(old);
            }
            d.setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.SRC_OVER);
            mCustomBackground = d;
            computeCustomBackgroundBounds(d);
            Bitmap b = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            mBackgroundDrawable.draw(c);

            Drawable dd = new BitmapDrawable(b);
            mTransitionBackground = new TransitionDrawable(new Drawable[]{old, dd});
            mTransitionBackground.setCrossFadeEnabled(true);
            setBackground(mTransitionBackground);

            mTransitionBackground.startTransition(200);
            mCustomBackground = dd;
            invalidate();
       }
    }

    private void computeCustomBackgroundBounds(Drawable background) {
        if (mCustomBackground == null) return; // Nothing to do

        final int bgWidth = background.getIntrinsicWidth();
        final int bgHeight = background.getIntrinsicHeight();
        final int vWidth = mScreenWidth;
        final int vHeight = mScreenHeight;

        final float bgAspect = (float) bgWidth / bgHeight;
        final float vAspect = (float) vWidth / vHeight;

        if (bgAspect > vAspect) {
            background.setBounds(0, 0, (int) (vHeight * bgAspect), vHeight);
        } else {
            background.setBounds(0, 0, vWidth, (int) (vWidth / bgAspect));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeCustomBackgroundBounds(mCustomBackground);
    }

    private Bitmap getBitmapForBackground() {
        Bitmap mBackground;
        try {
             mBackground = mWallpaperManager.getBitmap();
        } catch (RuntimeException e) {
             mBackground = null;
        } catch (OutOfMemoryError e) {
             mBackground = null;
        }
        if (mBackground != null) {
            return blurBitmap(mBackground, mBackground.getWidth() < 900 ? 14: 18);
        }
        return mBackground;
    }

    private Bitmap blurBitmap(Bitmap bmp, int radius) {
        Bitmap out = Bitmap.createBitmap(bmp);
        RenderScript rs = RenderScript.create(mContext);

        Allocation input = Allocation.createFromBitmap(
               rs, bmp, MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation output = Allocation.createTyped(rs, input.getType());

        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        script.setInput(input);
        script.setRadius(radius);
        script.forEach(output);

        output.copyTo(out);

        rs.destroy();
        return out;
    }
}
