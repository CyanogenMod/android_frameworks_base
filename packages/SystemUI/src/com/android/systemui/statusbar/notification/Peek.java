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
import android.animation.LayoutTransition;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.database.ContentObserver;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.policy.Clock;

import java.util.List;
import java.util.ArrayList;

public class Peek implements SensorActivityHandler.SensorChangedCallback {

    private final static String TAG = "Peek";
    public final static boolean DEBUG = false;

    private static final String PEEK_APPLICATION = "com.jedga.peek";

    private static final float ICON_LOW_OPACITY = 0.3f;
    private static final long SCREEN_ON_START_DELAY = 300; // 300 ms
    private static final long REMOVE_VIEW_DELAY = 300; // 300 ms

    private int mPeekPickupTimeout;
    private int mPeekWakeTimeout;

    private BaseStatusBar mStatusBar;

    private SensorActivityHandler mSensorHandler;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private WindowManager mWindowManager;

    private PowerManager.WakeLock mPartialWakeLock;
    private Runnable mPartialWakeLockRunnable;
    private Handler mWakeLockHandler;
    private Handler mHandler;

    private List<StatusBarNotification> mShownNotifications
            = new ArrayList<StatusBarNotification>();
    private StatusBarNotification mNextNotification;
    private RelativeLayout mPeekView;
    private LinearLayout mNotificationView;
    private LinearLayout mNotificationsContainer;
    private ImageView mNotificationIcon;
    private TextView mNotificationText;

    private NotificationHelper mNotificationHelper;

    private Context mContext;

    private boolean mShowing;
    private boolean mAnimating;

    public boolean mEnabled;

    private boolean mEventsRegistered = true;

    private boolean isKeyguardSecureShowing() {
        return mKeyguardManager.isKeyguardLocked() && mKeyguardManager.isKeyguardSecure();
    }

    private boolean isPeekAppInstalled() {
        return isPackageInstalled(PEEK_APPLICATION);
    }

    private boolean isPackageInstalled(String packagename) {
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private void updateStatus() {
        mEnabled = Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.ACTIVE_NOTIFICATIONS_MODE,
                0) == 3 && !isPeekAppInstalled();
        if (mEnabled) {
            mSensorHandler.registerScreenReceiver();
        } else {
            mHandler.removeCallbacksAndMessages(null);
            mSensorHandler.unregisterScreenReceiver();
            mSensorHandler.unregisterEventListeners();
        }
    }

