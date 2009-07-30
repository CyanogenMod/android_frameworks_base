package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderTwinSubHeader extends LinearLayout {

	TextView mHeader;
	TextView mSubHeader;
	TextView mSubHeader2;
	
	public HeaderTwinSubHeader(Context context) {
		this(context, null);
	}

	public HeaderTwinSubHeader(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutTwinSubHeader);
	}
	
	public HeaderTwinSubHeader(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
				
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_twin_subheader, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mHeader = (TextView)findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }

        mSubHeader = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText);
        if (subHeaderText != null) {
        	mSubHeader.setText(subHeaderText);
        }
        
        mSubHeader2 = (TextView)findViewById(R.id.subHeaderId2);
        subHeaderText = a.getString(R.styleable.HeaderLayout_subHeader2Text);
        if (subHeaderText != null) {
        	mSubHeader2.setText(subHeaderText);
        }

        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public CharSequence getSubHeaderText() {
		return mSubHeader.getText();
	}
	
	public void setSubHeaderText(CharSequence text) {
		mSubHeader.setText(text);
	}
	
	public CharSequence getSubHeader2Text() {
		return mSubHeader2.getText();
	}
	
	public void setSubHeader2Text(CharSequence text) {
		mSubHeader2.setText(text);
	}
}
