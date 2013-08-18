/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Broadcaster;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.util.pie.PiePosition;
<<<<<<< HEAD
import com.android.systemui.EventLogTags;
=======

>>>>>>> e4a69a6... NavigationBar and StatusBar backaground, transparency and colors (2/2)
import com.android.systemui.R;

import java.util.List;

public class PhoneStatusBarView extends PanelBar {
    private static final String TAG = "PhoneStatusBarView";
    private static final boolean DEBUG = PhoneStatusBar.DEBUG;
    private static final boolean DEBUG_GESTURES = true;

    ActivityManager mActivityManager;
    KeyguardManager mKeyguardManager;  

    PhoneStatusBar mBar;
    int mScrimColor;
    float mSettingsPanelDragzoneFrac;
    float mSettingsPanelDragzoneMin;

    boolean mFullWidthNotifications;
    PanelView mFadingPanel = null;
    PanelView mLastFullyOpenedPanel = null;
    PanelView mNotificationPanel, mSettingsPanel;
    private boolean mShouldFade;

    float mAlpha;
    int mAlphaMode;
    int mStatusBarColor;

    private Runnable mUpdateInHomeAlpha = new Runnable() {
        @Override
        public void run() {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    final List<ActivityManager.RecentTaskInfo> recentTasks = mActivityManager.getRecentTasksForUser(
                            1, ActivityManager.RECENT_WITH_EXCLUDED, UserHandle.CURRENT.getIdentifier());
                    if (recentTasks.size() > 0) {
                        ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(0);
                        Intent intent = new Intent(recentInfo.baseIntent);
                        if (recentInfo.origActivity != null) {
                            intent.setComponent(recentInfo.origActivity);
                        }
                        if (isCurrentHomeActivity(intent.getComponent(), null)) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                protected void onPostExecute(Boolean inHome) {
                    setBackgroundAlpha(inHome ? mAlpha : 1);
                }
            }.execute();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBackgroundAlpha();
        }
    };

