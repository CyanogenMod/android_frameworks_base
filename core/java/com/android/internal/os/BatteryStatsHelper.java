/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.BatteryStats;
import android.os.BatteryStats.Uid;
import android.os.Bundle;
import android.os.MemoryFile;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatterySipper.DrainType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A helper class for retrieving the power usage information for all applications and services.
 *
 * The caller must initialize this class as soon as activity object is ready to use (for example, in
 * onAttach() for Fragment), call create() in onCreate() and call destroy() in onDestroy().
 */
public final class BatteryStatsHelper {
    static final boolean DEBUG = false;

    private static final String TAG = BatteryStatsHelper.class.getSimpleName();

    private static BatteryStats sStatsXfer;
    private static BatteryStats sDockStatsXfer;
    private static Intent sBatteryBroadcastXfer;
    private static ArrayMap<File, BatteryStats> sFileXfer = new ArrayMap<>();

    final private Context mContext;
    final private BatteryManager mBatteryService;
    final private boolean mCollectBatteryBroadcast;
    final private boolean mWifiOnly;

    private IBatteryStats mBatteryInfo;
    private BatteryStats mStats;
    private BatteryStats mDockStats;
    private Intent mBatteryBroadcast;
    private PowerProfile mPowerProfile;

    /**
     * List of apps using power.
     */
    private final List<BatterySipper> mUsageList = new ArrayList<>();

    /**
     * List of apps using wifi power.
     */
    private final List<BatterySipper> mWifiSippers = new ArrayList<>();

    /**
     * List of apps using bluetooth power.
     */
    private final List<BatterySipper> mBluetoothSippers = new ArrayList<>();

    private final SparseArray<List<BatterySipper>> mUserSippers = new SparseArray<>();

    private final List<BatterySipper> mMobilemsppList = new ArrayList<>();

    private int mStatsType = BatteryStats.STATS_SINCE_CHARGED;

    long mRawRealtime;
    long mRawUptime;
    long mBatteryRealtime;
    long mBatteryUptime;
    long mTypeBatteryRealtime;
    long mTypeBatteryUptime;
    long mBatteryTimeRemaining;
    long mChargeTimeRemaining;

    private long mStatsPeriod = 0;

    // The largest entry by power.
    private double mMaxPower = 1;

    // The largest real entry by power (not undercounted or overcounted).
    private double mMaxRealPower = 1;

    // Total computed power.
    private double mComputedPower;
    private double mTotalPower;
    private double mMinDrainedPower;
    private double mMaxDrainedPower;

    PowerCalculator mCpuPowerCalculator;
    PowerCalculator mWakelockPowerCalculator;
    MobileRadioPowerCalculator mMobileRadioPowerCalculator;
    PowerCalculator mWifiPowerCalculator;
    PowerCalculator mBluetoothPowerCalculator;
    PowerCalculator mSensorPowerCalculator;
    PowerCalculator mCameraPowerCalculator;
    PowerCalculator mFlashlightPowerCalculator;

    boolean mHasWifiPowerReporting = false;
    boolean mHasBluetoothPowerReporting = false;

