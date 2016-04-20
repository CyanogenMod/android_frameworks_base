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

package com.android.server;

import android.os.RemoteException;
import android.provider.Settings;
import com.android.internal.annotations.VisibleForTesting;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;
import com.android.internal.widget.LockPatternUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static android.content.Context.USER_SERVICE;

/**
 * Storage for the lock settings service.
 */
class LockSettingsStorage {

    private static final String TAG = "LockSettingsStorage";
    private static final String TABLE = "locksettings";

    private static final String COLUMN_KEY = "name";
    private static final String COLUMN_USERID = "user";
    private static final String COLUMN_VALUE = "value";

    private static final String[] COLUMNS_FOR_QUERY = {
            COLUMN_VALUE
    };
    private static final String[] COLUMNS_FOR_PREFETCH = {
            COLUMN_KEY, COLUMN_VALUE
    };

    private static final String SYSTEM_DIRECTORY = "/system/";
    private static final String LOCK_PATTERN_FILE = "gatekeeper.pattern.key";
    private static final String BASE_ZERO_LOCK_PATTERN_FILE = "gatekeeper.gesture.key";
    private static final String LEGACY_LOCK_PATTERN_FILE = "gesture.key";
    private static final String CM_LEGACY_LOCK_PATTERN_FILE = "cm_gesture.key";
    private static final String LOCK_PASSWORD_FILE = "gatekeeper.password.key";
    private static final String LEGACY_LOCK_PASSWORD_FILE = "password.key";

    private static final Object DEFAULT = new Object();

    private final DatabaseHelper mOpenHelper;
    private final Context mContext;
    private final Cache mCache = new Cache();
    private final Object mFileWriteLock = new Object();

    private int mStoredCredentialType;

    class CredentialHash {
        static final int TYPE_NONE = -1;
        static final int TYPE_PATTERN = 1;
        static final int TYPE_PASSWORD = 2;

        static final int VERSION_LEGACY = 0;
        static final int VERSION_GATEKEEPER = 1;

        CredentialHash(byte[] hash, int version) {
            this.hash = hash;
            this.version = version;
            this.isBaseZeroPattern = false;
        }

        CredentialHash(byte[] hash, boolean isBaseZeroPattern) {
            this.hash = hash;
            this.version = VERSION_GATEKEEPER;
            this.isBaseZeroPattern = isBaseZeroPattern;
        }

        byte[] hash;
        int version;
        boolean isBaseZeroPattern;
    }

    public LockSettingsStorage(Context context, Callback callback) {
        mContext = context;
        mOpenHelper = new DatabaseHelper(context, callback);
    }

    public void writeKeyValue(String key, String value, int userId) {
        writeKeyValue(mOpenHelper.getWritableDatabase(), key, value, userId);
    }

