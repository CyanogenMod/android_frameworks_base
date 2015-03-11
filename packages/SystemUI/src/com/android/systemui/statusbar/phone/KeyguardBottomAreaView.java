/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.util.cm.LockscreenShortcutsHelper;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.EmergencyButton;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.policy.AccessibilityController;
import com.android.systemui.statusbar.policy.PreviewInflater;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

import java.util.List;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

/**
 * Implementation for the bottom area of the Keyguard, including camera/phone affordance and status
 * text.
 */
public class KeyguardBottomAreaView extends FrameLayout implements View.OnClickListener,
        UnlockMethodCache.OnUnlockMethodChangedListener,
        AccessibilityController.AccessibilityStateChangedCallback, View.OnLongClickListener,
        LockscreenShortcutsHelper.OnChangeListener {

    final static String TAG = "PhoneStatusBar/KeyguardBottomAreaView";

    private static final boolean DEBUG = false;

    private static final Intent SECURE_CAMERA_INTENT =
            new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                    .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    private static final Intent INSECURE_CAMERA_INTENT =
            new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
    private static final Intent PHONE_INTENT = new Intent(Intent.ACTION_DIAL);

    // the length to animate the visualizer in and out
    private static final int VISUALIZER_ANIMATION_DURATION = 300;

    private KeyguardAffordanceView mCameraImageView;
    private KeyguardAffordanceView mPhoneImageView;
    private KeyguardAffordanceView mLockIcon;
    private TextView mIndicationText;
    private EmergencyButton mEmergencyButton;
    private ViewGroup mPreviewContainer;

    private View mPhonePreview;
    private View mCameraPreview;

    private ActivityStarter mActivityStarter;
    private UnlockMethodCache mUnlockMethodCache;
    private LockPatternUtils mLockPatternUtils;
    private PreviewInflater mPreviewInflater;
    private KeyguardIndicationController mIndicationController;
    private AccessibilityController mAccessibilityController;
    private PhoneStatusBar mPhoneStatusBar;
    private LockscreenShortcutsHelper mShortcutHelper;

    private final TrustDrawable mTrustDrawable;

    private int mLastUnlockIconRes = 0;

    private VisualizerView mVisualizer;
    private boolean mScreenOn;
    private boolean mLinked;
    private boolean mVisualizerEnabled;
    private boolean mPowerSaveModeEnabled;
    private SettingsObserver mSettingsObserver;

    public KeyguardBottomAreaView(Context context) {
        this(context, null);
    }

    public KeyguardBottomAreaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardBottomAreaView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyguardBottomAreaView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mTrustDrawable = new TrustDrawable(mContext);
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    private AccessibilityDelegate mAccessibilityDelegate = new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            String label = null;
            if (host == mLockIcon) {
                label = getResources().getString(R.string.unlock_label);
            } else if (host == mCameraImageView) {
                label = getResources().getString(R.string.camera_label);
            } else if (host == mPhoneImageView) {
                label = getResources().getString(R.string.phone_label);
            }
            info.addAction(new AccessibilityAction(ACTION_CLICK, label));
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (action == ACTION_CLICK) {
                if (host == mLockIcon) {
                    mPhoneStatusBar.animateCollapsePanels(
                            CommandQueue.FLAG_EXCLUDE_NONE, true /* force */);
                    return true;
                } else if (host == mCameraImageView) {
                    launchCamera();
                    return true;
                } else if (host == mPhoneImageView) {
                    launchPhone();
                    return true;
                }
            }
            return super.performAccessibilityAction(host, action, args);
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPreviewContainer = (ViewGroup) findViewById(R.id.preview_container);
        mCameraImageView = (KeyguardAffordanceView) findViewById(R.id.camera_button);
        mPhoneImageView = (KeyguardAffordanceView) findViewById(R.id.phone_button);
        mLockIcon = (KeyguardAffordanceView) findViewById(R.id.lock_icon);
        mIndicationText = (TextView) findViewById(R.id.keyguard_indication_text);
        mEmergencyButton = (EmergencyButton) findViewById(R.id.emergency_call_button);
        mShortcutHelper = new LockscreenShortcutsHelper(mContext, this);
        watchForCameraPolicyChanges();
        updateCameraVisibility();
        updatePhoneVisibility();
        mUnlockMethodCache = UnlockMethodCache.getInstance(getContext());
        mUnlockMethodCache.addListener(this);
        updateLockIcon();
        updateEmergencyButton();
        setClipChildren(false);
        setClipToPadding(false);
        mPreviewInflater = new PreviewInflater(mContext, new LockPatternUtils(mContext));
        inflatePreviews();
        mLockIcon.setOnClickListener(this);
        mLockIcon.setBackground(mTrustDrawable);
        mLockIcon.setOnLongClickListener(this);
        mCameraImageView.setOnClickListener(this);
        mPhoneImageView.setOnClickListener(this);
        if (ActivityManager.isHighEndGfx()) {
            mVisualizer = (VisualizerView) findViewById(R.id.visualizerView);
            if (mVisualizer != null) {
                Paint paint = new Paint();
                Resources res = mContext.getResources();
                paint.setStrokeWidth(res.getDimensionPixelSize(
                        R.dimen.kg_visualizer_path_stroke_width));
                paint.setAntiAlias(true);
                paint.setColor(res.getColor(R.color.equalizer_fill_color));
                paint.setPathEffect(new DashPathEffect(new float[] {
                        res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_1),
                        res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_2)
                }, 0));

                int bars = res.getInteger(R.integer.kg_visualizer_divisions);
                mVisualizer.addRenderer(new LockscreenBarEqRenderer(bars, paint,
                        res.getInteger(R.integer.kg_visualizer_db_fuzz),
                        res.getInteger(R.integer.kg_visualizer_db_fuzz_factor)));
            }
        }

        initAccessibility();
        updateCustomShortcuts();
    }

    private void updateCustomShortcuts() {
        KeyguardAffordanceView[] targets = new KeyguardAffordanceView[] {
                mPhoneImageView, mCameraImageView};
        List<LockscreenShortcutsHelper.TargetInfo> items = mShortcutHelper.getDrawablesForTargets();
        for (int i = 0; i < targets.length; i++) {
            LockscreenShortcutsHelper.TargetInfo item = items.get(i);
            KeyguardAffordanceView v = targets[i];
            v.setDefaultFilter(item.colorFilter);
            v.setImageDrawable(getScaledDrawable(item.icon));
        }
        updateCameraVisibility();
        updatePhoneVisibility();
    }

    private Drawable getScaledDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Resources res = mContext.getResources();
            int width = res.getDimensionPixelSize(R.dimen.keyguard_affordance_icon_width);
            int height = res.getDimensionPixelSize(R.dimen.keyguard_affordance_icon_height);
            return new BitmapDrawable(mContext.getResources(),
                    Bitmap.createScaledBitmap(((BitmapDrawable) drawable).getBitmap(),
                            width, height, true));
        } else {
            return drawable;
        }
    }

    private void initAccessibility() {
        mLockIcon.setAccessibilityDelegate(mAccessibilityDelegate);
        mPhoneImageView.setAccessibilityDelegate(mAccessibilityDelegate);
        mCameraImageView.setAccessibilityDelegate(mAccessibilityDelegate);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int indicationBottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_indication_margin_bottom);
        MarginLayoutParams mlp = (MarginLayoutParams) mIndicationText.getLayoutParams();
        if (mlp.bottomMargin != indicationBottomMargin) {
            mlp.bottomMargin = indicationBottomMargin;
            mIndicationText.setLayoutParams(mlp);
        }

        // Respect font size setting.
        mIndicationText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material));

        updateEmergencyButton();
    }

    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    public void setAccessibilityController(AccessibilityController accessibilityController) {
        mAccessibilityController = accessibilityController;
        accessibilityController.addStateChangedCallback(this);
    }

    public void setPhoneStatusBar(PhoneStatusBar phoneStatusBar) {
        mPhoneStatusBar = phoneStatusBar;
    }

    private Intent getCameraIntent() {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        boolean currentUserHasTrust = updateMonitor.getUserHasTrust(
                mLockPatternUtils.getCurrentUser());
        return mLockPatternUtils.isSecure() && !currentUserHasTrust
                ? SECURE_CAMERA_INTENT : INSECURE_CAMERA_INTENT;
    }

    private void updateCameraVisibility() {
        ResolveInfo resolved = mContext.getPackageManager().resolveActivityAsUser(getCameraIntent(),
                PackageManager.MATCH_DEFAULT_ONLY,
                mLockPatternUtils.getCurrentUser());
        boolean visible = !isCameraDisabledByDpm() && resolved != null
                && getResources().getBoolean(R.bool.config_keyguardShowCameraAffordance);
        visible = updateVisibilityCheck(visible,
                LockscreenShortcutsHelper.Shortcuts.RIGHT_SHORTCUT);
        mCameraImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean updateVisibilityCheck(boolean visible, LockscreenShortcutsHelper.Shortcuts
            shortcut) {
        boolean customTarget = mShortcutHelper.isTargetCustom(shortcut);
        if (customTarget) {
            if (isProtected(mShortcutHelper.getIntent(shortcut))) {
                return false;
            }
        } else if (shortcut == LockscreenShortcutsHelper.Shortcuts.LEFT_SHORTCUT
                && isProtected(PHONE_INTENT)) {
            // is dialer protected?
            return false;
        } else if (shortcut == LockscreenShortcutsHelper.Shortcuts.RIGHT_SHORTCUT
                && isProtected(getCameraIntent())) {
            // is camera protected?
            return false;
        }

        if (customTarget) {
            boolean isEmpty = mShortcutHelper.isTargetEmpty(shortcut);
            if (visible && isEmpty) {
                visible = false;
            } else {
                visible = true;
            }
        }
        return visible;
    }

    private boolean isProtected(Intent intent) {
        ResolveInfo resolved = mContext.getPackageManager().resolveActivityAsUser(intent,
                PackageManager.MATCH_DEFAULT_ONLY,
                mLockPatternUtils.getCurrentUser());
        if (resolved != null) {
            try {
                boolean protect = mContext.getPackageManager().getApplicationInfo(
                        resolved.activityInfo.packageName, 0).protect;
                return protect;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        return false;
    }

    private void updatePhoneVisibility() {
        boolean visible = isPhoneVisible();
        visible = updateVisibilityCheck(visible,
                LockscreenShortcutsHelper.Shortcuts.LEFT_SHORTCUT);
        mPhoneImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean isPhoneVisible() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && pm.resolveActivity(PHONE_INTENT, 0) != null;
    }

    private boolean isCameraDisabledByDpm() {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            try {
                final int userId = ActivityManagerNative.getDefault().getCurrentUser().id;
                final int disabledFlags = dpm.getKeyguardDisabledFeatures(null, userId);
                final  boolean disabledBecauseKeyguardSecure =
                        (disabledFlags & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0
                                && KeyguardTouchDelegate.getInstance(getContext()).isSecure();
                return dpm.getCameraDisabled(null) || disabledBecauseKeyguardSecure;
            } catch (RemoteException e) {
                Log.e(TAG, "Can't get userId", e);
            }
        }
        return false;
    }

    private void watchForCameraPolicyChanges() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        getContext().registerReceiverAsUser(mDevicePolicyReceiver,
                UserHandle.ALL, filter, null, null);
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    public void onStateChanged(boolean accessibilityEnabled, boolean touchExplorationEnabled) {
        mCameraImageView.setClickable(touchExplorationEnabled);
        mPhoneImageView.setClickable(touchExplorationEnabled);
        mCameraImageView.setFocusable(accessibilityEnabled);
        mPhoneImageView.setFocusable(accessibilityEnabled);
        updateLockIconClickability();
    }

    private void updateLockIconClickability() {
        if (mAccessibilityController == null) {
            return;
        }
        boolean clickToUnlock = mAccessibilityController.isTouchExplorationEnabled();
        boolean clickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !mAccessibilityController.isAccessibilityEnabled();
        boolean longClickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !clickToForceLock;
        mLockIcon.setClickable(clickToForceLock || clickToUnlock);
        mLockIcon.setLongClickable(longClickToForceLock);
        mLockIcon.setFocusable(mAccessibilityController.isAccessibilityEnabled());
    }

    @Override
    public void onClick(View v) {
        if (v == mCameraImageView) {
            launchCamera();
        } else if (v == mPhoneImageView) {
            launchPhone();
        } if (v == mLockIcon) {
            if (!mAccessibilityController.isAccessibilityEnabled()) {
                handleTrustCircleClick();
            } else {
                mPhoneStatusBar.animateCollapsePanels(
                        CommandQueue.FLAG_EXCLUDE_NONE, true /* force */);
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        handleTrustCircleClick();
        return true;
    }

    private void handleTrustCircleClick() {
        mIndicationController.showTransientIndication(
                R.string.keyguard_indication_trust_disabled);
        mLockPatternUtils.requireCredentialEntry(mLockPatternUtils.getCurrentUser());
    }

    public void launchCamera() {
        Intent intent;
        if (!mShortcutHelper.isTargetCustom(LockscreenShortcutsHelper.Shortcuts.RIGHT_SHORTCUT)) {
            intent = getCameraIntent();
        } else {
            intent = mShortcutHelper.getIntent(LockscreenShortcutsHelper.Shortcuts.RIGHT_SHORTCUT);
        }
        boolean wouldLaunchResolverActivity = PreviewInflater.wouldLaunchResolverActivity(
                mContext, intent, mLockPatternUtils.getCurrentUser());
        if (intent == SECURE_CAMERA_INTENT && !wouldLaunchResolverActivity) {
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } else {

            // We need to delay starting the activity because ResolverActivity finishes itself if
            // launched behind lockscreen.
            mActivityStarter.startActivity(intent, false /* dismissShade */);
        }
    }

    public void launchPhone() {
        if (!mShortcutHelper.isTargetCustom(LockscreenShortcutsHelper.Shortcuts.LEFT_SHORTCUT)) {
            final TelecomManager tm = TelecomManager.from(mContext);
            if (tm.isInCall()) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        tm.showInCallScreen(false /* showDialpad */);
                    }
                });
            } else {
                mActivityStarter.startActivity(PHONE_INTENT, false /* dismissShade */);
            }
        } else {
                Intent intent = mShortcutHelper.getIntent(
                        LockscreenShortcutsHelper.Shortcuts.LEFT_SHORTCUT);
                mActivityStarter.startActivity(intent, false /* dismissShade */);
        }
    }


    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isShown()) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
            requestVisualizer(false, 0);
        }
        if (changedView == this && visibility == VISIBLE) {
            updateLockIcon();
            updateCameraVisibility();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContext.registerReceiver(mReceiver, new IntentFilter(
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mReceiver);
        mTrustDrawable.stop();
        requestVisualizer(false, 0);
    }

    private void updateLockIcon() {
        boolean visible = isShown() && KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        if (visible) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
        }
        if (!visible) {
            return;
        }
        // TODO: Real icon for facelock.
        int iconRes = mUnlockMethodCache.isFaceUnlockRunning()
                ? com.android.internal.R.drawable.ic_account_circle
                : mUnlockMethodCache.isMethodInsecure() ? R.drawable.ic_lock_open_24dp
                : R.drawable.ic_lock_24dp;
        if (mLastUnlockIconRes != iconRes) {
            Drawable icon = mContext.getDrawable(iconRes);
            int iconHeight = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_height);
            int iconWidth = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_width);
            if (icon.getIntrinsicHeight() != iconHeight || icon.getIntrinsicWidth() != iconWidth) {
                icon = new IntrinsicSizeDrawable(icon, iconWidth, iconHeight);
            }
            mLockIcon.setImageDrawable(icon);
        }
        boolean trustManaged = mUnlockMethodCache.isTrustManaged();
        mTrustDrawable.setTrustManaged(trustManaged);
        updateLockIconClickability();
    }

    private String getIndexHint(LockscreenShortcutsHelper.Shortcuts shortcut) {
        if (mShortcutHelper.isTargetCustom(shortcut)) {
            boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            String label = mShortcutHelper.getFriendlyNameForUri(shortcut);
            int resId = 0;
            switch (shortcut) {
                case LEFT_SHORTCUT:
                    resId = isRtl ? R.string.right_shortcut_hint : R.string.left_shortcut_hint;
                    break;
                case RIGHT_SHORTCUT:
                    resId = isRtl ? R.string.left_shortcut_hint : R.string.right_shortcut_hint;
                    break;
            }
            return mContext.getString(resId, label);
        } else {
            return null;
        }
    }

    public String getLeftHint() {
        String label = getIndexHint(LockscreenShortcutsHelper.Shortcuts.LEFT_SHORTCUT);
        if (label == null) {
            label = mContext.getString(R.string.phone_hint);
        }
        return label;
    }

    public String getRightHint() {
        String label = getIndexHint(LockscreenShortcutsHelper.Shortcuts.RIGHT_SHORTCUT);
        if (label == null) {
            label = mContext.getString(R.string.camera_hint);
        }
        return label;
    }

    public KeyguardAffordanceView getPhoneView() {
        return mPhoneImageView;
    }

    public KeyguardAffordanceView getCameraView() {
        return mCameraImageView;
    }

    public View getPhonePreview() {
        return mPhonePreview;
    }

    public View getCameraPreview() {
        return mCameraPreview;
    }

    public KeyguardAffordanceView getLockIcon() {
        return mLockIcon;
    }

    public View getIndicationView() {
        return mIndicationText;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onMethodSecureChanged(boolean methodSecure) {
        updateLockIcon();
        updateCameraVisibility();
    }

    private void inflatePreviews() {
        mPhonePreview = mPreviewInflater.inflatePreview(PHONE_INTENT);
        mCameraPreview = mPreviewInflater.inflatePreview(getCameraIntent());
        if (mPhonePreview != null) {
            mPreviewContainer.addView(mPhonePreview);
            mPhonePreview.setVisibility(View.INVISIBLE);
        }
        if (mCameraPreview != null) {
            mPreviewContainer.addView(mCameraPreview);
            mCameraPreview.setVisibility(View.INVISIBLE);
        }
    }

    private void updateEmergencyButton() {
        boolean enabled = getResources().getBoolean(R.bool.config_showEmergencyButton);
        if (mEmergencyButton != null) {
            mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyButton, enabled, false);
        }
    }

    private final BroadcastReceiver mDevicePolicyReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            post(new Runnable() {
                @Override
                public void run() {
                    updateCameraVisibility();
                }
            });
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                removeCallbacks(mStartVisualizer);
                removeCallbacks(mStopVisualizer);
                post(mPowerSaveModeEnabled ? mStopVisualizer : mStartVisualizer);
            }
        }
    };

    private final KeyguardUpdateMonitorCallback mUpdateMonitorCallback =
            new KeyguardUpdateMonitorCallback() {
        @Override
        public void onUserSwitchComplete(int userId) {
            updateCameraVisibility();
        }

        @Override
        public void onScreenTurnedOn() {
            mScreenOn = true;
            updateLockIcon();
            requestVisualizer(true, 300);
        }

        @Override
        public void onScreenTurnedOff(int why) {
            mScreenOn = false;
            updateLockIcon();
            requestVisualizer(false, 0);
        }
    };

    public void setKeyguardIndicationController(
            KeyguardIndicationController keyguardIndicationController) {
        mIndicationController = keyguardIndicationController;
    }

    public boolean isTargetCustom(LockscreenShortcutsHelper.Shortcuts shortcut) {
        return mShortcutHelper.isTargetCustom(shortcut);
    }

    @Override
    public void onChange() {
        updateCustomShortcuts();
    }

    /**
     * A wrapper around another Drawable that overrides the intrinsic size.
     */
    private static class IntrinsicSizeDrawable extends InsetDrawable {

        private final int mIntrinsicWidth;
        private final int mIntrinsicHeight;

        public IntrinsicSizeDrawable(Drawable drawable, int intrinsicWidth, int intrinsicHeight) {
            super(drawable, 0);
            mIntrinsicWidth = intrinsicWidth;
            mIntrinsicHeight = intrinsicHeight;
        }

        @Override
        public int getIntrinsicWidth() {
            return mIntrinsicWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mIntrinsicHeight;
        }
    }

    public void requestVisualizer(boolean show, int delay) {
        if (mVisualizer == null || !mVisualizerEnabled || mPowerSaveModeEnabled) {
            return;
        }
        removeCallbacks(mStartVisualizer);
        removeCallbacks(mStopVisualizer);
        if (DEBUG) Log.d(TAG, "requestVisualizer(show: " + show + ", delay: " + delay + ")");
        if (show && mScreenOn
                && mPhoneStatusBar.getBarState() == StatusBarState.KEYGUARD
                && !mPhoneStatusBar.isKeyguardFadingAway()
                && !mPhoneStatusBar.isGoingToNotificationShade()
                && mPhoneStatusBar.getCurrentMediaNotificationKey() != null) {
            if (DEBUG) Log.d(TAG, "--> starting visualizer");
            postDelayed(mStartVisualizer, delay);
        } else {
            if (DEBUG) Log.d(TAG, "--> stopping visualizer");
            postDelayed(mStopVisualizer, delay);
        }
    }

    private static class LockscreenBarEqRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

        /**
         * Renders the FFT data as a series of lines, in histogram form
         *
         * @param divisions - must be a power of 2. Controls how many lines to draw
         * @param paint - Paint to draw lines with
         * @param dbfuzz - final dB display adjustment
         * @param dbFactor - dbfuzz is multiplied by dbFactor.
         */
        public LockscreenBarEqRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
            super();
            if (DEBUG) {
                Log.d(TAG, "Lockscreen EQ Renderer; divisions:" + divisions + ", dbfuzz: "
                        + dbfuzz + "dbFactor: " + dbFactor);
            }
            mDivisions = divisions;
            mPaint = paint;
            mDbFuzz = dbfuzz;
            mDbFuzzFactor = dbFactor;
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect) {
            // Do nothing, we only display FFT data
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = data.bytes[mDivisions * i];
                byte ifk = data.bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - ((dbValue * mDbFuzzFactor) + mDbFuzz);
            }

            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    private final Runnable mStartVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStartVisualizer");

            mVisualizer.animate()
                    .alpha(1f)
                    .setDuration(VISUALIZER_ANIMATION_DURATION);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (mVisualizer != null && !mLinked) {
                        mVisualizer.link(0);
                        mLinked = true;
                    }
                }
            });
        }
    };

    private final Runnable mStopVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStopVisualizer");

            mVisualizer.animate()
                    .alpha(0f)
                    .setDuration(VISUALIZER_ANIMATION_DURATION);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (mVisualizer != null && mLinked) {
                        mVisualizer.unlink();
                        mLinked = false;
                    }
                }
            });
        }
    };

    private class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mVisualizerEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED, 1, UserHandle.USER_CURRENT) != 0;

        }
    }
}
