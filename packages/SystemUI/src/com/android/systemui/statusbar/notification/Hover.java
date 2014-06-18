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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.NotificationData.Entry;

import java.util.ArrayList;

/**
 * Hover constructor
 * Handles creating, processing and displaying hover notifications
 * Must be initilized and needs a NotificationHelper instance
 */
public class Hover {

    public static final boolean DEBUG = false;
    public static final boolean DEBUG_REPARENT = false;

    private static final String TAG = "Hover";
    private static final String IN_CALL_UI = "com.android.incallui";
    private static final String DIALER = "com.android.dialer";
    private static final String DELIMITER = "|";

    private static final int ANIMATION_DURATION = 350; // 350 ms
    private static final int INDEX_CURRENT = 0; // first array object
    private static final int INDEX_NEXT = 1; // second array object
    private static final int INSTANT_FADE_OUT_DELAY = 0; // 0 seconds
    private static final int MICRO_FADE_OUT_DELAY = 1250; // 1.25 seconds, enough
    //private static final int LONG_FADE_OUT_DELAY = 5000; // 5 seconds, default show time
    private static final int SHORT_FADE_OUT_DELAY = 2500; // 2.5 seconds to show next one

    private static final int OVERLAY_NOTIFICATION_OFFSET = 125; // special purpose

    public boolean mHoverActive;
    public HoverNotification mLastNotification = null; // special purpose

    private boolean mAnimatingVisibility;
    private boolean mAttached;
    private boolean mHiding;
    private boolean mShowing;
    private boolean mUserLocked;
    private int mHoverHeight;
    private BaseStatusBar mStatusBar;
    private Context mContext;
    private DecelerateInterpolator mAnimInterpolator;
    private FrameLayout mNotificationView;
    private Handler mHandler;
    private HoverLayout mHoverLayout;
    private KeyguardManager mKeyguardManager;
    private LayoutInflater mInflater;
    private NotificationHelper mNotificationHelper;
    private PowerManager mPowerManager;
    private Runnable mHideRunnable;
    private Runnable mOverrideRunnable;
    private TelephonyManager mTelephonyManager;
    private WindowManager mWindowManager;

    private ArrayList<HoverNotification> mNotificationList;
    private ArrayList<StatusBarNotification> mStatusBarNotifications;

    private IWindowManager mWindowManagerService;

    /**
     * Creates a new hover instance
     * @Param context the current Context
     * @Param statusBar the current BaseStatusBar
     */
    public Hover(BaseStatusBar statusBar, Context context) {
        mContext = context;
        mStatusBar = statusBar;
        mPowerManager = mStatusBar.getPowerManagerInstance(); // get power manager instance from status bar
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mHoverLayout = (HoverLayout) mInflater.inflate(R.layout.hover_container, null);
        mHoverLayout.setHoverContainer(this);
        mHoverHeight = mContext.getResources().getDimensionPixelSize(R.dimen.default_notification_min_height);
        mNotificationList = new ArrayList<HoverNotification>();
        mStatusBarNotifications = new ArrayList<StatusBarNotification>();
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();

        // root hover view
        mNotificationView = (FrameLayout) mHoverLayout.findViewById(R.id.hover_notification);

        mNotificationHelper = null;

        mHandler = new Handler();

        mHideRunnable = new Runnable() {
            @Override
            public void run() {
                dismissHover(false, false);
            }
        };

        mOverrideRunnable = new Runnable() {
            @Override
            public void run() {
                clearHandlerCallbacks();
                if (hasMultipleNotifications()) {
                    overrideShowingNotification();
                } else {
                    startMicroHideCountdown();
                }
            }
        };

        // initialize system services
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mAnimInterpolator = new DecelerateInterpolator();
    }

    public static String getContentDescription(StatusBarNotification content) {
        if (content != null) {
            String tag = content.getTag() == null ? "null" : content.getTag();
            return content.getPackageName() + DELIMITER + content.getId() + DELIMITER + tag;
        }
        return null;
    }

    public static String getEntryDescription(Entry entry) {
        if (entry != null) {
            return getContentDescription(entry.notification) + DELIMITER + entry.key;
        }
        return null;
    }

    public BaseStatusBar getStatusBar() {
        return mStatusBar;
    }

