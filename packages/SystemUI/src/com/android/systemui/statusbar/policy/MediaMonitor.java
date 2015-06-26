package com.android.systemui.statusbar.policy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class which does the bookkeeping on media sessions
 * and reports when the current play state changes via {@link #onPlayStateChanged(boolean)}
 */
public abstract class MediaMonitor implements MediaSessionManager.OnActiveSessionsChangedListener {

    private Map<MediaSession.Token, CallbackInfo> mCallbacks = new HashMap<>();
    private MediaSessionManager mMediaSessionManager;
    private boolean mIsAnythingPlaying;
    private boolean mListening;

    public MediaMonitor(Context context) {
        mMediaSessionManager = (MediaSessionManager)
                context.getSystemService(Context.MEDIA_SESSION_SERVICE);

        mIsAnythingPlaying = isAnythingPlayingColdCheck();
    }

    public abstract void onPlayStateChanged(boolean playing);

    public boolean isAnythingPlaying() {
        return mIsAnythingPlaying;
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mListening) {
            mIsAnythingPlaying = isAnythingPlayingColdCheck();
            mMediaSessionManager.addOnActiveSessionsChangedListener(this, null);
        } else {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
            cleanup();
        }
    }

    private void cleanup() {
        for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
            entry.getValue().unregister();
        }
        mCallbacks.clear();
    }

    @Override
    public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (!mCallbacks.containsKey(controller.getSessionToken())) {
                    mCallbacks.put(controller.getSessionToken(), new CallbackInfo(controller));
                }
            }
        }
        checkIfPlaying();
    }

    public void checkIfPlaying() {
        boolean anythingPlaying = false;
        for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
            if (entry.getValue().isPlaying()) {
                anythingPlaying = true;
                break;
            }
        }

        if (anythingPlaying != mIsAnythingPlaying) {
            mIsAnythingPlaying = anythingPlaying;
            if (mListening) {
                onPlayStateChanged(mIsAnythingPlaying);
            }
        }
    }

    private class CallbackInfo {
        MediaController.Callback mCallback;
        MediaController mController;
        boolean mIsPlaying;

        public CallbackInfo(final MediaController controller) {
            this.mController = controller;
            mCallback = new MediaController.Callback() {
                @Override
                public void onSessionDestroyed() {
                    destroy();
                    checkIfPlaying();
                }

                @Override
                public void onPlaybackStateChanged(@NonNull PlaybackState state) {
                    mIsPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                    checkIfPlaying();
                }
            };
            controller.registerCallback(mCallback);

            mIsPlaying = controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        }

        public boolean isPlaying() {
            return mIsPlaying;
        }

        public void unregister() {
            mController.unregisterCallback(mCallback);
            mIsPlaying = false;
        }

        public void destroy() {
            unregister();
            mCallbacks.remove(mController.getSessionToken());
            mController = null;
            mCallback = null;
        }
    }

    public boolean isAnythingPlayingColdCheck() {
        List<MediaController> activeSessions = mMediaSessionManager.getActiveSessions(null);
        for (MediaController activeSession : activeSessions) {
            PlaybackState playbackState = activeSession.getPlaybackState();
            if (playbackState != null && playbackState.getState()
                    == PlaybackState.STATE_PLAYING) {
                return true;
            }
        }
        return false;
    }
}
