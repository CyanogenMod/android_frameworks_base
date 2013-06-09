/**
 * Copyright (C) 2012 Svyatoslav Hresyk
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;

/**
 * PrivacySettingsManager's counterpart running in the system process, which
 * allows write access to /data/
 * 
 * @author Svyatoslav Hresyk TODO: add selective contact access management API
 * 
 *         {@hide}
 */
public final class PrivacySettingsManagerService extends IPrivacySettingsManager.Stub {

    private static final String TAG = "PrivacySettingsManagerService";
    private static final String WRITE_PRIVACY_SETTINGS = "android.privacy.WRITE_PRIVACY_SETTINGS";
    private static final String READ_PRIVACY_SETTINGS = "android.privacy.READ_PRIVACY_SETTINGS";

    private static boolean sendNotifications = true; 
    private PrivacyPersistenceAdapter persistenceAdapter;
    
    private Context context;

    public static PrivacyFileObserver obs;

    private boolean enabled;
    private boolean notificationsEnabled;
    private boolean bootCompleted;

    static final double API_VERSION = 1.51;
    static final double MOD_VERSION = 1.0;
    static final String MOD_DETAILS = "OpenPDroid 1.0 by FFU5y, Mateor, wbedard; forked from PDroid 2.0\n" +
    		"PDroid 2.0 by CollegeDev; forked from PDroid\n" +
    		"PDroid by Syvat's\n" +
    		"Additional contributions by Pastime1971";

    /**
     * @hide - this should be instantiated through Context.getSystemService
     * @param context
     */
    public PrivacySettingsManagerService(Context context) {
        Log.i(TAG,
                "PrivacySettingsManagerService - initializing for package: "
                        + context.getPackageName() + " UID: " + Binder.getCallingUid());
        this.context = context;

        persistenceAdapter = new PrivacyPersistenceAdapter(context);
        obs = new PrivacyFileObserver("/data/system/privacy", this);

        enabled = persistenceAdapter.getValue(PrivacyPersistenceAdapter.SETTING_ENABLED).equals(
                PrivacyPersistenceAdapter.VALUE_TRUE);
        notificationsEnabled = persistenceAdapter.getValue(
                PrivacyPersistenceAdapter.SETTING_NOTIFICATIONS_ENABLED).equals(
                PrivacyPersistenceAdapter.VALUE_TRUE);
        bootCompleted = false;
    }

    public PrivacySettings getSettings(String packageName) {
        // Log.d(TAG, "getSettings - " + packageName);
        if (enabled || context.getPackageName().equals("com.privacy.pdroid")
                || context.getPackageName().equals("com.privacy.pdroid.Addon")
                || context.getPackageName().equals("com.android.privacy.pdroid.extension"))
            // we have to add our addon package here, to get real settings
            return persistenceAdapter.getSettings(packageName);
        else
            return null;
    }

    public boolean saveSettings(PrivacySettings settings) throws RemoteException {
        Log.d(TAG, "saveSettings - checking if caller (UID: " + Binder.getCallingUid()
                + ") has sufficient permissions");
        // Why are we letting the system delete package settings??
        if (Binder.getCallingUid() != 1000) {
            checkCallerCanWriteOrThrow();
        }
        
        Log.d(TAG, "saveSettings - " + settings);
        boolean result = persistenceAdapter.saveSettings(settings);
        if (result == true)
            obs.addObserver(settings.getPackageName());
        return result;
    }

    public boolean deleteSettings(String packageName) throws RemoteException {
        // Why are we letting the system delete package settings??
        if (Binder.getCallingUid() != 1000) {
            checkCallerCanWriteOrThrow();
        }

        boolean result = persistenceAdapter.deleteSettings(packageName);
        // update observer if directory exists
        String observePath = PrivacyPersistenceAdapter.SETTINGS_DIRECTORY + "/" + packageName;
        if (new File(observePath).exists() && result == true) {
            obs.addObserver(observePath);
        } else if (result == true) {
            obs.children.remove(observePath);
        }
        return result;
    }

