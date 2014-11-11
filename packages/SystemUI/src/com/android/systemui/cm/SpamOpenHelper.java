package com.android.systemui.cm;

import com.android.internal.util.cm.SpamFilter.SpamContract.NotificationTable;
import com.android.internal.util.cm.SpamFilter.SpamContract.PackageTable;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SpamOpenHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "spam.db";
    private static final int VERSION = 4;
    private static final String CREATE_PACKAGES_TABLE =
            "create table " + PackageTable.TABLE_NAME + "(" +
             PackageTable.ID + " INTEGER PRIMARY KEY," +
             PackageTable.PACKAGE_NAME + " TEXT UNIQUE);";
    private static final String CREATE_NOTIFICATIONS_TABLE =
            "create table " + NotificationTable.TABLE_NAME + "(" +
             NotificationTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
             NotificationTable.PACKAGE_ID + " INTEGER," +
             NotificationTable.MESSAGE_TEXT + " STRING," +
             NotificationTable.LAST_BLOCKED + " INTEGER," +
             NotificationTable.NORMALIZED_TEXT + " STRING," +
             NotificationTable.COUNT + " INTEGER DEFAULT 0);";

    private Context mContext;

    public SpamOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PACKAGES_TABLE);
        db.execSQL(CREATE_NOTIFICATIONS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mContext.deleteDatabase(DATABASE_NAME);
        onCreate(db);
    }

}
