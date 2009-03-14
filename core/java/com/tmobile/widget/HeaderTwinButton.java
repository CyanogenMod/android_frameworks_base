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
	private Button mButton1;
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
        
        mButton1 = (Button)findViewById(R.id.button1);
        String buttonText1 = a.getString(R.styleable.HeaderLayout_buttonText1);
        if (buttonText1 != null) {
        	mButton1.setText(buttonText1);
        }
        
        mButton2 = (Button)findViewById(R.id.button2);
        String buttonText2 = a.getString(R.styleable.HeaderLayout_buttonText2);
        if (buttonText2 != null) {
        	mButton2.setText(buttonText2);
        }
        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public CharSequence getButtonText1() {
		return getButton1().getText();
	}

	public void setButtonText1(CharSequence text) {
		getButton1().setText(text);
	}
	
	public CharSequence getButtonText2() {
		return getButton2().getText();
	}

	public void setButtonText2(CharSequence text) {
		getButton2().setText(text);
	}

	public Button getButton1() {
		return mButton1;
	}
	
	public Button getButton2() {
		return mButton2;
	}
	
}
