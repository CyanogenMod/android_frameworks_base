package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Property;

public class BackButtonDrawable extends Drawable {
    private final Drawable mWrappedDrawable;
    private float mRotation;
    private Animator mCurrentAnimator;

    private static final int ANIMATION_DURATION = 200;
    public static final Property<BackButtonDrawable, Float> ROTATION
            = new FloatProperty<BackButtonDrawable>("rotation") {
        @Override
        public void setValue(BackButtonDrawable object, float value) {
            object.setRotation(value);
        }

        @Override
        public Float get(BackButtonDrawable object) {
            return object.getRotation();
        }
    };

    public BackButtonDrawable(Drawable wrappedDrawable) {
        mWrappedDrawable = wrappedDrawable;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = mWrappedDrawable.getBounds();
        final int boundsCenterX = bounds.width() / 2;
        final int boundsCenterY = bounds.height() / 2;

        canvas.translate(boundsCenterX, boundsCenterY);
        canvas.rotate(mRotation);
        canvas.translate(- boundsCenterX, - boundsCenterY);

        mWrappedDrawable.draw(canvas);
    }

    @Override
    public void setBounds(Rect bounds) {
        mWrappedDrawable.setBounds(bounds);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        mWrappedDrawable.setBounds(left, top, right, bottom);
    }

    @Override
    public void setAlpha(int alpha) {
        mWrappedDrawable.setAlpha(alpha);
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }

    @Override
    public int getAlpha() {
        return mWrappedDrawable.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return mWrappedDrawable.getOpacity();
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

        final float nextRotation = ime ? - 90 : 0;
        if (mRotation == nextRotation) {
            return;
        }

        if (isVisible() && ActivityManager.isHighEndGfx()) {
            mCurrentAnimator = ObjectAnimator.ofFloat(this, ROTATION, nextRotation)
                    .setDuration(ANIMATION_DURATION);
            mCurrentAnimator.start();
        } else {
            setRotation(nextRotation);
        }
    }
}
