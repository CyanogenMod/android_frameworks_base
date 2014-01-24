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

import android.app.ActivityManager.StackBoxInfo;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.UriPermission;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Singleton;

import java.util.ArrayList;
import java.util.List;

/** {@hide} */
public abstract class ActivityManagerNative extends Binder implements IActivityManager
{
    /**
     * Cast a Binder object into an activity manager interface, generating
     * a proxy if needed.
     */
    static public IActivityManager asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        IActivityManager in =
            (IActivityManager)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }

        return new ActivityManagerProxy(obj);
    }

    /**
     * Retrieve the system's default/global activity manager.
     */
    static public IActivityManager getDefault() {
        return gDefault.get();
    }

    /**
     * Convenience for checking whether the system is ready.  For internal use only.
     */
    static public boolean isSystemReady() {
        if (!sSystemReady) {
            sSystemReady = getDefault().testIsSystemReady();
        }
        return sSystemReady;
    }
    static boolean sSystemReady = false;

    /**
     * Convenience for sending a sticky broadcast.  For internal use only.
     * If you don't care about permission, use null.
     */
    static public void broadcastStickyIntent(Intent intent, String permission, int userId) {
        try {
            getDefault().broadcastIntent(
                null, intent, null, null, Activity.RESULT_OK, null, null,
                null /*permission*/, AppOpsManager.OP_NONE, false, true, userId);
        } catch (RemoteException ex) {
        }
    }

    static public void noteWakeupAlarm(PendingIntent ps) {
        try {
            getDefault().noteWakeupAlarm(ps.getTarget());
        } catch (RemoteException ex) {
        }
    }

    public ActivityManagerNative() {
        attachInterface(this, descriptor);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
        case START_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            int startFlags = data.readInt();
            String profileFile = data.readString();
            ParcelFileDescriptor profileFd = data.readInt() != 0
                    ? data.readFileDescriptor() : null;
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int result = startActivity(app, callingPackage, intent, resolvedType,
                    resultTo, resultWho, requestCode, startFlags,
                    profileFile, profileFd, options);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case START_ACTIVITY_AS_USER_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            int startFlags = data.readInt();
            String profileFile = data.readString();
            ParcelFileDescriptor profileFd = data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int userId = data.readInt();
            int result = startActivityAsUser(app, callingPackage, intent, resolvedType,
                    resultTo, resultWho, requestCode, startFlags,
                    profileFile, profileFd, options, userId);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case START_ACTIVITY_AND_WAIT_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            int startFlags = data.readInt();
            String profileFile = data.readString();
            ParcelFileDescriptor profileFd = data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int userId = data.readInt();
            WaitResult result = startActivityAndWait(app, callingPackage, intent, resolvedType,
                    resultTo, resultWho, requestCode, startFlags,
                    profileFile, profileFd, options, userId);
            reply.writeNoException();
            result.writeToParcel(reply, 0);
            return true;
        }

        case START_ACTIVITY_WITH_CONFIG_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            int startFlags = data.readInt();
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int userId = data.readInt();
            int result = startActivityWithConfig(app, callingPackage, intent, resolvedType,
                    resultTo, resultWho, requestCode, startFlags, config, options, userId);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case START_ACTIVITY_INTENT_SENDER_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            IntentSender intent = IntentSender.CREATOR.createFromParcel(data);
            Intent fillInIntent = null;
            if (data.readInt() != 0) {
                fillInIntent = Intent.CREATOR.createFromParcel(data);
            }
            String resolvedType = data.readString();
            IBinder resultTo = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            int flagsMask = data.readInt();
            int flagsValues = data.readInt();
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int result = startActivityIntentSender(app, intent,
                    fillInIntent, resolvedType, resultTo, resultWho,
                    requestCode, flagsMask, flagsValues, options);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case START_NEXT_MATCHING_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder callingActivity = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            boolean result = startNextMatchingActivity(callingActivity, intent, options);
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
            return true;
        }

        case FINISH_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Intent resultData = null;
            int resultCode = data.readInt();
            if (data.readInt() != 0) {
                resultData = Intent.CREATOR.createFromParcel(data);
            }
            boolean res = finishActivity(token, resultCode, resultData);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case FINISH_SUB_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            finishSubActivity(token, resultWho, requestCode);
            reply.writeNoException();
            return true;
        }

        case FINISH_ACTIVITY_AFFINITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean res = finishActivityAffinity(token);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case WILL_ACTIVITY_BE_VISIBLE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean res = willActivityBeVisible(token);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case REGISTER_RECEIVER_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app =
                b != null ? ApplicationThreadNative.asInterface(b) : null;
            String packageName = data.readString();
            b = data.readStrongBinder();
            IIntentReceiver rec
                = b != null ? IIntentReceiver.Stub.asInterface(b) : null;
            IntentFilter filter = IntentFilter.CREATOR.createFromParcel(data);
            String perm = data.readString();
            int userId = data.readInt();
            Intent intent = registerReceiver(app, packageName, rec, filter, perm, userId);
            reply.writeNoException();
            if (intent != null) {
                reply.writeInt(1);
                intent.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case UNREGISTER_RECEIVER_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            if (b == null) {
                return true;
            }
            IIntentReceiver rec = IIntentReceiver.Stub.asInterface(b);
            unregisterReceiver(rec);
            reply.writeNoException();
            return true;
        }

        case BROADCAST_INTENT_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app =
                b != null ? ApplicationThreadNative.asInterface(b) : null;
            Intent intent = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            b = data.readStrongBinder();
            IIntentReceiver resultTo =
                b != null ? IIntentReceiver.Stub.asInterface(b) : null;
            int resultCode = data.readInt();
            String resultData = data.readString();
            Bundle resultExtras = data.readBundle();
            String perm = data.readString();
            int appOp = data.readInt();
            boolean serialized = data.readInt() != 0;
            boolean sticky = data.readInt() != 0;
            int userId = data.readInt();
            int res = broadcastIntent(app, intent, resolvedType, resultTo,
                    resultCode, resultData, resultExtras, perm, appOp,
                    serialized, sticky, userId);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case UNBROADCAST_INTENT_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = b != null ? ApplicationThreadNative.asInterface(b) : null;
            Intent intent = Intent.CREATOR.createFromParcel(data);
            int userId = data.readInt();
            unbroadcastIntent(app, intent, userId);
            reply.writeNoException();
            return true;
        }

        case FINISH_RECEIVER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder who = data.readStrongBinder();
            int resultCode = data.readInt();
            String resultData = data.readString();
            Bundle resultExtras = data.readBundle();
            boolean resultAbort = data.readInt() != 0;
            if (who != null) {
                finishReceiver(who, resultCode, resultData, resultExtras, resultAbort);
            }
            reply.writeNoException();
            return true;
        }

        case ATTACH_APPLICATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IApplicationThread app = ApplicationThreadNative.asInterface(
                    data.readStrongBinder());
            if (app != null) {
                attachApplication(app);
            }
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_IDLE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Configuration config = null;
            if (data.readInt() != 0) {
                config = Configuration.CREATOR.createFromParcel(data);
            }
            boolean stopProfiling = data.readInt() != 0;
            if (token != null) {
                activityIdle(token, config, stopProfiling);
            }
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_RESUMED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            activityResumed(token);
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_PAUSED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            activityPaused(token);
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_STOPPED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Bundle map = data.readBundle();
            Bitmap thumbnail = data.readInt() != 0
                ? Bitmap.CREATOR.createFromParcel(data) : null;
            CharSequence description = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(data);
            activityStopped(token, map, thumbnail, description);
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_SLEPT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            activitySlept(token);
            reply.writeNoException();
            return true;
        }

        case ACTIVITY_DESTROYED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            activityDestroyed(token);
            reply.writeNoException();
            return true;
        }

        case GET_CALLING_PACKAGE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            String res = token != null ? getCallingPackage(token) : null;
            reply.writeNoException();
            reply.writeString(res);
            return true;
        }

        case GET_CALLING_PACKAGE_FOR_BROADCAST_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            boolean foreground = data.readInt() == 1 ? true : false;
            String res = getCallingPackageForBroadcast(foreground);
            reply.writeNoException();
            reply.writeString(res);
            return true;
        }

        case GET_CALLING_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            ComponentName cn = getCallingActivity(token);
            reply.writeNoException();
            ComponentName.writeToParcel(cn, reply);
            return true;
        }

        case GET_TASKS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int maxNum = data.readInt();
            int fl = data.readInt();
            IBinder receiverBinder = data.readStrongBinder();
            IThumbnailReceiver receiver = receiverBinder != null
                ? IThumbnailReceiver.Stub.asInterface(receiverBinder)
                : null;
            List<ActivityManager.RunningTaskInfo> list = getTasks(maxNum, fl, receiver);
            reply.writeNoException();
            int N = list != null ? list.size() : -1;
            reply.writeInt(N);
            int i;
            for (i=0; i<N; i++) {
                ActivityManager.RunningTaskInfo info = list.get(i);
                info.writeToParcel(reply, 0);
            }
            return true;
        }

        case GET_RECENT_TASKS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int maxNum = data.readInt();
            int fl = data.readInt();
            int userId = data.readInt();
            List<ActivityManager.RecentTaskInfo> list = getRecentTasks(maxNum,
                    fl, userId);
            reply.writeNoException();
            reply.writeTypedList(list);
            return true;
        }

        case GET_TASK_THUMBNAILS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int id = data.readInt();
            ActivityManager.TaskThumbnails bm = getTaskThumbnails(id);
            reply.writeNoException();
            if (bm != null) {
                reply.writeInt(1);
                bm.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case GET_TASK_TOP_THUMBNAIL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int id = data.readInt();
            Bitmap bm = getTaskTopThumbnail(id);
            reply.writeNoException();
            if (bm != null) {
                reply.writeInt(1);
                bm.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case GET_SERVICES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int maxNum = data.readInt();
            int fl = data.readInt();
            List<ActivityManager.RunningServiceInfo> list = getServices(maxNum, fl);
            reply.writeNoException();
            int N = list != null ? list.size() : -1;
            reply.writeInt(N);
            int i;
            for (i=0; i<N; i++) {
                ActivityManager.RunningServiceInfo info = list.get(i);
                info.writeToParcel(reply, 0);
            }
            return true;
        }

        case GET_PROCESSES_IN_ERROR_STATE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            List<ActivityManager.ProcessErrorStateInfo> list = getProcessesInErrorState();
            reply.writeNoException();
            reply.writeTypedList(list);
            return true;
        }

        case GET_RUNNING_APP_PROCESSES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            List<ActivityManager.RunningAppProcessInfo> list = getRunningAppProcesses();
            reply.writeNoException();
            reply.writeTypedList(list);
            return true;
        }

        case GET_RUNNING_EXTERNAL_APPLICATIONS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            List<ApplicationInfo> list = getRunningExternalApplications();
            reply.writeNoException();
            reply.writeTypedList(list);
            return true;
        }

        case MOVE_TASK_TO_FRONT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int task = data.readInt();
            int fl = data.readInt();
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            moveTaskToFront(task, fl, options);
            reply.writeNoException();
            return true;
        }

        case MOVE_TASK_TO_BACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int task = data.readInt();
            moveTaskToBack(task);
            reply.writeNoException();
            return true;
        }

        case MOVE_ACTIVITY_TASK_TO_BACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean nonRoot = data.readInt() != 0;
            boolean res = moveActivityTaskToBack(token, nonRoot);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case MOVE_TASK_BACKWARDS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int task = data.readInt();
            moveTaskBackwards(task);
            reply.writeNoException();
            return true;
        }

        case CREATE_STACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int taskId = data.readInt();
            int relativeStackId = data.readInt();
            int position = data.readInt();
            float weight = data.readFloat();
            int res = createStack(taskId, relativeStackId, position, weight);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case MOVE_TASK_TO_STACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int taskId = data.readInt();
            int stackId = data.readInt();
            boolean toTop = data.readInt() != 0;
            moveTaskToStack(taskId, stackId, toTop);
            reply.writeNoException();
            return true;
        }

        case RESIZE_STACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int stackBoxId = data.readInt();
            float weight = data.readFloat();
            resizeStackBox(stackBoxId, weight);
            reply.writeNoException();
            return true;
        }

        case GET_STACK_BOXES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            List<StackBoxInfo> list = getStackBoxes();
            reply.writeNoException();
            reply.writeTypedList(list);
            return true;
        }

        case GET_STACK_BOX_INFO_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int stackBoxId = data.readInt();
            StackBoxInfo info = getStackBoxInfo(stackBoxId);
            reply.writeNoException();
            if (info != null) {
                reply.writeInt(1);
                info.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case SET_FOCUSED_STACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int stackId = data.readInt();
            setFocusedStack(stackId);
            reply.writeNoException();
            return true;
        }

        case GET_TASK_FOR_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean onlyRoot = data.readInt() != 0;
            int res = token != null
                ? getTaskForActivity(token, onlyRoot) : -1;
                reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case GET_ACTIVITY_FOR_TASK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int task = data.readInt();
            boolean onlyRoot = data.readInt() != 0;
            IBinder res = getActivityForTask(task, onlyRoot);
            reply.writeNoException();
            reply.writeStrongBinder(res);
            return true;
        }

        case REPORT_THUMBNAIL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Bitmap thumbnail = data.readInt() != 0
                ? Bitmap.CREATOR.createFromParcel(data) : null;
            CharSequence description = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(data);
            reportThumbnail(token, thumbnail, description);
            reply.writeNoException();
            return true;
        }

        case GET_CONTENT_PROVIDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String name = data.readString();
            int userId = data.readInt();
            boolean stable = data.readInt() != 0;
            ContentProviderHolder cph = getContentProvider(app, name, userId, stable);
            reply.writeNoException();
            if (cph != null) {
                reply.writeInt(1);
                cph.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case GET_CONTENT_PROVIDER_EXTERNAL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String name = data.readString();
            int userId = data.readInt();
            IBinder token = data.readStrongBinder();
            ContentProviderHolder cph = getContentProviderExternal(name, userId, token);
            reply.writeNoException();
            if (cph != null) {
                reply.writeInt(1);
                cph.writeToParcel(reply, 0);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case PUBLISH_CONTENT_PROVIDERS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            ArrayList<ContentProviderHolder> providers =
                data.createTypedArrayList(ContentProviderHolder.CREATOR);
            publishContentProviders(app, providers);
            reply.writeNoException();
            return true;
        }

        case REF_CONTENT_PROVIDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            int stable = data.readInt();
            int unstable = data.readInt();
            boolean res = refContentProvider(b, stable, unstable);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case UNSTABLE_PROVIDER_DIED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            unstableProviderDied(b);
            reply.writeNoException();
            return true;
        }

        case APP_NOT_RESPONDING_VIA_PROVIDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            appNotRespondingViaProvider(b);
            reply.writeNoException();
            return true;
        }

        case REMOVE_CONTENT_PROVIDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            boolean stable = data.readInt() != 0;
            removeContentProvider(b, stable);
            reply.writeNoException();
            return true;
        }

        case REMOVE_CONTENT_PROVIDER_EXTERNAL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String name = data.readString();
            IBinder token = data.readStrongBinder();
            removeContentProviderExternal(name, token);
            reply.writeNoException();
            return true;
        }

        case GET_RUNNING_SERVICE_CONTROL_PANEL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ComponentName comp = ComponentName.CREATOR.createFromParcel(data);
            PendingIntent pi = getRunningServiceControlPanel(comp);
            reply.writeNoException();
            PendingIntent.writePendingIntentOrNullToParcel(pi, reply);
            return true;
        }

        case START_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            Intent service = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            int userId = data.readInt();
            ComponentName cn = startService(app, service, resolvedType, userId);
            reply.writeNoException();
            ComponentName.writeToParcel(cn, reply);
            return true;
        }

        case STOP_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            Intent service = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            int userId = data.readInt();
            int res = stopService(app, service, resolvedType, userId);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case STOP_SERVICE_TOKEN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ComponentName className = ComponentName.readFromParcel(data);
            IBinder token = data.readStrongBinder();
            int startId = data.readInt();
            boolean res = stopServiceToken(className, token, startId);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case SET_SERVICE_FOREGROUND_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ComponentName className = ComponentName.readFromParcel(data);
            IBinder token = data.readStrongBinder();
            int id = data.readInt();
            Notification notification = null;
            if (data.readInt() != 0) {
                notification = Notification.CREATOR.createFromParcel(data);
            }
            boolean removeNotification = data.readInt() != 0;
            setServiceForeground(className, token, id, notification, removeNotification);
            reply.writeNoException();
            return true;
        }

        case BIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            IBinder token = data.readStrongBinder();
            Intent service = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            b = data.readStrongBinder();
            int fl = data.readInt();
            int userId = data.readInt();
            IServiceConnection conn = IServiceConnection.Stub.asInterface(b);
            int res = bindService(app, token, service, resolvedType, conn, fl, userId);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case UNBIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IServiceConnection conn = IServiceConnection.Stub.asInterface(b);
            boolean res = unbindService(conn);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case PUBLISH_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            IBinder service = data.readStrongBinder();
            publishService(token, intent, service);
            reply.writeNoException();
            return true;
        }

        case UNBIND_FINISHED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            boolean doRebind = data.readInt() != 0;
            unbindFinished(token, intent, doRebind);
            reply.writeNoException();
            return true;
        }

        case SERVICE_DONE_EXECUTING_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            int type = data.readInt();
            int startId = data.readInt();
            int res = data.readInt();
            serviceDoneExecuting(token, type, startId, res);
            reply.writeNoException();
            return true;
        }

        case START_INSTRUMENTATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ComponentName className = ComponentName.readFromParcel(data);
            String profileFile = data.readString();
            int fl = data.readInt();
            Bundle arguments = data.readBundle();
            IBinder b = data.readStrongBinder();
            IInstrumentationWatcher w = IInstrumentationWatcher.Stub.asInterface(b);
            b = data.readStrongBinder();
            IUiAutomationConnection c = IUiAutomationConnection.Stub.asInterface(b);
            int userId = data.readInt();
            boolean res = startInstrumentation(className, profileFile, fl, arguments, w, c, userId);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }


        case FINISH_INSTRUMENTATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            int resultCode = data.readInt();
            Bundle results = data.readBundle();
            finishInstrumentation(app, resultCode, results);
            reply.writeNoException();
            return true;
        }

        case GET_CONFIGURATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Configuration config = getConfiguration();
            reply.writeNoException();
            config.writeToParcel(reply, 0);
            return true;
        }

        case UPDATE_CONFIGURATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            updateConfiguration(config);
            reply.writeNoException();
            return true;
        }

        case SET_REQUESTED_ORIENTATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            int requestedOrientation = data.readInt();
            setRequestedOrientation(token, requestedOrientation);
            reply.writeNoException();
            return true;
        }

        case GET_REQUESTED_ORIENTATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            int req = getRequestedOrientation(token);
            reply.writeNoException();
            reply.writeInt(req);
            return true;
        }

        case GET_ACTIVITY_CLASS_FOR_TOKEN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            ComponentName cn = getActivityClassForToken(token);
            reply.writeNoException();
            ComponentName.writeToParcel(cn, reply);
            return true;
        }

        case GET_PACKAGE_FOR_TOKEN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            reply.writeNoException();
            reply.writeString(getPackageForToken(token));
            return true;
        }

        case GET_INTENT_SENDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int type = data.readInt();
            String packageName = data.readString();
            IBinder token = data.readStrongBinder();
            String resultWho = data.readString();
            int requestCode = data.readInt();
            Intent[] requestIntents;
            String[] requestResolvedTypes;
            if (data.readInt() != 0) {
                requestIntents = data.createTypedArray(Intent.CREATOR);
                requestResolvedTypes = data.createStringArray();
            } else {
                requestIntents = null;
                requestResolvedTypes = null;
            }
            int fl = data.readInt();
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int userId = data.readInt();
            IIntentSender res = getIntentSender(type, packageName, token,
                    resultWho, requestCode, requestIntents,
                    requestResolvedTypes, fl, options, userId);
            reply.writeNoException();
            reply.writeStrongBinder(res != null ? res.asBinder() : null);
            return true;
        }

        case CANCEL_INTENT_SENDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            cancelIntentSender(r);
            reply.writeNoException();
            return true;
        }

        case GET_PACKAGE_FOR_INTENT_SENDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            String res = getPackageForIntentSender(r);
            reply.writeNoException();
            reply.writeString(res);
            return true;
        }

        case GET_UID_FOR_INTENT_SENDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            int res = getUidForIntentSender(r);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case HANDLE_INCOMING_USER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int callingPid = data.readInt();
            int callingUid = data.readInt();
            int userId = data.readInt();
            boolean allowAll = data.readInt() != 0 ;
            boolean requireFull = data.readInt() != 0;
            String name = data.readString();
            String callerPackage = data.readString();
            int res = handleIncomingUser(callingPid, callingUid, userId, allowAll,
                    requireFull, name, callerPackage);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case SET_PROCESS_LIMIT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int max = data.readInt();
            setProcessLimit(max);
            reply.writeNoException();
            return true;
        }

        case GET_PROCESS_LIMIT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int limit = getProcessLimit();
            reply.writeNoException();
            reply.writeInt(limit);
            return true;
        }

        case SET_PROCESS_FOREGROUND_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            int pid = data.readInt();
            boolean isForeground = data.readInt() != 0;
            setProcessForeground(token, pid, isForeground);
            reply.writeNoException();
            return true;
        }

        case CHECK_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String perm = data.readString();
            int pid = data.readInt();
            int uid = data.readInt();
            int res = checkPermission(perm, pid, uid);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case CHECK_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int pid = data.readInt();
            int uid = data.readInt();
            int mode = data.readInt();
            int res = checkUriPermission(uri, pid, uid, mode);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case CLEAR_APP_DATA_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String packageName = data.readString();
            IPackageDataObserver observer = IPackageDataObserver.Stub.asInterface(
                    data.readStrongBinder());
            int userId = data.readInt();
            boolean res = clearApplicationUserData(packageName, observer, userId);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case GRANT_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String targetPkg = data.readString();
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int mode = data.readInt();
            grantUriPermission(app, targetPkg, uri, mode);
            reply.writeNoException();
            return true;
        }

        case REVOKE_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int mode = data.readInt();
            revokeUriPermission(app, uri, mode);
            reply.writeNoException();
            return true;
        }

        case TAKE_PERSISTABLE_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int mode = data.readInt();
            takePersistableUriPermission(uri, mode);
            reply.writeNoException();
            return true;
        }

        case RELEASE_PERSISTABLE_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int mode = data.readInt();
            releasePersistableUriPermission(uri, mode);
            reply.writeNoException();
            return true;
        }

        case GET_PERSISTED_URI_PERMISSIONS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            final String packageName = data.readString();
            final boolean incoming = data.readInt() != 0;
            final ParceledListSlice<UriPermission> perms = getPersistedUriPermissions(
                    packageName, incoming);
            reply.writeNoException();
            perms.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            return true;
        }

        case SHOW_WAITING_FOR_DEBUGGER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            boolean waiting = data.readInt() != 0;
            showWaitingForDebugger(app, waiting);
            reply.writeNoException();
            return true;
        }

        case GET_MEMORY_INFO_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            getMemoryInfo(mi);
            reply.writeNoException();
            mi.writeToParcel(reply, 0);
            return true;
        }

        case UNHANDLED_BACK_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            unhandledBack();
            reply.writeNoException();
            return true;
        }

        case OPEN_CONTENT_URI_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Uri uri = Uri.parse(data.readString());
            ParcelFileDescriptor pfd = openContentUri(uri);
            reply.writeNoException();
            if (pfd != null) {
                reply.writeInt(1);
                pfd.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case GOING_TO_SLEEP_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            goingToSleep();
            reply.writeNoException();
            return true;
        }

        case WAKING_UP_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            wakingUp();
            reply.writeNoException();
            return true;
        }

        case SET_LOCK_SCREEN_SHOWN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            setLockScreenShown(data.readInt() != 0);
            reply.writeNoException();
            return true;
        }

        case SET_DEBUG_APP_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String pn = data.readString();
            boolean wfd = data.readInt() != 0;
            boolean per = data.readInt() != 0;
            setDebugApp(pn, wfd, per);
            reply.writeNoException();
            return true;
        }

        case SET_ALWAYS_FINISH_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            boolean enabled = data.readInt() != 0;
            setAlwaysFinish(enabled);
            reply.writeNoException();
            return true;
        }

        case SET_ACTIVITY_CONTROLLER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IActivityController watcher = IActivityController.Stub.asInterface(
                    data.readStrongBinder());
            setActivityController(watcher);
            reply.writeNoException();
            return true;
        }

        case ENTER_SAFE_MODE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            enterSafeMode();
            reply.writeNoException();
            return true;
        }

        case NOTE_WAKEUP_ALARM_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender is = IIntentSender.Stub.asInterface(
                    data.readStrongBinder());
            noteWakeupAlarm(is);
            reply.writeNoException();
            return true;
        }

        case KILL_PIDS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int[] pids = data.createIntArray();
            String reason = data.readString();
            boolean secure = data.readInt() != 0;
            boolean res = killPids(pids, reason, secure);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case KILL_PROCESSES_BELOW_FOREGROUND_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String reason = data.readString();
            boolean res = killProcessesBelowForeground(reason);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case START_RUNNING_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            String cls = data.readString();
            String action = data.readString();
            String indata = data.readString();
            startRunning(pkg, cls, action, indata);
            reply.writeNoException();
            return true;
        }

        case HANDLE_APPLICATION_CRASH_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder app = data.readStrongBinder();
            ApplicationErrorReport.CrashInfo ci = new ApplicationErrorReport.CrashInfo(data);
            handleApplicationCrash(app, ci);
            reply.writeNoException();
            return true;
        }

        case HANDLE_APPLICATION_WTF_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder app = data.readStrongBinder();
            String tag = data.readString();
            ApplicationErrorReport.CrashInfo ci = new ApplicationErrorReport.CrashInfo(data);
            boolean res = handleApplicationWtf(app, tag, ci);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case HANDLE_APPLICATION_STRICT_MODE_VIOLATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder app = data.readStrongBinder();
            int violationMask = data.readInt();
            StrictMode.ViolationInfo info = new StrictMode.ViolationInfo(data);
            handleApplicationStrictModeViolation(app, violationMask, info);
            reply.writeNoException();
            return true;
        }

        case SIGNAL_PERSISTENT_PROCESSES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int sig = data.readInt();
            signalPersistentProcesses(sig);
            reply.writeNoException();
            return true;
        }

        case KILL_BACKGROUND_PROCESSES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String packageName = data.readString();
            int userId = data.readInt();
            killBackgroundProcesses(packageName, userId);
            reply.writeNoException();
            return true;
        }

        case KILL_ALL_BACKGROUND_PROCESSES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            killAllBackgroundProcesses();
            reply.writeNoException();
            return true;
        }

        case FORCE_STOP_PACKAGE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String packageName = data.readString();
            int userId = data.readInt();
            forceStopPackage(packageName, userId);
            reply.writeNoException();
            return true;
        }

        case GET_MY_MEMORY_STATE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ActivityManager.RunningAppProcessInfo info =
                    new ActivityManager.RunningAppProcessInfo();
            getMyMemoryState(info);
            reply.writeNoException();
            info.writeToParcel(reply, 0);
            return true;
        }

        case GET_DEVICE_CONFIGURATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ConfigurationInfo config = getDeviceConfigurationInfo();
            reply.writeNoException();
            config.writeToParcel(reply, 0);
            return true;
        }
        
        case PROFILE_CONTROL_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String process = data.readString();
            int userId = data.readInt();
            boolean start = data.readInt() != 0;
            int profileType = data.readInt();
            String path = data.readString();
            ParcelFileDescriptor fd = data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            boolean res = profileControl(process, userId, start, path, fd, profileType);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }
        
        case SHUTDOWN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            boolean res = shutdown(data.readInt());
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }
        
        case STOP_APP_SWITCHES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            stopAppSwitches();
            reply.writeNoException();
            return true;
        }
        
        case RESUME_APP_SWITCHES_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            resumeAppSwitches();
            reply.writeNoException();
            return true;
        }
        
        case PEEK_SERVICE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Intent service = Intent.CREATOR.createFromParcel(data);
            String resolvedType = data.readString();
            IBinder binder = peekService(service, resolvedType);
            reply.writeNoException();
            reply.writeStrongBinder(binder);
            return true;
        }
        
        case START_BACKUP_AGENT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ApplicationInfo info = ApplicationInfo.CREATOR.createFromParcel(data);
            int backupRestoreMode = data.readInt();
            boolean success = bindBackupAgent(info, backupRestoreMode);
            reply.writeNoException();
            reply.writeInt(success ? 1 : 0);
            return true;
        }

        case BACKUP_AGENT_CREATED_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String packageName = data.readString();
            IBinder agent = data.readStrongBinder();
            backupAgentCreated(packageName, agent);
            reply.writeNoException();
            return true;
        }

        case UNBIND_BACKUP_AGENT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            ApplicationInfo info = ApplicationInfo.CREATOR.createFromParcel(data);
            unbindBackupAgent(info);
            reply.writeNoException();
            return true;
        }
        
        case KILL_APPLICATION_WITH_APPID_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            int appid = data.readInt();
            String reason = data.readString();
            killApplicationWithAppId(pkg, appid, reason);
            reply.writeNoException();
            return true;
        }
        
        case CLOSE_SYSTEM_DIALOGS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String reason = data.readString();
            closeSystemDialogs(reason);
            reply.writeNoException();
            return true;
        }
        
        case GET_PROCESS_MEMORY_INFO_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int[] pids = data.createIntArray();
            Debug.MemoryInfo[] res =  getProcessMemoryInfo(pids);
            reply.writeNoException();
            reply.writeTypedArray(res, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            return true;
        }

        case KILL_APPLICATION_PROCESS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String processName = data.readString();
            int uid = data.readInt();
            killApplicationProcess(processName, uid);
            reply.writeNoException();
            return true;
        }
        
        case OVERRIDE_PENDING_TRANSITION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            String packageName = data.readString();
            int enterAnim = data.readInt();
            int exitAnim = data.readInt();
            overridePendingTransition(token, packageName, enterAnim, exitAnim);
            reply.writeNoException();
            return true;
        }
        
        case IS_USER_A_MONKEY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            boolean areThey = isUserAMonkey();
            reply.writeNoException();
            reply.writeInt(areThey ? 1 : 0);
            return true;
        }
        
        case SET_USER_IS_MONKEY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            final boolean monkey = (data.readInt() == 1);
            setUserIsMonkey(monkey);
            reply.writeNoException();
            return true;
        }

        case FINISH_HEAVY_WEIGHT_APP_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            finishHeavyWeightApp();
            reply.writeNoException();
            return true;
        }

        case IS_IMMERSIVE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean isit = isImmersive(token);
            reply.writeNoException();
            reply.writeInt(isit ? 1 : 0);
            return true;
        }

        case CONVERT_FROM_TRANSLUCENT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean converted = convertFromTranslucent(token);
            reply.writeNoException();
            reply.writeInt(converted ? 1 : 0);
            return true;
        }

        case CONVERT_TO_TRANSLUCENT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean converted = convertToTranslucent(token);
            reply.writeNoException();
            reply.writeInt(converted ? 1 : 0);
            return true;
        }

        case SET_IMMERSIVE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            boolean imm = data.readInt() == 1;
            setImmersive(token, imm);
            reply.writeNoException();
            return true;
        }
        
        case IS_TOP_ACTIVITY_IMMERSIVE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            boolean isit = isTopActivityImmersive();
            reply.writeNoException();
            reply.writeInt(isit ? 1 : 0);
            return true;
        }

        case CRASH_APPLICATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int uid = data.readInt();
            int initialPid = data.readInt();
            String packageName = data.readString();
            String message = data.readString();
            crashApplication(uid, initialPid, packageName, message);
            reply.writeNoException();
            return true;
        }

        case GET_PROVIDER_MIME_TYPE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int userId = data.readInt();
            String type = getProviderMimeType(uri, userId);
            reply.writeNoException();
            reply.writeString(type);
            return true;
        }

        case NEW_URI_PERMISSION_OWNER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String name = data.readString();
            IBinder perm = newUriPermissionOwner(name);
            reply.writeNoException();
            reply.writeStrongBinder(perm);
            return true;
        }

        case GRANT_URI_PERMISSION_FROM_OWNER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder owner = data.readStrongBinder();
            int fromUid = data.readInt();
            String targetPkg = data.readString();
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int mode = data.readInt();
            grantUriPermissionFromOwner(owner, fromUid, targetPkg, uri, mode);
            reply.writeNoException();
            return true;
        }

        case REVOKE_URI_PERMISSION_FROM_OWNER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder owner = data.readStrongBinder();
            Uri uri = null;
            if (data.readInt() != 0) {
                Uri.CREATOR.createFromParcel(data);
            }
            int mode = data.readInt();
            revokeUriPermissionFromOwner(owner, uri, mode);
            reply.writeNoException();
            return true;
        }

        case CHECK_GRANT_URI_PERMISSION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int callingUid = data.readInt();
            String targetPkg = data.readString();
            Uri uri = Uri.CREATOR.createFromParcel(data);
            int modeFlags = data.readInt();
            int res = checkGrantUriPermission(callingUid, targetPkg, uri, modeFlags);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case DUMP_HEAP_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            String process = data.readString();
            int userId = data.readInt();
            boolean managed = data.readInt() != 0;
            String path = data.readString();
            ParcelFileDescriptor fd = data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            boolean res = dumpHeap(process, userId, managed, path, fd);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case START_ACTIVITIES_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder b = data.readStrongBinder();
            IApplicationThread app = ApplicationThreadNative.asInterface(b);
            String callingPackage = data.readString();
            Intent[] intents = data.createTypedArray(Intent.CREATOR);
            String[] resolvedTypes = data.createStringArray();
            IBinder resultTo = data.readStrongBinder();
            Bundle options = data.readInt() != 0
                    ? Bundle.CREATOR.createFromParcel(data) : null;
            int userId = data.readInt();
            int result = startActivities(app, callingPackage, intents, resolvedTypes, resultTo,
                    options, userId);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case GET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            int mode = getFrontActivityScreenCompatMode();
            reply.writeNoException();
            reply.writeInt(mode);
            return true;
        }

        case SET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            int mode = data.readInt();
            setFrontActivityScreenCompatMode(mode);
            reply.writeNoException();
            reply.writeInt(mode);
            return true;
        }

        case GET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            int mode = getPackageScreenCompatMode(pkg);
            reply.writeNoException();
            reply.writeInt(mode);
            return true;
        }

        case SET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            int mode = data.readInt();
            setPackageScreenCompatMode(pkg, mode);
            reply.writeNoException();
            return true;
        }
        
        case SWITCH_USER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int userid = data.readInt();
            boolean result = switchUser(userid);
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
            return true;
        }

        case STOP_USER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int userid = data.readInt();
            IStopUserCallback callback = IStopUserCallback.Stub.asInterface(
                    data.readStrongBinder());
            int result = stopUser(userid, callback);
            reply.writeNoException();
            reply.writeInt(result);
            return true;
        }

        case GET_CURRENT_USER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            UserInfo userInfo = getCurrentUser();
            reply.writeNoException();
            userInfo.writeToParcel(reply, 0);
            return true;
        }

        case IS_USER_RUNNING_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int userid = data.readInt();
            boolean orStopping = data.readInt() != 0;
            boolean result = isUserRunning(userid, orStopping);
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
            return true;
        }

        case GET_RUNNING_USER_IDS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int[] result = getRunningUserIds();
            reply.writeNoException();
            reply.writeIntArray(result);
            return true;
        }

        case REMOVE_SUB_TASK_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            int taskId = data.readInt();
            int subTaskIndex = data.readInt();
            boolean result = removeSubTask(taskId, subTaskIndex);
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
            return true;
        }

        case REMOVE_TASK_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            int taskId = data.readInt();
            int fl = data.readInt();
            boolean result = removeTask(taskId, fl);
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
            return true;
        }

        case REGISTER_PROCESS_OBSERVER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IProcessObserver observer = IProcessObserver.Stub.asInterface(
                    data.readStrongBinder());
            registerProcessObserver(observer);
            return true;
        }

        case UNREGISTER_PROCESS_OBSERVER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IProcessObserver observer = IProcessObserver.Stub.asInterface(
                    data.readStrongBinder());
            unregisterProcessObserver(observer);
            return true;
        }

        case GET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            boolean ask = getPackageAskScreenCompat(pkg);
            reply.writeNoException();
            reply.writeInt(ask ? 1 : 0);
            return true;
        }

        case SET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION:
        {
            data.enforceInterface(IActivityManager.descriptor);
            String pkg = data.readString();
            boolean ask = data.readInt() != 0;
            setPackageAskScreenCompat(pkg, ask);
            reply.writeNoException();
            return true;
        }

        case IS_INTENT_SENDER_TARGETED_TO_PACKAGE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            boolean res = isIntentSenderTargetedToPackage(r);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case IS_INTENT_SENDER_AN_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            boolean res = isIntentSenderAnActivity(r);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case GET_INTENT_FOR_INTENT_SENDER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IIntentSender r = IIntentSender.Stub.asInterface(
                data.readStrongBinder());
            Intent intent = getIntentForIntentSender(r);
            reply.writeNoException();
            if (intent != null) {
                reply.writeInt(1);
                intent.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            } else {
                reply.writeInt(0);
            }
            return true;
        }

        case UPDATE_PERSISTENT_CONFIGURATION_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            updatePersistentConfiguration(config);
            reply.writeNoException();
            return true;
        }

        case GET_PROCESS_PSS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int[] pids = data.createIntArray();
            long[] pss = getProcessPss(pids);
            reply.writeNoException();
            reply.writeLongArray(pss);
            return true;
        }

        case SHOW_BOOT_MESSAGE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            CharSequence msg = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(data);
            boolean always = data.readInt() != 0;
            showBootMessage(msg, always);
            reply.writeNoException();
            return true;
        }

        case DISMISS_KEYGUARD_ON_NEXT_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            dismissKeyguardOnNextActivity();
            reply.writeNoException();
            return true;
        }

        case TARGET_TASK_AFFINITY_MATCHES_ACTIVITY_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            String destAffinity = data.readString();
            boolean res = targetTaskAffinityMatchesActivity(token, destAffinity);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case NAVIGATE_UP_TO_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Intent target = Intent.CREATOR.createFromParcel(data);
            int resultCode = data.readInt();
            Intent resultData = null;
            if (data.readInt() != 0) {
                resultData = Intent.CREATOR.createFromParcel(data);
            }
            boolean res = navigateUpTo(token, target, resultCode, resultData);
            reply.writeNoException();
            reply.writeInt(res ? 1 : 0);
            return true;
        }

        case GET_LAUNCHED_FROM_UID_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            int res = getLaunchedFromUid(token);
            reply.writeNoException();
            reply.writeInt(res);
            return true;
        }

        case GET_LAUNCHED_FROM_PACKAGE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            String res = getLaunchedFromPackage(token);
            reply.writeNoException();
            reply.writeString(res);
            return true;
        }

        case REGISTER_USER_SWITCH_OBSERVER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IUserSwitchObserver observer = IUserSwitchObserver.Stub.asInterface(
                    data.readStrongBinder());
            registerUserSwitchObserver(observer);
            reply.writeNoException();
            return true;
        }

        case UNREGISTER_USER_SWITCH_OBSERVER_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IUserSwitchObserver observer = IUserSwitchObserver.Stub.asInterface(
                    data.readStrongBinder());
            unregisterUserSwitchObserver(observer);
            reply.writeNoException();
            return true;
        }

        case REQUEST_BUG_REPORT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            requestBugReport();
            reply.writeNoException();
            return true;
        }

        case INPUT_DISPATCHING_TIMED_OUT_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int pid = data.readInt();
            boolean aboveSystem = data.readInt() != 0;
            String reason = data.readString();
            long res = inputDispatchingTimedOut(pid, aboveSystem, reason);
            reply.writeNoException();
            reply.writeLong(res);
            return true;
        }

        case GET_ASSIST_CONTEXT_EXTRAS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int requestType = data.readInt();
            Bundle res = getAssistContextExtras(requestType);
            reply.writeNoException();
            reply.writeBundle(res);
            return true;
        }

        case REPORT_ASSIST_CONTEXT_EXTRAS_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            Bundle extras = data.readBundle();
            reportAssistContextExtras(token, extras);
            reply.writeNoException();
            return true;
        }

        case KILL_UID_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            int uid = data.readInt();
            String reason = data.readString();
            killUid(uid, reason);
            reply.writeNoException();
            return true;
        }

        case HANG_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder who = data.readStrongBinder();
            boolean allowRestart = data.readInt() != 0;
            hang(who, allowRestart);
            reply.writeNoException();
            return true;
        }

        case REPORT_ACTIVITY_FULLY_DRAWN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            reportActivityFullyDrawn(token);
            reply.writeNoException();
            return true;
        }

        case NOTIFY_ACTIVITY_DRAWN_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            IBinder token = data.readStrongBinder();
            notifyActivityDrawn(token);
            reply.writeNoException();
            return true;
        }

        case RESTART_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            restart();
            reply.writeNoException();
            return true;
        }

        case PERFORM_IDLE_MAINTENANCE_TRANSACTION: {
            data.enforceInterface(IActivityManager.descriptor);
            performIdleMaintenance();
            reply.writeNoException();
            return true;
        }

        case NOTIFY_SPLIT_VIEW_LAYOUT_CHANGED: {
            data.enforceInterface(IActivityManager.descriptor);
            notifySplitViewLayoutChanged();
            reply.writeNoException();
            return true;
        }
        }

        return super.onTransact(code, data, reply, flags);
    }

    public IBinder asBinder() {
        return this;
    }

    private static final Singleton<IActivityManager> gDefault = new Singleton<IActivityManager>() {
        protected IActivityManager create() {
            IBinder b = ServiceManager.getService("activity");
            if (false) {
                Log.v("ActivityManager", "default service binder = " + b);
            }
            IActivityManager am = asInterface(b);
            if (false) {
                Log.v("ActivityManager", "default service = " + am);
            }
            return am;
        }
    };
}

