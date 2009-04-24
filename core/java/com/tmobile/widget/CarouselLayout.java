package com.tmobile.widget;

import com.tmobile.widget.Carousel.CarouselViewAdapter;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsoluteLayout;
import android.widget.Adapter;
import android.widget.Scroller;

public class CarouselLayout extends AbsoluteLayout {

    private class ScrollerRunnable implements Runnable {

        private Scroller mScroller;
        private int mLastScroll;

        public ScrollerRunnable() {
            mScroller = new Scroller(getContext());
        }

        public void scrollBy(int distance, int duration) {
             
            if (distance == 0) return;
            
            removeCallbacks(this);
            
            mLastScroll = 0;
            mScroller.startScroll(0, 0, distance, 0, duration);
            post(this);
        }
        
        public void stop() {
            removeCallbacks(this);
            endScroll();
        }
        
        private void endScroll() {
            // Force the scroller's status to finished (without setting its position to the end)
            mScroller.forceFinished(true);
        }

        public void run() {
            if (getChildCount() == 0) {
                endScroll();
                return;
            }            
            final Scroller scroller = mScroller;
            boolean hasMoreToScroll = scroller.computeScrollOffset();
            final int x = scroller.getCurrX();
            int changeInX = x - mLastScroll;

            handleScroll(changeInX);

            if (hasMoreToScroll) {
                mLastScroll = x;
                post(this);
            } else {
                endScroll();
                onScrollFinished();
            }
        }
    }
    
    public static final int INVALID_POSITION = -1;
    
    private int mOldSelectedPosition = INVALID_POSITION;
    private int mSelectedPosition = INVALID_POSITION;
    private CarouselViewAdapter mAdapter;
    private int mScrollDuration = 0;
    
    private ScrollerRunnable mScrollerRunnable = new ScrollerRunnable();

    public CarouselLayout(Context context) {
        this(context, null);
    }
    
    public CarouselLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public CarouselLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

//        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CarouselLayout, defStyle, 0);
//        mScrollDuration = a.getInt(R.styleable.CarouselLayout_animationDuration, SCROLL_DURATION_DEFAULT);
//        a.recycle();
    }
    
    
    
    public void setScrollDuration(int scrollDuration) {
		mScrollDuration = scrollDuration;
	}

	private void resetLayout() {
        removeAllViewsInLayout();
        mOldSelectedPosition = INVALID_POSITION;
        mSelectedPosition = INVALID_POSITION;
        invalidate();
    }

    public void setAdapter(CarouselViewAdapter adapter) {
        if (null != mAdapter) {
            //mAdapter.unregisterDataSetObserver(mDataSetObserver);
            resetLayout();
        }
        mAdapter = adapter;
        
        if (mAdapter != null) {
            //mDataSetObserver = new AdapterDataSetObserver();
            //mAdapter.registerDataSetObserver(mDataSetObserver);

            int position = mAdapter.getCount() > 0 ? 0 : INVALID_POSITION;
            setSelection(position);
        }
    }
    
    public Adapter getAdapter() {
        return mAdapter;
    }
    
    public int getSelectedPosition() {
    	return mSelectedPosition;
  	}


    private void removeOldSelectedView() {
        if (INVALID_POSITION == mOldSelectedPosition) return;

        View theOldSelectedView = mAdapter.getView(mOldSelectedPosition);
        removeView(theOldSelectedView);
        
        mOldSelectedPosition = INVALID_POSITION;
    }
  
    
    private void snapToCurrentSelection() {
        mScrollerRunnable.stop();
        removeOldSelectedView();

        
        if (INVALID_POSITION != mSelectedPosition) {
            View theSelectedView = mAdapter.getView(mSelectedPosition);
            theSelectedView.setLayoutParams(new AbsoluteLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                                                            ViewGroup.LayoutParams.FILL_PARENT, 0, 0));
            requestLayout();
            theSelectedView.setFocusable(true);
            theSelectedView.setFocusableInTouchMode(true);
            ((ViewGroup) theSelectedView).setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
            
            // Do not request focus in order to keep the focus in Gallery when it has the focus
            //theSelectedView.requestFocus();
        }
	  
    }
    
    public void setSelection(int position) {
        setSelection(position, false);
    }
    
    public void setSelection(int position, boolean animate) {
        if (position == mSelectedPosition) return;
        
        snapToCurrentSelection();
        mOldSelectedPosition = mSelectedPosition;
        mSelectedPosition = position;
        if (INVALID_POSITION != mSelectedPosition) {
            int xView = 0;
            
            View theSelectedView = mAdapter.getView(mSelectedPosition);
            
            if (animate && (INVALID_POSITION != mOldSelectedPosition)) {
                
                xView = (mSelectedPosition > mOldSelectedPosition) ? getWidth() : -getWidth();
                mScrollerRunnable.scrollBy(-xView, mScrollDuration);
            } else {
                removeOldSelectedView();
            }
            
            
            ViewParent parent = theSelectedView.getParent();
    		
    		if (parent != null) {
    			if (parent == this) {
    				this.removeView(theSelectedView);
    			}
    		
    		}
    		
            addView(theSelectedView,
                    new AbsoluteLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                                    ViewGroup.LayoutParams.FILL_PARENT, xView, 0));
                                                    
                 
        
        } else {
           removeOldSelectedView();
       }
        requestLayout();
    }

    private void handleScroll(int inChangeInX) {
           
        if (getChildCount() == 0) return;
        
        for (int i = getChildCount() - 1; i >= 0; i--) {
            getChildAt(i).offsetLeftAndRight(inChangeInX);
        }
        invalidate();
    }

    private void onScrollFinished() {
        snapToCurrentSelection();
    }
}
