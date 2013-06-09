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
import android.os.RemoteException;
import android.util.Log;

/**
 * Provides API access to the privacy settings
 * @author Svyatoslav Hresyk
 * TODO: selective contacts access
 * {@hide}
 */
public final class PrivacySettingsManager {

    private static final String TAG = "PrivacySettingsManager";
    
    public static final String ACTION_PRIVACY_NOTIFICATION = "com.privacy.pdroid.PRIVACY_NOTIFICATION";
    public static final String ACTION_PRIVACY_NOTIFICATION_ADDON = "com.privacy.pdroid.PRIVACY_NOTIFICATION_ADDON";
    
    private IPrivacySettingsManager service;
    
    /**
     * @hide - this should be instantiated through Context.getSystemService
     * @param context
     */
    public PrivacySettingsManager(Context context, IPrivacySettingsManager service) {
//        Log.d(TAG, "PrivacySettingsManager - initializing for package: " + context.getPackageName() + 
//                " UID:" + Binder.getCallingUid());
        this.service = service;
    }

    @Deprecated
    public PrivacySettings getSettings(String packageName, int uid) {
        return getSettings(packageName);
    }
    
    public PrivacySettings getSettings(String packageName) {
        try {
            if (service != null) {
                return service.getSettings(packageName);
            } else {
                Log.e(TAG, "getSettings - PrivacySettingsManagerService is null");
                return null;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean saveSettings(PrivacySettings settings) {
        try {
//            Log.d(TAG, "saveSettings - " + settings);
            if (service != null) {            
                return service.saveSettings(settings);
            } else {
                Log.e(TAG, "saveSettings - PrivacySettingsManagerService is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in saveSettings: ", e);
            return false;
        }
    }
    
    public boolean deleteSettings(String packageName) {
        try {
            if (service != null) {
                return service.deleteSettings(packageName);
            } else {
                Log.e(TAG, "PrivacySettingsManager:deleteSettings: PrivacySettingsManagerService is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in deleteSettings: ", e);
            return false;
        }
    }
    
    @Deprecated
    public boolean deleteSettings(String packageName, int uid) {
        return deleteSettings(packageName);
    }
    
    /**
     * Checks whether the PrivacySettingsManagerService is available. For some reason,
     * occasionally it appears to be null. In this case it should be initialized again.
     */
    public boolean isServiceAvailable() {
        if (service != null) return true;
        return false;
    }
    
    @Deprecated
    public void notification(String packageName, int uid, byte accessMode, String dataType, String output, PrivacySettings pSet) {
        notification(packageName, accessMode, dataType, output);
    }
    
    @Deprecated
    public void notification(String packageName, byte accessMode, String dataType, String output, PrivacySettings pSet) {
        notification(packageName, accessMode, dataType, output);
    }

    public void notification(String packageName, byte accessMode, String dataType, String output) {
          try {
              if (service != null) {
                  service.notification(packageName, accessMode, dataType, output);
              } else {
                  Log.e(TAG, "PrivacySettingsManager:notification: PrivacySettingsManagerService is null");
              }            
          } catch (RemoteException e) {
              Log.e(TAG, "RemoteException in notification: ", e);
          }
  }
    
    public void registerObservers() {
        try {
            if (service != null) {
                service.registerObservers();
            } else {
                Log.e(TAG, "PrivacySettingsManager:registerObservers: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in registerObservers: ", e);
        }
    }
    
    public void addObserver(String packageName) {
        try {
            if (service != null) {
                service.addObserver(packageName);
            } else {
                Log.e(TAG, "PrivacySettingsManager:addObserver: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in addObserver: ", e);
        }
    }
    
    public boolean purgeSettings() {
        try {
            if (service != null) {
                return service.purgeSettings();
            } else {
                Log.e(TAG, "PrivacySettingsManager:purgeSettings: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in purgeSettings: ", e);
        }
        return false;
    }
    
    @Deprecated
    public double getVersion() {
        return PrivacySettingsManagerService.API_VERSION;
    }

    public double getApiVersion() {
        return PrivacySettingsManagerService.API_VERSION;
    }

    public double getModVersion() {
        return PrivacySettingsManagerService.MOD_VERSION;
    }

    public String getModDetails() {
        return PrivacySettingsManagerService.MOD_DETAILS;
    }
    
    public boolean setEnabled(boolean enable) {
        try {
            if (service != null) {
                return service.setEnabled(enable);
            } else {
                Log.e(TAG, "PrivacySettingsManager:setEnabled: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in setEnabled: ", e);
        }
        return false;
    }
    
    public boolean setNotificationsEnabled(boolean enable) {
        try {
            if (service != null) {
                return service.setNotificationsEnabled(enable);
            } else {
                Log.e(TAG, "PrivacySettingsManager:setNotificationsEnabled: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in setNotificationsEnabled: ", e);
        }
        return false;
    }
    
    public void setBootCompleted() {
        try {
            if (service != null) {
                service.setBootCompleted();
            } else {
                Log.e(TAG, "PrivacySettingsManager:setBootCompleted: PrivacySettingsManagerService is null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in setBootCompleted: ", e);
        }
    }
}
