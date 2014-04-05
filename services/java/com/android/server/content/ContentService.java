/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.content;

import android.Manifest;
import android.accounts.Account;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.ISyncStatusObserver;
import android.content.PeriodicSync;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncRequest;
import android.content.SyncStatusInfo;
import android.database.IContentObserver;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseIntArray;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * {@hide}
 */
public final class ContentService extends IContentService.Stub {
    private static final String TAG = "ContentService";
    private Context mContext;
    private boolean mFactoryTest;
    private final ObserverNode mRootNode = new ObserverNode("");
    private SyncManager mSyncManager = null;
    private final Object mSyncManagerLock = new Object();

    private SyncManager getSyncManager() {
        if (SystemProperties.getBoolean("config.disable_network", false)) {
            return null;
        }

        synchronized(mSyncManagerLock) {
            try {
                // Try to create the SyncManager, return null if it fails (e.g. the disk is full).
                if (mSyncManager == null) mSyncManager = new SyncManager(mContext, mFactoryTest);
            } catch (SQLiteException e) {
                Log.e(TAG, "Can't create SyncManager", e);
            }
            return mSyncManager;
        }
    }

    @Override
    protected synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.DUMP,
                "caller doesn't have the DUMP permission");

        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        long identityToken = clearCallingIdentity();
        try {
            if (mSyncManager == null) {
                pw.println("No SyncManager created!  (Disk full?)");
            } else {
                mSyncManager.dump(fd, pw);
            }
            pw.println();
            pw.println("Observer tree:");
            synchronized (mRootNode) {
                int[] counts = new int[2];
                final SparseIntArray pidCounts = new SparseIntArray();
                mRootNode.dumpLocked(fd, pw, args, "", "  ", counts, pidCounts);
                pw.println();
                ArrayList<Integer> sorted = new ArrayList<Integer>();
                for (int i=0; i<pidCounts.size(); i++) {
                    sorted.add(pidCounts.keyAt(i));
                }
                Collections.sort(sorted, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer lhs, Integer rhs) {
                        int lc = pidCounts.get(lhs);
                        int rc = pidCounts.get(rhs);
                        if (lc < rc) {
                            return 1;
                        } else if (lc > rc) {
                            return -1;
                        }
                        return 0;
                    }

                });
                for (int i=0; i<sorted.size(); i++) {
                    int pid = sorted.get(i);
                    pw.print("  pid "); pw.print(pid); pw.print(": ");
                            pw.print(pidCounts.get(pid)); pw.println(" observers");
                }
                pw.println();
                pw.print(" Total number of nodes: "); pw.println(counts[0]);
                pw.print(" Total number of observers: "); pw.println(counts[1]);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            // The content service only throws security exceptions, so let's
            // log all others.
            if (!(e instanceof SecurityException)) {
                Slog.wtf(TAG, "Content Service Crash", e);
            }
            throw e;
        }
    }

    /*package*/ ContentService(Context context, boolean factoryTest) {
        mContext = context;
        mFactoryTest = factoryTest;
    }

    public void systemReady() {
        getSyncManager();
    }

    /**
     * Register a content observer tied to a specific user's view of the provider.
     * @param userHandle the user whose view of the provider is to be observed.  May be
     *     the calling user without requiring any permission, otherwise the caller needs to
     *     hold the INTERACT_ACROSS_USERS_FULL permission.  Pseudousers USER_ALL and
     *     USER_CURRENT are properly handled; all other pseudousers are forbidden.
     */
    @Override
    public void registerContentObserver(Uri uri, boolean notifyForDescendants,
            IContentObserver observer, int userHandle) {
        if (observer == null || uri == null) {
            throw new IllegalArgumentException("You must pass a valid uri and observer");
        }

        final int callingUser = UserHandle.getCallingUserId();
        if (callingUser != userHandle) {
            mContext.enforceCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "no permission to observe other users' provider view");
        }

        if (userHandle < 0) {
            if (userHandle == UserHandle.USER_CURRENT) {
                userHandle = ActivityManager.getCurrentUser();
            } else if (userHandle != UserHandle.USER_ALL) {
                throw new InvalidParameterException("Bad user handle for registerContentObserver: "
                        + userHandle);
            }
        }

        synchronized (mRootNode) {
            mRootNode.addObserverLocked(uri, observer, notifyForDescendants, mRootNode,
                    Binder.getCallingUid(), Binder.getCallingPid(), userHandle);
        }
    }

    public void registerContentObserver(Uri uri, boolean notifyForDescendants,
            IContentObserver observer) {
        registerContentObserver(uri, notifyForDescendants, observer,
                UserHandle.getCallingUserId());
    }

    public void unregisterContentObserver(IContentObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("You must pass a valid observer");
        }
        synchronized (mRootNode) {
            mRootNode.removeObserverLocked(observer);
            if (false) Log.v(TAG, "Unregistered observer " + observer);
        }
    }

    /**
     * Notify observers of a particular user's view of the provider.
     * @param userHandle the user whose view of the provider is to be notified.  May be
     *     the calling user without requiring any permission, otherwise the caller needs to
     *     hold the INTERACT_ACROSS_USERS_FULL permission.  Pseudousers USER_ALL and
     *     USER_CURRENT are properly interpreted; no other pseudousers are allowed.
     */
    @Override
    public void notifyChange(Uri uri, IContentObserver observer,
            boolean observerWantsSelfNotifications, boolean syncToNetwork,
            int userHandle) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Notifying update of " + uri + " for user " + userHandle
                    + " from observer " + observer + ", syncToNetwork " + syncToNetwork);
        }

        // Notify for any user other than the caller's own requires permission.
        final int callingUserHandle = UserHandle.getCallingUserId();
        if (userHandle != callingUserHandle) {
            mContext.enforceCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "no permission to notify other users");
        }

        // We passed the permission check; resolve pseudouser targets as appropriate
        if (userHandle < 0) {
            if (userHandle == UserHandle.USER_CURRENT) {
                userHandle = ActivityManager.getCurrentUser();
            } else if (userHandle != UserHandle.USER_ALL) {
                throw new InvalidParameterException("Bad user handle for notifyChange: "
                        + userHandle);
            }
        }

        final int uid = Binder.getCallingUid();
        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        long identityToken = clearCallingIdentity();
        try {
            ArrayList<ObserverCall> calls = new ArrayList<ObserverCall>();
            synchronized (mRootNode) {
                mRootNode.collectObserversLocked(uri, 0, observer, observerWantsSelfNotifications,
                        userHandle, calls);
            }
            final int numCalls = calls.size();
            for (int i=0; i<numCalls; i++) {
                ObserverCall oc = calls.get(i);
                try {
                    oc.mObserver.onChange(oc.mSelfChange, uri);
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Notified " + oc.mObserver + " of " + "update at " + uri);
                    }
                } catch (RemoteException ex) {
                    synchronized (mRootNode) {
                        Log.w(TAG, "Found dead observer, removing");
                        IBinder binder = oc.mObserver.asBinder();
                        final ArrayList<ObserverNode.ObserverEntry> list
                                = oc.mNode.mObservers;
                        int numList = list.size();
                        for (int j=0; j<numList; j++) {
                            ObserverNode.ObserverEntry oe = list.get(j);
                            if (oe.observer.asBinder() == binder) {
                                list.remove(j);
                                j--;
                                numList--;
                            }
                        }
                    }
                }
            }
            if (syncToNetwork) {
                SyncManager syncManager = getSyncManager();
                if (syncManager != null) {
                    syncManager.scheduleLocalSync(null /* all accounts */, callingUserHandle, uid,
                            uri != null ? uri.getAuthority() : null);
                }
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public void notifyChange(Uri uri, IContentObserver observer,
            boolean observerWantsSelfNotifications, boolean syncToNetwork) {
        notifyChange(uri, observer, observerWantsSelfNotifications, syncToNetwork,
                UserHandle.getCallingUserId());
    }

    /**
     * Hide this class since it is not part of api,
     * but current unittest framework requires it to be public
     * @hide
     *
     */
    public static final class ObserverCall {
        final ObserverNode mNode;
        final IContentObserver mObserver;
        final boolean mSelfChange;

        ObserverCall(ObserverNode node, IContentObserver observer, boolean selfChange) {
            mNode = node;
            mObserver = observer;
            mSelfChange = selfChange;
        }
    }

    @Override
    public void requestSync(Account account, String authority, Bundle extras) {
        ContentResolver.validateSyncExtrasBundle(extras);
        int userId = UserHandle.getCallingUserId();
        int uId = Binder.getCallingUid();

        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                syncManager.scheduleSync(account, userId, uId, authority, extras,
                        0 /* no delay */, 0 /* no delay */,
                        false /* onlyThoseWithUnkownSyncableState */);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    /**
     * Request a sync with a generic {@link android.content.SyncRequest} object. This will be
     * either:
     *   periodic OR one-off sync.
     * and
     *   anonymous OR provider sync.
     * Depending on the request, we enqueue to suit in the SyncManager.
     * @param request The request object. Validation of this object is done by its builder.
     */
    @Override
    public void sync(SyncRequest request) {
        Bundle extras = request.getBundle();
        long flextime = request.getSyncFlexTime();
        long runAtTime = request.getSyncRunTime();
        int userId = UserHandle.getCallingUserId();
        int uId = Binder.getCallingUid();

        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                if (request.hasAuthority()) {
                    // Sync Adapter registered with the system - old API.
                    final  Account account = request.getAccount();
                    final String provider = request.getProvider();
                    if (request.isPeriodic()) {
                        mContext.enforceCallingOrSelfPermission(
                                Manifest.permission.WRITE_SYNC_SETTINGS,
                                "no permission to write the sync settings");
                        if (runAtTime < 60) {
                            Slog.w(TAG, "Requested poll frequency of " + runAtTime
                                    + " seconds being rounded up to 60 seconds.");
                            runAtTime = 60;
                        }
                        PeriodicSync syncToAdd =
                                new PeriodicSync(account, provider, extras, runAtTime, flextime);
                        getSyncManager().getSyncStorageEngine().addPeriodicSync(syncToAdd, userId);
                    } else {
                        long beforeRuntimeMillis = (flextime) * 1000;
                        long runtimeMillis = runAtTime * 1000;
                        syncManager.scheduleSync(
                                account, userId, uId, provider, extras,
                                beforeRuntimeMillis, runtimeMillis,
                                false /* onlyThoseWithUnknownSyncableState */);
                    }
                } else {
                    Log.w(TAG, "Unrecognised sync parameters, doing nothing.");
                }
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    /**
     * Clear all scheduled sync operations that match the uri and cancel the active sync
     * if they match the authority and account, if they are present.
     * @param account filter the pending and active syncs to cancel using this account
     * @param authority filter the pending and active syncs to cancel using this authority
     */
    @Override
    public void cancelSync(Account account, String authority) {
        if (authority != null && authority.length() == 0) {
            throw new IllegalArgumentException("Authority must be non-empty");
        }

        int userId = UserHandle.getCallingUserId();
        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                syncManager.clearScheduledSyncOperations(account, userId, authority);
                syncManager.cancelActiveSync(account, userId, authority);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    /**
     * Get information about the SyncAdapters that are known to the system.
     * @return an array of SyncAdapters that have registered with the system
     */
    @Override
    public SyncAdapterType[] getSyncAdapterTypes() {
        // This makes it so that future permission checks will be in the context of this
        // process rather than the caller's process. We will restore this before returning.
        final int userId = UserHandle.getCallingUserId();
        final long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            return syncManager.getSyncAdapterTypes(userId);
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    @Override
    public boolean getSyncAutomatically(Account account, String providerName) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_SETTINGS,
                "no permission to read the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getSyncStorageEngine().getSyncAutomatically(
                        account, userId, providerName);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return false;
    }

    @Override
    public void setSyncAutomatically(Account account, String providerName, boolean sync) {
        if (TextUtils.isEmpty(providerName)) {
            throw new IllegalArgumentException("Authority must be non-empty");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_SYNC_SETTINGS,
                "no permission to write the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                syncManager.getSyncStorageEngine().setSyncAutomatically(
                        account, userId, providerName, sync);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    /** Old API. Schedule periodic sync with default flex time. */
    @Override
    public void addPeriodicSync(Account account, String authority, Bundle extras,
            long pollFrequency) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        if (TextUtils.isEmpty(authority)) {
            throw new IllegalArgumentException("Authority must not be empty.");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_SYNC_SETTINGS,
                "no permission to write the sync settings");

        int userId = UserHandle.getCallingUserId();
        if (pollFrequency < 60) {
            Slog.w(TAG, "Requested poll frequency of " + pollFrequency
                    + " seconds being rounded up to 60 seconds.");
            pollFrequency = 60;
        }

        long identityToken = clearCallingIdentity();
        try {
            // Add default flex time to this sync.
            PeriodicSync syncToAdd =
                    new PeriodicSync(account, authority, extras,
                            pollFrequency,
                            SyncStorageEngine.calculateDefaultFlexTime(pollFrequency));
            getSyncManager().getSyncStorageEngine().addPeriodicSync(syncToAdd, userId);
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    @Override
    public void removePeriodicSync(Account account, String authority, Bundle extras) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        if (TextUtils.isEmpty(authority)) {
            throw new IllegalArgumentException("Authority must not be empty");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_SYNC_SETTINGS,
                "no permission to write the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            PeriodicSync syncToRemove = new PeriodicSync(account, authority, extras,
                    0 /* Not read for removal */, 0 /* Not read for removal */);
            getSyncManager().getSyncStorageEngine().removePeriodicSync(syncToRemove, userId);
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    /**
     * TODO: Implement.
     * @param request Sync to remove.
     */
    public void removeSync(SyncRequest request) {

    }

    @Override
    public List<PeriodicSync> getPeriodicSyncs(Account account, String providerName) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        if (TextUtils.isEmpty(providerName)) {
            throw new IllegalArgumentException("Authority must not be empty");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_SETTINGS,
                "no permission to read the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            return getSyncManager().getSyncStorageEngine().getPeriodicSyncs(
                    account, userId, providerName);
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public int getIsSyncable(Account account, String providerName) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_SETTINGS,
                "no permission to read the sync settings");
        int userId = UserHandle.getCallingUserId();

        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getIsSyncable(
                        account, userId, providerName);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return -1;
    }

    @Override
    public void setIsSyncable(Account account, String providerName, int syncable) {
        if (TextUtils.isEmpty(providerName)) {
            throw new IllegalArgumentException("Authority must not be empty");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_SYNC_SETTINGS,
                "no permission to write the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                syncManager.getSyncStorageEngine().setIsSyncable(
                        account, userId, providerName, syncable);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    @Override
    public boolean getMasterSyncAutomatically() {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_SETTINGS,
                "no permission to read the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getSyncStorageEngine().getMasterSyncAutomatically(userId);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return false;
    }

    @Override
    public void setMasterSyncAutomatically(boolean flag) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_SYNC_SETTINGS,
                "no permission to write the sync settings");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                syncManager.getSyncStorageEngine().setMasterSyncAutomatically(flag, userId);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public boolean isSyncActive(Account account, String authority) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_STATS,
                "no permission to read the sync stats");
        int userId = UserHandle.getCallingUserId();

        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getSyncStorageEngine().isSyncActive(
                        account, userId, authority);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return false;
    }

    public List<SyncInfo> getCurrentSyncs() {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_STATS,
                "no permission to read the sync stats");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            return getSyncManager().getSyncStorageEngine().getCurrentSyncsCopy(userId);
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public SyncStatusInfo getSyncStatus(Account account, String authority) {
        if (TextUtils.isEmpty(authority)) {
            throw new IllegalArgumentException("Authority must not be empty");
        }
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_STATS,
                "no permission to read the sync stats");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getSyncStorageEngine().getStatusByAccountAndAuthority(
                        account, userId, authority);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return null;
    }

    public boolean isSyncPending(Account account, String authority) {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.READ_SYNC_STATS,
                "no permission to read the sync stats");

        int userId = UserHandle.getCallingUserId();
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null) {
                return syncManager.getSyncStorageEngine().isSyncPending(account, userId, authority);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
        return false;
    }

    public void addStatusChangeListener(int mask, ISyncStatusObserver callback) {
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null && callback != null) {
                syncManager.getSyncStorageEngine().addStatusChangeListener(mask, callback);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public void removeStatusChangeListener(ISyncStatusObserver callback) {
        long identityToken = clearCallingIdentity();
        try {
            SyncManager syncManager = getSyncManager();
            if (syncManager != null && callback != null) {
                syncManager.getSyncStorageEngine().removeStatusChangeListener(callback);
            }
        } finally {
            restoreCallingIdentity(identityToken);
        }
    }

    public static ContentService main(Context context, boolean factoryTest) {
        ContentService service = new ContentService(context, factoryTest);
        ServiceManager.addService(ContentResolver.CONTENT_SERVICE_NAME, service);
        return service;
    }

    /**
     * Hide this class since it is not part of api,
     * but current unittest framework requires it to be public
     * @hide
     */
    public static final class ObserverNode {
        private class ObserverEntry implements IBinder.DeathRecipient {
            public final IContentObserver observer;
            public final int uid;
            public final int pid;
            public final boolean notifyForDescendants;
            private final int userHandle;
            private final Object observersLock;

            public ObserverEntry(IContentObserver o, boolean n, Object observersLock,
                    int _uid, int _pid, int _userHandle) {
                this.observersLock = observersLock;
                observer = o;
                uid = _uid;
                pid = _pid;
                userHandle = _userHandle;
                notifyForDescendants = n;
                try {
                    observer.asBinder().linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                }
            }

            public void binderDied() {
                synchronized (observersLock) {
                    removeObserverLocked(observer);
                }
            }

            public void dumpLocked(FileDescriptor fd, PrintWriter pw, String[] args,
                    String name, String prefix, SparseIntArray pidCounts) {
                pidCounts.put(pid, pidCounts.get(pid)+1);
                pw.print(prefix); pw.print(name); pw.print(": pid=");
                        pw.print(pid); pw.print(" uid=");
                        pw.print(uid); pw.print(" user=");
                        pw.print(userHandle); pw.print(" target=");
                        pw.println(Integer.toHexString(System.identityHashCode(
                                observer != null ? observer.asBinder() : null)));
            }
        }

        public static final int INSERT_TYPE = 0;
        public static final int UPDATE_TYPE = 1;
        public static final int DELETE_TYPE = 2;

        private String mName;
        private ArrayList<ObserverNode> mChildren = new ArrayList<ObserverNode>();
        private ArrayList<ObserverEntry> mObservers = new ArrayList<ObserverEntry>();

        public ObserverNode(String name) {
            mName = name;
        }

        public void dumpLocked(FileDescriptor fd, PrintWriter pw, String[] args,
                String name, String prefix, int[] counts, SparseIntArray pidCounts) {
            String innerName = null;
            if (mObservers.size() > 0) {
                if ("".equals(name)) {
                    innerName = mName;
                } else {
                    innerName = name + "/" + mName;
                }
                for (int i=0; i<mObservers.size(); i++) {
                    counts[1]++;
                    mObservers.get(i).dumpLocked(fd, pw, args, innerName, prefix,
                            pidCounts);
                }
            }
            if (mChildren.size() > 0) {
                if (innerName == null) {
                    if ("".equals(name)) {
                        innerName = mName;
                    } else {
                        innerName = name + "/" + mName;
                    }
                }
                for (int i=0; i<mChildren.size(); i++) {
                    counts[0]++;
                    mChildren.get(i).dumpLocked(fd, pw, args, innerName, prefix,
                            counts, pidCounts);
                }
            }
        }

        private String getUriSegment(Uri uri, int index) {
            if (uri != null) {
                if (index == 0) {
                    return uri.getAuthority();
                } else {
                    return uri.getPathSegments().get(index - 1);
                }
            } else {
                return null;
            }
        }

        private int countUriSegments(Uri uri) {
            if (uri == null) {
                return 0;
            }
            return uri.getPathSegments().size() + 1;
        }

        // Invariant:  userHandle is either a hard user number or is USER_ALL
        public void addObserverLocked(Uri uri, IContentObserver observer,
                boolean notifyForDescendants, Object observersLock,
                int uid, int pid, int userHandle) {
            addObserverLocked(uri, 0, observer, notifyForDescendants, observersLock,
                    uid, pid, userHandle);
        }

        private void addObserverLocked(Uri uri, int index, IContentObserver observer,
                boolean notifyForDescendants, Object observersLock,
                int uid, int pid, int userHandle) {
            // If this is the leaf node add the observer
            if (index == countUriSegments(uri)) {
                Iterator<ObserverEntry> iter = mObservers.iterator();
                while(iter.hasNext()) {
                    ObserverEntry next = iter.next();
                    if(next.observer.asBinder() == observer.asBinder()) {
                         Log.w(TAG, "Observer " + observer + " is already registered.");
                         return;
                    }
                }
                mObservers.add(new ObserverEntry(observer, notifyForDescendants, observersLock,
                        uid, pid, userHandle));
                if (false) Log.v(TAG, "Registered observer " + observer + " at " + uri +
                        " with notifyForDescendants " + notifyForDescendants);
                return;
            }

            // Look to see if the proper child already exists
            String segment = getUriSegment(uri, index);
            if (segment == null) {
                throw new IllegalArgumentException("Invalid Uri (" + uri + ") used for observer");
            }
            int N = mChildren.size();
            for (int i = 0; i < N; i++) {
                ObserverNode node = mChildren.get(i);
                if (node.mName.equals(segment)) {
                    node.addObserverLocked(uri, index + 1, observer, notifyForDescendants,
                            observersLock, uid, pid, userHandle);
                    return;
                }
            }

            // No child found, create one
            ObserverNode node = new ObserverNode(segment);
            mChildren.add(node);
            node.addObserverLocked(uri, index + 1, observer, notifyForDescendants,
                    observersLock, uid, pid, userHandle);
        }

        public boolean removeObserverLocked(IContentObserver observer) {
            int size = mChildren.size();
            for (int i = 0; i < size; i++) {
                boolean empty = mChildren.get(i).removeObserverLocked(observer);
                if (empty) {
                    mChildren.remove(i);
                    i--;
                    size--;
                }
            }

            IBinder observerBinder = observer.asBinder();
            size = mObservers.size();
            for (int i = 0; i < size; i++) {
                ObserverEntry entry = mObservers.get(i);
                if (entry.observer.asBinder() == observerBinder) {
                    mObservers.remove(i);
                    // We no longer need to listen for death notifications. Remove it.
                    observerBinder.unlinkToDeath(entry, 0);
                    break;
                }
            }

            if (mChildren.size() == 0 && mObservers.size() == 0) {
                return true;
            }
            return false;
        }

        private void collectMyObserversLocked(boolean leaf, IContentObserver observer,
                boolean observerWantsSelfNotifications, int targetUserHandle,
                ArrayList<ObserverCall> calls) {
            int N = mObservers.size();
            IBinder observerBinder = observer == null ? null : observer.asBinder();
            for (int i = 0; i < N; i++) {
                ObserverEntry entry = mObservers.get(i);

                // Don't notify the observer if it sent the notification and isn't interested
                // in self notifications
                boolean selfChange = (entry.observer.asBinder() == observerBinder);
                if (selfChange && !observerWantsSelfNotifications) {
                    continue;
                }

                // Does this observer match the target user?
                if (targetUserHandle == UserHandle.USER_ALL
                        || entry.userHandle == UserHandle.USER_ALL
                        || targetUserHandle == entry.userHandle) {
                    // Make sure the observer is interested in the notification
                    if (leaf || (!leaf && entry.notifyForDescendants)) {
                        calls.add(new ObserverCall(this, entry.observer, selfChange));
                    }
                }
            }
        }

        /**
         * targetUserHandle is either a hard user handle or is USER_ALL
         */
        public void collectObserversLocked(Uri uri, int index, IContentObserver observer,
                boolean observerWantsSelfNotifications, int targetUserHandle,
                ArrayList<ObserverCall> calls) {
            String segment = null;
            int segmentCount = countUriSegments(uri);
            if (index >= segmentCount) {
                // This is the leaf node, notify all observers
                collectMyObserversLocked(true, observer, observerWantsSelfNotifications,
                        targetUserHandle, calls);
            } else if (index < segmentCount){
                segment = getUriSegment(uri, index);
                // Notify any observers at this level who are interested in descendants
                collectMyObserversLocked(false, observer, observerWantsSelfNotifications,
                        targetUserHandle, calls);
            }

            int N = mChildren.size();
            for (int i = 0; i < N; i++) {
                ObserverNode node = mChildren.get(i);
                if (segment == null || node.mName.equals(segment)) {
                    // We found the child,
                    node.collectObserversLocked(uri, index + 1,
                            observer, observerWantsSelfNotifications, targetUserHandle, calls);
                    if (segment != null) {
                        break;
                    }
                }
            }
        }
    }
}
