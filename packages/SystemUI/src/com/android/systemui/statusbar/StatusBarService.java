/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.systemui.statusbar.CmBatteryMiniIcon.SettingsObserver;
import com.android.systemui.statusbar.powerwidget.PowerWidget;
import com.android.systemui.R;
import android.os.IPowerManager;
import android.provider.Settings.SettingNotFoundException;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.CustomTheme;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.CmSystem;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class StatusBarService extends Service implements CommandQueue.Callbacks {
    static final String TAG = "StatusBarService";
    static final boolean SPEW_ICONS = false;
    static final boolean SPEW = false;

    public static final String ACTION_STATUSBAR_START
            = "com.android.internal.policy.statusbar.START";

    // values changed onCreate if its a bottomBar
    static int EXPANDED_LEAVE_ALONE = -10000;
    static int EXPANDED_FULL_OPEN = -10001;

    private static final int MSG_ANIMATE = 1000;
    private static final int MSG_ANIMATE_REVEAL = 1001;

    StatusBarPolicy mIconPolicy;

    CommandQueue mCommandQueue;
    IStatusBarService mBarService;

    /**
     * Shallow container for {@link #mStatusBarView} which is added to the
     * window manager impl as the actual status bar root view. This is done so
     * that the original status_bar layout can be reinflated into this container
     * on skin change.
     */
    FrameLayout mStatusBarContainer;

    int mIconSize;
    Display mDisplay;
    CmStatusBarView mStatusBarView;
    int mPixelFormat;
    H mHandler = new H();
    Object mQueueLock = new Object();

    // last theme that was applied in order to detect theme change (as opposed
    // to some other configuration change).
    CustomTheme mCurrentTheme;

    // icons
    LinearLayout mIcons;
    IconMerger mNotificationIcons;
    LinearLayout mStatusIcons;

    // expanded notifications
    Dialog mExpandedDialog;
    ExpandedView mExpandedView;
    WindowManager.LayoutParams mExpandedParams;
    ScrollView mScrollView;
    View mNotificationLinearLayout;
    View mExpandedContents;
    // top bar
    TextView mNoNotificationsTitle;
    TextView mClearButton;
    TextView mCompactClearButton;
    ViewGroup mClearButtonParent;
    CmBatteryMiniIcon mCmBatteryMiniIcon;
    // drag bar
    CloseDragHandle mCloseView;
    // ongoing
    NotificationData mOngoing = new NotificationData();
    TextView mOngoingTitle;
    LinearLayout mOngoingItems;
    // latest
    NotificationData mLatest = new NotificationData();
    TextView mLatestTitle;
    LinearLayout mLatestItems;
    // position
    int[] mPositionTmp = new int[2];
    boolean mExpanded;
    boolean mExpandedVisible;

    // the date view
    DateView mDateView;

    // the tracker view
    TrackingView mTrackingView;
    WindowManager.LayoutParams mTrackingParams;
    int mTrackingPosition; // the position of the top of the tracking view.
    private boolean mPanelSlightlyVisible;

    // the power widget
    PowerWidget mPowerWidget;

    //Carrier label stuff
    LinearLayout mCarrierLabelLayout;
    LinearLayout mCompactCarrierLayout;
    LinearLayout mPowerAndCarrier;

    // ticker
    private Ticker mTicker;
    private View mTickerView;
    private boolean mTicking;

    // Tracking finger for opening/closing.
    int mEdgeBorder; // corresponds to R.dimen.status_bar_edge_ignore
    boolean mTracking;
    VelocityTracker mVelocityTracker;

    static final int ANIM_FRAME_DURATION = (1000/60);

    boolean mAnimating;
    long mCurAnimationTime;
    float mDisplayHeight;
    float mAnimY;
    float mAnimVel;
    float mAnimAccel;
    long mAnimLastTime;
    boolean mAnimatingReveal = false;
    int mViewDelta;
    int[] mAbsPos = new int[2];

    // for disabling the status bar
    int mDisabled = 0;

    // weather or not to show status bar on bottom
    boolean mBottomBar;
    boolean mButtonsLeft;
    boolean mDeadZone;
    boolean mHasSoftButtons;
    Context mContext;

    // tracks changes to settings, so status bar is moved to top/bottom
    // as soon as cmparts setting is changed
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BOTTOM), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTONS_LEFT), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_DEAD_ZONE), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_COMPACT_CARRIER), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_VIEW_WIDGET), false, this);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            int defValue;

            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_BOTTOM_STATUS_BAR) ? 1 : 0);
            mBottomBar = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_BOTTOM, defValue) == 1);
            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_SOFT_BUTTONS_LEFT) ? 1 : 0);
            mButtonsLeft = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTONS_LEFT, defValue) == 1);
            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_USE_DEAD_ZONE) ? 1 : 0);
            mDeadZone = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_DEAD_ZONE, defValue) == 1);
            mCompactCarrier = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_COMPACT_CARRIER, 0) == 1);
            updateLayout();
            updateCarrierLabel();
        }
    }

    // for brightness control on status bar
    int mLinger = 0;

    private class ExpandedDialog extends Dialog {
        ExpandedDialog(Context context) {
            super(context, com.android.internal.R.style.Theme_Light_NoTitleBar);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    animateCollapse();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }


    @Override
    public void onCreate() {
        // First set up our views and stuff.
        mDisplay = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        CustomTheme currentTheme = getResources().getConfiguration().customTheme;
        if (currentTheme != null) {
            mCurrentTheme = (CustomTheme)currentTheme.clone();
        }
        makeStatusBarView(this);

        // reset vars for bottom bar
        if(mBottomBar){
            EXPANDED_LEAVE_ALONE *= -1;
            EXPANDED_FULL_OPEN *= -1;
        }

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, filter);

        // Connect in to the status bar manager service
        StatusBarIconList iconList = new StatusBarIconList();
        ArrayList<IBinder> notificationKeys = new ArrayList<IBinder>();
        ArrayList<StatusBarNotification> notifications = new ArrayList<StatusBarNotification>();
        mCommandQueue = new CommandQueue(this, iconList);
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        try {
            mBarService.registerStatusBar(mCommandQueue, iconList, notificationKeys, notifications);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

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
            Slog.e(TAG, "Notification list length mismatch: keys=" + N
                    + " notifications=" + notifications.size());
        }

        // Put up the view
        FrameLayout container = new FrameLayout(this);
        container.addView(mStatusBarView);
        mStatusBarContainer = container;
        addStatusBarView();

        // Lastly, call to the icon policy to install/update all the icons.
        mIconPolicy = new StatusBarPolicy(this);

        // set up settings observer
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        // load config to determine if we want statusbar buttons
        mHasSoftButtons = CmSystem.getDefaultBool(mContext, CmSystem.CM_HAS_SOFT_BUTTONS);
    }

    @Override
    public void onDestroy() {
        // we're never destroyed
    }

    /**
     * Nobody binds to us.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean mCompactCarrier = false;

    // ================================================================================
    // Constructing the view
    // ================================================================================
    private void makeStatusBarView(Context context) {
        Resources res = context.getResources();

        mIconSize = res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_icon_size);

        //Check for compact carrier layout and apply if enabled
        mCompactCarrier = Settings.System.getInt(getContentResolver(),
                                                Settings.System.STATUS_BAR_COMPACT_CARRIER, 0) == 1;
        ExpandedView expanded = (ExpandedView)View.inflate(context,
                                                R.layout.status_bar_expanded, null);
        expanded.mService = this;

        CmStatusBarView sb = (CmStatusBarView)View.inflate(context, R.layout.status_bar, null);
        sb.mService = this;

        // figure out which pixel-format to use for the status bar.
        mPixelFormat = PixelFormat.TRANSLUCENT;
        Drawable bg = sb.getBackground();
        if (bg != null) {
            mPixelFormat = bg.getOpacity();
        }

        mStatusBarView = sb;
        mStatusIcons = (LinearLayout)sb.findViewById(R.id.statusIcons);
        mNotificationIcons = (IconMerger)sb.findViewById(R.id.notificationIcons);
        mIcons = (LinearLayout)sb.findViewById(R.id.icons);
        mTickerView = sb.findViewById(R.id.ticker);
        mDateView = (DateView)sb.findViewById(R.id.date);
        mCmBatteryMiniIcon = (CmBatteryMiniIcon)sb.findViewById(R.id.CmBatteryMiniIcon);

        mExpandedDialog = new ExpandedDialog(context);
        mExpandedView = expanded;
        mExpandedContents = expanded.findViewById(R.id.notificationLinearLayout);
        mOngoingTitle = (TextView)expanded.findViewById(R.id.ongoingTitle);
        mOngoingItems = (LinearLayout)expanded.findViewById(R.id.ongoingItems);
        mLatestTitle = (TextView)expanded.findViewById(R.id.latestTitle);
        mLatestItems = (LinearLayout)expanded.findViewById(R.id.latestItems);
        mNoNotificationsTitle = (TextView)expanded.findViewById(R.id.noNotificationsTitle);
        mClearButton = (TextView)expanded.findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);
        mCompactClearButton = (TextView)expanded.findViewById(R.id.compact_clear_all_button);
        mCompactClearButton.setOnClickListener(mClearButtonListener);
        mPowerAndCarrier = (LinearLayout)expanded.findViewById(R.id.power_and_carrier);
        mScrollView = (ScrollView)expanded.findViewById(R.id.scroll);
        mNotificationLinearLayout = expanded.findViewById(R.id.notificationLinearLayout);

        mExpandedView.setVisibility(View.GONE);
        mOngoingTitle.setVisibility(View.GONE);
        mLatestTitle.setVisibility(View.GONE);

        mPowerWidget = (PowerWidget)expanded.findViewById(R.id.exp_power_stat);
        mPowerWidget.setupSettingsObserver(mHandler);
        mPowerWidget.setGlobalButtonOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if(Settings.System.getInt(getContentResolver(),
                                Settings.System.EXPANDED_HIDE_ONCHANGE, 0) == 1) {
                            animateCollapse();
                        }
                    }
                });
        mPowerWidget.setGlobalButtonOnLongClickListener(new View.OnLongClickListener() {
                   public boolean onLongClick(View v) {
                       animateCollapse();
                       return true;
                   }
               });

        mCarrierLabelLayout = (LinearLayout)expanded.findViewById(R.id.carrier_label_layout);
        mCompactCarrierLayout = (LinearLayout)expanded.findViewById(R.id.compact_carrier_layout);

        mTicker = new MyTicker(context, sb);

        TickerView tickerView = (TickerView)sb.findViewById(R.id.tickerText);
        tickerView.mTicker = mTicker;

        mTrackingView = (TrackingView)View.inflate(context, R.layout.status_bar_tracking, null);
        mTrackingView.mService = this;
        mCloseView = (CloseDragHandle)mTrackingView.findViewById(R.id.close);
        mCloseView.mService = this;

        mContext=context;
        updateLayout();
        updateCarrierLabel();

        mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);

        // set the inital view visibility
        setAreThereNotifications();
        mDateView.setVisibility(View.INVISIBLE);
    }

    private void updateCarrierLabel() {
        if (mCompactCarrier) {
            mCarrierLabelLayout.setVisibility(View.GONE);
            mCompactCarrierLayout.setVisibility(View.VISIBLE);
            if (mLatest.hasClearableItems())
                mCompactClearButton.setVisibility(View.VISIBLE);
        } else {
            mCarrierLabelLayout.setVisibility(View.VISIBLE);
            mCompactCarrierLayout.setVisibility(View.GONE);
            mCompactClearButton.setVisibility(View.GONE);
        }

    }

    private void updateLayout() {
        if(mTrackingView==null || mCloseView==null || mExpandedView==null)
            return;

        // handle trackingview
        mTrackingView.removeView(mCloseView);
        mTrackingView.addView(mCloseView, mBottomBar ? 0 : 1);

        // handle expanded view reording for bottom bar
        LinearLayout powerAndCarrier=(LinearLayout)mExpandedView.findViewById(R.id.power_and_carrier);
        PowerWidget power=(PowerWidget)mExpandedView.findViewById(R.id.exp_power_stat);
        //FrameLayout notifications=(FrameLayout)mExpandedView.findViewById(R.id.notifications);

        // remove involved views
        powerAndCarrier.removeView(power);
        mExpandedView.removeView(powerAndCarrier);

        // readd in right order
        mExpandedView.addView(powerAndCarrier, mBottomBar ? 1 : 0);
        powerAndCarrier.addView(power, mBottomBar && !mCompactCarrier ? 1 : 0);

        //remove small ugly grey area if compactcarrier is enabled and power widget disabled
        boolean hideArea = mCompactCarrier &&
                           Settings.System.getInt(mContext.getContentResolver(),
                                   Settings.System.EXPANDED_VIEW_WIDGET, 1) == 0;
        mPowerAndCarrier.setVisibility(hideArea ? View.GONE : View.VISIBLE);
    }

    protected void addStatusBarView() {
        Resources res = getResources();
        final int height= res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);

        final View view = mStatusBarContainer;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING,
                PixelFormat.RGBX_8888);
        lp.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        lp.setTitle("StatusBar");
        lp.windowAnimations = com.android.internal.R.style.Animation_StatusBar;

        WindowManagerImpl.getDefault().addView(view, lp);

        mPowerWidget.setupWidget();
    }

    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        if (SPEW_ICONS) {
            Slog.d(TAG, "addIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                    + " icon=" + icon);
        }
        StatusBarIconView view = new StatusBarIconView(this, slot);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(mIconSize, mIconSize));
    }

    public void updateIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        if (SPEW_ICONS) {
            Slog.d(TAG, "updateIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                    + " old=" + old + " icon=" + icon);
        }
        StatusBarIconView view = (StatusBarIconView)mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
    }

    public void removeIcon(String slot, int index, int viewIndex) {
        if (SPEW_ICONS) {
            Slog.d(TAG, "removeIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex);
        }
        mStatusIcons.removeViewAt(viewIndex);
    }

    public void addNotification(IBinder key, StatusBarNotification notification) {
        boolean shouldTick = true;
        if (notification.notification.fullScreenIntent != null) {
            shouldTick = false;
            Slog.d(TAG, "Notification has fullScreenIntent; sending fullScreenIntent");
            try {
                notification.notification.fullScreenIntent.send();
            } catch (PendingIntent.CanceledException e) {
            }
        }

        StatusBarIconView iconView = addNotificationViews(key, notification);
        if (iconView == null) return;

        if (shouldTick) {
            tick(notification);
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
    }

    public void updateNotification(IBinder key, StatusBarNotification notification) {
        NotificationData oldList;
        int oldIndex = mOngoing.findEntry(key);
        if (oldIndex >= 0) {
            oldList = mOngoing;
        } else {
            oldIndex = mLatest.findEntry(key);
            if (oldIndex < 0) {
                Slog.w(TAG, "updateNotification for unknown key: " + key);
                return;
            }
            oldList = mLatest;
        }
        final NotificationData.Entry oldEntry = oldList.getEntryAt(oldIndex);
        final StatusBarNotification oldNotification = oldEntry.notification;
        final RemoteViews oldContentView = oldNotification.notification.contentView;

        final RemoteViews contentView = notification.notification.contentView;

        if (false) {
            Slog.d(TAG, "old notification: when=" + oldNotification.notification.when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " expanded=" + oldEntry.expanded
                    + " contentView=" + oldContentView);
            Slog.d(TAG, "new notification: when=" + notification.notification.when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " contentView=" + contentView);
        }

        // Can we just reapply the RemoteViews in place?  If when didn't change, the order
        // didn't change.
        if (notification.notification.when == oldNotification.notification.when
                && notification.isOngoing() == oldNotification.isOngoing()
                && oldEntry.expanded != null
                && contentView != null && oldContentView != null
                && contentView.getPackage() != null
                && oldContentView.getPackage() != null
                && oldContentView.getPackage().equals(contentView.getPackage())
                && oldContentView.getLayoutId() == contentView.getLayoutId()) {
            if (SPEW) Slog.d(TAG, "reusing notification");
            oldEntry.notification = notification;
            try {
                // Reapply the RemoteViews
                contentView.reapply(this, oldEntry.content);
                // update the contentIntent
                final PendingIntent contentIntent = notification.notification.contentIntent;
                if (contentIntent != null) {
                    oldEntry.content.setOnClickListener(new Launcher(contentIntent,
                                notification.pkg, notification.tag, notification.id));
                }
                // Update the icon.
                final StatusBarIcon ic = new StatusBarIcon(notification.pkg,
                        notification.notification.icon, notification.notification.iconLevel,
                        notification.notification.number);
                if (!oldEntry.icon.set(ic)) {
                    handleNotificationError(key, notification, "Couldn't update icon: " + ic);
                    return;
                }
            }
            catch (RuntimeException e) {
                // It failed to add cleanly.  Log, and remove the view from the panel.
                Slog.w(TAG, "Couldn't reapply views for package " + contentView.getPackage(), e);
                removeNotificationViews(key);
                addNotificationViews(key, notification);
            }
        } else {
            if (SPEW) Slog.d(TAG, "not reusing notification");
            removeNotificationViews(key);
            addNotificationViews(key, notification);
        }

        // Restart the ticker if it's still running
        if (notification.notification.tickerText != null
                && !TextUtils.equals(notification.notification.tickerText,
                    oldEntry.notification.notification.tickerText)) {
            tick(notification);
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
    }

    public void removeNotification(IBinder key) {
        if (SPEW) Slog.d(TAG, "removeNotification key=" + key);
        StatusBarNotification old = removeNotificationViews(key);

        if (old != null) {
            // Cancel the ticker if it's still running
            mTicker.removeEntry(old);

            // Recalculate the position of the sliding windows and the titles.
            setAreThereNotifications();
            updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
        }
    }

    private int chooseIconIndex(boolean isOngoing, int viewIndex) {
        final int latestSize = mLatest.size();
        if (isOngoing) {
            return latestSize + (mOngoing.size() - viewIndex);
        } else {
            return latestSize - viewIndex;
        }
    }

    View[] makeNotificationView(final StatusBarNotification notification, ViewGroup parent) {
        Notification n = notification.notification;
        RemoteViews remoteViews = n.contentView;
        if (remoteViews == null) {
            return null;
        }

        // create the row view
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LatestItemContainer row = (LatestItemContainer) inflater.inflate(R.layout.status_bar_latest_event, parent, false);
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) == 0 && (n.flags & Notification.FLAG_NO_CLEAR) == 0) {
            row.setOnSwipeCallback(new Runnable() {
                public void run() {
                    try {
                        mBarService.onNotificationClear(notification.pkg, notification.tag, notification.id);
                    } catch (RemoteException e) {
                        // Skip it, don't crash.
                    }
                }
            });
        }

        // bind the click event to the content area
        ViewGroup content = (ViewGroup)row.findViewById(R.id.content);
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        content.setOnFocusChangeListener(mFocusChangeListener);
        PendingIntent contentIntent = n.contentIntent;
        if (contentIntent != null) {
            content.setOnClickListener(new Launcher(contentIntent, notification.pkg,
                        notification.tag, notification.id));
        }

        View expanded = null;
        Exception exception = null;
        try {
            expanded = remoteViews.apply(this, content);
        }
        catch (RuntimeException e) {
            exception = e;
        }
        if (expanded == null) {
            String ident = notification.pkg + "/0x" + Integer.toHexString(notification.id);
            Slog.e(TAG, "couldn't inflate view for notification " + ident, exception);
            return null;
        } else {
            content.addView(expanded);
            row.setDrawingCacheEnabled(true);
        }

        return new View[] { row, content, expanded };
    }

    StatusBarIconView addNotificationViews(IBinder key, StatusBarNotification notification) {
        NotificationData list;
        ViewGroup parent;
        final boolean isOngoing = notification.isOngoing();
        if (isOngoing) {
            list = mOngoing;
            parent = mOngoingItems;
        } else {
            list = mLatest;
            parent = mLatestItems;
        }
        // Construct the expanded view.
        final View[] views = makeNotificationView(notification, parent);
        if (views == null) {
            handleNotificationError(key, notification, "Couldn't expand RemoteViews for: "
                    + notification);
            return null;
        }
        final View row = views[0];
        final View content = views[1];
        final View expanded = views[2];
        // Construct the icon.
        final StatusBarIconView iconView = new StatusBarIconView(this,
                notification.pkg + "/0x" + Integer.toHexString(notification.id));
        final StatusBarIcon ic = new StatusBarIcon(notification.pkg, notification.notification.icon,
                    notification.notification.iconLevel, notification.notification.number);
        if (!iconView.set(ic)) {
            handleNotificationError(key, notification, "Coulding create icon: " + ic);
            return null;
        }
        // Add the expanded view.
        final int viewIndex = list.add(key, notification, row, content, expanded, iconView);
        parent.addView(row, viewIndex);
        // Add the icon.
        final int iconIndex = chooseIconIndex(isOngoing, viewIndex);
        mNotificationIcons.addView(iconView, iconIndex);
        return iconView;
    }

    StatusBarNotification removeNotificationViews(IBinder key) {
        NotificationData.Entry entry = mOngoing.remove(key);
        if (entry == null) {
            entry = mLatest.remove(key);
            if (entry == null) {
                Slog.w(TAG, "removeNotification for unknown key: " + key);
                return null;
            }
        }
        // Remove the expanded view.
        ((ViewGroup)entry.row.getParent()).removeView(entry.row);
        // Remove the icon.
        ((ViewGroup)entry.icon.getParent()).removeView(entry.icon);

        return entry.notification;
    }

    private void setAreThereNotifications() {
        boolean ongoing = mOngoing.hasVisibleItems();
        boolean latest = mLatest.hasVisibleItems();

        // (no ongoing notifications are clearable)
        if (mLatest.hasClearableItems()) {
            if (mCompactCarrier) mCompactClearButton.setVisibility(View.VISIBLE);
            mClearButton.setVisibility(View.VISIBLE);
        } else {
            mCompactClearButton.setVisibility(View.GONE);
            mClearButton.setVisibility(View.INVISIBLE);
        }

        mOngoingTitle.setVisibility(ongoing ? View.VISIBLE : View.GONE);
        mLatestTitle.setVisibility(latest ? View.VISIBLE : View.GONE);

        if (ongoing || latest) {
            mNoNotificationsTitle.setVisibility(View.GONE);
        } else {
            mNoNotificationsTitle.setVisibility(View.VISIBLE);
        }
    }


    /**
     * State is one or more of the DISABLE constants from StatusBarManager.
     */
    public void disable(int state) {
        final int old = mDisabled;
        final int diff = state ^ old;
        mDisabled = state;

        if ((diff & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((state & StatusBarManager.DISABLE_EXPAND) != 0) {
                if (SPEW) Slog.d(TAG, "DISABLE_EXPAND: yes");
                animateCollapse();
            }
        }
        if ((diff & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
                if (SPEW) Slog.d(TAG, "DISABLE_NOTIFICATION_ICONS: yes");
                if (mTicking) {
                    mTicker.halt();
                } else {
                    setNotificationIconVisibility(false, com.android.internal.R.anim.fade_out);
                }
            } else {
                if (SPEW) Slog.d(TAG, "DISABLE_NOTIFICATION_ICONS: no");
                if (!mExpandedVisible) {
                    setNotificationIconVisibility(true, com.android.internal.R.anim.fade_in);
                }
            }
        } else if ((diff & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
            if (mTicking && (state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
                if (SPEW) Slog.d(TAG, "DISABLE_NOTIFICATION_TICKER: yes");
                mTicker.halt();
            }
        }
    }

    /**
     * All changes to the status bar and notifications funnel through here and are batched.
     */
    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_ANIMATE:
                    doAnimation();
                    break;
                case MSG_ANIMATE_REVEAL:
                    doRevealAnimation();
                    break;
            }
        }
    }

    View.OnFocusChangeListener mFocusChangeListener = new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            // Because 'v' is a ViewGroup, all its children will be (un)selected
            // too, which allows marqueeing to work.
            v.setSelected(hasFocus);
        }
    };

    private void makeExpandedVisible() {
        if (SPEW) Slog.d(TAG, "Make expanded visible: expanded visible=" + mExpandedVisible);
        if (mExpandedVisible) {
            return;
        }
        mExpandedVisible = true;
        visibilityChanged(true);

        mPowerWidget.updateWidget();

        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
        mExpandedParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mExpandedParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mExpandedDialog.getWindow().setAttributes(mExpandedParams);
        mExpandedView.requestFocus(View.FOCUS_FORWARD);
        mTrackingView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.VISIBLE);

        if (!mTicking) {
            setDateViewVisibility(true, com.android.internal.R.anim.fade_in);
        }
    }

    public void animateExpand() {
        if (SPEW) Slog.d(TAG, "Animate expand: expanded=" + mExpanded);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return ;
        }
        if (mExpanded) {
            return;
        }

        prepareTracking(0, true);
        performFling(0, 2000.0f, true);
    }

    public void animateCollapse() {
        if (SPEW) {
            Slog.d(TAG, "animateCollapse(): mExpanded=" + mExpanded
                    + " mExpandedVisible=" + mExpandedVisible
                    + " mExpanded=" + mExpanded
                    + " mAnimating=" + mAnimating
                    + " mAnimY=" + mAnimY
                    + " mAnimVel=" + mAnimVel);
        }

        if (!mExpandedVisible) {
            return;
        }

        int y;
        if (mAnimating) {
            y = (int)mAnimY;
        } else {
            if(mBottomBar)
                y = 0;
            else
                y = mDisplay.getHeight()-1;
        }
        // Let the fling think that we're open so it goes in the right direction
        // and doesn't try to re-open the windowshade.
        mExpanded = true;
        prepareTracking(y, false);
        performFling(y, -2000.0f, true);
    }

    void performExpand() {
        if (SPEW) Slog.d(TAG, "performExpand: mExpanded=" + mExpanded);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return ;
        }
        if (mExpanded) {
            return;
        }

        mExpanded = true;
        mStatusBarView.updateQuickNaImage();
        makeExpandedVisible();
        updateExpandedViewPos(EXPANDED_FULL_OPEN);

        if (false) postStartTracing();
    }

    void performCollapse() {
        if (SPEW) Slog.d(TAG, "performCollapse: mExpanded=" + mExpanded
                + " mExpandedVisible=" + mExpandedVisible
                + " mTicking=" + mTicking);

        if (!mExpandedVisible) {
            return;
        }
        mExpandedVisible = false;
        visibilityChanged(false);
        mExpandedParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mExpandedParams.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mExpandedDialog.getWindow().setAttributes(mExpandedParams);
        mTrackingView.setVisibility(View.GONE);
        mExpandedView.setVisibility(View.GONE);

        if ((mDisabled & StatusBarManager.DISABLE_NOTIFICATION_ICONS) == 0) {
            setNotificationIconVisibility(true, com.android.internal.R.anim.fade_in);
        }
        if (mDateView.getVisibility() == View.VISIBLE) {
            setDateViewVisibility(false, com.android.internal.R.anim.fade_out);
        }

        if (!mExpanded) {
            return;
        }
        mExpanded = false;
        mStatusBarView.updateQuickNaImage();
    }

    void doAnimation() {
        if (mAnimating) {
            if (SPEW) Slog.d(TAG, "doAnimation");
            if (SPEW) Slog.d(TAG, "doAnimation before mAnimY=" + mAnimY);
            incrementAnim();
            if (SPEW) Slog.d(TAG, "doAnimation after  mAnimY=" + mAnimY);
            if ((!mBottomBar && mAnimY >= mDisplay.getHeight()-1) || (mBottomBar && mAnimY <= 0)) {
                if (SPEW) Slog.d(TAG, "Animation completed to expanded state.");
                mAnimating = false;
                updateExpandedViewPos(EXPANDED_FULL_OPEN);
                performExpand();
            }
            else if ((!mBottomBar && mAnimY < mStatusBarView.getHeight())
                    || (mBottomBar && mAnimY > (mDisplay.getHeight()-mStatusBarView.getHeight()))) {
                if (SPEW) Slog.d(TAG, "Animation completed to collapsed state.");
                mAnimating = false;
                if(mBottomBar)
                    updateExpandedViewPos(mDisplay.getHeight());
                else
                    updateExpandedViewPos(0);
                performCollapse();
            }
            else {
                updateExpandedViewPos((int)mAnimY);
                mCurAnimationTime += ANIM_FRAME_DURATION;
                mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_ANIMATE), mCurAnimationTime);
            }
        }
    }

    void stopTracking() {
        mTracking = false;
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    void incrementAnim() {
        long now = SystemClock.uptimeMillis();
        float t = ((float)(now - mAnimLastTime)) / 1000;            // ms -> s
        final float y = mAnimY;
        final float v = mAnimVel;                                   // px/s
        final float a = mAnimAccel;                                 // px/s/s
        if(mBottomBar)
            mAnimY = y - (v*t) - (0.5f*a*t*t);                          // px
        else
            mAnimY = y + (v*t) + (0.5f*a*t*t);                          // px
        mAnimVel = v + (a*t);                                       // px/s
        mAnimLastTime = now;                                        // ms
        //Slog.d(TAG, "y=" + y + " v=" + v + " a=" + a + " t=" + t + " mAnimY=" + mAnimY
        //        + " mAnimAccel=" + mAnimAccel);
    }

    void doRevealAnimation() {
        int h = mCloseView.getHeight() + mStatusBarView.getHeight();

        if(mBottomBar)
            h = mDisplay.getHeight() - mStatusBarView.getHeight();
        if (mAnimatingReveal && mAnimating &&
                ((mBottomBar && mAnimY > h) || (!mBottomBar && mAnimY < h))) {
            incrementAnim();
            if ((mBottomBar && mAnimY <= h) || (!mBottomBar && mAnimY >=h)) {
                mAnimY = h;
                updateExpandedViewPos((int)mAnimY);
            } else {
                updateExpandedViewPos((int)mAnimY);
                mCurAnimationTime += ANIM_FRAME_DURATION;
                mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_ANIMATE_REVEAL),
                        mCurAnimationTime);
            }
        }
    }

    void prepareTracking(int y, boolean opening) {
        mTracking = true;
        mVelocityTracker = VelocityTracker.obtain();
        if (opening) {
            mAnimAccel = 2000.0f;
            mAnimVel = 200;
            mAnimY = mBottomBar ? mDisplay.getHeight() : mStatusBarView.getHeight();
            updateExpandedViewPos((int)mAnimY);
            mAnimating = true;
            mAnimatingReveal = true;
            mHandler.removeMessages(MSG_ANIMATE);
            mHandler.removeMessages(MSG_ANIMATE_REVEAL);
            long now = SystemClock.uptimeMillis();
            mAnimLastTime = now;
            mCurAnimationTime = now + ANIM_FRAME_DURATION;
            mAnimating = true;
            mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_ANIMATE_REVEAL),
                    mCurAnimationTime);
            makeExpandedVisible();
        } else {
            // it's open, close it?
            if (mAnimating) {
                mAnimating = false;
                mHandler.removeMessages(MSG_ANIMATE);
            }
            updateExpandedViewPos(y + mViewDelta);
        }
    }

    void performFling(int y, float vel, boolean always) {
        mAnimatingReveal = false;
        mDisplayHeight = mDisplay.getHeight();

        mAnimY = y;
        mAnimVel = vel;

        //Slog.d(TAG, "starting with mAnimY=" + mAnimY + " mAnimVel=" + mAnimVel);

        if (mExpanded) {
            if (!always &&
                    ((mBottomBar && (vel < -200.0f || (y < 25 && vel < 200.0f))) ||
                    (!mBottomBar && (vel >  200.0f || (y > (mDisplayHeight-25) && vel > -200.0f))))) {
                // We are expanded, but they didn't move sufficiently to cause
                // us to retract.  Animate back to the expanded position.
                mAnimAccel = 2000.0f;
                if (vel < 0) {
                    mAnimVel *= -1;
                }
            }
            else {
                // We are expanded and are now going to animate away.
                mAnimAccel = -2000.0f;
                if (vel > 0) {
                    mAnimVel *= -1;
                }
            }
        } else {
            if (always
                    || ( mBottomBar && (vel < -200.0f || (y < (mDisplayHeight/2) && vel <  200.0f)))
                    || (!mBottomBar && (vel >  200.0f || (y > (mDisplayHeight/2) && vel > -200.0f)))) {
                // We are collapsed, and they moved enough to allow us to
                // expand.  Animate in the notifications.
                mAnimAccel = 2000.0f;
                if (vel < 0) {
                    mAnimVel *= -1;
                }
            }
            else {
                // We are collapsed, but they didn't move sufficiently to cause
                // us to retract.  Animate back to the collapsed position.
                mAnimAccel = -2000.0f;
                if (vel > 0) {
                    mAnimVel *= -1;
                }
            }
        }
        //Slog.d(TAG, "mAnimY=" + mAnimY + " mAnimVel=" + mAnimVel
        //        + " mAnimAccel=" + mAnimAccel);

        long now = SystemClock.uptimeMillis();
        mAnimLastTime = now;
        mCurAnimationTime = now + ANIM_FRAME_DURATION;
        mAnimating = true;
        mHandler.removeMessages(MSG_ANIMATE);
        mHandler.removeMessages(MSG_ANIMATE_REVEAL);
        mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_ANIMATE), mCurAnimationTime);
        stopTracking();
    }

    boolean interceptTouchEvent(MotionEvent event) {
        if (SPEW) {
            Slog.d(TAG, "Touch: rawY=" + event.getRawY() + " event=" + event + " mDisabled="
                + mDisabled);
        }

        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return false;
        }

        if (!mTrackingView.mIsAttachedToWindow) {
            return false;
        }

        final int statusBarSize = mStatusBarView.getHeight();
        final int hitSize = statusBarSize*2;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            final int y = (int)event.getRawY();
            mLinger = 0;
            if (!mExpanded) {
                mViewDelta = mBottomBar ? mDisplay.getHeight() - y : statusBarSize - y;
            } else {
                mTrackingView.getLocationOnScreen(mAbsPos);
                mViewDelta = mAbsPos[1] + (mBottomBar ? 0 : mTrackingView.getHeight()) - y;
            }
            if ((!mBottomBar && ((!mExpanded && y < hitSize) || ( mExpanded && y > (mDisplay.getHeight()-hitSize)))) ||
                 (mBottomBar && (( mExpanded && y < hitSize) || (!mExpanded && y > (mDisplay.getHeight()-hitSize))))) {

                // We drop events at the edge of the screen to make the windowshade come
                // down by accident less, especially when pushing open a device with a keyboard
                // that rotates (like g1 and droid)
                int x = (int)event.getRawX();

                final int edgeBorder = mEdgeBorder;
                int edgeLeft = mButtonsLeft ? mStatusBarView.getSoftButtonsWidth() : 0;
                int edgeRight = mButtonsLeft ? 0 : mStatusBarView.getSoftButtonsWidth();

                final int w = mDisplay.getWidth();
                final int deadLeft = w / 2 - w / 4;  // left side of the dead zone
                final int deadRight = w / 2 + w / 4; // right side of the dead zone

                boolean expandedHit = (mExpanded && (x >= edgeBorder && x < w - edgeBorder));
                boolean collapsedHit = (!mExpanded && (x >= edgeBorder + edgeLeft && x < w - edgeBorder - edgeRight)
                                && (!mDeadZone || mDeadZone && (x < deadLeft || x > deadRight)));

                if (expandedHit || collapsedHit) {
                    prepareTracking(y, !mExpanded);// opening if we're not already fully visible
                    mVelocityTracker.addMovement(event);
                }
            }
        } else if (mTracking) {
            mVelocityTracker.addMovement(event);
            int minY = statusBarSize + mCloseView.getHeight();
            if (mBottomBar)
                minY = mDisplay.getHeight() - statusBarSize - mCloseView.getHeight();
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int y = (int)event.getRawY();
                if ((!mBottomBar && mAnimatingReveal && y < minY) ||
                        (mBottomBar && mAnimatingReveal && y > minY)) {
                        try {
                                if (Settings.System.getInt(mStatusBarView.getContext().getContentResolver(),Settings.System.STATUS_BAR_BRIGHTNESS_TOGGLE) == 1){
                                        //Credit for code goes to daryelv github : https://github.com/daryelv/android_frameworks_base
                                        // See if finger is moving left/right an adequate amount
                                        mVelocityTracker.computeCurrentVelocity(1000);
                                        float yVel = mVelocityTracker.getYVelocity();
                                        if (yVel < 0) {
                                                yVel = -yVel;
                                        }
                                        if (yVel < 50.0f) {
                                                if (mLinger > 50) {
                                                        // Check that Auto-Brightness not enabled
                                                        Context context = mStatusBarView.getContext();
                                                        boolean auto_brightness = false;
                                                        int brightness_mode = 0;
                                                        try {
                                                                brightness_mode = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
                                                        }catch (SettingNotFoundException e){
                                                                auto_brightness = false;
                                                        }
                                                        auto_brightness = (brightness_mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                                                        if (auto_brightness)
                                                        {
                                                                // do nothing - Don't manually set brightness from statusbar
                                                        }
                                                        else
                                                        {
                                                                // set brightness according to x position on statusbar
                                                                float x = (float)event.getRawX();
                                                                float screen_width = (float)(context.getResources().getDisplayMetrics().widthPixels);
                                                                // Brightness set from the 90% of pixels in the middle of screen, can't always get to the edges
                                                                int new_brightness = (int)(((x - (screen_width * 0.05f))/(screen_width * 0.9f)) * (float)android.os.Power.BRIGHTNESS_ON );
                                                                // don't let screen go completely dim or past 100% bright
                                                                if (new_brightness < 10) new_brightness = 10;
                                                                if (new_brightness > android.os.Power.BRIGHTNESS_ON ) new_brightness = android.os.Power.BRIGHTNESS_ON;
                                                                // Set the brightness
                                                                try {
                                                                        IPowerManager.Stub.asInterface(ServiceManager.getService("power")).setBacklightBrightness(new_brightness);
                                                                        Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, new_brightness);
                                                                }catch (Exception e){
                                                                        Slog.w(TAG, "Setting Brightness failed: " + e);
                                                                }
                                                       }
                                                }
                                                else
                                                {
                                                        mLinger++;
                                                }
                                        }
                                        else
                                        {
                                                mLinger = 0;
                                        }
                                }
                }catch (SettingNotFoundException e){
                }
                } else  {
                    mAnimatingReveal = false;
                    updateExpandedViewPos(y + (mBottomBar ? -mViewDelta : mViewDelta));
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mVelocityTracker.computeCurrentVelocity(1000);

                float yVel = mVelocityTracker.getYVelocity();
                boolean negative = yVel < 0;

                float xVel = mVelocityTracker.getXVelocity();
                if (xVel < 0) {
                    xVel = -xVel;
                }
                if (xVel > 150.0f) {
                    xVel = 150.0f; // limit how much we care about the x axis
                }

                float vel = (float)Math.hypot(yVel, xVel);
                if (negative) {
                    vel = -vel;
                }

                performFling((int)event.getRawY(), vel, false);
            }

        }
        return false;
    }

    private class Launcher implements View.OnClickListener {
        private PendingIntent mIntent;
        private String mPkg;
        private String mTag;
        private int mId;

        Launcher(PendingIntent intent, String pkg, String tag, int id) {
            mIntent = intent;
            mPkg = pkg;
            mTag = tag;
            mId = id;
        }

        public void onClick(View v) {
            try {
                // The intent we are sending is for the application, which
                // won't have permission to immediately start an activity after
                // the user switches to home.  We know it is safe to do at this
                // point, so make sure new activity switches are now allowed.
                ActivityManagerNative.getDefault().resumeAppSwitches();
            } catch (RemoteException e) {
            }

            if (mIntent != null) {
                int[] pos = new int[2];
                v.getLocationOnScreen(pos);
                Intent overlay = new Intent();
                overlay.setSourceBounds(
                        new Rect(pos[0], pos[1], pos[0]+v.getWidth(), pos[1]+v.getHeight()));
                try {
                    mIntent.send(StatusBarService.this, 0, overlay);
                } catch (PendingIntent.CanceledException e) {
                    // the stack trace isn't very helpful here.  Just log the exception message.
                    Slog.w(TAG, "Sending contentIntent failed: " + e);
                }
            }

            try {
                mBarService.onNotificationClick(mPkg, mTag, mId);
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }

            // close the shade if it was open
            animateCollapse();
        }
    }

    private void tick(StatusBarNotification n) {
        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if (n.notification.tickerText != null && mStatusBarView.getWindowToken() != null) {
            if (0 == (mDisabled & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                            | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                if(!mHasSoftButtons || mStatusBarView.getSoftButtonsWidth() == 0)
                    mTicker.addEntry(n);
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
            mBarService.onNotificationError(n.pkg, n.tag, n.id, n.uid, n.initialPid, message);
        } catch (RemoteException ex) {
            // The end is nigh.
        }
    }

    private class MyTicker extends Ticker {
        MyTicker(Context context, CmStatusBarView sb) {
            super(context, sb);
        }

        @Override
        void tickerStarting() {
            if (SPEW) Slog.d(TAG, "tickerStarting");
            mTicking = true;
            mIcons.setVisibility(View.GONE);
            mTickerView.setVisibility(View.VISIBLE);
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_up_in, null));
            mIcons.startAnimation(loadAnim(com.android.internal.R.anim.push_up_out, null));
            if (mExpandedVisible) {
                setDateViewVisibility(false, com.android.internal.R.anim.push_up_out);
            }
        }

        @Override
        void tickerDone() {
            if (SPEW) Slog.d(TAG, "tickerDone");
            mTicking = false;
            mIcons.setVisibility(View.VISIBLE);
            mTickerView.setVisibility(View.GONE);
            mIcons.startAnimation(loadAnim(com.android.internal.R.anim.push_down_in, null));
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_down_out, null));
            if (mExpandedVisible) {
                setDateViewVisibility(true, com.android.internal.R.anim.push_down_in);
            }
        }

        void tickerHalting() {
            if (SPEW) Slog.d(TAG, "tickerHalting");
            mTicking = false;
            mIcons.setVisibility(View.VISIBLE);
            mTickerView.setVisibility(View.GONE);
            mIcons.startAnimation(loadAnim(com.android.internal.R.anim.fade_in, null));
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.fade_out, null));
            if (mExpandedVisible) {
                setDateViewVisibility(true, com.android.internal.R.anim.fade_in);
            }
        }
    }

    private Animation loadAnim(int id, Animation.AnimationListener listener) {
        Animation anim = AnimationUtils.loadAnimation(StatusBarService.this, id);
        if (listener != null) {
            anim.setAnimationListener(listener);
        }
        return anim;
    }

    public String viewInfo(View v) {
        return "(" + v.getLeft() + "," + v.getTop() + ")(" + v.getRight() + "," + v.getBottom()
                + " " + v.getWidth() + "x" + v.getHeight() + ")";
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump StatusBar from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        synchronized (mQueueLock) {
            pw.println("Current Status Bar state:");
            pw.println("  mExpanded=" + mExpanded
                    + ", mExpandedVisible=" + mExpandedVisible);
            pw.println("  mTicking=" + mTicking);
            pw.println("  mTracking=" + mTracking);
            pw.println("  mAnimating=" + mAnimating
                    + ", mAnimY=" + mAnimY + ", mAnimVel=" + mAnimVel
                    + ", mAnimAccel=" + mAnimAccel);
            pw.println("  mCurAnimationTime=" + mCurAnimationTime
                    + " mAnimLastTime=" + mAnimLastTime);
            pw.println("  mDisplayHeight=" + mDisplayHeight
                    + " mAnimatingReveal=" + mAnimatingReveal
                    + " mViewDelta=" + mViewDelta);
            pw.println("  mDisplayHeight=" + mDisplayHeight);
            pw.println("  mExpandedParams: " + mExpandedParams);
            pw.println("  mExpandedView: " + viewInfo(mExpandedView));
            pw.println("  mExpandedDialog: " + mExpandedDialog);
            pw.println("  mTrackingParams: " + mTrackingParams);
            pw.println("  mTrackingView: " + viewInfo(mTrackingView));
            pw.println("  mOngoingTitle: " + viewInfo(mOngoingTitle));
            pw.println("  mOngoingItems: " + viewInfo(mOngoingItems));
            pw.println("  mLatestTitle: " + viewInfo(mLatestTitle));
            pw.println("  mLatestItems: " + viewInfo(mLatestItems));
            pw.println("  mNoNotificationsTitle: " + viewInfo(mNoNotificationsTitle));
            pw.println("  mCloseView: " + viewInfo(mCloseView));
            pw.println("  mTickerView: " + viewInfo(mTickerView));
            pw.println("  mScrollView: " + viewInfo(mScrollView)
                    + " scroll " + mScrollView.getScrollX() + "," + mScrollView.getScrollY());
            pw.println("mNotificationLinearLayout: " + viewInfo(mNotificationLinearLayout));
        }

        if (true) {
            // must happen on ui thread
            mHandler.post(new Runnable() {
                    public void run() {
                        Slog.d(TAG, "mStatusIcons:");
                        mStatusIcons.debug();
                    }
                });
        }

    }

    void onBarViewAttached() {
        WindowManager.LayoutParams lp;
        int pixelFormat;
        Drawable bg;

        /// ---------- Tracking View --------------
        pixelFormat = PixelFormat.TRANSLUCENT;
        bg = mTrackingView.getBackground();
        if (bg != null) {
            pixelFormat = bg.getOpacity();
        }

        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                pixelFormat);
