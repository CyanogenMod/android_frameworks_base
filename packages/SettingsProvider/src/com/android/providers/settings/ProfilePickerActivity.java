/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.providers.settings;

import android.app.Profile;
import android.app.ProfileManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The {@link ProfilePickerActivity} allows the user to choose one from all of the
 * available profiles.
 *
 * @see ProfileManager#ACTION_PROFILE_PICKER
 * @hide
 */
public final class ProfilePickerActivity extends AlertActivity implements
        DialogInterface.OnClickListener, AlertController.AlertParams.OnPrepareListViewListener {

    private static final String TAG = "ProfilePickerActivity";

    private static final String SAVE_CLICKED_POS = "clicked_pos";

    private ProfileManager mProfileManager;

    /** The position in the list of the 'None' item. */
    private int mNonePos = -1;

    /** The position in the list of the last clicked item. */
    private int mClickedPos = -1;

    /** Whether this list has the 'None' item. */
    private boolean mHasNoneItem;

    /** The UUID to place a checkmark next to. */
    private UUID mExistingUUID;

    private List<Profile> mProfiles;

    private DialogInterface.OnClickListener mProfileClickListener =
            new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Save the position of most recently clicked item
            mClickedPos = which;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (savedInstanceState != null) {
            mClickedPos = savedInstanceState.getInt(SAVE_CLICKED_POS, -1);
        }

        // Get whether to show the 'None' item
        mHasNoneItem = intent.getBooleanExtra(ProfileManager.EXTRA_PROFILE_SHOW_NONE, true);

        // Give the Activity so it can do managed queries
        mProfileManager = (ProfileManager) getSystemService(Context.PROFILE_SERVICE);
        mProfiles = Arrays.asList(mProfileManager.getProfiles());

        // Get the UUID whose list item should have a checkmark
        String uuid = intent.getStringExtra(ProfileManager.EXTRA_PROFILE_EXISTING_UUID);
        if (uuid == null) {
            mExistingUUID = null;
        } else {
            try {
                mExistingUUID = UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid existing UUID: " + uuid);
                mExistingUUID = ProfileManager.NO_PROFILE;
            }
        }

        final ArrayList<String> profileNames = new ArrayList<String>();
        for (Profile profile : mProfiles) {
            profileNames.add(profile.getName());
        }

        final AlertController.AlertParams p = mAlertParams;
        p.mAdapter = new ArrayAdapter<String>(this,
                com.android.internal.R.layout.select_dialog_singlechoice_holo, profileNames);
        p.mOnClickListener = mProfileClickListener;
        p.mIsSingleChoice = true;
        p.mPositiveButtonText = getString(com.android.internal.R.string.ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(com.android.internal.R.string.cancel);
        p.mPositiveButtonListener = this;
        p.mOnPrepareListViewListener = this;

        p.mTitle = intent.getCharSequenceExtra(ProfileManager.EXTRA_PROFILE_TITLE);
        if (p.mTitle == null) {
            p.mTitle = getString(com.android.internal.R.string.profile_picker_title);
        }

        setupAlert();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_CLICKED_POS, mClickedPos);
    }

    @Override
    public void onPrepareListView(ListView listView) {
        if (mHasNoneItem) {
            mNonePos = addNoneItem(listView);

            // The 'None' item should use a NO_PROFILE UUID
            if (mExistingUUID == null || mExistingUUID.equals(ProfileManager.NO_PROFILE)) {
                mClickedPos = mNonePos;
            }
        }

        // Has a valid UUID
        if (mClickedPos == -1 && mExistingUUID != null) {
            mClickedPos = getPositionForUUID(mExistingUUID);
        }
        if (mClickedPos == -1) {
            mClickedPos = getPositionForUUID(mProfileManager.getActiveProfile().getUuid());
        }

        // Put a checkmark next to an item.
        mAlertParams.mCheckedItem = mClickedPos;
    }

    private int getPositionForUUID(UUID uuid) {
        int count = mProfiles.size();
        for (int i = 0; i < count; i++) {
            if (mProfiles.get(i).getUuid().equals(uuid)) {
                return mNonePos + i + 1;
            }
        }
        return -1;
    }

    /**
     * Adds a static item to the top of the list. A static item is one that is not from the
     * ProfileManager.
     *
     * @param listView The ListView to add to.
     * @param textResId The resource ID of the text for the item.
     * @return The position of the inserted item.
     */
    private int addStaticItem(ListView listView, int textResId) {
        TextView textView = (TextView) getLayoutInflater().inflate(
                com.android.internal.R.layout.select_dialog_singlechoice_holo, listView, false);
        textView.setText(textResId);
        listView.addHeaderView(textView);
        return listView.getHeaderViewsCount() - 1;
    }

    private int addNoneItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.profile_none);
    }

    /*
     * On click of Ok/Cancel buttons
     */
    public void onClick(DialogInterface dialog, int which) {
        boolean positiveResult = which == DialogInterface.BUTTON_POSITIVE;

        if (positiveResult) {
            Intent resultIntent = new Intent();
            UUID uuid = ProfileManager.NO_PROFILE;

            if (mClickedPos != mNonePos) {
                int pos = mHasNoneItem ? mClickedPos - 1 : mClickedPos;
                if (pos >= 0 && pos < mProfiles.size()) {
                    uuid = mProfiles.get(pos).getUuid();
                }
            }

            resultIntent.putExtra(ProfileManager.EXTRA_PROFILE_PICKED_UUID, uuid.toString());
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_CANCELED);
        }

        finish();
    }
}
