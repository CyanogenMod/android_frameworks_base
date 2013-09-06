/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.Manifest.permission.START_ANY_ACTIVITY;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import com.android.internal.app.HeavyWeightSwitcherActivity;
import com.android.internal.os.BatteryStatsImpl;
import com.android.server.am.ActivityManagerService.PendingActivityLaunch;
import com.android.server.power.PowerManagerService;
import com.android.server.wm.AppTransition;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IThumbnailRetriever;
import android.app.IApplicationThread;
import android.app.PendingIntent;
import android.app.ResultInfo;
import android.app.IActivityManager.WaitResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.Display;

import com.android.internal.app.ActivityTrigger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * State and management of a single stack of activities.
 */
final class ActivityStack {
    static final String TAG = ActivityManagerService.TAG;
    static final boolean localLOGV = ActivityManagerService.localLOGV;
    static final boolean DEBUG_SWITCH = ActivityManagerService.DEBUG_SWITCH;
    static final boolean DEBUG_PAUSE = ActivityManagerService.DEBUG_PAUSE;
    static final boolean DEBUG_VISBILITY = ActivityManagerService.DEBUG_VISBILITY;
    static final boolean DEBUG_USER_LEAVING = ActivityManagerService.DEBUG_USER_LEAVING;
    static final boolean DEBUG_TRANSITION = ActivityManagerService.DEBUG_TRANSITION;
    static final boolean DEBUG_RESULTS = ActivityManagerService.DEBUG_RESULTS;
    static final boolean DEBUG_CONFIGURATION = ActivityManagerService.DEBUG_CONFIGURATION;
    static final boolean DEBUG_TASKS = ActivityManagerService.DEBUG_TASKS;
    static final boolean DEBUG_CLEANUP = ActivityManagerService.DEBUG_CLEANUP;
    
    static final boolean DEBUG_STATES = false;
    static final boolean DEBUG_ADD_REMOVE = false;
    static final boolean DEBUG_SAVED_STATE = false;
    static final boolean DEBUG_APP = false;

    static final boolean VALIDATE_TOKENS = ActivityManagerService.VALIDATE_TOKENS;
    
    // How long we wait until giving up on the last activity telling us it
    // is idle.
    static final int IDLE_TIMEOUT = 10*1000;

    // Ticks during which we check progress while waiting for an app to launch.
    static final int LAUNCH_TICK = 500;

    // How long we wait until giving up on the last activity to pause.  This
    // is short because it directly impacts the responsiveness of starting the
    // next activity.
    static final int PAUSE_TIMEOUT = 500;

    // How long we wait for the activity to tell us it has stopped before
    // giving up.  This is a good amount of time because we really need this
    // from the application in order to get its saved state.
    static final int STOP_TIMEOUT = 10*1000;

    // How long we can hold the sleep wake lock before giving up.
    static final int SLEEP_TIMEOUT = 5*1000;

    // How long we can hold the launch wake lock before giving up.
    static final int LAUNCH_TIMEOUT = 10*1000;

    // How long we wait until giving up on an activity telling us it has
    // finished destroying itself.
    static final int DESTROY_TIMEOUT = 10*1000;
    
    // How long until we reset a task when the user returns to it.  Currently
    // disabled.
    static final long ACTIVITY_INACTIVE_RESET_TIME = 0;
    
    // How long between activity launches that we consider safe to not warn
    // the user about an unexpected activity being launched on top.
    static final long START_WARN_TIME = 5*1000;
    
    // Set to false to disable the preview that is shown while a new activity
    // is being started.
    static final boolean SHOW_APP_STARTING_PREVIEW = true;
    
    enum ActivityState {
        INITIALIZING,
        RESUMED,
        PAUSING,
        PAUSED,
        STOPPING,
        STOPPED,
        FINISHING,
        DESTROYING,
        DESTROYED
    }

    final ActivityManagerService mService;
    final boolean mMainStack;
    
    final Context mContext;
    
    /**
     * The back history of all previous (and possibly still
     * running) activities.  It contains HistoryRecord objects.
     */
    final ArrayList<ActivityRecord> mHistory = new ArrayList<ActivityRecord>();

    /**
     * Used for validating app tokens with window manager.
     */
    final ArrayList<IBinder> mValidateAppTokens = new ArrayList<IBinder>();

    /**
     * List of running activities, sorted by recent usage.
     * The first entry in the list is the least recently used.
     * It contains HistoryRecord objects.
     */
    final ArrayList<ActivityRecord> mLRUActivities = new ArrayList<ActivityRecord>();

    /**
     * List of activities that are waiting for a new activity
     * to become visible before completing whatever operation they are
     * supposed to do.
     */
    final ArrayList<ActivityRecord> mWaitingVisibleActivities
            = new ArrayList<ActivityRecord>();

    /**
     * List of activities that are ready to be stopped, but waiting
     * for the next activity to settle down before doing so.  It contains
     * HistoryRecord objects.
     */
    final ArrayList<ActivityRecord> mStoppingActivities
            = new ArrayList<ActivityRecord>();

    /**
     * List of activities that are in the process of going to sleep.
     */
    final ArrayList<ActivityRecord> mGoingToSleepActivities
            = new ArrayList<ActivityRecord>();

    /**
     * Animations that for the current transition have requested not to
     * be considered for the transition animation.
     */
    final ArrayList<ActivityRecord> mNoAnimActivities
            = new ArrayList<ActivityRecord>();

    /**
     * List of activities that are ready to be finished, but waiting
     * for the previous activity to settle down before doing so.  It contains
     * HistoryRecord objects.
     */
    final ArrayList<ActivityRecord> mFinishingActivities
            = new ArrayList<ActivityRecord>();
    
    /**
     * List of people waiting to find out about the next launched activity.
     */
    final ArrayList<IActivityManager.WaitResult> mWaitingActivityLaunched
            = new ArrayList<IActivityManager.WaitResult>();
    
    /**
     * List of people waiting to find out about the next visible activity.
     */
    final ArrayList<IActivityManager.WaitResult> mWaitingActivityVisible
            = new ArrayList<IActivityManager.WaitResult>();

    final ArrayList<UserStartedState> mStartingUsers
            = new ArrayList<UserStartedState>();

    /**
     * Set when the system is going to sleep, until we have
     * successfully paused the current activity and released our wake lock.
     * At that point the system is allowed to actually sleep.
     */
    final PowerManager.WakeLock mGoingToSleep;

    /**
     * We don't want to allow the device to go to sleep while in the process
     * of launching an activity.  This is primarily to allow alarm intent
     * receivers to launch an activity and get that to run before the device
     * goes back to sleep.
     */
    final PowerManager.WakeLock mLaunchingActivity;

    /**
     * When we are in the process of pausing an activity, before starting the
     * next one, this variable holds the activity that is currently being paused.
     */
    ActivityRecord mPausingActivity = null;

    /**
     * This is the last activity that we put into the paused state.  This is
     * used to determine if we need to do an activity transition while sleeping,
     * when we normally hold the top activity paused.
     */
    ActivityRecord mLastPausedActivity = null;

    /**
     * Current activity that is resumed, or null if there is none.
     */
    ActivityRecord mResumedActivity = null;
    
    /**
     * This is the last activity that has been started.  It is only used to
     * identify when multiple activities are started at once so that the user
     * can be warned they may not be in the activity they think they are.
     */
    ActivityRecord mLastStartedActivity = null;
    
    /**
     * Set when we know we are going to be calling updateConfiguration()
     * soon, so want to skip intermediate config checks.
     */
    boolean mConfigWillChange;

    /**
     * Set to indicate whether to issue an onUserLeaving callback when a
     * newly launched activity is being brought in front of us.
     */
    boolean mUserLeaving = false;
    
    long mInitialStartTime = 0;
    
    /**
     * Set when we have taken too long waiting to go to sleep.
     */
    boolean mSleepTimeout = false;

    /**
     * Dismiss the keyguard after the next activity is displayed?
     */
    boolean mDismissKeyguardOnNextActivity = false;

    /**
     * Is the privacy guard currently enabled?
     */
    String mPrivacyGuardPackageName = null;

    /**
     * Save the most recent screenshot for reuse. This keeps Recents from taking two identical
     * screenshots, one for the Recents thumbnail and one for the pauseActivity thumbnail.
     */
    private ActivityRecord mLastScreenshotActivity = null;
    private Bitmap mLastScreenshotBitmap = null;

    int mThumbnailWidth = -1;
    int mThumbnailHeight = -1;

    private int mCurrentUser;

    static final int SLEEP_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG;
    static final int PAUSE_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 1;
    static final int IDLE_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 2;
    static final int IDLE_NOW_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 3;
    static final int LAUNCH_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 4;
    static final int DESTROY_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 5;
    static final int RESUME_TOP_ACTIVITY_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 6;
    static final int LAUNCH_TICK_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 7;
    static final int STOP_TIMEOUT_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 8;
    static final int DESTROY_ACTIVITIES_MSG = ActivityManagerService.FIRST_ACTIVITY_STACK_MSG + 9;

    static class ScheduleDestroyArgs {
        final ProcessRecord mOwner;
        final boolean mOomAdj;
        final String mReason;
        ScheduleDestroyArgs(ProcessRecord owner, boolean oomAdj, String reason) {
            mOwner = owner;
            mOomAdj = oomAdj;
            mReason = reason;
        }
    }

    private static final ActivityTrigger mActivityTrigger;

    private final PowerManagerService mPm;

    static {
        if (SystemProperties.QCOM_HARDWARE) {
            mActivityTrigger = new ActivityTrigger();
        } else {
            mActivityTrigger = null;
        }
    }

    final Handler mHandler;

    final class ActivityStackHandler extends Handler {
        //public Handler() {
        //    if (localLOGV) Slog.v(TAG, "Handler started!");
        //}
        public ActivityStackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SLEEP_TIMEOUT_MSG: {
                    synchronized (mService) {
                        if (mService.isSleeping()) {
                            Slog.w(TAG, "Sleep timeout!  Sleeping now.");
                            mSleepTimeout = true;
                            checkReadyForSleepLocked();
                        }
                    }
                } break;
                case PAUSE_TIMEOUT_MSG: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    // We don't at this point know if the activity is fullscreen,
                    // so we need to be conservative and assume it isn't.
                    Slog.w(TAG, "Activity pause timeout for " + r);
                    synchronized (mService) {
                        if (r.app != null) {
                            mService.logAppTooSlow(r.app, r.pauseTime,
                                    "pausing " + r);
                        }
                    }

