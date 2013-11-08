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

import android.database.sqlite.SQLiteDatabase;

public class SidebarTable {
    public static final String TABLE_SIDEBAR = "sidebar_items";
    public static final String COLUMN_ITEM_ID = "_id";
    public static final String COLUMN_ITEM_TYPE = "itemType";
    public static final String COLUMN_CONTAINER = "container";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_COMPONENT = "component";

    private static final String DATABASE_CREATE =
            "create table "
            + TABLE_SIDEBAR + "("
            + COLUMN_ITEM_ID + " integer primary key, "
            + COLUMN_ITEM_TYPE + " integer, "
            + COLUMN_CONTAINER + " integer, "
            + COLUMN_TITLE + " text, "
            + COLUMN_COMPONENT + " text);";

    public static void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }
    
    public static void onUpgrade(SQLiteDatabase database, int oldVersion,
            int newVersion) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_SIDEBAR);
        onCreate(database);
    }
}
