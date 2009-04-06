package com.tmobile.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TabHost;
import android.widget.TabWidget;

import android.widget.AdapterView.OnItemSelectedListener;

public class CarouselTabWidget extends TabWidget {

	private Filmstrip mFilmstrip;
	private TabHost mTabHost;

	public CarouselTabWidget(Context context) {
		this(context, null);
	}

	public CarouselTabWidget(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CarouselTabWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mFilmstrip = new Filmstrip(context);
		
		this.addView(mFilmstrip, -1);

		mFilmstrip
				.setOnItemSelectedListener(new CarouselTabWidgetOnItemSelectedListener());
		
	}


	@Override
	public void focusCurrentTab(int index) {
		// Do nothing
	}


	public class CarouselTabWidgetOnItemSelectedListener extends
	Filmstrip.FilmstripOnItemSelectedListener {

		CarouselTabWidgetOnItemSelectedListener() {
			mFilmstrip.super();
		}

		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			super.onItemSelected(parent, view, position, id);
			
			CarouselTabWidget.this.requestFocus();
			
			mTabHost.setCurrentTab(position);
		}

		public void onNothingSelected(AdapterView<?> parent) {
			super.onNothingSelected(parent);

		}
	}


	public void setTabHost(TabHost aTabHost) {
		mTabHost = aTabHost;
	}

	public Filmstrip getFilmstrip() {
		return mFilmstrip;
	}
	
	public int getFilmstripSelection() {
		return mFilmstrip.getFilmstripSelection();
	}
	
	public void setOnItemSelectedListener(OnItemSelectedListener l){
		mFilmstrip.setOnItemSelectedListener(l);
	}
	
	@Override
	 public void onFocusChange(View v, boolean hasFocus) {
	 
		 if (hasFocus) {
		 // check to see the view is one of its child, if yes, make filmstrip to have the focus		 
			int no = getChildCount();
		
			for (int i = 0; i < no; i++) {
				View aChild = getChildAt(i);
				
				if (aChild == v) {
					mFilmstrip.requestFocus();
					return;
				}
			}
		 }
		 
		 super.onFocusChange(v, hasFocus);
	 }

	public void setFilmstripSelection(int position) {
		mFilmstrip.setFilmstripSelection(position);
	}
	
}
