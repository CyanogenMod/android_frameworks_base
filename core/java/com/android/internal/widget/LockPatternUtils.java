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

package com.android.internal.widget;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Utilities for the lock patten and its settings.
 */
public class LockPatternUtils {

    private static final String TAG = "LockPatternUtils";

    private static final String LOCK_PATTERN_FILE = "/system/gesture.key";

    /**
     * The maximum number of incorrect attempts before the user is prevented
     * from trying again for {@link #FAILED_ATTEMPT_TIMEOUT_MS}.
     */
    public static final int FAILED_ATTEMPTS_BEFORE_TIMEOUT = 5;

    /**
     * The number of incorrect attempts before which we fall back on an alternative
     * method of verifying the user, and resetting their lock pattern.
     */
    public static final int FAILED_ATTEMPTS_BEFORE_RESET = 20;

    /**
     * How long the user is prevented from trying again after entering the
     * wrong pattern too many times.
     */
    public static final long FAILED_ATTEMPT_TIMEOUT_MS = 30000L;

    /**
     * The interval of the countdown for showing progress of the lockout.
     */
    public static final long FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS = 1000L;

    /**
     * The minimum number of dots in a valid pattern.
     */
    public static final int MIN_LOCK_PATTERN_SIZE = 4;

    /**
     * The minimum number of dots the user must include in a wrong pattern
     * attempt for it to be counted against the counts that affect
     * {@link #FAILED_ATTEMPTS_BEFORE_TIMEOUT} and {@link #FAILED_ATTEMPTS_BEFORE_RESET}
     */
    public static final int MIN_PATTERN_REGISTER_FAIL = 3;

    private final static String LOCKOUT_PERMANENT_KEY = "lockscreen.lockedoutpermanently";
    private final static String LOCKOUT_ATTEMPT_DEADLINE = "lockscreen.lockoutattemptdeadline";
    private final static String PATTERN_EVER_CHOSEN = "lockscreen.patterneverchosen";
    private final static String PIN_BASED_LOCKING_ENABLED = "lockscreen.pinbased";
    private final static String PIN_CHECK_TIMEOUT = "lockscreen.pinchecktimeout";
    private final static String LOCK_DOTS_VISIBLE = "lockscreen.dotsvisible";
    private final static String LOCK_SHOW_ERROR_PATH = "lockscreen.showerrorpath";
    private final static String LOCK_SHOW_CUSTOM_MSG = "lockscreen.showcustommsg";
    private final static String LOCK_CUSTOM_MSG = "lockscreen.custommsg";
    private final static String LOCK_SHOW_SLIDERS = "lockscreen.showsliders";
    private final static String LOCK_INCORRECT_DELAY = "lockscreen.incorrectdelay";

    private static final int PIN_CHECK_TIMEOUT_MIN = 500;
    private static final int PIN_CHECK_TIMEOUT_DEFAULT = 1500;
    
    private final ContentResolver mContentResolver;

    private static String sLockPatternFilename;

    /**
     * @param contentResolver Used to look up and save settings.
     */
    public LockPatternUtils(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
        // Initialize the location of gesture lock file
        if (sLockPatternFilename == null) {
            sLockPatternFilename = android.os.Environment.getDataDirectory()
                    .getAbsolutePath() + LOCK_PATTERN_FILE;
        }
    }

    /**
     * Check to see if a pattern matches the saved pattern.  If no pattern exists,
     * always returns true.
     * @param pattern The pattern to check.
     * @return Whether the pattern matchees the stored one.
     */
    public boolean checkPattern(List<LockPattern.Cell> pattern) {
        try {
            // Read all the bytes from the file
            RandomAccessFile raf = new RandomAccessFile(sLockPatternFilename, "r");
            final byte[] stored = new byte[(int) raf.length()];
            int got = raf.read(stored, 0, stored.length);
            raf.close();
            if (got <= 0) {
                return true;
            }
            // Compare the hash from the file with the entered pattern's hash
            return Arrays.equals(stored, LockPatternUtils.patternToHash(pattern));
        } catch (FileNotFoundException fnfe) {
            return true;
        } catch (IOException ioe) {
            return true;
        }
    }

