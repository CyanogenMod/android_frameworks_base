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

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.app.INotificationManager;
import android.app.ITransientNotification;
import android.app.Notification;
import android.app.PendingIntent;
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
import android.media.IAudioService;
import android.media.IRingtonePlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.android.internal.statusbar.StatusBarNotification;
import com.android.internal.util.FastXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import libcore.io.IoUtils;


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
    private static final int VIBRATE_PATTERN_MAXLEN = 8 * 2 + 1; // up to eight bumps

    private static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_NOTIFICATION;
    private static final boolean SCORE_ONGOING_HIGHER = false;

    private static final int JUNK_SCORE = -1000;
    private static final int NOTIFICATION_PRIORITY_MULTIPLIER = 10;
    private static final int SCORE_DISPLAY_THRESHOLD = Notification.PRIORITY_MIN * NOTIFICATION_PRIORITY_MULTIPLIER;

    // Notifications with scores below this will not interrupt the user, either via LED or
    // sound or vibration
    private static final int SCORE_INTERRUPTION_THRESHOLD =
            Notification.PRIORITY_LOW * NOTIFICATION_PRIORITY_MULTIPLIER;

    private static final boolean ENABLE_BLOCKED_NOTIFICATIONS = true;
    private static final boolean ENABLE_BLOCKED_TOASTS = true;

    final Context mContext;
    final IActivityManager mAm;
    final IBinder mForegroundToken = new Binder();

    private WorkerHandler mHandler;
    private StatusBarManagerService mStatusBar;
    private LightsService.Light mNotificationLight;
    private LightsService.Light mAttentionLight;

    private int mDefaultNotificationColor;
    private int mDefaultNotificationLedOn;
    private int mDefaultNotificationLedOff;

    private long[] mDefaultVibrationPattern;
    private long[] mFallbackVibrationPattern;
    private long[] mNoAlertsVibrationPattern;

    private boolean mSystemReady;
    private int mDisabledNotifications;

    private NotificationRecord mSoundNotification;
    private NotificationRecord mVibrateNotification;

    private IAudioService mAudioService;
    private Vibrator mVibrator;

    // for enabling and disabling notification pulse behaviour
    private boolean mScreenOn = true;
    private boolean mDreaming = false;
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

    // Notification control database. For now just contains disabled packages.
    private AtomicFile mPolicyFile;
    private HashSet<String> mBlockedPackages = new HashSet<String>();

    private static final int DB_VERSION = 1;

    private static final String TAG_BODY = "notification-policy";
    private static final String ATTR_VERSION = "version";

    private static final String TAG_BLOCKED_PKGS = "blocked-packages";
    private static final String TAG_PACKAGE = "package";
    private static final String ATTR_NAME = "name";

    private void loadBlockDb() {
        synchronized(mBlockedPackages) {
            if (mPolicyFile == null) {
                File dir = new File("/data/system");
                mPolicyFile = new AtomicFile(new File(dir, "notification_policy.xml"));

                mBlockedPackages.clear();

                FileInputStream infile = null;
                try {
                    infile = mPolicyFile.openRead();
                    final XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(infile, null);

                    int type;
                    String tag;
                    int version = DB_VERSION;
                    while ((type = parser.next()) != END_DOCUMENT) {
                        tag = parser.getName();
                        if (type == START_TAG) {
                            if (TAG_BODY.equals(tag)) {
                                version = Integer.parseInt(parser.getAttributeValue(null, ATTR_VERSION));
                            } else if (TAG_BLOCKED_PKGS.equals(tag)) {
                                while ((type = parser.next()) != END_DOCUMENT) {
                                    tag = parser.getName();
                                    if (TAG_PACKAGE.equals(tag)) {
                                        mBlockedPackages.add(parser.getAttributeValue(null, ATTR_NAME));
                                    } else if (TAG_BLOCKED_PKGS.equals(tag) && type == END_TAG) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    // No data yet
                } catch (IOException e) {
                    Log.wtf(TAG, "Unable to read blocked notifications database", e);
                } catch (NumberFormatException e) {
                    Log.wtf(TAG, "Unable to parse blocked notifications database", e);
                } catch (XmlPullParserException e) {
                    Log.wtf(TAG, "Unable to parse blocked notifications database", e);
                } finally {
                    IoUtils.closeQuietly(infile);
                }
            }
        }
    }

    private void writeBlockDb() {
        synchronized(mBlockedPackages) {
            FileOutputStream outfile = null;
            try {
                outfile = mPolicyFile.startWrite();

                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(outfile, "utf-8");

                out.startDocument(null, true);

                out.startTag(null, TAG_BODY); {
                    out.attribute(null, ATTR_VERSION, String.valueOf(DB_VERSION));
                    out.startTag(null, TAG_BLOCKED_PKGS); {
                        // write all known network policies
                        for (String pkg : mBlockedPackages) {
                            out.startTag(null, TAG_PACKAGE); {
                                out.attribute(null, ATTR_NAME, pkg);
                            } out.endTag(null, TAG_PACKAGE);
                        }
                    } out.endTag(null, TAG_BLOCKED_PKGS);
                } out.endTag(null, TAG_BODY);

                out.endDocument();

                mPolicyFile.finishWrite(outfile);
            } catch (IOException e) {
                if (outfile != null) {
                    mPolicyFile.failWrite(outfile);
                }
            }
        }
    }

    public boolean areNotificationsEnabledForPackage(String pkg) {
        checkCallerIsSystem();
        return areNotificationsEnabledForPackageInt(pkg);
    }

    // Unchecked. Not exposed via Binder, but can be called in the course of enqueue*().
    private boolean areNotificationsEnabledForPackageInt(String pkg) {
        final boolean enabled = !mBlockedPackages.contains(pkg);
        if (DBG) {
            Slog.v(TAG, "notifications are " + (enabled?"en":"dis") + "abled for " + pkg);
        }
        return enabled;
    }

    public void setNotificationsEnabledForPackage(String pkg, boolean enabled) {
        checkCallerIsSystem();
        if (DBG) {
            Slog.v(TAG, (enabled?"en":"dis") + "abling notifications for " + pkg);
        }
        if (enabled) {
            mBlockedPackages.remove(pkg);
        } else {
            mBlockedPackages.add(pkg);

            // Now, cancel any outstanding notifications that are part of a just-disabled app
            if (ENABLE_BLOCKED_NOTIFICATIONS) {
                synchronized (mNotificationList) {
                    final int N = mNotificationList.size();
                    for (int i=0; i<N; i++) {
                        final NotificationRecord r = mNotificationList.get(i);
                        if (r.pkg.equals(pkg)) {
                            cancelNotificationLocked(r, false);
                        }
                    }
                }
            }
            // Don't bother canceling toasts, they'll go away soon enough.
        }
        writeBlockDb();
    }


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
        final int userId;
        final Notification notification;
        final int score;
        IBinder statusBarKey;

        NotificationRecord(String pkg, String tag, int id, int uid, int initialPid,
                int userId, int score, Notification notification)
        {
            this.pkg = pkg;
            this.tag = tag;
            this.id = id;
            this.uid = uid;
            this.initialPid = initialPid;
            this.userId = userId;
            this.score = score;
            this.notification = notification;
        }

        void dump(PrintWriter pw, String prefix, Context baseContext) {
            pw.println(prefix + this);
            pw.println(prefix + "  icon=0x" + Integer.toHexString(notification.icon)
                    + " / " + idDebugString(baseContext, this.pkg, notification.icon));
            pw.println(prefix + "  pri=" + notification.priority);
            pw.println(prefix + "  score=" + this.score);
            pw.println(prefix + "  contentIntent=" + notification.contentIntent);
            pw.println(prefix + "  deleteIntent=" + notification.deleteIntent);
            pw.println(prefix + "  tickerText=" + notification.tickerText);
            pw.println(prefix + "  contentView=" + notification.contentView);
            pw.println(prefix + "  uid=" + uid + " userId=" + userId);
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
                + " score=" + score
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
                        final IRingtonePlayer player = mAudioService.getRingtonePlayer();
                        if (player != null) {
                            player.stopAsync();
                        }
                    } catch (RemoteException e) {
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }

                    identity = Binder.clearCallingIdentity();
                    try {
                        mVibrator.cancel();
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }
        }

        public void onClearAll() {
            // XXX to be totally correct, the caller should tell us which user
            // this is for.
            cancelAll(ActivityManager.getCurrentUser());
        }

        public void onNotificationClick(String pkg, String tag, int id) {
            // XXX to be totally correct, the caller should tell us which user
            // this is for.
            cancelNotification(pkg, tag, id, Notification.FLAG_AUTO_CANCEL,
                    Notification.FLAG_FOREGROUND_SERVICE, false,
                    ActivityManager.getCurrentUser());
        }

        public void onNotificationClear(String pkg, String tag, int id) {
            // XXX to be totally correct, the caller should tell us which user
            // this is for.
            cancelNotification(pkg, tag, id, 0,
                Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE,
                true, ActivityManager.getCurrentUser());
        }

        public void onPanelRevealed() {
            synchronized (mNotificationList) {
                // sound
                mSoundNotification = null;

                long identity = Binder.clearCallingIdentity();
                try {
                    final IRingtonePlayer player = mAudioService.getRingtonePlayer();
                    if (player != null) {
                        player.stopAsync();
                    }
                } catch (RemoteException e) {
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }

                // vibrate
                mVibrateNotification = null;
                identity = Binder.clearCallingIdentity();
                try {
                    mVibrator.cancel();
                } finally {
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
            // XXX to be totally correct, the caller should tell us which user
            // this is for.
            cancelNotification(pkg, tag, id, 0, 0, false, UserHandle.getUserId(uid));
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

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            boolean queryRestart = false;
            boolean packageChanged = false;

            if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_RESTARTED)
                    || (packageChanged=action.equals(Intent.ACTION_PACKAGE_CHANGED))
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
                    if (packageChanged) {
                        // We cancel notifications for packages which have just been disabled
                        final int enabled = mContext.getPackageManager()
                                .getApplicationEnabledSetting(pkgName);
                        if (enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                || enabled == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                            return;
                        }
                    }
                    pkgList = new String[]{pkgName};
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkgName : pkgList) {
                        cancelAllNotificationsInt(pkgName, 0, 0, !queryRestart,
                                UserHandle.USER_ALL);
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                // Keep track of screen on/off state, but do not turn off the notification light
                // until user passes through the lock screen or views the notification.
                mScreenOn = true;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_DREAMING_STARTED)) {
                mDreaming = true;
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_DREAMING_STOPPED)) {
                mDreaming = false;
                if (mScreenOn) {
                    mNotificationLight.turnOff();
                }
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mInCall = (intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(
                        TelephonyManager.EXTRA_STATE_OFFHOOK));
                updateNotificationPulse();
            } else if (action.equals(Intent.ACTION_USER_STOPPED)) {
                int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (userHandle >= 0) {
                    cancelAllNotificationsInt(null, 0, 0, true, userHandle);
                }
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                // turn off LED when user passes through lock screen
                if (!mDreaming) {
                    mNotificationLight.turnOff();
                }
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
            mNotificationPulseEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE, 0, UserHandle.USER_CURRENT) != 0;

            // LED default color
            mDefaultNotificationColor = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR, mDefaultNotificationColor,
                    UserHandle.USER_CURRENT);

            // LED default on MS
            mDefaultNotificationLedOn = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON, mDefaultNotificationLedOn,
                    UserHandle.USER_CURRENT);

            // LED default off MS
            mDefaultNotificationLedOff = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF, mDefaultNotificationLedOff,
                    UserHandle.USER_CURRENT);

            // LED custom notification colors
            mNotificationPulseCustomLedValues.clear();
            if (Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE, 0,
                    UserHandle.USER_CURRENT) != 0) {
                parseNotificationPulseCustomValuesString(Settings.System.getStringForUser(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES, UserHandle.USER_CURRENT));
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
            mQuietHoursEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
            mQuietHoursStart = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_START, 0, UserHandle.USER_CURRENT_OR_SELF);
            mQuietHoursEnd = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_END, 0, UserHandle.USER_CURRENT_OR_SELF);
            mQuietHoursMute = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_MUTE, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
            mQuietHoursStill = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_STILL, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
            mQuietHoursDim = Settings.System.getIntForUser(resolver,
                    Settings.System.QUIET_HOURS_DIM, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
        }
    }

    static long[] getLongArray(Resources r, int resid, int maxlen, long[] def) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return def;
        }
        final int len = ar.length > maxlen ? maxlen : ar.length;
        long[] out = new long[len];
        for (int i=0; i<len; i++) {
            out[i] = ar[i];
        }
        return out;
    }

    NotificationManagerService(Context context, StatusBarManagerService statusBar,
            LightsService lights)
    {
        super();
        mContext = context;
        mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        mAm = ActivityManagerNative.getDefault();
        mToastQueue = new ArrayList<ToastRecord>();
        mHandler = new WorkerHandler();

        loadBlockDb();

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

        mDefaultVibrationPattern = getLongArray(resources,
                com.android.internal.R.array.config_defaultNotificationVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);

        mFallbackVibrationPattern = getLongArray(resources,
                com.android.internal.R.array.config_notificationFallbackVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);

        mNoAlertsVibrationPattern = getLongArray(resources,
                com.android.internal.R.array.config_notificationNoAlertsVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);

        // Don't start allowing notifications until the setup wizard has run once.
        // After that, including subsequent boots, init with notifications turned on.
        // This works on the first boot because the setup wizard will toggle this
        // flag at least once and we'll go back to 0 after that.
        if (0 == Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONED, 0)) {
            mDisabledNotifications = StatusBarManager.DISABLE_NOTIFICATION_ALERTS;
        }

        // register for various Intents
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
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
    }

    void systemReady() {
        mAudioService = IAudioService.Stub.asInterface(
                ServiceManager.getService(Context.AUDIO_SERVICE));

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

        final boolean isSystemToast = ("android".equals(pkg));

        if (ENABLE_BLOCKED_TOASTS && !isSystemToast && !areNotificationsEnabledForPackageInt(pkg)) {
            Slog.e(TAG, "Suppressing toast from package " + pkg + " by user request.");
            return;
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
                    if (!isSystemToast) {
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
    public void enqueueNotificationWithTag(String pkg, String tag, int id, Notification notification,
            int[] idOut, int userId)
    {
        enqueueNotificationInternal(pkg, Binder.getCallingUid(), Binder.getCallingPid(),
                tag, id, notification, idOut, userId);
    }
    
    private final static int clamp(int x, int low, int high) {
        return (x < low) ? low : ((x > high) ? high : x);
    }

    // Not exposed via Binder; for system use only (otherwise malicious apps could spoof the
    // uid/pid of another application)
    public void enqueueNotificationInternal(String pkg, int callingUid, int callingPid,
            String tag, int id, Notification notification, int[] idOut, int userId)
    {
        if (DBG) {
            Slog.v(TAG, "enqueueNotificationInternal: pkg=" + pkg + " id=" + id + " notification=" + notification);
        }
        checkCallerIsSystemOrSameApp(pkg);
        final boolean isSystemNotification = ("android".equals(pkg));

        userId = ActivityManager.handleIncomingUser(callingPid,
                callingUid, userId, true, false, "enqueueNotification", pkg);
        final UserHandle user = new UserHandle(userId);

        // Limit the number of notifications that any given package except the android
        // package can enqueue.  Prevents DOS attacks and deals with leaks.
        if (!isSystemNotification) {
            synchronized (mNotificationList) {
                int count = 0;
                final int N = mNotificationList.size();
                for (int i=0; i<N; i++) {
                    final NotificationRecord r = mNotificationList.get(i);
                    if (r.pkg.equals(pkg) && r.userId == userId) {
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
            EventLog.writeEvent(EventLogTags.NOTIFICATION_ENQUEUE, pkg, id, tag, userId,
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

        // === Scoring ===

        // 0. Sanitize inputs
        notification.priority = clamp(notification.priority, Notification.PRIORITY_MIN, Notification.PRIORITY_MAX);
        // Migrate notification flags to scores
        if (0 != (notification.flags & Notification.FLAG_HIGH_PRIORITY)) {
            if (notification.priority < Notification.PRIORITY_MAX) notification.priority = Notification.PRIORITY_MAX;
        } else if (SCORE_ONGOING_HIGHER && 0 != (notification.flags & Notification.FLAG_ONGOING_EVENT)) {
            if (notification.priority < Notification.PRIORITY_HIGH) notification.priority = Notification.PRIORITY_HIGH;
        }

        // 1. initial score: buckets of 10, around the app 
        int score = notification.priority * NOTIFICATION_PRIORITY_MULTIPLIER; //[-20..20]

        // 2. Consult external heuristics (TBD)

        // 3. Apply local rules

        // blocked apps
        if (ENABLE_BLOCKED_NOTIFICATIONS && !isSystemNotification && !areNotificationsEnabledForPackageInt(pkg)) {
            score = JUNK_SCORE;
            Slog.e(TAG, "Suppressing notification from package " + pkg + " by user request.");
        }

        if (DBG) {
            Slog.v(TAG, "Assigned score=" + score + " to " + notification);
        }

        if (score < SCORE_DISPLAY_THRESHOLD) {
            // Notification will be blocked because the score is too low.
            return;
        }

        // Should this notification make noise, vibe, or use the LED?
        final boolean canInterrupt = (score >= SCORE_INTERRUPTION_THRESHOLD);

        synchronized (mNotificationList) {
            final boolean inQuietHours = inQuietHours();

            NotificationRecord r = new NotificationRecord(pkg, tag, id, 
                    callingUid, callingPid, userId,
                    score,
                    notification);
            NotificationRecord old = null;

            int index = indexOfNotificationLocked(pkg, tag, id, userId);
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

            final int currentUser;
            final long token = Binder.clearCallingIdentity();
            try {
                currentUser = ActivityManager.getCurrentUser();
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            if (notification.icon != 0) {
                final StatusBarNotification n = new StatusBarNotification(
                        pkg, id, tag, r.uid, r.initialPid, score, notification, user);
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
                        if ((n.notification.flags & Notification.FLAG_SHOW_LIGHTS) != 0
                                && canInterrupt) {
                            mAttentionLight.pulse();
                        }
                    }
                    finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
                // Send accessibility events only for the current user.
                if (currentUser == userId) {
                    sendAccessibilityEvent(notification, pkg);
                }
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
                if (group != null) {
                    notification = group.processNotification(notification);
                }
            } catch(Throwable th) {
                Log.e(TAG, "An error occurred profiling the notification.", th);
            }

            final boolean alertsDisabled =
                    (mDisabledNotifications & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0;
            boolean readyForAlerts = canInterrupt && mSystemReady &&
                    (r.userId == UserHandle.USER_ALL || r.userId == userId && r.userId == currentUser) &&
                    (old == null || (notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0);
            boolean hasValidSound = false;

            // If we're not supposed to beep, vibrate, etc. then don't.
            if (readyForAlerts && !alertsDisabled) {
                final AudioManager audioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);

                // sound
                final boolean useDefaultSound =
                    (notification.defaults & Notification.DEFAULT_SOUND) != 0;

                Uri soundUri = null;

                if (!(inQuietHours && mQuietHoursMute) && useDefaultSound) {
                    soundUri = Settings.System.DEFAULT_NOTIFICATION_URI;

                    // check to see if the default notification sound is silent
                    ContentResolver resolver = mContext.getContentResolver();
                    hasValidSound = Settings.System.getString(resolver,
                           Settings.System.NOTIFICATION_SOUND) != null;
                } else if (!(inQuietHours && mQuietHoursMute) && notification.sound != null) {
                    soundUri = notification.sound;
                    hasValidSound = (soundUri != null);
                }

                if (hasValidSound) {
                    boolean looping = (notification.flags & Notification.FLAG_INSISTENT) != 0;
                    int audioStreamType;
                    if (notification.audioStreamType >= 0) {
                        audioStreamType = notification.audioStreamType;
                    } else {
                        audioStreamType = DEFAULT_STREAM_TYPE;
                    }
                    mSoundNotification = r;
                    // do not play notifications if stream volume is 0
                    // (typically because ringer mode is silent) or if speech recognition is active.
                    if ((audioManager.getStreamVolume(audioStreamType) != 0)
                            && !audioManager.isSpeechRecognitionActive()) {
                        final long identity = Binder.clearCallingIdentity();
                        try {
                            final IRingtonePlayer player = mAudioService.getRingtonePlayer();
                            if (player != null) {
                                player.playAsync(soundUri, user, looping, audioStreamType);
                            }
                        } catch (RemoteException e) {
                        } finally {
                            Binder.restoreCallingIdentity(identity);
                        }
                    }
                }
            }

            if (readyForAlerts && (!alertsDisabled || canVibrateDuringAlertsDisabled())) {
                // vibrate

                final AudioManager audioManager = (AudioManager)
                        mContext.getSystemService(Context.AUDIO_SERVICE);

                // Does the notification want to specify its own vibration?
                final boolean hasCustomVibrate = notification.vibrate != null;

                // new in 4.2: if there was supposed to be a sound and we're in vibrate mode,
                // and no other vibration is specified, we fall back to vibration
                final boolean convertSoundToVibration =
                           !hasCustomVibrate
                        && hasValidSound
                        && shouldConvertSoundToVibration()
                        && (audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

                // The DEFAULT_VIBRATE flag trumps any custom vibration AND the fallback.
                final boolean useDefaultVibrate =
                        (notification.defaults & Notification.DEFAULT_VIBRATE) != 0;

                if (!(inQuietHours && mQuietHoursStill)
                        && (useDefaultVibrate || convertSoundToVibration || hasCustomVibrate)
                        && !(audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT)) {
                    mVibrateNotification = r;

                    int repeat = (notification.flags & Notification.FLAG_INSISTENT) != 0 ? 0: -1;
                    long[] pattern;

                    if (alertsDisabled) {
                        pattern = mNoAlertsVibrationPattern;
                    } else if (useDefaultVibrate) {
                        pattern = mDefaultVibrationPattern;
                    } else if (hasCustomVibrate) {
                        pattern = notification.vibrate;
                    } else {
                        pattern = mFallbackVibrationPattern;
                    }

                    if (useDefaultVibrate || convertSoundToVibration) {
                        // Escalate privileges so we can use the vibrator even if the notifying app
                        // does not have the VIBRATE permission.
                        long identity = Binder.clearCallingIdentity();
                        try {
                            mVibrator.vibrate(pattern, repeat);
                        } finally {
                            Binder.restoreCallingIdentity(identity);
                        }
                    } else if (pattern.length > 1) {
                        // If you want your own vibration pattern, you need the VIBRATE permission
                        mVibrator.vibrate(notification.vibrate, repeat);
                    }
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
            if ((notification.flags & Notification.FLAG_SHOW_LIGHTS) != 0
                    && canInterrupt) {
                mLights.add(r);
                // force reevaluation of active light
                mLedNotification = null;
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

    private boolean shouldConvertSoundToVibration() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_CONVERT_SOUND_TO_VIBRATION,
                1, UserHandle.USER_CURRENT_OR_SELF) != 0;
    }

    private boolean canVibrateDuringAlertsDisabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_VIBRATE_DURING_ALERTS_DISABLED,
                0, UserHandle.USER_CURRENT_OR_SELF) != 0;
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
            final long identity = Binder.clearCallingIdentity();
            try {
                final IRingtonePlayer player = mAudioService.getRingtonePlayer();
                if (player != null) {
                    player.stopAsync();
                }
            } catch (RemoteException e) {
            } finally {
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
            int mustNotHaveFlags, boolean sendDelete, int userId) {
        EventLog.writeEvent(EventLogTags.NOTIFICATION_CANCEL, pkg, id, tag, userId,
                mustHaveFlags, mustNotHaveFlags);

        synchronized (mNotificationList) {
            int index = indexOfNotificationLocked(pkg, tag, id, userId);
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
     * Determine whether the userId applies to the notification in question, either because
     * they match exactly, or one of them is USER_ALL (which is treated as a wildcard).
     */
    private boolean notificationMatchesUserId(NotificationRecord r, int userId) {
        return
                // looking for USER_ALL notifications? match everything
                   userId == UserHandle.USER_ALL
                // a notification sent to USER_ALL matches any query
                || r.userId == UserHandle.USER_ALL
                // an exact user match
                || r.userId == userId;
    }

    /**
     * Cancels all notifications from a given package that have all of the
     * {@code mustHaveFlags}.
     */
    boolean cancelAllNotificationsInt(String pkg, int mustHaveFlags,
            int mustNotHaveFlags, boolean doit, int userId) {
        EventLog.writeEvent(EventLogTags.NOTIFICATION_CANCEL_ALL, pkg, userId,
                mustHaveFlags, mustNotHaveFlags);

        synchronized (mNotificationList) {
            final int N = mNotificationList.size();
            boolean canceledSomething = false;
            for (int i = N-1; i >= 0; --i) {
                NotificationRecord r = mNotificationList.get(i);
                if (!notificationMatchesUserId(r, userId)) {
                    continue;
                }
                // Don't remove notifications to all, if there's no package name specified
                if (r.userId == UserHandle.USER_ALL && pkg == null) {
                    continue;
                }
                if ((r.notification.flags & mustHaveFlags) != mustHaveFlags) {
                    continue;
                }
                if ((r.notification.flags & mustNotHaveFlags) != 0) {
                    continue;
                }
                if (pkg != null && !r.pkg.equals(pkg)) {
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

    public void cancelNotificationWithTag(String pkg, String tag, int id, int userId) {
        checkCallerCanCancelNotification(pkg);
        userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), userId, true, false, "cancelNotificationWithTag", pkg);
        // Don't allow client applications to cancel foreground service notis.
        cancelNotification(pkg, tag, id, 0,
                Binder.getCallingUid() == Process.SYSTEM_UID
                ? 0 : Notification.FLAG_FOREGROUND_SERVICE, false, userId);
    }

    public void cancelAllNotifications(String pkg, int userId) {
        checkCallerCanCancelNotification(pkg);

        userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), userId, true, false, "cancelAllNotifications", pkg);

        // Calling from user space, don't allow the canceling of actively
        // running foreground services.
        cancelAllNotificationsInt(pkg, 0, Notification.FLAG_FOREGROUND_SERVICE, true, userId);
    }

    void checkCallerIsSystem() {
        int uid = Binder.getCallingUid();
        if (UserHandle.getAppId(uid) == Process.SYSTEM_UID || uid == 0) {
            return;
        }
        throw new SecurityException("Disallowed call for uid " + uid);
    }

    void checkCallerCanCancelNotification(String pkg) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.CANCEL_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        checkCallerIsSystemOrSameApp(pkg);
    }

    void checkCallerIsSystemOrSameApp(String pkg) {
        int uid = Binder.getCallingUid();
        if (UserHandle.getAppId(uid) == Process.SYSTEM_UID || uid == 0) {
            return;
        }
        try {
            ApplicationInfo ai = AppGlobals.getPackageManager().getApplicationInfo(
                    pkg, 0, UserHandle.getCallingUserId());
            if (!UserHandle.isSameApp(ai.uid, uid)) {
                throw new SecurityException("Calling uid " + uid + " gave package"
                        + pkg + " which is owned by uid " + ai.uid);
            }
        } catch (RemoteException re) {
            throw new SecurityException("Unknown package " + pkg + "\n" + re);
        }
    }

    void cancelAll(int userId) {
        synchronized (mNotificationList) {
            final int N = mNotificationList.size();
            for (int i=N-1; i>=0; i--) {
                NotificationRecord r = mNotificationList.get(i);

                if (!notificationMatchesUserId(r, userId)) {
                    continue;
                }

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
            // use most recent light with highest score
            for (int i = mLights.size(); i > 0; i--) {
                NotificationRecord r = mLights.get(i - 1);
                if (mLedNotification == null || r.score > mLedNotification.score) {
                    mLedNotification = r;
                }
            }
        }

        // Don't flash while we are in a call, screen is on or we are in quiet hours with light dimmed
        if (mLedNotification == null || mInCall
                || (mScreenOn && !mDreaming) || (inQuietHours() && mQuietHoursDim)) {
            mNotificationLight.turnOff();
        } else {
            int ledARGB;
            int ledOnMS;
            int ledOffMS;
            NotificationLedValues ledValues = getLedValuesForNotification(mLedNotification);
            if (ledValues != null) {
                ledARGB = ledValues.color != 0 ? ledValues.color : mDefaultNotificationColor;
                ledOnMS = ledValues.onMS >= 0 ? ledValues.onMS : mDefaultNotificationLedOn;
                ledOffMS = ledValues.offMS >= 0 ? ledValues.offMS : mDefaultNotificationLedOff;
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
    private int indexOfNotificationLocked(String pkg, String tag, int id, int userId)
    {
        ArrayList<NotificationRecord> list = mNotificationList;
        final int len = list.size();
        for (int i=0; i<len; i++) {
            NotificationRecord r = list.get(i);
            if (!notificationMatchesUserId(r, userId) || r.id != id) {
                continue;
            }
            if (tag == null) {
                if (r.tag != null) {
                    continue;
                }
            } else {
                if (!tag.equals(r.tag)) {
                    continue;
                }
            }
            if (r.pkg.equals(pkg)) {
                return i;
            }
        }
        return -1;
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
            pw.println("  mVibrateNotification=" + mVibrateNotification);
            pw.println("  mLedNotification=" + mLedNotification);
            pw.println("  mDisabledNotifications=0x" + Integer.toHexString(mDisabledNotifications));
            pw.println("  mSystemReady=" + mSystemReady);
        }
    }
}
