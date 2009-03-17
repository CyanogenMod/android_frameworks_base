package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderProductPanel extends LinearLayout {

	private TextView mHeader;
	private TextView mSubHeader;
	private TextView mSubHeader2;
	private TextView mSubLabel;

	public HeaderProductPanel(Context context) {
		this(context, null);
	}

	public HeaderProductPanel(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.headerLayoutProductPanel);
	}

	public HeaderProductPanel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater.from(context).inflate(
				R.layout.tmobile_header_product_panel, this, true);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.HeaderLayout);

		mHeader = (TextView) findViewById(R.id.headerId);
		String headerText = a.getString(R.styleable.HeaderLayout_headerText);
		if (headerText != null) {
			mHeader.setText(headerText);
		}

		mSubHeader = (TextView) findViewById(R.id.subHeaderId1);
		String subHeaderText = a.getString(R.styleable.HeaderLayout_subHeaderText);
		if (subHeaderText != null) {
			mSubHeader.setText(subHeaderText);
		}

		mSubHeader2 = (TextView) findViewById(R.id.subHeaderId2);
		subHeaderText = a.getString(R.styleable.HeaderLayout_subHeader2Text);
		if (subHeaderText != null) {
			mSubHeader2.setText(subHeaderText);
		}

		mSubLabel = (TextView) findViewById(R.id.subLabel);
		String subLabelText = a
				.getString(R.styleable.HeaderLayout_subLabelText);
		if (subLabelText != null) {
			mSubLabel.setText(subLabelText);
		}

		Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_icon2Src);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.starIcon);
			imageView.setImageDrawable(icon);
			imageView = (ImageView) findViewById(R.id.star2Icon);
			imageView.setImageDrawable(icon);
			imageView = (ImageView) findViewById(R.id.star3Icon);
			imageView.setImageDrawable(icon);
			imageView = (ImageView) findViewById(R.id.star4Icon);
			imageView.setImageDrawable(icon);
			imageView = (ImageView) findViewById(R.id.star5Icon);
			imageView.setImageDrawable(icon);
		}

		String buttonText = a.getString(R.styleable.HeaderLayout_buttonText);
		if (buttonText != null) {
			Button button = (Button) findViewById(R.id.button1);
			button.setText(buttonText);
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

	public CharSequence getSubHeader2Text() {
		return mSubHeader2.getText();
	}

	public void setSubHeader2Text(CharSequence text) {
		mSubHeader2.setText(text);
	}

	public CharSequence getSubLabelText() {
		return mSubLabel.getText();
	}

	public void setSubLabelText(CharSequence text) {
		mSubLabel.setText(text);
	}

}