//        lp.token = mStatusBarView.getWindowToken();
        lp.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        lp.setTitle("TrackingView");
        lp.y = mTrackingPosition;
        mTrackingParams = lp;

        WindowManagerImpl.getDefault().addView(mTrackingView, lp);
    }

    void onBarViewDetached() {
        WindowManagerImpl.getDefault().removeView(mTrackingView);
    }

    void onTrackingViewAttached() {
        WindowManager.LayoutParams lp;
        int pixelFormat;
        Drawable bg;

        /// ---------- Expanded View --------------
        pixelFormat = PixelFormat.TRANSLUCENT;

        final int disph = mDisplay.getHeight();
        lp = mExpandedDialog.getWindow().getAttributes();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = getExpandedHeight();
        lp.x = 0;
        mTrackingPosition = lp.y = (mBottomBar ? disph : -disph); // sufficiently large positive
        lp.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_DITHER
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.format = pixelFormat;
        lp.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        lp.setTitle("StatusBarExpanded");
        mExpandedDialog.getWindow().setAttributes(lp);
        mExpandedDialog.getWindow().setFormat(pixelFormat);
        mExpandedParams = lp;

        mExpandedDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        mExpandedDialog.setContentView(mExpandedView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                           ViewGroup.LayoutParams.MATCH_PARENT));
        mExpandedDialog.getWindow().setBackgroundDrawable(null);
        mExpandedDialog.show();
        FrameLayout hack = (FrameLayout)mExpandedView.getParent();
    }

    void onTrackingViewDetached() {
    }

    void setDateViewVisibility(boolean visible, int anim) {
        if(mHasSoftButtons && mButtonsLeft)
            return;

        mDateView.setUpdates(visible);
        mDateView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        mDateView.startAnimation(loadAnim(anim, null));
    }

    void setNotificationIconVisibility(boolean visible, int anim) {
        int old = mNotificationIcons.getVisibility();
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (old != v) {
            mNotificationIcons.setVisibility(v);
            mNotificationIcons.startAnimation(loadAnim(anim, null));
        }
    }

    void updateExpandedViewPos(int expandedPosition) {
        if (SPEW) {
            Slog.d(TAG, "updateExpandedViewPos before expandedPosition=" + expandedPosition
                    + " mTrackingParams.y="
                    + ((mTrackingParams == null) ? "???" : mTrackingParams.y)
                    + " mTrackingPosition=" + mTrackingPosition);
        }

        int h = mBottomBar ? 0 : mStatusBarView.getHeight();
        int disph = mDisplay.getHeight();

        // If the expanded view is not visible, make sure they're still off screen.
        // Maybe the view was resized.
        if (!mExpandedVisible) {
            if (mTrackingView != null) {
                mTrackingPosition = mBottomBar ? disph : -disph;
                if (mTrackingParams != null) {
                    mTrackingParams.y = mTrackingPosition;
                    WindowManagerImpl.getDefault().updateViewLayout(mTrackingView, mTrackingParams);
                }
            }
            if (mExpandedParams != null) {
                mExpandedParams.y = mBottomBar ? disph : -disph;
                mExpandedDialog.getWindow().setAttributes(mExpandedParams);
            }
            return;
        }

        // tracking view...
        int pos;
        if (expandedPosition == EXPANDED_FULL_OPEN) {
            pos = h;
        }
        else if (expandedPosition == EXPANDED_LEAVE_ALONE) {
            pos = mTrackingPosition;
        }
        else {
            if ((mBottomBar && expandedPosition >= 0) || (!mBottomBar && expandedPosition <= disph)) {
                pos = expandedPosition;
            } else {
                pos = disph;
            }
            pos -= mBottomBar ? mCloseView.getHeight() : disph-h;
        }
        if(mBottomBar && pos < 0)
            pos=0;

        mTrackingPosition = mTrackingParams.y = pos;
        mTrackingParams.height = disph-h;
        WindowManagerImpl.getDefault().updateViewLayout(mTrackingView, mTrackingParams);

        if (mExpandedParams != null) {
            mCloseView.getLocationInWindow(mPositionTmp);
            final int closePos = mPositionTmp[1];

            mExpandedContents.getLocationInWindow(mPositionTmp);
            final int contentsBottom = mPositionTmp[1] + mExpandedContents.getHeight();

            if (expandedPosition != EXPANDED_LEAVE_ALONE) {
                if(mBottomBar)
                    mExpandedParams.y = pos + mCloseView.getHeight();
                else
                    mExpandedParams.y = pos + mTrackingView.getHeight()
                        - (mTrackingParams.height-closePos) - contentsBottom;
                int max = mBottomBar ? mDisplay.getHeight() : h;
                if (mExpandedParams.y > max) {
                    mExpandedParams.y = max;
                }
                int min = mBottomBar ? mCloseView.getHeight() : mTrackingPosition;
                if (mExpandedParams.y < min) {
                    mExpandedParams.y = min;
                    if(mBottomBar)
                        mTrackingParams.y = 0;
                }

                boolean visible = mBottomBar ? mTrackingPosition < mDisplay.getHeight()
                        : (mTrackingPosition + mTrackingView.getHeight()) > h;
                if (!visible) {
                    // if the contents aren't visible, move the expanded view way off screen
                    // because the window itself extends below the content view.
                    mExpandedParams.y = mBottomBar ? disph : -disph;
                }
                mExpandedDialog.getWindow().setAttributes(mExpandedParams);

                if (SPEW) Slog.d(TAG, "updateExpandedViewPos visibilityChanged(" + visible + ")");
                visibilityChanged(visible);
            }
        }

        if (SPEW) {
            Slog.d(TAG, "updateExpandedViewPos after  expandedPosition=" + expandedPosition
                    + " mTrackingParams.y=" + mTrackingParams.y
                    + " mTrackingView.getHeight=" + mTrackingView.getHeight()
                    + " mTrackingPosition=" + mTrackingPosition
                    + " mExpandedParams.y=" + mExpandedParams.y
                    + " mExpandedParams.height=" + mExpandedParams.height);
        }
    }

    int getExpandedHeight() {
        return mDisplay.getHeight() - mStatusBarView.getHeight() - mCloseView.getHeight();
    }

    void updateExpandedHeight() {
        if (mExpandedView != null) {
            mExpandedParams.height = getExpandedHeight();
            mExpandedDialog.getWindow().setAttributes(mExpandedParams);
        }
    }

    /**
     * The LEDs are turned o)ff when the notification panel is shown, even just a little bit.
     * This was added last-minute and is inconsistent with the way the rest of the notifications
     * are handled, because the notification isn't really cancelled.  The lights are just
     * turned off.  If any other notifications happen, the lights will turn back on.  Steve says
     * this is what he wants. (see bug 1131461)
     */
    void visibilityChanged(boolean visible) {
        if (mPanelSlightlyVisible != visible) {
            mPanelSlightlyVisible = visible;
            try {
                mBarService.onPanelRevealed();
            } catch (RemoteException ex) {
                // Won't fail unless the world has ended.
            }
        }
    }

    void performDisableActions(int net) {
        int old = mDisabled;
        int diff = net ^ old;
        mDisabled = net;

        // act accordingly
        if ((diff & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((net & StatusBarManager.DISABLE_EXPAND) != 0) {
                Slog.d(TAG, "DISABLE_EXPAND: yes");
                animateCollapse();
            }
        }
        if ((diff & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((net & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
                Slog.d(TAG, "DISABLE_NOTIFICATION_ICONS: yes");
                if (mTicking) {
                    mNotificationIcons.setVisibility(View.INVISIBLE);
                    mTicker.halt();
                } else {
                    setNotificationIconVisibility(false, com.android.internal.R.anim.fade_out);
                }
            } else {
                Slog.d(TAG, "DISABLE_NOTIFICATION_ICONS: no");
                if (!mExpandedVisible) {
                    setNotificationIconVisibility(true, com.android.internal.R.anim.fade_in);
                }
            }
        } else if ((diff & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
            Slog.d(TAG, "DISABLE_NOTIFICATION_TICKER: "
                + (((net & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0)
                    ? "yes" : "no"));
            if (mTicking && (net & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
                mTicker.halt();
            }
        }
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            try {
                mBarService.onClearAllNotifications();
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }
            animateCollapse();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                animateCollapse();
            } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                updateResources();
            }
        }
    };

    private static void copyNotifications(ArrayList<Pair<IBinder, StatusBarNotification>> dest,
            NotificationData source) {
        int N = source.size();
        for (int i = 0; i < N; i++) {
            NotificationData.Entry entry = source.getEntryAt(i);
            dest.add(Pair.create(entry.key, entry.notification));
        }
    }

    private void recreateStatusBar() {
        mStatusBarContainer.removeAllViews();

        // extract icons from the soon-to-be recreated viewgroup.
        int nIcons = mStatusIcons.getChildCount();
        ArrayList<StatusBarIcon> icons = new ArrayList<StatusBarIcon>(nIcons);
        ArrayList<String> iconSlots = new ArrayList<String>(nIcons);
        for (int i = 0; i < nIcons; i++) {
            StatusBarIconView iconView = (StatusBarIconView)mStatusIcons.getChildAt(i);
            icons.add(iconView.getStatusBarIcon());
            iconSlots.add(iconView.getStatusBarSlot());
        }

        // extract notifications.
        int nNotifs = mOngoing.size() + mLatest.size();
        ArrayList<Pair<IBinder, StatusBarNotification>> notifications =
                new ArrayList<Pair<IBinder, StatusBarNotification>>(nNotifs);
        copyNotifications(notifications, mOngoing);
        copyNotifications(notifications, mLatest);
        mOngoing.clear();
        mLatest.clear();

        makeStatusBarView(this);

        // recreate StatusBarIconViews.
        for (int i = 0; i < nIcons; i++) {
            StatusBarIcon icon = icons.get(i);
            String slot = iconSlots.get(i);
            addIcon(slot, i, i, icon);
        }

        // recreate notifications.
        for (int i = 0; i < nNotifs; i++) {
            Pair<IBinder, StatusBarNotification> notifData = notifications.get(i);
            addNotificationViews(notifData.first, notifData.second);
        }

        setAreThereNotifications();
        mStatusBarContainer.addView(mStatusBarView);
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
    }

    /**
     * Reload some of our resources when the configuration changes.
     *
     * We don't reload everything when the configuration changes -- we probably
     * should, but getting that smooth is tough.  Someday we'll fix that.  In the
     * meantime, just update the things that we know change.
     */
    void updateResources() {
        Resources res = getResources();

        // detect theme change.
        CustomTheme newTheme = res.getConfiguration().customTheme;
        if (newTheme != null &&
                (mCurrentTheme == null || !mCurrentTheme.equals(newTheme))) {
            mCurrentTheme = (CustomTheme)newTheme.clone();
            mCmBatteryMiniIcon.updateIconCache();
            mCmBatteryMiniIcon.updateMatrix();
            recreateStatusBar();
        } else {
            mClearButton.setText(getText(R.string.status_bar_clear_all_button));
            mOngoingTitle.setText(getText(R.string.status_bar_ongoing_events_title));
            mLatestTitle.setText(getText(R.string.status_bar_latest_events_title));
            mNoNotificationsTitle.setText(getText(R.string.status_bar_no_notifications_title));

            mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);
        }

        if (false) Slog.v(TAG, "updateResources");
    }

    //
    // tracing
    //

    void postStartTracing() {
        mHandler.postDelayed(mStartTracing, 3000);
    }

    void vibrate() {
        android.os.Vibrator vib = (android.os.Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(250);
    }

    Runnable mStartTracing = new Runnable() {
        public void run() {
            vibrate();
            SystemClock.sleep(250);
            Slog.d(TAG, "startTracing");
            android.os.Debug.startMethodTracing("/data/statusbar-traces/trace");
            mHandler.postDelayed(mStopTracing, 10000);
        }
    };

    Runnable mStopTracing = new Runnable() {
        public void run() {
            android.os.Debug.stopMethodTracing();
            Slog.d(TAG, "stopTracing");
            vibrate();
        }
    };
}
