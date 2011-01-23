package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public abstract class MediaKeyEventButton extends PowerButton {
    private static final String TAG = "MediaKeyEventButton";

    private static AudioManager AUDIO_MANAGER = null;

    // possible media states
    private static final int MEDIA_STATE_UNKNOWN = -1;
    private static final int MEDIA_STATE_ACTIVE = 0;
    private static final int MEDIA_STATE_INACTIVE = 1;

    // we set this to -1 since we don't know the current media state
    private int mCurrentMediaState = MEDIA_STATE_UNKNOWN;

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(MediaPlayer.MEDIA_PLAYBACK_STATE_CHANGED_ACTION)) {
            switch(intent.getIntExtra(MediaPlayer.EXTRA_MEDIA_PLAYBACK_STATE, -1)) {
                case MediaPlayer.MEDIA_PLAYBACK_STATE_STARTED:
                    mCurrentMediaState = MEDIA_STATE_ACTIVE;
                    break;
                case MediaPlayer.MEDIA_PLAYBACK_STATE_PAUSED:
                case MediaPlayer.MEDIA_PLAYBACK_STATE_COMPLETED:
                    mCurrentMediaState = MEDIA_STATE_INACTIVE;
                    break;
                default:
                    mCurrentMediaState = MEDIA_STATE_UNKNOWN;
                    break;
            }
        }
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlayer.MEDIA_PLAYBACK_STATE_CHANGED_ACTION);
        return filter;
    }

    protected void sendMediaKeyEvent(int code) {
        Context context = mView.getContext();
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        context.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        context.sendOrderedBroadcast(upIntent, null);
    }

    protected boolean isMusicActive() {
        if(mCurrentMediaState == MEDIA_STATE_UNKNOWN) {
            AudioManager am = getAudioManager(mView.getContext());
            if(am != null) {
                return am.isMusicActive();
            } else {
                return false;
            }
        } else {
            boolean active = (mCurrentMediaState == MEDIA_STATE_ACTIVE);
            mCurrentMediaState = MEDIA_STATE_UNKNOWN;
            return active;
        }
    }

    private static AudioManager getAudioManager(Context context) {
        if(AUDIO_MANAGER == null) {
            AUDIO_MANAGER = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        return AUDIO_MANAGER;
    }
}
