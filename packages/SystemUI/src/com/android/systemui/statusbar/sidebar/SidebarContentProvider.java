/*
 * Copyright (C) 2013 The ChameleonOS Project
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

package com.android.systemui.statusbar.sidebar;

import java.util.Arrays;
import java.util.HashSet;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class SidebarContentProvider extends ContentProvider {
    
    private SidebarSQLiteHelper database;
    
    // Used for the UriMatcher
    private static final int ITEMS = 10;
    private static final int ITEM_ID = 20;
    
    private static final String AUTHORITY = "org.chameleonos.sidebar.contentprovider";
    
    private static final String BASE_PATH = "sidebar_items";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH);
    
    public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
            + "/sidebar_items";
    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
            + "/sidebar_item";
    
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, BASE_PATH, ITEMS);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", ITEM_ID);
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = database.getWritableDatabase();
        int rowsDeleted = 0;
        switch (uriType) {
        case ITEMS:
            rowsDeleted = sqlDB.delete(SidebarTable.TABLE_SIDEBAR, selection,
                    selectionArgs);
            break;
        case ITEM_ID:
            String id = uri.getLastPathSegment();
            if (TextUtils.isEmpty(selection)) {
                rowsDeleted = sqlDB.delete(SidebarTable.TABLE_SIDEBAR,
                        SidebarTable.COLUMN_ITEM_ID + "=" + id, 
                        null);
            } else {
                rowsDeleted = sqlDB.delete(SidebarTable.TABLE_SIDEBAR,
                        SidebarTable.COLUMN_ITEM_ID + "=" + id 
                        + " and " + selection,
                        selectionArgs);
            }
            break;
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase db = database.getWritableDatabase();
        long id = 0;
        switch (uriType) {
        case ITEMS:
            db.insert(SidebarTable.TABLE_SIDEBAR, null, values);
            id = values.getAsLong(SidebarTable.COLUMN_ITEM_ID);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(BASE_PATH + "/" + id);
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        database = new SidebarSQLiteHelper(getContext());
        return false;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        
        // Using SQLiteQueryBuilder instead of query() method
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        
        // Check if the caller has requested a column which does not exist
        checkColumns(projection);
        
        // Set the table
        queryBuilder.setTables(SidebarTable.TABLE_SIDEBAR);
        
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
        case ITEMS:
            break;
        case ITEM_ID:
            // Add the ID to the original query
            queryBuilder.appendWhere(SidebarTable.COLUMN_ITEM_ID + "="
                    + uri.getLastPathSegment());
            break;
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        
        SQLiteDatabase db = database.getWritableDatabase();
        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
        // Make sure that potential listeners are notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        
        return cursor;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = database.getWritableDatabase();
        int rowsUpdated = 0;
        switch (uriType) {
        case ITEMS:
            rowsUpdated = sqlDB.update(SidebarTable.TABLE_SIDEBAR, 
                    values, 
                    selection,
                    selectionArgs);
            break;
        case ITEM_ID:
            String id = uri.getLastPathSegment();
            if (TextUtils.isEmpty(selection)) {
                rowsUpdated = sqlDB.update(SidebarTable.TABLE_SIDEBAR, 
                        values,
                        SidebarTable.COLUMN_ITEM_ID + "=" + id, 
                        null);
            } else {
                rowsUpdated = sqlDB.update(SidebarTable.TABLE_SIDEBAR, 
                        values,
                        SidebarTable.COLUMN_ITEM_ID + "=" + id 
                        + " and " 
                        + selection,
                        selectionArgs);
            }
            break;
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }

    private void checkColumns(String[] projection) {
        String[] available = {
                SidebarTable.COLUMN_ITEM_ID,
                SidebarTable.COLUMN_ITEM_TYPE,
                SidebarTable.COLUMN_CONTAINER,
                SidebarTable.COLUMN_TITLE,
                SidebarTable.COLUMN_COMPONENT
        };
        if (projection != null) {
            HashSet<String> requestedColumns = new HashSet<String>(Arrays.asList(projection));
            HashSet<String> availableColumns = new HashSet<String>(Arrays.asList(available));
            // Check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }
}
