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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.GestureDetector;
import android.os.PowerManager;
import android.provider.Settings;

import com.android.systemui.EventLogTags;
import com.android.systemui.R;

public class PhoneStatusBarView extends PanelBar {
    private static final String TAG = "PhoneStatusBarView";
    private static final boolean DEBUG = PhoneStatusBar.DEBUG;
    private static final boolean DEBUG_GESTURES = false;

    PhoneStatusBar mBar;

    PanelView mLastFullyOpenedPanel = null;
    PanelView mNotificationPanel;
    private final PhoneStatusBarTransitions mBarTransitions;
    private ScrimController mScrimController;
    private GestureDetector mDoubleTapGesture;

    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;
    private boolean mDoubleTapToSleepEnabled;

    public PhoneStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = getContext().getResources();
        mBarTransitions = new PhoneStatusBarTransitions(this);

        mSettingsObserver = new SettingsObserver(mHandler);
        mDoubleTapGesture = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                Log.d(TAG, "Gesture!!");
                if(pm != null)
                    pm.goToSleep(e.getEventTime());
                else
                    Log.d(TAG, "getSystemService returned null PowerManager");

                return true;
            }
        });
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setBar(PhoneStatusBar bar) {
        mBar = bar;
    }

    public void setScrimController(ScrimController scrimController) {
        mScrimController = scrimController;
    }

    @Override
    public void onFinishInflate() {
        mBarTransitions.init();
    }

    @Override
    public void addPanel(PanelView pv) {
        super.addPanel(pv);
        if (pv.getId() == R.id.notification_panel) {
            mNotificationPanel = pv;
        }
    }

    @Override
    public boolean panelsEnabled() {
        return mBar.panelsEnabled();
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
        // No double swiping. If either panel is open, nothing else can be pulled down.
        return mNotificationPanel.getExpandedHeight() > 0
                ? null
                : mNotificationPanel;
    }

    @Override
    public void onPanelPeeked() {
        super.onPanelPeeked();
        mBar.makeExpandedVisible(false);
    }

    @Override
    public void onAllPanelsCollapsed() {
        super.onAllPanelsCollapsed();

        // Close the status bar in the next frame so we can show the end of the animation.
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                mBar.makeExpandedInvisible();
            }
        });
        mLastFullyOpenedPanel = null;
    }

    @Override
    public void onPanelFullyOpened(PanelView openPanel) {
        super.onPanelFullyOpened(openPanel);
        if (openPanel != mLastFullyOpenedPanel) {
            openPanel.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        mLastFullyOpenedPanel = openPanel;
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

        if (mDoubleTapToSleepEnabled) {
            mDoubleTapGesture.onTouchEvent(event);
        }

        return barConsumedEvent || super.onTouchEvent(event);
    }

    @Override
    public void onTrackingStarted(PanelView panel) {
        super.onTrackingStarted(panel);
        mBar.onTrackingStarted();
        mScrimController.onTrackingStarted();
    }

    @Override
    public void onTrackingStopped(PanelView panel, boolean expand) {
        super.onTrackingStopped(panel, expand);
        mBar.onTrackingStopped(expand);
    }

    @Override
    public void onExpandingFinished() {
        super.onExpandingFinished();
        mScrimController.onExpandingFinished();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mBar.interceptTouchEvent(event) || super.onInterceptTouchEvent(event);
    }

    @Override
    public void panelExpansionChanged(PanelView panel, float frac, boolean expanded) {
        super.panelExpansionChanged(panel, frac, expanded);
        mScrimController.setPanelExpansion(frac);
        mBar.updateCarrierLabelVisibility(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DOUBLE_TAP_SLEEP_GESTURE), false, this);
            update();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mDoubleTapToSleepEnabled = Settings.System.getInt(
                    resolver, Settings.System.DOUBLE_TAP_SLEEP_GESTURE, 1) == 1;
        }
    }
}
