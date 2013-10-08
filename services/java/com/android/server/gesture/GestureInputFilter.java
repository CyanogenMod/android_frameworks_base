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

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Slog;
import android.view.IInputFilter;
import android.view.IInputFilterHost;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.server.gesture.GestureTracker.GestureType;
import com.android.server.gesture.GestureTracker.OnMatchedListener;

import java.io.PrintWriter;

/**
 * A simple input filter that listens for gesture sensor events and converts
 * them to input events to be injected into the input stream.
 */
public class GestureInputFilter implements IInputFilter {
    private static final String TAG = "GestureInputFilter";
    private static final boolean DEBUG = false;

    private enum State {
        LISTEN, DETECT, LOCKED;
    }
    private State mState = State.LISTEN;

    private IInputFilterHost mHost = null; // dispatcher thread

    private GestureTracker mGestureTracker;

    public GestureInputFilter(Context context) {
        mGestureTracker = new GestureTracker();
        mGestureTracker.setOnMatchedListener(new OnMatchedListener() {
            public void onMatched() {
                if (DEBUG) Slog.d(TAG, "Recognized gesture");
                // notify that gesture was recognized
                mState = State.LOCKED;
            }
        });
    }

    /**
     * Called to enqueue the input event for filtering.
     * The event must be recycled after the input filter processed it.
     * This method is guaranteed to be non-reentrant.
     *
     * @see InputFilter#filterInputEvent(InputEvent, int)
     * @param event The input event to enqueue.
     */
    // called by the input dispatcher thread
    public void filterInputEvent(InputEvent event, int policyFlags) throws RemoteException {
        if (DEBUG) Slog.d(TAG, "filterInputEvent");

        try {
            if (event.getSource() != InputDevice.SOURCE_GESTURE_SENSOR
                    || !(event instanceof MotionEvent)) {
                sendInputEvent(event, policyFlags);
                return;
            }

            MotionEvent motionEvent = (MotionEvent) event;
            processMotionEvent(motionEvent, policyFlags);
        } finally {
            event.recycle();
        }
    }

    // called by the input dispatcher thread
    public void install(IInputFilterHost host) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Gesture input filter installed.");
        }
        mHost = host;
    }

    // called by the input dispatcher thread
    public void uninstall() throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Gesture input filter uninstalled.");
        }
    }

    // should never be called
    public IBinder asBinder() {
        throw new UnsupportedOperationException();
    }

    // called by a Binder thread
    public void dump(PrintWriter pw, String prefix) {

    }

    private void processMotionEvent(MotionEvent event, int policyFlags) {
        final int action = event.getActionMasked();

        switch (mState) {
            case LISTEN:
                if (action == MotionEvent.ACTION_DOWN) {
                    if (DEBUG) Slog.d(TAG, "Beginning detection at " + event);
                    mGestureTracker.start(event);
                    mState = State.DETECT;
                }
                break;
            case DETECT:
                if (action == MotionEvent.ACTION_MOVE) {
                    if (DEBUG) Slog.d(TAG, "Moving detection to " + event);
                    mGestureTracker.move(event);
                } else if (action == MotionEvent.ACTION_UP) {
                    if (DEBUG) Slog.d(TAG, "Canceling detection on finger up");
                    mState = State.LISTEN;
                }
                break;
            case LOCKED:
                if (action == MotionEvent.ACTION_UP) {
                    if (DEBUG) Slog.d(TAG, "Got end of action, sending gesture");

                    // end of gesture, generate swipe from initial and end coords
                    float deltaX = Math.abs(event.getX() - mGestureTracker.getInitialX());
                    float deltaY = Math.abs(event.getY() - mGestureTracker.getInitialY());
                    // TODO: scale for screen sizing
                    generateSwipe(mGestureTracker.getGesture(),
                            (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY));

                    // reset
                    mState = State.LISTEN;
                }
                break;
        }
    }

    private void generateSwipe(GestureType direction, float magnitude) {
        if (DEBUG) Slog.d(TAG, "Gesture: " + direction + " with magnitude " + magnitude);
        // originate all generated swipes from the center of main touchscreen
        // TODO: calculate origin position
        float origX = 540.0f;
        float origY = 960.0f;
        float endX = 0.0f;
        float endY = 0.0f;
        switch (direction) {
            case SWIPE_UP:
                endX = origX;
                endY = origY + magnitude * 4;
                if (endY > 1920)
                    endY = 1920.0f;
                break;
            case SWIPE_DOWN:
                endX = origX;
                endY = origY - magnitude * 4;
                if (endY < 0)
                    endY = 0.0f;
                break;
            case SWIPE_LEFT:
                endX = origX - magnitude * 4;
                endY = origY;
                if (endX < 0)
                    endX = 0.0f;
                break;
            case SWIPE_RIGHT:
                endX = origX + magnitude * 4;
                endY = origY;
                if (endX > 1080)
                    endX = 1080.0f;
                break;
        }

        sendSwipe(origX, origY, endX, endY);
    }

    private void sendSwipe(float x1, float y1, float x2, float y2) {
        final long DEFAULT_DURATION = 250;
        long now = SystemClock.uptimeMillis();
        final long startTime = now;
        final long endTime = startTime + DEFAULT_DURATION;

        sendMotionEvent(MotionEvent.ACTION_DOWN, now, x1, y1, 1.0f);

        while (now < endTime) {
            long elapsedTime = now - startTime;
            float alpha = (float) elapsedTime / DEFAULT_DURATION;
            sendMotionEvent(MotionEvent.ACTION_MOVE, now,
                    lerp(x1, x2, alpha), lerp(y1, y2, alpha), 1.0f);
            now = SystemClock.uptimeMillis();
        }
        sendMotionEvent(MotionEvent.ACTION_UP, now, x2, y2, 1.0f);
    }

    private void sendMotionEvent(int action, long when, float x, float y,
            float pressure) {
        final float DEFAULT_SIZE = 1.0f;
        final int DEFAULT_META_STATE = 0;
        final float DEFAULT_PRECISION_X = 1.0f;
        final float DEFAULT_PRECISION_Y = 1.0f;
        final int DEFAULT_DEVICE_ID = 0;
        final int DEFAULT_EDGE_FLAGS = 0;
        final int DEFAULT_POLICY_FLAGS = 0;

        MotionEvent e = MotionEvent.obtain(when, when, action, x, y, pressure,
                DEFAULT_SIZE, DEFAULT_META_STATE, DEFAULT_PRECISION_X,
                DEFAULT_PRECISION_Y, DEFAULT_DEVICE_ID, DEFAULT_EDGE_FLAGS);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendInputEvent(e, DEFAULT_POLICY_FLAGS);
    }

    private void sendInputEvent(InputEvent event, int policyFlags) {
        try {
            mHost.sendInputEvent(event, policyFlags);
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    private static final float lerp(float a, float b, float alpha) {
        return (b - a) * alpha + a;
    }
}
