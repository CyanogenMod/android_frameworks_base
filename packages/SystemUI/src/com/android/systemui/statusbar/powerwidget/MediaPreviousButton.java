package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.view.KeyEvent;

public class MediaPreviousButton extends MediaKeyEventButton {
    public MediaPreviousButton() { mType = BUTTON_MEDIA_PREVIOUS; }

    @Override
    protected void updateState() {
        mIcon = R.drawable.stat_media_previous;
        mState = STATE_DISABLED;
    }

    @Override
    protected void toggleState() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    @Override
    protected boolean handleLongClick() {
        return false;
    }
}
