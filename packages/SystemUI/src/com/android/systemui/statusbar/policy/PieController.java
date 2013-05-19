/*
 * Copyright (C) 2013 The CyanogenMod Project (Jens Doll)
 * This code is loosely based on portions of the ParanoidAndroid Project source, Copyright (C) 2012.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.policy;

import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.pie.PieManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.internal.util.cm.DevUtils;
import com.android.internal.util.pie.PiePosition;
import com.android.internal.util.pie.PieServiceConstants;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.NavigationButtons;
import com.android.systemui.statusbar.NavigationButtons.ButtonInfo;
import com.android.systemui.statusbar.pie.PieItem;
import com.android.systemui.statusbar.pie.PieView;
import com.android.systemui.statusbar.pie.PieView.PieDrawable;
import com.android.systemui.statusbar.pie.PieView.PieSlice;
import com.android.systemui.statusbar.pie.PieSliceContainer;
import com.android.systemui.statusbar.pie.PieSysInfo;

/**
 * Controller class for the default pie control.
 * <p>
 * This class is responsible for setting up the pie control, activating it, and defining and
 * executing the actions that can be triggered by the pie control.
 */
public class PieController implements BaseStatusBar.NavigationBarCallback, PieView.OnExitListener,
        PieView.OnSnapListener, PieItem.PieOnClickListener, PieItem.PieOnLongClickListener {
    public static final String TAG = "PieController";
    public static final boolean DEBUG = false;

    private static final ButtonInfo SEARCHLIGHT = new ButtonInfo(0, 0, 0,
            R.drawable.search_light, R.drawable.search_light, 0);

    public static final float EMPTY_ANGLE = 10;
    public static final float START_ANGLE = 180 + EMPTY_ANGLE;

    private static final int MSG_INJECT_KEY_DOWN = 1066;
    private static final int MSG_INJECT_KEY_UP = 1067;
    private static final int MSG_PIE_GAIN_FOCUS = 1068;
    private static final int MSG_PIE_RESTORE_LISTENER_STATE = 1069;

    private Context mContext;
    private PieManager mPieManager;
    private PieView mPieContainer;
    /**
     * This is only needed for #toggleRecentApps() and #showSearchPanel()
     */
    private BaseStatusBar mStatusBar;
    private Vibrator mVibrator;
    private WindowManager mWindowManager;
    private IWindowManager mWm;
    private int mBatteryLevel;
    private int mBatteryStatus;
    private TelephonyManager mTelephonyManager;
    private ServiceState mServiceState;

    // all pie slices that are managed by the controller
    private PieSliceContainer mNavigationSlice;
    private PieSysInfo mSysInfo;
    private PieItem mMenuButton;
    private PieItem mSearchLight;

    private int mNavigationIconHints = 0;
    private int mDisabledFlags = 0;
    private boolean mShowMenu = false;
    private Drawable mBackIcon;
    private Drawable mBackAltIcon;

    protected int mExpandedDesktopState;
    private int mPieTriggerSlots;
    private int mPieTriggerMask = PiePosition.LEFT.FLAG
            | PiePosition.BOTTOM.FLAG
            | PiePosition.RIGHT.FLAG
            | PiePosition.TOP.FLAG;
    private PiePosition mPosition;

    private PieManager.PieActivationListener mPieActivationListener =
            new PieManager.PieActivationListener(Looper.getMainLooper()) {
        @Override
        public void onPieActivation(int touchX, int touchY, PiePosition position, int flags) {
            if (position == PiePosition.BOTTOM && isSearchLightEnabled() && mStatusBar != null) {
                // if we are at the bottom and nothing else is there, use a
                // search light!
                mStatusBar.showSearchPanel();
                // restore listener state immediately (after the bookkeeping), and since the
                // search panel is a single gesture we will not trigger again
                mHandler.obtainMessage(MSG_PIE_RESTORE_LISTENER_STATE).sendToTarget();
            } else if (mPieContainer != null) {
                // set the snap points depending on current trigger and mask
                mPieContainer.setSnapPoints(mPieTriggerMask & ~mPieTriggerSlots);
                activateFromListener(touchX, touchY, position);
                // give the main thread some time to do the bookkeeping
                mHandler.obtainMessage(MSG_PIE_GAIN_FOCUS).sendToTarget();
            }
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message m) {
            final InputManager inputManager = InputManager.getInstance();
            switch (m.what) {
                case MSG_INJECT_KEY_DOWN:
                    inputManager.injectInputEvent((KeyEvent) m.obj,
                            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
                    break;
                case MSG_INJECT_KEY_UP:
                    inputManager.injectInputEvent((KeyEvent) m.obj,
                            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
                    break;
                case MSG_PIE_GAIN_FOCUS:
                    if (!mPieActivationListener.gainTouchFocus(mPieContainer.getWindowToken())) {
                        mPieContainer.exit();
                    }
                    break;
                case MSG_PIE_RESTORE_LISTENER_STATE:
                    mPieActivationListener.restoreListenerState();
                    break;
            }
        }
    };

    private void injectKeyDelayed(int keyCode, long when) {
        mHandler.removeMessages(MSG_INJECT_KEY_DOWN);
        mHandler.removeMessages(MSG_INJECT_KEY_UP);

        KeyEvent down = new KeyEvent(when, when + 10, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = new KeyEvent(when, when + 30, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);

        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_INJECT_KEY_DOWN, down), 10);
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_INJECT_KEY_UP, up), 30);
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            // trigger setupNavigationItems()
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_BUTTONS), false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.KILL_APP_LONGPRESS_BACK), false, this);
            // trigger setupContainer()
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STYLE), false, this);
            // trigger setupListener()
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_POSITIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_SENSITIVITY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            boolean expanded = Settings.System.getInt(resolver,
                    Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;
            if (expanded) {
                mExpandedDesktopState = Settings.System.getInt(resolver,
                        Settings.System.EXPANDED_DESKTOP_STYLE, 0);
            } else {
                mExpandedDesktopState = 0;
            }
            if (isEnabled()) {
                setupContainer();
                setupNavigationItems();
                setupListener();
            } else {
                detachContainer();
            }
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                         BatteryManager.BATTERY_STATUS_UNKNOWN);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // Give up on screen off. what's the point in pie controls if you don't see them?
                if (isShowing()) {
                    mPieContainer.exit();
                }
            }
        }
    };

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            mServiceState = serviceState;
        }
    };

    public PieController(Context context) {
        mContext = context;

        mPieManager = PieManager.getInstance();
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            mTelephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }

        final Resources res = mContext.getResources();

        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);

        mPieManager.setPieActivationListener(mPieActivationListener);

        // start listening for changes (calls setupListener & setupNavigationItems)
        mSettingsObserver.observe();
        mSettingsObserver.onChange(true);
    }

    private void detachContainer() {
        if (mPieContainer == null) {
            return;
        }

        mPieManager.updatePieActivationListener(mPieActivationListener, 0);

        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        mContext.unregisterReceiver(mBroadcastReceiver);

        mPieContainer.clearSlices();
        mPieContainer = null;
    }

    public void attachStatusBar(BaseStatusBar statusBar) {
        mStatusBar = statusBar;
    }

    private void setupContainer() {
        if (mPieContainer == null) {
            mPieContainer = new PieView(mContext);
            mPieContainer.setOnSnapListener(this);
            mPieContainer.setOnExitListener(this);

            if (mTelephonyManager != null) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(mBroadcastReceiver, filter);
        }

        mPieContainer.clearSlices();

        final Resources res = mContext.getResources();

        // construct navbar slice
        int inner = res.getDimensionPixelSize(R.dimen.pie_navbar_radius);
        int outer = inner + res.getDimensionPixelSize(R.dimen.pie_navbar_height);
        mNavigationSlice = new PieSliceContainer(mPieContainer, PieSlice.IMPORTANT
                | PieDrawable.DISPLAY_ALL);
        mNavigationSlice.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        mPieContainer.addSlice(mNavigationSlice);

        // construct sysinfo slice
        inner = res.getDimensionPixelSize(R.dimen.pie_sysinfo_radius);
        outer = inner + res.getDimensionPixelSize(R.dimen.pie_sysinfo_height);
        mSysInfo = new PieSysInfo(mContext, mPieContainer, this, PieDrawable.DISPLAY_NOT_AT_TOP);
        mSysInfo.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        mPieContainer.addSlice(mSysInfo);
    }

    private void setupListener() {
        ContentResolver resolver = mContext.getContentResolver();

        mPieTriggerSlots = Settings.System.getInt(resolver,
                Settings.System.PIE_POSITIONS, PiePosition.BOTTOM.FLAG);

        int sensitivity = Settings.System.getInt(resolver,
                Settings.System.PIE_SENSITIVITY, 3);
        if (sensitivity < PieServiceConstants.SENSITIVITY_LOWEST
                || sensitivity > PieServiceConstants.SENSITIVITY_HIGHEST) {
            sensitivity = PieServiceConstants.SENSITIVITY_DEFAULT;
        }

        mPieManager.updatePieActivationListener(mPieActivationListener,
                sensitivity<<PieServiceConstants.SENSITIVITY_SHIFT
                | mPieTriggerSlots & mPieTriggerMask);
    }

    private void setupNavigationItems() {
        int minimumImageSize = (int)mContext.getResources().getDimension(R.dimen.pie_item_size);
        boolean killAppLongPress = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.KILL_APP_LONGPRESS_BACK, 0) == 1;
        ButtonInfo[] buttons = NavigationButtons.loadButtonMap(mContext);

        mNavigationSlice.clear();

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] != NavigationButtons.EMPTY) {
                ButtonInfo bi = buttons[i];

                // search light is at the same position as the home button
                if (bi == NavigationButtons.HOME) {
                    // search light has a width of 6 to take the complete space that normally
                    // BACK HOME RECENT would occupy
                    mSearchLight = constructItem(6, SEARCHLIGHT,
                            SEARCHLIGHT.portResource, minimumImageSize, false);
                    mNavigationSlice.addItem(mSearchLight);
                }

                boolean canLongPress = bi == NavigationButtons.HOME
                        || (bi == NavigationButtons.BACK && killAppLongPress);
                boolean isSmall = NavigationButtons.IS_SLOT_SMALL[i];
                mNavigationSlice.addItem(constructItem(isSmall ? 1 : 2, bi,
                        isSmall ? bi.sideResource : bi.portResource, minimumImageSize,
                        canLongPress));
            }
        }
        mMenuButton = findItem(NavigationButtons.CONDITIONAL_MENU);

        setNavigationIconHints(mNavigationIconHints, true);
        setMenuVisibility(mShowMenu);
    }

    private PieItem constructItem(int width, ButtonInfo type, int image, int minimumImageSize,
            boolean canLongPress) {
        ImageView view = new ImageView(mContext);
        view.setImageResource(image);
        view.setMinimumWidth(minimumImageSize);
        view.setMinimumHeight(minimumImageSize);
        LayoutParams lp = new LayoutParams(minimumImageSize, minimumImageSize);
        view.setLayoutParams(lp);
        PieItem item = new PieItem(mContext, mPieContainer, 0, width, type, view);
        item.setOnClickListener(this);
        if (canLongPress) {
            item.setOnLongClickListener(this);
        }
        return item;
    }

    private PieItem findItem(ButtonInfo type) {
        for (PieItem item : mNavigationSlice.getItems()) {
            if (type == item.tag) {
                return item;
            }
        }

        return null;
    }

    private void setItemWithTagVisibility(ButtonInfo type, boolean show) {
        PieItem item = findItem(type);
        if (item != null) {
            item.show(show);
        }
    }

    public void activateFromListener(int touchX, int touchY, PiePosition position) {
        if (!isShowing()) {
            doHapticTriggerFeedback();

            mPosition = position;
            Point center = new Point(touchX, touchY);
            mPieContainer.activate(center, position);
            mWindowManager.addView(mPieContainer, generateLayoutParam());
        }
    }

    private WindowManager.LayoutParams generateLayoutParam() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        // This title is for debugging only. See: dumpsys window
        lp.setTitle("PieControlPanel");
        lp.windowAnimations = android.R.style.Animation;
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND;
        return lp;
    }

    @Override
    public void onExit() {
        mWindowManager.removeView(mPieContainer);
        mPieActivationListener.restoreListenerState();
    }

    public void updatePieTriggerMask(int newMask) {
        int oldState = mPieTriggerSlots & mPieTriggerMask;
        mPieTriggerMask = newMask;

        // first we check, if it would make a change
        if ((mPieTriggerSlots & mPieTriggerMask) != oldState) {
            setupListener();
        }
    }

    @Override
    public void setNavigationIconHints(int hints) {
        // this call may come from outside
        // check if we already have a navigation slice to manipulate
        if (mNavigationSlice != null) {
            setNavigationIconHints(hints, false);
        } else {
            mNavigationIconHints = hints;
        }
    }

    protected void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;

        if (DEBUG) Slog.v(TAG, "Pie navigation hints: " + hints);

        mNavigationIconHints = hints;

        PieItem item = findItem(NavigationButtons.HOME);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
        }
        item = findItem(NavigationButtons.RECENT);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
        }
        item = findItem(NavigationButtons.BACK);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP) != 0;
            boolean isAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
            item.setImageDrawable(isAlt ? mBackAltIcon : mBackIcon);
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    @Override
    public void setDisabledFlags(int disabledFlags) {
        // this call may come from outside
        // check if we already have a navigation slice to manipulate
        if (mNavigationSlice != null) {
            setDisabledFlags(disabledFlags, false);
        } else {
            mDisabledFlags = disabledFlags;
        }
    }

    protected void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        setItemWithTagVisibility(NavigationButtons.BACK, !disableBack);
        setItemWithTagVisibility(NavigationButtons.HOME, !disableHome);
        setItemWithTagVisibility(NavigationButtons.RECENT, !disableRecent);
        setItemWithTagVisibility(NavigationButtons.ALWAYS_MENU, !disableRecent);
        setItemWithTagVisibility(NavigationButtons.MENU_BIG, !disableRecent);
        setItemWithTagVisibility(NavigationButtons.SEARCH, !disableRecent);
        // enable search light when nothing except search is enabled
        if (mSearchLight != null) {
            mSearchLight.show(disableHome && disableRecent && disableBack && !disableSearch);
        }
        setMenuVisibility(mShowMenu);
    }

    @Override
    public void setMenuVisibility(boolean showMenu) {
        // this call may come from outside
        if (mMenuButton != null) {
            mMenuButton.show(showMenu);
        }

        mShowMenu = showMenu;
    }

    @Override
    public void onSnap(PiePosition position) {
        if (position == mPosition) {
            return;
        }

        doHapticTriggerFeedback();

        if (DEBUG) {
            Slog.d(TAG, "onSnap from " + position.name());
        }

        int triggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_POSITIONS, PiePosition.BOTTOM.FLAG);

        triggerSlots = triggerSlots & ~mPosition.FLAG | position.FLAG;

        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PIE_POSITIONS, triggerSlots);
    }

    @Override
    public void onClick(PieItem item) {
        long when = SystemClock.uptimeMillis();
        ButtonInfo bi = (ButtonInfo) item.tag;

        // play sound effect directly, since detaching the container will prevent to play the sound
        // at a later time.
        mPieContainer.playSoundEffect(SoundEffectConstants.CLICK);
        if (bi.keyCode != 0) {
            injectKeyDelayed(bi.keyCode, when);
        } else {
            // provide the same haptic feedback as if a virtual key is pressed
            mPieContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (bi == NavigationButtons.RECENT) {
                if (mStatusBar != null) {
                    mStatusBar.toggleRecentApps();
                }
            } else if (bi == SEARCHLIGHT) {
                launchAssistAction(true);
            }
        }
    }

    @Override
    public void onLongClick(PieItem item) {
        ButtonInfo bi = (ButtonInfo) item.tag;
        mPieContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        mPieContainer.playSoundEffect(SoundEffectConstants.CLICK);

        if (bi == NavigationButtons.HOME) {
            launchAssistAction(false);
        } else if (bi == NavigationButtons.BACK) {
            if (DevUtils.killForegroundApplication(mContext)) {
                Toast.makeText(mContext, com.android.internal.R.string.app_killed_message,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void doHapticTriggerFeedback() {
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }

        int hapticSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT);
        if (hapticSetting != 0) {
            mVibrator.vibrate(5);
        }
    }

    private void launchAssistAction(boolean force) {
        boolean isKeyguardShowing = false;
        try {
            isKeyguardShowing = mWm.isKeyguardLocked();
        } catch (RemoteException e) {
            // oh damn ...
        }

        if (isKeyguardShowing && force) {
            // Have keyguard show the bouncer and launch the activity if the user succeeds.
            try {
                mWm.showAssistant();
            } catch (RemoteException e) {
                // too bad, so sad...
            }
        } else {
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, UserHandle.USER_CURRENT);

            if (intent != null) {
                try {
                    ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                            R.anim.search_launch_enter, R.anim.search_launch_exit);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivityAsUser(intent, opts.toBundle(),
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (ActivityNotFoundException ignored) {
                    // fall through
                }
            }
        }
    }

    public boolean isShowing() {
        return mPieContainer.isShowing();
    }

    public boolean isSearchLightEnabled() {
        return mSearchLight != null && (mSearchLight.flags & PieDrawable.VISIBLE) != 0;
    }

    public boolean isEnabled() {
        int pie = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0);

        return (pie == 1 && mExpandedDesktopState != 0) || pie == 2;
    }

    public String getOperatorState() {
        if (mTelephonyManager == null) {
            return null;
        }
        if (mServiceState == null || mServiceState.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
            return mContext.getString(R.string.pie_phone_status_no_service);
        }
        if (mServiceState.getState() == ServiceState.STATE_POWER_OFF) {
            return mContext.getString(R.string.pie_phone_status_airplane_mode);
        }
        if (mServiceState.isEmergencyOnly()) {
            return mContext.getString(R.string.pie_phone_status_emergency_only);
        }
        return mServiceState.getOperatorAlphaLong();
    }

    public String getBatteryLevel() {
        if (mBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            return mContext.getString(R.string.pie_battery_status_full);
        }
        if (mBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            return mContext.getString(R.string.pie_battery_status_charging, mBatteryLevel);
        }
        return mContext.getString(R.string.pie_battery_status_discharging, mBatteryLevel);
    }
}
