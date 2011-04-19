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

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

public class ProfileManager
{

    private static IProfileManager sService;

    private Context                mContext;

    private static final String TAG = "ProfileManager";

    /** @hide */
    static public IProfileManager getService()
    {
        if (sService != null)
        {
            return sService;
        }
        IBinder b = ServiceManager.getService(Context.PROFILE_SERVICE);
        sService = IProfileManager.Stub.asInterface(b);
        return sService;
    }

    /** @hide */
    ProfileManager(Context context, Handler handler)
    {
        mContext = context;
    }

    public void setActiveProfile(String profileName) {
        try {
            getService().setActiveProfile(profileName);
            getService().persist();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public Profile getActiveProfile(){
        try {
            return getService().getActiveProfile();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public void addProfile(Profile profile){
        try {
            getService().addProfile(profile);
            getService().persist();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void removeProfile(Profile profile){
        try {
            getService().removeProfile(profile);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public Profile getProfile(String profileName){
        try {
            return getService().getProfile(profileName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public String[] getProfileNames(){
        try {
            Profile[] profiles = getService().getProfiles();
            String[] names = new String[profiles.length];
            int i=0;
            for(Profile profile : profiles){
                names[i++] = profile.getName();
            }
            return names;
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    public Profile[] getProfiles(){
        try {
            return getService().getProfiles();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public NotificationGroup[] getNotificationGroups(){
        try {
            return getService().getNotificationGroups();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public void addNotificationGroup(NotificationGroup group){
        try {
            getService().addNotificationGroup(group);
            getService().persist();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public void removeNotificationGroup(NotificationGroup group){
        try {
            getService().removeNotificationGroup(group);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /** @hide */
    public NotificationGroup getNotificationGroupForPackage(String pkg){
        try {
            return getService().getNotificationGroupForPackage(pkg);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /** @hide */
    public NotificationGroup getNotificationGroup(String name){
        try {
            return getService().getNotificationGroup(name);
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
            Log.v(TAG, "No active group, returning default: " + (defaultGroup == null ? "null" : defaultGroup.getName()));
            return defaultGroup;
        }
        String notificationGroupName = notificationGroup.getName();
        return getActiveProfile().getProfileGroup(notificationGroupName);
    }

}
