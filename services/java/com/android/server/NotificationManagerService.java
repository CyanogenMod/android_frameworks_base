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

package com.android.server;

import com.android.internal.statusbar.StatusBarNotification;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.ITransientNotification;
import android.app.Notification;
import android.app.NotificationGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Profile;
import android.app.ProfileGroup;
import android.app.ProfileManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.android.internal.app.ThemeUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Map;

/** {@hide} */
public class NotificationManagerService extends INotificationManager.Stub
{
    private static final String TAG = "NotificationService";
    private static final boolean DBG = false;

    private static final int MAX_PACKAGE_NOTIFICATIONS = 50;

    // message codes
    private static final int MESSAGE_TIMEOUT = 2;

    private static final int LONG_DELAY = 3500; // 3.5 seconds
    private static final int SHORT_DELAY = 2000; // 2 seconds

    private static final long[] DEFAULT_VIBRATE_PATTERN = {0, 250, 250, 250};

    private static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_NOTIFICATION;

    final Context mContext;
    Context mUiContext;
    final IActivityManager mAm;
    final IBinder mForegroundToken = new Binder();

    private WorkerHandler mHandler;
    private StatusBarManagerService mStatusBar;
    private LightsService.Light mNotificationLight;
    private LightsService.Light mAttentionLight;

    private int mDefaultNotificationColor;
    private int mDefaultNotificationLedOn;
    private int mDefaultNotificationLedOff;

    private NotificationRecord mSoundNotification;
    private NotificationPlayer mSound;
    private boolean mSystemReady;
    private int mDisabledNotifications;

    private NotificationRecord mVibrateNotification;
    private Vibrator mVibrator = new Vibrator();

    // for enabling and disabling notification pulse behaviour
    private boolean mScreenOn = true;
    private boolean mWasScreenOn = false;
    private boolean mInCall = false;
    private boolean mNotificationPulseEnabled;
    private HashMap<String, NotificationLedValues> mNotificationPulseCustomLedValues;
    private Map<String, String> mPackageNameMappings;

    private final ArrayList<NotificationRecord> mNotificationList =
            new ArrayList<NotificationRecord>();

    private ArrayList<ToastRecord> mToastQueue;

    private ArrayList<NotificationRecord> mLights = new ArrayList<NotificationRecord>();
    private NotificationRecord mLedNotification;

    private boolean mQuietHoursEnabled = false;
    // Minutes from midnight when quiet hours begin.
    private int mQuietHoursStart = 0;
    // Minutes from midnight when quiet hours end.
    private int mQuietHoursEnd = 0;
    // Don't play sounds.
    private boolean mQuietHoursMute = true;
    // Don't vibrate.
    private boolean mQuietHoursStill = true;
    // Dim LED if hardware supports it.
    private boolean mQuietHoursDim = true;

    private static String idDebugString(Context baseContext, String packageName, int id) {
        Context c = null;

        if (packageName != null) {
            try {
                c = baseContext.createPackageContext(packageName, 0);
            } catch (NameNotFoundException e) {
                c = baseContext;
            }
        } else {
            c = baseContext;
        }

        String pkg;
        String type;
        String name;

        Resources r = c.getResources();
        try {
            return r.getResourceName(id);
        } catch (Resources.NotFoundException e) {
            return "<name unknown>";
        }
    }

    private static final class NotificationRecord
    {
        final String pkg;
        final String tag;
        final int id;
        final int uid;
        final int initialPid;
        final int priority;
        final Notification notification;
        IBinder statusBarKey;

        NotificationRecord(String pkg, String tag, int id, int uid, int initialPid, int priority,
                Notification notification)
        {
            this.pkg = pkg;
            this.tag = tag;
            this.id = id;
            this.uid = uid;
            this.initialPid = initialPid;
            this.priority = priority;
            this.notification = notification;
        }

        void dump(PrintWriter pw, String prefix, Context baseContext) {
            pw.println(prefix + this);
            pw.println(prefix + "  icon=0x" + Integer.toHexString(notification.icon)
                    + " / " + idDebugString(baseContext, this.pkg, notification.icon));
            pw.println(prefix + "  contentIntent=" + notification.contentIntent);
            pw.println(prefix + "  deleteIntent=" + notification.deleteIntent);
            pw.println(prefix + "  tickerText=" + notification.tickerText);
            pw.println(prefix + "  contentView=" + notification.contentView);
            pw.println(prefix + "  defaults=0x" + Integer.toHexString(notification.defaults));
            pw.println(prefix + "  flags=0x" + Integer.toHexString(notification.flags));
            pw.println(prefix + "  sound=" + notification.sound);
            pw.println(prefix + "  vibrate=" + Arrays.toString(notification.vibrate));
            pw.println(prefix + "  ledARGB=0x" + Integer.toHexString(notification.ledARGB)
                    + " ledOnMS=" + notification.ledOnMS
                    + " ledOffMS=" + notification.ledOffMS);
        }

