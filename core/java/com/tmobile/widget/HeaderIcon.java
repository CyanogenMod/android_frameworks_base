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

public class HeaderIcon extends LinearLayout {

	private TextView mHeader;
	
	public HeaderIcon(Context context) {
		this(context, null);
	}

	public HeaderIcon(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutIcon);
	}
	
	public HeaderIcon(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_icon, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mHeader = (TextView)findViewById(R.id.headerId);
        String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }

        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon);
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
}
