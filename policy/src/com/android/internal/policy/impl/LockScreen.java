/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.*;
import android.widget.ImageView.ScaleType;

import com.android.internal.R;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.InfoCallbackImpl;
import com.android.internal.policy.impl.KeyguardUpdateMonitor.SimStateCallback;
import com.android.internal.telephony.IccCard.State;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.SlidingTab;
import com.android.internal.widget.WaveView;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen {

    private static final int ON_RESUME_PING_DELAY = 500; // delay first ping until the screen is on
    private static final boolean DBG = false;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final int WAIT_FOR_ANIMATION_TIMEOUT = 0;
    private static final int STAY_ON_WHILE_GRABBED_TIMEOUT = 30000;
    private static final String ASSIST_ICON_METADATA_NAME =
            "com.android.systemui.action_assist_icon";

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    // set to 'true' to show the ring/silence target when camera isn't available
    private boolean mEnableRingSilenceFallback = false;

    // current configuration state of keyboard and display
    private int mCreationOrientation;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private boolean mEnableMenuKeyInLockScreen;

    private KeyguardStatusViewManager mStatusViewManager;
    private UnlockWidgetCommonMethods mUnlockWidgetMethods;
    private View mUnlockWidget;
    private boolean mCameraDisabled;
    private boolean mSearchDisabled;
    // Is there a vibrator
    private final boolean mHasVibrator;

    InfoCallbackImpl mInfoCallback = new InfoCallbackImpl() {

        @Override
        public void onRingerModeChanged(int state) {
            boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
            if (silent != mSilentMode) {
                mSilentMode = silent;
                mUnlockWidgetMethods.updateResources();
            }
        }

        @Override
        public void onDevicePolicyManagerStateChanged() {
            updateTargets();
        }

    };

    SimStateCallback mSimStateCallback = new SimStateCallback() {
        public void onSimStateChanged(State simState) {
            updateTargets();
        }
    };

    private interface UnlockWidgetCommonMethods {
        // Update resources based on phone state
        public void updateResources();

        // Get the view associated with this widget
        public View getView();

        // Reset the view
        public void reset(boolean animate);

        // Animate the widget if it supports ping()
        public void ping();

        // Enable or disable a target. ResourceId is the id of the *drawable* associated with the
        // target.
        public void setEnabled(int resourceId, boolean enabled);

        // Get the target position for the given resource. Returns -1 if not found.
        public int getTargetPosition(int resourceId);

        // Clean up when this widget is going away
        public void cleanUp();
    }

    class SlidingTabMethods implements SlidingTab.OnTriggerListener, UnlockWidgetCommonMethods {
        private final SlidingTab mSlidingTab;

        SlidingTabMethods(SlidingTab slidingTab) {
            mSlidingTab = slidingTab;
        }

        public void updateResources() {
            boolean vibe = mSilentMode
                && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

            mSlidingTab.setRightTabResources(
                    mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
                                         : R.drawable.ic_jog_dial_sound_off )
                                : R.drawable.ic_jog_dial_sound_on,
                    mSilentMode ? R.drawable.jog_tab_target_yellow
                                : R.drawable.jog_tab_target_gray,
                    mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                                : R.drawable.jog_tab_bar_right_sound_off,
                    mSilentMode ? R.drawable.jog_tab_right_sound_on
                                : R.drawable.jog_tab_right_sound_off);
        }

        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
                mCallback.goToUnlockScreen();
            } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                toggleRingMode();
                mCallback.pokeWakelock();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                mSilentMode = isSilentMode();
                mSlidingTab.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                        : R.string.lockscreen_sound_off_label);
            }
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mSlidingTab;
        }

        public void reset(boolean animate) {
            mSlidingTab.reset(animate);
        }

        public void ping() {
        }

        public void setEnabled(int resourceId, boolean enabled) {
            // Not used
        }

        public int getTargetPosition(int resourceId) {
            return -1; // Not supported
        }

        public void cleanUp() {
            mSlidingTab.setOnTriggerListener(null);
        }
    }

    class WaveViewMethods implements WaveView.OnTriggerListener, UnlockWidgetCommonMethods {

        private final WaveView mWaveView;

        WaveViewMethods(WaveView waveView) {
            mWaveView = waveView;
        }
        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == WaveView.OnTriggerListener.CENTER_HANDLE) {
                requestUnlockScreen();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState == WaveView.OnTriggerListener.CENTER_HANDLE) {
                mCallback.pokeWakelock(STAY_ON_WHILE_GRABBED_TIMEOUT);
            }
        }

        public void updateResources() {
        }

        public View getView() {
            return mWaveView;
        }
        public void reset(boolean animate) {
            mWaveView.reset();
        }
        public void ping() {
        }
        public void setEnabled(int resourceId, boolean enabled) {
            // Not used
        }
        public int getTargetPosition(int resourceId) {
            return -1; // Not supported
        }
        public void cleanUp() {
            mWaveView.setOnTriggerListener(null);
        }
    }

    class GlowPadViewMethods implements GlowPadView.OnTriggerListener,
            UnlockWidgetCommonMethods {
        private final GlowPadView mGlowPadView;
        private String[] mStoredTargets;
        private int mTargetOffset;
        private boolean mIsScreenLarge;

        GlowPadViewMethods(GlowPadView glowPadView) {
            mGlowPadView = glowPadView;
        }

        public boolean isScreenLarge() {
            final int screenSize = Resources.getSystem().getConfiguration().screenLayout &
                    Configuration.SCREENLAYOUT_SIZE_MASK;
            boolean isScreenLarge = screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE ||
                    screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
            return isScreenLarge;
        }

        private StateListDrawable getLayeredDrawable(Drawable back, Drawable front, int inset, boolean frontBlank) {
            Resources res = getResources();
            InsetDrawable[] inactivelayer = new InsetDrawable[2];
            InsetDrawable[] activelayer = new InsetDrawable[2];
            inactivelayer[0] = new InsetDrawable(res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_lock_pressed), 0, 0, 0, 0);
            inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
            activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
            activelayer[1] = new InsetDrawable(frontBlank ? res.getDrawable(android.R.color.transparent) : front, inset, inset, inset, inset);
            StateListDrawable states = new StateListDrawable();
            LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
            inactiveLayerDrawable.setId(0, 0);
            inactiveLayerDrawable.setId(1, 1);
            LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
            activeLayerDrawable.setId(0, 0);
            activeLayerDrawable.setId(1, 1);
            states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
            states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
            states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);
            return states;
        }

        public boolean isTargetPresent(int resId) {
            return mGlowPadView.getTargetPosition(resId) != -1;
        }

        public void updateResources() {
            String storedVal = Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_TARGETS);
            if (storedVal == null) {
                int resId;
                if (mCameraDisabled && mEnableRingSilenceFallback) {
                    // Fall back to showing ring/silence if camera is disabled...
                    resId = mSilentMode ? R.array.lockscreen_targets_when_silent
                            : R.array.lockscreen_targets_when_soundon;
                } else {
                    resId = R.array.lockscreen_targets_with_camera;
                }
                if (mGlowPadView.getTargetResourceId() != resId) {
                    mGlowPadView.setTargetResources(resId);
                }
                // Update the search icon with drawable from the search .apk
                if (!mSearchDisabled) {
                    Intent intent = SearchManager.getAssistIntent(mContext);
                    if (intent != null) {
                        // XXX Hack. We need to substitute the icon here but haven't formalized
                        // the public API. The "_google" metadata will be going away, so
                        // DON'T USE IT!
                        ComponentName component = intent.getComponent();
                        boolean replaced = mGlowPadView.replaceTargetDrawablesIfPresent(component,
                                ASSIST_ICON_METADATA_NAME + "_google",
                                com.android.internal.R.drawable.ic_action_assist_generic);

                        if (!replaced && !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                                    ASSIST_ICON_METADATA_NAME,
                                    com.android.internal.R.drawable.ic_action_assist_generic)) {
                                Slog.w(TAG, "Couldn't grab icon from package " + component);
                        }
                    }
                }
                setEnabled(com.android.internal.R.drawable.ic_lockscreen_camera, !mCameraDisabled);
                setEnabled(com.android.internal.R.drawable.ic_action_assist_generic, !mSearchDisabled);
            } else {
                mStoredTargets = storedVal.split("\\|");
                mIsScreenLarge = isScreenLarge();
                ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();
                final Resources res = getResources();
                final int targetInset = res.getDimensionPixelSize(com.android.internal.R.dimen.lockscreen_target_inset);
                final PackageManager packMan = mContext.getPackageManager();
                final boolean isLandscape = mCreationOrientation == Configuration.ORIENTATION_LANDSCAPE;
                final Drawable blankActiveDrawable = res.getDrawable(R.drawable.ic_lockscreen_target_activated);
                final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);
                // Shift targets for landscape lockscreen on phones
                mTargetOffset = isLandscape && !mIsScreenLarge ? 2 : 0;
                if (mTargetOffset == 2) {
                    storedDraw.add(new TargetDrawable(res, null));
                    storedDraw.add(new TargetDrawable(res, null));
                }
                // Add unlock target
                storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_lockscreen_unlock)));
                for (int i = 0; i < 8 - mTargetOffset - 1; i++) {
                    int tmpInset = targetInset;
                    if (i < mStoredTargets.length) {
                        String uri = mStoredTargets[i];
                        if (!uri.equals(GlowPadView.EMPTY_TARGET)) {
                            try {
                                Intent in = Intent.parseUri(uri,0);
                                Drawable front = null;
                                Drawable back = activeBack;
                                boolean frontBlank = false;
                                if (in.hasExtra(GlowPadView.ICON_FILE)) {
                                    String fSource = in.getStringExtra(GlowPadView.ICON_FILE);
                                    if (fSource != null) {
                                        File fPath = new File(fSource);
                                        if (fPath.exists()) {
                                            front = new BitmapDrawable(res, BitmapFactory.decodeFile(fSource));
                                        }
                                    }
                                } else if (in.hasExtra(GlowPadView.ICON_RESOURCE)) {
                                    String rSource = in.getStringExtra(GlowPadView.ICON_RESOURCE);
                                    String rPackage = in.getStringExtra(GlowPadView.ICON_PACKAGE);
                                    if (rSource != null) {
                                        if (rPackage != null) {
                                            try {
                                                Context rContext = mContext.createPackageContext(rPackage, 0);
                                                int id = rContext.getResources().getIdentifier(rSource, "drawable", rPackage);
                                                front = rContext.getResources().getDrawable(id);
                                                id = rContext.getResources().getIdentifier(rSource.replaceAll("_normal", "_activated"),
                                                        "drawable", rPackage);
                                                back = rContext.getResources().getDrawable(id);
                                                tmpInset = 0;
                                                frontBlank = true;
                                            } catch (NameNotFoundException e) {
                                                e.printStackTrace();
                                            } catch (NotFoundException e) {
                                                e.printStackTrace();
                                            }
                                        } else {
                                            front = res.getDrawable(res.getIdentifier(rSource, "drawable", "android"));
                                            back = res.getDrawable(res.getIdentifier(
                                                    rSource.replaceAll("_normal", "_activated"), "drawable", "android"));
                                            tmpInset = 0;
                                            frontBlank = true;
                                        }
                                    }
                                }
                                if (front == null || back == null) {
                                    ActivityInfo aInfo = in.resolveActivityInfo(packMan, PackageManager.GET_ACTIVITIES);
                                    if (aInfo != null) {
                                        front = aInfo.loadIcon(packMan);
                                    } else {
                                        front = res.getDrawable(android.R.drawable.sym_def_app_icon);
                                    }
                                }
                                TargetDrawable nDrawable = new TargetDrawable(res, getLayeredDrawable(back,front, tmpInset, frontBlank));
                                ComponentName compName = in.getComponent();
                                if (compName != null) {
                                    String cls = compName.getClassName();
                                    if (cls.equals("com.android.camera.CameraLauncher")) {
                                        nDrawable.setEnabled(!mCameraDisabled);
                                    } else if (cls.equals("SearchActivity")) {
                                        nDrawable.setEnabled(!mSearchDisabled);
                                    }
                                }
                                storedDraw.add(nDrawable);
                            } catch (Exception e) {
                                storedDraw.add(new TargetDrawable(res, 0));
                            }
                        } else {
                            storedDraw.add(new TargetDrawable(res, 0));
                        }
                    } else {
                        storedDraw.add(new TargetDrawable(res, 0));
                    }
                }
                mGlowPadView.setTargetResources(storedDraw);
            }
        }

        public void onGrabbed(View v, int handle) {

        }

        public void onReleased(View v, int handle) {

        }

        public void onTrigger(View v, int target) {
            if (mStoredTargets == null) {
                final int resId = mGlowPadView.getResourceIdForTarget(target);
                switch (resId) {
                case com.android.internal.R.drawable.ic_action_assist_generic:
                    Intent assistIntent = SearchManager.getAssistIntent(mContext);
                    if (assistIntent != null) {
                        launchActivity(assistIntent);
                    } else {
                        Log.w(TAG, "Failed to get intent for assist activity");
                    }
                    mCallback.pokeWakelock();
                    break;

                case com.android.internal.R.drawable.ic_lockscreen_camera:
                    launchActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    mCallback.pokeWakelock();
                    break;

                case com.android.internal.R.drawable.ic_lockscreen_silent:
                    toggleRingMode();
                    mCallback.pokeWakelock();
                    break;

                case com.android.internal.R.drawable.ic_lockscreen_unlock_phantom:
                case com.android.internal.R.drawable.ic_lockscreen_unlock:
                    mCallback.goToUnlockScreen();
                    break;
                }
            } else {
                final boolean isLand = mCreationOrientation == Configuration.ORIENTATION_LANDSCAPE;
                if ((target == 0 && (mIsScreenLarge || !isLand)) || (target == 2 && !mIsScreenLarge && isLand)) {
                    mCallback.goToUnlockScreen();
                } else {
                    target -= 1 + mTargetOffset;
                    if (target < mStoredTargets.length && mStoredTargets[target] != null) {
                        try {
                            launchActivity(Intent.parseUri(mStoredTargets[target], 0));
                        } catch (URISyntaxException e) {
                        }
                    }
                }
            }
        }

        private void launchActivity(Intent intent) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                mContext.startActivity(intent);
                mCallback.goToUnlockScreen();
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Activity not found for intent + " + intent.getAction());
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (handle != GlowPadView.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mGlowPadView;
        }

        public void reset(boolean animate) {
            mGlowPadView.reset(animate);
        }

        public void ping() {
            mGlowPadView.ping();
        }

        public void setEnabled(int resourceId, boolean enabled) {
            mGlowPadView.setEnableTarget(resourceId, enabled);
        }

        public int getTargetPosition(int resourceId) {
            return mGlowPadView.getTargetPosition(resourceId);
        }

        public void cleanUp() {
            mGlowPadView.setOnTriggerListener(null);
        }

        public void onFinishFinalAnimation() {

        }
    }

    private void requestUnlockScreen() {
        // Delay hiding lock screen long enough for animation to finish
        postDelayed(new Runnable() {
            public void run() {
                mCallback.goToUnlockScreen();
            }
        }, WAIT_FOR_ANIMATION_TIMEOUT);
    }

    private void toggleRingMode() {
        // toggle silent mode
        mSilentMode = !mSilentMode;
        if (mSilentMode) {
            mAudioManager.setRingerMode(mHasVibrator
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isTestHarness = ActivityManager.isRunningInTestHarness();
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        final boolean menuOverride = Settings.System.getInt(getContext().getContentResolver(), Settings.System.MENU_UNLOCK_SCREEN, 0) == 1;
        return !configDisabled || isTestHarness || fileOverride || menuOverride;
    }

    boolean mHomeUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.HOME_UNLOCK_SCREEN, 0) == 1);

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;
        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();
        mCreationOrientation = configuration.orientation;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
        if (mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land, this, true);
        }

        setBackground(mContext, (ViewGroup) findViewById(R.id.root));

        mStatusViewManager = new KeyguardStatusViewManager(this, mUpdateMonitor, mLockPatternUtils,
                mCallback, false);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator == null ? false : vibrator.hasVibrator();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();
        mUnlockWidget = findViewById(R.id.unlock_widget);
        mUnlockWidgetMethods = createUnlockMethods(mUnlockWidget);

        if (DBG) Log.v(TAG, "*** LockScreen accel is "
                + (mUnlockWidget.isHardwareAccelerated() ? "on":"off"));
    }

    private UnlockWidgetCommonMethods createUnlockMethods(View unlockWidget) {
        if (unlockWidget instanceof SlidingTab) {
            SlidingTab slidingTabView = (SlidingTab) unlockWidget;
            slidingTabView.setHoldAfterTrigger(true, false);
            slidingTabView.setLeftHintText(R.string.lockscreen_unlock_label);
            slidingTabView.setLeftTabResources(
                    R.drawable.ic_jog_dial_unlock,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_tab_bar_left_unlock,
                    R.drawable.jog_tab_left_unlock);
            SlidingTabMethods slidingTabMethods = new SlidingTabMethods(slidingTabView);
            slidingTabView.setOnTriggerListener(slidingTabMethods);
            return slidingTabMethods;
        } else if (unlockWidget instanceof WaveView) {
            WaveView waveView = (WaveView) unlockWidget;
            WaveViewMethods waveViewMethods = new WaveViewMethods(waveView);
            waveView.setOnTriggerListener(waveViewMethods);
            return waveViewMethods;
        } else if (unlockWidget instanceof GlowPadView) {
            GlowPadView glowPadView = (GlowPadView) unlockWidget;
            GlowPadViewMethods glowPadViewMethods = new GlowPadViewMethods(glowPadView);
            glowPadView.setOnTriggerListener(glowPadViewMethods);
            return glowPadViewMethods;
        } else {
            throw new IllegalStateException("Unrecognized unlock widget: " + unlockWidget);
        }
    }

    private void updateTargets() {
        boolean disabledByAdmin = mLockPatternUtils.getDevicePolicyManager()
                .getCameraDisabled(null);
        boolean disabledBySimState = mUpdateMonitor.isSimLocked();
        boolean cameraPresent = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean searchTargetPresent = (mUnlockWidgetMethods instanceof GlowPadViewMethods)
                ? ((GlowPadViewMethods) mUnlockWidgetMethods)
                        .isTargetPresent(com.android.internal.R.drawable.ic_action_assist_generic)
                        : false;

        if (disabledByAdmin) {
            Log.v(TAG, "Camera disabled by Device Policy");
        } else if (disabledBySimState) {
            Log.v(TAG, "Camera disabled by Sim State");
        }
        boolean searchActionAvailable = SearchManager.getAssistIntent(mContext) != null;
        mCameraDisabled = disabledByAdmin || disabledBySimState || !cameraPresent;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent;
        mUnlockWidgetMethods.updateResources();
    }

    static void setBackground(Context context, ViewGroup layout) {
        String lockBack = Settings.System.getString(context.getContentResolver(), Settings.System.LOCKSCREEN_BACKGROUND);
        if (lockBack != null) {
            if (!lockBack.isEmpty()) {
                try {
                    layout.setBackgroundColor(Integer.parseInt(lockBack));
                } catch(NumberFormatException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    ViewParent parent =  layout.getParent();
                    if (parent != null) {
                        //change parent to show background correctly on scale
                        RelativeLayout rlout = new RelativeLayout(context);
                        ((ViewGroup) parent).removeView(layout);
                        layout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        ((ViewGroup) parent).addView(rlout); // change parent to new layout
                        rlout.addView(layout);
                        // create framelayout and add imageview to set background
                        FrameLayout flayout = new FrameLayout(context);
                        flayout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        ImageView mLockScreenWallpaperImage = new ImageView(flayout.getContext());
                        mLockScreenWallpaperImage.setScaleType(ScaleType.CENTER_CROP);
                        flayout.addView(mLockScreenWallpaperImage, -1, -1);
                        Context settingsContext = context.createPackageContext("com.android.settings", 0);
                        String wallpaperFile = settingsContext.getFilesDir() + "/lockwallpaper";
                        Bitmap background = BitmapFactory.decodeFile(wallpaperFile);
                        Drawable d = new BitmapDrawable(context.getResources(), background);
                        mLockScreenWallpaperImage.setImageDrawable(d);
                        // add background to lock screen.
                        rlout.addView(flayout,0);
                    }
                } catch (NameNotFoundException e) {
                }
            }
        }
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_MENU) {
            event.startTracking();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen) ||
            (keyCode == KeyEvent.KEYCODE_HOME && mHomeUnlockScreen)) {
            mCallback.goToUnlockScreen();
        }
        return false;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (handleKeyLongPress(mContext, keyCode)) {
            mCallback.pokeWakelock();
            return true;
        }
        return false;
    }

    private static final int ACTION_RESULT_RUN = 0;
    private static final int ACTION_RESULT_NOTRUN = 1;

    private static int runAction(Context context, String uri) {
        if ("FLASHLIGHT".equals(uri)) {
            context.sendBroadcast(new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT"));
            return ACTION_RESULT_RUN;
        } else if ("NEXT".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT);
            return ACTION_RESULT_RUN;
        } else if ("PREVIOUS".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return ACTION_RESULT_RUN;
        } else if ("PLAYPAUSE".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return ACTION_RESULT_RUN;
        } else if ("SOUND".equals(uri)) {
            toggleSilentMode(context);
            return ACTION_RESULT_RUN;
        }

        return ACTION_RESULT_NOTRUN;
    }

    public static boolean handleKeyLongPress(Context context, int keyCode) {
        String action = null;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                action = Settings.System.LOCKSCREEN_LONG_BACK_ACTION;
                break;
            case KeyEvent.KEYCODE_HOME:
                action = Settings.System.LOCKSCREEN_LONG_HOME_ACTION;
                break;
            case KeyEvent.KEYCODE_MENU:
                action = Settings.System.LOCKSCREEN_LONG_MENU_ACTION;
                break;
        }

        if (action != null) {
            String uri = Settings.System.getString(context.getContentResolver(), action);
            if (uri != null && runAction(context, uri) != ACTION_RESULT_NOTRUN) {
                return true;
            }
        }

        return false;
    }

    private static void sendMediaButtonEvent(Context context, int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        context.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        context.sendOrderedBroadcast(upIntent, null);
    }

    private static void toggleSilentMode(Context context) {
        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        final boolean hasVib = vib == null ? false : vib.hasVibrator();
        if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            am.setRingerMode(hasVib
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean suspendRecreate() {
        return false;
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {
        mUpdateMonitor.removeCallback(mInfoCallback);
        mUpdateMonitor.removeCallback(mSimStateCallback);
        mStatusViewManager.onPause();
        mUnlockWidgetMethods.reset(false);
    }

    private final Runnable mOnResumePing = new Runnable() {
        public void run() {
            mUnlockWidgetMethods.ping();
        }
    };

    /** {@inheritDoc} */
    public void onResume() {
        // We don't want to show the camera target if SIM state prevents us from
        // launching the camera. So watch for SIM changes...
        mUpdateMonitor.registerSimStateCallback(mSimStateCallback);
        mUpdateMonitor.registerInfoCallback(mInfoCallback);

        mStatusViewManager.onResume();
        postDelayed(mOnResumePing, ON_RESUME_PING_DELAY);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(mInfoCallback); // this must be first
        mUpdateMonitor.removeCallback(mSimStateCallback);
        mUnlockWidgetMethods.cleanUp();
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }
}
