/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;

public class DeadZone extends View {
    public static final String TAG = "DeadZone";

    public static final boolean DEBUG = false;
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    private static final boolean CHATTY = true; // print to logcat when we eat a click

    private boolean mShouldFlash;
    private float mFlashFrac = 0f;

    private int mSizeMax;
    private int mSizeMin;
    // Upon activity elsewhere in the UI, the dead zone will hold steady for
    // mHold ms, then move back over the course of mDecay ms
    private int mHold, mDecay;
    private boolean mVertical;
    private boolean mStartFromRight;
    private long mLastPokeTime;

    private final Runnable mDebugFlash = new Runnable() {
        @Override
        public void run() {
            ObjectAnimator.ofFloat(DeadZone.this, "flash", 1f, 0f).setDuration(150).start();
        }
    };

    public DeadZone(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeadZone(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DeadZone,
                defStyle, 0);

        mHold = a.getInteger(R.styleable.DeadZone_holdTime, 0);
        mDecay = a.getInteger(R.styleable.DeadZone_decayTime, 0);

        mSizeMin = a.getDimensionPixelSize(R.styleable.DeadZone_minSize, 0);
        mSizeMax = a.getDimensionPixelSize(R.styleable.DeadZone_maxSize, 0);

        int index = a.getInt(R.styleable.DeadZone_orientation, -1);
        mVertical = (index == VERTICAL);
        mStartFromRight = false; // Assume deadzone is starting from the left side of the zone

        if (DEBUG)
            Slog.v(TAG, this + " size=[" + mSizeMin + "-" + mSizeMax + "] hold=" + mHold
                    + (mVertical ? " vertical" : " horizontal"));

        setFlashOnTouchCapture(context.getResources().getBoolean(R.bool.config_dead_zone_flash));
    }

    static float lerp(float a, float b, float f) {
        return (b - a) * f + a;
    }

    private float getSize(long now) {
        if (mSizeMax == 0)
            return 0;
        long dt = (now - mLastPokeTime);
        if (dt > mHold + mDecay)
            return mSizeMin;
        if (dt < mHold)
            return mSizeMax;
        return (int) lerp(mSizeMax, mSizeMin, (float) (dt - mHold) / mDecay);
    }

    public void setFlashOnTouchCapture(boolean dbg) {
        mShouldFlash = dbg;
        mFlashFrac = 0f;
        postInvalidate();
        mFlashFrac = dbg ? 1f : 0f;
    }

    // I made you a touch event...
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG) {
            Slog.v(TAG, this + " onTouch: " + MotionEvent.actionToString(event.getAction()));
        }

        final int action = event.getAction();
        if (action == MotionEvent.ACTION_OUTSIDE) {
            poke(event);
        } else if (action == MotionEvent.ACTION_DOWN) {
            if (DEBUG) {
                Slog.v(TAG, this + " ACTION_DOWN: " + event.getX() + "," + event.getY());
            }
            int size = (int) getSize(event.getEventTime());
            boolean isCaptured;
            if (mVertical && mStartFromRight) {
                // Landscape on the left side of the screen
                float pixelsFromRight = getWidth() - event.getX();
                isCaptured = 0 <= pixelsFromRight && pixelsFromRight < size;
            } else if (mVertical) {
                // Landscape
                isCaptured = event.getX() < size;
            } else {
                // Portrait
                isCaptured = event.getY() < size;
            }
            if (isCaptured) {
                if (CHATTY) {
                    Slog.v(TAG, "consuming errant click: (" + event.getX() + "," + event.getY() + ")");
                }
                if (mShouldFlash) {
                    post(mDebugFlash);
                    postInvalidate();
                }
                return true; // ...but I eated it
            }
        }
        return false;
    }

    public void poke(MotionEvent event) {
        mLastPokeTime = event.getEventTime();
        if (DEBUG)
            Slog.v(TAG, "poked! size=" + getSize(mLastPokeTime));
        if (mShouldFlash) postInvalidate();
    }

    public void setFlash(float f) {
        mFlashFrac = f;
        postInvalidate();
    }

    public float getFlash() {
        return mFlashFrac;
    }

    public void setStartFromRight(boolean startFromRight) {
        mStartFromRight = startFromRight;
        if (mShouldFlash) postInvalidate();
    }

    @Override
    public void onDraw(Canvas can) {
        if (!mShouldFlash || mFlashFrac <= 0f) {
            return;
        }

        final int size = (int) getSize(SystemClock.uptimeMillis());
        if (mVertical && mStartFromRight) {
            // Landscape on the left side of the screen
            can.clipRect(can.getWidth() - size, 0, can.getWidth(), can.getHeight());
        } else if (mVertical) {
            // Landscape
            can.clipRect(0, 0, size, can.getHeight());
        } else {
            // Portrait
            can.clipRect(0, 0, can.getWidth(), size);
        }

        final float frac = DEBUG ? (mFlashFrac - 0.5f) + 0.5f : mFlashFrac;
        can.drawARGB((int) (frac * 0xFF), 0xDD, 0xEE, 0xAA);

        if (DEBUG && size > mSizeMin)
            // crazy aggressive redrawing here, for debugging only
            postInvalidateDelayed(100);
    }
}