    public void writeKeyValue(SQLiteDatabase db, String key, String value, int userId) {
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
            mCache.putKeyValue(key, value, userId);
        } finally {
            db.endTransaction();
        }

    }

    public String readKeyValue(String key, String defaultValue, int userId) {
        int version;
        synchronized (mCache) {
            if (mCache.hasKeyValue(key, userId)) {
                return mCache.peekKeyValue(key, defaultValue, userId);
            }
            version = mCache.getVersion();
        }

        Cursor cursor;
        Object result = DEFAULT;
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
        mCache.putKeyValueIfUnchanged(key, result, userId, version);
        return result == DEFAULT ? defaultValue : (String) result;
    }

    public void prefetchUser(int userId) {
        int version;
        synchronized (mCache) {
            if (mCache.isFetched(userId)) {
                return;
            }
            mCache.setFetched(userId);
            version = mCache.getVersion();
        }

        Cursor cursor;
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if ((cursor = db.query(TABLE, COLUMNS_FOR_PREFETCH,
                COLUMN_USERID + "=?",
                new String[] { Integer.toString(userId) },
                null, null, null)) != null) {
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                String value = cursor.getString(1);
                mCache.putKeyValueIfUnchanged(key, value, userId, version);
            }
            cursor.close();
        }

        // Populate cache by reading the password and pattern files.
        readPasswordHash(userId);
        readPatternHash(userId);
    }

    public int getStoredCredentialType(int userId) {
        if (mStoredCredentialType != 0) {
            return mStoredCredentialType;
        }

        CredentialHash pattern = readPatternHash(userId);
        if (pattern == null) {
            if (readPasswordHash(userId) != null) {
                mStoredCredentialType = CredentialHash.TYPE_PASSWORD;
            } else {
                mStoredCredentialType = CredentialHash.TYPE_NONE;
            }
        } else {
            CredentialHash password = readPasswordHash(userId);
            if (password != null) {
                // Both will never be GateKeeper
                if (password.version == CredentialHash.VERSION_GATEKEEPER) {
                    mStoredCredentialType = CredentialHash.TYPE_PASSWORD;
                } else {
                    mStoredCredentialType = CredentialHash.TYPE_PATTERN;
                }
            } else {
                mStoredCredentialType = CredentialHash.TYPE_PATTERN;
            }
        }

        return mStoredCredentialType;
    }


    public CredentialHash readPasswordHash(int userId) {
        byte[] stored = readFile(getLockPasswordFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, CredentialHash.VERSION_GATEKEEPER);
        }

        stored = readFile(getLegacyLockPasswordFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, CredentialHash.VERSION_LEGACY);
        }

        return null;
    }

    public CredentialHash readPatternHash(int userId) {
        byte[] stored = readFile(getLockPatternFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, CredentialHash.VERSION_GATEKEEPER);
        }

        stored = readFile(getBaseZeroLockPatternFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, true);
        }

        stored = readFile(getLegacyLockPatternFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, CredentialHash.VERSION_LEGACY);
        }

        stored = readFile(getCmLegacyLockPatternFilename(userId));
        if (stored != null && stored.length > 0) {
            return new CredentialHash(stored, CredentialHash.VERSION_LEGACY);
        }

        return null;
    }


    public boolean hasPassword(int userId) {
        return hasFile(getLockPasswordFilename(userId)) ||
            hasFile(getLegacyLockPasswordFilename(userId));
    }

    public boolean hasPattern(int userId) {
        return hasFile(getLockPatternFilename(userId)) ||
            hasFile(getBaseZeroLockPatternFilename(userId)) ||
            hasFile(getLegacyLockPatternFilename(userId)) ||
            hasFile(getCmLegacyLockPatternFilename(userId));
    }

    private boolean hasFile(String name) {
        byte[] contents = readFile(name);
        return contents != null && contents.length > 0;
    }

    private byte[] readFile(String name) {
        int version;
        synchronized (mCache) {
            if (mCache.hasFile(name)) {
                return mCache.peekFile(name);
            }
            version = mCache.getVersion();
        }

        RandomAccessFile raf = null;
        byte[] stored = null;
        try {
            raf = new RandomAccessFile(name, "r");
            stored = new byte[(int) raf.length()];
            raf.readFully(stored, 0, stored.length);
            raf.close();
        } catch (IOException e) {
            Slog.e(TAG, "Cannot read file " + e);
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    Slog.e(TAG, "Error closing file " + e);
                }
            }
        }
        mCache.putFileIfUnchanged(name, stored, version);
        return stored;
    }

    private void writeFile(String name, byte[] hash) {
        synchronized (mFileWriteLock) {
            RandomAccessFile raf = null;
            try {
                // Write the hash to file
                raf = new RandomAccessFile(name, "rw");
                // Truncate the file if pattern is null, to clear the lock
                if (hash == null || hash.length == 0) {
                    raf.setLength(0);
                } else {
                    raf.write(hash, 0, hash.length);
                }
                raf.close();
            } catch (IOException e) {
                Slog.e(TAG, "Error writing to file " + e);
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (IOException e) {
                        Slog.e(TAG, "Error closing file " + e);
                    }
                }
            }
            mCache.putFile(name, hash);
        }
    }

    private void deleteFile(String name) {
        File f = new File(name);
        if (f != null) {
            f.delete();
        }
    }

    public void writePatternHash(byte[] hash, int userId) {
        mStoredCredentialType = hash == null
            ? CredentialHash.TYPE_NONE
            : CredentialHash.TYPE_PATTERN;

        writeFile(getLockPatternFilename(userId), hash);
        clearPasswordHash(userId);
    }

    private void clearPatternHash(int userId) {
        writeFile(getLockPatternFilename(userId), null);
    }

    public void writePasswordHash(byte[] hash, int userId) {
        mStoredCredentialType = hash == null
            ? CredentialHash.TYPE_NONE
            : CredentialHash.TYPE_PASSWORD;
        writeFile(getLockPasswordFilename(userId), hash);
        clearPatternHash(userId);
    }

    private void clearPasswordHash(int userId) {
        writeFile(getLockPasswordFilename(userId), null);
    }

    public byte getLockPatternSize(int userId) {
        long size = Long.valueOf(readKeyValue(Settings.Secure.LOCK_PATTERN_SIZE, "-1", userId));
        if (size > 0 && size < 128) {
            return (byte) size;
        }
        return LockPatternUtils.PATTERN_SIZE_DEFAULT;
    }

    public boolean isDefaultSize(int userId) {
        return getLockPatternSize(userId) == LockPatternUtils.PATTERN_SIZE_DEFAULT;
    }

    @VisibleForTesting
    String getLockPatternFilename(int userId) {
        String baseFileName = LOCK_PATTERN_FILE;
        return getLockCredentialFilePathForUser(userId, baseFileName);
    }

    @VisibleForTesting
    String getLockPasswordFilename(int userId) {
        return getLockCredentialFilePathForUser(userId, LOCK_PASSWORD_FILE);
    }

    @VisibleForTesting
    String getLegacyLockPatternFilename(int userId) {
        return getLockCredentialFilePathForUser(userId, LEGACY_LOCK_PATTERN_FILE);
    }

    String getCmLegacyLockPatternFilename(int userId) {
        return getLockCredentialFilePathForUser(userId, CM_LEGACY_LOCK_PATTERN_FILE);
    }

    @VisibleForTesting
    String getLegacyLockPasswordFilename(int userId) {
        return getLockCredentialFilePathForUser(userId, LEGACY_LOCK_PASSWORD_FILE);
    }

    private String getBaseZeroLockPatternFilename(int userId) {
        return getLockCredentialFilePathForUser(userId, BASE_ZERO_LOCK_PATTERN_FILE);
    }

    private String getLockCredentialFilePathForUser(int userId, String basename) {
        userId = getUserParentOrSelfId(userId);
        String dataSystemDirectory =
                android.os.Environment.getDataDirectory().getAbsolutePath() +
                        SYSTEM_DIRECTORY;
        if (userId == 0) {
            // Leave it in the same place for user 0
            return dataSystemDirectory + basename;
        } else {
            return new File(Environment.getUserSystemDirectory(userId), basename).getAbsolutePath();
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

    public void removeUser(int userId) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        final UserManager um = (UserManager) mContext.getSystemService(USER_SERVICE);
        final UserInfo parentInfo = um.getProfileParent(userId);

        if (parentInfo == null) {
            // This user owns its lock settings files - safe to delete them
            synchronized (mFileWriteLock) {
                String name = getLockPasswordFilename(userId);
                File file = new File(name);
                if (file.exists()) {
                    file.delete();
                    mCache.putFile(name, null);
                }
                name = getLockPatternFilename(userId);
                file = new File(name);
                if (file.exists()) {
                    file.delete();
                    mCache.putFile(name, null);
                }
            }
        }

        try {
            db.beginTransaction();
            db.delete(TABLE, COLUMN_USERID + "='" + userId + "'", null);
            db.setTransactionSuccessful();
            mCache.removeUser(userId);
        } finally {
            db.endTransaction();
        }
    }

    @VisibleForTesting
    void closeDatabase() {
        mOpenHelper.close();
    }

    @VisibleForTesting
    void clearCache() {
        mCache.clear();
    }

    public interface Callback {
        void initialize(SQLiteDatabase db);
    }

    class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = "LockSettingsDB";
        private static final String DATABASE_NAME = "locksettings.db";

        private static final int DATABASE_VERSION = 2;

        private final Callback mCallback;

        public DatabaseHelper(Context context, Callback callback) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            setWriteAheadLoggingEnabled(true);
            mCallback = callback;
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
            mCallback.initialize(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            int upgradeVersion = oldVersion;
            if (upgradeVersion == 1) {
                // Previously migrated lock screen widget settings. Now defunct.
                upgradeVersion = 2;
            }

            if (upgradeVersion != DATABASE_VERSION) {
                Log.w(TAG, "Failed to upgrade database!");
            }
        }
    }

    /**
     * Cache consistency model:
     * - Writes to storage write directly to the cache, but this MUST happen within the atomic
     *   section either provided by the database transaction or mWriteLock, such that writes to the
     *   cache and writes to the backing storage are guaranteed to occur in the same order
     *
     * - Reads can populate the cache, but because they are no strong ordering guarantees with
     *   respect to writes this precaution is taken:
     *   - The cache is assigned a version number that increases every time the cache is modified.
     *     Reads from backing storage can only populate the cache if the backing storage
     *     has not changed since the load operation has begun.
     *     This guarantees that no read operation can shadow a write to the cache that happens
     *     after it had begun.
     */
    private static class Cache {
        private final ArrayMap<CacheKey, Object> mCache = new ArrayMap<>();
        private final CacheKey mCacheKey = new CacheKey();
        private int mVersion = 0;

        String peekKeyValue(String key, String defaultValue, int userId) {
            Object cached = peek(CacheKey.TYPE_KEY_VALUE, key, userId);
            return cached == DEFAULT ? defaultValue : (String) cached;
        }

        boolean hasKeyValue(String key, int userId) {
            return contains(CacheKey.TYPE_KEY_VALUE, key, userId);
        }

        void putKeyValue(String key, String value, int userId) {
            put(CacheKey.TYPE_KEY_VALUE, key, value, userId);
        }

        void putKeyValueIfUnchanged(String key, Object value, int userId, int version) {
            putIfUnchanged(CacheKey.TYPE_KEY_VALUE, key, value, userId, version);
        }

        byte[] peekFile(String fileName) {
            return (byte[]) peek(CacheKey.TYPE_FILE, fileName, -1 /* userId */);
        }

        boolean hasFile(String fileName) {
            return contains(CacheKey.TYPE_FILE, fileName, -1 /* userId */);
        }

        void putFile(String key, byte[] value) {
            put(CacheKey.TYPE_FILE, key, value, -1 /* userId */);
        }

        void putFileIfUnchanged(String key, byte[] value, int version) {
            putIfUnchanged(CacheKey.TYPE_FILE, key, value, -1 /* userId */, version);
        }

        void setFetched(int userId) {
            put(CacheKey.TYPE_FETCHED, "isFetched", "true", userId);
        }

        boolean isFetched(int userId) {
            return contains(CacheKey.TYPE_FETCHED, "", userId);
        }


        private synchronized void put(int type, String key, Object value, int userId) {
            // Create a new CachKey here because it may be saved in the map if the key is absent.
            mCache.put(new CacheKey().set(type, key, userId), value);
            mVersion++;
        }

        private synchronized void putIfUnchanged(int type, String key, Object value, int userId,
                int version) {
            if (!contains(type, key, userId) && mVersion == version) {
                put(type, key, value, userId);
            }
        }

        private synchronized boolean contains(int type, String key, int userId) {
            return mCache.containsKey(mCacheKey.set(type, key, userId));
        }

        private synchronized Object peek(int type, String key, int userId) {
            return mCache.get(mCacheKey.set(type, key, userId));
        }

        private synchronized int getVersion() {
            return mVersion;
        }

        synchronized void removeUser(int userId) {
            for (int i = mCache.size() - 1; i >= 0; i--) {
                if (mCache.keyAt(i).userId == userId) {
                    mCache.removeAt(i);
                }
            }

            // Make sure in-flight loads can't write to cache.
            mVersion++;
        }

        synchronized void clear() {
            mCache.clear();
            mVersion++;
        }

        private static final class CacheKey {
            static final int TYPE_KEY_VALUE = 0;
            static final int TYPE_FILE = 1;
            static final int TYPE_FETCHED = 2;

            String key;
            int userId;
            int type;

            public CacheKey set(int type, String key, int userId) {
                this.type = type;
                this.key = key;
                this.userId = userId;
                return this;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof CacheKey))
                    return false;
                CacheKey o = (CacheKey) obj;
                return userId == o.userId && type == o.type && key.equals(o.key);
            }

            @Override
            public int hashCode() {
                return key.hashCode() ^ userId ^ type;
            }
        }
    }
}
