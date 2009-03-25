package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TabHost.OnTabChangeListener;

public class TabScrollWidget extends LinearLayout {

	private static final int INVALID_POSITION = -1;
	int mOldCurrentTab = INVALID_POSITION;
	int mCurrentTab = INVALID_POSITION;
	int mSelectedPosition = INVALID_POSITION;
	int mNoTabsDisplayed = 2;
	int mTabSize[] = new int[2];
	boolean mScrolledByClick;

	TabScrollHost mPlutoTabHost;
	TabScrollTabWidget mPlutoTabWidget;
	View mTab;
	Button mMoveRight;
	Button mMoveLeft;
	Drawable mNavButtonBottomStrip;

	Context mContext;

	public TabScrollWidget(Context context) {
		this(context, null);
		mContext = context;
	}

	public TabScrollWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		View.inflate(context, R.layout.tmobile_tab_scroll_widget, this);

		mSelectedPosition = 0;

		mPlutoTabWidget = (TabScrollTabWidget) findViewById(R.id.tabs);

		mMoveRight = (Button) findViewById(R.id.moveRight);
		mMoveRight.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				moveRight(true);
			}

		});

		mMoveLeft = (Button) findViewById(R.id.moveLeft);
		mMoveLeft.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				moveLeft(true);
			}

		});
		mNavButtonBottomStrip = mContext.getResources().getDrawable(
				R.drawable.tab_bottom_left);
	}

	private void moveLeft(boolean clicked) {
		int scrollX = mPlutoTabWidget.getScrollX();
		int currentTab = mPlutoTabHost.getCurrentTab();
		if (clicked) {
			mScrolledByClick = true;
			currentTab = currentTab - 1;
			mPlutoTabHost.setCurrentTab(currentTab);
		}

		View currentTabView = mPlutoTabHost.getCurrentTabView();
		int currentTabLeft = currentTabView.getLeft();

		if (currentTabLeft < scrollX & currentTab >= 0) {
			int x = currentTabView.getMeasuredWidth();
			int y = 0; // btn1.getTop();
			mPlutoTabWidget.scrollBy(-x, y);
		}
		if(currentTab < mPlutoTabWidget.getChildCount() - 1) {
			mMoveRight.setEnabled(true);
		}
		if(currentTab == 0) {
			mMoveLeft.setEnabled(false);
		} 
		mPlutoTabWidget.requestLayout();
	}

	private void moveRight(boolean clicked) {
		int scrollX = mPlutoTabWidget.getScrollX();
		int currentTab = mPlutoTabHost.getCurrentTab();
		if (clicked) {
			mScrolledByClick = true;
			currentTab = currentTab + 1;
			mPlutoTabHost.setCurrentTab(currentTab);
		}

		LinearLayout myChild = (LinearLayout) getChildAt(0);
		int tabsDisplayAreaRight = myChild.getRight();
		View currentTabView = mPlutoTabHost.getCurrentTabView();
		int currentTabRight = currentTabView.getRight();

		if (currentTabRight > tabsDisplayAreaRight + scrollX
				& currentTab < mPlutoTabWidget.getChildCount()) {
			int x = currentTabView.getMeasuredWidth();
			int y = 0; // btn1.getTop();
			mPlutoTabWidget.scrollBy(x, y);
		}
		if(currentTab > 0) {
			mMoveLeft.setEnabled(true);
		}
		if(currentTab == mPlutoTabWidget.getChildCount()-1) {
			mMoveRight.setEnabled(false);
		}
		mPlutoTabWidget.requestLayout();
	}

	OnTabChangeListener onTabChangeListener = new OnTabChangeListener() {
		public void onTabChanged(String tabId) {
			int currentTab = mPlutoTabHost.getCurrentTab();
			if (currentTab == mOldCurrentTab) {
				return;
			} else if (currentTab > mOldCurrentTab) {
				if (mScrolledByClick) {
					mScrolledByClick = false;
				} else if (mPlutoTabWidget.isButtonRequired) {
						moveRight(false);
				}
			} else {
				if (mScrolledByClick) {
					mScrolledByClick = false;
				} else if (mPlutoTabWidget.isButtonRequired) {
						moveLeft(false);
				}
			}
			mOldCurrentTab = currentTab;
		}
	};

	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (isNavButtonsRequired()) {
			placeButtons(widthMeasureSpec, heightMeasureSpec);
		} else {
			mPlutoTabWidget.isButtonRequired = false;
			removeNavButtons();
			measureChildren(widthMeasureSpec, heightMeasureSpec);
		}

		// setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
	}

	private void placeButtons(int widthMeasureSpec, int heightMeasureSpec) {

		LayoutParams params = new LinearLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		measureChildren(widthMeasureSpec, heightMeasureSpec);
		// measureChild(plutoTabWidget, widthMeasureSpec, heightMeasureSpec);

		int childwidth = 0;
		int childheight = 0;
		mPlutoTabWidget.isButtonRequired = true;
		addNavButtons();
		childwidth = getMeasuredWidth() - (mMoveRight.getMeasuredWidth() * 2);
		childheight = getMeasuredHeight() + getPaddingTop()
				+ getPaddingBottom();
		params.width = childwidth;
		params.height = childheight;
		LinearLayout myChild = (LinearLayout) getChildAt(0);
		myChild.setLayoutParams(params);
		measureChild(myChild, childwidth, childheight);
		resizeTabToFitContainer(childwidth);
	}

	private void resizeTabToFitContainer(int containerWidth) {
		int[] tabSize = mPlutoTabWidget.computeMaxTabSize();
		int tabWidth = tabSize[0];
		if (containerWidth <= 0 || tabWidth <= 0) {
			return;
		}
		int tabCount = containerWidth / tabWidth;
		int extraSpace = containerWidth - (tabCount * tabWidth);
		tabWidth = tabWidth + extraSpace / tabCount;
		tabSize[0] = tabWidth;
		mPlutoTabWidget.setTabsSize(tabSize);
	}

	private void drawNavButtonBottomImage(Canvas canvas) {
		Rect selBounds = new Rect(); // Bounds of the selected tab indicator
		selBounds.left = mMoveLeft.getLeft();
		selBounds.right = mMoveRight.getRight();
		int myHeight = getHeight();
		int[] drawableState = mPlutoTabHost.getCurrentTabView()
				.getDrawableState();
		mNavButtonBottomStrip.setState(drawableState);
		mNavButtonBottomStrip.setBounds(selBounds.left, myHeight
				- mNavButtonBottomStrip.getIntrinsicHeight(), getWidth(),
				myHeight);
		mNavButtonBottomStrip.draw(canvas);
		invalidate();
	}

	public void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		drawNavButtonBottomImage(canvas);
	}

	protected boolean isNavButtonsRequired() {
		int[] tabSize = mPlutoTabWidget.computeMaxTabSize();

		int totalTabsWidth = tabSize[0] * mPlutoTabWidget.getChildCount();

		if (totalTabsWidth > getMeasuredWidth()) {
			return true;
		}
		return false;
	}

	private void addNavButtons() {
		mMoveRight.setVisibility(View.VISIBLE);
		mMoveLeft.setVisibility(View.VISIBLE);
	}

	private void removeNavButtons() {
		mMoveRight.setVisibility(View.GONE);
		mMoveLeft.setVisibility(View.GONE);
	}

	public void setHost(TabScrollHost iPlutoTabHost) {
		mPlutoTabHost = iPlutoTabHost;
		mPlutoTabHost.setOnTabChangedListener(onTabChangeListener);
		mOldCurrentTab = mPlutoTabHost.getCurrentTab();
	}
}
