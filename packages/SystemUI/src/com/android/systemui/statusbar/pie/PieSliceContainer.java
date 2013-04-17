/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.graphics.Canvas;
import android.util.Slog;

import com.android.internal.util.pie.PiePosition;
import com.android.systemui.statusbar.pie.PieView.PieDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic container for {@link PieItems}.
 */
public class PieSliceContainer extends PieView.PieSlice {

    protected PieView mPieLayout;
    private List<PieItem> mItems = new ArrayList<PieItem>();

    public PieSliceContainer(PieView parent, int initialFlags) {
        mPieLayout = parent;

        flags = initialFlags | PieView.PieDrawable.VISIBLE;
    }

    @Override
    public void prepare(PiePosition position, float scale) {
        if (hasItems()) {
            int totalWidth = 0;
            for (PieItem item : mItems) {
                if ((item.flags & PieDrawable.VISIBLE) != 0) {
                    totalWidth += item.width;
                }
            }

            // if there is no item to be lay out stop here
            if (totalWidth == 0) {
                return;
            }

            float gapMinder = ((totalWidth * GAP * 2.0f) / (mOuter + mInner));
            float deltaSweep = mSweep / totalWidth;
            int width = position != PiePosition.TOP ? 0 : totalWidth;

            int viewMask = PieDrawable.VISIBLE | position.FLAG;

            boolean top = position == PiePosition.TOP;
            for (PieItem item : mItems) {
                if ((item.flags & viewMask) == viewMask) {
                    if (top) width -= item.width;

                    item.setGeometry(mStart + deltaSweep * width,
                            item.width * deltaSweep, mInner, mOuter);
                    item.setGap(deltaSweep * gapMinder);

                    if (PieView.DEBUG) {
                        Slog.d(PieView.TAG, "Layout " + item.tag + " : ("
                                + (mStart + deltaSweep * width) + ","
                                + (item.width * deltaSweep) + ")");
                    }

                    if (!top) width += item.width;
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas, PiePosition gravity) {
    }

    @Override
    public PieItem interact(float alpha, int radius) {
        return null;
    }

    public boolean hasItems() {
        return !mItems.isEmpty();
    }

    public void addItem(PieItem item) {
        mItems.add(item);
    }

    public List<PieItem> getItems() {
        return mItems;
    }

    public void clear() {
        mItems.clear();
    }
}
