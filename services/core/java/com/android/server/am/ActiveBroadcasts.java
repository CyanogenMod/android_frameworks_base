/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.am;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.PrintWriterPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.server.IntentResolver;
import com.android.server.pm.UserManagerService;

public final class ActiveBroadcasts {
    static final String TAG = ActivityManagerService.TAG;
    static final boolean DEBUG = false;
    static final boolean localLOGV = DEBUG;
    static final boolean DEBUG_BROADCAST = localLOGV || false;
    static final boolean DEBUG_BROADCAST_LIGHT = DEBUG_BROADCAST || false;
    static final boolean DEBUG_BACKGROUND_BROADCAST = DEBUG_BROADCAST || false;

    static final boolean IS_LOW_RAM_DEVICE = ActivityManager.isLowRamDeviceStatic();
    static final int MAX_BROADCAST_HISTORY = IS_LOW_RAM_DEVICE ? 10 : 50;
    static final int MAX_BROADCAST_SUMMARY_HISTORY = IS_LOW_RAM_DEVICE ? 25 : 300;

    static final boolean ENABLE_EXTRA_QUEUES = !IS_LOW_RAM_DEVICE;
    static final int QUEUE_SIZE = ENABLE_EXTRA_QUEUES ? 5 : 2;

    static final int QUEUE_CONTROL_FLAGS = Intent.FLAG_RECEIVER_BOOTING
            | Intent.FLAG_RECEIVER_LONG_TIME | Intent.FLAG_RECEIVER_NON_SYSTEM_APP;

    private static final int UID_TYPE_INITIAL = 0;
    private static final int UID_TYPE_SYSTEM_APP = 1;
    private static final int UID_TYPE_NON_SYSTEM_APP = 2;

    /**
     * Store whether the uid is non-system app to determine which queue to run.
     */
    final SparseIntArray mAppUidTypes;

    /**
     * Historical data of past broadcasts, for debugging.
     */
    final BroadcastRecord[] mBroadcastHistory = new BroadcastRecord[MAX_BROADCAST_HISTORY];

    /**
     * Summary of historical data of past broadcasts, for debugging.
     */
    final Intent[] mBroadcastSummaryHistory = new Intent[MAX_BROADCAST_SUMMARY_HISTORY];

    /**
     * State of all active sticky broadcasts per user.  Keys are the action of the
     * sticky Intent, values are an ArrayList of all broadcasted intents with
     * that action (which should usually be one).  The SparseArray is keyed
     * by the user ID the sticky is for, and can include UserHandle.USER_ALL
     * for stickies that are sent to all users.
     */
    final SparseArray<ArrayMap<String, ArrayList<Intent>>> mStickyBroadcasts = new SparseArray<>();

    /**
     * Keeps track of all IIntentReceivers that have been registered for
     * broadcasts.  Hash keys are the receiver IBinder, hash value is
     * a ReceiverList.
     */
    final HashMap<IBinder, ReceiverList> mRegisteredReceivers = new HashMap<>();

