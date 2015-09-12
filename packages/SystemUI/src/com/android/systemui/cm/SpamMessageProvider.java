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
    private static final int MESSAGES = 1;
    private static final int PACKAGE_FOR_ID = 2;
    private static final int MESSAGE_UPDATE_COUNT = 3;
    private static final int MESSAGE_FOR_ID = 4;
    static {
        sURIMatcher.addURI(AUTHORITY, "packages", PACKAGES);
        sURIMatcher.addURI(AUTHORITY, "messages", MESSAGES);
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
        case PACKAGE_FOR_ID:
            return mDbHelper.getReadableDatabase().query(PackageTable.TABLE_NAME,
                    new String[]{NotificationTable.ID}, PackageTable.PACKAGE_NAME + "=?",
                    new String[]{uri.getLastPathSegment()}, null, null, null);
        case PACKAGES:
            return mDbHelper.getReadableDatabase().query(PackageTable.TABLE_NAME,
                    projection, selection, selectionArgs, null, null, sortOrder);
        case MESSAGES:
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(NotificationTable.TABLE_NAME + " LEFT OUTER JOIN " + PackageTable.TABLE_NAME +
                    " ON (" + NotificationTable.TABLE_NAME + "." + NotificationTable.PACKAGE_ID + " = "
                    + PackageTable.TABLE_NAME + "." + PackageTable.ID + ")");
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            return qb.query(db, projection, selection, selectionArgs,
                    null, null, null);
        case MESSAGE_FOR_ID:
            qb = new SQLiteQueryBuilder();
            qb.setTables(NotificationTable.TABLE_NAME + " LEFT OUTER JOIN " + PackageTable.TABLE_NAME +
                    " ON (" + NotificationTable.TABLE_NAME + "." + NotificationTable.PACKAGE_ID + " = "
                    + PackageTable.TABLE_NAME + "." + PackageTable.ID + ")");
            qb.appendWhere(NotificationTable.TABLE_NAME + "." + NotificationTable.ID + "="
                    + uri.getLastPathSegment());
            return qb.query(mDbHelper.getReadableDatabase(),
                    null, null, null, null,
                    null, null);
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
        case MESSAGES:
            String msgText = values.getAsString(NotificationTable.MESSAGE_TEXT);
            String packageName = values.getAsString(PackageTable.PACKAGE_NAME);
            if (TextUtils.isEmpty(msgText) || TextUtils.isEmpty(packageName)) {
                return null;
            }
            values.clear();
            values.put(PackageTable.PACKAGE_NAME, packageName);
            long packageId = getPackageId(packageName);
            if (packageId == -1) {
                SQLiteDatabase writableDb = mDbHelper.getWritableDatabase();
                packageId = writableDb.insert(
                        PackageTable.TABLE_NAME, null, values);
            }
            if (packageId != -1) {
                values.clear();
                values.put(NotificationTable.MESSAGE_TEXT, msgText);
                values.put(NotificationTable.NORMALIZED_TEXT,
                        SpamFilter.getNormalizedContent(msgText));
                values.put(NotificationTable.PACKAGE_ID, packageId);
                values.put(NotificationTable.LAST_BLOCKED, System.currentTimeMillis());
                long id = mDbHelper.getReadableDatabase().insert(NotificationTable.TABLE_NAME,
                        null, values);
                if (id != -1) {
                    notifyChange(String.valueOf(id));
                }
            }
            return null;
        default:
            return null;
        }
    }

    private void notifyChange(String id) {
        Uri uri = Uri.withAppendedPath(SpamFilter.NOTIFICATION_URI,
                id);
        getContext().getContentResolver().notifyChange(uri, null);
    }

    private void removePackageIfNecessary(int packageId) {
        long numEntries = DatabaseUtils.queryNumEntries(mDbHelper.getReadableDatabase(),
                NotificationTable.TABLE_NAME, NotificationTable.PACKAGE_ID + "=?",
                new String[]{String.valueOf(packageId)});
        if (numEntries == 0) {
            SQLiteDatabase writableDb = mDbHelper.getWritableDatabase();
            writableDb.delete(PackageTable.TABLE_NAME, PackageTable.ID + "=?",
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
            SQLiteDatabase writableDb = mDbHelper.getWritableDatabase();
            String id = uri.getLastPathSegment();
            int result = writableDb.delete(NotificationTable.TABLE_NAME,
                    NotificationTable.ID + "=?", new String[]{id});
            removePackageIfNecessary(packageId);
            if (result > 0) {
                notifyChange(id);
            }
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
            String id = uri.getLastPathSegment();
            String formattedQuery = String.format(UPDATE_COUNT_QUERY,
                    System.currentTimeMillis(), id);
            SQLiteDatabase writableDb = mDbHelper.getWritableDatabase();
            writableDb.execSQL(formattedQuery);
            notifyChange(id);
            return 0;
        default:
            return 0;
        }
    }

}
