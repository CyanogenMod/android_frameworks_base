package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemTwinLabelText extends LinearLayout {

	private TextView mLabel1;
	private TextView mLabel2;
	private TextView mText1;
	
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

		mLabel1 = (TextView)findViewById(R.id.itemLabelText);
		String text = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (text != null) {
        	mLabel1.setText(text);
        }
        
        mLabel2 = (TextView)findViewById(R.id.itemLabel2Text);
        text = a.getString(R.styleable.ListItemLayout_itemLabel2Text);
        if (text != null) {
        	mLabel2.setText(text);
        }
        
        mText1 = (TextView)findViewById(R.id.itemText);
        text = a.getString(R.styleable.ListItemLayout_itemText);
        if (text != null) {
        	mText1.setText(text);
        }
        a.recycle();
	}

	public CharSequence getLabel1() {
		return mLabel1.getText();
	}
	
	public void setLabel1(CharSequence text) {
		mLabel1.setText(text);
	}
	
	public CharSequence getLabel2() {
		return mLabel2.getText();
	}
	
	public void setLabel2(CharSequence text) {
		mLabel2.setText(text);
	}
	
	public CharSequence getText1() {
		return mText1.getText();
	}
	
	public void setText1(CharSequence text) {
		mText1.setText(text);
	}
	
}
