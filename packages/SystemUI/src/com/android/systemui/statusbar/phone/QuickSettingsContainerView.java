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
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
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

    private boolean mSingleRow;

    public QuickSettingsContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuickSettingsContainer, 0, 0);
        mSingleRow = a.getBoolean(R.styleable.QuickSettingsContainer_singleRow, false);
        a.recycle();
        updateResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO: Setup the layout transitions
        LayoutTransition transitions = getLayoutTransition();
    }

    void updateResources() {
        Resources r = getContext().getResources();
        mCellGap = r.getDimension(R.dimen.quick_settings_cell_gap);
        mNumColumns = r.getInteger(R.integer.quick_settings_num_columns);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate the cell width dynamically
        int width = MeasureSpec.getSize(widthMeasureSpec);

        int availableWidth = (int) (width - getPaddingLeft() - getPaddingRight() -
                (mNumColumns - 1) * mCellGap);
        float cellWidth = (float) Math.ceil(((float) availableWidth) / mNumColumns);
        int cellHeight = 0;
        float cellGap = mCellGap;

        if (mSingleRow) {
            cellWidth = MeasureSpec.getSize(heightMeasureSpec);
            cellHeight = (int) cellWidth;
            cellGap /= 2;
        } else {
            cellHeight = (int) getResources().getDimension(R.dimen.quick_settings_cell_height);
        }

        // Update each of the children's widths accordingly to the cell width
        final int N = getChildCount();
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

        // Set the measured dimensions.  We always fill the tray width, but wrap to the height of
        // all the tiles.
        int numRows = (int) Math.ceil((float) cursor / mNumColumns);
        int newHeight = (int) ((numRows * cellHeight) + ((numRows - 1) * cellGap)) +
                getPaddingTop() + getPaddingBottom();
        if (mSingleRow) {
            int totalHeight = cellHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(totalWidth, totalHeight);
        } else {
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
}
