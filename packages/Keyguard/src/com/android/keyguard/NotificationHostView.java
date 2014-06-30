/*
 * Copyright (C) 2013 Team AOSPAL
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

package com.android.keyguard;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.ActivityManagerNative;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;

import com.android.keyguard.KeyguardViewMediator.ViewMediatorCallback;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

public class NotificationHostView extends FrameLayout {
    private static final String TAG = "Keyguard:NotificationView";
    private static final int MSG_NOTIFICATION_ADD = 0;
    private static final int MSG_NOTIFICATION_REMOVE = 1;

    private static final float SWIPE = 0.2f;
    private static final int ANIMATION_MAX_DURATION = 300;
    private static final int PPS = 2000;
    private static final int MAX_ALPHA = 150;

    //Here we store dimissed notifications so we don't add them again in onFinishInflate
    private static HashMap<String, StatusBarNotification> mDismissedNotifications = new HashMap<String, StatusBarNotification>();

    private Queue<NotificationView> mNotificationsToAdd = new ArrayDeque<NotificationView>();
    private Queue<NotificationView> mNotificationsToRemove = new ArrayDeque<NotificationView>();
    private HashMap<String, NotificationView> mNotifications = new HashMap<String, NotificationView>();
    private INotificationManager mNotificationManager;
    private WindowManager mWindowManager;
    private int mNotificationMinHeight, mNotificationMinRowHeight;
    private int mNotificationMaxHeight, mNotificationMaxRowHeight;
    private int mDisplayWidth, mDisplayHeight;
    private int mShownNotifications = 0;

    private ViewMediatorCallback mViewMediatorCallback;
    private LinearLayout mNotifView;
    private TouchModalScrollView mScrollView;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_NOTIFICATION_ADD:
                    handleAddNotification(msg.arg1 == 1, msg.arg2 == 1);
                    break;
                case MSG_NOTIFICATION_REMOVE:
                    handleRemoveNotification(msg.arg1 == 1);
                    break;
            }
        }
    };

    public NotificationView getViewByPoint(int x, int y) {
        y += mScrollView.getScrollY();
        for (NotificationView nv : mNotifications.values()) {
            Rect hitRect = new Rect();
            nv.getChildAt(0).getHitRect(hitRect);
            hitRect.top = nv.getTop();
            hitRect.bottom = nv.getBottom();
            if (hitRect.contains(x, y))
                return nv;
        }
        return null;
    }

    public static class TouchModalScrollView extends ScrollView {
        private NotificationHostView hostView;
        private boolean touchAllowed = false;

        public TouchModalScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public void setHostView(NotificationHostView view) {
            hostView = view;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                NotificationView v = hostView.getViewByPoint((int)event.getX(), (int)event.getY());
                touchAllowed = (v != null);
            }
            if (touchAllowed) {
                return super.dispatchTouchEvent(event);
            }
            return false;
        }
    }

    private class NotificationView extends FrameLayout implements View.OnClickListener {
        private static final int CLICK_THRESHOLD = 10;

        private StatusBarNotification statusBarNotification;
        private int animationCount = 0;
        private long animationEndTime = 0;
        private boolean animating = false;
        private boolean preventClick = false;
        private float initialX;
        private float delta;
        private boolean shown = false;
        private float previousX = 0;
        private float previousTime = 0;
        private float speedX = 0;
        private float count = 0;
        private boolean swipeGesture = false;

        public NotificationView(Context context, StatusBarNotification sbn) {
            super(context);
            statusBarNotification = sbn;
        }

        public ViewPropertyAnimator animate() {
            final ViewPropertyAnimator animation = super.animate();
            animation.withEndAction(new Runnable() {
               public void run() {
                   animationCount--;
                   if (animationCount == 0) {
                       animationEndTime = 0;
                       animating = false;
                   }
               }
            });
            animation.withStartAction(new Runnable() {
                public void run() {
                    animating = true;
                    animationCount++;
                    long endTime = System.currentTimeMillis() + animation.getDuration();
                    if (endTime > animationEndTime) {
                        animationEndTime = endTime;
                    }
                }
             });
            return animation;
        }

        @Override
        public void onClick(View v) {
            if (!preventClick) {
                PendingIntent i = statusBarNotification.getNotification().contentIntent;
                if (i != null) {
                    try {
                        Intent intent = i.getIntent();
                        intent.setFlags(
                            intent.getFlags()
                            | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        if (i.isActivity()) ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                        i.send();
                    } catch (CanceledException ex) {
                        Log.e(TAG, "intent canceled!");
                    } catch (RemoteException ex) {
                        Log.e(TAG, "failed to dimiss keyguard!");
                    }
                }
            }
        }
        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            mViewMediatorCallback.userActivity();
            if (!NotificationViewManager.config.privacyMode) {
                View v = getChildAt(0);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getX();
                        delta = initialX - v.getX();
                        preventClick = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float time = System.nanoTime() / 1000000.0f;
                        float x = (event.getX() - delta);
                        float xr = x - (mDisplayWidth - v.getWidth());
                        speedX += (x - previousX) / (time - previousTime);
                        count++;
                        previousX = x;
                        previousTime = time;
                        if (speedX < 0 && x < mDisplayWidth - v.getWidth()) {
                            v.setAlpha(1f + (xr / (v.getWidth() * (SWIPE * 2))));
                        }
                        if (mShownNotifications == 0 || (shown && mShownNotifications == 1))
                            NotificationHostView.this.setBackgroundColor(Color.argb(MAX_ALPHA -
                                    (int)(Math.abs(xr) / v.getWidth() * MAX_ALPHA), 0, 0, 0));
                        if (swipeGesture  || Math.abs(event.getX() - initialX) > CLICK_THRESHOLD) {
                            swipeGesture = true;
                            preventClick = true;
                            v.cancelPendingInputEvents();
                            mScrollView.requestDisallowInterceptTouchEvent(true);
                            v.setTranslationX(x);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        speedX /= count;
                        if (v.getX() - (mDisplayWidth - v.getWidth())< -SWIPE * mDisplayWidth &&
                                (NotificationViewManager.config.dismissAll || statusBarNotification.isClearable())) {
                            removeNotification(statusBarNotification);
                        } else if (v.getX() < (SWIPE * mDisplayWidth)) {
                            showNotification(this);
                        } else if (v.getX() < ((1 - SWIPE) * mDisplayWidth) && speedX < 0) {
                            showNotification(this);
                        } else {
                            hideNotification(this);
                        }
                        speedX = 0;
                        count = 0;
                        swipeGesture = false;
                        break;
                }
            }
            return false;
        }

        @Override
        public void addView(View v) {
            v.setOnClickListener(this);
            super.addView(v);
        }
    }

    public NotificationHostView(Context context, AttributeSet attributes) {
        super(context, attributes);

        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mNotificationMinHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);
        mNotificationMaxHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_max_height);
        mNotificationMinRowHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        mNotificationMaxRowHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        mNotificationManager = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
    }

    @Override
    public void onFinishInflate() {
        if (NotificationViewManager.config != null) {
            mNotifications.clear();
            mNotificationsToAdd.clear();
            mNotificationsToRemove.clear();
            mShownNotifications = 0;
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    if (mShownNotifications > 0) {
                        hideAllNotifications();
                    }
                    return false;
                }
            });
            Point p = new Point();
            mWindowManager.getDefaultDisplay().getSize(p);
            mDisplayWidth = p.x;
            mDisplayHeight = p.y;
            mNotifView = (LinearLayout) findViewById(R.id.linearlayout);
            mScrollView = (TouchModalScrollView) findViewById(R.id.scrollview);
            mScrollView.setHostView(this);
            mScrollView.setY(mDisplayHeight * NotificationViewManager.config.offsetTop);
            int maxHeight = Math.round(mDisplayHeight - mDisplayHeight * NotificationViewManager.config.offsetTop);
            mScrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    Math.min(maxHeight, NotificationViewManager.config.notificationsHeight * mNotificationMinRowHeight)));
        }
    }

    public void addNotifications() {
        if (NotificationViewManager.NotificationListener != null) {
            try {
                StatusBarNotification[] sbns = mNotificationManager.getActiveNotificationsFromListener(NotificationViewManager.NotificationListener);
                StatusBarNotification dismissedSbn;
                for (StatusBarNotification sbn : sbns) {
                    if ((dismissedSbn = mDismissedNotifications.get(describeNotification(sbn))) == null || dismissedSbn.getPostTime() != sbn.getPostTime())
                        addNotification(sbn);
                }
                bringToFront();
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get active notifications!");
            }
        }
    }

    public void setViewMediator(ViewMediatorCallback viewMediator) {
        mViewMediatorCallback = viewMediator;
    }

    public boolean addNotification(StatusBarNotification sbn) {
        return addNotification(sbn, false, NotificationViewManager.config.forceExpandedView);
    }

    public boolean addNotification(StatusBarNotification sbn, boolean showNotification, boolean forceBigContentView) {
        if ((!NotificationViewManager.config.hideLowPriority || sbn.getNotification().priority > Notification.PRIORITY_LOW)
                && NotificationViewManager.NotificationListener.isValidNotification(sbn)
                && (!NotificationViewManager.config.hideNonClearable || sbn.isClearable())) {
            mNotificationsToAdd.add(new NotificationView(mContext, sbn));
            Message msg = new Message();
            msg.arg1 = showNotification ? 1 : 0;
            msg.arg2 = forceBigContentView ? 1 : 0;
            msg.what = MSG_NOTIFICATION_ADD;
            mHandler.sendMessage(msg);
            return true;
        }
        return false;
    }

    private void setBackgroundRecursive(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) {
                setBackgroundRecursive((ViewGroup)v);
                ((ViewGroup)v).setBackgroundColor(0x33555555);
            }
        }
    }

    private void handleAddNotification(final boolean showNotification, boolean forceBigContentView) {
        final NotificationView nv = mNotificationsToAdd.poll();
        Log.d(TAG, "Add: " + describeNotification(nv.statusBarNotification));
        final StatusBarNotification sbn = nv.statusBarNotification;
        mDismissedNotifications.remove(describeNotification(sbn));

        if (sbn.getNotification().contentView == null) {
            if (sbn.getNotification().bigContentView == null) {
                return;
            }
            forceBigContentView = true;
        }
        boolean bigContentView = sbn.getNotification().bigContentView != null &&
                (NotificationViewManager.config.expandedView || sbn.getNotification().contentView == null);
        RemoteViews rv = forceBigContentView && bigContentView ? sbn.getNotification().bigContentView : sbn.getNotification().contentView;
        final View remoteView = rv.apply(mContext, null);
        remoteView.setBackgroundColor(0x33ffffff);
        boolean dynamicWidth = getResources().getDisplayMetrics().density >= DisplayMetrics.DENSITY_XXHIGH;
        remoteView.setLayoutParams(new LayoutParams(dynamicWidth ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        remoteView.setX(mDisplayWidth - mNotificationMinHeight);
        if (bigContentView && forceBigContentView) {
            setBackgroundRecursive((ViewGroup)remoteView);
        }
        remoteView.setAlpha(1f);
        if (bigContentView && sbn.getNotification().contentView != null) {
            if (forceBigContentView) {
                remoteView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        addNotification(sbn, false, false);
                        return true;
                    }
                });
            } else {
                remoteView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        addNotification(sbn, false, true);
                        return true;
                    }
                });
            }
        }

        if (mNotifications.containsKey(describeNotification(sbn))){
            //The notification already exists, so it was just changed. Remove the old view and add the new one
            final NotificationView oldView = (NotificationView) mNotifications.get(describeNotification(sbn));
            Runnable r = new Runnable() {
                public void run() {
                    final float oldX = oldView.getChildAt(0).getX() - (mDisplayWidth - oldView.getChildAt(0).getWidth());
                    oldView.removeAllViews();
                    oldView.addView(remoteView);
                    oldView.statusBarNotification = sbn;
                    mHandler.post(new Runnable() {
                        public void run() {
                            remoteView.setX(oldX + (mDisplayWidth - remoteView.getWidth()));
                            if (showNotification) {
                                showNotification(oldView);
                            }
                        }
                    });
                }
            };
            if (oldView.animating) {
                mHandler.postDelayed(r, System.currentTimeMillis() - oldView.animationEndTime);
            } else {
                r.run();
            }
            return;
        }

        nv.addView(remoteView);
        nv.setPadding(0, 0, 0, mNotificationMinRowHeight - mNotificationMinHeight);

        mNotifView.addView(nv);
        mNotifications.put(describeNotification(sbn), nv);
        mNotifView.bringToFront();
        if (showNotification) {
            // showNotification uses v.getWidth but until the layout is done, this just returns 0.
            // by using mHandler.post, we wait until getWidth returns the real width
            mHandler.post(new Runnable() {
                public void run() {
                    showNotification(nv);
                }
            });
        }
    }

    public void removeNotification(final StatusBarNotification sbn) {
        removeNotification(sbn, true);
    }

    public void removeNotification(final StatusBarNotification sbn, boolean dismiss) {
        mNotificationsToRemove.add(mNotifications.get(describeNotification(sbn)));
        Message msg = new Message();
        msg.what = MSG_NOTIFICATION_REMOVE;
        msg.arg1 = dismiss ? 1 : 0;
        mHandler.sendMessage(msg);
    }

    private void handleRemoveNotification(final boolean dismiss) {
        final NotificationView v = mNotificationsToRemove.poll();
        final StatusBarNotification sbn = v.statusBarNotification;
        if (mNotifications.containsKey(describeNotification(sbn)) && sbn != null) {
            Log.d(TAG, "Remove: " + describeNotification(v.statusBarNotification));
            if (v.shown) {
                if (mShownNotifications > 0) mShownNotifications--;
                if (mShownNotifications == 0) {
                    animateBackgroundColor(0, getDurationFromDistance(v.getChildAt(0), mDisplayWidth, 0));
                }
            }
            if (!sbn.isClearable()) {
                mDismissedNotifications.put(describeNotification(sbn), sbn);
            }
            int duration =  getDurationFromDistance(v.getChildAt(0), v.shown ? -mDisplayWidth : mDisplayWidth, 0);
            v.getChildAt(0).animate().setDuration(duration).alpha(0).start();
            mNotifications.remove(describeNotification(sbn));
            animateTranslation(v.getChildAt(0), v.shown ? -mDisplayWidth : mDisplayWidth, 0,
                    duration,
                    new AnimatorListener() {
                        public void onAnimationStart(Animator animation) {}
                        public void onAnimationEnd(Animator animation) {
                            if (dismiss) {
                                INotificationManager nm = INotificationManager.Stub.asInterface(
                                        ServiceManager.getService(Context.NOTIFICATION_SERVICE));
                                try {
                                    nm.cancelNotificationFromListener(NotificationViewManager.NotificationListener, sbn.getPackageName(), sbn.getTag(), sbn.getId());
                                } catch (RemoteException ex) {
                                    Log.e(TAG, "Failed to cancel notification: " + sbn.getPackageName());
                                }
                            }
                            mNotifView.removeView(v);
                            mNotifView.requestLayout();
                        }
                        public void onAnimationCancel(Animator animation) {}
                        public void onAnimationRepeat(Animator animation) {}
            });
        }
    }

    public void showNotification(StatusBarNotification sbn) {
        showNotification(mNotifications.get(describeNotification(sbn)));
    }

    private void showNotification(NotificationView nv) {
        if (!NotificationViewManager.config.privacyMode) {
            View v = nv.getChildAt(0);
            int targetX = mDisplayWidth - v.getWidth();
            int duration = getDurationFromDistance(v, targetX, 0, Math.abs(nv.speedX));
            v.animate().setDuration(duration).alpha(1);
            animateTranslation(v, targetX, 0, duration);
            if (mShownNotifications == 0 ||
                    (mShownNotifications == 1 && nv.shown)) {
                animateBackgroundColor(Color.argb(MAX_ALPHA, 0, 0, 0), duration);
            }
            if (!nv.shown) {
                nv.shown = true;
                mShownNotifications++;
            }
        }
        bringToFront();
    }

    private void hideNotification(NotificationView nv) {
        View v = nv.getChildAt(0);
        int targetX = Math.round(mDisplayWidth - mNotificationMinHeight);
        int duration = getDurationFromDistance(v, targetX, (int)v.getY(), Math.abs(nv.speedX));
        if (mShownNotifications > 0 && nv.shown) mShownNotifications--;
        if (mShownNotifications == 0) animateBackgroundColor(0, duration);
        animateTranslation(v, targetX, 0, duration);
        nv.shown = false;
    }

    public void showAllNotifications() {
        for (NotificationView nv : mNotifications.values()) {
            showNotification (nv);
        }
        mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    public void hideAllNotifications() {
        for (NotificationView nv : mNotifications.values()) {
            if (nv.shown)
                hideNotification (nv);
        }
    }

    private void animateBackgroundColor(final int targetColor, final int duration) {
        if (!(getBackground() instanceof ColorDrawable)) {
            setBackgroundColor(0x0);
        }
        final ObjectAnimator colorFade = ObjectAnimator.ofObject(this, "backgroundColor", new ArgbEvaluator(),
                ((ColorDrawable)getBackground()).getColor(),
                targetColor);
        colorFade.setDuration(Math.min(duration, ANIMATION_MAX_DURATION));
        Runnable r = new Runnable() {
            public void run() {
                colorFade.start();
            }
        };
        if (Looper.myLooper() == mHandler.getLooper()) {
            r.run();
        } else {
            mHandler.post(r);
        }
    }

    private void animateTranslation(final View v, final float targetX, final float targetY, final int duration) {
        animateTranslation(v, targetX, targetY, duration, null);
    }

    private void animateTranslation(final View v, final float targetX, final float targetY, final int duration, final AnimatorListener al) {
        ViewPropertyAnimator vpa = v.animate();
        if (al != null) {
            vpa.setListener(al);
        }
        vpa.setDuration(Math.min(duration, ANIMATION_MAX_DURATION)).translationX(targetX);
        vpa.setDuration(Math.min(duration, ANIMATION_MAX_DURATION)).translationY(targetY);
    }

    public int getNotificationCount() {
        return mNotifications.size();
    }

    public boolean containsNotification(StatusBarNotification sbn) {
        return mNotifications.containsKey(describeNotification(sbn));
    }

    private String describeNotification(StatusBarNotification sbn) {
        return sbn.getPackageName() + sbn.getId();
    }

    private int getDurationFromDistance (View v, int targetX, int targetY) {
        return getDurationFromDistance (v, targetX, targetY, Math.round(PPS / 1000f));
    }

    private int getDurationFromDistance (View v, int targetX, int targetY, float ppms) {
        int distance = 0;
        float x = v.getX();
        float y = v.getY();
        if (targetY == y) distance = Math.abs(Math.round(x) - targetX);
        else if (targetX == x) distance = Math.abs(Math.round(y - targetY));
        else distance = (int) Math.abs(Math.round(Math.sqrt((x - targetX)*(x * targetX)+(y - targetY)*(y - targetY))));
        return Math.round(distance / ppms);
    }

    public void bringToFront() {
        mNotifView.bringToFront();
        super.bringToFront();
    }

    public void setVisibility (int v) {
        super.setVisibility(v);
        bringToFront();
    }
}
