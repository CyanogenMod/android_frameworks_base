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

package com.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.systemui.cm.ActionTarget;
import com.android.systemui.cm.NavigationRingHelpers;
import com.android.systemui.cm.ShortcutPickHelper;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusBarPanel;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import java.util.ArrayList;
import java.util.List;

import static com.android.internal.util.cm.NavigationRingConstants.*;

public class SearchPanelView extends FrameLayout implements StatusBarPanel,
        View.OnClickListener, ShortcutPickHelper.OnPickListener {

    private static final String TAG = "SearchPanelView";
    private static final String ASSIST_ICON_METADATA_NAME =
            "com.android.systemui.action_assist_icon";

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private final Context mContext;
    private BaseStatusBar mBar;

    private SearchPanelCircleView mCircle;
    private ImageView mLogo;
    private View mScrim;

    private int mThreshold;
    private boolean mHorizontal;

    private boolean mLaunching;
    private boolean mDragging;
    private boolean mDraggedFarEnough;
    private float mStartTouch;
    private float mStartDrag;
    private boolean mLaunchPending;
    private String[] mTargetActivities;
    private boolean mInEditMode;
    private View mEditButton;
    private ImageView mSelectedView;
    private List<ImageView> mTargetViews;
    private ImageView mLogoRight, mLogoLeft;
    private final ActionTarget mActionTarget;
    private ShortcutPickHelper mPicker;
    private SettingsObserver mSettingsObserver;

    public SearchPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mThreshold = context.getResources().getDimensionPixelSize(R.dimen.search_panel_threshold);
        mActionTarget = new ActionTarget(context);
        mPicker = new ShortcutPickHelper(mContext, this);
        mTargetViews = new ArrayList<ImageView>();
        // Instantiate receiver/observer
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST);
        mContext.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, null);
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    private void startAssistActivity() {
        if (!mBar.isDeviceProvisioned()) return;

        // Close Recent Apps if needed
        mBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_SEARCH_PANEL);

        final Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
        if (intent == null) return;

        try {
            final ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                    R.anim.search_launch_enter, R.anim.search_launch_exit);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    mContext.startActivityAsUser(intent, opts.toBundle(),
                            new UserHandle(UserHandle.USER_CURRENT));
                }
            });
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Activity not found for " + intent.getAction());
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mCircle = (SearchPanelCircleView) findViewById(R.id.search_panel_circle);
        mLogo = (ImageView) findViewById(R.id.search_logo);
        mLogo.setOnClickListener(this);
        mScrim = findViewById(R.id.search_panel_scrim);

        mEditButton = findViewById(R.id.nav_ring_edit);
        mEditButton.setOnClickListener(this);

        mLogoLeft = (ImageView) findViewById(R.id.search_logo1);
        mLogoLeft.setOnClickListener(this);

        mLogoRight = (ImageView) findViewById(R.id.search_logo2);
        mLogoRight.setOnClickListener(this);

        // Order matters
        mTargetViews.add(mLogoLeft);
        mTargetViews.add(mLogo);
        mTargetViews.add(mLogoRight);

        mCircle.initializeAdditionalTargets(this);
        updateDrawables();
    }

    private void maybeSwapSearchIcon(ImageView view) {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT);
        if (intent != null) {
            ComponentName component = intent.getComponent();
            replaceDrawable(view, component, ASSIST_ICON_METADATA_NAME);
        } else {
            mLogo.setImageDrawable(null);
        }
    }

    public void replaceDrawable(ImageView v, ComponentName component, String name) {
        if (component != null) {
            try {
                PackageManager packageManager = mContext.getPackageManager();
                // Look for the search icon specified in the activity meta-data
                Bundle metaData = packageManager.getActivityInfo(
                        component, PackageManager.GET_META_DATA).metaData;
                if (metaData != null) {
                    int iconResId = metaData.getInt(name);
                    if (iconResId != 0) {
                        Resources res = packageManager.getResourcesForActivity(component);
                        v.setImageDrawable(res.getDrawable(iconResId));
                        return;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to swap drawable; "
                        + component.flattenToShortString() + " not found", e);
            } catch (Resources.NotFoundException nfe) {
                Log.w(TAG, "Failed to swap drawable from "
                        + component.flattenToShortString(), nfe);
            }
        }
        v.setImageDrawable(null);
    }

    @Override
    public boolean isInContentArea(int x, int y) {
        return true;
    }

    private void vibrate() {
        Context context = getContext();
        if (Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0) {
            Resources res = context.getResources();
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(res.getInteger(R.integer.config_search_panel_view_vibration_duration),
                    VIBRATION_ATTRIBUTES);
        }
    }

    public void show(final boolean show, boolean animate) {
        if (show) {
            if (getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
                vibrate();
                if (animate) {
                    startEnterAnimation();
                } else {
                    mScrim.setAlpha(1f);
                }
            }
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        } else {
            if (mInEditMode) return;
            if (animate) {
                startAbortAnimation();
            } else {
                setVisibility(View.INVISIBLE);
            }
        }
    }

    private void startEnterAnimation() {
        mCircle.startEnterAnimation();
        mScrim.setAlpha(0f);
        mScrim.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(50)
                .setInterpolator(PhoneStatusBar.ALPHA_IN)
                .start();

    }

    private void startAbortAnimation() {
        if (mInEditMode) return;
        mCircle.startAbortAnimation(new Runnable() {
                    @Override
                    public void run() {
                        mCircle.setAnimatingOut(false);
                        setVisibility(View.INVISIBLE);
                    }
                });
        mCircle.setAnimatingOut(true);
        mScrim.animate()
                .alpha(0f)
                .setDuration(300)
                .setStartDelay(0)
                .setInterpolator(PhoneStatusBar.ALPHA_OUT);
    }

    public void hide(boolean animate) {
        if (mInEditMode) return;
        if (mBar != null) {
            // This will indirectly cause show(false, ...) to get called
            mBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
        } else {
            if (animate) {
                startAbortAnimation();
            } else {
                setVisibility(View.INVISIBLE);
            }
        }
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
        return getVisibility() == View.VISIBLE && !mCircle.isAnimatingOut();
    }

    public void setBar(BaseStatusBar bar) {
        mBar = bar;
    }

    public boolean isAssistantAvailable() {
        return ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mLaunching || mLaunchPending || mInEditMode) {
            return super.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mStartTouch = mHorizontal ? event.getX() : event.getY();
                mDragging = false;
                mDraggedFarEnough = false;
                mCircle.reset();
                break;
            case MotionEvent.ACTION_MOVE:
                float currentTouch = mHorizontal ? event.getX() : event.getY();
                if (getVisibility() == View.VISIBLE && !mDragging &&
                        (!mCircle.isAnimationRunning(true /* enterAnimation */)
                                || Math.abs(mStartTouch - currentTouch) > mThreshold)) {
                    mStartDrag = currentTouch;
                    mDragging = true;
                }
                if (mDragging) {
                    float offset = Math.max(mStartDrag - currentTouch, 0.0f);
                    mCircle.setDragDistance(offset);
                    int indexOfIntersect = mCircle.isIntersecting(event);
                    mDraggedFarEnough = indexOfIntersect != -1;
                    mCircle.setDraggedFarEnough(mDraggedFarEnough, indexOfIntersect);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDraggedFarEnough) {
                    if (mCircle.isAnimationRunning(true  /* enterAnimation */)) {
                        mLaunchPending = true;
                        mCircle.setAnimatingOut(true);
                        mCircle.performOnAnimationFinished(new Runnable() {
                            @Override
                            public void run() {
                                startExitAnimation();
                            }
                        });
                    } else {
                        startExitAnimation();
                    }
                } else {
                    startAbortAnimation();
                }
                break;
        }
        return true;
    }

    private void startExitAnimation() {
        mLaunchPending = false;
        if (mLaunching || getVisibility() != View.VISIBLE) {
            return;
        }
        mLaunching = true;
        if (!mActionTarget.launchAction(mTargetActivities[mCircle.mIntersectIndex])) {
            startAssistActivity();
        }
        vibrate();
        mCircle.setAnimatingOut(true);
        mCircle.startExitAnimation(new Runnable() {
                    @Override
                    public void run() {
                        mLaunching = false;
                        mCircle.setAnimatingOut(false);
                        setVisibility(View.INVISIBLE);
                    }
                });
        mScrim.animate()
                .alpha(0f)
                .setDuration(300)
                .setStartDelay(0)
                .setInterpolator(PhoneStatusBar.ALPHA_OUT);
    }

    public void setHorizontal(boolean horizontal) {
        mHorizontal = horizontal;
        mCircle.setHorizontal(horizontal);
    }

    @Override
    public void onClick(View v) {
        if (mInEditMode) {
            if (v == mEditButton) {
                mInEditMode = false;
                show(false, true);
                startEditAnimation(false);
                updateTargetVisibility();
                mPicker.cleanup();
            } else if (v == mLogo || v == mLogoLeft || v == mLogoRight) {
                mSelectedView = (ImageView) v;
                mPicker.pickShortcut(v != mLogo);
            }
        }
    }

    @Override
    public void shortcutPicked(String uri) {
        if (uri != null) {
            int index = mTargetViews.indexOf(mSelectedView);
            Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    Settings.Secure.NAVIGATION_RING_TARGETS[index], uri, UserHandle.USER_CURRENT);
        }
    }

    private class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            ContentResolver resolver = mContext.getContentResolver();
            for (int i = 0; i < NavigationRingHelpers.MAX_ACTIONS; i++) {
                resolver.registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.NAVIGATION_RING_TARGETS[i]),
                        false, this, UserHandle.USER_ALL);
            }
        }

        @Override
        public void update() {
            updateDrawables();
        }
    }

    private void updateDrawables() {
        mTargetActivities = NavigationRingHelpers.getTargetActions(mContext);
        for (int i = 0; i < NavigationRingHelpers.MAX_ACTIONS; i++) {
            ImageView target = mTargetViews.get(i);
            String action = mTargetActivities[i];

            if (isAssistantAvailable() && ((TextUtils.isEmpty(action) && target == mLogo)
                    || ACTION_ASSIST.equals(action))) {
                maybeSwapSearchIcon(target);
                continue;
            }

            target.setImageDrawable(NavigationRingHelpers.getTargetDrawable(
                        mContext, mTargetActivities[i]));
        }
        updateTargetVisibility();
    }

    private void updateTargetVisibility() {
        for (int i = 0; i < mTargetViews.size(); i++) {
            View v = mTargetViews.get(i);
            // Special case, middle target never be invisible
            if (v == mLogo) {
                continue;
            }
            View parent = (View) v.getParent();
            String action = mTargetActivities[i];
            boolean visible = mInEditMode
                    || (!TextUtils.isEmpty(action) && !ACTION_NONE.equals(action));
            parent.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean editMode = intent.getBooleanExtra(EDIT_STATE_EXTRA, false);
            if (editMode == mInEditMode) {
                return;
            }
            mInEditMode = editMode;
            if (mInEditMode) {
                show(true, true);
                startEditAnimation(true);
                updateTargetVisibility();
            } else {
                mEditButton.performClick();
            }
        }
    };

    private void startEditAnimation(boolean show) {
        if (show) {
            mEditButton.setScaleX(0);
            mEditButton.setScaleY(0);
            mEditButton.setAlpha(0f);
            mEditButton
                    .animate()
                    .scaleX(1)
                    .scaleY(1)
                    .alpha(1)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditButton.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            mEditButton
                    .animate()
                    .scaleX(0)
                    .scaleY(0)
                    .alpha(0)
                    .setDuration(500)
                    .setInterpolator(new AnticipateOvershootInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mEditButton.setVisibility(View.GONE);
                        }
                    });
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        mPicker.cleanup();
    }
}
