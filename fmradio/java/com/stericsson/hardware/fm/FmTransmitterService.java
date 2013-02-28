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
 * Author: Markus Grape (markus.grape@stericsson.com) for ST-Ericsson
 */

package com.stericsson.hardware.fm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The implementation of the FM transmitter service.
 *
 * @hide
 */

public class FmTransmitterService extends IFmTransmitter.Stub {

    private static final String TAG = "FmTransmitterService";

    private Context mContext;

    private final HashMap<Object, OnStateChangedReceiver> mOnStateChangedReceivers =
        new HashMap<Object, OnStateChangedReceiver>();

    private final HashMap<Object, OnStartedReceiver> mOnStartedReceivers =
        new HashMap<Object, OnStartedReceiver>();

    private final HashMap<Object, OnErrorReceiver> mOnErrorReceivers =
        new HashMap<Object, OnErrorReceiver>();

    private final HashMap<Object, OnBlockScanReceiver> mOnBlockScanReceivers =
        new HashMap<Object, OnBlockScanReceiver>();

    private final HashMap<Object, OnForcedPauseReceiver> mOnForcedPauseReceivers =
        new HashMap<Object, OnForcedPauseReceiver>();

    private final HashMap<Object, OnForcedResetReceiver> mOnForcedResetReceivers =
        new HashMap<Object, OnForcedResetReceiver>();

    private final HashMap<Object, OnExtraCommandReceiver> mOnExtraCommandReceivers =
        new HashMap<Object, OnExtraCommandReceiver>();

    private final class OnStateChangedReceiver implements IBinder.DeathRecipient {
        final IOnStateChangedListener mListener;
        final Object mKey;

