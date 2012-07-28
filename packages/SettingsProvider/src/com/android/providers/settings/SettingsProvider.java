/*
 * Copyright (C) 2007 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

import java.io.FileNotFoundException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.backup.BackupManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.provider.DrmStore;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

public class SettingsProvider extends ContentProvider {
    private static final String TAG = "SettingsProvider";
    private static final boolean LOCAL_LOGV = false;

    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_OLD_FAVORITES = "old_favorites";

    private static final String[] COLUMN_VALUE = new String[] { "value" };

    // Cache for settings, access-ordered for acting as LRU.
    // Guarded by themselves.
    private static final int MAX_CACHE_ENTRIES = 200;
    private static final SettingsCache sSystemCache = new SettingsCache("system");
    private static final SettingsCache sSecureCache = new SettingsCache("secure");

    // The count of how many known (handled by SettingsProvider)
    // database mutations are currently being handled.  Used by
    // sFileObserver to not reload the database when it's ourselves
    // modifying it.
    private static final AtomicInteger sKnownMutationsInFlight = new AtomicInteger(0);

    // Over this size we don't reject loading or saving settings but
    // we do consider them broken/malicious and don't keep them in
    // memory at least:
    private static final int MAX_CACHE_ENTRY_SIZE = 500;

    private static final Bundle NULL_SETTING = Bundle.forPair("value", null);

    // Used as a sentinel value in an instance equality test when we
    // want to cache the existence of a key, but not store its value.
    private static final Bundle TOO_LARGE_TO_CACHE_MARKER = Bundle.forPair("_dummy", null);

    protected DatabaseHelper mOpenHelper;
    private BackupManager mBackupManager;

    /**
     * Decode a content URL into the table, projection, and arguments
     * used to access the corresponding database rows.
     */
    private static class SqlArguments {
        public String table;
        public final String where;
        public final String[] args;

        /** Operate on existing rows. */
        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                if (!DatabaseHelper.isValidTable(this.table)) {
                    throw new IllegalArgumentException("Bad root path: " + this.table);
                }
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                if (!DatabaseHelper.isValidTable(this.table)) {
                    throw new IllegalArgumentException("Bad root path: " + this.table);
                }
                if ("system".equals(this.table) || "secure".equals(this.table)) {
                    this.where = Settings.NameValueTable.NAME + "=?";
                    this.args = new String[] { url.getPathSegments().get(1) };
                } else {
                    this.where = "_id=" + ContentUris.parseId(url);
                    this.args = null;
                }
            }
        }

        /** Insert new rows (no where clause allowed). */
        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                if (!DatabaseHelper.isValidTable(this.table)) {
                    throw new IllegalArgumentException("Bad root path: " + this.table);
                }
                this.where = null;
                this.args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }

    /**
     * Get the content URI of a row added to a table.
     * @param tableUri of the entire table
     * @param values found in the row
     * @param rowId of the row
     * @return the content URI for this particular row
     */
    private Uri getUriFor(Uri tableUri, ContentValues values, long rowId) {
        if (tableUri.getPathSegments().size() != 1) {
            throw new IllegalArgumentException("Invalid URI: " + tableUri);
        }
        String table = tableUri.getPathSegments().get(0);
        if ("system".equals(table) || "secure".equals(table)) {
            String name = values.getAsString(Settings.NameValueTable.NAME);
            return Uri.withAppendedPath(tableUri, name);
        } else {
            return ContentUris.withAppendedId(tableUri, rowId);
        }
    }

    /**
     * Send a notification when a particular content URI changes.
     * Modify the system property used to communicate the version of
     * this table, for tables which have such a property.  (The Settings
     * contract class uses these to provide client-side caches.)
     * @param uri to send notifications for
     */
    private void sendNotify(Uri uri) {
        // Update the system property *first*, so if someone is listening for
        // a notification and then using the contract class to get their data,
        // the system property will be updated and they'll get the new data.

        boolean backedUpDataChanged = false;
        String property = null, table = uri.getPathSegments().get(0);
        if (table.equals("system")) {
            property = Settings.System.SYS_PROP_SETTING_VERSION;
            backedUpDataChanged = true;
        } else if (table.equals("secure")) {
            property = Settings.Secure.SYS_PROP_SETTING_VERSION;
            backedUpDataChanged = true;
        }

        if (property != null) {
            long version = SystemProperties.getLong(property, 0) + 1;
            if (LOCAL_LOGV) Log.v(TAG, "property: " + property + "=" + version);
            SystemProperties.set(property, Long.toString(version));
        }

        // Inform the backup manager about a data change
        if (backedUpDataChanged) {
            mBackupManager.dataChanged();
        }
        // Now send the notification through the content framework.

        String notify = uri.getQueryParameter("notify");
        if (notify == null || "true".equals(notify)) {
            getContext().getContentResolver().notifyChange(uri, null);
            if (LOCAL_LOGV) Log.v(TAG, "notifying: " + uri);
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "notification suppressed: " + uri);
        }
    }

    /**
     * Make sure the caller has permission to write this data.
     * @param args supplied by the caller
     * @throws SecurityException if the caller is forbidden to write.
     */
    private void checkWritePermissions(SqlArguments args) {
        if ("secure".equals(args.table) &&
            getContext().checkCallingOrSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    String.format("Permission denial: writing to secure settings requires %1$s",
                                  android.Manifest.permission.WRITE_SECURE_SETTINGS));
        }
    }

    // FileObserver for external modifications to the database file.
    // Note that this is for platform developers only with
    // userdebug/eng builds who should be able to tinker with the
    // sqlite database out from under the SettingsProvider, which is
    // normally the exclusive owner of the database.  But we keep this
    // enabled all the time to minimize development-vs-user
    // differences in testing.
    private static SettingsFileObserver sObserverInstance;
    private class SettingsFileObserver extends FileObserver {
        private final AtomicBoolean mIsDirty = new AtomicBoolean(false);
        private final String mPath;

        public SettingsFileObserver(String path) {
            super(path, FileObserver.CLOSE_WRITE |
                  FileObserver.CREATE | FileObserver.DELETE |
                  FileObserver.MOVED_TO | FileObserver.MODIFY);
            mPath = path;
        }

        public void onEvent(int event, String path) {
            int modsInFlight = sKnownMutationsInFlight.get();
            if (modsInFlight > 0) {
                // our own modification.
                return;
            }
            Log.d(TAG, "external modification to " + mPath + "; event=" + event);
            if (!mIsDirty.compareAndSet(false, true)) {
                // already handled. (we get a few update events
                // during an sqlite write)
                return;
            }
            Log.d(TAG, "updating our caches for " + mPath);
            fullyPopulateCaches();
            mIsDirty.set(false);
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        mBackupManager = new BackupManager(getContext());

        if (!ensureAndroidIdIsSet()) {
            return false;
        }

        // Watch for external modifications to the database file,
        // keeping our cache in sync.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        sObserverInstance = new SettingsFileObserver(db.getPath());
        sObserverInstance.startWatching();
        startAsyncCachePopulation();
        return true;
    }

    private void startAsyncCachePopulation() {
        new Thread("populate-settings-caches") {
            public void run() {
                fullyPopulateCaches();
            }
        }.start();
    }

    private void fullyPopulateCaches() {
        fullyPopulateCache("secure", sSecureCache);
        fullyPopulateCache("system", sSystemCache);
    }

    // Slurp all values (if sane in number & size) into cache.
    private void fullyPopulateCache(String table, SettingsCache cache) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = db.query(
            table,
            new String[] { Settings.NameValueTable.NAME, Settings.NameValueTable.VALUE },
            null, null, null, null, null,
            "" + (MAX_CACHE_ENTRIES + 1) /* limit */);
        try {
            synchronized (cache) {
                cache.evictAll();
                cache.setFullyMatchesDisk(true);  // optimistic
                int rows = 0;
                while (c.moveToNext()) {
                    rows++;
                    String name = c.getString(0);
                    String value = c.getString(1);
                    cache.populate(name, value);
                }
                if (rows > MAX_CACHE_ENTRIES) {
                    // Somewhat redundant, as removeEldestEntry() will
                    // have already done this, but to be explicit:
                    cache.setFullyMatchesDisk(false);
                    Log.d(TAG, "row count exceeds max cache entries for table " + table);
                }
                Log.d(TAG, "cache for settings table '" + table + "' rows=" + rows + "; fullycached=" +
                      cache.fullyMatchesDisk());
            }
        } finally {
            c.close();
        }
    }

    private boolean ensureAndroidIdIsSet() {
        final Cursor c = query(Settings.Secure.CONTENT_URI,
                new String[] { Settings.NameValueTable.VALUE },
                Settings.NameValueTable.NAME + "=?",
                new String[] { Settings.Secure.ANDROID_ID }, null);
        try {
            final String value = c.moveToNext() ? c.getString(0) : null;
            if (value == null) {
                final SecureRandom random = new SecureRandom();
                final String newAndroidIdValue = Long.toHexString(random.nextLong());
                Log.d(TAG, "Generated and saved new ANDROID_ID [" + newAndroidIdValue + "]");
                final ContentValues values = new ContentValues();
                values.put(Settings.NameValueTable.NAME, Settings.Secure.ANDROID_ID);
                values.put(Settings.NameValueTable.VALUE, newAndroidIdValue);
                final Uri uri = insert(Settings.Secure.CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
            }
            return true;
        } finally {
            c.close();
        }
    }

    /**
     * Fast path that avoids the use of chatty remoted Cursors.
     */
    @Override
    public Bundle call(String method, String request, Bundle args) {
        if (Settings.CALL_METHOD_GET_SYSTEM.equals(method)) {
            return lookupValue("system", sSystemCache, request);
        }
        if (Settings.CALL_METHOD_GET_SECURE.equals(method)) {
            return lookupValue("secure", sSecureCache, request);
        }
        return null;
    }

    // Looks up value 'key' in 'table' and returns either a single-pair Bundle,
    // possibly with a null value, or null on failure.
    private Bundle lookupValue(String table, SettingsCache cache, String key) {
        synchronized (cache) {
            Bundle value = cache.get(key);
            if (value != null) {
                if (value != TOO_LARGE_TO_CACHE_MARKER) {
                    return value;
                }
                // else we fall through and read the value from disk
            } else if (cache.fullyMatchesDisk()) {
                // Fast path (very common).  Don't even try touch disk
                // if we know we've slurped it all in.  Trying to
                // touch the disk would mean waiting for yaffs2 to
                // give us access, which could takes hundreds of
                // milliseconds.  And we're very likely being called
                // from somebody's UI thread...
                return NULL_SETTING;
            }
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(table, COLUMN_VALUE, "name=?", new String[]{key},
                              null, null, null, null);
            if (cursor != null && cursor.getCount() == 1) {
                cursor.moveToFirst();
                return cache.putIfAbsent(key, cursor.getString(0));
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "settings lookup error", e);
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
        cache.putIfAbsent(key, null);
        return NULL_SETTING;
    }

    @Override
    public Cursor query(Uri url, String[] select, String where, String[] whereArgs, String sort) {
        SqlArguments args = new SqlArguments(url, where, whereArgs);
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        // The favorites table was moved from this provider to a provider inside Home
        // Home still need to query this table to upgrade from pre-cupcake builds
        // However, a cupcake+ build with no data does not contain this table which will
        // cause an exception in the SQL stack. The following line is a special case to
        // let the caller of the query have a chance to recover and avoid the exception
        if (TABLE_FAVORITES.equals(args.table)) {
            return null;
        } else if (TABLE_OLD_FAVORITES.equals(args.table)) {
            args.table = TABLE_FAVORITES;
            Cursor cursor = db.rawQuery("PRAGMA table_info(favorites);", null);
            if (cursor != null) {
                boolean exists = cursor.getCount() > 0;
                cursor.close();
                if (!exists) return null;
            } else {
                return null;
            }
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        Cursor ret = qb.query(db, select, args.where, args.args, null, null, sort);
        ret.setNotificationUri(getContext().getContentResolver(), url);
        return ret;
    }

    @Override
    public String getType(Uri url) {
        // If SqlArguments supplies a where clause, then it must be an item
        // (because we aren't supplying our own where clause).
        SqlArguments args = new SqlArguments(url, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);
        if (TABLE_FAVORITES.equals(args.table)) {
            return 0;
        }
        checkWritePermissions(args);
        SettingsCache cache = SettingsCache.forTable(args.table);

        sKnownMutationsInFlight.incrementAndGet();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                if (db.insert(args.table, null, values[i]) < 0) return 0;
                SettingsCache.populate(cache, values[i]);
                if (LOCAL_LOGV) Log.v(TAG, args.table + " <- " + values[i]);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            sKnownMutationsInFlight.decrementAndGet();
        }

        sendNotify(uri);
        return values.length;
    }

    /*
     * Used to parse changes to the value of Settings.Secure.LOCATION_PROVIDERS_ALLOWED.
     * This setting contains a list of the currently enabled location providers.
     * But helper functions in android.providers.Settings can enable or disable
     * a single provider by using a "+" or "-" prefix before the provider name.
     *
     * @returns whether the database needs to be updated or not, also modifying
     *     'initialValues' if needed.
     */
    private boolean parseProviderList(Uri url, ContentValues initialValues) {
        String value = initialValues.getAsString(Settings.Secure.VALUE);
        String newProviders = null;
        if (value != null && value.length() > 1) {
            char prefix = value.charAt(0);
            if (prefix == '+' || prefix == '-') {
                // skip prefix
                value = value.substring(1);

                // read list of enabled providers into "providers"
                String providers = "";
                String[] columns = {Settings.Secure.VALUE};
                String where = Settings.Secure.NAME + "=\'" + Settings.Secure.LOCATION_PROVIDERS_ALLOWED + "\'";
                Cursor cursor = query(url, columns, where, null, null);
                if (cursor != null && cursor.getCount() == 1) {
                    try {
                        cursor.moveToFirst();
                        providers = cursor.getString(0);
                    } finally {
                        cursor.close();
                    }
                }

                int index = providers.indexOf(value);
                int end = index + value.length();
                // check for commas to avoid matching on partial string
                if (index > 0 && providers.charAt(index - 1) != ',') index = -1;
                if (end < providers.length() && providers.charAt(end) != ',') index = -1;

                if (prefix == '+' && index < 0) {
                    // append the provider to the list if not present
                    if (providers.length() == 0) {
                        newProviders = value;
                    } else {
                        newProviders = providers + ',' + value;
                    }
                } else if (prefix == '-' && index >= 0) {
                    // remove the provider from the list if present
                    // remove leading or trailing comma
                    if (index > 0) {
                        index--;
                    } else if (end < providers.length()) {
                        end++;
                    }

                    newProviders = providers.substring(0, index);
                    if (end < providers.length()) {
                        newProviders += providers.substring(end);
                    }
                } else {
                    // nothing changed, so no need to update the database
                    return false;
                }

                if (newProviders != null) {
                    initialValues.put(Settings.Secure.VALUE, newProviders);
                }
            }
        }

        return true;
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(url);
        if (TABLE_FAVORITES.equals(args.table)) {
            return null;
        }
        checkWritePermissions(args);

        // Special case LOCATION_PROVIDERS_ALLOWED.
        // Support enabling/disabling a single provider (using "+" or "-" prefix)
        String name = initialValues.getAsString(Settings.Secure.NAME);
        if (Settings.Secure.LOCATION_PROVIDERS_ALLOWED.equals(name)) {
            if (!parseProviderList(url, initialValues)) return null;
        }

        SettingsCache cache = SettingsCache.forTable(args.table);
        String value = initialValues.getAsString(Settings.NameValueTable.VALUE);
        if (SettingsCache.isRedundantSetValue(cache, name, value)) {
            return Uri.withAppendedPath(url, name);
        }

        sKnownMutationsInFlight.incrementAndGet();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long rowId = db.insert(args.table, null, initialValues);
        sKnownMutationsInFlight.decrementAndGet();
        if (rowId <= 0) return null;

        SettingsCache.populate(cache, initialValues);  // before we notify

        if (LOCAL_LOGV) Log.v(TAG, args.table + " <- " + initialValues);
        url = getUriFor(url, initialValues, rowId);
        sendNotify(url);
        return url;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        SqlArguments args = new SqlArguments(url, where, whereArgs);
        if (TABLE_FAVORITES.equals(args.table)) {
            return 0;
        } else if (TABLE_OLD_FAVORITES.equals(args.table)) {
            args.table = TABLE_FAVORITES;
        }
        checkWritePermissions(args);

        sKnownMutationsInFlight.incrementAndGet();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.delete(args.table, args.where, args.args);
        sKnownMutationsInFlight.decrementAndGet();
        if (count > 0) {
            SettingsCache.invalidate(args.table);  // before we notify
            sendNotify(url);
        }
        startAsyncCachePopulation();
        if (LOCAL_LOGV) Log.v(TAG, args.table + ": " + count + " row(s) deleted");
        return count;
    }

    @Override
    public int update(Uri url, ContentValues initialValues, String where, String[] whereArgs) {
        SqlArguments args = new SqlArguments(url, where, whereArgs);
        if (TABLE_FAVORITES.equals(args.table)) {
            return 0;
        }
        checkWritePermissions(args);

        sKnownMutationsInFlight.incrementAndGet();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, initialValues, args.where, args.args);
        sKnownMutationsInFlight.decrementAndGet();
        if (count > 0) {
            SettingsCache.invalidate(args.table);  // before we notify
            sendNotify(url);
        }
        startAsyncCachePopulation();
        if (LOCAL_LOGV) Log.v(TAG, args.table + ": " + count + " row(s) <- " + initialValues);
        return count;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {

        /*
         * When a client attempts to openFile the default ringtone or
         * notification setting Uri, we will proxy the call to the current
         * default ringtone's Uri (if it is in the DRM or media provider).
         */
        int ringtoneType = RingtoneManager.getDefaultType(uri);
        // Above call returns -1 if the Uri doesn't match a default type
        if (ringtoneType != -1) {
            Context context = getContext();

            // Get the current value for the default sound
            Uri soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, ringtoneType);

            if (soundUri != null) {
                // Only proxy the openFile call to drm or media providers
                String authority = soundUri.getAuthority();
                boolean isDrmAuthority = authority.equals(DrmStore.AUTHORITY);
                if (isDrmAuthority || authority.equals(MediaStore.AUTHORITY) ||
                        authority.equals(RingtoneManager.THEME_AUTHORITY)) {

                    if (isDrmAuthority) {
                        try {
                            // Check DRM access permission here, since once we
                            // do the below call the DRM will be checking our
                            // permission, not our caller's permission
                            DrmStore.enforceAccessDrmPermission(context);
                        } catch (SecurityException e) {
                            throw new FileNotFoundException(e.getMessage());
                        }
                    }

                    return context.getContentResolver().openFileDescriptor(soundUri, mode);
                }
            }
        }

        return super.openFile(uri, mode);
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {

        /*
         * When a client attempts to openFile the default ringtone or
         * notification setting Uri, we will proxy the call to the current
         * default ringtone's Uri (if it is in the DRM or media provider).
         */
        int ringtoneType = RingtoneManager.getDefaultType(uri);
        // Above call returns -1 if the Uri doesn't match a default type
        if (ringtoneType != -1) {
            Context context = getContext();

            // Get the current value for the default sound
            Uri soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, ringtoneType);

            if (soundUri != null) {
                // Only proxy the openFile call to drm or media providers
                String authority = soundUri.getAuthority();
                boolean isDrmAuthority = authority.equals(DrmStore.AUTHORITY);
                if (isDrmAuthority || authority.equals(MediaStore.AUTHORITY) ||
                        authority.equals(RingtoneManager.THEME_AUTHORITY)) {

                    if (isDrmAuthority) {
                        try {
                            // Check DRM access permission here, since once we
                            // do the below call the DRM will be checking our
                            // permission, not our caller's permission
                            DrmStore.enforceAccessDrmPermission(context);
                        } catch (SecurityException e) {
                            throw new FileNotFoundException(e.getMessage());
                        }
                    }

                    try {
                        return context.getContentResolver().openAssetFileDescriptor(soundUri, mode);
                    } catch (FileNotFoundException ex) {
                        // fall through and open the fallback ringtone below
                    }
                }

                try {
                    return super.openAssetFile(soundUri, mode);
                } catch (FileNotFoundException ex) {
                    // Since a non-null Uri was specified, but couldn't be opened,
                    // fall back to the built-in ringtone.
                    return context.getResources().openRawResourceFd(
                            com.android.internal.R.raw.fallbackring);
                }
            }
            // no need to fall through and have openFile() try again, since we
            // already know that will fail.
            throw new FileNotFoundException(); // or return null ?
        }

        // Note that this will end up calling openFile() above.
        return super.openAssetFile(uri, mode);
    }

    /**
     * In-memory LRU Cache of system and secure settings, along with
     * associated helper functions to keep cache coherent with the
     * database.
     */
    private static final class SettingsCache extends LruCache<String, Bundle> {

        private final String mCacheName;
        private boolean mCacheFullyMatchesDisk = false;  // has the whole database slurped.

        public SettingsCache(String name) {
            super(MAX_CACHE_ENTRIES);
            mCacheName = name;
        }

        /**
         * Is the whole database table slurped into this cache?
         */
        public boolean fullyMatchesDisk() {
            synchronized (this) {
                return mCacheFullyMatchesDisk;
            }
        }

        public void setFullyMatchesDisk(boolean value) {
            synchronized (this) {
                mCacheFullyMatchesDisk = value;
            }
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Bundle oldValue, Bundle newValue) {
            if (evicted) {
                mCacheFullyMatchesDisk = false;
            }
        }

        /**
         * Atomic cache population, conditional on size of value and if
         * we lost a race.
         *
         * @returns a Bundle to send back to the client from call(), even
         *     if we lost the race.
         */
        public Bundle putIfAbsent(String key, String value) {
            Bundle bundle = (value == null) ? NULL_SETTING : Bundle.forPair("value", value);
            if (value == null || value.length() <= MAX_CACHE_ENTRY_SIZE) {
                synchronized (this) {
                    if (get(key) == null) {
                        put(key, bundle);
                    }
                }
            }
            return bundle;
        }

        public static SettingsCache forTable(String tableName) {
            if ("system".equals(tableName)) {
                return SettingsProvider.sSystemCache;
            }
            if ("secure".equals(tableName)) {
                return SettingsProvider.sSecureCache;
            }
            return null;
        }

        /**
         * Populates a key in a given (possibly-null) cache.
         */
        public static void populate(SettingsCache cache, ContentValues contentValues) {
            if (cache == null) {
                return;
            }
            String name = contentValues.getAsString(Settings.NameValueTable.NAME);
            if (name == null) {
                Log.w(TAG, "null name populating settings cache.");
                return;
            }
            String value = contentValues.getAsString(Settings.NameValueTable.VALUE);
            cache.populate(name, value);
        }

        public void populate(String name, String value) {
            synchronized (this) {
                if (value == null || value.length() <= MAX_CACHE_ENTRY_SIZE) {
                    put(name, Bundle.forPair(Settings.NameValueTable.VALUE, value));
                } else {
                    put(name, TOO_LARGE_TO_CACHE_MARKER);
                }
            }
        }

        /**
         * Used for wiping a whole cache on deletes when we're not
         * sure what exactly was deleted or changed.
         */
        public static void invalidate(String tableName) {
            SettingsCache cache = SettingsCache.forTable(tableName);
            if (cache == null) {
                return;
            }
            synchronized (cache) {
                cache.evictAll();
                cache.mCacheFullyMatchesDisk = false;
            }
        }

        /**
         * For suppressing duplicate/redundant settings inserts early,
         * checking our cache first (but without faulting it in),
         * before going to sqlite with the mutation.
         */
        public static boolean isRedundantSetValue(SettingsCache cache, String name, String value) {
            if (cache == null) return false;
            synchronized (cache) {
                Bundle bundle = cache.get(name);
                if (bundle == null) return false;
                String oldValue = bundle.getPairValue();
                if (oldValue == null && value == null) return true;
                if ((oldValue == null) != (value == null)) return false;
                return oldValue.equals(value);
            }
        }
    }
}
