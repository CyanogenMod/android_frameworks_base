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

package com.android.server.statusbar;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;

import cyanogenmod.app.CustomTile;
import cyanogenmod.app.CustomTileListenerService;
import cyanogenmod.app.ICustomTileListener;

import com.android.internal.statusbar.IStatusBar;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
import com.android.server.LocalServices;
import com.android.server.notification.ManagedServices;
import com.android.server.notification.NotificationDelegate;
import com.android.server.wm.WindowManagerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.internal.R;

import org.cyanogenmod.internal.statusbar.IStatusBarCustomTileHolder;
import org.cyanogenmod.internal.statusbar.StatusBarPanelCustomTile;
import org.cyanogenmod.internal.statusbar.ExternalQuickSettingsRecord;


/**
 * A note on locking:  We rely on the fact that calls onto mBar are oneway or
 * if they are local, that they just enqueue messages to not deadlock.
 */
public class StatusBarManagerService extends IStatusBarService.Stub {
    private static final String TAG = "StatusBarManagerService";

    private static final boolean SPEW = false;

    static final int MAX_PACKAGE_TILES = 4;

    private final Context mContext;
    private final WindowManagerService mWindowManager;
    private Handler mHandler = new Handler();
    private NotificationDelegate mNotificationDelegate;
    private CustomTileListeners mCustomTileListeners;
    private volatile IStatusBar mBar;
    private StatusBarIconList mIcons = new StatusBarIconList();
    final ArrayList<ExternalQuickSettingsRecord> mQSTileList =
            new ArrayList<ExternalQuickSettingsRecord>();
    final ArrayMap<String, ExternalQuickSettingsRecord> mCustomTileByKey =
            new ArrayMap<String, ExternalQuickSettingsRecord>();
    private final ManagedServices.UserProfiles mUserProfiles = new ManagedServices.UserProfiles();

    // for disabling the status bar
    private final ArrayList<DisableRecord> mDisableRecords = new ArrayList<DisableRecord>();
    private IBinder mSysUiVisToken = new Binder();
    private int mDisabled = 0;

    private Object mLock = new Object();
    // encompasses lights-out mode and other flags defined on View
    private int mSystemUiVisibility = 0;
    private boolean mMenuVisible = false;
    private int mImeWindowVis = 0;
    private int mImeBackDisposition;
    private boolean mShowImeSwitcher;
    private IBinder mImeToken = null;
    private int mCurrentUserId;

    private class DisableRecord implements IBinder.DeathRecipient {
        int userId;
        String pkg;
        int what;
        IBinder token;

        public void binderDied() {
            Slog.i(TAG, "binder died for pkg=" + pkg);
            disableInternal(userId, 0, token, pkg);
            token.unlinkToDeath(this, 0);
        }
    }

    /**
     * Construct the service, add the status bar view to the window manager
     */
    public StatusBarManagerService(Context context, WindowManagerService windowManager) {
        mContext = context;
        mWindowManager = windowManager;

        mCustomTileListeners = new CustomTileListeners();
        final Resources res = context.getResources();
        mIcons.defineSlots(res.getStringArray(com.android.internal.R.array.config_statusBarIcons));

        LocalServices.addService(StatusBarManagerInternal.class, mInternalService);
    }

    /**
     * Private API used by NotificationManagerService.
     */
    private final StatusBarManagerInternal mInternalService = new StatusBarManagerInternal() {
        private boolean mNotificationLightOn;

        @Override
        public void setNotificationDelegate(NotificationDelegate delegate) {
            mNotificationDelegate = delegate;
        }

        @Override
        public void buzzBeepBlinked() {
            if (mBar != null) {
                try {
                    mBar.buzzBeepBlinked();
                } catch (RemoteException ex) {
                }
            }
        }

        @Override
        public void notificationLightPulse(int argb, int onMillis, int offMillis) {
            mNotificationLightOn = true;
            if (mBar != null) {
                try {
                    mBar.notificationLightPulse(argb, onMillis, offMillis);
                } catch (RemoteException ex) {
                }
            }
        }

        @Override
        public void notificationLightOff() {
            if (mNotificationLightOn) {
                mNotificationLightOn = false;
                if (mBar != null) {
                    try {
                        mBar.notificationLightOff();
                    } catch (RemoteException ex) {
                    }
                }
            }
        }

        @Override
        public void showScreenPinningRequest() {
            if (mBar != null) {
                try {
                    mBar.showScreenPinningRequest();
                } catch (RemoteException e) {
                }
            }
        }
    };

