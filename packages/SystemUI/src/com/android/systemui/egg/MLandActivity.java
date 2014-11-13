/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2014-2015 The CyanogenMod Project
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
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;

public class MLandActivity extends Activity {
    CMLand cmLand = null;
    MLand mLand = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean isCM = getIntent().getBooleanExtra("is_cm", false);
        setContentView(isCM ? R.layout.cmland : R.layout.mland);
        if (isCM) {
          cmLand = (CMLand) findViewById(R.id.world);
        } else {
          mLand = (MLand) findViewById(R.id.world);
        }
        getLand().setScoreFieldHolder((ViewGroup) findViewById(R.id.scores));
        final View welcome = findViewById(R.id.welcome);
        getLand().setSplash(welcome);
        final int numControllers = getLand().getGameControllers().size();
        if (numControllers > 0) {
            getLand().setupPlayers(numControllers);
        }
    }

    private MLand getLand() {
        if (cmLand != null) {
            return cmLand;
        } else {
            return mLand;
        }
    }

    public void updateSplashPlayers() {
        final int N = getLand().getNumPlayers();
        final View minus = findViewById(R.id.player_minus_button);
        final View plus = findViewById(R.id.player_plus_button);
        if (N == 1) {
            minus.setVisibility(View.INVISIBLE);
            plus.setVisibility(View.VISIBLE);
            plus.requestFocus();
        } else if (N == getLand().MAX_PLAYERS) {
            minus.setVisibility(View.VISIBLE);
            plus.setVisibility(View.INVISIBLE);
            minus.requestFocus();
        } else {
            minus.setVisibility(View.VISIBLE);
            plus.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        getLand().stop();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        getLand().onAttachedToWindow(); // resets and starts animation
        updateSplashPlayers();
        getLand().showSplash();
    }

    public void playerMinus(View v) {
        getLand().removePlayer();
        updateSplashPlayers();
    }

    public void playerPlus(View v) {
        getLand().addPlayer();
        updateSplashPlayers();
    }

    public void startButtonPressed(View v) {
        findViewById(R.id.player_minus_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.player_plus_button).setVisibility(View.INVISIBLE);
        getLand().start(true);
    }
}
