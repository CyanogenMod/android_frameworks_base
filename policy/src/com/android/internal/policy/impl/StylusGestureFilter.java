/*
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
package com.android.internal.policy.impl;

import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

public class StylusGestureFilter extends SimpleOnGestureListener {

    public final static int SWIPE_UP = 1;
    public final static int SWIPE_DOWN = 2;
    public final static int SWIPE_LEFT = 3;
    public final static int SWIPE_RIGHT = 4;
    private int swipe_Min_Distance = 50;

    private int swipe_Min_Velocity = 100;

    private boolean running = true;

    private int action;

    private GestureDetector detector;

    private static StylusGestureFilter simple = null;

    private StylusGestureFilter() {

	this.detector = new GestureDetector(this);

    }

    public static StylusGestureFilter getFilter() {
	if (simple == null)
	    simple = new StylusGestureFilter();
	return simple;
    }

    public boolean onTouchEvent(MotionEvent event) {

	if (!this.running)
	    return false;

	boolean result = this.detector.onTouchEvent(event);

	return result;

    }

    public int getAction() {
	return this.action;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
	    float velocityY) {

	final float xDistance = Math.abs(e1.getX() - e2.getX());
	final float yDistance = Math.abs(e1.getY() - e2.getY());

	velocityX = Math.abs(velocityX);
	velocityY = Math.abs(velocityY);
	boolean result = false;

	if (velocityX > this.swipe_Min_Velocity
		&& xDistance > this.swipe_Min_Distance) {
	    if (e1.getX() > e2.getX()) { // right to left
		// Swipe Left
		action = SWIPE_LEFT;
	    } else {
		// Swipe Right
		action = SWIPE_RIGHT;
	    }
	    result = true;
	} else if (velocityY > this.swipe_Min_Velocity
		&& yDistance > this.swipe_Min_Distance) {
	    if (e1.getY() > e2.getY()) { // bottom to up
		// Swipe Up
		action = SWIPE_UP;
	    } else {
		// Swipe Down
		action = SWIPE_DOWN;
	    }
	    result = true;
	}
	return result;
    }

    @Override
    public boolean onDoubleTap(MotionEvent arg0) {

	return false;
    }

    public void onLongPress(MotionEvent e) {

    }

}
