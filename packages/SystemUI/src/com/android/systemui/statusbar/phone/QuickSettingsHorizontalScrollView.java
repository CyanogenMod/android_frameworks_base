package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
public class QuickSettingsHorizontalScrollView extends HorizontalScrollView {

    enum EventStates {
        SCROLLING,
        FLING
    }

    private EventStates systemState = EventStates.SCROLLING;

    public QuickSettingsHorizontalScrollView(Context context) {
        super(context);
    }

    public QuickSettingsHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QuickSettingsHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    Runnable mSnapRunnable = new Runnable(){
        @Override
        public void run() {
            snapItems();
            systemState = EventStates.SCROLLING;
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            systemState = EventStates.FLING;
        } else if (action == MotionEvent.ACTION_DOWN) {
            systemState = EventStates.SCROLLING;
            removeCallbacks(mSnapRunnable);
        }
        return super.onTouchEvent(ev);
    }

    private void snapItems() {
        Rect parentBounds = new Rect();
        getDrawingRect(parentBounds);
        Rect childBounds = new Rect();
        ViewGroup parent = (ViewGroup) getChildAt(0);
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            view.getHitRect(childBounds);
            if (childBounds.right >= parentBounds.left && childBounds.left <= parentBounds.left) {
                // First partially visible child
                if ((childBounds.right - parentBounds.left) >= (parentBounds.left - childBounds.left)) {
                    smoothScrollTo(Math.abs(childBounds.left), 0);
                } else {
                    smoothScrollTo(Math.abs(childBounds.right), 0);
                }
                break;
            }
        }
    }

    // Overwrite measureChildX as we want our child to be able to tell the
    // parents width but do not impose any limits (AT_MOST; AT_MAX)
    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();

        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop
                + mPaddingBottom, lp.height);

        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(parentWidthMeasureSpec) - (mPaddingLeft + mPaddingRight),
                MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);

        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(parentWidthMeasureSpec) -
                        (mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin
                        + widthUsed), MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        if (systemState == EventStates.SCROLLING) {
            return;
        }
        if (Math.abs(l - oldl) <= 1 && systemState == EventStates.FLING) {
            removeCallbacks(mSnapRunnable);
            postDelayed(mSnapRunnable, 100);
        }
    }
}
