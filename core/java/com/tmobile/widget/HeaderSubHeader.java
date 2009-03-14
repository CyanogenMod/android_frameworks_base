package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderSubHeader extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader;
	
	public HeaderSubHeader(Context context) {
		this(context, null);
	}

	public HeaderSubHeader(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutSubHeader);
	}
	
	public HeaderSubHeader(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
				
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_subheader, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mHeader = (TextView)findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }

        mSubHeader = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText1);
        if (subHeaderText != null) {
        	mSubHeader.setText(subHeaderText);
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
		return mSubHeader.getText();
	}
	
	public void setSubHeaderText1(CharSequence text) {
		mSubHeader.setText(text);
	}
	
}
