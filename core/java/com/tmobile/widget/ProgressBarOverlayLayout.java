package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

public class ProgressBarOverlayLayout extends FrameLayout {
    private ProgressBar mProgressBar;
    private final int mProgressBarType;
    
    private static final int PROGRESS_TYPE_INDICATOR = 0;
    private static final int PROGRESS_TYPE_BAR = 1;
    private static final int PROGRESS_TYPE_BAR_BOTTOM = 2;

    public ProgressBarOverlayLayout(Context context) {
        this(context, null);
    }

    public ProgressBarOverlayLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public ProgressBarOverlayLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ProgressOverlayLayout, defStyle, 0);
        final int progressType = a.getInt(R.styleable.ProgressOverlayLayout_progressType, 
                PROGRESS_TYPE_INDICATOR);
        mProgressBarType = progressType;
        a.recycle();
        
        init(context);
    }
    
    private void init(Context context) {
        int layout;
        
        switch(mProgressBarType) {
            case PROGRESS_TYPE_INDICATOR:
                layout = R.layout.tmobile_progressindicator_overlay;
                break;
            case PROGRESS_TYPE_BAR:
                layout = R.layout.tmobile_progressbar_overlay;
                break;
            case PROGRESS_TYPE_BAR_BOTTOM:
                layout = R.layout.tmobile_progressbar_bottom_overlay;
                break;
            default:
                throw new IllegalArgumentException("Unknown progressType " + mProgressBarType);
        }
        
        LayoutInflater.from(context).inflate(layout, this);
        mProgressBar = (ProgressBar)getChildAt(0);
    }

    public int getProgressBarType() {
        return mProgressBarType;
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        /* Make sure the ProgressBar is last. This is necessary because drawing
         * on FrameLayout occurs in order of addition. */
        if (mProgressBar != null && (index == -1 || index == getChildCount())) {
            super.addView(child, getChildCount() - 1, params);
        } else {
            super.addView(child, index, params);
        }
    }
}
