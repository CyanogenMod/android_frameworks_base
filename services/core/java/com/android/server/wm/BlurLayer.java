/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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
 * limitations under the License.
 */

package com.android.server.wm;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.SurfaceControl;

import java.io.PrintWriter;

public class BlurLayer {
    private static final String TAG = "BlurLayer";
    private static final boolean DEBUG = false;

    /** Reference to the owner of this object. */
    final DisplayContent mDisplayContent;

    /** Actual surface that blurs */
    SurfaceControl mBlurSurface;

    /** Last value passed to mBlurSurface.setBlur() */
    float mBlur = 0;

    /** Last value passed to mBlurSurface.setLayer() */
    int mLayer = -1;

    /** Next values to pass to mBlurSurface.setPosition() and mBlurSurface.setSize() */
    Rect mBounds = new Rect();

    /** Last values passed to mBlurSurface.setPosition() and mBlurSurface.setSize() */
    Rect mLastBounds = new Rect();

    /** True after mBlurSurface.show() has been called, false after mBlurSurface.hide(). */
    private boolean mShowing = false;

    /** Value of mBlur when beginning transition to mTargetBlur */
    float mStartBlur = 0;

    /** Final value of mBlur following transition */
    float mTargetBlur = 0;

    /** Time in units of SystemClock.uptimeMillis() at which the current transition started */
    long mStartTime;

    /** Time in milliseconds to take to transition from mStartBlur to mTargetBlur */
    long mDuration;

    /** Owning stack */
    final TaskStack mStack;

    BlurLayer(WindowManagerService service, TaskStack stack, DisplayContent displayContent) {
        mStack = stack;
        mDisplayContent = displayContent;
        final int displayId = mDisplayContent.getDisplayId();
        if (DEBUG) Slog.v(TAG, "Ctor: displayId=" + displayId);
        SurfaceControl.openTransaction();
        try {
            if (WindowManagerService.DEBUG_SURFACE_TRACE) {
                mBlurSurface = new WindowStateAnimator.SurfaceTrace(service.mFxSession,
                    "BlurSurface",
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_BLUR | SurfaceControl.HIDDEN);
            } else {
                mBlurSurface = new SurfaceControl(service.mFxSession, TAG,
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_BLUR | SurfaceControl.HIDDEN);
            }
            if (WindowManagerService.SHOW_TRANSACTIONS ||
                    WindowManagerService.SHOW_SURFACE_ALLOC) Slog.i(TAG,
                            "  BLUR " + mBlurSurface + ": CREATE");
            mBlurSurface.setLayerStack(displayId);
        } catch (Exception e) {
            Slog.e(WindowManagerService.TAG, "Exception creating Blur surface", e);
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
        if (mLayer != layer) {
            mLayer = layer;
            mBlurSurface.setLayer(layer);
        }
    }

    int getLayer() {
        return mLayer;
    }

    private void setBlur(float blur) {
        if (mBlur != blur) {
            if (DEBUG) Slog.v(TAG, "setBlur blur=" + blur);
            try {
                mBlurSurface.setBlur(blur);
                if (blur == 0 && mShowing) {
                    if (DEBUG) Slog.v(TAG, "setBlur hiding");
                    mBlurSurface.hide();
                    mShowing = false;
                } else if (blur > 0 && !mShowing) {
                    if (DEBUG) Slog.v(TAG, "setBlur showing");
                    mBlurSurface.show();
                    mShowing = true;
                }
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting blur immediately", e);
            }
            mBlur = blur;
        }
    }

    void adjustBounds() {
        final int dw, dh;
        final float xPos, yPos;
        if (!mStack.isFullscreen()) {
            dw = mBounds.width();
            dh = mBounds.height();
            xPos = mBounds.left;
            yPos = mBounds.top;
        } else {
            // Set surface size to screen size.
            final DisplayInfo info = mDisplayContent.getDisplayInfo();
            dw = info.logicalWidth;
            dh = info.logicalHeight;
            xPos = 0;
            yPos = 0;
        }

        mBlurSurface.setPosition(xPos, yPos);
        mBlurSurface.setSize(dw, dh);
        mLastBounds.set(mBounds);
    }

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
            if (DEBUG) Slog.v(TAG, "show: immediate");
            show(mLayer, mTargetBlur, 0);
        }
    }

    /**
     * Begin an animation to a new dim value.
     * NOTE: Must be called with Surface transaction open.
     *
     * @param layer The layer to set the surface to.
     * @param blur The dim value to end at.
     * @param duration How long to take to get there in milliseconds.
     */
    void show(int layer, float blur, long duration) {
        if (DEBUG) Slog.v(TAG, "show: layer=" + layer + " blur=" + blur
                + " duration=" + duration);
        if (mBlurSurface == null) {
            Slog.e(TAG, "show: no Surface");
            // Make sure isAnimating() returns false.
            mTargetBlur = mBlur = 0;
            return;
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
        if (DEBUG) Slog.v(TAG, "show: mStartBlur=" + mStartBlur + " mStartTime=" + mStartTime);
        mTargetBlur = blur;
    }

    /** Immediate hide.
     * NOTE: Must be called with Surface transaction open. */
    void hide() {
        if (mShowing) {
            if (DEBUG) Slog.v(TAG, "hide: immediate");
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
            if (DEBUG) Slog.v(TAG, "hide: duration=" + duration);
            show(mLayer, 0, duration);
        }
    }

    /**
     * Advance the dimming per the last #show(int, float, long) call.
     * NOTE: Must be called with Surface transaction open.
     *
     * @return True if animation is still required after this step.
     */
    boolean stepAnimation() {
        if (mBlurSurface == null) {
            Slog.e(TAG, "stepAnimation: null Surface");
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
            if (DEBUG) Slog.v(TAG, "stepAnimation: curTime=" + curTime + " blur=" + blur);
            setBlur(blur);
        }

        return isAnimating();
    }

    /** Cleanup */
    void destroySurface() {
        if (DEBUG) Slog.v(TAG, "destroySurface.");
        if (mBlurSurface != null) {
            mBlurSurface.destroy();
            mBlurSurface = null;
        }
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
