/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.systemui;

import android.animation.LayoutTransition;
import android.app.ActivityOptions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.FrameLayout;

import com.android.internal.util.cm.NavigationRingConstants;
import com.android.internal.util.cm.NavigationRingHelpers;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import com.android.systemui.EventLogTags;
import com.android.systemui.R;
import com.android.systemui.cm.ActionTarget;
import com.android.systemui.recent.StatusBarTouchProxy;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.tablet.TabletStatusBar;

import java.util.ArrayList;

public class SearchPanelView extends FrameLayout implements
        StatusBarPanel, ActivityOptions.OnAnimationStartedListener {
    private static final int SEARCH_PANEL_HOLD_DURATION = 0;
    static final String TAG = "SearchPanelView";
    static final boolean DEBUG = TabletStatusBar.DEBUG || PhoneStatusBar.DEBUG || false;
    public static final boolean DEBUG_GESTURES = true;
    private final Context mContext;
    private BaseStatusBar mBar;
    private StatusBarTouchProxy mStatusBarTouchProxy;
    private SettingsObserver mObserver;

    private boolean mShowing;
    private View mSearchTargetsContainer;
    private GlowPadView mGlowPadView;
    private IWindowManager mWm;

    private ActionTarget mActionTarget;
    private String[] mTargetActivities;
    private int mStartPosOffset;
    private int mEndPosOffset;

    public SearchPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        mActionTarget = new ActionTarget(context);

        mObserver = new SettingsObserver(new Handler());
    }

    class GlowPadTriggerListener implements GlowPadView.OnTriggerListener {
        boolean mWaitingForLaunch;

        public void onGrabbed(View v, int handle) {
        }

        public void onReleased(View v, int handle) {
        }

        public void onGrabbedStateChange(View v, int handle) {
            if (!mWaitingForLaunch && OnTriggerListener.NO_HANDLE == handle) {
                mBar.hideSearchPanel();
            }
        }

        public void onTrigger(View v, final int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);
            mActionTarget.launchAction(mTargetActivities[target - mStartPosOffset]);
        }

        public void onFinishFinalAnimation() {
        }
    }
    final GlowPadTriggerListener mGlowPadViewListener = new GlowPadTriggerListener();

    @Override
    public void onAnimationStarted() {
        postDelayed(new Runnable() {
            public void run() {
                mGlowPadViewListener.mWaitingForLaunch = false;
                mBar.hideSearchPanel();
            }
        }, SEARCH_PANEL_HOLD_DURATION);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSearchTargetsContainer = findViewById(R.id.search_panel_container);
        mStatusBarTouchProxy = (StatusBarTouchProxy) findViewById(R.id.status_bar_touch_proxy);
        // TODO: fetch views
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mGlowPadViewListener);
        updateSettings();
        setDrawables();
    }

    private void setDrawables() {
        final ArrayList<TargetDrawable> targets = new ArrayList<TargetDrawable>();

        if (isScreenLarge() || isScreenPortrait()) {
            mStartPosOffset =  1;
            mEndPosOffset = 4;
        } else {
            mStartPosOffset = 3;
            mEndPosOffset =  2;
        }

         // Add Initial Place Holder Targets
        for (int i = 0; i < mStartPosOffset; i++) {
            targets.add(NavigationRingHelpers.getTargetDrawable(mContext, null));
        }
        // Add User Targets
        for (int i = 0; i < mTargetActivities.length; i++) {
            targets.add(NavigationRingHelpers.getTargetDrawable(mContext, mTargetActivities[i]));
        }
        // Add End Place Holder Targets
        for (int i = 0; i < mEndPosOffset; i++) {
            targets.add(NavigationRingHelpers.getTargetDrawable(mContext, null));
        }
        mGlowPadView.setTargetResources(targets);
    }

    private boolean pointInside(int x, int y, View v) {
        final int l = v.getLeft();
        final int r = v.getRight();
        final int t = v.getTop();
        final int b = v.getBottom();
        return x >= l && x < r && y >= t && y < b;
    }

    public boolean isInContentArea(int x, int y) {
        if (pointInside(x, y, mSearchTargetsContainer)) {
            return true;
        } else if (mStatusBarTouchProxy != null &&
                pointInside(x, y, mStatusBarTouchProxy)) {
            return true;
        } else {
            return false;
        }
    }

    private final OnPreDrawListener mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mGlowPadView.resumeAnimations();
            return false;
        }
    };

    private void vibrate() {
        Context context = getContext();
        if (Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0) {
            Resources res = context.getResources();
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(res.getInteger(R.integer.config_search_panel_view_vibration_duration));
        }
    }

    private boolean hasValidTargets() {
        for (String target : mTargetActivities) {
            if (!TextUtils.isEmpty(target) && !target.equals(NavigationRingConstants.ACTION_NONE)) {
                return true;
            }
        }
        return false;
    }

    public void show(final boolean show, boolean animate) {
        if (!show) {
            final LayoutTransition transitioner = animate ? createLayoutTransitioner() : null;
            ((ViewGroup) mSearchTargetsContainer).setLayoutTransition(transitioner);
        }
        mShowing = show;
        if (show && hasValidTargets()) {
            for (int i = 0; i < mTargetActivities.length; i++) {
                NavigationRingHelpers.updateDynamicIconIfNeeded(mContext, mGlowPadView,
                        mTargetActivities[i], i + mStartPosOffset);
            }
            NavigationRingHelpers.swapSearchIconIfNeeded(mContext, mGlowPadView);
            if (getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
                // Don't start the animation until we've created the layer, which is done
                // right before we are drawn
                mGlowPadView.suspendAnimations();
                mGlowPadView.ping();
                getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
                vibrate();
            }
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    public void hide(boolean animate) {
        if (mBar != null) {
            // This will indirectly cause show(false, ...) to get called
            mBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    /**
     * We need to be aligned at the bottom.  LinearLayout can't do this, so instead,
     * let LinearLayout do all the hard work, and then shift everything down to the bottom.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // setPanelHeight(mSearchTargetsContainer.getHeight());
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Ignore hover events outside of this panel bounds since such events
        // generate spurious accessibility events with the panel content when
        // tapping outside of it, thus confusing the user.
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return super.dispatchHoverEvent(event);
        }
        return true;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        mObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mObserver.unobserve();
    }

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
    public boolean isShowing() {
        return mShowing;
    }

    public void setBar(BaseStatusBar bar) {
        mBar = bar;
    }

    public void setStatusBarView(final View statusBarView) {
        if (mStatusBarTouchProxy != null) {
            mStatusBarTouchProxy.setStatusBar(statusBarView);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_SEARCHPANEL_TOUCH,
                        event.getActionMasked(), (int) event.getX(), (int) event.getY());
            }
        }
        return super.onTouchEvent(event);
    }

    private LayoutTransition createLayoutTransitioner() {
        LayoutTransition transitioner = new LayoutTransition();
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
        return transitioner;
    }

    private boolean isScreenLarge() {
        final Configuration configuration = mContext.getResources().getConfiguration();
        final int screenSize = configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

        return screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE
                || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    private boolean isScreenPortrait() {
        final Configuration configuration = mContext.getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void updateSettings() {
        mTargetActivities = NavigationRingHelpers.getTargetActions(mContext);
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            for (int i = 0; i < NavigationRingHelpers.MAX_ACTIONS; i++) {
                resolver.registerContentObserver(
                        Settings.System.getUriFor(Settings.System.NAVIGATION_RING_TARGETS[i]),
                        false, this);
            }
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            setDrawables();
        }
    }
}
