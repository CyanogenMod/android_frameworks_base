package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class TextFieldLabel extends LinearLayout {
	private TextView mLabel;
	//private TextView mLabel2;
	private EditText mTextField;
	
	public TextFieldLabel(Context context) {
		this(context, null);
	}

	public TextFieldLabel(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public TextFieldLabel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.TextFieldLabel, defStyle, 0);

		 String labelPosition = a.getString(R.styleable.TextFieldLabel_labelPosition);
		 if (labelPosition.equalsIgnoreCase("top")) {
			 LayoutInflater.from(context).inflate(R.layout.tmobile_textfield_label_top, this, true);
		 } else {
			LayoutInflater.from(context).inflate(R.layout.tmobile_textfield_label_left, this, true);
		}
		 
		 mLabel = (TextView) findViewById(R.id.labelId);
		 if (mLabel != null) {
			 
			 mLabel.setTextSize(16);
		 String headerText = a.getString(R.styleable.TextFieldLabel_labelText);
	        if (headerText != null) {
	        	
	        	
	   			 mLabel.setText(headerText);
	   			 
	   		 }
	      
	       // mLabel.setTextColor(R.)
	        	
	        	//mLabel.setText(new String ("label"));
	        }
		 
		 
		 mTextField = (EditText) findViewById(R.id.textFieldId);
	}
	
	private void oldTextFieldLabel(Context context, AttributeSet attrs, int defStyle) {
		//super(context, attrs, defStyle);
		this.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
    	
		mLabel = new TextView(context, attrs, R.attr.textViewStyle);
		
		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.TextFieldLabel, defStyle, 0);

		
		String headerText = a.getString(R.styleable.TextFieldLabel_labelText);
        if (headerText != null) {
        	mLabel.setText(headerText);
        	
        	//mLabel.setText(new String ("label"));
        }
        String labelPosition = a.getString(R.styleable.TextFieldLabel_labelPosition);
		
//        if (labelPosition.equalsIgnoreCase("left")) {
//        	mLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
//	
//        } else {
        	mLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        	
 //       }
        
		mLabel.setGravity(Gravity.CENTER);
		mLabel.setFocusable(false);
		mLabel.setFocusableInTouchMode(false);
			addView(mLabel);
		
		mTextField = new EditText(context, attrs, R.attr.editTextStyle);
//		mTextField.setText(new String ("text"));
//		if (labelPosition.equalsIgnoreCase("left")) {
//			mTextField.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
//		} else {
			mTextField.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        	
  //      }
		
		
		mTextField.setHorizontallyScrolling(true);
		addView(mTextField);
		
		a.recycle();
		
//		LayoutInflater.from(context).inflate(R.layout.tmobile_textfield_label, this, true);

//		TypedArray a = 
//            context.obtainStyledAttributes(attrs, R.styleable.HeaderLayout);
//
//		mHeader = (TextView)findViewById(R.id.headerId);
//        String headerText = a.getString(R.styleable.HeaderLayout_headerText);
//        if (headerText != null) {
//        	mHeader.setText(headerText);
//        } 
//
//        Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
//        if (icon != null) {
//        	ImageView imageView = (ImageView)findViewById(R.id.headerIcon);
//        	imageView.setImageDrawable(icon);
//        } 
//
//        mSubHeader = (TextView)findViewById(R.id.subHeaderId1);
//        String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText);
//        if (subHeaderText != null) {
//        	mSubHeader.setText(subHeaderText);
//        }
//        
//        a.recycle();
	}

//	@Override
//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		// TODO Auto-generated method stub
//		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//		
//		mTextField.getMeasuredHeight();
//		
//	}
	
//	public CharSequence getHeaderText() {
//		return mHeader.getText();
//	}
//	
//	public void setHeaderText(CharSequence text) {
//		mHeader.setText(text);
//	}
//	
//	public CharSequence getSubHeaderText() {
//		return mSubHeader.getText();
//	}
//	
//	public void setSubHeaderText(CharSequence text) {
//		mSubHeader.setText(text);
//	}
	
	
}
