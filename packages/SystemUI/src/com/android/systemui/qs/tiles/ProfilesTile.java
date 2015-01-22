package com.android.systemui.qs.tiles;


import android.app.ProfileManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

public class ProfilesTile extends QSTile<QSTile.State> {

    private boolean mListening;
    private ProfilesObserver mObserver;
    private ProfileManager mProfileManager;

    public ProfilesTile(Host host) {
        super(host);
        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);
        mObserver = new ProfilesObserver(mHandler);
    }

    @Override
    protected State newTileState() {
        return new State();
    }

    @Override
    protected void handleClick() {
        showDetail(true);
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.visible = true;
        state.label = profilesEnabled() ? mProfileManager.getActiveProfile().getName()
                : mContext.getString(R.string.quick_settings_profiles_disabled);
        state.iconId = R.drawable.ic_qs_system_profiles;
    }

    private boolean profilesEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
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

    @Override
    public DetailAdapter getDetailAdapter() {
        return new ProfileDetailAdapter();
    }

    public class ProfileDetailAdapter implements DetailAdapter {
        ProfilesDetailView mDetailView;

        @Override
        public int getTitle() {
            return R.string.quick_settings_profiles_title;
        }

        @Override
        public Boolean getToggleState() {
            return profilesEnabled();
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mDetailView = (ProfilesDetailView) LayoutInflater.from(context)
                    .inflate(R.layout.profiles_detail_view, parent, false);
            mDetailView.setEnabled(profilesEnabled());
            return mDetailView;
        }

        @Override
        public Intent getSettingsIntent() {
            return new Intent("com.android.settings.PROFILES_SETTINGS");
        }

        @Override
        public void setToggleState(boolean state) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SYSTEM_PROFILES_ENABLED, state ? 1 : 0);
            fireToggleStateChanged(state);
            mDetailView.setEnabled(state);
        }
    }

    private class ProfilesObserver extends ContentObserver {
        public ProfilesObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SYSTEM_PROFILES_ENABLED),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }
}
