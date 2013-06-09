/**
 * Copyright (C) 2012 Svyatoslav Hresyk
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy.surrogate;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.ILocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.GpsStatus.NmeaListener;
import android.os.Binder;
import android.os.Looper;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.util.Log;

/**
 * Provides privacy handling for {@link android.location.LocationManager}
 * @author Svyatoslav Hresyk
 * {@hide}
 */
public final class PrivacyLocationManager extends LocationManager {

    private static final String TAG = "PrivacyLocationManager";
    
    private static final int CUSTOM_LOCATION_UPDATE_COUNT = 5;
    
    private Context context;
    
    private PrivacySettingsManager pSetMan;
    
    private Object lock = new Object();
    
    /** {@hide} */
    public PrivacyLocationManager(ILocationManager service, Context context) {
        super(context, service);
        this.context = context;
//        pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
        pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));        
    }

    @Override
    public boolean addNmeaListener(NmeaListener listener) {
        // only blocks if access is not allowed
        // custom and random values not implemented due to Decimal Degrees->NMEA conversion complexity
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        
        if (pSet != null && pSet.getLocationGpsSetting() != PrivacySettings.REAL) {
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
            return false;
        } else {
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
        }
//        Log.d(TAG, "addNmeaListener - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: [real value]");
        return super.addNmeaListener(listener);
    }

    @Override
    public Location getLastKnownLocation(String provider) {
        if (provider == null) return super.getLastKnownLocation(provider);
        
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        Location output = null;
        
        if (pSet != null) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                switch (pSet.getLocationGpsSetting()) {
                    case PrivacySettings.REAL:
                        output = super.getLastKnownLocation(provider);
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, 
                                (output != null ? "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude() : null), pSet);
                        break;
                    case PrivacySettings.EMPTY:
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
                        break;
                    case PrivacySettings.CUSTOM:
                        output = new Location(provider);
                        output.setLatitude(Double.parseDouble(pSet.getLocationGpsLat()));
                        output.setLongitude(Double.parseDouble(pSet.getLocationGpsLon()));
                        pSetMan.notification(packageName, uid, PrivacySettings.CUSTOM, PrivacySettings.DATA_LOCATION_GPS, 
                                "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude(), pSet);
                        break;
                    case PrivacySettings.RANDOM:
                        output = new Location(provider);
                        output.setLatitude(Double.parseDouble(pSet.getLocationGpsLat()));
                        output.setLongitude(Double.parseDouble(pSet.getLocationGpsLon()));
                        pSetMan.notification(packageName, uid, PrivacySettings.RANDOM, PrivacySettings.DATA_LOCATION_GPS, 
                                "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude(), pSet);
                        break;
                }
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                switch (pSet.getLocationNetworkSetting()) {
                    case PrivacySettings.REAL:
                        output = super.getLastKnownLocation(provider);
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, 
                                output != null ? "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude() : null, pSet);
                        break;
                    case PrivacySettings.EMPTY:
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_NETWORK, null, pSet);
                        break;
                    case PrivacySettings.CUSTOM:
                        output = new Location(provider);
                        output.setLatitude(Double.parseDouble(pSet.getLocationNetworkLat()));
                        output.setLongitude(Double.parseDouble(pSet.getLocationNetworkLon()));
                        pSetMan.notification(packageName, uid, PrivacySettings.CUSTOM, PrivacySettings.DATA_LOCATION_NETWORK, 
                                "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude(), pSet);
                        break;
                    case PrivacySettings.RANDOM:
                        output = new Location(provider);
                        output.setLatitude(Double.parseDouble(pSet.getLocationNetworkLat()));
                        output.setLongitude(Double.parseDouble(pSet.getLocationNetworkLon()));
                        pSetMan.notification(packageName, uid, PrivacySettings.RANDOM, PrivacySettings.DATA_LOCATION_NETWORK, 
                                "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude(), pSet);
                        break;
                }
            } else if (provider.equals(LocationManager.PASSIVE_PROVIDER) && 
                    pSet.getLocationGpsSetting() == PrivacySettings.REAL && 
                            pSet.getLocationNetworkSetting() == PrivacySettings.REAL) {
                // only output real location if both gps and network are allowed
                output = super.getLastKnownLocation(provider);
                pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, 
                        output != null ? "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude() : null, pSet);
            }
        } else {
            output = super.getLastKnownLocation(provider);
            if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, 
                    output != null ? "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude() : null, pSet);
            } else { // including GPS and passive providers
                pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, 
                        output != null ? "Lat: " + output.getLatitude() + " Lon: " + output.getLongitude() : null, pSet);
            }
        }
        
//        Log.d(TAG, "getLastKnownLocation - " + context.getPackageName() + " (" + Binder.getCallingUid() + 
//                ") output: " + output);
        return output;
    }

    @Override
    public LocationProvider getProvider(String name) {
        if (name == null) return super.getProvider(name);
        
        PrivacySettings pSet = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
        LocationProvider output = null;
        
        if (pSet != null) {
            if (name.equals(LocationManager.GPS_PROVIDER)) {
                switch (pSet.getLocationGpsSetting()) {
                    case PrivacySettings.REAL:
                    case PrivacySettings.CUSTOM:
                    case PrivacySettings.RANDOM:
                        output = super.getProvider(name);
                        break;
                    case PrivacySettings.EMPTY:
                        break;
                }
            } else if (name.equals(LocationManager.NETWORK_PROVIDER)) {
                switch (pSet.getLocationNetworkSetting()) {
                    case PrivacySettings.REAL:
                    case PrivacySettings.CUSTOM:
                    case PrivacySettings.RANDOM:
                        output = super.getProvider(name);
                        break;
                    case PrivacySettings.EMPTY:
                        break;
                }
            } else if (name.equals(LocationManager.PASSIVE_PROVIDER)) { // could get location from any of above
                if (pSet.getLocationGpsSetting() == PrivacySettings.REAL || 
                        pSet.getLocationNetworkSetting() == PrivacySettings.REAL) {
                    output = super.getProvider(name);
                }
            }
        } else {
            output = super.getProvider(name);
        }
            
//        Log.d(TAG, "getProvider - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + 
//                (output != null ? "[real value]" : "[null]"));
        return output;
    }

    @Override
    public boolean isProviderEnabled(String provider) {
        if (provider == null) return super.isProviderEnabled(provider);
        
        PrivacySettings pSet = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
        boolean output = false;
        
        if (pSet != null) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                switch (pSet.getLocationGpsSetting()) {
                    case PrivacySettings.REAL:
                        output = super.isProviderEnabled(provider);
                        break;
                    case PrivacySettings.EMPTY:
                        break;
                    case PrivacySettings.CUSTOM:
                    case PrivacySettings.RANDOM:
                        output = true;
                        break;
                }
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                switch (pSet.getLocationNetworkSetting()) {
                    case PrivacySettings.REAL:
                        output = super.isProviderEnabled(provider);
                        break;
                    case PrivacySettings.EMPTY:
                        break;
                    case PrivacySettings.CUSTOM:
                    case PrivacySettings.RANDOM:
                        output = true;
                        break;
                }
            } else if (provider.equals(LocationManager.PASSIVE_PROVIDER)) { // could get location from any of above
                if (pSet.getLocationGpsSetting() == PrivacySettings.REAL || 
                        pSet.getLocationNetworkSetting() == PrivacySettings.REAL) {
                    output = super.isProviderEnabled(provider);
                } else {
                    output = false;
                }
            }
        } else { // if querying unknown provider
            output = super.isProviderEnabled(provider);
        }
        
