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

package android.location;

import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Printer;
import android.util.TimeUtils;

import java.text.DecimalFormat;
import java.util.StringTokenizer;

/**
 * A data class representing a geographic location.
 *
 * <p>A location can consist of a latitude, longitude, timestamp,
 * and other information such as bearing, altitude and velocity.
 *
 * <p>All locations generated by the {@link LocationManager} are
 * guaranteed to have a valid latitude, longitude, and timestamp
 * (both UTC time and elapsed real-time since boot), all other
 * parameters are optional.
 */
public class Location implements Parcelable {
    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "[+-]DDD.DDDDD where D indicates degrees.
     */
    public static final int FORMAT_DEGREES = 0;

    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "[+-]DDD:MM.MMMMM" where D indicates degrees and
     * M indicates minutes of arc (1 minute = 1/60th of a degree).
     */
    public static final int FORMAT_MINUTES = 1;

    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "DDD:MM:SS.SSSSS" where D indicates degrees, M
     * indicates minutes of arc, and S indicates seconds of arc (1
     * minute = 1/60th of a degree, 1 second = 1/3600th of a degree).
     */
    public static final int FORMAT_SECONDS = 2;

    /**
     * Bundle key for a version of the location that has been fed through
     * LocationFudger. Allows location providers to flag locations as being
     * safe for use with ACCESS_COARSE_LOCATION permission.
     *
     * @hide
     */
    public static final String EXTRA_COARSE_LOCATION = "coarseLocation";

    /**
     * Bundle key for a version of the location containing no GPS data.
     * Allows location providers to flag locations as being safe to
     * feed to LocationFudger.
     *
     * @hide
     */
    public static final String EXTRA_NO_GPS_LOCATION = "noGPSLocation";

    private String mProvider;
    private long mTime = 0;
    private long mElapsedRealtimeNanos = 0;
    private double mLatitude = 0.0;
    private double mLongitude = 0.0;
    private boolean mHasAltitude = false;
    private double mAltitude = 0.0f;
    private boolean mHasSpeed = false;
    private float mSpeed = 0.0f;
    private boolean mHasBearing = false;
    private float mBearing = 0.0f;
    private boolean mHasAccuracy = false;
    private float mAccuracy = 0.0f;
    private Bundle mExtras = null;
    private boolean mIsFromMockProvider = false;

    // Cache the inputs and outputs of computeDistanceAndBearing
    // so calls to distanceTo() and bearingTo() can share work
    private double mLat1 = 0.0;
    private double mLon1 = 0.0;
    private double mLat2 = 0.0;
    private double mLon2 = 0.0;
    private float mDistance = 0.0f;
    private float mInitialBearing = 0.0f;
    // Scratchpad
    private final float[] mResults = new float[2];

    /**
     * Construct a new Location with a named provider.
     *
     * <p>By default time, latitude and longitude are 0, and the location
     * has no bearing, altitude, speed, accuracy or extras.
     *
     * @param provider the name of the provider that generated this location
     */
    public Location(String provider) {
        mProvider = provider;
    }

    /**
     * Construct a new Location object that is copied from an existing one.
     */
    public Location(Location l) {
        set(l);
    }

    /**
     * Sets the contents of the location to the values from the given location.
     */
    public void set(Location l) {
        mProvider = l.mProvider;
        mTime = l.mTime;
        mElapsedRealtimeNanos = l.mElapsedRealtimeNanos;
        mLatitude = l.mLatitude;
        mLongitude = l.mLongitude;
        mHasAltitude = l.mHasAltitude;
        mAltitude = l.mAltitude;
        mHasSpeed = l.mHasSpeed;
        mSpeed = l.mSpeed;
        mHasBearing = l.mHasBearing;
        mBearing = l.mBearing;
        mHasAccuracy = l.mHasAccuracy;
        mAccuracy = l.mAccuracy;
        mExtras = (l.mExtras == null) ? null : new Bundle(l.mExtras);
        mIsFromMockProvider = l.mIsFromMockProvider;
    }

