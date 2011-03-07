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
import android.content.res.XmlResourceParser;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ProfileManagerService extends IProfileManager.Stub {

    private static final String PROFILE_FILENAME = "/data/system/profiles.xml";

    private static final String TAG = "ProfileService";

    private Map<String, Profile> mProfiles = new HashMap<String, Profile>();

    private Map<String, NotificationGroup> mGroups = new HashMap<String, NotificationGroup>();

    private String mActiveProfile;

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

    // TODO: Could do with returning true/false to convert to exception
    // TODO: Exceptions not supported in aidl.
    @Override
    public void setActiveProfile(String profileName) throws RemoteException {
        if(mProfiles.containsKey(profileName)){
            Log.d(TAG, "Set active profile to: " + profileName);
            mActiveProfile = profileName;
        }else{
            Log.e(TAG, "Cannot set active profile to: " + profileName + " - does not exist.");
        }
    }

    @Override
    public void addProfile(Profile profile) throws RemoteException {
        // Make sure this profile has all of the correct groups.
        for (NotificationGroup group : mGroups.values()) {
            profile.ensureProfleGroup(group.getName());
        }
        profile.ensureProfleGroup(
                mContext.getString(com.android.internal.R.string.wildcardProfile), true);
        mProfiles.put(profile.getName(), profile);
    }

    @Override
    public Profile getProfile(String profileName) throws RemoteException {
        return mProfiles.get(profileName);
    }

    @Override
    public Profile[] getProfiles() throws RemoteException {
        return mProfiles.values().toArray(new Profile[mProfiles.size()]);
    }

    @Override
    public Profile getActiveProfile() throws RemoteException {
        return mProfiles.get(mActiveProfile);
    }

    @Override
    public void removeProfile(Profile profile) throws RemoteException {
        mProfiles.remove(profile.getName());
    }

    @Override
    public NotificationGroup[] getNotificationGroups() throws RemoteException {
        return mGroups.values().toArray(new NotificationGroup[mGroups.size()]);
    }

    @Override
    public void addNotificationGroup(NotificationGroup group) throws RemoteException {
        if (mGroups.put(group.getName(), group) == null) {
            // If the above is true, then the ProfileGroup shouldn't exist in
            // the profile. Ensure it is added.
            for (Profile profile : mProfiles.values()) {
                profile.ensureProfleGroup(group.getName());
            }
        }
    }

    @Override
    public void removeNotificationGroup(NotificationGroup group) throws RemoteException {
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
        loadXml(xpp);
    }

    private void loadXml(XmlPullParser xpp) throws XmlPullParserException, IOException,
            RemoteException {
        loadXml(xpp, null);
    }

    private void loadXml(XmlPullParser xpp, Context context) throws XmlPullParserException, IOException,
            RemoteException {
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
                        active = prof.getName();
                    }
                } else if (name.equals("notificationGroup")) {
                    NotificationGroup ng = NotificationGroup.fromXml(xpp, context);
                    addNotificationGroup(ng);
                }
            }
            event = xpp.next();
        }
        setActiveProfile(active);
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
        getXmlString(builder);
        return builder.toString();
    }

    private void getXmlString(StringBuilder builder) throws RemoteException {
        builder.append("<profiles>\n<active>" + TextUtils.htmlEncode(getActiveProfile().getName())
                + "</active>\n");
        for (Profile p : mProfiles.values()) {
            p.getXmlString(builder);
        }
        for (NotificationGroup g : mGroups.values()) {
            g.getXmlString(builder);
        }
        builder.append("</profiles>\n");
    }

    @Override
    public void persist() throws RemoteException {
        try {
            FileWriter fw = new FileWriter(PROFILE_FILENAME);
            fw.write(getXmlString());
            fw.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public NotificationGroup getNotificationGroup(String name) throws RemoteException {
        return mGroups.get(name);
    }

}
