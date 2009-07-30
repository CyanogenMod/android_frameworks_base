package com.tmobile.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.android.internal.R;

public class TabScrollHost extends android.widget.TabHost {

	public TabScrollHost(Context context) {
		super(context);
	}

	public TabScrollHost(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setup() {
		super.setup();
		TabScrollTabWidget widget = (TabScrollTabWidget) findViewById(R.id.tabs);
		widget.setHost(this);
	}
    
	//TODO: setIndicator using IndicatorStrategy
}
