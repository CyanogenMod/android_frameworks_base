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
import com.android.server.pie.PieGestureTracker.OnActivationListener;
import com.android.server.pie.PieService.Position;

import java.io.PrintWriter;

/**
 * A simple input filter, that listens for pie activation gestures in the motion event input
 * stream.
 * <p>
 * There are 4 distinct states of this filter.
 * 1) LISTEN:
 *    mTracker.active == false && mLocked == false;
 *    All motion events are passed through. If a ACTION_DOWN within a pie trigger area happen
 *    switch to DETECTING.
 * 2) DETECTING:
 *    mTracker.active == true && mLocked == false;
 *    All events are buffered now, and the gesture is checked by mTracker. If mTracker rejects
 *    the gesture (hopefully as fast as possible) all cached events will be flushed out and the
 *    filter falls back to LISTEN.
 *    If mTracker accepts the gesture, clear all cached events and go to LOCKED.
 * 3) LOCKED:
 *    mTracker.active == false; mLocked == true;
 *    All events will be cached until the state changes to SYNTHESIZE through a filter
 *    unlock event. If there is a ACTION_UP, _CANCEL or any PointerId differently to the last
 *    event seen when mTracker accepted the gesture, we flush all events and go to LISTEN.
 * 4) SYNTHESIZE:
 *    The first motion event found will be turned into a ACTION_DOWN event, all previous events
 *    will be discarded.
 * <p>
 * If you are reading this within Java Doc, you are doing something wrong ;)
 */
public class PieInputFilter implements IInputFilter {
    /* WARNING!! The IInputFilter interface is used directly, there is no Binder between this and
     * the InputDispatcher.
     * This is fine, because it prevents unnecessary parceling, but beware:
     * This means we are running on the dispatch thread of the input dispatcher! When being called
     * the input dispatcher may hold it's internal lock, so be careful of holding own locks when
     * calling into the input dispatcher (e.g. through #sendMotionEvent())
     */
    private static final String TAG = "PieInputFilter";
    private static final boolean DEBUG = PieService.DEBUG;
    private static final boolean DEBUG_INPUT = false;

    private final Handler mHandler;

    private boolean mInstalled = false; // dispatcher thread
    private IInputFilterHost mHost = null; // dispatcher thread

    private static final class MotionEventInfo {
        private static final int MAX_POOL_SIZE = 16;

        private static final Object sLock = new Object();
        private static MotionEventInfo sPool;
        private static int sPoolSize;

        private boolean mInPool;
        private MotionEventInfo mTail;

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

        public void append(MotionEventInfo head) {
            if (head != null) {
                MotionEventInfo tail = head.mTail;
                head.mTail = this;
                tail.next = this;
            } else {
                mTail = this;
            }
            next = null;
        }

        public MotionEventInfo next;
        public MotionEvent event;
        public int policyFlags;
    }
    private final Object mLock = new Object();
    private MotionEventInfo mMotionEventQueue; // guarded by mLock

