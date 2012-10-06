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
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemService;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.hardware.input.InputManager;


public class AtCkpdCmdHandler extends AtCmdBaseHandler {

    private static final String LOG_TAG = "AtCkpdCmdHandler";

    // Default times (in milliseconds)
    private static final int DEFAULT_PRESS_TIME = 200;
    private static final int DEFAULT_PAUSE_TIME = 400;

    private android.view.IWindowManager mWm;
    private LinkedList<ParsedCkpdCmd> mEventQ;
    private Thread mInjectThread;
    // For normal keys as specified in TS 27.007
    private static HashMap<Character, Integer> key2keycode;
    // For alpha keys
    private static HashMap<Character, Integer> alphacode;
    KeyCharacterMap mKcm;

    private class ParsedCkpdCmd {
        private AtCmd mOriginalCommand;
        private long mPressTime;
        private long mPauseTime;
        private Vector<Object> mEvents;

        public ParsedCkpdCmd(AtCmd cmd) throws AtCmdParseException {
            mOriginalCommand = cmd;
            mPressTime = DEFAULT_PRESS_TIME;
            mPauseTime = DEFAULT_PAUSE_TIME;
            mEvents = new Vector<Object>();
            parse_cmd();
        }

        public Vector<Object> getEvents() {
            return mEvents;
        }

