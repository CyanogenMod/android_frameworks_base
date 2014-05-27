/*
 * Copyright (C) 2014 The SlimRoms Project
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

package com.android.systemui;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.android.systemui.R;

public class ReminderMessageView extends LinearLayout {

    public ReminderMessageView(Context context, String message) {
        super(context);
        View view = LayoutInflater.from(context).inflate(R.layout.reminder_entry, this, true);
        TextView messageView = (TextView) view.findViewById(R.id.message_content);
        messageView.setText(message);
    }
}
