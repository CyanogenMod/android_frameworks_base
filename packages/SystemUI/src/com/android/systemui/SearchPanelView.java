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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.PowerManager;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.systemui.R;
import com.android.systemui.cm.ActionTarget;
import com.android.systemui.recent.StatusBarTouchProxy;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.tablet.TabletStatusBar;

import static com.android.internal.util.cm.ActionConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.net.URISyntaxException;

public class SearchPanelView extends FrameLayout implements
        StatusBarPanel, ActivityOptions.OnAnimationStartedListener {
    private static final int SEARCH_PANEL_HOLD_DURATION = 0;
    static final String TAG = "SearchPanelView";
    static final boolean DEBUG = TabletStatusBar.DEBUG || PhoneStatusBar.DEBUG || false;
    private static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";
    private final Context mContext;
    private BaseStatusBar mBar;
    private StatusBarTouchProxy mStatusBarTouchProxy;

    private boolean mShowing;
    private View mSearchTargetsContainer;
    private GlowPadView mGlowPadView;
    private IWindowManager mWm;

    private ActionTarget mActionTarget;
    private PackageManager mPackageManager;
    private Resources mResources;
    private TargetObserver mTargetObserver;
    private ContentResolver mContentResolver;
    private String[] mTargetActivities = new String[3];
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
        mPackageManager = mContext.getPackageManager();
        mResources = mContext.getResources();
        mContentResolver = mContext.getContentResolver();
        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
        updateSettings();

    }

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
            }
        }
    }

    private H mHandler = new H();

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
        String trgCenter = Settings.System.getString(mContext.getContentResolver(), Settings.System.NAVIGATION_RING_TARGETS[1]);
        if (trgCenter == null || trgCenter.equals("")) {
            mActionTarget.restoreDefaults();
        }

        // Custom Targets
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();

        if (screenLayout() || isScreenPortrait()) {
            mStartPosOffset =  1;
            mEndPosOffset = 4;
        } else {
            mStartPosOffset = 3;
            mEndPosOffset =  2;
        }

         // Add Initial Place Holder Targets
        for (int i = 0; i < mStartPosOffset; i++) {
            storedDraw.add(getTargetDrawable(""));
        }
        // Add User Targets
        for (int i = 0; i < 3; i++) {
            storedDraw.add(getTargetDrawable(mTargetActivities[i]));
        }

        // Add End Place Holder Targets
        for (int i = 0; i < mEndPosOffset; i++) {
            storedDraw.add(getTargetDrawable(""));
        }
        mGlowPadView.setTargetResources(storedDraw);
    }

    private TargetDrawable getTargetDrawable (String action) {
        TargetDrawable cDrawable = new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_navigation_ring_blank));
        cDrawable.setEnabled(false);

        if (action == null || action.equals("") || action.equals(ACTION_NONE)) {
            return cDrawable;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_screenshot));
        } else if (action.equals(ACTION_IME)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_ime_switcher));
        } else if (action.equals(ACTION_VIBRATE)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_vibrate));
        } else if (action.equals(ACTION_SILENT)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_silent));
        } else if (action.equals(ACTION_RING_SILENT_VIBRATE)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_ring_vibrate_silent));
        } else if (action.equals(ACTION_KILL)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_killtask));
        } else if (action.equals(ACTION_POWER)) {
            return new TargetDrawable(mResources, mResources.getDrawable(R.drawable.ic_action_power));
        } else if (action.equals(ACTION_GOOGLE_NOW)) {
            return new TargetDrawable(mResources, com.android.internal.R.drawable.ic_action_assist_generic);
        } else {
            try {
                Intent in = Intent.parseUri(action, 0);
                ActivityInfo aInfo = in.resolveActivityInfo(mPackageManager, PackageManager.GET_ACTIVITIES);
                Drawable activityIcon = aInfo.loadIcon(mPackageManager);
                Drawable iconBg = mResources.getDrawable(R.drawable.ic_navigation_ring_blank);
                Drawable iconBgActivated = mResources.getDrawable(R.drawable.ic_navigation_ring_blank_activated);
                int margin = (int)(iconBg.getIntrinsicHeight() / 3);
                LayerDrawable icon = new LayerDrawable (new Drawable[] {iconBg, activityIcon});
                icon.setLayerInset(1, margin, margin, margin, margin);
                LayerDrawable iconActivated = new LayerDrawable (new Drawable[] {iconBgActivated, activityIcon});
                iconActivated.setLayerInset(1, margin, margin, margin, margin);
                StateListDrawable selector = new StateListDrawable();
                selector.addState(new int[] {android.R.attr.state_enabled, -android.R.attr.state_active, -android.R.attr.state_focused}, icon);
                selector.addState(new int[] {android.R.attr.state_enabled, android.R.attr.state_active, -android.R.attr.state_focused}, iconActivated);
                selector.addState(new int[] {android.R.attr.state_enabled, -android.R.attr.state_active, android.R.attr.state_focused}, iconActivated);
                return new TargetDrawable(mResources, selector);
            } catch (Exception e) {
                return cDrawable;
            }
        }
    }

    private void maybeSwapSearchIcon() {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT);
        if (intent != null) {
            ComponentName component = intent.getComponent();
            if (component == null || !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                    ASSIST_ICON_METADATA_NAME,
                    com.android.internal.R.drawable.ic_action_assist_generic)) {
                if (DEBUG) Slog.v(TAG, "Couldn't grab icon for component " + component);
            }
        }
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

    public void show(final boolean show, boolean animate) {
        if (!show) {
            final LayoutTransition transitioner = animate ? createLayoutTransitioner() : null;
            ((ViewGroup) mSearchTargetsContainer).setLayoutTransition(transitioner);
        }
        mShowing = show;
        if (show) {
            maybeSwapSearchIcon();
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

    private LayoutTransition createLayoutTransitioner() {
        LayoutTransition transitioner = new LayoutTransition();
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
        return transitioner;
    }

    public boolean isAssistantAvailable() {
        return ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT) != null;
    }

    public boolean screenLayout() {
        final int screenSize = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public boolean isScreenPortrait() {
        return mResources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public class TargetObserver extends ContentObserver {
        public TargetObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            setDrawables();
            updateSettings();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            for (int i = 0; i < 3; i++) {
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_RING_TARGETS[i]), false, this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            setDrawables();
        }
    }

    public void updateSettings() {

        for (int i = 0; i < 3; i++) {
            mTargetActivities[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.NAVIGATION_RING_TARGETS[i]);
        }
    }
}
