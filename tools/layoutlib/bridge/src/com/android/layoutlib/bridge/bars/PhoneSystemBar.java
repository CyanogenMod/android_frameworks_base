/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.layoutlib.bridge.bars;

import com.android.resources.Density;
import com.android.resources.ResourceType;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.Gravity;
import android.widget.TextView;

public class PhoneSystemBar extends CustomBar {

    public PhoneSystemBar(Context context, Density density) throws XmlPullParserException {
        super(context, density, "/bars/phone_system_bar.xml");

        setGravity(mGravity | Gravity.RIGHT);
        setBackgroundColor(0xFF000000);

        // Cannot access the inside items through id because no R.id values have been
        // created for them.
        // We do know the order though.
        // 0 is the spacer.
        loadIcon(1, "stat_sys_wifi_signal_4_fully.png", density);
        Drawable drawable = loadIcon(2, ResourceType.DRAWABLE, "stat_sys_battery_charge");
        if (drawable instanceof LevelListDrawable) {
            ((LevelListDrawable) drawable).setLevel(100);
        }
    }

    @Override
    protected TextView getStyleableTextView() {
        return null;
    }
}