    // ================================================================================
    // From IStatusBarService
    // ================================================================================
    @Override
    public void expandNotificationsPanel() {
        enforceExpandStatusBar();

        if (mBar != null) {
            try {
                mBar.animateExpandNotificationsPanel();
            } catch (RemoteException ex) {
            }
        }
    }

    @Override
    public void collapsePanels() {
        enforceExpandStatusBar();

        if (mBar != null) {
            try {
                mBar.animateCollapsePanels();
            } catch (RemoteException ex) {
            }
        }
    }

    @Override
    public void expandSettingsPanel() {
        enforceExpandStatusBar();

        if (mBar != null) {
            try {
                mBar.animateExpandSettingsPanel();
            } catch (RemoteException ex) {
            }
        }
    }

    @Override
    public void disable(int what, IBinder token, String pkg) {
        disableInternal(mCurrentUserId, what, token, pkg);
    }

    private void disableInternal(int userId, int what, IBinder token, String pkg) {
        enforceStatusBar();

        synchronized (mLock) {
            disableLocked(userId, what, token, pkg);
        }
    }

    private void disableLocked(int userId, int what, IBinder token, String pkg) {
        // It's important that the the callback and the call to mBar get done
        // in the same order when multiple threads are calling this function
        // so they are paired correctly.  The messages on the handler will be
        // handled in the order they were enqueued, but will be outside the lock.
        manageDisableListLocked(userId, what, token, pkg);

        // Ensure state for the current user is applied, even if passed a non-current user.
        final int net = gatherDisableActionsLocked(mCurrentUserId);
        if (net != mDisabled) {
            mDisabled = net;
            mHandler.post(new Runnable() {
                    public void run() {
                        mNotificationDelegate.onSetDisabled(net);
                    }
                });
            if (mBar != null) {
                try {
                    mBar.disable(net);
                } catch (RemoteException ex) {
                }
            }
        }
    }

    @Override
    public void setIcon(String slot, String iconPackage, int iconId, int iconLevel,
            String contentDescription) {
        enforceStatusBar();

        synchronized (mIcons) {
            int index = mIcons.getSlotIndex(slot);
            if (index < 0) {
                throw new SecurityException("invalid status bar icon slot: " + slot);
            }

            StatusBarIcon icon = new StatusBarIcon(iconPackage, UserHandle.OWNER, iconId,
                    iconLevel, 0,
                    contentDescription);
            //Slog.d(TAG, "setIcon slot=" + slot + " index=" + index + " icon=" + icon);
            mIcons.setIcon(index, icon);

            if (mBar != null) {
                try {
                    mBar.setIcon(index, icon);
                } catch (RemoteException ex) {
                }
            }
        }
    }

    @Override
    public void setIconVisibility(String slot, boolean visible) {
        enforceStatusBar();

        synchronized (mIcons) {
            int index = mIcons.getSlotIndex(slot);
            if (index < 0) {
                throw new SecurityException("invalid status bar icon slot: " + slot);
            }

            StatusBarIcon icon = mIcons.getIcon(index);
            if (icon == null) {
                return;
            }

            if (icon.visible != visible) {
                icon.visible = visible;

                if (mBar != null) {
                    try {
                        mBar.setIcon(index, icon);
                    } catch (RemoteException ex) {
                    }
                }
            }
        }
    }

