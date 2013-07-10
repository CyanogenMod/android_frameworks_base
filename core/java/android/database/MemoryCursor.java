/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.database;

import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DatabaseUtils;

/**
 * Implementation of an in-memory cursor backed by a cursor window.
 *
 * @hide
 */
public class MemoryCursor extends AbstractWindowedCursor {
    private CursorWindow mDeactivatedWindow;
    private final String[] mColumnNames;

    public MemoryCursor(String name, String[] columnNames) {
        setWindow(new CursorWindow(name));
        mColumnNames = columnNames;
    }

    public void fillFromCursor(Cursor cursor) {
        DatabaseUtils.cursorFillWindow(cursor, 0, getWindow());
    }

    @Override
    public int getCount() {
        return getWindow().getNumRows();
    }

    @Override
    public String[] getColumnNames() {
        return mColumnNames;
    }

    @Override
    public boolean requery() {
        if (mDeactivatedWindow != null) {
            setWindow(mDeactivatedWindow);
            mDeactivatedWindow = null;
        }
        return super.requery();
    }

    @Override
    protected void onDeactivateOrClose() {
        // when deactivating the cursor, we need to keep our in-memory cursor
        // window as we have no chance of requerying it later on
        if (!isClosed() && getWindow() != null) {
            mDeactivatedWindow = getWindow();
            mWindow = null;
        }
        super.onDeactivateOrClose();
        if (isClosed() && mDeactivatedWindow != null) {
            mDeactivatedWindow.close();
            mDeactivatedWindow = null;
        }
    }
}
