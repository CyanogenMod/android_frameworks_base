/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.slim.AppHelper;
import com.android.internal.util.slim.LockscreenTargetUtils;
import com.android.internal.util.slim.DeviceUtils;
import com.android.internal.util.slim.ImageHelper;
import com.android.internal.util.slim.SlimActions;
import com.android.internal.util.slim.TorchConstants;
import com.android.internal.view.RotationPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class KeyguardSelectorView extends LinearLayout implements KeyguardSecurityView {

    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final String TAG = "SecuritySelectorView";

    private static final String ASSIST_ICON_METADATA_NAME =
        "com.android.systemui.action_assist_icon";

    private Handler mHandler = new Handler();

    private KeyguardSecurityCallback mCallback;
    private GlowPadView mGlowPadView;
    private KeyguardShortcuts mShortcuts;
    private ObjectAnimator mAnim;
    private View mFadeView;
    private boolean mIsBouncing;
    private boolean mCameraDisabled;
    private boolean mSearchDisabled;
    private boolean mGlowTorch;
    private boolean mGlowTorchRunning;
    private boolean mUserRotation;
    private LockPatternUtils mLockPatternUtils;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private Drawable mBouncerFrame;
    private float mBatteryLevel;
    private String[] mStoredTargets;
    private int mTargetOffset;
    private int mTaps;
    private GestureDetector mDoubleTapGesture;

    OnTriggerListener mOnTriggerListener = new OnTriggerListener() {

        public void onTrigger(View v, int target) {
            if (mStoredTargets == null) {
                mCallback.userActivity(0);
                mCallback.dismiss(false);
            } else {
                if (target == mTargetOffset) {
                    mCallback.userActivity(0);
                    mCallback.dismiss(false);
                } else {
                    int realTarget = target - mTargetOffset - 1;
                    String targetUri = realTarget < mStoredTargets.length
                            ? mStoredTargets[realTarget] : null;

                    if (GlowPadView.EMPTY_TARGET.equals(targetUri)) {
                        mCallback.userActivity(0);
                        mCallback.dismiss(false);
                    } else {
                        SlimActions.processAction(mContext, targetUri, false);
                        mCallback.userActivity(0);
                    }
                }
            }
        }

        public void onReleased(View v, int handle) {
            if (!mIsBouncing) {
                doTransition(mFadeView, 1.0f);
            }
            killGlowpadTorch();
        }

        public void onGrabbed(View v, int handle) {
            mCallback.userActivity(0);
            doTransition(mFadeView, 0.0f);
            startGlowpadTorch();
        }

        public void onGrabbedStateChange(View v, int handle) {

        }

        public void onTargetChange(View v, int target) {
            if (target != -1) {
                killGlowpadTorch();
            } else {
                if (mGlowTorch && mGlowTorchRunning) {
                    // Keep screen alive extremely tiny and
                    // unintentional movement is logged here
                    mCallback.userActivity(0);
                }
            }
        }

        public void onFinishFinalAnimation() {

        }

    };

    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onDevicePolicyManagerStateChanged() {
            updateTargets();
        }

        @Override
        public void onSimStateChanged(State simState) {
            updateTargets();
        }

        @Override
        public void onRefreshBatteryInfo(KeyguardUpdateMonitor.BatteryStatus batStatus) {
            updateLockscreenBattery(batStatus);
        }
    };

    private final KeyguardActivityLauncher mActivityLauncher = new KeyguardActivityLauncher() {

        @Override
        KeyguardSecurityCallback getCallback() {
            return mCallback;
        }

        @Override
        LockPatternUtils getLockPatternUtils() {
            return mLockPatternUtils;
        }

        @Override
        Context getContext() {
            return mContext;
        }
    };

    public KeyguardSelectorView(Context context) {
        this(context, null);
    }

    public KeyguardSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLockPatternUtils = new LockPatternUtils(getContext());
        mTargetOffset = LockscreenTargetUtils.getTargetOffset(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources res = getResources();

        LinearLayout glowPadContainer = (LinearLayout) findViewById(
                R.id.keyguard_glow_pad_container);
        if (glowPadContainer != null) {
            glowPadContainer.bringToFront();
        }
        final boolean isLandscape = res.getSystem().getConfiguration()
                .orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (glowPadContainer != null &&  isLandscape &&
                LockscreenTargetUtils.isShortcuts(mContext) &&
                DeviceUtils.isPhone(mContext) &&
                !LockscreenTargetUtils.isEightTargets(mContext)) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            int pxBottom = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                60,
                res.getDisplayMetrics());
            params.setMargins(0, 0, 0, -pxBottom);
            glowPadContainer.setLayoutParams(params);
        }

        if (glowPadContainer != null &&
                LockscreenTargetUtils.isEightTargets(mContext)) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            );
            int pxBottom = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10,
                res.getDisplayMetrics());
            params.setMargins(0, 0, 0, -pxBottom);
            glowPadContainer.setLayoutParams(params);
        }

        LinearLayout msgAndShortcutsContainer = (LinearLayout) findViewById(
                R.id.keyguard_message_and_shortcuts);
        msgAndShortcutsContainer.bringToFront();

        int lockColor = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_COLOR, -2,
                UserHandle.USER_CURRENT);

        int dotColor = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_DOTS_COLOR, -2,
                UserHandle.USER_CURRENT);

        int ringColor = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_MISC_COLOR, -2,
                UserHandle.USER_CURRENT);

        String lockIcon = Settings.Secure.getStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_ICON,
                UserHandle.USER_CURRENT);

        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);

        Bitmap lock = null;

        if (lockIcon != null && lockIcon.length() > 0) {
            File f = new File(lockIcon);
            if (f.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                lock = BitmapFactory.decodeFile(lockIcon, options);

                if (Settings.Secure.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.Secure.LOCKSCREEN_COLORIZE_LOCK, 0,
                        UserHandle.USER_CURRENT) == 0) {
                    lockColor = -2;
                }
            }
        }

        mGlowPadView.setColoredIcons(lockColor, dotColor, ringColor, lock);

        updateTargets();

        if (Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_VIBRATE_ENABLED, 1) == 0) {
            mGlowPadView.setVibrateEnabled(false);
        }

        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);
        View bouncerFrameView = findViewById(R.id.keyguard_selector_view_frame);
        mBouncerFrame =
                KeyguardSecurityViewHelper.colorizeFrame(
                mContext, bouncerFrameView.getBackground());

        final boolean lockBeforeUnlock = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCK_BEFORE_UNLOCK, 0,
                UserHandle.USER_CURRENT) == 1;

        // bring emergency button on slider lockscreen
        // to front when lockBeforeUnlock is enabled
        // to make it clickable
        if (mLockPatternUtils != null && mLockPatternUtils.isSecure() && lockBeforeUnlock) {
            LinearLayout ecaContainer =
                (LinearLayout) findViewById(R.id.keyguard_selector_fade_container);
            if (ecaContainer != null) {
                ecaContainer.bringToFront();
            }
        }
        mGlowTorchRunning = false;
        mGlowTorch = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_GLOWPAD_TORCH, 0,
                UserHandle.USER_CURRENT) == 1;

        mDoubleTapGesture = new GestureDetector(mContext,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) pm.goToSleep(e.getEventTime());
                return true;
            }
        });

        if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_DOUBLE_TAP_SLEEP_GESTURE, 0) == 1) {
            mGlowPadView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return mDoubleTapGesture.onTouchEvent(event);
                }
            });
        }
    }

    public void setCarrierArea(View carrierArea) {
        mFadeView = carrierArea;
    }

    public boolean isTargetPresent(int resId) {
        return mGlowPadView.getTargetPosition(resId) != -1;
    }

    @Override
    public void showUsabilityHint() {
        mGlowPadView.ping();
    }

    private void updateTargets() {
        int currentUserHandle = mLockPatternUtils.getCurrentUser();
        DevicePolicyManager dpm = mLockPatternUtils.getDevicePolicyManager();
        int disabledFeatures = dpm.getKeyguardDisabledFeatures(null, currentUserHandle);
        boolean secureCameraDisabled = mLockPatternUtils.isSecure()
                && (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0;
        boolean cameraDisabledByAdmin = dpm.getCameraDisabled(null, currentUserHandle)
                || secureCameraDisabled;
        final KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(getContext());
        boolean disabledBySimState = monitor.isSimLocked();
        boolean cameraPresent =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean searchTargetPresent =
                isTargetPresent(com.android.internal.R.drawable.ic_action_assist_generic);

        if (cameraDisabledByAdmin) {
            Log.v(TAG, "Camera disabled by Device Policy");
        } else if (disabledBySimState) {
            Log.v(TAG, "Camera disabled by Sim State");
        }
        boolean currentUserSetup = 0 != Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE,
                0 /*default */,
                currentUserHandle);
        boolean searchActionAvailable =
                ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
        mCameraDisabled = cameraDisabledByAdmin || disabledBySimState || !cameraPresent
                || !currentUserSetup;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent
                || !currentUserSetup;
        updateResources();
        updateLockscreenBattery(null);
    }

    public void updateResources() {
        String storedTargets = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS, UserHandle.USER_CURRENT);
        ArrayList<String> description = new ArrayList<String>();
        ArrayList<String> directionDescription = new ArrayList<String>();
        final Resources res = getResources();

        int frontColor = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_TARGETS_COLOR, -2,
                UserHandle.USER_CURRENT);

        int backColor = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_MISC_COLOR, -2,
                UserHandle.USER_CURRENT);

        Drawable unlockFront = res.getDrawable(R.drawable.ic_lockscreen_unlock_normal);
        Drawable unlockBack = res.getDrawable(R.drawable.ic_lockscreen_unlock_activated);;

        if (frontColor != -2) {
            unlockFront = new BitmapDrawable(
                    res, ImageHelper.getColoredBitmap(unlockFront, frontColor));
        }

        if (backColor != -2) {
            unlockBack = new BitmapDrawable(
                    res, ImageHelper.getColoredBitmap(unlockBack, backColor));
        }

        int insetType = LockscreenTargetUtils.getInsetForIconType(
                mContext, GlowPadView.ICON_RESOURCE);
        Drawable unlock = LockscreenTargetUtils.getLayeredDrawable(mContext,
                unlockBack, unlockFront, insetType, true);
        TargetDrawable unlockTarget = new TargetDrawable(res, unlock);

        // Add unlock target
        description.add(getResources().getString(
            com.android.internal.R.string.description_target_unlock));
        directionDescription.add(getResources().getString(
            com.android.internal.R.string.accessibility_target_direction));
        if (storedTargets == null) {
            ArrayList<TargetDrawable> unlockDrawable = new ArrayList<TargetDrawable>();
            unlockDrawable.add(unlockTarget);
            mGlowPadView.setTargetResources(unlockDrawable);
            mGlowPadView.setTargetDescriptions(description);
            mGlowPadView.setDirectionDescriptions(directionDescription);
            mGlowPadView.setMagneticTargets(true);
        } else {
            mGlowPadView.setMagneticTargets(false);
            mStoredTargets = storedTargets.split("\\|");
            ArrayList<TargetDrawable> storedDrawables = new ArrayList<TargetDrawable>();
            storedDrawables.add(unlockTarget);
            final Drawable blankActiveDrawable = res.getDrawable(
                    R.drawable.ic_lockscreen_target_activated);
            final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);

            for (int i = 0; i < 8 - mTargetOffset - 1; i++) {
                if (i >= mStoredTargets.length) {
                    storedDrawables.add(new TargetDrawable(res, 0));
                    description.add("");
                    directionDescription.add("");
                    continue;
                }

                String uri = mStoredTargets[i];
                if (uri.equals(GlowPadView.EMPTY_TARGET)) {
                    storedDrawables.add(new TargetDrawable(res, 0));
                    description.add("");
                    directionDescription.add("");
                    continue;
                }

                try {
                    Intent intent = Intent.parseUri(uri, 0);
                    Drawable front = null;
                    Drawable back = activeBack;
                    boolean frontBlank = false;
                    String type = null;

                    if (intent.hasExtra(GlowPadView.ICON_FILE)) {
                        type = GlowPadView.ICON_FILE;
                        front = LockscreenTargetUtils.getDrawableFromFile(mContext,
                                intent.getStringExtra(GlowPadView.ICON_FILE));
                    } else if (intent.hasExtra(GlowPadView.ICON_RESOURCE)) {
                        String source = intent.getStringExtra(GlowPadView.ICON_RESOURCE);
                        String packageName = intent.getStringExtra(GlowPadView.ICON_PACKAGE);

                        if (source != null) {
                            front = LockscreenTargetUtils.getDrawableFromResources(mContext,
                                    packageName, source, false);
                            back = LockscreenTargetUtils.getDrawableFromResources(mContext,
                                    packageName, source, true);
                            type = GlowPadView.ICON_RESOURCE;
                            frontBlank = true;
                        }
                    }
                    if (front == null || back == null) {
                        front = LockscreenTargetUtils.getDrawableFromIntent(mContext, intent);
                    }

                    int inset = LockscreenTargetUtils.getInsetForIconType(mContext, type);

                    if (frontColor != -2) {
                        front = new BitmapDrawable(
                                res, ImageHelper.getColoredBitmap(front, frontColor));
                    }

                    if (backColor != -2) {
                        if ((back instanceof InsetDrawable)) {
                            back = new BitmapDrawable(res, ImageHelper.getColoredBitmap(
                                    blankActiveDrawable, backColor));
                        } else {
                            back = new BitmapDrawable(res, ImageHelper.getColoredBitmap(
                                    back, backColor));
                        }
                    }

                    Drawable drawable = LockscreenTargetUtils.getLayeredDrawable(mContext,
                            back,front, inset, frontBlank);
                    TargetDrawable targetDrawable = new TargetDrawable(res, drawable);

                    ComponentName compName = intent.getComponent();
                    String className = compName == null ? null : compName.getClassName();
                    if (TextUtils.equals(className, "com.android.camera.CameraLauncher")) {
                        targetDrawable.setEnabled(!mCameraDisabled);
                    } else if (TextUtils.equals(className, "SearchActivity")) {
                        targetDrawable.setEnabled(!mSearchDisabled);
                    }

                    storedDrawables.add(targetDrawable);
                    description.add(AppHelper.getFriendlyNameForUri(
                            mContext, mContext.getPackageManager(), uri));
                    directionDescription.add("");
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Invalid target uri " + uri);
                    storedDrawables.add(new TargetDrawable(res, 0));
                    description.add("");
                    directionDescription.add("");
                }
            }

            // Shift targets for landscape lockscreen on phones
            for (int i = 0; i < mTargetOffset; i++) {
                storedDrawables.add(new TargetDrawable(res, null));
                description.add("");
                directionDescription.add("");
            }

            mGlowPadView.setTargetResources(storedDrawables);
            mGlowPadView.setTargetDescriptions(description);
            mGlowPadView.setDirectionDescriptions(directionDescription);
        }
    }

    void doTransition(View view, float to) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = ObjectAnimator.ofFloat(view, "alpha", to);
        mAnim.start();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
        mShortcuts = (KeyguardShortcuts) findViewById(R.id.shortcuts);
        if (mShortcuts != null) {
            mShortcuts.setKeyguardCallback(callback);
        }
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    private void startGlowpadTorch() {
        if (mGlowTorch) {
            mHandler.removeCallbacks(checkDouble);
            mHandler.removeCallbacks(checkLongPress);
            if (mTaps > 0) {
                mHandler.postDelayed(checkLongPress,
                        ViewConfiguration.getLongPressTimeout());
                mTaps = 0;
            } else {
                mTaps += 1;
                mHandler.postDelayed(checkDouble, 400);
            }
        }
    }

    private void killGlowpadTorch() {
        if (mGlowTorch) {
            mHandler.removeCallbacks(checkLongPress);
            // Don't mess with torch if we didn't start it
            if (mGlowTorchRunning) {
                mGlowTorchRunning = false;
                Intent intent = new Intent(TorchConstants.ACTION_OFF);
                mContext.sendBroadcastAsUser(
                        intent, new UserHandle(UserHandle.USER_CURRENT));
                // Restore user rotation policy
                RotationPolicy.setRotationLock(mContext, mUserRotation);
            }
        }
    }

    final Runnable checkLongPress = new Runnable () {
        public void run() {
            mGlowTorchRunning = true;
            mUserRotation = RotationPolicy.isRotationLocked(mContext);
            // Lock device so user doesn't accidentally rotate and lose torch
            RotationPolicy.setRotationLock(mContext, true);
            Intent intent = new Intent(TorchConstants.ACTION_ON);
            mContext.sendBroadcastAsUser(
                    intent, new UserHandle(UserHandle.USER_CURRENT));
        }
    };

    final Runnable checkDouble = new Runnable () {
        public void run() {
            mTaps = 0;
        }
    };

    @Override
    public void reset() {
        mGlowPadView.reset(false);
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
        KeyguardUpdateMonitor.getInstance(getContext()).removeCallback(mUpdateCallback);
    }

    @Override
    public void onResume(int reason) {
        KeyguardUpdateMonitor.getInstance(getContext()).registerCallback(mUpdateCallback);
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showBouncer(int duration) {
        mIsBouncing = true;
        KeyguardSecurityViewHelper.
                showBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    @Override
    public void hideBouncer(int duration) {
        mIsBouncing = false;
        KeyguardSecurityViewHelper.
                hideBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    public void updateLockscreenBattery(KeyguardUpdateMonitor.BatteryStatus status) {
        if (Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.BATTERY_AROUND_LOCKSCREEN_RING,
                0 /*default */,
                UserHandle.USER_CURRENT) == 1) {
            if (status != null) mBatteryLevel = status.level;
            float cappedBattery = mBatteryLevel;

            if (mBatteryLevel < 15) {
                cappedBattery = 15;
            }
            else if (mBatteryLevel > 90) {
                cappedBattery = 90;
            }

            final float hue = (cappedBattery - 15) * 1.6f;
            mGlowPadView.setArc(mBatteryLevel * 3.6f, Color.HSVToColor(0x80, new float[]{ hue, 1.f, 1.f }));
        } else {
            mGlowPadView.setArc(0, 0);
        }
    }
}
