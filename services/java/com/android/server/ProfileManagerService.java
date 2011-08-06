/*
 * Copyright (c) 2011, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.IProfileManager;
import android.app.NotificationGroup;
import android.app.Profile;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.os.ParcelUuid;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** {@hide} */
public class ProfileManagerService extends IProfileManager.Stub {
    // Enable the below for detailed logging of this class
    private static final boolean LOCAL_LOGV = false;
    /**
     * <p>Broadcast Action: A new profile has been selected. This can be triggered by the user
     * or by calls to the ProfileManagerService / Profile.</p>
     * @hide
     */
    public static final String INTENT_ACTION_PROFILE_SELECTED = "android.intent.action.PROFILE_SELECTED";

    public static final String PERMISSION_CHANGE_SETTINGS = "android.permission.WRITE_SETTINGS";

    private static final String PROFILE_FILENAME = "/data/system/profiles.xml";

    private static final String TAG = "ProfileService";

    private Map<UUID, Profile> mProfiles = new HashMap<UUID, Profile>();

    // Match UUIDs and names, used for reverse compatibility
    private Map<String, UUID> mProfileNames = new HashMap<String, UUID>();

    private Map<String, NotificationGroup> mGroups = new HashMap<String, NotificationGroup>();

    private Profile mActiveProfile;

    private Context mContext;

    public ProfileManagerService(Context context) {
        mContext = context;
        try {
            loadFromFile();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            try {
                initialiseStructure();
            } catch (Throwable ex) {
                Log.e(TAG, "Error loading xml from resource: ", ex);
            }
        } catch (IOException e) {
            try {
                initialiseStructure();
            } catch (Throwable ex) {
                Log.e(TAG, "Error loading xml from resource: ", ex);
            }
        }
    }

    @Override
    @Deprecated
    public boolean setActiveProfileByName(String profileName) throws RemoteException, SecurityException {
        if (mProfileNames.containsKey(profileName)) {
            if (LOCAL_LOGV) Log.v(TAG, "setActiveProfile(String) found profile name in mProfileNames.");
            return setActiveProfile(mProfiles.get(mProfileNames.get(profileName)), true);
        } else {
            // Since profileName could not be casted into a UUID, we can call it a string.
            Log.w(TAG, "Unable to find profile to set active, based on string: " + profileName);
            return false;
        }
    }

    @Override
    public boolean setActiveProfile(ParcelUuid profileParcelUuid) throws RemoteException, SecurityException {
        UUID profileUuid = profileParcelUuid.getUuid();
        if(mProfiles.containsKey(profileUuid)){
            if (LOCAL_LOGV) Log.v(TAG, "setActiveProfileByUuid(ParcelUuid) found profile UUID in mProfileNames.");
            return setActiveProfile(mProfiles.get(profileUuid), true);
        } else {
            Log.e(TAG, "Cannot set active profile to: " + profileUuid.toString() + " - does not exist.");
            return false;
        }
    }

    private boolean setActiveProfile(UUID profileUuid, boolean doinit) throws RemoteException {
        if(mProfiles.containsKey(profileUuid)){
            if (LOCAL_LOGV) Log.v(TAG, "setActiveProfile(UUID, boolean) found profile UUID in mProfiles.");
            return setActiveProfile(mProfiles.get(profileUuid), doinit);
        } else {
            Log.e(TAG, "Cannot set active profile to: " + profileUuid.toString() + " - does not exist.");
            return false;
        }
    }

