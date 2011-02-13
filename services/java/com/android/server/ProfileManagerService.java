
package com.android.server;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.IProfileManager;
import android.app.NotificationGroup;
import android.app.Profile;
import android.content.Context;
import android.os.RemoteException;

import java.io.FileNotFoundException;
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

    private Profile mActiveProfile;

    public ProfileManagerService(Context context) {
        try {
            loadFromFile();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            initialiseStructure();
        } catch (IOException e) {
            initialiseStructure();
        }
    }

    // TODO: Could do with returning true/false to convert to exception
    // TODO: Exceptions not supported in aidl.
    @Override
    public void setActiveProfile(String profileName) throws RemoteException {
        mActiveProfile = mProfiles.get(profileName);
        // We might want to add logic here to change ring-tone volume settings
        // etc, to widen support beyond notifications.
    }

    @Override
    public void addProfile(Profile profile) throws RemoteException {
        // Make sure this profile has all of the correct groups.
        for (NotificationGroup group : mGroups.values()) {
            profile.ensureProfleGroup(group.getName());
        }
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
        return mActiveProfile;
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
            int event = xpp.next();
            String active = xpp.getAttributeValue(null, "active");
            while (event != XmlPullParser.END_TAG) {
                if (event == XmlPullParser.START_TAG) {
                    String name = xpp.getName();
                    if (name.equals("profile")) {
                        Profile prof = Profile.fromXml(xpp);
                        addProfile(prof);
                    }
                    if (name.equals("notificationGroup")) {
                        NotificationGroup ng = NotificationGroup.fromXml(xpp);
                        addNotificationGroup(ng);
                    }
                }
                event = xpp.next();
            }
            setActiveProfile(active);
    }

    private void initialiseStructure() {
        try {
            addProfile(new Profile("Home"));
            addProfile(new Profile("Work"));
            addProfile(new Profile("Night"));
            addProfile(new Profile("Silent"));
            addProfile(new Profile("Meeting"));

            NotificationGroup group = new NotificationGroup("SMS");
            group.addPackage("com.android.mms");
            addNotificationGroup(group);
            group = new NotificationGroup("Phone");
            group.addPackage("com.android.phone");
            addNotificationGroup(group);
            group = new NotificationGroup("GMail");
            group.addPackage("com.google.android.gm");
            addNotificationGroup(group);
            group = new NotificationGroup("Email");
            group.addPackage("com.android.email");
            addNotificationGroup(group);
            group = new NotificationGroup("Calendar");
            group.addPackage("com.android.calendar");
            addNotificationGroup(group);
            
            setActiveProfile("Home");

            persist();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private String getXmlString() throws RemoteException {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    private void getXmlString(StringBuilder builder) throws RemoteException {
        builder.append("<profiles active=\"" + getActiveProfile().getName() + "\">\n");
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

}
