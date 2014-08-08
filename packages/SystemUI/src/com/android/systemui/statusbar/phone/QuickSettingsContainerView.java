/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.systemui.R;

/**
 *
 */
public class QuickSettingsContainerView extends FrameLayout {

    // The number of columns in the QuickSettings grid
    private int mNumColumns;

    // The gap between tiles in the QuickSettings grid
    private float mCellGap;

    private float mPadding4Tiles = -4.0f;
    private float mPadding3Tiles = 0.0f;
    private float mSmallSize4Tiles = 9.0f;
    private float mSize4Tiles = 10.0f;
    private float mSize3Tiles = 12.0f;

    private int mTextSize;
    private int mTextPadding;

    private boolean mSingleRow;
    private Context mContext;
    private boolean mSmallIcons;

    // Cell width for single row
    private int mCellWidth = -1;
    private int mMinCellWidth = 0;
    private int mMaxCellWidth = 0;
    private int mMinMargin = 0;
    private int mMaxMargin = 0;

    public QuickSettingsContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuickSettingsContainer, 0, 0);
        mSingleRow = a.getBoolean(R.styleable.QuickSettingsContainer_singleRow, false);
        a.recycle();
        mContext = context;
        updateResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO: Setup the layout transitions
        LayoutTransition transitions = getLayoutTransition();
    }

    public void updateResources() {
        Resources r = getContext().getResources();
        ContentResolver resolver = mContext.getContentResolver();
        mSmallIcons = Settings.System.getIntForUser(resolver,
                Settings.System.QUICK_SETTINGS_SMALL_ICONS, 0, UserHandle.USER_CURRENT) == 1;
        mCellGap = r.getDimension(R.dimen.quick_settings_cell_gap);
        mNumColumns = r.getInteger(R.integer.quick_settings_num_columns);
        mTextSize = (int) mSize3Tiles;
        mTextPadding = (int) mPadding3Tiles;
        if (mSmallIcons) {
            mNumColumns = r.getInteger(R.integer.quick_settings_num_columns_small);
            mTextSize = (int) mSize4Tiles;
            mTextPadding = (int) mPadding4Tiles;
        }
        QSSize size = getRibbonSize();
        mMinCellWidth = r.getDimensionPixelSize(R.dimen.qs_ribbon_width_min);
        mMaxCellWidth = r.getDimensionPixelSize(R.dimen.qs_ribbon_width_max);
        if (size == QSSize.Auto || size == QSSize.AutoNarrow) {
            mCellWidth = -1;
            if (size == QSSize.Auto) {
                mMaxMargin = r.getDimensionPixelSize(R.dimen.qs_tile_ribbon_icon_margin_big_max);
                mMinMargin = r.getDimensionPixelSize(R.dimen.qs_tile_ribbon_icon_margin_big_min);
            } else {
                mMaxMargin = r.getDimensionPixelSize(R.dimen.qs_tile_ribbon_icon_margin_small_max);
                mMinMargin = r.getDimensionPixelSize(R.dimen.qs_tile_ribbon_icon_margin_small_min);
            }
        } else {
            mCellWidth = r.getDimensionPixelSize(R.dimen.qs_ribbon_width_big);
        }
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate the cell width dynamically

        int width = MeasureSpec.getSize(widthMeasureSpec);
        float availableWidth = width - getPaddingLeft() - getPaddingRight();
        float cellWidth;
        int cellHeight;
        float cellGap = mCellGap;

        final int N = getChildCount();
        if (mSingleRow) {
            cellGap /= 2;
            cellHeight = MeasureSpec.getSize(heightMeasureSpec);
            if (mCellWidth > 0) {
                cellWidth = mCellWidth;
            } else {
                if (width <= 0) {
                    // On first layout pass the parent width is 0
                    // So set the maximum width possible here
                    cellWidth = mMaxCellWidth;
                } else {
                    int numColumns = 0;
                    for (int i = 0; i < N; ++i) {
                        QuickSettingsTileView v = (QuickSettingsTileView) getChildAt(i);
                        if (v.getVisibility() != View.GONE) {
                            numColumns += v.getColumnSpan();
                        }
                    }
                    if (numColumns == 0)
                        numColumns = 1; // Avoid division by zero
                    availableWidth -= (numColumns - 1) * cellGap;
                    cellWidth = (float) Math.floor(availableWidth / numColumns);
                    if (cellWidth < mMinCellWidth)
                        cellWidth = mMinCellWidth;
                    else if (cellWidth > mMaxCellWidth)
                        cellWidth = mMaxCellWidth;
                    // Calculate the margin based on a linear interpolation
                    int widthDiff = mMaxCellWidth - mMinCellWidth;
                    float factor = widthDiff > 0 ? (cellWidth - mMinCellWidth) / widthDiff : 0.5f;
                    int margin = Math.round(mMaxMargin - factor * (mMaxMargin - mMinMargin));
                    for (int i = 0; i < N; ++i) {
                        QuickSettingsTileView v = (QuickSettingsTileView) getChildAt(i);
                        v.getTile().setImageMargins(margin);
                    }
                }
            }
        } else {
            availableWidth -= (mNumColumns - 1) * cellGap;
            cellWidth = (float) Math.floor(availableWidth / mNumColumns);
            cellHeight = getResources().getDimensionPixelSize(R.dimen.quick_settings_cell_height);
        }

        // Update each of the children's widths accordingly to the cell width
        int totalWidth = 0;
        int cursor = 0;
        for (int i = 0; i < N; ++i) {
            // Update the child's width
            QuickSettingsTileView v = (QuickSettingsTileView) getChildAt(i);
            if (v.getVisibility() != View.GONE) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int colSpan = v.getColumnSpan();
                lp.width = (int) ((colSpan * cellWidth) + (colSpan - 1) * cellGap);
                lp.height = cellHeight;

                // Measure the child
                int newWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
                int newHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
                v.measure(newWidthSpec, newHeightSpec);
                cursor += colSpan;
                totalWidth += v.getMeasuredWidth() + cellGap;
            }
        }

        // Set the measured dimensions.
        if (mSingleRow) {
            int totalHeight = cellHeight + getPaddingTop() + getPaddingBottom();
            if (totalWidth > 0)
                totalWidth -= cellGap; // No space at the end
            setMeasuredDimension(totalWidth, totalHeight);
        } else {
            // We always fill the tray width, but wrap to the height of all the
            // tiles.
            int numRows = (int) Math.ceil((float) cursor / mNumColumns);
            int newHeight = (int) ((numRows * cellHeight) + ((numRows - 1) * cellGap)) +
                    getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, newHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int N = getChildCount();
        final boolean isLayoutRtl = isLayoutRtl();
        final int width = getWidth();

        int x = getPaddingStart();
        int y = getPaddingTop();
        int cursor = 0;

        float cellGap = mCellGap;

        if (mSingleRow) {
            cellGap /= 2;
        }

        for (int i = 0; i < N; ++i) {
            QuickSettingsTileView child = (QuickSettingsTileView) getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (child.getVisibility() != GONE) {
                final int col = cursor % mNumColumns;
                final int colSpan = child.getColumnSpan();

                final int childWidth = lp.width;
                final int childHeight = lp.height;

                int row = (int) (cursor / mNumColumns);

                // Push the item to the next row if it can't fit on this one
                if ((col + colSpan) > mNumColumns && !mSingleRow) {
                    x = getPaddingStart();
                    y += childHeight + cellGap;
                    row++;
                }

                final int childLeft = (isLayoutRtl) ? width - x - childWidth : x;
                final int childRight = childLeft + childWidth;

                final int childTop = y;
                final int childBottom = childTop + childHeight;

                // Layout the container
                child.layout(childLeft, childTop, childRight, childBottom);

                // Offset the position by the cell gap or reset the position and cursor when we
                // reach the end of the row
                cursor += child.getColumnSpan();
                if (cursor < (((row + 1) * mNumColumns)) || mSingleRow) {
                    x += childWidth + cellGap;
                } else if (!mSingleRow) {
                    x = getPaddingStart();
                    y += childHeight + cellGap;
                }
            }
        }
    }

    public int getTileTextSize() {
        ContentResolver resolver = mContext.getContentResolver();
        mSmallIcons = Settings.System.getIntForUser(resolver,
                Settings.System.QUICK_SETTINGS_SMALL_ICONS, 0, UserHandle.USER_CURRENT) == 1;
        if (mSmallIcons) {
            int dpi = mContext.getResources().getDisplayMetrics().densityDpi;
            boolean isTablet = mContext.getResources().
                    getBoolean(R.bool.config_recents_interface_for_tablets);
            return mTextSize = (isTablet || dpi > DisplayMetrics.DENSITY_HIGH) ?
                    (int) mSize4Tiles : (int) mSmallSize4Tiles;
        } else {
            return mTextSize = (int) mSize3Tiles;
        }
    }

    public int getTileTextPadding() {
        ContentResolver resolver = mContext.getContentResolver();
        mSmallIcons = Settings.System.getIntForUser(resolver,
                Settings.System.QUICK_SETTINGS_SMALL_ICONS, 0, UserHandle.USER_CURRENT) == 1;
        if (mSmallIcons) {
            return mTextPadding = (int) mPadding4Tiles;
        } else {
            return mTextPadding = (int) mPadding3Tiles;
        }
    }

    public enum QSSize {
        Auto,
        AutoNarrow,
        Big,
        Narrow
    }

    public QSSize getRibbonSize() {
        int size = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_QUICK_ACCESS_SIZE, 0, UserHandle.USER_CURRENT);
        switch (size) {
            case 0:
                return QSSize.Auto;
            case 1:
                return QSSize.AutoNarrow;
            case 2:
                return QSSize.Big;
            case 3:
                return QSSize.Narrow;
        }
        return QSSize.Auto;
    }
}
