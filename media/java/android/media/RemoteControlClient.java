/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import java.lang.IllegalArgumentException;

/**
 * RemoteControlClient enables exposing information meant to be consumed by remote controls
 * capable of displaying metadata, artwork and media transport control buttons.
 *
 * <p>A remote control client object is associated with a media button event receiver. This
 * event receiver must have been previously registered with
 * {@link AudioManager#registerMediaButtonEventReceiver(ComponentName)} before the
 * RemoteControlClient can be registered through
 * {@link AudioManager#registerRemoteControlClient(RemoteControlClient)}.
 *
 * <p>Here is an example of creating a RemoteControlClient instance after registering a media
 * button event receiver:
 * <pre>ComponentName myEventReceiver = new ComponentName(getPackageName(), MyRemoteControlEventReceiver.class.getName());
 * AudioManager myAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
 * myAudioManager.registerMediaButtonEventReceiver(myEventReceiver);
 * // build the PendingIntent for the remote control client
 * Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
 * mediaButtonIntent.setComponent(myEventReceiver);
 * PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);
 * // create and register the remote control client
 * RemoteControlClient myRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
 * myAudioManager.registerRemoteControlClient(myRemoteControlClient);</pre>
 */
public class RemoteControlClient
{
    private final static String TAG = "RemoteControlClient";

    /**
     * Playback state of a RemoteControlClient which is stopped.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_STOPPED            = 1;
    /**
     * Playback state of a RemoteControlClient which is paused.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_PAUSED             = 2;
    /**
     * Playback state of a RemoteControlClient which is playing media.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_PLAYING            = 3;
    /**
     * Playback state of a RemoteControlClient which is fast forwarding in the media
     *    it is currently playing.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_FAST_FORWARDING    = 4;
    /**
     * Playback state of a RemoteControlClient which is fast rewinding in the media
     *    it is currently playing.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_REWINDING          = 5;
    /**
     * Playback state of a RemoteControlClient which is skipping to the next
     *    logical chapter (such as a song in a playlist) in the media it is currently playing.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_SKIPPING_FORWARDS  = 6;
    /**
     * Playback state of a RemoteControlClient which is skipping back to the previous
     *    logical chapter (such as a song in a playlist) in the media it is currently playing.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_SKIPPING_BACKWARDS = 7;
    /**
     * Playback state of a RemoteControlClient which is buffering data to play before it can
     *    start or resume playback.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_BUFFERING          = 8;
    /**
     * Playback state of a RemoteControlClient which cannot perform any playback related
     *    operation because of an internal error. Examples of such situations are no network
     *    connectivity when attempting to stream data from a server, or expired user credentials
     *    when trying to play subscription-based content.
     *
     * @see #setPlaybackState(int)
     */
    public final static int PLAYSTATE_ERROR              = 9;
    /**
     * @hide
     * The value of a playback state when none has been declared.
     * Intentionally hidden as an application shouldn't set such a playback state value.
     */
    public final static int PLAYSTATE_NONE               = 0;

    /**
     * @hide
     * The default playback type, "local", indicating the presentation of the media is happening on
     * the same device (e.g. a phone, a tablet) as where it is controlled from.
     */
    public final static int PLAYBACK_TYPE_LOCAL = 0;
    /**
     * @hide
     * A playback type indicating the presentation of the media is happening on
     * a different device (i.e. the remote device) than where it is controlled from.
     */
    public final static int PLAYBACK_TYPE_REMOTE = 1;
    private final static int PLAYBACK_TYPE_MIN = PLAYBACK_TYPE_LOCAL;
    private final static int PLAYBACK_TYPE_MAX = PLAYBACK_TYPE_REMOTE;
    /**
     * @hide
     * Playback information indicating the playback volume is fixed, i.e. it cannot be controlled
     * from this object. An example of fixed playback volume is a remote player, playing over HDMI
     * where the user prefer to control the volume on the HDMI sink, rather than attenuate at the
     * source.
     * @see #PLAYBACKINFO_VOLUME_HANDLING.
     */
    public final static int PLAYBACK_VOLUME_FIXED = 0;
    /**
     * @hide
     * Playback information indicating the playback volume is variable and can be controlled from
     * this object.
     * @see #PLAYBACKINFO_VOLUME_HANDLING.
     */
    public final static int PLAYBACK_VOLUME_VARIABLE = 1;
    /**
     * @hide (to be un-hidden)
     * The playback information value indicating the value of a given information type is invalid.
     * @see #PLAYBACKINFO_VOLUME_HANDLING.
     */
    public final static int PLAYBACKINFO_INVALID_VALUE = Integer.MIN_VALUE;

