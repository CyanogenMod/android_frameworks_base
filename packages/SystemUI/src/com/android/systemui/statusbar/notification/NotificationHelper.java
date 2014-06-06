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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.LightingColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.systemui.statusbar.NotificationData.Entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class
 * Provides some helper methods
 * Works as a bridge between Hover, Peek and their surrounding layers
 */
public class NotificationHelper {

    public static final String DELIMITER = "|";
    public static final int HOVER_ALPHA = 175;
    public static final int DEFAULT_ALPHA = 255;

    private static final String PEEK_SHOWING_BROADCAST = "com.jedga.peek.PEEK_SHOWING";
    private static final String PEEK_HIDING_BROADCAST = "com.jedga.peek.PEEK_HIDING";
    private static final String FONT_FAMILY_DEFAULT = "sans-serif";
    private static final String FONT_FAMILY_LIGHT = "sans-serif-light";
    private static final String FONT_FAMILY_CONDENSED = "sans-serif-condensed";
    private static final double DISTANCE_THRESHOLD = 20.0;
    private static final int HOVER_STYLE = 0;
    private static final int DEFAULT_STYLE = 1;

    private BaseStatusBar mStatusBar;
    private Context mContext;
    private Hover mHover;
    private IntentFilter mPeekAppFilter;
    private Peek mPeek;
    private PeekAppReceiver mPeekAppReceiver;
    private TelephonyManager mTelephonyManager;
    private ActivityManager mActivityManager;

    public boolean mRingingOrConnected = false;
    public boolean mPeekAppOverlayShowing = false;

    /**
     * Creates a new instance
     * @Param context the current Context
     * @Param statusBar the current BaseStatusBar
     */
    public NotificationHelper(BaseStatusBar statusBar, Context context) {
        mContext = context;
        mStatusBar = statusBar;
        mHover = mStatusBar.getHoverInstance();
        mPeek = mStatusBar.getPeekInstance();
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(new CallStateListener(), PhoneStateListener.LISTEN_CALL_STATE);

        // we need to know which is the foreground app
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        // create peek app receiver if null
        if (mPeekAppReceiver == null) {
            mPeekAppReceiver = new PeekAppReceiver();
        }
        if (mPeekAppFilter == null) {
            mPeekAppFilter = new IntentFilter();
            mPeekAppFilter.addAction(PEEK_SHOWING_BROADCAST);
            mPeekAppFilter.addAction(PEEK_HIDING_BROADCAST);
            mContext.registerReceiver(mPeekAppReceiver, mPeekAppFilter);
        }
    }

    public String getForegroundPackageName() {
        List<RunningTaskInfo> taskInfo = mActivityManager.getRunningTasks(1);
        ComponentName componentInfo = taskInfo.get(0).topActivity;
        return componentInfo.getPackageName();
    }

    public Hover getHover() {
        return mHover;
    }

    public Peek getPeek() {
        return mPeek;
    }

    public boolean isHoverEnabled() {
        return mHover.mHoverActive;
    }

    public boolean isHoverShowing() {
        return mHover.isShowing();
    }
    
    public boolean isPeekEnabled() {
        return mPeek.mEnabled;
    }

    public boolean isPeekShowing() {
        return mPeek.isShowing();
    }

    public boolean isPeekAppShowing() {
        return mPeekAppOverlayShowing;
    }

