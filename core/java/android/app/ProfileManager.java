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
 * limitations under the License.
 */

package android.app;

import java.util.UUID;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * @hide
 */
public class ProfileManager {

    private static IProfileManager sService;

    private Context mContext;

    private static final String TAG = "ProfileManager";

    /** @hide */
    static public IProfileManager getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(Context.PROFILE_SERVICE);
        sService = IProfileManager.Stub.asInterface(b);
        return sService;
    }

    /** @hide */
    ProfileManager(Context context, Handler handler) {
        mContext = context;
    }

    @Deprecated
    public void setActiveProfile(String profileName) {
        try {
            getService().setActiveProfileByName(profileName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public void setActiveProfile(UUID profileUuid) {
        try {
            getService().setActiveProfile(new ParcelUuid(profileUuid));
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public Profile getActiveProfile() {
        try {
            return getService().getActiveProfile();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public void addProfile(Profile profile) {
        try {
            getService().addProfile(profile);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void removeProfile(Profile profile) {
        try {
            getService().removeProfile(profile);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void updateProfile(Profile profile) {
        try {
            getService().updateProfile(profile);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    @Deprecated
    public Profile getProfile(String profileName) {
        try {
            return getService().getProfileByName(profileName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public Profile getProfile(UUID profileUuid) {
        try {
            return getService().getProfile(new ParcelUuid(profileUuid));
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public String[] getProfileNames() {
        try {
            Profile[] profiles = getService().getProfiles();
            String[] names = new String[profiles.length];
            for (int i = 0; i < profiles.length; i++) {
                names[i] = profiles[i].getName();
            }
            return names;
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public Profile[] getProfiles() {
        try {
            return getService().getProfiles();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public boolean profileExists(String profileName) {
        try {
            return getService().profileExistsByName(profileName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            // To be on the safe side, we'll return "true", to prevent duplicate profiles
            // from being created.
            return true;
        }
    }

    public boolean profileExists(UUID profileUuid) {
        try {
            return getService().profileExists(new ParcelUuid(profileUuid));
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            // To be on the safe side, we'll return "true", to prevent duplicate profiles
            // from being created.
            return true;
        }
    }

    public boolean notificationGroupExists(String notificationGroupName) {
        try {
            return getService().notificationGroupExistsByName(notificationGroupName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            // To be on the safe side, we'll return "true", to prevent duplicate notification
            // groups from being created.
            return true;
        }
    }

    /** @hide */
    public NotificationGroup[] getNotificationGroups() {
        try {
            return getService().getNotificationGroups();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public void addNotificationGroup(NotificationGroup group) {
        try {
            getService().addNotificationGroup(group);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void removeNotificationGroup(NotificationGroup group) {
        try {
            getService().removeNotificationGroup(group);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void updateNotificationGroup(NotificationGroup group) {
        try {
            getService().updateNotificationGroup(group);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public NotificationGroup getNotificationGroupForPackage(String pkg) {
        try {
            return getService().getNotificationGroupForPackage(pkg);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public NotificationGroup getNotificationGroup(UUID uuid) {
        try {
            return getService().getNotificationGroup(new ParcelUuid(uuid));
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public ProfileGroup getActiveProfileGroup(String packageName) {
        NotificationGroup notificationGroup = getNotificationGroupForPackage(packageName);
        if(notificationGroup == null){
            ProfileGroup defaultGroup = getActiveProfile().getDefaultGroup();
            return defaultGroup;
        }
        return getActiveProfile().getProfileGroup(notificationGroup.getUuid());
    }

    /** @hide */
    public void resetAll() {
        try {
            getService().resetAll();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        } catch (SecurityException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }
}
