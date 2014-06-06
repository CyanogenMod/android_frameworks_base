/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.media;

import android.Manifest;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.IRemoteControlDisplay;
import android.media.MediaMetadataEditor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.ref.WeakReference;

/**
 * The RemoteController class is used to control media playback, display and update media metadata
 * and playback status, published by applications using the {@link RemoteControlClient} class.
 * <p>
 * A RemoteController shall be registered through
 * {@link AudioManager#registerRemoteController(RemoteController)} in order for the system to send
 * media event updates to the {@link OnClientUpdateListener} listener set in the class constructor.
 * Implement the methods of the interface to receive the information published by the active
 * {@link RemoteControlClient} instances.
 * <br>By default an {@link OnClientUpdateListener} implementation will not receive bitmaps for
 * album art. Use {@link #setArtworkConfiguration(int, int)} to receive images as well.
 * <p>
 * Registration requires the {@link OnClientUpdateListener} listener to be one of the enabled
 * notification listeners (see {@link android.service.notification.NotificationListenerService}).
 */
public final class RemoteController
{
    private final static int MAX_BITMAP_DIMENSION = 512;
    private final static int TRANSPORT_UNKNOWN = 0;
    private final static String TAG = "RemoteController";
    private final static boolean DEBUG = false;
    private final static Object mGenLock = new Object();
    private final static Object mInfoLock = new Object();
    private final RcDisplay mRcd;
    private final Context mContext;
    private final AudioManager mAudioManager;
    private final int mMaxBitmapDimension;
    private MetadataEditor mMetadataEditor;

    /**
     * Synchronized on mGenLock
     */
    private int mClientGenerationIdCurrent = 0;

    /**
     * Synchronized on mInfoLock
     */
    private boolean mIsRegistered = false;
    private PendingIntent mClientPendingIntentCurrent;
    private OnClientUpdateListener mOnClientUpdateListener;
    private PlaybackInfo mLastPlaybackInfo;
    private int mArtworkWidth = -1;
    private int mArtworkHeight = -1;
    private boolean mEnabled = true;

    /**
     * Class constructor.
     * @param context the {@link Context}, must be non-null.
     * @param updateListener the listener to be called whenever new client information is available,
     *     must be non-null.
     * @throws IllegalArgumentException
     */
    public RemoteController(Context context, OnClientUpdateListener updateListener)
            throws IllegalArgumentException {
        this(context, updateListener, null);
    }

