/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.os.Binder;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;
import android.view.View;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Allows an app to control the status bar.
 *
 * @hide
 */
public class StatusBarManager {

    private static String TAG = "StatusBarManager";
    private static boolean localLOGV = false;

    public static final int DISABLE_EXPAND = View.STATUS_BAR_DISABLE_EXPAND;
    public static final int DISABLE_NOTIFICATION_ICONS = View.STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
    public static final int DISABLE_NOTIFICATION_ALERTS
            = View.STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
    @Deprecated
    public static final int DISABLE_NOTIFICATION_TICKER
            = View.STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
    public static final int DISABLE_SYSTEM_INFO = View.STATUS_BAR_DISABLE_SYSTEM_INFO;
    public static final int DISABLE_HOME = View.STATUS_BAR_DISABLE_HOME;
    public static final int DISABLE_RECENT = View.STATUS_BAR_DISABLE_RECENT;
    public static final int DISABLE_BACK = View.STATUS_BAR_DISABLE_BACK;
    public static final int DISABLE_CLOCK = View.STATUS_BAR_DISABLE_CLOCK;
    public static final int DISABLE_SEARCH = View.STATUS_BAR_DISABLE_SEARCH;

    @Deprecated
    public static final int DISABLE_NAVIGATION = 
            View.STATUS_BAR_DISABLE_HOME | View.STATUS_BAR_DISABLE_RECENT;

    public static final int DISABLE_NONE = 0x00000000;

    public static final int DISABLE_MASK = DISABLE_EXPAND | DISABLE_NOTIFICATION_ICONS
            | DISABLE_NOTIFICATION_ALERTS | DISABLE_NOTIFICATION_TICKER
            | DISABLE_SYSTEM_INFO | DISABLE_RECENT | DISABLE_HOME | DISABLE_BACK | DISABLE_CLOCK
            | DISABLE_SEARCH;

    public static final int NAVIGATION_HINT_BACK_ALT      = 1 << 0;
    public static final int NAVIGATION_HINT_IME_SHOWN     = 1 << 1;

    public static final int WINDOW_STATUS_BAR = 1;
    public static final int WINDOW_NAVIGATION_BAR = 2;

    public static final int WINDOW_STATE_SHOWING = 0;
    public static final int WINDOW_STATE_HIDING = 1;
    public static final int WINDOW_STATE_HIDDEN = 2;

    private Context mContext;
    private IStatusBarService mService;
    private IBinder mToken = new Binder();

    StatusBarManager(Context context) {
        mContext = context;
    }

    private synchronized IStatusBarService getService() {
        if (mService == null) {
            mService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
            if (mService == null) {
                Slog.w("StatusBarManager", "warning: no STATUS_BAR_SERVICE");
            }
        }
        return mService;
    }

    /**
     * Disable some features in the status bar.  Pass the bitwise-or of the DISABLE_* flags.
     * To re-enable everything, pass {@link #DISABLE_NONE}.
     */
    public void disable(int what) {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.disable(what, mToken, mContext.getPackageName());
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Expand the notifications panel.
     */
    public void expandNotificationsPanel() {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.expandNotificationsPanel();
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Collapse the notifications and settings panels.
     */
    public void collapsePanels() {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.collapsePanels();
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }

    /**
     * Expand the settings panel.
     */
    public void expandSettingsPanel() {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.expandSettingsPanel();
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }

    public void setIcon(String slot, int iconId, int iconLevel, String contentDescription) {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.setIcon(slot, mContext.getPackageName(), iconId, iconLevel,
                    contentDescription);
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }

    public void removeIcon(String slot) {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.removeIcon(slot);
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }

    public void setIconVisibility(String slot, boolean visible) {
        try {
            final IStatusBarService svc = getService();
            if (svc != null) {
                svc.setIconVisibility(slot, visible);
            }
        } catch (RemoteException ex) {
            // system process is dead anyway.
            throw new RuntimeException(ex);
        }
    }

    /** @hide */
    public static String windowStateToString(int state) {
        if (state == WINDOW_STATE_HIDING) return "WINDOW_STATE_HIDING";
        if (state == WINDOW_STATE_HIDDEN) return "WINDOW_STATE_HIDDEN";
        if (state == WINDOW_STATE_SHOWING) return "WINDOW_STATE_SHOWING";
        return "WINDOW_STATE_UNKNOWN";
    }

    /**
     * Post a custom tile to be shown in the status bar panel. If a custom tile with
     * the same id has already been posted by your application and has not yet been removed, it
     * will be replaced by the updated information.
     *
     * @param id An identifier for this customTile unique within your
     *        application.
     * @param customTile A {@link CustomTile} object describing what to show the user. Must not
     *        be null.
     */
    public void createTile(int id, CustomTile customTile)
    {
        createTile(null, id, customTile);
    }

    /**
     * Post a custom tile to be shown in the status bar panel. If a custom tile with
     * the same tag and id has already been posted by your application and has not yet been
     * removed, it will be replaced by the updated information.
     *
     * @param tag A string identifier for this custom tile.  May be {@code null}.
     * @param id An identifier for this custom tile.  The pair (tag, id) must be unique
     *        within your application.
     * @param customTile A {@link CustomTile} object describing what to
     *        show the user. Must not be null.
     */
    public void createTile(String tag, int id, CustomTile customTile)
    {
        int[] idOut = new int[1];
        IStatusBarService service = getService();
        String pkg = mContext.getPackageName();
        if (localLOGV) Log.v(TAG, pkg + ": notify(" + id + ", " + customTile + ")");
        CustomTile stripped = customTile.clone();
        try {
            service.createCustomTileWithTag(pkg, mContext.getOpPackageName(), tag, id,
                    stripped, idOut, UserHandle.myUserId());
            if (id != idOut[0]) {
                Log.w(TAG, "notify: id corrupted: sent " + id + ", got back " + idOut[0]);
            }
        } catch (RemoteException e) {
        }
    }

    /**
     * @hide
     */
    public void createTileAsUser(String tag, int id, CustomTile customTile, UserHandle user)
    {
        int[] idOut = new int[1];
        IStatusBarService service = getService();
        String pkg = mContext.getPackageName();
        if (localLOGV) Log.v(TAG, pkg + ": notify(" + id + ", " + customTile + ")");
        CustomTile stripped = customTile.clone();
        try {
            service.createCustomTileWithTag(pkg, mContext.getOpPackageName(), tag, id,
                    stripped, idOut, user.getIdentifier());
            if (id != idOut[0]) {
                Log.w(TAG, "notify: id corrupted: sent " + id + ", got back " + idOut[0]);
            }
        } catch (RemoteException e) {
        }
    }

    /**
     * Remove a previously shown custom tile.
     */
    public void removeTile(int id)
    {
        removeTile(null, id);
    }

    /**
     * Remove a previously shown custom tile.
     */
    public void removeTile(String tag, int id)
    {
        IStatusBarService service = getService();
        String pkg = mContext.getPackageName();
        if (localLOGV) Log.v(TAG, pkg + ": cancel(" + id + ")");
        try {
            service.removeCustomTileWithTag(pkg, tag, id, UserHandle.myUserId());
        } catch (RemoteException e) {
        }
    }

    /**
     * @hide
     */
    public void removeTileAsUser(String tag, int id, UserHandle user)
    {
        IStatusBarService service = getService();
        String pkg = mContext.getPackageName();
        if (localLOGV) Log.v(TAG, pkg + ": cancel(" + id + ")");
        try {
            service.removeCustomTileWithTag(pkg, tag, id, user.getIdentifier());
        } catch (RemoteException e) {
        }
    }
}