        private void parse_cmd() throws AtCmdParseException {
            String tokens[] = mOriginalCommand.getTokens();

            // Must have at least one token, and at most 3
            // AT+CKPD=<keys>[,<time>[,<pause]]
            if (tokens == null || tokens.length == 0 || tokens.length > 3)
                throw new AtCmdParseException("Must provide 1 to three tokens");

            char []keys = tokens[0].toUpperCase().toCharArray();
            char []orig = tokens[0].toCharArray();

            // Times must be numeric
            if (tokens.length >= 2) {
                try {
                    // TS 27.007 Specify times as 0.1s... multiply by 100 to get ms
                    mPressTime = Integer.parseInt(tokens[1]) * 100;
                } catch (NumberFormatException e) {
                    throw new AtCmdParseException("Wrong arg2: " + tokens[1]);
                }
            }

            if (tokens.length == 3) {
                try {
                    mPauseTime = Integer.parseInt(tokens[2]) * 100;
                } catch (NumberFormatException e) {
                    throw new AtCmdParseException("Wrong arg3: " + tokens[2]);
                }
            }

            boolean instring = false;
            StringBuilder theString = new StringBuilder();
            for (int i=0; i < keys.length; i++) {
                if (instring) {
                    if (keys[i] == ';') {
                        instring = false;
                        KeyEvent []events = mKcm.getEvents(theString.toString().toCharArray());
                        theString.setLength(0);
                        if (events != null) {
                            for (KeyEvent keyEvent : events) {
                                mEvents.add(keyEvent);
                            };
                        } else {
                            throw new AtCmdParseException("Unable to find all keycodes for string '" + theString + "'");
                        }
                        continue;
                    }
                    theString.append(orig[i]);
                }
                if (keys[i] == ';') {
                    instring = true;
                    continue;
                }
                if (keys[i] == '"')
                    continue;
                if (keys[i] == 'W') {
                    mEvents.add(new AtCmdHandler.PauseEvent(mPauseTime));
                    continue;
                }

                if (!key2keycode.containsKey(keys[i]))
                    throw new AtCmdParseException("Invalid Character " + orig[i]);
                // Actual times will be patched in at injection time
                mEvents.add(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key2keycode.get(keys[i]), 0));
                mEvents.add(new KeyEvent(0, 0, KeyEvent.ACTION_UP, key2keycode.get(keys[i]), 0));
                mEvents.add(new AtCmdHandler.PauseEvent(mPressTime));
            }
        }
    }

    public AtCkpdCmdHandler(Context c) throws AtCmdHandlerInstantiationException {
        super(c);
        IWindowManager service = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Service.WINDOW_SERVICE));

        if (service == null)
            throw new RuntimeException("Unable to connect to Window Service");

        mWm = service;
        mEventQ = new LinkedList<ParsedCkpdCmd>();
        key2keycode = new HashMap<Character, Integer>();
        key2keycode.put('#', KeyEvent.KEYCODE_POUND);
        key2keycode.put('*', KeyEvent.KEYCODE_STAR);
        key2keycode.put('0', KeyEvent.KEYCODE_0);
        key2keycode.put('1', KeyEvent.KEYCODE_1);
        key2keycode.put('2', KeyEvent.KEYCODE_2);
        key2keycode.put('3', KeyEvent.KEYCODE_3);
        key2keycode.put('4', KeyEvent.KEYCODE_4);
        key2keycode.put('5', KeyEvent.KEYCODE_5);
        key2keycode.put('6', KeyEvent.KEYCODE_6);
        key2keycode.put('7', KeyEvent.KEYCODE_7);
        key2keycode.put('8', KeyEvent.KEYCODE_8);
        key2keycode.put('9', KeyEvent.KEYCODE_9);
        key2keycode.put('<', KeyEvent.KEYCODE_DPAD_LEFT);
        key2keycode.put('>', KeyEvent.KEYCODE_DPAD_RIGHT);
        key2keycode.put('^', KeyEvent.KEYCODE_DPAD_UP);
        key2keycode.put('V', KeyEvent.KEYCODE_DPAD_DOWN);
        key2keycode.put('D', KeyEvent.KEYCODE_VOLUME_DOWN);
        key2keycode.put('E', KeyEvent.KEYCODE_ENDCALL);
        key2keycode.put('M', KeyEvent.KEYCODE_MENU);
        key2keycode.put('P', KeyEvent.KEYCODE_POWER);
        key2keycode.put('Q', KeyEvent.KEYCODE_MUTE);
        key2keycode.put('S', KeyEvent.KEYCODE_CALL);
        key2keycode.put('U', KeyEvent.KEYCODE_VOLUME_UP);
        key2keycode.put('V', KeyEvent.KEYCODE_DPAD_DOWN);
        key2keycode.put('Y', KeyEvent.KEYCODE_DEL);
        key2keycode.put('[', KeyEvent.KEYCODE_SOFT_LEFT);
        key2keycode.put(']', KeyEvent.KEYCODE_SOFT_RIGHT);

        alphacode = new HashMap<Character, Integer>();
        for (int i = 0; i < 'Z' - 'A' + 1 ; i++)
            alphacode.put((char)('A' + i), KeyEvent.KEYCODE_A + i);
        for (int i = 0 ; i < 10 ; i++)
            alphacode.put((char)('0' + i), KeyEvent.KEYCODE_0 + i);
        alphacode.put('@', KeyEvent.KEYCODE_AT);
        alphacode.put('=', KeyEvent.KEYCODE_EQUALS);
        alphacode.put('[', KeyEvent.KEYCODE_LEFT_BRACKET);
        alphacode.put('.', KeyEvent.KEYCODE_PERIOD);
        alphacode.put('+', KeyEvent.KEYCODE_PLUS);
        alphacode.put('#', KeyEvent.KEYCODE_POUND);
        alphacode.put(']', KeyEvent.KEYCODE_RIGHT_BRACKET);
        alphacode.put('/', KeyEvent.KEYCODE_SLASH);
        alphacode.put(' ', KeyEvent.KEYCODE_SPACE);
        alphacode.put('*', KeyEvent.KEYCODE_STAR);
        alphacode.put('\t', KeyEvent.KEYCODE_TAB);
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);
        mKcm = kcm;

        mInjectThread = new Thread() {
            public void run() {
                ParsedCkpdCmd cmd = null;
                while(true) {
                    Log.d(LOG_TAG, "De-queing command");
                    synchronized(mEventQ) {
                        while (mEventQ.isEmpty()) {
                            try {
                                mEventQ.wait();
                            } catch (InterruptedException e) {
                                Log.e(LOG_TAG, "Inject thread interrupted", e);
                                continue;
                            }
                        }
                        cmd = mEventQ.remove();
                    }
                    if (cmd == null) continue;
                    Log.d(LOG_TAG, "Command de-queued: " + cmd);

                    PowerManager pm =
                        (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE, "+CKPD Handler");
                    for (Object evt : cmd.getEvents()) {
                        if (evt instanceof AtCmdHandler.PauseEvent) {
                            try {
                                Thread.sleep(((AtCmdHandler.PauseEvent)evt).getTime());
                            } catch (InterruptedException e) {
                                Log.d(LOG_TAG, "Interrupted pause");
                            }
                            continue;
                        }
                        if (!(evt instanceof KeyEvent)) {
                            Log.e(LOG_TAG, "Unknown type of event " + evt.getClass().getName());
                            continue;
                        }

                        // Key Press
                        long dntime = SystemClock.uptimeMillis();
                        KeyEvent ev = (KeyEvent) evt;
                        ev = new KeyEvent(dntime,
                                dntime,
                                ev.getAction(),
                                ev.getKeyCode(), 0);

                        wl.acquire();
                        injectKeyEvent(ev , false);
                        wl.release();
                        // TODO: Add callbacks to provide support for +CKEV unsolicited codes
                    }
                }
            }
        };
        mInjectThread.start();
    }

    public AtCmdResponse handleCommand(AtCmd cmd) {
        AtCmdResponse ret = null;
        ParsedCkpdCmd valid = null;
        boolean dead = false;
        Log.d(LOG_TAG, "handleCommand: " + cmd);

        dead = !mInjectThread.isAlive();
        if (!dead) {
            // According to TS 27.007 8.7:
            // "This command should be accepted (OK) before
            // "actually starting to press the keys
            // Thus we validate first before queuing for execution
            try {
                valid = new ParsedCkpdCmd(cmd);
                ret = new AtCmdResponse(AtCmdResponse.RESULT_OK, null);
                Log.d(LOG_TAG, "Queuing command");
                synchronized (mEventQ) {
                    mEventQ.add(valid);
                    mEventQ.notify();
                }
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
        return "+CKPD";
    }

    /**
     * Injects a keystroke event into the UI.
     * Even when sync is false, this method may block while waiting for current
     * input events to be dispatched.
     *
     */

    private void injectKeyEvent(KeyEvent event, boolean sync) {
        Log.d(LOG_TAG, "InjectKeyEvent: " + event);
        InputManager.getInstance().injectInputEvent(event,
                sync ? InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
                        : InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
        return;
    }

}
