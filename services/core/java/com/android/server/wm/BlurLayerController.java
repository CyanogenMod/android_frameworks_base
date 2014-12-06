package com.android.server.wm;

import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_BLUR_LAYER;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import static com.android.server.wm.WindowManagerService.LAYER_OFFSET_BLUR;
import static com.android.server.wm.WindowManagerService.LAYER_OFFSET_BLUR_WITH_MASKING;

import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.TypedValue;

import com.android.server.wm.BlurLayer.BlurLayerUser;

import java.io.PrintWriter;

/**
 * Centralizes the control of blur layers used for
 * {@link android.view.WindowManager.LayoutParams#FLAG_BLUR_BEHIND}
 * as well as other use cases (such as blurring a surface)
 */
class BlurLayerController {
    private static final String TAG_LOCAL = "BlurLayerController";
    private static final String TAG = TAG_WITH_CLASS_NAME ? TAG_LOCAL : TAG_WM;

    /** Amount of time in milliseconds to animate the blur surface from one value to another,
     * when no window animation is driving it. */
    private static final int DEFAULT_BLUR_DURATION = 50;

    /**
     * The default amount of blur applied to mask a surface
     */
    private static final float DEFAULT_BLUR_MASK_SURFACE_AMOUNT = 0.5f;

    // Shared blur layer for fullscreen users. {@link BlurLayerState#blurLayer} will point to this
    // instead of creating a new object per fullscreen task on a display.
    private BlurLayer mSharedFullScreenBlurLayer;

    private ArrayMap<BlurLayer.BlurLayerUser, BlurLayerState> mState = new ArrayMap<>();

    private DisplayContent mDisplayContent;

    private Rect mTmpBounds = new Rect();

    BlurLayerController(DisplayContent displayContent) {
        mDisplayContent = displayContent;
    }

    /** Updates the blur layer bounds, recreating it if needed. */
    void updateBlurLayer(BlurLayer.BlurLayerUser blurLayerUser) {
        final BlurLayerState state = getOrCreateBlurLayerState(blurLayerUser);
        final boolean previousFullscreen = state.blurLayer != null
                && state.blurLayer == mSharedFullScreenBlurLayer;
        BlurLayer newBlurLayer;
        final int displayId = mDisplayContent.getDisplayId();
        if (blurLayerUser.blurFullscreen()) {
            if (previousFullscreen && mSharedFullScreenBlurLayer != null) {
                // Update the bounds for fullscreen in case of rotation.
                mSharedFullScreenBlurLayer.setBoundsForFullscreen();
                return;
            }
            // Use shared fullscreen blur layer
            newBlurLayer = mSharedFullScreenBlurLayer;
            if (newBlurLayer == null) {
                if (state.blurLayer != null) {
                    // Re-purpose the previous blur layer.
                    newBlurLayer = state.blurLayer;
                } else {
                    // Create new full screen blur layer.
                    newBlurLayer = new BlurLayer(mDisplayContent.mService, blurLayerUser, displayId,
                            getBlurLayerTag(blurLayerUser));
                }
                blurLayerUser.getBlurBounds(mTmpBounds);
                newBlurLayer.setBounds(mTmpBounds);
                mSharedFullScreenBlurLayer = newBlurLayer;
            } else if (state.blurLayer != null) {
                state.blurLayer.destroySurface();
            }
        } else {
            newBlurLayer = (state.blurLayer == null || previousFullscreen)
                    ? new BlurLayer(mDisplayContent.mService, blurLayerUser, displayId,
                            getBlurLayerTag(blurLayerUser))
                    : state.blurLayer;
            blurLayerUser.getBlurBounds(mTmpBounds);
            newBlurLayer.setBounds(mTmpBounds);
        }
        state.blurLayer = newBlurLayer;
    }

    private static String getBlurLayerTag(BlurLayerUser blurLayerUser) {
        return TAG_LOCAL + "/" + blurLayerUser.toShortString();
    }

