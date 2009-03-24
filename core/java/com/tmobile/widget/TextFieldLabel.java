package com.tmobile.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class TextFieldLabel extends LinearLayout {
	private TextView mLabel;
	private EditText mTextField;

	public TextFieldLabel(Context context) {
		this(context, null);
	}

	public TextFieldLabel(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextFieldLabel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.TextFieldLabel, defStyle, 0);

		String labelPosition = a
				.getString(R.styleable.TextFieldLabel_labelPosition);
		if (labelPosition.equalsIgnoreCase("top")) {
			LayoutInflater.from(context).inflate(
					R.layout.tmobile_textfield_label_top, this, true);
		} else {
			LayoutInflater.from(context).inflate(
					R.layout.tmobile_textfield_label_left, this, true);
		}

		mLabel = (TextView) findViewById(R.id.labelId);
		if (mLabel != null) {

			String labelText = a
					.getString(R.styleable.TextFieldLabel_labelText);
			if (labelText != null) {

				mLabel.setText(labelText);

			}

		}

		mTextField = (EditText) findViewById(R.id.textFieldId);
	}

	public EditText getEditText() {
		return mTextField;
	}
}
