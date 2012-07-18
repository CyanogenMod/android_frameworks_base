/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Author: Bjorn Pileryd (bjorn.pileryd@sonyericsson.com)
 * Author: Markus Grape (markus.grape@stericsson.com) for ST-Ericsson
 * Author: Andreas Gustafsson (andreas.a.gustafsson@stericsson.com) for ST-Ericsson
 */

package com.stericsson.hardware.fm;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

/**
 * The implementation of the FmReceiver.
 *
 * @hide
 */
public class FmReceiverImpl extends FmReceiver {

    private static final String TAG = "FmReceiver";

    private IFmReceiver mService;

    /**
     * Save the FmBand used to be able to validate frequencies.
     */
    private FmBand mBand;

    /**
     * Map from OnStateChanged to their associated ListenerTransport objects.
     */
    private HashMap<OnStateChangedListener, OnStateChangedListenerTransport> mOnStateChanged =
        new HashMap<OnStateChangedListener, OnStateChangedListenerTransport>();

    /**
     * Map from OnStarted to their associated ListenerTransport objects.
     */
    private HashMap<OnStartedListener, OnStartedListenerTransport> mOnStarted =
        new HashMap<OnStartedListener, OnStartedListenerTransport>();

    /**
     * Map from OnError to their associated ListenerTransport objects.
     */
    private HashMap<OnErrorListener, OnErrorListenerTransport> mOnError =
        new HashMap<OnErrorListener, OnErrorListenerTransport>();

    /**
     * Map from OnScan to their associated ListenerTransport objects.
     */
    private HashMap<OnScanListener, OnScanListenerTransport> mOnScan =
        new HashMap<OnScanListener, OnScanListenerTransport>();

    /**
     * Map from OnForcedPause to their associated ListenerTransport objects.
     */
    private HashMap<OnForcedPauseListener, OnForcedPauseListenerTransport> mOnForcedPause =
        new HashMap<OnForcedPauseListener, OnForcedPauseListenerTransport>();

    /**
     * Map from OnForcedReset to their associated ListenerTransport objects.
     */
    private HashMap<OnForcedResetListener, OnForcedResetListenerTransport> mOnForcedReset =
        new HashMap<OnForcedResetListener, OnForcedResetListenerTransport>();

    /**
     * Map from OnRDSDataFound to their associated ListenerTransport objects.
     */
    private HashMap<OnRDSDataFoundListener, OnRDSDataListenerTransport> mOnRDSData =
        new HashMap<OnRDSDataFoundListener, OnRDSDataListenerTransport>();

    /**
     * Map from OnSignalStrength to their associated ListenerTransport objects.
     */
    private HashMap<OnSignalStrengthChangedListener, OnSignalStrengthListenerTransport> mOnSignalStrength =
        new HashMap<OnSignalStrengthChangedListener, OnSignalStrengthListenerTransport>();

    /**
     * Map from OnStereo to their associated ListenerTransport objects.
     */
    private HashMap<OnPlayingInStereoListener, OnStereoListenerTransport> mOnStereo =
        new HashMap<OnPlayingInStereoListener, OnStereoListenerTransport>();

    /**
     * Map from OnExtraCommand to their associated ListenerTransport objects.
     */
    private HashMap<OnExtraCommandListener, OnExtraCommandListenerTransport> mOnExtraCommand =
        new HashMap<OnExtraCommandListener, OnExtraCommandListenerTransport>();

    /**
     * Map from OnAutomaticSwitch to their associated ListenerTransport objects.
     */
    private HashMap<OnAutomaticSwitchListener, OnAutomaticSwitchListenerTransport> mOnAutomaticSwitch =
        new HashMap<OnAutomaticSwitchListener, OnAutomaticSwitchListenerTransport>();

    private static class OnStateChangedListenerTransport extends IOnStateChangedListener.Stub {
        private static final int TYPE_ON_STATE_CHANGED = 1;

        private OnStateChangedListener mListener;
        private final Handler mListenerHandler;

        OnStateChangedListenerTransport(OnStateChangedListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }


