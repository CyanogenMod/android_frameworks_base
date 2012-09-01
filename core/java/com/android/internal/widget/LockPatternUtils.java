/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012 The CyanogenMod Project (Calendar)
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

import com.android.internal.R;
import com.android.internal.telephony.ITelephony;
import com.google.android.collect.Lists;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserId;
import android.os.storage.IMountService;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.security.KeyStore;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utilities for the lock pattern and its settings.
 */
public class LockPatternUtils {

    private static final String OPTION_ENABLE_FACELOCK = "enable_facelock";

    private static final String TAG = "LockPatternUtils";

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
     * This dictates when we start telling the user that continued failed attempts will wipe
     * their device.
     */
    public static final int FAILED_ATTEMPTS_BEFORE_WIPE_GRACE = 5;

    /**
     * The minimum number of dots in a valid pattern.
     */
    public static final int MIN_LOCK_PATTERN_SIZE = 4;

    /**
     * The minimum number of dots the user must include in a wrong pattern
     * attempt for it to be counted against the counts that affect
     * {@link #FAILED_ATTEMPTS_BEFORE_TIMEOUT} and {@link #FAILED_ATTEMPTS_BEFORE_RESET}
     */
    public static final int MIN_PATTERN_REGISTER_FAIL = MIN_LOCK_PATTERN_SIZE;

    /**
     * The bit in LOCK_BIOMETRIC_WEAK_FLAGS to be used to indicate whether liveliness should
     * be used
     */
    public static final int FLAG_BIOMETRIC_WEAK_LIVELINESS = 0x1;

    protected final static String LOCKOUT_PERMANENT_KEY = "lockscreen.lockedoutpermanently";
    protected final static String LOCKOUT_ATTEMPT_DEADLINE = "lockscreen.lockoutattemptdeadline";
    protected final static String PATTERN_EVER_CHOSEN_KEY = "lockscreen.patterneverchosen";
    public final static String PASSWORD_TYPE_KEY = "lockscreen.password_type";
    public static final String PASSWORD_TYPE_ALTERNATE_KEY = "lockscreen.password_type_alternate";
    protected final static String LOCK_PASSWORD_SALT_KEY = "lockscreen.password_salt";
    protected final static String DISABLE_LOCKSCREEN_KEY = "lockscreen.disabled";
    protected final static String LOCKSCREEN_OPTIONS = "lockscreen.options";
    public final static String LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK
            = "lockscreen.biometric_weak_fallback";
    public final static String BIOMETRIC_WEAK_EVER_CHOSEN_KEY
            = "lockscreen.biometricweakeverchosen";
    public final static String LOCKSCREEN_POWER_BUTTON_INSTANTLY_LOCKS
            = "lockscreen.power_button_instantly_locks";

    protected final static String PASSWORD_HISTORY_KEY = "lockscreen.passwordhistory";

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private DevicePolicyManager mDevicePolicyManager;
    private ILockSettings mLockSettingsService;
    private int mCurrentUserId = 0;

    private static int PATTERN_SIZE = 3;

    public DevicePolicyManager getDevicePolicyManager() {
        if (mDevicePolicyManager == null) {
            mDevicePolicyManager =
                (DevicePolicyManager)mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (mDevicePolicyManager == null) {
                Log.e(TAG, "Can't get DevicePolicyManagerService: is it running?",
                        new IllegalStateException("Stack trace:"));
            }
        }
        return mDevicePolicyManager;
    }

