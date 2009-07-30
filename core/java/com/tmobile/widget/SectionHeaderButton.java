package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SectionHeaderButton extends LinearLayout {

	private TextView mHeader;
    private Button mButton;
	
	public SectionHeaderButton(Context context) {
		this(context, null);
	}

	public SectionHeaderButton(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.sectionHeaderLayoutLabel);
	}
	
	public SectionHeaderButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater.from(context).inflate(R.layout.tmobile_section_header_button, this, true);

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
		return mButton.getText();
	}

	public void setButtonText(CharSequence text) {
		mButton.setText(text);
	}

	public Button getButton() {
		return mButton;
	}
	
}
