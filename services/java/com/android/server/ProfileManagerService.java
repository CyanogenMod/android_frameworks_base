
package com.android.server;

import java.util.HashMap;
import java.util.Map;

import android.app.IProfileManager;
import android.app.Profile;
import android.content.Context;
import android.os.RemoteException;

public class ProfileManagerService extends IProfileManager.Stub {

    // TODO Maybe persistence could be added to this class. Currently the client
    // app will persist.

    private static final String TAG = "ProfileService";

    private Map<String, Profile> profiles = new HashMap<String, Profile>();

    private Profile activeProfile;

    public ProfileManagerService(Context context) {
    }

    // TODO Could do with returning true/false to convert to exception
    // TODO Exceptions not supported in aidl.
    @Override
    public void setActiveProfile(String profileName) throws RemoteException {
        activeProfile = profiles.get(profileName);
        // We might want to add logic here to change ring-tone volume settings
        // etc, to widen support beyond notifications.
    }

    @Override
    public void updateProfile(Profile profile) throws RemoteException {
        profiles.put(profile.getName(), profile);
    }

    @Override
    public Profile getProfile(String profileName) throws RemoteException {
        return profiles.get(profileName);
    }

    @Override
    public Profile[] getProfiles() throws RemoteException {
        return profiles.values().toArray(new Profile[profiles.size()]);
    }

    @Override
    public Profile getActiveProfile() throws RemoteException {
        return activeProfile;
    }

    @Override
    public void removeProfile(Profile profile) throws RemoteException {
        profiles.remove(profile.getName());
    }

}
