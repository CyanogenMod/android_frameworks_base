/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.database.ContentObserver;
import android.hardware.camera2.utils.ArrayUtils;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.qs.QSTile;

/** Quick settings tile: LiveDisplay mode switcher **/
public class LiveDisplayTile extends QSTile<LiveDisplayTile.LiveDisplayState> {
    private static final Intent DISPLAY_SETTINGS = new Intent(Settings.ACTION_DISPLAY_SETTINGS);

    private final LiveDisplayObserver mObserver;
    private final String[] mEntries;
    private final String[] mValues;

    private boolean mListening;

    public LiveDisplayTile(Host host) {
        super(host);

        mObserver = new LiveDisplayObserver(mHandler);
        mObserver.startObserving();

        mEntries = mContext.getResources().getStringArray(
                com.android.internal.R.array.live_display_entries);
        mValues = mContext.getResources().getStringArray(
                com.android.internal.R.array.live_display_values);
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
        mHost.startSettingsActivity(DISPLAY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(LiveDisplayState state, Object arg) {
        state.visible = true;
        state.mode = arg == null ? getCurrentModeIndex() : (Integer) arg;
        state.label = mEntries[state.mode];
        // state.icon = mContext.getDrawable(mEntryIconRes[state.mode]);
    }

    private int getCurrentModeIndex() {
        return ArrayUtils.getArrayIndex(mValues,
                String.valueOf(Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.DISPLAY_TEMPERATURE_MODE,
                        0, UserHandle.USER_CURRENT)));
    }

    private void changeToNextMode() {
        int next = getCurrentModeIndex() + 1;
        if (next >= mValues.length) {
            next = 0;
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
            refreshState(getCurrentModeIndex());
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_MODE),
                    false, this);
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