    public static boolean checkWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return !cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
    }

    public static boolean checkHasWifiPowerReporting(BatteryStats stats, PowerProfile profile) {
        return stats.hasWifiActivityReporting() &&
                profile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_IDLE) != 0 &&
                profile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_RX) != 0 &&
                profile.getAveragePower(PowerProfile.POWER_WIFI_CONTROLLER_TX) != 0;
    }

    public static boolean checkHasBluetoothPowerReporting(BatteryStats stats,
                                                          PowerProfile profile) {
        return stats.hasBluetoothActivityReporting() &&
                profile.getAveragePower(PowerProfile.POWER_BLUETOOTH_CONTROLLER_IDLE) != 0 &&
                profile.getAveragePower(PowerProfile.POWER_BLUETOOTH_CONTROLLER_RX) != 0 &&
                profile.getAveragePower(PowerProfile.POWER_BLUETOOTH_CONTROLLER_TX) != 0;
    }

    public BatteryStatsHelper(Context context) {
        this(context, true);
    }

    public BatteryStatsHelper(Context context, boolean collectBatteryBroadcast) {
        this(context, collectBatteryBroadcast, checkWifiOnly(context));
    }

    public BatteryStatsHelper(Context context, boolean collectBatteryBroadcast, boolean wifiOnly) {
        mContext = context;
        mBatteryService = ((BatteryManager) context.getSystemService(Context.BATTERY_SERVICE));
        mCollectBatteryBroadcast = collectBatteryBroadcast;
        mWifiOnly = wifiOnly;
    }

    public void storeStatsHistoryInFile(String fname) {
        internalStoreStatsHistoryInFile(getStats(), fname);
    }

    public void storeDockStatsHistoryInFile(String fname) {
        internalStoreStatsHistoryInFile(getDockStats(), fname);
    }

    public void internalStoreStatsHistoryInFile(BatteryStats stats, String fname) {
        synchronized (sFileXfer) {
            File path = makeFilePath(mContext, fname);
            sFileXfer.put(path, stats);
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(path);
                Parcel hist = Parcel.obtain();
                stats.writeToParcelWithoutUids(hist, 0);
                byte[] histData = hist.marshall();
                fout.write(histData);
            } catch (IOException e) {
                Log.w(TAG, "Unable to write history to file", e);
            } finally {
                if (fout != null) {
                    try {
                        fout.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    public static BatteryStats statsFromFile(Context context, String fname) {
        synchronized (sFileXfer) {
            File path = makeFilePath(context, fname);
            BatteryStats stats = sFileXfer.get(path);
            if (stats != null) {
                return stats;
            }
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(path);
                byte[] data = readFully(fin);
                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(data, 0, data.length);
                parcel.setDataPosition(0);
                return com.android.internal.os.BatteryStatsImpl.CREATOR.createFromParcel(parcel);
            } catch (IOException e) {
                Log.w(TAG, "Unable to read history to file", e);
            } finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return getStats(IBatteryStats.Stub.asInterface(
                        ServiceManager.getService(BatteryStats.SERVICE_NAME)));
    }

    public static void dropFile(Context context, String fname) {
        makeFilePath(context, fname).delete();
    }

    private static File makeFilePath(Context context, String fname) {
        return new File(context.getFilesDir(), fname);
    }

    /** Clears the current stats and forces recreating for future use. */
    public void clearStats() {
        mStats = null;
        mDockStats = null;
    }

    private void clearAllStats() {
        clearStats();
        sStatsXfer = null;
        sDockStatsXfer = null;
        sBatteryBroadcastXfer = null;
        for (File f : sFileXfer.keySet()) {
            f.delete();
        }
        sFileXfer.clear();
    }

    public BatteryStats getStats() {
        if (mStats == null) {
            loadStats();
        }
        return mStats;
    }

    public BatteryStats getDockStats() {
        if (mDockStats == null) {
            loadDockStats();
        }
        return mDockStats;
    }

    public Intent getBatteryBroadcast() {
        if (mBatteryBroadcast == null && mCollectBatteryBroadcast) {
            loadStats();
            loadDockStats();
        }
        return mBatteryBroadcast;
    }

    public PowerProfile getPowerProfile() {
        return mPowerProfile;
    }

    public void create(BatteryStats stats) {
        mPowerProfile = new PowerProfile(mContext);
        mStats = stats;
    }

    public void create(Bundle icicle) {
        if (icicle != null) {
            mStats = sStatsXfer;
            mDockStats = sDockStatsXfer;
            mBatteryBroadcast = sBatteryBroadcastXfer;
        }
        mBatteryInfo = IBatteryStats.Stub.asInterface(
                ServiceManager.getService(BatteryStats.SERVICE_NAME));
        mPowerProfile = new PowerProfile(mContext);
    }

    public void storeState() {
        sStatsXfer = mStats;
        sDockStatsXfer = mDockStats;
        sBatteryBroadcastXfer = mBatteryBroadcast;
    }

    public static String makemAh(double power) {
        if (power == 0) return "0";

        final String format;
        if (power < .00001) format = "%.8f";
        else if (power < .0001) format = "%.7f";
        else if (power < .001) format = "%.6f";
        else if (power < .01) format = "%.5f";
        else if (power < .1) format = "%.4f";
        else if (power < 1) format = "%.3f";
        else if (power < 10) format = "%.2f";
        else if (power < 100) format = "%.1f";
        else format = "%.0f";

        // Use English locale because this is never used in UI (only in checkin and dump).
        return String.format(Locale.ENGLISH, format, power);
    }

    /**
     * Refreshes the power usage list.
     */
    public void refreshStats(int statsType, int asUser) {
        SparseArray<UserHandle> users = new SparseArray<>(1);
        users.put(asUser, new UserHandle(asUser));
        refreshStats(statsType, users);
    }

    /**
     * Refreshes the power usage list.
     */
    public void refreshStats(int statsType, List<UserHandle> asUsers) {
        final int n = asUsers.size();
        SparseArray<UserHandle> users = new SparseArray<>(n);
        for (int i = 0; i < n; ++i) {
            UserHandle userHandle = asUsers.get(i);
            users.put(userHandle.getIdentifier(), userHandle);
        }
        refreshStats(statsType, users);
    }

    /**
     * Refreshes the power usage list.
     */
    public void refreshStats(int statsType, SparseArray<UserHandle> asUsers) {
        refreshStats(statsType, asUsers, SystemClock.elapsedRealtime() * 1000,
                SystemClock.uptimeMillis() * 1000);
    }

    public void refreshStats(int statsType, SparseArray<UserHandle> asUsers, long rawRealtimeUs,
            long rawUptimeUs) {
        // Initialize mStats if necessary.
        getStats();
        getDockStats();

        mMaxPower = 0;
        mMaxRealPower = 0;
        mComputedPower = 0;
        mTotalPower = 0;

        mUsageList.clear();
        mWifiSippers.clear();
        mBluetoothSippers.clear();
        mUserSippers.clear();
        mMobilemsppList.clear();

        if (mStats == null) {
            return;
        }

        if (mCpuPowerCalculator == null) {
            mCpuPowerCalculator = new CpuPowerCalculator(mPowerProfile);
        }
        mCpuPowerCalculator.reset();

        if (mWakelockPowerCalculator == null) {
            mWakelockPowerCalculator = new WakelockPowerCalculator(mPowerProfile);
        }
        mWakelockPowerCalculator.reset();

        if (mMobileRadioPowerCalculator == null) {
            mMobileRadioPowerCalculator = new MobileRadioPowerCalculator(mPowerProfile, mStats);
        }
        mMobileRadioPowerCalculator.reset(mStats);

        // checkHasWifiPowerReporting can change if we get energy data at a later point, so
        // always check this field.
        final boolean hasWifiPowerReporting = checkHasWifiPowerReporting(mStats, mPowerProfile);
        if (mWifiPowerCalculator == null || hasWifiPowerReporting != mHasWifiPowerReporting) {
            mWifiPowerCalculator = hasWifiPowerReporting ?
                    new WifiPowerCalculator(mPowerProfile) :
                    new WifiPowerEstimator(mPowerProfile);
            mHasWifiPowerReporting = hasWifiPowerReporting;
        }
        mWifiPowerCalculator.reset();

        final boolean hasBluetoothPowerReporting = checkHasBluetoothPowerReporting(mStats,
                                                                                   mPowerProfile);
        if (mBluetoothPowerCalculator == null ||
                hasBluetoothPowerReporting != mHasBluetoothPowerReporting) {
            mBluetoothPowerCalculator = new BluetoothPowerCalculator(mPowerProfile);
            mHasBluetoothPowerReporting = hasBluetoothPowerReporting;
        }
        mBluetoothPowerCalculator.reset();

        if (mSensorPowerCalculator == null) {
            mSensorPowerCalculator = new SensorPowerCalculator(mPowerProfile,
                    (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE));
        }
        mSensorPowerCalculator.reset();

        if (mCameraPowerCalculator == null) {
            mCameraPowerCalculator = new CameraPowerCalculator(mPowerProfile);
        }
        mCameraPowerCalculator.reset();

        if (mFlashlightPowerCalculator == null) {
            mFlashlightPowerCalculator = new FlashlightPowerCalculator(mPowerProfile);
        }
        mFlashlightPowerCalculator.reset();

        mStatsType = statsType;
        mRawUptime = rawUptimeUs;
        mRawRealtime = rawRealtimeUs;
        mBatteryUptime = mStats.getBatteryUptime(rawUptimeUs);
        mBatteryRealtime = mStats.getBatteryRealtime(rawRealtimeUs);
        mTypeBatteryUptime = mStats.computeBatteryUptime(rawUptimeUs, mStatsType);
        mTypeBatteryRealtime = mStats.computeBatteryRealtime(rawRealtimeUs, mStatsType);
        mBatteryTimeRemaining = mStats.computeBatteryTimeRemaining(rawRealtimeUs);
        mChargeTimeRemaining = mStats.computeChargeTimeRemaining(rawRealtimeUs);

        if (DEBUG) {
            Log.d(TAG, "Raw time: realtime=" + (rawRealtimeUs/1000) + " uptime="
                    + (rawUptimeUs/1000));
            Log.d(TAG, "Battery time: realtime=" + (mBatteryRealtime/1000) + " uptime="
                    + (mBatteryUptime/1000));
            Log.d(TAG, "Battery type time: realtime=" + (mTypeBatteryRealtime/1000) + " uptime="
                    + (mTypeBatteryUptime/1000));
        }
        mMinDrainedPower = (mStats.getLowDischargeAmountSinceCharge()
                * mPowerProfile.getBatteryCapacity()) / 100;
        mMaxDrainedPower = (mStats.getHighDischargeAmountSinceCharge()
                * mPowerProfile.getBatteryCapacity()) / 100;

        processAppUsage(asUsers);

        // Before aggregating apps in to users, collect all apps to sort by their ms per packet.
        for (int i=0; i<mUsageList.size(); i++) {
            BatterySipper bs = mUsageList.get(i);
            bs.computeMobilemspp();
            if (bs.mobilemspp != 0) {
                mMobilemsppList.add(bs);
            }
        }

        for (int i=0; i<mUserSippers.size(); i++) {
            List<BatterySipper> user = mUserSippers.valueAt(i);
            for (int j=0; j<user.size(); j++) {
                BatterySipper bs = user.get(j);
                bs.computeMobilemspp();
                if (bs.mobilemspp != 0) {
                    mMobilemsppList.add(bs);
                }
            }
        }
        Collections.sort(mMobilemsppList, new Comparator<BatterySipper>() {
            @Override
            public int compare(BatterySipper lhs, BatterySipper rhs) {
                return Double.compare(rhs.mobilemspp, lhs.mobilemspp);
            }
        });

        processMiscUsage();

        Collections.sort(mUsageList);

        // At this point, we've sorted the list so we are guaranteed the max values are at the top.
        // We have only added real powers so far.
        if (!mUsageList.isEmpty()) {
            mMaxRealPower = mMaxPower = mUsageList.get(0).totalPowerMah;
            final int usageListCount = mUsageList.size();
            for (int i = 0; i < usageListCount; i++) {
                mComputedPower += mUsageList.get(i).totalPowerMah;
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Accuracy: total computed=" + makemAh(mComputedPower) + ", min discharge="
                    + makemAh(mMinDrainedPower) + ", max discharge=" + makemAh(mMaxDrainedPower));
        }

        mTotalPower = mComputedPower;
        if (mStats.getLowDischargeAmountSinceCharge() > 1) {
            if (mMinDrainedPower > mComputedPower) {
                double amount = mMinDrainedPower - mComputedPower;
                mTotalPower = mMinDrainedPower;
                BatterySipper bs = new BatterySipper(DrainType.UNACCOUNTED, null, amount);

                // Insert the BatterySipper in its sorted position.
                int index = Collections.binarySearch(mUsageList, bs);
                if (index < 0) {
                    index = -(index + 1);
                }
                mUsageList.add(index, bs);
                mMaxPower = Math.max(mMaxPower, amount);
            } else if (mMaxDrainedPower < mComputedPower) {
                double amount = mComputedPower - mMaxDrainedPower;

                // Insert the BatterySipper in its sorted position.
                BatterySipper bs = new BatterySipper(DrainType.OVERCOUNTED, null, amount);
                int index = Collections.binarySearch(mUsageList, bs);
                if (index < 0) {
                    index = -(index + 1);
                }
                mUsageList.add(index, bs);
                mMaxPower = Math.max(mMaxPower, amount);
            }
        }
    }

    private void processAppUsage(SparseArray<UserHandle> asUsers) {
        final boolean forAllUsers = (asUsers.get(UserHandle.USER_ALL) != null);
        mStatsPeriod = mTypeBatteryRealtime;

        BatterySipper osSipper = null;
        final SparseArray<? extends Uid> uidStats = mStats.getUidStats();
        final int NU = uidStats.size();
        for (int iu = 0; iu < NU; iu++) {
            final Uid u = uidStats.valueAt(iu);
            final BatterySipper app = new BatterySipper(BatterySipper.DrainType.APP, u, 0);

            mCpuPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mWakelockPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mMobileRadioPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mWifiPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mBluetoothPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mSensorPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mCameraPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);
            mFlashlightPowerCalculator.calculateApp(app, u, mRawRealtime, mRawUptime, mStatsType);

            final double totalPower = app.sumPower();
            if (DEBUG && totalPower != 0) {
                Log.d(TAG, String.format("UID %d: total power=%s", u.getUid(),
                        makemAh(totalPower)));
            }

            // Add the app to the list if it is consuming power.
            if (totalPower != 0 || u.getUid() == 0) {
                //
                // Add the app to the app list, WiFi, Bluetooth, etc, or into "Other Users" list.
                //
                final int uid = app.getUid();
                final int userId = UserHandle.getUserId(uid);
                if (uid == Process.WIFI_UID) {
                    mWifiSippers.add(app);
                } else if (uid == Process.BLUETOOTH_UID) {
                    mBluetoothSippers.add(app);
                } else if (!forAllUsers && asUsers.get(userId) == null
                        && UserHandle.getAppId(uid) >= Process.FIRST_APPLICATION_UID) {
                    // We are told to just report this user's apps as one large entry.
                    List<BatterySipper> list = mUserSippers.get(userId);
                    if (list == null) {
                        list = new ArrayList<>();
                        mUserSippers.put(userId, list);
                    }
                    list.add(app);
                } else {
                    mUsageList.add(app);
                }

                if (uid == 0) {
                    osSipper = app;
                }
            }
        }

        if (osSipper != null) {
            // The device has probably been awake for longer than the screen on
            // time and application wake lock time would account for.  Assign
            // this remainder to the OS, if possible.
            mWakelockPowerCalculator.calculateRemaining(osSipper, mStats, mRawRealtime,
                                                        mRawUptime, mStatsType);
            osSipper.sumPower();
        }
    }

    private void addPhoneUsage() {
        long phoneOnTimeMs = mStats.getPhoneOnTime(mRawRealtime, mStatsType) / 1000;
        double phoneOnPower = mPowerProfile.getAveragePower(PowerProfile.POWER_RADIO_ACTIVE)
                * phoneOnTimeMs / (60*60*1000);
        if (phoneOnPower != 0) {
            addEntry(BatterySipper.DrainType.PHONE, phoneOnTimeMs, phoneOnPower);
        }
    }

    private void addScreenUsage() {
        double power = 0;
        long screenOnTimeMs = mStats.getScreenOnTime(mRawRealtime, mStatsType) / 1000;
        power += screenOnTimeMs * mPowerProfile.getAveragePower(PowerProfile.POWER_SCREEN_ON);
        final double screenFullPower =
                mPowerProfile.getAveragePower(PowerProfile.POWER_SCREEN_FULL);
        for (int i = 0; i < BatteryStats.NUM_SCREEN_BRIGHTNESS_BINS; i++) {
            double screenBinPower = screenFullPower * (i + 0.5f)
                    / BatteryStats.NUM_SCREEN_BRIGHTNESS_BINS;
            long brightnessTime = mStats.getScreenBrightnessTime(i, mRawRealtime, mStatsType)
                    / 1000;
            double p = screenBinPower*brightnessTime;
            if (DEBUG && p != 0) {
                Log.d(TAG, "Screen bin #" + i + ": time=" + brightnessTime
                        + " power=" + makemAh(p / (60 * 60 * 1000)));
            }
            power += p;
        }
        power /= (60*60*1000); // To hours
        if (power != 0) {
            addEntry(BatterySipper.DrainType.SCREEN, screenOnTimeMs, power);
        }
    }

    private void addRadioUsage() {
        BatterySipper radio = new BatterySipper(BatterySipper.DrainType.CELL, null, 0);
        mMobileRadioPowerCalculator.calculateRemaining(radio, mStats, mRawRealtime, mRawUptime,
                mStatsType);
        radio.sumPower();
        if (radio.totalPowerMah > 0) {
            mUsageList.add(radio);
        }
    }

    private void aggregateSippers(BatterySipper bs, List<BatterySipper> from, String tag) {
        for (int i=0; i<from.size(); i++) {
            BatterySipper wbs = from.get(i);
            if (DEBUG) Log.d(TAG, tag + " adding sipper " + wbs + ": cpu=" + wbs.cpuTimeMs);
            bs.add(wbs);
        }
        bs.computeMobilemspp();
        bs.sumPower();
    }

    private void addIdleUsage() {
        long idleTimeMs = (mTypeBatteryRealtime
                - mStats.getScreenOnTime(mRawRealtime, mStatsType)) / 1000;
        double idlePower = (idleTimeMs * mPowerProfile.getAveragePower(PowerProfile.POWER_CPU_IDLE))
                / (60*60*1000);
        if (DEBUG && idlePower != 0) {
            Log.d(TAG, "Idle: time=" + idleTimeMs + " power=" + makemAh(idlePower));
        }
        if (idlePower != 0) {
            addEntry(BatterySipper.DrainType.IDLE, idleTimeMs, idlePower);
        }
    }

    /**
     * We do per-app blaming of WiFi activity. If energy info is reported from the controller,
     * then only the WiFi process gets blamed here since we normalize power calculations and
     * assign all the power drain to apps. If energy info is not reported, we attribute the
     * difference between total running time of WiFi for all apps and the actual running time
     * of WiFi to the WiFi subsystem.
     */
    private void addWiFiUsage() {
        BatterySipper bs = new BatterySipper(DrainType.WIFI, null, 0);
        mWifiPowerCalculator.calculateRemaining(bs, mStats, mRawRealtime, mRawUptime, mStatsType);
        aggregateSippers(bs, mWifiSippers, "WIFI");
        if (bs.totalPowerMah > 0) {
            mUsageList.add(bs);
        }
    }

    /**
     * Bluetooth usage is not attributed to any apps yet, so the entire blame goes to the
     * Bluetooth Category.
     */
    private void addBluetoothUsage() {
        BatterySipper bs = new BatterySipper(BatterySipper.DrainType.BLUETOOTH, null, 0);
        mBluetoothPowerCalculator.calculateRemaining(bs, mStats, mRawRealtime, mRawUptime,
                mStatsType);
        aggregateSippers(bs, mBluetoothSippers, "Bluetooth");
        if (bs.totalPowerMah > 0) {
            mUsageList.add(bs);
        }
    }

    private void addUserUsage() {
        for (int i = 0; i < mUserSippers.size(); i++) {
            final int userId = mUserSippers.keyAt(i);
            BatterySipper bs = new BatterySipper(DrainType.USER, null, 0);
            bs.userId = userId;
            aggregateSippers(bs, mUserSippers.valueAt(i), "User");
            mUsageList.add(bs);
        }
    }

    private void processMiscUsage() {
        addUserUsage();
        addPhoneUsage();
        addScreenUsage();
        addWiFiUsage();
        addBluetoothUsage();
        addIdleUsage(); // Not including cellular idle power
        // Don't compute radio usage if it's a wifi-only device
        if (!mWifiOnly) {
            addRadioUsage();
        }
    }

    private BatterySipper addEntry(DrainType drainType, long time, double power) {
        BatterySipper bs = new BatterySipper(drainType, null, 0);
        bs.usagePowerMah = power;
        bs.usageTimeMs = time;
        bs.sumPower();
        mUsageList.add(bs);
        return bs;
    }

    public List<BatterySipper> getUsageList() {
        return mUsageList;
    }

    public List<BatterySipper> getMobilemsppList() {
        return mMobilemsppList;
    }

    public long getStatsPeriod() { return mStatsPeriod; }

    public int getStatsType() { return mStatsType; }

    public double getMaxPower() { return mMaxPower; }

    public double getMaxRealPower() { return mMaxRealPower; }

    public double getTotalPower() { return mTotalPower; }

    public double getComputedPower() { return mComputedPower; }

    public double getMinDrainedPower() {
        return mMinDrainedPower;
    }

    public double getMaxDrainedPower() {
        return mMaxDrainedPower;
    }

    public long getBatteryTimeRemaining() { return mBatteryTimeRemaining; }

    public long getChargeTimeRemaining() { return mChargeTimeRemaining; }

    public static byte[] readFully(FileInputStream stream) throws java.io.IOException {
        return readFully(stream, stream.available());
    }

    public static byte[] readFully(FileInputStream stream, int avail) throws java.io.IOException {
        int pos = 0;
        byte[] data = new byte[avail];
        while (true) {
            int amt = stream.read(data, pos, data.length-pos);
            //Log.i("foo", "Read " + amt + " bytes at " + pos
            //        + " of avail " + data.length);
            if (amt <= 0) {
                //Log.i("foo", "**** FINISHED READING: pos=" + pos
                //        + " len=" + data.length);
                return data;
            }
            pos += amt;
            avail = stream.available();
            if (avail > data.length-pos) {
                byte[] newData = new byte[pos+avail];
                System.arraycopy(data, 0, newData, 0, pos);
                data = newData;
            }
        }
    }

    private void loadStats() {
        if (mBatteryInfo == null) {
            return;
        }
        mStats = getStats(mBatteryInfo);
        if (mCollectBatteryBroadcast) {
            mBatteryBroadcast = mContext.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }

    private void loadDockStats() {
        if (mBatteryInfo == null) {
            return;
        }
        if (mBatteryService.isDockBatterySupported()) {
            mDockStats = getDockStats(mBatteryInfo);
        } else {
            mDockStats = null;
        }
    }

    public void resetStatistics() {
        try {
            clearAllStats();
            mBatteryInfo.resetStatistics();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:", e);
        }
    }

    private static BatteryStatsImpl getStats(IBatteryStats service) {
        try {
            ParcelFileDescriptor pfd = service.getStatisticsStream();
            if (pfd != null) {
                FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                try {
                    byte[] data = readFully(fis, MemoryFile.getSize(pfd.getFileDescriptor()));
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(data, 0, data.length);
                    parcel.setDataPosition(0);
                    BatteryStatsImpl stats = com.android.internal.os.BatteryStatsImpl.CREATOR
                            .createFromParcel(parcel);
                    return stats;
                } catch (IOException e) {
                    Log.w(TAG, "Unable to read statistics stream", e);
                }
            }
        } catch (RemoteException e) {
            Log.w(TAG, "RemoteException:", e);
        }
        return new BatteryStatsImpl();
    }

    private static BatteryStatsImpl getDockStats(IBatteryStats service) {
        try {
            ParcelFileDescriptor pfd = service.getDockStatisticsStream();
            if (pfd != null) {
                FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                try {
                    byte[] data = readFully(fis, MemoryFile.getSize(pfd.getFileDescriptor()));
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(data, 0, data.length);
                    parcel.setDataPosition(0);
                    BatteryStatsImpl stats = com.android.internal.os.DockBatteryStatsImpl.CREATOR
                            .createFromParcel(parcel);
                    return stats;
                } catch (IOException e) {
                    Log.w(TAG, "Unable to read statistics stream", e);
                }
            }
        } catch (RemoteException e) {
            Log.w(TAG, "RemoteException:", e);
        }
        return new BatteryStatsImpl();
    }
}
