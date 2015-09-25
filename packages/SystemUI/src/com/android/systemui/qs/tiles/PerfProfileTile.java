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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

public class PerfProfileTile extends QSTile<PerfProfileTile.ProfileState> {

    private static final Intent BATTERY_SETTINGS = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);

    private AnimationIcon mHighPerf = new AnimationIcon(R.drawable.ic_qs_perf_profile_highperf_avd);
    private AnimationIcon mBattery = new AnimationIcon(R.drawable.ic_qs_perf_profile_pwrsv_avd);
    private AnimationIcon mBalanced = new AnimationIcon(R.drawable.ic_qs_perf_profile_bal_avd);

    private final String[] mEntries;
    private final String[] mDescriptionEntries;
    private final String[] mAnnouncementEntries;
    private final String[] mPerfProfileValues;
    private String mPerfProfileDefaultEntry;

    private final PowerManager mPm;
    private boolean mListening;

    private PerformanceProfileObserver mObserver;

    public PerfProfileTile(Host host) {
        super(host);
        mObserver = new PerformanceProfileObserver(mHandler);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        Resources res = mContext.getResources();

        mPerfProfileDefaultEntry = mPm.getDefaultPowerProfile();
        mPerfProfileValues = res.getStringArray(com.android.internal.R.array.perf_profile_values);

        mEntries = res.getStringArray(com.android.internal.R.array.perf_profile_entries);
        mDescriptionEntries = res.getStringArray(R.array.perf_profile_description);
        mAnnouncementEntries = res.getStringArray(R.array.perf_profile_announcement);
    }

    @Override
    protected ProfileState newTileState() {
        return new ProfileState();
    }

    @Override
    protected void handleClick() {
        changeToNextProfile();
        mHighPerf.setAllowAnimation(true);
        mBattery.setAllowAnimation(true);
        mBalanced.setAllowAnimation(true);
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(BATTERY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(ProfileState state, Object arg) {
        state.visible = mPm.hasPowerProfiles();
        state.profile = arg == null ? getCurrentProfileIndex() : (Integer) arg;
        state.label = mEntries[state.profile];
        state.icon = getIconForState(state.profile);
        state.contentDescription = mDescriptionEntries[state.profile];
    }

    @Override
    protected String composeChangeAnnouncement() {
        return mAnnouncementEntries[getCurrentProfileIndex()];
    }

    private Icon getIconForState(int powerIndex) {
        switch (powerIndex) {
            case 0:
                return mHighPerf;

            case 1:
                return mBattery;

            case 2:
            default:
                return mBalanced;
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class PerformanceProfileObserver extends ContentObserver {
        public PerformanceProfileObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState(getCurrentProfileIndex());
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PERFORMANCE_PROFILE),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private int getCurrentProfileIndex() {
        int index = 0;
        String perfProfile = mPm.getPowerProfile();
        if (perfProfile == null) {
            perfProfile = mPerfProfileDefaultEntry;
        }

        int count = mPerfProfileValues.length;
        for (int i = 0; i < count; i++) {
            if (mPerfProfileValues[i].equals(perfProfile)) {
                index = i;
                break;
            }
        }

        return index;
    }

    private void changeToNextProfile() {
        int current = getCurrentProfileIndex() + 1;
        if (current >= mPerfProfileValues.length) {
            current = 0;
        }
        mPm.setPowerProfile(mPerfProfileValues[current]); // content observer will notify
    }

    public static class ProfileState extends QSTile.State {
        public int profile;

        @Override
        public boolean copyTo(State other) {
            final ProfileState o = (ProfileState) other;
            final boolean changed = profile != o.profile;
            return super.copyTo(other) || changed;
        }

        @Override
        protected StringBuilder toStringBuilder() {
            final StringBuilder rt = super.toStringBuilder();
            rt.insert(rt.length() - 1, ",profile=" + profile);
            return rt;
        }
    }
}
