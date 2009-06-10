package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ListItemIconTwinLabelSecondaryText extends LinearLayout {

	private TextView mItemLabel;
	private TextView mItemLabel2;
	private ImageView mImage1;
	private ImageView mImage2;
	private ImageView mImage3;
	private ImageView mImage4;
	
	public ListItemIconTwinLabelSecondaryText(Context context) {
		this(context, null);
	}

	public ListItemIconTwinLabelSecondaryText(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.listItemIconTwinLabelSecondryText);
	}
	
	public ListItemIconTwinLabelSecondaryText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_list_item_icon_twin_label_secondary_text, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListItemLayout, defStyle, 0);

		mItemLabel = (TextView)findViewById(R.id.itemLabelText);
		String labelText = a.getString(R.styleable.ListItemLayout_itemLabelText);
        if (labelText != null) {
        	mItemLabel.setText(labelText);
        }
        
        mItemLabel2 = (TextView)findViewById(R.id.itemLabel2Text);
		String item1Text = a.getString(R.styleable.ListItemLayout_itemLabel2Text);
        if (item1Text != null) {
        	mItemLabel2.setText(labelText);
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
        
        mImage3 = (ImageView)findViewById(R.id.icon3);
        imageSrc = a.getDrawable(R.styleable.ListItemLayout_itemIcon3Src);
        if (imageSrc != null) {
        	mImage3.setImageDrawable(imageSrc);
        }
        
        mImage4 = (ImageView)findViewById(R.id.icon4);
        imageSrc = a.getDrawable(R.styleable.ListItemLayout_itemIcon4Src);
        if (imageSrc != null) {
        	mImage4.setImageDrawable(imageSrc);
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

	public void setItemLabel2Text(CharSequence text2) {
		mItemLabel2.setText(text2);
	}
	
	public Drawable getIcon1() {
		return mImage1.getDrawable();
	}

    public void setIcon1(Drawable image1) {
        mImage1.setImageDrawable(image1);
    }
    
    public void setIcon1(Bitmap image1) {
        mImage1.setImageBitmap(image1);
    }
    
    public void setIcon1ImageResource(int resId) {
        mImage1.setImageResource(resId);
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

    public void setIcon2(Bitmap image2) {
        mImage2.setImageBitmap(image2);
    }
    
    public void setIcon2ImageResource(int resId) {
        mImage2.setImageResource(resId);
    }

    public void setIcon2Visibility(int flag) {
        mImage2.setVisibility(flag);
    }
	
	public Drawable getIcon3() {
		return mImage3.getDrawable();
	}

    public void setIcon3(Drawable image3) {
        mImage3.setImageDrawable(image3);
    }

    public void setIcon3(Bitmap image3) {
        mImage3.setImageBitmap(image3);
    }
    
    public void setIcon3ImageResource(int resId) {
        mImage3.setImageResource(resId);
    }

    public void setIcon3Visibility(int flag) {
        mImage3.setVisibility(flag);
    }

	public Drawable getIcon4() {
		return mImage4.getDrawable();
	}

	public void setIcon4(Drawable image4) {
		mImage4.setImageDrawable(image4);
	}

    public void setIcon4(Bitmap image4) {
        mImage4.setImageBitmap(image4);
    }
    
    public void setIcon4ImageResource(int resId) {
        mImage4.setImageResource(resId);
    }

    public void setIcon4Visibility(int flag) {
        mImage4.setVisibility(flag);
    }
	
}
