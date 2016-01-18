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

import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.BatteryLevelTextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarIconView;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.LinkedHashMap;

public class OverflowIconTracker {

    private static final int sThresholdDp = 5;

    private final ViewGroup mOverflowIconContainer;
    private final ViewGroup mStatusIcons;
    private final LinearLayout mSystemIconArea;
    private final BatteryLevelTextView mHeaderBatteryText;
    private float mMaxWidth = -1;
    private final float mThresholdPx;
    private int mLastTotalWidth;

    // Maps slots -> visibility
    private LinkedHashMap<String, Boolean> mIcons;
    private boolean mLayoutFinished;

    OverflowIconTracker(View statusBar, View headerView) {
        mSystemIconArea = (LinearLayout) statusBar.findViewById(R.id.system_icon_area);
        mOverflowIconContainer = (ViewGroup) headerView.findViewById(R.id.overFlowContainer);
        mHeaderBatteryText = (BatteryLevelTextView) headerView.findViewById(
            R.id.battery_level_text);
        mStatusIcons = (LinearLayout) statusBar.findViewById(R.id.statusIcons);
        mIcons = new LinkedHashMap<String, Boolean>();
        Resources resources = statusBar.getResources();
        mThresholdPx = Math.round(resources.getDisplayMetrics().density * sThresholdDp);
    }

    public void addIcon(String slot, StatusBarIconView view) {
        mIcons.put(slot, view.getVisibility() == View.VISIBLE);
        updateSlots(true);
    }

    public void updateIcon(String slot, StatusBarIcon icon) {
        if (mIcons.containsKey(slot)) {
            boolean wasVisible = mIcons.get(slot);
            if (wasVisible) {
                StatusBarIconView overflowSlot = (StatusBarIconView)
                    mOverflowIconContainer.findViewWithTag(slot);
                boolean visibleInOverflow = overflowSlot.getVisibility() == View.VISIBLE;
                overflowSlot.set(icon, true); // Sets new visibility
                boolean isVisible = overflowSlot.getVisibility() == View.VISIBLE;
                if (wasVisible != isVisible) {
                    // We were previously visible, and now want to be hidden
                    mIcons.put(slot, isVisible);
                    updateSlots(true);
                } else if (visibleInOverflow) {
                    // Maintain visibility in overflow
                    showSlotInOverflow(slot);
                } else {
                    // Maintain visibility in main panel
                    showSlotInMain(slot);
                }
                return;
            }
        }
        StatusBarIconView view = (StatusBarIconView) mOverflowIconContainer.findViewWithTag(slot);
        view.set(icon, true);
        mIcons.put(slot, view.getVisibility() == View.VISIBLE);
        updateSlots(true);
    }

    public void updateSlots(boolean force) {
        if (mMaxWidth == -1) {
            // Layout has not occured yet, ignore
            return;
        }
        float currentWidth = mSystemIconArea.getMeasuredWidth();
        if (currentWidth == mLastTotalWidth && !force) {
            return;
        }
        // Reset visiblity of children
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            mStatusIcons.getChildAt(i).setVisibility(View.GONE);
        }
        for (int i = 0; i < mOverflowIconContainer.getChildCount(); i++) {
            mOverflowIconContainer.getChildAt(i).setVisibility(View.GONE);
        }
        mSystemIconArea.measure(View.MeasureSpec.UNSPECIFIED,
                 View.MeasureSpec.UNSPECIFIED);
        currentWidth = mSystemIconArea.getMeasuredWidth();
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
        mSystemIconArea.measure(View.MeasureSpec.UNSPECIFIED,
                 View.MeasureSpec.UNSPECIFIED);
        mLastTotalWidth = mSystemIconArea.getMeasuredWidth();
        mLayoutFinished = true;
        updateHeaderBattery(widthExceedsMax);
    }

    private void updateHeaderBattery(boolean hasOverflow) {
        if (hasOverflow) {
            mHeaderBatteryText.setForceHidden(hasOverflow);
        }
    }

    private float getSlotWidth(String slot) {
        View v = mOverflowIconContainer.findViewWithTag(slot);
        v.measure(View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED);
        return v.getMeasuredWidth();
    }

    private void showSlotInMain(String slot) {
        View v = mOverflowIconContainer.findViewWithTag(slot);
        v.setVisibility(View.GONE);
        v = mStatusIcons.findViewWithTag(slot);
        v.setVisibility(View.VISIBLE);
    }

    private void showSlotInOverflow(String slot) {
        View v = mStatusIcons.findViewWithTag(slot);
        v.setVisibility(View.GONE);
        v = mOverflowIconContainer.findViewWithTag(slot);
        v.setVisibility(View.VISIBLE);
    }

    public void removeIcon(String slot) {
        if (mIcons.containsKey(slot) && mIcons.get(slot)) {
            mIcons.put(slot, false);
            updateSlots(true);
        }
    }

    public void setMaxWidth(float maxWidth) {
        mMaxWidth = maxWidth;
    }
}
