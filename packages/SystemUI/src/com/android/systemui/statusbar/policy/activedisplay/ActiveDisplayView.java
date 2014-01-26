/*
 * Copyright (C) 2013 The ChameleonOS Project
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
package com.android.systemui.statusbar.policy.activedisplay;

import android.animation.ObjectAnimator;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.INotificationManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.INotificationListener;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.internal.widget.LockPatternUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ActiveDisplayView extends FrameLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "ActiveDisplayView";

    private static final String ACTION_REDISPLAY_NOTIFICATION
            = "com.android.systemui.action.REDISPLAY_NOTIFICATION";

    private static final String ACTION_DISPLAY_TIMEOUT
            = "com.android.systemui.action.DISPLAY_TIMEOUT";

    private static final int MAX_OVERFLOW_ICONS = 8;

    private static final int HIDE_NOTIFICATIONS_BELOW_SCORE = Notification.PRIORITY_LOW;

    // the different pocket mode options
    private static final int POCKET_MODE_OFF = 0;
    private static final int POCKET_MODE_NOTIFICATIONS_ONLY = 1;
    private static final int POCKET_MODE_ACTIVE_DISPLAY = 2;

    // Targets
    private static final int UNLOCK_TARGET = 0;
    private static final int OPEN_APP_TARGET = 4;
    private static final int DISMISS_TARGET = 6;

    // messages sent to the handler for processing
    private static final int MSG_SHOW_NOTIFICATION_VIEW = 1000;
    private static final int MSG_HIDE_NOTIFICATION_VIEW = 1001;
    private static final int MSG_SHOW_NOTIFICATION      = 1002;
    private static final int MSG_DISMISS_NOTIFICATION   = 1004;

    private BaseStatusBar mBar;
    private KeyguardManager mKeyguardManager;
    
    private GlowPadView mGlowPadView;
    private View mRemoteView;
    private View mClock;
    private FrameLayout mRemoteViewLayout;
    private FrameLayout mContents;
    private ObjectAnimator mAnim;
    private Drawable mNotificationDrawable;
    private int mCreationOrientation;
    private SettingsObserver mSettingsObserver;
    private IPowerManager mPM;
    private INotificationManager mNM;
    private INotificationListenerWrapper mNotificationListener;
    private StatusBarNotification mNotification;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private boolean mProximityIsFar = true;
    private LinearLayout mOverflowNotifications;
    private LayoutParams mRemoteViewLayoutParams;
    private int mIconSize;
    private int mIconMargin;
    private int mIconPadding;
    private long mPocketTime = 0;
    private long mResetTime = 0;
    private LinearLayout.LayoutParams mOverflowLayoutParams;
    private boolean mCallbacksRegistered = false;
    private boolean mShow = true;

    // user customizable settings
    private boolean mDisplayNotifications = false;
    private boolean mDisplayNotificationText = false;
    private boolean hideNonClearable = true;
    private boolean mHideLowPriorityNotifications = false;
    private int mPocketMode = POCKET_MODE_OFF;
    private boolean privacyMode = false;
    private boolean mQuietTime;
    private long mRedisplayTimeout = 0;
    private float mInitialBrightness = 1f;
    private int mBrightnessMode = -1;
    private int mUserBrightnessLevel = -1;
    private Set<String> mExcludedApps = new HashSet<String>();
    private long mDisplayTimeout = 8000L;
    private long mProximityThreshold = 5000L;
    private boolean mDistanceFar;
    private boolean mWaitPeriod = true;
    private boolean mAttached;

    /**
     * Simple class that listens to changes in notifications
     */
    private class INotificationListenerWrapper extends INotificationListener.Stub {
        @Override
        public void onNotificationPosted(final StatusBarNotification sbn) {
            if (inQuietHours() && mQuietTime) return;
            if (shouldShowNotification() && isValidNotification(sbn) && mShow) {
                // need to make sure either the screen is off or the user is currently
                // viewing the notifications
                if (ActiveDisplayView.this.getVisibility() == View.VISIBLE
                        || !isScreenOn())
                    showNotification(sbn, true);
                    mShow = false;
            }
        }
        @Override
        public void onNotificationRemoved(final StatusBarNotification sbn) {
            if (mNotification != null && sbn.getPackageName().equals(mNotification.getPackageName())) {
                if (getVisibility() == View.VISIBLE) {
                    mNotification = getNextAvailableNotification();
                    if (mNotification != null) {
                        setActiveNotification(mNotification, true);
                        isUserActivity();
                        return;
                    }
                } else {
                    mNotification = null;
                }
            }
        }
    }

    private OnTriggerListener mOnTriggerListener = new OnTriggerListener() {

        public void onTrigger(final View v, final int target) {
            if (target == UNLOCK_TARGET) {
                disableProximitySensor();
                mNotification = null;
                hideNotificationView();
                unlockKeyguardActivity();
                mPocketTime = 0;
                Intent intent = new Intent(mContext, DummyActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
            } else if (target == OPEN_APP_TARGET) {
                disableProximitySensor();
                hideNotificationView();
                unlockKeyguardActivity();
                launchNotificationPendingIntent();
            } else if (target == DISMISS_TARGET) {
                enableProximitySensor();
                dismissNotification();
                mNotification = getNextAvailableNotification();
                if (mNotification != null) {
                    setActiveNotification(mNotification, true);
                    invalidate();
                    mGlowPadView.ping();
                    isUserActivity();
                    return;
                }
            }
        }

        public void onReleased(final View v, final int handle) {
            initWaitPeriod();
            doTransition(mOverflowNotifications, 1.0f, 0);
            if (!privacyMode) {
                if (mRemoteView != null) {
                    ObjectAnimator.ofFloat(mRemoteView, "alpha", 0f).start();
                    ObjectAnimator.ofFloat(mClock, "alpha", 1f).start();
                }
            }
            // user stopped interacting so kick off the timeout timer
            updateTimeoutTimer();
        }

        public void onGrabbed(final View v, final int handle) {
            mWaitPeriod = true;
            // prevent the ActiveDisplayView from turning off while user is interacting with it
            cancelTimeoutTimer();
            restoreBrightness();
            doTransition(mOverflowNotifications, 0.0f, 0);
            if (!privacyMode) {
                if (mRemoteView != null) {
                    ObjectAnimator.ofFloat(mRemoteView, "alpha", 1f).start();
                    ObjectAnimator.ofFloat(mClock, "alpha", 0f).start();
                }
            }
        }

        public void onGrabbedStateChange(final View v, final int handle) {
        }

        public void onTargetChange(View v, int target) {
        }

        public void onFinishFinalAnimation() {
        }
    };

    /**
     * Class used to listen for changes to active display related settings
     */
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver =
                    ActiveDisplayView.this.mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_ACTIVE_DISPLAY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_ALL_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_REDISPLAY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_BRIGHTNESS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_THRESHOLD), false, this);
            update();
        }

        void unobserve() {
            ActiveDisplayView.this.mContext.getContentResolver()
                    .unregisterContentObserver(this);
            unregisterCallbacks();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver =
                    ActiveDisplayView.this.mContext.getContentResolver();
            boolean mNotOverridden;

            mNotOverridden = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1;
            mDisplayNotifications = Settings.System.getInt(
                    resolver, Settings.System.ENABLE_ACTIVE_DISPLAY, 0) == 1;
            mQuietTime = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS, 0) == 1;
            privacyMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE, 0) == 1;
            mDisplayNotificationText = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_DISPLAY_TEXT, 0) == 1;
            hideNonClearable = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE, 1) == 0;
            mHideLowPriorityNotifications = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY, 0) == 1;
            mPocketMode = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE, POCKET_MODE_ACTIVE_DISPLAY);
            mRedisplayTimeout = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_REDISPLAY, 0L);
            mInitialBrightness = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_DISPLAY_BRIGHTNESS, 100) / 100f;
            String excludedApps = Settings.System.getString(resolver,
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS);
            mDisplayTimeout = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_TIMEOUT, 8000L);
            mProximityThreshold = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_THRESHOLD, 8000L);

            if (!mNotOverridden) {
                mDisplayNotifications = false;
            } else {

                createExcludedAppsSet(excludedApps);

                int brightnessMode = Settings.System.getInt(
                        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
                if (mBrightnessMode != brightnessMode) {
                    mBrightnessMode = brightnessMode;
                    mUserBrightnessLevel = -1;
                }

                if (!mDisplayNotifications || mRedisplayTimeout <= 0) {
                    cancelRedisplayTimer();
                }


                registerCallbacks();
            }
            if (!mDisplayNotifications || !mNotOverridden) {
                unregisterCallbacks();
            }
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_SHOW_NOTIFICATION_VIEW:
                    handleShowNotificationView();
                    break;
                case MSG_HIDE_NOTIFICATION_VIEW:
                    handleHideNotificationView();
                    break;
                case MSG_SHOW_NOTIFICATION:
                    boolean ping = msg.arg1 == 1;
                    handleShowNotification(ping);
                    break;
                case MSG_DISMISS_NOTIFICATION:
                    handleDismissNotification();
                    break;
                default:
                    break;
            }
        }
    };

    public ActiveDisplayView(Context context) {
        this(context, null);
    }

    public ActiveDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        mPM = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        mNM = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mNotificationListener = new INotificationListenerWrapper();

        mIconSize = getResources().getDimensionPixelSize(R.dimen.overflow_icon_size);
        mIconMargin = getResources().getDimensionPixelSize(R.dimen.ad_notification_margin);
        mIconPadding = getResources().getDimensionPixelSize(R.dimen.overflow_icon_padding);

        mSettingsObserver = new SettingsObserver(new Handler());
        mCreationOrientation = Resources.getSystem().getConfiguration().orientation;
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContents = (FrameLayout) findViewById(R.id.active_view_contents);
        makeActiveDisplayView(mCreationOrientation, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        if (mRedisplayTimeout > 0 && !isScreenOn()) updateRedisplayTimer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        makeActiveDisplayView(newConfig.orientation, true);
    }

    private void makeActiveDisplayView(int orientation, boolean recreate) {
        mContents.removeAllViews();
        View contents = View.inflate(mContext, R.layout.active_display_content, mContents);
        mGlowPadView = (GlowPadView) contents.findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);
        mGlowPadView.setDrawOuterRing(false);
        TargetDrawable nDrawable = new TargetDrawable(getResources(),
                R.drawable.ic_handle_notification_normal);
        mGlowPadView.setHandleDrawable(nDrawable);

        mRemoteViewLayout = (FrameLayout) contents.findViewById(R.id.remote_content_parent);
        mClock = contents.findViewById(R.id.clock_view);

        mOverflowNotifications = (LinearLayout) contents.findViewById(R.id.keyguard_other_notifications);
        mOverflowNotifications.setOnTouchListener(mOverflowTouchListener);

        mRemoteViewLayoutParams = getRemoteViewLayoutParams(orientation);
        mOverflowLayoutParams = getOverflowLayoutParams();
        updateTargets();
        if (recreate) {
            updateTimeoutTimer();
            if (mNotification == null) {
                mNotification = getNextAvailableNotification();
            }
            showNotification(mNotification, true);
        }
    }

    private FrameLayout.LayoutParams getRemoteViewLayoutParams(int orientation) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.notification_min_height),
                orientation == Configuration.ORIENTATION_LANDSCAPE ? Gravity.CENTER : Gravity.TOP);
        return lp;
    }

    private LinearLayout.LayoutParams getOverflowLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mIconSize,
                mIconSize);
        lp.setMargins(mIconMargin, 0, mIconMargin, 0);
        return lp;
    }

    private StateListDrawable getLayeredDrawable(Drawable back, Drawable front, int inset, boolean frontBlank) {
        Resources res = getResources();
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];
        inactivelayer[0] = new InsetDrawable(
                res.getDrawable(R.drawable.ic_ad_lock_pressed), 0, 0, 0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(
                frontBlank ? res.getDrawable(android.R.color.transparent) : front, inset, inset, inset, inset);
        StateListDrawable states = new StateListDrawable();
        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);
        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);
        return states;
    }

    private void updateTargets() {
        updateResources();
    }

    public void updateResources() {
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();
        final Resources res = getResources();
        final int targetInset = res.getDimensionPixelSize(com.android.internal.R.dimen.lockscreen_target_inset);
        final Drawable blankActiveDrawable =
                res.getDrawable(R.drawable.ic_lockscreen_target_activated);
        final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);

        // Add unlock target
        storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_ad_target_unlock)));
        if (mNotificationDrawable != null) {
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, getLayeredDrawable(activeBack,
                    mNotificationDrawable, targetInset, false)));
            storedDraw.add(new TargetDrawable(res, null));
            if (mNotification != null && mNotification.isClearable()) {
                storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_ad_dismiss_notification)));
            } else {
                storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_qs_power)));
            }
        }
        storedDraw.add(new TargetDrawable(res, null));
        mGlowPadView.setTargetResources(storedDraw);
    }

    private void doTransition(View view, float to, long duration) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = ObjectAnimator.ofFloat(view, "alpha", to);
        if (duration > 0) mAnim.setDuration(duration);
        mAnim.start();
    }

    /**
     * Launches the pending intent for the currently selected notification
     */
    private void launchNotificationPendingIntent() {
        if (mNotification != null) {
            PendingIntent i = mNotification.getNotification().contentIntent;
            if (i != null) {
                try {
                    Intent intent = i.getIntent();
                    intent.setFlags(
                        intent.getFlags()
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    if (i.isActivity()) ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    i.send();
                    KeyguardTouchDelegate.getInstance(mContext).dismiss();
                } catch (CanceledException ex) {
                } catch (RemoteException ex) {
                }
            }
            if (mNotification.isClearable()) {
                try {
                     mNM.cancelNotificationFromSystemListener(mNotificationListener,
                             mNotification.getPackageName(), mNotification.getTag(),
                             mNotification.getId());
                } catch (RemoteException e) {
                } catch (NullPointerException npe) {
                }
            }
            mNotification = null;
        }
        handleForceHideNotificationView();
    }

    private void showNotificationView() {
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_SHOW_NOTIFICATION_VIEW);
    }

    private void hideNotificationView() {
        mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);
    }

    private void showNotification(StatusBarNotification sbn, boolean ping) {
        mNotification = sbn;
        Message msg = new Message();
        msg.what = MSG_SHOW_NOTIFICATION;
        msg.arg1 = ping ? 1 : 0;
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION);
        mHandler.sendMessage(msg);
    }

    private void dismissNotification() {
        mHandler.removeMessages(MSG_DISMISS_NOTIFICATION);
        mHandler.sendEmptyMessage(MSG_DISMISS_NOTIFICATION);
    }

    private final Runnable runSystemUiVisibilty = new Runnable() {
        public void run() {
            adjustStatusBarLocked(1);
        }
    };

    private void adjustStatusBarLocked(int show) {
        int flags = 0x00000000;
        if (show == 1) {
            flags = getSystemUiVisibility() | STATUS_BAR_DISABLE_BACK
                    | STATUS_BAR_DISABLE_HOME | STATUS_BAR_DISABLE_RECENT
                    | STATUS_BAR_DISABLE_SEARCH | STATUS_BAR_DISABLE_CLOCK;
        } else if (show == 2) {
            flags = getSystemUiVisibility() | STATUS_BAR_DISABLE_BACK
                    | STATUS_BAR_DISABLE_HOME | STATUS_BAR_DISABLE_RECENT
                    | STATUS_BAR_DISABLE_CLOCK;
        }
        mBar.disable(flags);
        mShow = true;
    }

    private void setSystemUIVisibility(/*boolean visible*/) {
        //  FRRT INSERT DIARRHEA STAIN UGHHHHH FRRRRRT TOO MANY TACOS FRRRT
        int newVis = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | SYSTEM_UI_FLAG_LAYOUT_STABLE //;
   //     if (!visible) {
    /*    int newVis |=*/| SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                //   | SYSTEM_UI_FLAG_FULLSCREEN
                   /*| SYSTEM_UI_FLAG_HIDE_NAVIGATION        */;
   //     }

        // kitkat or bust.
        setSystemUiVisibility(newVis);
    }

    private void unlockKeyguardActivity() {
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            ActivityManagerNative.getDefault().resumeAppSwitches();
            if(!isScreenOn()) {
                turnScreenOn();
            }
        } catch (RemoteException e) {
        }
        handleForceHideNotificationView();
    }

    private void handleShowNotificationView() {
        setVisibility(View.VISIBLE);
        setSystemUIVisibility(/*false*/);
        mHandler.postDelayed(runSystemUiVisibilty, 100);
    }

    private void handleHideNotificationView() {
        mHandler.removeCallbacks(runSystemUiVisibilty);
        setVisibility(View.GONE);
        restoreBrightness();
        cancelTimeoutTimer();
        if (isLockScreenDisabled()) {
            adjustStatusBarLocked(0);
        } else {
            adjustStatusBarLocked(2);
        }
    }

    private void handleForceHideNotificationView() {
        mHandler.removeCallbacks(runSystemUiVisibilty);
        setVisibility(View.GONE);
        restoreBrightness();
        cancelTimeoutTimer();
        adjustStatusBarLocked(0);
    }

    private void handleShowNotification(boolean ping) {
        if (!mDisplayNotifications) return;
        handleShowNotificationView();
        setActiveNotification(mNotification, true);
        inflateRemoteView(mNotification);
        if (!isScreenOn()) {
            turnScreenOn();
        }
        if (ping) mGlowPadView.ping();
    }

    private void handleDismissNotification() {
        if (mNotification != null && mNotification.isClearable()) {
            try {
                mNM.cancelNotificationFromSystemListener(mNotificationListener,
                        mNotification.getPackageName(), mNotification.getTag(),
                        mNotification.getId());
            } catch (RemoteException e) {
            } catch (NullPointerException npe) {
            } finally {
                if (mRemoteView != null) mRemoteViewLayout.removeView(mRemoteView);
            }
            mNotification = getNextAvailableNotification();
            if (mNotification != null) {
                setActiveNotification(mNotification, true);
                inflateRemoteView(mNotification);
                invalidate();
                mGlowPadView.ping();
                isUserActivity();
                return;
            }
        }
        // no other notifications to display so turn screen off
        if (mNotification == null) return;
        turnScreenOff();
    }

    private void onScreenTurnedOn() {
        cancelRedisplayTimer();
        if (mPocketMode == 2) {
            mResetTime = System.currentTimeMillis();
        }
    }

    private void onScreenTurnedOff() {
        cancelTimeoutTimer();
        if (mRedisplayTimeout > 0) updateRedisplayTimer();
        hideNotificationView();
        if (mPocketMode == 2) {
            // delay initial proximity sample here
            mPocketTime = System.currentTimeMillis();
            enableProximitySensor();
        } else {
            return;
        }
    }

    private void turnScreenOff() {
        mHandler.removeCallbacks(runWakeDevice);
        try {
            mPM.goToSleep(SystemClock.uptimeMillis(), 0);
        } catch (RemoteException e) {
        }
    }

    private void turnScreenOn() {
        if (mPocketMode == 2 && !mDistanceFar) return;
        initWaitPeriod();
        // to avoid flicker and showing any other screen than the ActiveDisplayView
        // we use a runnable posted with a 250ms delay to turn wake the device
        mHandler.removeCallbacks(runWakeDevice);
        mHandler.postDelayed(runWakeDevice, 250);
    }

    private void initWaitPeriod() {
        mWaitPeriod = true;
        // delay proximitiy events by 2 seconds
        mHandler.removeCallbacks(setWaitPeriod);
        mHandler.postDelayed(setWaitPeriod, 2250);
    }

    private final Runnable setWaitPeriod = new Runnable() {
        public void run() {
            mWaitPeriod = false;
        }
    };

    private final Runnable runWakeDevice = new Runnable() {
        public void run() {
            setBrightness(mInitialBrightness);
            wakeDevice();
            doTransition(ActiveDisplayView.this, 1f, 1000);
        }
    };

    private boolean isScreenOn() {
        try {
            return mPM.isScreenOn();
        } catch (RemoteException e) {
        }
        return false;
    }

    private void enableProximitySensor() {
        if (mDisplayNotifications) {
            registerSensorListener(mProximitySensor);
        }
    }

    private void disableProximitySensor() {
        if (mProximitySensor != null) {
            unregisterSensorListener(mProximitySensor);
        }
    }

    private void setBrightness(float brightness) {
        final ContentResolver resolver = mContext.getContentResolver();
        mBrightnessMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        if (mBrightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            mUserBrightnessLevel = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS,
                    android.os.PowerManager.BRIGHTNESS_ON);
            final int dim = getResources().getInteger(
                    com.android.internal.R.integer.config_screenBrightnessDim);
            int level = (int)((android.os.PowerManager.BRIGHTNESS_ON - dim) * brightness) + dim;
            Settings.System.putInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            try {
                mPM.setTemporaryScreenBrightnessSettingOverride(level);
            } catch (RemoteException e) {
            }
        }
    }

    private void restoreBrightness() {
        if (mUserBrightnessLevel < 0 || mBrightnessMode < 0
                || mBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            return;
        }
        final ContentResolver resolver = mContext.getContentResolver();
        try {
            mPM.setTemporaryScreenBrightnessSettingOverride(mUserBrightnessLevel);
        } catch (RemoteException e) {
        }
        Settings.System.putInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mBrightnessMode);
    }

    private void isUserActivity() {
        restoreBrightness();
        updateTimeoutTimer();
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_REDISPLAY_NOTIFICATION);
        filter.addAction(ACTION_DISPLAY_TIMEOUT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_KEYGUARD_TARGET);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    private void unregisterBroadcastReceiver() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    private void registerNotificationListener() {
        ComponentName cn = new ComponentName(mContext, getClass().getName());
        try {
            mNM.registerListener(mNotificationListener, cn, UserHandle.USER_ALL);
        } catch (RemoteException e) {
        }
    }

    private void unregisterNotificationListener() {
        if (mNotificationListener != null) {
            try {
                mNM.unregisterListener(mNotificationListener, UserHandle.USER_ALL);
            } catch (RemoteException e) {
            }
        }
    }

    private void registerSensorListener(Sensor sensor) {
        if (sensor != null && !mAttached) {
            mSensorManager.registerListener(mSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            mAttached = true;
        }
    }

    private void unregisterSensorListener(Sensor sensor) {
        if (sensor != null) {
            mSensorManager.unregisterListener(mSensorListener, sensor);
            mAttached = false;
        }
    }

    private void registerCallbacks() {
        if (!mCallbacksRegistered) {
            registerBroadcastReceiver();
            registerNotificationListener();
            mCallbacksRegistered = true;
        }
    }

    private void unregisterCallbacks() {
        if (mCallbacksRegistered) {
            unregisterBroadcastReceiver();
            unregisterNotificationListener();
            mCallbacksRegistered = false;
        }
    }

    private StatusBarNotification getNextAvailableNotification() {
        try {
            // check if other notifications exist and if so display the next one
            StatusBarNotification[] sbns = mNM
                    .getActiveNotificationsFromSystemListener(mNotificationListener);
            if (sbns == null) return null;
            for (int i = sbns.length - 1; i >= 0; i--) {
                if (sbns[i] == null)
                    continue;
                if (shouldShowNotification() && isValidNotification(sbns[i])) {
                    return sbns[i];
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    private void updateOtherNotifications() {
        mOverflowNotifications.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // check if other clearable notifications exist and if so display the next one
                    StatusBarNotification[] sbns = mNM
                            .getActiveNotificationsFromSystemListener(mNotificationListener);
                    mOverflowNotifications.removeAllViews();
                    for (int i = sbns.length - 1; i >= 0; i--) {
                        if (isValidNotification(sbns[i])
                                && mOverflowNotifications.getChildCount() < MAX_OVERFLOW_ICONS) {
                            ImageView iv = new ImageView(mContext);
                            if (mOverflowNotifications.getChildCount() < (MAX_OVERFLOW_ICONS - 1)) {
                                Drawable iconDrawable = null;
                                try {
                                    Context pkgContext = mContext.createPackageContext(
                                            sbns[i].getPackageName(), Context.CONTEXT_RESTRICTED);
                                    iconDrawable = pkgContext.getResources()
                                            .getDrawable(sbns[i].getNotification().icon);
                                } catch (NameNotFoundException nnfe) {
                                    iconDrawable = mContext.getResources()
                                            .getDrawable(R.drawable.ic_ad_unknown_icon);
                                } catch (Resources.NotFoundException nfe) {
                                    iconDrawable = mContext.getResources()
                                            .getDrawable(R.drawable.ic_ad_unknown_icon);
                                }
                                iv.setImageDrawable(iconDrawable);
                                iv.setTag(sbns[i]);
                                if (sbns[i].getPackageName().equals(mNotification.getPackageName())
                                        && sbns[i].getId() == mNotification.getId()) {
                                    iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                } else {
                                    iv.setBackgroundResource(0);
                                }
                            } else {
                                iv.setImageResource(R.drawable.ic_ad_morenotifications);
                            }
                            iv.setPadding(mIconPadding, mIconPadding, mIconPadding, mIconPadding);
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            mOverflowNotifications.addView(iv, mOverflowLayoutParams);
                        }
                    }
                } catch (RemoteException re) {
                } catch (NullPointerException npe) {
                }
            }
        });
    }

    private OnTouchListener mOverflowTouchListener = new OnTouchListener() {
        int mLastChildPosition = -1;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mLastChildPosition = -1;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    final int childCount = mOverflowNotifications.getChildCount();
                    Rect hitRect = new Rect();
                    for (int i = 0; i < childCount; i++) {
                        final ImageView iv = (ImageView) mOverflowNotifications.getChildAt(i);
                        final StatusBarNotification sbn = (StatusBarNotification) iv.getTag();
                        iv.getHitRect(hitRect);
                        if (i != mLastChildPosition ) {
                            if (hitRect.contains((int)x, (int)y)) {
                                mLastChildPosition = i;
                                if (sbn != null) {
                                    swapNotification(sbn);
                                    iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                }
                            } else {
                                iv.setBackgroundResource(0);
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    inflateRemoteView(mNotification);
                    break;
            }
            isUserActivity();
            return true;
        }
    };

    /**
     * Swaps the current StatusBarNotification with {@code sbn}
     * @param sbn The StatusBarNotification to swap with the current
     */
    private void swapNotification(StatusBarNotification sbn) {
        mNotification = sbn;
        setActiveNotification(sbn, false);
    }

    /**
     * Determine if a given notification should be used.
     * @param sbn StatusBarNotification to check.
     * @return True if it should be used, false otherwise.
     */
    private boolean isValidNotification(StatusBarNotification sbn) {
        return (!mExcludedApps.contains(sbn.getPackageName()) && !isOnCall()
                && sbn.getNotification().icon != 0 && (sbn.isClearable() || !hideNonClearable)
                && !(mHideLowPriorityNotifications && sbn.getNotification().priority < HIDE_NOTIFICATIONS_BELOW_SCORE));
    }

    /**
     * Determine if we should show notifications or not.
     * @return True if we should show this view.
     */
    private boolean shouldShowNotification() {
        if (mPocketMode != 2) return true;

        if (mDistanceFar) {
            return true;
        }
        return false;
    }

    /**
     * Wakes the device up and turns the screen on.
     */
    private void wakeDevice() {
        try {
            mPM.wakeUp(SystemClock.uptimeMillis());
        } catch (RemoteException e) {
        }
        updateTimeoutTimer();
    }

    /**
     * Determine i a call is currently in progress.
     * @return True if a call is in progress.
     */
    private boolean isOnCall() {
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    /**
     * Sets {@code sbn} as the current notification inside the ring.
     * @param sbn StatusBarNotification to be placed as the current one.
     * @param updateOthers Set to true to update the overflow notifications.
     */
    private void setActiveNotification(final StatusBarNotification sbn, final boolean updateOthers) {
        try {
            Context pkgContext = mContext.createPackageContext(sbn.getPackageName(), Context.CONTEXT_RESTRICTED);
            mNotificationDrawable = pkgContext.getResources().getDrawable(sbn.getNotification().icon);
        } catch (NameNotFoundException nnfe) {
            mNotificationDrawable = mContext.getResources().getDrawable(R.drawable.ic_ad_unknown_icon);
        } catch (Resources.NotFoundException nfe) {
            mNotificationDrawable = mContext.getResources().getDrawable(R.drawable.ic_ad_unknown_icon);
        }
        post(new Runnable() {
            @Override
            public void run() {
                TargetDrawable centerDrawable = new TargetDrawable(getResources(),createCenterDrawable(mNotificationDrawable));
                centerDrawable.setScaleX(0.9f);
                centerDrawable.setScaleY(0.9f);
                mGlowPadView.setCenterDrawable(centerDrawable);
                setHandleText(sbn);
                mNotification = sbn;
                updateResources();
                mGlowPadView.invalidate();
                if (updateOthers) updateOtherNotifications();
            }
        });
    }

    /**
     * Inflates the RemoteViews specified by {@code sbn}.  If bigContentView is available it will be
     * used otherwise the standard contentView will be inflated.
     * @param sbn The StatusBarNotification to inflate content from.
     */
    private void inflateRemoteView(StatusBarNotification sbn) {
        final Notification notification = sbn.getNotification();
        boolean useBigContent = notification.bigContentView != null;
        RemoteViews rv = useBigContent ? notification.bigContentView : notification.contentView;
        if (rv != null) {
            if (mRemoteView != null) mRemoteViewLayout.removeView(mRemoteView);
            if (useBigContent) {
                rv.removeAllViews(com.android.internal.R.id.actions);
                rv.setViewVisibility(com.android.internal.R.id.action_divider, View.GONE);
                mRemoteViewLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                mRemoteViewLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.notification_min_height);
            }
            mRemoteView = rv.apply(mContext, null);
            mRemoteView.setAlpha(0f);
            mRemoteViewLayout.addView(mRemoteView, mRemoteViewLayoutParams);
        }
    }

    /**
     * Sets the text to be displayed around the outside of the ring.
     * @param sbn The StatusBarNotification to get the text from.
     */
    private void setHandleText(StatusBarNotification sbn) {
        if (!privacyMode && mDisplayNotificationText) {
            final Notification notificiation = sbn.getNotification();
            CharSequence tickerText = mDisplayNotificationText ? notificiation.tickerText
                    : "";
            if (tickerText == null) {
                Bundle extras = notificiation.extras;
                if (extras != null)
                    tickerText = extras.getCharSequence(Notification.EXTRA_TITLE, null);
            }
            mGlowPadView.setHandleText(tickerText != null ? tickerText.toString() : "");
        } else if (privacyMode && mDisplayNotificationText) {
            mGlowPadView.setHandleText("Security: notification text disabled");
        }
        if (!mDisplayNotificationText) {
            mGlowPadView.setHandleText("");
        }
    }

    /**
     * Creates a drawable with the required states for the center ring handle
     * @param handle Drawable to use as the base image
     * @return A StateListDrawable with the appropriate states defined.
     */
    private Drawable createLockHandle(Drawable handle) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(TargetDrawable.STATE_INACTIVE, handle);
        stateListDrawable.addState(TargetDrawable.STATE_ACTIVE, handle);
        stateListDrawable.addState(TargetDrawable.STATE_FOCUSED, handle);
        return stateListDrawable;
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mPocketMode == 2) {
                // continue
            } else {
                return;
            }
            if (isOnCall()) return;

            long checkTime = System.currentTimeMillis();
            float value = event.values[0];
            if (event.sensor.equals(mProximitySensor)) {
                if (value >= mProximitySensor.getMaximumRange()) {
                    mDistanceFar = true;
                    if (inQuietHours() && mQuietTime) return;
                    synchronized (this) {
                        if (!isScreenOn()) {
                            if (checkTime >= (mPocketTime + mProximityThreshold)){
                                if (mNotification == null) {
                                    mNotification = getNextAvailableNotification();
                                }
                                showNotification(mNotification, true);
                                turnScreenOn();
                            }
                        }
                    }
                } else if (value <= 1.5) {
                    mDistanceFar = false;
                    mPocketTime = System.currentTimeMillis();
                    if (!isKeyguardLocked() || mWaitPeriod) {
                        return;
                    }

                    if (mDisplayTimeout >= mProximityThreshold) {
                        if (isScreenOn() && (mPocketTime >= (mProximityThreshold + mResetTime))) {
                            restoreBrightness();
                            cancelTimeoutTimer();
                            turnScreenOff();
                        }
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private boolean isKeyguardLocked() {
        boolean isKeyguardShowing = true;
        try {
            isKeyguardShowing = mKeyguardManager.isKeyguardLocked();
        } catch (Exception e) {
        }
        return isKeyguardShowing;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_REDISPLAY_NOTIFICATION.equals(action)) {
                if (mNotification == null) {
                    mNotification = getNextAvailableNotification();
                }
                if (mNotification != null) showNotification(mNotification, true);
            } else if (ACTION_DISPLAY_TIMEOUT.equals(action)) {
                turnScreenOff();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                onScreenTurnedOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                onScreenTurnedOn();
            } else if (Intent.ACTION_KEYGUARD_TARGET.equals(action)) {
                Log.i(TAG, "HEY DICKBAG, DISABLING PROXIMITY SENSOR BECAUSE YOU UNLOCKED THE KEYGUARD!!!!!!!!!");
                disableProximitySensor();
            }
        }
    };

    /**
     * Restarts the timer for re-displaying notifications.
     */
    private void updateRedisplayTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_REDISPLAY_NOTIFICATION);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + mRedisplayTimeout);
        am.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    /**
     * Cancels the timer for re-displaying notifications.
     */
    private void cancelRedisplayTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_REDISPLAY_NOTIFICATION);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
    }

    /**
     * Restarts the timeout timer used to turn the screen off.
     */
    private void updateTimeoutTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_DISPLAY_TIMEOUT);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + mDisplayTimeout);
        am.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    /**
     * Cancels the timeout timer used to turn the screen off.
     */
    private void cancelTimeoutTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_DISPLAY_TIMEOUT);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
    }

    private Drawable createCenterDrawable(Drawable handle) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(TargetDrawable.STATE_INACTIVE, handle);
        
        return stateListDrawable;
    }

    /**
     * Create the set of excluded apps given a string of packages delimited with '|'.
     * @param excludedApps
     */
    private void createExcludedAppsSet(String excludedApps) {
        if (TextUtils.isEmpty(excludedApps))
            return;
        String[] appsToExclude = excludedApps.split("\\|");
        mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
    }

    private boolean isLockScreenDisabled() {
        LockPatternUtils utils = new LockPatternUtils(mContext);
        utils.setCurrentUser(UserHandle.USER_OWNER);
        return utils.isLockScreenDisabled();
    }

    /**
     * Check if device is in Quiet Hours in the moment.
     */
    private boolean inQuietHours() {
        boolean quietHoursEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
        int quietHoursStart = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_START, 0, UserHandle.USER_CURRENT_OR_SELF);
        int quietHoursEnd = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_END, 0, UserHandle.USER_CURRENT_OR_SELF);
        boolean quietHoursDim = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_DIM, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;

        if (quietHoursEnabled && quietHoursDim && (quietHoursStart != quietHoursEnd)) {
            Calendar calendar = Calendar.getInstance();
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            if (quietHoursEnd < quietHoursStart) {
                return (minutes > quietHoursStart) || (minutes < quietHoursEnd);
            } else {
                return (minutes > quietHoursStart) && (minutes < quietHoursEnd);
            }
        }
        return false;
    }
}
