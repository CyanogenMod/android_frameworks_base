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

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.location.Address;
import android.location.Criteria;
import android.location.GeocoderParams;
import android.location.Geofence;
import android.location.GeoFenceParams;
import android.location.IGpsStatusListener;
import android.location.IGpsStatusProvider;
import android.location.ILocationListener;
import android.location.ILocationManager;
import android.location.INetInitiatedListener;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import com.android.internal.content.PackageMonitor;
import com.android.internal.location.ProviderProperties;
import com.android.internal.location.ProviderRequest;
import com.android.internal.os.BackgroundThread;
import com.android.server.location.FlpHardwareProvider;
import com.android.server.location.FusedProxy;
import com.android.server.location.GeocoderProxy;
import com.android.server.location.GeofenceProxy;
import com.android.server.location.GeofenceManager;
import com.android.server.location.GeoFencerBase;
import com.android.server.location.GeoFencerProxy;
import com.android.server.location.GpsLocationProvider;
import com.android.server.location.LocationBlacklist;
import com.android.server.location.LocationFudger;
import com.android.server.location.LocationProviderInterface;
import com.android.server.location.LocationProviderProxy;
import com.android.server.location.MockProvider;
import com.android.server.location.PassiveProvider;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The service class that manages LocationProviders and issues location
 * updates and alerts.
 */
public class LocationManagerService extends ILocationManager.Stub {
    private static final String TAG = "LocationManagerService";
    public static final boolean D = Log.isLoggable(TAG, Log.DEBUG);

    private static final String WAKELOCK_KEY = TAG;

    // Location resolution level: no location data whatsoever
    private static final int RESOLUTION_LEVEL_NONE = 0;
    // Location resolution level: coarse location data only
    private static final int RESOLUTION_LEVEL_COARSE = 1;
    // Location resolution level: fine location data
    private static final int RESOLUTION_LEVEL_FINE = 2;

    private static final String ACCESS_MOCK_LOCATION =
            android.Manifest.permission.ACCESS_MOCK_LOCATION;
    private static final String ACCESS_LOCATION_EXTRA_COMMANDS =
            android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS;
    private static final String INSTALL_LOCATION_PROVIDER =
            android.Manifest.permission.INSTALL_LOCATION_PROVIDER;

    private static final String NETWORK_LOCATION_SERVICE_ACTION =
            "com.android.location.service.v3.NetworkLocationProvider";
    private static final String FUSED_LOCATION_SERVICE_ACTION =
            "com.android.location.service.FusedLocationProvider";

    private static final int MSG_LOCATION_CHANGED = 1;

    private static final long NANOS_PER_MILLI = 1000000L;

    // The maximum interval a location request can have and still be considered "high power".
    private static final long HIGH_POWER_INTERVAL_MS = 5 * 60 * 1000;

    // Location Providers may sometimes deliver location updates
    // slightly faster that requested - provide grace period so
    // we don't unnecessarily filter events that are otherwise on
    // time
    private static final int MAX_PROVIDER_SCHEDULING_JITTER_MS = 100;

    private static final LocationRequest DEFAULT_LOCATION_REQUEST = new LocationRequest();

    private final Context mContext;
    private final AppOpsManager mAppOps;

    // used internally for synchronization
    private final Object mLock = new Object();

    // --- fields below are final after systemReady() ---
    private LocationFudger mLocationFudger;
    private GeofenceManager mGeofenceManager;
    private String mGeoFencerPackageName;
    private String mComboNlpPackageName;
    private String mComboNlpReadyMarker;
    private String mComboNlpScreenMarker;
    private GeoFencerBase mGeoFencer;
    private boolean mGeoFencerEnabled;
    private PackageManager mPackageManager;
    private PowerManager mPowerManager;
    private GeocoderProxy mGeocodeProvider;
    private IGpsStatusProvider mGpsStatusProvider;
    private INetInitiatedListener mNetInitiatedListener;
    private LocationWorkerHandler mLocationHandler;
    private PassiveProvider mPassiveProvider;  // track passive provider for special cases
    private LocationBlacklist mBlacklist;

    // --- fields below are protected by mLock ---
    // Set of providers that are explicitly enabled
    private final Set<String> mEnabledProviders = new HashSet<String>();

    // Set of providers that are explicitly disabled
    private final Set<String> mDisabledProviders = new HashSet<String>();

    // Mock (test) providers
    private final HashMap<String, MockProvider> mMockProviders =
            new HashMap<String, MockProvider>();

    // all receivers
    private final HashMap<Object, Receiver> mReceivers = new HashMap<Object, Receiver>();

    // currently installed providers (with mocks replacing real providers)
    private final ArrayList<LocationProviderInterface> mProviders =
            new ArrayList<LocationProviderInterface>();

    // real providers, saved here when mocked out
    private final HashMap<String, LocationProviderInterface> mRealProviders =
            new HashMap<String, LocationProviderInterface>();

    // mapping from provider name to provider
    private final HashMap<String, LocationProviderInterface> mProvidersByName =
            new HashMap<String, LocationProviderInterface>();

    // mapping from provider name to all its UpdateRecords
    private final HashMap<String, ArrayList<UpdateRecord>> mRecordsByProvider =
            new HashMap<String, ArrayList<UpdateRecord>>();

    // mapping from provider name to last known location
    private final HashMap<String, Location> mLastLocation = new HashMap<String, Location>();

    // same as mLastLocation, but is not updated faster than LocationFudger.FASTEST_INTERVAL_MS.
    // locations stored here are not fudged for coarse permissions.
    private final HashMap<String, Location> mLastLocationCoarseInterval =
            new HashMap<String, Location>();

    // all providers that operate over proxy, for authorizing incoming location
    private final ArrayList<LocationProviderProxy> mProxyProviders =
            new ArrayList<LocationProviderProxy>();

    // current active user on the device - other users are denied location data
    private int mCurrentUserId = UserHandle.USER_OWNER;

    public LocationManagerService(Context context) {
        super();
        mContext = context;
        mAppOps = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        mGeoFencerEnabled = false;

        if (D) Log.d(TAG, "Constructed");

        // most startup is deferred until systemReady()
    }

