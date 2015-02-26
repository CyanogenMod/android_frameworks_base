package android.service.statusbar;

import android.app.CustomTile;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Created by Adnan on 3/2/15.
 */
public class CustomTileListenerService extends Service {
    private final String TAG = CustomTileListenerService.class.getSimpleName()
            + "[" + getClass().getSimpleName() + "]";

    public static final String SERVICE_INTERFACE
            = "android.service.statusbar.CustomTileListenerService";

    private ICustomTileListenerWrapper mWrapper = null;

    @Override
    public IBinder onBind(Intent intent) {
        if (mWrapper == null) {
            mWrapper = new ICustomTileListenerWrapper();
        }
        return mWrapper;
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
        onCustomTilePosted(sbc);
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