//        Log.d(TAG, "isProviderEnabled - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") provider: " 
//                + provider + "output: " + output);
        return output;
    }

    @Override
    public void requestLocationUpdates(long minTime, float minDistance, Criteria criteria, LocationListener listener,
            Looper looper) {
        if (criteria == null || listener == null) {
            super.requestLocationUpdates(minTime, minDistance, criteria, listener, looper);
            return;
        }
        if (requestLocationUpdates(criteria, listener, null)) return;
        super.requestLocationUpdates(minTime, minDistance, criteria, listener, looper);
    }

    @Override
    public void requestLocationUpdates(long minTime, float minDistance, Criteria criteria, PendingIntent intent) {
        if (criteria == null || intent == null) {
            super.requestLocationUpdates(minTime, minDistance, criteria, intent);
            return;
        }
        if (requestLocationUpdates(criteria, null, intent)) return;
        super.requestLocationUpdates(minTime, minDistance, criteria, intent);
    }

    @Override
    public void requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener,
            Looper looper) {
        if (provider == null || listener == null) {
            super.requestLocationUpdates(provider, minTime, minDistance, listener, looper);
            return;
        }
        if (requestLocationUpdates(provider, listener, null)) return;
        super.requestLocationUpdates(provider, minTime, minDistance, listener, looper);
    }

    @Override
    public void requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener) {
        if (provider == null || listener == null) {
            super.requestLocationUpdates(provider, minTime, minDistance, listener);
            return;
        }
        if (requestLocationUpdates(provider, listener, null)) return;
        super.requestLocationUpdates(provider, minTime, minDistance, listener);
    }

    @Override
    public void requestLocationUpdates(String provider, long minTime, float minDistance, PendingIntent intent) {
        if (provider == null || intent == null) {
            super.requestLocationUpdates(provider, minTime, minDistance, intent);
            return;
        }
        if (requestLocationUpdates(provider, null, intent)) return;
        super.requestLocationUpdates(provider, minTime, minDistance, intent);
    }

    @Override
    public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
        if (criteria == null || listener == null) {
            super.requestSingleUpdate(criteria, listener, looper);
            return;
        }
        if (requestLocationUpdates(criteria, listener, null)) return;
        super.requestSingleUpdate(criteria, listener, looper);
    }

    @Override
    public void requestSingleUpdate(Criteria criteria, PendingIntent intent) {
        if (criteria == null || intent == null) {
            super.requestSingleUpdate(criteria, intent);
            return;
        }
        if (requestLocationUpdates(criteria, null, intent)) return;
        super.requestSingleUpdate(criteria, intent);
    }

    @Override
    public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
        if (provider == null || listener == null) {
            super.requestSingleUpdate(provider, listener, looper);
            return;
        }
        if (requestLocationUpdates(provider, listener, null)) return;
        super.requestSingleUpdate(provider, listener, looper);
    }

    @Override
    public void requestSingleUpdate(String provider, PendingIntent intent) {
        if (provider == null || intent == null) {
            super.requestSingleUpdate(provider, intent);
            return;
        }
        if (requestLocationUpdates(provider, null, intent)) return;
        super.requestSingleUpdate(provider, intent);
    }
    
    /**
     * Monitoring purposes only
     */
