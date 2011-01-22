package android.app;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
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
        IBinder b = ServiceManager.getService("notification");
        sService = IProfileManager.Stub.asInterface(b);
        return sService;
    }

    /* package */ProfileManager(Context context, Handler handler)
    {
        mContext = context;
    }

}
