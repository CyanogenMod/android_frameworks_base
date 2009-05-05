package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SectionHeaderImageButton extends LinearLayout {

	private TextView mHeader;
    private Button mButton;
	
	public SectionHeaderImageButton(Context context) {
		this(context, null);
	}

	public SectionHeaderImageButton(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.sectionHeaderLayoutLabel);
	}
	
	public SectionHeaderImageButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater.from(context).inflate(R.layout.tmobile_section_header_image_button, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout, defStyle, 0);

		mHeader = (TextView)findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        }
        
        mButton = (Button)findViewById(R.id.button1);
        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public Button getButton() {
		return mButton;
	}
	
	public void setButtonBackGround(int resource){
		mButton.setBackgroundResource(resource);
	}
}
