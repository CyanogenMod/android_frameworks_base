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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.android.systemui.R;

public class LatestItemContainer extends LinearLayout {
    private boolean mEventsControlledByDispatcher = false;
    private ItemTouchDispatcher mDispatcher = null;
    private Runnable mSwipeCallback = null;
    private final Handler mHandler = new Handler();
    private final Point mStartPoint = new Point();
    private int mTouchSlop;

    public LatestItemContainer(final Context context, AttributeSet attrs) {
        super(context, attrs);
        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
    }

    public void finishSwipe(boolean toRight) {
        int id = toRight ? R.anim.slide_out_right_basic : R.anim.slide_out_left_basic;
        Animation animation = AnimationUtils.loadAnimation(getContext(), id);
        startAnimation(animation);
        mHandler.postDelayed(mSwipeCallback, animation.getDuration());
        mEventsControlledByDispatcher = false;
    }

    public void stopSwipe() {
        reset();
        mEventsControlledByDispatcher = false;
    }

    public void setEventsControlledByDispatcher() {
        mEventsControlledByDispatcher = true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mDispatcher != null) {
            boolean handled = false;

            /*
             * Only call into dispatcher when we're not registered with it yet,
             * otherwise we get into a loop
             */
            if (!mEventsControlledByDispatcher) {
                handled = mDispatcher.handleTouchEvent(event);
            }

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mStartPoint.set((int) event.getX(), (int) event.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    int diffX = ((int) event.getX()) - mStartPoint.x;
                    int diffY = ((int) event.getY()) - mStartPoint.y;
                    if (Math.abs(diffX) > mTouchSlop && Math.abs(diffX) > Math.abs(diffY)) {
                        mDispatcher.setItem(this);
                    }
                    scrollTo(-diffX, 0);
                    break;
                 case MotionEvent.ACTION_UP:
                    if (!handled) {
                        reset();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    /*
                     * Ignore cancel events after registering with the dispatcher
                     * as they will appear sometimes (when ExpandedView takes over
                     * event control). The dispatcher will call stopSwipe() when
                     * the gesture is aborted.
                     */
                    if (!mEventsControlledByDispatcher) {
                        mDispatcher.releaseItem(this);
                        reset();
                    }
                    break;
            }
        }

        return super.dispatchTouchEvent(event);
    }

    private void reset() {
        scrollTo(0, 0);
    }

    public void setOnSwipeCallback(ItemTouchDispatcher dispatcher, Runnable callback) {
        mDispatcher = dispatcher;
        mSwipeCallback = callback;
    }
}
