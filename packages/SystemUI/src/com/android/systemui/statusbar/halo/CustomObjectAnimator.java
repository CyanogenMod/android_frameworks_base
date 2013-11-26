/*
 * Copyright (C) 2013 ParanoidAndroid.
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

package com.android.systemui.statusbar.halo;

import android.os.Handler;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.view.View;

public class CustomObjectAnimator {

    private View rootView;
    private Handler handler = new Handler();
    private ObjectAnimator animator;
    private boolean delayed = false;

    public CustomObjectAnimator(View root) {
        rootView = root;
    }

    public boolean isRunning() {
        return animator != null && (animator.isRunning() || delayed);
    }

    public void animate(ObjectAnimator newInstance, TimeInterpolator interpolator, AnimatorUpdateListener update) {
        runAnimation(newInstance, interpolator, update, null);
    }

    public void animate(final ObjectAnimator newInstance, final TimeInterpolator interpolator,
            final AnimatorUpdateListener update, long startDelay, final Runnable executeAfter) {

        delayed = true;
        handler.postDelayed(new Runnable() {
            public void run() {
                runAnimation(newInstance, interpolator, update, executeAfter);
                delayed = false;
            }}, startDelay);
    }

    private void runAnimation(ObjectAnimator newInstance, TimeInterpolator interpolator,
            AnimatorUpdateListener update, final Runnable executeAfter) {

        // Terminate old instance, if present
        cancel(false);
        animator = newInstance;

        // Invalidate
        if (update == null) {
            animator.addUpdateListener(new AnimatorUpdateListener() {
                float oldValue = -1f;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float value = animation.getAnimatedFraction();
                    if (value != oldValue) rootView.postInvalidate();
                    oldValue = value;
                }});
        } else {
            animator.addUpdateListener(update);
        }

        animator.setInterpolator(interpolator);

        if (executeAfter != null) {
            animator.addListener(new Animator.AnimatorListener() {
                boolean canceled = false;
                @Override public void onAnimationRepeat(Animator animation) {}
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }
                @Override public void onAnimationEnd(Animator animation) {
                    if (!canceled) executeAfter.run();
                }});
        }
        
        animator.start();
    }

    public void cancel(boolean unschedule) {
        if (unschedule) handler.removeCallbacksAndMessages(null);
        if (animator != null) animator.cancel();
        delayed = false;
    }
}