    public IStatusBarService getStatusBarService() {
        return mStatusBar.getStatusBarService();
    }

    public void setNotificationHelper(NotificationHelper notificationHelper) {
        mNotificationHelper = notificationHelper;
    }

    public void setHoverActive(boolean active) {
        mHoverActive = active;
        if (!active) {
            if (mShowing && !mHiding) {
                dismissHover(true, true);
            } else { // clear everything
                mNotificationView.removeAllViews();
                if (mAttached) {
                    mWindowManager.removeView(mHoverLayout);
                    mAttached = false;
                }
                mShowing = false;
                mHiding = false;
                setTouchOutside(false);
                setAnimatingVisibility(false);
                clearHandlerCallbacks();
                clearNotificationList();
            }
        }
    }

    public void setLocked(boolean locked) {
        mUserLocked = locked;
        // on locked if we expand we need to update the layout
        // to include the expanded view, set to match parent one in the locked interval
        WindowManager.LayoutParams params
                = (WindowManager.LayoutParams) mHoverLayout.getLayoutParams();
        params.height = locked
                ? WindowManager.LayoutParams.MATCH_PARENT
                : WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowManager.updateViewLayout(mHoverLayout, params);
    }

    public void setTouchOutside(boolean touch) {
        mHoverLayout.setTouchOutside(touch);
    }

    public View getCurrentLayout() {
        return getCurrentNotification().getLayout();
    }

    public HoverNotification getCurrentNotification() {
        HoverNotification notif = getHoverNotification(INDEX_CURRENT);
        // use last viewed one, something bad happened if we're here
        // so this is a safe backdoor.
        if (notif == null) notif = mLastNotification;
        return notif;
    }

    private int getCurrentHeight() {
        int height = getCurrentLayout().getHeight();
        return height != 0 ? height : mHoverHeight;
    }

    private int getNewHeight(View layout) {
        int height = layout.getHeight();
        return height != 0 ? height : mHoverHeight;
    }

    private WindowManager.LayoutParams getHoverLayoutParams() {
        WindowManager.LayoutParams lp = getLayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        return lp;
    }

    private WindowManager.LayoutParams getLayoutParams(int width, int height, int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, 0, 0,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = gravity;
        return lp;
    }

