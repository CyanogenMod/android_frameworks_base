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

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.KeyEvent;
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
            // Draw mouse cursor
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
    
    private void mouseScrollUp()
    {
	long downTime = SystemClock.uptimeMillis();
	long eventTime = SystemClock.uptimeMillis();
	Instrumentation inst = new Instrumentation();
	MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, mouseX, mouseY, 0);
	inst.sendPointerSync(event);
	eventTime = SystemClock.uptimeMillis();
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 5.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 10.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 15.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 25.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 35.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY + 45.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, mouseX, mouseY + 60.0f, 0);
	inst.sendPointerSync(event);
    }

    private void mouseScrollDown()
    {
	long downTime = SystemClock.uptimeMillis();
	long eventTime = SystemClock.uptimeMillis();
	Instrumentation inst = new Instrumentation();
	MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, mouseX, mouseY, 0);
	inst.sendPointerSync(event);
	eventTime = SystemClock.uptimeMillis();
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 5.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 10.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 15.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 25.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 35.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mouseX, mouseY - 45.0f, 0);
	inst.sendPointerSync(event);
	event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, mouseX, mouseY - 60.0f, 0);
	inst.sendPointerSync(event);
    }

    public void addTouchEvent(MotionEvent event) {
	lastX = mouseX;
	lastY = mouseY;
	mouseX = event.getX();
	mouseY = event.getY();
	postInvalidate();
    }

    public void addKeyEvent(KeyEvent event) {
	if (event.getKeyCode() == 0x5c) {
		mouseScrollUp();
	}
	else if (event.getKeyCode() == 0x5d) {
		mouseScrollDown();
	}
	postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        addTouchEvent(event);
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return super.onTrackballEvent(event);
    }
 
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        addKeyEvent(event);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

}