    private enum State {
        LISTEN, DETECTING, LOCKED, SYNTHESIZE;
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
                res.getDimensionPixelSize(R.dimen.pie_trigger_distance));
        mTracker.setOnActivationListener(new OnActivationListener() {
            public void onActivation(MotionEvent event, int touchX, int touchY, Position position) {
                mHandler.obtainMessage(PieService.MSG_PIE_ACTIVATION,
                        touchX, touchY, position).sendToTarget();
                mState = State.LOCKED;
            }
        });
        mTempPointerCoords[0] = new PointerCoords();
        mTempPointerProperties[0] = new PointerProperties();
    }

    // called from handler thread
    public void updateDisplay(Display display, DisplayInfo displayInfo) {
        synchronized (mLock) {
            mTracker.updateDisplay(display);
        }
    }

    // called from handler thread
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

    // called by the input dispatcher thread
    public void filterInputEvent(InputEvent event, int policyFlags) throws RemoteException {
        if (!mInstalled) {
            return;
        }
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0
                || (policyFlags & WindowManagerPolicy.FLAG_PASS_TO_USER) == 0) {
            mHost.sendInputEvent(event, policyFlags);
        }
        if (DEBUG_INPUT) {
            Slog.d(TAG, "Received event: " + event + ", policyFlags=0x"
                    + Integer.toHexString(policyFlags));
        }

        if (event instanceof MotionEvent) {
            MotionEvent motionEvent = (MotionEvent) event;
            processMotionEvent(motionEvent, policyFlags);
        }
    }

    private void processMotionEvent(MotionEvent motionEvent, int policyFlags) {
        final int action = motionEvent.getAction();

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
                    } else if (action == MotionEvent.ACTION_UP) {
                        mSyntheticDownTime = -1;
                    }
                    if (mSyntheticDownTime != -1) {
                        motionEvent.setDownTime(mSyntheticDownTime);
                    }
                    sendMotionEvent(motionEvent, policyFlags);
                    break;
                case DETECTING:
                    cacheDelayedMotionEventLocked(motionEvent, policyFlags);
                    if (action == MotionEvent.ACTION_MOVE) {
                        if (mTracker.move(motionEvent)) {
                            // return: the tracker is either detecting or triggered onActivation
                            return;
                        }
                    } else {
                        if (DEBUG) {
                            Slog.d(TAG, "move: reset within: " + mTracker.eventCountDebug);
                        }
                        mTracker.reset();
                    }
                    sendDelayedMotionEventsLocked();
                    mState = State.LISTEN;
                    break;
                case LOCKED:
                    cacheDelayedMotionEventLocked(motionEvent, policyFlags);
                    if (action != MotionEvent.ACTION_MOVE) {
                        sendDelayedMotionEventsLocked();
                        mState = State.LISTEN;
                    }
                    break;
                case SYNTHESIZE:
                    if (action == MotionEvent.ACTION_MOVE) {
                        clearDelayedMotionEventsLocked();
                        sendSynthesizedMotionEvent(motionEvent, policyFlags);
                        mState = State.LISTEN;
                    } else {
                        // This is the case where a race condition caught us: We already
                        // returned the handler thread that it is all right to show up the pie
                        // in #gainTouchFocus(), but apparently this was wrong, as the gesture
                        // was canceled now.
                        sendDelayedMotionEventsLocked();
                        mState = State.LISTEN;
                    }
                    break;
            }
        }
    }

    private void sendMotionEvent(MotionEvent event, int policyFlags) {
        try {
            mHost.sendInputEvent(event, policyFlags);
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    private void cacheDelayedMotionEventLocked(MotionEvent event, int policyFlags) {
        MotionEventInfo info = MotionEventInfo.obtain(event, policyFlags);
        info.append(mMotionEventQueue);
        if (mMotionEventQueue == null) {
            mMotionEventQueue = info;
        }
    }

    private void sendDelayedMotionEventsLocked() {
        sendDelayedMotionEvents(mMotionEventQueue);
        mMotionEventQueue = null;
    }

    private void sendDelayedMotionEvents(MotionEventInfo head) {
        while (head != null) {
            MotionEventInfo info = head;
            head = info.next;

            if (DEBUG) {
                Slog.d(TAG, "Replay event: " + info.event);
            }
            try {
                mHost.sendInputEvent(info.event, info.policyFlags);
            } catch (RemoteException e) {
                /* ignore */
            }
            info.recycle();
        }
    }

    private void clearDelayedMotionEventsLocked() {
        while (mMotionEventQueue != null) {
            MotionEventInfo next = mMotionEventQueue.next;
            mMotionEventQueue.recycle();
            mMotionEventQueue = next;
        }
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
            sendMotionEvent(down, policyFlags);
            mSyntheticDownTime = event.getEventTime();
        } else {
            Slog.w(TAG, "Could not synthesize MotionEvent, this will drop all following events!");
        }
    }

    // should never be called
    public IBinder asBinder() {
        return null;
    }

    // called by the input dispatcher thread
    public void install(IInputFilterHost host) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Pie input filter installed.");
        }
        mHost = host;
        mInstalled = true;
    }

    // called by the input dispatcher thread
    public void uninstall() throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Pie input filter uninstalled.");
        }
        mInstalled = false;
    }

    // called by a Binder thread
    public void dump(PrintWriter pw, String prefix) {
        synchronized (mLock) {
            int queueFill = 0;
            synchronized (mLock) {
                MotionEventInfo info = mMotionEventQueue;
                while (info != null) {
                    queueFill++;
                    info = info.next;
                }
            }

            pw.print(prefix);
            pw.println("mState=" + mState.name());
            pw.print(prefix);
            pw.println("mQueue=" + queueFill + " items");
        }
    }
}