    public PhoneStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = getContext().getResources();
        mScrimColor = res.getColor(R.color.notification_panel_scrim_color);
        mSettingsPanelDragzoneMin = res.getDimension(R.dimen.settings_panel_dragzone_min);
        try {
            mSettingsPanelDragzoneFrac = res.getFraction(R.dimen.settings_panel_dragzone_fraction, 1, 1);
        } catch (NotFoundException ex) {
            mSettingsPanelDragzoneFrac = 0f;
        }
        mFullWidthNotifications = mSettingsPanelDragzoneFrac <= 0f;
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();
        Drawable bg = mContext.getResources().getDrawable(R.drawable.status_bar_background);
        if(bg instanceof ColorDrawable) {
            BackgroundAlphaColorDrawable bacd = new BackgroundAlphaColorDrawable(
                    mStatusBarColor != -1 ? mStatusBarColor : ((ColorDrawable) bg).getColor());
            setBackground(bacd);
        }
    }

    public void setBar(PhoneStatusBar bar) {
        mBar = bar;
    }

    public boolean hasFullWidthNotifications() {
        return mFullWidthNotifications;
    }

    @Override
    public void onAttachedToWindow() {
        for (PanelView pv : mPanels) {
            pv.setRubberbandingEnabled(!mFullWidthNotifications);
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mBroadcastReceiver, f);
        updateBackgroundAlpha();
    }

    @Override
    public void addPanel(PanelView pv) {
        super.addPanel(pv);
        if (pv.getId() == R.id.notification_panel) {
            mNotificationPanel = pv;
        } else if (pv.getId() == R.id.settings_panel){
            mSettingsPanel = pv;
        }
        pv.setRubberbandingEnabled(!mFullWidthNotifications);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBar.onBarViewDetached();
        mContext.unregisterReceiver(mBroadcastReceiver);
    }
 
    @Override
    public boolean panelsEnabled() {
        return ((mBar.mDisabled & StatusBarManager.DISABLE_EXPAND) == 0);
    }

    private boolean isKeyguardEnabled() {
        if(mKeyguardManager == null) return false;
        return mKeyguardManager.isKeyguardLocked();
    }	

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        if (super.onRequestSendAccessibilityEvent(child, event)) {
            // The status bar is very small so augment the view that the user is touching
            // with the content of the status bar a whole. This way an accessibility service
            // may announce the current item as well as the entire content if appropriate.
            AccessibilityEvent record = AccessibilityEvent.obtain();
            onInitializeAccessibilityEvent(record);
            dispatchPopulateAccessibilityEvent(record);
            event.appendRecord(record);
            return true;
        }
        return false;
    }

    @Override
    public PanelView selectPanelForTouch(MotionEvent touch) {
        final float x = touch.getX();
        final boolean isLayoutRtl = isLayoutRtl();

        if (mFullWidthNotifications) {
            // No double swiping. If either panel is open, nothing else can be pulled down.
            return ((mSettingsPanel == null ? 0 : mSettingsPanel.getExpandedHeight()) 
                        + mNotificationPanel.getExpandedHeight() > 0) 
                    ? null 
                    : mNotificationPanel;
        }

        // We split the status bar into thirds: the left 2/3 are for notifications, and the
        // right 1/3 for quick settings. If you pull the status bar down a second time you'll
        // toggle panels no matter where you pull it down.

        final float w = getMeasuredWidth();
        float region = (w * mSettingsPanelDragzoneFrac);

        if (DEBUG) {
            Slog.v(TAG, String.format(
                "w=%.1f frac=%.3f region=%.1f min=%.1f x=%.1f w-x=%.1f",
                w, mSettingsPanelDragzoneFrac, region, mSettingsPanelDragzoneMin, x, (w-x)));
        }

        if (region < mSettingsPanelDragzoneMin) region = mSettingsPanelDragzoneMin;

        final boolean showSettings = isLayoutRtl ? (x < region) : (w - region < x);
        return showSettings ? mSettingsPanel : mNotificationPanel;
    }

    @Override
    public void onPanelPeeked() {
        super.onPanelPeeked();
        mBar.makeExpandedVisible(true);
    }

    @Override
    public void startOpeningPanel(PanelView panel) {
        super.startOpeningPanel(panel);
        // we only want to start fading if this is the "first" or "last" panel,
        // which is kind of tricky to determine
        mShouldFade = (mFadingPanel == null || mFadingPanel.isFullyExpanded());
        if (DEBUG) {
            Slog.v(TAG, "start opening: " + panel + " shouldfade=" + mShouldFade);
        }
        mFadingPanel = panel;
    }

    @Override
    public void onAllPanelsCollapsed() {
        super.onAllPanelsCollapsed();
        // give animations time to settle
        mBar.makeExpandedInvisibleSoon();
        mFadingPanel = null;
        mLastFullyOpenedPanel = null;

        // show up you pie controls
        mBar.updatePieTriggerMask(PiePosition.LEFT.FLAG
                | PiePosition.TOP.FLAG
                | PiePosition.RIGHT.FLAG
                | PiePosition.BOTTOM.FLAG);
    }

    @Override
    public void onPanelFullyOpened(PanelView openPanel) {
        super.onPanelFullyOpened(openPanel);
        if (openPanel != mLastFullyOpenedPanel) {
            openPanel.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }

        // back off you pie controls!
        if (mShouldFade) {
            mBar.updatePieTriggerMask(PiePosition.LEFT.FLAG
                    | PiePosition.RIGHT.FLAG
                    | PiePosition.TOP.FLAG);
        }

        mFadingPanel = openPanel;
        mLastFullyOpenedPanel = openPanel;
        mShouldFade = true; // now you own the fade, mister
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean barConsumedEvent = mBar.interceptTouchEvent(event);

        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_PANELBAR_TOUCH,
                        event.getActionMasked(), (int) event.getX(), (int) event.getY(),
                        barConsumedEvent ? 1 : 0);
            }
        }

        return barConsumedEvent || super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mBar.interceptTouchEvent(event) || super.onInterceptTouchEvent(event);
    }

    @Override
    public void panelExpansionChanged(PanelView panel, float frac) {
        super.panelExpansionChanged(panel, frac);

        if (DEBUG) {
            Slog.v(TAG, "panelExpansionChanged: f=" + frac);
        }

        if (panel == mFadingPanel && mScrimColor != 0 && ActivityManager.isHighEndGfx()) {
            if (mShouldFade) {
                frac = mPanelExpandedFractionSum; // don't judge me
                // let's start this 20% of the way down the screen
                frac = frac * 1.2f - 0.2f;
                if (frac <= 0) {
                    mBar.mStatusBarWindow.setBackgroundColor(0);
                } else {
                    // woo, special effects
                    final float k = (float)(1f-0.5f*(1f-Math.cos(3.14159f * Math.pow(1f-frac, 2f))));
                    // attenuate background color alpha by k
                    final int color = (int) ((mScrimColor >>> 24) * k) << 24 | (mScrimColor & 0xFFFFFF);
                    mBar.mStatusBarWindow.setBackgroundColor(color);
                }
            }
        }

        // fade out the panel as it gets buried into the status bar to avoid overdrawing the
        // status bar on the last frame of a close animation
        final int H = mBar.getStatusBarHeight();
        final float ph = panel.getExpandedHeight() + panel.getPaddingBottom();
        float alpha = 1f;
        if (ph < 2*H) {
            if (ph < H) alpha = 0f;
            else alpha = (ph - H) / H;
            alpha = alpha * alpha; // get there faster
        }
        if (panel.getAlpha() != alpha) {
            panel.setAlpha(alpha);
        }

<<<<<<< HEAD
        mBar.updateCarrierLabelVisibility(false);
=======
        updateBackgroundAlpha();
        mBar.updateCarrierAndWifiLabelVisibility(false);
>>>>>>> e4a69a6... NavigationBar and StatusBar backaground, transparency and colors (2/2)
    }

    /*
     * ]0 < alpha < 1[
     */
    protected void setBackgroundAlpha(float alpha) {
        Drawable bg = getBackground();
        if (bg == null)
            return;

        if(bg instanceof BackgroundAlphaColorDrawable) {
            ((BackgroundAlphaColorDrawable) bg).setBgColor(mStatusBarColor);
        }
        int a = Math.round(alpha * 255);
        bg.setAlpha(a);
    }

    public void updateBackgroundAlpha() {
        if(mFadingPanel != null || (isKeyguardEnabled() && mAlphaMode == 0)) {
            setBackgroundAlpha(1);
        } else if (isKeyguardEnabled() || mAlphaMode == 2) {
            setBackgroundAlpha(mAlpha);
        } else {
            removeCallbacks(mUpdateInHomeAlpha);
            postDelayed(mUpdateInHomeAlpha, 100);
        }
    }

    private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
        if (homeInfo == null) {
            final PackageManager pm = mContext.getPackageManager();
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .resolveActivityInfo(pm, 0);
        }
        return homeInfo != null
            && homeInfo.packageName.equals(component.getPackageName())
            && homeInfo.name.equals(component.getClassName());
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_ALPHA), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_ALPHA_MODE), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_COLOR), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        mAlpha = 1.0f - Settings.System.getFloat(mContext.getContentResolver(),
                       Settings.System.STATUS_BAR_ALPHA,
                       0.0f);
        mAlphaMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_ALPHA_MODE, 1);
        mStatusBarColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_COLOR, -1);

        updateBackgroundAlpha();

    }

}