    /**
     * Clears the contents of the location.
     */
    public void reset() {
        mProvider = null;
        mTime = 0;
        mElapsedRealtimeNanos = 0;
        mLatitude = 0;
        mLongitude = 0;
        mHasAltitude = false;
        mAltitude = 0;
        mHasSpeed = false;
        mSpeed = 0;
        mHasBearing = false;
        mBearing = 0;
        mHasAccuracy = false;
        mAccuracy = 0;
        mExtras = null;
        mIsFromMockProvider = false;
    }

    /**
     * Converts a coordinate to a String representation. The outputType
     * may be one of FORMAT_DEGREES, FORMAT_MINUTES, or FORMAT_SECONDS.
     * The coordinate must be a valid double between -180.0 and 180.0.
     * This conversion is performed in a method that is dependent on the
     * default locale, and so is not guaranteed to round-trip with
     * {@link #convert(String)}.
     *
     * @throws IllegalArgumentException if coordinate is less than
     * -180.0, greater than 180.0, or is not a number.
     * @throws IllegalArgumentException if outputType is not one of
     * FORMAT_DEGREES, FORMAT_MINUTES, or FORMAT_SECONDS.
     */
    public static String convert(double coordinate, int outputType) {
        if (coordinate < -180.0 || coordinate > 180.0 ||
            Double.isNaN(coordinate)) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        if ((outputType != FORMAT_DEGREES) &&
            (outputType != FORMAT_MINUTES) &&
            (outputType != FORMAT_SECONDS)) {
            throw new IllegalArgumentException("outputType=" + outputType);
        }

        StringBuilder sb = new StringBuilder();

        // Handle negative values
        if (coordinate < 0) {
            sb.append('-');
            coordinate = -coordinate;
        }

        DecimalFormat df = new DecimalFormat("###.#####");
        if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
            int degrees = (int) Math.floor(coordinate);
            sb.append(degrees);
            sb.append(':');
            coordinate -= degrees;
            coordinate *= 60.0;
            if (outputType == FORMAT_SECONDS) {
                int minutes = (int) Math.floor(coordinate);
                sb.append(minutes);
                sb.append(':');
                coordinate -= minutes;
                coordinate *= 60.0;
            }
        }
        sb.append(df.format(coordinate));
        return sb.toString();
    }

    /**
     * Converts a String in one of the formats described by
     * FORMAT_DEGREES, FORMAT_MINUTES, or FORMAT_SECONDS into a
     * double. This conversion is performed in a locale agnostic
     * method, and so is not guaranteed to round-trip with
     * {@link #convert(double, int)}.
     *
     * @throws NullPointerException if coordinate is null
     * @throws IllegalArgumentException if the coordinate is not
     * in one of the valid formats.
     */
    public static double convert(String coordinate) {
        // IllegalArgumentException if bad syntax
        if (coordinate == null) {
            throw new NullPointerException("coordinate");
        }

        boolean negative = false;
        if (coordinate.charAt(0) == '-') {
            coordinate = coordinate.substring(1);
            negative = true;
        }

        StringTokenizer st = new StringTokenizer(coordinate, ":");
        int tokens = st.countTokens();
        if (tokens < 1) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        try {
            String degrees = st.nextToken();
            double val;
            if (tokens == 1) {
                val = Double.parseDouble(degrees);
                return negative ? -val : val;
            }

            String minutes = st.nextToken();
            int deg = Integer.parseInt(degrees);
            double min;
            double sec = 0.0;

            if (st.hasMoreTokens()) {
                min = Integer.parseInt(minutes);
                String seconds = st.nextToken();
                sec = Double.parseDouble(seconds);
            } else {
                min = Double.parseDouble(minutes);
            }

            boolean isNegative180 = negative && (deg == 180) &&
                (min == 0) && (sec == 0);

            // deg must be in [0, 179] except for the case of -180 degrees
            if ((deg < 0.0) || (deg > 179 && !isNegative180)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (min < 0 || min > 59) {
                throw new IllegalArgumentException("coordinate=" +
                        coordinate);
            }
            if (sec < 0 || sec > 59) {
                throw new IllegalArgumentException("coordinate=" +
                        coordinate);
            }

            val = deg*3600.0 + min*60.0 + sec;
            val /= 3600.0;
            return negative ? -val : val;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
    }

    private static void computeDistanceAndBearing(double lat1, double lon1,
        double lat2, double lon2, float[] results) {
        // Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
        // using the "Inverse Formula" (section 4)

        int MAXITERS = 20;
        // Convert lat/long to radians
        lat1 *= Math.PI / 180.0;
        lat2 *= Math.PI / 180.0;
        lon1 *= Math.PI / 180.0;
        lon2 *= Math.PI / 180.0;

        double a = 6378137.0; // WGS84 major axis
        double b = 6356752.3142; // WGS84 semi-major axis
        double f = (a - b) / a;
        double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

        double L = lon2 - lon1;
        double A = 0.0;
        double U1 = Math.atan((1.0 - f) * Math.tan(lat1));
        double U2 = Math.atan((1.0 - f) * Math.tan(lat2));

        double cosU1 = Math.cos(U1);
        double cosU2 = Math.cos(U2);
        double sinU1 = Math.sin(U1);
        double sinU2 = Math.sin(U2);
        double cosU1cosU2 = cosU1 * cosU2;
        double sinU1sinU2 = sinU1 * sinU2;

        double sigma = 0.0;
        double deltaSigma = 0.0;
        double cosSqAlpha = 0.0;
        double cos2SM = 0.0;
        double cosSigma = 0.0;
        double sinSigma = 0.0;
        double cosLambda = 0.0;
        double sinLambda = 0.0;

        double lambda = L; // initial guess
        for (int iter = 0; iter < MAXITERS; iter++) {
            double lambdaOrig = lambda;
            cosLambda = Math.cos(lambda);
            sinLambda = Math.sin(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            double sinSqSigma = t1 * t1 + t2 * t2; // (14)
            sinSigma = Math.sqrt(sinSqSigma);
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda; // (15)
            sigma = Math.atan2(sinSigma, cosSigma); // (16)
            double sinAlpha = (sinSigma == 0) ? 0.0 :
                cosU1cosU2 * sinLambda / sinSigma; // (17)
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SM = (cosSqAlpha == 0) ? 0.0 :
                cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha; // (18)

            double uSquared = cosSqAlpha * aSqMinusBSqOverBSq; // defn
            A = 1 + (uSquared / 16384.0) * // (3)
                (4096.0 + uSquared *
                 (-768 + uSquared * (320.0 - 175.0 * uSquared)));
            double B = (uSquared / 1024.0) * // (4)
                (256.0 + uSquared *
                 (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
            double C = (f / 16.0) *
                cosSqAlpha *
                (4.0 + f * (4.0 - 3.0 * cosSqAlpha)); // (10)
            double cos2SMSq = cos2SM * cos2SM;
            deltaSigma = B * sinSigma * // (6)
                (cos2SM + (B / 4.0) *
                 (cosSigma * (-1.0 + 2.0 * cos2SMSq) -
                  (B / 6.0) * cos2SM *
                  (-3.0 + 4.0 * sinSigma * sinSigma) *
                  (-3.0 + 4.0 * cos2SMSq)));

            lambda = L +
                (1.0 - C) * f * sinAlpha *
                (sigma + C * sinSigma *
                 (cos2SM + C * cosSigma *
                  (-1.0 + 2.0 * cos2SM * cos2SM))); // (11)

            double delta = (lambda - lambdaOrig) / lambda;
            if (Math.abs(delta) < 1.0e-12) {
                break;
            }
        }

        float distance = (float) (b * A * (sigma - deltaSigma));
        results[0] = distance;
        if (results.length > 1) {
            float initialBearing = (float) Math.atan2(cosU2 * sinLambda,
                cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
            initialBearing *= 180.0 / Math.PI;
            results[1] = initialBearing;
            if (results.length > 2) {
                float finalBearing = (float) Math.atan2(cosU1 * sinLambda,
                    -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
                finalBearing *= 180.0 / Math.PI;
                results[2] = finalBearing;
            }
        }
    }

    /**
     * Computes the approximate distance in meters between two
     * locations, and optionally the initial and final bearings of the
     * shortest path between them.  Distance and bearing are defined using the
     * WGS84 ellipsoid.
     *
     * <p> The computed distance is stored in results[0].  If results has length
     * 2 or greater, the initial bearing is stored in results[1]. If results has
     * length 3 or greater, the final bearing is stored in results[2].
     *
     * @param startLatitude the starting latitude
     * @param startLongitude the starting longitude
     * @param endLatitude the ending latitude
     * @param endLongitude the ending longitude
     * @param results an array of floats to hold the results
     *
     * @throws IllegalArgumentException if results is null or has length < 1
     */
    public static void distanceBetween(double startLatitude, double startLongitude,
        double endLatitude, double endLongitude, float[] results) {
        if (results == null || results.length < 1) {
            throw new IllegalArgumentException("results is null or has length < 1");
        }
        computeDistanceAndBearing(startLatitude, startLongitude,
            endLatitude, endLongitude, results);
    }

    /**
     * Returns the approximate distance in meters between this
     * location and the given location.  Distance is defined using
     * the WGS84 ellipsoid.
     *
     * @param dest the destination location
     * @return the approximate distance in meters
     */
    public float distanceTo(Location dest) {
        // See if we already have the result
        synchronized (mResults) {
            if (mLatitude != mLat1 || mLongitude != mLon1 ||
                dest.mLatitude != mLat2 || dest.mLongitude != mLon2) {
                computeDistanceAndBearing(mLatitude, mLongitude,
                    dest.mLatitude, dest.mLongitude, mResults);
                mLat1 = mLatitude;
                mLon1 = mLongitude;
                mLat2 = dest.mLatitude;
                mLon2 = dest.mLongitude;
                mDistance = mResults[0];
                mInitialBearing = mResults[1];
            }
            return mDistance;
        }
    }

    /**
     * Returns the approximate initial bearing in degrees East of true
     * North when traveling along the shortest path between this
     * location and the given location.  The shortest path is defined
     * using the WGS84 ellipsoid.  Locations that are (nearly)
     * antipodal may produce meaningless results.
     *
     * @param dest the destination location
     * @return the initial bearing in degrees
     */
    public float bearingTo(Location dest) {
        synchronized (mResults) {
            // See if we already have the result
            if (mLatitude != mLat1 || mLongitude != mLon1 ||
                            dest.mLatitude != mLat2 || dest.mLongitude != mLon2) {
                computeDistanceAndBearing(mLatitude, mLongitude,
                    dest.mLatitude, dest.mLongitude, mResults);
                mLat1 = mLatitude;
                mLon1 = mLongitude;
                mLat2 = dest.mLatitude;
                mLon2 = dest.mLongitude;
                mDistance = mResults[0];
                mInitialBearing = mResults[1];
            }
            return mInitialBearing;
        }
    }

    /**
     * Returns the name of the provider that generated this fix.
     *
     * @return the provider, or null if it has not been set
     */
    public String getProvider() {
        return mProvider;
    }

    /**
     * Sets the name of the provider that generated this fix.
     */
    public void setProvider(String provider) {
        mProvider = provider;
    }

    /**
     * Return the UTC time of this fix, in milliseconds since January 1, 1970.
     *
     * <p>Note that the UTC time on a device is not monotonic: it
     * can jump forwards or backwards unpredictably. So always use
     * {@link #getElapsedRealtimeNanos} when calculating time deltas.
     *
     * <p>On the other hand, {@link #getTime} is useful for presenting
     * a human readable time to the user, or for carefully comparing
     * location fixes across reboot or across devices.
     *
     * <p>All locations generated by the {@link LocationManager}
     * are guaranteed to have a valid UTC time, however remember that
     * the system time may have changed since the location was generated.
     *
     * @return time of fix, in milliseconds since January 1, 1970.
     */
    public long getTime() {
        return mTime;
    }

    /**
     * Set the UTC time of this fix, in milliseconds since January 1,
     * 1970.
     *
     * @param time UTC time of this fix, in milliseconds since January 1, 1970
     */
    public void setTime(long time) {
        mTime = time;
    }

    /**
     * Return the time of this fix, in elapsed real-time since system boot.
     *
     * <p>This value can be reliably compared to
     * {@link android.os.SystemClock#elapsedRealtimeNanos},
     * to calculate the age of a fix and to compare Location fixes. This
     * is reliable because elapsed real-time is guaranteed monotonic for
     * each system boot and continues to increment even when the system
     * is in deep sleep (unlike {@link #getTime}.
     *
     * <p>All locations generated by the {@link LocationManager}
     * are guaranteed to have a valid elapsed real-time.
     *
     * @return elapsed real-time of fix, in nanoseconds since system boot.
     */
    public long getElapsedRealtimeNanos() {
        return mElapsedRealtimeNanos;
    }

    /**
     * Set the time of this fix, in elapsed real-time since system boot.
     *
     * @param time elapsed real-time of fix, in nanoseconds since system boot.
     */
    public void setElapsedRealtimeNanos(long time) {
        mElapsedRealtimeNanos = time;
    }

    /**
     * Get the latitude, in degrees.
     *
     * <p>All locations generated by the {@link LocationManager}
     * will have a valid latitude.
     */
    public double getLatitude() {
        return mLatitude;
    }

    /**
     * Set the latitude, in degrees.
     */
    public void setLatitude(double latitude) {
        mLatitude = latitude;
    }

    /**
     * Get the longitude, in degrees.
     *
     * <p>All locations generated by the {@link LocationManager}
     * will have a valid longitude.
     */
    public double getLongitude() {
        return mLongitude;
    }

    /**
     * Set the longitude, in degrees.
     */
    public void setLongitude(double longitude) {
        mLongitude = longitude;
    }

    /**
     * True if this location has an altitude.
     */
    public boolean hasAltitude() {
        return mHasAltitude;
    }

    /**
     * Get the altitude if available, in meters above the WGS 84 reference
     * ellipsoid.
     *
     * <p>If this location does not have an altitude then 0.0 is returned.
     */
    public double getAltitude() {
        return mAltitude;
    }

    /**
     * Set the altitude, in meters above the WGS 84 reference ellipsoid.
     *
     * <p>Following this call {@link #hasAltitude} will return true.
     */
    public void setAltitude(double altitude) {
        mAltitude = altitude;
        mHasAltitude = true;
    }

    /**
     * Remove the altitude from this location.
     *
     * <p>Following this call {@link #hasAltitude} will return false,
     * and {@link #getAltitude} will return 0.0.
     */
    public void removeAltitude() {
        mAltitude = 0.0f;
        mHasAltitude = false;
    }

    /**
     * True if this location has a speed.
     */
    public boolean hasSpeed() {
        return mHasSpeed;
    }

    /**
     * Get the speed if it is available, in meters/second over ground.
     *
     * <p>If this location does not have a speed then 0.0 is returned.
     */
    public float getSpeed() {
        return mSpeed;
    }

    /**
     * Set the speed, in meters/second over ground.
     *
     * <p>Following this call {@link #hasSpeed} will return true.
     */
    public void setSpeed(float speed) {
        mSpeed = speed;
        mHasSpeed = true;
    }

    /**
     * Remove the speed from this location.
     *
     * <p>Following this call {@link #hasSpeed} will return false,
     * and {@link #getSpeed} will return 0.0.
     */
    public void removeSpeed() {
        mSpeed = 0.0f;
        mHasSpeed = false;
    }

    /**
     * True if this location has a bearing.
     */
    public boolean hasBearing() {
        return mHasBearing;
    }

    /**
     * Get the bearing, in degrees.
     *
     * <p>Bearing is the horizontal direction of travel of this device,
     * and is not related to the device orientation. It is guaranteed to
     * be in the range (0.0, 360.0] if the device has a bearing.
     *
     * <p>If this location does not have a bearing then 0.0 is returned.
     */
    public float getBearing() {
        return mBearing;
    }

    /**
     * Set the bearing, in degrees.
     *
     * <p>Bearing is the horizontal direction of travel of this device,
     * and is not related to the device orientation.
     *
     * <p>The input will be wrapped into the range (0.0, 360.0].
     */
    public void setBearing(float bearing) {
        while (bearing < 0.0f) {
            bearing += 360.0f;
        }
        while (bearing >= 360.0f) {
            bearing -= 360.0f;
        }
        mBearing = bearing;
        mHasBearing = true;
    }

    /**
     * Remove the bearing from this location.
     *
     * <p>Following this call {@link #hasBearing} will return false,
     * and {@link #getBearing} will return 0.0.
     */
    public void removeBearing() {
        mBearing = 0.0f;
        mHasBearing = false;
    }

    /**
     * True if this location has an accuracy.
     *
     * <p>All locations generated by the {@link LocationManager} have an
     * accuracy.
     */
    public boolean hasAccuracy() {
        return mHasAccuracy;
    }

    /**
     * Get the estimated accuracy of this location, in meters.
     *
     * <p>We define accuracy as the radius of 68% confidence. In other
     * words, if you draw a circle centered at this location's
     * latitude and longitude, and with a radius equal to the accuracy,
     * then there is a 68% probability that the true location is inside
     * the circle.
     *
     * <p>In statistical terms, it is assumed that location errors
     * are random with a normal distribution, so the 68% confidence circle
     * represents one standard deviation. Note that in practice, location
     * errors do not always follow such a simple distribution.
     *
     * <p>This accuracy estimation is only concerned with horizontal
     * accuracy, and does not indicate the accuracy of bearing,
     * velocity or altitude if those are included in this Location.
     *
     * <p>If this location does not have an accuracy, then 0.0 is returned.
     * All locations generated by the {@link LocationManager} include
     * an accuracy.
     */
    public float getAccuracy() {
        return mAccuracy;
    }

    /**
     * Set the estimated accuracy of this location, meters.
     *
     * <p>See {@link #getAccuracy} for the definition of accuracy.
     *
     * <p>Following this call {@link #hasAccuracy} will return true.
     */
    public void setAccuracy(float accuracy) {
        mAccuracy = accuracy;
        mHasAccuracy = true;
    }

    /**
     * Remove the accuracy from this location.
     *
     * <p>Following this call {@link #hasAccuracy} will return false, and
     * {@link #getAccuracy} will return 0.0.
     */
    public void removeAccuracy() {
        mAccuracy = 0.0f;
        mHasAccuracy = false;
    }

    /**
     * Return true if this Location object is complete.
     *
     * <p>A location object is currently considered complete if it has
     * a valid provider, accuracy, wall-clock time and elapsed real-time.
     *
     * <p>All locations supplied by the {@link LocationManager} to
     * applications must be complete.
     *
     * @see #makeComplete
     * @hide
     */
    @SystemApi
    public boolean isComplete() {
        if (mProvider == null) return false;
        if (!mHasAccuracy) return false;
        if (mTime == 0) return false;
        if (mElapsedRealtimeNanos == 0) return false;
        return true;
    }

    /**
     * Helper to fill incomplete fields.
     *
     * <p>Used to assist in backwards compatibility with
     * Location objects received from applications.
     *
     * @see #isComplete
     * @hide
     */
    @SystemApi
    public void makeComplete() {
        if (mProvider == null) mProvider = "?";
        if (!mHasAccuracy) {
            mHasAccuracy = true;
            mAccuracy = 100.0f;
        }
        if (mTime == 0) mTime = System.currentTimeMillis();
        if (mElapsedRealtimeNanos == 0) mElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Returns additional provider-specific information about the
     * location fix as a Bundle.  The keys and values are determined
     * by the provider.  If no additional information is available,
     * null is returned.
     *
     * <p> A number of common key/value pairs are listed
     * below. Providers that use any of the keys on this list must
     * provide the corresponding value as described below.
     *
     * <ul>
     * <li> satellites - the number of satellites used to derive the fix
     * </ul>
     */
    public Bundle getExtras() {
        return mExtras;
    }

    /**
     * Sets the extra information associated with this fix to the
     * given Bundle.
     */
    public void setExtras(Bundle extras) {
        mExtras = (extras == null) ? null : new Bundle(extras);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Location[");
        s.append(mProvider);
        s.append(String.format(" %.6f,%.6f", mLatitude, mLongitude));
        if (mHasAccuracy) s.append(String.format(" acc=%.0f", mAccuracy));
        else s.append(" acc=???");
        if (mTime == 0) {
            s.append(" t=?!?");
        }
        if (mElapsedRealtimeNanos == 0) {
            s.append(" et=?!?");
        } else {
            s.append(" et=");
            TimeUtils.formatDuration(mElapsedRealtimeNanos / 1000000L, s);
        }
        if (mHasAltitude) s.append(" alt=").append(mAltitude);
        if (mHasSpeed) s.append(" vel=").append(mSpeed);
        if (mHasBearing) s.append(" bear=").append(mBearing);
        if (mIsFromMockProvider) s.append(" mock");

        if (mExtras != null) {
            s.append(" {").append(mExtras).append('}');
        }
        s.append(']');
        return s.toString();
    }

    public void dump(Printer pw, String prefix) {
        pw.println(prefix + toString());
    }

    public static final Parcelable.Creator<Location> CREATOR =
        new Parcelable.Creator<Location>() {
        @Override
        public Location createFromParcel(Parcel in) {
            String provider = in.readString();
            Location l = new Location(provider);
            l.mTime = in.readLong();
            l.mElapsedRealtimeNanos = in.readLong();
            l.mLatitude = in.readDouble();
            l.mLongitude = in.readDouble();
            l.mHasAltitude = in.readInt() != 0;
            l.mAltitude = in.readDouble();
            l.mHasSpeed = in.readInt() != 0;
            l.mSpeed = in.readFloat();
            l.mHasBearing = in.readInt() != 0;
            l.mBearing = in.readFloat();
            l.mHasAccuracy = in.readInt() != 0;
            l.mAccuracy = in.readFloat();
            l.mExtras = in.readBundle();
            l.mIsFromMockProvider = in.readInt() != 0;
            return l;
        }

        @Override
        public Location[] newArray(int size) {
            return new Location[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mProvider);
        parcel.writeLong(mTime);
        parcel.writeLong(mElapsedRealtimeNanos);
        parcel.writeDouble(mLatitude);
        parcel.writeDouble(mLongitude);
        parcel.writeInt(mHasAltitude ? 1 : 0);
        parcel.writeDouble(mAltitude);
        parcel.writeInt(mHasSpeed ? 1 : 0);
        parcel.writeFloat(mSpeed);
        parcel.writeInt(mHasBearing ? 1 : 0);
        parcel.writeFloat(mBearing);
        parcel.writeInt(mHasAccuracy ? 1 : 0);
        parcel.writeFloat(mAccuracy);
        parcel.writeBundle(mExtras);
        parcel.writeInt(mIsFromMockProvider? 1 : 0);
    }

    /**
     * Returns one of the optional extra {@link Location}s that can be attached
     * to this Location.
     *
     * @param key the key associated with the desired extra Location
     * @return the extra Location, or null if unavailable
     * @hide
     */
    public Location getExtraLocation(String key) {
        if (mExtras != null) {
            Parcelable value = mExtras.getParcelable(key);
            if (value instanceof Location) {
                return (Location) value;
            }
        }
        return null;
    }

    /**
     * Attaches an extra {@link Location} to this Location.
     *
     * @param key the key associated with the Location extra
     * @param location the Location to attach
     * @hide
     */
    public void setExtraLocation(String key, Location value) {
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putParcelable(key, value);
    }

    /**
     * Returns true if the Location came from a mock provider.
     *
     * @return true if this Location came from a mock provider, false otherwise
     */
    public boolean isFromMockProvider() {
        return false;
    }

    /**
     * Flag this Location as having come from a mock provider or not.
     *
     * @param isFromMockProvider true if this Location came from a mock provider, false otherwise
     * @hide
     */
    @SystemApi
    public void setIsFromMockProvider(boolean isFromMockProvider) {
        mIsFromMockProvider = isFromMockProvider;
    }
}
