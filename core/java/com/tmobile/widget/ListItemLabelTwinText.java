package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemLabelTwinText extends LinearLayout {

	private TextView mLabel;
	private TextView mText1;
	private TextView mText2;
	
	public ListItemLabelTwinText(Context context) {
		this(context, null);
	}

	public ListItemLabelTwinText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemLayoutItemLabelTwinText);
	}
	
	public ListItemLabelTwinText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_label_twin_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mLabel = (TextView)findViewById(R.id.itemLabel);
		String headerText = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (headerText != null) {
        	mLabel.setText(headerText);
        }
        
        mText1 = (TextView)findViewById(R.id.item1Text);
        String buttonText = a.getString(R.styleable.ListItemLayout_itemText);
        if (buttonText != null) {
        	mText1.setText(buttonText);
        }
        
        mText2 = (TextView)findViewById(R.id.item2Text);
        buttonText = a.getString(R.styleable.ListItemLayout_item2Text);
        if (buttonText != null) {
        	mText2.setText(buttonText);
        }
        a.recycle();
	}

	public CharSequence getLabel() {
		return mLabel.getText();
	}
	
	public void setLabel(CharSequence text) {
		mLabel.setText(text);
	}
	
	public CharSequence getText1() {
		return mText1.getText();
	}
	
	public void setText1(CharSequence text) {
		mText1.setText(text);
	}
	
	public CharSequence getText2() {
		return mText2.getText();
	}
	
	public void setText2(CharSequence text) {
		mText2.setText(text);
	}
	
}
