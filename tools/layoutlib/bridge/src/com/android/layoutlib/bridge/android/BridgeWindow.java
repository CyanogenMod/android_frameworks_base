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

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.IWindow;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View.AttachInfo;

/**
 * Implementation of {@link IWindow} to pass to the {@link AttachInfo}.
 */
public final class BridgeWindow implements IWindow {

    public void dispatchAppVisibility(boolean arg0) throws RemoteException {
        // pass for now.
    }

    public void dispatchGetNewSurface() throws RemoteException {
        // pass for now.
    }

    public void dispatchKey(KeyEvent arg0) throws RemoteException {
        // pass for now.
    }

    public void dispatchPointer(MotionEvent arg0, long arg1, boolean arg2) throws RemoteException {
        // pass for now.
    }

    public void dispatchTrackball(MotionEvent arg0, long arg1, boolean arg2)
    throws RemoteException {
        // pass for now.
    }

    public void executeCommand(String arg0, String arg1, ParcelFileDescriptor arg2)
            throws RemoteException {
        // pass for now.
    }

    public void resized(int arg0, int arg1, Rect arg2, Rect arg3, boolean arg4, Configuration arg5)
            throws RemoteException {
        // pass for now.
    }

    public void windowFocusChanged(boolean arg0, boolean arg1) throws RemoteException {
        // pass for now.
    }

    public void dispatchWallpaperOffsets(float x, float y, float xStep, float yStep,
            boolean sync) {
        // pass for now.
    }

    public void dispatchWallpaperCommand(String action, int x, int y,
            int z, Bundle extras, boolean sync) {
        // pass for now.
    }

    public void closeSystemDialogs(String reason) {
        // pass for now.
    }

    public void dispatchSystemUiVisibilityChanged(int visibility) {
        // pass for now.
    }

    public IBinder asBinder() {
        // pass for now.
        return null;
    }
}
