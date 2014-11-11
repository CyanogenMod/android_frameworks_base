package com.android.systemui.cm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import com.android.internal.util.cm.SpamFilter;
import com.android.internal.util.cm.SpamFilter.SpamContract.PackageTable;
import com.android.internal.util.cm.SpamFilter.SpamContract.NotificationTable;

public class SpamMessageProvider extends ContentProvider {
    public static final String AUTHORITY = SpamFilter.AUTHORITY;

    private static final String UPDATE_COUNT_QUERY =
            "UPDATE " + NotificationTable.TABLE_NAME +
            " SET " + NotificationTable.LAST_BLOCKED + "=%d," +
            NotificationTable.COUNT + "=" + NotificationTable.COUNT + "+1 " +
            " WHERE " + NotificationTable.ID + "='%s'";

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int PACKAGES = 0;
    private static final int MESSAGE = 1;
    private static final int PACKAGE_ID = 2;
    private static final int MESSAGE_UPDATE_COUNT = 3;
    private static final int MESSAGE_FOR_ID = 4;
    static {
        sURIMatcher.addURI(AUTHORITY, "packages", PACKAGES);
        sURIMatcher.addURI(AUTHORITY, "package/id/*", PACKAGE_ID);
        sURIMatcher.addURI(AUTHORITY, "message", MESSAGE);
        sURIMatcher.addURI(AUTHORITY, "message/#", MESSAGE_FOR_ID);
        sURIMatcher.addURI(AUTHORITY, "message/inc_count/#", MESSAGE_UPDATE_COUNT);
    }

    private SpamOpenHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new SpamOpenHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        int match = sURIMatcher.match(uri);
        switch (match) {
        case PACKAGE_ID:
            Cursor idCursor = mDbHelper.getReadableDatabase().query(PackageTable.TABLE_NAME,
                    new String[]{NotificationTable.ID}, PackageTable.PACKAGE_NAME + "=?",
                    new String[]{uri.getLastPathSegment()}, null, null, null);
            return idCursor;
        case PACKAGES:
            Cursor pkgCursor = mDbHelper.getReadableDatabase().query(PackageTable.TABLE_NAME,
                    null, null, null, null, null, null);
            return pkgCursor;
        case MESSAGE:
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(PackageTable.TABLE_NAME + "," + NotificationTable.TABLE_NAME);
            String pkgId = PackageTable.TABLE_NAME + "." + PackageTable.ID;
            String notificationPkgId = NotificationTable.TABLE_NAME + "."
                    + NotificationTable.PACKAGE_ID;
            qb.appendWhere(pkgId + "=" + notificationPkgId);
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            Cursor ret = qb.query(db, new String[]{NotificationTable.TABLE_NAME + ".*"},
                    selection, selectionArgs, null, null, null);
            ret.moveToFirst();
            return ret;
        case MESSAGE_FOR_ID:
            qb = new SQLiteQueryBuilder();
            qb.setTables(NotificationTable.TABLE_NAME);
            qb.appendWhere(NotificationTable.PACKAGE_ID + "=" + uri.getLastPathSegment());
            db = mDbHelper.getReadableDatabase();
            ret = qb.query(db, null, null, null, null, null, null);
            return ret;
        default:
            return null;
        }
    }

    private long getPackageId(String pkg) {
        long rowId = -1;
        Cursor idCursor = mDbHelper.getReadableDatabase().query(PackageTable.TABLE_NAME,
                new String[]{NotificationTable.ID}, PackageTable.PACKAGE_NAME + "=?",
                new String[]{pkg}, null, null, null);
        if (idCursor != null) {
            if (idCursor.moveToFirst()) {
                rowId = idCursor.getLong(0);
            }
            idCursor.close();
        }
        return rowId;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (values == null) {
            return null;
        }
        int match = sURIMatcher.match(uri);
        switch (match) {
        case MESSAGE:
            String msgText = values.getAsString(NotificationTable.MESSAGE_TEXT);
            String packageName = values.getAsString(PackageTable.PACKAGE_NAME);
            if (TextUtils.isEmpty(msgText) || TextUtils.isEmpty(packageName)) {
                return null;
            }
            values.clear();
            values.put(PackageTable.PACKAGE_NAME, packageName);
            long packageId = getPackageId(packageName);
            if (packageId == -1) {
                packageId = mDbHelper.getWritableDatabase().insert(
                        PackageTable.TABLE_NAME, null, values);
            }
            if (packageId != -1) {
                values.clear();
                values.put(NotificationTable.MESSAGE_TEXT, msgText);
                values.put(NotificationTable.NORMALIZED_TEXT,
                        SpamFilter.getNormalizedContent(msgText));
                values.put(NotificationTable.PACKAGE_ID, packageId);
                values.put(NotificationTable.LAST_BLOCKED, System.currentTimeMillis());
                mDbHelper.getReadableDatabase().insert(NotificationTable.TABLE_NAME,
                        null, values);
                notifyChange();
            }
            return null;
        default:
            return null;
        }
    }

    private void notifyChange() {
        getContext().getContentResolver().notifyChange(SpamFilter.NOTIFICATION_URI, null);
    }

    private void removePackageIfNecessary(int packageId) {
        long numEntries = DatabaseUtils.queryNumEntries(mDbHelper.getReadableDatabase(),
                NotificationTable.TABLE_NAME, NotificationTable.PACKAGE_ID + "=?",
                new String[]{String.valueOf(packageId)});
        if (numEntries == 0) {
            mDbHelper.getWritableDatabase().delete(PackageTable.TABLE_NAME, PackageTable.ID + "=?",
                    new String[]{String.valueOf(packageId)});
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = sURIMatcher.match(uri);
        switch (match) {
        case MESSAGE_FOR_ID:
            int packageId = -1;
            Cursor idCursor = mDbHelper.getReadableDatabase().query(NotificationTable.TABLE_NAME,
                    new String[]{NotificationTable.PACKAGE_ID}, NotificationTable.ID + "=?",
                    new String[]{uri.getLastPathSegment()}, null, null, null);
            if (idCursor != null) {
                if (idCursor.moveToFirst()) {
                    packageId = idCursor.getInt(0);
                }
                idCursor.close();
            }
            int result = mDbHelper.getWritableDatabase().delete(NotificationTable.TABLE_NAME,
                    NotificationTable.ID + "=?", new String[]{uri.getLastPathSegment()});
            removePackageIfNecessary(packageId);
            notifyChange();
            return result;
        default:
            return 0;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int match = sURIMatcher.match(uri);
        switch (match) {
        case MESSAGE_UPDATE_COUNT:
            String formattedQuery = String.format(UPDATE_COUNT_QUERY,
                    System.currentTimeMillis(), uri.getLastPathSegment());
            mDbHelper.getWritableDatabase().execSQL(formattedQuery);
            notifyChange();
            return 0;
        default:
            return 0;
        }
    }

}
