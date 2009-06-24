package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class ListItemLabelText extends LinearLayout {

    private TextView mItemLabel;
    private TextView mItemText;
    private ImageView mImage1;
    private ImageView mImage2;
    
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
    
    public CharSequence getItemText() {
        return mItemText.getText();
    }
    
    public void setItemText(CharSequence text) {
        mItemText.setText(text);
    }
    
    public void setItemTextNumLines(int num) {
        mItemText.setLines(num);
    }
    
    public int getItemTextNumLines() {
        return mItemText.getLineCount();
    }
    
    public Drawable getIcon1() {
        return mImage1.getDrawable();
    }

    public void setIcon1(Drawable image1) {
        mImage1.setImageDrawable(image1);
    }
    
    public void setIcon1Src(Bitmap bm) {
        mImage1.setImageBitmap(bm);
    }
    
    public void setIcon1ImageResource(int id) {
        mImage1.setImageResource(id);
    }
    
    public void setIcon1Visibility(int flag) {
        mImage1.setVisibility(flag);
    }

    public Drawable getIcon2() {
        return mImage2.getDrawable();
    }

    public void setIcon2(Drawable image2) {
        mImage2.setImageDrawable(image2);
    }
    
    public void setIcon2Src(Bitmap bm) {
        mImage2.setImageBitmap(bm);
    }
    
    public void setIcon2ImageResource(int id) {
        mImage2.setImageResource(id);
    }
    
    public void setIcon2Visibility(int flag) {
        mImage2.setVisibility(flag);
    }
    
}
