/*
 * Copyright (C) 2006-2007 The Android Open Source Project
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

package com.android.internal.os;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.UidTraffic;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkStats;
import android.net.wifi.WifiActivityEnergyInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.BatteryStats;
import android.os.Build;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.WorkSource;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LogWriter;
import android.util.MutableInt;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.util.TimeUtils;
import android.util.Xml;
import android.view.Display;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.net.NetworkStatsFactory;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastPrintWriter;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.XmlUtils;
import com.android.server.NetworkManagementSocketTagger;
import libcore.util.EmptyArray;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * All information we are collecting about things that can happen that impact
 * battery life.  All times are represented in microseconds except where indicated
 * otherwise.
 */
public class BatteryStatsImpl extends BatteryStats {
    private static final String TAG = "BatteryStatsImpl";
    private static final boolean DEBUG = false;
    public static final boolean DEBUG_ENERGY = false;
    private static final boolean DEBUG_ENERGY_CPU = DEBUG_ENERGY;
    private static final boolean DEBUG_HISTORY = false;
    private static final boolean USE_OLD_HISTORY = false;   // for debugging.

    // TODO: remove "tcp" from network methods, since we measure total stats.

    // In-memory Parcel magic number, used to detect attempts to unmarshall bad data
    private static final int MAGIC = 0xBA757475; // 'BATSTATS'

    // Current on-disk Parcel version
    private static final int VERSION = 150 + (USE_OLD_HISTORY ? 1000 : 0);

    // Maximum number of items we will record in the history.
    private static final int MAX_HISTORY_ITEMS = 2000;

    // No, really, THIS is the maximum number of items we will record in the history.
    private static final int MAX_MAX_HISTORY_ITEMS = 3000;

    // The maximum number of names wakelocks we will keep track of
    // per uid; once the limit is reached, we batch the remaining wakelocks
    // in to one common name.
    private static final int MAX_WAKELOCKS_PER_UID = 100;

    // Number of transmit power states the Wifi controller can be in.
    private static final int NUM_WIFI_TX_LEVELS = 1;

    // Number of transmit power states the Bluetooth controller can be in.
    private static final int NUM_BT_TX_LEVELS = 1;

    protected Clocks mClocks;

    private final JournaledFile mFile;
    public final AtomicFile mCheckinFile;
    public final AtomicFile mDailyFile;

    static final int MSG_UPDATE_WAKELOCKS = 1;
    static final int MSG_REPORT_POWER_CHANGE = 2;
    static final int MSG_REPORT_CHARGING = 3;
    static final long DELAY_UPDATE_WAKELOCKS = 5*1000;

    private final KernelWakelockReader mKernelWakelockReader = new KernelWakelockReader();
    private final KernelWakelockStats mTmpWakelockStats = new KernelWakelockStats();

    private final KernelUidCpuTimeReader mKernelUidCpuTimeReader = new KernelUidCpuTimeReader();
    private KernelCpuSpeedReader[] mKernelCpuSpeedReaders;

    public interface BatteryCallback {
        public void batteryNeedsCpuUpdate();
        public void batteryPowerChanged(boolean onBattery);
        public void batterySendBroadcast(Intent intent);
    }

    public interface PlatformIdleStateCallback {
        public String getPlatformLowPowerStats();
    }

    private final PlatformIdleStateCallback mPlatformIdleStateCallback;


    final class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            BatteryCallback cb = mCallback;
            switch (msg.what) {
                case MSG_UPDATE_WAKELOCKS:
                    synchronized (BatteryStatsImpl.this) {
                        updateCpuTimeLocked();
                    }
                    if (cb != null) {
                        cb.batteryNeedsCpuUpdate();
                    }
                    break;
                case MSG_REPORT_POWER_CHANGE:
                    if (cb != null) {
                        cb.batteryPowerChanged(msg.arg1 != 0);
                    }
                    break;
                case MSG_REPORT_CHARGING:
                    if (cb != null) {
                        final String action;
                        synchronized (BatteryStatsImpl.this) {
                            action = mCharging ? BatteryManager.ACTION_CHARGING
                                    : BatteryManager.ACTION_DISCHARGING;
                        }
                        Intent intent = new Intent(action);
                        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        cb.batterySendBroadcast(intent);
                    }
                    break;
            }
        }
    }

    public interface Clocks {
        public long elapsedRealtime();
        public long uptimeMillis();
    }

    public static class SystemClocks implements Clocks {
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        public long uptimeMillis() {
            return SystemClock.uptimeMillis();
        }
    }

    public interface ExternalStatsSync {
        public static final int UPDATE_CPU = 0x01;
        public static final int UPDATE_WIFI = 0x02;
        public static final int UPDATE_RADIO = 0x04;
        public static final int UPDATE_BT = 0x08;
        public static final int UPDATE_ALL = UPDATE_CPU | UPDATE_WIFI | UPDATE_RADIO | UPDATE_BT;

        void scheduleSync(String reason, int flags);
        void scheduleCpuSyncDueToRemovedUid(int uid);
    }

    public final MyHandler mHandler;
    private final ExternalStatsSync mExternalSync;

    private BatteryCallback mCallback;

    /**
     * Mapping isolated uids to the actual owning app uid.
     */
    final SparseIntArray mIsolatedUids = new SparseIntArray();

    /**
     * The statistics we have collected organized by uids.
     */
    final SparseArray<BatteryStatsImpl.Uid> mUidStats = new SparseArray<>();

    // A set of pools of currently active timers.  When a timer is queried, we will divide the
    // elapsed time by the number of active timers to arrive at that timer's share of the time.
    // In order to do this, we must refresh each timer whenever the number of active timers
    // changes.
    final ArrayList<StopwatchTimer> mPartialTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mFullTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mWindowTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mDrawTimers = new ArrayList<>();
    final SparseArray<ArrayList<StopwatchTimer>> mSensorTimers = new SparseArray<>();
    final ArrayList<StopwatchTimer> mWifiRunningTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mFullWifiLockTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mWifiMulticastTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mWifiScanTimers = new ArrayList<>();
    final SparseArray<ArrayList<StopwatchTimer>> mWifiBatchedScanTimers = new SparseArray<>();
    final ArrayList<StopwatchTimer> mAudioTurnedOnTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mVideoTurnedOnTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mFlashlightTurnedOnTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mCameraTurnedOnTimers = new ArrayList<>();
    final ArrayList<StopwatchTimer> mBluetoothScanOnTimers = new ArrayList<>();

    // Last partial timers we use for distributing CPU usage.
    final ArrayList<StopwatchTimer> mLastPartialTimers = new ArrayList<>();

    // These are the objects that will want to do something when the device
    // is unplugged from power.
    protected final TimeBase mOnBatteryTimeBase = new TimeBase();

    // These are the objects that will want to do something when the device
    // is unplugged from power *and* the screen is off.
    final TimeBase mOnBatteryScreenOffTimeBase = new TimeBase();

    // Set to true when we want to distribute CPU across wakelocks for the next
    // CPU update, even if we aren't currently running wake locks.
    boolean mDistributeWakelockCpu;

    boolean mShuttingDown;

    final HistoryEventTracker mActiveEvents = new HistoryEventTracker();

    long mHistoryBaseTime;
    boolean mHaveBatteryLevel = false;
    boolean mRecordingHistory = false;
    int mNumHistoryItems;

    static final int MAX_HISTORY_BUFFER = 256*1024; // 256KB
    static final int MAX_MAX_HISTORY_BUFFER = 320*1024; // 320KB
    final Parcel mHistoryBuffer = Parcel.obtain();
    final HistoryItem mHistoryLastWritten = new HistoryItem();
    final HistoryItem mHistoryLastLastWritten = new HistoryItem();
    final HistoryItem mHistoryReadTmp = new HistoryItem();
    final HistoryItem mHistoryAddTmp = new HistoryItem();
    final HashMap<HistoryTag, Integer> mHistoryTagPool = new HashMap<>();
    String[] mReadHistoryStrings;
    int[] mReadHistoryUids;
    int mReadHistoryChars;
    int mNextHistoryTagIdx = 0;
    int mNumHistoryTagChars = 0;
    int mHistoryBufferLastPos = -1;
    boolean mHistoryOverflow = false;
    int mActiveHistoryStates = 0xffffffff;
    int mActiveHistoryStates2 = 0xffffffff;
    long mLastHistoryElapsedRealtime = 0;
    long mTrackRunningHistoryElapsedRealtime = 0;
    long mTrackRunningHistoryUptime = 0;

    final HistoryItem mHistoryCur = new HistoryItem();

    HistoryItem mHistory;
    HistoryItem mHistoryEnd;
    HistoryItem mHistoryLastEnd;
    HistoryItem mHistoryCache;

    // Used by computeHistoryStepDetails
    HistoryStepDetails mLastHistoryStepDetails = null;
    byte mLastHistoryStepLevel = 0;
    final HistoryStepDetails mCurHistoryStepDetails = new HistoryStepDetails();
    final HistoryStepDetails mReadHistoryStepDetails = new HistoryStepDetails();
    final HistoryStepDetails mTmpHistoryStepDetails = new HistoryStepDetails();

    /**
     * Total time (in milliseconds) spent executing in user code.
     */
    long mLastStepCpuUserTime;
    long mCurStepCpuUserTime;
    /**
     * Total time (in milliseconds) spent executing in kernel code.
     */
    long mLastStepCpuSystemTime;
    long mCurStepCpuSystemTime;
    /**
     * Times from /proc/stat (but measured in milliseconds).
     */
    long mLastStepStatUserTime;
    long mLastStepStatSystemTime;
    long mLastStepStatIOWaitTime;
    long mLastStepStatIrqTime;
    long mLastStepStatSoftIrqTime;
    long mLastStepStatIdleTime;
    long mCurStepStatUserTime;
    long mCurStepStatSystemTime;
    long mCurStepStatIOWaitTime;
    long mCurStepStatIrqTime;
    long mCurStepStatSoftIrqTime;
    long mCurStepStatIdleTime;

    private HistoryItem mHistoryIterator;
    private boolean mReadOverflow;
    private boolean mIteratingHistory;

    int mStartCount;

    long mStartClockTime;
    String mStartPlatformVersion;
    String mEndPlatformVersion;

    long mUptime;
    long mUptimeStart;
    long mRealtime;
    long mRealtimeStart;

    int mWakeLockNesting;
    boolean mWakeLockImportant;
    public boolean mRecordAllHistory;
    boolean mNoAutoReset;

    int mScreenState = Display.STATE_UNKNOWN;
    StopwatchTimer mScreenOnTimer;

    int mScreenBrightnessBin = -1;
    final StopwatchTimer[] mScreenBrightnessTimer = new StopwatchTimer[NUM_SCREEN_BRIGHTNESS_BINS];

    boolean mInteractive;
    StopwatchTimer mInteractiveTimer;

    boolean mPowerSaveModeEnabled;
    StopwatchTimer mPowerSaveModeEnabledTimer;

    boolean mDeviceIdling;
    StopwatchTimer mDeviceIdlingTimer;

    boolean mDeviceLightIdling;
    StopwatchTimer mDeviceLightIdlingTimer;

    int mDeviceIdleMode;
    long mLastIdleTimeStart;
    long mLongestLightIdleTime;
    long mLongestFullIdleTime;
    StopwatchTimer mDeviceIdleModeLightTimer;
    StopwatchTimer mDeviceIdleModeFullTimer;

    boolean mPhoneOn;
    StopwatchTimer mPhoneOnTimer;

    int mAudioOnNesting;
    StopwatchTimer mAudioOnTimer;

    int mVideoOnNesting;
    StopwatchTimer mVideoOnTimer;

    int mFlashlightOnNesting;
    StopwatchTimer mFlashlightOnTimer;

    int mCameraOnNesting;
    StopwatchTimer mCameraOnTimer;

    int mPhoneSignalStrengthBin = -1;
    int mPhoneSignalStrengthBinRaw = -1;
    final StopwatchTimer[] mPhoneSignalStrengthsTimer =
            new StopwatchTimer[SignalStrength.NUM_SIGNAL_STRENGTH_BINS];

    StopwatchTimer mPhoneSignalScanningTimer;

    int mPhoneDataConnectionType = -1;
    final StopwatchTimer[] mPhoneDataConnectionsTimer =
            new StopwatchTimer[NUM_DATA_CONNECTION_TYPES];

    final LongSamplingCounter[] mNetworkByteActivityCounters =
            new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];
    final LongSamplingCounter[] mNetworkPacketActivityCounters =
            new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];

    /**
     * The WiFi controller activity (time in tx, rx, idle, and power consumed) for the device.
     */
    ControllerActivityCounterImpl mWifiActivity;

    /**
     * The Bluetooth controller activity (time in tx, rx, idle, and power consumed) for the device.
     */
    ControllerActivityCounterImpl mBluetoothActivity;

    /**
     * The Modem controller activity (time in tx, rx, idle, and power consumed) for the device.
     */
    ControllerActivityCounterImpl mModemActivity;

    /**
     * Whether the device supports WiFi controller energy reporting. This is set to true on
     * the first WiFi energy report. See {@link #mWifiActivity}.
     */
    boolean mHasWifiReporting = false;

    /**
     * Whether the device supports Bluetooth controller energy reporting. This is set to true on
     * the first Bluetooth energy report. See {@link #mBluetoothActivity}.
     */
    boolean mHasBluetoothReporting = false;

    /**
     * Whether the device supports Modem controller energy reporting. This is set to true on
     * the first Modem energy report. See {@link #mModemActivity}.
     */
    boolean mHasModemReporting = false;

    boolean mWifiOn;
    StopwatchTimer mWifiOnTimer;

    boolean mGlobalWifiRunning;
    StopwatchTimer mGlobalWifiRunningTimer;

    int mWifiState = -1;
    final StopwatchTimer[] mWifiStateTimer = new StopwatchTimer[NUM_WIFI_STATES];

    int mWifiSupplState = -1;
    final StopwatchTimer[] mWifiSupplStateTimer = new StopwatchTimer[NUM_WIFI_SUPPL_STATES];

    int mWifiSignalStrengthBin = -1;
    final StopwatchTimer[] mWifiSignalStrengthsTimer =
            new StopwatchTimer[NUM_WIFI_SIGNAL_STRENGTH_BINS];

    int mBluetoothScanNesting;
    StopwatchTimer mBluetoothScanTimer;

    int mMobileRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
    long mMobileRadioActiveStartTime;
    StopwatchTimer mMobileRadioActiveTimer;
    StopwatchTimer mMobileRadioActivePerAppTimer;
    LongSamplingCounter mMobileRadioActiveAdjustedTime;
    LongSamplingCounter mMobileRadioActiveUnknownTime;
    LongSamplingCounter mMobileRadioActiveUnknownCount;

    int mWifiRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;

    /**
     * These provide time bases that discount the time the device is plugged
     * in to power.
     */
    boolean mOnBattery;
    boolean mOnBatteryInternal;

    /**
     * External reporting of whether the device is actually charging.
     */
    boolean mCharging = true;
    int mLastChargingStateLevel;

    /*
     * These keep track of battery levels (1-100) at the last plug event and the last unplug event.
     */
    int mDischargeStartLevel;
    int mDischargeUnplugLevel;
    int mDischargePlugLevel;
    int mDischargeCurrentLevel;
    int mCurrentBatteryLevel;
    int mLowDischargeAmountSinceCharge;
    int mHighDischargeAmountSinceCharge;
    int mDischargeScreenOnUnplugLevel;
    int mDischargeScreenOffUnplugLevel;
    int mDischargeAmountScreenOn;
    int mDischargeAmountScreenOnSinceCharge;
    int mDischargeAmountScreenOff;
    int mDischargeAmountScreenOffSinceCharge;

    private LongSamplingCounter mDischargeScreenOffCounter;
    private LongSamplingCounter mDischargeCounter;

    static final int MAX_LEVEL_STEPS = 200;

    int mInitStepMode = 0;
    int mCurStepMode = 0;
    int mModStepMode = 0;

    int mLastDischargeStepLevel;
    int mMinDischargeStepLevel;
    final LevelStepTracker mDischargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS);
    final LevelStepTracker mDailyDischargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS*2);
    ArrayList<PackageChange> mDailyPackageChanges;

    int mLastChargeStepLevel;
    int mMaxChargeStepLevel;
    final LevelStepTracker mChargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS);
    final LevelStepTracker mDailyChargeStepTracker = new LevelStepTracker(MAX_LEVEL_STEPS*2);

    static final int MAX_DAILY_ITEMS = 10;

    long mDailyStartTime = 0;
    long mNextMinDailyDeadline = 0;
    long mNextMaxDailyDeadline = 0;

    final ArrayList<DailyItem> mDailyItems = new ArrayList<>();

    long mLastWriteTime = 0; // Milliseconds

    private int mPhoneServiceState = -1;
    private int mPhoneServiceStateRaw = -1;
    private int mPhoneSimStateRaw = -1;

    private int mNumConnectivityChange;
    private int mLoadedNumConnectivityChange;
    private int mUnpluggedNumConnectivityChange;

    private int mEstimatedBatteryCapacity = -1;

    private final NetworkStats.Entry mTmpNetworkStatsEntry = new NetworkStats.Entry();

    private PowerProfile mPowerProfile;

    /*
     * Holds a SamplingTimer associated with each kernel wakelock name being tracked.
     */
    private final HashMap<String, SamplingTimer> mKernelWakelockStats = new HashMap<>();

    public Map<String, ? extends Timer> getKernelWakelockStats() {
        return mKernelWakelockStats;
    }

    String mLastWakeupReason = null;
    long mLastWakeupUptimeMs = 0;
    private final HashMap<String, SamplingTimer> mWakeupReasonStats = new HashMap<>();

    public Map<String, ? extends Timer> getWakeupReasonStats() {
        return mWakeupReasonStats;
    }

    @Override
    public LongCounter getDischargeScreenOffCoulombCounter() {
        return mDischargeScreenOffCounter;
    }

    @Override
    public LongCounter getDischargeCoulombCounter() {
        return mDischargeCounter;
    }

    @Override
    public int getEstimatedBatteryCapacity() {
        return mEstimatedBatteryCapacity;
    }

    public BatteryStatsImpl() {
        this(new SystemClocks());
    }

    public BatteryStatsImpl(Clocks clocks) {
        init(clocks);
        mFile = null;
        mCheckinFile = null;
        mDailyFile = null;
        mHandler = null;
        mExternalSync = null;
        mPlatformIdleStateCallback = null;
        clearHistoryLocked();
    }

    private void init(Clocks clocks) {
        mClocks = clocks;
        mMobileNetworkStats = new NetworkStats[] {
                new NetworkStats(mClocks.elapsedRealtime(), 50),
                new NetworkStats(mClocks.elapsedRealtime(), 50),
                new NetworkStats(mClocks.elapsedRealtime(), 50)
        };
        mWifiNetworkStats = new NetworkStats[] {
                new NetworkStats(mClocks.elapsedRealtime(), 50),
                new NetworkStats(mClocks.elapsedRealtime(), 50),
                new NetworkStats(mClocks.elapsedRealtime(), 50)
            };
    }

    public static interface TimeBaseObs {
        void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime);
        void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime);
    }

    // methods are protected not private to be VisibleForTesting
    public static class TimeBase {
        protected final ArrayList<TimeBaseObs> mObservers = new ArrayList<>();

        protected long mUptime;
        protected long mRealtime;

        protected boolean mRunning;

        protected long mPastUptime;
        protected long mUptimeStart;
        protected long mPastRealtime;
        protected long mRealtimeStart;
        protected long mUnpluggedUptime;
        protected long mUnpluggedRealtime;

        public void dump(PrintWriter pw, String prefix) {
            StringBuilder sb = new StringBuilder(128);
            pw.print(prefix); pw.print("mRunning="); pw.println(mRunning);
            sb.setLength(0);
            sb.append(prefix);
                    sb.append("mUptime=");
                    formatTimeMs(sb, mUptime / 1000);
            pw.println(sb.toString());
            sb.setLength(0);
            sb.append(prefix);
                    sb.append("mRealtime=");
                    formatTimeMs(sb, mRealtime / 1000);
            pw.println(sb.toString());
            sb.setLength(0);
            sb.append(prefix);
                    sb.append("mPastUptime=");
                    formatTimeMs(sb, mPastUptime / 1000); sb.append("mUptimeStart=");
                    formatTimeMs(sb, mUptimeStart / 1000);
                    sb.append("mUnpluggedUptime="); formatTimeMs(sb, mUnpluggedUptime / 1000);
            pw.println(sb.toString());
            sb.setLength(0);
            sb.append(prefix);
                    sb.append("mPastRealtime=");
                    formatTimeMs(sb, mPastRealtime / 1000); sb.append("mRealtimeStart=");
                    formatTimeMs(sb, mRealtimeStart / 1000);
                    sb.append("mUnpluggedRealtime="); formatTimeMs(sb, mUnpluggedRealtime / 1000);
            pw.println(sb.toString());
        }

        public void add(TimeBaseObs observer) {
            mObservers.add(observer);
        }

        public void remove(TimeBaseObs observer) {
            if (!mObservers.remove(observer)) {
                Slog.wtf(TAG, "Removed unknown observer: " + observer);
            }
        }

        public boolean hasObserver(TimeBaseObs observer) {
            return mObservers.contains(observer);
        }

        public void init(long uptime, long realtime) {
            mRealtime = 0;
            mUptime = 0;
            mPastUptime = 0;
            mPastRealtime = 0;
            mUptimeStart = uptime;
            mRealtimeStart = realtime;
            mUnpluggedUptime = getUptime(mUptimeStart);
            mUnpluggedRealtime = getRealtime(mRealtimeStart);
        }

        public void reset(long uptime, long realtime) {
            if (!mRunning) {
                mPastUptime = 0;
                mPastRealtime = 0;
            } else {
                mUptimeStart = uptime;
                mRealtimeStart = realtime;
                // TODO: Since mUptimeStart was just reset and we are running, getUptime will
                // just return mPastUptime. Also, are we sure we don't want to reset that?
                mUnpluggedUptime = getUptime(uptime);
                // TODO: likewise.
                mUnpluggedRealtime = getRealtime(realtime);
            }
        }

        public long computeUptime(long curTime, int which) {
            switch (which) {
                case STATS_SINCE_CHARGED:
                    return mUptime + getUptime(curTime);
                case STATS_CURRENT:
                    return getUptime(curTime);
                case STATS_SINCE_UNPLUGGED:
                    return getUptime(curTime) - mUnpluggedUptime;
            }
            return 0;
        }

        public long computeRealtime(long curTime, int which) {
            switch (which) {
                case STATS_SINCE_CHARGED:
                    return mRealtime + getRealtime(curTime);
                case STATS_CURRENT:
                    return getRealtime(curTime);
                case STATS_SINCE_UNPLUGGED:
                    return getRealtime(curTime) - mUnpluggedRealtime;
            }
            return 0;
        }

        public long getUptime(long curTime) {
            long time = mPastUptime;
            if (mRunning) {
                time += curTime - mUptimeStart;
            }
            return time;
        }

        public long getRealtime(long curTime) {
            long time = mPastRealtime;
            if (mRunning) {
                time += curTime - mRealtimeStart;
            }
            return time;
        }

        public long getUptimeStart() {
            return mUptimeStart;
        }

        public long getRealtimeStart() {
            return mRealtimeStart;
        }

        public boolean isRunning() {
            return mRunning;
        }

        public boolean setRunning(boolean running, long uptime, long realtime) {
            if (mRunning != running) {
                mRunning = running;
                if (running) {
                    mUptimeStart = uptime;
                    mRealtimeStart = realtime;
                    long batteryUptime = mUnpluggedUptime = getUptime(uptime);
                    long batteryRealtime = mUnpluggedRealtime = getRealtime(realtime);

                    for (int i = mObservers.size() - 1; i >= 0; i--) {
                        mObservers.get(i).onTimeStarted(realtime, batteryUptime, batteryRealtime);
                    }
                } else {
                    mPastUptime += uptime - mUptimeStart;
                    mPastRealtime += realtime - mRealtimeStart;

                    long batteryUptime = getUptime(uptime);
                    long batteryRealtime = getRealtime(realtime);

                    for (int i = mObservers.size() - 1; i >= 0; i--) {
                        mObservers.get(i).onTimeStopped(realtime, batteryUptime, batteryRealtime);
                    }
                }
                return true;
            }
            return false;
        }

        public void readSummaryFromParcel(Parcel in) {
            mUptime = in.readLong();
            mRealtime = in.readLong();
        }

        public void writeSummaryToParcel(Parcel out, long uptime, long realtime) {
            out.writeLong(computeUptime(uptime, STATS_SINCE_CHARGED));
            out.writeLong(computeRealtime(realtime, STATS_SINCE_CHARGED));
        }

        public void readFromParcel(Parcel in) {
            mRunning = false;
            mUptime = in.readLong();
            mPastUptime = in.readLong();
            mUptimeStart = in.readLong();
            mRealtime = in.readLong();
            mPastRealtime = in.readLong();
            mRealtimeStart = in.readLong();
            mUnpluggedUptime = in.readLong();
            mUnpluggedRealtime = in.readLong();
        }

        public void writeToParcel(Parcel out, long uptime, long realtime) {
            final long runningUptime = getUptime(uptime);
            final long runningRealtime = getRealtime(realtime);
            out.writeLong(mUptime);
            out.writeLong(runningUptime);
            out.writeLong(mUptimeStart);
            out.writeLong(mRealtime);
            out.writeLong(runningRealtime);
            out.writeLong(mRealtimeStart);
            out.writeLong(mUnpluggedUptime);
            out.writeLong(mUnpluggedRealtime);
        }
    }

    /**
     * State for keeping track of counting information.
     */
    public static class Counter extends BatteryStats.Counter implements TimeBaseObs {
        final AtomicInteger mCount = new AtomicInteger();
        final TimeBase mTimeBase;
        int mLoadedCount;
        int mLastCount;
        int mUnpluggedCount;
        int mPluggedCount;

        Counter(TimeBase timeBase, Parcel in) {
            mTimeBase = timeBase;
            mPluggedCount = in.readInt();
            mCount.set(mPluggedCount);
            mLoadedCount = in.readInt();
            mLastCount = 0;
            mUnpluggedCount = in.readInt();
            timeBase.add(this);
        }

        Counter(TimeBase timeBase) {
            mTimeBase = timeBase;
            timeBase.add(this);
        }

        public void writeToParcel(Parcel out) {
            out.writeInt(mCount.get());
            out.writeInt(mLoadedCount);
            out.writeInt(mUnpluggedCount);
        }

        public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
            mUnpluggedCount = mPluggedCount;
            mCount.set(mPluggedCount);
        }

        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            mPluggedCount = mCount.get();
        }

        /**
         * Writes a possibly null Counter to a Parcel.
         *
         * @param out the Parcel to be written to.
         * @param counter a Counter, or null.
         */
        public static void writeCounterToParcel(Parcel out, Counter counter) {
            if (counter == null) {
                out.writeInt(0); // indicates null
                return;
            }
            out.writeInt(1); // indicates non-null

            counter.writeToParcel(out);
        }

        @Override
        public int getCountLocked(int which) {
            int val = mCount.get();
            if (which == STATS_SINCE_UNPLUGGED) {
                val -= mUnpluggedCount;
            } else if (which != STATS_SINCE_CHARGED) {
                val -= mLoadedCount;
            }

            return val;
        }

        public void logState(Printer pw, String prefix) {
            pw.println(prefix + "mCount=" + mCount.get()
                    + " mLoadedCount=" + mLoadedCount + " mLastCount=" + mLastCount
                    + " mUnpluggedCount=" + mUnpluggedCount
                    + " mPluggedCount=" + mPluggedCount);
        }

        void stepAtomic() {
            mCount.incrementAndGet();
        }

        /**
         * Clear state of this counter.
         */
        void reset(boolean detachIfReset) {
            mCount.set(0);
            mLoadedCount = mLastCount = mPluggedCount = mUnpluggedCount = 0;
            if (detachIfReset) {
                detach();
            }
        }

        void detach() {
            mTimeBase.remove(this);
        }

        void writeSummaryFromParcelLocked(Parcel out) {
            int count = mCount.get();
            out.writeInt(count);
        }

        void readSummaryFromParcelLocked(Parcel in) {
            mLoadedCount = in.readInt();
            mCount.set(mLoadedCount);
            mLastCount = 0;
            mUnpluggedCount = mPluggedCount = mLoadedCount;
        }
    }

    public static class LongSamplingCounter extends LongCounter implements TimeBaseObs {
        final TimeBase mTimeBase;
        long mCount;
        long mLoadedCount;
        long mUnpluggedCount;
        long mPluggedCount;

        LongSamplingCounter(TimeBase timeBase, Parcel in) {
            mTimeBase = timeBase;
            mPluggedCount = in.readLong();
            mCount = mPluggedCount;
            mLoadedCount = in.readLong();
            mUnpluggedCount = in.readLong();
            timeBase.add(this);
        }

        LongSamplingCounter(TimeBase timeBase) {
            mTimeBase = timeBase;
            timeBase.add(this);
        }

        public void writeToParcel(Parcel out) {
            out.writeLong(mCount);
            out.writeLong(mLoadedCount);
            out.writeLong(mUnpluggedCount);
        }

        @Override
        public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
            mUnpluggedCount = mPluggedCount;
            mCount = mPluggedCount;
        }

        @Override
        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            mPluggedCount = mCount;
        }

        public long getCountLocked(int which) {
            long val = mTimeBase.isRunning() ? mCount : mPluggedCount;
            if (which == STATS_SINCE_UNPLUGGED) {
                val -= mUnpluggedCount;
            } else if (which != STATS_SINCE_CHARGED) {
                val -= mLoadedCount;
            }
            return val;
        }

        @Override
        public void logState(Printer pw, String prefix) {
            pw.println(prefix + "mCount=" + mCount
                    + " mLoadedCount=" + mLoadedCount
                    + " mUnpluggedCount=" + mUnpluggedCount
                    + " mPluggedCount=" + mPluggedCount);
        }

        void addCountLocked(long count) {
            mCount += count;
        }

        /**
         * Clear state of this counter.
         */
        void reset(boolean detachIfReset) {
            mCount = 0;
            mLoadedCount = mPluggedCount = mUnpluggedCount = 0;
            if (detachIfReset) {
                detach();
            }
        }

        void detach() {
            mTimeBase.remove(this);
        }

        void writeSummaryFromParcelLocked(Parcel out) {
            out.writeLong(mCount);
        }

        void readSummaryFromParcelLocked(Parcel in) {
            mLoadedCount = in.readLong();
            mCount = mLoadedCount;
            mUnpluggedCount = mPluggedCount = mLoadedCount;
        }
    }

    /**
     * State for keeping track of timing information.
     */
    public static abstract class Timer extends BatteryStats.Timer implements TimeBaseObs {
        protected final Clocks mClocks;
        protected final int mType;
        protected final TimeBase mTimeBase;

        protected int mCount;
        protected int mLoadedCount;
        protected int mLastCount;
        protected int mUnpluggedCount;

        // Times are in microseconds for better accuracy when dividing by the
        // lock count, and are in "battery realtime" units.

        /**
         * The total time we have accumulated since the start of the original
         * boot, to the last time something interesting happened in the
         * current run.
         */
        protected long mTotalTime;

        /**
         * The total time we loaded for the previous runs.  Subtract this from
         * mTotalTime to find the time for the current run of the system.
         */
        protected long mLoadedTime;

        /**
         * The run time of the last run of the system, as loaded from the
         * saved data.
         */
        protected long mLastTime;

        /**
         * The value of mTotalTime when unplug() was last called.  Subtract
         * this from mTotalTime to find the time since the last unplug from
         * power.
         */
        protected long mUnpluggedTime;

        /**
         * The total time this timer has been running until the latest mark has been set.
         * Subtract this from mTotalTime to get the time spent running since the mark was set.
         */
        protected long mTimeBeforeMark;

        /**
         * Constructs from a parcel.
         * @param type
         * @param timeBase
         * @param in
         */
        public Timer(Clocks clocks, int type, TimeBase timeBase, Parcel in) {
            mClocks = clocks;
            mType = type;
            mTimeBase = timeBase;

            mCount = in.readInt();
            mLoadedCount = in.readInt();
            mLastCount = 0;
            mUnpluggedCount = in.readInt();
            mTotalTime = in.readLong();
            mLoadedTime = in.readLong();
            mLastTime = 0;
            mUnpluggedTime = in.readLong();
            mTimeBeforeMark = in.readLong();
            timeBase.add(this);
            if (DEBUG) Log.i(TAG, "**** READ TIMER #" + mType + ": mTotalTime=" + mTotalTime);
        }

        public Timer(Clocks clocks, int type, TimeBase timeBase) {
            mClocks = clocks;
            mType = type;
            mTimeBase = timeBase;
            timeBase.add(this);
        }

        protected abstract long computeRunTimeLocked(long curBatteryRealtime);

        protected abstract int computeCurrentCountLocked();

        /**
         * Clear state of this timer.  Returns true if the timer is inactive
         * so can be completely dropped.
         */
        public boolean reset(boolean detachIfReset) {
            mTotalTime = mLoadedTime = mLastTime = mTimeBeforeMark = 0;
            mCount = mLoadedCount = mLastCount = 0;
            if (detachIfReset) {
                detach();
            }
            return true;
        }

        public void detach() {
            mTimeBase.remove(this);
        }

        public void writeToParcel(Parcel out, long elapsedRealtimeUs) {
            if (DEBUG) Log.i(TAG, "**** WRITING TIMER #" + mType + ": mTotalTime="
                    + computeRunTimeLocked(mTimeBase.getRealtime(elapsedRealtimeUs)));
            out.writeInt(computeCurrentCountLocked());
            out.writeInt(mLoadedCount);
            out.writeInt(mUnpluggedCount);
            out.writeLong(computeRunTimeLocked(mTimeBase.getRealtime(elapsedRealtimeUs)));
            out.writeLong(mLoadedTime);
            out.writeLong(mUnpluggedTime);
            out.writeLong(mTimeBeforeMark);
        }

        @Override
        public void onTimeStarted(long elapsedRealtime, long timeBaseUptime, long baseRealtime) {
            if (DEBUG && mType < 0) {
                Log.v(TAG, "unplug #" + mType + ": realtime=" + baseRealtime
                        + " old mUnpluggedTime=" + mUnpluggedTime
                        + " old mUnpluggedCount=" + mUnpluggedCount);
            }
            mUnpluggedTime = computeRunTimeLocked(baseRealtime);
            mUnpluggedCount = computeCurrentCountLocked();
            if (DEBUG && mType < 0) {
                Log.v(TAG, "unplug #" + mType
                        + ": new mUnpluggedTime=" + mUnpluggedTime
                        + " new mUnpluggedCount=" + mUnpluggedCount);
            }
        }

        @Override
        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            if (DEBUG && mType < 0) {
                Log.v(TAG, "plug #" + mType + ": realtime=" + baseRealtime
                        + " old mTotalTime=" + mTotalTime);
            }
            mTotalTime = computeRunTimeLocked(baseRealtime);
            mCount = computeCurrentCountLocked();
            if (DEBUG && mType < 0) {
                Log.v(TAG, "plug #" + mType
                        + ": new mTotalTime=" + mTotalTime);
            }
        }

        /**
         * Writes a possibly null Timer to a Parcel.
         *
         * @param out the Parcel to be written to.
         * @param timer a Timer, or null.
         */
        public static void writeTimerToParcel(Parcel out, Timer timer, long elapsedRealtimeUs) {
            if (timer == null) {
                out.writeInt(0); // indicates null
                return;
            }
            out.writeInt(1); // indicates non-null

            timer.writeToParcel(out, elapsedRealtimeUs);
        }

        @Override
        public long getTotalTimeLocked(long elapsedRealtimeUs, int which) {
            long val = computeRunTimeLocked(mTimeBase.getRealtime(elapsedRealtimeUs));
            if (which == STATS_SINCE_UNPLUGGED) {
                val -= mUnpluggedTime;
            } else if (which != STATS_SINCE_CHARGED) {
                val -= mLoadedTime;
            }

            return val;
        }

        @Override
        public int getCountLocked(int which) {
            int val = computeCurrentCountLocked();
            if (which == STATS_SINCE_UNPLUGGED) {
                val -= mUnpluggedCount;
            } else if (which != STATS_SINCE_CHARGED) {
                val -= mLoadedCount;
            }

            return val;
        }

        @Override
        public long getTimeSinceMarkLocked(long elapsedRealtimeUs) {
            long val = computeRunTimeLocked(mTimeBase.getRealtime(elapsedRealtimeUs));
            return val - mTimeBeforeMark;
        }

        @Override
        public void logState(Printer pw, String prefix) {
            pw.println(prefix + "mCount=" + mCount
                    + " mLoadedCount=" + mLoadedCount + " mLastCount=" + mLastCount
                    + " mUnpluggedCount=" + mUnpluggedCount);
            pw.println(prefix + "mTotalTime=" + mTotalTime
                    + " mLoadedTime=" + mLoadedTime);
            pw.println(prefix + "mLastTime=" + mLastTime
                    + " mUnpluggedTime=" + mUnpluggedTime);
        }


        public void writeSummaryFromParcelLocked(Parcel out, long elapsedRealtimeUs) {
            long runTime = computeRunTimeLocked(mTimeBase.getRealtime(elapsedRealtimeUs));
            out.writeLong(runTime);
            out.writeInt(computeCurrentCountLocked());
        }

        public void readSummaryFromParcelLocked(Parcel in) {
            // Multiply by 1000 for backwards compatibility
            mTotalTime = mLoadedTime = in.readLong();
            mLastTime = 0;
            mUnpluggedTime = mTotalTime;
            mCount = mLoadedCount = in.readInt();
            mLastCount = 0;
            mUnpluggedCount = mCount;

            // When reading the summary, we set the mark to be the latest information.
            mTimeBeforeMark = mTotalTime;
        }
    }

    /**
     * A counter meant to accept monotonically increasing values to its {@link #update(long, int)}
     * method. The state of the timer according to its {@link TimeBase} will determine how much
     * of the value is recorded.
     *
     * If the value being recorded resets, {@link #endSample()} can be called in order to
     * account for the change. If the value passed in to {@link #update(long, int)} decreased
     * between calls, the {@link #endSample()} is automatically called and the new value is
     * expected to increase monotonically from that point on.
     */
    public static class SamplingTimer extends Timer {

        /**
         * The most recent reported count from /proc/wakelocks.
         */
        int mCurrentReportedCount;

        /**
         * The reported count from /proc/wakelocks when unplug() was last
         * called.
         */
        int mUnpluggedReportedCount;

        /**
         * The most recent reported total_time from /proc/wakelocks.
         */
        long mCurrentReportedTotalTime;


        /**
         * The reported total_time from /proc/wakelocks when unplug() was last
         * called.
         */
        long mUnpluggedReportedTotalTime;

        /**
         * Whether we are currently in a discharge cycle.
         */
        boolean mTimeBaseRunning;

        /**
         * Whether we are currently recording reported values.
         */
        boolean mTrackingReportedValues;

        /*
         * A sequence counter, incremented once for each update of the stats.
         */
        int mUpdateVersion;

        @VisibleForTesting
        public SamplingTimer(Clocks clocks, TimeBase timeBase, Parcel in) {
            super(clocks, 0, timeBase, in);
            mCurrentReportedCount = in.readInt();
            mUnpluggedReportedCount = in.readInt();
            mCurrentReportedTotalTime = in.readLong();
            mUnpluggedReportedTotalTime = in.readLong();
            mTrackingReportedValues = in.readInt() == 1;
            mTimeBaseRunning = timeBase.isRunning();
        }

        @VisibleForTesting
        public SamplingTimer(Clocks clocks, TimeBase timeBase) {
            super(clocks, 0, timeBase);
            mTrackingReportedValues = false;
            mTimeBaseRunning = timeBase.isRunning();
        }

        /**
         * Ends the current sample, allowing subsequent values to {@link #update(long, int)} to
         * be less than the values used for a previous invocation.
         */
        public void endSample() {
            mTotalTime = computeRunTimeLocked(0 /* unused by us */);
            mCount = computeCurrentCountLocked();
            mUnpluggedReportedTotalTime = mCurrentReportedTotalTime = 0;
            mUnpluggedReportedCount = mCurrentReportedCount = 0;
        }

        public void setUpdateVersion(int version) {
            mUpdateVersion = version;
        }

        public int getUpdateVersion() {
            return mUpdateVersion;
        }

        /**
         * Updates the current recorded values. These are meant to be monotonically increasing
         * and cumulative. If you are dealing with deltas, use {@link #add(long, int)}.
         *
         * If the values being recorded have been reset, the monotonically increasing requirement
         * will be broken. In this case, {@link #endSample()} is automatically called and
         * the total value of totalTime and count are recorded, starting a new monotonically
         * increasing sample.
         *
         * @param totalTime total time of sample in microseconds.
         * @param count total number of times the event being sampled occurred.
         */
        public void update(long totalTime, int count) {
            if (mTimeBaseRunning && !mTrackingReportedValues) {
                // Updating the reported value for the first time.
                mUnpluggedReportedTotalTime = totalTime;
                mUnpluggedReportedCount = count;
            }

            mTrackingReportedValues = true;

            if (totalTime < mCurrentReportedTotalTime || count < mCurrentReportedCount) {
                endSample();
            }

            mCurrentReportedTotalTime = totalTime;
            mCurrentReportedCount = count;
        }

        /**
         * Adds deltaTime and deltaCount to the current sample.
         *
         * @param deltaTime additional time recorded since the last sampled event, in microseconds.
         * @param deltaCount additional number of times the event being sampled occurred.
         */
        public void add(long deltaTime, int deltaCount) {
            update(mCurrentReportedTotalTime + deltaTime, mCurrentReportedCount + deltaCount);
        }

        @Override
        public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
            super.onTimeStarted(elapsedRealtime, baseUptime, baseRealtime);
            if (mTrackingReportedValues) {
                mUnpluggedReportedTotalTime = mCurrentReportedTotalTime;
                mUnpluggedReportedCount = mCurrentReportedCount;
            }
            mTimeBaseRunning = true;
        }

        @Override
        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            super.onTimeStopped(elapsedRealtime, baseUptime, baseRealtime);
            mTimeBaseRunning = false;
        }

        @Override
        public void logState(Printer pw, String prefix) {
            super.logState(pw, prefix);
            pw.println(prefix + "mCurrentReportedCount=" + mCurrentReportedCount
                    + " mUnpluggedReportedCount=" + mUnpluggedReportedCount
                    + " mCurrentReportedTotalTime=" + mCurrentReportedTotalTime
                    + " mUnpluggedReportedTotalTime=" + mUnpluggedReportedTotalTime);
        }

        @Override
        protected long computeRunTimeLocked(long curBatteryRealtime) {
            return mTotalTime + (mTimeBaseRunning && mTrackingReportedValues
                    ? mCurrentReportedTotalTime - mUnpluggedReportedTotalTime : 0);
        }

        @Override
        protected int computeCurrentCountLocked() {
            return mCount + (mTimeBaseRunning && mTrackingReportedValues
                    ? mCurrentReportedCount - mUnpluggedReportedCount : 0);
        }

        @Override
        public void writeToParcel(Parcel out, long elapsedRealtimeUs) {
            super.writeToParcel(out, elapsedRealtimeUs);
            out.writeInt(mCurrentReportedCount);
            out.writeInt(mUnpluggedReportedCount);
            out.writeLong(mCurrentReportedTotalTime);
            out.writeLong(mUnpluggedReportedTotalTime);
            out.writeInt(mTrackingReportedValues ? 1 : 0);
        }

        @Override
        public boolean reset(boolean detachIfReset) {
            super.reset(detachIfReset);
            mTrackingReportedValues = false;
            mUnpluggedReportedTotalTime = 0;
            mUnpluggedReportedCount = 0;
            return true;
        }
    }

    /**
     * A timer that increments in batches.  It does not run for durations, but just jumps
     * for a pre-determined amount.
     */
    public static class BatchTimer extends Timer {
        final Uid mUid;

        /**
         * The last time at which we updated the timer.  This is in elapsed realtime microseconds.
         */
        long mLastAddedTime;

        /**
         * The last duration that we added to the timer.  This is in microseconds.
         */
        long mLastAddedDuration;

        /**
         * Whether we are currently in a discharge cycle.
         */
        boolean mInDischarge;

        BatchTimer(Clocks clocks, Uid uid, int type, TimeBase timeBase, Parcel in) {
            super(clocks, type, timeBase, in);
            mUid = uid;
            mLastAddedTime = in.readLong();
            mLastAddedDuration = in.readLong();
            mInDischarge = timeBase.isRunning();
        }

        BatchTimer(Clocks clocks, Uid uid, int type, TimeBase timeBase) {
            super(clocks, type, timeBase);
            mUid = uid;
            mInDischarge = timeBase.isRunning();
        }

        @Override
        public void writeToParcel(Parcel out, long elapsedRealtimeUs) {
            super.writeToParcel(out, elapsedRealtimeUs);
            out.writeLong(mLastAddedTime);
            out.writeLong(mLastAddedDuration);
        }

        @Override
        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            recomputeLastDuration(mClocks.elapsedRealtime() * 1000, false);
            mInDischarge = false;
            super.onTimeStopped(elapsedRealtime, baseUptime, baseRealtime);
        }

        @Override
        public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
            recomputeLastDuration(elapsedRealtime, false);
            mInDischarge = true;
            // If we are still within the last added duration, then re-added whatever remains.
            if (mLastAddedTime == elapsedRealtime) {
                mTotalTime += mLastAddedDuration;
            }
            super.onTimeStarted(elapsedRealtime, baseUptime, baseRealtime);
        }

        @Override
        public void logState(Printer pw, String prefix) {
            super.logState(pw, prefix);
            pw.println(prefix + "mLastAddedTime=" + mLastAddedTime
                    + " mLastAddedDuration=" + mLastAddedDuration);
        }

        private long computeOverage(long curTime) {
            if (mLastAddedTime > 0) {
                return mLastTime + mLastAddedDuration - curTime;
            }
            return 0;
        }

        private void recomputeLastDuration(long curTime, boolean abort) {
            final long overage = computeOverage(curTime);
            if (overage > 0) {
                // Aborting before the duration ran out -- roll back the remaining
                // duration.  Only do this if currently discharging; otherwise we didn't
                // actually add the time.
                if (mInDischarge) {
                    mTotalTime -= overage;
                }
                if (abort) {
                    mLastAddedTime = 0;
                } else {
                    mLastAddedTime = curTime;
                    mLastAddedDuration -= overage;
                }
            }
        }

        public void addDuration(BatteryStatsImpl stats, long durationMillis) {
            final long now = mClocks.elapsedRealtime() * 1000;
            recomputeLastDuration(now, true);
            mLastAddedTime = now;
            mLastAddedDuration = durationMillis * 1000;
            if (mInDischarge) {
                mTotalTime += mLastAddedDuration;
                mCount++;
            }
        }

        public void abortLastDuration(BatteryStatsImpl stats) {
            final long now = mClocks.elapsedRealtime() * 1000;
            recomputeLastDuration(now, true);
        }

        @Override
        protected int computeCurrentCountLocked() {
            return mCount;
        }

        @Override
        protected long computeRunTimeLocked(long curBatteryRealtime) {
            final long overage = computeOverage(mClocks.elapsedRealtime() * 1000);
            if (overage > 0) {
                return mTotalTime = overage;
            }
            return mTotalTime;
        }

        @Override
        public boolean reset(boolean detachIfReset) {
            final long now = mClocks.elapsedRealtime() * 1000;
            recomputeLastDuration(now, true);
            boolean stillActive = mLastAddedTime == now;
            super.reset(!stillActive && detachIfReset);
            return !stillActive;
        }
    }


    /**
     * A StopwatchTimer that also tracks the total and max individual
     * time spent active according to the given timebase.  Whereas
     * StopwatchTimer apportions the time amongst all in the pool,
     * the total and max durations are not apportioned.
     */
    public static class DurationTimer extends StopwatchTimer {
        /**
         * The time (in ms) that the timer was last acquired or the time base
         * last (re-)started. Increasing the nesting depth does not reset this time.
         *
         * -1 if the timer is currently not running or the time base is not running.
         *
         * If written to a parcel, the start time is reset, as is mNesting in the base class
         * StopwatchTimer.
         */
        long mStartTimeMs = -1;

        /**
         * The longest time period (in ms) that the timer has been active.
         */
        long mMaxDurationMs;

        /**
         * The total time (in ms) that that the timer has been active since reset().
         */
        long mCurrentDurationMs;

        public DurationTimer(Clocks clocks, Uid uid, int type, ArrayList<StopwatchTimer> timerPool,
                TimeBase timeBase, Parcel in) {
            super(clocks, uid, type, timerPool, timeBase, in);
            mMaxDurationMs = in.readLong();
        }

        public DurationTimer(Clocks clocks, Uid uid, int type, ArrayList<StopwatchTimer> timerPool,
                TimeBase timeBase) {
            super(clocks, uid, type, timerPool, timeBase);
        }

        @Override
        public void writeToParcel(Parcel out, long elapsedRealtimeUs) {
            super.writeToParcel(out, elapsedRealtimeUs);
            out.writeLong(mMaxDurationMs);
        }

        /**
         * Write the summary to the parcel.
         *
         * Since the time base is probably meaningless after we come back, reading
         * from this will have the effect of stopping the timer. So here all we write
         * is the max duration.
         */
        @Override
        public void writeSummaryFromParcelLocked(Parcel out, long elapsedRealtimeUs) {
            super.writeSummaryFromParcelLocked(out, elapsedRealtimeUs);
            out.writeLong(mMaxDurationMs);
        }

        /**
         * Read the summary parcel.
         *
         * Has the side effect of stopping the timer.
         */
        @Override
        public void readSummaryFromParcelLocked(Parcel in) {
            super.readSummaryFromParcelLocked(in);
            mMaxDurationMs = in.readLong();
            mStartTimeMs = -1;
            mCurrentDurationMs = 0;
        }

        /**
         * The TimeBase time started (again).
         *
         * If the timer is also running, store the start time.
         */
        public void onTimeStarted(long elapsedRealtimeUs, long baseUptime, long baseRealtime) {
            super.onTimeStarted(elapsedRealtimeUs, baseUptime, baseRealtime);
            if (mNesting > 0) {
                mStartTimeMs = mTimeBase.getRealtime(mClocks.elapsedRealtime()*1000) / 1000;
            }
        }

        /**
         * The TimeBase stopped running.
         *
         * If the timer is running, add the duration into mCurrentDurationMs.
         */
        @Override
        public void onTimeStopped(long elapsedRealtimeUs, long baseUptime, long baseRealtime) {
            super.onTimeStopped(elapsedRealtimeUs, baseUptime, baseRealtime);
            if (mNesting > 0) {
                mCurrentDurationMs += (elapsedRealtimeUs / 1000) - mStartTimeMs;
            }
            mStartTimeMs = -1;
        }

        @Override
        public void logState(Printer pw, String prefix) {
            super.logState(pw, prefix);
        }

        @Override
        public void startRunningLocked(long elapsedRealtimeMs) {
            super.startRunningLocked(elapsedRealtimeMs);
            if (mNesting == 1 && mTimeBase.isRunning()) {
                // Just started
                mStartTimeMs = mTimeBase.getRealtime(mClocks.elapsedRealtime()*1000) / 1000;
            }
        }

        /**
         * Decrements the mNesting ref-count on this timer.
         *
         * If it actually stopped (mNesting went to 0), then possibly update
         * mMaxDuration if the current duration was the longest ever.
         */
        @Override
        public void stopRunningLocked(long elapsedRealtimeMs) {
            super.stopRunningLocked(elapsedRealtimeMs);
            if (mNesting == 0) {
                final long durationMs = getCurrentDurationMsLocked(elapsedRealtimeMs);
                if (durationMs > mMaxDurationMs) {
                    mMaxDurationMs = durationMs;
                }
                mStartTimeMs = -1;
                mCurrentDurationMs = 0;
            }
        }

        @Override
        public boolean reset(boolean detachIfReset) {
            boolean result = super.reset(detachIfReset);
            mMaxDurationMs = 0;
            mCurrentDurationMs = 0;
            if (mNesting > 0) {
                mStartTimeMs = mTimeBase.getRealtime(mClocks.elapsedRealtime()*1000) / 1000;
            } else {
                mStartTimeMs = -1;
            }
            return result;
        }

        /**
         * Returns the max duration that this timer has ever seen.
         *
         * Note that this time is NOT split between the timers in the timer group that
         * this timer is attached to.  It is the TOTAL time.
         */
        @Override
        public long getMaxDurationMsLocked(long elapsedRealtimeMs) {
            if (mNesting > 0) {
                final long durationMs = getCurrentDurationMsLocked(elapsedRealtimeMs);
                if (durationMs > mMaxDurationMs) {
                    return durationMs;
                }
            }
            return mMaxDurationMs;
        }

        /**
         * Returns the time since the timer was started.
         *
         * Note that this time is NOT split between the timers in the timer group that
         * this timer is attached to.  It is the TOTAL time.
         */
        @Override
        public long getCurrentDurationMsLocked(long elapsedRealtimeMs) {
            long durationMs = mCurrentDurationMs;
            if (mNesting > 0) {
                if (mTimeBase.isRunning()) {
                    durationMs += (mTimeBase.getRealtime(elapsedRealtimeMs*1000)/1000)
                            - mStartTimeMs;
                }
            }
            return durationMs;
        }
    }

    /**
     * State for keeping track of timing information.
     */
    public static class StopwatchTimer extends Timer {
        final Uid mUid;
        final ArrayList<StopwatchTimer> mTimerPool;

        int mNesting;

        /**
         * The last time at which we updated the timer.  If mNesting is > 0,
         * subtract this from the current battery time to find the amount of
         * time we have been running since we last computed an update.
         */
        long mUpdateTime;

        /**
         * The total time at which the timer was acquired, to determine if it
         * was actually held for an interesting duration.
         */
        long mAcquireTime;

        long mTimeout;

        /**
         * For partial wake locks, keep track of whether we are in the list
         * to consume CPU cycles.
         */
        boolean mInList;

        public StopwatchTimer(Clocks clocks, Uid uid, int type, ArrayList<StopwatchTimer> timerPool,
                TimeBase timeBase, Parcel in) {
            super(clocks, type, timeBase, in);
            mUid = uid;
            mTimerPool = timerPool;
            mUpdateTime = in.readLong();
        }

        public StopwatchTimer(Clocks clocks, Uid uid, int type, ArrayList<StopwatchTimer> timerPool,
                TimeBase timeBase) {
            super(clocks, type, timeBase);
            mUid = uid;
            mTimerPool = timerPool;
        }

        public void setTimeout(long timeout) {
            mTimeout = timeout;
        }

        public void writeToParcel(Parcel out, long elapsedRealtimeUs) {
            super.writeToParcel(out, elapsedRealtimeUs);
            out.writeLong(mUpdateTime);
        }

        public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            if (mNesting > 0) {
                if (DEBUG && mType < 0) {
                    Log.v(TAG, "old mUpdateTime=" + mUpdateTime);
                }
                super.onTimeStopped(elapsedRealtime, baseUptime, baseRealtime);
                mUpdateTime = baseRealtime;
                if (DEBUG && mType < 0) {
                    Log.v(TAG, "new mUpdateTime=" + mUpdateTime);
                }
            }
        }

        public void logState(Printer pw, String prefix) {
            super.logState(pw, prefix);
            pw.println(prefix + "mNesting=" + mNesting + " mUpdateTime=" + mUpdateTime
                    + " mAcquireTime=" + mAcquireTime);
        }

        public void startRunningLocked(long elapsedRealtimeMs) {
            if (mNesting++ == 0) {
                final long batteryRealtime = mTimeBase.getRealtime(elapsedRealtimeMs * 1000);
                mUpdateTime = batteryRealtime;
                if (mTimerPool != null) {
                    // Accumulate time to all currently active timers before adding
                    // this new one to the pool.
                    refreshTimersLocked(batteryRealtime, mTimerPool, null);
                    // Add this timer to the active pool
                    mTimerPool.add(this);
                }
                // Increment the count
                mCount++;
                mAcquireTime = mTotalTime;
                if (DEBUG && mType < 0) {
                    Log.v(TAG, "start #" + mType + ": mUpdateTime=" + mUpdateTime
                            + " mTotalTime=" + mTotalTime + " mCount=" + mCount
                            + " mAcquireTime=" + mAcquireTime);
                }
            }
        }

        public boolean isRunningLocked() {
            return mNesting > 0;
        }

        public void stopRunningLocked(long elapsedRealtimeMs) {
            // Ignore attempt to stop a timer that isn't running
            if (mNesting == 0) {
                return;
            }
            if (--mNesting == 0) {
                final long batteryRealtime = mTimeBase.getRealtime(elapsedRealtimeMs * 1000);
                if (mTimerPool != null) {
                    // Accumulate time to all active counters, scaled by the total
                    // active in the pool, before taking this one out of the pool.
                    refreshTimersLocked(batteryRealtime, mTimerPool, null);
                    // Remove this timer from the active pool
                    mTimerPool.remove(this);
                } else {
                    mNesting = 1;
                    mTotalTime = computeRunTimeLocked(batteryRealtime);
                    mNesting = 0;
                }

                if (DEBUG && mType < 0) {
                    Log.v(TAG, "stop #" + mType + ": mUpdateTime=" + mUpdateTime
                            + " mTotalTime=" + mTotalTime + " mCount=" + mCount
                            + " mAcquireTime=" + mAcquireTime);
                }

                if (mTotalTime == mAcquireTime) {
                    // If there was no change in the time, then discard this
                    // count.  A somewhat cheezy strategy, but hey.
                    mCount--;
                }
            }
        }

        public void stopAllRunningLocked(long elapsedRealtimeMs) {
            if (mNesting > 0) {
                mNesting = 1;
                stopRunningLocked(elapsedRealtimeMs);
            }
        }

        // Update the total time for all other running Timers with the same type as this Timer
        // due to a change in timer count
        private static long refreshTimersLocked(long batteryRealtime,
                final ArrayList<StopwatchTimer> pool, StopwatchTimer self) {
            long selfTime = 0;
            final int N = pool.size();
            for (int i=N-1; i>= 0; i--) {
                final StopwatchTimer t = pool.get(i);
                long heldTime = batteryRealtime - t.mUpdateTime;
                if (heldTime > 0) {
                    final long myTime = heldTime / N;
                    if (t == self) {
                        selfTime = myTime;
                    }
                    t.mTotalTime += myTime;
                }
                t.mUpdateTime = batteryRealtime;
            }
            return selfTime;
        }

        @Override
        protected long computeRunTimeLocked(long curBatteryRealtime) {
            if (mTimeout > 0 && curBatteryRealtime > mUpdateTime + mTimeout) {
                curBatteryRealtime = mUpdateTime + mTimeout;
            }
            return mTotalTime + (mNesting > 0
                    ? (curBatteryRealtime - mUpdateTime)
                            / (mTimerPool != null ? mTimerPool.size() : 1)
                    : 0);
        }

        @Override
        protected int computeCurrentCountLocked() {
            return mCount;
        }

        @Override
        public boolean reset(boolean detachIfReset) {
            boolean canDetach = mNesting <= 0;
            super.reset(canDetach && detachIfReset);
            if (mNesting > 0) {
                mUpdateTime = mTimeBase.getRealtime(mClocks.elapsedRealtime() * 1000);
            }
            mAcquireTime = mTotalTime;
            return canDetach;
        }

        @Override
        public void detach() {
            super.detach();
            if (mTimerPool != null) {
                mTimerPool.remove(this);
            }
        }

        @Override
        public void readSummaryFromParcelLocked(Parcel in) {
            super.readSummaryFromParcelLocked(in);
            mNesting = 0;
        }

        /**
         * Set the mark so that we can query later for the total time the timer has
         * accumulated since this point. The timer can be running or not.
         *
         * @param elapsedRealtimeMs the current elapsed realtime in milliseconds.
         */
        public void setMark(long elapsedRealtimeMs) {
            final long batteryRealtime = mTimeBase.getRealtime(elapsedRealtimeMs * 1000);
            if (mNesting > 0) {
                // We are running.
                if (mTimerPool != null) {
                    refreshTimersLocked(batteryRealtime, mTimerPool, this);
                } else {
                    mTotalTime += batteryRealtime - mUpdateTime;
                    mUpdateTime = batteryRealtime;
                }
            }
            mTimeBeforeMark = mTotalTime;
        }
    }

    public abstract class OverflowArrayMap<T> {
        private static final String OVERFLOW_NAME = "*overflow*";

        final int mUid;
        final ArrayMap<String, T> mMap = new ArrayMap<>();
        T mCurOverflow;
        ArrayMap<String, MutableInt> mActiveOverflow;
        long mLastOverflowTime;
        long mLastOverflowFinishTime;
        long mLastClearTime;
        long mLastCleanupTime;

        public OverflowArrayMap(int uid) {
            mUid = uid;
        }

        public ArrayMap<String, T> getMap() {
            return mMap;
        }

        public void clear() {
            mLastClearTime = SystemClock.elapsedRealtime();
            mMap.clear();
            mCurOverflow = null;
            mActiveOverflow = null;
        }

        public void add(String name, T obj) {
            if (name == null) {
                name = "";
            }
            mMap.put(name, obj);
            if (OVERFLOW_NAME.equals(name)) {
                mCurOverflow = obj;
            }
        }

        public void cleanup() {
            mLastCleanupTime = SystemClock.elapsedRealtime();
            if (mActiveOverflow != null) {
                if (mActiveOverflow.size() == 0) {
                    mActiveOverflow = null;
                }
            }
            if (mActiveOverflow == null) {
                // There is no currently active overflow, so we should no longer have
                // an overflow entry.
                if (mMap.containsKey(OVERFLOW_NAME)) {
                    Slog.wtf(TAG, "Cleaning up with no active overflow, but have overflow entry "
                            + mMap.get(OVERFLOW_NAME));
                    mMap.remove(OVERFLOW_NAME);
                }
                mCurOverflow = null;
            } else {
                // There is currently active overflow, so we should still have an overflow entry.
                if (mCurOverflow == null || !mMap.containsKey(OVERFLOW_NAME)) {
                    Slog.wtf(TAG, "Cleaning up with active overflow, but no overflow entry: cur="
                            + mCurOverflow + " map=" + mMap.get(OVERFLOW_NAME));
                }
            }
        }

        public T startObject(String name) {
            if (name == null) {
                name = "";
            }
            T obj = mMap.get(name);
            if (obj != null) {
                return obj;
            }

            // No object exists for the given name, but do we currently have it
            // running as part of the overflow?
            if (mActiveOverflow != null) {
                MutableInt over = mActiveOverflow.get(name);
                if (over != null) {
                    // We are already actively counting this name in the overflow object.
                    obj = mCurOverflow;
                    if (obj == null) {
                        // Shouldn't be here, but we'll try to recover.
                        Slog.wtf(TAG, "Have active overflow " + name + " but null overflow");
                        obj = mCurOverflow = instantiateObject();
                        mMap.put(OVERFLOW_NAME, obj);
                    }
                    over.value++;
                    return obj;
                }
            }

            // No object exists for given name nor in the overflow; we need to make
            // a new one.
            final int N = mMap.size();
            if (N >= MAX_WAKELOCKS_PER_UID) {
                // Went over the limit on number of objects to track; this one goes
                // in to the overflow.
                obj = mCurOverflow;
                if (obj == null) {
                    // Need to start overflow now...
                    obj = mCurOverflow = instantiateObject();
                    mMap.put(OVERFLOW_NAME, obj);
                }
                if (mActiveOverflow == null) {
                    mActiveOverflow = new ArrayMap<>();
                }
                mActiveOverflow.put(name, new MutableInt(1));
                mLastOverflowTime = SystemClock.elapsedRealtime();
                return obj;
            }

            // Normal case where we just need to make a new object.
            obj = instantiateObject();
            mMap.put(name, obj);
            return obj;
        }

        public T stopObject(String name) {
            if (name == null) {
                name = "";
            }
            T obj = mMap.get(name);
            if (obj != null) {
                return obj;
            }

            // No object exists for the given name, but do we currently have it
            // running as part of the overflow?
            if (mActiveOverflow != null) {
                MutableInt over = mActiveOverflow.get(name);
                if (over != null) {
                    // We are already actively counting this name in the overflow object.
                    obj = mCurOverflow;
                    if (obj != null) {
                        over.value--;
                        if (over.value <= 0) {
                            mActiveOverflow.remove(name);
                            mLastOverflowFinishTime = SystemClock.elapsedRealtime();
                        }
                        return obj;
                    }
                }
            }

            // Huh, they are stopping an active operation but we can't find one!
            // That's not good.
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to find object for ");
            sb.append(name);
            sb.append(" in uid ");
            sb.append(mUid);
            sb.append(" mapsize=");
            sb.append(mMap.size());
            sb.append(" activeoverflow=");
            sb.append(mActiveOverflow);
            sb.append(" curoverflow=");
            sb.append(mCurOverflow);
            long now = SystemClock.elapsedRealtime();
            if (mLastOverflowTime != 0) {
                sb.append(" lastOverflowTime=");
                TimeUtils.formatDuration(mLastOverflowTime-now, sb);
            }
            if (mLastOverflowFinishTime != 0) {
                sb.append(" lastOverflowFinishTime=");
                TimeUtils.formatDuration(mLastOverflowFinishTime-now, sb);
            }
            if (mLastClearTime != 0) {
                sb.append(" lastClearTime=");
                TimeUtils.formatDuration(mLastClearTime-now, sb);
            }
            if (mLastCleanupTime != 0) {
                sb.append(" lastCleanupTime=");
                TimeUtils.formatDuration(mLastCleanupTime-now, sb);
            }
            Slog.wtf(TAG, sb.toString());
            return null;
        }

        public abstract T instantiateObject();
    }

    public static class ControllerActivityCounterImpl extends ControllerActivityCounter
            implements Parcelable {
        private final LongSamplingCounter mIdleTimeMillis;
        private final LongSamplingCounter mRxTimeMillis;
        private final LongSamplingCounter[] mTxTimeMillis;
        private final LongSamplingCounter mPowerDrainMaMs;

        public ControllerActivityCounterImpl(TimeBase timeBase, int numTxStates) {
            mIdleTimeMillis = new LongSamplingCounter(timeBase);
            mRxTimeMillis = new LongSamplingCounter(timeBase);
            mTxTimeMillis = new LongSamplingCounter[numTxStates];
            for (int i = 0; i < numTxStates; i++) {
                mTxTimeMillis[i] = new LongSamplingCounter(timeBase);
            }
            mPowerDrainMaMs = new LongSamplingCounter(timeBase);
        }

        public ControllerActivityCounterImpl(TimeBase timeBase, int numTxStates, Parcel in) {
            mIdleTimeMillis = new LongSamplingCounter(timeBase, in);
            mRxTimeMillis = new LongSamplingCounter(timeBase, in);
            final int recordedTxStates = in.readInt();
            if (recordedTxStates != numTxStates) {
                throw new ParcelFormatException("inconsistent tx state lengths");
            }

            mTxTimeMillis = new LongSamplingCounter[numTxStates];
            for (int i = 0; i < numTxStates; i++) {
                mTxTimeMillis[i] = new LongSamplingCounter(timeBase, in);
            }
            mPowerDrainMaMs = new LongSamplingCounter(timeBase, in);
        }

        public void readSummaryFromParcel(Parcel in) {
            mIdleTimeMillis.readSummaryFromParcelLocked(in);
            mRxTimeMillis.readSummaryFromParcelLocked(in);
            final int recordedTxStates = in.readInt();
            if (recordedTxStates != mTxTimeMillis.length) {
                throw new ParcelFormatException("inconsistent tx state lengths");
            }
            for (LongSamplingCounter counter : mTxTimeMillis) {
                counter.readSummaryFromParcelLocked(in);
            }
            mPowerDrainMaMs.readSummaryFromParcelLocked(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public void writeSummaryToParcel(Parcel dest) {
            mIdleTimeMillis.writeSummaryFromParcelLocked(dest);
            mRxTimeMillis.writeSummaryFromParcelLocked(dest);
            dest.writeInt(mTxTimeMillis.length);
            for (LongSamplingCounter counter : mTxTimeMillis) {
                counter.writeSummaryFromParcelLocked(dest);
            }
            mPowerDrainMaMs.writeSummaryFromParcelLocked(dest);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            mIdleTimeMillis.writeToParcel(dest);
            mRxTimeMillis.writeToParcel(dest);
            dest.writeInt(mTxTimeMillis.length);
            for (LongSamplingCounter counter : mTxTimeMillis) {
                counter.writeToParcel(dest);
            }
            mPowerDrainMaMs.writeToParcel(dest);
        }

        public void reset(boolean detachIfReset) {
            mIdleTimeMillis.reset(detachIfReset);
            mRxTimeMillis.reset(detachIfReset);
            for (LongSamplingCounter counter : mTxTimeMillis) {
                counter.reset(detachIfReset);
            }
            mPowerDrainMaMs.reset(detachIfReset);
        }

        public void detach() {
            mIdleTimeMillis.detach();
            mRxTimeMillis.detach();
            for (LongSamplingCounter counter : mTxTimeMillis) {
                counter.detach();
            }
            mPowerDrainMaMs.detach();
        }

        /**
         * @return a LongSamplingCounter, measuring time spent in the idle state in
         * milliseconds.
         */
        @Override
        public LongSamplingCounter getIdleTimeCounter() {
            return mIdleTimeMillis;
        }

        /**
         * @return a LongSamplingCounter, measuring time spent in the receive state in
         * milliseconds.
         */
        @Override
        public LongSamplingCounter getRxTimeCounter() {
            return mRxTimeMillis;
        }

        /**
         * @return a LongSamplingCounter[], measuring time spent in various transmit states in
         * milliseconds.
         */
        @Override
        public LongSamplingCounter[] getTxTimeCounters() {
            return mTxTimeMillis;
        }

        /**
         * @return a LongSamplingCounter, measuring power use in milli-ampere milliseconds (mAmS).
         */
        @Override
        public LongSamplingCounter getPowerCounter() {
            return mPowerDrainMaMs;
        }
    }

    /*
     * Get the wakeup reason counter, and create a new one if one
     * doesn't already exist.
     */
    public SamplingTimer getWakeupReasonTimerLocked(String name) {
        SamplingTimer timer = mWakeupReasonStats.get(name);
        if (timer == null) {
            timer = new SamplingTimer(mClocks, mOnBatteryTimeBase);
            mWakeupReasonStats.put(name, timer);
        }
        return timer;
    }

    /*
     * Get the KernelWakelockTimer associated with name, and create a new one if one
     * doesn't already exist.
     */
    public SamplingTimer getKernelWakelockTimerLocked(String name) {
        SamplingTimer kwlt = mKernelWakelockStats.get(name);
        if (kwlt == null) {
            kwlt = new SamplingTimer(mClocks, mOnBatteryScreenOffTimeBase);
            mKernelWakelockStats.put(name, kwlt);
        }
        return kwlt;
    }

    private int writeHistoryTag(HistoryTag tag) {
        Integer idxObj = mHistoryTagPool.get(tag);
        int idx;
        if (idxObj != null) {
            idx = idxObj;
        } else {
            idx = mNextHistoryTagIdx;
            HistoryTag key = new HistoryTag();
            key.setTo(tag);
            tag.poolIdx = idx;
            mHistoryTagPool.put(key, idx);
            mNextHistoryTagIdx++;
            mNumHistoryTagChars += key.string.length() + 1;
        }
        return idx;
    }

    private void readHistoryTag(int index, HistoryTag tag) {
        tag.string = mReadHistoryStrings[index];
        tag.uid = mReadHistoryUids[index];
        tag.poolIdx = index;
    }

    /*
        The history delta format uses flags to denote further data in subsequent ints in the parcel.

        There is always the first token, which may contain the delta time, or an indicator of
        the length of the time (int or long) following this token.

        First token: always present,
        31              23              15               7             0
        █M|L|K|J|I|H|G|F█E|D|C|B|A|T|T|T█T|T|T|T|T|T|T|T█T|T|T|T|T|T|T|T█

        T: the delta time if it is <= 0x7fffd. Otherwise 0x7fffe indicates an int immediately
           follows containing the time, and 0x7ffff indicates a long immediately follows with the
           delta time.
        A: battery level changed and an int follows with battery data.
        B: state changed and an int follows with state change data.
        C: state2 has changed and an int follows with state2 change data.
        D: wakelock/wakereason has changed and an wakelock/wakereason struct follows.
        E: event data has changed and an event struct follows.
        F: battery charge in coulombs has changed and an int with the charge follows.
        G: state flag denoting that the mobile radio was active.
        H: state flag denoting that the wifi radio was active.
        I: state flag denoting that a wifi scan occurred.
        J: state flag denoting that a wifi full lock was held.
        K: state flag denoting that the gps was on.
        L: state flag denoting that a wakelock was held.
        M: state flag denoting that the cpu was running.

        Time int/long: if T in the first token is 0x7ffff or 0x7fffe, then an int or long follows
        with the time delta.

        Battery level int: if A in the first token is set,
        31              23              15               7             0
        █L|L|L|L|L|L|L|T█T|T|T|T|T|T|T|T█T|V|V|V|V|V|V|V█V|V|V|V|V|V|V|D█

        D: indicates that extra history details follow.
        V: the battery voltage.
        T: the battery temperature.
        L: the battery level (out of 100).

        State change int: if B in the first token is set,
        31              23              15               7             0
        █S|S|S|H|H|H|P|P█F|E|D|C|B| | |A█ | | | | | | | █ | | | | | | | █

        A: wifi multicast was on.
        B: battery was plugged in.
        C: screen was on.
        D: phone was scanning for signal.
        E: audio was on.
        F: a sensor was active.

        State2 change int: if C in the first token is set,
        31              23              15               7             0
        █M|L|K|J|I|H|H|G█F|E|D|C| | | | █ | | | | | | | █ |B|B|B|A|A|A|A█

        A: 4 bits indicating the wifi supplicant state: {@link BatteryStats#WIFI_SUPPL_STATE_NAMES}.
        B: 3 bits indicating the wifi signal strength: 0, 1, 2, 3, 4.
        C: a bluetooth scan was active.
        D: the camera was active.
        E: bluetooth was on.
        F: a phone call was active.
        G: the device was charging.
        H: 2 bits indicating the device-idle (doze) state: off, light, full
        I: the flashlight was on.
        J: wifi was on.
        K: wifi was running.
        L: video was playing.
        M: power save mode was on.

        Wakelock/wakereason struct: if D in the first token is set,
        TODO(adamlesinski): describe wakelock/wakereason struct.

        Event struct: if E in the first token is set,
        TODO(adamlesinski): describe the event struct.

        History step details struct: if D in the battery level int is set,
        TODO(adamlesinski): describe the history step details struct.

        Battery charge int: if F in the first token is set, an int representing the battery charge
        in coulombs follows.
     */

    // Part of initial delta int that specifies the time delta.
    static final int DELTA_TIME_MASK = 0x7ffff;
    static final int DELTA_TIME_LONG = 0x7ffff;   // The delta is a following long
    static final int DELTA_TIME_INT = 0x7fffe;    // The delta is a following int
    static final int DELTA_TIME_ABS = 0x7fffd;    // Following is an entire abs update.
    // Flag in delta int: a new battery level int follows.
    static final int DELTA_BATTERY_LEVEL_FLAG               = 0x00080000;
    // Flag in delta int: a new full state and battery status int follows.
    static final int DELTA_STATE_FLAG                       = 0x00100000;
    // Flag in delta int: a new full state2 int follows.
    static final int DELTA_STATE2_FLAG                      = 0x00200000;
    // Flag in delta int: contains a wakelock or wakeReason tag.
    static final int DELTA_WAKELOCK_FLAG                    = 0x00400000;
    // Flag in delta int: contains an event description.
    static final int DELTA_EVENT_FLAG                       = 0x00800000;
    // Flag in delta int: contains the battery charge count in uAh.
    static final int DELTA_BATTERY_CHARGE_FLAG              = 0x01000000;
    // These upper bits are the frequently changing state bits.
    static final int DELTA_STATE_MASK                       = 0xfe000000;

    // These are the pieces of battery state that are packed in to the upper bits of
    // the state int that have been packed in to the first delta int.  They must fit
    // in STATE_BATTERY_MASK.
    static final int STATE_BATTERY_MASK         = 0xff000000;
    static final int STATE_BATTERY_STATUS_MASK  = 0x00000007;
    static final int STATE_BATTERY_STATUS_SHIFT = 29;
    static final int STATE_BATTERY_HEALTH_MASK  = 0x00000007;
    static final int STATE_BATTERY_HEALTH_SHIFT = 26;
    static final int STATE_BATTERY_PLUG_MASK    = 0x00000003;
    static final int STATE_BATTERY_PLUG_SHIFT   = 24;

    // We use the low bit of the battery state int to indicate that we have full details
    // from a battery level change.
    static final int BATTERY_DELTA_LEVEL_FLAG   = 0x00000001;

    public void writeHistoryDelta(Parcel dest, HistoryItem cur, HistoryItem last) {
        if (last == null || cur.cmd != HistoryItem.CMD_UPDATE) {
            dest.writeInt(DELTA_TIME_ABS);
            cur.writeToParcel(dest, 0);
            return;
        }

        final long deltaTime = cur.time - last.time;
        final int lastBatteryLevelInt = buildBatteryLevelInt(last);
        final int lastStateInt = buildStateInt(last);

        int deltaTimeToken;
        if (deltaTime < 0 || deltaTime > Integer.MAX_VALUE) {
            deltaTimeToken = DELTA_TIME_LONG;
        } else if (deltaTime >= DELTA_TIME_ABS) {
            deltaTimeToken = DELTA_TIME_INT;
        } else {
            deltaTimeToken = (int)deltaTime;
        }
        int firstToken = deltaTimeToken | (cur.states&DELTA_STATE_MASK);
        final int includeStepDetails = mLastHistoryStepLevel > cur.batteryLevel
                ? BATTERY_DELTA_LEVEL_FLAG : 0;
        final boolean computeStepDetails = includeStepDetails != 0
                || mLastHistoryStepDetails == null;
        final int batteryLevelInt = buildBatteryLevelInt(cur) | includeStepDetails;
        final boolean batteryLevelIntChanged = batteryLevelInt != lastBatteryLevelInt;
        if (batteryLevelIntChanged) {
            firstToken |= DELTA_BATTERY_LEVEL_FLAG;
        }
        final int stateInt = buildStateInt(cur);
        final boolean stateIntChanged = stateInt != lastStateInt;
        if (stateIntChanged) {
            firstToken |= DELTA_STATE_FLAG;
        }
        final boolean state2IntChanged = cur.states2 != last.states2;
        if (state2IntChanged) {
            firstToken |= DELTA_STATE2_FLAG;
        }
        if (cur.wakelockTag != null || cur.wakeReasonTag != null) {
            firstToken |= DELTA_WAKELOCK_FLAG;
        }
        if (cur.eventCode != HistoryItem.EVENT_NONE) {
            firstToken |= DELTA_EVENT_FLAG;
        }

        final boolean batteryChargeChanged = cur.batteryChargeUAh != last.batteryChargeUAh;
        if (batteryChargeChanged) {
            firstToken |= DELTA_BATTERY_CHARGE_FLAG;
        }
        dest.writeInt(firstToken);
        if (DEBUG) Slog.i(TAG, "WRITE DELTA: firstToken=0x" + Integer.toHexString(firstToken)
                + " deltaTime=" + deltaTime);

        if (deltaTimeToken >= DELTA_TIME_INT) {
            if (deltaTimeToken == DELTA_TIME_INT) {
                if (DEBUG) Slog.i(TAG, "WRITE DELTA: int deltaTime=" + (int)deltaTime);
                dest.writeInt((int)deltaTime);
            } else {
                if (DEBUG) Slog.i(TAG, "WRITE DELTA: long deltaTime=" + deltaTime);
                dest.writeLong(deltaTime);
            }
        }
        if (batteryLevelIntChanged) {
            dest.writeInt(batteryLevelInt);
            if (DEBUG) Slog.i(TAG, "WRITE DELTA: batteryToken=0x"
                    + Integer.toHexString(batteryLevelInt)
                    + " batteryLevel=" + cur.batteryLevel
                    + " batteryTemp=" + cur.batteryTemperature
                    + " batteryVolt=" + (int)cur.batteryVoltage);
        }
        if (stateIntChanged) {
            dest.writeInt(stateInt);
            if (DEBUG) Slog.i(TAG, "WRITE DELTA: stateToken=0x"
                    + Integer.toHexString(stateInt)
                    + " batteryStatus=" + cur.batteryStatus
                    + " batteryHealth=" + cur.batteryHealth
                    + " batteryPlugType=" + cur.batteryPlugType
                    + " states=0x" + Integer.toHexString(cur.states));
        }
        if (state2IntChanged) {
            dest.writeInt(cur.states2);
            if (DEBUG) Slog.i(TAG, "WRITE DELTA: states2=0x"
                    + Integer.toHexString(cur.states2));
        }
        if (cur.wakelockTag != null || cur.wakeReasonTag != null) {
            int wakeLockIndex;
            int wakeReasonIndex;
            if (cur.wakelockTag != null) {
                wakeLockIndex = writeHistoryTag(cur.wakelockTag);
                if (DEBUG) Slog.i(TAG, "WRITE DELTA: wakelockTag=#" + cur.wakelockTag.poolIdx
                    + " " + cur.wakelockTag.uid + ":" + cur.wakelockTag.string);
            } else {
                wakeLockIndex = 0xffff;
            }
            if (cur.wakeReasonTag != null) {
                wakeReasonIndex = writeHistoryTag(cur.wakeReasonTag);
                if (DEBUG) Slog.i(TAG, "WRITE DELTA: wakeReasonTag=#" + cur.wakeReasonTag.poolIdx
                    + " " + cur.wakeReasonTag.uid + ":" + cur.wakeReasonTag.string);
            } else {
                wakeReasonIndex = 0xffff;
            }
            dest.writeInt((wakeReasonIndex<<16) | wakeLockIndex);
        }
        if (cur.eventCode != HistoryItem.EVENT_NONE) {
            int index = writeHistoryTag(cur.eventTag);
            int codeAndIndex = (cur.eventCode&0xffff) | (index<<16);
            dest.writeInt(codeAndIndex);
            if (DEBUG) Slog.i(TAG, "WRITE DELTA: event=" + cur.eventCode + " tag=#"
                    + cur.eventTag.poolIdx + " " + cur.eventTag.uid + ":"
                    + cur.eventTag.string);
        }
        if (computeStepDetails) {
            if (mPlatformIdleStateCallback != null) {
                mCurHistoryStepDetails.statPlatformIdleState =
                        mPlatformIdleStateCallback.getPlatformLowPowerStats();
                if (DEBUG) Slog.i(TAG, "WRITE PlatformIdleState:" +
                        mCurHistoryStepDetails.statPlatformIdleState);
            }
            computeHistoryStepDetails(mCurHistoryStepDetails, mLastHistoryStepDetails);
            if (includeStepDetails != 0) {
                mCurHistoryStepDetails.writeToParcel(dest);
            }
            cur.stepDetails = mCurHistoryStepDetails;
            mLastHistoryStepDetails = mCurHistoryStepDetails;
        } else {
            cur.stepDetails = null;
        }
        if (mLastHistoryStepLevel < cur.batteryLevel) {
            mLastHistoryStepDetails = null;
        }
        mLastHistoryStepLevel = cur.batteryLevel;

        if (batteryChargeChanged) {
            if (DEBUG) Slog.i(TAG, "WRITE DELTA: batteryChargeUAh=" + cur.batteryChargeUAh);
            dest.writeInt(cur.batteryChargeUAh);
        }
    }

    private int buildBatteryLevelInt(HistoryItem h) {
        return ((((int)h.batteryLevel)<<25)&0xfe000000)
                | ((((int)h.batteryTemperature)<<15)&0x01ff8000)
                | ((((int)h.batteryVoltage)<<1)&0x00007ffe);
    }

    private void readBatteryLevelInt(int batteryLevelInt, HistoryItem out) {
        out.batteryLevel = (byte)((batteryLevelInt & 0xfe000000) >>> 25);
        out.batteryTemperature = (short)((batteryLevelInt & 0x01ff8000) >>> 15);
        out.batteryVoltage = (char)((batteryLevelInt & 0x00007ffe) >>> 1);
    }

    private int buildStateInt(HistoryItem h) {
        int plugType = 0;
        if ((h.batteryPlugType&BatteryManager.BATTERY_PLUGGED_AC) != 0) {
            plugType = 1;
        } else if ((h.batteryPlugType&BatteryManager.BATTERY_PLUGGED_USB) != 0) {
            plugType = 2;
        } else if ((h.batteryPlugType&BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) {
            plugType = 3;
        }
        return ((h.batteryStatus&STATE_BATTERY_STATUS_MASK)<<STATE_BATTERY_STATUS_SHIFT)
                | ((h.batteryHealth&STATE_BATTERY_HEALTH_MASK)<<STATE_BATTERY_HEALTH_SHIFT)
                | ((plugType&STATE_BATTERY_PLUG_MASK)<<STATE_BATTERY_PLUG_SHIFT)
                | (h.states&(~STATE_BATTERY_MASK));
    }

    private void computeHistoryStepDetails(final HistoryStepDetails out,
            final HistoryStepDetails last) {
        final HistoryStepDetails tmp = last != null ? mTmpHistoryStepDetails : out;

        // Perform a CPU update right after we do this collection, so we have started
        // collecting good data for the next step.
        requestImmediateCpuUpdate();

        if (last == null) {
            // We are not generating a delta, so all we need to do is reset the stats
            // we will later be doing a delta from.
            final int NU = mUidStats.size();
            for (int i=0; i<NU; i++) {
                final BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.mLastStepUserTime = uid.mCurStepUserTime;
                uid.mLastStepSystemTime = uid.mCurStepSystemTime;
            }
            mLastStepCpuUserTime = mCurStepCpuUserTime;
            mLastStepCpuSystemTime = mCurStepCpuSystemTime;
            mLastStepStatUserTime = mCurStepStatUserTime;
            mLastStepStatSystemTime = mCurStepStatSystemTime;
            mLastStepStatIOWaitTime = mCurStepStatIOWaitTime;
            mLastStepStatIrqTime = mCurStepStatIrqTime;
            mLastStepStatSoftIrqTime = mCurStepStatSoftIrqTime;
            mLastStepStatIdleTime = mCurStepStatIdleTime;
            tmp.clear();
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "Step stats last: user=" + mLastStepCpuUserTime + " sys="
                    + mLastStepStatSystemTime + " io=" + mLastStepStatIOWaitTime
                    + " irq=" + mLastStepStatIrqTime + " sirq="
                    + mLastStepStatSoftIrqTime + " idle=" + mLastStepStatIdleTime);
            Slog.d(TAG, "Step stats cur: user=" + mCurStepCpuUserTime + " sys="
                    + mCurStepStatSystemTime + " io=" + mCurStepStatIOWaitTime
                    + " irq=" + mCurStepStatIrqTime + " sirq="
                    + mCurStepStatSoftIrqTime + " idle=" + mCurStepStatIdleTime);
        }
        out.userTime = (int)(mCurStepCpuUserTime - mLastStepCpuUserTime);
        out.systemTime = (int)(mCurStepCpuSystemTime - mLastStepCpuSystemTime);
        out.statUserTime = (int)(mCurStepStatUserTime - mLastStepStatUserTime);
        out.statSystemTime = (int)(mCurStepStatSystemTime - mLastStepStatSystemTime);
        out.statIOWaitTime = (int)(mCurStepStatIOWaitTime - mLastStepStatIOWaitTime);
        out.statIrqTime = (int)(mCurStepStatIrqTime - mLastStepStatIrqTime);
        out.statSoftIrqTime = (int)(mCurStepStatSoftIrqTime - mLastStepStatSoftIrqTime);
        out.statIdlTime = (int)(mCurStepStatIdleTime - mLastStepStatIdleTime);
        out.appCpuUid1 = out.appCpuUid2 = out.appCpuUid3 = -1;
        out.appCpuUTime1 = out.appCpuUTime2 = out.appCpuUTime3 = 0;
        out.appCpuSTime1 = out.appCpuSTime2 = out.appCpuSTime3 = 0;
        final int NU = mUidStats.size();
        for (int i=0; i<NU; i++) {
            final BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
            final int totalUTime = (int)(uid.mCurStepUserTime - uid.mLastStepUserTime);
            final int totalSTime = (int)(uid.mCurStepSystemTime - uid.mLastStepSystemTime);
            final int totalTime = totalUTime + totalSTime;
            uid.mLastStepUserTime = uid.mCurStepUserTime;
            uid.mLastStepSystemTime = uid.mCurStepSystemTime;
            if (totalTime <= (out.appCpuUTime3+out.appCpuSTime3)) {
                continue;
            }
            if (totalTime <= (out.appCpuUTime2+out.appCpuSTime2)) {
                out.appCpuUid3 = uid.mUid;
                out.appCpuUTime3 = totalUTime;
                out.appCpuSTime3 = totalSTime;
            } else {
                out.appCpuUid3 = out.appCpuUid2;
                out.appCpuUTime3 = out.appCpuUTime2;
                out.appCpuSTime3 = out.appCpuSTime2;
                if (totalTime <= (out.appCpuUTime1+out.appCpuSTime1)) {
                    out.appCpuUid2 = uid.mUid;
                    out.appCpuUTime2 = totalUTime;
                    out.appCpuSTime2 = totalSTime;
                } else {
                    out.appCpuUid2 = out.appCpuUid1;
                    out.appCpuUTime2 = out.appCpuUTime1;
                    out.appCpuSTime2 = out.appCpuSTime1;
                    out.appCpuUid1 = uid.mUid;
                    out.appCpuUTime1 = totalUTime;
                    out.appCpuSTime1 = totalSTime;
                }
            }
        }
        mLastStepCpuUserTime = mCurStepCpuUserTime;
        mLastStepCpuSystemTime = mCurStepCpuSystemTime;
        mLastStepStatUserTime = mCurStepStatUserTime;
        mLastStepStatSystemTime = mCurStepStatSystemTime;
        mLastStepStatIOWaitTime = mCurStepStatIOWaitTime;
        mLastStepStatIrqTime = mCurStepStatIrqTime;
        mLastStepStatSoftIrqTime = mCurStepStatSoftIrqTime;
        mLastStepStatIdleTime = mCurStepStatIdleTime;
    }

    public void readHistoryDelta(Parcel src, HistoryItem cur) {
        int firstToken = src.readInt();
        int deltaTimeToken = firstToken&DELTA_TIME_MASK;
        cur.cmd = HistoryItem.CMD_UPDATE;
        cur.numReadInts = 1;
        if (DEBUG) Slog.i(TAG, "READ DELTA: firstToken=0x" + Integer.toHexString(firstToken)
                + " deltaTimeToken=" + deltaTimeToken);

        if (deltaTimeToken < DELTA_TIME_ABS) {
            cur.time += deltaTimeToken;
        } else if (deltaTimeToken == DELTA_TIME_ABS) {
            cur.time = src.readLong();
            cur.numReadInts += 2;
            if (DEBUG) Slog.i(TAG, "READ DELTA: ABS time=" + cur.time);
            cur.readFromParcel(src);
            return;
        } else if (deltaTimeToken == DELTA_TIME_INT) {
            int delta = src.readInt();
            cur.time += delta;
            cur.numReadInts += 1;
            if (DEBUG) Slog.i(TAG, "READ DELTA: time delta=" + delta + " new time=" + cur.time);
        } else {
            long delta = src.readLong();
            if (DEBUG) Slog.i(TAG, "READ DELTA: time delta=" + delta + " new time=" + cur.time);
            cur.time += delta;
            cur.numReadInts += 2;
        }

        final int batteryLevelInt;
        if ((firstToken&DELTA_BATTERY_LEVEL_FLAG) != 0) {
            batteryLevelInt = src.readInt();
            readBatteryLevelInt(batteryLevelInt, cur);
            cur.numReadInts += 1;
            if (DEBUG) Slog.i(TAG, "READ DELTA: batteryToken=0x"
                    + Integer.toHexString(batteryLevelInt)
                    + " batteryLevel=" + cur.batteryLevel
                    + " batteryTemp=" + cur.batteryTemperature
                    + " batteryVolt=" + (int)cur.batteryVoltage);
        } else {
            batteryLevelInt = 0;
        }

        if ((firstToken&DELTA_STATE_FLAG) != 0) {
            int stateInt = src.readInt();
            cur.states = (firstToken&DELTA_STATE_MASK) | (stateInt&(~STATE_BATTERY_MASK));
            cur.batteryStatus = (byte)((stateInt>>STATE_BATTERY_STATUS_SHIFT)
                    & STATE_BATTERY_STATUS_MASK);
            cur.batteryHealth = (byte)((stateInt>>STATE_BATTERY_HEALTH_SHIFT)
                    & STATE_BATTERY_HEALTH_MASK);
            cur.batteryPlugType = (byte)((stateInt>>STATE_BATTERY_PLUG_SHIFT)
                    & STATE_BATTERY_PLUG_MASK);
            switch (cur.batteryPlugType) {
                case 1:
                    cur.batteryPlugType = BatteryManager.BATTERY_PLUGGED_AC;
                    break;
                case 2:
                    cur.batteryPlugType = BatteryManager.BATTERY_PLUGGED_USB;
                    break;
                case 3:
                    cur.batteryPlugType = BatteryManager.BATTERY_PLUGGED_WIRELESS;
                    break;
            }
            cur.numReadInts += 1;
            if (DEBUG) Slog.i(TAG, "READ DELTA: stateToken=0x"
                    + Integer.toHexString(stateInt)
                    + " batteryStatus=" + cur.batteryStatus
                    + " batteryHealth=" + cur.batteryHealth
                    + " batteryPlugType=" + cur.batteryPlugType
                    + " states=0x" + Integer.toHexString(cur.states));
        } else {
            cur.states = (firstToken&DELTA_STATE_MASK) | (cur.states&(~STATE_BATTERY_MASK));
        }

        if ((firstToken&DELTA_STATE2_FLAG) != 0) {
            cur.states2 = src.readInt();
            if (DEBUG) Slog.i(TAG, "READ DELTA: states2=0x"
                    + Integer.toHexString(cur.states2));
        }

        if ((firstToken&DELTA_WAKELOCK_FLAG) != 0) {
            int indexes = src.readInt();
            int wakeLockIndex = indexes&0xffff;
            int wakeReasonIndex = (indexes>>16)&0xffff;
            if (wakeLockIndex != 0xffff) {
                cur.wakelockTag = cur.localWakelockTag;
                readHistoryTag(wakeLockIndex, cur.wakelockTag);
                if (DEBUG) Slog.i(TAG, "READ DELTA: wakelockTag=#" + cur.wakelockTag.poolIdx
                    + " " + cur.wakelockTag.uid + ":" + cur.wakelockTag.string);
            } else {
                cur.wakelockTag = null;
            }
            if (wakeReasonIndex != 0xffff) {
                cur.wakeReasonTag = cur.localWakeReasonTag;
                readHistoryTag(wakeReasonIndex, cur.wakeReasonTag);
                if (DEBUG) Slog.i(TAG, "READ DELTA: wakeReasonTag=#" + cur.wakeReasonTag.poolIdx
                    + " " + cur.wakeReasonTag.uid + ":" + cur.wakeReasonTag.string);
            } else {
                cur.wakeReasonTag = null;
            }
            cur.numReadInts += 1;
        } else {
            cur.wakelockTag = null;
            cur.wakeReasonTag = null;
        }

        if ((firstToken&DELTA_EVENT_FLAG) != 0) {
            cur.eventTag = cur.localEventTag;
            final int codeAndIndex = src.readInt();
            cur.eventCode = (codeAndIndex&0xffff);
            final int index = ((codeAndIndex>>16)&0xffff);
            readHistoryTag(index, cur.eventTag);
            cur.numReadInts += 1;
            if (DEBUG) Slog.i(TAG, "READ DELTA: event=" + cur.eventCode + " tag=#"
                    + cur.eventTag.poolIdx + " " + cur.eventTag.uid + ":"
                    + cur.eventTag.string);
        } else {
            cur.eventCode = HistoryItem.EVENT_NONE;
        }

        if ((batteryLevelInt&BATTERY_DELTA_LEVEL_FLAG) != 0) {
            cur.stepDetails = mReadHistoryStepDetails;
            cur.stepDetails.readFromParcel(src);
        } else {
            cur.stepDetails = null;
        }

        if ((firstToken&DELTA_BATTERY_CHARGE_FLAG) != 0) {
            cur.batteryChargeUAh = src.readInt();
        }
    }

    @Override
    public void commitCurrentHistoryBatchLocked() {
        mHistoryLastWritten.cmd = HistoryItem.CMD_NULL;
    }

    void addHistoryBufferLocked(long elapsedRealtimeMs, long uptimeMs, HistoryItem cur) {
        if (!mHaveBatteryLevel || !mRecordingHistory) {
            return;
        }

        final long timeDiff = (mHistoryBaseTime+elapsedRealtimeMs) - mHistoryLastWritten.time;
        final int diffStates = mHistoryLastWritten.states^(cur.states&mActiveHistoryStates);
        final int diffStates2 = mHistoryLastWritten.states2^(cur.states2&mActiveHistoryStates2);
        final int lastDiffStates = mHistoryLastWritten.states^mHistoryLastLastWritten.states;
        final int lastDiffStates2 = mHistoryLastWritten.states2^mHistoryLastLastWritten.states2;
        if (DEBUG) Slog.i(TAG, "ADD: tdelta=" + timeDiff + " diff="
                + Integer.toHexString(diffStates) + " lastDiff="
                + Integer.toHexString(lastDiffStates) + " diff2="
                + Integer.toHexString(diffStates2) + " lastDiff2="
                + Integer.toHexString(lastDiffStates2));
        if (mHistoryBufferLastPos >= 0 && mHistoryLastWritten.cmd == HistoryItem.CMD_UPDATE
                && timeDiff < 1000 && (diffStates&lastDiffStates) == 0
                && (diffStates2&lastDiffStates2) == 0
                && (mHistoryLastWritten.wakelockTag == null || cur.wakelockTag == null)
                && (mHistoryLastWritten.wakeReasonTag == null || cur.wakeReasonTag == null)
                && mHistoryLastWritten.stepDetails == null
                && (mHistoryLastWritten.eventCode == HistoryItem.EVENT_NONE
                        || cur.eventCode == HistoryItem.EVENT_NONE)
                && mHistoryLastWritten.batteryLevel == cur.batteryLevel
                && mHistoryLastWritten.batteryStatus == cur.batteryStatus
                && mHistoryLastWritten.batteryHealth == cur.batteryHealth
                && mHistoryLastWritten.batteryPlugType == cur.batteryPlugType
                && mHistoryLastWritten.batteryTemperature == cur.batteryTemperature
                && mHistoryLastWritten.batteryVoltage == cur.batteryVoltage) {
            // We can merge this new change in with the last one.  Merging is
            // allowed as long as only the states have changed, and within those states
            // as long as no bit has changed both between now and the last entry, as
            // well as the last entry and the one before it (so we capture any toggles).
            if (DEBUG) Slog.i(TAG, "ADD: rewinding back to " + mHistoryBufferLastPos);
            mHistoryBuffer.setDataSize(mHistoryBufferLastPos);
            mHistoryBuffer.setDataPosition(mHistoryBufferLastPos);
            mHistoryBufferLastPos = -1;
            elapsedRealtimeMs = mHistoryLastWritten.time - mHistoryBaseTime;
            // If the last written history had a wakelock tag, we need to retain it.
            // Note that the condition above made sure that we aren't in a case where
            // both it and the current history item have a wakelock tag.
            if (mHistoryLastWritten.wakelockTag != null) {
                cur.wakelockTag = cur.localWakelockTag;
                cur.wakelockTag.setTo(mHistoryLastWritten.wakelockTag);
            }
            // If the last written history had a wake reason tag, we need to retain it.
            // Note that the condition above made sure that we aren't in a case where
            // both it and the current history item have a wakelock tag.
            if (mHistoryLastWritten.wakeReasonTag != null) {
                cur.wakeReasonTag = cur.localWakeReasonTag;
                cur.wakeReasonTag.setTo(mHistoryLastWritten.wakeReasonTag);
            }
            // If the last written history had an event, we need to retain it.
            // Note that the condition above made sure that we aren't in a case where
            // both it and the current history item have an event.
            if (mHistoryLastWritten.eventCode != HistoryItem.EVENT_NONE) {
                cur.eventCode = mHistoryLastWritten.eventCode;
                cur.eventTag = cur.localEventTag;
                cur.eventTag.setTo(mHistoryLastWritten.eventTag);
            }
            mHistoryLastWritten.setTo(mHistoryLastLastWritten);
        }

        final int dataSize = mHistoryBuffer.dataSize();
        if (dataSize >= MAX_HISTORY_BUFFER) {
            if (!mHistoryOverflow) {
                mHistoryOverflow = true;
                addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_UPDATE, cur);
                addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_OVERFLOW, cur);
                return;
            }

            // After overflow, we allow various bit-wise states to settle to 0.
            boolean writeAnyway = false;
            final int curStates = cur.states & HistoryItem.SETTLE_TO_ZERO_STATES
                    & mActiveHistoryStates;
            if (mHistoryLastWritten.states != curStates) {
                // mActiveHistoryStates keeps track of which bits in .states are now being
                // forced to 0.
                int old = mActiveHistoryStates;
                mActiveHistoryStates &= curStates | ~HistoryItem.SETTLE_TO_ZERO_STATES;
                writeAnyway |= old != mActiveHistoryStates;
            }
            final int curStates2 = cur.states2 & HistoryItem.SETTLE_TO_ZERO_STATES2
                    & mActiveHistoryStates2;
            if (mHistoryLastWritten.states2 != curStates2) {
                // mActiveHistoryStates2 keeps track of which bits in .states2 are now being
                // forced to 0.
                int old = mActiveHistoryStates2;
                mActiveHistoryStates2 &= curStates2 | ~HistoryItem.SETTLE_TO_ZERO_STATES2;
                writeAnyway |= old != mActiveHistoryStates2;
            }

            // Once we've reached the maximum number of items, we only
            // record changes to the battery level and the most interesting states.
            // Once we've reached the maximum maximum number of items, we only
            // record changes to the battery level.
            if (!writeAnyway && mHistoryLastWritten.batteryLevel == cur.batteryLevel &&
                    (dataSize >= MAX_MAX_HISTORY_BUFFER
                            || ((mHistoryLastWritten.states^cur.states)
                                    & HistoryItem.MOST_INTERESTING_STATES) == 0
                            || ((mHistoryLastWritten.states2^cur.states2)
                                    & HistoryItem.MOST_INTERESTING_STATES2) == 0)) {
                return;
            }

            addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_UPDATE, cur);
            return;
        }

        if (dataSize == 0) {
            // The history is currently empty; we need it to start with a time stamp.
            cur.currentTime = System.currentTimeMillis();
            addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_RESET, cur);
        }
        addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_UPDATE, cur);
    }

    private void addHistoryBufferLocked(long elapsedRealtimeMs, long uptimeMs, byte cmd,
            HistoryItem cur) {
        if (mIteratingHistory) {
            throw new IllegalStateException("Can't do this while iterating history!");
        }
        mHistoryBufferLastPos = mHistoryBuffer.dataPosition();
        mHistoryLastLastWritten.setTo(mHistoryLastWritten);
        mHistoryLastWritten.setTo(mHistoryBaseTime + elapsedRealtimeMs, cmd, cur);
        mHistoryLastWritten.states &= mActiveHistoryStates;
        mHistoryLastWritten.states2 &= mActiveHistoryStates2;
        writeHistoryDelta(mHistoryBuffer, mHistoryLastWritten, mHistoryLastLastWritten);
        mLastHistoryElapsedRealtime = elapsedRealtimeMs;
        cur.wakelockTag = null;
        cur.wakeReasonTag = null;
        cur.eventCode = HistoryItem.EVENT_NONE;
        cur.eventTag = null;
        if (DEBUG_HISTORY) Slog.i(TAG, "Writing history buffer: was " + mHistoryBufferLastPos
                + " now " + mHistoryBuffer.dataPosition()
                + " size is now " + mHistoryBuffer.dataSize());
    }

    int mChangedStates = 0;
    int mChangedStates2 = 0;

    void addHistoryRecordLocked(long elapsedRealtimeMs, long uptimeMs) {
        if (mTrackRunningHistoryElapsedRealtime != 0) {
            final long diffElapsed = elapsedRealtimeMs - mTrackRunningHistoryElapsedRealtime;
            final long diffUptime = uptimeMs - mTrackRunningHistoryUptime;
            if (diffUptime < (diffElapsed-20)) {
                final long wakeElapsedTime = elapsedRealtimeMs - (diffElapsed - diffUptime);
                mHistoryAddTmp.setTo(mHistoryLastWritten);
                mHistoryAddTmp.wakelockTag = null;
                mHistoryAddTmp.wakeReasonTag = null;
                mHistoryAddTmp.eventCode = HistoryItem.EVENT_NONE;
                mHistoryAddTmp.states &= ~HistoryItem.STATE_CPU_RUNNING_FLAG;
                addHistoryRecordInnerLocked(wakeElapsedTime, uptimeMs, mHistoryAddTmp);
            }
        }
        mHistoryCur.states |= HistoryItem.STATE_CPU_RUNNING_FLAG;
        mTrackRunningHistoryElapsedRealtime = elapsedRealtimeMs;
        mTrackRunningHistoryUptime = uptimeMs;
        addHistoryRecordInnerLocked(elapsedRealtimeMs, uptimeMs, mHistoryCur);
    }

    void addHistoryRecordInnerLocked(long elapsedRealtimeMs, long uptimeMs, HistoryItem cur) {
        addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, cur);

        if (!USE_OLD_HISTORY) {
            return;
        }

        if (!mHaveBatteryLevel || !mRecordingHistory) {
            return;
        }

        // If the current time is basically the same as the last time,
        // and no states have since the last recorded entry changed and
        // are now resetting back to their original value, then just collapse
        // into one record.
        if (mHistoryEnd != null && mHistoryEnd.cmd == HistoryItem.CMD_UPDATE
                && (mHistoryBaseTime+elapsedRealtimeMs) < (mHistoryEnd.time+1000)
                && ((mHistoryEnd.states^cur.states)&mChangedStates&mActiveHistoryStates) == 0
                && ((mHistoryEnd.states2^cur.states2)&mChangedStates2&mActiveHistoryStates2) == 0) {
            // If the current is the same as the one before, then we no
            // longer need the entry.
            if (mHistoryLastEnd != null && mHistoryLastEnd.cmd == HistoryItem.CMD_UPDATE
                    && (mHistoryBaseTime+elapsedRealtimeMs) < (mHistoryEnd.time+500)
                    && mHistoryLastEnd.sameNonEvent(cur)) {
                mHistoryLastEnd.next = null;
                mHistoryEnd.next = mHistoryCache;
                mHistoryCache = mHistoryEnd;
                mHistoryEnd = mHistoryLastEnd;
                mHistoryLastEnd = null;
            } else {
                mChangedStates |= mHistoryEnd.states^(cur.states&mActiveHistoryStates);
                mChangedStates2 |= mHistoryEnd.states^(cur.states2&mActiveHistoryStates2);
                mHistoryEnd.setTo(mHistoryEnd.time, HistoryItem.CMD_UPDATE, cur);
            }
            return;
        }

        mChangedStates = 0;
        mChangedStates2 = 0;

        if (mNumHistoryItems == MAX_HISTORY_ITEMS
                || mNumHistoryItems == MAX_MAX_HISTORY_ITEMS) {
            addHistoryRecordLocked(elapsedRealtimeMs, HistoryItem.CMD_OVERFLOW);
        }

        if (mNumHistoryItems >= MAX_HISTORY_ITEMS) {
            // Once we've reached the maximum number of items, we only
            // record changes to the battery level and the most interesting states.
            // Once we've reached the maximum maximum number of items, we only
            // record changes to the battery level.
            if (mHistoryEnd != null && mHistoryEnd.batteryLevel
                    == cur.batteryLevel &&
                    (mNumHistoryItems >= MAX_MAX_HISTORY_ITEMS
                            || ((mHistoryEnd.states^(cur.states&mActiveHistoryStates))
                                    & HistoryItem.MOST_INTERESTING_STATES) == 0)) {
                return;
            }
        }

        addHistoryRecordLocked(elapsedRealtimeMs, HistoryItem.CMD_UPDATE);
    }

    public void addHistoryEventLocked(long elapsedRealtimeMs, long uptimeMs, int code,
            String name, int uid) {
        mHistoryCur.eventCode = code;
        mHistoryCur.eventTag = mHistoryCur.localEventTag;
        mHistoryCur.eventTag.string = name;
        mHistoryCur.eventTag.uid = uid;
        addHistoryRecordLocked(elapsedRealtimeMs, uptimeMs);
    }

    void addHistoryRecordLocked(long elapsedRealtimeMs, long uptimeMs, byte cmd, HistoryItem cur) {
        HistoryItem rec = mHistoryCache;
        if (rec != null) {
            mHistoryCache = rec.next;
        } else {
            rec = new HistoryItem();
        }
        rec.setTo(mHistoryBaseTime + elapsedRealtimeMs, cmd, cur);

        addHistoryRecordLocked(rec);
    }

    void addHistoryRecordLocked(HistoryItem rec) {
        mNumHistoryItems++;
        rec.next = null;
        mHistoryLastEnd = mHistoryEnd;
        if (mHistoryEnd != null) {
            mHistoryEnd.next = rec;
            mHistoryEnd = rec;
        } else {
            mHistory = mHistoryEnd = rec;
        }
    }

    void clearHistoryLocked() {
        if (DEBUG_HISTORY) Slog.i(TAG, "********** CLEARING HISTORY!");
        if (USE_OLD_HISTORY) {
            if (mHistory != null) {
                mHistoryEnd.next = mHistoryCache;
                mHistoryCache = mHistory;
                mHistory = mHistoryLastEnd = mHistoryEnd = null;
            }
            mNumHistoryItems = 0;
        }

        mHistoryBaseTime = 0;
        mLastHistoryElapsedRealtime = 0;
        mTrackRunningHistoryElapsedRealtime = 0;
        mTrackRunningHistoryUptime = 0;

        mHistoryBuffer.setDataSize(0);
        mHistoryBuffer.setDataPosition(0);
        mHistoryBuffer.setDataCapacity(MAX_HISTORY_BUFFER / 2);
        mHistoryLastLastWritten.clear();
        mHistoryLastWritten.clear();
        mHistoryTagPool.clear();
        mNextHistoryTagIdx = 0;
        mNumHistoryTagChars = 0;
        mHistoryBufferLastPos = -1;
        mHistoryOverflow = false;
        mActiveHistoryStates = 0xffffffff;
        mActiveHistoryStates2 = 0xffffffff;
    }

    public void updateTimeBasesLocked(boolean unplugged, boolean screenOff, long uptime,
            long realtime) {
        mOnBatteryTimeBase.setRunning(unplugged, uptime, realtime);

        boolean unpluggedScreenOff = unplugged && screenOff;
        if (unpluggedScreenOff != mOnBatteryScreenOffTimeBase.isRunning()) {
            updateKernelWakelocksLocked();
            if (DEBUG_ENERGY_CPU) {
                Slog.d(TAG, "Updating cpu time because screen is now " +
                        (unpluggedScreenOff ? "off" : "on"));
            }
            updateCpuTimeLocked();
            mOnBatteryScreenOffTimeBase.setRunning(unpluggedScreenOff, uptime, realtime);
        }
    }

    public void addIsolatedUidLocked(int isolatedUid, int appUid) {
        mIsolatedUids.put(isolatedUid, appUid);
    }

    /**
     * Schedules a read of the latest cpu times before removing the isolated UID.
     * @see #removeIsolatedUidLocked(int)
     */
    public void scheduleRemoveIsolatedUidLocked(int isolatedUid, int appUid) {
        int curUid = mIsolatedUids.get(isolatedUid, -1);
        if (curUid == appUid) {
            if (mExternalSync != null) {
                mExternalSync.scheduleCpuSyncDueToRemovedUid(isolatedUid);
            }
        }
    }

    /**
     * This should only be called after the cpu times have been read.
     * @see #scheduleRemoveIsolatedUidLocked(int, int)
     */
    public void removeIsolatedUidLocked(int isolatedUid) {
        mIsolatedUids.delete(isolatedUid);
        mKernelUidCpuTimeReader.removeUid(isolatedUid);
    }

    public int mapUid(int uid) {
        int isolated = mIsolatedUids.get(uid, -1);
        return isolated > 0 ? isolated : uid;
    }

    public void noteEventLocked(int code, String name, int uid) {
        uid = mapUid(uid);
        if (!mActiveEvents.updateState(code, name, uid, 0)) {
            return;
        }
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, code, name, uid);
    }

    boolean ensureStartClockTime(final long currentTime) {
        final long ABOUT_ONE_YEAR = 365*24*60*60*1000L;
        if (currentTime > ABOUT_ONE_YEAR && mStartClockTime < (currentTime-ABOUT_ONE_YEAR)) {
            // If the start clock time has changed by more than a year, then presumably
            // the previous time was completely bogus.  So we are going to figure out a
            // new time based on how much time has elapsed since we started counting.
            mStartClockTime = currentTime - (mClocks.elapsedRealtime()-(mRealtimeStart/1000));
            return true;
        }
        return false;
    }

    public void noteCurrentTimeChangedLocked() {
        final long currentTime = System.currentTimeMillis();
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        recordCurrentTimeChangeLocked(currentTime, elapsedRealtime, uptime);
        ensureStartClockTime(currentTime);
    }

    public void noteProcessStartLocked(String name, int uid) {
        uid = mapUid(uid);
        if (isOnBattery()) {
            Uid u = getUidStatsLocked(uid);
            u.getProcessStatsLocked(name).incStartsLocked();
        }
        if (!mActiveEvents.updateState(HistoryItem.EVENT_PROC_START, name, uid, 0)) {
            return;
        }
        if (!mRecordAllHistory) {
            return;
        }
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_PROC_START, name, uid);
    }

    public void noteProcessCrashLocked(String name, int uid) {
        uid = mapUid(uid);
        if (isOnBattery()) {
            Uid u = getUidStatsLocked(uid);
            u.getProcessStatsLocked(name).incNumCrashesLocked();
        }
    }

    public void noteProcessAnrLocked(String name, int uid) {
        uid = mapUid(uid);
        if (isOnBattery()) {
            Uid u = getUidStatsLocked(uid);
            u.getProcessStatsLocked(name).incNumAnrsLocked();
        }
    }

    public void noteUidProcessStateLocked(int uid, int state) {
        uid = mapUid(uid);
        getUidStatsLocked(uid).updateUidProcessStateLocked(state);
    }

    public void noteProcessFinishLocked(String name, int uid) {
        uid = mapUid(uid);
        if (!mActiveEvents.updateState(HistoryItem.EVENT_PROC_FINISH, name, uid, 0)) {
            return;
        }
        if (!mRecordAllHistory) {
            return;
        }
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_PROC_FINISH, name, uid);
    }

    public void noteSyncStartLocked(String name, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        getUidStatsLocked(uid).noteStartSyncLocked(name, elapsedRealtime);
        if (!mActiveEvents.updateState(HistoryItem.EVENT_SYNC_START, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_SYNC_START, name, uid);
    }

    public void noteSyncFinishLocked(String name, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        getUidStatsLocked(uid).noteStopSyncLocked(name, elapsedRealtime);
        if (!mActiveEvents.updateState(HistoryItem.EVENT_SYNC_FINISH, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_SYNC_FINISH, name, uid);
    }

    public void noteJobStartLocked(String name, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        getUidStatsLocked(uid).noteStartJobLocked(name, elapsedRealtime);
        if (!mActiveEvents.updateState(HistoryItem.EVENT_JOB_START, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_JOB_START, name, uid);
    }

    public void noteJobFinishLocked(String name, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        getUidStatsLocked(uid).noteStopJobLocked(name, elapsedRealtime);
        if (!mActiveEvents.updateState(HistoryItem.EVENT_JOB_FINISH, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_JOB_FINISH, name, uid);
    }

    public void noteAlarmStartLocked(String name, int uid) {
        if (!mRecordAllHistory) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (!mActiveEvents.updateState(HistoryItem.EVENT_ALARM_START, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_ALARM_START, name, uid);
    }

    public void noteAlarmFinishLocked(String name, int uid) {
        if (!mRecordAllHistory) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (!mActiveEvents.updateState(HistoryItem.EVENT_ALARM_FINISH, name, uid, 0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_ALARM_FINISH, name, uid);
    }

    private void requestWakelockCpuUpdate() {
        if (!mHandler.hasMessages(MSG_UPDATE_WAKELOCKS)) {
            Message m = mHandler.obtainMessage(MSG_UPDATE_WAKELOCKS);
            mHandler.sendMessageDelayed(m, DELAY_UPDATE_WAKELOCKS);
        }
    }

    private void requestImmediateCpuUpdate() {
        mHandler.removeMessages(MSG_UPDATE_WAKELOCKS);
        mHandler.sendEmptyMessage(MSG_UPDATE_WAKELOCKS);
    }

    public void setRecordAllHistoryLocked(boolean enabled) {
        mRecordAllHistory = enabled;
        if (!enabled) {
            // Clear out any existing state.
            mActiveEvents.removeEvents(HistoryItem.EVENT_WAKE_LOCK);
            mActiveEvents.removeEvents(HistoryItem.EVENT_ALARM);
            // Record the currently running processes as stopping, now that we are no
            // longer tracking them.
            HashMap<String, SparseIntArray> active = mActiveEvents.getStateForEvent(
                    HistoryItem.EVENT_PROC);
            if (active != null) {
                long mSecRealtime = mClocks.elapsedRealtime();
                final long mSecUptime = mClocks.uptimeMillis();
                for (HashMap.Entry<String, SparseIntArray> ent : active.entrySet()) {
                    SparseIntArray uids = ent.getValue();
                    for (int j=0; j<uids.size(); j++) {
                        addHistoryEventLocked(mSecRealtime, mSecUptime,
                                HistoryItem.EVENT_PROC_FINISH, ent.getKey(), uids.keyAt(j));
                    }
                }
            }
        } else {
            // Record the currently running processes as starting, now that we are tracking them.
            HashMap<String, SparseIntArray> active = mActiveEvents.getStateForEvent(
                    HistoryItem.EVENT_PROC);
            if (active != null) {
                long mSecRealtime = mClocks.elapsedRealtime();
                final long mSecUptime = mClocks.uptimeMillis();
                for (HashMap.Entry<String, SparseIntArray> ent : active.entrySet()) {
                    SparseIntArray uids = ent.getValue();
                    for (int j=0; j<uids.size(); j++) {
                        addHistoryEventLocked(mSecRealtime, mSecUptime,
                                HistoryItem.EVENT_PROC_START, ent.getKey(), uids.keyAt(j));
                    }
                }
            }
        }
    }

    public void setNoAutoReset(boolean enabled) {
        mNoAutoReset = enabled;
    }

    private String mInitialAcquireWakeName;
    private int mInitialAcquireWakeUid = -1;

    public void noteStartWakeLocked(int uid, int pid, String name, String historyName, int type,
            boolean unimportantForLogging, long elapsedRealtime, long uptime) {
        uid = mapUid(uid);
        if (type == WAKE_TYPE_PARTIAL) {
            // Only care about partial wake locks, since full wake locks
            // will be canceled when the user puts the screen to sleep.
            aggregateLastWakeupUptimeLocked(uptime);
            if (historyName == null) {
                historyName = name;
            }
            if (mRecordAllHistory) {
                if (mActiveEvents.updateState(HistoryItem.EVENT_WAKE_LOCK_START, historyName,
                        uid, 0)) {
                    addHistoryEventLocked(elapsedRealtime, uptime,
                            HistoryItem.EVENT_WAKE_LOCK_START, historyName, uid);
                }
            }
            if (mWakeLockNesting == 0) {
                mHistoryCur.states |= HistoryItem.STATE_WAKE_LOCK_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Start wake lock to: "
                        + Integer.toHexString(mHistoryCur.states));
                mHistoryCur.wakelockTag = mHistoryCur.localWakelockTag;
                mHistoryCur.wakelockTag.string = mInitialAcquireWakeName = historyName;
                mHistoryCur.wakelockTag.uid = mInitialAcquireWakeUid = uid;
                mWakeLockImportant = !unimportantForLogging;
                addHistoryRecordLocked(elapsedRealtime, uptime);
            } else if (!mWakeLockImportant && !unimportantForLogging
                    && mHistoryLastWritten.cmd == HistoryItem.CMD_UPDATE) {
                if (mHistoryLastWritten.wakelockTag != null) {
                    // We'll try to update the last tag.
                    mHistoryLastWritten.wakelockTag = null;
                    mHistoryCur.wakelockTag = mHistoryCur.localWakelockTag;
                    mHistoryCur.wakelockTag.string = mInitialAcquireWakeName = historyName;
                    mHistoryCur.wakelockTag.uid = mInitialAcquireWakeUid = uid;
                    addHistoryRecordLocked(elapsedRealtime, uptime);
                }
                mWakeLockImportant = true;
            }
            mWakeLockNesting++;
        }
        if (uid >= 0) {
            if (mOnBatteryScreenOffTimeBase.isRunning()) {
                // We only update the cpu time when a wake lock is acquired if the screen is off.
                // If the screen is on, we don't distribute the power amongst partial wakelocks.
                if (DEBUG_ENERGY_CPU) {
                    Slog.d(TAG, "Updating cpu time because of +wake_lock");
                }
                requestWakelockCpuUpdate();
            }
            getUidStatsLocked(uid).noteStartWakeLocked(pid, name, type, elapsedRealtime);
        }
    }

    public void noteStopWakeLocked(int uid, int pid, String name, String historyName, int type,
            long elapsedRealtime, long uptime) {
        uid = mapUid(uid);
        if (type == WAKE_TYPE_PARTIAL) {
            mWakeLockNesting--;
            if (mRecordAllHistory) {
                if (historyName == null) {
                    historyName = name;
                }
                if (mActiveEvents.updateState(HistoryItem.EVENT_WAKE_LOCK_FINISH, historyName,
                        uid, 0)) {
                    addHistoryEventLocked(elapsedRealtime, uptime,
                            HistoryItem.EVENT_WAKE_LOCK_FINISH, historyName, uid);
                }
            }
            if (mWakeLockNesting == 0) {
                mHistoryCur.states &= ~HistoryItem.STATE_WAKE_LOCK_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Stop wake lock to: "
                        + Integer.toHexString(mHistoryCur.states));
                mInitialAcquireWakeName = null;
                mInitialAcquireWakeUid = -1;
                addHistoryRecordLocked(elapsedRealtime, uptime);
            }
        }
        if (uid >= 0) {
            if (mOnBatteryScreenOffTimeBase.isRunning()) {
                if (DEBUG_ENERGY_CPU) {
                    Slog.d(TAG, "Updating cpu time because of -wake_lock");
                }
                requestWakelockCpuUpdate();
            }
            getUidStatsLocked(uid).noteStopWakeLocked(pid, name, type, elapsedRealtime);
        }
    }

    public void noteStartWakeFromSourceLocked(WorkSource ws, int pid, String name,
            String historyName, int type, boolean unimportantForLogging) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        final int N = ws.size();
        for (int i=0; i<N; i++) {
            noteStartWakeLocked(ws.get(i), pid, name, historyName, type, unimportantForLogging,
                    elapsedRealtime, uptime);
        }
    }

    public void noteChangeWakelockFromSourceLocked(WorkSource ws, int pid, String name,
            String historyName, int type, WorkSource newWs, int newPid, String newName,
            String newHistoryName, int newType, boolean newUnimportantForLogging) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        // For correct semantics, we start the need worksources first, so that we won't
        // make inappropriate history items as if all wake locks went away and new ones
        // appeared.  This is okay because tracking of wake locks allows nesting.
        final int NN = newWs.size();
        for (int i=0; i<NN; i++) {
            noteStartWakeLocked(newWs.get(i), newPid, newName, newHistoryName, newType,
                    newUnimportantForLogging, elapsedRealtime, uptime);
        }
        final int NO = ws.size();
        for (int i=0; i<NO; i++) {
            noteStopWakeLocked(ws.get(i), pid, name, historyName, type, elapsedRealtime, uptime);
        }
    }

    public void noteStopWakeFromSourceLocked(WorkSource ws, int pid, String name,
            String historyName, int type) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        final int N = ws.size();
        for (int i=0; i<N; i++) {
            noteStopWakeLocked(ws.get(i), pid, name, historyName, type, elapsedRealtime, uptime);
        }
    }

    public void noteLongPartialWakelockStart(String name, String historyName, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (historyName == null) {
            historyName = name;
        }
        if (!mActiveEvents.updateState(HistoryItem.EVENT_LONG_WAKE_LOCK_START, historyName, uid,
                0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_LONG_WAKE_LOCK_START,
                historyName, uid);
    }

    public void noteLongPartialWakelockFinish(String name, String historyName, int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (historyName == null) {
            historyName = name;
        }
        if (!mActiveEvents.updateState(HistoryItem.EVENT_LONG_WAKE_LOCK_FINISH, historyName, uid,
                0)) {
            return;
        }
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_LONG_WAKE_LOCK_FINISH,
                historyName, uid);
    }

    void aggregateLastWakeupUptimeLocked(long uptimeMs) {
        if (mLastWakeupReason != null) {
            long deltaUptime = uptimeMs - mLastWakeupUptimeMs;
            SamplingTimer timer = getWakeupReasonTimerLocked(mLastWakeupReason);
            timer.add(deltaUptime * 1000, 1); // time in in microseconds
            mLastWakeupReason = null;
        }
    }

    public void noteWakeupReasonLocked(String reason) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (DEBUG_HISTORY) Slog.v(TAG, "Wakeup reason \"" + reason +"\": "
                + Integer.toHexString(mHistoryCur.states));
        aggregateLastWakeupUptimeLocked(uptime);
        mHistoryCur.wakeReasonTag = mHistoryCur.localWakeReasonTag;
        mHistoryCur.wakeReasonTag.string = reason;
        mHistoryCur.wakeReasonTag.uid = 0;
        mLastWakeupReason = reason;
        mLastWakeupUptimeMs = uptime;
        addHistoryRecordLocked(elapsedRealtime, uptime);
    }

    public boolean startAddingCpuLocked() {
        mHandler.removeMessages(MSG_UPDATE_WAKELOCKS);
        return mOnBatteryInternal;
    }

    public void finishAddingCpuLocked(int totalUTime, int totalSTime, int statUserTime,
                                      int statSystemTime, int statIOWaitTime, int statIrqTime,
                                      int statSoftIrqTime, int statIdleTime) {
        if (DEBUG) Slog.d(TAG, "Adding cpu: tuser=" + totalUTime + " tsys=" + totalSTime
                + " user=" + statUserTime + " sys=" + statSystemTime
                + " io=" + statIOWaitTime + " irq=" + statIrqTime
                + " sirq=" + statSoftIrqTime + " idle=" + statIdleTime);
        mCurStepCpuUserTime += totalUTime;
        mCurStepCpuSystemTime += totalSTime;
        mCurStepStatUserTime += statUserTime;
        mCurStepStatSystemTime += statSystemTime;
        mCurStepStatIOWaitTime += statIOWaitTime;
        mCurStepStatIrqTime += statIrqTime;
        mCurStepStatSoftIrqTime += statSoftIrqTime;
        mCurStepStatIdleTime += statIdleTime;
    }

    public void noteProcessDiedLocked(int uid, int pid) {
        uid = mapUid(uid);
        Uid u = mUidStats.get(uid);
        if (u != null) {
            u.mPids.remove(pid);
        }
    }

    public long getProcessWakeTime(int uid, int pid, long realtime) {
        uid = mapUid(uid);
        Uid u = mUidStats.get(uid);
        if (u != null) {
            Uid.Pid p = u.mPids.get(pid);
            if (p != null) {
                return p.mWakeSumMs + (p.mWakeNesting > 0 ? (realtime - p.mWakeStartMs) : 0);
            }
        }
        return 0;
    }

    public void reportExcessiveWakeLocked(int uid, String proc, long overTime, long usedTime) {
        uid = mapUid(uid);
        Uid u = mUidStats.get(uid);
        if (u != null) {
            u.reportExcessiveWakeLocked(proc, overTime, usedTime);
        }
    }

    public void reportExcessiveCpuLocked(int uid, String proc, long overTime, long usedTime) {
        uid = mapUid(uid);
        Uid u = mUidStats.get(uid);
        if (u != null) {
            u.reportExcessiveCpuLocked(proc, overTime, usedTime);
        }
    }

    int mSensorNesting;

    public void noteStartSensorLocked(int uid, int sensor) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mSensorNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_SENSOR_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Start sensor to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        mSensorNesting++;
        getUidStatsLocked(uid).noteStartSensor(sensor, elapsedRealtime);
    }

    public void noteStopSensorLocked(int uid, int sensor) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        mSensorNesting--;
        if (mSensorNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_SENSOR_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Stop sensor to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        getUidStatsLocked(uid).noteStopSensor(sensor, elapsedRealtime);
    }

    int mGpsNesting;

    public void noteStartGpsLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mGpsNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_GPS_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Start GPS to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        mGpsNesting++;
        getUidStatsLocked(uid).noteStartGps(elapsedRealtime);
    }

    public void noteStopGpsLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        mGpsNesting--;
        if (mGpsNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_GPS_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Stop GPS to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        getUidStatsLocked(uid).noteStopGps(elapsedRealtime);
    }

    public void noteScreenStateLocked(int state) {
        if (mScreenState != state) {
            recordDailyStatsIfNeededLocked(true);
            final int oldState = mScreenState;
            mScreenState = state;
            if (DEBUG) Slog.v(TAG, "Screen state: oldState=" + Display.stateToString(oldState)
                    + ", newState=" + Display.stateToString(state));

            if (state != Display.STATE_UNKNOWN) {
                int stepState = state-1;
                if (stepState < 4) {
                    mModStepMode |= (mCurStepMode&STEP_LEVEL_MODE_SCREEN_STATE) ^ stepState;
                    mCurStepMode = (mCurStepMode&~STEP_LEVEL_MODE_SCREEN_STATE) | stepState;
                } else {
                    Slog.wtf(TAG, "Unexpected screen state: " + state);
                }
            }

            if (state == Display.STATE_ON) {
                // Screen turning on.
                final long elapsedRealtime = mClocks.elapsedRealtime();
                final long uptime = mClocks.uptimeMillis();
                mHistoryCur.states |= HistoryItem.STATE_SCREEN_ON_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Screen on to: "
                        + Integer.toHexString(mHistoryCur.states));
                addHistoryRecordLocked(elapsedRealtime, uptime);
                mScreenOnTimer.startRunningLocked(elapsedRealtime);
                if (mScreenBrightnessBin >= 0) {
                    mScreenBrightnessTimer[mScreenBrightnessBin].startRunningLocked(elapsedRealtime);
                }

                updateTimeBasesLocked(mOnBatteryTimeBase.isRunning(), false,
                        mClocks.uptimeMillis() * 1000, elapsedRealtime * 1000);

                // Fake a wake lock, so we consider the device waked as long
                // as the screen is on.
                noteStartWakeLocked(-1, -1, "screen", null, WAKE_TYPE_PARTIAL, false,
                        elapsedRealtime, uptime);

                // Update discharge amounts.
                if (mOnBatteryInternal) {
                    updateDischargeScreenLevelsLocked(false, true);
                }
            } else if (oldState == Display.STATE_ON) {
                // Screen turning off or dozing.
                final long elapsedRealtime = mClocks.elapsedRealtime();
                final long uptime = mClocks.uptimeMillis();
                mHistoryCur.states &= ~HistoryItem.STATE_SCREEN_ON_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Screen off to: "
                        + Integer.toHexString(mHistoryCur.states));
                addHistoryRecordLocked(elapsedRealtime, uptime);
                mScreenOnTimer.stopRunningLocked(elapsedRealtime);
                if (mScreenBrightnessBin >= 0) {
                    mScreenBrightnessTimer[mScreenBrightnessBin].stopRunningLocked(elapsedRealtime);
                }

                noteStopWakeLocked(-1, -1, "screen", "screen", WAKE_TYPE_PARTIAL,
                        elapsedRealtime, uptime);

                updateTimeBasesLocked(mOnBatteryTimeBase.isRunning(), true,
                        mClocks.uptimeMillis() * 1000, elapsedRealtime * 1000);

                // Update discharge amounts.
                if (mOnBatteryInternal) {
                    updateDischargeScreenLevelsLocked(true, false);
                }
            }
        }
    }

    public void noteScreenBrightnessLocked(int brightness) {
        // Bin the brightness.
        int bin = brightness / (256/NUM_SCREEN_BRIGHTNESS_BINS);
        if (bin < 0) bin = 0;
        else if (bin >= NUM_SCREEN_BRIGHTNESS_BINS) bin = NUM_SCREEN_BRIGHTNESS_BINS-1;
        if (mScreenBrightnessBin != bin) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states = (mHistoryCur.states&~HistoryItem.STATE_BRIGHTNESS_MASK)
                    | (bin << HistoryItem.STATE_BRIGHTNESS_SHIFT);
            if (DEBUG_HISTORY) Slog.v(TAG, "Screen brightness " + bin + " to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            if (mScreenState == Display.STATE_ON) {
                if (mScreenBrightnessBin >= 0) {
                    mScreenBrightnessTimer[mScreenBrightnessBin].stopRunningLocked(elapsedRealtime);
                }
                mScreenBrightnessTimer[bin].startRunningLocked(elapsedRealtime);
            }
            mScreenBrightnessBin = bin;
        }
    }

    public void noteUserActivityLocked(int uid, int event) {
        if (mOnBatteryInternal) {
            uid = mapUid(uid);
            getUidStatsLocked(uid).noteUserActivityLocked(event);
        }
    }

    public void noteWakeUpLocked(String reason, int reasonUid) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_SCREEN_WAKE_UP,
                reason, reasonUid);
    }

    public void noteInteractiveLocked(boolean interactive) {
        if (mInteractive != interactive) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            mInteractive = interactive;
            if (DEBUG) Slog.v(TAG, "Interactive: " + interactive);
            if (interactive) {
                mInteractiveTimer.startRunningLocked(elapsedRealtime);
            } else {
                mInteractiveTimer.stopRunningLocked(elapsedRealtime);
            }
        }
    }

    public void noteConnectivityChangedLocked(int type, String extra) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_CONNECTIVITY_CHANGED,
                extra, type);
        mNumConnectivityChange++;
    }

    private void noteMobileRadioApWakeupLocked(final long elapsedRealtimeMillis,
            final long uptimeMillis, int uid) {
        uid = mapUid(uid);
        addHistoryEventLocked(elapsedRealtimeMillis, uptimeMillis, HistoryItem.EVENT_WAKEUP_AP, "",
                uid);
        getUidStatsLocked(uid).noteMobileRadioApWakeupLocked();
    }

    public void noteMobileRadioPowerState(int powerState, long timestampNs, int uid) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mMobileRadioPowerState != powerState) {
            long realElapsedRealtimeMs;
            final boolean active =
                    powerState == DataConnectionRealTimeInfo.DC_POWER_STATE_MEDIUM
                            || powerState == DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
            if (active) {
                if (uid > 0) {
                    noteMobileRadioApWakeupLocked(elapsedRealtime, uptime, uid);
                }

                mMobileRadioActiveStartTime = realElapsedRealtimeMs = timestampNs / (1000 * 1000);
                mHistoryCur.states |= HistoryItem.STATE_MOBILE_RADIO_ACTIVE_FLAG;
            } else {
                realElapsedRealtimeMs = timestampNs / (1000*1000);
                long lastUpdateTimeMs = mMobileRadioActiveStartTime;
                if (realElapsedRealtimeMs < lastUpdateTimeMs) {
                    Slog.wtf(TAG, "Data connection inactive timestamp " + realElapsedRealtimeMs
                            + " is before start time " + lastUpdateTimeMs);
                    realElapsedRealtimeMs = elapsedRealtime;
                } else if (realElapsedRealtimeMs < elapsedRealtime) {
                    mMobileRadioActiveAdjustedTime.addCountLocked(elapsedRealtime
                            - realElapsedRealtimeMs);
                }
                mHistoryCur.states &= ~HistoryItem.STATE_MOBILE_RADIO_ACTIVE_FLAG;
            }
            if (DEBUG_HISTORY) Slog.v(TAG, "Mobile network active " + active + " to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mMobileRadioPowerState = powerState;
            if (active) {
                mMobileRadioActiveTimer.startRunningLocked(elapsedRealtime);
                mMobileRadioActivePerAppTimer.startRunningLocked(elapsedRealtime);
            } else {
                mMobileRadioActiveTimer.stopRunningLocked(realElapsedRealtimeMs);
                updateMobileRadioStateLocked(realElapsedRealtimeMs, null);
                mMobileRadioActivePerAppTimer.stopRunningLocked(realElapsedRealtimeMs);
            }
        }
    }

    public void notePowerSaveMode(boolean enabled) {
        if (mPowerSaveModeEnabled != enabled) {
            int stepState = enabled ? STEP_LEVEL_MODE_POWER_SAVE : 0;
            mModStepMode |= (mCurStepMode&STEP_LEVEL_MODE_POWER_SAVE) ^ stepState;
            mCurStepMode = (mCurStepMode&~STEP_LEVEL_MODE_POWER_SAVE) | stepState;
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mPowerSaveModeEnabled = enabled;
            if (enabled) {
                mHistoryCur.states2 |= HistoryItem.STATE2_POWER_SAVE_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Power save mode enabled to: "
                        + Integer.toHexString(mHistoryCur.states2));
                mPowerSaveModeEnabledTimer.startRunningLocked(elapsedRealtime);
            } else {
                mHistoryCur.states2 &= ~HistoryItem.STATE2_POWER_SAVE_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Power save mode disabled to: "
                        + Integer.toHexString(mHistoryCur.states2));
                mPowerSaveModeEnabledTimer.stopRunningLocked(elapsedRealtime);
            }
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
    }

    public void noteDeviceIdleModeLocked(int mode, String activeReason, int activeUid) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        boolean nowIdling = mode == DEVICE_IDLE_MODE_DEEP;
        if (mDeviceIdling && !nowIdling && activeReason == null) {
            // We don't go out of general idling mode until explicitly taken out of
            // device idle through going active or significant motion.
            nowIdling = true;
        }
        boolean nowLightIdling = mode == DEVICE_IDLE_MODE_LIGHT;
        if (mDeviceLightIdling && !nowLightIdling && !nowIdling && activeReason == null) {
            // We don't go out of general light idling mode until explicitly taken out of
            // device idle through going active or significant motion.
            nowLightIdling = true;
        }
        if (activeReason != null && (mDeviceIdling || mDeviceLightIdling)) {
            addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_ACTIVE,
                    activeReason, activeUid);
        }
        if (mDeviceIdling != nowIdling) {
            mDeviceIdling = nowIdling;
            int stepState = nowIdling ? STEP_LEVEL_MODE_DEVICE_IDLE : 0;
            mModStepMode |= (mCurStepMode&STEP_LEVEL_MODE_DEVICE_IDLE) ^ stepState;
            mCurStepMode = (mCurStepMode&~STEP_LEVEL_MODE_DEVICE_IDLE) | stepState;
            if (nowIdling) {
                mDeviceIdlingTimer.startRunningLocked(elapsedRealtime);
            } else {
                mDeviceIdlingTimer.stopRunningLocked(elapsedRealtime);
            }
        }
        if (mDeviceLightIdling != nowLightIdling) {
            mDeviceLightIdling = nowLightIdling;
            if (nowLightIdling) {
                mDeviceLightIdlingTimer.startRunningLocked(elapsedRealtime);
            } else {
                mDeviceLightIdlingTimer.stopRunningLocked(elapsedRealtime);
            }
        }
        if (mDeviceIdleMode != mode) {
            mHistoryCur.states2 = (mHistoryCur.states2 & ~HistoryItem.STATE2_DEVICE_IDLE_MASK)
                    | (mode << HistoryItem.STATE2_DEVICE_IDLE_SHIFT);
            if (DEBUG_HISTORY) Slog.v(TAG, "Device idle mode changed to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            long lastDuration = elapsedRealtime - mLastIdleTimeStart;
            mLastIdleTimeStart = elapsedRealtime;
            if (mDeviceIdleMode == DEVICE_IDLE_MODE_LIGHT) {
                if (lastDuration > mLongestLightIdleTime) {
                    mLongestLightIdleTime = lastDuration;
                }
                mDeviceIdleModeLightTimer.stopRunningLocked(elapsedRealtime);
            } else if (mDeviceIdleMode == DEVICE_IDLE_MODE_DEEP) {
                if (lastDuration > mLongestFullIdleTime) {
                    mLongestFullIdleTime = lastDuration;
                }
                mDeviceIdleModeFullTimer.stopRunningLocked(elapsedRealtime);
            }
            if (mode == DEVICE_IDLE_MODE_LIGHT) {
                mDeviceIdleModeLightTimer.startRunningLocked(elapsedRealtime);
            } else if (mode == DEVICE_IDLE_MODE_DEEP) {
                mDeviceIdleModeFullTimer.startRunningLocked(elapsedRealtime);
            }
            mDeviceIdleMode = mode;
        }
    }

    public void notePackageInstalledLocked(String pkgName, int versionCode) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_PACKAGE_INSTALLED,
                pkgName, versionCode);
        PackageChange pc = new PackageChange();
        pc.mPackageName = pkgName;
        pc.mUpdate = true;
        pc.mVersionCode = versionCode;
        addPackageChange(pc);
    }

    public void notePackageUninstalledLocked(String pkgName) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        addHistoryEventLocked(elapsedRealtime, uptime, HistoryItem.EVENT_PACKAGE_UNINSTALLED,
                pkgName, 0);
        PackageChange pc = new PackageChange();
        pc.mPackageName = pkgName;
        pc.mUpdate = true;
        addPackageChange(pc);
    }

    private void addPackageChange(PackageChange pc) {
        if (mDailyPackageChanges == null) {
            mDailyPackageChanges = new ArrayList<>();
        }
        mDailyPackageChanges.add(pc);
    }

    public void notePhoneOnLocked() {
        if (!mPhoneOn) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states2 |= HistoryItem.STATE2_PHONE_IN_CALL_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Phone on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mPhoneOn = true;
            mPhoneOnTimer.startRunningLocked(elapsedRealtime);
        }
    }

    public void notePhoneOffLocked() {
        if (mPhoneOn) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states2 &= ~HistoryItem.STATE2_PHONE_IN_CALL_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Phone off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mPhoneOn = false;
            mPhoneOnTimer.stopRunningLocked(elapsedRealtime);
        }
    }

    void stopAllPhoneSignalStrengthTimersLocked(int except) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        for (int i = 0; i < SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            if (i == except) {
                continue;
            }
            while (mPhoneSignalStrengthsTimer[i].isRunningLocked()) {
                mPhoneSignalStrengthsTimer[i].stopRunningLocked(elapsedRealtime);
            }
        }
    }

    private int fixPhoneServiceState(int state, int signalBin) {
        if (mPhoneSimStateRaw == TelephonyManager.SIM_STATE_ABSENT) {
            // In this case we will always be STATE_OUT_OF_SERVICE, so need
            // to infer that we are scanning from other data.
            if (state == ServiceState.STATE_OUT_OF_SERVICE
                    && signalBin > SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                state = ServiceState.STATE_IN_SERVICE;
            }
        }

        return state;
    }

    private void updateAllPhoneStateLocked(int state, int simState, int strengthBin) {
        boolean scanning = false;
        boolean newHistory = false;

        mPhoneServiceStateRaw = state;
        mPhoneSimStateRaw = simState;
        mPhoneSignalStrengthBinRaw = strengthBin;

        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();

        if (simState == TelephonyManager.SIM_STATE_ABSENT) {
            // In this case we will always be STATE_OUT_OF_SERVICE, so need
            // to infer that we are scanning from other data.
            if (state == ServiceState.STATE_OUT_OF_SERVICE
                    && strengthBin > SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                state = ServiceState.STATE_IN_SERVICE;
            }
        }

        // If the phone is powered off, stop all timers.
        if (state == ServiceState.STATE_POWER_OFF) {
            strengthBin = -1;

        // If we are in service, make sure the correct signal string timer is running.
        } else if (state == ServiceState.STATE_IN_SERVICE) {
            // Bin will be changed below.

        // If we're out of service, we are in the lowest signal strength
        // bin and have the scanning bit set.
        } else if (state == ServiceState.STATE_OUT_OF_SERVICE) {
            scanning = true;
            strengthBin = SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            if (!mPhoneSignalScanningTimer.isRunningLocked()) {
                mHistoryCur.states |= HistoryItem.STATE_PHONE_SCANNING_FLAG;
                newHistory = true;
                if (DEBUG_HISTORY) Slog.v(TAG, "Phone started scanning to: "
                        + Integer.toHexString(mHistoryCur.states));
                mPhoneSignalScanningTimer.startRunningLocked(elapsedRealtime);
            }
        }

        if (!scanning) {
            // If we are no longer scanning, then stop the scanning timer.
            if (mPhoneSignalScanningTimer.isRunningLocked()) {
                mHistoryCur.states &= ~HistoryItem.STATE_PHONE_SCANNING_FLAG;
                if (DEBUG_HISTORY) Slog.v(TAG, "Phone stopped scanning to: "
                        + Integer.toHexString(mHistoryCur.states));
                newHistory = true;
                mPhoneSignalScanningTimer.stopRunningLocked(elapsedRealtime);
            }
        }

        if (mPhoneServiceState != state) {
            mHistoryCur.states = (mHistoryCur.states&~HistoryItem.STATE_PHONE_STATE_MASK)
                    | (state << HistoryItem.STATE_PHONE_STATE_SHIFT);
            if (DEBUG_HISTORY) Slog.v(TAG, "Phone state " + state + " to: "
                    + Integer.toHexString(mHistoryCur.states));
            newHistory = true;
            mPhoneServiceState = state;
        }

        if (mPhoneSignalStrengthBin != strengthBin) {
            if (mPhoneSignalStrengthBin >= 0) {
                mPhoneSignalStrengthsTimer[mPhoneSignalStrengthBin].stopRunningLocked(
                        elapsedRealtime);
            }
            if (strengthBin >= 0) {
                if (!mPhoneSignalStrengthsTimer[strengthBin].isRunningLocked()) {
                    mPhoneSignalStrengthsTimer[strengthBin].startRunningLocked(elapsedRealtime);
                }
                mHistoryCur.states = (mHistoryCur.states&~HistoryItem.STATE_PHONE_SIGNAL_STRENGTH_MASK)
                        | (strengthBin << HistoryItem.STATE_PHONE_SIGNAL_STRENGTH_SHIFT);
                if (DEBUG_HISTORY) Slog.v(TAG, "Signal strength " + strengthBin + " to: "
                        + Integer.toHexString(mHistoryCur.states));
                newHistory = true;
            } else {
                stopAllPhoneSignalStrengthTimersLocked(-1);
            }
            mPhoneSignalStrengthBin = strengthBin;
        }

        if (newHistory) {
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
    }

    /**
     * Telephony stack updates the phone state.
     * @param state phone state from ServiceState.getState()
     */
    public void notePhoneStateLocked(int state, int simState) {
        updateAllPhoneStateLocked(state, simState, mPhoneSignalStrengthBinRaw);
    }

    public void notePhoneSignalStrengthLocked(SignalStrength signalStrength) {
        // Bin the strength.
        int bin = signalStrength.getLevel();
        updateAllPhoneStateLocked(mPhoneServiceStateRaw, mPhoneSimStateRaw, bin);
    }

    public void notePhoneDataConnectionStateLocked(int dataType, boolean hasData) {
        int bin = DATA_CONNECTION_NONE;
        if (hasData) {
            switch (dataType) {
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    bin = DATA_CONNECTION_EDGE;
                    break;
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    bin = DATA_CONNECTION_GPRS;
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    bin = DATA_CONNECTION_UMTS;
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    bin = DATA_CONNECTION_CDMA;
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    bin = DATA_CONNECTION_EVDO_0;
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    bin = DATA_CONNECTION_EVDO_A;
                    break;
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    bin = DATA_CONNECTION_1xRTT;
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    bin = DATA_CONNECTION_HSDPA;
                    break;
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    bin = DATA_CONNECTION_HSUPA;
                    break;
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    bin = DATA_CONNECTION_HSPA;
                    break;
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    bin = DATA_CONNECTION_IDEN;
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    bin = DATA_CONNECTION_EVDO_B;
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    bin = DATA_CONNECTION_LTE;
                    break;
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    bin = DATA_CONNECTION_EHRPD;
                    break;
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    bin = DATA_CONNECTION_HSPAP;
                    break;
                default:
                    bin = DATA_CONNECTION_OTHER;
                    break;
            }
        }
        if (DEBUG) Log.i(TAG, "Phone Data Connection -> " + dataType + " = " + hasData);
        if (mPhoneDataConnectionType != bin) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states = (mHistoryCur.states&~HistoryItem.STATE_DATA_CONNECTION_MASK)
                    | (bin << HistoryItem.STATE_DATA_CONNECTION_SHIFT);
            if (DEBUG_HISTORY) Slog.v(TAG, "Data connection " + bin + " to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            if (mPhoneDataConnectionType >= 0) {
                mPhoneDataConnectionsTimer[mPhoneDataConnectionType].stopRunningLocked(
                        elapsedRealtime);
            }
            mPhoneDataConnectionType = bin;
            mPhoneDataConnectionsTimer[bin].startRunningLocked(elapsedRealtime);
        }
    }

    public void noteWifiOnLocked() {
        if (!mWifiOn) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states2 |= HistoryItem.STATE2_WIFI_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mWifiOn = true;
            mWifiOnTimer.startRunningLocked(elapsedRealtime);
            scheduleSyncExternalStatsLocked("wifi-off", ExternalStatsSync.UPDATE_WIFI);
        }
    }

    public void noteWifiOffLocked() {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mWifiOn) {
            mHistoryCur.states2 &= ~HistoryItem.STATE2_WIFI_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mWifiOn = false;
            mWifiOnTimer.stopRunningLocked(elapsedRealtime);
            scheduleSyncExternalStatsLocked("wifi-on", ExternalStatsSync.UPDATE_WIFI);
        }
    }

    public void noteAudioOnLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mAudioOnNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_AUDIO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Audio on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mAudioOnTimer.startRunningLocked(elapsedRealtime);
        }
        mAudioOnNesting++;
        getUidStatsLocked(uid).noteAudioTurnedOnLocked(elapsedRealtime);
    }

    public void noteAudioOffLocked(int uid) {
        if (mAudioOnNesting == 0) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (--mAudioOnNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_AUDIO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Audio off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mAudioOnTimer.stopRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteAudioTurnedOffLocked(elapsedRealtime);
    }

    public void noteVideoOnLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mVideoOnNesting == 0) {
            mHistoryCur.states2 |= HistoryItem.STATE2_VIDEO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Video on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mVideoOnTimer.startRunningLocked(elapsedRealtime);
        }
        mVideoOnNesting++;
        getUidStatsLocked(uid).noteVideoTurnedOnLocked(elapsedRealtime);
    }

    public void noteVideoOffLocked(int uid) {
        if (mVideoOnNesting == 0) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (--mVideoOnNesting == 0) {
            mHistoryCur.states2 &= ~HistoryItem.STATE2_VIDEO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Video off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mVideoOnTimer.stopRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteVideoTurnedOffLocked(elapsedRealtime);
    }

    public void noteResetAudioLocked() {
        if (mAudioOnNesting > 0) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mAudioOnNesting = 0;
            mHistoryCur.states &= ~HistoryItem.STATE_AUDIO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Audio off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mAudioOnTimer.stopAllRunningLocked(elapsedRealtime);
            for (int i=0; i<mUidStats.size(); i++) {
                BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.noteResetAudioLocked(elapsedRealtime);
            }
        }
    }

    public void noteResetVideoLocked() {
        if (mVideoOnNesting > 0) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mAudioOnNesting = 0;
            mHistoryCur.states2 &= ~HistoryItem.STATE2_VIDEO_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Video off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mVideoOnTimer.stopAllRunningLocked(elapsedRealtime);
            for (int i=0; i<mUidStats.size(); i++) {
                BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.noteResetVideoLocked(elapsedRealtime);
            }
        }
    }

    public void noteActivityResumedLocked(int uid) {
        uid = mapUid(uid);
        getUidStatsLocked(uid).noteActivityResumedLocked(mClocks.elapsedRealtime());
    }

    public void noteActivityPausedLocked(int uid) {
        uid = mapUid(uid);
        getUidStatsLocked(uid).noteActivityPausedLocked(mClocks.elapsedRealtime());
    }

    public void noteVibratorOnLocked(int uid, long durationMillis) {
        uid = mapUid(uid);
        getUidStatsLocked(uid).noteVibratorOnLocked(durationMillis);
    }

    public void noteVibratorOffLocked(int uid) {
        uid = mapUid(uid);
        getUidStatsLocked(uid).noteVibratorOffLocked();
    }

    public void noteFlashlightOnLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mFlashlightOnNesting++ == 0) {
            mHistoryCur.states2 |= HistoryItem.STATE2_FLASHLIGHT_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Flashlight on to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mFlashlightOnTimer.startRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteFlashlightTurnedOnLocked(elapsedRealtime);
    }

    public void noteFlashlightOffLocked(int uid) {
        if (mFlashlightOnNesting == 0) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (--mFlashlightOnNesting == 0) {
            mHistoryCur.states2 &= ~HistoryItem.STATE2_FLASHLIGHT_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Flashlight off to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mFlashlightOnTimer.stopRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteFlashlightTurnedOffLocked(elapsedRealtime);
    }

    public void noteCameraOnLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mCameraOnNesting++ == 0) {
            mHistoryCur.states2 |= HistoryItem.STATE2_CAMERA_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Camera on to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mCameraOnTimer.startRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteCameraTurnedOnLocked(elapsedRealtime);
    }

    public void noteCameraOffLocked(int uid) {
        if (mCameraOnNesting == 0) {
            return;
        }
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (--mCameraOnNesting == 0) {
            mHistoryCur.states2 &= ~HistoryItem.STATE2_CAMERA_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Camera off to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mCameraOnTimer.stopRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteCameraTurnedOffLocked(elapsedRealtime);
    }

    public void noteResetCameraLocked() {
        if (mCameraOnNesting > 0) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mCameraOnNesting = 0;
            mHistoryCur.states2 &= ~HistoryItem.STATE2_CAMERA_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Camera off to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mCameraOnTimer.stopAllRunningLocked(elapsedRealtime);
            for (int i=0; i<mUidStats.size(); i++) {
                BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.noteResetCameraLocked(elapsedRealtime);
            }
        }
    }

    public void noteResetFlashlightLocked() {
        if (mFlashlightOnNesting > 0) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mFlashlightOnNesting = 0;
            mHistoryCur.states2 &= ~HistoryItem.STATE2_FLASHLIGHT_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Flashlight off to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mFlashlightOnTimer.stopAllRunningLocked(elapsedRealtime);
            for (int i=0; i<mUidStats.size(); i++) {
                BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.noteResetFlashlightLocked(elapsedRealtime);
            }
        }
    }

    private void noteBluetoothScanStartedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = SystemClock.elapsedRealtime();
        final long uptime = SystemClock.uptimeMillis();
        if (mBluetoothScanNesting == 0) {
            mHistoryCur.states2 |= HistoryItem.STATE2_BLUETOOTH_SCAN_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "BLE scan started for: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mBluetoothScanTimer.startRunningLocked(elapsedRealtime);
        }
        mBluetoothScanNesting++;
        getUidStatsLocked(uid).noteBluetoothScanStartedLocked(elapsedRealtime);
    }

    public void noteBluetoothScanStartedFromSourceLocked(WorkSource ws) {
        final int N = ws.size();
        for (int i = 0; i < N; i++) {
            noteBluetoothScanStartedLocked(ws.get(i));
        }
    }

    private void noteBluetoothScanStoppedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = SystemClock.elapsedRealtime();
        final long uptime = SystemClock.uptimeMillis();
        mBluetoothScanNesting--;
        if (mBluetoothScanNesting == 0) {
            mHistoryCur.states2 &= ~HistoryItem.STATE2_BLUETOOTH_SCAN_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "BLE scan stopped for: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mBluetoothScanTimer.stopRunningLocked(elapsedRealtime);
        }
        getUidStatsLocked(uid).noteBluetoothScanStoppedLocked(elapsedRealtime);
    }

    public void noteBluetoothScanStoppedFromSourceLocked(WorkSource ws) {
        final int N = ws.size();
        for (int i = 0; i < N; i++) {
            noteBluetoothScanStoppedLocked(ws.get(i));
        }
    }

    public void noteResetBluetoothScanLocked() {
        if (mBluetoothScanNesting > 0) {
            final long elapsedRealtime = SystemClock.elapsedRealtime();
            final long uptime = SystemClock.uptimeMillis();
            mBluetoothScanNesting = 0;
            mHistoryCur.states2 &= ~HistoryItem.STATE2_BLUETOOTH_SCAN_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "BLE can stopped for: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mBluetoothScanTimer.stopAllRunningLocked(elapsedRealtime);
            for (int i=0; i<mUidStats.size(); i++) {
                BatteryStatsImpl.Uid uid = mUidStats.valueAt(i);
                uid.noteResetBluetoothScanLocked(elapsedRealtime);
            }
        }
    }

    private void noteWifiRadioApWakeupLocked(final long elapsedRealtimeMillis,
            final long uptimeMillis, int uid) {
        uid = mapUid(uid);
        addHistoryEventLocked(elapsedRealtimeMillis, uptimeMillis, HistoryItem.EVENT_WAKEUP_AP, "",
                uid);
        getUidStatsLocked(uid).noteWifiRadioApWakeupLocked();
    }

    public void noteWifiRadioPowerState(int powerState, long timestampNs, int uid) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mWifiRadioPowerState != powerState) {
            final boolean active =
                    powerState == DataConnectionRealTimeInfo.DC_POWER_STATE_MEDIUM
                            || powerState == DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
            if (active) {
                if (uid > 0) {
                    noteWifiRadioApWakeupLocked(elapsedRealtime, uptime, uid);
                }
                mHistoryCur.states |= HistoryItem.STATE_WIFI_RADIO_ACTIVE_FLAG;
            } else {
                mHistoryCur.states &= ~HistoryItem.STATE_WIFI_RADIO_ACTIVE_FLAG;
            }
            if (DEBUG_HISTORY) Slog.v(TAG, "Wifi network active " + active + " to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mWifiRadioPowerState = powerState;
        }
    }

    public void noteWifiRunningLocked(WorkSource ws) {
        if (!mGlobalWifiRunning) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states2 |= HistoryItem.STATE2_WIFI_RUNNING_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI running to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mGlobalWifiRunning = true;
            mGlobalWifiRunningTimer.startRunningLocked(elapsedRealtime);
            int N = ws.size();
            for (int i=0; i<N; i++) {
                int uid = mapUid(ws.get(i));
                getUidStatsLocked(uid).noteWifiRunningLocked(elapsedRealtime);
            }
            scheduleSyncExternalStatsLocked("wifi-running", ExternalStatsSync.UPDATE_WIFI);
        } else {
            Log.w(TAG, "noteWifiRunningLocked -- called while WIFI running");
        }
    }

    public void noteWifiRunningChangedLocked(WorkSource oldWs, WorkSource newWs) {
        if (mGlobalWifiRunning) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            int N = oldWs.size();
            for (int i=0; i<N; i++) {
                int uid = mapUid(oldWs.get(i));
                getUidStatsLocked(uid).noteWifiStoppedLocked(elapsedRealtime);
            }
            N = newWs.size();
            for (int i=0; i<N; i++) {
                int uid = mapUid(newWs.get(i));
                getUidStatsLocked(uid).noteWifiRunningLocked(elapsedRealtime);
            }
        } else {
            Log.w(TAG, "noteWifiRunningChangedLocked -- called while WIFI not running");
        }
    }

    public void noteWifiStoppedLocked(WorkSource ws) {
        if (mGlobalWifiRunning) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            mHistoryCur.states2 &= ~HistoryItem.STATE2_WIFI_RUNNING_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI stopped to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
            mGlobalWifiRunning = false;
            mGlobalWifiRunningTimer.stopRunningLocked(elapsedRealtime);
            int N = ws.size();
            for (int i=0; i<N; i++) {
                int uid = mapUid(ws.get(i));
                getUidStatsLocked(uid).noteWifiStoppedLocked(elapsedRealtime);
            }
            scheduleSyncExternalStatsLocked("wifi-stopped", ExternalStatsSync.UPDATE_WIFI);
        } else {
            Log.w(TAG, "noteWifiStoppedLocked -- called while WIFI not running");
        }
    }

    public void noteWifiStateLocked(int wifiState, String accessPoint) {
        if (DEBUG) Log.i(TAG, "WiFi state -> " + wifiState);
        if (mWifiState != wifiState) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            if (mWifiState >= 0) {
                mWifiStateTimer[mWifiState].stopRunningLocked(elapsedRealtime);
            }
            mWifiState = wifiState;
            mWifiStateTimer[wifiState].startRunningLocked(elapsedRealtime);
            scheduleSyncExternalStatsLocked("wifi-state", ExternalStatsSync.UPDATE_WIFI);
        }
    }

    public void noteWifiSupplicantStateChangedLocked(int supplState, boolean failedAuth) {
        if (DEBUG) Log.i(TAG, "WiFi suppl state -> " + supplState);
        if (mWifiSupplState != supplState) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            if (mWifiSupplState >= 0) {
                mWifiSupplStateTimer[mWifiSupplState].stopRunningLocked(elapsedRealtime);
            }
            mWifiSupplState = supplState;
            mWifiSupplStateTimer[supplState].startRunningLocked(elapsedRealtime);
            mHistoryCur.states2 =
                    (mHistoryCur.states2&~HistoryItem.STATE2_WIFI_SUPPL_STATE_MASK)
                    | (supplState << HistoryItem.STATE2_WIFI_SUPPL_STATE_SHIFT);
            if (DEBUG_HISTORY) Slog.v(TAG, "Wifi suppl state " + supplState + " to: "
                    + Integer.toHexString(mHistoryCur.states2));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
    }

    void stopAllWifiSignalStrengthTimersLocked(int except) {
        final long elapsedRealtime = mClocks.elapsedRealtime();
        for (int i = 0; i < NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            if (i == except) {
                continue;
            }
            while (mWifiSignalStrengthsTimer[i].isRunningLocked()) {
                mWifiSignalStrengthsTimer[i].stopRunningLocked(elapsedRealtime);
            }
        }
    }

    public void noteWifiRssiChangedLocked(int newRssi) {
        int strengthBin = WifiManager.calculateSignalLevel(newRssi, NUM_WIFI_SIGNAL_STRENGTH_BINS);
        if (DEBUG) Log.i(TAG, "WiFi rssi -> " + newRssi + " bin=" + strengthBin);
        if (mWifiSignalStrengthBin != strengthBin) {
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            if (mWifiSignalStrengthBin >= 0) {
                mWifiSignalStrengthsTimer[mWifiSignalStrengthBin].stopRunningLocked(
                        elapsedRealtime);
            }
            if (strengthBin >= 0) {
                if (!mWifiSignalStrengthsTimer[strengthBin].isRunningLocked()) {
                    mWifiSignalStrengthsTimer[strengthBin].startRunningLocked(elapsedRealtime);
                }
                mHistoryCur.states2 =
                        (mHistoryCur.states2&~HistoryItem.STATE2_WIFI_SIGNAL_STRENGTH_MASK)
                        | (strengthBin << HistoryItem.STATE2_WIFI_SIGNAL_STRENGTH_SHIFT);
                if (DEBUG_HISTORY) Slog.v(TAG, "Wifi signal strength " + strengthBin + " to: "
                        + Integer.toHexString(mHistoryCur.states2));
                addHistoryRecordLocked(elapsedRealtime, uptime);
            } else {
                stopAllWifiSignalStrengthTimersLocked(-1);
            }
            mWifiSignalStrengthBin = strengthBin;
        }
    }

    int mWifiFullLockNesting = 0;

    public void noteFullWifiLockAcquiredLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mWifiFullLockNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_WIFI_FULL_LOCK_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI full lock on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        mWifiFullLockNesting++;
        getUidStatsLocked(uid).noteFullWifiLockAcquiredLocked(elapsedRealtime);
    }

    public void noteFullWifiLockReleasedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        mWifiFullLockNesting--;
        if (mWifiFullLockNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_WIFI_FULL_LOCK_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI full lock off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        getUidStatsLocked(uid).noteFullWifiLockReleasedLocked(elapsedRealtime);
    }

    int mWifiScanNesting = 0;

    public void noteWifiScanStartedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mWifiScanNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_WIFI_SCAN_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI scan started for: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        mWifiScanNesting++;
        getUidStatsLocked(uid).noteWifiScanStartedLocked(elapsedRealtime);
    }

    public void noteWifiScanStoppedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        mWifiScanNesting--;
        if (mWifiScanNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_WIFI_SCAN_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI scan stopped for: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        getUidStatsLocked(uid).noteWifiScanStoppedLocked(elapsedRealtime);
    }

    public void noteWifiBatchedScanStartedLocked(int uid, int csph) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        getUidStatsLocked(uid).noteWifiBatchedScanStartedLocked(csph, elapsedRealtime);
    }

    public void noteWifiBatchedScanStoppedLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        getUidStatsLocked(uid).noteWifiBatchedScanStoppedLocked(elapsedRealtime);
    }

    int mWifiMulticastNesting = 0;

    public void noteWifiMulticastEnabledLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        if (mWifiMulticastNesting == 0) {
            mHistoryCur.states |= HistoryItem.STATE_WIFI_MULTICAST_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI multicast on to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        mWifiMulticastNesting++;
        getUidStatsLocked(uid).noteWifiMulticastEnabledLocked(elapsedRealtime);
    }

    public void noteWifiMulticastDisabledLocked(int uid) {
        uid = mapUid(uid);
        final long elapsedRealtime = mClocks.elapsedRealtime();
        final long uptime = mClocks.uptimeMillis();
        mWifiMulticastNesting--;
        if (mWifiMulticastNesting == 0) {
            mHistoryCur.states &= ~HistoryItem.STATE_WIFI_MULTICAST_ON_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "WIFI multicast off to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(elapsedRealtime, uptime);
        }
        getUidStatsLocked(uid).noteWifiMulticastDisabledLocked(elapsedRealtime);
    }

    public void noteFullWifiLockAcquiredFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteFullWifiLockAcquiredLocked(ws.get(i));
        }
    }

    public void noteFullWifiLockReleasedFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteFullWifiLockReleasedLocked(ws.get(i));
        }
    }

    public void noteWifiScanStartedFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiScanStartedLocked(ws.get(i));
        }
    }

    public void noteWifiScanStoppedFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiScanStoppedLocked(ws.get(i));
        }
    }

    public void noteWifiBatchedScanStartedFromSourceLocked(WorkSource ws, int csph) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiBatchedScanStartedLocked(ws.get(i), csph);
        }
    }

    public void noteWifiBatchedScanStoppedFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiBatchedScanStoppedLocked(ws.get(i));
        }
    }

    public void noteWifiMulticastEnabledFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiMulticastEnabledLocked(ws.get(i));
        }
    }

    public void noteWifiMulticastDisabledFromSourceLocked(WorkSource ws) {
        int N = ws.size();
        for (int i=0; i<N; i++) {
            noteWifiMulticastDisabledLocked(ws.get(i));
        }
    }

    private static String[] includeInStringArray(String[] array, String str) {
        if (ArrayUtils.indexOf(array, str) >= 0) {
            return array;
        }
        String[] newArray = new String[array.length+1];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = str;
        return newArray;
    }

    private static String[] excludeFromStringArray(String[] array, String str) {
        int index = ArrayUtils.indexOf(array, str);
        if (index >= 0) {
            String[] newArray = new String[array.length-1];
            if (index > 0) {
                System.arraycopy(array, 0, newArray, 0, index);
            }
            if (index < array.length-1) {
                System.arraycopy(array, index+1, newArray, index, array.length-index-1);
            }
            return newArray;
        }
        return array;
    }

    public void noteNetworkInterfaceTypeLocked(String iface, int networkType) {
        if (TextUtils.isEmpty(iface)) return;
        if (ConnectivityManager.isNetworkTypeMobile(networkType)) {
            mMobileIfaces = includeInStringArray(mMobileIfaces, iface);
            if (DEBUG) Slog.d(TAG, "Note mobile iface " + iface + ": " + mMobileIfaces);
        } else {
            mMobileIfaces = excludeFromStringArray(mMobileIfaces, iface);
            if (DEBUG) Slog.d(TAG, "Note non-mobile iface " + iface + ": " + mMobileIfaces);
        }
        if (ConnectivityManager.isNetworkTypeWifi(networkType)) {
            mWifiIfaces = includeInStringArray(mWifiIfaces, iface);
            if (DEBUG) Slog.d(TAG, "Note wifi iface " + iface + ": " + mWifiIfaces);
        } else {
            mWifiIfaces = excludeFromStringArray(mWifiIfaces, iface);
            if (DEBUG) Slog.d(TAG, "Note non-wifi iface " + iface + ": " + mWifiIfaces);
        }
    }

    public void noteNetworkStatsEnabledLocked() {
        // During device boot, qtaguid isn't enabled until after the inital
        // loading of battery stats. Now that they're enabled, take our initial
        // snapshot for future delta calculation.
        updateMobileRadioStateLocked(mClocks.elapsedRealtime(), null);
        updateWifiStateLocked(null);
    }

    @Override public long getScreenOnTime(long elapsedRealtimeUs, int which) {
        return mScreenOnTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public int getScreenOnCount(int which) {
        return mScreenOnTimer.getCountLocked(which);
    }

    @Override public long getScreenBrightnessTime(int brightnessBin,
            long elapsedRealtimeUs, int which) {
        return mScreenBrightnessTimer[brightnessBin].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public long getInteractiveTime(long elapsedRealtimeUs, int which) {
        return mInteractiveTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public long getPowerSaveModeEnabledTime(long elapsedRealtimeUs, int which) {
        return mPowerSaveModeEnabledTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public int getPowerSaveModeEnabledCount(int which) {
        return mPowerSaveModeEnabledTimer.getCountLocked(which);
    }

    @Override public long getDeviceIdleModeTime(int mode, long elapsedRealtimeUs,
            int which) {
        switch (mode) {
            case DEVICE_IDLE_MODE_LIGHT:
                return mDeviceIdleModeLightTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
            case DEVICE_IDLE_MODE_DEEP:
                return mDeviceIdleModeFullTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }
        return 0;
    }

    @Override public int getDeviceIdleModeCount(int mode, int which) {
        switch (mode) {
            case DEVICE_IDLE_MODE_LIGHT:
                return mDeviceIdleModeLightTimer.getCountLocked(which);
            case DEVICE_IDLE_MODE_DEEP:
                return mDeviceIdleModeFullTimer.getCountLocked(which);
        }
        return 0;
    }

    @Override public long getLongestDeviceIdleModeTime(int mode) {
        switch (mode) {
            case DEVICE_IDLE_MODE_LIGHT:
                return mLongestLightIdleTime;
            case DEVICE_IDLE_MODE_DEEP:
                return mLongestFullIdleTime;
        }
        return 0;
    }

    @Override public long getDeviceIdlingTime(int mode, long elapsedRealtimeUs, int which) {
        switch (mode) {
            case DEVICE_IDLE_MODE_LIGHT:
                return mDeviceLightIdlingTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
            case DEVICE_IDLE_MODE_DEEP:
                return mDeviceIdlingTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }
        return 0;
    }

    @Override public int getDeviceIdlingCount(int mode, int which) {
        switch (mode) {
            case DEVICE_IDLE_MODE_LIGHT:
                return mDeviceLightIdlingTimer.getCountLocked(which);
            case DEVICE_IDLE_MODE_DEEP:
                return mDeviceIdlingTimer.getCountLocked(which);
        }
        return 0;
    }

    @Override public int getNumConnectivityChange(int which) {
        int val = mNumConnectivityChange;
        if (which == STATS_CURRENT) {
            val -= mLoadedNumConnectivityChange;
        } else if (which == STATS_SINCE_UNPLUGGED) {
            val -= mUnpluggedNumConnectivityChange;
        }
        return val;
    }

    @Override public long getPhoneOnTime(long elapsedRealtimeUs, int which) {
        return mPhoneOnTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public int getPhoneOnCount(int which) {
        return mPhoneOnTimer.getCountLocked(which);
    }

    @Override public long getPhoneSignalStrengthTime(int strengthBin,
            long elapsedRealtimeUs, int which) {
        return mPhoneSignalStrengthsTimer[strengthBin].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public long getPhoneSignalScanningTime(
            long elapsedRealtimeUs, int which) {
        return mPhoneSignalScanningTimer.getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public int getPhoneSignalStrengthCount(int strengthBin, int which) {
        return mPhoneSignalStrengthsTimer[strengthBin].getCountLocked(which);
    }

    @Override public long getPhoneDataConnectionTime(int dataType,
            long elapsedRealtimeUs, int which) {
        return mPhoneDataConnectionsTimer[dataType].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public int getPhoneDataConnectionCount(int dataType, int which) {
        return mPhoneDataConnectionsTimer[dataType].getCountLocked(which);
    }

    @Override public long getMobileRadioActiveTime(long elapsedRealtimeUs, int which) {
        return mMobileRadioActiveTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public int getMobileRadioActiveCount(int which) {
        return mMobileRadioActiveTimer.getCountLocked(which);
    }

    @Override public long getMobileRadioActiveAdjustedTime(int which) {
        return mMobileRadioActiveAdjustedTime.getCountLocked(which);
    }

    @Override public long getMobileRadioActiveUnknownTime(int which) {
        return mMobileRadioActiveUnknownTime.getCountLocked(which);
    }

    @Override public int getMobileRadioActiveUnknownCount(int which) {
        return (int)mMobileRadioActiveUnknownCount.getCountLocked(which);
    }

    @Override public long getWifiOnTime(long elapsedRealtimeUs, int which) {
        return mWifiOnTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public long getGlobalWifiRunningTime(long elapsedRealtimeUs, int which) {
        return mGlobalWifiRunningTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override public long getWifiStateTime(int wifiState,
            long elapsedRealtimeUs, int which) {
        return mWifiStateTimer[wifiState].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public int getWifiStateCount(int wifiState, int which) {
        return mWifiStateTimer[wifiState].getCountLocked(which);
    }

    @Override public long getWifiSupplStateTime(int state,
            long elapsedRealtimeUs, int which) {
        return mWifiSupplStateTimer[state].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public int getWifiSupplStateCount(int state, int which) {
        return mWifiSupplStateTimer[state].getCountLocked(which);
    }

    @Override public long getWifiSignalStrengthTime(int strengthBin,
            long elapsedRealtimeUs, int which) {
        return mWifiSignalStrengthsTimer[strengthBin].getTotalTimeLocked(
                elapsedRealtimeUs, which);
    }

    @Override public int getWifiSignalStrengthCount(int strengthBin, int which) {
        return mWifiSignalStrengthsTimer[strengthBin].getCountLocked(which);
    }

    @Override
    public ControllerActivityCounter getBluetoothControllerActivity() {
        return mBluetoothActivity;
    }

    @Override
    public ControllerActivityCounter getWifiControllerActivity() {
        return mWifiActivity;
    }

    @Override
    public ControllerActivityCounter getModemControllerActivity() {
        return mModemActivity;
    }

    @Override
    public boolean hasBluetoothActivityReporting() {
        return mHasBluetoothReporting;
    }

    @Override
    public boolean hasWifiActivityReporting() {
        return mHasWifiReporting;
    }

    @Override
    public boolean hasModemActivityReporting() {
        return mHasModemReporting;
    }

    @Override
    public long getFlashlightOnTime(long elapsedRealtimeUs, int which) {
        return mFlashlightOnTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override
    public long getFlashlightOnCount(int which) {
        return mFlashlightOnTimer.getCountLocked(which);
    }

    @Override
    public long getCameraOnTime(long elapsedRealtimeUs, int which) {
        return mCameraOnTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override
    public long getBluetoothScanTime(long elapsedRealtimeUs, int which) {
        return mBluetoothScanTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
    }

    @Override
    public long getNetworkActivityBytes(int type, int which) {
        if (type >= 0 && type < mNetworkByteActivityCounters.length) {
            return mNetworkByteActivityCounters[type].getCountLocked(which);
        } else {
            return 0;
        }
    }

    @Override
    public long getNetworkActivityPackets(int type, int which) {
        if (type >= 0 && type < mNetworkPacketActivityCounters.length) {
            return mNetworkPacketActivityCounters[type].getCountLocked(which);
        } else {
            return 0;
        }
    }

    @Override public long getStartClockTime() {
        final long currentTime = System.currentTimeMillis();
        if (ensureStartClockTime(currentTime)) {
            recordCurrentTimeChangeLocked(currentTime, mClocks.elapsedRealtime(),
                    mClocks.uptimeMillis());
        }
        return mStartClockTime;
    }

    @Override public String getStartPlatformVersion() {
        return mStartPlatformVersion;
    }

    @Override public String getEndPlatformVersion() {
        return mEndPlatformVersion;
    }

    @Override public int getParcelVersion() {
        return VERSION;
    }

    @Override public boolean getIsOnBattery() {
        return mOnBattery;
    }

    @Override public SparseArray<? extends BatteryStats.Uid> getUidStats() {
        return mUidStats;
    }

    private static void detachTimerIfNotNull(BatteryStatsImpl.Timer timer) {
        if (timer != null) {
            timer.detach();
        }
    }

    private static boolean resetTimerIfNotNull(BatteryStatsImpl.Timer timer,
            boolean detachIfReset) {
        if (timer != null) {
            return timer.reset(detachIfReset);
        }
        return true;
    }

    private static void detachLongCounterIfNotNull(LongSamplingCounter counter) {
        if (counter != null) {
            counter.detach();
        }
    }

    private static void resetLongCounterIfNotNull(LongSamplingCounter counter,
            boolean detachIfReset) {
        if (counter != null) {
            counter.reset(detachIfReset);
        }
    }

    /**
     * The statistics associated with a particular uid.
     */
    public static class Uid extends BatteryStats.Uid {
        /**
         * BatteryStatsImpl that we are associated with.
         */
        protected BatteryStatsImpl mBsi;

        final int mUid;

        boolean mWifiRunning;
        StopwatchTimer mWifiRunningTimer;

        boolean mFullWifiLockOut;
        StopwatchTimer mFullWifiLockTimer;

        boolean mWifiScanStarted;
        StopwatchTimer mWifiScanTimer;

        static final int NO_BATCHED_SCAN_STARTED = -1;
        int mWifiBatchedScanBinStarted = NO_BATCHED_SCAN_STARTED;
        StopwatchTimer[] mWifiBatchedScanTimer;

        boolean mWifiMulticastEnabled;
        StopwatchTimer mWifiMulticastTimer;

        StopwatchTimer mAudioTurnedOnTimer;
        StopwatchTimer mVideoTurnedOnTimer;
        StopwatchTimer mFlashlightTurnedOnTimer;
        StopwatchTimer mCameraTurnedOnTimer;
        StopwatchTimer mForegroundActivityTimer;
        StopwatchTimer mBluetoothScanTimer;

        int mProcessState = ActivityManager.PROCESS_STATE_NONEXISTENT;
        StopwatchTimer[] mProcessStateTimer;

        BatchTimer mVibratorOnTimer;

        Counter[] mUserActivityCounters;

        LongSamplingCounter[] mNetworkByteActivityCounters;
        LongSamplingCounter[] mNetworkPacketActivityCounters;
        LongSamplingCounter mMobileRadioActiveTime;
        LongSamplingCounter mMobileRadioActiveCount;

        /**
         * How many times this UID woke up the Application Processor due to a Mobile radio packet.
         */
        private LongSamplingCounter mMobileRadioApWakeupCount;

        /**
         * How many times this UID woke up the Application Processor due to a Wifi packet.
         */
        private LongSamplingCounter mWifiRadioApWakeupCount;

        /**
         * The amount of time this uid has kept the WiFi controller in idle, tx, and rx mode.
         * Can be null if the UID has had no such activity.
         */
        private ControllerActivityCounterImpl mWifiControllerActivity;

        /**
         * The amount of time this uid has kept the Bluetooth controller in idle, tx, and rx mode.
         * Can be null if the UID has had no such activity.
         */
        private ControllerActivityCounterImpl mBluetoothControllerActivity;

        /**
         * The amount of time this uid has kept the Modem controller in idle, tx, and rx mode.
         * Can be null if the UID has had no such activity.
         */
        private ControllerActivityCounterImpl mModemControllerActivity;

        /**
         * The CPU times we had at the last history details update.
         */
        long mLastStepUserTime;
        long mLastStepSystemTime;
        long mCurStepUserTime;
        long mCurStepSystemTime;

        LongSamplingCounter mUserCpuTime;
        LongSamplingCounter mSystemCpuTime;
        LongSamplingCounter mCpuPower;
        LongSamplingCounter[][] mCpuClusterSpeed;

        /**
         * The statistics we have collected for this uid's wake locks.
         */
        final OverflowArrayMap<Wakelock> mWakelockStats;

        /**
         * The statistics we have collected for this uid's syncs.
         */
        final OverflowArrayMap<StopwatchTimer> mSyncStats;

        /**
         * The statistics we have collected for this uid's jobs.
         */
        final OverflowArrayMap<StopwatchTimer> mJobStats;

        /**
         * The statistics we have collected for this uid's sensor activations.
         */
        final SparseArray<Sensor> mSensorStats = new SparseArray<>();

        /**
         * The statistics we have collected for this uid's processes.
         */
        final ArrayMap<String, Proc> mProcessStats = new ArrayMap<>();

        /**
         * The statistics we have collected for this uid's processes.
         */
        final ArrayMap<String, Pkg> mPackageStats = new ArrayMap<>();

        /**
         * The transient wake stats we have collected for this uid's pids.
         */
        final SparseArray<Pid> mPids = new SparseArray<>();

        public Uid(BatteryStatsImpl bsi, int uid) {
            mBsi = bsi;
            mUid = uid;

            mUserCpuTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            mSystemCpuTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            mCpuPower = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);

            mWakelockStats = mBsi.new OverflowArrayMap<Wakelock>(uid) {
                @Override public Wakelock instantiateObject() {
                    return new Wakelock(mBsi, Uid.this);
                }
            };
            mSyncStats = mBsi.new OverflowArrayMap<StopwatchTimer>(uid) {
                @Override public StopwatchTimer instantiateObject() {
                    return new StopwatchTimer(mBsi.mClocks, Uid.this, SYNC, null,
                            mBsi.mOnBatteryTimeBase);
                }
            };
            mJobStats = mBsi.new OverflowArrayMap<StopwatchTimer>(uid) {
                @Override public StopwatchTimer instantiateObject() {
                    return new StopwatchTimer(mBsi.mClocks, Uid.this, JOB, null,
                            mBsi.mOnBatteryTimeBase);
                }
            };

            mWifiRunningTimer = new StopwatchTimer(mBsi.mClocks, this, WIFI_RUNNING,
                    mBsi.mWifiRunningTimers, mBsi.mOnBatteryTimeBase);
            mFullWifiLockTimer = new StopwatchTimer(mBsi.mClocks, this, FULL_WIFI_LOCK,
                    mBsi.mFullWifiLockTimers, mBsi.mOnBatteryTimeBase);
            mWifiScanTimer = new StopwatchTimer(mBsi.mClocks, this, WIFI_SCAN,
                    mBsi.mWifiScanTimers, mBsi.mOnBatteryTimeBase);
            mWifiBatchedScanTimer = new StopwatchTimer[NUM_WIFI_BATCHED_SCAN_BINS];
            mWifiMulticastTimer = new StopwatchTimer(mBsi.mClocks, this, WIFI_MULTICAST_ENABLED,
                    mBsi.mWifiMulticastTimers, mBsi.mOnBatteryTimeBase);
            mProcessStateTimer = new StopwatchTimer[NUM_PROCESS_STATE];
        }

        @Override
        public ArrayMap<String, ? extends BatteryStats.Uid.Wakelock> getWakelockStats() {
            return mWakelockStats.getMap();
        }

        @Override
        public ArrayMap<String, ? extends BatteryStats.Timer> getSyncStats() {
            return mSyncStats.getMap();
        }

        @Override
        public ArrayMap<String, ? extends BatteryStats.Timer> getJobStats() {
            return mJobStats.getMap();
        }

        @Override
        public SparseArray<? extends BatteryStats.Uid.Sensor> getSensorStats() {
            return mSensorStats;
        }

        @Override
        public ArrayMap<String, ? extends BatteryStats.Uid.Proc> getProcessStats() {
            return mProcessStats;
        }

        @Override
        public ArrayMap<String, ? extends BatteryStats.Uid.Pkg> getPackageStats() {
            return mPackageStats;
        }

        @Override
        public int getUid() {
            return mUid;
        }

        @Override
        public void noteWifiRunningLocked(long elapsedRealtimeMs) {
            if (!mWifiRunning) {
                mWifiRunning = true;
                if (mWifiRunningTimer == null) {
                    mWifiRunningTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, WIFI_RUNNING,
                            mBsi.mWifiRunningTimers, mBsi.mOnBatteryTimeBase);
                }
                mWifiRunningTimer.startRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteWifiStoppedLocked(long elapsedRealtimeMs) {
            if (mWifiRunning) {
                mWifiRunning = false;
                mWifiRunningTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteFullWifiLockAcquiredLocked(long elapsedRealtimeMs) {
            if (!mFullWifiLockOut) {
                mFullWifiLockOut = true;
                if (mFullWifiLockTimer == null) {
                    mFullWifiLockTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, FULL_WIFI_LOCK,
                            mBsi.mFullWifiLockTimers, mBsi.mOnBatteryTimeBase);
                }
                mFullWifiLockTimer.startRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteFullWifiLockReleasedLocked(long elapsedRealtimeMs) {
            if (mFullWifiLockOut) {
                mFullWifiLockOut = false;
                mFullWifiLockTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteWifiScanStartedLocked(long elapsedRealtimeMs) {
            if (!mWifiScanStarted) {
                mWifiScanStarted = true;
                if (mWifiScanTimer == null) {
                    mWifiScanTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, WIFI_SCAN,
                            mBsi.mWifiScanTimers, mBsi.mOnBatteryTimeBase);
                }
                mWifiScanTimer.startRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteWifiScanStoppedLocked(long elapsedRealtimeMs) {
            if (mWifiScanStarted) {
                mWifiScanStarted = false;
                mWifiScanTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteWifiBatchedScanStartedLocked(int csph, long elapsedRealtimeMs) {
            int bin = 0;
            while (csph > 8 && bin < NUM_WIFI_BATCHED_SCAN_BINS-1) {
                csph = csph >> 3;
                bin++;
            }

            if (mWifiBatchedScanBinStarted == bin) return;

            if (mWifiBatchedScanBinStarted != NO_BATCHED_SCAN_STARTED) {
                mWifiBatchedScanTimer[mWifiBatchedScanBinStarted].
                        stopRunningLocked(elapsedRealtimeMs);
            }
            mWifiBatchedScanBinStarted = bin;
            if (mWifiBatchedScanTimer[bin] == null) {
                makeWifiBatchedScanBin(bin, null);
            }
            mWifiBatchedScanTimer[bin].startRunningLocked(elapsedRealtimeMs);
        }

        @Override
        public void noteWifiBatchedScanStoppedLocked(long elapsedRealtimeMs) {
            if (mWifiBatchedScanBinStarted != NO_BATCHED_SCAN_STARTED) {
                mWifiBatchedScanTimer[mWifiBatchedScanBinStarted].
                        stopRunningLocked(elapsedRealtimeMs);
                mWifiBatchedScanBinStarted = NO_BATCHED_SCAN_STARTED;
            }
        }

        @Override
        public void noteWifiMulticastEnabledLocked(long elapsedRealtimeMs) {
            if (!mWifiMulticastEnabled) {
                mWifiMulticastEnabled = true;
                if (mWifiMulticastTimer == null) {
                    mWifiMulticastTimer = new StopwatchTimer(mBsi.mClocks, Uid.this,
                            WIFI_MULTICAST_ENABLED, mBsi.mWifiMulticastTimers, mBsi.mOnBatteryTimeBase);
                }
                mWifiMulticastTimer.startRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteWifiMulticastDisabledLocked(long elapsedRealtimeMs) {
            if (mWifiMulticastEnabled) {
                mWifiMulticastEnabled = false;
                mWifiMulticastTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public ControllerActivityCounter getWifiControllerActivity() {
            return mWifiControllerActivity;
        }

        @Override
        public ControllerActivityCounter getBluetoothControllerActivity() {
            return mBluetoothControllerActivity;
        }

        @Override
        public ControllerActivityCounter getModemControllerActivity() {
            return mModemControllerActivity;
        }

        public ControllerActivityCounterImpl getOrCreateWifiControllerActivityLocked() {
            if (mWifiControllerActivity == null) {
                mWifiControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        NUM_BT_TX_LEVELS);
            }
            return mWifiControllerActivity;
        }

        public ControllerActivityCounterImpl getOrCreateBluetoothControllerActivityLocked() {
            if (mBluetoothControllerActivity == null) {
                mBluetoothControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        NUM_BT_TX_LEVELS);
            }
            return mBluetoothControllerActivity;
        }

        public ControllerActivityCounterImpl getOrCreateModemControllerActivityLocked() {
            if (mModemControllerActivity == null) {
                mModemControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        ModemActivityInfo.TX_POWER_LEVELS);
            }
            return mModemControllerActivity;
        }

        public StopwatchTimer createAudioTurnedOnTimerLocked() {
            if (mAudioTurnedOnTimer == null) {
                mAudioTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, AUDIO_TURNED_ON,
                        mBsi.mAudioTurnedOnTimers, mBsi.mOnBatteryTimeBase);
            }
            return mAudioTurnedOnTimer;
        }

        public void noteAudioTurnedOnLocked(long elapsedRealtimeMs) {
            createAudioTurnedOnTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        public void noteAudioTurnedOffLocked(long elapsedRealtimeMs) {
            if (mAudioTurnedOnTimer != null) {
                mAudioTurnedOnTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteResetAudioLocked(long elapsedRealtimeMs) {
            if (mAudioTurnedOnTimer != null) {
                mAudioTurnedOnTimer.stopAllRunningLocked(elapsedRealtimeMs);
            }
        }

        public StopwatchTimer createVideoTurnedOnTimerLocked() {
            if (mVideoTurnedOnTimer == null) {
                mVideoTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, VIDEO_TURNED_ON,
                        mBsi.mVideoTurnedOnTimers, mBsi.mOnBatteryTimeBase);
            }
            return mVideoTurnedOnTimer;
        }

        public void noteVideoTurnedOnLocked(long elapsedRealtimeMs) {
            createVideoTurnedOnTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        public void noteVideoTurnedOffLocked(long elapsedRealtimeMs) {
            if (mVideoTurnedOnTimer != null) {
                mVideoTurnedOnTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteResetVideoLocked(long elapsedRealtimeMs) {
            if (mVideoTurnedOnTimer != null) {
                mVideoTurnedOnTimer.stopAllRunningLocked(elapsedRealtimeMs);
            }
        }

        public StopwatchTimer createFlashlightTurnedOnTimerLocked() {
            if (mFlashlightTurnedOnTimer == null) {
                mFlashlightTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this,
                        FLASHLIGHT_TURNED_ON, mBsi.mFlashlightTurnedOnTimers, mBsi.mOnBatteryTimeBase);
            }
            return mFlashlightTurnedOnTimer;
        }

        public void noteFlashlightTurnedOnLocked(long elapsedRealtimeMs) {
            createFlashlightTurnedOnTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        public void noteFlashlightTurnedOffLocked(long elapsedRealtimeMs) {
            if (mFlashlightTurnedOnTimer != null) {
                mFlashlightTurnedOnTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteResetFlashlightLocked(long elapsedRealtimeMs) {
            if (mFlashlightTurnedOnTimer != null) {
                mFlashlightTurnedOnTimer.stopAllRunningLocked(elapsedRealtimeMs);
            }
        }

        public StopwatchTimer createCameraTurnedOnTimerLocked() {
            if (mCameraTurnedOnTimer == null) {
                mCameraTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, CAMERA_TURNED_ON,
                        mBsi.mCameraTurnedOnTimers, mBsi.mOnBatteryTimeBase);
            }
            return mCameraTurnedOnTimer;
        }

        public void noteCameraTurnedOnLocked(long elapsedRealtimeMs) {
            createCameraTurnedOnTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        public void noteCameraTurnedOffLocked(long elapsedRealtimeMs) {
            if (mCameraTurnedOnTimer != null) {
                mCameraTurnedOnTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteResetCameraLocked(long elapsedRealtimeMs) {
            if (mCameraTurnedOnTimer != null) {
                mCameraTurnedOnTimer.stopAllRunningLocked(elapsedRealtimeMs);
            }
        }

        public StopwatchTimer createForegroundActivityTimerLocked() {
            if (mForegroundActivityTimer == null) {
                mForegroundActivityTimer = new StopwatchTimer(mBsi.mClocks, Uid.this,
                        FOREGROUND_ACTIVITY, null, mBsi.mOnBatteryTimeBase);
            }
            return mForegroundActivityTimer;
        }

        public StopwatchTimer createBluetoothScanTimerLocked() {
            if (mBluetoothScanTimer == null) {
                mBluetoothScanTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, BLUETOOTH_SCAN_ON,
                        mBsi.mBluetoothScanOnTimers, mBsi.mOnBatteryTimeBase);
            }
            return mBluetoothScanTimer;
        }

        public void noteBluetoothScanStartedLocked(long elapsedRealtimeMs) {
            createBluetoothScanTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        public void noteBluetoothScanStoppedLocked(long elapsedRealtimeMs) {
            if (mBluetoothScanTimer != null) {
                mBluetoothScanTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteResetBluetoothScanLocked(long elapsedRealtimeMs) {
            if (mBluetoothScanTimer != null) {
                mBluetoothScanTimer.stopAllRunningLocked(elapsedRealtimeMs);
            }
        }

        @Override
        public void noteActivityResumedLocked(long elapsedRealtimeMs) {
            // We always start, since we want multiple foreground PIDs to nest
            createForegroundActivityTimerLocked().startRunningLocked(elapsedRealtimeMs);
        }

        @Override
        public void noteActivityPausedLocked(long elapsedRealtimeMs) {
            if (mForegroundActivityTimer != null) {
                mForegroundActivityTimer.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public BatchTimer createVibratorOnTimerLocked() {
            if (mVibratorOnTimer == null) {
                mVibratorOnTimer = new BatchTimer(mBsi.mClocks, Uid.this, VIBRATOR_ON,
                        mBsi.mOnBatteryTimeBase);
            }
            return mVibratorOnTimer;
        }

        public void noteVibratorOnLocked(long durationMillis) {
            createVibratorOnTimerLocked().addDuration(mBsi, durationMillis);
        }

        public void noteVibratorOffLocked() {
            if (mVibratorOnTimer != null) {
                mVibratorOnTimer.abortLastDuration(mBsi);
            }
        }

        @Override
        public long getWifiRunningTime(long elapsedRealtimeUs, int which) {
            if (mWifiRunningTimer == null) {
                return 0;
            }
            return mWifiRunningTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public long getFullWifiLockTime(long elapsedRealtimeUs, int which) {
            if (mFullWifiLockTimer == null) {
                return 0;
            }
            return mFullWifiLockTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public long getWifiScanTime(long elapsedRealtimeUs, int which) {
            if (mWifiScanTimer == null) {
                return 0;
            }
            return mWifiScanTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public int getWifiScanCount(int which) {
            if (mWifiScanTimer == null) {
                return 0;
            }
            return mWifiScanTimer.getCountLocked(which);
        }

        @Override
        public long getWifiBatchedScanTime(int csphBin, long elapsedRealtimeUs, int which) {
            if (csphBin < 0 || csphBin >= NUM_WIFI_BATCHED_SCAN_BINS) return 0;
            if (mWifiBatchedScanTimer[csphBin] == null) {
                return 0;
            }
            return mWifiBatchedScanTimer[csphBin].getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public int getWifiBatchedScanCount(int csphBin, int which) {
            if (csphBin < 0 || csphBin >= NUM_WIFI_BATCHED_SCAN_BINS) return 0;
            if (mWifiBatchedScanTimer[csphBin] == null) {
                return 0;
            }
            return mWifiBatchedScanTimer[csphBin].getCountLocked(which);
        }

        @Override
        public long getWifiMulticastTime(long elapsedRealtimeUs, int which) {
            if (mWifiMulticastTimer == null) {
                return 0;
            }
            return mWifiMulticastTimer.getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public Timer getAudioTurnedOnTimer() {
            return mAudioTurnedOnTimer;
        }

        @Override
        public Timer getVideoTurnedOnTimer() {
            return mVideoTurnedOnTimer;
        }

        @Override
        public Timer getFlashlightTurnedOnTimer() {
            return mFlashlightTurnedOnTimer;
        }

        @Override
        public Timer getCameraTurnedOnTimer() {
            return mCameraTurnedOnTimer;
        }

        @Override
        public Timer getForegroundActivityTimer() {
            return mForegroundActivityTimer;
        }

        @Override
        public Timer getBluetoothScanTimer() {
            return mBluetoothScanTimer;
        }

        void makeProcessState(int i, Parcel in) {
            if (i < 0 || i >= NUM_PROCESS_STATE) return;

            if (in == null) {
                mProcessStateTimer[i] = new StopwatchTimer(mBsi.mClocks, this, PROCESS_STATE, null,
                        mBsi.mOnBatteryTimeBase);
            } else {
                mProcessStateTimer[i] = new StopwatchTimer(mBsi.mClocks, this, PROCESS_STATE, null,
                        mBsi.mOnBatteryTimeBase, in);
            }
        }

        @Override
        public long getProcessStateTime(int state, long elapsedRealtimeUs, int which) {
            if (state < 0 || state >= NUM_PROCESS_STATE) return 0;
            if (mProcessStateTimer[state] == null) {
                return 0;
            }
            return mProcessStateTimer[state].getTotalTimeLocked(elapsedRealtimeUs, which);
        }

        @Override
        public Timer getProcessStateTimer(int state) {
            if (state < 0 || state >= NUM_PROCESS_STATE) return null;
            return mProcessStateTimer[state];
        }

        @Override
        public Timer getVibratorOnTimer() {
            return mVibratorOnTimer;
        }

        @Override
        public void noteUserActivityLocked(int type) {
            if (mUserActivityCounters == null) {
                initUserActivityLocked();
            }
            if (type >= 0 && type < NUM_USER_ACTIVITY_TYPES) {
                mUserActivityCounters[type].stepAtomic();
            } else {
                Slog.w(TAG, "Unknown user activity type " + type + " was specified.",
                        new Throwable());
            }
        }

        @Override
        public boolean hasUserActivity() {
            return mUserActivityCounters != null;
        }

        @Override
        public int getUserActivityCount(int type, int which) {
            if (mUserActivityCounters == null) {
                return 0;
            }
            return mUserActivityCounters[type].getCountLocked(which);
        }

        void makeWifiBatchedScanBin(int i, Parcel in) {
            if (i < 0 || i >= NUM_WIFI_BATCHED_SCAN_BINS) return;

            ArrayList<StopwatchTimer> collected = mBsi.mWifiBatchedScanTimers.get(i);
            if (collected == null) {
                collected = new ArrayList<StopwatchTimer>();
                mBsi.mWifiBatchedScanTimers.put(i, collected);
            }
            if (in == null) {
                mWifiBatchedScanTimer[i] = new StopwatchTimer(mBsi.mClocks, this, WIFI_BATCHED_SCAN,
                        collected, mBsi.mOnBatteryTimeBase);
            } else {
                mWifiBatchedScanTimer[i] = new StopwatchTimer(mBsi.mClocks, this, WIFI_BATCHED_SCAN,
                        collected, mBsi.mOnBatteryTimeBase, in);
            }
        }


        void initUserActivityLocked() {
            mUserActivityCounters = new Counter[NUM_USER_ACTIVITY_TYPES];
            for (int i=0; i<NUM_USER_ACTIVITY_TYPES; i++) {
                mUserActivityCounters[i] = new Counter(mBsi.mOnBatteryTimeBase);
            }
        }

        void noteNetworkActivityLocked(int type, long deltaBytes, long deltaPackets) {
            if (mNetworkByteActivityCounters == null) {
                initNetworkActivityLocked();
            }
            if (type >= 0 && type < NUM_NETWORK_ACTIVITY_TYPES) {
                mNetworkByteActivityCounters[type].addCountLocked(deltaBytes);
                mNetworkPacketActivityCounters[type].addCountLocked(deltaPackets);
            } else {
                Slog.w(TAG, "Unknown network activity type " + type + " was specified.",
                        new Throwable());
            }
        }

        void noteMobileRadioActiveTimeLocked(long batteryUptime) {
            if (mNetworkByteActivityCounters == null) {
                initNetworkActivityLocked();
            }
            mMobileRadioActiveTime.addCountLocked(batteryUptime);
            mMobileRadioActiveCount.addCountLocked(1);
        }

        @Override
        public boolean hasNetworkActivity() {
            return mNetworkByteActivityCounters != null;
        }

        @Override
        public long getNetworkActivityBytes(int type, int which) {
            if (mNetworkByteActivityCounters != null && type >= 0
                    && type < mNetworkByteActivityCounters.length) {
                return mNetworkByteActivityCounters[type].getCountLocked(which);
            } else {
                return 0;
            }
        }

        @Override
        public long getNetworkActivityPackets(int type, int which) {
            if (mNetworkPacketActivityCounters != null && type >= 0
                    && type < mNetworkPacketActivityCounters.length) {
                return mNetworkPacketActivityCounters[type].getCountLocked(which);
            } else {
                return 0;
            }
        }

        @Override
        public long getMobileRadioActiveTime(int which) {
            return mMobileRadioActiveTime != null
                    ? mMobileRadioActiveTime.getCountLocked(which) : 0;
        }

        @Override
        public int getMobileRadioActiveCount(int which) {
            return mMobileRadioActiveCount != null
                    ? (int)mMobileRadioActiveCount.getCountLocked(which) : 0;
        }

        @Override
        public long getUserCpuTimeUs(int which) {
            return mUserCpuTime.getCountLocked(which);
        }

        @Override
        public long getSystemCpuTimeUs(int which) {
            return mSystemCpuTime.getCountLocked(which);
        }

        @Override
        public long getCpuPowerMaUs(int which) {
            return mCpuPower.getCountLocked(which);
        }

        @Override
        public long getTimeAtCpuSpeed(int cluster, int step, int which) {
            if (mCpuClusterSpeed != null) {
                if (cluster >= 0 && cluster < mCpuClusterSpeed.length) {
                    final LongSamplingCounter[] cpuSpeeds = mCpuClusterSpeed[cluster];
                    if (cpuSpeeds != null) {
                        if (step >= 0 && step < cpuSpeeds.length) {
                            final LongSamplingCounter c = cpuSpeeds[step];
                            if (c != null) {
                                return c.getCountLocked(which);
                            }
                        }
                    }
                }
            }
            return 0;
        }

        public void noteMobileRadioApWakeupLocked() {
            if (mMobileRadioApWakeupCount == null) {
                mMobileRadioApWakeupCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            }
            mMobileRadioApWakeupCount.addCountLocked(1);
        }

        @Override
        public long getMobileRadioApWakeupCount(int which) {
            if (mMobileRadioApWakeupCount != null) {
                return mMobileRadioApWakeupCount.getCountLocked(which);
            }
            return 0;
        }

        public void noteWifiRadioApWakeupLocked() {
            if (mWifiRadioApWakeupCount == null) {
                mWifiRadioApWakeupCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            }
            mWifiRadioApWakeupCount.addCountLocked(1);
        }

        @Override
        public long getWifiRadioApWakeupCount(int which) {
            if (mWifiRadioApWakeupCount != null) {
                return mWifiRadioApWakeupCount.getCountLocked(which);
            }
            return 0;
        }

        void initNetworkActivityLocked() {
            mNetworkByteActivityCounters = new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];
            mNetworkPacketActivityCounters = new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];
            for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                mNetworkByteActivityCounters[i] = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
                mNetworkPacketActivityCounters[i] = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            }
            mMobileRadioActiveTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
            mMobileRadioActiveCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase);
        }

        /**
         * Clear all stats for this uid.  Returns true if the uid is completely
         * inactive so can be dropped.
         */
        boolean reset() {
            boolean active = false;

            if (mWifiRunningTimer != null) {
                active |= !mWifiRunningTimer.reset(false);
                active |= mWifiRunning;
            }
            if (mFullWifiLockTimer != null) {
                active |= !mFullWifiLockTimer.reset(false);
                active |= mFullWifiLockOut;
            }
            if (mWifiScanTimer != null) {
                active |= !mWifiScanTimer.reset(false);
                active |= mWifiScanStarted;
            }
            if (mWifiBatchedScanTimer != null) {
                for (int i = 0; i < NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                    if (mWifiBatchedScanTimer[i] != null) {
                        active |= !mWifiBatchedScanTimer[i].reset(false);
                    }
                }
                active |= (mWifiBatchedScanBinStarted != NO_BATCHED_SCAN_STARTED);
            }
            if (mWifiMulticastTimer != null) {
                active |= !mWifiMulticastTimer.reset(false);
                active |= mWifiMulticastEnabled;
            }

            active |= !resetTimerIfNotNull(mAudioTurnedOnTimer, false);
            active |= !resetTimerIfNotNull(mVideoTurnedOnTimer, false);
            active |= !resetTimerIfNotNull(mFlashlightTurnedOnTimer, false);
            active |= !resetTimerIfNotNull(mCameraTurnedOnTimer, false);
            active |= !resetTimerIfNotNull(mForegroundActivityTimer, false);
            active |= !resetTimerIfNotNull(mBluetoothScanTimer, false);

            if (mProcessStateTimer != null) {
                for (int i = 0; i < NUM_PROCESS_STATE; i++) {
                    if (mProcessStateTimer[i] != null) {
                        active |= !mProcessStateTimer[i].reset(false);
                    }
                }
                active |= (mProcessState != ActivityManager.PROCESS_STATE_NONEXISTENT);
            }
            if (mVibratorOnTimer != null) {
                if (mVibratorOnTimer.reset(false)) {
                    mVibratorOnTimer.detach();
                    mVibratorOnTimer = null;
                } else {
                    active = true;
                }
            }

            if (mUserActivityCounters != null) {
                for (int i=0; i<NUM_USER_ACTIVITY_TYPES; i++) {
                    mUserActivityCounters[i].reset(false);
                }
            }

            if (mNetworkByteActivityCounters != null) {
                for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                    mNetworkByteActivityCounters[i].reset(false);
                    mNetworkPacketActivityCounters[i].reset(false);
                }
                mMobileRadioActiveTime.reset(false);
                mMobileRadioActiveCount.reset(false);
            }

            if (mWifiControllerActivity != null) {
                mWifiControllerActivity.reset(false);
            }

            if (mBluetoothControllerActivity != null) {
                mBluetoothControllerActivity.reset(false);
            }

            if (mModemControllerActivity != null) {
                mModemControllerActivity.reset(false);
            }

            mUserCpuTime.reset(false);
            mSystemCpuTime.reset(false);
            mCpuPower.reset(false);

            if (mCpuClusterSpeed != null) {
                for (LongSamplingCounter[] speeds : mCpuClusterSpeed) {
                    if (speeds != null) {
                        for (LongSamplingCounter speed : speeds) {
                            if (speed != null) {
                                speed.reset(false);
                            }
                        }
                    }
                }
            }

            resetLongCounterIfNotNull(mMobileRadioApWakeupCount, false);
            resetLongCounterIfNotNull(mWifiRadioApWakeupCount, false);

            final ArrayMap<String, Wakelock> wakeStats = mWakelockStats.getMap();
            for (int iw=wakeStats.size()-1; iw>=0; iw--) {
                Wakelock wl = wakeStats.valueAt(iw);
                if (wl.reset()) {
                    wakeStats.removeAt(iw);
                } else {
                    active = true;
                }
            }
            mWakelockStats.cleanup();
            final ArrayMap<String, StopwatchTimer> syncStats = mSyncStats.getMap();
            for (int is=syncStats.size()-1; is>=0; is--) {
                StopwatchTimer timer = syncStats.valueAt(is);
                if (timer.reset(false)) {
                    syncStats.removeAt(is);
                    timer.detach();
                } else {
                    active = true;
                }
            }
            mSyncStats.cleanup();
            final ArrayMap<String, StopwatchTimer> jobStats = mJobStats.getMap();
            for (int ij=jobStats.size()-1; ij>=0; ij--) {
                StopwatchTimer timer = jobStats.valueAt(ij);
                if (timer.reset(false)) {
                    jobStats.removeAt(ij);
                    timer.detach();
                } else {
                    active = true;
                }
            }
            mJobStats.cleanup();
            for (int ise=mSensorStats.size()-1; ise>=0; ise--) {
                Sensor s = mSensorStats.valueAt(ise);
                if (s.reset()) {
                    mSensorStats.removeAt(ise);
                } else {
                    active = true;
                }
            }
            for (int ip=mProcessStats.size()-1; ip>=0; ip--) {
                Proc proc = mProcessStats.valueAt(ip);
                proc.detach();
            }
            mProcessStats.clear();
            if (mPids.size() > 0) {
                for (int i=mPids.size()-1; i>=0; i--) {
                    Pid pid = mPids.valueAt(i);
                    if (pid.mWakeNesting > 0) {
                        active = true;
                    } else {
                        mPids.removeAt(i);
                    }
                }
            }
            if (mPackageStats.size() > 0) {
                Iterator<Map.Entry<String, Pkg>> it = mPackageStats.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Pkg> pkgEntry = it.next();
                    Pkg p = pkgEntry.getValue();
                    p.detach();
                    if (p.mServiceStats.size() > 0) {
                        Iterator<Map.Entry<String, Pkg.Serv>> it2
                                = p.mServiceStats.entrySet().iterator();
                        while (it2.hasNext()) {
                            Map.Entry<String, Pkg.Serv> servEntry = it2.next();
                            servEntry.getValue().detach();
                        }
                    }
                }
                mPackageStats.clear();
            }

            mLastStepUserTime = mLastStepSystemTime = 0;
            mCurStepUserTime = mCurStepSystemTime = 0;

            if (!active) {
                if (mWifiRunningTimer != null) {
                    mWifiRunningTimer.detach();
                }
                if (mFullWifiLockTimer != null) {
                    mFullWifiLockTimer.detach();
                }
                if (mWifiScanTimer != null) {
                    mWifiScanTimer.detach();
                }
                for (int i = 0; i < NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                    if (mWifiBatchedScanTimer[i] != null) {
                        mWifiBatchedScanTimer[i].detach();
                    }
                }
                if (mWifiMulticastTimer != null) {
                    mWifiMulticastTimer.detach();
                }
                if (mAudioTurnedOnTimer != null) {
                    mAudioTurnedOnTimer.detach();
                    mAudioTurnedOnTimer = null;
                }
                if (mVideoTurnedOnTimer != null) {
                    mVideoTurnedOnTimer.detach();
                    mVideoTurnedOnTimer = null;
                }
                if (mFlashlightTurnedOnTimer != null) {
                    mFlashlightTurnedOnTimer.detach();
                    mFlashlightTurnedOnTimer = null;
                }
                if (mCameraTurnedOnTimer != null) {
                    mCameraTurnedOnTimer.detach();
                    mCameraTurnedOnTimer = null;
                }
                if (mForegroundActivityTimer != null) {
                    mForegroundActivityTimer.detach();
                    mForegroundActivityTimer = null;
                }
                if (mBluetoothScanTimer != null) {
                    mBluetoothScanTimer.detach();
                    mBluetoothScanTimer = null;
                }
                if (mUserActivityCounters != null) {
                    for (int i=0; i<NUM_USER_ACTIVITY_TYPES; i++) {
                        mUserActivityCounters[i].detach();
                    }
                }
                if (mNetworkByteActivityCounters != null) {
                    for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                        mNetworkByteActivityCounters[i].detach();
                        mNetworkPacketActivityCounters[i].detach();
                    }
                }

                if (mWifiControllerActivity != null) {
                    mWifiControllerActivity.detach();
                }

                if (mBluetoothControllerActivity != null) {
                    mBluetoothControllerActivity.detach();
                }

                if (mModemControllerActivity != null) {
                    mModemControllerActivity.detach();
                }

                mPids.clear();

                mUserCpuTime.detach();
                mSystemCpuTime.detach();
                mCpuPower.detach();

                if (mCpuClusterSpeed != null) {
                    for (LongSamplingCounter[] cpuSpeeds : mCpuClusterSpeed) {
                        if (cpuSpeeds != null) {
                            for (LongSamplingCounter c : cpuSpeeds) {
                                if (c != null) {
                                    c.detach();
                                }
                            }
                        }
                    }
                }

                detachLongCounterIfNotNull(mMobileRadioApWakeupCount);
                detachLongCounterIfNotNull(mWifiRadioApWakeupCount);
            }

            return !active;
        }

        void writeToParcelLocked(Parcel out, long elapsedRealtimeUs) {
            final ArrayMap<String, Wakelock> wakeStats = mWakelockStats.getMap();
            int NW = wakeStats.size();
            out.writeInt(NW);
            for (int iw=0; iw<NW; iw++) {
                out.writeString(wakeStats.keyAt(iw));
                Uid.Wakelock wakelock = wakeStats.valueAt(iw);
                wakelock.writeToParcelLocked(out, elapsedRealtimeUs);
            }

            final ArrayMap<String, StopwatchTimer> syncStats = mSyncStats.getMap();
            int NS = syncStats.size();
            out.writeInt(NS);
            for (int is=0; is<NS; is++) {
                out.writeString(syncStats.keyAt(is));
                StopwatchTimer timer = syncStats.valueAt(is);
                Timer.writeTimerToParcel(out, timer, elapsedRealtimeUs);
            }

            final ArrayMap<String, StopwatchTimer> jobStats = mJobStats.getMap();
            int NJ = jobStats.size();
            out.writeInt(NJ);
            for (int ij=0; ij<NJ; ij++) {
                out.writeString(jobStats.keyAt(ij));
                StopwatchTimer timer = jobStats.valueAt(ij);
                Timer.writeTimerToParcel(out, timer, elapsedRealtimeUs);
            }

            int NSE = mSensorStats.size();
            out.writeInt(NSE);
            for (int ise=0; ise<NSE; ise++) {
                out.writeInt(mSensorStats.keyAt(ise));
                Uid.Sensor sensor = mSensorStats.valueAt(ise);
                sensor.writeToParcelLocked(out, elapsedRealtimeUs);
            }

            int NP = mProcessStats.size();
            out.writeInt(NP);
            for (int ip=0; ip<NP; ip++) {
                out.writeString(mProcessStats.keyAt(ip));
                Uid.Proc proc = mProcessStats.valueAt(ip);
                proc.writeToParcelLocked(out);
            }

            out.writeInt(mPackageStats.size());
            for (Map.Entry<String, Uid.Pkg> pkgEntry : mPackageStats.entrySet()) {
                out.writeString(pkgEntry.getKey());
                Uid.Pkg pkg = pkgEntry.getValue();
                pkg.writeToParcelLocked(out);
            }

            if (mWifiRunningTimer != null) {
                out.writeInt(1);
                mWifiRunningTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mFullWifiLockTimer != null) {
                out.writeInt(1);
                mFullWifiLockTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mWifiScanTimer != null) {
                out.writeInt(1);
                mWifiScanTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            for (int i = 0; i < NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                if (mWifiBatchedScanTimer[i] != null) {
                    out.writeInt(1);
                    mWifiBatchedScanTimer[i].writeToParcel(out, elapsedRealtimeUs);
                } else {
                    out.writeInt(0);
                }
            }
            if (mWifiMulticastTimer != null) {
                out.writeInt(1);
                mWifiMulticastTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }

            if (mAudioTurnedOnTimer != null) {
                out.writeInt(1);
                mAudioTurnedOnTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mVideoTurnedOnTimer != null) {
                out.writeInt(1);
                mVideoTurnedOnTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mFlashlightTurnedOnTimer != null) {
                out.writeInt(1);
                mFlashlightTurnedOnTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mCameraTurnedOnTimer != null) {
                out.writeInt(1);
                mCameraTurnedOnTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mForegroundActivityTimer != null) {
                out.writeInt(1);
                mForegroundActivityTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mBluetoothScanTimer != null) {
                out.writeInt(1);
                mBluetoothScanTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            for (int i = 0; i < NUM_PROCESS_STATE; i++) {
                if (mProcessStateTimer[i] != null) {
                    out.writeInt(1);
                    mProcessStateTimer[i].writeToParcel(out, elapsedRealtimeUs);
                } else {
                    out.writeInt(0);
                }
            }
            if (mVibratorOnTimer != null) {
                out.writeInt(1);
                mVibratorOnTimer.writeToParcel(out, elapsedRealtimeUs);
            } else {
                out.writeInt(0);
            }
            if (mUserActivityCounters != null) {
                out.writeInt(1);
                for (int i=0; i<NUM_USER_ACTIVITY_TYPES; i++) {
                    mUserActivityCounters[i].writeToParcel(out);
                }
            } else {
                out.writeInt(0);
            }
            if (mNetworkByteActivityCounters != null) {
                out.writeInt(1);
                for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                    mNetworkByteActivityCounters[i].writeToParcel(out);
                    mNetworkPacketActivityCounters[i].writeToParcel(out);
                }
                mMobileRadioActiveTime.writeToParcel(out);
                mMobileRadioActiveCount.writeToParcel(out);
            } else {
                out.writeInt(0);
            }

            if (mWifiControllerActivity != null) {
                out.writeInt(1);
                mWifiControllerActivity.writeToParcel(out, 0);
            } else {
                out.writeInt(0);
            }

            if (mBluetoothControllerActivity != null) {
                out.writeInt(1);
                mBluetoothControllerActivity.writeToParcel(out, 0);
            } else {
                out.writeInt(0);
            }

            if (mModemControllerActivity != null) {
                out.writeInt(1);
                mModemControllerActivity.writeToParcel(out, 0);
            } else {
                out.writeInt(0);
            }

            mUserCpuTime.writeToParcel(out);
            mSystemCpuTime.writeToParcel(out);
            mCpuPower.writeToParcel(out);

            if (mCpuClusterSpeed != null) {
                out.writeInt(1);
                out.writeInt(mCpuClusterSpeed.length);
                for (LongSamplingCounter[] cpuSpeeds : mCpuClusterSpeed) {
                    if (cpuSpeeds != null) {
                        out.writeInt(1);
                        out.writeInt(cpuSpeeds.length);
                        for (LongSamplingCounter c : cpuSpeeds) {
                            if (c != null) {
                                out.writeInt(1);
                                c.writeToParcel(out);
                            } else {
                                out.writeInt(0);
                            }
                        }
                    } else {
                        out.writeInt(0);
                    }
                }
            } else {
                out.writeInt(0);
            }

            if (mMobileRadioApWakeupCount != null) {
                out.writeInt(1);
                mMobileRadioApWakeupCount.writeToParcel(out);
            } else {
                out.writeInt(0);
            }

            if (mWifiRadioApWakeupCount != null) {
                out.writeInt(1);
                mWifiRadioApWakeupCount.writeToParcel(out);
            } else {
                out.writeInt(0);
            }
        }

        void readFromParcelLocked(TimeBase timeBase, TimeBase screenOffTimeBase, Parcel in) {
            int numWakelocks = in.readInt();
            mWakelockStats.clear();
            for (int j = 0; j < numWakelocks; j++) {
                String wakelockName = in.readString();
                Uid.Wakelock wakelock = new Wakelock(mBsi, this);
                wakelock.readFromParcelLocked(timeBase, screenOffTimeBase, in);
                mWakelockStats.add(wakelockName, wakelock);
            }

            int numSyncs = in.readInt();
            mSyncStats.clear();
            for (int j = 0; j < numSyncs; j++) {
                String syncName = in.readString();
                if (in.readInt() != 0) {
                    mSyncStats.add(syncName,
                            new StopwatchTimer(mBsi.mClocks, Uid.this, SYNC, null, timeBase, in));
                }
            }

            int numJobs = in.readInt();
            mJobStats.clear();
            for (int j = 0; j < numJobs; j++) {
                String jobName = in.readString();
                if (in.readInt() != 0) {
                    mJobStats.add(jobName, new StopwatchTimer(mBsi.mClocks, Uid.this, JOB, null,
                                timeBase, in));
                }
            }

            int numSensors = in.readInt();
            mSensorStats.clear();
            for (int k = 0; k < numSensors; k++) {
                int sensorNumber = in.readInt();
                Uid.Sensor sensor = new Sensor(mBsi, this, sensorNumber);
                sensor.readFromParcelLocked(mBsi.mOnBatteryTimeBase, in);
                mSensorStats.put(sensorNumber, sensor);
            }

            int numProcs = in.readInt();
            mProcessStats.clear();
            for (int k = 0; k < numProcs; k++) {
                String processName = in.readString();
                Uid.Proc proc = new Proc(mBsi, processName);
                proc.readFromParcelLocked(in);
                mProcessStats.put(processName, proc);
            }

            int numPkgs = in.readInt();
            mPackageStats.clear();
            for (int l = 0; l < numPkgs; l++) {
                String packageName = in.readString();
                Uid.Pkg pkg = new Pkg(mBsi);
                pkg.readFromParcelLocked(in);
                mPackageStats.put(packageName, pkg);
            }

            mWifiRunning = false;
            if (in.readInt() != 0) {
                mWifiRunningTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, WIFI_RUNNING,
                        mBsi.mWifiRunningTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mWifiRunningTimer = null;
            }
            mFullWifiLockOut = false;
            if (in.readInt() != 0) {
                mFullWifiLockTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, FULL_WIFI_LOCK,
                        mBsi.mFullWifiLockTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mFullWifiLockTimer = null;
            }
            mWifiScanStarted = false;
            if (in.readInt() != 0) {
                mWifiScanTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, WIFI_SCAN,
                        mBsi.mWifiScanTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mWifiScanTimer = null;
            }
            mWifiBatchedScanBinStarted = NO_BATCHED_SCAN_STARTED;
            for (int i = 0; i < NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                if (in.readInt() != 0) {
                    makeWifiBatchedScanBin(i, in);
                } else {
                    mWifiBatchedScanTimer[i] = null;
                }
            }
            mWifiMulticastEnabled = false;
            if (in.readInt() != 0) {
                mWifiMulticastTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, WIFI_MULTICAST_ENABLED,
                        mBsi.mWifiMulticastTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mWifiMulticastTimer = null;
            }
            if (in.readInt() != 0) {
                mAudioTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, AUDIO_TURNED_ON,
                        mBsi.mAudioTurnedOnTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mAudioTurnedOnTimer = null;
            }
            if (in.readInt() != 0) {
                mVideoTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, VIDEO_TURNED_ON,
                        mBsi.mVideoTurnedOnTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mVideoTurnedOnTimer = null;
            }
            if (in.readInt() != 0) {
                mFlashlightTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this,
                        FLASHLIGHT_TURNED_ON, mBsi.mFlashlightTurnedOnTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mFlashlightTurnedOnTimer = null;
            }
            if (in.readInt() != 0) {
                mCameraTurnedOnTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, CAMERA_TURNED_ON,
                        mBsi.mCameraTurnedOnTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mCameraTurnedOnTimer = null;
            }
            if (in.readInt() != 0) {
                mForegroundActivityTimer = new StopwatchTimer(mBsi.mClocks, Uid.this,
                        FOREGROUND_ACTIVITY, null, mBsi.mOnBatteryTimeBase, in);
            } else {
                mForegroundActivityTimer = null;
            }
            if (in.readInt() != 0) {
                mBluetoothScanTimer = new StopwatchTimer(mBsi.mClocks, Uid.this, BLUETOOTH_SCAN_ON,
                        mBsi.mBluetoothScanOnTimers, mBsi.mOnBatteryTimeBase, in);
            } else {
                mBluetoothScanTimer = null;
            }
            mProcessState = ActivityManager.PROCESS_STATE_NONEXISTENT;
            for (int i = 0; i < NUM_PROCESS_STATE; i++) {
                if (in.readInt() != 0) {
                    makeProcessState(i, in);
                } else {
                    mProcessStateTimer[i] = null;
                }
            }
            if (in.readInt() != 0) {
                mVibratorOnTimer = new BatchTimer(mBsi.mClocks, Uid.this, VIBRATOR_ON,
                        mBsi.mOnBatteryTimeBase, in);
            } else {
                mVibratorOnTimer = null;
            }
            if (in.readInt() != 0) {
                mUserActivityCounters = new Counter[NUM_USER_ACTIVITY_TYPES];
                for (int i=0; i<NUM_USER_ACTIVITY_TYPES; i++) {
                    mUserActivityCounters[i] = new Counter(mBsi.mOnBatteryTimeBase, in);
                }
            } else {
                mUserActivityCounters = null;
            }
            if (in.readInt() != 0) {
                mNetworkByteActivityCounters = new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];
                mNetworkPacketActivityCounters
                        = new LongSamplingCounter[NUM_NETWORK_ACTIVITY_TYPES];
                for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                    mNetworkByteActivityCounters[i]
                            = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
                    mNetworkPacketActivityCounters[i]
                            = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
                }
                mMobileRadioActiveTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
                mMobileRadioActiveCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
            } else {
                mNetworkByteActivityCounters = null;
                mNetworkPacketActivityCounters = null;
            }

            if (in.readInt() != 0) {
                mWifiControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        NUM_WIFI_TX_LEVELS, in);
            } else {
                mWifiControllerActivity = null;
            }

            if (in.readInt() != 0) {
                mBluetoothControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        NUM_BT_TX_LEVELS, in);
            } else {
                mBluetoothControllerActivity = null;
            }

            if (in.readInt() != 0) {
                mModemControllerActivity = new ControllerActivityCounterImpl(mBsi.mOnBatteryTimeBase,
                        ModemActivityInfo.TX_POWER_LEVELS, in);
            } else {
                mModemControllerActivity = null;
            }

            mUserCpuTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
            mSystemCpuTime = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
            mCpuPower = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);

            if (in.readInt() != 0) {
                int numCpuClusters = in.readInt();
                if (mBsi.mPowerProfile != null && mBsi.mPowerProfile.getNumCpuClusters() != numCpuClusters) {
                    throw new ParcelFormatException("Incompatible number of cpu clusters");
                }

                mCpuClusterSpeed = new LongSamplingCounter[numCpuClusters][];
                for (int cluster = 0; cluster < numCpuClusters; cluster++) {
                    if (in.readInt() != 0) {
                        int numSpeeds = in.readInt();
                        if (mBsi.mPowerProfile != null &&
                                mBsi.mPowerProfile.getNumSpeedStepsInCpuCluster(cluster) != numSpeeds) {
                            throw new ParcelFormatException("Incompatible number of cpu speeds");
                        }

                        final LongSamplingCounter[] cpuSpeeds = new LongSamplingCounter[numSpeeds];
                        mCpuClusterSpeed[cluster] = cpuSpeeds;
                        for (int speed = 0; speed < numSpeeds; speed++) {
                            if (in.readInt() != 0) {
                                cpuSpeeds[speed] = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
                            }
                        }
                    } else {
                        mCpuClusterSpeed[cluster] = null;
                    }
                }
            } else {
                mCpuClusterSpeed = null;
            }

            if (in.readInt() != 0) {
                mMobileRadioApWakeupCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
            } else {
                mMobileRadioApWakeupCount = null;
            }

            if (in.readInt() != 0) {
                mWifiRadioApWakeupCount = new LongSamplingCounter(mBsi.mOnBatteryTimeBase, in);
            } else {
                mWifiRadioApWakeupCount = null;
            }
        }

        /**
         * The statistics associated with a particular wake lock.
         */
        public static class Wakelock extends BatteryStats.Uid.Wakelock {
            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected BatteryStatsImpl mBsi;

            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected Uid mUid;

            /**
             * How long (in ms) this uid has been keeping the device partially awake.
             */
            DurationTimer mTimerPartial;

            /**
             * How long (in ms) this uid has been keeping the device fully awake.
             */
            StopwatchTimer mTimerFull;

            /**
             * How long (in ms) this uid has had a window keeping the device awake.
             */
            StopwatchTimer mTimerWindow;

            /**
             * How long (in ms) this uid has had a draw wake lock.
             */
            StopwatchTimer mTimerDraw;

            public Wakelock(BatteryStatsImpl bsi, Uid uid) {
                mBsi = bsi;
                mUid = uid;
            }

            /**
             * Reads a possibly null Timer from a Parcel.  The timer is associated with the
             * proper timer pool from the given BatteryStatsImpl object.
             *
             * @param in the Parcel to be read from.
             * return a new Timer, or null.
             */
            private StopwatchTimer readStopwatchTimerFromParcel(int type,
                    ArrayList<StopwatchTimer> pool, TimeBase timeBase, Parcel in) {
                if (in.readInt() == 0) {
                    return null;
                }

                return new StopwatchTimer(mBsi.mClocks, mUid, type, pool, timeBase, in);
            }

            /**
             * Reads a possibly null Timer from a Parcel.  The timer is associated with the
             * proper timer pool from the given BatteryStatsImpl object.
             *
             * @param in the Parcel to be read from.
             * return a new Timer, or null.
             */
            private DurationTimer readDurationTimerFromParcel(int type,
                    ArrayList<StopwatchTimer> pool, TimeBase timeBase, Parcel in) {
                if (in.readInt() == 0) {
                    return null;
                }

                return new DurationTimer(mBsi.mClocks, mUid, type, pool, timeBase, in);
            }

            boolean reset() {
                boolean wlactive = false;
                if (mTimerFull != null) {
                    wlactive |= !mTimerFull.reset(false);
                }
                if (mTimerPartial != null) {
                    wlactive |= !mTimerPartial.reset(false);
                }
                if (mTimerWindow != null) {
                    wlactive |= !mTimerWindow.reset(false);
                }
                if (mTimerDraw != null) {
                    wlactive |= !mTimerDraw.reset(false);
                }
                if (!wlactive) {
                    if (mTimerFull != null) {
                        mTimerFull.detach();
                        mTimerFull = null;
                    }
                    if (mTimerPartial != null) {
                        mTimerPartial.detach();
                        mTimerPartial = null;
                    }
                    if (mTimerWindow != null) {
                        mTimerWindow.detach();
                        mTimerWindow = null;
                    }
                    if (mTimerDraw != null) {
                        mTimerDraw.detach();
                        mTimerDraw = null;
                    }
                }
                return !wlactive;
            }

            void readFromParcelLocked(TimeBase timeBase, TimeBase screenOffTimeBase, Parcel in) {
                mTimerPartial = readDurationTimerFromParcel(WAKE_TYPE_PARTIAL,
                        mBsi.mPartialTimers, screenOffTimeBase, in);
                mTimerFull = readStopwatchTimerFromParcel(WAKE_TYPE_FULL,
                        mBsi.mFullTimers, timeBase, in);
                mTimerWindow = readStopwatchTimerFromParcel(WAKE_TYPE_WINDOW,
                        mBsi.mWindowTimers, timeBase, in);
                mTimerDraw = readStopwatchTimerFromParcel(WAKE_TYPE_DRAW,
                        mBsi.mDrawTimers, timeBase, in);
            }

            void writeToParcelLocked(Parcel out, long elapsedRealtimeUs) {
                Timer.writeTimerToParcel(out, mTimerPartial, elapsedRealtimeUs);
                Timer.writeTimerToParcel(out, mTimerFull, elapsedRealtimeUs);
                Timer.writeTimerToParcel(out, mTimerWindow, elapsedRealtimeUs);
                Timer.writeTimerToParcel(out, mTimerDraw, elapsedRealtimeUs);
            }

            @Override
            public Timer getWakeTime(int type) {
                switch (type) {
                case WAKE_TYPE_FULL: return mTimerFull;
                case WAKE_TYPE_PARTIAL: return mTimerPartial;
                case WAKE_TYPE_WINDOW: return mTimerWindow;
                case WAKE_TYPE_DRAW: return mTimerDraw;
                default: throw new IllegalArgumentException("type = " + type);
                }
            }

            public StopwatchTimer getStopwatchTimer(int type) {
                switch (type) {
                    case WAKE_TYPE_PARTIAL: {
                        DurationTimer t = mTimerPartial;
                        if (t == null) {
                            t = new DurationTimer(mBsi.mClocks, mUid, WAKE_TYPE_PARTIAL,
                                    mBsi.mPartialTimers, mBsi.mOnBatteryScreenOffTimeBase);
                            mTimerPartial = t;
                        }
                        return t;
                    }
                    case WAKE_TYPE_FULL: {
                        StopwatchTimer t = mTimerFull;
                        if (t == null) {
                            t = new StopwatchTimer(mBsi.mClocks, mUid, WAKE_TYPE_FULL,
                                    mBsi.mFullTimers, mBsi.mOnBatteryTimeBase);
                            mTimerFull = t;
                        }
                        return t;
                    }
                    case WAKE_TYPE_WINDOW: {
                        StopwatchTimer t = mTimerWindow;
                        if (t == null) {
                            t = new StopwatchTimer(mBsi.mClocks, mUid, WAKE_TYPE_WINDOW,
                                    mBsi.mWindowTimers, mBsi.mOnBatteryTimeBase);
                            mTimerWindow = t;
                        }
                        return t;
                    }
                    case WAKE_TYPE_DRAW: {
                        StopwatchTimer t = mTimerDraw;
                        if (t == null) {
                            t = new StopwatchTimer(mBsi.mClocks, mUid, WAKE_TYPE_DRAW,
                                    mBsi.mDrawTimers, mBsi.mOnBatteryTimeBase);
                            mTimerDraw = t;
                        }
                        return t;
                    }
                    default:
                        throw new IllegalArgumentException("type=" + type);
                }
            }
        }

        public static class Sensor extends BatteryStats.Uid.Sensor {
            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected BatteryStatsImpl mBsi;

            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected Uid mUid;

            final int mHandle;
            StopwatchTimer mTimer;

            public Sensor(BatteryStatsImpl bsi, Uid uid, int handle) {
                mBsi = bsi;
                mUid = uid;
                mHandle = handle;
            }

            private StopwatchTimer readTimerFromParcel(TimeBase timeBase, Parcel in) {
                if (in.readInt() == 0) {
                    return null;
                }

                ArrayList<StopwatchTimer> pool = mBsi.mSensorTimers.get(mHandle);
                if (pool == null) {
                    pool = new ArrayList<StopwatchTimer>();
                    mBsi.mSensorTimers.put(mHandle, pool);
                }
                return new StopwatchTimer(mBsi.mClocks, mUid, 0, pool, timeBase, in);
            }

            boolean reset() {
                if (mTimer.reset(true)) {
                    mTimer = null;
                    return true;
                }
                return false;
            }

            void readFromParcelLocked(TimeBase timeBase, Parcel in) {
                mTimer = readTimerFromParcel(timeBase, in);
            }

            void writeToParcelLocked(Parcel out, long elapsedRealtimeUs) {
                Timer.writeTimerToParcel(out, mTimer, elapsedRealtimeUs);
            }

            @Override
            public Timer getSensorTime() {
                return mTimer;
            }

            @Override
            public int getHandle() {
                return mHandle;
            }
        }

        /**
         * The statistics associated with a particular process.
         */
        public static class Proc extends BatteryStats.Uid.Proc implements TimeBaseObs {
            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected BatteryStatsImpl mBsi;

            /**
             * The name of this process.
             */
            final String mName;

            /**
             * Remains true until removed from the stats.
             */
            boolean mActive = true;

            /**
             * Total time (in ms) spent executing in user code.
             */
            long mUserTime;

            /**
             * Total time (in ms) spent executing in kernel code.
             */
            long mSystemTime;

            /**
             * Amount of time (in ms) the process was running in the foreground.
             */
            long mForegroundTime;

            /**
             * Number of times the process has been started.
             */
            int mStarts;

            /**
             * Number of times the process has crashed.
             */
            int mNumCrashes;

            /**
             * Number of times the process has had an ANR.
             */
            int mNumAnrs;

            /**
             * The amount of user time loaded from a previous save.
             */
            long mLoadedUserTime;

            /**
             * The amount of system time loaded from a previous save.
             */
            long mLoadedSystemTime;

            /**
             * The amount of foreground time loaded from a previous save.
             */
            long mLoadedForegroundTime;

            /**
             * The number of times the process has started from a previous save.
             */
            int mLoadedStarts;

            /**
             * Number of times the process has crashed from a previous save.
             */
            int mLoadedNumCrashes;

            /**
             * Number of times the process has had an ANR from a previous save.
             */
            int mLoadedNumAnrs;

            /**
             * The amount of user time when last unplugged.
             */
            long mUnpluggedUserTime;

            /**
             * The amount of system time when last unplugged.
             */
            long mUnpluggedSystemTime;

            /**
             * The amount of foreground time since unplugged.
             */
            long mUnpluggedForegroundTime;

            /**
             * The number of times the process has started before unplugged.
             */
            int mUnpluggedStarts;

            /**
             * Number of times the process has crashed before unplugged.
             */
            int mUnpluggedNumCrashes;

            /**
             * Number of times the process has had an ANR before unplugged.
             */
            int mUnpluggedNumAnrs;

            ArrayList<ExcessivePower> mExcessivePower;

            public Proc(BatteryStatsImpl bsi, String name) {
                mBsi = bsi;
                mName = name;
                mBsi.mOnBatteryTimeBase.add(this);
            }

            public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
                mUnpluggedUserTime = mUserTime;
                mUnpluggedSystemTime = mSystemTime;
                mUnpluggedForegroundTime = mForegroundTime;
                mUnpluggedStarts = mStarts;
                mUnpluggedNumCrashes = mNumCrashes;
                mUnpluggedNumAnrs = mNumAnrs;
            }

            public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            }

            void detach() {
                mActive = false;
                mBsi.mOnBatteryTimeBase.remove(this);
            }

            public int countExcessivePowers() {
                return mExcessivePower != null ? mExcessivePower.size() : 0;
            }

            public ExcessivePower getExcessivePower(int i) {
                if (mExcessivePower != null) {
                    return mExcessivePower.get(i);
                }
                return null;
            }

            public void addExcessiveWake(long overTime, long usedTime) {
                if (mExcessivePower == null) {
                    mExcessivePower = new ArrayList<ExcessivePower>();
                }
                ExcessivePower ew = new ExcessivePower();
                ew.type = ExcessivePower.TYPE_WAKE;
                ew.overTime = overTime;
                ew.usedTime = usedTime;
                mExcessivePower.add(ew);
            }

            public void addExcessiveCpu(long overTime, long usedTime) {
                if (mExcessivePower == null) {
                    mExcessivePower = new ArrayList<ExcessivePower>();
                }
                ExcessivePower ew = new ExcessivePower();
                ew.type = ExcessivePower.TYPE_CPU;
                ew.overTime = overTime;
                ew.usedTime = usedTime;
                mExcessivePower.add(ew);
            }

            void writeExcessivePowerToParcelLocked(Parcel out) {
                if (mExcessivePower == null) {
                    out.writeInt(0);
                    return;
                }

                final int N = mExcessivePower.size();
                out.writeInt(N);
                for (int i=0; i<N; i++) {
                    ExcessivePower ew = mExcessivePower.get(i);
                    out.writeInt(ew.type);
                    out.writeLong(ew.overTime);
                    out.writeLong(ew.usedTime);
                }
            }

            void readExcessivePowerFromParcelLocked(Parcel in) {
                final int N = in.readInt();
                if (N == 0) {
                    mExcessivePower = null;
                    return;
                }

                if (N > 10000) {
                    throw new ParcelFormatException(
                            "File corrupt: too many excessive power entries " + N);
                }

                mExcessivePower = new ArrayList<>();
                for (int i=0; i<N; i++) {
                    ExcessivePower ew = new ExcessivePower();
                    ew.type = in.readInt();
                    ew.overTime = in.readLong();
                    ew.usedTime = in.readLong();
                    mExcessivePower.add(ew);
                }
            }

            void writeToParcelLocked(Parcel out) {
                out.writeLong(mUserTime);
                out.writeLong(mSystemTime);
                out.writeLong(mForegroundTime);
                out.writeInt(mStarts);
                out.writeInt(mNumCrashes);
                out.writeInt(mNumAnrs);
                out.writeLong(mLoadedUserTime);
                out.writeLong(mLoadedSystemTime);
                out.writeLong(mLoadedForegroundTime);
                out.writeInt(mLoadedStarts);
                out.writeInt(mLoadedNumCrashes);
                out.writeInt(mLoadedNumAnrs);
                out.writeLong(mUnpluggedUserTime);
                out.writeLong(mUnpluggedSystemTime);
                out.writeLong(mUnpluggedForegroundTime);
                out.writeInt(mUnpluggedStarts);
                out.writeInt(mUnpluggedNumCrashes);
                out.writeInt(mUnpluggedNumAnrs);
                writeExcessivePowerToParcelLocked(out);
            }

            void readFromParcelLocked(Parcel in) {
                mUserTime = in.readLong();
                mSystemTime = in.readLong();
                mForegroundTime = in.readLong();
                mStarts = in.readInt();
                mNumCrashes = in.readInt();
                mNumAnrs = in.readInt();
                mLoadedUserTime = in.readLong();
                mLoadedSystemTime = in.readLong();
                mLoadedForegroundTime = in.readLong();
                mLoadedStarts = in.readInt();
                mLoadedNumCrashes = in.readInt();
                mLoadedNumAnrs = in.readInt();
                mUnpluggedUserTime = in.readLong();
                mUnpluggedSystemTime = in.readLong();
                mUnpluggedForegroundTime = in.readLong();
                mUnpluggedStarts = in.readInt();
                mUnpluggedNumCrashes = in.readInt();
                mUnpluggedNumAnrs = in.readInt();
                readExcessivePowerFromParcelLocked(in);
            }

            public void addCpuTimeLocked(int utime, int stime) {
                mUserTime += utime;
                mSystemTime += stime;
            }

            public void addForegroundTimeLocked(long ttime) {
                mForegroundTime += ttime;
            }

            public void incStartsLocked() {
                mStarts++;
            }

            public void incNumCrashesLocked() {
                mNumCrashes++;
            }

            public void incNumAnrsLocked() {
                mNumAnrs++;
            }

            @Override
            public boolean isActive() {
                return mActive;
            }

            @Override
            public long getUserTime(int which) {
                long val = mUserTime;
                if (which == STATS_CURRENT) {
                    val -= mLoadedUserTime;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedUserTime;
                }
                return val;
            }

            @Override
            public long getSystemTime(int which) {
                long val = mSystemTime;
                if (which == STATS_CURRENT) {
                    val -= mLoadedSystemTime;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedSystemTime;
                }
                return val;
            }

            @Override
            public long getForegroundTime(int which) {
                long val = mForegroundTime;
                if (which == STATS_CURRENT) {
                    val -= mLoadedForegroundTime;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedForegroundTime;
                }
                return val;
            }

            @Override
            public int getStarts(int which) {
                int val = mStarts;
                if (which == STATS_CURRENT) {
                    val -= mLoadedStarts;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedStarts;
                }
                return val;
            }

            @Override
            public int getNumCrashes(int which) {
                int val = mNumCrashes;
                if (which == STATS_CURRENT) {
                    val -= mLoadedNumCrashes;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedNumCrashes;
                }
                return val;
            }

            @Override
            public int getNumAnrs(int which) {
                int val = mNumAnrs;
                if (which == STATS_CURRENT) {
                    val -= mLoadedNumAnrs;
                } else if (which == STATS_SINCE_UNPLUGGED) {
                    val -= mUnpluggedNumAnrs;
                }
                return val;
            }
        }

        /**
         * The statistics associated with a particular package.
         */
        public static class Pkg extends BatteryStats.Uid.Pkg implements TimeBaseObs {
            /**
             * BatteryStatsImpl that we are associated with.
             */
            protected BatteryStatsImpl mBsi;

            /**
             * Number of times wakeup alarms have occurred for this app.
             */
            ArrayMap<String, Counter> mWakeupAlarms = new ArrayMap<>();

            /**
             * The statics we have collected for this package's services.
             */
            final ArrayMap<String, Serv> mServiceStats = new ArrayMap<>();

            public Pkg(BatteryStatsImpl bsi) {
                mBsi = bsi;
                mBsi.mOnBatteryScreenOffTimeBase.add(this);
            }

            public void onTimeStarted(long elapsedRealtime, long baseUptime, long baseRealtime) {
            }

            public void onTimeStopped(long elapsedRealtime, long baseUptime, long baseRealtime) {
            }

            void detach() {
                mBsi.mOnBatteryScreenOffTimeBase.remove(this);
            }

            void readFromParcelLocked(Parcel in) {
                int numWA = in.readInt();
                mWakeupAlarms.clear();
                for (int i=0; i<numWA; i++) {
                    String tag = in.readString();
                    mWakeupAlarms.put(tag, new Counter(mBsi.mOnBatteryTimeBase, in));
                }

                int numServs = in.readInt();
                mServiceStats.clear();
                for (int m = 0; m < numServs; m++) {
                    String serviceName = in.readString();
                    Uid.Pkg.Serv serv = new Serv(mBsi);
                    mServiceStats.put(serviceName, serv);

                    serv.readFromParcelLocked(in);
                }
            }

            void writeToParcelLocked(Parcel out) {
                int numWA = mWakeupAlarms.size();
                out.writeInt(numWA);
                for (int i=0; i<numWA; i++) {
                    out.writeString(mWakeupAlarms.keyAt(i));
                    mWakeupAlarms.valueAt(i).writeToParcel(out);
                }

                final int NS = mServiceStats.size();
                out.writeInt(NS);
                for (int i=0; i<NS; i++) {
                    out.writeString(mServiceStats.keyAt(i));
                    Uid.Pkg.Serv serv = mServiceStats.valueAt(i);
                    serv.writeToParcelLocked(out);
                }
            }

            @Override
            public ArrayMap<String, ? extends BatteryStats.Counter> getWakeupAlarmStats() {
                return mWakeupAlarms;
            }

            public void noteWakeupAlarmLocked(String tag) {
                Counter c = mWakeupAlarms.get(tag);
                if (c == null) {
                    c = new Counter(mBsi.mOnBatteryTimeBase);
                    mWakeupAlarms.put(tag, c);
                }
                c.stepAtomic();
            }

            @Override
            public ArrayMap<String, ? extends BatteryStats.Uid.Pkg.Serv> getServiceStats() {
                return mServiceStats;
            }

            /**
             * The statistics associated with a particular service.
             */
            public static class Serv extends BatteryStats.Uid.Pkg.Serv implements TimeBaseObs {
                /**
                 * BatteryStatsImpl that we are associated with.
                 */
                protected BatteryStatsImpl mBsi;

                /**
                 * The android package in which this service resides.
                 */
                protected Pkg mPkg;

                /**
                 * Total time (ms in battery uptime) the service has been left started.
                 */
                protected long mStartTime;

                /**
                 * If service has been started and not yet stopped, this is
                 * when it was started.
                 */
                protected long mRunningSince;

                /**
                 * True if we are currently running.
                 */
                protected boolean mRunning;

                /**
                 * Total number of times startService() has been called.
                 */
                protected int mStarts;

                /**
                 * Total time (ms in battery uptime) the service has been left launched.
                 */
                protected long mLaunchedTime;

                /**
                 * If service has been launched and not yet exited, this is
                 * when it was launched (ms in battery uptime).
                 */
                protected long mLaunchedSince;

                /**
                 * True if we are currently launched.
                 */
                protected boolean mLaunched;

                /**
                 * Total number times the service has been launched.
                 */
                protected int mLaunches;

                /**
                 * The amount of time spent started loaded from a previous save
                 * (ms in battery uptime).
                 */
                protected long mLoadedStartTime;

                /**
                 * The number of starts loaded from a previous save.
                 */
                protected int mLoadedStarts;

                /**
                 * The number of launches loaded from a previous save.
                 */
                protected int mLoadedLaunches;

                /**
                 * The amount of time spent started as of the last run (ms
                 * in battery uptime).
                 */
                protected long mLastStartTime;

                /**
                 * The number of starts as of the last run.
                 */
                protected int mLastStarts;

                /**
                 * The number of launches as of the last run.
                 */
                protected int mLastLaunches;

                /**
                 * The amount of time spent started when last unplugged (ms
                 * in battery uptime).
                 */
                protected long mUnpluggedStartTime;

                /**
                 * The number of starts when last unplugged.
                 */
                protected int mUnpluggedStarts;

                /**
                 * The number of launches when last unplugged.
                 */
                protected int mUnpluggedLaunches;

                /**
                 * Construct a Serv. Also adds it to the on-battery time base as a listener.
                 */
                public Serv(BatteryStatsImpl bsi) {
                    mBsi = bsi;
                    mBsi.mOnBatteryTimeBase.add(this);
                }

                public void onTimeStarted(long elapsedRealtime, long baseUptime,
                        long baseRealtime) {
                    mUnpluggedStartTime = getStartTimeToNowLocked(baseUptime);
                    mUnpluggedStarts = mStarts;
                    mUnpluggedLaunches = mLaunches;
                }

                public void onTimeStopped(long elapsedRealtime, long baseUptime,
                        long baseRealtime) {
                }

                /**
                 * Remove this Serv as a listener from the time base.
                 */
                public void detach() {
                    mBsi.mOnBatteryTimeBase.remove(this);
                }

                public void readFromParcelLocked(Parcel in) {
                    mStartTime = in.readLong();
                    mRunningSince = in.readLong();
                    mRunning = in.readInt() != 0;
                    mStarts = in.readInt();
                    mLaunchedTime = in.readLong();
                    mLaunchedSince = in.readLong();
                    mLaunched = in.readInt() != 0;
                    mLaunches = in.readInt();
                    mLoadedStartTime = in.readLong();
                    mLoadedStarts = in.readInt();
                    mLoadedLaunches = in.readInt();
                    mLastStartTime = 0;
                    mLastStarts = 0;
                    mLastLaunches = 0;
                    mUnpluggedStartTime = in.readLong();
                    mUnpluggedStarts = in.readInt();
                    mUnpluggedLaunches = in.readInt();
                }

                public void writeToParcelLocked(Parcel out) {
                    out.writeLong(mStartTime);
                    out.writeLong(mRunningSince);
                    out.writeInt(mRunning ? 1 : 0);
                    out.writeInt(mStarts);
                    out.writeLong(mLaunchedTime);
                    out.writeLong(mLaunchedSince);
                    out.writeInt(mLaunched ? 1 : 0);
                    out.writeInt(mLaunches);
                    out.writeLong(mLoadedStartTime);
                    out.writeInt(mLoadedStarts);
                    out.writeInt(mLoadedLaunches);
                    out.writeLong(mUnpluggedStartTime);
                    out.writeInt(mUnpluggedStarts);
                    out.writeInt(mUnpluggedLaunches);
                }

                public long getLaunchTimeToNowLocked(long batteryUptime) {
                    if (!mLaunched) return mLaunchedTime;
                    return mLaunchedTime + batteryUptime - mLaunchedSince;
                }

                public long getStartTimeToNowLocked(long batteryUptime) {
                    if (!mRunning) return mStartTime;
                    return mStartTime + batteryUptime - mRunningSince;
                }

                public void startLaunchedLocked() {
                    if (!mLaunched) {
                        mLaunches++;
                        mLaunchedSince = mBsi.getBatteryUptimeLocked();
                        mLaunched = true;
                    }
                }

                public void stopLaunchedLocked() {
                    if (mLaunched) {
                        long time = mBsi.getBatteryUptimeLocked() - mLaunchedSince;
                        if (time > 0) {
                            mLaunchedTime += time;
                        } else {
                            mLaunches--;
                        }
                        mLaunched = false;
                    }
                }

                public void startRunningLocked() {
                    if (!mRunning) {
                        mStarts++;
                        mRunningSince = mBsi.getBatteryUptimeLocked();
                        mRunning = true;
                    }
                }

                public void stopRunningLocked() {
                    if (mRunning) {
                        long time = mBsi.getBatteryUptimeLocked() - mRunningSince;
                        if (time > 0) {
                            mStartTime += time;
                        } else {
                            mStarts--;
                        }
                        mRunning = false;
                    }
                }

                public BatteryStatsImpl getBatteryStats() {
                    return mBsi;
                }

                @Override
                public int getLaunches(int which) {
                    int val = mLaunches;
                    if (which == STATS_CURRENT) {
                        val -= mLoadedLaunches;
                    } else if (which == STATS_SINCE_UNPLUGGED) {
                        val -= mUnpluggedLaunches;
                    }
                    return val;
                }

                @Override
                public long getStartTime(long now, int which) {
                    long val = getStartTimeToNowLocked(now);
                    if (which == STATS_CURRENT) {
                        val -= mLoadedStartTime;
                    } else if (which == STATS_SINCE_UNPLUGGED) {
                        val -= mUnpluggedStartTime;
                    }
                    return val;
                }

                @Override
                public int getStarts(int which) {
                    int val = mStarts;
                    if (which == STATS_CURRENT) {
                        val -= mLoadedStarts;
                    } else if (which == STATS_SINCE_UNPLUGGED) {
                        val -= mUnpluggedStarts;
                    }

                    return val;
                }
            }

            final Serv newServiceStatsLocked() {
                return new Serv(mBsi);
            }
        }

        /**
         * Retrieve the statistics object for a particular process, creating
         * if needed.
         */
        public Proc getProcessStatsLocked(String name) {
            Proc ps = mProcessStats.get(name);
            if (ps == null) {
                ps = new Proc(mBsi, name);
                mProcessStats.put(name, ps);
            }

            return ps;
        }

        public void updateUidProcessStateLocked(int procState) {
            int uidRunningState;
            if (procState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
                uidRunningState = ActivityManager.PROCESS_STATE_NONEXISTENT;
            } else if (procState == ActivityManager.PROCESS_STATE_TOP) {
                uidRunningState = PROCESS_STATE_TOP;
            } else if (procState <= ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE) {
                // Persistent and other foreground states go here.
                uidRunningState = PROCESS_STATE_FOREGROUND_SERVICE;
            } else if (procState <= ActivityManager.PROCESS_STATE_TOP_SLEEPING) {
                uidRunningState = PROCESS_STATE_TOP_SLEEPING;
            } else if (procState <= ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND) {
                // Persistent and other foreground states go here.
                uidRunningState = PROCESS_STATE_FOREGROUND;
            } else if (procState <= ActivityManager.PROCESS_STATE_RECEIVER) {
                uidRunningState = PROCESS_STATE_BACKGROUND;
            } else {
                uidRunningState = PROCESS_STATE_CACHED;
            }

            if (mProcessState == uidRunningState) return;

            final long elapsedRealtime = mBsi.mClocks.elapsedRealtime();

            if (mProcessState != ActivityManager.PROCESS_STATE_NONEXISTENT) {
                mProcessStateTimer[mProcessState].stopRunningLocked(elapsedRealtime);
            }
            mProcessState = uidRunningState;
            if (uidRunningState != ActivityManager.PROCESS_STATE_NONEXISTENT) {
                if (mProcessStateTimer[uidRunningState] == null) {
                    makeProcessState(uidRunningState, null);
                }
                mProcessStateTimer[uidRunningState].startRunningLocked(elapsedRealtime);
            }
        }

        public SparseArray<? extends Pid> getPidStats() {
            return mPids;
        }

        public Pid getPidStatsLocked(int pid) {
            Pid p = mPids.get(pid);
            if (p == null) {
                p = new Pid();
                mPids.put(pid, p);
            }
            return p;
        }

        /**
         * Retrieve the statistics object for a particular service, creating
         * if needed.
         */
        public Pkg getPackageStatsLocked(String name) {
            Pkg ps = mPackageStats.get(name);
            if (ps == null) {
                ps = new Pkg(mBsi);
                mPackageStats.put(name, ps);
            }

            return ps;
        }

        /**
         * Retrieve the statistics object for a particular service, creating
         * if needed.
         */
        public Pkg.Serv getServiceStatsLocked(String pkg, String serv) {
            Pkg ps = getPackageStatsLocked(pkg);
            Pkg.Serv ss = ps.mServiceStats.get(serv);
            if (ss == null) {
                ss = ps.newServiceStatsLocked();
                ps.mServiceStats.put(serv, ss);
            }

            return ss;
        }

        public void readSyncSummaryFromParcelLocked(String name, Parcel in) {
            StopwatchTimer timer = mSyncStats.instantiateObject();
            timer.readSummaryFromParcelLocked(in);
            mSyncStats.add(name, timer);
        }

        public void readJobSummaryFromParcelLocked(String name, Parcel in) {
            StopwatchTimer timer = mJobStats.instantiateObject();
            timer.readSummaryFromParcelLocked(in);
            mJobStats.add(name, timer);
        }

        public void readWakeSummaryFromParcelLocked(String wlName, Parcel in) {
            Wakelock wl = new Wakelock(mBsi, this);
            mWakelockStats.add(wlName, wl);
            if (in.readInt() != 0) {
                wl.getStopwatchTimer(WAKE_TYPE_FULL).readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                wl.getStopwatchTimer(WAKE_TYPE_PARTIAL).readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                wl.getStopwatchTimer(WAKE_TYPE_WINDOW).readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                wl.getStopwatchTimer(WAKE_TYPE_DRAW).readSummaryFromParcelLocked(in);
            }
        }

        public StopwatchTimer getSensorTimerLocked(int sensor, boolean create) {
            Sensor se = mSensorStats.get(sensor);
            if (se == null) {
                if (!create) {
                    return null;
                }
                se = new Sensor(mBsi, this, sensor);
                mSensorStats.put(sensor, se);
            }
            StopwatchTimer t = se.mTimer;
            if (t != null) {
                return t;
            }
            ArrayList<StopwatchTimer> timers = mBsi.mSensorTimers.get(sensor);
            if (timers == null) {
                timers = new ArrayList<StopwatchTimer>();
                mBsi.mSensorTimers.put(sensor, timers);
            }
            t = new StopwatchTimer(mBsi.mClocks, this, BatteryStats.SENSOR, timers,
                    mBsi.mOnBatteryTimeBase);
            se.mTimer = t;
            return t;
        }

        public void noteStartSyncLocked(String name, long elapsedRealtimeMs) {
            StopwatchTimer t = mSyncStats.startObject(name);
            if (t != null) {
                t.startRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStopSyncLocked(String name, long elapsedRealtimeMs) {
            StopwatchTimer t = mSyncStats.stopObject(name);
            if (t != null) {
                t.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStartJobLocked(String name, long elapsedRealtimeMs) {
            StopwatchTimer t = mJobStats.startObject(name);
            if (t != null) {
                t.startRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStopJobLocked(String name, long elapsedRealtimeMs) {
            StopwatchTimer t = mJobStats.stopObject(name);
            if (t != null) {
                t.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStartWakeLocked(int pid, String name, int type, long elapsedRealtimeMs) {
            Wakelock wl = mWakelockStats.startObject(name);
            if (wl != null) {
                wl.getStopwatchTimer(type).startRunningLocked(elapsedRealtimeMs);
            }
            if (pid >= 0 && type == WAKE_TYPE_PARTIAL) {
                Pid p = getPidStatsLocked(pid);
                if (p.mWakeNesting++ == 0) {
                    p.mWakeStartMs = elapsedRealtimeMs;
                }
            }
        }

        public void noteStopWakeLocked(int pid, String name, int type, long elapsedRealtimeMs) {
            Wakelock wl = mWakelockStats.stopObject(name);
            if (wl != null) {
                wl.getStopwatchTimer(type).stopRunningLocked(elapsedRealtimeMs);
            }
            if (pid >= 0 && type == WAKE_TYPE_PARTIAL) {
                Pid p = mPids.get(pid);
                if (p != null && p.mWakeNesting > 0) {
                    if (p.mWakeNesting-- == 1) {
                        p.mWakeSumMs += elapsedRealtimeMs - p.mWakeStartMs;
                        p.mWakeStartMs = 0;
                    }
                }
            }
        }

        public void reportExcessiveWakeLocked(String proc, long overTime, long usedTime) {
            Proc p = getProcessStatsLocked(proc);
            if (p != null) {
                p.addExcessiveWake(overTime, usedTime);
            }
        }

        public void reportExcessiveCpuLocked(String proc, long overTime, long usedTime) {
            Proc p = getProcessStatsLocked(proc);
            if (p != null) {
                p.addExcessiveCpu(overTime, usedTime);
            }
        }

        public void noteStartSensor(int sensor, long elapsedRealtimeMs) {
            StopwatchTimer t = getSensorTimerLocked(sensor, true);
            if (t != null) {
                t.startRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStopSensor(int sensor, long elapsedRealtimeMs) {
            // Don't create a timer if one doesn't already exist
            StopwatchTimer t = getSensorTimerLocked(sensor, false);
            if (t != null) {
                t.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStartGps(long elapsedRealtimeMs) {
            StopwatchTimer t = getSensorTimerLocked(Sensor.GPS, true);
            if (t != null) {
                t.startRunningLocked(elapsedRealtimeMs);
            }
        }

        public void noteStopGps(long elapsedRealtimeMs) {
            StopwatchTimer t = getSensorTimerLocked(Sensor.GPS, false);
            if (t != null) {
                t.stopRunningLocked(elapsedRealtimeMs);
            }
        }

        public BatteryStatsImpl getBatteryStats() {
            return mBsi;
        }
    }

    public BatteryStatsImpl(File systemDir, Handler handler, ExternalStatsSync externalSync) {
        this(new SystemClocks(), systemDir, handler, externalSync, null);
    }

    public BatteryStatsImpl(File systemDir, Handler handler, ExternalStatsSync externalSync,
                            PlatformIdleStateCallback cb) {
        this(new SystemClocks(), systemDir, handler, externalSync, cb);
    }

    public BatteryStatsImpl(Clocks clocks, File systemDir, Handler handler,
            ExternalStatsSync externalSync, PlatformIdleStateCallback cb) {
        init(clocks);

        if (systemDir != null) {
            mFile = new JournaledFile(new File(systemDir, "batterystats.bin"),
                    new File(systemDir, "batterystats.bin.tmp"));
        } else {
            mFile = null;
        }
        mCheckinFile = new AtomicFile(new File(systemDir, "batterystats-checkin.bin"));
        mDailyFile = new AtomicFile(new File(systemDir, "batterystats-daily.xml"));
        mExternalSync = externalSync;
        mHandler = new MyHandler(handler.getLooper());
        mStartCount++;
        mScreenOnTimer = new StopwatchTimer(mClocks, null, -1, null, mOnBatteryTimeBase);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i] = new StopwatchTimer(mClocks, null, -100-i, null,
                    mOnBatteryTimeBase);
        }
        mInteractiveTimer = new StopwatchTimer(mClocks, null, -10, null, mOnBatteryTimeBase);
        mPowerSaveModeEnabledTimer = new StopwatchTimer(mClocks, null, -2, null,
                mOnBatteryTimeBase);
        mDeviceIdleModeLightTimer = new StopwatchTimer(mClocks, null, -11, null,
                mOnBatteryTimeBase);
        mDeviceIdleModeFullTimer = new StopwatchTimer(mClocks, null, -14, null, mOnBatteryTimeBase);
        mDeviceLightIdlingTimer = new StopwatchTimer(mClocks, null, -15, null, mOnBatteryTimeBase);
        mDeviceIdlingTimer = new StopwatchTimer(mClocks, null, -12, null, mOnBatteryTimeBase);
        mPhoneOnTimer = new StopwatchTimer(mClocks, null, -3, null, mOnBatteryTimeBase);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i] = new StopwatchTimer(mClocks, null, -200-i, null,
                    mOnBatteryTimeBase);
        }
        mPhoneSignalScanningTimer = new StopwatchTimer(mClocks, null, -200+1, null,
                mOnBatteryTimeBase);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i] = new StopwatchTimer(mClocks, null, -300-i, null,
                    mOnBatteryTimeBase);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i] = new LongSamplingCounter(mOnBatteryTimeBase);
            mNetworkPacketActivityCounters[i] = new LongSamplingCounter(mOnBatteryTimeBase);
        }
        mWifiActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase, NUM_WIFI_TX_LEVELS);
        mBluetoothActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase,
                NUM_BT_TX_LEVELS);
        mModemActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase,
                ModemActivityInfo.TX_POWER_LEVELS);

        mMobileRadioActiveTimer = new StopwatchTimer(mClocks, null, -400, null, mOnBatteryTimeBase);
        mMobileRadioActivePerAppTimer = new StopwatchTimer(mClocks, null, -401, null,
                mOnBatteryTimeBase);
        mMobileRadioActiveAdjustedTime = new LongSamplingCounter(mOnBatteryTimeBase);
        mMobileRadioActiveUnknownTime = new LongSamplingCounter(mOnBatteryTimeBase);
        mMobileRadioActiveUnknownCount = new LongSamplingCounter(mOnBatteryTimeBase);
        mWifiOnTimer = new StopwatchTimer(mClocks, null, -4, null, mOnBatteryTimeBase);
        mGlobalWifiRunningTimer = new StopwatchTimer(mClocks, null, -5, null, mOnBatteryTimeBase);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i] = new StopwatchTimer(mClocks, null, -600-i, null,
                    mOnBatteryTimeBase);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i] = new StopwatchTimer(mClocks, null, -700-i, null,
                    mOnBatteryTimeBase);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i] = new StopwatchTimer(mClocks, null, -800-i, null,
                    mOnBatteryTimeBase);
        }
        mAudioOnTimer = new StopwatchTimer(mClocks, null, -7, null, mOnBatteryTimeBase);
        mVideoOnTimer = new StopwatchTimer(mClocks, null, -8, null, mOnBatteryTimeBase);
        mFlashlightOnTimer = new StopwatchTimer(mClocks, null, -9, null, mOnBatteryTimeBase);
        mCameraOnTimer = new StopwatchTimer(mClocks, null, -13, null, mOnBatteryTimeBase);
        mBluetoothScanTimer = new StopwatchTimer(mClocks, null, -14, null, mOnBatteryTimeBase);
        mDischargeScreenOffCounter = new LongSamplingCounter(mOnBatteryScreenOffTimeBase);
        mDischargeCounter = new LongSamplingCounter(mOnBatteryTimeBase);
        mOnBattery = mOnBatteryInternal = false;
        long uptime = mClocks.uptimeMillis() * 1000;
        long realtime = mClocks.elapsedRealtime() * 1000;
        initTimes(uptime, realtime);
        mStartPlatformVersion = mEndPlatformVersion = Build.ID;
        mDischargeStartLevel = 0;
        mDischargeUnplugLevel = 0;
        mDischargePlugLevel = -1;
        mDischargeCurrentLevel = 0;
        mCurrentBatteryLevel = 0;
        initDischarge();
        clearHistoryLocked();
        updateDailyDeadlineLocked();
        mPlatformIdleStateCallback = cb;
    }

    public BatteryStatsImpl(Parcel p) {
        this(new SystemClocks(), p);
    }

    public BatteryStatsImpl(Clocks clocks, Parcel p) {
        init(clocks);
        mFile = null;
        mCheckinFile = null;
        mDailyFile = null;
        mHandler = null;
        mExternalSync = null;
        clearHistoryLocked();
        readFromParcel(p);
        mPlatformIdleStateCallback = null;
    }

    public void setPowerProfile(PowerProfile profile) {
        synchronized (this) {
            mPowerProfile = profile;

            // We need to initialize the KernelCpuSpeedReaders to read from
            // the first cpu of each core. Once we have the PowerProfile, we have access to this
            // information.
            final int numClusters = mPowerProfile.getNumCpuClusters();
            mKernelCpuSpeedReaders = new KernelCpuSpeedReader[numClusters];
            int firstCpuOfCluster = 0;
            for (int i = 0; i < numClusters; i++) {
                final int numSpeedSteps = mPowerProfile.getNumSpeedStepsInCpuCluster(i);
                mKernelCpuSpeedReaders[i] = new KernelCpuSpeedReader(firstCpuOfCluster,
                        numSpeedSteps);
                firstCpuOfCluster += mPowerProfile.getNumCoresInCpuCluster(i);
            }

            if (mEstimatedBatteryCapacity == -1) {
                // Initialize the estimated battery capacity to a known preset one.
                mEstimatedBatteryCapacity = (int) mPowerProfile.getBatteryCapacity();
            }
        }
    }

    public void setCallback(BatteryCallback cb) {
        mCallback = cb;
    }

    public void setRadioScanningTimeout(long timeout) {
        if (mPhoneSignalScanningTimer != null) {
            mPhoneSignalScanningTimer.setTimeout(timeout);
        }
    }

    public void updateDailyDeadlineLocked() {
        // Get the current time.
        long currentTime = mDailyStartTime = System.currentTimeMillis();
        Calendar calDeadline = Calendar.getInstance();
        calDeadline.setTimeInMillis(currentTime);

        // Move time up to the next day, ranging from 1am to 3pm.
        calDeadline.set(Calendar.DAY_OF_YEAR, calDeadline.get(Calendar.DAY_OF_YEAR) + 1);
        calDeadline.set(Calendar.MILLISECOND, 0);
        calDeadline.set(Calendar.SECOND, 0);
        calDeadline.set(Calendar.MINUTE, 0);
        calDeadline.set(Calendar.HOUR_OF_DAY, 1);
        mNextMinDailyDeadline = calDeadline.getTimeInMillis();
        calDeadline.set(Calendar.HOUR_OF_DAY, 3);
        mNextMaxDailyDeadline = calDeadline.getTimeInMillis();
    }

    public void recordDailyStatsIfNeededLocked(boolean settled) {
        long currentTime = System.currentTimeMillis();
        if (currentTime >= mNextMaxDailyDeadline) {
            recordDailyStatsLocked();
        } else if (settled && currentTime >= mNextMinDailyDeadline) {
            recordDailyStatsLocked();
        } else if (currentTime < (mDailyStartTime-(1000*60*60*24))) {
            recordDailyStatsLocked();
        }
    }

    public void recordDailyStatsLocked() {
        DailyItem item = new DailyItem();
        item.mStartTime = mDailyStartTime;
        item.mEndTime = System.currentTimeMillis();
        boolean hasData = false;
        if (mDailyDischargeStepTracker.mNumStepDurations > 0) {
            hasData = true;
            item.mDischargeSteps = new LevelStepTracker(
                    mDailyDischargeStepTracker.mNumStepDurations,
                    mDailyDischargeStepTracker.mStepDurations);
        }
        if (mDailyChargeStepTracker.mNumStepDurations > 0) {
            hasData = true;
            item.mChargeSteps = new LevelStepTracker(
                    mDailyChargeStepTracker.mNumStepDurations,
                    mDailyChargeStepTracker.mStepDurations);
        }
        if (mDailyPackageChanges != null) {
            hasData = true;
            item.mPackageChanges = mDailyPackageChanges;
            mDailyPackageChanges = null;
        }
        mDailyDischargeStepTracker.init();
        mDailyChargeStepTracker.init();
        updateDailyDeadlineLocked();

        if (hasData) {
            mDailyItems.add(item);
            while (mDailyItems.size() > MAX_DAILY_ITEMS) {
                mDailyItems.remove(0);
            }
            final ByteArrayOutputStream memStream = new ByteArrayOutputStream();
            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(memStream, StandardCharsets.UTF_8.name());
                writeDailyItemsLocked(out);
                BackgroundThread.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mCheckinFile) {
                            FileOutputStream stream = null;
                            try {
                                stream = mDailyFile.startWrite();
                                memStream.writeTo(stream);
                                stream.flush();
                                FileUtils.sync(stream);
                                stream.close();
                                mDailyFile.finishWrite(stream);
                            } catch (IOException e) {
                                Slog.w("BatteryStats",
                                        "Error writing battery daily items", e);
                                mDailyFile.failWrite(stream);
                            }
                        }
                    }
                });
            } catch (IOException e) {
            }
        }
    }

    private void writeDailyItemsLocked(XmlSerializer out) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        out.startDocument(null, true);
        out.startTag(null, "daily-items");
        for (int i=0; i<mDailyItems.size(); i++) {
            final DailyItem dit = mDailyItems.get(i);
            out.startTag(null, "item");
            out.attribute(null, "start", Long.toString(dit.mStartTime));
            out.attribute(null, "end", Long.toString(dit.mEndTime));
            writeDailyLevelSteps(out, "dis", dit.mDischargeSteps, sb);
            writeDailyLevelSteps(out, "chg", dit.mChargeSteps, sb);
            if (dit.mPackageChanges != null) {
                for (int j=0; j<dit.mPackageChanges.size(); j++) {
                    PackageChange pc = dit.mPackageChanges.get(j);
                    if (pc.mUpdate) {
                        out.startTag(null, "upd");
                        out.attribute(null, "pkg", pc.mPackageName);
                        out.attribute(null, "ver", Integer.toString(pc.mVersionCode));
                        out.endTag(null, "upd");
                    } else {
                        out.startTag(null, "rem");
                        out.attribute(null, "pkg", pc.mPackageName);
                        out.endTag(null, "rem");
                    }
                }
            }
            out.endTag(null, "item");
        }
        out.endTag(null, "daily-items");
        out.endDocument();
    }

    private void writeDailyLevelSteps(XmlSerializer out, String tag, LevelStepTracker steps,
            StringBuilder tmpBuilder) throws IOException {
        if (steps != null) {
            out.startTag(null, tag);
            out.attribute(null, "n", Integer.toString(steps.mNumStepDurations));
            for (int i=0; i<steps.mNumStepDurations; i++) {
                out.startTag(null, "s");
                tmpBuilder.setLength(0);
                steps.encodeEntryAt(i, tmpBuilder);
                out.attribute(null, "v", tmpBuilder.toString());
                out.endTag(null, "s");
            }
            out.endTag(null, tag);
        }
    }

    public void readDailyStatsLocked() {
        Slog.d(TAG, "Reading daily items from " + mDailyFile.getBaseFile());
        mDailyItems.clear();
        FileInputStream stream;
        try {
            stream = mDailyFile.openRead();
        } catch (FileNotFoundException e) {
            return;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, StandardCharsets.UTF_8.name());
            readDailyItemsLocked(parser);
        } catch (XmlPullParserException e) {
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
            }
        }
    }

    private void readDailyItemsLocked(XmlPullParser parser) {
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("no start tag found");
            }

            int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("item")) {
                    readDailyItemTagLocked(parser);
                } else {
                    Slog.w(TAG, "Unknown element under <daily-items>: "
                            + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }

        } catch (IllegalStateException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        } catch (NullPointerException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        } catch (NumberFormatException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        } catch (IOException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        } catch (IndexOutOfBoundsException e) {
            Slog.w(TAG, "Failed parsing daily " + e);
        }
    }

    void readDailyItemTagLocked(XmlPullParser parser) throws NumberFormatException,
            XmlPullParserException, IOException {
        DailyItem dit = new DailyItem();
        String attr = parser.getAttributeValue(null, "start");
        if (attr != null) {
            dit.mStartTime = Long.parseLong(attr);
        }
        attr = parser.getAttributeValue(null, "end");
        if (attr != null) {
            dit.mEndTime = Long.parseLong(attr);
        }
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("dis")) {
                readDailyItemTagDetailsLocked(parser, dit, false, "dis");
            } else if (tagName.equals("chg")) {
                readDailyItemTagDetailsLocked(parser, dit, true, "chg");
            } else if (tagName.equals("upd")) {
                if (dit.mPackageChanges == null) {
                    dit.mPackageChanges = new ArrayList<>();
                }
                PackageChange pc = new PackageChange();
                pc.mUpdate = true;
                pc.mPackageName = parser.getAttributeValue(null, "pkg");
                String verStr = parser.getAttributeValue(null, "ver");
                pc.mVersionCode = verStr != null ? Integer.parseInt(verStr) : 0;
                dit.mPackageChanges.add(pc);
                XmlUtils.skipCurrentTag(parser);
            } else if (tagName.equals("rem")) {
                if (dit.mPackageChanges == null) {
                    dit.mPackageChanges = new ArrayList<>();
                }
                PackageChange pc = new PackageChange();
                pc.mUpdate = false;
                pc.mPackageName = parser.getAttributeValue(null, "pkg");
                dit.mPackageChanges.add(pc);
                XmlUtils.skipCurrentTag(parser);
            } else {
                Slog.w(TAG, "Unknown element under <item>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
        mDailyItems.add(dit);
    }

    void readDailyItemTagDetailsLocked(XmlPullParser parser, DailyItem dit, boolean isCharge,
            String tag)
            throws NumberFormatException, XmlPullParserException, IOException {
        final String numAttr = parser.getAttributeValue(null, "n");
        if (numAttr == null) {
            Slog.w(TAG, "Missing 'n' attribute at " + parser.getPositionDescription());
            XmlUtils.skipCurrentTag(parser);
            return;
        }
        final int num = Integer.parseInt(numAttr);
        LevelStepTracker steps = new LevelStepTracker(num);
        if (isCharge) {
            dit.mChargeSteps = steps;
        } else {
            dit.mDischargeSteps = steps;
        }
        int i = 0;
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if ("s".equals(tagName)) {
                if (i < num) {
                    String valueAttr = parser.getAttributeValue(null, "v");
                    if (valueAttr != null) {
                        steps.decodeEntryAt(i, valueAttr);
                        i++;
                    }
                }
            } else {
                Slog.w(TAG, "Unknown element under <" + tag + ">: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
        steps.mNumStepDurations = i;
    }

    @Override
    public DailyItem getDailyItemLocked(int daysAgo) {
        int index = mDailyItems.size()-1-daysAgo;
        return index >= 0 ? mDailyItems.get(index) : null;
    }

    @Override
    public long getCurrentDailyStartTime() {
        return mDailyStartTime;
    }

    @Override
    public long getNextMinDailyDeadline() {
        return mNextMinDailyDeadline;
    }

    @Override
    public long getNextMaxDailyDeadline() {
        return mNextMaxDailyDeadline;
    }

    @Override
    public boolean startIteratingOldHistoryLocked() {
        if (DEBUG_HISTORY) Slog.i(TAG, "ITERATING: buff size=" + mHistoryBuffer.dataSize()
                + " pos=" + mHistoryBuffer.dataPosition());
        if ((mHistoryIterator = mHistory) == null) {
            return false;
        }
        mHistoryBuffer.setDataPosition(0);
        mHistoryReadTmp.clear();
        mReadOverflow = false;
        mIteratingHistory = true;
        return true;
    }

    @Override
    public boolean getNextOldHistoryLocked(HistoryItem out) {
        boolean end = mHistoryBuffer.dataPosition() >= mHistoryBuffer.dataSize();
        if (!end) {
            readHistoryDelta(mHistoryBuffer, mHistoryReadTmp);
            mReadOverflow |= mHistoryReadTmp.cmd == HistoryItem.CMD_OVERFLOW;
        }
        HistoryItem cur = mHistoryIterator;
        if (cur == null) {
            if (!mReadOverflow && !end) {
                Slog.w(TAG, "Old history ends before new history!");
            }
            return false;
        }
        out.setTo(cur);
        mHistoryIterator = cur.next;
        if (!mReadOverflow) {
            if (end) {
                Slog.w(TAG, "New history ends before old history!");
            } else if (!out.same(mHistoryReadTmp)) {
                PrintWriter pw = new FastPrintWriter(new LogWriter(android.util.Log.WARN, TAG));
                pw.println("Histories differ!");
                pw.println("Old history:");
                (new HistoryPrinter()).printNextItem(pw, out, 0, false, true);
                pw.println("New history:");
                (new HistoryPrinter()).printNextItem(pw, mHistoryReadTmp, 0, false,
                        true);
                pw.flush();
            }
        }
        return true;
    }

    @Override
    public void finishIteratingOldHistoryLocked() {
        mIteratingHistory = false;
        mHistoryBuffer.setDataPosition(mHistoryBuffer.dataSize());
        mHistoryIterator = null;
    }

    public int getHistoryTotalSize() {
        return MAX_HISTORY_BUFFER;
    }

    public int getHistoryUsedSize() {
        return mHistoryBuffer.dataSize();
    }

    @Override
    public boolean startIteratingHistoryLocked() {
        if (DEBUG_HISTORY) Slog.i(TAG, "ITERATING: buff size=" + mHistoryBuffer.dataSize()
                + " pos=" + mHistoryBuffer.dataPosition());
        if (mHistoryBuffer.dataSize() <= 0) {
            return false;
        }
        mHistoryBuffer.setDataPosition(0);
        mReadOverflow = false;
        mIteratingHistory = true;
        mReadHistoryStrings = new String[mHistoryTagPool.size()];
        mReadHistoryUids = new int[mHistoryTagPool.size()];
        mReadHistoryChars = 0;
        for (HashMap.Entry<HistoryTag, Integer> ent : mHistoryTagPool.entrySet()) {
            final HistoryTag tag = ent.getKey();
            final int idx = ent.getValue();
            mReadHistoryStrings[idx] = tag.string;
            mReadHistoryUids[idx] = tag.uid;
            mReadHistoryChars += tag.string.length() + 1;
        }
        return true;
    }

    @Override
    public int getHistoryStringPoolSize() {
        return mReadHistoryStrings.length;
    }

    @Override
    public int getHistoryStringPoolBytes() {
        // Each entry is a fixed 12 bytes: 4 for index, 4 for uid, 4 for string size
        // Each string character is 2 bytes.
        return (mReadHistoryStrings.length * 12) + (mReadHistoryChars * 2);
    }

    @Override
    public String getHistoryTagPoolString(int index) {
        return mReadHistoryStrings[index];
    }

    @Override
    public int getHistoryTagPoolUid(int index) {
        return mReadHistoryUids[index];
    }

    @Override
    public boolean getNextHistoryLocked(HistoryItem out) {
        final int pos = mHistoryBuffer.dataPosition();
        if (pos == 0) {
            out.clear();
        }
        boolean end = pos >= mHistoryBuffer.dataSize();
        if (end) {
            return false;
        }

        final long lastRealtime = out.time;
        final long lastWalltime = out.currentTime;
        readHistoryDelta(mHistoryBuffer, out);
        if (out.cmd != HistoryItem.CMD_CURRENT_TIME
                && out.cmd != HistoryItem.CMD_RESET && lastWalltime != 0) {
            out.currentTime = lastWalltime + (out.time - lastRealtime);
        }
        return true;
    }

    @Override
    public void finishIteratingHistoryLocked() {
        mIteratingHistory = false;
        mHistoryBuffer.setDataPosition(mHistoryBuffer.dataSize());
        mReadHistoryStrings = null;
    }

    @Override
    public long getHistoryBaseTime() {
        return mHistoryBaseTime;
    }

    @Override
    public int getStartCount() {
        return mStartCount;
    }

    public boolean isOnBattery() {
        return mOnBattery;
    }

    public boolean isCharging() {
        return mCharging;
    }

    public boolean isScreenOn() {
        return mScreenState == Display.STATE_ON;
    }

    void initTimes(long uptime, long realtime) {
        mStartClockTime = System.currentTimeMillis();
        mOnBatteryTimeBase.init(uptime, realtime);
        mOnBatteryScreenOffTimeBase.init(uptime, realtime);
        mRealtime = 0;
        mUptime = 0;
        mRealtimeStart = realtime;
        mUptimeStart = uptime;
    }

    void initDischarge() {
        mLowDischargeAmountSinceCharge = 0;
        mHighDischargeAmountSinceCharge = 0;
        mDischargeAmountScreenOn = 0;
        mDischargeAmountScreenOnSinceCharge = 0;
        mDischargeAmountScreenOff = 0;
        mDischargeAmountScreenOffSinceCharge = 0;
        mDischargeStepTracker.init();
        mChargeStepTracker.init();
        mDischargeScreenOffCounter.reset(false);
        mDischargeCounter.reset(false);
    }

    public void resetAllStatsCmdLocked() {
        resetAllStatsLocked();
        final long mSecUptime = mClocks.uptimeMillis();
        long uptime = mSecUptime * 1000;
        long mSecRealtime = mClocks.elapsedRealtime();
        long realtime = mSecRealtime * 1000;
        mDischargeStartLevel = mHistoryCur.batteryLevel;
        pullPendingStateUpdatesLocked();
        addHistoryRecordLocked(mSecRealtime, mSecUptime);
        mDischargeCurrentLevel = mDischargeUnplugLevel = mDischargePlugLevel
                = mCurrentBatteryLevel = mHistoryCur.batteryLevel;
        mOnBatteryTimeBase.reset(uptime, realtime);
        mOnBatteryScreenOffTimeBase.reset(uptime, realtime);
        if ((mHistoryCur.states&HistoryItem.STATE_BATTERY_PLUGGED_FLAG) == 0) {
            if (mScreenState == Display.STATE_ON) {
                mDischargeScreenOnUnplugLevel = mHistoryCur.batteryLevel;
                mDischargeScreenOffUnplugLevel = 0;
            } else {
                mDischargeScreenOnUnplugLevel = 0;
                mDischargeScreenOffUnplugLevel = mHistoryCur.batteryLevel;
            }
            mDischargeAmountScreenOn = 0;
            mDischargeAmountScreenOff = 0;
        }
        initActiveHistoryEventsLocked(mSecRealtime, mSecUptime);
    }

    private void resetAllStatsLocked() {
        final long uptimeMillis = mClocks.uptimeMillis();
        final long elapsedRealtimeMillis = mClocks.elapsedRealtime();
        mStartCount = 0;
        initTimes(uptimeMillis * 1000, elapsedRealtimeMillis * 1000);
        mScreenOnTimer.reset(false);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i].reset(false);
        }

        if (mPowerProfile != null) {
            mEstimatedBatteryCapacity = (int) mPowerProfile.getBatteryCapacity();
        } else {
            mEstimatedBatteryCapacity = -1;
        }
        mInteractiveTimer.reset(false);
        mPowerSaveModeEnabledTimer.reset(false);
        mLastIdleTimeStart = elapsedRealtimeMillis;
        mLongestLightIdleTime = 0;
        mLongestFullIdleTime = 0;
        mDeviceIdleModeLightTimer.reset(false);
        mDeviceIdleModeFullTimer.reset(false);
        mDeviceLightIdlingTimer.reset(false);
        mDeviceIdlingTimer.reset(false);
        mPhoneOnTimer.reset(false);
        mAudioOnTimer.reset(false);
        mVideoOnTimer.reset(false);
        mFlashlightOnTimer.reset(false);
        mCameraOnTimer.reset(false);
        mBluetoothScanTimer.reset(false);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i].reset(false);
        }
        mPhoneSignalScanningTimer.reset(false);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i].reset(false);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i].reset(false);
            mNetworkPacketActivityCounters[i].reset(false);
        }
        mMobileRadioActiveTimer.reset(false);
        mMobileRadioActivePerAppTimer.reset(false);
        mMobileRadioActiveAdjustedTime.reset(false);
        mMobileRadioActiveUnknownTime.reset(false);
        mMobileRadioActiveUnknownCount.reset(false);
        mWifiOnTimer.reset(false);
        mGlobalWifiRunningTimer.reset(false);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i].reset(false);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i].reset(false);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i].reset(false);
        }
        mWifiActivity.reset(false);
        mBluetoothActivity.reset(false);
        mModemActivity.reset(false);
        mNumConnectivityChange = mLoadedNumConnectivityChange = mUnpluggedNumConnectivityChange = 0;

        for (int i=0; i<mUidStats.size(); i++) {
            if (mUidStats.valueAt(i).reset()) {
                mUidStats.remove(mUidStats.keyAt(i));
                i--;
            }
        }

        if (mKernelWakelockStats.size() > 0) {
            for (SamplingTimer timer : mKernelWakelockStats.values()) {
                mOnBatteryScreenOffTimeBase.remove(timer);
            }
            mKernelWakelockStats.clear();
        }

        if (mWakeupReasonStats.size() > 0) {
            for (SamplingTimer timer : mWakeupReasonStats.values()) {
                mOnBatteryTimeBase.remove(timer);
            }
            mWakeupReasonStats.clear();
        }

        mLastHistoryStepDetails = null;
        mLastStepCpuUserTime = mLastStepCpuSystemTime = 0;
        mCurStepCpuUserTime = mCurStepCpuSystemTime = 0;
        mLastStepCpuUserTime = mCurStepCpuUserTime = 0;
        mLastStepCpuSystemTime = mCurStepCpuSystemTime = 0;
        mLastStepStatUserTime = mCurStepStatUserTime = 0;
        mLastStepStatSystemTime = mCurStepStatSystemTime = 0;
        mLastStepStatIOWaitTime = mCurStepStatIOWaitTime = 0;
        mLastStepStatIrqTime = mCurStepStatIrqTime = 0;
        mLastStepStatSoftIrqTime = mCurStepStatSoftIrqTime = 0;
        mLastStepStatIdleTime = mCurStepStatIdleTime = 0;

        initDischarge();

        clearHistoryLocked();
    }

    private void initActiveHistoryEventsLocked(long elapsedRealtimeMs, long uptimeMs) {
        for (int i=0; i<HistoryItem.EVENT_COUNT; i++) {
            if (!mRecordAllHistory && i == HistoryItem.EVENT_PROC) {
                // Not recording process starts/stops.
                continue;
            }
            HashMap<String, SparseIntArray> active = mActiveEvents.getStateForEvent(i);
            if (active == null) {
                continue;
            }
            for (HashMap.Entry<String, SparseIntArray> ent : active.entrySet()) {
                SparseIntArray uids = ent.getValue();
                for (int j=0; j<uids.size(); j++) {
                    addHistoryEventLocked(elapsedRealtimeMs, uptimeMs, i, ent.getKey(),
                            uids.keyAt(j));
                }
            }
        }
    }

    void updateDischargeScreenLevelsLocked(boolean oldScreenOn, boolean newScreenOn) {
        if (oldScreenOn) {
            int diff = mDischargeScreenOnUnplugLevel - mDischargeCurrentLevel;
            if (diff > 0) {
                mDischargeAmountScreenOn += diff;
                mDischargeAmountScreenOnSinceCharge += diff;
            }
        } else {
            int diff = mDischargeScreenOffUnplugLevel - mDischargeCurrentLevel;
            if (diff > 0) {
                mDischargeAmountScreenOff += diff;
                mDischargeAmountScreenOffSinceCharge += diff;
            }
        }
        if (newScreenOn) {
            mDischargeScreenOnUnplugLevel = mDischargeCurrentLevel;
            mDischargeScreenOffUnplugLevel = 0;
        } else {
            mDischargeScreenOnUnplugLevel = 0;
            mDischargeScreenOffUnplugLevel = mDischargeCurrentLevel;
        }
    }

    public void pullPendingStateUpdatesLocked() {
        if (mOnBatteryInternal) {
            final boolean screenOn = mScreenState == Display.STATE_ON;
            updateDischargeScreenLevelsLocked(screenOn, screenOn);
        }
    }

    private String[] mMobileIfaces = EmptyArray.STRING;
    private String[] mWifiIfaces = EmptyArray.STRING;

    private final NetworkStatsFactory mNetworkStatsFactory = new NetworkStatsFactory();

    private static final int NETWORK_STATS_LAST = 0;
    private static final int NETWORK_STATS_NEXT = 1;
    private static final int NETWORK_STATS_DELTA = 2;

    private NetworkStats[] mMobileNetworkStats;
    private NetworkStats[] mWifiNetworkStats;

    /**
     * Retrieves the delta of network stats for the given network ifaces. Uses networkStatsBuffer
     * as a buffer of NetworkStats objects to cycle through when computing deltas.
     */
    private NetworkStats getNetworkStatsDeltaLocked(String[] ifaces,
                                                    NetworkStats[] networkStatsBuffer)
            throws IOException {
        if (!SystemProperties.getBoolean(NetworkManagementSocketTagger.PROP_QTAGUID_ENABLED,
                false)) {
            return null;
        }

        final NetworkStats stats = mNetworkStatsFactory.readNetworkStatsDetail(NetworkStats.UID_ALL,
                ifaces, NetworkStats.TAG_NONE, networkStatsBuffer[NETWORK_STATS_NEXT]);
        networkStatsBuffer[NETWORK_STATS_DELTA] = NetworkStats.subtract(stats,
                networkStatsBuffer[NETWORK_STATS_LAST], null, null,
                networkStatsBuffer[NETWORK_STATS_DELTA]);
        networkStatsBuffer[NETWORK_STATS_NEXT] = networkStatsBuffer[NETWORK_STATS_LAST];
        networkStatsBuffer[NETWORK_STATS_LAST] = stats;
        return networkStatsBuffer[NETWORK_STATS_DELTA];
    }

    /**
     * Distribute WiFi energy info and network traffic to apps.
     * @param info The energy information from the WiFi controller.
     */
    public void updateWifiStateLocked(@Nullable final WifiActivityEnergyInfo info) {
        if (DEBUG_ENERGY) {
            Slog.d(TAG, "Updating wifi stats");
        }

        final long elapsedRealtimeMs = mClocks.elapsedRealtime();
        NetworkStats delta = null;
        try {
            if (!ArrayUtils.isEmpty(mWifiIfaces)) {
                delta = getNetworkStatsDeltaLocked(mWifiIfaces, mWifiNetworkStats);
            }
        } catch (IOException e) {
            Slog.wtf(TAG, "Failed to get wifi network stats", e);
            return;
        }

        if (!mOnBatteryInternal) {
            return;
        }

        SparseLongArray rxPackets = new SparseLongArray();
        SparseLongArray txPackets = new SparseLongArray();
        long totalTxPackets = 0;
        long totalRxPackets = 0;
        if (delta != null) {
            final int size = delta.size();
            for (int i = 0; i < size; i++) {
                final NetworkStats.Entry entry = delta.getValues(i, mTmpNetworkStatsEntry);

                if (DEBUG_ENERGY) {
                    Slog.d(TAG, "Wifi uid " + entry.uid + ": delta rx=" + entry.rxBytes
                            + " tx=" + entry.txBytes + " rxPackets=" + entry.rxPackets
                            + " txPackets=" + entry.txPackets);
                }

                if (entry.rxBytes == 0 && entry.txBytes == 0) {
                    // Skip the lookup below since there is no work to do.
                    continue;
                }

                final Uid u = getUidStatsLocked(mapUid(entry.uid));
                if (entry.rxBytes != 0) {
                    u.noteNetworkActivityLocked(NETWORK_WIFI_RX_DATA, entry.rxBytes,
                            entry.rxPackets);
                    mNetworkByteActivityCounters[NETWORK_WIFI_RX_DATA].addCountLocked(
                            entry.rxBytes);
                    mNetworkPacketActivityCounters[NETWORK_WIFI_RX_DATA].addCountLocked(
                            entry.rxPackets);

                    rxPackets.put(u.getUid(), entry.rxPackets);

                    // Sum the total number of packets so that the Rx Power can
                    // be evenly distributed amongst the apps.
                    totalRxPackets += entry.rxPackets;
                }

                if (entry.txBytes != 0) {
                    u.noteNetworkActivityLocked(NETWORK_WIFI_TX_DATA, entry.txBytes,
                            entry.txPackets);
                    mNetworkByteActivityCounters[NETWORK_WIFI_TX_DATA].addCountLocked(
                            entry.txBytes);
                    mNetworkPacketActivityCounters[NETWORK_WIFI_TX_DATA].addCountLocked(
                            entry.txPackets);

                    txPackets.put(u.getUid(), entry.txPackets);

                    // Sum the total number of packets so that the Tx Power can
                    // be evenly distributed amongst the apps.
                    totalTxPackets += entry.txPackets;
                }
            }
        }

        if (info != null) {
            mHasWifiReporting = true;

            // Measured in mAms
            final long txTimeMs = info.getControllerTxTimeMillis();
            final long rxTimeMs = info.getControllerRxTimeMillis();
            final long idleTimeMs = info.getControllerIdleTimeMillis();
            final long totalTimeMs = txTimeMs + rxTimeMs + idleTimeMs;

            long leftOverRxTimeMs = rxTimeMs;
            long leftOverTxTimeMs = txTimeMs;

            if (DEBUG_ENERGY) {
                Slog.d(TAG, "------ BEGIN WiFi power blaming ------");
                Slog.d(TAG, "  Tx Time:    " + txTimeMs + " ms");
                Slog.d(TAG, "  Rx Time:    " + rxTimeMs + " ms");
                Slog.d(TAG, "  Idle Time:  " + idleTimeMs + " ms");
                Slog.d(TAG, "  Total Time: " + totalTimeMs + " ms");
            }

            long totalWifiLockTimeMs = 0;
            long totalScanTimeMs = 0;

            // On the first pass, collect some totals so that we can normalize power
            // calculations if we need to.
            final int uidStatsSize = mUidStats.size();
            for (int i = 0; i < uidStatsSize; i++) {
                final Uid uid = mUidStats.valueAt(i);

                // Sum the total scan power for all apps.
                totalScanTimeMs += uid.mWifiScanTimer.getTimeSinceMarkLocked(
                        elapsedRealtimeMs * 1000) / 1000;

                // Sum the total time holding wifi lock for all apps.
                totalWifiLockTimeMs += uid.mFullWifiLockTimer.getTimeSinceMarkLocked(
                        elapsedRealtimeMs * 1000) / 1000;
            }

            if (DEBUG_ENERGY && totalScanTimeMs > rxTimeMs) {
                Slog.d(TAG, "  !Estimated scan time > Actual rx time (" + totalScanTimeMs + " ms > "
                        + rxTimeMs + " ms). Normalizing scan time.");
            }
            if (DEBUG_ENERGY && totalScanTimeMs > txTimeMs) {
                Slog.d(TAG, "  !Estimated scan time > Actual tx time (" + totalScanTimeMs + " ms > "
                        + txTimeMs + " ms). Normalizing scan time.");
            }

            // Actually assign and distribute power usage to apps.
            for (int i = 0; i < uidStatsSize; i++) {
                final Uid uid = mUidStats.valueAt(i);

                long scanTimeSinceMarkMs = uid.mWifiScanTimer.getTimeSinceMarkLocked(
                        elapsedRealtimeMs * 1000) / 1000;
                if (scanTimeSinceMarkMs > 0) {
                    // Set the new mark so that next time we get new data since this point.
                    uid.mWifiScanTimer.setMark(elapsedRealtimeMs);

                    long scanRxTimeSinceMarkMs = scanTimeSinceMarkMs;
                    long scanTxTimeSinceMarkMs = scanTimeSinceMarkMs;

                    // Our total scan time is more than the reported Tx/Rx time.
                    // This is possible because the cost of a scan is approximate.
                    // Let's normalize the result so that we evenly blame each app
                    // scanning.
                    //
                    // This means that we may have apps that transmitted/received packets not be
                    // blamed for this, but this is fine as scans are relatively more expensive.
                    if (totalScanTimeMs > rxTimeMs) {
                        scanRxTimeSinceMarkMs = (rxTimeMs * scanRxTimeSinceMarkMs) /
                                totalScanTimeMs;
                    }
                    if (totalScanTimeMs > txTimeMs) {
                        scanTxTimeSinceMarkMs = (txTimeMs * scanTxTimeSinceMarkMs) /
                                totalScanTimeMs;
                    }

                    if (DEBUG_ENERGY) {
                        Slog.d(TAG, "  ScanTime for UID " + uid.getUid() + ": Rx:"
                                + scanRxTimeSinceMarkMs + " ms  Tx:"
                                + scanTxTimeSinceMarkMs + " ms)");
                    }

                    ControllerActivityCounterImpl activityCounter =
                            uid.getOrCreateWifiControllerActivityLocked();
                    activityCounter.getRxTimeCounter().addCountLocked(scanRxTimeSinceMarkMs);
                    activityCounter.getTxTimeCounters()[0].addCountLocked(scanTxTimeSinceMarkMs);
                    leftOverRxTimeMs -= scanRxTimeSinceMarkMs;
                    leftOverTxTimeMs -= scanTxTimeSinceMarkMs;
                }

                // Distribute evenly the power consumed while Idle to each app holding a WiFi
                // lock.
                final long wifiLockTimeSinceMarkMs = uid.mFullWifiLockTimer.getTimeSinceMarkLocked(
                        elapsedRealtimeMs * 1000) / 1000;
                if (wifiLockTimeSinceMarkMs > 0) {
                    // Set the new mark so that next time we get new data since this point.
                    uid.mFullWifiLockTimer.setMark(elapsedRealtimeMs);

                    final long myIdleTimeMs = (wifiLockTimeSinceMarkMs * idleTimeMs)
                            / totalWifiLockTimeMs;
                    if (DEBUG_ENERGY) {
                        Slog.d(TAG, "  IdleTime for UID " + uid.getUid() + ": "
                                + myIdleTimeMs + " ms");
                    }
                    uid.getOrCreateWifiControllerActivityLocked().getIdleTimeCounter()
                            .addCountLocked(myIdleTimeMs);
                }
            }

            if (DEBUG_ENERGY) {
                Slog.d(TAG, "  New RxPower: " + leftOverRxTimeMs + " ms");
                Slog.d(TAG, "  New TxPower: " + leftOverTxTimeMs + " ms");
            }

            // Distribute the remaining Tx power appropriately between all apps that transmitted
            // packets.
            for (int i = 0; i < txPackets.size(); i++) {
                final Uid uid = getUidStatsLocked(txPackets.keyAt(i));
                final long myTxTimeMs = (txPackets.valueAt(i) * leftOverTxTimeMs) / totalTxPackets;
                if (DEBUG_ENERGY) {
                    Slog.d(TAG, "  TxTime for UID " + uid.getUid() + ": " + myTxTimeMs + " ms");
                }
                uid.getOrCreateWifiControllerActivityLocked().getTxTimeCounters()[0]
                        .addCountLocked(myTxTimeMs);
            }

            // Distribute the remaining Rx power appropriately between all apps that received
            // packets.
            for (int i = 0; i < rxPackets.size(); i++) {
                final Uid uid = getUidStatsLocked(rxPackets.keyAt(i));
                final long myRxTimeMs = (rxPackets.valueAt(i) * leftOverRxTimeMs) / totalRxPackets;
                if (DEBUG_ENERGY) {
                    Slog.d(TAG, "  RxTime for UID " + uid.getUid() + ": " + myRxTimeMs + " ms");
                }
                uid.getOrCreateWifiControllerActivityLocked().getRxTimeCounter()
                        .addCountLocked(myRxTimeMs);
            }

            // Any left over power use will be picked up by the WiFi category in BatteryStatsHelper.

            // Update WiFi controller stats.
            mWifiActivity.getRxTimeCounter().addCountLocked(info.getControllerRxTimeMillis());
            mWifiActivity.getTxTimeCounters()[0].addCountLocked(info.getControllerTxTimeMillis());
            mWifiActivity.getIdleTimeCounter().addCountLocked(info.getControllerIdleTimeMillis());

            // POWER_WIFI_CONTROLLER_OPERATING_VOLTAGE is measured in mV, so convert to V.
            final double opVolt = mPowerProfile.getAveragePower(
                    PowerProfile.POWER_WIFI_CONTROLLER_OPERATING_VOLTAGE) / 1000.0;
            if (opVolt != 0) {
                // We store the power drain as mAms.
                mWifiActivity.getPowerCounter().addCountLocked(
                        (long)(info.getControllerEnergyUsed() / opVolt));
            }
        }
    }

    /**
     * Distribute Cell radio energy info and network traffic to apps.
     */
    public void updateMobileRadioStateLocked(final long elapsedRealtimeMs,
                                             final ModemActivityInfo activityInfo) {
        if (DEBUG_ENERGY) {
            Slog.d(TAG, "Updating mobile radio stats with " + activityInfo);
        }

        NetworkStats delta = null;
        try {
            if (!ArrayUtils.isEmpty(mMobileIfaces)) {
                delta = getNetworkStatsDeltaLocked(mMobileIfaces, mMobileNetworkStats);
            }
        } catch (IOException e) {
            Slog.wtf(TAG, "Failed to get mobile network stats", e);
            return;
        }

        if (!mOnBatteryInternal) {
            return;
        }

        long radioTime = mMobileRadioActivePerAppTimer.getTimeSinceMarkLocked(
                elapsedRealtimeMs * 1000);
        mMobileRadioActivePerAppTimer.setMark(elapsedRealtimeMs);

        long totalRxPackets = 0;
        long totalTxPackets = 0;
        if (delta != null) {
            final int size = delta.size();
            for (int i = 0; i < size; i++) {
                final NetworkStats.Entry entry = delta.getValues(i, mTmpNetworkStatsEntry);
                if (entry.rxPackets == 0 && entry.txPackets == 0) {
                    continue;
                }

                if (DEBUG_ENERGY) {
                    Slog.d(TAG, "Mobile uid " + entry.uid + ": delta rx=" + entry.rxBytes
                            + " tx=" + entry.txBytes + " rxPackets=" + entry.rxPackets
                            + " txPackets=" + entry.txPackets);
                }

                totalRxPackets += entry.rxPackets;
                totalTxPackets += entry.txPackets;

                final Uid u = getUidStatsLocked(mapUid(entry.uid));
                u.noteNetworkActivityLocked(NETWORK_MOBILE_RX_DATA, entry.rxBytes, entry.rxPackets);
                u.noteNetworkActivityLocked(NETWORK_MOBILE_TX_DATA, entry.txBytes, entry.txPackets);

                mNetworkByteActivityCounters[NETWORK_MOBILE_RX_DATA].addCountLocked(
                        entry.rxBytes);
                mNetworkByteActivityCounters[NETWORK_MOBILE_TX_DATA].addCountLocked(
                        entry.txBytes);
                mNetworkPacketActivityCounters[NETWORK_MOBILE_RX_DATA].addCountLocked(
                        entry.rxPackets);
                mNetworkPacketActivityCounters[NETWORK_MOBILE_TX_DATA].addCountLocked(
                        entry.txPackets);
            }

            // Now distribute proportional blame to the apps that did networking.
            long totalPackets = totalRxPackets + totalTxPackets;
            if (totalPackets > 0) {
                for (int i = 0; i < size; i++) {
                    final NetworkStats.Entry entry = delta.getValues(i, mTmpNetworkStatsEntry);
                    if (entry.rxPackets == 0 && entry.txPackets == 0) {
                        continue;
                    }

                    final Uid u = getUidStatsLocked(mapUid(entry.uid));

                    // Distribute total radio active time in to this app.
                    final long appPackets = entry.rxPackets + entry.txPackets;
                    final long appRadioTime = (radioTime * appPackets) / totalPackets;
                    u.noteMobileRadioActiveTimeLocked(appRadioTime);

                    // Remove this app from the totals, so that we don't lose any time
                    // due to rounding.
                    radioTime -= appRadioTime;
                    totalPackets -= appPackets;

                    if (activityInfo != null) {
                        ControllerActivityCounterImpl activityCounter =
                                u.getOrCreateModemControllerActivityLocked();
                        if (totalRxPackets > 0 && entry.rxPackets > 0) {
                            final long rxMs = (entry.rxPackets * activityInfo.getRxTimeMillis())
                                    / totalRxPackets;
                            activityCounter.getRxTimeCounter().addCountLocked(rxMs);
                        }

                        if (totalTxPackets > 0 && entry.txPackets > 0) {
                            for (int lvl = 0; lvl < ModemActivityInfo.TX_POWER_LEVELS; lvl++) {
                                long txMs = entry.txPackets * activityInfo.getTxTimeMillis()[lvl];
                                txMs /= totalTxPackets;
                                activityCounter.getTxTimeCounters()[lvl].addCountLocked(txMs);
                            }
                        }
                    }
                }
            }

            if (radioTime > 0) {
                // Whoops, there is some radio time we can't blame on an app!
                mMobileRadioActiveUnknownTime.addCountLocked(radioTime);
                mMobileRadioActiveUnknownCount.addCountLocked(1);
            }
        }

        if (activityInfo != null) {
            mHasModemReporting = true;
            mModemActivity.getIdleTimeCounter().addCountLocked(activityInfo.getIdleTimeMillis());
            mModemActivity.getRxTimeCounter().addCountLocked(activityInfo.getRxTimeMillis());
            for (int lvl = 0; lvl < ModemActivityInfo.TX_POWER_LEVELS; lvl++) {
                mModemActivity.getTxTimeCounters()[lvl]
                        .addCountLocked(activityInfo.getTxTimeMillis()[lvl]);
            }

            // POWER_MODEM_CONTROLLER_OPERATING_VOLTAGE is measured in mV, so convert to V.
            final double opVolt = mPowerProfile.getAveragePower(
                    PowerProfile.POWER_MODEM_CONTROLLER_OPERATING_VOLTAGE) / 1000.0;
            if (opVolt != 0) {
                // We store the power drain as mAms.
                mModemActivity.getPowerCounter().addCountLocked(
                        (long) (activityInfo.getEnergyUsed() / opVolt));
            }
        }
    }

    /**
     * Distribute Bluetooth energy info and network traffic to apps.
     * @param info The energy information from the bluetooth controller.
     */
    public void updateBluetoothStateLocked(@Nullable final BluetoothActivityEnergyInfo info) {
        if (DEBUG_ENERGY) {
            Slog.d(TAG, "Updating bluetooth stats: " + info);
        }

        if (info == null || !mOnBatteryInternal) {
            return;
        }

        mHasBluetoothReporting = true;

        final long elapsedRealtimeMs = SystemClock.elapsedRealtime();
        final long rxTimeMs = info.getControllerRxTimeMillis();
        final long txTimeMs = info.getControllerTxTimeMillis();

        if (DEBUG_ENERGY) {
            Slog.d(TAG, "------ BEGIN BLE power blaming ------");
            Slog.d(TAG, "  Tx Time:    " + txTimeMs + " ms");
            Slog.d(TAG, "  Rx Time:    " + rxTimeMs + " ms");
            Slog.d(TAG, "  Idle Time:  " + info.getControllerIdleTimeMillis() + " ms");
        }

        long totalScanTimeMs = 0;

        final int uidCount = mUidStats.size();
        for (int i = 0; i < uidCount; i++) {
            final Uid u = mUidStats.valueAt(i);
            if (u.mBluetoothScanTimer == null) {
                continue;
            }

            totalScanTimeMs += u.mBluetoothScanTimer.getTimeSinceMarkLocked(
                    elapsedRealtimeMs * 1000) / 1000;
        }

        final boolean normalizeScanRxTime = (totalScanTimeMs > rxTimeMs);
        final boolean normalizeScanTxTime = (totalScanTimeMs > txTimeMs);

        if (DEBUG_ENERGY) {
            Slog.d(TAG, "Normalizing scan power for RX=" + normalizeScanRxTime
                    + " TX=" + normalizeScanTxTime);
        }

        long leftOverRxTimeMs = rxTimeMs;
        long leftOverTxTimeMs = txTimeMs;

        for (int i = 0; i < uidCount; i++) {
            final Uid u = mUidStats.valueAt(i);
            if (u.mBluetoothScanTimer == null) {
                continue;
            }

            long scanTimeSinceMarkMs = u.mBluetoothScanTimer.getTimeSinceMarkLocked(
                    elapsedRealtimeMs * 1000) / 1000;
            if (scanTimeSinceMarkMs > 0) {
                // Set the new mark so that next time we get new data since this point.
                u.mBluetoothScanTimer.setMark(elapsedRealtimeMs);

                long scanTimeRxSinceMarkMs = scanTimeSinceMarkMs;
                long scanTimeTxSinceMarkMs = scanTimeSinceMarkMs;

                if (normalizeScanRxTime) {
                    // Scan time is longer than the total rx time in the controller,
                    // so distribute the scan time proportionately. This means regular traffic
                    // will not blamed, but scans are more expensive anyways.
                    scanTimeRxSinceMarkMs = (rxTimeMs * scanTimeRxSinceMarkMs) / totalScanTimeMs;
                }

                if (normalizeScanTxTime) {
                    // Scan time is longer than the total tx time in the controller,
                    // so distribute the scan time proportionately. This means regular traffic
                    // will not blamed, but scans are more expensive anyways.
                    scanTimeTxSinceMarkMs = (txTimeMs * scanTimeTxSinceMarkMs) / totalScanTimeMs;
                }

                final ControllerActivityCounterImpl counter =
                        u.getOrCreateBluetoothControllerActivityLocked();
                counter.getRxTimeCounter().addCountLocked(scanTimeRxSinceMarkMs);
                counter.getTxTimeCounters()[0].addCountLocked(scanTimeTxSinceMarkMs);

                leftOverRxTimeMs -= scanTimeRxSinceMarkMs;
                leftOverTxTimeMs -= scanTimeTxSinceMarkMs;
            }
        }

        if (DEBUG_ENERGY) {
            Slog.d(TAG, "Left over time for traffic RX=" + leftOverRxTimeMs
                    + " TX=" + leftOverTxTimeMs);
        }

        //
        // Now distribute blame to apps that did bluetooth traffic.
        //

        long totalTxBytes = 0;
        long totalRxBytes = 0;

        final UidTraffic[] uidTraffic = info.getUidTraffic();
        final int numUids = uidTraffic != null ? uidTraffic.length : 0;
        for (int i = 0; i < numUids; i++) {
            final UidTraffic traffic = uidTraffic[i];

            // Add to the global counters.
            mNetworkByteActivityCounters[NETWORK_BT_RX_DATA].addCountLocked(
                    traffic.getRxBytes());
            mNetworkByteActivityCounters[NETWORK_BT_TX_DATA].addCountLocked(
                    traffic.getTxBytes());

            // Add to the UID counters.
            final Uid u = getUidStatsLocked(mapUid(traffic.getUid()));
            u.noteNetworkActivityLocked(NETWORK_BT_RX_DATA, traffic.getRxBytes(), 0);
            u.noteNetworkActivityLocked(NETWORK_BT_TX_DATA, traffic.getTxBytes(), 0);

            // Calculate the total traffic.
            totalTxBytes += traffic.getTxBytes();
            totalRxBytes += traffic.getRxBytes();
        }

        if ((totalTxBytes != 0 || totalRxBytes != 0) &&
                (leftOverRxTimeMs != 0 || leftOverTxTimeMs != 0)) {
            for (int i = 0; i < numUids; i++) {
                final UidTraffic traffic = uidTraffic[i];

                final Uid u = getUidStatsLocked(mapUid(traffic.getUid()));
                final ControllerActivityCounterImpl counter =
                        u.getOrCreateBluetoothControllerActivityLocked();

                if (totalRxBytes > 0 && traffic.getRxBytes() > 0) {
                    final long timeRxMs = (leftOverRxTimeMs * traffic.getRxBytes()) / totalRxBytes;

                    if (DEBUG_ENERGY) {
                        Slog.d(TAG, "UID=" + traffic.getUid() + " rx_bytes=" + traffic.getRxBytes()
                                + " rx_time=" + timeRxMs);
                    }
                    counter.getRxTimeCounter().addCountLocked(timeRxMs);
                    leftOverRxTimeMs -= timeRxMs;
                }

                if (totalTxBytes > 0 && traffic.getTxBytes() > 0) {
                    final long timeTxMs = (leftOverTxTimeMs * traffic.getTxBytes()) / totalTxBytes;

                    if (DEBUG_ENERGY) {
                        Slog.d(TAG, "UID=" + traffic.getUid() + " tx_bytes=" + traffic.getTxBytes()
                                + " tx_time=" + timeTxMs);
                    }

                    counter.getTxTimeCounters()[0].addCountLocked(timeTxMs);
                    leftOverTxTimeMs -= timeTxMs;
                }
            }
        }

        mBluetoothActivity.getRxTimeCounter().addCountLocked(
                info.getControllerRxTimeMillis());
        mBluetoothActivity.getTxTimeCounters()[0].addCountLocked(
                info.getControllerTxTimeMillis());
        mBluetoothActivity.getIdleTimeCounter().addCountLocked(
                info.getControllerIdleTimeMillis());

        // POWER_BLUETOOTH_CONTROLLER_OPERATING_VOLTAGE is measured in mV, so convert to V.
        final double opVolt = mPowerProfile.getAveragePower(
                PowerProfile.POWER_BLUETOOTH_CONTROLLER_OPERATING_VOLTAGE) / 1000.0;
        if (opVolt != 0) {
            // We store the power drain as mAms.
            mBluetoothActivity.getPowerCounter().addCountLocked(
                    (long) (info.getControllerEnergyUsed() / opVolt));
        }
    }

    /**
     * Read and distribute kernel wake lock use across apps.
     */
    public void updateKernelWakelocksLocked() {
        final KernelWakelockStats wakelockStats = mKernelWakelockReader.readKernelWakelockStats(
                mTmpWakelockStats);
        if (wakelockStats == null) {
            // Not crashing might make board bringup easier.
            Slog.w(TAG, "Couldn't get kernel wake lock stats");
            return;
        }

        for (Map.Entry<String, KernelWakelockStats.Entry> ent : wakelockStats.entrySet()) {
            String name = ent.getKey();
            KernelWakelockStats.Entry kws = ent.getValue();

            SamplingTimer kwlt = mKernelWakelockStats.get(name);
            if (kwlt == null) {
                kwlt = new SamplingTimer(mClocks, mOnBatteryScreenOffTimeBase);
                mKernelWakelockStats.put(name, kwlt);
            }

            kwlt.update(kws.mTotalTime, kws.mCount);
            kwlt.setUpdateVersion(kws.mVersion);
        }

        int numWakelocksSetStale = 0;
        // Set timers to stale if they didn't appear in /d/wakeup_sources (or /proc/wakelocks)
        // this time.
        for (Map.Entry<String, SamplingTimer> ent : mKernelWakelockStats.entrySet()) {
            SamplingTimer st = ent.getValue();
            if (st.getUpdateVersion() != wakelockStats.kernelWakelockVersion) {
                st.endSample();
                numWakelocksSetStale++;
            }
        }

        // Record whether we've seen a non-zero time (for debugging b/22716723).
        if (wakelockStats.isEmpty()) {
            Slog.wtf(TAG, "All kernel wakelocks had time of zero");
        }

        if (numWakelocksSetStale == mKernelWakelockStats.size()) {
            Slog.wtf(TAG, "All kernel wakelocks were set stale. new version=" +
                    wakelockStats.kernelWakelockVersion);
        }
    }

    // We use an anonymous class to access these variables,
    // so they can't live on the stack or they'd have to be
    // final MutableLong objects (more allocations).
    // Used in updateCpuTimeLocked().
    long mTempTotalCpuUserTimeUs;
    long mTempTotalCpuSystemTimeUs;

    /**
     * Read and distribute CPU usage across apps. If their are partial wakelocks being held
     * and we are on battery with screen off, we give more of the cpu time to those apps holding
     * wakelocks. If the screen is on, we just assign the actual cpu time an app used.
     */
    public void updateCpuTimeLocked() {
        if (mPowerProfile == null) {
            return;
        }

        if (DEBUG_ENERGY_CPU) {
            Slog.d(TAG, "!Cpu updating!");
        }

        // Holding a wakelock costs more than just using the cpu.
        // Currently, we assign only half the cpu time to an app that is running but
        // not holding a wakelock. The apps holding wakelocks get the rest of the blame.
        // If no app is holding a wakelock, then the distribution is normal.
        final int wakelockWeight = 50;

        // Read the time spent for each cluster at various cpu frequencies.
        final long[][] clusterSpeeds = new long[mKernelCpuSpeedReaders.length][];
        for (int cluster = 0; cluster < mKernelCpuSpeedReaders.length; cluster++) {
            clusterSpeeds[cluster] = mKernelCpuSpeedReaders[cluster].readDelta();
        }

        int numWakelocks = 0;

        // Calculate how many wakelocks we have to distribute amongst. The system is excluded.
        // Only distribute cpu power to wakelocks if the screen is off and we're on battery.
        final int numPartialTimers = mPartialTimers.size();
        if (mOnBatteryScreenOffTimeBase.isRunning()) {
            for (int i = 0; i < numPartialTimers; i++) {
                final StopwatchTimer timer = mPartialTimers.get(i);
                if (timer.mInList && timer.mUid != null && timer.mUid.mUid != Process.SYSTEM_UID) {
                    // Since the collection and blaming of wakelocks can be scheduled to run after
                    // some delay, the mPartialTimers list may have new entries. We can't blame
                    // the newly added timer for past cpu time, so we only consider timers that
                    // were present for one round of collection. Once a timer has gone through
                    // a round of collection, its mInList field is set to true.
                    numWakelocks++;
                }
            }
        }

        final int numWakelocksF = numWakelocks;
        mTempTotalCpuUserTimeUs = 0;
        mTempTotalCpuSystemTimeUs = 0;

        // Read the CPU data for each UID. This will internally generate a snapshot so next time
        // we read, we get a delta. If we are to distribute the cpu time, then do so. Otherwise
        // we just ignore the data.
        final long startTimeMs = mClocks.elapsedRealtime();
        mKernelUidCpuTimeReader.readDelta(!mOnBatteryInternal ? null :
                new KernelUidCpuTimeReader.Callback() {
                    @Override
                    public void onUidCpuTime(int uid, long userTimeUs, long systemTimeUs,
                                             long powerMaUs) {
                        final Uid u = getUidStatsLocked(mapUid(uid));

                        // Accumulate the total system and user time.
                        mTempTotalCpuUserTimeUs += userTimeUs;
                        mTempTotalCpuSystemTimeUs += systemTimeUs;

                        StringBuilder sb = null;
                        if (DEBUG_ENERGY_CPU) {
                            sb = new StringBuilder();
                            sb.append("  got time for uid=").append(u.mUid).append(": u=");
                            TimeUtils.formatDuration(userTimeUs / 1000, sb);
                            sb.append(" s=");
                            TimeUtils.formatDuration(systemTimeUs / 1000, sb);
                            sb.append(" p=").append(powerMaUs / 1000).append("mAms\n");
                        }

                        if (numWakelocksF > 0) {
                            // We have wakelocks being held, so only give a portion of the
                            // time to the process. The rest will be distributed among wakelock
                            // holders.
                            userTimeUs = (userTimeUs * wakelockWeight) / 100;
                            systemTimeUs = (systemTimeUs * wakelockWeight) / 100;
                        }

                        if (sb != null) {
                            sb.append("  adding to uid=").append(u.mUid).append(": u=");
                            TimeUtils.formatDuration(userTimeUs / 1000, sb);
                            sb.append(" s=");
                            TimeUtils.formatDuration(systemTimeUs / 1000, sb);
                            sb.append(" p=").append(powerMaUs / 1000).append("mAms");
                            Slog.d(TAG, sb.toString());
                        }

                        u.mUserCpuTime.addCountLocked(userTimeUs);
                        u.mSystemCpuTime.addCountLocked(systemTimeUs);
                        u.mCpuPower.addCountLocked(powerMaUs);

                        // Add the cpu speeds to this UID. These are used as a ratio
                        // for computing the power this UID used.
                        final int numClusters = mPowerProfile.getNumCpuClusters();
                        if (u.mCpuClusterSpeed == null || u.mCpuClusterSpeed.length !=
                                numClusters) {
                            u.mCpuClusterSpeed = new LongSamplingCounter[numClusters][];
                        }

                        for (int cluster = 0; cluster < clusterSpeeds.length; cluster++) {
                            final int speedsInCluster = mPowerProfile.getNumSpeedStepsInCpuCluster(
                                    cluster);
                            if (u.mCpuClusterSpeed[cluster] == null || speedsInCluster !=
                                    u.mCpuClusterSpeed[cluster].length) {
                                u.mCpuClusterSpeed[cluster] =
                                        new LongSamplingCounter[speedsInCluster];
                            }

                            final LongSamplingCounter[] cpuSpeeds = u.mCpuClusterSpeed[cluster];
                            for (int speed = 0; speed < clusterSpeeds[cluster].length; speed++) {
                                if (cpuSpeeds[speed] == null) {
                                    cpuSpeeds[speed] = new LongSamplingCounter(mOnBatteryTimeBase);
                                }
                                cpuSpeeds[speed].addCountLocked(clusterSpeeds[cluster][speed]);
                            }
                        }
                    }
                });

        if (DEBUG_ENERGY_CPU) {
            Slog.d(TAG, "Reading cpu stats took " + (mClocks.elapsedRealtime() - startTimeMs) +
                    " ms");
        }

        if (mOnBatteryInternal && numWakelocks > 0) {
            // Distribute a portion of the total cpu time to wakelock holders.
            mTempTotalCpuUserTimeUs = (mTempTotalCpuUserTimeUs * (100 - wakelockWeight)) / 100;
            mTempTotalCpuSystemTimeUs =
                    (mTempTotalCpuSystemTimeUs * (100 - wakelockWeight)) / 100;

            for (int i = 0; i < numPartialTimers; i++) {
                final StopwatchTimer timer = mPartialTimers.get(i);

                // The system does not share any blame, as it is usually holding the wakelock
                // on behalf of an app.
                if (timer.mInList && timer.mUid != null && timer.mUid.mUid != Process.SYSTEM_UID) {
                    int userTimeUs = (int) (mTempTotalCpuUserTimeUs / numWakelocks);
                    int systemTimeUs = (int) (mTempTotalCpuSystemTimeUs / numWakelocks);

                    if (DEBUG_ENERGY_CPU) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("  Distributing wakelock uid=").append(timer.mUid.mUid)
                                .append(": u=");
                        TimeUtils.formatDuration(userTimeUs / 1000, sb);
                        sb.append(" s=");
                        TimeUtils.formatDuration(systemTimeUs / 1000, sb);
                        Slog.d(TAG, sb.toString());
                    }

                    timer.mUid.mUserCpuTime.addCountLocked(userTimeUs);
                    timer.mUid.mSystemCpuTime.addCountLocked(systemTimeUs);

                    final Uid.Proc proc = timer.mUid.getProcessStatsLocked("*wakelock*");
                    proc.addCpuTimeLocked(userTimeUs / 1000, systemTimeUs / 1000);

                    mTempTotalCpuUserTimeUs -= userTimeUs;
                    mTempTotalCpuSystemTimeUs -= systemTimeUs;
                    numWakelocks--;
                }
            }

            if (mTempTotalCpuUserTimeUs > 0 || mTempTotalCpuSystemTimeUs > 0) {
                // Anything left over is given to the system.
                if (DEBUG_ENERGY_CPU) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("  Distributing lost time to system: u=");
                    TimeUtils.formatDuration(mTempTotalCpuUserTimeUs / 1000, sb);
                    sb.append(" s=");
                    TimeUtils.formatDuration(mTempTotalCpuSystemTimeUs / 1000, sb);
                    Slog.d(TAG, sb.toString());
                }

                final Uid u = getUidStatsLocked(Process.SYSTEM_UID);
                u.mUserCpuTime.addCountLocked(mTempTotalCpuUserTimeUs);
                u.mSystemCpuTime.addCountLocked(mTempTotalCpuSystemTimeUs);

                final Uid.Proc proc = u.getProcessStatsLocked("*lost*");
                proc.addCpuTimeLocked((int) mTempTotalCpuUserTimeUs / 1000,
                        (int) mTempTotalCpuSystemTimeUs / 1000);
            }
        }

        // See if there is a difference in wakelocks between this collection and the last
        // collection.
        if (ArrayUtils.referenceEquals(mPartialTimers, mLastPartialTimers)) {
            // No difference, so each timer is now considered for the next collection.
            for (int i = 0; i < numPartialTimers; i++) {
                mPartialTimers.get(i).mInList = true;
            }
        } else {
            // The lists are different, meaning we added (or removed a timer) since the last
            // collection.
            final int numLastPartialTimers = mLastPartialTimers.size();
            for (int i = 0; i < numLastPartialTimers; i++) {
                mLastPartialTimers.get(i).mInList = false;
            }
            mLastPartialTimers.clear();

            // Mark the current timers as gone through a collection.
            for (int i = 0; i < numPartialTimers; i++) {
                final StopwatchTimer timer = mPartialTimers.get(i);
                timer.mInList = true;
                mLastPartialTimers.add(timer);
            }
        }
    }

    boolean setChargingLocked(boolean charging) {
        if (mCharging != charging) {
            mCharging = charging;
            if (charging) {
                mHistoryCur.states2 |= HistoryItem.STATE2_CHARGING_FLAG;
            } else {
                mHistoryCur.states2 &= ~HistoryItem.STATE2_CHARGING_FLAG;
            }
            mHandler.sendEmptyMessage(MSG_REPORT_CHARGING);
            return true;
        }
        return false;
    }

    void setOnBatteryLocked(final long mSecRealtime, final long mSecUptime, final boolean onBattery,
            final int oldStatus, final int level, final int chargeUAh) {
        boolean doWrite = false;
        Message m = mHandler.obtainMessage(MSG_REPORT_POWER_CHANGE);
        m.arg1 = onBattery ? 1 : 0;
        mHandler.sendMessage(m);

        final long uptime = mSecUptime * 1000;
        final long realtime = mSecRealtime * 1000;
        final boolean screenOn = mScreenState == Display.STATE_ON;
        if (onBattery) {
            // We will reset our status if we are unplugging after the
            // battery was last full, or the level is at 100, or
            // we have gone through a significant charge (from a very low
            // level to a now very high level).
            boolean reset = false;
            if (!mNoAutoReset && (oldStatus == BatteryManager.BATTERY_STATUS_FULL
                    || level >= 90
                    || (mDischargeCurrentLevel < 20 && level >= 80)
                    || (getHighDischargeAmountSinceCharge() >= 200
                            && mHistoryBuffer.dataSize() >= MAX_HISTORY_BUFFER))) {
                Slog.i(TAG, "Resetting battery stats: level=" + level + " status=" + oldStatus
                        + " dischargeLevel=" + mDischargeCurrentLevel
                        + " lowAmount=" + getLowDischargeAmountSinceCharge()
                        + " highAmount=" + getHighDischargeAmountSinceCharge());
                // Before we write, collect a snapshot of the final aggregated
                // stats to be reported in the next checkin.  Only do this if we have
                // a sufficient amount of data to make it interesting.
                if (getLowDischargeAmountSinceCharge() >= 20) {
                    final Parcel parcel = Parcel.obtain();
                    writeSummaryToParcel(parcel, true);
                    BackgroundThread.getHandler().post(new Runnable() {
                        @Override public void run() {
                            synchronized (mCheckinFile) {
                                FileOutputStream stream = null;
                                try {
                                    stream = mCheckinFile.startWrite();
                                    stream.write(parcel.marshall());
                                    stream.flush();
                                    FileUtils.sync(stream);
                                    stream.close();
                                    mCheckinFile.finishWrite(stream);
                                } catch (IOException e) {
                                    Slog.w("BatteryStats",
                                            "Error writing checkin battery statistics", e);
                                    mCheckinFile.failWrite(stream);
                                } finally {
                                    parcel.recycle();
                                }
                            }
                        }
                    });
                }
                doWrite = true;
                resetAllStatsLocked();
                if (chargeUAh > 0) {
                    // Only use the reported coulomb charge value if it is supported and reported.
                    mEstimatedBatteryCapacity = (int) ((level / 100.0) * (chargeUAh / 1000));
                }
                mDischargeStartLevel = level;
                reset = true;
                mDischargeStepTracker.init();
            }
            if (mCharging) {
                setChargingLocked(false);
            }
            mLastChargingStateLevel = level;
            mOnBattery = mOnBatteryInternal = true;
            mLastDischargeStepLevel = level;
            mMinDischargeStepLevel = level;
            mDischargeStepTracker.clearTime();
            mDailyDischargeStepTracker.clearTime();
            mInitStepMode = mCurStepMode;
            mModStepMode = 0;
            pullPendingStateUpdatesLocked();
            mHistoryCur.batteryLevel = (byte)level;
            mHistoryCur.states &= ~HistoryItem.STATE_BATTERY_PLUGGED_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Battery unplugged to: "
                    + Integer.toHexString(mHistoryCur.states));
            if (reset) {
                mRecordingHistory = true;
                startRecordingHistory(mSecRealtime, mSecUptime, reset);
            }
            addHistoryRecordLocked(mSecRealtime, mSecUptime);
            mDischargeCurrentLevel = mDischargeUnplugLevel = level;
            if (screenOn) {
                mDischargeScreenOnUnplugLevel = level;
                mDischargeScreenOffUnplugLevel = 0;
            } else {
                mDischargeScreenOnUnplugLevel = 0;
                mDischargeScreenOffUnplugLevel = level;
            }
            mDischargeAmountScreenOn = 0;
            mDischargeAmountScreenOff = 0;
            updateTimeBasesLocked(true, !screenOn, uptime, realtime);
        } else {
            mLastChargingStateLevel = level;
            mOnBattery = mOnBatteryInternal = false;
            pullPendingStateUpdatesLocked();
            mHistoryCur.batteryLevel = (byte)level;
            mHistoryCur.states |= HistoryItem.STATE_BATTERY_PLUGGED_FLAG;
            if (DEBUG_HISTORY) Slog.v(TAG, "Battery plugged to: "
                    + Integer.toHexString(mHistoryCur.states));
            addHistoryRecordLocked(mSecRealtime, mSecUptime);
            mDischargeCurrentLevel = mDischargePlugLevel = level;
            if (level < mDischargeUnplugLevel) {
                mLowDischargeAmountSinceCharge += mDischargeUnplugLevel-level-1;
                mHighDischargeAmountSinceCharge += mDischargeUnplugLevel-level;
            }
            updateDischargeScreenLevelsLocked(screenOn, screenOn);
            updateTimeBasesLocked(false, !screenOn, uptime, realtime);
            mChargeStepTracker.init();
            mLastChargeStepLevel = level;
            mMaxChargeStepLevel = level;
            mInitStepMode = mCurStepMode;
            mModStepMode = 0;
        }
        if (doWrite || (mLastWriteTime + (60 * 1000)) < mSecRealtime) {
            if (mFile != null) {
                writeAsyncLocked();
            }
        }
    }

    private void startRecordingHistory(final long elapsedRealtimeMs, final long uptimeMs,
            boolean reset) {
        mRecordingHistory = true;
        mHistoryCur.currentTime = System.currentTimeMillis();
        addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs,
                reset ? HistoryItem.CMD_RESET : HistoryItem.CMD_CURRENT_TIME,
                mHistoryCur);
        mHistoryCur.currentTime = 0;
        if (reset) {
            initActiveHistoryEventsLocked(elapsedRealtimeMs, uptimeMs);
        }
    }

    private void recordCurrentTimeChangeLocked(final long currentTime, final long elapsedRealtimeMs,
            final long uptimeMs) {
        if (mRecordingHistory) {
            mHistoryCur.currentTime = currentTime;
            addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_CURRENT_TIME,
                    mHistoryCur);
            mHistoryCur.currentTime = 0;
        }
    }

    private void recordShutdownLocked(final long elapsedRealtimeMs, final long uptimeMs) {
        if (mRecordingHistory) {
            mHistoryCur.currentTime = System.currentTimeMillis();
            addHistoryBufferLocked(elapsedRealtimeMs, uptimeMs, HistoryItem.CMD_SHUTDOWN,
                    mHistoryCur);
            mHistoryCur.currentTime = 0;
        }
    }

    private void scheduleSyncExternalStatsLocked(String reason, int updateFlags) {
        if (mExternalSync != null) {
            mExternalSync.scheduleSync(reason, updateFlags);
        }
    }

    // This should probably be exposed in the API, though it's not critical
    public static final int BATTERY_PLUGGED_NONE = 0;

    public void setBatteryStateLocked(int status, int health, int plugType, int level,
            int temp, int volt, int chargeUAh) {
        final boolean onBattery = plugType == BATTERY_PLUGGED_NONE;
        final long uptime = mClocks.uptimeMillis();
        final long elapsedRealtime = mClocks.elapsedRealtime();
        if (!mHaveBatteryLevel) {
            mHaveBatteryLevel = true;
            // We start out assuming that the device is plugged in (not
            // on battery).  If our first report is now that we are indeed
            // plugged in, then twiddle our state to correctly reflect that
            // since we won't be going through the full setOnBattery().
            if (onBattery == mOnBattery) {
                if (onBattery) {
                    mHistoryCur.states &= ~HistoryItem.STATE_BATTERY_PLUGGED_FLAG;
                } else {
                    mHistoryCur.states |= HistoryItem.STATE_BATTERY_PLUGGED_FLAG;
                }
            }
            // Always start out assuming charging, that will be updated later.
            mHistoryCur.states2 |= HistoryItem.STATE2_CHARGING_FLAG;
            mHistoryCur.batteryStatus = (byte)status;
            mHistoryCur.batteryLevel = (byte)level;
            mHistoryCur.batteryChargeUAh = chargeUAh;
            mMaxChargeStepLevel = mMinDischargeStepLevel =
                    mLastChargeStepLevel = mLastDischargeStepLevel = level;
            mLastChargingStateLevel = level;
        } else if (mCurrentBatteryLevel != level || mOnBattery != onBattery) {
            recordDailyStatsIfNeededLocked(level >= 100 && onBattery);
        }
        int oldStatus = mHistoryCur.batteryStatus;
        if (onBattery) {
            mDischargeCurrentLevel = level;
            if (!mRecordingHistory) {
                mRecordingHistory = true;
                startRecordingHistory(elapsedRealtime, uptime, true);
            }
        } else if (level < 96) {
            if (!mRecordingHistory) {
                mRecordingHistory = true;
                startRecordingHistory(elapsedRealtime, uptime, true);
            }
        }
        mCurrentBatteryLevel = level;
        if (mDischargePlugLevel < 0) {
            mDischargePlugLevel = level;
        }

        if (onBattery != mOnBattery) {
            mHistoryCur.batteryLevel = (byte)level;
            mHistoryCur.batteryStatus = (byte)status;
            mHistoryCur.batteryHealth = (byte)health;
            mHistoryCur.batteryPlugType = (byte)plugType;
            mHistoryCur.batteryTemperature = (short)temp;
            mHistoryCur.batteryVoltage = (char)volt;
            if (chargeUAh < mHistoryCur.batteryChargeUAh) {
                // Only record discharges
                final long chargeDiff = mHistoryCur.batteryChargeUAh - chargeUAh;
                mDischargeCounter.addCountLocked(chargeDiff);
                mDischargeScreenOffCounter.addCountLocked(chargeDiff);
            }
            mHistoryCur.batteryChargeUAh = chargeUAh;
            setOnBatteryLocked(elapsedRealtime, uptime, onBattery, oldStatus, level, chargeUAh);
        } else {
            boolean changed = false;
            if (mHistoryCur.batteryLevel != level) {
                mHistoryCur.batteryLevel = (byte)level;
                changed = true;

                // TODO(adamlesinski): Schedule the creation of a HistoryStepDetails record
                // which will pull external stats.
                scheduleSyncExternalStatsLocked("battery-level", ExternalStatsSync.UPDATE_ALL);
            }
            if (mHistoryCur.batteryStatus != status) {
                mHistoryCur.batteryStatus = (byte)status;
                changed = true;
            }
            if (mHistoryCur.batteryHealth != health) {
                mHistoryCur.batteryHealth = (byte)health;
                changed = true;
            }
            if (mHistoryCur.batteryPlugType != plugType) {
                mHistoryCur.batteryPlugType = (byte)plugType;
                changed = true;
            }
            if (temp >= (mHistoryCur.batteryTemperature+10)
                    || temp <= (mHistoryCur.batteryTemperature-10)) {
                mHistoryCur.batteryTemperature = (short)temp;
                changed = true;
            }
            if (volt > (mHistoryCur.batteryVoltage+20)
                    || volt < (mHistoryCur.batteryVoltage-20)) {
                mHistoryCur.batteryVoltage = (char)volt;
                changed = true;
            }
            if (chargeUAh >= (mHistoryCur.batteryChargeUAh+10)
                    || chargeUAh <= (mHistoryCur.batteryChargeUAh-10)) {
                if (chargeUAh < mHistoryCur.batteryChargeUAh) {
                    // Only record discharges
                    final long chargeDiff = mHistoryCur.batteryChargeUAh - chargeUAh;
                    mDischargeCounter.addCountLocked(chargeDiff);
                    mDischargeScreenOffCounter.addCountLocked(chargeDiff);
                }
                mHistoryCur.batteryChargeUAh = chargeUAh;
                changed = true;
            }
            long modeBits = (((long)mInitStepMode) << STEP_LEVEL_INITIAL_MODE_SHIFT)
                    | (((long)mModStepMode) << STEP_LEVEL_MODIFIED_MODE_SHIFT)
                    | (((long)(level&0xff)) << STEP_LEVEL_LEVEL_SHIFT);
            if (onBattery) {
                changed |= setChargingLocked(false);
                if (mLastDischargeStepLevel != level && mMinDischargeStepLevel > level) {
                    mDischargeStepTracker.addLevelSteps(mLastDischargeStepLevel - level,
                            modeBits, elapsedRealtime);
                    mDailyDischargeStepTracker.addLevelSteps(mLastDischargeStepLevel - level,
                            modeBits, elapsedRealtime);
                    mLastDischargeStepLevel = level;
                    mMinDischargeStepLevel = level;
                    mInitStepMode = mCurStepMode;
                    mModStepMode = 0;
                }
            } else {
                if (level >= 90) {
                    // If the battery level is at least 90%, always consider the device to be
                    // charging even if it happens to go down a level.
                    changed |= setChargingLocked(true);
                    mLastChargeStepLevel = level;
                } if (!mCharging) {
                    if (mLastChargeStepLevel < level) {
                        // We have not reporting that we are charging, but the level has now
                        // gone up, so consider the state to be charging.
                        changed |= setChargingLocked(true);
                        mLastChargeStepLevel = level;
                    }
                } else {
                    if (mLastChargeStepLevel > level) {
                        // We had reported that the device was charging, but here we are with
                        // power connected and the level going down.  Looks like the current
                        // power supplied isn't enough, so consider the device to now be
                        // discharging.
                        changed |= setChargingLocked(false);
                        mLastChargeStepLevel = level;
                    }
                }
                if (mLastChargeStepLevel != level && mMaxChargeStepLevel < level) {
                    mChargeStepTracker.addLevelSteps(level - mLastChargeStepLevel,
                            modeBits, elapsedRealtime);
                    mDailyChargeStepTracker.addLevelSteps(level - mLastChargeStepLevel,
                            modeBits, elapsedRealtime);
                    mLastChargeStepLevel = level;
                    mMaxChargeStepLevel = level;
                    mInitStepMode = mCurStepMode;
                    mModStepMode = 0;
                }
            }
            if (changed) {
                addHistoryRecordLocked(elapsedRealtime, uptime);
            }
        }
        if (!onBattery && status == BatteryManager.BATTERY_STATUS_FULL) {
            // We don't record history while we are plugged in and fully charged.
            // The next time we are unplugged, history will be cleared.
            mRecordingHistory = DEBUG;
        }
    }

    public long getAwakeTimeBattery() {
        return computeBatteryUptime(getBatteryUptimeLocked(), STATS_CURRENT);
    }

    public long getAwakeTimePlugged() {
        return (mClocks.uptimeMillis() * 1000) - getAwakeTimeBattery();
    }

    @Override
    public long computeUptime(long curTime, int which) {
        switch (which) {
            case STATS_SINCE_CHARGED: return mUptime + (curTime-mUptimeStart);
            case STATS_CURRENT: return (curTime-mUptimeStart);
            case STATS_SINCE_UNPLUGGED: return (curTime-mOnBatteryTimeBase.getUptimeStart());
        }
        return 0;
    }

    @Override
    public long computeRealtime(long curTime, int which) {
        switch (which) {
            case STATS_SINCE_CHARGED: return mRealtime + (curTime-mRealtimeStart);
            case STATS_CURRENT: return (curTime-mRealtimeStart);
            case STATS_SINCE_UNPLUGGED: return (curTime-mOnBatteryTimeBase.getRealtimeStart());
        }
        return 0;
    }

    @Override
    public long computeBatteryUptime(long curTime, int which) {
        return mOnBatteryTimeBase.computeUptime(curTime, which);
    }

    @Override
    public long computeBatteryRealtime(long curTime, int which) {
        return mOnBatteryTimeBase.computeRealtime(curTime, which);
    }

    @Override
    public long computeBatteryScreenOffUptime(long curTime, int which) {
        return mOnBatteryScreenOffTimeBase.computeUptime(curTime, which);
    }

    @Override
    public long computeBatteryScreenOffRealtime(long curTime, int which) {
        return mOnBatteryScreenOffTimeBase.computeRealtime(curTime, which);
    }

    private long computeTimePerLevel(long[] steps, int numSteps) {
        // For now we'll do a simple average across all steps.
        if (numSteps <= 0) {
            return -1;
        }
        long total = 0;
        for (int i=0; i<numSteps; i++) {
            total += steps[i] & STEP_LEVEL_TIME_MASK;
        }
        return total / numSteps;
        /*
        long[] buckets = new long[numSteps];
        int numBuckets = 0;
        int numToAverage = 4;
        int i = 0;
        while (i < numSteps) {
            long totalTime = 0;
            int num = 0;
            for (int j=0; j<numToAverage && (i+j)<numSteps; j++) {
                totalTime += steps[i+j] & STEP_LEVEL_TIME_MASK;
                num++;
            }
            buckets[numBuckets] = totalTime / num;
            numBuckets++;
            numToAverage *= 2;
            i += num;
        }
        if (numBuckets < 1) {
            return -1;
        }
        long averageTime = buckets[numBuckets-1];
        for (i=numBuckets-2; i>=0; i--) {
            averageTime = (averageTime + buckets[i]) / 2;
        }
        return averageTime;
        */
    }

    @Override
    public long computeBatteryTimeRemaining(long curTime) {
        if (!mOnBattery) {
            return -1;
        }
        /* Simple implementation just looks at the average discharge per level across the
           entire sample period.
        int discharge = (getLowDischargeAmountSinceCharge()+getHighDischargeAmountSinceCharge())/2;
        if (discharge < 2) {
            return -1;
        }
        long duration = computeBatteryRealtime(curTime, STATS_SINCE_CHARGED);
        if (duration < 1000*1000) {
            return -1;
        }
        long usPerLevel = duration/discharge;
        return usPerLevel * mCurrentBatteryLevel;
        */
        if (mDischargeStepTracker.mNumStepDurations < 1) {
            return -1;
        }
        long msPerLevel = mDischargeStepTracker.computeTimePerLevel();
        if (msPerLevel <= 0) {
            return -1;
        }
        return (msPerLevel * mCurrentBatteryLevel) * 1000;
    }

    @Override
    public LevelStepTracker getDischargeLevelStepTracker() {
        return mDischargeStepTracker;
    }

    @Override
    public LevelStepTracker getDailyDischargeLevelStepTracker() {
        return mDailyDischargeStepTracker;
    }

    @Override
    public long computeChargeTimeRemaining(long curTime) {
        if (mOnBattery) {
            // Not yet working.
            return -1;
        }
        /* Broken
        int curLevel = mCurrentBatteryLevel;
        int plugLevel = mDischargePlugLevel;
        if (plugLevel < 0 || curLevel < (plugLevel+1)) {
            return -1;
        }
        long duration = computeBatteryRealtime(curTime, STATS_SINCE_UNPLUGGED);
        if (duration < 1000*1000) {
            return -1;
        }
        long usPerLevel = duration/(curLevel-plugLevel);
        return usPerLevel * (100-curLevel);
        */
        if (mChargeStepTracker.mNumStepDurations < 1) {
            return -1;
        }
        long msPerLevel = mChargeStepTracker.computeTimePerLevel();
        if (msPerLevel <= 0) {
            return -1;
        }
        return (msPerLevel * (100-mCurrentBatteryLevel)) * 1000;
    }

    @Override
    public LevelStepTracker getChargeLevelStepTracker() {
        return mChargeStepTracker;
    }

    @Override
    public LevelStepTracker getDailyChargeLevelStepTracker() {
        return mDailyChargeStepTracker;
    }

    @Override
    public ArrayList<PackageChange> getDailyPackageChanges() {
        return mDailyPackageChanges;
    }

    protected long getBatteryUptimeLocked() {
        return mOnBatteryTimeBase.getUptime(mClocks.uptimeMillis() * 1000);
    }

    @Override
    public long getBatteryUptime(long curTime) {
        return mOnBatteryTimeBase.getUptime(curTime);
    }

    @Override
    public long getBatteryRealtime(long curTime) {
        return mOnBatteryTimeBase.getRealtime(curTime);
    }

    @Override
    public int getDischargeStartLevel() {
        synchronized(this) {
            return getDischargeStartLevelLocked();
        }
    }

    public int getDischargeStartLevelLocked() {
            return mDischargeUnplugLevel;
    }

    @Override
    public int getDischargeCurrentLevel() {
        synchronized(this) {
            return getDischargeCurrentLevelLocked();
        }
    }

    public int getDischargeCurrentLevelLocked() {
        return mDischargeCurrentLevel;
    }

    @Override
    public int getLowDischargeAmountSinceCharge() {
        synchronized(this) {
            int val = mLowDischargeAmountSinceCharge;
            if (mOnBattery && mDischargeCurrentLevel < mDischargeUnplugLevel) {
                val += mDischargeUnplugLevel-mDischargeCurrentLevel-1;
            }
            return val;
        }
    }

    @Override
    public int getHighDischargeAmountSinceCharge() {
        synchronized(this) {
            int val = mHighDischargeAmountSinceCharge;
            if (mOnBattery && mDischargeCurrentLevel < mDischargeUnplugLevel) {
                val += mDischargeUnplugLevel-mDischargeCurrentLevel;
            }
            return val;
        }
    }

    @Override
    public int getDischargeAmount(int which) {
        int dischargeAmount = which == STATS_SINCE_CHARGED
                ? getHighDischargeAmountSinceCharge()
                : (getDischargeStartLevel() - getDischargeCurrentLevel());
        if (dischargeAmount < 0) {
            dischargeAmount = 0;
        }
        return dischargeAmount;
    }

    public int getDischargeAmountScreenOn() {
        synchronized(this) {
            int val = mDischargeAmountScreenOn;
            if (mOnBattery && mScreenState == Display.STATE_ON
                    && mDischargeCurrentLevel < mDischargeScreenOnUnplugLevel) {
                val += mDischargeScreenOnUnplugLevel-mDischargeCurrentLevel;
            }
            return val;
        }
    }

    public int getDischargeAmountScreenOnSinceCharge() {
        synchronized(this) {
            int val = mDischargeAmountScreenOnSinceCharge;
            if (mOnBattery && mScreenState == Display.STATE_ON
                    && mDischargeCurrentLevel < mDischargeScreenOnUnplugLevel) {
                val += mDischargeScreenOnUnplugLevel-mDischargeCurrentLevel;
            }
            return val;
        }
    }

    public int getDischargeAmountScreenOff() {
        synchronized(this) {
            int val = mDischargeAmountScreenOff;
            if (mOnBattery && mScreenState != Display.STATE_ON
                    && mDischargeCurrentLevel < mDischargeScreenOffUnplugLevel) {
                val += mDischargeScreenOffUnplugLevel-mDischargeCurrentLevel;
            }
            return val;
        }
    }

    public int getDischargeAmountScreenOffSinceCharge() {
        synchronized(this) {
            int val = mDischargeAmountScreenOffSinceCharge;
            if (mOnBattery && mScreenState != Display.STATE_ON
                    && mDischargeCurrentLevel < mDischargeScreenOffUnplugLevel) {
                val += mDischargeScreenOffUnplugLevel-mDischargeCurrentLevel;
            }
            return val;
        }
    }

    /**
     * Retrieve the statistics object for a particular uid, creating if needed.
     */
    public Uid getUidStatsLocked(int uid) {
        Uid u = mUidStats.get(uid);
        if (u == null) {
            u = new Uid(this, uid);
            mUidStats.put(uid, u);
        }
        return u;
    }

    /**
     * Remove the statistics object for a particular uid.
     */
    public void removeUidStatsLocked(int uid) {
        mKernelUidCpuTimeReader.removeUid(uid);
        mUidStats.remove(uid);
    }

    /**
     * Retrieve the statistics object for a particular process, creating
     * if needed.
     */
    public Uid.Proc getProcessStatsLocked(int uid, String name) {
        uid = mapUid(uid);
        Uid u = getUidStatsLocked(uid);
        return u.getProcessStatsLocked(name);
    }

    /**
     * Retrieve the statistics object for a particular process, creating
     * if needed.
     */
    public Uid.Pkg getPackageStatsLocked(int uid, String pkg) {
        uid = mapUid(uid);
        Uid u = getUidStatsLocked(uid);
        return u.getPackageStatsLocked(pkg);
    }

    /**
     * Retrieve the statistics object for a particular service, creating
     * if needed.
     */
    public Uid.Pkg.Serv getServiceStatsLocked(int uid, String pkg, String name) {
        uid = mapUid(uid);
        Uid u = getUidStatsLocked(uid);
        return u.getServiceStatsLocked(pkg, name);
    }

    public void shutdownLocked() {
        recordShutdownLocked(mClocks.elapsedRealtime(), mClocks.uptimeMillis());
        writeSyncLocked();
        mShuttingDown = true;
    }

    Parcel mPendingWrite = null;
    final ReentrantLock mWriteLock = new ReentrantLock();

    public void writeAsyncLocked() {
        writeLocked(false);
    }

    public void writeSyncLocked() {
        writeLocked(true);
    }

    void writeLocked(boolean sync) {
        if (mFile == null) {
            Slog.w("BatteryStats", "writeLocked: no file associated with this instance");
            return;
        }

        if (mShuttingDown) {
            return;
        }

        Parcel out = Parcel.obtain();
        writeSummaryToParcel(out, true);
        mLastWriteTime = mClocks.elapsedRealtime();

        if (mPendingWrite != null) {
            mPendingWrite.recycle();
        }
        mPendingWrite = out;

        if (sync) {
            commitPendingDataToDisk();
        } else {
            BackgroundThread.getHandler().post(new Runnable() {
                @Override public void run() {
                    commitPendingDataToDisk();
                }
            });
        }
    }

    public void commitPendingDataToDisk() {
        final Parcel next;
        synchronized (this) {
            next = mPendingWrite;
            mPendingWrite = null;
            if (next == null) {
                return;
            }

            mWriteLock.lock();
        }

        try {
            FileOutputStream stream = new FileOutputStream(mFile.chooseForWrite());
            stream.write(next.marshall());
            stream.flush();
            FileUtils.sync(stream);
            stream.close();
            mFile.commit();
        } catch (IOException e) {
            Slog.w("BatteryStats", "Error writing battery statistics", e);
            mFile.rollback();
        } finally {
            next.recycle();
            mWriteLock.unlock();
        }
    }

    public void readLocked() {
        if (mDailyFile != null) {
            readDailyStatsLocked();
        }

        if (mFile == null) {
            Slog.w("BatteryStats", "readLocked: no file associated with this instance");
            return;
        }

        mUidStats.clear();

        try {
            File file = mFile.chooseForRead();
            if (!file.exists()) {
                return;
            }
            FileInputStream stream = new FileInputStream(file);

            byte[] raw = BatteryStatsHelper.readFully(stream);
            Parcel in = Parcel.obtain();
            in.unmarshall(raw, 0, raw.length);
            in.setDataPosition(0);
            stream.close();

            readSummaryFromParcel(in);
        } catch(Exception e) {
            Slog.e("BatteryStats", "Error reading battery statistics", e);
            resetAllStatsLocked();
        }

        mEndPlatformVersion = Build.ID;

        if (mHistoryBuffer.dataPosition() > 0) {
            mRecordingHistory = true;
            final long elapsedRealtime = mClocks.elapsedRealtime();
            final long uptime = mClocks.uptimeMillis();
            if (USE_OLD_HISTORY) {
                addHistoryRecordLocked(elapsedRealtime, uptime, HistoryItem.CMD_START, mHistoryCur);
            }
            addHistoryBufferLocked(elapsedRealtime, uptime, HistoryItem.CMD_START, mHistoryCur);
            startRecordingHistory(elapsedRealtime, uptime, false);
        }

        recordDailyStatsIfNeededLocked(false);
    }

    public int describeContents() {
        return 0;
    }

    void readHistory(Parcel in, boolean andOldHistory) throws ParcelFormatException {
        final long historyBaseTime = in.readLong();

        mHistoryBuffer.setDataSize(0);
        mHistoryBuffer.setDataPosition(0);
        mHistoryTagPool.clear();
        mNextHistoryTagIdx = 0;
        mNumHistoryTagChars = 0;

        int numTags = in.readInt();
        for (int i=0; i<numTags; i++) {
            int idx = in.readInt();
            String str = in.readString();
            if (str == null) {
                throw new ParcelFormatException("null history tag string");
            }
            int uid = in.readInt();
            HistoryTag tag = new HistoryTag();
            tag.string = str;
            tag.uid = uid;
            tag.poolIdx = idx;
            mHistoryTagPool.put(tag, idx);
            if (idx >= mNextHistoryTagIdx) {
                mNextHistoryTagIdx = idx+1;
            }
            mNumHistoryTagChars += tag.string.length() + 1;
        }

        int bufSize = in.readInt();
        int curPos = in.dataPosition();
        if (bufSize >= (MAX_MAX_HISTORY_BUFFER*3)) {
            throw new ParcelFormatException("File corrupt: history data buffer too large " +
                    bufSize);
        } else if ((bufSize&~3) != bufSize) {
            throw new ParcelFormatException("File corrupt: history data buffer not aligned " +
                    bufSize);
        } else {
            if (DEBUG_HISTORY) Slog.i(TAG, "***************** READING NEW HISTORY: " + bufSize
                    + " bytes at " + curPos);
            mHistoryBuffer.appendFrom(in, curPos, bufSize);
            in.setDataPosition(curPos + bufSize);
        }

        if (andOldHistory) {
            readOldHistory(in);
        }

        if (DEBUG_HISTORY) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("****************** OLD mHistoryBaseTime: ");
            TimeUtils.formatDuration(mHistoryBaseTime, sb);
            Slog.i(TAG, sb.toString());
        }
        mHistoryBaseTime = historyBaseTime;
        if (DEBUG_HISTORY) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("****************** NEW mHistoryBaseTime: ");
            TimeUtils.formatDuration(mHistoryBaseTime, sb);
            Slog.i(TAG, sb.toString());
        }

        // We are just arbitrarily going to insert 1 minute from the sample of
        // the last run until samples in this run.
        if (mHistoryBaseTime > 0) {
            long oldnow = mClocks.elapsedRealtime();
            mHistoryBaseTime = mHistoryBaseTime - oldnow + 1;
            if (DEBUG_HISTORY) {
                StringBuilder sb = new StringBuilder(128);
                sb.append("****************** ADJUSTED mHistoryBaseTime: ");
                TimeUtils.formatDuration(mHistoryBaseTime, sb);
                Slog.i(TAG, sb.toString());
            }
        }
    }

    void readOldHistory(Parcel in) {
        if (!USE_OLD_HISTORY) {
            return;
        }
        mHistory = mHistoryEnd = mHistoryCache = null;
        long time;
        while (in.dataAvail() > 0 && (time=in.readLong()) >= 0) {
            HistoryItem rec = new HistoryItem(time, in);
            addHistoryRecordLocked(rec);
        }
    }

    void writeHistory(Parcel out, boolean inclData, boolean andOldHistory) {
        if (DEBUG_HISTORY) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("****************** WRITING mHistoryBaseTime: ");
            TimeUtils.formatDuration(mHistoryBaseTime, sb);
            sb.append(" mLastHistoryElapsedRealtime: ");
            TimeUtils.formatDuration(mLastHistoryElapsedRealtime, sb);
            Slog.i(TAG, sb.toString());
        }
        out.writeLong(mHistoryBaseTime + mLastHistoryElapsedRealtime);
        if (!inclData) {
            out.writeInt(0);
            out.writeInt(0);
            return;
        }
        out.writeInt(mHistoryTagPool.size());
        for (HashMap.Entry<HistoryTag, Integer> ent : mHistoryTagPool.entrySet()) {
            HistoryTag tag = ent.getKey();
            out.writeInt(ent.getValue());
            out.writeString(tag.string);
            out.writeInt(tag.uid);
        }
        out.writeInt(mHistoryBuffer.dataSize());
        if (DEBUG_HISTORY) Slog.i(TAG, "***************** WRITING HISTORY: "
                + mHistoryBuffer.dataSize() + " bytes at " + out.dataPosition());
        out.appendFrom(mHistoryBuffer, 0, mHistoryBuffer.dataSize());

        if (andOldHistory) {
            writeOldHistory(out);
        }
    }

    void writeOldHistory(Parcel out) {
        if (!USE_OLD_HISTORY) {
            return;
        }
        HistoryItem rec = mHistory;
        while (rec != null) {
            if (rec.time >= 0) rec.writeToParcel(out, 0);
            rec = rec.next;
        }
        out.writeLong(-1);
    }

    public void readSummaryFromParcel(Parcel in) throws ParcelFormatException {
        final int version = in.readInt();
        if (version != VERSION) {
            Slog.w("BatteryStats", "readFromParcel: version got " + version
                + ", expected " + VERSION + "; erasing old stats");
            return;
        }

        readHistory(in, true);

        mStartCount = in.readInt();
        mUptime = in.readLong();
        mRealtime = in.readLong();
        mStartClockTime = in.readLong();
        mStartPlatformVersion = in.readString();
        mEndPlatformVersion = in.readString();
        mOnBatteryTimeBase.readSummaryFromParcel(in);
        mOnBatteryScreenOffTimeBase.readSummaryFromParcel(in);
        mDischargeUnplugLevel = in.readInt();
        mDischargePlugLevel = in.readInt();
        mDischargeCurrentLevel = in.readInt();
        mCurrentBatteryLevel = in.readInt();
        mEstimatedBatteryCapacity = in.readInt();
        mLowDischargeAmountSinceCharge = in.readInt();
        mHighDischargeAmountSinceCharge = in.readInt();
        mDischargeAmountScreenOnSinceCharge = in.readInt();
        mDischargeAmountScreenOffSinceCharge = in.readInt();
        mDischargeStepTracker.readFromParcel(in);
        mChargeStepTracker.readFromParcel(in);
        mDailyDischargeStepTracker.readFromParcel(in);
        mDailyChargeStepTracker.readFromParcel(in);
        mDischargeCounter.readSummaryFromParcelLocked(in);
        mDischargeScreenOffCounter.readSummaryFromParcelLocked(in);
        int NPKG = in.readInt();
        if (NPKG > 0) {
            mDailyPackageChanges = new ArrayList<>(NPKG);
            while (NPKG > 0) {
                NPKG--;
                PackageChange pc = new PackageChange();
                pc.mPackageName = in.readString();
                pc.mUpdate = in.readInt() != 0;
                pc.mVersionCode = in.readInt();
                mDailyPackageChanges.add(pc);
            }
        } else {
            mDailyPackageChanges = null;
        }
        mDailyStartTime = in.readLong();
        mNextMinDailyDeadline = in.readLong();
        mNextMaxDailyDeadline = in.readLong();

        mStartCount++;

        mScreenState = Display.STATE_UNKNOWN;
        mScreenOnTimer.readSummaryFromParcelLocked(in);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i].readSummaryFromParcelLocked(in);
        }
        mInteractive = false;
        mInteractiveTimer.readSummaryFromParcelLocked(in);
        mPhoneOn = false;
        mPowerSaveModeEnabledTimer.readSummaryFromParcelLocked(in);
        mLongestLightIdleTime = in.readLong();
        mLongestFullIdleTime = in.readLong();
        mDeviceIdleModeLightTimer.readSummaryFromParcelLocked(in);
        mDeviceIdleModeFullTimer.readSummaryFromParcelLocked(in);
        mDeviceLightIdlingTimer.readSummaryFromParcelLocked(in);
        mDeviceIdlingTimer.readSummaryFromParcelLocked(in);
        mPhoneOnTimer.readSummaryFromParcelLocked(in);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i].readSummaryFromParcelLocked(in);
        }
        mPhoneSignalScanningTimer.readSummaryFromParcelLocked(in);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i].readSummaryFromParcelLocked(in);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i].readSummaryFromParcelLocked(in);
            mNetworkPacketActivityCounters[i].readSummaryFromParcelLocked(in);
        }
        mMobileRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
        mMobileRadioActiveTimer.readSummaryFromParcelLocked(in);
        mMobileRadioActivePerAppTimer.readSummaryFromParcelLocked(in);
        mMobileRadioActiveAdjustedTime.readSummaryFromParcelLocked(in);
        mMobileRadioActiveUnknownTime.readSummaryFromParcelLocked(in);
        mMobileRadioActiveUnknownCount.readSummaryFromParcelLocked(in);
        mWifiRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
        mWifiOn = false;
        mWifiOnTimer.readSummaryFromParcelLocked(in);
        mGlobalWifiRunning = false;
        mGlobalWifiRunningTimer.readSummaryFromParcelLocked(in);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i].readSummaryFromParcelLocked(in);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i].readSummaryFromParcelLocked(in);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i].readSummaryFromParcelLocked(in);
        }
        mWifiActivity.readSummaryFromParcel(in);
        mBluetoothActivity.readSummaryFromParcel(in);
        mModemActivity.readSummaryFromParcel(in);
        mHasWifiReporting = in.readInt() != 0;
        mHasBluetoothReporting = in.readInt() != 0;
        mHasModemReporting = in.readInt() != 0;

        mNumConnectivityChange = mLoadedNumConnectivityChange = in.readInt();
        mFlashlightOnNesting = 0;
        mFlashlightOnTimer.readSummaryFromParcelLocked(in);
        mCameraOnNesting = 0;
        mCameraOnTimer.readSummaryFromParcelLocked(in);
        mBluetoothScanNesting = 0;
        mBluetoothScanTimer.readSummaryFromParcelLocked(in);

        int NKW = in.readInt();
        if (NKW > 10000) {
            throw new ParcelFormatException("File corrupt: too many kernel wake locks " + NKW);
        }
        for (int ikw = 0; ikw < NKW; ikw++) {
            if (in.readInt() != 0) {
                String kwltName = in.readString();
                getKernelWakelockTimerLocked(kwltName).readSummaryFromParcelLocked(in);
            }
        }

        int NWR = in.readInt();
        if (NWR > 10000) {
            throw new ParcelFormatException("File corrupt: too many wakeup reasons " + NWR);
        }
        for (int iwr = 0; iwr < NWR; iwr++) {
            if (in.readInt() != 0) {
                String reasonName = in.readString();
                getWakeupReasonTimerLocked(reasonName).readSummaryFromParcelLocked(in);
            }
        }

        final int NU = in.readInt();
        if (NU > 10000) {
            throw new ParcelFormatException("File corrupt: too many uids " + NU);
        }
        for (int iu = 0; iu < NU; iu++) {
            int uid = in.readInt();
            Uid u = new Uid(this, uid);
            mUidStats.put(uid, u);

            u.mWifiRunning = false;
            if (in.readInt() != 0) {
                u.mWifiRunningTimer.readSummaryFromParcelLocked(in);
            }
            u.mFullWifiLockOut = false;
            if (in.readInt() != 0) {
                u.mFullWifiLockTimer.readSummaryFromParcelLocked(in);
            }
            u.mWifiScanStarted = false;
            if (in.readInt() != 0) {
                u.mWifiScanTimer.readSummaryFromParcelLocked(in);
            }
            u.mWifiBatchedScanBinStarted = Uid.NO_BATCHED_SCAN_STARTED;
            for (int i = 0; i < Uid.NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                if (in.readInt() != 0) {
                    u.makeWifiBatchedScanBin(i, null);
                    u.mWifiBatchedScanTimer[i].readSummaryFromParcelLocked(in);
                }
            }
            u.mWifiMulticastEnabled = false;
            if (in.readInt() != 0) {
                u.mWifiMulticastTimer.readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createAudioTurnedOnTimerLocked().readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createVideoTurnedOnTimerLocked().readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createFlashlightTurnedOnTimerLocked().readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createCameraTurnedOnTimerLocked().readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createForegroundActivityTimerLocked().readSummaryFromParcelLocked(in);
            }
            if (in.readInt() != 0) {
                u.createBluetoothScanTimerLocked().readSummaryFromParcelLocked(in);
            }
            u.mProcessState = ActivityManager.PROCESS_STATE_NONEXISTENT;
            for (int i = 0; i < Uid.NUM_PROCESS_STATE; i++) {
                if (in.readInt() != 0) {
                    u.makeProcessState(i, null);
                    u.mProcessStateTimer[i].readSummaryFromParcelLocked(in);
                }
            }
            if (in.readInt() != 0) {
                u.createVibratorOnTimerLocked().readSummaryFromParcelLocked(in);
            }

            if (in.readInt() != 0) {
                if (u.mUserActivityCounters == null) {
                    u.initUserActivityLocked();
                }
                for (int i=0; i<Uid.NUM_USER_ACTIVITY_TYPES; i++) {
                    u.mUserActivityCounters[i].readSummaryFromParcelLocked(in);
                }
            }

            if (in.readInt() != 0) {
                if (u.mNetworkByteActivityCounters == null) {
                    u.initNetworkActivityLocked();
                }
                for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                    u.mNetworkByteActivityCounters[i].readSummaryFromParcelLocked(in);
                    u.mNetworkPacketActivityCounters[i].readSummaryFromParcelLocked(in);
                }
                u.mMobileRadioActiveTime.readSummaryFromParcelLocked(in);
                u.mMobileRadioActiveCount.readSummaryFromParcelLocked(in);
            }

            u.mUserCpuTime.readSummaryFromParcelLocked(in);
            u.mSystemCpuTime.readSummaryFromParcelLocked(in);
            u.mCpuPower.readSummaryFromParcelLocked(in);

            if (in.readInt() != 0) {
                final int numClusters = in.readInt();
                if (mPowerProfile != null && mPowerProfile.getNumCpuClusters() != numClusters) {
                    throw new ParcelFormatException("Incompatible cpu cluster arrangement");
                }

                u.mCpuClusterSpeed = new LongSamplingCounter[numClusters][];
                for (int cluster = 0; cluster < numClusters; cluster++) {
                    if (in.readInt() != 0) {
                        final int NSB = in.readInt();
                        if (mPowerProfile != null &&
                                mPowerProfile.getNumSpeedStepsInCpuCluster(cluster) != NSB) {
                            throw new ParcelFormatException("File corrupt: too many speed bins " +
                                    NSB);
                        }

                        u.mCpuClusterSpeed[cluster] = new LongSamplingCounter[NSB];
                        for (int speed = 0; speed < NSB; speed++) {
                            if (in.readInt() != 0) {
                                u.mCpuClusterSpeed[cluster][speed] = new LongSamplingCounter(
                                        mOnBatteryTimeBase);
                                u.mCpuClusterSpeed[cluster][speed].readSummaryFromParcelLocked(in);
                            }
                        }
                    } else {
                        u.mCpuClusterSpeed[cluster] = null;
                    }
                }
            } else {
                u.mCpuClusterSpeed = null;
            }

            if (in.readInt() != 0) {
                u.mMobileRadioApWakeupCount = new LongSamplingCounter(mOnBatteryTimeBase);
                u.mMobileRadioApWakeupCount.readSummaryFromParcelLocked(in);
            } else {
                u.mMobileRadioApWakeupCount = null;
            }

            if (in.readInt() != 0) {
                u.mWifiRadioApWakeupCount = new LongSamplingCounter(mOnBatteryTimeBase);
                u.mWifiRadioApWakeupCount.readSummaryFromParcelLocked(in);
            } else {
                u.mWifiRadioApWakeupCount = null;
            }

            int NW = in.readInt();
            if (NW > (MAX_WAKELOCKS_PER_UID+1)) {
                throw new ParcelFormatException("File corrupt: too many wake locks " + NW);
            }
            for (int iw = 0; iw < NW; iw++) {
                String wlName = in.readString();
                u.readWakeSummaryFromParcelLocked(wlName, in);
            }

            int NS = in.readInt();
            if (NS > (MAX_WAKELOCKS_PER_UID+1)) {
                throw new ParcelFormatException("File corrupt: too many syncs " + NS);
            }
            for (int is = 0; is < NS; is++) {
                String name = in.readString();
                u.readSyncSummaryFromParcelLocked(name, in);
            }

            int NJ = in.readInt();
            if (NJ > (MAX_WAKELOCKS_PER_UID+1)) {
                throw new ParcelFormatException("File corrupt: too many job timers " + NJ);
            }
            for (int ij = 0; ij < NJ; ij++) {
                String name = in.readString();
                u.readJobSummaryFromParcelLocked(name, in);
            }

            int NP = in.readInt();
            if (NP > 1000) {
                throw new ParcelFormatException("File corrupt: too many sensors " + NP);
            }
            for (int is = 0; is < NP; is++) {
                int seNumber = in.readInt();
                if (in.readInt() != 0) {
                    u.getSensorTimerLocked(seNumber, true)
                            .readSummaryFromParcelLocked(in);
                }
            }

            NP = in.readInt();
            if (NP > 1000) {
                throw new ParcelFormatException("File corrupt: too many processes " + NP);
            }
            for (int ip = 0; ip < NP; ip++) {
                String procName = in.readString();
                Uid.Proc p = u.getProcessStatsLocked(procName);
                p.mUserTime = p.mLoadedUserTime = in.readLong();
                p.mSystemTime = p.mLoadedSystemTime = in.readLong();
                p.mForegroundTime = p.mLoadedForegroundTime = in.readLong();
                p.mStarts = p.mLoadedStarts = in.readInt();
                p.mNumCrashes = p.mLoadedNumCrashes = in.readInt();
                p.mNumAnrs = p.mLoadedNumAnrs = in.readInt();
                p.readExcessivePowerFromParcelLocked(in);
            }

            NP = in.readInt();
            if (NP > 10000) {
                throw new ParcelFormatException("File corrupt: too many packages " + NP);
            }
            for (int ip = 0; ip < NP; ip++) {
                String pkgName = in.readString();
                Uid.Pkg p = u.getPackageStatsLocked(pkgName);
                final int NWA = in.readInt();
                if (NWA > 1000) {
                    throw new ParcelFormatException("File corrupt: too many wakeup alarms " + NWA);
                }
                p.mWakeupAlarms.clear();
                for (int iwa=0; iwa<NWA; iwa++) {
                    String tag = in.readString();
                    Counter c = new Counter(mOnBatteryTimeBase);
                    c.readSummaryFromParcelLocked(in);
                    p.mWakeupAlarms.put(tag, c);
                }
                NS = in.readInt();
                if (NS > 1000) {
                    throw new ParcelFormatException("File corrupt: too many services " + NS);
                }
                for (int is = 0; is < NS; is++) {
                    String servName = in.readString();
                    Uid.Pkg.Serv s = u.getServiceStatsLocked(pkgName, servName);
                    s.mStartTime = s.mLoadedStartTime = in.readLong();
                    s.mStarts = s.mLoadedStarts = in.readInt();
                    s.mLaunches = s.mLoadedLaunches = in.readInt();
                }
            }
        }
    }

    /**
     * Writes a summary of the statistics to a Parcel, in a format suitable to be written to
     * disk.  This format does not allow a lossless round-trip.
     *
     * @param out the Parcel to be written to.
     */
    public void writeSummaryToParcel(Parcel out, boolean inclHistory) {
        pullPendingStateUpdatesLocked();

        // Pull the clock time.  This may update the time and make a new history entry
        // if we had originally pulled a time before the RTC was set.
        long startClockTime = getStartClockTime();

        final long NOW_SYS = mClocks.uptimeMillis() * 1000;
        final long NOWREAL_SYS = mClocks.elapsedRealtime() * 1000;

        out.writeInt(VERSION);

        writeHistory(out, inclHistory, true);

        out.writeInt(mStartCount);
        out.writeLong(computeUptime(NOW_SYS, STATS_SINCE_CHARGED));
        out.writeLong(computeRealtime(NOWREAL_SYS, STATS_SINCE_CHARGED));
        out.writeLong(startClockTime);
        out.writeString(mStartPlatformVersion);
        out.writeString(mEndPlatformVersion);
        mOnBatteryTimeBase.writeSummaryToParcel(out, NOW_SYS, NOWREAL_SYS);
        mOnBatteryScreenOffTimeBase.writeSummaryToParcel(out, NOW_SYS, NOWREAL_SYS);
        out.writeInt(mDischargeUnplugLevel);
        out.writeInt(mDischargePlugLevel);
        out.writeInt(mDischargeCurrentLevel);
        out.writeInt(mCurrentBatteryLevel);
        out.writeInt(mEstimatedBatteryCapacity);
        out.writeInt(getLowDischargeAmountSinceCharge());
        out.writeInt(getHighDischargeAmountSinceCharge());
        out.writeInt(getDischargeAmountScreenOnSinceCharge());
        out.writeInt(getDischargeAmountScreenOffSinceCharge());
        mDischargeStepTracker.writeToParcel(out);
        mChargeStepTracker.writeToParcel(out);
        mDailyDischargeStepTracker.writeToParcel(out);
        mDailyChargeStepTracker.writeToParcel(out);
        mDischargeCounter.writeSummaryFromParcelLocked(out);
        mDischargeScreenOffCounter.writeSummaryFromParcelLocked(out);
        if (mDailyPackageChanges != null) {
            final int NPKG = mDailyPackageChanges.size();
            out.writeInt(NPKG);
            for (int i=0; i<NPKG; i++) {
                PackageChange pc = mDailyPackageChanges.get(i);
                out.writeString(pc.mPackageName);
                out.writeInt(pc.mUpdate ? 1 : 0);
                out.writeInt(pc.mVersionCode);
            }
        } else {
            out.writeInt(0);
        }
        out.writeLong(mDailyStartTime);
        out.writeLong(mNextMinDailyDeadline);
        out.writeLong(mNextMaxDailyDeadline);

        mScreenOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        mInteractiveTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mPowerSaveModeEnabledTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        out.writeLong(mLongestLightIdleTime);
        out.writeLong(mLongestFullIdleTime);
        mDeviceIdleModeLightTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mDeviceIdleModeFullTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mDeviceLightIdlingTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mDeviceIdlingTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mPhoneOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        mPhoneSignalScanningTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i].writeSummaryFromParcelLocked(out);
            mNetworkPacketActivityCounters[i].writeSummaryFromParcelLocked(out);
        }
        mMobileRadioActiveTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mMobileRadioActivePerAppTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mMobileRadioActiveAdjustedTime.writeSummaryFromParcelLocked(out);
        mMobileRadioActiveUnknownTime.writeSummaryFromParcelLocked(out);
        mMobileRadioActiveUnknownCount.writeSummaryFromParcelLocked(out);
        mWifiOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mGlobalWifiRunningTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        }
        mWifiActivity.writeSummaryToParcel(out);
        mBluetoothActivity.writeSummaryToParcel(out);
        mModemActivity.writeSummaryToParcel(out);
        out.writeInt(mHasWifiReporting ? 1 : 0);
        out.writeInt(mHasBluetoothReporting ? 1 : 0);
        out.writeInt(mHasModemReporting ? 1 : 0);

        out.writeInt(mNumConnectivityChange);
        mFlashlightOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mCameraOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
        mBluetoothScanTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);

        out.writeInt(mKernelWakelockStats.size());
        for (Map.Entry<String, SamplingTimer> ent : mKernelWakelockStats.entrySet()) {
            Timer kwlt = ent.getValue();
            if (kwlt != null) {
                out.writeInt(1);
                out.writeString(ent.getKey());
                kwlt.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
        }

        out.writeInt(mWakeupReasonStats.size());
        for (Map.Entry<String, SamplingTimer> ent : mWakeupReasonStats.entrySet()) {
            SamplingTimer timer = ent.getValue();
            if (timer != null) {
                out.writeInt(1);
                out.writeString(ent.getKey());
                timer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
        }

        final int NU = mUidStats.size();
        out.writeInt(NU);
        for (int iu = 0; iu < NU; iu++) {
            out.writeInt(mUidStats.keyAt(iu));
            Uid u = mUidStats.valueAt(iu);

            if (u.mWifiRunningTimer != null) {
                out.writeInt(1);
                u.mWifiRunningTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mFullWifiLockTimer != null) {
                out.writeInt(1);
                u.mFullWifiLockTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mWifiScanTimer != null) {
                out.writeInt(1);
                u.mWifiScanTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            for (int i = 0; i < Uid.NUM_WIFI_BATCHED_SCAN_BINS; i++) {
                if (u.mWifiBatchedScanTimer[i] != null) {
                    out.writeInt(1);
                    u.mWifiBatchedScanTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
            }
            if (u.mWifiMulticastTimer != null) {
                out.writeInt(1);
                u.mWifiMulticastTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mAudioTurnedOnTimer != null) {
                out.writeInt(1);
                u.mAudioTurnedOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mVideoTurnedOnTimer != null) {
                out.writeInt(1);
                u.mVideoTurnedOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mFlashlightTurnedOnTimer != null) {
                out.writeInt(1);
                u.mFlashlightTurnedOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mCameraTurnedOnTimer != null) {
                out.writeInt(1);
                u.mCameraTurnedOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mForegroundActivityTimer != null) {
                out.writeInt(1);
                u.mForegroundActivityTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            if (u.mBluetoothScanTimer != null) {
                out.writeInt(1);
                u.mBluetoothScanTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }
            for (int i = 0; i < Uid.NUM_PROCESS_STATE; i++) {
                if (u.mProcessStateTimer[i] != null) {
                    out.writeInt(1);
                    u.mProcessStateTimer[i].writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
            }
            if (u.mVibratorOnTimer != null) {
                out.writeInt(1);
                u.mVibratorOnTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            } else {
                out.writeInt(0);
            }

            if (u.mUserActivityCounters == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                for (int i=0; i<Uid.NUM_USER_ACTIVITY_TYPES; i++) {
                    u.mUserActivityCounters[i].writeSummaryFromParcelLocked(out);
                }
            }

            if (u.mNetworkByteActivityCounters == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
                    u.mNetworkByteActivityCounters[i].writeSummaryFromParcelLocked(out);
                    u.mNetworkPacketActivityCounters[i].writeSummaryFromParcelLocked(out);
                }
                u.mMobileRadioActiveTime.writeSummaryFromParcelLocked(out);
                u.mMobileRadioActiveCount.writeSummaryFromParcelLocked(out);
            }

            u.mUserCpuTime.writeSummaryFromParcelLocked(out);
            u.mSystemCpuTime.writeSummaryFromParcelLocked(out);
            u.mCpuPower.writeSummaryFromParcelLocked(out);

            if (u.mCpuClusterSpeed != null) {
                out.writeInt(1);
                out.writeInt(u.mCpuClusterSpeed.length);
                for (LongSamplingCounter[] cpuSpeeds : u.mCpuClusterSpeed) {
                    if (cpuSpeeds != null) {
                        out.writeInt(1);
                        out.writeInt(cpuSpeeds.length);
                        for (LongSamplingCounter c : cpuSpeeds) {
                            if (c != null) {
                                out.writeInt(1);
                                c.writeSummaryFromParcelLocked(out);
                            } else {
                                out.writeInt(0);
                            }
                        }
                    } else {
                        out.writeInt(0);
                    }
                }
            } else {
                out.writeInt(0);
            }

            if (u.mMobileRadioApWakeupCount != null) {
                out.writeInt(1);
                u.mMobileRadioApWakeupCount.writeSummaryFromParcelLocked(out);
            } else {
                out.writeInt(0);
            }

            if (u.mWifiRadioApWakeupCount != null) {
                out.writeInt(1);
                u.mWifiRadioApWakeupCount.writeSummaryFromParcelLocked(out);
            } else {
                out.writeInt(0);
            }

            final ArrayMap<String, Uid.Wakelock> wakeStats = u.mWakelockStats.getMap();
            int NW = wakeStats.size();
            out.writeInt(NW);
            for (int iw=0; iw<NW; iw++) {
                out.writeString(wakeStats.keyAt(iw));
                Uid.Wakelock wl = wakeStats.valueAt(iw);
                if (wl.mTimerFull != null) {
                    out.writeInt(1);
                    wl.mTimerFull.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
                if (wl.mTimerPartial != null) {
                    out.writeInt(1);
                    wl.mTimerPartial.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
                if (wl.mTimerWindow != null) {
                    out.writeInt(1);
                    wl.mTimerWindow.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
                if (wl.mTimerDraw != null) {
                    out.writeInt(1);
                    wl.mTimerDraw.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
            }

            final ArrayMap<String, StopwatchTimer> syncStats = u.mSyncStats.getMap();
            int NS = syncStats.size();
            out.writeInt(NS);
            for (int is=0; is<NS; is++) {
                out.writeString(syncStats.keyAt(is));
                syncStats.valueAt(is).writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            }

            final ArrayMap<String, StopwatchTimer> jobStats = u.mJobStats.getMap();
            int NJ = jobStats.size();
            out.writeInt(NJ);
            for (int ij=0; ij<NJ; ij++) {
                out.writeString(jobStats.keyAt(ij));
                jobStats.valueAt(ij).writeSummaryFromParcelLocked(out, NOWREAL_SYS);
            }

            int NSE = u.mSensorStats.size();
            out.writeInt(NSE);
            for (int ise=0; ise<NSE; ise++) {
                out.writeInt(u.mSensorStats.keyAt(ise));
                Uid.Sensor se = u.mSensorStats.valueAt(ise);
                if (se.mTimer != null) {
                    out.writeInt(1);
                    se.mTimer.writeSummaryFromParcelLocked(out, NOWREAL_SYS);
                } else {
                    out.writeInt(0);
                }
            }

            int NP = u.mProcessStats.size();
            out.writeInt(NP);
            for (int ip=0; ip<NP; ip++) {
                out.writeString(u.mProcessStats.keyAt(ip));
                Uid.Proc ps = u.mProcessStats.valueAt(ip);
                out.writeLong(ps.mUserTime);
                out.writeLong(ps.mSystemTime);
                out.writeLong(ps.mForegroundTime);
                out.writeInt(ps.mStarts);
                out.writeInt(ps.mNumCrashes);
                out.writeInt(ps.mNumAnrs);
                ps.writeExcessivePowerToParcelLocked(out);
            }

            NP = u.mPackageStats.size();
            out.writeInt(NP);
            if (NP > 0) {
                for (Map.Entry<String, BatteryStatsImpl.Uid.Pkg> ent
                    : u.mPackageStats.entrySet()) {
                    out.writeString(ent.getKey());
                    Uid.Pkg ps = ent.getValue();
                    final int NWA = ps.mWakeupAlarms.size();
                    out.writeInt(NWA);
                    for (int iwa=0; iwa<NWA; iwa++) {
                        out.writeString(ps.mWakeupAlarms.keyAt(iwa));
                        ps.mWakeupAlarms.valueAt(iwa).writeSummaryFromParcelLocked(out);
                    }
                    NS = ps.mServiceStats.size();
                    out.writeInt(NS);
                    for (int is=0; is<NS; is++) {
                        out.writeString(ps.mServiceStats.keyAt(is));
                        BatteryStatsImpl.Uid.Pkg.Serv ss = ps.mServiceStats.valueAt(is);
                        long time = ss.getStartTimeToNowLocked(
                                mOnBatteryTimeBase.getUptime(NOW_SYS));
                        out.writeLong(time);
                        out.writeInt(ss.mStarts);
                        out.writeInt(ss.mLaunches);
                    }
                }
            }
        }
    }

    public void readFromParcel(Parcel in) {
        readFromParcelLocked(in);
    }

    void readFromParcelLocked(Parcel in) {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new ParcelFormatException("Bad magic number: #" + Integer.toHexString(magic));
        }

        readHistory(in, false);

        mStartCount = in.readInt();
        mStartClockTime = in.readLong();
        mStartPlatformVersion = in.readString();
        mEndPlatformVersion = in.readString();
        mUptime = in.readLong();
        mUptimeStart = in.readLong();
        mRealtime = in.readLong();
        mRealtimeStart = in.readLong();
        mOnBattery = in.readInt() != 0;
        mEstimatedBatteryCapacity = in.readInt();
        mOnBatteryInternal = false; // we are no longer really running.
        mOnBatteryTimeBase.readFromParcel(in);
        mOnBatteryScreenOffTimeBase.readFromParcel(in);

        mScreenState = Display.STATE_UNKNOWN;
        mScreenOnTimer = new StopwatchTimer(mClocks, null, -1, null, mOnBatteryTimeBase, in);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i] = new StopwatchTimer(mClocks, null, -100-i, null,
                    mOnBatteryTimeBase, in);
        }
        mInteractive = false;
        mInteractiveTimer = new StopwatchTimer(mClocks, null, -10, null, mOnBatteryTimeBase, in);
        mPhoneOn = false;
        mPowerSaveModeEnabledTimer = new StopwatchTimer(mClocks, null, -2, null,
                mOnBatteryTimeBase, in);
        mLongestLightIdleTime = in.readLong();
        mLongestFullIdleTime = in.readLong();
        mDeviceIdleModeLightTimer = new StopwatchTimer(mClocks, null, -14, null,
                mOnBatteryTimeBase, in);
        mDeviceIdleModeFullTimer = new StopwatchTimer(mClocks, null, -11, null,
                mOnBatteryTimeBase, in);
        mDeviceLightIdlingTimer = new StopwatchTimer(mClocks, null, -15, null,
                mOnBatteryTimeBase, in);
        mDeviceIdlingTimer = new StopwatchTimer(mClocks, null, -12, null, mOnBatteryTimeBase, in);
        mPhoneOnTimer = new StopwatchTimer(mClocks, null, -3, null, mOnBatteryTimeBase, in);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i] = new StopwatchTimer(mClocks, null, -200-i,
                    null, mOnBatteryTimeBase, in);
        }
        mPhoneSignalScanningTimer = new StopwatchTimer(mClocks, null, -200+1, null,
                mOnBatteryTimeBase, in);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i] = new StopwatchTimer(mClocks, null, -300-i,
                    null, mOnBatteryTimeBase, in);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i] = new LongSamplingCounter(mOnBatteryTimeBase, in);
            mNetworkPacketActivityCounters[i] = new LongSamplingCounter(mOnBatteryTimeBase, in);
        }
        mMobileRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
        mMobileRadioActiveTimer = new StopwatchTimer(mClocks, null, -400, null,
                mOnBatteryTimeBase, in);
        mMobileRadioActivePerAppTimer = new StopwatchTimer(mClocks, null, -401, null, 
                mOnBatteryTimeBase, in);
        mMobileRadioActiveAdjustedTime = new LongSamplingCounter(mOnBatteryTimeBase, in);
        mMobileRadioActiveUnknownTime = new LongSamplingCounter(mOnBatteryTimeBase, in);
        mMobileRadioActiveUnknownCount = new LongSamplingCounter(mOnBatteryTimeBase, in);
        mWifiRadioPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
        mWifiOn = false;
        mWifiOnTimer = new StopwatchTimer(mClocks, null, -4, null, mOnBatteryTimeBase, in);
        mGlobalWifiRunning = false;
        mGlobalWifiRunningTimer = new StopwatchTimer(mClocks, null, -5, null,
                mOnBatteryTimeBase, in);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i] = new StopwatchTimer(mClocks, null, -600-i,
                    null, mOnBatteryTimeBase, in);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i] = new StopwatchTimer(mClocks, null, -700-i,
                    null, mOnBatteryTimeBase, in);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i] = new StopwatchTimer(mClocks, null, -800-i,
                    null, mOnBatteryTimeBase, in);
        }

        mWifiActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase,
                NUM_WIFI_TX_LEVELS, in);
        mBluetoothActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase,
                NUM_BT_TX_LEVELS, in);
        mModemActivity = new ControllerActivityCounterImpl(mOnBatteryTimeBase,
                ModemActivityInfo.TX_POWER_LEVELS, in);
        mHasWifiReporting = in.readInt() != 0;
        mHasBluetoothReporting = in.readInt() != 0;
        mHasModemReporting = in.readInt() != 0;

        mNumConnectivityChange = in.readInt();
        mLoadedNumConnectivityChange = in.readInt();
        mUnpluggedNumConnectivityChange = in.readInt();
        mAudioOnNesting = 0;
        mAudioOnTimer = new StopwatchTimer(mClocks, null, -7, null, mOnBatteryTimeBase);
        mVideoOnNesting = 0;
        mVideoOnTimer = new StopwatchTimer(mClocks, null, -8, null, mOnBatteryTimeBase);
        mFlashlightOnNesting = 0;
        mFlashlightOnTimer = new StopwatchTimer(mClocks, null, -9, null, mOnBatteryTimeBase, in);
        mCameraOnNesting = 0;
        mCameraOnTimer = new StopwatchTimer(mClocks, null, -13, null, mOnBatteryTimeBase, in);
        mBluetoothScanNesting = 0;
        mBluetoothScanTimer = new StopwatchTimer(mClocks, null, -14, null, mOnBatteryTimeBase, in);
        mDischargeUnplugLevel = in.readInt();
        mDischargePlugLevel = in.readInt();
        mDischargeCurrentLevel = in.readInt();
        mCurrentBatteryLevel = in.readInt();
        mLowDischargeAmountSinceCharge = in.readInt();
        mHighDischargeAmountSinceCharge = in.readInt();
        mDischargeAmountScreenOn = in.readInt();
        mDischargeAmountScreenOnSinceCharge = in.readInt();
        mDischargeAmountScreenOff = in.readInt();
        mDischargeAmountScreenOffSinceCharge = in.readInt();
        mDischargeStepTracker.readFromParcel(in);
        mChargeStepTracker.readFromParcel(in);
        mDischargeCounter = new LongSamplingCounter(mOnBatteryTimeBase, in);
        mDischargeScreenOffCounter = new LongSamplingCounter(mOnBatteryTimeBase, in);
        mLastWriteTime = in.readLong();

        mKernelWakelockStats.clear();
        int NKW = in.readInt();
        for (int ikw = 0; ikw < NKW; ikw++) {
            if (in.readInt() != 0) {
                String wakelockName = in.readString();
                SamplingTimer kwlt = new SamplingTimer(mClocks, mOnBatteryScreenOffTimeBase, in);
                mKernelWakelockStats.put(wakelockName, kwlt);
            }
        }

        mWakeupReasonStats.clear();
        int NWR = in.readInt();
        for (int iwr = 0; iwr < NWR; iwr++) {
            if (in.readInt() != 0) {
                String reasonName = in.readString();
                SamplingTimer timer = new SamplingTimer(mClocks, mOnBatteryTimeBase, in);
                mWakeupReasonStats.put(reasonName, timer);
            }
        }

        mPartialTimers.clear();
        mFullTimers.clear();
        mWindowTimers.clear();
        mWifiRunningTimers.clear();
        mFullWifiLockTimers.clear();
        mWifiScanTimers.clear();
        mWifiBatchedScanTimers.clear();
        mWifiMulticastTimers.clear();
        mAudioTurnedOnTimers.clear();
        mVideoTurnedOnTimers.clear();
        mFlashlightTurnedOnTimers.clear();
        mCameraTurnedOnTimers.clear();

        int numUids = in.readInt();
        mUidStats.clear();
        for (int i = 0; i < numUids; i++) {
            int uid = in.readInt();
            Uid u = new Uid(this, uid);
            u.readFromParcelLocked(mOnBatteryTimeBase, mOnBatteryScreenOffTimeBase, in);
            mUidStats.append(uid, u);
        }
    }

    public void writeToParcel(Parcel out, int flags) {
        writeToParcelLocked(out, true, flags);
    }

    public void writeToParcelWithoutUids(Parcel out, int flags) {
        writeToParcelLocked(out, false, flags);
    }

    @SuppressWarnings("unused")
    void writeToParcelLocked(Parcel out, boolean inclUids, int flags) {
        // Need to update with current kernel wake lock counts.
        pullPendingStateUpdatesLocked();

        // Pull the clock time.  This may update the time and make a new history entry
        // if we had originally pulled a time before the RTC was set.
        long startClockTime = getStartClockTime();

        final long uSecUptime = mClocks.uptimeMillis() * 1000;
        final long uSecRealtime = mClocks.elapsedRealtime() * 1000;
        final long batteryRealtime = mOnBatteryTimeBase.getRealtime(uSecRealtime);
        final long batteryScreenOffRealtime = mOnBatteryScreenOffTimeBase.getRealtime(uSecRealtime);

        out.writeInt(MAGIC);

        writeHistory(out, true, false);

        out.writeInt(mStartCount);
        out.writeLong(startClockTime);
        out.writeString(mStartPlatformVersion);
        out.writeString(mEndPlatformVersion);
        out.writeLong(mUptime);
        out.writeLong(mUptimeStart);
        out.writeLong(mRealtime);
        out.writeLong(mRealtimeStart);
        out.writeInt(mOnBattery ? 1 : 0);
        out.writeInt(mEstimatedBatteryCapacity);
        mOnBatteryTimeBase.writeToParcel(out, uSecUptime, uSecRealtime);
        mOnBatteryScreenOffTimeBase.writeToParcel(out, uSecUptime, uSecRealtime);

        mScreenOnTimer.writeToParcel(out, uSecRealtime);
        for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            mScreenBrightnessTimer[i].writeToParcel(out, uSecRealtime);
        }
        mInteractiveTimer.writeToParcel(out, uSecRealtime);
        mPowerSaveModeEnabledTimer.writeToParcel(out, uSecRealtime);
        out.writeLong(mLongestLightIdleTime);
        out.writeLong(mLongestFullIdleTime);
        mDeviceIdleModeLightTimer.writeToParcel(out, uSecRealtime);
        mDeviceIdleModeFullTimer.writeToParcel(out, uSecRealtime);
        mDeviceLightIdlingTimer.writeToParcel(out, uSecRealtime);
        mDeviceIdlingTimer.writeToParcel(out, uSecRealtime);
        mPhoneOnTimer.writeToParcel(out, uSecRealtime);
        for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
            mPhoneSignalStrengthsTimer[i].writeToParcel(out, uSecRealtime);
        }
        mPhoneSignalScanningTimer.writeToParcel(out, uSecRealtime);
        for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
            mPhoneDataConnectionsTimer[i].writeToParcel(out, uSecRealtime);
        }
        for (int i = 0; i < NUM_NETWORK_ACTIVITY_TYPES; i++) {
            mNetworkByteActivityCounters[i].writeToParcel(out);
            mNetworkPacketActivityCounters[i].writeToParcel(out);
        }
        mMobileRadioActiveTimer.writeToParcel(out, uSecRealtime);
        mMobileRadioActivePerAppTimer.writeToParcel(out, uSecRealtime);
        mMobileRadioActiveAdjustedTime.writeToParcel(out);
        mMobileRadioActiveUnknownTime.writeToParcel(out);
        mMobileRadioActiveUnknownCount.writeToParcel(out);
        mWifiOnTimer.writeToParcel(out, uSecRealtime);
        mGlobalWifiRunningTimer.writeToParcel(out, uSecRealtime);
        for (int i=0; i<NUM_WIFI_STATES; i++) {
            mWifiStateTimer[i].writeToParcel(out, uSecRealtime);
        }
        for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
            mWifiSupplStateTimer[i].writeToParcel(out, uSecRealtime);
        }
        for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
            mWifiSignalStrengthsTimer[i].writeToParcel(out, uSecRealtime);
        }
        mWifiActivity.writeToParcel(out, 0);
        mBluetoothActivity.writeToParcel(out, 0);
        mModemActivity.writeToParcel(out, 0);
        out.writeInt(mHasWifiReporting ? 1 : 0);
        out.writeInt(mHasBluetoothReporting ? 1 : 0);
        out.writeInt(mHasModemReporting ? 1 : 0);

        out.writeInt(mNumConnectivityChange);
        out.writeInt(mLoadedNumConnectivityChange);
        out.writeInt(mUnpluggedNumConnectivityChange);
        mFlashlightOnTimer.writeToParcel(out, uSecRealtime);
        mCameraOnTimer.writeToParcel(out, uSecRealtime);
        mBluetoothScanTimer.writeToParcel(out, uSecRealtime);
        out.writeInt(mDischargeUnplugLevel);
        out.writeInt(mDischargePlugLevel);
        out.writeInt(mDischargeCurrentLevel);
        out.writeInt(mCurrentBatteryLevel);
        out.writeInt(mLowDischargeAmountSinceCharge);
        out.writeInt(mHighDischargeAmountSinceCharge);
        out.writeInt(mDischargeAmountScreenOn);
        out.writeInt(mDischargeAmountScreenOnSinceCharge);
        out.writeInt(mDischargeAmountScreenOff);
        out.writeInt(mDischargeAmountScreenOffSinceCharge);
        mDischargeStepTracker.writeToParcel(out);
        mChargeStepTracker.writeToParcel(out);
        mDischargeCounter.writeToParcel(out);
        mDischargeScreenOffCounter.writeToParcel(out);
        out.writeLong(mLastWriteTime);

        if (inclUids) {
            out.writeInt(mKernelWakelockStats.size());
            for (Map.Entry<String, SamplingTimer> ent : mKernelWakelockStats.entrySet()) {
                SamplingTimer kwlt = ent.getValue();
                if (kwlt != null) {
                    out.writeInt(1);
                    out.writeString(ent.getKey());
                    kwlt.writeToParcel(out, uSecRealtime);
                } else {
                    out.writeInt(0);
                }
            }
            out.writeInt(mWakeupReasonStats.size());
            for (Map.Entry<String, SamplingTimer> ent : mWakeupReasonStats.entrySet()) {
                SamplingTimer timer = ent.getValue();
                if (timer != null) {
                    out.writeInt(1);
                    out.writeString(ent.getKey());
                    timer.writeToParcel(out, uSecRealtime);
                } else {
                    out.writeInt(0);
                }
            }
        } else {
            out.writeInt(0);
        }

        if (inclUids) {
            int size = mUidStats.size();
            out.writeInt(size);
            for (int i = 0; i < size; i++) {
                out.writeInt(mUidStats.keyAt(i));
                Uid uid = mUidStats.valueAt(i);

                uid.writeToParcelLocked(out, uSecRealtime);
            }
        } else {
            out.writeInt(0);
        }
    }

    public static final Parcelable.Creator<BatteryStatsImpl> CREATOR =
        new Parcelable.Creator<BatteryStatsImpl>() {
        public BatteryStatsImpl createFromParcel(Parcel in) {
            return new BatteryStatsImpl(in);
        }

        public BatteryStatsImpl[] newArray(int size) {
            return new BatteryStatsImpl[size];
        }
    };

    public void prepareForDumpLocked() {
        // Need to retrieve current kernel wake lock stats before printing.
        pullPendingStateUpdatesLocked();

        // Pull the clock time.  This may update the time and make a new history entry
        // if we had originally pulled a time before the RTC was set.
        getStartClockTime();
    }

    public void dumpLocked(Context context, PrintWriter pw, int flags, int reqUid, long histStart) {
        if (DEBUG) {
            pw.println("mOnBatteryTimeBase:");
            mOnBatteryTimeBase.dump(pw, "  ");
            pw.println("mOnBatteryScreenOffTimeBase:");
            mOnBatteryScreenOffTimeBase.dump(pw, "  ");
            Printer pr = new PrintWriterPrinter(pw);
            pr.println("*** Screen timer:");
            mScreenOnTimer.logState(pr, "  ");
            for (int i=0; i<NUM_SCREEN_BRIGHTNESS_BINS; i++) {
                pr.println("*** Screen brightness #" + i + ":");
                mScreenBrightnessTimer[i].logState(pr, "  ");
            }
            pr.println("*** Interactive timer:");
            mInteractiveTimer.logState(pr, "  ");
            pr.println("*** Power save mode timer:");
            mPowerSaveModeEnabledTimer.logState(pr, "  ");
            pr.println("*** Device idle mode light timer:");
            mDeviceIdleModeLightTimer.logState(pr, "  ");
            pr.println("*** Device idle mode full timer:");
            mDeviceIdleModeFullTimer.logState(pr, "  ");
            pr.println("*** Device light idling timer:");
            mDeviceLightIdlingTimer.logState(pr, "  ");
            pr.println("*** Device idling timer:");
            mDeviceIdlingTimer.logState(pr, "  ");
            pr.println("*** Phone timer:");
            mPhoneOnTimer.logState(pr, "  ");
            for (int i=0; i<SignalStrength.NUM_SIGNAL_STRENGTH_BINS; i++) {
                pr.println("*** Phone signal strength #" + i + ":");
                mPhoneSignalStrengthsTimer[i].logState(pr, "  ");
            }
            pr.println("*** Signal scanning :");
            mPhoneSignalScanningTimer.logState(pr, "  ");
            for (int i=0; i<NUM_DATA_CONNECTION_TYPES; i++) {
                pr.println("*** Data connection type #" + i + ":");
                mPhoneDataConnectionsTimer[i].logState(pr, "  ");
            }
            pr.println("*** mMobileRadioPowerState=" + mMobileRadioPowerState);
            pr.println("*** Mobile network active timer:");
            mMobileRadioActiveTimer.logState(pr, "  ");
            pr.println("*** Mobile network active adjusted timer:");
            mMobileRadioActiveAdjustedTime.logState(pr, "  ");
            pr.println("*** mWifiRadioPowerState=" + mWifiRadioPowerState);
            pr.println("*** Wifi timer:");
            mWifiOnTimer.logState(pr, "  ");
            pr.println("*** WifiRunning timer:");
            mGlobalWifiRunningTimer.logState(pr, "  ");
            for (int i=0; i<NUM_WIFI_STATES; i++) {
                pr.println("*** Wifi state #" + i + ":");
                mWifiStateTimer[i].logState(pr, "  ");
            }
            for (int i=0; i<NUM_WIFI_SUPPL_STATES; i++) {
                pr.println("*** Wifi suppl state #" + i + ":");
                mWifiSupplStateTimer[i].logState(pr, "  ");
            }
            for (int i=0; i<NUM_WIFI_SIGNAL_STRENGTH_BINS; i++) {
                pr.println("*** Wifi signal strength #" + i + ":");
                mWifiSignalStrengthsTimer[i].logState(pr, "  ");
            }
            pr.println("*** Flashlight timer:");
            mFlashlightOnTimer.logState(pr, "  ");
            pr.println("*** Camera timer:");
            mCameraOnTimer.logState(pr, "  ");
        }
        super.dumpLocked(context, pw, flags, reqUid, histStart);
    }
}
