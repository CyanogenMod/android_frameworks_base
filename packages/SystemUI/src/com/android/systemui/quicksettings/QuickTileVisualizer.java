/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.util.AttributeSet;

import com.pheelicks.visualizer.VisualizerView;

public class QuickTileVisualizer extends VisualizerView {
    public QuickTileVisualizer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
    }

    public QuickTileVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public QuickTileVisualizer(Context context) {
        super(context, null, 0);
    }
}
