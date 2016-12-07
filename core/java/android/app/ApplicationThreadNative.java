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
import android.content.Intent;
import android.content.IIntentReceiver;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.TransactionTooLargeException;
import android.util.Log;

import com.android.internal.app.IVoiceInteractor;
import com.android.internal.content.ReferrerIntent;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@hide} */
public abstract class ApplicationThreadNative extends Binder
        implements IApplicationThread {
    /**
     * Cast a Binder object into an application thread interface, generating
     * a proxy if needed.
     */
    static public IApplicationThread asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        IApplicationThread in =
            (IApplicationThread)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }

        return new ApplicationThreadProxy(obj);
    }

    public ApplicationThreadNative() {
        attachInterface(this, descriptor);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
        case SCHEDULE_PAUSE_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean finished = data.readInt() != 0;
            boolean userLeaving = data.readInt() != 0;
            int configChanges = data.readInt();
            boolean dontReport = data.readInt() != 0;
            schedulePauseActivity(b, finished, userLeaving, configChanges, dontReport);
            return true;
        }

        case SCHEDULE_STOP_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean show = data.readInt() != 0;
            int configChanges = data.readInt();
            scheduleStopActivity(b, show, configChanges);
            return true;
        }

        case SCHEDULE_WINDOW_VISIBILITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean show = data.readInt() != 0;
            scheduleWindowVisibility(b, show);
            return true;
        }

        case SCHEDULE_SLEEPING_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean sleeping = data.readInt() != 0;
            scheduleSleeping(b, sleeping);
            return true;
        }

        case SCHEDULE_RESUME_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            int procState = data.readInt();
            boolean isForward = data.readInt() != 0;
            Bundle resumeArgs = data.readBundle();
            scheduleResumeActivity(b, procState, isForward, resumeArgs);
            return true;
        }

        case SCHEDULE_SEND_RESULT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            scheduleSendResult(b, ri);
            return true;
        }

        case SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Intent intent = Intent.CREATOR.createFromParcel(data);
            IBinder b = data.readStrongBinder();
            int ident = data.readInt();
            ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(data);
            Configuration curConfig = Configuration.CREATOR.createFromParcel(data);
            Configuration overrideConfig = null;
            if (data.readInt() != 0) {
                overrideConfig = Configuration.CREATOR.createFromParcel(data);
            }
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            String referrer = data.readString();
            IVoiceInteractor voiceInteractor = IVoiceInteractor.Stub.asInterface(
                    data.readStrongBinder());
            int procState = data.readInt();
            Bundle state = data.readBundle();
            PersistableBundle persistentState = data.readPersistableBundle();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            List<ReferrerIntent> pi = data.createTypedArrayList(ReferrerIntent.CREATOR);
            boolean notResumed = data.readInt() != 0;
            boolean isForward = data.readInt() != 0;
            ProfilerInfo profilerInfo = data.readInt() != 0
                    ? ProfilerInfo.CREATOR.createFromParcel(data) : null;
            scheduleLaunchActivity(intent, b, ident, info, curConfig, overrideConfig, compatInfo,
                    referrer, voiceInteractor, procState, state, persistentState, ri, pi,
                    notResumed, isForward, profilerInfo);
            return true;
        }

        case SCHEDULE_RELAUNCH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            List<ReferrerIntent> pi = data.createTypedArrayList(ReferrerIntent.CREATOR);
            int configChanges = data.readInt();
            boolean notResumed = data.readInt() != 0;
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            Configuration overrideConfig = null;
            if (data.readInt() != 0) {
                overrideConfig = Configuration.CREATOR.createFromParcel(data);
            }
            boolean preserveWindows = data.readInt() == 1;
            scheduleRelaunchActivity(b, ri, pi, configChanges, notResumed, config, overrideConfig,
                    preserveWindows);
            return true;
        }

        case SCHEDULE_NEW_INTENT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            List<ReferrerIntent> pi = data.createTypedArrayList(ReferrerIntent.CREATOR);
            IBinder b = data.readStrongBinder();
            final boolean andPause = data.readInt() == 1;
            scheduleNewIntent(pi, b, andPause);
            return true;
        }

        case SCHEDULE_FINISH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean finishing = data.readInt() != 0;
            int configChanges = data.readInt();
            scheduleDestroyActivity(b, finishing, configChanges);
            return true;
        }

        case SCHEDULE_RECEIVER_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Intent intent = Intent.CREATOR.createFromParcel(data);
            ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(data);
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            int resultCode = data.readInt();
            String resultData = data.readString();
            Bundle resultExtras = data.readBundle();
            boolean sync = data.readInt() != 0;
            int sendingUser = data.readInt();
            int processState = data.readInt();
            scheduleReceiver(intent, info, compatInfo, resultCode, resultData,
                    resultExtras, sync, sendingUser, processState);
            return true;
        }

        case SCHEDULE_CREATE_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            ServiceInfo info = ServiceInfo.CREATOR.createFromParcel(data);
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            int processState = data.readInt();
            scheduleCreateService(token, info, compatInfo, processState);
            return true;
        }

        case SCHEDULE_BIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            boolean rebind = data.readInt() != 0;
            int processState = data.readInt();
            scheduleBindService(token, intent, rebind, processState);
            return true;
        }

        case SCHEDULE_UNBIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            scheduleUnbindService(token, intent);
            return true;
        }

        case SCHEDULE_SERVICE_ARGS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            boolean taskRemoved = data.readInt() != 0;
            int startId = data.readInt();
            int fl = data.readInt();
            Intent args;
            if (data.readInt() != 0) {
                args = Intent.CREATOR.createFromParcel(data);
            } else {
                args = null;
            }
            scheduleServiceArgs(token, taskRemoved, startId, fl, args);
            return true;
        }

        case SCHEDULE_STOP_SERVICE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            scheduleStopService(token);
            return true;
        }

        case BIND_APPLICATION_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            String packageName = data.readString();
            ApplicationInfo info =
                ApplicationInfo.CREATOR.createFromParcel(data);
            List<ProviderInfo> providers =
                data.createTypedArrayList(ProviderInfo.CREATOR);
            ComponentName testName = (data.readInt() != 0)
                ? new ComponentName(data) : null;
            ProfilerInfo profilerInfo = data.readInt() != 0
                    ? ProfilerInfo.CREATOR.createFromParcel(data) : null;
            Bundle testArgs = data.readBundle();
            IBinder binder = data.readStrongBinder();
            IInstrumentationWatcher testWatcher = IInstrumentationWatcher.Stub.asInterface(binder);
            binder = data.readStrongBinder();
            IUiAutomationConnection uiAutomationConnection =
                    IUiAutomationConnection.Stub.asInterface(binder);
            int testMode = data.readInt();
            boolean enableBinderTracking = data.readInt() != 0;
            boolean trackAllocation = data.readInt() != 0;
            boolean restrictedBackupMode = (data.readInt() != 0);
            boolean persistent = (data.readInt() != 0);
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            HashMap<String, IBinder> services = data.readHashMap(null);
            Bundle coreSettings = data.readBundle();
            bindApplication(packageName, info, providers, testName, profilerInfo, testArgs,
                    testWatcher, uiAutomationConnection, testMode, enableBinderTracking,
                    trackAllocation, restrictedBackupMode, persistent, config, compatInfo, services,
                    coreSettings);
            return true;
        }

        case SCHEDULE_EXIT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            scheduleExit();
            return true;
        }

        case SCHEDULE_SUICIDE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            scheduleSuicide();
            return true;
        }

        case SCHEDULE_CONFIGURATION_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            scheduleConfigurationChanged(config);
            return true;
        }

        case UPDATE_TIME_ZONE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            updateTimeZone();
            return true;
        }

        case CLEAR_DNS_CACHE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            clearDnsCache();
            return true;
        }

        case SET_HTTP_PROXY_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            final String proxy = data.readString();
            final String port = data.readString();
            final String exclList = data.readString();
            final Uri pacFileUrl = Uri.CREATOR.createFromParcel(data);
            setHttpProxy(proxy, port, exclList, pacFileUrl);
            return true;
        }

        case PROCESS_IN_BACKGROUND_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            processInBackground();
            return true;
        }

        case DUMP_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            final IBinder service = data.readStrongBinder();
            final String[] args = data.readStringArray();
            if (fd != null) {
                dumpService(fd.getFileDescriptor(), service, args);
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
            return true;
        }

        case DUMP_PROVIDER_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            final IBinder service = data.readStrongBinder();
            final String[] args = data.readStringArray();
            if (fd != null) {
                dumpProvider(fd.getFileDescriptor(), service, args);
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
            return true;
        }

        case SCHEDULE_REGISTERED_RECEIVER_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IIntentReceiver receiver = IIntentReceiver.Stub.asInterface(
                    data.readStrongBinder());
            Intent intent = Intent.CREATOR.createFromParcel(data);
            int resultCode = data.readInt();
            String dataStr = data.readString();
            Bundle extras = data.readBundle();
            boolean ordered = data.readInt() != 0;
            boolean sticky = data.readInt() != 0;
            int sendingUser = data.readInt();
            int processState = data.readInt();
            scheduleRegisteredReceiver(receiver, intent,
                    resultCode, dataStr, extras, ordered, sticky, sendingUser, processState);
            return true;
        }

        case SCHEDULE_LOW_MEMORY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            scheduleLowMemory();
            return true;
        }

        case SCHEDULE_ACTIVITY_CONFIGURATION_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            Configuration overrideConfig = null;
            if (data.readInt() != 0) {
                overrideConfig = Configuration.CREATOR.createFromParcel(data);
            }
            final boolean reportToActivity = data.readInt() == 1;
            scheduleActivityConfigurationChanged(b, overrideConfig, reportToActivity);
            return true;
        }

        case SCHEDULE_LOCAL_VOICE_INTERACTION_STARTED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            IVoiceInteractor voiceInteractor = IVoiceInteractor.Stub.asInterface(
                    data.readStrongBinder());
            scheduleLocalVoiceInteractionStarted(token, voiceInteractor);
            return true;
        }

        case PROFILER_CONTROL_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            boolean start = data.readInt() != 0;
            int profileType = data.readInt();
            ProfilerInfo profilerInfo = data.readInt() != 0
                    ? ProfilerInfo.CREATOR.createFromParcel(data) : null;
            profilerControl(start, profilerInfo, profileType);
            return true;
        }

        case SET_SCHEDULING_GROUP_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            int group = data.readInt();
            setSchedulingGroup(group);
            return true;
        }

        case SCHEDULE_CREATE_BACKUP_AGENT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ApplicationInfo appInfo = ApplicationInfo.CREATOR.createFromParcel(data);
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            int backupMode = data.readInt();
            scheduleCreateBackupAgent(appInfo, compatInfo, backupMode);
            return true;
        }

        case SCHEDULE_DESTROY_BACKUP_AGENT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ApplicationInfo appInfo = ApplicationInfo.CREATOR.createFromParcel(data);
            CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
            scheduleDestroyBackupAgent(appInfo, compatInfo);
            return true;
        }

        case DISPATCH_PACKAGE_BROADCAST_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            int cmd = data.readInt();
            String[] packages = data.readStringArray();
            dispatchPackageBroadcast(cmd, packages);
            return true;
        }

        case SCHEDULE_CRASH_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            String msg = data.readString();
            scheduleCrash(msg);
            return true;
        }

        case DUMP_HEAP_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            boolean managed = data.readInt() != 0;
            String path = data.readString();
            ParcelFileDescriptor fd = data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            dumpHeap(managed, path, fd);
            return true;
        }

        case DUMP_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            final IBinder activity = data.readStrongBinder();
            final String prefix = data.readString();
            final String[] args = data.readStringArray();
            if (fd != null) {
                dumpActivity(fd.getFileDescriptor(), activity, prefix, args);
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
            return true;
        }

        case SET_CORE_SETTINGS_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            Bundle settings = data.readBundle();
            setCoreSettings(settings);
            return true;
        }

        case UPDATE_PACKAGE_COMPATIBILITY_INFO_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            String pkg = data.readString();
            CompatibilityInfo compat = CompatibilityInfo.CREATOR.createFromParcel(data);
            updatePackageCompatibilityInfo(pkg, compat);
            return true;
        }

        case SCHEDULE_TRIM_MEMORY_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            int level = data.readInt();
            scheduleTrimMemory(level);
            return true;
        }

        case DUMP_MEM_INFO_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            Debug.MemoryInfo mi = Debug.MemoryInfo.CREATOR.createFromParcel(data);
            boolean checkin = data.readInt() != 0;
            boolean dumpInfo = data.readInt() != 0;
            boolean dumpDalvik = data.readInt() != 0;
            boolean dumpSummaryOnly = data.readInt() != 0;
            boolean dumpUnreachable = data.readInt() != 0;
            String[] args = data.readStringArray();
            if (fd != null) {
                try {
                    dumpMemInfo(fd.getFileDescriptor(), mi, checkin, dumpInfo,
                            dumpDalvik, dumpSummaryOnly, dumpUnreachable, args);
                } finally {
                    try {
                        fd.close();
                    } catch (IOException e) {
                        // swallowed, not propagated back to the caller
                    }
                }
            }
            reply.writeNoException();
            return true;
        }

        case DUMP_GFX_INFO_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            String[] args = data.readStringArray();
            if (fd != null) {
                try {
                    dumpGfxInfo(fd.getFileDescriptor(), args);
                } finally {
                    try {
                        fd.close();
                    } catch (IOException e) {
                        // swallowed, not propagated back to the caller
                    }
                }
            }
            reply.writeNoException();
            return true;
        }

        case DUMP_DB_INFO_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            String[] args = data.readStringArray();
            if (fd != null) {
                try {
                    dumpDbInfo(fd.getFileDescriptor(), args);
                } finally {
                    try {
                        fd.close();
                    } catch (IOException e) {
                        // swallowed, not propagated back to the caller
                    }
                }
            }
            reply.writeNoException();
            return true;
        }

        case UNSTABLE_PROVIDER_DIED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder provider = data.readStrongBinder();
            unstableProviderDied(provider);
            reply.writeNoException();
            return true;
        }

        case REQUEST_ASSIST_CONTEXT_EXTRAS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder activityToken = data.readStrongBinder();
            IBinder requestToken = data.readStrongBinder();
            int requestType = data.readInt();
            int sessionId = data.readInt();
            requestAssistContextExtras(activityToken, requestToken, requestType, sessionId);
            reply.writeNoException();
            return true;
        }

        case SCHEDULE_TRANSLUCENT_CONVERSION_COMPLETE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            boolean timeout = data.readInt() == 1;
            scheduleTranslucentConversionComplete(token, timeout);
            reply.writeNoException();
            return true;
        }

        case SCHEDULE_ON_NEW_ACTIVITY_OPTIONS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            ActivityOptions options = new ActivityOptions(data.readBundle());
            scheduleOnNewActivityOptions(token, options);
            reply.writeNoException();
            return true;
        }

        case SET_PROCESS_STATE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            int state = data.readInt();
            setProcessState(state);
            reply.writeNoException();
            return true;
        }

        case SCHEDULE_INSTALL_PROVIDER_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ProviderInfo provider = ProviderInfo.CREATOR.createFromParcel(data);
            scheduleInstallProvider(provider);
            reply.writeNoException();
            return true;
        }

        case UPDATE_TIME_PREFS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            byte is24Hour = data.readByte();
            updateTimePrefs(is24Hour == (byte) 1);
            reply.writeNoException();
            return true;
        }

        case CANCEL_VISIBLE_BEHIND_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            scheduleCancelVisibleBehind(token);
            reply.writeNoException();
            return true;
        }

        case BACKGROUND_VISIBLE_BEHIND_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            boolean enabled = data.readInt() > 0;
            scheduleBackgroundVisibleBehindChanged(token, enabled);
            reply.writeNoException();
            return true;
        }

        case ENTER_ANIMATION_COMPLETE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            scheduleEnterAnimationComplete(token);
            reply.writeNoException();
            return true;
        }

        case NOTIFY_CLEARTEXT_NETWORK_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            final byte[] firstPacket = data.createByteArray();
            notifyCleartextNetwork(firstPacket);
            reply.writeNoException();
            return true;
        }

        case START_BINDER_TRACKING_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            startBinderTracking();
            return true;
        }

        case STOP_BINDER_TRACKING_AND_DUMP_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            if (fd != null) {
                stopBinderTrackingAndDump(fd.getFileDescriptor());
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
            return true;
        }

        case SCHEDULE_MULTI_WINDOW_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            final IBinder b = data.readStrongBinder();
            final boolean inMultiWindow = data.readInt() != 0;
            scheduleMultiWindowModeChanged(b, inMultiWindow);
            return true;
        }

        case SCHEDULE_PICTURE_IN_PICTURE_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            final IBinder b = data.readStrongBinder();
            final boolean inPip = data.readInt() != 0;
            schedulePictureInPictureModeChanged(b, inPip);
            return true;
        }

        }

        return super.onTransact(code, data, reply, flags);
    }

    public IBinder asBinder()
    {
        return this;
    }
}

