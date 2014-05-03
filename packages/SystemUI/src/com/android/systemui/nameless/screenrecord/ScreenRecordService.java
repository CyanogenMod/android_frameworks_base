/*
* <!--
*    Copyright (C) 2014 The NamelessROM Project
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
* -->
*/

package com.android.systemui.nameless.screenrecord;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ScreenRecordService extends Service {

    private static final String  TAG   = "ScreenRecordService";
    private static final boolean DEBUG = false;

    private static final int NOTIFICATION_ID = 91333379;

    public static final String ACTION_START          = "start";
    public static final String ACTION_STOP           = "stop";
    public static final String ACTION_TOGGLE_POINTER = "toggle_pointer";

    private static final String TMP_PATH = Environment.getExternalStorageDirectory()
            + File.separator + "__tmp_screenrecord.mp4";

    private final Handler mHandler = new Handler();

    private CaptureTask         mCaptureTask;
    private NotificationManager mNotificationManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logDebug("onStartCommand called");

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();

        if (action != null && !action.isEmpty()) {
            logDebug("Action: " + action);
            if (action.equals(ACTION_START)) {
                startScreenRecord();
            } else if (action.equals(ACTION_STOP)) {
                stopScreenRecord();
            } else if (action.equals(ACTION_TOGGLE_POINTER)) {
                try {
                    final int currentStatus = Settings.System.getInt(getContentResolver(),
                            Settings.System.SHOW_TOUCHES);
                    Settings.System.putInt(getContentResolver(),
                            Settings.System.SHOW_TOUCHES, 1 - currentStatus);
                } catch (Settings.SettingNotFoundException ignore) { /* ignored */ }
            }
        } else {
            logDebug("Action is NULL or EMPTY!");
            stopScreenRecord();
        }

        return START_NOT_STICKY;
    }

    private void startScreenRecord() {
        if (mNotificationManager != null) {
            logDebug("Starting while active, stopping.");
            stopScreenRecord();
            return;
        }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotification();
        if (Settings.System.getInt(getContentResolver(),
                Settings.System.SREC_ENABLE_TOUCHES, 0) == 1) {
            togglePointer(true);
        }

        mCaptureTask = new CaptureTask();
        mCaptureTask.execute();
    }

    private void stopScreenRecord() {
        togglePointer(false);
        mCaptureTask.cancel(false);

        // Cancel notification
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
            mNotificationManager = null;
        }

        mHandler.removeCallbacks(mScreenrecordTimeout);
        mHandler.removeCallbacks(mFinishRecord);
        mHandler.postDelayed(mFinishRecord, 2000);
    }

    private void createNotification() {
        final Resources r = getResources();

        final Notification.Builder builder = new Notification.Builder(this)
                .setTicker(r.getString(R.string.screenrecord_notif_ticker))
                .setContentTitle(r.getString(R.string.screenrecord_notif_title))
                .setSmallIcon(R.drawable.ic_sysbar_camera)
                .setWhen(System.currentTimeMillis())
                .setUsesChronometer(true)
                .setOngoing(true);

        final Intent stopIntent = new Intent(this, ScreenRecordService.class)
                .setAction(ScreenRecordService.ACTION_STOP);
        final PendingIntent stopPendIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        final Intent pointerIntent = new Intent(this, ScreenRecordService.class)
                .setAction(ScreenRecordService.ACTION_TOGGLE_POINTER);
        PendingIntent pointerPendIntent = PendingIntent.getService(this, 0, pointerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder
                .addAction(com.android.internal.R.drawable.ic_media_stop,
                        r.getString(R.string.screenrecord_notif_stop), stopPendIntent)
                .addAction(com.android.internal.R.drawable.ic_text_dot,
                        r.getString(R.string.screenrecord_notif_pointer), pointerPendIntent);

        Notification notif = builder.build();
        mNotificationManager.notify(NOTIFICATION_ID, notif);
    }

    private void togglePointer(final boolean on) {
        final ContentResolver resolver = getContentResolver();
        Settings.System.putInt(resolver, Settings.System.SHOW_TOUCHES, on ? 1 : 0);
    }

    private void logDebug(final String msg) {
        if (DEBUG) Log.e(TAG, msg);
    }

    private final Runnable mScreenrecordTimeout = new Runnable() {
        @Override
        public void run() {
            stopScreenRecord();
        }
    };

    private final Runnable mFinishRecord = new Runnable() {
        public void run() {
            final File screenshots = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Screenrecords");

            if (!screenshots.exists()) {
                if (!screenshots.mkdir()) {
                    logDebug("Cannot create screenshots directory");
                    return;
                }
            }

            final String fileName = "SCR_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
            final File input = new File(TMP_PATH);
            final File output = new File(screenshots, fileName);

            try {
                logDebug("Copying file to " + output.getAbsolutePath());
                copyFileUsingStream(input, output);
                if (!input.delete()) {
                    logDebug("Couldn't delete temporary screenrecord");
                }
            } catch (IOException e) {
                logDebug("Unable to copy output file: " + e);
            }

            // Make it appear in gallery, run MediaScanner
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(output)));

            stopSelf();
        }
    };

    private class CaptureTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            mHandler.postDelayed(mScreenrecordTimeout, 31 * 60 * 1000);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final Runtime rt = Runtime.getRuntime();
            final List<String> cmds = new ArrayList<String>();
            cmds.add("/system/bin/screenrecord");
            if (Settings.System.getInt(getContentResolver(),
                    Settings.System.SREC_ENABLE_MIC, 0) == 1) {
                cmds.add("--microphone");
            }
            if (DEBUG) cmds.add("--verbose");
            cmds.add(TMP_PATH);

            try {
                final Process proc = rt.exec(cmds.toArray(new String[cmds.size()]));
                final BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()));

                String log;
                while (!isCancelled()) {
                    if (br.ready()) {
                        log = br.readLine();
                        logDebug(log);
                    }

                    try {
                        // throws exception if not ended, in this case kill it below
                        proc.exitValue();
                        return null;
                    } catch (IllegalThreadStateException ignore) {
                        // ignored
                    }
                }

                // Terminate the recording process
                // HACK: There is no way to send SIGINT to a process, so we... hack
                rt.exec(new String[]{"killall", "-2", "screenrecord"});
            } catch (final IOException e) {
                // Log the error
                logDebug("Error while starting the screenrecord process: " + e);
            }

            return null;
        }

        @Override
        protected void onCancelled() {
            stopScreenRecord();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            stopScreenRecord();
        }
    }

    private static void copyFileUsingStream(final File source, final File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) is.close();
            if (os != null) os.close();
        }
    }

}
