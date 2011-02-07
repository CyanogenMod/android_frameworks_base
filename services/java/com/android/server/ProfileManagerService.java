package com.android.server;

import android.app.IProfileManager;
import android.app.NotificationGroup;
import android.app.Profile;
import android.app.ProfileGroup;
import android.content.Context;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

public class ProfileManagerService extends IProfileManager.Stub {

    // TODO: Maybe persistence could be added to this class. Currently the
    // client app will persist.

    private static final String TAG = "ProfileService";

    private Map<String, Profile> profiles = new HashMap<String, Profile>();

    private Map<String, NotificationGroup> groups = new HashMap<String, NotificationGroup>();

    private Profile activeProfile;

    public ProfileManagerService(Context context) {
    }

    // TODO: Could do with returning true/false to convert to exception
    // TODO: Exceptions not supported in aidl.
    @Override
    public void setActiveProfile(String profileName) throws RemoteException {
        activeProfile = profiles.get(profileName);
        // We might want to add logic here to change ring-tone volume settings
        // etc, to widen support beyond notifications.
    }

    @Override
    public void addProfile(Profile profile) throws RemoteException {
        // If this is genuinely a new profile, populate it with the groups.
        // Log.i("PROF",
        // "Adding profile : " + profile.getName() + " profilegroups : "
        // + profile.getProfileGroups().length + " groups : " + groups.size());
        if (!profiles.containsKey(profile.getName())
                || profile.getProfileGroups().length < groups.size()) {
            for (NotificationGroup group : groups.values()) {
                // Log.i("PROF", "Adding : " + profile.getName() + " : " +
                // group.getName());
                profile.addProfleGroup(new ProfileGroup(group.getName()));
            }
        }
        profiles.put(profile.getName(), profile);
        // Log.i("PROF",
        // "Added profile : " + profile.getName() + " profilegroups : "
        // + profile.getProfileGroups().length + " groups : " + groups.size());
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

    @Override
    public NotificationGroup[] getNotificationGroups() throws RemoteException {
        return groups.values().toArray(new NotificationGroup[groups.size()]);
    }

    @Override
    public void addNotificationGroup(NotificationGroup group) throws RemoteException {
        if (groups.put(group.getName(), group) == null) {
            // If the above is true, then the ProfileGroup shouldn't exist in
            // the profile, so there is no risk of replacing it.
            for (Profile profile : profiles.values()) {
                // Log.i("PROF", "Adding : " + profile.getName() + " : " +
                // group.getName());
                profile.addProfleGroup(new ProfileGroup(group.getName()));
            }
        }
    }

    @Override
    public void removeNotificationGroup(NotificationGroup group) throws RemoteException {
        groups.remove(group.getName());
        // Remove the corresponding ProfileGroup from all the profiles too if
        // they use it.
        for (Profile profile : profiles.values()) {
            profile.removeProfileGroup(group.getName());
        }
    }

    @Override
    public NotificationGroup getNotificationGroupForPackage(String pkg) throws RemoteException {
        for (NotificationGroup group : groups.values()) {
            if (group.hasPackage(pkg)) {
                return group;
            }
        }
        return null;
    }

}
