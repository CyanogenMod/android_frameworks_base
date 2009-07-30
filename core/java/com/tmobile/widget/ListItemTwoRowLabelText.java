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

public class ListItemTwoRowLabelText extends LinearLayout {

    private TextView mItemLabel;
    private TextView mItemText;
    private TextView mItem2Label;
    private TextView mItem2Text;
    
    public ListItemTwoRowLabelText(Context context) {
        this(context, null);
    }

    public ListItemTwoRowLabelText(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.listItemLayout);
    }
    
    public ListItemTwoRowLabelText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_two_row_label_text, this, true);

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

        mItem2Label = (TextView)findViewById(R.id.item2Label);
        String label2Text = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (label2Text != null) {
            mItem2Label.setText(label2Text);
        }
        
        mItem2Text = (TextView)findViewById(R.id.item2Text);
        String item2Text = a.getString(R.styleable.ListItemLayout_itemText);
        if (item2Text != null) {
            mItem2Text.setText(item2Text);
        }
        a.recycle();
    }

    public CharSequence getItemLabelText() {
        return mItemLabel.getText();
    }
    
    public void setItemLabelText(CharSequence text) {
        mItemLabel.setText(text);
    }
    
    public void setItemLabelText(int id) {
        mItemLabel.setText(id);
    }
    
    public CharSequence getItemText() {
        return mItemText.getText();
    }
    
    public void setItemText(CharSequence text) {
        mItemText.setText(text);
    }
    
    public void setItemText(int id) {
        mItemText.setText(id);
    }
    
    public CharSequence getItem2LabelText() {
        return mItem2Label.getText();
    }
    
    public void setItem2LabelText(CharSequence text) {
        mItem2Label.setText(text);
    }
    
    public void setItem2LabelText(int id) {
        mItem2Label.setText(id);
    }
    
    public CharSequence getItem2Text() {
        return mItem2Text.getText();
    }
    
    public void setItem2Text(CharSequence text) {
        mItem2Text.setText(text);
    }
    
    public void setItem2Text(int id) {
        mItem2Text.setText(id);
    }
}
