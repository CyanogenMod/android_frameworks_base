/**
 * Copyright (c) 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.notification;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;

import android.app.AppOpsManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.service.notification.NotificationListenerService;
import android.service.notification.ZenModeConfig;
import android.telecom.TelecomManager;
import android.util.Log;
import android.util.Slog;

import com.android.internal.R;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;

/**
 * NotificationManagerService helper for functionality related to zen mode.
 */
public class ZenModeHelper {
    private static final String TAG = "ZenModeHelper";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final Handler mHandler;
    private final SettingsObserver mSettingsObserver;
    private final AppOpsManager mAppOps;
    private final ZenModeConfig mDefaultConfig;
    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();

    private ComponentName mDefaultPhoneApp;
    private int mZenMode;
    private ZenModeConfig mConfig;
    private AudioManager mAudioManager;
    private int mPreviousRingerMode = -1;

    public ZenModeHelper(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mDefaultConfig = readDefaultConfig(context.getResources());
        mConfig = mDefaultConfig;
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
    }

    public static ZenModeConfig readDefaultConfig(Resources resources) {
        XmlResourceParser parser = null;
        try {
            parser = resources.getXml(R.xml.default_zen_mode_config);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                final ZenModeConfig config = ZenModeConfig.readXml(parser);
                if (config != null) return config;
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error reading default zen mode config from resource", e);
        } finally {
            IoUtils.closeQuietly(parser);
        }
        return new ZenModeConfig();
    }

    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    public void setAudioManager(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    public int getZenModeListenerInterruptionFilter() {
        switch (mZenMode) {
            case Global.ZEN_MODE_OFF:
                return NotificationListenerService.INTERRUPTION_FILTER_ALL;
            case Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                return NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
            case Global.ZEN_MODE_NO_INTERRUPTIONS:
                return NotificationListenerService.INTERRUPTION_FILTER_NONE;
            default:
                return 0;
        }
    }

    private static int zenModeFromListenerInterruptionFilter(int listenerInterruptionFilter,
            int defValue) {
        switch (listenerInterruptionFilter) {
            case NotificationListenerService.INTERRUPTION_FILTER_ALL:
                return Global.ZEN_MODE_OFF;
            case NotificationListenerService.INTERRUPTION_FILTER_PRIORITY:
                return Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
            case NotificationListenerService.INTERRUPTION_FILTER_NONE:
                return Global.ZEN_MODE_NO_INTERRUPTIONS;
            default:
                return defValue;
        }
    }

    public void requestFromListener(int interruptionFilter) {
        final int newZen = zenModeFromListenerInterruptionFilter(interruptionFilter, -1);
        if (newZen != -1) {
            setZenMode(newZen, "listener");
        }
    }

    public boolean shouldIntercept(NotificationRecord record) {
        if (isSystem(record)) {
            return false;
        }
        switch (mZenMode) {
            case Global.ZEN_MODE_NO_INTERRUPTIONS:
                // #notevenalarms
                ZenLog.traceIntercepted(record, "none");
                return true;
            case Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                if (isAlarm(record)) {
                    // Alarms are always priority
                    return false;
                }
                // allow user-prioritized packages through in priority mode
                if (record.getPackagePriority() == Notification.PRIORITY_MAX) {
                    ZenLog.traceNotIntercepted(record, "priorityApp");
                    return false;
                }
                if (isCall(record)) {
                    if (!mConfig.allowCalls) {
                        ZenLog.traceIntercepted(record, "!allowCalls");
                        return true;
                    }
                    return shouldInterceptAudience(record);
                }
                if (isMessage(record)) {
                    if (!mConfig.allowMessages) {
                        ZenLog.traceIntercepted(record, "!allowMessages");
                        return true;
                    }
                    return shouldInterceptAudience(record);
                }
                if (isEvent(record)) {
                    if (!mConfig.allowEvents) {
                        ZenLog.traceIntercepted(record, "!allowEvents");
                        return true;
                    }
                    return false;
                }
                ZenLog.traceIntercepted(record, "!priority");
                return true;
            default:
                return false;
        }
    }

    private boolean shouldInterceptAudience(NotificationRecord record) {
        if (!audienceMatches(record.getContactAffinity())) {
            ZenLog.traceIntercepted(record, "!audienceMatches");
            return true;
        }
        return false;
    }

    public int getZenMode() {
        return mZenMode;
    }

    public void setZenMode(int zenModeValue, String reason) {
        ZenLog.traceSetZenMode(zenModeValue, reason);
        Global.putInt(mContext.getContentResolver(), Global.ZEN_MODE, zenModeValue);
    }

    public void updateZenMode() {
        final int mode = Global.getInt(mContext.getContentResolver(),
                Global.ZEN_MODE, Global.ZEN_MODE_OFF);
        if (mode != mZenMode) {
            ZenLog.traceUpdateZenMode(mZenMode, mode);
        }
        mZenMode = mode;
        final boolean zen = mZenMode != Global.ZEN_MODE_OFF;
        final String[] exceptionPackages = null; // none (for now)

        // call restrictions
        final boolean muteCalls = zen && !mConfig.allowCalls;
        mAppOps.setRestriction(AppOpsManager.OP_VIBRATE, USAGE_NOTIFICATION_RINGTONE,
                muteCalls ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED,
                exceptionPackages);
        mAppOps.setRestriction(AppOpsManager.OP_PLAY_AUDIO, USAGE_NOTIFICATION_RINGTONE,
                muteCalls ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED,
                exceptionPackages);

        // alarm restrictions
        final boolean muteAlarms = mZenMode == Global.ZEN_MODE_NO_INTERRUPTIONS;
        mAppOps.setRestriction(AppOpsManager.OP_VIBRATE, USAGE_ALARM,
                muteAlarms ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED,
                exceptionPackages);
        mAppOps.setRestriction(AppOpsManager.OP_PLAY_AUDIO, USAGE_ALARM,
                muteAlarms ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED,
                exceptionPackages);

        // force ringer mode into compliance
        if (mAudioManager != null) {
            int ringerMode = mAudioManager.getRingerMode();
            int forcedRingerMode = -1;
            if (mZenMode == Global.ZEN_MODE_NO_INTERRUPTIONS) {
                if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
                    mPreviousRingerMode = ringerMode;
                    if (DEBUG) Slog.d(TAG, "Silencing ringer");
                    forcedRingerMode = AudioManager.RINGER_MODE_SILENT;
                }
            } else {
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    if (DEBUG) Slog.d(TAG, "Unsilencing ringer");
                    forcedRingerMode = mPreviousRingerMode != -1 ? mPreviousRingerMode
                            : AudioManager.RINGER_MODE_NORMAL;
                    mPreviousRingerMode = -1;
                }
            }
            if (forcedRingerMode != -1) {
                mAudioManager.setRingerMode(forcedRingerMode, false /*checkZen*/);
                ZenLog.traceSetRingerMode(forcedRingerMode);
            }
        }
        dispatchOnZenModeChanged();
    }

    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.print("mZenMode=");
        pw.println(Global.zenModeToString(mZenMode));
        pw.print(prefix); pw.print("mConfig="); pw.println(mConfig);
        pw.print(prefix); pw.print("mDefaultConfig="); pw.println(mDefaultConfig);
        pw.print(prefix); pw.print("mPreviousRingerMode="); pw.println(mPreviousRingerMode);
        pw.print(prefix); pw.print("mDefaultPhoneApp="); pw.println(mDefaultPhoneApp);
    }

    public void readXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        final ZenModeConfig config = ZenModeConfig.readXml(parser);
        if (config != null) {
            setConfig(config);
        }
    }

    public void writeXml(XmlSerializer out) throws IOException {
        mConfig.writeXml(out);
    }

    public ZenModeConfig getConfig() {
        return mConfig;
    }

    public boolean setConfig(ZenModeConfig config) {
        if (config == null || !config.isValid()) return false;
        if (config.equals(mConfig)) return true;
        ZenLog.traceConfig(mConfig, config);
        mConfig = config;
        dispatchOnConfigChanged();
        final String val = Integer.toString(mConfig.hashCode());
        Global.putString(mContext.getContentResolver(), Global.ZEN_MODE_CONFIG_ETAG, val);
        updateZenMode();
        return true;
    }

    private void handleRingerModeChanged() {
        if (mAudioManager != null) {
            // follow ringer mode if necessary
            final int ringerMode = mAudioManager.getRingerMode();
            int newZen = -1;
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                if (mZenMode == Global.ZEN_MODE_OFF && !mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_setZenModeWhenSilentModeOn)) {
                    newZen = Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
                } else if (mZenMode != Global.ZEN_MODE_NO_INTERRUPTIONS) {
                    newZen = Global.ZEN_MODE_NO_INTERRUPTIONS;
                }
            } else if ((ringerMode == AudioManager.RINGER_MODE_NORMAL
                    || ringerMode == AudioManager.RINGER_MODE_VIBRATE)
                    && mZenMode == Global.ZEN_MODE_NO_INTERRUPTIONS) {
                newZen = Global.ZEN_MODE_OFF;
            }
            if (newZen != -1) {
                ZenLog.traceFollowRingerMode(ringerMode, mZenMode, newZen);
                setZenMode(newZen, "ringerMode");
            }
        }
    }

    private void dispatchOnConfigChanged() {
        for (Callback callback : mCallbacks) {
            callback.onConfigChanged();
        }
    }

    private void dispatchOnZenModeChanged() {
        for (Callback callback : mCallbacks) {
            callback.onZenModeChanged();
        }
    }

    private static boolean isSystem(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_SYSTEM);
    }

    private static boolean isAlarm(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_ALARM)
                || record.isAudioStream(AudioManager.STREAM_ALARM)
                || record.isAudioAttributesUsage(AudioAttributes.USAGE_ALARM);
    }

    private static boolean isEvent(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_EVENT);
    }

    public boolean isCall(NotificationRecord record) {
        return record != null && (isDefaultPhoneApp(record.sbn.getPackageName())
                || record.isCategory(Notification.CATEGORY_CALL));
    }

    private boolean isDefaultPhoneApp(String pkg) {
        if (mDefaultPhoneApp == null) {
            final TelecomManager telecomm =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            mDefaultPhoneApp = telecomm != null ? telecomm.getDefaultPhoneApp() : null;
            if (DEBUG) Slog.d(TAG, "Default phone app: " + mDefaultPhoneApp);
        }
        return pkg != null && mDefaultPhoneApp != null
                && pkg.equals(mDefaultPhoneApp.getPackageName());
    }

    private boolean isDefaultMessagingApp(NotificationRecord record) {
        final int userId = record.getUserId();
        if (userId == UserHandle.USER_NULL || userId == UserHandle.USER_ALL) return false;
        final String defaultApp = Secure.getStringForUser(mContext.getContentResolver(),
                Secure.SMS_DEFAULT_APPLICATION, userId);
        return Objects.equals(defaultApp, record.sbn.getPackageName());
    }

    private boolean isMessage(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_MESSAGE) || isDefaultMessagingApp(record);
    }

    /**
     * @param extras extras of the notification with EXTRA_PEOPLE populated
     * @param contactsTimeoutMs timeout in milliseconds to wait for contacts response
     * @param timeoutAffinity affinity to return when the timeout specified via
     *                        <code>contactsTimeoutMs</code> is hit
     */
    public boolean matchesCallFilter(UserHandle userHandle, Bundle extras,
            ValidateNotificationPeople validator, int contactsTimeoutMs, float timeoutAffinity) {
        final int zen = mZenMode;
        if (zen == Global.ZEN_MODE_NO_INTERRUPTIONS) return false; // nothing gets through
        if (zen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            if (!mConfig.allowCalls) return false; // no calls get through
            if (validator != null) {
                final float contactAffinity = validator.getContactAffinity(userHandle, extras,
                        contactsTimeoutMs, timeoutAffinity);
                return audienceMatches(contactAffinity);
            }
        }
        return true;
    }

    private boolean audienceMatches(float contactAffinity) {
        switch (mConfig.allowFrom) {
            case ZenModeConfig.SOURCE_ANYONE:
                return true;
            case ZenModeConfig.SOURCE_CONTACT:
                return contactAffinity >= ValidateNotificationPeople.VALID_CONTACT;
            case ZenModeConfig.SOURCE_STAR:
                return contactAffinity >= ValidateNotificationPeople.STARRED_CONTACT;
            default:
                Slog.w(TAG, "Encountered unknown source: " + mConfig.allowFrom);
                return true;
        }
    }

    private final Runnable mRingerModeChanged = new Runnable() {
        @Override
        public void run() {
            handleRingerModeChanged();
        }
    };

    private class SettingsObserver extends ContentObserver {
        private final Uri ZEN_MODE = Global.getUriFor(Global.ZEN_MODE);

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(ZEN_MODE, false /*notifyForDescendents*/, this);
            update(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update(uri);
        }

        public void update(Uri uri) {
            if (ZEN_MODE.equals(uri)) {
                updateZenMode();
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.post(mRingerModeChanged);
        }
    };

    public static class Callback {
        void onConfigChanged() {}
        void onZenModeChanged() {}
    }
}
