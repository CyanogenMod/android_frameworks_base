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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

public class PerfProfileTile extends QSTile<PerfProfileTile.ProfileState> {

    private int[] mEntryIconRes;
    private String[] mEntries;
    private String[] mPerfProfileValues;
    private String mPerfProfileDefaultEntry;

    private final PowerManager mPm;
    private boolean mListening;

    private PerformanceProfileObserver mObserver;

    public PerfProfileTile(Host host) {
        super(host);
        mObserver = new PerformanceProfileObserver(mHandler);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        Resources res = mContext.getResources();
        TypedArray typedArray = res.obtainTypedArray(R.array.perf_profile_drawables);
        mEntryIconRes = new int[typedArray.length()];
        for (int i = 0; i < mEntryIconRes.length; i++) {
            mEntryIconRes[i] = typedArray.getResourceId(i, 0);
        }
        typedArray.recycle();

        mPerfProfileDefaultEntry = mPm.getDefaultPowerProfile();
        mPerfProfileValues = res.getStringArray(com.android.internal.R.array.perf_profile_values);

        mEntries = res.getStringArray(com.android.internal.R.array.perf_profile_entries);
    }

    @Override
    protected ProfileState newTileState() {
        return new ProfileState();
    }

    @Override
    protected void handleClick() {
        changeToNextProfile();
    }

    @Override
    protected void handleUpdateState(ProfileState state, Object arg) {
        state.visible = true;
        state.profile = arg == null ? getCurrentProfileIndex() : (Integer) arg;
        state.label = mEntries[state.profile];
        state.icon = mContext.getDrawable(mEntryIconRes[state.profile]);
        if (state.icon instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) getState().icon).start();
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