    /**
     * Resolver for broadcast intents to registered receivers.
     * Holds BroadcastFilter (subclass of IntentFilter).
     */
    final IntentResolver<BroadcastFilter, BroadcastFilter> mReceiverResolver
            = new IntentResolver<BroadcastFilter, BroadcastFilter>() {
        @Override
        protected boolean allowFilterResult(
                BroadcastFilter filter, List<BroadcastFilter> dest) {
            IBinder target = filter.receiverList.receiver.asBinder();
            for (int i = dest.size() - 1; i >= 0; i--) {
                if (dest.get(i).receiverList.receiver.asBinder() == target) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected BroadcastFilter newResult(BroadcastFilter filter, int match, int userId) {
            if (userId == UserHandle.USER_ALL || filter.owningUserId == UserHandle.USER_ALL
                    || userId == filter.owningUserId) {
                return super.newResult(filter, match, userId);
            }
            return null;
        }

        @Override
        protected BroadcastFilter[] newArray(int size) {
            return new BroadcastFilter[size];
        }

        @Override
        protected boolean isPackageForFilter(String packageName, BroadcastFilter filter) {
            return packageName.equals(filter.packageName);
        }
    };

    private final ActivityManagerService mService;
    private final Handler mHandler;

    private final BroadcastQueue mFgBroadcastQueue;
    private final BroadcastQueue mBgBroadcastQueue;
    private final BroadcastQueue mLtBroadcastQueue;
    private final BroadcastQueue mNsBroadcastQueue;
    private BroadcastQueue mBootBroadcastQueue;

    // Convenient for easy iteration over the queues. Foreground is first
    // so that dispatch of foreground broadcasts gets precedence.
    private BroadcastQueue[] mBroadcastQueues = new BroadcastQueue[QUEUE_SIZE];

    ActiveBroadcasts(ActivityManagerService service) {
        mService = service;
        mHandler = service.mHandler;

        mBroadcastQueues[0] = mFgBroadcastQueue = new BroadcastQueue(service, mHandler,
                "foreground", Process.THREAD_GROUP_DEFAULT, false);
        mBroadcastQueues[1] = mBgBroadcastQueue = new BroadcastQueue(service, mHandler,
                "background", Process.THREAD_GROUP_BG_NONINTERACTIVE, true);
        if (ENABLE_EXTRA_QUEUES) {
            mBroadcastQueues[2] = mLtBroadcastQueue = new BroadcastQueue(service, mHandler,
                    "longtime", Process.THREAD_GROUP_BG_NONINTERACTIVE, true);
            mBroadcastQueues[3] = mNsBroadcastQueue = new BroadcastQueue(service, mHandler,
                    "nonsysapp", Process.THREAD_GROUP_BG_NONINTERACTIVE, true);
            mBroadcastQueues[4] = mBootBroadcastQueue = new BroadcastQueue(service, mHandler,
                    "booting", Process.THREAD_GROUP_BG_NONINTERACTIVE, false);
            mAppUidTypes = new SparseIntArray();
        } else {
            mLtBroadcastQueue = mNsBroadcastQueue = null;
            mAppUidTypes = null;
        }
    }

    private void selectQueueLocked(Intent intent, boolean fromSystem,
            ProcessRecord callerApp, String callerPackage, int callingUid) {
        int flags = intent.getFlags();
        if ((flags & Intent.FLAG_RECEIVER_FOREGROUND) != 0) {
            // Foreground flag has the highest priority.
            return;
        }
        if ((flags & QUEUE_CONTROL_FLAGS) != 0) {
            // We does not allow control internal queue by sender.
            Slog.w(TAG, "Found control flags in " + intent
                    + ", clear 0x" + Integer.toHexString(QUEUE_CONTROL_FLAGS));
            intent.setFlags(flags & ~QUEUE_CONTROL_FLAGS);
        }

        if (fromSystem) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case Intent.ACTION_POWER_CONNECTED:
                    case Intent.ACTION_POWER_DISCONNECTED:
                    case Intent.ACTION_PACKAGE_ADDED:
                    case Intent.ACTION_PACKAGE_CHANGED:
                    case Intent.ACTION_PACKAGE_REMOVED:
                    case Intent.ACTION_PACKAGE_REPLACED:
                    case android.net.ConnectivityManager.CONNECTIVITY_ACTION:
                    case android.accounts.AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION:
                        intent.addFlags(Intent.FLAG_RECEIVER_LONG_TIME);
                        return;
                    case Intent.ACTION_BOOT_COMPLETED:
                        if (mBootBroadcastQueue != null) {
                            intent.addFlags(Intent.FLAG_RECEIVER_BOOTING);
                        }
                        return;
                }
            }
        } else if (callerApp != null) {
            if ((callerApp.info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                intent.addFlags(Intent.FLAG_RECEIVER_NON_SYSTEM_APP);
            }
        } else { // No callerApp if it is sent by PendingIntent.
            if (UserHandle.isApp(callingUid)) {
                int type = mAppUidTypes.get(callingUid, UID_TYPE_INITIAL);
                if (type == UID_TYPE_NON_SYSTEM_APP) {
                    intent.addFlags(Intent.FLAG_RECEIVER_NON_SYSTEM_APP);
                } else if (type == UID_TYPE_INITIAL) {
                    try {
                        ApplicationInfo info = AppGlobals.getPackageManager().getApplicationInfo(
                                callerPackage, 0, UserHandle.getUserId(callingUid));
                        if (info != null && (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            mAppUidTypes.append(callingUid, UID_TYPE_SYSTEM_APP);
                        } else {
                            mAppUidTypes.append(callingUid, UID_TYPE_NON_SYSTEM_APP);
                            intent.addFlags(Intent.FLAG_RECEIVER_NON_SYSTEM_APP);
                        }
                    } catch (RemoteException e) {
                    }
                }
            }
        }
    }

    BroadcastQueue broadcastQueueByFlag(int flags) {
        if ((flags & Intent.FLAG_RECEIVER_FOREGROUND) != 0) {
            return mFgBroadcastQueue;
        }
        if (ENABLE_EXTRA_QUEUES) {
            if ((flags & Intent.FLAG_RECEIVER_LONG_TIME) != 0) {
                return mLtBroadcastQueue;
            } else if ((flags & Intent.FLAG_RECEIVER_NON_SYSTEM_APP) != 0) {
                return mNsBroadcastQueue;
            } else if ((flags & Intent.FLAG_RECEIVER_BOOTING) != 0
                    && mBootBroadcastQueue != null) {
                return mBootBroadcastQueue;
            }
        }
        return mBgBroadcastQueue;
    }

    BroadcastQueue broadcastQueueForIntent(Intent intent) {
        BroadcastQueue queue = broadcastQueueByFlag(intent.getFlags());
        if (DEBUG_BACKGROUND_BROADCAST) {
            Slog.i(TAG, "Broadcast intent " + intent + " on "
                    + queue.mQueueName + " queue");
        }
        return queue;
    }

    int broadcastIntentLocked(ProcessRecord callerApp,
            String callerPackage, Intent intent, String resolvedType,
            IIntentReceiver resultTo, int resultCode, String resultData,
            Bundle map, String requiredPermission, int appOp,
            boolean ordered, boolean sticky, int callingPid, int callingUid,
            int userId, boolean fromSystem) {
        if (ENABLE_EXTRA_QUEUES) {
            selectQueueLocked(intent, fromSystem, callerApp, callerPackage, callingUid);
        }

        // Add to the sticky list if requested.
        if (sticky) {
            if (mService.checkPermission(android.Manifest.permission.BROADCAST_STICKY,
                    callingPid, callingUid) != PackageManager.PERMISSION_GRANTED) {
                String msg = "Permission Denial: broadcastIntent() requesting a sticky broadcast from pid="
                        + callingPid + ", uid=" + callingUid
                        + " requires " + android.Manifest.permission.BROADCAST_STICKY;
                Slog.w(TAG, msg);
                throw new SecurityException(msg);
            }
            if (requiredPermission != null) {
                Slog.w(TAG, "Can't broadcast sticky intent " + intent
                        + " and enforce permission " + requiredPermission);
                return ActivityManager.BROADCAST_STICKY_CANT_HAVE_PERMISSION;
            }
            if (intent.getComponent() != null) {
                throw new SecurityException(
                        "Sticky broadcasts can't target a specific component");
            }
            // We use userId directly here, since the "all" target is maintained
            // as a separate set of sticky broadcasts.
            if (userId != UserHandle.USER_ALL) {
                // But first, if this is not a broadcast to all users, then
                // make sure it doesn't conflict with an existing broadcast to
                // all users.
                ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(
                        UserHandle.USER_ALL);
                if (stickies != null) {
                    ArrayList<Intent> list = stickies.get(intent.getAction());
                    if (list != null) {
                        for (int i = list.size() - 1; i >= 0; i--) {
                            if (intent.filterEquals(list.get(i))) {
                                throw new IllegalArgumentException(
                                        "Sticky broadcast " + intent + " for user "
                                        + userId + " conflicts with existing global broadcast");
                            }
                        }
                    }
                }
            }
            ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(userId);
            if (stickies == null) {
                stickies = new ArrayMap<>();
                mStickyBroadcasts.put(userId, stickies);
            }
            ArrayList<Intent> list = stickies.get(intent.getAction());
            if (list == null) {
                list = new ArrayList<>();
                stickies.put(intent.getAction(), list);
            }
            final int N = list.size();
            int i;
            for (i = 0; i < N; i++) {
                if (intent.filterEquals(list.get(i))) {
                    // This sticky already exists, replace it.
                    list.set(i, new Intent(intent));
                    break;
                }
            }
            if (i >= N) {
                list.add(new Intent(intent));
            }
        }

        int[] users;
        if (userId == UserHandle.USER_ALL) {
            // Caller wants broadcast to go to all started users.
            users = mService.mStartedUserArray;
        } else {
            // Caller wants broadcast to go to one specific user.
            users = new int[] {userId};
        }

        // Figure out who all will receive this broadcast.
        List receivers = null;
        List<BroadcastFilter> registeredReceivers = null;
        // Need to resolve the intent to interested receivers...
        if ((intent.getFlags() & Intent.FLAG_RECEIVER_REGISTERED_ONLY) == 0) {
            receivers = collectReceiverComponents(intent, resolvedType, callingUid, users);
        }
        if (intent.getComponent() == null) {
            if (userId == UserHandle.USER_ALL && callingUid == Process.SHELL_UID) {
                // Query one target user at a time, excluding shell-restricted users
                UserManagerService ums = mService.getUserManagerLocked();
                for (int targetUserId : users) {
                    if (ums.hasUserRestriction(
                            UserManager.DISALLOW_DEBUGGING_FEATURES, targetUserId)) {
                        continue;
                    }
                    List<BroadcastFilter> registeredReceiversForUser =
                            mReceiverResolver.queryIntent(intent,
                                    resolvedType, false, targetUserId);
                    if (registeredReceivers == null) {
                        registeredReceivers = registeredReceiversForUser;
                    } else if (registeredReceiversForUser != null) {
                        registeredReceivers.addAll(registeredReceiversForUser);
                    }
                }
            } else {
                registeredReceivers = mReceiverResolver.queryIntent(intent,
                        resolvedType, false, userId);
            }
        }

        final boolean replacePending =
                (intent.getFlags() & Intent.FLAG_RECEIVER_REPLACE_PENDING) != 0;

        if (DEBUG_BROADCAST) Slog.v(TAG, "Enqueing broadcast: " + intent.getAction()
                + " replacePending=" + replacePending);

        int NR = registeredReceivers != null ? registeredReceivers.size() : 0;
        if (!ordered && NR > 0) {
            // If we are not serializing this broadcast, then send the
            // registered receivers separately so they don't wait for the
            // components to be launched.
            final BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType, requiredPermission,
                    appOp, registeredReceivers, resultTo, resultCode, resultData, map,
                    ordered, sticky, false, userId);
            if (DEBUG_BROADCAST) Slog.v(
                    TAG, "Enqueueing parallel broadcast " + r);
            final boolean replaced = replacePending && queue.replaceParallelBroadcastLocked(r);

            if (!replaced) {
                queue.enqueueParallelBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
            registeredReceivers = null;
            NR = 0;
        }

        // Merge into one list.
        int ir = 0;
        if (receivers != null) {
            // A special case for PACKAGE_ADDED: do not allow the package
            // being added to see this broadcast.  This prevents them from
            // using this as a back door to get run as soon as they are
            // installed.  Maybe in the future we want to have a special install
            // broadcast or such for apps, but we'd like to deliberately make
            // this decision.
            String skipPackages[] = null;
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_RESTARTED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_DATA_CLEARED.equals(intent.getAction())) {
                Uri data = intent.getData();
                if (data != null) {
                    String pkgName = data.getSchemeSpecificPart();
                    if (pkgName != null) {
                        skipPackages = new String[] { pkgName };
                    }
                }
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(intent.getAction())) {
                skipPackages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            }
            if (skipPackages != null && (skipPackages.length > 0)) {
                for (String skipPackage : skipPackages) {
                    if (skipPackage != null) {
                        for (int i = receivers.size() - 1; i >= 0; i--) {
                            ResolveInfo curt = (ResolveInfo) receivers.get(i);
                            if (curt.activityInfo.packageName.equals(skipPackage)) {
                                receivers.remove(i);
                            }
                        }
                    }
                }
            }

            int NT = receivers != null ? receivers.size() : 0;
            int it = 0;
            ResolveInfo curt = null;
            BroadcastFilter curr = null;
            while (it < NT && ir < NR) {
                if (curt == null) {
                    curt = (ResolveInfo) receivers.get(it);
                }
                if (curr == null) {
                    curr = registeredReceivers.get(ir);
                }
                if (curr.getPriority() >= curt.priority) {
                    // Insert this broadcast record into the final list.
                    receivers.add(it, curr);
                    ir++;
                    curr = null;
                    it++;
                    NT++;
                } else {
                    // Skip to the next ResolveInfo in the final list.
                    it++;
                    curt = null;
                }
            }
        }
        while (ir < NR) {
            if (receivers == null) {
                receivers = new ArrayList();
            }
            receivers.add(registeredReceivers.get(ir));
            ir++;
        }

        if ((receivers != null && !receivers.isEmpty()) || resultTo != null) {
            BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType,
                    requiredPermission, appOp, receivers, resultTo, resultCode,
                    resultData, map, ordered, sticky, false, userId);
            if (DEBUG_BROADCAST) Slog.v(
                    TAG, "Enqueueing ordered broadcast " + r
                    + ": prev had " + queue.mOrderedBroadcasts.size());
            if (DEBUG_BROADCAST) {
                int seq = r.intent.getIntExtra("seq", -1);
                Slog.i(TAG, "Enqueueing broadcast " + r.intent.getAction() + " seq=" + seq);
            }
            boolean replaced = replacePending && queue.replaceOrderedBroadcastLocked(r);
            if (!replaced) {
                queue.enqueueOrderedBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
        }

        return ActivityManager.BROADCAST_SUCCESS;
    }