    public void updateNotificationLayoutParams(HoverNotification notif) {
        SizeAdaptiveLayout sal = notif.getLayout();
        Entry entry = notif.getEntry();
        if (sal != null && entry != null) {
            int height = mHoverHeight;

            if (sal.getParent() == null) {
                mNotificationView.addView(sal);
            }

            FrameLayout.LayoutParams params
                    = new FrameLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height, Gravity.CENTER_HORIZONTAL);
            sal.setLayoutParams(params);
            sal.setVisibility(!mShowing ? View.GONE : View.VISIBLE);

            // reset all the possible modified parameters
            notif.resetLayoutProperties(mShowing);
        }
    }

    public int getNotificationCount() {
        return mNotificationList.size();
    }

    public boolean isStatusBarExpanded() {
        return mStatusBar.isExpandedVisible();
    }

    public boolean isKeyguardSecureShowing() {
        return mKeyguardManager.isKeyguardLocked() && mKeyguardManager.isKeyguardSecure();
    }

    public boolean isShowing() {
        return mShowing;
    }

    public boolean isScreenOn() {
        return mPowerManager.isScreenOn();
    }

    public boolean isAnimatingOrUserLocked() {
        return mAnimatingVisibility || mUserLocked;
    }

    public boolean isAnimatingVisibility() {
        return mAnimatingVisibility;
    }

    public void setAnimatingVisibility(boolean animating) {
        mAnimatingVisibility = animating;
    }

    public boolean isHiding() {
        return mHiding;
    }

    public boolean isExpanded() {
        return mHoverLayout.getExpanded();
    }

    public boolean isSimPanelShowing() {
        return mNotificationHelper.isSimPanelShowing();
    }

    public boolean isRingingOrConnected() {
        return mNotificationHelper.isRingingOrConnected();
    }

    public boolean isDialpadShowing() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DIALPAD_STATE, 0) != 0;
    }

    public boolean requireFullscreenMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HOVER_REQUIRE_FULLSCREEN_MODE, 1) != 0;
    }

    public boolean excludeNonClearable() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HOVER_EXCLUDE_NON_CLEARABLE, 0) != 0;
    }

    public boolean excludeLowPriority() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HOVER_EXCLUDE_LOW_PRIORITY, 0) != 0;
    }

    public int longFadeOutDelay() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HOVER_LONG_FADE_OUT_DELAY, 5000);
    }

    public boolean excludeTopmost() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HOVER_EXCLUDE_TOPMOST, 1) != 0;
    }

    public boolean isInCallUINotification(Entry entry) {
        if (entry != null) return entry.notification.getPackageName().equals(IN_CALL_UI)
                | entry.notification.getPackageName().equals(DIALER);
        return false;
    }

    public boolean hasNotifications() {
        return getNotificationCount() > 0;
    }

    public boolean hasMultipleNotifications() {
        return getNotificationCount() > 1;
    }

    public boolean isClearable() {
        return getCurrentNotification().getContent().isClearable();
    }

    public boolean isClickable() {
        return getCurrentNotification().getLayout().hasOnClickListeners();
    }

    public void dismissHover(boolean instant, boolean quit) {
        hideCurrentNotification(instant, quit);
    }

    public void showCurrentNotification() {
        final HoverNotification currentNotification = getHoverNotification(INDEX_CURRENT);
        if (currentNotification != null && !isKeyguardSecureShowing() && !isStatusBarExpanded()
                && mHoverActive && !mShowing && isScreenOn() && !isSimPanelShowing()) {
            if (isRingingOrConnected() && isDialpadShowing()) {
                // incoming call notification has been already processed,
                // and since we don't want to show other ones, clear and return.
                clearNotificationList();
                return;
            }

            if (mAnimatingVisibility) return;

            mShowing = true;
            setAnimatingVisibility(true);

            if (!mAttached) {
                mWindowManager.addView(mHoverLayout, getHoverLayoutParams());
                mAttached = true;
            }

            mNotificationHelper.reparentNotificationToHover(currentNotification);

            // don't pull expanded notifications
            updateNotificationLayoutParams(currentNotification);
            currentNotification.getEntry().row.setExpanded(false);

            // hide status bar right before showing hover
            mStatusBar.animateStatusBarOut();

            final View notificationLayout = getCurrentLayout();
            notificationLayout.setY(-getCurrentHeight());
            // safely check if statusbar somehow expanded on starting and on ending
            notificationLayout.animate()
                    .setDuration(ANIMATION_DURATION)
                    .y(0f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (isStatusBarExpanded() | (isRingingOrConnected() && isDialpadShowing())
                            | isKeyguardSecureShowing() | !isScreenOn() | isSimPanelShowing()) {
                        clearHandlerCallbacks();
                        setAnimatingVisibility(false);
                        dismissHover(true, true);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isStatusBarExpanded() | (isRingingOrConnected() && isDialpadShowing())
                            | isKeyguardSecureShowing() | !isScreenOn() | isSimPanelShowing()) {
                        clearHandlerCallbacks();
                        setAnimatingVisibility(false);
                        dismissHover(false, true);
                        return;
                    } else {
                        setAnimatingVisibility(false);
                    }
                    mLastNotification = currentNotification; // backup current notification object
                    // check if there are other notif to override with
                    processOverridingQueue(isExpanded());
                }
            }).setInterpolator(mAnimInterpolator);
        } else {
            // remove any possible stored notifications
            clearNotificationList();
        }
    }

    private void overrideShowingNotification() {
        final HoverNotification currentNotification = getCurrentNotification();
        final HoverNotification nextNotification = getHoverNotification(INDEX_NEXT);

        clearHandlerCallbacks();

        // just to be safe if something bad happened that satisfy these cases
        if (!isScreenOn() | isKeyguardSecureShowing() | isStatusBarExpanded()
                | isRingingOrConnected() | isDialpadShowing() | isSimPanelShowing()) {
            dismissHover(true, true);
            return;
        }

        mShowing = true;
        setAnimatingVisibility(true);

        if (currentNotification != null) {
            // we asume that old notification is the first children on container
            final View currentLayout = mNotificationView.getChildAt(0);

            /**
             * TODO:
             * Animated (with same show duration) reverse expander.
             * Reset expanding before overriding (bad UI):
             * we should animate dexpansion with same duration @ANIMATION_DURATION
             */

            updateNotificationLayoutParams(currentNotification);
            currentNotification.getEntry().row.setExpanded(false);

            // animate current notification out
            currentLayout.animate().setDuration(ANIMATION_DURATION).alpha(0f)
                    .rotationX(90f).yBy(OVERLAY_NOTIFICATION_OFFSET)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mNotificationHelper.reparentNotificationToStatusBar(currentNotification);
                            mNotificationView.removeView(currentLayout);
                            removeNotificationFromList(currentNotification);
                        }
                    });
        } else {
            // run forrest
        }

        if (nextNotification == null) {
            dismissHover(false, true);
            return;
        } else {
            mNotificationHelper.reparentNotificationToHover(nextNotification);
        }

        // update next notification parameters
        updateNotificationLayoutParams(nextNotification);

        final View nextLayout = nextNotification.getLayout();
        nextLayout.setY(-getNewHeight(nextLayout));
        nextLayout.animate().setDuration(ANIMATION_DURATION)
                .y(0f).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLastNotification = nextNotification; // backup current notification object
                setAnimatingVisibility(false);
                setTouchOutside(false);
                // check if there are other notif to override with
                processOverridingQueue(isExpanded());
            }
        }).setInterpolator(mAnimInterpolator);
    }

    public void hideCurrentNotification(final boolean instant, final boolean quit) {
        final HoverNotification currentNotification = getCurrentNotification();
        if (currentNotification != null && mHoverActive && mShowing && !mAnimatingVisibility
                && !mHiding) {
            clearHandlerCallbacks();
            setAnimatingVisibility(true);
            mHiding = true;
            if (mUserLocked) setLocked(false); // unlock if locked

            // show statusbar
            mStatusBar.animateStatusBarIn();

            // animate container to make sure we hide hover
            mNotificationView.animate().yBy(-mNotificationView.getHeight())
                    .setDuration(instant ? 0 : ANIMATION_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mShowing = false;
                            setAnimatingVisibility(false);

                            // relocate view's container
                            mNotificationView.setY(
                                    mNotificationView.getY() + mNotificationView.getHeight());

                            if (!quit) { // else clearNotificationList() takes care
                                // reparent current to status bar and update expansion
                                mNotificationHelper.reparentNotificationToStatusBar(currentNotification);
                                mStatusBar.updateExpansionStates();
                                // remove current hover object from list
                                removeNotificationFromList(currentNotification);
                            }

                            // remove all stored views
                            mNotificationView.removeAllViews();

                            // remove hover container
                            if (mAttached) {
                                mWindowManager.removeView(mHoverLayout);
                                mAttached = false;
                            }

                            mHiding = false;
                            setTouchOutside(false);

                            // check if there are other notif to show
                            if (!quit) {
                                processShowingQueue();
                            } else {
                                // clear everything and go home
                                clearHandlerCallbacks();
                                clearNotificationList();
                                mStatusBar.updateExpansionStates();
                            }
                        }
                    }).setInterpolator(mAnimInterpolator);
        } else {
            // take a break
        }
    }

    // callbacks to handle the queue
    public void startLongHideCountdown() {
        startHideCountdown(longFadeOutDelay());
    }

    public void startShortHideCountdown() {
        startHideCountdown(SHORT_FADE_OUT_DELAY);
    }

    public void startMicroHideCountdown() {
        startHideCountdown(MICRO_FADE_OUT_DELAY);
    }

    public void startLongOverrideCountdown() {
        startOverrideCountdown(longFadeOutDelay());
    }

    public void startShortOverrideCountdown() {
        startOverrideCountdown(SHORT_FADE_OUT_DELAY);
    }

    public void startHideCountdown(int delay) {
        mHandler.postDelayed(mHideRunnable, delay);
    }

    public void startOverrideCountdown(int delay) {
        mHandler.postDelayed(mOverrideRunnable, delay);
    }

    public void clearHandlerCallbacks() {
        mHandler.removeCallbacksAndMessages(null); // wipe everything
    }

    // notifications processing
    public void setNotification(Entry entry, boolean update) {
        // first, check if current notification's package is blacklisted or excluded in another way
        boolean allowed = true; // default on

        //Exclude blacklisted
        try {
            final String packageName = entry.notification.getPackageName();
            allowed = mStatusBar.getNotificationManager().isPackageAllowedForHover(packageName);
        } catch (android.os.RemoteException ex) {
            // System is dead
        }

        //Check for fullscreen mode
        if (requireFullscreenMode()) {
            int vis = 0;
            try {
                vis = mWindowManagerService.getSystemUIVisibility();
            } catch (android.os.RemoteException ex) {
            }
            final boolean isStatusBarVisible = (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                    || (vis & View.STATUS_BAR_TRANSIENT) != 0;
            if (isStatusBarVisible)
                allowed = false;
        }

        //Exclude non-clearable
        if (!entry.notification.isClearable() && excludeNonClearable())
            allowed = false;

        //Exclude low priority
        if (excludeLowPriority() && entry.notification.getNotification().priority < Notification.PRIORITY_LOW)
            allowed = false;

        //Exclude topmost app
        if (excludeTopmost() && entry.notification.getPackageName().equals(
                mNotificationHelper.getForegroundPackageName())) {
            allowed = false;
        }

        if (!allowed) {
            addStatusBarNotification(entry.notification);
            return;
        }

        // second, if we've just expanded statusbar or turned screen off return
        if (!isScreenOn() | isStatusBarExpanded() | isKeyguardSecureShowing()) {
            if (mShowing) {
                dismissHover(true, true);
            } else {
                clearNotificationList();
            }
            addStatusBarNotification(entry.notification);
            return;
        }

        // third, when ringing or connected and dialpad is showing don't show notifications
        if (isScreenOn() && isRingingOrConnected()) {
            // incoming call in background enabled? continue
            if (!isDialpadShowing()) {
                if (!isInCallUINotification(entry)) {
                    // return we just need filter phone's notifications if we're here
                    addStatusBarNotification(entry.notification);
                    return;
                }
            } else {
                // we show incoming call notifications only if background mode is
                // enabled and dialpad is not showing, so exit if we're here.
                addStatusBarNotification(entry.notification);
                return;
            }

        }

        // define params
        SizeAdaptiveLayout sal =
                (SizeAdaptiveLayout) entry.row.findViewById(R.id.adaptive);
        HoverNotification notif = null;

        // figure out which type of notification we have and do our boolean checks
        boolean show = shouldDisplayNotification(entry);
        boolean isOnList = isNotificationOnList(entry);

        if (!update && show) {
            notif = new HoverNotification(entry, sal);
            addNotificationToList(notif);
        } else {
            if (!isOnList && show) {
                notif = new HoverNotification(entry, sal);
                addNotificationToList(notif);
            } else if (isOnList && show) {
                notif = getNotificationForEntry(entry);
                // if updates are for current notification update click listener
                HoverNotification current = getCurrentNotification();
                if (current != null && getEntryDescription(current.getEntry()).equals(getEntryDescription(entry))) {
                    current.setEntry(entry);
                    View child = mNotificationView.getChildAt(0);
                    if (child != null) {
                        child.setTag(getContentDescription(entry.notification));
                        child.setOnClickListener(null); // remove current
                        child.setOnClickListener(mNotificationHelper.getNotificationClickListener(entry, true));
                    }
                }
            } else {
                // uh spam detected...go away!
                addStatusBarNotification(entry.notification);
                return;
            }
        }

        // here we have already done spam checks and can finally add
        // it to the status bar array before we check if we need to show it
        addStatusBarNotification(entry.notification);

        // call showCurrentNotification() only if is not showing,
        // if not will clear all notifications, that is even safe
        // but unneeded (@link showCurrentNotification())
        if (!mShowing) showCurrentNotification();
    }

    public void processShowingQueue() {
        if (!mShowing) {
            if (hasNotifications()) { // proced
                showCurrentNotification();
            }
        } else if (!mHiding) {
            startLongHideCountdown();
        }
    }

    public void processOverridingQueue(boolean expanded) {
        clearHandlerCallbacks();
        if (!mShowing) {
            showCurrentNotification();
        } else if (hasMultipleNotifications()) { // proced
            startOverrideCountdown(expanded ? longFadeOutDelay() : SHORT_FADE_OUT_DELAY);
        } else if (!mHiding) {
            startLongHideCountdown();
        }
    }

    public void removeNotificationView(HoverNotification notif) {
        for (int i = 0; i < mNotificationView.getChildCount(); i++) {
            View child = mNotificationView.getChildAt(i);
            if (notif.toString().equals(child.getTag())) {
                mNotificationView.removeView(child);
            }
        }
    }

    public void addStatusBarNotification(StatusBarNotification n) {
        for (int i = 0; i < mStatusBarNotifications.size(); i++) {
            if (NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(mStatusBarNotifications.get(i)))) {
                mStatusBarNotifications.set(i, n);
                return;
            }
        }
        mStatusBarNotifications.add(n);
    }

    public void addNotificationToList(HoverNotification notif) {
        for (int i = 0; i < mNotificationList.size(); i++) {
            if (getContentDescription(notif.getContent())
                    .equals(getContentDescription(mNotificationList.get(i).getContent()))) {
                mNotificationList.set(i, notif);
                return;
            }
        }
        mNotificationList.add(notif);
    }

    public void removeStatusBarNotification(StatusBarNotification n) {
        for (int i = 0; i < mStatusBarNotifications.size(); i++) {
            if (NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(mStatusBarNotifications.get(i)))) {
                mStatusBarNotifications.remove(i);
                i--;
            }
        }
    }

    public void removeNotificationFromList(HoverNotification notif) {
        for (int i = 0; i < mNotificationList.size(); i++) {
            if (notif.toString().equals(mNotificationList.get(i).toString())) {
                mNotificationList.remove(i);
                break;
            }
        }
    }

    public void removeNotification(Entry entry) {
        HoverNotification notif = getNotificationForEntry(entry);

        if (notif == null) {
            if (entry != null) removeStatusBarNotification(entry.notification);
            return; // notification not added to the list
        }

        HoverNotification current = getCurrentNotification();

        if (mShowing) {
            if (current != null &&
                    getContentDescription(current.getEntry().notification)
                            .equals(getContentDescription(notif.getEntry().notification))) {
                // will be removed after animating just insta-hide
                clearHandlerCallbacks();
                dismissHover(false, false);
            } else {
                // gotta remove from temp stored list
                if (DEBUG) Log.d(TAG, "Removing notification: {notification: " + notif + "}");
                removeNotificationView(notif);
                removeNotificationFromList(notif);
            }
        } else if (!mShowing && isNotificationOnList(entry)) {
            // gotta remove from temp stored list
            if (DEBUG) Log.d(TAG, "Removing notification: {notification: " + notif + "}");
            removeNotificationView(notif);
            removeNotificationFromList(notif);
        }

        if (entry != null) removeStatusBarNotification(entry.notification);
    }

    public void clearNotificationList() {
        reparentAllNotifications();
        mNotificationList.clear();
    }

    public void reparentAllNotifications() {
        // force reparenting all temp stored notifications to status bar
        for (HoverNotification stored : mNotificationList) {
            mNotificationHelper.reparentNotificationToStatusBar(stored);
        }
        mStatusBar.updateExpansionStates();
    }

    public boolean isNotificationOnList(Entry entry) {
        for (HoverNotification notification : mNotificationList) {
            if (getContentDescription(notification.getEntry().notification)
                    .equals(getContentDescription(entry.notification))) return true;
        }
        return false;
    }

    public boolean isCurrentNotificationOnList() {
        return isNotificationOnList(getCurrentNotification().getEntry());
    }

    public boolean shouldDisplayNotification(Entry newEntry) {
        // compare with pre-current status bar notification(s) stored in a special array
        for (StatusBarNotification stored : mStatusBarNotifications) {
            if (mNotificationHelper.getContentDescription(newEntry.notification).equals(
                    mNotificationHelper.getContentDescription(stored))) {
                return mNotificationHelper.shouldDisplayNotification(stored, newEntry.notification, true);
            }
        }
        return true;
    }

    public HoverNotification getHoverNotification(int index) {
        if (getNotificationCount() > 0) {
            return mNotificationList.get(index);
        }
        return null;
    }

    public HoverNotification getNotificationForEntry(Entry entry) {
        for (HoverNotification notif : mNotificationList) {
            if (getEntryDescription(notif.getEntry()).equals(getEntryDescription(entry))) return notif;
        }
        return null;
    }
}
