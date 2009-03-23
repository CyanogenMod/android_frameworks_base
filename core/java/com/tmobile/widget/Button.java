package com.tmobile.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;

public class Button extends android.widget.Button {

	private static final int DISABLE_FOREGROUND_COLOR = 0xffC0C0C0;
	private static final int FOREGROUND_COLOR = 0xff000000;
	private static final int FOCUS_FOREGROUND_COLOR = 0xffffffff;
	
	public Button(Context context) {
		super(context);
	}

	public Button(Context context, AttributeSet attrSet) {
		super(context, attrSet);
	}

	public Button(Context context, AttributeSet attrSet,
			int defStyle) {
		super(context, attrSet, defStyle);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		TextPaint paint = getPaint();
		
		paint.setTextAlign(Paint.Align.CENTER);
		
		paint.setColor(isEnabled() ? (hasFocus() ? FOCUS_FOREGROUND_COLOR
				: FOREGROUND_COLOR) : DISABLE_FOREGROUND_COLOR);
		
		if (hasFocus()) {
			paint.setTypeface(Typeface.DEFAULT_BOLD);
		} else {
			paint.setTypeface(Typeface.DEFAULT);
		}
		
		CharSequence text = getText();
		
		float textHeight = paint.getFontMetrics().ascent + paint.getFontMetrics().descent;
	
		canvas.drawText(text, 0, text.length(), getWidth()/2, (getHeight() - textHeight)/2, paint);

	}
}
