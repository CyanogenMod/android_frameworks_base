/*
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * Galaxy S3 Intl GT-I9300 has a sysfs node which is updated depending on lux when screen brightness
 * changes. Logic is activated/deactivated in DeviceSettings.
 *
 * @hide
 */
public class AutoBrightnessDeviceHandler {

    private static final String TAG = "AutoBrightnessDeviceHandler";
    private static final String FILE = "/sys/class/backlight/panel/auto_brightness";

    private static final int MANUAL = 0;
    private static final int AUTO_INDOOR1 = 1;
    private static final int AUTO_INDOOR2 = 2;
    private static final int AUTO_INDOOR3 = 3;
    private static final int AUTO_OUTDOOR1 = 4;
    private static final int AUTO_OUTDOOR2 = 5;

    private static final float lux15 = 15.0f;
    private static final float lux150 = 150.0f;
    private static final float lux1500 = 1500.0f;
    private static final float lux15000 = 15000.0f;

    private boolean mSysfsFileExists = false;

 // Our context
    private final Context mContext;

    public AutoBrightnessDeviceHandler(Context context) {
        mContext = context;
        mSysfsFileExists = fileExists(FILE);

        if (mSysfsFileExists) {
            toggleAutoBrightness();

            final ContentResolver cr = mContext.getContentResolver();
            final ContentObserver observer = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    toggleAutoBrightness();
                }
            };

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                    false, observer, UserHandle.USER_ALL);
        }
    }

    public void onUpdateAutoBrightness(float ambientLux) {
        int sysfsValue = 0;

        if (mSysfsFileExists) {
            if (readInt(FILE) >= AUTO_INDOOR1) {
                // Autobrightness allowed in Device settings
                if (ambientLux < lux15) {
                    sysfsValue = AUTO_INDOOR1;
                } else if (ambientLux < lux150) {
                    sysfsValue = AUTO_INDOOR2;
                } else if (ambientLux < lux1500) {
                    sysfsValue = AUTO_INDOOR3;
                } else if (ambientLux < lux15000) {
                    sysfsValue = AUTO_OUTDOOR1;
                } else {
                    sysfsValue = AUTO_OUTDOOR2;
                }

                Slog.d(TAG, "AutoBrightness: Lux=" + ambientLux + " Value=" + sysfsValue);
                writeValue(FILE, sysfsValue);
            } else {
                Slog.d(TAG, "AutoBrightness disabled in DeviceSettings");
            }
        }
    }

    public void enableAutoBrightness() {
        if (mSysfsFileExists && (readInt(FILE) >= AUTO_INDOOR1)) {
            Slog.d(TAG, "AutoBrightness enabled");
        } else {
            Slog.d(TAG, "AutoBrightness disabled");
        }
    }

    public void disableAutoBrightness() {
        if (mSysfsFileExists) {
            writeValue(FILE, MANUAL);
        }
        Slog.d(TAG, "AutoBrightness disabled");
    }

    /* Enable/disable autobrightness depending on system and device settings */
    public void toggleAutoBrightness() {
        final ContentResolver cr = mContext.getContentResolver();

        if ((Settings.System.getIntForUser(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, UserHandle.USER_CURRENT) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) &&
           (readInt(FILE) >= AUTO_INDOOR1)){
            enableAutoBrightness();
        } else {
            disableAutoBrightness();
        }
    }

    private static boolean fileExists(String filename) {
        File f = new File(filename);
        return f.exists();
    }

    private static void writeValue(String filename, int value) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(filename));
            fos.write(String.valueOf(value).getBytes());
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readOneLine(String filename) {
        BufferedReader brBuffer;
        String sLine = null;

        try {
            brBuffer = new BufferedReader(new FileReader(filename), 512);
            try {
                sLine = brBuffer.readLine();
            } finally {
                //Slog.w(TAG, "file " + filename + ": " + sLine);
                brBuffer.close();
            }
        } catch (Exception e) {
            Slog.e(TAG, "IO Exception when reading /sys/ file", e);
        }
        return sLine;
    }

    private static int readInt(String filename) {
        Integer i = 0;

        try {
            i = Integer.parseInt(readOneLine(filename));
        } catch (Exception e) {
            i = -1;
        }

        Slog.d(TAG, "readInt " + filename + "=" + i.toString());
        return i;
    }
}
