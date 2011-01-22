package com.android.server;

import android.app.IProfileManager;
import android.app.Profile;
import android.os.RemoteException;

public class ProfileManagerService extends IProfileManager.Stub
{

    @Override
    public void setActiveProfile(String profileName) throws RemoteException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateProfile(Profile profile) throws RemoteException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public Profile getProfile(String profileName) throws RemoteException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Profile[] getProfiles() throws RemoteException
    {
        // TODO Auto-generated method stub
        return null;
    }

}
