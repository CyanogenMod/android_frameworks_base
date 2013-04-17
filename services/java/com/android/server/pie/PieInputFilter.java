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

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IInputFilter;
import android.view.IInputFilterHost;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.WindowManagerPolicy;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.internal.R;
import com.android.internal.util.pie.PiePosition;
import com.android.server.pie.PieGestureTracker.OnActivationListener;

import java.io.PrintWriter;

/**
 * A simple input filter, that listens for pie activation gestures in the motion event input
 * stream.
 * <p>
 * There are 5 distinct states of this filter.
 * 1) LISTEN:
 *    mTracker.active == false
 *    All motion events are passed through. If a ACTION_DOWN within a pie trigger area happen
 *    switch to DETECTING.
 * 2) DETECTING:
 *    mTracker.active == true
 *    All events are buffered now, and the gesture is checked by mTracker. If mTracker rejects
 *    the gesture (hopefully as fast as possible) all cached events will be flushed out and the
 *    filter falls back to LISTEN.
 *    If mTracker accepts the gesture, clear all cached events and go to LOCKED.
 * 3) LOCKED:
 *    mTracker.active == false
 *    All events will be cached until the state changes to SYNTHESIZE through a filter
 *    unlock event. If there is a ACTION_UP, _CANCEL or any PointerId differently to the last
 *    event seen when mTracker accepted the gesture, we flush all events and go to LISTEN.
 * 4) SYNTHESIZE:
 *    The first motion event found will be turned into a ACTION_DOWN event, all previous events
 *    will be discarded.
 * 5) POSTSYNTHESIZE:
 *    mSyntheticDownTime != -1
 *    All following events will have the down time set to the synthesized ACTION_DOWN event time
 *    until an ACTION_UP is encountered and the state is reset to LISTEN.
 * <p>
 * If you are reading this within Java Doc, you are doing something wrong ;)
 */
public class PieInputFilter implements IInputFilter {
    /* WARNING!! The IInputFilter interface is used directly, there is no Binder between this and
     * the InputDispatcher.
     * This is fine, because it prevents unnecessary parceling, but beware:
     * This means we are running on the dispatch or listener thread of the input dispatcher. Every
     * cycle we waste here adds to the overall input latency.
     */
    private static final String TAG = "PieInputFilter";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_INPUT = false;
    // TODO: Should be turned off in final commit
    private static final boolean SYSTRACE = true;

    private final Handler mHandler;

    private IInputFilterHost mHost = null; // dispatcher thread

    private static final class MotionEventInfo {
        private static final int MAX_POOL_SIZE = 16;

        private static final Object sLock = new Object();
        private static MotionEventInfo sPool;
        private static int sPoolSize;

        private boolean mInPool;

        public static MotionEventInfo obtain(MotionEvent event, int policyFlags) {
            synchronized (sLock) {
                MotionEventInfo info;
                if (sPoolSize > 0) {
                    sPoolSize--;
                    info = sPool;
                    sPool = info.next;
                    info.next = null;
                    info.mInPool = false;
                } else {
                    info = new MotionEventInfo();
                }
                info.initialize(event, policyFlags);
                return info;
            }
        }

        private void initialize(MotionEvent event, int policyFlags) {
            this.event = MotionEvent.obtain(event);
            this.policyFlags = policyFlags;
            cachedTimeMillis = SystemClock.uptimeMillis();
        }

        public void recycle() {
            synchronized (sLock) {
                if (mInPool) {
                    throw new IllegalStateException("Already recycled.");
                }
                clear();
                if (sPoolSize < MAX_POOL_SIZE) {
                    sPoolSize++;
                    next = sPool;
                    sPool = this;
                    mInPool = true;
                }
            }
        }

        private void clear() {
            event.recycle();
            event = null;
            policyFlags = 0;
        }

        public MotionEventInfo next;
        public MotionEvent event;
        public int policyFlags;
        public long cachedTimeMillis;
    }
    private final Object mLock = new Object();
    private MotionEventInfo mMotionEventQueue; // guarded by mLock
    private MotionEventInfo mMotionEventQueueTail; // guarded by mLock
    /* DEBUG */
    private int mMotionEventQueueCountDebug; // guarded by mLock

