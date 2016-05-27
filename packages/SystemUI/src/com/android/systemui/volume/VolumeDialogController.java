/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.volume;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IVolumeController;
import android.media.ToneGenerator;
import android.media.VolumePolicy;
import android.media.session.MediaController.PlaybackInfo;
import android.media.session.MediaSession.Token;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.ZenModeConfig;
import android.util.Log;
import android.util.SparseArray;

import com.android.systemui.R;
import com.android.systemui.qs.tiles.DndTile;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import cyanogenmod.providers.CMSettings;

/**
 *  Source of truth for all state / events related to the volume dialog.  No presentation.
 *
 *  All work done on a dedicated background worker thread & associated worker.
 *
 *  Methods ending in "W" must be called on the worker thread.
 */
public class VolumeDialogController {
    private static final String TAG = Util.logTag(VolumeDialogController.class);

    private static final int DYNAMIC_STREAM_START_INDEX = 100;
    private static final int VIBRATE_HINT_DURATION = 50;

    private static final int FREE_DELAY = 10000;
    private static final int BEEP_DURATION = 150;

    private static final int[] STREAMS = {
        AudioSystem.STREAM_ALARM,
        AudioSystem.STREAM_BLUETOOTH_SCO,
        AudioSystem.STREAM_DTMF,
        AudioSystem.STREAM_MUSIC,
        AudioSystem.STREAM_NOTIFICATION,
        AudioSystem.STREAM_RING,
        AudioSystem.STREAM_SYSTEM,
        AudioSystem.STREAM_SYSTEM_ENFORCED,
        AudioSystem.STREAM_TTS,
        AudioSystem.STREAM_VOICE_CALL,
    };

    private final HandlerThread mWorkerThread;
    private final W mWorker;
    private final Context mContext;
    private final AudioManager mAudio;
    private final NotificationManager mNoMan;
    private final ComponentName mComponent;
    private final SettingObserver mObserver;
    private final Receiver mReceiver = new Receiver();
    private final MediaSessions mMediaSessions;
    private final VC mVolumeController = new VC();
    private final C mCallbacks = new C();
    private final State mState = new State();
    private final String[] mStreamTitles;
    private final MediaSessionsCallbacks mMediaSessionsCallbacksW = new MediaSessionsCallbacks();
    private final Vibrator mVibrator;
    private final boolean mHasVibrator;

    private boolean mEnabled;
    private boolean mDestroyed;
    private VolumePolicy mVolumePolicy;
    private boolean mShowDndTile = true;

    private ToneGenerator mToneGenerators[];

    public VolumeDialogController(Context context, ComponentName component) {
        mContext = context.getApplicationContext();
        Events.writeEvent(mContext, Events.EVENT_COLLECTION_STARTED);
        mComponent = component;
        mToneGenerators = new ToneGenerator[AudioSystem.getNumStreamTypes()];
        mWorkerThread = new HandlerThread(VolumeDialogController.class.getSimpleName());
        mWorkerThread.start();
        mWorker = new W(mWorkerThread.getLooper());
        mMediaSessions = createMediaSessions(mContext, mWorkerThread.getLooper(),
                mMediaSessionsCallbacksW);
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNoMan = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mObserver = new SettingObserver(mWorker);
        mObserver.init();
        mReceiver.init();
        mStreamTitles = mContext.getResources().getStringArray(R.array.volume_stream_titles);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = mVibrator != null && mVibrator.hasVibrator();
    }

    public AudioManager getAudioManager() {
        return mAudio;
    }

    public ZenModeConfig getZenModeConfig() {
        return mNoMan.getZenModeConfig();
    }

    public void dismiss() {
        mCallbacks.onDismissRequested(Events.DISMISS_REASON_VOLUME_CONTROLLER);
    }

    public void register() {
        try {
            mAudio.setVolumeController(mVolumeController);
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to set the volume controller", e);
            return;
        }
        setVolumePolicy(mVolumePolicy);
        showDndTile(mShowDndTile);
        try {
            mMediaSessions.init();
        } catch (SecurityException e) {
            Log.w(TAG, "No access to media sessions", e);
        }
    }

    public void setVolumePolicy(VolumePolicy policy) {
        mVolumePolicy = policy;
        if (mVolumePolicy == null) return;
        try {
            mAudio.setVolumePolicy(mVolumePolicy);
        } catch (NoSuchMethodError e) {
            Log.w(TAG, "No volume policy api");
        }
    }

    protected MediaSessions createMediaSessions(Context context, Looper looper,
            MediaSessions.Callbacks callbacks) {
        return new MediaSessions(context, looper, callbacks);
    }

