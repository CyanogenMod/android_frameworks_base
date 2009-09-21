/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.server;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IActivityManager;
import android.app.IApplicationThread;
import android.app.IBackupAgent;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.Settings;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.EventLog;
import android.util.Log;
import android.util.SparseArray;

import android.backup.IBackupManager;
import android.backup.IRestoreObserver;
import android.backup.IRestoreSession;
import android.backup.RestoreSet;

import com.android.internal.backup.BackupConstants;
import com.android.internal.backup.LocalTransport;
import com.android.internal.backup.IBackupTransport;

import com.android.server.PackageManagerBackupAgent;
import com.android.server.PackageManagerBackupAgent.Metadata;

import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class BackupManagerService extends IBackupManager.Stub {
    private static final String TAG = "BackupManagerService";
    private static final boolean DEBUG = true;

    // How often we perform a backup pass.  Privileged external callers can
    // trigger an immediate pass.
    private static final long BACKUP_INTERVAL = AlarmManager.INTERVAL_HOUR;

    // The amount of time between the initial provisioning of the device and
    // the first backup pass.
    private static final long FIRST_BACKUP_INTERVAL = 12 * AlarmManager.INTERVAL_HOUR;

    private static final String RUN_BACKUP_ACTION = "_backup_run_";
    private static final int MSG_RUN_BACKUP = 1;
    private static final int MSG_RUN_FULL_BACKUP = 2;
    private static final int MSG_RUN_RESTORE = 3;
    private static final int MSG_RUN_CLEAR = 4;

    // Event tags -- see system/core/logcat/event-log-tags
    private static final int BACKUP_DATA_CHANGED_EVENT = 2820;
    private static final int BACKUP_START_EVENT = 2821;
    private static final int BACKUP_TRANSPORT_FAILURE_EVENT = 2822;
    private static final int BACKUP_AGENT_FAILURE_EVENT = 2823;
    private static final int BACKUP_PACKAGE_EVENT = 2824;
    private static final int BACKUP_SUCCESS_EVENT = 2825;
    private static final int BACKUP_RESET_EVENT = 2826;

    private static final int RESTORE_START_EVENT = 2830;
    private static final int RESTORE_TRANSPORT_FAILURE_EVENT = 2831;
    private static final int RESTORE_AGENT_FAILURE_EVENT = 2832;
    private static final int RESTORE_PACKAGE_EVENT = 2833;
    private static final int RESTORE_SUCCESS_EVENT = 2834;

    // Timeout interval for deciding that a bind or clear-data has taken too long
    static final long TIMEOUT_INTERVAL = 10 * 1000;

    private Context mContext;
    private PackageManager mPackageManager;
    private IActivityManager mActivityManager;
    private PowerManager mPowerManager;
    private AlarmManager mAlarmManager;

    boolean mEnabled;   // access to this is synchronized on 'this'
    boolean mProvisioned;
    PowerManager.WakeLock mWakelock;
    final BackupHandler mBackupHandler = new BackupHandler();
    PendingIntent mRunBackupIntent;
    BroadcastReceiver mRunBackupReceiver;
    IntentFilter mRunBackupFilter;
    // map UIDs to the set of backup client services within that UID's app set
    final SparseArray<HashSet<ApplicationInfo>> mBackupParticipants
        = new SparseArray<HashSet<ApplicationInfo>>();
    // set of backup services that have pending changes
    class BackupRequest {
        public ApplicationInfo appInfo;
        public boolean fullBackup;

        BackupRequest(ApplicationInfo app, boolean isFull) {
            appInfo = app;
            fullBackup = isFull;
        }

        public String toString() {
            return "BackupRequest{app=" + appInfo + " full=" + fullBackup + "}";
        }
    }
    // Backups that we haven't started yet.
    HashMap<ApplicationInfo,BackupRequest> mPendingBackups
            = new HashMap<ApplicationInfo,BackupRequest>();

    // Pseudoname that we use for the Package Manager metadata "package"
    static final String PACKAGE_MANAGER_SENTINEL = "@pm@";

    // locking around the pending-backup management
    final Object mQueueLock = new Object();

    // The thread performing the sequence of queued backups binds to each app's agent
    // in succession.  Bind notifications are asynchronously delivered through the
    // Activity Manager; use this lock object to signal when a requested binding has
    // completed.
    final Object mAgentConnectLock = new Object();
    IBackupAgent mConnectedAgent;
    volatile boolean mConnecting;

    // A similar synchronicity mechanism around clearing apps' data for restore
    final Object mClearDataLock = new Object();
    volatile boolean mClearingData;

    // Transport bookkeeping
    final HashMap<String,IBackupTransport> mTransports
            = new HashMap<String,IBackupTransport>();
    String mCurrentTransport;
    IBackupTransport mLocalTransport, mGoogleTransport;
    RestoreSession mActiveRestoreSession;

    class RestoreParams {
        public IBackupTransport transport;
        public IRestoreObserver observer;
        public long token;

        RestoreParams(IBackupTransport _transport, IRestoreObserver _obs, long _token) {
            transport = _transport;
            observer = _obs;
            token = _token;
        }
    }

    class ClearParams {
        public IBackupTransport transport;
        public PackageInfo packageInfo;

        ClearParams(IBackupTransport _transport, PackageInfo _info) {
            transport = _transport;
            packageInfo = _info;
        }
    }

    // Where we keep our journal files and other bookkeeping
    File mBaseStateDir;
    File mDataDir;
    File mJournalDir;
    File mJournal;
    RandomAccessFile mJournalStream;

    // Keep a log of all the apps we've ever backed up
    private File mEverStored;
    private RandomAccessFile mEverStoredStream;
    HashSet<String> mEverStoredApps = new HashSet<String>();


    public BackupManagerService(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mActivityManager = ActivityManagerNative.getDefault();

        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // Set up our bookkeeping
        boolean areEnabled = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.BACKUP_ENABLED, 0) != 0;
        // !!! TODO: mProvisioned needs to default to 0, not 1.
        mProvisioned = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.BACKUP_PROVISIONED, 0) != 0;
        mBaseStateDir = new File(Environment.getDataDirectory(), "backup");
        mDataDir = Environment.getDownloadCacheDirectory();

        mRunBackupReceiver = new RunBackupReceiver();
        mRunBackupFilter = new IntentFilter();
        mRunBackupFilter.addAction(RUN_BACKUP_ACTION);
        context.registerReceiver(mRunBackupReceiver, mRunBackupFilter);

        Intent backupIntent = new Intent(RUN_BACKUP_ACTION);
        // !!! TODO: restrict delivery to our receiver; the naive setClass() doesn't seem to work
        //backupIntent.setClass(context, mRunBackupReceiver.getClass());
        backupIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mRunBackupIntent = PendingIntent.getBroadcast(context, MSG_RUN_BACKUP, backupIntent, 0);

        // Set up the backup-request journaling
        mJournalDir = new File(mBaseStateDir, "pending");
        mJournalDir.mkdirs();   // creates mBaseStateDir along the way
        makeJournalLocked();    // okay because no other threads are running yet

        // Set up the various sorts of package tracking we do
        initPackageTracking();

        // Build our mapping of uid to backup client services.  This implicitly
        // schedules a backup pass on the Package Manager metadata the first
        // time anything needs to be backed up.
        synchronized (mBackupParticipants) {
            addPackageParticipantsLocked(null);
        }

        // Set up our transport options and initialize the default transport
        // TODO: Have transports register themselves somehow?
        // TODO: Don't create transports that we don't need to?
        mLocalTransport = new LocalTransport(context);  // This is actually pretty cheap
        ComponentName localName = new ComponentName(context, LocalTransport.class);
        registerTransport(localName.flattenToShortString(), mLocalTransport);

        mGoogleTransport = null;
        mCurrentTransport = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.BACKUP_TRANSPORT);
        if ("".equals(mCurrentTransport)) {
            mCurrentTransport = null;
        }
        if (DEBUG) Log.v(TAG, "Starting with transport " + mCurrentTransport);

        // Attach to the Google backup transport.  When this comes up, it will set
        // itself as the current transport because we explicitly reset mCurrentTransport
        // to null.
        Intent intent = new Intent().setComponent(new ComponentName(
                "com.google.android.backup",
                "com.google.android.backup.BackupTransportService"));
        context.bindService(intent, mGoogleConnection, Context.BIND_AUTO_CREATE);

        // Now that we know about valid backup participants, parse any
        // leftover journal files into the pending backup set
        parseLeftoverJournals();

        // Power management
        mWakelock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "backup");

        // Start the backup passes going
        setBackupEnabled(areEnabled);
    }

    private class RunBackupReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (RUN_BACKUP_ACTION.equals(intent.getAction())) {
                if (DEBUG) Log.v(TAG, "Running a backup pass");

                synchronized (mQueueLock) {
                    // acquire a wakelock and pass it to the backup thread.  it will
                    // be released once backup concludes.
                    mWakelock.acquire();

                    Message msg = mBackupHandler.obtainMessage(MSG_RUN_BACKUP);
                    mBackupHandler.sendMessage(msg);
                }
            }
        }
    }

    private void initPackageTracking() {
        if (DEBUG) Log.v(TAG, "Initializing package tracking");

        // Keep a log of what apps we've ever backed up.  Because we might have
        // rebooted in the middle of an operation that was removing something from
        // this log, we sanity-check its contents here and reconstruct it.
        mEverStored = new File(mBaseStateDir, "processed");
        File tempProcessedFile = new File(mBaseStateDir, "processed.new");
        try {
            // If there are previous contents, parse them out then start a new
            // file to continue the recordkeeping.
            if (mEverStored.exists()) {
                RandomAccessFile temp = new RandomAccessFile(tempProcessedFile, "rw");
                mEverStoredStream = new RandomAccessFile(mEverStored, "r");

                // parse its existing contents
                mEverStoredStream.seek(0);
                temp.seek(0);
                try {
                    while (true) {
                        PackageInfo info;
                        String pkg = mEverStoredStream.readUTF();
                        try {
                            info = mPackageManager.getPackageInfo(pkg, 0);
                            mEverStoredApps.add(pkg);
                            temp.writeUTF(pkg);
                            if (DEBUG) Log.v(TAG, "   + " + pkg);
                        } catch (NameNotFoundException e) {
                            // nope, this package was uninstalled; don't include it
                            if (DEBUG) Log.v(TAG, "   - " + pkg);
                        }
                    }
                } catch (EOFException e) {
                    // now we're at EOF
                }

                // Once we've rewritten the backup history log, atomically replace the
                // old one with the new one then reopen the file for continuing use.
                temp.close();
                mEverStoredStream.close();
                tempProcessedFile.renameTo(mEverStored);
            }
            // This will create the file if it doesn't exist
            mEverStoredStream = new RandomAccessFile(mEverStored, "rwd");
            mEverStoredStream.seek(mEverStoredStream.length());
        } catch (IOException e) {
            Log.e(TAG, "Unable to open known-stored file!");
            mEverStoredStream = null;
        }

        // If we were in the middle of removing something from the ever-backed-up
        // file, there might be a transient "processed.new" file still present.
        // We've reconstructed a coherent state at this point though, so we can
        // safely discard that file now.
        if (tempProcessedFile.exists()) {
            tempProcessedFile.delete();
        }

        // Register for broadcasts about package install, etc., so we can
        // update the provider list.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    private void makeJournalLocked() {
        try {
            mJournal = File.createTempFile("journal", null, mJournalDir);
            mJournalStream = new RandomAccessFile(mJournal, "rwd");
        } catch (IOException e) {
            Log.e(TAG, "Unable to write backup journals");
            mJournal = null;
            mJournalStream = null;
        }
    }

    private void parseLeftoverJournals() {
        if (mJournal != null) {
            File[] allJournals = mJournalDir.listFiles();
            for (File f : allJournals) {
                if (f.compareTo(mJournal) != 0) {
                    // This isn't the current journal, so it must be a leftover.  Read
                    // out the package names mentioned there and schedule them for
                    // backup.
                    try {
                        Log.i(TAG, "Found stale backup journal, scheduling:");
                        RandomAccessFile in = new RandomAccessFile(f, "r");
                        while (true) {
                            String packageName = in.readUTF();
                            Log.i(TAG, "    + " + packageName);
                            dataChanged(packageName);
                        }
                    } catch (EOFException e) {
                        // no more data; we're done
                    } catch (Exception e) {
                        // can't read it or other error; just skip it
                    } finally {
                        // close/delete the file
                        f.delete();
                    }
                }
            }
        }
    }

    // Reset all of our bookkeeping, in response to having been told that
    // the backend data has been wiped [due to idle expiry, for example],
    // so we must re-upload all saved settings.
    void resetBackupState(File stateFileDir) {
        synchronized (mQueueLock) {
            // Wipe the "what we've ever backed up" tracking
            try {
                // close the ever-stored journal...
                if (mEverStoredStream != null) {
                    mEverStoredStream.close();
                }
                // ... so we can delete it and start over
                mEverStored.delete();
                mEverStoredStream = new RandomAccessFile(mEverStored, "rwd");
            } catch (IOException e) {
                Log.e(TAG, "Unable to open known-stored file!");
                mEverStoredStream = null;
            }
            mEverStoredApps.clear();

            // Remove all the state files
            for (File sf : stateFileDir.listFiles()) {
                sf.delete();
            }

            // Enqueue a new backup of every participant
            int N = mBackupParticipants.size();
            for (int i=0; i<N; i++) {
                int uid = mBackupParticipants.keyAt(i);
                HashSet<ApplicationInfo> participants = mBackupParticipants.valueAt(i);
                for (ApplicationInfo app: participants) {
                    try {
                        dataChanged(app.packageName);
                    } catch (RemoteException e) {
                        // can't happen; we're in the same process
                    }
                }
            }
        }
    }

    // Add a transport to our set of available backends
    private void registerTransport(String name, IBackupTransport transport) {
        synchronized (mTransports) {
            if (DEBUG) Log.v(TAG, "Registering transport " + name + " = " + transport);
            mTransports.put(name, transport);
        }
    }

    // ----- Track installation/removal of packages -----
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Received broadcast " + intent);

            Uri uri = intent.getData();
            if (uri == null) {
                return;
            }
            String pkgName = uri.getSchemeSpecificPart();
            if (pkgName == null) {
                return;
            }

            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                synchronized (mBackupParticipants) {
                    Bundle extras = intent.getExtras();
                    if (extras != null && extras.getBoolean(Intent.EXTRA_REPLACING, false)) {
                        // The package was just upgraded
                        updatePackageParticipantsLocked(pkgName);
                    } else {
                        // The package was just added
                        addPackageParticipantsLocked(pkgName);
                    }
                }
            }
            else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                Bundle extras = intent.getExtras();
                if (extras != null && extras.getBoolean(Intent.EXTRA_REPLACING, false)) {
                    // The package is being updated.  We'll receive a PACKAGE_ADDED shortly.
                } else {
                    synchronized (mBackupParticipants) {
                        removePackageParticipantsLocked(pkgName);
                    }
                }
            }
        }
    };

    // ----- Track connection to GoogleBackupTransport service -----
    ServiceConnection mGoogleConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.v(TAG, "Connected to Google transport");
            mGoogleTransport = IBackupTransport.Stub.asInterface(service);
            registerTransport(name.flattenToShortString(), mGoogleTransport);
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.v(TAG, "Disconnected from Google transport");
            mGoogleTransport = null;
            registerTransport(name.flattenToShortString(), null);
        }
    };

    // ----- Run the actual backup process asynchronously -----

    private class BackupHandler extends Handler {
        public void handleMessage(Message msg) {

            switch (msg.what) {
            case MSG_RUN_BACKUP:
            {
                IBackupTransport transport = getTransport(mCurrentTransport);
                if (transport == null) {
                    Log.v(TAG, "Backup requested but no transport available");
                    mWakelock.release();
                    break;
                }

                // snapshot the pending-backup set and work on that
                ArrayList<BackupRequest> queue = new ArrayList<BackupRequest>();
                File oldJournal = mJournal;
                synchronized (mQueueLock) {
                    // Do we have any work to do?
                    if (mPendingBackups.size() > 0) {
                        for (BackupRequest b: mPendingBackups.values()) {
                            queue.add(b);
                        }
                        Log.v(TAG, "clearing pending backups");
                        mPendingBackups.clear();

                        // Start a new backup-queue journal file too
                        if (mJournalStream != null) {
                            try {
                                mJournalStream.close();
                            } catch (IOException e) {
                                // don't need to do anything
                            }
                            makeJournalLocked();
                        }

                        // At this point, we have started a new journal file, and the old
                        // file identity is being passed to the backup processing thread.
                        // When it completes successfully, that old journal file will be
                        // deleted.  If we crash prior to that, the old journal is parsed
                        // at next boot and the journaled requests fulfilled.
                        (new PerformBackupThread(transport, queue, oldJournal)).start();
                    } else {
                        Log.v(TAG, "Backup requested but nothing pending");
                        mWakelock.release();
                    }
                }
                break;
            }

            case MSG_RUN_FULL_BACKUP:
                break;

            case MSG_RUN_RESTORE:
            {
                RestoreParams params = (RestoreParams)msg.obj;
                Log.d(TAG, "MSG_RUN_RESTORE observer=" + params.observer);
                (new PerformRestoreThread(params.transport, params.observer,
                        params.token)).start();
                break;
            }

            case MSG_RUN_CLEAR:
            {
                ClearParams params = (ClearParams)msg.obj;
                (new PerformClearThread(params.transport, params.packageInfo)).start();
                break;
            }
            }
        }
    }

    // Add the backup agents in the given package to our set of known backup participants.
    // If 'packageName' is null, adds all backup agents in the whole system.
    void addPackageParticipantsLocked(String packageName) {
        // Look for apps that define the android:backupAgent attribute
        if (DEBUG) Log.v(TAG, "addPackageParticipantsLocked: " + packageName);
        List<PackageInfo> targetApps = allAgentPackages();
        addPackageParticipantsLockedInner(packageName, targetApps);
    }

    private void addPackageParticipantsLockedInner(String packageName,
            List<PackageInfo> targetPkgs) {
        if (DEBUG) {
            Log.v(TAG, "Adding " + targetPkgs.size() + " backup participants:");
            for (PackageInfo p : targetPkgs) {
                Log.v(TAG, "    " + p + " agent=" + p.applicationInfo.backupAgentName
                        + " uid=" + p.applicationInfo.uid
                        + " killAfterRestore="
                        + (((p.applicationInfo.flags & ApplicationInfo.FLAG_KILL_AFTER_RESTORE) != 0) ? "true" : "false")
                        + " restoreNeedsApplication="
                        + (((p.applicationInfo.flags & ApplicationInfo.FLAG_RESTORE_NEEDS_APPLICATION) != 0) ? "true" : "false")
                        );
            }
        }

        for (PackageInfo pkg : targetPkgs) {
            if (packageName == null || pkg.packageName.equals(packageName)) {
                int uid = pkg.applicationInfo.uid;
                HashSet<ApplicationInfo> set = mBackupParticipants.get(uid);
                if (set == null) {
                    set = new HashSet<ApplicationInfo>();
                    mBackupParticipants.put(uid, set);
                }
                set.add(pkg.applicationInfo);

                // If we've never seen this app before, schedule a backup for it
                if (!mEverStoredApps.contains(pkg.packageName)) {
                    if (DEBUG) Log.i(TAG, "New app " + pkg.packageName
                            + " never backed up; scheduling");
                    try {
                        dataChanged(pkg.packageName);
                    } catch (RemoteException e) {
                        // can't happen; it's a local method call
                    }
                }
            }
        }
    }

    // Remove the given package's entry from our known active set.  If
    // 'packageName' is null, *all* participating apps will be removed.
    void removePackageParticipantsLocked(String packageName) {
        if (DEBUG) Log.v(TAG, "removePackageParticipantsLocked: " + packageName);
        List<PackageInfo> allApps = null;
        if (packageName != null) {
            allApps = new ArrayList<PackageInfo>();
            try {
                int flags = PackageManager.GET_SIGNATURES;
                allApps.add(mPackageManager.getPackageInfo(packageName, flags));
            } catch (Exception e) {
                // just skip it (???)
            }
        } else {
            // all apps with agents
            allApps = allAgentPackages();
        }
        removePackageParticipantsLockedInner(packageName, allApps);
    }

    private void removePackageParticipantsLockedInner(String packageName,
            List<PackageInfo> agents) {
        if (DEBUG) {
            Log.v(TAG, "removePackageParticipantsLockedInner (" + packageName
                    + ") removing " + agents.size() + " entries");
            for (PackageInfo p : agents) {
                Log.v(TAG, "    - " + p);
            }
        }
        for (PackageInfo pkg : agents) {
            if (packageName == null || pkg.packageName.equals(packageName)) {
                int uid = pkg.applicationInfo.uid;
                HashSet<ApplicationInfo> set = mBackupParticipants.get(uid);
                if (set != null) {
                    // Find the existing entry with the same package name, and remove it.
                    // We can't just remove(app) because the instances are different.
                    for (ApplicationInfo entry: set) {
                        if (entry.packageName.equals(pkg.packageName)) {
                            set.remove(entry);
                            removeEverBackedUp(pkg.packageName);
                            break;
                        }
                    }
                    if (set.size() == 0) {
                        mBackupParticipants.delete(uid);
                    }
                }
            }
        }
    }

    // Returns the set of all applications that define an android:backupAgent attribute
    List<PackageInfo> allAgentPackages() {
        // !!! TODO: cache this and regenerate only when necessary
        int flags = PackageManager.GET_SIGNATURES;
        List<PackageInfo> packages = mPackageManager.getInstalledPackages(flags);
        int N = packages.size();
        for (int a = N-1; a >= 0; a--) {
            PackageInfo pkg = packages.get(a);
            try {
                ApplicationInfo app = pkg.applicationInfo;
                if (((app.flags&ApplicationInfo.FLAG_ALLOW_BACKUP) == 0)
                        || app.backupAgentName == null
                        || (mPackageManager.checkPermission(android.Manifest.permission.BACKUP_DATA,
                                pkg.packageName) != PackageManager.PERMISSION_GRANTED)) {
                    packages.remove(a);
                }
                else {
                    // we will need the shared library path, so look that up and store it here
                    app = mPackageManager.getApplicationInfo(pkg.packageName,
                            PackageManager.GET_SHARED_LIBRARY_FILES);
                    pkg.applicationInfo.sharedLibraryFiles = app.sharedLibraryFiles;
                }
            } catch (NameNotFoundException e) {
                packages.remove(a);
            }
        }
        return packages;
    }

    // Reset the given package's known backup participants.  Unlike add/remove, the update
    // action cannot be passed a null package name.
    void updatePackageParticipantsLocked(String packageName) {
        if (packageName == null) {
            Log.e(TAG, "updatePackageParticipants called with null package name");
            return;
        }
        if (DEBUG) Log.v(TAG, "updatePackageParticipantsLocked: " + packageName);

        // brute force but small code size
        List<PackageInfo> allApps = allAgentPackages();
        removePackageParticipantsLockedInner(packageName, allApps);
        addPackageParticipantsLockedInner(packageName, allApps);
    }

    // Called from the backup thread: record that the given app has been successfully
    // backed up at least once
    void logBackupComplete(String packageName) {
        if (mEverStoredStream != null && !packageName.equals(PACKAGE_MANAGER_SENTINEL)) {
            synchronized (mEverStoredApps) {
                if (mEverStoredApps.add(packageName)) {
                    try {
                        mEverStoredStream.writeUTF(packageName);
                    } catch (IOException e) {
                        Log.e(TAG, "Unable to log backup of " + packageName + ", ceasing log");
                        try {
                            mEverStoredStream.close();
                        } catch (IOException ioe) {
                            // we're dropping it; no need to handle an exception on close here
                        }
                        mEverStoredStream = null;
                    }
                }
            }
        }
    }

    // Remove our awareness of having ever backed up the given package
    void removeEverBackedUp(String packageName) {
        if (DEBUG) Log.v(TAG, "Removing backed-up knowledge of " + packageName
                + ", new set:");

        if (mEverStoredStream != null) {
            synchronized (mEverStoredApps) {
                // Rewrite the file and rename to overwrite.  If we reboot in the middle,
                // we'll recognize on initialization time that the package no longer
                // exists and fix it up then.
                File tempKnownFile = new File(mBaseStateDir, "processed.new");
                try {
                    mEverStoredStream.close();
                    RandomAccessFile known = new RandomAccessFile(tempKnownFile, "rw");
                    mEverStoredApps.remove(packageName);
                    for (String s : mEverStoredApps) {
                        known.writeUTF(s);
                        if (DEBUG) Log.v(TAG, "    " + s);
                    }
                    known.close();
                    tempKnownFile.renameTo(mEverStored);
                    mEverStoredStream = new RandomAccessFile(mEverStored, "rwd");
                } catch (IOException e) {
                    // Bad: we couldn't create the new copy.  For safety's sake we
                    // abandon the whole process and remove all what's-backed-up
                    // state entirely, meaning we'll force a backup pass for every
                    // participant on the next boot or [re]install.
                    Log.w(TAG, "Error rewriting backed-up set; halting log");
                    mEverStoredStream = null;
                    mEverStoredApps.clear();
                    tempKnownFile.delete();
                    mEverStored.delete();
                }
            }
        }
    }

    // Return the given transport
    private IBackupTransport getTransport(String transportName) {
        synchronized (mTransports) {
            IBackupTransport transport = mTransports.get(transportName);
            if (transport == null) {
                Log.w(TAG, "Requested unavailable transport: " + transportName);
            }
            return transport;
        }
    }

    // fire off a backup agent, blocking until it attaches or times out
    IBackupAgent bindToAgentSynchronous(ApplicationInfo app, int mode) {
        IBackupAgent agent = null;
        synchronized(mAgentConnectLock) {
            mConnecting = true;
            mConnectedAgent = null;
            try {
                if (mActivityManager.bindBackupAgent(app, mode)) {
                    Log.d(TAG, "awaiting agent for " + app);

                    // success; wait for the agent to arrive
                    // only wait 10 seconds for the clear data to happen
                    long timeoutMark = System.currentTimeMillis() + TIMEOUT_INTERVAL;
                    while (mConnecting && mConnectedAgent == null
                            && (System.currentTimeMillis() < timeoutMark)) {
                        try {
                            mAgentConnectLock.wait(5000);
                        } catch (InterruptedException e) {
                            // just bail
                            return null;
                        }
                    }

                    // if we timed out with no connect, abort and move on
                    if (mConnecting == true) {
                        Log.w(TAG, "Timeout waiting for agent " + app);
                        return null;
                    }
                    agent = mConnectedAgent;
                }
            } catch (RemoteException e) {
                // can't happen
            }
        }
        return agent;
    }

    // clear an application's data, blocking until the operation completes or times out
    void clearApplicationDataSynchronous(String packageName) {
        // Don't wipe packages marked allowClearUserData=false
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName, 0);
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0) {
                if (DEBUG) Log.i(TAG, "allowClearUserData=false so not wiping "
                        + packageName);
                return;
            }
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Tried to clear data for " + packageName + " but not found");
            return;
        }

        ClearDataObserver observer = new ClearDataObserver();

        synchronized(mClearDataLock) {
            mClearingData = true;
            /* This is causing some critical processes to be killed during setup.
               Temporarily revert this change until we find a better solution.
            try {
                mActivityManager.clearApplicationUserData(packageName, observer);
            } catch (RemoteException e) {
                // can't happen because the activity manager is in this process
            }
            */
            mPackageManager.clearApplicationUserData(packageName, observer);

            // only wait 10 seconds for the clear data to happen
            long timeoutMark = System.currentTimeMillis() + TIMEOUT_INTERVAL;
            while (mClearingData && (System.currentTimeMillis() < timeoutMark)) {
                try {
                    mClearDataLock.wait(5000);
                } catch (InterruptedException e) {
                    // won't happen, but still.
                    mClearingData = false;
                }
            }
        }
    }

    class ClearDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(String packageName, boolean succeeded)
                throws RemoteException {
            synchronized(mClearDataLock) {
                mClearingData = false;
                mClearDataLock.notifyAll();
            }
        }
    }

    // ----- Back up a set of applications via a worker thread -----

    class PerformBackupThread extends Thread {
        private static final String TAG = "PerformBackupThread";
        IBackupTransport mTransport;
        ArrayList<BackupRequest> mQueue;
        File mStateDir;
        File mJournal;

        public PerformBackupThread(IBackupTransport transport, ArrayList<BackupRequest> queue,
                File journal) {
            mTransport = transport;
            mQueue = queue;
            mJournal = journal;

            try {
                mStateDir = new File(mBaseStateDir, transport.transportDirName());
            } catch (RemoteException e) {
                // can't happen; the transport is local
            }
            mStateDir.mkdirs();
        }

        @Override
        public void run() {
            long startRealtime = SystemClock.elapsedRealtime();
            if (DEBUG) Log.v(TAG, "Beginning backup of " + mQueue.size() + " targets");

            // Backups run at background priority
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            try {
                EventLog.writeEvent(BACKUP_START_EVENT, mTransport.transportDirName());

                // The package manager doesn't have a proper <application> etc, but since
                // it's running here in the system process we can just set up its agent
                // directly and use a synthetic BackupRequest.  We always run this pass
                // because it's cheap and this way we guarantee that we don't get out of
                // step even if we're selecting among various transports at run time.
                PackageManagerBackupAgent pmAgent = new PackageManagerBackupAgent(
                        mPackageManager, allAgentPackages());
                BackupRequest pmRequest = new BackupRequest(new ApplicationInfo(), false);
                pmRequest.appInfo.packageName = PACKAGE_MANAGER_SENTINEL;

                // If we haven't stored anything yet, we need to do an init
                // operation along with recording the metadata blob.
                boolean needInit = (mEverStoredApps.size() == 0);
                int result = processOneBackup(pmRequest,
                        IBackupAgent.Stub.asInterface(pmAgent.onBind()),
                        mTransport, needInit);
                if (result == BackupConstants.TRANSPORT_NOT_INITIALIZED) {
                    // The backend reports that our dataset has been wiped.  We need to
                    // reset all of our bookkeeping and instead run a new backup pass for
                    // everything.
                    EventLog.writeEvent(BACKUP_RESET_EVENT, mTransport.transportDirName());
                    resetBackupState(mStateDir);
                    backupNow();
                    return;
                } else if (result != BackupConstants.TRANSPORT_OK) {
                    // Give up if we couldn't even process the metadata
                    Log.e(TAG, "Meta backup err " + result);
                    return;
                }

                // Now run all the backups in our queue
                int count = mQueue.size();
                doQueuedBackups(mTransport);

                // Finally, tear down the transport
                if (mTransport.finishBackup()) {
                    int millis = (int) (SystemClock.elapsedRealtime() - startRealtime);
                    EventLog.writeEvent(BACKUP_SUCCESS_EVENT, count, millis);
                } else {
                    EventLog.writeEvent(BACKUP_TRANSPORT_FAILURE_EVENT, "");
                    Log.e(TAG, "Transport error in finishBackup()");
                }

                if (!mJournal.delete()) {
                    Log.e(TAG, "Unable to remove backup journal file " + mJournal);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in backup thread", e);
            } finally {
                // Only once we're entirely finished do we release the wakelock
                mWakelock.release();
            }
        }

        private void doQueuedBackups(IBackupTransport transport) {
            for (BackupRequest request : mQueue) {
                Log.d(TAG, "starting agent for backup of " + request);

                // Don't run backup, even if requested, if the target app does not have
                // the requisite permission
                if (mPackageManager.checkPermission(android.Manifest.permission.BACKUP_DATA,
                        request.appInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Skipping backup of unprivileged package "
                            + request.appInfo.packageName);
                    continue;
                }

                IBackupAgent agent = null;
                int mode = (request.fullBackup)
                        ? IApplicationThread.BACKUP_MODE_FULL
                        : IApplicationThread.BACKUP_MODE_INCREMENTAL;
                try {
                    agent = bindToAgentSynchronous(request.appInfo, mode);
                    if (agent != null) {
                        processOneBackup(request, agent, transport, false);
                    }

                    // unbind even on timeout, just in case
                    mActivityManager.unbindBackupAgent(request.appInfo);
                } catch (SecurityException ex) {
                    // Try for the next one.
                    Log.d(TAG, "error in bind/backup", ex);
                } catch (RemoteException e) {
                    Log.v(TAG, "bind/backup threw");
                    e.printStackTrace();
                }
            }
        }

        int processOneBackup(BackupRequest request, IBackupAgent agent,
                IBackupTransport transport, boolean doInit) {
            final String packageName = request.appInfo.packageName;
            if (DEBUG) Log.d(TAG, "processOneBackup doBackup(" + doInit + ") on " + packageName);

            File savedStateName = new File(mStateDir, packageName);
            File backupDataName = new File(mDataDir, packageName + ".data");
            File newStateName = new File(mStateDir, packageName + ".new");

            ParcelFileDescriptor savedState = null;
            ParcelFileDescriptor backupData = null;
            ParcelFileDescriptor newState = null;

            PackageInfo packInfo;
            try {
                // Look up the package info & signatures.  This is first so that if it
                // throws an exception, there's no file setup yet that would need to
                // be unraveled.
                if (packageName.equals(PACKAGE_MANAGER_SENTINEL)) {
                    // The metadata 'package' is synthetic
                    packInfo = new PackageInfo();
                    packInfo.packageName = packageName;
                } else {
                    packInfo = mPackageManager.getPackageInfo(packageName,
                        PackageManager.GET_SIGNATURES);
                }

                // In a full backup, we pass a null ParcelFileDescriptor as
                // the saved-state "file"
                if (!request.fullBackup) {
                    savedState = ParcelFileDescriptor.open(savedStateName,
                            ParcelFileDescriptor.MODE_READ_ONLY |
                            ParcelFileDescriptor.MODE_CREATE);  // Make an empty file if necessary
                }

                backupData = ParcelFileDescriptor.open(backupDataName,
                        ParcelFileDescriptor.MODE_READ_WRITE |
                        ParcelFileDescriptor.MODE_CREATE |
                        ParcelFileDescriptor.MODE_TRUNCATE);

                newState = ParcelFileDescriptor.open(newStateName,
                        ParcelFileDescriptor.MODE_READ_WRITE |
                        ParcelFileDescriptor.MODE_CREATE |
                        ParcelFileDescriptor.MODE_TRUNCATE);

                // Run the target's backup pass
                agent.doBackup(savedState, backupData, newState);
                logBackupComplete(packageName);
                if (DEBUG) Log.v(TAG, "doBackup() success");
            } catch (Exception e) {
                Log.e(TAG, "Error backing up " + packageName, e);
                EventLog.writeEvent(BACKUP_AGENT_FAILURE_EVENT, packageName, e.toString());
                backupDataName.delete();
                newStateName.delete();
                return BackupConstants.TRANSPORT_ERROR;
            } finally {
                try { if (savedState != null) savedState.close(); } catch (IOException e) {}
                try { if (backupData != null) backupData.close(); } catch (IOException e) {}
                try { if (newState != null) newState.close(); } catch (IOException e) {}
                savedState = backupData = newState = null;
            }

            // Now propagate the newly-backed-up data to the transport
            try {
                int size = (int) backupDataName.length();
                if (size > 0) {
                    backupData = ParcelFileDescriptor.open(backupDataName,
                            ParcelFileDescriptor.MODE_READ_ONLY);

                    // TODO - We call finishBackup() for each application backed up, because
                    // we need to know now whether it succeeded or failed.  Instead, we should
                    // hold off on finishBackup() until the end, which implies holding off on
                    // renaming *all* the output state files (see below) until that happens.

                    int performOkay = transport.performBackup(packInfo, backupData, doInit);
                    if (performOkay == BackupConstants.TRANSPORT_NOT_INITIALIZED) {
                        Log.i(TAG, "Backend not initialized");
                        return performOkay;
                    }

                    if ((performOkay != 0) ||
                        !transport.finishBackup()) {
                        throw new Exception("Backup transport failed");
                    }
                } else {
                    if (DEBUG) Log.i(TAG, "no backup data written; not calling transport");
                }

                // After successful transport, delete the now-stale data
                // and juggle the files so that next time we supply the agent
                // with the new state file it just created.
                backupDataName.delete();
                newStateName.renameTo(savedStateName);
                EventLog.writeEvent(BACKUP_PACKAGE_EVENT, packageName, size);
            } catch (Exception e) {
                Log.e(TAG, "Transport error backing up " + packageName, e);
                EventLog.writeEvent(BACKUP_TRANSPORT_FAILURE_EVENT, packageName);
                return BackupConstants.TRANSPORT_ERROR;
            } finally {
                try { if (backupData != null) backupData.close(); } catch (IOException e) {}
            }

            return BackupConstants.TRANSPORT_OK;
        }
    }


    // ----- Restore handling -----

    private boolean signaturesMatch(Signature[] storedSigs, Signature[] deviceSigs) {
        // Allow unsigned apps, but not signed on one device and unsigned on the other
        // !!! TODO: is this the right policy?
        if (DEBUG) Log.v(TAG, "signaturesMatch(): stored=" + storedSigs
                + " device=" + deviceSigs);
        if ((storedSigs == null || storedSigs.length == 0)
                && (deviceSigs == null || deviceSigs.length == 0)) {
            return true;
        }
        if (storedSigs == null || deviceSigs == null) {
            return false;
        }

        // !!! TODO: this demands that every stored signature match one
        // that is present on device, and does not demand the converse.
        // Is this this right policy?
        int nStored = storedSigs.length;
        int nDevice = deviceSigs.length;

        for (int i=0; i < nStored; i++) {
            boolean match = false;
            for (int j=0; j < nDevice; j++) {
                if (storedSigs[i].equals(deviceSigs[j])) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }

    class PerformRestoreThread extends Thread {
        private IBackupTransport mTransport;
        private IRestoreObserver mObserver;
        private long mToken;
        private File mStateDir;

        class RestoreRequest {
            public PackageInfo app;
            public int storedAppVersion;

            RestoreRequest(PackageInfo _app, int _version) {
                app = _app;
                storedAppVersion = _version;
            }
        }

        PerformRestoreThread(IBackupTransport transport, IRestoreObserver observer,
                long restoreSetToken) {
            mTransport = transport;
            Log.d(TAG, "PerformRestoreThread mObserver=" + mObserver);
            mObserver = observer;
            mToken = restoreSetToken;

            try {
                mStateDir = new File(mBaseStateDir, transport.transportDirName());
            } catch (RemoteException e) {
                // can't happen; the transport is local
            }
            mStateDir.mkdirs();
        }

        @Override
        public void run() {
            long startRealtime = SystemClock.elapsedRealtime();
            if (DEBUG) Log.v(TAG, "Beginning restore process mTransport=" + mTransport
                    + " mObserver=" + mObserver + " mToken=" + Long.toHexString(mToken));
            /**
             * Restore sequence:
             *
             * 1. get the restore set description for our identity
             * 2. for each app in the restore set:
             *    2.a. if it's restorable on this device, add it to the restore queue
             * 3. for each app in the restore queue:
             *    3.a. clear the app data
             *    3.b. get the restore data for the app from the transport
             *    3.c. launch the backup agent for the app
             *    3.d. agent.doRestore() with the data from the server
             *    3.e. unbind the agent [and kill the app?]
             * 4. shut down the transport
             *
             * On errors, we try our best to recover and move on to the next
             * application, but if necessary we abort the whole operation --
             * the user is waiting, after al.
             */

            int error = -1; // assume error

            // build the set of apps to restore
            try {
                // TODO: Log this before getAvailableRestoreSets, somehow
                EventLog.writeEvent(RESTORE_START_EVENT, mTransport.transportDirName());

                // Get the list of all packages which have backup enabled.
                // (Include the Package Manager metadata pseudo-package first.)
                ArrayList<PackageInfo> restorePackages = new ArrayList<PackageInfo>();
                PackageInfo omPackage = new PackageInfo();
                omPackage.packageName = PACKAGE_MANAGER_SENTINEL;
                restorePackages.add(omPackage);

                List<PackageInfo> agentPackages = allAgentPackages();
                restorePackages.addAll(agentPackages);

                // let the observer know that we're running
                if (mObserver != null) {
                    try {
                        // !!! TODO: get an actual count from the transport after
                        // its startRestore() runs?
                        mObserver.restoreStarting(restorePackages.size());
                    } catch (RemoteException e) {
                        Log.d(TAG, "Restore observer died at restoreStarting");
                        mObserver = null;
                    }
                }

                if (!mTransport.startRestore(mToken, restorePackages.toArray(new PackageInfo[0]))) {
                    Log.e(TAG, "Error starting restore operation");
                    EventLog.writeEvent(RESTORE_TRANSPORT_FAILURE_EVENT);
                    return;
                }

                String packageName = mTransport.nextRestorePackage();
                if (packageName == null) {
                    Log.e(TAG, "Error getting first restore package");
                    EventLog.writeEvent(RESTORE_TRANSPORT_FAILURE_EVENT);
                    return;
                } else if (packageName.equals("")) {
                    Log.i(TAG, "No restore data available");
                    int millis = (int) (SystemClock.elapsedRealtime() - startRealtime);
                    EventLog.writeEvent(RESTORE_SUCCESS_EVENT, 0, millis);
                    return;
                } else if (!packageName.equals(PACKAGE_MANAGER_SENTINEL)) {
                    Log.e(TAG, "Expected restore data for \"" + PACKAGE_MANAGER_SENTINEL
                          + "\", found only \"" + packageName + "\"");
                    EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, PACKAGE_MANAGER_SENTINEL,
                            "Package manager data missing");
                    return;
                }

                // Pull the Package Manager metadata from the restore set first
                PackageManagerBackupAgent pmAgent = new PackageManagerBackupAgent(
                        mPackageManager, agentPackages);
                processOneRestore(omPackage, 0, IBackupAgent.Stub.asInterface(pmAgent.onBind()));

                // Verify that the backup set includes metadata.  If not, we can't do
                // signature/version verification etc, so we simply do not proceed with
                // the restore operation.
                if (!pmAgent.hasMetadata()) {
                    Log.e(TAG, "No restore metadata available, so not restoring settings");
                    EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, PACKAGE_MANAGER_SENTINEL,
                            "Package manager restore metadata missing");
                    return;
                }

                int count = 0;
                for (;;) {
                    packageName = mTransport.nextRestorePackage();

                    if (packageName == null) {
                        Log.e(TAG, "Error getting next restore package");
                        EventLog.writeEvent(RESTORE_TRANSPORT_FAILURE_EVENT);
                        return;
                    } else if (packageName.equals("")) {
                        break;
                    }

                    if (mObserver != null) {
                        try {
                            mObserver.onUpdate(count);
                        } catch (RemoteException e) {
                            Log.d(TAG, "Restore observer died in onUpdate");
                            mObserver = null;
                        }
                    }

                    Metadata metaInfo = pmAgent.getRestoredMetadata(packageName);
                    if (metaInfo == null) {
                        Log.e(TAG, "Missing metadata for " + packageName);
                        EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName,
                                "Package metadata missing");
                        continue;
                    }

                    PackageInfo packageInfo;
                    try {
                        int flags = PackageManager.GET_SIGNATURES;
                        packageInfo = mPackageManager.getPackageInfo(packageName, flags);
                    } catch (NameNotFoundException e) {
                        Log.e(TAG, "Invalid package restoring data", e);
                        EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName,
                                "Package missing on device");
                        continue;
                    }

                    if (metaInfo.versionCode > packageInfo.versionCode) {
                        String message = "Version " + metaInfo.versionCode
                                + " > installed version " + packageInfo.versionCode;
                        Log.w(TAG, "Package " + packageName + ": " + message);
                        EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName, message);
                        continue;
                    }

                    if (!signaturesMatch(metaInfo.signatures, packageInfo.signatures)) {
                        Log.w(TAG, "Signature mismatch restoring " + packageName);
                        EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName,
                                "Signature mismatch");
                        continue;
                    }

                    if (DEBUG) Log.v(TAG, "Package " + packageName
                            + " restore version [" + metaInfo.versionCode
                            + "] is compatible with installed version ["
                            + packageInfo.versionCode + "]");

                    // Now perform the actual restore:  first clear the app's data
                    // if appropriate
                    clearApplicationDataSynchronous(packageName);

                    // Then set up and bind the agent (with a restricted Application object
                    // unless the application says otherwise)
                    boolean useRealApp = (packageInfo.applicationInfo.flags
                            & ApplicationInfo.FLAG_RESTORE_NEEDS_APPLICATION) != 0;
                    if (DEBUG && useRealApp) {
                        Log.v(TAG, "agent requires real Application subclass for restore");
                    }
                    IBackupAgent agent = bindToAgentSynchronous(
                            packageInfo.applicationInfo,
                            (useRealApp ? IApplicationThread.BACKUP_MODE_INCREMENTAL
                                    : IApplicationThread.BACKUP_MODE_RESTORE));
                    if (agent == null) {
                        Log.w(TAG, "Can't find backup agent for " + packageName);
                        EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName,
                                "Restore agent missing");
                        continue;
                    }

                    // And then finally run the restore on this agent
                    try {
                        processOneRestore(packageInfo, metaInfo.versionCode, agent);
                        ++count;
                    } finally {
                        // unbind and tidy up even on timeout or failure, just in case
                        mActivityManager.unbindBackupAgent(packageInfo.applicationInfo);

                        // The agent was probably running with a stub Application object,
                        // which isn't a valid run mode for the main app logic.  Shut
                        // down the app so that next time it's launched, it gets the
                        // usual full initialization.
                        if ((packageInfo.applicationInfo.flags
                                & ApplicationInfo.FLAG_KILL_AFTER_RESTORE) != 0) {
                            if (DEBUG) Log.d(TAG, "Restore complete, killing host process of "
                                    + packageInfo.applicationInfo.processName);
                            mActivityManager.killApplicationProcess(
                                    packageInfo.applicationInfo.processName,
                                    packageInfo.applicationInfo.uid);
                        }
                    }
                }

                // if we get this far, report success to the observer
                error = 0;
                int millis = (int) (SystemClock.elapsedRealtime() - startRealtime);
                EventLog.writeEvent(RESTORE_SUCCESS_EVENT, count, millis);
            } catch (Exception e) {
                Log.e(TAG, "Error in restore thread", e);
            } finally {
                if (DEBUG) Log.d(TAG, "finishing restore mObserver=" + mObserver);

                try {
                    mTransport.finishRestore();
                } catch (RemoteException e) {
                    Log.e(TAG, "Error finishing restore", e);
                }

                if (mObserver != null) {
                    try {
                        mObserver.restoreFinished(error);
                    } catch (RemoteException e) {
                        Log.d(TAG, "Restore observer died at restoreFinished");
                    }
                }

                // done; we can finally release the wakelock
                mWakelock.release();
            }
        }

        // Do the guts of a restore of one application, using mTransport.getRestoreData().
        void processOneRestore(PackageInfo app, int appVersionCode, IBackupAgent agent) {
            // !!! TODO: actually run the restore through mTransport
            final String packageName = app.packageName;

            if (DEBUG) Log.d(TAG, "processOneRestore packageName=" + packageName);

            // Don't restore to unprivileged packages
            if (mPackageManager.checkPermission(android.Manifest.permission.BACKUP_DATA,
                    packageName) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Skipping restore of unprivileged package " + packageName);
            }

            // !!! TODO: get the dirs from the transport
            File backupDataName = new File(mDataDir, packageName + ".restore");
            File newStateName = new File(mStateDir, packageName + ".new");
            File savedStateName = new File(mStateDir, packageName);

            ParcelFileDescriptor backupData = null;
            ParcelFileDescriptor newState = null;

            try {
                // Run the transport's restore pass
                backupData = ParcelFileDescriptor.open(backupDataName,
                            ParcelFileDescriptor.MODE_READ_WRITE |
                            ParcelFileDescriptor.MODE_CREATE |
                            ParcelFileDescriptor.MODE_TRUNCATE);

                if (!mTransport.getRestoreData(backupData)) {
                    Log.e(TAG, "Error getting restore data for " + packageName);
                    EventLog.writeEvent(RESTORE_TRANSPORT_FAILURE_EVENT);
                    return;
                }

                // Okay, we have the data.  Now have the agent do the restore.
                backupData.close();
                backupData = ParcelFileDescriptor.open(backupDataName,
                            ParcelFileDescriptor.MODE_READ_ONLY);

                newState = ParcelFileDescriptor.open(newStateName,
                            ParcelFileDescriptor.MODE_READ_WRITE |
                            ParcelFileDescriptor.MODE_CREATE |
                            ParcelFileDescriptor.MODE_TRUNCATE);

                agent.doRestore(backupData, appVersionCode, newState);

                // if everything went okay, remember the recorded state now
                //
                // !!! TODO: the restored data should be migrated on the server
                // side into the current dataset.  In that case the new state file
                // we just created would reflect the data already extant in the
                // backend, so there'd be nothing more to do.  Until that happens,
                // however, we need to make sure that we record the data to the
                // current backend dataset.  (Yes, this means shipping the data over
                // the wire in both directions.  That's bad, but consistency comes
                // first, then efficiency.)  Once we introduce server-side data
                // migration to the newly-restored device's dataset, we will change
                // the following from a discard of the newly-written state to the
                // "correct" operation of renaming into the canonical state blob.
                newStateName.delete();                      // TODO: remove; see above comment
                //newStateName.renameTo(savedStateName);    // TODO: replace with this

                int size = (int) backupDataName.length();
                EventLog.writeEvent(RESTORE_PACKAGE_EVENT, packageName, size);
            } catch (Exception e) {
                Log.e(TAG, "Error restoring data for " + packageName, e);
                EventLog.writeEvent(RESTORE_AGENT_FAILURE_EVENT, packageName, e.toString());

                // If the agent fails restore, it might have put the app's data
                // into an incoherent state.  For consistency we wipe its data
                // again in this case before propagating the exception
                clearApplicationDataSynchronous(packageName);
            } finally {
                backupDataName.delete();
                try { if (backupData != null) backupData.close(); } catch (IOException e) {}
                try { if (newState != null) newState.close(); } catch (IOException e) {}
                backupData = newState = null;
            }
        }
    }

    class PerformClearThread extends Thread {
        IBackupTransport mTransport;
        PackageInfo mPackage;

        PerformClearThread(IBackupTransport transport, PackageInfo packageInfo) {
            mTransport = transport;
            mPackage = packageInfo;
        }

        @Override
        public void run() {
            try {
                // Clear the on-device backup state to ensure a full backup next time
                File stateDir = new File(mBaseStateDir, mTransport.transportDirName());
                File stateFile = new File(stateDir, mPackage.packageName);
                stateFile.delete();

                // Tell the transport to remove all the persistent storage for the app
                // STOPSHIP TODO - need to handle failures
                mTransport.clearBackupData(mPackage);
            } catch (RemoteException e) {
                // can't happen; the transport is local
            } finally {
                try {
                    // STOPSHIP TODO - need to handle failures
                    mTransport.finishBackup();
                } catch (RemoteException e) {
                    // can't happen; the transport is local
                }

                // Last but not least, release the cpu
                mWakelock.release();
            }
        }
    }


    // ----- IBackupManager binder interface -----

    public void dataChanged(String packageName) throws RemoteException {
        // Record that we need a backup pass for the caller.  Since multiple callers
        // may share a uid, we need to note all candidates within that uid and schedule
        // a backup pass for each of them.
        EventLog.writeEvent(BACKUP_DATA_CHANGED_EVENT, packageName);

        // If the caller does not hold the BACKUP permission, it can only request a
        // backup of its own data.
        HashSet<ApplicationInfo> targets;
        if ((mContext.checkPermission(android.Manifest.permission.BACKUP, Binder.getCallingPid(),
                Binder.getCallingUid())) == PackageManager.PERMISSION_DENIED) {
            targets = mBackupParticipants.get(Binder.getCallingUid());
        } else {
            // a caller with full permission can ask to back up any participating app
            // !!! TODO: allow backup of ANY app?
            targets = new HashSet<ApplicationInfo>();
            int N = mBackupParticipants.size();
            for (int i = 0; i < N; i++) {
                HashSet<ApplicationInfo> s = mBackupParticipants.valueAt(i);
                if (s != null) {
                    targets.addAll(s);
                }
            }
        }
        if (targets != null) {
            synchronized (mQueueLock) {
                // Note that this client has made data changes that need to be backed up
                for (ApplicationInfo app : targets) {
                    // validate the caller-supplied package name against the known set of
                    // packages associated with this uid
                    if (app.packageName.equals(packageName)) {
                        // Add the caller to the set of pending backups.  If there is
                        // one already there, then overwrite it, but no harm done.
                        BackupRequest req = new BackupRequest(app, false);
                        if (mPendingBackups.put(app, req) == null) {
                            // Journal this request in case of crash.  The put()
                            // operation returned null when this package was not already
                            // in the set; we want to avoid touching the disk redundantly.
                            writeToJournalLocked(packageName);

                            if (DEBUG) {
                                int numKeys = mPendingBackups.size();
                                Log.d(TAG, "Now awaiting backup for " + numKeys + " participants:");
                                for (BackupRequest b : mPendingBackups.values()) {
                                    Log.d(TAG, "    + " + b + " agent=" + b.appInfo.backupAgentName);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "dataChanged but no participant pkg='" + packageName + "'"
                    + " uid=" + Binder.getCallingUid());
        }
    }

    private void writeToJournalLocked(String str) {
        if (mJournalStream != null) {
            try {
                mJournalStream.writeUTF(str);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to backup journal");
                mJournalStream = null;
                mJournal = null;
            }
        }
    }

    // Clear the given package's backup data from the current transport
    public void clearBackupData(String packageName) {
        if (DEBUG) Log.v(TAG, "clearBackupData() of " + packageName);
        PackageInfo info;
        try {
            info = mPackageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            Log.d(TAG, "No such package '" + packageName + "' - not clearing backup data");
            return;
        }

        // If the caller does not hold the BACKUP permission, it can only request a
        // wipe of its own backed-up data.
        HashSet<ApplicationInfo> apps;
        if ((mContext.checkPermission(android.Manifest.permission.BACKUP, Binder.getCallingPid(),
                Binder.getCallingUid())) == PackageManager.PERMISSION_DENIED) {
            apps = mBackupParticipants.get(Binder.getCallingUid());
        } else {
            // a caller with full permission can ask to back up any participating app
            // !!! TODO: allow data-clear of ANY app?
            if (DEBUG) Log.v(TAG, "Privileged caller, allowing clear of other apps");
            apps = new HashSet<ApplicationInfo>();
            int N = mBackupParticipants.size();
            for (int i = 0; i < N; i++) {
                HashSet<ApplicationInfo> s = mBackupParticipants.valueAt(i);
                if (s != null) {
                    apps.addAll(s);
                }
            }
        }

        // now find the given package in the set of candidate apps
        for (ApplicationInfo app : apps) {
            if (app.packageName.equals(packageName)) {
                if (DEBUG) Log.v(TAG, "Found the app - running clear process");
                // found it; fire off the clear request
                synchronized (mQueueLock) {
                    long oldId = Binder.clearCallingIdentity();
                    mWakelock.acquire();
                    Message msg = mBackupHandler.obtainMessage(MSG_RUN_CLEAR,
                            new ClearParams(getTransport(mCurrentTransport), info));
                    mBackupHandler.sendMessage(msg);
                    Binder.restoreCallingIdentity(oldId);
                }
                break;
            }
        }
    }

    // Run a backup pass immediately for any applications that have declared
    // that they have pending updates.
    public void backupNow() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP, "backupNow");

        if (DEBUG) Log.v(TAG, "Scheduling immediate backup pass");
        synchronized (mQueueLock) {
            try {
                mRunBackupIntent.send();
            } catch (PendingIntent.CanceledException e) {
                // should never happen
                Log.e(TAG, "run-backup intent cancelled!");
            }
        }
    }

    // Enable/disable the backup service
    public void setBackupEnabled(boolean enable) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                "setBackupEnabled");

        boolean wasEnabled = mEnabled;
        synchronized (this) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.BACKUP_ENABLED, enable ? 1 : 0);
            mEnabled = enable;
        }

        synchronized (mQueueLock) {
            if (enable && !wasEnabled && mProvisioned) {
                // if we've just been enabled, start scheduling backup passes
                startBackupAlarmsLocked(BACKUP_INTERVAL);
            } else if (!enable) {
                // No longer enabled, so stop running backups
                mAlarmManager.cancel(mRunBackupIntent);
            }
        }
    }

    // Mark the backup service as having been provisioned
    public void setBackupProvisioned(boolean available) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                "setBackupProvisioned");

        boolean wasProvisioned = mProvisioned;
        synchronized (this) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.BACKUP_PROVISIONED, available ? 1 : 0);
            mProvisioned = available;
        }

        synchronized (mQueueLock) {
            if (available && !wasProvisioned && mEnabled) {
                // we're now good to go, so start the backup alarms
                startBackupAlarmsLocked(FIRST_BACKUP_INTERVAL);
            } else if (!available) {
                // No longer enabled, so stop running backups
                Log.w(TAG, "Backup service no longer provisioned");
                mAlarmManager.cancel(mRunBackupIntent);
            }
        }
    }

    private void startBackupAlarmsLocked(long delayBeforeFirstBackup) {
        long when = System.currentTimeMillis() + delayBeforeFirstBackup;
        mAlarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, when,
                BACKUP_INTERVAL, mRunBackupIntent);
    }

    // Report whether the backup mechanism is currently enabled
    public boolean isBackupEnabled() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP, "isBackupEnabled");
        return mEnabled;    // no need to synchronize just to read it
    }

    // Report the name of the currently active transport
    public String getCurrentTransport() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                "getCurrentTransport");
        Log.v(TAG, "... getCurrentTransport() returning " + mCurrentTransport);
        return mCurrentTransport;
    }

    // Report all known, available backup transports
    public String[] listAllTransports() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP, "listAllTransports");

        String[] list = null;
        ArrayList<String> known = new ArrayList<String>();
        for (Map.Entry<String, IBackupTransport> entry : mTransports.entrySet()) {
            if (entry.getValue() != null) {
                known.add(entry.getKey());
            }
        }

        if (known.size() > 0) {
            list = new String[known.size()];
            known.toArray(list);
        }
        return list;
    }

    // Select which transport to use for the next backup operation.  If the given
    // name is not one of the available transports, no action is taken and the method
    // returns null.
    public String selectBackupTransport(String transport) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP, "selectBackupTransport");

        synchronized (mTransports) {
            String prevTransport = null;
            if (mTransports.get(transport) != null) {
                prevTransport = mCurrentTransport;
                mCurrentTransport = transport;
                Settings.Secure.putString(mContext.getContentResolver(),
                        Settings.Secure.BACKUP_TRANSPORT, transport);
                Log.v(TAG, "selectBackupTransport() set " + mCurrentTransport
                        + " returning " + prevTransport);
            } else {
                Log.w(TAG, "Attempt to select unavailable transport " + transport);
            }
            return prevTransport;
        }
    }

    // Callback: a requested backup agent has been instantiated.  This should only
    // be called from the Activity Manager.
    public void agentConnected(String packageName, IBinder agentBinder) {
        synchronized(mAgentConnectLock) {
            if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                Log.d(TAG, "agentConnected pkg=" + packageName + " agent=" + agentBinder);
                IBackupAgent agent = IBackupAgent.Stub.asInterface(agentBinder);
                mConnectedAgent = agent;
                mConnecting = false;
            } else {
                Log.w(TAG, "Non-system process uid=" + Binder.getCallingUid()
                        + " claiming agent connected");
            }
            mAgentConnectLock.notifyAll();
        }
    }

    // Callback: a backup agent has failed to come up, or has unexpectedly quit.
    // If the agent failed to come up in the first place, the agentBinder argument
    // will be null.  This should only be called from the Activity Manager.
    public void agentDisconnected(String packageName) {
        // TODO: handle backup being interrupted
        synchronized(mAgentConnectLock) {
            if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                mConnectedAgent = null;
                mConnecting = false;
            } else {
                Log.w(TAG, "Non-system process uid=" + Binder.getCallingUid()
                        + " claiming agent disconnected");
            }
            mAgentConnectLock.notifyAll();
        }
    }

    // Hand off a restore session
    public IRestoreSession beginRestoreSession(String transport) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP, "beginRestoreSession");

        synchronized(this) {
            if (mActiveRestoreSession != null) {
                Log.d(TAG, "Restore session requested but one already active");
                return null;
            }
            mActiveRestoreSession = new RestoreSession(transport);
        }
        return mActiveRestoreSession;
    }

    // ----- Restore session -----

    class RestoreSession extends IRestoreSession.Stub {
        private static final String TAG = "RestoreSession";

        private IBackupTransport mRestoreTransport = null;
        RestoreSet[] mRestoreSets = null;

        RestoreSession(String transport) {
            mRestoreTransport = getTransport(transport);
        }

        // --- Binder interface ---
        public synchronized RestoreSet[] getAvailableRestoreSets() {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                    "getAvailableRestoreSets");

            try {
                if (mRestoreTransport == null) {
                    Log.w(TAG, "Null transport getting restore sets");
                    return null;
                }
                if (mRestoreSets == null) { // valid transport; do the one-time fetch
                    mRestoreSets = mRestoreTransport.getAvailableRestoreSets();
                    if (mRestoreSets == null) EventLog.writeEvent(RESTORE_TRANSPORT_FAILURE_EVENT);
                }
                return mRestoreSets;
            } catch (Exception e) {
                Log.e(TAG, "Error in getAvailableRestoreSets", e);
                return null;
            }
        }

        public synchronized int performRestore(long token, IRestoreObserver observer) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                    "performRestore");

            if (DEBUG) Log.d(TAG, "performRestore token=" + Long.toHexString(token)
                    + " observer=" + observer);

            if (mRestoreTransport == null || mRestoreSets == null) {
                Log.e(TAG, "Ignoring performRestore() with no restore set");
                return -1;
            }

            for (int i = 0; i < mRestoreSets.length; i++) {
                if (token == mRestoreSets[i].token) {
                    long oldId = Binder.clearCallingIdentity();
                    mWakelock.acquire();
                    Message msg = mBackupHandler.obtainMessage(MSG_RUN_RESTORE);
                    msg.obj = new RestoreParams(mRestoreTransport, observer, token);
                    mBackupHandler.sendMessage(msg);
                    Binder.restoreCallingIdentity(oldId);
                    return 0;
                }
            }

            Log.w(TAG, "Restore token " + Long.toHexString(token) + " not found");
            return -1;
        }

        public synchronized void endRestoreSession() {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.BACKUP,
                    "endRestoreSession");

            if (DEBUG) Log.d(TAG, "endRestoreSession");

            synchronized (this) {
                try {
                    if (mRestoreTransport != null) mRestoreTransport.finishRestore();
                } catch (Exception e) {
                    Log.e(TAG, "Error in finishRestore", e);
                } finally {
                    mRestoreTransport = null;
                }
            }

            synchronized (BackupManagerService.this) {
                if (BackupManagerService.this.mActiveRestoreSession == this) {
                    BackupManagerService.this.mActiveRestoreSession = null;
                } else {
                    Log.e(TAG, "ending non-current restore session");
                }
            }
        }
    }


    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mQueueLock) {
            pw.println("Backup Manager is " + (mEnabled ? "enabled" : "disabled")
                    + " / " + (!mProvisioned ? "not " : "") + "provisioned");
            pw.println("Available transports:");
            for (String t : listAllTransports()) {
                String pad = (t.equals(mCurrentTransport)) ? "  * " : "    ";
                pw.println(pad + t);
            }
            int N = mBackupParticipants.size();
            pw.println("Participants: " + N);
            for (int i=0; i<N; i++) {
                int uid = mBackupParticipants.keyAt(i);
                pw.print("  uid: ");
                pw.println(uid);
                HashSet<ApplicationInfo> participants = mBackupParticipants.valueAt(i);
                for (ApplicationInfo app: participants) {
                    pw.println("    " + app.toString());
                }
            }
            pw.println("Ever backed up: " + mEverStoredApps.size());
            for (String pkg : mEverStoredApps) {
                pw.println("    " + pkg);
            }
            pw.println("Pending: " + mPendingBackups.size());
            for (BackupRequest req : mPendingBackups.values()) {
                pw.println("    " + req);
            }
        }
    }
}
