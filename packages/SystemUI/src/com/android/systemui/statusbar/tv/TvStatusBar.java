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

package com.android.systemui.statusbar.tv;

import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.BaseStatusBar;

/*
 * Status bar implementation for "large screen" products that mostly present no on-screen nav
 */

public class TvStatusBar extends BaseStatusBar {

    @Override
    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
    }

    @Override
    public void updateIcon(String slot, int index, int viewIndex, StatusBarIcon old,
            StatusBarIcon icon) {
    }

    @Override
    public void removeIcon(String slot, int index, int viewIndex) {
    }

    @Override
    public void addNotification(IBinder key, StatusBarNotification notification) {
    }

    @Override
    public void updateNotification(IBinder key, StatusBarNotification notification) {
    }

    @Override
    public void removeNotification(IBinder key) {
    }

    @Override
    public void disable(int state) {
        propagateDisabledFlags(state);
    }

    @Override
    public void animateExpandNotificationsPanel() {
    }

    @Override
    public void animateCollapsePanels(int flags) {
    }

    @Override
    public void setSystemUiVisibility(int vis, int mask) {
    }

    @Override
    public void topAppWindowChanged(boolean visible) {
        propagateMenuVisibility(visible);
    }

    @Override
    public void setImeWindowStatus(IBinder token, int vis, int backDisposition) {
    }

    @Override
    public void setHardKeyboardStatus(boolean available, boolean enabled) {
    }

    @Override
    public void toggleRecentApps() {
    }

    @Override // CommandQueue
    public void setWindowState(int window, int state) {
    }

    @Override // CommandQueue
    public void setAutoRotate(boolean enabled) {
    }

    @Override // CommandQueue
    public void toggleNotificationShade() {
    }

    @Override // CommandQueue
    public void toggleQSShade() {
    }

    @Override // CommandQueue
    public void toggleSmartPulldown() {
    }

    @Override
    protected void createAndAddWindows() {
    }

    @Override
    protected WindowManager.LayoutParams getSearchLayoutParams(
            LayoutParams layoutParams) {
        return null;
    }

    @Override
    protected void haltTicker() {
    }

    @Override
    protected void setAreThereNotifications() {
    }

    @Override
    public void updateNotificationIcons() {
    }

    @Override
    protected void tick(IBinder key, StatusBarNotification n, boolean firstTime) {
    }

    @Override
    protected void updateExpandedViewPos(int expandedPosition) {
    }

    @Override
    protected int getExpandedViewMaxHeight() {
        return 0;
    }

    @Override
    protected boolean shouldDisableNavbarGestures() {
        return true;
    }

    @Override
    public boolean isExpandedVisible() {
        return false;
    }

    @Override
    public View getStatusBarView() {
        return null;
    }

    @Override
    public void resetHeadsUpDecayTimer() {
    }

    @Override
    public void hideHeadsUp() {
    }

    @Override
    public void animateExpandSettingsPanel(boolean flip) {
    }

    @Override
    protected void refreshLayout(int layoutDirection) {
    }

    @Override
    protected boolean isDisabled(int flag) {
        return false;
    }
}
