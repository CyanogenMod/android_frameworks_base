/*
 * Copyright (C) 2014 Vince Mi <vince@makeramen.com>
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
package com.android.systemui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.android.systemui.R;

import java.text.NumberFormat;

public class RoundedImageView extends ImageView {

  public static final String TAG = "RoundedImageView";
  private static final ScaleType[] SCALE_TYPES = {
      ScaleType.MATRIX,
      ScaleType.FIT_XY,
      ScaleType.FIT_START,
      ScaleType.FIT_CENTER,
      ScaleType.FIT_END,
      ScaleType.CENTER,
      ScaleType.CENTER_CROP,
      ScaleType.CENTER_INSIDE
  };

  private int mCornerRadius = RoundedDrawable.DEFAULT_RADIUS;
  private int mBorderWidth = RoundedDrawable.DEFAULT_BORDER_WIDTH;
  private ColorStateList mBorderColor =
      ColorStateList.valueOf(RoundedDrawable.DEFAULT_BORDER_COLOR);
  private boolean mOval = false;
  private boolean mRoundBackground = false;

  private int mResource;
  private Drawable mDrawable;
  private Drawable mBackgroundDrawable;

  private ScaleType mScaleType;

  private Drawable mNumberBackground;
  private Paint mNumberPain;
  private int mNumberX;
  private int mNumberY;
  private int mNumbers;
  private String mNumberText;
  private boolean mShowNumber = false;

  public RoundedImageView(Context context) {
    super(context);
  }

  public RoundedImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public RoundedImageView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    final Resources res = context.getResources();
    final float densityMultiplier = res.getDisplayMetrics().density;
    final float scaledPx = 8 * densityMultiplier;
    mNumberPain = new Paint();
    mNumberPain.setTextAlign(Paint.Align.CENTER);
    mNumberPain.setColor(res.getColor(R.drawable.notification_number_text_color));
    mNumberPain.setAntiAlias(true);
    mNumberPain.setTypeface(Typeface.DEFAULT_BOLD);
    mNumberPain.setTextSize(scaledPx);

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView, defStyle, 0);

    int index = a.getInt(R.styleable.RoundedImageView_android_scaleType, -1);
    if (index >= 0) {
      setScaleType(SCALE_TYPES[index]);
    } else {
      // default scaletype to FIT_CENTER
      setScaleType(ScaleType.FIT_CENTER);
    }

    mCornerRadius = a.getDimensionPixelSize(R.styleable.RoundedImageView_corner_radius, -1);
    mBorderWidth = a.getDimensionPixelSize(R.styleable.RoundedImageView_border_width, -1);

    // don't allow negative values for radius and border
    if (mCornerRadius < 0) {
      mCornerRadius = RoundedDrawable.DEFAULT_RADIUS;
    }
    if (mBorderWidth < 0) {
      mBorderWidth = RoundedDrawable.DEFAULT_BORDER_WIDTH;
    }

    mBorderColor = a.getColorStateList(R.styleable.RoundedImageView_border_color);
    if (mBorderColor == null) {
      mBorderColor = ColorStateList.valueOf(RoundedDrawable.DEFAULT_BORDER_COLOR);
    }

    mRoundBackground = a.getBoolean(R.styleable.RoundedImageView_round_background, false);
    mOval = a.getBoolean(R.styleable.RoundedImageView_is_oval, false);

    updateDrawableAttrs();
    updateBackgroundDrawableAttrs();

    a.recycle();
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    invalidate();
  }

  /**
   * Return the current scale type in use by this ImageView.
   *
   * @attr ref android.R.styleable#ImageView_scaleType
   * @see android.widget.ImageView.ScaleType
   */
  @Override
  public ScaleType getScaleType() {
    return mScaleType;
  }

  /**
   * Controls how the image should be resized or moved to match the size
   * of this ImageView.
   *
   * @param scaleType The desired scaling mode.
   * @attr ref android.R.styleable#ImageView_scaleType
   */
  @Override
  public void setScaleType(ScaleType scaleType) {
    if (scaleType == null) {
      throw new NullPointerException();
    }

    if (mScaleType != scaleType) {
      mScaleType = scaleType;

      switch (scaleType) {
        case CENTER:
        case CENTER_CROP:
        case CENTER_INSIDE:
        case FIT_CENTER:
        case FIT_START:
        case FIT_END:
        case FIT_XY:
          super.setScaleType(ScaleType.FIT_XY);
          break;
        default:
          super.setScaleType(scaleType);
          break;
      }

      updateDrawableAttrs();
      updateBackgroundDrawableAttrs();
      invalidate();
    }
  }

  @Override
  public void setImageDrawable(Drawable drawable) {
    mResource = 0;
    mDrawable = RoundedDrawable.fromDrawable(drawable);
    updateDrawableAttrs();
    super.setImageDrawable(mDrawable);
  }

  @Override
  public void setImageBitmap(Bitmap bm) {
    mResource = 0;
    mDrawable = RoundedDrawable.fromBitmap(bm);
    updateDrawableAttrs();
    super.setImageDrawable(mDrawable);
  }

  @Override
  public void setImageResource(int resId) {
    if (mResource != resId) {
      mResource = resId;
      mDrawable = resolveResource();
      updateDrawableAttrs();
      super.setImageDrawable(mDrawable);
    }
  }

  @Override public void setImageURI(Uri uri) {
    super.setImageURI(uri);
    setImageDrawable(getDrawable());
  }

  private Drawable resolveResource() {
    Resources rsrc = getResources();
    if (rsrc == null) {
      return null;
    }

    Drawable d = null;

    if (mResource != 0) {
      try {
        d = rsrc.getDrawable(mResource);
      } catch (Exception e) {
        Log.w(TAG, "Unable to find resource: " + mResource, e);
        // Don't try again.
        mResource = 0;
      }
    }
    return RoundedDrawable.fromDrawable(d);
  }

  @Override
  public void setBackground(Drawable background) {
    setBackgroundDrawable(background);
  }

  private void updateDrawableAttrs() {
    updateAttrs(mDrawable, false);
  }

  private void updateBackgroundDrawableAttrs() {
    updateAttrs(mBackgroundDrawable, true);
  }

  private void updateAttrs(Drawable drawable, boolean background) {
    if (drawable == null) {
      return;
    }

    if (drawable instanceof RoundedDrawable) {
      ((RoundedDrawable) drawable)
          .setScaleType(mScaleType)
          .setCornerRadius(background && !mRoundBackground ? 0 : mCornerRadius)
          .setBorderWidth(background && !mRoundBackground ? 0 : mBorderWidth)
          .setBorderColors(mBorderColor)
          .setOval(mOval);
    } else if (drawable instanceof LayerDrawable) {
      // loop through layers to and set drawable attrs
      LayerDrawable ld = ((LayerDrawable) drawable);
      int layers = ld.getNumberOfLayers();
      for (int i = 0; i < layers; i++) {
        updateAttrs(ld.getDrawable(i), background);
      }
    }
  }

  @Override
  @Deprecated
  public void setBackgroundDrawable(Drawable background) {
    mBackgroundDrawable = RoundedDrawable.fromDrawable(background);
    updateBackgroundDrawableAttrs();
    super.setBackgroundDrawable(mBackgroundDrawable);
  }

  public int getCornerRadius() {
    return mCornerRadius;
  }

  public void setCornerRadius(int radius) {
    if (mCornerRadius == radius) {
      return;
    }

    mCornerRadius = radius;
    updateDrawableAttrs();
    updateBackgroundDrawableAttrs();
  }

  public int getBorderWidth() {
    return mBorderWidth;
  }

  public void setBorderWidth(int width) {
    if (mBorderWidth == width) {
      return;
    }

    mBorderWidth = width;
    updateDrawableAttrs();
    updateBackgroundDrawableAttrs();
    invalidate();
  }

  public int getBorderColor() {
    return mBorderColor.getDefaultColor();
  }

  public void setBorderColor(int color) {
    setBorderColors(ColorStateList.valueOf(color));
  }

  public ColorStateList getBorderColors() {
    return mBorderColor;
  }

  public void setBorderColors(ColorStateList colors) {
    if (mBorderColor.equals(colors)) {
      return;
    }

    mBorderColor =
        (colors != null) ? colors : ColorStateList.valueOf(RoundedDrawable.DEFAULT_BORDER_COLOR);
    updateDrawableAttrs();
    updateBackgroundDrawableAttrs();
    if (mBorderWidth > 0) {
      invalidate();
    }
  }

  public boolean isOval() {
    return mOval;
  }

  public void setOval(boolean oval) {
    mOval = oval;
    updateDrawableAttrs();
    updateBackgroundDrawableAttrs();
    invalidate();
  }

  public boolean isRoundBackground() {
    return mRoundBackground;
  }

  public void setRoundBackground(boolean roundBackground) {
    if (mRoundBackground == roundBackground) {
      return;
    }

    mRoundBackground = roundBackground;
    updateBackgroundDrawableAttrs();
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if ((mNumberBackground != null) && mShowNumber) {
        placeNumber(mNumbers, mShowNumber);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if ((mNumberBackground != null) && mShowNumber) {
        mNumberBackground.draw(canvas);
        canvas.drawText(mNumberText, mNumberX, mNumberY, mNumberPain);
    }
  }

  public void placeNumber(int number, boolean showNumber) {
    mShowNumber = showNumber;
    mNumbers = number;
    if (showNumber && (number > 1)) {
        mNumberBackground = mContext.getResources().getDrawable(
                            R.drawable.ic_notification_overlay);
        final String str;
        final int tooBig = mContext.getResources().getInteger(
             android.R.integer.status_bar_notification_info_maxnum);
        if (number > tooBig) {
            str = mContext.getResources().getString(
                    android.R.string.status_bar_notification_info_overflow);
        } else {
            NumberFormat f = NumberFormat.getIntegerInstance();
            str = f.format(number);
        }
        mNumberText = str;

        final int w = getWidth();
        final int h = getHeight();
        final Rect r = new Rect();
        mNumberPain.getTextBounds(str, 0, str.length(), r);
        final int tw = r.right - r.left;
        final int th = r.bottom - r.top;
        mNumberBackground.getPadding(r);
        int dw = r.left + tw + r.right;
        if (dw < mNumberBackground.getMinimumWidth()) {
            dw = mNumberBackground.getMinimumWidth();
        }
        mNumberX = w-r.right-((dw-r.right-r.left)/2);
        int dh = r.top + th + r.bottom;
        if (dh < mNumberBackground.getMinimumWidth()) {
            dh = mNumberBackground.getMinimumWidth();
        }
        mNumberY = h-r.bottom-((dh-r.top-th-r.bottom)/2);
        mNumberBackground.setBounds(w-dw, h-dh, w, h);
    } else {
        mNumberBackground = null;
        mNumberText = null;
    }
  }
}
