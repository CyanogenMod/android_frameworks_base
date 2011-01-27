package android.app;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

public class ProfileManager
{

    private static IProfileManager sService;

    private Context                mContext;

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

    /* package */ProfileManager(Context context, Handler handler)
    {
        mContext = context;
    }

    public void setActiveProfile(String profileName) {
        try {
            getService().setActiveProfile(profileName);
        } catch (RemoteException e) {
        }
    }

    public Profile getActiveProfile(){
        try {
            return getService().getActiveProfile();
        } catch (RemoteException e) {
        }
        return null;
    }

    public void addProfile(Profile profile){
        try {
            getService().addProfile(profile);
        } catch (RemoteException e) {
        }
    }

    public void removeProfile(Profile profile){
        try {
            getService().removeProfile(profile);
        } catch (RemoteException e) {
        }
    }

    public Profile getProfile(String profileName){
        try {
            return getService().getProfile(profileName);
        } catch (RemoteException e) {
        }
        return null;
    }

    public Profile[] getProfiles(){
        try {
            return getService().getProfiles();
        } catch (RemoteException e) {
        }
        return null;
    }
    
    public NotificationGroup[] getNotificationGroups(){
        try {
            return getService().getNotificationGroups();
        } catch (RemoteException e) {
        }
        return null;
    }
    
    public void addNotificationGroup(NotificationGroup group){
        try {
            getService().addNotificationGroup(group);
        } catch (RemoteException e) {
        }
    }
    
    public void removeNotificationGroup(NotificationGroup group){
        try {
            getService().removeNotificationGroup(group);
        } catch (RemoteException e) {
        }
    }
    
    public NotificationGroup getNotificationGroupForPackage(String pkg){
        try {
            return getService().getNotificationGroupForPackage(pkg);
        } catch (RemoteException e) {
        }
        return null;
    }
    

}
