/*
 * Copyright (C) 2006 The Android Open Source Project
 * Patched by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
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

package com.android.internal.policy.impl;

import com.android.internal.R;

import android.app.Activity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.IUiModeManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.LocalPowerManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.CmSystem;
import android.provider.Settings;

import com.android.internal.app.ThemeUtils;
import com.android.internal.policy.PolicyManager;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.telephony.ITelephony;
import com.android.internal.view.BaseInputHandler;
import com.android.internal.widget.PointerLocationView;

import android.util.Config;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputQueue;
import android.view.InputHandler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowOrientationListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
import static android.view.WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_SEARCH_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import android.view.WindowManagerImpl;
import android.view.WindowManagerPolicy;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import android.media.IAudioService;
import android.media.AudioManager;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * WindowManagerPolicy implementation for the Android phone UI.  This
 * introduces a new method suffix, Lp, for an internal lock of the
 * PhoneWindowManager.  This is used to protect some internal state, and
 * can be acquired with either thw Lw and Li lock held, so has the restrictions
 * of both of those when held.
 */
public class PhoneWindowManager implements WindowManagerPolicy {
    static final String TAG = "WindowManager";
    static final boolean DEBUG = false;
    static final boolean localLOGV = DEBUG ? Config.LOGD : Config.LOGV;
    static final boolean DEBUG_LAYOUT = false;
    static final boolean SHOW_STARTING_ANIMATIONS = true;
    static final boolean SHOW_PROCESSES_ON_ALT_MENU = false;

    // wallpaper is at the bottom, though the window manager may move it.
    static final int WALLPAPER_LAYER = 2;
    static final int APPLICATION_LAYER = 2;
    static final int PHONE_LAYER = 3;
    static final int SEARCH_BAR_LAYER = 4;
    static final int STATUS_BAR_PANEL_LAYER = 5;
    static final int SYSTEM_DIALOG_LAYER = 6;
    // toasts and the plugged-in battery thing
    static final int TOAST_LAYER = 7;
    static final int STATUS_BAR_LAYER = 8;
    // SIM errors and unlock.  Not sure if this really should be in a high layer.
    static final int PRIORITY_PHONE_LAYER = 9;
    // like the ANR / app crashed dialogs
    static final int SYSTEM_ALERT_LAYER = 10;
    // system-level error dialogs
    static final int SYSTEM_ERROR_LAYER = 11;
    // on-screen keyboards and other such input method user interfaces go here.
    static final int INPUT_METHOD_LAYER = 12;
    // on-screen keyboards and other such input method user interfaces go here.
    static final int INPUT_METHOD_DIALOG_LAYER = 13;
    // the keyguard; nothing on top of these can take focus, since they are
    // responsible for power management when displayed.
    static final int KEYGUARD_LAYER = 14;
    static final int KEYGUARD_DIALOG_LAYER = 15;
    // things in here CAN NOT take focus, but are shown on top of everything else.
    static final int SYSTEM_OVERLAY_LAYER = 16;
    static final int SECURE_SYSTEM_OVERLAY_LAYER = 17;

    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;

    // Debugging: set this to have the system act like there is no hard keyboard.
    static final boolean KEYBOARD_ALWAYS_HIDDEN = false;

    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

    // Useful scan codes.
    private static final int SW_LID = 0x00;
    private static final int BTN_MOUSE = 0x110;

    final Object mLock = new Object();

    Context mContext;
    Context mUiContext;
    IWindowManager mWindowManager;
    LocalPowerManager mPowerManager;
    Vibrator mVibrator; // Vibrator for giving feedback of orientation changes

    // Vibrator pattern for haptic feedback of a long press.
    long[] mLongPressVibePattern;

    // Vibrator pattern for haptic feedback of virtual key press.
    long[] mVirtualKeyVibePattern;

    // Vibrator pattern for a short vibration.
    long[] mKeyboardTapVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is disabled.
    long[] mSafeModeDisabledVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is enabled.
    long[] mSafeModeEnabledVibePattern;

    // Vibrator pattern for haptic feedback on KEY_UP
    long[] mVirtualKeyUpVibePattern;

    /** If true, hitting shift & menu will broadcast Intent.ACTION_BUG_REPORT */
    boolean mEnableShiftMenuBugReports = false;

    boolean mSafeMode;
    WindowState mStatusBar = null;
    final ArrayList<WindowState> mStatusBarPanels = new ArrayList<WindowState>();
    WindowState mKeyguard = null;
    KeyguardViewMediator mKeyguardMediator = null;
    GlobalActions mGlobalActions;
    volatile boolean mPowerKeyHandled;
    RecentApplicationsDialog mRecentAppsDialog;
    Handler mHandler;

    boolean mSystemReady;
    boolean mLidOpen;
    int mUiMode = Configuration.UI_MODE_TYPE_NORMAL;
    int mDockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    int mLidOpenRotation;
    int mCarDockRotation;
    int mDeskDockRotation;

    int mUserRotationMode = WindowManagerPolicy.USER_ROTATION_FREE;
    int mUserRotation = Surface.ROTATION_0;

    boolean mAllowAllRotations;
    boolean mCarDockEnablesAccelerometer;
    boolean mDeskDockEnablesAccelerometer;
    int mLidKeyboardAccessibility;
    int mLidNavigationAccessibility;
    boolean mScreenOn = false;
    int mScreenOffReason;
    boolean mOrientationSensorEnabled = false;
    int mCurrentAppOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    static final int DEFAULT_ACCELEROMETER_ROTATION = 0;
    int mAccelerometerDefault = DEFAULT_ACCELEROMETER_ROTATION;
    boolean mHasSoftInput = false;
    boolean mCameraKeyPressable = false;
    static final long NEXT_DURATION = 400;
    private boolean mBottomBar;
    int mPointerLocationMode = 0;
    PointerLocationView mPointerLocationView = null;
    InputChannel mPointerLocationInputChannel;

    private final InputHandler mPointerLocationInputHandler = new BaseInputHandler() {
        @Override
        public void handleMotion(MotionEvent event, Runnable finishedCallback) {
            finishedCallback.run();

            synchronized (mLock) {
                if (mPointerLocationView != null) {
                    mPointerLocationView.addTouchEvent(event);
                }
            }
        }
    };

    // The current size of the screen.
    int mW, mH;
    // During layout, the current screen borders with all outer decoration
    // (status bar, input method dock) accounted for.
    int mCurLeft, mCurTop, mCurRight, mCurBottom;
    // During layout, the frame in which content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.  This is usually
    // the same as mCur*, but may be larger if the screen decor has supplied
    // content insets.
    int mContentLeft, mContentTop, mContentRight, mContentBottom;
    // During layout, the current screen borders along with input method
    // windows are placed.
    int mDockLeft, mDockTop, mDockRight, mDockBottom;
    // During layout, the layer at which the doc window is placed.
    int mDockLayer;

    static final Rect mTmpParentFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();

    WindowState mTopFullscreenOpaqueWindowState;
    boolean mForceStatusBar;
    boolean mHideLockScreen;
    boolean mDismissKeyguard;
    boolean mHomePressed;
    Intent mHomeIntent;
    Intent mCarDockIntent;
    Intent mDeskDockIntent;
    boolean mSearchKeyPressed;
    boolean mConsumeSearchKeyUp;

    // support for activating the lock screen while the screen is on
    boolean mAllowLockscreenWhenOn;
    int mLockScreenTimeout;
    boolean mLockScreenTimerActive;

    // Behavior of ENDCALL Button.  (See Settings.System.END_BUTTON_BEHAVIOR.)
    int mEndcallBehavior;

    // Behavior of trackball wake
    boolean mTrackballWakeScreen;

    // Behavior of volume wake
    boolean mVolumeWakeScreen;

    // Behavior of volbtn music controls
    boolean mVolBtnMusicControls;
    // Behavior of cambtn music controls
    boolean mCamBtnMusicControls;
    // keeps track of long press state
    boolean mIsLongPress;
    // keeps track of headset button repeat count
    int mHsetRepeats;

    // Behavior of POWER button while in-call and screen on.
    // (See Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR.)
    int mIncallPowerBehavior;

    int mLandscapeRotation = -1; // default landscape rotation
    int mSeascapeRotation = -1; // "other" landscape rotation, 180 degrees from mLandscapeRotation
    int mPortraitRotation = -1; // default portrait rotation
    int mUpsideDownRotation = -1; // "other" portrait rotation

    // Nothing to see here, move along...
    int mFancyRotationAnimation;