                    activityPaused(r != null ? r.appToken : null, true);
                } break;
                case IDLE_TIMEOUT_MSG: {
                    if (mService.mDidDexOpt) {
                        mService.mDidDexOpt = false;
                        Message nmsg = mHandler.obtainMessage(IDLE_TIMEOUT_MSG);
                        nmsg.obj = msg.obj;
                        mHandler.sendMessageDelayed(nmsg, IDLE_TIMEOUT);
                        return;
                    }
                    // We don't at this point know if the activity is fullscreen,
                    // so we need to be conservative and assume it isn't.
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    Slog.w(TAG, "Activity idle timeout for " + r);
                    activityIdleInternal(r != null ? r.appToken : null, true, null);
                } break;
                case LAUNCH_TICK_MSG: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    synchronized (mService) {
                        if (r.continueLaunchTickingLocked()) {
                            mService.logAppTooSlow(r.app, r.launchTickTime,
                                    "launching " + r);
                        }
                    }
                } break;
                case DESTROY_TIMEOUT_MSG: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    // We don't at this point know if the activity is fullscreen,
                    // so we need to be conservative and assume it isn't.
                    Slog.w(TAG, "Activity destroy timeout for " + r);
                    activityDestroyed(r != null ? r.appToken : null);
                } break;
                case IDLE_NOW_MSG: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    activityIdleInternal(r != null ? r.appToken : null, false, null);
                } break;
                case LAUNCH_TIMEOUT_MSG: {
                    if (mService.mDidDexOpt) {
                        mService.mDidDexOpt = false;
                        Message nmsg = mHandler.obtainMessage(LAUNCH_TIMEOUT_MSG);
                        mHandler.sendMessageDelayed(nmsg, LAUNCH_TIMEOUT);
                        return;
                    }
                    synchronized (mService) {
                        if (mLaunchingActivity.isHeld()) {
                            Slog.w(TAG, "Launch timeout has expired, giving up wake lock!");
                            mLaunchingActivity.release();
                        }
                    }
                } break;
                case RESUME_TOP_ACTIVITY_MSG: {
                    synchronized (mService) {
                        resumeTopActivityLocked(null);
                    }
                } break;
                case STOP_TIMEOUT_MSG: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    // We don't at this point know if the activity is fullscreen,
                    // so we need to be conservative and assume it isn't.
                    Slog.w(TAG, "Activity stop timeout for " + r);
                    synchronized (mService) {
                        if (r.isInHistory()) {
                            activityStoppedLocked(r, null, null, null);
                        }
                    }
                } break;
                case DESTROY_ACTIVITIES_MSG: {
                    ScheduleDestroyArgs args = (ScheduleDestroyArgs)msg.obj;
                    synchronized (mService) {
                        destroyActivitiesLocked(args.mOwner, args.mOomAdj, args.mReason);
                    }
                }
            }
        }
    }

    ActivityStack(ActivityManagerService service, Context context, boolean mainStack, Looper looper) {
        mHandler = new ActivityStackHandler(looper);
        mService = service;
        mContext = context;
        mMainStack = mainStack;
        PowerManager pm =
            (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mGoingToSleep = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivityManager-Sleep");
        mLaunchingActivity = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivityManager-Launch");
        mPm = (PowerManagerService) ServiceManager.getService("power");
        mLaunchingActivity.setReferenceCounted(false);
    }

    private boolean okToShow(ActivityRecord r) {
        return r.userId == mCurrentUser
                || (r.info.flags & ActivityInfo.FLAG_SHOW_ON_LOCK_SCREEN) != 0;
    }

    final ActivityRecord topRunningActivityLocked(ActivityRecord notTop) {
        int i = mHistory.size()-1;
        while (i >= 0) {
            ActivityRecord r = mHistory.get(i);
            if (!r.finishing && r != notTop && okToShow(r)) {
                return r;
            }
            i--;
        }
        return null;
    }

    final ActivityRecord topRunningNonDelayedActivityLocked(ActivityRecord notTop) {
        int i = mHistory.size()-1;
        while (i >= 0) {
            ActivityRecord r = mHistory.get(i);
            if (!r.finishing && !r.delayedResume && r != notTop && okToShow(r)) {
                return r;
            }
            i--;
        }
        return null;
    }

    /**
     * This is a simplified version of topRunningActivityLocked that provides a number of
     * optional skip-over modes.  It is intended for use with the ActivityController hook only.
     * 
     * @param token If non-null, any history records matching this token will be skipped.
     * @param taskId If non-zero, we'll attempt to skip over records with the same task ID.
     * 
     * @return Returns the HistoryRecord of the next activity on the stack.
     */
    final ActivityRecord topRunningActivityLocked(IBinder token, int taskId) {
        int i = mHistory.size()-1;
        while (i >= 0) {
            ActivityRecord r = mHistory.get(i);
            // Note: the taskId check depends on real taskId fields being non-zero
            if (!r.finishing && (token != r.appToken) && (taskId != r.task.taskId)
                    && okToShow(r)) {
                return r;
            }
            i--;
        }
        return null;
    }

    final int indexOfTokenLocked(IBinder token) {
        return mHistory.indexOf(ActivityRecord.forToken(token));
    }

    final int indexOfActivityLocked(ActivityRecord r) {
        return mHistory.indexOf(r);
    }

    final ActivityRecord isInStackLocked(IBinder token) {
        ActivityRecord r = ActivityRecord.forToken(token);
        if (mHistory.contains(r)) {
            return r;
        }
        return null;
    }

    private final boolean updateLRUListLocked(ActivityRecord r) {
        final boolean hadit = mLRUActivities.remove(r);
        mLRUActivities.add(r);
        return hadit;
    }

    /**
     * Returns the top activity in any existing task matching the given
     * Intent.  Returns null if no such task is found.
     */
    private ActivityRecord findTaskLocked(Intent intent, ActivityInfo info) {
        ComponentName cls = intent.getComponent();
        if (info.targetActivity != null) {
            cls = new ComponentName(info.packageName, info.targetActivity);
        }

        TaskRecord cp = null;

        final int userId = UserHandle.getUserId(info.applicationInfo.uid);
        final int N = mHistory.size();
        for (int i=(N-1); i>=0; i--) {
            ActivityRecord r = mHistory.get(i);
            if (!r.finishing && r.task != cp && r.userId == userId
                    && r.launchMode != ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                cp = r.task;
                //Slog.i(TAG, "Comparing existing cls=" + r.task.intent.getComponent().flattenToShortString()
                //        + "/aff=" + r.task.affinity + " to new cls="
                //        + intent.getComponent().flattenToShortString() + "/aff=" + taskAffinity);
                if (r.task.affinity != null) {
                    if (r.task.affinity.equals(info.taskAffinity)) {
                        //Slog.i(TAG, "Found matching affinity!");
                        return r;
                    }
                } else if (r.task.intent != null
                        && r.task.intent.getComponent().equals(cls)) {
                    //Slog.i(TAG, "Found matching class!");
                    //dump();
                    //Slog.i(TAG, "For Intent " + intent + " bringing to top: " + r.intent);
                    return r;
                } else if (r.task.affinityIntent != null
                        && r.task.affinityIntent.getComponent().equals(cls)) {
                    //Slog.i(TAG, "Found matching class!");
                    //dump();
                    //Slog.i(TAG, "For Intent " + intent + " bringing to top: " + r.intent);
                    return r;
                }
            }
        }

        return null;
    }

    /**
     * Returns the first activity (starting from the top of the stack) that
     * is the same as the given activity.  Returns null if no such activity
     * is found.
     */
    private ActivityRecord findActivityLocked(Intent intent, ActivityInfo info) {
        ComponentName cls = intent.getComponent();
        if (info.targetActivity != null) {
            cls = new ComponentName(info.packageName, info.targetActivity);
        }
        final int userId = UserHandle.getUserId(info.applicationInfo.uid);

        final int N = mHistory.size();
        for (int i=(N-1); i>=0; i--) {
            ActivityRecord r = mHistory.get(i);
            if (!r.finishing) {
                if (r.intent.getComponent().equals(cls) && r.userId == userId) {
                    //Slog.i(TAG, "Found matching class!");
                    //dump();
                    //Slog.i(TAG, "For Intent " + intent + " bringing to top: " + r.intent);
                    return r;
                }
            }
        }

        return null;
    }

    final void showAskCompatModeDialogLocked(ActivityRecord r) {
        Message msg = Message.obtain();
        msg.what = ActivityManagerService.SHOW_COMPAT_MODE_DIALOG_MSG;
        msg.obj = r.task.askedCompatMode ? null : r;
        mService.mHandler.sendMessage(msg);
    }

    /*
     * Move the activities around in the stack to bring a user to the foreground.
     * @return whether there are any activities for the specified user.
     */
    final boolean switchUserLocked(int userId, UserStartedState uss) {
        mCurrentUser = userId;
        mStartingUsers.add(uss);

        // Only one activity? Nothing to do...
        if (mHistory.size() < 2)
            return false;

        boolean haveActivities = false;
        // Check if the top activity is from the new user.
        ActivityRecord top = mHistory.get(mHistory.size() - 1);
        if (top.userId == userId) return true;
        // Otherwise, move the user's activities to the top.
        int N = mHistory.size();
        int i = 0;
        while (i < N) {
            ActivityRecord r = mHistory.get(i);
            if (r.userId == userId) {
                ActivityRecord moveToTop = mHistory.remove(i);
                mHistory.add(moveToTop);
                // No need to check the top one now
                N--;
                haveActivities = true;
            } else {
                i++;
            }
        }
        // Transition from the old top to the new top
        resumeTopActivityLocked(top);
        return haveActivities;
    }

    final boolean realStartActivityLocked(ActivityRecord r,
            ProcessRecord app, boolean andResume, boolean checkConfig)
            throws RemoteException {

        r.startFreezingScreenLocked(app, 0);
        mService.mWindowManager.setAppVisibility(r.appToken, true);

        // schedule launch ticks to collect information about slow apps.
        r.startLaunchTickingLocked();

        // Have the window manager re-evaluate the orientation of
        // the screen based on the new activity order.  Note that
        // as a result of this, it can call back into the activity
        // manager with a new orientation.  We don't care about that,
        // because the activity is not currently running so we are
        // just restarting it anyway.
        if (checkConfig) {
            Configuration config = mService.mWindowManager.updateOrientationFromAppTokens(
                    mService.mConfiguration,
                    r.mayFreezeScreenLocked(app) ? r.appToken : null);
            mService.updateConfigurationLocked(config, r, false, false);
        }

        r.app = app;
        app.waitingToKill = null;
        r.launchCount++;
        r.lastLaunchTime = SystemClock.uptimeMillis();

        if (localLOGV) Slog.v(TAG, "Launching: " + r);

        int idx = app.activities.indexOf(r);
        if (idx < 0) {
            app.activities.add(r);
        }
        mService.updateLruProcessLocked(app, true);

        try {
            if (app.thread == null) {
                throw new RemoteException();
            }
            List<ResultInfo> results = null;
            List<Intent> newIntents = null;
            if (andResume) {
                results = r.results;
                newIntents = r.newIntents;
            }
            if (DEBUG_SWITCH) Slog.v(TAG, "Launching: " + r
                    + " icicle=" + r.icicle
                    + " with results=" + results + " newIntents=" + newIntents
                    + " andResume=" + andResume);
            if (andResume) {
                EventLog.writeEvent(EventLogTags.AM_RESTART_ACTIVITY,
                        r.userId, System.identityHashCode(r),
                        r.task.taskId, r.shortComponentName);
            }
            if (r.isHomeActivity) {
                mService.mHomeProcess = app;
            }
            mService.ensurePackageDexOpt(r.intent.getComponent().getPackageName());
            r.sleeping = false;
            r.forceNewConfig = false;
            showAskCompatModeDialogLocked(r);
            r.compat = mService.compatibilityInfoForPackageLocked(r.info.applicationInfo);
            String profileFile = null;
            ParcelFileDescriptor profileFd = null;
            boolean profileAutoStop = false;
            if (mService.mProfileApp != null && mService.mProfileApp.equals(app.processName)) {
                if (mService.mProfileProc == null || mService.mProfileProc == app) {
                    mService.mProfileProc = app;
                    profileFile = mService.mProfileFile;
                    profileFd = mService.mProfileFd;
                    profileAutoStop = mService.mAutoStopProfiler;
                }
            }
            app.hasShownUi = true;
            app.pendingUiClean = true;
            if (profileFd != null) {
                try {
                    profileFd = profileFd.dup();
                } catch (IOException e) {
                    profileFd = null;
                }
            }
            app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
                    System.identityHashCode(r), r.info,
                    new Configuration(mService.mConfiguration),
                    r.compat, r.icicle, results, newIntents, !andResume,
                    mService.isNextTransitionForward(), profileFile, profileFd,
                    profileAutoStop);
            
            if ((app.info.flags&ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
                // This may be a heavy-weight process!  Note that the package
                // manager will ensure that only activity can run in the main
                // process of the .apk, which is the only thing that will be
                // considered heavy-weight.
                if (app.processName.equals(app.info.packageName)) {
                    if (mService.mHeavyWeightProcess != null
                            && mService.mHeavyWeightProcess != app) {
                        Log.w(TAG, "Starting new heavy weight process " + app
                                + " when already running "
                                + mService.mHeavyWeightProcess);
                    }
                    mService.mHeavyWeightProcess = app;
                    Message msg = mService.mHandler.obtainMessage(
                            ActivityManagerService.POST_HEAVY_NOTIFICATION_MSG);
                    msg.obj = r;
                    mService.mHandler.sendMessage(msg);
                }
            }
            
        } catch (RemoteException e) {
            if (r.launchFailed) {
                // This is the second time we failed -- finish activity
                // and give up.
                Slog.e(TAG, "Second failure launching "
                      + r.intent.getComponent().flattenToShortString()
                      + ", giving up", e);
                mService.appDiedLocked(app, app.pid, app.thread);
                requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                        "2nd-crash", false);
                return false;
            }

            // This is the first time we failed -- restart process and
            // retry.
            app.activities.remove(r);
            throw e;
        }

        r.launchFailed = false;
        if (updateLRUListLocked(r)) {
            Slog.w(TAG, "Activity " + r
                  + " being launched, but already in LRU list");
        }

        if (andResume) {
            // As part of the process of launching, ActivityThread also performs
            // a resume.
            r.state = ActivityState.RESUMED;
            if (DEBUG_STATES) Slog.v(TAG, "Moving to RESUMED: " + r
                    + " (starting new instance)");
            r.stopped = false;
            mResumedActivity = r;
            r.task.touchActiveTime();
            if (mMainStack) {
                mService.addRecentTaskLocked(r.task);
            }
            completeResumeLocked(r);
            checkReadyForSleepLocked();
            if (DEBUG_SAVED_STATE) Slog.i(TAG, "Launch completed; removing icicle of " + r.icicle);
        } else {
            // This activity is not starting in the resumed state... which
            // should look like we asked it to pause+stop (but remain visible),
            // and it has done so and reported back the current icicle and
            // other state.
            if (DEBUG_STATES) Slog.v(TAG, "Moving to STOPPED: " + r
                    + " (starting in stopped state)");
            r.state = ActivityState.STOPPED;
            r.stopped = true;
        }

        // Launch the new version setup screen if needed.  We do this -after-
        // launching the initial activity (that is, home), so that it can have
        // a chance to initialize itself while in the background, making the
        // switch back to it faster and look better.
        if (mMainStack) {
            mService.startSetupActivityLocked();
        }
        
        return true;
    }

    private final void startSpecificActivityLocked(ActivityRecord r,
            boolean andResume, boolean checkConfig) {
        // Is this activity's application already running?
        ProcessRecord app = mService.getProcessRecordLocked(r.processName,
                r.info.applicationInfo.uid);

        if (r.launchTime == 0) {
            r.launchTime = SystemClock.uptimeMillis();
            if (mInitialStartTime == 0) {
                mInitialStartTime = r.launchTime;
            }
        } else if (mInitialStartTime == 0) {
            mInitialStartTime = SystemClock.uptimeMillis();
        }
        
        if (app != null && app.thread != null) {
            try {
                app.addPackage(r.info.packageName);
                realStartActivityLocked(r, app, andResume, checkConfig);
                return;
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception when starting activity "
                        + r.intent.getComponent().flattenToShortString(), e);
            }

            // If a dead object exception was thrown -- fall through to
            // restart the application.
        }

        mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
                "activity", r.intent.getComponent(), false, false);
    }
    
    void stopIfSleepingLocked() {
        if (mService.isSleeping()) {
            if (!mGoingToSleep.isHeld()) {
                mGoingToSleep.acquire();
                if (mLaunchingActivity.isHeld()) {
                    mLaunchingActivity.release();
                    mService.mHandler.removeMessages(LAUNCH_TIMEOUT_MSG);
                }
            }
            mHandler.removeMessages(SLEEP_TIMEOUT_MSG);
            Message msg = mHandler.obtainMessage(SLEEP_TIMEOUT_MSG);
            mHandler.sendMessageDelayed(msg, SLEEP_TIMEOUT);
            checkReadyForSleepLocked();
        }
    }

    void awakeFromSleepingLocked() {
        mHandler.removeMessages(SLEEP_TIMEOUT_MSG);
        mSleepTimeout = false;
        if (mGoingToSleep.isHeld()) {
            mGoingToSleep.release();
        }
        // Ensure activities are no longer sleeping.
        for (int i=mHistory.size()-1; i>=0; i--) {
            ActivityRecord r = mHistory.get(i);
            r.setSleeping(false);
        }
        mGoingToSleepActivities.clear();
    }

    void activitySleptLocked(ActivityRecord r) {
        mGoingToSleepActivities.remove(r);
        checkReadyForSleepLocked();
    }

    void checkReadyForSleepLocked() {
        if (!mService.isSleeping()) {
            // Do not care.
            return;
        }

        if (!mSleepTimeout) {
            if (mResumedActivity != null) {
                // Still have something resumed; can't sleep until it is paused.
                if (DEBUG_PAUSE) Slog.v(TAG, "Sleep needs to pause " + mResumedActivity);
                if (DEBUG_USER_LEAVING) Slog.v(TAG, "Sleep => pause with userLeaving=false");
                startPausingLocked(false, true);
                return;
            }
            if (mPausingActivity != null) {
                // Still waiting for something to pause; can't sleep yet.
                if (DEBUG_PAUSE) Slog.v(TAG, "Sleep still waiting to pause " + mPausingActivity);
                return;
            }

            if (mStoppingActivities.size() > 0) {
                // Still need to tell some activities to stop; can't sleep yet.
                if (DEBUG_PAUSE) Slog.v(TAG, "Sleep still need to stop "
                        + mStoppingActivities.size() + " activities");
                scheduleIdleLocked();
                return;
            }

            ensureActivitiesVisibleLocked(null, 0);

            // Make sure any stopped but visible activities are now sleeping.
            // This ensures that the activity's onStop() is called.
            for (int i=mHistory.size()-1; i>=0; i--) {
                ActivityRecord r = mHistory.get(i);
                if (r.state == ActivityState.STOPPING || r.state == ActivityState.STOPPED) {
                    r.setSleeping(true);
                }
            }

            if (mGoingToSleepActivities.size() > 0) {
                // Still need to tell some activities to sleep; can't sleep yet.
                if (DEBUG_PAUSE) Slog.v(TAG, "Sleep still need to sleep "
                        + mGoingToSleepActivities.size() + " activities");
                return;
            }
        }

        mHandler.removeMessages(SLEEP_TIMEOUT_MSG);

        if (mGoingToSleep.isHeld()) {
            mGoingToSleep.release();
        }
        if (mService.mShuttingDown) {
            mService.notifyAll();
        }
    }

    public final Bitmap screenshotActivities(ActivityRecord who) {
        if (who.noDisplay) {
            return null;
        }
        
        Resources res = mService.mContext.getResources();
        int w = mThumbnailWidth;
        int h = mThumbnailHeight;
        if (w < 0) {
            mThumbnailWidth = w =
                res.getDimensionPixelSize(com.android.internal.R.dimen.thumbnail_width);
            mThumbnailHeight = h =
                res.getDimensionPixelSize(com.android.internal.R.dimen.thumbnail_height);
        }

        if (w > 0) {
            if (who != mLastScreenshotActivity || mLastScreenshotBitmap == null
                    || mLastScreenshotBitmap.getWidth() != w
                    || mLastScreenshotBitmap.getHeight() != h) {
                mLastScreenshotActivity = who;
                mLastScreenshotBitmap = mService.mWindowManager.screenshotApplications(
                        who.appToken, Display.DEFAULT_DISPLAY, w, h);
            }
            if (mLastScreenshotBitmap != null) {
                return mLastScreenshotBitmap.copy(Config.ARGB_8888, true);
            }
        }
        return null;
    }

    private final void startPausingLocked(boolean userLeaving, boolean uiSleeping) {
        if (mPausingActivity != null) {
            RuntimeException e = new RuntimeException();
            Slog.e(TAG, "Trying to pause when pause is already pending for "
                  + mPausingActivity, e);
        }
        ActivityRecord prev = mResumedActivity;
        if (prev == null) {
            RuntimeException e = new RuntimeException();
            Slog.e(TAG, "Trying to pause when nothing is resumed", e);
            resumeTopActivityLocked(null);
            return;
        }
        if (DEBUG_STATES) Slog.v(TAG, "Moving to PAUSING: " + prev);
        else if (DEBUG_PAUSE) Slog.v(TAG, "Start pausing: " + prev);
        mResumedActivity = null;
        mPausingActivity = prev;
        mLastPausedActivity = prev;
        prev.state = ActivityState.PAUSING;
        prev.task.touchActiveTime();
        prev.updateThumbnail(screenshotActivities(prev), null);

        mService.updateCpuStats();
        
        if (prev.app != null && prev.app.thread != null) {
            if (DEBUG_PAUSE) Slog.v(TAG, "Enqueueing pending pause: " + prev);
            try {
                EventLog.writeEvent(EventLogTags.AM_PAUSE_ACTIVITY,
                        prev.userId, System.identityHashCode(prev),
                        prev.shortComponentName);
                prev.app.thread.schedulePauseActivity(prev.appToken, prev.finishing,
                        userLeaving, prev.configChangeFlags);
                if (mMainStack) {
                    mService.updateUsageStats(prev, false);
                }
            } catch (Exception e) {
                // Ignore exception, if process died other code will cleanup.
                Slog.w(TAG, "Exception thrown during pause", e);
                mPausingActivity = null;
                mLastPausedActivity = null;
            }
        } else {
            mPausingActivity = null;
            mLastPausedActivity = null;
        }

        // If we are not going to sleep, we want to ensure the device is
        // awake until the next activity is started.
        if (!mService.mSleeping && !mService.mShuttingDown) {
            mLaunchingActivity.acquire();
            if (!mHandler.hasMessages(LAUNCH_TIMEOUT_MSG)) {
                // To be safe, don't allow the wake lock to be held for too long.
                Message msg = mHandler.obtainMessage(LAUNCH_TIMEOUT_MSG);
                mHandler.sendMessageDelayed(msg, LAUNCH_TIMEOUT);
            }
        }


        if (mPausingActivity != null) {
            // Have the window manager pause its key dispatching until the new
            // activity has started.  If we're pausing the activity just because
            // the screen is being turned off and the UI is sleeping, don't interrupt
            // key dispatch; the same activity will pick it up again on wakeup.
            if (!uiSleeping) {
                prev.pauseKeyDispatchingLocked();
            } else {
                if (DEBUG_PAUSE) Slog.v(TAG, "Key dispatch not paused for screen off");
            }

            // Schedule a pause timeout in case the app doesn't respond.
            // We don't give it much time because this directly impacts the
            // responsiveness seen by the user.
            Message msg = mHandler.obtainMessage(PAUSE_TIMEOUT_MSG);
            msg.obj = prev;
            prev.pauseTime = SystemClock.uptimeMillis();
            mHandler.sendMessageDelayed(msg, PAUSE_TIMEOUT);
            if (DEBUG_PAUSE) Slog.v(TAG, "Waiting for pause to complete...");
        } else {
            // This activity failed to schedule the
            // pause, so just treat it as being paused now.
            if (DEBUG_PAUSE) Slog.v(TAG, "Activity not running, resuming next.");
            resumeTopActivityLocked(null);
        }
    }

    final void activityResumed(IBinder token) {
        ActivityRecord r = null;

        synchronized (mService) {
            int index = indexOfTokenLocked(token);
            if (index >= 0) {
                r = mHistory.get(index);
                if (DEBUG_SAVED_STATE) Slog.i(TAG, "Resumed activity; dropping state of: " + r);
                r.icicle = null;
                r.haveState = false;
            }
        }
    }

    final void activityPaused(IBinder token, boolean timeout) {
        if (DEBUG_PAUSE) Slog.v(
            TAG, "Activity paused: token=" + token + ", timeout=" + timeout);

        ActivityRecord r = null;

        synchronized (mService) {
            int index = indexOfTokenLocked(token);
            if (index >= 0) {
                r = mHistory.get(index);
                mHandler.removeMessages(PAUSE_TIMEOUT_MSG, r);
                if (mPausingActivity == r) {
                    if (DEBUG_STATES) Slog.v(TAG, "Moving to PAUSED: " + r
                            + (timeout ? " (due to timeout)" : " (pause complete)"));
                    r.state = ActivityState.PAUSED;
                    completePauseLocked();
                } else {
                    EventLog.writeEvent(EventLogTags.AM_FAILED_TO_PAUSE,
                            r.userId, System.identityHashCode(r), r.shortComponentName, 
                            mPausingActivity != null
                                ? mPausingActivity.shortComponentName : "(none)");
                }
            }
        }
    }

    final void activityStoppedLocked(ActivityRecord r, Bundle icicle, Bitmap thumbnail,
            CharSequence description) {
        if (r.state != ActivityState.STOPPING) {
            Slog.i(TAG, "Activity reported stop, but no longer stopping: " + r);
            mHandler.removeMessages(STOP_TIMEOUT_MSG, r);
            return;
        }
        if (DEBUG_SAVED_STATE) Slog.i(TAG, "Saving icicle of " + r + ": " + icicle);
        if (icicle != null) {
            // If icicle is null, this is happening due to a timeout, so we
            // haven't really saved the state.
            r.icicle = icicle;
            r.haveState = true;
            r.launchCount = 0;
            r.updateThumbnail(thumbnail, description);
        }
        if (!r.stopped) {
            if (DEBUG_STATES) Slog.v(TAG, "Moving to STOPPED: " + r + " (stop complete)");
            mHandler.removeMessages(STOP_TIMEOUT_MSG, r);
            r.stopped = true;
            r.state = ActivityState.STOPPED;
            if (r.finishing) {
                r.clearOptionsLocked();
            } else {
                if (r.configDestroy) {
                    destroyActivityLocked(r, true, false, "stop-config");
                    resumeTopActivityLocked(null);
                } else {
                    // Now that this process has stopped, we may want to consider
                    // it to be the previous app to try to keep around in case
                    // the user wants to return to it.
                    ProcessRecord fgApp = null;
                    if (mResumedActivity != null) {
                        fgApp = mResumedActivity.app;
                    } else if (mPausingActivity != null) {
                        fgApp = mPausingActivity.app;
                    }
                    if (r.app != null && fgApp != null && r.app != fgApp
                            && r.lastVisibleTime > mService.mPreviousProcessVisibleTime
                            && r.app != mService.mHomeProcess) {
                        mService.mPreviousProcess = r.app;
                        mService.mPreviousProcessVisibleTime = r.lastVisibleTime;
                    }
                }
            }
        }
    }

    private final void completePauseLocked() {
        ActivityRecord prev = mPausingActivity;
        if (DEBUG_PAUSE) Slog.v(TAG, "Complete pause: " + prev);
        
        if (prev != null) {
            if (prev.finishing) {
                if (DEBUG_PAUSE) Slog.v(TAG, "Executing finish of activity: " + prev);
                prev = finishCurrentActivityLocked(prev, FINISH_AFTER_VISIBLE, false);
            } else if (prev.app != null) {
                if (DEBUG_PAUSE) Slog.v(TAG, "Enqueueing pending stop: " + prev);
                if (prev.waitingVisible) {
                    prev.waitingVisible = false;
                    mWaitingVisibleActivities.remove(prev);
                    if (DEBUG_SWITCH || DEBUG_PAUSE) Slog.v(
                            TAG, "Complete pause, no longer waiting: " + prev);
                }
                if (prev.configDestroy) {
                    // The previous is being paused because the configuration
                    // is changing, which means it is actually stopping...
                    // To juggle the fact that we are also starting a new
                    // instance right now, we need to first completely stop
                    // the current instance before starting the new one.
                    if (DEBUG_PAUSE) Slog.v(TAG, "Destroying after pause: " + prev);
                    destroyActivityLocked(prev, true, false, "pause-config");
                } else {
                    mStoppingActivities.add(prev);
                    if (mStoppingActivities.size() > 3) {
                        // If we already have a few activities waiting to stop,
                        // then give up on things going idle and start clearing
                        // them out.
                        if (DEBUG_PAUSE) Slog.v(TAG, "To many pending stops, forcing idle");
                        scheduleIdleLocked();
                    } else {
                        checkReadyForSleepLocked();
                    }
                }
            } else {
                if (DEBUG_PAUSE) Slog.v(TAG, "App died during pause, not stopping: " + prev);
                prev = null;
            }
            mPausingActivity = null;
        }

        if (!mService.isSleeping()) {
            resumeTopActivityLocked(prev);
        } else {
            checkReadyForSleepLocked();
            ActivityRecord top = topRunningActivityLocked(null);
            if (top == null || (prev != null && top != prev)) {
                // If there are no more activities available to run,
                // do resume anyway to start something.  Also if the top
                // activity on the stack is not the just paused activity,
                // we need to go ahead and resume it to ensure we complete
                // an in-flight app switch.
                resumeTopActivityLocked(null);
            }
        }
        
        if (prev != null) {
            prev.resumeKeyDispatchingLocked();
        }

        if (prev.app != null && prev.cpuTimeAtResume > 0
                && mService.mBatteryStatsService.isOnBattery()) {
            long diff = 0;
            synchronized (mService.mProcessStatsThread) {
                diff = mService.mProcessStats.getCpuTimeForPid(prev.app.pid)
                        - prev.cpuTimeAtResume;
            }
            if (diff > 0) {
                BatteryStatsImpl bsi = mService.mBatteryStatsService.getActiveStatistics();
                synchronized (bsi) {
                    BatteryStatsImpl.Uid.Proc ps =
                            bsi.getProcessStatsLocked(prev.info.applicationInfo.uid,
                            prev.info.packageName);
                    if (ps != null) {
                        ps.addForegroundTimeLocked(diff);
                    }
                }
            }
        }
        prev.cpuTimeAtResume = 0; // reset it
    }

    /**
     * Once we know that we have asked an application to put an activity in
     * the resumed state (either by launching it or explicitly telling it),
     * this function updates the rest of our state to match that fact.
     */
    private final void completeResumeLocked(ActivityRecord next) {
        next.idle = false;
        next.results = null;
        next.newIntents = null;

        // schedule an idle timeout in case the app doesn't do it for us.
        Message msg = mHandler.obtainMessage(IDLE_TIMEOUT_MSG);
        msg.obj = next;
        mHandler.sendMessageDelayed(msg, IDLE_TIMEOUT);

        if (false) {
            // The activity was never told to pause, so just keep
            // things going as-is.  To maintain our own state,
            // we need to emulate it coming back and saying it is
            // idle.
            msg = mHandler.obtainMessage(IDLE_NOW_MSG);
            msg.obj = next;
            mHandler.sendMessage(msg);
        }

        if (mMainStack) {
            mService.reportResumedActivityLocked(next);
        }

        if (mMainStack) {
            mService.setFocusedActivityLocked(next);
        }
        next.resumeKeyDispatchingLocked();
        ensureActivitiesVisibleLocked(null, 0);
        mService.mWindowManager.executeAppTransition();
        mNoAnimActivities.clear();

        // Mark the point when the activity is resuming
        // TODO: To be more accurate, the mark should be before the onCreate,
        //       not after the onResume. But for subsequent starts, onResume is fine.
        if (next.app != null) {
            synchronized (mService.mProcessStatsThread) {
                next.cpuTimeAtResume = mService.mProcessStats.getCpuTimeForPid(next.app.pid);
            }
        } else {
            next.cpuTimeAtResume = 0; // Couldn't get the cpu time of process
        }
        updatePrivacyGuardNotificationLocked(next);
    }

    /**
     * Make sure that all activities that need to be visible (that is, they
     * currently can be seen by the user) actually are.
     */
    final void ensureActivitiesVisibleLocked(ActivityRecord top,
            ActivityRecord starting, String onlyThisProcess, int configChanges) {
        if (DEBUG_VISBILITY) Slog.v(
                TAG, "ensureActivitiesVisible behind " + top
                + " configChanges=0x" + Integer.toHexString(configChanges));

        // If the top activity is not fullscreen, then we need to
        // make sure any activities under it are now visible.
        final int count = mHistory.size();
        int i = count-1;
        while (mHistory.get(i) != top) {
            i--;
        }
        ActivityRecord r;
        boolean behindFullscreen = false;
        for (; i>=0; i--) {
            r = mHistory.get(i);
            if (DEBUG_VISBILITY) Slog.v(
                    TAG, "Make visible? " + r + " finishing=" + r.finishing
                    + " state=" + r.state);
            if (r.finishing) {
                continue;
            }
            
            final boolean doThisProcess = onlyThisProcess == null
                    || onlyThisProcess.equals(r.processName);
            
            // First: if this is not the current activity being started, make
            // sure it matches the current configuration.
            if (r != starting && doThisProcess) {
                ensureActivityConfigurationLocked(r, 0);
            }
            
            if (r.app == null || r.app.thread == null) {
                if (onlyThisProcess == null
                        || onlyThisProcess.equals(r.processName)) {
                    // This activity needs to be visible, but isn't even
                    // running...  get it started, but don't resume it
                    // at this point.
                    if (DEBUG_VISBILITY) Slog.v(
                            TAG, "Start and freeze screen for " + r);
                    if (r != starting) {
                        r.startFreezingScreenLocked(r.app, configChanges);
                    }
                    if (!r.visible) {
                        if (DEBUG_VISBILITY) Slog.v(
                                TAG, "Starting and making visible: " + r);
                        mService.mWindowManager.setAppVisibility(r.appToken, true);
                    }
                    if (r != starting) {
                        startSpecificActivityLocked(r, false, false);
                    }
                }

            } else if (r.visible) {
                // If this activity is already visible, then there is nothing
                // else to do here.
                if (DEBUG_VISBILITY) Slog.v(
                        TAG, "Skipping: already visible at " + r);
                r.stopFreezingScreenLocked(false);

            } else if (onlyThisProcess == null) {
                // This activity is not currently visible, but is running.
                // Tell it to become visible.
                r.visible = true;
                if (r.state != ActivityState.RESUMED && r != starting) {
                    // If this activity is paused, tell it
                    // to now show its window.
                    if (DEBUG_VISBILITY) Slog.v(
                            TAG, "Making visible and scheduling visibility: " + r);
                    try {
                        mService.mWindowManager.setAppVisibility(r.appToken, true);
                        r.sleeping = false;
                        r.app.pendingUiClean = true;
                        r.app.thread.scheduleWindowVisibility(r.appToken, true);
                        r.stopFreezingScreenLocked(false);
                    } catch (Exception e) {
                        // Just skip on any failure; we'll make it
                        // visible when it next restarts.
                        Slog.w(TAG, "Exception thrown making visibile: "
                                + r.intent.getComponent(), e);
                    }
                }
            }

            // Aggregate current change flags.
            configChanges |= r.configChangeFlags;

            if (r.fullscreen) {
                // At this point, nothing else needs to be shown
                if (DEBUG_VISBILITY) Slog.v(
                        TAG, "Stopping: fullscreen at " + r);
                behindFullscreen = true;
                i--;
                break;
            }
        }

        // Now for any activities that aren't visible to the user, make
        // sure they no longer are keeping the screen frozen.
        while (i >= 0) {
            r = mHistory.get(i);
            if (DEBUG_VISBILITY) Slog.v(
                    TAG, "Make invisible? " + r + " finishing=" + r.finishing
                    + " state=" + r.state
                    + " behindFullscreen=" + behindFullscreen);
            if (!r.finishing) {
                if (behindFullscreen) {
                    if (r.visible) {
                        if (DEBUG_VISBILITY) Slog.v(
                                TAG, "Making invisible: " + r);
                        r.visible = false;
                        try {
                            mService.mWindowManager.setAppVisibility(r.appToken, false);
                            if ((r.state == ActivityState.STOPPING
                                    || r.state == ActivityState.STOPPED)
                                    && r.app != null && r.app.thread != null) {
                                if (DEBUG_VISBILITY) Slog.v(
                                        TAG, "Scheduling invisibility: " + r);
                                r.app.thread.scheduleWindowVisibility(r.appToken, false);
                            }
                        } catch (Exception e) {
                            // Just skip on any failure; we'll make it
                            // visible when it next restarts.
                            Slog.w(TAG, "Exception thrown making hidden: "
                                    + r.intent.getComponent(), e);
                        }
                    } else {
                        if (DEBUG_VISBILITY) Slog.v(
                                TAG, "Already invisible: " + r);
                    }
                } else if (r.fullscreen) {
                    if (DEBUG_VISBILITY) Slog.v(
                            TAG, "Now behindFullscreen: " + r);
                    behindFullscreen = true;
                }
            }
            i--;
        }
    }

    /**
     * Version of ensureActivitiesVisible that can easily be called anywhere.
     */
    final void ensureActivitiesVisibleLocked(ActivityRecord starting,
            int configChanges) {
        ActivityRecord r = topRunningActivityLocked(null);
        if (r != null) {
            ensureActivitiesVisibleLocked(r, starting, null, configChanges);
        }
    }
    
    /**
     * Ensure that the top activity in the stack is resumed.
     *
     * @param prev The previously resumed activity, for when in the process
     * of pausing; can be null to call from elsewhere.
     *
     * @return Returns true if something is being resumed, or false if
     * nothing happened.
     */
    final boolean resumeTopActivityLocked(ActivityRecord prev) {
        return resumeTopActivityLocked(prev, null);
    }

    final boolean resumeTopActivityLocked(ActivityRecord prev, Bundle options) {

        mPm.cpuBoost(1500000);

        // Find the first activity that is not finishing.
        ActivityRecord next = topRunningActivityLocked(null);

        // Remember how we'll process this pause/resume situation, and ensure
        // that the state is reset however we wind up proceeding.
        final boolean userLeaving = mUserLeaving;
        mUserLeaving = false;

        if (next == null) {
            // There are no more activities!  Let's just start up the
            // Launcher...
            if (mMainStack) {
                ActivityOptions.abort(options);
                return mService.startHomeActivityLocked(mCurrentUser);
            }
        }

        next.delayedResume = false;
        
        // If the top activity is the resumed one, nothing to do.
        if (mResumedActivity == next && next.state == ActivityState.RESUMED) {
            // Make sure we have executed any pending transitions, since there
            // should be nothing left to do at this point.
            mService.mWindowManager.executeAppTransition();
            mNoAnimActivities.clear();
            ActivityOptions.abort(options);
            return false;
        }

        // If we are sleeping, and there is no resumed activity, and the top
        // activity is paused, well that is the state we want.
        if ((mService.mSleeping || mService.mShuttingDown)
                && mLastPausedActivity == next
                && (next.state == ActivityState.PAUSED
                    || next.state == ActivityState.STOPPED
                    || next.state == ActivityState.STOPPING)) {
            // Make sure we have executed any pending transitions, since there
            // should be nothing left to do at this point.
            mService.mWindowManager.executeAppTransition();
            mNoAnimActivities.clear();
            ActivityOptions.abort(options);
            return false;
        }

        // Make sure that the user who owns this activity is started.  If not,
        // we will just leave it as is because someone should be bringing
        // another user's activities to the top of the stack.
        if (mService.mStartedUsers.get(next.userId) == null) {
            Slog.w(TAG, "Skipping resume of top activity " + next
                    + ": user " + next.userId + " is stopped");
            return false;
        }

        // The activity may be waiting for stop, but that is no longer
        // appropriate for it.
        mStoppingActivities.remove(next);
        mGoingToSleepActivities.remove(next);
        next.sleeping = false;
        mWaitingVisibleActivities.remove(next);

        next.updateOptionsLocked(options);

        if (DEBUG_SWITCH) Slog.v(TAG, "Resuming " + next);

        if (mActivityTrigger != null) {
            mActivityTrigger.activityResumeTrigger(next.intent);
        }

        // If we are currently pausing an activity, then don't do anything
        // until that is done.
        if (mPausingActivity != null) {
            if (DEBUG_SWITCH || DEBUG_PAUSE) Slog.v(TAG,
                    "Skip resume: pausing=" + mPausingActivity);
            return false;
        }

        // Okay we are now going to start a switch, to 'next'.  We may first
        // have to pause the current activity, but this is an important point
        // where we have decided to go to 'next' so keep track of that.
        // XXX "App Redirected" dialog is getting too many false positives
        // at this point, so turn off for now.
        if (false) {
            if (mLastStartedActivity != null && !mLastStartedActivity.finishing) {
                long now = SystemClock.uptimeMillis();
                final boolean inTime = mLastStartedActivity.startTime != 0
                        && (mLastStartedActivity.startTime + START_WARN_TIME) >= now;
                final int lastUid = mLastStartedActivity.info.applicationInfo.uid;
                final int nextUid = next.info.applicationInfo.uid;
                if (inTime && lastUid != nextUid
                        && lastUid != next.launchedFromUid
                        && mService.checkPermission(
                                android.Manifest.permission.STOP_APP_SWITCHES,
                                -1, next.launchedFromUid)
                        != PackageManager.PERMISSION_GRANTED) {
                    mService.showLaunchWarningLocked(mLastStartedActivity, next);
                } else {
                    next.startTime = now;
                    mLastStartedActivity = next;
                }
            } else {
                next.startTime = SystemClock.uptimeMillis();
                mLastStartedActivity = next;
            }
        }
        
        // We need to start pausing the current activity so the top one
        // can be resumed...
        if (mResumedActivity != null) {
            if (DEBUG_SWITCH) Slog.v(TAG, "Skip resume: need to start pausing");
            // At this point we want to put the upcoming activity's process
            // at the top of the LRU list, since we know we will be needing it
            // very soon and it would be a waste to let it get killed if it
            // happens to be sitting towards the end.
            if (next.app != null && next.app.thread != null) {
                // No reason to do full oom adj update here; we'll let that
                // happen whenever it needs to later.
                mService.updateLruProcessLocked(next.app, false);
            }
            startPausingLocked(userLeaving, false);
            return true;
        }

        // If the most recent activity was noHistory but was only stopped rather
        // than stopped+finished because the device went to sleep, we need to make
        // sure to finish it as we're making a new activity topmost.
        final ActivityRecord last = mLastPausedActivity;
        if (mService.mSleeping && last != null && !last.finishing) {
            if ((last.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
                    || (last.info.flags&ActivityInfo.FLAG_NO_HISTORY) != 0) {
                if (DEBUG_STATES) {
                    Slog.d(TAG, "no-history finish of " + last + " on new resume");
                }
                requestFinishActivityLocked(last.appToken, Activity.RESULT_CANCELED, null,
                        "no-history", false);
            }
        }

        if (prev != null && prev != next) {
            if (!prev.waitingVisible && next != null && !next.nowVisible) {
                prev.waitingVisible = true;
                mWaitingVisibleActivities.add(prev);
                if (DEBUG_SWITCH) Slog.v(
                        TAG, "Resuming top, waiting visible to hide: " + prev);
            } else {
                // The next activity is already visible, so hide the previous
                // activity's windows right now so we can show the new one ASAP.
                // We only do this if the previous is finishing, which should mean
                // it is on top of the one being resumed so hiding it quickly
                // is good.  Otherwise, we want to do the normal route of allowing
                // the resumed activity to be shown so we can decide if the
                // previous should actually be hidden depending on whether the
                // new one is found to be full-screen or not.
                if (prev.finishing) {
                    mService.mWindowManager.setAppVisibility(prev.appToken, false);
                    if (DEBUG_SWITCH) Slog.v(TAG, "Not waiting for visible to hide: "
                            + prev + ", waitingVisible="
                            + (prev != null ? prev.waitingVisible : null)
                            + ", nowVisible=" + next.nowVisible);
                } else {
                    if (DEBUG_SWITCH) Slog.v(TAG, "Previous already visible but still waiting to hide: "
                        + prev + ", waitingVisible="
                        + (prev != null ? prev.waitingVisible : null)
                        + ", nowVisible=" + next.nowVisible);
                }
            }
        }

        // Launching this app's activity, make sure the app is no longer
        // considered stopped.
        try {
            AppGlobals.getPackageManager().setPackageStoppedState(
                    next.packageName, false, next.userId); /* TODO: Verify if correct userid */
        } catch (RemoteException e1) {
        } catch (IllegalArgumentException e) {
            Slog.w(TAG, "Failed trying to unstop package "
                    + next.packageName + ": " + e);
        }

        // We are starting up the next activity, so tell the window manager
        // that the previous one will be hidden soon.  This way it can know
        // to ignore it when computing the desired screen orientation.
        boolean noAnim = false;
        if (prev != null) {
            if (prev.finishing) {
                if (DEBUG_TRANSITION) Slog.v(TAG,
                        "Prepare close transition: prev=" + prev);
                if (mNoAnimActivities.contains(prev)) {
                    mService.mWindowManager.prepareAppTransition(
                            AppTransition.TRANSIT_NONE, false);
                } else {
                    mService.mWindowManager.prepareAppTransition(prev.task == next.task
                            ? AppTransition.TRANSIT_ACTIVITY_CLOSE
                            : AppTransition.TRANSIT_TASK_CLOSE, false);
                }
                mService.mWindowManager.setAppWillBeHidden(prev.appToken);
                mService.mWindowManager.setAppVisibility(prev.appToken, false);
            } else {
                if (DEBUG_TRANSITION) Slog.v(TAG,
                        "Prepare open transition: prev=" + prev);
                if (mNoAnimActivities.contains(next)) {
                    noAnim = true;
                    mService.mWindowManager.prepareAppTransition(
                            AppTransition.TRANSIT_NONE, false);
                } else {
                    mService.mWindowManager.prepareAppTransition(prev.task == next.task
                            ? AppTransition.TRANSIT_ACTIVITY_OPEN
                            : AppTransition.TRANSIT_TASK_OPEN, false);
                }
            }
            if (false) {
                mService.mWindowManager.setAppWillBeHidden(prev.appToken);
                mService.mWindowManager.setAppVisibility(prev.appToken, false);
            }
        } else if (mHistory.size() > 1) {
            if (DEBUG_TRANSITION) Slog.v(TAG,
                    "Prepare open transition: no previous");
            if (mNoAnimActivities.contains(next)) {
                noAnim = true;
                mService.mWindowManager.prepareAppTransition(
                        AppTransition.TRANSIT_NONE, false);
            } else {
                mService.mWindowManager.prepareAppTransition(
                        AppTransition.TRANSIT_ACTIVITY_OPEN, false);
            }
        }
        if (!noAnim) {
            next.applyOptionsLocked();
        } else {
            next.clearOptionsLocked();
        }

        if (next.app != null && next.app.thread != null) {
            if (DEBUG_SWITCH) Slog.v(TAG, "Resume running: " + next);

            // This activity is now becoming visible.
            mService.mWindowManager.setAppVisibility(next.appToken, true);

            // schedule launch ticks to collect information about slow apps.
            next.startLaunchTickingLocked();

            ActivityRecord lastResumedActivity = mResumedActivity;
            ActivityState lastState = next.state;

            mService.updateCpuStats();
            
            if (DEBUG_STATES) Slog.v(TAG, "Moving to RESUMED: " + next + " (in existing)");
            next.state = ActivityState.RESUMED;
            mResumedActivity = next;
            next.task.touchActiveTime();
            if (mMainStack) {
                mService.addRecentTaskLocked(next.task);
            }
            mService.updateLruProcessLocked(next.app, true);
            updateLRUListLocked(next);

            // Have the window manager re-evaluate the orientation of
            // the screen based on the new activity order.
            boolean updated = false;
            if (mMainStack) {
                synchronized (mService) {
                    Configuration config = mService.mWindowManager.updateOrientationFromAppTokens(
                            mService.mConfiguration,
                            next.mayFreezeScreenLocked(next.app) ? next.appToken : null);
                    if (config != null) {
                        next.frozenBeforeDestroy = true;
                    }
                    updated = mService.updateConfigurationLocked(config, next, false, false);
                }
            }
            if (!updated) {
                // The configuration update wasn't able to keep the existing
                // instance of the activity, and instead started a new one.
                // We should be all done, but let's just make sure our activity
                // is still at the top and schedule another run if something
                // weird happened.
                ActivityRecord nextNext = topRunningActivityLocked(null);
                if (DEBUG_SWITCH) Slog.i(TAG,
                        "Activity config changed during resume: " + next
                        + ", new next: " + nextNext);
                if (nextNext != next) {
                    // Do over!
                    mHandler.sendEmptyMessage(RESUME_TOP_ACTIVITY_MSG);
                }
                if (mMainStack) {
                    mService.setFocusedActivityLocked(next);
                }
                ensureActivitiesVisibleLocked(null, 0);
                mService.mWindowManager.executeAppTransition();
                mNoAnimActivities.clear();
                return true;
            }
            
            try {
                // Deliver all pending results.
                ArrayList a = next.results;
                if (a != null) {
                    final int N = a.size();
                    if (!next.finishing && N > 0) {
                        if (DEBUG_RESULTS) Slog.v(
                                TAG, "Delivering results to " + next
                                + ": " + a);
                        next.app.thread.scheduleSendResult(next.appToken, a);
                    }
                }

                if (next.newIntents != null) {
                    next.app.thread.scheduleNewIntent(next.newIntents, next.appToken);
                }

                EventLog.writeEvent(EventLogTags.AM_RESUME_ACTIVITY,
                        next.userId, System.identityHashCode(next),
                        next.task.taskId, next.shortComponentName);
                
                next.sleeping = false;
                showAskCompatModeDialogLocked(next);
                next.app.pendingUiClean = true;
                next.app.thread.scheduleResumeActivity(next.appToken,
                        mService.isNextTransitionForward());
                
                checkReadyForSleepLocked();

            } catch (Exception e) {
                // Whoops, need to restart this activity!
                if (DEBUG_STATES) Slog.v(TAG, "Resume failed; resetting state to "
                        + lastState + ": " + next);
                next.state = lastState;
                mResumedActivity = lastResumedActivity;
                Slog.i(TAG, "Restarting because process died: " + next);
                if (!next.hasBeenLaunched) {
                    next.hasBeenLaunched = true;
                } else {
                    if (SHOW_APP_STARTING_PREVIEW && mMainStack) {
                        mService.mWindowManager.setAppStartingWindow(
                                next.appToken, next.packageName, next.theme,
                                mService.compatibilityInfoForPackageLocked(
                                        next.info.applicationInfo),
                                next.nonLocalizedLabel,
                                next.labelRes, next.icon, next.windowFlags,
                                null, true);
                    }
                }
                startSpecificActivityLocked(next, true, false);
                return true;
            }

            // From this point on, if something goes wrong there is no way
            // to recover the activity.
            try {
                next.visible = true;
                completeResumeLocked(next);
            } catch (Exception e) {
                // If any exception gets thrown, toss away this
                // activity and try the next one.
                Slog.w(TAG, "Exception thrown during resume of " + next, e);
                requestFinishActivityLocked(next.appToken, Activity.RESULT_CANCELED, null,
                        "resume-exception", true);
                return true;
            }
            next.stopped = false;

        } else {
            // Whoops, need to restart this activity!
            if (!next.hasBeenLaunched) {
                next.hasBeenLaunched = true;
            } else {
                if (SHOW_APP_STARTING_PREVIEW) {
                    mService.mWindowManager.setAppStartingWindow(
                            next.appToken, next.packageName, next.theme,
                            mService.compatibilityInfoForPackageLocked(
                                    next.info.applicationInfo),
                            next.nonLocalizedLabel,
                            next.labelRes, next.icon, next.windowFlags,
                            null, true);
                }
                if (DEBUG_SWITCH) Slog.v(TAG, "Restarting: " + next);
            }
            startSpecificActivityLocked(next, true, true);
        }

        return true;
    }

    private final void updatePrivacyGuardNotificationLocked(ActivityRecord next) {

        if (mPrivacyGuardPackageName != null && mPrivacyGuardPackageName.equals(next.packageName)) {
            return;
        }

        boolean privacy = mService.mAppOpsService.getPrivacyGuardSettingForPackage(
                next.app.uid, next.packageName);

        if (mPrivacyGuardPackageName != null && !privacy) {
            Message msg = mService.mHandler.obtainMessage(
                    ActivityManagerService.CANCEL_PRIVACY_NOTIFICATION_MSG, next.userId);
            msg.sendToTarget();
            mPrivacyGuardPackageName = null;
        } else if (privacy) {
            Message msg = mService.mHandler.obtainMessage(
                    ActivityManagerService.POST_PRIVACY_NOTIFICATION_MSG, next);
            msg.sendToTarget();
            mPrivacyGuardPackageName = next.packageName;
        }
    }

    private final void startActivityLocked(ActivityRecord r, boolean newTask,
            boolean doResume, boolean keepCurTransition, Bundle options) {
        final int NH = mHistory.size();

        int addPos = -1;
        
        if (!newTask) {
            // If starting in an existing task, find where that is...
            boolean startIt = true;
            for (int i = NH-1; i >= 0; i--) {
                ActivityRecord p = mHistory.get(i);
                if (p.finishing) {
                    continue;
                }
                if (p.task == r.task) {
                    // Here it is!  Now, if this is not yet visible to the
                    // user, then just add it without starting; it will
                    // get started when the user navigates back to it.
                    addPos = i+1;
                    if (!startIt) {
                        if (DEBUG_ADD_REMOVE) {
                            RuntimeException here = new RuntimeException("here");
                            here.fillInStackTrace();
                            Slog.i(TAG, "Adding activity " + r + " to stack at " + addPos,
                                    here);
                        }
                        mHistory.add(addPos, r);
                        r.putInHistory();
                        mService.mWindowManager.addAppToken(addPos, r.appToken, r.task.taskId,
                                r.info.screenOrientation, r.fullscreen,
                                (r.info.flags & ActivityInfo.FLAG_SHOW_ON_LOCK_SCREEN) != 0);
                        if (VALIDATE_TOKENS) {
                            validateAppTokensLocked();
                        }
                        ActivityOptions.abort(options);
                        return;
                    }
                    break;
                }
                if (p.fullscreen) {
                    startIt = false;
                }
            }
        }

        // Place a new activity at top of stack, so it is next to interact
        // with the user.
        if (addPos < 0) {
            addPos = NH;
        }
        
        // If we are not placing the new activity frontmost, we do not want
        // to deliver the onUserLeaving callback to the actual frontmost
        // activity
        if (addPos < NH) {
            mUserLeaving = false;
            if (DEBUG_USER_LEAVING) Slog.v(TAG, "startActivity() behind front, mUserLeaving=false");
        }
        
        // Slot the activity into the history stack and proceed
        if (DEBUG_ADD_REMOVE) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG, "Adding activity " + r + " to stack at " + addPos, here);
        }
        mHistory.add(addPos, r);
        r.putInHistory();
        r.frontOfTask = newTask;
        if (NH > 0) {
            // We want to show the starting preview window if we are
            // switching to a new task, or the next activity's process is
            // not currently running.
            boolean showStartingIcon = newTask;
            ProcessRecord proc = r.app;
            if (proc == null) {
                proc = mService.mProcessNames.get(r.processName, r.info.applicationInfo.uid);
            }
            if (proc == null || proc.thread == null) {
                showStartingIcon = true;
            }
            if (DEBUG_TRANSITION) Slog.v(TAG,
                    "Prepare open transition: starting " + r);
            if ((r.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
                mService.mWindowManager.prepareAppTransition(
                        AppTransition.TRANSIT_NONE, keepCurTransition);
                mNoAnimActivities.add(r);
            } else {
                mService.mWindowManager.prepareAppTransition(newTask
                        ? AppTransition.TRANSIT_TASK_OPEN
                        : AppTransition.TRANSIT_ACTIVITY_OPEN, keepCurTransition);
                mNoAnimActivities.remove(r);
            }
            r.updateOptionsLocked(options);
            mService.mWindowManager.addAppToken(
                    addPos, r.appToken, r.task.taskId, r.info.screenOrientation, r.fullscreen,
                    (r.info.flags & ActivityInfo.FLAG_SHOW_ON_LOCK_SCREEN) != 0);
            boolean doShow = true;
            if (newTask) {
                // Even though this activity is starting fresh, we still need
                // to reset it to make sure we apply affinities to move any
                // existing activities from other tasks in to it.
                // If the caller has requested that the target task be
                // reset, then do so.
                if ((r.intent.getFlags()
                        &Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0) {
                    resetTaskIfNeededLocked(r, r);
                    doShow = topRunningNonDelayedActivityLocked(null) == r;
                }
            }
            if (SHOW_APP_STARTING_PREVIEW && doShow) {
                // Figure out if we are transitioning from another activity that is
                // "has the same starting icon" as the next one.  This allows the
                // window manager to keep the previous window it had previously
                // created, if it still had one.
                ActivityRecord prev = mResumedActivity;
                if (prev != null) {
                    // We don't want to reuse the previous starting preview if:
                    // (1) The current activity is in a different task.
                    if (prev.task != r.task) prev = null;
                    // (2) The current activity is already displayed.
                    else if (prev.nowVisible) prev = null;
                }
                mService.mWindowManager.setAppStartingWindow(
                        r.appToken, r.packageName, r.theme,
                        mService.compatibilityInfoForPackageLocked(
                                r.info.applicationInfo), r.nonLocalizedLabel,
                        r.labelRes, r.icon, r.windowFlags,
                        prev != null ? prev.appToken : null, showStartingIcon);
            }
        } else {
            // If this is the first activity, don't do any fancy animations,
            // because there is nothing for it to animate on top of.
            mService.mWindowManager.addAppToken(addPos, r.appToken, r.task.taskId,
                    r.info.screenOrientation, r.fullscreen,
                    (r.info.flags & ActivityInfo.FLAG_SHOW_ON_LOCK_SCREEN) != 0);
            ActivityOptions.abort(options);
        }
        if (VALIDATE_TOKENS) {
            validateAppTokensLocked();
        }

        if (doResume) {
            resumeTopActivityLocked(null);
        }
    }

    final void validateAppTokensLocked() {
        mValidateAppTokens.clear();
        mValidateAppTokens.ensureCapacity(mHistory.size());
        for (int i=0; i<mHistory.size(); i++) {
            mValidateAppTokens.add(mHistory.get(i).appToken);
        }
        mService.mWindowManager.validateAppTokens(mValidateAppTokens);
    }

    /**
     * Perform a reset of the given task, if needed as part of launching it.
     * Returns the new HistoryRecord at the top of the task.
     */
    private final ActivityRecord resetTaskIfNeededLocked(ActivityRecord taskTop,
            ActivityRecord newActivity) {
        boolean forceReset = (newActivity.info.flags
                &ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH) != 0;
        if (ACTIVITY_INACTIVE_RESET_TIME > 0
                && taskTop.task.getInactiveDuration() > ACTIVITY_INACTIVE_RESET_TIME) {
            if ((newActivity.info.flags
                    &ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE) == 0) {
                forceReset = true;
            }
        }
        
        final TaskRecord task = taskTop.task;
        
        // We are going to move through the history list so that we can look
        // at each activity 'target' with 'below' either the interesting
        // activity immediately below it in the stack or null.
        ActivityRecord target = null;
        int targetI = 0;
        int taskTopI = -1;
        int replyChainEnd = -1;
        int lastReparentPos = -1;
        ActivityOptions topOptions = null;
        boolean canMoveOptions = true;
        for (int i=mHistory.size()-1; i>=-1; i--) {
            ActivityRecord below = i >= 0 ? mHistory.get(i) : null;
            
            if (below != null && below.finishing) {
                continue;
            }
            // Don't check any lower in the stack if we're crossing a user boundary.
            if (below != null && below.userId != taskTop.userId) {
                break;
            }
            if (target == null) {
                target = below;
                targetI = i;
                // If we were in the middle of a reply chain before this
                // task, it doesn't appear like the root of the chain wants
                // anything interesting, so drop it.
                replyChainEnd = -1;
                continue;
            }
        
            final int flags = target.info.flags;
            
            final boolean finishOnTaskLaunch =
                (flags&ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH) != 0;
            final boolean allowTaskReparenting =
                (flags&ActivityInfo.FLAG_ALLOW_TASK_REPARENTING) != 0;
            
            if (target.task == task) {
                // We are inside of the task being reset...  we'll either
                // finish this activity, push it out for another task,
                // or leave it as-is.  We only do this
                // for activities that are not the root of the task (since
                // if we finish the root, we may no longer have the task!).
                if (taskTopI < 0) {
                    taskTopI = targetI;
                }
                if (below != null && below.task == task) {
                    final boolean clearWhenTaskReset =
                            (target.intent.getFlags()
                                    &Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) != 0;
                    if (!finishOnTaskLaunch && !clearWhenTaskReset && target.resultTo != null) {
                        // If this activity is sending a reply to a previous
                        // activity, we can't do anything with it now until
                        // we reach the start of the reply chain.
                        // XXX note that we are assuming the result is always
                        // to the previous activity, which is almost always
                        // the case but we really shouldn't count on.
                        if (replyChainEnd < 0) {
                            replyChainEnd = targetI;
                        }
                    } else if (!finishOnTaskLaunch && !clearWhenTaskReset && allowTaskReparenting
                            && target.taskAffinity != null
                            && !target.taskAffinity.equals(task.affinity)) {
                        // If this activity has an affinity for another
                        // task, then we need to move it out of here.  We will
                        // move it as far out of the way as possible, to the
                        // bottom of the activity stack.  This also keeps it
                        // correctly ordered with any activities we previously
                        // moved.
                        ActivityRecord p = mHistory.get(0);
                        if (target.taskAffinity != null
                                && target.taskAffinity.equals(p.task.affinity)) {
                            // If the activity currently at the bottom has the
                            // same task affinity as the one we are moving,
                            // then merge it into the same task.
                            target.setTask(p.task, p.thumbHolder, false);
                            if (DEBUG_TASKS) Slog.v(TAG, "Start pushing activity " + target
                                    + " out to bottom task " + p.task);
                        } else {
                            mService.mCurTask++;
                            if (mService.mCurTask <= 0) {
                                mService.mCurTask = 1;
                            }
                            target.setTask(new TaskRecord(mService.mCurTask, target.info, null),
                                    null, false);
                            target.task.affinityIntent = target.intent;
                            if (DEBUG_TASKS) Slog.v(TAG, "Start pushing activity " + target
                                    + " out to new task " + target.task);
                        }
                        mService.mWindowManager.setAppGroupId(target.appToken, task.taskId);
                        if (replyChainEnd < 0) {
                            replyChainEnd = targetI;
                        }
                        int dstPos = 0;
                        ThumbnailHolder curThumbHolder = target.thumbHolder;
                        boolean gotOptions = !canMoveOptions;
                        for (int srcPos=targetI; srcPos<=replyChainEnd; srcPos++) {
                            p = mHistory.get(srcPos);
                            if (p.finishing) {
                                continue;
                            }
                            if (DEBUG_TASKS) Slog.v(TAG, "Pushing next activity " + p
                                    + " out to target's task " + target.task);
                            p.setTask(target.task, curThumbHolder, false);
                            curThumbHolder = p.thumbHolder;
                            canMoveOptions = false;
                            if (!gotOptions && topOptions == null) {
                                topOptions = p.takeOptionsLocked();
                                if (topOptions != null) {
                                    gotOptions = true;
                                }
                            }
                            if (DEBUG_ADD_REMOVE) {
                                RuntimeException here = new RuntimeException("here");
                                here.fillInStackTrace();
                                Slog.i(TAG, "Removing and adding activity " + p + " to stack at "
                                        + dstPos, here);
                            }
                            mHistory.remove(srcPos);
                            mHistory.add(dstPos, p);
                            mService.mWindowManager.moveAppToken(dstPos, p.appToken);
                            mService.mWindowManager.setAppGroupId(p.appToken, p.task.taskId);
                            dstPos++;
                            if (VALIDATE_TOKENS) {
                                validateAppTokensLocked();
                            }
                            i++;
                        }
                        if (taskTop == p) {
                            taskTop = below;
                        }
                        if (taskTopI == replyChainEnd) {
                            taskTopI = -1;
                        }
                        replyChainEnd = -1;
                    } else if (forceReset || finishOnTaskLaunch
                            || clearWhenTaskReset) {
                        // If the activity should just be removed -- either
                        // because it asks for it, or the task should be
                        // cleared -- then finish it and anything that is
                        // part of its reply chain.
                        if (clearWhenTaskReset) {
                            // In this case, we want to finish this activity
                            // and everything above it, so be sneaky and pretend
                            // like these are all in the reply chain.
                            replyChainEnd = targetI+1;
                            while (replyChainEnd < mHistory.size() &&
                                    (mHistory.get(
                                                replyChainEnd)).task == task) {
                                replyChainEnd++;
                            }
                            replyChainEnd--;
                        } else if (replyChainEnd < 0) {
                            replyChainEnd = targetI;
                        }
                        ActivityRecord p = null;
                        boolean gotOptions = !canMoveOptions;
                        for (int srcPos=targetI; srcPos<=replyChainEnd; srcPos++) {
                            p = mHistory.get(srcPos);
                            if (p.finishing) {
                                continue;
                            }
                            canMoveOptions = false;
                            if (!gotOptions && topOptions == null) {
                                topOptions = p.takeOptionsLocked();
                                if (topOptions != null) {
                                    gotOptions = true;
                                }
                            }
                            if (finishActivityLocked(p, srcPos,
                                    Activity.RESULT_CANCELED, null, "reset", false)) {
                                replyChainEnd--;
                                srcPos--;
                            }
                        }
                        if (taskTop == p) {
                            taskTop = below;
                        }
                        if (taskTopI == replyChainEnd) {
                            taskTopI = -1;
                        }
                        replyChainEnd = -1;
                    } else {
                        // If we were in the middle of a chain, well the
                        // activity that started it all doesn't want anything
                        // special, so leave it all as-is.
                        replyChainEnd = -1;
                    }
                } else {
                    // Reached the bottom of the task -- any reply chain
                    // should be left as-is.
                    replyChainEnd = -1;
                }

            } else if (target.resultTo != null && (below == null
                    || below.task == target.task)) {
                // If this activity is sending a reply to a previous
                // activity, we can't do anything with it now until
                // we reach the start of the reply chain.
                // XXX note that we are assuming the result is always
                // to the previous activity, which is almost always
                // the case but we really shouldn't count on.
                if (replyChainEnd < 0) {
                    replyChainEnd = targetI;
                }

            } else if (taskTopI >= 0 && allowTaskReparenting
                    && task.affinity != null
                    && task.affinity.equals(target.taskAffinity)) {
                // We are inside of another task...  if this activity has
                // an affinity for our task, then either remove it if we are
                // clearing or move it over to our task.  Note that
                // we currently punt on the case where we are resetting a
                // task that is not at the top but who has activities above
                // with an affinity to it...  this is really not a normal
                // case, and we will need to later pull that task to the front
                // and usually at that point we will do the reset and pick
                // up those remaining activities.  (This only happens if
                // someone starts an activity in a new task from an activity
                // in a task that is not currently on top.)
                if (forceReset || finishOnTaskLaunch) {
                    if (replyChainEnd < 0) {
                        replyChainEnd = targetI;
                    }
                    ActivityRecord p = null;
                    if (DEBUG_TASKS) Slog.v(TAG, "Finishing task at index "
                            + targetI + " to " + replyChainEnd);
                    for (int srcPos=targetI; srcPos<=replyChainEnd; srcPos++) {
                        p = mHistory.get(srcPos);
                        if (p.finishing) {
                            continue;
                        }
                        if (finishActivityLocked(p, srcPos,
                                Activity.RESULT_CANCELED, null, "reset", false)) {
                            taskTopI--;
                            lastReparentPos--;
                            replyChainEnd--;
                            srcPos--;
                        }
                    }
                    replyChainEnd = -1;
                } else {
                    if (replyChainEnd < 0) {
                        replyChainEnd = targetI;
                    }
                    if (DEBUG_TASKS) Slog.v(TAG, "Reparenting task at index "
                            + targetI + " to " + replyChainEnd);
                    for (int srcPos=replyChainEnd; srcPos>=targetI; srcPos--) {
                        ActivityRecord p = mHistory.get(srcPos);
                        if (p.finishing) {
                            continue;
                        }
                        if (lastReparentPos < 0) {
                            lastReparentPos = taskTopI;
                            taskTop = p;
                        } else {
                            lastReparentPos--;
                        }
                        if (DEBUG_ADD_REMOVE) {
                            RuntimeException here = new RuntimeException("here");
                            here.fillInStackTrace();
                            Slog.i(TAG, "Removing and adding activity " + p + " to stack at "
                                    + lastReparentPos, here);
                        }
                        mHistory.remove(srcPos);
                        p.setTask(task, null, false);
                        mHistory.add(lastReparentPos, p);
                        if (DEBUG_TASKS) Slog.v(TAG, "Pulling activity " + p
                                + " from " + srcPos + " to " + lastReparentPos
                                + " in to resetting task " + task);
                        mService.mWindowManager.moveAppToken(lastReparentPos, p.appToken);
                        mService.mWindowManager.setAppGroupId(p.appToken, p.task.taskId);
                        if (VALIDATE_TOKENS) {
                            validateAppTokensLocked();
                        }
                    }
                    replyChainEnd = -1;
                    
                    // Now we've moved it in to place...  but what if this is
                    // a singleTop activity and we have put it on top of another
                    // instance of the same activity?  Then we drop the instance
                    // below so it remains singleTop.
                    if (target.info.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP) {
                        for (int j=lastReparentPos-1; j>=0; j--) {
                            ActivityRecord p = mHistory.get(j);
                            if (p.finishing) {
                                continue;
                            }
                            if (p.intent.getComponent().equals(target.intent.getComponent())) {
                                if (finishActivityLocked(p, j,
                                        Activity.RESULT_CANCELED, null, "replace", false)) {
                                    taskTopI--;
                                    lastReparentPos--;
                                }
                            }
                        }
                    }
                }

            } else if (below != null && below.task != target.task) {
                // We hit the botton of a task; the reply chain can't
                // pass through it.
                replyChainEnd = -1;
            }
            
            target = below;
            targetI = i;
        }

        if (topOptions != null) {
            // If we got some ActivityOptions from an activity on top that
            // was removed from the task, propagate them to the new real top.
            if (taskTop != null) {
                taskTop.updateOptionsLocked(topOptions);
            } else {
                topOptions.abort();
            }
        }

        return taskTop;
    }
    
    /**
     * Perform clear operation as requested by
     * {@link Intent#FLAG_ACTIVITY_CLEAR_TOP}: search from the top of the
     * stack to the given task, then look for
     * an instance of that activity in the stack and, if found, finish all
     * activities on top of it and return the instance.
     *
     * @param newR Description of the new activity being started.
     * @return Returns the old activity that should be continued to be used,
     * or null if none was found.
     */
    private final ActivityRecord performClearTaskLocked(int taskId,
            ActivityRecord newR, int launchFlags) {
        int i = mHistory.size();
        
        // First find the requested task.
        while (i > 0) {
            i--;
            ActivityRecord r = mHistory.get(i);
            if (r.task.taskId == taskId) {
                i++;
                break;
            }
        }
        
        // Now clear it.
        while (i > 0) {
            i--;
            ActivityRecord r = mHistory.get(i);
            if (r.finishing) {
                continue;
            }
            if (r.task.taskId != taskId) {
                return null;
            }
            if (r.realActivity.equals(newR.realActivity)) {
                // Here it is!  Now finish everything in front...
                ActivityRecord ret = r;
                while (i < (mHistory.size()-1)) {
                    i++;
                    r = mHistory.get(i);
                    if (r.task.taskId != taskId) {
                        break;
                    }
                    if (r.finishing) {
                        continue;
                    }
                    ActivityOptions opts = r.takeOptionsLocked();
                    if (opts != null) {
                        ret.updateOptionsLocked(opts);
                    }
                    if (finishActivityLocked(r, i, Activity.RESULT_CANCELED,
                            null, "clear", false)) {
                        i--;
                    }
                }
                
                // Finally, if this is a normal launch mode (that is, not
                // expecting onNewIntent()), then we will finish the current
                // instance of the activity so a new fresh one can be started.
                if (ret.launchMode == ActivityInfo.LAUNCH_MULTIPLE
                        && (launchFlags&Intent.FLAG_ACTIVITY_SINGLE_TOP) == 0) {
                    if (!ret.finishing) {
                        int index = indexOfTokenLocked(ret.appToken);
                        if (index >= 0) {
                            finishActivityLocked(ret, index, Activity.RESULT_CANCELED,
                                    null, "clear", false);
                        }
                        return null;
                    }
                }
                
                return ret;
            }
        }

        return null;
    }

    /**
     * Completely remove all activities associated with an existing
     * task starting at a specified index.
     */
    private final void performClearTaskAtIndexLocked(int taskId, int i) {
        while (i < mHistory.size()) {
            ActivityRecord r = mHistory.get(i);
            if (r.task.taskId != taskId) {
                // Whoops hit the end.
                return;
            }
            if (r.finishing) {
                i++;
                continue;
            }
            if (!finishActivityLocked(r, i, Activity.RESULT_CANCELED,
                    null, "clear", false)) {
                i++;
            }
        }
    }

    /**
     * Completely remove all activities associated with an existing task.
     */
    private final void performClearTaskLocked(int taskId) {
        int i = mHistory.size();

        // First find the requested task.
        while (i > 0) {
            i--;
            ActivityRecord r = mHistory.get(i);
            if (r.task.taskId == taskId) {
                i++;
                break;
            }
        }

        // Now find the start and clear it.
        while (i > 0) {
            i--;
            ActivityRecord r = mHistory.get(i);
            if (r.finishing) {
                continue;
            }
            if (r.task.taskId != taskId) {
                // We hit the bottom.  Now finish it all...
                performClearTaskAtIndexLocked(taskId, i+1);
                return;
            }
        }
    }

    /**
     * Find the activity in the history stack within the given task.  Returns
     * the index within the history at which it's found, or < 0 if not found.
     */
    private final int findActivityInHistoryLocked(ActivityRecord r, int task) {
        int i = mHistory.size();
        while (i > 0) {
            i--;
            ActivityRecord candidate = mHistory.get(i);
            if (candidate.finishing) {
                continue;
            }
            if (candidate.task.taskId != task) {
                break;
            }
            if (candidate.realActivity.equals(r.realActivity)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Reorder the history stack so that the activity at the given index is
     * brought to the front.
     */
    private final ActivityRecord moveActivityToFrontLocked(int where) {
        ActivityRecord newTop = mHistory.remove(where);
        int top = mHistory.size();
        ActivityRecord oldTop = mHistory.get(top-1);
        if (DEBUG_ADD_REMOVE) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG, "Removing and adding activity " + newTop + " to stack at "
                    + top, here);
        }
        mHistory.add(top, newTop);
        oldTop.frontOfTask = false;
        newTop.frontOfTask = true;
        return newTop;
    }

    final int startActivityLocked(IApplicationThread caller,
            Intent intent, String resolvedType, ActivityInfo aInfo, IBinder resultTo,
            String resultWho, int requestCode,
            int callingPid, int callingUid, String callingPackage, int startFlags, Bundle options,
            boolean componentSpecified, ActivityRecord[] outActivity) {

        int err = ActivityManager.START_SUCCESS;

        mPm.cpuBoost(1500000);

        ProcessRecord callerApp = null;

        if (caller != null) {
            callerApp = mService.getRecordForAppLocked(caller);
            if (callerApp != null) {
                callingPid = callerApp.pid;
                callingUid = callerApp.info.uid;
            } else {
                Slog.w(TAG, "Unable to find app for caller " + caller
                      + " (pid=" + callingPid + ") when starting: "
                      + intent.toString());
                err = ActivityManager.START_PERMISSION_DENIED;
            }
        }

        if (err == ActivityManager.START_SUCCESS) {
            final int userId = aInfo != null ? UserHandle.getUserId(aInfo.applicationInfo.uid) : 0;
            Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true, true, false)
                    + "} from pid " + (callerApp != null ? callerApp.pid : callingPid));
            if (mActivityTrigger != null) {
                mActivityTrigger.activityStartTrigger(intent);
            }
        }

        ActivityRecord sourceRecord = null;
        ActivityRecord resultRecord = null;
        if (resultTo != null) {
            int index = indexOfTokenLocked(resultTo);
            if (DEBUG_RESULTS) Slog.v(
                TAG, "Will send result to " + resultTo + " (index " + index + ")");
            if (index >= 0) {
                sourceRecord = mHistory.get(index);
                if (requestCode >= 0 && !sourceRecord.finishing) {
                    resultRecord = sourceRecord;
                }
            }
        }

        int launchFlags = intent.getFlags();

        if ((launchFlags&Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0
                && sourceRecord != null) {
            // Transfer the result target from the source activity to the new
            // one being started, including any failures.
            if (requestCode >= 0) {
                ActivityOptions.abort(options);
                return ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT;
            }
            resultRecord = sourceRecord.resultTo;
            resultWho = sourceRecord.resultWho;
            requestCode = sourceRecord.requestCode;
            sourceRecord.resultTo = null;
            if (resultRecord != null) {
                resultRecord.removeResultsLocked(
                    sourceRecord, resultWho, requestCode);
            }
        }

        if (err == ActivityManager.START_SUCCESS && intent.getComponent() == null) {
            // We couldn't find a class that can handle the given Intent.
            // That's the end of that!
            err = ActivityManager.START_INTENT_NOT_RESOLVED;
        }

        if (err == ActivityManager.START_SUCCESS && aInfo == null) {
            // We couldn't find the specific class specified in the Intent.
            // Also the end of the line.
            err = ActivityManager.START_CLASS_NOT_FOUND;
        }

        if (err != ActivityManager.START_SUCCESS) {
            if (resultRecord != null) {
                sendActivityResultLocked(-1,
                    resultRecord, resultWho, requestCode,
                    Activity.RESULT_CANCELED, null);
            }
            mDismissKeyguardOnNextActivity = false;
            ActivityOptions.abort(options);
            return err;
        }

        final int startAnyPerm = mService.checkPermission(
                START_ANY_ACTIVITY, callingPid, callingUid);
        final int componentPerm = mService.checkComponentPermission(aInfo.permission, callingPid,
                callingUid, aInfo.applicationInfo.uid, aInfo.exported);
        if (startAnyPerm != PERMISSION_GRANTED && componentPerm != PERMISSION_GRANTED) {
            if (resultRecord != null) {
                sendActivityResultLocked(-1,
                    resultRecord, resultWho, requestCode,
                    Activity.RESULT_CANCELED, null);
            }
            mDismissKeyguardOnNextActivity = false;
            String msg;
            if (!aInfo.exported) {
                msg = "Permission Denial: starting " + intent.toString()
                        + " from " + callerApp + " (pid=" + callingPid
                        + ", uid=" + callingUid + ")"
                        + " not exported from uid " + aInfo.applicationInfo.uid;
            } else {
                msg = "Permission Denial: starting " + intent.toString()
                        + " from " + callerApp + " (pid=" + callingPid
                        + ", uid=" + callingUid + ")"
                        + " requires " + aInfo.permission;
            }
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        boolean abort = !mService.mIntentFirewall.checkStartActivity(intent,
                callerApp==null?null:callerApp.info, callingUid, callingPid, resolvedType, aInfo);

        if (mMainStack) {
            if (mService.mController != null) {
                try {
                    // The Intent we give to the watcher has the extra data
                    // stripped off, since it can contain private information.
                    Intent watchIntent = intent.cloneFilter();
                    abort |= !mService.mController.activityStarting(watchIntent,
                            aInfo.applicationInfo.packageName);
                } catch (RemoteException e) {
                    mService.mController = null;
                }
            }
        }

        if (abort) {
            if (resultRecord != null) {
                sendActivityResultLocked(-1,
                    resultRecord, resultWho, requestCode,
                    Activity.RESULT_CANCELED, null);
            }
            // We pretend to the caller that it was really started, but
            // they will just get a cancel result.
            mDismissKeyguardOnNextActivity = false;
            ActivityOptions.abort(options);
            return ActivityManager.START_SUCCESS;
        }

        ActivityRecord r = new ActivityRecord(mService, this, callerApp, callingUid, callingPackage,
                intent, resolvedType, aInfo, mService.mConfiguration,
                resultRecord, resultWho, requestCode, componentSpecified);
        if (outActivity != null) {
            outActivity[0] = r;
        }

        if (mMainStack) {
            if (mResumedActivity == null
                    || mResumedActivity.info.applicationInfo.uid != callingUid) {
                if (!mService.checkAppSwitchAllowedLocked(callingPid, callingUid, "Activity start")) {
                    PendingActivityLaunch pal = new PendingActivityLaunch();
                    pal.r = r;
                    pal.sourceRecord = sourceRecord;
                    pal.startFlags = startFlags;
                    mService.mPendingActivityLaunches.add(pal);
                    mDismissKeyguardOnNextActivity = false;
                    ActivityOptions.abort(options);
                    return ActivityManager.START_SWITCHES_CANCELED;
                }
            }
        
            if (mService.mDidAppSwitch) {
                // This is the second allowed switch since we stopped switches,
                // so now just generally allow switches.  Use case: user presses
                // home (switches disabled, switch to home, mDidAppSwitch now true);
                // user taps a home icon (coming from home so allowed, we hit here
                // and now allow anyone to switch again).
                mService.mAppSwitchesAllowedTime = 0;
            } else {
                mService.mDidAppSwitch = true;
            }
         
            mService.doPendingActivityLaunchesLocked(false);
        }
        
        err = startActivityUncheckedLocked(r, sourceRecord,
                startFlags, true, options);
        if (mDismissKeyguardOnNextActivity && mPausingActivity == null) {
            // Someone asked to have the keyguard dismissed on the next
            // activity start, but we are not actually doing an activity
            // switch...  just dismiss the keyguard now, because we
            // probably want to see whatever is behind it.
            mDismissKeyguardOnNextActivity = false;
            mService.mWindowManager.dismissKeyguard();
        }
        return err;
    }
  
    final void moveHomeToFrontFromLaunchLocked(int launchFlags) {
        if ((launchFlags &
                (Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_TASK_ON_HOME))
                == (Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_TASK_ON_HOME)) {
            // Caller wants to appear on home activity, so before starting
            // their own activity we will bring home to the front.
            moveHomeToFrontLocked();
        }
    }

    final int startActivityUncheckedLocked(ActivityRecord r,
            ActivityRecord sourceRecord, int startFlags, boolean doResume,
            Bundle options) {
        final Intent intent = r.intent;
        final int callingUid = r.launchedFromUid;

        int launchFlags = intent.getFlags();
        
        // We'll invoke onUserLeaving before onPause only if the launching
        // activity did not explicitly state that this is an automated launch.
        mUserLeaving = (launchFlags&Intent.FLAG_ACTIVITY_NO_USER_ACTION) == 0;
        if (DEBUG_USER_LEAVING) Slog.v(TAG,
                "startActivity() => mUserLeaving=" + mUserLeaving);
        
        // If the caller has asked not to resume at this point, we make note
        // of this in the record so that we can skip it when trying to find
        // the top running activity.
        if (!doResume) {
            r.delayedResume = true;
        }
        
        ActivityRecord notTop = (launchFlags&Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
                != 0 ? r : null;

        // If the onlyIfNeeded flag is set, then we can do this if the activity
        // being launched is the same as the one making the call...  or, as
        // a special case, if we do not know the caller then we count the
        // current top activity as the caller.
        if ((startFlags&ActivityManager.START_FLAG_ONLY_IF_NEEDED) != 0) {
            ActivityRecord checkedCaller = sourceRecord;
            if (checkedCaller == null) {
                checkedCaller = topRunningNonDelayedActivityLocked(notTop);
            }
            if (!checkedCaller.realActivity.equals(r.realActivity)) {
                // Caller is not the same as launcher, so always needed.
                startFlags &= ~ActivityManager.START_FLAG_ONLY_IF_NEEDED;
            }
        }

        if (sourceRecord == null) {
            // This activity is not being started from another...  in this
            // case we -always- start a new task.
            if ((launchFlags&Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
                Slog.w(TAG, "startActivity called from non-Activity context; forcing Intent.FLAG_ACTIVITY_NEW_TASK for: "
                      + intent);
                launchFlags |= Intent.FLAG_ACTIVITY_NEW_TASK;
            }
        } else if (sourceRecord.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            // The original activity who is starting us is running as a single
            // instance...  this new activity it is starting must go on its
            // own task.
            launchFlags |= Intent.FLAG_ACTIVITY_NEW_TASK;
        } else if (r.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE
                || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
            // The activity being started is a single instance...  it always
            // gets launched into its own task.
            launchFlags |= Intent.FLAG_ACTIVITY_NEW_TASK;
        }

        if (r.resultTo != null && (launchFlags&Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            // For whatever reason this activity is being launched into a new
            // task...  yet the caller has requested a result back.  Well, that
            // is pretty messed up, so instead immediately send back a cancel
            // and let the new task continue launched as normal without a
            // dependency on its originator.
            Slog.w(TAG, "Activity is launching as a new task, so cancelling activity result.");
            sendActivityResultLocked(-1,
                    r.resultTo, r.resultWho, r.requestCode,
                Activity.RESULT_CANCELED, null);
            r.resultTo = null;
        }

        boolean addingToTask = false;
        boolean movedHome = false;
        TaskRecord reuseTask = null;
        if (((launchFlags&Intent.FLAG_ACTIVITY_NEW_TASK) != 0 &&
                (launchFlags&Intent.FLAG_ACTIVITY_MULTIPLE_TASK) == 0)
                || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK
                || r.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            // If bring to front is requested, and no result is requested, and
            // we can find a task that was started with this same
            // component, then instead of launching bring that one to the front.
            if (r.resultTo == null) {
                // See if there is a task to bring to the front.  If this is
                // a SINGLE_INSTANCE activity, there can be one and only one
                // instance of it in the history, and it is always in its own
                // unique task, so we do a special search.
                ActivityRecord taskTop = r.launchMode != ActivityInfo.LAUNCH_SINGLE_INSTANCE
                        ? findTaskLocked(intent, r.info)
                        : findActivityLocked(intent, r.info);
                if (taskTop != null) {
                    if (taskTop.task.intent == null) {
                        // This task was started because of movement of
                        // the activity based on affinity...  now that we
                        // are actually launching it, we can assign the
                        // base intent.
                        taskTop.task.setIntent(intent, r.info);
                    }
                    // If the target task is not in the front, then we need
                    // to bring it to the front...  except...  well, with
                    // SINGLE_TASK_LAUNCH it's not entirely clear.  We'd like
                    // to have the same behavior as if a new instance was
                    // being started, which means not bringing it to the front
                    // if the caller is not itself in the front.
                    ActivityRecord curTop = topRunningNonDelayedActivityLocked(notTop);
                    if (curTop != null && curTop.task != taskTop.task) {
                        r.intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                        boolean callerAtFront = sourceRecord == null
                                || curTop.task == sourceRecord.task;
                        if (callerAtFront) {
                            // We really do want to push this one into the
                            // user's face, right now.
                            movedHome = true;
                            moveHomeToFrontFromLaunchLocked(launchFlags);
                            moveTaskToFrontLocked(taskTop.task, r, options);
                            options = null;
                        }
                    }
                    // If the caller has requested that the target task be
                    // reset, then do so.
                    if ((launchFlags&Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0) {
                        taskTop = resetTaskIfNeededLocked(taskTop, r);
                    }
                    if ((startFlags&ActivityManager.START_FLAG_ONLY_IF_NEEDED)  != 0) {
                        // We don't need to start a new activity, and
                        // the client said not to do anything if that
                        // is the case, so this is it!  And for paranoia, make
                        // sure we have correctly resumed the top activity.
                        if (doResume) {
                            resumeTopActivityLocked(null, options);
                        } else {
                            ActivityOptions.abort(options);
                        }
                        return ActivityManager.START_RETURN_INTENT_TO_CALLER;
                    }
                    if ((launchFlags &
                            (Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            == (Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)) {
                        // The caller has requested to completely replace any
                        // existing task with its new activity.  Well that should
                        // not be too hard...
                        reuseTask = taskTop.task;
                        performClearTaskLocked(taskTop.task.taskId);
                        reuseTask.setIntent(r.intent, r.info);
                    } else if ((launchFlags&Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0
                            || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK
                            || r.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                        // In this situation we want to remove all activities
                        // from the task up to the one being started.  In most
                        // cases this means we are resetting the task to its
                        // initial state.
                        ActivityRecord top = performClearTaskLocked(
                                taskTop.task.taskId, r, launchFlags);
                        if (top != null) {
                            if (top.frontOfTask) {
                                // Activity aliases may mean we use different
                                // intents for the top activity, so make sure
                                // the task now has the identity of the new
                                // intent.
                                top.task.setIntent(r.intent, r.info);
                            }
                            logStartActivity(EventLogTags.AM_NEW_INTENT, r, top.task);
                            top.deliverNewIntentLocked(callingUid, r.intent);
                        } else {
                            // A special case: we need to
                            // start the activity because it is not currently
                            // running, and the caller has asked to clear the
                            // current task to have this activity at the top.
                            addingToTask = true;
                            // Now pretend like this activity is being started
                            // by the top of its task, so it is put in the
                            // right place.
                            sourceRecord = taskTop;
                        }
                    } else if (r.realActivity.equals(taskTop.task.realActivity)) {
                        // In this case the top activity on the task is the
                        // same as the one being launched, so we take that
                        // as a request to bring the task to the foreground.
                        // If the top activity in the task is the root
                        // activity, deliver this new intent to it if it
                        // desires.
                        if (((launchFlags&Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0
                                || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP)
                                && taskTop.realActivity.equals(r.realActivity)) {
                            logStartActivity(EventLogTags.AM_NEW_INTENT, r, taskTop.task);
                            if (taskTop.frontOfTask) {
                                taskTop.task.setIntent(r.intent, r.info);
                            }
                            taskTop.deliverNewIntentLocked(callingUid, r.intent);
                        } else if (!r.intent.filterEquals(taskTop.task.intent)) {
                            // In this case we are launching the root activity
                            // of the task, but with a different intent.  We
                            // should start a new instance on top.
                            addingToTask = true;
                            sourceRecord = taskTop;
                        }
                    } else if ((launchFlags&Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) == 0) {
                        // In this case an activity is being launched in to an
                        // existing task, without resetting that task.  This
                        // is typically the situation of launching an activity
                        // from a notification or shortcut.  We want to place
                        // the new activity on top of the current task.
                        addingToTask = true;
                        sourceRecord = taskTop;
                    } else if (!taskTop.task.rootWasReset) {
                        // In this case we are launching in to an existing task
                        // that has not yet been started from its front door.
                        // The current task has been brought to the front.
                        // Ideally, we'd probably like to place this new task
                        // at the bottom of its stack, but that's a little hard
                        // to do with the current organization of the code so
                        // for now we'll just drop it.
                        taskTop.task.setIntent(r.intent, r.info);
                    }
                    if (!addingToTask && reuseTask == null) {
                        // We didn't do anything...  but it was needed (a.k.a., client
                        // don't use that intent!)  And for paranoia, make
                        // sure we have correctly resumed the top activity.
                        if (doResume) {
                            resumeTopActivityLocked(null, options);
                        } else {
                            ActivityOptions.abort(options);
                        }
                        return ActivityManager.START_TASK_TO_FRONT;
                    }
                }
            }
        }

        //String uri = r.intent.toURI();
        //Intent intent2 = new Intent(uri);
        //Slog.i(TAG, "Given intent: " + r.intent);
        //Slog.i(TAG, "URI is: " + uri);
        //Slog.i(TAG, "To intent: " + intent2);

        if (r.packageName != null) {
            // If the activity being launched is the same as the one currently
            // at the top, then we need to check if it should only be launched
            // once.
            ActivityRecord top = topRunningNonDelayedActivityLocked(notTop);
            if (top != null && r.resultTo == null) {
                if (top.realActivity.equals(r.realActivity) && top.userId == r.userId) {
                    if (top.app != null && top.app.thread != null) {
                        if ((launchFlags&Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0
                            || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP
                            || r.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
                            logStartActivity(EventLogTags.AM_NEW_INTENT, top, top.task);
                            // For paranoia, make sure we have correctly
                            // resumed the top activity.
                            if (doResume) {
                                resumeTopActivityLocked(null);
                            }
                            ActivityOptions.abort(options);
                            if ((startFlags&ActivityManager.START_FLAG_ONLY_IF_NEEDED) != 0) {
                                // We don't need to start a new activity, and
                                // the client said not to do anything if that
                                // is the case, so this is it!
                                return ActivityManager.START_RETURN_INTENT_TO_CALLER;
                            }
                            top.deliverNewIntentLocked(callingUid, r.intent);
                            return ActivityManager.START_DELIVERED_TO_TOP;
                        }
                    }
                }
            }

        } else {
            if (r.resultTo != null) {
                sendActivityResultLocked(-1,
                        r.resultTo, r.resultWho, r.requestCode,
                    Activity.RESULT_CANCELED, null);
            }
            ActivityOptions.abort(options);
            return ActivityManager.START_CLASS_NOT_FOUND;
        }

        boolean newTask = false;
        boolean keepCurTransition = false;

        // Should this be considered a new task?
        if (r.resultTo == null && !addingToTask
                && (launchFlags&Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            if (reuseTask == null) {
                // todo: should do better management of integers.
                mService.mCurTask++;
                if (mService.mCurTask <= 0) {
                    mService.mCurTask = 1;
                }
                r.setTask(new TaskRecord(mService.mCurTask, r.info, intent), null, true);
                if (DEBUG_TASKS) Slog.v(TAG, "Starting new activity " + r
                        + " in new task " + r.task);
            } else {
                r.setTask(reuseTask, reuseTask, true);
            }
            newTask = true;
            if (!movedHome) {
                moveHomeToFrontFromLaunchLocked(launchFlags);
            }
            
        } else if (sourceRecord != null) {
            if (!addingToTask &&
                    (launchFlags&Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0) {
                // In this case, we are adding the activity to an existing
                // task, but the caller has asked to clear that task if the
                // activity is already running.
                ActivityRecord top = performClearTaskLocked(
                        sourceRecord.task.taskId, r, launchFlags);
                keepCurTransition = true;
                if (top != null) {
                    logStartActivity(EventLogTags.AM_NEW_INTENT, r, top.task);
                    top.deliverNewIntentLocked(callingUid, r.intent);
                    // For paranoia, make sure we have correctly
                    // resumed the top activity.
                    if (doResume) {
                        resumeTopActivityLocked(null);
                    }
                    ActivityOptions.abort(options);
                    return ActivityManager.START_DELIVERED_TO_TOP;
                }
            } else if (!addingToTask &&
                    (launchFlags&Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0) {
                // In this case, we are launching an activity in our own task
                // that may already be running somewhere in the history, and
                // we want to shuffle it to the front of the stack if so.
                int where = findActivityInHistoryLocked(r, sourceRecord.task.taskId);
                if (where >= 0) {
                    ActivityRecord top = moveActivityToFrontLocked(where);
                    logStartActivity(EventLogTags.AM_NEW_INTENT, r, top.task);
                    top.updateOptionsLocked(options);
                    top.deliverNewIntentLocked(callingUid, r.intent);
                    if (doResume) {
                        resumeTopActivityLocked(null);
                    }
                    return ActivityManager.START_DELIVERED_TO_TOP;
                }
            }
            // An existing activity is starting this new activity, so we want
            // to keep the new one in the same task as the one that is starting
            // it.
            r.setTask(sourceRecord.task, sourceRecord.thumbHolder, false);
            if (DEBUG_TASKS) Slog.v(TAG, "Starting new activity " + r
                    + " in existing task " + r.task);

        } else {
            // This not being started from an existing activity, and not part
            // of a new task...  just put it in the top task, though these days
            // this case should never happen.
            final int N = mHistory.size();
            ActivityRecord prev =
                N > 0 ? mHistory.get(N-1) : null;
            r.setTask(prev != null
                    ? prev.task
                    : new TaskRecord(mService.mCurTask, r.info, intent), null, true);
            if (DEBUG_TASKS) Slog.v(TAG, "Starting new activity " + r
                    + " in new guessed " + r.task);
        }

        mService.grantUriPermissionFromIntentLocked(callingUid, r.packageName,
                intent, r.getUriPermissionsLocked());

        if (newTask) {
            EventLog.writeEvent(EventLogTags.AM_CREATE_TASK, r.userId, r.task.taskId);
        }
        logStartActivity(EventLogTags.AM_CREATE_ACTIVITY, r, r.task);
        startActivityLocked(r, newTask, doResume, keepCurTransition, options);
        return ActivityManager.START_SUCCESS;
    }

    ActivityInfo resolveActivity(Intent intent, String resolvedType, int startFlags,
            String profileFile, ParcelFileDescriptor profileFd, int userId) {
        // Collect information about the target of the Intent.
        ActivityInfo aInfo;
        try {
            ResolveInfo rInfo =
                AppGlobals.getPackageManager().resolveIntent(
                        intent, resolvedType,
                        PackageManager.MATCH_DEFAULT_ONLY
                                    | ActivityManagerService.STOCK_PM_FLAGS, userId);
            aInfo = rInfo != null ? rInfo.activityInfo : null;
        } catch (RemoteException e) {
            aInfo = null;
        }

        if (aInfo != null) {
            // Store the found target back into the intent, because now that
            // we have it we never want to do this again.  For example, if the
            // user navigates back to this point in the history, we should
            // always restart the exact same activity.
            intent.setComponent(new ComponentName(
                    aInfo.applicationInfo.packageName, aInfo.name));

            // Don't debug things in the system process
            if ((startFlags&ActivityManager.START_FLAG_DEBUG) != 0) {
                if (!aInfo.processName.equals("system")) {
                    mService.setDebugApp(aInfo.processName, true, false);
                }
            }

            if ((startFlags&ActivityManager.START_FLAG_OPENGL_TRACES) != 0) {
                if (!aInfo.processName.equals("system")) {
                    mService.setOpenGlTraceApp(aInfo.applicationInfo, aInfo.processName);
                }
            }

            if (profileFile != null) {
                if (!aInfo.processName.equals("system")) {
                    mService.setProfileApp(aInfo.applicationInfo, aInfo.processName,
                            profileFile, profileFd,
                            (startFlags&ActivityManager.START_FLAG_AUTO_STOP_PROFILER) != 0);
                }
            }
        }
        return aInfo;
    }

    final int startActivityMayWait(IApplicationThread caller, int callingUid,
            String callingPackage, Intent intent, String resolvedType, IBinder resultTo,
            String resultWho, int requestCode, int startFlags, String profileFile,
            ParcelFileDescriptor profileFd, WaitResult outResult, Configuration config,
            Bundle options, int userId) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }
        boolean componentSpecified = intent.getComponent() != null;

        // Don't modify the client's object!
        intent = new Intent(intent);

        // Collect information about the target of the Intent.
        ActivityInfo aInfo = resolveActivity(intent, resolvedType, startFlags,
                profileFile, profileFd, userId);

        synchronized (mService) {
            int callingPid;
            if (callingUid >= 0) {
                callingPid = -1;
            } else if (caller == null) {
                callingPid = Binder.getCallingPid();
                callingUid = Binder.getCallingUid();
            } else {
                callingPid = callingUid = -1;
            }
            
            mConfigWillChange = config != null
                    && mService.mConfiguration.diff(config) != 0;
            if (DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Starting activity when config will change = " + mConfigWillChange);
            
            final long origId = Binder.clearCallingIdentity();
            
            if (mMainStack && aInfo != null &&
                    (aInfo.applicationInfo.flags&ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
                // This may be a heavy-weight process!  Check to see if we already
                // have another, different heavy-weight process running.
                if (aInfo.processName.equals(aInfo.applicationInfo.packageName)) {
                    if (mService.mHeavyWeightProcess != null &&
                            (mService.mHeavyWeightProcess.info.uid != aInfo.applicationInfo.uid ||
                            !mService.mHeavyWeightProcess.processName.equals(aInfo.processName))) {
                        int realCallingPid = callingPid;
                        int realCallingUid = callingUid;
                        if (caller != null) {
                            ProcessRecord callerApp = mService.getRecordForAppLocked(caller);
                            if (callerApp != null) {
                                realCallingPid = callerApp.pid;
                                realCallingUid = callerApp.info.uid;
                            } else {
                                Slog.w(TAG, "Unable to find app for caller " + caller
                                      + " (pid=" + realCallingPid + ") when starting: "
                                      + intent.toString());
                                ActivityOptions.abort(options);
                                return ActivityManager.START_PERMISSION_DENIED;
                            }
                        }
                        
                        IIntentSender target = mService.getIntentSenderLocked(
                                ActivityManager.INTENT_SENDER_ACTIVITY, "android",
                                realCallingUid, userId, null, null, 0, new Intent[] { intent },
                                new String[] { resolvedType }, PendingIntent.FLAG_CANCEL_CURRENT
                                | PendingIntent.FLAG_ONE_SHOT, null);
                        
                        Intent newIntent = new Intent();
                        if (requestCode >= 0) {
                            // Caller is requesting a result.
                            newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_HAS_RESULT, true);
                        }
                        newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_INTENT,
                                new IntentSender(target));
                        if (mService.mHeavyWeightProcess.activities.size() > 0) {
                            ActivityRecord hist = mService.mHeavyWeightProcess.activities.get(0);
                            newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_CUR_APP,
                                    hist.packageName);
                            newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_CUR_TASK,
                                    hist.task.taskId);
                        }
                        newIntent.putExtra(HeavyWeightSwitcherActivity.KEY_NEW_APP,
                                aInfo.packageName);
                        newIntent.setFlags(intent.getFlags());
                        newIntent.setClassName("android",
                                HeavyWeightSwitcherActivity.class.getName());
                        intent = newIntent;
                        resolvedType = null;
                        caller = null;
                        callingUid = Binder.getCallingUid();
                        callingPid = Binder.getCallingPid();
                        componentSpecified = true;
                        try {
                            ResolveInfo rInfo =
                                AppGlobals.getPackageManager().resolveIntent(
                                        intent, null,
                                        PackageManager.MATCH_DEFAULT_ONLY
                                        | ActivityManagerService.STOCK_PM_FLAGS, userId);
                            aInfo = rInfo != null ? rInfo.activityInfo : null;
                            aInfo = mService.getActivityInfoForUser(aInfo, userId);
                        } catch (RemoteException e) {
                            aInfo = null;
                        }
                    }
                }
            }
            
            int res = startActivityLocked(caller, intent, resolvedType,
                    aInfo, resultTo, resultWho, requestCode, callingPid, callingUid,
                    callingPackage, startFlags, options, componentSpecified, null);
            
            if (mConfigWillChange && mMainStack) {
                // If the caller also wants to switch to a new configuration,
                // do so now.  This allows a clean switch, as we are waiting
                // for the current activity to pause (so we will not destroy
                // it), and have not yet started the next activity.
                mService.enforceCallingPermission(android.Manifest.permission.CHANGE_CONFIGURATION,
                        "updateConfiguration()");
                mConfigWillChange = false;
                if (DEBUG_CONFIGURATION) Slog.v(TAG,
                        "Updating to new configuration after starting activity.");
                mService.updateConfigurationLocked(config, null, false, false);
            }
            
            Binder.restoreCallingIdentity(origId);
            
            if (outResult != null) {
                outResult.result = res;
                if (res == ActivityManager.START_SUCCESS) {
                    mWaitingActivityLaunched.add(outResult);
                    do {
                        try {
                            mService.wait();
                        } catch (InterruptedException e) {
                        }
                    } while (!outResult.timeout && outResult.who == null);
                } else if (res == ActivityManager.START_TASK_TO_FRONT) {
                    ActivityRecord r = this.topRunningActivityLocked(null);
                    if (r.nowVisible) {
                        outResult.timeout = false;
                        outResult.who = new ComponentName(r.info.packageName, r.info.name);
                        outResult.totalTime = 0;
                        outResult.thisTime = 0;
                    } else {
                        outResult.thisTime = SystemClock.uptimeMillis();
                        mWaitingActivityVisible.add(outResult);
                        do {
                            try {
                                mService.wait();
                            } catch (InterruptedException e) {
                            }
                        } while (!outResult.timeout && outResult.who == null);
                    }
                }
            }
            
            return res;
        }
    }
    
    final int startActivities(IApplicationThread caller, int callingUid, String callingPackage,
            Intent[] intents, String[] resolvedTypes, IBinder resultTo,
            Bundle options, int userId) {
        if (intents == null) {
            throw new NullPointerException("intents is null");
        }
        if (resolvedTypes == null) {
            throw new NullPointerException("resolvedTypes is null");
        }
        if (intents.length != resolvedTypes.length) {
            throw new IllegalArgumentException("intents are length different than resolvedTypes");
        }

        ActivityRecord[] outActivity = new ActivityRecord[1];

        int callingPid;
        if (callingUid >= 0) {
            callingPid = -1;
        } else if (caller == null) {
            callingPid = Binder.getCallingPid();
            callingUid = Binder.getCallingUid();
        } else {
            callingPid = callingUid = -1;
        }
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (mService) {

                for (int i=0; i<intents.length; i++) {
                    Intent intent = intents[i];
                    if (intent == null) {
                        continue;
                    }

                    // Refuse possible leaked file descriptors
                    if (intent != null && intent.hasFileDescriptors()) {
                        throw new IllegalArgumentException("File descriptors passed in Intent");
                    }

                    boolean componentSpecified = intent.getComponent() != null;

                    // Don't modify the client's object!
                    intent = new Intent(intent);

                    // Collect information about the target of the Intent.
                    ActivityInfo aInfo = resolveActivity(intent, resolvedTypes[i],
                            0, null, null, userId);
                    // TODO: New, check if this is correct
                    aInfo = mService.getActivityInfoForUser(aInfo, userId);

                    if (mMainStack && aInfo != null && (aInfo.applicationInfo.flags
                            & ApplicationInfo.FLAG_CANT_SAVE_STATE) != 0) {
                        throw new IllegalArgumentException(
                                "FLAG_CANT_SAVE_STATE not supported here");
                    }

                    Bundle theseOptions;
                    if (options != null && i == intents.length-1) {
                        theseOptions = options;
                    } else {
                        theseOptions = null;
                    }
                    int res = startActivityLocked(caller, intent, resolvedTypes[i],
                            aInfo, resultTo, null, -1, callingPid, callingUid, callingPackage,
                            0, theseOptions, componentSpecified, outActivity);
                    if (res < 0) {
                        return res;
                    }

                    resultTo = outActivity[0] != null ? outActivity[0].appToken : null;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        return ActivityManager.START_SUCCESS;
    }

    void reportActivityLaunchedLocked(boolean timeout, ActivityRecord r,
            long thisTime, long totalTime) {
        for (int i=mWaitingActivityLaunched.size()-1; i>=0; i--) {
            WaitResult w = mWaitingActivityLaunched.get(i);
            w.timeout = timeout;
            if (r != null) {
                w.who = new ComponentName(r.info.packageName, r.info.name);
            }
            w.thisTime = thisTime;
            w.totalTime = totalTime;
        }
        mService.notifyAll();
    }
    
    void reportActivityVisibleLocked(ActivityRecord r) {
        for (int i=mWaitingActivityVisible.size()-1; i>=0; i--) {
            WaitResult w = mWaitingActivityVisible.get(i);
            w.timeout = false;
            if (r != null) {
                w.who = new ComponentName(r.info.packageName, r.info.name);
            }
            w.totalTime = SystemClock.uptimeMillis() - w.thisTime;
            w.thisTime = w.totalTime;
        }
        mService.notifyAll();

        if (mDismissKeyguardOnNextActivity) {
            mDismissKeyguardOnNextActivity = false;
            mService.mWindowManager.dismissKeyguard();
        }
    }

    void sendActivityResultLocked(int callingUid, ActivityRecord r,
            String resultWho, int requestCode, int resultCode, Intent data) {

        if (callingUid > 0) {
            mService.grantUriPermissionFromIntentLocked(callingUid, r.packageName,
                    data, r.getUriPermissionsLocked());
        }

        if (DEBUG_RESULTS) Slog.v(TAG, "Send activity result to " + r
                + " : who=" + resultWho + " req=" + requestCode
                + " res=" + resultCode + " data=" + data);
        if (mResumedActivity == r && r.app != null && r.app.thread != null) {
            try {
                ArrayList<ResultInfo> list = new ArrayList<ResultInfo>();
                list.add(new ResultInfo(resultWho, requestCode,
                        resultCode, data));
                r.app.thread.scheduleSendResult(r.appToken, list);
                return;
            } catch (Exception e) {
                Slog.w(TAG, "Exception thrown sending result to " + r, e);
            }
        }

        r.addResultLocked(null, resultWho, requestCode, resultCode, data);
    }

    private final void stopActivityLocked(ActivityRecord r) {
        if (DEBUG_SWITCH) Slog.d(TAG, "Stopping: " + r);
        if ((r.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
                || (r.info.flags&ActivityInfo.FLAG_NO_HISTORY) != 0) {
            if (!r.finishing) {
                if (!mService.mSleeping) {
                    if (DEBUG_STATES) {
                        Slog.d(TAG, "no-history finish of " + r);
                    }
                    requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                            "no-history", false);
                } else {
                    if (DEBUG_STATES) Slog.d(TAG, "Not finishing noHistory " + r
                            + " on stop because we're just sleeping");
                }
            }
        }

        if (r.app != null && r.app.thread != null) {
            if (mMainStack) {
                if (mService.mFocusedActivity == r) {
                    mService.setFocusedActivityLocked(topRunningActivityLocked(null));
                }
            }
            r.resumeKeyDispatchingLocked();
            try {
                r.stopped = false;
                if (DEBUG_STATES) Slog.v(TAG, "Moving to STOPPING: " + r
                        + " (stop requested)");
                r.state = ActivityState.STOPPING;
                if (DEBUG_VISBILITY) Slog.v(
                        TAG, "Stopping visible=" + r.visible + " for " + r);
                if (!r.visible) {
                    mService.mWindowManager.setAppVisibility(r.appToken, false);
                }
                r.app.thread.scheduleStopActivity(r.appToken, r.visible, r.configChangeFlags);
                if (mService.isSleeping()) {
                    r.setSleeping(true);
                }
                Message msg = mHandler.obtainMessage(STOP_TIMEOUT_MSG);
                msg.obj = r;
                mHandler.sendMessageDelayed(msg, STOP_TIMEOUT);
            } catch (Exception e) {
                // Maybe just ignore exceptions here...  if the process
                // has crashed, our death notification will clean things
                // up.
                Slog.w(TAG, "Exception thrown during pause", e);
                // Just in case, assume it to be stopped.
                r.stopped = true;
                if (DEBUG_STATES) Slog.v(TAG, "Stop failed; moving to STOPPED: " + r);
                r.state = ActivityState.STOPPED;
                if (r.configDestroy) {
                    destroyActivityLocked(r, true, false, "stop-except");
                }
            }
        }
    }
    
    final ArrayList<ActivityRecord> processStoppingActivitiesLocked(
            boolean remove) {
        int N = mStoppingActivities.size();
        if (N <= 0) return null;

        ArrayList<ActivityRecord> stops = null;

        final boolean nowVisible = mResumedActivity != null
                && mResumedActivity.nowVisible
                && !mResumedActivity.waitingVisible;
        for (int i=0; i<N; i++) {
            ActivityRecord s = mStoppingActivities.get(i);
            if (localLOGV) Slog.v(TAG, "Stopping " + s + ": nowVisible="
                    + nowVisible + " waitingVisible=" + s.waitingVisible
                    + " finishing=" + s.finishing);
            if (s.waitingVisible && nowVisible) {
                mWaitingVisibleActivities.remove(s);
                s.waitingVisible = false;
                if (s.finishing) {
                    // If this activity is finishing, it is sitting on top of
                    // everyone else but we now know it is no longer needed...
                    // so get rid of it.  Otherwise, we need to go through the
                    // normal flow and hide it once we determine that it is
                    // hidden by the activities in front of it.
                    if (localLOGV) Slog.v(TAG, "Before stopping, can hide: " + s);
                    mService.mWindowManager.setAppVisibility(s.appToken, false);
                }
            }
            if ((!s.waitingVisible || mService.isSleeping()) && remove) {
                if (localLOGV) Slog.v(TAG, "Ready to stop: " + s);
                if (stops == null) {
                    stops = new ArrayList<ActivityRecord>();
                }
                stops.add(s);
                mStoppingActivities.remove(i);
                N--;
                i--;
            }
        }

        return stops;
    }

    final void scheduleIdleLocked() {
        Message msg = Message.obtain();
        msg.what = IDLE_NOW_MSG;
        mHandler.sendMessage(msg);
    }

    final ActivityRecord activityIdleInternal(IBinder token, boolean fromTimeout,
            Configuration config) {
        if (localLOGV) Slog.v(TAG, "Activity idle: " + token);

        ActivityRecord res = null;

        ArrayList<ActivityRecord> stops = null;
        ArrayList<ActivityRecord> finishes = null;
        ArrayList<ActivityRecord> thumbnails = null;
        ArrayList<UserStartedState> startingUsers = null;
        int NS = 0;
        int NF = 0;
        int NT = 0;
        IApplicationThread sendThumbnail = null;
        boolean booting = false;
        boolean enableScreen = false;
        boolean activityRemoved = false;

        synchronized (mService) {
            ActivityRecord r = ActivityRecord.forToken(token);
            if (r != null) {
                mHandler.removeMessages(IDLE_TIMEOUT_MSG, r);
                r.finishLaunchTickingLocked();
            }

            // Get the activity record.
            int index = indexOfActivityLocked(r);
            if (index >= 0) {
                res = r;

                if (fromTimeout) {
                    reportActivityLaunchedLocked(fromTimeout, r, -1, -1);
                }
                
                // This is a hack to semi-deal with a race condition
                // in the client where it can be constructed with a
                // newer configuration from when we asked it to launch.
                // We'll update with whatever configuration it now says
                // it used to launch.
                if (config != null) {
                    r.configuration = config;
                }
                
                // No longer need to keep the device awake.
                if (mResumedActivity == r && mLaunchingActivity.isHeld()) {
                    mHandler.removeMessages(LAUNCH_TIMEOUT_MSG);
                    mLaunchingActivity.release();
                }

                // We are now idle.  If someone is waiting for a thumbnail from
                // us, we can now deliver.
                r.idle = true;
                mService.scheduleAppGcsLocked();
                if (r.thumbnailNeeded && r.app != null && r.app.thread != null) {
                    sendThumbnail = r.app.thread;
                    r.thumbnailNeeded = false;
                }

                // If this activity is fullscreen, set up to hide those under it.

                if (DEBUG_VISBILITY) Slog.v(TAG, "Idle activity for " + r);
                ensureActivitiesVisibleLocked(null, 0);

                //Slog.i(TAG, "IDLE: mBooted=" + mBooted + ", fromTimeout=" + fromTimeout);
                if (mMainStack) {
                    if (!mService.mBooted) {
                        mService.mBooted = true;
                        enableScreen = true;
                    }
                }
                
            } else if (fromTimeout) {
                reportActivityLaunchedLocked(fromTimeout, null, -1, -1);
            }

            // Atomically retrieve all of the other things to do.
            stops = processStoppingActivitiesLocked(true);
            NS = stops != null ? stops.size() : 0;
            if ((NF=mFinishingActivities.size()) > 0) {
                finishes = new ArrayList<ActivityRecord>(mFinishingActivities);
                mFinishingActivities.clear();
            }
            if ((NT=mService.mCancelledThumbnails.size()) > 0) {
                thumbnails = new ArrayList<ActivityRecord>(mService.mCancelledThumbnails);
                mService.mCancelledThumbnails.clear();
            }

            if (mMainStack) {
                booting = mService.mBooting;
                mService.mBooting = false;
            }
            if (mStartingUsers.size() > 0) {
                startingUsers = new ArrayList<UserStartedState>(mStartingUsers);
                mStartingUsers.clear();
            }
        }

        int i;

        // Send thumbnail if requested.
        if (sendThumbnail != null) {
            try {
                sendThumbnail.requestThumbnail(token);
            } catch (Exception e) {
                Slog.w(TAG, "Exception thrown when requesting thumbnail", e);
                mService.sendPendingThumbnail(null, token, null, null, true);
            }
        }

        // Stop any activities that are scheduled to do so but have been
        // waiting for the next one to start.
        for (i=0; i<NS; i++) {
            ActivityRecord r = (ActivityRecord)stops.get(i);
            synchronized (mService) {
                if (r.finishing) {
                    finishCurrentActivityLocked(r, FINISH_IMMEDIATELY, false);
                } else {
                    stopActivityLocked(r);
                }
            }
        }

        // Finish any activities that are scheduled to do so but have been
        // waiting for the next one to start.
        for (i=0; i<NF; i++) {
            ActivityRecord r = (ActivityRecord)finishes.get(i);
            synchronized (mService) {
                activityRemoved = destroyActivityLocked(r, true, false, "finish-idle");
            }
        }

        // Report back to any thumbnail receivers.
        for (i=0; i<NT; i++) {
            ActivityRecord r = (ActivityRecord)thumbnails.get(i);
            mService.sendPendingThumbnail(r, null, null, null, true);
        }

        if (booting) {
            mService.finishBooting();
        } else if (startingUsers != null) {
            for (i=0; i<startingUsers.size(); i++) {
                mService.finishUserSwitch(startingUsers.get(i));
            }
        }

        mService.trimApplications();
        //dump();
        //mWindowManager.dump();

        if (enableScreen) {
            mService.enableScreenAfterBoot();
        }

        if (activityRemoved) {
            synchronized (mService) {
                resumeTopActivityLocked(null);
            }
        }

        return res;
    }

    /**
     * @return Returns true if the activity is being finished, false if for
     * some reason it is being left as-is.
     */
    final boolean requestFinishActivityLocked(IBinder token, int resultCode,
            Intent resultData, String reason, boolean oomAdj) {
        int index = indexOfTokenLocked(token);
        if (DEBUG_RESULTS || DEBUG_STATES) Slog.v(
                TAG, "Finishing activity @" + index + ": token=" + token
                + ", result=" + resultCode + ", data=" + resultData
                + ", reason=" + reason);
        if (index < 0) {
            return false;
        }
        ActivityRecord r = mHistory.get(index);

        finishActivityLocked(r, index, resultCode, resultData, reason, oomAdj);
        return true;
    }

    final void finishSubActivityLocked(IBinder token, String resultWho, int requestCode) {
        ActivityRecord self = isInStackLocked(token);
        if (self == null) {
            return;
        }

        int i;
        for (i=mHistory.size()-1; i>=0; i--) {
            ActivityRecord r = (ActivityRecord)mHistory.get(i);
            if (r.resultTo == self && r.requestCode == requestCode) {
                if ((r.resultWho == null && resultWho == null) ||
                    (r.resultWho != null && r.resultWho.equals(resultWho))) {
                    finishActivityLocked(r, i,
                            Activity.RESULT_CANCELED, null, "request-sub", false);
                }
            }
        }
        mService.updateOomAdjLocked();
    }

    final boolean finishActivityAffinityLocked(IBinder token) {
        int index = indexOfTokenLocked(token);
        if (DEBUG_RESULTS) Slog.v(
                TAG, "Finishing activity affinity @" + index + ": token=" + token);
        if (index < 0) {
            return false;
        }
        ActivityRecord r = mHistory.get(index);

        while (index >= 0) {
            ActivityRecord cur = mHistory.get(index);
            if (cur.task != r.task) {
                break;
            }
            if (cur.taskAffinity == null && r.taskAffinity != null) {
                break;
            }
            if (cur.taskAffinity != null && !cur.taskAffinity.equals(r.taskAffinity)) {
                break;
            }
            finishActivityLocked(cur, index, Activity.RESULT_CANCELED, null,
                    "request-affinity", true);
            index--;
        }
        return true;
    }

    final void finishActivityResultsLocked(ActivityRecord r, int resultCode, Intent resultData) {
        // send the result
        ActivityRecord resultTo = r.resultTo;
        if (resultTo != null) {
            if (DEBUG_RESULTS) Slog.v(TAG, "Adding result to " + resultTo
                    + " who=" + r.resultWho + " req=" + r.requestCode
                    + " res=" + resultCode + " data=" + resultData);
            if (r.info.applicationInfo.uid > 0) {
                mService.grantUriPermissionFromIntentLocked(r.info.applicationInfo.uid,
                        resultTo.packageName, resultData,
                        resultTo.getUriPermissionsLocked());
            }
            resultTo.addResultLocked(r, r.resultWho, r.requestCode, resultCode,
                                     resultData);
            r.resultTo = null;
        }
        else if (DEBUG_RESULTS) Slog.v(TAG, "No result destination from " + r);

        // Make sure this HistoryRecord is not holding on to other resources,
        // because clients have remote IPC references to this object so we
        // can't assume that will go away and want to avoid circular IPC refs.
        r.results = null;
        r.pendingResults = null;
        r.newIntents = null;
        r.icicle = null;
    }

    /**
     * @return Returns true if this activity has been removed from the history
     * list, or false if it is still in the list and will be removed later.
     */
    final boolean finishActivityLocked(ActivityRecord r, int index,
            int resultCode, Intent resultData, String reason, boolean oomAdj) {
        return finishActivityLocked(r, index, resultCode, resultData, reason, false, oomAdj);
    }

    /**
     * @return Returns true if this activity has been removed from the history
     * list, or false if it is still in the list and will be removed later.
     */
    final boolean finishActivityLocked(ActivityRecord r, int index, int resultCode,
            Intent resultData, String reason, boolean immediate, boolean oomAdj) {
        if (r.finishing) {
            Slog.w(TAG, "Duplicate finish request for " + r);
            return false;
        }

        r.makeFinishing();
        EventLog.writeEvent(EventLogTags.AM_FINISH_ACTIVITY,
                r.userId, System.identityHashCode(r),
                r.task.taskId, r.shortComponentName, reason);
        if (index < (mHistory.size()-1)) {
            ActivityRecord next = mHistory.get(index+1);
            if (next.task == r.task) {
                if (r.frontOfTask) {
                    // The next activity is now the front of the task.
                    next.frontOfTask = true;
                }
                if ((r.intent.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) != 0) {
                    // If the caller asked that this activity (and all above it)
                    // be cleared when the task is reset, don't lose that information,
                    // but propagate it up to the next activity.
                    next.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                }
            }
        }

        r.pauseKeyDispatchingLocked();
        if (mMainStack) {
            if (mService.mFocusedActivity == r) {
                mService.setFocusedActivityLocked(topRunningActivityLocked(null));
            }
        }

        finishActivityResultsLocked(r, resultCode, resultData);
        
        if (mService.mPendingThumbnails.size() > 0) {
            // There are clients waiting to receive thumbnails so, in case
            // this is an activity that someone is waiting for, add it
            // to the pending list so we can correctly update the clients.
            mService.mCancelledThumbnails.add(r);
        }

        if (immediate) {
            return finishCurrentActivityLocked(r, index,
                    FINISH_IMMEDIATELY, oomAdj) == null;
        } else if (mResumedActivity == r) {
            boolean endTask = index <= 0
                    || (mHistory.get(index-1)).task != r.task;
            if (DEBUG_TRANSITION) Slog.v(TAG,
                    "Prepare close transition: finishing " + r);
            mService.mWindowManager.prepareAppTransition(endTask
                    ? AppTransition.TRANSIT_TASK_CLOSE
                    : AppTransition.TRANSIT_ACTIVITY_CLOSE, false);
    
            // Tell window manager to prepare for this one to be removed.
            mService.mWindowManager.setAppVisibility(r.appToken, false);
                
            if (mPausingActivity == null) {
                if (DEBUG_PAUSE) Slog.v(TAG, "Finish needs to pause: " + r);
                if (DEBUG_USER_LEAVING) Slog.v(TAG, "finish() => pause with userLeaving=false");
                startPausingLocked(false, false);
            }

        } else if (r.state != ActivityState.PAUSING) {
            // If the activity is PAUSING, we will complete the finish once
            // it is done pausing; else we can just directly finish it here.
            if (DEBUG_PAUSE) Slog.v(TAG, "Finish not pausing: " + r);
            return finishCurrentActivityLocked(r, index,
                    FINISH_AFTER_PAUSE, oomAdj) == null;
        } else {
            if (DEBUG_PAUSE) Slog.v(TAG, "Finish waiting for pause of: " + r);
        }

        return false;
    }

    private static final int FINISH_IMMEDIATELY = 0;
    private static final int FINISH_AFTER_PAUSE = 1;
    private static final int FINISH_AFTER_VISIBLE = 2;

    private final ActivityRecord finishCurrentActivityLocked(ActivityRecord r,
            int mode, boolean oomAdj) {
        final int index = indexOfActivityLocked(r);
        if (index < 0) {
            return null;
        }

        return finishCurrentActivityLocked(r, index, mode, oomAdj);
    }

    private final ActivityRecord finishCurrentActivityLocked(ActivityRecord r,
            int index, int mode, boolean oomAdj) {
        // First things first: if this activity is currently visible,
        // and the resumed activity is not yet visible, then hold off on
        // finishing until the resumed one becomes visible.
        if (mode == FINISH_AFTER_VISIBLE && r.nowVisible) {
            if (!mStoppingActivities.contains(r)) {
                mStoppingActivities.add(r);
                if (mStoppingActivities.size() > 3) {
                    // If we already have a few activities waiting to stop,
                    // then give up on things going idle and start clearing
                    // them out.
                    scheduleIdleLocked();
                } else {
                    checkReadyForSleepLocked();
                }
            }
            if (DEBUG_STATES) Slog.v(TAG, "Moving to STOPPING: " + r
                    + " (finish requested)");
            r.state = ActivityState.STOPPING;
            if (oomAdj) {
                mService.updateOomAdjLocked();
            }
            return r;
        }

        // make sure the record is cleaned out of other places.
        mStoppingActivities.remove(r);
        mGoingToSleepActivities.remove(r);
        mWaitingVisibleActivities.remove(r);
        if (mResumedActivity == r) {
            mResumedActivity = null;
        }
        final ActivityState prevState = r.state;
        if (DEBUG_STATES) Slog.v(TAG, "Moving to FINISHING: " + r);
        r.state = ActivityState.FINISHING;

        if (mode == FINISH_IMMEDIATELY
                || prevState == ActivityState.STOPPED
                || prevState == ActivityState.INITIALIZING) {
            // If this activity is already stopped, we can just finish
            // it right now.
            boolean activityRemoved = destroyActivityLocked(r, true,
                    oomAdj, "finish-imm");
            if (activityRemoved) {
                resumeTopActivityLocked(null);
            }
            return activityRemoved ? null : r;
        } else {
            // Need to go through the full pause cycle to get this
            // activity into the stopped state and then finish it.
            if (localLOGV) Slog.v(TAG, "Enqueueing pending finish: " + r);
            mFinishingActivities.add(r);
            resumeTopActivityLocked(null);
        }
        return r;
    }

    /**
     * Perform the common clean-up of an activity record.  This is called both
     * as part of destroyActivityLocked() (when destroying the client-side
     * representation) and cleaning things up as a result of its hosting
     * processing going away, in which case there is no remaining client-side
     * state to destroy so only the cleanup here is needed.
     */
    final void cleanUpActivityLocked(ActivityRecord r, boolean cleanServices,
            boolean setState) {
        if (mResumedActivity == r) {
            mResumedActivity = null;
        }
        if (mService.mFocusedActivity == r) {
            mService.mFocusedActivity = null;
        }

        r.configDestroy = false;
        r.frozenBeforeDestroy = false;

        if (setState) {
            if (DEBUG_STATES) Slog.v(TAG, "Moving to DESTROYED: " + r + " (cleaning up)");
            r.state = ActivityState.DESTROYED;
            if (DEBUG_APP) Slog.v(TAG, "Clearing app during cleanUp for activity " + r);
            r.app = null;
        }

        // Make sure this record is no longer in the pending finishes list.
        // This could happen, for example, if we are trimming activities
        // down to the max limit while they are still waiting to finish.
        mFinishingActivities.remove(r);
        mWaitingVisibleActivities.remove(r);
        
        // Remove any pending results.
        if (r.finishing && r.pendingResults != null) {
            for (WeakReference<PendingIntentRecord> apr : r.pendingResults) {
                PendingIntentRecord rec = apr.get();
                if (rec != null) {
                    mService.cancelIntentSenderLocked(rec, false);
                }
            }
            r.pendingResults = null;
        }

        if (cleanServices) {
            cleanUpActivityServicesLocked(r);            
        }

        if (mService.mPendingThumbnails.size() > 0) {
            // There are clients waiting to receive thumbnails so, in case
            // this is an activity that someone is waiting for, add it
            // to the pending list so we can correctly update the clients.
            mService.mCancelledThumbnails.add(r);
        }

        // Get rid of any pending idle timeouts.
        removeTimeoutsForActivityLocked(r);
    }

    private void removeTimeoutsForActivityLocked(ActivityRecord r) {
        mHandler.removeMessages(PAUSE_TIMEOUT_MSG, r);
        mHandler.removeMessages(STOP_TIMEOUT_MSG, r);
        mHandler.removeMessages(IDLE_TIMEOUT_MSG, r);
        mHandler.removeMessages(DESTROY_TIMEOUT_MSG, r);
        r.finishLaunchTickingLocked();
    }

    final void removeActivityFromHistoryLocked(ActivityRecord r) {
        finishActivityResultsLocked(r, Activity.RESULT_CANCELED, null);
        r.makeFinishing();
        if (DEBUG_ADD_REMOVE) {
            RuntimeException here = new RuntimeException("here");
            here.fillInStackTrace();
            Slog.i(TAG, "Removing activity " + r + " from stack");
        }
        mHistory.remove(r);
        r.takeFromHistory();
        removeTimeoutsForActivityLocked(r);
        if (DEBUG_STATES) Slog.v(TAG, "Moving to DESTROYED: " + r
                + " (removed from history)");
        r.state = ActivityState.DESTROYED;
        if (DEBUG_APP) Slog.v(TAG, "Clearing app during remove for activity " + r);
        r.app = null;
        mService.mWindowManager.removeAppToken(r.appToken);
        if (VALIDATE_TOKENS) {
            validateAppTokensLocked();
        }
        cleanUpActivityServicesLocked(r);
        r.removeUriPermissionsLocked();
    }
    
    /**
     * Perform clean-up of service connections in an activity record.
     */
    final void cleanUpActivityServicesLocked(ActivityRecord r) {
        // Throw away any services that have been bound by this activity.
        if (r.connections != null) {
            Iterator<ConnectionRecord> it = r.connections.iterator();
            while (it.hasNext()) {
                ConnectionRecord c = it.next();
                mService.mServices.removeConnectionLocked(c, null, r);
            }
            r.connections = null;
        }
    }

    final void scheduleDestroyActivities(ProcessRecord owner, boolean oomAdj, String reason) {
        Message msg = mHandler.obtainMessage(DESTROY_ACTIVITIES_MSG);
        msg.obj = new ScheduleDestroyArgs(owner, oomAdj, reason);
        mHandler.sendMessage(msg);
    }

    final void destroyActivitiesLocked(ProcessRecord owner, boolean oomAdj, String reason) {
        boolean lastIsOpaque = false;
        boolean activityRemoved = false;
        for (int i=mHistory.size()-1; i>=0; i--) {
            ActivityRecord r = mHistory.get(i);
            if (r.finishing) {
                continue;
            }
            if (r.fullscreen) {
                lastIsOpaque = true;
            }
            if (owner != null && r.app != owner) {
                continue;
            }
            if (!lastIsOpaque) {
                continue;
            }
            // We can destroy this one if we have its icicle saved and
            // it is not in the process of pausing/stopping/finishing.
            if (r.app != null && r != mResumedActivity && r != mPausingActivity
                    && r.haveState && !r.visible && r.stopped
                    && r.state != ActivityState.DESTROYING
                    && r.state != ActivityState.DESTROYED) {
                if (DEBUG_SWITCH) Slog.v(TAG, "Destroying " + r + " in state " + r.state
                        + " resumed=" + mResumedActivity
                        + " pausing=" + mPausingActivity);
                if (destroyActivityLocked(r, true, oomAdj, reason)) {
                    activityRemoved = true;
                }
            }
        }
        if (activityRemoved) {
            resumeTopActivityLocked(null);
        }
    }

    /**
     * Destroy the current CLIENT SIDE instance of an activity.  This may be
     * called both when actually finishing an activity, or when performing
     * a configuration switch where we destroy the current client-side object
     * but then create a new client-side object for this same HistoryRecord.
     */
    final boolean destroyActivityLocked(ActivityRecord r,
            boolean removeFromApp, boolean oomAdj, String reason) {
        if (DEBUG_SWITCH || DEBUG_CLEANUP) Slog.v(
            TAG, "Removing activity from " + reason + ": token=" + r
              + ", app=" + (r.app != null ? r.app.processName : "(null)"));
        EventLog.writeEvent(EventLogTags.AM_DESTROY_ACTIVITY,
                r.userId, System.identityHashCode(r),
                r.task.taskId, r.shortComponentName, reason);

        boolean removedFromHistory = false;

        cleanUpActivityLocked(r, false, false);

        final boolean hadApp = r.app != null;

        if (hadApp) {
            if (removeFromApp) {
                int idx = r.app.activities.indexOf(r);
                if (idx >= 0) {
                    r.app.activities.remove(idx);
                }
                if (mService.mHeavyWeightProcess == r.app && r.app.activities.size() <= 0) {
                    mService.mHeavyWeightProcess = null;
                    mService.mHandler.sendEmptyMessage(
                            ActivityManagerService.CANCEL_HEAVY_NOTIFICATION_MSG);
                }
                if (r.app.activities.size() == 0) {
                    // No longer have activities, so update oom adj.
                    mService.updateOomAdjLocked();
                }
            }

            boolean skipDestroy = false;
            
            try {
                if (DEBUG_SWITCH) Slog.i(TAG, "Destroying: " + r);
                r.app.thread.scheduleDestroyActivity(r.appToken, r.finishing,
                        r.configChangeFlags);
            } catch (Exception e) {
                // We can just ignore exceptions here...  if the process
                // has crashed, our death notification will clean things
                // up.
                //Slog.w(TAG, "Exception thrown during finish", e);
                if (r.finishing) {
                    removeActivityFromHistoryLocked(r);
                    removedFromHistory = true;
                    skipDestroy = true;
                }
            }

            r.nowVisible = false;
            
            // If the activity is finishing, we need to wait on removing it
            // from the list to give it a chance to do its cleanup.  During
            // that time it may make calls back with its token so we need to
            // be able to find it on the list and so we don't want to remove
            // it from the list yet.  Otherwise, we can just immediately put
            // it in the destroyed state since we are not removing it from the
            // list.
            if (r.finishing && !skipDestroy) {
                if (DEBUG_STATES) Slog.v(TAG, "Moving to DESTROYING: " + r
                        + " (destroy requested)");
                r.state = ActivityState.DESTROYING;
                Message msg = mHandler.obtainMessage(DESTROY_TIMEOUT_MSG);
                msg.obj = r;
                mHandler.sendMessageDelayed(msg, DESTROY_TIMEOUT);
            } else {
                if (DEBUG_STATES) Slog.v(TAG, "Moving to DESTROYED: " + r
                        + " (destroy skipped)");
                r.state = ActivityState.DESTROYED;
                if (DEBUG_APP) Slog.v(TAG, "Clearing app during destroy for activity " + r);
                r.app = null;
            }
        } else {
            // remove this record from the history.
            if (r.finishing) {
                removeActivityFromHistoryLocked(r);
                removedFromHistory = true;
            } else {
                if (DEBUG_STATES) Slog.v(TAG, "Moving to DESTROYED: " + r
                        + " (no app)");
                r.state = ActivityState.DESTROYED;
                if (DEBUG_APP) Slog.v(TAG, "Clearing app during destroy for activity " + r);
                r.app = null;
            }
        }

        r.configChangeFlags = 0;
        
        if (!mLRUActivities.remove(r) && hadApp) {
            Slog.w(TAG, "Activity " + r + " being finished, but not in LRU list");
        }
        
        return removedFromHistory;
    }

    final void activityDestroyed(IBinder token) {
        synchronized (mService) {
            final long origId = Binder.clearCallingIdentity();
            try {
                ActivityRecord r = ActivityRecord.forToken(token);
                if (r != null) {
                    mHandler.removeMessages(DESTROY_TIMEOUT_MSG, r);
                }

                int index = indexOfActivityLocked(r);
                if (index >= 0) {
                    if (r.state == ActivityState.DESTROYING) {
                        cleanUpActivityLocked(r, true, false);
                        removeActivityFromHistoryLocked(r);
                    }
                }
                resumeTopActivityLocked(null);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }
    
    private void removeHistoryRecordsForAppLocked(ArrayList list, ProcessRecord app,
            String listName) {
        int i = list.size();
        if (DEBUG_CLEANUP) Slog.v(
            TAG, "Removing app " + app + " from list " + listName
            + " with " + i + " entries");
        while (i > 0) {
            i--;
            ActivityRecord r = (ActivityRecord)list.get(i);
            if (DEBUG_CLEANUP) Slog.v(TAG, "Record #" + i + " " + r);
            if (r.app == app) {
                if (DEBUG_CLEANUP) Slog.v(TAG, "---> REMOVING this entry!");
                list.remove(i);
                removeTimeoutsForActivityLocked(r);
            }
        }
    }

    boolean removeHistoryRecordsForAppLocked(ProcessRecord app) {
        removeHistoryRecordsForAppLocked(mLRUActivities, app, "mLRUActivities");
        removeHistoryRecordsForAppLocked(mStoppingActivities, app, "mStoppingActivities");
        removeHistoryRecordsForAppLocked(mGoingToSleepActivities, app, "mGoingToSleepActivities");
        removeHistoryRecordsForAppLocked(mWaitingVisibleActivities, app,
                "mWaitingVisibleActivities");
        removeHistoryRecordsForAppLocked(mFinishingActivities, app, "mFinishingActivities");

        boolean hasVisibleActivities = false;

        // Clean out the history list.
        int i = mHistory.size();
        if (DEBUG_CLEANUP) Slog.v(
            TAG, "Removing app " + app + " from history with " + i + " entries");
        while (i > 0) {
            i--;
            ActivityRecord r = (ActivityRecord)mHistory.get(i);
            if (DEBUG_CLEANUP) Slog.v(
                TAG, "Record #" + i + " " + r + ": app=" + r.app);
            if (r.app == app) {
                boolean remove;
                if ((!r.haveState && !r.stateNotNeeded) || r.finishing) {
                    // Don't currently have state for the activity, or
                    // it is finishing -- always remove it.
                    remove = true;
                } else if (r.launchCount > 2 &&
                        r.lastLaunchTime > (SystemClock.uptimeMillis()-60000)) {
                    // We have launched this activity too many times since it was
                    // able to run, so give up and remove it.
                    remove = true;
                } else {
                    // The process may be gone, but the activity lives on!
                    remove = false;
                }
                if (remove) {
                    if (ActivityStack.DEBUG_ADD_REMOVE || DEBUG_CLEANUP) {
                        RuntimeException here = new RuntimeException("here");
                        here.fillInStackTrace();
                        Slog.i(TAG, "Removing activity " + r + " from stack at " + i
                                + ": haveState=" + r.haveState
                                + " stateNotNeeded=" + r.stateNotNeeded
                                + " finishing=" + r.finishing
                                + " state=" + r.state, here);
                    }
                    if (!r.finishing) {
                        Slog.w(TAG, "Force removing " + r + ": app died, no saved state");
                        EventLog.writeEvent(EventLogTags.AM_FINISH_ACTIVITY,
                                r.userId, System.identityHashCode(r),
                                r.task.taskId, r.shortComponentName,
                                "proc died without state saved");
                    }
                    removeActivityFromHistoryLocked(r);

                } else {
                    // We have the current state for this activity, so
                    // it can be restarted later when needed.
                    if (localLOGV) Slog.v(
                        TAG, "Keeping entry, setting app to null");
                    if (r.visible) {
                        hasVisibleActivities = true;
                    }
                    if (DEBUG_APP) Slog.v(TAG, "Clearing app during removeHistory for activity "
                            + r);
                    r.app = null;
                    r.nowVisible = false;
                    if (!r.haveState) {
                        if (ActivityStack.DEBUG_SAVED_STATE) Slog.i(TAG,
                                "App died, clearing saved state of " + r);
                        r.icicle = null;
                    }
                }

                r.stack.cleanUpActivityLocked(r, true, true);
            }
        }

        return hasVisibleActivities;
    }
    
    /**
     * Move the current home activity's task (if one exists) to the front
     * of the stack.
     */
    final void moveHomeToFrontLocked() {
        TaskRecord homeTask = null;
        for (int i=mHistory.size()-1; i>=0; i--) {
            ActivityRecord hr = mHistory.get(i);
            if (hr.isHomeActivity) {
                homeTask = hr.task;
                break;
            }
        }
        if (homeTask != null) {
            moveTaskToFrontLocked(homeTask, null, null);
        }
    }

    final void updateTransitLocked(int transit, Bundle options) {
        if (options != null) {
            ActivityRecord r = topRunningActivityLocked(null);
            if (r != null && r.state != ActivityState.RESUMED) {
                r.updateOptionsLocked(options);
            } else {
                ActivityOptions.abort(options);
            }
        }
        mService.mWindowManager.prepareAppTransition(transit, false);
    }

    final void moveTaskToFrontLocked(TaskRecord tr, ActivityRecord reason, Bundle options) {
        if (DEBUG_SWITCH) Slog.v(TAG, "moveTaskToFront: " + tr);

        final int task = tr.taskId;
        int top = mHistory.size()-1;

        if (top < 0 || (mHistory.get(top)).task.taskId == task) {
            // nothing to do!
            if (reason != null &&
                    (reason.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
                ActivityOptions.abort(options);
            } else {
                updateTransitLocked(AppTransition.TRANSIT_TASK_TO_FRONT, options);
            }
            return;
        }

        ArrayList<IBinder> moved = new ArrayList<IBinder>();

        // Applying the affinities may have removed entries from the history,
        // so get the size again.
        top = mHistory.size()-1;
        int pos = top;

        // Shift all activities with this task up to the top
        // of the stack, keeping them in the same internal order.
        while (pos >= 0) {
            ActivityRecord r = mHistory.get(pos);
            if (localLOGV) Slog.v(
                TAG, "At " + pos + " ckp " + r.task + ": " + r);
            if (r.task.taskId == task) {
                if (localLOGV) Slog.v(TAG, "Removing and adding at " + top);
                if (DEBUG_ADD_REMOVE) {
                    RuntimeException here = new RuntimeException("here");
                    here.fillInStackTrace();
                    Slog.i(TAG, "Removing and adding activity " + r + " to stack at " + top, here);
                }
                mHistory.remove(pos);
                mHistory.add(top, r);
                moved.add(0, r.appToken);
                top--;
            }
            pos--;
        }

        if (DEBUG_TRANSITION) Slog.v(TAG,
                "Prepare to front transition: task=" + tr);
        if (reason != null &&
                (reason.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
            mService.mWindowManager.prepareAppTransition(
                    AppTransition.TRANSIT_NONE, false);
            ActivityRecord r = topRunningActivityLocked(null);
            if (r != null) {
                mNoAnimActivities.add(r);
            }
            ActivityOptions.abort(options);
        } else {
            updateTransitLocked(AppTransition.TRANSIT_TASK_TO_FRONT, options);
        }
        
        mService.mWindowManager.moveAppTokensToTop(moved);
        if (VALIDATE_TOKENS) {
            validateAppTokensLocked();
        }

        finishTaskMoveLocked(task);
        EventLog.writeEvent(EventLogTags.AM_TASK_TO_FRONT, tr.userId, task);
    }

    private final void finishTaskMoveLocked(int task) {
        resumeTopActivityLocked(null);
    }

    /**
     * Worker method for rearranging history stack.  Implements the function of moving all 
     * activities for a specific task (gathering them if disjoint) into a single group at the 
     * bottom of the stack.
     * 
     * If a watcher is installed, the action is preflighted and the watcher has an opportunity
     * to premeptively cancel the move.
     * 
     * @param task The taskId to collect and move to the bottom.
     * @return Returns true if the move completed, false if not.
     */
    final boolean moveTaskToBackLocked(int task, ActivityRecord reason) {
        Slog.i(TAG, "moveTaskToBack: " + task);
        
        // If we have a watcher, preflight the move before committing to it.  First check
        // for *other* available tasks, but if none are available, then try again allowing the
        // current task to be selected.
        if (mMainStack && mService.mController != null) {
            ActivityRecord next = topRunningActivityLocked(null, task);
            if (next == null) {
                next = topRunningActivityLocked(null, 0);
            }
            if (next != null) {
                // ask watcher if this is allowed
                boolean moveOK = true;
                try {
                    moveOK = mService.mController.activityResuming(next.packageName);
                } catch (RemoteException e) {
                    mService.mController = null;
                }
                if (!moveOK) {
                    return false;
                }
            }
        }

        ArrayList<IBinder> moved = new ArrayList<IBinder>();

        if (DEBUG_TRANSITION) Slog.v(TAG,
                "Prepare to back transition: task=" + task);
        
        final int N = mHistory.size();
        int bottom = 0;
        int pos = 0;

        // Shift all activities with this task down to the bottom
        // of the stack, keeping them in the same internal order.
        while (pos < N) {
            ActivityRecord r = mHistory.get(pos);
            if (localLOGV) Slog.v(
                TAG, "At " + pos + " ckp " + r.task + ": " + r);
            if (r.task.taskId == task) {
                if (localLOGV) Slog.v(TAG, "Removing and adding at " + (N-1));
                if (DEBUG_ADD_REMOVE) {
                    RuntimeException here = new RuntimeException("here");
                    here.fillInStackTrace();
                    Slog.i(TAG, "Removing and adding activity " + r + " to stack at "
                            + bottom, here);
                }
                mHistory.remove(pos);
                mHistory.add(bottom, r);
                moved.add(r.appToken);
                bottom++;
            }
            pos++;
        }

        if (reason != null &&
                (reason.intent.getFlags()&Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
            mService.mWindowManager.prepareAppTransition(
                    AppTransition.TRANSIT_NONE, false);
            ActivityRecord r = topRunningActivityLocked(null);
            if (r != null) {
                mNoAnimActivities.add(r);
            }
        } else {
            mService.mWindowManager.prepareAppTransition(
                    AppTransition.TRANSIT_TASK_TO_BACK, false);
        }
        mService.mWindowManager.moveAppTokensToBottom(moved);
        if (VALIDATE_TOKENS) {
            validateAppTokensLocked();
        }

        finishTaskMoveLocked(task);
        return true;
    }

    public ActivityManager.TaskThumbnails getTaskThumbnailsLocked(TaskRecord tr) {
        TaskAccessInfo info = getTaskAccessInfoLocked(tr.taskId, true);
        ActivityRecord resumed = mResumedActivity;
        if (resumed != null && resumed.thumbHolder == tr) {
            info.mainThumbnail = resumed.stack.screenshotActivities(resumed);
        }
        if (info.mainThumbnail == null) {
            info.mainThumbnail = tr.lastThumbnail;
        }
        return info;
    }

    public Bitmap getTaskTopThumbnailLocked(TaskRecord tr) {
        ActivityRecord resumed = mResumedActivity;
        if (resumed != null && resumed.task == tr) {
            // This task is the current resumed task, we just need to take
            // a screenshot of it and return that.
            return resumed.stack.screenshotActivities(resumed);
        }
        // Return the information about the task, to figure out the top
        // thumbnail to return.
        TaskAccessInfo info = getTaskAccessInfoLocked(tr.taskId, true);
        if (info.numSubThumbbails <= 0) {
            return info.mainThumbnail != null ? info.mainThumbnail : tr.lastThumbnail;
        } else {
            return info.subtasks.get(info.numSubThumbbails-1).holder.lastThumbnail;
        }
    }

    public ActivityRecord removeTaskActivitiesLocked(int taskId, int subTaskIndex,
            boolean taskRequired) {
        TaskAccessInfo info = getTaskAccessInfoLocked(taskId, false);
        if (info.root == null) {
            if (taskRequired) {
                Slog.w(TAG, "removeTaskLocked: unknown taskId " + taskId);
            }
            return null;
        }

        if (subTaskIndex < 0) {
            // Just remove the entire task.
            performClearTaskAtIndexLocked(taskId, info.rootIndex);
            return info.root;
        }

        if (subTaskIndex >= info.subtasks.size()) {
            if (taskRequired) {
                Slog.w(TAG, "removeTaskLocked: unknown subTaskIndex " + subTaskIndex);
            }
            return null;
        }

        // Remove all of this task's activities starting at the sub task.
        TaskAccessInfo.SubTask subtask = info.subtasks.get(subTaskIndex);
        performClearTaskAtIndexLocked(taskId, subtask.index);
        return subtask.activity;
    }

    public TaskAccessInfo getTaskAccessInfoLocked(int taskId, boolean inclThumbs) {
        final TaskAccessInfo thumbs = new TaskAccessInfo();
        // How many different sub-thumbnails?
        final int NA = mHistory.size();
        int j = 0;
        ThumbnailHolder holder = null;
        while (j < NA) {
            ActivityRecord ar = mHistory.get(j);
            if (!ar.finishing && ar.task.taskId == taskId) {
                thumbs.root = ar;
                thumbs.rootIndex = j;
                holder = ar.thumbHolder;
                if (holder != null) {
                    thumbs.mainThumbnail = holder.lastThumbnail;
                }
                j++;
                break;
            }
            j++;
        }

        if (j >= NA) {
            return thumbs;
        }

        ArrayList<TaskAccessInfo.SubTask> subtasks = new ArrayList<TaskAccessInfo.SubTask>();
        thumbs.subtasks = subtasks;
        while (j < NA) {
            ActivityRecord ar = mHistory.get(j);
            j++;
            if (ar.finishing) {
                continue;
            }
            if (ar.task.taskId != taskId) {
                break;
            }
            if (ar.thumbHolder != holder && holder != null) {
                thumbs.numSubThumbbails++;
                holder = ar.thumbHolder;
                TaskAccessInfo.SubTask sub = new TaskAccessInfo.SubTask();
                sub.holder = holder;
                sub.activity = ar;
                sub.index = j-1;
                subtasks.add(sub);
            }
        }
        if (thumbs.numSubThumbbails > 0) {
            thumbs.retriever = new IThumbnailRetriever.Stub() {
                public Bitmap getThumbnail(int index) {
                    if (index < 0 || index >= thumbs.subtasks.size()) {
                        return null;
                    }
                    TaskAccessInfo.SubTask sub = thumbs.subtasks.get(index);
                    ActivityRecord resumed = mResumedActivity;
                    if (resumed != null && resumed.thumbHolder == sub.holder) {
                        return resumed.stack.screenshotActivities(resumed);
                    }
                    return sub.holder.lastThumbnail;
                }
            };
        }
        return thumbs;
    }

    private final void logStartActivity(int tag, ActivityRecord r,
            TaskRecord task) {
        final Uri data = r.intent.getData();
        final String strData = data != null ? data.toSafeString() : null;

        EventLog.writeEvent(tag,
                r.userId, System.identityHashCode(r), task.taskId,
                r.shortComponentName, r.intent.getAction(),
                r.intent.getType(), strData, r.intent.getFlags());
    }

    /**
     * Make sure the given activity matches the current configuration.  Returns
     * false if the activity had to be destroyed.  Returns true if the
     * configuration is the same, or the activity will remain running as-is
     * for whatever reason.  Ensures the HistoryRecord is updated with the
     * correct configuration and all other bookkeeping is handled.
     */
    final boolean ensureActivityConfigurationLocked(ActivityRecord r,
            int globalChanges) {
        if (mConfigWillChange) {
            if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Skipping config check (will change): " + r);
            return true;
        }
        
        if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                "Ensuring correct configuration: " + r);
        
        // Short circuit: if the two configurations are the exact same
        // object (the common case), then there is nothing to do.
        Configuration newConfig = mService.mConfiguration;
        if (r.configuration == newConfig && !r.forceNewConfig) {
            if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Configuration unchanged in " + r);
            return true;
        }
        
        // We don't worry about activities that are finishing.
        if (r.finishing) {
            if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Configuration doesn't matter in finishing " + r);
            r.stopFreezingScreenLocked(false);
            return true;
        }
        
        // Okay we now are going to make this activity have the new config.
        // But then we need to figure out how it needs to deal with that.
        Configuration oldConfig = r.configuration;
        r.configuration = newConfig;

        // Determine what has changed.  May be nothing, if this is a config
        // that has come back from the app after going idle.  In that case
        // we just want to leave the official config object now in the
        // activity and do nothing else.
        final int changes = oldConfig.diff(newConfig);
        if (changes == 0 && !r.forceNewConfig) {
            if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Configuration no differences in " + r);
            return true;
        }

        // If the activity isn't currently running, just leave the new
        // configuration and it will pick that up next time it starts.
        if (r.app == null || r.app.thread == null) {
            if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                    "Configuration doesn't matter not running " + r);
            r.stopFreezingScreenLocked(false);
            r.forceNewConfig = false;
            return true;
        }
        
        // Figure out how to handle the changes between the configurations.
        if (DEBUG_SWITCH || DEBUG_CONFIGURATION) {
            Slog.v(TAG, "Checking to restart " + r.info.name + ": changed=0x"
                    + Integer.toHexString(changes) + ", handles=0x"
                    + Integer.toHexString(r.info.getRealConfigChanged())
                    + ", newConfig=" + newConfig);
        }
        if ((changes&(~r.info.getRealConfigChanged())) != 0 || r.forceNewConfig) {
            // Aha, the activity isn't handling the change, so DIE DIE DIE.
            r.configChangeFlags |= changes;
            r.startFreezingScreenLocked(r.app, globalChanges);
            r.forceNewConfig = false;
            if (r.app == null || r.app.thread == null) {
                if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                        "Config is destroying non-running " + r);
                destroyActivityLocked(r, true, false, "config");
            } else if (r.state == ActivityState.PAUSING) {
                // A little annoying: we are waiting for this activity to
                // finish pausing.  Let's not do anything now, but just
                // flag that it needs to be restarted when done pausing.
                if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                        "Config is skipping already pausing " + r);
                r.configDestroy = true;
                return true;
            } else if (r.state == ActivityState.RESUMED) {
                // Try to optimize this case: the configuration is changing
                // and we need to restart the top, resumed activity.
                // Instead of doing the normal handshaking, just say
                // "restart!".
                if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                        "Config is relaunching resumed " + r);
                relaunchActivityLocked(r, r.configChangeFlags, true);
                r.configChangeFlags = 0;
            } else {
                if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.v(TAG,
                        "Config is relaunching non-resumed " + r);
                relaunchActivityLocked(r, r.configChangeFlags, false);
                r.configChangeFlags = 0;
            }
            
            // All done...  tell the caller we weren't able to keep this
            // activity around.
            return false;
        }
        
        // Default case: the activity can handle this new configuration, so
        // hand it over.  Note that we don't need to give it the new
        // configuration, since we always send configuration changes to all
        // process when they happen so it can just use whatever configuration
        // it last got.
        if (r.app != null && r.app.thread != null) {
            try {
                if (DEBUG_CONFIGURATION) Slog.v(TAG, "Sending new config to " + r);
                r.app.thread.scheduleActivityConfigurationChanged(r.appToken);
            } catch (RemoteException e) {
                // If process died, whatever.
            }
        }
        r.stopFreezingScreenLocked(false);
        
        return true;
    }

    private final boolean relaunchActivityLocked(ActivityRecord r,
            int changes, boolean andResume) {
        List<ResultInfo> results = null;
        List<Intent> newIntents = null;
        if (andResume) {
            results = r.results;
            newIntents = r.newIntents;
        }
        if (DEBUG_SWITCH) Slog.v(TAG, "Relaunching: " + r
                + " with results=" + results + " newIntents=" + newIntents
                + " andResume=" + andResume);
        EventLog.writeEvent(andResume ? EventLogTags.AM_RELAUNCH_RESUME_ACTIVITY
                : EventLogTags.AM_RELAUNCH_ACTIVITY, r.userId, System.identityHashCode(r),
                r.task.taskId, r.shortComponentName);
        
        r.startFreezingScreenLocked(r.app, 0);
        
        try {
            if (DEBUG_SWITCH || DEBUG_STATES) Slog.i(TAG,
                    (andResume ? "Relaunching to RESUMED " : "Relaunching to PAUSED ")
                    + r);
            r.forceNewConfig = false;
            r.app.thread.scheduleRelaunchActivity(r.appToken, results, newIntents,
                    changes, !andResume, new Configuration(mService.mConfiguration));
            // Note: don't need to call pauseIfSleepingLocked() here, because
            // the caller will only pass in 'andResume' if this activity is
            // currently resumed, which implies we aren't sleeping.
        } catch (RemoteException e) {
            if (DEBUG_SWITCH || DEBUG_STATES) Slog.i(TAG, "Relaunch failed", e);
        }

        if (andResume) {
            r.results = null;
            r.newIntents = null;
            if (mMainStack) {
                mService.reportResumedActivityLocked(r);
            }
            r.state = ActivityState.RESUMED;
        } else {
            mHandler.removeMessages(PAUSE_TIMEOUT_MSG, r);
            r.state = ActivityState.PAUSED;
        }

        return true;
    }
    
    public void dismissKeyguardOnNextActivityLocked() {
        mDismissKeyguardOnNextActivity = true;
    }
}
