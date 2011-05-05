/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.layoutlib.bridge.android;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.IContentProvider;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.IBulkCursor;
import android.database.IContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Mock implementation of {@link IContentProvider}.
 *
 * TODO: never return null when the method is not supposed to. Return fake data instead.
 */
public final class BridgeContentProvider implements IContentProvider {

    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> arg0)
            throws RemoteException, OperationApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    public int bulkInsert(Uri arg0, ContentValues[] arg1) throws RemoteException {
        // TODO Auto-generated method stub
        return 0;
    }

    public IBulkCursor bulkQuery(Uri arg0, String[] arg1, String arg2, String[] arg3,
            String arg4, IContentObserver arg5, CursorWindow arg6) throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public Bundle call(String arg0, String arg1, Bundle arg2) throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public int delete(Uri arg0, String arg1, String[] arg2) throws RemoteException {
        // TODO Auto-generated method stub
        return 0;
    }

    public String getType(Uri arg0) throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public Uri insert(Uri arg0, ContentValues arg1) throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public AssetFileDescriptor openAssetFile(Uri arg0, String arg1) throws RemoteException,
            FileNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }

    public ParcelFileDescriptor openFile(Uri arg0, String arg1) throws RemoteException,
            FileNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }

    public Cursor query(Uri arg0, String[] arg1, String arg2, String[] arg3, String arg4)
            throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3)
            throws RemoteException {
        // TODO Auto-generated method stub
        return 0;
    }

    public IBinder asBinder() {
        // TODO Auto-generated method stub
        return null;
    }

    public String[] getStreamTypes(Uri arg0, String arg1) throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    public AssetFileDescriptor openTypedAssetFile(Uri arg0, String arg1, Bundle arg2)
            throws RemoteException, FileNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }

}
