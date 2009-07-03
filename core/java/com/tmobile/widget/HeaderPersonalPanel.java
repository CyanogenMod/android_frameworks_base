package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderPersonalPanel extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader;
	private ImageView mImageView;
	private ImageView mImageView2;
	private ImageView mImageView3;
	
	public HeaderPersonalPanel(Context context) {
		this(context, null);
	}

	public HeaderPersonalPanel(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutPersonalPanel);
	}
	
	public HeaderPersonalPanel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		LayoutInflater.from(context).inflate(R.layout.tmobile_header_personal_panel, this, true);

		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout);

		mHeader = (TextView)findViewById(R.id.headerId);
        String headerText = a.getString(R.styleable.HeaderLayout_headerText);
        if (headerText != null) {
        	mHeader.setText(headerText);
        } 

        mSubHeader = (TextView)findViewById(R.id.subHeaderId1);
        String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText);
        if (subHeaderText != null) {
        	mSubHeader.setText(subHeaderText);
        }

        mImageView = (ImageView)findViewById(R.id.headerIcon);
        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
        if (icon != null) {
        	mImageView.setImageDrawable(icon);
        }

        mImageView2 = (ImageView)findViewById(R.id.header2Icon);
        icon = a.getDrawable(R.styleable.HeaderLayout_icon2Src);
        if (icon != null) {
            mImageView2.setImageDrawable(icon);
        }

        mImageView3 = (ImageView)findViewById(R.id.header3Icon);
        icon = a.getDrawable(R.styleable.HeaderLayout_icon3Src);
        if (icon != null) {
            mImageView3.setImageDrawable(icon);
        }
        
        a.recycle();
	}

	public CharSequence getHeaderText() {
		return mHeader.getText();
	}
	
	public void setHeaderText(CharSequence text) {
		mHeader.setText(text);
	}
	
	public CharSequence getSubHeaderText() {
		return mSubHeader.getText();
	}
	
	public void setSubHeaderText(CharSequence text) {
		mSubHeader.setText(text);
	}
	
	public Drawable getIcon1Src() {
        return mImageView.getDrawable();
    }
    
    public void setIcon1Src(Drawable drawable) {
        mImageView.setImageDrawable(drawable);
    }
    
    public void setIcon1Visibility(int flag) {
        mImageView.setVisibility(flag);
    }
    
    public Drawable getIcon2Src() {
        return mImageView2.getDrawable();
    }
    
    public void setIcon2Src(Drawable drawable) {
        mImageView2.setImageDrawable(drawable);
    }
    
    public void setIcon2Visibility(int flag) {
        mImageView2.setVisibility(flag);
    }
    
    public Drawable getIcon3Src() {
        return mImageView3.getDrawable();
    }
    
    public void setIcon3Src(Drawable drawable) {
        mImageView3.setImageDrawable(drawable);
    }
    
    public void setIcon3Visibility(int flag) {
        mImageView3.setVisibility(flag);
    }
	
}
