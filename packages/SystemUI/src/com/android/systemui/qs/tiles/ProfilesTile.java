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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
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
import com.android.systemui.statusbar.policy.KeyguardMonitor;

import cyanogenmod.app.Profile;
import cyanogenmod.app.ProfileManager;
import cyanogenmod.app.StatusBarPanelCustomTile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProfilesTile extends QSTile<QSTile.State> implements KeyguardMonitor.Callback {

    private static final Intent PROFILES_SETTINGS =
            new Intent("android.settings.PROFILES_SETTINGS");

    private boolean mListening;
    private ProfilesObserver mObserver;
    private ProfileManager mProfileManager;
    private QSDetailItemsList mDetails;
    private ProfileAdapter mAdapter;
    private KeyguardMonitor mKeyguardMonitor;

    public ProfilesTile(Host host) {
        super(host);
        mProfileManager = ProfileManager.getInstance(mContext);
        mObserver = new ProfilesObserver(mHandler);
        mKeyguardMonitor = host.getKeyguardMonitor();
        mKeyguardMonitor.addCallback(this);
    }

    @Override
    public boolean hasSensitiveData() {
        return true;
    }

    @Override
    protected void handleDestroy() {
        mKeyguardMonitor.removeCallback(this);
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
    protected void handleLongClick() {
        mHost.startSettingsActivity(PROFILES_SETTINGS);
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.visible = true;
        state.enabled = !mKeyguardMonitor.isShowing() || !mKeyguardMonitor.isSecure();
        state.icon = ResourceIcon.get(R.drawable.ic_qs_system_profiles);
        if (profilesEnabled()) {
            state.label = mProfileManager.getActiveProfile().getName();
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_profiles, state.label);
        } else {
            state.label = mContext.getString(R.string.quick_settings_profiles_disabled);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_profiles_off);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (profilesEnabled()) {
            return mContext.getString(R.string.accessibility_quick_settings_profiles_changed,
                    mState.label);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_profiles_changed_off);
        }
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
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ProfileManager.INTENT_ACTION_PROFILE_SELECTED);
            filter.addAction(ProfileManager.INTENT_ACTION_PROFILE_UPDATED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mObserver.endObserving();
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new ProfileDetailAdapter();
    }

    @Override
    public void onKeyguardChanged() {
        refreshState();
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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ProfileManager.INTENT_ACTION_PROFILE_SELECTED.equals(intent.getAction())
                    || ProfileManager.INTENT_ACTION_PROFILE_UPDATED.equals(intent.getAction())) {
                refreshState();
            }
        }
    };

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
            return PROFILES_SETTINGS;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
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
