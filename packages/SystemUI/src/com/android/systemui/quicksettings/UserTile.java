package com.android.systemui.quicksettings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class UserTile extends QuickSettingsTile {

    private static final String TAG = "UserTile";
    private static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";
    private Drawable userAvatar;
    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    public UserTile(Context context, QuickSettingsController qsc) {
        super(context, qsc, R.layout.quick_settings_tile_user);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                int numUsers = um.getUsers(true).size();
                if (numUsers <= 1) {
                    final Cursor cursor = mContext.getContentResolver().query(
                            Profile.CONTENT_URI, null, null, null, null);
                    if (cursor.moveToNext() && !cursor.isNull(0)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Profile.CONTENT_URI);
                        startSettingsActivity(intent);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        intent.putExtra(INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                        startSettingsActivity(intent);
                    }
                    cursor.close();
                } else {
                    try {
                        WindowManagerGlobal.getWindowManagerService().lockNow(
                                null);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Couldn't show user switcher", e);
                    }
                }
            }
        };
        qsc.registerAction(Intent.ACTION_USER_SWITCHED, this);
        qsc.registerAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        queryForUserInformation();
    }

    @Override
    void onPostCreate() {
        queryForUserInformation();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        queryForUserInformation();
    }

    @Override
    void updateQuickSettings() {
        ImageView iv = (ImageView) mTile.findViewById(R.id.user_imageview);
        TextView tv = (TextView) mTile.findViewById(R.id.user_textview);
        tv.setText(mLabel);
        iv.setImageDrawable(userAvatar);
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                try {
                    // The system needs some time to change the picture, if we try to load it when we receive the broadcast, we will load the old one
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);

                String name = null;
                Drawable avatar = null;
                String id = null;
                // If it's a single-user device, get the profile name, since the nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {Phone.PHOTO_FILE_ID, Phone.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                                id = cursor.getString(cursor.getColumnIndex(Phone.PHOTO_FILE_ID));
                            }
                        } finally {
                            cursor.close();
                        }
                        // Fall back to the UserManager nickname if we can't read the name from the local
                        // profile below.
                        if (name == null) {
                            avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                            name = mContext.getResources().getString(com.android.internal.R.string.owner_name);
                        } else {
                            Bitmap rawAvatar = null;
                            InputStream is = null;
                            try {
                                Uri.Builder uriBuilder = ContactsContract.DisplayPhoto.CONTENT_URI.buildUpon();
                                uriBuilder.appendPath(id);
                                is = mContext.getContentResolver().openInputStream(uriBuilder.build());
                                rawAvatar = BitmapFactory.decodeStream(is);
                                avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                            } catch (FileNotFoundException e) {
                                avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                            } finally {
                                if (is != null) {
                                    try {
                                        is.close();
                                    } catch (IOException e) {
                                    }
                                }
                            }
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    void setUserTileInfo(String name, Drawable avatar) {
        mLabel = name;
        userAvatar = avatar;
        updateQuickSettings();
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
    }

}
