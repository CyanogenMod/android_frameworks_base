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

import static android.app.ActivityManager.StackId;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import static android.content.pm.ActivityInfo.RESIZE_MODE_CROP_WINDOWS;
import static android.content.pm.ActivityInfo.FLAG_ALWAYS_FOCUSABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE_AND_PIPABLE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_CONFIGURATION;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_SWITCH;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_THUMBNAILS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_STATES;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_SWITCH;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_THUMBNAILS;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.am.TaskRecord.INVALID_TASK_ID;

import android.app.ActivityManager.TaskDescription;
import android.app.ActivityOptions;
import android.app.PendingIntent;
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
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.service.voice.IVoiceInteractionSession;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.TimeUtils;
import android.view.AppTransitionAnimationSpec;
import android.view.IApplicationToken;
import android.view.WindowManager;

import com.android.internal.app.ResolverActivity;
import com.android.internal.content.ReferrerIntent;
import com.android.internal.util.XmlUtils;
import com.android.server.AttributeCache;
import com.android.server.am.ActivityStack.ActivityState;
import com.android.server.am.ActivityStackSupervisor.ActivityContainer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * An entry in the history stack, representing an activity.
 */
final class ActivityRecord {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "ActivityRecord" : TAG_AM;
    private static final String TAG_STATES = TAG + POSTFIX_STATES;
    private static final String TAG_SWITCH = TAG + POSTFIX_SWITCH;
    private static final String TAG_THUMBNAILS = TAG + POSTFIX_THUMBNAILS;

    private static final boolean SHOW_ACTIVITY_START_TIME = true;
    final public static String RECENTS_PACKAGE_NAME = "com.android.systemui.recents";

    private static final String ATTR_ID = "id";
    private static final String TAG_INTENT = "intent";
    private static final String ATTR_USERID = "user_id";
    private static final String TAG_PERSISTABLEBUNDLE = "persistable_bundle";
    private static final String ATTR_LAUNCHEDFROMUID = "launched_from_uid";
    private static final String ATTR_LAUNCHEDFROMPACKAGE = "launched_from_package";
    private static final String ATTR_RESOLVEDTYPE = "resolved_type";
    private static final String ATTR_COMPONENTSPECIFIED = "component_specified";
    static final String ACTIVITY_ICON_SUFFIX = "_activity_icon_";

    final ActivityManagerService service; // owner
    final IApplicationToken.Stub appToken; // window manager token
    final ActivityInfo info; // all about me
    final ApplicationInfo appInfo; // information about activity's app
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
    final boolean componentSpecified;  // did caller specify an explicit component?
    final boolean rootVoiceInteraction;  // was this the root activity of a voice interaction?

    static final int APPLICATION_ACTIVITY_TYPE = 0;
    static final int HOME_ACTIVITY_TYPE = 1;
    static final int RECENTS_ACTIVITY_TYPE = 2;
    int mActivityType;

    CharSequence nonLocalizedLabel;  // the label information from the package mgr.
    int labelRes;           // the label information from the package mgr.
    int icon;               // resource identifier of activity's icon.
    int logo;               // resource identifier of activity's logo.
    int theme;              // resource identifier of activity's theme.
    int realTheme;          // actual theme resource we will use, never 0.
    int windowFlags;        // custom window flags for preview window.
    TaskRecord task;        // the task this is in.
    long createTime = System.currentTimeMillis();
    long displayStartTime;  // when we started launching this activity
    long fullyDrawnStartTime; // when we started launching this activity
    long startTime;         // last time this activity was started
    long lastVisibleTime;   // last time this activity became visible
    long cpuTimeAtResume;   // the cpu time of host process at the time of resuming activity
    long pauseTime;         // last time we started pausing the activity
    long launchTickTime;    // base time for launch tick messages
    Configuration configuration; // configuration activity was last running in
    // Overridden configuration by the activity task
    // WARNING: Reference points to {@link TaskRecord#mOverrideConfig}, so its internal state
    // should never be altered directly.
    Configuration taskConfigOverride;
    CompatibilityInfo compat;// last used compatibility mode
    ActivityRecord resultTo; // who started this entry, so will get our reply
    final String resultWho; // additional identifier for use by resultTo.
    final int requestCode;  // code given by requester (resultTo)
    ArrayList<ResultInfo> results; // pending ActivityResult objs we have received
    HashSet<WeakReference<PendingIntentRecord>> pendingResults; // all pending intents for this act
    ArrayList<ReferrerIntent> newIntents; // any pending new intents for single-top mode
    ActivityOptions pendingOptions; // most recently given options
    ActivityOptions returningOptions; // options that are coming back via convertToTranslucent
    AppTimeTracker appTimeTracker; // set if we are tracking the time in this app/task/activity
    HashSet<ConnectionRecord> connections; // All ConnectionRecord we hold
    UriPermissionOwner uriPermissions; // current special URI access perms.
    ProcessRecord app;      // if non-null, hosting application
    ActivityState state;    // current state we are in
    Bundle  icicle;         // last saved activity state
    PersistableBundle persistentState; // last persistently saved activity state
    boolean frontOfTask;    // is this the root activity of its task?
    boolean launchFailed;   // set if a launched failed, to abort on 2nd try
    boolean haveState;      // have we gotten the last activity state?
    boolean stopped;        // is activity pause finished?
    boolean delayedResume;  // not yet resumed because of stopped app switches?
    boolean finishing;      // activity in pending finish list?
    boolean deferRelaunchUntilPaused;   // relaunch of activity is being deferred until pause is
                                        // completed
    boolean preserveWindowOnDeferredRelaunch; // activity windows are preserved on deferred relaunch
    int configChangeFlags;  // which config values have changed
    boolean keysPaused;     // has key dispatching been paused for it?
    int launchMode;         // the launch mode activity attribute.
    boolean visible;        // does this activity's window need to be shown?
    boolean sleeping;       // have we told the activity to sleep?
    boolean nowVisible;     // is this activity's window visible?
    boolean idle;           // has the activity gone idle?
    boolean hasBeenLaunched;// has this activity ever been launched?
    boolean frozenBeforeDestroy;// has been frozen but not yet destroyed.
    boolean immersive;      // immersive mode (don't interrupt if possible)
    boolean forceNewConfig; // force re-create with new config next time
    int launchCount;        // count of launches since last state
    long lastLaunchTime;    // time of last launch of this activity
    ComponentName requestedVrComponent; // the requested component for handling VR mode.
    ArrayList<ActivityContainer> mChildContainers = new ArrayList<>();