    private int mDeviceId; // dispatcher only
    private enum State {
        LISTEN, DETECTING, LOCKED, SYNTHESIZE, POSTSYNTHESIZE;
    }
    private State mState = State.LISTEN; // guarded by mLock
    private PieGestureTracker mTracker; // guarded by mLock
    private volatile int mPositions; // written by handler / read by dispatcher

    // only used by dispatcher
    private long mSyntheticDownTime = -1;
    private PointerCoords[] mTempPointerCoords = new PointerCoords[1];
    private PointerProperties[] mTempPointerProperties = new PointerProperties[1];

    public PieInputFilter(Context context, Handler handler) {
        mHandler = handler;

        final Resources res = context.getResources();
        mTracker = new PieGestureTracker(res.getDimensionPixelSize(R.dimen.pie_trigger_thickness),
                res.getDimensionPixelSize(R.dimen.pie_trigger_distance),
                res.getDimensionPixelSize(R.dimen.pie_perpendicular_distance));
        mTracker.setOnActivationListener(new OnActivationListener() {
            public void onActivation(MotionEvent event, int touchX, int touchY, PiePosition position) {
                mHandler.obtainMessage(PieService.MSG_PIE_ACTIVATION,
                        touchX, touchY, position).sendToTarget();
                mState = State.LOCKED;
            }
        });
        mTempPointerCoords[0] = new PointerCoords();
        mTempPointerProperties[0] = new PointerProperties();
    }

    // called from handler thread (lock taken)
    public void updateDisplay(Display display, DisplayInfo displayInfo) {
        synchronized (mLock) {
            mTracker.updateDisplay(display);
        }
    }

    // called from handler thread (lock taken)
    public void updatePositions(int positions) {
        mPositions = positions;
    }

    // called from handler thread
    public boolean unlockFilter() {
        synchronized (mLock) {
            if (mState == State.LOCKED) {
                mState = State.SYNTHESIZE;
                return true;
            }
        }
        return false;
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
        if (SYSTRACE) {
            Trace.traceBegin(Trace.TRACE_TAG_INPUT, "filterInputEvent");
        }
        try {
            if (event.getSource() != InputDevice.SOURCE_TOUCHSCREEN
                    || !(event instanceof MotionEvent)) {
                sendInputEvent(event, policyFlags);
                return;
            }
            if (DEBUG_INPUT) {
                Slog.d(TAG, "Received event: " + event + ", policyFlags=0x"
                        + Integer.toHexString(policyFlags));
            }
            MotionEvent motionEvent = (MotionEvent) event;
            final int deviceId = event.getDeviceId();
            if (deviceId != mDeviceId) {
                processDeviceSwitch(deviceId, motionEvent, policyFlags);
            } else {
                if ((policyFlags & WindowManagerPolicy.FLAG_PASS_TO_USER) == 0) {
                    synchronized (mLock) {
                        clearAndResetStateLocked(false, true);
                    }
                }
                processMotionEvent(motionEvent, policyFlags);
            }
        } finally {
            event.recycle();
            if (SYSTRACE) {
                Trace.traceEnd(Trace.TRACE_TAG_INPUT);
            }
        }
    }

