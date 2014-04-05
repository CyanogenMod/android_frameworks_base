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

package com.android.server.am;

import android.os.Trace;
import com.android.internal.R.styleable;
import com.android.internal.app.ResolverActivity;
import com.android.server.AttributeCache;
import com.android.server.am.ActivityStack.ActivityState;

import android.app.ActivityOptions;
import android.app.ResultInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.TimeUtils;
import android.view.IApplicationToken;
import android.view.WindowManager;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * An entry in the history stack, representing an activity.
 */
final class ActivityRecord {
    static final String TAG = ActivityManagerService.TAG;
    static final String TAG_TIMELINE = "Timeline";
    static final boolean DEBUG_SAVED_STATE = ActivityStackSupervisor.DEBUG_SAVED_STATE;
    final public static String RECENTS_PACKAGE_NAME = "com.android.systemui.recent";

    final ActivityManagerService service; // owner
    final IApplicationToken.Stub appToken; // window manager token
    final ActivityInfo info; // all about me
    final int launchedFromUid; // always the uid who started the activity.
    final String launchedFromPackage; // always the package who started the activity.
    final int userId;          // Which user is this running for?
    final Intent intent;    // the original intent that generated us
    final ComponentName realActivity;  // the intent component, or target of an alias.
    final String shortComponentName; // the short component name of the intent
    final String resolvedType; // as per original caller;
    final String packageName; // the package implementing intent's component
    final String processName; // process where this component wants to run
    final String taskAffinity; // as per ActivityInfo.taskAffinity
    final boolean stateNotNeeded; // As per ActivityInfo.flags
    boolean fullscreen; // covers the full screen?
    final boolean noDisplay;  // activity is not displayed?
    final boolean componentSpecified;  // did caller specifiy an explicit component?

    static final int APPLICATION_ACTIVITY_TYPE = 0;
    static final int HOME_ACTIVITY_TYPE = 1;
    static final int RECENTS_ACTIVITY_TYPE = 2;
    int mActivityType;

    final String baseDir;   // where activity source (resources etc) located
    final String resDir;   // where public activity source (public resources etc) located
    final String dataDir;   // where activity data should go
    CharSequence nonLocalizedLabel;  // the label information from the package mgr.
    int labelRes;           // the label information from the package mgr.
    int icon;               // resource identifier of activity's icon.
    int logo;               // resource identifier of activity's logo.
    int theme;              // resource identifier of activity's theme.
    int realTheme;          // actual theme resource we will use, never 0.
    int windowFlags;        // custom window flags for preview window.
    TaskRecord task;        // the task this is in.
    ThumbnailHolder thumbHolder; // where our thumbnails should go.
    long displayStartTime;  // when we started launching this activity
    long fullyDrawnStartTime; // when we started launching this activity
    long startTime;         // last time this activity was started
    long lastVisibleTime;   // last time this activity became visible
    long cpuTimeAtResume;   // the cpu time of host process at the time of resuming activity
    long pauseTime;         // last time we started pausing the activity
    long launchTickTime;    // base time for launch tick messages
    Configuration configuration; // configuration activity was last running in
    CompatibilityInfo compat;// last used compatibility mode
    ActivityRecord resultTo; // who started this entry, so will get our reply
    final String resultWho; // additional identifier for use by resultTo.
    final int requestCode;  // code given by requester (resultTo)
    ArrayList<ResultInfo> results; // pending ActivityResult objs we have received
    HashSet<WeakReference<PendingIntentRecord>> pendingResults; // all pending intents for this act
    ArrayList<Intent> newIntents; // any pending new intents for single-top mode
    ActivityOptions pendingOptions; // most recently given options
    HashSet<ConnectionRecord> connections; // All ConnectionRecord we hold
    UriPermissionOwner uriPermissions; // current special URI access perms.
    ProcessRecord app;      // if non-null, hosting application
    ActivityState state;    // current state we are in
    Bundle  icicle;         // last saved activity state
    boolean frontOfTask;    // is this the root activity of its task?
    boolean launchFailed;   // set if a launched failed, to abort on 2nd try
    boolean haveState;      // have we gotten the last activity state?
    boolean stopped;        // is activity pause finished?
    boolean delayedResume;  // not yet resumed because of stopped app switches?
    boolean finishing;      // activity in pending finish list?
    boolean configDestroy;  // need to destroy due to config change?
    int configChangeFlags;  // which config values have changed
    boolean keysPaused;     // has key dispatching been paused for it?
    int launchMode;         // the launch mode activity attribute.
    boolean visible;        // does this activity's window need to be shown?
    boolean sleeping;       // have we told the activity to sleep?
    boolean waitingVisible; // true if waiting for a new act to become vis
    boolean nowVisible;     // is this activity's window visible?
    boolean thumbnailNeeded;// has someone requested a thumbnail?
    boolean idle;           // has the activity gone idle?
    boolean hasBeenLaunched;// has this activity ever been launched?
    boolean frozenBeforeDestroy;// has been frozen but not yet destroyed.
    boolean immersive;      // immersive mode (don't interrupt if possible)
    boolean forceNewConfig; // force re-create with new config next time
    int launchCount;        // count of launches since last state
    long lastLaunchTime;    // time of last lauch of this activity

