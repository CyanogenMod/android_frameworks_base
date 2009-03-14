package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderTwinSubHeaderIcon extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader1;
	private TextView mSubHeader2;
	
	public HeaderTwinSubHeaderIcon(Context context) {
		this(context, null);
	}

	public HeaderTwinSubHeaderIcon(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutTwinSubHeaderIcon);
	}
	
	public HeaderTwinSubHeaderIcon(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_twin_subheader_icon, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);


        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc1);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon);
        	imageView.setImageDrawable(icon);
        }

        mHeader = (TextView)findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }

        mSubHeader1 = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText1);
        if (subHeaderText != null) {
        	mSubHeader1.setText(subHeaderText);
        }   
        
        mSubHeader2 = (TextView)findViewById(R.id.subHeaderId2);
        subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText2);
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
