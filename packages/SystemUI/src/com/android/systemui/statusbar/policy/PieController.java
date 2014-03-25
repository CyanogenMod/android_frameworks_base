/*
 * Copyright (C) 2014 SlimRoms Project
 * This code is loosely based on portions of the CyanogenMod Project (Jens Doll) Copyright (C) 2013
 * and the ParanoidAndroid Project source, Copyright (C) 2012.
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.StatusBarManager;
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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.gesture.EdgeGestureManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.internal.util.gesture.EdgeGesturePosition;
import com.android.internal.util.gesture.EdgeServiceConstants;
import com.android.internal.util.slim.ButtonConfig;
import com.android.internal.util.slim.ButtonsConstants;
import com.android.internal.util.slim.ButtonsHelper;
import com.android.internal.util.slim.Converter;
import com.android.internal.util.slim.ImageHelper;
import com.android.internal.util.slim.SlimActions;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.NavigationBarOverlay;
import com.android.systemui.statusbar.pie.PieItem;
import com.android.systemui.statusbar.pie.PieView;
import com.android.systemui.statusbar.pie.PieView.PieDrawable;
import com.android.systemui.statusbar.pie.PieView.PieSlice;
import com.android.systemui.statusbar.pie.PieSliceContainer;
import com.android.systemui.statusbar.pie.PieSysInfo;

import java.util.ArrayList;

/**
 * Controller class for the default pie control.
 * <p>
 * This class is responsible for setting up the pie control, activating it, and defining and
 * executing the actions that can be triggered by the pie control.
 */
