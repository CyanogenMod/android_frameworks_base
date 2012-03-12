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

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UEventObserver;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.Vibrator;

import android.view.VolumePanel;

import android.media.AudioService;
import android.media.IAudioService;
import android.media.AudioManager;

import com.android.internal.app.ThemeUtils;

import java.io.FileReader;
import java.io.FileNotFoundException;

/**
 * <p>RingerSwitchObserver monitors the ONE's mute switch
 */
class RingerSwitchObserver extends UEventObserver {
    private static final String TAG = RingerSwitchObserver.class.getSimpleName();
    private static final boolean LOG = false;

    private static final String RINGER_SWITCH_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/ringer_switch";
    private static final String RINGER_SWITCH_STATE_PATH = "/sys/class/switch/ringer_switch/state";
    private static final String RINGER_SWITCH_NAME_PATH = "/sys/class/switch/ringer_switch/name";

    private int mRingerswitchState;
    private String mRingerswitchName;
    private IAudioService mAudioService;
    private Context mContext;
    private Context mUiContext;
    private VolumePanel mVolumePanel;

    private final WakeLock mWakeLock;  // held while there is a pending route change

    public RingerSwitchObserver(Context context) {

        mContext = context;

        ThemeUtils.registerThemeChangeReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mUiContext = null;
            }
        });

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RingerSwitchObserver");
        mWakeLock.setReferenceCounted(false);

        startObserving(RINGER_SWITCH_UEVENT_MATCH);

        init();  // set initial status
    }

    @Override
        public void onUEvent(UEventObserver.UEvent event) {
            if (LOG) Log.v(TAG, "Ringerswitch UEVENT: " + event.toString());

            try {
                update(event.get("SWITCH_NAME"), Integer.parseInt(event.get("SWITCH_STATE")));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not parse switch state from event " + event);
            }
        }

    private synchronized final void init() {
        char[] buffer = new char[1024];

        String newName = mRingerswitchName;
        int newState = mRingerswitchState;
        try {
            FileReader file = new FileReader(RINGER_SWITCH_STATE_PATH);
            int len = file.read(buffer, 0, 1024);
            newState = Integer.valueOf((new String(buffer, 0, len)).trim());

            file = new FileReader(RINGER_SWITCH_NAME_PATH);
            len = file.read(buffer, 0, 1024);
            newName = new String(buffer, 0, len).trim();

        } catch (FileNotFoundException e) {
            Log.w(TAG, "This kernel does not have ringer switch support");
            return;
        } catch (Exception e) {
            Log.e(TAG, "" , e);
        }

        mAudioService = IAudioService.Stub.asInterface(ServiceManager.checkService(Context.AUDIO_SERVICE));
    }

    private synchronized final VolumePanel getVolumePanel() {
        if (mUiContext == null || mVolumePanel == null) {
            if (mUiContext == null) {
                mUiContext = ThemeUtils.createUiContext(mContext);
            }
            final Context context = mUiContext != null ? mUiContext : mContext;
            mVolumePanel = new VolumePanel(mContext, (AudioService) mAudioService);
        }

        return mVolumePanel;
    }

    private synchronized final void update(String newName, int newState) {
        if (newName != mRingerswitchName || newState != mRingerswitchState) {
            boolean isRingerOn = (newState == 0 && mRingerswitchState == 1);

            mRingerswitchName = newName;
            mRingerswitchState = newState;


            try {

                if (isRingerOn) {
                    //Log.w(TAG,"Got ringer on");
                    mAudioService.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                } else {
                    //Log.w(TAG,"Got ringer off");
                    AudioManager mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                    boolean vibrateSetting = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON;


                    // We probably lowered the volume on the way "down", so
                    // raise it a notch before muting
                    mAudioService.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE, 0 );
                    mAudioService.setRingerMode(vibrateSetting ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_SILENT);

                }

                // Raise UI
                getVolumePanel().postVolumeChanged(AudioManager.STREAM_RING,AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_VIBRATE);

            } catch (RemoteException e) {
            }
        }
    }


}