    private void processDeviceSwitch(int deviceId, MotionEvent motionEvent, int policyFlags) {
        if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDeviceId = deviceId;
            synchronized (mLock) {
                clearAndResetStateLocked(true, false);
                processMotionEvent(motionEvent, policyFlags);
            }
        } else {
            sendInputEvent(motionEvent, policyFlags);
        }
    }

    private void processMotionEvent(MotionEvent motionEvent, int policyFlags) {
        final int action = motionEvent.getActionMasked();

        synchronized (mLock) {
            switch (mState) {
                case LISTEN:
                    if (action ==  MotionEvent.ACTION_DOWN) {
                        boolean hit = mPositions != 0 && mTracker.start(motionEvent, mPositions);
                        if (DEBUG) Slog.d(TAG, "start:" + hit);
                        if (hit) {
                            // cache the down event
                            cacheDelayedMotionEventLocked(motionEvent, policyFlags);
                            mState = State.DETECTING;
                            return;
                        }
                    }
                    sendInputEvent(motionEvent, policyFlags);
                    break;
                case DETECTING:
                    cacheDelayedMotionEventLocked(motionEvent, policyFlags);
                    if (action == MotionEvent.ACTION_MOVE) {
                        if (mTracker.move(motionEvent)) {
                            // return: the tracker is either detecting or triggered onActivation
                            return;
                        }
                    }
                    if (DEBUG) {
                        Slog.d(TAG, "move: reset!");
                    }
                    clearAndResetStateLocked(false, true);
                    break;
                case LOCKED:
                    cacheDelayedMotionEventLocked(motionEvent, policyFlags);
                    if (action != MotionEvent.ACTION_MOVE) {
                        clearAndResetStateLocked(false, true);
                    }
                    break;
                case SYNTHESIZE:
                    if (action == MotionEvent.ACTION_MOVE) {
                        clearDelayedMotionEventsLocked();
                        sendSynthesizedMotionEvent(motionEvent, policyFlags);
                        mState = State.POSTSYNTHESIZE;
                    } else {
                        // This is the case where a race condition caught us: We already
                        // returned the handler thread that it is all right to show up the pie
                        // in #gainTouchFocus(), but apparently this was wrong, as the gesture
                        // was canceled now.
                        clearAndResetStateLocked(false, true);
                    }
                    break;
                case POSTSYNTHESIZE:
                    motionEvent.setDownTime(mSyntheticDownTime);
                    if (action == MotionEvent.ACTION_UP) {
                        mState = State.LISTEN;
                        mSyntheticDownTime = -1;
                    }
                    sendInputEvent(motionEvent, policyFlags);
                    break;
            }
        }
    }

    private void clearAndResetStateLocked(boolean force, boolean shift) {
        // ignore soft reset in POSTSYNTHESIZE, because we need to tamper with
        // the event stream and going to LISTEN after an ACTION_UP anyway
        if (!force && (mState == State.POSTSYNTHESIZE)) {
            return;
        }
        switch (mState) {
            case LISTEN:
                // this is a nop
                break;
            case DETECTING:
                mTracker.reset();
                // intentionally no break here
            case LOCKED:
            case SYNTHESIZE:
                sendDelayedMotionEventsLocked(shift);
                break;
            case POSTSYNTHESIZE:
                // hard reset (this will break the event stream)
                Slog.w(TAG, "Quit POSTSYNTHESIZE without ACTION_UP from ACTION_DOWN at "
                        + mSyntheticDownTime);
                mSyntheticDownTime = -1;
                break;
        }
        // if there are future events that need to be tampered with, goto POSTSYNTHESIZE
        mState = mSyntheticDownTime == -1 ? State.LISTEN : State.POSTSYNTHESIZE;
    }

    private void sendInputEvent(InputEvent event, int policyFlags) {
        try {
            mHost.sendInputEvent(event, policyFlags);
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    private void cacheDelayedMotionEventLocked(MotionEvent event, int policyFlags) {
        MotionEventInfo info = MotionEventInfo.obtain(event, policyFlags);
        if (mMotionEventQueue == null) {
            mMotionEventQueue = info;
        } else {
            mMotionEventQueueTail.next = info;
        }
        mMotionEventQueueTail = info;
        mMotionEventQueueCountDebug++;
        if (SYSTRACE) {
            Trace.traceCounter(Trace.TRACE_TAG_INPUT, "meq", mMotionEventQueueCountDebug);
        }
    }

    private void sendDelayedMotionEventsLocked(boolean shift) {
        while (mMotionEventQueue != null) {
            MotionEventInfo info = mMotionEventQueue;
            mMotionEventQueue = info.next;

            if (DEBUG) {
                Slog.d(TAG, "Replay event: " + info.event);
            }
            mMotionEventQueueCountDebug--;
            if (SYSTRACE) {
                Trace.traceCounter(Trace.TRACE_TAG_INPUT, "meq", mMotionEventQueueCountDebug);
            }
            if (shift) {
                final long offset = SystemClock.uptimeMillis() - info.cachedTimeMillis;
                if (info.event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mSyntheticDownTime = info.event.getDownTime() + offset;
                }
                sendMotionEventWithOffset(info.event, info.policyFlags, mSyntheticDownTime, offset);
                if (info.event.getActionMasked() == MotionEvent.ACTION_UP) {
                    mSyntheticDownTime = -1;
                }
            } else {
                sendInputEvent(info.event, info.policyFlags);
            }
            info.recycle();
        }
        mMotionEventQueueTail = null;
    }

    private void clearDelayedMotionEventsLocked() {
        while (mMotionEventQueue != null) {
            MotionEventInfo next = mMotionEventQueue.next;
            mMotionEventQueue.recycle();
            mMotionEventQueue = next;
        }
        mMotionEventQueueTail = null;
        mMotionEventQueueCountDebug = 0;
        if (SYSTRACE) {
            Trace.traceCounter(Trace.TRACE_TAG_INPUT, "meq", mMotionEventQueueCountDebug);
        }
    }

    private void sendMotionEventWithOffset(MotionEvent event, int policyFlags,
            long downTime, long offset) {
        final int pointerCount = event.getPointerCount();
        PointerCoords[] coords = getTempPointerCoordsWithMinSize(pointerCount);
        PointerProperties[] properties = getTempPointerPropertiesWithMinSize(pointerCount);
        for (int i = 0; i < pointerCount; i++) {
            event.getPointerCoords(i, coords[i]);
            event.getPointerProperties(i, properties[i]);
        }
        final long eventTime = event.getEventTime() + offset;
        sendInputEvent(MotionEvent.obtain(downTime, eventTime, event.getAction(), pointerCount,
                properties, coords, event.getMetaState(), event.getButtonState(), 1.0f, 1.0f,
                event.getDeviceId(), event.getEdgeFlags(), event.getSource(), event.getFlags()),
                policyFlags);
    }

    private PointerCoords[] getTempPointerCoordsWithMinSize(int size) {
        final int oldSize = mTempPointerCoords.length;
        if (oldSize < size) {
            PointerCoords[] oldTempPointerCoords = mTempPointerCoords;
            mTempPointerCoords = new PointerCoords[size];
            System.arraycopy(oldTempPointerCoords, 0, mTempPointerCoords, 0, oldSize);
        }
        for (int i = oldSize; i < size; i++) {
            mTempPointerCoords[i] = new PointerCoords();
        }
        return mTempPointerCoords;
    }

    private PointerProperties[] getTempPointerPropertiesWithMinSize(int size) {
        final int oldSize = mTempPointerProperties.length;
        if (oldSize < size) {
            PointerProperties[] oldTempPointerProperties = mTempPointerProperties;
            mTempPointerProperties = new PointerProperties[size];
            System.arraycopy(oldTempPointerProperties, 0, mTempPointerProperties, 0, oldSize);
        }
        for (int i = oldSize; i < size; i++) {
            mTempPointerProperties[i] = new PointerProperties();
        }
        return mTempPointerProperties;
    }

    private void sendSynthesizedMotionEvent(MotionEvent event, int policyFlags) {
        if (event.getPointerCount() == 1) {
            event.getPointerCoords(0, mTempPointerCoords[0]);
            event.getPointerProperties(0, mTempPointerProperties[0]);
            MotionEvent down = MotionEvent.obtain(event.getEventTime(), event.getEventTime(),
                    MotionEvent.ACTION_DOWN, 1, mTempPointerProperties, mTempPointerCoords,
                    event.getMetaState(), event.getButtonState(),
                    1.0f, 1.0f, event.getDeviceId(), event.getEdgeFlags(),
                    event.getSource(), event.getFlags());
            Slog.d(TAG, "Synthesized event:" + down);
            sendInputEvent(down, policyFlags);
            mSyntheticDownTime = event.getEventTime();
        } else {
            Slog.w(TAG, "Could not synthesize MotionEvent, this will drop all following events!");
        }
    }

    // should never be called
    public IBinder asBinder() {
        throw new UnsupportedOperationException();
    }

    // called by the input dispatcher thread
    public void install(IInputFilterHost host) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Pie input filter installed.");
        }
        mHost = host;
        synchronized (mLock) {
            clearAndResetStateLocked(true, false);
        }
    }

    // called by the input dispatcher thread
    public void uninstall() throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Pie input filter uninstalled.");
        }
    }

    // called by a Binder thread
    public void dump(PrintWriter pw, String prefix) {
        synchronized (mLock) {
            pw.print(prefix);
            pw.println("mState=" + mState.name());
            pw.print(prefix);
            pw.println("mPositions=0x" + Integer.toHexString(mPositions));
            pw.print(prefix);
            pw.println("mQueue=" + mMotionEventQueueCountDebug + " items");
        }
    }
}
