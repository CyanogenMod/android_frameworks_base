package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

/**
 * Created by roman on 11/5/15.
 */
public class QSViewPager extends ViewPager {

    private static final String TAG = "QSViewPager";

    protected static final float SCROLL_PERCENT = .10f;

    public QSViewPager(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = child.getMeasuredHeight();
            if (h > height) height = h;
        }
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void animatePagerTransition(final boolean forward) {
        ValueAnimator animator = ValueAnimator.ofInt(0, getWidth());
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (isFakeDragging()) {
                    endFakeDrag();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (isFakeDragging()) {
                    endFakeDrag();
                }
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            private int oldDragPosition = 0;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (isFakeDragging()) {
                    int dragPosition = (Integer) animation.getAnimatedValue();
                    int dragOffset = dragPosition - oldDragPosition;
                    oldDragPosition = dragPosition;
                    fakeDragBy(dragOffset * (forward ? -1 : 1));
                }
            }
        });
        if (beginFakeDrag()) {
            animator.setDuration(500);
            animator.start();
        } else {
            Log.e(TAG, "can't start fake drag?");
        }
    }
}