    /**
     * Check to see if the user has stored a lock pattern.
     * @return Whether a saved pattern exists.
     */
    public boolean savedPatternExists() {
        try {
            // Check if we can read a byte from the file
            RandomAccessFile raf = new RandomAccessFile(sLockPatternFilename, "r");
            byte first = raf.readByte();
            raf.close();
            return true;
        } catch (FileNotFoundException fnfe) {
            return false;
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Return true if the user has ever chosen a pattern.  This is true even if the pattern is
     * currently cleared.
     *
     * @return True if the user has ever chosen a pattern.
     */
    public boolean isPatternEverChosen() {
        return getBoolean(PATTERN_EVER_CHOSEN);
    }

    /**
     * Save a lock pattern.
     * @param pattern The new pattern to save.
     */
    public void saveLockPattern(List<LockPattern.Cell> pattern) {
        // Compute the hash
        final byte[] hash  = LockPatternUtils.patternToHash(pattern);
        try {
            // Write the hash to file
            RandomAccessFile raf = new RandomAccessFile(sLockPatternFilename, "rw");
            // Truncate the file if pattern is null, to clear the lock
            if (pattern == null) {
                raf.setLength(0);
            } else {
                raf.write(hash, 0, hash.length);
            }
            raf.close();
            setBoolean(PATTERN_EVER_CHOSEN, true);
        } catch (FileNotFoundException fnfe) {
            // Cant do much, unless we want to fail over to using the settings provider
            Log.e(TAG, "Unable to save lock pattern to " + sLockPatternFilename);
        } catch (IOException ioe) {
            // Cant do much
            Log.e(TAG, "Unable to save lock pattern to " + sLockPatternFilename);
        }
    }

    /**
     * Deserialize a pattern.
     * @param string The pattern serialized with {@link #patternToString}
     * @return The pattern.
     */
    public static List<LockPattern.Cell> stringToPattern(String string) {
        List<LockPattern.Cell> result = new ArrayList<LockPattern.Cell>();

        final byte[] bytes = string.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            result.add(LockPattern.Cell.of(b / 3, b % 3));
        }
        return result;
    }

    /**
     * Serialize a pattern.
     * @param pattern The pattern.
     * @return The pattern in string form.
     */
    public static String patternToString(List<LockPattern.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();

        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPattern.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
        }
        return new String(res);
    }