        public void onStateChanged(int oldState, int newState) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_STATE_CHANGED;
            Bundle b = new Bundle();
            b.putInt("oldState", oldState);
            b.putInt("newState", newState);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_STATE_CHANGED:
                Bundle b = (Bundle) msg.obj;
                int oldState = b.getInt("oldState");
                int newState = b.getInt("newState");
                mListener.onStateChanged(oldState, newState);
                break;
            }
        }
    }

    private static class OnStartedListenerTransport extends IOnStartedListener.Stub {
        private static final int TYPE_ON_STARTED = 1;

        private OnStartedListener mListener;
        private final Handler mListenerHandler;

        OnStartedListenerTransport(OnStartedListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onStarted() {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_STARTED;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_STARTED:
                mListener.onStarted();
                break;
            }
        }
    }

    private static class OnErrorListenerTransport extends IOnErrorListener.Stub {
        private static final int TYPE_ON_ERROR = 1;

        private OnErrorListener mListener;
        private final Handler mListenerHandler;

        OnErrorListenerTransport(OnErrorListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onError() {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_ERROR;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_ERROR:
                mListener.onError();
                break;
            }
        }
    }

    private static class OnScanListenerTransport extends IOnScanListener.Stub {
        private static final int TYPE_ON_SCAN = 1;
        private static final int TYPE_ON_FULLSCAN = 2;

        private OnScanListener mListener;
        private final Handler mListenerHandler;

        OnScanListenerTransport(OnScanListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onScan(int tunedFrequency, int signalStrength,
                           int scanDirection, boolean aborted) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_SCAN;
            Bundle b = new Bundle();
            b.putInt("tunedFrequency", tunedFrequency);
            b.putInt("signalStrength", signalStrength);
            b.putInt("scanDirection", scanDirection);
            b.putBoolean("aborted", aborted);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        public void onFullScan(int[] frequency, int[] signalStrength, boolean aborted) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_FULLSCAN;
            Bundle b = new Bundle();
            b.putIntArray("frequency", frequency);
            b.putIntArray("signalStrength", signalStrength);
            b.putBoolean("aborted", aborted);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;
            boolean aborted;

            switch (msg.what) {
            case TYPE_ON_SCAN:
                b = (Bundle) msg.obj;
                int tunedFrequency = b.getInt("tunedFrequency");
                int signalStrength = b.getInt("signalStrength");
                int scanDirection = b.getInt("scanDirection");
                aborted = b.getBoolean("aborted");
                mListener.onScan(tunedFrequency, signalStrength, scanDirection, aborted);
                break;
            case TYPE_ON_FULLSCAN:
                b = (Bundle) msg.obj;
                int[] frequency = b.getIntArray("frequency");
                int[] signalStrengths = b.getIntArray("signalStrength");
                aborted = b.getBoolean("aborted");
                mListener.onFullScan(frequency, signalStrengths, aborted);
                break;
            }
        }
    }

    private static class OnForcedPauseListenerTransport extends IOnForcedPauseListener.Stub {
        private static final int TYPE_ON_FORCEDPAUSE = 1;

        private OnForcedPauseListener mListener;
        private final Handler mListenerHandler;

        OnForcedPauseListenerTransport(OnForcedPauseListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onForcedPause() {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_FORCEDPAUSE;
            Bundle b = new Bundle();
            // Need more here? Or remove?
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_FORCEDPAUSE:
                Bundle b = (Bundle) msg.obj;
                mListener.onForcedPause();
                break;
            }
        }
    }

    private static class OnForcedResetListenerTransport extends IOnForcedResetListener.Stub {
        private static final int TYPE_ON_FORCEDRESET = 1;

        private OnForcedResetListener mListener;
        private final Handler mListenerHandler;

        OnForcedResetListenerTransport(OnForcedResetListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onForcedReset(int reason) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_FORCEDRESET;
            Bundle b = new Bundle();
            b.putInt("reason", reason);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_FORCEDRESET:
                Bundle b = (Bundle) msg.obj;
                int reason = b.getInt("reason");
                mListener.onForcedReset(reason);
                break;
            }
        }
    }

    private static class OnRDSDataListenerTransport extends IOnRDSDataFoundListener.Stub {
        private static final int TYPE_ON_RDS_DATA = 1;

        private OnRDSDataFoundListener mListener;
        private final Handler mListenerHandler;

        OnRDSDataListenerTransport(OnRDSDataFoundListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onRDSDataFound(Bundle rdsData, int frequency) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_RDS_DATA;
            Bundle b = new Bundle();
            if (rdsData != null) {
                b.putBundle("rdsData", rdsData);
            }
            b.putInt("frequency", frequency);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;

            switch (msg.what) {
            case TYPE_ON_RDS_DATA:
                b = (Bundle) msg.obj;
                int frequency = b.getInt("frequency");
                Bundle rdsData = b.getBundle("rdsData");
                mListener.onRDSDataFound(rdsData, frequency);
                break;
            }
        }
    }

    private static class OnSignalStrengthListenerTransport extends IOnSignalStrengthListener.Stub {
        private static final int TYPE_ON_SIGNAL_STRENGTH_CHANGED = 1;

        private OnSignalStrengthChangedListener mListener;
        private final Handler mListenerHandler;

        OnSignalStrengthListenerTransport(OnSignalStrengthChangedListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onSignalStrengthChanged(int signalStrength) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_SIGNAL_STRENGTH_CHANGED;
            Bundle b = new Bundle();
            b.putInt("signalStrength", signalStrength);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;
            boolean aborted;

            switch (msg.what) {
            case TYPE_ON_SIGNAL_STRENGTH_CHANGED:
                b = (Bundle) msg.obj;
                int signalStrength = b.getInt("signalStrength");
                mListener.onSignalStrengthChanged(signalStrength);
                break;
            }
        }
    }

    private static class OnStereoListenerTransport extends IOnStereoListener.Stub {
        private static final int TYPE_ON_STEREO = 1;

        private OnPlayingInStereoListener mListener;
        private final Handler mListenerHandler;

        OnStereoListenerTransport(OnPlayingInStereoListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onPlayingInStereo(boolean inStereo) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_STEREO;
            Bundle b = new Bundle();
            b.putBoolean("inStereo", inStereo);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;
            boolean aborted;

            switch (msg.what) {
            case TYPE_ON_STEREO:
                b = (Bundle) msg.obj;
                boolean inStereo = b.getBoolean("inStereo");
                mListener.onPlayingInStereo(inStereo);
                break;
            }
        }
    }

    private static class OnExtraCommandListenerTransport extends IOnExtraCommandListener.Stub {
        private static final int TYPE_ON_EXTRA_COMMAND = 1;

        private OnExtraCommandListener mListener;
        private final Handler mListenerHandler;

        OnExtraCommandListenerTransport(OnExtraCommandListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onExtraCommand(String response, Bundle extras) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_EXTRA_COMMAND;
            Bundle b = new Bundle();
            b.putString("response", response);
            b.putBundle("extras", extras);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;
            boolean aborted;

            switch (msg.what) {
            case TYPE_ON_EXTRA_COMMAND:
                b = (Bundle) msg.obj;
                String response = b.getString("response");
                Bundle extras = b.getBundle("extras");
                mListener.onExtraCommand(response, extras);
                break;
            }
        }
    }

    private static class OnAutomaticSwitchListenerTransport extends IOnAutomaticSwitchListener.Stub {
        private static final int TYPE_ON_AUTOMATIC_SWITCH = 1;

        private OnAutomaticSwitchListener mListener;
        private final Handler mListenerHandler;

        OnAutomaticSwitchListenerTransport(OnAutomaticSwitchListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onAutomaticSwitch(int newFrequency, int reason) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_AUTOMATIC_SWITCH;
            Bundle b = new Bundle();
            b.putInt("newFrequency", newFrequency);
            b.putInt("reason", reason);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            Bundle b;
            boolean aborted;

            switch (msg.what) {
            case TYPE_ON_AUTOMATIC_SWITCH:
                b = (Bundle) msg.obj;
                int newFrequency = b.getInt("newFrequency");
                int reason = b.getInt("reason");
                mListener.onAutomaticSwitch(newFrequency, reason);
                break;
            }
        }
    }

    /**
     * Creates a new FmReceiver instance. Applications will almost always want
     * to use {@link android.content.Context#getSystemService
     * Context.getSystemService()} to retrieve the standard
     * {@link android.content.Context "fm_receiver"}.
     *
     * @param service
     *            the Binder interface
     * @hide - hide this because it takes in a parameter of type IFmReceiver,
     *       which is a system private class.
     */
    public FmReceiverImpl(IFmReceiver service) {
        mService = service;
    }

    @Override
    public void startAsync(FmBand band) throws IOException {
        if (band == null) {
            throw new IllegalArgumentException("Band cannot be null");
        }
        try {
            mService.startAsync(band);
            mBand = band;
        } catch (RemoteException ex) {
            Log.e(TAG, "startAsync: RemoteException", ex);
        }
    }

    @Override
    public void start(FmBand band) throws IOException {
        if (band == null) {
            throw new IllegalArgumentException("Band cannot be null");
        }
        try {
            mService.start(band);
            mBand = band;
        } catch (RemoteException ex) {
            Log.e(TAG, "start: RemoteException", ex);
        }
    }

    @Override
    public void resume() throws IOException {
        try {
            mService.resume();
        } catch (RemoteException ex) {
            Log.e(TAG, "resume: RemoteException", ex);
        }
    }

    @Override
    public void pause() throws IOException {
        try {
            mService.pause();
        } catch (RemoteException ex) {
            Log.e(TAG, "pause: RemoteException", ex);
        }
    }

    @Override
    public void reset() throws IOException {
        try {
            mService.reset();
            mBand = null;
        } catch (RemoteException ex) {
            Log.e(TAG, "reset: RemoteException", ex);
        }
    }

    @Override
    public int getState() {
        try {
            return mService.getState();
        } catch (RemoteException ex) {
            Log.e(TAG, "getState: RemoteException", ex);
            return STATE_IDLE;
        }
    }

    @Override
    public boolean isRDSDataSupported() {
        try {
            return mService.isRDSDataSupported();
        } catch (RemoteException ex) {
            Log.e(TAG, "isRDSDataSupported: RemoteException", ex);
            return false;
        }
    }

    @Override
    public boolean isTunedToValidChannel() {
        try {
            return mService.isTunedToValidChannel();
        } catch (RemoteException ex) {
            Log.e(TAG, "isTunedToValidChannel: RemoteException", ex);
            return false;
        }
    }

    @Override
    public void setThreshold(int threshold) throws IOException {
        if (threshold < 0 || threshold > 1000) {
            throw new IllegalArgumentException("threshold not within limits");
        }
        try {
            mService.setThreshold(threshold);
        } catch (RemoteException ex) {
            Log.e(TAG, "setThreshold: RemoteException", ex);
        }
    }

    @Override
    public int getThreshold() throws IOException {
        try {
            return mService.getThreshold();
        } catch (RemoteException ex) {
            Log.e(TAG, "getThreshold: RemoteException", ex);
            return 0;
        }
    }

    @Override
    public int getFrequency() throws IOException {
        try {
            return mService.getFrequency();
        } catch (RemoteException ex) {
            Log.e(TAG, "getFrequency: RemoteException", ex);
            return FmBand.FM_FREQUENCY_UNKNOWN;
        }
    }

    @Override
    public int getSignalStrength() throws IOException {
        try {
            return mService.getSignalStrength();
        } catch (RemoteException ex) {
            Log.e(TAG, "getSignalStrength: RemoteException", ex);
            return SIGNAL_STRENGTH_UNKNOWN;
        }
    }

    @Override
    public boolean isPlayingInStereo() {
        try {
            return mService.isPlayingInStereo();
        } catch (RemoteException ex) {
            Log.e(TAG, "isPlayingInStereo: RemoteException", ex);
            return false;
        }
    }

    @Override
    public void setForceMono(boolean forceMono) {
        try {
            mService.setForceMono(forceMono);
        } catch (RemoteException ex) {
            Log.e(TAG, "setForceMono: RemoteException", ex);
        }
    }

    @Override
    public void setAutomaticAFSwitching(boolean automatic) {
        try {
            mService.setAutomaticAFSwitching(automatic);
        } catch (RemoteException ex) {
            Log.e(TAG, "setAutomaticAFSwitching: RemoteException", ex);
        }
    }

    @Override
    public void setAutomaticTASwitching(boolean automatic) {
        try {
            mService.setAutomaticTASwitching(automatic);
        } catch (RemoteException ex) {
            Log.e(TAG, "setAutomaticTASwitching: RemoteException", ex);
        }
    }

    @Override
    public void setFrequency(int frequency) throws IOException {
        if (mBand != null && !mBand.isFrequencyValid(frequency)) {
            throw new IllegalArgumentException("Frequency is not valid in this band.");
        }

        try {
            mService.setFrequency(frequency);
        } catch (RemoteException ex) {
            Log.e(TAG, "setFrequency: RemoteException", ex);
        }
    }

    @Override
    public void startFullScan() {
        try {
            mService.startFullScan();
        } catch (RemoteException ex) {
            Log.e(TAG, "startFullScan: RemoteException", ex);
        }
    }

    @Override
    public void scanDown() {
        try {
            mService.scanDown();
        } catch (RemoteException ex) {
            Log.e(TAG, "scanDown: RemoteException", ex);
        }
    }

    @Override
    public void scanUp() {
        try {
            mService.scanUp();
        } catch (RemoteException ex) {
            Log.e(TAG, "scanUp: RemoteException", ex);
        }
    }

    @Override
    public void stopScan() {
        try {
            mService.stopScan();
        } catch (RemoteException ex) {
            Log.e(TAG, "stopScan: RemoteException", ex);
        }
    }

    @Override
    public boolean sendExtraCommand(String command, String[] extras) {
        try {
            return mService.sendExtraCommand(command, extras);
        } catch (RemoteException ex) {
            Log.e(TAG, "sendExtraCommand: RemoteException", ex);
            return false;
        }
    }

    @Override
    public void addOnStartedListener(OnStartedListener listener) {
        if (mOnStarted.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnStarted) {
                OnStartedListenerTransport transport = new OnStartedListenerTransport(listener);
                mService.addOnStartedListener(transport);
                mOnStarted.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnStartedListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnStartedListener(OnStartedListener listener) {
        try {
            OnStartedListenerTransport transport = mOnStarted.remove(listener);
            if (transport != null) {
                mService.removeOnStartedListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnStartedListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnScanListener(OnScanListener listener) {
        if (mOnScan.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnScan) {
                OnScanListenerTransport transport = new OnScanListenerTransport(listener);
                mService.addOnScanListener(transport);
                mOnScan.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnScanListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnScanListener(OnScanListener listener) {
        try {
            OnScanListenerTransport transport = mOnScan.remove(listener);
            if (transport != null) {
                mService.removeOnScanListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnScanListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnRDSDataFoundListener(OnRDSDataFoundListener listener) {
        if (mOnRDSData.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnRDSData) {
                OnRDSDataListenerTransport transport = new OnRDSDataListenerTransport(listener);
                mService.addOnRDSDataFoundListener(transport);
                mOnRDSData.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnRDSDataFoundListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnRDSDataFoundListener(OnRDSDataFoundListener listener) {
        try {
            OnRDSDataListenerTransport transport = mOnRDSData.remove(listener);
            if (transport != null) {
                mService.removeOnRDSDataFoundListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnRDSDataFoundListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnErrorListener(OnErrorListener listener) {
        if (mOnError.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnError) {
                OnErrorListenerTransport transport = new OnErrorListenerTransport(listener);
                mService.addOnErrorListener(transport);
                mOnError.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnErrorListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnErrorListener(OnErrorListener listener) {
        try {
            OnErrorListenerTransport transport = mOnError.remove(listener);
            if (transport != null) {
                mService.removeOnErrorListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnErrorListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnSignalStrengthChangedListener(OnSignalStrengthChangedListener listener) {
        if (mOnSignalStrength.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnSignalStrength) {
                OnSignalStrengthListenerTransport transport = new OnSignalStrengthListenerTransport(
                        listener);
                mService.addOnSignalStrengthChangedListener(transport);
                mOnSignalStrength.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnSignalStrengthChangedListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnSignalStrengthChangedListener(OnSignalStrengthChangedListener listener) {
        try {
            OnSignalStrengthListenerTransport transport = mOnSignalStrength.remove(listener);
            if (transport != null) {
                mService.removeOnSignalStrengthChangedListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnSignalStrengthChangedListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnPlayingInStereoListener(OnPlayingInStereoListener listener) {
        if (mOnStereo.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnStereo) {
                OnStereoListenerTransport transport = new OnStereoListenerTransport(listener);
                mService.addOnPlayingInStereoListener(transport);
                mOnStereo.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnPlayingInStereoListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnPlayingInStereoListener(OnPlayingInStereoListener listener) {
        try {
            OnStereoListenerTransport transport = mOnStereo.remove(listener);
            if (transport != null) {
                mService.removeOnPlayingInStereoListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnPlayingInStereoListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnForcedPauseListener(OnForcedPauseListener listener) {
        if (mOnForcedPause.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnForcedPause) {
                OnForcedPauseListenerTransport transport = new OnForcedPauseListenerTransport(
                        listener);
                mService.addOnForcedPauseListener(transport);
                mOnForcedPause.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnForcedPauseListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnForcedPauseListener(OnForcedPauseListener listener) {
        try {
            OnForcedPauseListenerTransport transport = mOnForcedPause.remove(listener);
            if (transport != null) {
                mService.removeOnForcedPauseListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnForcedPauseListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnForcedResetListener(OnForcedResetListener listener) {
        if (mOnForcedReset.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnForcedReset) {
                OnForcedResetListenerTransport transport = new OnForcedResetListenerTransport(
                        listener);
                mService.addOnForcedResetListener(transport);
                mOnForcedReset.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnForcedResetListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnForcedResetListener(OnForcedResetListener listener) {
        try {
            OnForcedResetListenerTransport transport = mOnForcedReset.remove(listener);
            if (transport != null) {
                mService.removeOnForcedResetListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnForcedResetListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnStateChangedListener(OnStateChangedListener listener) {
        if (mOnStateChanged.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnStateChanged) {
                OnStateChangedListenerTransport transport = new OnStateChangedListenerTransport(
                        listener);
                mService.addOnStateChangedListener(transport);
                mOnStateChanged.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnStateChangedListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnStateChangedListener(OnStateChangedListener listener) {
        try {
            OnStateChangedListenerTransport transport = mOnStateChanged.remove(listener);
            if (transport != null) {
                mService.removeOnStateChangedListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnStateChangedListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnExtraCommandListener(OnExtraCommandListener listener) {
        if (mOnExtraCommand.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnExtraCommand) {
                OnExtraCommandListenerTransport transport = new OnExtraCommandListenerTransport(
                        listener);
                mService.addOnExtraCommandListener(transport);
                mOnExtraCommand.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnExtraCommandListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnExtraCommandListener(OnExtraCommandListener listener) {
        try {
            OnExtraCommandListenerTransport transport = mOnExtraCommand.remove(listener);
            if (transport != null) {
                mService.removeOnExtraCommandListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnExtraCommandListener: DeadObjectException", ex);
        }
    }

    @Override
    public void addOnAutomaticSwitchListener(OnAutomaticSwitchListener listener) {
        if (mOnAutomaticSwitch.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnAutomaticSwitch) {
                OnAutomaticSwitchListenerTransport transport = new OnAutomaticSwitchListenerTransport(listener);
                mService.addOnAutomaticSwitchListener(transport);
                mOnAutomaticSwitch.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnAutomaticSwitchListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnAutomaticSwitchListener(OnAutomaticSwitchListener listener) {
        try {
            OnAutomaticSwitchListenerTransport transport = mOnAutomaticSwitch.remove(listener);
            if (transport != null) {
                mService.removeOnAutomaticSwitchListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnAutomaticSwitchListener: DeadObjectException", ex);
        }
    }
}
