package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

public class ToggleButton extends android.widget.ToggleButton {

	private static final int DISABLE_FOREGROUND_COLOR = 0xffC0C0C0;
	private static final int FOREGROUND_COLOR = 0xff000000;
	private Paint mPaint;
	private int mOffTextOffsetX;
	private int mOnTextOffsetX;
	private int mImageH;
	private int mTextGap;
	

	public ToggleButton(Context context) {
		super(context);
	}

	public ToggleButton(Context context, AttributeSet attrSet) {

		this(context, attrSet, R.attr.buttonStyleToggle);
	}

	public ToggleButton(Context context, AttributeSet attrSet,
			int defStyle) {
		super(context, attrSet, defStyle);

		mPaint = getPaint();

		TypedArray a = context.obtainStyledAttributes(attrSet,
				R.styleable.ToggleButton, defStyle, 0);

		// retrieve (x,y) offset of the ON, OFF text
		mOffTextOffsetX = a.getInt(
				R.styleable.ToggleButton_offTextOffsetX, 0);
		mOnTextOffsetX = a.getInt(R.styleable.ToggleButton_onTextOffsetX,
				0);

		mTextGap = a.getInt(R.styleable.ToggleButton_textGap, 0);
		
		a.recycle();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int w = getMeasuredWidth();
		mImageH = getMeasuredHeight();
		
		// Measure the text (beware: ascent is a negative number)
		int ascent = (int) mPaint.ascent();
		
		int textHeight = (int) (-ascent + mPaint.descent());

		textHeight += getPaddingTop() + getPaddingBottom();

		// Add the height to the widget dimension
		setMeasuredDimension(w, mImageH + textHeight + mTextGap);
	}


	@Override
	protected void onDraw(Canvas canvas) {

		mPaint.setTextAlign(Paint.Align.CENTER);

		mPaint.setColor(isEnabled() ? (isChecked() ? FOREGROUND_COLOR
				: DISABLE_FOREGROUND_COLOR) : DISABLE_FOREGROUND_COLOR);


		// draw the 'on' and 'off' text of the toggle button, center aligned.
		CharSequence onText = getTextOn();
		drawText(canvas, onText, mOnTextOffsetX);

		mPaint.setColor(isEnabled() ? (isChecked() ? DISABLE_FOREGROUND_COLOR
				: FOREGROUND_COLOR) : DISABLE_FOREGROUND_COLOR);

		CharSequence offText = getTextOff();
		drawText(canvas, offText, mOffTextOffsetX);

	}

	private void drawText(Canvas canvas, CharSequence text, float textOffsetX) {

		Rect textBounds = new Rect();
		mPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
		
		float textOffsetY = getHeight() - mImageH - mTextGap;
		
		canvas
				.drawText(text, 0, text.length(), textOffsetX, textOffsetY,
						mPaint);

	}
}
