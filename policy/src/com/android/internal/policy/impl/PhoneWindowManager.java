/*
 * File modifications copyright (C) 2012 The CyanogenMod Project
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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.IUiModeManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.LocalPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.Vibrator;
import android.provider.Settings;

import com.android.internal.R;
import com.android.internal.app.ThemeUtils;
import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.policy.PolicyManager;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.telephony.ITelephony;
import com.android.internal.widget.PointerLocationView;

import dalvik.system.DexClassLoader;

import android.service.dreams.IDreamManager;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyCharacterMap;
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
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
import static android.view.WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_DRAG;
import static android.view.WindowManager.LayoutParams.TYPE_DREAM;
import static android.view.WindowManager.LayoutParams.TYPE_HIDDEN_NAV_CONSUMER;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_SEARCH_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.view.WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.view.WindowManager.LayoutParams.TYPE_POINTER;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_BOOT_PROGRESS;
import android.view.WindowManagerImpl;
import android.view.WindowManagerPolicy;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_OPEN;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_CLOSED;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.VolumePanel;

import android.widget.Toast;
import android.media.IAudioService;
import android.media.AudioService;
import android.media.AudioManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
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
    static final boolean localLOGV = false;
    static final boolean DEBUG_LAYOUT = false;
    static final boolean DEBUG_INPUT = false;
    static final boolean DEBUG_STARTING_WINDOW = false;
    static final boolean SHOW_STARTING_ANIMATIONS = true;
    static final boolean SHOW_PROCESSES_ON_ALT_MENU = false;

    // Whether to allow dock apps with METADATA_DOCK_HOME to temporarily take over the Home key.
    // No longer recommended for desk docks; still useful in car docks.
    static final boolean ENABLE_CAR_DOCK_HOME_CAPTURE = true;
    static final boolean ENABLE_DESK_DOCK_HOME_CAPTURE = false;

    // Should screen savers use their own timeout, or the SCREEN_OFF_TIMEOUT?
    static final boolean SEPARATE_TIMEOUT_FOR_SCREEN_SAVER = false;

    static final int LONG_PRESS_POWER_NOTHING = 0;
    static final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
    static final int LONG_PRESS_POWER_SHUT_OFF = 2;

    // Available custom actions to perform on a key press.
    // Must match values for KEY_HOME_LONG_PRESS_ACTION in:
    // core/java/android/provider/Settings.java
    private static final int KEY_ACTION_NOTHING = 0;
    private static final int KEY_ACTION_MENU = 1;
    private static final int KEY_ACTION_APP_SWITCH = 2;
    private static final int KEY_ACTION_SEARCH = 3;
    private static final int KEY_ACTION_VOICE_SEARCH = 4;
    private static final int KEY_ACTION_IN_APP_SEARCH = 5;

    // Masks for checking presence of hardware keys.
    // Must match values in core/res/res/values/config.xml
    private static final int KEY_MASK_HOME = 0x01;
    private static final int KEY_MASK_BACK = 0x02;
    private static final int KEY_MASK_MENU = 0x04;
    private static final int KEY_MASK_ASSIST = 0x08;
    private static final int KEY_MASK_APP_SWITCH = 0x10;

    // wallpaper is at the bottom, though the window manager may move it.
    static final int WALLPAPER_LAYER = 2;
    static final int APPLICATION_LAYER = 2;
    static final int PHONE_LAYER = 3;
    static final int SEARCH_BAR_LAYER = 4;
    static final int SYSTEM_DIALOG_LAYER = 5;
    // toasts and the plugged-in battery thing
    static final int TOAST_LAYER = 6;
    // SIM errors and unlock.  Not sure if this really should be in a high layer.
    static final int PRIORITY_PHONE_LAYER = 7;
    // like the ANR / app crashed dialogs
    static final int SYSTEM_ALERT_LAYER = 8;
    // on-screen keyboards and other such input method user interfaces go here.
    static final int INPUT_METHOD_LAYER = 9;
    // on-screen keyboards and other such input method user interfaces go here.
    static final int INPUT_METHOD_DIALOG_LAYER = 10;
    // the keyguard; nothing on top of these can take focus, since they are
    // responsible for power management when displayed.
    static final int KEYGUARD_LAYER = 11;
    static final int KEYGUARD_DIALOG_LAYER = 12;
    // used for Dreams (screensavers with TYPE_DREAM windows)
    static final int SCREENSAVER_LAYER = 13; 
    static final int STATUS_BAR_SUB_PANEL_LAYER = 14;
    static final int STATUS_BAR_LAYER = 15;
    static final int STATUS_BAR_PANEL_LAYER = 16;
    // the on-screen volume indicator and controller shown when the user
    // changes the device volume
    static final int VOLUME_OVERLAY_LAYER = 17;
    // things in here CAN NOT take focus, but are shown on top of everything else.
    static final int SYSTEM_OVERLAY_LAYER = 18;
    // the navigation bar, if available, shows atop most things
    static final int NAVIGATION_BAR_LAYER = 19;
    // some panels (e.g. search) need to show on top of the navigation bar
    static final int NAVIGATION_BAR_PANEL_LAYER = 20;
    // system-level error dialogs
    static final int SYSTEM_ERROR_LAYER = 21;
    // the drag layer: input for drag-and-drop is associated with this window,
    // which sits above all other focusable windows
    static final int DRAG_LAYER = 22;
    static final int SECURE_SYSTEM_OVERLAY_LAYER = 23;
    static final int BOOT_PROGRESS_LAYER = 24;
    // the (mouse) pointer layer
    static final int POINTER_LAYER = 25;
    static final int HIDDEN_NAV_CONSUMER_LAYER = 26;

    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;
    
    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    static public final String SYSTEM_DIALOG_REASON_ASSIST = "assist";

    // Useful scan codes.
    //private static final int SW_LID = 0x00;
    private static final int SW_HEADPHONE_INSERT = 0x02;
    private static final int SW_MICROPHONE_INSERT = 0x04;
    //ToDo: This needs to be revisted once the kernel has support to report ANC event
    private static final int SW_ANC_INSERT = 0x08;
    private static final int SW_HEADSET_INSERT = (SW_HEADPHONE_INSERT|SW_MICROPHONE_INSERT);
    private static final int SW_ANC_HEADPHONE_INSERT = 0x10;
    private static final int SW_ANC_MICROPHONE_INSERT = 0x20;
    private static final int SW_ANC_HEADSET_INSERT = (SW_ANC_HEADPHONE_INSERT|SW_ANC_MICROPHONE_INSERT);
    private static final int BTN_MOUSE = 0x110;

    // Useful HeadSet codes... Same definition as WireAccessoryObserver.java
    private static int mHeadsetJackState = 0;
    private static boolean mIsAncOn = false;
    private static final int BIT_HEADSET = (1 << 0);
    private static final int BIT_HEADSET_SPEAKER_ONLY = (1 << 1);
    //Useful Headset codes... QC added definitions
    private static final int BIT_HEADSET_MIC_ONLY = (1 << 2);
    private static final int BIT_ANC_HEADSET = (1 << 3);
    private static final int BIT_ANC_HEADSET_SPEAKER_ONLY = (1 << 4);
    private static final int BIT_ANC_HEADSET_MIC_ONLY = (1 << 5);
    private static final int SUPPORTED_HEADSETS = (BIT_HEADSET|BIT_HEADSET_SPEAKER_ONLY|BIT_HEADSET_MIC_ONLY |
                                                   BIT_ANC_HEADSET|BIT_ANC_HEADSET_SPEAKER_ONLY|BIT_ANC_HEADSET_MIC_ONLY);

    private static final int BIT_HEADSET_NO_MIC = BIT_HEADSET_SPEAKER_ONLY;
    private static final int BIT_ANC_HEADSET_NO_MIC = BIT_ANC_HEADSET_SPEAKER_ONLY;
    private static final int ANC_HEADSETS_WITH_MIC = BIT_ANC_HEADSET;
    private static final int HEADSETS_WITH_MIC = BIT_HEADSET;

    private String mHeadsetName = "Headset";
    private int mPrevHeadsetState = 0;
    private int mCurHeadsetState = 0;
    private boolean mHeadsetIntent = false;
    private final Object mHeadsetLock = new Object();

    /**
     * These are the system UI flags that, when changing, can cause the layout
     * of the screen to change.
     */
    static final int SYSTEM_UI_CHANGING_LAYOUT =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;

    /* Table of Application Launch keys.  Maps from key codes to intent categories.
     *
     * These are special keys that are used to launch particular kinds of applications,
     * such as a web browser.  HID defines nearly a hundred of them in the Consumer (0x0C)
     * usage page.  We don't support quite that many yet...
     */
    static SparseArray<String> sApplicationLaunchKeyCategories;
    static {
        sApplicationLaunchKeyCategories = new SparseArray<String>();
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_EXPLORER, Intent.CATEGORY_APP_BROWSER);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_ENVELOPE, Intent.CATEGORY_APP_EMAIL);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CONTACTS, Intent.CATEGORY_APP_CONTACTS);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALENDAR, Intent.CATEGORY_APP_CALENDAR);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_MUSIC, Intent.CATEGORY_APP_MUSIC);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALCULATOR, Intent.CATEGORY_APP_CALCULATOR);
    }

    DeviceKeyHandler mDeviceKeyHandler;

    /**
     * Lock protecting internal state.  Must not call out into window
     * manager with lock held.  (This lock will be acquired in places
     * where the window manager is calling in with its own lock held.)
     */
    final Object mLock = new Object();

    Context mContext;
    Context mUiContext;
    IWindowManager mWindowManager;
    WindowManagerFuncs mWindowManagerFuncs;
    LocalPowerManager mPowerManager;
    IStatusBarService mStatusBarService;
    final Object mServiceAquireLock = new Object();
    Vibrator mVibrator; // Vibrator for giving feedback of orientation changes
    SearchManager mSearchManager;

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

    /** If true, hitting shift & menu will broadcast Intent.ACTION_BUG_REPORT */
    boolean mEnableShiftMenuBugReports = false;

    boolean mHeadless;
    boolean mSafeMode;
    WindowState mStatusBar = null;
    boolean mHasSystemNavBar;
    int mStatusBarHeight;
    WindowState mNavigationBar = null;
    boolean mHasNavigationBar = false;
    boolean mCanHideNavigationBar = false;
    boolean mNavigationBarCanMove = false; // can the navigation bar ever move to the side?
    boolean mNavigationBarOnBottom = true; // is the navigation bar on the bottom *right now*?
    int[] mNavigationBarHeightForRotation = new int[4];
    int[] mNavigationBarWidthForRotation = new int[4];

    WindowState mKeyguard = null;
    KeyguardViewMediator mKeyguardMediator;
    GlobalActions mGlobalActions;
    volatile boolean mPowerKeyHandled; // accessed from input reader and handler thread
    boolean mPendingPowerKeyUpCanceled;
    Handler mHandler;
    WindowState mLastInputMethodWindow = null;
    WindowState mLastInputMethodTargetWindow = null;

    static final int RECENT_APPS_BEHAVIOR_SHOW_OR_DISMISS = 0;
    static final int RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW = 1;
    static final int RECENT_APPS_BEHAVIOR_DISMISS = 2;
    static final int RECENT_APPS_BEHAVIOR_DISMISS_AND_SWITCH = 3;

    RecentApplicationsDialog mRecentAppsDialog;
    int mRecentAppsDialogHeldModifiers;
    boolean mLanguageSwitchKeyPressed;

    int mLidState = LID_ABSENT;
    boolean mHaveBuiltInKeyboard;

    boolean mSystemReady;
    boolean mSystemBooted;
    boolean mHdmiPlugged;
    int mExternalDisplayWidth;
    int mExternalDisplayHeight;
    int mUiMode;
    int mDockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    int mLidOpenRotation;
    boolean mHasRemovableLid;
    int mCarDockRotation;
    int mDeskDockRotation;
    int mHdmiRotation;

    int mUserRotationMode = WindowManagerPolicy.USER_ROTATION_FREE;
    int mUserRotation = Surface.ROTATION_0;
    int mUserRotationAngles = -1;
    boolean mAccelerometerDefault;

    int mAllowAllRotations = -1;
    boolean mCarDockEnablesAccelerometer;
    boolean mDeskDockEnablesAccelerometer;
    int mLidKeyboardAccessibility;
    int mLidNavigationAccessibility;
    boolean mLidControlsSleep;
    int mLongPressOnPowerBehavior = -1;
    boolean mScreenOnEarly = false;
    boolean mScreenOnFully = false;
    boolean mOrientationSensorEnabled = false;
    int mCurrentAppOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    boolean mHasSoftInput = false;
    int mBackKillTimeout;
    int mDeviceHardwareKeys;
    boolean mHasHomeKey;
    boolean mHasMenuKey;
    boolean mHasAssistKey;
    boolean mHasAppSwitchKey;
    
    int mPointerLocationMode = 0; // guarded by mLock

    // The last window we were told about in focusChanged.
    WindowState mFocusedWindow;
    IApplicationToken mFocusedApp;

    // Behavior of volume wake
    boolean mVolumeWakeScreen;

    // Behavior of volbtn music controls
    boolean mVolBtnMusicControls;
    boolean mIsLongPress;

    private static final class PointerLocationInputEventReceiver extends InputEventReceiver {
        private final PointerLocationView mView;

        public PointerLocationInputEventReceiver(InputChannel inputChannel, Looper looper,
                PointerLocationView view) {
            super(inputChannel, looper);
            mView = view;
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    mView.addPointerEvent(motionEvent);
                    handled = true;
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }

    // Pointer location view state, only modified on the mHandler Looper.
    PointerLocationInputEventReceiver mPointerLocationInputEventReceiver;
    PointerLocationView mPointerLocationView;
    InputChannel mPointerLocationInputChannel;

    // The current size of the screen; really; (ir)regardless of whether the status
    // bar can be hidden or not
    int mUnrestrictedScreenLeft, mUnrestrictedScreenTop;
    int mUnrestrictedScreenWidth, mUnrestrictedScreenHeight;
    // The current size of the screen; these may be different than (0,0)-(dw,dh)
    // if the status bar can't be hidden; in that case it effectively carves out
    // that area of the display from all other windows.
    int mRestrictedScreenLeft, mRestrictedScreenTop;
    int mRestrictedScreenWidth, mRestrictedScreenHeight;
    // During layout, the current screen borders accounting for any currently
    // visible system UI elements.
    int mSystemLeft, mSystemTop, mSystemRight, mSystemBottom;
    // For applications requesting stable content insets, these are them.
    int mStableLeft, mStableTop, mStableRight, mStableBottom;
    // For applications requesting stable content insets but have also set the
    // fullscreen window flag, these are the stable dimensions without the status bar.
    int mStableFullscreenLeft, mStableFullscreenTop;
    int mStableFullscreenRight, mStableFullscreenBottom;
    // During layout, the current screen borders with all outer decoration
    // (status bar, input method dock) accounted for.
    int mCurLeft, mCurTop, mCurRight, mCurBottom;
    // During layout, the frame in which content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.  This is usually
    // the same as mCur*, but may be larger if the screen decor has supplied
    // content insets.
    int mContentLeft, mContentTop, mContentRight, mContentBottom;
    // During layout, the current screen borders along which input method
    // windows are placed.
    int mDockLeft, mDockTop, mDockRight, mDockBottom;
    // During layout, the layer at which the doc window is placed.
    int mDockLayer;
    // During layout, this is the layer of the status bar.
    int mStatusBarLayer;
    int mLastSystemUiFlags;
    // Bits that we are in the process of clearing, so we want to prevent
    // them from being set by applications until everything has been updated
    // to have them clear.
    int mResettingSystemUiFlags = 0;
    // Bits that we are currently always keeping cleared.
    int mForceClearedSystemUiFlags = 0;
    // What we last reported to system UI about whether the compatibility
    // menu needs to be displayed.
    boolean mLastFocusNeedsMenu = false;

    FakeWindow mHideNavFakeWindow = null;

    static final Rect mTmpParentFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();
    static final Rect mTmpNavigationFrame = new Rect();
    
    WindowState mTopFullscreenOpaqueWindowState;
    boolean mTopIsFullscreen;
    boolean mForceStatusBar;
    boolean mHideLockScreen;
    boolean mDismissKeyguard;
    boolean mHomeLongPressed;
    boolean mAppSwitchLongPressed;
    Intent mHomeIntent;
    Intent mCarDockIntent;
    Intent mDeskDockIntent;
    boolean mSearchKeyShortcutPending;
    boolean mConsumeSearchKeyUp;
    boolean mAssistKeyLongPressed;

    // Used when key is pressed and performing non-default action
    boolean mMenuDoCustomAction;

    // Tracks user-customisable behavior for certain key events
    private int mLongPressOnHomeBehavior = -1;
    private int mPressOnMenuBehavior = -1;
    private int mLongPressOnMenuBehavior = -1;
    private int mPressOnAssistBehavior = -1;
    private int mLongPressOnAssistBehavior = -1;
    private int mPressOnAppSwitchBehavior = -1;
    private int mLongPressOnAppSwitchBehavior = -1;

    private boolean mHomePressed;

    // To identify simulated keypresses, so we can perform
    // the default action for that key
    private boolean mIsVirtualKeypress;

    // Tracks preloading of the recent apps screen
    private boolean mRecentAppsPreloaded;

    // support for activating the lock screen while the screen is on
    boolean mAllowLockscreenWhenOn;
    int mLockScreenTimeout;
    boolean mLockScreenTimerActive;

    // visual screen saver support
    boolean mScreenSaverFeatureAvailable;
    int mScreenSaverTimeout = 0;
    boolean mScreenSaverEnabledByUser = false;
    boolean mScreenSaverMayRun = true; // false if a wakelock is held
    boolean mPluggedIn;

    // Behavior of ENDCALL Button.  (See Settings.System.END_BUTTON_BEHAVIOR.)
    int mEndcallBehavior;

    // Behavior of home unlock
    boolean mHomeUnlockScreen;

    // Behavior of POWER button while in-call and screen on.
    // (See Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR.)
    int mIncallPowerBehavior;

    Display mDisplay;

    // Behavior of HOME button during incomming call ring.
    // (See Settings.Secure.RING_HOME_BUTTON_BEHAVIOR.)
    int mRingHomeBehavior;

    int mLandscapeRotation = 0;  // default landscape rotation
    int mSeascapeRotation = 0;   // "other" landscape rotation, 180 degrees from mLandscapeRotation
    int mPortraitRotation = 0;   // default portrait rotation
    int mUpsideDownRotation = 0; // "other" portrait rotation

    // Screenshot trigger states
    // Time to volume and power must be pressed within this interval of each other.
    private static final long ACTION_CHORD_DEBOUNCE_DELAY_MILLIS = 150;
    private boolean mScreenshotChordEnabled;
    private boolean mVolumeDownKeyTriggered;
    private long mVolumeDownKeyTime;
    private long mVolumeUpKeyTime;
    private boolean mVolumeDownKeyConsumedByChord;
    private boolean mVolumeUpKeyConsumedByChord;
    private boolean mVolumeUpKeyTriggered;
    private boolean mPowerKeyTriggered;
    private long mPowerKeyTime;

    ShortcutManager mShortcutManager;
    PowerManager.WakeLock mBroadcastWakeLock;
    boolean mHavePendingMediaKeyRepeatWithWakeLock;

    // Fallback actions by key code.
    private final SparseArray<KeyCharacterMap.FallbackAction> mFallbackActions =
            new SparseArray<KeyCharacterMap.FallbackAction>();

    private static final int MSG_ENABLE_POINTER_LOCATION = 1;
    private static final int MSG_DISABLE_POINTER_LOCATION = 2;
    private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
    private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
    private static final int MSG_DISPATCH_VOLKEY_WITH_WAKE_LOCK = 5;

    private class PolicyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_POINTER_LOCATION:
                    enablePointerLocation();
                    break;
                case MSG_DISABLE_POINTER_LOCATION:
                    disablePointerLocation();
                    break;
                case MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK:
                    dispatchMediaKeyWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK:
                    dispatchMediaKeyRepeatWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_VOLKEY_WITH_WAKE_LOCK:
                    mIsLongPress = true;
                    dispatchMediaKeyWithWakeLockToAudioService((KeyEvent)msg.obj);
                    dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.changeAction((KeyEvent)msg.obj, KeyEvent.ACTION_UP));
                    break;
            }
        }
    }

    private UEventObserver mHDMIObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            setHdmiPlugged("1".equals(event.get("SWITCH_STATE")));
        }
    };

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
                    Settings.System.VOLUME_WAKE_SCREEN), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOLBTN_MUSIC_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACCELEROMETER_ROTATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HOME_UNLOCK_SCREEN), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.USER_ROTATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.POINTER_LOCATION), false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DEFAULT_INPUT_METHOD), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    "fancy_rotation_anim"), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACCELEROMETER_ROTATION_ANGLES), false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SCREENSAVER_ENABLED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_HOME_LONG_PRESS_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_MENU_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_MENU_LONG_PRESS_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_ASSIST_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_ASSIST_LONG_PRESS_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_APP_SWITCH_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HARDWARE_KEY_REBINDING), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this);
            if (SEPARATE_TIMEOUT_FOR_SCREEN_SAVER) {
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                        "screensaver_timeout"), false, this);
            } // otherwise SCREEN_OFF_TIMEOUT will do nicely
            updateSettings();
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
            updateRotation(false);
        }
    }
    
    class MyOrientationListener extends WindowOrientationListener {
        MyOrientationListener(Context context) {
            super(context);
        }
        
        @Override
        public void onProposedRotationChanged(int rotation) {
            if (localLOGV) Log.v(TAG, "onProposedRotationChanged, rotation=" + rotation);
            updateRotation(false);
        }
    }
    MyOrientationListener mOrientationListener;

    IStatusBarService getStatusBarService() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarService == null) {
                mStatusBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
            }
            return mStatusBarService;
        }
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
                (mDeskDockEnablesAccelerometer && (mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK))) {
            // enable accelerometer if we are docked in a dock that enables accelerometer
            // orientation management,
            return true;
        }
        if (mUserRotationMode == USER_ROTATION_LOCKED) {
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
        if (localLOGV) Log.v(TAG, "Screen status="+mScreenOnEarly+
                ", current orientation="+mCurrentAppOrientation+
                ", SensorEnabled="+mOrientationSensorEnabled);
        boolean disable = true;
        if (mScreenOnEarly) {
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
        }
        return false;
    }

    private void cancelPendingPowerKeyAction() {
        if (!mPowerKeyHandled) {
            mHandler.removeCallbacks(mPowerLongPress);
        }
        if (mPowerKeyTriggered) {
            mPendingPowerKeyUpCanceled = true;
        }
    }

    private void interceptScreenshotChord() {
        if (mScreenshotChordEnabled
                && mVolumeDownKeyTriggered && mPowerKeyTriggered && !mVolumeUpKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mVolumeDownKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mPowerKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mVolumeDownKeyConsumedByChord = true;
                cancelPendingPowerKeyAction();

                mHandler.postDelayed(mScreenshotChordLongPress,
                        ViewConfiguration.getGlobalActionKeyTimeout());
            }
        }
    }

    private void cancelPendingScreenshotChordAction() {
        mHandler.removeCallbacks(mScreenshotChordLongPress);
    }

    private void interceptRingerChord() {
        if (mVolumeDownKeyTriggered && !mPowerKeyTriggered && mVolumeUpKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mVolumeDownKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mVolumeUpKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mVolumeDownKeyConsumedByChord = true;
                mVolumeUpKeyConsumedByChord = true;

                mHandler.postDelayed(mRingerChordLongPress,
                        ViewConfiguration.getGlobalActionKeyTimeout());
            }
        }
    }

    private void cancelPendingRingerChordAction() {
        mHandler.removeCallbacks(mRingerChordLongPress);
    }

    private final Runnable mPowerLongPress = new Runnable() {
        public void run() {
            // The context isn't read
            if (mLongPressOnPowerBehavior < 0) {
                mLongPressOnPowerBehavior = mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_longPressOnPowerBehavior);
            }
            switch (mLongPressOnPowerBehavior) {
            case LONG_PRESS_POWER_NOTHING:
                break;
            case LONG_PRESS_POWER_GLOBAL_ACTIONS:
                mPowerKeyHandled = true;
                performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
                showGlobalActionsDialog();
                break;
            case LONG_PRESS_POWER_SHUT_OFF:
                mPowerKeyHandled = true;
                performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
                mWindowManagerFuncs.shutdown();
                break;
            }
        }
    };

    private final Runnable mScreenshotChordLongPress = new Runnable() {
        public void run() {
            takeScreenshot();
        }
    };

    private final Runnable mRingerChordLongPress = new Runnable() {
        public void run() {
            // Do the switch
            final AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
            final int ringerMode = am.getRingerMode();
            final VolumePanel volumePanel = new VolumePanel(ThemeUtils.createUiContext(mContext),
                                                              (AudioService) getAudioService());
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                boolean vibrateSetting = Settings.System.getInt(mContext.getContentResolver(),
                                           Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
                am.setRingerMode(vibrateSetting ? AudioManager.RINGER_MODE_VIBRATE :
                                   AudioManager.RINGER_MODE_SILENT);
            } else {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
            volumePanel.postVolumeChanged(AudioManager.STREAM_RING,AudioManager.FLAG_SHOW_UI
                                          | AudioManager.FLAG_VIBRATE);
        }
    };

    Runnable mBackLongPress = new Runnable() {
        public void run() {
            try {
                final Intent intent = new Intent(Intent.ACTION_MAIN);
                String defaultHomePackage = "com.android.launcher";
                intent.addCategory(Intent.CATEGORY_HOME);
                final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
                if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                    defaultHomePackage = res.activityInfo.packageName;
                }
                boolean targetKilled = false;
                IActivityManager am = ActivityManagerNative.getDefault();
                List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
                for (RunningAppProcessInfo appInfo : apps) {
                    int uid = appInfo.uid;
                    // Make sure it's a foreground user application (not system,
                    // root, phone, etc.)
                    if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                            && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                            for (String pkg : appInfo.pkgList) {
                                if (!pkg.equals("com.android.systemui") && !pkg.equals(defaultHomePackage)) {
                                    am.forceStopPackage(pkg);
                                    targetKilled = true;
                                    break;
                                }
                            }
                        } else {
                            Process.killProcess(appInfo.pid);
                            targetKilled = true;
                        }
                    }
                    if (targetKilled) {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            } catch (RemoteException remoteException) {
                // Do nothing; just let it go.
            }
        }
    };

    void showGlobalActionsDialog() {
        if (mGlobalActions == null) {
            mGlobalActions = new GlobalActions(mContext, mWindowManagerFuncs);
        }
        final boolean keyguardShowing = keyguardIsShowingTq();
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

    private void triggerVirtualKeypress(final int keyCode) {
        new Thread(new Runnable() {
            public void run() {
                InputManager im = InputManager.getInstance();
                long now = SystemClock.uptimeMillis();

                final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                        keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                        KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
                final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

                mIsVirtualKeypress = true;
                im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
                im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
                mIsVirtualKeypress = false;
            }
        }).start();
    }

    private void performKeyAction(int behavior) {
        switch (behavior) {
            case KEY_ACTION_NOTHING:
                break;
            case KEY_ACTION_MENU:
                triggerVirtualKeypress(KeyEvent.KEYCODE_MENU);
                break;
            case KEY_ACTION_APP_SWITCH:
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_RECENT_APPS);
                try {
                    IStatusBarService statusbar = getStatusBarService();
                    if (statusbar != null) {
                        statusbar.toggleRecentApps();
                        mRecentAppsPreloaded = false;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "RemoteException when showing recent apps", e);
                    // re-acquire status bar service next time it is needed.
                    mStatusBarService = null;
                }
                break;
            case KEY_ACTION_SEARCH:
                launchAssistAction();
                break;
            case KEY_ACTION_VOICE_SEARCH:
                launchAssistLongPressAction();
                break;
            case KEY_ACTION_IN_APP_SEARCH:
                triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH);
                break;
            default:
                break;
        }
    }

    private void preloadRecentApps() {
        try {
            IStatusBarService statusbar = getStatusBarService();
            if (statusbar != null) {
                statusbar.preloadRecentApps();
                mRecentAppsPreloaded = true;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when preloading recent apps", e);
            // re-acquire status bar service next time it is needed.
            mStatusBarService = null;
        }
    }

    private void cancelPreloadRecentApps() {
        try {
            IStatusBarService statusbar = getStatusBarService();
            if (statusbar != null) {
                statusbar.cancelPreloadRecentApps();
                mRecentAppsPreloaded = false;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when showing recent apps", e);
            // re-acquire status bar service next time it is needed.
            mStatusBarService = null;
        }
    }

    /**
     * Create (if necessary) and show or dismiss the recent apps dialog according
     * according to the requested behavior.
     */
    void showOrHideRecentAppsDialog(final int behavior) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mRecentAppsDialog == null) {
                    mRecentAppsDialog = new RecentApplicationsDialog(mContext);
                }
                if (mRecentAppsDialog.isShowing()) {
                    switch (behavior) {
                        case RECENT_APPS_BEHAVIOR_SHOW_OR_DISMISS:
                        case RECENT_APPS_BEHAVIOR_DISMISS:
                            mRecentAppsDialog.dismiss();
                            break;
                        case RECENT_APPS_BEHAVIOR_DISMISS_AND_SWITCH:
                            mRecentAppsDialog.dismissAndSwitch();
                            break;
                        case RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW:
                        default:
                            break;
                    }
                } else {
                    switch (behavior) {
                        case RECENT_APPS_BEHAVIOR_SHOW_OR_DISMISS:
                            mRecentAppsDialog.show();
                            break;
                        case RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW:
                            try {
                                mWindowManager.setInTouchMode(false);
                            } catch (RemoteException e) {
                            }
                            mRecentAppsDialog.show();
                            break;
                        case RECENT_APPS_BEHAVIOR_DISMISS:
                        case RECENT_APPS_BEHAVIOR_DISMISS_AND_SWITCH:
                        default:
                            break;
                    }
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs,
            LocalPowerManager powerManager) {
        mContext = context;
        mWindowManager = windowManager;
        mWindowManagerFuncs = windowManagerFuncs;
        mPowerManager = powerManager;
        mHeadless = "1".equals(SystemProperties.get("ro.config.headless", "0"));
        if (!mHeadless) {
            // don't create KeyguardViewMediator if headless
            mKeyguardMediator = new KeyguardViewMediator(context, this, powerManager);
        }
        mHandler = new PolicyHandler();
        mOrientationListener = new MyOrientationListener(mContext);
        try {
            mOrientationListener.setCurrentRotation(windowManager.getRotation());
        } catch (RemoteException ex) { }
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        mShortcutManager = new ShortcutManager(context, mHandler);
        mShortcutManager.observe();
        mUiMode = context.getResources().getInteger(
                com.android.internal.R.integer.config_defaultUiModeType);
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
        mCarDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_carDockEnablesAccelerometer);
        mDeskDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_deskDockEnablesAccelerometer);
        mLidKeyboardAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidKeyboardAccessibility);
        mLidNavigationAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidNavigationAccessibility);
        mLidControlsSleep = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_lidControlsSleep);
        mHasRemovableLid = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_hasRemovableLid);
        mBackKillTimeout = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_backKillTimeout);
        mDeviceHardwareKeys = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        mHasHomeKey = ((mDeviceHardwareKeys & KEY_MASK_HOME) != 0);
        mHasMenuKey = ((mDeviceHardwareKeys & KEY_MASK_MENU) != 0);
        mHasAssistKey = ((mDeviceHardwareKeys & KEY_MASK_ASSIST) != 0);
        mHasAppSwitchKey = ((mDeviceHardwareKeys & KEY_MASK_APP_SWITCH) != 0);
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

        // watch the plug to know whether to trigger the screen saver
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intent = context.registerReceiver(mPowerReceiver, filter);
        if (intent != null) {
            mPluggedIn = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
        }

        mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        mLongPressVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_longPressVibePattern);
        mVirtualKeyVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_virtualKeyVibePattern);
        mKeyboardTapVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_keyboardTapVibePattern);
        mSafeModeDisabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeDisabledVibePattern);
        mSafeModeEnabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeEnabledVibePattern);

        mScreenshotChordEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableScreenshotChord);

        // Controls rotation and the like.
        initializeHdmiState();

        // Match current screen state.
        if (mPowerManager.isScreenOn()) {
            screenTurningOn(null);
        } else {
            screenTurnedOff(WindowManagerPolicy.OFF_BECAUSE_OF_USER);
        }

        String deviceKeyHandlerLib = mContext.getResources().getString(
                com.android.internal.R.string.config_deviceKeyHandlerLib);

        String deviceKeyHandlerClass = mContext.getResources().getString(
                com.android.internal.R.string.config_deviceKeyHandlerClass);

        if (!deviceKeyHandlerLib.equals("") && !deviceKeyHandlerClass.equals("")) {
            DexClassLoader loader =  new DexClassLoader(deviceKeyHandlerLib,
                    new ContextWrapper(mContext).getCacheDir().getAbsolutePath(),
                    null,
                    ClassLoader.getSystemClassLoader());
            try {
                Class<?> klass = loader.loadClass(deviceKeyHandlerClass);
                Constructor<?> constructor = klass.getConstructor(Context.class);
                mDeviceKeyHandler = (DeviceKeyHandler) constructor.newInstance(
                        mContext);
                Slog.d(TAG, "Device key handler loaded");
            } catch (Exception e) {
                Slog.d(TAG, "Could not instantiate device key handler "
                        + deviceKeyHandlerClass + " from class "
                        + deviceKeyHandlerLib, e);
            }
        }
    }

    public void setInitialDisplaySize(Display display, int width, int height) {
        mDisplay = display;

        int shortSize, longSize;
        if (width > height) {
            shortSize = height;
            longSize = width;
            mLandscapeRotation = Surface.ROTATION_0;
            mSeascapeRotation = Surface.ROTATION_180;
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mPortraitRotation = Surface.ROTATION_90;
                mUpsideDownRotation = Surface.ROTATION_270;
            } else {
                mPortraitRotation = Surface.ROTATION_270;
                mUpsideDownRotation = Surface.ROTATION_90;
            }
        } else {
            shortSize = width;
            longSize = height;
            mPortraitRotation = Surface.ROTATION_0;
            mUpsideDownRotation = Surface.ROTATION_180;
            if (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mLandscapeRotation = Surface.ROTATION_270;
                mSeascapeRotation = Surface.ROTATION_90;
            } else {
                mLandscapeRotation = Surface.ROTATION_90;
                mSeascapeRotation = Surface.ROTATION_270;
            }
        }

        mExternalDisplayWidth = mDisplay.getRawExternalWidth();
        mExternalDisplayHeight = mDisplay.getRawExternalHeight();

        mStatusBarHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        // Height of the navigation bar when presented horizontally at bottom
        mNavigationBarHeightForRotation[mPortraitRotation] =
        mNavigationBarHeightForRotation[mUpsideDownRotation] =
                mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_height);
        mNavigationBarHeightForRotation[mLandscapeRotation] =
        mNavigationBarHeightForRotation[mSeascapeRotation] =
                mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_height_landscape);

        // Width of the navigation bar when presented vertically along one side
        mNavigationBarWidthForRotation[mPortraitRotation] =
        mNavigationBarWidthForRotation[mUpsideDownRotation] =
        mNavigationBarWidthForRotation[mLandscapeRotation] =
        mNavigationBarWidthForRotation[mSeascapeRotation] =
                mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_width);

        // SystemUI (status bar) layout policy
        int shortSizeDp = shortSize
                * DisplayMetrics.DENSITY_DEFAULT
                / DisplayMetrics.DENSITY_DEVICE;

        if (shortSizeDp < 600) {
            // 0-599dp: "phone" UI with a separate status & navigation bar
            mHasSystemNavBar = false;
            mNavigationBarCanMove = true;
        } else if (shortSizeDp < 720) {
            // 600-719dp: "phone" UI with modifications for larger screens
            mHasSystemNavBar = false;
            mNavigationBarCanMove = false;
        } else {
            // 720dp: "tablet" UI with a single combined status & navigation bar
            mHasSystemNavBar = true;
            mNavigationBarCanMove = false;
        }

        if (!mHasSystemNavBar) {
            mHasNavigationBar = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_showNavigationBar);
            // Allow a system property to override this. Used by the emulator.
            // See also hasNavigationBar().
            String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
            if (! "".equals(navBarOverride)) {
                if      (navBarOverride.equals("1")) mHasNavigationBar = false;
                else if (navBarOverride.equals("0")) mHasNavigationBar = true;
            }
        } else {
            mHasNavigationBar = false;
        }

        if (mHasSystemNavBar) {
            // The system bar is always at the bottom.  If you are watching
            // a video in landscape, we don't need to hide it if we can still
            // show a 16:9 aspect ratio with it.
            int longSizeDp = longSize
                    * DisplayMetrics.DENSITY_DEFAULT
                    / DisplayMetrics.DENSITY_DEVICE;
            int barHeightDp = mNavigationBarHeightForRotation[mLandscapeRotation]
                    * DisplayMetrics.DENSITY_DEFAULT
                    / DisplayMetrics.DENSITY_DEVICE;
            int aspect = ((shortSizeDp-barHeightDp) * 16) / longSizeDp;
            // We have computed the aspect ratio with the bar height taken
            // out to be 16:aspect.  If this is less than 9, then hiding
            // the navigation bar will provide more useful space for wide
            // screen movies.
            mCanHideNavigationBar = aspect < 9;
        } else if (mHasNavigationBar) {
            // The navigation bar is at the right in landscape; it seems always
            // useful to hide it for showing a video.
            mCanHideNavigationBar = true;
        } else {
            mCanHideNavigationBar = false;
        }

        if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
            mHdmiRotation = mPortraitRotation;
        } else {
            mHdmiRotation = mLandscapeRotation;
        }
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        boolean updateRotation = false;
        synchronized (mLock) {
            mEndcallBehavior = Settings.System.getInt(resolver,
                    Settings.System.END_BUTTON_BEHAVIOR,
                    Settings.System.END_BUTTON_BEHAVIOR_DEFAULT);
            mIncallPowerBehavior = Settings.Secure.getInt(resolver,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT);
            mRingHomeBehavior = Settings.Secure.getInt(resolver,
                    Settings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                    Settings.Secure.RING_HOME_BUTTON_BEHAVIOR_DEFAULT);
            mVolumeWakeScreen = (Settings.System.getInt(resolver,
                    Settings.System.VOLUME_WAKE_SCREEN, 0) == 1);
            mVolBtnMusicControls = (Settings.System.getInt(resolver,
                    Settings.System.VOLBTN_MUSIC_CONTROLS, 1) == 1);
            mHomeUnlockScreen = (Settings.System.getInt(resolver,
                    Settings.System.HOME_UNLOCK_SCREEN, 0) == 1);

            boolean keyRebindingEnabled = Settings.System.getInt(resolver,
                    Settings.System.HARDWARE_KEY_REBINDING, 0) == 1;

            if (!keyRebindingEnabled) {
                if (mHasHomeKey) {
                    if (mHasAppSwitchKey) {
                        mLongPressOnHomeBehavior = KEY_ACTION_NOTHING;
                    } else {
                        mLongPressOnHomeBehavior = KEY_ACTION_APP_SWITCH;
                    }
                }
                if (mHasMenuKey) {
                    mPressOnMenuBehavior = KEY_ACTION_MENU;
                    if (mHasAssistKey) {
                        mLongPressOnMenuBehavior = KEY_ACTION_NOTHING;
                    } else {
                        mLongPressOnMenuBehavior = KEY_ACTION_SEARCH;
                    }
                }
                if (mHasAssistKey) {
                    mPressOnAssistBehavior = KEY_ACTION_SEARCH;
                    mLongPressOnAssistBehavior = KEY_ACTION_VOICE_SEARCH;
                }
                if (mHasAppSwitchKey) {
                    mPressOnAppSwitchBehavior = KEY_ACTION_APP_SWITCH;
                    mLongPressOnAppSwitchBehavior = KEY_ACTION_NOTHING;
                }
            } else {
                if (mHasHomeKey) {
                    if (mHasAppSwitchKey) {
                        mLongPressOnHomeBehavior = Settings.System.getInt(resolver,
                                Settings.System.KEY_HOME_LONG_PRESS_ACTION, KEY_ACTION_NOTHING);
                    } else {
                        mLongPressOnHomeBehavior = Settings.System.getInt(resolver,
                                Settings.System.KEY_HOME_LONG_PRESS_ACTION, KEY_ACTION_APP_SWITCH);
                    }
                }
                if (mHasMenuKey) {
                    mPressOnMenuBehavior = Settings.System.getInt(resolver,
                            Settings.System.KEY_MENU_ACTION, KEY_ACTION_MENU);
                    if (mHasAssistKey) {
                        mLongPressOnMenuBehavior = Settings.System.getInt(resolver,
                                Settings.System.KEY_MENU_LONG_PRESS_ACTION, KEY_ACTION_NOTHING);
                    } else {
                        mLongPressOnMenuBehavior = Settings.System.getInt(resolver,
                                Settings.System.KEY_MENU_LONG_PRESS_ACTION, KEY_ACTION_SEARCH);
                    }
                }
                if (mHasAssistKey) {
                    mPressOnAssistBehavior = Settings.System.getInt(resolver,
                            Settings.System.KEY_ASSIST_ACTION, KEY_ACTION_SEARCH);
                    mLongPressOnAssistBehavior = Settings.System.getInt(resolver,
                            Settings.System.KEY_ASSIST_LONG_PRESS_ACTION, KEY_ACTION_VOICE_SEARCH);
                }
                if (mHasAppSwitchKey) {
                    mPressOnAppSwitchBehavior = Settings.System.getInt(resolver,
                            Settings.System.KEY_APP_SWITCH_ACTION, KEY_ACTION_APP_SWITCH);
                    mLongPressOnAppSwitchBehavior = Settings.System.getInt(resolver,
                            Settings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION, KEY_ACTION_NOTHING);
                }
            }

            // Configure rotation lock.
            int userRotation = Settings.System.getInt(resolver,
                    Settings.System.USER_ROTATION, Surface.ROTATION_0);

            if (mUserRotation != userRotation) {
                mUserRotation = userRotation;
                updateRotation = true;
            }
            int userRotationMode = Settings.System.getInt(resolver,
                    Settings.System.ACCELEROMETER_ROTATION, 0) != 0 ?
                            WindowManagerPolicy.USER_ROTATION_FREE :
                                    WindowManagerPolicy.USER_ROTATION_LOCKED;
            if (mUserRotationMode != userRotationMode) {
                mUserRotationMode = userRotationMode;
                updateRotation = true;
                updateOrientationListenerLp();
            }

            mUserRotationAngles = Settings.System.getInt(resolver,
                    Settings.System.ACCELEROMETER_ROTATION_ANGLES, -1);

            if (mSystemReady) {
                int pointerLocation = Settings.System.getInt(resolver,
                        Settings.System.POINTER_LOCATION, 0);
                if (mPointerLocationMode != pointerLocation) {
                    mPointerLocationMode = pointerLocation;
                    mHandler.sendEmptyMessage(pointerLocation != 0 ?
                            MSG_ENABLE_POINTER_LOCATION : MSG_DISABLE_POINTER_LOCATION);
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

            // dreams
            mScreenSaverFeatureAvailable = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_enableDreams);
            
            mScreenSaverEnabledByUser = 0 != Settings.Secure.getInt(resolver,
                    Settings.Secure.SCREENSAVER_ENABLED, 1);

            if (SEPARATE_TIMEOUT_FOR_SCREEN_SAVER) {
                mScreenSaverTimeout = Settings.Secure.getInt(resolver,
                        "screensaver_timeout", 0);
            } else {
                mScreenSaverTimeout = Settings.System.getInt(resolver,
                        Settings.System.SCREEN_OFF_TIMEOUT, 0);
                if (mScreenSaverTimeout > 0) {
                    // We actually want to activate the screensaver just before the
                    // power manager's screen timeout
                    mScreenSaverTimeout -= 5000;
                }
            }
        }
        if (updateRotation) {
            updateRotation(true);
        }
    }

    private void enablePointerLocation() {
        if (mPointerLocationView == null) {
            mPointerLocationView = new PointerLocationView(mContext);
            mPointerLocationView.setPrintCoords(false);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            lp.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
            lp.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            lp.format = PixelFormat.TRANSLUCENT;
            lp.setTitle("PointerLocation");
            WindowManager wm = (WindowManager)
                    mContext.getSystemService(Context.WINDOW_SERVICE);
            lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
            wm.addView(mPointerLocationView, lp);

            mPointerLocationInputChannel =
                    mWindowManagerFuncs.monitorInput("PointerLocationView");
            mPointerLocationInputEventReceiver =
                    new PointerLocationInputEventReceiver(mPointerLocationInputChannel,
                            Looper.myLooper(), mPointerLocationView);
        }
    }

    private void disablePointerLocation() {
        if (mPointerLocationInputEventReceiver != null) {
            mPointerLocationInputEventReceiver.dispose();
            mPointerLocationInputEventReceiver = null;
        }

        if (mPointerLocationInputChannel != null) {
            mPointerLocationInputChannel.dispose();
            mPointerLocationInputChannel = null;
        }

        if (mPointerLocationView != null) {
            WindowManager wm = (WindowManager)
                    mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mPointerLocationView);
            mPointerLocationView = null;
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
            case TYPE_DREAM:
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
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                break;
        }
    }
    
    void readLidState() {
        mLidState = mWindowManagerFuncs.getLidState();
    }
    
    private boolean isHidden(int accessibilityMode) {
        switch (accessibilityMode) {
            case 1:
                return mLidState == LID_CLOSED;
            case 2:
                return mLidState == LID_OPEN;
            default:
                return false;
        }
    }

    private boolean isBuiltInKeyboardVisible() {
        return mHaveBuiltInKeyboard && !isHidden(mLidKeyboardAccessibility);
    }

    /** {@inheritDoc} */
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {
        mHaveBuiltInKeyboard = (keyboardPresence & PRESENCE_INTERNAL) != 0;

        readLidState();
        applyLidSwitchState();

        if (config.keyboard == Configuration.KEYBOARD_NOKEYS
                || (keyboardPresence == PRESENCE_INTERNAL
                        && isHidden(mLidKeyboardAccessibility))) {
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;
            if (!mHasSoftInput) {
                config.keyboardHidden = Configuration.KEYBOARDHIDDEN_YES;
            }
        }

        if (config.navigation == Configuration.NAVIGATION_NONAV
                || (navigationPresence == PRESENCE_INTERNAL
                        && isHidden(mLidNavigationAccessibility))) {
            config.navigationHidden = Configuration.NAVIGATIONHIDDEN_YES;
        }
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
        case TYPE_STATUS_BAR_SUB_PANEL:
            return STATUS_BAR_SUB_PANEL_LAYER;
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
        case TYPE_VOLUME_OVERLAY:
            return VOLUME_OVERLAY_LAYER;
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
        case TYPE_DRAG:
            return DRAG_LAYER;
        case TYPE_POINTER:
            return POINTER_LAYER;
        case TYPE_NAVIGATION_BAR:
            return NAVIGATION_BAR_LAYER;
        case TYPE_NAVIGATION_BAR_PANEL:
            return NAVIGATION_BAR_PANEL_LAYER;
        case TYPE_BOOT_PROGRESS:
            return BOOT_PROGRESS_LAYER;
        case TYPE_HIDDEN_NAV_CONSUMER:
            return HIDDEN_NAV_CONSUMER_LAYER;
        case TYPE_DREAM:
            return SCREENSAVER_LAYER;
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

    public boolean hasSystemNavBar() {
        return mHasSystemNavBar;
    }

    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation) {
        if (mHasNavigationBar) {
            // For a basic navigation bar, when we are in landscape mode we place
            // the navigation bar to the side.
            if (mNavigationBarCanMove && fullWidth > fullHeight) {
                return fullWidth - mNavigationBarWidthForRotation[rotation];
            }
        }
        return fullWidth;
    }

    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation) {
        if (mHasSystemNavBar) {
            // For the system navigation bar, we always place it at the bottom.
            return fullHeight - mNavigationBarHeightForRotation[rotation];
        }
        if (mHasNavigationBar) {
            // For a basic navigation bar, when we are in portrait mode we place
            // the navigation bar to the bottom.
            if (!mNavigationBarCanMove || fullWidth < fullHeight) {
                return fullHeight - mNavigationBarHeightForRotation[rotation];
            }
        }
        return fullHeight;
    }

    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation) {
        return getNonDecorDisplayWidth(fullWidth, fullHeight, rotation);
    }

    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation) {
        // If we don't have a system nav bar, then there is a separate status
        // bar at the top of the display.  We don't count that as part of the
        // fixed decor, since it can hide; however, for purposes of configurations,
        // we do want to exclude it since applications can't generally use that part
        // of the screen.
        if (!mHasSystemNavBar) {
            return getNonDecorDisplayHeight(fullWidth, fullHeight, rotation) - mStatusBarHeight;
        }
        return getNonDecorDisplayHeight(fullWidth, fullHeight, rotation);
    }

    public boolean doesForceHide(WindowState win, WindowManager.LayoutParams attrs) {
        return attrs.type == WindowManager.LayoutParams.TYPE_KEYGUARD;
    }
    
    public boolean canBeForceHidden(WindowState win, WindowManager.LayoutParams attrs) {
        return attrs.type != WindowManager.LayoutParams.TYPE_STATUS_BAR
                && attrs.type != WindowManager.LayoutParams.TYPE_NAVIGATION_BAR
                && attrs.type != WindowManager.LayoutParams.TYPE_WALLPAPER;
    }

    /** {@inheritDoc} */
    @Override
    public View addStartingWindow(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes,
            int icon, int windowFlags) {
        if (!SHOW_STARTING_ANIMATIONS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        try {
            Context context = mContext;
            if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "addStartingWindow " + packageName
                    + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme="
                    + Integer.toHexString(theme));

            try {
                context = context.createPackageContext(packageName, 0);
                if (theme != context.getThemeResId()) {

                    context.setTheme(theme);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }

            Window win = PolicyManager.makeNewWindow(context);
            final TypedArray ta = win.getWindowStyle();
            if (ta.getBoolean(
                        com.android.internal.R.styleable.Window_windowDisablePreview, false)
                || ta.getBoolean(
                        com.android.internal.R.styleable.Window_windowShowWallpaper,false)) {
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
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            if (!compatInfo.supportsScreen()) {
                win.addFlags(WindowManager.LayoutParams.FLAG_COMPATIBLE_WINDOW);
            }

            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            final WindowManager.LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
            params.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED;
            params.setTitle("Starting " + packageName);

            WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
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

            if (DEBUG_STARTING_WINDOW) Slog.d(
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
        if (DEBUG_STARTING_WINDOW) {
            RuntimeException e = new RuntimeException("here");
            e.fillInStackTrace();
            Log.v(TAG, "Removing starting window for " + appToken + ": " + window, e);
        }

        if (window != null) {
            WindowManager wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
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
                if (mStatusBar != null) {
                    if (mStatusBar.isAlive()) {
                        return WindowManagerImpl.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mStatusBar = win;
                break;
            case TYPE_NAVIGATION_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                if (mNavigationBar != null) {
                    if (mNavigationBar.isAlive()) {
                        return WindowManagerImpl.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mNavigationBar = win;
                if (DEBUG_LAYOUT) Log.i(TAG, "NAVIGATION BAR: " + mNavigationBar);
                break;
            case TYPE_NAVIGATION_BAR_PANEL:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                break;
            case TYPE_STATUS_BAR_PANEL:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                break;
            case TYPE_STATUS_BAR_SUB_PANEL:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
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
        } else if (mKeyguard == win) {
            mKeyguard = null;
        } else if (mNavigationBar == win) {
            mNavigationBar = null;
        }
    }

    static final boolean PRINT_ANIM = false;
    
    /** {@inheritDoc} */
    public int selectAnimationLw(WindowState win, int transit) {
        if (PRINT_ANIM) Log.i(TAG, "selectAnimation in " + win
              + ": transit=" + transit);
        if (win == mStatusBar) {
            if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                return R.anim.dock_top_exit;
            } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                return R.anim.dock_top_enter;
            }
        } else if (win == mNavigationBar) {
            // This can be on either the bottom or the right.
            if (mNavigationBarOnBottom) {
                if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                    return R.anim.dock_bottom_exit;
                } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                    return R.anim.dock_bottom_enter;
                }
            } else {
                if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                    return R.anim.dock_right_exit;
                } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                    return R.anim.dock_right_enter;
                }
            }
        } if (transit == TRANSIT_PREVIEW_DONE) {
            if (win.hasAppShownWindows()) {
                if (PRINT_ANIM) Log.i(TAG, "**** STARTING EXIT");
                return com.android.internal.R.anim.app_starting_exit;
            }
        }

        return 0;
    }

    public Animation createForceHideEnterAnimation(boolean onWallpaper) {
        return AnimationUtils.loadAnimation(mContext, onWallpaper
                ? com.android.internal.R.anim.lock_screen_wallpaper_behind_enter
                : com.android.internal.R.anim.lock_screen_behind_enter);
    }
    
    static ITelephony getTelephonyService() {
        return ITelephony.Stub.asInterface(
                ServiceManager.checkService(Context.TELEPHONY_SERVICE));
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
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        final boolean keyguardOn = keyguardOn();
        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final boolean longPress = (flags & KeyEvent.FLAG_LONG_PRESS) != 0;

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTi keyCode=" + keyCode + " down=" + down + " repeatCount="
                    + repeatCount + " keyguardOn=" + keyguardOn + " canceled=" + canceled);
        }

        // If we think we might have a volume down & power/volume-up key chord on the way
        // but we're not sure, then tell the dispatcher to wait a little while and
        // try again later before dispatching.
        if (mScreenshotChordEnabled && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if (mVolumeDownKeyTriggered && !mPowerKeyTriggered && !mVolumeUpKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = mVolumeDownKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            } else if (mVolumeUpKeyTriggered && !mVolumeDownKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = mVolumeUpKeyTime + ACTION_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    && mVolumeDownKeyConsumedByChord) {
                if (!down) {
                    mVolumeDownKeyConsumedByChord = false;
                }
                return -1;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    && mVolumeUpKeyConsumedByChord) {
                if (!down) {
                    mVolumeUpKeyConsumedByChord = false;
                }
                return -1;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK && !down) {
            mHandler.removeCallbacks(mBackLongPress);
        }

        // First we always handle the home key here, so applications
        // can never break it, although if keyguard is on, we do let
        // it handle it, because that gives us the correct 5 second
        // timeout.
        if (keyCode == KeyEvent.KEYCODE_HOME) {

            // If we have released the home key, and didn't do anything else
            // while it was pressed, then it is time to go home!
            if (!down && mHomePressed) {
                final boolean homeWasLongPressed = mHomeLongPressed;
                mHomeLongPressed = false;
                mHomePressed = false;
                if (!homeWasLongPressed) {
                    if (mRecentAppsPreloaded) {
                        cancelPreloadRecentApps();
                    }

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
                            if ((mRingHomeBehavior
                                 & Settings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER) != 0
                                 && incomingRinging) {
                               Log.i(TAG, "Answering with HOME button.");
                               telephonyService.answerRingingCall();
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
                    return -1;
                }
            }

            // If a system window has focus, then it doesn't make sense
            // right now to interact with applications.
            WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
            if (attrs != null) {
                final int type = attrs.type;
                if (type == WindowManager.LayoutParams.TYPE_KEYGUARD
                        || type == WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG) {
                    // the "app" is keyguard, so give it the key
                    return 0;
                }
                final int typeCount = WINDOW_TYPES_WHERE_HOME_DOESNT_WORK.length;
                for (int i=0; i<typeCount; i++) {
                    if (type == WINDOW_TYPES_WHERE_HOME_DOESNT_WORK[i]) {
                        // don't do anything, but also don't pass it to the app
                        return -1;
                    }
                }
            }
            if (down) {
                if (repeatCount == 0) {
                    mHomePressed = true;
                    if (!mRecentAppsPreloaded && mLongPressOnHomeBehavior == KEY_ACTION_APP_SWITCH) {
                        preloadRecentApps();
                    }
                } else if (longPress) {
                    if (!keyguardOn && mLongPressOnHomeBehavior != KEY_ACTION_NOTHING) {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mLongPressOnHomeBehavior);
                        // Eat the long-press so it won't take us home when the key is released
                        mHomeLongPressed = true;
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Hijack modified menu keys for debugging features
            final int chordBug = KeyEvent.META_SHIFT_ON;

            if (down) {
                if (!mRecentAppsPreloaded && (mPressOnMenuBehavior == KEY_ACTION_APP_SWITCH ||
                        mLongPressOnMenuBehavior == KEY_ACTION_APP_SWITCH)) {
                    preloadRecentApps();
                }
                if (repeatCount == 0) {
                    if (mEnableShiftMenuBugReports && (metaState & chordBug) == chordBug) {
                        Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
                        mContext.sendOrderedBroadcast(intent, null);
                        return -1;
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
                        return -1;
                    } else if (mPressOnMenuBehavior != KEY_ACTION_MENU && !mIsVirtualKeypress) {
                        mMenuDoCustomAction = true;
                        return -1;
                    }
                } else if (longPress) {
                    if (mRecentAppsPreloaded &&
                            mLongPressOnMenuBehavior != KEY_ACTION_APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!keyguardOn && mLongPressOnMenuBehavior != KEY_ACTION_NOTHING) {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mLongPressOnMenuBehavior);
                        // Do not perform action when key is released
                        mMenuDoCustomAction = false;
                        return -1;
                    }
                }
            } else {
                if (mRecentAppsPreloaded && mPressOnMenuBehavior != KEY_ACTION_APP_SWITCH) {
                    cancelPreloadRecentApps();
                }
                if (mMenuDoCustomAction) {
                    mMenuDoCustomAction = false;
                    if (!canceled && !keyguardOn) {
                        performKeyAction(mPressOnMenuBehavior);
                        return -1;
                    }
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (down) {
                if (repeatCount == 0) {
                    mSearchKeyShortcutPending = true;
                    mConsumeSearchKeyUp = false;
                }
            } else {
                mSearchKeyShortcutPending = false;
                if (mConsumeSearchKeyUp) {
                    mConsumeSearchKeyUp = false;
                    return -1;
                }
            }
            return 0;
        } else if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            if (down) {
                if (!mRecentAppsPreloaded && (mPressOnAppSwitchBehavior == KEY_ACTION_APP_SWITCH ||
                        mLongPressOnAppSwitchBehavior == KEY_ACTION_APP_SWITCH)) {
                    preloadRecentApps();
                }
                if (repeatCount == 0) {
                    mAppSwitchLongPressed = false;
                } else if (longPress) {
                    if (mRecentAppsPreloaded &&
                            mLongPressOnAppSwitchBehavior != KEY_ACTION_APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!keyguardOn && mLongPressOnAppSwitchBehavior != KEY_ACTION_NOTHING) {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mLongPressOnAppSwitchBehavior);
                        mAppSwitchLongPressed = true;
                    }
                }
            } else {
                if (mAppSwitchLongPressed) {
                    mAppSwitchLongPressed = false;
                } else {
                    if (mRecentAppsPreloaded &&
                            mPressOnAppSwitchBehavior != KEY_ACTION_APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!canceled && !keyguardOn) {
                        performKeyAction(mPressOnAppSwitchBehavior);
                    }
                    return -1;
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (down) {
                if (!mRecentAppsPreloaded && (mPressOnAssistBehavior == KEY_ACTION_APP_SWITCH ||
                        mLongPressOnAssistBehavior == KEY_ACTION_APP_SWITCH)) {
                    preloadRecentApps();
                }
                if (repeatCount == 0) {
                    mAssistKeyLongPressed = false;
                } else if (longPress) {
                    if (mRecentAppsPreloaded &&
                            mLongPressOnAssistBehavior != KEY_ACTION_APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!keyguardOn && mLongPressOnAssistBehavior != KEY_ACTION_NOTHING) {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
                        performKeyAction(mLongPressOnAssistBehavior);
                        mAssistKeyLongPressed = true;
                    }
                }
            } else {
                if (mAssistKeyLongPressed) {
                    mAssistKeyLongPressed = false;
                } else {
                    if (mRecentAppsPreloaded &&
                            mPressOnAssistBehavior != KEY_ACTION_APP_SWITCH) {
                        cancelPreloadRecentApps();
                    }
                    if (!keyguardOn) {
                        performKeyAction(mPressOnAssistBehavior);
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.KILL_APP_LONGPRESS_BACK, 0) == 1) {
                if (down && repeatCount == 0) {
                    mHandler.postDelayed(mBackLongPress, mBackKillTimeout);
                }
            }
        }

        // Shortcuts are invoked through Search+key, so intercept those here
        // Any printing key that is chorded with Search should be consumed
        // even if no shortcut was invoked.  This prevents text from being
        // inadvertently inserted when using a keyboard that has built-in macro
        // shortcut keys (that emit Search+x) and some of them are not registered.
        if (mSearchKeyShortcutPending) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                mConsumeSearchKeyUp = true;
                mSearchKeyShortcutPending = false;
                if (down && repeatCount == 0 && !keyguardOn) {
                    Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode, metaState);
                    if (shortcutIntent != null) {
                        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            mContext.startActivity(shortcutIntent);
                        } catch (ActivityNotFoundException ex) {
                            Slog.w(TAG, "Dropping shortcut key combination because "
                                    + "the activity to which it is registered was not found: "
                                    + "SEARCH+" + KeyEvent.keyCodeToString(keyCode), ex);
                        }
                    } else {
                        Slog.i(TAG, "Dropping unregistered shortcut key combination: "
                                + "SEARCH+" + KeyEvent.keyCodeToString(keyCode));
                    }
                }
                return -1;
            }
        }

        // Invoke shortcuts using Meta.
        if (down && repeatCount == 0 && !keyguardOn
                && (metaState & KeyEvent.META_META_ON) != 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode,
                        metaState & ~(KeyEvent.META_META_ON
                                | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON));
                if (shortcutIntent != null) {
                    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        mContext.startActivity(shortcutIntent);
                    } catch (ActivityNotFoundException ex) {
                        Slog.w(TAG, "Dropping shortcut key combination because "
                                + "the activity to which it is registered was not found: "
                                + "META+" + KeyEvent.keyCodeToString(keyCode), ex);
                    }
                    return -1;
                }
            }
        }

        // Handle application launch keys.
        if (down && repeatCount == 0 && !keyguardOn) {
            String category = sApplicationLaunchKeyCategories.get(keyCode);
            if (category != null) {
                Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    mContext.startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Slog.w(TAG, "Dropping application launch key because "
                            + "the activity to which it is registered was not found: "
                            + "keyCode=" + keyCode + ", category=" + category, ex);
                }
                return -1;
            }
        }

        // Display task switcher for ALT-TAB or Meta-TAB.
        if (down && repeatCount == 0 && keyCode == KeyEvent.KEYCODE_TAB) {
            if (mRecentAppsDialogHeldModifiers == 0 && !keyguardOn) {
                final int shiftlessModifiers = event.getModifiers() & ~KeyEvent.META_SHIFT_MASK;
                if (KeyEvent.metaStateHasModifiers(shiftlessModifiers, KeyEvent.META_ALT_ON)
                        || KeyEvent.metaStateHasModifiers(
                                shiftlessModifiers, KeyEvent.META_META_ON)) {
                    mRecentAppsDialogHeldModifiers = shiftlessModifiers;
                    showOrHideRecentAppsDialog(RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW);
                    return -1;
                }
            }
        } else if (!down && mRecentAppsDialogHeldModifiers != 0
                && (metaState & mRecentAppsDialogHeldModifiers) == 0) {
            mRecentAppsDialogHeldModifiers = 0;
            showOrHideRecentAppsDialog(keyguardOn ? RECENT_APPS_BEHAVIOR_DISMISS :
                    RECENT_APPS_BEHAVIOR_DISMISS_AND_SWITCH);
        }

        // Handle keyboard language switching.
        if (down && repeatCount == 0
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || (keyCode == KeyEvent.KEYCODE_SPACE
                                && (metaState & KeyEvent.META_CTRL_MASK) != 0))) {
            int direction = (metaState & KeyEvent.META_SHIFT_MASK) != 0 ? -1 : 1;
            mWindowManagerFuncs.switchKeyboardLayout(event.getDeviceId(), direction);
            return -1;
        }
        if (mLanguageSwitchKeyPressed && !down
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || keyCode == KeyEvent.KEYCODE_SPACE)) {
            mLanguageSwitchKeyPressed = false;
            return -1;
        }

        if (mDeviceKeyHandler != null) {
            try {
                return mDeviceKeyHandler.handleKeyEvent(event);
            } catch (Exception e) {
                Slog.d(TAG, "Could not dispatch event to device key handler", e);
            }
        }

        // Let the application handle the key.
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        // Note: This method is only called if the initial down was unhandled.
        if (DEBUG_INPUT) {
            Slog.d(TAG, "Unhandled key: win=" + win + ", action=" + event.getAction()
                    + ", flags=" + event.getFlags()
                    + ", keyCode=" + event.getKeyCode()
                    + ", scanCode=" + event.getScanCode()
                    + ", metaState=" + event.getMetaState()
                    + ", repeatCount=" + event.getRepeatCount()
                    + ", policyFlags=" + policyFlags);
        }

        KeyEvent fallbackEvent = null;
        if ((event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            final int keyCode = event.getKeyCode();
            final int metaState = event.getMetaState();
            final boolean initialDown = event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0;

            // Check for fallback actions specified by the key character map.
            final FallbackAction fallbackAction;
            if (initialDown) {
                fallbackAction = kcm.getFallbackAction(keyCode, metaState);
            } else {
                fallbackAction = mFallbackActions.get(keyCode);
            }

            if (fallbackAction != null) {
                if (DEBUG_INPUT) {
                    Slog.d(TAG, "Fallback: keyCode=" + fallbackAction.keyCode
                            + " metaState=" + Integer.toHexString(fallbackAction.metaState));
                }

                final int flags = event.getFlags() | KeyEvent.FLAG_FALLBACK;
                fallbackEvent = KeyEvent.obtain(
                        event.getDownTime(), event.getEventTime(),
                        event.getAction(), fallbackAction.keyCode,
                        event.getRepeatCount(), fallbackAction.metaState,
                        event.getDeviceId(), event.getScanCode(),
                        flags, event.getSource(), null);

                if (!interceptFallback(win, fallbackEvent, policyFlags)) {
                    fallbackEvent.recycle();
                    fallbackEvent = null;
                }

                if (initialDown) {
                    mFallbackActions.put(keyCode, fallbackAction);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    mFallbackActions.remove(keyCode);
                    fallbackAction.recycle();
                }
            }
        }

        if (DEBUG_INPUT) {
            if (fallbackEvent == null) {
                Slog.d(TAG, "No fallback.");
            } else {
                Slog.d(TAG, "Performing fallback: " + fallbackEvent);
            }
        }
        return fallbackEvent;
    }

    private boolean interceptFallback(WindowState win, KeyEvent fallbackEvent, int policyFlags) {
        int actions = interceptKeyBeforeQueueing(fallbackEvent, policyFlags, true);
        if ((actions & ACTION_PASS_TO_USER) != 0) {
            long delayMillis = interceptKeyBeforeDispatching(
                    win, fallbackEvent, policyFlags);
            if (delayMillis == 0) {
                return true;
            }
        }
        return false;
    }

    private void launchAssistLongPressAction() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);

        // launch the search activity
        Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // TODO: This only stops the factory-installed search manager.  
            // Need to formalize an API to handle others
            SearchManager searchManager = getSearchManager();
            if (searchManager != null) {
                searchManager.stopSearch();
            }
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "No activity to handle assist long press action.", e);
        }
    }

    private void launchAssistAction() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);
        Intent intent = SearchManager.getAssistIntent(mContext);
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Slog.w(TAG, "No activity to handle assist action.", e);
            }
        }
    }

    private SearchManager getSearchManager() {
        if (mSearchManager == null) {
            mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        }
        return mSearchManager;
    }

    /**
     * A home key -> launch home action was detected.  Take the appropriate action
     * given the situation with the keyguard.
     */
    void launchHomeFromHotKey() {
        if (mKeyguardMediator != null && mKeyguardMediator.isShowingAndNotHidden()) {
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

    /**
     * A delayed callback use to determine when it is okay to re-allow applications
     * to use certain system UI flags.  This is used to prevent applications from
     * spamming system UI changes that prevent the navigation bar from being shown.
     */
    final Runnable mAllowSystemUiDelay = new Runnable() {
        @Override public void run() {
        }
    };

    /**
     * Input handler used while nav bar is hidden.  Captures any touch on the screen,
     * to determine when the nav bar should be shown and prevent applications from
     * receiving those touches.
     */
    final class HideNavInputEventReceiver extends InputEventReceiver {
        public HideNavInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        // When the user taps down, we re-show the nav bar.
                        boolean changed = false;
                        synchronized (mLock) {
                            // Any user activity always causes us to show the
                            // navigation controls, if they had been hidden.
                            // We also clear the low profile and only content
                            // flags so that tapping on the screen will atomically
                            // restore all currently hidden screen decorations.
                            int newVal = mResettingSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                    View.SYSTEM_UI_FLAG_LOW_PROFILE |
                                    View.SYSTEM_UI_FLAG_FULLSCREEN;
                            if (mResettingSystemUiFlags != newVal) {
                                mResettingSystemUiFlags = newVal;
                                changed = true;
                            }
                            // We don't allow the system's nav bar to be hidden
                            // again for 1 second, to prevent applications from
                            // spamming us and keeping it from being shown.
                            newVal = mForceClearedSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            if (mForceClearedSystemUiFlags != newVal) {
                                mForceClearedSystemUiFlags = newVal;
                                changed = true;
                                mHandler.postDelayed(new Runnable() {
                                    @Override public void run() {
                                        synchronized (mLock) {
                                            // Clear flags.
                                            mForceClearedSystemUiFlags &=
                                                    ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                                        }
                                        mWindowManagerFuncs.reevaluateStatusBarVisibility();
                                    }
                                }, 1000);
                            }
                        }
                        if (changed) {
                            mWindowManagerFuncs.reevaluateStatusBarVisibility();
                        }
                    }
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }
    final InputEventReceiver.Factory mHideNavInputEventReceiverFactory =
            new InputEventReceiver.Factory() {
        @Override
        public InputEventReceiver createInputEventReceiver(
                InputChannel inputChannel, Looper looper) {
            return new HideNavInputEventReceiver(inputChannel, looper);
        }
    };

    @Override
    public int adjustSystemUiVisibilityLw(int visibility) {
        // Reset any bits in mForceClearingStatusBarVisibility that
        // are now clear.
        mResettingSystemUiFlags &= visibility;
        // Clear any bits in the new visibility that are currently being
        // force cleared, before reporting it.
        return visibility & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
    }

    public void getContentInsetHintLw(WindowManager.LayoutParams attrs, Rect contentInset) {
        final int fl = attrs.flags;
        final int systemUiVisibility = (attrs.systemUiVisibility|attrs.subtreeSystemUiVisibility);

        if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
            int availRight, availBottom;
            if (mCanHideNavigationBar &&
                    (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                availRight = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                availBottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            } else {
                availRight = mRestrictedScreenLeft + mRestrictedScreenWidth;
                availBottom = mRestrictedScreenTop + mRestrictedScreenHeight;
            }
            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                if ((fl & FLAG_FULLSCREEN) != 0) {
                    contentInset.set(mStableFullscreenLeft, mStableFullscreenTop,
                            availRight - mStableFullscreenRight,
                            availBottom - mStableFullscreenBottom);
                } else {
                    contentInset.set(mStableLeft, mStableTop,
                            availRight - mStableRight, availBottom - mStableBottom);
                }
            } else if ((fl & FLAG_FULLSCREEN) != 0) {
                contentInset.setEmpty();
            } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                contentInset.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            } else {
                contentInset.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            }
            return;
        }
        contentInset.setEmpty();
    }

    /** {@inheritDoc} */
    public void beginLayoutLw(int displayWidth, int displayHeight, int displayRotation) {
        mUnrestrictedScreenLeft = mUnrestrictedScreenTop = 0;
        mUnrestrictedScreenWidth = displayWidth;
        mUnrestrictedScreenHeight = displayHeight;
        mRestrictedScreenLeft = mRestrictedScreenTop = 0;
        mRestrictedScreenWidth = displayWidth;
        mRestrictedScreenHeight = displayHeight;
        mDockLeft = mContentLeft = mStableLeft = mStableFullscreenLeft
                = mSystemLeft = mCurLeft = 0;
        mDockTop = mContentTop = mStableTop = mStableFullscreenTop
                = mSystemTop = mCurTop = 0;
        mDockRight = mContentRight = mStableRight = mStableFullscreenRight
                = mSystemRight = mCurRight = displayWidth;
        mDockBottom = mContentBottom = mStableBottom = mStableFullscreenBottom
                = mSystemBottom = mCurBottom = displayHeight;
        mDockLayer = 0x10000000;
        mStatusBarLayer = -1;

        // start with the current dock rect, which will be (0,0,displayWidth,displayHeight)
        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect vf = mTmpVisibleFrame;
        pf.left = df.left = vf.left = mDockLeft;
        pf.top = df.top = vf.top = mDockTop;
        pf.right = df.right = vf.right = mDockRight;
        pf.bottom = df.bottom = vf.bottom = mDockBottom;

        // For purposes of putting out fake window up to steal focus, we will
        // drive nav being hidden only by whether it is requested.
        boolean navVisible = (mLastSystemUiFlags&View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;

        // When the navigation bar isn't visible, we put up a fake
        // input window to catch all touch events.  This way we can
        // detect when the user presses anywhere to bring back the nav
        // bar and ensure the application doesn't see the event.
        if (navVisible) {
            if (mHideNavFakeWindow != null) {
                mHideNavFakeWindow.dismiss();
                mHideNavFakeWindow = null;
            }
        } else if (mHideNavFakeWindow == null) {
            mHideNavFakeWindow = mWindowManagerFuncs.addFakeWindow(
                    mHandler.getLooper(), mHideNavInputEventReceiverFactory,
                    "hidden nav", WindowManager.LayoutParams.TYPE_HIDDEN_NAV_CONSUMER,
                    0, false, false, true);
        }

        // For purposes of positioning and showing the nav bar, if we have
        // decided that it can't be hidden (because of the screen aspect ratio),
        // then take that into account.
        navVisible |= !mCanHideNavigationBar;
        navVisible &= (Settings.System.getInt(mContext.getContentResolver(), Settings.System.EXPANDED_DESKTOP_STATE, 0) == 0);

        if (mNavigationBar != null) {
            // Force the navigation bar to its appropriate place and
            // size.  We need to do this directly, instead of relying on
            // it to bubble up from the nav bar, because this needs to
            // change atomically with screen rotations.
            mNavigationBarOnBottom = (!mNavigationBarCanMove || displayWidth < displayHeight);
            if (mNavigationBarOnBottom) {
                // It's a system nav bar or a portrait screen; nav bar goes on bottom.
                int top = displayHeight - mNavigationBarHeightForRotation[displayRotation];
                if (mHdmiPlugged) {
                    // Move the nav bar up if the external display is the same aspect ratio
                    // but shorter.  This avoids clipping on the external display.
                    boolean sameAspect = mExternalDisplayHeight > 0 && displayHeight > 0
                        && ((float) mExternalDisplayWidth / mExternalDisplayHeight > 1)
                        == ((float) displayWidth / displayHeight > 1);
                    if (sameAspect && top > mExternalDisplayHeight) {
                        top = mExternalDisplayHeight;
                    }
                }
                mTmpNavigationFrame.set(0, top, displayWidth, displayHeight);
                mStableBottom = mStableFullscreenBottom = mTmpNavigationFrame.top;
                if (navVisible) {
                    mNavigationBar.showLw(true);
                    mDockBottom = mTmpNavigationFrame.top;
                    mRestrictedScreenHeight = mDockBottom - mDockTop;
                } else {
                    // We currently want to hide the navigation UI.
                    mNavigationBar.hideLw(true);
                }
                if (navVisible && !mNavigationBar.isAnimatingLw()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemBottom = mTmpNavigationFrame.top;
                }
            } else {
                // Landscape screen; nav bar goes to the right.
                int left = displayWidth - mNavigationBarWidthForRotation[displayRotation];
                if (mHdmiPlugged) {
                    if (left > mExternalDisplayWidth) {
                        left = mExternalDisplayWidth;
                    }
                }
                mTmpNavigationFrame.set(left, 0, displayWidth, displayHeight);
                mStableRight = mStableFullscreenRight = mTmpNavigationFrame.left;
                if (navVisible) {
                    mNavigationBar.showLw(true);
                    mDockRight = mTmpNavigationFrame.left;
                    mRestrictedScreenWidth = mDockRight - mDockLeft;
                } else {
                    // We currently want to hide the navigation UI.
                    mNavigationBar.hideLw(true);
                }
                if (navVisible && !mNavigationBar.isAnimatingLw()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemRight = mTmpNavigationFrame.left;
                }
            }
            // Make sure the content and current rectangles are updated to
            // account for the restrictions from the navigation bar.
            mContentTop = mCurTop = mDockTop;
            mContentBottom = mCurBottom = mDockBottom;
            mContentLeft = mCurLeft = mDockLeft;
            mContentRight = mCurRight = mDockRight;
            mStatusBarLayer = mNavigationBar.getSurfaceLayer();
            // And compute the final frame.
            mNavigationBar.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame,
                    mTmpNavigationFrame, mTmpNavigationFrame);
            if (DEBUG_LAYOUT) Log.i(TAG, "mNavigationBar frame: " + mTmpNavigationFrame);
        }
        if (DEBUG_LAYOUT) Log.i(TAG, String.format("mDock rect: (%d,%d - %d,%d)",
                mDockLeft, mDockTop, mDockRight, mDockBottom));

        // decide where the status bar goes ahead of time
        if (mStatusBar != null) {
            // apply any navigation bar insets
            pf.left = df.left = mUnrestrictedScreenLeft;
            pf.top = df.top = mUnrestrictedScreenTop;
            pf.right = df.right = mUnrestrictedScreenWidth - mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = mUnrestrictedScreenHeight - mUnrestrictedScreenTop;
            vf.left = mStableLeft;
            vf.top = mStableTop;
            vf.right = mStableRight;
            vf.bottom = mStableBottom;

            mStatusBarLayer = mStatusBar.getSurfaceLayer();

            // Let the status bar determine its size.
            mStatusBar.computeFrameLw(pf, df, vf, vf);

            // For layout, the status bar is always at the top with our fixed height.
            mStableTop = mUnrestrictedScreenTop + mStatusBarHeight;

            // If the status bar is hidden, we don't want to cause
            // windows behind it to scroll.
            if (mStatusBar.isVisibleLw()) {
                // Status bar may go away, so the screen area it occupies
                // is available to apps but just covering them when the
                // status bar is visible.
                mDockTop = mUnrestrictedScreenTop + mStatusBarHeight;
                
                mContentTop = mCurTop = mDockTop;
                mContentBottom = mCurBottom = mDockBottom;
                mContentLeft = mCurLeft = mDockLeft;
                mContentRight = mCurRight = mDockRight;

                if (DEBUG_LAYOUT) Log.v(TAG, "Status bar: " +
                    String.format(
                        "dock=[%d,%d][%d,%d] content=[%d,%d][%d,%d] cur=[%d,%d][%d,%d]",
                        mDockLeft, mDockTop, mDockRight, mDockBottom,
                        mContentLeft, mContentTop, mContentRight, mContentBottom,
                        mCurLeft, mCurTop, mCurRight, mCurBottom));
            }
            if (mStatusBar.isVisibleLw() && !mStatusBar.isAnimatingLw()) {
                // If the status bar is currently requested to be visible,
                // and not in the process of animating on or off, then
                // we can tell the app that it is covered by it.
                mSystemTop = mUnrestrictedScreenTop + mStatusBarHeight;
            }
        }
    }

    /** {@inheritDoc} */
    public int getSystemDecorRectLw(Rect systemRect) {
        systemRect.left = mSystemLeft;
        systemRect.top = mSystemTop;
        systemRect.right = mSystemRight;
        systemRect.bottom = mSystemBottom;
        if (mStatusBar != null) return mStatusBar.getSurfaceLayer();
        if (mNavigationBar != null) return mNavigationBar.getSurfaceLayer();
        return 0;
    }

    void setAttachedWindowFrames(WindowState win, int fl, int adjust,
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
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
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

    private void applyStableConstraints(int sysui, int fl, Rect r) {
        if ((sysui & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
            // If app is requesting a stable layout, don't let the
            // content insets go below the stable values.
            if ((fl & FLAG_FULLSCREEN) != 0) {
                if (r.left < mStableFullscreenLeft) r.left = mStableFullscreenLeft;
                if (r.top < mStableFullscreenTop) r.top = mStableFullscreenTop;
                if (r.right > mStableFullscreenRight) r.right = mStableFullscreenRight;
                if (r.bottom > mStableFullscreenBottom) r.bottom = mStableFullscreenBottom;
            } else {
                if (r.left < mStableLeft) r.left = mStableLeft;
                if (r.top < mStableTop) r.top = mStableTop;
                if (r.right > mStableRight) r.right = mStableRight;
                if (r.bottom > mStableBottom) r.bottom = mStableBottom;
            }
        }
    }

    /** {@inheritDoc} */
    public void layoutWindowLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached) {
        // we've already done the status bar
        if (win == mStatusBar || win == mNavigationBar) {
            return;
        }
        final boolean needsToOffsetInputMethodTarget =
                (win == mLastInputMethodTargetWindow && mLastInputMethodWindow != null);
        if (needsToOffsetInputMethodTarget) {
            if (DEBUG_LAYOUT) {
                Slog.i(TAG, "Offset ime target window by the last ime window state");
            }
            offsetInputMethodWindowLw(mLastInputMethodWindow);
        }

        final int fl = attrs.flags;
        final int sim = attrs.softInputMode;
        final int sysUiFl = win.getSystemUiVisibility();

        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect cf = mTmpContentFrame;
        final Rect vf = mTmpVisibleFrame;
        
        final boolean hasNavBar = (mHasNavigationBar 
                && mNavigationBar != null && mNavigationBar.isVisibleLw());

        if (attrs.type == TYPE_INPUT_METHOD) {
            pf.left = df.left = cf.left = vf.left = mDockLeft;
            pf.top = df.top = cf.top = vf.top = mDockTop;
            pf.right = df.right = cf.right = vf.right = mDockRight;
            pf.bottom = df.bottom = cf.bottom = vf.bottom = mDockBottom;
            // IM dock windows always go to the bottom of the screen.
            attrs.gravity = Gravity.BOTTOM;
            mDockLayer = win.getSurfaceLayer();
        } else {
            final int adjust = sim & SOFT_INPUT_MASK_ADJUST;

            if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_FULLSCREEN | FLAG_LAYOUT_INSET_DECOR))
                    == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)
                    && (sysUiFl & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                if (DEBUG_LAYOUT)
                    Log.v(TAG, "layoutWindowLw(" + attrs.getTitle() 
                            + "): IN_SCREEN, INSET_DECOR, !FULLSCREEN");
                // This is the case for a normal activity window: we want it
                // to cover all of the screen space, and it can take care of
                // moving its contents to account for screen decorations that
                // intrude into that space.
                if (attached != null) {
                    // If this window is attached to another, our display
                    // frame is the same as the one we are attached to.
                    setAttachedWindowFrames(win, fl, sim, attached, true, pf, df, cf, vf);
                } else {
                    if (attrs.type == TYPE_STATUS_BAR_PANEL
                            || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                        // Status bar panels are the only windows who can go on top of
                        // the status bar.  They are protected by the STATUS_BAR_SERVICE
                        // permission, so they have the same privileges as the status
                        // bar itself.
                        //
                        // However, they should still dodge the navigation bar if it exists.

                        pf.left = df.left = hasNavBar ? mDockLeft : mUnrestrictedScreenLeft;
                        pf.top = df.top = mUnrestrictedScreenTop;
                        pf.right = df.right = hasNavBar
                                            ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                            : mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                        pf.bottom = df.bottom = hasNavBar
                                              ? mRestrictedScreenTop+mRestrictedScreenHeight
                                              : mUnrestrictedScreenTop+mUnrestrictedScreenHeight;

                        if (DEBUG_LAYOUT) {
                            Log.v(TAG, String.format(
                                        "Laying out status bar window: (%d,%d - %d,%d)",
                                        pf.left, pf.top, pf.right, pf.bottom));
                        }
                    } else if (mCanHideNavigationBar
                            && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking for layout as if the nav bar is hidden, lets the
                        // application extend into the unrestricted screen area.  We
                        // only do this for application windows to ensure no window that
                        // can be above the nav bar can do this.
                        pf.left = df.left = mUnrestrictedScreenLeft;
                        pf.top = df.top = mUnrestrictedScreenTop;
                        pf.right = df.right = mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                        pf.bottom = df.bottom = mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                    } else {
                        pf.left = df.left = mRestrictedScreenLeft;
                        pf.top = df.top = mRestrictedScreenTop;
                        pf.right = df.right = mRestrictedScreenLeft+mRestrictedScreenWidth;
                        pf.bottom = df.bottom = mRestrictedScreenTop+mRestrictedScreenHeight;
                    }
                    if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
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
                    applyStableConstraints(sysUiFl, fl, cf);
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            } else if ((fl & FLAG_LAYOUT_IN_SCREEN) != 0 || (sysUiFl
                    & (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)) != 0) {
                if (DEBUG_LAYOUT)
                    Log.v(TAG, "layoutWindowLw(" + attrs.getTitle() + "): IN_SCREEN");
                // A window that has requested to fill the entire screen just
                // gets everything, period.
                if (attrs.type == TYPE_STATUS_BAR_PANEL
                        || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                    pf.left = df.left = cf.left = hasNavBar ? mDockLeft : mUnrestrictedScreenLeft;
                    pf.top = df.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = cf.right = hasNavBar
                                        ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                        : mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = cf.bottom = hasNavBar
                                          ? mRestrictedScreenTop+mRestrictedScreenHeight
                                          : mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) {
                        Log.v(TAG, String.format(
                                    "Laying out IN_SCREEN status bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                    }
                } else if (attrs.type == TYPE_NAVIGATION_BAR
                        || attrs.type == TYPE_NAVIGATION_BAR_PANEL) {
                    // The navigation bar has Real Ultimate Power.
                    pf.left = df.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = mUnrestrictedScreenTop;
                    pf.right = df.right = mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) {
                        Log.v(TAG, String.format(
                                    "Laying out navigation bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                    }
                } else if ((attrs.type == TYPE_SECURE_SYSTEM_OVERLAY
                                || attrs.type == TYPE_BOOT_PROGRESS)
                        && ((fl & FLAG_FULLSCREEN) != 0)) {
                    // Fullscreen secure system overlays get what they ask for.
                    pf.left = df.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = mUnrestrictedScreenTop;
                    pf.right = df.right = mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                } else if (attrs.type == TYPE_BOOT_PROGRESS) {
                    // Boot progress screen always covers entire display.
                    pf.left = df.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = cf.right = mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = cf.bottom
                            = mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                } else if (mCanHideNavigationBar
                        && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                        && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                        && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                    // Asking for layout as if the nav bar is hidden, lets the
                    // application extend into the unrestricted screen area.  We
                    // only do this for application windows to ensure no window that
                    // can be above the nav bar can do this.
                    // XXX This assumes that an app asking for this will also
                    // ask for layout in only content.  We can't currently figure out
                    // what the screen would be if only laying out to hide the nav bar.
                    pf.left = df.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = cf.right = mUnrestrictedScreenLeft+mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = cf.bottom
                            = mUnrestrictedScreenTop+mUnrestrictedScreenHeight;
                } else {
                    pf.left = df.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = cf.right = mRestrictedScreenLeft+mRestrictedScreenWidth;
                    pf.bottom = df.bottom = cf.bottom
                            = mRestrictedScreenTop+mRestrictedScreenHeight;
                }
                applyStableConstraints(sysUiFl, fl, cf);
                if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                    vf.left = mCurLeft;
                    vf.top = mCurTop;
                    vf.right = mCurRight;
                    vf.bottom = mCurBottom;
                } else {
                    vf.set(cf);
                }
            } else if (attached != null) {
                if (DEBUG_LAYOUT)
                    Log.v(TAG, "layoutWindowLw(" + attrs.getTitle() + "): attached to " + attached);
                // A child window should be placed inside of the same visible
                // frame that its parent had.
                setAttachedWindowFrames(win, fl, adjust, attached, false, pf, df, cf, vf);
            } else {
                if (DEBUG_LAYOUT)
                    Log.v(TAG, "layoutWindowLw(" + attrs.getTitle() + "): normal window");
                // Otherwise, a normal window must be placed inside the content
                // of all screen decorations.
                if (attrs.type == TYPE_STATUS_BAR_PANEL) {
                    // Status bar panels are the only windows who can go on top of
                    // the status bar.  They are protected by the STATUS_BAR_SERVICE
                    // permission, so they have the same privileges as the status
                    // bar itself.
                    pf.left = df.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = cf.right = mRestrictedScreenLeft+mRestrictedScreenWidth;
                    pf.bottom = df.bottom = cf.bottom
                            = mRestrictedScreenTop+mRestrictedScreenHeight;
                } else {
                    pf.left = mContentLeft;
                    pf.top = mContentTop;
                    pf.right = mContentRight;
                    pf.bottom = mContentBottom;
                    if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
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
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            }
        }
        
        if ((fl & FLAG_LAYOUT_NO_LIMITS) != 0) {
            df.left = df.top = cf.left = cf.top = vf.left = vf.top = -10000;
            df.right = df.bottom = cf.right = cf.bottom = vf.right = vf.bottom = 10000;
        }

        if (DEBUG_LAYOUT) Log.v(TAG, "Compute frame " + attrs.getTitle()
                + ": sim=#" + Integer.toHexString(sim)
                + " attach=" + attached + " type=" + attrs.type 
                + String.format(" flags=0x%08x", fl)
                + " pf=" + pf.toShortString() + " df=" + df.toShortString()
                + " cf=" + cf.toShortString() + " vf=" + vf.toShortString());
        
        win.computeFrameLw(pf, df, cf, vf);
        
        // Dock windows carve out the bottom of the screen, so normal windows
        // can't appear underneath them.
        if (attrs.type == TYPE_INPUT_METHOD && !win.getGivenInsetsPendingLw()) {
            setLastInputMethodWindowLw(null, null);
            offsetInputMethodWindowLw(win);
        }
    }

    private void offsetInputMethodWindowLw(WindowState win) {
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

    /** {@inheritDoc} */
    @Override
    public void finishLayoutLw() {
        return;
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
        if (DEBUG_LAYOUT) Slog.i(TAG, "Win " + win + ": isVisibleOrBehindKeyguardLw="
                + win.isVisibleOrBehindKeyguardLw());
        if (mTopFullscreenOpaqueWindowState == null &&
                win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw()) {
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
        boolean topIsFullscreen = false;

        final WindowManager.LayoutParams lp = (mTopFullscreenOpaqueWindowState != null)
                ? mTopFullscreenOpaqueWindowState.getAttrs()
                : null;

        if (mStatusBar != null) {
            if (DEBUG_LAYOUT) Log.i(TAG, "force=" + mForceStatusBar
                    + " top=" + mTopFullscreenOpaqueWindowState);
            if (mForceStatusBar) {
                if (DEBUG_LAYOUT) Log.v(TAG, "Showing status bar: forced");
                if (mStatusBar.showLw(true)) changes |= FINISH_LAYOUT_REDO_LAYOUT;
            } else if (mTopFullscreenOpaqueWindowState != null) {
                if (localLOGV) {
                    Log.d(TAG, "frame: " + mTopFullscreenOpaqueWindowState.getFrameLw()
                            + " shown frame: " + mTopFullscreenOpaqueWindowState.getShownFrameLw());
                    Log.d(TAG, "attr: " + mTopFullscreenOpaqueWindowState.getAttrs()
                            + " lp.flags=0x" + Integer.toHexString(lp.flags));
                }
                topIsFullscreen = (lp.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
                        || (mLastSystemUiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                // The subtle difference between the window for mTopFullscreenOpaqueWindowState
                // and mTopIsFullscreen is that that mTopIsFullscreen is set only if the window
                // has the FLAG_FULLSCREEN set.  Not sure if there is another way that to be the
                // case though.
                if (topIsFullscreen || Settings.System.getInt(mContext.getContentResolver(), Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1) {
                    if (DEBUG_LAYOUT) Log.v(TAG, "** HIDING status bar");
                    if (mStatusBar.hideLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;

                        mHandler.post(new Runnable() { public void run() {
                            try {
                                IStatusBarService statusbar = getStatusBarService();
                                if (statusbar != null) {
                                    statusbar.collapse();
                                }
                            } catch (RemoteException ex) {
                                // re-acquire status bar service next time it is needed.
                                mStatusBarService = null;
                            }
                        }});
                    } else if (DEBUG_LAYOUT) {
                        Log.v(TAG, "Preventing status bar from hiding by policy");
                    }
                } else {
                    if (DEBUG_LAYOUT) Log.v(TAG, "** SHOWING status bar: top is not fullscreen");
                    if (mStatusBar.showLw(true)) changes |= FINISH_LAYOUT_REDO_LAYOUT;
                }
            }
        }

        mTopIsFullscreen = topIsFullscreen;

        // Hide the key guard if a visible window explicitly specifies that it wants to be displayed
        // when the screen is locked
        if (mKeyguard != null) {
            if (localLOGV) Log.v(TAG, "finishAnimationLw::mHideKeyguard="+mHideLockScreen);
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

        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            changes |= FINISH_LAYOUT_REDO_LAYOUT;
        }

        // update since mAllowLockscreenWhenOn might have changed
        updateLockScreenTimeout();
        return changes;
    }

    public boolean allowAppAnimationsLw() {
        if (mKeyguard != null && mKeyguard.isVisibleLw() && !mKeyguard.isAnimatingLw()) {
            // If keyguard is currently visible, no reason to animate
            // behind it.
            return false;
        }
        return true;
    }

    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        mFocusedWindow = newFocus;
        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            return FINISH_LAYOUT_REDO_LAYOUT;
        }
        return 0;
    }

    /** {@inheritDoc} */
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        // do nothing if headless
        if (mHeadless) return;

        // lid changed state
        final int newLidState = lidOpen ? LID_OPEN : LID_CLOSED;
        if (newLidState == mLidState) {
            return;
        }

        mLidState = newLidState;
        applyLidSwitchState();
        updateRotation(true);

        if (lidOpen) {
            if (keyguardIsShowingTq()) {
                mKeyguardMediator.onWakeKeyWhenKeyguardShowingTq(
                        KeyEvent.KEYCODE_POWER, mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED);
            } else {
                mPowerManager.userActivity(SystemClock.uptimeMillis(), false,
                        LocalPowerManager.BUTTON_EVENT);
            }
        } else if (!mLidControlsSleep) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false,
                    LocalPowerManager.OTHER_EVENT);
        }
    }

    void setHdmiPlugged(boolean plugged) {
        if (mHdmiPlugged != plugged) {
            mHdmiPlugged = plugged;
            if (plugged && mDisplay != null) {
                mExternalDisplayWidth = mDisplay.getRawExternalWidth();
                mExternalDisplayHeight = mDisplay.getRawExternalHeight();
            }
            updateRotation(true, true);
            Intent intent = new Intent(ACTION_HDMI_PLUGGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(EXTRA_HDMI_PLUGGED_STATE, plugged);
            mContext.sendStickyBroadcast(intent);
        }
    }

    void initializeHdmiState() {
        boolean plugged = false;
        // watch for HDMI plug messages if the hdmi switch exists
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            mHDMIObserver.startObserving("DEVPATH=/devices/virtual/switch/hdmi");

            final String filename = "/sys/class/switch/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }
        // This dance forces the code in setHdmiPlugged to run.
        // Always do this so the sticky intent is stuck (to false) if there is no hdmi.
        mHdmiPlugged = !plugged;
        setHdmiPlugged(!mHdmiPlugged);
    }

    /** {@inheritDoc} */
    public void notifyJackSwitchChanged(long whenNanos, int switchCode, boolean switchState) {
        Slog.d(TAG,"notifyJackSwitchChanged(): switchCode "+switchCode+" switchState "+switchState);
        synchronized(mHeadsetLock) {
            if(switchCode == SW_ANC_INSERT) {
                if(switchState) {
                    mIsAncOn = true;
                    if((mHeadsetJackState & BIT_HEADSET) != 0) {
                        mHeadsetJackState &= ~BIT_HEADSET;
                        mHeadsetJackState |= BIT_ANC_HEADSET;
                    }
                    if((mHeadsetJackState & BIT_HEADSET_SPEAKER_ONLY) != 0) {
                        mHeadsetJackState &= ~BIT_HEADSET_SPEAKER_ONLY;
                        mHeadsetJackState |= BIT_ANC_HEADSET_SPEAKER_ONLY;
                    }
                    if((mHeadsetJackState & BIT_HEADSET_MIC_ONLY) != 0) {
                        mHeadsetJackState &= ~BIT_HEADSET_MIC_ONLY;
                        mHeadsetJackState |= BIT_ANC_HEADSET_MIC_ONLY;
                    }
                } else {
                    mIsAncOn = false;
                    if((mHeadsetJackState & BIT_ANC_HEADSET) != 0) {
                        mHeadsetJackState &= ~BIT_ANC_HEADSET;
                        mHeadsetJackState |= BIT_HEADSET;
                    }
                    if((mHeadsetJackState & BIT_ANC_HEADSET_SPEAKER_ONLY) != 0) {
                        mHeadsetJackState &= ~BIT_ANC_HEADSET_SPEAKER_ONLY;
                        mHeadsetJackState |= BIT_HEADSET_SPEAKER_ONLY;
                    }
                    if((mHeadsetJackState & BIT_ANC_HEADSET_MIC_ONLY) != 0) {
                        mHeadsetJackState &= ~BIT_ANC_HEADSET_MIC_ONLY;
                        mHeadsetJackState |= BIT_HEADSET_MIC_ONLY;
                    }
                }
            } else if ( switchCode == SW_HEADPHONE_INSERT) {
                if (switchState) {
                    if(mIsAncOn) {
                        mHeadsetJackState |= BIT_ANC_HEADSET_SPEAKER_ONLY;
                    } else {
                        mHeadsetJackState |= BIT_HEADSET_SPEAKER_ONLY;
                    }
                } else if( (mHeadsetJackState & BIT_HEADSET) != 0 ) {
                    mHeadsetJackState &= ~BIT_HEADSET;
                    mHeadsetJackState |= BIT_HEADSET_MIC_ONLY;
                } else if( (mHeadsetJackState & BIT_ANC_HEADSET) != 0 ) {
                    mHeadsetJackState &= ~BIT_ANC_HEADSET;
                    mHeadsetJackState |= BIT_ANC_HEADSET_MIC_ONLY;
                } else {
                    if(mIsAncOn) {
                        mHeadsetJackState &= ~BIT_ANC_HEADSET_SPEAKER_ONLY;
                    } else {
                        mHeadsetJackState &= ~BIT_HEADSET_SPEAKER_ONLY;
                    }
                }
            } else if ( switchCode == SW_MICROPHONE_INSERT) {
                if (switchState) {
                    if(mIsAncOn) {
                        mHeadsetJackState |= BIT_ANC_HEADSET_MIC_ONLY;
                    } else {
                        mHeadsetJackState |= BIT_HEADSET_MIC_ONLY;
                    }
                } else if( (mHeadsetJackState & BIT_HEADSET) != 0 ) {
                    mHeadsetJackState &= ~BIT_HEADSET;
                    mHeadsetJackState |= BIT_HEADSET_SPEAKER_ONLY;
                } else if( (mHeadsetJackState & BIT_ANC_HEADSET) != 0 ) {
                    mHeadsetJackState &= ~BIT_ANC_HEADSET;
                    mHeadsetJackState |= BIT_ANC_HEADSET_SPEAKER_ONLY;
                } else {
                    if(mIsAncOn) {
                        mHeadsetJackState &= ~BIT_ANC_HEADSET_MIC_ONLY;
                    } else {
                        mHeadsetJackState &= ~BIT_HEADSET_MIC_ONLY;
                    }
                }
            }

            if( mHeadsetJackState == SW_HEADSET_INSERT ) {
                // If both Speaker and Mic bits are set, remove them and
                // set Headset bit to indicate Headset insertion
                mHeadsetJackState = BIT_HEADSET;
            }

            if( mHeadsetJackState == SW_ANC_HEADSET_INSERT ) {
                mHeadsetJackState = BIT_ANC_HEADSET;
            }
            update();
        } // synchronized(mHeadsetLock)
    }

    private final void update() {

        // Retain only relevant bits
        int headsetState = mHeadsetJackState & SUPPORTED_HEADSETS;
        // Set default delay to 10msec to allow all the events to reach before sending intent
        int delay = 10;
        // reject all suspect transitions: only accept state changes from:
        // - a: 0 heaset to 1 headset
        // - b: 1 headset to 0 headset
        if (mCurHeadsetState == headsetState) {
            return;
        }

        if (headsetState == 0) {
            Intent intent = new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            mContext.sendBroadcast(intent);
            // It can take hundreds of ms flush the audio pipeline after
            // apps pause audio playback, but audio route changes are
            // immediate, so delay the route change by 1000ms.
            // This could be improved once the audio sub-system provides an
            // interface to clear the audio pipeline.
            delay = 1000;
        } else {
            // Insert the same delay for headset connection so that the connection event is not
            // broadcast before the disconnection event in case of fast removal/insertion
            if ( mIntentHandler.hasMessages(0)) {
                delay = 1000;
            }
        }
        mBroadcastWakeLock.acquire();
        Slog.d(TAG,"update(): sending Message to IntentHander with delay of "+delay);
        mIntentHandler.sendMessageDelayed( mIntentHandler.obtainMessage(0, mHeadsetName),
                                           delay);
    }

    private synchronized final void sendIntents(String headsetName) {

        Slog.d(TAG,"sendIntents(): mHeadsetJackState "+mHeadsetJackState+" mCurHeadsetState "+mCurHeadsetState+" mPrevHeadsetState "+mPrevHeadsetState);
        int headsetState;

        synchronized(mHeadsetLock) {
            headsetState = mHeadsetJackState & SUPPORTED_HEADSETS;
            if (mCurHeadsetState == headsetState) {
                return;
            }
            mPrevHeadsetState = mCurHeadsetState;
            mCurHeadsetState  = headsetState;
        }

        int allHeadsets = SUPPORTED_HEADSETS;
        //Handle unplug events first and then handle plug-in events
        for (int curHeadset = 1; curHeadset < SUPPORTED_HEADSETS; curHeadset <<= 1) {
            if (((headsetState & curHeadset) == 0) && ((mPrevHeadsetState & curHeadset) == curHeadset)) {
                if ((curHeadset & allHeadsets) != 0) {
                    sendIntent(curHeadset, headsetState, mPrevHeadsetState, headsetName);
                    allHeadsets &= ~curHeadset;
                }
            }
        }

        for (int curHeadset = 1; curHeadset < SUPPORTED_HEADSETS; curHeadset <<= 1) {
            if (((headsetState & curHeadset) == curHeadset) && ((mPrevHeadsetState & curHeadset) == 0)) {
                if ((curHeadset & allHeadsets) != 0) {
                    sendIntent(curHeadset, headsetState, mPrevHeadsetState, headsetName);
                    allHeadsets &= ~curHeadset;
                }
            }
        }
    }

    private final void sendIntent(int headset, int headsetState, int prevHeadsetState, String headsetName) {

        if ((headsetState & headset) != (prevHeadsetState & headset)) {
            //  Pack up the values and broadcast them to everyone
            int state = 0;
            int device = 0;


            if ((headset & BIT_HEADSET) != 0) {
                device = AudioManager.DEVICE_OUT_WIRED_HEADSET;
            } else if ((headset & BIT_HEADSET_SPEAKER_ONLY) != 0) {
                device = AudioManager.DEVICE_OUT_WIRED_HEADPHONE;
            } else if ((headset & BIT_HEADSET_MIC_ONLY ) != 0) {
                device = 0;
            } else if ((headset & BIT_ANC_HEADSET) != 0) {
                device = 0;//AudioManager.DEVICE_OUT_ANC_HEADSET;
            } else if ((headset & BIT_ANC_HEADSET_SPEAKER_ONLY) != 0) {
                device = 0;//AudioManager.DEVICE_OUT_ANC_HEADPHONE;
            } else if ((headset & BIT_ANC_HEADSET_MIC_ONLY) != 0) {
                device = 0;
            } else {
                Slog.e(TAG, "setDeviceState() invalid headset type: "+headset);
                return;
            }

            if ((headsetState & headset) != 0) {
                state = 1;
            } else {
                state = 0;
            }

            if (mSystemBooted) {
                final AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) {
                    Log.e(TAG, "sendIntent: couldn't get AudioManager reference");
                    return;
                }
                Slog.d(TAG,"sendIntent(): device "+device+" state "+state);
                am.setWiredDeviceConnectionState(device, state, headsetName);
            } else {
                Slog.d(TAG,"system not booted yet, call setWiredDeviceConnectionState later");
            }
        }
    }

    private final Handler mIntentHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        Slog.d(TAG,"Headset detect: Inside handleMessage() for IntentHandler" + (String)msg.obj);
            if (mSystemBooted) {
            sendIntents((String)msg.obj);
            } else {
                Log.e(TAG, "system not booted yet, send headset intent later");
                mHeadsetIntent = true;
                mHeadsetName = (String)msg.obj;
            }
            mBroadcastWakeLock.release();
        }
    };

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
            if (keycode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                final AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) {
                    Log.w(TAG, "handleVolumeKey: couldn't get AudioManager reference");
                } else {
                    am.toggleGlobalMute();
                }
            } else {
                // since audio is playing, we shouldn't have to hold a wake lock
                // during the call, but we do it as a precaution for the rare possibility
                // that the music stops right before we call this
                mBroadcastWakeLock.acquire();
                audioService.adjustStreamVolume(stream,
                    keycode == KeyEvent.KEYCODE_VOLUME_UP
                                ? AudioManager.ADJUST_RAISE
                                : AudioManager.ADJUST_LOWER,
                        0);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "IAudioService.adjustStreamVolume() threw RemoteException " + e);
        } finally {
            mBroadcastWakeLock.release();
        }
    }

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    // Assume this is called from the Handler thread.
    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };
            if (mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                mHandler.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags, boolean isScreenOn) {
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        int keyCode = event.getKeyCode();

        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        // If screen is off then we treat the case where the keyguard is open but hidden
        // the same as if it were open and in front.
        // This will prevent any keys other than the power button from waking the screen
        // when the keyguard is hidden by another activity.
        final boolean keyguardActive = (mKeyguardMediator == null ? false :
                                            (isScreenOn ?
                                                mKeyguardMediator.isShowingAndNotHidden() :
                                                mKeyguardMediator.isShowing()));

        if (!mSystemBooted) {
            // If we have not yet booted, don't let key events do anything.
            return 0;
        }

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                  + " screenIsOn=" + isScreenOn + " keyguardActive=" + keyguardActive);
        }

        if (down && (policyFlags & WindowManagerPolicy.FLAG_VIRTUAL) != 0
                && event.getRepeatCount() == 0) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
        }

        if (keyCode == KeyEvent.KEYCODE_POWER) {
            policyFlags |= WindowManagerPolicy.FLAG_WAKE;
        }
        final boolean isWakeKey = (policyFlags
                & (WindowManagerPolicy.FLAG_WAKE | WindowManagerPolicy.FLAG_WAKE_DROPPED)) != 0;

        // Basic policy based on screen state and keyguard.
        // FIXME: This policy isn't quite correct.  We shouldn't care whether the screen
        //        is on or off, really.  We should care about whether the device is in an
        //        interactive state or is in suspend pretending to be "off".
        //        The primary screen might be turned off due to proximity sensor or
        //        because we are presenting media on an auxiliary screen or remotely controlling
        //        the device some other way (which is why we have an exemption here for injected
        //        events).
        int result;
        if ((isScreenOn && !mHeadless) || (isInjected && !isWakeKey)) {
            // When the screen is on or if the key is injected pass the key to the application.
            result = ACTION_PASS_TO_USER;
        } else {
            // When the screen is off and the key is not injected, determine whether
            // to wake the device but don't pass the key to the application.
            result = 0;
            if (down && isWakeKey) {
                if (keyguardActive) {
                    // If the keyguard is showing, let it decide what to do with the wake key.
                    mKeyguardMediator.onWakeKeyWhenKeyguardShowingTq(keyCode,
                            mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED);
                } else if ((keyCode != KeyEvent.KEYCODE_VOLUME_UP) && (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN)) {
                    // Otherwise, wake the device ourselves.
                    result |= ACTION_POKE_USER_ACTIVITY;
                }
            }
        }

        // Handle special keys.
        switch (keyCode) {
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
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE: {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (down) {
                        if (isScreenOn && !mVolumeDownKeyTriggered
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mVolumeDownKeyTriggered = true;
                            mVolumeDownKeyTime = event.getDownTime();
                            mVolumeDownKeyConsumedByChord = false;
                            cancelPendingPowerKeyAction();
                            interceptScreenshotChord();
                            interceptRingerChord();
                        }
                    } else {
                        mVolumeDownKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                        cancelPendingRingerChordAction();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (down) {
                        if (isScreenOn && !mVolumeUpKeyTriggered
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mVolumeUpKeyTriggered = true;
                            mVolumeUpKeyTime = event.getDownTime();
                            mVolumeUpKeyConsumedByChord = false;
                            cancelPendingPowerKeyAction();
                            cancelPendingScreenshotChordAction();
                            interceptRingerChord();
                        }
                    } else {
                        mVolumeUpKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                        cancelPendingRingerChordAction();
                    }
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
                }
                if (isMusicActive() && (result & ACTION_PASS_TO_USER) == 0) {
                    if (mVolBtnMusicControls && down && (keyCode != KeyEvent.KEYCODE_VOLUME_MUTE)) {
                        mIsLongPress = false;
                        int newKeyCode = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP ?
                                KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                        Message msg = mHandler.obtainMessage(MSG_DISPATCH_VOLKEY_WITH_WAKE_LOCK,
                                new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), newKeyCode, 0));
                        msg.setAsynchronous(true);
                        mHandler.sendMessageDelayed(msg, ViewConfiguration.getLongPressTimeout());
                        break;
                    } else {
                        if (mVolBtnMusicControls && !down) {
                            mHandler.removeMessages(MSG_DISPATCH_VOLKEY_WITH_WAKE_LOCK);
                            if (mIsLongPress) {
                                break;
                            }
                        }
                        if (!isScreenOn && !mVolumeWakeScreen) {
                            handleVolumeKey(AudioManager.STREAM_MUSIC, keyCode);
                        }
                    }
                }
                if (isScreenOn || !mVolumeWakeScreen) {
                    break;
                } else if (keyguardActive) {
                    keyCode = KeyEvent.KEYCODE_POWER;
                    mKeyguardMediator.onWakeKeyWhenKeyguardShowingTq(keyCode,
                            mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED);
                } else {
                    result |= ACTION_POKE_USER_ACTIVITY;
                    break;
                }
            }

            case KeyEvent.KEYCODE_POWER: {
                if ((mTopFullscreenOpaqueWindowState.getAttrs().flags & WindowManager.LayoutParams.PREVENT_POWER_KEY) != 0){
                    return result;
                }
                result &= ~ACTION_PASS_TO_USER;
                if (down) {
                    if (isScreenOn && !mPowerKeyTriggered
                            && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                        mPowerKeyTriggered = true;
                        mPowerKeyTime = event.getDownTime();
                        cancelPendingRingerChordAction();
                        interceptScreenshotChord();
                    }

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
                    interceptPowerKeyDown(!isScreenOn || hungUp
                            || mVolumeDownKeyTriggered || mVolumeUpKeyTriggered);
                } else {
                    mPowerKeyTriggered = false;
                    cancelPendingScreenshotChordAction();
                    if (interceptPowerKeyUp(canceled || mPendingPowerKeyUpCanceled)) {
                        result = (result & ~ACTION_POKE_USER_ACTIVITY) | ACTION_GO_TO_SLEEP;
                    }
                    mPendingPowerKeyUpCanceled = false;
                }
                break;
            }

            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (down) {
                    ITelephony telephonyService = getTelephonyService();
                    if (telephonyService != null) {
                        try {
                            if (!telephonyService.isIdle()) {
                                // Suppress PLAY/PAUSE toggle when phone is ringing or in-call
                                // to avoid music playback.
                                break;
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "ITelephony threw RemoteException", ex);
                        }
                    }
                }
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                if ((result & ACTION_PASS_TO_USER) == 0) {
                    // Only do this if we would otherwise not pass it to the user. In that
                    // case, the PhoneWindow class will do the same thing, except it will
                    // only do it if the showing app doesn't process the key on its own.
                    // Note that we need to make a copy of the key event here because the
                    // original key event will be recycled when we return.
                    mBroadcastWakeLock.acquire();
                    Message msg = mHandler.obtainMessage(MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK,
                            new KeyEvent(event));
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
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

    /** {@inheritDoc} */
    @Override
    public int interceptMotionBeforeQueueingWhenScreenOff(int policyFlags) {
        int result = 0;

        final boolean isWakeMotion = (policyFlags
                & (WindowManagerPolicy.FLAG_WAKE | WindowManagerPolicy.FLAG_WAKE_DROPPED)) != 0;
        if (isWakeMotion) {
            if (mKeyguardMediator != null && mKeyguardMediator.isShowing()) {
                // If the keyguard is showing, let it decide what to do with the wake motion.
                mKeyguardMediator.onWakeMotionWhenKeyguardShowingTq();
            } else {
                // Otherwise, wake the device ourselves.
                result |= ACTION_POKE_USER_ACTIVITY;
            }
        }
        return result;
    }

    void dispatchMediaKeyWithWakeLock(KeyEvent event) {
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyWithWakeLock: " + event);
        }

        if (mHavePendingMediaKeyRepeatWithWakeLock) {
            if (DEBUG_INPUT) {
                Slog.d(TAG, "dispatchMediaKeyWithWakeLock: canceled repeat");
            }

            mHandler.removeMessages(MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK);
            mHavePendingMediaKeyRepeatWithWakeLock = false;
            mBroadcastWakeLock.release(); // pending repeat was holding onto the wake lock
        }

        dispatchMediaKeyWithWakeLockToAudioService(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            mHavePendingMediaKeyRepeatWithWakeLock = true;

            Message msg = mHandler.obtainMessage(
                    MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK, event);
            msg.setAsynchronous(true);
            mHandler.sendMessageDelayed(msg, ViewConfiguration.getKeyRepeatTimeout());
        } else {
            mBroadcastWakeLock.release();
        }
    }

    void dispatchMediaKeyRepeatWithWakeLock(KeyEvent event) {
        mHavePendingMediaKeyRepeatWithWakeLock = false;

        KeyEvent repeatEvent = KeyEvent.changeTimeRepeat(event,
                SystemClock.uptimeMillis(), 1, event.getFlags() | KeyEvent.FLAG_LONG_PRESS);
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyRepeatWithWakeLock: " + repeatEvent);
        }

        dispatchMediaKeyWithWakeLockToAudioService(repeatEvent);
        mBroadcastWakeLock.release();
    }

    void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        if (ActivityManagerNative.isSystemReady()) {
            IAudioService audioService = getAudioService();
            if (audioService != null) {
                try {
                    audioService.dispatchMediaKeyEventUnderWakelock(event);
                } catch (RemoteException e) {
                    Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
                }
            }
        }
    }

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
            updateRotation(true);
            updateOrientationListenerLp();
        }
    };

    BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                mPluggedIn = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
                if (localLOGV) Log.v(TAG, "BATTERY_CHANGED: " + intent + " plugged=" + mPluggedIn);
            }
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
        synchronized (mLock) {
            mScreenOnEarly = false;
            mScreenOnFully = false;
        }
        if (mKeyguardMediator != null) {
            mKeyguardMediator.onScreenTurnedOff(why);
        }
        synchronized (mLock) {
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
    }

    /** {@inheritDoc} */
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        EventLog.writeEvent(70000, 1);
        if (false) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG, "Screen turning on...", here);
        }
        if (screenOnListener != null) {
            if (mKeyguardMediator != null) {
                try {
                    mWindowManager.setEventDispatching(true);
                } catch (RemoteException unhandled) {
                }
                mKeyguardMediator.onScreenTurnedOn(new KeyguardViewManager.ShowListener() {
                    @Override public void onShown(IBinder windowToken) {
                        if (windowToken != null) {
                            try {
                                mWindowManager.waitForWindowDrawn(windowToken,
                                        new IRemoteCallback.Stub() {
                                    @Override public void sendResult(Bundle data) {
                                        Slog.i(TAG, "Lock screen displayed!");
                                        screenOnListener.onScreenOn();
                                        synchronized (mLock) {
                                            mScreenOnFully = true;
                                        }
                                    }
                                });
                            } catch (RemoteException e) {
                            }
                        } else {
                            Slog.i(TAG, "No lock screen!");
                            screenOnListener.onScreenOn();
                            synchronized (mLock) {
                                mScreenOnFully = true;
                            }
                        }
                    }
                });
            }
        } else {
            if (mKeyguardMediator != null) {
                // Must set mScreenOn = true.
                mKeyguardMediator.onScreenTurnedOn(null);
            }
            synchronized (mLock) {
                mScreenOnFully = true;
            }
        }
        synchronized (mLock) {
            mScreenOnEarly = true;
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
    }

    /** {@inheritDoc} */
    public boolean isScreenOnEarly() {
        return mScreenOnEarly;
    }
    
    /** {@inheritDoc} */
    public boolean isScreenOnFully() {
        return mScreenOnFully;
    }
    
    /** {@inheritDoc} */
    public void enableKeyguard(boolean enabled) {
        if (mKeyguardMediator != null) {
            mKeyguardMediator.setKeyguardEnabled(enabled);
        }
    }

    /** {@inheritDoc} */
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        if (mKeyguardMediator != null) {
            mKeyguardMediator.verifyUnlock(callback);
        }
    }

    private boolean keyguardIsShowingTq() {
        if (mKeyguardMediator == null) return false;
        return mKeyguardMediator.isShowingAndNotHidden();
    }


    /** {@inheritDoc} */
    public boolean isKeyguardLocked() {
        return keyguardOn();
    }

    /** {@inheritDoc} */
    public boolean isKeyguardSecure() {
        if (mKeyguardMediator == null) return false;
        return mKeyguardMediator.isSecure();
    }

    /** {@inheritDoc} */
    public boolean inKeyguardRestrictedKeyInputMode() {
        if (mKeyguardMediator == null) return false;
        return mKeyguardMediator.isInputRestricted();
    }

    public void dismissKeyguardLw() {
        if (!mKeyguardMediator.isSecure()) {
            if (mKeyguardMediator.isShowing()) {
                mHandler.post(new Runnable() {
                    public void run() {
                        mKeyguardMediator.keyguardDone(false, true);
                    }
                });
            }
        }
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

    @Override
    public int rotationForOrientationLw(int orientation, int lastRotation) {
        if (false) {
            Slog.v(TAG, "rotationForOrientationLw(orient="
                        + orientation + ", last=" + lastRotation
                        + "); user=" + mUserRotation + " "
                        + ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED)
                            ? "USER_ROTATION_LOCKED" : "")
                        );
        }

        synchronized (mLock) {
            int sensorRotation = mOrientationListener.getProposedRotation(); // may be -1
            if (sensorRotation < 0) {
                sensorRotation = lastRotation;
            }

            final int preferredRotation;
            if ((mLidState == LID_OPEN && mLidOpenRotation >= 0)
                    && !(mHasRemovableLid
                            && mDockMode == Intent.EXTRA_DOCK_STATE_UNDOCKED)) {
                // Ignore sensor when lid switch is open and rotation is forced
                // and a removable lid was not undocked.
                preferredRotation = mLidOpenRotation;
            } else if (mDockMode == Intent.EXTRA_DOCK_STATE_CAR
                    && (mCarDockEnablesAccelerometer || mCarDockRotation >= 0)) {
                // Ignore sensor when in car dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mCarDockEnablesAccelerometer
                        ? sensorRotation : mCarDockRotation;
            } else if ((mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK)
                    && (mDeskDockEnablesAccelerometer || mDeskDockRotation >= 0)) {
                // Ignore sensor when in desk dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mDeskDockEnablesAccelerometer
                        ? sensorRotation : mDeskDockRotation;
            } else if (mHdmiPlugged) {
                // Ignore sensor when plugged into HDMI.
                // Note that the dock orientation overrides the HDMI orientation.
                preferredRotation = mHdmiRotation;
            } else if ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_FREE
                            && (orientation == ActivityInfo.SCREEN_ORIENTATION_USER
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED))
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
                // Otherwise, use sensor only if requested by the application or enabled
                // by default for USER or UNSPECIFIED modes.  Does not apply to NOSENSOR.
                if (mAllowAllRotations < 0) {
                    // Can't read this during init() because the context doesn't
                    // have display metrics at that time so we cannot determine
                    // tablet vs. phone then.
                    mAllowAllRotations = mContext.getResources().getBoolean(
                            com.android.internal.R.bool.config_allowAllRotations) ? 1 : 0;
                }
                // Rotation setting bitmask
                // 1=0 2=90 4=180 8=270
                boolean allowed = true;
                if (mUserRotationAngles < 0) {
                    // Not set by user so use these defaults
                    mUserRotationAngles = mAllowAllRotations == 1 ?
                            (1 | 2 | 4 | 8) : // All angles
                                (1 | 2 | 8); // All except 180
                }
                switch (sensorRotation) {
                    case Surface.ROTATION_0:
                        allowed = (mUserRotationAngles & 1) != 0;
                        break;
                    case Surface.ROTATION_90:
                        allowed = (mUserRotationAngles & 2) != 0;
                        break;
                    case Surface.ROTATION_180:
                        allowed = (mUserRotationAngles & 4) != 0;
                        break;
                    case Surface.ROTATION_270:
                        allowed = (mUserRotationAngles & 8) != 0;
                        break;
                }
                if (allowed) {
                    preferredRotation = sensorRotation;
                } else {
                    preferredRotation = lastRotation;
                }
            } else if (mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED
                    && orientation != ActivityInfo.SCREEN_ORIENTATION_NOSENSOR) {
                // Apply rotation lock.  Does not apply to NOSENSOR.
                // The idea is that the user rotation expresses a weak preference for the direction
                // of gravity and as NOSENSOR is never affected by gravity, then neither should
                // NOSENSOR be affected by rotation lock (although it will be affected by docks).
                preferredRotation = mUserRotation;
            } else {
                // No overriding preference.
                // We will do exactly what the application asked us to do.
                preferredRotation = -1;
            }

            switch (orientation) {
                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                    // Return portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mPortraitRotation;

                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    // Return landscape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    // Return reverse portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mUpsideDownRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    // Return seascape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mSeascapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // Return either landscape rotation.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isLandscapeOrSeascape(lastRotation)) {
                        return lastRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                    // Return either portrait rotation.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isAnyPortrait(lastRotation)) {
                        return lastRotation;
                    }
                    return mPortraitRotation;

                default:
                    // For USER, UNSPECIFIED, NOSENSOR, SENSOR and FULL_SENSOR,
                    // just return the preferred orientation we already calculated.
                    if (preferredRotation >= 0) {
                        return preferredRotation;
                    }
                    return Surface.ROTATION_0;
            }
        }
    }

    @Override
    public boolean rotationHasCompatibleMetricsLw(int orientation, int rotation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return isAnyPortrait(rotation);

            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return isLandscapeOrSeascape(rotation);

            default:
                return true;
        }
    }

    @Override
    public void setRotationLw(int rotation) {
        mOrientationListener.setCurrentRotation(rotation);
    }

    private boolean isLandscapeOrSeascape(int rotation) {
        return rotation == mLandscapeRotation || rotation == mSeascapeRotation;
    }

    private boolean isAnyPortrait(int rotation) {
        return rotation == mPortraitRotation || rotation == mUpsideDownRotation;
    }


    // User rotation: to be used when all else fails in assigning an orientation to the device
    public void setUserRotationMode(int mode, int rot) {
        ContentResolver res = mContext.getContentResolver();

        // mUserRotationMode and mUserRotation will be assigned by the content observer
        if (mode == WindowManagerPolicy.USER_ROTATION_LOCKED) {
            Settings.System.putInt(res,
                    Settings.System.USER_ROTATION,
                    rot);
            Settings.System.putInt(res,
                    Settings.System.ACCELEROMETER_ROTATION,
                    0);
        } else {
            Settings.System.putInt(res,
                    Settings.System.ACCELEROMETER_ROTATION,
                    1);
        }
    }

    public void setSafeMode(boolean safeMode) {
        mSafeMode = safeMode;
        performHapticFeedbackLw(null, safeMode
                ? HapticFeedbackConstants.SAFE_MODE_ENABLED
                : HapticFeedbackConstants.SAFE_MODE_DISABLED, true);
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
        if (mKeyguardMediator != null) {
            // tell the keyguard
            mKeyguardMediator.onSystemReady();
        }
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
    public void systemBooted() {
        synchronized (mLock) {
            mSystemBooted = true;
            if (mHeadsetIntent) {
                Log.e(TAG, "send headset intent after system booted");
                sendIntents(mHeadsetName);
            }
        }
    }

    ProgressDialog mBootMsgDialog = null;

    /** {@inheritDoc} */
    public void showBootMessage(final CharSequence msg, final boolean always) {
        if (mHeadless) return;
        mHandler.post(new Runnable() {
            @Override public void run() {
                if (mBootMsgDialog == null) {
                    mBootMsgDialog = new ProgressDialog(mContext) {
                        // This dialog will consume all events coming in to
                        // it, to avoid it trying to do things too early in boot.
                        @Override public boolean dispatchKeyEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchTrackballEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchPopulateAccessibilityEvent(
                                AccessibilityEvent event) {
                            return true;
                        }
                    };
                    mBootMsgDialog.setTitle(R.string.android_upgrading_title);
                    mBootMsgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mBootMsgDialog.setIndeterminate(true);
                    mBootMsgDialog.getWindow().setType(
                            WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
                    mBootMsgDialog.getWindow().addFlags(
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                    mBootMsgDialog.getWindow().setDimAmount(1);
                    WindowManager.LayoutParams lp = mBootMsgDialog.getWindow().getAttributes();
                    lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
                    mBootMsgDialog.getWindow().setAttributes(lp);
                    mBootMsgDialog.setCancelable(false);
                    mBootMsgDialog.show();
                }
                mBootMsgDialog.setMessage(msg);
            }
        });
    }

    /** {@inheritDoc} */
    public void hideBootMessages() {
        mHandler.post(new Runnable() {
            @Override public void run() {
                if (mBootMsgDialog != null) {
                    mBootMsgDialog.dismiss();
                    mBootMsgDialog = null;
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void userActivity() {
        // ***************************************
        // NOTE NOTE NOTE NOTE NOTE NOTE NOTE NOTE
        // ***************************************
        // THIS IS CALLED FROM DEEP IN THE POWER MANAGER
        // WITH ITS LOCKS HELD.
        //
        // This code must be VERY careful about the locks
        // it acquires.
        // In fact, the current code acquires way too many,
        // and probably has lurking deadlocks.

        synchronized (mScreenLockTimeout) {
            if (mLockScreenTimerActive) {
                // reset the timer
                mHandler.removeCallbacks(mScreenLockTimeout);
                mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
            }
        }
    }

    private IDreamManager getDreamManager() {
        if (!mScreenSaverFeatureAvailable) {
            return null;
        }
        
        IDreamManager sandman = IDreamManager.Stub.asInterface(
                ServiceManager.checkService("dreams"));
        if (sandman == null) {
            Log.w(TAG, "Unable to find IDreamManager");
        }
        return sandman;
    }

    @Override
    public boolean isScreenSaverEnabled() {
        return (mScreenSaverFeatureAvailable && mScreenSaverEnabledByUser
                && mScreenSaverMayRun && mScreenOnEarly && mPluggedIn);
    }

    @Override
    public boolean startScreenSaver() {
        synchronized (mLock) {
            if (isScreenSaverEnabled()) {
                IDreamManager dm = getDreamManager();
                if (dm == null) return false;
                
                try {
                    if (localLOGV) Log.v(TAG, "startScreenSaver: entering dreamland...");

                    dm.dream();
                    return true;
                } catch (RemoteException ex) {
                    // too bad, so sad, oh mom, oh dad
                }
            }
        }
        return false;
    }

    @Override
    public void stopScreenSaver() {
        synchronized (mLock) {
            IDreamManager dm = getDreamManager();
            if (dm == null) return;
            
            try {
                if (localLOGV) Log.v(TAG, "startScreenSaver: awakening...");
                
                dm.awaken();
            } catch (RemoteException ex) {
            }
        }
    }

    Runnable mScreenLockTimeout = new Runnable() {
        public void run() {
            synchronized (this) {
                if (localLOGV) Log.v(TAG, "mScreenLockTimeout activating keyguard");
                if (mKeyguardMediator != null) {
                    mKeyguardMediator.doKeyguardTimeout();
                }
                mLockScreenTimerActive = false;
            }
        }
    };

    public void lockNow() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);
        mHandler.removeCallbacks(mScreenLockTimeout);
        mHandler.post(mScreenLockTimeout);
    }

    private void updateLockScreenTimeout() {
        synchronized (mScreenLockTimeout) {
            boolean enable = (mAllowLockscreenWhenOn && mScreenOnEarly &&
                    mKeyguardMediator != null && mKeyguardMediator.isSecure());
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
        applyLidSwitchState();
        updateRotation(true);
    }

    private void applyLidSwitchState() {
        mPowerManager.setKeyboardVisibility(isBuiltInKeyboardVisible());

        if (mLidState == LID_CLOSED && mLidControlsSleep) {
            mPowerManager.goToSleep(SystemClock.uptimeMillis());
        }
    }

    void updateRotation(boolean alwaysSendConfiguration) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, false);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, forceRelayout);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    /**
     * Return an Intent to launch the currently active dock app as home.  Returns
     * null if the standard home should be launched, which is the case if any of the following is
     * true:
     * <ul>
     *  <li>The device is not in either car mode or desk mode
     *  <li>The device is in car mode but ENABLE_CAR_DOCK_HOME_CAPTURE is false
     *  <li>The device is in desk mode but ENABLE_DESK_DOCK_HOME_CAPTURE is false
     *  <li>The device is in car mode but there's no CAR_DOCK app with METADATA_DOCK_HOME
     *  <li>The device is in desk mode but there's no DESK_DOCK app with METADATA_DOCK_HOME
     * </ul>
     * @return
     */
    Intent createHomeDockIntent() {
        Intent intent = null;
        
        // What home does is based on the mode, not the dock state.  That
        // is, when in car mode you should be taken to car home regardless
        // of whether we are actually in a car dock.
        if (mUiMode == Configuration.UI_MODE_TYPE_CAR) {
            if (ENABLE_CAR_DOCK_HOME_CAPTURE) {
                intent = mCarDockIntent;
            }
        } else if (mUiMode == Configuration.UI_MODE_TYPE_DESK) {
            if (ENABLE_DESK_DOCK_HOME_CAPTURE) {
                intent = mDeskDockIntent;
            }
        }

        if (intent == null) {
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
                                        null, null, 0,
                                        ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                        null, null, null);
                        if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
                            return false;
                        }
                    }
                }
                int result = ActivityManagerNative.getDefault()
                        .startActivity(null, mHomeIntent,
                                mHomeIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                                null, null, 0,
                                ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                null, null, null);
                if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
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
    
    public void screenOnStartedLw() {
        // The window manager has just grabbed a wake lock. This is our cue to disable the screen
        // saver.
        synchronized (mLock) {
            mScreenSaverMayRun = false;
        }
    }

    public void screenOnStoppedLw() {
        if (mPowerManager.isScreenOn()) {
            if (mKeyguardMediator != null && !mKeyguardMediator.isShowingAndNotHidden()) {
                long curTime = SystemClock.uptimeMillis();
                mPowerManager.userActivity(curTime, false, LocalPowerManager.OTHER_EVENT);
            }

            synchronized (mLock) {
                // even if the keyguard is up, now that all the wakelocks have been released, we
                // should re-enable the screen saver
                mScreenSaverMayRun = true;
            }
        }
    }

    public boolean allowKeyRepeat() {
        // disable key repeat when screen is off
        return mScreenOnEarly;
    }

    private int updateSystemUiVisibilityLw() {
        // If there is no window focused, there will be nobody to handle the events
        // anyway, so just hang on in whatever state we're in until things settle down.
        if (mFocusedWindow == null) {
            return 0;
        }
        final int visibility = mFocusedWindow.getSystemUiVisibility()
                & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
        int diff = visibility ^ mLastSystemUiFlags;
        final boolean needsMenu = mFocusedWindow.getNeedsMenuLw(mTopFullscreenOpaqueWindowState);
        if (diff == 0 && mLastFocusNeedsMenu == needsMenu
                && mFocusedApp == mFocusedWindow.getAppToken()) {
            return 0;
        }
        mLastSystemUiFlags = visibility;
        mLastFocusNeedsMenu = needsMenu;
        mFocusedApp = mFocusedWindow.getAppToken();
        mHandler.post(new Runnable() {
                public void run() {
                    try {
                        IStatusBarService statusbar = getStatusBarService();
                        if (statusbar != null) {
                            statusbar.setSystemUiVisibility(visibility, 0xffffffff);
                            statusbar.topAppWindowChanged(needsMenu);
                        }
                    } catch (RemoteException e) {
                        // re-acquire status bar service next time it is needed.
                        mStatusBarService = null;
                    }
                }
            });
        return diff;
    }

    // Use this instead of checking config_showNavigationBar so that it can be consistently
    // overridden by qemu.hw.mainkeys in the emulator.
    public boolean hasNavigationBar() {
        return mHasNavigationBar;
    }

    @Override
    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        mLastInputMethodWindow = ime;
        mLastInputMethodTargetWindow = target;
    }

    public void dump(String prefix, PrintWriter pw, String[] args) {
        pw.print(prefix); pw.print("mSafeMode="); pw.print(mSafeMode);
                pw.print(" mSystemReady="); pw.print(mSystemReady);
                pw.print(" mSystemBooted="); pw.println(mSystemBooted);
        pw.print(prefix); pw.print("mLidState="); pw.print(mLidState);
                pw.print(" mLidOpenRotation="); pw.print(mLidOpenRotation);
                pw.print(" mHdmiPlugged="); pw.println(mHdmiPlugged);
        if (mLastSystemUiFlags != 0 || mResettingSystemUiFlags != 0
                || mForceClearedSystemUiFlags != 0) {
            pw.print(prefix); pw.print("mLastSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mLastSystemUiFlags));
                    pw.print(" mResettingSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mResettingSystemUiFlags));
                    pw.print(" mForceClearedSystemUiFlags=0x");
                    pw.println(Integer.toHexString(mForceClearedSystemUiFlags));
        }
        if (mLastFocusNeedsMenu) {
            pw.print(prefix); pw.print("mLastFocusNeedsMenu=");
                    pw.println(mLastFocusNeedsMenu);
        }
        pw.print(prefix); pw.print("mUiMode="); pw.print(mUiMode);
                pw.print(" mDockMode="); pw.print(mDockMode);
                pw.print(" mCarDockRotation="); pw.print(mCarDockRotation);
                pw.print(" mDeskDockRotation="); pw.println(mDeskDockRotation);
        pw.print(prefix); pw.print("mUserRotationMode="); pw.print(mUserRotationMode);
                pw.print(" mUserRotation="); pw.print(mUserRotation);
                pw.print(" mAllowAllRotations="); pw.println(mAllowAllRotations);
        pw.print(prefix); pw.print("mCurrentAppOrientation="); pw.println(mCurrentAppOrientation);
        pw.print(prefix); pw.print("mCarDockEnablesAccelerometer=");
                pw.print(mCarDockEnablesAccelerometer);
                pw.print(" mDeskDockEnablesAccelerometer=");
                pw.println(mDeskDockEnablesAccelerometer);
        pw.print(prefix); pw.print("mLidKeyboardAccessibility=");
                pw.print(mLidKeyboardAccessibility);
                pw.print(" mLidNavigationAccessibility="); pw.print(mLidNavigationAccessibility);
                pw.print(" mLidControlsSleep="); pw.println(mLidControlsSleep);
        pw.print(prefix); pw.print("mLongPressOnPowerBehavior=");
                pw.print(mLongPressOnPowerBehavior);
                pw.print(" mHasSoftInput="); pw.println(mHasSoftInput);
        pw.print(prefix); pw.print("mScreenOnEarly="); pw.print(mScreenOnEarly);
                pw.print(" mScreenOnFully="); pw.print(mScreenOnFully);
                pw.print(" mOrientationSensorEnabled="); pw.println(mOrientationSensorEnabled);
        pw.print(prefix); pw.print("mUnrestrictedScreen=("); pw.print(mUnrestrictedScreenLeft);
                pw.print(","); pw.print(mUnrestrictedScreenTop);
                pw.print(") "); pw.print(mUnrestrictedScreenWidth);
                pw.print("x"); pw.println(mUnrestrictedScreenHeight);
        pw.print(prefix); pw.print("mRestrictedScreen=("); pw.print(mRestrictedScreenLeft);
                pw.print(","); pw.print(mRestrictedScreenTop);
                pw.print(") "); pw.print(mRestrictedScreenWidth);
                pw.print("x"); pw.println(mRestrictedScreenHeight);
        pw.print(prefix); pw.print("mStableFullscreen=("); pw.print(mStableFullscreenLeft);
                pw.print(","); pw.print(mStableFullscreenTop);
                pw.print(")-("); pw.print(mStableFullscreenRight);
                pw.print(","); pw.print(mStableFullscreenBottom); pw.println(")");
        pw.print(prefix); pw.print("mStable=("); pw.print(mStableLeft);
                pw.print(","); pw.print(mStableTop);
                pw.print(")-("); pw.print(mStableRight);
                pw.print(","); pw.print(mStableBottom); pw.println(")");
        pw.print(prefix); pw.print("mSystem=("); pw.print(mSystemLeft);
                pw.print(","); pw.print(mSystemTop);
                pw.print(")-("); pw.print(mSystemRight);
                pw.print(","); pw.print(mSystemBottom); pw.println(")");
        pw.print(prefix); pw.print("mCur=("); pw.print(mCurLeft);
                pw.print(","); pw.print(mCurTop);
                pw.print(")-("); pw.print(mCurRight);
                pw.print(","); pw.print(mCurBottom); pw.println(")");
        pw.print(prefix); pw.print("mContent=("); pw.print(mContentLeft);
                pw.print(","); pw.print(mContentTop);
                pw.print(")-("); pw.print(mContentRight);
                pw.print(","); pw.print(mContentBottom); pw.println(")");
        pw.print(prefix); pw.print("mDock=("); pw.print(mDockLeft);
                pw.print(","); pw.print(mDockTop);
                pw.print(")-("); pw.print(mDockRight);
                pw.print(","); pw.print(mDockBottom); pw.println(")");
        pw.print(prefix); pw.print("mDockLayer="); pw.print(mDockLayer);
                pw.print(" mStatusBarLayer="); pw.println(mStatusBarLayer);
        pw.print(prefix); pw.print("mTopFullscreenOpaqueWindowState=");
                pw.println(mTopFullscreenOpaqueWindowState);
        pw.print(prefix); pw.print("mTopIsFullscreen="); pw.print(mTopIsFullscreen);
                pw.print(" mForceStatusBar="); pw.print(mForceStatusBar);
                pw.print(" mHideLockScreen="); pw.println(mHideLockScreen);
        pw.print(prefix); pw.print("mDismissKeyguard="); pw.print(mDismissKeyguard);
        pw.print(prefix); pw.print("mAllowLockscreenWhenOn="); pw.print(mAllowLockscreenWhenOn);
                pw.print(" mLockScreenTimeout="); pw.print(mLockScreenTimeout);
                pw.print(" mLockScreenTimerActive="); pw.println(mLockScreenTimerActive);
        pw.print(prefix); pw.print("mEndcallBehavior="); pw.print(mEndcallBehavior);
                pw.print(" mIncallPowerBehavior="); pw.print(mIncallPowerBehavior);
                pw.print(" mRingHomeBehavior="); pw.print(mRingHomeBehavior);
                pw.print(" mLongPressOnHomeBehavior="); pw.println(mLongPressOnHomeBehavior);
        pw.print(prefix); pw.print("mLandscapeRotation="); pw.print(mLandscapeRotation);
                pw.print(" mSeascapeRotation="); pw.println(mSeascapeRotation);
        pw.print(prefix); pw.print("mPortraitRotation="); pw.print(mPortraitRotation);
                pw.print(" mUpsideDownRotation="); pw.println(mUpsideDownRotation);
    }

    @Override
    public void notifySPenSwitchChanged(long whenNanos, boolean penon) {
        Intent i = new Intent();
        if (penon) {
            i.setAction(ACTION_SPEN_REMOVED);
        } else {
            i.setAction(ACTION_SPEN_INSERTED);
        }
        mContext.sendBroadcast(i);
    }
}
