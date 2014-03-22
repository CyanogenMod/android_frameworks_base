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

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.R;


/**
 * @hide
 */
public class ProfileManager {

    private static IProfileManager sService;

    private Context mContext;

    private static final String TAG = "ProfileManager";

    private static final String SYSTEM_PROFILES_ENABLED = "system_profiles_enabled";

    /**
     * <p>Broadcast Action: A new profile has been selected. This can be triggered by the user
     * or by calls to the ProfileManagerService / Profile.</p>
     * @hide
     */
    public static final String INTENT_ACTION_PROFILE_SELECTED =
            "android.intent.action.PROFILE_SELECTED";

    /**
     * Extra for {@link INTENT_ACTION_PROFILE_SELECTED} and {@link INTENT_ACTION_PROFILE_UPDATED}:
     * The name of the newly activated or updated profile
     * @hide
     */
    public static final String EXTRA_PROFILE_NAME = "name";

    /**
     * Extra for {@link INTENT_ACTION_PROFILE_SELECTED} and {@link INTENT_ACTION_PROFILE_UPDATED}:
     * The string representation of the UUID of the newly activated or updated profile
     * @hide
     */
    public static final String EXTRA_PROFILE_UUID = "uuid";

    /**
     * Extra for {@link INTENT_ACTION_PROFILE_SELECTED}:
     * The name of the previously active profile
     * @hide
     */
    public static final String EXTRA_LAST_PROFILE_NAME = "lastName";

    /**
     * Extra for {@link INTENT_ACTION_PROFILE_SELECTED}:
     * The string representation of the UUID of the previously active profile
     * @hide
     */
    public static final String EXTRA_LAST_PROFILE_UUID = "uuid";

    /**
    * <p>Broadcast Action: Current profile has been updated. This is triggered every time the
    * currently active profile is updated, instead of selected.</p>
    * <p> For instance, this includes profile updates caused by a locale change, which doesn't
    * trigger a profile selection, but causes its name to change.</p>
    * @hide
    */
    public static final String INTENT_ACTION_PROFILE_UPDATED =
            "android.intent.action.PROFILE_UPDATED";

    /**
     * Activity Action: Shows a profile picker.
     * <p>
     * Input: {@link #EXTRA_PROFILE_EXISTING_UUID}, {@link #EXTRA_PROFILE_SHOW_NONE},
     * {@link #EXTRA_PROFILE_TITLE}.
     * <p>
     * Output: {@link #EXTRA_PROFILE_PICKED_UUID}.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PROFILE_PICKER = "android.intent.action.PROFILE_PICKER";

    /**
     * @hide
     */
    public static final UUID NO_PROFILE =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Given to the profile picker as a boolean. Whether to show an item for
     * deselect the profile. If the "None" item is picked,
     * {@link #EXTRA_PROFILE_PICKED_UUID} will be {@link #NO_PROFILE}.
     *
     * @see #ACTION_PROFILE_PICKER
     * @hide
     */
    public static final String EXTRA_PROFILE_SHOW_NONE =
            "android.intent.extra.profile.SHOW_NONE";

    /**
     * Given to the profile picker as a {@link UUID} string representation. The {@link UUID}
     * representation of the current profile, which will be used to show a checkmark next to
     * the item for this {@link UUID}. If the item is {@link #NO_PROFILE} then "None" item
     * is selected if {@link EXTRA_PROFILE_SHOW_NONE} is enabled. Otherwise, the current
     * profile is selected.
     *
     * @see #ACTION_PROFILE_PICKER
     * @hide
     */
    public static final String EXTRA_PROFILE_EXISTING_UUID =
            "android.intent.extra.profile.EXISTING_UUID";

    /**
     * Given to the profile picker as a {@link CharSequence}. The title to
     * show for the profile picker. This has a default value that is suitable
     * in most cases.
     *
     * @see #ACTION_PROFILE_PICKER
     * @hide
     */
    public static final String EXTRA_PROFILE_TITLE = "android.intent.extra.profile.TITLE";

    /**
     * Returned from the profile picker as a {@link UUID} string representation.
     * <p>
     * It will be one of:
     * <li> the picked profile,
     * <li> null if the "None" item was picked.
     *
     * @see #ACTION_PROFILE_PICKER
     * @hide
     */
    public static final String EXTRA_PROFILE_PICKED_UUID =
            "android.intent.extra.profile.PICKED_UUID";


    /**
     * Broadcast intent action indicating that Profiles has been enabled or disabled.
     * One extra provides this state as an int.
     *
     * @see #EXTRA_PROFILES_STATE
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String PROFILES_STATE_CHANGED_ACTION =
        "android.app.profiles.PROFILES_STATE_CHANGED";
    /**
     * The lookup key for an int that indicates whether Profiles are enabled or
     * disabled. Retrieve it with {@link android.content.Intent#getIntExtra(String,int)}.
     *
     * @see #PROFILES_STATE_DISABLED
     * @see #PROFILES_STATE_ENABLED
     * @hide
     */
    public static final String EXTRA_PROFILES_STATE = "profile_state";

    /**
     * Set the resource id theme to use for the dialog picker activity.<br/>
     * The default theme is <code>com.android.internal.R.Theme_Holo_Dialog_Alert</code>.
     *
     * @see #ACTION_PROFILE_PICKER
     * @hide
     */
    public static final String EXTRA_PROFILE_DIALOG_THEME =
            "android.intent.extra.profile.DIALOG_THEME";

    /**
     * Profiles are disabled.
     *
     * @see #PROFILES_STATE_CHANGED_ACTION
     * @hide
     */
    public static final int PROFILES_STATE_DISABLED = 0;
    /**
     * Profiles are enabled.
     *
     * @see #PROFILES_STATE_CHANGED_ACTION
     * @hide
     */
    public static final int PROFILES_STATE_ENABLED = 1;

    // A blank profile that is created to be returned if profiles disabled
    private static Profile mEmptyProfile;

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
        mEmptyProfile = new Profile("EmptyProfile");
    }

    @Deprecated
    public void setActiveProfile(String profileName) {
        if (Settings.System.getInt(mContext.getContentResolver(),
                SYSTEM_PROFILES_ENABLED, 1) == 1) {
            // Profiles are enabled, return active profile
            try {
                getService().setActiveProfileByName(profileName);
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
        }
    }

    public void setActiveProfile(UUID profileUuid) {
        if (Settings.System.getInt(mContext.getContentResolver(),
                SYSTEM_PROFILES_ENABLED, 1) == 1) {
            // Profiles are enabled, return active profile
            try {
                getService().setActiveProfile(new ParcelUuid(profileUuid));
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
        }
    }

    public Profile getActiveProfile() {
        if (Settings.System.getInt(mContext.getContentResolver(),
                SYSTEM_PROFILES_ENABLED, 1) == 1) {
            // Profiles are enabled, return active profile
            try {
                return getService().getActiveProfile();
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
            return null;

        } else {
            // Profiles are not enabled, return the empty profile
            return mEmptyProfile;
        }

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
