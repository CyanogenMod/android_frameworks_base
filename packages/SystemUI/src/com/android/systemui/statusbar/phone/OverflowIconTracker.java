/*
 * Copyright (C) 2016 The CyanogenMod Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import com.android.systemui.statusbar.StatusBarIconView;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.LinkedHashMap;

public class OverflowIconTracker {

    private static final int sThresholdDp = 5;

    private final ViewGroup mOverflow;
    private final ViewGroup mMain;
    private final LinearLayout mSystemIconArea;
    private float mMaxWidth = -1;
    private final float mThresholdPx;

    // Maps slots -> visibility
    private LinkedHashMap<String, Boolean> mIcons;
    private boolean mLayoutFinished;

    OverflowIconTracker(LinearLayout systemIconArea, ViewGroup main, ViewGroup overflow) {
        mSystemIconArea = systemIconArea;
        mMain = main;

        mOverflow = overflow;
        mIcons = new LinkedHashMap<String, Boolean>();
        mThresholdPx = Math.round(main.getResources().getDisplayMetrics().density * sThresholdDp);
    }

    public void updateIcon(String slot, StatusBarIconView view) {
        boolean isVisible = view.getVisibility() == View.VISIBLE;
        if (!mLayoutFinished && mIcons.containsKey(slot) && mIcons.get(slot) == isVisible) {
            return;
        }
        mIcons.put(slot, view.getVisibility() == View.VISIBLE);
        updateSlots();
    }

    public void updateSlots() {
        if (mMaxWidth == -1) {
            // Layout has not occured yet, ignore
            return;
        }
        // Reset visiblity of children
        for (int i = 0; i < mMain.getChildCount(); i++) {
            mMain.getChildAt(i).setVisibility(View.GONE);
        }
        for (int i = 0; i < mOverflow.getChildCount(); i++) {
            mOverflow.getChildAt(i).setVisibility(View.GONE);
        }
        mMain.invalidate();
        mMain.post(new Runnable() {
            @Override
            public void run() {
                float currentWidth = mSystemIconArea.getWidth();
                boolean widthExceedsMax = false;
                for (String slot : mIcons.keySet()) {
                    // We only care if the child is visible
                    if (mIcons.get(slot)) {
                        if (!widthExceedsMax) {
                            float newWidth = currentWidth + getSlotWidth(slot);
                            widthExceedsMax = (mMaxWidth - newWidth) <= mThresholdPx;
                            if (!widthExceedsMax) {
                                currentWidth = newWidth;
                            }
                        }
                        if (widthExceedsMax) {
                            showSlotInOverflow(slot);
                        } else {
                            showSlotInMain(slot);
                        }
                    }
                }
                mLayoutFinished = true;
            }
        });
    }

    private float getSlotWidth(String slot) {
        View v = mOverflow.findViewWithTag(slot);
        v.measure(View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED);
        return v.getMeasuredWidth();
    }

    private void showSlotInMain(String slot) {
        View v = mOverflow.findViewWithTag(slot);
        v.setVisibility(View.GONE);
        v = mMain.findViewWithTag(slot);
        v.setVisibility(View.VISIBLE);
    }

    private void showSlotInOverflow(String slot) {
        View v = mMain.findViewWithTag(slot);
        v.setVisibility(View.GONE);
        v = mOverflow.findViewWithTag(slot);
        v.setVisibility(View.VISIBLE);
    }

    public void removeIcon(String slot) {
        if (mIcons.containsKey(slot) && mIcons.get(slot)) {
            mIcons.put(slot, false);
            updateSlots();
        }
    }

    public void setMaxWidth(float maxWidth) {
        mMaxWidth = maxWidth;
    }
}
