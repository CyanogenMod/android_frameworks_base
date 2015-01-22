package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
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
        state.iconId = mEntryIconRes[state.profile];
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