    String stringName;      // for caching of toString().

    private boolean inHistory;  // are we in the history stack?
    final ActivityStackSupervisor mStackSupervisor;

    void dump(PrintWriter pw, String prefix) {
        final long now = SystemClock.uptimeMillis();
        pw.print(prefix); pw.print("packageName="); pw.print(packageName);
                pw.print(" processName="); pw.println(processName);
        pw.print(prefix); pw.print("launchedFromUid="); pw.print(launchedFromUid);
                pw.print(" launchedFromPackage="); pw.print(launchedFromPackage);
                pw.print(" userId="); pw.println(userId);
        pw.print(prefix); pw.print("app="); pw.println(app);
        pw.print(prefix); pw.println(intent.toInsecureStringWithClip());
        pw.print(prefix); pw.print("frontOfTask="); pw.print(frontOfTask);
                pw.print(" task="); pw.println(task);
        pw.print(prefix); pw.print("taskAffinity="); pw.println(taskAffinity);
        pw.print(prefix); pw.print("realActivity=");
                pw.println(realActivity.flattenToShortString());
        pw.print(prefix); pw.print("baseDir="); pw.println(baseDir);
        if (!resDir.equals(baseDir)) {
            pw.print(prefix); pw.print("resDir="); pw.println(resDir);
        }
        pw.print(prefix); pw.print("dataDir="); pw.println(dataDir);
        pw.print(prefix); pw.print("stateNotNeeded="); pw.print(stateNotNeeded);
                pw.print(" componentSpecified="); pw.print(componentSpecified);
                pw.print(" mActivityType="); pw.println(mActivityType);
        pw.print(prefix); pw.print("compat="); pw.print(compat);
                pw.print(" labelRes=0x"); pw.print(Integer.toHexString(labelRes));
                pw.print(" icon=0x"); pw.print(Integer.toHexString(icon));
                pw.print(" theme=0x"); pw.println(Integer.toHexString(theme));
        pw.print(prefix); pw.print("config="); pw.println(configuration);
        if (resultTo != null || resultWho != null) {
            pw.print(prefix); pw.print("resultTo="); pw.print(resultTo);
                    pw.print(" resultWho="); pw.print(resultWho);
                    pw.print(" resultCode="); pw.println(requestCode);
        }
        if (results != null) {
            pw.print(prefix); pw.print("results="); pw.println(results);
        }
        if (pendingResults != null && pendingResults.size() > 0) {
            pw.print(prefix); pw.println("Pending Results:");
            for (WeakReference<PendingIntentRecord> wpir : pendingResults) {
                PendingIntentRecord pir = wpir != null ? wpir.get() : null;
                pw.print(prefix); pw.print("  - ");
                if (pir == null) {
                    pw.println("null");
                } else {
                    pw.println(pir);
                    pir.dump(pw, prefix + "    ");
                }
            }
        }
        if (newIntents != null && newIntents.size() > 0) {
            pw.print(prefix); pw.println("Pending New Intents:");
            for (int i=0; i<newIntents.size(); i++) {
                Intent intent = newIntents.get(i);
                pw.print(prefix); pw.print("  - ");
                if (intent == null) {
                    pw.println("null");
                } else {
                    pw.println(intent.toShortString(false, true, false, true));
                }
            }
        }
        if (pendingOptions != null) {
            pw.print(prefix); pw.print("pendingOptions="); pw.println(pendingOptions);
        }
        if (uriPermissions != null) {
            if (uriPermissions.readUriPermissions != null) {
                pw.print(prefix); pw.print("readUriPermissions=");
                        pw.println(uriPermissions.readUriPermissions);
            }
            if (uriPermissions.writeUriPermissions != null) {
                pw.print(prefix); pw.print("writeUriPermissions=");
                        pw.println(uriPermissions.writeUriPermissions);
            }
        }
        pw.print(prefix); pw.print("launchFailed="); pw.print(launchFailed);
                pw.print(" launchCount="); pw.print(launchCount);
                pw.print(" lastLaunchTime=");
                if (lastLaunchTime == 0) pw.print("0");
                else TimeUtils.formatDuration(lastLaunchTime, now, pw);
                pw.println();
        pw.print(prefix); pw.print("haveState="); pw.print(haveState);
                pw.print(" icicle="); pw.println(icicle);
        pw.print(prefix); pw.print("state="); pw.print(state);
                pw.print(" stopped="); pw.print(stopped);
                pw.print(" delayedResume="); pw.print(delayedResume);
                pw.print(" finishing="); pw.println(finishing);
        pw.print(prefix); pw.print("keysPaused="); pw.print(keysPaused);
                pw.print(" inHistory="); pw.print(inHistory);
                pw.print(" visible="); pw.print(visible);
                pw.print(" sleeping="); pw.print(sleeping);
                pw.print(" idle="); pw.println(idle);
        pw.print(prefix); pw.print("fullscreen="); pw.print(fullscreen);
                pw.print(" noDisplay="); pw.print(noDisplay);
                pw.print(" immersive="); pw.print(immersive);
                pw.print(" launchMode="); pw.println(launchMode);
        pw.print(prefix); pw.print("frozenBeforeDestroy="); pw.print(frozenBeforeDestroy);
                pw.print(" thumbnailNeeded="); pw.print(thumbnailNeeded);
                pw.print(" forceNewConfig="); pw.println(forceNewConfig);
        pw.print(prefix); pw.print("mActivityType=");
                pw.println(activityTypeToString(mActivityType));
        pw.print(prefix); pw.print("thumbHolder: ");
                pw.print(Integer.toHexString(System.identityHashCode(thumbHolder)));
                if (thumbHolder != null) {
                    pw.print(" bm="); pw.print(thumbHolder.lastThumbnail);
                    pw.print(" desc="); pw.print(thumbHolder.lastDescription);
                }
                pw.println();
        if (displayStartTime != 0 || startTime != 0) {
            pw.print(prefix); pw.print("displayStartTime=");
                    if (displayStartTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(displayStartTime, now, pw);
                    pw.print(" startTime=");
                    if (startTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(startTime, now, pw);
                    pw.println();
        }
        if (lastVisibleTime != 0 || waitingVisible || nowVisible) {
            pw.print(prefix); pw.print("waitingVisible="); pw.print(waitingVisible);
                    pw.print(" nowVisible="); pw.print(nowVisible);
                    pw.print(" lastVisibleTime=");
                    if (lastVisibleTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(lastVisibleTime, now, pw);
                    pw.println();
        }
        if (configDestroy || configChangeFlags != 0) {
            pw.print(prefix); pw.print("configDestroy="); pw.print(configDestroy);
                    pw.print(" configChangeFlags=");
                    pw.println(Integer.toHexString(configChangeFlags));
        }
        if (connections != null) {
            pw.print(prefix); pw.print("connections="); pw.println(connections);
        }
    }

    static class Token extends IApplicationToken.Stub {
        final WeakReference<ActivityRecord> weakActivity;

        Token(ActivityRecord activity) {
            weakActivity = new WeakReference<ActivityRecord>(activity);
        }

        @Override public void windowsDrawn() {
            ActivityRecord activity = weakActivity.get();
            if (activity != null) {
                activity.windowsDrawn();
            }
        }

        @Override public void windowsVisible() {
            ActivityRecord activity = weakActivity.get();
            if (activity != null) {
                activity.windowsVisible();
            }
        }

        @Override public void windowsGone() {
            ActivityRecord activity = weakActivity.get();
            if (activity != null) {
                activity.windowsGone();
            }
        }

        @Override public boolean keyDispatchingTimedOut(String reason) {
            ActivityRecord activity = weakActivity.get();
            return activity != null && activity.keyDispatchingTimedOut(reason);
        }

        @Override public long getKeyDispatchingTimeout() {
            ActivityRecord activity = weakActivity.get();
            if (activity != null) {
                return activity.getKeyDispatchingTimeout();
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Token{");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(' ');
            sb.append(weakActivity.get());
            sb.append('}');
            return sb.toString();
        }
    }

    static ActivityRecord forToken(IBinder token) {
        try {
            return token != null ? ((Token)token).weakActivity.get() : null;
        } catch (ClassCastException e) {
            Slog.w(ActivityManagerService.TAG, "Bad activity token: " + token, e);
            return null;
        }
    }

    boolean isNotResolverActivity() {
        return !ResolverActivity.class.getName().equals(realActivity.getClassName());
    }

    ActivityRecord(ActivityManagerService _service, ProcessRecord _caller,
            int _launchedFromUid, String _launchedFromPackage, Intent _intent, String _resolvedType,
            ActivityInfo aInfo, Configuration _configuration,
            ActivityRecord _resultTo, String _resultWho, int _reqCode,
            boolean _componentSpecified, ActivityStackSupervisor supervisor) {
        service = _service;
        appToken = new Token(this);
        info = aInfo;
        launchedFromUid = _launchedFromUid;
        launchedFromPackage = _launchedFromPackage;
        userId = UserHandle.getUserId(aInfo.applicationInfo.uid);
        intent = _intent;
        shortComponentName = _intent.getComponent().flattenToShortString();
        resolvedType = _resolvedType;
        componentSpecified = _componentSpecified;
        configuration = _configuration;
        resultTo = _resultTo;
        resultWho = _resultWho;
        requestCode = _reqCode;
        state = ActivityState.INITIALIZING;
        frontOfTask = false;
        launchFailed = false;
        stopped = false;
        delayedResume = false;
        finishing = false;
        configDestroy = false;
        keysPaused = false;
        inHistory = false;
        visible = true;
        waitingVisible = false;
        nowVisible = false;
        thumbnailNeeded = false;
        idle = false;
        hasBeenLaunched = false;
        mStackSupervisor = supervisor;

        // This starts out true, since the initial state of an activity
        // is that we have everything, and we shouldn't never consider it
        // lacking in state to be removed if it dies.
        haveState = true;

        if (aInfo != null) {
            if (aInfo.targetActivity == null
                    || aInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE
                    || aInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP) {
                realActivity = _intent.getComponent();
            } else {
                realActivity = new ComponentName(aInfo.packageName,
                        aInfo.targetActivity);
            }
            taskAffinity = aInfo.taskAffinity;
            stateNotNeeded = (aInfo.flags&
                    ActivityInfo.FLAG_STATE_NOT_NEEDED) != 0;
            baseDir = aInfo.applicationInfo.sourceDir;
            resDir = aInfo.applicationInfo.publicSourceDir;
            dataDir = aInfo.applicationInfo.dataDir;
            nonLocalizedLabel = aInfo.nonLocalizedLabel;
            labelRes = aInfo.labelRes;
            if (nonLocalizedLabel == null && labelRes == 0) {
                ApplicationInfo app = aInfo.applicationInfo;
                nonLocalizedLabel = app.nonLocalizedLabel;
                labelRes = app.labelRes;
            }
            icon = aInfo.getIconResource();
            logo = aInfo.getLogoResource();
            theme = aInfo.getThemeResource();
            realTheme = theme;
            if (realTheme == 0) {
                realTheme = aInfo.applicationInfo.targetSdkVersion
                        < Build.VERSION_CODES.HONEYCOMB
                        ? android.R.style.Theme
                        : android.R.style.Theme_Holo;
            }
            if ((aInfo.flags&ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
                windowFlags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            }
            if ((aInfo.flags&ActivityInfo.FLAG_MULTIPROCESS) != 0
                    && _caller != null
                    && (aInfo.applicationInfo.uid == Process.SYSTEM_UID
                            || aInfo.applicationInfo.uid == _caller.info.uid)) {
                processName = _caller.processName;
            } else {
                processName = aInfo.processName;
            }

            if (intent != null && (aInfo.flags & ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0) {
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }

            packageName = aInfo.applicationInfo.packageName;
            launchMode = aInfo.launchMode;

            AttributeCache.Entry ent = AttributeCache.instance().get(packageName,
                    realTheme, com.android.internal.R.styleable.Window, userId);
            fullscreen = ent != null && !ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowIsFloating, false)
                    && !ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowIsTranslucent, false);
            noDisplay = ent != null && ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowNoDisplay, false);

            if ((!_componentSpecified || _launchedFromUid == Process.myUid()
                    || _launchedFromUid == 0) &&
                    Intent.ACTION_MAIN.equals(_intent.getAction()) &&
                    _intent.hasCategory(Intent.CATEGORY_HOME) &&
                    _intent.getCategories().size() == 1 &&
                    _intent.getData() == null &&
                    _intent.getType() == null &&
                    (intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK) != 0 &&
                    isNotResolverActivity()) {
                // This sure looks like a home activity!
                mActivityType = HOME_ACTIVITY_TYPE;
            } else if (realActivity.getClassName().contains(RECENTS_PACKAGE_NAME)) {
                mActivityType = RECENTS_ACTIVITY_TYPE;
            } else {
                mActivityType = APPLICATION_ACTIVITY_TYPE;
            }

            immersive = (aInfo.flags & ActivityInfo.FLAG_IMMERSIVE) != 0;
        } else {
            realActivity = null;
            taskAffinity = null;
            stateNotNeeded = false;
            baseDir = null;
            resDir = null;
            dataDir = null;
            processName = null;
            packageName = null;
            fullscreen = true;
            noDisplay = false;
            mActivityType = APPLICATION_ACTIVITY_TYPE;
            immersive = false;
        }
    }

    void setTask(TaskRecord newTask, ThumbnailHolder newThumbHolder, boolean isRoot) {
        if (task != null && task.removeActivity(this)) {
            if (task != newTask) {
                mStackSupervisor.removeTask(task);
            } else {
                Slog.d(TAG, "!!! REMOVE THIS LOG !!! setTask: nearly removed stack=" +
                        (newTask == null ? null : newTask.stack));
            }
        }
        if (inHistory && !finishing) {
            if (task != null) {
                task.numActivities--;
            }
            if (newTask != null) {
                newTask.numActivities++;
            }
        }
        if (newThumbHolder == null) {
            newThumbHolder = newTask;
        }
        task = newTask;
        if (!isRoot && (intent.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) != 0) {
            // This is the start of a new sub-task.
            if (thumbHolder == null) {
                thumbHolder = new ThumbnailHolder();
            }
        } else {
            thumbHolder = newThumbHolder;
        }
    }

    boolean changeWindowTranslucency(boolean toOpaque) {
        if (fullscreen == toOpaque) {
            return false;
        }
        AttributeCache.Entry ent =
                AttributeCache.instance().get(packageName, realTheme, styleable.Window, userId);
        if (ent == null
                || !ent.array.getBoolean(styleable.Window_windowIsTranslucent, false)
                || ent.array.getBoolean(styleable.Window_windowIsFloating, false)) {
            return false;
        }

        // Keep track of the number of fullscreen activities in this task.
        task.numFullscreen += toOpaque ? +1 : -1;

        fullscreen = toOpaque;
        return true;
    }

    void putInHistory() {
        if (!inHistory) {
            inHistory = true;
            if (task != null && !finishing) {
                task.numActivities++;
            }
        }
    }

    void takeFromHistory() {
        if (inHistory) {
            inHistory = false;
            if (task != null && !finishing) {
                task.numActivities--;
                task = null;
            }
            clearOptionsLocked();
        }
    }

    boolean isInHistory() {
        return inHistory;
    }

    boolean isHomeActivity() {
        return mActivityType == HOME_ACTIVITY_TYPE;
    }

    boolean isRecentsActivity() {
        return mActivityType == RECENTS_ACTIVITY_TYPE;
    }

    boolean isApplicationActivity() {
        return mActivityType == APPLICATION_ACTIVITY_TYPE;
    }

    void makeFinishing() {
        if (!finishing) {
            finishing = true;
            if (task != null && inHistory) {
                task.numActivities--;
            }
            if (stopped) {
                clearOptionsLocked();
            }
        }
    }

    boolean isRootActivity() {
        final ArrayList<ActivityRecord> activities = task.mActivities;
        return activities.size() == 0 || this == activities.get(0);
    }

    UriPermissionOwner getUriPermissionsLocked() {
        if (uriPermissions == null) {
            uriPermissions = new UriPermissionOwner(service, this);
        }
        return uriPermissions;
    }

    void addResultLocked(ActivityRecord from, String resultWho,
            int requestCode, int resultCode,
            Intent resultData) {
        ActivityResult r = new ActivityResult(from, resultWho,
        		requestCode, resultCode, resultData);
        if (results == null) {
            results = new ArrayList<ResultInfo>();
        }
        results.add(r);
    }

    void removeResultsLocked(ActivityRecord from, String resultWho,
            int requestCode) {
        if (results != null) {
            for (int i=results.size()-1; i>=0; i--) {
                ActivityResult r = (ActivityResult)results.get(i);
                if (r.mFrom != from) continue;
                if (r.mResultWho == null) {
                    if (resultWho != null) continue;
                } else {
                    if (!r.mResultWho.equals(resultWho)) continue;
                }
                if (r.mRequestCode != requestCode) continue;

                results.remove(i);
            }
        }
    }

    void addNewIntentLocked(Intent intent) {
        if (newIntents == null) {
            newIntents = new ArrayList<Intent>();
        }
        newIntents.add(intent);
    }

    /**
     * Deliver a new Intent to an existing activity, so that its onNewIntent()
     * method will be called at the proper time.
     */
    final void deliverNewIntentLocked(int callingUid, Intent intent) {
        // The activity now gets access to the data associated with this Intent.
        service.grantUriPermissionFromIntentLocked(callingUid, packageName,
                intent, getUriPermissionsLocked());
        // We want to immediately deliver the intent to the activity if
        // it is currently the top resumed activity...  however, if the
        // device is sleeping, then all activities are stopped, so in that
        // case we will deliver it if this is the current top activity on its
        // stack.
        boolean unsent = true;
        if ((state == ActivityState.RESUMED || (service.mSleeping
                        && task.stack.topRunningActivityLocked(null) == this))
                && app != null && app.thread != null) {
            try {
                ArrayList<Intent> ar = new ArrayList<Intent>();
                intent = new Intent(intent);
                ar.add(intent);
                app.thread.scheduleNewIntent(ar, appToken);
                unsent = false;
            } catch (RemoteException e) {
                Slog.w(ActivityManagerService.TAG,
                        "Exception thrown sending new intent to " + this, e);
            } catch (NullPointerException e) {
                Slog.w(ActivityManagerService.TAG,
                        "Exception thrown sending new intent to " + this, e);
            }
        }
        if (unsent) {
            addNewIntentLocked(new Intent(intent));
        }
    }

    void updateOptionsLocked(Bundle options) {
        if (options != null) {
            if (pendingOptions != null) {
                pendingOptions.abort();
            }
            pendingOptions = new ActivityOptions(options);
        }
    }

    void updateOptionsLocked(ActivityOptions options) {
        if (options != null) {
            if (pendingOptions != null) {
                pendingOptions.abort();
            }
            pendingOptions = options;
        }
    }

    void applyOptionsLocked() {
        if (pendingOptions != null) {
            final int animationType = pendingOptions.getAnimationType();
            switch (animationType) {
                case ActivityOptions.ANIM_CUSTOM:
                    service.mWindowManager.overridePendingAppTransition(
                            pendingOptions.getPackageName(),
                            pendingOptions.getCustomEnterResId(),
                            pendingOptions.getCustomExitResId(),
                            pendingOptions.getOnAnimationStartListener());
                    break;
                case ActivityOptions.ANIM_SCALE_UP:
                    service.mWindowManager.overridePendingAppTransitionScaleUp(
                            pendingOptions.getStartX(), pendingOptions.getStartY(),
                            pendingOptions.getStartWidth(), pendingOptions.getStartHeight());
                    if (intent.getSourceBounds() == null) {
                        intent.setSourceBounds(new Rect(pendingOptions.getStartX(),
                                pendingOptions.getStartY(),
                                pendingOptions.getStartX()+pendingOptions.getStartWidth(),
                                pendingOptions.getStartY()+pendingOptions.getStartHeight()));
                    }
                    break;
                case ActivityOptions.ANIM_THUMBNAIL_SCALE_UP:
                case ActivityOptions.ANIM_THUMBNAIL_SCALE_DOWN:
                    boolean scaleUp = (animationType == ActivityOptions.ANIM_THUMBNAIL_SCALE_UP);
                    service.mWindowManager.overridePendingAppTransitionThumb(
                            pendingOptions.getThumbnail(),
                            pendingOptions.getStartX(), pendingOptions.getStartY(),
                            pendingOptions.getOnAnimationStartListener(),
                            scaleUp);
                    if (intent.getSourceBounds() == null) {
                        intent.setSourceBounds(new Rect(pendingOptions.getStartX(),
                                pendingOptions.getStartY(),
                                pendingOptions.getStartX()
                                        + pendingOptions.getThumbnail().getWidth(),
                                pendingOptions.getStartY()
                                        + pendingOptions.getThumbnail().getHeight()));
                    }
                    break;
            }
            pendingOptions = null;
        }
    }

    void clearOptionsLocked() {
        if (pendingOptions != null) {
            pendingOptions.abort();
            pendingOptions = null;
        }
    }

    ActivityOptions takeOptionsLocked() {
        ActivityOptions opts = pendingOptions;
        pendingOptions = null;
        return opts;
    }

    void removeUriPermissionsLocked() {
        if (uriPermissions != null) {
            uriPermissions.removeUriPermissionsLocked();
            uriPermissions = null;
        }
    }

    void pauseKeyDispatchingLocked() {
        if (!keysPaused) {
            keysPaused = true;
            service.mWindowManager.pauseKeyDispatching(appToken);
        }
    }

    void resumeKeyDispatchingLocked() {
        if (keysPaused) {
            keysPaused = false;
            service.mWindowManager.resumeKeyDispatching(appToken);
        }
    }

    void updateThumbnail(Bitmap newThumbnail, CharSequence description) {
        if (thumbHolder != null) {
            if (newThumbnail != null) {
                if (ActivityManagerService.DEBUG_THUMBNAILS) Slog.i(ActivityManagerService.TAG,
                        "Setting thumbnail of " + this + " holder " + thumbHolder
                        + " to " + newThumbnail);
                thumbHolder.lastThumbnail = newThumbnail;
            }
            thumbHolder.lastDescription = description;
        }
    }

    void startLaunchTickingLocked() {
        if (ActivityManagerService.IS_USER_BUILD) {
            return;
        }
        if (launchTickTime == 0) {
            launchTickTime = SystemClock.uptimeMillis();
            continueLaunchTickingLocked();
        }
    }

    boolean continueLaunchTickingLocked() {
        if (launchTickTime != 0) {
            final ActivityStack stack = task.stack;
            Message msg = stack.mHandler.obtainMessage(ActivityStack.LAUNCH_TICK_MSG, this);
            stack.mHandler.removeMessages(ActivityStack.LAUNCH_TICK_MSG);
            stack.mHandler.sendMessageDelayed(msg, ActivityStack.LAUNCH_TICK);
            return true;
        }
        return false;
    }

    void finishLaunchTickingLocked() {
        launchTickTime = 0;
        task.stack.mHandler.removeMessages(ActivityStack.LAUNCH_TICK_MSG);
    }

    // IApplicationToken

    public boolean mayFreezeScreenLocked(ProcessRecord app) {
        // Only freeze the screen if this activity is currently attached to
        // an application, and that application is not blocked or unresponding.
        // In any other case, we can't count on getting the screen unfrozen,
        // so it is best to leave as-is.
        return app != null && !app.crashing && !app.notResponding;
    }

    public void startFreezingScreenLocked(ProcessRecord app, int configChanges) {
        if (mayFreezeScreenLocked(app)) {
            service.mWindowManager.startAppFreezingScreen(appToken, configChanges);
        }
    }

    public void stopFreezingScreenLocked(boolean force) {
        if (force || frozenBeforeDestroy) {
            frozenBeforeDestroy = false;
            service.mWindowManager.stopAppFreezingScreen(appToken, force);
        }
    }

    public void reportFullyDrawnLocked() {
        final long curTime = SystemClock.uptimeMillis();
        if (displayStartTime != 0) {
            reportLaunchTimeLocked(curTime);
        }
        if (fullyDrawnStartTime != 0) {
            final ActivityStack stack = task.stack;
            final long thisTime = curTime - fullyDrawnStartTime;
            final long totalTime = stack.mFullyDrawnStartTime != 0
                    ? (curTime - stack.mFullyDrawnStartTime) : thisTime;
            if (ActivityManagerService.SHOW_ACTIVITY_START_TIME) {
                Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "drawing", 0);
                EventLog.writeEvent(EventLogTags.AM_ACTIVITY_FULLY_DRAWN_TIME,
                        userId, System.identityHashCode(this), shortComponentName,
                        thisTime, totalTime);
                StringBuilder sb = service.mStringBuilder;
                sb.setLength(0);
                sb.append("Fully drawn ");
                sb.append(shortComponentName);
                sb.append(": ");
                TimeUtils.formatDuration(thisTime, sb);
                if (thisTime != totalTime) {
                    sb.append(" (total ");
                    TimeUtils.formatDuration(totalTime, sb);
                    sb.append(")");
                }
                Log.i(ActivityManagerService.TAG, sb.toString());
            }
            if (totalTime > 0) {
                service.mUsageStatsService.noteFullyDrawnTime(realActivity, (int) totalTime);
            }
            fullyDrawnStartTime = 0;
            stack.mFullyDrawnStartTime = 0;
        }
    }

    private void reportLaunchTimeLocked(final long curTime) {
        final ActivityStack stack = task.stack;
        final long thisTime = curTime - displayStartTime;
        final long totalTime = stack.mLaunchStartTime != 0
                ? (curTime - stack.mLaunchStartTime) : thisTime;
        if (ActivityManagerService.SHOW_ACTIVITY_START_TIME) {
            Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "launching", 0);
            EventLog.writeEvent(EventLogTags.AM_ACTIVITY_LAUNCH_TIME,
                    userId, System.identityHashCode(this), shortComponentName,
                    thisTime, totalTime);
            StringBuilder sb = service.mStringBuilder;
            sb.setLength(0);
            sb.append("Displayed ");
            sb.append(shortComponentName);
            sb.append(": ");
            TimeUtils.formatDuration(thisTime, sb);
            if (thisTime != totalTime) {
                sb.append(" (total ");
                TimeUtils.formatDuration(totalTime, sb);
                sb.append(")");
            }
            Log.i(ActivityManagerService.TAG, sb.toString());
        }
        mStackSupervisor.reportActivityLaunchedLocked(false, this, thisTime, totalTime);
        if (totalTime > 0) {
            service.mUsageStatsService.noteLaunchTime(realActivity, (int)totalTime);
        }
        displayStartTime = 0;
        stack.mLaunchStartTime = 0;
    }

    public void windowsDrawn() {
        synchronized(service) {
            if (displayStartTime != 0) {
                reportLaunchTimeLocked(SystemClock.uptimeMillis());
            }
            startTime = 0;
            finishLaunchTickingLocked();
        }
    }

    public void windowsVisible() {
        synchronized(service) {
            mStackSupervisor.reportActivityVisibleLocked(this);
            if (ActivityManagerService.DEBUG_SWITCH) Log.v(
                    ActivityManagerService.TAG, "windowsVisible(): " + this);
            if (!nowVisible) {
                nowVisible = true;
                lastVisibleTime = SystemClock.uptimeMillis();
                if (!idle) {
                    // Instead of doing the full stop routine here, let's just
                    // hide any activities we now can, and let them stop when
                    // the normal idle happens.
                    mStackSupervisor.processStoppingActivitiesLocked(false);
                } else {
                    // If this activity was already idle, then we now need to
                    // make sure we perform the full stop of any activities
                    // that are waiting to do so.  This is because we won't
                    // do that while they are still waiting for this one to
                    // become visible.
                    final int N = mStackSupervisor.mWaitingVisibleActivities.size();
                    if (N > 0) {
                        for (int i=0; i<N; i++) {
                            ActivityRecord r = mStackSupervisor.mWaitingVisibleActivities.get(i);
                            r.waitingVisible = false;
                            if (ActivityManagerService.DEBUG_SWITCH) Log.v(
                                    ActivityManagerService.TAG,
                                    "Was waiting for visible: " + r);
                        }
                        mStackSupervisor.mWaitingVisibleActivities.clear();
                        mStackSupervisor.scheduleIdleLocked();
                    }
                }
                service.scheduleAppGcsLocked();
            }
        }
        Log.i(TAG_TIMELINE, "Timeline: Activity_windows_visible id: "
                + this + " time:" + SystemClock.uptimeMillis());
    }

    public void windowsGone() {
        if (ActivityManagerService.DEBUG_SWITCH) Log.v(
                ActivityManagerService.TAG, "windowsGone(): " + this);
        nowVisible = false;
    }

    private ActivityRecord getWaitingHistoryRecordLocked() {
        // First find the real culprit...  if we are waiting
        // for another app to start, then we have paused dispatching
        // for this activity.
        ActivityRecord r = this;
        final ActivityStack stack = task.stack;
        if (r.waitingVisible) {
            // Hmmm, who might we be waiting for?
            r = stack.mResumedActivity;
            if (r == null) {
                r = stack.mPausingActivity;
            }
            // Both of those null?  Fall back to 'this' again
            if (r == null) {
                r = this;
            }
        }

        return r;
    }

    public boolean keyDispatchingTimedOut(String reason) {
        ActivityRecord r;
        ProcessRecord anrApp;
        synchronized(service) {
            r = getWaitingHistoryRecordLocked();
            anrApp = r != null ? r.app : null;
        }
        return service.inputDispatchingTimedOut(anrApp, r, this, false, reason);
    }

    /** Returns the key dispatching timeout for this application token. */
    public long getKeyDispatchingTimeout() {
        synchronized(service) {
            ActivityRecord r = getWaitingHistoryRecordLocked();
            return ActivityManagerService.getInputDispatchingTimeoutLocked(r);
        }
    }

    /**
     * This method will return true if the activity is either visible, is becoming visible, is
     * currently pausing, or is resumed.
     */
    public boolean isInterestingToUserLocked() {
        return visible || nowVisible || state == ActivityState.PAUSING ||
                state == ActivityState.RESUMED;
    }

    public void setSleeping(boolean _sleeping) {
        if (sleeping == _sleeping) {
            return;
        }
        if (app != null && app.thread != null) {
            try {
                app.thread.scheduleSleeping(appToken, _sleeping);
                if (_sleeping && !mStackSupervisor.mGoingToSleepActivities.contains(this)) {
                    mStackSupervisor.mGoingToSleepActivities.add(this);
                }
                sleeping = _sleeping;
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception thrown when sleeping: " + intent.getComponent(), e);
            }
        }
    }

    static void activityResumedLocked(IBinder token) {
        final ActivityRecord r = ActivityRecord.forToken(token);
        if (DEBUG_SAVED_STATE) Slog.i(TAG, "Resumed activity; dropping state of: " + r);
        r.icicle = null;
        r.haveState = false;
    }

    static int getTaskForActivityLocked(IBinder token, boolean onlyRoot) {
        final ActivityRecord r = ActivityRecord.forToken(token);
        if (r == null) {
            return -1;
        }
        final TaskRecord task = r.task;
        switch (task.mActivities.indexOf(r)) {
            case -1: return -1;
            case 0: return task.taskId;
            default: return onlyRoot ? -1 : task.taskId;
        }
    }

    static ActivityRecord isInStackLocked(IBinder token) {
        final ActivityRecord r = ActivityRecord.forToken(token);
        if (r != null) {
            return r.task.stack.isInStackLocked(token);
        }
        return null;
    }

    static ActivityStack getStackLocked(IBinder token) {
        final ActivityRecord r = ActivityRecord.isInStackLocked(token);
        if (r != null) {
            return r.task.stack;
        }
        return null;
    }

    private String activityTypeToString(int type) {
        switch (type) {
            case APPLICATION_ACTIVITY_TYPE: return "APPLICATION_ACTIVITY_TYPE";
            case HOME_ACTIVITY_TYPE: return "HOME_ACTIVITY_TYPE";
            case RECENTS_ACTIVITY_TYPE: return "RECENTS_ACTIVITY_TYPE";
            default: return Integer.toString(type);
        }
    }

    @Override
    public String toString() {
        if (stringName != null) {
            return stringName + " t" + (task == null ? -1 : task.taskId) +
                    (finishing ? " f}" : "}");
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("ActivityRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" u");
        sb.append(userId);
        sb.append(' ');
        sb.append(intent.getComponent().flattenToShortString());
        stringName = sb.toString();
        return toString();
    }
}
