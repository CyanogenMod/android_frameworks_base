package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderSubHeaderIconButton extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader;
	private Button mButton;
		
	public HeaderSubHeaderIconButton(Context context) {
		this(context, null);
	}

	public HeaderSubHeaderIconButton(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutSubHeaderIconButton);
	}
	
	public HeaderSubHeaderIconButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_subheader_icon_button, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout);

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

        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc1);
        if (icon != null) {
        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon);
        	imageView.setImageDrawable(icon);
        }
        
        mButton = (Button)findViewById(R.id.button1);
        String buttonText = a.getString(R.styleable.HeaderLayout_buttonText1);
        if (buttonText != null) {
        	mButton.setText(buttonText);
        }
        a.recycle();
	}

	public Button getButton() {
		return mButton;
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