    public void systemRunning() {
        synchronized (mLock) {
            if (D) Log.d(TAG, "systemReady()");

            // fetch package manager
            mPackageManager = mContext.getPackageManager();

            // fetch power manager
            mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

            // prepare worker thread
            mLocationHandler = new LocationWorkerHandler(BackgroundThread.get().getLooper());

            // prepare mLocationHandler's dependents
            mLocationFudger = new LocationFudger(mContext, mLocationHandler);
            mBlacklist = new LocationBlacklist(mContext, mLocationHandler);
            mBlacklist.init();
            mGeofenceManager = new GeofenceManager(mContext, mBlacklist);

            // Monitor for app ops mode changes.
            AppOpsManager.OnOpChangedListener callback
                    = new AppOpsManager.OnOpChangedInternalListener() {
                public void onOpChanged(int op, String packageName) {
                    synchronized (mLock) {
                        for (Receiver receiver : mReceivers.values()) {
                            receiver.updateMonitoring(true);
                        }
                        applyAllProviderRequirementsLocked();
                    }
                }
            };
            mAppOps.startWatchingMode(AppOpsManager.OP_COARSE_LOCATION, null, callback);

            // prepare providers
            loadProvidersLocked();
            updateProvidersLocked();
        }

        // listen for settings changes
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCATION_PROVIDERS_ALLOWED), true,
                new ContentObserver(mLocationHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        synchronized (mLock) {
                            updateProvidersLocked();
                        }
                    }
                }, UserHandle.USER_ALL);
        mPackageMonitor.register(mContext, mLocationHandler.getLooper(), true);

        // listen for user change
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_SWITCHED);

        mContext.registerReceiverAsUser(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    switchUser(intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0));
                }
            }
        }, UserHandle.ALL, intentFilter, null, mLocationHandler);
    }

    private void ensureFallbackFusedProviderPresentLocked(ArrayList<String> pkgs) {
        PackageManager pm = mContext.getPackageManager();
        String systemPackageName = mContext.getPackageName();
        ArrayList<HashSet<Signature>> sigSets = ServiceWatcher.getSignatureSets(mContext, pkgs);

        List<ResolveInfo> rInfos = pm.queryIntentServicesAsUser(
                new Intent(FUSED_LOCATION_SERVICE_ACTION),
                PackageManager.GET_META_DATA, mCurrentUserId);
        for (ResolveInfo rInfo : rInfos) {
            String packageName = rInfo.serviceInfo.packageName;

            // Check that the signature is in the list of supported sigs. If it's not in
            // this list the standard provider binding logic won't bind to it.
            try {
                PackageInfo pInfo;
                pInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (!ServiceWatcher.isSignatureMatch(pInfo.signatures, sigSets)) {
                    Log.w(TAG, packageName + " resolves service " + FUSED_LOCATION_SERVICE_ACTION +
                            ", but has wrong signature, ignoring");
                    continue;
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "missing package: " + packageName);
                continue;
            }

            // Get the version info
            if (rInfo.serviceInfo.metaData == null) {
                Log.w(TAG, "Found fused provider without metadata: " + packageName);
                continue;
            }

            int version = rInfo.serviceInfo.metaData.getInt(
                    ServiceWatcher.EXTRA_SERVICE_VERSION, -1);
            if (version == 0) {
                // This should be the fallback fused location provider.

                // Make sure it's in the system partition.
                if ((rInfo.serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    if (D) Log.d(TAG, "Fallback candidate not in /system: " + packageName);
                    continue;
                }

                // Check that the fallback is signed the same as the OS
                // as a proxy for coreApp="true"
                if (pm.checkSignatures(systemPackageName, packageName)
                        != PackageManager.SIGNATURE_MATCH) {
                    if (D) Log.d(TAG, "Fallback candidate not signed the same as system: "
                            + packageName);
                    continue;
                }

                // Found a valid fallback.
                if (D) Log.d(TAG, "Found fallback provider: " + packageName);
                return;
            } else {
                if (D) Log.d(TAG, "Fallback candidate not version 0: " + packageName);
            }
        }

        throw new IllegalStateException("Unable to find a fused location provider that is in the "
                + "system partition with version 0 and signed with the platform certificate. "
                + "Such a package is needed to provide a default fused location provider in the "
                + "event that no other fused location provider has been installed or is currently "
                + "available. For example, coreOnly boot mode when decrypting the data "
                + "partition. The fallback must also be marked coreApp=\"true\" in the manifest");
    }

    private void loadProvidersLocked() {
        // create a passive location provider, which is always enabled
        PassiveProvider passiveProvider = new PassiveProvider(this);
        addProviderLocked(passiveProvider);
        mEnabledProviders.add(passiveProvider.getName());
        mPassiveProvider = passiveProvider;
        // Create a gps location provider
        GpsLocationProvider gpsProvider = new GpsLocationProvider(mContext, this,
                mLocationHandler.getLooper());

        if (GpsLocationProvider.isSupported()) {
            mGpsStatusProvider = gpsProvider.getGpsStatusProvider();
            mNetInitiatedListener = gpsProvider.getNetInitiatedListener();
            addProviderLocked(gpsProvider);
            mRealProviders.put(LocationManager.GPS_PROVIDER, gpsProvider);
        }

        /*
        Load package name(s) containing location provider support.
        These packages can contain services implementing location providers:
        Geocoder Provider, Network Location Provider, and
        Fused Location Provider. They will each be searched for
        service components implementing these providers.
        The location framework also has support for installation
        of new location providers at run-time. The new package does not
        have to be explicitly listed here, however it must have a signature
        that matches the signature of at least one package on this list.
        */
        Resources resources = mContext.getResources();
        ArrayList<String> providerPackageNames = new ArrayList<String>();
        String[] pkgs = resources.getStringArray(
                com.android.internal.R.array.config_locationProviderPackageNames);
        if (D) Log.d(TAG, "certificates for location providers pulled from: " +
                Arrays.toString(pkgs));
        if (pkgs != null) providerPackageNames.addAll(Arrays.asList(pkgs));

        ensureFallbackFusedProviderPresentLocked(providerPackageNames);

        // bind to network provider
        LocationProviderProxy networkProvider = LocationProviderProxy.createAndBind(
                mContext,
                LocationManager.NETWORK_PROVIDER,
                NETWORK_LOCATION_SERVICE_ACTION,
                com.android.internal.R.bool.config_enableNetworkLocationOverlay,
                com.android.internal.R.string.config_networkLocationProviderPackageName,
                com.android.internal.R.array.config_locationProviderPackageNames,
                mLocationHandler);
        if (networkProvider != null) {
            mRealProviders.put(LocationManager.NETWORK_PROVIDER, networkProvider);
            mProxyProviders.add(networkProvider);
            addProviderLocked(networkProvider);
        } else {
            Slog.w(TAG,  "no network location provider found");
        }

        // bind to fused provider
        LocationProviderProxy fusedLocationProvider = LocationProviderProxy.createAndBind(
                mContext,
                LocationManager.FUSED_PROVIDER,
                FUSED_LOCATION_SERVICE_ACTION,
                com.android.internal.R.bool.config_enableFusedLocationOverlay,
                com.android.internal.R.string.config_fusedLocationProviderPackageName,
                com.android.internal.R.array.config_locationProviderPackageNames,
                mLocationHandler);
        if (fusedLocationProvider != null) {
            addProviderLocked(fusedLocationProvider);
            mProxyProviders.add(fusedLocationProvider);
            mEnabledProviders.add(fusedLocationProvider.getName());
            mRealProviders.put(LocationManager.FUSED_PROVIDER, fusedLocationProvider);
        } else {
            Slog.e(TAG, "no fused location provider found",
                    new IllegalStateException("Location service needs a fused location provider"));
        }

        // bind to geocoder provider
        mGeocodeProvider = GeocoderProxy.createAndBind(mContext,
                com.android.internal.R.bool.config_enableGeocoderOverlay,
                com.android.internal.R.string.config_geocoderProviderPackageName,
                com.android.internal.R.array.config_locationProviderPackageNames,
                mLocationHandler);
        if (mGeocodeProvider == null) {
            Slog.e(TAG,  "no geocoder provider found");
        }

        // bind to fused provider
        FlpHardwareProvider flpHardwareProvider = FlpHardwareProvider.getInstance(mContext);
        FusedProxy fusedProxy = FusedProxy.createAndBind(
                mContext,
                mLocationHandler,
                flpHardwareProvider.getLocationHardware(),
                com.android.internal.R.bool.config_enableHwFlpOverlay,
                com.android.internal.R.string.config_fusedLocationProviderPackageName,
                com.android.internal.R.array.config_locationProviderPackageNames);
        if(fusedProxy == null) {
            Slog.e(TAG, "No FusedProvider found.");
        }

        // bind to geofence provider
        GeofenceProxy provider = GeofenceProxy.createAndBind(mContext,
                com.android.internal.R.bool.config_enableGeofenceOverlay,
                com.android.internal.R.string.config_geofenceProviderPackageName,
                com.android.internal.R.array.config_locationProviderPackageNames,
                mLocationHandler,
                gpsProvider.getGpsGeofenceProxy(),
                flpHardwareProvider.getGeofenceHardware());
        if (provider == null) {
            Slog.e(TAG,  "no geofence provider found");
        }

        mGeoFencerPackageName = resources.getString(
                com.android.internal.R.string.config_geofenceServicesProvider);
        if (mGeoFencerPackageName != null &&
                mPackageManager.resolveService(new Intent(mGeoFencerPackageName), 0) != null) {
            mGeoFencer = GeoFencerProxy.getGeoFencerProxy(mContext, mGeoFencerPackageName);
            mGeoFencerEnabled = true;
        } else {
            mGeoFencer = null;
            mGeoFencerEnabled = false;
        }

        mComboNlpPackageName = resources.getString(
                com.android.internal.R.string.config_comboNetworkLocationProvider);
        if (mComboNlpPackageName != null) {
            mComboNlpReadyMarker = mComboNlpPackageName + ".nlp:ready";
            mComboNlpScreenMarker = mComboNlpPackageName + ".nlp:screen";
        }

    }

    /**
     * Called when the device's active user changes.
     * @param userId the new active user's UserId
     */
    private void switchUser(int userId) {
        if (mCurrentUserId == userId) {
            return;
        }
        mBlacklist.switchUser(userId);
        mLocationHandler.removeMessages(MSG_LOCATION_CHANGED);
        synchronized (mLock) {
            mLastLocation.clear();
            mLastLocationCoarseInterval.clear();
            for (LocationProviderInterface p : mProviders) {
                updateProviderListenersLocked(p.getName(), false, mCurrentUserId);
            }
            mCurrentUserId = userId;
            updateProvidersLocked();
        }
    }

    /**
     * A wrapper class holding either an ILocationListener or a PendingIntent to receive
     * location updates.
     */
    private final class Receiver implements IBinder.DeathRecipient, PendingIntent.OnFinished {
        final int mUid;  // uid of receiver
        final int mPid;  // pid of receiver
        final String mPackageName;  // package name of receiver
        final int mAllowedResolutionLevel;  // resolution level allowed to receiver

        final ILocationListener mListener;
        final PendingIntent mPendingIntent;
        final WorkSource mWorkSource; // WorkSource for battery blame, or null to assign to caller.
        final boolean mHideFromAppOps; // True if AppOps should not monitor this receiver.
        final Object mKey;

        final HashMap<String,UpdateRecord> mUpdateRecords = new HashMap<String,UpdateRecord>();

        // True if app ops has started monitoring this receiver for locations.
        boolean mOpMonitoring;
        // True if app ops has started monitoring this receiver for high power (gps) locations.
        boolean mOpHighPowerMonitoring;
        int mPendingBroadcasts;
        PowerManager.WakeLock mWakeLock;

        Receiver(ILocationListener listener, PendingIntent intent, int pid, int uid,
                String packageName, WorkSource workSource, boolean hideFromAppOps) {
            mListener = listener;
            mPendingIntent = intent;
            if (listener != null) {
                mKey = listener.asBinder();
            } else {
                mKey = intent;
            }
            mAllowedResolutionLevel = getAllowedResolutionLevel(pid, uid);
            mUid = uid;
            mPid = pid;
            mPackageName = packageName;
            if (workSource != null && workSource.size() <= 0) {
                workSource = null;
            }
            mWorkSource = workSource;
            mHideFromAppOps = hideFromAppOps;

            updateMonitoring(true);

            // construct/configure wakelock
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);
            if (workSource == null) {
                workSource = new WorkSource(mUid, mPackageName);
            }
            mWakeLock.setWorkSource(workSource);
        }

        @Override
        public boolean equals(Object otherObj) {
            if (otherObj instanceof Receiver) {
                return mKey.equals(((Receiver)otherObj).mKey);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mKey.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("Reciever[");
            s.append(Integer.toHexString(System.identityHashCode(this)));
            if (mListener != null) {
                s.append(" listener");
            } else {
                s.append(" intent");
            }
            for (String p : mUpdateRecords.keySet()) {
                s.append(" ").append(mUpdateRecords.get(p).toString());
            }
            s.append("]");
            return s.toString();
        }

        /**
         * Update AppOp monitoring for this receiver.
         *
         * @param allow If true receiver is currently active, if false it's been removed.
         */
        public void updateMonitoring(boolean allow) {
            if (mHideFromAppOps) {
                return;
            }

            boolean requestingLocation = false;
            boolean requestingHighPowerLocation = false;
            if (allow) {
                // See if receiver has any enabled update records.  Also note if any update records
                // are high power (has a high power provider with an interval under a threshold).
                for (UpdateRecord updateRecord : mUpdateRecords.values()) {
                    if (isAllowedByCurrentUserSettingsLocked(updateRecord.mProvider)) {
                        requestingLocation = true;
                        LocationProviderInterface locationProvider
                            = mProvidersByName.get(updateRecord.mProvider);
                        ProviderProperties properties = locationProvider != null
                                ? locationProvider.getProperties() : null;
                        if (properties != null
                                && properties.mPowerRequirement == Criteria.POWER_HIGH
                                && updateRecord.mRequest.getInterval() < HIGH_POWER_INTERVAL_MS) {
                            requestingHighPowerLocation = true;
                            break;
                        }
                    }
                }
            }

            // First update monitoring of any location request (including high power).
            mOpMonitoring = updateMonitoring(
                    requestingLocation,
                    mOpMonitoring,
                    AppOpsManager.OP_MONITOR_LOCATION);

            // Now update monitoring of high power requests only.
            boolean wasHighPowerMonitoring = mOpHighPowerMonitoring;
            mOpHighPowerMonitoring = updateMonitoring(
                    requestingHighPowerLocation,
                    mOpHighPowerMonitoring,
                    AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION);
            if (mOpHighPowerMonitoring != wasHighPowerMonitoring) {
                // Send an intent to notify that a high power request has been added/removed.
                Intent intent = new Intent(LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        }

        /**
         * Update AppOps monitoring for a single location request and op type.
         *
         * @param allowMonitoring True if monitoring is allowed for this request/op.
         * @param currentlyMonitoring True if AppOps is currently monitoring this request/op.
         * @param op AppOps code for the op to update.
         * @return True if monitoring is on for this request/op after updating.
         */
        private boolean updateMonitoring(boolean allowMonitoring, boolean currentlyMonitoring,
                int op) {
            if (!currentlyMonitoring) {
                if (allowMonitoring) {
                    return mAppOps.startOpNoThrow(op, mUid, mPackageName)
                            == AppOpsManager.MODE_ALLOWED;
                }
            } else {
                int mode =  mAppOps.checkOpNoThrow(op, mUid, mPackageName);

                if(allowMonitoring && mode == AppOpsManager.MODE_ASK) {
                    return true;
                }

                if (!allowMonitoring || mode != AppOpsManager.MODE_ALLOWED) {
                    mAppOps.finishOp(op, mUid, mPackageName);
                    return false;
                }
            }

            return currentlyMonitoring;
        }

        public boolean isListener() {
            return mListener != null;
        }

        public boolean isPendingIntent() {
            return mPendingIntent != null;
        }

        public ILocationListener getListener() {
            if (mListener != null) {
                return mListener;
            }
            throw new IllegalStateException("Request for non-existent listener");
        }

        public boolean callStatusChangedLocked(String provider, int status, Bundle extras) {
            if (mListener != null) {
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        mListener.onStatusChanged(provider, status, extras);
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (RemoteException e) {
                    return false;
                }
            } else {
                Intent statusChanged = new Intent();
                statusChanged.putExtras(new Bundle(extras));
                statusChanged.putExtra(LocationManager.KEY_STATUS_CHANGED, status);
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        mPendingIntent.send(mContext, 0, statusChanged, this, mLocationHandler,
                                getResolutionPermission(mAllowedResolutionLevel));
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (PendingIntent.CanceledException e) {
                    return false;
                }
            }
            return true;
        }

        public boolean callLocationChangedLocked(Location location) {
            if (mListener != null) {
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        mListener.onLocationChanged(new Location(location));
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (RemoteException e) {
                    return false;
                }
            } else {
                Intent locationChanged = new Intent();
                locationChanged.putExtra(LocationManager.KEY_LOCATION_CHANGED, new Location(location));
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        mPendingIntent.send(mContext, 0, locationChanged, this, mLocationHandler,
                                getResolutionPermission(mAllowedResolutionLevel));
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (PendingIntent.CanceledException e) {
                    return false;
                }
            }
            return true;
        }

        public boolean callProviderEnabledLocked(String provider, boolean enabled) {
            // First update AppOp monitoring.
            // An app may get/lose location access as providers are enabled/disabled.
            updateMonitoring(true);

            if (mListener != null) {
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        if (enabled) {
                            mListener.onProviderEnabled(provider);
                        } else {
                            mListener.onProviderDisabled(provider);
                        }
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (RemoteException e) {
                    return false;
                }
            } else {
                Intent providerIntent = new Intent();
                providerIntent.putExtra(LocationManager.KEY_PROVIDER_ENABLED, enabled);
                try {
                    synchronized (this) {
                        // synchronize to ensure incrementPendingBroadcastsLocked()
                        // is called before decrementPendingBroadcasts()
                        mPendingIntent.send(mContext, 0, providerIntent, this, mLocationHandler,
                                getResolutionPermission(mAllowedResolutionLevel));
                        // call this after broadcasting so we do not increment
                        // if we throw an exeption.
                        incrementPendingBroadcastsLocked();
                    }
                } catch (PendingIntent.CanceledException e) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void binderDied() {
            if (D) Log.d(TAG, "Location listener died");

            synchronized (mLock) {
                removeUpdatesLocked(this);
            }
            synchronized (this) {
                clearPendingBroadcastsLocked();
            }
        }

        @Override
        public void onSendFinished(PendingIntent pendingIntent, Intent intent,
                int resultCode, String resultData, Bundle resultExtras) {
            synchronized (this) {
                decrementPendingBroadcastsLocked();
            }
        }

        // this must be called while synchronized by caller in a synchronized block
        // containing the sending of the broadcaset
        private void incrementPendingBroadcastsLocked() {
            if (mPendingBroadcasts++ == 0) {
                mWakeLock.acquire();
            }
        }

        private void decrementPendingBroadcastsLocked() {
            if (--mPendingBroadcasts == 0) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
        }

        public void clearPendingBroadcastsLocked() {
            if (mPendingBroadcasts > 0) {
                mPendingBroadcasts = 0;
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
        }
    }

    @Override
    public void locationCallbackFinished(ILocationListener listener) {
        //Do not use getReceiverLocked here as that will add the ILocationListener to
        //the receiver list if it is not found.  If it is not found then the
        //LocationListener was removed when it had a pending broadcast and should
        //not be added back.
        synchronized (mLock) {
            IBinder binder = listener.asBinder();
            Receiver receiver = mReceivers.get(binder);
            if (receiver != null) {
                synchronized (receiver) {
                    // so wakelock calls will succeed
                    long identity = Binder.clearCallingIdentity();
                    receiver.decrementPendingBroadcastsLocked();
                    Binder.restoreCallingIdentity(identity);
               }
            }
        }
    }

    private void addProviderLocked(LocationProviderInterface provider) {
        mProviders.add(provider);
        mProvidersByName.put(provider.getName(), provider);
    }

    private void removeProviderLocked(LocationProviderInterface provider) {
        provider.disable();
        mProviders.remove(provider);
        mProvidersByName.remove(provider.getName());
    }

    /**
     * Returns "true" if access to the specified location provider is allowed by the current
     * user's settings. Access to all location providers is forbidden to non-location-provider
     * processes belonging to background users.
     *
     * @param provider the name of the location provider
     * @return
     */
    private boolean isAllowedByCurrentUserSettingsLocked(String provider) {
        if (mEnabledProviders.contains(provider)) {
            return true;
        }
        if (mDisabledProviders.contains(provider)) {
            return false;
        }
        // Use system settings
        ContentResolver resolver = mContext.getContentResolver();

        return Settings.Secure.isLocationProviderEnabledForUser(resolver, provider, mCurrentUserId);
    }

    /**
     * Returns "true" if access to the specified location provider is allowed by the specified
     * user's settings. Access to all location providers is forbidden to non-location-provider
     * processes belonging to background users.
     *
     * @param provider the name of the location provider
     * @param uid the requestor's UID
     * @return
     */
    private boolean isAllowedByUserSettingsLocked(String provider, int uid) {
        if (UserHandle.getUserId(uid) != mCurrentUserId && !isUidALocationProvider(uid)) {
            return false;
        }
        return isAllowedByCurrentUserSettingsLocked(provider);
    }

    /**
     * Returns the permission string associated with the specified resolution level.
     *
     * @param resolutionLevel the resolution level
     * @return the permission string
     */
    private String getResolutionPermission(int resolutionLevel) {
        switch (resolutionLevel) {
            case RESOLUTION_LEVEL_FINE:
                return android.Manifest.permission.ACCESS_FINE_LOCATION;
            case RESOLUTION_LEVEL_COARSE:
                return android.Manifest.permission.ACCESS_COARSE_LOCATION;
            default:
                return null;
        }
    }

    /**
     * Returns the resolution level allowed to the given PID/UID pair.
     *
     * @param pid the PID
     * @param uid the UID
     * @return resolution level allowed to the pid/uid pair
     */
    private int getAllowedResolutionLevel(int pid, int uid) {
        if (mContext.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION,
                pid, uid) == PackageManager.PERMISSION_GRANTED) {
            return RESOLUTION_LEVEL_FINE;
        } else if (mContext.checkPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                pid, uid) == PackageManager.PERMISSION_GRANTED) {
            return RESOLUTION_LEVEL_COARSE;
        } else {
            return RESOLUTION_LEVEL_NONE;
        }
    }

    /**
     * Returns the resolution level allowed to the caller
     *
     * @return resolution level allowed to caller
     */
    private int getCallerAllowedResolutionLevel() {
        return getAllowedResolutionLevel(Binder.getCallingPid(), Binder.getCallingUid());
    }

    /**
     * Throw SecurityException if specified resolution level is insufficient to use geofences.
     *
     * @param allowedResolutionLevel resolution level allowed to caller
     */
    private void checkResolutionLevelIsSufficientForGeofenceUse(int allowedResolutionLevel) {
        if (allowedResolutionLevel < RESOLUTION_LEVEL_FINE) {
            throw new SecurityException("Geofence usage requires ACCESS_FINE_LOCATION permission");
        }
    }

    /**
     * Return the minimum resolution level required to use the specified location provider.
     *
     * @param provider the name of the location provider
     * @return minimum resolution level required for provider
     */
    private int getMinimumResolutionLevelForProviderUse(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider) ||
                LocationManager.PASSIVE_PROVIDER.equals(provider)) {
            // gps and passive providers require FINE permission
            return RESOLUTION_LEVEL_FINE;
        } else if (LocationManager.NETWORK_PROVIDER.equals(provider) ||
                LocationManager.FUSED_PROVIDER.equals(provider)) {
            // network and fused providers are ok with COARSE or FINE
            return RESOLUTION_LEVEL_COARSE;
        } else {
            // mock providers
            LocationProviderInterface lp = mMockProviders.get(provider);
            if (lp != null) {
                ProviderProperties properties = lp.getProperties();
                if (properties != null) {
                    if (properties.mRequiresSatellite) {
                        // provider requiring satellites require FINE permission
                        return RESOLUTION_LEVEL_FINE;
                    } else if (properties.mRequiresNetwork || properties.mRequiresCell) {
                        // provider requiring network and or cell require COARSE or FINE
                        return RESOLUTION_LEVEL_COARSE;
                    }
                }
            }
        }
        return RESOLUTION_LEVEL_FINE; // if in doubt, require FINE
    }

    /**
     * Throw SecurityException if specified resolution level is insufficient to use the named
     * location provider.
     *
     * @param allowedResolutionLevel resolution level allowed to caller
     * @param providerName the name of the location provider
     */
    private void checkResolutionLevelIsSufficientForProviderUse(int allowedResolutionLevel,
            String providerName) {
        int requiredResolutionLevel = getMinimumResolutionLevelForProviderUse(providerName);
        if (allowedResolutionLevel < requiredResolutionLevel) {
            switch (requiredResolutionLevel) {
                case RESOLUTION_LEVEL_FINE:
                    throw new SecurityException("\"" + providerName + "\" location provider " +
                            "requires ACCESS_FINE_LOCATION permission.");
                case RESOLUTION_LEVEL_COARSE:
                    throw new SecurityException("\"" + providerName + "\" location provider " +
                            "requires ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission.");
                default:
                    throw new SecurityException("Insufficient permission for \"" + providerName +
                            "\" location provider.");
            }
        }
    }

    /**
     * Throw SecurityException if WorkSource use is not allowed (i.e. can't blame other packages
     * for battery).
     */
    private void checkDeviceStatsAllowed() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_DEVICE_STATS, null);
    }

    private void checkUpdateAppOpsAllowed() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.UPDATE_APP_OPS_STATS, null);
    }

    public static int resolutionLevelToOp(int allowedResolutionLevel) {
        if (allowedResolutionLevel != RESOLUTION_LEVEL_NONE) {
            if (allowedResolutionLevel == RESOLUTION_LEVEL_COARSE) {
                return AppOpsManager.OP_COARSE_LOCATION;
            } else {
                return AppOpsManager.OP_FINE_LOCATION;
            }
        }
        return -1;
    }

    boolean reportLocationAccessNoThrow(int uid, String packageName, int allowedResolutionLevel) {
        int op = resolutionLevelToOp(allowedResolutionLevel);
        if (op >= 0) {
            if (mAppOps.noteOpNoThrow(op, uid, packageName) != AppOpsManager.MODE_ALLOWED) {
                return false;
            }
        }
        return true;
    }

    boolean checkLocationAccess(int uid, String packageName, int allowedResolutionLevel) {
        int op = resolutionLevelToOp(allowedResolutionLevel);
        if (op >= 0) {
            int mode = mAppOps.checkOp(op, uid, packageName);
            if (mode != AppOpsManager.MODE_ALLOWED && mode != AppOpsManager.MODE_ASK ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns all providers by name, including passive, but excluding
     * fused, also including ones that are not permitted to
     * be accessed by the calling activity or are currently disabled.
     */
    @Override
    public List<String> getAllProviders() {
        ArrayList<String> out;
        synchronized (mLock) {
            out = new ArrayList<String>(mProviders.size());
            for (LocationProviderInterface provider : mProviders) {
                String name = provider.getName();
                if (LocationManager.FUSED_PROVIDER.equals(name)) {
                    continue;
                }
                out.add(name);
            }
        }

        if (D) Log.d(TAG, "getAllProviders()=" + out);
        return out;
    }

    /**
     * Return all providers by name, that match criteria and are optionally
     * enabled.
     * Can return passive provider, but never returns fused provider.
     */
    @Override
    public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        int allowedResolutionLevel = getCallerAllowedResolutionLevel();
        ArrayList<String> out;
        int uid = Binder.getCallingUid();;
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                out = new ArrayList<String>(mProviders.size());
                for (LocationProviderInterface provider : mProviders) {
                    String name = provider.getName();
                    if (LocationManager.FUSED_PROVIDER.equals(name)) {
                        continue;
                    }
                    if (allowedResolutionLevel >= getMinimumResolutionLevelForProviderUse(name)) {
                        if (enabledOnly && !isAllowedByUserSettingsLocked(name, uid)) {
                            continue;
                        }
                        if (criteria != null && !LocationProvider.propertiesMeetCriteria(
                                name, provider.getProperties(), criteria)) {
                            continue;
                        }
                        out.add(name);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        if (D) Log.d(TAG, "getProviders()=" + out);
        return out;
    }

    /**
     * Return the name of the best provider given a Criteria object.
     * This method has been deprecated from the public API,
     * and the whole LocationProvider (including #meetsCriteria)
     * has been deprecated as well. So this method now uses
     * some simplified logic.
     */
    @Override
    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        String result = null;

        List<String> providers = getProviders(criteria, enabledOnly);
        if (!providers.isEmpty()) {
            result = pickBest(providers);
            if (D) Log.d(TAG, "getBestProvider(" + criteria + ", " + enabledOnly + ")=" + result);
            return result;
        }
        providers = getProviders(null, enabledOnly);
        if (!providers.isEmpty()) {
            result = pickBest(providers);
            if (D) Log.d(TAG, "getBestProvider(" + criteria + ", " + enabledOnly + ")=" + result);
            return result;
        }

        if (D) Log.d(TAG, "getBestProvider(" + criteria + ", " + enabledOnly + ")=" + result);
        return null;
    }

    private String pickBest(List<String> providers) {
        if (providers.contains(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        } else {
            return providers.get(0);
        }
    }

    @Override
    public boolean providerMeetsCriteria(String provider, Criteria criteria) {
        LocationProviderInterface p = mProvidersByName.get(provider);
        if (p == null) {
            throw new IllegalArgumentException("provider=" + provider);
        }

        boolean result = LocationProvider.propertiesMeetCriteria(
                p.getName(), p.getProperties(), criteria);
        if (D) Log.d(TAG, "providerMeetsCriteria(" + provider + ", " + criteria + ")=" + result);
        return result;
    }

    private void updateProvidersLocked() {
        boolean changesMade = false;
        for (int i = mProviders.size() - 1; i >= 0; i--) {
            LocationProviderInterface p = mProviders.get(i);
            boolean isEnabled = p.isEnabled();
            String name = p.getName();
            boolean shouldBeEnabled = isAllowedByCurrentUserSettingsLocked(name);
            if (isEnabled && !shouldBeEnabled) {
                updateProviderListenersLocked(name, false, mCurrentUserId);
                changesMade = true;
            } else if (!isEnabled && shouldBeEnabled) {
                updateProviderListenersLocked(name, true, mCurrentUserId);
                changesMade = true;
            }
        }
        if (changesMade) {
            mContext.sendBroadcastAsUser(new Intent(LocationManager.PROVIDERS_CHANGED_ACTION),
                    UserHandle.ALL);
            mContext.sendBroadcastAsUser(new Intent(LocationManager.MODE_CHANGED_ACTION),
                    UserHandle.ALL);
        }
    }

    private void updateProviderListenersLocked(String provider, boolean enabled, int userId) {
        int listeners = 0;

        LocationProviderInterface p = mProvidersByName.get(provider);
        if (p == null) return;

        ArrayList<Receiver> deadReceivers = null;

        ArrayList<UpdateRecord> records = mRecordsByProvider.get(provider);
        if (records != null) {
            final int N = records.size();
            for (int i = 0; i < N; i++) {
                UpdateRecord record = records.get(i);
                if (UserHandle.getUserId(record.mReceiver.mUid) == userId) {
                    // Sends a notification message to the receiver
                    if (!record.mReceiver.callProviderEnabledLocked(provider, enabled)) {
                        if (deadReceivers == null) {
                            deadReceivers = new ArrayList<Receiver>();
                        }
                        deadReceivers.add(record.mReceiver);
                    }
                    listeners++;
                }
            }
        }

        if (deadReceivers != null) {
            for (int i = deadReceivers.size() - 1; i >= 0; i--) {
                removeUpdatesLocked(deadReceivers.get(i));
            }
        }

        if (enabled) {
            p.enable();
            if (listeners > 0) {
                applyRequirementsLocked(provider);
            }
        } else {
            p.disable();
        }
    }

    private void applyRequirementsLocked(String provider) {
        LocationProviderInterface p = mProvidersByName.get(provider);
        if (p == null) return;

        ArrayList<UpdateRecord> records = mRecordsByProvider.get(provider);
        WorkSource worksource = new WorkSource();
        ProviderRequest providerRequest = new ProviderRequest();

        if (records != null) {
            for (UpdateRecord record : records) {
                if (UserHandle.getUserId(record.mReceiver.mUid) == mCurrentUserId) {
                    if (checkLocationAccess(record.mReceiver.mUid, record.mReceiver.mPackageName,
                            record.mReceiver.mAllowedResolutionLevel)) {
                        LocationRequest locationRequest = record.mRequest;
                        providerRequest.locationRequests.add(locationRequest);
                        if (locationRequest.getInterval() < providerRequest.interval) {
                            providerRequest.reportLocation = true;
                            providerRequest.interval = locationRequest.getInterval();
                        }
                    }
                }
            }

            if (providerRequest.reportLocation) {
                // calculate who to blame for power
                // This is somewhat arbitrary. We pick a threshold interval
                // that is slightly higher that the minimum interval, and
                // spread the blame across all applications with a request
                // under that threshold.
                long thresholdInterval = (providerRequest.interval + 1000) * 3 / 2;
                for (UpdateRecord record : records) {
                    if (UserHandle.getUserId(record.mReceiver.mUid) == mCurrentUserId) {
                        LocationRequest locationRequest = record.mRequest;
                        if (locationRequest.getInterval() <= thresholdInterval) {
                            if (record.mReceiver.mWorkSource != null
                                    && record.mReceiver.mWorkSource.size() > 0
                                    && record.mReceiver.mWorkSource.getName(0) != null) {
                                // Assign blame to another work source.
                                // Can only assign blame if the WorkSource contains names.
                                worksource.add(record.mReceiver.mWorkSource);
                            } else {
                                // Assign blame to caller.
                                worksource.add(
                                        record.mReceiver.mUid,
                                        record.mReceiver.mPackageName);
                            }
                        }
                    }
                }
            }
        }

        if (D) Log.d(TAG, "provider request: " + provider + " " + providerRequest);
        p.setRequest(providerRequest, worksource);
    }

    private class UpdateRecord {
        final String mProvider;
        final LocationRequest mRequest;
        final Receiver mReceiver;
        Location mLastFixBroadcast;
        long mLastStatusBroadcast;

        /**
         * Note: must be constructed with lock held.
         */
        UpdateRecord(String provider, LocationRequest request, Receiver receiver) {
            mProvider = provider;
            mRequest = request;
            mReceiver = receiver;

            ArrayList<UpdateRecord> records = mRecordsByProvider.get(provider);
            if (records == null) {
                records = new ArrayList<UpdateRecord>();
                mRecordsByProvider.put(provider, records);
            }
            if (!records.contains(this)) {
                records.add(this);
            }
        }

        /**
         * Method to be called when a record will no longer be used.  Calling this multiple times
         * must have the same effect as calling it once.
         */
        void disposeLocked(boolean removeReceiver) {
            // remove from mRecordsByProvider
            ArrayList<UpdateRecord> globalRecords = mRecordsByProvider.get(this.mProvider);
            if (globalRecords != null) {
                globalRecords.remove(this);
            }

            if (!removeReceiver) return;  // the caller will handle the rest

            // remove from Receiver#mUpdateRecords
            HashMap<String, UpdateRecord> receiverRecords = mReceiver.mUpdateRecords;
            if (receiverRecords != null) {
                receiverRecords.remove(this.mProvider);

                // and also remove the Receiver if it has no more update records
                if (removeReceiver && receiverRecords.size() == 0) {
                    removeUpdatesLocked(mReceiver);
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("UpdateRecord[");
            s.append(mProvider);
            s.append(' ').append(mReceiver.mPackageName).append('(');
            s.append(mReceiver.mUid).append(')');
            s.append(' ').append(mRequest);
            s.append(']');
            return s.toString();
        }
    }

    private Receiver getReceiverLocked(ILocationListener listener, int pid, int uid,
            String packageName, WorkSource workSource, boolean hideFromAppOps) {
        IBinder binder = listener.asBinder();
        Receiver receiver = mReceivers.get(binder);
        if (receiver == null) {
            receiver = new Receiver(listener, null, pid, uid, packageName, workSource,
                    hideFromAppOps);
            mReceivers.put(binder, receiver);

            try {
                receiver.getListener().asBinder().linkToDeath(receiver, 0);
            } catch (RemoteException e) {
                Slog.e(TAG, "linkToDeath failed:", e);
                return null;
            }
        }
        return receiver;
    }

    private Receiver getReceiverLocked(PendingIntent intent, int pid, int uid, String packageName,
            WorkSource workSource, boolean hideFromAppOps) {
        Receiver receiver = mReceivers.get(intent);
        if (receiver == null) {
            receiver = new Receiver(null, intent, pid, uid, packageName, workSource,
                    hideFromAppOps);
            mReceivers.put(intent, receiver);
        }
        return receiver;
    }

    /**
     * Creates a LocationRequest based upon the supplied LocationRequest that to meets resolution
     * and consistency requirements.
     *
     * @param request the LocationRequest from which to create a sanitized version
     * @return a version of request that meets the given resolution and consistency requirements
     * @hide
     */
    private LocationRequest createSanitizedRequest(LocationRequest request, int resolutionLevel) {
        LocationRequest sanitizedRequest = new LocationRequest(request);
        if (resolutionLevel < RESOLUTION_LEVEL_FINE) {
            switch (sanitizedRequest.getQuality()) {
                case LocationRequest.ACCURACY_FINE:
                    sanitizedRequest.setQuality(LocationRequest.ACCURACY_BLOCK);
                    break;
                case LocationRequest.POWER_HIGH:
                    sanitizedRequest.setQuality(LocationRequest.POWER_LOW);
                    break;
            }
            // throttle
            if (sanitizedRequest.getInterval() < LocationFudger.FASTEST_INTERVAL_MS) {
                sanitizedRequest.setInterval(LocationFudger.FASTEST_INTERVAL_MS);
            }
            if (sanitizedRequest.getFastestInterval() < LocationFudger.FASTEST_INTERVAL_MS) {
                sanitizedRequest.setFastestInterval(LocationFudger.FASTEST_INTERVAL_MS);
            }
        }
        // make getFastestInterval() the minimum of interval and fastest interval
        if (sanitizedRequest.getFastestInterval() > sanitizedRequest.getInterval()) {
            request.setFastestInterval(request.getInterval());
        }
        return sanitizedRequest;
    }

    private void checkPackageName(String packageName) {
        if (packageName == null) {
            throw new SecurityException("invalid package name: " + packageName);
        }
        int uid = Binder.getCallingUid();
        String[] packages = mPackageManager.getPackagesForUid(uid);
        if (packages == null) {
            throw new SecurityException("invalid UID " + uid);
        }
        for (String pkg : packages) {
            if (packageName.equals(pkg)) return;
        }
        throw new SecurityException("invalid package name: " + packageName);
    }

    private void checkPendingIntent(PendingIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("invalid pending intent: " + intent);
        }
    }

    private Receiver checkListenerOrIntentLocked(ILocationListener listener, PendingIntent intent,
            int pid, int uid, String packageName, WorkSource workSource, boolean hideFromAppOps) {
        if (intent == null && listener == null) {
            throw new IllegalArgumentException("need either listener or intent");
        } else if (intent != null && listener != null) {
            throw new IllegalArgumentException("cannot register both listener and intent");
        } else if (intent != null) {
            checkPendingIntent(intent);
            return getReceiverLocked(intent, pid, uid, packageName, workSource, hideFromAppOps);
        } else {
            return getReceiverLocked(listener, pid, uid, packageName, workSource, hideFromAppOps);
        }
    }

    @Override
    public void requestLocationUpdates(LocationRequest request, ILocationListener listener,
            PendingIntent intent, String packageName) {
        if (request == null) request = DEFAULT_LOCATION_REQUEST;
        checkPackageName(packageName);
        int allowedResolutionLevel = getCallerAllowedResolutionLevel();
        checkResolutionLevelIsSufficientForProviderUse(allowedResolutionLevel,
                request.getProvider());
        WorkSource workSource = request.getWorkSource();
        if (workSource != null && workSource.size() > 0) {
            checkDeviceStatsAllowed();
        }
        boolean hideFromAppOps = request.getHideFromAppOps();
        if (hideFromAppOps) {
            checkUpdateAppOpsAllowed();
        }
        LocationRequest sanitizedRequest = createSanitizedRequest(request, allowedResolutionLevel);

        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();
        // providers may use public location API's, need to clear identity
        long identity = Binder.clearCallingIdentity();
        try {
            // We don't check for MODE_IGNORED here; we will do that when we go to deliver
            // a location.
            checkLocationAccess(uid, packageName, allowedResolutionLevel);

            synchronized (mLock) {
                Receiver receiver = checkListenerOrIntentLocked(listener, intent, pid, uid,
                        packageName, workSource, hideFromAppOps);
                if (receiver != null) {
                        requestLocationUpdatesLocked(sanitizedRequest, receiver, pid,
                                                     uid, packageName);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void requestLocationUpdatesLocked(LocationRequest request, Receiver receiver,
            int pid, int uid, String packageName) {
        // Figure out the provider. Either its explicitly request (legacy use cases), or
        // use the fused provider
        if (request == null) request = DEFAULT_LOCATION_REQUEST;
        String name = request.getProvider();
        if (name == null) {
            throw new IllegalArgumentException("provider name must not be null");
        }

        if (D) Log.d(TAG, "request " + Integer.toHexString(System.identityHashCode(receiver))
                + " " + name + " " + request + " from " + packageName + "(" + uid + ")");
        LocationProviderInterface provider = mProvidersByName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("provider doesn't exist: " + name);
        }

        UpdateRecord record = new UpdateRecord(name, request, receiver);
        UpdateRecord oldRecord = receiver.mUpdateRecords.put(name, record);
        if (oldRecord != null) {
            oldRecord.disposeLocked(false);
        }

        boolean isProviderEnabled = isAllowedByUserSettingsLocked(name, uid);
        if (isProviderEnabled) {
            applyRequirementsLocked(name);
        } else {
            // Notify the listener that updates are currently disabled
            receiver.callProviderEnabledLocked(name, false);
        }
        // Update the monitoring here just in case multiple location requests were added to the
        // same receiver (this request may be high power and the initial might not have been).
        receiver.updateMonitoring(true);
    }

    @Override
    public void removeUpdates(ILocationListener listener, PendingIntent intent,
            String packageName) {
        checkPackageName(packageName);

        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();

        synchronized (mLock) {
            WorkSource workSource = null;
            boolean hideFromAppOps = false;
            Receiver receiver = checkListenerOrIntentLocked(listener, intent, pid, uid,
                    packageName, workSource, hideFromAppOps);

            // providers may use public location API's, need to clear identity
            long identity = Binder.clearCallingIdentity();
            try {
                if (receiver != null) {
                    removeUpdatesLocked(receiver);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private void removeUpdatesLocked(Receiver receiver) {
        if (D) Log.i(TAG, "remove " + Integer.toHexString(System.identityHashCode(receiver)));

        if (mReceivers.remove(receiver.mKey) != null && receiver.isListener()) {
            receiver.getListener().asBinder().unlinkToDeath(receiver, 0);
            synchronized (receiver) {
                receiver.clearPendingBroadcastsLocked();
            }
        }

        receiver.updateMonitoring(false);

        // Record which providers were associated with this listener
        HashSet<String> providers = new HashSet<String>();
        HashMap<String, UpdateRecord> oldRecords = receiver.mUpdateRecords;
        if (oldRecords != null) {
            // Call dispose() on the obsolete update records.
            for (UpdateRecord record : oldRecords.values()) {
                record.disposeLocked(false);
            }
            // Accumulate providers
            providers.addAll(oldRecords.keySet());
        }

        // update provider
        for (String provider : providers) {
            // If provider is already disabled, don't need to do anything
            if (!isAllowedByCurrentUserSettingsLocked(provider)) {
                continue;
            }

            applyRequirementsLocked(provider);
        }
    }

    private void applyAllProviderRequirementsLocked() {
        for (LocationProviderInterface p : mProviders) {
            // If provider is already disabled, don't need to do anything
            if (!isAllowedByCurrentUserSettingsLocked(p.getName())) {
                continue;
            }

            applyRequirementsLocked(p.getName());
        }
    }

    @Override
    public Location getLastLocation(LocationRequest request, String packageName) {
        if (D) Log.d(TAG, "getLastLocation: " + request);
        if (request == null) request = DEFAULT_LOCATION_REQUEST;
        int allowedResolutionLevel = getCallerAllowedResolutionLevel();
        checkPackageName(packageName);
        checkResolutionLevelIsSufficientForProviderUse(allowedResolutionLevel,
                request.getProvider());
        // no need to sanitize this request, as only the provider name is used

        final int uid = Binder.getCallingUid();
        final long identity = Binder.clearCallingIdentity();
        try {
            if (mBlacklist.isBlacklisted(packageName)) {
                if (D) Log.d(TAG, "not returning last loc for blacklisted app: " +
                        packageName);
                return null;
            }

            if (!reportLocationAccessNoThrow(uid, packageName, allowedResolutionLevel)) {
                if (D) Log.d(TAG, "not returning last loc for no op app: " +
                        packageName);
                return null;
            }

            synchronized (mLock) {
                // Figure out the provider. Either its explicitly request (deprecated API's),
                // or use the fused provider
                String name = request.getProvider();
                if (name == null) name = LocationManager.FUSED_PROVIDER;
                LocationProviderInterface provider = mProvidersByName.get(name);
                if (provider == null) return null;

                if (!isAllowedByUserSettingsLocked(name, uid)) return null;

                Location location;
                if (allowedResolutionLevel < RESOLUTION_LEVEL_FINE) {
                    // Make sure that an app with coarse permissions can't get frequent location
                    // updates by calling LocationManager.getLastKnownLocation repeatedly.
                    location = mLastLocationCoarseInterval.get(name);
                } else {
                    location = mLastLocation.get(name);
                }
                if (location == null) {
                    return null;
                }
                if (allowedResolutionLevel < RESOLUTION_LEVEL_FINE) {
                    Location noGPSLocation = location.getExtraLocation(Location.EXTRA_NO_GPS_LOCATION);
                    if (noGPSLocation != null) {
                        return new Location(mLocationFudger.getOrCreate(noGPSLocation));
                    }
                } else {
                    return new Location(location);
                }
            }
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void requestGeofence(LocationRequest request, Geofence geofence, PendingIntent intent,
            String packageName) {
        if (request == null) request = DEFAULT_LOCATION_REQUEST;
        int allowedResolutionLevel = getCallerAllowedResolutionLevel();
        checkResolutionLevelIsSufficientForGeofenceUse(allowedResolutionLevel);
        checkPendingIntent(intent);
        checkPackageName(packageName);
        checkResolutionLevelIsSufficientForProviderUse(allowedResolutionLevel,
                request.getProvider());
        LocationRequest sanitizedRequest = createSanitizedRequest(request, allowedResolutionLevel);

        if (D) Log.d(TAG, "requestGeofence: " + sanitizedRequest + " " + geofence + " " + intent);

        // geo-fence manager uses the public location API, need to clear identity
        int uid = Binder.getCallingUid();
        if (UserHandle.getUserId(uid) != UserHandle.USER_OWNER) {
            // temporary measure until geofences work for secondary users
            Log.w(TAG, "proximity alerts are currently available only to the primary user");
            return;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            if (mGeoFencer != null && mGeoFencerEnabled) {
                long expiration;
                if (sanitizedRequest.getExpireAt() == Long.MAX_VALUE) {
                    expiration = -1; // -1 means forever
                } else {
                    expiration = sanitizedRequest.getExpireAt() - SystemClock.elapsedRealtime();
                }
                mGeoFencer.add(new GeoFenceParams(uid, geofence.getLatitude(),
                                                  geofence.getLongitude(), geofence.getRadius(),
                                                  expiration, intent, packageName));
            } else {
                mGeofenceManager.addFence(sanitizedRequest, geofence, intent,
                                          allowedResolutionLevel, uid, packageName);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void removeGeofence(Geofence geofence, PendingIntent intent, String packageName) {
        checkResolutionLevelIsSufficientForGeofenceUse(getCallerAllowedResolutionLevel());
        checkPendingIntent(intent);
        checkPackageName(packageName);

        if (D) Log.d(TAG, "removeGeofence: " + geofence + " " + intent);

        // geo-fence manager uses the public location API, need to clear identity
        long identity = Binder.clearCallingIdentity();
        try {
            if (mGeoFencer != null && mGeoFencerEnabled) {
                mGeoFencer.remove(intent);
            } else {
                mGeofenceManager.removeFence(geofence, intent);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }


    @Override
    public boolean addGpsStatusListener(IGpsStatusListener listener, String packageName) {
        if (mGpsStatusProvider == null) {
            return false;
        }
        int allowedResolutionLevel = getCallerAllowedResolutionLevel();
        checkResolutionLevelIsSufficientForProviderUse(allowedResolutionLevel,
                LocationManager.GPS_PROVIDER);

        final int uid = Binder.getCallingUid();
        final long ident = Binder.clearCallingIdentity();
        try {
            if (!checkLocationAccess(uid, packageName, allowedResolutionLevel)) {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        try {
            mGpsStatusProvider.addGpsStatusListener(listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "mGpsStatusProvider.addGpsStatusListener failed", e);
            return false;
        }
        return true;
    }

    @Override
    public void removeGpsStatusListener(IGpsStatusListener listener) {
        synchronized (mLock) {
            try {
                mGpsStatusProvider.removeGpsStatusListener(listener);
            } catch (Exception e) {
                Slog.e(TAG, "mGpsStatusProvider.removeGpsStatusListener failed", e);
            }
        }
    }

    @Override
    public boolean sendExtraCommand(String provider, String command, Bundle extras) {
        if (provider == null) {
            // throw NullPointerException to remain compatible with previous implementation
            throw new NullPointerException();
        }
        checkResolutionLevelIsSufficientForProviderUse(getCallerAllowedResolutionLevel(),
                provider);

        // and check for ACCESS_LOCATION_EXTRA_COMMANDS
        if ((mContext.checkCallingOrSelfPermission(ACCESS_LOCATION_EXTRA_COMMANDS)
                != PackageManager.PERMISSION_GRANTED)) {
            throw new SecurityException("Requires ACCESS_LOCATION_EXTRA_COMMANDS permission");
        }

        synchronized (mLock) {
            LocationProviderInterface p = mProvidersByName.get(provider);
            if (p == null) return false;

            return p.sendExtraCommand(command, extras);
        }
    }

    @Override
    public boolean sendNiResponse(int notifId, int userResponse) {
        if (Binder.getCallingUid() != Process.myUid()) {
            throw new SecurityException(
                    "calling sendNiResponse from outside of the system is not allowed");
        }
        try {
            return mNetInitiatedListener.sendNiResponse(notifId, userResponse);
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException in LocationManagerService.sendNiResponse");
            return false;
        }
    }

    /**
     * @return null if the provider does not exist
     * @throws SecurityException if the provider is not allowed to be
     * accessed by the caller
     */
    @Override
    public ProviderProperties getProviderProperties(String provider) {
        if (mProvidersByName.get(provider) == null) {
          return null;
        }

        checkResolutionLevelIsSufficientForProviderUse(getCallerAllowedResolutionLevel(),
                provider);

        LocationProviderInterface p;
        synchronized (mLock) {
            p = mProvidersByName.get(provider);
        }

        if (p == null) return null;
        return p.getProperties();
    }

    @Override
    public boolean isProviderEnabled(String provider) {
        // TODO: remove this check in next release, see b/10696351
        checkResolutionLevelIsSufficientForProviderUse(getCallerAllowedResolutionLevel(),
                provider);

        // Fused provider is accessed indirectly via criteria rather than the provider-based APIs,
        // so we discourage its use
        if (LocationManager.FUSED_PROVIDER.equals(provider)) return false;

        int uid = Binder.getCallingUid();
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                LocationProviderInterface p = mProvidersByName.get(provider);
                if (p == null) return false;

                return isAllowedByUserSettingsLocked(provider, uid);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns "true" if the UID belongs to a bound location provider.
     *
     * @param uid the uid
     * @return true if uid belongs to a bound location provider
     */
    private boolean isUidALocationProvider(int uid) {
        if (uid == Process.SYSTEM_UID) {
            return true;
        }
        if (mGeocodeProvider != null) {
            if (doesPackageHaveUid(uid, mGeocodeProvider.getConnectedPackageName())) return true;
        }
        for (LocationProviderProxy proxy : mProxyProviders) {
            if (doesPackageHaveUid(uid, proxy.getConnectedPackageName())) return true;
        }
        return false;
    }

    private void checkCallerIsProvider() {
        if (mContext.checkCallingOrSelfPermission(INSTALL_LOCATION_PROVIDER)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Previously we only used the INSTALL_LOCATION_PROVIDER
        // check. But that is system or signature
        // protection level which is not flexible enough for
        // providers installed oustide the system image. So
        // also allow providers with a UID matching the
        // currently bound package name

        if (isUidALocationProvider(Binder.getCallingUid())) {
            return;
        }

        throw new SecurityException("need INSTALL_LOCATION_PROVIDER permission, " +
                "or UID of a currently bound location provider");
    }

    private boolean doesPackageHaveUid(int uid, String packageName) {
        if (packageName == null) {
            return false;
        }
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName, 0);
            if (appInfo.uid != uid) {
                return false;
            }
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    @Override
    public void reportLocation(Location location, boolean passive) {
        checkCallerIsProvider();

        if (!location.isComplete()) {
            Log.w(TAG, "Dropping incomplete location: " + location);
            return;
        }

        mLocationHandler.removeMessages(MSG_LOCATION_CHANGED, location);
        Message m = Message.obtain(mLocationHandler, MSG_LOCATION_CHANGED, location);
        m.arg1 = (passive ? 1 : 0);
        mLocationHandler.sendMessageAtFrontOfQueue(m);
    }


    private static boolean shouldBroadcastSafe(
            Location loc, Location lastLoc, UpdateRecord record, long now) {
        // Always broadcast the first update
        if (lastLoc == null) {
            return true;
        }

        // Check whether sufficient time has passed
        long minTime = record.mRequest.getFastestInterval();
        long delta = (loc.getElapsedRealtimeNanos() - lastLoc.getElapsedRealtimeNanos())
                / NANOS_PER_MILLI;
        if (delta < minTime - MAX_PROVIDER_SCHEDULING_JITTER_MS) {
            return false;
        }

        // Check whether sufficient distance has been traveled
        double minDistance = record.mRequest.getSmallestDisplacement();
        if (minDistance > 0.0) {
            if (loc.distanceTo(lastLoc) <= minDistance) {
                return false;
            }
        }

        // Check whether sufficient number of udpates is left
        if (record.mRequest.getNumUpdates() <= 0) {
            return false;
        }

        // Check whether the expiry date has passed
        if (record.mRequest.getExpireAt() < now) {
            return false;
        }

        return true;
    }

    private void handleLocationChangedLocked(Location location, boolean passive) {
        if (D) Log.d(TAG, "incoming location: " + location);

        long now = SystemClock.elapsedRealtime();
        String provider = (passive ? LocationManager.PASSIVE_PROVIDER : location.getProvider());

        // Skip if the provider is unknown.
        LocationProviderInterface p = mProvidersByName.get(provider);
        if (p == null) return;

        // Update last known locations
        Location noGPSLocation = location.getExtraLocation(Location.EXTRA_NO_GPS_LOCATION);
        Location lastNoGPSLocation = null;
        Location lastLocation = mLastLocation.get(provider);
        if (lastLocation == null) {
            lastLocation = new Location(provider);
            mLastLocation.put(provider, lastLocation);
        } else {
            lastNoGPSLocation = lastLocation.getExtraLocation(Location.EXTRA_NO_GPS_LOCATION);
            if (noGPSLocation == null && lastNoGPSLocation != null) {
                // New location has no no-GPS location: adopt last no-GPS location. This is set
                // directly into location because we do not want to notify COARSE clients.
                location.setExtraLocation(Location.EXTRA_NO_GPS_LOCATION, lastNoGPSLocation);
            }
        }
        lastLocation.set(location);

        // Update last known coarse interval location if enough time has passed.
        Location lastLocationCoarseInterval = mLastLocationCoarseInterval.get(provider);
        if (lastLocationCoarseInterval == null) {
            lastLocationCoarseInterval = new Location(location);
            mLastLocationCoarseInterval.put(provider, lastLocationCoarseInterval);
        }
        long timeDiffNanos = location.getElapsedRealtimeNanos()
                - lastLocationCoarseInterval.getElapsedRealtimeNanos();
        if (timeDiffNanos > LocationFudger.FASTEST_INTERVAL_MS * NANOS_PER_MILLI) {
            lastLocationCoarseInterval.set(location);
        }
        // Don't ever return a coarse location that is more recent than the allowed update
        // interval (i.e. don't allow an app to keep registering and unregistering for
        // location updates to overcome the minimum interval).
        noGPSLocation =
                lastLocationCoarseInterval.getExtraLocation(Location.EXTRA_NO_GPS_LOCATION);

        // Skip if there are no UpdateRecords for this provider.
        ArrayList<UpdateRecord> records = mRecordsByProvider.get(provider);
        if (records == null || records.size() == 0) return;

        // Fetch coarse location
        Location coarseLocation = null;
        if (noGPSLocation != null) {
            coarseLocation = mLocationFudger.getOrCreate(noGPSLocation);
        }

        // Fetch latest status update time
        long newStatusUpdateTime = p.getStatusUpdateTime();

       // Get latest status
        Bundle extras = new Bundle();
        int status = p.getStatus(extras);

        ArrayList<Receiver> deadReceivers = null;
        ArrayList<UpdateRecord> deadUpdateRecords = null;

        // Broadcast location or status to all listeners
        for (UpdateRecord r : records) {
            Receiver receiver = r.mReceiver;
            boolean receiverDead = false;

            int receiverUserId = UserHandle.getUserId(receiver.mUid);
            if (receiverUserId != mCurrentUserId && !isUidALocationProvider(receiver.mUid)) {
                if (D) {
                    Log.d(TAG, "skipping loc update for background user " + receiverUserId +
                            " (current user: " + mCurrentUserId + ", app: " +
                            receiver.mPackageName + ")");
                }
                continue;
            }

            if (mBlacklist.isBlacklisted(receiver.mPackageName)) {
                if (D) Log.d(TAG, "skipping loc update for blacklisted app: " +
                        receiver.mPackageName);
                continue;
            }

            if (!reportLocationAccessNoThrow(receiver.mUid, receiver.mPackageName,
                    receiver.mAllowedResolutionLevel)) {
                if (D) Log.d(TAG, "skipping loc update for no op app: " +
                        receiver.mPackageName);
                continue;
            }

            Location notifyLocation = null;
            if (receiver.mAllowedResolutionLevel < RESOLUTION_LEVEL_FINE) {
                notifyLocation = coarseLocation;  // use coarse location
            } else {
                notifyLocation = lastLocation;  // use fine location
            }
            if (notifyLocation != null) {
                Location lastLoc = r.mLastFixBroadcast;
                if ((lastLoc == null) || shouldBroadcastSafe(notifyLocation, lastLoc, r, now)) {
                    if (lastLoc == null) {
                        lastLoc = new Location(notifyLocation);
                        r.mLastFixBroadcast = lastLoc;
                    } else {
                        lastLoc.set(notifyLocation);
                    }
                    if (!receiver.callLocationChangedLocked(notifyLocation)) {
                        Slog.w(TAG, "RemoteException calling onLocationChanged on " + receiver);
                        receiverDead = true;
                    }
                    r.mRequest.decrementNumUpdates();
                }
            }

            long prevStatusUpdateTime = r.mLastStatusBroadcast;
            if ((newStatusUpdateTime > prevStatusUpdateTime) &&
                    (prevStatusUpdateTime != 0 || status != LocationProvider.AVAILABLE)) {

                r.mLastStatusBroadcast = newStatusUpdateTime;
                if (!receiver.callStatusChangedLocked(provider, status, extras)) {
                    receiverDead = true;
                    Slog.w(TAG, "RemoteException calling onStatusChanged on " + receiver);
                }
            }

            // track expired records
            if (r.mRequest.getNumUpdates() <= 0 || r.mRequest.getExpireAt() < now) {
                if (deadUpdateRecords == null) {
                    deadUpdateRecords = new ArrayList<UpdateRecord>();
                }
                deadUpdateRecords.add(r);
            }
            // track dead receivers
            if (receiverDead) {
                if (deadReceivers == null) {
                    deadReceivers = new ArrayList<Receiver>();
                }
                if (!deadReceivers.contains(receiver)) {
                    deadReceivers.add(receiver);
                }
            }
        }

        // remove dead records and receivers outside the loop
        if (deadReceivers != null) {
            for (Receiver receiver : deadReceivers) {
                removeUpdatesLocked(receiver);
            }
        }
        if (deadUpdateRecords != null) {
            for (UpdateRecord r : deadUpdateRecords) {
                r.disposeLocked(true);
            }
            applyRequirementsLocked(provider);
        }
    }

    private class LocationWorkerHandler extends Handler {
        public LocationWorkerHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOCATION_CHANGED:
                    handleLocationChanged((Location) msg.obj, msg.arg1 == 1);
                    break;
            }
        }
    }

    private boolean isMockProvider(String provider) {
        synchronized (mLock) {
            return mMockProviders.containsKey(provider);
        }

    }

    private Location screenLocationLocked(Location location, String provider) {

        if (isMockProvider(LocationManager.NETWORK_PROVIDER)) {
            return location;
        }

        LocationProviderProxy providerProxy =
                (LocationProviderProxy)mProvidersByName.get(LocationManager.NETWORK_PROVIDER);
        if (mComboNlpPackageName == null || providerProxy == null ||
            false == provider.equals(LocationManager.NETWORK_PROVIDER)) {
            return location;
        }

        String connectedNlpPackage = providerProxy.getConnectedPackageName();
        if (connectedNlpPackage == null || !connectedNlpPackage.equals(mComboNlpPackageName)) {
            return location;
        }

        Bundle extras = location.getExtras();
        boolean isBeingScreened = false;
        if (extras == null) {
            extras = new Bundle();
        }

        if (!extras.containsKey(mComboNlpReadyMarker)) {
            // see if Combo Nlp is a passive listener
            ArrayList<UpdateRecord> records =
                mRecordsByProvider.get(LocationManager.PASSIVE_PROVIDER);
            if (records != null) {
                for (UpdateRecord r : records) {
                    if (r.mReceiver.mPackageName.equals(mComboNlpPackageName)) {
                        if (!isBeingScreened) {
                            isBeingScreened = true;
                            extras.putBoolean(mComboNlpScreenMarker, true);
                        }
                        // send location to Combo Nlp for screening
                        if (!r.mReceiver.callLocationChangedLocked(location)) {
                            Slog.w(TAG, "RemoteException calling onLocationChanged on "
                                   + r.mReceiver);
                        } else {
                            if (D) {
                                Log.d(TAG, "Sending location for screening");
                            }
                        }
                    }
                }
            }
            if (isBeingScreened) {
                return null;
            }
            if (D) {
                Log.d(TAG, "Not screening locations");
            }
        } else {
            if (D) {
                Log.d(TAG, "This location is marked as ready for broadcast");
            }
            // clear the ready marker
            extras.remove(mComboNlpReadyMarker);
        }

        return location;
    }

    private void handleLocationChanged(Location location, boolean passive) {
        // create a working copy of the incoming Location so that the service can modify it without
        // disturbing the caller's copy
        Location myLocation = new Location(location);
        String provider = myLocation.getProvider();

        // set "isFromMockProvider" bit if location came from a mock provider. we do not clear this
        // bit if location did not come from a mock provider because passive/fused providers can
        // forward locations from mock providers, and should not grant them legitimacy in doing so.
        if (!myLocation.isFromMockProvider() && isMockProvider(provider)) {
            myLocation.setIsFromMockProvider(true);
        }

        synchronized (mLock) {
            if (isAllowedByCurrentUserSettingsLocked(provider)) {
                if (!passive) {
                    location = screenLocationLocked(location, provider);
                    if (location == null) {
                        return;
                    }
                    // notify passive provider of the new location
                    mPassiveProvider.updateLocation(myLocation);
                }
                handleLocationChangedLocked(myLocation, passive);
            }
        }
    }

    private final PackageMonitor mPackageMonitor = new PackageMonitor() {
        @Override
        public void onPackageDisappeared(String packageName, int reason) {
            // remove all receivers associated with this package name
            synchronized (mLock) {
                ArrayList<Receiver> deadReceivers = null;

                for (Receiver receiver : mReceivers.values()) {
                    if (receiver.mPackageName.equals(packageName)) {
                        if (deadReceivers == null) {
                            deadReceivers = new ArrayList<Receiver>();
                        }
                        deadReceivers.add(receiver);
                    }
                }

                // perform removal outside of mReceivers loop
                if (deadReceivers != null) {
                    for (Receiver receiver : deadReceivers) {
                        removeUpdatesLocked(receiver);
                    }
                }
            }
        }
    };

    // Geocoder

    @Override
    public boolean geocoderIsPresent() {
        return mGeocodeProvider != null;
    }

    @Override
    public String getFromLocation(double latitude, double longitude, int maxResults,
            GeocoderParams params, List<Address> addrs) {
        if (mGeocodeProvider != null) {
            return mGeocodeProvider.getFromLocation(latitude, longitude, maxResults,
                    params, addrs);
        }
        return null;
    }


    @Override
    public String getFromLocationName(String locationName,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude, int maxResults,
            GeocoderParams params, List<Address> addrs) {

        if (mGeocodeProvider != null) {
            return mGeocodeProvider.getFromLocationName(locationName, lowerLeftLatitude,
                    lowerLeftLongitude, upperRightLatitude, upperRightLongitude,
                    maxResults, params, addrs);
        }
        return null;
    }

    // Mock Providers

    private void checkMockPermissionsSafe() {
        boolean allowMocks = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 1;
        if (!allowMocks) {
            throw new SecurityException("Requires ACCESS_MOCK_LOCATION secure setting");
        }

        if (mContext.checkCallingPermission(ACCESS_MOCK_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires ACCESS_MOCK_LOCATION permission");
        }
    }

    @Override
    public void addTestProvider(String name, ProviderProperties properties) {
        checkMockPermissionsSafe();

        if (LocationManager.PASSIVE_PROVIDER.equals(name)) {
            throw new IllegalArgumentException("Cannot mock the passive location provider");
        }

        long identity = Binder.clearCallingIdentity();
        synchronized (mLock) {
            MockProvider provider = new MockProvider(name, this, properties);
            // remove the real provider if we are replacing GPS or network provider
            if (LocationManager.GPS_PROVIDER.equals(name)
                    || LocationManager.NETWORK_PROVIDER.equals(name)
                    || LocationManager.FUSED_PROVIDER.equals(name)) {
                LocationProviderInterface p = mProvidersByName.get(name);
                if (p != null) {
                    removeProviderLocked(p);
                }
            }
            if (mProvidersByName.get(name) != null) {
                throw new IllegalArgumentException("Provider \"" + name + "\" already exists");
            }

            mGeoFencerEnabled = false;

            addProviderLocked(provider);
            mMockProviders.put(name, provider);
            mLastLocation.put(name, null);
            mLastLocationCoarseInterval.put(name, null);
            updateProvidersLocked();
        }
        Binder.restoreCallingIdentity(identity);
    }

    @Override
    public void removeTestProvider(String provider) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.remove(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            long identity = Binder.clearCallingIdentity();
            removeProviderLocked(mProvidersByName.get(provider));

            if (mGeoFencer != null) {
                mGeoFencerEnabled = true;
            }

            // reinstate real provider if available
            LocationProviderInterface realProvider = mRealProviders.get(provider);
            if (realProvider != null) {
                addProviderLocked(realProvider);
            }
            mLastLocation.put(provider, null);
            mLastLocationCoarseInterval.put(provider, null);
            updateProvidersLocked();
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setTestProviderLocation(String provider, Location loc) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            // clear calling identity so INSTALL_LOCATION_PROVIDER permission is not required
            long identity = Binder.clearCallingIdentity();
            mockProvider.setLocation(loc);
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void clearTestProviderLocation(String provider) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            mockProvider.clearLocation();
        }
    }

    @Override
    public void setTestProviderEnabled(String provider, boolean enabled) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            long identity = Binder.clearCallingIdentity();
            if (enabled) {
                mockProvider.enable();
                mEnabledProviders.add(provider);
                mDisabledProviders.remove(provider);
            } else {
                mockProvider.disable();
                mEnabledProviders.remove(provider);
                mDisabledProviders.add(provider);
            }
            updateProvidersLocked();
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void clearTestProviderEnabled(String provider) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            long identity = Binder.clearCallingIdentity();
            mEnabledProviders.remove(provider);
            mDisabledProviders.remove(provider);
            updateProvidersLocked();
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setTestProviderStatus(String provider, int status, Bundle extras, long updateTime) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            mockProvider.setStatus(status, extras, updateTime);
        }
    }

    @Override
    public void clearTestProviderStatus(String provider) {
        checkMockPermissionsSafe();
        synchronized (mLock) {
            MockProvider mockProvider = mMockProviders.get(provider);
            if (mockProvider == null) {
                throw new IllegalArgumentException("Provider \"" + provider + "\" unknown");
            }
            mockProvider.clearStatus();
        }
    }

    private void log(String log) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Slog.d(TAG, log);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump LocationManagerService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        synchronized (mLock) {
            pw.println("Current Location Manager state:");
            pw.println("  Location Listeners:");
            for (Receiver receiver : mReceivers.values()) {
                pw.println("    " + receiver);
            }
            pw.println("  Records by Provider:");
            for (Map.Entry<String, ArrayList<UpdateRecord>> entry : mRecordsByProvider.entrySet()) {
                pw.println("    " + entry.getKey() + ":");
                for (UpdateRecord record : entry.getValue()) {
                    pw.println("      " + record);
                }
            }
            pw.println("  Last Known Locations:");
            for (Map.Entry<String, Location> entry : mLastLocation.entrySet()) {
                String provider = entry.getKey();
                Location location = entry.getValue();
                pw.println("    " + provider + ": " + location);
            }

            pw.println("  Last Known Locations Coarse Intervals:");
            for (Map.Entry<String, Location> entry : mLastLocationCoarseInterval.entrySet()) {
                String provider = entry.getKey();
                Location location = entry.getValue();
                pw.println("    " + provider + ": " + location);
            }

            mGeofenceManager.dump(pw);
            if (mGeoFencer != null && mGeoFencerEnabled) {
                mGeoFencer.dump(pw, "");
            }

            if (mEnabledProviders.size() > 0) {
                pw.println("  Enabled Providers:");
                for (String i : mEnabledProviders) {
                    pw.println("    " + i);
                }

            }
            if (mDisabledProviders.size() > 0) {
                pw.println("  Disabled Providers:");
                for (String i : mDisabledProviders) {
                    pw.println("    " + i);
                }
            }
            pw.append("  ");
            mBlacklist.dump(pw);
            if (mMockProviders.size() > 0) {
                pw.println("  Mock Providers:");
                for (Map.Entry<String, MockProvider> i : mMockProviders.entrySet()) {
                    i.getValue().dump(pw, "      ");
                }
            }

            pw.append("  fudger: ");
            mLocationFudger.dump(fd, pw,  args);

            if (args.length > 0 && "short".equals(args[0])) {
                return;
            }
            for (LocationProviderInterface provider: mProviders) {
                pw.print(provider.getName() + " Internal State");
                if (provider instanceof LocationProviderProxy) {
                    LocationProviderProxy proxy = (LocationProviderProxy) provider;
                    pw.print(" (" + proxy.getConnectedPackageName() + ")");
                }
                pw.println(":");
                provider.dump(fd, pw, args);
            }
        }
    }
}