    List<ResolveInfo> collectReceiverComponents(Intent intent, String resolvedType,
            int callingUid, int[] users) {
        List<ResolveInfo> receivers = null;
        try {
            HashSet<ComponentName> singleUserReceivers = null;
            boolean scannedFirstReceivers = false;
            for (int user : users) {
                // Skip users that have Shell restrictions
                if (callingUid == Process.SHELL_UID
                        && mService.getUserManagerLocked().hasUserRestriction(
                                UserManager.DISALLOW_DEBUGGING_FEATURES, user)) {
                    continue;
                }
                List<ResolveInfo> newReceivers = AppGlobals.getPackageManager()
                        .queryIntentReceivers(intent, resolvedType,
                                ActivityManagerService.STOCK_PM_FLAGS, user);
                if (user != 0 && newReceivers != null) {
                    // If this is not the primary user, we need to check for
                    // any receivers that should be filtered out.
                    for (int i = newReceivers.size() - 1; i >= 0; i--) {
                        ResolveInfo ri = newReceivers.get(i);
                        if ((ri.activityInfo.flags & ActivityInfo.FLAG_PRIMARY_USER_ONLY) != 0) {
                            newReceivers.remove(i);
                        }
                    }
                }
                if (newReceivers != null && newReceivers.isEmpty()) {
                    newReceivers = null;
                }
                if (receivers == null) {
                    receivers = newReceivers;
                } else if (newReceivers != null) {
                    // We need to concatenate the additional receivers
                    // found with what we have do far.  This would be easy,
                    // but we also need to de-dup any receivers that are
                    // singleUser.
                    if (!scannedFirstReceivers) {
                        // Collect any single user receivers we had already retrieved.
                        scannedFirstReceivers = true;
                        for (int i = 0; i < receivers.size(); i++) {
                            ResolveInfo ri = receivers.get(i);
                            if ((ri.activityInfo.flags & ActivityInfo.FLAG_SINGLE_USER) != 0) {
                                ComponentName cn = new ComponentName(
                                        ri.activityInfo.packageName, ri.activityInfo.name);
                                if (singleUserReceivers == null) {
                                    singleUserReceivers = new HashSet<ComponentName>();
                                }
                                singleUserReceivers.add(cn);
                            }
                        }
                    }
                    // Add the new results to the existing results, tracking
                    // and de-dupping single user receivers.
                    for (int i = 0; i < newReceivers.size(); i++) {
                        ResolveInfo ri = newReceivers.get(i);
                        if ((ri.activityInfo.flags & ActivityInfo.FLAG_SINGLE_USER) != 0) {
                            ComponentName cn = new ComponentName(
                                    ri.activityInfo.packageName, ri.activityInfo.name);
                            if (singleUserReceivers == null) {
                                singleUserReceivers = new HashSet<ComponentName>();
                            }
                            if (!singleUserReceivers.contains(cn)) {
                                singleUserReceivers.add(cn);
                                receivers.add(ri);
                            }
                        } else {
                            receivers.add(ri);
                        }
                    }
                }
            }
        } catch (RemoteException ex) {
            // pm is in same process, this will never happen.
        }
        return receivers;
    }

