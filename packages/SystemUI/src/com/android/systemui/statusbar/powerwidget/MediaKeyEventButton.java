package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public abstract class MediaKeyEventButton extends PowerButton {
    private static final String TAG = "MediaKeyEventButton";

    private static AudioManager AUDIO_MANAGER = null;

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

    protected static AudioManager getAudioManager(Context context) {
        if(AUDIO_MANAGER == null) {
            AUDIO_MANAGER = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        return AUDIO_MANAGER;
    }
}
