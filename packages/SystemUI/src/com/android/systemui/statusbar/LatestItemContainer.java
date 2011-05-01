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

package com.android.systemui.statusbar;

import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.android.systemui.R;

public class LatestItemContainer extends LinearLayout {
    private final GestureDetector mGestureDetector;

    private Runnable mSwipeCallback = null;

    private final Handler mHandler = new Handler();

    private final Point mStartPoint = new Point();

    public LatestItemContainer(final Context context, AttributeSet attrs) {
        super(context, attrs);

        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                        if (mSwipeCallback != null) {
                            if (Math.abs(vX) > Math.abs(vY)) {
                                int id;
                                if (vX > 0) {
                                    id = R.anim.slide_out_right_basic;
                                } else {
                                    id = R.anim.slide_out_left_basic;
                                }
                                Animation animation = AnimationUtils.loadAnimation(context, id);
                                startAnimation(animation);
                                mHandler.postDelayed(mSwipeCallback, animation.getDuration());
                                return true;
                            }
                        }
                        return false;
                    }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mSwipeCallback != null) {
            boolean handled = mGestureDetector.onTouchEvent(event);
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_OUTSIDE:
                case MotionEvent.ACTION_CANCEL:
                    reset();
                    break;
                case MotionEvent.ACTION_UP:
                    if (!handled) {
                        reset();
                    }
                    return handled;
                case MotionEvent.ACTION_MOVE:
                    int diffX = ((int) event.getX()) - mStartPoint.x;
                    scrollTo(-diffX, 0);
                    break;
                case MotionEvent.ACTION_DOWN:
                    mStartPoint.x = (int) event.getX();
                    break;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    private void reset() {
        scrollTo(0, 0);
    }

    public void setOnSwipeCallback(Runnable callback) {
        mSwipeCallback = callback;
    }
}
