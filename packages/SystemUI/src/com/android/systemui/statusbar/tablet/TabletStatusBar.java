/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.tablet;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.CustomTheme;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.DoNotDisturb;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CircleBattery;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.CompatModeButton;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.Prefs;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class TabletStatusBar extends BaseStatusBar implements
        InputMethodsPanel.OnHardKeyboardEnabledChangeListener {
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_COMPAT_HELP = false;
    public static final String TAG = "TabletStatusBar";


    public static final int MSG_OPEN_NOTIFICATION_PANEL = 1000;
    public static final int MSG_CLOSE_NOTIFICATION_PANEL = 1001;
    public static final int MSG_OPEN_NOTIFICATION_PEEK = 1002;
    public static final int MSG_CLOSE_NOTIFICATION_PEEK = 1003;
    // 1020-1029 reserved for BaseStatusBar
    public static final int MSG_SHOW_CHROME = 1030;
    public static final int MSG_HIDE_CHROME = 1031;
    public static final int MSG_OPEN_INPUT_METHODS_PANEL = 1040;
    public static final int MSG_CLOSE_INPUT_METHODS_PANEL = 1041;
    public static final int MSG_OPEN_COMPAT_MODE_PANEL = 1050;
    public static final int MSG_CLOSE_COMPAT_MODE_PANEL = 1051;
    public static final int MSG_STOP_TICKER = 2000;

    // Fitts' Law assistance for LatinIME; see policy.EventHole
    private static final boolean FAKE_SPACE_BAR = true;

    // Notification "peeking" (flyover preview of individual notifications)
    final static int NOTIFICATION_PEEK_HOLD_THRESH = 200; // ms
    final static int NOTIFICATION_PEEK_FADE_DELAY = 3000; // ms

    private static final int NOTIFICATION_PRIORITY_MULTIPLIER = 10; // see NotificationManagerService
    private static final int HIDE_ICONS_BELOW_SCORE = Notification.PRIORITY_LOW * NOTIFICATION_PRIORITY_MULTIPLIER;

    // The height of the bar, as definied by the build.  It may be taller if we're plugged
    // into hdmi.
    int mNaturalBarHeight = -1;
    int mIconSize = -1;
    int mIconHPadding = -1;
    int mNavIconWidth = -1;
    int mMenuNavIconWidth = -1;
    private int mMaxNotificationIcons = 5;

    TabletStatusBarView mStatusBarView;
    View mNotificationArea;
    View mNotificationTrigger;
    NotificationIconArea mNotificationIconArea;
    ViewGroup mNavigationArea;

    boolean mNotificationDNDMode;
    NotificationData.Entry mNotificationDNDDummyEntry;

    ImageView mBackButton;
    View mHomeButton;
    View mMenuButton;
    View mRecentButton;
    private boolean mAltBackButtonEnabledForIme;

    ViewGroup mFeedbackIconArea; // notification icons, IME icon, compat icon
    InputMethodButton mInputMethodSwitchButton;
    CompatModeButton mCompatModeButton;

    NotificationPanel mNotificationPanel;
    WindowManager.LayoutParams mNotificationPanelParams;
    NotificationPeekPanel mNotificationPeekWindow;
    ViewGroup mNotificationPeekRow;
    int mNotificationPeekIndex;
    IBinder mNotificationPeekKey;
    LayoutTransition mNotificationPeekScrubLeft, mNotificationPeekScrubRight;

    int mNotificationPeekTapDuration;
    int mNotificationFlingVelocity;

    BatteryController mBatteryController;
    BluetoothController mBluetoothController;
    LocationController mLocationController;
    NetworkController mNetworkController;
    DoNotDisturb mDoNotDisturb;

    ViewGroup mBarContents;

    SignalClusterView mSignalView;
    Clock mClock;

    // hide system chrome ("lights out") support
    View mShadow;

    NotificationIconArea.IconLayout mIconLayout;

    TabletTicker mTicker;

    View mFakeSpaceBar;
    KeyEvent mSpaceBarKeyEvent = null;

    View mCompatibilityHelpDialog = null;

    // for disabling the status bar
    int mDisabled = 0;

    private InputMethodsPanel mInputMethodsPanel;
    private CompatModePanel mCompatModePanel;

    private int mSystemUiVisibility = 0;

    private int mNavigationIconHints = 0;

    private int mShowSearchHoldoff = 0;

    public Context getContext() { return mContext; }

    private Runnable mShowSearchPanel = new Runnable() {
        public void run() {
            showSearchPanel();
        }
    };

    private View.OnTouchListener mHomeSearchActionListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!shouldDisableNavbarGestures() && !inKeyguardRestrictedInputMode()) {
                        mHandler.removeCallbacks(mShowSearchPanel);
                        mHandler.postDelayed(mShowSearchPanel, mShowSearchHoldoff);
                    }
                break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeCallbacks(mShowSearchPanel);
                break;
            }
            return false;
        }
    };

    @Override
    protected void createAndAddWindows() {
        addStatusBarWindow();
        addPanelWindows();
    }

    private void addStatusBarWindow() {
        final View sb = makeStatusBarView();

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.OPAQUE);

        // We explicitly leave FLAG_HARDWARE_ACCELERATED out of the flags.  The status bar occupies
        // very little screen real-estate and is updated fairly frequently.  By using CPU rendering
        // for the status bar, we prevent the GPU from having to wake up just to do these small
        // updates, which should help keep power consumption down.

        lp.gravity = getStatusBarGravity();
        lp.setTitle("SystemBar");
        lp.packageName = mContext.getPackageName();
        mWindowManager.addView(sb, lp);
    }

    // last theme that was applied in order to detect theme change (as opposed
    // to some other configuration change).
    CustomTheme mCurrentTheme;
    private boolean mRecreating = false;


    protected void addPanelWindows() {
        final Context context = mContext;
        final Resources res = mContext.getResources();

        // Notification Panel
        mNotificationPanel = (NotificationPanel)View.inflate(context,
                R.layout.system_bar_notification_panel, null);
        mNotificationPanel.setBar(this);
        mNotificationPanel.show(false, false);
        mNotificationPanel.setOnTouchListener(
                new TouchOutsideListener(MSG_CLOSE_NOTIFICATION_PANEL, mNotificationPanel));

        // the battery icon
        mBatteryController.addIconView((ImageView)mNotificationPanel.findViewById(R.id.battery));
        mBatteryController.addLabelView(
                (TextView)mNotificationPanel.findViewById(R.id.battery_text));

        // Bt
        mBluetoothController.addIconView(
                (ImageView)mNotificationPanel.findViewById(R.id.bluetooth));

        // network icons: either a combo icon that switches between mobile and data, or distinct
        // mobile and data icons
        final ImageView mobileRSSI =
                (ImageView)mNotificationPanel.findViewById(R.id.mobile_signal);
        if (mobileRSSI != null) {
            mNetworkController.addPhoneSignalIconView(mobileRSSI);
        }
        final ImageView wifiRSSI =
                (ImageView)mNotificationPanel.findViewById(R.id.wifi_signal);
        if (wifiRSSI != null) {
            mNetworkController.addWifiIconView(wifiRSSI);
        }
        mNetworkController.addWifiLabelView(
                (TextView)mNotificationPanel.findViewById(R.id.wifi_text));

        mNetworkController.addDataTypeIconView(
                (ImageView)mNotificationPanel.findViewById(R.id.mobile_type));
        mNetworkController.addMobileLabelView(
                (TextView)mNotificationPanel.findViewById(R.id.mobile_text));
        mNetworkController.addCombinedLabelView(
                (TextView)mBarContents.findViewById(R.id.network_text));

        mStatusBarView.setIgnoreChildren(0, mNotificationTrigger, mNotificationPanel);

        WindowManager.LayoutParams lp = mNotificationPanelParams = new WindowManager.LayoutParams(
                res.getDimensionPixelSize(R.dimen.notification_panel_width),
                getNotificationPanelHeight(),
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setTitle("NotificationPanel");
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        lp.windowAnimations = com.android.internal.R.style.Animation; // == no animation
//        lp.windowAnimations = com.android.internal.R.style.Animation_ZoomButtons; // simple fade

        mWindowManager.addView(mNotificationPanel, lp);

        // Search Panel
        mStatusBarView.setBar(this);
        mHomeButton.setOnTouchListener(mHomeSearchActionListener);
        updateSearchPanel();

        // Input methods Panel
        mInputMethodsPanel = (InputMethodsPanel) View.inflate(context,
                R.layout.system_bar_input_methods_panel, null);
        mInputMethodsPanel.setHardKeyboardEnabledChangeListener(this);
        mInputMethodsPanel.setOnTouchListener(new TouchOutsideListener(
                MSG_CLOSE_INPUT_METHODS_PANEL, mInputMethodsPanel));
        mInputMethodsPanel.setImeSwitchButton(mInputMethodSwitchButton);
        mStatusBarView.setIgnoreChildren(2, mInputMethodSwitchButton, mInputMethodsPanel);
        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setTitle("InputMethodsPanel");
        lp.windowAnimations = R.style.Animation_RecentPanel;

        mWindowManager.addView(mInputMethodsPanel, lp);

        // Compatibility mode selector panel
        mCompatModePanel = (CompatModePanel) View.inflate(context,
                R.layout.system_bar_compat_mode_panel, null);
        mCompatModePanel.setOnTouchListener(new TouchOutsideListener(
                MSG_CLOSE_COMPAT_MODE_PANEL, mCompatModePanel));
        mCompatModePanel.setTrigger(mCompatModeButton);
        mCompatModePanel.setVisibility(View.GONE);
        mStatusBarView.setIgnoreChildren(3, mCompatModeButton, mCompatModePanel);
        lp = new WindowManager.LayoutParams(
                250,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setTitle("CompatModePanel");
        lp.windowAnimations = android.R.style.Animation_Dialog;

        mWindowManager.addView(mCompatModePanel, lp);

        mRecentButton.setOnTouchListener(mRecentsPreloadOnTouchListener);

        mPile = (NotificationRowLayout)mNotificationPanel.findViewById(R.id.content);
        mPile.removeAllViews();
        mPile.setLongPressListener(getNotificationLongClicker());

        ScrollView scroller = (ScrollView)mPile.getParent();
        scroller.setFillViewport(true);
    }

    @Override
    protected int getExpandedViewMaxHeight() {
        return getNotificationPanelHeight();
    }

    @Override
    protected boolean isNotificationPanelFullyVisible() {
        return mNotificationPanel.getVisibility() == View.VISIBLE;
    }

    private int getNotificationPanelHeight() {
        final Resources res = mContext.getResources();
        final Display d = mWindowManager.getDefaultDisplay();
        final Point size = new Point();
        d.getRealSize(size);
        return Math.max(res.getDimensionPixelSize(R.dimen.notification_panel_min_height), size.y);
    }

    @Override
    public void start() {
        super.start(); // will add the main bar view
    }

    private static void copyNotifications(ArrayList<Pair<IBinder, StatusBarNotification>> dest,
            NotificationData source) {
        int N = source.size();
        for (int i = 0; i < N; i++) {
            NotificationData.Entry entry = source.get(i);
            dest.add(Pair.create(entry.key, entry.notification));
        }
    }

    private void recreateStatusBar() {
        mRecreating = true;
        mStatusBarContainer.removeAllViews();

        // extract notifications.
        int nNotifs = mNotificationData.size();
        ArrayList<Pair<IBinder, StatusBarNotification>> notifications =
                new ArrayList<Pair<IBinder, StatusBarNotification>>(nNotifs);
        copyNotifications(notifications, mNotificationData);
        mNotificationData.clear();

        mStatusBarContainer.addView(makeStatusBarView());

        // recreate notifications.
        for (int i = 0; i < nNotifs; i++) {
            Pair<IBinder, StatusBarNotification> notifData = notifications.get(i);
            addNotificationViews(notifData.first, notifData.second);
        }

        setAreThereNotifications();

        mRecreating = false;
    }


    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        // detect theme change.
        CustomTheme newTheme = mContext.getResources().getConfiguration().customTheme;
        if (newTheme != null &&
                (mCurrentTheme == null || !mCurrentTheme.equals(newTheme))) {
            mCurrentTheme = (CustomTheme)newTheme.clone();
            recreateStatusBar();
        }
        loadDimens();
        mNotificationPanelParams.height = getNotificationPanelHeight();
        mWindowManager.updateViewLayout(mNotificationPanel, mNotificationPanelParams);
        mShowSearchHoldoff = mContext.getResources().getInteger(
                R.integer.config_show_search_delay);
        updateSearchPanel();
    }

    protected void loadDimens() {
        final Resources res = mContext.getResources();

        mNaturalBarHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height);

        int newIconSize = res.getDimensionPixelSize(
            com.android.internal.R.dimen.system_bar_icon_size);
        int newIconHPadding = res.getDimensionPixelSize(
            R.dimen.status_bar_icon_padding);
        int newNavIconWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        int newMenuNavIconWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);

        if (mNavigationArea != null && newNavIconWidth != mNavIconWidth) {
            mNavIconWidth = newNavIconWidth;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                     mNavIconWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            mBackButton.setLayoutParams(lp);
            mHomeButton.setLayoutParams(lp);
            mRecentButton.setLayoutParams(lp);
        }

        if (mNavigationArea != null && newMenuNavIconWidth != mMenuNavIconWidth) {
            mMenuNavIconWidth = newMenuNavIconWidth;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                     mMenuNavIconWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            mMenuButton.setLayoutParams(lp);
        }

        if (newIconHPadding != mIconHPadding || newIconSize != mIconSize) {
//            Slog.d(TAG, "size=" + newIconSize + " padding=" + newIconHPadding);
            mIconHPadding = newIconHPadding;
            mIconSize = newIconSize;
            reloadAllNotificationIcons(); // reload the tray
        }

        final int numIcons = res.getInteger(R.integer.config_maxNotificationIcons);
        if (numIcons != mMaxNotificationIcons) {
            mMaxNotificationIcons = numIcons;
            if (DEBUG) Slog.d(TAG, "max notification icons: " + mMaxNotificationIcons);
            reloadAllNotificationIcons();
        }
    }

    @Override
    public View getStatusBarView() {
        return mStatusBarView;
    }

    protected View makeStatusBarView() {
        final Context context = mContext;

        CustomTheme currentTheme = mContext.getResources().getConfiguration().customTheme;
        if (currentTheme != null) {
            mCurrentTheme = (CustomTheme)currentTheme.clone();
        }

        loadDimens();

        final TabletStatusBarView sb = (TabletStatusBarView)View.inflate(
                context, R.layout.system_bar, null);
        mStatusBarView = sb;

        sb.setHandler(mHandler);

        try {
            // Sanity-check that someone hasn't set up the config wrong and asked for a navigation
            // bar on a tablet that has only the system bar
            if (mWindowManagerService.hasNavigationBar()) {
                Slog.e(TAG, "Tablet device cannot show navigation bar and system bar");
            }
        } catch (RemoteException ex) {
        }

        mBarContents = (ViewGroup) sb.findViewById(R.id.bar_contents);

        // the whole right-hand side of the bar
        mNotificationArea = sb.findViewById(R.id.notificationArea);
        mNotificationArea.setOnTouchListener(new NotificationTriggerTouchListener());

        // the button to open the notification area
        mNotificationTrigger = sb.findViewById(R.id.notificationTrigger);

        // the more notifications icon
        mNotificationIconArea = (NotificationIconArea)sb.findViewById(R.id.notificationIcons);

        // where the icons go
        mIconLayout = (NotificationIconArea.IconLayout) sb.findViewById(R.id.icons);

        mNotificationPeekTapDuration = ViewConfiguration.getTapTimeout();
        mNotificationFlingVelocity = 300; // px/s

        mTicker = new TabletTicker(this);

        // The icons
        mLocationController = new LocationController(mContext); // will post a notification

        // watch the PREF_DO_NOT_DISTURB and convert to appropriate disable() calls
        mDoNotDisturb = new DoNotDisturb(mContext);

        mBatteryController = new BatteryController(mContext);
        mBatteryController.addIconView((ImageView)sb.findViewById(R.id.battery));

        final CircleBattery circleBattery =
                (CircleBattery) sb.findViewById(R.id.circle_battery);
        mBatteryController.addStateChangedCallback(circleBattery);

        mBluetoothController = new BluetoothController(mContext);
        mBluetoothController.addIconView((ImageView)sb.findViewById(R.id.bluetooth));

        mNetworkController = new NetworkController(mContext);
        mSignalView = (SignalClusterView) sb.findViewById(R.id.signal_cluster);
        mNetworkController.addSignalCluster(mSignalView);

        mClock = (Clock) sb.findViewById(R.id.clock);

        // The navigation buttons
        mBackButton = (ImageView)sb.findViewById(R.id.back);
        mNavigationArea = (ViewGroup) sb.findViewById(R.id.navigationArea);
        mHomeButton = mNavigationArea.findViewById(R.id.home);
        mMenuButton = mNavigationArea.findViewById(R.id.menu);
        mRecentButton = mNavigationArea.findViewById(R.id.recent_apps);
        mRecentButton.setOnClickListener(mOnClickListener);

        LayoutTransition lt = new LayoutTransition();
        lt.setDuration(250);
        // don't wait for these transitions; we just want icons to fade in/out, not move around
        lt.setDuration(LayoutTransition.CHANGE_APPEARING, 0);
        lt.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 0);
        lt.addTransitionListener(new LayoutTransition.TransitionListener() {
            public void endTransition(LayoutTransition transition, ViewGroup container,
                    View view, int transitionType) {
                // ensure the menu button doesn't stick around on the status bar after it's been
                // removed
                mBarContents.invalidate();
            }
            public void startTransition(LayoutTransition transition, ViewGroup container,
                    View view, int transitionType) {}
        });
        mNavigationArea.setLayoutTransition(lt);
        // no multi-touch on the nav buttons
        mNavigationArea.setMotionEventSplittingEnabled(false);

        // The bar contents buttons
        mFeedbackIconArea = (ViewGroup)sb.findViewById(R.id.feedbackIconArea);
        mInputMethodSwitchButton = (InputMethodButton) sb.findViewById(R.id.imeSwitchButton);
        // Overwrite the lister
        mInputMethodSwitchButton.setOnClickListener(mOnClickListener);

        mCompatModeButton = (CompatModeButton) sb.findViewById(R.id.compatModeButton);
        mCompatModeButton.setOnClickListener(mOnClickListener);
        mCompatModeButton.setVisibility(View.GONE);

        // for redirecting errant bar taps to the IME
        mFakeSpaceBar = sb.findViewById(R.id.fake_space_bar);

        // "shadows" of the status bar features, for lights-out mode
        mShadow = sb.findViewById(R.id.bar_shadow);
        mShadow.setOnTouchListener(
            new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent ev) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        // even though setting the systemUI visibility below will turn these views
                        // on, we need them to come up faster so that they can catch this motion
                        // event
                        mShadow.setVisibility(View.GONE);
                        mBarContents.setVisibility(View.VISIBLE);

                        try {
                            mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
                        } catch (RemoteException ex) {
                            // system process dead
                        }
                    }
                    return false;
                }
            });

        // tuning parameters
        final int LIGHTS_GOING_OUT_SYSBAR_DURATION = 750;
        final int LIGHTS_GOING_OUT_SHADOW_DURATION = 750;
        final int LIGHTS_GOING_OUT_SHADOW_DELAY    = 0;

        final int LIGHTS_COMING_UP_SYSBAR_DURATION = 200;