    /**
     * @param contentResolver Used to look up and save settings.
     */
    public LockPatternUtils(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    private ILockSettings getLockSettings() {
        if (mLockSettingsService == null) {
            mLockSettingsService = ILockSettings.Stub.asInterface(
                (IBinder) ServiceManager.getService("lock_settings"));
        }
        return mLockSettingsService;
    }

    public int getRequestedMinimumPasswordLength() {
        return getDevicePolicyManager().getPasswordMinimumLength(null);
    }

    /**
     * Gets the device policy password mode. If the mode is non-specific, returns
     * MODE_PATTERN which allows the user to choose anything.
     */
    public int getRequestedPasswordQuality() {
        return getDevicePolicyManager().getPasswordQuality(null);
    }

    public int getRequestedPasswordHistoryLength() {
        return getDevicePolicyManager().getPasswordHistoryLength(null);
    }

    public int getRequestedPasswordMinimumLetters() {
        return getDevicePolicyManager().getPasswordMinimumLetters(null);
    }

    public int getRequestedPasswordMinimumUpperCase() {
        return getDevicePolicyManager().getPasswordMinimumUpperCase(null);
    }

    public int getRequestedPasswordMinimumLowerCase() {
        return getDevicePolicyManager().getPasswordMinimumLowerCase(null);
    }

    public int getRequestedPasswordMinimumNumeric() {
        return getDevicePolicyManager().getPasswordMinimumNumeric(null);
    }

    public int getRequestedPasswordMinimumSymbols() {
        return getDevicePolicyManager().getPasswordMinimumSymbols(null);
    }

    public int getRequestedPasswordMinimumNonLetter() {
        return getDevicePolicyManager().getPasswordMinimumNonLetter(null);
    }
    /**
     * Returns the actual password mode, as set by keyguard after updating the password.
     *
     * @return
     */
    public void reportFailedPasswordAttempt() {
        getDevicePolicyManager().reportFailedPasswordAttempt();
    }

    public void reportSuccessfulPasswordAttempt() {
        getDevicePolicyManager().reportSuccessfulPasswordAttempt();
    }

    public void setCurrentUser(int userId) {
        if (Process.myUid() == Process.SYSTEM_UID) {
            mCurrentUserId = userId;
        } else {
            throw new SecurityException("Only the system process can set the current user");
        }
    }

    public int getCurrentUser() {
        if (Process.myUid() == Process.SYSTEM_UID) {
            return mCurrentUserId;
        } else {
            throw new SecurityException("Only the system process can get the current user");
        }
    }

    public void removeUser(int userId) {
        if (Process.myUid() == Process.SYSTEM_UID) {
            try {
                getLockSettings().removeUser(userId);
            } catch (RemoteException re) {
                Log.e(TAG, "Couldn't remove lock settings for user " + userId);
            }
        }
    }

    private int getCurrentOrCallingUserId() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == android.os.Process.SYSTEM_UID) {
            return mCurrentUserId;
        } else {
            return UserId.getUserId(callingUid);
        }
    }

    /**
     * Check to see if a pattern matches the saved pattern.  If no pattern exists,
     * always returns true.
     * @param pattern The pattern to check.
     * @return Whether the pattern matches the stored one.
     */
    public boolean checkPattern(List<LockPatternView.Cell> pattern) {
        int userId = getCurrentOrCallingUserId();
        try {
            return getLockSettings().checkPattern(patternToHash(pattern), userId);
        } catch (RemoteException re) {
            return true;
        }
    }

    /**
     * Check to see if a password matches the saved password.  If no password exists,
     * always returns true.
     * @param password The password to check.
     * @return Whether the password matches the stored one.
     */
    public boolean checkPassword(String password) {
        int userId = getCurrentOrCallingUserId();
        try {
            return getLockSettings().checkPassword(passwordToHash(password), userId);
        } catch (RemoteException re) {
            return true;
        }
    }

    /**
     * Check to see if a password matches any of the passwords stored in the
     * password history.
     *
     * @param password The password to check.
     * @return Whether the password matches any in the history.
     */
    public boolean checkPasswordHistory(String password) {
        String passwordHashString = new String(passwordToHash(password));
        String passwordHistory = getString(PASSWORD_HISTORY_KEY);
        if (passwordHistory == null) {
            return false;
        }
        // Password History may be too long...
        int passwordHashLength = passwordHashString.length();
        int passwordHistoryLength = getRequestedPasswordHistoryLength();
        if(passwordHistoryLength == 0) {
            return false;
        }
        int neededPasswordHistoryLength = passwordHashLength * passwordHistoryLength
                + passwordHistoryLength - 1;
        if (passwordHistory.length() > neededPasswordHistoryLength) {
            passwordHistory = passwordHistory.substring(0, neededPasswordHistoryLength);
        }
        return passwordHistory.contains(passwordHashString);
    }

    /**
     * Check to see if the user has stored a lock pattern.
     * @return Whether a saved pattern exists.
     */
    public boolean savedPatternExists() {
        try {
            return getLockSettings().havePattern(getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            return false;
        }
    }

    /**
     * Check to see if the user has stored a lock pattern.
     * @return Whether a saved pattern exists.
     */
    public boolean savedPasswordExists() {
        try {
            return getLockSettings().havePassword(getCurrentOrCallingUserId());
        } catch (RemoteException re) {
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
        return getBoolean(PATTERN_EVER_CHOSEN_KEY, false);
    }

    /**
     * Return true if the user has ever chosen biometric weak.  This is true even if biometric
     * weak is not current set.
     *
     * @return True if the user has ever chosen biometric weak.
     */
    public boolean isBiometricWeakEverChosen() {
        return getBoolean(BIOMETRIC_WEAK_EVER_CHOSEN_KEY, false);
    }

    /**
     * Used by device policy manager to validate the current password
     * information it has.
     */
    public int getActivePasswordQuality() {
        int activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        // Note we don't want to use getKeyguardStoredPasswordQuality() because we want this to
        // return biometric_weak if that is being used instead of the backup
        int quality =
                (int) getLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        switch (quality) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                if (isLockPatternEnabled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK:
                if (isBiometricWeakInstalled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                if (isLockPasswordEnabled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                if (isLockPasswordEnabled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                if (isLockPasswordEnabled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                if (isLockPasswordEnabled()) {
                    activePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
                }
                break;
        }

        return activePasswordQuality;
    }

    /**
     * Clear any lock pattern or password.
     */
    public void clearLock(boolean isFallback) {
        if(!isFallback) deleteGallery();
        saveLockPassword(null, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        setLockPatternEnabled(false);
        saveLockPattern(null);
        setLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        setLong(PASSWORD_TYPE_ALTERNATE_KEY, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
    }

    /**
     * Disable showing lock screen at all when the DevicePolicyManager allows it.
     * This is only meaningful if pattern, pin or password are not set.
     *
     * @param disable Disables lock screen when true
     */
    public void setLockScreenDisabled(boolean disable) {
        setLong(DISABLE_LOCKSCREEN_KEY, disable ? 1 : 0);
    }

    /**
     * Determine if LockScreen can be disabled. This is used, for example, to tell if we should
     * show LockScreen or go straight to the home screen.
     *
     * @return true if lock screen is can be disabled
     */
    public boolean isLockScreenDisabled() {
        return !isSecure() && getLong(DISABLE_LOCKSCREEN_KEY, 0) != 0;
    }

    /**
     * Calls back SetupFaceLock to delete the temporary gallery file
     */
    public void deleteTempGallery() {
        Intent intent = new Intent().setAction("com.android.facelock.DELETE_GALLERY");
        intent.putExtra("deleteTempGallery", true);
        mContext.sendBroadcast(intent);
    }

    /**
     * Calls back SetupFaceLock to delete the gallery file when the lock type is changed
    */
    void deleteGallery() {
        if(usingBiometricWeak()) {
            Intent intent = new Intent().setAction("com.android.facelock.DELETE_GALLERY");
            intent.putExtra("deleteGallery", true);
            mContext.sendBroadcast(intent);
        }
    }

    /**
     * Save a lock pattern.
     * @param pattern The new pattern to save.
     */
    public void saveLockPattern(List<LockPatternView.Cell> pattern) {
        this.saveLockPattern(pattern, false);
    }

    /**
     * Save a lock pattern.
     * @param pattern The new pattern to save.
     * @param isFallback Specifies if this is a fallback to biometric weak
     */
    public void saveLockPattern(List<LockPatternView.Cell> pattern, boolean isFallback) {
        // Compute the hash
        final byte[] hash = LockPatternUtils.patternToHash(pattern);
        try {
            getLockSettings().setLockPattern(hash, getCurrentOrCallingUserId());
            DevicePolicyManager dpm = getDevicePolicyManager();
            KeyStore keyStore = KeyStore.getInstance();
            if (pattern != null) {
                keyStore.password(patternToString(pattern));
                setBoolean(PATTERN_EVER_CHOSEN_KEY, true);
                if (!isFallback) {
                    deleteGallery();
                    setLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                    dpm.setActivePasswordState(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING,
                            pattern.size(), 0, 0, 0, 0, 0, 0);
                } else {
                    setLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK);
                    setLong(PASSWORD_TYPE_ALTERNATE_KEY,
                            DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                    finishBiometricWeak();
                    dpm.setActivePasswordState(DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK,
                            0, 0, 0, 0, 0, 0, 0);
                }
            } else {
                if (keyStore.isEmpty()) {
                    keyStore.reset();
                }
                dpm.setActivePasswordState(DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, 0, 0,
                        0, 0, 0, 0, 0);
            }
        } catch (RemoteException re) {
            Log.e(TAG, "Couldn't save lock pattern " + re);
        }
    }

    /**
     * Compute the password quality from the given password string.
     */
    static public int computePasswordQuality(String password) {
        boolean hasDigit = false;
        boolean hasNonDigit = false;
        final int len = password.length();
        for (int i = 0; i < len; i++) {
            if (Character.isDigit(password.charAt(i))) {
                hasDigit = true;
            } else {
                hasNonDigit = true;
            }
        }

        if (hasNonDigit && hasDigit) {
            return DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
        }
        if (hasNonDigit) {
            return DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
        }
        if (hasDigit) {
            return DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        }
        return DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    }

    /** Update the encryption password if it is enabled **/
    private void updateEncryptionPassword(String password) {
        DevicePolicyManager dpm = getDevicePolicyManager();
        if (dpm.getStorageEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE) {
            return;
        }

        IBinder service = ServiceManager.getService("mount");
        if (service == null) {
            Log.e(TAG, "Could not find the mount service to update the encryption password");
            return;
        }

        IMountService mountService = IMountService.Stub.asInterface(service);
        try {
            mountService.changeEncryptionPassword(password);
        } catch (RemoteException e) {
            Log.e(TAG, "Error changing encryption password", e);
        }
    }

    /**
     * Save a lock password.  Does not ensure that the password is as good
     * as the requested mode, but will adjust the mode to be as good as the
     * pattern.
     * @param password The password to save
     * @param quality {@see DevicePolicyManager#getPasswordQuality(android.content.ComponentName)}
     */
    public void saveLockPassword(String password, int quality) {
        this.saveLockPassword(password, quality, false);
    }

    /**
     * Save a lock password.  Does not ensure that the password is as good
     * as the requested mode, but will adjust the mode to be as good as the
     * pattern.
     * @param password The password to save
     * @param quality {@see DevicePolicyManager#getPasswordQuality(android.content.ComponentName)}
     * @param isFallback Specifies if this is a fallback to biometric weak
     */
    public void saveLockPassword(String password, int quality, boolean isFallback) {
        // Compute the hash
        final byte[] hash = passwordToHash(password);
        try {
            getLockSettings().setLockPassword(hash, getCurrentOrCallingUserId());
            DevicePolicyManager dpm = getDevicePolicyManager();
            KeyStore keyStore = KeyStore.getInstance();
            if (password != null) {
                // Update the encryption password.
                updateEncryptionPassword(password);

                // Update the keystore password
                keyStore.password(password);

                int computedQuality = computePasswordQuality(password);
                if (!isFallback) {
                    deleteGallery();
                    setLong(PASSWORD_TYPE_KEY, Math.max(quality, computedQuality));
                    if (computedQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                        int letters = 0;
                        int uppercase = 0;
                        int lowercase = 0;
                        int numbers = 0;
                        int symbols = 0;
                        int nonletter = 0;
                        for (int i = 0; i < password.length(); i++) {
                            char c = password.charAt(i);
                            if (c >= 'A' && c <= 'Z') {
                                letters++;
                                uppercase++;
                            } else if (c >= 'a' && c <= 'z') {
                                letters++;
                                lowercase++;
                            } else if (c >= '0' && c <= '9') {
                                numbers++;
                                nonletter++;
                            } else {
                                symbols++;
                                nonletter++;
                            }
                        }
                        dpm.setActivePasswordState(Math.max(quality, computedQuality),
                                password.length(), letters, uppercase, lowercase,
                                numbers, symbols, nonletter);
                    } else {
                        // The password is not anything.
                        dpm.setActivePasswordState(
                                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED,
                                0, 0, 0, 0, 0, 0, 0);
                    }
                } else {
                    // Case where it's a fallback for biometric weak
                    setLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK);
                    setLong(PASSWORD_TYPE_ALTERNATE_KEY, Math.max(quality, computedQuality));
                    finishBiometricWeak();
                    dpm.setActivePasswordState(DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK,
                            0, 0, 0, 0, 0, 0, 0);
                }
                // Add the password to the password history. We assume all
                // password
                // hashes have the same length for simplicity of implementation.
                String passwordHistory = getString(PASSWORD_HISTORY_KEY);
                if (passwordHistory == null) {
                    passwordHistory = new String();
                }
                int passwordHistoryLength = getRequestedPasswordHistoryLength();
                if (passwordHistoryLength == 0) {
                    passwordHistory = "";
                } else {
                    passwordHistory = new String(hash) + "," + passwordHistory;
                    // Cut it to contain passwordHistoryLength hashes
                    // and passwordHistoryLength -1 commas.
                    passwordHistory = passwordHistory.substring(0, Math.min(hash.length
                            * passwordHistoryLength + passwordHistoryLength - 1, passwordHistory
                            .length()));
                }
                setString(PASSWORD_HISTORY_KEY, passwordHistory);
            } else {
                // Conditionally reset the keystore if empty. If
                // non-empty, we are just switching key guard type
                if (keyStore.isEmpty()) {
                    keyStore.reset();
                }
                dpm.setActivePasswordState(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, 0, 0, 0, 0, 0, 0, 0);
            }
        } catch (RemoteException re) {
            // Cant do much
            Log.e(TAG, "Unable to save lock password " + re);
        }
    }

    /**
     * Retrieves the quality mode we're in.
     * {@see DevicePolicyManager#getPasswordQuality(android.content.ComponentName)}
     *
     * @return stored password quality
     */
    public int getKeyguardStoredPasswordQuality() {
        int quality =
                (int) getLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        // If the user has chosen to use weak biometric sensor, then return the backup locking
        // method and treat biometric as a special case.
        if (quality == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK) {
            quality =
                (int) getLong(PASSWORD_TYPE_ALTERNATE_KEY,
                        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        }
        return quality;
    }

    /**
     * @return true if the lockscreen method is set to biometric weak
     */
    public boolean usingBiometricWeak() {
        int quality =
                (int) getLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        return quality == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK;
    }

    /**
     * Deserialize a pattern.
     * @param string The pattern serialized with {@link #patternToString}
     * @return The pattern.
     */
    public static List<LockPatternView.Cell> stringToPattern(String string) {
        List<LockPatternView.Cell> result = Lists.newArrayList();

        final byte[] bytes = string.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            result.add(LockPatternView.Cell.of(b / PATTERN_SIZE, b % PATTERN_SIZE));
        }
        return result;
    }

    /**
     * Serialize a pattern.
     * @param pattern The pattern.
     * @return The pattern in string form.
     */
    public static String patternToString(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();

        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * PATTERN_SIZE + cell.getColumn());
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
    private static byte[] patternToHash(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return null;
        }

        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * PATTERN_SIZE + cell.getColumn());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(res);
            return hash;
        } catch (NoSuchAlgorithmException nsa) {
            return res;
        }
    }

    private String getSalt() {
        long salt = getLong(LOCK_PASSWORD_SALT_KEY, 0);
        if (salt == 0) {
            try {
                salt = SecureRandom.getInstance("SHA1PRNG").nextLong();
                setLong(LOCK_PASSWORD_SALT_KEY, salt);
                Log.v(TAG, "Initialized lock password salt");
            } catch (NoSuchAlgorithmException e) {
                // Throw an exception rather than storing a password we'll never be able to recover
                throw new IllegalStateException("Couldn't get SecureRandom number", e);
            }
        }
        return Long.toHexString(salt);
    }

    /*
     * Generate a hash for the given password. To avoid brute force attacks, we use a salted hash.
     * Not the most secure, but it is at least a second level of protection. First level is that
     * the file is in a location only readable by the system process.
     * @param password the gesture pattern.
     * @return the hash of the pattern in a byte array.
     */
    public byte[] passwordToHash(String password) {
        if (password == null) {
            return null;
        }
        String algo = null;
        byte[] hashed = null;
        try {
            byte[] saltedPassword = (password + getSalt()).getBytes();
            byte[] sha1 = MessageDigest.getInstance(algo = "SHA-1").digest(saltedPassword);
            byte[] md5 = MessageDigest.getInstance(algo = "MD5").digest(saltedPassword);
            hashed = (toHex(sha1) + toHex(md5)).getBytes();
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "Failed to encode string because of missing algorithm: " + algo);
        }
        return hashed;
    }

    private static String toHex(byte[] ary) {
        final String hex = "0123456789ABCDEF";
        String ret = "";
        for (int i = 0; i < ary.length; i++) {
            ret += hex.charAt((ary[i] >> 4) & 0xf);
            ret += hex.charAt(ary[i] & 0xf);
        }
        return ret;
    }

    /**
     * @return Whether the lock password is enabled, or if it is set as a backup for biometric weak
     */
    public boolean isLockPasswordEnabled() {
        long mode = getLong(PASSWORD_TYPE_KEY, 0);
        long backupMode = getLong(PASSWORD_TYPE_ALTERNATE_KEY, 0);
        final boolean passwordEnabled = mode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
        final boolean backupEnabled = backupMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                || backupMode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                || backupMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                || backupMode == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;

        return savedPasswordExists() && (passwordEnabled ||
                (usingBiometricWeak() && backupEnabled));
    }

    /**
     * @return Whether the lock pattern is enabled, or if it is set as a backup for biometric weak
     */
    public boolean isLockPatternEnabled() {
        final boolean backupEnabled =
                getLong(PASSWORD_TYPE_ALTERNATE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING)
                == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;

        return getBoolean(Settings.Secure.LOCK_PATTERN_ENABLED, false)
                && (getLong(PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING)
                        == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING ||
                        (usingBiometricWeak() && backupEnabled));
    }

    /**
     * @return Whether biometric weak lock is installed and that the front facing camera exists
     */
    public boolean isBiometricWeakInstalled() {
        // Check that it's installed
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo("com.android.facelock", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        // Check that the camera is enabled
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return false;
        }
        if (getDevicePolicyManager().getCameraDisabled(null)) {
            return false;
        }


        return true;
    }

    /**
     * Set whether biometric weak liveliness is enabled.
     */
    public void setBiometricWeakLivelinessEnabled(boolean enabled) {
        long currentFlag = getLong(Settings.Secure.LOCK_BIOMETRIC_WEAK_FLAGS, 0L);
        long newFlag;
        if (enabled) {
            newFlag = currentFlag | FLAG_BIOMETRIC_WEAK_LIVELINESS;
        } else {
            newFlag = currentFlag & ~FLAG_BIOMETRIC_WEAK_LIVELINESS;
        }
        setLong(Settings.Secure.LOCK_BIOMETRIC_WEAK_FLAGS, newFlag);
    }

    /**
     * @return Whether the biometric weak liveliness is enabled.
     */
    public boolean isBiometricWeakLivelinessEnabled() {
        long currentFlag = getLong(Settings.Secure.LOCK_BIOMETRIC_WEAK_FLAGS, 0L);
        return ((currentFlag & FLAG_BIOMETRIC_WEAK_LIVELINESS) != 0);
    }

    /**
     * Set whether the lock pattern is enabled.
     */
    public void setLockPatternEnabled(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_PATTERN_ENABLED, enabled);
    }

    /**
     * @return Whether the visible pattern is enabled.
     */
    public boolean isVisiblePatternEnabled() {
        return getBoolean(Settings.Secure.LOCK_PATTERN_VISIBLE, false);
    }

    /**
     * Set whether the visible pattern is enabled.
     */
    public void setVisiblePatternEnabled(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_PATTERN_VISIBLE, enabled);
    }

    /**
     * @return Whether tactile feedback for the pattern is enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return getBoolean(Settings.Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED, false);
    }

    /**
     * Set whether tactile feedback for the pattern is enabled.
     */
    public void setTactileFeedbackEnabled(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED, enabled);
    }

    public void setVisibleDotsEnabled(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_DOTS_VISIBLE, enabled);
    }

    public boolean isVisibleDotsEnabled() {
        return getBoolean(Settings.Secure.LOCK_DOTS_VISIBLE, true);
    }

    public void setShowErrorPath(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_SHOW_ERROR_PATH, enabled);
    }

    public boolean isShowErrorPath() {
        return getBoolean(Settings.Secure.LOCK_SHOW_ERROR_PATH, true);
    }

    /**
     * @return the pattern lockscreen size
     */
    public int getLockPatternSize() {
        return getInteger(Settings.Secure.LOCK_PATTERN_SIZE, 3);
    }

    /**
     * Set the pattern lockscreen size
     */
    public void setLockPatternSize(int size) {
        setInteger(Settings.Secure.LOCK_PATTERN_SIZE, size);
        PATTERN_SIZE = size;
    }

    /**
     * Update PATTERN_SIZE for this LockPatternUtils instance
     * This must be called before patternToHash, patternToString, etc
     * will work correctly with a non-standard size
     */
    public void updateLockPatternSize() {
        PATTERN_SIZE = getLockPatternSize();
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
        return getBoolean(LOCKOUT_PERMANENT_KEY, false);
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

    public boolean isEmergencyCallCapable() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
    }

    public boolean isPukUnlockScreenEnable() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_puk_unlock_screen);
    }

    public boolean isEmergencyCallEnabledWhileSimLocked() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_emergency_call_while_sim_locked);
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

    /**
     * @return A formatted string of the next calendar event with a reminder
     * (for showing on the lock screen), or null if there is no next event
     * within a certain look-ahead time.
     */
    public String[] getNextCalendarAlarm(long lookahead, String[] calendars,
            boolean remindersOnly) {
        long now = System.currentTimeMillis();
        long later = now + lookahead;

        StringBuilder where = new StringBuilder();
        if (remindersOnly) {
            where.append(CalendarContract.Events.HAS_ALARM + "=1");
        }
        if (calendars != null && calendars.length > 0) {
            if (remindersOnly) {
                where.append(" AND ");
            }
            where.append(CalendarContract.Events.CALENDAR_ID + " in (");
            for (int i = 0; i < calendars.length; i++) {
                where.append(calendars[i]);
                if (i != calendars.length - 1) {
                    where.append(",");
                }
            }
            where.append(") ");
        }

        // Projection array
        String[] projection = new String[] {
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        };

        // The indices for the projection array
        int TITLE_INDEX = 0;
        int BEGIN_TIME_INDEX = 1;
        int DESCRIPTION_INDEX = 2;
        int LOCATION_INDEX = 3;
        int ALL_DAY_INDEX = 4;

        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now, later));
        String[] nextCalendarAlarm = new String[2];
        Cursor cursor = null;

        try {
            cursor = mContentResolver.query(uri, projection,
                    where.toString(), null, "begin ASC");

            if (cursor != null && cursor.moveToFirst()) {

                String title = cursor.getString(TITLE_INDEX);
                long begin = cursor.getLong(BEGIN_TIME_INDEX);
                String description = cursor.getString(DESCRIPTION_INDEX);
                String location = cursor.getString(LOCATION_INDEX);
                boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;

                // Check the next event in the case of all day event. As UTC is used for all day
                // events, the next event may be the one that actually starts sooner
                if (allDay && !cursor.isLast()) {
                    cursor.moveToNext();
                    long nextBegin = cursor.getLong(BEGIN_TIME_INDEX);
                    if (nextBegin < begin + TimeZone.getDefault().getOffset(begin)) {
                        title = cursor.getString(TITLE_INDEX);
                        begin = nextBegin;
                        description = cursor.getString(DESCRIPTION_INDEX);
                        location = cursor.getString(LOCATION_INDEX);
                        allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                    }
                }

                // Set the event title as the first array item
                nextCalendarAlarm[0] = title.toString();

                // Start building the event details string
                // Starting with the date
                Date start = new Date(begin);
                StringBuilder sb = new StringBuilder();

                if (allDay) {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            mContext.getString(R.string.abbrev_wday_month_day_no_year));
                    // Calendar stores all-day events in UTC -- setting the time zone ensures
                    // the correct date is shown.
                    sdf.setTimeZone(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
                    sb.append(sdf.format(start));
                } else {
                    sb.append(DateFormat.format("E", start));
                    sb.append(" ");
                    sb.append(DateFormat.getTimeFormat(mContext).format(start));
                }

                // Add the event location if it should be shown
                int showLocation = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.LOCKSCREEN_CALENDAR_SHOW_LOCATION, 0);
                if (showLocation != 0 && !TextUtils.isEmpty(location)) {
                    switch(showLocation) {
                        case 1:
                            // Show first line
                            int end = location.indexOf('\n');
                            if(end == -1) {
                                sb.append(": " + location);
                            } else {
                                sb.append(": " + location.substring(0, end));
                            }
                            break;
                        case 2:
                            // Show all
                            sb.append(": " + location);
                            break;
                    }
                }

                // Add the event description if it should be shown
                int showDescription = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.LOCKSCREEN_CALENDAR_SHOW_DESCRIPTION, 0);
                if (showDescription != 0 && !TextUtils.isEmpty(description)) {

                    // Show the appropriate separator
                    if (showLocation == 0) {
                        sb.append(": ");
                    } else {
                        sb.append(" - ");
                    }

                    switch(showDescription) {
                        case 1:
                            // Show first line
                            int end = description.indexOf('\n');
                            if(end == -1) {
                                sb.append(description);
                            } else {
                                sb.append(description.substring(0, end));
                            }
                            break;
                        case 2:
                            // Show all
                            sb.append(description);
                            break;
                    }
                }

                // Set the time, location and description as the second array item
                nextCalendarAlarm[1] = sb.toString();
            }
        } catch (Exception e) {
            // Do nothing
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return nextCalendarAlarm;
    }

    private boolean getBoolean(String secureSettingKey, boolean defaultValue) {
        try {
            return getLockSettings().getBoolean(secureSettingKey, defaultValue,
                    getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            return defaultValue;
        }
    }

    private void setBoolean(String secureSettingKey, boolean enabled) {
        try {
            getLockSettings().setBoolean(secureSettingKey, enabled, getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            // What can we do?
            Log.e(TAG, "Couldn't write boolean " + secureSettingKey + re);
        }
    }

    private long getLong(String secureSettingKey, long defaultValue) {
        try {
            return getLockSettings().getLong(secureSettingKey, defaultValue,
                    getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            return defaultValue;
        }
    }

    private void setLong(String secureSettingKey, long value) {
        try {
            getLockSettings().setLong(secureSettingKey, value, getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            // What can we do?
            Log.e(TAG, "Couldn't write long " + secureSettingKey + re);
        }
    }

    private int getInteger(String secureSettingKey, int defaultValue) {
        try {
            return getLockSettings().getInteger(secureSettingKey, defaultValue,
                    getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            return defaultValue;
        }
    }

    private void setInteger(String secureSettingKey, int value) {
        try {
            getLockSettings().setInteger(secureSettingKey, value, getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            // What can we do?
            Log.e(TAG, "Couldn't write boolean " + secureSettingKey + re);
        }
    }

    private String getString(String secureSettingKey) {
        try {
            return getLockSettings().getString(secureSettingKey, null,
                    getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            return null;
        }
    }

    private void setString(String secureSettingKey, String value) {
        try {
            getLockSettings().setString(secureSettingKey, value, getCurrentOrCallingUserId());
        } catch (RemoteException re) {
            // What can we do?
            Log.e(TAG, "Couldn't write string " + secureSettingKey + re);
        }
    }

    public boolean isSecure() {
        long mode = getKeyguardStoredPasswordQuality();
        final boolean isPattern = mode == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
        final boolean isPassword = mode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                || mode == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
        final boolean secure = isPattern && isLockPatternEnabled() && savedPatternExists()
                || isPassword && savedPasswordExists();
        return secure;
    }

    /**
     * Sets the emergency button visibility based on isEmergencyCallCapable().
     *
     * If the emergency button is visible, sets the text on the emergency button
     * to indicate what action will be taken.
     *
     * If there's currently a call in progress, the button will take them to the call
     * @param button the button to update
     * @param the phone state:
     *  {@link TelephonyManager#CALL_STATE_IDLE}
     *  {@link TelephonyManager#CALL_STATE_RINGING}
     *  {@link TelephonyManager#CALL_STATE_OFFHOOK}
     * @param shown indicates whether the given screen wants the emergency button to show at all
     */
    public void updateEmergencyCallButtonState(Button button, int  phoneState, boolean shown) {
        if (isEmergencyCallCapable() && shown) {
            button.setVisibility(View.VISIBLE);
        } else {
            button.setVisibility(View.GONE);
            return;
        }

        int textId;
        if (phoneState == TelephonyManager.CALL_STATE_OFFHOOK) {
            // show "return to call" text and show phone icon
            textId = R.string.lockscreen_return_to_call;
            int phoneCallIcon = R.drawable.stat_sys_phone_call;
            button.setCompoundDrawablesWithIntrinsicBounds(phoneCallIcon, 0, 0, 0);
        } else {
            textId = R.string.lockscreen_emergency_call;
            int emergencyIcon = R.drawable.ic_emergency;
            button.setCompoundDrawablesWithIntrinsicBounds(emergencyIcon, 0, 0, 0);
        }
        button.setText(textId);
    }

    /**
     * Resumes a call in progress. Typically launched from the EmergencyCall button
     * on various lockscreens.
     *
     * @return true if we were able to tell InCallScreen to show.
     */
    public boolean resumeCall() {
        ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        try {
            if (phone != null && phone.showCallScreen()) {
                return true;
            }
        } catch (RemoteException e) {
            // What can we do?
        }
        return false;
    }

    private void finishBiometricWeak() {
        setBoolean(BIOMETRIC_WEAK_EVER_CHOSEN_KEY, true);

        // Launch intent to show final screen, this also
        // moves the temporary gallery to the actual gallery
        Intent intent = new Intent();
        intent.setClassName("com.android.facelock",
                "com.android.facelock.SetupEndScreen");
        mContext.startActivity(intent);
    }

    public void setPowerButtonInstantlyLocks(boolean enabled) {
        setBoolean(LOCKSCREEN_POWER_BUTTON_INSTANTLY_LOCKS, enabled);
    }

    public boolean getPowerButtonInstantlyLocks() {
        return getBoolean(LOCKSCREEN_POWER_BUTTON_INSTANTLY_LOCKS, true);
    }

    /**
     * @hide
     * Set the lock-before-unlock option (show widgets before the secure
     * unlock screen). See config_enableLockBeforeUnlockScreen
     */
    public void setLockBeforeUnlock(boolean enabled) {
        setBoolean(Settings.Secure.LOCK_BEFORE_UNLOCK, enabled);
    }

    /**
     * @hide
     * Get the lock-before-unlock option (show widgets before the secure
     * unlock screen).
     */
    public boolean getLockBeforeUnlock() {
        return getBoolean(Settings.Secure.LOCK_BEFORE_UNLOCK, false);
    }
}
