/*
 * Copyright (C) 2013 The CyanogenMod Project (Jens Doll)
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
package com.android.server.pie;

import android.graphics.Point;
import android.util.Slog;
import android.view.Display;
import android.view.MotionEvent;

import com.android.internal.util.pie.Position;

/**
 * A simple {@link MotionEvent} tracker class. The main aim of this tracker is to
 * reject gestures as fast as possible, so there is only a small amount of events
 * that will be delayed.
 */
public class PieGestureTracker {
    public final static String TAG = "PieTracker";
    public final static boolean DEBUG = false;

    public final static long TRIGGER_TIME_MS = 80;

    private final int mTriggerThickness;
    private final int mTriggerDistance;
    private final int mPerpendicularDistance;

    private int mDisplayWidth;
    private int mDisplayHeight;

    private boolean mActive;
    private Position mPosition;
    private long mDownTime;
    private int mInitialX;
    private int mInitialY;
    private int mLastX;
    private int mLastY;
    private int mGracePeriod;

    public interface OnActivationListener {
        public void onActivation(MotionEvent event, int touchX, int touchY, Position position);
    }
    private OnActivationListener mActivationListener;

    public PieGestureTracker(int thickness, int distance, int perpendicular) {
        if (DEBUG) {
            Slog.d(TAG, "init: " + thickness + "," + distance);
        }
        mTriggerThickness = thickness;
        mTriggerDistance = distance;
        mPerpendicularDistance = perpendicular;
    }

    public void setOnActivationListener(OnActivationListener listener) {
        mActivationListener = listener;
    }

    public void reset() {
        mActive = false;
    }

    public void updateDisplay(Display display) {
        Point outSize = new Point(0,0);
        display.getSize(outSize);
        mDisplayWidth = outSize.x;
        mDisplayHeight = outSize.y;
        if (DEBUG) {
            Slog.d(TAG, "new display: " + mDisplayWidth + "," + mDisplayHeight);
        }
    }

    public boolean start(MotionEvent motionEvent, int positions) {
        final int x = (int) motionEvent.getX();
        final float fx = motionEvent.getX() / mDisplayWidth;
        final int y = (int) motionEvent.getY();
        final float fy = motionEvent.getY() / mDisplayHeight;

        if ((positions & Position.LEFT.FLAG) != 0) {
            if (x < mTriggerThickness && fy > 0.1f && fy < 0.9f) {
                startWithPosition(motionEvent, Position.LEFT);
                return true;
            }
        }
        if ((positions & Position.BOTTOM.FLAG) != 0) {
            if (y > mDisplayHeight - mTriggerThickness && fx > 0.1f && fx < 0.9f) {
                startWithPosition(motionEvent, Position.BOTTOM);
                return true;
            }
        }
        if ((positions & Position.RIGHT.FLAG) != 0) {
            if (x > mDisplayWidth - mTriggerThickness && fy > 0.1f && fy < 0.9f) {
                startWithPosition(motionEvent, Position.RIGHT);
                return true;
            }
        }
        if ((positions & Position.TOP.FLAG) != 0) {
            if (y < mTriggerThickness && fx > 0.1f && fx < 0.9f) {
                startWithPosition(motionEvent, Position.TOP);
                return true;
            }
        }
        return false;
    }

    private void startWithPosition(MotionEvent motionEvent, Position position) {
        if (DEBUG) {
            Slog.d(TAG, "start tracking from " + position.name());
        }

        mDownTime = motionEvent.getDownTime();
        this.mPosition = position;
        mLastX = mInitialX = (int) motionEvent.getX();
        mLastY = mInitialY = (int) motionEvent.getY();
        if (position == Position.LEFT) {
            mGracePeriod = (int) (mTriggerDistance / 3.0f);
        } else if (position == Position.RIGHT) {
            mGracePeriod = mDisplayWidth - (int) (mTriggerDistance / 3.0f);
        }
        mActive = true;
    }

    public boolean move(MotionEvent motionEvent) {
        if (!mActive || motionEvent.getEventTime() - mDownTime > TRIGGER_TIME_MS) {
            mActive = false;
            return false;
        }

        final int x = (int) motionEvent.getX();
        final int y = (int) motionEvent.getY();
        final int deltaX = x - mInitialX;
        final int deltaY = y - mInitialY;

        if (DEBUG) {
            Slog.d(TAG, "move at " + x + "," + y + " " + deltaX + "," + deltaY);
        }

        boolean loaded = false;
        switch (mPosition) {
            case LEFT:
                if (x < mGracePeriod) {
                    mInitialY = y;
                }
                if (deltaY < mPerpendicularDistance && deltaY > -mPerpendicularDistance
                        && x >= mLastX) {
                    if (deltaX < mTriggerDistance) {
                        mLastX = x;
                        return true;
                    }
                    loaded = true;
                }
                break;
            case BOTTOM:
                if (deltaX < mPerpendicularDistance && deltaX > -mPerpendicularDistance
                        && y <= mLastY) {
                    if (deltaY > -mTriggerDistance) {
                        mLastY = y;
                        return true;
                    }
                    loaded = true;
                }
                break;
            case RIGHT:
                if (x > mGracePeriod) {
                    mInitialY = y;
                }
                if (deltaY < mPerpendicularDistance && deltaY > -mPerpendicularDistance
                        && x <= mLastX) {
                    if (deltaX > -mTriggerDistance) {
                        mLastX = x;
                        return true;
                    }
                    loaded = true;
                }
                break;
            case TOP:
                if (deltaX < mPerpendicularDistance && deltaX > -mPerpendicularDistance
                        && y >= mLastY) {
                    if (deltaY < mTriggerDistance) {
                        mLastY = y;
                        return true;
                    }
                    loaded = true;
                }
                break;
        }
        mActive = false;
        if (loaded && mActivationListener != null) {
            if (DEBUG) {
                Slog.d(TAG, "activate at " + x + "," + y + " " + mPosition + " within "
                        + (System.currentTimeMillis() - mDownTime) + "ms");
            }
            mActivationListener.onActivation(motionEvent, x, y, mPosition);
        }
        return loaded;
    }
}