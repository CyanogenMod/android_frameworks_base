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
import android.os.Handler;
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
    private Handler mHandler;
    private Drawable userAvatar;
    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    public UserTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc, R.layout.quick_settings_tile_user);
        mHandler = handler;
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                final UserManager um = UserManager.get(mContext);
                if (um.getUsers(true).size() > 1) {
                    // Since keyguard and systemui were merged into the same process to save
                    // memory, they share the same Looper and graphics context.  As a result,
                    // there's no way to allow concurrent animation while keyguard inflates.
                    // The workaround is to add a slight delay to allow the animation to finish.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                WindowManagerGlobal.getWindowManagerService().lockNow(null);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Couldn't show user switcher", e);
                            }
                        }
                    }, 400); // TODO: ideally this would be tied to the collapse of the panel
                } else {
                    final Cursor cursor = mContext.getContentResolver().query(
                            Profile.CONTENT_URI, null, null, null, null);
                    if (cursor.moveToNext() && !cursor.isNull(0)) {
                        Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                                mContext, v, ContactsContract.Profile.CONTENT_URI,
                                ContactsContract.QuickContact.MODE_LARGE, null);
                        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        intent.putExtra(INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                        startSettingsActivity(intent);
                    }
                    cursor.close();
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
        if (tv != null) {
            tv.setText(mLabel);
        }

        if (iv != null) {
            iv.setImageDrawable(userAvatar);
        }
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
                final UserManager um = UserManager.get(mContext);

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
                } else {
                    name = userName;
                    Bitmap rawAvatar = um.getUserIcon(userId);
                    if (rawAvatar != null) {
                        avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                    } else {
                        avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
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
