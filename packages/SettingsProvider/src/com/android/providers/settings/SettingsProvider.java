/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.hardware.camera2.utils.ArrayUtils;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.android.providers.settings.SettingsState.BaseSetting;
import com.android.providers.settings.SettingsState.Setting;
import cyanogenmod.providers.CMSettings;

/**
 * <p>
 * This class is a content provider that publishes the system settings.
 * It can be accessed via the content provider APIs or via custom call
 * commands. The latter is a bit faster and is the preferred way to access
 * the platform settings.
 * </p>
 * <p>
 * There are three settings types, global (with signature level protection
 * and shared across users), secure (with signature permission level
 * protection and per user), and system (with dangerous permission level
 * protection and per user). Global settings are stored under the device owner.
 * Each of these settings is represented by a {@link
 * com.android.providers.settings.SettingsState} object mapped to an integer
 * key derived from the setting type in the most significant bits and user
 * id in the least significant bits. Settings are synchronously loaded on
 * instantiation of a SettingsState and asynchronously persisted on mutation.
 * Settings are stored in the user specific system directory.
 * </p>
 * <p>
 * Apps targeting APIs Lollipop MR1 and lower can add custom settings entries
 * and get a warning. Targeting higher API version prohibits this as the
 * system settings are not a place for apps to save their state. When a package
 * is removed the settings it added are deleted. Apps cannot delete system
 * settings added by the platform. System settings values are validated to
 * ensure the clients do not put bad values. Global and secure settings are
 * changed only by trusted parties, therefore no validation is performed. Also
 * there is a limit on the amount of app specific settings that can be added
 * to prevent unlimited growth of the system process memory footprint.
 * </p>
 */
@SuppressWarnings("deprecation")
public class SettingsProvider extends ContentProvider {
    private static final boolean DEBUG = false;

    private static final boolean DROP_DATABASE_ON_MIGRATION = !Build.IS_DEBUGGABLE;

    private static final String LOG_TAG = "SettingsProvider";

    private static final String TABLE_SYSTEM = "system";
    private static final String TABLE_SECURE = "secure";
    private static final String TABLE_GLOBAL = "global";

    // Old tables no longer exist.
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_OLD_FAVORITES = "old_favorites";
    private static final String TABLE_BLUETOOTH_DEVICES = "bluetooth_devices";
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String TABLE_ANDROID_METADATA = "android_metadata";

    private static final String HAS_REPLAYED_DEFAULTS_FROM_L = "has_replayed_defaults_from_L";

    // The set of removed legacy tables.
    private static final Set<String> REMOVED_LEGACY_TABLES = new ArraySet<>();
    static {
        REMOVED_LEGACY_TABLES.add(TABLE_FAVORITES);
        REMOVED_LEGACY_TABLES.add(TABLE_OLD_FAVORITES);
        REMOVED_LEGACY_TABLES.add(TABLE_BLUETOOTH_DEVICES);
        REMOVED_LEGACY_TABLES.add(TABLE_BOOKMARKS);
        REMOVED_LEGACY_TABLES.add(TABLE_ANDROID_METADATA);
    }

    private static final int MUTATION_OPERATION_INSERT = 1;
    private static final int MUTATION_OPERATION_DELETE = 2;
    private static final int MUTATION_OPERATION_UPDATE = 3;

    private static final String[] ALL_COLUMNS = new String[] {
            Settings.NameValueTable._ID,
            Settings.NameValueTable.NAME,
            Settings.NameValueTable.VALUE
    };

    private static final Bundle NULL_SETTING = Bundle.forPair(Settings.NameValueTable.VALUE, null);

