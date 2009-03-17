package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderButton extends LinearLayout {

	private TextView mHeader;
	private Button mButton;
	
	public HeaderButton(Context context) {
		this(context, null);
	}

	public HeaderButton(Context context, AttributeSet attrs) {
		this(context, attrs, com.android.internal.R.attr.headerLayoutButton);
	}
	
	public HeaderButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_button, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mHeader = (TextView)findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }
        
		mButton = (Button)findViewById(R.id.button1);
        String buttonText = a.getString(R.styleable.HeaderLayout_buttonText);
        if (buttonText != null) {
        	mButton.setText(buttonText);
        }
        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public CharSequence getButtonText() {
		return getButton().getText();
	}

	public void setButtonText(CharSequence text) {
		getButton().setText(text);
	}

	public Button getButton() {
		return mButton;
	}
}
