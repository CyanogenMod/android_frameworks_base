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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.systemui.R;

import cyanogenmod.providers.CMSettings;

/**
 * The guts of a media notification revealed when performing a long press.
 */
public class MediaNotificationGuts extends LinearLayout {

    private static final String TAG = MediaNotificationGuts.class.getSimpleName();

    private ViewGroup mQueueGroup;
    private TextView mText;
    private Switch mSwitch;

    public MediaNotificationGuts(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // do nothing!
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQueueGroup = (ViewGroup) findViewById(R.id.notification_guts_media_extension);

        mSwitch = (Switch) findViewById(R.id.queue_switch);
        boolean enabled = CMSettings.System.getInt(getContext().getContentResolver(),
                CMSettings.System.NOTIFICATION_PLAY_QUEUE, 1) == 1;

        mSwitch.setChecked(enabled);
        mText = (TextView) findViewById(R.id.switch_label);
        mText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSwitch.toggle();
            }
        });
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonView.setChecked(isChecked);
                CMSettings.System.putInt(getContext().getContentResolver(),
                        CMSettings.System.NOTIFICATION_PLAY_QUEUE,
                        isChecked ? 1 : 0);
            }
        });
    }
}