    //==========================================
    // Public keys for playback information
    /**
     * @hide
     * Playback information that defines the type of playback associated with this
     * RemoteControlClient. See {@link #PLAYBACK_TYPE_LOCAL} and {@link #PLAYBACK_TYPE_REMOTE}.
     */
    public final static int PLAYBACKINFO_PLAYBACK_TYPE = 1;
    /**
     * @hide
     * Playback information that defines at what volume the playback associated with this
     * RemoteControlClient is performed. This information is only used when the playback type is not
     * local (see {@link #PLAYBACKINFO_PLAYBACK_TYPE}).
     */
    public final static int PLAYBACKINFO_VOLUME = 2;
    /**
     * @hide
     * Playback information that defines the maximum volume volume value that is supported
     * by the playback associated with this RemoteControlClient. This information is only used
     * when the playback type is not local (see {@link #PLAYBACKINFO_PLAYBACK_TYPE}).
     */
    public final static int PLAYBACKINFO_VOLUME_MAX = 3;
    /**
     * @hide
     * Playback information that defines how volume is handled for the presentation of the media.
     * @see #PLAYBACK_VOLUME_FIXED
     * @see #PLAYBACK_VOLUME_VARIABLE
     */
    public final static int PLAYBACKINFO_VOLUME_HANDLING = 4;
    /**
     * @hide
     * Playback information that defines over what stream type the media is presented.
     */
    public final static int PLAYBACKINFO_USES_STREAM = 5;

    //==========================================
    // Private keys for playback information
    /**
     * @hide
     * Used internally to relay playback state (set by the application with
     * {@link #setPlaybackState(int)}) to AudioService
     */
    public final static int PLAYBACKINFO_PLAYSTATE = 255;


    /**
     * Flag indicating a RemoteControlClient makes use of the "previous" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_PREVIOUS
     */
    public final static int FLAG_KEY_MEDIA_PREVIOUS = 1 << 0;
    /**
     * Flag indicating a RemoteControlClient makes use of the "rewind" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_REWIND
     */
    public final static int FLAG_KEY_MEDIA_REWIND = 1 << 1;
    /**
     * Flag indicating a RemoteControlClient makes use of the "play" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_PLAY
     */
    public final static int FLAG_KEY_MEDIA_PLAY = 1 << 2;
    /**
     * Flag indicating a RemoteControlClient makes use of the "play/pause" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_PLAY_PAUSE
     */
    public final static int FLAG_KEY_MEDIA_PLAY_PAUSE = 1 << 3;
    /**
     * Flag indicating a RemoteControlClient makes use of the "pause" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_PAUSE
     */
    public final static int FLAG_KEY_MEDIA_PAUSE = 1 << 4;
    /**
     * Flag indicating a RemoteControlClient makes use of the "stop" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_STOP
     */
    public final static int FLAG_KEY_MEDIA_STOP = 1 << 5;
    /**
     * Flag indicating a RemoteControlClient makes use of the "fast forward" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_FAST_FORWARD
     */
    public final static int FLAG_KEY_MEDIA_FAST_FORWARD = 1 << 6;
    /**
     * Flag indicating a RemoteControlClient makes use of the "next" media key.
     *
     * @see #setTransportControlFlags(int)
     * @see android.view.KeyEvent#KEYCODE_MEDIA_NEXT
     */
    public final static int FLAG_KEY_MEDIA_NEXT = 1 << 7;

    /**
     * @hide
     * The flags for when no media keys are declared supported.
     * Intentionally hidden as an application shouldn't set the transport control flags
     *     to this value.
     */
    public final static int FLAGS_KEY_MEDIA_NONE = 0;

    /**
     * @hide
     * Flag used to signal some type of metadata exposed by the RemoteControlClient is requested.
     */
    public final static int FLAG_INFORMATION_REQUEST_METADATA = 1 << 0;
    /**
     * @hide
     * Flag used to signal that the transport control buttons supported by the
     *     RemoteControlClient are requested.
     * This can for instance happen when playback is at the end of a playlist, and the "next"
     * operation is not supported anymore.
     */
    public final static int FLAG_INFORMATION_REQUEST_KEY_MEDIA = 1 << 1;
    /**
     * @hide
     * Flag used to signal that the playback state of the RemoteControlClient is requested.
     */
    public final static int FLAG_INFORMATION_REQUEST_PLAYSTATE = 1 << 2;
    /**
     * @hide
     * Flag used to signal that the album art for the RemoteControlClient is requested.
     */
    public final static int FLAG_INFORMATION_REQUEST_ALBUM_ART = 1 << 3;

