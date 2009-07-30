package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemLabelTwinText extends LinearLayout {

	private TextView mItemLabel;
	private TextView mItemText;
	private TextView mItem2Text;
	
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

		mItemLabel = (TextView)findViewById(R.id.itemLabel);
		String headerText = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (headerText != null) {
        	mItemLabel.setText(headerText);
        }
        
        mItemText = (TextView)findViewById(R.id.item1Text);
        String buttonText = a.getString(R.styleable.ListItemLayout_itemText);
        if (buttonText != null) {
        	mItemText.setText(buttonText);
        }
        
        mItem2Text = (TextView)findViewById(R.id.item2Text);
        buttonText = a.getString(R.styleable.ListItemLayout_item2Text);
        if (buttonText != null) {
        	mItem2Text.setText(buttonText);
        }
        a.recycle();
	}

	public CharSequence getItemLabelText() {
		return mItemLabel.getText();
	}
	
	public void setItemLabelText(CharSequence text) {
		mItemLabel.setText(text);
	}
	
	public CharSequence getItemText() {
		return mItemText.getText();
	}
	
	public void setItemText(CharSequence text) {
		mItemText.setText(text);
	}
	
	public CharSequence getItem2Text() {
		return mItem2Text.getText();
	}
	
	public void setItem2Text(CharSequence text) {
		mItem2Text.setText(text);
	}
	
}
