/*
 * Copyright (C) 2013-14 The Android Open Source Project
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.systemui;

import android.animation.ArgbEvaluator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryStateRegistar;

import org.cyanogenmod.graphics.drawable.StopMotionVectorDrawable;

public class BatteryMeterView extends View implements DemoMode,
        BatteryController.BatteryStateChangeCallback {
    public static final String TAG = BatteryMeterView.class.getSimpleName();
    public static final String ACTION_LEVEL_TEST = "com.android.systemui.BATTERY_LEVEL_TEST";

    private final int[] mColors;

    protected boolean mShowPercent = true;

    public enum BatteryMeterMode {
        BATTERY_METER_GONE,
        BATTERY_METER_ICON_PORTRAIT,
        BATTERY_METER_ICON_LANDSCAPE,
        BATTERY_METER_CIRCLE,
        BATTERY_METER_TEXT
    }

    private int mHeight;
    private int mWidth;
    private String mWarningString;
    private final int mCriticalLevel;

    private boolean mAnimationsEnabled;

    private BatteryStateRegistar mBatteryStateRegistar;
    private BatteryController mBatteryController;
    private boolean mPowerSaveEnabled;

    private int mDarkModeBackgroundColor;
    private int mDarkModeFillColor;

    private int mLightModeBackgroundColor;
    private int mLightModeFillColor;

    protected BatteryMeterMode mMeterMode = null;

    protected boolean mAttached;

    private boolean mDemoMode;
    protected BatteryTracker mDemoTracker = new BatteryTracker();
    protected BatteryTracker mTracker = new BatteryTracker();
    private BatteryMeterDrawable mBatteryMeterDrawable;
    private int mIconTint = Color.WHITE;

    private int mCurrentBackgroundColor = 0;
    private int mCurrentFillColor = 0;

    protected class BatteryTracker extends BroadcastReceiver {
        public static final int UNKNOWN_LEVEL = -1;

        // current battery status
        boolean present = true;
        int level = UNKNOWN_LEVEL;
        String percentStr;
        int plugType;
        boolean plugged;
        int health;
        int status;
        String technology;
        int voltage;
        int temperature;
        boolean testmode = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (testmode && ! intent.getBooleanExtra("testmode", false)) return;

                level = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));

                present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
                plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                plugged = plugType != 0;
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN);
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                setContentDescription(
                        context.getString(R.string.accessibility_battery_level, level));
                if (mBatteryMeterDrawable != null) {
                    setVisibility(View.VISIBLE);
                    invalidate();
                }
            } else if (action.equals(ACTION_LEVEL_TEST)) {
                testmode = true;
                post(new Runnable() {
                    int curLevel = 0;
                    int incr = 1;
                    int saveLevel = level;
                    int savePlugged = plugType;
                    Intent dummy = new Intent(Intent.ACTION_BATTERY_CHANGED);
                    @Override
                    public void run() {
                        if (curLevel < 0) {
                            testmode = false;
                            dummy.putExtra("level", saveLevel);
                            dummy.putExtra("plugged", savePlugged);
                            dummy.putExtra("testmode", false);
                        } else {
                            dummy.putExtra("level", curLevel);
                            dummy.putExtra("plugged", incr > 0
                                    ? BatteryManager.BATTERY_PLUGGED_AC : 0);
                            dummy.putExtra("testmode", true);
                        }
                        getContext().sendBroadcast(dummy);

                        if (!testmode) return;

                        curLevel += incr;
                        if (curLevel == 100) {
                            incr *= -1;
                        }
                        postDelayed(this, 200);
                    }
                });
            }
        }

        protected boolean shouldIndicateCharging() {
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                return true;
            }
            if (plugged) {
                return status == BatteryManager.BATTERY_STATUS_FULL;
            }
            return false;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ACTION_LEVEL_TEST);
        final Intent sticky = getContext().registerReceiver(mTracker, filter);
        if (sticky != null) {
            // preload the battery level
            mTracker.onReceive(getContext(), sticky);
        }
        if (mBatteryStateRegistar != null) {
            mBatteryStateRegistar.addStateChangedCallback(this);
        }
        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttached = false;
        getContext().unregisterReceiver(mTracker);
        if (mBatteryStateRegistar != null) {
            mBatteryStateRegistar.removeStateChangedCallback(this);
        }
    }

    public BatteryMeterView(Context context) {
        this(context, null, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        TypedArray atts = context.obtainStyledAttributes(attrs, R.styleable.BatteryMeterView,
                defStyle, 0);
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();
        atts.recycle();
        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);
        mCriticalLevel = getContext().getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        mDarkModeBackgroundColor =
                context.getColor(R.color.dark_mode_icon_color_dual_tone_background);
        mDarkModeFillColor = context.getColor(R.color.dark_mode_icon_color_dual_tone_fill);
        mLightModeBackgroundColor =
                context.getColor(R.color.light_mode_icon_color_dual_tone_background);
        mLightModeFillColor = context.getColor(R.color.light_mode_icon_color_dual_tone_fill);

        setAnimationsEnabled(true);
    }

    protected BatteryMeterDrawable createBatteryMeterDrawable(BatteryMeterMode mode) {
        Resources res = getResources();
        switch (mode) {
            case BATTERY_METER_TEXT:
            case BATTERY_METER_GONE:
                return null;
            default:
                return new AllInOneBatteryMeterDrawable(res, mode);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (mMeterMode == BatteryMeterMode.BATTERY_METER_TEXT) {
            onSizeChanged(width, height, 0, 0); // Force a size changed event
        }

        setMeasuredDimension(width, height);
    }

    public void setBatteryStateRegistar(BatteryStateRegistar batteryStateRegistar) {
        mBatteryStateRegistar = batteryStateRegistar;
        if (!mAttached) {
            mBatteryStateRegistar.addStateChangedCallback(this);
        }
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mPowerSaveEnabled = mBatteryController.isPowerSave();
    }

    @Override
    public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn,
            boolean charging) {
        // TODO: Use this callback instead of own broadcast receiver.
    }

    @Override
    public void onPowerSaveChanged() {
        mPowerSaveEnabled = mBatteryController.isPowerSave();
        invalidate();
    }

    public void setAnimationsEnabled(boolean enabled) {
        if (mAnimationsEnabled != enabled) {
            mAnimationsEnabled = enabled;
            setLayerType(mAnimationsEnabled ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
            invalidate();
        }
    }

    @Override
    public void onBatteryStyleChanged(int style, int percentMode) {
        boolean showInsidePercent = percentMode == BatteryController.PERCENTAGE_MODE_INSIDE;
        BatteryMeterMode meterMode = BatteryMeterMode.BATTERY_METER_ICON_PORTRAIT;

        switch (style) {
            case BatteryController.STYLE_CIRCLE:
                meterMode = BatteryMeterMode.BATTERY_METER_CIRCLE;
                break;
            case BatteryController.STYLE_GONE:
                meterMode = BatteryMeterMode.BATTERY_METER_GONE;
                showInsidePercent = false;
                break;
            case BatteryController.STYLE_ICON_LANDSCAPE:
                meterMode = BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE;
                break;
            case BatteryController.STYLE_TEXT:
                meterMode = BatteryMeterMode.BATTERY_METER_TEXT;
                showInsidePercent = false;
                break;
            default:
                break;
        }

        setMode(meterMode);
        mShowPercent = showInsidePercent;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mHeight = h;
        mWidth = w;
        if (mBatteryMeterDrawable != null) {
            mBatteryMeterDrawable.onSizeChanged(w, h, oldw, oldh);
        }
    }

    public void setMode(BatteryMeterMode mode) {
        if (mMeterMode == mode) {
            return;
        }

        mMeterMode = mode;
        BatteryTracker tracker = mDemoMode ? mDemoTracker : mTracker;
        if (mode == BatteryMeterMode.BATTERY_METER_GONE ||
                mode == BatteryMeterMode.BATTERY_METER_TEXT) {
            setVisibility(View.GONE);
            mBatteryMeterDrawable = null;
        } else {
            if (mBatteryMeterDrawable != null) {
                mBatteryMeterDrawable.onDispose();
            }
            mBatteryMeterDrawable = createBatteryMeterDrawable(mode);
            if (tracker.present) {
                setVisibility(View.VISIBLE);
                requestLayout();
                invalidate();
            } else {
                setVisibility(View.GONE);
            }
        }
    }

    public int getColorForLevel(int percent) {
        // If we are in power save mode, always use the normal color.
        if (mPowerSaveEnabled) {
            return mColors[mColors.length-1];
        }
        int thresh, color = 0;
        for (int i=0; i<mColors.length; i+=2) {
            thresh = mColors[i];
            color = mColors[i+1];
            if (percent <= thresh) {

                // Respect tinting for "normal" level
                if (i == mColors.length-2) {
                    return mIconTint;
                } else {
                    return color;
                }
            }
        }
        return color;
    }

    public void setDarkIntensity(float darkIntensity) {
        if (mBatteryMeterDrawable != null) {
            mCurrentBackgroundColor = getBackgroundColor(darkIntensity);
            mCurrentFillColor = getFillColor(darkIntensity);
            mBatteryMeterDrawable.setDarkIntensity(mCurrentBackgroundColor, mCurrentFillColor);
        }
    }

    private int getBackgroundColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeBackgroundColor, mDarkModeBackgroundColor);
    }

    private int getFillColor(float darkIntensity) {
        return getColorForDarkIntensity(
                darkIntensity, mLightModeFillColor, mDarkModeFillColor);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBatteryMeterDrawable != null) {
            BatteryTracker tracker = mDemoMode ? mDemoTracker : mTracker;
            mBatteryMeterDrawable.onDraw(canvas, tracker);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (getVisibility() == View.VISIBLE) {
            if (!mDemoMode && command.equals(COMMAND_ENTER)) {
                mDemoMode = true;
                mDemoTracker.level = mTracker.level;
                mDemoTracker.plugged = mTracker.plugged;
            } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
                mDemoMode = false;
                postInvalidate();
            } else if (mDemoMode && command.equals(COMMAND_BATTERY)) {
                String level = args.getString("level");
                String plugged = args.getString("plugged");
                if (level != null) {
                    mDemoTracker.level = Math.min(Math.max(Integer.parseInt(level), 0), 100);
                }
                if (plugged != null) {
                    mDemoTracker.plugged = Boolean.parseBoolean(plugged);
                }
                postInvalidate();
            }
        }
    }

    protected interface BatteryMeterDrawable {
        void onDraw(Canvas c, BatteryTracker tracker);
        void onSizeChanged(int w, int h, int oldw, int oldh);
        void onDispose();
        void setDarkIntensity(int backgroundColor, int fillColor);
    }

    protected class AllInOneBatteryMeterDrawable  implements BatteryMeterDrawable {
        private static final boolean SINGLE_DIGIT_PERCENT = false;
        private static final boolean SHOW_100_PERCENT = false;

        private boolean mDisposed;

        private boolean mIsAnimating; // stores charge-animation status to remove callbacks

        private float mTextX, mTextY; // precalculated position for drawText() to appear centered

        private boolean mInitialized;

        private Paint mTextAndBoltPaint;
        private Paint mWarningTextPaint;
        private Paint mClearPaint;

        private LayerDrawable mBatteryDrawable;
        private Drawable mFrameDrawable;
        private StopMotionVectorDrawable mLevelDrawable;
        private Drawable mBoltDrawable;

        private BatteryMeterMode mMode;
        private int mTextGravity;

        public AllInOneBatteryMeterDrawable(Resources res, BatteryMeterMode mode) {
            super();

            loadBatteryDrawables(res, mode);

            mMode = mode;
            mDisposed = false;

            // load text gravity and blend mode
            int[] attrs = new int[] {android.R.attr.gravity, R.attr.blendMode};
            int resId = getBatteryDrawableStyleResourceForMode(mode);
            PorterDuff.Mode xferMode = PorterDuff.Mode.XOR;
            if (resId != 0) {
                TypedArray a = getContext().obtainStyledAttributes(
                        getBatteryDrawableStyleResourceForMode(mode), attrs);
                mTextGravity = a.getInt(0, Gravity.CENTER);
                xferMode = PorterDuff.intToMode(a.getInt(1,
                        PorterDuff.modeToInt(PorterDuff.Mode.XOR)));
            } else {
                mTextGravity = Gravity.CENTER;
            }
            Log.d(TAG, "mTextGravity=" + mTextGravity);

            mTextAndBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            mTextAndBoltPaint.setTypeface(font);
            mTextAndBoltPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));
            mTextAndBoltPaint.setXfermode(new PorterDuffXfermode(xferMode));
            mTextAndBoltPaint.setColor(mCurrentFillColor != 0
                    ? mCurrentFillColor
                    : res.getColor(R.color.batterymeter_bolt_color));

            mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWarningTextPaint.setColor(mColors[1]);
            font = Typeface.create("sans-serif", Typeface.BOLD);
            mWarningTextPaint.setTypeface(font);
            mWarningTextPaint.setTextAlign(getPaintAlignmentFromGravity(mTextGravity));

            mClearPaint = new Paint();
            mClearPaint.setColor(0);
        }

        @Override
        public void onDraw(Canvas c, BatteryTracker tracker) {
            if (mDisposed) return;

            if (!mInitialized) {
                init();
            }

            drawBattery(c, tracker);
            if (mAnimationsEnabled) {
                // TODO: Allow custom animations to be used
            }
        }

        @Override
        public void onDispose() {
            mDisposed = true;
        }

        @Override
        public void setDarkIntensity(int backgroundColor, int fillColor) {
            mIconTint = fillColor;
            // Make bolt fully opaque for increased visibility
            mBoltDrawable.setTint(0xff000000 | fillColor);
            mFrameDrawable.setTint(backgroundColor);
            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);
            invalidate();
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            init();
        }

        private boolean isThemeApplied() {
            ThemeConfig themeConfig = ThemeConfig.getBootTheme(getContext().getContentResolver());
            return themeConfig != null &&
                    !ThemeConfig.SYSTEM_DEFAULT.equals(themeConfig.getOverlayForStatusBar());
        }

        private void checkBatteryMeterDrawableValid(Resources res, BatteryMeterMode mode) {
            final int resId = getBatteryDrawableResourceForMode(mode);
            final Drawable batteryDrawable;
            try {
                batteryDrawable = res.getDrawable(resId);
            } catch (Resources.NotFoundException e) {
                throw new BatteryMeterDrawableException(res.getResourceName(resId) + " is an " +
                        "invalid drawable", e);
            }

            // check that the drawable is a LayerDrawable
            if (!(batteryDrawable instanceof LayerDrawable)) {
                throw new BatteryMeterDrawableException("Expected a LayerDrawable but received a " +
                        batteryDrawable.getClass().getSimpleName());
            }

            final LayerDrawable layerDrawable = (LayerDrawable) batteryDrawable;
            final Drawable frame = layerDrawable.findDrawableByLayerId(R.id.battery_frame);
            final Drawable level = layerDrawable.findDrawableByLayerId(R.id.battery_fill);
            final Drawable bolt = layerDrawable.findDrawableByLayerId(
                    R.id.battery_charge_indicator);
            // now check that the required layers exist and are of the correct type
            if (frame == null) {
                throw new BatteryMeterDrawableException("Missing battery_frame drawble");
            }
            if (bolt == null) {
                throw new BatteryMeterDrawableException(
                        "Missing battery_charge_indicator drawable");
            }
            if (level != null) {
                // check that the level drawable is an AnimatedVectorDrawable
                if (!(level instanceof AnimatedVectorDrawable)) {
                    throw new BatteryMeterDrawableException("Expected a AnimatedVectorDrawable " +
                            "but received a " + level.getClass().getSimpleName());
                }
                // make sure we can stop motion animate the level drawable
                try {
                    StopMotionVectorDrawable smvd = new StopMotionVectorDrawable(level);
                    smvd.setCurrentFraction(0.5f);
                } catch (Exception e) {
                    throw new BatteryMeterDrawableException("Unable to perform stop motion on " +
                            "battery_fill drawable", e);
                }
            } else {
                throw new BatteryMeterDrawableException("Missing battery_fill drawable");
            }
        }

        private void loadBatteryDrawables(Resources res, BatteryMeterMode mode) {
            if (isThemeApplied()) {
                try {
                    checkBatteryMeterDrawableValid(res, mode);
                } catch (BatteryMeterDrawableException e) {
                    Log.w(TAG, "Invalid themed battery meter drawable, falling back to system", e);
                    final Context context = getContext();
                    PackageManager pm = getContext().getPackageManager();
                    try {
                        res = pm.getThemedResourcesForApplication(context.getPackageName(),
                                ThemeConfig.SYSTEM_DEFAULT);
                    } catch (PackageManager.NameNotFoundException nnfe) {
                        /* ignore, this should not happen */
                    }
                }
            }

            int drawableResId = getBatteryDrawableResourceForMode(mode);
            mBatteryDrawable = (LayerDrawable) res.getDrawable(drawableResId);
            mFrameDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_frame);
            mFrameDrawable.setTint(mCurrentBackgroundColor != 0
                    ? mCurrentBackgroundColor
                    : res.getColor(R.color.batterymeter_frame_color));
            // set the animated vector drawable we will be stop animating
            Drawable levelDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_fill);
            mLevelDrawable = new StopMotionVectorDrawable(levelDrawable);
            mBoltDrawable = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
        }

        private void drawBattery(Canvas canvas, BatteryTracker tracker) {
            boolean unknownStatus = tracker.status == BatteryManager.BATTERY_STATUS_UNKNOWN;
            int level = tracker.level;

            if (unknownStatus || tracker.status == BatteryManager.BATTERY_STATUS_FULL) {
                level = 100;
            }

            mTextAndBoltPaint.setColor(getColorForLevel(level));

            // Make sure we don't draw the charge indicator if not plugged in
            Drawable d = mBatteryDrawable.findDrawableByLayerId(R.id.battery_charge_indicator);
            if (d instanceof BitmapDrawable) {
                // In case we are using a BitmapDrawable, which we should be unless something bad
                // happened, we need to change the paint rather than the alpha in case the blendMode
                // has been set to clear.  Clear always clears regardless of alpha level ;)
                BitmapDrawable bd = (BitmapDrawable) d;
                bd.getPaint().set(tracker.plugged ? mTextAndBoltPaint : mClearPaint);
            } else {
                d.setAlpha(tracker.plugged ? 255 : 0);
            }

            // Now draw the level indicator
            // set the level and tint color of the fill drawable
            mLevelDrawable.setCurrentFraction(level / 100f);
            mLevelDrawable.setTint(getColorForLevel(level));
            mBatteryDrawable.draw(canvas);

            // if chosen by options, draw percentage text in the middle
            // always skip percentage when 100, so layout doesnt break
            if (unknownStatus) {
                mTextAndBoltPaint.setColor(getContext().getColor(R.color.batterymeter_frame_color));
                canvas.drawText("?", mTextX, mTextY, mTextAndBoltPaint);

            } else if (!tracker.plugged) {
                drawPercentageText(canvas, tracker);
            }
        }

        private void drawPercentageText(Canvas canvas, BatteryTracker tracker) {
            final int level = tracker.level;
            if (level > mCriticalLevel
                    && (mShowPercent && !(level == 100 && !SHOW_100_PERCENT))) {
                // draw the percentage text
                String pctText = String.valueOf(SINGLE_DIGIT_PERCENT ? (level/10) : level);
                mTextAndBoltPaint.setColor(getColorForLevel(level));
                canvas.drawText(pctText, mTextX, mTextY, mTextAndBoltPaint);
            } else if (level <= mCriticalLevel) {
                // draw the warning text
                canvas.drawText(mWarningString, mTextX, mTextY, mWarningTextPaint);
            }
        }

        /**
         * initializes all size dependent variables
         */
        private void init() {
            // not much we can do with zero width or height, we'll get another pass later
            if (mWidth <= 0 || mHeight <=0) return;

            final float widthDiv2 = mWidth / 2f;
            // text size is width / 2 - 2dp for wiggle room
            final float textSize = widthDiv2 - getResources().getDisplayMetrics().density * 2;
            mTextAndBoltPaint.setTextSize(textSize);
            mWarningTextPaint.setTextSize(textSize);

            int pLeft = getPaddingLeft();
            Rect iconBounds = new Rect(pLeft, 0, pLeft + mWidth, mHeight);
            mBatteryDrawable.setBounds(iconBounds);

            // calculate text position
            Rect bounds = new Rect();
            mTextAndBoltPaint.getTextBounds("99", 0, "99".length(), bounds);
            boolean isRtl = isLayoutRtl();

            // compute mTextX based on text gravity
            if ((mTextGravity & Gravity.START) == Gravity.START) {
                mTextX = isRtl ? mWidth : 0;
            } else if ((mTextGravity & Gravity.END) == Gravity.END) {
                mTextX = isRtl ? 0 : mWidth;
            } else if ((mTextGravity & Gravity.LEFT) == Gravity.LEFT) {
                mTextX = 0;
            }else if ((mTextGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                mTextX = mWidth;
            } else {
                mTextX = widthDiv2 + pLeft;
            }

            // compute mTextY based on text gravity
            if ((mTextGravity & Gravity.TOP) == Gravity.TOP) {
                mTextY = bounds.height();
            } else if ((mTextGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                mTextY = mHeight;
            } else {
                mTextY = widthDiv2 + bounds.height() / 2.0f;
            }

            updateBoltDrawableLayer(mBatteryDrawable, mBoltDrawable);

            mInitialized = true;
        }

        private int getBatteryDrawableResourceForMode(BatteryMeterMode mode) {
            switch (mode) {
                case BATTERY_METER_ICON_LANDSCAPE:
                    return R.drawable.ic_battery_landscape;
                case BATTERY_METER_CIRCLE:
                    return R.drawable.ic_battery_circle;
                case BATTERY_METER_ICON_PORTRAIT:
                    return R.drawable.ic_battery_portrait;
                default:
                    return 0;
            }
        }

        private int getBatteryDrawableStyleResourceForMode(BatteryMeterMode mode) {
            switch (mode) {
                case BATTERY_METER_ICON_LANDSCAPE:
                    return R.style.BatteryMeterViewDrawable_Landscape;
                case BATTERY_METER_CIRCLE:
                    return R.style.BatteryMeterViewDrawable_Circle;
                case BATTERY_METER_ICON_PORTRAIT:
                    return R.style.BatteryMeterViewDrawable_Portrait;
                default:
                    return R.style.BatteryMeterViewDrawable;
            }
        }

        private Paint.Align getPaintAlignmentFromGravity(int gravity) {
            boolean isRtl = isLayoutRtl();
            if ((gravity & Gravity.START) == Gravity.START) {
                return isRtl ? Paint.Align.RIGHT : Paint.Align.LEFT;
            }
            if ((gravity & Gravity.END) == Gravity.END) {
                return isRtl ? Paint.Align.LEFT : Paint.Align.RIGHT;
            }
            if ((gravity & Gravity.LEFT) == Gravity.LEFT) return Paint.Align.LEFT;
            if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) return Paint.Align.RIGHT;

            // default to center
            return Paint.Align.CENTER;
        }

        // Creates a BitmapDrawable of the bolt so we can make use of the XOR xfer mode with vector
        // based drawables
        private void updateBoltDrawableLayer(LayerDrawable batteryDrawable, Drawable boltDrawable) {
            BitmapDrawable newBoltDrawable;
            if (boltDrawable instanceof BitmapDrawable) {
                newBoltDrawable = (BitmapDrawable) boltDrawable.mutate();
            } else {
                Bitmap boltBitmap = createBoltBitmap(boltDrawable);
                if (boltBitmap == null) {
                    // not much to do with a null bitmap so keep original bolt for now
                    return;
                }
                Rect bounds = boltDrawable.getBounds();
                newBoltDrawable = new BitmapDrawable(getResources(), boltBitmap);
                newBoltDrawable.setBounds(bounds);
            }
            newBoltDrawable.getPaint().set(mTextAndBoltPaint);
            batteryDrawable.setDrawableByLayerId(R.id.battery_charge_indicator, newBoltDrawable);
        }

        private Bitmap createBoltBitmap(Drawable boltDrawable) {
            // not much we can do with zero width or height, we'll get another pass later
            if (mWidth <= 0 || mHeight <= 0) return null;

            Bitmap bolt;
            if (!(boltDrawable instanceof BitmapDrawable)) {
                int pLeft = getPaddingLeft();
                Rect iconBounds = new Rect(pLeft, 0, pLeft + mWidth, mHeight);
                bolt = Bitmap.createBitmap(iconBounds.width(), iconBounds.height(),
                        Bitmap.Config.ARGB_8888);
                if (bolt != null) {
                    Canvas c = new Canvas(bolt);
                    c.drawColor(-1, PorterDuff.Mode.CLEAR);
                    boltDrawable.draw(c);
                }
            } else {
                bolt = ((BitmapDrawable) boltDrawable).getBitmap();
            }

            return bolt;
        }

        private class BatteryMeterDrawableException extends RuntimeException {
            public BatteryMeterDrawableException(String detailMessage) {
                super(detailMessage);
            }

            public BatteryMeterDrawableException(String detailMessage, Throwable throwable) {
                super(detailMessage, throwable);
            }
        }
    }
}
