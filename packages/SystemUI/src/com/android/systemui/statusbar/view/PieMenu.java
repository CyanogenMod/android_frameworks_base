/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
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

package com.android.systemui.statusbar.view;

import android.app.Notification;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.TimeInterpolator;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.LightingColorFilter;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.service.notification.StatusBarNotification;

import com.android.internal.statusbar.StatusBarIcon;

import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.PieControl;
import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.PiePolicy;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    // Linear
    private static int ANIMATOR_DEC_SPEED15 = 1;
    private static int ANIMATOR_ACC_SPEED15 = 2;

    // Cascade
    private static int ANIMATOR_ACC_INC_1 = ANIMATOR_ACC_SPEED15 + 1;
    private static int ANIMATOR_ACC_INC_15 = ANIMATOR_ACC_INC_1 + 15;

    // Special purpose
    private static int ANIMATOR_BATTERY_METER = ANIMATOR_ACC_INC_15 + 1;
    private static int ANIMATOR_SNAP_GROW = ANIMATOR_ACC_INC_15 + 2;
    private static int ANIMATOR_END = ANIMATOR_SNAP_GROW;

    private static final int COLOR_OUTLINES_MASK = 0x22000000;
    private static final int COLOR_ALPHA_MASK = 0xaa000000;
    private static final int COLOR_OPAQUE_MASK = 0xff000000;
    private static final int COLOR_SNAP_BACKGROUND = 0xffffffff;
    private static final int COLOR_PIE_BACKGROUND = 0xaaff005e;
    private static final int COLOR_PIE_BUTTON = 0xb2ffffff;
    private static final int COLOR_PIE_SELECT = 0xaadbff00;
    private static final int COLOR_PIE_OUTLINES = 0x55ffffff;
    private static final int COLOR_CHEVRON_LEFT = 0x0999cc;
    private static final int COLOR_CHEVRON_RIGHT = 0x33b5e5;
    private static final int COLOR_BATTERY_JUICE = 0x33b5e5;
    private static final int COLOR_BATTERY_JUICE_LOW = 0xffbb33;
    private static final int COLOR_BATTERY_JUICE_CRITICAL = 0xff4444;
    private static final int COLOR_BATTERY_BACKGROUND = 0xffffff;
    private static final int COLOR_STATUS = 0xffffff;
    private static final int BASE_SPEED = 1000;
    private static final int CHEVRON_FRAGMENTS = 16;
    private static final float SIZE_BASE = 1.0f;

    // System
    private Context mContext;
    private Resources mResources;
    private PiePolicy mPolicy;
    private Vibrator mVibrator;

    // Pie handlers
    private PieItem mCurrentItem;
    private List<PieItem> mItems;
    private PieControlPanel mPanel;
    private PieStatusPanel mStatusPanel;

    private int mOverallSpeed = BASE_SPEED;
    private int mPanelDegree;
    private int mPanelOrientation;
    private int mInnerPieRadius;
    private int mOuterPieRadius;
    private int mPieAngle;
    private int mPieGap;
    private int mInnerChevronRadius;
    private int mOuterChevronRadius;
    private int mInnerChevronRightRadius;
    private int mOuterChevronRightRadius;
    private int mInnerBatteryRadius;
    private int mOuterBatteryRadius;
    private int mStatusRadius;
    private int mNotificationsRadius;
    private int mEmptyAngle;

    private Point mCenter = new Point(0, 0);
    private float mCenterDistance = 0;

    private Path mStatusPath = new Path();
    private Path[] mChevronPathLeft  = new Path[CHEVRON_FRAGMENTS+1];
    private Path mChevronPathRight;
    private Path mBatteryPathBackground;
    private Path mBatteryPathJuice;

    private Paint mPieBackground = new Paint(COLOR_PIE_BACKGROUND);
    private Paint mPieSelected = new Paint(COLOR_PIE_SELECT);
    private Paint mPieOutlines = new Paint(COLOR_PIE_OUTLINES);
    private Paint mChevronBackgroundLeft = new Paint(COLOR_CHEVRON_LEFT);
    private Paint mChevronBackgroundRight = new Paint(COLOR_CHEVRON_RIGHT);
    private Paint mBatteryJuice = new Paint(COLOR_BATTERY_JUICE);
    private Paint mBatteryBackground = new Paint(COLOR_BATTERY_BACKGROUND);
    private Paint mSnapBackground = new Paint(COLOR_SNAP_BACKGROUND);

    private Paint mClockPaint;
    private Paint mAmPmPaint;
    private Paint mStatusPaint;
    private Paint mNotificationPaint;

    private String mClockText;
    private String mClockTextAmPm;
    private float mClockTextAmPmSize;
    private float mClockTextTotalOffset = 0;
    private float[] mClockTextOffsets = new float[20];
    private float mClockTextRotation;
    private float mClockOffset;
    private float mAmPmOffset;
    private float mStatusOffset;

    private int mNotificationCount;
    private int mHiddenNotification;
    private float mNotificationsRowSize;
    private int mNotificationIconSize;
    private int mNotificationTextSize;
    private String[] mNotificationText;
    private Bitmap[] mNotificationIcon;
    private Path[] mNotificationPath;

    private float mStartBattery;
    private float mEndBattery;
    private int mBatteryLevel;

    private class SnapPoint {
        public SnapPoint(int snapX, int snapY, int snapRadius, int snapAlpha, int snapGravity) {
            x = snapX;
            y = snapY;
            radius = snapRadius;
            alpha = snapAlpha;
            gravity = snapGravity;
            active = false;
        }

        public int x;
        public int y;
        public int radius;
        public int alpha;
        public int gravity;
        public boolean active;
    }

    private SnapPoint[] mSnapPoint = new SnapPoint[3];
    int mSnapRadius;
    int mSnapThickness;

    // Flags
    private int mStatusMode;
    private float mPieSize = SIZE_BASE;
    private boolean mOpen;
    private boolean mEnableColor;
    private boolean mUseMenuAlways;
    private boolean mUseSearch;
    private boolean mUseAppWindow;
    private boolean mUseActNotif;
    private boolean mUseActQs;
    private boolean mUseLastApp;
    private boolean mUseKillTask;
    private boolean mUseOmniRecent;
    private boolean mUsePower;
    private boolean mUseScreenShot;
    private boolean mUseTorch;
    private boolean mUseGesture;
    private boolean mHapticFeedback;
    private boolean mIsProtected;

    // Animations
    private int mGlowOffsetLeft = 150;
    private int mGlowOffsetRight = 150;

    private class CustomValueAnimator {

        public CustomValueAnimator(int animateIndex) {
            index = animateIndex;
            manual = false;
            animateIn = true;
            animator = ValueAnimator.ofInt(0, 1);
            animator.addUpdateListener(new CustomAnimatorUpdateListener(index));
            fraction = 0;
        }

        public void start() {
            if (!manual) {
                animator.setDuration(duration);
                animator.start();
            }
        }

        public void reverse(int milliSeconds) {
            if (!manual) {
                animator.setDuration(milliSeconds);
                animator.reverse();
            }
        }

        public void cancel() {
            animator.cancel();
            fraction = 0;
        }

        public int index;
        public int duration;
        public boolean manual;
        public boolean animateIn;
        public float fraction;
        public ValueAnimator animator;
    }

    private CustomValueAnimator[] mAnimators = new CustomValueAnimator[ANIMATOR_END + 1];

    private float mX = 0;
    private float mY = 0;

    private void getDimensions() {
        mPanelDegree = mPanel.getDegree();
        mPanelOrientation = mPanel.getOrientation();

        // Fetch modes
        boolean expanded = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE, 0, UserHandle.USER_CURRENT) == 1;
        mUseMenuAlways = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_MENU, 1, UserHandle.USER_CURRENT) == 1;
        mUseSearch = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_SEARCH, 0, UserHandle.USER_CURRENT) == 1;
        mUseLastApp = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_LAST_APP, 0, UserHandle.USER_CURRENT) == 1;
        mUsePower = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_POWER, 0, UserHandle.USER_CURRENT) == 1;
        mUseKillTask = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_KILL_TASK, 0, UserHandle.USER_CURRENT) == 1;
        mUseAppWindow = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_APP_WINDOW, 0, UserHandle.USER_CURRENT) == 1;
        mUseActNotif = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ACT_NOTIF, 0, UserHandle.USER_CURRENT) == 1;
        mUseActQs = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ACT_QS, 0, UserHandle.USER_CURRENT) == 1;
        mUseScreenShot = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_SCREENSHOT, 0, UserHandle.USER_CURRENT) == 1;
        mUseTorch = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_TORCH, 0, UserHandle.USER_CURRENT) == 1;
        mUseGesture = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_GESTURE, 0, UserHandle.USER_CURRENT) == 1;
        mUseOmniRecent = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_OMNISWITCH, 0, UserHandle.USER_CURRENT) == 1;
        mStatusMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_MODE, 0, UserHandle.USER_CURRENT);
        mPieSize = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.PIE_SIZE, 1.0f, UserHandle.USER_CURRENT);
        mPieAngle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ANGLE, 12, UserHandle.USER_CURRENT);
        mPieGap = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_GAP, 2, UserHandle.USER_CURRENT);
        mHapticFeedback = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0;
        mIsProtected = mPanel.getKeyguardStatus();

        // Snap
        mSnapRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_radius) * mPieSize);
        mSnapThickness = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_thickness) * mPieSize);

        Point outSize = new Point(0,0);
        WindowManager windowManager = (WindowManager)mContext
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(outSize);
        int mWidth = outSize.x;
        int mHeight = outSize.y;

        int snapIndex = 0;
        if (mPanelOrientation != Gravity.LEFT)
            mSnapPoint[snapIndex++] = new SnapPoint(0 + mSnapThickness / 2, mHeight / 2,
                    mSnapRadius, 0x22, Gravity.LEFT);
        if (mPanelOrientation != Gravity.TOP)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mSnapThickness / 2,
                    mSnapRadius, 0x22, Gravity.TOP);
        if (mPanelOrientation != Gravity.RIGHT)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth - mSnapThickness / 2, mHeight / 2,
                    mSnapRadius, 0x22, Gravity.RIGHT);
        if (mPanelOrientation != Gravity.BOTTOM)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mHeight - mSnapThickness / 2,
                    mSnapRadius, 0x22, Gravity.BOTTOM);

        // Create Pie
        mEmptyAngle = (int)(mPieAngle * mPieSize);
        mInnerPieRadius = (int)(mResources
                .getDimensionPixelSize(R.dimen.pie_radius_start) * mPieSize);
        mOuterPieRadius = (int)(mInnerPieRadius + mResources
                .getDimensionPixelSize(R.dimen.pie_radius_increment) * mPieSize);

        // Calculate chevrons: 0 - 82 & -4 - 90
        mInnerChevronRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_chevron_start) * mPieSize);
        mOuterChevronRadius = (int)(mInnerChevronRadius + mResources
                .getDimensionPixelSize(R.dimen.pie_chevron_increment) * mPieSize);
        mInnerChevronRightRadius = (int)(mResources
                .getDimensionPixelSize(R.dimen.pie_chevron_start_right) * mPieSize);
        mOuterChevronRightRadius = (int)(mInnerChevronRightRadius + mResources
                .getDimensionPixelSize(R.dimen.pie_chevron_increment_right) * mPieSize);

        // Create slices
        float fragmentSize = 90 / CHEVRON_FRAGMENTS;
        for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
            mChevronPathLeft[i] = makeSlice(mPanelDegree + (i * fragmentSize), mPanelDegree
                    + (i * fragmentSize) + fragmentSize / 2,
                    mInnerChevronRadius, mOuterChevronRadius, mCenter);
        }

        mChevronPathRight = makeSlice(mPanelDegree + (mPanelOrientation != Gravity.TOP ? -5 : 3), mPanelDegree + 90, mInnerChevronRightRadius,
                mOuterChevronRightRadius, mCenter);

        // Calculate text circle
        mStatusRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_status_start) * mPieSize);
        mStatusPath.reset();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mStatusRadius, Path.Direction.CW);

        mClockPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_clock_size) * mPieSize);
        mClockOffset = mResources.getDimensionPixelSize(R.dimen.pie_clock_offset) * mPieSize;
        mAmPmPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_ampm_size) * mPieSize);
        mAmPmOffset = mResources.getDimensionPixelSize(R.dimen.pie_ampm_offset) * mPieSize;

        mStatusPaint.setTextSize((int)(mResources.getDimensionPixelSize(R.dimen.pie_status_size) * mPieSize));
        mStatusOffset = mResources.getDimensionPixelSize(R.dimen.pie_status_offset) * mPieSize;
        mNotificationTextSize = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notification_size) * mPieSize);
        mNotificationPaint.setTextSize(mNotificationTextSize);

        // Battery
        mInnerBatteryRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_battery_start) * mPieSize);
        mOuterBatteryRadius = (int)(mInnerBatteryRadius + mResources.getDimensionPixelSize(R.dimen.pie_battery_increment) * mPieSize);

        mBatteryBackground.setColor(getResources().getColor(R.color.battery_background));
        mBatteryLevel = mPolicy.getBatteryLevel();
        if (mBatteryLevel <= PiePolicy.LOW_BATTERY_LEVEL
                && mBatteryLevel > PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(getResources().getColor(R.color.battery_juice_low));
        } else if (mBatteryLevel <= PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(getResources().getColor(R.color.battery_juice_critical));
        } else {
            mBatteryJuice.setColor(getResources().getColor(R.color.battery_juice));
        }

        mStartBattery = mPanel.getDegree() + mEmptyAngle + mPieGap;
        mEndBattery = mPanel.getDegree() + (mPieGap <= 2 ? 88 : 90 - mPieGap);
        mBatteryPathBackground = makeSlice(mStartBattery, mEndBattery, mInnerBatteryRadius,
                mOuterBatteryRadius, mCenter);
        mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery, mInnerBatteryRadius,
                mOuterBatteryRadius, mCenter);

        // Colors
        mEnableColor = (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_ENABLE_COLOR, 0, UserHandle.USER_CURRENT) == 1);

        mNotificationPaint.setColor(getResources().getColor(R.color.status));
        mSnapBackground.setColor(getResources().getColor(R.color.snap_background));

        if (mEnableColor) {
            mPieBackground.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_BACKGROUND, COLOR_PIE_BACKGROUND, UserHandle.USER_CURRENT) | COLOR_ALPHA_MASK);
            mPieSelected.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_SELECT, COLOR_PIE_SELECT, UserHandle.USER_CURRENT) | COLOR_ALPHA_MASK);
            mPieOutlines.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_OUTLINES, COLOR_PIE_OUTLINES, UserHandle.USER_CURRENT) | COLOR_OUTLINES_MASK);
            mClockPaint.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_STATUS_CLOCK, COLOR_STATUS, UserHandle.USER_CURRENT));
            mAmPmPaint.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_STATUS_CLOCK, COLOR_STATUS, UserHandle.USER_CURRENT));
            mStatusPaint.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_STATUS, COLOR_STATUS, UserHandle.USER_CURRENT));
            mChevronBackgroundLeft.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_CHEVRON_LEFT, COLOR_CHEVRON_LEFT, UserHandle.USER_CURRENT) | COLOR_OPAQUE_MASK);
            mChevronBackgroundRight.setColor(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_CHEVRON_RIGHT, COLOR_CHEVRON_RIGHT, UserHandle.USER_CURRENT) | COLOR_OPAQUE_MASK);
            mBatteryJuice.setColorFilter(new PorterDuffColorFilter(extractRGB(Settings.System
                    .getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_JUICE, COLOR_BATTERY_JUICE, UserHandle.USER_CURRENT)) | COLOR_OPAQUE_MASK,
                        Mode.SRC_ATOP));

            for (PieItem item : mItems) {
                item.setColor(Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON, UserHandle.USER_CURRENT));
            }
        } else {
            mPieBackground.setColor(getResources().getColor(R.color.pie_background));
            mPieSelected.setColor(getResources().getColor(R.color.pie_select));
            mPieOutlines.setColor(getResources().getColor(R.color.pie_outlines));
            mClockPaint.setColor(getResources().getColor(R.color.status));
            mAmPmPaint.setColor(getResources().getColor(R.color.status));
            mStatusPaint.setColor(getResources().getColor(R.color.status));
            mChevronBackgroundLeft.setColor(getResources().getColor(R.color.chevron_left));
            mChevronBackgroundRight.setColor(getResources().getColor(R.color.chevron_right));
            mBatteryJuice.setColorFilter(null);
            for (PieItem item: mItems) {
                item.setColor(getResources().getColor(R.color.status));
            }
        }

        // Notifications
        mNotificationCount = 0;
        mHiddenNotification = 0;
        mNotificationsRadius = (int)(mResources
                .getDimensionPixelSize(R.dimen.pie_notifications_start) * mPieSize);
        mNotificationIconSize = (int)(mResources
                .getDimensionPixelSize(R.dimen.pie_notification_icon_size) * mPieSize);
        mNotificationsRowSize = mResources.getDimensionPixelSize(R.dimen.pie_notification_row_size)
                * mPieSize;

        if (mPanel.getBar() != null) {
            getNotifications();
        }

        // Measure clock
        measureClock(mPolicy.getSimpleTime());

        // Determine animationspeed
        mOverallSpeed = BASE_SPEED / 4;
        int mInitialSpeed = BASE_SPEED * (mStatusMode == -1 ? 0 : mStatusMode) / 2;

        // Create animators
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i] = new CustomValueAnimator(i);
        }

        // Linear animators
        mAnimators[ANIMATOR_DEC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_DEC_SPEED15].animator.setInterpolator(new DecelerateInterpolator());
        mAnimators[ANIMATOR_DEC_SPEED15].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        mAnimators[ANIMATOR_ACC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_ACC_SPEED15].animator.setInterpolator(new AccelerateInterpolator());
        mAnimators[ANIMATOR_ACC_SPEED15].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        // Cascade accelerators
        int count = 0;
        for(int i = ANIMATOR_ACC_INC_1; i < ANIMATOR_ACC_INC_15 + 1; i++) {
            mAnimators[i].duration = 150;
            mAnimators[i].animator.setInterpolator(new DecelerateInterpolator());
            mAnimators[i].animator.setStartDelay((int)(mInitialSpeed * 1.5f + (++count * 75) ));
        }

        // Special purpose
        mAnimators[ANIMATOR_BATTERY_METER].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_BATTERY_METER].animator.setInterpolator(new DecelerateInterpolator());
        mAnimators[ANIMATOR_BATTERY_METER].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        mAnimators[ANIMATOR_SNAP_GROW].manual = true;
        mAnimators[ANIMATOR_SNAP_GROW].animator.setDuration(1000);
        mAnimators[ANIMATOR_SNAP_GROW].animator.setInterpolator(new AccelerateInterpolator());
        mAnimators[ANIMATOR_SNAP_GROW].animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                if (mAnimators[ANIMATOR_SNAP_GROW].fraction == 1) {
                    for (int i = 0; i < 3; i++) {
                        SnapPoint snap = mSnapPoint[i];
                        if (snap.active) {
                            if (mHapticFeedback) mVibrator.vibrate(2);
                            mStatusPanel.hidePanels(true);
                            deselect();
                            animateOut();
                            mPanel.reorient(snap.gravity, true);
                        }
                    }
                }
            }});
    }

    private int extractRGB(int color) {
        return color & 0x00FFFFFF;
    }

    private void measureClock(String text) {
        mClockText = text;

        mClockTextAmPm = mPolicy.getAmPm();
        mClockTextAmPmSize = mAmPmPaint.measureText(mClockTextAmPm);
        mClockTextTotalOffset = 0;

        for( int i = 0; i < mClockText.length(); i++ ) {
            char character = mClockText.charAt(i);
            float measure = mClockPaint.measureText("" + character);
            mClockTextOffsets[i] = measure * (character == '1' || character == ':' ? 0.5f : 0.8f);
            mClockTextTotalOffset += measure * (character == '1' || character == ':' ? 0.6f : 0.9f);
        }

        mClockTextRotation = mPanel.getDegree() + (180 - (mClockTextTotalOffset * 360 /
                (2f * (mStatusRadius+Math.abs(mClockOffset)) * (float)Math.PI))) - 2;
    }

    private void getNotifications() {
        NotificationData notifData = mPanel.getBar().getNotificationData();
        if (notifData != null) {

            mNotificationText = new String[notifData.size()];
            mNotificationIcon = new Bitmap[notifData.size()];
            mNotificationPath = new Path[notifData.size()];

            for (int i = 0; i < notifData.size(); i++ ) {
                NotificationData.Entry entry = notifData.get(i);
                StatusBarNotification statusNotif = entry.notification;
                if (statusNotif == null) continue;
                boolean hide = statusNotif.getPackageName().equals("com.paranoid.halo");
                if (hide) {
                    mHiddenNotification++;
                    continue;
                }
                Notification notif = statusNotif.getNotification();
                if (notif == null)
                    continue;
                CharSequence tickerText = notif.tickerText;
                if (tickerText == null)
                    continue;

                if (entry.icon != null) {
                    StatusBarIconView iconView = entry.icon;
                    StatusBarIcon icon = iconView.getStatusBarIcon();
                    Drawable drawable = entry.icon.getIcon(mContext, icon);
                    if (!(drawable instanceof BitmapDrawable))
                        continue;

                    mNotificationIcon[mNotificationCount] = ((BitmapDrawable)drawable).getBitmap();

                    String text = tickerText.toString();
                    if (text.length() > 100) text = text.substring(0, 100) + "..";
                    mNotificationText[mNotificationCount] = text;

                    Path notifictionPath = new Path();
                    notifictionPath.addCircle(mCenter.x, mCenter.y, mNotificationsRadius +
                            (mNotificationsRowSize * mNotificationCount) + (mNotificationsRowSize-mNotificationTextSize),
                            Path.Direction.CW);
                    mNotificationPath[mNotificationCount] = notifictionPath;

                    mNotificationCount++;
                }
            }
        }
    }

    public PieMenu(Context context, PieControlPanel panel) {
        super(context);

        mContext = context;
        mResources = mContext.getResources();
        mPanel = panel;

        setWillNotDraw(false);
        setDrawingCacheEnabled(false);

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mPolicy = new PiePolicy(mContext);

        // Initialize classes
        mItems = new ArrayList<PieItem>();
        mPieBackground.setAntiAlias(true);
        mPieSelected.setAntiAlias(true);
        mPieOutlines.setAntiAlias(true);
        mPieOutlines.setStyle(Style.STROKE);
        mPieOutlines.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_outline));
        mChevronBackgroundLeft.setAntiAlias(true);
        mChevronBackgroundRight.setAntiAlias(true);
        mBatteryJuice.setAntiAlias(true);
        mBatteryBackground.setAntiAlias(true);
        mSnapBackground.setAntiAlias(true);

        mClockPaint = new Paint();
        mClockPaint.setAntiAlias(true);
        mClockPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mAmPmPaint = new Paint();
        mAmPmPaint.setAntiAlias(true);
        mAmPmPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mStatusPaint = new Paint();
        mStatusPaint.setAntiAlias(true);
        mStatusPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mNotificationPaint = new Paint();
        mNotificationPaint.setAntiAlias(true);
        mNotificationPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        // Clock observer
        mPolicy.setOnClockChangedListener(new PiePolicy.OnClockChangedListener() {
            public void onChange(String s) {
                measureClock(s);
            }
        });

        mPieSettingsObserver.observe();

        // Get all dimensions
        getDimensions();
    }

    private PieSettingsObserver mPieSettingsObserver = new PieSettingsObserver(new Handler());

    public void init() {
        mStatusPanel = new PieStatusPanel(mContext, mPanel);
        getNotifications();
    }

    public void onPieConfigurationChanged() {
        if (mStatusPanel != null) mStatusPanel.updatePanelConfiguration();
    }

    public PieStatusPanel getStatusPanel() {
        return mStatusPanel;
    }

    public void addItem(PieItem item) {
        mItems.add(item);
    }

    public void show(boolean show) {
        mOpen = show;
        if (mOpen) {

            // Get fresh dimensions
            getDimensions();

            // De-select all items
            mCurrentItem = null;
            for (PieItem item : mItems) {
                item.setSelected(false);
            }

            // Calculate pie's
            layoutPie();
        }
        invalidate();
    }

    public void setCenter(int x, int y) {
        mCenter.y = y;
        mCenter.x = x;

        mStatusPath.reset();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mStatusRadius, Path.Direction.CW);
    }

    private boolean canItemDisplay(PieItem item) {
        return !(item.getName().equals(PieControl.MENU_BUTTON) && !mPanel.currentAppUsesMenu()
                        && !mUseMenuAlways) &&
               !(item.getName().equals(PieControl.SEARCH_BUTTON) && !mUseSearch) &&
               !(item.getName().equals(PieControl.ACT_NOTIF_BUTTON) && !mUseActNotif) &&
               !(item.getName().equals(PieControl.ACT_QS_BUTTON) && !mUseActQs) &&
               !(item.getName().equals(PieControl.APP_WINDOW_BUTTON) && !mUseAppWindow) &&
               !(item.getName().equals(PieControl.KILL_TASK_BUTTON) && !mUseKillTask) &&
               !(item.getName().equals(PieControl.LAST_APP_BUTTON) && !mUseLastApp) &&
               !(item.getName().equals(PieControl.POWER_BUTTON) && !mUsePower) &&
               !(item.getName().equals(PieControl.SCREENSHOT_BUTTON) && !mUseScreenShot) &&
               !(item.getName().equals(PieControl.TORCH_BUTTON) && !mUseTorch) &&
               !(item.getName().equals(PieControl.GESTURE_BUTTON) && !mUseGesture);
    }

    private void layoutPie() {
        float emptyangle = mEmptyAngle * (float)Math.PI / 180;
        int inner = mInnerPieRadius;
        int outer = mOuterPieRadius;

        int itemCount = mItems.size();
        if (!mPanel.currentAppUsesMenu() && !mUseMenuAlways) itemCount--;
        if (!mUseSearch)
            itemCount--;
        if (!mUseAppWindow)
            itemCount--;
        if (!mUseActNotif)
            itemCount--;
        if (!mUseActQs)
            itemCount--;
        if (!mUseLastApp)
            itemCount--;
        if (!mUseKillTask)
            itemCount--;
        if (!mUsePower)
            itemCount--;
        if (!mUseScreenShot)
            itemCount--;
        if (!mUseTorch)
            itemCount--;
        if (!mUseGesture)
            itemCount--;

        int lesserSweepCount = 0;
        for (PieItem item : mItems) {
            boolean canDisplay = canItemDisplay(item);
            if (canDisplay) {
                if (item.isLesser()) {
                    lesserSweepCount += 1;
                }
            }
        }

        float adjustedSweep = lesserSweepCount > 0 ? (((1-0.65f) * lesserSweepCount) / (itemCount-lesserSweepCount)) : 0;
        float sweep = 0;
        float angle = 0;
        float total = 0;

        int count = 0;
        for (PieItem item : mItems) {
            if (!canItemDisplay(item))
                continue;

            sweep = ((float) (Math.PI - 2 * emptyangle) / itemCount)
                    * (item.isLesser() ? 0.65f : 1 + adjustedSweep);
            angle = (emptyangle + sweep / 2 - (float)Math.PI/2);
            item.setPath(makeSlice(getDegrees(0) - mPieGap, getDegrees(sweep) + mPieGap, outer,
                    inner, mCenter,
                    (mPieGap > 0 ? mPieGap + 0.4f : 0), count != 0));
            View view = item.getView();

            if (view != null) {
                view.measure(view.getLayoutParams().width, view.getLayoutParams().height);
                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                int r = inner + (outer - inner) * 2 / 3;
                int x = (int) (r * Math.sin(total + angle));
                int y = (int) (r * Math.cos(total + angle));

                switch(mPanelOrientation) {
                    case Gravity.LEFT:
                        y = mCenter.y - (int) (r * Math.sin(total + angle)) - h / 2;
                        x = (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.RIGHT:
                        y = mCenter.y - (int) (Math.PI/2-r * Math.sin(total + angle)) - h / 2;
                        x = mCenter.x - (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.TOP:
                        y = y - h / 2;
                        x = mCenter.x - (int)(Math.PI/2-x) - w / 2;
                        break;
                    case Gravity.BOTTOM:
                        y = mCenter.y - y - h / 2;
                        x = mCenter.x - x - w / 2;
                        break;
                }
                view.layout(x, y, x + w, y + h);
            }
            float itemstart = total + angle - sweep / 2;
            item.setGeometry(itemstart, sweep, inner, outer);
            total += sweep;
            count++;
        }
    }

    // param angle from 0..PI to Android degrees (clockwise starting at 3
    private float getDegrees(double angle) {
        return (float) (270 - 180 * angle / Math.PI);
    }

    private class CustomAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        private int mIndex;
        CustomAnimatorUpdateListener(int index) {
            mIndex = index;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mAnimators[mIndex].fraction = animation.getAnimatedFraction();

            // Special purpose animators go here
            if (mIndex == ANIMATOR_BATTERY_METER) {
                mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery +
                        (float)animation.getAnimatedFraction() *
                                (mBatteryLevel * (mEndBattery-mStartBattery) / 100),
                        mInnerBatteryRadius, mOuterBatteryRadius, mCenter);
            }
            invalidate();
        }
    }

    private void cancelAnimation() {
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].cancel();
        }
    }

    private void animateIn() {
        // Cancel & start all animations
        cancelAnimation();
        invalidate();
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].animateIn = true;
            mAnimators[i].start();
        }
    }

    public void animateOut() {
        mPanel.show(false);
        cancelAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int state;

            // Draw background
            if (mStatusMode != -1 && !mIsProtected) {
                canvas.drawARGB((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xcc), 0, 0, 0);
            }

            // Snap points
            if (mCenterDistance > mOuterChevronRadius) {
                for (int i = 0; i < 3; i++) {
                    SnapPoint snap = mSnapPoint[i];
                    mSnapBackground
                            .setAlpha((int)(snap.alpha + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 80
                                    : 0)));

                    canvas.drawCircle (snap.x, snap.y,
                            (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction *
                                Math.max(getWidth(), getHeight()) * 1.5f : 0), mSnapBackground);

                    float snapDistanceX = snap.x-mX;
                    float snapDistanceY = snap.y-mY;
                    float snapDistance = (float)Math.sqrt(Math.pow(snapDistanceX, 2)
                            + Math.pow(snapDistanceY, 2));
                    float snapTouch = snapDistance < mSnapRadius * 7 ? 200 - (snapDistance
                            * (200 - snap.alpha) / (mSnapRadius * 7)) : snap.alpha;

                    mSnapBackground.setAlpha((int)(snapTouch));
                    int len = (int)(snap.radius * 1.3f);
                    int thick = (int)(len * 0.2f);

                    Path plus = new Path();
                    plus.addRect(snap.x - len / 2, snap.y - thick / 2, snap.x
                            + len / 2, snap.y + thick / 2, Path.Direction.CW);
                    plus.addRect(snap.x - thick / 2, snap.y - len / 2, snap.x + thick / 2, snap.y
                            + len / 2, Path.Direction.CW);
                    canvas.drawPath(plus, mSnapBackground);
                }
            }

            // Draw base menu
            for (PieItem item : mItems) {
                if (!canItemDisplay(item))
                    continue;
                drawItem(canvas, item);
            }

            // Paint status report only if settings allow
            if (mStatusMode != -1 && !mIsProtected) {

                // Draw chevron rings
                mChevronBackgroundLeft.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction
                        * mGlowOffsetLeft / 2 * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));
                mChevronBackgroundRight.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction
                        * mGlowOffsetRight * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
                        canvas.drawPath(mChevronPathLeft[i], mChevronBackgroundLeft);
                    }
                    canvas.restoreToCount(state);
                }

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(180 + (1-mAnimators[ANIMATOR_BATTERY_METER].fraction) * 90, mCenter.x, mCenter.y);
                    canvas.drawPath(mChevronPathRight, mChevronBackgroundRight);
                    canvas.restoreToCount(state);
                }

                // Better not show inverted junk for top pies
                if (mPanelOrientation != Gravity.TOP) {

                    // Draw Battery
                    mBatteryBackground.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0x22));
                    mBatteryJuice.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0x88));

                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathBackground, mBatteryBackground);
                    canvas.restoreToCount(state);

                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathJuice, mBatteryJuice);
                    canvas.restoreToCount(state);

                    // Draw clock && AM/PM
                    state = canvas.save();
                    canvas.rotate(mClockTextRotation - (1-mAnimators[ANIMATOR_DEC_SPEED15].fraction) * 90, mCenter.x, mCenter.y);

                    mClockPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xcc));
                    float lastPos = 0;
                    for(int i = 0; i < mClockText.length(); i++) {
                        canvas.drawTextOnPath("" + mClockText.charAt(i), mStatusPath, lastPos, mClockOffset, mClockPaint);
                        lastPos += mClockTextOffsets[i];
                    }

                    mAmPmPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xaa));
                    canvas.drawTextOnPath(mClockTextAmPm, mStatusPath, lastPos - mClockTextAmPmSize,
                            mAmPmOffset, mAmPmPaint);
                    canvas.restoreToCount(state);

                    // Device status information and date
                    mStatusPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0xaa));

                    state = canvas.save();
                    canvas.rotate(mPanel.getDegree() + 180
                            + (1-mAnimators[ANIMATOR_DEC_SPEED15].fraction) * 90, mCenter.x,
                            mCenter.y);
                    if (mPolicy.supportsTelephony()) {
                        canvas.drawTextOnPath(mPolicy.getNetworkProvider(), mStatusPath, 0,
                                mStatusOffset * 4, mStatusPaint);
                    }
                    canvas.drawTextOnPath(mPolicy.getSimpleDate(), mStatusPath, 0,
                            mStatusOffset * 3, mStatusPaint);
                    canvas.drawTextOnPath(
                            mPanel.getBar().getNotificationData().size() - mHiddenNotification + " "
                            + mContext.getString(R.string.status_bar_latest_events_title).toUpperCase(), mStatusPath, 0,
                            mStatusOffset * 2, mStatusPaint);
                    canvas.drawTextOnPath(
                            mContext.getString(R.string.quick_settings_wifi_label).toUpperCase() + ": " + mPolicy.getWifiSsid(),
                            mStatusPath, 0, mStatusOffset * 1, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getBatteryLevelReadable(), mStatusPath, 0,
                            mStatusOffset * 0, mStatusPaint);
                    canvas.restoreToCount(state);

                    state = canvas.save();
                    canvas.rotate(mPanel.getDegree() + 180, mCenter.x, mCenter.y);

                    // Notifications
                    if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {

                        for (int i = 0; i < mNotificationCount && i < 10; i++) {
                            mNotificationPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_INC_1 + i].fraction * mGlowOffsetRight));

                            canvas.drawTextOnPath(mNotificationText[i], mNotificationPath[i], 0, 0, mNotificationPaint);

                            int IconState = canvas.save();
                            int posX = (int)(mCenter.x + mNotificationsRadius + i * mNotificationsRowSize);
                            int posY = (int)(mCenter.y - mNotificationIconSize * 1.4f);
                            int iconCenter = mNotificationIconSize / 2;

                            canvas.rotate(90, posX + iconCenter, posY + iconCenter);
                            canvas.drawBitmap(mNotificationIcon[i], null, new Rect(posX, posY, posX +
                                    mNotificationIconSize,posY + mNotificationIconSize), mNotificationPaint);
                            canvas.restoreToCount(IconState);
                        }
                    }
                    canvas.restoreToCount(state);
                }
            }
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        if (item.getView() != null) {
            int state = canvas.save();
            canvas.rotate(getDegrees(item.getStartAngle())
                        + mPanel.getDegree(), mCenter.x, mCenter.y);
            canvas.drawPath(item.getPath(), item.isSelected() ? mPieSelected : mPieBackground);
            canvas.drawPath(item.getPath(), mPieOutlines);
            canvas.restoreToCount(state);

            state = canvas.save();
            ImageView view = (ImageView)item.getView();
            canvas.translate(view.getX(), view.getY());
            canvas.rotate(getDegrees(item.getStartAngle()
                    + item.getSweep() / 2) + mPanel.getDegree(),
                    view.getWidth() / 2, view.getHeight() / 2);

            view.draw(canvas);
            canvas.restoreToCount(state);
        }
    }

    private Path makeSlice(float start, float end, int outer, int inner, Point center) {
        return makeSlice(start, end, outer, inner, center, 0, true);
    }

    private Path makeSlice(float start, float end, int outer, int inner, Point center, float narrow, boolean bothEnds) {
        RectF bb = new RectF(center.x - outer, center.y - outer, center.x + outer, center.y + outer);
        RectF bbi = new RectF(center.x - inner, center.y - inner, center.x + inner, center.y + inner);
        Path path = new Path();
        path.arcTo(bb, start, end - start, true);
        path.arcTo(bbi, end + narrow, start - end - (bothEnds ? narrow : narrow*2));
        path.close();
        return path;
    }

    // touch handling for pie
    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        if (evt.getPointerCount() > 1)
            return true;
        mX = evt.getRawX();
        mY = evt.getRawY();
        float distanceX = mCenter.x-mX;
        float distanceY = mCenter.y-mY;
        mCenterDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
        float shadeTreshold = mOuterChevronRadius; 
        int action = evt.getActionMasked();
        if (MotionEvent.ACTION_DOWN == action) {
            // Open panel
            animateIn();
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                PieItem item = mCurrentItem;

                // Activate any panels?
                mStatusPanel.hidePanels(true);
                switch(mStatusPanel.getFlipViewState()) {
                    case PieStatusPanel.NOTIFICATIONS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        mStatusPanel.showNotificationsPanel();
                        break;
                    case PieStatusPanel.QUICK_SETTINGS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        mStatusPanel.showTilesPanel();
                    break;
                }

                // Check for click actions
                if (item != null && item.getView() != null && mCenterDistance < shadeTreshold) {
                    if (mHapticFeedback)
                        mVibrator.vibrate(2);
                    item.getView().performClick();
                }
            }

            // Say good bye
            deselect();
            animateOut();
            return true;
        } else if (MotionEvent.ACTION_MOVE == action) {

            boolean snapActive = false;
            for (int i = 0; i < 3; i++) {
                SnapPoint snap = mSnapPoint[i];
                float snapDistanceX = snap.x-mX;
                float snapDistanceY = snap.y-mY;
                float snapDistance = (float)Math.sqrt(Math.pow(snapDistanceX, 2) + Math.pow(snapDistanceY, 2));

                if (snapDistance < mSnapRadius) {
                    snap.alpha = 60;
                    if (!snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                        mAnimators[ANIMATOR_SNAP_GROW].animator.start();
                        if (mHapticFeedback) mVibrator.vibrate(2);
                    }
                    snap.active = true;
                    snapActive = true;
                    mStatusPanel.setFlipViewState(-1);
                    mGlowOffsetLeft = 150;
                    mGlowOffsetRight = 150;
                } else {
                    if (snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                    }
                    snap.alpha = 30;
                    snap.active = false;
                }
            }

            // Trigger the shades?
            if (mCenterDistance > shadeTreshold) {
                int state = -1;
                switch (mPanelOrientation) {
                    case Gravity.BOTTOM:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL
                                : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.TOP:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL
                                : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.LEFT:
                        state = distanceY > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL
                                : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.RIGHT:
                        state = distanceY < 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL
                                : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                }
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.PIE_NOTIFICATIONS, 0) == 1 && !mIsProtected) {
                    if (state == PieStatusPanel.QUICK_SETTINGS_PANEL &&
                            mStatusPanel.getFlipViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 150 : 255;;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mStatusPanel.setFlipViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        if (mHapticFeedback && !snapActive)
                            mVibrator.vibrate(2);
                    } else if (state == PieStatusPanel.NOTIFICATIONS_PANEL &&
                            mStatusPanel.getFlipViewState() != PieStatusPanel.NOTIFICATIONS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 150 : 255;
                        mStatusPanel.setFlipViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        if (mHapticFeedback && !snapActive)
                            mVibrator.vibrate(2);
                    }
                }
                deselect();
            }

            // Take back shade trigger if user decides to abandon his gesture
            if (mCenterDistance < shadeTreshold) {
                mStatusPanel.setFlipViewState(-1);
                mGlowOffsetLeft = 150;
                mGlowOffsetRight = 150;

                // Check for onEnter separately or'll face constant deselect
                PieItem item = findItem(getPolar(mX, mY));
                if (item != null) {
                    if (mCenterDistance < shadeTreshold && mCenterDistance > (mInnerPieRadius * 0.75f)) {
                        onEnter(item);
                    } else {
                        deselect();
                    }
                }
            }
            invalidate();
        }
        // always re-dispatch event
        return false;
    }

    private void onEnter(PieItem item) {
        if (mCurrentItem == item)
            return;

        // deselect
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null) {
            // clear up stack
            playSoundEffect(SoundEffectConstants.CLICK);
            item.setSelected(true);
            mCurrentItem = item;
        } else {
            mCurrentItem = null;
        }
    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        mCurrentItem = null;
    }

    private float getPolar(float x, float y) {
        float deltaY = mCenter.y - y;
        float deltaX = mCenter.x - x;
        float adjustAngle = 0;;
        switch(mPanelOrientation) {
            case Gravity.TOP:
            case Gravity.LEFT:
                adjustAngle = 90;
                break;
            case Gravity.RIGHT:
                adjustAngle = -90;
                break;
        }
        return (adjustAngle + (float)Math.atan2(mPanelOrientation == Gravity.TOP ? deltaY : deltaX,
                mPanelOrientation == Gravity.TOP ? deltaX : deltaY) * 180 / (float)Math.PI)
                * (mPanelOrientation == Gravity.TOP ? -1 : 1) * (float)Math.PI / 180;
    }

    private PieItem findItem(float polar) {
        if (mItems != null) {
            int c = 0;
            for (PieItem item : mItems) {
                if (!canItemDisplay(item))
                    continue;
                if (inside(polar, item)) {
                    return item;
                }
            }
        }
        return null;
    }

    private boolean inside(float polar, PieItem item) {
        return (item.getStartAngle() < polar)
        && (item.getStartAngle() + item.getSweep() > polar);
    }

    //setup observer to do stuff!
    private class PieSettingsObserver extends ContentObserver {
        PieSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_BACKGROUND), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_SELECT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_OUTLINES), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_STATUS_CLOCK), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_STATUS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CHEVRON_LEFT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CHEVRON_RIGHT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_JUICE), false, this,
                    UserHandle.USER_ALL);

            // Get all dimensions
            getDimensions();
        }

        @Override
        public void onChange(boolean selfChange) {
            getDimensions();
        }
    }
}