    void unbroadcastIntentLocked(Intent intent, int userId) {
        ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(userId);
        if (stickies != null) {
            ArrayList<Intent> list = stickies.get(intent.getAction());
            if (list != null) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (intent.filterEquals(list.get(i))) {
                        list.remove(i);
                        break;
                    }
                }
                if (list.isEmpty()) {
                    stickies.remove(intent.getAction());
                }
            }
            if (stickies.isEmpty()) {
                mStickyBroadcasts.remove(userId);
            }
        }
    }

    public Intent registerReceiver(IApplicationThread caller, String callerPackage,
            IIntentReceiver receiver, IntentFilter filter, String permission, int userId) {
        ArrayList<Intent> stickyIntents = null;
        ProcessRecord callerApp = null;
        int callingUid;
        int callingPid;
        synchronized (mService) {
            if (caller != null) {
                callerApp = mService.getRecordForAppLocked(caller);
                if (callerApp == null) {
                    throw new SecurityException(
                            "Unable to find app for caller " + caller
                            + " (pid=" + Binder.getCallingPid()
                            + ") when registering receiver " + receiver);
                }
                if (callerApp.info.uid != Process.SYSTEM_UID &&
                        !callerApp.pkgList.containsKey(callerPackage) &&
                        !"android".equals(callerPackage)) {
                    throw new SecurityException("Given caller package " + callerPackage
                            + " is not running in process " + callerApp);
                }
                callingUid = callerApp.info.uid;
                callingPid = callerApp.pid;
            } else {
                callerPackage = null;
                callingUid = Binder.getCallingUid();
                callingPid = Binder.getCallingPid();
            }

            userId = mService.handleIncomingUser(callingPid, callingUid, userId, true,
                    ActivityManagerService.ALLOW_FULL_ONLY, "registerReceiver", callerPackage);

            Iterator<String> actions = filter.actionsIterator();
            if (actions == null) {
                ArrayList<String> noAction = new ArrayList<>(1);
                noAction.add(null);
                actions = noAction.iterator();
            }

            // Collect stickies of users
            int[] userIds = { UserHandle.USER_ALL, UserHandle.getUserId(callingUid) };
            while (actions.hasNext()) {
                String action = actions.next();
                for (int id : userIds) {
                    ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(id);
                    if (stickies != null) {
                        ArrayList<Intent> intents = stickies.get(action);
                        if (intents != null) {
                            if (stickyIntents == null) {
                                stickyIntents = new ArrayList<>();
                            }
                            stickyIntents.addAll(intents);
                        }
                    }
                }
            }
        }

        ArrayList<Intent> allSticky = null;
        if (stickyIntents != null) {
            final ContentResolver resolver = mService.mContext.getContentResolver();
            // Look for any matching sticky broadcasts...
            for (int i = 0, N = stickyIntents.size(); i < N; i++) {
                Intent intent = stickyIntents.get(i);
                // If intent has scheme "content", it will need to acccess
                // provider that needs to lock mProviderMap in ActivityThread
                // and also it may need to wait application response, so we
                // cannot lock ActivityManagerService here.
                if (filter.match(resolver, intent, true, TAG) >= 0) {
                    if (allSticky == null) {
                        allSticky = new ArrayList<>();
                    }
                    allSticky.add(intent);
                }
            }
        }

        // The first sticky in the list is returned directly back to the client.
        Intent sticky = allSticky != null ? allSticky.get(0) : null;
        if (DEBUG_BROADCAST) Slog.v(TAG, "Register receiver " + filter + ": " + sticky);

        if (receiver == null) {
            return sticky;
        }

        synchronized (mService) {
            if (callerApp != null && (callerApp.thread == null
                    || callerApp.thread.asBinder() != caller.asBinder())) {
                // Original caller already died
                return null;
            }
            ReceiverList rl = mRegisteredReceivers.get(receiver.asBinder());
            if (rl == null) {
                rl = new ReceiverList(mService, callerApp, callingPid, callingUid,
                        userId, receiver);
                if (rl.app != null) {
                    rl.app.receivers.add(rl);
                } else {
                    try {
                        receiver.asBinder().linkToDeath(rl, 0);
                    } catch (RemoteException e) {
                        return sticky;
                    }
                    rl.linkedToDeath = true;
                }
                mRegisteredReceivers.put(receiver.asBinder(), rl);
            } else if (rl.uid != callingUid) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for uid " + callingUid
                        + " was previously registered for uid " + rl.uid);
            } else if (rl.pid != callingPid) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for pid " + callingPid
                        + " was previously registered for pid " + rl.pid);
            } else if (rl.userId != userId) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for user " + userId
                        + " was previously registered for user " + rl.userId);
            }
            BroadcastFilter bf = new BroadcastFilter(filter, rl, callerPackage,
                    permission, callingUid, userId,
                    (callerApp.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            rl.add(bf);
            if (!bf.debugCheck()) {
                Slog.w(TAG, "==> For Dynamic broadast");
            }
            mReceiverResolver.addFilter(bf);

            // Enqueue broadcasts for all existing stickies that match this filter.
            if (allSticky != null) {
                ArrayList<BroadcastFilter> receivers = new ArrayList<>();
                receivers.add(bf);

                final int N = allSticky.size();
                for (int i = 0; i < N; i++) {
                    Intent intent = allSticky.get(i);
                    BroadcastQueue queue = broadcastQueueForIntent(intent);
                    BroadcastRecord r = new BroadcastRecord(queue, intent, null,
                            null, -1, -1, null, null, AppOpsManager.OP_NONE, receivers, null, 0,
                            null, null, false, true, true, -1);
                    queue.enqueueParallelBroadcastLocked(r);
                    queue.scheduleBroadcastsLocked();
                }
            }

            return sticky;
        }
    }

    boolean unregisterReceiverLocked(IIntentReceiver receiver) {
        boolean doTrim = false;
        ReceiverList rl = mRegisteredReceivers.get(receiver.asBinder());
        if (rl != null) {
            final BroadcastRecord r = rl.curBroadcast;
            if (r != null && r == r.queue.getMatchingOrderedReceiver(r)) {
                final boolean doNext = r.queue.finishReceiverLocked(
                        r, r.resultCode, r.resultData, r.resultExtras,
                        r.resultAbort, false);
                if (doNext) {
                    doTrim = true;
                    r.queue.processNextBroadcast(false);
                }
            }

            if (rl.app != null) {
                rl.app.receivers.remove(rl);
            }
            removeReceiverLocked(rl);
            if (rl.linkedToDeath) {
                rl.linkedToDeath = false;
                rl.receiver.asBinder().unlinkToDeath(rl, 0);
            }
        }
        return doTrim;
    }

    private void removeReceiverLocked(ReceiverList rl) {
        mRegisteredReceivers.remove(rl.receiver.asBinder());
        for (int i = rl.size() - 1; i >= 0; i--) {
            mReceiverResolver.removeFilter(rl.get(i));
        }
    }

    void removeAllReceiverLocked(ProcessRecord app) {
        for (int i = app.receivers.size() - 1; i >= 0; i--) {
            removeReceiverLocked(app.receivers.valueAt(i));
        }
        app.receivers.clear();
    }

    void skipCurrentReceiverLocked(ProcessRecord app) {
        for (BroadcastQueue queue : mBroadcastQueues) {
            queue.skipCurrentReceiverLocked(app);
        }
    }

    void skipPendingBroadcastIfNeededLocked(int pid) {
        if (isPendingBroadcastProcessLocked(pid)) {
            Slog.w(TAG, "Unattached app died before broadcast acknowledged, skipping pid=" + pid);
            for (BroadcastQueue queue : mBroadcastQueues) {
                queue.skipPendingBroadcastLocked(pid);
            }
        }
    }

    // The app just attached; send any pending broadcasts that it should receive
    boolean sendPendingBroadcastsLocked(ProcessRecord app) {
        boolean didSomething = false;
        for (BroadcastQueue queue : mBroadcastQueues) {
            didSomething |= queue.sendPendingBroadcastsLocked(app);
        }
        return didSomething;
    }

    boolean isPendingBroadcastProcessLocked(int pid) {
        for (BroadcastQueue queue : mBroadcastQueues) {
            if (queue.isPendingBroadcastProcessLocked(pid)) {
                return true;
            }
        }
        return false;
    }

    boolean processingBroadcasts() {
        for (BroadcastQueue q : mBroadcastQueues) {
            if (!q.mParallelBroadcasts.isEmpty() || !q.mOrderedBroadcasts.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Returns which broadcast queue the app is the current [or imminent] receiver
    // on, or 'null' if the app is not an active broadcast recipient.
    BroadcastQueue isReceivingBroadcast(ProcessRecord app) {
        BroadcastRecord r = app.curReceiver;
        if (r != null) {
            return r.queue;
        }

        // It's not the current receiver, but it might be starting up to become one
        synchronized (mService) {
            for (BroadcastQueue queue : mBroadcastQueues) {
                r = queue.mPendingBroadcast;
                if (r != null && r.curApp == app) {
                    // found it; report which queue it's in
                    return queue;
                }
            }
        }

        return null;
    }

    void finishReceiver(IBinder who, int resultCode, String resultData,
            Bundle resultExtras, boolean resultAbort, int flags) {
        boolean doNext = false;
        BroadcastRecord r;
        synchronized (mService) {
            BroadcastQueue queue = broadcastQueueByFlag(flags);
            r = queue.getMatchingOrderedReceiver(who);
            if (r != null) {
                doNext = queue.finishReceiverLocked(r, resultCode,
                    resultData, resultExtras, resultAbort, true);
                if (queue == mBootBroadcastQueue && r.nextReceiver >= r.receivers.size()) {
                    // The last boot receiver is finished, just drop the boot queue.
                    // Note this does not include boot complete by user switch.
                    Slog.i(TAG, "All boot receivers are complete");
                    mBootBroadcastQueue = null;
                    BroadcastQueue[] queues = new BroadcastQueue[mBroadcastQueues.length - 1];
                    System.arraycopy(mBroadcastQueues, 0, queues, 0, queues.length);
                    mBroadcastQueues = queues;
                }
            }
        }

        if (doNext) {
            r.queue.processNextBroadcast(false);
        }
    }

    void dumpBroadcastsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean needSep = false;
        boolean onlyHistory = false;
        boolean printedAnything = false;

        if ("history".equals(dumpPackage)) {
            if (opti < args.length && "-s".equals(args[opti])) {
                dumpAll = false;
            }
            onlyHistory = true;
            dumpPackage = null;
        }

        pw.println("ACTIVITY MANAGER BROADCAST STATE (dumpsys activity broadcasts)");
        if (!onlyHistory && dumpAll) {
            if (mRegisteredReceivers.size() > 0) {
                boolean printed = false;
                for (ReceiverList r : mRegisteredReceivers.values()) {
                    if (dumpPackage != null && (r.app == null ||
                            !dumpPackage.equals(r.app.info.packageName))) {
                        continue;
                    }
                    if (!printed) {
                        pw.println("  Registered Receivers:");
                        needSep = true;
                        printed = true;
                        printedAnything = true;
                    }
                    pw.print("  * "); pw.println(r);
                    r.dump(pw, "    ");
                }
            }

            if (mReceiverResolver.dump(pw, needSep ?
                    "\n  Receiver Resolver Table:" : "  Receiver Resolver Table:",
                    "    ", dumpPackage, false, false)) {
                needSep = true;
                printedAnything = true;
            }
        }

        for (BroadcastQueue q : mBroadcastQueues) {
            needSep = q.dumpLocked(fd, pw, args, opti, dumpAll, dumpPackage, needSep);
            printedAnything |= needSep;
        }

        boolean printed = false;
        for (int i = 0; i < MAX_BROADCAST_HISTORY; i++) {
            BroadcastRecord r = mBroadcastHistory[i];
            if (r == null) {
                break;
            }
            if (dumpPackage != null && !dumpPackage.equals(r.callerPackage)) {
                continue;
            }
            if (!printed) {
                if (needSep) {
                    pw.println();
                }
                needSep = true;
                pw.println("  Historical broadcasts:");
                printed = true;
            }
            if (dumpAll) {
                pw.print("  Historical Broadcast " + r.queue.mQueueName + " #");
                        pw.print(i); pw.println(":");
                r.dump(pw, "    ");
            } else {
                pw.print("  #"); pw.print(i); pw.print(": "); pw.println(r);
                pw.print("    ");
                pw.println(r.intent.toShortString(false, true, true, false));
                if (r.targetComp != null && r.targetComp != r.intent.getComponent()) {
                    pw.print("    targetComp: "); pw.println(r.targetComp.toShortString());
                }
                Bundle bundle = r.intent.getExtras();
                if (bundle != null) {
                    pw.print("    extras: "); pw.println(bundle.toString());
                }
            }
        }

        if (dumpPackage == null) {
            if (dumpAll) {
                printed = false;
            }
            for (int i = 0; i < MAX_BROADCAST_SUMMARY_HISTORY; i++) {
                Intent intent = mBroadcastSummaryHistory[i];
                if (intent == null) {
                    break;
                }
                if (!printed) {
                    if (needSep) {
                        pw.println();
                    }
                    needSep = true;
                    pw.println("  Historical broadcasts summary:");
                    printed = true;
                }
                if (!dumpAll && i >= 50) {
                    pw.println("  ...");
                    break;
                }
                pw.print("  #"); pw.print(i);
                BroadcastQueue queue = broadcastQueueForIntent(intent);
                pw.print(" [" + queue.mQueueName + "]: ");
                pw.println(intent.toShortString(false, true, true, false));
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    pw.print("    extras: "); pw.println(bundle.toString());
                }
            }
        }

        needSep = true;
        if (!onlyHistory && mStickyBroadcasts != null && dumpPackage == null) {
            for (int user = 0; user < mStickyBroadcasts.size(); user++) {
                if (needSep) {
                    pw.println();
                }
                needSep = true;
                printedAnything = true;
                pw.print("  Sticky broadcasts for user ");
                        pw.print(mStickyBroadcasts.keyAt(user)); pw.println(":");
                StringBuilder sb = new StringBuilder(128);
                for (Map.Entry<String, ArrayList<Intent>> ent
                        : mStickyBroadcasts.valueAt(user).entrySet()) {
                    pw.print("  * Sticky action "); pw.print(ent.getKey());
                    if (dumpAll) {
                        pw.println(":");
                        ArrayList<Intent> intents = ent.getValue();
                        final int N = intents.size();
                        for (int i = 0; i < N; i++) {
                            sb.setLength(0);
                            sb.append("    Intent: ");
                            intents.get(i).toShortString(sb, false, true, false, false);
                            pw.println(sb.toString());
                            Bundle bundle = intents.get(i).getExtras();
                            if (bundle != null) {
                                pw.print("      ");
                                pw.println(bundle.toString());
                            }
                        }
                    } else {
                        pw.println("");
                    }
                }
            }
        }

        if (!onlyHistory && dumpAll) {
            pw.println();
            for (BroadcastQueue queue : mBroadcastQueues) {
                pw.println("  mBroadcastsScheduled [" + queue.mQueueName + "]="
                        + queue.mBroadcastsScheduled);
            }
            pw.println("  mHandler:");
            mHandler.dump(new PrintWriterPrinter(pw), "    ");
            needSep = true;
            printedAnything = true;
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void backgroundServicesFinishedLocked(int userId) {
        for (BroadcastQueue queue : mBroadcastQueues) {
            queue.backgroundServicesFinishedLocked(userId);
        }
    }

    void addBroadcastToHistoryLocked(BroadcastRecord r) {
        if (r.callingUid < 0) {
            // This was from a registerReceiver() call; ignore it.
            return;
        }
        System.arraycopy(mBroadcastHistory, 0, mBroadcastHistory, 1,
                MAX_BROADCAST_HISTORY - 1);
        r.finishTime = SystemClock.uptimeMillis();
        mBroadcastHistory[0] = r;
        System.arraycopy(mBroadcastSummaryHistory, 0, mBroadcastSummaryHistory, 1,
                MAX_BROADCAST_SUMMARY_HISTORY - 1);
        mBroadcastSummaryHistory[0] = r.intent;
    }
}
