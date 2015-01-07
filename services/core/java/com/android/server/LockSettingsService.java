/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;

import static android.content.Context.USER_SERVICE;
import static android.Manifest.permission.READ_PROFILE;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.IMountService;
import android.os.ServiceManager;
import android.os.storage.StorageManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.security.KeyStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.internal.widget.ILockSettings;
import com.android.internal.widget.ILockSettingsObserver;
import com.android.internal.widget.LockPatternUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps the lock pattern/password data and related settings for each user.
 * Used by LockPatternUtils. Needs to be a service because Settings app also needs
 * to be able to save lockscreen information for secondary users.
 * @hide
 */
public class LockSettingsService extends ILockSettings.Stub {

    private static final String PERMISSION = "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE";

    private static final String SYSTEM_DEBUGGABLE = "ro.debuggable";

    private final DatabaseHelper mOpenHelper;
    private static final String TAG = "LockSettingsService";

    private static final String TABLE = "locksettings";
    private static final String COLUMN_KEY = "name";
    private static final String COLUMN_USERID = "user";
    private static final String COLUMN_VALUE = "value";

    private static final String[] COLUMNS_FOR_QUERY = {
        COLUMN_VALUE
    };

    private static final String SYSTEM_DIRECTORY = "/system/";
    private static final String LOCK_PATTERN_FILE = "gesture.key";
    private static final String LOCK_PASSWORD_FILE = "password.key";

    private final Context mContext;
    private LockPatternUtils mLockPatternUtils;
    private boolean mFirstCallToVold;

    private final ArrayList<LockSettingsObserver> mObservers = new ArrayList<>();