    /**
     * Class constructor.
     * @param mediaButtonIntent The intent that will be sent for the media button events sent
     *     by remote controls.
     *     This intent needs to have been constructed with the {@link Intent#ACTION_MEDIA_BUTTON}
     *     action, and have a component that will handle the intent (set with
     *     {@link Intent#setComponent(ComponentName)}) registered with
     *     {@link AudioManager#registerMediaButtonEventReceiver(ComponentName)}
     *     before this new RemoteControlClient can itself be registered with
     *     {@link AudioManager#registerRemoteControlClient(RemoteControlClient)}.
     * @see AudioManager#registerMediaButtonEventReceiver(ComponentName)
     * @see AudioManager#registerRemoteControlClient(RemoteControlClient)
     */
    public RemoteControlClient(PendingIntent mediaButtonIntent) {
        mRcMediaIntent = mediaButtonIntent;

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
            Log.e(TAG, "RemoteControlClient() couldn't find main application thread");
        }
    }

    /**
     * Class constructor for a remote control client whose internal event handling
     * happens on a user-provided Looper.
     * @param mediaButtonIntent The intent that will be sent for the media button events sent
     *     by remote controls.
     *     This intent needs to have been constructed with the {@link Intent#ACTION_MEDIA_BUTTON}
     *     action, and have a component that will handle the intent (set with
     *     {@link Intent#setComponent(ComponentName)}) registered with
     *     {@link AudioManager#registerMediaButtonEventReceiver(ComponentName)}
     *     before this new RemoteControlClient can itself be registered with
     *     {@link AudioManager#registerRemoteControlClient(RemoteControlClient)}.
     * @param looper The Looper running the event loop.
     * @see AudioManager#registerMediaButtonEventReceiver(ComponentName)
     * @see AudioManager#registerRemoteControlClient(RemoteControlClient)
     */
    public RemoteControlClient(PendingIntent mediaButtonIntent, Looper looper) {
        mRcMediaIntent = mediaButtonIntent;

        mEventHandler = new EventHandler(this, looper);
    }

    private static final int[] METADATA_KEYS_TYPE_STRING = {
        MediaMetadataRetriever.METADATA_KEY_ALBUM,
        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
        MediaMetadataRetriever.METADATA_KEY_TITLE,
        MediaMetadataRetriever.METADATA_KEY_ARTIST,
        MediaMetadataRetriever.METADATA_KEY_AUTHOR,
        MediaMetadataRetriever.METADATA_KEY_COMPILATION,
        MediaMetadataRetriever.METADATA_KEY_COMPOSER,
        MediaMetadataRetriever.METADATA_KEY_DATE,
        MediaMetadataRetriever.METADATA_KEY_GENRE,
        MediaMetadataRetriever.METADATA_KEY_TITLE,
        MediaMetadataRetriever.METADATA_KEY_WRITER };
    private static final int[] METADATA_KEYS_TYPE_LONG = {
        MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
        MediaMetadataRetriever.METADATA_KEY_DURATION };

    /**
     * Class used to modify metadata in a {@link RemoteControlClient} object.
     * Use {@link RemoteControlClient#editMetadata(boolean)} to create an instance of an editor,
     * on which you set the metadata for the RemoteControlClient instance. Once all the information
     * has been set, use {@link #apply()} to make it the new metadata that should be displayed
     * for the associated client. Once the metadata has been "applied", you cannot reuse this
     * instance of the MetadataEditor.
     */
    public class MetadataEditor {
        /**
         * @hide
         */
        protected boolean mMetadataChanged;
        /**
         * @hide
         */
        protected boolean mArtworkChanged;
        /**
         * @hide
         */
        protected Bitmap mEditorArtwork;
        /**
         * @hide
         */
        protected Bundle mEditorMetadata;
        private boolean mApplied = false;

        // only use RemoteControlClient.editMetadata() to get a MetadataEditor instance
        private MetadataEditor() { }
        /**
         * @hide
         */
        public Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException();
        }

        /**
         * The metadata key for the content artwork / album art.
         */
        public final static int BITMAP_KEY_ARTWORK = 100;
        /**
         * @hide
         * TODO(jmtrivi) have lockscreen and music move to the new key name
         */
        public final static int METADATA_KEY_ARTWORK = BITMAP_KEY_ARTWORK;

        /**
         * Adds textual information to be displayed.
         * Note that none of the information added after {@link #apply()} has been called,
         * will be displayed.
         * @param key The identifier of a the metadata field to set. Valid values are
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_ALBUM},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_ALBUMARTIST},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_TITLE},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_ARTIST},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_AUTHOR},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_COMPILATION},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_COMPOSER},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_DATE},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_GENRE},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_TITLE},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_WRITER}.
         * @param value The text for the given key, or {@code null} to signify there is no valid
         *      information for the field.
         * @return Returns a reference to the same MetadataEditor object, so you can chain put
         *      calls together.
         */
        public synchronized MetadataEditor putString(int key, String value)
                throws IllegalArgumentException {
            if (mApplied) {
                Log.e(TAG, "Can't edit a previously applied MetadataEditor");
                return this;
            }
            if (!validTypeForKey(key, METADATA_KEYS_TYPE_STRING)) {
                throw(new IllegalArgumentException("Invalid type 'String' for key "+ key));
            }
            mEditorMetadata.putString(String.valueOf(key), value);
            mMetadataChanged = true;
            return this;
        }

        /**
         * Adds numerical information to be displayed.
         * Note that none of the information added after {@link #apply()} has been called,
         * will be displayed.
         * @param key the identifier of a the metadata field to set. Valid values are
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_CD_TRACK_NUMBER},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_DISC_NUMBER},
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_DURATION} (with a value
         *      expressed in milliseconds),
         *      {@link android.media.MediaMetadataRetriever#METADATA_KEY_YEAR}.
         * @param value The long value for the given key
         * @return Returns a reference to the same MetadataEditor object, so you can chain put
         *      calls together.
         * @throws IllegalArgumentException
         */
        public synchronized MetadataEditor putLong(int key, long value)
                throws IllegalArgumentException {
            if (mApplied) {
                Log.e(TAG, "Can't edit a previously applied MetadataEditor");
                return this;
            }
            if (!validTypeForKey(key, METADATA_KEYS_TYPE_LONG)) {
                throw(new IllegalArgumentException("Invalid type 'long' for key "+ key));
            }
            mEditorMetadata.putLong(String.valueOf(key), value);
            mMetadataChanged = true;
            return this;
        }

        /**
         * Sets the album / artwork picture to be displayed on the remote control.
         * @param key the identifier of the bitmap to set. The only valid value is
         *      {@link #BITMAP_KEY_ARTWORK}
         * @param bitmap The bitmap for the artwork, or null if there isn't any.
         * @return Returns a reference to the same MetadataEditor object, so you can chain put
         *      calls together.
         * @throws IllegalArgumentException
         * @see android.graphics.Bitmap
         */
        public synchronized MetadataEditor putBitmap(int key, Bitmap bitmap)
                throws IllegalArgumentException {
            if (mApplied) {
                Log.e(TAG, "Can't edit a previously applied MetadataEditor");
                return this;
            }
            if (key != BITMAP_KEY_ARTWORK) {
                throw(new IllegalArgumentException("Invalid type 'Bitmap' for key "+ key));
            }
            if ((mArtworkExpectedWidth > 0) && (mArtworkExpectedHeight > 0)) {
                mEditorArtwork = scaleBitmapIfTooBig(bitmap,
                        mArtworkExpectedWidth, mArtworkExpectedHeight);
            } else {
                // no valid resize dimensions, store as is
                mEditorArtwork = bitmap;
            }
            mArtworkChanged = true;
            return this;
        }

        /**
         * Clears all the metadata that has been set since the MetadataEditor instance was
         *     created with {@link RemoteControlClient#editMetadata(boolean)}.
         */
        public synchronized void clear() {
            if (mApplied) {
                Log.e(TAG, "Can't clear a previously applied MetadataEditor");
                return;
            }
            mEditorMetadata.clear();
            mEditorArtwork = null;
        }

        /**
         * Associates all the metadata that has been set since the MetadataEditor instance was
         *     created with {@link RemoteControlClient#editMetadata(boolean)}, or since
         *     {@link #clear()} was called, with the RemoteControlClient. Once "applied",
         *     this MetadataEditor cannot be reused to edit the RemoteControlClient's metadata.
         */
        public synchronized void apply() {
            if (mApplied) {
                Log.e(TAG, "Can't apply a previously applied MetadataEditor");
                return;
            }
            synchronized(mCacheLock) {
                // assign the edited data
                mMetadata = new Bundle(mEditorMetadata);
                if ((mArtwork != null) && (!mArtwork.equals(mEditorArtwork))) {
                    mArtwork.recycle();
                }
                mArtwork = mEditorArtwork;
                mEditorArtwork = null;
                if (mMetadataChanged & mArtworkChanged) {
                    // send to remote control display if conditions are met
                    sendMetadataWithArtwork_syncCacheLock();
                } else if (mMetadataChanged) {
                    // send to remote control display if conditions are met
                    sendMetadata_syncCacheLock();
                } else if (mArtworkChanged) {
                    // send to remote control display if conditions are met
                    sendArtwork_syncCacheLock();
                }
                mApplied = true;
            }
        }
    }

    /**
     * Creates a {@link MetadataEditor}.
     * @param startEmpty Set to false if you want the MetadataEditor to contain the metadata that
     *     was previously applied to the RemoteControlClient, or true if it is to be created empty.
     * @return a new MetadataEditor instance.
     */
    public MetadataEditor editMetadata(boolean startEmpty) {
        MetadataEditor editor = new MetadataEditor();
        if (startEmpty) {
            editor.mEditorMetadata = new Bundle();
            editor.mEditorArtwork = null;
            editor.mMetadataChanged = true;
            editor.mArtworkChanged = true;
        } else {
            editor.mEditorMetadata = new Bundle(mMetadata);
            editor.mEditorArtwork = mArtwork;
            editor.mMetadataChanged = false;
            editor.mArtworkChanged = false;
        }
        return editor;
    }

    /**
     * Sets the current playback state.
     * @param state The current playback state, one of the following values:
     *       {@link #PLAYSTATE_STOPPED},
     *       {@link #PLAYSTATE_PAUSED},
     *       {@link #PLAYSTATE_PLAYING},
     *       {@link #PLAYSTATE_FAST_FORWARDING},
     *       {@link #PLAYSTATE_REWINDING},
     *       {@link #PLAYSTATE_SKIPPING_FORWARDS},
     *       {@link #PLAYSTATE_SKIPPING_BACKWARDS},
     *       {@link #PLAYSTATE_BUFFERING},
     *       {@link #PLAYSTATE_ERROR}.
     */
    public void setPlaybackState(int state) {
        synchronized(mCacheLock) {
            if (mPlaybackState != state) {
                // store locally
                mPlaybackState = state;
                // keep track of when the state change occurred
                mPlaybackStateChangeTimeMs = SystemClock.elapsedRealtime();

                // send to remote control display if conditions are met
                sendPlaybackState_syncCacheLock();
                // update AudioService
                sendAudioServiceNewPlaybackInfo_syncCacheLock(PLAYBACKINFO_PLAYSTATE, state);
            }
        }
    }

    /**
     * Sets the flags for the media transport control buttons that this client supports.
     * @param transportControlFlags A combination of the following flags:
     *      {@link #FLAG_KEY_MEDIA_PREVIOUS},
     *      {@link #FLAG_KEY_MEDIA_REWIND},
     *      {@link #FLAG_KEY_MEDIA_PLAY},
     *      {@link #FLAG_KEY_MEDIA_PLAY_PAUSE},
     *      {@link #FLAG_KEY_MEDIA_PAUSE},
     *      {@link #FLAG_KEY_MEDIA_STOP},
     *      {@link #FLAG_KEY_MEDIA_FAST_FORWARD},
     *      {@link #FLAG_KEY_MEDIA_NEXT}
     */
    public void setTransportControlFlags(int transportControlFlags) {
        synchronized(mCacheLock) {
            // store locally
            mTransportControlFlags = transportControlFlags;

            // send to remote control display if conditions are met
            sendTransportControlFlags_syncCacheLock();
        }
    }

    /** @hide */
    public final static int DEFAULT_PLAYBACK_VOLUME_HANDLING = PLAYBACK_VOLUME_VARIABLE;
    /** @hide */
    // hard-coded to the same number of steps as AudioService.MAX_STREAM_VOLUME[STREAM_MUSIC]
    public final static int DEFAULT_PLAYBACK_VOLUME = 15;

    private int mPlaybackType = PLAYBACK_TYPE_LOCAL;
    private int mPlaybackVolumeMax = DEFAULT_PLAYBACK_VOLUME;
    private int mPlaybackVolume = DEFAULT_PLAYBACK_VOLUME;
    private int mPlaybackVolumeHandling = DEFAULT_PLAYBACK_VOLUME_HANDLING;
    private int mPlaybackStream = AudioManager.STREAM_MUSIC;

    /**
     * @hide
     * Set information describing information related to the playback of media so the system
     * can implement additional behavior to handle non-local playback usecases.
     * @param what a key to specify the type of information to set. Valid keys are
     *        {@link #PLAYBACKINFO_PLAYBACK_TYPE},
     *        {@link #PLAYBACKINFO_USES_STREAM},
     *        {@link #PLAYBACKINFO_VOLUME},
     *        {@link #PLAYBACKINFO_VOLUME_MAX},
     *        and {@link #PLAYBACKINFO_VOLUME_HANDLING}.
     * @param value the value for the supplied information to set.
     */
    public void setPlaybackInformation(int what, int value) {
        synchronized(mCacheLock) {
            switch (what) {
                case PLAYBACKINFO_PLAYBACK_TYPE:
                    if ((value >= PLAYBACK_TYPE_MIN) && (value <= PLAYBACK_TYPE_MAX)) {
                        if (mPlaybackType != value) {
                            mPlaybackType = value;
                            sendAudioServiceNewPlaybackInfo_syncCacheLock(what, value);
                        }
                    } else {
                        Log.w(TAG, "using invalid value for PLAYBACKINFO_PLAYBACK_TYPE");
                    }
                    break;
                case PLAYBACKINFO_VOLUME:
                    if ((value > -1) && (value <= mPlaybackVolumeMax)) {
                        if (mPlaybackVolume != value) {
                            mPlaybackVolume = value;
                            sendAudioServiceNewPlaybackInfo_syncCacheLock(what, value);
                        }
                    } else {
                        Log.w(TAG, "using invalid value for PLAYBACKINFO_VOLUME");
                    }
                    break;
                case PLAYBACKINFO_VOLUME_MAX:
                    if (value > 0) {
                        if (mPlaybackVolumeMax != value) {
                            mPlaybackVolumeMax = value;
                            sendAudioServiceNewPlaybackInfo_syncCacheLock(what, value);
                        }
                    } else {
                        Log.w(TAG, "using invalid value for PLAYBACKINFO_VOLUME_MAX");
                    }
                    break;
                case PLAYBACKINFO_USES_STREAM:
                    if ((value >= 0) && (value < AudioSystem.getNumStreamTypes())) {
                        mPlaybackStream = value;
                    } else {
                        Log.w(TAG, "using invalid value for PLAYBACKINFO_USES_STREAM");
                    }
                    break;
                case PLAYBACKINFO_VOLUME_HANDLING:
                    if ((value >= PLAYBACK_VOLUME_FIXED) && (value <= PLAYBACK_VOLUME_VARIABLE)) {
                        if (mPlaybackVolumeHandling != value) {
                            mPlaybackVolumeHandling = value;
                            sendAudioServiceNewPlaybackInfo_syncCacheLock(what, value);
                        }
                    } else {
                        Log.w(TAG, "using invalid value for PLAYBACKINFO_VOLUME_HANDLING");
                    }
                    break;
                default:
                    // not throwing an exception or returning an error if more keys are to be
                    // supported in the future
                    Log.w(TAG, "setPlaybackInformation() ignoring unknown key " + what);
                    break;
            }
        }
    }

    /**
     * @hide
     * Return playback information represented as an integer value.
     * @param what a key to specify the type of information to retrieve. Valid keys are
     *        {@link #PLAYBACKINFO_PLAYBACK_TYPE},
     *        {@link #PLAYBACKINFO_USES_STREAM},
     *        {@link #PLAYBACKINFO_VOLUME},
     *        {@link #PLAYBACKINFO_VOLUME_MAX},
     *        and {@link #PLAYBACKINFO_VOLUME_HANDLING}.
     * @return the current value for the given information type, or
     *   {@link #PLAYBACKINFO_INVALID_VALUE} if an error occurred or the request is invalid, or
     *   the value is unknown.
     */
    public int getIntPlaybackInformation(int what) {
        synchronized(mCacheLock) {
            switch (what) {
                case PLAYBACKINFO_PLAYBACK_TYPE:
                    return mPlaybackType;
                case PLAYBACKINFO_VOLUME:
                    return mPlaybackVolume;
                case PLAYBACKINFO_VOLUME_MAX:
                    return mPlaybackVolumeMax;
                case PLAYBACKINFO_USES_STREAM:
                    return mPlaybackStream;
                case PLAYBACKINFO_VOLUME_HANDLING:
                    return mPlaybackVolumeHandling;
                default:
                    Log.e(TAG, "getIntPlaybackInformation() unknown key " + what);
                    return PLAYBACKINFO_INVALID_VALUE;
            }
        }
    }

    /**
     * Lock for all cached data
     */
    private final Object mCacheLock = new Object();
    /**
     * Cache for the playback state.
     * Access synchronized on mCacheLock
     */
    private int mPlaybackState = PLAYSTATE_NONE;
    /**
     * Time of last play state change
     * Access synchronized on mCacheLock
     */
    private long mPlaybackStateChangeTimeMs = 0;
    /**
     * Cache for the artwork bitmap.
     * Access synchronized on mCacheLock
     * Artwork and metadata are not kept in one Bundle because the bitmap sometimes needs to be
     * accessed to be resized, in which case a copy will be made. This would add overhead in
     * Bundle operations.
     */
    private Bitmap mArtwork;
    private final int ARTWORK_DEFAULT_SIZE = 256;
    private final int ARTWORK_INVALID_SIZE = -1;
    private int mArtworkExpectedWidth = ARTWORK_DEFAULT_SIZE;
    private int mArtworkExpectedHeight = ARTWORK_DEFAULT_SIZE;
    /**
     * Cache for the transport control mask.
     * Access synchronized on mCacheLock
     */
    private int mTransportControlFlags = FLAGS_KEY_MEDIA_NONE;
    /**
     * Cache for the metadata strings.
     * Access synchronized on mCacheLock
     * This is re-initialized in apply() and so cannot be final.
     */
    private Bundle mMetadata = new Bundle();

    /**
     * The current remote control client generation ID across the system
     */
    private int mCurrentClientGenId = -1;
    /**
     * The remote control client generation ID, the last time it was told it was the current RC.
     * If (mCurrentClientGenId == mInternalClientGenId) is true, it means that this remote control
     * client is the "focused" one, and that whenever this client's info is updated, it needs to
     * send it to the known IRemoteControlDisplay interfaces.
     */
    private int mInternalClientGenId = -2;

    /**
     * The media button intent description associated with this remote control client
     * (can / should include target component for intent handling)
     */
    private final PendingIntent mRcMediaIntent;

    /**
     * The remote control display to which this client will send information.
     * NOTE: Only one IRemoteControlDisplay supported in this implementation
     */
    private IRemoteControlDisplay mRcDisplay;

    /**
     * @hide
     * Accessor to media button intent description (includes target component)
     */
    public PendingIntent getRcMediaIntent() {
        return mRcMediaIntent;
    }
    /**
     * @hide
     * Accessor to IRemoteControlClient
     */
    public IRemoteControlClient getIRemoteControlClient() {
        return mIRCC;
    }

    /**
     * The IRemoteControlClient implementation
     */
    private final IRemoteControlClient mIRCC = new IRemoteControlClient.Stub() {

        public void onInformationRequested(int clientGeneration, int infoFlags,
                int artWidth, int artHeight) {
            // only post messages, we can't block here
            if (mEventHandler != null) {
                // signal new client
                mEventHandler.removeMessages(MSG_NEW_INTERNAL_CLIENT_GEN);
                mEventHandler.dispatchMessage(
                        mEventHandler.obtainMessage(
                                MSG_NEW_INTERNAL_CLIENT_GEN,
                                artWidth, artHeight,
                                new Integer(clientGeneration)));
                // send the information
                mEventHandler.removeMessages(MSG_REQUEST_PLAYBACK_STATE);
                mEventHandler.removeMessages(MSG_REQUEST_METADATA);
                mEventHandler.removeMessages(MSG_REQUEST_TRANSPORTCONTROL);
                mEventHandler.removeMessages(MSG_REQUEST_ARTWORK);
                mEventHandler.dispatchMessage(
                        mEventHandler.obtainMessage(MSG_REQUEST_PLAYBACK_STATE));
                mEventHandler.dispatchMessage(
                        mEventHandler.obtainMessage(MSG_REQUEST_TRANSPORTCONTROL));
                mEventHandler.dispatchMessage(mEventHandler.obtainMessage(MSG_REQUEST_METADATA));
                mEventHandler.dispatchMessage(mEventHandler.obtainMessage(MSG_REQUEST_ARTWORK));
            }
        }

        public void setCurrentClientGenerationId(int clientGeneration) {
            // only post messages, we can't block here
            if (mEventHandler != null) {
                mEventHandler.removeMessages(MSG_NEW_CURRENT_CLIENT_GEN);
                mEventHandler.dispatchMessage(mEventHandler.obtainMessage(
                        MSG_NEW_CURRENT_CLIENT_GEN, clientGeneration, 0/*ignored*/));
            }
        }

        public void plugRemoteControlDisplay(IRemoteControlDisplay rcd) {
            // only post messages, we can't block here
            if (mEventHandler != null) {
                mEventHandler.dispatchMessage(mEventHandler.obtainMessage(
                        MSG_PLUG_DISPLAY, rcd));
            }
        }

        public void unplugRemoteControlDisplay(IRemoteControlDisplay rcd) {
            // only post messages, we can't block here
            if (mEventHandler != null) {
                mEventHandler.dispatchMessage(mEventHandler.obtainMessage(
                        MSG_UNPLUG_DISPLAY, rcd));
            }
        }
    };

    /**
     * @hide
     * Default value for the unique identifier
     */
    public final static int RCSE_ID_UNREGISTERED = -1;
    /**
     * Unique identifier of the RemoteControlStackEntry in AudioService with which
     * this RemoteControlClient is associated.
     */
    private int mRcseId = RCSE_ID_UNREGISTERED;
    /**
     * @hide
     * To be only used by AudioManager after it has received the unique id from
     * IAudioService.registerRemoteControlClient()
     * @param id the unique identifier of the RemoteControlStackEntry in AudioService with which
     *              this RemoteControlClient is associated.
     */
    public void setRcseId(int id) {
        mRcseId = id;
    }

    /**
     * @hide
     */
    public int getRcseId() {
        return mRcseId;
    }

    private EventHandler mEventHandler;
    private final static int MSG_REQUEST_PLAYBACK_STATE = 1;
    private final static int MSG_REQUEST_METADATA = 2;
    private final static int MSG_REQUEST_TRANSPORTCONTROL = 3;
    private final static int MSG_REQUEST_ARTWORK = 4;
    private final static int MSG_NEW_INTERNAL_CLIENT_GEN = 5;
    private final static int MSG_NEW_CURRENT_CLIENT_GEN = 6;
    private final static int MSG_PLUG_DISPLAY = 7;
    private final static int MSG_UNPLUG_DISPLAY = 8;

    private class EventHandler extends Handler {
        public EventHandler(RemoteControlClient rcc, Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_REQUEST_PLAYBACK_STATE:
                    synchronized (mCacheLock) {
                        sendPlaybackState_syncCacheLock();
                    }
                    break;
                case MSG_REQUEST_METADATA:
                    synchronized (mCacheLock) {
                        sendMetadata_syncCacheLock();
                    }
                    break;
                case MSG_REQUEST_TRANSPORTCONTROL:
                    synchronized (mCacheLock) {
                        sendTransportControlFlags_syncCacheLock();
                    }
                    break;
                case MSG_REQUEST_ARTWORK:
                    synchronized (mCacheLock) {
                        sendArtwork_syncCacheLock();
                    }
                    break;
                case MSG_NEW_INTERNAL_CLIENT_GEN:
                    onNewInternalClientGen((Integer)msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_NEW_CURRENT_CLIENT_GEN:
                    onNewCurrentClientGen(msg.arg1);
                    break;
                case MSG_PLUG_DISPLAY:
                    onPlugDisplay((IRemoteControlDisplay)msg.obj);
                    break;
                case MSG_UNPLUG_DISPLAY:
                    onUnplugDisplay((IRemoteControlDisplay)msg.obj);
                    break;
                default:
                    Log.e(TAG, "Unknown event " + msg.what + " in RemoteControlClient handler");
            }
        }
    }

    //===========================================================
    // Communication with IRemoteControlDisplay

    private void detachFromDisplay_syncCacheLock() {
        mRcDisplay = null;
        mArtworkExpectedWidth = ARTWORK_INVALID_SIZE;
        mArtworkExpectedHeight = ARTWORK_INVALID_SIZE;
    }

    private void sendPlaybackState_syncCacheLock() {
        if ((mCurrentClientGenId == mInternalClientGenId) && (mRcDisplay != null)) {
            try {
                mRcDisplay.setPlaybackState(mInternalClientGenId, mPlaybackState,
                        mPlaybackStateChangeTimeMs);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in setPlaybackState(), dead display "+e);
                detachFromDisplay_syncCacheLock();
            }
        }
    }

    private void sendMetadata_syncCacheLock() {
        if ((mCurrentClientGenId == mInternalClientGenId) && (mRcDisplay != null)) {
            try {
                mRcDisplay.setMetadata(mInternalClientGenId, mMetadata);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in sendPlaybackState(), dead display "+e);
                detachFromDisplay_syncCacheLock();
            }
        }
    }

    private void sendTransportControlFlags_syncCacheLock() {
        if ((mCurrentClientGenId == mInternalClientGenId) && (mRcDisplay != null)) {
            try {
                mRcDisplay.setTransportControlFlags(mInternalClientGenId,
                        mTransportControlFlags);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in sendTransportControlFlags(), dead display "+e);
                detachFromDisplay_syncCacheLock();
            }
        }
    }

    private void sendArtwork_syncCacheLock() {
        if ((mCurrentClientGenId == mInternalClientGenId) && (mRcDisplay != null)) {
            // even though we have already scaled in setArtwork(), when this client needs to
            // send the bitmap, there might be newer and smaller expected dimensions, so we have
            // to check again.
            mArtwork = scaleBitmapIfTooBig(mArtwork, mArtworkExpectedWidth, mArtworkExpectedHeight);
            try {
                mRcDisplay.setArtwork(mInternalClientGenId, mArtwork);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in sendArtwork(), dead display "+e);
                detachFromDisplay_syncCacheLock();
            }
        }
    }

    private void sendMetadataWithArtwork_syncCacheLock() {
        if ((mCurrentClientGenId == mInternalClientGenId) && (mRcDisplay != null)) {
            // even though we have already scaled in setArtwork(), when this client needs to
            // send the bitmap, there might be newer and smaller expected dimensions, so we have
            // to check again.
            mArtwork = scaleBitmapIfTooBig(mArtwork, mArtworkExpectedWidth, mArtworkExpectedHeight);
            try {
                mRcDisplay.setAllMetadata(mInternalClientGenId, mMetadata, mArtwork);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in setAllMetadata(), dead display "+e);
                detachFromDisplay_syncCacheLock();
            }
        }
    }

    //===========================================================
    // Communication with AudioService

    private static IAudioService sService;

    private static IAudioService getService()
    {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(Context.AUDIO_SERVICE);
        sService = IAudioService.Stub.asInterface(b);
        return sService;
    }

    private void sendAudioServiceNewPlaybackInfo_syncCacheLock(int what, int value) {
        if (mRcseId == RCSE_ID_UNREGISTERED) {
            return;
        }
        Log.d(TAG, "sending to AudioService key=" + what + ", value=" + value);
        IAudioService service = getService();
        try {
            service.setPlaybackInfoForRcc(mRcseId, what, value);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in sendAudioServiceNewPlaybackInfo_syncCacheLock", e);
        }
    }

    //===========================================================
    // Message handlers

    private void onNewInternalClientGen(Integer clientGeneration, int artWidth, int artHeight) {
        synchronized (mCacheLock) {
            // this remote control client is told it is the "focused" one:
            // it implies that now (mCurrentClientGenId == mInternalClientGenId) is true
            mInternalClientGenId = clientGeneration.intValue();
            if (artWidth > 0) {
                mArtworkExpectedWidth = artWidth;
                mArtworkExpectedHeight = artHeight;
            }
        }
    }

    private void onNewCurrentClientGen(int clientGeneration) {
        synchronized (mCacheLock) {
            mCurrentClientGenId = clientGeneration;
        }
    }

    private void onPlugDisplay(IRemoteControlDisplay rcd) {
        synchronized(mCacheLock) {
            mRcDisplay = rcd;
        }
    }

    private void onUnplugDisplay(IRemoteControlDisplay rcd) {
        synchronized(mCacheLock) {
            if ((mRcDisplay != null) && (mRcDisplay.asBinder().equals(rcd.asBinder()))) {
                mRcDisplay = null;
                mArtworkExpectedWidth = ARTWORK_DEFAULT_SIZE;
                mArtworkExpectedHeight = ARTWORK_DEFAULT_SIZE;
            }
        }
    }

    //===========================================================
    // Internal utilities

    /**
     * Scale a bitmap to fit the smallest dimension by uniformly scaling the incoming bitmap.
     * If the bitmap fits, then do nothing and return the original.
     *
     * @param bitmap
     * @param maxWidth
     * @param maxHeight
     * @return
     */

    private Bitmap scaleBitmapIfTooBig(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap != null) {
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();
            if (width > maxWidth || height > maxHeight) {
                float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
                int newWidth = Math.round(scale * width);
                int newHeight = Math.round(scale * height);
                Bitmap.Config newConfig = bitmap.getConfig();
                if (newConfig == null) {
                    newConfig = Bitmap.Config.ARGB_8888;
                }
                Bitmap outBitmap = Bitmap.createBitmap(newWidth, newHeight, newConfig);
                Canvas canvas = new Canvas(outBitmap);
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setFilterBitmap(true);
                canvas.drawBitmap(bitmap, null,
                        new RectF(0, 0, outBitmap.getWidth(), outBitmap.getHeight()), paint);
                bitmap = outBitmap;
            }
        }
        return bitmap;
    }

    /**
     *  Fast routine to go through an array of allowed keys and return whether the key is part
     *  of that array
     * @param key the key value
     * @param validKeys the array of valid keys for a given type
     * @return true if the key is part of the array, false otherwise
     */
    private static boolean validTypeForKey(int key, int[] validKeys) {
        try {
            for (int i = 0 ; ; i++) {
                if (key == validKeys[i]) {
                    return true;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
