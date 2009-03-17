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

public class HeaderPersonalPanel extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader;
	
	public HeaderPersonalPanel(Context context) {
		this(context, null);
	}

	public HeaderPersonalPanel(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutPersonalPanel);
	}
	
	public HeaderPersonalPanel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_personal_panel, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout);

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

        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon);
        	imageView.setImageDrawable(icon);
        }
        
        icon = a.getDrawable(R.styleable.HeaderLayout_icon2Src);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.header2Icon);
        	imageView.setImageDrawable(icon);
        }

        icon = a.getDrawable(R.styleable.HeaderLayout_icon3Src);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.header3Icon);
        	imageView.setImageDrawable(icon);
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
	
}