    ShortcutManager mShortcutManager;
    PowerManager.WakeLock mBroadcastWakeLock;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.END_BUTTON_BEHAVIOR), false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACCELEROMETER_ROTATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.POINTER_LOCATION), false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DEFAULT_INPUT_METHOD), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    "fancy_rotation_anim"), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TRACKBALL_WAKE_SCREEN), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOLUME_WAKE_SCREEN), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOLBTN_MUSIC_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CAMBTN_MUSIC_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BOTTOM), false, this);
            updateSettings();
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
            try {
                mWindowManager.setRotation(USE_LAST_ROTATION, false,
                        mFancyRotationAnimation);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }

    class MyOrientationListener extends WindowOrientationListener {
        MyOrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int rotation) {
            // Send updates based on orientation value
            if (localLOGV) Log.v(TAG, "onOrientationChanged, rotation changed to " +rotation);
            try {
                mWindowManager.setRotation(rotation, false,
                        mFancyRotationAnimation);
            } catch (RemoteException e) {
                // Ignore

            }
        }
    }
    MyOrientationListener mOrientationListener;

    boolean useSensorForOrientationLp(int appOrientation) {
        // The app says use the sensor.
        if (appOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                || appOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                || appOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || appOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
            return true;
        }
        // The user preference says we can rotate, and the app is willing to rotate.
        if (mAccelerometerDefault != 0 &&
                (appOrientation == ActivityInfo.SCREEN_ORIENTATION_USER
                 || appOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)) {
            return true;
        }
        // We're in a dock that has a rotation affinity, and the app is willing to rotate.
        if ((mCarDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_CAR)
                || (mDeskDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_DESK)) {
            // Note we override the nosensor flag here.
            if (appOrientation == ActivityInfo.SCREEN_ORIENTATION_USER
                    || appOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    || appOrientation == ActivityInfo.SCREEN_ORIENTATION_NOSENSOR) {
                return true;
            }
        }
        // Else, don't use the sensor.
        return false;
    }

    /*
     * We always let the sensor be switched on by default except when
     * the user has explicitly disabled sensor based rotation or when the
     * screen is switched off.
     */
    boolean needSensorRunningLp() {
        if (mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            // If the application has explicitly requested to follow the
            // orientation, then we need to turn the sensor or.
            return true;
        }
        if ((mCarDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_CAR) ||
                (mDeskDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_DESK)) {
            // enable accelerometer if we are docked in a dock that enables accelerometer
            // orientation management,
            return true;
        }
        if (mAccelerometerDefault == 0) {
            // If the setting for using the sensor by default is enabled, then
            // we will always leave it on.  Note that the user could go to
            // a window that forces an orientation that does not use the
            // sensor and in theory we could turn it off... however, when next
            // turning it on we won't have a good value for the current
            // orientation for a little bit, which can cause orientation
            // changes to lag, so we'd like to keep it always on.  (It will
            // still be turned off when the screen is off.)
            return false;
        }
        return true;
    }

    /*
     * Various use cases for invoking this function
     * screen turning off, should always disable listeners if already enabled
     * screen turned on and current app has sensor based orientation, enable listeners
     * if not already enabled
     * screen turned on and current app does not have sensor orientation, disable listeners if
     * already enabled
     * screen turning on and current app has sensor based orientation, enable listeners if needed
     * screen turning on and current app has nosensor based orientation, do nothing
     */
    void updateOrientationListenerLp() {
        if (!mOrientationListener.canDetectOrientation()) {
            // If sensor is turned off or nonexistent for some reason
            return;
        }
        //Could have been invoked due to screen turning on or off or
        //change of the currently visible window's orientation
        if (localLOGV) Log.v(TAG, "Screen status="+mScreenOn+
                ", current orientation="+mCurrentAppOrientation+
                ", SensorEnabled="+mOrientationSensorEnabled);
        boolean disable = true;
        if (mScreenOn) {
            if (needSensorRunningLp()) {
                disable = false;
                //enable listener if not already enabled
                if (!mOrientationSensorEnabled) {
                    mOrientationListener.enable();
                    if(localLOGV) Log.v(TAG, "Enabling listeners");
                    mOrientationSensorEnabled = true;
                }
            }
        }
        //check if sensors need to be disabled
        if (disable && mOrientationSensorEnabled) {
            mOrientationListener.disable();
            if(localLOGV) Log.v(TAG, "Disabling listeners");
            mOrientationSensorEnabled = false;
        }
    }

    private void interceptPowerKeyDown(boolean handled) {
        mPowerKeyHandled = handled;
        if (!handled) {
            mHandler.postDelayed(mPowerLongPress, ViewConfiguration.getGlobalActionKeyTimeout());
        }
    }

    private boolean interceptPowerKeyUp(boolean canceled) {
        if (!mPowerKeyHandled) {
            mHandler.removeCallbacks(mPowerLongPress);
            return !canceled;
        } else {
            mPowerKeyHandled = true;
            return false;
        }
    }

    private final Runnable mPowerLongPress = new Runnable() {
        public void run() {
            if (!mPowerKeyHandled) {
                mPowerKeyHandled = true;
                performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
                showGlobalActionsDialog();
            }
        }
    };

    void showGlobalActionsDialog() {
        if (mGlobalActions == null) {
            mGlobalActions = new GlobalActions(mContext);
        }
        final boolean keyguardShowing = mKeyguardMediator.isShowingAndNotHidden();
        mGlobalActions.showDialog(keyguardShowing, isDeviceProvisioned());
        if (keyguardShowing) {
            // since it took two seconds of long press to bring this up,
            // poke the wake lock so they have some time to see the dialog.
            mKeyguardMediator.pokeWakelock();
        }
    }

    boolean isDeviceProvisioned() {
        return Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.DEVICE_PROVISIONED, 0) != 0;
    }

    /**
     * When a home-key longpress expires, close other system windows and launch the recent apps
     */
    Runnable mHomeLongPress = new Runnable() {
        public void run() {
            /*
             * Eat the longpress so it won't dismiss the recent apps dialog when
             * the user lets go of the home key
             */
            mHomePressed = false;
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.USE_CUSTOM_APP, 0) != 0) {
                runCustomApp();
            } else {
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_RECENT_APPS);
                showRecentAppsDialog();
            }
        }
    };

    Runnable mBackLongPress = new Runnable() {
        public void run() {
            if (Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.KILL_APP_LONGPRESS_BACK, 0) == 0) {
                // Bail out unless the user has elected to turn this on.
                return;
            }
            try {
                performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                IActivityManager mgr = ActivityManagerNative.getDefault();
                List<RunningAppProcessInfo> apps = mgr.getRunningAppProcesses();
                for (RunningAppProcessInfo appInfo : apps) {
                    int uid = appInfo.uid;
                    // Make sure it's a foreground user application (not system,
                    // root, phone, etc.)
                    if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                            && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        // Kill the entire pid
                        Toast.makeText(getUiContext(), R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                        if (appInfo.pkgList!=null && (apps.size() > 0)){
                                mgr.forceStopPackage(appInfo.pkgList[0]);
                        }else{
                                Process.killProcess(appInfo.pid);
                        }
                        break;
                    }
                }
            } catch (RemoteException remoteException) {
                // Do nothing; just let it go.
            }
        }
    };

    /**
     * When a volumeup-key longpress expires, skip songs based on key press
     */
    Runnable mVolumeUpLongPress = new Runnable() {
        public void run() {
            // set the long press flag to true
            mIsLongPress = true;

            // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
        };
    };

    /**
     * When a volumedown-key longpress expires, skip songs based on key press
     */
    Runnable mVolumeDownLongPress = new Runnable() {
        public void run() {
            // set the long press flag to true
            mIsLongPress = true;

            // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        };
    };

    /**
     * When a headset hook longpress expires, pass new repeat event to user
     */
    Runnable mHsetHookLongPress = new Runnable() {
        public void run() {
            if (mScreenOn) return;
            mHsetRepeats++;
            long eventtime = SystemClock.uptimeMillis();
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, mHsetRepeats);
            if (mHsetRepeats == 1) {
                downEvent = KeyEvent.changeFlags(downEvent, KeyEvent.FLAG_LONG_PRESS);
            }
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            mContext.sendOrderedBroadcast(downIntent, null);
            mHandler.postDelayed(mHsetHookLongPress, ViewConfiguration.getLongPressTimeout());
        };
    };

    /**
     * When a camera-key longpress expires, toggle play/pause based on key press
     */
    Runnable mCameraLongPress = new Runnable() {
        public void run() {
            // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        };
    };

    private Context getUiContext() {
        if (mUiContext == null) {
            mUiContext = ThemeUtils.createUiContext(mContext);
        }
        return mUiContext != null ? mUiContext : mContext;
    }

    protected void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        mContext.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        mContext.sendOrderedBroadcast(upIntent, null);
    }

    protected void sendHwButtonEvent(int keycode) {
        try {
            mWindowManager.injectKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode), true);
            mWindowManager.injectKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode), true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create (if necessary) and launch the recent apps dialog
     */
    void showRecentAppsDialog() {
        if (mRecentAppsDialog == null) {
            mRecentAppsDialog = new RecentApplicationsDialog(mContext);
        }
        mRecentAppsDialog.show();
    }

    void runCustomApp() {
        String uri = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.SELECTED_CUSTOM_APP);

        if (uri != null) {
            try {
                Intent i = Intent.parseUri(uri, 0);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                mContext.startActivity(i);
            } catch (URISyntaxException e) {

            } catch (ActivityNotFoundException e) {

            }
        }
    }

    /** {@inheritDoc} */
    public void init(Context context, IWindowManager windowManager,
            LocalPowerManager powerManager) {
        mContext = context;
        mWindowManager = windowManager;
        mPowerManager = powerManager;
        if(mKeyguardMediator==null)
            mKeyguardMediator = new KeyguardViewMediator(context, this, powerManager);
        mHandler = new Handler();
        mOrientationListener = new MyOrientationListener(mContext);
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        mShortcutManager = new ShortcutManager(context, mHandler);
        mShortcutManager.observe();
        mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mCarDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mCarDockIntent.addCategory(Intent.CATEGORY_CAR_DOCK);
        mCarDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mDeskDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mDeskDockIntent.addCategory(Intent.CATEGORY_DESK_DOCK);
        mDeskDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mBroadcastWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mBroadcastWakeLock");
        mEnableShiftMenuBugReports = "1".equals(SystemProperties.get("ro.debuggable"));
        mLidOpenRotation = readRotation(
                com.android.internal.R.integer.config_lidOpenRotation);
        mCarDockRotation = readRotation(
                com.android.internal.R.integer.config_carDockRotation);
        mDeskDockRotation = readRotation(
                com.android.internal.R.integer.config_deskDockRotation);
        mAllowAllRotations = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowAllRotations);
        mCarDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_carDockEnablesAccelerometer);
        mDeskDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_deskDockEnablesAccelerometer);
        mLidKeyboardAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidKeyboardAccessibility);
        mLidNavigationAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidNavigationAccessibility);
        // register for dock events
        IntentFilter filter = new IntentFilter();
        filter.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_ENTER_DESK_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
        filter.addAction(Intent.ACTION_DOCK_EVENT);
        Intent intent = context.registerReceiver(mDockReceiver, filter);
        if (intent != null) {
            // Retrieve current sticky dock event broadcast.
            mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                    Intent.EXTRA_DOCK_STATE_UNDOCKED);
        }
        // register for theme change events
        ThemeUtils.registerThemeChangeReceiver(context, mThemeChangeReceiver);
        mVibrator = new Vibrator();
        mLongPressVibePattern = loadHaptic(HapticFeedbackConstants.LONG_PRESS);
        mVirtualKeyVibePattern = loadHaptic(HapticFeedbackConstants.VIRTUAL_KEY);
        mKeyboardTapVibePattern = loadHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        mSafeModeDisabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeDisabledVibePattern);
        mSafeModeEnabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeEnabledVibePattern);
        mVirtualKeyUpVibePattern = loadHaptic(HapticFeedbackConstants.VIRTUAL_RELEASED);
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        boolean updateRotation = false;
        View addView = null;
        View removeView = null;
        synchronized (mLock) {
            mEndcallBehavior = Settings.System.getInt(resolver,
                    Settings.System.END_BUTTON_BEHAVIOR,
                    Settings.System.END_BUTTON_BEHAVIOR_DEFAULT);
            mIncallPowerBehavior = Settings.Secure.getInt(resolver,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT);
            mFancyRotationAnimation = Settings.System.getInt(resolver,
                    "fancy_rotation_anim", 0) != 0 ? 0x80 : 0;
            mTrackballWakeScreen = (Settings.System.getInt(resolver,
                    Settings.System.TRACKBALL_WAKE_SCREEN, 1) == 1);
            mVolumeWakeScreen = (Settings.System.getInt(resolver,
                    Settings.System.VOLUME_WAKE_SCREEN, 0) == 1);
            mVolBtnMusicControls = (Settings.System.getInt(resolver,
                    Settings.System.VOLBTN_MUSIC_CONTROLS, 1) == 1);
            mCamBtnMusicControls = (Settings.System.getInt(resolver,
                    Settings.System.CAMBTN_MUSIC_CONTROLS, 0) == 1);
            int defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_BOTTOM_STATUS_BAR) ? 1 : 0);
            mBottomBar = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_BOTTOM, defValue) == 1);
            int accelerometerDefault = Settings.System.getInt(resolver,
                    Settings.System.ACCELEROMETER_ROTATION, DEFAULT_ACCELEROMETER_ROTATION);
            if (mAccelerometerDefault != accelerometerDefault) {
                mAccelerometerDefault = accelerometerDefault;
                updateOrientationListenerLp();
            }
            if (mSystemReady) {
                int pointerLocation = Settings.System.getInt(resolver,
                        Settings.System.POINTER_LOCATION, 0);
                if (mPointerLocationMode != pointerLocation) {
                    mPointerLocationMode = pointerLocation;
                    if (pointerLocation != 0) {
                        if (mPointerLocationView == null) {
                            mPointerLocationView = new PointerLocationView(mContext);
                            mPointerLocationView.setPrintCoords(false);
                            addView = mPointerLocationView;
                        }
                    } else {
                        removeView = mPointerLocationView;
                        mPointerLocationView = null;
                    }
                }
            }
            // use screen off timeout setting as the timeout for the lockscreen
            mLockScreenTimeout = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT, 0);
            String imId = Settings.Secure.getString(resolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            boolean hasSoftInput = imId != null && imId.length() > 0;
            if (mHasSoftInput != hasSoftInput) {
                mHasSoftInput = hasSoftInput;
                updateRotation = true;
            }
        }
        if (updateRotation) {
            updateRotation(0);
        }
        if (addView != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            lp.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
            lp.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            lp.format = PixelFormat.TRANSLUCENT;
            lp.setTitle("PointerLocation");
            WindowManagerImpl wm = (WindowManagerImpl)
                    mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(addView, lp);

            if (mPointerLocationInputChannel == null) {
                try {
                    mPointerLocationInputChannel =
                        mWindowManager.monitorInput("PointerLocationView");
                    InputQueue.registerInputChannel(mPointerLocationInputChannel,
                            mPointerLocationInputHandler, mHandler.getLooper().getQueue());
                } catch (RemoteException ex) {
                    Slog.e(TAG, "Could not set up input monitoring channel for PointerLocation.",
                            ex);
                }
            }
        }
        if (removeView != null) {
            if (mPointerLocationInputChannel != null) {
                InputQueue.unregisterInputChannel(mPointerLocationInputChannel);
                mPointerLocationInputChannel.dispose();
                mPointerLocationInputChannel = null;
            }

            WindowManagerImpl wm = (WindowManagerImpl)
                    mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(removeView);
        }
    }

    private int readRotation(int resID) {
        try {
            int rotation = mContext.getResources().getInteger(resID);
            switch (rotation) {
                case 0:
                    return Surface.ROTATION_0;
                case 90:
                    return Surface.ROTATION_90;
                case 180:
                    return Surface.ROTATION_180;
                case 270:
                    return Surface.ROTATION_270;
            }
        } catch (Resources.NotFoundException e) {
            // fall through
        }
        return -1;
    }

    /** {@inheritDoc} */
    public int checkAddPermission(WindowManager.LayoutParams attrs) {
        int type = attrs.type;

        if (type < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW
                || type > WindowManager.LayoutParams.LAST_SYSTEM_WINDOW) {
            return WindowManagerImpl.ADD_OKAY;
        }
        String permission = null;
        switch (type) {
            case TYPE_TOAST:
                // XXX right now the app process has complete control over
                // this...  should introduce a token to let the system
                // monitor/control what they are doing.
                break;
            case TYPE_INPUT_METHOD:
            case TYPE_WALLPAPER:
                // The window manager will check these.
                break;
            case TYPE_PHONE:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SYSTEM_ALERT:
            case TYPE_SYSTEM_ERROR:
            case TYPE_SYSTEM_OVERLAY:
                permission = android.Manifest.permission.SYSTEM_ALERT_WINDOW;
                break;
            default:
                permission = android.Manifest.permission.INTERNAL_SYSTEM_WINDOW;
        }
        if (permission != null) {
            if (mContext.checkCallingOrSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return WindowManagerImpl.ADD_PERMISSION_DENIED;
            }
        }
        return WindowManagerImpl.ADD_OKAY;
    }

    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_SYSTEM_OVERLAY:
            case TYPE_SECURE_SYSTEM_OVERLAY:
            case TYPE_TOAST:
                // These types of windows can't receive input events.
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                break;
        }
    }

    void readLidState() {
        try {
            int sw = mWindowManager.getSwitchState(SW_LID);
            if (sw >= 0) {
                mLidOpen = sw == 0;
            }
        } catch (RemoteException e) {
            // Ignore
        }
    }

    private int determineHiddenState(boolean lidOpen,
            int mode, int hiddenValue, int visibleValue) {
        switch (mode) {
            case 1:
                return lidOpen ? visibleValue : hiddenValue;
            case 2:
                return lidOpen ? hiddenValue : visibleValue;
        }
        return visibleValue;
    }

    /** {@inheritDoc} */
    public void adjustConfigurationLw(Configuration config) {
        readLidState();
        final boolean lidOpen = !KEYBOARD_ALWAYS_HIDDEN && mLidOpen;
        mPowerManager.setKeyboardVisibility(lidOpen);
        config.hardKeyboardHidden = determineHiddenState(lidOpen,
                mLidKeyboardAccessibility, Configuration.HARDKEYBOARDHIDDEN_YES,
                Configuration.HARDKEYBOARDHIDDEN_NO);
        config.navigationHidden = determineHiddenState(lidOpen,
                mLidNavigationAccessibility, Configuration.NAVIGATIONHIDDEN_YES,
                Configuration.NAVIGATIONHIDDEN_NO);
        config.keyboardHidden = (config.hardKeyboardHidden
                        == Configuration.HARDKEYBOARDHIDDEN_NO || mHasSoftInput)
                ? Configuration.KEYBOARDHIDDEN_NO
                : Configuration.KEYBOARDHIDDEN_YES;
    }

    /** {@inheritDoc} */
    public int windowTypeToLayerLw(int type) {
        if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
            return APPLICATION_LAYER;
        }
        switch (type) {
        case TYPE_STATUS_BAR:
            return STATUS_BAR_LAYER;
        case TYPE_STATUS_BAR_PANEL:
            return STATUS_BAR_PANEL_LAYER;
        case TYPE_SYSTEM_DIALOG:
            return SYSTEM_DIALOG_LAYER;
        case TYPE_SEARCH_BAR:
            return SEARCH_BAR_LAYER;
        case TYPE_PHONE:
            return PHONE_LAYER;
        case TYPE_KEYGUARD:
            return KEYGUARD_LAYER;
        case TYPE_KEYGUARD_DIALOG:
            return KEYGUARD_DIALOG_LAYER;
        case TYPE_SYSTEM_ALERT:
            return SYSTEM_ALERT_LAYER;
        case TYPE_SYSTEM_ERROR:
            return SYSTEM_ERROR_LAYER;
        case TYPE_INPUT_METHOD:
            return INPUT_METHOD_LAYER;
        case TYPE_INPUT_METHOD_DIALOG:
            return INPUT_METHOD_DIALOG_LAYER;
        case TYPE_SYSTEM_OVERLAY:
            return SYSTEM_OVERLAY_LAYER;
        case TYPE_SECURE_SYSTEM_OVERLAY:
            return SECURE_SYSTEM_OVERLAY_LAYER;
        case TYPE_PRIORITY_PHONE:
            return PRIORITY_PHONE_LAYER;
        case TYPE_TOAST:
            return TOAST_LAYER;
        case TYPE_WALLPAPER:
            return WALLPAPER_LAYER;
        }
        Log.e(TAG, "Unknown window type: " + type);
        return APPLICATION_LAYER;
    }

    /** {@inheritDoc} */
    public int subWindowTypeToLayerLw(int type) {
        switch (type) {
        case TYPE_APPLICATION_PANEL:
        case TYPE_APPLICATION_ATTACHED_DIALOG:
            return APPLICATION_PANEL_SUBLAYER;
        case TYPE_APPLICATION_MEDIA:
            return APPLICATION_MEDIA_SUBLAYER;
        case TYPE_APPLICATION_MEDIA_OVERLAY:
            return APPLICATION_MEDIA_OVERLAY_SUBLAYER;
        case TYPE_APPLICATION_SUB_PANEL:
            return APPLICATION_SUB_PANEL_SUBLAYER;
        }
        Log.e(TAG, "Unknown sub-window type: " + type);
        return 0;
    }

    public int getMaxWallpaperLayer() {
        return STATUS_BAR_LAYER;
    }

    public boolean doesForceHide(WindowState win, WindowManager.LayoutParams attrs) {
        return attrs.type == WindowManager.LayoutParams.TYPE_KEYGUARD;
    }

    public boolean canBeForceHidden(WindowState win, WindowManager.LayoutParams attrs) {
        return attrs.type != WindowManager.LayoutParams.TYPE_STATUS_BAR
                && attrs.type != WindowManager.LayoutParams.TYPE_WALLPAPER;
    }

    /** {@inheritDoc} */
    public View addStartingWindow(IBinder appToken, String packageName,
                                  int theme, CharSequence nonLocalizedLabel,
                                  int labelRes, int icon) {
        if (!SHOW_STARTING_ANIMATIONS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        try {
            Context context = mContext;
            boolean setTheme = false;
            //Log.i(TAG, "addStartingWindow " + packageName + ": nonLocalizedLabel="
            //        + nonLocalizedLabel + " theme=" + Integer.toHexString(theme));
            try {
                context = context.createPackageContext(packageName, 0);
                if (theme != 0) {
                    context.setTheme(theme);
                    setTheme = true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
            if (!setTheme) {
                context.setTheme(com.android.internal.R.style.Theme);
            }

            Window win = PolicyManager.makeNewWindow(context);
            if (win.getWindowStyle().getBoolean(
                    com.android.internal.R.styleable.Window_windowDisablePreview, false)) {
                return null;
            }

            Resources r = context.getResources();
            win.setTitle(r.getText(labelRes, nonLocalizedLabel));

            win.setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);
            // Force the window flags: this is a fake window, so it is not really
            // touchable or focusable by the user.  We also add in the ALT_FOCUSABLE_IM
            // flag because we do know that the next window will take input
            // focus, so we want to get the IME window up on top of us right away.
            win.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT);

            final WindowManager.LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
            params.setTitle("Starting " + packageName);

            WindowManagerImpl wm = (WindowManagerImpl)
                    context.getSystemService(Context.WINDOW_SERVICE);
            View view = win.getDecorView();

            if (win.isFloating()) {
                // Whoops, there is no way to display an animation/preview
                // of such a thing!  After all that work...  let's skip it.
                // (Note that we must do this here because it is in
                // getDecorView() where the theme is evaluated...  maybe
                // we should peek the floating attribute from the theme
                // earlier.)
                return null;
            }

            if (localLOGV) Log.v(
                TAG, "Adding starting window for " + packageName
                + " / " + appToken + ": "
                + (view.getParent() != null ? view : null));

            wm.addView(view, params);

            // Only return the view if it was successfully added to the
            // window manager... which we can tell by it having a parent.
            return view.getParent() != null ? view : null;
        } catch (WindowManagerImpl.BadTokenException e) {
            // ignore
            Log.w(TAG, appToken + " already running, starting window not displayed");
        } catch (RuntimeException e) {
            // don't crash if something else bad happens, for example a
            // failure loading resources because we are loading from an app
            // on external storage that has been unmounted.
            Log.w(TAG, appToken + " failed creating starting window", e);
        }

        return null;
    }

    /** {@inheritDoc} */
    public void removeStartingWindow(IBinder appToken, View window) {
        // RuntimeException e = new RuntimeException();
        // Log.i(TAG, "remove " + appToken + " " + window, e);

        if (localLOGV) Log.v(
            TAG, "Removing starting window for " + appToken + ": " + window);

        if (window != null) {
            WindowManagerImpl wm = (WindowManagerImpl) mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(window);
        }
    }

    /**
     * Preflight adding a window to the system.
     *
     * Currently enforces that three window types are singletons:
     * <ul>
     * <li>STATUS_BAR_TYPE</li>
     * <li>KEYGUARD_TYPE</li>
     * </ul>
     *
     * @param win The window to be added
     * @param attrs Information about the window to be added
     *
     * @return If ok, WindowManagerImpl.ADD_OKAY.  If too many singletons, WindowManagerImpl.ADD_MULTIPLE_SINGLETON
     */
    public int prepareAddWindowLw(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                // TODO: Need to handle the race condition of the status bar proc
                // dying and coming back before the removeWindowLw cleanup has happened.
                if (mStatusBar != null) {
                    return WindowManagerImpl.ADD_MULTIPLE_SINGLETON;
                }
                mStatusBar = win;
                break;
            case TYPE_STATUS_BAR_PANEL:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                mStatusBarPanels.add(win);
                break;
            case TYPE_KEYGUARD:
                if (mKeyguard != null) {
                    return WindowManagerImpl.ADD_MULTIPLE_SINGLETON;
                }
                mKeyguard = win;
                break;
        }
        return WindowManagerImpl.ADD_OKAY;
    }

    /** {@inheritDoc} */
    public void removeWindowLw(WindowState win) {
        if (mStatusBar == win) {
            mStatusBar = null;
        }
        else if (mKeyguard == win) {
            mKeyguard = null;
        } else {
            mStatusBarPanels.remove(win);
        }
    }

    static final boolean PRINT_ANIM = false;

    /** {@inheritDoc} */
    public int selectAnimationLw(WindowState win, int transit) {
        if (PRINT_ANIM) Log.i(TAG, "selectAnimation in " + win
              + ": transit=" + transit);
        if (transit == TRANSIT_PREVIEW_DONE) {
            if (win.hasAppShownWindows()) {
                if (PRINT_ANIM) Log.i(TAG, "**** STARTING EXIT");
                return com.android.internal.R.anim.app_starting_exit;
            }
        }

        return 0;
    }

    public Animation createForceHideEnterAnimation() {
        return AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.lock_screen_behind_enter);
    }
    static ITelephony getTelephonyService() {
        ITelephony telephonyService = ITelephony.Stub.asInterface(
                ServiceManager.checkService(Context.TELEPHONY_SERVICE));
        if (telephonyService == null) {
            Log.w(TAG, "Unable to find ITelephony interface.");
        }
        return telephonyService;
    }

    static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(
                ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    boolean keyguardOn() {
        return keyguardIsShowingTq() || inKeyguardRestrictedKeyInputMode();
    }

    private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
        };

    /** {@inheritDoc} */
    @Override
    public boolean interceptKeyBeforeDispatching(WindowState win, int action, int flags,
            int keyCode, int scanCode, int metaState, int repeatCount, int policyFlags) {
        final boolean keyguardOn = keyguardOn();
        final boolean down = (action == KeyEvent.ACTION_DOWN);
        final boolean canceled = ((flags & KeyEvent.FLAG_CANCELED) != 0);

        if (false) {
            Log.d(TAG, "interceptKeyTi keyCode=" + keyCode + " down=" + down + " repeatCount="
                    + repeatCount + " keyguardOn=" + keyguardOn + " mHomePressed=" + mHomePressed);
        }

        // Clear a pending HOME longpress if the user releases Home
        // TODO: This could probably be inside the next bit of logic, but that code
        // turned out to be a bit fragile so I'm doing it here explicitly, for now.
        if ((keyCode == KeyEvent.KEYCODE_HOME) && !down) {
            mHandler.removeCallbacks(mHomeLongPress);
        }

        // Clear a pending BACK longpress if the user releases Back.
        if ((keyCode == KeyEvent.KEYCODE_BACK) && !down) {
            mHandler.removeCallbacks(mBackLongPress);
        }

        // If the HOME button is currently being held, then we do special
        // chording with it.
        if (mHomePressed) {

            // If we have released the home key, and didn't do anything else
            // while it was pressed, then it is time to go home!
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                if (!down) {
                    mHomePressed = false;

                    if (!canceled) {
                        // If an incoming call is ringing, HOME is totally disabled.
                        // (The user is already on the InCallScreen at this point,
                        // and his ONLY options are to answer or reject the call.)
                        boolean incomingRinging = false;
                        try {
                            ITelephony telephonyService = getTelephonyService();
                            if (telephonyService != null) {
                                incomingRinging = telephonyService.isRinging();
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "RemoteException from getPhoneInterface()", ex);
                        }

                        if (incomingRinging) {
                            Log.i(TAG, "Ignoring HOME; there's a ringing incoming call.");
                        } else {
                            launchHomeFromHotKey();
                        }
                    } else {
                        Log.i(TAG, "Ignoring HOME; event canceled.");
                    }
                }
            }

            return true;
        }

        // First we always handle the home key here, so applications
        // can never break it, although if keyguard is on, we do let
        // it handle it, because that gives us the correct 5 second
        // timeout.
        if (keyCode == KeyEvent.KEYCODE_HOME) {

            // If a system window has focus, then it doesn't make sense
            // right now to interact with applications.
            WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
            if (attrs != null) {
                final int type = attrs.type;
                if (type == WindowManager.LayoutParams.TYPE_KEYGUARD
                        || type == WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG) {
                    // the "app" is keyguard, so give it the key
                    return false;
                }
                final int typeCount = WINDOW_TYPES_WHERE_HOME_DOESNT_WORK.length;
                for (int i=0; i<typeCount; i++) {
                    if (type == WINDOW_TYPES_WHERE_HOME_DOESNT_WORK[i]) {
                        // don't do anything, but also don't pass it to the app
                        return true;
                    }
                }
            }

            if (down && repeatCount == 0) {
                if (!keyguardOn) {
                    mHandler.postDelayed(mHomeLongPress, ViewConfiguration.getGlobalActionKeyTimeout());
                }
                mHomePressed = true;
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (down && repeatCount == 0) {
                mHandler.postDelayed(mBackLongPress, ViewConfiguration.getGlobalActionKeyTimeout());
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Hijack modified menu keys for debugging features
            final int chordBug = KeyEvent.META_SHIFT_ON;

            if (down && repeatCount == 0) {
                if (mEnableShiftMenuBugReports && (metaState & chordBug) == chordBug) {
                    Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
                    mContext.sendOrderedBroadcast(intent, null);
                    return true;
                } else if (SHOW_PROCESSES_ON_ALT_MENU &&
                        (metaState & KeyEvent.META_ALT_ON) == KeyEvent.META_ALT_ON) {
                    Intent service = new Intent();
                    service.setClassName(mContext, "com.android.server.LoadAverageService");
                    ContentResolver res = mContext.getContentResolver();
                    boolean shown = Settings.System.getInt(
                            res, Settings.System.SHOW_PROCESSES, 0) != 0;
                    if (!shown) {
                        mContext.startService(service);
                    } else {
                        mContext.stopService(service);
                    }
                    Settings.System.putInt(
                            res, Settings.System.SHOW_PROCESSES, shown ? 0 : 1);
                    return true;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (down) {
                if (repeatCount == 0) {
                    mSearchKeyPressed = true;
                }
            } else {
                mSearchKeyPressed = false;

                if (mConsumeSearchKeyUp) {
                    // Consume the up-event
                    mConsumeSearchKeyUp = false;
                    return true;
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_ENVELOPE || keyCode == KeyEvent.KEYCODE_EXPLORER
                || keyCode == KeyEvent.KEYCODE_USER1 || keyCode == KeyEvent.KEYCODE_USER2 || keyCode == KeyEvent.KEYCODE_USER3) {
            return handleQuickKeys(win, keyCode, down, keyguardOn);
        }

        // Shortcuts are invoked through Search+key, so intercept those here
        if (mSearchKeyPressed) {
            if (down && repeatCount == 0 && !keyguardOn) {
                Intent shortcutIntent = mShortcutManager.getIntent(keyCode, metaState);
                if (shortcutIntent != null) {
                    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(shortcutIntent);

                    /*
                     * We launched an app, so the up-event of the search key
                     * should be consumed
                     */
                    mConsumeSearchKeyUp = true;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Quick Keys
     */
    private boolean handleQuickKeys(WindowState win, int code, boolean down, boolean keyguardOn) {

        WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
        if (attrs != null) {
            final int type = attrs.type;
            if (type == WindowManager.LayoutParams.TYPE_KEYGUARD
                    || type == WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG) {
                // the "app" is keyguard, so give it the key
                return false;
            }
            if (down) {
                if (!keyguardOn) {
                    String property = null;
                    switch (code) {
                        case KeyEvent.KEYCODE_USER1:
                            property = Settings.System.USER_DEFINED_KEY1_APP;
                            break;
                        case KeyEvent.KEYCODE_USER2:
                            property = Settings.System.USER_DEFINED_KEY2_APP;
                            break;
                        case KeyEvent.KEYCODE_USER3:
                            property = Settings.System.USER_DEFINED_KEY3_APP;
                            break;
                        case KeyEvent.KEYCODE_ENVELOPE:
                            property = Settings.System.USER_DEFINED_KEY_ENVELOPE;
                            break;
                        case KeyEvent.KEYCODE_EXPLORER:
                            property = Settings.System.USER_DEFINED_KEY_EXPLORER;
                            break;
                        default:
                            return false;
                    }
                    String appUri = Settings.System.getString(mContext.getContentResolver(),
                            property);
                    if (appUri != null) {
                        try {
                            Intent qkIntent = Intent.parseUri(appUri, 0);
                            qkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                            mContext.startActivity(qkIntent);
                        } catch (URISyntaxException e) {
                            // TODO: Toast Message
                        } catch (ActivityNotFoundException e) {
                            // TODO: Toast Message
                        }
                    } else {
                        // TODO: Open CMParts
                    }
                    // TODO: Add long press shortcut?
                    // mHandler.postDelayed(mHomeLongPress, ViewConfiguration.getGlobalActionKeyTimeout());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * A home key -> launch home action was detected.  Take the appropriate action
     * given the situation with the keyguard.
     */
    void launchHomeFromHotKey() {
        if (mKeyguardMediator.isShowingAndNotHidden()) {
            // don't launch home if keyguard showing
        } else if (!mHideLockScreen && mKeyguardMediator.isInputRestricted()) {
            // when in keyguard restricted mode, must first verify unlock
            // before launching home
            mKeyguardMediator.verifyUnlock(new OnKeyguardExitResult() {
                public void onKeyguardExitResult(boolean success) {
                    if (success) {
                        try {
                            ActivityManagerNative.getDefault().stopAppSwitches();
                        } catch (RemoteException e) {
                        }
                        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
                        startDockOrHome();
                    }
                }
            });
        } else {
            // no keyguard stuff to worry about, just launch home!
            try {
                ActivityManagerNative.getDefault().stopAppSwitches();
            } catch (RemoteException e) {
            }
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
            startDockOrHome();
        }
    }

    public void getContentInsetHintLw(WindowManager.LayoutParams attrs, Rect contentInset) {
        final int fl = attrs.flags;

        if ((fl &
                (FLAG_LAYOUT_IN_SCREEN | FLAG_FULLSCREEN | FLAG_LAYOUT_INSET_DECOR))
                == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
            contentInset.set(mCurLeft, mCurTop, mW - mCurRight, mH - mCurBottom);
        } else {
            contentInset.setEmpty();
        }
    }

    /** {@inheritDoc} */
    public void beginLayoutLw(int displayWidth, int displayHeight) {
        mW = displayWidth;
        mH = displayHeight;
        mDockLeft = mContentLeft = mCurLeft = 0;
        mDockTop = mContentTop = mCurTop = 0;
        mDockRight = mContentRight = mCurRight = displayWidth;
        mDockBottom = mContentBottom = mCurBottom = displayHeight;
        mDockLayer = 0x10000000;

        // decide where the status bar goes ahead of time
        if (mStatusBar != null) {
            final Rect pf = mTmpParentFrame;
            final Rect df = mTmpDisplayFrame;
            final Rect vf = mTmpVisibleFrame;
            pf.left = df.left = vf.left = 0;
            pf.top = df.top = vf.top = 0;

            if(mBottomBar){
                //get status bar height from dimen.xml
                final int statusbar_height= mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
                //setting status bar's top, to bottom of the screen, minus status bar height
                pf.top = df.top = vf.top = (displayHeight-statusbar_height);
            }

            pf.right = df.right = vf.right = displayWidth;
            pf.bottom = df.bottom = vf.bottom = displayHeight;

            mStatusBar.computeFrameLw(pf, df, vf, vf);
            if (mStatusBar.isVisibleLw()) {
                // If the status bar is hidden, we don't want to cause
                // windows behind it to scroll.
                if(mBottomBar)
                    //setting activites bottoms, to top of status bar
                    mDockBottom = mContentBottom = mCurBottom = mStatusBar.getFrameLw().top;
                else
                    mDockTop = mContentTop = mCurTop = mStatusBar.getFrameLw().bottom;

                if (DEBUG_LAYOUT) Log.v(TAG, "Status bar: mDockBottom="
                        + mDockBottom + " mContentBottom="
                        + mContentBottom + " mCurBottom=" + mCurBottom);
            }
        }
    }

    void setAttachedWindowFrames(WindowState win, int fl, int sim,
            WindowState attached, boolean insetDecors, Rect pf, Rect df, Rect cf, Rect vf) {
        if (win.getSurfaceLayer() > mDockLayer && attached.getSurfaceLayer() < mDockLayer) {
            // Here's a special case: if this attached window is a panel that is
            // above the dock window, and the window it is attached to is below
            // the dock window, then the frames we computed for the window it is
            // attached to can not be used because the dock is effectively part
            // of the underlying window and the attached window is floating on top
            // of the whole thing.  So, we ignore the attached window and explicitly
            // compute the frames that would be appropriate without the dock.
            df.left = cf.left = vf.left = mDockLeft;
            df.top = cf.top = vf.top = mDockTop;
            df.right = cf.right = vf.right = mDockRight;
            df.bottom = cf.bottom = vf.bottom = mDockBottom;
        } else {
            // The effective display frame of the attached window depends on
            // whether it is taking care of insetting its content.  If not,
            // we need to use the parent's content frame so that the entire
            // window is positioned within that content.  Otherwise we can use
            // the display frame and let the attached window take care of
            // positioning its content appropriately.
            if ((sim & SOFT_INPUT_MASK_ADJUST) != SOFT_INPUT_ADJUST_RESIZE) {
                cf.set(attached.getDisplayFrameLw());
            } else {
                // If the window is resizing, then we want to base the content
                // frame on our attached content frame to resize...  however,
                // things can be tricky if the attached window is NOT in resize
                // mode, in which case its content frame will be larger.
                // Ungh.  So to deal with that, make sure the content frame
                // we end up using is not covering the IM dock.
                cf.set(attached.getContentFrameLw());
                if (attached.getSurfaceLayer() < mDockLayer) {
                    if (cf.left < mContentLeft) cf.left = mContentLeft;
                    if (cf.top < mContentTop) cf.top = mContentTop;
                    if (cf.right > mContentRight) cf.right = mContentRight;
                    if (cf.bottom > mContentBottom) cf.bottom = mContentBottom;
                }
            }
            df.set(insetDecors ? attached.getDisplayFrameLw() : cf);
            vf.set(attached.getVisibleFrameLw());
        }
        // The LAYOUT_IN_SCREEN flag is used to determine whether the attached
        // window should be positioned relative to its parent or the entire
        // screen.
        pf.set((fl & FLAG_LAYOUT_IN_SCREEN) == 0
                ? attached.getFrameLw() : df);
    }

    /** {@inheritDoc} */
    public void layoutWindowLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached) {
        // we've already done the status bar
        if (win == mStatusBar) {
            return;
        }

        if (false) {
            if ("com.google.android.youtube".equals(attrs.packageName)
                    && attrs.type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL) {
                Log.i(TAG, "GOTCHA!");
            }
        }

        final int fl = attrs.flags;
        final int sim = attrs.softInputMode;

        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect cf = mTmpContentFrame;
        final Rect vf = mTmpVisibleFrame;

        if (attrs.type == TYPE_INPUT_METHOD) {
            pf.left = df.left = cf.left = vf.left = mDockLeft;
            pf.top = df.top = cf.top = vf.top = mDockTop;
            pf.right = df.right = cf.right = vf.right = mDockRight;
            pf.bottom = df.bottom = cf.bottom = vf.bottom = mDockBottom;
            // IM dock windows always go to the bottom of the screen.
            attrs.gravity = Gravity.BOTTOM;
            mDockLayer = win.getSurfaceLayer();
        } else {
            if ((fl &
                    (FLAG_LAYOUT_IN_SCREEN | FLAG_FULLSCREEN | FLAG_LAYOUT_INSET_DECOR))
                    == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
                // This is the case for a normal activity window: we want it
                // to cover all of the screen space, and it can take care of
                // moving its contents to account for screen decorations that
                // intrude into that space.
                if (attached != null) {
                    // If this window is attached to another, our display
                    // frame is the same as the one we are attached to.
                    setAttachedWindowFrames(win, fl, sim, attached, true, pf, df, cf, vf);
                } else {
                    pf.left = df.left = 0;
                    pf.top = df.top = 0;
                    pf.right = df.right = mW;
                    pf.bottom = df.bottom = mH;
                    if ((sim & SOFT_INPUT_MASK_ADJUST) != SOFT_INPUT_ADJUST_RESIZE) {
                        cf.left = mDockLeft;
                        cf.top = mDockTop;
                        cf.right = mDockRight;
                        cf.bottom = mDockBottom;
                    } else {
                        cf.left = mContentLeft;
                        cf.top = mContentTop;
                        cf.right = mContentRight;
                        cf.bottom = mContentBottom;
                    }
                    vf.left = mCurLeft;
                    vf.top = mCurTop;
                    vf.right = mCurRight;
                    vf.bottom = mCurBottom;
                }
            } else if ((fl & FLAG_LAYOUT_IN_SCREEN) != 0) {
                // A window that has requested to fill the entire screen just
                // gets everything, period.
                pf.left = df.left = cf.left = 0;
                pf.top = df.top = cf.top = 0;
                pf.right = df.right = cf.right = mW;
                pf.bottom = df.bottom = cf.bottom = mH;
                vf.left = mCurLeft;
                vf.top = mCurTop;
                vf.right = mCurRight;
                vf.bottom = mCurBottom;
            } else if (attached != null) {
                // A child window should be placed inside of the same visible
                // frame that its parent had.
                setAttachedWindowFrames(win, fl, sim, attached, false, pf, df, cf, vf);
            } else {
                // Otherwise, a normal window must be placed inside the content
                // of all screen decorations.
                pf.left = mContentLeft;
                pf.top = mContentTop;
                pf.right = mContentRight;
                pf.bottom = mContentBottom;
                if ((sim & SOFT_INPUT_MASK_ADJUST) != SOFT_INPUT_ADJUST_RESIZE) {
                    df.left = cf.left = mDockLeft;
                    df.top = cf.top = mDockTop;
                    df.right = cf.right = mDockRight;
                    df.bottom = cf.bottom = mDockBottom;
                } else {
                    df.left = cf.left = mContentLeft;
                    df.top = cf.top = mContentTop;
                    df.right = cf.right = mContentRight;
                    df.bottom = cf.bottom = mContentBottom;
                }
                vf.left = mCurLeft;
                vf.top = mCurTop;
                vf.right = mCurRight;
                vf.bottom = mCurBottom;
            }
        }

        if ((fl & FLAG_LAYOUT_NO_LIMITS) != 0) {
            df.left = df.top = cf.left = cf.top = vf.left = vf.top = -10000;
            df.right = df.bottom = cf.right = cf.bottom = vf.right = vf.bottom = 10000;
        }

        if (DEBUG_LAYOUT) Log.v(TAG, "Compute frame " + attrs.getTitle()
                + ": sim=#" + Integer.toHexString(sim)
                + " pf=" + pf.toShortString() + " df=" + df.toShortString()
                + " cf=" + cf.toShortString() + " vf=" + vf.toShortString());

        if (false) {
            if ("com.google.android.youtube".equals(attrs.packageName)
                    && attrs.type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL) {
                if (true || localLOGV) Log.v(TAG, "Computing frame of " + win +
                        ": sim=#" + Integer.toHexString(sim)
                        + " pf=" + pf.toShortString() + " df=" + df.toShortString()
                        + " cf=" + cf.toShortString() + " vf=" + vf.toShortString());
            }
        }

        win.computeFrameLw(pf, df, cf, vf);

        // Dock windows carve out the bottom of the screen, so normal windows
        // can't appear underneath them.
        if (attrs.type == TYPE_INPUT_METHOD && !win.getGivenInsetsPendingLw()) {
            int top = win.getContentFrameLw().top;
            top += win.getGivenContentInsetsLw().top;
            if (mContentBottom > top) {
                mContentBottom = top;
            }
            top = win.getVisibleFrameLw().top;
            top += win.getGivenVisibleInsetsLw().top;
            if (mCurBottom > top) {
                mCurBottom = top;
            }
            if (DEBUG_LAYOUT) Log.v(TAG, "Input method: mDockBottom="
                    + mDockBottom + " mContentBottom="
                    + mContentBottom + " mCurBottom=" + mCurBottom);
        }
    }

    /** {@inheritDoc} */
    public int finishLayoutLw() {
        return 0;
    }

    /** {@inheritDoc} */
    public void beginAnimationLw(int displayWidth, int displayHeight) {
        mTopFullscreenOpaqueWindowState = null;
        mForceStatusBar = false;

        mHideLockScreen = false;
        mAllowLockscreenWhenOn = false;
        mDismissKeyguard = false;
    }

    /** {@inheritDoc} */
    public void animatingWindowLw(WindowState win,
                                WindowManager.LayoutParams attrs) {
        if (mTopFullscreenOpaqueWindowState == null &&
                win.isVisibleOrBehindKeyguardLw()) {
            if ((attrs.flags & FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                mForceStatusBar = true;
            }
            if (attrs.type >= FIRST_APPLICATION_WINDOW
                    && attrs.type <= LAST_APPLICATION_WINDOW
                    && attrs.x == 0 && attrs.y == 0
                    && attrs.width == WindowManager.LayoutParams.MATCH_PARENT
                    && attrs.height == WindowManager.LayoutParams.MATCH_PARENT) {
                if (DEBUG_LAYOUT) Log.v(TAG, "Fullscreen window: " + win);
                mTopFullscreenOpaqueWindowState = win;
                if ((attrs.flags & FLAG_SHOW_WHEN_LOCKED) != 0) {
                    if (localLOGV) Log.v(TAG, "Setting mHideLockScreen to true by win " + win);
                    mHideLockScreen = true;
                }
                if ((attrs.flags & FLAG_DISMISS_KEYGUARD) != 0) {
                    if (localLOGV) Log.v(TAG, "Setting mDismissKeyguard to true by win " + win);
                    mDismissKeyguard = true;
                }
                if ((attrs.flags & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON) != 0) {
                    mAllowLockscreenWhenOn = true;
                }
            }
        }
    }

    /** {@inheritDoc} */
    public int finishAnimationLw() {
        int changes = 0;

        boolean hiding = false;
        if (mStatusBar != null) {
            if (localLOGV) Log.i(TAG, "force=" + mForceStatusBar
                    + " top=" + mTopFullscreenOpaqueWindowState);
            if (mForceStatusBar) {
                if (DEBUG_LAYOUT) Log.v(TAG, "Showing status bar");
                if (mStatusBar.showLw(true)) changes |= FINISH_LAYOUT_REDO_LAYOUT;
            } else if (mTopFullscreenOpaqueWindowState != null) {
                //Log.i(TAG, "frame: " + mTopFullscreenOpaqueWindowState.getFrameLw()
                //        + " shown frame: " + mTopFullscreenOpaqueWindowState.getShownFrameLw());
                //Log.i(TAG, "attr: " + mTopFullscreenOpaqueWindowState.getAttrs());
                WindowManager.LayoutParams lp =
                    mTopFullscreenOpaqueWindowState.getAttrs();
                boolean hideStatusBar =
                    (lp.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
                if (hideStatusBar) {
                    if (DEBUG_LAYOUT) Log.v(TAG, "Hiding status bar");
                    if (mStatusBar.hideLw(true)) changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    hiding = true;
                } else {
                    if (DEBUG_LAYOUT) Log.v(TAG, "Showing status bar");
                    if (mStatusBar.showLw(true)) changes |= FINISH_LAYOUT_REDO_LAYOUT;
                }
            }
        }

        if (changes != 0 && hiding) {
            IStatusBarService sbs = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
            if (sbs != null) {
                try {
                    // Make sure the window shade is hidden.
                    sbs.collapse();
                } catch (RemoteException e) {
                }
            }
        }

        // Hide the key guard if a visible window explicitly specifies that it wants to be displayed
        // when the screen is locked
        if (mKeyguard != null) {
            if (localLOGV) Log.v(TAG, "finishLayoutLw::mHideKeyguard="+mHideLockScreen);
            if (mDismissKeyguard && !mKeyguardMediator.isSecure()) {
                if (mKeyguard.hideLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
                if (mKeyguardMediator.isShowing()) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            mKeyguardMediator.keyguardDone(false, false);
                        }
                    });
                }
            } else if (mHideLockScreen) {
                if (mKeyguard.hideLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
                mKeyguardMediator.setHidden(true);
            } else {
                if (mKeyguard.showLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
                mKeyguardMediator.setHidden(false);
            }
        }

        // update since mAllowLockscreenWhenOn might have changed
        updateLockScreenTimeout();
        return changes;
    }

    public boolean allowAppAnimationsLw() {
        if (mKeyguard != null && mKeyguard.isVisibleLw()) {
            // If keyguard is currently visible, no reason to animate
            // behind it.
            return false;
        }
        if (mStatusBar != null && mStatusBar.isVisibleLw()) {
            Rect rect = new Rect(mStatusBar.getShownFrameLw());
            for (int i=mStatusBarPanels.size()-1; i>=0; i--) {
                WindowState w = mStatusBarPanels.get(i);
                if (w.isVisibleLw()) {
                    rect.union(w.getShownFrameLw());
                }
            }
            final int insetw = mW/10;
            final int inseth = mH/10;
            if (rect.contains(insetw, inseth, mW-insetw, mH-inseth)) {
                // All of the status bar windows put together cover the
                // screen, so the app can't be seen.  (Note this test doesn't
                // work if the rects of these windows are at off offsets or
                // sizes, causing gaps in the rect union we have computed.)
                return false;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        // lid changed state
        mLidOpen = lidOpen;
        boolean awakeNow = mKeyguardMediator.doLidChangeTq(mLidOpen);
        updateRotation(Surface.FLAGS_ORIENTATION_ANIMATION_DISABLE);
        if (awakeNow) {
            // If the lid opening and we don't have to keep the
            // keyguard up, then we can turn on the screen
            // immediately.
            mKeyguardMediator.pokeWakelock();
        } else if (keyguardIsShowingTq()) {
            if (mLidOpen) {
                // If we are opening the lid and not hiding the
                // keyguard, then we need to have it turn on the
                // screen once it is shown.
                mKeyguardMediator.onWakeKeyWhenKeyguardShowingTq(
                        KeyEvent.KEYCODE_POWER);
            }
        } else {
            // Light up the keyboard if we are sliding up.
            if (mLidOpen) {
                mPowerManager.userActivity(SystemClock.uptimeMillis(), false,
                        LocalPowerManager.BUTTON_EVENT);
            } else {
                mPowerManager.userActivity(SystemClock.uptimeMillis(), false,
                        LocalPowerManager.OTHER_EVENT);
            }
        }
    }

    /**
     * @return Whether music is being played right now.
     */
    boolean isMusicActive() {
        final AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            Log.w(TAG, "isMusicActive: couldn't get AudioManager reference");
            return false;
        }
        return am.isMusicActive();
    }

    /**
     * Tell the audio service to adjust the volume appropriate to the event.
     * @param keycode
     */
    void handleVolumeKey(int stream, int keycode) {
        IAudioService audioService = getAudioService();
        if (audioService == null) {
            return;
        }
        try {
            // since audio is playing, we shouldn't have to hold a wake lock
            // during the call, but we do it as a precaution for the rare possibility
            // that the music stops right before we call this
            mBroadcastWakeLock.acquire();

            audioService.adjustStreamVolume(stream,
                keycode == KeyEvent.KEYCODE_VOLUME_UP
                            ? AudioManager.ADJUST_RAISE
                            : AudioManager.ADJUST_LOWER,
                    0);
        } catch (RemoteException e) {
            Log.w(TAG, "IAudioService.adjustStreamVolume() threw RemoteException " + e);
        } finally {
            mBroadcastWakeLock.release();
        }
    }

    void handleVolumeLongPress(int keycode) {
        Runnable btnHandler;

        if (keycode == KeyEvent.KEYCODE_VOLUME_UP)
            btnHandler = mVolumeUpLongPress;
        else
            btnHandler = mVolumeDownLongPress;

        mHandler.postDelayed(btnHandler, ViewConfiguration.getLongPressTimeout());
    }

    void handleVolumeLongPressAbort() {
        mHandler.removeCallbacks(mVolumeUpLongPress);
        mHandler.removeCallbacks(mVolumeDownLongPress);
    }

    void handleCameraKeyDown() {
        if (mCamBtnMusicControls) {
            // if the camera key is not pressable, see if music is active
            if (!mCameraKeyPressable) {
                mCameraKeyPressable = isMusicActive();
            }

            if (mCameraKeyPressable) {
                mHandler.postDelayed(mCameraLongPress, ViewConfiguration.getLongPressTimeout());
            }
        }
    }

    void handleCameraKeyUp() {
        mHandler.removeCallbacks(mCameraLongPress);
    }

    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(long whenNanos, int action, int flags,
            int keyCode, int scanCode, int policyFlags, boolean isScreenOn) {
        final boolean down = action == KeyEvent.ACTION_DOWN;
        final boolean canceled = (flags & KeyEvent.FLAG_CANCELED) != 0;

        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        // If screen is off then we treat the case where the keyguard is open but hidden
        // the same as if it were open and in front.
        // This will prevent any keys other than the power button from waking the screen
        // when the keyguard is hidden by another activity.
        final boolean keyguardActive = (isScreenOn ?
                                        mKeyguardMediator.isShowingAndNotHidden() :
                                        mKeyguardMediator.isShowing());

        if (false) {
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                  + " screenIsOn=" + isScreenOn + " keyguardActive=" + keyguardActive);
        }

        if (down && (policyFlags & WindowManagerPolicy.FLAG_VIRTUAL) != 0) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
        } else if ((policyFlags & WindowManagerPolicy.FLAG_VIRTUAL) != 0) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_RELEASED, false);
        }

        // Basic policy based on screen state and keyguard.
        // FIXME: This policy isn't quite correct.  We shouldn't care whether the screen
        //        is on or off, really.  We should care about whether the device is in an
        //        interactive state or is in suspend pretending to be "off".
        //        The primary screen might be turned off due to proximity sensor or
        //        because we are presenting media on an auxiliary screen or remotely controlling
        //        the device some other way (which is why we have an exemption here for injected
        //        events).
        int result;
        if (isScreenOn || isInjected) {
            // When the screen is on or if the key is injected pass the key to the application.
            result = ACTION_PASS_TO_USER;
        } else {
            // When the screen is off and the key is not injected, determine whether
            // to wake the device but don't pass the key to the application.
            result = 0;

            boolean isWakeKey = (policyFlags
                    & (WindowManagerPolicy.FLAG_WAKE | WindowManagerPolicy.FLAG_WAKE_DROPPED)) != 0
                    || ((keyCode == BTN_MOUSE) && mTrackballWakeScreen)
                    || ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) && mVolumeWakeScreen)
                    || ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && mVolumeWakeScreen);

            // Don't wake the screen if we have not set the option "wake with volume" in CMParts
            // OR if "wake with volume" is set but screen is off due to proximity sensor
            // regardless if WAKE Flag is set in keylayout
            final boolean isOffByProx = (mScreenOffReason == WindowManagerPolicy.OFF_BECAUSE_OF_PROX_SENSOR);
            if (isWakeKey
                    && (!mVolumeWakeScreen || isOffByProx)
                    && ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))) {
                isWakeKey = false;
            }

            // make sure keyevent get's handled as power key on volume-wake
            if(mVolumeWakeScreen && isWakeKey && ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)))
                keyCode = KeyEvent.KEYCODE_POWER;

            if (down && isWakeKey) {
                if (keyguardActive) {
                    // If the keyguard is showing, let it decide what to do with the wake key.
                    mKeyguardMediator.onWakeKeyWhenKeyguardShowingTq(keyCode);
                } else {
                    // Otherwise, wake the device ourselves.
                    result |= ACTION_POKE_USER_ACTIVITY;
                }
            }
        }

        // Handle special keys.
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP: {
                // cm71 nightlies: will be replaced by CmPhoneWindowManager's new volume handling
                if(mVolBtnMusicControls && !down)
                {
                    handleVolumeLongPressAbort();

                    // delay handling volume events if mVolBtnMusicControls is desired
                    if (isMusicActive() && !mIsLongPress && (result & ACTION_PASS_TO_USER) == 0)
                        handleVolumeKey(AudioManager.STREAM_MUSIC, keyCode);
                }
                if (down) {
                    ITelephony telephonyService = getTelephonyService();
                    if (telephonyService != null) {
                        try {
                            if (telephonyService.isRinging()) {
                                // If an incoming call is ringing, either VOLUME key means
                                // "silence ringer".  We handle these keys here, rather than
                                // in the InCallScreen, to make sure we'll respond to them
                                // even if the InCallScreen hasn't come to the foreground yet.
                                // Look for the DOWN event here, to agree with the "fallback"
                                // behavior in the InCallScreen.
                                Log.i(TAG, "interceptKeyBeforeQueueing:"
                                      + " VOLUME key-down while ringing: Silence ringer!");

                                // Silence the ringer.  (It's safe to call this
                                // even if the ringer has already been silenced.)
                                telephonyService.silenceRinger();

                                // And *don't* pass this key thru to the current activity
                                // (which is probably the InCallScreen.)
                                result &= ~ACTION_PASS_TO_USER;
                                break;
                            }
                            if (telephonyService.isOffhook()
                                    && (result & ACTION_PASS_TO_USER) == 0) {
                                // If we are in call but we decided not to pass the key to
                                // the application, handle the volume change here.
                                handleVolumeKey(AudioManager.STREAM_VOICE_CALL, keyCode);
                                break;
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "ITelephony threw RemoteException", ex);
                        }
                    }

                    // cm71 nightlies: will be replaced by CmPhoneWindowManager's new volume handling
                    if (isMusicActive() && (result & ACTION_PASS_TO_USER) == 0) {
                        // Care for long-press actions to skip tracks
                        if(mVolBtnMusicControls) {
                            // initialize long press flag to false for volume events
                            mIsLongPress = false;

                            // if the button is held long enough, the following
                            // procedure will set mIsLongPress=true
                            handleVolumeLongPress(keyCode);
                        } else {
                            // If music is playing but we decided not to pass the key to the
                            // application, handle the volume change here.
                            handleVolumeKey(AudioManager.STREAM_MUSIC, keyCode);
                        }
                        break;
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_CAMERA: {
                if (down) {
                    handleCameraKeyDown();
                } else {
                    handleCameraKeyUp();
                }
                break;
            }

            case KeyEvent.KEYCODE_ENDCALL: {
                result &= ~ACTION_PASS_TO_USER;
                if (down) {
                    ITelephony telephonyService = getTelephonyService();
                    boolean hungUp = false;
                    if (telephonyService != null) {
                        try {
                            hungUp = telephonyService.endCall();
                        } catch (RemoteException ex) {
                            Log.w(TAG, "ITelephony threw RemoteException", ex);
                        }
                    }
                    interceptPowerKeyDown(!isScreenOn || hungUp);
                } else {
                    if (interceptPowerKeyUp(canceled)) {
                        if ((mEndcallBehavior
                                & Settings.System.END_BUTTON_BEHAVIOR_HOME) != 0) {
                            if (goHome()) {
                                break;
                            }
                        }
                        if ((mEndcallBehavior
                                & Settings.System.END_BUTTON_BEHAVIOR_SLEEP) != 0) {
                            result = (result & ~ACTION_POKE_USER_ACTIVITY) | ACTION_GO_TO_SLEEP;
                        }
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_POWER: {
                if (mTopFullscreenOpaqueWindowState != null &&
                        (mTopFullscreenOpaqueWindowState.getAttrs().flags
                        & WindowManager.LayoutParams.PREVENT_POWER_KEY) != 0) {
                    return result;
                }
                result &= ~ACTION_PASS_TO_USER;
                if (down) {
                    ITelephony telephonyService = getTelephonyService();
                    boolean hungUp = false;
                    if (telephonyService != null) {
                        try {
                            if (telephonyService.isRinging()) {
                                // Pressing Power while there's a ringing incoming
                                // call should silence the ringer.
                                telephonyService.silenceRinger();
                            } else if ((mIncallPowerBehavior
                                    & Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP) != 0
                                    && telephonyService.isOffhook()) {
                                // Otherwise, if "Power button ends call" is enabled,
                                // the Power button will hang up any current active call.
                                hungUp = telephonyService.endCall();
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "ITelephony threw RemoteException", ex);
                        }
                    }
                    interceptPowerKeyDown(!isScreenOn || hungUp);
                } else {
                    if (interceptPowerKeyUp(canceled)) {
                        result = (result & ~ACTION_POKE_USER_ACTIVITY) | ACTION_GO_TO_SLEEP;
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                if ((result & ACTION_PASS_TO_USER) == 0) {
                    // Only do this if we would otherwise not pass it to the user. In that
                    // case, the PhoneWindow class will do the same thing, except it will
                    // only do it if the showing app doesn't process the key on its own.
                    long when = whenNanos / 1000000;
                    KeyEvent keyEvent = new KeyEvent(when, when, action, keyCode, 0, 0,
                            0, scanCode, flags, InputDevice.SOURCE_KEYBOARD);
                    mBroadcastWakeLock.acquire();
                    mHandler.post(new PassHeadsetKey(keyEvent));
                }

                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                    if (!down) {
                        mHandler.removeCallbacks(mHsetHookLongPress);
                    }
                    else if ((result & ACTION_PASS_TO_USER) == 0) {
                        mHsetRepeats = 0;
                        mHandler.postDelayed(mHsetHookLongPress, ViewConfiguration.getLongPressTimeout());
                    }
                }

                break;
            }

            case KeyEvent.KEYCODE_CALL: {
                if (down) {
                    ITelephony telephonyService = getTelephonyService();
                    if (telephonyService != null) {
                        try {
                            if (telephonyService.isRinging()) {
                                Log.i(TAG, "interceptKeyBeforeQueueing:"
                                      + " CALL key-down while ringing: Answer the call!");
                                telephonyService.answerRingingCall();

                                // And *don't* pass this key thru to the current activity
                                // (which is presumably the InCallScreen.)
                                result &= ~ACTION_PASS_TO_USER;
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "ITelephony threw RemoteException", ex);
                        }
                    }
                }
                break;
            }
        }
        return result;
    }

    class PassHeadsetKey implements Runnable {
        KeyEvent mKeyEvent;

        PassHeadsetKey(KeyEvent keyEvent) {
            mKeyEvent = keyEvent;
        }

        public void run() {
            if (ActivityManagerNative.isSystemReady()) {
                Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
                intent.putExtra(Intent.EXTRA_KEY_EVENT, mKeyEvent);
                mContext.sendOrderedBroadcast(intent, null, mBroadcastDone,
                        mHandler, Activity.RESULT_OK, null, null);
            }
        }
    }

    BroadcastReceiver mBroadcastDone = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mBroadcastWakeLock.release();
        }
    };

    BroadcastReceiver mDockReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DOCK_EVENT.equals(intent.getAction())) {
                mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
            } else {
                try {
                    IUiModeManager uiModeService = IUiModeManager.Stub.asInterface(
                            ServiceManager.getService(Context.UI_MODE_SERVICE));
                    mUiMode = uiModeService.getCurrentModeType();
                } catch (RemoteException e) {
                }
            }
            updateRotation(Surface.FLAGS_ORIENTATION_ANIMATION_DISABLE);
            updateOrientationListenerLp();
        }
    };

    BroadcastReceiver mThemeChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mUiContext = null;
        }
    };

    /** {@inheritDoc} */
    public void screenTurnedOff(int why) {
        EventLog.writeEvent(70000, 0);
        mKeyguardMediator.onScreenTurnedOff(why);
        synchronized (mLock) {
            mScreenOn = false;
            mScreenOffReason = why;
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
    }

    /** {@inheritDoc} */
    public void screenTurnedOn() {
        EventLog.writeEvent(70000, 1);
        mKeyguardMediator.onScreenTurnedOn();
        synchronized (mLock) {
            mScreenOn = true;
            // since the screen turned on, assume we don't enable play-pause again
            // unless they turn it off and music is still playing.  this is done to
            // prevent the camera button from starting playback if playback wasn't
            // originally running
            mCameraKeyPressable = false;
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
    }

    /** {@inheritDoc} */
    public boolean isScreenOn() {
        return mScreenOn;
    }

    /** {@inheritDoc} */
    public void enableKeyguard(boolean enabled) {
        mKeyguardMediator.setKeyguardEnabled(enabled);
    }

    /** {@inheritDoc} */
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        mKeyguardMediator.verifyUnlock(callback);
    }

    private boolean keyguardIsShowingTq() {
        return mKeyguardMediator.isShowingAndNotHidden();
    }

    /** {@inheritDoc} */
    public boolean inKeyguardRestrictedKeyInputMode() {
        return mKeyguardMediator.isInputRestricted();
    }

    void sendCloseSystemWindows() {
        sendCloseSystemWindows(mContext, null);
    }

    void sendCloseSystemWindows(String reason) {
        sendCloseSystemWindows(mContext, reason);
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    public int rotationForOrientationLw(int orientation, int lastRotation,
            boolean displayEnabled) {

        if (mPortraitRotation < 0) {
            // Initialize the rotation angles for each orientation once.
            Display d = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            if (d.getWidth() > d.getHeight()) {
                mPortraitRotation = Surface.ROTATION_90;
                mLandscapeRotation = Surface.ROTATION_0;
                mUpsideDownRotation = Surface.ROTATION_270;
                mSeascapeRotation = Surface.ROTATION_180;
            } else {
                mPortraitRotation = Surface.ROTATION_0;
                mLandscapeRotation = Surface.ROTATION_90;
                mUpsideDownRotation = Surface.ROTATION_180;
                mSeascapeRotation = Surface.ROTATION_270;
            }
        }

        synchronized (mLock) {
            switch (orientation) {
                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                    //always return portrait if orientation set to portrait
                    return mPortraitRotation;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    //always return landscape if orientation set to landscape
                    return mLandscapeRotation;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    //always return portrait if orientation set to portrait
                    return mUpsideDownRotation;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    //always return seascape if orientation set to reverse landscape
                    return mSeascapeRotation;
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    //return either landscape rotation based on the sensor
                    mOrientationListener.setAllow180Rotation(
                            isLandscapeOrSeascape(Surface.ROTATION_180));
                    return getCurrentLandscapeRotation(lastRotation);
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                    mOrientationListener.setAllow180Rotation(
                            !isLandscapeOrSeascape(Surface.ROTATION_180));
                    return getCurrentPortraitRotation(lastRotation);
            }

            mOrientationListener.setAllow180Rotation(mAllowAllRotations ||
                    orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

            // case for nosensor meaning ignore sensor and consider only lid
            // or orientation sensor disabled
            //or case.unspecified
            if (mLidOpen) {
                return mLidOpenRotation;
            } else if (mDockMode == Intent.EXTRA_DOCK_STATE_CAR && mCarDockRotation >= 0) {
                return mCarDockRotation;
            } else if (mDockMode == Intent.EXTRA_DOCK_STATE_DESK && mDeskDockRotation >= 0) {
                return mDeskDockRotation;
            } else {
                if (useSensorForOrientationLp(orientation)) {
                    return mOrientationListener.getCurrentRotation(lastRotation);
                }
                return Surface.ROTATION_0;
            }
        }
    }

    private int getCurrentLandscapeRotation(int lastRotation) {
        int sensorRotation = mOrientationListener.getCurrentRotation(lastRotation);
        if (isLandscapeOrSeascape(sensorRotation)) {
            return sensorRotation;
        }
        // try to preserve the old rotation if it was landscape
        if (isLandscapeOrSeascape(lastRotation)) {
            return lastRotation;
        }
        // default to one of the primary landscape rotation
        return mLandscapeRotation;
    }

    private boolean isLandscapeOrSeascape(int sensorRotation) {
        return sensorRotation == mLandscapeRotation || sensorRotation == mSeascapeRotation;
    }

    private int getCurrentPortraitRotation(int lastRotation) {
        int sensorRotation = mOrientationListener.getCurrentRotation(lastRotation);
        if (isAnyPortrait(sensorRotation)) {
            return sensorRotation;
        }
        // try to preserve the old rotation if it was portrait
        if (isAnyPortrait(lastRotation)) {
            return lastRotation;
        }
        // default to one of the primary portrait rotations
        return mPortraitRotation;
    }

    private boolean isAnyPortrait(int sensorRotation) {
        return sensorRotation == mPortraitRotation || sensorRotation == mUpsideDownRotation;
    }

    public boolean detectSafeMode() {
        try {
            int menuState = mWindowManager.getKeycodeState(KeyEvent.KEYCODE_MENU);
            int sState = mWindowManager.getKeycodeState(KeyEvent.KEYCODE_S);
            int dpadState = mWindowManager.getDPadKeycodeState(KeyEvent.KEYCODE_DPAD_CENTER);
            int trackballState = mWindowManager.getTrackballScancodeState(BTN_MOUSE);
            mSafeMode = menuState > 0 || sState > 0 || dpadState > 0 || trackballState > 0;
            performHapticFeedbackLw(null, mSafeMode
                    ? HapticFeedbackConstants.SAFE_MODE_ENABLED
                    : HapticFeedbackConstants.SAFE_MODE_DISABLED, true);
            if (mSafeMode) {
                Log.i(TAG, "SAFE MODE ENABLED (menu=" + menuState + " s=" + sState
                        + " dpad=" + dpadState + " trackball=" + trackballState + ")");
            } else {
                Log.i(TAG, "SAFE MODE not enabled");
            }
            return mSafeMode;
        } catch (RemoteException e) {
            // Doom! (it's also local)
            throw new RuntimeException("window manager dead");
        }
    }

    static long[] getLongIntArray(Resources r, int resid) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return null;
        }
        long[] out = new long[ar.length];
        for (int i=0; i<ar.length; i++) {
            out[i] = ar[i];
        }
        return out;
    }

    /** {@inheritDoc} */
    public void systemReady() {
        // tell the keyguard
        mKeyguardMediator.onSystemReady();
        android.os.SystemProperties.set("dev.bootcomplete", "1");
        synchronized (mLock) {
            updateOrientationListenerLp();
            mSystemReady = true;
            mHandler.post(new Runnable() {
                public void run() {
                    updateSettings();
                }
            });
        }
    }

    /** {@inheritDoc} */
    public void userActivity() {
        synchronized (mScreenLockTimeout) {
            if (mLockScreenTimerActive) {
                // reset the timer
                mHandler.removeCallbacks(mScreenLockTimeout);
                mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
            }
        }
    }

    Runnable mScreenLockTimeout = new Runnable() {
        public void run() {
            synchronized (this) {
                if (localLOGV) Log.v(TAG, "mScreenLockTimeout activating keyguard");
                mKeyguardMediator.doKeyguardTimeout();
                mLockScreenTimerActive = false;
            }
        }
    };

    private void updateLockScreenTimeout() {
        synchronized (mScreenLockTimeout) {
            boolean enable = (mAllowLockscreenWhenOn && mScreenOn && mKeyguardMediator.isSecure());
            if (mLockScreenTimerActive != enable) {
                if (enable) {
                    if (localLOGV) Log.v(TAG, "setting lockscreen timer");
                    mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
                } else {
                    if (localLOGV) Log.v(TAG, "clearing lockscreen timer");
                    mHandler.removeCallbacks(mScreenLockTimeout);
                }
                mLockScreenTimerActive = enable;
            }
        }
    }

    /** {@inheritDoc} */
    public void enableScreenAfterBoot() {
        readLidState();
        updateRotation(Surface.FLAGS_ORIENTATION_ANIMATION_DISABLE);
    }

    void updateRotation(int animFlags) {
        mPowerManager.setKeyboardVisibility(mLidOpen);
        int rotation = Surface.ROTATION_0;
        if (mLidOpen) {
            rotation = mLidOpenRotation;
        } else if (mDockMode == Intent.EXTRA_DOCK_STATE_CAR && mCarDockRotation >= 0) {
            rotation = mCarDockRotation;
        } else if (mDockMode == Intent.EXTRA_DOCK_STATE_DESK && mDeskDockRotation >= 0) {
            rotation = mDeskDockRotation;
        }
        //if lid is closed orientation will be portrait
        try {
            //set orientation on WindowManager
            mWindowManager.setRotation(rotation, true,
                    mFancyRotationAnimation | animFlags);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    /**
     * Return an Intent to launch the currently active dock as home.  Returns
     * null if the standard home should be launched.
     * @return
     */
    Intent createHomeDockIntent() {
        Intent intent;

        // What home does is based on the mode, not the dock state.  That
        // is, when in car mode you should be taken to car home regardless
        // of whether we are actually in a car dock.
        if (mUiMode == Configuration.UI_MODE_TYPE_CAR) {
            intent = mCarDockIntent;
        } else if (mUiMode == Configuration.UI_MODE_TYPE_DESK) {
            intent = mDeskDockIntent;
        } else {
            return null;
        }

        ActivityInfo ai = intent.resolveActivityInfo(
                mContext.getPackageManager(), PackageManager.GET_META_DATA);
        if (ai == null) {
            return null;
        }

        if (ai.metaData != null && ai.metaData.getBoolean(Intent.METADATA_DOCK_HOME)) {
            intent = new Intent(intent);
            intent.setClassName(ai.packageName, ai.name);
            return intent;
        }

        return null;
    }

    void startDockOrHome() {
        Intent dock = createHomeDockIntent();
        if (dock != null) {
            try {
                mContext.startActivity(dock);
                return;
            } catch (ActivityNotFoundException e) {
            }
        }
        mContext.startActivity(mHomeIntent);
    }

    /**
     * goes to the home screen
     * @return whether it did anything
     */
    boolean goHome() {
        if (false) {
            // This code always brings home to the front.
            try {
                ActivityManagerNative.getDefault().stopAppSwitches();
            } catch (RemoteException e) {
            }
            sendCloseSystemWindows();
            startDockOrHome();
        } else {
            // This code brings home to the front or, if it is already
            // at the front, puts the device to sleep.
            try {
                if (SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1) {
                    /// Roll back EndcallBehavior as the cupcake design to pass P1 lab entry.
                    Log.d(TAG, "UTS-TEST-MODE");
                } else {
                    ActivityManagerNative.getDefault().stopAppSwitches();
                    sendCloseSystemWindows();
                    Intent dock = createHomeDockIntent();
                    if (dock != null) {
                        int result = ActivityManagerNative.getDefault()
                                .startActivity(null, dock,
                                        dock.resolveTypeIfNeeded(mContext.getContentResolver()),
                                        null, 0, null, null, 0, true /* onlyIfNeeded*/, false);
                        if (result == IActivityManager.START_RETURN_INTENT_TO_CALLER) {
                            return false;
                        }
                    }
                }
                int result = ActivityManagerNative.getDefault()
                        .startActivity(null, mHomeIntent,
                                mHomeIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                                null, 0, null, null, 0, true /* onlyIfNeeded*/, false);
                if (result == IActivityManager.START_RETURN_INTENT_TO_CALLER) {
                    return false;
                }
            } catch (RemoteException ex) {
                // bummer, the activity manager, which is in this process, is dead
            }
        }
        return true;
    }

    public void setCurrentOrientationLw(int newOrientation) {
        synchronized (mLock) {
            if (newOrientation != mCurrentAppOrientation) {
                mCurrentAppOrientation = newOrientation;
                updateOrientationListenerLp();
            }
        }
    }

    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        final boolean hapticsDisabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0;
        if (!always && (hapticsDisabled || mKeyguardMediator.isShowingAndNotHidden())) {
            return false;
        }
        long[] pattern = null;
        switch (effectId) {
            case HapticFeedbackConstants.LONG_PRESS:
                pattern = mLongPressVibePattern;
                break;
            case HapticFeedbackConstants.VIRTUAL_KEY:
                pattern = mVirtualKeyVibePattern;
                break;
            case HapticFeedbackConstants.KEYBOARD_TAP:
                pattern = mKeyboardTapVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_DISABLED:
                pattern = mSafeModeDisabledVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_ENABLED:
                pattern = mSafeModeEnabledVibePattern;
                break;
            case HapticFeedbackConstants.VIRTUAL_RELEASED:
                final boolean upDisabled = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.HAPTIC_FEEDBACK_UP_ENABLED, 0) == 0;
                if (upDisabled != true) {
                    pattern = mVirtualKeyUpVibePattern;
                    break;
                } else {
                    return false;
                }

            default:
                return false;
        }
        if (pattern.length == 1) {
            // One-shot vibration
            mVibrator.vibrate(pattern[0]);
        } else {
            // Pattern vibration
            mVibrator.vibrate(pattern, -1);
        }
        return true;
    }

    public void screenOnStoppedLw() {
        if (!mKeyguardMediator.isShowingAndNotHidden() && mPowerManager.isScreenOn()) {
            long curTime = SystemClock.uptimeMillis();
            mPowerManager.userActivity(curTime, false, LocalPowerManager.OTHER_EVENT);
        }
    }

    public boolean allowKeyRepeat() {
        // disable key repeat when screen is off
        return mScreenOn;
    }

    /*
     * mtwebster - Added functions for allowing adjustable haptic feedback in
     * certain global areas of phone
     */
    long[] hapLoaded; // the end result we want saved haptic value from settings.system
    long[] hapDefault; // pull default haptic value from xml (probably from device overlay)
    String hapString; // pull saved value from system.settings
    String hapTypeString;
    String hapDefTypeString;
    boolean hapDone = false;

    private long[] loadHaptic(int hapType) {
        switch (hapType) {
            case HapticFeedbackConstants.VIRTUAL_KEY:
                hapDone = false;
                hapTypeString = Settings.System.HAPTIC_DOWN_ARRAY;
                hapDefTypeString = Settings.System.HAPTIC_DOWN_ARRAY_DEFAULT;
                Log.i(TAG, "names for haptic settings for haptic down array: " + hapTypeString
                        + " default: " + hapDefTypeString);
                hapString = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.HAPTIC_DOWN_ARRAY);
                hapDefault = getLongIntArray(mContext.getResources(),
                        com.android.internal.R.array.config_virtualKeyVibePattern);
                Log.i(TAG, "pulled string: " + hapString + " default: "
                        + longArrayToString(hapDefault));
                if (hapString != null) {
                    hapLoaded = stringToLongArray(hapString);
                    hapDone = true;
                    Log.i(TAG, "haptic done for down array!");
                }
                break;
            case HapticFeedbackConstants.VIRTUAL_RELEASED:
                hapDone = false;
                hapTypeString = Settings.System.HAPTIC_UP_ARRAY;
                hapDefTypeString = Settings.System.HAPTIC_UP_ARRAY_DEFAULT;
                Log.i(TAG, "names for haptic settings for haptic up array: " + hapTypeString
                        + " default: " + hapDefTypeString);
                hapString = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.HAPTIC_UP_ARRAY);
                hapDefault = getLongIntArray(mContext.getResources(),
                        com.android.internal.R.array.config_virtualKeyUpPattern);
                Log.i(TAG, "pulled string: " + hapString + " default: "
                        + longArrayToString(hapDefault));
                if (hapString != null) {
                    hapLoaded = stringToLongArray(hapString);
                    hapDone = true;
                    Log.i(TAG, "haptic done for up array!");
                }
                break;
            case HapticFeedbackConstants.LONG_PRESS:
                hapDone = false;
                hapTypeString = Settings.System.HAPTIC_LONG_ARRAY;
                hapDefTypeString = Settings.System.HAPTIC_LONG_ARRAY_DEFAULT;
                Log.i(TAG, "names for haptic settings for haptic long array: " + hapTypeString
                        + " default: " + hapDefTypeString);
                hapString = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.HAPTIC_LONG_ARRAY);
                hapDefault = getLongIntArray(mContext.getResources(),
                        com.android.internal.R.array.config_longPressVibePattern);
                Log.i(TAG, "pulled string: " + hapString + " default: "
                        + longArrayToString(hapDefault));
                if (hapString != null) {
                    hapLoaded = stringToLongArray(hapString);
                    hapDone = true;
                    Log.i(TAG, "haptic done for long array!");
                }
                break;
            case HapticFeedbackConstants.KEYBOARD_TAP:
                hapDone = false;
                hapTypeString = Settings.System.HAPTIC_TAP_ARRAY;
                hapDefTypeString = Settings.System.HAPTIC_TAP_ARRAY_DEFAULT;
                Log.i(TAG, "names for haptic settings for haptic tap array: " + hapTypeString
                        + " default: " + hapDefTypeString);
                hapString = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.HAPTIC_TAP_ARRAY);
                hapDefault = getLongIntArray(mContext.getResources(),
                        com.android.internal.R.array.config_keyboardTapVibePattern);
                Log.i(TAG, "pulled string: " + hapString + " default: "
                        + longArrayToString(hapDefault));
                if (hapString != null) {
                    hapLoaded = stringToLongArray(hapString);
                    hapDone = true;
                    Log.i(TAG, "haptic done for tap array!");
                }
                break;
            default:
                hapString = null;
                hapDefault = null;
        }
        if (hapDone != true) {
            hapString = longArrayToString(hapDefault);
            saveHapticToSettings(hapTypeString, hapDefTypeString, hapString);
            hapLoaded = hapDefault;
            hapDone = true;
        }
        Settings.System.putString(mContext.getContentResolver(), hapDefTypeString,
                longArrayToString(hapDefault));
        return hapLoaded;
    }

    /**
     * saveHapticToSettings should only be run on first boot of system - it
     * saves the config.xml values to system settings, in both 'current'
     * settings and 'default' settings - so the user can always go back to stock
     * it also turns off the 2 additional haptic settings by default
     *
     * @hide
     */
    private void saveHapticToSettings(String hapType, String hapDefType, String defString) {
        Log.i(TAG, "Had to load default haptic settings for: " + hapType + " saving " + defString
                + "to settings");
        Settings.System.putString(mContext.getContentResolver(), hapType, defString);
    }

    private long[] stringToLongArray(String inpString) {
        if (inpString == null) {
            long[] returnLong = new long[1];
            returnLong[0] = 0;
            return returnLong;
        }
        String[] splitStr = inpString.split(",");
        int los = splitStr.length;
        long[] returnLong = new long[los];
        int i;
        for (i = 0; i < los; i++) {
            returnLong[i] = Long.parseLong(splitStr[i].trim());
        }
        return returnLong;
    }

    private String longArrayToString(long[] inpLong) {
        int lol = inpLong.length;
        String workString = "";
        int i;
        for (i = 0; i < lol; i++) {
            if (i > 0) {
                workString = workString + ",";
            }
            workString = workString + String.valueOf(inpLong[i]);
        }
        return workString;
    }

}