    @Override
    public void removeIcon(String slot) {
        enforceStatusBar();

        synchronized (mIcons) {
            int index = mIcons.getSlotIndex(slot);
            if (index < 0) {
                throw new SecurityException("invalid status bar icon slot: " + slot);
            }

            mIcons.removeIcon(index);

            if (mBar != null) {
                try {
                    mBar.removeIcon(index);
                } catch (RemoteException ex) {
                }
            }
        }
    }

    /**
     * Register a listener binder directly with the status bar manager.
     *
     * Only works with system callers. Apps should extend
     * {@link cyanogenmod.app.CustomTileListenerService}.
     * @hide
     */
    @Override
    public void registerListener(final ICustomTileListener listener,
                                 final ComponentName component, final int userid) {
        enforceBindCustomTileListener();
        mCustomTileListeners.registerService(listener, component, userid);
    }

    /**
     * Remove a listener binder directly
     * @hide
     */
    @Override
    public void unregisterListener(ICustomTileListener listener, int userid) {
        enforceBindCustomTileListener();
        mCustomTileListeners.unregisterService(listener, userid);
    }

    /**
     * @hide
     */
    @Override
    public void createCustomTileWithTag(String pkg, String opPkg, String tag, int id,
            CustomTile customTile, int[] idOut, int userId) throws RemoteException {
        enforceCustomTilePublish();
        createCustomTileWithTagInternal(pkg, opPkg, Binder.getCallingUid(),
                Binder.getCallingPid(), tag, id, customTile, idOut, userId);
    }

    void createCustomTileWithTagInternal(final String pkg, final String opPkg, final int callingUid,
            final int callingPid, final String tag, final int id, final CustomTile customTile,
            final int[] idOut, final int incomingUserId) {

        if (pkg == null || customTile == null) {
            throw new IllegalArgumentException("null not allowed: pkg=" + pkg
                    + " id=" + id + " customTile=" + customTile);
        }

        final int userId = ActivityManager.handleIncomingUser(callingPid,
                callingUid, incomingUserId, true, false, "createCustomTileWithTag", pkg);
        final UserHandle user = new UserHandle(userId);

        // remove custom tile call ends up in not removing the custom tile.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final StatusBarPanelCustomTile sbc = new StatusBarPanelCustomTile(
                        pkg, opPkg, id, tag, callingUid, callingPid, customTile,
                        user);
                ExternalQuickSettingsRecord r = new ExternalQuickSettingsRecord(sbc);
                ExternalQuickSettingsRecord old = mCustomTileByKey.get(sbc.getKey());

                int index = indexOfQsTileLocked(sbc.getKey());
                if (index < 0) {
                    // If this tile unknown to us, check DOS protection
                    if (checkDosProtection(pkg, callingUid, userId)) return;
                    mQSTileList.add(r);
                } else {
                    old = mQSTileList.get(index);
                    mQSTileList.set(index, r);
                    r.isUpdate = true;
                }

                mCustomTileByKey.put(sbc.getKey(), r);

