/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package com.android.systemui.tuner;

import android.annotation.Nullable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import android.preference.PreferenceGroup;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarIconController;

public class StatusBarIconBlacklistFragment extends PreferenceFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tuner_statusbar_icons);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerPrefs(getPreferenceScreen());
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterPrefs(getPreferenceScreen());
    }

    private void registerPrefs(PreferenceGroup group) {
        TunerService tunerService = TunerService.get(getContext());
        final int N = group.getPreferenceCount();
        for (int i = 0; i < N; i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof StatusBarSwitch) {
                tunerService.addTunable((TunerService.Tunable) pref, StatusBarIconController.ICON_BLACKLIST);
            } else if (pref instanceof PreferenceGroup) {
                registerPrefs((PreferenceGroup) pref);
            }
        }
    }

    private void unregisterPrefs(PreferenceGroup group) {
        TunerService tunerService = TunerService.get(getContext());
        final int N = group.getPreferenceCount();
        for (int i = 0; i < N; i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof TunerService.Tunable) {
                tunerService.removeTunable((TunerService.Tunable) pref);
            } else if (pref instanceof PreferenceGroup) {
                registerPrefs((PreferenceGroup) pref);
            }
        }
    }
}