//        final int LIGHTS_COMING_UP_SYSBAR_DELAY    = 50;
        final int LIGHTS_COMING_UP_SHADOW_DURATION = 0;

        LayoutTransition xition = new LayoutTransition();
        xition.setAnimator(LayoutTransition.APPEARING,
               ObjectAnimator.ofFloat(null, "alpha", 0.5f, 1f));
        xition.setDuration(LayoutTransition.APPEARING, LIGHTS_COMING_UP_SYSBAR_DURATION);
        xition.setStartDelay(LayoutTransition.APPEARING, 0);
        xition.setAnimator(LayoutTransition.DISAPPEARING,
               ObjectAnimator.ofFloat(null, "alpha", 1f, 0f));
        xition.setDuration(LayoutTransition.DISAPPEARING, LIGHTS_GOING_OUT_SYSBAR_DURATION);
        xition.setStartDelay(LayoutTransition.DISAPPEARING, 0);
        ((ViewGroup)sb.findViewById(R.id.bar_contents_holder)).setLayoutTransition(xition);

        xition = new LayoutTransition();
        xition.setAnimator(LayoutTransition.APPEARING,
               ObjectAnimator.ofFloat(null, "alpha", 0f, 1f));
        xition.setDuration(LayoutTransition.APPEARING, LIGHTS_GOING_OUT_SHADOW_DURATION);
        xition.setStartDelay(LayoutTransition.APPEARING, LIGHTS_GOING_OUT_SHADOW_DELAY);
        xition.setAnimator(LayoutTransition.DISAPPEARING,
               ObjectAnimator.ofFloat(null, "alpha", 1f, 0f));
        xition.setDuration(LayoutTransition.DISAPPEARING, LIGHTS_COMING_UP_SHADOW_DURATION);
        xition.setStartDelay(LayoutTransition.DISAPPEARING, 0);
        ((ViewGroup)sb.findViewById(R.id.bar_shadow_holder)).setLayoutTransition(xition);

        // set the initial view visibility
        setAreThereNotifications();

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mBroadcastReceiver, filter);

        return sb;
    }

    @Override
    protected WindowManager.LayoutParams getRecentsLayoutParams(LayoutParams layoutParams) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) mContext.getResources().getDimension(R.dimen.status_bar_recents_width),
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        lp.setTitle("RecentsPanel");
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;

        return lp;
    }

    @Override
    protected WindowManager.LayoutParams getSearchLayoutParams(LayoutParams layoutParams) {
        boolean opaque = false;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                (opaque ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT));
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        } else {
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            lp.dimAmount = 0.7f;
        }
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        lp.setTitle("SearchPanel");
        // TODO: Define custom animation for Search panel
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    @Override
    protected void updateSearchPanel() {
        super.updateSearchPanel();
        mSearchPanelView.setStatusBarView(mStatusBarView);
        mStatusBarView.setDelegateView(mSearchPanelView);
    }

    @Override
    public void showSearchPanel() {
        super.showSearchPanel();
        WindowManager.LayoutParams lp =
            (android.view.WindowManager.LayoutParams) mStatusBarView.getLayoutParams();
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mWindowManager.updateViewLayout(mStatusBarView, lp);
    }

    @Override
    public void hideSearchPanel() {
        super.hideSearchPanel();
        WindowManager.LayoutParams lp =
            (android.view.WindowManager.LayoutParams) mStatusBarView.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mWindowManager.updateViewLayout(mStatusBarView, lp);
    }

    public int getStatusBarHeight() {
        return mStatusBarView != null ? mStatusBarView.getHeight()
                : mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_height);
    }

    protected int getStatusBarGravity() {
        return Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
    }

    public void onBarHeightChanged(int height) {
        final WindowManager.LayoutParams lp
                = (WindowManager.LayoutParams)mStatusBarContainer.getLayoutParams();
        if (lp == null) {
            // haven't been added yet
            return;
        }
        if (lp.height != height) {
            lp.height = height;
            mWindowManager.updateViewLayout(mStatusBarContainer, lp);
        }
    }

    @Override
    protected BaseStatusBar.H createHandler() {
        return new TabletStatusBar.H();
    }

    private class H extends BaseStatusBar.H {
        public void handleMessage(Message m) {
            super.handleMessage(m);
            switch (m.what) {
                case MSG_OPEN_NOTIFICATION_PEEK:
                    if (DEBUG) Slog.d(TAG, "opening notification peek window; arg=" + m.arg1);

                    if (m.arg1 >= 0) {
                        final int N = mNotificationData.size();

                        if (!mNotificationDNDMode) {
                            if (mNotificationPeekIndex >= 0 && mNotificationPeekIndex < N) {
                                NotificationData.Entry entry = mNotificationData.get(N-1-mNotificationPeekIndex);
                                entry.icon.setBackgroundColor(0);
                                mNotificationPeekIndex = -1;
                                mNotificationPeekKey = null;
                            }
                        }

                        final int peekIndex = m.arg1;
                        if (peekIndex < N) {
                            //Slog.d(TAG, "loading peek: " + peekIndex);
                            NotificationData.Entry entry =
                                mNotificationDNDMode
                                    ? mNotificationDNDDummyEntry
                                    : mNotificationData.get(N-1-peekIndex);
                            NotificationData.Entry copy = new NotificationData.Entry(
                                    entry.key,
                                    entry.notification,
                                    entry.icon);
                            inflateViews(copy, mNotificationPeekRow);

                            if (mNotificationDNDMode) {
                                copy.content.setOnClickListener(new View.OnClickListener() {
                                    public void onClick(View v) {
                                        SharedPreferences.Editor editor = Prefs.edit(mContext);
                                        editor.putBoolean(Prefs.DO_NOT_DISTURB_PREF, false);
                                        editor.apply();
                                        animateCollapsePanels();
                                        visibilityChanged(false);
                                    }
                                });
                            }

                            entry.icon.setBackgroundColor(0x20FFFFFF);

//                          mNotificationPeekRow.setLayoutTransition(
//                              peekIndex < mNotificationPeekIndex
//                                  ? mNotificationPeekScrubLeft
//                                  : mNotificationPeekScrubRight);

                            mNotificationPeekRow.removeAllViews();
                            mNotificationPeekRow.addView(copy.row);

                            mNotificationPeekWindow.setVisibility(View.VISIBLE);
                            mNotificationPanel.show(false, true);

                            mNotificationPeekIndex = peekIndex;
                            mNotificationPeekKey = entry.key;
                        }
                    }
                    break;
                case MSG_CLOSE_NOTIFICATION_PEEK:
                    if (DEBUG) Slog.d(TAG, "closing notification peek window");
                    mNotificationPeekWindow.setVisibility(View.GONE);
                    mNotificationPeekRow.removeAllViews();

                    final int N = mNotificationData.size();
                    if (mNotificationPeekIndex >= 0 && mNotificationPeekIndex < N) {
                        NotificationData.Entry entry =
                            mNotificationDNDMode
                                ? mNotificationDNDDummyEntry
                                : mNotificationData.get(N-1-mNotificationPeekIndex);
                        entry.icon.setBackgroundColor(0);
                    }

                    mNotificationPeekIndex = -1;
                    mNotificationPeekKey = null;
                    break;
                case MSG_OPEN_NOTIFICATION_PANEL:
                    if (DEBUG) Slog.d(TAG, "opening notifications panel");
                    if (!mNotificationPanel.isShowing()) {
                        mNotificationPanel.show(true, true);
                        mNotificationArea.setVisibility(View.INVISIBLE);
                        mTicker.halt();
                    }
                    break;
                case MSG_CLOSE_NOTIFICATION_PANEL:
                    if (DEBUG) Slog.d(TAG, "closing notifications panel");
                    if (mNotificationPanel.isShowing()) {
                        mNotificationPanel.show(false, true);
                        mNotificationArea.setVisibility(View.VISIBLE);
                    }
                    break;
                case MSG_OPEN_INPUT_METHODS_PANEL:
                    if (DEBUG) Slog.d(TAG, "opening input methods panel");
                    if (mInputMethodsPanel != null) mInputMethodsPanel.openPanel();
                    break;
                case MSG_CLOSE_INPUT_METHODS_PANEL:
                    if (DEBUG) Slog.d(TAG, "closing input methods panel");
                    if (mInputMethodsPanel != null) mInputMethodsPanel.closePanel(false);
                    break;
                case MSG_OPEN_COMPAT_MODE_PANEL:
                    if (DEBUG) Slog.d(TAG, "opening compat panel");
                    if (mCompatModePanel != null) mCompatModePanel.openPanel();
                    break;
                case MSG_CLOSE_COMPAT_MODE_PANEL:
                    if (DEBUG) Slog.d(TAG, "closing compat panel");
                    if (mCompatModePanel != null) mCompatModePanel.closePanel();
                    break;
                case MSG_SHOW_CHROME:
                    if (DEBUG) Slog.d(TAG, "hiding shadows (lights on)");
                    mBarContents.setVisibility(View.VISIBLE);
                    mShadow.setVisibility(View.GONE);
                    mSystemUiVisibility &= ~View.SYSTEM_UI_FLAG_LOW_PROFILE;
                    notifyUiVisibilityChanged();
                    break;
                case MSG_HIDE_CHROME:
                    if (DEBUG) Slog.d(TAG, "showing shadows (lights out)");
                    animateCollapsePanels();
                    visibilityChanged(false);
                    mBarContents.setVisibility(View.GONE);
                    mShadow.setVisibility(View.VISIBLE);
                    mSystemUiVisibility |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
                    notifyUiVisibilityChanged();
                    break;
                case MSG_STOP_TICKER:
                    mTicker.halt();
                    break;
            }
        }
    }

    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        if (DEBUG) Slog.d(TAG, "addIcon(" + slot + ") -> " + icon);
    }

    public void updateIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        if (DEBUG) Slog.d(TAG, "updateIcon(" + slot + ") -> " + icon);
    }

    public void removeIcon(String slot, int index, int viewIndex) {
        if (DEBUG) Slog.d(TAG, "removeIcon(" + slot + ")");
    }

    public void addNotification(IBinder key, StatusBarNotification notification) {
        if (DEBUG) Slog.d(TAG, "addNotification(" + key + " -> " + notification + ")");
        addNotificationViews(key, notification);

        final boolean immersive = isImmersive();
        if (false && immersive) {
            // TODO: immersive mode popups for tablet
        } else if (notification.notification.fullScreenIntent != null) {
            // not immersive & a full-screen alert should be shown
            Slog.w(TAG, "Notification has fullScreenIntent and activity is not immersive;"
                    + " sending fullScreenIntent");
            try {
                notification.notification.fullScreenIntent.send();
            } catch (PendingIntent.CanceledException e) {
            }
        } else if (!mRecreating) {
            tick(key, notification, true);
        }

        setAreThereNotifications();
    }

    public void removeNotification(IBinder key) {
        if (DEBUG) Slog.d(TAG, "removeNotification(" + key + ")");
        removeNotificationViews(key);
        mTicker.remove(key);
        setAreThereNotifications();
    }

    public void showClock(boolean show) {
        if (mClock != null) {
            mClock.setHidden(!show);
        }
        View networkText = mBarContents.findViewById(R.id.network_text);
        if (networkText != null) {
            networkText.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    public void disable(int state) {
        int old = mDisabled;
        int diff = state ^ old;
        mDisabled = state;

        // act accordingly
        if ((diff & StatusBarManager.DISABLE_CLOCK) != 0) {
            boolean show = (state & StatusBarManager.DISABLE_CLOCK) == 0;
            Slog.i(TAG, "DISABLE_CLOCK: " + (show ? "no" : "yes"));
            showClock(show);
        }
        if ((diff & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) {
            boolean show = (state & StatusBarManager.DISABLE_SYSTEM_INFO) == 0;
            Slog.i(TAG, "DISABLE_SYSTEM_INFO: " + (show ? "no" : "yes"));
            mNotificationTrigger.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if ((diff & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((state & StatusBarManager.DISABLE_EXPAND) != 0) {
                Slog.i(TAG, "DISABLE_EXPAND: yes");
                animateCollapsePanels();
                visibilityChanged(false);
            }
        }
        if ((diff & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
            mNotificationDNDMode = Prefs.read(mContext)
                        .getBoolean(Prefs.DO_NOT_DISTURB_PREF, Prefs.DO_NOT_DISTURB_DEFAULT);

            if ((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
                Slog.i(TAG, "DISABLE_NOTIFICATION_ICONS: yes" + (mNotificationDNDMode?" (DND)":""));
                mTicker.halt();
            } else {
                Slog.i(TAG, "DISABLE_NOTIFICATION_ICONS: no" + (mNotificationDNDMode?" (DND)":""));
            }

            // refresh icons to show either notifications or the DND message
            reloadAllNotificationIcons();
        } else if ((diff & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
            if ((state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
                mTicker.halt();
            }
        }
        if ((diff & (StatusBarManager.DISABLE_RECENT
                        | StatusBarManager.DISABLE_BACK
                        | StatusBarManager.DISABLE_HOME)) != 0) {
            setNavigationVisibility(state);

            if ((state & StatusBarManager.DISABLE_RECENT) != 0) {
                // close recents if it's visible
                mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
                mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
            }
        }
        if ((diff & (StatusBarManager.DISABLE_HOME
                | StatusBarManager.DISABLE_RECENT
                | StatusBarManager.DISABLE_BACK
                | StatusBarManager.DISABLE_SEARCH)) != 0) {

            // all navigation bar listeners will take care of these
            propagateDisabledFlags(state);
        }
    }

    private void setNavigationVisibility(int visibility) {
        boolean disableHome = ((visibility & StatusBarManager.DISABLE_HOME) != 0);
        boolean disableRecent = ((visibility & StatusBarManager.DISABLE_RECENT) != 0);
        boolean disableBack = ((visibility & StatusBarManager.DISABLE_BACK) != 0);

        mBackButton.setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
        mHomeButton.setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
        mRecentButton.setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);

        mInputMethodSwitchButton.setScreenLocked(
                (visibility & StatusBarManager.DISABLE_SYSTEM_INFO) != 0);
    }

    private boolean hasTicker(Notification n) {
        return n.tickerView != null || !TextUtils.isEmpty(n.tickerText);
    }

    @Override
    protected void tick(IBinder key, StatusBarNotification n, boolean firstTime) {
        // Don't show the ticker when the windowshade is open.
        if (mNotificationPanel.isShowing()) {
            return;
        }
        // If they asked for FLAG_ONLY_ALERT_ONCE, then only show this notification
        // if it's a new notification.
        if (!firstTime && (n.notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) {
            return;
        }
        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if (hasTicker(n.notification) && mStatusBarView.getWindowToken() != null) {
            if (0 == (mDisabled & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                            | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                mTicker.add(key, n);
                mFeedbackIconArea.setVisibility(View.GONE);
            }
        }
    }

    // called by TabletTicker when it's done with all queued ticks
    public void doneTicking() {
        mFeedbackIconArea.setVisibility(View.VISIBLE);
    }

    public void animateExpandNotificationsPanel() {
        mHandler.removeMessages(MSG_OPEN_NOTIFICATION_PANEL);
        mHandler.sendEmptyMessage(MSG_OPEN_NOTIFICATION_PANEL);
    }

    public void animateCollapsePanels() {
        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
    }

    public void animateCollapsePanels(int flags) {
        if ((flags & CommandQueue.FLAG_EXCLUDE_NOTIFICATION_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_NOTIFICATION_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_NOTIFICATION_PANEL);
        }
        if ((flags & CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
        }
        if ((flags & CommandQueue.FLAG_EXCLUDE_SEARCH_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_SEARCH_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_SEARCH_PANEL);
        }
        if ((flags & CommandQueue.FLAG_EXCLUDE_INPUT_METHODS_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_INPUT_METHODS_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_INPUT_METHODS_PANEL);
        }
        if ((flags & CommandQueue.FLAG_EXCLUDE_COMPAT_MODE_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_COMPAT_MODE_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_COMPAT_MODE_PANEL);
        }

    }

    @Override
    public void animateExpandSettingsPanel() {
        // TODO: Implement when TabletStatusBar begins to be used.
    }

    @Override // CommandQueue
    public void setNavigationIconHints(int hints) {
        if (hints == mNavigationIconHints) return;

        if (DEBUG) {
            android.widget.Toast.makeText(mContext,
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        mBackButton.setAlpha(
            (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP)) ? 0.5f : 1.0f);
        mHomeButton.setAlpha(
            (0 != (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP)) ? 0.5f : 1.0f);
        mRecentButton.setAlpha(
            (0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP)) ? 0.5f : 1.0f);

        mBackButton.setImageResource(
            (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT))
                ? R.drawable.ic_sysbar_back_ime
                : R.drawable.ic_sysbar_back);

        propagateNavigationIconHints(hints);
    }

    private void notifyUiVisibilityChanged() {
        try {
            mWindowManagerService.statusBarVisibilityChanged(mSystemUiVisibility);
        } catch (RemoteException ex) {
        }
    }

    @Override // CommandQueue
    public void setSystemUiVisibility(int vis, int mask) {
        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask) | (vis&mask);
        final int diff = newVal ^ oldVal;

        if (diff != 0) {
            mSystemUiVisibility = newVal;

            if (0 != (diff & View.SYSTEM_UI_FLAG_LOW_PROFILE)) {
                mHandler.removeMessages(MSG_HIDE_CHROME);
                mHandler.removeMessages(MSG_SHOW_CHROME);
                mHandler.sendEmptyMessage(0 == (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE)
                        ? MSG_SHOW_CHROME : MSG_HIDE_CHROME);
            }

            notifyUiVisibilityChanged();
        }
    }

    public void setLightsOn(boolean on) {
        // Policy note: if the frontmost activity needs the menu key, we assume it is a legacy app
        // that can't handle lights-out mode.
        if (mMenuButton.getVisibility() == View.VISIBLE) {
            on = true;
        }

        Slog.v(TAG, "setLightsOn(" + on + ")");
        if (on) {
            setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    public void topAppWindowChanged(boolean showMenu) {
        if (DEBUG) {
            Slog.d(TAG, (showMenu?"showing":"hiding") + " the MENU button");
        }
        mMenuButton.setVisibility(showMenu ? View.VISIBLE : View.GONE);
        propagateMenuVisibility(showMenu);

        // See above re: lights-out policy for legacy apps.
        if (showMenu) setLightsOn(true);

        mCompatModeButton.refresh();
        if (mCompatModeButton.getVisibility() == View.VISIBLE) {
            if (DEBUG_COMPAT_HELP
                    || ! Prefs.read(mContext).getBoolean(Prefs.SHOWN_COMPAT_MODE_HELP, false)) {
                showCompatibilityHelp();
            }
        } else {
            hideCompatibilityHelp();
            mCompatModePanel.closePanel();
        }
    }

    private void showCompatibilityHelp() {
        if (mCompatibilityHelpDialog != null) {
            return;
        }

        mCompatibilityHelpDialog = View.inflate(mContext, R.layout.compat_mode_help, null);
        View button = mCompatibilityHelpDialog.findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCompatibilityHelp();
                SharedPreferences.Editor editor = Prefs.edit(mContext);
                editor.putBoolean(Prefs.SHOWN_COMPAT_MODE_HELP, true);
                editor.apply();
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("CompatibilityModeDialog");
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        lp.windowAnimations = com.android.internal.R.style.Animation_ZoomButtons; // simple fade

        mWindowManager.addView(mCompatibilityHelpDialog, lp);
    }

    private void hideCompatibilityHelp() {
        if (mCompatibilityHelpDialog != null) {
            mWindowManager.removeView(mCompatibilityHelpDialog);
            mCompatibilityHelpDialog = null;
        }
    }

    public void setImeWindowStatus(IBinder token, int vis, int backDisposition) {
        mInputMethodSwitchButton.setImeWindowStatus(token,
                (vis & InputMethodService.IME_ACTIVE) != 0);
        updateNotificationIcons();
        mInputMethodsPanel.setImeToken(token);

        boolean altBack = (backDisposition == InputMethodService.BACK_DISPOSITION_WILL_DISMISS)
            || ((vis & InputMethodService.IME_VISIBLE) != 0);
        mAltBackButtonEnabledForIme = altBack;

        mCommandQueue.setNavigationIconHints(
                altBack ? (mNavigationIconHints | StatusBarManager.NAVIGATION_HINT_BACK_ALT)
                        : (mNavigationIconHints & ~StatusBarManager.NAVIGATION_HINT_BACK_ALT));

        if (FAKE_SPACE_BAR) {
            mFakeSpaceBar.setVisibility(((vis & InputMethodService.IME_VISIBLE) != 0)
                    ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void setHardKeyboardStatus(boolean available, boolean enabled) {
        if (DEBUG) {
            Slog.d(TAG, "Set hard keyboard status: available=" + available
                    + ", enabled=" + enabled);
        }
        mInputMethodSwitchButton.setHardKeyboardStatus(available);
        updateNotificationIcons();
        mInputMethodsPanel.setHardKeyboardStatus(available, enabled);
    }

    @Override
    public void onHardKeyboardEnabledChange(boolean enabled) {
        try {
            mBarService.setHardKeyboardEnabled(enabled);
        } catch (RemoteException ex) {
        }
    }

    private boolean isImmersive() {
        try {
            return ActivityManagerNative.getDefault().isTopActivityImmersive();
            //Slog.d(TAG, "Top activity is " + (immersive?"immersive":"not immersive"));
        } catch (RemoteException ex) {
            // the end is nigh
            return false;
        }
    }

    @Override
    protected void setAreThereNotifications() {
        if (mNotificationPanel != null) {
            mNotificationPanel.setClearable(isDeviceProvisioned() && mNotificationData.hasClearableItems());
        }
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (v == mRecentButton) {
                onClickRecentButton();
            } else if (v == mInputMethodSwitchButton) {
                onClickInputMethodSwitchButton();
            } else if (v == mCompatModeButton) {
                onClickCompatModeButton();
            }
        }
    };

    public void onClickRecentButton() {
        if (DEBUG) Slog.d(TAG, "clicked recent apps; disabled=" + mDisabled);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) == 0) {
            toggleRecentApps();
        }
    }

    public void onClickInputMethodSwitchButton() {
        if (DEBUG) Slog.d(TAG, "clicked input methods panel; disabled=" + mDisabled);
        int msg = (mInputMethodsPanel.getVisibility() == View.GONE) ?
                MSG_OPEN_INPUT_METHODS_PANEL : MSG_CLOSE_INPUT_METHODS_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    public void onClickCompatModeButton() {
        int msg = (mCompatModePanel.getVisibility() == View.GONE) ?
                MSG_OPEN_COMPAT_MODE_PANEL : MSG_CLOSE_COMPAT_MODE_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    private class NotificationTriggerTouchListener implements View.OnTouchListener {
        VelocityTracker mVT;
        float mInitialTouchX, mInitialTouchY;
        int mTouchSlop;

        public NotificationTriggerTouchListener() {
            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        }

        private Runnable mHiliteOnR = new Runnable() { public void run() {
            mNotificationArea.setBackgroundResource(
                com.android.internal.R.drawable.list_selector_pressed_holo_dark);
        }};
        public void hilite(final boolean on) {
            if (on) {
                mNotificationArea.postDelayed(mHiliteOnR, 100);
            } else {
                mNotificationArea.removeCallbacks(mHiliteOnR);
                mNotificationArea.setBackground(null);
            }
        }

        public boolean onTouch(View v, MotionEvent event) {
//            Slog.d(TAG, String.format("touch: (%.1f, %.1f) initial: (%.1f, %.1f)",
//                        event.getX(),
//                        event.getY(),
//                        mInitialTouchX,
//                        mInitialTouchY));

            if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
                return true;
            }

            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mVT = VelocityTracker.obtain();
                    mInitialTouchX = event.getX();
                    mInitialTouchY = event.getY();
                    hilite(true);
                    // fall through
                case MotionEvent.ACTION_OUTSIDE:
                case MotionEvent.ACTION_MOVE:
                    // check for fling
                    if (mVT != null) {
                        mVT.addMovement(event);
                        mVT.computeCurrentVelocity(1000); // pixels per second
                        // require a little more oomph once we're already in peekaboo mode
                        if (mVT.getYVelocity() < -mNotificationFlingVelocity) {
                            animateExpandNotificationsPanel();
                            visibilityChanged(true);
                            hilite(false);
                            mVT.recycle();
                            mVT = null;
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hilite(false);
                    if (mVT != null) {
                        if (action == MotionEvent.ACTION_UP
                         // was this a sloppy tap?
                         && Math.abs(event.getX() - mInitialTouchX) < mTouchSlop
                         && Math.abs(event.getY() - mInitialTouchY) < (mTouchSlop / 3)
                         // dragging off the bottom doesn't count
                         && (int)event.getY() < v.getBottom()) {
                            animateExpandNotificationsPanel();
                            visibilityChanged(true);
                            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                            v.playSoundEffect(SoundEffectConstants.CLICK);
                        }

                        mVT.recycle();
                        mVT = null;
                        return true;
                    }
            }
            return false;
        }
    }

    public void resetNotificationPeekFadeTimer() {
        if (DEBUG) {
            Slog.d(TAG, "setting peek fade timer for " + NOTIFICATION_PEEK_FADE_DELAY
                + "ms from now");
        }
        mHandler.removeMessages(MSG_CLOSE_NOTIFICATION_PEEK);
        mHandler.sendEmptyMessageDelayed(MSG_CLOSE_NOTIFICATION_PEEK,
                NOTIFICATION_PEEK_FADE_DELAY);
    }

    private void reloadAllNotificationIcons() {
        if (mIconLayout == null) return;
        mIconLayout.removeAllViews();
        updateNotificationIcons();
    }

    @Override
    protected void updateNotificationIcons() {
        // XXX: need to implement a new limited linear layout class
        // to avoid removing & readding everything

        if (mIconLayout == null) return;

        // first, populate the main notification panel
        loadNotificationPanel();

        final LinearLayout.LayoutParams params
            = new LinearLayout.LayoutParams(mIconSize + 2*mIconHPadding, mNaturalBarHeight);

        // alternate behavior in DND mode
        if (mNotificationDNDMode) {
            if (mIconLayout.getChildCount() == 0) {
                final Notification dndNotification = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getText(R.string.notifications_off_title))
                    .setContentText(mContext.getText(R.string.notifications_off_text))
                    .setSmallIcon(R.drawable.ic_notification_dnd)
                    .setOngoing(true)
                    .getNotification();

                final StatusBarIconView iconView = new StatusBarIconView(mContext, "_dnd",
                        dndNotification);
                iconView.setImageResource(R.drawable.ic_notification_dnd);
                iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iconView.setPadding(mIconHPadding, 0, mIconHPadding, 0);

                mNotificationDNDDummyEntry = new NotificationData.Entry(
                        null, new StatusBarNotification("", 0, "", 0, 0, Notification.PRIORITY_MAX,
                                dndNotification, android.os.Process.myUserHandle()), iconView);

                mIconLayout.addView(iconView, params);
            }

            return;
        } else if (0 != (mDisabled & StatusBarManager.DISABLE_NOTIFICATION_ICONS)) {
            // if icons are disabled but we're not in DND mode, this is probably Setup and we should
            // just leave the area totally empty
            return;
        }

        int N = mNotificationData.size();

        if (DEBUG) {
            Slog.d(TAG, "refreshing icons: " + N + " notifications, mIconLayout=" + mIconLayout);
        }

        ArrayList<View> toShow = new ArrayList<View>();

        // Extra Special Icons
        // The IME switcher and compatibility mode icons take the place of notifications. You didn't
        // need to see all those new emails, did you?
        int maxNotificationIconsCount = mMaxNotificationIcons;
        if (mInputMethodSwitchButton.getVisibility() != View.GONE) maxNotificationIconsCount --;
        if (mCompatModeButton.getVisibility()        != View.GONE) maxNotificationIconsCount --;

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; toShow.size()< maxNotificationIconsCount; i++) {
            if (i >= N) break;
            Entry ent = mNotificationData.get(N-i-1);
            if ((provisioned && ent.notification.score >= HIDE_ICONS_BELOW_SCORE)
                    || showNotificationEvenIfUnprovisioned(ent.notification)) {
                toShow.add(ent.icon);
            }
        }

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mIconLayout.getChildCount(); i++) {
            View child = mIconLayout.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mIconLayout.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            v.setPadding(mIconHPadding, 0, mIconHPadding, 0);
            if (v.getParent() == null) {
                mIconLayout.addView(v, i, params);
            }
        }
    }

    private void loadNotificationPanel() {
        int N = mNotificationData.size();

        ArrayList<View> toShow = new ArrayList<View>();

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if (provisioned || showNotificationEvenIfUnprovisioned(ent.notification)) {
                toShow.add(ent.row);
            }
        }

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mPile.getChildCount(); i++) {
            View child = mPile.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mPile.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                // the notification panel has the most important things at the bottom
                mPile.addView(v, Math.min(toShow.size()-1-i, mPile.getChildCount()));
            }
        }

        mNotificationPanel.setNotificationCount(toShow.size());
        mNotificationPanel.setSettingsEnabled(isDeviceProvisioned());
    }

    @Override
    protected void workAroundBadLayerDrawableOpacity(View v) {
        Drawable bgd = v.getBackground();
        if (!(bgd instanceof LayerDrawable)) return;

        LayerDrawable d = (LayerDrawable) bgd;
        v.setBackground(null);
        d.setOpacity(PixelFormat.TRANSLUCENT);
        v.setBackground(d);
    }

    public void clearAll() {
        try {
            mBarService.onClearAllNotifications();
        } catch (RemoteException ex) {
            // system process is dead if we're here.
        }
        animateCollapsePanels();
        visibilityChanged(false);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                || Intent.ACTION_SCREEN_OFF.equals(action)) {
                int flags = CommandQueue.FLAG_EXCLUDE_NONE;
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    String reason = intent.getStringExtra("reason");
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                        flags |= CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL;
                    }
                }
                animateCollapsePanels(flags);
            }
        }
    };

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mDisabled=0x");
        pw.println(Integer.toHexString(mDisabled));
        pw.println("mNetworkController:");
        mNetworkController.dump(fd, pw, args);
    }

    @Override
    protected boolean isTopNotification(ViewGroup parent, NotificationData.Entry entry) {
        if (parent == null || entry == null) return false;
        return parent.indexOfChild(entry.row) == parent.getChildCount()-1;
    }

    @Override
    protected void haltTicker() {
        mTicker.halt();
    }

    @Override
    protected void updateExpandedViewPos(int expandedPosition) {
    }

    @Override
    protected boolean shouldDisableNavbarGestures() {
        return mNotificationPanel.getVisibility() == View.VISIBLE
                || (mDisabled & StatusBarManager.DISABLE_HOME) != 0;
    }

    @Override
    public void userSwitched(int newUserId) {
        if (mSignalView != null) {
            mSignalView.updateSettings();
        }
        if (mClock != null) {
            mClock.updateSettings();
        }
        if (mBatteryController != null) {
            mBatteryController.updateSettings();
        }
        super.userSwitched(newUserId);
    }
}


