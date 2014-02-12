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

package com.android.server.am;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryStats;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.WorkSource;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatteryStatsImpl;
import com.android.internal.os.DockBatteryStatsImpl;
import com.android.internal.os.PowerProfile;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * All information we are collecting about things that can happen that impact
 * battery life.
 */
public final class BatteryStatsService extends IBatteryStats.Stub {
    static IBatteryStats sService;
    
    final BatteryStatsImpl mStats;
    // The dock stats only collect statistics about battery (no wakelocks, no counters, ...),
    // just the dock battery history
    final BatteryStatsImpl mDockStats;
    Context mContext;
    private boolean mBluetoothPendingStats;
    private BluetoothHeadset mBluetoothHeadset;

    BatteryStatsService(String filename, String dockfilename) {
        mStats = new BatteryStatsImpl(filename);
        mDockStats = new DockBatteryStatsImpl(dockfilename);
    }
    
    public void publish(Context context) {
        mContext = context;
        ServiceManager.addService(BatteryStats.SERVICE_NAME, asBinder());
        mStats.setNumSpeedSteps(new PowerProfile(mContext).getNumSpeedSteps());
        mStats.setRadioScanningTimeout(mContext.getResources().getInteger(
                com.android.internal.R.integer.config_radioScanningTimeout)
                * 1000L);
    }
    
    public void shutdown() {
        Slog.w("BatteryStats", "Writing battery stats before shutdown...");
        synchronized (mStats) {
            mStats.shutdownLocked();
        }
        synchronized (mDockStats) {
            mDockStats.shutdownLocked();
        }
    }
    
