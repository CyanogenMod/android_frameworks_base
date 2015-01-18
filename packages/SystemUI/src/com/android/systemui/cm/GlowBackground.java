package com.android.systemui.cm;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.animation.AnticipateOvershootInterpolator;

public class GlowBackground extends Drawable implements ValueAnimator.AnimatorUpdateListener {

    private static final int MAX_CIRCLE_SIZE = 150;

    private final Paint mPaint;
    private Animator mAnimator;
    private float mCircleSize;

    public GlowBackground(int color) {
        mPaint = new Paint();
        mPaint.setColor(color);
    }

    private void startAnimation(boolean hide) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (hide && mCircleSize == 0f) {
            return;
        } else if (!hide && mCircleSize == MAX_CIRCLE_SIZE) {
            return;
        }
        mAnimator = getAnimator(hide);
        mAnimator.start();
    }

    private Animator getAnimator(boolean hide) {
        float start = mCircleSize;
        float end = MAX_CIRCLE_SIZE;
        if (hide) {
            end = 0f;
        }
        ValueAnimator animator = ObjectAnimator.ofFloat(start, end);
        animator.setInterpolator(new AnticipateOvershootInterpolator());
        animator.setDuration(300);
        animator.addUpdateListener(this);
        return animator;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawCircle(getBounds().width() / 2, getBounds().height() / 2, mCircleSize, mPaint);
    }

    @Override
    public void setAlpha(int i) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        mCircleSize = (Float) valueAnimator.getAnimatedValue();
        invalidateSelf();
    }

    public void hideGlow() {
        startAnimation(true);
    }

    public void showGlow() {
        startAnimation(false);
    }
}

