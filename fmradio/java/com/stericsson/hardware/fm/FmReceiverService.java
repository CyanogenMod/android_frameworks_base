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
 * The implementation of the FM receiver service.
 *
 * @hide
 */
public class FmReceiverService extends IFmReceiver.Stub {

    private static final String TAG = "FmReceiverService";

    private Context mContext;

    private final HashMap<Object, OnStateChangedReceiver> mOnStateChangedReceivers =
        new HashMap<Object, OnStateChangedReceiver>();

    private final HashMap<Object, OnStartedReceiver> mOnStartedReceivers =
        new HashMap<Object, OnStartedReceiver>();

    private final HashMap<Object, OnErrorReceiver> mOnErrorReceivers =
        new HashMap<Object, OnErrorReceiver>();

    private final HashMap<Object, OnScanReceiver> mOnScanReceivers =
        new HashMap<Object, OnScanReceiver>();

    private final HashMap<Object, OnForcedPauseReceiver> mOnForcedPauseReceivers =
        new HashMap<Object, OnForcedPauseReceiver>();

    private final HashMap<Object, OnForcedResetReceiver> mOnForcedResetReceivers =
        new HashMap<Object, OnForcedResetReceiver>();

    private final HashMap<Object, OnRDSDataReceiver> mOnRDSDataReceivers =
        new HashMap<Object, OnRDSDataReceiver>();

    private final HashMap<Object, OnSignalStrengthReceiver> mOnSignalStrengthReceivers =
        new HashMap<Object, OnSignalStrengthReceiver>();

    private final HashMap<Object, OnStereoReceiver> mOnStereoReceivers =
        new HashMap<Object, OnStereoReceiver>();

    private final HashMap<Object, OnExtraCommandReceiver> mOnExtraCommandReceivers =
        new HashMap<Object, OnExtraCommandReceiver>();

