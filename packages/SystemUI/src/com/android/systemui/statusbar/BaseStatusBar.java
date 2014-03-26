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

package com.android.systemui.statusbar;

import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import com.android.systemui.chaos.lab.gestureanywhere.GestureAnywhereView;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.gesture.EdgeGestureManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
import com.android.systemui.statusbar.phone.Ticker;
import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.internal.util.slim.ButtonConfig;
import com.android.internal.util.slim.DeviceUtils;
import com.android.systemui.R;
import com.android.systemui.RecentsComponent;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.recent.TaskDescription;
import com.android.systemui.SearchPanelView;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.halo.Halo;
import com.android.systemui.slimrecent.RecentController;
import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;
import com.android.systemui.statusbar.phone.NavigationBarOverlay;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.activedisplay.ActiveDisplayView;
import com.android.systemui.statusbar.policy.PieController;

// pa pie
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.view.PieStatusPanel;
import com.android.systemui.statusbar.view.PieExpandPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class BaseStatusBar extends SystemUI implements
        CommandQueue.Callbacks {
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
    public static final boolean MULTIUSER_DEBUG = false;

    protected static final int MSG_TOGGLE_RECENTS_PANEL = 1020;
    protected static final int MSG_CLOSE_RECENTS_PANEL = 1021;
    protected static final int MSG_PRELOAD_RECENT_APPS = 1022;
    protected static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 1023;
    protected static final int MSG_OPEN_SEARCH_PANEL = 1024;
    protected static final int MSG_CLOSE_SEARCH_PANEL = 1025;
    protected static final int MSG_SHOW_HEADS_UP = 1026;
    protected static final int MSG_HIDE_HEADS_UP = 1027;
    protected static final int MSG_ESCALATE_HEADS_UP = 1028;
    protected static final int MSG_TOGGLE_SCREENSHOT = 1029;
    protected static final int MSG_TOGGLE_LAST_APP = 1030;
    protected static final int MSG_TOGGLE_KILL_APP = 1031;
    protected static final int MSG_SET_PIE_TRIGGER_MASK = 1032;
    protected static final int MSG_SWITCH_APPS = 1033;

    protected static final boolean ENABLE_HEADS_UP = true;
    // scores above this threshold should be displayed in heads up mode.
    protected static final int INTERRUPTION_THRESHOLD = 11;
    protected static final String SETTING_HEADS_UP = "heads_up_enabled";

    // Should match the value in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

    public static final int EXPANDED_LEAVE_ALONE = -10000;
    public static final int EXPANDED_FULL_OPEN = -10001;

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;
    protected H mHandler = createHandler();

    // all notifications
    protected NotificationData mNotificationData = new NotificationData();
    protected NotificationRowLayout mPile;

    protected NotificationData.Entry mInterruptingNotificationEntry;
    protected long mInterruptingNotificationTime;

    // used to notify status bar for suppressing notification LED
    protected boolean mPanelSlightlyVisible;

    // Search panel
    protected SearchPanelView mSearchPanelView;

    protected PopupMenu mNotificationBlamePopup;

    protected int mCurrentUserId = 0;

    protected FrameLayout mStatusBarContainer;

    protected int mLayoutDirection = -1; // invalid
    private Locale mLocale;
    protected boolean mUseHeadsUp = false;

    protected IDreamManager mDreamManager;
    PowerManager mPowerManager;
    protected int mRowHeight;

    // Pie controls
    public PieControlPanel mPieControlPanel;
    public View mPieControlsTrigger;
    public PieExpandPanel mContainer;
    public View[] mPieDummyTrigger = new View[4];
    int mIndex;

    // Policy
    public NetworkController mNetworkController;
    public BatteryController mBatteryController;

    // Halo
    protected Halo mHalo = null;
    protected Ticker mTicker;
    protected boolean mHaloEnabled;
    protected boolean mHaloActive;
    public boolean mHaloTaskerActive = false;
    protected ImageView mHaloButton;
    protected boolean mHaloButtonVisible = true;

    /**
     * An interface for navigation key bars to allow status bars to signal which keys are
     * currently of interest to the user.<br>
     * See {@link NavigationBarView} in Phone UI for an example.
     */
    public interface NavigationBarCallback {
        /**
         * @param hints flags from StatusBarManager (NAVIGATION_HINT...) to indicate which key is
         * available for navigation
         * @see StatusBarManager
         */
        public abstract void setNavigationIconHints(int hints);
        /**
         * @param showMenu {@code true} when an menu key should be displayed by the navigation bar.
         */
        public abstract void setMenuVisibility(boolean showMenu);
        /**
         * @param disabledFlags flags from View (STATUS_BAR_DISABLE_...) to indicate which key
         * is currently disabled on the navigation bar.
         * {@see View}
         */
        public void setDisabledFlags(int disabledFlags);
    };
    private ArrayList<NavigationBarCallback> mNavigationCallbacks =
            new ArrayList<NavigationBarCallback>();

    // Slim Pie Control
    private PieController mPieController;
    protected NavigationBarOverlay mNavigationBarOverlay;

    private EdgeGestureManager mEdgeGestureManager;

    // UI-specific methods

    /**
     * Create all windows necessary for the status bar (including navigation, overlay panels, etc)
     * and add them to the window manager.
     */
    protected abstract void createAndAddWindows();

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;
    protected abstract void refreshLayout(int layoutDirection);

    protected Display mDisplay;

    private boolean mDeviceProvisioned = false;

    // Recents Style
    private RecentController cRecents;
    private RecentsComponent mRecents;
    private boolean mCustomRecent = false;

    protected ActiveDisplayView mActiveDisplayView;

    protected AppSidebar mAppSidebar;
    protected int mSidebarPosition;

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
    protected GestureAnywhereView mGestureAnywhereView;

    public Ticker getTicker() {
        return mTicker;
    }

    public IStatusBarService getService() {
        return mBarService;
    }

    public NotificationData getNotificationData() {
        return mNotificationData;
    }

    private ActivityManager mActivityManager;

    public IStatusBarService getStatusBarService() {
        return mBarService;
    }

    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public void collapse() {
    }

    protected void onPieConfigurationChanged(Configuration newConfig) {
        if (mPieControlPanel != null) mPieControlPanel.bumpConfiguration();
    }

    public QuickSettingsContainerView getQuickSettingsPanel() {
        // This method should be overriden
        return null;
    }

    public NotificationRowLayout getNotificationRowLayout() {
        return mPile;
    }

    private Runnable mPanelCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
        }
    };

     private ContentObserver mProvisioningObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean provisioned = 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
            if (provisioned != mDeviceProvisioned) {
                mDeviceProvisioned = provisioned;
                updateNotificationIcons();
            }
        }
    };

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_TRIGGER), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_GRAVITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_STICK), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updatePieControls();
            if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0) == 0) {
                    PieStatusPanel.ResetPanels(true);
            } else if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_STICK, 1) == 0) {
                updatePieControls();
            }
        }
    };

    private RemoteViews.OnClickHandler mOnClickHandler = new RemoteViews.OnClickHandler() {
        @Override
        public boolean onClickHandler(View view, PendingIntent pendingIntent, Intent fillInIntent) {
            if (DEBUG) {
                Log.v(TAG, "Notification click handler invoked for intent: " + pendingIntent);
            }
            final boolean isActivity = pendingIntent.isActivity();
            if (isActivity) {
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

            boolean handled = super.onClickHandler(view, pendingIntent, fillInIntent);

            if (isActivity && handled) {
                // close the shade if it was open
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                visibilityChanged(false);
            }
            return handled;
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (true) Log.v(TAG, "userId " + mCurrentUserId + " is in the house");
                userSwitched(mCurrentUserId);
                if (mPieController != null) {
                    mPieController.refreshContainer();
                }
            }
        }
    };

    private class PieControlsTouchListener implements View.OnTouchListener {
        private int orient;
        private boolean actionDown = false;
        private boolean centerPie = true;
        private float initialX = 0;
        private float initialY = 0;
        int index;

        public PieControlsTouchListener() {
            orient = mPieControlPanel.getOrientation();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();

            if (!mPieControlPanel.isShowing()) {
                switch(action) {
                    case MotionEvent.ACTION_DOWN:
                        centerPie = Settings.System.getInt(mContext.getContentResolver(), Settings.System.PIE_CENTER, 1) == 1;
                        actionDown = true;
                        initialX = event.getX();
                        initialY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (actionDown != true) break;

                        float deltaX = Math.abs(event.getX() - initialX);
                        float deltaY = Math.abs(event.getY() - initialY);
                        float distance = orient == Gravity.BOTTOM ||
                                orient == Gravity.TOP ? deltaY : deltaX;
                        // Swipe up
                        if (distance > 10) {
                            orient = mPieControlPanel.getOrientation();
                            mPieControlPanel.show(centerPie ? -1 : (int)(orient == Gravity.BOTTOM ||
                                orient == Gravity.TOP ? initialX : initialY));
                            event.setAction(MotionEvent.ACTION_DOWN);
                            mPieControlPanel.onTouchEvent(event);
                            actionDown = false;
                        }
                }
            } else {
                return mPieControlPanel.onTouchEvent(event);
            }
            return false;
        }
    }

    public void start() {
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDisplay = mWindowManager.getDefaultDisplay();

        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        mProvisioningObserver.onChange(false); // set up
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), true,
                mProvisioningObserver);

        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        mCustomRecent = Settings.System.getBoolean(
                        mContext.getContentResolver(), Settings.System.RECENTS_STYLE, false);

        if (mCustomRecent) {
            cRecents = new RecentController(mContext, mLayoutDirection);
        } else {
            mRecents = getComponent(RecentsComponent.class);
        }

        mLocale = mContext.getResources().getConfiguration().locale;
        mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(mLocale);

        mStatusBarContainer = new FrameLayout(mContext);

        // Connect in to the status bar manager service
        StatusBarIconList iconList = new StatusBarIconList();
        ArrayList<IBinder> notificationKeys = new ArrayList<IBinder>();
        ArrayList<StatusBarNotification> notifications = new ArrayList<StatusBarNotification>();
        mCommandQueue = new CommandQueue(this, iconList);

        int[] switches = new int[7];
        ArrayList<IBinder> binders = new ArrayList<IBinder>();
        try {
            mBarService.registerStatusBar(mCommandQueue, iconList, notificationKeys, notifications,
                    switches, binders);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

        mHaloEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ENABLED, 0) == 1;

        mHaloActive = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ACTIVE, 0) == 1;

        createAndAddWindows();

        disable(switches[0]);
        setSystemUiVisibility(switches[1], 0xffffffff);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(binders.get(0), switches[3], switches[4]);
        setHardKeyboardStatus(switches[5] != 0, switches[6] != 0);

        // Set up the initial icon state
        int N = iconList.size();
        int viewIndex = 0;
        for (int i=0; i<N; i++) {
            StatusBarIcon icon = iconList.getIcon(i);
            if (icon != null) {
                addIcon(iconList.getSlot(i), i, viewIndex, icon);
                viewIndex++;
            }
        }

        // Set up the initial notification state
        N = notificationKeys.size();
        if (N == notifications.size()) {
            for (int i=0; i<N; i++) {
                addNotification(notificationKeys.get(i), notifications.get(i));
            }
        } else {
            Log.wtf(TAG, "Notification list length mismatch: keys=" + N
                    + " notifications=" + notifications.size());
        }

        if (DEBUG) {
            Log.d(TAG, String.format(
                    "init: icons=%d disabled=0x%08x lights=0x%08x menu=0x%08x imeButton=0x%08x",
                   iconList.size(),
                   switches[0],
                   switches[1],
                   switches[2],
                   switches[3]
                   ));
        }

        initPieController();

        mCurrentUserId = ActivityManager.getCurrentUser();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
        SidebarObserver observer = new SidebarObserver(mHandler);
        observer.observe();

        // Listen for HALO enabled switch
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_ENABLED), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateHalo();
            }});

        // Listen for HALO state
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_ACTIVE), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateHalo();
            }});

        IntentFilter switchFilter = new IntentFilter();
        switchFilter.addAction("com.android.systemui.APP_SWITCH");
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int msg = MSG_SWITCH_APPS;
                mHandler.removeMessages(msg);
                mHandler.sendEmptyMessage(msg);
            }
        }, switchFilter);

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_SIZE), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                restartHalo();
            }});

        updateHalo();

        // Listen for PIE gravity
        mContext.getContentResolver().registerContentObserver(
            Settings.System.getUriFor(Settings.System.PIE_GRAVITY), false, new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    if (Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.PIE_STICK, 1) == 0) {
                        updatePieControls();
                    }
                }
            });
            attachPie();

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
    }

    public void setHaloTaskerActive(boolean haloTaskerActive, boolean updateNotificationIcons) {
        mHaloTaskerActive = haloTaskerActive;
        if (updateNotificationIcons) {
            updateNotificationIcons();
        }
    }

    protected void updateHaloButton() {
        if (!mHaloEnabled) {
            mHaloButtonVisible = false;
        } else {
            mHaloButtonVisible = true;
        }
        if (mHaloButton != null) {
            mHaloButton.setVisibility(mHaloButtonVisible && !mHaloActive ? View.VISIBLE : View.GONE);
        }
    }

    public void restartHalo() {
        if (mHalo != null) {
            mHalo.cleanUp();
            mWindowManager.removeView(mHalo);
            mHalo = null;
        }
        updateNotificationIcons();
        updateHalo();
    }

    protected void updateHalo() {
        mHaloEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ENABLED, 0) == 1;
        mHaloActive = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ACTIVE, 0) == 1;

        updateHaloButton();

        if (!mHaloEnabled) {
            mHaloActive = false;
        }

        if (mHaloActive) {
            if (mHalo == null) {
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                mHalo = (Halo)inflater.inflate(R.layout.halo_trigger, null);
                mHalo.setLayerType (View.LAYER_TYPE_HARDWARE, null);
                WindowManager.LayoutParams params = mHalo.getWMParams();
                mWindowManager.addView(mHalo,params);
                mHalo.setStatusBar(this);
            }
        } else {
            if (mHalo != null) {
                mHalo.cleanUp();
                mWindowManager.removeView(mHalo);
                mHalo = null;
            }
        }

        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    private boolean showPie() {
        boolean pie = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0) == 1;

        return (pie);
    }

    public void updatePieControls() {
        if (mPieControlsTrigger != null) mWindowManager.removeView(mPieControlsTrigger);
        if (mPieControlPanel != null) mWindowManager.removeView(mPieControlPanel);

        for (int i = 0; i < 4; i++) {
            if (mPieDummyTrigger[i] != null) mWindowManager.removeView(mPieDummyTrigger[i]);
        }

        attachPie();
    }

    private void attachPie() {
        if(showPie()) {
            if (mContainer == null) {
                // Add panel window, one to be used by all pies that is
                LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                mContainer = (PieExpandPanel)inflater.inflate(R.layout.pie_expanded_panel, null);
                mContainer.init(mPile, mContainer.findViewById(R.id.content_scroll));
                mWindowManager.addView(mContainer, PieStatusPanel.getFlipPanelLayoutParams());
            }

            // Add pie (s), want some slice?
            int gravity = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY, 2);

            switch(gravity) {
                case 0:
                    addPieInLocation(Gravity.LEFT);
                    break;
                case 1:
                    addPieInLocation(Gravity.TOP);
                    break;
                case 2:
                    addPieInLocation(Gravity.RIGHT);
                    break;
                default:
                    addPieInLocation(Gravity.BOTTOM);
                    break;
            }


        } else {
            mPieControlsTrigger = null;
            mPieControlPanel = null;
            for (int i = 0; i < 4; i++) {
                mPieDummyTrigger[i] = null;
            }
        }
    }

    private void addPieInLocation(int gravity) {
        // Quick navigation bar panel
        mPieControlPanel = (PieControlPanel) View.inflate(mContext,
                R.layout.pie_control_panel, null);

        // Quick navigation bar trigger area
        mPieControlsTrigger = new View(mContext);
        mPieControlsTrigger.setOnTouchListener(new PieControlsTouchListener());
        mWindowManager.addView(mPieControlsTrigger, getPieTriggerLayoutParams(mContext, gravity));

        // Overload screen with views that literally do nothing, thank you Google
        int dummyGravity[] = {Gravity.LEFT, Gravity.TOP, Gravity.RIGHT, Gravity.BOTTOM};
        for (int i = 0; i < 4; i++) {
            mPieDummyTrigger[i] = new View(mContext);
            mWindowManager.addView(mPieDummyTrigger[i], getDummyTriggerLayoutParams(mContext, dummyGravity[i]));
        }

        // Init Panel
        mPieControlPanel.init(mHandler, this, mPieControlsTrigger, gravity);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("PieControlPanel");
        lp.windowAnimations = android.R.style.Animation;

        mWindowManager.addView(mPieControlPanel, lp);
    }

    public static WindowManager.LayoutParams getPieTriggerLayoutParams(Context context, int gravity) {
        final Resources res = context.getResources();
        final float mPieSize = Settings.System.getFloat(context.getContentResolver(),
                Settings.System.PIE_TRIGGER, 1f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
              (gravity == Gravity.TOP || gravity == Gravity.BOTTOM ?
                    ViewGroup.LayoutParams.MATCH_PARENT : (int)(res.getDimensionPixelSize(R.dimen.pie_trigger_height)*mPieSize)),
              (gravity == Gravity.LEFT || gravity == Gravity.RIGHT ?
                    ViewGroup.LayoutParams.MATCH_PARENT : (int)(res.getDimensionPixelSize(R.dimen.pie_trigger_height)*mPieSize)),
              WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        lp.gravity = gravity;
        return lp;
    }

    public static WindowManager.LayoutParams getDummyTriggerLayoutParams(Context context, int gravity) {
        final Resources res = context.getResources();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
              (gravity == Gravity.TOP || gravity == Gravity.BOTTOM ?
                    ViewGroup.LayoutParams.MATCH_PARENT : 1),
              (gravity == Gravity.LEFT || gravity == Gravity.RIGHT ?
                    ViewGroup.LayoutParams.MATCH_PARENT : 1),
              WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        lp.gravity = gravity;
        return lp;
    }

    private void initPieController() {
        if (mEdgeGestureManager == null) {
            mEdgeGestureManager = EdgeGestureManager.getInstance();
        }
        if (mNavigationBarOverlay == null) {
            mNavigationBarOverlay = new NavigationBarOverlay();
        }
        if (mPieController == null) {
            mPieController = new PieController(
                    mContext, this, mEdgeGestureManager, mNavigationBarOverlay);
            addNavigationBarCallback(mPieController);
        }
    }

    protected void attachPieContainer(boolean enabled) {
        initPieController();
        if (enabled) {
            mPieController.attachContainer();
        } else {
            mPieController.detachContainer(false);
        }
    }

    public void setOverwriteImeIsActive(boolean enabled) {
        if (mEdgeGestureManager != null) {
            mEdgeGestureManager.setOverwriteImeIsActive(enabled);
        }
    }

    public void userSwitched(int newUserId) {
        // should be overridden
    }

    public boolean notificationIsForCurrentUser(StatusBarNotification n) {
        final int thisUserId = mCurrentUserId;
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Log.v(TAG, String.format("%s: current userid: %d, notification userid: %d",
                    n, thisUserId, notificationUserId));
        }
        return notificationUserId == UserHandle.USER_ALL
                || thisUserId == notificationUserId;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final Locale locale = mContext.getResources().getConfiguration().locale;
        final int ld = TextUtils.getLayoutDirectionFromLocale(locale);
        if (! locale.equals(mLocale) || ld != mLayoutDirection) {
            if (DEBUG) {
                Log.v(TAG, String.format(
                        "config changed locale/LD: %s (%d) -> %s (%d)", mLocale, mLayoutDirection,
                        locale, ld));
            }
            mLocale = locale;
            mLayoutDirection = ld;
            refreshLayout(ld);
        }
    }

    protected View updateNotificationVetoButton(View row, StatusBarNotification n) {
        View vetoButton = row.findViewById(R.id.veto);
        if (n.isClearable() || (mInterruptingNotificationEntry != null
                && mInterruptingNotificationEntry.row == row)) {
            final String _pkg = n.getPackageName();
            final String _tag = n.getTag();
            final int _id = n.getId();
            vetoButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Accessibility feedback
                        v.announceForAccessibility(
                                mContext.getString(R.string.accessibility_notification_dismissed));
                        try {
                            mBarService.onNotificationClear(_pkg, _tag, _id);

                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                });
            vetoButton.setVisibility(View.VISIBLE);
        } else {
            vetoButton.setVisibility(View.GONE);
        }
        vetoButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return vetoButton;
    }


    protected void applyLegacyRowBackground(StatusBarNotification sbn, View content) {
        if (sbn.getNotification().contentView.getLayoutId() !=
                com.android.internal.R.layout.notification_template_base) {
            int version = 0;
            try {
                ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(sbn.getPackageName(), 0);
                version = info.targetSdkVersion;
            } catch (NameNotFoundException ex) {
                Log.e(TAG, "Failed looking up ApplicationInfo for " + sbn.getPackageName(), ex);
            }
            if (version > 0 && version < Build.VERSION_CODES.GINGERBREAD) {
                content.setBackgroundResource(R.drawable.notification_row_legacy_bg);
            } else {
                content.setBackgroundResource(com.android.internal.R.drawable.notification_bg);
            }
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext).addNextIntentWithParentStack(intent).startActivities(
                null, UserHandle.CURRENT);
    }

    private void launchFloating(PendingIntent pIntent) {
        Intent overlay = new Intent();
        overlay.addFlags(Intent.FLAG_FLOATING_WINDOW | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        try {
            pIntent.send(mContext, 0, overlay);
        } catch (PendingIntent.CanceledException e) {
            // the stack trace isn't very helpful here.  Just log the exception message.
            Slog.w(TAG, "Sending contentIntent failed: " + e);
        }
    }

    protected View.OnLongClickListener getNotificationLongClicker() {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                NotificationData.Entry  entry = (NotificationData.Entry) v.getTag();
                StatusBarNotification sbn = entry.notification;

                final String packageNameF = sbn.getPackageName();
                final PendingIntent contentIntent = sbn.getNotification().contentIntent;

                if (packageNameF == null) return false;
                if (v.getWindowToken() == null) return false;

                mNotificationBlamePopup = new PopupMenu(mContext, v);
                mNotificationBlamePopup.getMenuInflater().inflate(
                        R.menu.notification_popup_menu,
                        mNotificationBlamePopup.getMenu());
                final ContentResolver cr = mContext.getContentResolver();
                if (Settings.Secure.getInt(cr,
                        Settings.Secure.DEVELOPMENT_SHORTCUT, 0) == 0) {
                    mNotificationBlamePopup.getMenu()
                            .findItem(R.id.notification_inspect_item_force_stop).setVisible(false);
                    mNotificationBlamePopup.getMenu()
                            .findItem(R.id.notification_inspect_item_wipe_app).setVisible(false);
                } else {
                    try {
                        PackageManager pm = (PackageManager) mContext.getPackageManager();
                        ApplicationInfo mAppInfo = pm.getApplicationInfo(packageNameF, 0);
                        DevicePolicyManager mDpm = (DevicePolicyManager) mContext.
                                getSystemService(Context.DEVICE_POLICY_SERVICE);
                        if ((mAppInfo.flags&(ApplicationInfo.FLAG_SYSTEM
                              | ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA))
                              == ApplicationInfo.FLAG_SYSTEM
                              || mDpm.packageHasActiveAdmins(packageNameF)) {
                            mNotificationBlamePopup.getMenu()
                            .findItem(R.id.notification_inspect_item_wipe_app).setEnabled(false);
                        }
                    } catch (NameNotFoundException ex) {
                        Slog.e(TAG, "Failed looking up ApplicationInfo for " + packageNameF, ex);
                    }
                }

                MenuItem hideIconCheck = mNotificationBlamePopup.getMenu().findItem(R.id.notification_hide_icon_packages);
                if(hideIconCheck != null) {
                    hideIconCheck.setChecked(isIconHiddenByUser(packageNameF));
                    if (packageNameF.equals("android")) {
                        // cannot set it, no one likes a liar
                        hideIconCheck.setVisible(false);
                    }
                }

                mNotificationBlamePopup
                .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.notification_inspect_item) {
                            startApplicationDetailsActivity(packageNameF);
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                        } else if (item.getItemId() == R.id.notification_hide_icon_packages) {
                            item.setChecked(!item.isChecked());
                            setIconHiddenByUser(packageNameF, item.isChecked());
                            updateNotificationIcons();
                        } else if (item.getItemId() == R.id.notification_inspect_item_force_stop) {
                            ActivityManager am = (ActivityManager) mContext
                                    .getSystemService(
                                    Context.ACTIVITY_SERVICE);
                            am.forceStopPackage(packageNameF);
                        } else if (item.getItemId() == R.id.notification_inspect_item_wipe_app) {
                            ActivityManager am = (ActivityManager) mContext
                                    .getSystemService(Context.ACTIVITY_SERVICE);
                            am.clearApplicationUserData(packageNameF,
                                    new FakeClearUserDataObserver());
                        } else if (item.getItemId() == R.id.notification_floating_item) {
                            launchFloating(contentIntent);
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                        } else {
                            return false;
                        }
                        return true;
                    }
                });

                mNotificationBlamePopup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                    @Override
                    public void onDismiss(PopupMenu popupMenu) {
                        mNotificationBlamePopup = null;
                    }
                });

                mNotificationBlamePopup.show();

                return true;
            }
        };
    }

    class FakeClearUserDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
        }
    }

    public void dismissPopups() {
        if (mNotificationBlamePopup != null) {
            mNotificationBlamePopup.dismiss();
            mNotificationBlamePopup = null;
        }
    }

    public void onHeadsUpDismissed() {
    }

    @Override
    public void toggleRecentApps() {
        int msg = MSG_TOGGLE_RECENTS_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

     @Override

    public void animateCollapsePanels(int flags) {
        if (mPieControlPanel != null
                && flags == CommandQueue.FLAG_EXCLUDE_NONE) {
            mPieControlPanel.animateCollapsePanels();
        }
    }

    @Override
    public void preloadRecentApps() {
        int msg = MSG_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void cancelPreloadRecentApps() {
        int msg = MSG_CANCEL_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void showSearchPanel() {
        int msg = MSG_OPEN_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void hideSearchPanel() {
        int msg = MSG_CLOSE_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleScreenshot() {
        int msg = MSG_TOGGLE_SCREENSHOT;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleLastApp() {
        int msg = MSG_TOGGLE_LAST_APP;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleKillApp() {
        int msg = MSG_TOGGLE_KILL_APP;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void setButtonDrawable(int buttonId, int iconId) {}

    public void setPieTriggerMask(int newMask, boolean lock) {
        int msg = MSG_SET_PIE_TRIGGER_MASK;
        mHandler.removeMessages(msg);
        mHandler.obtainMessage(MSG_SET_PIE_TRIGGER_MASK,
                newMask, lock ? 1 : 0, null).sendToTarget();
    }

    protected abstract WindowManager.LayoutParams getSearchLayoutParams(
            LayoutParams layoutParams);

    protected void updateSearchPanel(boolean navigationBarCanMove,
            ArrayList<ButtonConfig> buttonConfig) {
        // Search Panel
        boolean visible = false;
        if (mSearchPanelView != null) {
            visible = mSearchPanelView.isShowing();
            mWindowManager.removeView(mSearchPanelView);
        }

        // Provide SearchPanel with a temporary parent to allow layout params to work.
        LinearLayout tmpRoot = new LinearLayout(mContext);

        if (!isScreenPortrait() && !navigationBarCanMove) {
            mSearchPanelView = (SearchPanelView) LayoutInflater.from(mContext).inflate(
                    R.layout.status_bar_search_panel_real_landscape, tmpRoot, false);
        } else {
            mSearchPanelView = (SearchPanelView) LayoutInflater.from(mContext).inflate(
                    R.layout.status_bar_search_panel, tmpRoot, false);
        }

        mSearchPanelView.setNavigationBarCanMove(navigationBarCanMove);
        mSearchPanelView.setNavigationRingConfig(buttonConfig);
        mSearchPanelView.setDrawables();

        mSearchPanelView.setOnTouchListener(
                 new TouchOutsideListener(MSG_CLOSE_SEARCH_PANEL, mSearchPanelView));
        mSearchPanelView.setVisibility(View.GONE);

        WindowManager.LayoutParams lp = getSearchLayoutParams(mSearchPanelView.getLayoutParams());

        mWindowManager.addView(mSearchPanelView, lp);
        mSearchPanelView.setBar(this);
        if (visible) {
            mSearchPanelView.show(true, false);
        }
    }

    protected H createHandler() {
         return new H();
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    protected abstract View getStatusBarView();

    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        // additional optimization when we have software system buttons - start loading the recent
        // tasks on touch down
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                preloadRecentTasksList();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelPreloadingRecentTasksList();
            } else if (action == MotionEvent.ACTION_UP) {
                if (!v.isPressed()) {
                    cancelPreloadingRecentTasksList();
                }

            }
            return false;
        }
    };

    protected void toggleRecentsActivity() {
        if (mRecents != null || cRecents != null) {
        mCustomRecent = Settings.System.getBoolean(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, false);
        if(mCustomRecent)
            cRecents.toggleRecents(mDisplay, mLayoutDirection, getStatusBarView());
        else
            mRecents.toggleRecents(mDisplay, mLayoutDirection, getStatusBarView());
        }
    }

    protected void preloadRecentTasksList() {
        if (mRecents != null || cRecents != null) {
        mCustomRecent = Settings.System.getBoolean(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, false);
        if (mCustomRecent)
            cRecents.preloadRecentTasksList();
        else
            mRecents.preloadRecentTasksList();
        }
    }

    protected void cancelPreloadingRecentTasksList() {
        if (mRecents != null || cRecents != null) {
        mCustomRecent = Settings.System.getBoolean(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, false);
        if (mCustomRecent)
            cRecents.cancelPreloadingRecentTasksList();
        else
            mRecents.cancelPreloadingRecentTasksList();
        }
    }

    protected void closeRecents() {
        if (mRecents != null || cRecents != null) {
        mCustomRecent = Settings.System.getBoolean(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, false);
        if (mCustomRecent)
            cRecents.closeRecents();
        else
            mRecents.closeRecents();
        }
    }

    protected void rebuildRecentsScreen() {
        mCustomRecent = Settings.System.getBoolean(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, false);
        if (cRecents != null && mCustomRecent)   
                cRecents.rebuildRecentsScreen();
    }

    public abstract void resetHeadsUpDecayTimer();

    protected class H extends Handler {
        public void handleMessage(Message m) {
            Intent intent;
            switch (m.what) {
             case MSG_TOGGLE_RECENTS_PANEL:
                 toggleRecentsActivity();
                 break;
             case MSG_CLOSE_RECENTS_PANEL:
                 closeRecents();
                 break;
             case MSG_PRELOAD_RECENT_APPS:
                  preloadRecentTasksList();
                  break;
             case MSG_CANCEL_PRELOAD_RECENT_APPS:
                  cancelPreloadingRecentTasksList();
                  break;
             case MSG_OPEN_SEARCH_PANEL:
                 if (DEBUG) Log.d(TAG, "opening search panel");
                 if (mSearchPanelView != null) {
                     mSearchPanelView.show(true, true);
                     onShowSearchPanel();
                 }
                 break;
             case MSG_CLOSE_SEARCH_PANEL:
                 if (DEBUG) Log.d(TAG, "closing search panel");
                 if (mSearchPanelView != null && mSearchPanelView.isShowing()) {
                     mSearchPanelView.show(false, true);
                     onHideSearchPanel();
                 }
                 break;
             case MSG_TOGGLE_SCREENSHOT:
                 if (DEBUG) Slog.d(TAG, "toggle screenshot");
                 takeScreenshot();
                 break;
             case MSG_TOGGLE_LAST_APP:
                 if (DEBUG) Slog.d(TAG, "toggle last app");
                 getLastApp();
                 break;
             case MSG_TOGGLE_KILL_APP:
                 if (DEBUG) Slog.d(TAG, "toggle kill app");
                 mHandler.post(mKillTask);
                 break;
            case MSG_SET_PIE_TRIGGER_MASK:
                updatePieTriggerMask(m.arg1, m.arg2 != 0);
                break;
            case MSG_SWITCH_APPS:
                final List<ActivityManager.RecentTaskInfo> recentTasks = mActivityManager.getRecentTasksForUser(
                        99, ActivityManager.RECENT_IGNORE_UNAVAILABLE, UserHandle.CURRENT.getIdentifier());
                if (recentTasks.size() > 1) {
                    ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(recentTasks.size() - 1);
                    Intent switchIntent = new Intent(recentInfo.baseIntent);
                    if (recentInfo.origActivity != null) {
                        switchIntent.setComponent(recentInfo.origActivity);
                    }
                    // Don't load ourselves
                    if (switchIntent.getComponent().getPackageName().equals(mContext.getPackageName())) {
                        return;
                    }
                    ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                            com.android.internal.R.anim.slide_in_right,
                            com.android.internal.R.anim.slide_out_left);
                    mContext.startActivityAsUser(switchIntent, opts.toBundle(), new UserHandle(
                            UserHandle.USER_CURRENT));
                }
            }
        }
    }

    public class TouchOutsideListener implements View.OnTouchListener {
        private int mMsg;
        private StatusBarPanel mPanel;

        public TouchOutsideListener(int msg, StatusBarPanel panel) {
            mMsg = msg;
            mPanel = panel;
        }

        public boolean onTouch(View v, MotionEvent ev) {
            final int action = ev.getAction();
            if (action == MotionEvent.ACTION_OUTSIDE
                || (action == MotionEvent.ACTION_DOWN
                    && !mPanel.isInContentArea((int)ev.getX(), (int)ev.getY()))) {
                mHandler.removeMessages(mMsg);
                mHandler.sendEmptyMessage(mMsg);
                return true;
            }
            return false;
        }
    }

    protected void workAroundBadLayerDrawableOpacity(View v) {
    }

    protected void onHideSearchPanel() {
    }

    protected void onShowSearchPanel() {
    }

    public boolean inflateViews(NotificationData.Entry entry, ViewGroup parent) {
        int minHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);
        int maxHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_max_height);
        StatusBarNotification sbn = entry.notification;
        RemoteViews contentView = sbn.getNotification().contentView;
        RemoteViews bigContentView = sbn.getNotification().bigContentView;
        if (contentView == null) {
            return false;
        }

        // create the row view
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ExpandableNotificationRow row = (ExpandableNotificationRow) inflater.inflate(
                R.layout.status_bar_notification_row, parent, false);

        // for blaming (see SwipeHelper.setLongPressListener)
        row.setTag(entry);

        workAroundBadLayerDrawableOpacity(row);
        View vetoButton = updateNotificationVetoButton(row, sbn);
        vetoButton.setContentDescription(mContext.getString(
                R.string.accessibility_remove_notification));

        // NB: the large icon is now handled entirely by the template

        // bind the click event to the content area
        ViewGroup content = (ViewGroup)row.findViewById(R.id.content);
        ViewGroup adaptive = (ViewGroup)row.findViewById(R.id.adaptive);

        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        PendingIntent contentIntent = sbn.getNotification().contentIntent;
        if (contentIntent != null) {
            final View.OnClickListener listener = new NotificationClicker(contentIntent,
                    sbn.getPackageName(), sbn.getTag(), sbn.getId());
            content.setOnClickListener(listener);
        } else {
            content.setOnClickListener(null);
        }

        View contentViewLocal = null;
        View bigContentViewLocal = null;
        try {
            contentViewLocal = contentView.apply(mContext, adaptive, mOnClickHandler);
            if (bigContentView != null) {
                bigContentViewLocal = bigContentView.apply(mContext, adaptive, mOnClickHandler);
            }
        }
        catch (RuntimeException e) {
            final String ident = sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId());
            Log.e(TAG, "couldn't inflate view for notification " + ident, e);
            return false;
        }

        if (contentViewLocal != null) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(contentViewLocal.getLayoutParams());
            params.minHeight = minHeight;
            params.maxHeight = minHeight;
            adaptive.addView(contentViewLocal, params);
        }
        if (bigContentViewLocal != null) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(bigContentViewLocal.getLayoutParams());
            params.minHeight = minHeight+1;
            params.maxHeight = maxHeight;
            adaptive.addView(bigContentViewLocal, params);
        }
        row.setDrawingCacheEnabled(true);

        applyLegacyRowBackground(sbn, content);

        if (MULTIUSER_DEBUG) {
            TextView debug = (TextView) row.findViewById(R.id.debug_info);
            if (debug != null) {
                debug.setVisibility(View.VISIBLE);
                debug.setText("U " + entry.notification.getUserId());
            }
        }
        entry.row = row;
        entry.row.setRowHeight(mRowHeight);
        entry.content = content;
        entry.expanded = contentViewLocal;
        entry.setBigContentView(bigContentViewLocal);

        return true;
    }

    public NotificationClicker makeClicker(PendingIntent intent, String pkg, String tag, int id) {
        return new NotificationClicker(intent, pkg, tag, id);
    }

    public class NotificationClicker implements View.OnClickListener {
        public PendingIntent mIntent;
        public String mPkg;
        public String mTag;
        public int mId;
        public boolean mFloat;

        public NotificationClicker(PendingIntent intent, String pkg, String tag, int id) {
            mIntent = intent;
            mPkg = pkg;
            mTag = tag;
            mId = id;
        }

        public void makeFloating(boolean floating) {
            mFloat = floating;
        }

        public void onClick(View v) {
            if (mNotificationBlamePopup != null) return;
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

            if (mIntent != null) {

                 if (mFloat && !"android".equals(mPkg)) {
                    Intent transparent = new Intent(mContext, com.android.systemui.Transparent.class);
                    transparent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_FLOATING_WINDOW);
                    mContext.startActivity(transparent);
                }

                int[] pos = new int[2];
                v.getLocationOnScreen(pos);
                Intent overlay = new Intent();
                if (mFloat) overlay.addFlags(Intent.FLAG_FLOATING_WINDOW | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                overlay.setSourceBounds(
                        new Rect(pos[0], pos[1], pos[0]+v.getWidth(), pos[1]+v.getHeight()));
                try {
                    mIntent.send(mContext, 0, overlay);
                } catch (PendingIntent.CanceledException e) {
                    // the stack trace isn't very helpful here.  Just log the exception message.
                    Log.w(TAG, "Sending contentIntent failed: " + e);
                }
                KeyguardTouchDelegate.getInstance(mContext).dismiss();
            }

            try {
                mBarService.onNotificationClick(mPkg, mTag, mId);
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }

            // close the shade if it was open
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
            visibilityChanged(false);
        }
    }
    /**
     * The LEDs are turned o)ff when the notification panel is shown, even just a little bit.
     * This was added last-minute and is inconsistent with the way the rest of the notifications
     * are handled, because the notification isn't really cancelled.  The lights are just
     * turned off.  If any other notifications happen, the lights will turn back on.  Steve says
     * this is what he wants. (see bug 1131461)
     */
    protected void visibilityChanged(boolean visible) {
        if (mPanelSlightlyVisible != visible) {
            mPanelSlightlyVisible = visible;
            try {
                mBarService.onPanelRevealed();
            } catch (RemoteException ex) {
                // Won't fail unless the world has ended.
            }
        }
    }

    /**
     * Cancel this notification and tell the StatusBarManagerService / NotificationManagerService
     * about the failure.
     *
     * WARNING: this will call back into us.  Don't hold any locks.
     */
    void handleNotificationError(IBinder key, StatusBarNotification n, String message) {
        removeNotification(key);
        try {
            mBarService.onNotificationError(n.getPackageName(), n.getTag(), n.getId(), n.getUid(), n.getInitialPid(), message);
        } catch (RemoteException ex) {
            // The end is nigh.
        }
    }

    protected StatusBarNotification removeNotificationViews(IBinder key) {
        NotificationData.Entry entry = mNotificationData.remove(key);
        if (entry == null) {
            Log.w(TAG, "removeNotification for unknown key: " + key);
            return null;
        }
        // Remove the expanded view.
        ViewGroup rowParent = (ViewGroup)entry.row.getParent();
        if (rowParent != null) rowParent.removeView(entry.row);
        updateExpansionStates();
        updateNotificationIcons();

        return entry.notification;
    }

    protected NotificationData.Entry createNotificationViews(IBinder key,
            StatusBarNotification notification) {
        if (DEBUG) {
            Log.d(TAG, "createNotificationViews(key=" + key + ", notification=" + notification);
        }
        // Construct the icon.
        final StatusBarIconView iconView = new StatusBarIconView(mContext,
                notification.getPackageName() + "/0x" + Integer.toHexString(notification.getId()),
                notification.getNotification());
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                notification.getUser(),
                    notification.getNotification().icon,
                    notification.getNotification().iconLevel,
                    notification.getNotification().number,
                    notification.getNotification().tickerText);
        if (!iconView.set(ic)) {
            handleNotificationError(key, notification, "Couldn't create icon: " + ic);
            return null;
        }

        NotificationData.Entry entry = new NotificationData.Entry(key, notification, iconView);
        prepareHaloNotification(entry, notification, false);
        entry.hide = entry.notification.getPackageName().equals("com.paranoid.halo");

        final PendingIntent contentIntent = notification.getNotification().contentIntent;
        if (contentIntent != null) {
            entry.floatingIntent = makeClicker(contentIntent,
                    notification.getPackageName(), notification.getTag(), notification.getId());
            entry.floatingIntent.makeFloating(true);
        }

        // Construct the expanded view.
        if (!inflateViews(entry, mPile)) {
            handleNotificationError(key, notification, "Couldn't expand RemoteViews for: "
                    + notification);
            return null;
        }

        if (mNotificationData.findByKey(entry.key) == null) {
            mNotificationData.add(entry);
        }

        return entry;
    }

    public void prepareHaloNotification(NotificationData.Entry entry, StatusBarNotification notification, boolean update) {

        Notification notif = notification.getNotification();

        // Get the remote view
        try {

            if (!update) {
                ViewGroup mainView = (ViewGroup)notif.contentView.apply(mContext, null, mOnClickHandler);

                if (mainView instanceof FrameLayout) {
                    entry.haloContent = mainView.getChildAt(1);
                    mainView.removeViewAt(1);
                } else {
                    entry.haloContent = mainView;
                }
            } else {
                notif.contentView.reapply(mContext, entry.haloContent, mOnClickHandler);
            }

        } catch (Exception e) {
            // Non uniform content?
            android.util.Log.d("PARANOID", " Non uniform content?");
        }


        // Construct the round icon
        final float haloSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        Resources res = mContext.getResources();
        int iconSize = (int) (res.getDimensionPixelSize(R.dimen.halo_bubble_size) * haloSize);
        int smallIconSize = (int) (res.getDimensionPixelSize(R.dimen.status_bar_icon_size) * haloSize);
        int largeIconWidth = notif.largeIcon != null ? (int)(notif.largeIcon.getWidth() * haloSize) : 0;
        int largeIconHeight = notif.largeIcon != null ? (int)(notif.largeIcon.getHeight() * haloSize) : 0;
        Bitmap roundIcon = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(roundIcon);
        canvas.drawARGB(0, 0, 0, 0);

        // If we have a bona fide avatar here stretching at least over half the size of our
        // halo-bubble, we'll use that one and cut it round
        // TODO: cache the halo bitmap, use 4.4 reveal pattern to draw the background
        if (notif.largeIcon != null
                && largeIconWidth >= iconSize / 2) {
            Paint smoothingPaint = new Paint();
            smoothingPaint.setAntiAlias(true);
            smoothingPaint.setFilterBitmap(true);
            smoothingPaint.setDither(true);
            canvas.drawCircle(iconSize / 2, iconSize / 2, iconSize / 2.3f, smoothingPaint);
            smoothingPaint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
            final int newWidth = iconSize;
            final int newHeight = iconSize * largeIconWidth / largeIconHeight;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(notif.largeIcon, newWidth, newHeight, true);
            canvas.drawBitmap(scaledBitmap, null, new Rect(0, 0,
                    iconSize, iconSize), smoothingPaint);
        } else {
            try {
                Drawable icon = StatusBarIconView.getIcon(mContext,
                    new StatusBarIcon(notification.getPackageName(), notification.getUser(), notif.icon,
                    notif.iconLevel, notif.number, notif.tickerText));
                if (icon == null) icon = mContext.getPackageManager().getApplicationIcon(notification.getPackageName());
                int margin = (iconSize - smallIconSize) / 2;
                icon.setBounds(margin, margin, iconSize - margin, iconSize - margin);
                icon.draw(canvas);
            } catch (Exception e) {
                // NameNotFoundException
            }
        }
        entry.roundIcon = roundIcon;
    }

    protected void addNotificationViews(NotificationData.Entry entry) {
        // Add the expanded view and icon.
        if (mNotificationData.findByKey(entry.key) == null) {
            int pos = mNotificationData.add(entry);
            if (DEBUG) {
                Log.d(TAG, "addNotificationViews: added at " + pos);
            }
        }
        updateExpansionStates();
        updateNotificationIcons();
    }

    private void addNotificationViews(IBinder key, StatusBarNotification notification) {
        addNotificationViews(createNotificationViews(key, notification));
    }

    protected void updateExpansionStates() {
        int N = mNotificationData.size();
        for (int i = 0; i < N; i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            if (!entry.row.isUserLocked()) {
                if (i == (N-1)) {
                    if (DEBUG) Log.d(TAG, "expanding top notification at " + i);
                    entry.row.setExpanded(true);
                } else {
                    if (!entry.row.isUserExpanded()) {
                        if (DEBUG) Log.d(TAG, "collapsing notification at " + i);
                        entry.row.setExpanded(false);
                    } else {
                        if (DEBUG) Log.d(TAG, "ignoring user-modified notification at " + i);
                    }
                }
            } else {
                if (DEBUG) Log.d(TAG, "ignoring notification being held by user at " + i);
            }
            if (entry.hide) entry.row.setVisibility(View.GONE);
        }
    }

    protected abstract void haltTicker();
    protected abstract void setAreThereNotifications();
    public abstract void updateNotificationIcons();
    protected abstract void tick(IBinder key, StatusBarNotification n, boolean firstTime);
    protected abstract void updateExpandedViewPos(int expandedPosition);
    protected abstract int getExpandedViewMaxHeight();
    protected abstract boolean shouldDisableNavbarGestures();

    protected boolean isTopNotification(ViewGroup parent, NotificationData.Entry entry) {
        return parent != null && parent.indexOfChild(entry.row) == 0;
    }

    public void updateNotification(IBinder key, StatusBarNotification notification) {
        if (DEBUG) Log.d(TAG, "updateNotification(" + key + " -> " + notification + ")");

        final NotificationData.Entry oldEntry = mNotificationData.findByKey(key);
        if (oldEntry == null) {
            Log.w(TAG, "updateNotification for unknown key: " + key);
            return;
        }

        final StatusBarNotification oldNotification = oldEntry.notification;

        // XXX: modify when we do something more intelligent with the two content views
        final RemoteViews oldContentView = oldNotification.getNotification().contentView;
        final RemoteViews contentView = notification.getNotification().contentView;
        final RemoteViews oldBigContentView = oldNotification.getNotification().bigContentView;
        final RemoteViews bigContentView = notification.getNotification().bigContentView;

        if (DEBUG) {
            Log.d(TAG, "old notification: when=" + oldNotification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " expanded=" + oldEntry.expanded
                    + " contentView=" + oldContentView
                    + " bigContentView=" + oldBigContentView
                    + " rowParent=" + oldEntry.row.getParent());
            Log.d(TAG, "new notification: when=" + notification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " contentView=" + contentView
                    + " bigContentView=" + bigContentView);
        }

        // Can we just reapply the RemoteViews in place?  If when didn't change, the order
        // didn't change.

        // 1U is never null
        boolean contentsUnchanged = oldEntry.expanded != null
                && contentView.getPackage() != null
                && oldContentView.getPackage() != null
                && oldContentView.getPackage().equals(contentView.getPackage())
                && oldContentView.getLayoutId() == contentView.getLayoutId();
        // large view may be null
        boolean bigContentsUnchanged =
                (oldEntry.getBigContentView() == null && bigContentView == null)
                || ((oldEntry.getBigContentView() != null && bigContentView != null)
                    && bigContentView.getPackage() != null
                    && oldBigContentView.getPackage() != null
                    && oldBigContentView.getPackage().equals(bigContentView.getPackage())
                    && oldBigContentView.getLayoutId() == bigContentView.getLayoutId());
        ViewGroup rowParent = (ViewGroup) oldEntry.row.getParent();
        boolean orderUnchanged = notification.getNotification().when== oldNotification.getNotification().when
                && notification.getScore() == oldNotification.getScore();
                // score now encompasses/supersedes isOngoing()

        boolean updateTicker = (notification.getNotification().tickerText != null
                && !TextUtils.equals(notification.getNotification().tickerText,
                        oldEntry.notification.getNotification().tickerText)) || mHaloActive;
        boolean isTopAnyway = isTopNotification(rowParent, oldEntry);
        if (contentsUnchanged && bigContentsUnchanged && (orderUnchanged || isTopAnyway)) {
            if (DEBUG) Log.d(TAG, "reusing notification for key: " + key);
            oldEntry.notification = notification;
            try {
                updateNotificationViews(oldEntry, notification);

                if (ENABLE_HEADS_UP && mInterruptingNotificationEntry != null
                        && oldNotification == mInterruptingNotificationEntry.notification) {
                    if (!shouldInterrupt(notification)) {
                        if (DEBUG) Log.d(TAG, "no longer interrupts!");
                        mHandler.sendEmptyMessage(MSG_HIDE_HEADS_UP);
                    } else {
                        if (DEBUG) Log.d(TAG, "updating the current heads up:" + notification);
                        mInterruptingNotificationEntry.notification = notification;
                        updateNotificationViews(mInterruptingNotificationEntry, notification);
                    }
                }

                // Update the icon.
                final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                        notification.getUser(),
                        notification.getNotification().icon, notification.getNotification().iconLevel,
                        notification.getNotification().number,
                        notification.getNotification().tickerText);
                if (!oldEntry.icon.set(ic)) {
                    handleNotificationError(key, notification, "Couldn't update icon: " + ic);
                    return;
                }
                updateExpansionStates();
            }
            catch (RuntimeException e) {
                // It failed to add cleanly.  Log, and remove the view from the panel.
                Log.w(TAG, "Couldn't reapply views for package " + contentView.getPackage(), e);
                removeNotificationViews(key);
                addNotificationViews(key, notification);
            }
        } else {
            if (DEBUG) Log.d(TAG, "not reusing notification for key: " + key);
            if (DEBUG) Log.d(TAG, "contents was " + (contentsUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Log.d(TAG, "order was " + (orderUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Log.d(TAG, "notification is " + (isTopAnyway ? "top" : "not top"));
            final boolean wasExpanded = oldEntry.row.isUserExpanded();
            removeNotificationViews(key);
            addNotificationViews(key, notification);  // will also replace the heads up
            if (wasExpanded) {
                final NotificationData.Entry newEntry = mNotificationData.findByKey(key);
                newEntry.row.setExpanded(true);
                if (newEntry.hide) newEntry.row.setVisibility(View.GONE);
                newEntry.row.setUserExpanded(true);
            }
        }

        // Update the veto button accordingly (and as a result, whether this row is
        // swipe-dismissable)
        updateNotificationVetoButton(oldEntry.row, notification);

        // Is this for you?
        boolean isForCurrentUser = notificationIsForCurrentUser(notification);
        if (DEBUG) Log.d(TAG, "notification is " + (isForCurrentUser ? "" : "not ") + "for you");

        // Restart the ticker if it's still running
        if (updateTicker && isForCurrentUser) {
            haltTicker();
            tick(key, notification, false);
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

        // Update halo
        if (mHalo != null) mHalo.update();
    }

    private void updateNotificationViews(NotificationData.Entry entry,
            StatusBarNotification notification) {
        final RemoteViews contentView = notification.getNotification().contentView;
        final RemoteViews bigContentView = notification.getNotification().bigContentView;
        // Reapply the RemoteViews
        contentView.reapply(mContext, entry.expanded, mOnClickHandler);
        if (bigContentView != null && entry.getBigContentView() != null) {
            bigContentView.reapply(mContext, entry.getBigContentView(), mOnClickHandler);
        }
        // update contentIntent and floatingIntent
        final PendingIntent contentIntent = notification.getNotification().contentIntent;
        if (contentIntent != null) {
            final View.OnClickListener listener = makeClicker(contentIntent,
                    notification.getPackageName(), notification.getTag(), notification.getId());
            entry.content.setOnClickListener(listener);
            entry.floatingIntent = makeClicker(contentIntent,
                    notification.getPackageName(), notification.getTag(), notification.getId());
            entry.floatingIntent.makeFloating(true);
        } else {
            entry.content.setOnClickListener(null);
            entry.floatingIntent = null;
        }
        // Update the roundIcon
        prepareHaloNotification(entry, notification, true);
    }

    protected void notifyHeadsUpScreenOn(boolean screenOn) {
        if (!screenOn && mInterruptingNotificationEntry != null) {
            mHandler.sendEmptyMessage(MSG_ESCALATE_HEADS_UP);
        }
    }

    protected boolean shouldInterrupt(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        // some predicates to make the boolean logic legible
        boolean isNoisy = (notification.defaults & Notification.DEFAULT_SOUND) != 0
                || (notification.defaults & Notification.DEFAULT_VIBRATE) != 0
                || notification.sound != null
                || notification.vibrate != null;
        boolean isHighPriority = sbn.getScore() >= INTERRUPTION_THRESHOLD;
        boolean isFullscreen = notification.fullScreenIntent != null;
        boolean isAllowed = notification.extras.getInt(Notification.EXTRA_AS_HEADS_UP,
                Notification.HEADS_UP_ALLOWED) != Notification.HEADS_UP_NEVER;

        final KeyguardTouchDelegate keyguard = KeyguardTouchDelegate.getInstance(mContext);
        boolean interrupt = (isFullscreen || (isHighPriority && isNoisy))
                && isAllowed
                && mPowerManager.isScreenOn()
                && !keyguard.isShowingAndNotHidden()
                && !keyguard.isInputRestricted();
        try {
            interrupt = interrupt && !mDreamManager.isDreaming();
        } catch (RemoteException e) {
            Log.d(TAG, "failed to query dream manager", e);
        }
        if (DEBUG) Log.d(TAG, "interrupt: " + interrupt);
        return interrupt;
    }

    // Q: What kinds of notifications should show during setup?
    // A: Almost none! Only things coming from the system (package is "android") that also
    // have special "kind" tags marking them as relevant for setup (see below).
    protected boolean showNotificationEvenIfUnprovisioned(StatusBarNotification sbn) {
        if ("android".equals(sbn.getPackageName())) {
            if (sbn.getNotification().kind != null) {
                for (String aKind : sbn.getNotification().kind) {
                    // IME switcher, created by InputMethodManagerService
                    if ("android.system.imeswitcher".equals(aKind)) return true;
                    // OTA availability & errors, created by SystemUpdateService
                    if ("android.system.update".equals(aKind)) return true;
                }
            }
        }
        return false;
    }

    public boolean inKeyguardRestrictedInputMode() {
        return KeyguardTouchDelegate.getInstance(mContext).isInputRestricted();
    }

    public void setInteracting(int barWindow, boolean interacting) {
        // hook for subclasses
    }

    public void destroy() {
        if (mSearchPanelView != null) {
            mWindowManager.removeViewImmediate(mSearchPanelView);
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    public void addNavigationBarCallback(NavigationBarCallback callback) {
        mNavigationCallbacks.add(callback);
    }

    protected void propagateNavigationIconHints(int hints) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setNavigationIconHints(hints);
        }
    }

    protected void propagateMenuVisibility(boolean showMenu) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setMenuVisibility(showMenu);
        }
    }

    protected void propagateDisabledFlags(int disabledFlags) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setDisabledFlags(disabledFlags);
        }
    }

    // Pie Controls
    public void updatePieTriggerMask(int newMask, boolean lock) {
        if (mPieController != null) {
            mPieController.updatePieTriggerMask(newMask, lock);
        }
    }

    public void restorePieTriggerMask() {
        if (mPieController != null) {
            mPieController.restorePieTriggerMask();
        }
    }

    private boolean isScreenPortrait() {
        return mContext.getResources()
            .getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            boolean targetKilled = false;
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
            for (RunningAppProcessInfo appInfo : apps) {
                int uid = appInfo.uid;
                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                        && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                        for (String pkg : appInfo.pkgList) {
                            if (!pkg.equals("com.android.systemui")
                                    && !pkg.equals(defaultHomePackage)) {
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
                    Toast.makeText(mContext,
                        R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    };

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private final Object mScreenshotLock = new Object();
    private ServiceConnection mScreenshotConnection = null;
    private Handler mHDL = new Handler();

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
                        Handler h = new Handler(mHDL.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHDL.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                mHDL.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private void getLastApp() {
        int lastAppId = 0;
        int looper = 1;
        String packageName;
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        while ((lastAppId == 0) && (looper < tasks.size())) {
            packageName = tasks.get(looper).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals("com.android.systemui")) {
                lastAppId = tasks.get(looper).id;
            }
            looper++;
        }
        if (lastAppId != 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        }
    }

    protected void addActiveDisplayView() {
        if (mActiveDisplayView == null) {
            mActiveDisplayView = (ActiveDisplayView)View.inflate(mContext, R.layout.active_display, null);
            mActiveDisplayView.setBar(this);
            mWindowManager.addView(mActiveDisplayView, getActiveDisplayViewLayoutParams());
        }
    }

    protected void removeActiveDisplayView() {
        if (mActiveDisplayView != null) {
            mWindowManager.removeView(mActiveDisplayView);
        }
    }

    protected WindowManager.LayoutParams getActiveDisplayViewLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.OPAQUE);
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.setTitle("ActiveDisplayView");

        return lp;
    }

    class SidebarObserver extends ContentObserver {
        SidebarObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_POSITION), false, this);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int sidebarPosition = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_POSITION, AppSidebar.SIDEBAR_POSITION_LEFT);
            if (sidebarPosition != mSidebarPosition) {
                mSidebarPosition = sidebarPosition;
                mWindowManager.updateViewLayout(mAppSidebar, getAppSidebarLayoutParams(sidebarPosition));
            }
        }
    }

    protected void addSidebarView() {
        mAppSidebar = (AppSidebar)View.inflate(mContext, R.layout.app_sidebar, null);
        mWindowManager.addView(mAppSidebar, getAppSidebarLayoutParams(mSidebarPosition));
    }

    protected void removeSidebarView() {
        if (mAppSidebar != null)
            mWindowManager.removeView(mAppSidebar);
    }

    protected WindowManager.LayoutParams getAppSidebarLayoutParams(int position) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        lp.gravity = Gravity.TOP;// | Gravity.FILL_VERTICAL;
        lp.gravity |= position == AppSidebar.SIDEBAR_POSITION_LEFT ? Gravity.LEFT : Gravity.RIGHT;
        lp.setTitle("AppSidebar");

        return lp;
    }

@ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected void addGestureAnywhereView() {
        mGestureAnywhereView = (GestureAnywhereView)View.inflate(
                mContext, R.layout.gesture_anywhere_overlay, null);
        mWindowManager.addView(mGestureAnywhereView, getGestureAnywhereViewLayoutParams(Gravity.LEFT));
        mGestureAnywhereView.setStatusBar(this);
    }

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected void removeGestureAnywhereView() {
        if (mGestureAnywhereView != null)
            mWindowManager.removeView(mGestureAnywhereView);
    }

    @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_METHOD)
    protected WindowManager.LayoutParams getGestureAnywhereViewLayoutParams(int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        lp.gravity = Gravity.TOP | gravity;
        lp.setTitle("GestureAnywhereView");

        return lp;
    }

    protected void setIconHiddenByUser(String iconPackage, boolean hide) {
        if (iconPackage == null
                || iconPackage.isEmpty()
                || iconPackage.equals("android")) {
            return;
        }
        mContext.getSharedPreferences("hidden_statusbar_icon_packages", 0)
                .edit()
                .putBoolean(iconPackage, hide)
                .apply();
    }

    protected boolean isIconHiddenByUser(String iconPackage) {
        if (iconPackage == null
                || iconPackage.isEmpty()
                || iconPackage.equals("android")) {
            return false;

        }
        final boolean hide = mContext.getSharedPreferences("hidden_statusbar_icon_packages", 0)
                .getBoolean(iconPackage, false);
        return hide;
    }
}
