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
