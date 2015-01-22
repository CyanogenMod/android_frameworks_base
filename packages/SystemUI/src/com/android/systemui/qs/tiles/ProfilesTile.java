package com.android.systemui.qs.tiles;


import android.app.Profile;
import android.app.ProfileManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.List;

public class ProfilesTile extends QSTile<QSTile.State> {

    private boolean mListening;
    private ProfilesObserver mObserver;
    private ProfileManager mProfileManager;
    private QSDetailItemsList mDetails;
    private ProfileAdapter mAdapter;

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


    private class ProfileAdapter extends ArrayAdapter<ProfileWrapper> {

        public ProfileAdapter(Context context, List<ProfileWrapper> profiles) {
            super(context, R.layout.qs_detail_item_radio, profiles);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckedTextView label = (CheckedTextView) LayoutInflater.from(mContext).inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);

            ProfileWrapper p = getItem(position);
            label.setText(p.getProfile().getName());

            label.setTextColor(mContext.getResources().getColor(mDetails.getListView().isEnabled()
                    ? R.color.qs_tile_text : R.color.detail_list_item_disablable));

            return label;
        }
    }

    public class ProfileDetailAdapter implements DetailAdapter {

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
            mDetails = QSDetailItemsList.convertOrInflate(context, convertView, parent);

            final Profile[] profiles = mProfileManager.getProfiles();
            List<ProfileWrapper> profilesList = new ArrayList<>();
            for (int i = 0; i < profiles.length; i++) {
                profilesList.add(new ProfileWrapper(profiles[i]));
            }

            mDetails.setAdapter(mAdapter = new ProfileAdapter(context, profilesList));
            mDetails.getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            mDetails.getListView().setDivider(null);
            mDetails.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ProfileWrapper selected = (ProfileWrapper) parent.getItemAtPosition(position);
                    mProfileManager.setActiveProfile(selected.getProfile().getUuid());
                }
            });
            int pos = mAdapter.getPosition(new ProfileWrapper(mProfileManager.getActiveProfile()));
            mDetails.getListView().setItemChecked(pos, true);
            return mDetails;
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
            mDetails.getListView().setEnabled(state);
            mAdapter.notifyDataSetChanged();
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

    private static class ProfileWrapper {
        public Profile mProfile;

        public ProfileWrapper(Profile profile) {
            mProfile = profile;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ProfileWrapper) {
                return mProfile.getUuid().equals(((ProfileWrapper) o).mProfile.getUuid());
            }
            return false;
        }

        public Profile getProfile() {
            return mProfile;
        }
    }
}
