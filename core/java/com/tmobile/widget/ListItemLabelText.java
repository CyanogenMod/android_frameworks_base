package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ListItemLabelText extends LinearLayout {

	private TextView mLabel;
	private TextView mText;
	
	public ListItemLabelText(Context context) {
		this(context, null);
	}

	public ListItemLabelText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemLayout);
	}
	
	public ListItemLabelText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_label_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mLabel = (TextView)findViewById(R.id.itemLabel);
		String headerText = a.getString(R.styleable.ListItemLayout_itemLabel);
        if (headerText != null) {
        	mLabel.setText(headerText);
        }
        
        mText = (TextView)findViewById(R.id.itemText);
        String buttonText = a.getString(R.styleable.ListItemLayout_itemText1);
        if (buttonText != null) {
        	mText.setText(buttonText);
        }
        a.recycle();
	}

	public CharSequence getLabelText() {
		return mLabel.getText();
	}
	
	public void setLabelText(CharSequence text) {
		mLabel.setText(text);
	}
	
	public CharSequence getText() {
		return mText.getText();
	}
	
	public void setText(CharSequence text) {
		mText.setText(text);
	}
	
}
