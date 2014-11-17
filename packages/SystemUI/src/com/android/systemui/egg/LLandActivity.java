/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.systemui.R;

public class LLandActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean isCM = getIntent().getBooleanExtra("is_cm", false);
        if (isCM) {
            setContentView(R.layout.cmland);
            CMLand world = (CMLand) findViewById(R.id.world);
            world.setScoreField((TextView) findViewById(R.id.score));
            world.setSplash(findViewById(R.id.welcome));
            Log.v(CMLand.TAG, "focus: " + world.requestFocus());
        } else {
            setContentView(R.layout.lland);
            LLand world = (LLand) findViewById(R.id.world);
            world.setScoreField((TextView) findViewById(R.id.score));
            world.setSplash(findViewById(R.id.welcome));
            Log.v(LLand.TAG, "focus: " + world.requestFocus());
        }
    }
}
