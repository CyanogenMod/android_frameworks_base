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

import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: LiveDisplay mode switcher **/
public class LiveDisplayTile extends QSTile<LiveDisplayTile.LiveDisplayState> {

    private static final Intent LIVEDISPLAY_SETTINGS =
            new Intent("android.settings.LIVEDISPLAY_SETTINGS");

    private final LiveDisplayObserver mObserver;
    private final String[] mEntries;
    private final String[] mValues;
    private final int[] mEntryIconRes;

    private boolean mListening;

    private static final int MODE_OUTDOOR = 3;
    private static final int MODE_DAY = 4;

    private static final int OFF_TEMPERATURE = 6500;

    private int mDayTemperature;

    private final boolean mOutdoorModeAvailable;
    private final int mDefaultDayTemperature;

    public LiveDisplayTile(Host host) {
        super(host);

        Resources res = mContext.getResources();
        TypedArray typedArray = res.obtainTypedArray(R.array.live_display_drawables);
        mEntryIconRes = new int[typedArray.length()];
        for (int i = 0; i < mEntryIconRes.length; i++) {
            mEntryIconRes[i] = typedArray.getResourceId(i, 0);
        }
        typedArray.recycle();

        mEntries = res.getStringArray(com.android.internal.R.array.live_display_entries);
        mValues = res.getStringArray(com.android.internal.R.array.live_display_values);

        mOutdoorModeAvailable = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                -1, UserHandle.USER_CURRENT) > -1;

        mDefaultDayTemperature = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_dayColorTemperature);

        mObserver = new LiveDisplayObserver(mHandler);
        mObserver.startObserving();
    }

    @Override
    protected LiveDisplayState newTileState() {
        return new LiveDisplayState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening)
            return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    @Override
    protected void handleClick() {
        changeToNextMode();
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(LIVEDISPLAY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(LiveDisplayState state, Object arg) {
        state.visible = true;
        state.mode = arg == null ? getCurrentModeIndex() : (Integer) arg;
        state.label = mEntries[state.mode];
        state.icon = mContext.getDrawable(mEntryIconRes[state.mode]);
    }

    private int getCurrentModeIndex() {
        return ArrayUtils.indexOf(mValues,
                String.valueOf(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.DISPLAY_TEMPERATURE_MODE,
                        0, UserHandle.USER_CURRENT)));
    }

    private void changeToNextMode() {
        int next = getCurrentModeIndex() + 1;

        if (next >= mValues.length) {
            next = 0;
        }

        while (true) {
            // Skip outdoor mode if it's unsupported, and skip the day setting
            // if it's the same as the off setting
            if ((!mOutdoorModeAvailable &&
                    Integer.valueOf(mValues[next]) == MODE_OUTDOOR) ||
                    (mDayTemperature == OFF_TEMPERATURE &&
                    Integer.valueOf(mValues[next]) == MODE_DAY)) {
                next++;
                if (next >= mValues.length) {
                    next = 0;
                }
            } else {
                break;
            }
        }

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_MODE,
                Integer.valueOf(mValues[next]), UserHandle.USER_CURRENT);
    }

    private class LiveDisplayObserver extends ContentObserver {
        public LiveDisplayObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mDayTemperature = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.DISPLAY_TEMPERATURE_DAY,
                    mDefaultDayTemperature,
                    UserHandle.USER_CURRENT);
            refreshState(getCurrentModeIndex());
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_MODE),
                    false, this, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_DAY),
                    false, this, UserHandle.USER_ALL);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    public static class LiveDisplayState extends QSTile.State {
        public int mode;

        @Override
        public boolean copyTo(State other) {
            final LiveDisplayState o = (LiveDisplayState) other;
            final boolean changed = mode != o.mode;
            return super.copyTo(other) || changed;
        }

        @Override
        protected StringBuilder toStringBuilder() {
            final StringBuilder rt = super.toStringBuilder();
            rt.insert(rt.length() - 1, ",mode=" + mode);
            return rt;
        }
    }
}
