/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server.status;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

public class LatestItemContainer extends LinearLayout {
    private static final int MAJOR_MOVE = 50;
    private static final int ANIM_DURATION = 400;
    private final GestureDetector mGestureDetector;

    private Runnable mSwipeCallback = null;
    private TranslateAnimation outRight;
    private TranslateAnimation outLeft;
    private final Handler mHandler = new Handler();

    public LatestItemContainer(Context context, AttributeSet attrs) {
        super(context, attrs);

        mGestureDetector =
                new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                        if (mSwipeCallback != null) {
                            int dx = (int) (e2.getX() - e1.getX());
                            if (Math.abs(dx) > MAJOR_MOVE && Math.abs(vX) > Math.abs(vY)) {
                                if (vX > 0) {
                                    startAnimation(outRight);
                                } else {
                                    startAnimation(outLeft);
                                }
                                mHandler.postDelayed(mSwipeCallback, ANIM_DURATION);
                                return true;
                            }
                        }
                        return false;
                    }
                });
    }

    @Override
    public void onSizeChanged(int w, int h, int oldW, int oldH) {
        outRight = new TranslateAnimation(0, w, 0, 0);
        outLeft = new TranslateAnimation(0, -w, 0, 0);
        outRight.setDuration(ANIM_DURATION);
        outLeft.setDuration(ANIM_DURATION);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean handled = mGestureDetector.onTouchEvent(event);
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
                if (handled) {
                    return true;
                } else {
                    return super.onInterceptTouchEvent(event);
                }
        }
        return false;
    }

    public void setOnSwipeCallback(Runnable callback) {
        mSwipeCallback = callback;
    }
}
