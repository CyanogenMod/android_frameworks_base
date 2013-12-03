/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2013-2014 The CyanogenMod Project
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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Slog;
import android.view.animation.DecelerateInterpolator;

import com.android.systemui.cm.CMCaseView;

public class DessertCase extends Activity {
    DessertCaseView mView;

    @Override
    public void onStart() {
        super.onStart();
        final boolean isCM = getIntent().getBooleanExtra("is_cm", false);

        PackageManager pm = getPackageManager();
        final ComponentName cn = new ComponentName(this, DessertCaseDream.class);
        if (pm.getComponentEnabledSetting(cn) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            if (isCM) {
                Slog.v("DessertCase", "CyanogenMod enabled!");
            } else {
                Slog.v("DessertCase", "ACHIEVEMENT UNLOCKED");
            }
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }

        if (isCM) {
            mView = new CMCaseView(this);
        } else {
            mView = new DessertCaseView(this);
        }

        DessertCaseView.RescalingContainer container = new DessertCaseView.RescalingContainer(this);

        container.setView(mView);

        setContentView(container);
    }

    @Override
    public void onResume() {
        super.onResume();
        mView.postDelayed(new Runnable() {
            public void run() {
                mView.start();
            }
        }, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        mView.stop();
    }
}