        @Override
        public final String toString()
        {
            return "NotificationRecord{"
                + Integer.toHexString(System.identityHashCode(this))
                + " pkg=" + pkg
                + " id=" + Integer.toHexString(id)
                + " tag=" + tag 
                + " pri=" + priority 
                + "}";
        }
    }

    private static final class ToastRecord
    {
        final int pid;
        final String pkg;
        final ITransientNotification callback;
        int duration;

        ToastRecord(int pid, String pkg, ITransientNotification callback, int duration)
        {
            this.pid = pid;
            this.pkg = pkg;
            this.callback = callback;
            this.duration = duration;
        }

        void update(int duration) {
            this.duration = duration;
        }

        void dump(PrintWriter pw, String prefix) {
            pw.println(prefix + this);
        }

        @Override
        public final String toString()
        {
            return "ToastRecord{"
                + Integer.toHexString(System.identityHashCode(this))
                + " pkg=" + pkg
                + " callback=" + callback
                + " duration=" + duration;
        }
    }

    class NotificationLedValues {
        public int color;
        public int onMS;
        public int offMS;
    }

    private StatusBarManagerService.NotificationCallbacks mNotificationCallbacks
            = new StatusBarManagerService.NotificationCallbacks() {

        public void onSetDisabled(int status) {
            synchronized (mNotificationList) {
                mDisabledNotifications = status;
                if ((mDisabledNotifications & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) {
                    // cancel whatever's going on
                    long identity = Binder.clearCallingIdentity();
                    try {
                        mSound.stop();
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }

                    identity = Binder.clearCallingIdentity();
                    try {
                        mVibrator.cancel();
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }
        }

        public void onClearAll() {
            cancelAll();
        }

        public void onNotificationClick(String pkg, String tag, int id) {
            cancelNotification(pkg, tag, id, Notification.FLAG_AUTO_CANCEL,
                    Notification.FLAG_FOREGROUND_SERVICE, false);
        }

        public void onNotificationClear(String pkg, String tag, int id) {
            cancelNotification(pkg, tag, id, 0,
                Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE,
                true);
        }

        public void onPanelRevealed() {
            synchronized (mNotificationList) {
                // sound
                mSoundNotification = null;
                long identity = Binder.clearCallingIdentity();
                try {
                    mSound.stop();
                }
                finally {
                    Binder.restoreCallingIdentity(identity);
                }

                // vibrate
                mVibrateNotification = null;
                identity = Binder.clearCallingIdentity();
                try {
                    mVibrator.cancel();
                }
                finally {
                    Binder.restoreCallingIdentity(identity);
                }

                // light
                mLights.clear();
                mLedNotification = null;
                updateLightsLocked();
            }
        }

        public void onNotificationError(String pkg, String tag, int id,
                int uid, int initialPid, String message) {
            Slog.d(TAG, "onNotification error pkg=" + pkg + " tag=" + tag + " id=" + id
                    + "; will crashApplication(uid=" + uid + ", pid=" + initialPid + ")");
            cancelNotification(pkg, tag, id, 0, 0, false);
            long ident = Binder.clearCallingIdentity();
            try {
                ActivityManagerNative.getDefault().crashApplication(uid, initialPid, pkg,
                        "Bad notification posted from package " + pkg
                        + ": " + message);
            } catch (RemoteException e) {
            }
            Binder.restoreCallingIdentity(ident);
        }
    };

    private BroadcastReceiver mThemeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mUiContext = null;
        }
    };

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            boolean queryRestart = false;
            
            if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_RESTARTED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)
                    || (queryRestart=action.equals(Intent.ACTION_QUERY_PACKAGE_RESTART))
                    || action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)) {
                String pkgList[] = null;
                if (action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                } else if (queryRestart) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                } else {
                    Uri uri = intent.getData();
                    if (uri == null) {
                        return;
                    }
                    String pkgName = uri.getSchemeSpecificPart();
                    if (pkgName == null) {
                        return;
                    }
                    pkgList = new String[]{pkgName};
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkgName : pkgList) {
                        cancelAllNotificationsInt(pkgName, 0, 0, !queryRestart);
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                // Keep track of screen on/off state, but do not turn off the notification light
                // until user passes through the lock screen or views the notification.
                mScreenOn = true;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                mWasScreenOn = true;
                updateLightsLocked();
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mInCall = (intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(
                        TelephonyManager.EXTRA_STATE_OFFHOOK));
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                // turn off LED when user passes through lock screen
                mNotificationLight.turnOff();
            }
        }
    };

    class LEDSettingsObserver extends ContentObserver {
        LEDSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES), false, this);
            update();
        }

        @Override public void onChange(boolean selfChange) {
            update();
            updateNotificationPulse();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            // LED enabled
            mNotificationPulseEnabled = Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE, 0) != 0;

            // LED default color
            mDefaultNotificationColor = Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR, mDefaultNotificationColor);

            // LED default on MS
            mDefaultNotificationLedOn = Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON, mDefaultNotificationLedOn);

            // LED default off MS
            mDefaultNotificationLedOff = Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF, mDefaultNotificationLedOff);

            // LED custom notification colors
            mNotificationPulseCustomLedValues.clear();
            if (Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE, 0) != 0) {
                parseNotificationPulseCustomValuesString(Settings.System.getString(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES));
            }
        }
    }

    class QuietHoursSettingsObserver extends ContentObserver {
        QuietHoursSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_ENABLED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_START), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_END), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_MUTE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_STILL), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUIET_HOURS_DIM), false, this);
            update();
        }

        @Override public void onChange(boolean selfChange) {
            update();
            updateNotificationPulse();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mQuietHoursEnabled = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_ENABLED, 0) != 0;
            mQuietHoursStart = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_START, 0);
            mQuietHoursEnd = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_END, 0);
            mQuietHoursMute = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_MUTE, 0) != 0;
            mQuietHoursStill = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_STILL, 0) != 0;
            mQuietHoursDim = Settings.System.getInt(resolver,
                    Settings.System.QUIET_HOURS_DIM, 0) != 0;
        }
    }

    NotificationManagerService(Context context, StatusBarManagerService statusBar,
            LightsService lights)
    {
        super();
        mContext = context;
        mAm = ActivityManagerNative.getDefault();
        mSound = new NotificationPlayer(TAG);
        mSound.setUsesWakeLock(context);
        mToastQueue = new ArrayList<ToastRecord>();
        mHandler = new WorkerHandler();

        mStatusBar = statusBar;
        statusBar.setNotificationCallbacks(mNotificationCallbacks);

        mNotificationLight = lights.getLight(LightsService.LIGHT_ID_NOTIFICATIONS);
        mAttentionLight = lights.getLight(LightsService.LIGHT_ID_ATTENTION);

        Resources resources = mContext.getResources();
        mDefaultNotificationColor = resources.getColor(
                com.android.internal.R.color.config_defaultNotificationColor);
        mDefaultNotificationLedOn = resources.getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOn);
        mDefaultNotificationLedOff = resources.getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOff);

        mNotificationPulseCustomLedValues = new HashMap<String, NotificationLedValues>();

        mPackageNameMappings = new HashMap<String, String>();
        for(String mapping : resources.getStringArray(
                com.android.internal.R.array.notification_light_package_mapping)) {
            String[] map = mapping.split("\\|");
            mPackageNameMappings.put(map[0], map[1]);
        }

        // Don't start allowing notifications until the setup wizard has run once.
        // After that, including subsequent boots, init with notifications turned on.
        // This works on the first boot because the setup wizard will toggle this
        // flag at least once and we'll go back to 0 after that.
        if (0 == Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.DEVICE_PROVISIONED, 0)) {
            mDisabledNotifications = StatusBarManager.DISABLE_NOTIFICATION_ALERTS;
        }

        // register for various Intents
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(mIntentReceiver, filter);
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        pkgFilter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
        pkgFilter.addDataScheme("package");
        mContext.registerReceiver(mIntentReceiver, pkgFilter);
        IntentFilter sdFilter = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(mIntentReceiver, sdFilter);

        LEDSettingsObserver ledObserver = new LEDSettingsObserver(mHandler);
        ledObserver.observe();
        QuietHoursSettingsObserver qhObserver = new QuietHoursSettingsObserver(mHandler);
        qhObserver.observe();
        ThemeUtils.registerThemeChangeReceiver(mContext, mThemeChangeReceiver);
    }

    void systemReady() {
        // no beeping until we're basically done booting
        mSystemReady = true;
    }

    // Toasts
    // ============================================================================
    public void enqueueToast(String pkg, ITransientNotification callback, int duration)
    {
        if (DBG) Slog.i(TAG, "enqueueToast pkg=" + pkg + " callback=" + callback + " duration=" + duration);

        if (pkg == null || callback == null) {
            Slog.e(TAG, "Not doing toast. pkg=" + pkg + " callback=" + callback);
            return ;
        }

        synchronized (mToastQueue) {
            int callingPid = Binder.getCallingPid();
            long callingId = Binder.clearCallingIdentity();
            try {
                ToastRecord record;
                int index = indexOfToastLocked(pkg, callback);
                // If it's already in the queue, we update it in place, we don't
                // move it to the end of the queue.
                if (index >= 0) {
                    record = mToastQueue.get(index);
                    record.update(duration);
                } else {
                    // Limit the number of toasts that any given package except the android
                    // package can enqueue.  Prevents DOS attacks and deals with leaks.
                    if (!"android".equals(pkg)) {
                        int count = 0;
                        final int N = mToastQueue.size();
                        for (int i=0; i<N; i++) {
                             final ToastRecord r = mToastQueue.get(i);
                             if (r.pkg.equals(pkg)) {
                                 count++;
                                 if (count >= MAX_PACKAGE_NOTIFICATIONS) {
                                     Slog.e(TAG, "Package has already posted " + count
                                            + " toasts. Not showing more. Package=" + pkg);
                                     return;
                                 }
                             }
                        }
                    }

                    record = new ToastRecord(callingPid, pkg, callback, duration);
                    mToastQueue.add(record);
                    index = mToastQueue.size() - 1;
                    keepProcessAliveLocked(callingPid);
                }
                // If it's at index 0, it's the current toast.  It doesn't matter if it's
                // new or just been updated.  Call back and tell it to show itself.
                // If the callback fails, this will remove it from the list, so don't
                // assume that it's valid after this.
                if (index == 0) {
                    showNextToastLocked();
                }
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }
    }

    public void cancelToast(String pkg, ITransientNotification callback) {
        Slog.i(TAG, "cancelToast pkg=" + pkg + " callback=" + callback);

        if (pkg == null || callback == null) {
            Slog.e(TAG, "Not cancelling notification. pkg=" + pkg + " callback=" + callback);
            return ;
        }

        synchronized (mToastQueue) {
            long callingId = Binder.clearCallingIdentity();
            try {
                int index = indexOfToastLocked(pkg, callback);
                if (index >= 0) {
                    cancelToastLocked(index);
                } else {
                    Slog.w(TAG, "Toast already cancelled. pkg=" + pkg + " callback=" + callback);
                }
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }
    }

    private void showNextToastLocked() {
        ToastRecord record = mToastQueue.get(0);
        while (record != null) {
            if (DBG) Slog.d(TAG, "Show pkg=" + record.pkg + " callback=" + record.callback);
            try {
                record.callback.show();
                scheduleTimeoutLocked(record, false);
                return;
            } catch (RemoteException e) {
                Slog.w(TAG, "Object died trying to show notification " + record.callback
                        + " in package " + record.pkg);
                // remove it from the list and let the process die
                int index = mToastQueue.indexOf(record);
                if (index >= 0) {
                    mToastQueue.remove(index);
                }
                keepProcessAliveLocked(record.pid);
                if (mToastQueue.size() > 0) {
                    record = mToastQueue.get(0);
                } else {
                    record = null;
                }
            }
        }
    }

    private void cancelToastLocked(int index) {
        ToastRecord record = mToastQueue.get(index);
        try {
            record.callback.hide();
        } catch (RemoteException e) {
            Slog.w(TAG, "Object died trying to hide notification " + record.callback
                    + " in package " + record.pkg);
            // don't worry about this, we're about to remove it from
            // the list anyway
        }
        mToastQueue.remove(index);
        keepProcessAliveLocked(record.pid);
        if (mToastQueue.size() > 0) {
            // Show the next one. If the callback fails, this will remove
            // it from the list, so don't assume that the list hasn't changed
            // after this point.
            showNextToastLocked();
        }
    }

    private void scheduleTimeoutLocked(ToastRecord r, boolean immediate)
    {
        Message m = Message.obtain(mHandler, MESSAGE_TIMEOUT, r);
        long delay = immediate ? 0 : (r.duration == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY);
        mHandler.removeCallbacksAndMessages(r);
        mHandler.sendMessageDelayed(m, delay);
    }

    private void handleTimeout(ToastRecord record)
    {
        if (DBG) Slog.d(TAG, "Timeout pkg=" + record.pkg + " callback=" + record.callback);
        synchronized (mToastQueue) {
            int index = indexOfToastLocked(record.pkg, record.callback);
            if (index >= 0) {
                cancelToastLocked(index);
            }
        }
    }

    // lock on mToastQueue
    private int indexOfToastLocked(String pkg, ITransientNotification callback)
    {
        IBinder cbak = callback.asBinder();
        ArrayList<ToastRecord> list = mToastQueue;
        int len = list.size();
        for (int i=0; i<len; i++) {
            ToastRecord r = list.get(i);
            if (r.pkg.equals(pkg) && r.callback.asBinder() == cbak) {
                return i;
            }
        }
        return -1;
    }

    // lock on mToastQueue
    private void keepProcessAliveLocked(int pid)
    {
        int toastCount = 0; // toasts from this pid
        ArrayList<ToastRecord> list = mToastQueue;
        int N = list.size();
        for (int i=0; i<N; i++) {
            ToastRecord r = list.get(i);
            if (r.pid == pid) {
                toastCount++;
            }
        }
        try {
            mAm.setProcessForeground(mForegroundToken, pid, toastCount > 0);
        } catch (RemoteException e) {
            // Shouldn't happen.
        }
    }

    private final class WorkerHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case MESSAGE_TIMEOUT:
                    handleTimeout((ToastRecord)msg.obj);
                    break;
            }
        }
    }


    // Notifications
    // ============================================================================
    @Deprecated
    public void enqueueNotification(String pkg, int id, Notification notification, int[] idOut)
    {
        enqueueNotificationWithTag(pkg, null /* tag */, id, notification, idOut);
    }

    public void enqueueNotificationWithTag(String pkg, String tag, int id, Notification notification,
            int[] idOut)
    {
        enqueueNotificationInternal(pkg, Binder.getCallingUid(), Binder.getCallingPid(),
                tag, id, notification, idOut);
    }

    public void enqueueNotificationWithTagPriority(String pkg, String tag, int id, int priority,
            Notification notification, int[] idOut)
    {
        enqueueNotificationInternal(pkg, Binder.getCallingUid(), Binder.getCallingPid(),
                tag, id, priority, notification, idOut);
    }

    // Not exposed via Binder; for system use only (otherwise malicious apps could spoof the
    // uid/pid of another application)
    public void enqueueNotificationInternal(String pkg, int callingUid, int callingPid,
            String tag, int id, Notification notification, int[] idOut)
    {
        enqueueNotificationInternal(pkg, callingUid, callingPid, tag, id, 
                ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                    ? StatusBarNotification.PRIORITY_ONGOING
                    : StatusBarNotification.PRIORITY_NORMAL,
                notification, idOut);
    }
    public void enqueueNotificationInternal(String pkg, int callingUid, int callingPid,
            String tag, int id, int priority, Notification notification, int[] idOut)
    {
        checkIncomingCall(pkg);

        // Limit the number of notifications that any given package except the android
        // package can enqueue.  Prevents DOS attacks and deals with leaks.
        if (!"android".equals(pkg)) {
            synchronized (mNotificationList) {
                int count = 0;
                final int N = mNotificationList.size();
                for (int i=0; i<N; i++) {
                    final NotificationRecord r = mNotificationList.get(i);
                    if (r.pkg.equals(pkg)) {
                        count++;
                        if (count >= MAX_PACKAGE_NOTIFICATIONS) {
                            Slog.e(TAG, "Package has already posted " + count
                                    + " notifications.  Not showing more.  package=" + pkg);
                            return;
                        }
                    }
                }
            }
        }

        // This conditional is a dirty hack to limit the logging done on
        //     behalf of the download manager without affecting other apps.
        if (!pkg.equals("com.android.providers.downloads")
                || Log.isLoggable("DownloadManager", Log.VERBOSE)) {
            EventLog.writeEvent(EventLogTags.NOTIFICATION_ENQUEUE, pkg, id, tag,
                    notification.toString());
        }

        if (pkg == null || notification == null) {
            throw new IllegalArgumentException("null not allowed: pkg=" + pkg
                    + " id=" + id + " notification=" + notification);
        }
        if (notification.icon != 0) {
            if (notification.contentView == null) {
                throw new IllegalArgumentException("contentView required: pkg=" + pkg
                        + " id=" + id + " notification=" + notification);
            }
        }

        synchronized (mNotificationList) {
            final boolean inQuietHours = inQuietHours();

            NotificationRecord r = new NotificationRecord(pkg, tag, id, 
                    callingUid, callingPid, 
                    priority,
                    notification);
            NotificationRecord old = null;

            int index = indexOfNotificationLocked(pkg, tag, id);
            if (index < 0) {
                mNotificationList.add(r);
            } else {
                old = mNotificationList.remove(index);
                mNotificationList.add(index, r);
                // Make sure we don't lose the foreground service state.
                if (old != null) {
                    notification.flags |=
                        old.notification.flags&Notification.FLAG_FOREGROUND_SERVICE;
                }
            }

            // Ensure if this is a foreground service that the proper additional
            // flags are set.
            if ((notification.flags&Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                notification.flags |= Notification.FLAG_ONGOING_EVENT
                        | Notification.FLAG_NO_CLEAR;
            }

            if (notification.icon != 0) {
                StatusBarNotification n = new StatusBarNotification(pkg, id, tag,
                        r.uid, r.initialPid, notification);
                n.priority = r.priority;

                if (old != null && old.statusBarKey != null) {
                    r.statusBarKey = old.statusBarKey;
                    long identity = Binder.clearCallingIdentity();
                    try {
                        mStatusBar.updateNotification(r.statusBarKey, n);
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                } else {
                    long identity = Binder.clearCallingIdentity();
                    try {
                        r.statusBarKey = mStatusBar.addNotification(n);
                        if ((n.notification.flags & Notification.FLAG_SHOW_LIGHTS) != 0) {
                            mAttentionLight.pulse();
                        }
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
                sendAccessibilityEvent(notification, pkg);
            } else {
                Slog.e(TAG, "Ignoring notification with icon==0: " + notification);
                if (old != null && old.statusBarKey != null) {
                    long identity = Binder.clearCallingIdentity();
                    try {
                        mStatusBar.removeNotification(old.statusBarKey);
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }

            try {
                final ProfileManager profileManager =
                        (ProfileManager) mContext.getSystemService(Context.PROFILE_SERVICE);

                ProfileGroup group = profileManager.getActiveProfileGroup(pkg);
                notification = group.processNotification(notification);
            } catch(Throwable th) {
                Log.e(TAG, "An error occurred profiling the notification.", th);
            }

            // If we're not supposed to beep, vibrate, etc. then don't.
            if (((mDisabledNotifications & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) == 0)
                    && (!(old != null
                        && (notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0 ))
                    && mSystemReady) {

                final AudioManager audioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
                // sound
                final boolean useDefaultSound =
                    (notification.defaults & Notification.DEFAULT_SOUND) != 0;
                if (!(inQuietHours && mQuietHoursMute)
                        && (useDefaultSound || notification.sound != null)) {
                    Uri uri;
                    if (useDefaultSound) {
                        uri = Settings.System.DEFAULT_NOTIFICATION_URI;
                    } else {
                        uri = notification.sound;
                    }
                    boolean looping = (notification.flags & Notification.FLAG_INSISTENT) != 0;
                    int audioStreamType;
                    if (notification.audioStreamType >= 0) {
                        audioStreamType = notification.audioStreamType;
                    } else {
                        audioStreamType = DEFAULT_STREAM_TYPE;
                    }
                    mSoundNotification = r;
                    // do not play notifications if stream volume is 0
                    // (typically because ringer mode is silent).
                    if (audioManager.getStreamVolume(audioStreamType) != 0) {
                        long identity = Binder.clearCallingIdentity();
                        try {
                            mSound.play(mContext, uri, looping, audioStreamType);
                        }
                        finally {
                            Binder.restoreCallingIdentity(identity);
                        }
                    }
                }

                // vibrate
                final boolean useDefaultVibrate =
                    (notification.defaults & Notification.DEFAULT_VIBRATE) != 0;
                if (!(inQuietHours && mQuietHoursStill)
                        && (useDefaultVibrate || notification.vibrate != null)
                        && audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_NOTIFICATION)) {
                    mVibrateNotification = r;

                    mVibrator.vibrate(useDefaultVibrate ? DEFAULT_VIBRATE_PATTERN
                                                        : notification.vibrate,
                              ((notification.flags & Notification.FLAG_INSISTENT) != 0) ? 0: -1);
                }
            }

            // this option doesn't shut off the lights

            // light
            // the most recent thing gets the light
            mLights.remove(old);
            if (mLedNotification == old) {
                mLedNotification = null;
            }
            //Slog.i(TAG, "notification.lights="
            //        + ((old.notification.lights.flags & Notification.FLAG_SHOW_LIGHTS) != 0));
            if ((notification.flags & Notification.FLAG_SHOW_LIGHTS) != 0) {
                mLights.add(r);
                updateLightsLocked();
            } else {
                if (old != null
                        && ((old.notification.flags & Notification.FLAG_SHOW_LIGHTS) != 0)) {
                    updateLightsLocked();
                }
            }
        }

        idOut[0] = id;
    }

    private boolean inQuietHours() {
        if (mQuietHoursEnabled && (mQuietHoursStart != mQuietHoursEnd)) {
            // Get the date in "quiet hours" format.
            Calendar calendar = Calendar.getInstance();
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            if (mQuietHoursEnd < mQuietHoursStart) {
                // Starts at night, ends in the morning.
                return (minutes > mQuietHoursStart) || (minutes < mQuietHoursEnd);
            } else {
                return (minutes > mQuietHoursStart) && (minutes < mQuietHoursEnd);
            }
        }
        return false;
    }

    private void sendAccessibilityEvent(Notification notification, CharSequence packageName) {
        AccessibilityManager manager = AccessibilityManager.getInstance(mContext);
        if (!manager.isEnabled()) {
            return;
        }

        AccessibilityEvent event =
            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setPackageName(packageName);
        event.setClassName(Notification.class.getName());
        event.setParcelableData(notification);
        CharSequence tickerText = notification.tickerText;
        if (!TextUtils.isEmpty(tickerText)) {
            event.getText().add(tickerText);
        }

        manager.sendAccessibilityEvent(event);
    }

    private void cancelNotificationLocked(NotificationRecord r, boolean sendDelete) {
        // tell the app
        if (sendDelete) {
            if (r.notification.deleteIntent != null) {
                try {
                    r.notification.deleteIntent.send();
                } catch (PendingIntent.CanceledException ex) {
                    // do nothing - there's no relevant way to recover, and
                    //     no reason to let this propagate
                    Slog.w(TAG, "canceled PendingIntent for " + r.pkg, ex);
                }
            }
        }

        // status bar
        if (r.notification.icon != 0) {
            long identity = Binder.clearCallingIdentity();
            try {
                mStatusBar.removeNotification(r.statusBarKey);
            }
            finally {
                Binder.restoreCallingIdentity(identity);
            }
            r.statusBarKey = null;
        }

        // sound
        if (mSoundNotification == r) {
            mSoundNotification = null;
            long identity = Binder.clearCallingIdentity();
            try {
                mSound.stop();
            }
            finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        // vibrate
        if (mVibrateNotification == r) {
            mVibrateNotification = null;
            long identity = Binder.clearCallingIdentity();
            try {
                mVibrator.cancel();
            }
            finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        // light
        mLights.remove(r);
        if (mLedNotification == r) {
            mLedNotification = null;
        }
    }

    /**
     * Cancels a notification ONLY if it has all of the {@code mustHaveFlags}
     * and none of the {@code mustNotHaveFlags}.
     */
    private void cancelNotification(String pkg, String tag, int id, int mustHaveFlags,
            int mustNotHaveFlags, boolean sendDelete) {
        EventLog.writeEvent(EventLogTags.NOTIFICATION_CANCEL, pkg, id, tag,
                mustHaveFlags, mustNotHaveFlags);

        synchronized (mNotificationList) {
            int index = indexOfNotificationLocked(pkg, tag, id);
            if (index >= 0) {
                NotificationRecord r = mNotificationList.get(index);

                if ((r.notification.flags & mustHaveFlags) != mustHaveFlags) {
                    return;
                }
                if ((r.notification.flags & mustNotHaveFlags) != 0) {
                    return;
                }

                mNotificationList.remove(index);

                cancelNotificationLocked(r, sendDelete);
                updateLightsLocked();
            }
        }
    }

    /**
     * Cancels all notifications from a given package that have all of the
     * {@code mustHaveFlags}.
     */
    boolean cancelAllNotificationsInt(String pkg, int mustHaveFlags,
            int mustNotHaveFlags, boolean doit) {
        EventLog.writeEvent(EventLogTags.NOTIFICATION_CANCEL_ALL, pkg, mustHaveFlags,
                mustNotHaveFlags);

        synchronized (mNotificationList) {
            final int N = mNotificationList.size();
            boolean canceledSomething = false;
            for (int i = N-1; i >= 0; --i) {
                NotificationRecord r = mNotificationList.get(i);
                if ((r.notification.flags & mustHaveFlags) != mustHaveFlags) {
                    continue;
                }
                if ((r.notification.flags & mustNotHaveFlags) != 0) {
                    continue;
                }
                if (!r.pkg.equals(pkg)) {
                    continue;
                }
                canceledSomething = true;
                if (!doit) {
                    return true;
                }
                mNotificationList.remove(i);
                cancelNotificationLocked(r, false);
            }
            if (canceledSomething) {
                updateLightsLocked();
            }
            return canceledSomething;
        }
    }

    @Deprecated
    public void cancelNotification(String pkg, int id) {
        cancelNotificationWithTag(pkg, null /* tag */, id);
    }

    public void cancelNotificationWithTag(String pkg, String tag, int id) {
        checkIncomingCall(pkg);
        // Don't allow client applications to cancel foreground service notis.
        cancelNotification(pkg, tag, id, 0,
                Binder.getCallingUid() == Process.SYSTEM_UID
                ? 0 : Notification.FLAG_FOREGROUND_SERVICE, false);
    }

    public void cancelAllNotifications(String pkg) {
        checkIncomingCall(pkg);

        // Calling from user space, don't allow the canceling of actively
        // running foreground services.
        cancelAllNotificationsInt(pkg, 0, Notification.FLAG_FOREGROUND_SERVICE, true);
    }

    void checkIncomingCall(String pkg) {
        int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID || uid == 0) {
            return;
        }
        try {
            ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(
                    pkg, 0);
            if (ai.uid != uid) {
                throw new SecurityException("Calling uid " + uid + " gave package"
                        + pkg + " which is owned by uid " + ai.uid);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("Unknown package " + pkg);
        }
    }

    void cancelAll() {
        synchronized (mNotificationList) {
            final int N = mNotificationList.size();
            for (int i=N-1; i>=0; i--) {
                NotificationRecord r = mNotificationList.get(i);

                if ((r.notification.flags & (Notification.FLAG_ONGOING_EVENT
                                | Notification.FLAG_NO_CLEAR)) == 0) {
                    mNotificationList.remove(i);
                    cancelNotificationLocked(r, true);
                }
            }

            updateLightsLocked();
        }
    }

    // lock on mNotificationList
    private void updateLightsLocked()
    {
        // handle notification lights
        if (mLedNotification == null) {
            // get next notification, if any
            int n = mLights.size();
            if (n > 0) {
                mLedNotification = mLights.get(n-1);
            }
        }

        boolean wasScreenOn = mWasScreenOn;
        mWasScreenOn = false;

        if (mLedNotification == null) {
            mNotificationLight.turnOff();
            return;
        }

        // We can assume that if the user turned the screen off while there was
        // still an active notification then they wanted to keep the notification
        // for later. In this case we shouldn't flash the notification light.
        // For special notifications that automatically turn the screen on (such
        // as missed calls), we use this flag to force the notification light
        // even if the screen was turned off.
        boolean forceWithScreenOff = (mLedNotification.notification.flags &
                Notification.FLAG_FORCE_LED_SCREEN_OFF) != 0;

        // Don't flash while we are in a call, screen is on or we are in quiet hours with light dimmed
        if (mInCall || mScreenOn || (inQuietHours() && mQuietHoursDim) || (wasScreenOn && !forceWithScreenOff)) {
            mNotificationLight.turnOff();
        } else {
            int ledARGB;
            int ledOnMS;
            int ledOffMS;
            NotificationLedValues ledValues = getLedValuesForNotification(mLedNotification);
            if (ledValues != null) {
                ledARGB = ledValues.color != 0 ? ledValues.color : mDefaultNotificationColor;
                ledOnMS = ledValues.onMS >= 0 ? ledValues.onMS : mDefaultNotificationLedOn;
                ledOffMS = ledValues.offMS >= 0 ? ledValues.offMS : mDefaultNotificationLedOn;
            } else {
                if ((mLedNotification.notification.defaults & Notification.DEFAULT_LIGHTS) != 0) {
                    ledARGB = mDefaultNotificationColor;
                    ledOnMS = mDefaultNotificationLedOn;
                    ledOffMS = mDefaultNotificationLedOff;
                } else {
                    ledARGB = mLedNotification.notification.ledARGB;
                    ledOnMS = mLedNotification.notification.ledOnMS;
                    ledOffMS = mLedNotification.notification.ledOffMS;
                }
            }
            if (mNotificationPulseEnabled) {
                // pulse repeatedly
                mNotificationLight.setFlashing(ledARGB, LightsService.LIGHT_FLASH_TIMED,
                        ledOnMS, ledOffMS);
            }
        }
    }

    private void parseNotificationPulseCustomValuesString(String customLedValuesString) {
        if (TextUtils.isEmpty(customLedValuesString)) {
            return;
        }

        for (String packageValuesString : customLedValuesString.split("\\|")) {
            String[] packageValues = packageValuesString.split("=");
            if (packageValues.length != 2) {
                Log.e(TAG, "Error parsing custom led values for unknown package");
                continue;
            }
            String packageName = packageValues[0];
            String[] values = packageValues[1].split(";");
            if (values.length != 3) {
                Log.e(TAG, "Error parsing custom led values '" + packageValues[1] + "' for " + packageName);
                continue;
            }
            NotificationLedValues ledValues = new NotificationLedValues();
            try {
                ledValues.color = Integer.parseInt(values[0]);
                ledValues.onMS = Integer.parseInt(values[1]);
                ledValues.offMS = Integer.parseInt(values[2]);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing custom led values '" + packageValues[1] + "' for " + packageName);
                continue;
            }
            mNotificationPulseCustomLedValues.put(packageName, ledValues);
        }
    }

    private NotificationLedValues getLedValuesForNotification(NotificationRecord ledNotification) {
        return mNotificationPulseCustomLedValues.get(mapPackage(ledNotification.pkg));
    }

    private String mapPackage(String pkg) {
        if(!mPackageNameMappings.containsKey(pkg)) {
            return pkg;
        }
        return mPackageNameMappings.get(pkg);
    }

    // lock on mNotificationList
    private int indexOfNotificationLocked(String pkg, String tag, int id)
    {
        ArrayList<NotificationRecord> list = mNotificationList;
        final int len = list.size();
        for (int i=0; i<len; i++) {
            NotificationRecord r = list.get(i);
            if (tag == null) {
                if (r.tag != null) {
                    continue;
                }
            } else {
                if (!tag.equals(r.tag)) {
                    continue;
                }
            }
            if (r.id == id && r.pkg.equals(pkg)) {
                return i;
            }
        }
        return -1;
    }

    private Context getUiContext() {
        if (mUiContext == null) {
            mUiContext = ThemeUtils.createUiContext(mContext);
        }
        return mUiContext != null ? mUiContext : mContext;
    }

    private void updateNotificationPulse() {
        synchronized (mNotificationList) {
            updateLightsLocked();
        }
    }

    // ======================================================================
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump NotificationManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        pw.println("Current Notification Manager state:");

        int N;

        synchronized (mToastQueue) {
            N = mToastQueue.size();
            if (N > 0) {
                pw.println("  Toast Queue:");
                for (int i=0; i<N; i++) {
                    mToastQueue.get(i).dump(pw, "    ");
                }
                pw.println("  ");
            }

        }

        synchronized (mNotificationList) {
            N = mNotificationList.size();
            if (N > 0) {
                pw.println("  Notification List:");
                for (int i=0; i<N; i++) {
                    mNotificationList.get(i).dump(pw, "    ", mContext);
                }
                pw.println("  ");
            }

            N = mLights.size();
            if (N > 0) {
                pw.println("  Lights List:");
                for (int i=0; i<N; i++) {
                    mLights.get(i).dump(pw, "    ", mContext);
                }
                pw.println("  ");
            }

            pw.println("  mSoundNotification=" + mSoundNotification);
            pw.println("  mSound=" + mSound);
            pw.println("  mVibrateNotification=" + mVibrateNotification);
            pw.println("  mDisabledNotifications=0x" + Integer.toHexString(mDisabledNotifications));
            pw.println("  mSystemReady=" + mSystemReady);
        }
    }
}
