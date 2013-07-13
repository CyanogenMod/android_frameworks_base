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
import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
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

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends FrameLayout implements Ticker.TickerCallback {

    public static final String TAG = "HaloLauncher";

    enum State {
        IDLE,
        HIDDEN,
        SILENT,
        DRAG,
        GESTURES
    }

    enum Gesture {
        NONE,
        TASK,
        UP1,
        UP2,
        DOWN1,
        DOWN2
    }

    private Context mContext;
    private PackageManager mPm;
    private Handler mHandler;
    private BaseStatusBar mBar;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;
    private INotificationManager mNotificationManager;
    private SettingsObserver mSettingsObserver;
    private GestureDetector mGestureDetector;
    private KeyguardManager mKeyguardManager;

    private HaloEffect mEffect;
    private WindowManager.LayoutParams mTriggerPos;
    private State mState = State.IDLE;
    private Gesture mGesture = Gesture.NONE;

    private View mRoot;
    private View mContent, mHaloContent;
    private NotificationData.Entry mLastNotificationEntry = null;
    private NotificationData.Entry mCurrentNotficationEntry = null;
    private NotificationClicker mContentIntent, mTaskIntent;
    private NotificationData mNotificationData;
    private String mNotificationText = "";

    private Paint mPaintHoloBlue = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintHoloRed = new Paint();

    private boolean mAttached = false;
    private boolean isBeingDragged = false;
    private boolean mHapticFeedback;
    private boolean mHideTicker;
    private boolean mFirstStart = true;
    private boolean mInitialized = false;
    private boolean mTickerLeft = true;
    private boolean mIsNotificationNew = true;
    private boolean mOverX = false;
    private boolean mInteractionReversed = true;
    private boolean hiddenState = false;

    private int mIconSize, mIconHalfSize;
    private int mScreenWidth, mScreenHeight;
    private int mKillX, mKillY;
    private int mMarkerIndex = -1;

    private int oldIconIndex = -1;
    private float initialX = 0;
    private float initialY = 0;
    
    // Halo dock position
    SharedPreferences preferences;
    private String KEY_HALO_POSITION_Y = "halo_position_y";
    private String KEY_HALO_POSITION_X = "halo_position_x";


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
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
            mSettingsObserver.onChange(true);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
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
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

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
        
        // Halo dock position
        preferences = mContext.getSharedPreferences("Halo", 0);
        int msavePositionX = preferences.getInt(KEY_HALO_POSITION_X, 0);
        int msavePositionY = preferences.getInt(KEY_HALO_POSITION_Y, mScreenHeight / 2 - mIconHalfSize);
        
        mKillX = mScreenWidth / 2;
        mKillY = mIconHalfSize;

        if (!mFirstStart) {
            if (msavePositionY < 0) mEffect.setHaloY(0);
            float mTmpHaloY = (float) msavePositionY / mScreenWidth * (mScreenHeight);
            if (msavePositionY > mScreenHeight-mIconSize) {
                mEffect.setHaloY((int)mTmpHaloY);
            } else {
                mEffect.setHaloY(isLandscapeMod() ? msavePositionY : (int)mTmpHaloY);
            }
 
            if (mState == State.HIDDEN || mState == State.SILENT) {
                mEffect.setHaloX((int)(mTickerLeft ? -mIconSize*0.8f : mScreenWidth - mIconSize*0.2f));
                final int triggerWidth = (int)(mTickerLeft ? -mIconSize*0.7f : mScreenWidth - mIconSize*0.3f);
                updateTriggerPosition(triggerWidth, mEffect.mHaloY);
            } else {
                mEffect.nap(500);
                if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
            }
        } else {
            // Do the startup animations only once
            mFirstStart = false;
            // Halo dock position
            mTickerLeft = msavePositionX == 0 ? true : false;
            updateTriggerPosition(msavePositionX, msavePositionY);
            mEffect.mHaloTextViewL.setVisibility(mTickerLeft ? View.VISIBLE : View.GONE);
            mEffect.mHaloTextViewR.setVisibility(mTickerLeft ? View.GONE : View.VISIBLE);
            mEffect.setHaloX(msavePositionX);
            mEffect.setHaloY(msavePositionY);
            mEffect.nap(500);
            if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
        }
    }
    
    private boolean isLandscapeMod() {
        return mScreenWidth < mScreenHeight;
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
                        mLastNotificationEntry.notification == mCurrentNotficationEntry.notification) {
                    boolean cancel = (mLastNotificationEntry.notification.notification.flags &
                            Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL;
                    if (cancel) mLastNotificationEntry = mNotificationData.get(mNotificationData.size() - 2);
                } else if (mNotificationData.size() == 1) {
                    boolean cancel = (mLastNotificationEntry.notification.notification.flags &
                            Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL;
                    if (cancel) {
                        // We have one notification left and it is dismissable, clear it...
                        clearTicker();
                        return;
                    }
                }
            }

            if (mLastNotificationEntry.notification != null
                    && mLastNotificationEntry.notification.notification != null
                    && mLastNotificationEntry.notification.notification.tickerText != null) {
                mNotificationText = mLastNotificationEntry.notification.notification.tickerText.toString();
            }

            tick(mLastNotificationEntry, "", 0, 0);
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

        // Do not launch tasks in hidden state or protected lock screen
        if (mState == State.HIDDEN || mState == State.SILENT
                || (mKeyguardManager.isKeyguardLocked() && mKeyguardManager.isKeyguardSecure())) return;

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

    private boolean verticalGesture() {
        return (mGesture == Gesture.UP1
                || mGesture == Gesture.DOWN1
                || mGesture == Gesture.UP2
                || mGesture == Gesture.DOWN2);
    }

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
                hiddenState = (mState == State.HIDDEN || mState == State.SILENT);
                if (hiddenState) {
                    mEffect.wake();
                    if (mHideTicker) {
                        mEffect.sleep(2500, HaloEffect.SLEEP_TIME, false);
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
                
                // Halo dock position
                float mTmpHaloY = (float) mEffect.mHaloY / mScreenHeight * mScreenWidth;
                preferences.edit().putInt(KEY_HALO_POSITION_X, mTickerLeft ?
                        0 : mScreenWidth - mIconSize).putInt(KEY_HALO_POSITION_Y, isLandscapeMod() ?
                        mEffect.mHaloY : (int)mTmpHaloY).apply();
                    
                if (mGesture == Gesture.TASK) {
                    // Launch tasks
                    if (mTaskIntent != null) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        launchTask(mTaskIntent);
                    }
                    mEffect.nap(0);
                    if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 1500, HaloEffect.SLEEP_TIME, false);

                } else if (mGesture == Gesture.DOWN2) {
                    // Hide & silence
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, true);

                } else if (mGesture == Gesture.DOWN1) {
                    // Hide from sight
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, false);

                } else if (mGesture == Gesture.UP2) {
                    // Clear all notifications
                    playSoundEffect(SoundEffectConstants.CLICK);

                    try {
                        mBar.getService().onClearAllNotifications();
                    } catch (RemoteException ex) {
                        // system process is dead if we're here.
                    }
                    
                    mCurrentNotficationEntry = null;
                    if (mNotificationData.size() > 0) {
                        if (mNotificationData.size() > 0) {
                            for (int i = mNotificationData.size() - 1; i >= 0; i--) {
                                NotificationData.Entry item = mNotificationData.get(i);
                                if (!((item.notification.notification.flags &
                                        Notification.FLAG_AUTO_CANCEL) == Notification.FLAG_AUTO_CANCEL)) {
                                    tick(item, "", 0, 0);
                                    break;
                                }
                            }
                        }
                    }

                    if (mCurrentNotficationEntry == null) clearTicker();
                    mLastNotificationEntry = null;
                } else if (mGesture == Gesture.UP1) {
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
                            boolean cancel = (item.notification.notification.flags &
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
                        tick(entry, "", 0, 0);
                    }

                    mEffect.nap(1500);
                    if (mHideTicker) mEffect.sleep(HaloEffect.NAP_TIME + 3000, HaloEffect.SLEEP_TIME, false);
                } else {
                    // No gesture, just snap HALO
                    mEffect.snap(0);
                    mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                    if (mHideTicker) mEffect.sleep(HaloEffect.SNAP_TIME + HaloEffect.NAP_TIME + 2500, HaloEffect.SLEEP_TIME, false);
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
                            mEffect.mHaloTextViewL.setVisibility(mTickerLeft ? View.VISIBLE : View.GONE);
                            mEffect.mHaloTextViewR.setVisibility(mTickerLeft ? View.GONE : View.VISIBLE);
                        }
                    }
                } else {
                    // We have three basic gestures, one horizontal for switching through tasks and
                    // two vertical for dismissing tasks or making HALO fall asleep

                    int deltaX = (int)(mTickerLeft ? event.getRawX() : mScreenWidth - event.getRawX());
                    int deltaY = (int)(mEffect.getHaloY() - event.getRawY() + mIconSize);
                    int horizontalThreshold = (int)(mIconSize * 1.5f);
                    int verticalThreshold = mIconHalfSize;
                    int verticalSteps = (int)(mIconSize * 0.6f);
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

                    // Up & down gestures
                    } else if (Math.abs(deltaY) > verticalThreshold) {
                        mMarkerIndex = -1;

                        boolean gestureChanged = false;
                        final int deltaIndex = (Math.abs(deltaY) - verticalThreshold) / verticalSteps;
                        if (deltaY > 0) {                           
                            if (deltaIndex < 2 && mGesture != Gesture.UP1) {
                                mGesture = Gesture.UP1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.DISMISS, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_dismiss);
                            } else if (deltaIndex > 1 && mGesture != Gesture.UP2) {
                                mGesture = Gesture.UP2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.CLEAR_ALL, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_clear_all);
                            }

                        } else {
                            if (deltaIndex < 2 && mGesture != Gesture.DOWN1) {
                                mGesture = Gesture.DOWN1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(mTickerLeft ? HaloProperties.Overlay.BACK_LEFT
                                        : HaloProperties.Overlay.BACK_RIGHT, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_hide);
                            } else if (deltaIndex > 1 && mGesture != Gesture.DOWN2) {
                                mGesture = Gesture.DOWN2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.SILENCE, 1f);
                                gestureText = mContext.getResources().getString(R.string.halo_silence);
                            }
                        }

                        if (gestureChanged) {
                            mMarkerIndex = -1;

                            // Tasking hasn't changed, we can tick the message here
                            if (mMarkerIndex == oldIconIndex) {
                                mEffect.ticker(gestureText, 0, 250);
                                mEffect.updateResources();
                                mEffect.invalidate();
                            }
                            if (mHapticFeedback) mVibrator.vibrate(10);
                        }

                    } else {
                        mMarkerIndex = -1;
                        if (mGesture != Gesture.NONE) {
                            mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            if (verticalGesture()) mEffect.killTicker();
                        }
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
                                tick(mLastNotificationEntry, gestureText, 0, 250);

                                // Ping to notify the user we're back where we started
                                mEffect.ping(mPaintHoloBlue, 0);
                            } else {
                                setIcon(mMarkerIndex);

                                NotificationData.Entry entry = mNotificationData.get(mMarkerIndex);
                                String text = "";
                                if (entry.notification.notification.tickerText != null) {
                                    text = entry.notification.notification.tickerText.toString();
                                }
                                tick(entry, text, 0, 250);
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

        private Bitmap mMarker, mMarkerT, mMarkerB;
        private Bitmap mBigRed;
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
            mMarker = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker);
            mMarkerT = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_t);
            mMarkerB = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_b);

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

                    CustomObjectAnimator pingAnimator = new CustomObjectAnimator(mEffect);
                    pingAnimator.animate(ObjectAnimator.ofInt(mPingPaint, "alpha", 200, 0).setDuration(PING_TIME),
                            new DecelerateInterpolator(), new AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    pingRadius = (int)((pingMaxRadius - pingMinRadius) *
                                            animation.getAnimatedFraction()) + pingMinRadius;
                                    invalidate();
                                }});

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
            if (mState == State.HIDDEN || mState == State.SILENT) mState = State.IDLE;
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

        public void sleep(long delay, int speed, final boolean silent) {
            final int newPos = (int)(mTickerLeft ? -mIconSize*0.8f : mScreenWidth - mIconSize*0.2f);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(speed),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                        public void run() {
                            mState = silent ? State.SILENT : State.HIDDEN;
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
            }

            // Content
            state = canvas.save();
            int ch = mHaloTickerContent.getMeasuredHeight();
            int cw = mHaloTickerContent.getMeasuredWidth();
            int y = mHaloY + mIconHalfSize - ch / 2;
            if (y < 0) y = 0;
            int x = mHaloX + mIconSize;
            if (!mTickerLeft) {
                x = mHaloX - cw;
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
                    int pulseY = (int)(mHaloY - mIconSize * 0.1f);
                    int items = mNotificationData.size();
                    int indexLength = (mScreenWidth - mIconSize * 2) / items;

                    for (int i = 0; i < items; i++) {
                        float pulseX = mTickerLeft ? (mIconSize * 1.15f + indexLength * i)
                                : (mScreenWidth - mIconSize * 1.15f - indexLength * i - mMarker.getWidth());
                        boolean markerState = mTickerLeft ? mMarkerIndex >= 0 && i < items-mMarkerIndex : i <= mMarkerIndex;
                        mMarkerPaint.setAlpha(markerState ? 255 : 100);
                        canvas.drawBitmap(mMarker, pulseX, pulseY, mMarkerPaint);
                    }
                }
            }

            // Vertical Markers
            if (verticalGesture()) {
                int xPos = mHaloX + mIconHalfSize - mMarkerT.getWidth() / 2;
                                
                mMarkerPaint.setAlpha(mGesture == Gesture.UP1 ? 255 : 100);
                int yTop = (int)(mHaloY + mIconHalfSize - mIconSize - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.UP2 ? 255 : 100);
                yTop = yTop - (int)(mIconSize * 0.6f);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.DOWN1 ? 255 : 100);
                int yButtom = (int)(mHaloY + mIconHalfSize + mIconSize - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == Gesture.DOWN2 ? 255 : 100);
                yButtom = yButtom + (int)(mIconSize * 0.6f);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);
            }


            // Bubble
            state = canvas.save();
            canvas.translate(mHaloX, mHaloY);
            mHaloBubble.draw(canvas);
            canvas.restoreToCount(state);

            // Number
            if (mState == State.IDLE || mState == State.GESTURES && !verticalGesture()) {
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

    void tick(NotificationData.Entry entry, String text, int delay, int duration) {
        if (entry == null) {
            clearTicker();
            return;
        }

        StatusBarNotification notification = entry.notification;
        Notification n = notification.notification;

        // Deal with the intent
        mContentIntent = entry.getFloatingIntent();
        mCurrentNotficationEntry = entry;

        // set the avatar
        mEffect.mHaloIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(), entry.getRoundIcon()));

        // Set Number
        if (n.number > 0) {
            mEffect.mHaloNumber.setText((n.number < 100) ? String.valueOf(n.number) : "+");
            mEffect.mHaloNumber.setAlpha(1f);
        } else {
            mEffect.mHaloNumber.setAlpha(0f);
        }

        // Set text
        if (mState != State.SILENT) mEffect.ticker(text, delay, duration);

        mEffect.updateResources();
        mEffect.invalidate();
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {

        boolean allowed = false; // default off
        try {
            allowed = mNotificationManager.isPackageAllowedForHalo(notification.pkg);
        } catch (android.os.RemoteException ex) {
            // System is dead
        }

        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);

            if (entry.notification == notification) {

                // No intent, no tick ...
                if (entry.notification.notification.contentIntent == null) return;

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
                        tick(entry, text, HaloEffect.WAKE_TIME, 1000);

                        // Pop while not tasking, only if notification is certified fresh
                        if (mGesture != Gesture.TASK && mState != State.SILENT) mEffect.ping(mPaintHoloBlue, HaloEffect.WAKE_TIME);

                        if (mState == State.IDLE || mState == State.HIDDEN) {
                            mEffect.wake();
                            mEffect.nap(HaloEffect.NAP_DELAY);
                            if (mHideTicker) mEffect.sleep(HaloEffect.SLEEP_DELAY, HaloEffect.SLEEP_TIME, false);
                        }
                    }
                }
                break;
            }
        }
    }
}
