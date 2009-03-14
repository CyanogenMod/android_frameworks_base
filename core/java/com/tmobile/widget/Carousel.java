package com.tmobile.widget;

import android.app.LocalActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.TabHost;

public class Carousel extends TabHost {

	private CarouselTabWidget mTabWidget;
	private CarouselLayout mCarouselLayout;
	protected LocalActivityManager mLocalActivityManager = null;

	public Carousel(Context context) {
		super(context);
	}

	public Carousel(Context context, AttributeSet attrs) {
		super(context, attrs);
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

	public void addTab(TabSpec tabSpec, Context context, String name,
			Intent intent) {

		super.addTab(tabSpec);

		if (mTabWidget != null) {
			Filmstrip filmstrip = mTabWidget.getFilmstrip();

			if (filmstrip != null) {
				filmstrip.add(context, name, intent);
				
				mLocalActivityManager.startActivity(name,
						intent);
				
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
			View child = getChildAt(0);

			if (child instanceof CarouselTabWidget) {
				mTabWidget = (CarouselTabWidget) child;

			} else if (child instanceof CarouselLayout) {
				mCarouselLayout = (CarouselLayout) child;
			}
		}

		if (mTabWidget != null) {
			mTabWidget.setTabHost(this);
		}

		if (mCarouselLayout != null) {
			mCarouselLayout.setAdapter(new CarouselViewAdapter());
		}
	}

}