public class PieController implements BaseStatusBar.NavigationBarCallback, PieView.OnExitListener,
        PieView.OnSnapListener, PieItem.PieOnClickListener, PieItem.PieOnLongClickListener {
    private static final String TAG = "PieController";
    private static final boolean DEBUG = false;

    private boolean mSecondLayerActive;

    private Handler mObservHandler = new Handler();

    public static final float EMPTY_ANGLE = 10;
    public static final float START_ANGLE = 180 + EMPTY_ANGLE;

    private static final int MSG_PIE_GAIN_FOCUS = 1068;

    private final static int MENU_VISIBILITY_ALWAYS = 0;
    private final static int MENU_VISIBILITY_NEVER = 1;
    private final static int MENU_VISIBILITY_SYSTEM = 2;

    private Context mContext;
    private PieView mPieContainer;
    private EdgeGestureManager mPieManager;
    private boolean mAttached = false;
    private boolean mIsDetaching = false;

    private BaseStatusBar mStatusBar;
    private NavigationBarOverlay mNavigationBarOverlay;
    private Vibrator mVibrator;
    private IWindowManager mWm;
    private WindowManager mWindowManager;
    private int mBatteryLevel;
    private int mBatteryStatus;
    private TelephonyManager mTelephonyManager;
    private ServiceState mServiceState;

    // All pie slices that are managed by the controller
    private PieSliceContainer mNavigationSlice;
    private PieSliceContainer mNavigationSliceSecondLayer;
    private PieItem mMenuButton;
    private PieSysInfo mSysInfo;

    private int mNavigationIconHints = 0;
    private int mDisabledFlags = 0;
    private boolean mShowMenu = false;
    private int mShowMenuVisibility;
    private Drawable mBackIcon;
    private Drawable mBackAltIcon;
    private boolean mIconResize = false;
    private float mIconResizeFactor ;

    private int mPieTriggerSlots;
    private int mPieTriggerMask = EdgeGesturePosition.LEFT.FLAG
            | EdgeGesturePosition.BOTTOM.FLAG
            | EdgeGesturePosition.RIGHT.FLAG;
    private boolean mPieTriggerMaskLocked;
    private int mRestorePieTriggerMask;
    private EdgeGesturePosition mPosition;

    private EdgeGestureManager.EdgeGestureActivationListener mPieActivationListener =
            new EdgeGestureManager.EdgeGestureActivationListener(Looper.getMainLooper()) {
        @Override
        public void onEdgeGestureActivation(
                int touchX, int touchY, EdgeGesturePosition position, int flags) {
            if (mPieContainer != null && activateFromListener(touchX, touchY, position)) {
                // give the main thread some time to do the bookkeeping
                mHandler.obtainMessage(MSG_PIE_GAIN_FOCUS).sendToTarget();
            } else {
                mPieActivationListener.restoreListenerState();
            }
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message m) {
            final InputManager inputManager = InputManager.getInstance();
            switch (m.what) {
                case MSG_PIE_GAIN_FOCUS:
                    if (mPieContainer != null) {
                        if (!mPieActivationListener.gainTouchFocus(
                                mPieContainer.getWindowToken())) {
                            mPieContainer.exit();
                        }
                    } else {
                        mPieActivationListener.restoreListenerState();
                    }
                    break;
            }
        }
    };

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SPIE_GRAVITY), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTONS_CONFIG), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SPIE_SIZE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SPIE_BUTTON_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTON_PRESSED_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTON_LONG_PRESSED_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTON_OUTLINE_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_ICON_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_ICON_COLOR_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTON_ALPHA), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTON_PRESSED_ALPHA), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTONS_CONFIG_SECOND_LAYER), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SPIE_MENU), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_IME_CONTROL), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.PIE_BUTTONS_CONFIG_SECOND_LAYER))) {

                ArrayList<ButtonConfig> buttonsConfig =
                        ButtonsHelper.getPieSecondLayerConfig(mContext);
                if (mSecondLayerActive != buttonsConfig.size() > 0) {
                    constructSlices();
                }

            }
            refreshContainer();
        }
    }
    private SettingsObserver mSettingsObserver = new SettingsObserver(mObservHandler);

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                         BatteryManager.BATTERY_STATUS_UNKNOWN);
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)
                        || Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                setupNavigationItems();
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

    public PieController(Context context, BaseStatusBar statusBar,
            EdgeGestureManager pieManager, NavigationBarOverlay nbo) {
        mContext = context;
        mStatusBar = statusBar;
        mNavigationBarOverlay = nbo;

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            mTelephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }

        mPieManager = pieManager;
        mPieManager.setEdgeGestureActivationListener(mPieActivationListener);
    }

    public void refreshContainer() {
        if (mAttached) {
            setupNavigationItems();
            setupListener();
        }
    }

    public void attachContainer() {
        if (!mAttached) {
            mAttached = true;
            setupContainer();
            refreshContainer();
            mSettingsObserver.observe();
        }
    }

    public void detachContainer(boolean onExit) {
        if (mPieContainer == null || !mAttached) {
            return;
        }
        if (isShowing() && !onExit) {
            mIsDetaching = true;
            return;
        }
        mIsDetaching = false;
        mAttached = false;

        mPieManager.updateEdgeGestureActivationListener(mPieActivationListener, 0);

        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);

        mContext.unregisterReceiver(mBroadcastReceiver);

        mPieContainer.clearSlices();
        mPieContainer = null;
    }

    private void setupContainer() {
        if (mPieContainer == null) {
            mPieContainer = new PieView(mContext, mStatusBar, mNavigationBarOverlay);
            mPieContainer.setOnSnapListener(this);
            mPieContainer.setOnExitListener(this);

            if (mTelephonyManager != null) {
                mTelephonyManager.listen(
                        mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
            }

            /**
             * Add intent actions to listen on it.
             * Battery change for the battery,
             * screen off to get rid of the pie,
             * apps available to check if apps on external sdcard
             * are available and reconstruct the button icons
             */
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            mContext.registerReceiver(mBroadcastReceiver, filter);

            // Construct the slices
            constructSlices();
        }

    }

    public void constructSlices() {
        final Resources res = mContext.getResources();

        // Clear the slices
        mPieContainer.clearSlices();

        // Construct navbar slice
        int inner = res.getDimensionPixelSize(R.dimen.pie_navbar_radius);
        int outer = inner + res.getDimensionPixelSize(R.dimen.pie_navbar_height);
        mNavigationSlice = new PieSliceContainer(mPieContainer, PieSlice.IMPORTANT
                | PieDrawable.DISPLAY_ALL);
        mNavigationSlice.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);

        // Construct maybe navbar slice second layer
        ArrayList<ButtonConfig> buttonsConfig = ButtonsHelper.getPieSecondLayerConfig(mContext);
        mSecondLayerActive = buttonsConfig.size() > 0;
        if (mSecondLayerActive) {
            inner = res.getDimensionPixelSize(R.dimen.pie_navbar_second_layer_radius);
            outer = inner + res.getDimensionPixelSize(R.dimen.pie_navbar_height);
            mNavigationSliceSecondLayer = new PieSliceContainer(mPieContainer, PieSlice.IMPORTANT
                    | PieDrawable.DISPLAY_ALL);
            mNavigationSliceSecondLayer.setGeometry(
                    START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        }

        // Setup buttons and add the slices finally
        mPieContainer.addSlice(mNavigationSlice);
        if (mSecondLayerActive) {
            mPieContainer.addSlice(mNavigationSliceSecondLayer);
            // Adjust dimensions for sysinfo when second layer is active
            inner = res.getDimensionPixelSize(R.dimen.pie_sysinfo_second_layer_radius);
        } else {
            inner = res.getDimensionPixelSize(R.dimen.pie_sysinfo_radius);
        }

        // Construct sysinfo slice
        outer = inner + res.getDimensionPixelSize(R.dimen.pie_sysinfo_height);
        mSysInfo = new PieSysInfo(mContext, mPieContainer, this, PieDrawable.DISPLAY_NOT_AT_TOP);
        mSysInfo.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        mPieContainer.addSlice(mSysInfo);
    }

    private void setupListener() {
        ContentResolver resolver = mContext.getContentResolver();

        mPieTriggerSlots = Settings.System.getIntForUser(resolver,
                Settings.System.SPIE_GRAVITY, EdgeGesturePosition.LEFT.FLAG,
                UserHandle.USER_CURRENT);

        int sensitivity = mContext.getResources().getInteger(R.integer.pie_gesture_sensivity);
        if (sensitivity < EdgeServiceConstants.SENSITIVITY_LOWEST
                || sensitivity > EdgeServiceConstants.SENSITIVITY_HIGHEST) {
            sensitivity = EdgeServiceConstants.SENSITIVITY_DEFAULT;
        }

        int flags = mPieTriggerSlots & mPieTriggerMask;

        if (Settings.System.getIntForUser(resolver,
                Settings.System.PIE_IME_CONTROL, 1,
                UserHandle.USER_CURRENT) == 1) {
            flags |= EdgeServiceConstants.IME_CONTROL;
        }

        mPieManager.updateEdgeGestureActivationListener(mPieActivationListener,
                sensitivity<<EdgeServiceConstants.SENSITIVITY_SHIFT | flags);
    }

    private void setupNavigationItems() {
        ContentResolver resolver = mContext.getContentResolver();
        // Get minimum allowed image size for layout
        int minimumImageSize = (int) mContext.getResources().getDimension(R.dimen.spie_item_size);

        mNavigationSlice.clear();

        // Reset mIconResizeFactor
        mIconResizeFactor = 1.0f;
        // Check the size set from the user and set resize values if needed
        float diff = PieView.PIE_ICON_START_SIZE_FACTOR -
                Settings.System.getFloatForUser(resolver,
                        Settings.System.SPIE_SIZE, PieView.PIE_CONTROL_SIZE_DEFAULT,
                        UserHandle.USER_CURRENT);
        if (diff > 0.0f) {
            mIconResize = true;
            mIconResizeFactor = 1.0f - diff;
        } else {
            mIconResize = false;
        }

        // Prepare IME back icon
        mBackAltIcon = mContext.getResources().getDrawable(R.drawable.ic_sysbar_back_ime);
        mBackAltIcon = prepareBackIcon(mBackAltIcon, false);

        ArrayList<ButtonConfig> buttonsConfig;

        // First we construct first buttons layer
        buttonsConfig = ButtonsHelper.getPieConfig(mContext);
        getCustomActionsAndConstruct(resolver, buttonsConfig, false, minimumImageSize);

        if (mSecondLayerActive) {
            // If second layer is active we construct second layer now
            mNavigationSliceSecondLayer.clear();
            buttonsConfig = ButtonsHelper.getPieSecondLayerConfig(mContext);
            getCustomActionsAndConstruct(resolver, buttonsConfig, true, minimumImageSize);
        }

        mShowMenuVisibility = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SPIE_MENU, MENU_VISIBILITY_SYSTEM,
                UserHandle.USER_CURRENT);

        setNavigationIconHints(mNavigationIconHints, true);
        setMenuVisibility(mShowMenu);
    }

    private void getCustomActionsAndConstruct(ContentResolver resolver,
            ArrayList<ButtonConfig> buttonsConfig, boolean secondLayer, int minimumImageSize) {

        int buttonWidth = 10 / buttonsConfig.size();
        ButtonConfig buttonConfig;

        for (int j = 0; j < buttonsConfig.size(); j++) {
            buttonConfig = buttonsConfig.get(j);
            if (secondLayer) {
                addItemToLayer(mNavigationSliceSecondLayer,
                        buttonConfig, buttonWidth, minimumImageSize);
            } else {
                addItemToLayer(mNavigationSlice,
                        buttonConfig, buttonWidth, minimumImageSize);
            }
        }

        if (!secondLayer) {
            mMenuButton = constructItem(1, ButtonsConstants.ACTION_MENU,
                    ButtonsConstants.ACTION_NULL,
                    ButtonsConstants.ICON_EMPTY,
                    minimumImageSize);
            mNavigationSlice.addItem(mMenuButton);
        }
    }

    private void addItemToLayer(PieSliceContainer layer, ButtonConfig buttonConfig,
            int buttonWidth, int minimumImageSize) {
        layer.addItem(constructItem(buttonWidth,
                buttonConfig.getClickAction(),
                buttonConfig.getLongpressAction(),
                buttonConfig.getIcon(), minimumImageSize));

        if (buttonConfig.getClickAction().equals(ButtonsConstants.ACTION_HOME)) {
            layer.addItem(constructItem(buttonWidth,
                    ButtonsConstants.ACTION_KEYGUARD_SEARCH,
                    buttonConfig.getLongpressAction(),
                    ButtonsConstants.ICON_EMPTY,
                    minimumImageSize));
        }
    }

    private PieItem constructItem(int width, String clickAction, String longPressAction,
                String iconUri, int minimumImageSize) {
        ImageView view = new ImageView(mContext);
        int iconType = setPieItemIcon(view, iconUri, clickAction);
        view.setMinimumWidth(minimumImageSize);
        view.setMinimumHeight(minimumImageSize);
        LayoutParams lp = new LayoutParams(minimumImageSize, minimumImageSize);
        view.setLayoutParams(lp);
        PieItem item = new PieItem(mContext, mPieContainer, 0, width, clickAction,
                longPressAction, view, iconType);
        item.setOnClickListener(this);
        if (!longPressAction.equals(ButtonsConstants.ACTION_NULL)) {
            item.setOnLongClickListener(this);
        }
        return item;
    }

    private int setPieItemIcon(ImageView view, String iconUri, String clickAction) {
        Drawable d = ButtonsHelper.getButtonIconImage(mContext, clickAction, iconUri);
        if (d != null) {
            view.setImageDrawable(d);
        }

        if (iconUri != null && !iconUri.equals(ButtonsConstants.ICON_EMPTY)
            && !iconUri.startsWith(ButtonsConstants.SYSTEM_ICON_IDENTIFIER)) {
            if (clickAction.equals(ButtonsConstants.ACTION_BACK)) {
                // Back icon image needs to be handled seperatly.
                // All other is handled in PieItem.
                mBackIcon = prepareBackIcon(d, true);
            } else {
                // Custom images need to be forced to resize to fit better
                resizeIcon(view, null, true);
            }
            return 2;
        } else {
            if (clickAction.startsWith("**")
                    || iconUri.startsWith(ButtonsConstants.SYSTEM_ICON_IDENTIFIER)) {
                if (clickAction.equals(ButtonsConstants.ACTION_BACK)) {
                    mBackIcon = prepareBackIcon(d, false);
                }
                if (mIconResize) {
                    resizeIcon(view, null, false);
                }
                return 0;
            }
            resizeIcon(view, null, true);
            return 1;
        }
    }

    private Drawable resizeIcon(ImageView view, Drawable d, boolean useSystemDimens) {
        int size = 0;
        Drawable dOriginal = d;
        if (d == null) {
            dOriginal = view.getDrawable();
        }
        if (useSystemDimens) {
            size = mContext.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.app_icon_size);
        } else {
            size = Math.max(dOriginal.getIntrinsicHeight(), dOriginal.getIntrinsicWidth());
        }

        Drawable dResized = ImageHelper.resize(
                mContext, dOriginal, Converter.pxToDp(mContext, (int) (size * mIconResizeFactor)));
        if (d == null) {
            view.setImageDrawable(dResized);
            return null;
        } else {
            return (dResized);
        }
    }

    private Drawable prepareBackIcon(Drawable d, boolean customIcon) {
        int customImageColorize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ICON_COLOR_MODE, 0,
                UserHandle.USER_CURRENT);
        int drawableColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ICON_COLOR, -2, UserHandle.USER_CURRENT);
        if (drawableColor == -2) {
            drawableColor = mContext.getResources().getColor(R.color.pie_foreground_color);
        }
        if (mIconResize && !customIcon) {
            d = resizeIcon(null, d, false);
        } else if (customIcon) {
            d = resizeIcon(null, d, true);
        }
        if ((customImageColorize != 1 || !customIcon) && customImageColorize != 3) {
            d = new BitmapDrawable(mContext.getResources(),
                    ImageHelper.getColoredBitmap(d, drawableColor));
        }
        return d;
    }

    public boolean activateFromListener(int touchX, int touchY, EdgeGesturePosition position) {
        if (isShowing()) {
            return false;
        }

        doHapticTriggerFeedback();
        mPosition = position;
        Point center = new Point(touchX, touchY);
        mPieContainer.setSnapPoints(mPieTriggerMask & ~mPieTriggerSlots);
        mPieContainer.activate(center, position);
        mWindowManager.addView(mPieContainer, generateLayoutParam());
        return true;
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
        // Turn on hardware acceleration for high end gfx devices.
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            lp.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
        }
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
        if (mIsDetaching) {
            detachContainer(true);
        }
    }

    public void updatePieTriggerMask(int newMask, boolean lock) {
        if (mPieTriggerMaskLocked) {
            // Outside call.
            // Update mask is currently locked. Save new requested mask
            // till lock is released and can be restored.
            mRestorePieTriggerMask = newMask;
            return;
        }
        int oldState = mPieTriggerSlots & mPieTriggerMask;
        if (lock) {
            // Lock update is requested. Save old state
            // to restore it later on release if no other mask
            // updates are requested inbetween.
            mPieTriggerMaskLocked = true;
            mRestorePieTriggerMask = oldState;
        }
        mPieTriggerMask = newMask;

        // Check if we are active and if it would make a change at all
        if (mPieContainer != null
                && ((mPieTriggerSlots & mPieTriggerMask) != oldState)) {
            setupListener();
        }
    }

    public void restorePieTriggerMask() {
        if (!mPieTriggerMaskLocked) {
            return;
        }
        // Restore last trigger mask
        mPieTriggerMaskLocked = false;
        updatePieTriggerMask(mRestorePieTriggerMask, false);
    }

    @Override
    public void setNavigationIconHints(int hints) {
        // This call may come from outside.
        // Check if we already have a navigation slice to manipulate
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
        PieItem item;

        for (int j = 0; j < 2; j++) {
            item = findItem(ButtonsConstants.ACTION_BACK, j);
            if (item != null) {
                boolean isAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
                item.setImageDrawable(isAlt ? mBackAltIcon : mBackIcon);
            }
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    private PieItem findItem(String type, int secondLayer) {
        if (secondLayer == 1) {
            if (mSecondLayerActive && mNavigationSliceSecondLayer != null) {
                for (PieItem item : mNavigationSliceSecondLayer.getItems()) {
                    String itemType = (String) item.tag;
                    if (type.equals(itemType)) {
                       return item;
                    }
                }
            }
        } else {
            for (PieItem item : mNavigationSlice.getItems()) {
                String itemType = (String) item.tag;
                if (type.equals(itemType)) {
                   return item;
                }
            }
        }

        return null;
    }

    @Override
    public void setDisabledFlags(int disabledFlags) {
        // This call may come from outside.
        // Check if we already have a navigation slice to manipulate
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

        PieItem item;
        for (int j = 0; j < 2; j++) {
            item = findItem(ButtonsConstants.ACTION_BACK, j);
            if (item != null) {
                item.show(!disableBack);
            }
            item = findItem(ButtonsConstants.ACTION_HOME, j);
            if (item != null) {
                item.show(!disableHome);
                // If the homebutton exists we can assume that the keyguard
                // search button exists as well.
                item = findItem(ButtonsConstants.ACTION_KEYGUARD_SEARCH, j);
                item.show(disableHome);
            }
            item = findItem(ButtonsConstants.ACTION_RECENTS, j);
            if (item != null) {
                item.show(!disableRecent);
            }
        }
        setMenuVisibility(mShowMenu, true);
    }

    @Override
    public void setMenuVisibility(boolean showMenu) {
        setMenuVisibility(showMenu, false);
    }

    private void setMenuVisibility(boolean showMenu, boolean force) {
        if (!force && mShowMenu == showMenu) {
            return;
        }
        if (mMenuButton != null) {
            final boolean disableRecent = ((mDisabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
            mMenuButton.show((showMenu || mShowMenuVisibility == MENU_VISIBILITY_ALWAYS)
                && mShowMenuVisibility != MENU_VISIBILITY_NEVER && !disableRecent);
        }
        mShowMenu = showMenu;
    }

    @Override
    public void onSnap(EdgeGesturePosition position) {
        if (position == mPosition) {
            return;
        }

        doHapticTriggerFeedback();

        if (DEBUG) {
            Slog.d(TAG, "onSnap from " + position.name());
        }

        int triggerSlots = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SPIE_GRAVITY, EdgeGesturePosition.LEFT.FLAG,
                UserHandle.USER_CURRENT);

        triggerSlots = triggerSlots & ~mPosition.FLAG | position.FLAG;

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SPIE_GRAVITY, triggerSlots,
                UserHandle.USER_CURRENT);
    }

    @Override
    public void onLongClick(PieItem item) {
        String type = (String) item.longTag;
        if (!SlimActions.isActionKeyEvent(type)) {
            mPieContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        mPieContainer.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        SlimActions.processAction(mContext, type, true);
    }

    @Override
    public void onClick(PieItem item) {
        String type = (String) item.tag;
        if (!SlimActions.isActionKeyEvent(type)) {
            mPieContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (!type.equals(ButtonsConstants.ACTION_MENU)) {
            mPieContainer.playSoundEffect(SoundEffectConstants.CLICK);
        }
        mPieContainer.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        SlimActions.processAction(mContext, type, false);
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

    public boolean isShowing() {
        return mPieContainer != null && mPieContainer.isShowing();
    }

    public String getOperatorState() {
        if (mTelephonyManager == null) {
            return null;
        }
        if (mServiceState == null
                || mServiceState.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
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
