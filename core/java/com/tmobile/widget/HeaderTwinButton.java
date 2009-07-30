package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderTwinButton extends LinearLayout {
	
	private TextView mHeader;
	private Button mButton;
	private Button mButton2;
	
	public HeaderTwinButton(Context context) {
		this(context, null);
	}

	public HeaderTwinButton(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutTwinButton);
	}
	
	public HeaderTwinButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_twin_button, this, true);

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
        
        mButton2 = (Button)findViewById(R.id.button2);
        String button2Text = a.getString(R.styleable.HeaderLayout_button2Text);
        if (button2Text != null) {
        	mButton2.setText(button2Text);
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
	
	public CharSequence getButton2Text() {
		return getButton2().getText();
	}

	public void setButton2Text(CharSequence text) {
		getButton2().setText(text);
	}

	public Button getButton() {
		return mButton;
	}
	
	public Button getButton2() {
		return mButton2;
	}
	
}