    private BlurLayerState getOrCreateBlurLayerState(BlurLayer.BlurLayerUser blurLayerUser) {
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "getOrCreateBlurLayerState, blurLayerUser="
                + blurLayerUser.toShortString());
        BlurLayerState state = mState.get(blurLayerUser);
        if (state == null) {
            state = new BlurLayerState();
            mState.put(blurLayerUser, state);
        }
        return state;
    }

    private void setContinueBlurring(BlurLayer.BlurLayerUser blurLayerUser) {
        BlurLayerState state = mState.get(blurLayerUser);
        if (state == null) {
            if (DEBUG_BLUR_LAYER) Slog.w(TAG, "setContinueBlurring, no state for: "
                    + blurLayerUser.toShortString());
            return;
        }
        state.continueBlurring = true;
    }

    boolean isBlurring() {
        for (int i = mState.size() - 1; i >= 0; i--) {
            BlurLayerState state = mState.valueAt(i);
            if (state.blurLayer != null && state.blurLayer.isBlurring()) {
                return true;
            }
        }
        return false;
    }

    void resetBlurring() {
        for (int i = mState.size() - 1; i >= 0; i--) {
            mState.valueAt(i).continueBlurring = false;
        }
    }

    private boolean getContinueBlurring(BlurLayer.BlurLayerUser blurLayerUser) {
        BlurLayerState state = mState.get(blurLayerUser);
        return state != null && state.continueBlurring;
    }

    void startBlurringIfNeeded(BlurLayer.BlurLayerUser blurLayerUser,
            WindowStateAnimator newWinAnimator, boolean aboveApp) {
        // Only set blur params on the highest blurred layer.
        // Don't turn on for an unshown surface, or for any layer but the highest blurred layer.
        BlurLayerState state = getOrCreateBlurLayerState(blurLayerUser);
        state.blurAbove = aboveApp;
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "startBlurringIfNeeded,"
                + " blurLayerUser=" + blurLayerUser.toShortString()
                + " newWinAnimator=" + newWinAnimator
                + " state.animator=" + state.animator);
        if (newWinAnimator.getShown() && (state.animator == null
                || !state.animator.getShown()
                || state.animator.mAnimLayer <= newWinAnimator.mAnimLayer)) {
            state.animator = newWinAnimator;
            if (state.animator.mWin.mAppToken == null && !blurLayerUser.blurFullscreen()) {
                // Blur should cover the entire screen for system windows.
                mDisplayContent.getLogicalDisplayRect(mTmpBounds);
            } else {
                blurLayerUser.getBlurBounds(mTmpBounds);
            }
            state.blurLayer.setBounds(mTmpBounds);
        }
    }

    void stopBlurringIfNeeded() {
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "stopBlurringIfNeeded, mState.size()=" + mState.size());
        for (int i = mState.size() - 1; i >= 0; i--) {
            BlurLayer.BlurLayerUser blurLayerUser = mState.keyAt(i);
            stopBlurringIfNeeded(blurLayerUser);
        }
    }

    private void stopBlurringIfNeeded(BlurLayer.BlurLayerUser blurLayerUser) {
        // No need to check if state is null, we know the key has a value.
        BlurLayerState state = mState.get(blurLayerUser);
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "stopBlurringIfNeeded,"
                + " blurLayerUser=" + blurLayerUser.toShortString()
                + " state.continueBlurring=" + state.continueBlurring
                + " state.blurLayer.isBlurring=" + state.blurLayer.isBlurring());
        if (state.animator != null && state.animator.mWin.mWillReplaceWindow) {
            return;
        }

        if (!state.continueBlurring && state.blurLayer.isBlurring()) {
            state.animator = null;
            blurLayerUser.getBlurBounds(mTmpBounds);
            state.blurLayer.setBounds(mTmpBounds);
        }
    }

    boolean animateBlurLayers() {
        int fullScreen = -1;
        int fullScreenAndBlurring = -1;
        boolean result = false;

        for (int i = mState.size() - 1; i >= 0; i--) {
            BlurLayer.BlurLayerUser user = mState.keyAt(i);
            BlurLayerState state = mState.valueAt(i);
            // We have to check that we are actually the shared fullscreen layer
            // for this path. If we began as non fullscreen and became fullscreen
            // (e.g. Docked stack closing), then we may not be the shared layer
            // and we have to make sure we always animate the layer.
            if (user.blurFullscreen() && state.blurLayer == mSharedFullScreenBlurLayer) {
                fullScreen = i;
                if (mState.valueAt(i).continueBlurring) {
                    fullScreenAndBlurring = i;
                }
            } else {
                // We always want to animate the non fullscreen windows, they don't share their
                // blur layers.
                result |= animateBlurLayers(user);
            }
        }
        // For the shared, full screen blur layer, we prefer the animation that is causing it to
        // appear.
        if (fullScreenAndBlurring != -1) {
            result |= animateBlurLayers(mState.keyAt(fullScreenAndBlurring));
        } else if (fullScreen != -1) {
            // If there is no animation for the full screen blur layer to appear, we can use any of
            // the animators that will cause it to disappear.
            result |= animateBlurLayers(mState.keyAt(fullScreen));
        }
        return result;
    }

    private boolean animateBlurLayers(BlurLayer.BlurLayerUser blurLayerUser) {
        BlurLayerState state = mState.get(blurLayerUser);
        if (DEBUG_BLUR_LAYER) Slog.v(TAG, "animateBlurLayers,"
                + " blurLayerUser=" + blurLayerUser.toShortString()
                + " state.animator=" + state.animator
                + " state.continueBlurring=" + state.continueBlurring);
        final int blurLayer;
        final float blurAmount;
        if (state.animator == null) {
            blurLayer = state.blurLayer.getLayer();
            blurAmount = 0;
        } else {
            if (state.blurAbove) {
                blurLayer = state.animator.mAnimLayer + LAYER_OFFSET_BLUR_WITH_MASKING;
            } else {
                blurLayer = state.animator.mAnimLayer - LAYER_OFFSET_BLUR;
            }
            blurAmount = state.animator.mWin.mAttrs.blurAmount;
        }
        final float targetBlur = state.blurLayer.getTargetBlur();
        if (targetBlur != blurAmount) {
            if (state.animator == null) {
                state.blurLayer.hide(DEFAULT_BLUR_DURATION);
            } else {
                long duration = (state.animator.mAnimating && state.animator.mAnimation != null)
                        ? state.animator.mAnimation.computeDurationHint()
                        : DEFAULT_BLUR_DURATION;
                if (targetBlur > blurAmount) {
                    duration = getBlurLayerFadeDuration(duration);
                }
                state.blurLayer.show(blurLayer, blurAmount, duration);
            }
        } else if (state.blurLayer.getLayer() != blurLayer) {
            state.blurLayer.setLayer(blurLayer);
        }
        if (state.blurLayer.isAnimating()) {
            if (!mDisplayContent.mService.okToDisplay()) {
                // Jump to the end of the animation.
                state.blurLayer.show();
            } else {
                return state.blurLayer.stepAnimation();
            }
        }
        return false;
    }

    boolean isBlurring(BlurLayer.BlurLayerUser blurLayerUser, WindowStateAnimator winAnimator) {
        BlurLayerState state = mState.get(blurLayerUser);
        return state != null && state.animator == winAnimator && state.blurLayer.isBlurring();
    }

    private long getBlurLayerFadeDuration(long duration) {
        TypedValue tv = new TypedValue();
        mDisplayContent.mService.mContext.getResources().getValue(
                com.android.internal.R.fraction.config_blurBehindFadeDuration, tv, true);
        if (tv.type == TypedValue.TYPE_FRACTION) {
            duration = (long) tv.getFraction(duration, duration);
        } else if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) {
            duration = tv.data;
        }
        return duration;
    }

    void close() {
        for (int i = mState.size() - 1; i >= 0; i--) {
            BlurLayerState state = mState.valueAt(i);
            state.blurLayer.destroySurface();
        }
        mState.clear();
        mSharedFullScreenBlurLayer = null;
    }

    void removeBlurLayerUser(BlurLayer.BlurLayerUser blurLayerUser) {
        BlurLayerState state = mState.get(blurLayerUser);
        if (state != null) {
            // Destroy the surface, unless it's the shared fullscreen blur.
            if (state.blurLayer != mSharedFullScreenBlurLayer) {
                state.blurLayer.destroySurface();
            }
            mState.remove(blurLayerUser);
        }
    }

    void applyBlurBehind(BlurLayer.BlurLayerUser blurLayerUser, WindowStateAnimator animator) {
        applyBlur(blurLayerUser, animator, false /* aboveApp */);
    }

    void applyBlurAbove(BlurLayer.BlurLayerUser blurLayerUser, WindowStateAnimator animator) {
        applyBlur(blurLayerUser, animator, true /* aboveApp */);
    }

    void applyBlur(
            BlurLayer.BlurLayerUser blurLayerUser, WindowStateAnimator animator, boolean aboveApp) {
        if (blurLayerUser == null) {
            Slog.e(TAG, "Trying to apply blur layer for: " + this
                    + ", but no blur layer user found.");
            return;
        }
        if (!getContinueBlurring(blurLayerUser)) {
            setContinueBlurring(blurLayerUser);
            if (!isBlurring(blurLayerUser, animator)) {
                if (DEBUG_BLUR_LAYER) Slog.v(TAG, "Win " + this + " start blurring.");
                startBlurringIfNeeded(blurLayerUser, animator, aboveApp);
            }
        }
    }

    private static class BlurLayerState {
        // The particular window requesting a blur layer. If null, hide blurLayer.
        WindowStateAnimator animator;
        // Set to false at the start of performLayoutAndPlaceSurfaces. If it is still false by the
        // end then stop any blurring.
        boolean continueBlurring;
        BlurLayer blurLayer;
        boolean blurAbove;
    }

    void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "BlurLayerController");
        final String doubleSpace = "  ";
        final String prefixPlusDoubleSpace = prefix + doubleSpace;

        for (int i = 0, n = mState.size(); i < n; i++) {
            pw.println(prefixPlusDoubleSpace + mState.keyAt(i).toShortString());
            BlurLayerState state = mState.valueAt(i);
            pw.println(prefixPlusDoubleSpace + doubleSpace + "blurLayer="
                    + (state.blurLayer == mSharedFullScreenBlurLayer ? "shared" : state.blurLayer)
                    + ", animator=" + state.animator + ", continueBlurring=" + state.continueBlurring);
            if (state.blurLayer != null) {
                state.blurLayer.printTo(prefixPlusDoubleSpace + doubleSpace, pw);
            }
        }
    }
}
