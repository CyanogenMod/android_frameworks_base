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

import android.app.ActivityManagerNative;
import android.app.IProfileManager;
import android.app.NotificationGroup;
import android.app.Profile;
import android.app.ProfileGroup;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XmlResourceParser;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.os.ParcelUuid;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
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

    /**
    * <p>Broadcast Action: Current profile has been updated. This is triggered every time the
    * currently active profile is updated, instead of selected.</p>
    * <p> For instance, this includes profile updates caused by a locale change, which doesn't
    * trigger a profile selection, but causes its name to change.</p>
    * @hide
    */
    public static final String INTENT_ACTION_PROFILE_UPDATED = "android.intent.action.PROFILE_UPDATED";

    public static final String PERMISSION_CHANGE_SETTINGS = "android.permission.WRITE_SETTINGS";

    private static final String PROFILE_FILENAME = "/data/system/profiles.xml";

    private static final String TAG = "ProfileService";

    private Map<UUID, Profile> mProfiles;

    // Match UUIDs and names, used for reverse compatibility
    private Map<String, UUID> mProfileNames;

    private Map<UUID, NotificationGroup> mGroups;

    private Profile mActiveProfile;

    // Well-known UUID of the wildcard group
    private static final UUID mWildcardUUID = UUID.fromString("a126d48a-aaef-47c4-baed-7f0e44aeffe5");
    private NotificationGroup mWildcardGroup;

    private Context mContext;
    private boolean mDirty;

    private WifiManager mWifiManager;
    private String mLastConnectedSSID;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                persistIfDirty();
                initialize();
            } else if (action.equals(Intent.ACTION_SHUTDOWN)) {
                persistIfDirty();

            } else if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                int triggerState;
                switch (state) {
                    case COMPLETED:
                        triggerState = Profile.TriggerState.ON_CONNECT;
                        mLastConnectedSSID = getActiveSSID();
                        break;
                    case DISCONNECTED:
                        triggerState = Profile.TriggerState.ON_DISCONNECT;
                        break;
                    default:
                        return;
                }
                checkTriggers(Profile.TriggerType.WIFI, mLastConnectedSSID, triggerState);
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                    || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                int triggerState = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                        ? Profile.TriggerState.ON_CONNECT : Profile.TriggerState.ON_DISCONNECT;
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                checkTriggers(Profile.TriggerType.BLUETOOTH, device.getAddress(), triggerState);
            }
        }

        private void checkTriggers(int type, String id, int newState) {
            for (Profile p : mProfiles.values()) {
                if (newState != p.getTrigger(type, id)) {
                    continue;
                }

                try {
                    setActiveProfile(p, true);
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not update profile on trigger", e);
                }
            }
        }
    };

    public ProfileManagerService(Context context) {
        mContext = context;
        mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        mLastConnectedSSID = getActiveSSID();

        mWildcardGroup = new NotificationGroup(
                context.getString(com.android.internal.R.string.wildcardProfile),
                com.android.internal.R.string.wildcardProfile,
                mWildcardUUID);

        initialize();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(mIntentReceiver, filter);
    }

    private void initialize() {
        initialize(false);
    }

    private void initialize(boolean skipFile) {
        mProfiles = new HashMap<UUID, Profile>();
        mProfileNames = new HashMap<String, UUID>();
        mGroups = new HashMap<UUID, NotificationGroup>();
        mDirty = false;

        boolean init = skipFile;

        if (! skipFile) {
            try {
                loadFromFile();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                init = true;
            } catch (IOException e) {
                init = true;
            }
        }

        if (init) {
            try {
                initialiseStructure();
            } catch (Throwable ex) {
                Log.e(TAG, "Error loading xml from resource: ", ex);
            }
        }
    }

    private String getActiveSSID() {
        return mWifiManager.getConnectionInfo().getSSID().replace("\"", "");
    }

    @Override
    public void resetAll() {
        enforceChangePermissions();
        initialize(true);
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
            mDirty = true;
            if (doinit) {
                if (LOCAL_LOGV) Log.v(TAG, "setActiveProfile(Profile, boolean) - Running init");

                /*
                 * We need to clear the caller's identity in order to
                 * - allow the profile switch to execute actions not included in the caller's permissions
                 * - broadcast INTENT_ACTION_PROFILE_SELECTED
                 */
                long token = clearCallingIdentity();

                // Call profile's "doSelect"
                mActiveProfile.doSelect(mContext);

                // Notify other applications of newly selected profile.
                Intent broadcast = new Intent(INTENT_ACTION_PROFILE_SELECTED);
                broadcast.putExtra("name", mActiveProfile.getName());
                broadcast.putExtra("uuid", mActiveProfile.getUuid().toString());
                broadcast.putExtra("lastName", lastProfile.getName());
                broadcast.putExtra("lastUuid", lastProfile.getUuid().toString());
                mContext.sendBroadcastAsUser(broadcast, UserHandle.ALL);

                restoreCallingIdentity(token);
                persistIfDirty();
            } else if (lastProfile != mActiveProfile &&
                    ActivityManagerNative.isSystemReady()) {
                // Something definitely changed: notify.
                long token = clearCallingIdentity();
                Intent broadcast = new Intent(INTENT_ACTION_PROFILE_UPDATED);
                broadcast.putExtra("name", mActiveProfile.getName());
                broadcast.putExtra("uuid", mActiveProfile.getUuid().toString());
                mContext.sendBroadcastAsUser(broadcast, UserHandle.ALL);
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
        addProfileInternal(profile);
        persistIfDirty();
        return true;
    }

    private void addProfileInternal(Profile profile) {
        // Make sure this profile has all of the correct groups.
        for (NotificationGroup group : mGroups.values()) {
            ensureGroupInProfile(profile, group, false);
        }
        ensureGroupInProfile(profile, mWildcardGroup, true);
        mProfiles.put(profile.getUuid(), profile);
        mProfileNames.put(profile.getName(), profile.getUuid());
        mDirty = true;
    }

    private void ensureGroupInProfile(Profile profile, NotificationGroup group, boolean defaultGroup) {
        if (profile.getProfileGroup(group.getUuid()) != null) {
            return;
        }

        /* enforce a matchup between profile and notification group, which not only
         * works by UUID, but also by name for backwards compatibility */
        for (ProfileGroup pg : profile.getProfileGroups()) {
            if (pg.matches(group, defaultGroup)) {
                return;
            }
        }

        /* didn't find any, create new group */
        profile.addProfileGroup(new ProfileGroup(group.getUuid(), defaultGroup));
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
        return getProfile(profileUuid);
    }

    public Profile getProfile(UUID profileUuid) {
        // use primary UUID first
        if (mProfiles.containsKey(profileUuid)) {
            return mProfiles.get(profileUuid);
        }
        // if no match was found: try secondary UUID
        for (Profile p : mProfiles.values()) {
            for (UUID uuid : p.getSecondaryUuids()) {
                if (profileUuid.equals(uuid)) {
                    return p;
                }
            }
        }
        // nothing found
        return null;
    }

    @Override
    public Profile[] getProfiles() throws RemoteException {
        Profile[] tmpArr = mProfiles.values().toArray(new Profile[mProfiles.size()]);
        Arrays.sort(tmpArr);
        return tmpArr;
    }

    @Override
    public Profile getActiveProfile() throws RemoteException {
        return mActiveProfile;
    }

    @Override
    public boolean removeProfile(Profile profile) throws RemoteException, SecurityException {
        enforceChangePermissions();
        if (mProfileNames.remove(profile.getName()) != null && mProfiles.remove(profile.getUuid()) != null) {
            mDirty = true;
            persistIfDirty();
            return true;
        } else{
            return false;
        }
    }

    @Override
    public void updateProfile(Profile profile) throws RemoteException, SecurityException {
        enforceChangePermissions();
        Profile old = mProfiles.get(profile.getUuid());
        if (old != null) {
            mProfileNames.remove(old.getName());
            mProfileNames.put(profile.getName(), profile.getUuid());
            mProfiles.put(profile.getUuid(), profile);
            /* no need to set mDirty, if the profile was actually changed,
             * it's marked as dirty by itself */
            persistIfDirty();

            // Also update we changed the active profile
            if (mActiveProfile != null && mActiveProfile.getUuid().equals(profile.getUuid())) {
                setActiveProfile(profile, true);
            }
        }
    }

    @Override
    public boolean profileExists(ParcelUuid profileUuid) throws RemoteException {
        return mProfiles.containsKey(profileUuid.getUuid());
    }

    @Override
    public boolean profileExistsByName(String profileName) throws RemoteException {
        for (Map.Entry<String, UUID> entry : mProfileNames.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(profileName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean notificationGroupExistsByName(String notificationGroupName) throws RemoteException {
        for (NotificationGroup group : mGroups.values()) {
            if (group.getName().equalsIgnoreCase(notificationGroupName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NotificationGroup[] getNotificationGroups() throws RemoteException {
        return mGroups.values().toArray(new NotificationGroup[mGroups.size()]);
    }

    @Override
    public void addNotificationGroup(NotificationGroup group) throws RemoteException, SecurityException {
        enforceChangePermissions();
        addNotificationGroupInternal(group);
        persistIfDirty();
    }

    private void addNotificationGroupInternal(NotificationGroup group) {
        if (mGroups.put(group.getUuid(), group) == null) {
            // If the above is true, then the ProfileGroup shouldn't exist in
            // the profile. Ensure it is added.
            for (Profile profile : mProfiles.values()) {
                ensureGroupInProfile(profile, group, false);
            }
        }
        mDirty = true;
    }

    @Override
    public void removeNotificationGroup(NotificationGroup group) throws RemoteException, SecurityException {
        enforceChangePermissions();
        mDirty |= (mGroups.remove(group.getUuid()) != null);
        // Remove the corresponding ProfileGroup from all the profiles too if
        // they use it.
        for (Profile profile : mProfiles.values()) {
            profile.removeProfileGroup(group.getUuid());
        }
        persistIfDirty();
    }

    @Override
    public void updateNotificationGroup(NotificationGroup group) throws RemoteException, SecurityException {
        enforceChangePermissions();
        NotificationGroup old = mGroups.get(group.getUuid());
        if (old != null) {
            mGroups.put(group.getUuid(), group);
            /* no need to set mDirty, if the group was actually changed,
             * it's marked as dirty by itself */
            persistIfDirty();
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
        loadXml(xpp, mContext);
        fr.close();
        persistIfDirty();
    }

    private void loadXml(XmlPullParser xpp, Context context) throws
            XmlPullParserException, IOException, RemoteException {
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
                    addProfileInternal(prof);
                    // Failsafe if no active found
                    if (active == null) {
                        active = prof.getUuid().toString();
                    }
                } else if (name.equals("notificationGroup")) {
                    NotificationGroup ng = NotificationGroup.fromXml(xpp, context);
                    addNotificationGroupInternal(ng);
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                throw new IOException("Premature end of file while reading " + PROFILE_FILENAME);
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
            mDirty = true;
        }
    }

    private void initialiseStructure() throws RemoteException, XmlPullParserException, IOException {
        XmlResourceParser xml = mContext.getResources().getXml(
                com.android.internal.R.xml.profile_default);
        try {
            loadXml(xml, mContext);
            mDirty = true;
            persistIfDirty();
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
            p.getXmlString(builder, mContext);
        }
        for (NotificationGroup g : mGroups.values()) {
            g.getXmlString(builder, mContext);
        }
        builder.append("</profiles>\n");
        return builder.toString();
    }

    @Override
    public NotificationGroup getNotificationGroup(ParcelUuid uuid) throws RemoteException {
        if (uuid.getUuid().equals(mWildcardGroup.getUuid())) {
            return mWildcardGroup;
        }
        return mGroups.get(uuid.getUuid());
    }

    private synchronized void persistIfDirty() {
        boolean dirty = mDirty;
        if (!dirty) {
            for (Profile profile : mProfiles.values()) {
                if (profile.isDirty()) {
                    dirty = true;
                    break;
                }
            }
        }
        if (!dirty) {
            for (NotificationGroup group : mGroups.values()) {
                if (group.isDirty()) {
                    dirty = true;
                    break;
                }
            }
        }
        if (dirty) {
            try {
                Log.d(TAG, "Saving profile data...");
                FileWriter fw = new FileWriter(PROFILE_FILENAME);
                fw.write(getXmlString());
                fw.close();
                Log.d(TAG, "Save completed.");
                mDirty = false;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void enforceChangePermissions() throws SecurityException {
        mContext.enforceCallingOrSelfPermission(PERMISSION_CHANGE_SETTINGS,
                "You do not have permissions to change the Profile Manager.");
    }
}