    public LockSettingsService(Context context) {
        mContext = context;
        // Open the database
        mOpenHelper = new DatabaseHelper(mContext);

        mLockPatternUtils = new LockPatternUtils(context);
        mFirstCallToVold = true;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        mContext.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_ADDED.equals(intent.getAction())) {
                final int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                final int userSysUid = UserHandle.getUid(userHandle, Process.SYSTEM_UID);
                final KeyStore ks = KeyStore.getInstance();

                // Clear up keystore in case anything was left behind by previous users
                ks.resetUid(userSysUid);

                // If this user has a parent, sync with its keystore password
                final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
                final UserInfo parentInfo = um.getProfileParent(userHandle);
                if (parentInfo != null) {
                    final int parentSysUid = UserHandle.getUid(parentInfo.id, Process.SYSTEM_UID);
                    ks.syncUid(parentSysUid, userSysUid);
                }
            }
        }
    };

    public void systemReady() {
        migrateOldData();
    }

    private void migrateOldData() {
        try {
            // These Settings moved before multi-user was enabled, so we only have to do it for the
            // root user.
            if (getString("migrated", null, 0) == null) {
                final ContentResolver cr = mContext.getContentResolver();
                for (String validSetting : VALID_SETTINGS) {
                    String value = Settings.Secure.getString(cr, validSetting);
                    if (value != null) {
                        setString(validSetting, value, 0);
                    }
                }
                // No need to move the password / pattern files. They're already in the right place.
                setString("migrated", "true", 0);
                Slog.i(TAG, "Migrated lock settings to new location");
            }

            // These Settings changed after multi-user was enabled, hence need to be moved per user.
            if (getString("migrated_user_specific", null, 0) == null) {
                final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
                final ContentResolver cr = mContext.getContentResolver();
                List<UserInfo> users = um.getUsers();
                for (int user = 0; user < users.size(); user++) {
                    // Migrate owner info
                    final int userId = users.get(user).id;
                    final String OWNER_INFO = Secure.LOCK_SCREEN_OWNER_INFO;
                    String ownerInfo = Settings.Secure.getStringForUser(cr, OWNER_INFO, userId);
                    if (ownerInfo != null) {
                        setString(OWNER_INFO, ownerInfo, userId);
                        Settings.Secure.putStringForUser(cr, ownerInfo, "", userId);
                    }

                    // Migrate owner info enabled.  Note there was a bug where older platforms only
                    // stored this value if the checkbox was toggled at least once. The code detects
                    // this case by handling the exception.
                    final String OWNER_INFO_ENABLED = Secure.LOCK_SCREEN_OWNER_INFO_ENABLED;
                    boolean enabled;
                    try {
                        int ivalue = Settings.Secure.getIntForUser(cr, OWNER_INFO_ENABLED, userId);
                        enabled = ivalue != 0;
                        setLong(OWNER_INFO_ENABLED, enabled ? 1 : 0, userId);
                    } catch (SettingNotFoundException e) {
                        // Setting was never stored. Store it if the string is not empty.
                        if (!TextUtils.isEmpty(ownerInfo)) {
                            setLong(OWNER_INFO_ENABLED, 1, userId);
                        }
                    }
                    Settings.Secure.putIntForUser(cr, OWNER_INFO_ENABLED, 0, userId);
                }
                // No need to move the password / pattern files. They're already in the right place.
                setString("migrated_user_specific", "true", 0);
                Slog.i(TAG, "Migrated per-user lock settings to new location");
            }
        } catch (RemoteException re) {
            Slog.e(TAG, "Unable to migrate old data", re);
        }
        migrateOldCMData();
    }

    private void migrateOldCMData() {
        try {
            // These Settings moved before multi-user was enabled, so we only have to do it for the
            // root user.
            if (getString("migrated_cm", null, 0) == null) {
                final ContentResolver cr = mContext.getContentResolver();

                // lockscreen quick unlock
                String cmSetting = "lockscreen_quick_unlock_control";
                String value = Settings.System.getString(cr, cmSetting);
                if (value != null) {
                    setString(LockPatternUtils.LOCKSCREEN_QUICK_UNLOCK_CONTROL, value, 0);
                }

                setString("migrated_cm", "true", 0);
                Slog.i(TAG, "Migrated CM lock settings to new location");
            }
        } catch (RemoteException re) {
            Slog.e(TAG, "Unable to migrate old CM data", re);
        }
    }

    private final void checkWritePermission(int userId) {
        mContext.enforceCallingOrSelfPermission(PERMISSION, "LockSettingsWrite");
    }

    private final void checkPasswordReadPermission(int userId) {
        mContext.enforceCallingOrSelfPermission(PERMISSION, "LockSettingsRead");
    }

    private final void checkReadPermission(String requestedKey, int userId) {
        final int callingUid = Binder.getCallingUid();
        for (int i = 0; i < READ_PROFILE_PROTECTED_SETTINGS.length; i++) {
            String key = READ_PROFILE_PROTECTED_SETTINGS[i];
            if (key.equals(requestedKey) && mContext.checkCallingOrSelfPermission(READ_PROFILE)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("uid=" + callingUid
                        + " needs permission " + READ_PROFILE + " to read "
                        + requestedKey + " for user " + userId);
            }
        }
    }

    @Override
    public void setBoolean(String key, boolean value, int userId) throws RemoteException {
        checkWritePermission(userId);

        writeToDb(key, value ? "1" : "0", userId);
    }

    @Override
    public void setLong(String key, long value, int userId) throws RemoteException {
        checkWritePermission(userId);

        writeToDb(key, Long.toString(value), userId);
    }

    @Override
    public void setString(String key, String value, int userId) throws RemoteException {
        checkWritePermission(userId);

        writeToDb(key, value, userId);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue, int userId) throws RemoteException {
        checkReadPermission(key, userId);

        String value = readFromDb(key, null, userId);
        return TextUtils.isEmpty(value) ?
                defaultValue : (value.equals("1") || value.equals("true"));
    }

    @Override
    public long getLong(String key, long defaultValue, int userId) throws RemoteException {
        checkReadPermission(key, userId);

        String value = readFromDb(key, null, userId);
        return TextUtils.isEmpty(value) ? defaultValue : Long.parseLong(value);
    }

    @Override
    public String getString(String key, String defaultValue, int userId) throws RemoteException {
        checkReadPermission(key, userId);

        return readFromDb(key, defaultValue, userId);
    }

    @Override
    public void registerObserver(ILockSettingsObserver remote) throws RemoteException {
        synchronized (mObservers) {
            for (int i = 0; i < mObservers.size(); i++) {
                if (mObservers.get(i).remote.asBinder() == remote.asBinder()) {
                    boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
                    if (isDebuggable) {
                        throw new IllegalStateException("Observer was already registered.");
                    } else {
                        Log.e(TAG, "Observer was already registered.");
                        return;
                    }
                }
            }
            LockSettingsObserver o = new LockSettingsObserver();
            o.remote = remote;
            o.remote.asBinder().linkToDeath(o, 0);
            mObservers.add(o);
        }
    }

    @Override
    public void unregisterObserver(ILockSettingsObserver remote) throws RemoteException {
        synchronized (mObservers) {
            for (int i = 0; i < mObservers.size(); i++) {
                if (mObservers.get(i).remote.asBinder() == remote.asBinder()) {
                    mObservers.remove(i);
                    return;
                }
            }
        }
    }

    public void notifyObservers(String key, int userId) {
        synchronized (mObservers) {
            for (int i = 0; i < mObservers.size(); i++) {
                try {
                    mObservers.get(i).remote.onLockSettingChanged(key, userId);
                } catch (RemoteException e) {
                    // The stack trace is not really helpful here.
                    Log.e(TAG, "Failed to notify ILockSettingsObserver: " + e);
                }
            }
        }
    }

    private int getUserParentOrSelfId(int userId) {
        if (userId != 0) {
            final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
            final UserInfo pi = um.getProfileParent(userId);
            if (pi != null) {
                return pi.id;
            }
        }
        return userId;
    }

    private String getLockPatternFilename(int userId) {
        String dataSystemDirectory =
                android.os.Environment.getDataDirectory().getAbsolutePath() +
                SYSTEM_DIRECTORY;
        userId = getUserParentOrSelfId(userId);
        if (userId == 0) {
            // Leave it in the same place for user 0
            return dataSystemDirectory + LOCK_PATTERN_FILE;
        } else {
            return  new File(Environment.getUserSystemDirectory(userId), LOCK_PATTERN_FILE)
                    .getAbsolutePath();
        }
    }

    private String getLockPasswordFilename(int userId) {
        userId = getUserParentOrSelfId(userId);
        String dataSystemDirectory =
                android.os.Environment.getDataDirectory().getAbsolutePath() +
                SYSTEM_DIRECTORY;
        if (userId == 0) {
            // Leave it in the same place for user 0
            return dataSystemDirectory + LOCK_PASSWORD_FILE;
        } else {
            return new File(Environment.getUserSystemDirectory(userId), LOCK_PASSWORD_FILE)
                    .getAbsolutePath();
        }
    }

    @Override
    public boolean havePassword(int userId) throws RemoteException {
        // Do we need a permissions check here?

        return new File(getLockPasswordFilename(userId)).length() > 0;
    }

    @Override
    public boolean havePattern(int userId) throws RemoteException {
        // Do we need a permissions check here?

        return new File(getLockPatternFilename(userId)).length() > 0;
    }

    private void maybeUpdateKeystore(String password, int userHandle) {
        final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
        final KeyStore ks = KeyStore.getInstance();

        final List<UserInfo> profiles = um.getProfiles(userHandle);
        boolean shouldReset = TextUtils.isEmpty(password);

        // For historical reasons, don't wipe a non-empty keystore if we have a single user with a
        // single profile.
        if (userHandle == UserHandle.USER_OWNER && profiles.size() == 1) {
            if (!ks.isEmpty()) {
                shouldReset = false;
            }
        }

        for (UserInfo pi : profiles) {
            final int profileUid = UserHandle.getUid(pi.id, Process.SYSTEM_UID);
            if (shouldReset) {
                ks.resetUid(profileUid);
            } else {
                ks.passwordUid(password, profileUid);
            }
        }
    }

    @Override
    public void setLockPattern(String pattern, int userId) throws RemoteException {
        checkWritePermission(userId);

        maybeUpdateKeystore(pattern, userId);

        final byte[] hash = LockPatternUtils.patternToHash(
                LockPatternUtils.stringToPattern(pattern));
        writeFile(getLockPatternFilename(userId), hash);
    }

    @Override
    public void setLockPassword(String password, int userId) throws RemoteException {
        checkWritePermission(userId);

        maybeUpdateKeystore(password, userId);

        writeFile(getLockPasswordFilename(userId),
                mLockPatternUtils.passwordToHash(password, userId));
    }

    @Override
    public boolean checkPattern(String pattern, int userId) throws RemoteException {
        checkPasswordReadPermission(userId);
        try {
            // Read all the bytes from the file
            RandomAccessFile raf = new RandomAccessFile(getLockPatternFilename(userId), "r");
            final byte[] stored = new byte[(int) raf.length()];
            int got = raf.read(stored, 0, stored.length);
            raf.close();
            if (got <= 0) {
                return true;
            }
            // Compare the hash from the file with the entered pattern's hash
            final byte[] hash = LockPatternUtils.patternToHash(
                    LockPatternUtils.stringToPattern(pattern));
            final boolean matched = Arrays.equals(stored, hash);
            if (matched && !TextUtils.isEmpty(pattern)) {
                maybeUpdateKeystore(pattern, userId);
            }
            return matched;
        } catch (FileNotFoundException fnfe) {
            Slog.e(TAG, "Cannot read file " + fnfe);
        } catch (IOException ioe) {
            Slog.e(TAG, "Cannot read file " + ioe);
        }
        return true;
    }

    @Override
    public boolean checkPassword(String password, int userId) throws RemoteException {
        checkPasswordReadPermission(userId);

        try {
            // Read all the bytes from the file
            RandomAccessFile raf = new RandomAccessFile(getLockPasswordFilename(userId), "r");
            final byte[] stored = new byte[(int) raf.length()];
            int got = raf.read(stored, 0, stored.length);
            raf.close();
            if (got <= 0) {
                return true;
            }
            // Compare the hash from the file with the entered password's hash
            final byte[] hash = mLockPatternUtils.passwordToHash(password, userId);
            final boolean matched = Arrays.equals(stored, hash);
            if (matched && !TextUtils.isEmpty(password)) {
                maybeUpdateKeystore(password, userId);
            }
            return matched;
        } catch (FileNotFoundException fnfe) {
            Slog.e(TAG, "Cannot read file " + fnfe);
        } catch (IOException ioe) {
            Slog.e(TAG, "Cannot read file " + ioe);
        }
        return true;
    }

    @Override
        public boolean checkVoldPassword(int userId) throws RemoteException {
        if (!mFirstCallToVold) {
            return false;
        }
        mFirstCallToVold = false;

        checkPasswordReadPermission(userId);

        // There's no guarantee that this will safely connect, but if it fails
        // we will simply show the lock screen when we shouldn't, so relatively
        // benign. There is an outside chance something nasty would happen if
        // this service restarted before vold stales out the password in this
        // case. The nastiness is limited to not showing the lock screen when
        // we should, within the first minute of decrypting the phone if this
        // service can't connect to vold, it restarts, and then the new instance
        // does successfully connect.
        final IMountService service = getMountService();
        String password = service.getPassword();
        service.clearPassword();
        if (password == null) {
            return false;
        }

        try {
            if (mLockPatternUtils.isLockPatternEnabled()) {
                if (checkPattern(password, userId)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }

        try {
            if (mLockPatternUtils.isLockPasswordEnabled()) {
                if (checkPassword(password, userId)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }

        return false;
    }

    @Override
    public void removeUser(int userId) {
        checkWritePermission(userId);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try {
            final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
            final UserInfo parentInfo = um.getProfileParent(userId);
            if (parentInfo == null) {
                // This user owns its lock settings files - safe to delete them
                File file = new File(getLockPasswordFilename(userId));
                if (file.exists()) {
                    file.delete();
                }
                file = new File(getLockPatternFilename(userId));
                if (file.exists()) {
                    file.delete();
                }
            }

            db.beginTransaction();
            db.delete(TABLE, COLUMN_USERID + "='" + userId + "'", null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        final KeyStore ks = KeyStore.getInstance();
        final int userUid = UserHandle.getUid(userId, Process.SYSTEM_UID);
        ks.resetUid(userUid);
    }

    private void writeFile(String name, byte[] hash) {
        try {
            // Write the hash to file
            RandomAccessFile raf = new RandomAccessFile(name, "rw");
            // Truncate the file if pattern is null, to clear the lock
            if (hash == null || hash.length == 0) {
                raf.setLength(0);
            } else {
                raf.write(hash, 0, hash.length);
            }
            raf.close();
        } catch (IOException ioe) {
            Slog.e(TAG, "Error writing to file " + ioe);
        }
    }

    private void writeToDb(String key, String value, int userId) {
        writeToDb(mOpenHelper.getWritableDatabase(), key, value, userId);
        notifyObservers(key, userId);
    }

    private void writeToDb(SQLiteDatabase db, String key, String value, int userId) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_KEY, key);
        cv.put(COLUMN_USERID, userId);
        cv.put(COLUMN_VALUE, value);

        db.beginTransaction();
        try {
            db.delete(TABLE, COLUMN_KEY + "=? AND " + COLUMN_USERID + "=?",
                    new String[] {key, Integer.toString(userId)});
            db.insert(TABLE, null, cv);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String readFromDb(String key, String defaultValue, int userId) {
        Cursor cursor;
        String result = defaultValue;
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if ((cursor = db.query(TABLE, COLUMNS_FOR_QUERY,
                COLUMN_USERID + "=? AND " + COLUMN_KEY + "=?",
                new String[] { Integer.toString(userId), key },
                null, null, null)) != null) {
            if (cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
            cursor.close();
        }
        return result;
    }

    class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = "LockSettingsDB";
        private static final String DATABASE_NAME = "locksettings.db";

        private static final int DATABASE_VERSION = 2;

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            setWriteAheadLoggingEnabled(true);
        }

        private void createTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_KEY + " TEXT," +
                    COLUMN_USERID + " INTEGER," +
                    COLUMN_VALUE + " TEXT" +
                    ");");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTable(db);
            initializeDefaults(db);
        }

        private void initializeDefaults(SQLiteDatabase db) {
            // Get the lockscreen default from a system property, if available
            boolean lockScreenDisable = SystemProperties.getBoolean("ro.lockscreen.disable.default",
                    false);
            if (lockScreenDisable) {
                writeToDb(db, LockPatternUtils.DISABLE_LOCKSCREEN_KEY, "1", 0);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            int upgradeVersion = oldVersion;
            if (upgradeVersion == 1) {
                // Set the initial value for {@link LockPatternUtils#LOCKSCREEN_WIDGETS_ENABLED}
                // during upgrade based on whether each user previously had widgets in keyguard.
                maybeEnableWidgetSettingForUsers(db);
                upgradeVersion = 2;
            }

            if (upgradeVersion != DATABASE_VERSION) {
                Log.w(TAG, "Failed to upgrade database!");
            }
        }

        private void maybeEnableWidgetSettingForUsers(SQLiteDatabase db) {
            final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
            final ContentResolver cr = mContext.getContentResolver();
            final List<UserInfo> users = um.getUsers();
            for (int i = 0; i < users.size(); i++) {
                final int userId = users.get(i).id;
                final boolean enabled = mLockPatternUtils.hasWidgetsEnabledInKeyguard(userId);
                Log.v(TAG, "Widget upgrade uid=" + userId + ", enabled="
                        + enabled + ", w[]=" + mLockPatternUtils.getAppWidgets());
                loadSetting(db, LockPatternUtils.LOCKSCREEN_WIDGETS_ENABLED, userId, enabled);
            }
        }

        private void loadSetting(SQLiteDatabase db, String key, int userId, boolean value) {
            SQLiteStatement stmt = null;
            try {
                stmt = db.compileStatement(
                        "INSERT OR REPLACE INTO locksettings(name,user,value) VALUES(?,?,?);");
                stmt.bindString(1, key);
                stmt.bindLong(2, userId);
                stmt.bindLong(3, value ? 1 : 0);
                stmt.execute();
            } finally {
                if (stmt != null) stmt.close();
            }
        }
    }

    private static final String[] VALID_SETTINGS = new String[] {
        LockPatternUtils.LOCKOUT_PERMANENT_KEY,
        LockPatternUtils.LOCKOUT_ATTEMPT_DEADLINE,
        LockPatternUtils.PATTERN_EVER_CHOSEN_KEY,
        LockPatternUtils.PASSWORD_TYPE_KEY,
        LockPatternUtils.PASSWORD_TYPE_ALTERNATE_KEY,
        LockPatternUtils.LOCK_PASSWORD_SALT_KEY,
        LockPatternUtils.DISABLE_LOCKSCREEN_KEY,
        LockPatternUtils.LOCKSCREEN_OPTIONS,
        LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK,
        LockPatternUtils.BIOMETRIC_WEAK_EVER_CHOSEN_KEY,
        LockPatternUtils.LOCKSCREEN_POWER_BUTTON_INSTANTLY_LOCKS,
        LockPatternUtils.PASSWORD_HISTORY_KEY,
        Secure.LOCK_PATTERN_ENABLED,
        Secure.LOCK_BIOMETRIC_WEAK_FLAGS,
        Secure.LOCK_PATTERN_VISIBLE,
        Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED
    };

    // These are protected with a read permission
    private static final String[] READ_PROFILE_PROTECTED_SETTINGS = new String[] {
        Secure.LOCK_SCREEN_OWNER_INFO_ENABLED,
        Secure.LOCK_SCREEN_OWNER_INFO
    };

    private IMountService getMountService() {
        final IBinder service = ServiceManager.getService("mount");
        if (service != null) {
            return IMountService.Stub.asInterface(service);
        }
        return null;
    }

    private class LockSettingsObserver implements DeathRecipient {
        ILockSettingsObserver remote;

        @Override
        public void binderDied() {
            mObservers.remove(this);
        }
    }
}