    private boolean setActiveProfile(Profile newActiveProfile, boolean doinit) throws RemoteException {
        /*
         * NOTE: Since this is not a public function, and all public functions
         * take either a string or a UUID, the active profile should always be
         * in the collection.  If writing another setActiveProfile which receives
         * a Profile object, run enforceChangePermissions, add the profile to the
         * list, and THEN add it.
         */

        try {
            enforceChangePermissions();
            Log.d(TAG, "Set active profile to: " + newActiveProfile.getUuid().toString() + " - " + newActiveProfile.getName());
            Profile lastProfile = mActiveProfile;
            mActiveProfile = newActiveProfile;
            if(doinit){
                if (LOCAL_LOGV) Log.v(TAG, "setActiveProfile(Profile, boolean) - Running init");

                // Call profile's "doSelect"
                mActiveProfile.doSelect(mContext);

                /*
                 * Clearing the calling identity AFTER the profile doSelect
                 * to reduce security risks based on an external class extending the
                 * Profile class and embedding malicious code to be executed with "system" rights.
                 * This isn't a fool-proof safety measure, but it's better than giving
                 * the child class system-level access by simply calling setActiveProfile.
                 *
                 * We need to clear the permissions to broadcast INTENT_ACTION_PROFILE_SELECTED.
                 */
                long token = clearCallingIdentity();
                // Notify other applications of newly selected profile.
                Intent broadcast = new Intent(INTENT_ACTION_PROFILE_SELECTED);
                broadcast.putExtra("name", mActiveProfile.getName());
                broadcast.putExtra("uuid", mActiveProfile.getUuid().toString());
                broadcast.putExtra("lastName", lastProfile.getName());
                broadcast.putExtra("lastUuid", lastProfile.getUuid().toString());
                mContext.sendBroadcast(broadcast);
                restoreCallingIdentity(token);
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean addProfile(Profile profile) throws RemoteException, SecurityException {
        enforceChangePermissions();
        // Make sure this profile has all of the correct groups.
        for (NotificationGroup group : mGroups.values()) {
            profile.ensureProfileGroup(group.getName());
        }
        profile.ensureProfileGroup(
                mContext.getString(com.android.internal.R.string.wildcardProfile), true);
        mProfiles.put(profile.getUuid(), profile);
        mProfileNames.put(profile.getName(), profile.getUuid());
        return true;
    }

    @Override
    @Deprecated
    public Profile getProfileByName(String profileName) throws RemoteException {
        if (mProfileNames.containsKey(profileName)) {
            return mProfiles.get(mProfileNames.get(profileName));
        } else if (mProfiles.containsKey(UUID.fromString((profileName)))) {
            return mProfiles.get(UUID.fromString(profileName));
        } else {
            return null;
        }
    }

    @Override
    public Profile getProfile(ParcelUuid profileParcelUuid) {
        UUID profileUuid = profileParcelUuid.getUuid();
        return mProfiles.get(profileUuid);
    }

    public Profile getProfile(UUID profileUuid) {
        return mProfiles.get(profileUuid);
    }

    @Override
    public Profile[] getProfiles() throws RemoteException {
        return mProfiles.values().toArray(new Profile[mProfiles.size()]);
    }

    @Override
    public Profile getActiveProfile() throws RemoteException {
        return mActiveProfile;
    }

    @Override
    public boolean removeProfile(Profile profile) throws RemoteException, SecurityException {
        enforceChangePermissions();
        if (mProfileNames.remove(profile.getName()) != null && mProfiles.remove(profile.getUuid()) != null) {
            return true;
        } else{
            return false;
        }
    }

    @Override
    public boolean profileExists(ParcelUuid profileUuid) throws RemoteException {
        return mProfiles.containsKey(profileUuid.getUuid());
    }

    @Override
    public boolean profileExistsByName(String profileName) throws RemoteException {
        return mProfileNames.containsKey(profileName);
    }

    @Override
    public NotificationGroup[] getNotificationGroups() throws RemoteException {
        return mGroups.values().toArray(new NotificationGroup[mGroups.size()]);
    }

    @Override
    public void addNotificationGroup(NotificationGroup group) throws RemoteException, SecurityException {
        enforceChangePermissions();
        if (mGroups.put(group.getName(), group) == null) {
            // If the above is true, then the ProfileGroup shouldn't exist in
            // the profile. Ensure it is added.
            for (Profile profile : mProfiles.values()) {
                profile.ensureProfileGroup(group.getName());
            }
        }
    }

    @Override
    public void removeNotificationGroup(NotificationGroup group) throws RemoteException, SecurityException {
        enforceChangePermissions();
        mGroups.remove(group.getName());
        // Remove the corresponding ProfileGroup from all the profiles too if
        // they use it.
        for (Profile profile : mProfiles.values()) {
            profile.removeProfileGroup(group.getName());
        }
    }

    @Override
    public NotificationGroup getNotificationGroupForPackage(String pkg) throws RemoteException {
        for (NotificationGroup group : mGroups.values()) {
            if (group.hasPackage(pkg)) {
                return group;
            }
        }
        return null;
    }

    private void loadFromFile() throws RemoteException, XmlPullParserException, IOException {
        XmlPullParserFactory xppf = XmlPullParserFactory.newInstance();
        XmlPullParser xpp = xppf.newPullParser();
        FileReader fr = new FileReader(PROFILE_FILENAME);
        xpp.setInput(fr);
        boolean saveRequired = loadXml(xpp);
        fr.close();
        if (saveRequired) {
            persist();
        }
    }

    private boolean loadXml(XmlPullParser xpp) throws XmlPullParserException, IOException,
            RemoteException {
        return loadXml(xpp, null);
    }

    private boolean loadXml(XmlPullParser xpp, Context context) throws XmlPullParserException, IOException,
            RemoteException {
        boolean saveRequired = false;
        int event = xpp.next();
        String active = null;
        while (event != XmlPullParser.END_TAG || !"profiles".equals(xpp.getName())) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("active")) {
                    active = xpp.nextText();
                    Log.d(TAG, "Found active: " + active);
                } else if (name.equals("profile")) {
                    Profile prof = Profile.fromXml(xpp, context);
                    addProfile(prof);
                    // Failsafe if no active found
                    if (active == null) {
                        active = prof.getUuid().toString();
                    }
                } else if (name.equals("notificationGroup")) {
                    NotificationGroup ng = NotificationGroup.fromXml(xpp, context);
                    addNotificationGroup(ng);
                }
            }
            event = xpp.next();
        }
        // Don't do initialisation on startup. The AudioManager doesn't exist yet
        // and besides, the volume settings will have survived the reboot.
        try {
            // Try / catch block to detect if XML file needs to be upgraded.
            setActiveProfile(UUID.fromString(active), false);
        } catch (IllegalArgumentException e) {
            if (mProfileNames.containsKey(active)) {
                setActiveProfile(mProfileNames.get(active), false);
            } else {
                // Final fail-safe: We must have SOME profile active.
                // If we couldn't select one by now, we'll pick the first in the set.
                setActiveProfile(mProfiles.values().iterator().next(), false);
            }
            // This is a hint that we probably just upgraded the XML file. Save changes.
            saveRequired = true;
        }
        return saveRequired;
    }

