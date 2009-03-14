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

public class HeaderSecondaryLargeIcon extends LinearLayout {
	
	private TextView mHeader;
	private TextView mSubHeader;
	
	public HeaderSecondaryLargeIcon(Context context) {
		this(context, null);
	}

	public HeaderSecondaryLargeIcon(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutSecondaryLargeIcon);
	}
	
	public HeaderSecondaryLargeIcon(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_secondary_large_icon, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout);

		mHeader = (TextView)findViewById(R.id.headerId);
        String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        } 

        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc1);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon1);
        	imageView.setImageDrawable(icon);
        }
        
        icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc2);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon2);
        	imageView.setImageDrawable(icon);
        }

        mSubHeader = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText1 = a.getString(R.styleable.HeaderLayout_subHeaderText1);
        if (subHeaderText1 != null) {
        	mSubHeader.setText(subHeaderText1);
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