    /*
     * Generate an SHA-1 hash for the pattern. Not the most secure, but it is
     * at least a second level of protection. First level is that the file
     * is in a location only readable by the system process.
     * @param pattern the gesture pattern.
     * @return the hash of the pattern in a byte array.
     */
    static byte[] patternToHash(List<LockPattern.Cell> pattern) {
        if (pattern == null) {
            return null;
        }

        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPattern.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(res);
            return hash;
        } catch (NoSuchAlgorithmException nsa) {
            return res;
        }
    }

    /**
     * @return Whether the lock pattern is enabled.
     */
    public boolean isLockPatternEnabled() {
        return getBoolean(Settings.System.LOCK_PATTERN_ENABLED);
    }

    /**
     * Set whether the lock pattern is enabled.
     */
    public void setLockPatternEnabled(boolean enabled) {
        setBoolean(Settings.System.LOCK_PATTERN_ENABLED, enabled);
    }

    /**
     * @return Whether the visible pattern is enabled.
     */
    public boolean isVisiblePatternEnabled() {
        return getBoolean(Settings.System.LOCK_PATTERN_VISIBLE);
    }

    /**
     * Set whether the visible pattern is enabled.
     */
    public void setVisiblePatternEnabled(boolean enabled) {
        setBoolean(Settings.System.LOCK_PATTERN_VISIBLE, enabled);
    }

    /**
     * @return Whether tactile feedback for the pattern is enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return getBoolean(Settings.System.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
    }

    /**
     * Set whether PIN-based locking is enabled.
     */
    public void setPinLockingEnabled(boolean enabled) {
        setBoolean(PIN_BASED_LOCKING_ENABLED, enabled);
    }

    /**
     * @return Whether PIN-based locking is enabled.
     */
    public boolean isPinLockingEnabled() {
        return getBoolean(PIN_BASED_LOCKING_ENABLED);
    }
    
    public void setVisibleDotsEnabled(boolean enabled) {
        setBoolean(LOCK_DOTS_VISIBLE, enabled);        
    }
    
    public boolean isVisibleDotsEnabled() {
        return getBoolean(LOCK_DOTS_VISIBLE, true);
    }
    
    public void setShowErrorPath(boolean enabled) {
        setBoolean(LOCK_SHOW_ERROR_PATH, enabled);        
    }
    
    public boolean isShowErrorPath() {
        return getBoolean(LOCK_SHOW_ERROR_PATH, true);
    }
    
    public void setShowCustomMsg(boolean enabled) {
        setBoolean(LOCK_SHOW_CUSTOM_MSG, enabled);
    }
    
    public boolean isShowCustomMsg() {
        return getBoolean(LOCK_SHOW_CUSTOM_MSG, false);
    }
    
    public void setCustomMsg(String msg) {
        setString(LOCK_CUSTOM_MSG, msg);
    }
    
    public String getCustomMsg() {
        return getString(LOCK_CUSTOM_MSG);
    }
    
    public void setShowSliders(boolean enabled) {
        setBoolean(LOCK_SHOW_SLIDERS, enabled);
    }
    
    public boolean isShowSliders() {
        return getBoolean(LOCK_SHOW_SLIDERS, true);
    }
    
    public int getIncorrectDelay() {
        return getInt(LOCK_INCORRECT_DELAY, 1500);
    }
    
    public void setIncorrectDelay(int delay) {
        setInt(LOCK_INCORRECT_DELAY, delay);
    }

    /**
     * Set whether tactile feedback for the pattern is enabled.
     */
    public void setTactileFeedbackEnabled(boolean enabled) {
        setBoolean(Settings.System.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED, enabled);
    }

    /**
     * @return delay when accepting patterns for the PIN lock mechanism
     */
    public int getPinCheckTimeout() {
    	return Math.max(
			PIN_CHECK_TIMEOUT_MIN,
			getInt(PIN_CHECK_TIMEOUT, PIN_CHECK_TIMEOUT_DEFAULT));
    }
    
    /**
     * Set the timeout pin check timeout, in milliseconds
     */
    public void setPinCheckTimeout(int timeoutMillis) {
    	timeoutMillis = Math.max(timeoutMillis, PIN_CHECK_TIMEOUT_MIN);
    	setInt(PIN_CHECK_TIMEOUT, timeoutMillis);
    }
    
    /**
     * Set and store the lockout deadline, meaning the user can't attempt his/her unlock
     * pattern until the deadline has passed.
     * @return the chosen deadline.
     */
    public long setLockoutAttemptDeadline() {
        final long deadline = SystemClock.elapsedRealtime() + FAILED_ATTEMPT_TIMEOUT_MS;
        setLong(LOCKOUT_ATTEMPT_DEADLINE, deadline);
        return deadline;
    }

    /**
     * @return The elapsed time in millis in the future when the user is allowed to
     *   attempt to enter his/her lock pattern, or 0 if the user is welcome to
     *   enter a pattern.
     */
    public long getLockoutAttemptDeadline() {
        final long deadline = getLong(LOCKOUT_ATTEMPT_DEADLINE, 0L);
        final long now = SystemClock.elapsedRealtime();
        if (deadline < now || deadline > (now + FAILED_ATTEMPT_TIMEOUT_MS)) {
            return 0L;
        }
        return deadline;
    }

    /**
     * @return Whether the user is permanently locked out until they verify their
     *   credentials.  Occurs after {@link #FAILED_ATTEMPTS_BEFORE_RESET} failed
     *   attempts.
     */
    public boolean isPermanentlyLocked() {
        return getBoolean(LOCKOUT_PERMANENT_KEY);
    }

    /**
     * Set the state of whether the device is permanently locked, meaning the user
     * must authenticate via other means.
     *
     * @param locked Whether the user is permanently locked out until they verify their
     *   credentials.  Occurs after {@link #FAILED_ATTEMPTS_BEFORE_RESET} failed
     *   attempts.
     */
    public void setPermanentlyLocked(boolean locked) {
        setBoolean(LOCKOUT_PERMANENT_KEY, locked);
    }

    /**
     * @return A formatted string of the next alarm (for showing on the lock screen),
     *   or null if there is no next alarm.
     */
    public String getNextAlarm() {
        String nextAlarm = Settings.System.getString(mContentResolver,
                Settings.System.NEXT_ALARM_FORMATTED);
        if (nextAlarm == null || TextUtils.isEmpty(nextAlarm)) {
            return null;
        }
        return nextAlarm;
    }

    private boolean getBoolean(String systemSettingKey) {
        return 1 ==
                android.provider.Settings.System.getInt(
                        mContentResolver,
                        systemSettingKey, 0);
    }

    private boolean getBoolean(String systemSettingKey, boolean defaultValue) {
        return 1 ==
                android.provider.Settings.System.getInt(
                        mContentResolver,
                        systemSettingKey, defaultValue ? 1 : 0);
    }
    
    private void setBoolean(String systemSettingKey, boolean enabled) {
        android.provider.Settings.System.putInt(
                        mContentResolver,
                        systemSettingKey,
                        enabled ? 1 : 0);
    }

    private long getLong(String systemSettingKey, long def) {
        return android.provider.Settings.System.getLong(mContentResolver, systemSettingKey, def);
    }

    private void setLong(String systemSettingKey, long value) {
        android.provider.Settings.System.putLong(mContentResolver, systemSettingKey, value);
    }

    private int getInt(String systemSettingKey, int def) {
        return android.provider.Settings.System.getInt(mContentResolver, systemSettingKey, def);
    }

    private void setInt(String systemSettingKey, int value) {
        android.provider.Settings.System.putInt(mContentResolver, systemSettingKey, value);
    }    
    
    private String getString(String systemSettingKey) {
        String s = android.provider.Settings.System.getString(mContentResolver, systemSettingKey);
        
        if (s == null)
            return "";
        return s;
    }
    
    private void setString(String systemSettingKey, String value) {
        android.provider.Settings.System.putString(mContentResolver, systemSettingKey, value);
    }
    
    
}
