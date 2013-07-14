/*
 * Copyright (C) 2011 Cuong Bui
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NmeaParser {
    private static final String TAG = "NMEAParser";
    private static final String delim = ",";

    // NMEA sentence pattern
    private final String patternString = "\\$([^*$]{5,})(\\*\\w{2})?";
    private final Pattern sentencePattern = Pattern.compile(patternString);

    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HHmmss.S");
    private final TimeZone GPSTimezone = TimeZone.getTimeZone("UTC");

    private GregorianCalendar GPSCalendar = new GregorianCalendar(GPSTimezone);
    private HashMap<String,ParseInterface> parseMap = new HashMap<String,ParseInterface>();
    private String provider;

    private static final String BUNDLE_SATS = "satellites";

    // For GPS SV statistics
    private static final int MAX_SVS = 32;
    public static final int EPHEMERIS_MASK = 0;
    public static final int ALMANAC_MASK = 1;
    public static final int USED_FOR_FIX_MASK = 2;

    // Preallocated arrays, to avoid memory allocation in reportStatus()
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

    // Horizontal estimated position error
    private float HEPE_FACTOR = 4f;

    // Last fix timestamp.
    // Is used to approximate and adjust gps mouse refresh rate.
    private long mFixTimestampDelta = 500;

    private boolean mSatsReady = true;
    private Location loc = new Location(provider);

    /**
     * @param prov  Location provider name
     */
    public NmeaParser(String prov) {
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
            if (mFixTimestampDelta < 100) {
                mFixTimestampDelta = 100;
            }
            if (mFixTimestampDelta > 1000) {
                mFixTimestampDelta = 1000;
            }
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
     * Resets fix variables
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

        if (mFixDateTimeStamp != 0) {
            loc.setTime(mFixDateTimeStamp);
        }

        loc.setLatitude(mFixLatitude);
        loc.setLongitude(mFixLongitude);

        Bundle extras = new Bundle();
        extras.putInt(BUNDLE_SATS, mFixSatsTracked);

        loc.setExtras(extras);
        loc.setAccuracy(mFixAccuracy);
        loc.setAltitude(mFixAltitude);
        loc.setSpeed(mFixSpeed);
        loc.setBearing(mFixBearing);
        loc.makeComplete();

        return loc;
    }

    /**
     * @param time UTC time
     * @return nr seconds since 1970
     */
    private long parseTimeToDate(String time) {
        try {
            // Parse time; We only get timestamp from sentences
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
        if (TextUtils.isEmpty(str)) {
            return 0;
        }

        int res = 0;

        try {
            res = Integer.parseInt(str);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return res;
    }

    private float parseStringToFloat(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0.0f;
        }

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

        int deg = (int) (c / 100);
        double res = deg + (c - deg * 100.0) * 0.016666666666667;

        if ("S".equalsIgnoreCase(orientation) || "W".equalsIgnoreCase(orientation)) {
            return -res;
        }

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
     * Every sentence is implemented as a seperate class. The Nmea parser
     * will select the correct parser based on the sentence identifier.
     * It will get or instantiate a parser to do the job.
     */
    private interface ParseInterface {
        public void parse(String sentence);
    }

    public class GPRMCParser implements ParseInterface {
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
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);

            if (tmp.length > 7) {
                // Always parse timestamp
                updateTimeStamp(parseTimeToDate(tmp[1]));
                mFixQuality =  Integer.parseInt(tmp[6]);

                if (mFixQuality == 0) {
                    // Ŕeturn invalid location
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
        @Override
        public void parse(String sentence) {
            String[] tmp = sentence.split(delim);

            if (tmp.length >= 16) {
                if ("1".equals(tmp[2])) {
                    // Return invalid location or invalid sentence
                    return;
                }

                for (int i = 3; i < 15; i++) {
                    // Tag sats used for fix
                    int sat = parseStringToInt(tmp[i]);

                    if (sat > 0) {
                        mSvMasks[USED_FOR_FIX_MASK] |= (1 << (sat - 1));
                    }
                }

                if (tmp.length > 15) {
                    PDOP = parseStringToFloat(tmp[15]);
                }
                if (tmp.length > 16) {
                    HDOP = parseStringToFloat(tmp[16]);
                }
                if (tmp.length > 17) {
                    VDOP = parseStringToFloat(tmp[17]);
                }
            }
        }
    }

    /**
     * Parse sats information. Use same structure as internal GPS provider
     */
    public class GPGSVParser implements ParseInterface {@Override
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
                    // Tag data as dirty when we have parsed the last part
                    mSatsReady = true;
                }

                int idx = 0;
                while ((currSentence <= totalSentences) && (idx < 4)) {
                    int offset = idx << 2;
                    int base_offset = (currSentence-1) << 2;

                    if (offset+4 < tmp.length) {
                        mSvs[base_offset + idx] = parseStringToInt(tmp[4 + offset]);
                    }
                    if (offset+5 < tmp.length) {
                        mSvElevations[base_offset + idx] = parseStringToInt(tmp[5 + offset]);
                    }
                    if (offset+6 < tmp.length) {
                        mSvAzimuths[base_offset + idx] = parseStringToInt(tmp[6 + offset]);
                    }
                    if (offset+7 < tmp.length) {
                        mSnrs[base_offset + idx] = parseStringToInt(tmp[7 + offset]);
                    }

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
                // Checksums are optional
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
                    // Catch exception thrown by parsers
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
