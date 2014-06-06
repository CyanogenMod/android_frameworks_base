/*
 * Copyright (C) 2014 ParanoidAndroid Project.
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

package com.android.systemui.statusbar.notification;

import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.systemui.statusbar.LatestItemView;
import com.android.systemui.statusbar.NotificationData.Entry;

/**
 * Hover notification builder
 * Creates hover objects and handles view reparenting
 */
public class HoverNotification {

    private static final String TAG = "HoverNotification";

    private Entry entry;
    private ViewGroup parent;
    private SizeAdaptiveLayout layout;
    private StatusBarNotification content;

    /**
     * Builds a new hoverNotification
     * @Param entry the current notification Entry
     * @Param layout the current SizeAdaptiveLayout
     */
    public HoverNotification(Entry entry, SizeAdaptiveLayout layout) {
        this.entry = entry;
        this.content = entry.notification;
        this.layout = layout;

        parent = (ViewGroup) layout.getParent();
    }

    public SizeAdaptiveLayout getLayout() {
        return layout;
    }

    public StatusBarNotification getContent() {
        return content;
    }

    public void setContent(StatusBarNotification content) {
        this.content = content;
    }

    public Entry getEntry() {
        return entry;
    }

    public void setEntry(Entry entry) {
        this.entry = entry;
    }

    public void reparentToHover() {
        if (parent != null && parent instanceof LatestItemView) {
            if (Hover.DEBUG_REPARENT) Log.d(TAG, "reparenting to hover. old parent: " + parent);
            parent.removeView(layout);
        }
    }

    public void reparentToStatusBar() {
        ViewGroup currParent = (ViewGroup) layout.getParent();
        if (!(currParent instanceof LatestItemView)) {
            if (Hover.DEBUG_REPARENT) Log.d(TAG, "reparenting to statusbar."
                    + " old parent: " + currParent + " new parent: " + parent);
            if (currParent != null) currParent.removeView(layout);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            parent.addView(layout, params);
            resetLayoutProperties(true);
        }
    }

    public void resetLayoutProperties(boolean visible) {
        layout.setVisibility(visible ? View.VISIBLE : View.GONE);
        layout.setEnabled(true);
        layout.setRotationX(0);
        layout.setAlpha(1f);
        layout.setX(0f);
        layout.setY(0f);
    }
}