                if (customTile.icon != 0) {
                    StatusBarPanelCustomTile oldSbn = (old != null) ? old.sbTile : null;
                    mCustomTileListeners.notifyPostedLocked(sbc, oldSbn);
                } else {
                    Slog.e(TAG, "Not posting custom tile with icon==0: " + customTile);
                    if (old != null && !old.isCanceled) {
                        mCustomTileListeners.notifyRemovedLocked(sbc);
                    }
                }
            }
        });
        idOut[0] = id;
    }

    private boolean checkDosProtection(String pkg, int callingUid, int userId) {
        final boolean isSystemTile = isUidSystem(callingUid) || ("android".equals(pkg));
        // Limit the number of Custom tiles that any given package except the android
        // package or a registered listener can enqueue.  Prevents DOS attacks and deals with leaks.
        if (!isSystemTile) {
            synchronized (mQSTileList) {
                int count = 0;
                final int N = mQSTileList.size();

                for (int i = 0; i < N; i++) {
                    final ExternalQuickSettingsRecord r = mQSTileList.get(i);
                    if (r.sbTile.getPackage().equals(pkg) && r.sbTile.getUserId() == userId) {
                        count++;
                        if (count >= MAX_PACKAGE_TILES) {
                            Slog.e(TAG, "Package has already posted " + count
                                    + " custom tiles.  Not showing more.  package=" + pkg);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // lock on mQSTileList
    int indexOfQsTileLocked(String key) {
        final int N = mQSTileList.size();
        for (int i = 0; i < N; i++) {
            if (key.equals(mQSTileList.get(i).getKey())) {
                return i;
            }
        }
        return -1;
    }

    // lock on mQSTileList
    int indexOfQsTileLocked(String pkg, String tag, int id, int userId) {
        ArrayList<ExternalQuickSettingsRecord> list = mQSTileList;
        final int len = list.size();
        for (int i = 0; i < len; i++) {
            ExternalQuickSettingsRecord r = list.get(i);
            if (!customTileMatchesUserId(r, userId) || r.sbTile.getId() != id) {
                continue;
            }
            if (tag == null) {
                if (r.sbTile.getTag() != null) {
                    continue;
                }
            } else {
                if (!tag.equals(r.sbTile.getTag())) {
                    continue;
                }
            }
            if (r.sbTile.getPackage().equals(pkg)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Determine whether the userId applies to the custom tile in question, either because
     * they match exactly, or one of them is USER_ALL (which is treated as a wildcard).
     */
    private boolean customTileMatchesUserId(ExternalQuickSettingsRecord r, int userId) {
        return
                // looking for USER_ALL custom tile? match everything
                userId == UserHandle.USER_ALL
                        // a custom tile sent to USER_ALL matches any query
                        || r.getUserId() == UserHandle.USER_ALL
                        // an exact user match
                        || r.getUserId() == userId;
    }

    /**
     * @hide
     */
    public void removeCustomTile(String pkg, int id, int userId) {
        checkCallerIsSystemOrSameApp(pkg);
        enforceCustomTilePublish();
        removeCustomTileWithTag(pkg, null, id, userId);
    }

    /**
     * @hide
     */
    @Override
    public void removeCustomTileWithTag(String pkg, String tag, int id, int userId) {
        checkCallerIsSystemOrSameApp(pkg);
        userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), userId, true, false, "cancelCustomTileWithTag", pkg);
        removeCustomTileWithTagInternal(Binder.getCallingUid(),
                Binder.getCallingPid(), pkg, tag, id, userId);
    }

    void removeCustomTileWithTagInternal(final int callingUid, final int callingPid,
            final String pkg, final String tag, final int id, final int userId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mQSTileList) {
                    int index = indexOfQsTileLocked(pkg, tag, id, userId);
                    if (index >= 0) {
                        ExternalQuickSettingsRecord r = mQSTileList.get(index);
                        mQSTileList.remove(index);
                        // status bar
                        r.isCanceled = true;
                        mCustomTileListeners.notifyRemovedLocked(r.sbTile);
                        mCustomTileByKey.remove(r.sbTile.getKey());
                    }
                }
            }
        });
    }

    private static void checkCallerIsSystemOrSameApp(String pkg) {
        if (isCallerSystem()) {
            return;
        }
        final int uid = Binder.getCallingUid();
        try {
            ApplicationInfo ai = AppGlobals.getPackageManager().getApplicationInfo(
                    pkg, 0, UserHandle.getCallingUserId());
            if (ai == null) {
                throw new SecurityException("Unknown package " + pkg);
            }
            if (!UserHandle.isSameApp(ai.uid, uid)) {
                throw new SecurityException("Calling uid " + uid + " gave package"
                        + pkg + " which is owned by uid " + ai.uid);
            }
        } catch (RemoteException re) {
            throw new SecurityException("Unknown package " + pkg + "\n" + re);
        }
    }

    private static boolean isUidSystem(int uid) {
        final int appid = UserHandle.getAppId(uid);
        return (appid == android.os.Process.SYSTEM_UID || appid == Process.PHONE_UID || uid == 0);
    }

    private static boolean isCallerSystem() {
        return isUidSystem(Binder.getCallingUid());
    }

    /**
     * Hide or show the on-screen Menu key. Only call this from the window manager, typically in
     * response to a window with {@link android.view.WindowManager.LayoutParams#needsMenuKey} set
     * to {@link android.view.WindowManager.LayoutParams#NEEDS_MENU_SET_TRUE}.
     */
    @Override
    public void topAppWindowChanged(final boolean menuVisible) {
        enforceStatusBar();

        if (SPEW) Slog.d(TAG, (menuVisible?"showing":"hiding") + " MENU key");

        synchronized(mLock) {
            mMenuVisible = menuVisible;
            mHandler.post(new Runnable() {
                    public void run() {
                        if (mBar != null) {
                            try {
                                mBar.topAppWindowChanged(menuVisible);
                            } catch (RemoteException ex) {
                            }
                        }
                    }
                });
        }
    }

    @Override
    public void setImeWindowStatus(final IBinder token, final int vis, final int backDisposition,
            final boolean showImeSwitcher) {
        enforceStatusBar();

        if (SPEW) {
            Slog.d(TAG, "swetImeWindowStatus vis=" + vis + " backDisposition=" + backDisposition);
        }

        synchronized(mLock) {
            // In case of IME change, we need to call up setImeWindowStatus() regardless of
            // mImeWindowVis because mImeWindowVis may not have been set to false when the
            // previous IME was destroyed.
            mImeWindowVis = vis;
            mImeBackDisposition = backDisposition;
            mImeToken = token;
            mShowImeSwitcher = showImeSwitcher;
            mHandler.post(new Runnable() {
                public void run() {
                    if (mBar != null) {
                        try {
                            mBar.setImeWindowStatus(token, vis, backDisposition, showImeSwitcher);
                        } catch (RemoteException ex) {
                        }
                    }
                }
            });
        }
    }

    @Override
    public void setSystemUiVisibility(int vis, int mask, String cause) {
        // also allows calls from window manager which is in this process.
        enforceStatusBarService();

        if (SPEW) Slog.d(TAG, "setSystemUiVisibility(0x" + Integer.toHexString(vis) + ")");

        synchronized (mLock) {
            updateUiVisibilityLocked(vis, mask);
            disableLocked(
                    mCurrentUserId,
                    vis & StatusBarManager.DISABLE_MASK,
                    mSysUiVisToken,
                    cause);
        }
    }

    private void updateUiVisibilityLocked(final int vis, final int mask) {
        if (mSystemUiVisibility != vis) {
            mSystemUiVisibility = vis;
            mHandler.post(new Runnable() {
                    public void run() {
                        if (mBar != null) {
                            try {
                                mBar.setSystemUiVisibility(vis, mask);
                            } catch (RemoteException ex) {
                            }
                        }
                    }
                });
        }
    }

    @Override
    public void toggleRecentApps() {
        if (mBar != null) {
            try {
                mBar.toggleRecentApps();
            } catch (RemoteException ex) {}
        }
    }

    @Override
    public void preloadRecentApps() {
        if (mBar != null) {
            try {
                mBar.preloadRecentApps();
            } catch (RemoteException ex) {}
        }
    }

    @Override
    public void cancelPreloadRecentApps() {
        if (mBar != null) {
            try {
                mBar.cancelPreloadRecentApps();
            } catch (RemoteException ex) {}
        }
    }

    @Override
    public void showRecentApps(boolean triggeredFromAltTab) {
        if (mBar != null) {
            try {
                mBar.showRecentApps(triggeredFromAltTab);
            } catch (RemoteException ex) {}
        }
    }

    @Override
    public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        if (mBar != null) {
            try {
                mBar.hideRecentApps(triggeredFromAltTab, triggeredFromHomeKey);
            } catch (RemoteException ex) {}
        }
    }

    @Override
    public void setCurrentUser(int newUserId) {
        if (SPEW) Slog.d(TAG, "Setting current user to user " + newUserId);
        mCurrentUserId = newUserId;
    }

    @Override
    public void setWindowState(int window, int state) {
        if (mBar != null) {
            try {
                mBar.setWindowState(window, state);
            } catch (RemoteException ex) {}
        }
    }

    private void enforceSystemOrSystemUI(String message) {
        if (isCallerSystem()) return;
        mContext.enforceCallingPermission(android.Manifest.permission.STATUS_BAR_SERVICE,
                message);
    }

    private void enforceStatusBar() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR,
                "StatusBarManagerService");
    }

    private void enforceExpandStatusBar() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.EXPAND_STATUS_BAR,
                "StatusBarManagerService");
    }

    private void enforceStatusBarService() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR_SERVICE,
                "StatusBarManagerService");
    }

    private void enforceCustomTilePublish() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.PUBLISH_QUICK_SETTINGS_TILE, "StatusBarManagerService");
    }

    private void enforceBindCustomTileListener() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_CUSTOM_TILE_LISTENER_SERVICE, "StatusBarManagerService");
    }

    // ================================================================================
    // Callbacks from the status bar service.
    // ================================================================================
    @Override
    public void registerStatusBar(IStatusBar bar, StatusBarIconList iconList,
            int switches[], List<IBinder> binders) {
        enforceStatusBarService();

        Slog.i(TAG, "registerStatusBar bar=" + bar);
        mBar = bar;
        synchronized (mIcons) {
            iconList.copyFrom(mIcons);
        }
        synchronized (mLock) {
            switches[0] = gatherDisableActionsLocked(mCurrentUserId);
            switches[1] = mSystemUiVisibility;
            switches[2] = mMenuVisible ? 1 : 0;
            switches[3] = mImeWindowVis;
            switches[4] = mImeBackDisposition;
            switches[5] = mShowImeSwitcher ? 1 : 0;
            binders.add(mImeToken);
        }
    }

    /**
     * @param clearNotificationEffects whether to consider notifications as "shown" and stop
     *     LED, vibration, and ringing
     */
    @Override
    public void onPanelRevealed(boolean clearNotificationEffects) {
        enforceStatusBarService();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onPanelRevealed(clearNotificationEffects);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void clearNotificationEffects() throws RemoteException {
        enforceStatusBarService();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.clearEffects();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onPanelHidden() throws RemoteException {
        enforceStatusBarService();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onPanelHidden();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationClick(String key) {
        enforceStatusBarService();
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onNotificationClick(callingUid, callingPid, key);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationActionClick(String key, int actionIndex) {
        enforceStatusBarService();
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onNotificationActionClick(callingUid, callingPid, key,
                    actionIndex);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationError(String pkg, String tag, int id,
            int uid, int initialPid, String message, int userId) {
        enforceStatusBarService();
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        long identity = Binder.clearCallingIdentity();
        try {
            // WARNING: this will call back into us to do the remove.  Don't hold any locks.
            mNotificationDelegate.onNotificationError(callingUid, callingPid,
                    pkg, tag, id, uid, initialPid, message, userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationClear(String pkg, String tag, int id, int userId) {
        enforceStatusBarService();
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onNotificationClear(callingUid, callingPid, pkg, tag, id, userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationVisibilityChanged(
            String[] newlyVisibleKeys, String[] noLongerVisibleKeys) throws RemoteException {
        enforceStatusBarService();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onNotificationVisibilityChanged(
                    newlyVisibleKeys, noLongerVisibleKeys);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onNotificationExpansionChanged(String key, boolean userAction,
            boolean expanded) throws RemoteException {
        enforceStatusBarService();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onNotificationExpansionChanged(
                    key, userAction, expanded);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void onClearAllNotifications(int userId) {
        enforceStatusBarService();
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        long identity = Binder.clearCallingIdentity();
        try {
            mNotificationDelegate.onClearAll(callingUid, callingPid, userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isVisibleToListener(StatusBarPanelCustomTile sbc,
            ManagedServices.ManagedServiceInfo listener) {
        return listener.enabledAndUserMatches(sbc.getUserId());
    }

    public class CustomTileListeners extends ManagedServices {

        private final ArraySet<ManagedServiceInfo> mLightTrimListeners = new ArraySet<>();

        public CustomTileListeners() {
            super(StatusBarManagerService.this.mContext, mHandler, mQSTileList, mUserProfiles);
        }

        @Override
        protected Config getConfig() {
            Config c = new Config();
            c.caption = "custom tile listener";
            c.serviceInterface = CustomTileListenerService.SERVICE_INTERFACE;
            //TODO: Implement this in the future
            //c.secureSettingName = Settings.Secure.ENABLED_CUSTOM_TILE_LISTENERS;
            c.bindPermission = android.Manifest.permission.BIND_CUSTOM_TILE_LISTENER_SERVICE;
            //TODO: Implement this in the future
            //c.settingsAction = Settings.ACTION_CUSTOM_TILE_LISTENER_SETTINGS;
            c.clientLabel = R.string.custom_tile_listener_binding_label;
            return c;
        }

        @Override
        protected IInterface asInterface(IBinder binder) {
            return ICustomTileListener.Stub.asInterface(binder);
        }

        @Override
        public void onServiceAdded(ManagedServiceInfo info) {
            final ICustomTileListener listener = (ICustomTileListener) info.service;
            try {
                listener.onListenerConnected();
            } catch (RemoteException e) {
                // we tried
            }
        }

        @Override
        protected void onServiceRemovedLocked(ManagedServiceInfo removed) {
            mLightTrimListeners.remove(removed);
        }


        /**
         * asynchronously notify all listeners about a new custom tile
         *
         * <p>
         * Also takes care of removing a custom tile that has been visible to a listener before,
         * but isn't anymore.
         */
        public void notifyPostedLocked(StatusBarPanelCustomTile sbc, StatusBarPanelCustomTile oldSbc) {
            // Lazily initialized snapshots of the custom tile.
            StatusBarPanelCustomTile sbcClone = null;

            for (final ManagedServiceInfo info : mServices) {
                boolean sbnVisible = isVisibleToListener(sbc, info);
                boolean oldSbnVisible = oldSbc != null ? isVisibleToListener(oldSbc, info) : false;
                // This custom tile hasn't been and still isn't visible -> ignore.
                if (!oldSbnVisible && !sbnVisible) {
                    continue;
                }

                // This custom tile became invisible -> remove the old one.
                if (oldSbnVisible && !sbnVisible) {
                    final StatusBarPanelCustomTile oldSbcClone = oldSbc.clone();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyRemoved(info, oldSbcClone);
                        }
                    });
                    continue;
                }
                sbcClone = sbc.clone();

                final StatusBarPanelCustomTile sbcToPost = sbcClone;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyPosted(info, sbcToPost);
                    }
                });
            }
        }

        /**
         * asynchronously notify all listeners about a removed custom tile
         */
        public void notifyRemovedLocked(StatusBarPanelCustomTile sbc) {
            // make a copy in case changes are made to the underlying CustomTile object
            final StatusBarPanelCustomTile sbcClone = sbc.clone();
            for (final ManagedServiceInfo info : mServices) {
                if (!isVisibleToListener(sbcClone, info)) {
                    continue;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyRemoved(info, sbcClone);
                    }
                });
            }
        }

        private void notifyPosted(final ManagedServiceInfo info,
                                  final StatusBarPanelCustomTile sbc) {
            final ICustomTileListener listener = (ICustomTileListener)info.service;
            StatusBarCustomTileHolder sbcHolder = new StatusBarCustomTileHolder(sbc);
            try {
                listener.onCustomTilePosted(sbcHolder);
            } catch (RemoteException ex) {
                Log.e(TAG, "unable to notify listener (posted): " + listener, ex);
            }
        }

        private void notifyRemoved(ManagedServiceInfo info, StatusBarPanelCustomTile sbc) {
            if (!info.enabledAndUserMatches(sbc.getUserId())) {
                return;
            }
            final ICustomTileListener listener = (ICustomTileListener) info.service;
            StatusBarCustomTileHolder sbcHolder = new StatusBarCustomTileHolder(sbc);
            try {
                listener.onCustomTileRemoved(sbcHolder);
            } catch (RemoteException ex) {
                Log.e(TAG, "unable to notify listener (removed): " + listener, ex);
            }
        }
    }

    // ================================================================================
    // Can be called from any thread
    // ================================================================================

    // lock on mDisableRecords
    void manageDisableListLocked(int userId, int what, IBinder token, String pkg) {
        if (SPEW) {
            Slog.d(TAG, "manageDisableList userId=" + userId
                    + " what=0x" + Integer.toHexString(what) + " pkg=" + pkg);
        }
        // update the list
        final int N = mDisableRecords.size();
        DisableRecord tok = null;
        int i;
        for (i=0; i<N; i++) {
            DisableRecord t = mDisableRecords.get(i);
            if (t.token == token && t.userId == userId) {
                tok = t;
                break;
            }
        }
        if (what == 0 || !token.isBinderAlive()) {
            if (tok != null) {
                mDisableRecords.remove(i);
                tok.token.unlinkToDeath(tok, 0);
            }
        } else {
            if (tok == null) {
                tok = new DisableRecord();
                tok.userId = userId;
                try {
                    token.linkToDeath(tok, 0);
                }
                catch (RemoteException ex) {
                    return; // give up
                }
                mDisableRecords.add(tok);
            }
            tok.what = what;
            tok.token = token;
            tok.pkg = pkg;
        }
    }

    // lock on mDisableRecords
    int gatherDisableActionsLocked(int userId) {
        final int N = mDisableRecords.size();
        // gather the new net flags
        int net = 0;
        for (int i=0; i<N; i++) {
            final DisableRecord rec = mDisableRecords.get(i);
            if (rec.userId == userId) {
                net |= rec.what;
            }
        }
        return net;
    }

    /**
     * Wrapper for a StatusBarPanelCustomTile object that allows transfer across a oneway
     * binder without sending large amounts of data over a oneway transaction.
     */
    private static final class StatusBarCustomTileHolder
            extends IStatusBarCustomTileHolder.Stub {
        private StatusBarPanelCustomTile mValue;

        public StatusBarCustomTileHolder(StatusBarPanelCustomTile value) {
            mValue = value;
        }

        /** Get the held value and clear it. This function should only be called once per holder */
        @Override
        public StatusBarPanelCustomTile get() {
            StatusBarPanelCustomTile value = mValue;
            mValue = null;
            return value;
        }
    }

    // ================================================================================
    // Always called from UI thread
    // ================================================================================

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump StatusBar from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        synchronized (mIcons) {
            mIcons.dump(pw);
        }

        synchronized (mLock) {
            pw.println("  mDisabled=0x" + Integer.toHexString(mDisabled));
            final int N = mDisableRecords.size();
            pw.println("  mDisableRecords.size=" + N);
            for (int i=0; i<N; i++) {
                DisableRecord tok = mDisableRecords.get(i);
                pw.println("    [" + i + "] userId=" + tok.userId
                                + " what=0x" + Integer.toHexString(tok.what)
                                + " pkg=" + tok.pkg
                                + " token=" + tok.token);
            }
        }
    }
}
