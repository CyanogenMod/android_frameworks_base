/*
 * Copyright (C) 2008 The Android Open Source Project
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

 
package com.android.server.power;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IActivityManager;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothManager;
import android.content.pm.ThemeUtils;
import android.media.AudioAttributes;
import android.nfc.NfcAdapter;
import android.nfc.INfcAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.os.SystemVibrator;
import android.os.storage.IMountService;
import android.os.storage.IMountShutdownObserver;
import android.provider.Settings;
import android.widget.ListView;

import com.android.internal.telephony.ITelephony;
import com.android.server.pm.PackageManagerService;
import com.android.server.power.PowerManagerService;
import android.util.Log;
import android.view.IWindowManager;
import android.view.WindowManager;
import java.lang.reflect.Method;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;

import java.lang.reflect.Method;

public final class ShutdownThread extends Thread {
    // constants
    private static final String TAG = "ShutdownThread";
    private static final int PHONE_STATE_POLL_SLEEP_MSEC = 500;
    // maximum time we wait for the shutdown broadcast before going on.
    private static final int MAX_BROADCAST_TIME = 10*1000;
    private static final int MAX_SHUTDOWN_WAIT_TIME = 20*1000;
    private static final int MAX_RADIO_WAIT_TIME = 12*1000;

    private static final String SOFT_REBOOT = "soft_reboot";

    // length of vibration before shutting down
    private static final int SHUTDOWN_VIBRATE_MS = 500;
    
    // state tracking
    private static Object sIsStartedGuard = new Object();
    private static boolean sIsStarted = false;
    
    private static boolean mReboot;
    private static boolean mRebootSafeMode;
    private static String mRebootReason;

    // Provides shutdown assurance in case the system_server is killed
    public static final String SHUTDOWN_ACTION_PROPERTY = "sys.shutdown.requested";

    // Indicates whether we are rebooting into safe mode
    public static final String REBOOT_SAFEMODE_PROPERTY = "persist.sys.safemode";

    // static instance of this thread
    private static final ShutdownThread sInstance = new ShutdownThread();

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private final Object mActionDoneSync = new Object();
    private boolean mActionDone;
    private Context mContext;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mCpuWakeLock;
    private PowerManager.WakeLock mScreenWakeLock;
    private Handler mHandler;
    private static MediaPlayer mMediaPlayer;
    private static final String OEM_BOOTANIMATION_FILE = "/oem/media/shutdownanimation.zip";
    private static final String SYSTEM_BOOTANIMATION_FILE = "/system/media/shutdownanimation.zip";
    private static final String SYSTEM_ENCRYPTED_BOOTANIMATION_FILE = "/system/media/shutdownanimation-encrypted.zip";

    private static final String SHUTDOWN_MUSIC_FILE = "/system/media/shutdown.wav";
    private static final String OEM_SHUTDOWN_MUSIC_FILE = "/oem/media/shutdown.wav";

    private boolean isShutdownMusicPlaying = false;

    private static AlertDialog sConfirmDialog;

    private static AudioManager mAudioManager;
    private ShutdownThread() {
    }
 
    /**
     * Request a clean shutdown, waiting for subsystems to clean up their
     * state etc.  Must be called from a Looper thread in which its UI
     * is shown.
     *
     * @param context Context used to display the shutdown progress dialog.
     * @param confirm true if user confirmation is needed before shutting down.
     */
    public static void shutdown(final Context context, boolean confirm) {
        final Context uiContext = getUiContext(context);
        mReboot = false;
        mRebootSafeMode = false;
        shutdownInner(uiContext, confirm);
    }

    private static boolean isAdvancedRebootPossible(final Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean keyguardLocked = km.inKeyguardRestrictedInputMode() && km.isKeyguardSecure();
        boolean advancedRebootEnabled = Settings.Secure.getInt(context.getContentResolver(),
            Settings.Secure.ADVANCED_REBOOT, 0) == 1;
        boolean isPrimaryUser = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;

        return advancedRebootEnabled && !keyguardLocked && isPrimaryUser;
    }

    static void shutdownInner(final Context context, boolean confirm) {
        // ensure that only one thread is trying to power down.
        // any additional calls are just returned
        synchronized (sIsStartedGuard) {
            if (sIsStarted) {
                Log.d(TAG, "Request to shutdown already running, returning.");
                return;
            }
        }

        boolean showRebootOption = false;

        String[] actionsArray;
        String actions = Settings.Secure.getStringForUser(context.getContentResolver(),
                Settings.Secure.POWER_MENU_ACTIONS, UserHandle.USER_CURRENT);
        if (actions == null) {
            actionsArray = context.getResources().getStringArray(
                    com.android.internal.R.array.config_globalActionsList);
        } else {
            actionsArray = actions.split("\\|");
        }

        for (int i = 0; i < actionsArray.length; i++) {
            if (actionsArray[i].equals("reboot")) {
                showRebootOption = true;
                break;
            }
        }
        final int longPressBehavior = context.getResources().getInteger(
                        com.android.internal.R.integer.config_longPressOnPowerBehavior);
        int resourceId = mRebootSafeMode
                ? com.android.internal.R.string.reboot_safemode_confirm
                : (longPressBehavior == 2
                        ? com.android.internal.R.string.shutdown_confirm_question
                        : com.android.internal.R.string.shutdown_confirm);
        if (showRebootOption && !mRebootSafeMode) {
            resourceId = com.android.internal.R.string.reboot_confirm;
        }

        Log.d(TAG, "Notifying thread to start shutdown longPressBehavior=" + longPressBehavior);

        if (confirm) {
            final CloseDialogReceiver closer = new CloseDialogReceiver(context);
            final boolean advancedReboot = isAdvancedRebootPossible(context);
            final Context uiContext = getUiContext(context);

            if (sConfirmDialog != null) {
                sConfirmDialog.dismiss();
                sConfirmDialog = null;
            }
            AlertDialog.Builder confirmDialogBuilder = new AlertDialog.Builder(uiContext)
                    .setTitle(mRebootSafeMode
                            ? com.android.internal.R.string.reboot_safemode_title
                            : showRebootOption
                                    ? com.android.internal.R.string.reboot_title
                                    : com.android.internal.R.string.power_off);

            if (!advancedReboot || mRebootSafeMode) {
                confirmDialogBuilder.setMessage(resourceId);
            } else {
                confirmDialogBuilder
                      .setSingleChoiceItems(com.android.internal.R.array.shutdown_reboot_options,
                              0, null);
            }

            confirmDialogBuilder.setPositiveButton(com.android.internal.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!mRebootSafeMode && advancedReboot) {
                                boolean softReboot = false;
                                ListView reasonsList = ((AlertDialog)dialog).getListView();
                                int selected = reasonsList.getCheckedItemPosition();
                                if (selected != ListView.INVALID_POSITION) {
                                    String actions[] = context.getResources().getStringArray(
                                            com.android.internal.R.array.shutdown_reboot_actions);
                                    if (selected >= 0 && selected < actions.length) {
                                        mRebootReason = actions[selected];
                                        if (actions[selected].equals(SOFT_REBOOT)) {
                                            doSoftReboot();
                                            return;
                                        }
                                    }
                                }

                                mReboot = true;
                            }
                            beginShutdownSequence(context);
                      }
                  });

            confirmDialogBuilder.setNegativeButton(com.android.internal.R.string.no, null);
            sConfirmDialog = confirmDialogBuilder.create();

            closer.dialog = sConfirmDialog;
            sConfirmDialog.setOnDismissListener(closer);
            sConfirmDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            sConfirmDialog.show();
        } else {
            beginShutdownSequence(context);
        }
    }

    private static void doSoftReboot() {
        try {
            final IActivityManager am =
                  ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
            if (am != null) {
                am.restart();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "failure trying to perform soft reboot", e);
        }
    }

    private static class CloseDialogReceiver extends BroadcastReceiver
            implements DialogInterface.OnDismissListener {
        private Context mContext;
        public Dialog dialog;

        CloseDialogReceiver(Context context) {
            mContext = context;
            IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            dialog.cancel();
        }

        public void onDismiss(DialogInterface unused) {
            mContext.unregisterReceiver(this);
        }
    }

    /**
     * Request a clean shutdown, waiting for subsystems to clean up their
     * state etc.  Must be called from a Looper thread in which its UI
     * is shown.
     *
     * @param context Context used to display the shutdown progress dialog.
     * @param reason code to pass to the kernel (e.g. "recovery"), or null.
     * @param confirm true if user confirmation is needed before shutting down.
     */
    public static void reboot(final Context context, String reason, boolean confirm) {
        final Context uiContext = getUiContext(context);
        mReboot = true;
        mRebootSafeMode = false;
        mRebootReason = reason;
        shutdownInner(uiContext, confirm);
    }

    private static String getShutdownMusicFilePath() {
        final String[] fileName = {OEM_SHUTDOWN_MUSIC_FILE, SHUTDOWN_MUSIC_FILE};
        File checkFile = null;
        for(String music : fileName) {
            checkFile = new File(music);
            if (checkFile.exists()) {
                return music;
            }
        }
        return null;
    }

    private static void lockDevice() {
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager
                .getService(Context.WINDOW_SERVICE));
        try {
            wm.updateRotation(false, false);
        } catch (RemoteException e) {
            Log.w(TAG, "boot animation can not lock device!");
        }
    }
    /**
     * Request a reboot into safe mode.  Must be called from a Looper thread in which its UI
     * is shown.
     *
     * @param context Context used to display the shutdown progress dialog.
     * @param confirm true if user confirmation is needed before shutting down.
     */
    public static void rebootSafeMode(final Context context, boolean confirm) {
        mReboot = true;
        mRebootSafeMode = true;
        mRebootReason = null;
        shutdownInner(context, confirm);
    }

    private static void beginShutdownSequence(Context context) {
        synchronized (sIsStartedGuard) {
            if (sIsStarted) {
                Log.d(TAG, "Shutdown sequence already running, returning.");
                return;
            }
            sIsStarted = true;
        }

        //acquire audio focus to make the other apps to stop playing muisc
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(null,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (!checkAnimationFileExist()) {
            // throw up an indeterminate system dialog to indicate radio is
            // shutting down.
            ProgressDialog pd = new ProgressDialog(context);
            if (mReboot) {
                pd.setTitle(context.getText(com.android.internal.R.string.reboot_title));
                pd.setMessage(context.getText(com.android.internal.R.string.reboot_progress));
            } else {
                pd.setTitle(context.getText(com.android.internal.R.string.power_off));
                pd.setMessage(context.getText(com.android.internal.R.string.shutdown_progress));
            }
            pd.setIndeterminate(true);
            pd.setCancelable(false);
            pd.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);

            pd.show();
        }

        sInstance.mContext = context;
        sInstance.mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);

        // make sure we never fall asleep again
        sInstance.mCpuWakeLock = null;
        try {
            sInstance.mCpuWakeLock = sInstance.mPowerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, TAG + "-cpu");
            sInstance.mCpuWakeLock.setReferenceCounted(false);
            sInstance.mCpuWakeLock.acquire();
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to acquire wake lock", e);
            sInstance.mCpuWakeLock = null;
        }

        // also make sure the screen stays on for better user experience
        sInstance.mScreenWakeLock = null;
        if (sInstance.mPowerManager.isScreenOn()) {
            try {
                sInstance.mScreenWakeLock = sInstance.mPowerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK, TAG + "-screen");
                sInstance.mScreenWakeLock.setReferenceCounted(false);
                sInstance.mScreenWakeLock.acquire();
            } catch (SecurityException e) {
                Log.w(TAG, "No permission to acquire wake lock", e);
                sInstance.mScreenWakeLock = null;
            }
        }

        // start the thread that initiates shutdown
        sInstance.mHandler = new Handler() {
        };
        sInstance.start();
    }

    void actionDone() {
        synchronized (mActionDoneSync) {
            mActionDone = true;
            mActionDoneSync.notifyAll();
        }
    }

    /**
     * Makes sure we handle the shutdown gracefully.
     * Shuts off power regardless of radio and bluetooth state if the alloted time has passed.
     */
    public void run() {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                // We don't allow apps to cancel this, so ignore the result.
                actionDone();
            }
        };

        /*
         * Write a system property in case the system_server reboots before we
         * get to the actual hardware restart. If that happens, we'll retry at
         * the beginning of the SystemServer startup.
         */
        {
            String reason = (mReboot ? "1" : "0") + (mRebootReason != null ? mRebootReason : "");
            SystemProperties.set(SHUTDOWN_ACTION_PROPERTY, reason);
        }

        /*
         * If we are rebooting into safe mode, write a system property
         * indicating so.
         */
        if (mRebootSafeMode) {
            SystemProperties.set(REBOOT_SAFEMODE_PROPERTY, "1");
        }

        Log.i(TAG, "Sending shutdown broadcast...");
        
        // First send the high-level shut down broadcast.
        mActionDone = false;
        Intent intent = new Intent(Intent.ACTION_SHUTDOWN);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendOrderedBroadcastAsUser(intent,
                UserHandle.ALL, null, br, mHandler, 0, null, null);
        
        final long endTime = SystemClock.elapsedRealtime() + MAX_BROADCAST_TIME;
        synchronized (mActionDoneSync) {
            while (!mActionDone) {
                long delay = endTime - SystemClock.elapsedRealtime();
                if (delay <= 0) {
                    Log.w(TAG, "Shutdown broadcast timed out");
                    break;
                }
                try {
                    mActionDoneSync.wait(delay);
                } catch (InterruptedException e) {
                }
            }
        }
        
        Log.i(TAG, "Shutting down activity manager...");
        
        final IActivityManager am =
            ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
        if (am != null) {
            try {
                am.shutdown(MAX_BROADCAST_TIME);
            } catch (RemoteException e) {
            }
        }

        Log.i(TAG, "Shutting down package manager...");

        final PackageManagerService pm = (PackageManagerService)
            ServiceManager.getService("package");
        if (pm != null) {
            pm.shutdown();
        }

        String shutDownFile = null;

        //showShutdownAnimation() is called from here to sync
        //music and animation properly
        if(checkAnimationFileExist()) {
            lockDevice();
            showShutdownAnimation();

            if (!isSilentMode()
                    && (shutDownFile = getShutdownMusicFilePath()) != null) {
                isShutdownMusicPlaying = true;
                shutdownMusicHandler.obtainMessage(0, shutDownFile).sendToTarget();
            }
        }

        Log.i(TAG, "wait for shutdown music");
        final long endTimeForMusic = SystemClock.elapsedRealtime() + MAX_BROADCAST_TIME;
        synchronized (mActionDoneSync) {
            while (isShutdownMusicPlaying) {
                long delay = endTimeForMusic - SystemClock.elapsedRealtime();
                if (delay <= 0) {
                    Log.w(TAG, "play shutdown music timeout!");
                    break;
                }
                try {
                    mActionDoneSync.wait(delay);
                } catch (InterruptedException e) {
                }
            }
            if (!isShutdownMusicPlaying) {
                Log.i(TAG, "play shutdown music complete.");
            }
        }

        // Shutdown radios.
        shutdownRadios(MAX_RADIO_WAIT_TIME);

        // Shutdown MountService to ensure media is in a safe state
        IMountShutdownObserver observer = new IMountShutdownObserver.Stub() {
            public void onShutDownComplete(int statusCode) throws RemoteException {
                Log.w(TAG, "Result code " + statusCode + " from MountService.shutdown");
                actionDone();
            }
        };

        Log.i(TAG, "Shutting down MountService");

        // Set initial variables and time out time.
        mActionDone = false;
        final long endShutTime = SystemClock.elapsedRealtime() + MAX_SHUTDOWN_WAIT_TIME;
        synchronized (mActionDoneSync) {
            try {
                final IMountService mount = IMountService.Stub.asInterface(
                        ServiceManager.checkService("mount"));
                if (mount != null) {
                    mount.shutdown(observer);
                } else {
                    Log.w(TAG, "MountService unavailable for shutdown");
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during MountService shutdown", e);
            }
            while (!mActionDone) {
                long delay = endShutTime - SystemClock.elapsedRealtime();
                if (delay <= 0) {
                    Log.w(TAG, "Shutdown wait timed out");
                    break;
                }
                try {
                    mActionDoneSync.wait(delay);
                } catch (InterruptedException e) {
                }
            }
        }

        rebootOrShutdown(mContext, mReboot, mRebootReason);
    }

    private void shutdownRadios(int timeout) {
        // If a radio is wedged, disabling it may hang so we do this work in another thread,
        // just in case.
        final long endTime = SystemClock.elapsedRealtime() + timeout;
        final boolean[] done = new boolean[1];
        Thread t = new Thread() {
            public void run() {
                boolean nfcOff;
                boolean bluetoothOff;
                boolean radioOff;

                final INfcAdapter nfc =
                        INfcAdapter.Stub.asInterface(ServiceManager.checkService("nfc"));
                final ITelephony phone =
                        ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                final IBluetoothManager bluetooth =
                        IBluetoothManager.Stub.asInterface(ServiceManager.checkService(
                        BluetoothAdapter.BLUETOOTH_MANAGER_SERVICE));

                try {
                    nfcOff = nfc == null || nfc.getState() == NfcAdapter.STATE_OFF;
                    if (!nfcOff) {
                        Log.w(TAG, "Turning off NFC...");
                        nfc.disable(false); // Don't persist new state
                    }
                } catch (RemoteException ex) {
                Log.e(TAG, "RemoteException during NFC shutdown", ex);
                    nfcOff = true;
                }

                try {
                    bluetoothOff = bluetooth == null || !bluetooth.isEnabled();
                    if (!bluetoothOff) {
                        Log.w(TAG, "Disabling Bluetooth...");
                        bluetooth.disable(false);  // disable but don't persist new state
                    }
                } catch (RemoteException ex) {
                    Log.e(TAG, "RemoteException during bluetooth shutdown", ex);
                    bluetoothOff = true;
                }

                try {
                    radioOff = phone == null || !phone.needMobileRadioShutdown();
                    if (!radioOff) {
                        Log.w(TAG, "Turning off cellular radios...");
                        phone.shutdownMobileRadios();
                    }
                } catch (RemoteException ex) {
                    Log.e(TAG, "RemoteException during radio shutdown", ex);
                    radioOff = true;
                }

                Log.i(TAG, "Waiting for NFC, Bluetooth and Radio...");

                while (SystemClock.elapsedRealtime() < endTime) {
                    if (!bluetoothOff) {
                        try {
                            bluetoothOff = !bluetooth.isEnabled();
                        } catch (RemoteException ex) {
                            Log.e(TAG, "RemoteException during bluetooth shutdown", ex);
                            bluetoothOff = true;
                        }
                        if (bluetoothOff) {
                            Log.i(TAG, "Bluetooth turned off.");
                        }
                    }
                    if (!radioOff) {
                        try {
                            radioOff = !phone.needMobileRadioShutdown();
                        } catch (RemoteException ex) {
                            Log.e(TAG, "RemoteException during radio shutdown", ex);
                            radioOff = true;
                        }
                        if (radioOff) {
                            Log.i(TAG, "Radio turned off.");
                        }
                    }
                    if (!nfcOff) {
                        try {
                            nfcOff = nfc.getState() == NfcAdapter.STATE_OFF;
                        } catch (RemoteException ex) {
                            Log.e(TAG, "RemoteException during NFC shutdown", ex);
                            nfcOff = true;
                        }
                        if (nfcOff) {
                            Log.i(TAG, "NFC turned off.");
                        }
                    }

                    if (radioOff && bluetoothOff && nfcOff) {
                        Log.i(TAG, "NFC, Radio and Bluetooth shutdown complete.");
                        done[0] = true;
                        break;
                    }
                    SystemClock.sleep(PHONE_STATE_POLL_SLEEP_MSEC);
                }
            }
        };

        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException ex) {
        }
        if (!done[0]) {
            Log.w(TAG, "Timed out waiting for NFC, Radio and Bluetooth shutdown.");
        }
    }

    /**
     * Do not call this directly. Use {@link #reboot(Context, String, boolean)}
     * or {@link #shutdown(Context, boolean)} instead.
     *
     * @param context Context used to vibrate or null without vibration
     * @param reboot true to reboot or false to shutdown
     * @param reason reason for reboot
     */
    public static void rebootOrShutdown(final Context context, boolean reboot, String reason) {
        deviceRebootOrShutdown(reboot, reason);
        if (reboot) {
            Log.i(TAG, "Rebooting, reason: " + reason);
            PowerManagerService.lowLevelReboot(reason);
            Log.e(TAG, "Reboot failed, will attempt shutdown instead");
        } else if (SHUTDOWN_VIBRATE_MS > 0 && context != null) {
            // vibrate before shutting down
            Vibrator vibrator = new SystemVibrator(context);
            try {
                vibrator.vibrate(SHUTDOWN_VIBRATE_MS, VIBRATION_ATTRIBUTES);
            } catch (Exception e) {
                // Failure to vibrate shouldn't interrupt shutdown.  Just log it.
                Log.w(TAG, "Failed to vibrate during shutdown.", e);
            }

            // vibrator is asynchronous so we need to wait to avoid shutting down too soon.
            try {
                Thread.sleep(SHUTDOWN_VIBRATE_MS);
            } catch (InterruptedException unused) {
            }
        }

        // Shutdown power
        Log.i(TAG, "Performing low-level shutdown...");
        PowerManagerService.lowLevelShutdown();
    }

    private static void deviceRebootOrShutdown(boolean reboot, String reason) {
        Class<?> cl;
        PathClassLoader oemClassLoader = new PathClassLoader("/system/framework/oem-services.jar",
            ClassLoader.getSystemClassLoader());
        String deviceShutdownClassName = "com.qti.server.power.ShutdownOem";
        try{
            cl = Class.forName(deviceShutdownClassName);
            Method m;
            try {
                m = cl.getMethod("rebootOrShutdown", new Class[] {boolean.class, String.class});
                m.invoke(cl.newInstance(), reboot, reason);
            } catch (NoSuchMethodException ex) {
                Log.e(TAG, "rebootOrShutdown method not found in class "
                        + deviceShutdownClassName);
            } catch (Exception ex) {
                Log.e(TAG, "Unknown exception hit while trying to invoke rebootOrShutdown");
            }
        } catch(ClassNotFoundException e) {
            Log.e(TAG, "Unable to find class " + deviceShutdownClassName);
        } catch (Exception e) {
            Log.e(TAG, "Unknown exception while trying to invoke rebootOrShutdown");
        }
    }

    private static boolean checkAnimationFileExist() {
        if (new File(OEM_BOOTANIMATION_FILE).exists()
                || new File(SYSTEM_BOOTANIMATION_FILE).exists()
                || new File(SYSTEM_ENCRYPTED_BOOTANIMATION_FILE).exists())
            return true;
        else
            return false;
    }

    private static boolean isSilentMode() {
        return mAudioManager.isSilentMode();
    }

    private static void showShutdownAnimation() {
        /*
         * When boot completed, "service.bootanim.exit" property is set to 1.
         * Bootanimation checks this property to stop showing the boot animation.
         * Since we use the same code for shutdown animation, we
         * need to reset this property to 0. If this is not set to 0 then shutdown
         * will stop and exit after displaying the first frame of the animation
         */
        SystemProperties.set("service.bootanim.exit", "0");

        SystemProperties.set("ctl.start", "bootanim");
    }

    private Handler shutdownMusicHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String path = (String) msg.obj;
            mMediaPlayer = new MediaPlayer();
            try
            {
                mMediaPlayer.reset();
                mMediaPlayer.setDataSource(path);
                mMediaPlayer.prepare();
                mMediaPlayer.start();
                mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        synchronized (mActionDoneSync) {
                            isShutdownMusicPlaying = false;
                            mActionDoneSync.notifyAll();
                        }
                    }
                });
            } catch (IOException e) {
                Log.d(TAG, "play shutdown music error:" + e);
            }
        }
    };

    private static Context getUiContext(Context context) {
        Context uiContext = null;
        if (context != null) {
            uiContext = ThemeUtils.createUiContext(context);
            uiContext.setTheme(android.R.style.Theme_DeviceDefault_Light_DarkActionBar);
        }
        return uiContext != null ? uiContext : context;
    }
}
