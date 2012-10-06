/* Copyright (c) 2010,2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.android.internal.atfwd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;

import javax.net.ssl.HandshakeCompletedListener;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemService;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.hardware.input.InputManager;
import android.view.InputDevice;

public class AtCtsaCmdHandler extends AtCmdBaseHandler {

    private static final String LOG_TAG = "AtCtsaCmdHandler";

    private static final int EVT_CTSA_CMD = 0;

    // Default times (in milliseconds)
    private static final int DEFAULT_PRESS_TIME = 200;
    private static final int DEFAULT_PAUSE_TIME = 400;

    private android.view.IWindowManager mWm;
    private LinkedList<ParsedCtsaCmd> mEventQ;
    private HandlerThread mInjectThread;
    private Handler mInjectHandler;

    private class ParsedCtsaCmd {
        private static final int CTSA_ACTION_RELEASE = 0;
        private static final int CTSA_ACTION_DEPRESS = 1;
        private static final int CTSA_ACTION_TAP = 2;
        private static final int CTSA_ACTION_DBL_TAP = 3;
        // Not in TS 27.007 (Android-specific)
        private static final int CTSA_ACTION_LNG_TAP = 4; // Long press

        private static final long PRESS_TIME = 200;
        private static final long LNG_PRESS_TIME = 1500;
        private AtCmd mOriginalCommand;

        private Vector<Object> mEvents;

        public ParsedCtsaCmd(AtCmd cmd) throws AtCmdParseException {
            mOriginalCommand = cmd;
            mEvents = new Vector<Object>();
            parse_cmd();
        }

        public AtCmd getOriginalCommand() {
            return mOriginalCommand;
        }

        private void parse_cmd() throws AtCmdParseException {
            int ctsaAction = -1;
            long time = SystemClock.uptimeMillis();
            String tokens[] = mOriginalCommand.getTokens();
            float x, y;
            long presstime = PRESS_TIME;
            MotionEvent evt;

            // Must have at least one token, and at most 3
            // AT+CTSA=<action>,<x>,<y>
            // action can be:
            //    0: release
            //    1: Depress
            //    2: Tap
            //    3: Double tap
            if (tokens == null || tokens.length != 3)
                throw new AtCmdParseException("Must provide three tokens");

            try {
                ctsaAction = Integer.parseInt(tokens[0]);

                x = Float.parseFloat(tokens[1]);
                y = Float.parseFloat(tokens[2]);

                switch (ctsaAction) {
                case CTSA_ACTION_RELEASE:
                    evt = MotionEvent.obtain(time, time,
                            MotionEvent.ACTION_UP, x, y, 0);
                    mEvents.add(evt);
                    break;
                case CTSA_ACTION_DEPRESS:
                    evt = MotionEvent.obtain(time, time,
                            MotionEvent.ACTION_DOWN, x, y, 0);
                    mEvents.add(evt);
                    break;
                case CTSA_ACTION_LNG_TAP:
                    presstime = LNG_PRESS_TIME;
                    // Fall-through
                case CTSA_ACTION_TAP:
                    addClick(x, y, presstime);
                    break;
                case CTSA_ACTION_DBL_TAP:
                    // First tap
                    addClick(x, y, presstime);
                    // Second tap
                    addClick(x, y, presstime);
                    break;
                }

            } catch (NumberFormatException e) {
                throw new AtCmdParseException(e);
            }
        }

        private void addClick(float x, float y, long presstime) {
            MotionEvent evt;

            evt = MotionEvent.obtain(0, 0,
                    MotionEvent.ACTION_DOWN, x, y, 0);
            mEvents.add(evt);

            mEvents.add(new PauseEvent(presstime));

            evt = MotionEvent.obtain(evt);
            evt.setAction(MotionEvent.ACTION_UP);
            mEvents.add(evt);
        }

        public Vector<Object>getEvents() {
            return mEvents;
        }
    }
    public AtCtsaCmdHandler(Context c) throws AtCmdHandlerInstantiationException {
        super(c);
        IWindowManager service = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Service.WINDOW_SERVICE));
        if (service == null)
            throw new RuntimeException("Unable to connect to Window Service");
        mWm = service;
        mEventQ = new LinkedList<ParsedCtsaCmd>();

        mInjectThread = new HandlerThread("CTSA Inject Thread",
                android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY) {
        };
        mInjectThread.start();
        mInjectHandler = new Handler(mInjectThread.getLooper()) {
            public void handleMessage(Message msg) {
                switch(msg.what) {
                case EVT_CTSA_CMD:
                    ParsedCtsaCmd cmd = (ParsedCtsaCmd) msg.obj;
                    Log.d(LOG_TAG, "Command de-queued: " + cmd);
                    if (cmd == null) return;

                    for (Object obj : cmd.getEvents()) {
                        if (obj instanceof PauseEvent) {
                            PauseEvent evt = (PauseEvent) obj;
                            try {
                                Thread.sleep(evt.getTime());
                                continue;
                            } catch (InterruptedException e) {
                                Log.d(LOG_TAG, "PauseEvent interrupted", e);
                            }
                        }

                        if (!(obj instanceof MotionEvent)) {
                            Log.d(LOG_TAG, "Ignoring unkown event of type " + obj.getClass().getName());
                            continue;
                        }

                        // Touch Event
                        long time = SystemClock.uptimeMillis();

                        MotionEvent oev = (MotionEvent) obj;
                        MotionEvent ev = MotionEvent.obtain(time,
                                time,
                                oev.getAction(),
                                oev.getX(), oev.getY(),
                                oev.getMetaState());
                        oev.recycle();
                        injectPointerEvent(ev , false);
                        // TODO: Add callbacks to provide support for unsolicited TS codes
                    }
                }
            }
        };
    }

    public AtCmdResponse handleCommand(AtCmd cmd) {
        AtCmdResponse ret = null;
        ParsedCtsaCmd valid = null;
        boolean dead = false;
        Log.d(LOG_TAG, "handleCommand: " + cmd);

        dead = !mInjectThread.isAlive();
        if (!dead) {
            // According to TS 27.007 8.7:
            // "This command should be accepted (OK returned) before
            // "actually emulating the touch screen action
            // Thus we validate first before queuing for execution
            try {
                valid = new ParsedCtsaCmd(cmd);
                ret = new AtCmdResponse(AtCmdResponse.RESULT_OK, null);
                Log.d(LOG_TAG, "Queuing command");
                Message.obtain(mInjectHandler, EVT_CTSA_CMD, valid).sendToTarget();
                Log.d(LOG_TAG, "Command queued");
            } catch (AtCmdParseException e) {
                Log.d(LOG_TAG, "Error parsing command " + cmd);
                ret = new AtCmdResponse(AtCmdResponse.RESULT_ERROR, "+CME ERROR: 25");
            }
        } else {
            ret = new AtCmdResponse(AtCmdResponse.RESULT_ERROR, "+CME ERROR: 1");
        }
        return ret;
    }

    public String getCommandName() {
        return "+CTSA";
    }

    /**
     * Inject a pointer (touch) event into the UI.
     * Even when sync is false, this method may block while waiting for current
     * input events to be dispatched.
     */
    private void injectPointerEvent(MotionEvent event, boolean sync) {
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        Log.i("Input", "InjectPointerEvent: " + event);
        InputManager.getInstance().injectInputEvent(event,
                sync ? InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
                        : InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
    }
}
