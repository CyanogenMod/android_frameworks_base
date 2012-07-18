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
public class FmTransmitterImpl extends FmTransmitter {

    private static final String TAG = "FmTransmitter";

    private IFmTransmitter mService;

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
     * Map from OnBlockScan to their associated ListenerTransport objects.
     */
    private HashMap<OnScanListener, OnBlockScanListenerTransport> mOnBlockScan =
        new HashMap<OnScanListener, OnBlockScanListenerTransport>();

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
     * Map from OnExtraCommand to their associated ListenerTransport objects.
     */
    private HashMap<OnExtraCommandListener, OnExtraCommandListenerTransport> mOnExtraCommand =
        new HashMap<OnExtraCommandListener, OnExtraCommandListenerTransport>();

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

    private static class OnBlockScanListenerTransport extends IOnBlockScanListener.Stub {
        private static final int TYPE_ON_BLOCKSCAN = 1;

        private OnScanListener mListener;
        private final Handler mListenerHandler;

        OnBlockScanListenerTransport(OnScanListener listener) {
            mListener = listener;

            mListenerHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    _handleMessage(msg);
                }
            };
        }

        public void onBlockScan(int[] frequency, int[] signalStrength, boolean aborted) {
            Message msg = Message.obtain();
            msg.what = TYPE_ON_BLOCKSCAN;
            Bundle b = new Bundle();
            b.putIntArray("frequency", frequency);
            b.putIntArray("signalStrength", signalStrength);
            b.putBoolean("aborted", aborted);
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {

            switch (msg.what) {
            case TYPE_ON_BLOCKSCAN:
                Bundle b = (Bundle) msg.obj;
                int[] frequency = b.getIntArray("frequency");
                int[] signalStrengths = b.getIntArray("signalStrength");
                boolean aborted = b.getBoolean("aborted");
                mListener.onBlockScan(frequency, signalStrengths, aborted);
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
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
            case TYPE_ON_FORCEDPAUSE:
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

    /**
     * Creates a new FmTransmitter instance. Applications will almost always
     * want to use {@link android.content.Context#getSystemService
     * Context.getSystemService()} to retrieve the standard
     * {@link android.content.Context "fm_transmitter"}.
     *
     * @param service
     *            the Binder interface
     * @hide - hide this because it takes in a parameter of type IFmReceiver,
     *       which is a system private class.
     */
    public FmTransmitterImpl(IFmTransmitter service) {
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
    public void setFrequency(int frequency) throws IOException {
        if (mBand != null && !mBand.isFrequencyValid(frequency)) {
            throw new IllegalArgumentException(
                    "Frequency is not valid in this band.");
        }
        try {
            mService.setFrequency(frequency);
        } catch (RemoteException ex) {
            Log.e(TAG, "setFrequency: RemoteException", ex);
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
    public void setRdsData(Bundle rdsData) {
        try {
            mService.setRdsData(rdsData);
        } catch (RemoteException ex) {
            Log.e(TAG, "setRdsData: RemoteException", ex);
        }
    }

    @Override
    public boolean isBlockScanSupported() {
        try {
            return mService.isBlockScanSupported();
        } catch (RemoteException ex) {
            Log.e(TAG, "isBlockScanSupported: RemoteException", ex);
            return false;
        }
    }

    @Override
    public void startBlockScan(int startFrequency, int endFrequency) {
        try {
            mService.startBlockScan(startFrequency, endFrequency);
        } catch (RemoteException ex) {
            Log.e(TAG, "startBlockScan: RemoteException", ex);
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
        if (mOnBlockScan.get(listener) != null) {
            // listener is already registered
            return;
        }
        try {
            synchronized (mOnBlockScan) {
                OnBlockScanListenerTransport transport = new OnBlockScanListenerTransport(listener);
                mService.addOnBlockScanListener(transport);
                mOnBlockScan.put(listener, transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "addOnScanListener: RemoteException", ex);
        }
    }

    @Override
    public void removeOnScanListener(OnScanListener listener) {
        try {
            OnBlockScanListenerTransport transport = mOnBlockScan.remove(listener);
            if (transport != null) {
                mService.removeOnBlockScanListener(transport);
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "removeOnScanListener: DeadObjectException", ex);
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
}
