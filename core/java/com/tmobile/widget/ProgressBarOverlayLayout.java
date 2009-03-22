package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ProgressBarOverlayLayout extends RelativeLayout {

	private Context mContext;
	private ProgressBar mProgressBar;
	
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
		mContext = context;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProgressOverlayLayout,
                defStyle, 0);
        final int progressType = a.getInt(R.styleable.ProgressOverlayLayout_progressType, PROGRESS_TYPE_INDICATOR);
        createProgressBar(progressType);
        a.recycle();
	}
	
	private void createProgressBar(int progressType) {
		switch(progressType) {
			case PROGRESS_TYPE_INDICATOR:
				mProgressBar = (ProgressBar) LayoutInflater.from(mContext).inflate(
						R.layout.tmobile_progressindicator_overlay, this, false);
				break;
			case PROGRESS_TYPE_BAR:
				mProgressBar = (ProgressBar) LayoutInflater.from(mContext).inflate(
						R.layout.tmobile_progressbar_overlay, this, false);
				break;
			case PROGRESS_TYPE_BAR_BOTTOM:
				mProgressBar = (ProgressBar) LayoutInflater.from(mContext).inflate(
						R.layout.tmobile_progressbar_bottom_overlay, this, false);
				break;
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		boolean isFixedWidthSpec = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
		boolean isFixedHeightSpec = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
		if (isFixedWidthSpec && isFixedHeightSpec) {
			addView(mProgressBar);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		} else {
			// RelativeLayout cannot layout out progress bar overlay correctly until it knows its children's dimensions.
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			addView(mProgressBar);
			widthMeasureSpec = isFixedWidthSpec ? widthMeasureSpec :
				MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
			heightMeasureSpec = isFixedHeightSpec ? heightMeasureSpec :
				MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}
	
	public ProgressBar getProgressBar() {
		return mProgressBar;
	}
}
