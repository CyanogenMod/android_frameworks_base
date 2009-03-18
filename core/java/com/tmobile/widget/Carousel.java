package com.tmobile.widget;

import com.tmobile.widget.CarouselTabWidget.CarouselTabWidgetOnItemSelectedListener;

import android.app.LocalActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.GestureDetector.OnGestureListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TabHost;

public class Carousel extends TabHost implements OnGestureListener {

	private CarouselTabWidget mTabWidget;
	private CarouselTabContentLayout mCarouselTabContentLayout;
	private GestureDetector mGestureDetector;
	
	protected LocalActivityManager mLocalActivityManager = null;

	public Carousel(Context context) {
		this(context, null);
	}

	public Carousel(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setOnTabChangedListener(null);
		
		mGestureDetector = new GestureDetector(this);
	}

	public class CarouselViewAdapter extends BaseAdapter {

		public int getCount() {
			return mTabWidget.getFilmstrip().getFilmstripSize();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			String name = mTabWidget.getFilmstrip().getFilmstripItem(position)
					.getName();
			Intent intent = mTabWidget.getFilmstrip()
					.getFilmstripItem(position).getIntent();

			Window window = mLocalActivityManager.startActivity(name, intent);
			View windowDecor = (window != null) ? window.getDecorView() : null;

			return windowDecor;
		}
	}

	public class CarouselOnItemSelectedListener extends
			CarouselTabWidgetOnItemSelectedListener {

		CarouselOnItemSelectedListener() {
			mTabWidget.super();
		}

		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			super.onItemSelected(parent, view, position, id);
			
			if ( (mCarouselTabContentLayout != null) && (mTabWidget != null) ) {
				mCarouselTabContentLayout.setSelection(mTabWidget.getFilmstripSelection(),
					true);
			}
		}

	}

	public void addTab(TabSpec tabSpec, Context context, String name,
			Intent intent) {

		super.addTab(tabSpec);

		if (mTabWidget != null) {
			Filmstrip filmstrip = mTabWidget.getFilmstrip();

			if (filmstrip != null) {
				
				filmstrip.add(context, name, intent);

				mLocalActivityManager.startActivity(name, intent);

			}
		}
	}

	
	@Override
	public void setup(LocalActivityManager activityGroup) {

		super.setup(activityGroup);

		mLocalActivityManager = activityGroup;

		int childCount = getChildCount();

		// A Hack to setup reference to CarouselTabWidget and CarouselLayout
		for (int i = 0; i < childCount; i++) {
			View child = getChildAt(i);

			if (child instanceof CarouselTabWidget) {
				mTabWidget = (CarouselTabWidget) child;

			} else if (child instanceof CarouselTabContentLayout) {
				mCarouselTabContentLayout = (CarouselTabContentLayout) child;
			}
			
		}

		if (mTabWidget != null) {
			mTabWidget.setTabHost(this);
			mTabWidget
					.setOnItemSelectedListener(new CarouselOnItemSelectedListener());

		}

		if (mCarouselTabContentLayout != null) {
			mCarouselTabContentLayout.setAdapter(new CarouselViewAdapter());
		}
	}

	 public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		   
			float x1 = e1.getX();
			float x2 = e2.getX();
			
			int selectedPosition = mCarouselTabContentLayout.getSelectedPosition();
			int newSelectedPosition = selectedPosition;
			
			if (x1 > x2) {
				newSelectedPosition -= 1;
			} else {
				newSelectedPosition += 1;
			}
			
			int size = mTabWidget.getFilmstrip().getFilmstripSize();
			
			if (newSelectedPosition < 0) {
				newSelectedPosition = 0;
			} else if (newSelectedPosition >= size) {
				newSelectedPosition = size -1;
			}
			
			mCarouselTabContentLayout.setSelection(newSelectedPosition);
			mTabWidget.setFilmstripSelection(newSelectedPosition);
			
			return true;
		}

	
	    public boolean onDown(MotionEvent e) {		
			return true;
		}
	 
		public void onLongPress(MotionEvent e) {
			// Do nothing
		}

		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
				float distanceY) {
			// let onFling handles the event
			return false;
		}

		public void onShowPress(MotionEvent e) {
			// Do nothing
			
		}

		public boolean onSingleTapUp(MotionEvent e) {
			
			return true;
		}
		
		@Override
	    public boolean onTouchEvent(MotionEvent event) {

	        // Give everything to the gesture detector
	        return mGestureDetector.onTouchEvent(event);

	    }

		public void setTabWidget(CarouselTabWidget tabWidget) {
			mTabWidget = tabWidget;
		}
}
