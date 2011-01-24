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

    public void updateProfile(Profile profile){
        try {
            getService().updateProfile(profile);
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

}