    public class PeekAppReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PEEK_SHOWING_BROADCAST)) {
                mPeekAppOverlayShowing = true;
                mHover.dismissHover(false, false);
            } else if (action.equals(PEEK_HIDING_BROADCAST)) {
                mPeekAppOverlayShowing = false;
            }
        }
    }

    /**
     * Hint: <!-- XYZ --> = XYZ feature(s) make use of the following
     * -------------------------------------------------------------
     *
     * <!-- Hover -->
     * Views handling methods
     */
    public void reparentNotificationToHover(HoverNotification hoverNotification) {
        Entry entry = hoverNotification.getEntry();

        SizeAdaptiveLayout sal = hoverNotification.getLayout();
        sal.setTag(mHover.getContentDescription(entry.notification));
        sal.setOnClickListener(getNotificationClickListener(entry, true));
        sal.setVisibility(View.GONE);
        sal.setEnabled(false);

        applyStyle(sal, HOVER_STYLE);

        // Move to hover
        hoverNotification.reparentToHover();
    }

    public void reparentNotificationToStatusBar(HoverNotification hoverNotification) {
        Entry entry = hoverNotification.getEntry();

        // We move click listener from LatestItemView to SizeAdaptiveLayout.
        // This is safe to do as content has only one children.
        entry.content.setOnClickListener(null);
        SizeAdaptiveLayout sal = hoverNotification.getLayout();
        sal.setOnClickListener(getNotificationClickListener(entry, false));

        applyStyle(sal, DEFAULT_STYLE);

        // Move back to status bar
        hoverNotification.reparentToStatusBar();
    }

    public NotificationClicker getNotificationClickListener(Entry entry, boolean floating) {
        NotificationClicker intent = null;
        final PendingIntent contentIntent = entry.notification.getNotification().contentIntent;
        if (contentIntent != null) {
            intent = mHover.getStatusBar().makeClicker(contentIntent,
                    entry.notification.getPackageName(), entry.notification.getTag(),
                    entry.notification.getId());
            boolean makeFloating = floating
                    && !isNotificationBlacklisted(entry.notification.getPackageName())
                    // if the notification is from the foreground app, don't open in floating mode
                    && !entry.notification.getPackageName().equals(getForegroundPackageName());

            intent.makeFloating(makeFloating);
        }
        return intent;
    }

    public void applyStyle(SizeAdaptiveLayout layout, int style) {
        boolean forHover = (style == HOVER_STYLE);
        ViewGroup notificationView = ((ViewGroup) layout);
        if (notificationView.getBackground() == null) {
            // if the notification has no background, use default notification background
            notificationView.setBackgroundResource(com.android.internal.R.drawable.notification_bg);
        }
        setViewBackgroundAlpha(notificationView, forHover ? HOVER_ALPHA : DEFAULT_ALPHA);

        List<View> subViews = getAllViewsInLayout(notificationView);
        for(int i = 0; i < subViews.size(); i++) {
            View v = subViews.get(i);
            if (v instanceof ViewGroup) { // apply hover alpha to the view group
                setViewBackgroundAlpha(v, forHover ? HOVER_ALPHA : DEFAULT_ALPHA);
            } else if (v instanceof ImageView) { // remove image background
                setViewBackgroundAlpha(v, forHover ? HOVER_ALPHA : DEFAULT_ALPHA);
            } else if (v instanceof TextView) { // set font family
                boolean title = v.getId() == android.R.id.title;
                boolean bold = title;
                TextView text = ((TextView) v);
                text.setTypeface(Typeface.create(
                        forHover ? FONT_FAMILY_CONDENSED :
                                (title ? FONT_FAMILY_LIGHT : FONT_FAMILY_DEFAULT),
                                        bold ? Typeface.BOLD : Typeface.NORMAL));
            }
        }
    }

    public void setViewBackgroundAlpha(View v, int alpha) {
        Drawable bg = v.getBackground();
        if(bg != null) bg.setAlpha(alpha);
    }

    private boolean hasButtons(ViewGroup layout) {
        List<View> views = getAllViewsInLayout(layout);
        for(View v : views) {
            if (v instanceof Button) return true;
        }
        return false;
    }

    private List<View> getAllViewsInLayout(ViewGroup layout) {
        List<View> viewList = new ArrayList<View>();
        for(int i = 0; i < layout.getChildCount(); i++) {
            View v = layout.getChildAt(i);
            if (v instanceof ViewGroup) {
                viewList.addAll(getAllViewsInLayout((ViewGroup) v));
            }
            viewList.add(v);
        }
        return viewList;
    }

    /**
     * <!-- Peek && Hover -->
     * Main check to verify we need to show this notification or process it
     */
    public static boolean shouldDisplayNotification(
            StatusBarNotification oldNotif, StatusBarNotification newNotif, boolean when) {
        // First check for ticker text, if they are different, some other parameters will be
        // checked to determine if we should show the notification.
        CharSequence oldTickerText = oldNotif.getNotification().tickerText;
        CharSequence newTickerText = newNotif.getNotification().tickerText;

        if (newTickerText == null ? oldTickerText == null : newTickerText.equals(oldTickerText)) {
            // If old notification title isn't null, show notification if
            // new notification title is different. If it is null, show notification
            // if the new one isn't.
            String oldNotificationText = getNotificationTitle(oldNotif);
            String newNotificationText = getNotificationTitle(newNotif);
            if(newNotificationText == null ? oldNotificationText != null : 
                    !newNotificationText.equals(oldNotificationText)) return true;

            // Last chance, check when the notifications were posted. If times
            // are equal, we shouldn't display the new notification. (Should apply to peek only)
            if(when && (oldNotif.getNotification().when != newNotif.getNotification().when)) return true;
            return false;
        }
        return true;
    }

    /**
     * <!-- Peek && Hover -->
     * Helpers methods
     */
    public static String getNotificationTitle(StatusBarNotification n) {
        String text = null;
        if (n != null) {
            Notification notification = n.getNotification();
            Bundle extras = notification.extras;
            text = extras.getString(Notification.EXTRA_TITLE);
        }
        return text;
    }

    public static String getContentDescription(StatusBarNotification content) {
        if (content != null) {
            String tag = content.getTag() == null ? "null" : content.getTag();
            return content.getPackageName() + DELIMITER + content.getId() + DELIMITER + tag;
        }
        return null;
    }

    public boolean isNotificationBlacklisted(String packageName) {
        String[] blackList = mContext.getResources()
                .getStringArray(R.array.hover_blacklisted_packages);
        for(String s : blackList) {
            if (s.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * <!-- Peek -->
     * Touch brigde
     */
    public static View.OnTouchListener getHighlightTouchListener(final int color) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                Drawable drawable = ((ImageView) view).getDrawable();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        LightingColorFilter lighten
                                = new LightingColorFilter(color, color);
                        drawable.setColorFilter(lighten);
                        break;
                    case MotionEvent.ACTION_UP:
                        drawable.clearColorFilter();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        Rect rect = new Rect();
                        view.getLocalVisibleRect(rect);
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            drawable.clearColorFilter();
                        }
                        break;
                    case MotionEvent.ACTION_OUTSIDE:
                    case MotionEvent.ACTION_CANCEL:
                        drawable.clearColorFilter();
                        break;
                }
                return false;
            }
        };
    }

    /**
     * <!-- Peek && Hover -->
     * Call state listener
     * Telephony states booleans
     */
    private class CallStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    mRingingOrConnected = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    mRingingOrConnected = false;
                    break;
            }
        }
    }

    public boolean isRingingOrConnected() {
        return mRingingOrConnected;
    }

    public boolean isSimPanelShowing() {
        int state = mTelephonyManager.getSimState();
        return state == TelephonyManager.SIM_STATE_PIN_REQUIRED
                | state == TelephonyManager.SIM_STATE_PUK_REQUIRED
                | state == TelephonyManager.SIM_STATE_NETWORK_LOCKED;
    }
}
