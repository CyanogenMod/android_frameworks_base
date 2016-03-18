/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.util.Slog;
import android.media.AudioManager;
import android.util.Log;
import android.view.InputDevice;

import com.android.internal.R;
import com.android.server.input.InputManagerService;
import com.android.server.input.InputManagerService.WiredAccessoryCallbacks;
import static com.android.server.input.InputManagerService.SW_HEADPHONE_INSERT;
import static com.android.server.input.InputManagerService.SW_MICROPHONE_INSERT;
import static com.android.server.input.InputManagerService.SW_LINEOUT_INSERT;
import static com.android.server.input.InputManagerService.SW_HEADPHONE_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_MICROPHONE_INSERT_BIT;
import static com.android.server.input.InputManagerService.SW_LINEOUT_INSERT_BIT;

import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <p>WiredAccessoryManager monitors for a wired headset on the main board or dock using
 * both the InputManagerService notifyWiredAccessoryChanged interface and the UEventObserver
 * subsystem.
 */
final class WiredAccessoryManager implements WiredAccessoryCallbacks {
    private static final String TAG = WiredAccessoryManager.class.getSimpleName();
    private static final boolean LOG = true;

    private static final int BIT_HEADSET = (1 << 0);
    private static final int BIT_HEADSET_NO_MIC = (1 << 1);
    private static final int BIT_USB_HEADSET_ANLG = (1 << 2);
    private static final int BIT_USB_HEADSET_DGTL = (1 << 3);
    private static final int BIT_HDMI_AUDIO = (1 << 4);
    private static final int BIT_LINEOUT = (1 << 5);
    private static final int SUPPORTED_HEADSETS = (BIT_HEADSET|BIT_HEADSET_NO_MIC|
                                                   BIT_USB_HEADSET_ANLG|BIT_USB_HEADSET_DGTL|
                                                   BIT_HDMI_AUDIO|BIT_LINEOUT);

    private static final String NAME_H2W = "h2w";
    private static final String NAME_USB_AUDIO = "usb_audio";
    private static final String NAME_EMU_AUDIO = "semu_audio";
    private static final String NAME_SAMSUNG_USB_AUDIO = "dock";
    private static final String NAME_HDMI_AUDIO = "hdmi_audio";
    private static final String NAME_HDMI = "hdmi";

    private static final int MSG_NEW_DEVICE_STATE = 1;
    private static final int MSG_SYSTEM_READY = 2;

    private final Object mLock = new Object();

    private final WakeLock mWakeLock;  // held while there is a pending route change
    private final AudioManager mAudioManager;

    private int mHeadsetState;

    private int mSwitchValues;

    private final WiredAccessoryObserver mObserver;
    private final InputManagerService mInputManager;

    private final boolean mUseDevInputEventForAudioJack;

    public WiredAccessoryManager(Context context, InputManagerService inputManager) {
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiredAccessoryManager");
        mWakeLock.setReferenceCounted(false);
        mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        mInputManager = inputManager;

        mUseDevInputEventForAudioJack =
                context.getResources().getBoolean(R.bool.config_useDevInputEventForAudioJack);

        mObserver = new WiredAccessoryObserver();
    }