        OnStateChangedReceiver(IOnStateChangedListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnStateChangedListener getListener() {
            return mListener;
        }

        public boolean callOnStateChanged(int oldState, int newState) {
            try {
                synchronized (this) {
                    mListener.onStateChanged(oldState, newState);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnStateChanged: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM transmitter listener died");

            synchronized (mOnStateChangedReceivers) {
                mOnStateChangedReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnStartedReceiver implements IBinder.DeathRecipient {
        final IOnStartedListener mListener;
        final Object mKey;

        OnStartedReceiver(IOnStartedListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnStartedListener getListener() {
            return mListener;
        }

        public boolean callOnStarted() {
            try {
                synchronized (this) {
                    mListener.onStarted();
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnStarted: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM transmitter listener died");

            synchronized (mOnStartedReceivers) {
                mOnStartedReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnErrorReceiver implements IBinder.DeathRecipient {
        final IOnErrorListener mListener;
        final Object mKey;

        OnErrorReceiver(IOnErrorListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnErrorListener getListener() {
            return mListener;
        }

        public boolean callOnError() {
            try {
                synchronized (this) {
                    mListener.onError();
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnError: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM transmitter listener died");

            synchronized (mOnErrorReceivers) {
                mOnErrorReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnBlockScanReceiver implements IBinder.DeathRecipient {
        final IOnBlockScanListener mListener;

        final Object mKey;

        OnBlockScanReceiver(IOnBlockScanListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnBlockScanListener getListener() {
            return mListener;
        }

        public boolean callOnBlockScan(int[] frequency, int[] signalLevel, boolean aborted) {
            try {
                synchronized (this) {
                    mListener.onBlockScan(frequency, signalLevel, aborted);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnBlockScan: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnBlockScanReceivers) {
                mOnBlockScanReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnForcedPauseReceiver implements IBinder.DeathRecipient {
        final IOnForcedPauseListener mListener;
        final Object mKey;

        OnForcedPauseReceiver(IOnForcedPauseListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnForcedPauseListener getListener() {
            return mListener;
        }

        public boolean callOnForcedPause() {
            try {
                synchronized (this) {
                    mListener.onForcedPause();
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnForcedPause: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM transmitter listener died");

            synchronized (mOnForcedPauseReceivers) {
                mOnForcedPauseReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnForcedResetReceiver implements IBinder.DeathRecipient {
        final IOnForcedResetListener mListener;
        final Object mKey;

        OnForcedResetReceiver(IOnForcedResetListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnForcedResetListener getListener() {
            return mListener;
        }

        public boolean callOnForcedReset(int reason) {
            try {
                synchronized (this) {
                    mListener.onForcedReset(reason);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnForcedReset: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM transmitter listener died");

            synchronized (mOnForcedResetReceivers) {
                mOnForcedResetReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnExtraCommandReceiver implements IBinder.DeathRecipient {
        final IOnExtraCommandListener mListener;

        final Object mKey;

        OnExtraCommandReceiver(IOnExtraCommandListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnExtraCommandListener getListener() {
            return mListener;
        }

        public boolean callOnExtraCommand(String response, Bundle extras) {
            try {
                synchronized (this) {
                    mListener.onExtraCommand(response, extras);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnExtraCommand: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnExtraCommandReceivers) {
                mOnExtraCommandReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Log.d(TAG, "onReceive:ACTION_AIRPLANE_MODE_CHANGED");

                // check that airplane mode is off
                if (!isAirplaneModeOn()) {
                    return;
                }

                // power down hardware
                if (_fm_transmitter_reset() > FmTransmitter.STATE_IDLE) {
                    notifyOnForcedReset(FmTransmitter.RESET_RADIO_FORBIDDEN);
                }
            }
        }

    };

    /* Returns true if airplane mode is currently on */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    public FmTransmitterService(Context context) {
        Log.i(TAG, "FmTransmitterService created");
        mContext = context;

        // Register for airplane mode
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_DOCK_EVENT);

        mContext.registerReceiver(mReceiver, filter);
    }

    public void start(FmBand band) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_start(band.getMinFrequency(), band.getMaxFrequency(), band
                .getDefaultFrequency(), band.getChannelOffset());
    }

    public void startAsync(FmBand band) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_startAsync(band.getMinFrequency(), band.getMaxFrequency(), band
                .getDefaultFrequency(), band.getChannelOffset());
    }

    public void reset() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_reset();
    }

    public void pause() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_pause();
    }

    public void resume() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_resume();
    }

    public int getState() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        return _fm_transmitter_getState();
    }

    public void setFrequency(int frequency) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_setFrequency(frequency);
    }

    public int getFrequency() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        return _fm_transmitter_getFrequency();
    }

    public boolean isBlockScanSupported() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        return _fm_transmitter_isBlockScanSupported();
    }

    public void startBlockScan(int startFrequency, int endFrequency) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_startBlockScan(startFrequency, endFrequency);
    }

    public void stopScan() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_stopScan();
    }

    public void setRdsData(Bundle rdsData) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        _fm_transmitter_setRdsData(rdsData);
    }

    public boolean sendExtraCommand(String command, String[] extras) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        return _fm_transmitter_sendExtraCommand(command, extras);
    }

    public void addOnStateChangedListener(IOnStateChangedListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnStateChangedReceiver receiver = mOnStateChangedReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnStateChangedReceiver(listener);
            mOnStateChangedReceivers.put(binder, receiver);
            Log.d(TAG, "addOnStateChangedListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnStateChangedListener(IOnStateChangedListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnStateChangedReceiver receiver = mOnStateChangedReceivers.get(binder);
        if (receiver != null) {
            mOnStateChangedReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnStateChangedListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnStartedListener(IOnStartedListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnStartedReceiver receiver = mOnStartedReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnStartedReceiver(listener);
            mOnStartedReceivers.put(binder, receiver);
            Log.d(TAG, "addOnStartedListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnStartedListener(IOnStartedListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnStartedReceiver receiver = mOnStartedReceivers.get(binder);
        if (receiver != null) {
            mOnStartedReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnStartedListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnErrorListener(IOnErrorListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnErrorReceiver receiver = mOnErrorReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnErrorReceiver(listener);
            mOnErrorReceivers.put(binder, receiver);
            Log.d(TAG, "addOnErrorListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnErrorListener(IOnErrorListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnErrorReceiver receiver = mOnErrorReceivers.get(binder);
        if (receiver != null) {
            mOnErrorReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnErrorListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnBlockScanListener(IOnBlockScanListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnBlockScanReceiver receiver = mOnBlockScanReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnBlockScanReceiver(listener);
            mOnBlockScanReceivers.put(binder, receiver);
            Log.d(TAG, "addOnBlockScanListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnBlockScanListener(IOnBlockScanListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnBlockScanReceiver receiver = mOnBlockScanReceivers.get(binder);
        if (receiver != null) {
            mOnBlockScanReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnBlockScanListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnForcedPauseListener(IOnForcedPauseListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnForcedPauseReceiver receiver = mOnForcedPauseReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnForcedPauseReceiver(listener);
            mOnForcedPauseReceivers.put(binder, receiver);
            Log.d(TAG, "addOnForcedPauseListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnForcedPauseListener(IOnForcedPauseListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnForcedPauseReceiver receiver = mOnForcedPauseReceivers.get(binder);
        if (receiver != null) {
            mOnForcedPauseReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnForcedPauseListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnForcedResetListener(IOnForcedResetListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnForcedResetReceiver receiver = mOnForcedResetReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnForcedResetReceiver(listener);
            mOnForcedResetReceivers.put(binder, receiver);
            Log.d(TAG, "addOnForcedResetListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnForcedResetListener(IOnForcedResetListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnForcedResetReceiver receiver = mOnForcedResetReceivers.get(binder);
        if (receiver != null) {
            mOnForcedResetReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnForcedResetListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnExtraCommandListener(IOnExtraCommandListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnExtraCommandReceiver receiver = mOnExtraCommandReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnExtraCommandReceiver(listener);
            mOnExtraCommandReceivers.put(binder, receiver);
            Log.d(TAG, "addOnExtraCommandListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnExtraCommandListener(IOnExtraCommandListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_TRANSMITTER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_TRANSMITTER permission");
        }

        IBinder binder = listener.asBinder();
        OnExtraCommandReceiver receiver = mOnExtraCommandReceivers.get(binder);
        if (receiver != null) {
            mOnExtraCommandReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnExtraCommandListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    private void notifyOnStateChanged(int oldState, int newState) {
        synchronized (mOnStateChangedReceivers) {
            Collection c = mOnStateChangedReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnStateChangedReceiver m = (OnStateChangedReceiver) iterator.next();
                m.callOnStateChanged(oldState, newState);
            }
        }
    }

    private void notifyOnStarted() {
        synchronized (mOnStartedReceivers) {
            Collection c = mOnStartedReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnStartedReceiver m = (OnStartedReceiver) iterator.next();
                m.callOnStarted();
            }
        }
    }

    private void notifyOnError() {
        synchronized (mOnErrorReceivers) {
            Collection c = mOnErrorReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnErrorReceiver m = (OnErrorReceiver) iterator.next();
                m.callOnError();
            }
        }
    }

    private void notifyOnBlockScan(int[] frequency, int[] signalLevel, boolean aborted) {
        synchronized (mOnBlockScanReceivers) {
            Collection c = mOnBlockScanReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnBlockScanReceiver m = (OnBlockScanReceiver) iterator.next();
                m.callOnBlockScan(frequency, signalLevel, aborted);
            }
        }
    }

    private void notifyOnForcedPause() {
        synchronized (mOnForcedPauseReceivers) {
            Collection c = mOnForcedPauseReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnForcedPauseReceiver m = (OnForcedPauseReceiver) iterator.next();
                m.callOnForcedPause();
            }
        }
    }

    private void notifyOnForcedReset(int reason) {
        synchronized (mOnForcedResetReceivers) {
            Collection c = mOnForcedResetReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnForcedResetReceiver m = (OnForcedResetReceiver) iterator.next();
                m.callOnForcedReset(reason);
            }
        }
    }

    private void notifyOnExtraCommand(String response, Bundle extras) {
        synchronized (mOnExtraCommandReceivers) {
            Collection c = mOnExtraCommandReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnExtraCommandReceiver m = (OnExtraCommandReceiver) iterator.next();
                m.callOnExtraCommand(response, extras);
            }
        }
    }

    private native void _fm_transmitter_start(int minFreq, int maxFreq, int defaultFreq, int offset);

    private native void _fm_transmitter_startAsync(int minFreq, int maxFreq, int defaultFreq,
            int offset);

    private native int _fm_transmitter_reset();

    private native void _fm_transmitter_pause();

    private native void _fm_transmitter_resume();

    private native int _fm_transmitter_getState();

    private native void _fm_transmitter_setFrequency(int frequency);

    private native int _fm_transmitter_getFrequency();

    private native boolean _fm_transmitter_isBlockScanSupported();

    private native void _fm_transmitter_startBlockScan(int startFrequency, int endFrequency);

    private native void _fm_transmitter_stopScan();

    private native void _fm_transmitter_setRdsData(Bundle rdsData);

    private native boolean _fm_transmitter_sendExtraCommand(String command, String[] extras);
}