    public void destroy() {
        if (D.BUG) Log.d(TAG, "destroy");
        if (mDestroyed) return;
        mDestroyed = true;
        Events.writeEvent(mContext, Events.EVENT_COLLECTION_STOPPED);
        mMediaSessions.destroy();
        mObserver.destroy();
        mReceiver.destroy();
        mWorkerThread.quitSafely();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println(VolumeDialogController.class.getSimpleName() + " state:");
        pw.print("  mEnabled: "); pw.println(mEnabled);
        pw.print("  mDestroyed: "); pw.println(mDestroyed);
        pw.print("  mVolumePolicy: "); pw.println(mVolumePolicy);
        pw.print("  mState: "); pw.println(mState.toString(4));
        pw.print("  mShowDndTile: "); pw.println(mShowDndTile);
        pw.print("  mHasVibrator: "); pw.println(mHasVibrator);
        pw.print("  mRemoteStreams: "); pw.println(mMediaSessionsCallbacksW.mRemoteStreams
                .values());
        pw.println();
        mMediaSessions.dump(pw);
    }

    public void addCallback(Callbacks callback, Handler handler) {
        mCallbacks.add(callback, handler);
    }

    public void removeCallback(Callbacks callback) {
        mCallbacks.remove(callback);
    }

    public void getState() {
        if (mDestroyed) return;
        mWorker.sendEmptyMessage(W.GET_STATE);
    }

