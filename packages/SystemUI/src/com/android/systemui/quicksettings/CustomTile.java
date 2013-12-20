/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import static com.android.internal.util.slim.QSConstants.TILE_CUSTOM_KEY;
import static com.android.internal.util.slim.QSConstants.TILE_CUSTOM_DELIMITER;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.util.Log;
import android.view.View;

import com.android.internal.util.slim.AppHelper;
import com.android.internal.util.slim.SlimActions;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.File;
import java.net.URISyntaxException;

public class CustomTile extends QuickSettingsTile {

    private static final String TAG = "CustomTile";

    private static final String KEY_TOGGLE_STATE = "custom_toggle_state";

    private String mKey;

    private String[] mClickActions = new String[5];
    private String[] mLongActions = new String[5];
    private  String[] mActionStrings = new String[5];
    private  String[] mCustomIcon = new String[5];

    private boolean mCollapse = false;
    private boolean mMatchState = false;

    private int mNumberOfActions = 0;
    private int mState = 0;
    private int mStateMatched = 0;

    SharedPreferences mShared;

    public CustomTile(Context context, QuickSettingsController qsc, String key) {
        super(context, qsc);
        mKey = key;
        mShared = mContext.getSharedPreferences(KEY_TOGGLE_STATE, Context.MODE_PRIVATE);
        // This will naver change and will filter itself out if an action exists
        mDrawable = R.drawable.ic_qs_settings;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performClickAction();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                SlimActions.processActionWithOptions(
                        mContext, mLongActions[mState], false, mCollapse);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.CUSTOM_TOGGLE_ACTIONS), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.CUSTOM_TOGGLE_EXTRAS), this);
    }

    private void updateSettings() {
        String clickHolder;
        String longHolder;
        String iconHolder;
        int actions = 0;
        String setting = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.CUSTOM_TOGGLE_ACTIONS, UserHandle.USER_CURRENT);

        String extras = extractActionFromString(
                Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.CUSTOM_TOGGLE_EXTRAS, UserHandle.USER_CURRENT));
        int extraValue = 0;
        if (extras != null) {
            extraValue = Integer.parseInt(extras);
        }
        saveExtras(extraValue);

        if (setting != null) {
            for (int i = 0; i < 5; i++) {
                clickHolder = " ";
                longHolder = " ";
                iconHolder = " ";
                if (setting.contains(mKey)) {
                    for (String action : setting.split("\\|")) {
                        if (action.contains(mKey) && action.endsWith(Integer.toString(i))) {
                            String[] split = action.split(TILE_CUSTOM_KEY);
                            String[] returned = split[0].split(TILE_CUSTOM_DELIMITER);
                            clickHolder = returned[0];
                            longHolder = returned[1];
                            iconHolder = returned[2];
                        }
                    }
                }

                mClickActions[actions] = clickHolder.equals(" ") ? null : clickHolder;
                mLongActions[actions] = longHolder.equals(" ") ? null : longHolder;
                mActionStrings[actions] = returnFriendlyName(
                        mClickActions[actions] == null
                        ? mLongActions[actions]
                        : mClickActions[actions]);
                mCustomIcon[actions] = iconHolder.equals(" ") ? null : iconHolder;
                if (!clickHolder.equals(" ") || !longHolder.equals(" ")) {
                    actions++;
                }
            }
        }
        mNumberOfActions = actions;
        mState = mShared.getInt("state" + mKey, 0);

        // User deleted the state they're currently in
        if (mState > mNumberOfActions - 1) {
            mState = 0;
            mShared.edit().putInt("state" + mKey, mState).commit();
        }

        updateResources();
    }

    private void saveExtras(int value) {
        switch (value) {
            case 0:
                mCollapse = false;
                mMatchState = false;
                break;
            case 1:
                mCollapse = true;
                mMatchState = false;
                break;
            case 2:
                mCollapse = false;
                mMatchState = true;
                break;
            case 3:
                mCollapse = true;
                mMatchState = true;
        }
    }

    private String returnFriendlyName(String uri) {
        if (uri != null) {
            return AppHelper.getFriendlyNameForUri(
                    mContext, mContext.getPackageManager(), uri);
        }
        return null;
    }

    private void performClickAction() {
        if (mState < mNumberOfActions - 1) {
            mState++;
            mStateMatched = mState - 1;
        } else {
            mState = 0;
            mStateMatched = mNumberOfActions - 1;
        }

        mShared.edit().putInt("state" + mKey, mState).commit();

        if (mMatchState && mNumberOfActions >= 1) {
            SlimActions.processActionWithOptions(
                    mContext, mClickActions[mStateMatched], false, mCollapse);
        } else {
            SlimActions.processActionWithOptions(
                    mContext, mClickActions[mState], false, mCollapse);
        }

        updateResources();
    }

    private synchronized void updateTile() {
        mRealDrawable = null;
        if (mNumberOfActions != 0) {
            if (mCustomIcon[mState] != null && mCustomIcon[mState].length() > 0) {
                File f = new File(Uri.parse(mCustomIcon[mState]).getPath());
                if (f.exists()) {
                    mRealDrawable = new BitmapDrawable(
                            mContext.getResources(), f.getAbsolutePath());
                }
            } else {
                try {
                    if (mClickActions[mState] != null) {
                        mRealDrawable = mContext.getPackageManager().getActivityIcon(
                                Intent.parseUri(mClickActions[mState], 0));
                    } else if (mLongActions[mState] != null) {
                        mRealDrawable = mContext.getPackageManager().getActivityIcon(
                                Intent.parseUri(mLongActions[mState], 0));
                    }
                } catch (NameNotFoundException e) {
                    mRealDrawable = null;
                    Log.e(TAG, "Invalid Package", e);
                } catch (URISyntaxException ex) {
                    mRealDrawable = null;
                    Log.e(TAG, "Invalid Package", ex);
                }
            }
        }
        mLabel = mActionStrings[mState] != null
                ? mActionStrings[mState]
                : mContext.getString(R.string.quick_settings_custom_toggle);
    }

    private String extractActionFromString(String actions) {
        String returnAction = null;
        if (actions != null && actions.contains(mKey)) {
            for (String action : actions.split("\\|")) {
                if(action.contains(mKey)) {
                    String[] split = action.split(TILE_CUSTOM_KEY);
                    returnAction = split[0];
                }
            }
        }
        return returnAction;
    }

    @Override
    void onPostCreate() {
        updateSettings();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateSettings();
    }

}
