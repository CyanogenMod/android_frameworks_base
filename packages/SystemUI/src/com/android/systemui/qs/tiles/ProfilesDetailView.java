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
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Layout for the Profiles detail in quick settings.
 */
public class ProfilesDetailView extends LinearLayout {

    private ProfileManager mProfileManager;
    private Map<Integer, Profile> mProfileMap = new HashMap<>();
    private RadioGroup mRadioGroup;

    public ProfilesDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mProfileManager = (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mRadioGroup != null) {
            UUID activeUuid = mProfileManager.getActiveProfile().getUuid();

            for (int i = 0; i < mRadioGroup.getChildCount(); i++) {
                View child = mRadioGroup.getChildAt(i);
                child.setEnabled(enabled);

                if (enabled
                        && child instanceof RadioButton
                        && mProfileMap.containsKey(child.getId())
                        && activeUuid.equals(mProfileMap.get(child.getId()).getUuid())) {
                    ((RadioButton)child).setChecked(true);
                }
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRadioGroup = (RadioGroup) findViewById(R.id.radiogroup_profiles);

        Profile[] profiles = mProfileManager.getProfiles();

        int leftPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.detail_radio_group_padding_left);
        int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.detail_radio_group_padding);

        for (int i = 0; i < profiles.length; i++) {
            int viewId = View.generateViewId();
            mProfileMap.put(viewId, profiles[i]);

            RadioButton btn = new RadioButton(mContext);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            btn.setText(profiles[i].getName());
            btn.setId(viewId);
            btn.setPadding(leftPadding, padding, padding, padding);

            mRadioGroup.addView(btn);
        }

        mRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Profile profile = mProfileMap.get((Integer) checkedId);
                if (profile != null) {
                    mProfileManager.setActiveProfile(profile.getUuid());
                }
            }
        });

        setEnabled(isEnabled());
    }
}
