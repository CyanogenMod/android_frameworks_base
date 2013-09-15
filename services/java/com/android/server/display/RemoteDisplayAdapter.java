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
import android.hardware.display.IDisplayAdapterListener;
import android.hardware.display.IDisplayDevice;
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

	IDisplayAdapterListener.Stub mRemoteDisplayAdapterListener = new IDisplayAdapterListener.Stub() {
    	public void registerDisplayDevice(IDisplayDevice displayDevice, String name, int width, int height, float refreshRate, int flags, String address) throws RemoteException {
    		synchronized (getSyncRoot()) {
    			RemoteDisplay display = new RemoteDisplay();
    			display.info = new DisplayDeviceInfo();
    			display.displayDevice = displayDevice;
    			devices.put(name, display);
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

    	public void unregisterDisplayDevice(IDisplayDevice displayDevice) throws RemoteException {
    		synchronized (getSyncRoot()) {
				RemoteDisplay display = devices.remove(displayDevice);
				if (display == null)
					return;
				if (display.remoteDisplayDevice == null)
					return;
				sendDisplayDeviceEventLocked(display.remoteDisplayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
				handleSendStatusChangeBroadcast();
			}
    	}
	};

    public void requestConnectLocked(final String name) {
    	RemoteDisplay display = devices.get(name);
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
			surface = display.displayDevice.getSurface();
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
		display.remoteDisplayDevice = new RemoteDisplayDevice(surface, displayToken);
		activeDisplay = display;
		sendDisplayDeviceEventLocked(display.remoteDisplayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
    }

	private static final String SCAN = "com.cyanogenmod.server.display.SCAN";
	private static final String STOP_SCAN = "com.cyanogenmod.server.display.STOP_SCAN";
	public void stopScan() {
		scanning = false;
		Intent scan = new Intent("com.cyanogenmod.server.display.SCAN");
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

	public void requestScanLocked() {
		scanning = true;
		Intent scan = new Intent("com.cyanogenmod.server.display.SCAN");
		PackageManager pm = getContext().getPackageManager();
		List<ResolveInfo> services = pm.queryIntentServices(scan, 0);
		if (services == null)
			return;
		for (ResolveInfo info: services) {
			Intent intent = new Intent();
			ComponentName name = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
			intent.setComponent(name);
			intent.setAction(SCAN);
			intent.putExtra("listener", mRemoteDisplayAdapterListener);
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

    // Runs on the handler.
    private void handleSendStatusChangeBroadcast() {
    	handler.post(new Runnable() {
			@Override
			public void run() {
		        final Intent intent = new Intent(DisplayManager.ACTION_REMOTE_DISPLAY_STATUS_CHANGED);
		        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
		        intent.putExtra(DisplayManager.EXTRA_REMOTE_DISPLAY_STATUS,
		                getRemoteDisplayStatus());

		        // Send protected broadcast about wifi display status to registered receivers.
		        getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
			}
		});
    }

	private class RemoteDisplayDevice extends DisplayDevice {
		private Surface surface;
		public RemoteDisplayDevice(Surface surface, IBinder displayToken) {
			super(RemoteDisplayAdapter.this, displayToken);
			this.surface = surface;
		}
		
		@Override
		public void performTraversalInTransactionLocked() {
			setSurfaceInTransactionLocked(surface);
		}

		private DisplayDeviceInfo info = new DisplayDeviceInfo();
		
		@Override
		public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
			synchronized (getSyncRoot()) {
				return info;
			}
		}
	}

	public IDisplayAdapterListener.Stub getRemoteDisplayAdapterListener() {
		return mRemoteDisplayAdapterListener;
	}

    public RemoteDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener,
            PersistentDataStore persistentDataStore) {
        super(syncRoot, context, handler, listener, TAG);
        this.handler = new Handler(handler.getLooper());
    }

    public void forgetRemoteDisplay(String address) throws RemoteException {
    }
    
    public void requestDisconnectLocked() throws RemoteException {
    	activeDisplay = null;
    }

    public WifiDisplayStatus getRemoteDisplayStatus() {
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
    
    public void renameRemoteDisplay(String address, String alias)
    		throws RemoteException {
    }
}
