/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.server.display;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayDevice;
import android.hardware.display.IRemoteDisplayAdapter;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

public class RemoteDisplayAdapter extends DisplayAdapter {
    private static class RemoteDisplay {
        RemoteDisplayDevice remoteDisplayDevice;
        DisplayDeviceInfo info;
        IDisplayDevice displayDevice;
    }

    private static final String TAG = "RemoteDisplayAdapter";
    private Hashtable<String, RemoteDisplay> devices = new Hashtable<String, RemoteDisplay>();
    private boolean scanning;
    private Handler handler;
    private static RemoteDisplay activeDisplay;

    class Stub extends IRemoteDisplayAdapter.Stub {
        public void registerDisplayDevice(IDisplayDevice displayDevice, String name, int width, int height, float refreshRate, int flags, String address) throws RemoteException {
            synchronized (getSyncRoot()) {
                RemoteDisplay display = new RemoteDisplay();
                display.info = new DisplayDeviceInfo();
                display.displayDevice = displayDevice;
                devices.put(address, display);
                DisplayDeviceInfo info = display.info;
                info.name = name;
                info.width = width;
                info.height = height;
                info.refreshRate = refreshRate;
                info.flags = flags;
                info.type = Display.TYPE_UNKNOWN;
                info.address = address;
                info.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
                info.setAssumedDensityForExternalDisplay(width, height);
                handleSendStatusChangeBroadcast();
            }
        }

        @Override
        public void unregisterDisplayDevice(IDisplayDevice displayDevice) {
            synchronized (getSyncRoot()) {
                RemoteDisplay display = devices.remove(displayDevice);
                if (display == null)
                    return;
                if (activeDisplay == display)
                    disconnectRemoteDisplay();
            }
        }

        @Override
        public void scanRemoteDisplays() {
            synchronized (getSyncRoot()) {
                scanning = true;
                Intent scan = new Intent(SCAN);
                PackageManager pm = getContext().getPackageManager();
                List<ResolveInfo> services = pm.queryIntentServices(scan, 0);
                if (services == null)
                    return;
                for (ResolveInfo info: services) {
                    Intent intent = new Intent();
                    ComponentName name = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
                    intent.setComponent(name);
                    intent.setAction(SCAN);
                    getContext().startService(intent);
                }
                handleSendStatusChangeBroadcast();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScan();
                    }
                }, 10000L);
            }
        }

        @Override
        public void connectRemoteDisplay(String address) throws RemoteException {
            synchronized (getSyncRoot()) {
                RemoteDisplay display = devices.get(address);
                if (display == null) {
                    Log.e(TAG, "Could not find display?");
                    return;
                }
                if (display.remoteDisplayDevice != null) {
                    Log.e(TAG, "Display device is already connected?");
                    return;
                }
                Surface surface;
                try {
                    surface = display.displayDevice.createDisplaySurface();
                    if (surface == null) {
                        Log.e(TAG, "Returned null Surface");
                        return;
                    }
                }
                catch (RemoteException e) {
                    Log.e(TAG, "Error creating surface", e);
                    return;
                }
                IBinder displayToken = SurfaceControl.createDisplay(display.info.name, false);
                display.remoteDisplayDevice = new RemoteDisplayDevice(surface, displayToken, display.info);
                activeDisplay = display;
                sendDisplayDeviceEventLocked(display.remoteDisplayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
                handleSendStatusChangeBroadcast();
            }
        }

        @Override
        public void disconnectRemoteDisplay() {
            synchronized (getSyncRoot()) {
                if (activeDisplay != null && activeDisplay.remoteDisplayDevice != null) {
                	try {
						activeDisplay.displayDevice.stop();
					} catch (Exception e) {
					}
                    activeDisplay.remoteDisplayDevice.clearSurfaceLocked();
                    sendDisplayDeviceEventLocked(activeDisplay.remoteDisplayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
                    activeDisplay.remoteDisplayDevice = null;
                }
                activeDisplay = null;
                handleSendStatusChangeBroadcast();
            }
        }

        @Override
        public void forgetRemoteDisplay(String address) {
            synchronized (getSyncRoot()) {
            }
        }

        @Override
        public void renameRemoteDisplay(String address, String alias) {
            synchronized (getSyncRoot()) {
            }
        }

        @Override
        public WifiDisplayStatus getRemoteDisplayStatus() {
            synchronized (getSyncRoot()) {
                ArrayList<WifiDisplay> availableDisplays = new ArrayList<WifiDisplay>();
                for (RemoteDisplay display: devices.values()) {
                    WifiDisplay add = new WifiDisplay(display.info.address, display.info.name, display.info.name);
                    availableDisplays.add(add);
                }

                WifiDisplay active = null;
                if (activeDisplay != null)
                    active = new WifiDisplay(activeDisplay.info.address, activeDisplay.info.name, activeDisplay.info.name);

                WifiDisplayStatus status = new WifiDisplayStatus(WifiDisplayStatus.FEATURE_STATE_ON,
                    scanning ? WifiDisplayStatus.SCAN_STATE_SCANNING : WifiDisplayStatus.SCAN_STATE_NOT_SCANNING,
                    WifiDisplayStatus.DISPLAY_STATE_NOT_CONNECTED,
                    active,
                    availableDisplays.toArray(new WifiDisplay[0]),
                    WifiDisplay.EMPTY_ARRAY);
                return status;
            }
        }
    };

    Stub mStub = new Stub();

    private static final String SCAN = "com.cyanogenmod.server.display.SCAN";
    private static final String STOP_SCAN = "com.cyanogenmod.server.display.STOP_SCAN";
    public void stopScan() {
        scanning = false;
        Intent scan = new Intent(SCAN);
        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(scan, 0);
        if (services == null)
            return;
        for (ResolveInfo info: services) {
            Intent intent = new Intent();
            ComponentName name = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
            intent.setComponent(name);
            intent.setAction(STOP_SCAN);
            getContext().startService(intent);
        }
        handleSendStatusChangeBroadcast();
    }

    // Runs on the handler.
    private void handleSendStatusChangeBroadcast() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(DisplayManager.ACTION_REMOTE_DISPLAY_STATUS_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                intent.putExtra(DisplayManager.EXTRA_REMOTE_DISPLAY_STATUS,
                        mStub.getRemoteDisplayStatus());

                // Send protected broadcast about wifi display status to registered receivers.
                getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        });
    }

    private class RemoteDisplayDevice extends DisplayDevice {
        private Surface surface;
        public RemoteDisplayDevice(Surface surface, IBinder displayToken, DisplayDeviceInfo info) {
            super(RemoteDisplayAdapter.this, displayToken);
            this.surface = surface;
            this.info = info;
        }

        public void clearSurfaceLocked() {
            surface = null;
            sendTraversalRequestLocked();
        }

        @Override
        public void performTraversalInTransactionLocked() {
            setSurfaceInTransactionLocked(surface);
        }

        private DisplayDeviceInfo info;

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            synchronized (getSyncRoot()) {
                return info;
            }
        }
    }

    public RemoteDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener,
            PersistentDataStore persistentDataStore) {
        super(syncRoot, context, handler, listener, TAG);
        this.handler = new Handler(handler.getLooper());
    }
}
