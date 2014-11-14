/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.policy;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

public class BackButtonDrawable extends DrawableWrapper {
    private float mRotation;
    private Animator mCurrentAnimator;

    private static final int ANIMATION_DURATION = 200;

    public BackButtonDrawable(Drawable wrappedDrawable) {
        super(wrappedDrawable);
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        final int boundsCenterX = bounds.width() / 2;
        final int boundsCenterY = bounds.height() / 2;

        canvas.save();
        canvas.translate(boundsCenterX, boundsCenterY);
        canvas.rotate(mRotation);
        canvas.translate(-boundsCenterX, -boundsCenterY);

        super.draw(canvas);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }

    public void setRotation(float rotation) {
        mRotation = rotation;
        invalidateSelf();
    }

    public float getRotation() {
        return mRotation;
    }

    public void setImeVisible(boolean ime) {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        final float nextRotation = ime ? -90 : 0;
        if (mRotation == nextRotation) {
            return;
        }

        if (isVisible() && ActivityManager.isHighEndGfx()) {
            mCurrentAnimator = ObjectAnimator.ofFloat(this, "rotation", nextRotation)
                    .setDuration(ANIMATION_DURATION);
            mCurrentAnimator.start();
        } else {
            setRotation(nextRotation);
        }
    }
}
