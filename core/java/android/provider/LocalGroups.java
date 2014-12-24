/*
 * Copyright (C) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package android.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;

/**
 * @hide
 */
public class LocalGroups {

    public static final String AUTHORITY = "com.android.contacts.groups";

    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "local-groups");

    public static interface GroupColumns {

        public static final String _ID = "_id";

        public static final String TITLE = "title";

        public static final String COUNT = "count";
    }

    public static class Group {
        private long id = -1;

        private String title = "";

        private int count;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public static Group restoreGroup(Cursor cursor) {
            if (cursor == null && cursor.getCount() == 0) {
                return null;
            }
            Group group = new Group();
            group.setId(cursor.getLong(cursor.getColumnIndex(GroupColumns._ID)));
            group.setTitle(cursor.getString(cursor.getColumnIndex(GroupColumns.TITLE)));
            group.setCount(cursor.getInt(cursor.getColumnIndex(GroupColumns.COUNT)));
            return group;
        }

        public ContentValues toContentValues() {
            ContentValues values = new ContentValues();
            values.put(GroupColumns.TITLE, getTitle());
            values.put(GroupColumns.COUNT, getCount());
            return values;
        }

        public boolean save(ContentResolver cr) {
            if (cr == null) {
                return false;
            }
            Uri uri = cr.insert(CONTENT_URI, toContentValues());
            if (uri != null) {
                setId(ContentUris.parseId(uri));
                return true;
            } else {
                return false;
            }
        }

        public boolean update(ContentResolver cr) {
            if (cr == null) {
                return false;
            }
            return cr.update(CONTENT_URI, toContentValues(), GroupColumns._ID + "=?", new String[] {
                String.valueOf(id)
            }) > 0;
        }

        public boolean delete(ContentResolver cr) {
            cr.delete(Data.CONTENT_URI, Data.MIMETYPE + "=? and "
                    + CommonDataKinds.LocalGroup.DATA1 + "=?", new String[] {
                    CommonDataKinds.LocalGroup.CONTENT_ITEM_TYPE, String.valueOf(getId())
            });
            return cr.delete(CONTENT_URI, GroupColumns._ID + "=?", new String[] {
                String.valueOf(id)
            }) > 0;
        }

        public static Group restoreGroupById(ContentResolver cr, long groupId) {
            Uri uri = ContentUris.withAppendedId(LocalGroups.CONTENT_URI, groupId);
            Cursor c = null;
            try {
                c = cr.query(uri, null, null, null, null);
                if (c != null && c.moveToNext())
                    return restoreGroup(c);
            } finally {
                if (c != null)
                    c.close();
            }
            return null;
        }
    }

}