    String stringName;      // for caching of toString().

    private boolean inHistory;  // are we in the history stack?
    final ActivityStackSupervisor mStackSupervisor;

    static final int STARTING_WINDOW_NOT_SHOWN = 0;
    static final int STARTING_WINDOW_SHOWN = 1;
    static final int STARTING_WINDOW_REMOVED = 2;
    int mStartingWindowState = STARTING_WINDOW_NOT_SHOWN;
    boolean mTaskOverlay = false; // Task is always on-top of other activities in the task.

    boolean mUpdateTaskThumbnailWhenHidden;
    ActivityContainer mInitialActivityContainer;

    TaskDescription taskDescription; // the recents information for this activity
    boolean mLaunchTaskBehind; // this activity is actively being launched with
        // ActivityOptions.setLaunchTaskBehind, will be cleared once launch is completed.

    // These configurations are collected from application's resources based on size-sensitive
    // qualifiers. For example, layout-w800dp will be added to mHorizontalSizeConfigurations as 800
    // and drawable-sw400dp will be added to both as 400.
    private int[] mVerticalSizeConfigurations;
    private int[] mHorizontalSizeConfigurations;
    private int[] mSmallestSizeConfigurations;

    boolean pendingVoiceInteractionStart;   // Waiting for activity-invoked voice session
    IVoiceInteractionSession voiceSession;  // Voice interaction session for this activity

    // A hint to override the window specified rotation animation, or -1
    // to use the window specified value. We use this so that
    // we can select the right animation in the cases of starting
    // windows, where the app hasn't had time to set a value
    // on the window.
    int mRotationAnimationHint = -1;

