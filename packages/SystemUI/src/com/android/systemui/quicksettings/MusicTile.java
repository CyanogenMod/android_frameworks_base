/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class MusicTile extends QuickSettingsTile {

    private final String TAG = "MusicTile";
    private final boolean DBG = false;

    private boolean mActive = false;
    private boolean mClientIdLost = true;
    private int mMusicTileMode;
    private Metadata mMetadata = new Metadata();

    private RemoteController mRemoteController;
    private IAudioService mAudioService = null;

    public MusicTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mRemoteController = new RemoteController(context, mRCClientUpdateListener);
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.registerRemoteController(mRemoteController);
        mRemoteController.setArtworkConfiguration(true, 100, 80);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_NEXT);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.MUSIC_TILE_MODE), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private void updateTile() {
        mMusicTileMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.MUSIC_TILE_MODE, 3,
                UserHandle.USER_CURRENT);
        final ImageView background =
                (ImageView) mTile.findViewById(R.id.background);
        if (background != null) {
            if (mMetadata.bitmap != null && (mMusicTileMode == 1 || mMusicTileMode == 3)) {
                background.setImageDrawable(new BitmapDrawable(mMetadata.bitmap));
                background.setColorFilter(
                    Color.rgb(123, 123, 123), android.graphics.PorterDuff.Mode.MULTIPLY);
            } else {
                background.setImageDrawable(null);
                background.setColorFilter(null);
            }
        }
        if (mActive) {
            mDrawable = R.drawable.ic_qs_media_pause;
            mLabel = mMetadata.trackTitle != null && mMusicTileMode > 1
                ? mMetadata.trackTitle : mContext.getString(R.string.quick_settings_music_pause);
        } else {
            mDrawable = R.drawable.ic_qs_media_play;
            mLabel = mContext.getString(R.string.quick_settings_music_play);
        }
    }

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mActive) {
            mActive = active;
            updateResources();
        }
    }

    private void sendMediaButtonClick(int keyCode) {
        if (!mClientIdLost) {
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            dispatchMediaKeyWithWakeLockToAudioService(key);
            dispatchMediaKeyWithWakeLockToAudioService(
                KeyEvent.changeAction(key, KeyEvent.ACTION_UP));
        }
    }

    private void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        mAudioService = getAudioService();
        if (mAudioService != null) {
            try {
                mAudioService.dispatchMediaKeyEventUnderWakelock(event);
            } catch (RemoteException e) {
                if (DBG) Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
            }
        }
    }

    private IAudioService getAudioService() {
        if (mAudioService == null) {
            mAudioService = IAudioService.Stub.asInterface(
                    ServiceManager.checkService(Context.AUDIO_SERVICE));
            if (mAudioService == null) {
                if (DBG) Log.w(TAG, "Unable to find IAudioService interface.");
            }
        }
        return mAudioService;
    }

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        private String mCurrentTrack = null;
        private Bitmap mCurrentBitmap = null;

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mCurrentTrack = null;
                mCurrentBitmap = null;
                mActive = false;
                mClientIdLost = true;
                updateResources();
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mMetadata.bitmap = data.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK,
                    mMetadata.bitmap);
            mClientIdLost = false;
            if ((mMetadata.trackTitle != null
                    && !mMetadata.trackTitle.equals(mCurrentTrack))
                || (mMetadata.bitmap != null && !mMetadata.bitmap.sameAs(mCurrentBitmap))) {
                mCurrentTrack = mMetadata.trackTitle;
                mCurrentBitmap = mMetadata.bitmap;
                updateResources();
            }
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;
        private Bitmap bitmap;

        public void clear() {
            trackTitle = null;
            bitmap = null;
        }
    }

}
