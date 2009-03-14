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
	TextView mSubHeader1;
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

        mSubHeader1 = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText1 = a.getString(R.styleable.HeaderLayout_subHeaderText1);
        if (subHeaderText1 != null) {
        	mSubHeader1.setText(subHeaderText1);
        }
        
        mSubHeader2 = (TextView)findViewById(R.id.subHeaderId2);
        String subHeaderText2 = a.getString(R.styleable.HeaderLayout_subHeaderText2);
        if (subHeaderText2 != null) {
        	mSubHeader2.setText(subHeaderText1);
        }

        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public CharSequence getSubHeaderText1() {
		return mSubHeader1.getText();
	}
	
	public void setSubHeaderText1(CharSequence text) {
		mSubHeader1.setText(text);
	}
	
	public CharSequence getSubHeaderText2() {
		return mSubHeader2.getText();
	}
	
	public void setSubHeaderText2(CharSequence text) {
		mSubHeader2.setText(text);
	}
}
