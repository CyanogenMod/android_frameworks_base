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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

public class NMEAParser {
    private static final String TAG = "NMEAParser";
    private static final String delim = ",";
    // NMEA sentence pattern
    private final Pattern sentencePattern = Pattern.compile("\\$([^*$]{5,})(\\*\\w{2})?");
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HHmmss.S");
    private final TimeZone GPSTimezone = TimeZone.getTimeZone("UTC");
    private GregorianCalendar GPSCalendar = new GregorianCalendar(GPSTimezone);

    private HashMap<String,ParseInterface> parseMap = new HashMap<String,ParseInterface>();
    private String provider;

    private static final String BUNDLE_SATS = "satellites";
    // for GPS SV statistics
    private static final int MAX_SVS = 32;
    public static final int EPHEMERIS_MASK = 0;
    public static final int ALMANAC_MASK = 1;
    public static final int USED_FOR_FIX_MASK = 2;

    // preallocated arrays, to avoid memory allocation in reportStatus()
    private int mSvs[] = new int[MAX_SVS];
    private float mSnrs[] = new float[MAX_SVS];
    private float mSvElevations[] = new float[MAX_SVS];
    private float mSvAzimuths[] = new float[MAX_SVS];
    private int mSvMasks[] = new int[3];
    private int mSvCount;

    private float PDOP = 0f;
    private float HDOP = 0f;
    private float VDOP = 0f;

    private boolean isValid = false;
    private long mFixDateTimeStamp = 0;
    private double mFixLongitude = 0.0;
    private double mFixLatitude = 0.0;
    private float mFixAltitude = 0f;
    private float mFixSpeed = 0f;
    private float mFixBearing = 0f;
    private float mFixAccuracy = 0f;
    private int mFixSatsTracked=0;
    private int mFixQuality = 0;

    //horizontal estimated position error
    private float HEPE_FACTOR = 4f;

    // last fix timestamp. Is used to approximate and adjust gps mouse refresh rate.
    private long mFixTimestampDelta=500;

    private boolean mSatsReady = true;
    private Location loc = new Location(provider);

    /**
     * @param prov  Location provider name
     */
    public NMEAParser(String prov) {
        // init parser map with all known parsers
        parseMap.put("GPRMC", new GPRMCParser());
        parseMap.put("GPGGA", new GPGGAParser());
        parseMap.put("GPGSA", new GPGSAParser());
        parseMap.put("GPGSV", new GPGSVParser());

        provider = prov;
        timeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

    }

    private void updateTimeStamp(long in) {
        if (mFixDateTimeStamp != 0 && in != mFixDateTimeStamp) {
            mFixTimestampDelta = in - mFixDateTimeStamp;
            if (mFixTimestampDelta < 100) mFixTimestampDelta = 100;
            if (mFixTimestampDelta > 1000) mFixTimestampDelta = 1000;
        }
        mFixDateTimeStamp = in;
    }

    public long getApproximatedRefreshRate() {
        return mFixTimestampDelta;
    }
    /**
     * @return if nmea sentence are valid then true
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * resets fix variables
     */
    public void reset() {
        mFixLongitude = 0.0;
        mFixLatitude = 0.0;
        mFixAltitude = 0f;
        mFixSpeed = 0f;
        mFixAccuracy = 0f;
        mFixQuality = 0;
        mFixSatsTracked = 0;
    }

    private void resetSats() {
        mSvCount = 0;
        java.util.Arrays.fill(mSvs, 0);
        java.util.Arrays.fill(mSnrs, 0f);
        java.util.Arrays.fill(mSvElevations, 0f);
        java.util.Arrays.fill(mSvAzimuths, 0f);
    }