    private static String startingWindowStateToString(int state) {
        switch (state) {
            case STARTING_WINDOW_NOT_SHOWN:
                return "STARTING_WINDOW_NOT_SHOWN";
            case STARTING_WINDOW_SHOWN:
                return "STARTING_WINDOW_SHOWN";
            case STARTING_WINDOW_REMOVED:
                return "STARTING_WINDOW_REMOVED";
            default:
                return "unknown state=" + state;
        }
    }

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
        if (appInfo != null) {
            pw.print(prefix); pw.print("baseDir="); pw.println(appInfo.sourceDir);
            if (!Objects.equals(appInfo.sourceDir, appInfo.publicSourceDir)) {
                pw.print(prefix); pw.print("resDir="); pw.println(appInfo.publicSourceDir);
            }
            pw.print(prefix); pw.print("dataDir="); pw.println(appInfo.dataDir);
            if (appInfo.splitSourceDirs != null) {
                pw.print(prefix); pw.print("splitDir=");
                        pw.println(Arrays.toString(appInfo.splitSourceDirs));
            }
        }
        pw.print(prefix); pw.print("stateNotNeeded="); pw.print(stateNotNeeded);
                pw.print(" componentSpecified="); pw.print(componentSpecified);
                pw.print(" mActivityType="); pw.println(mActivityType);
        if (rootVoiceInteraction) {
            pw.print(prefix); pw.print("rootVoiceInteraction="); pw.println(rootVoiceInteraction);
        }
        pw.print(prefix); pw.print("compat="); pw.print(compat);
                pw.print(" labelRes=0x"); pw.print(Integer.toHexString(labelRes));
                pw.print(" icon=0x"); pw.print(Integer.toHexString(icon));
                pw.print(" theme=0x"); pw.println(Integer.toHexString(theme));
        pw.print(prefix); pw.print("config="); pw.println(configuration);
        pw.print(prefix); pw.print("taskConfigOverride="); pw.println(taskConfigOverride);
        if (resultTo != null || resultWho != null) {
            pw.print(prefix); pw.print("resultTo="); pw.print(resultTo);
                    pw.print(" resultWho="); pw.print(resultWho);
                    pw.print(" resultCode="); pw.println(requestCode);
        }
        if (taskDescription != null) {
            final String iconFilename = taskDescription.getIconFilename();
            if (iconFilename != null || taskDescription.getLabel() != null ||
                    taskDescription.getPrimaryColor() != 0) {
                pw.print(prefix); pw.print("taskDescription:");
                        pw.print(" iconFilename="); pw.print(taskDescription.getIconFilename());
                        pw.print(" label=\""); pw.print(taskDescription.getLabel());
                                pw.print("\"");
                        pw.print(" color=");
                        pw.println(Integer.toHexString(taskDescription.getPrimaryColor()));
            }
            if (iconFilename == null && taskDescription.getIcon() != null) {
                pw.print(prefix); pw.println("taskDescription contains Bitmap");
            }
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
        if (appTimeTracker != null) {
            appTimeTracker.dumpWithHeader(pw, prefix, false);
        }
        if (uriPermissions != null) {
            uriPermissions.dump(pw, prefix);
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
                pw.print(" idle="); pw.print(idle);
                pw.print(" mStartingWindowState=");
                pw.println(startingWindowStateToString(mStartingWindowState));
        pw.print(prefix); pw.print("fullscreen="); pw.print(fullscreen);
                pw.print(" noDisplay="); pw.print(noDisplay);
                pw.print(" immersive="); pw.print(immersive);
                pw.print(" launchMode="); pw.println(launchMode);
        pw.print(prefix); pw.print("frozenBeforeDestroy="); pw.print(frozenBeforeDestroy);
                pw.print(" forceNewConfig="); pw.println(forceNewConfig);
        pw.print(prefix); pw.print("mActivityType=");
                pw.println(activityTypeToString(mActivityType));
        if (requestedVrComponent != null) {
            pw.print(prefix);
            pw.print("requestedVrComponent=");
            pw.println(requestedVrComponent);
        }
        if (displayStartTime != 0 || startTime != 0) {
            pw.print(prefix); pw.print("displayStartTime=");
                    if (displayStartTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(displayStartTime, now, pw);
                    pw.print(" startTime=");
                    if (startTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(startTime, now, pw);
                    pw.println();
        }
        final boolean waitingVisible = mStackSupervisor.mWaitingVisibleActivities.contains(this);
        if (lastVisibleTime != 0 || waitingVisible || nowVisible) {
            pw.print(prefix); pw.print("waitingVisible="); pw.print(waitingVisible);
                    pw.print(" nowVisible="); pw.print(nowVisible);
                    pw.print(" lastVisibleTime=");
                    if (lastVisibleTime == 0) pw.print("0");
                    else TimeUtils.formatDuration(lastVisibleTime, now, pw);
                    pw.println();
        }
        if (deferRelaunchUntilPaused || configChangeFlags != 0) {
            pw.print(prefix); pw.print("deferRelaunchUntilPaused="); pw.print(deferRelaunchUntilPaused);
                    pw.print(" configChangeFlags=");
                    pw.println(Integer.toHexString(configChangeFlags));
        }
        if (connections != null) {
            pw.print(prefix); pw.print("connections="); pw.println(connections);
        }
        if (info != null) {
            pw.println(prefix + "resizeMode=" + ActivityInfo.resizeModeToString(info.resizeMode));
        }
    }

    public boolean crossesHorizontalSizeThreshold(int firstDp, int secondDp) {
        return crossesSizeThreshold(mHorizontalSizeConfigurations, firstDp, secondDp);
    }

    public boolean crossesVerticalSizeThreshold(int firstDp, int secondDp) {
        return crossesSizeThreshold(mVerticalSizeConfigurations, firstDp, secondDp);
    }

    public boolean crossesSmallestSizeThreshold(int firstDp, int secondDp) {
        return crossesSizeThreshold(mSmallestSizeConfigurations, firstDp, secondDp);
    }

    /**
     * The purpose of this method is to decide whether the activity needs to be relaunched upon
     * changing its size. In most cases the activities don't need to be relaunched, if the resize
     * is small, all the activity content has to do is relayout itself within new bounds. There are
     * cases however, where the activity's content would be completely changed in the new size and
     * the full relaunch is required.
     *
     * The activity will report to us vertical and horizontal thresholds after which a relaunch is
     * required. These thresholds are collected from the application resource qualifiers. For
     * example, if application has layout-w600dp resource directory, then it needs a relaunch when
     * we resize from width of 650dp to 550dp, as it crosses the 600dp threshold. However, if
     * it resizes width from 620dp to 700dp, it won't be relaunched as it stays on the same side
     * of the threshold.
     */
    private static boolean crossesSizeThreshold(int[] thresholds, int firstDp,
            int secondDp) {
        if (thresholds == null) {
            return false;
        }
        for (int i = thresholds.length - 1; i >= 0; i--) {
            final int threshold = thresholds[i];
            if ((firstDp < threshold && secondDp >= threshold)
                    || (firstDp >= threshold && secondDp < threshold)) {
                return true;
            }
        }
        return false;
    }

    public void setSizeConfigurations(int[] horizontalSizeConfiguration,
            int[] verticalSizeConfigurations, int[] smallestSizeConfigurations) {
        mHorizontalSizeConfigurations = horizontalSizeConfiguration;
        mVerticalSizeConfigurations = verticalSizeConfigurations;
        mSmallestSizeConfigurations = smallestSizeConfigurations;
    }

    void scheduleConfigurationChanged(Configuration config, boolean reportToActivity) {
        if (app == null || app.thread == null) {
            return;
        }
        try {
            // Make sure fontScale is always equal to global. For fullscreen apps, config is
            // the shared EMPTY config, which has default fontScale of 1.0. We don't want it
            // to be applied as an override config.
            Configuration overrideConfig = new Configuration(config);
            overrideConfig.fontScale = service.mConfiguration.fontScale;

            if (DEBUG_CONFIGURATION) Slog.v(TAG, "Sending new config to " + this + " " +
                    "reportToActivity=" + reportToActivity + " and config: " + overrideConfig);

            app.thread.scheduleActivityConfigurationChanged(
                    appToken, overrideConfig, reportToActivity);
        } catch (RemoteException e) {
            // If process died, whatever.
        }
    }

    void scheduleMultiWindowModeChanged() {
        if (task == null || task.stack == null || app == null || app.thread == null) {
            return;
        }
        try {
            // An activity is considered to be in multi-window mode if its task isn't fullscreen.
            app.thread.scheduleMultiWindowModeChanged(appToken, !task.mFullscreen);
        } catch (Exception e) {
            // If process died, I don't care.
        }
    }

    void schedulePictureInPictureModeChanged() {
        if (task == null || task.stack == null || app == null || app.thread == null) {
            return;
        }
        try {
            app.thread.schedulePictureInPictureModeChanged(
                    appToken, task.stack.mStackId == PINNED_STACK_ID);
        } catch (Exception e) {
            // If process died, no one cares.
        }
    }

    boolean isFreeform() {
        return task != null && task.stack != null
                && task.stack.mStackId == FREEFORM_WORKSPACE_STACK_ID;
    }

    static class Token extends IApplicationToken.Stub {
        private final WeakReference<ActivityRecord> weakActivity;
        private final ActivityManagerService mService;

        Token(ActivityRecord activity, ActivityManagerService service) {
            weakActivity = new WeakReference<>(activity);
            mService = service;
        }

        @Override
        public void windowsDrawn() {
            synchronized (mService) {
                ActivityRecord r = tokenToActivityRecordLocked(this);
                if (r != null) {
                    r.windowsDrawnLocked();
                }
            }
        }

        @Override
        public void windowsVisible() {
            synchronized (mService) {
                ActivityRecord r = tokenToActivityRecordLocked(this);
                if (r != null) {
                    r.windowsVisibleLocked();
                }
            }
        }

        @Override
        public void windowsGone() {
            synchronized (mService) {
                ActivityRecord r = tokenToActivityRecordLocked(this);
                if (r != null) {
                    if (DEBUG_SWITCH) Log.v(TAG_SWITCH, "windowsGone(): " + r);
                    r.nowVisible = false;
                    return;
                }
            }
        }

        @Override
        public boolean keyDispatchingTimedOut(String reason) {
            ActivityRecord r;
            ActivityRecord anrActivity;
            ProcessRecord anrApp;
            synchronized (mService) {
                r = tokenToActivityRecordLocked(this);
                if (r == null) {
                    return false;
                }
                anrActivity = r.getWaitingHistoryRecordLocked();
                anrApp = r != null ? r.app : null;
            }
            return mService.inputDispatchingTimedOut(anrApp, anrActivity, r, false, reason);
        }

        @Override
        public long getKeyDispatchingTimeout() {
            synchronized (mService) {
                ActivityRecord r = tokenToActivityRecordLocked(this);
                if (r == null) {
                    return 0;
                }
                r = r.getWaitingHistoryRecordLocked();
                return ActivityManagerService.getInputDispatchingTimeoutLocked(r);
            }
        }

        private static final ActivityRecord tokenToActivityRecordLocked(Token token) {
            if (token == null) {
                return null;
            }
            ActivityRecord r = token.weakActivity.get();
            if (r == null || r.task == null || r.task.stack == null) {
                return null;
            }
            return r;
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

    static ActivityRecord forTokenLocked(IBinder token) {
        try {
            return Token.tokenToActivityRecordLocked((Token)token);
        } catch (ClassCastException e) {
            Slog.w(TAG, "Bad activity token: " + token, e);
            return null;
        }
    }

    boolean isResolverActivity() {
        return ResolverActivity.class.getName().equals(realActivity.getClassName());
    }

    ActivityRecord(ActivityManagerService _service, ProcessRecord _caller,
            int _launchedFromUid, String _launchedFromPackage, Intent _intent, String _resolvedType,
            ActivityInfo aInfo, Configuration _configuration,
            ActivityRecord _resultTo, String _resultWho, int _reqCode,
            boolean _componentSpecified, boolean _rootVoiceInteraction,
            ActivityStackSupervisor supervisor,
            ActivityContainer container, ActivityOptions options, ActivityRecord sourceRecord) {
        service = _service;
        appToken = new Token(this, service);
        info = aInfo;
        launchedFromUid = _launchedFromUid;
        launchedFromPackage = _launchedFromPackage;
        userId = UserHandle.getUserId(aInfo.applicationInfo.uid);
        intent = _intent;
        shortComponentName = _intent.getComponent().flattenToShortString();
        resolvedType = _resolvedType;
        componentSpecified = _componentSpecified;
        rootVoiceInteraction = _rootVoiceInteraction;
        configuration = _configuration;
        taskConfigOverride = Configuration.EMPTY;
        resultTo = _resultTo;
        resultWho = _resultWho;
        requestCode = _reqCode;
        state = ActivityState.INITIALIZING;
        frontOfTask = false;
        launchFailed = false;
        stopped = false;
        delayedResume = false;
        finishing = false;
        deferRelaunchUntilPaused = false;
        keysPaused = false;
        inHistory = false;
        visible = false;
        nowVisible = false;
        idle = false;
        hasBeenLaunched = false;
        mStackSupervisor = supervisor;
        mInitialActivityContainer = container;
        if (options != null) {
            pendingOptions = options;
            mLaunchTaskBehind = pendingOptions.getLaunchTaskBehind();
            mRotationAnimationHint = pendingOptions.getRotationAnimationHint();
            PendingIntent usageReport = pendingOptions.getUsageTimeReport();
            if (usageReport != null) {
                appTimeTracker = new AppTimeTracker(usageReport);
            }
        }

        // This starts out true, since the initial state of an activity
        // is that we have everything, and we shouldn't never consider it
        // lacking in state to be removed if it dies.
        haveState = true;

        if (aInfo != null) {
            // If the class name in the intent doesn't match that of the target, this is
            // probably an alias. We have to create a new ComponentName object to keep track
            // of the real activity name, so that FLAG_ACTIVITY_CLEAR_TOP is handled properly.
            if (aInfo.targetActivity == null
                    || (aInfo.targetActivity.equals(_intent.getComponent().getClassName())
                    && (aInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE
                    || aInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP))) {
                realActivity = _intent.getComponent();
            } else {
                realActivity = new ComponentName(aInfo.packageName, aInfo.targetActivity);
            }
            taskAffinity = aInfo.taskAffinity;
            stateNotNeeded = (aInfo.flags&
                    ActivityInfo.FLAG_STATE_NOT_NEEDED) != 0;
            appInfo = aInfo.applicationInfo;
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
            final boolean translucent = ent != null && (ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowIsTranslucent, false)
                    || (!ent.array.hasValue(
                            com.android.internal.R.styleable.Window_windowIsTranslucent)
                            && ent.array.getBoolean(
                                    com.android.internal.R.styleable.Window_windowSwipeToDismiss,
                                            false)));
            fullscreen = ent != null && !ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowIsFloating, false)
                    && !translucent;
            noDisplay = ent != null && ent.array.getBoolean(
                    com.android.internal.R.styleable.Window_windowNoDisplay, false);

            setActivityType(_componentSpecified, _launchedFromUid, _intent, sourceRecord);

            immersive = (aInfo.flags & ActivityInfo.FLAG_IMMERSIVE) != 0;

            requestedVrComponent = (aInfo.requestedVrComponent == null) ?
                    null : ComponentName.unflattenFromString(aInfo.requestedVrComponent);
        } else {
            realActivity = null;
            taskAffinity = null;
            stateNotNeeded = false;
            appInfo = null;
            processName = null;
            packageName = null;
            fullscreen = true;
            noDisplay = false;
            mActivityType = APPLICATION_ACTIVITY_TYPE;
            immersive = false;
            requestedVrComponent  = null;
        }
    }

    private boolean isHomeIntent(Intent intent) {
        return Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)
                && intent.getCategories().size() == 1
                && intent.getData() == null
                && intent.getType() == null;
    }

    static boolean isMainIntent(Intent intent) {
        return Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && intent.getCategories().size() == 1
                && intent.getData() == null
                && intent.getType() == null;
    }

    private boolean canLaunchHomeActivity(int uid, ActivityRecord sourceRecord) {
        if (uid == Process.myUid() || uid == 0) {
            // System process can launch home activity.
            return true;
        }
        // Resolver activity can launch home activity.
        return sourceRecord != null && sourceRecord.isResolverActivity();
    }

    private void setActivityType(boolean componentSpecified,
            int launchedFromUid, Intent intent, ActivityRecord sourceRecord) {
        if ((!componentSpecified || canLaunchHomeActivity(launchedFromUid, sourceRecord))
                && isHomeIntent(intent) && !isResolverActivity()) {
            // This sure looks like a home activity!
            mActivityType = HOME_ACTIVITY_TYPE;
        } else if (realActivity.getClassName().contains(RECENTS_PACKAGE_NAME)) {
            mActivityType = RECENTS_ACTIVITY_TYPE;
        } else {
            mActivityType = APPLICATION_ACTIVITY_TYPE;
        }
    }

    void setTask(TaskRecord newTask, TaskRecord taskToAffiliateWith) {
        if (task != null && task.removeActivity(this) && task != newTask && task.stack != null) {
            task.stack.removeTask(task, "setTask");
        }
        task = newTask;
        setTaskToAffiliateWith(taskToAffiliateWith);
    }

    void setTaskToAffiliateWith(TaskRecord taskToAffiliateWith) {
        if (taskToAffiliateWith != null &&
                launchMode != ActivityInfo.LAUNCH_SINGLE_INSTANCE &&
                launchMode != ActivityInfo.LAUNCH_SINGLE_TASK) {
            task.setTaskToAffiliateWith(taskToAffiliateWith);
        }
    }

    boolean changeWindowTranslucency(boolean toOpaque) {
        if (fullscreen == toOpaque) {
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
        }
    }

    void takeFromHistory() {
        if (inHistory) {
            inHistory = false;
            if (task != null && !finishing) {
                task = null;
            }
            clearOptionsLocked();
        }
    }

    boolean isInHistory() {
        return inHistory;
    }

    boolean isInStackLocked() {
        return task != null && task.stack != null && task.stack.isInStackLocked(this) != null;
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

    boolean isPersistable() {
        return (info.persistableMode == ActivityInfo.PERSIST_ROOT_ONLY ||
                info.persistableMode == ActivityInfo.PERSIST_ACROSS_REBOOTS) &&
                (intent == null ||
                        (intent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0);
    }

    boolean isFocusable() {
        return StackId.canReceiveKeys(task.stack.mStackId) || isAlwaysFocusable();
    }

    boolean isResizeable() {
        return !isHomeActivity() && ActivityInfo.isResizeableMode(info.resizeMode);
    }

    boolean isResizeableOrForced() {
        return !isHomeActivity() && (isResizeable() || service.mForceResizableActivities);
    }

    boolean isNonResizableOrForced() {
        return !isHomeActivity() && info.resizeMode != RESIZE_MODE_RESIZEABLE
                && info.resizeMode != RESIZE_MODE_RESIZEABLE_AND_PIPABLE;
    }

    boolean supportsPictureInPicture() {
        return !isHomeActivity() && info.resizeMode == RESIZE_MODE_RESIZEABLE_AND_PIPABLE;
    }

    boolean canGoInDockedStack() {
        return !isHomeActivity()
                && (isResizeableOrForced() || info.resizeMode == RESIZE_MODE_CROP_WINDOWS);
    }

    boolean isAlwaysFocusable() {
        return (info.flags & FLAG_ALWAYS_FOCUSABLE) != 0;
    }

    void makeFinishingLocked() {
        if (!finishing) {
            if (task != null && task.stack != null
                    && this == task.stack.getVisibleBehindActivity()) {
                // A finishing activity should not remain as visible in the background
                mStackSupervisor.requestVisibleBehindLocked(this, false);
            }
            finishing = true;
            if (stopped) {
                clearOptionsLocked();
            }
        }
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

    void addNewIntentLocked(ReferrerIntent intent) {
        if (newIntents == null) {
            newIntents = new ArrayList<>();
        }
        newIntents.add(intent);
    }

    /**
     * Deliver a new Intent to an existing activity, so that its onNewIntent()
     * method will be called at the proper time.
     */
    final void deliverNewIntentLocked(int callingUid, Intent intent, String referrer) {
        // The activity now gets access to the data associated with this Intent.
        service.grantUriPermissionFromIntentLocked(callingUid, packageName,
                intent, getUriPermissionsLocked(), userId);
        final ReferrerIntent rintent = new ReferrerIntent(intent, referrer);
        boolean unsent = true;
        final ActivityStack stack = task.stack;
        final boolean isTopActivityInStack =
                stack != null && stack.topRunningActivityLocked() == this;
        final boolean isTopActivityWhileSleeping =
                service.isSleepingLocked() && isTopActivityInStack;

        // We want to immediately deliver the intent to the activity if:
        // - It is currently resumed or paused. i.e. it is currently visible to the user and we want
        //   the user to see the visual effects caused by the intent delivery now.
        // - The device is sleeping and it is the top activity behind the lock screen (b/6700897).
        if ((state == ActivityState.RESUMED || state == ActivityState.PAUSED
                || isTopActivityWhileSleeping) && app != null && app.thread != null) {
            try {
                ArrayList<ReferrerIntent> ar = new ArrayList<>(1);
                ar.add(rintent);
                app.thread.scheduleNewIntent(
                        ar, appToken, state == ActivityState.PAUSED /* andPause */);
                unsent = false;
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception thrown sending new intent to " + this, e);
            } catch (NullPointerException e) {
                Slog.w(TAG, "Exception thrown sending new intent to " + this, e);
            }
        }
        if (unsent) {
            addNewIntentLocked(rintent);
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
        if (pendingOptions != null
                && pendingOptions.getAnimationType() != ActivityOptions.ANIM_SCENE_TRANSITION) {
            final int animationType = pendingOptions.getAnimationType();
            switch (animationType) {
                case ActivityOptions.ANIM_CUSTOM:
                    service.mWindowManager.overridePendingAppTransition(
                            pendingOptions.getPackageName(),
                            pendingOptions.getCustomEnterResId(),
                            pendingOptions.getCustomExitResId(),
                            pendingOptions.getOnAnimationStartListener());
                    break;
                case ActivityOptions.ANIM_CLIP_REVEAL:
                    service.mWindowManager.overridePendingAppTransitionClipReveal(
                            pendingOptions.getStartX(), pendingOptions.getStartY(),
                            pendingOptions.getWidth(), pendingOptions.getHeight());
                    if (intent.getSourceBounds() == null) {
                        intent.setSourceBounds(new Rect(pendingOptions.getStartX(),
                                pendingOptions.getStartY(),
                                pendingOptions.getStartX()+pendingOptions.getWidth(),
                                pendingOptions.getStartY()+pendingOptions.getHeight()));
                    }
                    break;
                case ActivityOptions.ANIM_SCALE_UP:
                    service.mWindowManager.overridePendingAppTransitionScaleUp(
                            pendingOptions.getStartX(), pendingOptions.getStartY(),
                            pendingOptions.getWidth(), pendingOptions.getHeight());
                    if (intent.getSourceBounds() == null) {
                        intent.setSourceBounds(new Rect(pendingOptions.getStartX(),
                                pendingOptions.getStartY(),
                                pendingOptions.getStartX()+pendingOptions.getWidth(),
                                pendingOptions.getStartY()+pendingOptions.getHeight()));
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
                case ActivityOptions.ANIM_THUMBNAIL_ASPECT_SCALE_UP:
                case ActivityOptions.ANIM_THUMBNAIL_ASPECT_SCALE_DOWN:
                    final AppTransitionAnimationSpec[] specs = pendingOptions.getAnimSpecs();
                    if (animationType == ActivityOptions.ANIM_THUMBNAIL_ASPECT_SCALE_DOWN
                            && specs != null) {
                        service.mWindowManager.overridePendingAppTransitionMultiThumb(
                                specs, pendingOptions.getOnAnimationStartListener(),
                                pendingOptions.getAnimationFinishedListener(), false);
                    } else {
                        service.mWindowManager.overridePendingAppTransitionAspectScaledThumb(
                                pendingOptions.getThumbnail(),
                                pendingOptions.getStartX(), pendingOptions.getStartY(),
                                pendingOptions.getWidth(), pendingOptions.getHeight(),
                                pendingOptions.getOnAnimationStartListener(),
                                (animationType == ActivityOptions.ANIM_THUMBNAIL_ASPECT_SCALE_UP));
                        if (intent.getSourceBounds() == null) {
                            intent.setSourceBounds(new Rect(pendingOptions.getStartX(),
                                    pendingOptions.getStartY(),
                                    pendingOptions.getStartX() + pendingOptions.getWidth(),
                                    pendingOptions.getStartY() + pendingOptions.getHeight()));
                        }
                    }
                    break;
                default:
                    Slog.e(TAG, "applyOptionsLocked: Unknown animationType=" + animationType);
                    break;
            }
            pendingOptions = null;
        }
    }

    ActivityOptions getOptionsForTargetActivityLocked() {
        return pendingOptions != null ? pendingOptions.forTargetActivity() : null;
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

    void updateThumbnailLocked(Bitmap newThumbnail, CharSequence description) {
        if (newThumbnail != null) {
            if (DEBUG_THUMBNAILS) Slog.i(TAG_THUMBNAILS,
                    "Setting thumbnail of " + this + " to " + newThumbnail);
            boolean thumbnailUpdated = task.setLastThumbnailLocked(newThumbnail);
            if (thumbnailUpdated && isPersistable()) {
                mStackSupervisor.mService.notifyTaskPersisterLocked(task, false);
            }
        }
        task.lastDescription = description;
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
        if (launchTickTime == 0) {
            return false;
        }

        final ActivityStack stack = task.stack;
        if (stack == null) {
            return false;
        }

        Message msg = stack.mHandler.obtainMessage(ActivityStack.LAUNCH_TICK_MSG, this);
        stack.mHandler.removeMessages(ActivityStack.LAUNCH_TICK_MSG);
        stack.mHandler.sendMessageDelayed(msg, ActivityStack.LAUNCH_TICK);
        return true;
    }

    void finishLaunchTickingLocked() {
        launchTickTime = 0;
        final ActivityStack stack = task.stack;
        if (stack != null) {
            stack.mHandler.removeMessages(ActivityStack.LAUNCH_TICK_MSG);
        }
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
        final ActivityStack stack = task.stack;
        if (fullyDrawnStartTime != 0 && stack != null) {
            final long thisTime = curTime - fullyDrawnStartTime;
            final long totalTime = stack.mFullyDrawnStartTime != 0
                    ? (curTime - stack.mFullyDrawnStartTime) : thisTime;
            if (SHOW_ACTIVITY_START_TIME) {
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
                Log.i(TAG, sb.toString());
            }
            if (totalTime > 0) {
                //service.mUsageStatsService.noteFullyDrawnTime(realActivity, (int) totalTime);
            }
            stack.mFullyDrawnStartTime = 0;
        }
        fullyDrawnStartTime = 0;
    }

    private void reportLaunchTimeLocked(final long curTime) {
        final ActivityStack stack = task.stack;
        if (stack == null) {
            return;
        }
        final long thisTime = curTime - displayStartTime;
        final long totalTime = stack.mLaunchStartTime != 0
                ? (curTime - stack.mLaunchStartTime) : thisTime;
        if (SHOW_ACTIVITY_START_TIME) {
            Trace.asyncTraceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, "launching: " + packageName, 0);
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
            Log.i(TAG, sb.toString());
        }
        mStackSupervisor.reportActivityLaunchedLocked(false, this, thisTime, totalTime);
        if (totalTime > 0) {
            //service.mUsageStatsService.noteLaunchTime(realActivity, (int)totalTime);
        }
        displayStartTime = 0;
        stack.mLaunchStartTime = 0;
    }

    void windowsDrawnLocked() {
        mStackSupervisor.mActivityMetricsLogger.notifyWindowsDrawn();
        if (displayStartTime != 0) {
            reportLaunchTimeLocked(SystemClock.uptimeMillis());
        }
        mStackSupervisor.sendWaitingVisibleReportLocked(this);
        startTime = 0;
        finishLaunchTickingLocked();
        if (task != null) {
            task.hasBeenVisible = true;
        }
    }

    void windowsVisibleLocked() {
        mStackSupervisor.reportActivityVisibleLocked(this);
        if (DEBUG_SWITCH) Log.v(TAG_SWITCH, "windowsVisibleLocked(): " + this);
        if (!nowVisible) {
            nowVisible = true;
            lastVisibleTime = SystemClock.uptimeMillis();
            if (!idle) {
                // Instead of doing the full stop routine here, let's just hide any activities
                // we now can, and let them stop when the normal idle happens.
                mStackSupervisor.processStoppingActivitiesLocked(false);
            } else {
                // If this activity was already idle, then we now need to make sure we perform
                // the full stop of any activities that are waiting to do so. This is because
                // we won't do that while they are still waiting for this one to become visible.
                final int size = mStackSupervisor.mWaitingVisibleActivities.size();
                if (size > 0) {
                    for (int i = 0; i < size; i++) {
                        ActivityRecord r = mStackSupervisor.mWaitingVisibleActivities.get(i);
                        if (DEBUG_SWITCH) Log.v(TAG_SWITCH, "Was waiting for visible: " + r);
                    }
                    mStackSupervisor.mWaitingVisibleActivities.clear();
                    mStackSupervisor.scheduleIdleLocked();
                }
            }
            service.scheduleAppGcsLocked();
        }
    }

    ActivityRecord getWaitingHistoryRecordLocked() {
        // First find the real culprit...  if this activity is waiting for
        // another activity to start or has stopped, then the key dispatching
        // timeout should not be caused by this.
        if (mStackSupervisor.mWaitingVisibleActivities.contains(this) || stopped) {
            final ActivityStack stack = mStackSupervisor.getFocusedStack();
            // Try to use the one which is closest to top.
            ActivityRecord r = stack.mResumedActivity;
            if (r == null) {
                r = stack.mPausingActivity;
            }
            if (r != null) {
                return r;
            }
        }
        return this;
    }

    /**
     * This method will return true if the activity is either visible, is becoming visible, is
     * currently pausing, or is resumed.
     */
    public boolean isInterestingToUserLocked() {
        return visible || nowVisible || state == ActivityState.PAUSING ||
                state == ActivityState.RESUMED;
    }

    void setSleeping(boolean _sleeping) {
        setSleeping(_sleeping, false);
    }

    void setSleeping(boolean _sleeping, boolean force) {
        if (!force && sleeping == _sleeping) {
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

    static int getTaskForActivityLocked(IBinder token, boolean onlyRoot) {
        final ActivityRecord r = ActivityRecord.forTokenLocked(token);
        if (r == null) {
            return INVALID_TASK_ID;
        }
        final TaskRecord task = r.task;
        final int activityNdx = task.mActivities.indexOf(r);
        if (activityNdx < 0 || (onlyRoot && activityNdx > task.findEffectiveRootIndex())) {
            return INVALID_TASK_ID;
        }
        return task.taskId;
    }

    static ActivityRecord isInStackLocked(IBinder token) {
        final ActivityRecord r = ActivityRecord.forTokenLocked(token);
        return (r != null) ? r.task.stack.isInStackLocked(r) : null;
    }

    static ActivityStack getStackLocked(IBinder token) {
        final ActivityRecord r = ActivityRecord.isInStackLocked(token);
        if (r != null) {
            return r.task.stack;
        }
        return null;
    }

    final boolean isDestroyable() {
        if (finishing || app == null || state == ActivityState.DESTROYING
                || state == ActivityState.DESTROYED) {
            // This would be redundant.
            return false;
        }
        if (task == null || task.stack == null || this == task.stack.mResumedActivity
                || this == task.stack.mPausingActivity || !haveState || !stopped) {
            // We're not ready for this kind of thing.
            return false;
        }
        if (visible) {
            // The user would notice this!
            return false;
        }
        return true;
    }

    private static String createImageFilename(long createTime, int taskId) {
        return String.valueOf(taskId) + ACTIVITY_ICON_SUFFIX + createTime +
                TaskPersister.IMAGE_EXTENSION;
    }

    void setTaskDescription(TaskDescription _taskDescription) {
        Bitmap icon;
        if (_taskDescription.getIconFilename() == null &&
                (icon = _taskDescription.getIcon()) != null) {
            final String iconFilename = createImageFilename(createTime, task.taskId);
            final File iconFile = new File(TaskPersister.getUserImagesDir(userId), iconFilename);
            final String iconFilePath = iconFile.getAbsolutePath();
            service.mRecentTasks.saveImage(icon, iconFilePath);
            _taskDescription.setIconFilename(iconFilePath);
        }
        taskDescription = _taskDescription;
    }

    void setVoiceSessionLocked(IVoiceInteractionSession session) {
        voiceSession = session;
        pendingVoiceInteractionStart = false;
    }

    void clearVoiceSessionLocked() {
        voiceSession = null;
        pendingVoiceInteractionStart = false;
    }

    void showStartingWindow(ActivityRecord prev, boolean createIfNeeded) {
        final CompatibilityInfo compatInfo =
                service.compatibilityInfoForPackageLocked(info.applicationInfo);
        final boolean shown = service.mWindowManager.setAppStartingWindow(
                appToken, packageName, theme, compatInfo, nonLocalizedLabel, labelRes, icon,
                logo, windowFlags, prev != null ? prev.appToken : null, createIfNeeded);
        if (shown) {
            mStartingWindowState = STARTING_WINDOW_SHOWN;
        }
    }

    void saveToXml(XmlSerializer out) throws IOException, XmlPullParserException {
        out.attribute(null, ATTR_ID, String.valueOf(createTime));
        out.attribute(null, ATTR_LAUNCHEDFROMUID, String.valueOf(launchedFromUid));
        if (launchedFromPackage != null) {
            out.attribute(null, ATTR_LAUNCHEDFROMPACKAGE, launchedFromPackage);
        }
        if (resolvedType != null) {
            out.attribute(null, ATTR_RESOLVEDTYPE, resolvedType);
        }
        out.attribute(null, ATTR_COMPONENTSPECIFIED, String.valueOf(componentSpecified));
        out.attribute(null, ATTR_USERID, String.valueOf(userId));

        if (taskDescription != null) {
            taskDescription.saveToXml(out);
        }

        out.startTag(null, TAG_INTENT);
        intent.saveToXml(out);
        out.endTag(null, TAG_INTENT);

        if (isPersistable() && persistentState != null) {
            out.startTag(null, TAG_PERSISTABLEBUNDLE);
            persistentState.saveToXml(out);
            out.endTag(null, TAG_PERSISTABLEBUNDLE);
        }
    }

    static ActivityRecord restoreFromXml(XmlPullParser in,
            ActivityStackSupervisor stackSupervisor) throws IOException, XmlPullParserException {
        Intent intent = null;
        PersistableBundle persistentState = null;
        int launchedFromUid = 0;
        String launchedFromPackage = null;
        String resolvedType = null;
        boolean componentSpecified = false;
        int userId = 0;
        long createTime = -1;
        final int outerDepth = in.getDepth();
        TaskDescription taskDescription = new TaskDescription();

        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (TaskPersister.DEBUG) Slog.d(TaskPersister.TAG,
                        "ActivityRecord: attribute name=" + attrName + " value=" + attrValue);
            if (ATTR_ID.equals(attrName)) {
                createTime = Long.valueOf(attrValue);
            } else if (ATTR_LAUNCHEDFROMUID.equals(attrName)) {
                launchedFromUid = Integer.parseInt(attrValue);
            } else if (ATTR_LAUNCHEDFROMPACKAGE.equals(attrName)) {
                launchedFromPackage = attrValue;
            } else if (ATTR_RESOLVEDTYPE.equals(attrName)) {
                resolvedType = attrValue;
            } else if (ATTR_COMPONENTSPECIFIED.equals(attrName)) {
                componentSpecified = Boolean.valueOf(attrValue);
            } else if (ATTR_USERID.equals(attrName)) {
                userId = Integer.parseInt(attrValue);
            } else if (attrName.startsWith(TaskDescription.ATTR_TASKDESCRIPTION_PREFIX)) {
                taskDescription.restoreFromXml(attrName, attrValue);
            } else {
                Log.d(TAG, "Unknown ActivityRecord attribute=" + attrName);
            }
        }

        int event;
        while (((event = in.next()) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || in.getDepth() >= outerDepth)) {
            if (event == XmlPullParser.START_TAG) {
                final String name = in.getName();
                if (TaskPersister.DEBUG)
                        Slog.d(TaskPersister.TAG, "ActivityRecord: START_TAG name=" + name);
                if (TAG_INTENT.equals(name)) {
                    intent = Intent.restoreFromXml(in);
                    if (TaskPersister.DEBUG)
                            Slog.d(TaskPersister.TAG, "ActivityRecord: intent=" + intent);
                } else if (TAG_PERSISTABLEBUNDLE.equals(name)) {
                    persistentState = PersistableBundle.restoreFromXml(in);
                    if (TaskPersister.DEBUG) Slog.d(TaskPersister.TAG,
                            "ActivityRecord: persistentState=" + persistentState);
                } else {
                    Slog.w(TAG, "restoreActivity: unexpected name=" + name);
                    XmlUtils.skipCurrentTag(in);
                }
            }
        }

        if (intent == null) {
            throw new XmlPullParserException("restoreActivity error intent=" + intent);
        }

        final ActivityManagerService service = stackSupervisor.mService;
        final ActivityInfo aInfo = stackSupervisor.resolveActivity(intent, resolvedType, 0, null,
                userId);
        if (aInfo == null) {
            throw new XmlPullParserException("restoreActivity resolver error. Intent=" + intent +
                    " resolvedType=" + resolvedType);
        }
        final ActivityRecord r = new ActivityRecord(service, /*caller*/null, launchedFromUid,
                launchedFromPackage, intent, resolvedType, aInfo, service.getConfiguration(),
                null, null, 0, componentSpecified, false, stackSupervisor, null, null, null);

        r.persistentState = persistentState;
        r.taskDescription = taskDescription;
        r.createTime = createTime;

        return r;
    }

    private static String activityTypeToString(int type) {
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
            return stringName + " t" + (task == null ? INVALID_TASK_ID : task.taskId) +
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
