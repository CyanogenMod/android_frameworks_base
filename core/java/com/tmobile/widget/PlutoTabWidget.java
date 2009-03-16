package com.tmobile.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TabWidget;

public class PlutoTabWidget extends TabWidget {

	static final int PRT_MIN_TAB_WIDTH = 95;
	static final int LND_MIN_TAB_WIDTH = 165;
	private static final int MIN_TAB_HEIGHT = 64; // 50;

	boolean mTabSizeDirtyFlag = true;
	int mTabSize[] = new int[2];
	boolean isButtonRequired = false;

	Context mContext;

	public PlutoTabWidget(Context context) {
		super(context);
		mContext = context;
	}

	public PlutoTabWidget(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.tabWidgetStyle);
	}

	public PlutoTabWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
	}

	public void addView(View child) {
		super.addView(child);
		mTabSizeDirtyFlag = true;
	}

	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		// TODO: Check Device Orientation

		int[] tabSize = computeMaxTabSize();
		if (isButtonRequired) {
			setMeasuredDimension(tabSize[0] * getChildCount(), tabSize[1]);
		} 
		setTabsSize(tabSize);
	}

	/**
	 * 
	 * @return int[0] - width int[1] - height
	 */
	int[] computeMaxTabSize() {

		if (!mTabSizeDirtyFlag) {
			return mTabSize;
		}
		int tabWidth = 0;
		int tabHeight = 0;
		int count = getChildCount();

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			int mw = child.getWidth();
			// + child.getPaddingLeft() + child.getPaddingRight();
			tabWidth = tabWidth < mw ? mw : tabWidth;
			int mh = child.getHeight();
			tabHeight = tabHeight < mh ? mh : tabHeight;
		}

		tabWidth = tabWidth > PRT_MIN_TAB_WIDTH ? tabWidth : PRT_MIN_TAB_WIDTH;
		// tabWidth = tabWidth + getPaddingLeft() + getPaddingRight();
		tabHeight = tabHeight > MIN_TAB_HEIGHT ? tabHeight : MIN_TAB_HEIGHT;
		// tabHeight = tabHeight + getPaddingTop() + getPaddingBottom();

		mTabSize[0] = tabWidth;
		mTabSize[1] = tabHeight;
		mTabSizeDirtyFlag = false;
		return mTabSize;
	}

	void setTabsSize(int[] childSize) {
		int count = getChildCount();

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			LayoutParams params = new LinearLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			params.width = PRT_MIN_TAB_WIDTH < childSize[0] ? childSize[0]
					: PRT_MIN_TAB_WIDTH;
			params.height = MIN_TAB_HEIGHT < childSize[1] ? childSize[1]
					: MIN_TAB_HEIGHT;
			child.setLayoutParams(params);
			measureChild(child, params.width, params.height);
		}
	}
	
	public void setHost(TabScrollHost iPlutoTabHost) {
		TabScrollWidget scroll = (TabScrollWidget)getParent();
		scroll.setHost(iPlutoTabHost);
	}
}