    /**
     * @return a Location object if valid null otherwise
     */
    public Location getLocation() {
        loc.reset();
        if (mFixDateTimeStamp != 0) loc.setTime(mFixDateTimeStamp);
        loc.setLatitude(mFixLatitude);
        loc.setLongitude(mFixLongitude);
        Bundle extras = new Bundle();
        extras.putInt(BUNDLE_SATS, mFixSatsTracked);
        loc.setExtras(extras);
        loc.setAccuracy(mFixAccuracy);
        loc.setAltitude(mFixAltitude);
        loc.setSpeed(mFixSpeed);
        loc.setBearing(mFixBearing);
        return loc;
    }

    /**
     * @param time UTC time
     * @return nr seconds since 1970
     */
    private long parseTimeToDate(String time) {
        try {
            // parse time , We only get timestamp from sentences
            // use UTC calendar to set the date.
            Date btTime = timeFormatter.parse(time);
            Calendar localCalendar = Calendar.getInstance(GPSTimezone);
            GPSCalendar.setTimeInMillis(btTime.getTime());
            GPSCalendar.set(localCalendar.get(Calendar.YEAR), localCalendar.get(Calendar.MONTH),
                    localCalendar.get(Calendar.DAY_OF_MONTH));
            return GPSCalendar.getTimeInMillis();
        } catch (ParseException e) {
            Log.e(TAG, "Could not parse: " + time);
            return 0;
        }
    }

    private int parseStringToInt(String str) {
        if (TextUtils.isEmpty(str))
            return 0;
        int res = 0;
        try {
            res = Integer.parseInt(str);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return res;
    }

    private float parseStringToFloat(String str) {
        if (TextUtils.isEmpty(str))
            return 0.0f;

        float res = 0.0f;
        try {
            res = Float.parseFloat(str);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return res;
    }

    /**
     * @param in    Longitude/Latitude
     * @param orientation    N,W,S,E
     * @return    The double representation of a Longitude/Latitude
     */
    private double parseCoordinate(String in, String orientation) {
        // dec = deg + mins.sec/60
        double c = Double.parseDouble(in);
        int deg = (int) (c/100);
        double res = deg + (c - deg*100.0)*0.016666666666667;
        if ("S".equalsIgnoreCase(orientation) || "W".equalsIgnoreCase(orientation)) return -res;
        return res;
    }

    private float parseSpeedInKnots(String str) {
        float res = 0.0f;
        res = Float.parseFloat(str) * 0.514444444f;
        return res;
    }

    private float parseSpeedInKMH(String str) {
        float res = 0.0f;
        res = Float.parseFloat(str) * 0.277777778f;
        return res;
    }

    /**
     * Interface that all sentence parsers have to implement.
     *
     *Every sentence is implemented as a seperate class. The Nmea
     *parser will select the correct parser based on the sentence identifier.
     *It will get or instantiate a parser to do the job.
     */
    private interface ParseInterface {
        public void parse(String sentence);
    }

    public class GPRMCParser implements ParseInterface {
        /*
         * $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A

Where:
     RMC          Recommended Minimum sentence C
     123519       Fix taken at 12:35:19 UTC
     A            Status A=active or V=Void.
     4807.038,N   Latitude 48 deg 07.038' N
     01131.000,E  Longitude 11 deg 31.000' E
     022.4        Speed over the ground in knots
     084.4        Track angle in degrees True
     230394       Date - 23rd of March 1994
     003.1,W      Magnetic Variation
         *6A          The checksum data, always begins with *

         */
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);
            if (tmp.length > 3) {
                updateTimeStamp(parseTimeToDate(tmp[1]));
                if (!"A".equals(tmp[2])) {
                    return;
                }
                mFixLatitude = parseCoordinate(tmp[3], tmp[4]);
                mFixLongitude = parseCoordinate(tmp[5], tmp[6]);
                mFixSpeed = parseSpeedInKnots(tmp[7]);
                mFixBearing = parseStringToFloat(tmp[8]);
            }
        }
    }

