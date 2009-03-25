package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemLabelText extends LinearLayout {

	private TextView mItemLabel;
	private TextView mItemText;
	
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

		mItemLabel = (TextView)findViewById(R.id.itemLabel);
		String labelText = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (labelText != null) {
        	mItemLabel.setText(labelText);
        }
        
        mItemText = (TextView)findViewById(R.id.itemText);
        String itemText = a.getString(R.styleable.ListItemLayout_itemText);
        if (itemText != null) {
        	mItemText.setText(itemText);
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
	
}
