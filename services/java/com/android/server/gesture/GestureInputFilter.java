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
import android.util.Slog;
import android.view.IInputFilter;
import android.view.IInputFilterHost;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.PrintWriter;

/**
 * A simple input filter that listens for gesture sensor events and converts
 * them to input events to be injected into the input stream.
 */
public class GestureInputFilter implements IInputFilter {
    private static final String TAG = "GestureInputFilter";
    private static final boolean DEBUG = false;

    private IInputFilterHost mHost = null; // dispatcher thread

    public GestureInputFilter(Context context) {

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

    private void sendInputEvent(InputEvent event, int policyFlags) {
        try {
            mHost.sendInputEvent(event, policyFlags);
        } catch (RemoteException e) {
            /* ignore */
        }
    }
}
