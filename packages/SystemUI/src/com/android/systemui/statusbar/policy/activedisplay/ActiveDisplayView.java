/*
 * Copyright (C) 2013 The ChameleonOS Project
 * This code has been modified. Portions copyright (C) 2013, OmniRom Project.
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
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.StatusBarManager;
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.IPowerManager;
import android.os.PowerManager;
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
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.internal.util.slim.DeviceUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;
import com.android.systemui.widget.RoundedDrawable;
import com.android.systemui.widget.RoundedImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ActiveDisplayView extends FrameLayout
               implements ProximitySensorManager.ProximityListener,
                   LightSensorManager.LightListener, ShakeSensorManager.ShakeListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "ActiveDisplayView";

    private static final String ACTION_REDISPLAY_NOTIFICATION
            = "com.android.systemui.action.REDISPLAY_NOTIFICATION";

    private static final String ACTION_SHAKE_TIMEOUT
            = "com.android.systemui.action.SHAKE_TIMEOUT";

    private static final String ACTION_PHONE_STATE
            = "android.intent.action.PHONE_STATE";

    private static final String ACTION_DISPLAY_TIMEOUT
            = "com.android.systemui.action.DISPLAY_TIMEOUT";

    private static final String ACTION_UNLOCK_DEVICE
            = "com.android.systemui.action.UNLOCK_DEVICE";

    private static final String[] AUTO_BANNED_PACKAGES = new String[] {
        "android",
        "com.android.music",
        "com.andrew.apollo",
        "com.google.android.music",
        "com.android.providers.downloads"
    };

    private static final int MAX_OVERFLOW_ICONS = 8;

    private static final int HIDE_NOTIFICATIONS_BELOW_SCORE = Notification.PRIORITY_LOW;
    private static final int SHOW_ANIMATION_DELAY = 1000;
    private static final int SCREEN_ANIMATION_DELAY = 500;

    private final int GO_TO_SLEEP_REASON_USER = 0;
    private final int GO_TO_SLEEP_REASON_TIMEOUT = 2;

    // the different pocket mode options
    private final int POCKET_MODE_OFF = 0;
    private final int POCKET_MODE_LOCK_NOTIFICATION = 1;
    private final int POCKET_MODE_NOTIFICATIONS_ONLY = 2;
    private final int POCKET_MODE_ALWAYS = 3;

    /** Screen turned off because of power button */
    private final int OFF_BECAUSE_OF_USER = 2;
    /** Screen turned off because of timeout */
    private final int OFF_BECAUSE_OF_TIMEOUT = 3;
    /** Screen turned off because of proximity sensor */
    private final int OFF_BECAUSE_OF_PROX_SENSOR = 4;

    // Targets
    private static final int UNLOCK_TARGET = 0;
    private static final int OPEN_APP_TARGET = 4;
    private static final int DISMISS_TARGET = 6;

    // messages sent to the handler for processing
    private static final int MSG_SHOW_NOTIFICATION_VIEW = 1000;
    private static final int MSG_HIDE_NOTIFICATION_VIEW = 1001;
    private static final int MSG_SHOW_NOTIFICATION      = 1002;
    private static final int MSG_SHOW_TIME              = 1003;
    private static final int MSG_DISMISS_NOTIFICATION   = 1004;
    private static final int MSG_HIDE_NOTIFICATION_CALL = 1005;
    private static final int MSG_SHOW_NOTHING           = 1006;

    GlowPadView mGlowPadView;
    GestureDetector mDoubleTapGesture;
    View mRemoteView;
    View mClock;
    private RoundedImageView mCurrentNotificationIcon;
    private FrameLayout mRemoteViewLayout;
    private FrameLayout mContents;
    private ObjectAnimator mAnim;
    private Drawable mNotificationDrawable;
    private Paint mInvertedPaint;
    private int mCreationOrientation;
    LinearLayout mOverflowNotifications;
    private LayoutParams mRemoteViewLayoutParams;
    private int mIconSize;
    private int mIconMargin;
    private int mIconPadding;
    private LinearLayout.LayoutParams mOverflowLayoutParams;

    private SettingsObserver mSettingsObserver;

    // service
    private StatusBarManager mStatusBarManager;
    private AlarmManager mAM;
    IPowerManager mPM;
    private TelephonyManager mTM;
    private BaseStatusBar mBaseStatusBar;

    private Context mContext;

    // notification
    INotificationManager mNM;
    INotificationListenerWrapper mNotificationListener;
    StatusBarNotification mNotification;

    // sensor
    private ProximitySensorManager mProximitySensorManager;
    private LightSensorManager mLightSensorManager;
    private ShakeSensorManager mShakeSensorManager;

    boolean mProximityIsFar = true;
    boolean mIsInBrightLight = false;
    boolean mWakedByPocketMode = false;
    boolean mWakedByShakeMode = false;
    boolean mIsScreenOff = false;
    boolean mCallbacksRegistered = false;
    long mPocketTime = 0;
    long mShakeTime = 0;
    int mCancelRedisplaySequence;
    int mCancelTimeoutSequence;
    boolean mIsActive = false;
    boolean mIsUnlockByUser = false;
    boolean mIsTurnOffBySensor = false;
    volatile boolean mScreenOnState = false;

    // user customizable settings
    private boolean mActiveDisplayEnabled;
    boolean mDisplayNotificationText = false;
    boolean mShowAllNotifications = false;
    boolean mHideLowPriorityNotifications = false;
    boolean mSunlightModeEnabled = false;
    boolean mTurnOffModeEnabled = false;
    boolean mUseActiveDisplayContent = false;
    boolean mBypassActiveDisplay = false;
    boolean mBatteryLockscreen = false;
    boolean mShowNotificationCount = false;
    boolean mEnableDoubleTap = false;
    boolean mEnableShake = false;
    boolean mEnableShakeForce = false;
    boolean mDisableShakeQuite = false;
    int mShakeTimeout = 3;
    int mShakeThreshold = 10;
    int mPocketMode = POCKET_MODE_OFF;
    boolean mQuietTime;
    int mBrightnessMode = -1;
    int mUserBrightnessLevel = -1;
    int mMinimumBacklight;
    int mMaximumBacklight;
    int mIsAnnoyingThreshold = 0;
    int mWallpaperMode = 0;
    long mRedisplayTimeout = 0;
    long mDisplayTimeout = 8000L;
    long mProximityThreshold = 5000L;
    int mShakeLongThreshold = 2;
    int mInitialBrightness = 0;
    private Set<String> mExcludedApps = new HashSet<String>();
    private Set<String> mPrivacyApps = new HashSet<String>();
    private HashMap<String, Long> mAnnoyingNotifications = new HashMap<String, Long>();

    /**
     * Simple class that listens to changes in notifications
     */
    private class INotificationListenerWrapper extends INotificationListener.Stub {

        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            if (shouldShowNotification() && isValidNotification(sbn) && !shouldDisableActiveDisplay()) {
                // need to make sure either the screen is off or the user is currently
                // viewing the notifications
                if (getVisibility() == View.VISIBLE || !mScreenOnState) {
                    if (mEnableShakeForce) {
                        if((mShakeTimeout > 0) && (!inQuietHours() && mQuietTime)) {
                            enableShakeSensor();
                            updateShakeTimer();
                            Log.i(TAG, "Shake enable by force option.");
                        }
                    } else {
                        showNotification(sbn, true);
                    }
                }
            }
        }
        @Override
        public void onNotificationRemoved(StatusBarNotification sbn) {
            if (mNotification != null && sbn.getPackageName().equals(mNotification.getPackageName())) {
                if (getVisibility() == View.VISIBLE && !mIsScreenOff) {
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

        public void onTrigger(View v, int target) {
            if (target == UNLOCK_TARGET) {
                mIsUnlockByUser = true;
                unlockKeyguardActivity();
                launchFakeActivityIntent();
            } else if (target == OPEN_APP_TARGET) {
                mIsUnlockByUser = true;
                unlockKeyguardActivity();
                launchNotificationPendingIntent();
            } else if (target == DISMISS_TARGET) {
                dismissNotification();
            }
        }

        public void onReleased(View v, int handle) {
            ObjectAnimator.ofFloat(mCurrentNotificationIcon, "alpha", 1f).start();
            doTransition(mOverflowNotifications, 1.0f, 0);
            if (mRemoteView != null) {
                ObjectAnimator.ofFloat(mRemoteView, "alpha", 0f).start();
                ObjectAnimator.ofFloat(mClock, "alpha", 1f).start();
            }
            // user stopped interacting so kick off the timeout timer
            updateTimeoutTimer();
        }

        public void onGrabbed(View v, int handle) {
            // prevent the ActiveDisplayView from turning off while user is interacting with it
            cancelTimeoutTimer();
            setUserActivity();
            restoreBrightness();
            ObjectAnimator.ofFloat(mCurrentNotificationIcon, "alpha", 0f).start();
            doTransition(mOverflowNotifications, 0.0f, 0);
            if (mRemoteView != null) {
                ObjectAnimator.ofFloat(mRemoteView, "alpha", 1f).start();
                ObjectAnimator.ofFloat(mClock, "alpha", 0f).start();
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
        }

    	public void onTargetChange(View v, int target) {
        }

        public void onFinishFinalAnimation() {
        }
    };

    /**
     * Class used to listen for changes to active display related settings
     */
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_ALL_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_REDISPLAY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_BRIGHTNESS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SUNLIGHT_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_PRIVACY_APPS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TURNOFF_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_THRESHOLD), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_CONTENT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_BYPASS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.BATTERY_AROUND_LOCKSCREEN_RING), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_ANNOYING), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_DOUBLE_TAP), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_EVENT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_FORCE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_QUITE_HOURS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_THRESHOLD), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_LONGTHRESHOLD), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHAKE_TIMEOUT), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
            if (mActiveDisplayEnabled) {
                unregisterCallbacks();
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            final ContentResolver resolver = mContext.getContentResolver();
            boolean mNotOverridden;

            mNotOverridden = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1;
            mQuietTime = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS, 0) == 1;
            mActiveDisplayEnabled = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_MODE, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            mDisplayNotificationText = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_TEXT, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            mShowAllNotifications = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_ALL_NOTIFICATIONS, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            mHideLowPriorityNotifications = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            mPocketMode = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE, POCKET_MODE_OFF,
                    UserHandle.USER_CURRENT_OR_SELF);
            mRedisplayTimeout = Settings.System.getLongForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_REDISPLAY, 0L,
                    UserHandle.USER_CURRENT_OR_SELF);
            mInitialBrightness = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_BRIGHTNESS, mMaximumBacklight,
                    UserHandle.USER_CURRENT_OR_SELF);
            mSunlightModeEnabled = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SUNLIGHT_MODE, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            String excludedApps = Settings.System.getStringForUser(resolver,
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS,
                    UserHandle.USER_CURRENT_OR_SELF);
            String privacyApps = Settings.System.getStringForUser(resolver,
                    Settings.System.ACTIVE_DISPLAY_PRIVACY_APPS,
                    UserHandle.USER_CURRENT_OR_SELF);
            mDisplayTimeout = Settings.System.getLongForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_TIMEOUT, mDisplayTimeout,
                    UserHandle.USER_CURRENT_OR_SELF);
            mTurnOffModeEnabled = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_TURNOFF_MODE, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1;
            mProximityThreshold = Settings.System.getLongForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_THRESHOLD, mProximityThreshold,
                    UserHandle.USER_CURRENT_OR_SELF);
            mUseActiveDisplayContent = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_CONTENT, 1,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mBypassActiveDisplay = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_BYPASS, 1,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mBatteryLockscreen = Settings.System.getIntForUser(
                    resolver, Settings.System.BATTERY_AROUND_LOCKSCREEN_RING, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mShowNotificationCount = Settings.System.getIntForUser(
                    resolver, Settings.System.STATUS_BAR_NOTIF_COUNT, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mIsAnnoyingThreshold = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_ANNOYING, 0,
                    UserHandle.USER_CURRENT_OR_SELF);
            mEnableDoubleTap = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_DOUBLE_TAP, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mEnableShake = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_EVENT, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mEnableShakeForce = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_FORCE, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mDisableShakeQuite = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_QUITE_HOURS, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
            mShakeThreshold = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_THRESHOLD, mShakeThreshold,
                    UserHandle.USER_CURRENT_OR_SELF);
            mShakeLongThreshold = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_LONGTHRESHOLD, mShakeLongThreshold,
                    UserHandle.USER_CURRENT_OR_SELF);
            mShakeTimeout = Settings.System.getIntForUser(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHAKE_TIMEOUT, mShakeTimeout,
                    UserHandle.USER_CURRENT_OR_SELF);

            if (!mNotOverridden) {
                mActiveDisplayEnabled = false;
            } else {
                createExcludedAppsSet(excludedApps);
                createPrivacyAppsSet(privacyApps);

               int brightnessMode = Settings.System.getIntForUser(
                        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
                        UserHandle.USER_CURRENT_OR_SELF);
                if (mBrightnessMode != brightnessMode) {
                    mBrightnessMode = brightnessMode;
                    mUserBrightnessLevel = -1;
                }

                if (!mActiveDisplayEnabled || mRedisplayTimeout <= 0) {
                    cancelRedisplayTimer();
                }

                if (mActiveDisplayEnabled || mNotOverridden) {
                    registerCallbacks();
                } else {
                    unregisterCallbacks();
                }
            }
        }
    }

    private Handler mHandler = new Handler() {
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
                case MSG_SHOW_TIME:
                    handleShowTime();
                    break;
                case MSG_DISMISS_NOTIFICATION:
                    handleDismissNotification();
                    break;
                case MSG_HIDE_NOTIFICATION_CALL:
                    handleHideNotificationViewOnCall();
                    break;
                case MSG_SHOW_NOTHING:
                    handleShowNothing();
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

        mContext = context;
        mPM = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        mAM = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mTM = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mNM = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mNotificationListener = new INotificationListenerWrapper();
        mProximitySensorManager = new ProximitySensorManager(context, this);
        mLightSensorManager = new LightSensorManager(context, this);
        mShakeSensorManager = new ShakeSensorManager(context, this);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mMinimumBacklight = pm.getMinimumScreenBrightnessSetting();
        mMaximumBacklight = pm.getMaximumScreenBrightnessSetting();

        mIconSize = getResources().getDimensionPixelSize(R.dimen.overflow_icon_size);
        mIconMargin = getResources().getDimensionPixelSize(R.dimen.ad_notification_margin);
        mIconPadding = getResources().getDimensionPixelSize(R.dimen.overflow_icon_padding);

        mSettingsObserver = new SettingsObserver(new Handler());
        mCreationOrientation = Resources.getSystem().getConfiguration().orientation;
        mInvertedPaint = makeInvertedPaint();

        mDoubleTapGesture = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                turnScreenOff();
                return true;
            }
        });

    }

    public void setBar(BaseStatusBar bar) {
        mBaseStatusBar = bar;
    }

    @Override
    public synchronized void onNear() {
        if (mProximityIsFar) {
            mPocketTime = System.currentTimeMillis();
            mProximityIsFar = false;
        }
        if (mScreenOnState && mPocketMode != POCKET_MODE_OFF && !shouldDisableActiveDisplay() && mWakedByPocketMode) {
            mWakedByPocketMode = false;
            Log.i(TAG, "ActiveDisplay: sent to sleep by Pocketmode");
            turnScreenOffbySensor();
        }
    }

    @Override
    public synchronized void onFar() {
        mProximityIsFar = true;
        if (!mScreenOnState && mPocketMode != POCKET_MODE_OFF && !shouldDisableActiveDisplay()) {
            if ((System.currentTimeMillis() >= (mPocketTime + mProximityThreshold)) && (mPocketTime != 0)) {
                Log.i(TAG, "ActiveDisplay: wake by Pocketmode");
                turnScreenOnbySensor();
                if (mNotification == null) {
                    mNotification = getNextAvailableNotification();
                }
                if (mNotification != null) {
                    showNotification(mNotification, true);
                } else if (mPocketMode == POCKET_MODE_ALWAYS) {
                    if (isKeyguardSecure()) {
                        showNothing();
                    } else {
                        showTime();
                    }
                }
            }
        }
    }

    @Override
    public synchronized void onDarker() {
        if (mIsInBrightLight) {
            mIsInBrightLight = false;
        }
        if (mSunlightModeEnabled) {
            invalidate();
        }
    }

    @Override
    public synchronized void onBrighter() {
        mIsInBrightLight = true;
        if (mSunlightModeEnabled) {
            invalidate();
        }
    }

    @Override
    public synchronized void onShake() {
        if (!mEnableShake || (!mDisableShakeQuite && (inQuietHours() && mQuietTime)) || isOnCall()) {
            return;
        }

        if (!mScreenOnState && !mIsActive) {
            Log.i(TAG, "ActiveDisplay: wake by Shakemode");
            mWakedByShakeMode = true;
            mShakeTime = System.currentTimeMillis();
            if (mNotification == null) {
                mNotification = getNextAvailableNotification();
            }
            if (mNotification != null) {
                showNotification(mNotification, true);
            } else {
                if (isKeyguardSecure()) {
                    showNothing();
                } else {
                    showTime();
                }
            }
        } else if (mIsActive && mWakedByShakeMode && mScreenOnState) {
            if ((System.currentTimeMillis() >= (mShakeTime + (long)(1000 * mShakeLongThreshold))) && (mShakeTime != 0)) {
                mWakedByShakeMode = false;
                Log.i(TAG, "ActiveDisplay: sent to sleep by Shakemode");
                turnScreenOff();
            }
        }
    }

    private Paint makeInvertedPaint() {
        Paint p = new Paint();
        float[] colorMatrix_Negative = {
                -1.0f, 0, 0, 0, 255, //red
                0, -1.0f, 0, 0, 255, //green
                0, 0, -1.0f, 0, 255, //blue
                0, 0, 0, 1.0f, 0 //alpha
        };
        p.setColorFilter(new ColorMatrixColorFilter(colorMatrix_Negative));
        return p;
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
        if (mRedisplayTimeout > 0 && !mScreenOnState) {
            updateRedisplayTimer();
        }
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEnableDoubleTap) {
            mDoubleTapGesture.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int layer = 0;
        if (mIsInBrightLight && mSunlightModeEnabled) {
            layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), mInvertedPaint,
                   Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.FULL_COLOR_LAYER_SAVE_FLAG);
        }
        super.dispatchDraw(canvas);
        if (mIsInBrightLight && mSunlightModeEnabled) canvas.restoreToCount(layer);
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
        mGlowPadView.setOnTouchListener(mGlowPadTouchListener);

        mRemoteViewLayout = (FrameLayout) contents.findViewById(R.id.remote_content_parent);
        mClock = contents.findViewById(R.id.clock_view);
        mCurrentNotificationIcon = (RoundedImageView) contents.findViewById(R.id.current_notification_icon);

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

    private void launchFakeActivityIntent() {
        mPocketTime = 0;
        mShakeTime = 0;
        mNotification = null;
        Intent intent = new Intent(mContext, DummyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT_OR_SELF));
    }

    /**
     * Launches the pending intent for the currently selected notification
     */
    private void launchNotificationPendingIntent() {
        if (mNotification != null) {
            PendingIntent contentIntent = mNotification.getNotification().contentIntent;
            if (contentIntent != null) {
                try {
                     Intent intent = contentIntent.getIntent();
                     intent.setFlags(
                            intent.getFlags()
                            | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    contentIntent.send();
                } catch (CanceledException ce) {
                }
                KeyguardTouchDelegate.getInstance(mContext).dismiss();
            }
            try {
                 if (mNotification.isClearable()) {
                     mNM.cancelNotificationFromSystemListener(mNotificationListener,
                         mNotification.getPackageName(), mNotification.getTag(),
                         mNotification.getId());
                 }
            } catch (RemoteException e) {
            } catch (NullPointerException npe) {
            }
            mNotification = null;
        }
    }

    protected void showNotificationView() {
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_SHOW_NOTIFICATION_VIEW);
    }

    protected void hideNotificationView() {
        mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);
    }

    protected void hideNotificationViewOnCall() {
        mHandler.removeMessages(MSG_HIDE_NOTIFICATION_CALL);
        mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_CALL);
    }

    protected void showNotification(StatusBarNotification sbn, boolean ping) {
        mNotification = sbn;
        Message msg = new Message();
        msg.what = MSG_SHOW_NOTIFICATION;
        msg.arg1 = ping ? 1 : 0;
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION);
        mHandler.sendMessage(msg);
    }

    private void showTime() {
        mHandler.removeMessages(MSG_SHOW_TIME);
        mHandler.sendEmptyMessage(MSG_SHOW_TIME);
    }

    private void showNothing() {
        mHandler.removeMessages(MSG_SHOW_NOTHING);
        mHandler.sendEmptyMessage(MSG_SHOW_NOTHING);
    }

    private void dismissNotification() {
        mHandler.removeMessages(MSG_DISMISS_NOTIFICATION);
        mHandler.sendEmptyMessage(MSG_DISMISS_NOTIFICATION);
    }

    private boolean isKeyguardSecure() {
        boolean isSecure = KeyguardTouchDelegate.getInstance(mContext).isSecure();
        return mBypassActiveDisplay && isSecure;
    }

    private void unlockKeyguardActivity() {
        hideNotificationView();
        sendUnlockBroadcast();
        disableProximitySensor();
        disableShakeSensor();
        try {
             // The intent we are sending is for the application, which
             // won't have permission to immediately start an activity after
             // the user switches to home.  We know it is safe to do at this
             // point, so make sure new activity switches are now allowed.
             ActivityManagerNative.getDefault().resumeAppSwitches();
             // Also, notifications can be launched from the lock screen,
             // so dismiss the lock screen when the activity starts.
             ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
    }

    private void sendUnlockBroadcast() {
        Intent u = new Intent();
        u.setAction(ACTION_UNLOCK_DEVICE);
        mContext.sendBroadcastAsUser(u, UserHandle.ALL);
    }

    private void adjustStatusBarLocked(boolean hiding) {
        if (mStatusBarManager == null) {
            mStatusBarManager = (StatusBarManager)
                    mContext.getSystemService(Context.STATUS_BAR_SERVICE);
        }
        if (mStatusBarManager == null) {
            Log.w(TAG, "Could not get status bar manager");
        } else {
            // Disable aspects of the system/status/navigation bars that must not be re-enabled by
            // windows that appear on top, ever
            int flags = StatusBarManager.DISABLE_NONE;
            if (hiding) {
                flags |= StatusBarManager.DISABLE_RECENT | StatusBarManager.DISABLE_HOME
                      | StatusBarManager.DISABLE_BACK | StatusBarManager.DISABLE_SEARCH;
            }
            mStatusBarManager.disable(flags);
        }
    }

    private void handleShowNotificationView() {
        mIsActive = true;
        setVisibility(View.VISIBLE);
        if (hasLightSensor()) {
            Log.i(TAG, "ActiveDisplay: enable LightSensor");
            mLightSensorManager.enable();
        }
        adjustStatusBarLocked(true);
        // Warm up KeyguardTouchDelegate so it's ready by the time
        KeyguardTouchDelegate.getInstance(mContext);
    }

    private void handleHideNotificationView() {
        mIsActive = false;
        restoreBrightness();
        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
        cancelTimeoutTimer();
        if (hasLightSensor()) {
            Log.i(TAG, "ActiveDisplay: disable LightSensor");
            mLightSensorManager.disable(true);
        }
        adjustStatusBarLocked(false);
        setVisibility(View.GONE);
    }

    private void handleHideNotificationViewOnCall() {
        mIsActive = false;
        restoreBrightness();
        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
        cancelTimeoutTimer();
        if (hasLightSensor()) {
            Log.i(TAG, "ActiveDisplay: disable LightSensor");
            mLightSensorManager.disable(true);
        }
        setVisibility(View.GONE);
    }

    private void handleShowNotification(boolean ping) {
        if (mNotification == null) {
            return;
        }
        handleShowNotificationView();
        setActiveNotification(mNotification, true);
        inflateRemoteView(mNotification);
        if (!mScreenOnState) {
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
        turnScreenOff();
    }

    private void handleShowTime() {
        mCurrentNotificationIcon.setImageResource(R.drawable.ic_ad_unlock);
        mCurrentNotificationIcon.placeNumber(0, false);
        mGlowPadView.setHandleText("");
        mNotificationDrawable = null;
        if (mRemoteView != null) {
            mRemoteViewLayout.removeView(mRemoteView);
        }
        mRemoteView = null;
        mOverflowNotifications.removeAllViews();
        updateTargets();
        showNotificationView();
        invalidate();
        if (!mScreenOnState) {
            turnScreenOn();
        }
    }

    private void handleShowNothing() {
        restoreBrightness();
        if (!mScreenOnState) {
            turnScreenOn();
        }
        setVisibility(View.GONE);
    }

    protected boolean inQuietHours() {
        boolean isQuietHourDim = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.QUIET_HOURS_DIM, 0) == 2;
        boolean isQuietHourMute = Settings.System.getInt(mContext.getContentResolver(),
                         Settings.System.QUIET_HOURS_MUTE, 0) == 2;
        return isQuietHourDim || isQuietHourMute;

     }

    private boolean shouldDisableActiveDisplay() {
        return (inQuietHours() && mQuietTime) || isOnCall();
    }

    protected void onScreenTurnedOn() {
        cancelRedisplayTimer();
        if (!mIsActive) {
            cancelTimeoutTimer();
        }
        if (!mWakedByPocketMode) {
            disableProximitySensor();
        }
        if (!mWakedByShakeMode) {
            disableShakeSensor();
        }
        mIsScreenOff = false;
    }

    protected void onScreenTurnedOff() {
        enableProximitySensor();

    if(mShakeTimeout == 0){
        enableShakeSensor();
            Log.i(TAG, "Shake always enable.");
    }

        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
        hideNotificationView();
        cancelTimeoutTimer();
        if (mRedisplayTimeout > 0) {
            updateRedisplayTimer();
        }
    }

    protected void turnScreenOff() {
        mHandler.removeCallbacks(runWakeDevice);
        Log.i(TAG, "ActiveDisplay: Screen Off");
        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
        mIsScreenOff = true;
        try {
            mPM.goToSleep(SystemClock.uptimeMillis(), GO_TO_SLEEP_REASON_USER);
        } catch (RemoteException e) {
        }
    }

    protected void turnScreenOffTimeOut() {
        if (getVisibility() != View.VISIBLE || mIsUnlockByUser) {
            return;
        }
        Log.i(TAG, "ActiveDisplay: Screen Timeout");
        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
        if(mShakeTimeout > 0) {
            enableShakeSensor();
            updateShakeTimer();
            Log.i(TAG, "Shake enable by screen time out.");
        }
        try {
            mPM.goToSleep(SystemClock.uptimeMillis(), GO_TO_SLEEP_REASON_TIMEOUT);
        } catch (RemoteException e) {
        }
    }

    protected void turnScreenOffbySensor() {
        mIsTurnOffBySensor = true;
        KeyguardTouchDelegate.getInstance(mContext).onScreenTurnedOff(OFF_BECAUSE_OF_PROX_SENSOR);
    	if(mShakeTimeout > 0) {
            enableShakeSensor();
            updateShakeTimer();
            Log.i(TAG, "Shake enable by sensor.");
        }
        turnScreenOff();
    }

    protected void turnScreenOnbySensor() {
        if (mTurnOffModeEnabled && mActiveDisplayEnabled) {
            mWakedByPocketMode = true;
        }
    }

    protected void turnScreenOn() {
        if (mIsTurnOffBySensor) {
            mIsTurnOffBySensor = false;
            mHandler.removeCallbacks(runWakeDevice);
            return;
        }
        mHandler.removeCallbacks(runWakeDevice);
        mHandler.postDelayed(runWakeDevice, SCREEN_ANIMATION_DELAY);
    }

    final Runnable runWakeDevice = new Runnable() {
        @Override
        public void run() {
            setBrightness(mInitialBrightness);
            wakeDevice();
            doTransition(ActiveDisplayView.this, 1f, SHOW_ANIMATION_DELAY);
        }
    };

    private final Runnable runScreenOn = new Runnable() {
        @Override
        public void run() {
            if (!mScreenOnState) {
                wakeDevice();
            }
            KeyguardTouchDelegate.getInstance(mContext).dismiss();
        }
    };

    protected void enableProximitySensor() {
        if (mPocketMode != POCKET_MODE_OFF && mActiveDisplayEnabled) {
            Log.i(TAG, "ActiveDisplay: enable ProximitySensor");
            mProximityIsFar = true;
            mProximitySensorManager.enable();
        }
    }

    protected void disableProximitySensor() {
        if (mPocketMode != POCKET_MODE_OFF) {
            Log.i(TAG, "ActiveDisplay: disable ProximitySensor");
            mProximitySensorManager.disable(true);
        }
    }

    protected void enableShakeSensor() {
        if (mEnableShake && mActiveDisplayEnabled) {
            Log.i(TAG, "ActiveDisplay: enable ShakeSensor");
            mShakeSensorManager.enable(mShakeThreshold);
        }
    }

    protected void disableShakeSensor() {
        if (mEnableShake) {
            Log.i(TAG, "ActiveDisplay: disable ShakeSensor");
            mShakeSensorManager.disable();
        }
    }

    private void setBrightness(int brightness) {
        final ContentResolver resolver = mContext.getContentResolver();
        mBrightnessMode = Settings.System.getIntForUser(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, UserHandle.USER_CURRENT_OR_SELF);
        if (mBrightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            mUserBrightnessLevel = Settings.System.getIntForUser(resolver, Settings.System.SCREEN_BRIGHTNESS,
                    mMaximumBacklight, UserHandle.USER_CURRENT_OR_SELF);
            final int level = brightness;
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

    private void setUserActivity() {
        try {
            mPM.userActivity(SystemClock.uptimeMillis(), 0, 0);
        } catch (RemoteException e) {
        }
    }

    private void isUserActivity() {
        restoreBrightness();
        updateTimeoutTimer();
        setUserActivity();
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REDISPLAY_NOTIFICATION);
        filter.addAction(ACTION_SHAKE_TIMEOUT);
        filter.addAction(ACTION_DISPLAY_TIMEOUT);
        filter.addAction(ACTION_PHONE_STATE);
        filter.addAction(ACTION_UNLOCK_DEVICE);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
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
            Log.e(TAG, "registerNotificationListener()", e);
        }
    }

    private void unregisterNotificationListener() {
        if (mNotificationListener != null) {
            try {
                mNM.unregisterListener(mNotificationListener, UserHandle.USER_ALL);
            } catch (RemoteException e) {
                Log.e(TAG, "registerNotificationListener()", e);
            }
        }
    }

    private void registerCallbacks() {
        if (!mCallbacksRegistered) {
            Log.i(TAG, "ActiveDisplay: register callbacks");
            registerBroadcastReceiver();
            registerNotificationListener();
            mCallbacksRegistered = true;
        }
    }

    private void unregisterCallbacks() {
        if (mPocketMode == 2 || mPocketMode == 3) {
            disableProximitySensor();
        }
        if (mCallbacksRegistered) {
            Log.i(TAG, "ActiveDisplay: unregister callbacks");
            unregisterBroadcastReceiver();
            unregisterNotificationListener();
            mCallbacksRegistered = false;
        }
    }

    protected StatusBarNotification getNextAvailableNotification() {
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

    protected void updateOtherNotifications() {
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
                            boolean updateOther = false;
                            RoundedImageView iv = new RoundedImageView(mContext);
                            if (mOverflowNotifications.getChildCount() < (MAX_OVERFLOW_ICONS - 1)) {
                                Drawable iconDrawable = getIconDrawable(sbns[i]);
                                if (iconDrawable != null) {
                                    updateOther = true;
                                    iv.setImageDrawable(iconDrawable);
                                    iv.setTag(sbns[i]);
                                    if (sbns[i].getPackageName().equals(mNotification.getPackageName())
                                           && sbns[i].getId() == mNotification.getId()) {
                                        if (mUseActiveDisplayContent) {
                                            iv.setBackgroundResource(R.drawable.ad_active_notification_background_blur);
                                        } else {
                                            iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                        }
                                    } else {
                                        iv.setBackgroundResource(0);
                                    }
                                }
                            } else {
                                updateOther = true;
                                iv.setImageResource(R.drawable.ic_ad_morenotifications);
                            }
                            iv.setPadding(mIconPadding, mIconPadding, mIconPadding, mIconPadding);
                            iv.setScaleType(RoundedImageView.ScaleType.FIT_CENTER);
                            if (updateOther) {
                                mOverflowNotifications.addView(iv, mOverflowLayoutParams);
                            }
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
                        final RoundedImageView iv = (RoundedImageView) mOverflowNotifications.getChildAt(i);
                        final StatusBarNotification sbn = (StatusBarNotification) iv.getTag();
                        iv.getHitRect(hitRect);
                        if (i != mLastChildPosition ) {
                            if (hitRect.contains((int)x, (int)y)) {
                                mLastChildPosition = i;
                                if (sbn != null) {
                                    // swap the notification
                                    mNotification = sbn;
                                    setActiveNotification(sbn, false);
                                    if (mUseActiveDisplayContent) {
                                        iv.setBackgroundResource(R.drawable.ad_active_notification_background_blur);
                                    } else {
                                        iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                    }
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
            if (mEnableDoubleTap) {
                mDoubleTapGesture.onTouchEvent(event);
            }
            return true;
        }
    };

    private OnTouchListener mGlowPadTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mEnableDoubleTap) {
                return mDoubleTapGesture.onTouchEvent(event);
            }
            return v.onTouchEvent(event);
        }
    };

    /**
     * Determine if a given notification should be used.
     * @param sbn StatusBarNotification to check.
     * @return True if it should be used, false otherwise.
     */
    private boolean isValidNotification(StatusBarNotification sbn) {
        return (!isExcludeApp(sbn) && isNotAppBanned(sbn.getPackageName())
                && (sbn.getNotification().icon != 0) && (sbn.isClearable() || mShowAllNotifications)
                && !(mHideLowPriorityNotifications && sbn.getNotification().priority < HIDE_NOTIFICATIONS_BELOW_SCORE)
                && isNotificationForUser(sbn) && !notificationIsAnnoying(sbn.getPackageName()));
    }

    /**
     * Determine if a given notification is for user current.
     * @param sbn StatusBarNotification to check.
     * @param BaseStatusBar notificationIsForCurrentUser to check.
     * @return True if this for current, false otherwise.
     */
    private boolean isNotificationForUser(StatusBarNotification sbn) {
        if (mBaseStatusBar != null) {
            return mBaseStatusBar.notificationIsForCurrentUser(sbn);
        }
        // if BaseStatusBar is null, always return true
        return true;
    }

    /**
     * Determine if a given notification is not came from auto banned app.
     * @return True if not, false otherwise.
     */
    private boolean isNotAppBanned(String getPackageName) {
        boolean showingApp = true;
        for (String packageName : AUTO_BANNED_PACKAGES) {
             if (packageName.equals(getPackageName)) {
                 showingApp = false;
                 break;
             }
        }
        return showingApp;
    }

    /**
     * Determine if a given notification from exclude apps.
     * @param sbn StatusBarNotification to check.
     * @return True if has privacy mode, false otherwise.
     */
    private boolean isExcludeApp(StatusBarNotification sbn) {
        if (mExcludedApps != null) {
            return mExcludedApps.contains(sbn.getPackageName());
        }
        return false;
    }

    /**
     * Determine if a given notification not from app that has privacy mode.
     * @param sbn StatusBarNotification to check.
     * @return True if has privacy mode, false otherwise.
     */
    private boolean isPrivacyApp(StatusBarNotification sbn) {
        if (mPrivacyApps != null) {
            return mPrivacyApps.contains(sbn.getPackageName());
        }
        return false;
    }

    /**
     * Determine if a given notification is annoying notification.
     * @return True if annoying, false otherwise.
     */
    private boolean notificationIsAnnoying(String pkg) {
        final long annoyingNotificationThreshold = ((long) mIsAnnoyingThreshold);

        if (annoyingNotificationThreshold == 0) {
            return false;
        }

        if ("android".equals(pkg)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (mAnnoyingNotifications.containsKey(pkg)
            && (currentTime - mAnnoyingNotifications.get(pkg)
                      < annoyingNotificationThreshold)) {
            // less than threshold; it's an annoying notification!!
            return true;
        } else {
            // not in map or time to re-add
            mAnnoyingNotifications.put(pkg, currentTime);
            return false;
        }
    }

    /**
     * Determine if we should show notifications or not.
     * @return True if we should show this view.
     */
    private boolean shouldShowNotification() {
        if (!hasProximitySensor()) {
            return true;
        }
        return mProximityIsFar;
    }

    private boolean hasProximitySensor() {
        return DeviceUtils.deviceSupportsProximitySensor(mContext);
    }

    private boolean hasLightSensor() {
        return DeviceUtils.deviceSupportsLightSensor(mContext);
    }

    /**
     * Wakes the device up and turns the screen on.
     */
    private void wakeDevice() {
        try {
            mPM.wakeUp(SystemClock.uptimeMillis());
        } catch (RemoteException e) {
        }
        Log.i(TAG, "ActiveDisplay: Wake device");
        updateTimeoutTimer();
    }

    /**
     * Determine i a call is currently in progress.
     * @return True if a call is in progress.
     */
    protected boolean isOnCall() {
        return mTM.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    /**
     * Sets {@code sbn} as the current notification inside the ring.
     * @param sbn StatusBarNotification to be placed as the current one.
     * @param updateOthers Set to true to update the overflow notifications.
     */
    protected void setActiveNotification(final StatusBarNotification sbn, final boolean updateOthers) {
        mNotificationDrawable = getIconDrawable(sbn);
        final Drawable drawable = mNotificationDrawable;
        final NotificationData nd = NotificationUtils.getNotificationData(mContext, sbn);
        post(new Runnable() {
             @Override
             public void run() {
                 if (drawable != null) {
                     mCurrentNotificationIcon.setImageDrawable(drawable);
                     if (nd != null) {
                         mCurrentNotificationIcon.placeNumber(nd.number, mShowNotificationCount);
                     } else {
                         mCurrentNotificationIcon.placeNumber(0, false);
                     }
                     setHandleText(sbn);
                     mNotification = sbn;
                 } else {
                     mCurrentNotificationIcon.setImageDrawable(null);
                     mCurrentNotificationIcon.setImageResource(R.drawable.ic_ad_unlock);
                     mCurrentNotificationIcon.placeNumber(0, false);
                     mNotification = null;
                 }
                 updateResources();
                 mGlowPadView.invalidate();
                 if (updateOthers) updateOtherNotifications();
             }
        });
    }

    private Drawable getIconDrawable(StatusBarNotification sbn) {
        Drawable drawable = null;
        try {
            Context pkgContext = mContext.createPackageContext(sbn.getPackageName(), Context.CONTEXT_RESTRICTED);
            drawable = pkgContext.getResources().getDrawable(sbn.getNotification().icon);
        } catch (NameNotFoundException nnfe) {
            drawable = null;
        } catch (Resources.NotFoundException nfe) {
            drawable = null;
        }
        return drawable;
    }

    /**
     * Inflates the RemoteViews specified by {@code sbn}.  If bigContentView is available it will be
     * used otherwise the standard contentView will be inflated.
     * @param sbn The StatusBarNotification to inflate content from.
     */
    protected void inflateRemoteView(StatusBarNotification sbn) {
        final Notification notification = sbn.getNotification();
        boolean useBigContent = notification.bigContentView != null;
        RemoteViews rv = useBigContent ? notification.bigContentView : notification.contentView;
        if (mUseActiveDisplayContent) {
            rv = applyStandardTemplate(notification, sbn);
        }
        if (rv != null) {
            if (mRemoteView != null) {
                mRemoteViewLayout.removeView(mRemoteView);
            }
            if (useBigContent) {
                if (!mUseActiveDisplayContent) {
                    rv.removeAllViews(com.android.internal.R.id.actions);
                    rv.setViewVisibility(com.android.internal.R.id.action_divider, View.GONE);
                }
                mRemoteViewLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                mRemoteViewLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.notification_min_height);
            }
            mRemoteView = rv.apply(mContext, null);
            mRemoteView.setAlpha(0f);
            mRemoteViewLayout.addView(mRemoteView, mRemoteViewLayoutParams);
        }
    }

    private RemoteViews applyStandardTemplate(Notification notification, StatusBarNotification sbn) {
        RemoteViews contentView = new RemoteViews(mContext.getPackageName(), R.layout.active_display_notification);
        NotificationData nd = NotificationUtils.getNotificationData(mContext, sbn);
        if (nd == null) {
            return null;
        }
        long when = notification.when;
        int notificationIconResource = notification.icon;
        String privacyApps = getResources().getString(R.string.active_display_privacy_content);
        boolean isPrivacy = isPrivacyApp(sbn);
        if (nd.iconApp != null) {
            contentView.setImageViewBitmap(R.id.notificationicon, nd.iconApp);
        } else {
            contentView.setImageViewResource(R.id.notificationicon, notificationIconResource);
        }
        if (nd.titleText != null) {
            contentView.setTextViewText(R.id.notificationtitle, nd.titleText);
        }
        if (nd.getLargeMessage() != null) {
            if (isPrivacy) {
                contentView.setTextViewText(R.id.notificationtext, privacyApps);
            } else {
                contentView.setTextViewText(R.id.notificationtext, nd.getLargeMessage());
            }
        } else {
            if (nd.tickerText != null) {
                if (isPrivacy) {
                    contentView.setTextViewText(R.id.notificationtext, privacyApps);
                } else {
                    contentView.setTextViewText(R.id.notificationtext, nd.tickerText);
                }
            }
        }
        if (nd.iconAppSmall != null) {
            contentView.setImageViewBitmap(R.id.notificationiconsmall, nd.iconAppSmall);
        } else {
            contentView.setImageViewResource(R.id.notificationiconsmall, notificationIconResource);
        }
        if (nd.infoText != null) {
            contentView.setTextViewText(R.id.notificationinfo, nd.infoText);
        }
        if (when != 0) {
            contentView.setLong(R.id.notificationtime, "setTime", when);
        }
        return contentView;
    }

    /**
     * Sets the text to be displayed around the outside of the ring.
     * @param sbn The StatusBarNotification to get the text from.
     */
    private void setHandleText(StatusBarNotification sbn) {
        final Notification notification = sbn.getNotification();
        final boolean isPrivacy = isPrivacyApp(sbn);
        CharSequence tickerText = mDisplayNotificationText ? notification.tickerText
                : "";
        if (tickerText == null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                tickerText = extras.getCharSequence(Notification.EXTRA_TITLE, null);
            }
        }
        if (isPrivacy) {
            mGlowPadView.setHandleText("");
        } else {
            mGlowPadView.setHandleText(tickerText != null ? tickerText.toString() : "");
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

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                if (mGlowPadView != null) {
                    if (mBatteryLockscreen) {
                        mGlowPadView.setArc(level * 3.6f, Color.WHITE);
                    } else {
                        mGlowPadView.setArc(0, 0);
                    }
                }
            } else if (action.equals(ACTION_REDISPLAY_NOTIFICATION)) {
                final int sequence = intent.getIntExtra("disp", 0);
                synchronized (ActiveDisplayView.this) {
                    if (mCancelRedisplaySequence == sequence) {
                        if (mNotification == null) {
                            mNotification = getNextAvailableNotification();
                        }
                        if (mNotification != null) {
                            showNotification(mNotification, true);
                        }
                    }
                }
            } else if (action.equals(ACTION_SHAKE_TIMEOUT)) {
                synchronized (ActiveDisplayView.this) {
                    disableShakeSensor();
                    Log.i(TAG, "Shake disabled by time out.");
                }
            } else if (action.equals(ACTION_DISPLAY_TIMEOUT)) {
                final int sequence = intent.getIntExtra("seq", 0);
                synchronized (ActiveDisplayView.this) {
                    if (mCancelTimeoutSequence == sequence) {
                        turnScreenOffTimeOut();
                    }
                }
            } else if (action.equals(ACTION_PHONE_STATE)) {
                if (isOnCall() && (getVisibility() == View.VISIBLE)) {
                    hideNotificationViewOnCall();
                }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOnState = false;
                onScreenTurnedOff();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOnState = true;
                onScreenTurnedOn();
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                if (!isLockScreenDisabled()) {
                    cancelAllState();
                }
            } else if (action.equals(ACTION_UNLOCK_DEVICE)) {
                cancelAllState();
                if (mIsUnlockByUser) {
                    mIsUnlockByUser = false;
                    if (!mScreenOnState) {
                        turnScreenOn();
                    }
                    KeyguardTouchDelegate.getInstance(mContext).dismiss();
                } else {
                    mHandler.removeCallbacks(runScreenOn);
                    mHandler.postDelayed(runScreenOn, SCREEN_ANIMATION_DELAY);
                }
            }
        }
    };

    private void cancelAllState() {
        cancelAllTimer();
        setUserActivity();
        restoreBrightness();
        disableProximitySensor();
        disableShakeSensor();
        mWakedByPocketMode = false;
        mWakedByShakeMode = false;
    }

    /**
     * Cancels the All timer.
     */
    private void cancelAllTimer() {
        cancelRedisplayTimer();
        cancelTimeoutTimer();
    }

    /**
     * Restarts the timer for re-displaying notifications.
     */
    protected void updateRedisplayTimer() {
        long when = SystemClock.elapsedRealtime() + mRedisplayTimeout;
        Intent intent = new Intent(ACTION_REDISPLAY_NOTIFICATION);
        intent.putExtra("disp", mCancelRedisplaySequence);
        PendingIntent sender = PendingIntent.getBroadcast(mContext,
                    0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mAM.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, sender);
    }

    /**
     * Restarts the timer for stop shake event.
     */
    protected void updateShakeTimer() {
        long when = SystemClock.elapsedRealtime() + (long)(mShakeTimeout * 60000);
        Intent intent = new Intent(ACTION_SHAKE_TIMEOUT);
        PendingIntent sender = PendingIntent.getBroadcast(mContext,
                    0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mAM.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, sender);
    	Log.i(TAG, "Shake timeout set.");
    }

    /**
     * Cancels the timer for re-displaying notifications.
     */
    protected void cancelRedisplayTimer() {
        mCancelRedisplaySequence++;
    }

    /**
     * Restarts the timeout timer used to turn the screen off.
     */
    protected void updateTimeoutTimer() {
        long when = SystemClock.elapsedRealtime() + mDisplayTimeout;
        Intent intent = new Intent(ACTION_DISPLAY_TIMEOUT);
        intent.putExtra("seq", mCancelTimeoutSequence);
        PendingIntent sender = PendingIntent.getBroadcast(mContext,
                    0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mAM.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, sender);
    }

    /**
     * Cancels the timeout timer used to turn the screen off.
     */
    protected void cancelTimeoutTimer() {
        mCancelTimeoutSequence++;
    }

    /**
     * Create the set of excluded apps given a string of packages delimited with '|'.
     * @param excludedApps
     */
    private void createExcludedAppsSet(String excludedApps) {
        if (TextUtils.isEmpty(excludedApps)) {
            mExcludedApps = null;
            return;
        }
        String[] appsToExclude = excludedApps.split("\\|");
        mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
    }

    /**
     * Create the set of privacy apps given a string of packages delimited with '|'.
     * @param privacyApps
     */
    private void createPrivacyAppsSet(String privacyApps) {
        if (TextUtils.isEmpty(privacyApps)) {
            mPrivacyApps = null;
            return;
        }
        String[] appsToExclude = privacyApps.split("\\|");
        mPrivacyApps = new HashSet<String>(Arrays.asList(appsToExclude));
    }

    private boolean isLockScreenDisabled() {
        LockPatternUtils utils = new LockPatternUtils(mContext);
        utils.setCurrentUser(UserHandle.USER_OWNER);
        return utils.isLockScreenDisabled();
    }
}

