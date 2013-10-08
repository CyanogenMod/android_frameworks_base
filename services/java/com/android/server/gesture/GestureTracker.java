/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.server.gesture;

import android.util.Slog;
import android.view.MotionEvent;

/**
 * A simple {@link MotionEvent} tracker class. The main aim of this tracker is to
 * match simple gestures for injection back into the input stream.
 */
public class GestureTracker {
    private final static String TAG = "GestureTracker";
    private final static boolean DEBUG = false;

    public enum GestureType {
        NO_GESTURE, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT;
    }

    public interface OnMatchedListener {
        public void onMatched();
    }

    private OnMatchedListener mMatchedListener;
    private float mInitialX;
    private float mInitialY;
    private float mMinHorizSlop;
    private float mMinVertSlop;
    private float mTriggerDistance;
    private GestureType mGesture;

    public GestureTracker() {
        mMinHorizSlop = 15.0f;
        mMinVertSlop = 15.0f;
        mTriggerDistance = 35.0f;
    }

    public void setOnMatchedListener(OnMatchedListener listener) {
        mMatchedListener = listener;
    }

    public float getInitialX() {
        return mInitialX;
    }

    public float getInitialY() {
        return mInitialY;
    }

    public GestureType getGesture() {
        return mGesture;
    }

    public void start(MotionEvent motionEvent) {
        if (DEBUG) Slog.d(TAG, "Start tracking from " + motionEvent);

        mInitialX = motionEvent.getX();
        mInitialY = motionEvent.getY();
        mGesture = GestureType.NO_GESTURE;
    }

    public void move(MotionEvent motionEvent) {
        if (DEBUG) Slog.d(TAG, "Moving to " + motionEvent);

        final float x = motionEvent.getX();
        final float y = motionEvent.getY();
        final float deltaX = x - mInitialX;
        final float deltaY = y - mInitialY;

        if (DEBUG) Slog.d(TAG, "x: " + x + " y: " + y + " deltaX: " + deltaX + " deltaY: " + deltaY);

        if (deltaX > 0 && deltaY < 0) {
            // up
            if (Math.abs(deltaY) > mMinVertSlop &&
                    Math.abs(deltaX) < mMinHorizSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaY) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe up from up/right");
                    mGesture = GestureType.SWIPE_UP;
                }
            }
            // right
            if (Math.abs(deltaX) > mMinHorizSlop &&
                    Math.abs(deltaY) < mMinVertSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaX) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe right from up/right");
                    mGesture = GestureType.SWIPE_RIGHT;
                }
            }
        } else if (deltaX > 0 && deltaY > 0) {
            // down
            if (Math.abs(deltaY) > mMinVertSlop &&
                    Math.abs(deltaX) < mMinHorizSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaY) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe down from down/right");
                    mGesture = GestureType.SWIPE_DOWN;
                }
            }
            // right
            if (Math.abs(deltaX) > mMinHorizSlop &&
                    Math.abs(deltaY) < mMinVertSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaX) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe right from down/right");
                    mGesture = GestureType.SWIPE_RIGHT;
                }
            }
        } else if (deltaX < 0 && deltaY > 0) {
            // down
            if (Math.abs(deltaY) > mMinVertSlop &&
                    Math.abs(deltaX) < mMinHorizSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaY) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe down from down/left");
                    mGesture = GestureType.SWIPE_DOWN;
                }
            }
            // left
            if (Math.abs(deltaX) > mMinHorizSlop &&
                    Math.abs(deltaY) < mMinVertSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaX) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe left from down/left");
                    mGesture = GestureType.SWIPE_LEFT;
                }
            }
        } else if (deltaX < 0 && deltaY < 0) {
            // up
            if (Math.abs(deltaY) > mMinVertSlop &&
                    Math.abs(deltaX) < mMinHorizSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaY) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe up from up/left");
                    mGesture = GestureType.SWIPE_UP;
                }
            }
            // left
            if (Math.abs(deltaX) > mMinHorizSlop &&
                    Math.abs(deltaY) < mMinVertSlop) {
                if (mGesture == GestureType.NO_GESTURE &&
                        Math.abs(deltaX) > mTriggerDistance) {
                    if (DEBUG) Slog.d(TAG, "Swipe left from up/left");
                    mGesture = GestureType.SWIPE_LEFT;
                }
            }
        }

        if (mGesture != GestureType.NO_GESTURE) {
            // detected direction
            if (DEBUG) Slog.d(TAG, "Detected gesture type " + mGesture);
            mMatchedListener.onMatched();
        }
    }
}