    private final HashMap<Object, OnAutomaticSwitchReceiver> mOnAutomaticSwitchReceivers =
        new HashMap<Object, OnAutomaticSwitchReceiver>();

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
            Log.d(TAG, "FM receiver listener died");

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
            Log.d(TAG, "FM receiver listener died");

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
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnErrorReceivers) {
                mOnErrorReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnScanReceiver implements IBinder.DeathRecipient {
        final IOnScanListener mListener;

        final Object mKey;

        OnScanReceiver(IOnScanListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnScanListener getListener() {
            return mListener;
        }

        public boolean callOnScan(int tunedFrequency, int signalLevel, int scanDirection, boolean aborted) {
            try {
                synchronized (this) {
                    mListener.onScan(tunedFrequency, signalLevel, scanDirection, aborted);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnScan: RemoteException", ex);
                return false;
            }
            return true;
        }

        public boolean callOnFullScan(int[] frequency, int[] signalLevel, boolean aborted) {
            try {
                synchronized (this) {
                    mListener.onFullScan(frequency, signalLevel, aborted);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnFullScan: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnScanReceivers) {
                mOnScanReceivers.remove(this.mKey);
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
            Log.d(TAG, "FM receiver listener died");

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
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnForcedResetReceivers) {
                mOnForcedResetReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnRDSDataReceiver implements IBinder.DeathRecipient {
        final IOnRDSDataFoundListener mListener;

        final Object mKey;

        OnRDSDataReceiver(IOnRDSDataFoundListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnRDSDataFoundListener getListener() {
            return mListener;
        }

        public boolean callOnRDSDataFound(Bundle bundle, int frequency) {
            try {
                synchronized (this) {
                    mListener.onRDSDataFound(bundle, frequency);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnRDSDataFound: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnRDSDataReceivers) {
                mOnRDSDataReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnSignalStrengthReceiver implements IBinder.DeathRecipient {
        final IOnSignalStrengthListener mListener;

        final Object mKey;

        OnSignalStrengthReceiver(IOnSignalStrengthListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnSignalStrengthListener getListener() {
            return mListener;
        }

        public boolean callOnSignalStrengthChanged(int signalStrength) {
            try {
                synchronized (this) {
                    mListener.onSignalStrengthChanged(signalStrength);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnSignalStrengthChanged: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnSignalStrengthReceivers) {
                mOnSignalStrengthReceivers.remove(this.mKey);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private final class OnStereoReceiver implements IBinder.DeathRecipient {
        final IOnStereoListener mListener;

        final Object mKey;

        OnStereoReceiver(IOnStereoListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnStereoListener getListener() {
            return mListener;
        }

        public boolean callOnPlayingInStereo(boolean inStereo) {
            try {
                synchronized (this) {
                    mListener.onPlayingInStereo(inStereo);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnPlayingInStereo: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnStereoReceivers) {
                mOnStereoReceivers.remove(this.mKey);
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

    private final class OnAutomaticSwitchReceiver implements IBinder.DeathRecipient {
        final IOnAutomaticSwitchListener mListener;

        final Object mKey;

        OnAutomaticSwitchReceiver(IOnAutomaticSwitchListener listener) {
            mListener = listener;
            mKey = listener.asBinder();
        }

        public IOnAutomaticSwitchListener getListener() {
            return mListener;
        }

        public boolean callOnAutomaticSwitch(int newFrequency, int reason) {
            try {
                synchronized (this) {
                    mListener.onAutomaticSwitch(newFrequency, reason);
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "callOnAutomaticSwitch: RemoteException", ex);
                return false;
            }
            return true;
        }

        public void binderDied() {
            Log.d(TAG, "FM receiver listener died");

            synchronized (mOnAutomaticSwitchReceivers) {
                mOnAutomaticSwitchReceivers.remove(this.mKey);
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
                if (_fm_receiver_reset() > FmReceiver.STATE_IDLE) {
                    notifyOnForcedReset(FmReceiver.RESET_RADIO_FORBIDDEN);
                }
            }
        }

    };

    /* Returns true if airplane mode is currently on */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    public FmReceiverService(Context context) {
        Log.i(TAG, "FmReceiverService created");
        mContext = context;

        // Register for airplane mode
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_DOCK_EVENT);

        mContext.registerReceiver(mReceiver, filter);
    }

    public void start(FmBand band) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_start(band.getMinFrequency(), band.getMaxFrequency(), band
                .getDefaultFrequency(), band.getChannelOffset());

        if (mOnRDSDataReceivers.size() > 0) {
            Log.d(TAG, "Started with RDS receiver(s), switching on RDS");
            _fm_receiver_setRDS(true);
        }
    }

    public void startAsync(FmBand band) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_startAsync(band.getMinFrequency(), band.getMaxFrequency(), band
                .getDefaultFrequency(), band.getChannelOffset());
    }

    public void reset() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_reset();
    }

    public void pause() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_pause();
    }

    public void resume() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_resume();
    }

    public int getState() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_getState();
    }

    public void setFrequency(int frequency) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_setFrequency(frequency);
    }

    public int getFrequency() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_getFrequency();
    }

    public void scanUp() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_scanUp();
    }

    public void scanDown() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_scanDown();
    }

    public void startFullScan() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_startFullScan();
    }

    public void stopScan() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_stopScan();
    }

    public boolean isRDSDataSupported() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_isRDSDataSupported();
    }

    public boolean isTunedToValidChannel() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_isTunedToValidChannel();
    }

    public void setThreshold(int threshold) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_setThreshold(threshold);
    }

    public int getThreshold() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_getThreshold();
    }

    public int getSignalStrength() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_getSignalStrength();
    }

    public boolean isPlayingInStereo() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_isPlayingInStereo();
    }

    public void setForceMono(boolean forceMono) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_setForceMono(forceMono);
    }

    public void setAutomaticAFSwitching(boolean automatic) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_setAutomaticAFSwitching(automatic);
    }

    public void setAutomaticTASwitching(boolean automatic) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        _fm_receiver_setAutomaticTASwitching(automatic);
    }

    public boolean sendExtraCommand(String command, String[] extras) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        return _fm_receiver_sendExtraCommand(command, extras);
    }

    public void addOnStateChangedListener(IOnStateChangedListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnErrorReceiver receiver = mOnErrorReceivers.get(binder);
        if (receiver != null) {
            mOnErrorReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnErrorListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnScanListener(IOnScanListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnScanReceiver receiver = mOnScanReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnScanReceiver(listener);
            mOnScanReceivers.put(binder, receiver);
            Log.d(TAG, "addOnScanListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnScanListener(IOnScanListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnScanReceiver receiver = mOnScanReceivers.get(binder);
        if (receiver != null) {
            mOnScanReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnScanListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnForcedPauseListener(IOnForcedPauseListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnForcedResetReceiver receiver = mOnForcedResetReceivers.get(binder);
        if (receiver != null) {
            mOnForcedResetReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnForcedResetListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnRDSDataFoundListener(IOnRDSDataFoundListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnRDSDataReceiver receiver = mOnRDSDataReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnRDSDataReceiver(listener);
            mOnRDSDataReceivers.put(binder, receiver);
            Log.d(TAG, "addOnRDSDataFoundListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
            if ((getState() >= FmReceiver.STATE_STARTED) &&
                (mOnRDSDataReceivers.size() == 1)) {
                Log.d(TAG, "First RDS receiver added, switching on RDS");
                _fm_receiver_setRDS(true);
            }
        }
    }

    public void removeOnRDSDataFoundListener(IOnRDSDataFoundListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnRDSDataReceiver receiver = mOnRDSDataReceivers.get(binder);
        if (receiver != null) {
            mOnRDSDataReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnRDSDataFoundListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
            if ((getState() >= FmReceiver.STATE_STARTED) &&
                mOnRDSDataReceivers.isEmpty()) {
                Log.d(TAG, "Last RDS receiver removed, switching off RDS");
                _fm_receiver_setRDS(false);
            }
        }
    }

    public void addOnSignalStrengthChangedListener(IOnSignalStrengthListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnSignalStrengthReceiver receiver = mOnSignalStrengthReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnSignalStrengthReceiver(listener);
            mOnSignalStrengthReceivers.put(binder, receiver);
            Log.d(TAG, "addOnSignalStrengthChangedListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnSignalStrengthChangedListener(IOnSignalStrengthListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnSignalStrengthReceiver receiver = mOnSignalStrengthReceivers.get(binder);
        if (receiver != null) {
            mOnSignalStrengthReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnSignalStrengthChangedListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnPlayingInStereoListener(IOnStereoListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnStereoReceiver receiver = mOnStereoReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnStereoReceiver(listener);
            mOnStereoReceivers.put(binder, receiver);
            Log.d(TAG, "addOnPlayingInStereoListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnPlayingInStereoListener(IOnStereoListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnStereoReceiver receiver = mOnStereoReceivers.get(binder);
        if (receiver != null) {
            mOnStereoReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnPlayingInStereoListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnExtraCommandListener(IOnExtraCommandListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
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
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnExtraCommandReceiver receiver = mOnExtraCommandReceivers.get(binder);
        if (receiver != null) {
            mOnExtraCommandReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnExtraCommandListener(), receiver removed");
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
        }
    }

    public void addOnAutomaticSwitchListener(IOnAutomaticSwitchListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnAutomaticSwitchReceiver receiver = mOnAutomaticSwitchReceivers.get(binder);
        if (receiver == null) {
            receiver = new OnAutomaticSwitchReceiver(listener);
            mOnAutomaticSwitchReceivers.put(binder, receiver);
            Log.d(TAG, "addOnAutomaticSwitchListener(), new receiver added");
            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "linkToDeath failed:", ex);
            }
        }
    }

    public void removeOnAutomaticSwitchListener(IOnAutomaticSwitchListener listener) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.FM_RADIO_RECEIVER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires FM_RADIO_RECEIVER permission");
        }

        IBinder binder = listener.asBinder();
        OnAutomaticSwitchReceiver receiver = mOnAutomaticSwitchReceivers.get(binder);
        if (receiver != null) {
            mOnAutomaticSwitchReceivers.remove(receiver.mKey);
            Log.d(TAG, "removeOnAutomaticSwitchListener(), receiver removed");
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

            if (mOnRDSDataReceivers.size() > 0) {
                Log.d(TAG, "Started event with RDS receiver(s), switching on RDS");
                _fm_receiver_setRDS(true);
            }
        }
    }

    private void notifyOnScan(int frequency, int signalLevel, int scanDirection, boolean aborted) {
        synchronized (mOnScanReceivers) {
            Collection c = mOnScanReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnScanReceiver m = (OnScanReceiver) iterator.next();
                m.callOnScan(frequency, signalLevel, scanDirection, aborted);
            }
        }
    }

    private void notifyOnFullScan(int[] frequency, int[] signalLevel, boolean aborted) {
        synchronized (mOnScanReceivers) {
            Collection c = mOnScanReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnScanReceiver m = (OnScanReceiver) iterator.next();
                m.callOnFullScan(frequency, signalLevel, aborted);
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

    private void notifyOnRDSDataFound(Bundle bundle, int frequency) {
        synchronized (mOnRDSDataReceivers) {
            Collection c = mOnRDSDataReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnRDSDataReceiver m = (OnRDSDataReceiver) iterator.next();
                m.callOnRDSDataFound(bundle, frequency);
            }
        }
    }

    private void notifyOnSignalStrengthChanged(int signalStrength) {
        synchronized (mOnSignalStrengthReceivers) {
            Collection c = mOnSignalStrengthReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnSignalStrengthReceiver m = (OnSignalStrengthReceiver) iterator.next();
                m.callOnSignalStrengthChanged(signalStrength);
            }
        }
    }

    private void notifyOnPlayingInStereo(boolean inStereo) {
        synchronized (mOnStereoReceivers) {
            Collection c = mOnStereoReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnStereoReceiver m = (OnStereoReceiver) iterator.next();
                m.callOnPlayingInStereo(inStereo);
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

    private void notifyOnAutomaticSwitching(int newFrequency, int reason) {
        synchronized (mOnAutomaticSwitchReceivers) {
            Collection c = mOnAutomaticSwitchReceivers.values();
            Iterator iterator = c.iterator();
            while (iterator.hasNext()) {
                OnAutomaticSwitchReceiver m = (OnAutomaticSwitchReceiver) iterator.next();
                m.callOnAutomaticSwitch(newFrequency, reason);
            }
        }
    }

    static
    {
        System.loadLibrary("analogradiobroadcasting");
    }

    private native void _fm_receiver_start(int minFreq, int maxFreq, int defaultFreq, int offset);

    private native void _fm_receiver_startAsync(int minFreq, int maxFreq, int defaultFreq,
            int offset);

    private native int _fm_receiver_reset();

    private native void _fm_receiver_pause();

    private native void _fm_receiver_resume();

    private native int _fm_receiver_getState();

    private native void _fm_receiver_setFrequency(int frequency);

    private native int _fm_receiver_getFrequency();

    private native void _fm_receiver_scanUp();

    private native void _fm_receiver_scanDown();

    private native void _fm_receiver_startFullScan();

    private native void _fm_receiver_stopScan();

    private native boolean _fm_receiver_isRDSDataSupported();

    private native boolean _fm_receiver_isTunedToValidChannel();

    private native void _fm_receiver_setThreshold(int threshold);

    private native int _fm_receiver_getThreshold();

    private native int _fm_receiver_getSignalStrength();

    private native boolean _fm_receiver_isPlayingInStereo();

    private native void _fm_receiver_setForceMono(boolean forceMono);

    private native void _fm_receiver_setAutomaticAFSwitching(boolean automatic);

    private native void _fm_receiver_setAutomaticTASwitching(boolean automatic);

    private native void _fm_receiver_setRDS(boolean receiveRDS);

    private native boolean _fm_receiver_sendExtraCommand(String command, String[] extras);
}
