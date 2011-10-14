/*
 * Copyright (C) 2011 Cuong Bui
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
package com.android.server.location;


import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.IGpsStatusListener;
import android.location.IGpsStatusProvider;
import android.location.ILocationManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.WorkSource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.app.IBatteryStats;


public class BTGpsLocationProvider  implements LocationProviderInterface {
    private static final boolean D = true;
    private final String PROVIDER = "External Bleutooth Location Provider";
    private final String TAG = "BTGpsLocationProvider";
    private final NMEAParser nmeaparser = new NMEAParser(PROVIDER);

    private final BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();

    // GPS update codes
    public static final int GPS_DATA_AVAILABLE = 1000;
    public static final int GPS_STATUS_UPDATE = 1001;
    public static final int GPS_CUSTOM_COMMAND = 1002;


    // Wakelocks
    private final static String WAKELOCK_KEY = "GpsLocationProvider";
    private final PowerManager.WakeLock mWakeLock;
    // bitfield of pending messages to our Handler
    // used only for messages that cannot have multiple instances queued
    private int mPendingMessageBits;
    // separate counter for ADD_LISTENER and REMOVE_LISTENER messages,
    // which might have multiple instances queued
    private int mPendingListenerMessages;

    private final IBatteryStats mBatteryStats;
    private final SparseIntArray mClientUids = new SparseIntArray();
    // Handler messages
    private static final int CHECK_LOCATION = 1;
    private static final int ENABLE = 2;
    private static final int ENABLE_TRACKING = 3;
    private static final int UPDATE_NETWORK_STATE = 4;
    private static final int INJECT_NTP_TIME = 5;
    private static final int DOWNLOAD_XTRA_DATA = 6;
    private static final int UPDATE_LOCATION = 7;
    private static final int ADD_LISTENER = 8;
    private static final int REMOVE_LISTENER = 9;
    private static final int REQUEST_SINGLE_SHOT = 10;

    // for calculating time to first fix
    private long mFixRequestTime = 0;
    // time to first fix for most recent session
    private int mTTFF = 0;
    // time we received our last fix
    private long mLastFixTime;

    // time for last status update
    private long mStatusUpdateTime = SystemClock.elapsedRealtime();

    // true if we are enabled
    private volatile boolean mEnabled;

    // true if GPS is navigating
    private boolean mNavigating;

    private int mSvCount;
    // current status
    private int mStatus = LocationProvider.TEMPORARILY_UNAVAILABLE;

    private Bundle mLocationExtras = new Bundle();
    private Location mLocation = new Location(PROVIDER);

    private final Context mContext;
    private final ILocationManager mLocationManager;

    private final IntentFilter mIntentBTFilter;

    private final Thread mMessageLoopThread = new BTGPSMessageThread();
    private final CountDownLatch mInitializedLatch = new CountDownLatch(1);

    /**
     *Listen for BT changes. If BT is turned off, disable GPS services
     */
    private final BroadcastReceiver mReceiver;

    /**
     *  Message handler
     *  Receives nmea sentences
     *  receives connection lost signals
     *  enabling/disabling gps signals
     *  adding/removing listeners
     */
    private Handler mHandler;

    // BT gps service. This class handles the actual BT connection and data xfer
    private final BTGPSService btsvc;

    // BT Location provider , uses the same method signature as the org GPS location provider
    public BTGpsLocationProvider(Context context, ILocationManager locationManager) {

        mContext = context;
        mLocationManager = locationManager;
        // innit message handler
        mMessageLoopThread.start();
        // wait for message handler to be ready
        while (true) {
            try {
                mInitializedLatch.await();
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // instantiate BTGPSService
        btsvc = new BTGPSService(mHandler);

        // Create a wake lock.
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);
        mWakeLock.setReferenceCounted(false);

        // Battery statistics service to be notified when GPS turns on or off
        mBatteryStats = IBatteryStats.Stub.asInterface(ServiceManager.getService("batteryinfo"));

        // receive BT state changes
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        if (D) Log.i(TAG, "BT turned on -> notify services?");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        if (btsvc.getServiceState() != BTGPSService.STATE_NONE) {
                            if (D) Log.i(TAG, "BT turned off -> stopping services");
                            btsvc.stop();
                        }
                        break;
                    }
                }
            }
        };
        mIntentBTFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, mIntentBTFilter);
    }

    private final class BTGPSMessageThread extends Thread {

        public void run() {
            try {
                Looper.prepare();
            } catch (RuntimeException e) {
                // ignored: Looper already prepared
            }
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    int message = msg.what;
                    switch (message) {
                    case GPS_DATA_AVAILABLE:
                        char[] writeBuf = (char[]) msg.obj;
                        int bytes = msg.arg1;
                        if ((writeBuf != null) && (mEnabled && bytes > 0)) {
                            String writeMessage = new String(writeBuf, 0, bytes);
                            handleNMEAMessages(writeMessage);
                            java.util.Arrays.fill(writeBuf, (char) ' ');
                        }
                        break;
                    case GPS_STATUS_UPDATE:
                        notifyEnableDisableGPS(msg.arg1 == 1);
                        break;
                    case GPS_CUSTOM_COMMAND:
                        if (mEnabled && btsvc.getServiceState() == BTGPSService.STATE_CONNECTED) {
                            // sends custom commands
                            byte[] cmds = (byte[]) msg.obj;
                            btsvc.write(cmds);
                        }
                        break;
                    case ENABLE:
                        if (msg.arg1 == 1) {
                            handleEnable();
                        } else {
                            handleDisable();
                        }
                        break;
                    case REQUEST_SINGLE_SHOT:
                    case ENABLE_TRACKING:
                    case UPDATE_NETWORK_STATE:
                    case INJECT_NTP_TIME:
                    case DOWNLOAD_XTRA_DATA:
                        break;
                    case UPDATE_LOCATION:
                        handleUpdateLocation((Location)msg.obj);
                        break;
                    case ADD_LISTENER:
                        handleAddListener(msg.arg1);
                        break;
                    case REMOVE_LISTENER:
                        handleRemoveListener(msg.arg1);
                        break;
                    }
                    // release wake lock if no messages are pending
                    synchronized (mWakeLock) {
                        mPendingMessageBits &= ~(1 << message);
                        if (message == ADD_LISTENER || message == REMOVE_LISTENER) {
                            mPendingListenerMessages--;
                        }
                        if (mPendingMessageBits == 0 && mPendingListenerMessages == 0) {
                            mWakeLock.release();
                        }
                    }
                }
            };
            mInitializedLatch.countDown();
            Looper.loop();
        }
    }

    @Override
    public void enable() {
        synchronized (mHandler) {
            sendMessage(ENABLE, 1, null);
        }
    }

    /**
     * Enables BT GPS provider
     */
    private synchronized void handleEnable() {
        if (D) Log.d(TAG, "handleEnable");
        if (mEnabled) return;
        // check if BT is enabled
        if (!mAdapter.isEnabled()) {
            int state = mAdapter.getState();
            if (state == BluetoothAdapter.STATE_OFF) {
                if (D) Log.d(TAG, "BT not available. Enable and wait for it...");
                mAdapter.enable();
            }
            // wait for adapter to be ready
            while (true) {
                try {
                    state = mAdapter.getState();
                    if (state == BluetoothAdapter.STATE_ON) {
                        break;
                    } else if (state == BluetoothAdapter.STATE_TURNING_ON) {
                        if (D) Log.d(TAG, "BT not available yet. waiting for another 400ms");
                        Thread.sleep(400);
                    } else {
                        if (D) Log.d(TAG, "BT got disabled or interrupted by other source");
                        return;
                    }

                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }
            }
        }
        if (D) Log.d(TAG, "mEnabled -> true");
        mEnabled = true;
        if (D) Log.d(TAG, "mStatus -> temp unavailable");
        mStatus = LocationProvider.TEMPORARILY_UNAVAILABLE;
        if (D) Log.d(TAG, "btservice start");
        btsvc.start();
        mFixRequestTime = System.currentTimeMillis();
        mTTFF = 0;
        String btDevice = Settings.System.getString(mContext.getContentResolver(),
                Settings.Secure.EXTERNAL_GPS_BT_DEVICE);
        if (D) Log.d(TAG, "Connecting to saved pref: " + btDevice);
        if ((btDevice != null) && !"0".equals(btDevice)) {
            if ((mAdapter != null) && (mAdapter.isEnabled())) {
                for (BluetoothDevice d: mAdapter.getBondedDevices()) {
                    if (btDevice.equals(d.getAddress())) {
                        if (D) Log.d(TAG, "Connecting...");
                        btsvc.connect(d);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Disables this provider.
     */
    @Override
    public void disable() {
        synchronized (mHandler) {
            sendMessage(ENABLE, 0, null);
        }
    }

    private synchronized void handleDisable() {
        if (D) Log.d(TAG, "handleDisable");
        if (!mEnabled) return;
        if (D) Log.d(TAG, "mEnabled -> false");
        mEnabled = false;
        if (D) Log.d(TAG, "reportstatus notify listeners and system");
        notifyEnableDisableGPS(false);
        if (D) Log.d(TAG, "update to out of service");
        updateStatus(LocationProvider.OUT_OF_SERVICE, mSvCount);
        if (D) Log.d(TAG, "btservice Stop");
        btsvc.stop();
    }

    /* We do not need to implement scheduled tracking. With internal gps providers it makes sence
     * to hibernate and resume periodically. With BT GPS providers it doesn't make sense
     * @see com.android.server.location.LocationProviderInterface#enableLocationTracking(boolean)
     */
    @Override
    public void enableLocationTracking(boolean enable) {
    }

    @Override
    public int getAccuracy() {
        return Criteria.ACCURACY_FINE;
    }

    /* Debug native state used by normal GPS provider only
     * @see com.android.server.location.LocationProviderInterface#getInternalState()
     */
    @Override
    public String getInternalState() {
        return null;
    }

    @Override
    public String getName() {
        return LocationManager.GPS_PROVIDER;
    }

    /**
     * Returns the power requirement for this provider.
     *
     * @return the power requirement for this provider, as one of the
     * constants Criteria.POWER_REQUIREMENT_*.
     */
    public int getPowerRequirement() {
        return Criteria.POWER_MEDIUM;
    }

    /**
     * Returns true if this provider meets the given criteria,
     * false otherwise.
     */
    public boolean meetsCriteria(Criteria criteria) {
        return (criteria.getPowerRequirement() != Criteria.POWER_LOW);
    }

    @Override
    public int getStatus(Bundle extras) {
        if (extras != null) {
            extras.putInt("satellites", mSvCount);
        }
        return mStatus;
    }

    @Override
    public long getStatusUpdateTime() {
        return mStatusUpdateTime;
    }

    @Override
    public boolean hasMonetaryCost() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }


    @Override
    public boolean requestSingleShotFix() {
        return false;
    }

    @Override
    public boolean requiresCell() {
        return false;
    }

    @Override
    public boolean requiresNetwork() {
        return false;
    }

    @Override
    public boolean requiresSatellite() {
        return true;
    }

    @Override
    public boolean sendExtraCommand(String command, Bundle extras) {
        if (TextUtils.isEmpty(command)) return false;
        synchronized (mHandler) {
            String customCommand = command + "\r\n";
            sendMessage(GPS_CUSTOM_COMMAND, customCommand.length(), customCommand.getBytes());
        }
        return true;
    }

    /* GPS scheduling stuff, not needed
     * @see com.android.server.location.LocationProviderInterface#setMinTime(long, android.os.WorkSource)
     */
    @Override
    public void setMinTime(long minTime, WorkSource ws) {

    }

    @Override
    public boolean supportsAltitude() {
        return mLocation.hasAltitude();
    }

    @Override
    public boolean supportsBearing() {
        return mLocation.hasBearing();
    }

    @Override
    public boolean supportsSpeed() {
        return mLocation.hasSpeed();
    }

    @Override
    /**
     * This is called to inform us when another location provider returns a location.
     * Someday we might use this for network location injection to aid the GPS
     */
    public void updateLocation(Location location) {
        sendMessage(UPDATE_LOCATION, 0, location);
    }

    private void handleUpdateLocation(Location location) {
        if (location.hasAccuracy()) {
            // Allow other provider GPS data ? discard for now
        }
    }

    /* unneeded by BT GPS provider
     * @see com.android.server.location.LocationProviderInterface#updateNetworkState(int, android.net.NetworkInfo)
     */
    @Override
    public void updateNetworkState(int state, NetworkInfo info) {
        // TODO Auto-generated method stub

    }

    /**
     * @param loc   Location object representing the fix
     * @param isValid   true if fix was valid
     */
    private void reportLocation(Location loc, boolean isValid) {

        if (!isValid) {
            if (mStatus == LocationProvider.AVAILABLE && mTTFF > 0) {
                if (D) Log.d(TAG, "Invalid sat fix -> sending notification to system");
                // send an intent to notify that the GPS is no longer receiving fixes.
                Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
                intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, false);
                mContext.sendBroadcast(intent);
                updateStatus(LocationProvider.TEMPORARILY_UNAVAILABLE, mSvCount);
            }
            return;
        }

        synchronized (mLocation) {
            mLocation.set(loc);
            mLocation.setProvider(this.getName());
            if (D) {
                Log.d(TAG, "reportLocation lat: " + mLocation.getLatitude() +
                    " long: " + mLocation.getLongitude() + " alt: " + mLocation.getAltitude() +
                    " accuracy: " + mLocation.getAccuracy() + " timestamp: " + mLocation.getTime());
            }
            try {
                mLocationManager.reportLocation(mLocation, false);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException calling reportLocation");
            }
        }

        mLastFixTime = System.currentTimeMillis();
        // report time to first fix
        if ((mTTFF == 0) && (isValid)) {
            mTTFF = (int)(mLastFixTime - mFixRequestTime);
            if (D) Log.d(TAG, "TTFF: " + mTTFF);

            // notify status listeners
            synchronized(mListeners) {
                int size = mListeners.size();
                for (int i = 0; i < size; i++) {
                    Listener listener = mListeners.get(i);
                    try {
                        listener.mListener.onFirstFix(mTTFF);
                    } catch (RemoteException e) {
                        Log.w(TAG, "RemoteException in first fix notification");
                        mListeners.remove(listener);
                        // adjust for size of list changing
                        size--;
                    }
                }
            }
        }

        if (mStatus != LocationProvider.AVAILABLE) {
            if (D) Log.d(TAG,"Notify that we're receiving fixes");
            // send an intent to notify that the GPS is receiving fixes.
            Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
            intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, true);
            mContext.sendBroadcast(intent);
            updateStatus(LocationProvider.AVAILABLE, mSvCount);
        }

    }

    /* report sats status
     */
    private void reportSvStatus(int svCount, int mSvs[], float mSnrs[],
            float mSvElevations[],  float mSvAzimuths[], int mSvMasks[]) {

        if (D) Log.d(TAG,"About to report sat status svcount: " + svCount);
        synchronized(mListeners) {
            int size = mListeners.size();
            for (int i = 0; i < size; i++) {
                Listener listener = mListeners.get(i);
                try {
                    listener.mListener.onSvStatusChanged(svCount, mSvs, mSnrs, mSvElevations,
                            mSvAzimuths, mSvMasks[NMEAParser.EPHEMERIS_MASK],
                            mSvMasks[NMEAParser.ALMANAC_MASK],
                            mSvMasks[NMEAParser.USED_FOR_FIX_MASK]);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException in reportSvInfo");
                    mListeners.remove(listener);
                    // adjust for size of list changing
                    size--;
                }
            }
        }

        // return number of sets used in fix instead of total
        updateStatus(mStatus, Integer.bitCount(mSvMasks[NMEAParser.USED_FOR_FIX_MASK]));
    }

    /**
     * Handles GPS status.
     * will also inform listeners when GPS started/stopped
     * @param status    new GPS status
     */
    private void notifyEnableDisableGPS(boolean status) {
        if (D) Log.v(TAG, "notifyEnableDisableGPS status: " + status);

        synchronized(mListeners) {
            mNavigating = status;
            int size = mListeners.size();
            for (int i = 0; i < size; i++) {
                Listener listener = mListeners.get(i);
                try {
                    if (status) {
                        listener.mListener.onGpsStarted();
                    } else {
                        listener.mListener.onGpsStopped();
                    }
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException in reportStatus");
                    mListeners.remove(listener);
                    // adjust for size of list changing
                    size--;
                }
            }
            try {
                // update battery stats
                for (int i=mClientUids.size() - 1; i >= 0; i--) {
                    int uid = mClientUids.keyAt(i);
                    if (mNavigating) {
                        mBatteryStats.noteStartGps(uid);
                    } else {
                        mBatteryStats.noteStopGps(uid);
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "RemoteException in reportStatus");
            }
            // send an intent to notify that the GPS has been enabled or disabled.
            Intent intent = new Intent(LocationManager.GPS_ENABLED_CHANGE_ACTION);
            intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, status);
            mContext.sendBroadcast(intent);
        }
        try {
            if (D) Log.d(TAG, "Setting System GPS status to " + status);
            Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                    LocationManager.GPS_PROVIDER, status);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * sends nmea sentences to NMEA parsers. Some apps use raw nmea data
     * @param nmeaString    nmea string
     * @param timestamp     time stamp
     */
    private void reportNmea(String nmeaString, long timestamp) {
        synchronized(mListeners) {
            int size = mListeners.size();
            if (size > 0) {
                // don't bother creating the String if we have no listeners
                for (int i = 0; i < size; i++) {
                    Listener listener = mListeners.get(i);
                    try {
                        listener.mListener.onNmeaReceived(timestamp, nmeaString);
                    } catch (RemoteException e) {
                        Log.w(TAG, "RemoteException in reportNmea");
                        mListeners.remove(listener);
                        // adjust for size of list changing
                        size--;
                    }
                }
            }
        }
    }

    /**
     * This methods parses the nmea sentences and sends the location updates
     * and sats updates to listeners.
     * @param sentences     raw nmea sentences received by BT GPS Mouse
     */
    private void handleNMEAMessages(String sentences) {
        String sentenceArray[] = sentences.split("\r\n");
        nmeaparser.reset();
        for (int i = 0; i < sentenceArray.length; i++) {
            if (D) Log.d(TAG, "About to parse: " + sentenceArray[i]);
            if ((sentenceArray[i] != null) && ("".equals(sentenceArray[i]))) continue;
            boolean parsed = nmeaparser.parseNMEALine(sentenceArray[i]);
            // handle nmea message. Also report messages that we could not parse as these
            // might be propriatery messages that other listeners could support.
            reportNmea(sentenceArray[i], System.currentTimeMillis());
        }
        Location loc = nmeaparser.getLocation();
        // handle location update if valid
        reportLocation(loc , nmeaparser.isValid());
        if (nmeaparser.isSatdataReady()) {
            reportSvStatus(nmeaparser.getmSvCount(), nmeaparser.getmSvs(), nmeaparser.getmSnrs(),
                nmeaparser.getmSvElevations(), nmeaparser.getmSvAzimuths(),
                nmeaparser.getmSvMasks());
        }

        // adjust refresh rate based on received timestamp of mouse
        // min 1hz and max 10 hz
        long newRate = nmeaparser.getApproximatedRefreshRate();
        if (btsvc.getRefreshRate() != newRate) {
            if (D) Log.d(TAG, "Setting refresh rate to: " + newRate
                    + " was: " + btsvc.getRefreshRate());
            btsvc.setRefreshRate(newRate);
        }
    }


    /*
     * Stuff below is taken from the android GPS location provider.
     * Does handling of messages/listeners and so on.
     */

    private void sendMessage(int message, int arg, Object obj) {
        // hold a wake lock while messages are pending
        synchronized (mWakeLock) {
            mPendingMessageBits |= (1 << message);
            mWakeLock.acquire();
            mHandler.removeMessages(message);
            Message m = Message.obtain(mHandler, message);
            m.arg1 = arg;
            m.obj = obj;
            mHandler.sendMessage(m);
        }
    }


    private void updateStatus(int status, int svCount) {
        if (status != mStatus || svCount != mSvCount) {
            mStatus = status;
            mSvCount = svCount;
            mLocationExtras.putInt("satellites", svCount);
            mStatusUpdateTime = SystemClock.elapsedRealtime();
        }
    }
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();

    private final class Listener implements IBinder.DeathRecipient {
        final IGpsStatusListener mListener;

        Listener(IGpsStatusListener listener) {
            mListener = listener;
        }

        public void binderDied() {
            if (D) Log.d(TAG, "GPS status listener died");

            synchronized(mListeners) {
                mListeners.remove(this);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }


    private final IGpsStatusProvider mGpsStatusProvider = new IGpsStatusProvider.Stub() {
        public void addGpsStatusListener(IGpsStatusListener listener) throws RemoteException {
            if (listener == null) {
                throw new NullPointerException("listener is null in addGpsStatusListener");
            }
            synchronized(mListeners) {
                IBinder binder = listener.asBinder();
                int size = mListeners.size();
                for (int i = 0; i < size; i++) {
                    Listener test = mListeners.get(i);
                    if (binder.equals(test.mListener.asBinder())) {
                        // listener already added
                        return;
                    }
                }
                Listener l = new Listener(listener);
                binder.linkToDeath(l, 0);
                mListeners.add(l);
            }
        }

        public void removeGpsStatusListener(IGpsStatusListener listener) {
            if (listener == null) {
                throw new NullPointerException("listener is null in addGpsStatusListener");
            }

            synchronized(mListeners) {
                IBinder binder = listener.asBinder();
                Listener l = null;
                int size = mListeners.size();
                for (int i = 0; i < size && l == null; i++) {
                    Listener test = mListeners.get(i);
                    if (binder.equals(test.mListener.asBinder())) {
                        l = test;
                    }
                }

                if (l != null) {
                    mListeners.remove(l);
                    binder.unlinkToDeath(l, 0);
                }
            }
        }
    };

    public IGpsStatusProvider getGpsStatusProvider() {
        return mGpsStatusProvider;
    }

    public void addListener(int uid) {
        synchronized (mWakeLock) {
            mPendingListenerMessages++;
            mWakeLock.acquire();
            Message m = Message.obtain(mHandler, ADD_LISTENER);
            m.arg1 = uid;
            mHandler.sendMessage(m);
        }
    }

    private void handleAddListener(int uid) {
        synchronized(mListeners) {
            if (mClientUids.indexOfKey(uid) >= 0) {
                // Shouldn't be here -- already have this uid.
                Log.w(TAG, "Duplicate add listener for uid " + uid);
                return;
            }
            mClientUids.put(uid, 0);
            if (mNavigating) {
                try {
                    mBatteryStats.noteStartGps(uid);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException in addListener");
                }
            }
        }
    }

    public void removeListener(int uid) {
        synchronized (mWakeLock) {
            mPendingListenerMessages++;
            mWakeLock.acquire();
            Message m = Message.obtain(mHandler, REMOVE_LISTENER);
            m.arg1 = uid;
            mHandler.sendMessage(m);
        }
    }

    private void handleRemoveListener(int uid) {
        synchronized(mListeners) {
            if (mClientUids.indexOfKey(uid) < 0) {
                // Shouldn't be here -- don't have this uid.
                Log.w(TAG, "Unneeded remove listener for uid " + uid);
                return;
            }
            mClientUids.delete(uid);
            if (mNavigating) {
                try {
                    mBatteryStats.noteStopGps(uid);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException in removeListener");
                }
            }
        }
    }
}