//    @Override
//    public boolean sendExtraCommand(String provider, String command, Bundle extras) {
//        Log.d(TAG, "sendExtraCommand - " + context.getPackageName() + " (" + Binder.getCallingUid() + ")");
//        return super.sendExtraCommand(provider, command, extras);
//    }

    /**
     * Handles calls to requestLocationUpdates and requestSingleUpdate methods
     * @return true, if action has been taken
     *         false, if the processing needs to be passed to the default method
     */
    private boolean requestLocationUpdates(String provider, LocationListener listener, PendingIntent intent) {
        synchronized (lock) { // custom listener should only return a value after this method has returned

            String packageName = context.getPackageName();
            int uid = Binder.getCallingUid();
            PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
            boolean output = false;
            
            if (pSet != null) {
                if (provider.equals(LocationManager.GPS_PROVIDER)) {
                    switch (pSet.getLocationGpsSetting()) {
                        case PrivacySettings.REAL:
                            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, null, pSet);                            
                            break;
                        case PrivacySettings.EMPTY:
                            if (intent != null) intent.cancel();
                            output = true;
                            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_GPS, null, pSet);                            
                            break;
                        case PrivacySettings.CUSTOM:
                            try {
                                new PrivacyLocationUpdater(provider, listener, intent, 
                                        Double.parseDouble(pSet.getLocationGpsLat()), 
                                        Double.parseDouble(pSet.getLocationGpsLon())).start();
                                output = true;
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "requestLocationUpdates: invalid coordinates");
                                output = true;
                            }
                            pSetMan.notification(packageName, uid, PrivacySettings.CUSTOM, PrivacySettings.DATA_LOCATION_GPS, 
                                    "Lat: " + pSet.getLocationGpsLat() + " Lon: " + pSet.getLocationGpsLon(), pSet);
                            break;
                        case PrivacySettings.RANDOM:
                            try {
                                new PrivacyLocationUpdater(provider, listener, intent, 
                                        Double.parseDouble(pSet.getLocationGpsLat()), 
                                        Double.parseDouble(pSet.getLocationGpsLon())).start();
                                output = true;
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "requestLocationUpdates: invalid coordinates");
                                output = true;
                            }
                            pSetMan.notification(packageName, uid, PrivacySettings.RANDOM, PrivacySettings.DATA_LOCATION_GPS, 
                                    "Lat: " + pSet.getLocationGpsLat() + " Lon: " + pSet.getLocationGpsLon(), pSet);
                            break;
                    }
                } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                    switch (pSet.getLocationNetworkSetting()) {
                        case PrivacySettings.REAL:
                            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, null, pSet);                            
                            break;
                        case PrivacySettings.EMPTY:
                            if (intent != null) intent.cancel();
                            output = true;
                            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_NETWORK, null, pSet);                            
                            break;
                        case PrivacySettings.CUSTOM:
                            try {
                                new PrivacyLocationUpdater(provider, listener, intent, 
                                        Double.parseDouble(pSet.getLocationNetworkLat()), 
                                        Double.parseDouble(pSet.getLocationNetworkLon())).start();
                                output = true;
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "requestLocationUpdates: invalid coordinates");
                                output = true;
                            }
                            pSetMan.notification(packageName, uid, PrivacySettings.CUSTOM, PrivacySettings.DATA_LOCATION_NETWORK, 
                                    "Lat: " + pSet.getLocationNetworkLat() + " Lon: " + pSet.getLocationNetworkLon(), pSet);
                            break;
                        case PrivacySettings.RANDOM:
                            try {
                                new PrivacyLocationUpdater(provider, listener, intent, 
                                        Double.parseDouble(pSet.getLocationNetworkLat()), 
                                        Double.parseDouble(pSet.getLocationNetworkLon())).start();
                                output = true;
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "requestLocationUpdates: invalid coordinates");
                                output = true;
                            }
                            pSetMan.notification(packageName, uid, PrivacySettings.RANDOM, PrivacySettings.DATA_LOCATION_NETWORK, 
                                    "Lat: " + pSet.getLocationNetworkLat() + " Lon: " + pSet.getLocationNetworkLon(), pSet);
                            break;
                    }
                } else if (provider.equals(LocationManager.PASSIVE_PROVIDER)) { // could get location from any of above
                    if (pSet.getLocationGpsSetting() == PrivacySettings.REAL && 
                            pSet.getLocationNetworkSetting() == PrivacySettings.REAL) {
                        output = false;
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
                    } else {
                        output = true;
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
                    }
                }
            } else {
                if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, null, pSet);
                } else { // including GPS and passive providers
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_GPS, null, pSet);
                }
            }
            
