/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.wm;

import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_BLUR_LAYER;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_SURFACE_TRACE;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_SURFACE_ALLOC;
import static com.android.server.wm.WindowManagerDebugConfig.SHOW_TRANSACTIONS;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.SurfaceControl;

import java.io.PrintWriter;

public class BlurLayer {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BlurLayer" : TAG_WM;
    private final WindowManagerService mService;

    /** Actual surface that blurs */
    private SurfaceControl mBlurSurface;

    /** Last value passed to mBlurSurface.setBlur() */
    private float mBlur = 0;

    /** Last value passed to mBlurSurface.setLayer() */
    private int mLayer = -1;

    /** Next values to pass to mBlurSurface.setPosition() and mBlurSurface.setSize() */
    private final Rect mBounds = new Rect();

    /** Last values passed to mBlurSurface.setPosition() and mBlurSurface.setSize() */
    private final Rect mLastBounds = new Rect();

    /** True after mBlurSurface.show() has been called, false after mBlurSurface.hide(). */
    private boolean mShowing = false;

    /** Value of mBlur when beginning transition to mTargetBlur */
    private float mStartBlur = 0;

    /** Final value of mBlur following transition */
    private float mTargetBlur = 0;

    /** Time in units of SystemClock.uptimeMillis() at which the current transition started */
    private long mStartTime;

    /** Time in milliseconds to take to transition from mStartBlur to mTargetBlur */
    private long mDuration;

    private boolean mDestroyed = false;

    private final int mDisplayId;

    /** Interface implemented by users of the blur layer */
    interface BlurLayerUser {
        /** Returns true if the blur should be fullscreen. */
        boolean blurFullscreen();
        /** Returns the display info. of the blur layer user. */
        DisplayInfo getDisplayInfo();
        /** Gets the bounds of the blur layer user. */
        void getBlurBounds(Rect outBounds);
        String toShortString();
    }
    /** The user of this blur layer. */
    private final BlurLayerUser mUser;

    private final String mName;