    public Peek(BaseStatusBar statusBar, Context context) {
        mStatusBar = statusBar;
        mContext = context;
        mPowerManager = mStatusBar.getPowerManagerInstance();

        mNotificationHelper = null;

        mSensorHandler = new SensorActivityHandler(context, this);
        mHandler = new Handler(Looper.getMainLooper());
        mWakeLockHandler = new Handler();

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACTIVE_NOTIFICATIONS_MODE),
                        false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateStatus();
            }
        });
        updateStatus();

        mPartialWakeLockRunnable = new Runnable() {
            @Override
            public void run() {
                // After PARTIAL_WAKELOCK_TIME with no user interaction, release CPU wakelock
                // and unregister event listeners.
                if(mPartialWakeLock.isHeld()) {
                    if(mEventsRegistered) {
                        if(DEBUG) Log.d(TAG, "Removing event listeners");
                        mSensorHandler.unregisterEventListeners();
                        mEventsRegistered = false;
                    }
                    mPartialWakeLock.release();
                }
            }
        };

        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        mPartialWakeLock = mPowerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, getClass().getSimpleName() + "_partial");
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // build the layout
        mPeekView = new RelativeLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                int action = event.getAction();
                if(action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_MOVE) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        mHandler.removeCallbacksAndMessages(null);
                    }
                }
                if(action == MotionEvent.ACTION_UP) scheduleTasks();
                return super.onInterceptTouchEvent(event);
            }
        };
        mPeekView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    // hide hover if showing
                    mStatusBar.getHoverInstance().dismissHover(false, false);
                    dismissNotification();
                }
                return true;
            }
        });

        // root view
        PeekLayout rootView = new PeekLayout(context);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setPeek(this);
        rootView.setId(1);

        mPeekView.addView(rootView);

        RelativeLayout.LayoutParams rootLayoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        rootLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        rootView.setLayoutParams(rootLayoutParams);

        // clock
        Clock clock = new Clock(context);
        Typeface clockTypeface = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        clock.setTypeface(clockTypeface);
        clock.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.clock_size));
        clock.setPadding(0, mContext.getResources()
                .getDimensionPixelSize(R.dimen.clock_padding), 0, 0);
        mPeekView.addView(clock);

        RelativeLayout.LayoutParams clockLayoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        clockLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        clock.setLayoutParams(clockLayoutParams);

        // notification container
        mNotificationView = new LinearLayout(context);
        mNotificationView.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(mNotificationView);

        // current notification icon
        mNotificationIcon = new ImageView(context);
        mNotificationIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mNotificationIcon.setOnTouchListener(NotificationHelper.getHighlightTouchListener(Color.DKGRAY));

        // current notification text
        mNotificationText = new TextView(context);
        Typeface textTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        mNotificationText.setTypeface(textTypeface);
        mNotificationText.setGravity(Gravity.CENTER);
        mNotificationText.setEllipsize(TextUtils.TruncateAt.END);
        mNotificationText.setSingleLine(true);
        mNotificationText.setPadding(0, mContext.getResources()
                .getDimensionPixelSize(R.dimen.item_padding), 0, 0);

        mNotificationView.addView(mNotificationIcon);
        mNotificationView.addView(mNotificationText);

        int iconSize = mContext.getResources()
                .getDimensionPixelSize(R.dimen.notification_icon_size);
        LinearLayout.LayoutParams linearLayoutParams
                = new LinearLayout.LayoutParams(iconSize, iconSize);
        linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        mNotificationIcon.setLayoutParams(linearLayoutParams);

        linearLayoutParams
                = new LinearLayout.LayoutParams(
                        mContext.getResources()
                                .getDimensionPixelSize(R.dimen.notification_text_width),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        mNotificationText.setLayoutParams(linearLayoutParams);

        // notification icons
        mNotificationsContainer = new LinearLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                int action = ev.getAction();
                if(action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_MOVE) {
                    StatusBarNotification n = getNotificationFromEvent(ev);
                    if(n != null) {
                        updateSelection(n);
                    }
                }
                return true;
            }
        };
        mNotificationsContainer.setOrientation(LinearLayout.HORIZONTAL);
        mNotificationsContainer.setPadding(0, mContext.getResources()
                .getDimensionPixelSize(R.dimen.item_padding) * 2, 0, 0);
        LayoutTransition transitioner = new LayoutTransition();
        transitioner.enableTransitionType(LayoutTransition.CHANGING);
        transitioner.disableTransitionType(LayoutTransition.DISAPPEARING);
        transitioner.disableTransitionType(LayoutTransition.APPEARING);
        transitioner.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        transitioner.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        mNotificationsContainer.setLayoutTransition(transitioner);

        mPeekView.addView(mNotificationsContainer);

        RelativeLayout.LayoutParams notificationsLayoutParams
                = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        notificationsLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        notificationsLayoutParams.addRule(RelativeLayout.BELOW, rootView.getId());
        mNotificationsContainer.setLayoutParams(notificationsLayoutParams);
    }

    public IStatusBarService getStatusBarService() {
        return mStatusBar.getStatusBarService();
    }

    public View getNotificationView() {
        return mNotificationView;
    }

    public void setNotificationHelper(NotificationHelper notificationHelper) {
        mNotificationHelper = notificationHelper;
    }

    public void setAnimating(boolean animating) {
        mAnimating = animating;
    }

    public boolean isShowing() {
        return mShowing;
    }

    private void scheduleTasks() {
        mHandler.removeCallbacksAndMessages(null);

        mPeekWakeTimeout = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PEEK_WAKE_TIMEOUT, 5000, UserHandle.USER_CURRENT);

        // turn on screen task
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(DEBUG) Log.d(TAG, "Turning screen on");
                mPowerManager.wakeUp(SystemClock.uptimeMillis());
            }
        }, SCREEN_ON_START_DELAY);

        // turn off screen task
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mShowing) {
                    if(DEBUG) Log.d(TAG, "Turning screen off");
                    mPowerManager.goToSleep(SystemClock.uptimeMillis());
                }
            }
        }, SCREEN_ON_START_DELAY + mPeekWakeTimeout);

        // remove view task (make sure screen is off by delaying a bit)
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissNotification();
            }
        }, SCREEN_ON_START_DELAY + (mPeekWakeTimeout * (long) 1.3));
    }

    public void showNotification(StatusBarNotification n, boolean update) {
        showNotification(n, update, false);
    }

    private void showNotification(StatusBarNotification n, boolean update, boolean force) {
        boolean shouldDisplay = shouldDisplayNotification(n) || force;
        addNotification(n);

        // first check if is blacklisted
        boolean allowed = true; // default on
        try {
            allowed = mStatusBar.getNotificationManager().isPackageAllowedForPeek(n.getPackageName());
        } catch (android.os.RemoteException ex) {
            // System is dead
        }
        if(!allowed) return;

        // not blacklisted, process it
        if(!mEnabled /* peek is disabled */
                || (mPowerManager.isScreenOn() && !mShowing) /* no peek when screen is on */
                || !shouldDisplay /* notification has already been displayed */
                || !n.isClearable() /* persistent notification */
                || mNotificationHelper.isSimPanelShowing() /* is sim not unlocked */
                || mNotificationHelper.isRingingOrConnected() /* is phone ringing? */) return;

        if (isNotificationActive(n) && (!update || (update && shouldDisplay))) {
            // update information
            updateNotificationIcons();
            updateSelection(n);

            // check if phone is in the pocket or lying on a table
            if(mSensorHandler.isInPocket() || mSensorHandler.isOnTable()) {
                if(DEBUG) Log.d(TAG, "Queueing notification");

                // use partial wakelock to get sensors working
                if(mPartialWakeLock.isHeld()) {
                    if(DEBUG) Log.d(TAG, "Releasing partial wakelock");
                    mPartialWakeLock.release();
                }

                if(DEBUG) Log.d(TAG, "Acquiring partial wakelock");
                mPartialWakeLock.acquire();
                if(!mEventsRegistered) {
                    mSensorHandler.registerEventListeners();
                    mEventsRegistered = true;
                }

                mWakeLockHandler.removeCallbacks(mPartialWakeLockRunnable);
                mPeekPickupTimeout = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PEEK_PICKUP_TIMEOUT, 10000, UserHandle.USER_CURRENT);
                mWakeLockHandler.postDelayed(mPartialWakeLockRunnable, mPeekPickupTimeout);

                mNextNotification = n;
                return;
            }

            mWakeLockHandler.removeCallbacks(mPartialWakeLockRunnable);

            addNotificationView(n); // add view instantly
            if(!mAnimating) scheduleTasks();
        }
    }

    private void addNotificationView(StatusBarNotification n) {
        if(!mShowing) {
            mShowing = true;
            mPeekView.setAlpha(1f);
            mPeekView.setVisibility(View.VISIBLE);
            mPeekView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            mWindowManager.addView(mPeekView, getLayoutParams());
        }
    }

    public void dismissNotification() {
        if(mShowing) {
            mShowing = false;
            mPeekView.animate().alpha(0f).setListener(
                    new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if(DEBUG) Log.d(TAG, "Dismissing view");
                    if(mPartialWakeLock.isHeld()) {
                        if(DEBUG) Log.d(TAG, "Releasing partial wakelock");
                        mPartialWakeLock.release();
                    }
                    mWindowManager.removeView(mPeekView);
                    mPeekView.setVisibility(View.GONE);
                }
            });
        }
    }

    public void addNotification(StatusBarNotification n) {
        for(int i = 0; i < mShownNotifications.size(); i++) {
            if(NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(mShownNotifications.get(i)))) {
                mShownNotifications.set(i, n);
                return;
            }
        }
        mShownNotifications.add(n);
    }

    public void removeNotification(StatusBarNotification n) {
        for(int i = 0; i < mShownNotifications.size(); i++) {
            if(NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(mShownNotifications.get(i)))) {
                mShownNotifications.remove(i);
                i--;
            }
        }
        updateNotificationIcons();
    }

    public void updateNotificationIcons() {
        mNotificationsContainer.removeAllViews();
        int iconSize = mContext.getResources()
                .getDimensionPixelSize(R.dimen.small_notification_icon_size);
        int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.small_notification_icon_padding);
        Object tag = mNotificationView.getTag();
        String currentNotification = tag != null ? ((StatusBarNotification) tag).toString() : null;
        boolean foundCurrentNotification = false;
        int notificationCount = mStatusBar.getNotificationCount();
        mNotificationsContainer.setVisibility(View.VISIBLE);
        if (notificationCount <= 1) {
            mNotificationsContainer.setVisibility(View.GONE);
        }
        for (int i = 0; i < notificationCount; i++) {
            final StatusBarNotification n = mStatusBar.getNotifications().get(i).notification;
            ImageView icon = new ImageView(mContext);
            if(n.toString().equals(currentNotification)) {
                foundCurrentNotification = true;
            } else {
                icon.setAlpha(ICON_LOW_OPACITY);
            }
            icon.setPadding(padding, 0, padding, 0);
            icon.setImageDrawable(getIconFromResource(n));
            icon.setTag(n);
            mNotificationsContainer.addView(icon);
            LinearLayout.LayoutParams linearLayoutParams
                    = new LinearLayout.LayoutParams(iconSize, iconSize);
            icon.setLayoutParams(linearLayoutParams);
        }
        if(!foundCurrentNotification) {
            if (notificationCount > 0) {
                updateSelection(mStatusBar
                        .getNotifications().get(notificationCount - 1).notification);
            } else {
                dismissNotification();
            }
        }
    }

    private void updateSelection(StatusBarNotification n) {
        String oldNotif = NotificationHelper.getContentDescription(
                (StatusBarNotification) mNotificationView.getTag());
        String newNotif = NotificationHelper.getContentDescription(n);
        boolean sameNotification = newNotif.equals(oldNotif);
        if(!mAnimating || sameNotification) {
            // update big icon
            Bitmap b = n.getNotification().largeIcon;
            if(b != null) {
                mNotificationIcon.setImageBitmap(getRoundedShape(b));
            } else {
                mNotificationIcon.setImageDrawable(getIconFromResource(n));
            }
            final PendingIntent contentIntent = n.getNotification().contentIntent;
            if (contentIntent != null) {
                final View.OnClickListener listener = mStatusBar.makeClicker(contentIntent,
                        n.getPackageName(), n.getTag(), n.getId());
                mNotificationIcon.setOnClickListener(listener);
            } else {
                mNotificationIcon.setOnClickListener(null);
            }
            mNotificationText.setText(getNotificationDisplayText(n));
            mNotificationText.setVisibility(isKeyguardSecureShowing() ? View.GONE : View.VISIBLE);
            mNotificationView.setTag(n);

            if(!sameNotification) {
                mNotificationView.setAlpha(1f);
                mNotificationView.setX(0);
            }
        }

        // update small icons
        for(int i = 0; i < mNotificationsContainer.getChildCount(); i++) {
            ImageView view = (ImageView) mNotificationsContainer.getChildAt(i);
            if((mAnimating ? oldNotif : newNotif).equals(NotificationHelper
                    .getContentDescription((StatusBarNotification) view.getTag()))) {
                view.setAlpha(1f);
            } else {
                view.setAlpha(ICON_LOW_OPACITY);
            }
        }
    }

    private boolean isNotificationActive(StatusBarNotification n) {
        for(int i = 0; i < mStatusBar.getNotificationCount(); i++) {
            if(NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(mStatusBar
                            .getNotifications().get(i).notification))) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldDisplayNotification(StatusBarNotification n) {
        if (n.getNotification().priority < Notification.PRIORITY_DEFAULT) return false;
        for(StatusBarNotification shown : mShownNotifications) {
            if(NotificationHelper.getContentDescription(n).equals(
                    NotificationHelper.getContentDescription(shown))) {
                return NotificationHelper
                        .shouldDisplayNotification(shown, n, true);
            }
        }
        return true;
    }

    private StatusBarNotification getNotificationFromEvent(MotionEvent event) {
        for(int i = 0; i < mNotificationsContainer.getChildCount(); i++) {
            View view = mNotificationsContainer.getChildAt(i);
            Rect rect = new Rect(view.getLeft(),
                    view.getTop(), view.getRight(), view.getBottom());
            if (rect.contains((int) event.getX(), (int) event.getY())) {
                if(view.getTag() instanceof StatusBarNotification) {
                    return (StatusBarNotification) view.getTag();
                }
            }
        }
        return null;
    }

    private String getNotificationDisplayText(StatusBarNotification n) {
        String text = null;
        if(n.getNotification().tickerText != null) {
            text = n.getNotification().tickerText.toString();
        }
        PackageManager pm = mContext.getPackageManager();
        if(n != null) {
            if (text == null) {
                text = NotificationHelper.getNotificationTitle(n);
                if(text == null) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(n.getPackageName(), 0);
                        text = (String) pm.getApplicationLabel(ai);
                    } catch (NameNotFoundException e) {
                        // application is uninstalled, run away
                        text = "";
                    }
                }
            }
        }
        return text;
    }

    public Bitmap getRoundedShape(Bitmap scaleBitmapImage) {
        int targetWidth = scaleBitmapImage.getWidth();
        int targetHeight = scaleBitmapImage.getHeight();
        Bitmap targetBitmap = Bitmap.createBitmap(
                targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(targetBitmap);
        Path path = new Path();
        path.addCircle(((float) targetWidth - 1) / 2, ((float) targetHeight - 1) / 2,
                (Math.min(((float) targetWidth), ((float) targetHeight)) / 2), Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmap = scaleBitmapImage;
        canvas.drawBitmap(sourceBitmap, 
                new Rect(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight()),
                new Rect(0, 0, targetWidth, targetHeight), null);
        return targetBitmap;
    }

    private Drawable getIconFromResource(StatusBarNotification n) {
        Drawable icon = null;
        String packageName = n.getPackageName();
        int resource = n.getNotification().icon;
        try {
            Context remotePackageContext = mContext.createPackageContext(packageName, 0);
            icon = remotePackageContext.getResources().getDrawable(resource);
        } catch(NameNotFoundException nnfe) {
            icon = new BitmapDrawable(mContext.getResources(), n.getNotification().largeIcon);
        }
        return icon;
    }

    private WindowManager.LayoutParams getLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        lp.dimAmount = 1f;
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    @Override
    public void onPocketModeChanged(boolean inPocket) {
        if(!inPocket && mNextNotification != null) {
            showNotification(mNextNotification, false, true);
            mNextNotification = null;
        }
    }

    @Override
    public void onTableModeChanged(boolean onTable) {
        if(!onTable && mNextNotification != null) {
            showNotification(mNextNotification, false, true);
            mNextNotification = null;
        }
    }

    @Override
    public void onScreenStateChaged(boolean screenOn) {
        if(!screenOn) {
            mHandler.removeCallbacksAndMessages(null);
            dismissNotification();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.System.PEEK_PICKUP_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.System.PEEK_WAKE_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
        }

        @Override public void onChange(boolean selfChange) {}
    }
}