    public void notifyVisible(boolean visible) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.NOTIFY_VISIBLE, visible ? 1 : 0, 0).sendToTarget();
    }

    public void userActivity() {
        if (mDestroyed) return;
        mWorker.removeMessages(W.USER_ACTIVITY);
        mWorker.sendEmptyMessage(W.USER_ACTIVITY);
    }

    public void setRingerMode(int value, boolean external) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_RINGER_MODE, value, external ? 1 : 0).sendToTarget();
    }

    public void setZenMode(int value) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_ZEN_MODE, value, 0).sendToTarget();
    }

    public void setExitCondition(Condition condition) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_EXIT_CONDITION, condition).sendToTarget();
    }

    public void setStreamMute(int stream, boolean mute) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_STREAM_MUTE, stream, mute ? 1 : 0).sendToTarget();
    }

    public void setStreamVolume(int stream, int level) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_STREAM_VOLUME, stream, level).sendToTarget();
    }

    public void setActiveStream(int stream) {
        if (mDestroyed) return;
        mWorker.obtainMessage(W.SET_ACTIVE_STREAM, stream, 0).sendToTarget();
    }

    public void vibrate() {
        if (mHasVibrator) {
            mVibrator.vibrate(VIBRATE_HINT_DURATION);
        }
    }

    public boolean hasVibrator() {
        return mHasVibrator;
    }

    private void onNotifyVisibleW(boolean visible) {
        if (mDestroyed) return;
        mAudio.notifyVolumeControllerVisible(mVolumeController, visible);
        if (!visible) {
            if (updateActiveStreamW(-1)) {
                mCallbacks.onStateChanged(mState);
            }
        }
    }

    protected void onUserActivityW() {
        // hook for subclasses
    }

    private void onShowSafetyWarningW(int flags) {
        mCallbacks.onShowSafetyWarning(flags);
    }

    private boolean checkRoutedToBluetoothW(int stream) {
        boolean changed = false;
        if (stream == AudioManager.STREAM_MUSIC) {
            final boolean routedToBluetooth =
                    (mAudio.getDevicesForStream(AudioManager.STREAM_MUSIC) &
                            (AudioManager.DEVICE_OUT_BLUETOOTH_A2DP |
                            AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                            AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER)) != 0;
            changed |= updateStreamRoutedToBluetoothW(stream, routedToBluetooth);
        }
        return changed;
    }

    private void onVolumeChangedW(int stream, int flags) {
        final boolean showUI = (flags & AudioManager.FLAG_SHOW_UI) != 0;
        final boolean fromKey = (flags & AudioManager.FLAG_FROM_KEY) != 0;
        final boolean showVibrateHint = (flags & AudioManager.FLAG_SHOW_VIBRATE_HINT) != 0;
        final boolean showSilentHint = (flags & AudioManager.FLAG_SHOW_SILENT_HINT) != 0;
        final boolean playSound = (flags & AudioManager.FLAG_PLAY_SOUND) != 0;
        boolean changed = false;
        if (showUI) {
            changed |= updateActiveStreamW(stream);
        }
        int lastAudibleStreamVolume = mAudio.getLastAudibleStreamVolume(stream);
        changed |= updateStreamLevelW(stream, lastAudibleStreamVolume);
        changed |= checkRoutedToBluetoothW(showUI ? AudioManager.STREAM_MUSIC : stream);
        if (changed) {
            mCallbacks.onStateChanged(mState);
        }
        if (showUI) {
            mCallbacks.onShowRequested(Events.SHOW_REASON_VOLUME_CHANGED);
        }
        if (showVibrateHint) {
            mCallbacks.onShowVibrateHint();
        }
        if (showSilentHint) {
            mCallbacks.onShowSilentHint();
        }
        if (playSound) {
            if ((flags & AudioManager.FLAG_PLAY_SOUND) != 0) {
                mWorker.removeMessages(W.PLAY_SOUND);
                mWorker.sendMessageDelayed(mWorker.obtainMessage(W.PLAY_SOUND, stream, flags),
                        AudioSystem.PLAY_SOUND_DELAY);
            }

            if ((flags & AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE) != 0) {
                mWorker.removeMessages(W.PLAY_SOUND);
                onStopSoundsW();
            }

        }
        if (changed && fromKey) {
            Events.writeEvent(mContext, Events.EVENT_KEY, stream, lastAudibleStreamVolume);
        }
    }

    private boolean updateActiveStreamW(int activeStream) {
        if (activeStream == mState.activeStream) return false;
        mState.activeStream = activeStream;
        Events.writeEvent(mContext, Events.EVENT_ACTIVE_STREAM_CHANGED, activeStream);
        if (D.BUG) Log.d(TAG, "updateActiveStreamW " + activeStream);
        final int s = activeStream < DYNAMIC_STREAM_START_INDEX ? activeStream : -1;
        if (D.BUG) Log.d(TAG, "forceVolumeControlStream " + s);
        mAudio.forceVolumeControlStream(s);
        return true;
    }

    private StreamState streamStateW(int stream) {
        StreamState ss = mState.states.get(stream);
        if (ss == null) {
            ss = new StreamState();
            mState.states.put(stream, ss);
        }
        return ss;
    }

    private void onGetStateW() {
        for (int stream : STREAMS) {
            updateStreamLevelW(stream, mAudio.getLastAudibleStreamVolume(stream));
            streamStateW(stream).levelMin = mAudio.getStreamMinVolume(stream);
            streamStateW(stream).levelMax = mAudio.getStreamMaxVolume(stream);
            updateStreamMuteW(stream, mAudio.isStreamMute(stream));
            final StreamState ss = streamStateW(stream);
            ss.muteSupported = mAudio.isStreamAffectedByMute(stream);
            ss.name = mStreamTitles[stream];
            checkRoutedToBluetoothW(stream);
        }
        updateRingerModeExternalW(mAudio.getRingerMode());
        updateZenModeW();
        updateEffectsSuppressorW(mNoMan.getEffectsSuppressor());
        updateZenModeConfigW();
        mCallbacks.onStateChanged(mState);
    }

    private boolean updateStreamRoutedToBluetoothW(int stream, boolean routedToBluetooth) {
        final StreamState ss = streamStateW(stream);
        if (ss.routedToBluetooth == routedToBluetooth) return false;
        ss.routedToBluetooth = routedToBluetooth;
        if (D.BUG) Log.d(TAG, "updateStreamRoutedToBluetoothW stream=" + stream
                + " routedToBluetooth=" + routedToBluetooth);
        return true;
    }

    private boolean updateStreamLevelW(int stream, int level) {
        final StreamState ss = streamStateW(stream);
        if (ss.level == level) return false;
        ss.level = level;
        if (isLogWorthy(stream)) {
            Events.writeEvent(mContext, Events.EVENT_LEVEL_CHANGED, stream, level);
        }
        return true;
    }

    private static boolean isLogWorthy(int stream) {
        switch (stream) {
            case AudioSystem.STREAM_ALARM:
            case AudioSystem.STREAM_BLUETOOTH_SCO:
            case AudioSystem.STREAM_MUSIC:
            case AudioSystem.STREAM_RING:
            case AudioSystem.STREAM_SYSTEM:
            case AudioSystem.STREAM_VOICE_CALL:
                return true;
        }
        return false;
    }

    private boolean updateStreamMuteW(int stream, boolean muted) {
        final StreamState ss = streamStateW(stream);
        if (ss.muted == muted) return false;
        ss.muted = muted;
        if (isLogWorthy(stream)) {
            Events.writeEvent(mContext, Events.EVENT_MUTE_CHANGED, stream, muted);
        }
        if (muted && isRinger(stream)) {
            updateRingerModeInternalW(mAudio.getRingerModeInternal());
        }
        return true;
    }

    private static boolean isRinger(int stream) {
        return stream == AudioManager.STREAM_RING || stream == AudioManager.STREAM_NOTIFICATION;
    }

    private boolean updateLinkNotificationConfigW() {
        boolean linkNotificationWithVolume = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.VOLUME_LINK_NOTIFICATION, 1) == 1;
        if (mState.linkedNotification == linkNotificationWithVolume) {
            return false;
        }
        mState.linkedNotification = linkNotificationWithVolume;
        return true;
    }

    private boolean updateZenModeConfigW() {
        final ZenModeConfig zenModeConfig = getZenModeConfig();
        if (Objects.equals(mState.zenModeConfig, zenModeConfig)) return false;
        mState.zenModeConfig = zenModeConfig;
        return true;
    }

    private boolean updateEffectsSuppressorW(ComponentName effectsSuppressor) {
        if (Objects.equals(mState.effectsSuppressor, effectsSuppressor)) return false;
        mState.effectsSuppressor = effectsSuppressor;
        mState.effectsSuppressorName = getApplicationName(mContext, mState.effectsSuppressor);
        Events.writeEvent(mContext, Events.EVENT_SUPPRESSOR_CHANGED, mState.effectsSuppressor,
                mState.effectsSuppressorName);
        return true;
    }

    private static String getApplicationName(Context context, ComponentName component) {
        if (component == null) return null;
        final PackageManager pm = context.getPackageManager();
        final String pkg = component.getPackageName();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            final String rt = Objects.toString(ai.loadLabel(pm), "").trim();
            if (rt.length() > 0) {
                return rt;
            }
        } catch (NameNotFoundException e) {}
        return pkg;
    }

    private boolean updateZenModeW() {
        final int zen = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
        if (mState.zenMode == zen) return false;
        mState.zenMode = zen;
        Events.writeEvent(mContext, Events.EVENT_ZEN_MODE_CHANGED, zen);
        return true;
    }

    private boolean updateRingerModeExternalW(int rm) {
        if (rm == mState.ringerModeExternal) return false;
        mState.ringerModeExternal = rm;
        Events.writeEvent(mContext, Events.EVENT_EXTERNAL_RINGER_MODE_CHANGED, rm);
        return true;
    }

    private boolean updateRingerModeInternalW(int rm) {
        if (rm == mState.ringerModeInternal) return false;
        mState.ringerModeInternal = rm;
        Events.writeEvent(mContext, Events.EVENT_INTERNAL_RINGER_MODE_CHANGED, rm);
        return true;
    }

    private void onSetRingerModeW(int mode, boolean external) {
        if (external) {
            mAudio.setRingerMode(mode);
        } else {
            mAudio.setRingerModeInternal(mode);
        }
    }

    private void onSetStreamMuteW(int stream, boolean mute) {
        mAudio.adjustStreamVolume(stream, mute ? AudioManager.ADJUST_MUTE
                : AudioManager.ADJUST_UNMUTE, 0);
    }

    private void onSetStreamVolumeW(int stream, int level) {
        if (D.BUG) Log.d(TAG, "onSetStreamVolume " + stream + " level=" + level);
        if (stream >= DYNAMIC_STREAM_START_INDEX) {
            mMediaSessionsCallbacksW.setStreamVolume(stream, level);
            return;
        }
        mAudio.setStreamVolume(stream, level, 0);
    }

    private void onSetActiveStreamW(int stream) {
        boolean changed = updateActiveStreamW(stream);
        if (changed) {
            mCallbacks.onStateChanged(mState);
        }
    }

    private void onSetExitConditionW(Condition condition) {
        mNoMan.setZenMode(mState.zenMode, condition != null ? condition.id : null, TAG);
    }

    private void onSetZenModeW(int mode) {
        if (D.BUG) Log.d(TAG, "onSetZenModeW " + mode);
        mNoMan.setZenMode(mode, null, TAG);
    }

    private void onDismissRequestedW(int reason) {
        mCallbacks.onDismissRequested(reason);
    }

    public void showDndTile(boolean visible) {
        if (D.BUG) Log.d(TAG, "showDndTile");
        DndTile.setVisible(mContext, visible);
    }

    private final class VC extends IVolumeController.Stub {
        private final String TAG = VolumeDialogController.TAG + ".VC";

        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {
            if (D.BUG) Log.d(TAG, "displaySafeVolumeWarning "
                    + Util.audioManagerFlagsToString(flags));
            if (mDestroyed) return;
            mWorker.obtainMessage(W.SHOW_SAFETY_WARNING, flags, 0).sendToTarget();
        }

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            if (D.BUG) Log.d(TAG, "volumeChanged " + AudioSystem.streamToString(streamType)
                    + " " + Util.audioManagerFlagsToString(flags));
            if (mDestroyed) return;
            mWorker.obtainMessage(W.VOLUME_CHANGED, streamType, flags).sendToTarget();
        }

        @Override
        public void masterMuteChanged(int flags) throws RemoteException {
            if (D.BUG) Log.d(TAG, "masterMuteChanged");
        }

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
            if (D.BUG) Log.d(TAG, "setLayoutDirection");
            if (mDestroyed) return;
            mWorker.obtainMessage(W.LAYOUT_DIRECTION_CHANGED, layoutDirection, 0).sendToTarget();
        }

        @Override
        public void dismiss() throws RemoteException {
            if (D.BUG) Log.d(TAG, "dismiss requested");
            if (mDestroyed) return;
            mWorker.obtainMessage(W.DISMISS_REQUESTED, Events.DISMISS_REASON_VOLUME_CONTROLLER, 0)
                    .sendToTarget();
            mWorker.sendEmptyMessage(W.DISMISS_REQUESTED);
        }
    }

    private final class W extends Handler {
        private static final int VOLUME_CHANGED = 1;
        private static final int DISMISS_REQUESTED = 2;
        private static final int GET_STATE = 3;
        private static final int SET_RINGER_MODE = 4;
        private static final int SET_ZEN_MODE = 5;
        private static final int SET_EXIT_CONDITION = 6;
        private static final int SET_STREAM_MUTE = 7;
        private static final int LAYOUT_DIRECTION_CHANGED = 8;
        private static final int CONFIGURATION_CHANGED = 9;
        private static final int SET_STREAM_VOLUME = 10;
        private static final int SET_ACTIVE_STREAM = 11;
        private static final int NOTIFY_VISIBLE = 12;
        private static final int USER_ACTIVITY = 13;
        private static final int SHOW_SAFETY_WARNING = 14;
        private static final int PLAY_SOUND = 15;
        private static final int STOP_SOUNDS = 16;
        private static final int FREE_RESOURCES = 17;

        W(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case VOLUME_CHANGED: onVolumeChangedW(msg.arg1, msg.arg2); break;
                case DISMISS_REQUESTED: onDismissRequestedW(msg.arg1); break;
                case GET_STATE: onGetStateW(); break;
                case SET_RINGER_MODE: onSetRingerModeW(msg.arg1, msg.arg2 != 0); break;
                case SET_ZEN_MODE: onSetZenModeW(msg.arg1); break;
                case SET_EXIT_CONDITION: onSetExitConditionW((Condition) msg.obj); break;
                case SET_STREAM_MUTE: onSetStreamMuteW(msg.arg1, msg.arg2 != 0); break;
                case LAYOUT_DIRECTION_CHANGED: mCallbacks.onLayoutDirectionChanged(msg.arg1); break;
                case CONFIGURATION_CHANGED: mCallbacks.onConfigurationChanged(); break;
                case SET_STREAM_VOLUME: onSetStreamVolumeW(msg.arg1, msg.arg2); break;
                case SET_ACTIVE_STREAM: onSetActiveStreamW(msg.arg1); break;
                case NOTIFY_VISIBLE: onNotifyVisibleW(msg.arg1 != 0); break;
                case USER_ACTIVITY: onUserActivityW(); break;
                case SHOW_SAFETY_WARNING: onShowSafetyWarningW(msg.arg1); break;
                case PLAY_SOUND: onPlaySoundW(msg.arg1, msg.arg2); break;
                case STOP_SOUNDS: onStopSoundsW(); break;
                case FREE_RESOURCES: onFreeResourcesW(); break;
            }
        }
    }

    private final class C implements Callbacks {
        private final HashMap<Callbacks, Handler> mCallbackMap = new HashMap<>();

        public void add(Callbacks callback, Handler handler) {
            if (callback == null || handler == null) throw new IllegalArgumentException();
            mCallbackMap.put(callback, handler);
        }

        public void remove(Callbacks callback) {
            mCallbackMap.remove(callback);
        }

        @Override
        public void onShowRequested(final int reason) {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onShowRequested(reason);
                    }
                });
            }
        }

        @Override
        public void onDismissRequested(final int reason) {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onDismissRequested(reason);
                    }
                });
            }
        }

        @Override
        public void onStateChanged(final State state) {
            final long time = System.currentTimeMillis();
            final State copy = state.copy();
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onStateChanged(copy);
                    }
                });
            }
            Events.writeState(time, copy);
        }

        @Override
        public void onLayoutDirectionChanged(final int layoutDirection) {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onLayoutDirectionChanged(layoutDirection);
                    }
                });
            }
        }

        @Override
        public void onConfigurationChanged() {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onConfigurationChanged();
                    }
                });
            }
        }

        @Override
        public void onShowVibrateHint() {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onShowVibrateHint();
                    }
                });
            }
        }

        @Override
        public void onShowSilentHint() {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onShowSilentHint();
                    }
                });
            }
        }

        @Override
        public void onScreenOff() {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onScreenOff();
                    }
                });
            }
        }

        @Override
        public void onShowSafetyWarning(final int flags) {
            for (final Map.Entry<Callbacks, Handler> entry : mCallbackMap.entrySet()) {
                entry.getValue().post(new Runnable() {
                    @Override
                    public void run() {
                        entry.getKey().onShowSafetyWarning(flags);
                    }
                });
            }
        }
    }


    protected void onPlaySoundW(int streamType, int flags) {

        // If preference is no sound - just exit here
        if (CMSettings.System.getInt(mContext.getContentResolver(),
                CMSettings.System.VOLUME_ADJUST_SOUNDS_ENABLED, 1) == 0) {
            return;
        }

        if (mWorker.hasMessages(W.STOP_SOUNDS)) {
            mWorker.removeMessages(W.STOP_SOUNDS);
            // Force stop right now
            onStopSoundsW();
        }

        ToneGenerator toneGen = getOrCreateToneGeneratorW(streamType);
        if (toneGen != null) {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP);
            mWorker.sendMessageDelayed(mWorker.obtainMessage(W.STOP_SOUNDS), BEEP_DURATION);
        }

        mWorker.removeMessages(W.FREE_RESOURCES);
        mWorker.sendMessageDelayed(mWorker.obtainMessage(W.FREE_RESOURCES), FREE_DELAY);
    }

    protected void onStopSoundsW() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int i = numStreamTypes - 1; i >= 0; i--) {
            ToneGenerator toneGen = mToneGenerators[i];
            if (toneGen != null) {
                toneGen.stopTone();
            }
        }
    }

    private ToneGenerator getOrCreateToneGeneratorW(int streamType) {
        if (mToneGenerators[streamType] == null) {
            try {
                mToneGenerators[streamType] = new ToneGenerator(streamType,
                        ToneGenerator.MAX_VOLUME);
            } catch (RuntimeException e) {
                if (false) {
                    Log.d(TAG, "ToneGenerator constructor failed with "
                            + "RuntimeException: " + e);
                }
            }
        }
        return mToneGenerators[streamType];
    }

    protected void onFreeResourcesW() {
        synchronized (this) {
            for (int i = mToneGenerators.length - 1; i >= 0; i--) {
                if (mToneGenerators[i] != null) {
                    mToneGenerators[i].release();
                }
                mToneGenerators[i] = null;
            }
        }
    }

    private final class SettingObserver extends ContentObserver {
        private final Uri SERVICE_URI = Settings.Secure.getUriFor(
                Settings.Secure.VOLUME_CONTROLLER_SERVICE_COMPONENT);
        private final Uri ZEN_MODE_URI =
                Settings.Global.getUriFor(Settings.Global.ZEN_MODE);
        private final Uri ZEN_MODE_CONFIG_URI =
                Settings.Global.getUriFor(Settings.Global.ZEN_MODE_CONFIG_ETAG);
        private final Uri VOLUME_LINK_NOTIFICATION_URI =
                Settings.Secure.getUriFor(Settings.Secure.VOLUME_LINK_NOTIFICATION);

        public SettingObserver(Handler handler) {
            super(handler);
        }

        public void init() {
            mContext.getContentResolver().registerContentObserver(SERVICE_URI, false, this);
            mContext.getContentResolver().registerContentObserver(ZEN_MODE_URI, false, this);
            mContext.getContentResolver().registerContentObserver(ZEN_MODE_CONFIG_URI, false, this);
            mContext.getContentResolver().registerContentObserver(VOLUME_LINK_NOTIFICATION_URI,
                    false, this);
            onChange(true, SERVICE_URI);
        }

        public void destroy() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            boolean changed = false;
            if (SERVICE_URI.equals(uri)) {
                final String setting = Settings.Secure.getString(mContext.getContentResolver(),
                        Settings.Secure.VOLUME_CONTROLLER_SERVICE_COMPONENT);
                final boolean enabled = setting != null && mComponent != null
                        && mComponent.equals(ComponentName.unflattenFromString(setting));
                if (enabled == mEnabled) return;
                if (enabled) {
                    register();
                }
                mEnabled = enabled;
            }
            if (ZEN_MODE_URI.equals(uri)) {
                changed = updateZenModeW();
            }
            if (ZEN_MODE_CONFIG_URI.equals(uri)) {
                changed = updateZenModeConfigW();
            }
            if (VOLUME_LINK_NOTIFICATION_URI.equals(uri)) {
                changed = updateLinkNotificationConfigW();
            }
            if (changed) {
                mCallbacks.onStateChanged(mState);
            }
        }
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
            filter.addAction(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
            filter.addAction(AudioManager.STREAM_MUTE_CHANGED_ACTION);
            filter.addAction(AudioManager.VOLUME_STEPS_CHANGED_ACTION);
            filter.addAction(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            mContext.registerReceiver(this, filter, null, mWorker);
        }

        public void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            boolean changed = false;
            if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                final int stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                final int level = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1);
                final int oldLevel = intent
                        .getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, -1);
                if (D.BUG) Log.d(TAG, "onReceive VOLUME_CHANGED_ACTION stream=" + stream
                        + " level=" + level + " oldLevel=" + oldLevel);
                changed = updateStreamLevelW(stream, level);
            } else if (action.equals(AudioManager.STREAM_DEVICES_CHANGED_ACTION)) {
                final int stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                final int devices = intent
                        .getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_DEVICES, -1);
                final int oldDevices = intent
                        .getIntExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_DEVICES, -1);
                if (D.BUG) Log.d(TAG, "onReceive STREAM_DEVICES_CHANGED_ACTION stream="
                        + stream + " devices=" + devices + " oldDevices=" + oldDevices);
                changed = checkRoutedToBluetoothW(stream);
            } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                final int rm = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                if (D.BUG) Log.d(TAG, "onReceive RINGER_MODE_CHANGED_ACTION rm="
                        + Util.ringerModeToString(rm));
                changed = updateRingerModeExternalW(rm);
            } else if (action.equals(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)) {
                final int rm = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                if (D.BUG) Log.d(TAG, "onReceive INTERNAL_RINGER_MODE_CHANGED_ACTION rm="
                        + Util.ringerModeToString(rm));
                changed = updateRingerModeInternalW(rm);
            } else if (action.equals(AudioManager.STREAM_MUTE_CHANGED_ACTION)) {
                final int stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                final boolean muted = intent
                        .getBooleanExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false);
                if (D.BUG) Log.d(TAG, "onReceive STREAM_MUTE_CHANGED_ACTION stream=" + stream
                        + " muted=" + muted);
                changed = updateStreamMuteW(stream, muted);
            } else if (action.equals(AudioManager.VOLUME_STEPS_CHANGED_ACTION)) {
                getState();
            } else if (action.equals(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED)) {
                if (D.BUG) Log.d(TAG, "onReceive ACTION_EFFECTS_SUPPRESSOR_CHANGED");
                changed = updateEffectsSuppressorW(mNoMan.getEffectsSuppressor());
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                if (D.BUG) Log.d(TAG, "onReceive ACTION_CONFIGURATION_CHANGED");
                mCallbacks.onConfigurationChanged();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (D.BUG) Log.d(TAG, "onReceive ACTION_SCREEN_OFF");
                mCallbacks.onScreenOff();
            } else if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                if (D.BUG) Log.d(TAG, "onReceive ACTION_CLOSE_SYSTEM_DIALOGS");
                dismiss();
            }
            if (changed) {
                mCallbacks.onStateChanged(mState);
            }
        }
    }

    private final class MediaSessionsCallbacks implements MediaSessions.Callbacks {
        private final HashMap<Token, Integer> mRemoteStreams = new HashMap<>();

        private int mNextStream = DYNAMIC_STREAM_START_INDEX;

        @Override
        public void onRemoteUpdate(Token token, String name, PlaybackInfo pi) {
            if (!mRemoteStreams.containsKey(token)) {
                mRemoteStreams.put(token, mNextStream);
                if (D.BUG) Log.d(TAG, "onRemoteUpdate: " + name + " is stream " + mNextStream);
                mNextStream++;
            }
            final int stream = mRemoteStreams.get(token);
            boolean changed = mState.states.indexOfKey(stream) < 0;
            final StreamState ss = streamStateW(stream);
            ss.dynamic = true;
            ss.levelMin = 0;
            ss.levelMax = pi.getMaxVolume();
            if (ss.level != pi.getCurrentVolume()) {
                ss.level = pi.getCurrentVolume();
                changed = true;
            }
            if (!Objects.equals(ss.name, name)) {
                ss.name = name;
                changed = true;
            }
            if (changed) {
                if (D.BUG) Log.d(TAG, "onRemoteUpdate: " + name + ": " + ss.level
                        + " of " + ss.levelMax);
                mCallbacks.onStateChanged(mState);
            }
        }

        @Override
        public void onRemoteVolumeChanged(Token token, int flags) {
            // If an inactive session changed the remoteVolume, bail
            // since we don't have any active streams to update
            if (!mRemoteStreams.containsKey(token)) {
                Log.i(TAG, "onRemoteVolumeChanged called on inactive" +
                        "stream. Ignoring");
                return;
            }
            final int stream = mRemoteStreams.get(token);
            final boolean showUI = (flags & AudioManager.FLAG_SHOW_UI) != 0;
            boolean changed = updateActiveStreamW(stream);
            if (showUI) {
                changed |= checkRoutedToBluetoothW(AudioManager.STREAM_MUSIC);
            }
            if (changed) {
                mCallbacks.onStateChanged(mState);
            }
            if (showUI) {
                mCallbacks.onShowRequested(Events.SHOW_REASON_REMOTE_VOLUME_CHANGED);
            }
        }

        @Override
        public void onRemoteRemoved(Token token) {
            final int stream = mRemoteStreams.get(token);
            mState.states.remove(stream);
            if (mState.activeStream == stream) {
                updateActiveStreamW(-1);
            }
            mCallbacks.onStateChanged(mState);
        }

        public void setStreamVolume(int stream, int level) {
            final Token t = findToken(stream);
            if (t == null) {
                Log.w(TAG, "setStreamVolume: No token found for stream: " + stream);
                return;
            }
            mMediaSessions.setVolume(t, level);
        }

        private Token findToken(int stream) {
            for (Map.Entry<Token, Integer> entry : mRemoteStreams.entrySet()) {
                if (entry.getValue().equals(stream)) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }

    public static final class StreamState {
        public boolean dynamic;
        public int level;
        public int levelMin;
        public int levelMax;
        public boolean muted;
        public boolean muteSupported;
        public String name;
        public boolean routedToBluetooth;

        public StreamState copy() {
            final StreamState rt = new StreamState();
            rt.dynamic = dynamic;
            rt.level = level;
            rt.levelMin = levelMin;
            rt.levelMax = levelMax;
            rt.muted = muted;
            rt.muteSupported = muteSupported;
            rt.name = name;
            rt.routedToBluetooth = routedToBluetooth;
            return rt;
        }
    }

    public static final class State {
        public static int NO_ACTIVE_STREAM = -1;

        public final SparseArray<StreamState> states = new SparseArray<StreamState>();

        public int ringerModeInternal;
        public int ringerModeExternal;
        public int zenMode;
        public ComponentName effectsSuppressor;
        public String effectsSuppressorName;
        public ZenModeConfig zenModeConfig;
        public int activeStream = NO_ACTIVE_STREAM;
        public boolean linkedNotification;

        public State copy() {
            final State rt = new State();
            for (int i = 0; i < states.size(); i++) {
                rt.states.put(states.keyAt(i), states.valueAt(i).copy());
            }
            rt.ringerModeExternal = ringerModeExternal;
            rt.ringerModeInternal = ringerModeInternal;
            rt.zenMode = zenMode;
            if (effectsSuppressor != null) rt.effectsSuppressor = effectsSuppressor.clone();
            rt.effectsSuppressorName = effectsSuppressorName;
            if (zenModeConfig != null) rt.zenModeConfig = zenModeConfig.copy();
            rt.activeStream = activeStream;
            rt.linkedNotification = linkedNotification;
            return rt;
        }

        @Override
        public String toString() {
            return toString(0);
        }

        public String toString(int indent) {
            final StringBuilder sb = new StringBuilder("{");
            if (indent > 0) sep(sb, indent);
            for (int i = 0; i < states.size(); i++) {
                if (i > 0) {
                    sep(sb, indent);
                }
                final int stream = states.keyAt(i);
                final StreamState ss = states.valueAt(i);
                sb.append(AudioSystem.streamToString(stream)).append(":").append(ss.level)
                        .append('[').append(ss.levelMin).append("..").append(ss.levelMax)
                        .append(']');
                if (ss.muted) sb.append(" [MUTED]");
            }
            sep(sb, indent); sb.append("ringerModeExternal:").append(ringerModeExternal);
            sep(sb, indent); sb.append("ringerModeInternal:").append(ringerModeInternal);
            sep(sb, indent); sb.append("zenMode:").append(zenMode);
            sep(sb, indent); sb.append("effectsSuppressor:").append(effectsSuppressor);
            sep(sb, indent); sb.append("effectsSuppressorName:").append(effectsSuppressorName);
            sep(sb, indent); sb.append("zenModeConfig:").append(zenModeConfig);
            sep(sb, indent); sb.append("activeStream:").append(activeStream);
            sep(sb, indent); sb.append("linkedNotification:").append(linkedNotification);
            if (indent > 0) sep(sb, indent);
            return sb.append('}').toString();
        }

        private static void sep(StringBuilder sb, int indent) {
            if (indent > 0) {
                sb.append('\n');
                for (int i = 0; i < indent; i++) {
                    sb.append(' ');
                }
            } else {
                sb.append(',');
            }
        }

        public Condition getManualExitCondition() {
            return zenModeConfig != null && zenModeConfig.manualRule != null
                    ? zenModeConfig.manualRule.condition : null;
        }
    }

    public interface Callbacks {
        void onShowRequested(int reason);
        void onDismissRequested(int reason);
        void onStateChanged(State state);
        void onLayoutDirectionChanged(int layoutDirection);
        void onConfigurationChanged();
        void onShowVibrateHint();
        void onShowSilentHint();
        void onScreenOff();
        void onShowSafetyWarning(int flags);
    }
}