    private void initialiseStructure() throws RemoteException, XmlPullParserException, IOException {
        XmlResourceParser xml = mContext.getResources().getXml(
                com.android.internal.R.xml.profile_default);
        try {
            loadXml(xml, mContext);
            persist();
        } finally {
            xml.close();
        }
    }

    private String getXmlString() throws RemoteException {
        StringBuilder builder = new StringBuilder();
        builder.append("<profiles>\n<active>");
        builder.append(TextUtils.htmlEncode(getActiveProfile().getUuid().toString()));
        builder.append("</active>\n");

        for (Profile p : mProfiles.values()) {
            p.getXmlString(builder);
        }
        for (NotificationGroup g : mGroups.values()) {
            g.getXmlString(builder);
        }
        builder.append("</profiles>\n");
        return builder.toString();
    }

    @Override
    public NotificationGroup getNotificationGroup(String name) throws RemoteException {
        return mGroups.get(name);
    }

    @Override
    public void persist() throws RemoteException, SecurityException {
        enforceChangePermissions();
        long token = clearCallingIdentity();
        try {
            Log.d(TAG, "Saving profile data...");
            FileWriter fw = new FileWriter(PROFILE_FILENAME);
            fw.write(getXmlString());
            fw.close();
            Log.d(TAG, "Save completed.");
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            restoreCallingIdentity(token);
        }
    }

    private void enforceChangePermissions() throws SecurityException {
        mContext.enforceCallingOrSelfPermission(PERMISSION_CHANGE_SETTINGS, "You do not have permissions to change the Profile Manager.");
    }
}
