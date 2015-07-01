/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile.State;

import java.util.Objects;

/** View that represents a standard quick settings tile. **/
public class QSTileView extends ViewGroup {
    private static final Typeface CONDENSED = Typeface.create("sans-serif-condensed",
            Typeface.NORMAL);

    protected final Context mContext;
    private final View mIcon;
    private final View mDivider;
    private final H mHandler = new H();
    private final int mIconSizePx;
    private final int mTileSpacingPx;
    private int mTilePaddingTopPx;
    private final int mTilePaddingBelowIconPx;
    private final int mDualTileVerticalPaddingPx;
    private final View mTopBackgroundView;

    private TextView mLabel;
    private QSDualTileLabel mDualLabel;
    private boolean mDual;
    private OnClickListener mClickPrimary;
    private OnClickListener mClickSecondary;
    private OnLongClickListener mLongClick;
    private Drawable mTileBackground;
    private RippleDrawable mRipple;

    public QSTileView(Context context) {
        super(context);

        mContext = context;
        final Resources res = context.getResources();
        mIconSizePx = res.getDimensionPixelSize(R.dimen.qs_tile_icon_size);
        mTileSpacingPx = res.getDimensionPixelSize(R.dimen.qs_tile_spacing);
        mTilePaddingBelowIconPx =  res.getDimensionPixelSize(R.dimen.qs_tile_padding_below_icon);
        mDualTileVerticalPaddingPx =
                res.getDimensionPixelSize(R.dimen.qs_dual_tile_padding_vertical);
        mTileBackground = newTileBackground();
        recreateLabel();
        setClipChildren(false);

        mTopBackgroundView = new View(context);
        addView(mTopBackgroundView);

        mIcon = createIcon();
        addView(mIcon);

        mDivider = new View(mContext);
        mDivider.setBackgroundColor(res.getColor(R.color.qs_tile_divider));
        final int dh = res.getDimensionPixelSize(R.dimen.qs_tile_divider_height);
        mDivider.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dh));
        addView(mDivider);

        setClickable(true);

        updateTopPadding();
    }

    private void updateTopPadding() {
        Resources res = getResources();
        int padding = res.getDimensionPixelSize(R.dimen.qs_tile_padding_top);
        int largePadding = res.getDimensionPixelSize(R.dimen.qs_tile_padding_top_large_text);
        float largeFactor = (MathUtils.constrain(getResources().getConfiguration().fontScale,
                1.0f, FontSizeUtils.LARGE_TEXT_SCALE) - 1f) / (FontSizeUtils.LARGE_TEXT_SCALE - 1f);
        mTilePaddingTopPx = Math.round((1 - largeFactor) * padding + largeFactor * largePadding);
        requestLayout();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTopPadding();
        FontSizeUtils.updateFontSize(mLabel, R.dimen.qs_tile_text_size);
        if (mDualLabel != null) {
            mDualLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.qs_tile_text_size));
        }
    }

    private void recreateLabel() {
        CharSequence labelText = null;
        CharSequence labelDescription = null;
        if (mDualLabel != null) {
            labelText = mDualLabel.getText();
            if (mLabel != null) {
                labelDescription = mLabel.getContentDescription();
            }
            removeView(mDualLabel);
            mDualLabel = null;
        }
        if (mLabel != null) {
            labelText = mLabel.getText();
            removeView(mLabel);
            mLabel = null;
        }
        final Resources res = mContext.getResources();
        if (mDual) {
            mDualLabel = new QSDualTileLabel(mContext);
            mDualLabel.setId(android.R.id.title);
            mDualLabel.setBackgroundResource(R.drawable.btn_borderless_rect);
            mDualLabel.setFirstLineCaret(res.getDrawable(R.drawable.qs_dual_tile_caret));
            mDualLabel.setTextColor(res.getColor(R.color.qs_tile_text));
            mDualLabel.setPadding(0, mDualTileVerticalPaddingPx, 0, mDualTileVerticalPaddingPx);
            mDualLabel.setTypeface(CONDENSED);
            mDualLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    res.getDimensionPixelSize(R.dimen.qs_tile_text_size));
            mDualLabel.setClickable(true);
            mDualLabel.setOnClickListener(mClickSecondary);
            mDualLabel.setFocusable(true);
            if (labelText != null) {
                mDualLabel.setText(labelText);
            }
            if (labelDescription != null) {
                mDualLabel.setContentDescription(labelDescription);
            }
            addView(mDualLabel);
        } else {
            mLabel = new TextView(mContext);
            mLabel.setId(android.R.id.title);
            mLabel.setTextColor(res.getColor(R.color.qs_tile_text));
            mLabel.setGravity(Gravity.CENTER_HORIZONTAL);
            mLabel.setMinLines(2);
            mLabel.setPadding(0, 0, 0, 0);
            mLabel.setTypeface(CONDENSED);
            mLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    res.getDimensionPixelSize(R.dimen.qs_tile_text_size));
            mLabel.setClickable(false);
            if (labelText != null) {
                mLabel.setText(labelText);
            }
            addView(mLabel);
        }
    }

    public boolean setDual(boolean dual) {
        final boolean changed = dual != mDual;
        mDual = dual;
        if (mTileBackground instanceof RippleDrawable) {
            setRipple((RippleDrawable) mTileBackground);
        }
        if (dual) {
            mTopBackgroundView.setOnClickListener(mClickPrimary);
            mTopBackgroundView.setOnLongClickListener(mLongClick);
            setOnClickListener(null);
            setClickable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mTopBackgroundView.setBackground(mTileBackground);
        } else {
            mTopBackgroundView.setOnClickListener(null);
            mTopBackgroundView.setClickable(false);
            setOnClickListener(mClickPrimary);
            setOnLongClickListener(mLongClick);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setBackground(mTileBackground);
        }
        mTopBackgroundView.setFocusable(dual);
        setFocusable(!dual);
        mDivider.setVisibility(dual ? VISIBLE : GONE);
        if (changed) {
            recreateLabel();
            updateTopPadding();
        }
        postInvalidate();
        return changed;
    }

    private void setRipple(RippleDrawable tileBackground) {
        mRipple = tileBackground;
        if (getWidth() != 0) {
            updateRippleSize(getWidth(), getHeight());
        }
    }

    public void init(OnClickListener clickPrimary, OnClickListener clickSecondary,
            OnLongClickListener longClick) {
        mClickPrimary = clickPrimary;
        mClickSecondary = clickSecondary;
        mLongClick = longClick;
    }

    protected View createIcon() {
        final ImageView icon = new ImageView(mContext);
        icon.setId(android.R.id.icon);
        icon.setScaleType(ScaleType.CENTER_INSIDE);
        return icon;
    }

    private Drawable newTileBackground() {
        final int[] attrs = new int[] { android.R.attr.selectableItemBackgroundBorderless };
        final TypedArray ta = mContext.obtainStyledAttributes(attrs);
        final Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

    private View labelView() {
        return mDual ? mDualLabel : mLabel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        final int h = MeasureSpec.getSize(heightMeasureSpec);
        final int iconSpec = exactly(mIconSizePx);
        mIcon.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), iconSpec);
        labelView().measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        if (mDual) {
            mDivider.measure(widthMeasureSpec, exactly(mDivider.getLayoutParams().height));
        }
        int heightSpec = exactly(
                mIconSizePx + mTilePaddingBelowIconPx + mTilePaddingTopPx);
        mTopBackgroundView.measure(widthMeasureSpec, heightSpec);
        setMeasuredDimension(w, h);
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = getMeasuredWidth();
        final int h = getMeasuredHeight();

        layout(mTopBackgroundView, 0, mTileSpacingPx);

        int top = 0;
        top += mTileSpacingPx;
        top += mTilePaddingTopPx;
        final int iconLeft = (w - mIcon.getMeasuredWidth()) / 2;
        layout(mIcon, iconLeft, top);
        if (mRipple != null) {
            updateRippleSize(w, h);

        }
        top = mIcon.getBottom();
        top += mTilePaddingBelowIconPx;
        if (mDual) {
            layout(mDivider, 0, top);
            top = mDivider.getBottom();
        }
        layout(labelView(), 0, top);
    }

    private void updateRippleSize(int width, int height) {
        // center the touch feedback on the center of the icon, and dial it down a bit
        final int cx = width / 2;
        final int cy = mDual ? mIcon.getTop() + mIcon.getHeight() : height / 2;
        final int rad = (int)(mIcon.getHeight() * 1.25f);
        mRipple.setHotspotBounds(cx - rad, cy - rad, cx + rad, cy + rad);
    }

    private static void layout(View child, int left, int top) {
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    protected void handleStateChanged(QSTile.State state) {
        if (mIcon instanceof ImageView) {
            setIcon((ImageView) mIcon, state);
        }
        if (mDual) {
            mDualLabel.setText(state.label);
            mDualLabel.setContentDescription(state.dualLabelContentDescription);
            mTopBackgroundView.setContentDescription(state.contentDescription);
            if (!Objects.equals(state.enabled, mDualLabel.isEnabled())) {
                mTopBackgroundView.setEnabled(state.enabled);
                mDualLabel.setEnabled(state.enabled);
                mDualLabel.setTextColor(mContext.getResources().getColor(state.enabled ?
                        R.color.qs_tile_text : R.color.qs_tile_text_disabled));
            }
        } else {
            mLabel.setText(state.label);
            setContentDescription(state.contentDescription);
            if (!Objects.equals(state.enabled, mLabel.isEnabled())) {
                mLabel.setEnabled(state.enabled);
                mLabel.setTextColor(mContext.getResources().getColor(state.enabled ?
                        R.color.qs_tile_text : R.color.qs_tile_text_disabled));
            }
        }
    }

    protected void setIcon(ImageView iv, QSTile.State state) {
        if (!Objects.equals(state.icon, iv.getTag(R.id.qs_icon_tag))) {
            Drawable d = state.icon != null ? state.icon.getDrawable(mContext) : null;
            if (d != null && state.autoMirrorDrawable) {
                d.setAutoMirrored(true);
            }
            iv.setImageDrawable(d);
            iv.setTag(R.id.qs_icon_tag, state.icon);
            if (d instanceof Animatable) {
                if (!iv.isShown()) {
                    ((Animatable) d).stop(); // skip directly to end state
                }
            }
        }
        if (!Objects.equals(state.enabled, iv.isEnabled())) {
            iv.setEnabled(state.enabled);
            if (state.enabled) {
                iv.setColorFilter(null);
            } else {
                iv.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
            }
        }
    }

    public void onStateChanged(QSTile.State state) {
        mHandler.obtainMessage(H.STATE_CHANGED, state).sendToTarget();
    }

    private class H extends Handler {
        private static final int STATE_CHANGED = 1;
        public H() {
            super(Looper.getMainLooper());
        }
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == STATE_CHANGED) {
                handleStateChanged((State) msg.obj);
            }
        }
    }
}
