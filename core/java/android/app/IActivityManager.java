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

package android.app;

import android.content.ComponentName;
import android.content.ContentProviderNative;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ProviderInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.StrictMode;

import java.util.List;

/**
 * System private API for talking with the activity manager service.  This
 * provides calls from the application back to the activity manager.
 *
 * {@hide}
 */
public interface IActivityManager extends IInterface {
    public int startActivity(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int flags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options) throws RemoteException;
    public int startActivityAsUser(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int flags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options, int userId) throws RemoteException;
    public WaitResult startActivityAndWait(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int flags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options, int userId) throws RemoteException;
    public int startActivityWithConfig(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int startFlags, Configuration newConfig,
            Bundle options, int userId) throws RemoteException;
    public int startActivityIntentSender(IApplicationThread caller,
            IntentSender intent, Intent fillInIntent, String resolvedType,
            IBinder resultTo, String resultWho, int requestCode,
            int flagsMask, int flagsValues, Bundle options) throws RemoteException;
    public boolean startNextMatchingActivity(IBinder callingActivity,
            Intent intent, Bundle options) throws RemoteException;
    public boolean finishActivity(IBinder token, int code, Intent data)
            throws RemoteException;
    public void finishSubActivity(IBinder token, String resultWho, int requestCode) throws RemoteException;
    public boolean finishActivityAffinity(IBinder token) throws RemoteException;
    public boolean willActivityBeVisible(IBinder token) throws RemoteException;
    public Intent registerReceiver(IApplicationThread caller, String callerPackage,
            IIntentReceiver receiver, IntentFilter filter,
            String requiredPermission, int userId) throws RemoteException;
    public void unregisterReceiver(IIntentReceiver receiver) throws RemoteException;
    public int broadcastIntent(IApplicationThread caller, Intent intent,
            String resolvedType, IIntentReceiver resultTo, int resultCode,
            String resultData, Bundle map, String requiredPermission,
            int appOp, boolean serialized, boolean sticky, int userId) throws RemoteException;
    public void unbroadcastIntent(IApplicationThread caller, Intent intent, int userId) throws RemoteException;
    public void finishReceiver(IBinder who, int resultCode, String resultData, Bundle map, boolean abortBroadcast) throws RemoteException;
    public void attachApplication(IApplicationThread app) throws RemoteException;
    public void activityResumed(IBinder token) throws RemoteException;
    public void activityIdle(IBinder token, Configuration config,
            boolean stopProfiling) throws RemoteException;
    public void activityPaused(IBinder token) throws RemoteException;
    public void activityStopped(IBinder token, Bundle state,
            Bitmap thumbnail, CharSequence description) throws RemoteException;
    public void activitySlept(IBinder token) throws RemoteException;
    public void activityDestroyed(IBinder token) throws RemoteException;
    public String getCallingPackage(IBinder token) throws RemoteException;
    public String getCallingPackageForBroadcast(boolean foreground) throws RemoteException;
    public ComponentName getCallingActivity(IBinder token) throws RemoteException;
    public List getTasks(int maxNum, int flags,
                         IThumbnailReceiver receiver) throws RemoteException;
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum,
            int flags, int userId) throws RemoteException;
    public ActivityManager.TaskThumbnails getTaskThumbnails(int taskId) throws RemoteException;
    public Bitmap getTaskTopThumbnail(int taskId) throws RemoteException;
    public List getServices(int maxNum, int flags) throws RemoteException;
    public List<ActivityManager.ProcessErrorStateInfo> getProcessesInErrorState()
            throws RemoteException;
    public void moveTaskToFront(int task, int flags, Bundle options) throws RemoteException;
    public void moveTaskToBack(int task) throws RemoteException;
    public boolean moveActivityTaskToBack(IBinder token, boolean nonRoot) throws RemoteException;
    public void moveTaskBackwards(int task) throws RemoteException;
    public int getTaskForActivity(IBinder token, boolean onlyRoot) throws RemoteException;
    /* oneway */
    public void reportThumbnail(IBinder token,
            Bitmap thumbnail, CharSequence description) throws RemoteException;
    public ContentProviderHolder getContentProvider(IApplicationThread caller,
            String name, int userId, boolean stable) throws RemoteException;
    public ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token)
            throws RemoteException;
    public void removeContentProvider(IBinder connection, boolean stable) throws RemoteException;
    public void removeContentProviderExternal(String name, IBinder token) throws RemoteException;
    public void publishContentProviders(IApplicationThread caller,
            List<ContentProviderHolder> providers) throws RemoteException;
    public boolean refContentProvider(IBinder connection, int stableDelta, int unstableDelta)
            throws RemoteException;
    public void unstableProviderDied(IBinder connection) throws RemoteException;
    public PendingIntent getRunningServiceControlPanel(ComponentName service)
            throws RemoteException;
    public ComponentName startService(IApplicationThread caller, Intent service,
            String resolvedType, int userId) throws RemoteException;
    public int stopService(IApplicationThread caller, Intent service,
            String resolvedType, int userId) throws RemoteException;
    public boolean stopServiceToken(ComponentName className, IBinder token,
            int startId) throws RemoteException;
    public void setServiceForeground(ComponentName className, IBinder token,
            int id, Notification notification, boolean keepNotification) throws RemoteException;
    public int bindService(IApplicationThread caller, IBinder token,
            Intent service, String resolvedType,
            IServiceConnection connection, int flags, int userId) throws RemoteException;
    public boolean unbindService(IServiceConnection connection) throws RemoteException;
    public void publishService(IBinder token,
            Intent intent, IBinder service) throws RemoteException;
    public void unbindFinished(IBinder token, Intent service,
            boolean doRebind) throws RemoteException;
    /* oneway */
    public void serviceDoneExecuting(IBinder token, int type, int startId,
            int res) throws RemoteException;
    public IBinder peekService(Intent service, String resolvedType) throws RemoteException;
    
    public boolean bindBackupAgent(ApplicationInfo appInfo, int backupRestoreMode)
            throws RemoteException;
    public void clearPendingBackup() throws RemoteException;
    public void backupAgentCreated(String packageName, IBinder agent) throws RemoteException;
    public void unbindBackupAgent(ApplicationInfo appInfo) throws RemoteException;
    public void killApplicationProcess(String processName, int uid) throws RemoteException;
    
    public boolean startInstrumentation(ComponentName className, String profileFile,
            int flags, Bundle arguments, IInstrumentationWatcher watcher,
            IUiAutomationConnection connection, int userId) throws RemoteException;
    public void finishInstrumentation(IApplicationThread target,
            int resultCode, Bundle results) throws RemoteException;

    public Configuration getConfiguration() throws RemoteException;
    public void updateConfiguration(Configuration values) throws RemoteException;
    public void setRequestedOrientation(IBinder token,
            int requestedOrientation) throws RemoteException;
    public int getRequestedOrientation(IBinder token) throws RemoteException;
    
    public ComponentName getActivityClassForToken(IBinder token) throws RemoteException;
    public String getPackageForToken(IBinder token) throws RemoteException;

    public IIntentSender getIntentSender(int type,
            String packageName, IBinder token, String resultWho,
            int requestCode, Intent[] intents, String[] resolvedTypes,
            int flags, Bundle options, int userId) throws RemoteException;
    public void cancelIntentSender(IIntentSender sender) throws RemoteException;
    public boolean clearApplicationUserData(final String packageName,
            final IPackageDataObserver observer, int userId) throws RemoteException;
    public String getPackageForIntentSender(IIntentSender sender) throws RemoteException;
    public int getUidForIntentSender(IIntentSender sender) throws RemoteException;
    
    public int handleIncomingUser(int callingPid, int callingUid, int userId, boolean allowAll,
            boolean requireFull, String name, String callerPackage) throws RemoteException;

    public void setProcessLimit(int max) throws RemoteException;
    public int getProcessLimit() throws RemoteException;
    
    public void setProcessForeground(IBinder token, int pid,
            boolean isForeground) throws RemoteException;
    
    public int checkPermission(String permission, int pid, int uid)
            throws RemoteException;

    public int checkUriPermission(Uri uri, int pid, int uid, int mode)
            throws RemoteException;
    public void grantUriPermission(IApplicationThread caller, String targetPkg,
            Uri uri, int mode) throws RemoteException;
    public void revokeUriPermission(IApplicationThread caller, Uri uri,
            int mode) throws RemoteException;
    
    public void showWaitingForDebugger(IApplicationThread who, boolean waiting)
            throws RemoteException;
    
    public void getMemoryInfo(ActivityManager.MemoryInfo outInfo) throws RemoteException;
    
    public void killBackgroundProcesses(final String packageName, int userId)
            throws RemoteException;
    public void killAllBackgroundProcesses() throws RemoteException;
    public void forceStopPackage(final String packageName, int userId) throws RemoteException;
    
    // Note: probably don't want to allow applications access to these.
    public void goingToSleep() throws RemoteException;
    public void wakingUp() throws RemoteException;
    public void setLockScreenShown(boolean shown) throws RemoteException;

    public void unhandledBack() throws RemoteException;
    public ParcelFileDescriptor openContentUri(Uri uri) throws RemoteException;
    public void setDebugApp(
        String packageName, boolean waitForDebugger, boolean persistent)
        throws RemoteException;
    public void setAlwaysFinish(boolean enabled) throws RemoteException;
    public void setActivityController(IActivityController watcher)
        throws RemoteException;

    public void enterSafeMode() throws RemoteException;
    
    public void noteWakeupAlarm(IIntentSender sender) throws RemoteException;

    public boolean killPids(int[] pids, String reason, boolean secure) throws RemoteException;
    public boolean killProcessesBelowForeground(String reason) throws RemoteException;

    // Special low-level communication with activity manager.
    public void startRunning(String pkg, String cls, String action,
            String data) throws RemoteException;
    public void handleApplicationCrash(IBinder app,
            ApplicationErrorReport.CrashInfo crashInfo) throws RemoteException;
    public boolean handleApplicationWtf(IBinder app, String tag,
            ApplicationErrorReport.CrashInfo crashInfo) throws RemoteException;

    // A StrictMode violation to be handled.  The violationMask is a
    // subset of the original StrictMode policy bitmask, with only the
    // bit violated and penalty bits to be executed by the
    // ActivityManagerService remaining set.
    public void handleApplicationStrictModeViolation(IBinder app, int violationMask,
            StrictMode.ViolationInfo crashInfo) throws RemoteException;

    /*
     * This will deliver the specified signal to all the persistent processes. Currently only 
     * SIGUSR1 is delivered. All others are ignored.
     */
    public void signalPersistentProcesses(int signal) throws RemoteException;
    // Retrieve running application processes in the system
    public List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses()
            throws RemoteException;
    // Retrieve info of applications installed on external media that are currently
    // running.
    public List<ApplicationInfo> getRunningExternalApplications()
            throws RemoteException;
    // Get memory information about the calling process.
    public void getMyMemoryState(ActivityManager.RunningAppProcessInfo outInfo)
            throws RemoteException;
    // Get device configuration
    public ConfigurationInfo getDeviceConfigurationInfo() throws RemoteException;
    
    // Turn on/off profiling in a particular process.
    public boolean profileControl(String process, int userId, boolean start,
            String path, ParcelFileDescriptor fd, int profileType) throws RemoteException;
    
    public boolean shutdown(int timeout) throws RemoteException;
    
    public void stopAppSwitches() throws RemoteException;
    public void resumeAppSwitches() throws RemoteException;
    
    public void killApplicationWithAppId(String pkg, int appid) throws RemoteException;
    
    public void closeSystemDialogs(String reason) throws RemoteException;
    
    public Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids)
            throws RemoteException;
    
    public void overridePendingTransition(IBinder token, String packageName,
            int enterAnim, int exitAnim) throws RemoteException;
    
    public boolean isUserAMonkey() throws RemoteException;

    public void setUserIsMonkey(boolean monkey) throws RemoteException;

    public void finishHeavyWeightApp() throws RemoteException;

    public void setImmersive(IBinder token, boolean immersive) throws RemoteException;
    public boolean isImmersive(IBinder token) throws RemoteException;
    public boolean isTopActivityImmersive() throws RemoteException;
    
    public void crashApplication(int uid, int initialPid, String packageName,
            String message) throws RemoteException;

    public String getProviderMimeType(Uri uri, int userId) throws RemoteException;
    
    public IBinder newUriPermissionOwner(String name) throws RemoteException;
    public void grantUriPermissionFromOwner(IBinder owner, int fromUid, String targetPkg,
            Uri uri, int mode) throws RemoteException;
    public void revokeUriPermissionFromOwner(IBinder owner, Uri uri,
            int mode) throws RemoteException;

    public int checkGrantUriPermission(int callingUid, String targetPkg,
            Uri uri, int modeFlags) throws RemoteException;

    // Cause the specified process to dump the specified heap.
    public boolean dumpHeap(String process, int userId, boolean managed, String path,
        ParcelFileDescriptor fd) throws RemoteException;

    public int startActivities(IApplicationThread caller, String callingPackage,
            Intent[] intents, String[] resolvedTypes, IBinder resultTo,
            Bundle options, int userId) throws RemoteException;

    public int getFrontActivityScreenCompatMode() throws RemoteException;
    public void setFrontActivityScreenCompatMode(int mode) throws RemoteException;
    public int getPackageScreenCompatMode(String packageName) throws RemoteException;
    public void setPackageScreenCompatMode(String packageName, int mode)
            throws RemoteException;
    public boolean getPackageAskScreenCompat(String packageName) throws RemoteException;
    public void setPackageAskScreenCompat(String packageName, boolean ask)
            throws RemoteException;

    // Multi-user APIs
    public boolean switchUser(int userid) throws RemoteException;
    public int stopUser(int userid, IStopUserCallback callback) throws RemoteException;
    public UserInfo getCurrentUser() throws RemoteException;
    public boolean isUserRunning(int userid, boolean orStopping) throws RemoteException;
    public int[] getRunningUserIds() throws RemoteException;

    public boolean removeSubTask(int taskId, int subTaskIndex) throws RemoteException;

    public boolean removeTask(int taskId, int flags) throws RemoteException;

    public void registerProcessObserver(IProcessObserver observer) throws RemoteException;
    public void unregisterProcessObserver(IProcessObserver observer) throws RemoteException;

    public boolean isIntentSenderTargetedToPackage(IIntentSender sender) throws RemoteException;

    public boolean isIntentSenderAnActivity(IIntentSender sender) throws RemoteException;

    public Intent getIntentForIntentSender(IIntentSender sender) throws RemoteException;

    public void updatePersistentConfiguration(Configuration values) throws RemoteException;

    public long[] getProcessPss(int[] pids) throws RemoteException;

    public void showBootMessage(CharSequence msg, boolean always) throws RemoteException;

    public void dismissKeyguardOnNextActivity() throws RemoteException;

    public boolean targetTaskAffinityMatchesActivity(IBinder token, String destAffinity)
            throws RemoteException;

    public boolean navigateUpTo(IBinder token, Intent target, int resultCode, Intent resultData)
            throws RemoteException;

    // These are not public because you need to be very careful in how you
    // manage your activity to make sure it is always the uid you expect.
    public int getLaunchedFromUid(IBinder activityToken) throws RemoteException;
    public String getLaunchedFromPackage(IBinder activityToken) throws RemoteException;

    public void registerUserSwitchObserver(IUserSwitchObserver observer) throws RemoteException;
    public void unregisterUserSwitchObserver(IUserSwitchObserver observer) throws RemoteException;

    public void requestBugReport() throws RemoteException;

    public long inputDispatchingTimedOut(int pid, boolean aboveSystem) throws RemoteException;

    public Bundle getTopActivityExtras(int requestType) throws RemoteException;

    public void reportTopActivityExtras(IBinder token, Bundle extras) throws RemoteException;

    public void killUid(int uid, String reason) throws RemoteException;

    public void hang(IBinder who, boolean allowRestart) throws RemoteException;

    /*
     * Private non-Binder interfaces
     */
    /* package */ boolean testIsSystemReady();
    
    /** Information you can retrieve about a particular application. */
    public static class ContentProviderHolder implements Parcelable {
        public final ProviderInfo info;
        public IContentProvider provider;
        public IBinder connection;
        public boolean noReleaseNeeded;

        public ContentProviderHolder(ProviderInfo _info) {
            info = _info;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            info.writeToParcel(dest, 0);
            if (provider != null) {
                dest.writeStrongBinder(provider.asBinder());
            } else {
                dest.writeStrongBinder(null);
            }
            dest.writeStrongBinder(connection);
            dest.writeInt(noReleaseNeeded ? 1:0);
        }

        public static final Parcelable.Creator<ContentProviderHolder> CREATOR
                = new Parcelable.Creator<ContentProviderHolder>() {
            public ContentProviderHolder createFromParcel(Parcel source) {
                return new ContentProviderHolder(source);
            }

            public ContentProviderHolder[] newArray(int size) {
                return new ContentProviderHolder[size];
            }
        };

        private ContentProviderHolder(Parcel source) {
            info = ProviderInfo.CREATOR.createFromParcel(source);
            provider = ContentProviderNative.asInterface(
                source.readStrongBinder());
            connection = source.readStrongBinder();
            noReleaseNeeded = source.readInt() != 0;
        }
    }

    /** Information returned after waiting for an activity start. */
    public static class WaitResult implements Parcelable {
        public int result;
        public boolean timeout;
        public ComponentName who;
        public long thisTime;
        public long totalTime;

        public WaitResult() {
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(result);
            dest.writeInt(timeout ? 1 : 0);
            ComponentName.writeToParcel(who, dest);
            dest.writeLong(thisTime);
            dest.writeLong(totalTime);
        }

        public static final Parcelable.Creator<WaitResult> CREATOR
                = new Parcelable.Creator<WaitResult>() {
            public WaitResult createFromParcel(Parcel source) {
                return new WaitResult(source);
            }

            public WaitResult[] newArray(int size) {
                return new WaitResult[size];
            }
        };

        private WaitResult(Parcel source) {
            result = source.readInt();
            timeout = source.readInt() != 0;
            who = ComponentName.readFromParcel(source);
            thisTime = source.readLong();
            totalTime = source.readLong();
        }
    };

    String descriptor = "android.app.IActivityManager";

    // Please keep these transaction codes the same -- they are also
    // sent by C++ code.
    int START_RUNNING_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
    int HANDLE_APPLICATION_CRASH_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+1;
    int START_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+2;
    int UNHANDLED_BACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+3;
    int OPEN_CONTENT_URI_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+4;

    // Remaining non-native transaction codes.
    int FINISH_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+10;
    int REGISTER_RECEIVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+11;
    int UNREGISTER_RECEIVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+12;
    int BROADCAST_INTENT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+13;
    int UNBROADCAST_INTENT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+14;
    int FINISH_RECEIVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+15;
    int ATTACH_APPLICATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+16;
    int ACTIVITY_IDLE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+17;
    int ACTIVITY_PAUSED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+18;
    int ACTIVITY_STOPPED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+19;
    int GET_CALLING_PACKAGE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+20;
    int GET_CALLING_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+21;
    int GET_TASKS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+22;
    int MOVE_TASK_TO_FRONT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+23;
    int MOVE_TASK_TO_BACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+24;
    int MOVE_TASK_BACKWARDS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+25;
    int GET_TASK_FOR_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+26;
    int REPORT_THUMBNAIL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+27;
    int GET_CONTENT_PROVIDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+28;
    int PUBLISH_CONTENT_PROVIDERS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+29;
    int REF_CONTENT_PROVIDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+30;
    int FINISH_SUB_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+31;
    int GET_RUNNING_SERVICE_CONTROL_PANEL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+32;
    int START_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+33;
    int STOP_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+34;
    int BIND_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+35;
    int UNBIND_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+36;
    int PUBLISH_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+37;
    int ACTIVITY_RESUMED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+38;
    int GOING_TO_SLEEP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+39;
    int WAKING_UP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+40;
    int SET_DEBUG_APP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+41;
    int SET_ALWAYS_FINISH_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+42;
    int START_INSTRUMENTATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+43;
    int FINISH_INSTRUMENTATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+44;
    int GET_CONFIGURATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+45;
    int UPDATE_CONFIGURATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+46;
    int STOP_SERVICE_TOKEN_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+47;
    int GET_ACTIVITY_CLASS_FOR_TOKEN_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+48;
    int GET_PACKAGE_FOR_TOKEN_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+49;
    int SET_PROCESS_LIMIT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+50;
    int GET_PROCESS_LIMIT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+51;
    int CHECK_PERMISSION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+52;
    int CHECK_URI_PERMISSION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+53;
    int GRANT_URI_PERMISSION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+54;
    int REVOKE_URI_PERMISSION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+55;
    int SET_ACTIVITY_CONTROLLER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+56;
    int SHOW_WAITING_FOR_DEBUGGER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+57;
    int SIGNAL_PERSISTENT_PROCESSES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+58;
    int GET_RECENT_TASKS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+59;
    int SERVICE_DONE_EXECUTING_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+60;
    int ACTIVITY_DESTROYED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+61;
    int GET_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+62;
    int CANCEL_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+63;
    int GET_PACKAGE_FOR_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+64;
    int ENTER_SAFE_MODE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+65;
    int START_NEXT_MATCHING_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+66;
    int NOTE_WAKEUP_ALARM_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+67;
    int REMOVE_CONTENT_PROVIDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+68;
    int SET_REQUESTED_ORIENTATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+69;
    int GET_REQUESTED_ORIENTATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+70;
    int UNBIND_FINISHED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+71;
    int SET_PROCESS_FOREGROUND_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+72;
    int SET_SERVICE_FOREGROUND_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+73;
    int MOVE_ACTIVITY_TASK_TO_BACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+74;
    int GET_MEMORY_INFO_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+75;
    int GET_PROCESSES_IN_ERROR_STATE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+76;
    int CLEAR_APP_DATA_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+77;
    int FORCE_STOP_PACKAGE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+78;
    int KILL_PIDS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+79;
    int GET_SERVICES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+80;
    int GET_TASK_THUMBNAILS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+81;
    int GET_RUNNING_APP_PROCESSES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+82;
    int GET_DEVICE_CONFIGURATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+83;
    int PEEK_SERVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+84;
    int PROFILE_CONTROL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+85;
    int SHUTDOWN_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+86;
    int STOP_APP_SWITCHES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+87;
    int RESUME_APP_SWITCHES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+88;
    int START_BACKUP_AGENT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+89;
    int BACKUP_AGENT_CREATED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+90;
    int UNBIND_BACKUP_AGENT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+91;
    int GET_UID_FOR_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+92;
    int HANDLE_INCOMING_USER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+93;
    int GET_TASK_TOP_THUMBNAIL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+94;
    int KILL_APPLICATION_WITH_APPID_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+95;
    int CLOSE_SYSTEM_DIALOGS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+96;
    int GET_PROCESS_MEMORY_INFO_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+97;
    int KILL_APPLICATION_PROCESS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+98;
    int START_ACTIVITY_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+99;
    int OVERRIDE_PENDING_TRANSITION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+100;
    int HANDLE_APPLICATION_WTF_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+101;
    int KILL_BACKGROUND_PROCESSES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+102;
    int IS_USER_A_MONKEY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+103;
    int START_ACTIVITY_AND_WAIT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+104;
    int WILL_ACTIVITY_BE_VISIBLE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+105;
    int START_ACTIVITY_WITH_CONFIG_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+106;
    int GET_RUNNING_EXTERNAL_APPLICATIONS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+107;
    int FINISH_HEAVY_WEIGHT_APP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+108;
    int HANDLE_APPLICATION_STRICT_MODE_VIOLATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+109;
    int IS_IMMERSIVE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+110;
    int SET_IMMERSIVE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+111;
    int IS_TOP_ACTIVITY_IMMERSIVE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+112;
    int CRASH_APPLICATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+113;
    int GET_PROVIDER_MIME_TYPE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+114;
    int NEW_URI_PERMISSION_OWNER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+115;
    int GRANT_URI_PERMISSION_FROM_OWNER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+116;
    int REVOKE_URI_PERMISSION_FROM_OWNER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+117;
    int CHECK_GRANT_URI_PERMISSION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+118;
    int DUMP_HEAP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+119;
    int START_ACTIVITIES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+120;
    int IS_USER_RUNNING_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+121;
    int ACTIVITY_SLEPT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+122;
    int GET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+123;
    int SET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+124;
    int GET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+125;
    int SET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+126;
    int GET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+127;
    int SET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+128;
    int SWITCH_USER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+129;
    int REMOVE_SUB_TASK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+130;
    int REMOVE_TASK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+131;
    int REGISTER_PROCESS_OBSERVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+132;
    int UNREGISTER_PROCESS_OBSERVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+133;
    int IS_INTENT_SENDER_TARGETED_TO_PACKAGE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+134;
    int UPDATE_PERSISTENT_CONFIGURATION_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+135;
    int GET_PROCESS_PSS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+136;
    int SHOW_BOOT_MESSAGE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+137;
    int DISMISS_KEYGUARD_ON_NEXT_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+138;
    int KILL_ALL_BACKGROUND_PROCESSES_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+139;
    int GET_CONTENT_PROVIDER_EXTERNAL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+140;
    int REMOVE_CONTENT_PROVIDER_EXTERNAL_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+141;
    int GET_MY_MEMORY_STATE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+142;
    int KILL_PROCESSES_BELOW_FOREGROUND_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+143;
    int GET_CURRENT_USER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+144;
    int TARGET_TASK_AFFINITY_MATCHES_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+145;
    int NAVIGATE_UP_TO_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+146;
    int SET_LOCK_SCREEN_SHOWN_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+147;
    int FINISH_ACTIVITY_AFFINITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+148;
    int GET_LAUNCHED_FROM_UID_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+149;
    int UNSTABLE_PROVIDER_DIED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+150;
    int IS_INTENT_SENDER_AN_ACTIVITY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+151;
    int START_ACTIVITY_AS_USER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+152;
    int STOP_USER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+153;
    int REGISTER_USER_SWITCH_OBSERVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+154;
    int UNREGISTER_USER_SWITCH_OBSERVER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+155;
    int GET_RUNNING_USER_IDS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+156;
    int REQUEST_BUG_REPORT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+157;
    int INPUT_DISPATCHING_TIMED_OUT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+158;
    int CLEAR_PENDING_BACKUP_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+159;
    int GET_INTENT_FOR_INTENT_SENDER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+160;
    int GET_TOP_ACTIVITY_EXTRAS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+161;
    int REPORT_TOP_ACTIVITY_EXTRAS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+162;
    int GET_LAUNCHED_FROM_PACKAGE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+163;
    int KILL_UID_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+164;
    int SET_USER_IS_MONKEY_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+165;
    int HANG_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+166;
    int GET_CALLING_PACKAGE_FOR_BROADCAST_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION+168;
}
