package com.tmobile.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.internal.R;

public class ToggleButton extends android.widget.ToggleButton {

	private static final int DISABLE_FOREGROUND_COLOR = 0xff9E9E9E;
	private static final int FOREGROUND_COLOR = 0xff333333;
	private Paint mPaint;
	private int mImageH;
	

	public ToggleButton(Context context) {
		super(context);
	}

	public ToggleButton(Context context, AttributeSet attrSet) {

		this(context, attrSet, R.attr.buttonStyleToggle);
	}

	public ToggleButton(Context context, AttributeSet attrSet, int defStyle) {
		super(context, attrSet, defStyle);

		mPaint = getPaint();
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
		setMeasuredDimension(w, mImageH + textHeight);
	}


	@Override
	protected void onDraw(Canvas canvas) {

		mPaint.setTextAlign(Paint.Align.CENTER);
		int quarterWidth = getWidth() >> 2;
		
		mPaint.setColor((isEnabled() && isChecked()) ? FOREGROUND_COLOR : DISABLE_FOREGROUND_COLOR);
		CharSequence onText = getTextOn();
		drawText(canvas, onText, quarterWidth);

		mPaint.setColor((isEnabled() && !isChecked()) ? FOREGROUND_COLOR : DISABLE_FOREGROUND_COLOR);
		CharSequence offText = getTextOff();
		drawText(canvas, offText, 3*quarterWidth);

	}

	private void drawText(Canvas canvas, CharSequence text, float textOffsetX) {

		Rect textBounds = new Rect();
		mPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
		
		float textOffsetY = getHeight() - mImageH;
		
		canvas.drawText(text, 0, text.length(), textOffsetX, textOffsetY, mPaint);

	}
}
