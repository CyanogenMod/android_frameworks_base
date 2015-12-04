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
 * limitations under the License.
 */

package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.view.ViewAnimationUtils;

/** Helper for quick settings detail panel clip animations. **/
public class QSDetailClipper {

    private final View mDetail;
    private final TransitionDrawable mBackground;

    private Animator mAnimator;

    public QSDetailClipper(View detail) {
        mDetail = detail;
        mBackground = (TransitionDrawable) detail.getBackground();
    }

    public boolean isAnimating() {
        return mAnimator != null && mAnimator.isRunning();
    }

    public void animateCircularClip(int x, int y, boolean in, AnimatorListener listener) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        final int w = mDetail.getWidth() - x;
        final int h = mDetail.getHeight() - y;
        int innerR = 0;
        if (x < 0 || w < 0 || y < 0 || h < 0) {
            innerR = Math.abs(x);
            innerR = Math.min(innerR, Math.abs(y));
            innerR = Math.min(innerR, Math.abs(w));
            innerR = Math.min(innerR, Math.abs(h));
        }
        int r = (int) Math.ceil(Math.sqrt(x * x + y * y));
        r = (int) Math.max(r, Math.ceil(Math.sqrt(w * w + y * y)));
        r = (int) Math.max(r, Math.ceil(Math.sqrt(w * w + h * h)));
        r = (int) Math.max(r, Math.ceil(Math.sqrt(x * x + h * h)));
        if (in) {
            mAnimator = ViewAnimationUtils.createCircularReveal(mDetail, x, y, innerR, r);
        } else {
            mAnimator = ViewAnimationUtils.createCircularReveal(mDetail, x, y, r, innerR);
        }
        mAnimator.setDuration((long)(mAnimator.getDuration() * 1.5));
        if (listener != null) {
            mAnimator.addListener(listener);
        }
        if (in) {
            mBackground.startTransition((int)(mAnimator.getDuration() * 0.6));
            mAnimator.addListener(mVisibleOnStart);
        } else {
            mDetail.postDelayed(mReverseBackground, (long)(mAnimator.getDuration() * 0.65));
            mAnimator.addListener(mGoneOnEnd);
        }
        mAnimator.start();
    }

    private final Runnable mReverseBackground = new Runnable() {
        @Override
        public void run() {
            if (mAnimator != null) {
                mBackground.reverseTransition((int)(mAnimator.getDuration() * 0.35));
            }
        }
    };

    private final AnimatorListenerAdapter mVisibleOnStart = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            mDetail.setVisibility(View.VISIBLE);
        }

        public void onAnimationEnd(Animator animation) {
            mAnimator = null;
        }
    };

    private final AnimatorListenerAdapter mGoneOnEnd = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mDetail.setVisibility(View.GONE);
            mBackground.resetTransition();
            mAnimator = null;
        };
    };
}
