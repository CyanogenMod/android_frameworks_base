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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.InputStream;

public class ContactTile extends QuickSettingsTile {

    private static final String TAG = "ContactsTile";

    private AsyncTask<Void, Void, Pair<String, Drawable>> mInfoTask;

    private String mKey;
    private String mSetting;

    private Drawable mContactIcon = null;

    public ContactTile(Context context, QuickSettingsController qsc, String key) {
        super(context, qsc, R.layout.quick_settings_tile_user);
        mKey = key;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSetting != null && mSetting.length() > 0) {
                    Uri lookupUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, mSetting);
                    Uri res = ContactsContract.Contacts.lookupContact(
                            mContext.getContentResolver(), lookupUri);
                    Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                        mContext, v, res, ContactsContract.QuickContact.MODE_LARGE, null);
                    startSettingsActivity(intent);
                }
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mSetting != null && mSetting.length() > 0) {
                    Uri lookupUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, mSetting);
                    Uri res = ContactsContract.Contacts.lookupContact(
                            mContext.getContentResolver(), lookupUri);
                    Intent intent = new Intent(Intent.ACTION_VIEW, res);
                    startSettingsActivity(intent);
                }
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.TILE_CONTACT_ACTIONS), this);
    }

    private void updateSettings() {
        String setting = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.TILE_CONTACT_ACTIONS, UserHandle.USER_CURRENT);
        mSetting = null;
        if (setting != null && setting.contains(mKey)) {
            for (String action : setting.split("\\|")) {
                if (action.contains(mKey)) {
                    String[] split = action.split(TILE_CUSTOM_KEY);
                    mSetting = split[0];
                }
            }
        }
    }

    private void updateTile() {
        if (mInfoTask != null) {
            mInfoTask.cancel(false);
            mInfoTask = null;
        }

        if (mSetting != null && mSetting.length() > 0) {
            mInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
                @Override
                protected Pair<String, Drawable> doInBackground(Void... params) {
                    String name = null;
                    Drawable avatar = null;
                    Bitmap rawAvatar = null;
                    if (mSetting != null && mSetting.length() > 0) {
                        Uri lookupUri = Uri.withAppendedPath(
                                ContactsContract.Contacts.CONTENT_LOOKUP_URI, mSetting);
                        Uri res = ContactsContract.Contacts.lookupContact(
                                mContext.getContentResolver(), lookupUri);
                        String[] projection = new String[] {
                                ContactsContract.Contacts.DISPLAY_NAME,
                                ContactsContract.Contacts.PHOTO_URI,
                                ContactsContract.Contacts.LOOKUP_KEY
                        };

                        final Cursor cursor = mContext.getContentResolver().query(res, projection,
                                null, null, null);
                        if (cursor != null) {
                            try {
                                if (cursor.moveToFirst()) {
                                    name = cursor.getString(cursor.getColumnIndex(
                                            ContactsContract.Contacts.DISPLAY_NAME));
                                }
                            } finally {
                                cursor.close();
                            }
                        }
                        InputStream input = ContactsContract.Contacts.
                                openContactPhotoInputStream(
                                mContext.getContentResolver(), res, true);
                        if (input != null) {
                            rawAvatar = BitmapFactory.decodeStream(input);
                        }

                        if (rawAvatar != null) {
                            avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                        }
                    }
                    return new Pair<String, Drawable>(name, avatar);
                }

                @Override
                protected void onPostExecute(Pair<String, Drawable> result) {
                    super.onPostExecute(result);
                    mLabel = result.first;
                    mContactIcon = result.second;
                    updateQuickSettings();
                    mInfoTask = null;
                }
            };
            mInfoTask.execute();
        } else {
            mContactIcon = mContext.getResources().getDrawable(
                    R.drawable.ic_qs_default_user);
            mLabel = mContext.getString(R.string.quick_settings_contact_toggle);
        }
    }

    @Override
    void updateQuickSettings() {
        ImageView iv = (ImageView) mTile.findViewById(R.id.user_imageview);
        TextView tv = (TextView) mTile.findViewById(R.id.user_textview);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            if (mTileTextColor != -2) {
                tv.setTextColor(mTileTextColor);
            }
        }
        iv.setImageDrawable(mContactIcon);
    }

    @Override
    void onPostCreate() {
        updateSettings();
        updateResources();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateSettings();
        updateResources();
    }

}
