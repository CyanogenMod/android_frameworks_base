package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.view.KeyEvent;

public class MediaPlayPauseButton extends MediaKeyEventButton {
    public MediaPlayPauseButton() { mType = BUTTON_MEDIA_PLAY_PAUSE; }

    @Override
    protected void updateState() {
        if(isMusicActive()) {
            mIcon = R.drawable.stat_media_pause;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_media_play;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }
}