    // Per user settings that cannot be modified if associated user restrictions are enabled.
    private static final Map<String, String> sSettingToUserRestrictionMap = new ArrayMap<>();
    static {
        sSettingToUserRestrictionMap.put(Settings.Secure.LOCATION_MODE,
                UserManager.DISALLOW_SHARE_LOCATION);
        sSettingToUserRestrictionMap.put(Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                UserManager.DISALLOW_SHARE_LOCATION);
        sSettingToUserRestrictionMap.put(Settings.Secure.INSTALL_NON_MARKET_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        sSettingToUserRestrictionMap.put(Settings.Global.ADB_ENABLED,
                UserManager.DISALLOW_DEBUGGING_FEATURES);
        sSettingToUserRestrictionMap.put(Settings.Global.PACKAGE_VERIFIER_ENABLE,
                UserManager.ENSURE_VERIFY_APPS);
        sSettingToUserRestrictionMap.put(Settings.Global.PREFERRED_NETWORK_MODE,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
    }

    // Per user secure settings that moved to the for all users global settings.
    static final Set<String> sSecureMovedToGlobalSettings = new ArraySet<>();
    static {
        Settings.Secure.getMovedToGlobalSettings(sSecureMovedToGlobalSettings);
    }

    // Per user system settings that moved to the for all users global settings.
    static final Set<String> sSystemMovedToGlobalSettings = new ArraySet<>();
    static {
        Settings.System.getMovedToGlobalSettings(sSystemMovedToGlobalSettings);
    }

    // Per user system settings that moved to the per user secure settings.
    static final Set<String> sSystemMovedToSecureSettings = new ArraySet<>();
    static {
        Settings.System.getMovedToSecureSettings(sSystemMovedToSecureSettings);
    }

    // Per all users global settings that moved to the per user secure settings.
    static final Set<String> sGlobalMovedToSecureSettings = new ArraySet<>();
    static {
        Settings.Global.getMovedToSecureSettings(sGlobalMovedToSecureSettings);
    }

    // Per user secure settings that are cloned for the managed profiles of the user.
    private static final Set<String> sSecureCloneToManagedSettings = new ArraySet<>();
    static {
        Settings.Secure.getCloneToManagedProfileSettings(sSecureCloneToManagedSettings);
    }

    // Per user system settings that are cloned for the managed profiles of the user.
    private static final Set<String> sSystemCloneToManagedSettings = new ArraySet<>();
    static {
        Settings.System.getCloneToManagedProfileSettings(sSystemCloneToManagedSettings);
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private SettingsRegistry mSettingsRegistry;

    // We have to call in the user manager with no lock held,
    private volatile UserManager mUserManager;

    // We have to call in the package manager with no lock held,
    private volatile PackageManager mPackageManager;

    @Override
    public boolean onCreate() {
        synchronized (mLock) {
            mUserManager = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
            mPackageManager = getContext().getPackageManager();
            mSettingsRegistry = new SettingsRegistry();
        }
        registerBroadcastReceivers();
        return true;
    }

    @Override
    public Bundle call(String method, String name, Bundle args) {
        final int requestingUserId = getRequestingUserId(args);
        switch (method) {
            case Settings.CALL_METHOD_GET_GLOBAL: {
                BaseSetting setting = getGlobalSetting(name);
                return packageValueForCallResult(setting);
            }

            case Settings.CALL_METHOD_GET_SECURE: {
                BaseSetting setting = getSecureSetting(name, requestingUserId);
                return packageValueForCallResult(setting);
            }

            case Settings.CALL_METHOD_GET_SYSTEM: {
                BaseSetting setting = getSystemSetting(name, requestingUserId);
                return packageValueForCallResult(setting);
            }

            case Settings.CALL_METHOD_PUT_GLOBAL: {
                String value = getSettingValue(args);
                insertGlobalSetting(name, value, requestingUserId);
                break;
            }

            case Settings.CALL_METHOD_PUT_SECURE: {
                String value = getSettingValue(args);
                insertSecureSetting(name, value, requestingUserId);
                break;
            }

            case Settings.CALL_METHOD_PUT_SYSTEM: {
                String value = getSettingValue(args);
                insertSystemSetting(name, value, requestingUserId);
                break;
            }

            default: {
                Slog.w(LOG_TAG, "call() with invalid method: " + method);
            } break;
        }

        return null;
    }

    @Override
    public String getType(Uri uri) {
        Arguments args = new Arguments(uri, null, null, true);
        if (TextUtils.isEmpty(args.name)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String where, String[] whereArgs,
            String order) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "query() for user: " + UserHandle.getCallingUserId());
        }

        Arguments args = new Arguments(uri, where, whereArgs, true);
        String[] normalizedProjection = normalizeProjection(projection);

        // If a legacy table that is gone, done.
        if (REMOVED_LEGACY_TABLES.contains(args.table)) {
            return new MatrixCursor(normalizedProjection, 0);
        }

        switch (args.table) {
            case TABLE_GLOBAL: {
                if (args.name != null) {
                    BaseSetting setting = getGlobalSetting(args.name);
                    if (CMSettings.Global.shouldInterceptSystemProvider(args.name)) {
                        return forwardedQuery(setting, normalizedProjection, args.table);
                    }
                    return packageSettingForQuery(setting, normalizedProjection);
                } else {
                    return getAllGlobalSettings(projection);
                }
            }

            case TABLE_SECURE: {
                final int userId = UserHandle.getCallingUserId();
                if (args.name != null) {
                    BaseSetting setting = getSecureSetting(args.name, userId);
                    if (CMSettings.Secure.shouldInterceptSystemProvider(args.name)) {
                        return forwardedQuery(setting, normalizedProjection, args.table);
                    }
                    return packageSettingForQuery(setting, normalizedProjection);
                } else {
                    return getAllSecureSettings(userId, projection);
                }
            }

            case TABLE_SYSTEM: {
                final int userId = UserHandle.getCallingUserId();
                if (args.name != null) {
                    BaseSetting setting = getSystemSetting(args.name, userId);
                    if (CMSettings.System.shouldInterceptSystemProvider(args.name)) {
                        return forwardedQuery(setting, normalizedProjection, args.table);
                    }
                    return packageSettingForQuery(setting, normalizedProjection);
                } else {
                    return getAllSystemSettings(userId, projection);
                }
            }

            default: {
                throw new IllegalArgumentException("Invalid Uri path:" + uri);
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "insert() for user: " + UserHandle.getCallingUserId());
        }

        String table = getValidTableOrThrow(uri);

        // If a legacy table that is gone, done.
        if (REMOVED_LEGACY_TABLES.contains(table)) {
            return null;
        }

        String name = values.getAsString(Settings.Secure.NAME);
        if (!isKeyValid(name)) {
            return null;
        }

        String value = values.getAsString(Settings.Secure.VALUE);

        switch (table) {
            case TABLE_GLOBAL: {
                if (insertGlobalSetting(name, value, UserHandle.getCallingUserId())) {
                    return Uri.withAppendedPath(Settings.Global.CONTENT_URI, name);
                }
            } break;

            case TABLE_SECURE: {
                if (insertSecureSetting(name, value, UserHandle.getCallingUserId())) {
                    return Uri.withAppendedPath(Settings.Secure.CONTENT_URI, name);
                }
            } break;

            case TABLE_SYSTEM: {
                if (insertSystemSetting(name, value, UserHandle.getCallingUserId())) {
                    return Uri.withAppendedPath(Settings.System.CONTENT_URI, name);
                }
            } break;

            default: {
                throw new IllegalArgumentException("Bad Uri path:" + uri);
            }
        }

        return null;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] allValues) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "bulkInsert() for user: " + UserHandle.getCallingUserId());
        }

        int insertionCount = 0;
        final int valuesCount = allValues.length;
        for (int i = 0; i < valuesCount; i++) {
            ContentValues values = allValues[i];
            if (insert(uri, values) != null) {
                insertionCount++;
            }
        }

        return insertionCount;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "delete() for user: " + UserHandle.getCallingUserId());
        }

        Arguments args = new Arguments(uri, where, whereArgs, false);

        // If a legacy table that is gone, done.
        if (REMOVED_LEGACY_TABLES.contains(args.table)) {
            return 0;
        }

        if (!isKeyValid(args.name)) {
            return 0;
        }

        switch (args.table) {
            case TABLE_GLOBAL: {
                final int userId = UserHandle.getCallingUserId();
                return deleteGlobalSetting(args.name, userId) ? 1 : 0;
            }

            case TABLE_SECURE: {
                final int userId = UserHandle.getCallingUserId();
                return deleteSecureSetting(args.name, userId) ? 1 : 0;
            }

            case TABLE_SYSTEM: {
                final int userId = UserHandle.getCallingUserId();
                return deleteSystemSetting(args.name, userId) ? 1 : 0;
            }

            default: {
                throw new IllegalArgumentException("Bad Uri path:" + uri);
            }
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "update() for user: " + UserHandle.getCallingUserId());
        }

        Arguments args = new Arguments(uri, where, whereArgs, false);

        // If a legacy table that is gone, done.
        if (REMOVED_LEGACY_TABLES.contains(args.table)) {
            return 0;
        }

        String name = values.getAsString(Settings.Secure.NAME);
        if (!isKeyValid(name)) {
            return 0;
        }
        String value = values.getAsString(Settings.Secure.VALUE);

        switch (args.table) {
            case TABLE_GLOBAL: {
                final int userId = UserHandle.getCallingUserId();
                return updateGlobalSetting(args.name, value, userId) ? 1 : 0;
            }

            case TABLE_SECURE: {
                final int userId = UserHandle.getCallingUserId();
                return updateSecureSetting(args.name, value, userId) ? 1 : 0;
            }

            case TABLE_SYSTEM: {
                final int userId = UserHandle.getCallingUserId();
                return updateSystemSetting(args.name, value, userId) ? 1 : 0;
            }

            default: {
                throw new IllegalArgumentException("Invalid Uri path:" + uri);
            }
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        throw new FileNotFoundException("Direct file access no longer supported; "
                + "ringtone playback is available through android.media.Ringtone");
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLock) {
            final long identity = Binder.clearCallingIdentity();
            try {
                List<UserInfo> users = mUserManager.getUsers(true);
                final int userCount = users.size();
                for (int i = 0; i < userCount; i++) {
                    UserInfo user = users.get(i);
                    dumpForUser(user.id, pw);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private void dumpForUser(int userId, PrintWriter pw) {
        if (userId == UserHandle.USER_OWNER) {
            pw.println("GLOBAL SETTINGS (user " + userId + ")");
            Cursor globalCursor = getAllGlobalSettings(ALL_COLUMNS);
            dumpSettings(globalCursor, pw);
            pw.println();
        }

        pw.println("SECURE SETTINGS (user " + userId + ")");
        Cursor secureCursor = getAllSecureSettings(userId, ALL_COLUMNS);
        dumpSettings(secureCursor, pw);
        pw.println();

        pw.println("SYSTEM SETTINGS (user " + userId + ")");
        Cursor systemCursor = getAllSystemSettings(userId, ALL_COLUMNS);
        dumpSettings(systemCursor, pw);
        pw.println();
    }

    private void dumpSettings(Cursor cursor, PrintWriter pw) {
        if (cursor == null || !cursor.moveToFirst()) {
            return;
        }

        final int idColumnIdx = cursor.getColumnIndex(Settings.NameValueTable._ID);
        final int nameColumnIdx = cursor.getColumnIndex(Settings.NameValueTable.NAME);
        final int valueColumnIdx = cursor.getColumnIndex(Settings.NameValueTable.VALUE);

        do {
            pw.append("_id:").append(toDumpString(cursor.getString(idColumnIdx)));
            pw.append(" name:").append(toDumpString(cursor.getString(nameColumnIdx)));
            pw.append(" value:").append(toDumpString(cursor.getString(valueColumnIdx)));
            pw.println();
        } while (cursor.moveToNext());
    }

    private static String toDumpString(String s) {
        if (s != null) {
            return s;
        }
        return "{null}";
    }

    private void registerBroadcastReceivers() {
        IntentFilter userFilter = new IntentFilter();
        userFilter.addAction(Intent.ACTION_USER_REMOVED);
        userFilter.addAction(Intent.ACTION_USER_STOPPED);

        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                        UserHandle.USER_OWNER);

                switch (intent.getAction()) {
                    case Intent.ACTION_USER_REMOVED: {
                        mSettingsRegistry.removeUserStateLocked(userId, true);
                    } break;

                    case Intent.ACTION_USER_STOPPED: {
                        mSettingsRegistry.removeUserStateLocked(userId, false);
                    } break;
                }
            }
        }, userFilter);

        PackageMonitor monitor = new PackageMonitor() {
            @Override
            public void onPackageRemoved(String packageName, int uid) {
                synchronized (mLock) {
                    mSettingsRegistry.onPackageRemovedLocked(packageName,
                            UserHandle.getUserId(uid));
                }
            }
        };

        // package changes
        monitor.register(getContext(), BackgroundThread.getHandler().getLooper(),
                UserHandle.ALL, true);
    }

    private Cursor getAllGlobalSettings(String[] projection) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getAllGlobalSettings()");
        }

        synchronized (mLock) {
            // Get the settings.
            SettingsState settingsState = mSettingsRegistry.getSettingsLocked(
                    SettingsRegistry.SETTINGS_TYPE_GLOBAL, UserHandle.USER_OWNER);

            List<String> names = settingsState.getSettingNamesLocked();

            final int nameCount = names.size();

            String[] normalizedProjection = normalizeProjection(projection);
            MatrixCursor result = new MatrixCursor(normalizedProjection, nameCount);

            // Anyone can get the global settings, so no security checks.
            for (int i = 0; i < nameCount; i++) {
                String name = names.get(i);
                Setting setting = settingsState.getSettingLocked(name);
                appendSettingToCursor(result, setting);
            }

            return result;
        }
    }

    private BaseSetting getGlobalSetting(String name) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getGlobalSetting(" + name + ")");
        }

        // Get the value.
        BaseSetting setting;
        synchronized (mLock) {
            setting = mSettingsRegistry.getSettingLocked(SettingsRegistry.SETTINGS_TYPE_GLOBAL,
                    UserHandle.USER_OWNER, name);
        }

        // If CMSettingsProvider owns this key, override the value
        if (CMSettings.Global.shouldInterceptSystemProvider(name)) {
            final ContentResolver cr = getContext().getContentResolver();
            final String value = CMSettings.Global.
                    getStringForUser(cr, name, UserHandle.USER_OWNER);

            if (setting == null) {
                setting = new BaseSetting(name, value, null);
            } else {
                setting.update(value, null);
            }
        }

        return setting;
    }

    private boolean updateGlobalSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "updateGlobalSetting(" + name + ", " + value + ")");
        }
        return mutateGlobalSetting(name, value, requestingUserId, MUTATION_OPERATION_UPDATE);
    }

    private boolean insertGlobalSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "insertGlobalSetting(" + name + ", " + value + ")");
        }
        return mutateGlobalSetting(name, value, requestingUserId, MUTATION_OPERATION_INSERT);
    }

    private boolean deleteGlobalSetting(String name, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "deleteGlobalSettingLocked(" + name + ")");
        }
        return mutateGlobalSetting(name, null, requestingUserId, MUTATION_OPERATION_DELETE);
    }

    private boolean mutateGlobalSetting(String name, String value, int requestingUserId,
            int operation) {
        // Make sure the caller can change the settings - treated as secure.
        enforceWritePermission(Manifest.permission.WRITE_SECURE_SETTINGS);

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(requestingUserId);

        // If this is a setting that is currently restricted for this user, done.
        if (isGlobalOrSecureSettingRestrictedForUser(name, callingUserId)) {
            return false;
        }

        // If CMSettingsProvider wants to own this key, let it.
        if (CMSettings.Global.shouldInterceptSystemProvider(name)) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT:
                case MUTATION_OPERATION_UPDATE:
                    final ContentResolver cr = getContext().getContentResolver();
                    return CMSettings.Global.putStringForUser(cr, name, value,
                            UserHandle.USER_OWNER);
                case MUTATION_OPERATION_DELETE:
                    // unsupported
                    return false;
            }
        }

        // Perform the mutation.
        synchronized (mLock) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT: {
                    return mSettingsRegistry
                            .insertSettingLocked(SettingsRegistry.SETTINGS_TYPE_GLOBAL,
                                    UserHandle.USER_OWNER, name, value, getCallingPackage());
                }

                case MUTATION_OPERATION_DELETE: {
                    return mSettingsRegistry.deleteSettingLocked(
                            SettingsRegistry.SETTINGS_TYPE_GLOBAL,
                            UserHandle.USER_OWNER, name);
                }

                case MUTATION_OPERATION_UPDATE: {
                    return mSettingsRegistry
                            .updateSettingLocked(SettingsRegistry.SETTINGS_TYPE_GLOBAL,
                                    UserHandle.USER_OWNER, name, value, getCallingPackage());
                }
            }
        }

        return false;
    }

    private Cursor getAllSecureSettings(int userId, String[] projection) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getAllSecureSettings(" + userId + ")");
        }

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(userId);

        synchronized (mLock) {
            List<String> names = mSettingsRegistry.getSettingsNamesLocked(
                    SettingsRegistry.SETTINGS_TYPE_SECURE, callingUserId);

            final int nameCount = names.size();

            String[] normalizedProjection = normalizeProjection(projection);
            MatrixCursor result = new MatrixCursor(normalizedProjection, nameCount);

            for (int i = 0; i < nameCount; i++) {
                String name = names.get(i);
                // Determine the owning user as some profile settings are cloned from the parent.
                final int owningUserId = resolveOwningUserIdForSecureSettingLocked(callingUserId,
                        name);

                // Special case for location (sigh).
                if (isLocationProvidersAllowedRestricted(name, callingUserId, owningUserId)) {
                    return null;
                }

                Setting setting = mSettingsRegistry.getSettingLocked(
                        SettingsRegistry.SETTINGS_TYPE_SECURE, owningUserId, name);
                appendSettingToCursor(result, setting);
            }

            return result;
        }
    }

    private BaseSetting getSecureSetting(String name, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getSecureSetting(" + name + ", " + requestingUserId + ")");
        }

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(requestingUserId);

        // Determine the owning user as some profile settings are cloned from the parent.
        final int owningUserId = resolveOwningUserIdForSecureSettingLocked(callingUserId, name);

        // Special case for location (sigh).
        if (isLocationProvidersAllowedRestricted(name, callingUserId, owningUserId)) {
            return null;
        }

        // Get the value.
        BaseSetting setting;
        synchronized (mLock) {
            setting = mSettingsRegistry.getSettingLocked(SettingsRegistry.SETTINGS_TYPE_SECURE,
                    owningUserId, name);
        }

        // If CMSettingsProvider owns this key, override the value
        if (CMSettings.Secure.shouldInterceptSystemProvider(name)) {
            final ContentResolver cr = getContext().getContentResolver();
            final String value = CMSettings.Secure.getStringForUser(cr, name, owningUserId);

            if (setting == null) {
                setting = new BaseSetting(name, value, null);
            } else {
                setting.update(value, null);
            }
        }

        return setting;
    }

    private boolean insertSecureSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "insertSecureSetting(" + name + ", " + value + ", "
                    + requestingUserId + ")");
        }

        return mutateSecureSetting(name, value, requestingUserId, MUTATION_OPERATION_INSERT);
    }

    private boolean deleteSecureSetting(String name, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "deleteSecureSetting(" + name + ", " + requestingUserId + ")");
        }

        return mutateSecureSetting(name, null, requestingUserId, MUTATION_OPERATION_DELETE);
    }

    private boolean updateSecureSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "updateSecureSetting(" + name + ", " + value + ", "
                    + requestingUserId + ")");
        }

        return mutateSecureSetting(name, value, requestingUserId, MUTATION_OPERATION_UPDATE);
    }

    private boolean mutateSecureSetting(String name, String value, int requestingUserId,
            int operation) {
        // Make sure the caller can change the settings.
        enforceWritePermission(Manifest.permission.WRITE_SECURE_SETTINGS);

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(requestingUserId);

        // If this is a setting that is currently restricted for this user, done.
        if (isGlobalOrSecureSettingRestrictedForUser(name, callingUserId)) {
            return false;
        }

        // Determine the owning user as some profile settings are cloned from the parent.
        final int owningUserId = resolveOwningUserIdForSecureSettingLocked(callingUserId, name);

        // Only the owning user can change the setting.
        if (owningUserId != callingUserId) {
            return false;
        }

        // Special cases for location providers (sigh).
        if (Settings.Secure.LOCATION_PROVIDERS_ALLOWED.equals(name)) {
            return updateLocationProvidersAllowedLocked(value, owningUserId);
        }

        // If CMSettingsProvider wants to own this key, let it.
        if (CMSettings.Secure.shouldInterceptSystemProvider(name)) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT:
                case MUTATION_OPERATION_UPDATE:
                    final ContentResolver cr = getContext().getContentResolver();
                    return CMSettings.Secure.putStringForUser(cr, name, value, owningUserId);
                case MUTATION_OPERATION_DELETE:
                    // unsupported
                    return false;
            }
        }

        // Mutate the value.
        synchronized (mLock) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT: {
                    return mSettingsRegistry
                            .insertSettingLocked(SettingsRegistry.SETTINGS_TYPE_SECURE,
                                    owningUserId, name, value, getCallingPackage());
                }

                case MUTATION_OPERATION_DELETE: {
                    return mSettingsRegistry.deleteSettingLocked(
                            SettingsRegistry.SETTINGS_TYPE_SECURE,
                            owningUserId, name);
                }

                case MUTATION_OPERATION_UPDATE: {
                    return mSettingsRegistry
                            .updateSettingLocked(SettingsRegistry.SETTINGS_TYPE_SECURE,
                                    owningUserId, name, value, getCallingPackage());
                }
            }
        }

        return false;
    }

    private Cursor getAllSystemSettings(int userId, String[] projection) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getAllSecureSystem(" + userId + ")");
        }

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(userId);

        synchronized (mLock) {
            List<String> names = mSettingsRegistry.getSettingsNamesLocked(
                    SettingsRegistry.SETTINGS_TYPE_SYSTEM, callingUserId);

            final int nameCount = names.size();

            String[] normalizedProjection = normalizeProjection(projection);
            MatrixCursor result = new MatrixCursor(normalizedProjection, nameCount);

            for (int i = 0; i < nameCount; i++) {
                String name = names.get(i);

                // Determine the owning user as some profile settings are cloned from the parent.
                final int owningUserId = resolveOwningUserIdForSystemSettingLocked(callingUserId,
                        name);

                Setting setting = mSettingsRegistry.getSettingLocked(
                        SettingsRegistry.SETTINGS_TYPE_SYSTEM, owningUserId, name);
                appendSettingToCursor(result, setting);
            }

            return result;
        }
    }

    private BaseSetting getSystemSetting(String name, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "getSystemSetting(" + name + ", " + requestingUserId + ")");
        }

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(requestingUserId);

        // Determine the owning user as some profile settings are cloned from the parent.
        final int owningUserId = resolveOwningUserIdForSystemSettingLocked(callingUserId, name);

        // Get the value.
        BaseSetting setting;
        synchronized (mLock) {
            setting = mSettingsRegistry.getSettingLocked(SettingsRegistry.SETTINGS_TYPE_SYSTEM,
                    owningUserId, name);
        }

        // If CMSettingsProvider owns this key, override the value
        if (CMSettings.System.shouldInterceptSystemProvider(name)) {
            final ContentResolver cr = getContext().getContentResolver();
            final String value = CMSettings.System.getStringForUser(cr, name, owningUserId);

            if (setting == null) {
                setting = new BaseSetting(name, value, null);
            } else {
                setting.update(value, null);
            }
        }

        return setting;
    }

    private boolean insertSystemSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "insertSystemSetting(" + name + ", " + value + ", "
                    + requestingUserId + ")");
        }

        return mutateSystemSetting(name, value, requestingUserId, MUTATION_OPERATION_INSERT);
    }

    private boolean deleteSystemSetting(String name, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "deleteSystemSetting(" + name + ", " + requestingUserId + ")");
        }

        return mutateSystemSetting(name, null, requestingUserId, MUTATION_OPERATION_DELETE);
    }

    private boolean updateSystemSetting(String name, String value, int requestingUserId) {
        if (DEBUG) {
            Slog.v(LOG_TAG, "updateSystemSetting(" + name + ", " + value + ", "
                    + requestingUserId + ")");
        }

        return mutateSystemSetting(name, value, requestingUserId, MUTATION_OPERATION_UPDATE);
    }

    private boolean mutateSystemSetting(String name, String value, int runAsUserId,
            int operation) {
        if (!hasWriteSecureSettingsPermission()) {
            // If the caller doesn't hold WRITE_SECURE_SETTINGS, we verify whether this
            // operation is allowed for the calling package through appops.
            if (!Settings.checkAndNoteWriteSettingsOperation(getContext(),
                    Binder.getCallingUid(), getCallingPackage(), true)) {
                return false;
            }
        }

        // Enforce what the calling package can mutate the system settings.
        enforceRestrictedSystemSettingsMutationForCallingPackage(operation, name);

        // Resolve the userId on whose behalf the call is made.
        final int callingUserId = resolveCallingUserIdEnforcingPermissionsLocked(runAsUserId);

        // Determine the owning user as some profile settings are cloned from the parent.
        final int owningUserId = resolveOwningUserIdForSystemSettingLocked(callingUserId, name);

        // Only the owning user id can change the setting.
        if (owningUserId != callingUserId) {
            return false;
        }

        // If CMSettingsProvider wants to own this key, let it.
        if (CMSettings.System.shouldInterceptSystemProvider(name)) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT:
                case MUTATION_OPERATION_UPDATE:
                    final ContentResolver cr = getContext().getContentResolver();
                    return CMSettings.System.putStringForUser(cr, name, value, owningUserId);
                case MUTATION_OPERATION_DELETE:
                    // unsupported
                    return false;
            }
        }

        // Mutate the value.
        synchronized (mLock) {
            switch (operation) {
                case MUTATION_OPERATION_INSERT: {
                    validateSystemSettingValue(name, value);
                    return mSettingsRegistry
                            .insertSettingLocked(SettingsRegistry.SETTINGS_TYPE_SYSTEM,
                                    owningUserId, name, value, getCallingPackage());
                }

                case MUTATION_OPERATION_DELETE: {
                    return mSettingsRegistry.deleteSettingLocked(
                            SettingsRegistry.SETTINGS_TYPE_SYSTEM,
                            owningUserId, name);
                }

                case MUTATION_OPERATION_UPDATE: {
                    validateSystemSettingValue(name, value);
                    return mSettingsRegistry
                            .updateSettingLocked(SettingsRegistry.SETTINGS_TYPE_SYSTEM,
                                    owningUserId, name, value, getCallingPackage());
                }
            }

            return false;
        }
    }

    private boolean hasWriteSecureSettingsPermission() {
        // Write secure settings is a more protected permission. If caller has it we are good.
        if (getContext().checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        return false;
    }

    private void validateSystemSettingValue(String name, String value) {
        Settings.System.Validator validator = Settings.System.VALIDATORS.get(name);
        if (validator != null && !validator.validate(value)) {
            throw new IllegalArgumentException("Invalid value: " + value
                    + " for setting: " + name);
        }
    }

    private boolean isLocationProvidersAllowedRestricted(String name, int callingUserId,
            int owningUserId) {
        // Optimization - location providers are restricted only for managed profiles.
        if (callingUserId == owningUserId) {
            return false;
        }
        if (Settings.Secure.LOCATION_PROVIDERS_ALLOWED.equals(name)
                && mUserManager.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION,
                new UserHandle(callingUserId))) {
            return true;
        }
        return false;
    }

    private boolean isGlobalOrSecureSettingRestrictedForUser(String setting, int userId) {
        String restriction = sSettingToUserRestrictionMap.get(setting);
        if (restriction == null) {
            return false;
        }
        return mUserManager.hasUserRestriction(restriction, new UserHandle(userId));
    }

    private int resolveOwningUserIdForSecureSettingLocked(int userId, String setting) {
        return resolveOwningUserIdLocked(userId, sSecureCloneToManagedSettings, setting);
    }

    private int resolveOwningUserIdForSystemSettingLocked(int userId, String setting) {
        return resolveOwningUserIdLocked(userId, sSystemCloneToManagedSettings, setting);
    }

    private int resolveOwningUserIdLocked(int userId, Set<String> keys, String name) {
        final int parentId = getGroupParentLocked(userId);
        if (parentId != userId && keys.contains(name)) {
            return parentId;
        }
        return userId;
    }

    private void enforceRestrictedSystemSettingsMutationForCallingPackage(int operation,
            String name) {
        // System/root/shell can mutate whatever secure settings they want.
        final int callingUid = Binder.getCallingUid();
        if (callingUid == android.os.Process.SYSTEM_UID
                || callingUid == Process.SHELL_UID
                || callingUid == Process.ROOT_UID) {
            return;
        }

        if (CMSettings.System.shouldInterceptSystemProvider(name)) {
            // this setting will be forwarded to CMSettingsProvider
            return;
        }

        switch (operation) {
            case MUTATION_OPERATION_INSERT:
                // Insert updates.
            case MUTATION_OPERATION_UPDATE: {
                if (Settings.System.PUBLIC_SETTINGS.contains(name)) {
                    return;
                }

                // The calling package is already verified.
                PackageInfo packageInfo = getCallingPackageInfoOrThrow();

                // Privileged apps can do whatever they want.
                if ((packageInfo.applicationInfo.privateFlags
                        & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0) {
                    return;
                }

                warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk(
                        packageInfo.applicationInfo.targetSdkVersion, name);
            } break;

            case MUTATION_OPERATION_DELETE: {
                if (Settings.System.PUBLIC_SETTINGS.contains(name)
                        || Settings.System.PRIVATE_SETTINGS.contains(name)) {
                    throw new IllegalArgumentException("You cannot delete system defined"
                            + " secure settings.");
                }

                // The calling package is already verified.
                PackageInfo packageInfo = getCallingPackageInfoOrThrow();

                // Privileged apps can do whatever they want.
                if ((packageInfo.applicationInfo.privateFlags &
                        ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0) {
                    return;
                }

                warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk(
                        packageInfo.applicationInfo.targetSdkVersion, name);
            } break;
        }
    }

    private PackageInfo getCallingPackageInfoOrThrow() {
        try {
            return mPackageManager.getPackageInfo(getCallingPackage(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Calling package doesn't exist");
        }
    }

    private int getGroupParentLocked(int userId) {
        // Most frequent use case.
        if (userId == UserHandle.USER_OWNER) {
            return userId;
        }
        // We are in the same process with the user manager and the returned
        // user info is a cached instance, so just look up instead of cache.
        final long identity = Binder.clearCallingIdentity();
        try {
            // Just a lookup and not reentrant, so holding a lock is fine.
            UserInfo userInfo = mUserManager.getProfileParent(userId);
            return (userInfo != null) ? userInfo.id : userId;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void enforceWritePermission(String permission) {
        if (getContext().checkCallingOrSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission denial: writing to settings requires:"
                    + permission);
        }
    }

    /*
     * Used to parse changes to the value of Settings.Secure.LOCATION_PROVIDERS_ALLOWED.
     * This setting contains a list of the currently enabled location providers.
     * But helper functions in android.providers.Settings can enable or disable
     * a single provider by using a "+" or "-" prefix before the provider name.
     *
     * @returns whether the enabled location providers changed.
     */
    private boolean updateLocationProvidersAllowedLocked(String value, int owningUserId) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }

        final char prefix = value.charAt(0);
        if (prefix != '+' && prefix != '-') {
            return false;
        }

        // skip prefix
        value = value.substring(1);

        BaseSetting settingValue = getSecureSetting(
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED, owningUserId);

        String oldProviders = (settingValue != null) ? settingValue.getValue() : "";

        int index = oldProviders.indexOf(value);
        int end = index + value.length();

        // check for commas to avoid matching on partial string
        if (index > 0 && oldProviders.charAt(index - 1) != ',') {
            index = -1;
        }

        // check for commas to avoid matching on partial string
        if (end < oldProviders.length() && oldProviders.charAt(end) != ',') {
            index = -1;
        }

        String newProviders;

        if (prefix == '+' && index < 0) {
            // append the provider to the list if not present
            if (oldProviders.length() == 0) {
                newProviders = value;
            } else {
                newProviders = oldProviders + ',' + value;
            }
        } else if (prefix == '-' && index >= 0) {
            // remove the provider from the list if present
            // remove leading or trailing comma
            if (index > 0) {
                index--;
            } else if (end < oldProviders.length()) {
                end++;
            }

            newProviders = oldProviders.substring(0, index);
            if (end < oldProviders.length()) {
                newProviders += oldProviders.substring(end);
            }
        } else {
            // nothing changed, so no need to update the database
            return false;
        }

        return mSettingsRegistry.insertSettingLocked(SettingsRegistry.SETTINGS_TYPE_SECURE,
                owningUserId, Settings.Secure.LOCATION_PROVIDERS_ALLOWED, newProviders,
                getCallingPackage());
    }

    private static void warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk(
            int targetSdkVersion, String name) {
        // If the app targets Lollipop MR1 or older SDK we warn, otherwise crash.
        if (targetSdkVersion <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (Settings.System.PRIVATE_SETTINGS.contains(name)) {
                Slog.w(LOG_TAG, "You shouldn't not change private system settings."
                        + " This will soon become an error.");
            } else {
                Slog.w(LOG_TAG, "You shouldn't keep your settings in the secure settings."
                        + " This will soon become an error.");
            }
        } else {
            if (Settings.System.PRIVATE_SETTINGS.contains(name)) {
                throw new IllegalArgumentException("You cannot change private secure settings.");
            } else {
                throw new IllegalArgumentException("You cannot keep your settings in"
                        + " the secure settings.");
            }
        }
    }

    private static int resolveCallingUserIdEnforcingPermissionsLocked(int requestingUserId) {
        if (requestingUserId == UserHandle.getCallingUserId()) {
            return requestingUserId;
        }
        return ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), requestingUserId, false, true,
                "get/set setting for user", null);
    }

    private static Bundle packageValueForCallResult(BaseSetting setting) {
        if (setting == null) {
            return NULL_SETTING;
        }
        return Bundle.forPair(Settings.NameValueTable.VALUE, setting.getValue());
    }

    private static int getRequestingUserId(Bundle args) {
        final int callingUserId = UserHandle.getCallingUserId();
        return (args != null) ? args.getInt(Settings.CALL_METHOD_USER_KEY, callingUserId)
                : callingUserId;
    }

    private static String getSettingValue(Bundle args) {
        return (args != null) ? args.getString(Settings.NameValueTable.VALUE) : null;
    }

    private static String getValidTableOrThrow(Uri uri) {
        if (uri.getPathSegments().size() > 0) {
            String table = uri.getPathSegments().get(0);
            if (DatabaseHelper.isValidTable(table)) {
                return table;
            }
            throw new IllegalArgumentException("Bad root path: " + table);
        }
        throw new IllegalArgumentException("Invalid URI:" + uri);
    }

    private MatrixCursor forwardedQuery(BaseSetting setting, String[] projection, String table) {
        if (setting == null) {
            return new MatrixCursor(projection, 0);
        }
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        switch (table) {
            case TABLE_SYSTEM:
                setting.update(CMSettings.System.getString(getContext().getContentResolver(),
                                setting.getName()), setting.getPackageName());
                break;
            case TABLE_GLOBAL:
                setting.update(CMSettings.Global.getString(getContext().getContentResolver(),
                        setting.getName()), setting.getPackageName());
                break;
            case TABLE_SECURE:
                setting.update(CMSettings.Secure.getString(getContext().getContentResolver(),
                        setting.getName()), setting.getPackageName());
                break;

        }
        appendSettingToCursor(cursor, setting);
        return cursor;
    }

    private static MatrixCursor packageSettingForQuery(BaseSetting setting, String[] projection) {
        if (setting == null) {
            return new MatrixCursor(projection, 0);
        }
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        appendSettingToCursor(cursor, setting);
        return cursor;
    }

    private static String[] normalizeProjection(String[] projection) {
        if (projection == null) {
            return ALL_COLUMNS;
        }

        final int columnCount = projection.length;
        for (int i = 0; i < columnCount; i++) {
            String column = projection[i];
            if (!ArrayUtils.contains(ALL_COLUMNS, column)) {
                throw new IllegalArgumentException("Invalid column: " + column);
            }
        }

        return projection;
    }

    private static void appendSettingToCursor(MatrixCursor cursor, BaseSetting setting) {
        final int columnCount = cursor.getColumnCount();

        String[] values =  new String[columnCount];

        for (int i = 0; i < columnCount; i++) {
            String column = cursor.getColumnName(i);

            switch (column) {
                case Settings.NameValueTable._ID: {
                    values[i] = setting.getId();
                } break;

                case Settings.NameValueTable.NAME: {
                    values[i] = setting.getName();
                } break;

                case Settings.NameValueTable.VALUE: {
                    values[i] = setting.getValue();
                } break;
            }
        }

        cursor.addRow(values);
    }

    private static boolean isKeyValid(String key) {
        return !(TextUtils.isEmpty(key) || SettingsState.isBinary(key));
    }

    private static final class Arguments {
        private static final Pattern WHERE_PATTERN_WITH_PARAM_NO_BRACKETS =
                Pattern.compile("[\\s]*name[\\s]*=[\\s]*\\?[\\s]*");

        private static final Pattern WHERE_PATTERN_WITH_PARAM_IN_BRACKETS =
                Pattern.compile("[\\s]*\\([\\s]*name[\\s]*=[\\s]*\\?[\\s]*\\)[\\s]*");

        private static final Pattern WHERE_PATTERN_NO_PARAM_IN_BRACKETS =
                Pattern.compile("[\\s]*\\([\\s]*name[\\s]*=[\\s]*['\"].*['\"][\\s]*\\)[\\s]*");

        private static final Pattern WHERE_PATTERN_NO_PARAM_NO_BRACKETS =
                Pattern.compile("[\\s]*name[\\s]*=[\\s]*['\"].*['\"][\\s]*");

        public final String table;
        public final String name;

        public Arguments(Uri uri, String where, String[] whereArgs, boolean supportAll) {
            final int segmentSize = uri.getPathSegments().size();
            switch (segmentSize) {
                case 1: {
                    if (where != null
                            && (WHERE_PATTERN_WITH_PARAM_NO_BRACKETS.matcher(where).matches()
                                || WHERE_PATTERN_WITH_PARAM_IN_BRACKETS.matcher(where).matches())
                            && whereArgs.length == 1) {
                        name = whereArgs[0];
                        table = computeTableForSetting(uri, name);
                        return;
                    } else if (where != null
                            && (WHERE_PATTERN_NO_PARAM_NO_BRACKETS.matcher(where).matches()
                                || WHERE_PATTERN_NO_PARAM_IN_BRACKETS.matcher(where).matches())) {
                        final int startIndex = Math.max(where.indexOf("'"),
                                where.indexOf("\"")) + 1;
                        final int endIndex = Math.max(where.lastIndexOf("'"),
                                where.lastIndexOf("\""));
                        name = where.substring(startIndex, endIndex);
                        table = computeTableForSetting(uri, name);
                        return;
                    } else if (supportAll && where == null && whereArgs == null) {
                        name = null;
                        table = computeTableForSetting(uri, null);
                        return;
                    }
                } break;

                case 2: {
                    if (where == null && whereArgs == null) {
                        name = uri.getPathSegments().get(1);
                        table = computeTableForSetting(uri, name);
                        return;
                    }
                } break;
            }

            EventLogTags.writeUnsupportedSettingsQuery(
                    uri.toSafeString(), where, Arrays.toString(whereArgs));
            String message = String.format( "Supported SQL:\n"
                    + "  uri content://some_table/some_property with null where and where args\n"
                    + "  uri content://some_table with query name=? and single name as arg\n"
                    + "  uri content://some_table with query name=some_name and null args\n"
                    + "  but got - uri:%1s, where:%2s whereArgs:%3s", uri, where,
                    Arrays.toString(whereArgs));
            throw new IllegalArgumentException(message);
        }

        private static String computeTableForSetting(Uri uri, String name) {
            String table = getValidTableOrThrow(uri);

            if (name != null) {
                if (sSystemMovedToSecureSettings.contains(name)) {
                    table = TABLE_SECURE;
                }

                if (sSystemMovedToGlobalSettings.contains(name)) {
                    table = TABLE_GLOBAL;
                }

                if (sSecureMovedToGlobalSettings.contains(name)) {
                    table = TABLE_GLOBAL;
                }

                if (sGlobalMovedToSecureSettings.contains(name)) {
                    table = TABLE_SECURE;
                }
            }

            return table;
        }
    }

    final class SettingsRegistry {
        private static final String DROPBOX_TAG_USERLOG = "restricted_profile_ssaid";

        private static final int SETTINGS_TYPE_GLOBAL = 0;
        private static final int SETTINGS_TYPE_SYSTEM = 1;
        private static final int SETTINGS_TYPE_SECURE = 2;

        private static final int SETTINGS_TYPE_MASK = 0xF0000000;
        private static final int SETTINGS_TYPE_SHIFT = 28;

        private static final String SETTINGS_FILE_GLOBAL = "settings_global.xml";
        private static final String SETTINGS_FILE_SYSTEM = "settings_system.xml";
        private static final String SETTINGS_FILE_SECURE = "settings_secure.xml";

        private final SparseArray<SettingsState> mSettingsStates = new SparseArray<>();

        private final BackupManager mBackupManager;

        private final Handler mHandler;

        public SettingsRegistry() {
            mBackupManager = new BackupManager(getContext());
            mHandler = new MyHandler(getContext().getMainLooper());
            migrateAllLegacySettingsIfNeeded();
        }

        public List<String> getSettingsNamesLocked(int type, int userId) {
            final int key = makeKey(type, userId);
            SettingsState settingsState = peekSettingsStateLocked(key);
            return settingsState.getSettingNamesLocked();
        }

        public SettingsState getSettingsLocked(int type, int userId) {
            final int key = makeKey(type, userId);
            return peekSettingsStateLocked(key);
        }

        public void ensureSettingsForUserLocked(int userId) {
            // Migrate the setting for this user if needed.
            migrateLegacySettingsForUserIfNeededLocked(userId);

            // Ensure global settings loaded if owner.
            if (userId == UserHandle.USER_OWNER) {
                final int globalKey = makeKey(SETTINGS_TYPE_GLOBAL, UserHandle.USER_OWNER);
                ensureSettingsStateLocked(globalKey);
            }

            // Ensure secure settings loaded.
            final int secureKey = makeKey(SETTINGS_TYPE_SECURE, userId);
            ensureSettingsStateLocked(secureKey);

            // Make sure the secure settings have an Android id set.
            SettingsState secureSettings = getSettingsLocked(SETTINGS_TYPE_SECURE, userId);
            ensureSecureSettingAndroidIdSetLocked(secureSettings);

            // Ensure system settings loaded.
            final int systemKey = makeKey(SETTINGS_TYPE_SYSTEM, userId);
            ensureSettingsStateLocked(systemKey);

            // Upgrade the settings to the latest version.
            UpgradeController upgrader = new UpgradeController(userId);
            upgrader.upgradeIfNeededLocked();
        }

        private void ensureSettingsStateLocked(int key) {
            if (mSettingsStates.get(key) == null) {
                final int maxBytesPerPackage = getMaxBytesPerPackageForType(getTypeFromKey(key));
                SettingsState settingsState = new SettingsState(mLock, getSettingsFile(key), key,
                        maxBytesPerPackage);
                mSettingsStates.put(key, settingsState);
            }
        }

        public void removeUserStateLocked(int userId, boolean permanently) {
            // We always keep the global settings in memory.

            // Nuke system settings.
            final int systemKey = makeKey(SETTINGS_TYPE_SYSTEM, userId);
            final SettingsState systemSettingsState = mSettingsStates.get(systemKey);
            if (systemSettingsState != null) {
                if (permanently) {
                    mSettingsStates.remove(systemKey);
                    systemSettingsState.destroyLocked(null);
                } else {
                    systemSettingsState.destroyLocked(new Runnable() {
                        @Override
                        public void run() {
                            mSettingsStates.remove(systemKey);
                        }
                    });
                }
            }

            // Nuke secure settings.
            final int secureKey = makeKey(SETTINGS_TYPE_SECURE, userId);
            final SettingsState secureSettingsState = mSettingsStates.get(secureKey);
            if (secureSettingsState != null) {
                if (permanently) {
                    mSettingsStates.remove(secureKey);
                    secureSettingsState.destroyLocked(null);
                } else {
                    secureSettingsState.destroyLocked(new Runnable() {
                        @Override
                        public void run() {
                            mSettingsStates.remove(secureKey);
                        }
                    });
                }
            }
        }

        public boolean insertSettingLocked(int type, int userId, String name, String value,
                String packageName) {
            final int key = makeKey(type, userId);

            SettingsState settingsState = peekSettingsStateLocked(key);
            final boolean success = settingsState.insertSettingLocked(name, value, packageName);

            if (success) {
                notifyForSettingsChange(key, name);
            }
            return success;
        }

        public boolean deleteSettingLocked(int type, int userId, String name) {
            final int key = makeKey(type, userId);

            SettingsState settingsState = peekSettingsStateLocked(key);
            final boolean success = settingsState.deleteSettingLocked(name);

            if (success) {
                notifyForSettingsChange(key, name);
            }
            return success;
        }

        public Setting getSettingLocked(int type, int userId, String name) {
            final int key = makeKey(type, userId);

            SettingsState settingsState = peekSettingsStateLocked(key);
            return settingsState.getSettingLocked(name);
        }

        public boolean updateSettingLocked(int type, int userId, String name, String value,
                String packageName) {
            final int key = makeKey(type, userId);

            SettingsState settingsState = peekSettingsStateLocked(key);
            final boolean success = settingsState.updateSettingLocked(name, value, packageName);

            if (success) {
                notifyForSettingsChange(key, name);
            }

            return success;
        }

        public void onPackageRemovedLocked(String packageName, int userId) {
            // Global and secure settings are signature protected. Apps signed
            // by the platform certificate are generally not uninstalled  and
            // the main exception is tests. We trust components signed
            // by the platform certificate and do not do a clean up after them.

            final int systemKey = makeKey(SETTINGS_TYPE_SYSTEM, userId);
            SettingsState systemSettings = mSettingsStates.get(systemKey);
            if (systemSettings != null) {
                systemSettings.onPackageRemovedLocked(packageName);
            }
        }

        private SettingsState peekSettingsStateLocked(int key) {
            SettingsState settingsState = mSettingsStates.get(key);
            if (settingsState != null) {
                return settingsState;
            }

            ensureSettingsForUserLocked(getUserIdFromKey(key));
            return mSettingsStates.get(key);
        }

        private void migrateAllLegacySettingsIfNeeded() {
            synchronized (mLock) {
                final int key = makeKey(SETTINGS_TYPE_GLOBAL, UserHandle.USER_OWNER);
                File globalFile = getSettingsFile(key);
                if (globalFile.exists()) {
                    return;
                }

                final long identity = Binder.clearCallingIdentity();
                try {
                    List<UserInfo> users = mUserManager.getUsers(true);

                    final int userCount = users.size();
                    for (int i = 0; i < userCount; i++) {
                        final int userId = users.get(i).id;

                        DatabaseHelper dbHelper = new DatabaseHelper(getContext(), userId);
                        SQLiteDatabase database = dbHelper.getWritableDatabase();
                        migrateLegacySettingsForUserLocked(dbHelper, database, userId);

                        // Upgrade to the latest version.
                        UpgradeController upgrader = new UpgradeController(userId);
                        upgrader.upgradeIfNeededLocked();

                        // Drop from memory if not a running user.
                        if (!mUserManager.isUserRunning(new UserHandle(userId))) {
                            removeUserStateLocked(userId, false);
                        }
                    }
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }

        private void migrateLegacySettingsForUserIfNeededLocked(int userId) {
            // Every user has secure settings and if no file we need to migrate.
            final int secureKey = makeKey(SETTINGS_TYPE_SECURE, userId);
            File secureFile = getSettingsFile(secureKey);
            if (secureFile.exists()) {
                return;
            }

            DatabaseHelper dbHelper = new DatabaseHelper(getContext(), userId);
            SQLiteDatabase database = dbHelper.getWritableDatabase();

            migrateLegacySettingsForUserLocked(dbHelper, database, userId);
        }

        private void migrateLegacySettingsForUserLocked(DatabaseHelper dbHelper,
                SQLiteDatabase database, int userId) {
            // Move over the global settings if owner.
            if (userId == UserHandle.USER_OWNER) {
                final int globalKey = makeKey(SETTINGS_TYPE_GLOBAL, userId);
                ensureSettingsStateLocked(globalKey);
                SettingsState globalSettings = mSettingsStates.get(globalKey);
                migrateLegacySettingsLocked(globalSettings, database, TABLE_GLOBAL);
                globalSettings.persistSyncLocked();
            }

            // Move over the secure settings.
            final int secureKey = makeKey(SETTINGS_TYPE_SECURE, userId);
            ensureSettingsStateLocked(secureKey);
            SettingsState secureSettings = mSettingsStates.get(secureKey);
            migrateLegacySettingsLocked(secureSettings, database, TABLE_SECURE);
            ensureSecureSettingAndroidIdSetLocked(secureSettings);
            secureSettings.persistSyncLocked();

            // Move over the system settings.
            final int systemKey = makeKey(SETTINGS_TYPE_SYSTEM, userId);
            ensureSettingsStateLocked(systemKey);
            SettingsState systemSettings = mSettingsStates.get(systemKey);
            migrateLegacySettingsLocked(systemSettings, database, TABLE_SYSTEM);
            systemSettings.persistSyncLocked();

            // Drop the database as now all is moved and persisted.
            if (DROP_DATABASE_ON_MIGRATION) {
                dbHelper.dropDatabase();
            } else {
                dbHelper.backupDatabase();
            }
        }

        private void migrateLegacySettingsLocked(SettingsState settingsState,
                SQLiteDatabase database, String table) {
            SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            queryBuilder.setTables(table);

            Cursor cursor = queryBuilder.query(database, ALL_COLUMNS,
                    null, null, null, null, null);

            if (cursor == null) {
                return;
            }

            try {
                if (!cursor.moveToFirst()) {
                    return;
                }

                final int nameColumnIdx = cursor.getColumnIndex(Settings.NameValueTable.NAME);
                final int valueColumnIdx = cursor.getColumnIndex(Settings.NameValueTable.VALUE);

                settingsState.setVersionLocked(database.getVersion());

                while (!cursor.isAfterLast()) {
                    String name = cursor.getString(nameColumnIdx);
                    String value = cursor.getString(valueColumnIdx);
                    settingsState.insertSettingLocked(name, value,
                            SettingsState.SYSTEM_PACKAGE_NAME);
                    cursor.moveToNext();
                }
            } finally {
                cursor.close();
            }
        }

        private void ensureSecureSettingAndroidIdSetLocked(SettingsState secureSettings) {
            Setting value = secureSettings.getSettingLocked(Settings.Secure.ANDROID_ID);

            if (value != null) {
                return;
            }

            final int userId = getUserIdFromKey(secureSettings.mKey);

            final UserInfo user;
            final long identity = Binder.clearCallingIdentity();
            try {
                user = mUserManager.getUserInfo(userId);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            if (user == null) {
                // Can happen due to races when deleting users - treat as benign.
                return;
            }

            String androidId = Long.toHexString(new SecureRandom().nextLong());
            secureSettings.insertSettingLocked(Settings.Secure.ANDROID_ID, androidId,
                    SettingsState.SYSTEM_PACKAGE_NAME);

            Slog.d(LOG_TAG, "Generated and saved new ANDROID_ID [" + androidId
                    + "] for user " + userId);

            // Write a drop box entry if it's a restricted profile
            if (user.isRestricted()) {
                DropBoxManager dbm = (DropBoxManager) getContext().getSystemService(
                        Context.DROPBOX_SERVICE);
                if (dbm != null && dbm.isTagEnabled(DROPBOX_TAG_USERLOG)) {
                    dbm.addText(DROPBOX_TAG_USERLOG, System.currentTimeMillis()
                            + "," + DROPBOX_TAG_USERLOG + "," + androidId + "\n");
                }
            }
        }

        private void notifyForSettingsChange(int key, String name) {
            // Update the system property *first*, so if someone is listening for
            // a notification and then using the contract class to get their data,
            // the system property will be updated and they'll get the new data.

            boolean backedUpDataChanged = false;
            String property = null;
            if (isGlobalSettingsKey(key)) {
                property = Settings.Global.SYS_PROP_SETTING_VERSION;
                backedUpDataChanged = true;
            } else if (isSecureSettingsKey(key)) {
                property = Settings.Secure.SYS_PROP_SETTING_VERSION;
                backedUpDataChanged = true;
            } else if (isSystemSettingsKey(key)) {
                property = Settings.System.SYS_PROP_SETTING_VERSION;
                backedUpDataChanged = true;
            }

            if (property != null) {
                final long version = SystemProperties.getLong(property, 0) + 1;
                SystemProperties.set(property, Long.toString(version));
                if (DEBUG) {
                    Slog.v(LOG_TAG, "System property " + property + "=" + version);
                }
            }

            // Inform the backup manager about a data change
            if (backedUpDataChanged) {
                mHandler.obtainMessage(MyHandler.MSG_NOTIFY_DATA_CHANGED).sendToTarget();
            }

            // Now send the notification through the content framework.

            final int userId = getUserIdFromKey(key);
            Uri uri = getNotificationUriFor(key, name);

            mHandler.obtainMessage(MyHandler.MSG_NOTIFY_URI_CHANGED,
                    userId, 0, uri).sendToTarget();

            if (isSecureSettingsKey(key)) {
                maybeNotifyProfiles(userId, uri, name, sSecureCloneToManagedSettings);
            } else if (isSystemSettingsKey(key)) {
                maybeNotifyProfiles(userId, uri, name, sSystemCloneToManagedSettings);
            }
        }

        private void maybeNotifyProfiles(int userId, Uri uri, String name,
                Set<String> keysCloned) {
            if (keysCloned.contains(name)) {
                List<UserInfo> profiles = mUserManager.getProfiles(userId);
                int size = profiles.size();
                for (int i = 0; i < size; i++) {
                    UserInfo profile = profiles.get(i);
                    // the notification for userId has already been sent.
                    if (profile.id != userId) {
                        mHandler.obtainMessage(MyHandler.MSG_NOTIFY_URI_CHANGED,
                                profile.id, 0, uri).sendToTarget();
                    }
                }
            }
        }

        private int makeKey(int type, int userId) {
            return (type << SETTINGS_TYPE_SHIFT) | userId;
        }

        private int getTypeFromKey(int key) {
            return key >> SETTINGS_TYPE_SHIFT;
        }

        private int getUserIdFromKey(int key) {
            return key & ~SETTINGS_TYPE_MASK;
        }

        private boolean isGlobalSettingsKey(int key) {
            return getTypeFromKey(key) == SETTINGS_TYPE_GLOBAL;
        }

        private boolean isSystemSettingsKey(int key) {
            return getTypeFromKey(key) == SETTINGS_TYPE_SYSTEM;
        }

        private boolean isSecureSettingsKey(int key) {
            return getTypeFromKey(key) == SETTINGS_TYPE_SECURE;
        }

        private File getSettingsFile(int key) {
            if (isGlobalSettingsKey(key)) {
                final int userId = getUserIdFromKey(key);
                return new File(Environment.getUserSystemDirectory(userId),
                        SETTINGS_FILE_GLOBAL);
            } else if (isSystemSettingsKey(key)) {
                final int userId = getUserIdFromKey(key);
                return new File(Environment.getUserSystemDirectory(userId),
                        SETTINGS_FILE_SYSTEM);
            } else if (isSecureSettingsKey(key)) {
                final int userId = getUserIdFromKey(key);
                return new File(Environment.getUserSystemDirectory(userId),
                        SETTINGS_FILE_SECURE);
            } else {
                throw new IllegalArgumentException("Invalid settings key:" + key);
            }
        }

        private Uri getNotificationUriFor(int key, String name) {
            if (isGlobalSettingsKey(key)) {
                return (name != null) ? Uri.withAppendedPath(Settings.Global.CONTENT_URI, name)
                        : Settings.Global.CONTENT_URI;
            } else if (isSecureSettingsKey(key)) {
                return (name != null) ? Uri.withAppendedPath(Settings.Secure.CONTENT_URI, name)
                        : Settings.Secure.CONTENT_URI;
            } else if (isSystemSettingsKey(key)) {
                return (name != null) ? Uri.withAppendedPath(Settings.System.CONTENT_URI, name)
                        : Settings.System.CONTENT_URI;
            } else {
                throw new IllegalArgumentException("Invalid settings key:" + key);
            }
        }

        private int getMaxBytesPerPackageForType(int type) {
            switch (type) {
                case SETTINGS_TYPE_GLOBAL:
                case SETTINGS_TYPE_SECURE: {
                    return SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED;
                }

                default: {
                    return SettingsState.MAX_BYTES_PER_APP_PACKAGE_LIMITED;
                }
            }
        }

        private final class MyHandler extends Handler {
            private static final int MSG_NOTIFY_URI_CHANGED = 1;
            private static final int MSG_NOTIFY_DATA_CHANGED = 2;

            public MyHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_NOTIFY_URI_CHANGED: {
                        final int userId = msg.arg1;
                        Uri uri = (Uri) msg.obj;
                        getContext().getContentResolver().notifyChange(uri, null, true, userId);
                        if (DEBUG) {
                            Slog.v(LOG_TAG, "Notifying for " + userId + ": " + uri);
                        }
                    } break;

                    case MSG_NOTIFY_DATA_CHANGED: {
                        mBackupManager.dataChanged();
                    } break;
                }
            }
        }

        private final class UpgradeController {
            private static final int SETTINGS_VERSION = 123;
            /**
             * This is the 12.1 database version (DO NOT INCREMENT)
             */
            private static final int CM_SETTINGS_DB_VERSION = 125;

            private final int mUserId;

            public UpgradeController(int userId) {
                mUserId = userId;
            }

            public void upgradeIfNeededLocked() {
                // The version of all settings for a user is the same (all users have secure).
                SettingsState secureSettings = getSettingsLocked(
                        SettingsRegistry.SETTINGS_TYPE_SECURE, mUserId);

                // Try an update from the current state.
                final int oldVersion = secureSettings.getVersionLocked(); //125
                final int newVersion = SETTINGS_VERSION;

                // If up do date - done.
                //
                // CYANOGENMOD
                // We moved our settings out to another settings provider (CMSettingsProvider)
                // however, we still have a problem of being a few versions ahead of AOSP.
                // We could approach this in the manner we have previously, and bump the version
                // to replay the defaults for specific os upgrade changes and have that maintenance
                // overhead forever OR we can take the approach below:
                //
                // Logic as follows:
                //       Until version 125 of this "DATABASE"
                //       force replay AOSP defaults as they get introduced
                //       once 125 is hit, we never have to maintain this again.
                if ((oldVersion == newVersion || oldVersion == CM_SETTINGS_DB_VERSION)) {
                    if (oldVersion == CM_SETTINGS_DB_VERSION && !hasReplayedDefaultsFromL()) {
                        forceReplayAOSPDefaults(mUserId);
                        forceMigrateProfilesEnabled(mUserId);
                        setDefaultsReplayedFromLFlag();
                    }
                    return;
                }

                // Try to upgrade.
                final int curVersion = onUpgradeLocked(mUserId, oldVersion, newVersion);

                // If upgrade failed start from scratch and upgrade.
                if (curVersion != newVersion) {
                    // Drop state we have for this user.
                    removeUserStateLocked(mUserId, true);

                    // Recreate the database.
                    DatabaseHelper dbHelper = new DatabaseHelper(getContext(), mUserId);
                    SQLiteDatabase database = dbHelper.getWritableDatabase();
                    dbHelper.recreateDatabase(database, newVersion, curVersion, oldVersion);

                    // Migrate the settings for this user.
                    migrateLegacySettingsForUserLocked(dbHelper, database, mUserId);

                    // Now upgrade should work fine.
                    onUpgradeLocked(mUserId, oldVersion, newVersion);
                }

                // Set the global settings version if owner.
                if (mUserId == UserHandle.USER_OWNER) {
                    SettingsState globalSettings = getSettingsLocked(
                            SettingsRegistry.SETTINGS_TYPE_GLOBAL, mUserId);
                    globalSettings.setVersionLocked(newVersion);
                }

                // Set the secure settings version.
                secureSettings.setVersionLocked(newVersion);

                // Set the system settings version.
                SettingsState systemSettings = getSettingsLocked(
                        SettingsRegistry.SETTINGS_TYPE_SYSTEM, mUserId);
                systemSettings.setVersionLocked(newVersion);
            }

            private boolean hasReplayedDefaultsFromL() {
                SharedPreferences sharedPreferences = PreferenceManager
                        .getDefaultSharedPreferences(getContext());
                return sharedPreferences.getBoolean(HAS_REPLAYED_DEFAULTS_FROM_L, false);
            }

            private void setDefaultsReplayedFromLFlag() {
                SharedPreferences sharedPreferences = PreferenceManager
                        .getDefaultSharedPreferences(getContext());
                sharedPreferences.edit().putBoolean(HAS_REPLAYED_DEFAULTS_FROM_L, true).apply();
            }

            private SettingsState getGlobalSettingsLocked() {
                return getSettingsLocked(SETTINGS_TYPE_GLOBAL, UserHandle.USER_OWNER);
            }

            private SettingsState getSecureSettingsLocked(int userId) {
                return getSettingsLocked(SETTINGS_TYPE_SECURE, userId);
            }

            private SettingsState getSystemSettingsLocked(int userId) {
                return getSettingsLocked(SETTINGS_TYPE_SYSTEM, userId);
            }

            /**
             * You must perform all necessary mutations to bring the settings
             * for this user from the old to the new version. When you add a new
             * upgrade step you *must* update SETTINGS_VERSION.
             *
             * This is an example of moving a setting from secure to global.
             *
             * // v119: Example settings changes.
             * if (currentVersion == 118) {
             *     if (userId == UserHandle.USER_OWNER) {
             *         // Remove from the secure settings.
             *         SettingsState secureSettings = getSecureSettingsLocked(userId);
             *         String name = "example_setting_to_move";
             *         String value = secureSettings.getSetting(name);
             *         secureSettings.deleteSetting(name);
             *
             *         // Add to the global settings.
             *         SettingsState globalSettings = getGlobalSettingsLocked();
             *         globalSettings.insertSetting(name, value, SettingsState.SYSTEM_PACKAGE_NAME);
             *     }
             *
             *     // Update the current version.
             *     currentVersion = 119;
             * }
             */
            private int onUpgradeLocked(int userId, int oldVersion, int newVersion) {
                if (DEBUG) {
                    Slog.w(LOG_TAG, "Upgrading settings for user: " + userId + " from version: "
                            + oldVersion + " to version: " + newVersion);
                }

                int currentVersion = oldVersion;

                // v119: Reset zen + ringer mode.
                if (currentVersion == 118) {
                    if (userId == UserHandle.USER_OWNER) {
                        final SettingsState globalSettings = getGlobalSettingsLocked();
                        globalSettings.updateSettingLocked(Settings.Global.ZEN_MODE,
                                Integer.toString(Settings.Global.ZEN_MODE_OFF),
                                SettingsState.SYSTEM_PACKAGE_NAME);
                        globalSettings.updateSettingLocked(Settings.Global.MODE_RINGER,
                                Integer.toString(AudioManager.RINGER_MODE_NORMAL),
                                SettingsState.SYSTEM_PACKAGE_NAME);
                    }
                    currentVersion = 119;
                }

                // v120: Add double tap to wake setting.
                if (currentVersion == 119) {
                    SettingsState secureSettings = getSecureSettingsLocked(userId);
                    secureSettings.insertSettingLocked(Settings.Secure.DOUBLE_TAP_TO_WAKE,
                            getContext().getResources().getBoolean(
                                    R.bool.def_double_tap_to_wake) ? "1" : "0",
                            SettingsState.SYSTEM_PACKAGE_NAME);

                    currentVersion = 120;
                }

                if (currentVersion == 120) {
                    // Before 121, we used a different string encoding logic.  We just bump the
                    // version here; SettingsState knows how to handle pre-version 120 files.
                    currentVersion = 121;
                }

                if (currentVersion == 121) {
                    // Version 122: allow OEMs to set a default payment component in resources.
                    // Note that we only write the default if no default has been set;
                    // if there is, we just leave the default at whatever it currently is.
                    final SettingsState secureSettings = getSecureSettingsLocked(userId);
                    String defaultComponent = (getContext().getResources().getString(
                            R.string.def_nfc_payment_component));
                    Setting currentSetting = secureSettings.getSettingLocked(
                            Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT);
                    if (defaultComponent != null && !defaultComponent.isEmpty() &&
                        currentSetting == null) {
                        secureSettings.insertSettingLocked(
                                Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT,
                                defaultComponent,
                                SettingsState.SYSTEM_PACKAGE_NAME);
                    }
                    currentVersion = 122;
                }

                if (currentVersion == 122) {
                    final SettingsState globalSettings = getGlobalSettingsLocked();
                    String defaultDisabledProfiles = (getContext().getResources().getString(
                            R.string.def_bluetooth_disabled_profiles));
                    globalSettings.insertSettingLocked(Settings.Global.BLUETOOTH_DISABLED_PROFILES,
                            defaultDisabledProfiles, SettingsState.SYSTEM_PACKAGE_NAME);
                    currentVersion = 123;
                }

                // vXXX: Add new settings above this point.

                // Return the current version.
                return currentVersion;
            }

            private void forceReplayAOSPDefaults(int userId) {
                // v119: Reset zen + ringer mode.
                if (userId == UserHandle.USER_OWNER) {
                    final SettingsState globalSettings = getGlobalSettingsLocked();
                    globalSettings.updateSettingLocked(Settings.Global.ZEN_MODE,
                            Integer.toString(Settings.Global.ZEN_MODE_OFF),
                            SettingsState.SYSTEM_PACKAGE_NAME);
                    globalSettings.updateSettingLocked(Settings.Global.MODE_RINGER,
                            Integer.toString(AudioManager.RINGER_MODE_NORMAL),
                            SettingsState.SYSTEM_PACKAGE_NAME);
                }

                // v120: Add double tap to wake setting.
                SettingsState secureSettings = getSecureSettingsLocked(userId);
                secureSettings.insertSettingLocked(Settings.Secure.DOUBLE_TAP_TO_WAKE,
                        getContext().getResources().getBoolean(
                                R.bool.def_double_tap_to_wake) ? "1" : "0",
                        SettingsState.SYSTEM_PACKAGE_NAME);

                // Version 122: allow OEMs to set a default payment component in resources.
                // Note that we only write the default if no default has been set;
                // if there is, we just leave the default at whatever it currently is.
                String defaultComponent = (getContext().getResources().getString(
                        R.string.def_nfc_payment_component));
                Setting currentSetting = secureSettings.getSettingLocked(
                        Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT);
                if (defaultComponent != null && !defaultComponent.isEmpty() &&
                        currentSetting == null) {
                    secureSettings.insertSettingLocked(
                            Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT,
                            defaultComponent,
                            SettingsState.SYSTEM_PACKAGE_NAME);
                }
            }

            private void forceMigrateProfilesEnabled(int userId) {
                final SettingsState systemSettings = getSystemSettingsLocked(userId);
                final Setting settingLocked = systemSettings.getSettingLocked(
                        CMSettings.System.SYSTEM_PROFILES_ENABLED);
                if (settingLocked != null) {
                    final String value = settingLocked.getValue();
                    if (value != null) {
                        CMSettings.System.putStringForUser(getContext().getContentResolver(),
                                CMSettings.System.SYSTEM_PROFILES_ENABLED,
                                value,
                                userId);
                        systemSettings.deleteSettingLocked(
                                CMSettings.System.SYSTEM_PROFILES_ENABLED);
                    }
                }
            }

        }
    }
}
