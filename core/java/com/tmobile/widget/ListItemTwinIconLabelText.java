package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemTwinIconLabelText extends LinearLayout {

	private TextView mItemLabel;
	private ImageView mImage1;
	private ImageView mImage2;
	
	public ListItemTwinIconLabelText(Context context) {
		this(context, null);
	}

	public ListItemTwinIconLabelText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemTwinIconLayout);
	}
	
	public ListItemTwinIconLabelText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_twin_icon_label_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mItemLabel = (TextView)findViewById(R.id.itemLabelText);
		String labelText = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (labelText != null) {
        	mItemLabel.setText(labelText);
        }
        
        mImage1 = (ImageView)findViewById(R.id.icon1);
        Drawable imageSrc = a.getDrawable(R.styleable.ListItemLayout_itemIconSrc);
        if (imageSrc != null) {
        	mImage1.setImageDrawable(imageSrc);
        }
        
        mImage2 = (ImageView)findViewById(R.id.icon2);
        imageSrc = a.getDrawable(R.styleable.ListItemLayout_itemIcon2Src);
        if (imageSrc != null) {
        	mImage2.setImageDrawable(imageSrc);
        }
        
        a.recycle();
	}

	public CharSequence getItemLabelText() {
		return mItemLabel.getText();
	}
	
	public void setItemLabelText(CharSequence text) {
		mItemLabel.setText(text);
	}
	
	public Drawable getIcon1() {
		return mImage1.getDrawable();
	}

	public void setIcon1(Drawable image1) {
		mImage1.setImageDrawable(image1);
	}

	public Drawable getIcon2() {
		return mImage2.getDrawable();
	}

	public void setIcon2(Drawable image2) {
		mImage2.setImageDrawable(image2);
	}
	
}
