/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

public class MousePointerView extends View {
    private static final String TAG = "MousePointer";

    private float mouseX;
    private float mouseY;
    private float lastX;
    private float lastY;

    private Paint mPaint;
    private Path mPath;

    public MousePointerView(Context c) {
        super(c);
        setFocusable(true);
        mPaint = new Paint();
	mPaint.setAntiAlias(true);
        mPath = new Path();
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
	if (mouseX == 0 && mouseY == 0) {
	}
	else if ((lastX == 0 && lastY == 0) || lastX - mouseX < 10.0f || lastY - mouseY < 10.0f) {
            // Draw mouse pointer
	    mPath.rewind();
            mPath.moveTo(mouseX, mouseY);
            mPath.lineTo(mouseX + 12.0f, mouseY + 12.0f);
            mPath.lineTo(mouseX + 7.0f, mouseY + 12.0f);
            mPath.lineTo(mouseX + 11.0f, mouseY + 20.0f);
            mPath.lineTo(mouseX + 8.0f, mouseY + 21.0f);
            mPath.lineTo(mouseX + 4.0f, mouseY + 13.0f);
            mPath.lineTo(mouseX + 0.0f, mouseY + 17.0f);
            mPath.close();
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaint.setStrokeWidth(1);
            mPaint.setColor(0xffffffff);
            canvas.drawPath(mPath, mPaint);
	    mPaint.setStyle(Paint.Style.STROKE);
	    mPaint.setStrokeWidth(1);
            mPaint.setColor(0xff000000);
	    mPath.offset(0,0);
	    canvas.drawPath(mPath, mPaint);
	}
    }
    
    public void addTouchEvent(MotionEvent event) {
	lastX = mouseX;
	lastY = mouseY;
	mouseX = event.getX();
	mouseY = event.getY();
	postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        addTouchEvent(event);
        return true;
    }
}