    /**
     * Class constructor.
     * @param context the {@link Context}, must be non-null.
     * @param updateListener the listener to be called whenever new client information is available,
     *     must be non-null.
     * @param looper the {@link Looper} on which to run the event loop,
     *     or null to use the current thread's looper.
     * @throws java.lang.IllegalArgumentException
     */
    public RemoteController(Context context, OnClientUpdateListener updateListener, Looper looper)
            throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException("Invalid null Context");
        }
        if (updateListener == null) {
            throw new IllegalArgumentException("Invalid null OnClientUpdateListener");
        }
        if (looper != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            Looper l = Looper.myLooper();
            if (l != null) {
                mEventHandler = new EventHandler(this, l);
            } else {
                throw new IllegalArgumentException("Calling thread not associated with a looper");
            }
        }
        mOnClientUpdateListener = updateListener;
        mContext = context;
        mRcd = new RcDisplay(this);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (ActivityManager.isLowRamDeviceStatic()) {
            mMaxBitmapDimension = MAX_BITMAP_DIMENSION;
        } else {
            final DisplayMetrics dm = context.getResources().getDisplayMetrics();
            mMaxBitmapDimension = Math.max(dm.widthPixels, dm.heightPixels);
        }
    }


    /**
     * Interface definition for the callbacks to be invoked whenever media events, metadata
     * and playback status are available.
     */
    public interface OnClientUpdateListener {
        /**
         * Called whenever all information, previously received through the other
         * methods of the listener, is no longer valid and is about to be refreshed.
         * This is typically called whenever a new {@link RemoteControlClient} has been selected
         * by the system to have its media information published.
         * @param clearing true if there is no selected RemoteControlClient and no information
         *     is available.
         */
        public void onClientChange(boolean clearing);

        /**
         * Called whenever the playback state has changed.
         * It is called when no information is known about the playback progress in the media and
         * the playback speed.
         * @param state one of the playback states authorized
         *     in {@link RemoteControlClient#setPlaybackState(int)}.
         */
        public void onClientPlaybackStateUpdate(int state);
        /**
         * Called whenever the playback state has changed, and playback position
         * and speed are known.
         * @param state one of the playback states authorized
         *     in {@link RemoteControlClient#setPlaybackState(int)}.
         * @param stateChangeTimeMs the system time at which the state change was reported,
         *     expressed in ms. Based on {@link android.os.SystemClock#elapsedRealtime()}.
         * @param currentPosMs a positive value for the current media playback position expressed
         *     in ms, a negative value if the position is temporarily unknown.
         * @param speed  a value expressed as a ratio of 1x playback: 1.0f is normal playback,
         *    2.0f is 2x, 0.5f is half-speed, -2.0f is rewind at 2x speed. 0.0f means nothing is
         *    playing (e.g. when state is {@link RemoteControlClient#PLAYSTATE_ERROR}).
         */
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed);
        /**
         * Called whenever the transport control flags have changed.
         * @param transportControlFlags one of the flags authorized
         *     in {@link RemoteControlClient#setTransportControlFlags(int)}.
         */
        public void onClientTransportControlUpdate(int transportControlFlags);
        /**
         * Called whenever new metadata is available.
         * See the {@link MediaMetadataEditor#putLong(int, long)},
         *  {@link MediaMetadataEditor#putString(int, String)},
         *  {@link MediaMetadataEditor#putBitmap(int, Bitmap)}, and
         *  {@link MediaMetadataEditor#putObject(int, Object)} methods for the various keys that
         *  can be queried.
         * @param metadataEditor the container of the new metadata.
         */
        public void onClientMetadataUpdate(MetadataEditor metadataEditor);
    };


    /**
     * @hide
     */
    public String getRemoteControlClientPackageName() {
        return mClientPendingIntentCurrent != null ?
                mClientPendingIntentCurrent.getCreatorPackage() : null;
    }

    /**
     * Return the estimated playback position of the current media track or a negative value
     * if not available.
     *
     * <p>The value returned is estimated by the current process and may not be perfect.
     * The time returned by this method is calculated from the last state change time based
     * on the current play position at that time and the last known playback speed.
     * An application may call {@link #setSynchronizationMode(int)} to apply
     * a synchronization policy that will periodically re-sync the estimated position
     * with the RemoteControlClient.</p>
     *
     * @return the current estimated playback position in milliseconds or a negative value
     *         if not available
     *
     * @see OnClientUpdateListener#onClientPlaybackStateUpdate(int, long, long, float)
     */
    public long getEstimatedMediaPosition() {
        if (mLastPlaybackInfo != null) {
            if (!RemoteControlClient.playbackPositionShouldMove(mLastPlaybackInfo.mState)) {
                return mLastPlaybackInfo.mCurrentPosMs;
            }

            // Take the current position at the time of state change and estimate.
            final long thenPos = mLastPlaybackInfo.mCurrentPosMs;
            if (thenPos < 0) {
                return -1;
            }

            final long now = SystemClock.elapsedRealtime();
            final long then = mLastPlaybackInfo.mStateChangeTimeMs;
            final long sinceThen = now - then;
            final long scaledSinceThen = (long) (sinceThen * mLastPlaybackInfo.mSpeed);
            return thenPos + scaledSinceThen;
        }
        return -1;
    }


    /**
     * Send a simulated key event for a media button to be received by the current client.
     * To simulate a key press, you must first send a KeyEvent built with
     * a {@link KeyEvent#ACTION_DOWN} action, then another event with the {@link KeyEvent#ACTION_UP}
     * action.
     * <p>The key event will be sent to the registered receiver
     * (see {@link AudioManager#registerMediaButtonEventReceiver(PendingIntent)}) whose associated
     * {@link RemoteControlClient}'s metadata and playback state is published (there may be
     * none under some circumstances).
     * @param keyEvent a {@link KeyEvent} instance whose key code is one of
     *     {@link KeyEvent#KEYCODE_MUTE},
     *     {@link KeyEvent#KEYCODE_HEADSETHOOK},
     *     {@link KeyEvent#KEYCODE_MEDIA_PLAY},
     *     {@link KeyEvent#KEYCODE_MEDIA_PAUSE},
     *     {@link KeyEvent#KEYCODE_MEDIA_PLAY_PAUSE},
     *     {@link KeyEvent#KEYCODE_MEDIA_STOP},
     *     {@link KeyEvent#KEYCODE_MEDIA_NEXT},
     *     {@link KeyEvent#KEYCODE_MEDIA_PREVIOUS},
     *     {@link KeyEvent#KEYCODE_MEDIA_REWIND},
     *     {@link KeyEvent#KEYCODE_MEDIA_RECORD},
     *     {@link KeyEvent#KEYCODE_MEDIA_FAST_FORWARD},
     *     {@link KeyEvent#KEYCODE_MEDIA_CLOSE},
     *     {@link KeyEvent#KEYCODE_MEDIA_EJECT},
     *     or {@link KeyEvent#KEYCODE_MEDIA_AUDIO_TRACK}.
     * @return true if the event was successfully sent, false otherwise.
     * @throws IllegalArgumentException
     */
    public boolean sendMediaKeyEvent(KeyEvent keyEvent) throws IllegalArgumentException {
        if (!MediaFocusControl.isMediaKeyCode(keyEvent.getKeyCode())) {
            throw new IllegalArgumentException("not a media key event");
        }
        final PendingIntent pi;
        synchronized(mInfoLock) {
            if (!mIsRegistered) {
                Log.e(TAG, "Cannot use sendMediaKeyEvent() from an unregistered RemoteController");
                return false;
            }
            if (!mEnabled) {
                Log.e(TAG, "Cannot use sendMediaKeyEvent() from a disabled RemoteController");
                return false;
            }
            pi = mClientPendingIntentCurrent;
        }
        if (pi != null) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
            try {
                pi.send(mContext, 0, intent);
            } catch (CanceledException e) {
                Log.e(TAG, "Error sending intent for media button down: ", e);
                return false;
            }
        } else {
            Log.i(TAG, "No-op when sending key click, no receiver right now");
            return false;
        }
        return true;
    }


    /**
     * Sets the new playback position.
     * This method can only be called on a registered RemoteController.
     * @param timeMs a 0 or positive value for the new playback position, expressed in ms.
     * @return true if the command to set the playback position was successfully sent.
     * @throws IllegalArgumentException
     */
    public boolean seekTo(long timeMs) throws IllegalArgumentException {
        if (!mEnabled) {
            Log.e(TAG, "Cannot use seekTo() from a disabled RemoteController");
            return false;
        }
        if (timeMs < 0) {
            throw new IllegalArgumentException("illegal negative time value");
        }
        final int genId;
        synchronized (mGenLock) {
            genId = mClientGenerationIdCurrent;
        }
        mAudioManager.setRemoteControlClientPlaybackPosition(genId, timeMs);
        return true;
    }


    /**
     * @hide
     * @param wantBitmap
     * @param width
     * @param height
     * @return true if successful
     * @throws IllegalArgumentException
     */
    public boolean setArtworkConfiguration(boolean wantBitmap, int width, int height)
            throws IllegalArgumentException {
        synchronized (mInfoLock) {
            if (wantBitmap) {
                if ((width > 0) && (height > 0)) {
                    if (width > mMaxBitmapDimension) { width = mMaxBitmapDimension; }
                    if (height > mMaxBitmapDimension) { height = mMaxBitmapDimension; }
                    mArtworkWidth = width;
                    mArtworkHeight = height;
                } else {
                    throw new IllegalArgumentException("Invalid dimensions");
                }
            } else {
                mArtworkWidth = -1;
                mArtworkHeight = -1;
            }
            if (mIsRegistered) {
                mAudioManager.remoteControlDisplayUsesBitmapSize(mRcd,
                        mArtworkWidth, mArtworkHeight);
            } // else new values have been stored, and will be read by AudioManager with
              //    RemoteController.getArtworkSize() when AudioManager.registerRemoteController()
              //    is called.
        }
        return true;
    }

    /**
     * Set the maximum artwork image dimensions to be received in the metadata.
     * No bitmaps will be received unless this has been specified.
     * @param width the maximum width in pixels
     * @param height  the maximum height in pixels
     * @return true if the artwork dimension was successfully set.
     * @throws IllegalArgumentException
     */
    public boolean setArtworkConfiguration(int width, int height) throws IllegalArgumentException {
        return setArtworkConfiguration(true, width, height);
    }

    /**
     * Prevents this RemoteController from receiving artwork images.
     * @return true if receiving artwork images was successfully disabled.
     */
    public boolean clearArtworkConfiguration() {
        return setArtworkConfiguration(false, -1, -1);
    }


    /**
     * Default playback position synchronization mode where the RemoteControlClient is not
     * asked regularly for its playback position to see if it has drifted from the estimated
     * position.
     */
    public static final int POSITION_SYNCHRONIZATION_NONE = 0;

    /**
     * The playback position synchronization mode where the RemoteControlClient instances which
     * expose their playback position to the framework, will be regularly polled to check
     * whether any drift has been noticed between their estimated position and the one they report.
     * Note that this mode should only ever be used when needing to display very accurate playback
     * position, as regularly polling a RemoteControlClient for its position may have an impact
     * on battery life (if applicable) when this query will trigger network transactions in the
     * case of remote playback.
     */
    public static final int POSITION_SYNCHRONIZATION_CHECK = 1;

    /**
     * Set the playback position synchronization mode.
     * Must be called on a registered RemoteController.
     * @param sync {@link #POSITION_SYNCHRONIZATION_NONE} or {@link #POSITION_SYNCHRONIZATION_CHECK}
     * @return true if the synchronization mode was successfully set.
     * @throws IllegalArgumentException
     */
    public boolean setSynchronizationMode(int sync) throws IllegalArgumentException {
        if ((sync != POSITION_SYNCHRONIZATION_NONE) && (sync != POSITION_SYNCHRONIZATION_CHECK)) {
            throw new IllegalArgumentException("Unknown synchronization mode " + sync);
        }
        if (!mIsRegistered) {
            Log.e(TAG, "Cannot set synchronization mode on an unregistered RemoteController");
            return false;
        }
        mAudioManager.remoteControlDisplayWantsPlaybackPositionSync(mRcd,
                POSITION_SYNCHRONIZATION_CHECK == sync);
        return true;
    }


    /**
     * Creates a {@link MetadataEditor} for updating metadata values of the editable keys of
     * the current {@link RemoteControlClient}.
     * This method can only be called on a registered RemoteController.
     * @return a new MetadataEditor instance.
     */
    public MetadataEditor editMetadata() {
        MetadataEditor editor = new MetadataEditor();
        editor.mEditorMetadata = new Bundle();
        editor.mEditorArtwork = null;
        editor.mMetadataChanged = true;
        editor.mArtworkChanged = true;
        editor.mEditableKeys = 0;
        return editor;
    }


    /**
     * A class to read the metadata published by a {@link RemoteControlClient}, or send a
     * {@link RemoteControlClient} new values for keys that can be edited.
     */
    public class MetadataEditor extends MediaMetadataEditor {
        /**
         * @hide
         */
        protected MetadataEditor() { }

        /**
         * @hide
         */
        protected MetadataEditor(Bundle metadata, long editableKeys) {
            mEditorMetadata = metadata;
            mEditableKeys = editableKeys;

            mEditorArtwork = (Bitmap) metadata.getParcelable(
                    String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK));
            if (mEditorArtwork != null) {
                cleanupBitmapFromBundle(MediaMetadataEditor.BITMAP_KEY_ARTWORK);
            }

            mMetadataChanged = true;
            mArtworkChanged = true;
            mApplied = false;
        }

        private void cleanupBitmapFromBundle(int key) {
            if (METADATA_KEYS_TYPE.get(key, METADATA_TYPE_INVALID) == METADATA_TYPE_BITMAP) {
                mEditorMetadata.remove(String.valueOf(key));
            }
        }

        /**
         * Applies all of the metadata changes that have been set since the MediaMetadataEditor
         * instance was created with {@link RemoteController#editMetadata()}
         * or since {@link #clear()} was called.
         */
        public synchronized void apply() {
            // "applying" a metadata bundle in RemoteController is only for sending edited
            // key values back to the RemoteControlClient, so here we only care about the only
            // editable key we support: RATING_KEY_BY_USER
            if (!mMetadataChanged) {
                return;
            }
            final int genId;
            synchronized(mGenLock) {
                genId = mClientGenerationIdCurrent;
            }
            synchronized(mInfoLock) {
                if (mEditorMetadata.containsKey(
                        String.valueOf(MediaMetadataEditor.RATING_KEY_BY_USER))) {
                    Rating rating = (Rating) getObject(
                            MediaMetadataEditor.RATING_KEY_BY_USER, null);
                    mAudioManager.updateRemoteControlClientMetadata(genId,
                          MediaMetadataEditor.RATING_KEY_BY_USER,
                          rating);
                } else {
                    Log.e(TAG, "no metadata to apply");
                }
                // NOT setting mApplied to true as this type of MetadataEditor will be applied
                // multiple times, whenever the user of a RemoteController needs to change the
                // metadata (e.g. user changes the rating of a song more than once during playback)
                mApplied = false;
            }
        }

    }


    //==================================================
    // Implementation of IRemoteControlDisplay interface
    private static class RcDisplay extends IRemoteControlDisplay.Stub {
        private final WeakReference<RemoteController> mController;

        RcDisplay(RemoteController rc) {
            mController = new WeakReference<RemoteController>(rc);
        }

        public void setCurrentClientId(int genId, PendingIntent clientMediaIntent,
                boolean clearing) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            boolean isNew = false;
            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    rc.mClientGenerationIdCurrent = genId;
                    isNew = true;
                }
            }
            if (clientMediaIntent != null) {
                sendMsg(rc.mEventHandler, MSG_NEW_PENDING_INTENT, SENDMSG_REPLACE,
                        genId /*arg1*/, 0, clientMediaIntent /*obj*/, 0 /*delay*/);
            }
            if (isNew || clearing) {
                sendMsg(rc.mEventHandler, MSG_CLIENT_CHANGE, SENDMSG_REPLACE,
                        genId /*arg1*/, clearing ? 1 : 0, null /*obj*/, 0 /*delay*/);
            }
        }

        public void setEnabled(boolean enabled) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            sendMsg(rc.mEventHandler, MSG_DISPLAY_ENABLE, SENDMSG_REPLACE,
                    enabled ? 1 : 0 /*arg1*/, 0, null /*obj*/, 0 /*delay*/);
        }

        public void setPlaybackState(int genId, int state,
                long stateChangeTimeMs, long currentPosMs, float speed) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "> new playback state: genId="+genId
                        + " state="+ state
                        + " changeTime="+ stateChangeTimeMs
                        + " pos=" + currentPosMs
                        + "ms speed=" + speed);
            }

            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    return;
                }
            }
            final PlaybackInfo playbackInfo =
                    new PlaybackInfo(state, stateChangeTimeMs, currentPosMs, speed);
            sendMsg(rc.mEventHandler, MSG_NEW_PLAYBACK_INFO, SENDMSG_REPLACE,
                    genId /*arg1*/, 0, playbackInfo /*obj*/, 0 /*delay*/);

        }

        public void setTransportControlInfo(int genId, int transportControlFlags,
                int posCapabilities) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    return;
                }
            }
            sendMsg(rc.mEventHandler, MSG_NEW_TRANSPORT_INFO, SENDMSG_REPLACE,
                    genId /*arg1*/, transportControlFlags /*arg2*/,
                    null /*obj*/, 0 /*delay*/);
        }

        public void setMetadata(int genId, Bundle metadata) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            if (DEBUG) { Log.e(TAG, "setMetadata("+genId+")"); }
            if (metadata == null) {
                return;
            }
            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    return;
                }
            }
            sendMsg(rc.mEventHandler, MSG_NEW_METADATA, SENDMSG_QUEUE,
                    genId /*arg1*/, 0 /*arg2*/,
                    metadata /*obj*/, 0 /*delay*/);
        }

        public void setArtwork(int genId, Bitmap artwork) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            if (DEBUG) { Log.v(TAG, "setArtwork("+genId+")"); }
            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    return;
                }
            }
            Bundle metadata = new Bundle(1);
            metadata.putParcelable(String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK), artwork);
            sendMsg(rc.mEventHandler, MSG_NEW_METADATA, SENDMSG_QUEUE,
                    genId /*arg1*/, 0 /*arg2*/,
                    metadata /*obj*/, 0 /*delay*/);
        }

        public void setAllMetadata(int genId, Bundle metadata, Bitmap artwork) {
            final RemoteController rc = mController.get();
            if (rc == null) {
                return;
            }
            if (DEBUG) { Log.e(TAG, "setAllMetadata("+genId+")"); }
            if ((metadata == null) && (artwork == null)) {
                return;
            }
            synchronized(mGenLock) {
                if (rc.mClientGenerationIdCurrent != genId) {
                    return;
                }
            }
            if (metadata == null) {
                metadata = new Bundle(1);
            }
            if (artwork != null) {
                metadata.putParcelable(String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK),
                        artwork);
            }
            sendMsg(rc.mEventHandler, MSG_NEW_METADATA, SENDMSG_QUEUE,
                    genId /*arg1*/, 0 /*arg2*/,
                    metadata /*obj*/, 0 /*delay*/);
        }
    }

    //==================================================
    // Event handling
    private final EventHandler mEventHandler;
    private final static int MSG_NEW_PENDING_INTENT = 0;
    private final static int MSG_NEW_PLAYBACK_INFO =  1;
    private final static int MSG_NEW_TRANSPORT_INFO = 2;
    private final static int MSG_NEW_METADATA       = 3; // msg always has non-null obj parameter
    private final static int MSG_CLIENT_CHANGE      = 4;
    private final static int MSG_DISPLAY_ENABLE     = 5;

    private class EventHandler extends Handler {

        public EventHandler(RemoteController rc, Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_NEW_PENDING_INTENT:
                    onNewPendingIntent(msg.arg1, (PendingIntent) msg.obj);
                    break;
                case MSG_NEW_PLAYBACK_INFO:
                    onNewPlaybackInfo(msg.arg1, (PlaybackInfo) msg.obj);
                    break;
                case MSG_NEW_TRANSPORT_INFO:
                    onNewTransportInfo(msg.arg1, msg.arg2);
                    break;
                case MSG_NEW_METADATA:
                    onNewMetadata(msg.arg1, (Bundle)msg.obj);
                    break;
                case MSG_CLIENT_CHANGE:
                    onClientChange(msg.arg1, msg.arg2 == 1);
                    break;
                case MSG_DISPLAY_ENABLE:
                    onDisplayEnable(msg.arg1 == 1);
                    break;
                default:
                    Log.e(TAG, "unknown event " + msg.what);
            }
        }
    }

    /** If the msg is already queued, replace it with this one. */
    private static final int SENDMSG_REPLACE = 0;
    /** If the msg is already queued, ignore this one and leave the old. */
    private static final int SENDMSG_NOOP = 1;
    /** If the msg is already queued, queue this one and leave the old. */
    private static final int SENDMSG_QUEUE = 2;

    private static void sendMsg(Handler handler, int msg, int existingMsgPolicy,
            int arg1, int arg2, Object obj, int delayMs) {
        if (handler == null) {
            Log.e(TAG, "null event handler, will not deliver message " + msg);
            return;
        }
        if (existingMsgPolicy == SENDMSG_REPLACE) {
            handler.removeMessages(msg);
        } else if (existingMsgPolicy == SENDMSG_NOOP && handler.hasMessages(msg)) {
            return;
        }
        handler.sendMessageDelayed(handler.obtainMessage(msg, arg1, arg2, obj), delayMs);
    }

    private void onNewPendingIntent(int genId, PendingIntent pi) {
        synchronized(mGenLock) {
            if (mClientGenerationIdCurrent != genId) {
                return;
            }
        }
        synchronized(mInfoLock) {
            mClientPendingIntentCurrent = pi;
        }
    }

    private void onNewPlaybackInfo(int genId, PlaybackInfo pi) {
        synchronized(mGenLock) {
            if (mClientGenerationIdCurrent != genId) {
                return;
            }
        }
        final OnClientUpdateListener l;
        synchronized(mInfoLock) {
            l = this.mOnClientUpdateListener;
            mLastPlaybackInfo = pi;
        }
        if (l != null) {
            if (pi.mCurrentPosMs == RemoteControlClient.PLAYBACK_POSITION_ALWAYS_UNKNOWN) {
                l.onClientPlaybackStateUpdate(pi.mState);
            } else {
                l.onClientPlaybackStateUpdate(pi.mState, pi.mStateChangeTimeMs, pi.mCurrentPosMs,
                        pi.mSpeed);
            }
        }
    }

    private void onNewTransportInfo(int genId, int transportControlFlags) {
        synchronized(mGenLock) {
            if (mClientGenerationIdCurrent != genId) {
                return;
            }
        }
        final OnClientUpdateListener l;
        synchronized(mInfoLock) {
            l = mOnClientUpdateListener;
        }
        if (l != null) {
            l.onClientTransportControlUpdate(transportControlFlags);
        }
    }

    /**
     * @param genId
     * @param metadata guaranteed to be always non-null
     */
    private void onNewMetadata(int genId, Bundle metadata) {
        synchronized(mGenLock) {
            if (mClientGenerationIdCurrent != genId) {
                return;
            }
        }
        final OnClientUpdateListener l;
        final MetadataEditor metadataEditor;
        // prepare the received Bundle to be used inside a MetadataEditor
        final long editableKeys = metadata.getLong(
                String.valueOf(MediaMetadataEditor.KEY_EDITABLE_MASK), 0);
        if (editableKeys != 0) {
            metadata.remove(String.valueOf(MediaMetadataEditor.KEY_EDITABLE_MASK));
        }
        synchronized(mInfoLock) {
            l = mOnClientUpdateListener;
            if ((mMetadataEditor != null) && (mMetadataEditor.mEditorMetadata != null)) {
                if (mMetadataEditor.mEditorMetadata != metadata) {
                    // existing metadata, merge existing and new
                    mMetadataEditor.mEditorMetadata.putAll(metadata);
                }

                mMetadataEditor.putBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK,
                        (Bitmap)metadata.getParcelable(
                                String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK)));
                mMetadataEditor.cleanupBitmapFromBundle(MediaMetadataEditor.BITMAP_KEY_ARTWORK);
            } else {
                mMetadataEditor = new MetadataEditor(metadata, editableKeys);
            }
            metadataEditor = mMetadataEditor;
        }
        if (l != null) {
            l.onClientMetadataUpdate(metadataEditor);
        }
    }

    private void onClientChange(int genId, boolean clearing) {
        synchronized(mGenLock) {
            if (mClientGenerationIdCurrent != genId) {
                return;
            }
        }
        final OnClientUpdateListener l;
        synchronized(mInfoLock) {
            l = mOnClientUpdateListener;
            mMetadataEditor = null;
        }
        if (l != null) {
            l.onClientChange(clearing);
        }
    }

    private void onDisplayEnable(boolean enabled) {
        final OnClientUpdateListener l;
        synchronized(mInfoLock) {
            mEnabled = enabled;
            l = this.mOnClientUpdateListener;
        }
        if (!enabled) {
            // when disabling, reset all info sent to the user
            final int genId;
            synchronized (mGenLock) {
                genId = mClientGenerationIdCurrent;
            }
            // send "stopped" state, happened "now", playback position is 0, speed 0.0f
            final PlaybackInfo pi = new PlaybackInfo(RemoteControlClient.PLAYSTATE_STOPPED,
                    SystemClock.elapsedRealtime() /*stateChangeTimeMs*/,
                    0 /*currentPosMs*/, 0.0f /*speed*/);
            sendMsg(mEventHandler, MSG_NEW_PLAYBACK_INFO, SENDMSG_REPLACE,
                    genId /*arg1*/, 0 /*arg2, ignored*/, pi /*obj*/, 0 /*delay*/);
            // send "blank" transport control info: no controls are supported
            sendMsg(mEventHandler, MSG_NEW_TRANSPORT_INFO, SENDMSG_REPLACE,
                    genId /*arg1*/, 0 /*arg2, no flags*/,
                    null /*obj, ignored*/, 0 /*delay*/);
            // send dummy metadata with empty string for title and artist, duration of 0
            Bundle metadata = new Bundle(3);
            metadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_TITLE), "");
            metadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_ARTIST), "");
            metadata.putLong(String.valueOf(MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            sendMsg(mEventHandler, MSG_NEW_METADATA, SENDMSG_QUEUE,
                    genId /*arg1*/, 0 /*arg2, ignored*/, metadata /*obj*/, 0 /*delay*/);
        }
    }

    //==================================================
    private static class PlaybackInfo {
        int mState;
        long mStateChangeTimeMs;
        long mCurrentPosMs;
        float mSpeed;

        PlaybackInfo(int state, long stateChangeTimeMs, long currentPosMs, float speed) {
            mState = state;
            mStateChangeTimeMs = stateChangeTimeMs;
            mCurrentPosMs = currentPosMs;
            mSpeed = speed;
        }
    }

    /**
     * @hide
     * Used by AudioManager to mark this instance as registered.
     * @param registered
     */
    void setIsRegistered(boolean registered) {
        synchronized (mInfoLock) {
            mIsRegistered = registered;
        }
    }

    /**
     * @hide
     * Used by AudioManager to access binder to be registered/unregistered inside MediaFocusControl
     * @return
     */
    RcDisplay getRcDisplay() {
        return mRcd;
    }

    /**
     * @hide
     * Used by AudioManager to read the current artwork dimension
     * @return array containing width (index 0) and height (index 1) of currently set artwork size
     */
    int[] getArtworkSize() {
        synchronized (mInfoLock) {
            int[] size = { mArtworkWidth, mArtworkHeight };
            return size;
        }
    }

    /**
     * @hide
     * Used by AudioManager to access user listener receiving the client update notifications
     * @return
     */
    OnClientUpdateListener getUpdateListener() {
        return mOnClientUpdateListener;
    }
}
