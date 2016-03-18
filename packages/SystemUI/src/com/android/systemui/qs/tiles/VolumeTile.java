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
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

public class VolumeTile extends QSTile<QSTile.BooleanState> {

    private static final Intent SOUND_SETTINGS = new Intent("android.settings.SOUND_SETTINGS");

    public VolumeTile(Host host) {
        super(host);
    }

    @Override
    protected void handleClick() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(SOUND_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_volume_panel_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_volume_panel); // TODO needs own icon
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_VOLUME;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        // Do nothing
    }
}