class ApplicationThreadProxy implements IApplicationThread {
    private final IBinder mRemote;

    public ApplicationThreadProxy(IBinder remote) {
        mRemote = remote;
    }

    public final IBinder asBinder() {
        return mRemote;
    }

    public final void schedulePauseActivity(IBinder token, boolean finished,
            boolean userLeaving, int configChanges, boolean dontReport) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(finished ? 1 : 0);
        data.writeInt(userLeaving ? 1 :0);
        data.writeInt(configChanges);
        data.writeInt(dontReport ? 1 : 0);
        mRemote.transact(SCHEDULE_PAUSE_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleStopActivity(IBinder token, boolean showWindow,
            int configChanges) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(showWindow ? 1 : 0);
        data.writeInt(configChanges);
        mRemote.transact(SCHEDULE_STOP_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleWindowVisibility(IBinder token,
            boolean showWindow) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(showWindow ? 1 : 0);
        mRemote.transact(SCHEDULE_WINDOW_VISIBILITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleSleeping(IBinder token,
            boolean sleeping) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(sleeping ? 1 : 0);
        mRemote.transact(SCHEDULE_SLEEPING_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleResumeActivity(IBinder token, int procState, boolean isForward,
            Bundle resumeArgs)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(procState);
        data.writeInt(isForward ? 1 : 0);
        data.writeBundle(resumeArgs);
        mRemote.transact(SCHEDULE_RESUME_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleSendResult(IBinder token, List<ResultInfo> results)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeTypedList(results);
        mRemote.transact(SCHEDULE_SEND_RESULT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident,
            ActivityInfo info, Configuration curConfig, Configuration overrideConfig,
            CompatibilityInfo compatInfo, String referrer, IVoiceInteractor voiceInteractor,
            int procState, Bundle state, PersistableBundle persistentState,
            List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents,
            boolean notResumed, boolean isForward, ProfilerInfo profilerInfo) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        intent.writeToParcel(data, 0);
        data.writeStrongBinder(token);
        data.writeInt(ident);
        info.writeToParcel(data, 0);
        curConfig.writeToParcel(data, 0);
        if (overrideConfig != null) {
            data.writeInt(1);
            overrideConfig.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        compatInfo.writeToParcel(data, 0);
        data.writeString(referrer);
        data.writeStrongBinder(voiceInteractor != null ? voiceInteractor.asBinder() : null);
        data.writeInt(procState);
        data.writeBundle(state);
        data.writePersistableBundle(persistentState);
        data.writeTypedList(pendingResults);
        data.writeTypedList(pendingNewIntents);
        data.writeInt(notResumed ? 1 : 0);
        data.writeInt(isForward ? 1 : 0);
        if (profilerInfo != null) {
            data.writeInt(1);
            profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleRelaunchActivity(IBinder token,
            List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents,
            int configChanges, boolean notResumed, Configuration config,
            Configuration overrideConfig, boolean preserveWindow) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeTypedList(pendingResults);
        data.writeTypedList(pendingNewIntents);
        data.writeInt(configChanges);
        data.writeInt(notResumed ? 1 : 0);
        config.writeToParcel(data, 0);
        if (overrideConfig != null) {
            data.writeInt(1);
            overrideConfig.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(preserveWindow ? 1 : 0);
        mRemote.transact(SCHEDULE_RELAUNCH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void scheduleNewIntent(List<ReferrerIntent> intents, IBinder token, boolean andPause)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeTypedList(intents);
        data.writeStrongBinder(token);
        data.writeInt(andPause ? 1 : 0);
        mRemote.transact(SCHEDULE_NEW_INTENT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleDestroyActivity(IBinder token, boolean finishing,
            int configChanges) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(finishing ? 1 : 0);
        data.writeInt(configChanges);
        mRemote.transact(SCHEDULE_FINISH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleReceiver(Intent intent, ActivityInfo info,
            CompatibilityInfo compatInfo, int resultCode, String resultData,
            Bundle map, boolean sync, int sendingUser, int processState) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        intent.writeToParcel(data, 0);
        info.writeToParcel(data, 0);
        compatInfo.writeToParcel(data, 0);
        data.writeInt(resultCode);
        data.writeString(resultData);
        data.writeBundle(map);
        data.writeInt(sync ? 1 : 0);
        data.writeInt(sendingUser);
        data.writeInt(processState);
        mRemote.transact(SCHEDULE_RECEIVER_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleCreateBackupAgent(ApplicationInfo app,
            CompatibilityInfo compatInfo, int backupMode) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        app.writeToParcel(data, 0);
        compatInfo.writeToParcel(data, 0);
        data.writeInt(backupMode);
        mRemote.transact(SCHEDULE_CREATE_BACKUP_AGENT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleDestroyBackupAgent(ApplicationInfo app,
            CompatibilityInfo compatInfo) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        app.writeToParcel(data, 0);
        compatInfo.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_DESTROY_BACKUP_AGENT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleCreateService(IBinder token, ServiceInfo info,
            CompatibilityInfo compatInfo, int processState) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        info.writeToParcel(data, 0);
        compatInfo.writeToParcel(data, 0);
        data.writeInt(processState);
        try {
            mRemote.transact(SCHEDULE_CREATE_SERVICE_TRANSACTION, data, null,
                    IBinder.FLAG_ONEWAY);
        } catch (TransactionTooLargeException e) {
            Log.e("CREATE_SERVICE", "Binder failure starting service; service=" + info);
            throw e;
        }
        data.recycle();
    }

    public final void scheduleBindService(IBinder token, Intent intent, boolean rebind,
            int processState) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        data.writeInt(rebind ? 1 : 0);
        data.writeInt(processState);
        mRemote.transact(SCHEDULE_BIND_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleUnbindService(IBinder token, Intent intent)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_UNBIND_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleServiceArgs(IBinder token, boolean taskRemoved, int startId,
            int flags, Intent args) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(taskRemoved ? 1 : 0);
        data.writeInt(startId);
        data.writeInt(flags);
        if (args != null) {
            data.writeInt(1);
            args.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(SCHEDULE_SERVICE_ARGS_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleStopService(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(SCHEDULE_STOP_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public final void bindApplication(String packageName, ApplicationInfo info,
            List<ProviderInfo> providers, ComponentName testName, ProfilerInfo profilerInfo,
            Bundle testArgs, IInstrumentationWatcher testWatcher,
            IUiAutomationConnection uiAutomationConnection, int debugMode,
            boolean enableBinderTracking, boolean trackAllocation, boolean restrictedBackupMode,
            boolean persistent, Configuration config, CompatibilityInfo compatInfo,
            Map<String, IBinder> services, Bundle coreSettings) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeString(packageName);
        info.writeToParcel(data, 0);
        data.writeTypedList(providers);
        if (testName == null) {
            data.writeInt(0);
        } else {
            data.writeInt(1);
            testName.writeToParcel(data, 0);
        }
        if (profilerInfo != null) {
            data.writeInt(1);
            profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        data.writeBundle(testArgs);
        data.writeStrongInterface(testWatcher);
        data.writeStrongInterface(uiAutomationConnection);
        data.writeInt(debugMode);
        data.writeInt(enableBinderTracking ? 1 : 0);
        data.writeInt(trackAllocation ? 1 : 0);
        data.writeInt(restrictedBackupMode ? 1 : 0);
        data.writeInt(persistent ? 1 : 0);
        config.writeToParcel(data, 0);
        compatInfo.writeToParcel(data, 0);
        data.writeMap(services);
        data.writeBundle(coreSettings);
        mRemote.transact(BIND_APPLICATION_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleExit() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(SCHEDULE_EXIT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleSuicide() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(SCHEDULE_SUICIDE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleConfigurationChanged(Configuration config)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        config.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_CONFIGURATION_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleLocalVoiceInteractionStarted(IBinder token,
            IVoiceInteractor voiceInteractor) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeStrongBinder(voiceInteractor != null ? voiceInteractor.asBinder() : null);
        mRemote.transact(SCHEDULE_LOCAL_VOICE_INTERACTION_STARTED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void updateTimeZone() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(UPDATE_TIME_ZONE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void clearDnsCache() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(CLEAR_DNS_CACHE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void setHttpProxy(String proxy, String port, String exclList,
            Uri pacFileUrl) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeString(proxy);
        data.writeString(port);
        data.writeString(exclList);
        pacFileUrl.writeToParcel(data, 0);
        mRemote.transact(SET_HTTP_PROXY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void processInBackground() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(PROCESS_IN_BACKGROUND_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpService(FileDescriptor fd, IBinder token, String[] args)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStrongBinder(token);
        data.writeStringArray(args);
        mRemote.transact(DUMP_SERVICE_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpProvider(FileDescriptor fd, IBinder token, String[] args)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStrongBinder(token);
        data.writeStringArray(args);
        mRemote.transact(DUMP_PROVIDER_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void scheduleRegisteredReceiver(IIntentReceiver receiver, Intent intent,
            int resultCode, String dataStr, Bundle extras, boolean ordered,
            boolean sticky, int sendingUser, int processState) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(receiver.asBinder());
        intent.writeToParcel(data, 0);
        data.writeInt(resultCode);
        data.writeString(dataStr);
        data.writeBundle(extras);
        data.writeInt(ordered ? 1 : 0);
        data.writeInt(sticky ? 1 : 0);
        data.writeInt(sendingUser);
        data.writeInt(processState);
        mRemote.transact(SCHEDULE_REGISTERED_RECEIVER_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public final void scheduleLowMemory() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(SCHEDULE_LOW_MEMORY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public final void scheduleActivityConfigurationChanged(IBinder token,
            Configuration overrideConfig, boolean reportToActivity) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        if (overrideConfig != null) {
            data.writeInt(1);
            overrideConfig.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(reportToActivity ? 1 : 0);
        mRemote.transact(SCHEDULE_ACTIVITY_CONFIGURATION_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void profilerControl(boolean start, ProfilerInfo profilerInfo, int profileType)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(start ? 1 : 0);
        data.writeInt(profileType);
        if (profilerInfo != null) {
            data.writeInt(1);
            profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(PROFILER_CONTROL_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void setSchedulingGroup(int group) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(group);
        mRemote.transact(SET_SCHEDULING_GROUP_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dispatchPackageBroadcast(int cmd, String[] packages) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(cmd);
        data.writeStringArray(packages);
        mRemote.transact(DISPATCH_PACKAGE_BROADCAST_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void scheduleCrash(String msg) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeString(msg);
        mRemote.transact(SCHEDULE_CRASH_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpHeap(boolean managed, String path,
            ParcelFileDescriptor fd) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(managed ? 1 : 0);
        data.writeString(path);
        if (fd != null) {
            data.writeInt(1);
            fd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(DUMP_HEAP_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpActivity(FileDescriptor fd, IBinder token, String prefix, String[] args)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStrongBinder(token);
        data.writeString(prefix);
        data.writeStringArray(args);
        mRemote.transact(DUMP_ACTIVITY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void setCoreSettings(Bundle coreSettings) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeBundle(coreSettings);
        mRemote.transact(SET_CORE_SETTINGS_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
    }

    public void updatePackageCompatibilityInfo(String pkg, CompatibilityInfo info)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeString(pkg);
        info.writeToParcel(data, 0);
        mRemote.transact(UPDATE_PACKAGE_COMPATIBILITY_INFO_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
    }

    public void scheduleTrimMemory(int level) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(level);
        mRemote.transact(SCHEDULE_TRIM_MEMORY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpMemInfo(FileDescriptor fd, Debug.MemoryInfo mem, boolean checkin,
            boolean dumpInfo, boolean dumpDalvik, boolean dumpSummaryOnly,
            boolean dumpUnreachable, String[] args) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        mem.writeToParcel(data, 0);
        data.writeInt(checkin ? 1 : 0);
        data.writeInt(dumpInfo ? 1 : 0);
        data.writeInt(dumpDalvik ? 1 : 0);
        data.writeInt(dumpSummaryOnly ? 1 : 0);
        data.writeInt(dumpUnreachable ? 1 : 0);
        data.writeStringArray(args);
        mRemote.transact(DUMP_MEM_INFO_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void dumpGfxInfo(FileDescriptor fd, String[] args) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStringArray(args);
        mRemote.transact(DUMP_GFX_INFO_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpDbInfo(FileDescriptor fd, String[] args) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStringArray(args);
        mRemote.transact(DUMP_DB_INFO_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void unstableProviderDied(IBinder provider) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(provider);
        mRemote.transact(UNSTABLE_PROVIDER_DIED_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void requestAssistContextExtras(IBinder activityToken, IBinder requestToken,
            int requestType, int sessionId) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(activityToken);
        data.writeStrongBinder(requestToken);
        data.writeInt(requestType);
        data.writeInt(sessionId);
        mRemote.transact(REQUEST_ASSIST_CONTEXT_EXTRAS_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleTranslucentConversionComplete(IBinder token, boolean timeout)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(timeout ? 1 : 0);
        mRemote.transact(SCHEDULE_TRANSLUCENT_CONVERSION_COMPLETE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleOnNewActivityOptions(IBinder token, ActivityOptions options)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeBundle(options == null ? null : options.toBundle());
        mRemote.transact(SCHEDULE_ON_NEW_ACTIVITY_OPTIONS_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void setProcessState(int state) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeInt(state);
        mRemote.transact(SET_PROCESS_STATE_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleInstallProvider(ProviderInfo provider) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        provider.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_INSTALL_PROVIDER_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void updateTimePrefs(boolean is24Hour) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeByte(is24Hour ? (byte) 1 : (byte) 0);
        mRemote.transact(UPDATE_TIME_PREFS_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleCancelVisibleBehind(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(CANCEL_VISIBLE_BEHIND_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleBackgroundVisibleBehindChanged(IBinder token, boolean enabled)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(enabled ? 1 : 0);
        mRemote.transact(BACKGROUND_VISIBLE_BEHIND_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void scheduleEnterAnimationComplete(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(ENTER_ANIMATION_COMPLETE_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void notifyCleartextNetwork(byte[] firstPacket) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeByteArray(firstPacket);
        mRemote.transact(NOTIFY_CLEARTEXT_NETWORK_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void startBinderTracking() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(START_BINDER_TRACKING_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public void stopBinderTrackingAndDump(FileDescriptor fd) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        mRemote.transact(STOP_BINDER_TRACKING_AND_DUMP_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public final void scheduleMultiWindowModeChanged(
            IBinder token, boolean isInMultiWindowMode) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(isInMultiWindowMode ? 1 : 0);
        mRemote.transact(SCHEDULE_MULTI_WINDOW_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    @Override
    public final void schedulePictureInPictureModeChanged(IBinder token, boolean isInPipMode)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(isInPipMode ? 1 : 0);
        mRemote.transact(SCHEDULE_PICTURE_IN_PICTURE_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
}