class ActivityManagerProxy implements IActivityManager
{
    public ActivityManagerProxy(IBinder remote)
    {
        mRemote = remote;
    }

    public IBinder asBinder()
    {
        return mRemote;
    }

    public int startActivity(IApplicationThread caller, String callingPackage, Intent intent,
            String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        if (intent.getComponent() != null) {
            Log.i("ActivityManager", "Timeline: Activity_launch_request id:"
                    + intent.getComponent().getPackageName() + " time:"
                    + SystemClock.uptimeMillis());
        }

        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        data.writeString(profileFile);
        if (profileFd != null) {
            data.writeInt(1);
            profileFd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(START_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }

    public int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent,
            String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        data.writeString(profileFile);
        if (profileFd != null) {
            data.writeInt(1);
            profileFd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(userId);
        mRemote.transact(START_ACTIVITY_AS_USER_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }
    public WaitResult startActivityAndWait(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int startFlags, String profileFile,
            ParcelFileDescriptor profileFd, Bundle options, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        data.writeString(profileFile);
        if (profileFd != null) {
            data.writeInt(1);
            profileFd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(userId);
        mRemote.transact(START_ACTIVITY_AND_WAIT_TRANSACTION, data, reply, 0);
        reply.readException();
        WaitResult result = WaitResult.CREATOR.createFromParcel(reply);
        reply.recycle();
        data.recycle();
        return result;
    }
    public int startActivityWithConfig(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int startFlags, Configuration config,
            Bundle options, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        config.writeToParcel(data, 0);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(userId);
        mRemote.transact(START_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }
    public int startActivityIntentSender(IApplicationThread caller,
            IntentSender intent, Intent fillInIntent, String resolvedType,
            IBinder resultTo, String resultWho, int requestCode,
            int flagsMask, int flagsValues, Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        intent.writeToParcel(data, 0);
        if (fillInIntent != null) {
            data.writeInt(1);
            fillInIntent.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(flagsMask);
        data.writeInt(flagsValues);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(START_ACTIVITY_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }
    public boolean startNextMatchingActivity(IBinder callingActivity,
            Intent intent, Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(callingActivity);
        intent.writeToParcel(data, 0);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(START_NEXT_MATCHING_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result != 0;
    }
    public boolean finishActivity(IBinder token, int resultCode, Intent resultData)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(resultCode);
        if (resultData != null) {
            data.writeInt(1);
            resultData.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(FINISH_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public void finishSubActivity(IBinder token, String resultWho, int requestCode) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        mRemote.transact(FINISH_SUB_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public boolean finishActivityAffinity(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(FINISH_ACTIVITY_AFFINITY_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public boolean willActivityBeVisible(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(WILL_ACTIVITY_BE_VISIBLE_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public Intent registerReceiver(IApplicationThread caller, String packageName,
            IIntentReceiver receiver,
            IntentFilter filter, String perm, int userId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(packageName);
        data.writeStrongBinder(receiver != null ? receiver.asBinder() : null);
        filter.writeToParcel(data, 0);
        data.writeString(perm);
        data.writeInt(userId);
        mRemote.transact(REGISTER_RECEIVER_TRANSACTION, data, reply, 0);
        reply.readException();
        Intent intent = null;
        int haveIntent = reply.readInt();
        if (haveIntent != 0) {
            intent = Intent.CREATOR.createFromParcel(reply);
        }
        reply.recycle();
        data.recycle();
        return intent;
    }
    public void unregisterReceiver(IIntentReceiver receiver) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(receiver.asBinder());
        mRemote.transact(UNREGISTER_RECEIVER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int broadcastIntent(IApplicationThread caller,
            Intent intent, String resolvedType,  IIntentReceiver resultTo,
            int resultCode, String resultData, Bundle map,
            String requiredPermission, int appOp, boolean serialized,
            boolean sticky, int userId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo != null ? resultTo.asBinder() : null);
        data.writeInt(resultCode);
        data.writeString(resultData);
        data.writeBundle(map);
        data.writeString(requiredPermission);
        data.writeInt(appOp);
        data.writeInt(serialized ? 1 : 0);
        data.writeInt(sticky ? 1 : 0);
        data.writeInt(userId);
        mRemote.transact(BROADCAST_INTENT_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        reply.recycle();
        data.recycle();
        return res;
    }
    public void unbroadcastIntent(IApplicationThread caller, Intent intent, int userId)
            throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        intent.writeToParcel(data, 0);
        data.writeInt(userId);
        mRemote.transact(UNBROADCAST_INTENT_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void finishReceiver(IBinder who, int resultCode, String resultData, Bundle map, boolean abortBroadcast) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(who);
        data.writeInt(resultCode);
        data.writeString(resultData);
        data.writeBundle(map);
        data.writeInt(abortBroadcast ? 1 : 0);
        mRemote.transact(FINISH_RECEIVER_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void attachApplication(IApplicationThread app) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(app.asBinder());
        mRemote.transact(ATTACH_APPLICATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activityIdle(IBinder token, Configuration config, boolean stopProfiling)
            throws RemoteException
    {
        Log.i("ActivityManager", "Timeline: Activity_idle id: " + token + " time:"
                + SystemClock.uptimeMillis());
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        if (config != null) {
            data.writeInt(1);
            config.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(stopProfiling ? 1 : 0);
        mRemote.transact(ACTIVITY_IDLE_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activityResumed(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(ACTIVITY_RESUMED_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activityPaused(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(ACTIVITY_PAUSED_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activityStopped(IBinder token, Bundle state,
            Bitmap thumbnail, CharSequence description) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeBundle(state);
        if (thumbnail != null) {
            data.writeInt(1);
            thumbnail.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        TextUtils.writeToParcel(description, data, 0);
        mRemote.transact(ACTIVITY_STOPPED_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activitySlept(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(ACTIVITY_SLEPT_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void activityDestroyed(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(ACTIVITY_DESTROYED_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public String getCallingPackage(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(GET_CALLING_PACKAGE_TRANSACTION, data, reply, 0);
        reply.readException();
        String res = reply.readString();
        data.recycle();
        reply.recycle();
        return res;
    }
    public String getCallingPackageForBroadcast(boolean foreground) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(foreground ? 1 : 0);
        mRemote.transact(GET_CALLING_PACKAGE_FOR_BROADCAST_TRANSACTION, data, reply, 0);
        reply.readException();
        String res = reply.readString();
        data.recycle();
        reply.recycle();
        return res;
    }
    public ComponentName getCallingActivity(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(GET_CALLING_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        ComponentName res = ComponentName.readFromParcel(reply);
        data.recycle();
        reply.recycle();
        return res;
    }
    public List getTasks(int maxNum, int flags,
            IThumbnailReceiver receiver) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(maxNum);
        data.writeInt(flags);
        data.writeStrongBinder(receiver != null ? receiver.asBinder() : null);
        mRemote.transact(GET_TASKS_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList list = null;
        int N = reply.readInt();
        if (N >= 0) {
            list = new ArrayList();
            while (N > 0) {
                ActivityManager.RunningTaskInfo info =
                        ActivityManager.RunningTaskInfo.CREATOR
                        .createFromParcel(reply);
                list.add(info);
                N--;
            }
        }
        data.recycle();
        reply.recycle();
        return list;
    }
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum,
            int flags, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(maxNum);
        data.writeInt(flags);
        data.writeInt(userId);
        mRemote.transact(GET_RECENT_TASKS_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList<ActivityManager.RecentTaskInfo> list
            = reply.createTypedArrayList(ActivityManager.RecentTaskInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return list;
    }
    public ActivityManager.TaskThumbnails getTaskThumbnails(int id) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(id);
        mRemote.transact(GET_TASK_THUMBNAILS_TRANSACTION, data, reply, 0);
        reply.readException();
        ActivityManager.TaskThumbnails bm = null;
        if (reply.readInt() != 0) {
            bm = ActivityManager.TaskThumbnails.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return bm;
    }
    public Bitmap getTaskTopThumbnail(int id) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(id);
        mRemote.transact(GET_TASK_TOP_THUMBNAIL_TRANSACTION, data, reply, 0);
        reply.readException();
        Bitmap bm = null;
        if (reply.readInt() != 0) {
            bm = Bitmap.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return bm;
    }
    public List getServices(int maxNum, int flags) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(maxNum);
        data.writeInt(flags);
        mRemote.transact(GET_SERVICES_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList list = null;
        int N = reply.readInt();
        if (N >= 0) {
            list = new ArrayList();
            while (N > 0) {
                ActivityManager.RunningServiceInfo info =
                        ActivityManager.RunningServiceInfo.CREATOR
                        .createFromParcel(reply);
                list.add(info);
                N--;
            }
        }
        data.recycle();
        reply.recycle();
        return list;
    }
    public List<ActivityManager.ProcessErrorStateInfo> getProcessesInErrorState()
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_PROCESSES_IN_ERROR_STATE_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList<ActivityManager.ProcessErrorStateInfo> list
            = reply.createTypedArrayList(ActivityManager.ProcessErrorStateInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return list;
    }
    public List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses()
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_RUNNING_APP_PROCESSES_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList<ActivityManager.RunningAppProcessInfo> list
        = reply.createTypedArrayList(ActivityManager.RunningAppProcessInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return list;
    }
    public List<ApplicationInfo> getRunningExternalApplications()
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_RUNNING_EXTERNAL_APPLICATIONS_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList<ApplicationInfo> list
        = reply.createTypedArrayList(ApplicationInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return list;
    }
    public void moveTaskToFront(int task, int flags, Bundle options) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(task);
        data.writeInt(flags);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(MOVE_TASK_TO_FRONT_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void moveTaskToBack(int task) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(task);
        mRemote.transact(MOVE_TASK_TO_BACK_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public boolean moveActivityTaskToBack(IBinder token, boolean nonRoot)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(nonRoot ? 1 : 0);
        mRemote.transact(MOVE_ACTIVITY_TASK_TO_BACK_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public void moveTaskBackwards(int task) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(task);
        mRemote.transact(MOVE_TASK_BACKWARDS_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    @Override
    public int createStack(int taskId, int relativeStackBoxId, int position, float weight)
            throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(taskId);
        data.writeInt(relativeStackBoxId);
        data.writeInt(position);
        data.writeFloat(weight);
        mRemote.transact(CREATE_STACK_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    @Override
    public void moveTaskToStack(int taskId, int stackId, boolean toTop) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(taskId);
        data.writeInt(stackId);
        data.writeInt(toTop ? 1 : 0);
        mRemote.transact(MOVE_TASK_TO_STACK_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    @Override
    public void resizeStackBox(int stackBoxId, float weight) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(stackBoxId);
        data.writeFloat(weight);
        mRemote.transact(RESIZE_STACK_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    @Override
    public List<StackBoxInfo> getStackBoxes() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_STACK_BOXES_TRANSACTION, data, reply, 0);
        reply.readException();
        ArrayList<StackBoxInfo> list = reply.createTypedArrayList(StackBoxInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return list;
    }
    @Override
    public StackBoxInfo getStackBoxInfo(int stackBoxId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(stackBoxId);
        mRemote.transact(GET_STACK_BOX_INFO_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        StackBoxInfo info = null;
        if (res != 0) {
            info = StackBoxInfo.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return info;
    }
    @Override
    public void setFocusedStack(int stackId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(stackId);
        mRemote.transact(SET_FOCUSED_STACK_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int getTaskForActivity(IBinder token, boolean onlyRoot) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(onlyRoot ? 1 : 0);
        mRemote.transact(GET_TASK_FOR_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public IBinder getActivityForTask(int task, boolean onlyRoot) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(task);
        data.writeInt(onlyRoot ? 1 : 0);
        mRemote.transact(GET_ACTIVITY_FOR_TASK_TRANSACTION, data, reply, 0);
        reply.readException();
        IBinder res = reply.readStrongBinder();
        data.recycle();
        reply.recycle();
        return res;
    }
    public void reportThumbnail(IBinder token,
                                Bitmap thumbnail, CharSequence description) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        if (thumbnail != null) {
            data.writeInt(1);
            thumbnail.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        TextUtils.writeToParcel(description, data, 0);
        mRemote.transact(REPORT_THUMBNAIL_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public ContentProviderHolder getContentProvider(IApplicationThread caller,
            String name, int userId, boolean stable) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(name);
        data.writeInt(userId);
        data.writeInt(stable ? 1 : 0);
        mRemote.transact(GET_CONTENT_PROVIDER_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        ContentProviderHolder cph = null;
        if (res != 0) {
            cph = ContentProviderHolder.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return cph;
    }
    public ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(name);
        data.writeInt(userId);
        data.writeStrongBinder(token);
        mRemote.transact(GET_CONTENT_PROVIDER_EXTERNAL_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        ContentProviderHolder cph = null;
        if (res != 0) {
            cph = ContentProviderHolder.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return cph;
    }
    public void publishContentProviders(IApplicationThread caller,
            List<ContentProviderHolder> providers) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeTypedList(providers);
        mRemote.transact(PUBLISH_CONTENT_PROVIDERS_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public boolean refContentProvider(IBinder connection, int stable, int unstable)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(connection);
        data.writeInt(stable);
        data.writeInt(unstable);
        mRemote.transact(REF_CONTENT_PROVIDER_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public void unstableProviderDied(IBinder connection) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(connection);
        mRemote.transact(UNSTABLE_PROVIDER_DIED_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    @Override
    public void appNotRespondingViaProvider(IBinder connection) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(connection);
        mRemote.transact(APP_NOT_RESPONDING_VIA_PROVIDER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void removeContentProvider(IBinder connection, boolean stable) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(connection);
        data.writeInt(stable ? 1 : 0);
        mRemote.transact(REMOVE_CONTENT_PROVIDER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void removeContentProviderExternal(String name, IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(name);
        data.writeStrongBinder(token);
        mRemote.transact(REMOVE_CONTENT_PROVIDER_EXTERNAL_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public PendingIntent getRunningServiceControlPanel(ComponentName service)
            throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        service.writeToParcel(data, 0);
        mRemote.transact(GET_RUNNING_SERVICE_CONTROL_PANEL_TRANSACTION, data, reply, 0);
        reply.readException();
        PendingIntent res = PendingIntent.readPendingIntentOrNullFromParcel(reply);
        data.recycle();
        reply.recycle();
        return res;
    }
    
    public ComponentName startService(IApplicationThread caller, Intent service,
            String resolvedType, int userId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        service.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeInt(userId);
        mRemote.transact(START_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        ComponentName res = ComponentName.readFromParcel(reply);
        data.recycle();
        reply.recycle();
        return res;
    }
    public int stopService(IApplicationThread caller, Intent service,
            String resolvedType, int userId) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        service.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeInt(userId);
        mRemote.transact(STOP_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        reply.recycle();
        data.recycle();
        return res;
    }
    public boolean stopServiceToken(ComponentName className, IBinder token,
            int startId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        ComponentName.writeToParcel(className, data);
        data.writeStrongBinder(token);
        data.writeInt(startId);
        mRemote.transact(STOP_SERVICE_TOKEN_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public void setServiceForeground(ComponentName className, IBinder token,
            int id, Notification notification, boolean removeNotification) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        ComponentName.writeToParcel(className, data);
        data.writeStrongBinder(token);
        data.writeInt(id);
        if (notification != null) {
            data.writeInt(1);
            notification.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(removeNotification ? 1 : 0);
        mRemote.transact(SET_SERVICE_FOREGROUND_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int bindService(IApplicationThread caller, IBinder token,
            Intent service, String resolvedType, IServiceConnection connection,
            int flags, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeStrongBinder(token);
        service.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(connection.asBinder());
        data.writeInt(flags);
        data.writeInt(userId);
        mRemote.transact(BIND_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public boolean unbindService(IServiceConnection connection) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(connection.asBinder());
        mRemote.transact(UNBIND_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    
    public void publishService(IBinder token,
            Intent intent, IBinder service) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        data.writeStrongBinder(service);
        mRemote.transact(PUBLISH_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void unbindFinished(IBinder token, Intent intent, boolean doRebind)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        data.writeInt(doRebind ? 1 : 0);
        mRemote.transact(UNBIND_FINISHED_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void serviceDoneExecuting(IBinder token, int type, int startId,
            int res) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(type);
        data.writeInt(startId);
        data.writeInt(res);
        mRemote.transact(SERVICE_DONE_EXECUTING_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    
    public IBinder peekService(Intent service, String resolvedType) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        service.writeToParcel(data, 0);
        data.writeString(resolvedType);
        mRemote.transact(PEEK_SERVICE_TRANSACTION, data, reply, 0);
        reply.readException();
        IBinder binder = reply.readStrongBinder();
        reply.recycle();
        data.recycle();
        return binder;
    }

    public boolean bindBackupAgent(ApplicationInfo app, int backupRestoreMode)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        app.writeToParcel(data, 0);
        data.writeInt(backupRestoreMode);
        mRemote.transact(START_BACKUP_AGENT_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean success = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return success;
    }

    public void clearPendingBackup() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(CLEAR_PENDING_BACKUP_TRANSACTION, data, reply, 0);
        reply.recycle();
        data.recycle();
    }

    public void backupAgentCreated(String packageName, IBinder agent) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeStrongBinder(agent);
        mRemote.transact(BACKUP_AGENT_CREATED_TRANSACTION, data, reply, 0);
        reply.recycle();
        data.recycle();
    }

    public void unbindBackupAgent(ApplicationInfo app) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        app.writeToParcel(data, 0);
        mRemote.transact(UNBIND_BACKUP_AGENT_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public boolean startInstrumentation(ComponentName className, String profileFile,
            int flags, Bundle arguments, IInstrumentationWatcher watcher,
            IUiAutomationConnection connection, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        ComponentName.writeToParcel(className, data);
        data.writeString(profileFile);
        data.writeInt(flags);
        data.writeBundle(arguments);
        data.writeStrongBinder(watcher != null ? watcher.asBinder() : null);
        data.writeStrongBinder(connection != null ? connection.asBinder() : null);
        data.writeInt(userId);
        mRemote.transact(START_INSTRUMENTATION_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return res;
    }

    public void finishInstrumentation(IApplicationThread target,
            int resultCode, Bundle results) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(target != null ? target.asBinder() : null);
        data.writeInt(resultCode);
        data.writeBundle(results);
        mRemote.transact(FINISH_INSTRUMENTATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public Configuration getConfiguration() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_CONFIGURATION_TRANSACTION, data, reply, 0);
        reply.readException();
        Configuration res = Configuration.CREATOR.createFromParcel(reply);
        reply.recycle();
        data.recycle();
        return res;
    }
    public void updateConfiguration(Configuration values) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        values.writeToParcel(data, 0);
        mRemote.transact(UPDATE_CONFIGURATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void setRequestedOrientation(IBinder token, int requestedOrientation)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(requestedOrientation);
        mRemote.transact(SET_REQUESTED_ORIENTATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int getRequestedOrientation(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(GET_REQUESTED_ORIENTATION_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public ComponentName getActivityClassForToken(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(GET_ACTIVITY_CLASS_FOR_TOKEN_TRANSACTION, data, reply, 0);
        reply.readException();
        ComponentName res = ComponentName.readFromParcel(reply);
        data.recycle();
        reply.recycle();
        return res;
    }
    public String getPackageForToken(IBinder token) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(GET_PACKAGE_FOR_TOKEN_TRANSACTION, data, reply, 0);
        reply.readException();
        String res = reply.readString();
        data.recycle();
        reply.recycle();
        return res;
    }
    public IIntentSender getIntentSender(int type,
            String packageName, IBinder token, String resultWho,
            int requestCode, Intent[] intents, String[] resolvedTypes, int flags,
            Bundle options, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(type);
        data.writeString(packageName);
        data.writeStrongBinder(token);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        if (intents != null) {
            data.writeInt(1);
            data.writeTypedArray(intents, 0);
            data.writeStringArray(resolvedTypes);
        } else {
            data.writeInt(0);
        }
        data.writeInt(flags);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(userId);
        mRemote.transact(GET_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        IIntentSender res = IIntentSender.Stub.asInterface(
            reply.readStrongBinder());
        data.recycle();
        reply.recycle();
        return res;
    }
    public void cancelIntentSender(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(CANCEL_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public String getPackageForIntentSender(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(GET_PACKAGE_FOR_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        String res = reply.readString();
        data.recycle();
        reply.recycle();
        return res;
    }
    public int getUidForIntentSender(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(GET_UID_FOR_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public int handleIncomingUser(int callingPid, int callingUid, int userId, boolean allowAll,
            boolean requireFull, String name, String callerPackage) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(callingPid);
        data.writeInt(callingUid);
        data.writeInt(userId);
        data.writeInt(allowAll ? 1 : 0);
        data.writeInt(requireFull ? 1 : 0);
        data.writeString(name);
        data.writeString(callerPackage);
        mRemote.transact(HANDLE_INCOMING_USER_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public void setProcessLimit(int max) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(max);
        mRemote.transact(SET_PROCESS_LIMIT_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int getProcessLimit() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_PROCESS_LIMIT_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public void setProcessForeground(IBinder token, int pid,
            boolean isForeground) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(pid);
        data.writeInt(isForeground ? 1 : 0);
        mRemote.transact(SET_PROCESS_FOREGROUND_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public int checkPermission(String permission, int pid, int uid)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(permission);
        data.writeInt(pid);
        data.writeInt(uid);
        mRemote.transact(CHECK_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public boolean clearApplicationUserData(final String packageName,
            final IPackageDataObserver observer, final int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeStrongBinder((observer != null) ? observer.asBinder() : null);
        data.writeInt(userId);
        mRemote.transact(CLEAR_APP_DATA_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public int checkUriPermission(Uri uri, int pid, int uid, int mode) 
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        uri.writeToParcel(data, 0);
        data.writeInt(pid);
        data.writeInt(uid);
        data.writeInt(mode);
        mRemote.transact(CHECK_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }
    public void grantUriPermission(IApplicationThread caller, String targetPkg,
            Uri uri, int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller.asBinder());
        data.writeString(targetPkg);
        uri.writeToParcel(data, 0);
        data.writeInt(mode);
        mRemote.transact(GRANT_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void revokeUriPermission(IApplicationThread caller, Uri uri,
            int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller.asBinder());
        uri.writeToParcel(data, 0);
        data.writeInt(mode);
        mRemote.transact(REVOKE_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    @Override
    public void takePersistableUriPermission(Uri uri, int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        uri.writeToParcel(data, 0);
        data.writeInt(mode);
        mRemote.transact(TAKE_PERSISTABLE_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    @Override
    public void releasePersistableUriPermission(Uri uri, int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        uri.writeToParcel(data, 0);
        data.writeInt(mode);
        mRemote.transact(RELEASE_PERSISTABLE_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    @Override
    public ParceledListSlice<UriPermission> getPersistedUriPermissions(
            String packageName, boolean incoming) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(incoming ? 1 : 0);
        mRemote.transact(GET_PERSISTED_URI_PERMISSIONS_TRANSACTION, data, reply, 0);
        reply.readException();
        final ParceledListSlice<UriPermission> perms = ParceledListSlice.CREATOR.createFromParcel(
                reply);
        data.recycle();
        reply.recycle();
        return perms;
    }

    public void showWaitingForDebugger(IApplicationThread who, boolean waiting)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(who.asBinder());
        data.writeInt(waiting ? 1 : 0);
        mRemote.transact(SHOW_WAITING_FOR_DEBUGGER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void getMemoryInfo(ActivityManager.MemoryInfo outInfo) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_MEMORY_INFO_TRANSACTION, data, reply, 0);
        reply.readException();
        outInfo.readFromParcel(reply);
        data.recycle();
        reply.recycle();
    }
    public void unhandledBack() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(UNHANDLED_BACK_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public ParcelFileDescriptor openContentUri(Uri uri) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(OPEN_CONTENT_URI_TRANSACTION, data, reply, 0);
        reply.readException();
        ParcelFileDescriptor pfd = null;
        if (reply.readInt() != 0) {
            pfd = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
        }
        data.recycle();
        reply.recycle();
        return pfd;
    }
    public void goingToSleep() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GOING_TO_SLEEP_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void wakingUp() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(WAKING_UP_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void setLockScreenShown(boolean shown) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(shown ? 1 : 0);
        mRemote.transact(SET_LOCK_SCREEN_SHOWN_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void setDebugApp(
        String packageName, boolean waitForDebugger, boolean persistent)
        throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(waitForDebugger ? 1 : 0);
        data.writeInt(persistent ? 1 : 0);
        mRemote.transact(SET_DEBUG_APP_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void setAlwaysFinish(boolean enabled) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(enabled ? 1 : 0);
        mRemote.transact(SET_ALWAYS_FINISH_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void setActivityController(IActivityController watcher) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(watcher != null ? watcher.asBinder() : null);
        mRemote.transact(SET_ACTIVITY_CONTROLLER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public void enterSafeMode() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(ENTER_SAFE_MODE_TRANSACTION, data, null, 0);
        data.recycle();
    }
    public void noteWakeupAlarm(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeStrongBinder(sender.asBinder());
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(NOTE_WAKEUP_ALARM_TRANSACTION, data, null, 0);
        data.recycle();
    }
    public boolean killPids(int[] pids, String reason, boolean secure) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeIntArray(pids);
        data.writeString(reason);
        data.writeInt(secure ? 1 : 0);
        mRemote.transact(KILL_PIDS_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    @Override
    public boolean killProcessesBelowForeground(String reason) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(reason);
        mRemote.transact(KILL_PROCESSES_BELOW_FOREGROUND_TRANSACTION, data, reply, 0);
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }
    public void startRunning(String pkg, String cls, String action,
            String indata) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(pkg);
        data.writeString(cls);
        data.writeString(action);
        data.writeString(indata);
        mRemote.transact(START_RUNNING_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    public boolean testIsSystemReady()
    {
        /* this base class version is never called */
        return true;
    }
    public void handleApplicationCrash(IBinder app,
            ApplicationErrorReport.CrashInfo crashInfo) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(app);
        crashInfo.writeToParcel(data, 0);
        mRemote.transact(HANDLE_APPLICATION_CRASH_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public boolean handleApplicationWtf(IBinder app, String tag,
            ApplicationErrorReport.CrashInfo crashInfo) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(app);
        data.writeString(tag);
        crashInfo.writeToParcel(data, 0);
        mRemote.transact(HANDLE_APPLICATION_WTF_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return res;
    }

    public void handleApplicationStrictModeViolation(IBinder app,
            int violationMask,
            StrictMode.ViolationInfo info) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(app);
        data.writeInt(violationMask);
        info.writeToParcel(data, 0);
        mRemote.transact(HANDLE_APPLICATION_STRICT_MODE_VIOLATION_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public void signalPersistentProcesses(int sig) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(sig);
        mRemote.transact(SIGNAL_PERSISTENT_PROCESSES_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void killBackgroundProcesses(String packageName, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(userId);
        mRemote.transact(KILL_BACKGROUND_PROCESSES_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void killAllBackgroundProcesses() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(KILL_ALL_BACKGROUND_PROCESSES_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void forceStopPackage(String packageName, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(userId);
        mRemote.transact(FORCE_STOP_PACKAGE_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    
    public void getMyMemoryState(ActivityManager.RunningAppProcessInfo outInfo)
            throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_MY_MEMORY_STATE_TRANSACTION, data, reply, 0);
        reply.readException();
        outInfo.readFromParcel(reply);
        reply.recycle();
        data.recycle();
    }

    public ConfigurationInfo getDeviceConfigurationInfo() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_DEVICE_CONFIGURATION_TRANSACTION, data, reply, 0);
        reply.readException();
        ConfigurationInfo res = ConfigurationInfo.CREATOR.createFromParcel(reply);
        reply.recycle();
        data.recycle();
        return res;
    }
    
    public boolean profileControl(String process, int userId, boolean start,
            String path, ParcelFileDescriptor fd, int profileType) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(process);
        data.writeInt(userId);
        data.writeInt(start ? 1 : 0);
        data.writeInt(profileType);
        data.writeString(path);
        if (fd != null) {
            data.writeInt(1);
            fd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(PROFILE_CONTROL_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return res;
    }
    
    public boolean shutdown(int timeout) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(timeout);
        mRemote.transact(SHUTDOWN_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return res;
    }
    
    public void stopAppSwitches() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(STOP_APP_SWITCHES_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }
    
    public void resumeAppSwitches() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(RESUME_APP_SWITCHES_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }
    
    public void killApplicationWithAppId(String pkg, int appid, String reason)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(pkg);
        data.writeInt(appid);
        data.writeString(reason);
        mRemote.transact(KILL_APPLICATION_WITH_APPID_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    
    public void closeSystemDialogs(String reason) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(reason);
        mRemote.transact(CLOSE_SYSTEM_DIALOGS_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    
    public Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeIntArray(pids);
        mRemote.transact(GET_PROCESS_MEMORY_INFO_TRANSACTION, data, reply, 0);
        reply.readException();
        Debug.MemoryInfo[] res = reply.createTypedArray(Debug.MemoryInfo.CREATOR);
        data.recycle();
        reply.recycle();
        return res;
    }

    public void killApplicationProcess(String processName, int uid) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(processName);
        data.writeInt(uid);
        mRemote.transact(KILL_APPLICATION_PROCESS_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
        
    public void overridePendingTransition(IBinder token, String packageName,
            int enterAnim, int exitAnim) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeString(packageName);
        data.writeInt(enterAnim);
        data.writeInt(exitAnim);
        mRemote.transact(OVERRIDE_PENDING_TRANSITION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }
    
    public boolean isUserAMonkey() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(IS_USER_A_MONKEY_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public void setUserIsMonkey(boolean monkey) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(monkey ? 1 : 0);
        mRemote.transact(SET_USER_IS_MONKEY_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void finishHeavyWeightApp() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(FINISH_HEAVY_WEIGHT_APP_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public boolean convertFromTranslucent(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(CONVERT_FROM_TRANSLUCENT_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public boolean convertToTranslucent(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(CONVERT_TO_TRANSLUCENT_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public void setImmersive(IBinder token, boolean immersive)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(immersive ? 1 : 0);
        mRemote.transact(SET_IMMERSIVE_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public boolean isImmersive(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(IS_IMMERSIVE_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() == 1;
        data.recycle();
        reply.recycle();
        return res;
    }

    public boolean isTopActivityImmersive()
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(IS_TOP_ACTIVITY_IMMERSIVE_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() == 1;
        data.recycle();
        reply.recycle();
        return res;
    }

    public void crashApplication(int uid, int initialPid, String packageName,
            String message) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(uid);
        data.writeInt(initialPid);
        data.writeString(packageName);
        data.writeString(message);
        mRemote.transact(CRASH_APPLICATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public String getProviderMimeType(Uri uri, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        uri.writeToParcel(data, 0);
        data.writeInt(userId);
        mRemote.transact(GET_PROVIDER_MIME_TYPE_TRANSACTION, data, reply, 0);
        reply.readException();
        String res = reply.readString();
        data.recycle();
        reply.recycle();
        return res;
    }

    public IBinder newUriPermissionOwner(String name)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(name);
        mRemote.transact(NEW_URI_PERMISSION_OWNER_TRANSACTION, data, reply, 0);
        reply.readException();
        IBinder res = reply.readStrongBinder();
        data.recycle();
        reply.recycle();
        return res;
    }

    public void grantUriPermissionFromOwner(IBinder owner, int fromUid, String targetPkg,
            Uri uri, int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(owner);
        data.writeInt(fromUid);
        data.writeString(targetPkg);
        uri.writeToParcel(data, 0);
        data.writeInt(mode);
        mRemote.transact(GRANT_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void revokeUriPermissionFromOwner(IBinder owner, Uri uri,
            int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(owner);
        if (uri != null) {
            data.writeInt(1);
            uri.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(mode);
        mRemote.transact(REVOKE_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public int checkGrantUriPermission(int callingUid, String targetPkg,
            Uri uri, int modeFlags) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(callingUid);
        data.writeString(targetPkg);
        uri.writeToParcel(data, 0);
        data.writeInt(modeFlags);
        mRemote.transact(CHECK_GRANT_URI_PERMISSION_TRANSACTION, data, reply, 0);
        reply.readException();
        int res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }

    public boolean dumpHeap(String process, int userId, boolean managed,
            String path, ParcelFileDescriptor fd) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(process);
        data.writeInt(userId);
        data.writeInt(managed ? 1 : 0);
        data.writeString(path);
        if (fd != null) {
            data.writeInt(1);
            fd.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(DUMP_HEAP_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return res;
    }
    
    public int startActivities(IApplicationThread caller, String callingPackage,
            Intent[] intents, String[] resolvedTypes, IBinder resultTo,
            Bundle options, int userId) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        data.writeTypedArray(intents, 0);
        data.writeStringArray(resolvedTypes);
        data.writeStrongBinder(resultTo);
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        data.writeInt(userId);
        mRemote.transact(START_ACTIVITIES_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }

    public int getFrontActivityScreenCompatMode() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION, data, reply, 0);
        reply.readException();
        int mode = reply.readInt();
        reply.recycle();
        data.recycle();
        return mode;
    }

    public void setFrontActivityScreenCompatMode(int mode) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(mode);
        mRemote.transact(SET_FRONT_ACTIVITY_SCREEN_COMPAT_MODE_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public int getPackageScreenCompatMode(String packageName) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        mRemote.transact(GET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION, data, reply, 0);
        reply.readException();
        int mode = reply.readInt();
        reply.recycle();
        data.recycle();
        return mode;
    }

    public void setPackageScreenCompatMode(String packageName, int mode)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(mode);
        mRemote.transact(SET_PACKAGE_SCREEN_COMPAT_MODE_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public boolean getPackageAskScreenCompat(String packageName) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        mRemote.transact(GET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean ask = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return ask;
    }

    public void setPackageAskScreenCompat(String packageName, boolean ask)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeString(packageName);
        data.writeInt(ask ? 1 : 0);
        mRemote.transact(SET_PACKAGE_ASK_SCREEN_COMPAT_TRANSACTION, data, reply, 0);
        reply.readException();
        reply.recycle();
        data.recycle();
    }

    public boolean switchUser(int userid) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(userid);
        mRemote.transact(SWITCH_USER_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return result;
    }

    public int stopUser(int userid, IStopUserCallback callback) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(userid);
        data.writeStrongInterface(callback);
        mRemote.transact(STOP_USER_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }

    public UserInfo getCurrentUser() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_CURRENT_USER_TRANSACTION, data, reply, 0);
        reply.readException();
        UserInfo userInfo = UserInfo.CREATOR.createFromParcel(reply);
        reply.recycle();
        data.recycle();
        return userInfo;
    }

    public boolean isUserRunning(int userid, boolean orStopping) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(userid);
        data.writeInt(orStopping ? 1 : 0);
        mRemote.transact(IS_USER_RUNNING_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return result;
    }

    public int[] getRunningUserIds() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(GET_RUNNING_USER_IDS_TRANSACTION, data, reply, 0);
        reply.readException();
        int[] result = reply.createIntArray();
        reply.recycle();
        data.recycle();
        return result;
    }

    public boolean removeSubTask(int taskId, int subTaskIndex) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(taskId);
        data.writeInt(subTaskIndex);
        mRemote.transact(REMOVE_SUB_TASK_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return result;
    }

    public boolean removeTask(int taskId, int flags) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(taskId);
        data.writeInt(flags);
        mRemote.transact(REMOVE_TASK_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        reply.recycle();
        data.recycle();
        return result;
    }

    public void registerProcessObserver(IProcessObserver observer) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(observer != null ? observer.asBinder() : null);
        mRemote.transact(REGISTER_PROCESS_OBSERVER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void unregisterProcessObserver(IProcessObserver observer) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(observer != null ? observer.asBinder() : null);
        mRemote.transact(UNREGISTER_PROCESS_OBSERVER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public boolean isIntentSenderTargetedToPackage(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(IS_INTENT_SENDER_TARGETED_TO_PACKAGE_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public boolean isIntentSenderAnActivity(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(IS_INTENT_SENDER_AN_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean res = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return res;
    }

    public Intent getIntentForIntentSender(IIntentSender sender) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(sender.asBinder());
        mRemote.transact(GET_INTENT_FOR_INTENT_SENDER_TRANSACTION, data, reply, 0);
        reply.readException();
        Intent res = reply.readInt() != 0
                ? Intent.CREATOR.createFromParcel(reply) : null;
        data.recycle();
        reply.recycle();
        return res;
    }

    public void updatePersistentConfiguration(Configuration values) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        values.writeToParcel(data, 0);
        mRemote.transact(UPDATE_PERSISTENT_CONFIGURATION_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public long[] getProcessPss(int[] pids) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeIntArray(pids);
        mRemote.transact(GET_PROCESS_PSS_TRANSACTION, data, reply, 0);
        reply.readException();
        long[] res = reply.createLongArray();
        data.recycle();
        reply.recycle();
        return res;
    }

    public void showBootMessage(CharSequence msg, boolean always) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        TextUtils.writeToParcel(msg, data, 0);
        data.writeInt(always ? 1 : 0);
        mRemote.transact(SHOW_BOOT_MESSAGE_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void dismissKeyguardOnNextActivity() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(DISMISS_KEYGUARD_ON_NEXT_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public boolean targetTaskAffinityMatchesActivity(IBinder token, String destAffinity)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeString(destAffinity);
        mRemote.transact(TARGET_TASK_AFFINITY_MATCHES_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return result;
    }

    public boolean navigateUpTo(IBinder token, Intent target, int resultCode, Intent resultData)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        target.writeToParcel(data, 0);
        data.writeInt(resultCode);
        if (resultData != null) {
            data.writeInt(1);
            resultData.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        mRemote.transact(NAVIGATE_UP_TO_TRANSACTION, data, reply, 0);
        reply.readException();
        boolean result = reply.readInt() != 0;
        data.recycle();
        reply.recycle();
        return result;
    }

    public int getLaunchedFromUid(IBinder activityToken) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(activityToken);
        mRemote.transact(GET_LAUNCHED_FROM_UID_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        data.recycle();
        reply.recycle();
        return result;
    }

    public String getLaunchedFromPackage(IBinder activityToken) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(activityToken);
        mRemote.transact(GET_LAUNCHED_FROM_PACKAGE_TRANSACTION, data, reply, 0);
        reply.readException();
        String result = reply.readString();
        data.recycle();
        reply.recycle();
        return result;
    }

    public void registerUserSwitchObserver(IUserSwitchObserver observer) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(observer != null ? observer.asBinder() : null);
        mRemote.transact(REGISTER_USER_SWITCH_OBSERVER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void unregisterUserSwitchObserver(IUserSwitchObserver observer) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(observer != null ? observer.asBinder() : null);
        mRemote.transact(UNREGISTER_USER_SWITCH_OBSERVER_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void requestBugReport() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(REQUEST_BUG_REPORT_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public long inputDispatchingTimedOut(int pid, boolean aboveSystem, String reason)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(pid);
        data.writeInt(aboveSystem ? 1 : 0);
        data.writeString(reason);
        mRemote.transact(INPUT_DISPATCHING_TIMED_OUT_TRANSACTION, data, reply, 0);
        reply.readException();
        long res = reply.readInt();
        data.recycle();
        reply.recycle();
        return res;
    }

    public Bundle getAssistContextExtras(int requestType) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(requestType);
        mRemote.transact(GET_ASSIST_CONTEXT_EXTRAS_TRANSACTION, data, reply, 0);
        reply.readException();
        Bundle res = reply.readBundle();
        data.recycle();
        reply.recycle();
        return res;
    }

    public void reportAssistContextExtras(IBinder token, Bundle extras)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        data.writeBundle(extras);
        mRemote.transact(REPORT_ASSIST_CONTEXT_EXTRAS_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void killUid(int uid, String reason) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeInt(uid);
        data.writeString(reason);
        mRemote.transact(KILL_UID_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void hang(IBinder who, boolean allowRestart) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(who);
        data.writeInt(allowRestart ? 1 : 0);
        mRemote.transact(HANG_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void reportActivityFullyDrawn(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(REPORT_ACTIVITY_FULLY_DRAWN_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void notifyActivityDrawn(IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(NOTIFY_ACTIVITY_DRAWN_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void restart() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(RESTART_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void performIdleMaintenance() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(PERFORM_IDLE_MAINTENANCE_TRANSACTION, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    public void notifySplitViewLayoutChanged() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        mRemote.transact(NOTIFY_SPLIT_VIEW_LAYOUT_CHANGED, data, reply, 0);
        reply.readException();
        data.recycle();
        reply.recycle();
    }

    private IBinder mRemote;
}
