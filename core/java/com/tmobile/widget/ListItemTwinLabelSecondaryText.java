package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemTwinLabelSecondaryText extends LinearLayout {

	private TextView mItemLabel;
	private TextView mItemLabel2Text;
	private TextView mItemText;
	private TextView mItem2Text;
	
	public ListItemTwinLabelSecondaryText(Context context) {
		this(context, null);
	}

	public ListItemTwinLabelSecondaryText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemTwinLabelSecondaryText);
	}
	
	public ListItemTwinLabelSecondaryText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_twin_label_secondary_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mItemLabel = (TextView)findViewById(R.id.itemLabelText);
		String text = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (text != null && !"".equals(text)) {
        	mItemLabel.setText(text);
        } else {
        	mItemLabel.setVisibility(GONE);
        }

        mItemText = (TextView)findViewById(R.id.itemText);
        text = a.getString(R.styleable.ListItemLayout_itemText);
        if (text != null) {
        	mItemText.setText(text);
        }
        
        mItemLabel2Text = (TextView)findViewById(R.id.itemLabel2Text);
		text = a.getString(R.styleable.ListItemLayout_itemLabel2Text);
        if (text != null) {
        	mItemLabel2Text.setText(text);
        }
        
        mItem2Text = (TextView)findViewById(R.id.item2Text);
        text = a.getString(R.styleable.ListItemLayout_item2Text);
        if (text != null) {
        	mItem2Text.setText(text);
        }
       
        a.recycle();
	}

	public CharSequence getItemLabelText() {
		return mItemLabel.getText();
	}
	
	public void setItemLabelText(CharSequence text) {
		if(text != null && !"".equals(text)) {
        	mItemLabel.setText(text);
        	mItemLabel.setVisibility(VISIBLE);
		} else {
			mItemLabel.setVisibility(GONE);
		}
	}
	
	public CharSequence getItemText() {
		return mItemText.getText();
	}
	
	public void setItemText(CharSequence text) {
  	    mItemText.setText(text);
	}
	
	public CharSequence getItemLabel2Text() {
		return mItemLabel2Text.getText();
	}
	
	public void setItemLabel2Text(CharSequence text) {
		mItemLabel2Text.setText(text);
	}
	
	public CharSequence getItem2Text() {
		return mItem2Text.getText();
	}
	
	public void setItem2Text(CharSequence text) {
		mItem2Text.setText(text);
	}
}
