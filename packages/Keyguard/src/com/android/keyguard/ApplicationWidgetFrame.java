/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class ApplicationWidgetFrame extends KeyguardWidgetFrame implements View.OnClickListener {
    private static final String TAG = ApplicationWidgetFrame.class.getSimpleName();
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final int WIDGET_ANIMATION_DURATION = 250; // ms
    private static final int WIDGET_WAIT_DURATION = 400; // ms

    interface Callbacks {
        void onLaunchingApplicationWidgetContainer();
        void onApplicationWidgetContainerLaunchedSuccessfully();
        void onApplicationWidgetContainerLaunchedUnsuccessfully();
    }

    private final Handler mHandler = new Handler();
    private final KeyguardActivityLauncher mActivityLauncher;
    private final Callbacks mCallbacks;
    private final WindowManager mWindowManager;
    private final Point mRenderedSize = new Point();
    private final int[] mTmpLoc = new int[2];

    private long mLaunchApplicationWidgetContainerStart;
    private boolean mActive;
    private boolean mTransitioning;
    private boolean mDown;

    private final Rect mInsets = new Rect();

    private FixedSizeFrameLayout mPreview;
    private View mFullscreenPreview;
    private View mFakeNavBar;
    private boolean mUseFastTransition;
    private String mApplicationWidgetPackageName;

    private final Runnable mTransitionToApplicationWidgetContainerRunnable = new Runnable() {
        @Override
        public void run() {
            transitionToApplicationWidgetContainer();
        }};

    private final Runnable mTransitionToApplicationWidgetContainerEndAction = new Runnable() {
        @Override
        public void run() {
            if (!mTransitioning) {
                return;
            }
            Handler worker =  getWorkerHandler() != null ? getWorkerHandler() : mHandler;
            mLaunchApplicationWidgetContainerStart = SystemClock.uptimeMillis();
            if (DEBUG) Log.d(TAG, "Launching ApplicationWidget at " +
                    mLaunchApplicationWidgetContainerStart);
            mActivityLauncher.launchApplicationWidget(worker, null, mApplicationWidgetPackageName);
        }};

    private final Runnable mPosttransitionToApplicationWidgetContainerEndAction = new Runnable() {
        @Override
        public void run() {
            mHandler.post(mTransitionToApplicationWidgetContainerEndAction);
        }};

    private final Runnable mRecoverRunnable = new Runnable() {
        @Override
        public void run() {
            recover();
        }};

    private final Runnable mRenderRunnable = new Runnable() {
        @Override
        public void run() {
            render();
        }};

    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        private boolean mShowing;
        void onKeyguardVisibilityChanged(boolean showing) {
            if (mShowing == showing) {
                return;
            }
            mShowing = showing;
            ApplicationWidgetFrame.this.onKeyguardVisibilityChanged(mShowing);
        };

        void onApplicationWidgetUpdated(String packageName, byte[] icon) {
            setApplicationWidgetPackageName(packageName);
            updatePreviewImage(icon);
        }
    };

    private static final class FixedSizeFrameLayout extends FrameLayout {
        int width;
        int height;

        FixedSizeFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }
    }

    private ApplicationWidgetFrame(Context context, Callbacks callbacks,
            KeyguardActivityLauncher activityLauncher, View previewWidget) {
        super(context);
        mCallbacks = callbacks;
        mActivityLauncher = activityLauncher;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        KeyguardUpdateMonitor.getInstance(context).registerCallback(mCallback);

        mPreview = new FixedSizeFrameLayout(context);
        mPreview.addView(previewWidget);
        addView(mPreview);

        View clickBlocker = new View(context);
        clickBlocker.setBackgroundColor(Color.TRANSPARENT);
        clickBlocker.setOnClickListener(this);
        addView(clickBlocker);

        setContentDescription(context.getString(R.string.keyguard_accessibility_camera));
        if (DEBUG) Log.d(TAG, "new ApplicationWidget instance " + instanceId());
    }

    public void updatePreviewImage(byte[] icon) {
        ImageView child = (ImageView) mPreview.getChildAt(0);
        child.setImageBitmap(BitmapFactory.decodeByteArray(icon, 0, icon.length));
    }

    public static ApplicationWidgetFrame create(Context context, Callbacks callbacks,
            KeyguardActivityLauncher launcher) {
        if (context == null || callbacks == null || launcher == null) {
            return null;
        }

        View previewWidget = getPreviewWidget(context);
        if (previewWidget == null) {
            return null;
        }

        return new ApplicationWidgetFrame(context, callbacks, launcher, previewWidget);
    }

    private static View getPreviewWidget(Context context) {
        return inflateGenericWidgetView(context);
    }

    private static View inflateGenericWidgetView(Context context) {
        if (DEBUG) Log.d(TAG, "inflateGenericWidgetView");
        ImageView iv = new ImageView(context);
        iv.setScaleType(ScaleType.CENTER);
        iv.setBackgroundColor(Color.argb(127, 0, 0, 0));
        return iv;
    }

    private void render() {
        final View root = getRootView();
        final int width = root.getWidth() - mInsets.right;    // leave room
        final int height = root.getHeight() - mInsets.bottom; // for bars
        if (mRenderedSize.x == width && mRenderedSize.y == height) {
            if (DEBUG) Log.d(TAG, String.format("Already rendered at size=%sx%s %d%%",
                    width, height, (int)(100*mPreview.getScaleX())));
            return;
        }
        if (width == 0 || height == 0) {
            return;
        }

        mPreview.width = width;
        mPreview.height = height;
        mPreview.requestLayout();

        final int thisWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int thisHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        final float pvScaleX = (float) thisWidth / width;
        final float pvScaleY = (float) thisHeight / height;
        final float pvScale = Math.min(pvScaleX, pvScaleY);

        final int pvWidth = (int) (pvScale * width);
        final int pvHeight = (int) (pvScale * height);

        final float pvTransX = pvWidth < thisWidth ? (thisWidth - pvWidth) / 2 : 0;
        final float pvTransY = pvHeight < thisHeight ? (thisHeight - pvHeight) / 2 : 0;

        final boolean isRtl = mPreview.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        mPreview.setPivotX(isRtl ? mPreview.width : 0 );
        mPreview.setPivotY(0);
        mPreview.setScaleX(pvScale);
        mPreview.setScaleY(pvScale);
        mPreview.setTranslationX((isRtl ? -1 : 1) * pvTransX);
        mPreview.setTranslationY(pvTransY);

        mRenderedSize.set(width, height);
        if (DEBUG) Log.d(TAG, String.format("Rendered application widget size=%sx%s %d%% instance=%s",
                width, height, (int)(100*mPreview.getScaleX()), instanceId()));
    }

    private void transitionToApplicationWidgetContainer() {
        if (mTransitioning || mDown) {
            return;
        }

        mTransitioning = true;

        enableWindowExitAnimation(false);

        final int navHeight = mInsets.bottom;
        final int navWidth = mInsets.right;

        mPreview.getLocationInWindow(mTmpLoc);
        final float pvHeight = mPreview.getHeight() * mPreview.getScaleY();
        final float pvCenter = mTmpLoc[1] + pvHeight / 2f;

        final ViewGroup root = (ViewGroup) getRootView();

        if (DEBUG) {
            Log.d(TAG, "root = " + root.getLeft() + "," + root.getTop() + " "
                    + root.getWidth() + "x" + root.getHeight());
        }

        if (mFullscreenPreview == null) {
            mFullscreenPreview = getPreviewWidget(mContext);
            mFullscreenPreview.setClickable(false);
            root.addView(mFullscreenPreview, new LayoutParams(
                        root.getWidth() - navWidth,
                        root.getHeight() - navHeight));
        }

        final float fsHeight = root.getHeight() - navHeight;
        final float fsCenter = root.getTop() + fsHeight / 2;

        final float fsScaleY = mPreview.getScaleY();
        final float fsTransY = pvCenter - fsCenter;
        final float fsScaleX = fsScaleY;

        mPreview.setVisibility(View.GONE);
        mFullscreenPreview.setVisibility(View.VISIBLE);
        mFullscreenPreview.setTranslationY(fsTransY);
        mFullscreenPreview.setScaleX(fsScaleX);
        mFullscreenPreview.setScaleY(fsScaleY);
        mFullscreenPreview
            .animate()
            .scaleX(1)
            .scaleY(1)
            .translationX(0)
            .translationY(0)
            .setDuration(WIDGET_ANIMATION_DURATION)
            .withEndAction(mPosttransitionToApplicationWidgetContainerEndAction)
            .start();

        if (navHeight > 0 || navWidth > 0) {
            final boolean atBottom = navHeight > 0;
            if (mFakeNavBar == null) {
                mFakeNavBar = new View(mContext);
                mFakeNavBar.setBackgroundColor(Color.BLACK);
                root.addView(mFakeNavBar, new LayoutParams(
                            atBottom ? LayoutParams.MATCH_PARENT
                                     : navWidth,
                            atBottom ? navHeight
                                     : LayoutParams.MATCH_PARENT,
                            atBottom ? Gravity.BOTTOM|Gravity.FILL_HORIZONTAL
                                     : Gravity.RIGHT|Gravity.FILL_VERTICAL));
                mFakeNavBar.setPivotY(navHeight);
                mFakeNavBar.setPivotX(navWidth);
            }
            mFakeNavBar.setAlpha(0f);
            if (atBottom) {
                mFakeNavBar.setScaleY(0.5f);
            } else {
                mFakeNavBar.setScaleX(0.5f);
            }
            mFakeNavBar.setVisibility(View.VISIBLE);
            mFakeNavBar.animate()
                .alpha(1f)
                .scaleY(1f)
                .scaleY(1f)
                .setDuration(WIDGET_ANIMATION_DURATION)
                .start();
        }
        mCallbacks.onLaunchingApplicationWidgetContainer();
    }

    private void recover() {
        if (DEBUG) Log.d(TAG, "recovering at " + SystemClock.uptimeMillis());
        mCallbacks.onApplicationWidgetContainerLaunchedUnsuccessfully();
        reset();
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        // ignore
    }

    @Override
    public void onClick(View v) {
        if (DEBUG) Log.d(TAG, "clicked");
        if (mTransitioning) {
            return;
        }
        if (mActive) {
            cancelTransitionToApplicationWidgetContainer();
            transitionToApplicationWidgetContainer();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DEBUG) Log.d(TAG, "onDetachedFromWindow: instance " + instanceId()
                + " at " + SystemClock.uptimeMillis());
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mCallback);
        cancelTransitionToApplicationWidgetContainer();
        mHandler.removeCallbacks(mRecoverRunnable);
    }

    @Override
    public void onActive(boolean isActive) {
        if (DEBUG) Log.d(TAG, "onActive");

        mActive = isActive;
        if (mActive) {
            rescheduleTransitionToApplicationWidgetContainer();
        } else {
            reset();
        }
    }

    @Override
    public boolean onUserInteraction(MotionEvent event) {
        if (DEBUG) Log.d(TAG, "onUserInteraction");

        if (mTransitioning) {
            if (DEBUG) Log.d(TAG, "onUserInteraction eaten: mTransitioning");
            return true;
        }

        getLocationOnScreen(mTmpLoc);
        int rawBottom = mTmpLoc[1] + getHeight();
        if (event.getRawY() > rawBottom) {
            if (DEBUG) Log.d(TAG, "onUserInteraction eaten: below widget");
            return true;
        }

        int action = event.getAction();
        mDown = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
        if (mActive) {
            rescheduleTransitionToApplicationWidgetContainer();
        }
        if (DEBUG) Log.d(TAG, "onUserInteraction observed, not eaten");
        return false;
    }


    public void setApplicationWidgetPackageName(String packageName) {
        mApplicationWidgetPackageName = packageName;
    }

    @Override
    protected void onFocusLost() {
        if (DEBUG) Log.d(TAG, "onFocusLost at " + SystemClock.uptimeMillis());
        cancelTransitionToApplicationWidgetContainer();
        super.onFocusLost();
    }

    public void onScreenTurnedOff() {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOff");
        reset();
    }

    private void rescheduleTransitionToApplicationWidgetContainer() {
        if (DEBUG) Log.d(TAG, "rescheduleTransitionToApplicationWidgetContainer at " +
                SystemClock.uptimeMillis());
        mHandler.removeCallbacks(mTransitionToApplicationWidgetContainerRunnable);
        final long duration = mUseFastTransition ? 0 : WIDGET_WAIT_DURATION;
        mHandler.postDelayed(mTransitionToApplicationWidgetContainerRunnable, duration);
    }

    private void cancelTransitionToApplicationWidgetContainer() {
        if (DEBUG) Log.d(TAG, "cancelTransitionToApplicationWidgetContainer at " +
                SystemClock.uptimeMillis());
        mHandler.removeCallbacks(mTransitionToApplicationWidgetContainerRunnable);
    }

    private void onApplicationWidgetContainerLaunched() {
        mCallbacks.onApplicationWidgetContainerLaunchedSuccessfully();
        reset();
    }

    private void reset() {
        if (DEBUG) Log.d(TAG, "reset at " + SystemClock.uptimeMillis());
        mLaunchApplicationWidgetContainerStart = 0;
        mTransitioning = false;
        mDown = false;
        cancelTransitionToApplicationWidgetContainer();
        mHandler.removeCallbacks(mRecoverRunnable);
        mPreview.setVisibility(View.VISIBLE);
        if (mFullscreenPreview != null) {
            mFullscreenPreview.animate().cancel();
            mFullscreenPreview.setVisibility(View.GONE);
        }
        if (mFakeNavBar != null) {
            mFakeNavBar.animate().cancel();
            mFakeNavBar.setVisibility(View.GONE);
        }
        enableWindowExitAnimation(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format("onSizeChanged new=%sx%s old=%sx%s at %s",
                w, h, oldw, oldh, SystemClock.uptimeMillis()));
        if ((w != oldw && oldw > 0) || (h != oldh && oldh > 0)) {
            // we can't trust the old geometry anymore; force a re-render
            mRenderedSize.x = mRenderedSize.y = -1;
        }
        mHandler.post(mRenderRunnable);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onBouncerShowing(boolean showing) {
        if (showing) {
            mTransitioning = false;
            mHandler.post(mRecoverRunnable);
        }
    }

    private void enableWindowExitAnimation(boolean isEnabled) {
        View root = getRootView();
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (!(lp instanceof WindowManager.LayoutParams)) {
            return;
        }
        WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
        int newWindowAnimations = isEnabled ? R.style.Animation_LockScreen : 0;
        if (newWindowAnimations != wlp.windowAnimations) {
            if (DEBUG) Log.d(TAG, "setting windowAnimations to: " + newWindowAnimations
                    + " at " + SystemClock.uptimeMillis());
            wlp.windowAnimations = newWindowAnimations;
            mWindowManager.updateViewLayout(root, wlp);
        }
    }

    private void onKeyguardVisibilityChanged(boolean showing) {
        if (DEBUG) Log.d(TAG, "onKeyguardVisibilityChanged " + showing
                + " at " + SystemClock.uptimeMillis());
        if (mTransitioning && !showing) {
            mTransitioning = false;
            mHandler.removeCallbacks(mRecoverRunnable);
            if (mLaunchApplicationWidgetContainerStart > 0) {
                long launchTime = SystemClock.uptimeMillis() -
                        mLaunchApplicationWidgetContainerStart;
                if (DEBUG) Log.d(TAG, String.format("ApplicationWidget Container launch" +
                        "took %sms to launch", launchTime));
                mLaunchApplicationWidgetContainerStart = 0;
                onApplicationWidgetContainerLaunched();
            }
        }
    }

    private String instanceId() {
        return Integer.toHexString(hashCode());
    }

    public void setInsets(Rect insets) {
        if (DEBUG) Log.d(TAG, "setInsets: " + insets);
        mInsets.set(insets);
    }

    public void setUseFastTransition(boolean useFastTransition) {
        mUseFastTransition = useFastTransition;
    }
}
