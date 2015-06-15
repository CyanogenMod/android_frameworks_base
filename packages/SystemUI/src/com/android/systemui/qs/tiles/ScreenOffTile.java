/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Screen off **/
public class ScreenOffTile extends QSTile<QSTile.BooleanState> {

    private PowerManager mPm;
    private boolean mListening;

    public ScreenOffTile(Host host) {
        super(host);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    public void handleClick() {
        mHost.collapsePanels();
        mPm.goToSleep(SystemClock.uptimeMillis());
    }

    @Override
    protected void handleSecondaryClick() {
        mHost.collapsePanels();
        triggerVirtualKeypress(KeyEvent.KEYCODE_POWER, true);
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        triggerVirtualKeypress(KeyEvent.KEYCODE_POWER, true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_screen_off);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_power);
    }

    private void triggerVirtualKeypress(final int keyCode, final boolean longPress) {
        new Thread(new Runnable() {
            public void run() {
                InputManager im = InputManager.getInstance();
                KeyEvent keyEvent;
                if (longPress) {
                    keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                    keyEvent.changeFlags(keyEvent, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_LONG_PRESS);
                } else {
                    keyEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
                    keyEvent.changeFlags(keyEvent, KeyEvent.FLAG_FROM_SYSTEM);
                }
                im.injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
            }
        }).start();
    }
}