    public static IBatteryStats getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(BatteryStats.SERVICE_NAME);
        sService = asInterface(b);
        return sService;
    }
    
    /**
     * @return the current statistics object, which may be modified
     * to reflect events that affect battery usage.  You must lock the
     * stats object before doing anything with it.
     */
    public BatteryStatsImpl getActiveStatistics() {
        return mStats;
    }
    
    public byte[] getStatistics() {
        mContext.enforceCallingPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        //Slog.i("foo", "SENDING BATTERY INFO:");
        //mStats.dumpLocked(new LogPrinter(Log.INFO, "foo", Log.LOG_ID_SYSTEM));
        Parcel out = Parcel.obtain();
        mStats.writeToParcel(out, 0);
        byte[] data = out.marshall();
        out.recycle();
        return data;
    }
    
    /**
     * @hide
     */
    @Override
    public byte[] getDockStatistics() {
        mContext.enforceCallingPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        //Slog.i("foo", "SENDING BATTERY INFO:");
        //mStats.dumpLocked(new LogPrinter(Log.INFO, "foo", Log.LOG_ID_SYSTEM));
        Parcel out = Parcel.obtain();
        mDockStats.writeToParcel(out, 0);
        byte[] data = out.marshall();
        out.recycle();
        return data;
    }

    /**
     * @hide
     */
    @Override
    public void resetStatistics() {
        mContext.enforceCallingPermission(
                android.Manifest.permission.RESET_BATTERY_STATS, null);
        synchronized (mStats) {
            mStats.resetAllStatsLocked();
        }
        synchronized (mDockStats) {
            mDockStats.resetAllStatsLocked();
        }
    }

    public void noteStartWakelock(int uid, int pid, String name, int type) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStartWakeLocked(uid, pid, name, type);
        }
    }

    public void noteStopWakelock(int uid, int pid, String name, int type) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStopWakeLocked(uid, pid, name, type);
        }
    }

    public void noteStartWakelockFromSource(WorkSource ws, int pid, String name, int type) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStartWakeFromSourceLocked(ws, pid, name, type);
        }
    }

    public void noteStopWakelockFromSource(WorkSource ws, int pid, String name, int type) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStopWakeFromSourceLocked(ws, pid, name, type);
        }
    }

    public void noteStartSensor(int uid, int sensor) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStartSensorLocked(uid, sensor);
        }
    }
    
    public void noteStopSensor(int uid, int sensor) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStopSensorLocked(uid, sensor);
        }
    }
    
    public void noteVibratorOn(int uid, long durationMillis) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteVibratorOnLocked(uid, durationMillis);
        }
    }

    public void noteVibratorOff(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteVibratorOffLocked(uid);
        }
    }

    public void noteStartGps(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStartGpsLocked(uid);
        }
    }
    
    public void noteStopGps(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteStopGpsLocked(uid);
        }
    }
        
    public void noteScreenOn() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteScreenOnLocked();
        }
    }
    
    public void noteScreenBrightness(int brightness) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteScreenBrightnessLocked(brightness);
        }
    }
    
    public void noteScreenOff() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteScreenOffLocked();
        }
    }

    public void noteInputEvent() {
        enforceCallingPermission();
        mStats.noteInputEventAtomic();
    }
    
    public void noteUserActivity(int uid, int event) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteUserActivityLocked(uid, event);
        }
    }
    
    public void notePhoneOn() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.notePhoneOnLocked();
        }
    }
    
    public void notePhoneOff() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.notePhoneOffLocked();
        }
    }
    
    public void notePhoneSignalStrength(SignalStrength signalStrength) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.notePhoneSignalStrengthLocked(signalStrength);
        }
    }
    
    public void notePhoneDataConnectionState(int dataType, boolean hasData) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.notePhoneDataConnectionStateLocked(dataType, hasData);
        }
    }

    public void notePhoneState(int state) {
        enforceCallingPermission();
        int simState = TelephonyManager.getDefault().getSimState();
        synchronized (mStats) {
            mStats.notePhoneStateLocked(state, simState);
        }
    }

    public void noteWifiOn() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiOnLocked();
        }
    }
    
    public void noteWifiOff() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiOffLocked();
        }
    }

    public void noteStartAudio(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteAudioOnLocked(uid);
        }
    }

    public void noteStopAudio(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteAudioOffLocked(uid);
        }
    }

    public void noteStartVideo(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteVideoOnLocked(uid);
        }
    }

    public void noteStopVideo(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteVideoOffLocked(uid);
        }
    }

    public void noteWifiRunning(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiRunningLocked(ws);
        }
    }

    public void noteWifiRunningChanged(WorkSource oldWs, WorkSource newWs) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiRunningChangedLocked(oldWs, newWs);
        }
    }

    public void noteWifiStopped(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiStoppedLocked(ws);
        }
    }

    public void noteBluetoothOn() {
        enforceCallingPermission();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(mContext, mBluetoothProfileServiceListener,
                                    BluetoothProfile.HEADSET);
        }
        synchronized (mStats) {
            if (mBluetoothHeadset != null) {
                mStats.noteBluetoothOnLocked();
                mStats.setBtHeadset(mBluetoothHeadset);
            } else {
                mBluetoothPendingStats = true;
            }
        }
    }

    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothHeadset = (BluetoothHeadset) proxy;
            synchronized (mStats) {
                if (mBluetoothPendingStats) {
                    mStats.noteBluetoothOnLocked();
                    mStats.setBtHeadset(mBluetoothHeadset);
                    mBluetoothPendingStats = false;
                }
            }
        }

        public void onServiceDisconnected(int profile) {
            mBluetoothHeadset = null;
        }
    };

    public void noteBluetoothOff() {
        enforceCallingPermission();
        synchronized (mStats) {
            mBluetoothPendingStats = false;
            mStats.noteBluetoothOffLocked();
            mStats.setBtHeadset(null);
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && mBluetoothHeadset != null) {
            adapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mBluetoothHeadset = null;
        }
    }
    
    public void noteFullWifiLockAcquired(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteFullWifiLockAcquiredLocked(uid);
        }
    }
    
    public void noteFullWifiLockReleased(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteFullWifiLockReleasedLocked(uid);
        }
    }

    public void noteWifiScanStarted(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiScanStartedLocked(uid);
        }
    }

    public void noteWifiScanStopped(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiScanStoppedLocked(uid);
        }
    }

    public void noteWifiMulticastEnabled(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiMulticastEnabledLocked(uid);
        }
    }

    public void noteWifiMulticastDisabled(int uid) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiMulticastDisabledLocked(uid);
        }
    }

    public void noteFullWifiLockAcquiredFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteFullWifiLockAcquiredFromSourceLocked(ws);
        }
    }

    public void noteFullWifiLockReleasedFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteFullWifiLockReleasedFromSourceLocked(ws);
        }
    }

    public void noteWifiScanStartedFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiScanStartedFromSourceLocked(ws);
        }
    }

    public void noteWifiScanStoppedFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiScanStoppedFromSourceLocked(ws);
        }
    }

    public void noteWifiBatchedScanStartedFromSource(WorkSource ws, int csph) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiBatchedScanStartedFromSourceLocked(ws, csph);
        }
    }

    public void noteWifiBatchedScanStoppedFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiBatchedScanStoppedFromSourceLocked(ws);
        }
    }

    public void noteWifiMulticastEnabledFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiMulticastEnabledFromSourceLocked(ws);
        }
    }

    public void noteWifiMulticastDisabledFromSource(WorkSource ws) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteWifiMulticastDisabledFromSourceLocked(ws);
        }
    }

    @Override
    public void noteNetworkInterfaceType(String iface, int type) {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteNetworkInterfaceTypeLocked(iface, type);
        }
    }

    @Override
    public void noteNetworkStatsEnabled() {
        enforceCallingPermission();
        synchronized (mStats) {
            mStats.noteNetworkStatsEnabledLocked();
        }
    }

    public boolean isOnBattery() {
        return mStats.isOnBattery();
    }
    
    public void setBatteryState(int status, int health, int plugType, int level,
            int temp, int volt) {
        enforceCallingPermission();
        mStats.setBatteryState(status, health, plugType, level, temp, volt);
    }
    
    public long getAwakeTimeBattery() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        return mStats.getAwakeTimeBattery();
    }

    public long getAwakeTimePlugged() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        return mStats.getAwakeTimePlugged();
    }

    /** @hide **/
    public boolean isDockOnBattery() {
        return mDockStats.isOnBattery();
    }

    /** @hide **/
    public void setDockBatteryState(int status, int health, int plugType, int level,
            int temp, int volt) {
        enforceCallingPermission();
        mDockStats.setBatteryState(status, health, plugType, level, temp, volt);
    }

    /** @hide **/
    public long getDockAwakeTimeBattery() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        return mDockStats.getAwakeTimeBattery();
    }

    /** @hide **/
    public long getDockAwakeTimePlugged() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BATTERY_STATS, null);
        return mDockStats.getAwakeTimePlugged();
    }

    public void enforceCallingPermission() {
        if (Binder.getCallingPid() == Process.myPid()) {
            return;
        }
        mContext.enforcePermission(android.Manifest.permission.UPDATE_DEVICE_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
    }
    
    private void dumpHelp(PrintWriter pw) {
        pw.println("Battery stats (batterystats) dump options:");
        pw.println("  [--checkin] [-c] [--unplugged] [--reset] [--write] [-h] [<package.name>]");
        pw.println("  --checkin: format output for a checkin report.");
        pw.println("  --unplugged: only output data since last unplugged.");
        pw.println("  --reset: reset the stats, clearing all current data.");
        pw.println("  --write: force write current collected stats to disk.");
        pw.println("  -h: print this help text.");
        pw.println("  <package.name>: optional name of package to filter output by.");
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump BatteryStats from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }

        boolean isCheckin = false;
        boolean includeHistory = false;
        boolean isUnpluggedOnly = false;
        boolean noOutput = false;
        int reqUid = -1;
        if (args != null) {
            for (String arg : args) {
                if ("--checkin".equals(arg)) {
                    isCheckin = true;
                } else if ("-c".equals(arg)) {
                    isCheckin = true;
                    includeHistory = true;
                } else if ("--unplugged".equals(arg)) {
                    isUnpluggedOnly = true;
                } else if ("--reset".equals(arg)) {
                    synchronized (mStats) {
                        mStats.resetAllStatsLocked();
                        mDockStats.resetAllStatsLocked();
                        pw.println("Battery stats reset.");
                        noOutput = true;
                    }
                } else if ("--write".equals(arg)) {
                    synchronized (mStats) {
                        mStats.writeSyncLocked();
                        mDockStats.writeSyncLocked();
                        pw.println("Battery stats written.");
                        noOutput = true;
                    }
                } else if ("-h".equals(arg)) {
                    dumpHelp(pw);
                    return;
                } else if ("-a".equals(arg)) {
                    // fall through
                } else if (arg.length() > 0 && arg.charAt(0) == '-'){
                    pw.println("Unknown option: " + arg);
                    dumpHelp(pw);
                    return;
                } else {
                    // Not an option, last argument must be a package name.
                    try {
                        reqUid = mContext.getPackageManager().getPackageUid(arg,
                                UserHandle.getCallingUserId());
                    } catch (PackageManager.NameNotFoundException e) {
                        pw.println("Unknown package: " + arg);
                        dumpHelp(pw);
                        return;
                    }
                }
            }
        }
        if (noOutput) {
            return;
        }
        if (isCheckin) {
            List<ApplicationInfo> apps = mContext.getPackageManager().getInstalledApplications(0);
            synchronized (mStats) {
                mStats.dumpCheckinLocked(pw, apps, isUnpluggedOnly, includeHistory);
            }
            synchronized (mDockStats) {
                mDockStats.dumpCheckinLocked(pw, apps, isUnpluggedOnly, includeHistory);
            }
        } else {
            synchronized (mStats) {
                mStats.dumpLocked(pw, isUnpluggedOnly, reqUid);
            }
            synchronized (mDockStats) {
                mDockStats.dumpLocked(pw, isUnpluggedOnly, reqUid);
            }
        }
    }
}