    public class GPGGAParser implements ParseInterface {
        /*
 $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
Where:
     GGA          Global Positioning System Fix Data
     123519       Fix taken at 12:35:19 UTC
     4807.038,N   Latitude 48 deg 07.038' N
     01131.000,E  Longitude 11 deg 31.000' E
     1            Fix quality: 0 = invalid
                               1 = GPS fix (SPS)
                               2 = DGPS fix
                               3 = PPS fix
                   4 = Real Time Kinematic
                   5 = Float RTK
                               6 = estimated (dead reckoning) (2.3 feature)
                   7 = Manual input mode
                   8 = Simulation mode
     08           Number of satellites being tracked
     0.9          Horizontal dilution of position
     545.4,M      Altitude, Meters, above mean sea level
     46.9,M       Height of geoid (mean sea level) above WGS84
                      ellipsoid
     (empty field) time in seconds since last DGPS update
     (empty field) DGPS station ID number
         *47          the checksum data, always begins with *         */
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);
            if (tmp.length > 7) {
                // always parse timestamp
                updateTimeStamp(parseTimeToDate(tmp[1]));
                mFixQuality =  Integer.parseInt(tmp[6]);
                if (mFixQuality == 0) {
                    // return invalid location
                    isValid = false;
                    return;
                }
                mFixLatitude = parseCoordinate(tmp[2], tmp[3]);
                mFixLongitude = parseCoordinate(tmp[4], tmp[5]);
                mFixSatsTracked = parseStringToInt(tmp[7]);
                mFixAccuracy = parseStringToFloat(tmp[8]) * HEPE_FACTOR;
                mFixAltitude = parseStringToFloat(tmp[9]);
                isValid = true;
            }
        }
    }

    public class GPGSAParser implements ParseInterface {
        /*
  $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39

Where:
     GSA      Satellite status
     A        Auto selection of 2D or 3D fix (M = manual)
     3        3D fix - values include: 1 = no fix
                                       2 = 2D fix
                                       3 = 3D fix
     04,05... PRNs of satellites used for fix (space for 12)
     2.5      PDOP (dilution of precision)
     1.3      Horizontal dilution of precision (HDOP)
     2.1      Vertical dilution of precision (VDOP)
         *39      the checksum data, always begins with *
         *         */
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);
            if (tmp.length >= 16) {
                if ("1".equals(tmp[2])) {
                    // return invalid location or invalid sentence
                    return;
                }
                for (int i=3; i < 15; i++) {
                    // tag sats used for fix
                    int sat = parseStringToInt(tmp[i]);
                    if (sat > 0) mSvMasks[USED_FOR_FIX_MASK] |= (1 << (sat - 1));
                }
                if (tmp.length > 15)
                    PDOP = parseStringToFloat(tmp[15]);
                if (tmp.length > 16)
                    HDOP = parseStringToFloat(tmp[16]);
                if (tmp.length > 17)
                    VDOP = parseStringToFloat(tmp[17]);
            }
        }
    }

    /**
     * Parse sats information. Use same structure as internal GPS provider
     *
     */
    public class GPGSVParser implements ParseInterface {
        /*
  $GPGSV,2,1,08,01,40,083,46,02,17,308,41,12,07,344,39,14,22,228,45*75

Where:
      GSV          Satellites in view
      2            Number of sentences for full data
      1            sentence 1 of 2
      08           Number of satellites in view

      01           Satellite PRN number
      40           Elevation, degrees
      083          Azimuth, degrees
      46           SNR - higher is better
           for up to 4 satellites per sentence
         *75          the checksum data, always begins with *
         */
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);
            if (tmp.length > 4) {
                mSvCount = parseStringToInt(tmp[3]);
                if (mSvCount == 0) {
                    return;
                }
                int totalSentences = parseStringToInt(tmp[1]);
                int currSentence = parseStringToInt(tmp[2]);

                if (mSatsReady) {
                    resetSats();
                    mSatsReady = false;
                } else if ((currSentence == totalSentences)  && !mSatsReady) {
                    // tag data as dirty when we have parsed the last part
                    mSatsReady = true;
                }
                int idx = 0;
                while ((currSentence <= totalSentences) && (idx < 4)) {
                    int offset = idx<<2;
                    int base_offset = (currSentence-1)<<2;
                    if (offset+4 < tmp.length)
                        mSvs[base_offset + idx] = parseStringToInt(tmp[4 + offset]);
                    if (offset+5 < tmp.length)
                        mSvElevations[base_offset + idx] = parseStringToInt(tmp[5 + offset]);
                    if (offset+6 < tmp.length)
                        mSvAzimuths[base_offset + idx] = parseStringToInt(tmp[6 + offset]);
                    if (offset+7 < tmp.length)
                        mSnrs[base_offset + idx] = parseStringToInt(tmp[7 + offset]);
                    idx++;
                }
            }
        }
    }

    /**
     * Using non static dynamic innerclass instantiation.
     * @param sid    sentence identifier
     * @return    parser associated with the sid
     */
    private ParseInterface getParser(String sid) {
        if (parseMap.containsKey(sid)) {
            return parseMap.get(sid);
        } else {
            Log.d(TAG, "Could not instantiate " + sid + "parser");
        }
        return null;
    }

    /**
     * @param in    nmea sentence
     * @return    String representing checksum of the input
     */
    private String computeChecksum(String in) {
        byte result = 0;
        char[] chars = in.toCharArray();
        for (int i=0; i < chars.length; i++)
            result ^= (byte) chars[i];
        return String.format("%02X", result);
    }

    public boolean parseNMEALine(String sentence) {
        Matcher m = sentencePattern.matcher(sentence);
        if (m.matches()) {
            String nmeaSentence = m.group(1);
            String command = nmeaSentence.substring(0, 5);
            String checksum = m.group(2);
            if (checksum != null) {
                // checksums are optional
                // strip off *, checksum always have length 3 here. else the regex will not match
                checksum = checksum.substring(1, 3);
                if (!computeChecksum(nmeaSentence).equals(checksum)) {
                    Log.w(TAG, "skipping sentence: " + sentence + " due to checksum error "
                            + checksum + " - " + computeChecksum(nmeaSentence));
                    return false;
                }
            }
            ParseInterface parser = getParser(command);
            if (parser != null) {
                try {
                    parser.parse(nmeaSentence);
                } catch (Exception e) {
                    // catch exception thrown by parsers
                    // mostly bad input causing out of bounds
                    Log.e(TAG,nmeaSentence, e);
                    return false;
                }
            }
        }
        return true;
    }

    public int getmSvCount() {
        return mSvCount;
    }

    public float getPDOP() {
        return PDOP;
    }

    public float getHDOP() {
        return HDOP;
    }

    public float getVDOP() {
        return VDOP;
    }

    public long getmFixDate() {
        return mFixDateTimeStamp;
    }

    public double getmFixLongitude() {
        return mFixLongitude;
    }

    public double getmFixLatitude() {
        return mFixLatitude;
    }

    public float getmFixAltitude() {
        return mFixAltitude;
    }

    public float getmFixSpeed() {
        return mFixSpeed;
    }

    public float getmFixAccuracy() {
        return mFixAccuracy;
    }

    public int getmFixQuality() {
        return mFixQuality;
    }
    public int[] getmSvs() {
        return mSvs;
    }

    public float[] getmSnrs() {
        return mSnrs;
    }

    public float[] getmSvElevations() {
        return mSvElevations;
    }

    public float[] getmSvAzimuths() {
        return mSvAzimuths;
    }

    public int[] getmSvMasks() {
        return mSvMasks;
    }

    public int getmFixSatsTracked() {
        return mFixSatsTracked;
    }

    public boolean isSatdataReady() {
        return mSatsReady;
    }
}
