/*
 * Copyright (C) 2012 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.media.AudioManager;
import android.content.IntentFilter;

import com.android.systemui.R;

public class SoundController extends BroadcastReceiver  implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.SoundController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mSoundMode;

    public SoundController(Context context, CompoundButton checkbox) {
        this(context);
        mContext = context;
        mSoundMode = getSoundMode();
        mCheckBox = checkbox;
        checkbox.setChecked(mSoundMode);
        checkbox.setOnCheckedChangeListener(this);
    }

    public SoundController(Context context) {
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        context.registerReceiver(this, filter);
    }

    public void release() {
        mContext.unregisterReceiver(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(checked ? AudioManager.RINGER_MODE_NORMAL : AudioManager.RINGER_MODE_SILENT);
    }

    public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mCheckBox.setChecked(getSoundMode());
            }
    }

    private boolean getSoundMode() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                return false;
            case AudioManager.RINGER_MODE_NORMAL:
                return true;
        }
        return false;
    }

}
