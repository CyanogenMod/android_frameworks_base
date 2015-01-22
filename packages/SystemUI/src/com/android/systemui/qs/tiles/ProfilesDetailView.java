/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.app.Profile;
import android.app.ProfileManager;
import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Layout for the Profiles detail in quick settings.
 */
public class ProfilesDetailView extends FrameLayout {

    private ListView mList;
    private ProfileManager mProfileManager;
    ProfileAdapter mAdapter;

    public ProfilesDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);
    }

    private class ProfileAdapter extends ArrayAdapter<Profile> {

        public ProfileAdapter(Context context) {
            super(context, R.layout.qs_detail_item_radio, mProfileManager.getProfiles());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup root = (ViewGroup) LayoutInflater.from(mContext).inflate(
                    R.layout.qs_detail_item_radio, parent, false);

            TextView label = (TextView) root.findViewById(android.R.id.title);
            RadioButton radioButton = (RadioButton) root.findViewById(R.id.radio_button);

            label.setEnabled(ProfilesDetailView.this.isEnabled());
            radioButton.setEnabled(ProfilesDetailView.this.isEnabled());

            Profile p = getItem(position);
            label.setText(p.getName());

            UUID activeUuid = mProfileManager.getActiveProfile().getUuid();
            radioButton.setChecked(p.getUuid().equals(activeUuid));

            return root;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mList != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        getParent().requestDisallowInterceptTouchEvent(true);
        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mList = (ListView) findViewById(android.R.id.list);
        mList.setAdapter(mAdapter = new ProfileAdapter(mContext));
        setEnabled(isEnabled());
    }
}
