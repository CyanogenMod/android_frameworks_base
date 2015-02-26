package android.service.statusbar;

import android.app.CustomTile;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Created by Adnan on 3/2/15.
 * @hide
 */
public class CustomTileListenerService extends Service {
    private final String TAG = CustomTileListenerService.class.getSimpleName()
            + "[" + getClass().getSimpleName() + "]";

    public static final String SERVICE_INTERFACE
            = "android.service.statusbar.CustomTileListenerService";

    private ICustomTileListenerWrapper mWrapper = null;
    private IStatusBarService mStatusBarService;
    /** Only valid after a successful call to (@link registerAsService}. */
    private int mCurrentUser;

    @Override
    public IBinder onBind(Intent intent) {
        if (mWrapper == null) {
            mWrapper = new ICustomTileListenerWrapper();
        }
        return mWrapper;
    }

    private final IStatusBarService getStatusBarInterface() {
        if (mStatusBarService == null) {
            mStatusBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        }
        return mStatusBarService;
    }

    /**
     * Directly register this service with the QSTileHost.
     *
     * <p>Only system services may use this call. It will fail for non-system callers.
     * Apps should ask the user to add their listener in Settings.
     *
     * @param context Context required for accessing resources. Since this service isn't
     *    launched as a real Service when using this method, a context has to be passed in.
     * @param componentName the component that will consume the notification information
     * @param currentUser the user to use as the stream filter
     * @hide
     */
    public void registerAsSystemService(Context context, ComponentName componentName,
                                        int currentUser) throws RemoteException {
        if (mWrapper == null) {
            mWrapper = new ICustomTileListenerWrapper();
        }
        IStatusBarService statusBarInterface = getStatusBarInterface();
        statusBarInterface.registerListener(mWrapper, componentName, currentUser);
        mCurrentUser = currentUser;
    }

    /**
     * Directly unregister this service from the Notification Manager.
     *
     * <P>This method will fail for listeners that were not registered
     * with (@link registerAsService).
     * @hide
     */
    public void unregisterAsSystemService() throws RemoteException {
        if (mWrapper != null) {
            IStatusBarService statusBarInterface = getStatusBarInterface();
            statusBarInterface.unregisterListener(mWrapper, mCurrentUser);
        }
    }


    private class ICustomTileListenerWrapper extends ICustomTileListener.Stub {
        @Override
        public void onListenerConnected() {
            synchronized (mWrapper) {
                try {
                    CustomTileListenerService.this.onListenerConnected();
                } catch (Throwable t) {
                    Log.w(TAG, "Error running onListenerConnected", t);
                }
            }
        }
        @Override
        public void onCustomTilePosted(IStatusBarCustomTileHolder sbcHolder) {
            StatusBarPanelCustomTile sbc;
            try {
                sbc = sbcHolder.get();
            } catch (RemoteException e) {
                Log.w(TAG, "onNotificationPosted: Error receiving StatusBarPanelCustomTile", e);
                return;
            }
            synchronized (mWrapper) {
                try {
                    CustomTileListenerService.this.onCustomTilePosted(sbc);
                } catch (Throwable t) {
                    Log.w(TAG, "Error running onCustomTilePosted", t);
                }
            }
        }
        @Override
        public void onCustomTileRemoved(IStatusBarCustomTileHolder sbcHolder) {
            StatusBarPanelCustomTile sbc;
            try {
                sbc = sbcHolder.get();
            } catch (RemoteException e) {
                Log.w(TAG, "onCustomTileRemoved: Error receiving StatusBarPanelCustomTile", e);
                return;
            }
            synchronized (mWrapper) {
                try {
                    CustomTileListenerService.this.onCustomTileRemoved(sbc);
                } catch (Throwable t) {
                    Log.w(TAG, "Error running onCustomTileRemoved", t);
                }
            }
        }
    }

    /**
     * Implement this method to learn about new custom tiles as they are posted by apps.
     *
     * @param sbc A data structure encapsulating the original {@link android.app.CustomTile}
     *            object as well as its identifying information (tag and id) and source
     *            (package name).
     */
    public void onCustomTilePosted(StatusBarPanelCustomTile sbc) {
        // optional
    }

    /**
     * Implement this method to learn when custom tiles are removed.
     *
     * @param sbc A data structure encapsulating at least the original information (tag and id)
     *            and source (package name) used to post the {@link android.app.CustomTile} that
     *            was just removed.
     */
    public void onCustomTileRemoved(StatusBarPanelCustomTile sbc) {
        // optional
    }

    /**
     * Implement this method to learn about when the listener is enabled and connected to
     * the status bar manager.
     * at this time.
     */
    public void onListenerConnected() {
        // optional
    }
}
