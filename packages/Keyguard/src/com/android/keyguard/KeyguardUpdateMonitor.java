/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;

import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN;
import static android.os.BatteryManager.EXTRA_STATUS;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_HEALTH;

import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.fingerprint.FingerprintManager;
import android.service.fingerprint.FingerprintManagerReceiver;
import android.service.fingerprint.FingerprintUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.google.android.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedUnlockAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedUnlockAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor implements TrustManager.TrustListener {

    private static final String TAG = "KeyguardUpdateMonitor";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final boolean DEBUG_SIM_STATES = DEBUG || false;
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private static final String ACTION_FACE_UNLOCK_STARTED
            = "com.android.facelock.FACE_UNLOCK_STARTED";
    private static final String ACTION_FACE_UNLOCK_STOPPED
            = "com.android.facelock.FACE_UNLOCK_STOPPED";

    // Callback messages
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_CLOCK_VISIBILITY_CHANGED = 307;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_DPM_STATE_CHANGED = 309;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_USER_REMOVED = 311;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 312;
    private static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_USER_SWITCH_COMPLETE = 314;
    private static final int MSG_SET_CURRENT_CLIENT_ID = 315;
    private static final int MSG_SET_PLAYBACK_STATE = 316;
    private static final int MSG_USER_INFO_CHANGED = 317;
    private static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_SCREEN_TURNED_ON = 319;
    private static final int MSG_SCREEN_TURNED_OFF = 320;
    private static final int MSG_AIRPLANE_MODE_CHANGED = 321;
    private static final int MSG_KEYGUARD_BOUNCER_CHANGED = 322;
    private static final int MSG_FINGERPRINT_PROCESSED = 323;
    private static final int MSG_FINGERPRINT_ACQUIRED = 324;
    private static final int MSG_FACE_UNLOCK_STATE_CHANGED = 325;
    private static final int MSG_SUBINFO_RECORD_UPDATE = 326;
    private static final int MSG_SUBINFO_CONTENT_CHANGE = 327;
    private static final int MSG_SERVICE_STATE_CHANGED = 328;
    private static final int MSG_SIM_SUBSCRIPTION_INFO_CHANGED = 329;

    private static final int INVALID_SUBID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static final int INVALID_SLOT_ID = SubscriptionManager.INVALID_SIM_SLOT_INDEX;

    private static KeyguardUpdateMonitor sInstance;

    private final Context mContext;

    HashMap<Integer, SimData> mSimDatas = new HashMap<Integer, SimData>();
    // Telephony state
    private HashMap<Integer, IccCardConstants.State> mSimState
            = new HashMap<Integer, IccCardConstants.State>();
    private HashMap<Integer, CharSequence> mPlmn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mSpn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mOriginalPlmn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mOriginalSpn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, Boolean> mShowPlmn = new HashMap<Integer, Boolean>();
    private HashMap<Integer, Boolean> mShowSpn = new HashMap<Integer, Boolean>();
    private HashMap<Integer, ServiceState> mServiceState = new HashMap<Integer, ServiceState>();
    private int mSubIdForSlot[];
    private int mRingMode;
    private int mPhoneState;
    private boolean mKeyguardIsVisible;
    private boolean mBouncer;
    private boolean mBootCompleted;

    // Device provisioning state
    private boolean mDeviceProvisioned;

    // Battery status
    private BatteryStatus mBatteryStatus;

    // Password attempts
    private int mFailedAttempts = 0;
    private int mFailedBiometricUnlockAttempts = 0;

    private boolean mAlternateUnlockEnabled;

    private boolean mClockVisible;

    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mCallbacks = Lists.newArrayList();
    private ContentObserver mDeviceProvisionedObserver;

    private boolean mSwitchingUser;

    private boolean mScreenOn;

    private int mNumPhones = 0;
    private SubscriptionManager mSubscriptionManager;
    private List<SubscriptionInfo> mSubscriptionInfo;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME_UPDATE:
                    handleTimeUpdate();
                    break;
                case MSG_BATTERY_UPDATE:
                    handleBatteryUpdate((BatteryStatus) msg.obj);
                    break;
                case MSG_CARRIER_INFO_UPDATE:
                    handleCarrierInfoUpdate((Integer) msg.obj);
                    break;
                case MSG_SIM_STATE_CHANGE:
                    handleSimStateChange((SimData) msg.obj);
                    break;
                case MSG_RINGER_MODE_CHANGED:
                    handleRingerModeChange(msg.arg1);
                    break;
                case MSG_PHONE_STATE_CHANGED:
                    handlePhoneStateChanged((String) msg.obj);
                    break;
                case MSG_CLOCK_VISIBILITY_CHANGED:
                    handleClockVisibilityChanged();
                    break;
                case MSG_DEVICE_PROVISIONED:
                    handleDeviceProvisioned();
                    break;
                case MSG_DPM_STATE_CHANGED:
                    handleDevicePolicyManagerStateChanged();
                    break;
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1, (IRemoteCallback) msg.obj);
                    break;
                case MSG_USER_SWITCH_COMPLETE:
                    handleUserSwitchComplete(msg.arg1);
                    break;
                case MSG_USER_REMOVED:
                    handleUserRemoved(msg.arg1);
                    break;
                case MSG_KEYGUARD_VISIBILITY_CHANGED:
                    handleKeyguardVisibilityChanged(msg.arg1);
                    break;
                case MSG_KEYGUARD_BOUNCER_CHANGED:
                    handleKeyguardBouncerChanged(msg.arg1);
                    break;
                case MSG_BOOT_COMPLETED:
                    handleBootCompleted();
                    break;
                case MSG_USER_INFO_CHANGED:
                    handleUserInfoChanged(msg.arg1);
                    break;
                case MSG_REPORT_EMERGENCY_CALL_ACTION:
                    handleReportEmergencyCallAction();
                    break;
                case MSG_SCREEN_TURNED_OFF:
                    handleScreenTurnedOff(msg.arg1);
                    break;
                case MSG_SCREEN_TURNED_ON:
                    handleScreenTurnedOn();
                    break;
                case MSG_AIRPLANE_MODE_CHANGED:
                    handleAirplaneModeChanged((Boolean) msg.obj);
                    break;
                case MSG_FINGERPRINT_ACQUIRED:
                    handleFingerprintAcquired(msg.arg1);
                    break;
                case MSG_FINGERPRINT_PROCESSED:
                    handleFingerprintProcessed(msg.arg1);
                    break;
                case MSG_FACE_UNLOCK_STATE_CHANGED:
                    handleFaceUnlockStateChanged(msg.arg1 != 0, msg.arg2);
                    break;
                case MSG_SUBINFO_RECORD_UPDATE:
                    handleSubInfoRecordUpdate();
                    break;
                case MSG_SUBINFO_CONTENT_CHANGE:
                    handleSubInfoContentChange((SubInfoContent) msg.obj);
                    break;
            }
        }
    };

    private OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            mHandler.sendEmptyMessage(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
        }
    };

    private SparseBooleanArray mUserHasTrust = new SparseBooleanArray();
    private SparseBooleanArray mUserTrustIsManaged = new SparseBooleanArray();
    private SparseBooleanArray mUserFingerprintRecognized = new SparseBooleanArray();
    private SparseBooleanArray mUserFaceUnlockRunning = new SparseBooleanArray();

    @Override
    public void onTrustChanged(boolean enabled, int userId, boolean initiatedByUser) {
        mUserHasTrust.put(userId, enabled);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustChanged(userId);
                if (enabled && initiatedByUser) {
                    cb.onTrustInitiatedByUser(userId);
                }
            }
        }
    }

    protected void handleSimSubscriptionInfoChanged() {
        if (DEBUG_SIM_STATES) {
            Log.v(TAG, "onSubscriptionInfoChanged()");
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            if (sil != null) {
                for (SubscriptionInfo subInfo : sil) {
                    Log.v(TAG, "SubInfo:" + subInfo);
                }
            } else {
                Log.v(TAG, "onSubscriptionInfoChanged: list is null");
            }
        }
        List<SubscriptionInfo> subscriptionInfos = getSubscriptionInfo(true /* forceReload */);

        // Hack level over 9000: Because the subscription id is not yet valid when we see the
        // first update in handleSimStateChange, we need to force refresh all all SIM states
        // so the subscription id for them is consistent.
        for (int i = 0; i < subscriptionInfos.size(); i++) {
            SubscriptionInfo info = subscriptionInfos.get(i);
            refreshSimState(info.getSubscriptionId(), info.getSimSlotIndex());
        }
        for (int i = 0; i < subscriptionInfos.size(); i++) {
            SimData data = mSimDatas.get(mSubscriptionInfo.get(i).getSubscriptionId());
            for (int j = 0; j < mCallbacks.size(); j++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(j).get();
                if (cb != null) {
                    cb.onSimStateChanged(data.subId, data.simState);
                }
            }
        }
    }

    /** @return List of SubscriptionInfo records, maybe empty but never null */
    List<SubscriptionInfo> getSubscriptionInfo(boolean forceReload) {
        List<SubscriptionInfo> sil = mSubscriptionInfo;
        if (sil == null || forceReload) {
            sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        }
        if (sil == null) {
            // getActiveSubscriptionInfoList was null callers expect an empty list.
            mSubscriptionInfo = new ArrayList<SubscriptionInfo>();
        } else {
            mSubscriptionInfo = sil;
        }
        return mSubscriptionInfo;
    }

    @Override
    public void onTrustManagedChanged(boolean managed, int userId) {
        mUserTrustIsManaged.put(userId, managed);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTrustManagedChanged(userId);
            }
        }
    }

    private void onFingerprintRecognized(int userId) {
        mUserFingerprintRecognized.put(userId, true);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintRecognized(userId);
            }
        }
    }

    private void handleFingerprintProcessed(int fingerprintId) {
        if (fingerprintId == 0) return; // not a valid fingerprint

        final int userId;
        try {
            userId = ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get current user id: ", e);
            return;
        }
        if (isFingerprintDisabled(userId)) {
            Log.d(TAG, "Fingerprint disabled by DPM for userId: " + userId);
            return;
        }
        final ContentResolver res = mContext.getContentResolver();
        final int ids[] = FingerprintUtils.getFingerprintIdsForUser(res, userId);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == fingerprintId) {
                onFingerprintRecognized(userId);
            }
        }
    }

    private void handleFingerprintAcquired(int info) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFingerprintAcquired(info);
            }
        }
    }

    private void handleFaceUnlockStateChanged(boolean running, int userId) {
        mUserFaceUnlockRunning.put(userId, running);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onFaceUnlockStateChanged(running, userId);
            }
        }
    }

    public boolean isFaceUnlockRunning(int userId) {
        return mUserFaceUnlockRunning.get(userId);
    }

    private boolean isTrustDisabled(int userId) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
                // TODO once UI is finalized
                final boolean disabledByGlobalActions = false;
                final boolean disabledBySettings = false;

                // Don't allow trust agent if device is secured with a SIM PIN. This is here
                // mainly because there's no other way to prompt the user to enter their SIM PIN
                // once they get past the keyguard screen.
                final boolean disabledBySimPin = isSimPinSecure();

                final boolean disabledByDpm = (dpm.getKeyguardDisabledFeatures(null, userId)
                        & DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS) != 0;
                return disabledByDpm || disabledByGlobalActions || disabledBySettings
                        || disabledBySimPin;
        }
        return false;
    }

    private boolean isFingerprintDisabled(int userId) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && (dpm.getKeyguardDisabledFeatures(null, userId)
                    & DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT) != 0;
    }

    public boolean getUserHasTrust(int userId) {
        return !isTrustDisabled(userId) && mUserHasTrust.get(userId)
                || mUserFingerprintRecognized.get(userId);
    }

    public boolean getUserTrustIsManaged(int userId) {
        return mUserTrustIsManaged.get(userId) && !isTrustDisabled(userId);
    }

    static class DisplayClientState {
        public int clientGeneration;
        public boolean clearing;
        public PendingIntent intent;
        public int playbackState;
        public long playbackEventTime;
    }

    private DisplayClientState mDisplayClientState = new DisplayClientState();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, INVALID_SUBID);

                mPlmn.put(subId, getTelephonyPlmnFrom(intent));
                mSpn.put(subId, getTelephonySpnFrom(intent));
                mOriginalPlmn.put(subId, getTelephonyPlmnFrom(intent));
                mOriginalSpn.put(subId, getTelephonySpnFrom(intent));
                mShowPlmn.put(subId, intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN,
                        false));
                mShowSpn.put(subId, intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false));
                if (DEBUG) Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, update subId=" + subId
                    +" , plmn=" + mPlmn.get(subId) + ", spn=" + mSpn.get(subId));
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE, subId));
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                final int status = intent.getIntExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
                final int plugged = intent.getIntExtra(EXTRA_PLUGGED, 0);
                final int level = intent.getIntExtra(EXTRA_LEVEL, 0);
                final int health = intent.getIntExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
                final Message msg = mHandler.obtainMessage(
                        MSG_BATTERY_UPDATE, new BatteryStatus(status, level, plugged, health));
                mHandler.sendMessage(msg);
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                SimData simArgs = SimData.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action=" + action + ", state=" + stateExtra
                        + ", slotId=" + simArgs.slotId + ", subId=" + simArgs.subId);
                }
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_SIM_STATE_CHANGE, simArgs));
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
            } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_REMOVED,
                       intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0), 0));
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                boolean state = intent.getBooleanExtra("state", false);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AIRPLANE_MODE_CHANGED, state));
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                dispatchBootCompleted();
            } else if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                if (DEBUG) Log.d(TAG, "received ACTION_SUBINFO_RECORD_UPDATED");
                mHandler.sendEmptyMessage(MSG_SUBINFO_RECORD_UPDATE);
            } else if (TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE.equals(action)) {
                int subId = intent.getIntExtra(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID, INVALID_SUBID);
                String column = intent.getStringExtra(TelephonyIntents.EXTRA_COLUMN_NAME);
                String sValue = intent.getStringExtra(TelephonyIntents.EXTRA_STRING_CONTENT);
                int iValue = intent.getIntExtra(TelephonyIntents.EXTRA_INT_CONTENT, 0);
                if (DEBUG) Log.d(TAG, "received SUBINFO_CONTENT_CHANGE" + " subid = " + subId
                           + " column = " + column + " sVal = " + sValue + " iVal = " + iValue);
                final Message msg = mHandler.obtainMessage(MSG_SUBINFO_CONTENT_CHANGE,
                        new SubInfoContent(subId, column, sValue, iValue));
                mHandler.sendMessage(msg);
            } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, INVALID_SUBID);
                mServiceState.put(subId, ServiceState.newFromBundle(intent.getExtras()));
                Log.d(TAG, "ACTION_SERVICE_STATE_CHANGED on sub: " + subId + " mServiceState: "
                        + mServiceState.get(subId));
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE, subId));
            } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                Log.d(TAG, "Received CONFIGURATION_CHANGED intent");
                for (int i = 0; i < mNumPhones; i++) {
                    int[] subIds = SubscriptionManager.getSubId(i);
                    if (subIds != null && subIds.length > 0) {
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE,
                                subIds[0]));
                    } else {
                        Log.d(TAG, "No valid subs");
                    }
                }
            }

        }
    };

    private final BroadcastReceiver mBroadcastAllReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_INFO_CHANGED,
                        intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId()), 0));
            } else if (ACTION_FACE_UNLOCK_STARTED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 1,
                        getSendingUserId()));
            } else if (ACTION_FACE_UNLOCK_STOPPED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FACE_UNLOCK_STATE_CHANGED, 0,
                        getSendingUserId()));
            } else if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                    .equals(action)) {
                mHandler.sendEmptyMessage(MSG_DPM_STATE_CHANGED);
            }
        }
    };
    private FingerprintManagerReceiver mFingerprintManagerReceiver =
            new FingerprintManagerReceiver() {
        @Override
        public void onProcessed(int fingerprintId) {
            mHandler.obtainMessage(MSG_FINGERPRINT_PROCESSED, fingerprintId, 0).sendToTarget();
        };

        @Override
        public void onAcquired(int info) {
            mHandler.obtainMessage(MSG_FINGERPRINT_ACQUIRED, info, 0).sendToTarget();
        }

        @Override
        public void onError(int error) {
            if (DEBUG) Log.w(TAG, "FingerprintManager reported error: " + error);
        }
    };

    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    private static class SimData {
        public State simState;
        int slotId = 0;
        int subId = INVALID_SUBID;

        SimData(IccCardConstants.State state, int slotId, int subId) {
            this.simState = state;
            this.slotId = slotId;
            this.subId = subId;
        }

        static SimData fromIntent(Intent intent) {
            IccCardConstants.State state;
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);

                if (IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state = IccCardConstants.State.PERM_DISABLED;
                } else {
                    state = IccCardConstants.State.ABSENT;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state = IccCardConstants.State.PIN_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state = IccCardConstants.State.PUK_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_PERSO.equals(lockedReason)) {
                    state = IccCardConstants.State.PERSO_LOCKED;
                } else {
                    state = IccCardConstants.State.UNKNOWN;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
                state = IccCardConstants.State.CARD_IO_ERROR;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                // This is required because telephony doesn't return to "READY" after
                // these state transitions. See bug 7197471.
                state = IccCardConstants.State.READY;
            } else {
                state = IccCardConstants.State.UNKNOWN;
            }
            return new SimData(state, slotId, subId);
        }

        public String toString() {
            return simState.toString();
        }
    }

    public static class BatteryStatus {
        public final int status;
        public final int level;
        public final int plugged;
        public final int health;
        public BatteryStatus(int status, int level, int plugged, int health) {
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

        /**
         * Determine whether the device is plugged in (USB, power, or wireless).
         * @return true if the device is plugged in.
         */
        public boolean isPluggedIn() {

            return plugged == BatteryManager.BATTERY_PLUGGED_AC
                    || plugged == BatteryManager.BATTERY_PLUGGED_USB
                    || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }

        /**
         * Whether or not the device is charged. Note that some devices never return 100% for
         * battery level, so this allows either battery level or status to determine if the
         * battery is charged.
         * @return true if the device is charged
         */
        public boolean isCharged() {
            return status == BATTERY_STATUS_FULL || level >= 100;
        }

        /**
         * Whether battery is low and needs to be charged.
         * @return true if battery is low
         */
        public boolean isBatteryLow() {
            return level < LOW_BATTERY_THRESHOLD;
        }

    }

    /* package */ static class SubInfoContent {
        public final int subInfoId;
        public final String column;
        public final String sValue;
        public final int iValue;
        public SubInfoContent(int subInfoId, String column, String sValue, int iValue) {
            this.subInfoId = subInfoId;
            this.column = column;
            this.sValue = sValue;
            this.iValue = iValue;
        }
    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardUpdateMonitor(context);
        }
        return sInstance;
    }

    protected void handleScreenTurnedOn() {
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onScreenTurnedOn();
            }
        }
    }

    protected void handleScreenTurnedOff(int arg1) {
        clearFingerprintRecognized();
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onScreenTurnedOff(arg1);
            }
        }
    }

    protected void handleSubInfoRecordUpdate() {
        List<SubscriptionInfo> activeSubInfos = null;
        activeSubInfos = mSubscriptionManager.getActiveSubscriptionInfoList();

        if (activeSubInfos != null) {
            for (SubscriptionInfo subInfo: activeSubInfos) {
                //subId for the slot initially initiazed to invalid value
                //Got intent with correct subId for the slot now.
                if (subInfo.getSimSlotIndex() > getNumPhones() - 1) {
                    continue;
                }
                if (mSubIdForSlot[subInfo.getSimSlotIndex()] != subInfo.getSubscriptionId()) {
                    int subId = mSubIdForSlot[subInfo.getSimSlotIndex()];
                    mSubIdForSlot[subInfo.getSimSlotIndex()] = subInfo.getSubscriptionId();
                    mPlmn.put(subInfo.getSubscriptionId(), mPlmn.get(subId));
                    mSpn.put(subInfo.getSubscriptionId(), mSpn.get(subId));

                    // If there is race condition it is possible that mSpn and mPlmn updated
                    // by SPN_STRINGS_UPDATED_ACTION intent gets overwritten here with invalid
                    // values, hence copy the original plmn and spn values got from intent.
                    copyOriginalPlmnSpn(subInfo.getSubscriptionId());
                    final int count = mCallbacks.size();
                    for (int i = 0; i < count; i++) {
                        KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                        if (cb != null) {
                            cb.onSubIdUpdated(subId, subInfo.getSubscriptionId());
                        }
                    }
                }
                if (DEBUG) {
                    Log.d(TAG, "handleSubInfoRecordUpdate mSubIdForSlot["
                        + subInfo.getSimSlotIndex() + "] = " + subInfo.getSubscriptionId());
                }
            }
        } else {
            if (DEBUG) Log.d(TAG, "updateStandbySubscriptions activeSubInfos is null");
        }
    }

    private void copyOriginalPlmnSpn(int subId) {
        // If mOriginalPlmn and mOriginalSpn are null here, then once
        // SPN_STRINGS_UPDATED_ACTION intent arrives mPlmn and mSpn will
        // get updated with right values.
        if (mOriginalPlmn.get(subId) != null) {
            mPlmn.put(subId, getLocaleString(mOriginalPlmn.get(subId).toString()));
        }

        if (mOriginalSpn.get(subId) != null) {
            mSpn.put(subId, getLocaleString(mOriginalSpn.get(subId).toString()));
        }
    }

    protected void handleSubInfoContentChange(SubInfoContent content) {
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSubInfoContentChanged(content.subInfoId, content.column,
                    content.sValue, content.iValue);
            }
        }
    }

    /**
     * IMPORTANT: Must be called from UI thread.
     */
    public void dispatchSetBackground(Bitmap bmp) {
        if (DEBUG) Log.d(TAG, "dispatchSetBackground");
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSetBackground(bmp);
            }
        }
    }

    private void handleAirplaneModeChanged(boolean on) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onAirplaneModeChanged(on);
            }
        }
    }

    private void handleUserInfoChanged(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserInfoChanged(userId);
            }
        }
    }

    private KeyguardUpdateMonitor(Context context) {
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(context);
        mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }

        mSubIdForSlot = new int[getNumPhones()];

        //Initialize subId for both slots to INVALID subId,
        //assign default plmn and spn values to INVALID subId
        //and assign the sim state as UNKNOWN for all the slots.
        for (int i = 0; i < getNumPhones(); i++) {
            mSubIdForSlot[i] = INVALID_SUBID;
            mSimState.put(i, IccCardConstants.State.UNKNOWN);
        }
        mSimState.put(INVALID_SLOT_ID, IccCardConstants.State.UNKNOWN);
        mPlmn.put(INVALID_SUBID, getDefaultPlmn());

        mBatteryStatus = new BatteryStatus(BATTERY_STATUS_UNKNOWN, 100, 0, 0);

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);

        context.registerReceiver(mBroadcastReceiver, filter);

        final IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootCompleteFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mBroadcastReceiver, bootCompleteFilter);

        final IntentFilter allUserFilter = new IntentFilter();
        allUserFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        allUserFilter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STARTED);
        allUserFilter.addAction(ACTION_FACE_UNLOCK_STOPPED);
        allUserFilter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        context.registerReceiverAsUser(mBroadcastAllReceiver, UserHandle.ALL, allUserFilter,
                null, null);

        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                    new IUserSwitchObserver.Stub() {
                        @Override
                        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHING,
                                    newUserId, 0, reply));
                            mSwitchingUser = true;
                        }
                        @Override
                        public void onUserSwitchComplete(int newUserId) throws RemoteException {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCH_COMPLETE,
                                    newUserId, 0));
                            mSwitchingUser = false;
                        }
                    });
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        TrustManager trustManager = (TrustManager) context.getSystemService(Context.TRUST_SERVICE);
        trustManager.registerTrustListener(this);

        FingerprintManager fpm;
        fpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        fpm.startListening(mFingerprintManagerReceiver);
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void watchForDeviceProvisioning() {
        mDeviceProvisionedObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
                if (mDeviceProvisioned) {
                    mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
                }
                if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                false, mDeviceProvisionedObserver);

        // prevent a race condition between where we check the flag and where we register the
        // observer by grabbing the value once again...
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != mDeviceProvisioned) {
            mDeviceProvisioned = provisioned;
            if (mDeviceProvisioned) {
                mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }

    /**
     * Handle {@link #MSG_DPM_STATE_CHANGED}
     */
    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDevicePolicyManagerStateChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCHING}
     */
    protected void handleUserSwitching(int userId, IRemoteCallback reply) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitching(userId);
            }
        }
        try {
            reply.sendResult(null);
        } catch (RemoteException e) {
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCH_COMPLETE}
     */
    protected void handleUserSwitchComplete(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitchComplete(userId);
            }
        }
    }

    /**
     * This is exposed since {@link Intent#ACTION_BOOT_COMPLETED} is not sticky. If
     * keyguard crashes sometime after boot, then it will never receive this
     * broadcast and hence not handle the event. This method is ultimately called by
     * PhoneWindowManager in this case.
     */
    public void dispatchBootCompleted() {
        mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    /**
     * Handle {@link #MSG_BOOT_COMPLETED}
     */
    protected void handleBootCompleted() {
        if (mBootCompleted) return;
        mBootCompleted = true;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBootCompleted();
            }
        }
    }

    /**
     * We need to store this state in the KeyguardUpdateMonitor since this class will not be
     * destroyed.
     */
    public boolean hasBootCompleted() {
        return mBootCompleted;
    }

    /**
     * Handle {@link #MSG_USER_REMOVED}
     */
    protected void handleUserRemoved(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserRemoved(userId);
            }
        }
    }

    /**
     * Handle {@link #MSG_DEVICE_PROVISIONED}
     */
    protected void handleDeviceProvisioned() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDeviceProvisioned();
            }
        }
        if (mDeviceProvisionedObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mDeviceProvisionedObserver);
            mDeviceProvisionedObserver = null;
        }
    }

    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     */
    protected void handlePhoneStateChanged(String newState) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_RINGING;
        }
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPhoneStateChanged(mPhoneState);
            }
        }
    }

    /**
     * Handle {@link #MSG_RINGER_MODE_CHANGED}
     */
    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRingerModeChanged(mode);
            }
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(BatteryStatus status) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        final boolean batteryUpdateInteresting = isBatteryUpdateInteresting(mBatteryStatus, status);
        mBatteryStatus = status;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onRefreshBatteryInfo(status);
                }
            }
        }
    }

    protected String getLocaleString(String originalCarrier) {
        String localeCarrier = android.util.NativeTextHelper.getLocalString(mContext,
                originalCarrier,
                com.android.internal.R.array.origin_carrier_names,
                com.android.internal.R.array.locale_carrier_names);
        return localeCarrier;
    }

    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int subId) {
        if (mContext.getResources().getBoolean(com.android.internal
                .R.bool.config_monitor_locale_change)) {
            if (mOriginalPlmn.get(subId) != null) {
                mPlmn.put(subId, getLocaleString(mOriginalPlmn.get(subId).toString()));
            }

            if (mOriginalSpn.get(subId) != null) {
                if (mOriginalPlmn.get(subId) != null && mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_spn_display_control)) {
                    mShowSpn.put(subId, false);
                    mSpn.put(subId, null);
                    Log.d(TAG,"Do not display spn string when Plmn and Spn both need to show"
                               + "and plmn string is not null");
                } else {
                    mSpn.put(subId, getLocaleString(mOriginalSpn.get(subId).toString()));
                }
            }
        }

        //display 2G/3G/4G if operator ask for showing radio tech
        if ((mServiceState.get(subId) != null) && (mShowSpn.get(subId) != null) &&
                (mShowPlmn.get(subId) != null) && (mServiceState.get(subId).getDataRegState()
                == ServiceState.STATE_IN_SERVICE || mServiceState.get(subId).getVoiceRegState()
                == ServiceState.STATE_IN_SERVICE) && mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_display_rat)) {
            concatenate(mShowSpn.get(subId), mShowPlmn.get(subId), subId,
                     mServiceState.get(subId));
        }

        if (DEBUG) Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mPlmn
            + ", spn = " + mSpn + ", subId = " + subId);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo(subId, mPlmn.get(subId), mSpn.get(subId));
            }
        }
    }

    private void concatenate(Boolean showSpn, Boolean showPlmn, int subId,
            ServiceState state) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        String rat = getRadioTech(state, phoneId);
        if (Boolean.TRUE.equals(showSpn) && !TextUtils.isEmpty(mSpn.get(subId))) {
            mSpn.put(subId, new StringBuilder().append(mSpn.get(subId)).append(rat));
        }
        if (Boolean.TRUE.equals(showPlmn) && !TextUtils.isEmpty(mPlmn.get(subId))) {
            mPlmn.put(subId, new StringBuilder().append(mPlmn.get(subId)).append(rat));
        }
    }

    private String getRadioTech(ServiceState serviceState, int phoneId) {
        String radioTech = "";
        int networkType = 0;
        Log.d(TAG, "dataRegState = " + serviceState.getDataRegState() + " voiceRegState = "
                + serviceState.getVoiceRegState() + " phoneId = " + phoneId);
        if (serviceState.getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            networkType = serviceState.getDataNetworkType();
            radioTech = new StringBuilder().append(" ").append(TelephonyManager.from(mContext).
                    networkTypeToString(networkType)).toString();
        } else if (serviceState.getRilVoiceRadioTechnology() != ServiceState.
                RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            networkType = serviceState.getVoiceNetworkType();
            radioTech = new StringBuilder().append(" ").append(TelephonyManager.from(mContext).
                    networkTypeToString(networkType)).toString();
        }
        return radioTech;
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimData simArgs) {
        final IccCardConstants.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString()
                    + "current sim state = " + mSimState.get(simArgs.slotId)
                    + " subId="+ simArgs.subId + "slotId = " + simArgs.slotId);
        }

        if (state != IccCardConstants.State.UNKNOWN && state != mSimState.get(simArgs.slotId)) {
            if (simArgs.slotId >= 0) {
                mSimState.put(simArgs.slotId, state);
                mSubIdForSlot[simArgs.slotId] = simArgs.subId;
                 for (int i = 0; i < mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                    if (cb != null) {
                        cb.onSimStateChanged(simArgs.subId, state);
                    }
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_CLOCK_VISIBILITY_CHANGED}
     */
    private void handleClockVisibilityChanged() {
        if (DEBUG) Log.d(TAG, "handleClockVisibilityChanged()");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onClockVisibilityChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_VISIBILITY_CHANGED}
     */
    private void handleKeyguardVisibilityChanged(int showing) {
        if (DEBUG) Log.d(TAG, "handleKeyguardVisibilityChanged(" + showing + ")");
        boolean isShowing = (showing == 1);
        mKeyguardIsVisible = isShowing;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardVisibilityChangedRaw(isShowing);
            }
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_BOUNCER_CHANGED}
     * @see #sendKeyguardBouncerChanged(boolean)
     */
    private void handleKeyguardBouncerChanged(int bouncer) {
        if (DEBUG) Log.d(TAG, "handleKeyguardBouncerChanged(" + bouncer + ")");
        boolean isBouncer = (bouncer == 1);
        mBouncer = isBouncer;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardBouncerChanged(isBouncer);
            }
        }
    }

    /**
     * Handle {@link #MSG_REPORT_EMERGENCY_CALL_ACTION}
     */
    private void handleReportEmergencyCallAction() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onEmergencyCallAction();
            }
        }
    }

    public boolean isKeyguardVisible() {
        return mKeyguardIsVisible;
    }

    /**
     * @return if the keyguard is currently in bouncer mode.
     */
    public boolean isKeyguardBouncer() {
        return mBouncer;
    }

    public boolean isSwitchingUser() {
        return mSwitchingUser;
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current) {
        final boolean nowPluggedIn = current.isPluggedIn();
        final boolean wasPluggedIn = old.isPluggedIn();
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && nowPluggedIn == true
            && (old.status != current.status);

        // change in plug state is always interesting
        if (wasPluggedIn != nowPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        if (nowPluggedIn && old.level != current.level) {
            return true;
        }

        // change where battery needs charging
        if (!nowPluggedIn && current.isBatteryLow() && current.level != old.level) {
            return true;
        }
        return false;
    }

    /**
     * @param intent The intent with action {@link TelephonyIntents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            String strEmergencyCallOnly = mContext.getResources().getText(
                    com.android.internal.R.string.emergency_calls_only).toString();
            if (mContext.getResources().getBoolean(
                    R.bool.config_showEmergencyCallOnlyInLockScreen)
                && plmn.equalsIgnoreCase(strEmergencyCallOnly)) {
                    return getDefaultPlmn();
            } else {
                return (plmn != null) ? plmn : getDefaultPlmn();
            }
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(R.string.keyguard_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /**
     * Remove the given observer's callback.
     *
     * @param callback The callback to remove
     */
    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** unregister callback for " + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback to register
     */
    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** register callback for " + callback);
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                if (DEBUG) Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<KeyguardUpdateMonitorCallback>(callback));
        removeCallback(null); // remove unused references
        sendUpdates(callback);
    }

    private void sendUpdates(KeyguardUpdateMonitorCallback callback) {
        // Notify listener of the current state
        callback.onRefreshBatteryInfo(mBatteryStatus);
        callback.onTimeChanged();
        callback.onRingerModeChanged(mRingMode);
        callback.onPhoneStateChanged(mPhoneState);
        callback.onClockVisibilityChanged();
        int subId;
        for(int slotId = 0; slotId < mSubIdForSlot.length; slotId++) {
            subId = mSubIdForSlot[slotId];
            callback.onRefreshCarrierInfo(subId, mPlmn.get(subId), mSpn.get(subId));
            if (DEBUG) Log.v(TAG, "sendUpdates: onSimStateChanged, subId = " + subId
                    + ", slotId = " + slotId + ", simState = " + mSimState.get(slotId));
            callback.onSimStateChanged(subId, mSimState.get(slotId));
        }
        boolean airplaneModeOn = Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        callback.onAirplaneModeChanged(airplaneModeOn);
    }

    public void sendKeyguardVisibilityChanged(boolean showing) {
        if (DEBUG) Log.d(TAG, "sendKeyguardVisibilityChanged(" + showing + ")");
        Message message = mHandler.obtainMessage(MSG_KEYGUARD_VISIBILITY_CHANGED);
        message.arg1 = showing ? 1 : 0;
        message.sendToTarget();
    }

    /**
     * @see #handleKeyguardBouncerChanged(int)
     */
    public void sendKeyguardBouncerChanged(boolean showingBouncer) {
        if (DEBUG) Log.d(TAG, "sendKeyguardBouncerChanged(" + showingBouncer + ")");
        Message message = mHandler.obtainMessage(MSG_KEYGUARD_BOUNCER_CHANGED);
        message.arg1 = showingBouncer ? 1 : 0;
        message.sendToTarget();
    }

    public void reportClockVisible(boolean visible) {
        mClockVisible = visible;
        mHandler.obtainMessage(MSG_CLOCK_VISIBILITY_CHANGED).sendToTarget();
    }

    public IccCardConstants.State getSimState(int subId) {
        int slotId =  INVALID_SLOT_ID;
        // return the sim state for the first slotId that has the corresponding
        // subId.
        for (int i = 0 ; i < mSubIdForSlot.length; i++) {
            if (subId == mSubIdForSlot[i]) {
                slotId = i;
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "getSimState: subId = " + subId + ", slotId = " + slotId
                + ", simState = " + mSimState.get(slotId));
        return mSimState.get(slotId);
    }

     /**	
      * @return true if and only if the state has changed for the specified {@code slotId}	
      */
     private boolean refreshSimState(int subId, int slotId) {

         // This is awful. It exists because there are two APIs for getting the SIM status	
         // that don't return the complete set of values and have different types. In Keyguard we
         // need IccCardConstants, but TelephonyManager would only give us
         // TelephonyManager.SIM_STATE*, so we retrieve it manually.
         final TelephonyManager tele = TelephonyManager.from(mContext);
         int simState =  tele.getSimState(slotId);
         State state = State.UNKNOWN;
         SimData data = mSimDatas.get(subId);
         final boolean changed;
         if (data == null) {
             data = new SimData(state, slotId, subId);
             mSimDatas.put(subId, data);
             changed = true; // no data yet; force update
         } else {
             changed = data.simState != state;
             data.simState = state;
         }
         return changed;
     }

    /**
     * Report that the user successfully entered the SIM PIN or PUK/SIM PIN so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     *
     * NOTE: Because handleSimStateChange() invokes callbacks immediately without going
     * through mHandler, this *must* be called from the UI thread.
     */
    public void reportSimUnlocked(int subId) {
        int slotId = SubscriptionManager.getSlotId(subId);
        handleSimStateChange(new SimData(IccCardConstants.State.READY, slotId, subId));
    }

    /**
     * Report that the emergency call button has been pressed and the emergency dialer is
     * about to be displayed.
     *
     * @param bypassHandler runs immediately.
     *
     * NOTE: Must be called from UI thread if bypassHandler == true.
     */
    public void reportEmergencyCallAction(boolean bypassHandler) {
        if (!bypassHandler) {
            mHandler.obtainMessage(MSG_REPORT_EMERGENCY_CALL_ACTION).sendToTarget();
        } else {
            handleReportEmergencyCallAction();
        }
    }

    public CharSequence getTelephonyPlmn(int subId) {
        return mPlmn.get(subId);
    }

    public CharSequence getTelephonySpn(int subId) {
        return mSpn.get(subId);
    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public int getFailedUnlockAttempts() {
        return mFailedAttempts;
    }

    public void clearFailedUnlockAttempts() {
        mFailedAttempts = 0;
        mFailedBiometricUnlockAttempts = 0;
    }

    public void clearFingerprintRecognized() {
        mUserFingerprintRecognized.clear();
    }

    public void reportFailedUnlockAttempt() {
        mFailedAttempts++;
    }

    public boolean isClockVisible() {
        return mClockVisible;
    }

    public int getPhoneState() {
        return mPhoneState;
    }

    public void reportFailedBiometricUnlockAttempt() {
        mFailedBiometricUnlockAttempts++;
    }

    public boolean getMaxBiometricUnlockAttemptsReached() {
        return mFailedBiometricUnlockAttempts >= FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP;
    }

    public boolean isAlternateUnlockEnabled() {
        return mAlternateUnlockEnabled;
    }

    public void setAlternateUnlockEnabled(boolean enabled) {
        mAlternateUnlockEnabled = enabled;
    }

    public boolean isSimLocked() {
        boolean bSimLocked = false;
        for (int slotId = 0; slotId < mSubIdForSlot.length; slotId++) {
            if (isSimLocked(mSimState.get(slotId))) {
                bSimLocked = true;
                break;
            }
        }
        return bSimLocked;
    }

    public static boolean isSimLocked(IccCardConstants.State state) {
        return state == IccCardConstants.State.PIN_REQUIRED
        || state == IccCardConstants.State.PUK_REQUIRED
        || state == IccCardConstants.State.PERM_DISABLED;
    }

    public boolean isSimPinSecure() {
        boolean isSecure = false;
        for (int slotId = 0; slotId < mSubIdForSlot.length; slotId++) {
            if (isSimPinSecure(mSimState.get(slotId))) {
                isSecure = true;
                break;
            }
        }
        return isSecure;
    }

    public static boolean isSimPinSecure(IccCardConstants.State state) {
        final IccCardConstants.State simState = state;
        return (simState == IccCardConstants.State.PIN_REQUIRED
                || simState == IccCardConstants.State.PUK_REQUIRED
                || simState == IccCardConstants.State.PERM_DISABLED);
    }

    public DisplayClientState getCachedDisplayClientState() {
        return mDisplayClientState;
    }

    // TODO: use these callbacks elsewhere in place of the existing notifyScreen*()
    // (KeyguardViewMediator, KeyguardHostView)
    public void dispatchScreenTurnedOn() {
        synchronized (this) {
            mScreenOn = true;
        }
        mHandler.sendEmptyMessage(MSG_SCREEN_TURNED_ON);
    }

    public void dispatchScreenTurndOff(int why) {
        synchronized(this) {
            mScreenOn = false;
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SCREEN_TURNED_OFF, why, 0));
    }

    public boolean isScreenOn() {
        return mScreenOn;
    }

    //return subId of first SIM that is PIN locked.
    public int getSimPinLockSubId() {
        int currentSimPinSubId = INVALID_SUBID;
        for (int slotId = 0; slotId < mSubIdForSlot.length; slotId++) {
            if (DEBUG) Log.d(TAG, "getSimPinLockSubId, slotId = " + slotId
                    + ", subId = "+ mSubIdForSlot[slotId]
                    + ", SimState = " + mSimState.get(slotId));
            if (mSimState.get(slotId) == IccCardConstants.State.PIN_REQUIRED) {
                currentSimPinSubId = mSubIdForSlot[slotId];
                break;
            }
        }
        return currentSimPinSubId;
    }

    //return subId of first SIM that is PUK locked.
    public int getSimPukLockSubId() {
        int currentSimPukSubId = INVALID_SUBID;
        for (int slotId = 0; slotId < mSubIdForSlot.length; slotId++) {
            if (DEBUG) Log.d(TAG, "getSimPukLockSubId, slotId = " + slotId
                    + ", subId = "+ mSubIdForSlot[slotId]
                    + ", SimState = " + mSimState.get(slotId));
            if (mSimState.get(slotId) == IccCardConstants.State.PUK_REQUIRED) {
                currentSimPukSubId = mSubIdForSlot[slotId];
                break;
            }
        }
        return currentSimPukSubId;
    }

    public int getNumPhones() {
        if (mNumPhones == 0) {
            mNumPhones = TelephonyManager.getDefault().getPhoneCount();
        }
        return mNumPhones;
    }

    public boolean isValidPhoneId(int phoneId) {
        if ((0 <= phoneId) && (phoneId < mNumPhones)) {
            return true;
        } else {
            return false;
        }
    }

    public int getPhoneIdBySubId(int subId) {
        int phoneId = -1;
        if (subId != INVALID_SUBID) {
            for (int i = 0; i < getNumPhones(); i++) {
                if (mSubIdForSlot[i] == subId) {
                    phoneId = i;
                    break;
                }
            }
        }
        return phoneId;
    }

    public int getSubIdByPhoneId(int phoneId) {
        int subId = INVALID_SUBID;
        if (isValidPhoneId(phoneId)) {
            subId = mSubIdForSlot[phoneId];
        }
        return subId;
    }

}
