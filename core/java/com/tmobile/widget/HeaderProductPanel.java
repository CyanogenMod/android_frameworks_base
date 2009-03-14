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
	private TextView mSubHeader1;
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

		mSubHeader1 = (TextView) findViewById(R.id.subHeaderId1);
		String subHeaderText1 = a
				.getString(R.styleable.HeaderLayout_subHeaderText1);
		if (subHeaderText1 != null) {
			mSubHeader1.setText(subHeaderText1);
		}

		mSubHeader2 = (TextView) findViewById(R.id.subHeaderId2);
		String subHeaderText2 = a
				.getString(R.styleable.HeaderLayout_subHeaderText2);
		if (subHeaderText2 != null) {
			mSubHeader2.setText(subHeaderText2);
		}

		mSubLabel = (TextView) findViewById(R.id.subLabel);
		String subLabelText1 = a
				.getString(R.styleable.HeaderLayout_subLabel);
		if (subLabelText1 != null) {
			mSubLabel.setText(subLabelText1);
		}

		Drawable icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc1);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon1);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc2);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon2);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc3);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon3);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc4);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon4);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_iconSrc5);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon5);
			imageView.setImageDrawable(icon);
		}

		icon = a.getDrawable(R.styleable.HeaderLayout_headerIcon);
		if (icon != null) {
			ImageView imageView = (ImageView) findViewById(R.id.headerIcon);
			imageView.setImageDrawable(icon);
		}

		String buttonText = a.getString(R.styleable.HeaderLayout_buttonText1);
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

	public CharSequence getSubHeaderText1() {
		return mSubHeader1.getText();
	}

	public void setSubHeaderText1(CharSequence text) {
		mSubHeader1.setText(text);
	}

	public CharSequence getSubHeaderText2() {
		return mSubHeader2.getText();
	}

	public void setSubHeaderText2(CharSequence text) {
		mSubHeader2.setText(text);
	}

	public CharSequence getSubLabelText() {
		return mSubLabel.getText();
	}

	public void setSubLabelText(CharSequence text) {
		mSubLabel.setText(text);
	}

}
