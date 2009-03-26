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

public class HeaderSubHeaderIcon extends LinearLayout {
	
	private TextView mHeader;
	private TextView mSubHeader;
	private ImageView mImageView;
		
	public HeaderSubHeaderIcon(Context context) {
		this(context, null);
	}

	public HeaderSubHeaderIcon(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutSubHeaderIcon);
	}
	
	public HeaderSubHeaderIcon(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_subheader_icon, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mImageView = (ImageView)findViewById(R.id.headerIcon);
        Drawable mIcon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
        if (mIcon != null) {
        	mImageView.setImageDrawable(mIcon);
        }

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
	
	public Drawable getIconSrc() {
		return mImageView.getDrawable();
	}
	
	public void setIconSrc(Drawable drawable) {
		mImageView.setImageDrawable(drawable);
	}
}