    BlurLayer(WindowManagerService service, BlurLayerUser user, int displayId, String name) {
		mUser = user;
		mDisplayId = displayId;
        mService = service;
        mName = name;
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "Ctor: displayId=" + displayId);
    }

    private void constructSurface(WindowManagerService service) {
        SurfaceControl.openTransaction();
        try {
            if (DEBUG_SURFACE_TRACE) {
                mBlurSurface = new WindowSurfaceController.SurfaceTrace(service.mFxSession,
                    "BlurSurface",
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_BLUR | SurfaceControl.HIDDEN);
            } else {
                mBlurSurface = new SurfaceControl(service.mFxSession, mName,
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_BLUR | SurfaceControl.HIDDEN);
            }
            if (SHOW_TRANSACTIONS || SHOW_SURFACE_ALLOC) Slog.i(TAG,
                    "  BLUR " + mBlurSurface + ": CREATE");
            mBlurSurface.setLayerStack(mDisplayId);
            adjustBounds();
            adjustBlur(mBlur);
            adjustLayer(mLayer);
        } catch (Exception e) {
            Slog.e(TAG, "Exception creating Blur surface", e);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    /** Return true if blur layer is showing */
    boolean isBlurring() {
        return mTargetBlur != 0;
    }

    /** Return true if in a transition period */
    boolean isAnimating() {
        return mTargetBlur != mBlur;
    }

    float getTargetBlur() {
        return mTargetBlur;
    }

    void setLayer(int layer) {
        if (mLayer == layer) {
            return;
        }
        mLayer = layer;
        adjustLayer(layer);
    }

    private void adjustLayer(int layer) {
        if (mBlurSurface != null) {
            mBlurSurface.setLayer(layer);
        }
    }

    int getLayer() {
        return mLayer;
    }

   private void setBlur(float blur) {
        if (mBlur == blur) {
            return;
        }
        mBlur = blur;
        adjustBlur(blur);
    }

    private void adjustBlur(float blur) {
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "setBlur blur=" + blur);
        try {
            if (mBlurSurface != null) {
                mBlurSurface.setBlur(blur);
            }
            if (blur == 0 && mShowing) {
                if (DEBUG_BLUR_LAYER) Slog.v(TAG, "setBlur hiding");
                if (mBlurSurface != null) {
                    mBlurSurface.hide();
                    mShowing = false;
                }
            } else if (blur > 0 && !mShowing) {
                if (DEBUG_BLUR_LAYER) Slog.v(TAG, "setBlur showing");
                if (mBlurSurface != null) {
                    mBlurSurface.show();
                    mShowing = true;
                }
            }
        } catch (RuntimeException e) {
            Slog.w(TAG, "Failure setting blur immediately", e);
        }
    }

    /**
     * NOTE: Must be called with Surface transaction open.
     */
    private void adjustBounds() {
        if (mUser.blurFullscreen()) {
            getBoundsForFullscreen(mBounds);
        }

        if (mBlurSurface != null) {
            mBlurSurface.setPosition(mBounds.left, mBounds.top);
            mBlurSurface.setSize(mBounds.width(), mBounds.height());
            if (DEBUG_BLUR_LAYER) Slog.v(TAG,
                    "adjustBounds user=" + mUser.toShortString() + " mBounds=" + mBounds);
        }

        mLastBounds.set(mBounds);
    }

    private void getBoundsForFullscreen(Rect outBounds) {
        final int dw, dh;
        final float xPos, yPos;
        // Set surface size to screen size.
        final DisplayInfo info = mUser.getDisplayInfo();
        // Multiply by 1.5 so that rotating a frozen surface that includes this does not expose
        // a corner.
        dw = (int) (info.logicalWidth * 1.5);
        dh = (int) (info.logicalHeight * 1.5);
        // back off position so 1/4 of Surface is before and 1/4 is after.
        xPos = -1 * dw / 6;
        yPos = -1 * dh / 6;
        outBounds.set((int) xPos, (int) yPos, (int) xPos + dw, (int) yPos + dh);
    }

    void setBoundsForFullscreen() {
        getBoundsForFullscreen(mBounds);
        setBounds(mBounds);
    }

    /** @param bounds The new bounds to set */
    void setBounds(Rect bounds) {
        mBounds.set(bounds);
        if (isBlurring() && !mLastBounds.equals(bounds)) {
            try {
                SurfaceControl.openTransaction();
                adjustBounds();
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting size", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    /**
     * @param duration The time to test.
     * @return True if the duration would lead to an earlier end to the current animation.
     */
    private boolean durationEndsEarlier(long duration) {
        return SystemClock.uptimeMillis() + duration < mStartTime + mDuration;
    }

    /** Jump to the end of the animation.
     * NOTE: Must be called with Surface transaction open. */
    void show() {
        if (isAnimating()) {
            if (DEBUG_BLUR_LAYER) Slog.v(TAG, "show: immediate");
            show(mLayer, mTargetBlur, 0);
        }
    }

    /**
     * Begin an animation to a new blur value.
     * NOTE: Must be called with Surface transaction open.
     *
     * @param layer The layer to set the surface to.
     * @param blur The blur value to end at.
     * @param duration How long to take to get there in milliseconds.
     */
    void show(int layer, float blur, long duration) {
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "show: layer=" + layer + " blur=" + blur
                + " duration=" + duration);
        if (mDestroyed) {
            Slog.e(TAG, "show: no Surface");
            // Make sure isAnimating() returns false.
            mTargetBlur = mBlur = 0;
            return;
        }

        if (mBlurSurface == null) {
            constructSurface(mService);
        }

        if (!mLastBounds.equals(mBounds)) {
            adjustBounds();
        }
        setLayer(layer);

        long curTime = SystemClock.uptimeMillis();
        final boolean animating = isAnimating();
        if ((animating && (mTargetBlur != blur || durationEndsEarlier(duration)))
                || (!animating && mBlur != blur)) {
            if (duration <= 0) {
                // No animation required, just set values.
                setBlur(blur);
            } else {
                // Start or continue animation with new parameters.
                mStartBlur = mBlur;
                mStartTime = curTime;
                mDuration = duration;
            }
        }
        mTargetBlur = blur;
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "show: mStartBlur=" + mStartBlur + " mStartTime=" + mStartTime);
    }

    /** Immediate hide.
     * NOTE: Must be called with Surface transaction open. */
    void hide() {
        if (mShowing) {
            if (DEBUG_BLUR_LAYER) Slog.v(TAG, "hide: immediate");
            hide(0);
        }
    }

    /**
     * Gradually fade to transparent.
     * NOTE: Must be called with Surface transaction open.
     *
     * @param duration Time to fade in milliseconds.
     */
    void hide(long duration) {
        if (mShowing && (mTargetBlur != 0 || durationEndsEarlier(duration))) {
            if (DEBUG_BLUR_LAYER) Slog.v(TAG, "hide: duration=" + duration);
            show(mLayer, 0, duration);
        }
    }

    /**
     * Advance the blurring per the last #show(int, float, long) call.
     * NOTE: Must be called with Surface transaction open.
     *
     * @return True if animation is still required after this step.
     */
    boolean stepAnimation() {
        if (mDestroyed) {
            Slog.e(TAG, "stepAnimation: surface destroyed");
            // Ensure that isAnimating() returns false;
            mTargetBlur = mBlur = 0;
            return false;
        }

        if (isAnimating()) {
            final long curTime = SystemClock.uptimeMillis();
            final float blurDelta = mTargetBlur - mStartBlur;
            float blur = mStartBlur + blurDelta * (curTime - mStartTime) / mDuration;
            if (blurDelta > 0 && blur > mTargetBlur ||
                    blurDelta < 0 && blur < mTargetBlur) {
                // Don't exceed limits.
                blur = mTargetBlur;
            }
            if (DEBUG_BLUR_LAYER) Slog.v(TAG, "stepAnimation: curTime=" + curTime + " blur=" + blur);
            setBlur(blur);
        }

        return isAnimating();
    }

    /** Cleanup */
    void destroySurface() {
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "destroySurface.");
        if (mBlurSurface != null) {
            mBlurSurface.destroy();
            mBlurSurface = null;
        }
		mDestroyed = true;
    }

    public void printTo(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mBlurSurface="); pw.print(mBlurSurface);
                pw.print(" mLayer="); pw.print(mLayer);
                pw.print(" mBlur="); pw.println(mBlur);
        pw.print(prefix); pw.print("mLastBounds="); pw.print(mLastBounds.toShortString());
                pw.print(" mBounds="); pw.println(mBounds.toShortString());
        pw.print(prefix); pw.print("Last animation: ");
                pw.print(" mDuration="); pw.print(mDuration);
                pw.print(" mStartTime="); pw.print(mStartTime);
                pw.print(" curTime="); pw.println(SystemClock.uptimeMillis());
        pw.print(prefix); pw.print(" mStartBlur="); pw.print(mStartBlur);
                pw.print(" mTargetBlur="); pw.println(mTargetBlur);
    }
}