//            Log.d(TAG, "requestLocationUpdates - " + context.getPackageName() + " (" + Binder.getCallingUid() + 
//                    ") output: " + (output == true ? "[custom location]" : "[real value]"));
            return output;
        }
    }
    
    /**
     * Helper method for categorizing the different requestLocationUpdates calls by
     * provider accuracy and handing them off to 
     * {@link android.privacy.surrogate.PrivacyLocationManager#requestLocationUpdates(String, LocationListener, PendingIntent)}
     * @param criteria
     * @param listener
     * @param intent
     * @return see {@link android.privacy.surrogate.PrivacyLocationManager#requestLocationUpdates(String, LocationListener, PendingIntent)}
     */
    private boolean requestLocationUpdates(Criteria criteria, LocationListener listener, PendingIntent intent) {
        if (criteria == null) return false;
            // treat providers with high accuracy as GPS providers
        else if (criteria.getAccuracy() == Criteria.ACCURACY_FINE || 
                criteria.getBearingAccuracy() == Criteria.ACCURACY_HIGH || 
                criteria.getHorizontalAccuracy() == Criteria.ACCURACY_HIGH || 
                criteria.getVerticalAccuracy() == Criteria.ACCURACY_HIGH || 
                criteria.getSpeedAccuracy() == Criteria.ACCURACY_HIGH) {
            return requestLocationUpdates(LocationManager.GPS_PROVIDER, listener, intent);
        } else { // treat all others as network providers
            return requestLocationUpdates(LocationManager.NETWORK_PROVIDER, listener, intent);
        }
    }
    
    private class PrivacyLocationUpdater extends Thread {
        
        private String provider;
        
        private LocationListener listener;
        
        private PendingIntent intent;
        
        private double latitude;
        
        private double longitude;

        public PrivacyLocationUpdater(String provider, LocationListener listener, PendingIntent intent,
                double latitude, double longitude) {
            this.provider = provider;
            this.listener = listener;
            this.intent = intent;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public void run() {
            if (provider != null) {
                Location location = new Location(provider);
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                for (int i = 0; i < CUSTOM_LOCATION_UPDATE_COUNT; i++) {
                    if (listener != null) {
                        listener.onLocationChanged(location);
                    } else if (intent != null) {
                        // no custom or random location implemented due to complexity
                        intent.cancel();
                    }
                    try {
                        sleep((int)(Math.random() * 1000));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
    }

}
