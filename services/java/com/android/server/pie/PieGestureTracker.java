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

import static com.android.internal.util.pie.PieServiceConstants.SENSITIVITY_HIGHEST;
import static com.android.internal.util.pie.PieServiceConstants.SENSITIVITY_LOWEST;

import android.graphics.Point;
import android.os.SystemClock;
import android.util.Slog;
import android.view.Display;
import android.view.MotionEvent;

import com.android.internal.util.pie.PiePosition;

/**
 * A simple {@link MotionEvent} tracker class. The main aim of this tracker is to
 * reject gestures as fast as possible, so there is only a small amount of events
 * that will be delayed.
 */
public class PieGestureTracker {
    public final static String TAG = "PieTracker";
    public final static boolean DEBUG = false;

    public final static long TRIGGER_TIME_MS = 140;
    public final static int PIXEL_SWIPE_OFFTAKE_SLOP = 2;

    private final int mBaseThickness;
    private final int mBaseTriggerDistance;
    private final int mBasePerpendicularDistance;

    private int mThickness;
    private int mTriggerDistance;
    private int mPerpendicularDistance;
    private int mGracePeriodDistance;
    private long mTimeOut;

    private int mDisplayWidth;
    private int mDisplayHeight;

    private boolean mActive;
    private PiePosition mPosition;
    private long mDownTime;
    private int mInitialX;
    private int mInitialY;
    private int mOffTake;
    private int mGracePeriod;

    public interface OnActivationListener {
        public void onActivation(MotionEvent event, int touchX, int touchY, PiePosition position);
    }
    private OnActivationListener mActivationListener;

    public PieGestureTracker(int thickness, int distance, int perpendicular) {
        if (DEBUG) {
            Slog.d(TAG, "init: " + thickness + "," + distance);
        }
        mBaseThickness = thickness;
        mBaseTriggerDistance = distance;
        mBasePerpendicularDistance = perpendicular;
        setSensitivity(0);
    }

    private void setSensitivity(int sensitivity) {
        float factor = 0.0f;
        if (sensitivity >= 1) {
             factor = (sensitivity - 1) / 4.0f;
        }
        if (DEBUG) {
            Slog.d(TAG, "sensitivity: " + sensitivity + " => factor:" + factor);
        }
        // default values (without overlay):
        // 140ms ... 210ms
        mTimeOut = (long) (TRIGGER_TIME_MS * (factor + 1.0f));
        // 12dp ... 18dp
        mThickness = (int) (mBaseThickness * (factor + 1.0f));
        // 12dp ... 6dp
        mTriggerDistance = (int) (mBaseTriggerDistance * (1.0f - factor));
        mPerpendicularDistance = (int) (mBasePerpendicularDistance * (1.0f - factor));
        mGracePeriodDistance = (int) (mThickness / 3.0f);
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

    public boolean start(MotionEvent motionEvent, int positions, int sensitivity) {
        final int x = (int) motionEvent.getX();
        final float fx = motionEvent.getX() / mDisplayWidth;
        final int y = (int) motionEvent.getY();
        final float fy = motionEvent.getY() / mDisplayHeight;

        // calculate trigger geometry based on sensitivity
        setSensitivity(sensitivity);

        if ((positions & PiePosition.LEFT.FLAG) != 0) {
            if (x < mThickness && fy > 0.1f && fy < 0.9f) {
                startWithPosition(motionEvent, PiePosition.LEFT);
                return true;
            }
        }
        if ((positions & PiePosition.BOTTOM.FLAG) != 0) {
            if (y > mDisplayHeight - mThickness && fx > 0.1f && fx < 0.9f) {
                startWithPosition(motionEvent, PiePosition.BOTTOM);
                return true;
            }
        }
        if ((positions & PiePosition.RIGHT.FLAG) != 0) {
            if (x > mDisplayWidth - mThickness && fy > 0.1f && fy < 0.9f) {
                startWithPosition(motionEvent, PiePosition.RIGHT);
                return true;
            }
        }
        if ((positions & PiePosition.TOP.FLAG) != 0) {
            if (y < mThickness && fx > 0.1f && fx < 0.9f) {
                startWithPosition(motionEvent, PiePosition.TOP);
                return true;
            }
        }
        return false;
    }

    private void startWithPosition(MotionEvent motionEvent, PiePosition position) {
        if (DEBUG) {
            Slog.d(TAG, "start tracking from " + position.name());
        }

        mDownTime = motionEvent.getDownTime();
        this.mPosition = position;
        mInitialX = (int) motionEvent.getX();
        mInitialY = (int) motionEvent.getY();
        switch (position) {
            case LEFT:
                mGracePeriod = mGracePeriodDistance;
                mOffTake = mInitialX - PIXEL_SWIPE_OFFTAKE_SLOP;
                break;
            case BOTTOM:
                mOffTake = mInitialY + PIXEL_SWIPE_OFFTAKE_SLOP;
                break;
            case RIGHT:
                mGracePeriod = mDisplayWidth - mGracePeriodDistance;
                mOffTake = mInitialX + PIXEL_SWIPE_OFFTAKE_SLOP;
                break;
            case TOP:
                mOffTake = mInitialY - PIXEL_SWIPE_OFFTAKE_SLOP;
                break;
        }
        mActive = true;
    }

    public boolean move(MotionEvent motionEvent) {
        if (!mActive || motionEvent.getEventTime() - mDownTime > mTimeOut) {
            Slog.d(TAG, "pie gesture timeout: " + (motionEvent.getEventTime() - mDownTime));
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
                        && x >= mOffTake) {
                    if (deltaX < mTriggerDistance) {
                        mOffTake = x - PIXEL_SWIPE_OFFTAKE_SLOP;
                        return true;
                    }
                    loaded = true;
                }
                break;
            case BOTTOM:
                if (deltaX < mPerpendicularDistance && deltaX > -mPerpendicularDistance
                        && y <= mOffTake) {
                    if (deltaY > -mTriggerDistance) {
                        mOffTake = y + PIXEL_SWIPE_OFFTAKE_SLOP;
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
                        && x <= mOffTake) {
                    if (deltaX > -mTriggerDistance) {
                        mOffTake = x + PIXEL_SWIPE_OFFTAKE_SLOP;
                        return true;
                    }
                    loaded = true;
                }
                break;
            case TOP:
                if (deltaX < mPerpendicularDistance && deltaX > -mPerpendicularDistance
                        && y >= mOffTake) {
                    if (deltaY < mTriggerDistance) {
                        mOffTake = y - PIXEL_SWIPE_OFFTAKE_SLOP;
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
                        + (SystemClock.uptimeMillis() - mDownTime) + "ms");
            }
            mActivationListener.onActivation(motionEvent, x, y, mPosition);
        }
        return loaded;
    }
}