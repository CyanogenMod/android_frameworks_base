/*
 * Copyright (C) 2012 The Android Open Source Project
 * Modifications Copyright (C) 2013 - 2014 The NamelessROM Project
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

import java.net.URISyntaxException;
import java.util.ArrayList;

import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.cm.LockscreenTargetUtils;
import com.android.internal.util.nameless.NamelessUtils;
import com.android.internal.util.nameless.constants.FlashLightConstants;
import com.android.internal.view.RotationPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;

public class KeyguardSelectorView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final String TAG = "SecuritySelectorView";
    private static final String ASSIST_ICON_METADATA_NAME =
        "com.android.systemui.action_assist_icon";

    private Handler mHandler = new Handler();

    private KeyguardSecurityCallback mCallback;
    private GlowPadView mGlowPadView;
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
    private String[] mStoredTargets;
    private int mTaps;
    private int mTargetOffset;
    private boolean mIsScreenLarge;
    private float mBatteryLevel;

    OnTriggerListener mOnTriggerListener = new OnTriggerListener() {

        public void onTrigger(View v, int target) {
            if (mStoredTargets == null) {
                final int resId = mGlowPadView.getResourceIdForTarget(target);

                switch (resId) {
                    case R.drawable.ic_action_assist_generic:
                        Intent assistIntent =
                                ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                                .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
                        if (assistIntent != null) {
                            mActivityLauncher.launchActivity(assistIntent,
                                    false, true, null, null);
                        } else {
                            Log.w(TAG, "Failed to get intent for assist activity");
                        }
                        mCallback.userActivity(0);
                        break;

                    case R.drawable.ic_lockscreen_camera:
                        mActivityLauncher.launchCamera(null, null);
                        mCallback.userActivity(0);
                        break;

                    case R.drawable.ic_lockscreen_unlock_phantom:
                    case R.drawable.ic_lockscreen_unlock:
                        mCallback.userActivity(0);
                        mCallback.dismiss(false);
                    break;
                }
            } else if (target == mTargetOffset) {
                mCallback.dismiss(false);
            } else {
                int realTarget = target - mTargetOffset - 1;
                String targetUri = realTarget < mStoredTargets.length
                        ? mStoredTargets[realTarget] : null;

                if (LockscreenTargetUtils.EMPTY_TARGET.equals(targetUri)) {
                    mCallback.dismiss(false);
                } else {
                    try {
                        Intent intent = Intent.parseUri(targetUri, 0);
                        mActivityLauncher.launchActivity(intent, false, true, null, null);
                    } catch (URISyntaxException e) {
                        Log.w(TAG, "Invalid lockscreen target " + targetUri);
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
                    // Keep screen alive extremely tiny
                    // unintentional movement is logged
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
        protected void dismissKeyguardOnNextActivity() {
            getCallback().dismiss(false);
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
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);
        updateTargets();

        if (Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.LOCKSCREEN_VIBRATE_ENABLED, 1) == 0) {
            mGlowPadView.setVibrateEnabled(false);
        }

        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);
        View bouncerFrameView = findViewById(R.id.keyguard_selector_view_frame);
        mBouncerFrame = bouncerFrameView.getBackground();

        mGlowTorchRunning = false;
        mGlowTorch = (Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.LOCKSCREEN_GLOWPAD_TORCH, 0,
                UserHandle.USER_CURRENT) == 1)
                && NamelessUtils.isPackageInstalled(mContext, FlashLightConstants.APP_PACKAGE_NAME);
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
        boolean cameraTargetPresent =
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean searchTargetPresent =
            isTargetPresent(R.drawable.ic_action_assist_generic);

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
        mCameraDisabled = cameraDisabledByAdmin || disabledBySimState || !cameraTargetPresent
                || !currentUserSetup;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent
                || !currentUserSetup;
        updateResources();
        updateLockscreenBattery(null);
    }

    public void updateResources() {
        String storedTargets = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS, UserHandle.USER_CURRENT);
        if (storedTargets == null) {
            // Update the search icon with drawable from the search .apk
            if (!mSearchDisabled) {
                Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                        .getAssistIntent(mContext, false, UserHandle.USER_CURRENT);
                if (intent != null) {
                    // XXX Hack. We need to substitute the icon here but haven't formalized
                    // the public API. The "_google" metadata will be going away, so
                    // DON'T USE IT!
                    ComponentName component = intent.getComponent();
                    boolean replaced = mGlowPadView.replaceTargetDrawablesIfPresent(component,
                            ASSIST_ICON_METADATA_NAME + "_google", R.drawable.ic_action_assist_generic);

                    if (!replaced && !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                            ASSIST_ICON_METADATA_NAME, R.drawable.ic_action_assist_generic)) {
                        Slog.w(TAG, "Couldn't grab icon from package " + component);
                    }
                }
            }

            mGlowPadView.setEnableTarget(R.drawable.ic_lockscreen_camera, !mCameraDisabled);
            mGlowPadView.setEnableTarget(R.drawable.ic_action_assist_generic, !mSearchDisabled);

            // Enable magnetic targets
            mGlowPadView.setMagneticTargets(true);

            mGlowPadView.setTargetDescriptionsResourceId(R.array.lockscreen_target_descriptions_unlock_only);
            mGlowPadView.setDirectionDescriptionsResourceId(R.array.lockscreen_direction_descriptions);
        } else {
            mStoredTargets = storedTargets.split("\\|");

            // Temporarily hide all targets if bouncing a widget
            if (mIsBouncing) {
                for (int i = 0; i < mStoredTargets.length; i++) {
                    mStoredTargets[i] = LockscreenTargetUtils.EMPTY_TARGET;
                }
            }

            ArrayList<TargetDrawable> storedDrawables = new ArrayList<TargetDrawable>();

            final Resources res = getResources();
            final Drawable blankActiveDrawable = res.getDrawable(
                    R.drawable.ic_lockscreen_target_activated);
            final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);

            // Disable magnetic target
            mGlowPadView.setMagneticTargets(false);

            // Magnetic target replacement
            final Drawable blankInActiveDrawable = res.getDrawable(
                    R.drawable.ic_lockscreen_lock_pressed);
            final Drawable unlockActiveDrawable = res.getDrawable(
                    R.drawable.ic_lockscreen_unlock_activated);

            // Shift targets for landscape lockscreen on phones
            for (int i = 0; i < mTargetOffset; i++) {
                storedDrawables.add(new TargetDrawable(res, null));
            }

            // Add unlock target
            storedDrawables.add(new TargetDrawable(res,
                    res.getDrawable(R.drawable.ic_lockscreen_unlock)));

            for (int i = 0; i < 8 - mTargetOffset - 1; i++) {
                if (i >= mStoredTargets.length) {
                    storedDrawables.add(new TargetDrawable(res, 0));
                    continue;
                }

                String uri = mStoredTargets[i];
                if (uri.equals(LockscreenTargetUtils.EMPTY_TARGET)) {
                    Drawable d = LockscreenTargetUtils.getLayeredDrawable(
                            mContext, unlockActiveDrawable, blankInActiveDrawable,
                            LockscreenTargetUtils.getInsetForIconType(mContext, null), true);
                    storedDrawables.add(new TargetDrawable(res, d));
                    continue;
                }

                try {
                    Intent intent = Intent.parseUri(uri, 0);
                    Drawable front = null;
                    Drawable back = activeBack;
                    boolean frontBlank = false;
                    String type = null;

                    if (intent.hasExtra(LockscreenTargetUtils.ICON_FILE)) {
                        type = LockscreenTargetUtils.ICON_FILE;
                        front = LockscreenTargetUtils.getDrawableFromFile(mContext,
                                intent.getStringExtra(LockscreenTargetUtils.ICON_FILE));
                    } else if (intent.hasExtra(LockscreenTargetUtils.ICON_RESOURCE)) {
                        String source = intent.getStringExtra(LockscreenTargetUtils.ICON_RESOURCE);
                        String packageName = intent.getStringExtra(LockscreenTargetUtils.ICON_PACKAGE);

                        if (source != null) {
                            front = LockscreenTargetUtils.getDrawableFromResources(mContext,
                                    packageName, source, false);
                            back = LockscreenTargetUtils.getDrawableFromResources(mContext,
                                    packageName, source, true);
                            type = LockscreenTargetUtils.ICON_RESOURCE;
                            frontBlank = true;
                        }
                    }
                    if (front == null) {
                        front = LockscreenTargetUtils.getDrawableFromIntent(mContext, intent);
                    }
                    if (back == null) {
                        back = activeBack;
                    }

                    int inset = LockscreenTargetUtils.getInsetForIconType(mContext, type);
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
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Invalid target uri " + uri);
                    storedDrawables.add(new TargetDrawable(res, 0));
                }
            }

            mGlowPadView.setTargetDescriptionsResourceId(0);
            mGlowPadView.setDirectionDescriptionsResourceId(0);
            mGlowPadView.setTargetResources(storedDrawables);
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
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    private void startGlowpadTorch() {
        if (DEBUG) {
            Log.v(TAG, "Start Glowpad Torch");
        }
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
        if (DEBUG) {
            Log.v(TAG, "Kill Glowpad Torch");
        }
        if (mGlowTorch) {
            mHandler.removeCallbacks(checkLongPress);
            // Don't mess with torch if we didn't start it
            if (mGlowTorchRunning) {
                mGlowTorchRunning = false;
                Intent intent = new Intent(FlashLightConstants.ACTION_TOGGLE_STATE);
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
            Intent intent = new Intent(FlashLightConstants.ACTION_TOGGLE_STATE);
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
        if (mGlowTorch) {
            try {
                mContext.unregisterReceiver(mTorchReceiver);
            } catch (Exception e) {
                if (DEBUG) {
                    Log.e(TAG, "unregistering mTorchReceiver: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onResume(int reason) {
        KeyguardUpdateMonitor.getInstance(getContext()).registerCallback(mUpdateCallback);
        if (mGlowTorch) {
            mContext.registerReceiver(mTorchReceiver,
                    new IntentFilter(FlashLightConstants.ACTION_STATE_CHANGED));
        }
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showBouncer(int duration) {
        mIsBouncing = true;
        updateResources();
        KeyguardSecurityViewHelper.
                showBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    @Override
    public void hideBouncer(int duration) {
        mIsBouncing = false;
        updateResources();
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

    private final BroadcastReceiver mTorchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String state = intent.getStringExtra(FlashLightConstants.EXTRA_CURRENT_STATE);
            mGlowTorchRunning = ((state != null) && (state.equals("1")));
        }
    };
}
