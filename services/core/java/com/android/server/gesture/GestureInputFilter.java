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

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Slog;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.IInputFilter;
import android.view.IInputFilterHost;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import java.io.PrintWriter;

/**
 * A simple input filter that listens for gesture sensor events and converts
 * them to input events to be injected into the input stream.
 */
public class GestureInputFilter implements IInputFilter, GestureDetector.OnGestureListener, OnDoubleTapListener {

    private static final String TAG = "GestureInputFilter";
    private static final boolean DEBUG = false;

    private IInputFilterHost mHost = null;

    private GestureDetector mGestureDetector;
    private InputManager mInputManager;
    private OrientationEventListener mOrientationListener;
    private final int mScreenWidth, mScreenHeight;
    private float mGesturePadWidth, mGesturePadHeight;
    private int mTouchSlop, mOrientation;
    private Context mContext;
    private PendingIntent mLongPressPendingIntent;
    private PendingIntent mDoubleClickPendingIntent;

    public GestureInputFilter(Context context) {
        mInputManager = InputManager.getInstance();
        mContext = context;
        for (int id : mInputManager.getInputDeviceIds()) {
            InputDevice inputDevice = mInputManager.getInputDevice(id);
            if ((inputDevice.getSources() & InputDevice.SOURCE_GESTURE_SENSOR)
                    == mInputManager.getInputDevice(id).getSources()) {
                mGesturePadWidth = inputDevice.getMotionRange(MotionEvent.AXIS_X).getMax();
                mGesturePadHeight = inputDevice.getMotionRange(MotionEvent.AXIS_Y).getMax();
                break;
            }
        }
        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        mScreenWidth = display.getWidth();
        mScreenHeight = display.getHeight();
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setOnDoubleTapListener(this);
        mOrientationListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == -1) {
                    return;
                }
                mOrientation = (orientation + 45) / 90 * 90;
            }
        };
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
    public void filterInputEvent(InputEvent event, int policyFlags)
            throws RemoteException {
        if (DEBUG) Slog.d(TAG, event.toString());

        try {
            if (event.getSource() != InputDevice.SOURCE_GESTURE_SENSOR
                    || !(event instanceof MotionEvent)) {
                try {
                    mHost.sendInputEvent(event, policyFlags);
                } catch (RemoteException e) {
                    /* ignore */
                }
                return;
            }

            MotionEvent motionEvent = (MotionEvent) event;
            mGestureDetector.onTouchEvent(motionEvent);
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
        mOrientationListener.enable();
    }

    // called by the input dispatcher thread
    public void uninstall() throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Gesture input filter uninstalled.");
        }
        mHost = null;
        mOrientationListener.disable();
        mContext = null;
    }

    // should never be called
    public IBinder asBinder() {
        throw new UnsupportedOperationException();
    }

    // called by a Binder thread
    public void dump(PrintWriter pw, String prefix) {

    }

    private boolean generateSwipe(MotionEvent e1, MotionEvent e2) {
        switch (mOrientation) {
        case 90:
            Slog.d(TAG, "Adjusting motion for 90 degrees");
            e1.setLocation(e1.getY(), e1.getX());
            e2.setLocation(e2.getY(), e2.getX());
            break;
        case 180:
            Slog.d(TAG, "Adjusting motion for 180 degrees");
            e1.setLocation(mGesturePadWidth - e1.getX(),
                    mGesturePadHeight - e1.getY());
            e2.setLocation(mGesturePadWidth - e2.getX(),
                    mGesturePadHeight - e2.getY());
            break;
        case 270:
            Slog.d(TAG, "Adjusting motion for 270 degrees");
            e1.setLocation(mGesturePadHeight - e1.getY(),
                    e1.getX());
            e2.setLocation(mGesturePadHeight - e2.getY(),
                    e2.getX());
            break;
        }

        float deltaX = Math.abs(e1.getX() - e2.getX());
        float deltaY = Math.abs(e1.getY() - e2.getY());

        if (deltaX < mTouchSlop && deltaY < mTouchSlop) {
            return false;
        }

        if (deltaX > deltaY) {
            e2.setLocation(e2.getX(), e1.getY());
        } else if (deltaY > deltaX) {
            e2.setLocation(e1.getX(), e2.getY());
        }

        float scaleX = mScreenWidth / mGesturePadWidth;
        float scaleY = mScreenHeight / mGesturePadHeight;

        float magnitudeX = deltaX * scaleX;
        float magnitudeY = deltaY * scaleY;

        float origX = mScreenWidth / 2;
        float origY = mScreenHeight / 2;
        float endX = 0.0f;
        float endY = 0.0f;

        if (e2.getY() > e1.getY()) {
            if (DEBUG) Slog.d(TAG, "Detected down motion");
            // Ensure selection does not occur
            endX = origX + mTouchSlop + 5;
            endY = origY + magnitudeY;
        } else if (e2.getY() < e1.getY()) {
            if (DEBUG) Slog.d(TAG, "Detected up motion");
            endX = origX + mTouchSlop + 5;
            endY = origY - magnitudeY;
        } else if (e2.getX() > e1.getX()) {
            if (DEBUG) Slog.d(TAG, "Detected left motion");
            endX = origX + magnitudeX;
            endY = origY + mTouchSlop + 5;
        } else if (e2.getX() < e1.getX()) {
            if (DEBUG) Slog.d(TAG, "Detected right motion");
            endX = origX - magnitudeX;
            endY = origY + mTouchSlop + 5;
        } else {
            return false;
        }

        sendSwipe(origX, origY, endX, endY);
        return true;
    }

    private void sendSwipe(float x1, float y1, float x2, float y2) {
        final long duration = 100;
        long now = SystemClock.uptimeMillis();
        final long startTime = now;
        final long endTime = startTime + duration;
        sendMotionEvent(MotionEvent.ACTION_DOWN, now, x1, y1, 1.0f);

        while (now < endTime) {
            long elapsedTime = now - startTime;
            float alpha = (float) elapsedTime / duration;
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

        MotionEvent e = MotionEvent.obtain(when, when, action, x, y, pressure,
                DEFAULT_SIZE, DEFAULT_META_STATE, DEFAULT_PRECISION_X,
                DEFAULT_PRECISION_Y, DEFAULT_DEVICE_ID, DEFAULT_EDGE_FLAGS);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendInputEvent(e);
    }

    private void sendInputEvent(InputEvent event) {
        mInputManager.injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }

    private static final float lerp(float a, float b, float alpha) {
        return (b - a) * alpha + a;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (mLongPressPendingIntent != null) {
            try {
                mLongPressPendingIntent.send();
            } catch (CanceledException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        return generateSwipe(e1, e2);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (mDoubleClickPendingIntent != null) {
            try {
                mDoubleClickPendingIntent.send();
                return true;
            } catch (CanceledException e1) {
                e1.printStackTrace();
            }
        }

        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    public void setOnLongPressPendingIntent(PendingIntent pendingIntent) {
        mLongPressPendingIntent = pendingIntent;
    }

    public void setOnDoubleClickPendingIntent(PendingIntent pendingIntent) {
        mDoubleClickPendingIntent = pendingIntent;
    }
}
