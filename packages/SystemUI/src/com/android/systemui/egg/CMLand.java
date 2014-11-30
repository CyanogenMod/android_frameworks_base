/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui.egg;

import com.android.systemui.R;

import android.content.Context;
import android.util.AttributeSet;

public class CMLand extends LLand {
    public static final String TAG = "CMLand";

    public CMLand(Context context) {
        super(context, null);
    }

    public CMLand(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public CMLand(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected int getEggPlayer() {
        return R.drawable.cid;
    }

    @Override
    protected int getEggPlayerColor() {
        return 0xFF33B5E7;
    }
}