    public void notification(final String packageName, final byte accessMode,
            final String dataType, final String output) {
        if (bootCompleted && notificationsEnabled && sendNotifications) {
            Intent intent = new Intent();
            intent.setAction(PrivacySettingsManager.ACTION_PRIVACY_NOTIFICATION);
            intent.putExtra("packageName", packageName);
            intent.putExtra("uid", PrivacyPersistenceAdapter.DUMMY_UID);
            intent.putExtra("accessMode", accessMode);
            intent.putExtra("dataType", dataType);
            intent.putExtra("output", output);
            context.sendBroadcast(intent);
        }
    }

    public void registerObservers() throws RemoteException {
        checkCallerCanWriteOrThrow();
        obs = new PrivacyFileObserver("/data/system/privacy", this);
    }

    public void addObserver(String packageName) throws RemoteException {
        checkCallerCanWriteOrThrow();
        obs.addObserver(packageName);
    }

    public boolean purgeSettings() {
        return persistenceAdapter.purgeSettings();
    }

    public void setBootCompleted() {
        bootCompleted = true;
    }

    public boolean setNotificationsEnabled(boolean enable) throws RemoteException {
        checkCallerCanWriteOrThrow();
        String value = enable ? PrivacyPersistenceAdapter.VALUE_TRUE
                : PrivacyPersistenceAdapter.VALUE_FALSE;
        if (persistenceAdapter.setValue(PrivacyPersistenceAdapter.SETTING_NOTIFICATIONS_ENABLED,
                value)) {
            this.notificationsEnabled = true;
            this.bootCompleted = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Enables or disables PDroid protection. If 'enabled' = true, PDroid will
     * return valid settings. Otherwise it will return 'null', which allows all.
     * Setting to 'enabled' has immediate effects; setting to 'disabled' has no effect until next reboot.
     * @param newIsEnabled 
     * @return new 'enabled' state.
     */
    public boolean setEnabled(boolean newIsEnabled) throws RemoteException {
        checkCallerCanWriteOrThrow();
        String value = newIsEnabled ? PrivacyPersistenceAdapter.VALUE_TRUE
                : PrivacyPersistenceAdapter.VALUE_FALSE;
        if (persistenceAdapter.setValue(PrivacyPersistenceAdapter.SETTING_ENABLED, value)) {
            this.enabled = true;
            return true;
        } else {
            return false;
        }
    }
    
        /**
     * Check the caller of the service has privileges to write to it
	 * Throw an exception if not. 
	 */
	private void checkCallerCanWriteOrThrow() throws RemoteException {
		context.enforceCallingPermission(WRITE_PRIVACY_SETTINGS,
				"Requires WRITE_PRIVACY_SETTINGS");
		//for future:
		// if not allowed then throw
		//			throw new SecurityException("Attempted to write without sufficient priviliges");

	}
	
	/**
	 * Check that the caller of the service has privileges to write to it.
	 * @return true if caller can write, false otherwise.
	 */
	private boolean checkCallerCanWriteSettings() throws RemoteException {
		try {
			checkCallerCanWriteOrThrow();
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}

	/**
	 * Check the caller of the service has privileges to read from it
	 * Throw an exception if not. 
	 */
	private void checkCallerCanReadOrThrow() {
		if (Binder.getCallingUid() == 1000) {
			return;
		}
		context.enforceCallingPermission(READ_PRIVACY_SETTINGS,
				"Requires READ_PRIVACY_SETTINGS");
		//for future:
		// if not allowed then throw
		//			throw new SecurityException("Attempted to read without sufficient priviliges");

	}
	
	/**
	 * Check that the caller of the service has privileges to read from it.
	 * @return true if caller can read, false otherwise.
	 */
	private boolean checkCallerCanReadSettings() {
		try {
			checkCallerCanReadOrThrow();
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}
	
}
