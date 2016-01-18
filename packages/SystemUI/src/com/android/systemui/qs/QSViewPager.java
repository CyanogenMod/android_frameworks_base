package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

public class QSViewPager extends ViewPager {

    private static final String TAG = "QSViewPager";

    protected static final float SCROLL_PERCENT = .10f;
    private boolean mPagingEnabled;
    QSDragPanel mDragPanel;

    public QSViewPager(Context context) {
        super(context);
    }

    public void setDragPanel(QSDragPanel p) {
        mDragPanel = p;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mDragPanel.isEditing();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (direction < 0
                && mDragPanel.isDragging()
                && mPagingEnabled
                && getCurrentItem() == 1) {
            // can't scroll left while not editing, OR dragging on the first page
            return false;
        }
        return super.canScrollHorizontally(direction);
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mPagingEnabled) {
            return super.onInterceptTouchEvent(event);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mPagingEnabled) {
            return super.onTouchEvent(event);
        }
        return false;
    }

    public void setPagingEnabled(boolean enabled) {
        if (mPagingEnabled == enabled) return;
        mPagingEnabled = enabled;
        //Log.i(TAG, "setPagingEnabled() called with " + "enabled = [" + enabled + "]");
        if (getCurrentItem() > 0 && !mPagingEnabled) {
            //Log.w(TAG, "resetting to item 0 because paging is disabled.");
            setCurrentItem(0, true);
        }
    }
}
