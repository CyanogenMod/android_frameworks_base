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
import android.widget.ListView;

import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    private class ProfileAdapter extends ArrayAdapter<Profile> {
        public ProfileAdapter(Context context, List<Profile> profiles) {
            super(context, android.R.layout.simple_list_item_single_choice, profiles);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            CheckedTextView label = (CheckedTextView) inflater.inflate(
                    android.R.layout.simple_list_item_single_choice, parent, false);

            Profile p = getItem(position);
            label.setText(p.getName());

            return label;
        }
    }

    public class ProfileDetailAdapter implements DetailAdapter, AdapterView.OnItemClickListener {

        private List<Profile> mProfilesList;

        @Override
        public int getTitle() {
            return R.string.quick_settings_profiles_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            boolean enabled = profilesEnabled();
            rebuildProfilesList(enabled);
            return enabled;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mDetails = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            mProfilesList = new ArrayList<>();
            mDetails.setAdapter(mAdapter = new ProfileAdapter(context, mProfilesList));

            final ListView list = mDetails.getListView();
            list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            list.setOnItemClickListener(this);

            mDetails.setEmptyState(R.drawable.ic_qs_system_profiles,
                    R.string.quick_settings_profiles_disabled);

            return mDetails;
        }

        private void rebuildProfilesList(boolean populate) {
            mProfilesList.clear();
            if (populate) {
                int selected = -1;

                final Profile[] profiles = mProfileManager.getProfiles();
                final Profile activeProfile = mProfileManager.getActiveProfile();
                final UUID activeUuid = activeProfile != null ? activeProfile.getUuid() : null;

                for (int i = 0; i < profiles.length; i++) {
                    mProfilesList.add(profiles[i]);
                    if (activeUuid != null && activeUuid.equals(profiles[i].getUuid())) {
                        selected = i;
                    }
                }
                mDetails.getListView().setItemChecked(selected, true);
            }
            mAdapter.notifyDataSetChanged();
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
            rebuildProfilesList(state);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Profile selected = (Profile) parent.getItemAtPosition(position);
            mProfileManager.setActiveProfile(selected.getUuid());
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
