/*
 * Copyright (C) 2013 ParanoidAndroid.
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

package com.android.systemui.statusbar.halo;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.INotificationManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.RemoteException;
import android.os.Vibrator;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.animation.TimeInterpolator;
import android.view.Display;
import android.view.View;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.SoundEffectConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import android.service.notification.StatusBarNotification;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends FrameLayout implements Ticker.TickerCallback {

    public static final String TAG = "HaloLauncher";

    private static final boolean DEBUG = true;

    enum State {
        IDLE,
        HIDDEN,
        DRAG,
        GESTURES
    }

    enum Gesture {
        NONE,
        TASK,
        UP,
        DOWN
    }

    private State mState = State.IDLE;
    private Gesture mGesture = Gesture.NONE;

    private WindowManager.LayoutParams mTriggerPos;
    private HaloEffect mEffect;
    private boolean mHideTicker;
    private INotificationManager mNotificationManager;

	private int id;
    private String appName;

    private Context mContext;
    private PackageManager mPm;
    private Handler mHandler;
    private BaseStatusBar mBar;
    private WindowManager mWindowManager;
    private View mRoot;
    private int mIconSize, mIconHalfSize;
    
    private boolean isBeingDragged = false;
    private boolean mHapticFeedback;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;

    private Display mDisplay;
    private View mContent, mHaloContent;
    private NotificationData.Entry mLastNotificationEntry = null;
    private NotificationData.Entry mCurrentNotficationEntry = null;
    private NotificationClicker mContentIntent, mTaskIntent;
    private NotificationData mNotificationData;
    private String mNotificationText = "";
    private GestureDetector mGestureDetector;

    private Paint mPaintHoloBlue = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintHoloRed = new Paint();

	public boolean mExpanded = false;
    public boolean mSnapped = true;

    public boolean mFirstStart = true;
    private boolean mInitialized = false;
    private boolean mTickerLeft = true;
    private boolean mIsNotificationNew = true;
    private boolean mOverX = false;

    private boolean mInteractionReversed = true;

    private int mScreenWidth, mScreenHeight;

    private int mKillX, mKillY;
    private int mMarkerIndex = -1;

    private SettingsObserver mSettingsObserver;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_REVERSED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_HIDE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HAPTIC_FEEDBACK_ENABLED), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mInteractionReversed =
                    Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_REVERSED, 1) == 1;
            mHideTicker =
                    Settings.System.getInt(mContext.getContentResolver(), Settings.System.HALO_HIDE, 0) == 1;
            mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;

            if (!selfChange) {
                mEffect.wake();
                mEffect.ping(mPaintHoloBlue, HaloEffect.WAKE_TIME);
                mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME);
            }
        }
    }

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mNotificationManager = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mDisplay = mWindowManager.getDefaultDisplay();
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
        mHandler = new Handler();
        mRoot = this;

        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        mSettingsObserver.onChange(true);

        // Init variables
        BitmapDrawable bd = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.halo_bg);
        mIconSize = bd.getBitmap().getWidth();
        mIconHalfSize = mIconSize / 2;

        mTriggerPos = getWMParams();

        // Init colors
        mPaintHoloBlue.setAntiAlias(true);
        mPaintHoloBlue.setColor(0xff33b5e5);
        mPaintWhite.setAntiAlias(true);
        mPaintWhite.setColor(0xfff0f0f0);
        mPaintHoloRed.setAntiAlias(true);
        mPaintHoloRed.setColor(0xffcc0000);

        // Create effect layer
        mEffect = new HaloEffect(mContext);
        mEffect.setLayerType (View.LAYER_TYPE_HARDWARE, null);
        mEffect.pingMinRadius = mIconHalfSize;
        mEffect.pingMaxRadius = (int)(mIconSize * 1.1f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        mWindowManager.addView(mEffect, lp);
    }

    private void initControl() {
        if (mInitialized) return;

        mInitialized = true;

        // Get actual screen size
        mScreenWidth = mEffect.getWidth();
        mScreenHeight = mEffect.getHeight();

        mKillX = mScreenWidth / 2;
        mKillY = mIconHalfSize;

        if (!mFirstStart) {
            if (mEffect.getHaloY() < 0) mEffect.setHaloY(0);
            if (mEffect.getHaloY() > mScreenHeight-mIconSize) mEffect.setHaloY(mScreenHeight-mIconSize);
            mEffect.nap(500);
            if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME);
        } else {
            // Do the startup animations only once
            mFirstStart = false;
            updateTriggerPosition(0, mScreenHeight / 2 - mIconHalfSize);
            mEffect.setHaloX(0);
            mEffect.setHaloY(mScreenHeight / 2 - mIconHalfSize);
            mEffect.nap(500);
            if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME);
        }
    }    

    private void updateTriggerPosition(int x, int y) {
        try {
            mTriggerPos.x = x;
            mTriggerPos.y = y;
            mWindowManager.updateViewLayout(mRoot, mTriggerPos);
        } catch(Exception e) {
            // Probably some animation still looking to move stuff around
        }
    }

    private void loadLastNotification(boolean includeCurrentDismissable) {
        if (mNotificationData.size() > 0) {
            //oldEntry = mLastNotificationEntry;
            mLastNotificationEntry = mNotificationData.get(mNotificationData.size() - 1);

            // If the current notification is dismissable we might want to skip it if so desired
            if (!includeCurrentDismissable) {
                if (mNotificationData.size() > 1 && mLastNotificationEntry != null &&
                        mCurrentNotficationEntry != null &&
                        mLastNotificationEntry.notification == mCurrentNotficationEntry.notification) {
                    boolean cancel = (mLastNotificationEntry.notification.getNotification().flags &
                            Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL;
                    if (cancel) mLastNotificationEntry = mNotificationData.get(mNotificationData.size() - 2);
                } else if (mNotificationData.size() == 1) {
                    boolean cancel = (mLastNotificationEntry.notification.getNotification().flags &
                            Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL;
                    if (cancel) {
                        // We have one notification left and it is dismissable, clear it...
                        clearTicker();
                        return;
                    }
                }
            }

            if (mLastNotificationEntry.notification != null
                    && mLastNotificationEntry.notification.getNotification() != null
                    && mLastNotificationEntry.notification.getNotification().tickerText != null) {
                mNotificationText = mLastNotificationEntry.notification.getNotification().tickerText.toString();
            }

            tick(mLastNotificationEntry, "", 0, 0, false);
        } else {
            clearTicker();
        }
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
        if (mBar.getTicker() != null) mBar.getTicker().setUpdateEvent(this);
        mNotificationData = mBar.getNotificationData();
        loadLastNotification(true);
    }

    void launchTask(NotificationClicker intent) {

        // Do not launch tasks in hidden state
        if (mState == State.HIDDEN) return;

        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ...
        }

        if (intent!= null) {
            intent.onClick(mRoot);
        }
    }

    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        
        @Override
        public boolean onSingleTapUp (MotionEvent event) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, 
                float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (mState != State.DRAG) {
                launchTask(mContentIntent);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (!mInteractionReversed) {
                mState = State.GESTURES;
                mEffect.wake();
                mBar.setHaloTaskerActive(true, true);
            } else {
                // Move
                mState = State.DRAG;
                mEffect.intro();
            }
            return true;
        }
    }

    void resetIcons() {
        final float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            entry.icon.setAlpha(originalAlpha);
        }
    }

    void setIcon(int index) {
        float originalAlpha = mContext.getResources().getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            entry.icon.setAlpha(index == i ? 1f : originalAlpha);
        }
    }

    private float initialX = 0;
    private float initialY = 0;        
    private int oldIconIndex = -1;
    private boolean hiddenState = false;

		@Override
		public boolean onTouchEvent(MotionEvent event) {
            mGestureDetector.onTouchEvent(event);

            final int action = event.getAction();
            switch(action) {
                case MotionEvent.ACTION_DOWN:

                    // Stop HALO from moving around, unschedule sleeping patterns
                    if (mState != State.GESTURES) mEffect.unscheduleSleep();

                    mMarkerIndex = -1;
                    oldIconIndex = -1;
                    mGesture = Gesture.NONE;
                    hiddenState = mState == State.HIDDEN;
                    if (mState == State.HIDDEN) {
                        mEffect.wake();
                        if (mHideTicker) {
                            mEffect.sleep(2500, HaloEffect.SLEEP_TIME);
                        } else {
                            mEffect.nap(2500);
                        }
                        return true;
                    }

                    initialX = event.getRawX();
                    initialY = event.getRawY(); 
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (hiddenState) break;

                    resetIcons();
                    mBar.setHaloTaskerActive(false, true);
                    mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                    updateTriggerPosition(mEffect.getHaloX(), mEffect.getHaloY());

                    mEffect.outro();
                    mEffect.killTicker();
                    mEffect.unscheduleSleep();

                    // Do we erase ourselves?
                    if (mOverX) {
                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.HALO_ACTIVE, 0);
                        return true;
                    }
                    
                    if (mGesture == Gesture.TASK) {
                        // Launch tasks
                        if (mTaskIntent != null) {
                            playSoundEffect(SoundEffectConstants.CLICK);
                            launchTask(mTaskIntent);
                        }
                        mEffect.nap(0);
                        if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 1500, HaloEffect.SLEEP_TIME);
                    } else if (mGesture == Gesture.DOWN) {
                        // Hide from sight
                        playSoundEffect(SoundEffectConstants.CLICK);
                        mEffect.sleep(0, HaloEffect.NAP_TIME / 2);
                    } else if (mGesture == Gesture.UP) {
                        // Dismiss notification
                        playSoundEffect(SoundEffectConstants.CLICK);

                        if (mContentIntent != null) {
                            try {
                                mBar.getService().onNotificationClear(mContentIntent.mPkg, mContentIntent.mTag, mContentIntent.mId);
                            } catch (RemoteException ex) {
                                // system process is dead if we're here.
                            }
                        }

                        // Find next entry
                        NotificationData.Entry entry = null;
                    	if (mNotificationData.size() > 0) {
                            for (int i = mNotificationData.size() - 1; i >= 0; i--) {
                            	NotificationData.Entry item = mNotificationData.get(i);
                            	if (mCurrentNotficationEntry != null
                            	        && mCurrentNotficationEntry.notification == item.notification) {
                            	    continue;
                            	}
                            	boolean cancel = (item.notification.getNotification().flags &
                            	        Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL;
                            	if (cancel) {
                            	    entry = item;
                            	    break;
                            	}
                            }
                    	}

                        // When no entry was found, take the last one
                        if (entry == null && mNotificationData.size() > 0) {
                            loadLastNotification(false);
                        } else {
                            tick(entry, "", 0, 0, false);
                        }

                        mEffect.nap(1500);
                        if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 3000, HaloEffect.SLEEP_TIME);
                    } else {
                        // No gesture, just snap HALO
                        mEffect.snap(0);
                        mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                        if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME);
                    }

                    mState = State.IDLE;
                    mGesture = Gesture.NONE;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (hiddenState) break;
               
                    float distanceX = mKillX-event.getRawX();
                    float distanceY = mKillY-event.getRawY();
                    float distanceToKill = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                    distanceX = initialX-event.getRawX();
                    distanceY = initialY-event.getRawY();
                    float initialDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

                    if (mState != State.GESTURES) {
                        // Check kill radius
                        if (distanceToKill < mIconSize) {
                            // Magnetize X
                            mEffect.setHaloX((int)mKillX - mIconHalfSize);
                            mEffect.setHaloY((int)(mKillY - mIconHalfSize));
                            
                            if (!mOverX) {
                                if (mHapticFeedback) mVibrator.vibrate(25);
                                mEffect.ping(mPaintHoloRed, 0);
                                mEffect.setHaloOverlay(HaloProperties.Overlay.BLACK_X, 1f);
                                mOverX = true;
                            }

                            return false;
                        } else {
                            if (mOverX) mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            mOverX = false;
                        }

                        // Drag
                        if (mState != State.DRAG) {
                            if (initialDistance > mIconSize * 0.7f) {
                                if (mInteractionReversed) {
                                    mState = State.GESTURES;
                                    mEffect.wake();
                                    mBar.setHaloTaskerActive(true, true);
                                } else {
                                    mState = State.DRAG;
                                    mEffect.intro();
                                    if (mHapticFeedback) mVibrator.vibrate(25);
                                }
                            }
                        } else {
                            int posX = (int)event.getRawX() - mIconHalfSize;
                            int posY = (int)event.getRawY() - mIconHalfSize;
                            if (posX < 0) posX = 0;
                            if (posY < 0) posY = 0;
                            if (posX > mScreenWidth-mIconSize) posX = mScreenWidth-mIconSize;
                            if (posY > mScreenHeight-mIconSize) posY = mScreenHeight-mIconSize;
                            mEffect.setHaloX(posX);
                            mEffect.setHaloY(posY);

                            // Update resources when the side changes
                            boolean oldTickerPos = mTickerLeft;
                            mTickerLeft = (posX + mIconHalfSize < mScreenWidth / 2);
                            if (oldTickerPos != mTickerLeft) {
                                mEffect.updateResources();
                            }
                        }
                    } else {
                        // We have three basic gestures, one horizontal for switching through tasks and
                        // two vertical for dismissing tasks or making HALO fall asleep

                        int deltaX = (int)(mTickerLeft ? event.getRawX() : mScreenWidth - event.getRawX());
                        int deltaY = (int)(mEffect.getHaloY() - event.getRawY() + mIconSize);
                        int horizontalThreshold = (int)(mIconSize * 1.5f);
                        int verticalThreshold = mIconSize;
			String gestureText = mNotificationText;

                        // Switch icons
                        if (deltaX > horizontalThreshold) {
                            if (mGesture != Gesture.TASK) mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);

                            mGesture = Gesture.TASK;

                            deltaX -= horizontalThreshold;
                            if (mNotificationData != null && mNotificationData.size() > 0) {
                                int items = mNotificationData.size();

                                // This will be the lenght we are going to use
                                int indexLength = (mScreenWidth - mIconSize * 2) / items;

                                // Calculate index
                                mMarkerIndex = mTickerLeft ? (items - deltaX / indexLength) - 1 : (deltaX / indexLength);

                                // Watch out for margins!
                                if (mMarkerIndex >= items) mMarkerIndex = items - 1;
                                if (mMarkerIndex < 0) mMarkerIndex = 0;
                            }
                        } else if (Math.abs(deltaY) > verticalThreshold) {
                            mMarkerIndex = -1;

                            // Up & down gestures
                            int newDeltaY = Math.abs(deltaY) - verticalThreshold;
                            boolean gestureChanged = false;

                            if (deltaY > 0) {
                                if (mGesture != Gesture.UP) {
                                    mGesture = Gesture.UP;
                                    gestureChanged = true;
                                    mEffect.setHaloOverlay(HaloProperties.Overlay.DISMISS, 1f);
                                }
                            } else {
                                if (mGesture != Gesture.DOWN) {
                                    mGesture = Gesture.DOWN;
                                    gestureChanged = true;
                                    mEffect.setHaloOverlay(mTickerLeft ? HaloProperties.Overlay.BACK_LEFT
                                            : HaloProperties.Overlay.BACK_RIGHT, 1f);
                                }
                            }

                            if (gestureChanged) {
                                mMarkerIndex = -1;
                                mEffect.killTicker();
                                if (mHapticFeedback) mVibrator.vibrate(10);
                            }

                        } else {
                            mMarkerIndex = -1;
                            if (mGesture != Gesture.NONE) mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            mGesture = Gesture.NONE;
                        }

                        // If the marker index changed, tick
                        if (mMarkerIndex != oldIconIndex) {
                            oldIconIndex = mMarkerIndex;

                            // Make a tiny pop if not so many icons are present
                            if (mHapticFeedback && mNotificationData.size() < 10) mVibrator.vibrate(1);

                            try {
                                if (mMarkerIndex == -1) {
                                    mTaskIntent = null;
                                    resetIcons();
                                    tick(mLastNotificationEntry, gestureText, 0, 250, false);

                                    // Ping to notify the user we're back where we started
                                    mEffect.ping(mPaintHoloBlue, 0);
                                } else {
                                    setIcon(mMarkerIndex);
                                    NotificationData.Entry entry = mNotificationData.get(mMarkerIndex);

                                    ApplicationInfo ai;
                                    try {
                                        ai = mPm.getApplicationInfo( entry.notification.getPackageName(), 0);
                                    } catch (final NameNotFoundException e) {
                                        ai = null;
                                    }
                                    String text = (String) (ai != null ? mPm.getApplicationLabel(ai) : "...");

                                    if (entry.notification.getNotification().tickerText != null) {
                                        text = entry.notification.getNotification().tickerText.toString();
                                    }
                                    tick(entry, text, 0, 250, false);
                                    mTaskIntent = entry.getFloatingIntent();
                                }
                            } catch (Exception e) {
                                // IndexOutOfBoundsException
                            }
                        }
                    }

                    mEffect.invalidate();
                    break;
            }
            return false;
        }

    public void cleanUp() {
        // Remove pending tasks, if we can
        mEffect.unscheduleSleep();
        mHandler.removeCallbacksAndMessages(null);
        // Kill callback
        mBar.getTicker().setUpdateEvent(null);
        // Flag tasker
        mBar.setHaloTaskerActive(false, false);
        // Kill the effect layer
        if (mEffect != null) mWindowManager.removeView(mEffect);
        // Remove resolver
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    class HaloEffect extends HaloProperties {

        public static final int WAKE_TIME = 300;
        public static final int SNAP_TIME = 300;
        public static final int NAP_TIME = 1000;
        public static final int SLEEP_TIME = 2000;
        public static final int PING_TIME = 1500;
        public static final int PULSE_TIME = 1500;
        public static final int TICKER_HIDE_TIME = 2500;

        public static final int NAP_DELAY = 4500;
        public static final int SLEEP_DELAY = 6500;  

        private Context mContext;
        private Paint mPingPaint;
        private int pingRadius = 0;
        private int mPingX, mPingY;
        protected int pingMinRadius = 0;
        protected int pingMaxRadius = 0;
        
        private boolean mPingAllowed = true;

        private Bitmap mMarkerL, mMarkerT, mMarkerR, mMarkerB;
        private Bitmap mBigRed;
        private Bitmap mPulse;
        private Paint mPulsePaint = new Paint();
        private Paint mMarkerPaint = new Paint();
        private Paint xPaint = new Paint();

        CustomObjectAnimator xAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator tickerAnimator = new CustomObjectAnimator(this);

        public HaloEffect(Context context) {
            super(context);

            mContext = context;
            setWillNotDraw(false);
            setDrawingCacheEnabled(false);

            mBigRed = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_bigred);
            mPulse = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_pulse1);
            mMarkerL = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_l);
            mMarkerT = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_t);
            mMarkerR = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_r);
            mMarkerB = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_b);

            mPulsePaint.setAntiAlias(true);
            mPulsePaint.setAlpha(0);   
            mMarkerPaint.setAntiAlias(true);
            mMarkerPaint.setAlpha(0);
            xPaint.setAntiAlias(true);
            xPaint.setAlpha(0);

            updateResources();
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            onConfigurationChanged(null);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            // This will reset the initialization flag
            mInitialized = false;
            // Generate a new content bubble
            updateResources();
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            super.onLayout (changed, left, top, right, bottom);
            // We have our effect-layer, now let's kickstart HALO
            initControl();
        }

        public void killTicker() {
            tickerAnimator.animate(ObjectAnimator.ofFloat(this, "haloContentAlpha", 0f).setDuration(250),
                    new DecelerateInterpolator(), null);
        }

        public void ticker(String tickerText, int delay, int startDuration) {
            if (tickerText == null || tickerText.isEmpty()) {
                killTicker();
                return;
            }

            mHaloTextViewL.setVisibility(mTickerLeft ? View.VISIBLE : View.GONE);
            mHaloTextViewR.setVisibility(mTickerLeft ? View.GONE : View.VISIBLE);

            mHaloTextViewR.setText(tickerText);
            mHaloTextViewL.setText(tickerText);

            float total = TICKER_HIDE_TIME + startDuration + 1000;
            PropertyValuesHolder tickerUpFrames = PropertyValuesHolder.ofKeyframe("haloContentAlpha",
                    Keyframe.ofFloat(0f, mHaloTextViewL.getAlpha()),
                    Keyframe.ofFloat(startDuration / total, 1f),
                    Keyframe.ofFloat((TICKER_HIDE_TIME + startDuration) / total, 1f),
                    Keyframe.ofFloat(1f, 0f));
            tickerAnimator.animate(ObjectAnimator.ofPropertyValuesHolder(this, tickerUpFrames).setDuration((int)total),
                    new DecelerateInterpolator(), null, delay, null);
        }

        public void ping(final Paint paint, final long delay) {
            if ((!mPingAllowed && paint != mPaintHoloRed)
                    && mGesture != Gesture.TASK) return;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mPingAllowed = false;

                    mPingX = mHaloX + mIconHalfSize;
                    mPingY = mHaloY + mIconHalfSize;
;
                    mPingPaint = paint;

                    int c = Color.argb(0xff, Color.red(paint.getColor()), Color.green(paint.getColor()), Color.blue(paint.getColor()));
                    mPulsePaint.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));

                    CustomObjectAnimator pingAnimator = new CustomObjectAnimator(mEffect);
                    pingAnimator.animate(ObjectAnimator.ofInt(mPingPaint, "alpha", 200, 0).setDuration(PING_TIME),
                            new DecelerateInterpolator(), new AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    pingRadius = (int)((pingMaxRadius - pingMinRadius) *
                                            animation.getAnimatedFraction()) + pingMinRadius;
                                    invalidate();
                                }});

                    CustomObjectAnimator pulseAnimator = new CustomObjectAnimator(mEffect);
                    pulseAnimator.animate(ObjectAnimator.ofInt(mPulsePaint, "alpha", 100, 0).setDuration(PULSE_TIME),
                            new AccelerateInterpolator(), null);

                    // prevent ping spam            
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            mPingAllowed = true;
                        }}, PING_TIME / 2);

                }}, delay);
        }

        public void intro() {
            xAnimator.animate(ObjectAnimator.ofInt(xPaint, "alpha", 255).setDuration(PING_TIME / 3),
                    new DecelerateInterpolator(), null);
        }

        public void outro() {
            xAnimator.animate(ObjectAnimator.ofInt(xPaint, "alpha", 0).setDuration(PING_TIME / 3),
                    new AccelerateInterpolator(), null);
        }

        CustomObjectAnimator snapAnimator = new CustomObjectAnimator(this);

        public void wake() {
            unscheduleSleep();
            if (mState == State.HIDDEN) mState = State.IDLE;
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(WAKE_TIME),
                    new DecelerateInterpolator(), null);
        }

        public void snap(long delay) {
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(SNAP_TIME),
                    new DecelerateInterpolator(), null, delay, null);
        }

        public void nap(long delay) {
            final int newPos = mTickerLeft ? -mIconHalfSize : mScreenWidth - mIconHalfSize;
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(NAP_TIME),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                        public void run() {
                            updateTriggerPosition(newPos, mHaloY);
                        }});
        }

        public void sleep(long delay, int speed) {
            final int newPos = (int)(mTickerLeft ? -mIconSize*0.8f : mScreenWidth - mIconSize*0.2f);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(speed),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                        public void run() {
                            mState = State.HIDDEN;
                            final int triggerWidth = (int)(mTickerLeft ? -mIconSize*0.7f : mScreenWidth - mIconSize*0.3f);
                            updateTriggerPosition(triggerWidth, mHaloY);
                        }});
        }

        public void unscheduleSleep() {
            snapAnimator.cancel(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            // Ping
            if (mPingPaint != null) {
                canvas.drawCircle(mPingX, mPingY, pingRadius, mPingPaint);
                int w = mPulse.getWidth() + (int)(mIconSize * 1.2f * mPulsePaint.getAlpha() / 100);
                Rect r = new Rect(mPingX - w / 2, mPingY - w / 2, mPingX + w / 2, mPingY + w / 2);
                canvas.drawBitmap(mPulse, null, r, mPulsePaint);
            }

            // Content
            state = canvas.save();

            int y = mHaloY - mHaloTickerContent.getMeasuredHeight() + (int)(mIconSize * 0.2);
            if (y < 0) y = 0;

            int x = mHaloX + (int)(mIconSize * 0.92f);
            int c = mHaloTickerContent.getMeasuredWidth();
            if (x > mScreenWidth - c) {
                x = mScreenWidth - c;
                if (mHaloX > mScreenWidth - (int)(mIconSize * 1.5f) ) {
                    x = mHaloX - c + (int)(mIconSize * 0.08f);
                }
            }

            state = canvas.save();
            canvas.translate(x, y);
            mHaloContentView.draw(canvas);
            canvas.restoreToCount(state);

            // X
            float fraction = 1 - ((float)xPaint.getAlpha()) / 255;
            int killyPos = (int)(mKillY - mBigRed.getWidth() / 2 - mIconSize * fraction);
            canvas.drawBitmap(mBigRed, mKillX - mBigRed.getWidth() / 2, killyPos, xPaint);

            // Horizontal Marker
            if (mGesture == Gesture.TASK) {
                if (y > 0 && mNotificationData != null && mNotificationData.size() > 0) {
                    int pulseY = mHaloY + mIconHalfSize - mMarkerR.getHeight() / 2;
                    int items = mNotificationData.size();
                    int indexLength = (mScreenWidth - mIconSize * 2) / items;

                    for (int i = 0; i < items; i++) {
                        float pulseX = mTickerLeft ? (mIconSize * 1.5f + indexLength * i)
                                : (mScreenWidth - mIconSize * 1.5f - indexLength * i - mMarkerR.getWidth());
                        boolean markerState = mTickerLeft ? mMarkerIndex >= 0 && i < items-mMarkerIndex : i <= mMarkerIndex;
                        mMarkerPaint.setAlpha(markerState ? 255 : 100);
                        canvas.drawBitmap(mTickerLeft ? mMarkerR : mMarkerL, pulseX, pulseY, mMarkerPaint);
                    }
                }
            }

            // Vertical Marker
            if (mGesture == Gesture.UP || mGesture == Gesture.DOWN) {
                int xPos = mHaloX + mIconHalfSize - mMarkerT.getWidth() / 2;
                int yTop = (int)(mHaloY + mIconHalfSize - mIconSize * 1.2f - mMarkerT.getHeight() / 2);
                int yButtom = (int)(mHaloY + mIconHalfSize + mIconSize * 1.2f - mMarkerT.getHeight() / 2);
                mMarkerPaint.setAlpha(mGesture == Gesture.UP ? 255 : 100);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);
                mMarkerPaint.setAlpha(mGesture == Gesture.DOWN ? 255 : 100);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);
            }


            // Bubble
            state = canvas.save();
            canvas.translate(mHaloX, mHaloY);
            mHaloBubble.draw(canvas);
            canvas.restoreToCount(state);

            // Number
            if (mState == State.IDLE || mState == State.GESTURES && !(mGesture == Gesture.UP || mGesture == Gesture.DOWN)) {
                state = canvas.save();
                canvas.translate(mTickerLeft ? mHaloX + mIconSize - mHaloNumber.getMeasuredWidth() : mHaloX, mHaloY);
                mHaloNumberView.draw(canvas);
                canvas.restoreToCount(state);
            }
        }
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mIconSize,
                mIconSize,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        return lp;
    }

    void clearTicker() {
        mEffect.mHaloIcon.setImageDrawable(null);
        mEffect.mHaloNumber.setAlpha(0f);
        mContentIntent = null;
        mCurrentNotficationEntry = null;
        mEffect.killTicker();
        mEffect.updateResources();
        mEffect.invalidate();
    }

    void tick(NotificationData.Entry entry, String text, int delay, int duration, boolean alwaysFlip) {
        if (entry == null) {
            clearTicker();
            return;
        }

        StatusBarNotification notification = entry.notification;
        Notification n = notification.getNotification();

        // Deal with the intent
        mContentIntent = entry.getFloatingIntent();
        mCurrentNotficationEntry = entry;

        // set the avatar
        mEffect.mHaloIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), entry.getRoundIcon()));

        // Set Number
        if (n.number > 0) {
            mEffect.mHaloNumber.setText((n.number < 100) ? String.valueOf(n.number) : "99+");
            mEffect.mHaloNumber.setAlpha(1f);
        } else {
            mEffect.mHaloNumber.setAlpha(0f);
        }

        // Set text
        mEffect.ticker(text, delay, duration);

        mEffect.updateResources();
        mEffect.invalidate();
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {

        boolean allowed = false; // default off
        try {
            allowed = mNotificationManager.isPackageAllowedForHalo(notification.getPackageName());
        } catch (android.os.RemoteException ex) {
            // System is dead
        }

        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);

            if (entry.notification == notification) {

                // No intent, no tick ...
                if (entry.notification.getNotification().contentIntent == null) return;

                mIsNotificationNew = true;
                if (mLastNotificationEntry != null && notification == mLastNotificationEntry.notification) {
                    // Ok, this is the same notification
                    // Let's give it a chance though, if the text has changed we allow it
                    mIsNotificationNew = !mNotificationText.equals(text);
                }

                if (mIsNotificationNew) {
                    mNotificationText = text;
                    mLastNotificationEntry = entry;

                    if (allowed) {
                        tick(entry, text, HaloEffect.WAKE_TIME * 3, 1000, true);

                        // Pop while not tasking, only if notification is certified fresh
                        if (mGesture != Gesture.TASK) mEffect.ping(mPaintHoloBlue, HaloEffect.WAKE_TIME);
                        if (mState == State.IDLE || mState == State.HIDDEN) {
                            mEffect.wake();
                            mEffect.nap(HaloEffect.NAP_DELAY);
                            if (mHideTicker) mEffect.sleep(HaloEffect.SLEEP_DELAY, HaloEffect.SLEEP_TIME);
                        }
                    }
                }
                break;
            }
        }
    }
}