    private void onSystemReady() {
        if (mUseDevInputEventForAudioJack) {
            int switchValues = 0;
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_HEADPHONE_INSERT) == 1) {
                switchValues |= SW_HEADPHONE_INSERT_BIT;
            }
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_MICROPHONE_INSERT) == 1) {
                switchValues |= SW_MICROPHONE_INSERT_BIT;
            }
            if (mInputManager.getSwitchState(-1, InputDevice.SOURCE_ANY, SW_LINEOUT_INSERT) == 1) {
                switchValues |= SW_LINEOUT_INSERT_BIT;
            }
            notifyWiredAccessoryChanged(0, switchValues,
                    SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT | SW_LINEOUT_INSERT_BIT);
        }

        mObserver.init();
    }

    @Override
    public void notifyWiredAccessoryChanged(long whenNanos, int switchValues, int switchMask) {
        if (LOG) Slog.v(TAG, "notifyWiredAccessoryChanged: when=" + whenNanos
                + " bits=" + switchCodeToString(switchValues, switchMask)
                + " mask=" + Integer.toHexString(switchMask));

        synchronized (mLock) {
            int headset;
            mSwitchValues = (mSwitchValues & ~switchMask) | switchValues;
            switch (mSwitchValues &
                (SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT | SW_LINEOUT_INSERT_BIT)) {
                case 0:
                    headset = 0;
                    break;

                case SW_HEADPHONE_INSERT_BIT:
                    headset = BIT_HEADSET_NO_MIC;
                    break;

                case SW_LINEOUT_INSERT_BIT:
                    headset = BIT_LINEOUT;
                    break;

                case SW_HEADPHONE_INSERT_BIT | SW_MICROPHONE_INSERT_BIT:
                    headset = BIT_HEADSET;
                    break;

                case SW_MICROPHONE_INSERT_BIT:
                    headset = BIT_HEADSET;
                    break;

                default:
                    headset = 0;
                    break;
            }

            updateLocked(NAME_H2W,
                (mHeadsetState & ~(BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT)) | headset);
        }
    }

    @Override
    public void systemReady() {
        synchronized (mLock) {
            mWakeLock.acquire();

            Message msg = mHandler.obtainMessage(MSG_SYSTEM_READY, 0, 0, null);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Compare the existing headset state with the new state and pass along accordingly. Note
     * that this only supports a single headset at a time. Inserting both a usb and jacked headset
     * results in support for the last one plugged in. Similarly, unplugging either is seen as
     * unplugging all.
     *
     * @param newName One of the NAME_xxx variables defined above.
     * @param newState 0 or one of the BIT_xxx variables defined above.
     */
    private void updateLocked(String newName, int newState) {
        // Retain only relevant bits
        int headsetState = newState & SUPPORTED_HEADSETS;
        int usb_headset_anlg = headsetState & BIT_USB_HEADSET_ANLG;
        int usb_headset_dgtl = headsetState & BIT_USB_HEADSET_DGTL;
        int h2w_headset = headsetState & (BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT);
        boolean h2wStateChange = true;
        boolean usbStateChange = true;
        if (LOG) Slog.v(TAG, "newName=" + newName
                + " newState=" + newState
                + " headsetState=" + headsetState
                + " prev headsetState=" + mHeadsetState);

        if (mHeadsetState == headsetState) {
            Log.e(TAG, "No state change.");
            return;
        }

        // reject all suspect transitions: only accept state changes from:
        // - a: 0 headset to 1 headset
        // - b: 1 headset to 0 headset
        if (h2w_headset == (BIT_HEADSET | BIT_HEADSET_NO_MIC | BIT_LINEOUT)) {
            Log.e(TAG, "Invalid combination, unsetting h2w flag");
            h2wStateChange = false;
        }
        // - c: 0 usb headset to 1 usb headset
        // - d: 1 usb headset to 0 usb headset
        if (usb_headset_anlg == BIT_USB_HEADSET_ANLG && usb_headset_dgtl == BIT_USB_HEADSET_DGTL) {
            Log.e(TAG, "Invalid combination, unsetting usb flag");
            usbStateChange = false;
        }
        if (!h2wStateChange && !usbStateChange) {
            Log.e(TAG, "invalid transition, returning ...");
            return;
        }

        mWakeLock.acquire();

        Log.i(TAG, "MSG_NEW_DEVICE_STATE");
        Message msg = mHandler.obtainMessage(MSG_NEW_DEVICE_STATE, headsetState,
                mHeadsetState, "");
        mHandler.sendMessage(msg);

        mHeadsetState = headsetState;
    }

    private final Handler mHandler = new Handler(Looper.myLooper(), null, true) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_NEW_DEVICE_STATE:
                    setDevicesState(msg.arg1, msg.arg2, (String)msg.obj);
                    mWakeLock.release();
                    break;
                case MSG_SYSTEM_READY:
                    onSystemReady();
                    mWakeLock.release();
                    break;
            }
        }
    };

    private void setDevicesState(
            int headsetState, int prevHeadsetState, String headsetName) {
        synchronized (mLock) {
            int allHeadsets = SUPPORTED_HEADSETS;
            for (int curHeadset = 1; allHeadsets != 0; curHeadset <<= 1) {
                if ((curHeadset & allHeadsets) != 0) {
                    setDeviceStateLocked(curHeadset, headsetState, prevHeadsetState, headsetName);
                    allHeadsets &= ~curHeadset;
                }
            }
        }
    }

    private void setDeviceStateLocked(int headset,
            int headsetState, int prevHeadsetState, String headsetName) {
        if ((headsetState & headset) != (prevHeadsetState & headset)) {
            int outDevice = 0;
            int inDevice = 0;
            int state;

            if ((headsetState & headset) != 0) {
                state = 1;
            } else {
                state = 0;
            }

            if (headset == BIT_HEADSET) {
                outDevice = AudioManager.DEVICE_OUT_WIRED_HEADSET;
                inDevice = AudioManager.DEVICE_IN_WIRED_HEADSET;
            } else if (headset == BIT_HEADSET_NO_MIC){
                outDevice = AudioManager.DEVICE_OUT_WIRED_HEADPHONE;
            } else if (headset == BIT_LINEOUT){
                outDevice = AudioManager.DEVICE_OUT_LINE;
            } else if (headset == BIT_USB_HEADSET_ANLG) {
                outDevice = AudioManager.DEVICE_OUT_ANLG_DOCK_HEADSET;
            } else if (headset == BIT_USB_HEADSET_DGTL) {
                outDevice = AudioManager.DEVICE_OUT_DGTL_DOCK_HEADSET;
            } else if (headset == BIT_HDMI_AUDIO) {
                outDevice = AudioManager.DEVICE_OUT_HDMI;
            } else {
                Slog.e(TAG, "setDeviceState() invalid headset type: "+headset);
                return;
            }

            if (LOG) {
                Slog.v(TAG, "headsetName: " + headsetName +
                        (state == 1 ? " connected" : " disconnected"));
            }

            if (outDevice != 0) {
              mAudioManager.setWiredDeviceConnectionState(outDevice, state, "", headsetName);
            }
            if (inDevice != 0) {
              mAudioManager.setWiredDeviceConnectionState(inDevice, state, "", headsetName);
            }
        }
    }

    private String switchCodeToString(int switchValues, int switchMask) {
        StringBuffer sb = new StringBuffer();
        if ((switchMask & SW_HEADPHONE_INSERT_BIT) != 0 &&
                (switchValues & SW_HEADPHONE_INSERT_BIT) != 0) {
            sb.append("SW_HEADPHONE_INSERT ");
        }
        if ((switchMask & SW_MICROPHONE_INSERT_BIT) != 0 &&
                (switchValues & SW_MICROPHONE_INSERT_BIT) != 0) {
            sb.append("SW_MICROPHONE_INSERT");
        }
        return sb.toString();
    }

    class WiredAccessoryObserver extends UEventObserver {
        private final List<UEventInfo> mUEventInfo;

        public WiredAccessoryObserver() {
            mUEventInfo = makeObservedUEventList();
        }

        void init() {
            synchronized (mLock) {
                if (LOG) Slog.v(TAG, "init()");
                char[] buffer = new char[1024];

                for (int i = 0; i < mUEventInfo.size(); ++i) {
                    UEventInfo uei = mUEventInfo.get(i);
                    try {
                        int curState;
                        FileReader file = new FileReader(uei.getSwitchStatePath());
                        int len = file.read(buffer, 0, 1024);
                        file.close();
                        curState = validateSwitchState(
                                Integer.valueOf((new String(buffer, 0, len)).trim()));

                        if (curState > 0) {
                            updateStateLocked(uei.getDevPath(), uei.getDevName(), curState);
                        }
                    } catch (FileNotFoundException e) {
                        Slog.w(TAG, uei.getSwitchStatePath() +
                                " not found while attempting to determine initial switch state");
                    } catch (Exception e) {
                        Slog.e(TAG, "" , e);
                    }
                }
            }

            // At any given time accessories could be inserted
            // one on the board, one on the dock, one on the
            // samsung dock and one on HDMI:
            // observe all UEVENTs that have valid switch supported
            // by the Kernel
            for (int i = 0; i < mUEventInfo.size(); ++i) {
                UEventInfo uei = mUEventInfo.get(i);
                startObserving("DEVPATH="+uei.getDevPath());
            }
        }

        private int validateSwitchState(int state) {
            // Some drivers, namely HTC headset ones, add additional bits to
            // the switch state. As we only are able to deal with the states
            // 0, 1 and 2, mask out all the other bits
            return state & 0x3;
        }

        private List<UEventInfo> makeObservedUEventList() {
            List<UEventInfo> retVal = new ArrayList<UEventInfo>();
            UEventInfo uei;

            // Monitor h2w
            if (!mUseDevInputEventForAudioJack) {
                uei = new UEventInfo(NAME_H2W, BIT_HEADSET, BIT_HEADSET_NO_MIC, BIT_LINEOUT);
                if (uei.checkSwitchExists()) {
                    retVal.add(uei);
                } else {
                    Slog.w(TAG, "This kernel does not have wired headset support");
                }
            }

            // Monitor USB
            uei = new UEventInfo(NAME_USB_AUDIO, BIT_USB_HEADSET_ANLG, BIT_USB_HEADSET_DGTL, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            } else {
                Slog.w(TAG, "This kernel does not have usb audio support");
            }

            // Monitor Motorola EMU audio jack
            uei = new UEventInfo(NAME_EMU_AUDIO, BIT_USB_HEADSET_ANLG, 0, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            }

            // Monitor Samsung USB audio
            uei = new UEventInfo(NAME_SAMSUNG_USB_AUDIO, BIT_USB_HEADSET_DGTL,
                                 BIT_USB_HEADSET_ANLG, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            }

            // Monitor HDMI
            //
            // If the kernel has support for the "hdmi_audio" switch, use that.  It will be
            // signalled only when the HDMI driver has a video mode configured, and the downstream
            // sink indicates support for audio in its EDID.
            //
            // If the kernel does not have an "hdmi_audio" switch, just fall back on the older
            // "hdmi" switch instead.
            uei = new UEventInfo(NAME_HDMI_AUDIO, BIT_HDMI_AUDIO, 0, 0);
            if (uei.checkSwitchExists()) {
                retVal.add(uei);
            } else {
                uei = new UEventInfo(NAME_HDMI, BIT_HDMI_AUDIO, 0, 0);
                if (uei.checkSwitchExists()) {
                    retVal.add(uei);
                } else {
                    Slog.w(TAG, "This kernel does not have HDMI audio support");
                }
            }

            return retVal;
        }

        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            if (LOG) Slog.v(TAG, "Headset UEVENT: " + event.toString());

            try {
                String devPath = event.get("DEVPATH");
                String name = event.get("SWITCH_NAME");
                if (SystemProperties.getBoolean("tcmd.whisper", false) &&
                        (name.equals("CAR") || name.equals("DESK"))) {
                    // Motorola dock - ignore this event and don't change
                    // the audio routing just because we're docked.
                    // Let only the dock emu audio jack sensing do that.
                    return;
                }
                int state = validateSwitchState(Integer.parseInt(event.get("SWITCH_STATE")));
                synchronized (mLock) {
                    updateStateLocked(devPath, name, state);
                }
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Could not parse switch state from event " + event);
            }
        }

        private void updateStateLocked(String devPath, String name, int state) {
            for (int i = 0; i < mUEventInfo.size(); ++i) {
                UEventInfo uei = mUEventInfo.get(i);
                if (devPath.equals(uei.getDevPath())) {
                    updateLocked(name, uei.computeNewHeadsetState(mHeadsetState, state));
                    return;
                }
            }
        }

        private final class UEventInfo {
            private final String mDevName;
            private final int mState1Bits;
            private final int mState2Bits;
            private final int mStateNbits;

            public UEventInfo(String devName, int state1Bits, int state2Bits, int stateNbits) {
                mDevName = devName;
                mState1Bits = state1Bits;
                mState2Bits = state2Bits;
                mStateNbits = stateNbits;
            }

            public String getDevName() { return mDevName; }

            public String getDevPath() {
                return String.format(Locale.US, "/devices/virtual/switch/%s", mDevName);
            }

            public String getSwitchStatePath() {
                return String.format(Locale.US, "/sys/class/switch/%s/state", mDevName);
            }

            public boolean checkSwitchExists() {
                File f = new File(getSwitchStatePath());
                return f.exists();
            }

            public int computeNewHeadsetState(int headsetState, int switchState) {
                int preserveMask = ~(mState1Bits | mState2Bits | mStateNbits);
                int setBits = ((switchState == 1) ? mState1Bits :
                              ((switchState == 2) ? mState2Bits :
                              ((switchState == mStateNbits) ? mStateNbits : 0)));

                return ((headsetState & preserveMask) | setBits);
            }
        }
    }
}
