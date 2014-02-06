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

import android.graphics.Canvas;
import android.util.Slog;

import com.android.internal.util.gesture.EdgeGesturePosition;

import com.android.systemui.statusbar.pie.PieView.PieDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic container for {@link PieItems}.
 */
public class PieSliceContainer extends PieView.PieSlice {

    protected PieView mPieView;
    private List<PieItem> mItems = new ArrayList<PieItem>();

    public PieSliceContainer(PieView parent, int initialFlags) {
        mPieView = parent;

        flags = initialFlags | PieView.PieDrawable.VISIBLE;
    }

    @Override
    public void prepare(EdgeGesturePosition position, float scale, boolean mirrorRightPie) {
        if (hasItems()) {
            int totalWidth = 0;
            boolean topRight = false;
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
            if (mirrorRightPie) {
                // Check if it is top or right trigger to mirror later the items correct
                topRight = (position == EdgeGesturePosition.TOP)
                        || (position == EdgeGesturePosition.RIGHT);
            } else {
                topRight = (position == EdgeGesturePosition.TOP);
            }
            int width = topRight ? totalWidth : 0;

            int viewMask = PieDrawable.VISIBLE | position.FLAG;

            for (PieItem item : mItems) {
                if ((item.flags & viewMask) == viewMask) {
                    if (topRight) width -= item.width;

                    item.setGeometry(mStart + deltaSweep * width,
                            item.width * deltaSweep, mInner, mOuter);
                    item.setGap(deltaSweep * gapMinder);

                    if (PieView.DEBUG) {
                        Slog.d(PieView.TAG, "Layout " + item.tag + " : ("
                                + (mStart + deltaSweep * width) + ","
                                + (item.width * deltaSweep) + ")");
                    }

                    if (!topRight) width += item.width;
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas, EdgeGesturePosition position) {
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
