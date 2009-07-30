package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemTwinLabelText extends LinearLayout {

	private TextView mItemLabel;
	private TextView mItemLabel2;
	private TextView mItemText;
	
	public ListItemTwinLabelText(Context context) {
		this(context, null);
	}

	public ListItemTwinLabelText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemLayoutItemTwinLabelText);
	}
	
	public ListItemTwinLabelText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_twin_label_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mItemLabel = (TextView)findViewById(R.id.itemLabelText);
		String text = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (text != null) {
        	mItemLabel.setText(text);
        }
        
        mItemLabel2 = (TextView)findViewById(R.id.itemLabel2Text);
        text = a.getString(R.styleable.ListItemLayout_itemLabel2Text);
        if (text != null) {
        	mItemLabel2.setText(text);
        }
        
        mItemText = (TextView)findViewById(R.id.itemText);
        text = a.getString(R.styleable.ListItemLayout_itemText);
        if (text != null) {
        	mItemText.setText(text);
        }
        a.recycle();
	}

	public CharSequence getItemLabelText() {
		return mItemLabel.getText();
	}
	
	public void setItemLabelText(CharSequence text) {
		mItemLabel.setText(text);
	}
	
	public CharSequence getItemLabel2Text() {
		return mItemLabel2.getText();
	}
	
	public void setItemLabel2Text(CharSequence text) {
		mItemLabel2.setText(text);
	}
	
	public CharSequence getItemText() {
		return mItemText.getText();
	}
	
	public void setItemText(CharSequence text) {
		mItemText.setText(text);
	}
	
}
