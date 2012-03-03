/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *   * Neither the name of Code Aurora nor
 *     the names of its contributors may be used to endorse or promote
 *     products derived from this software without specific prior written
 *     permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintWriter;

import java.util.Vector;
import java.util.ConcurrentModificationException;

import android.os.SystemProperties;

import android.app.PendingIntent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.text.format.Time;

import android.util.Log;

class CpuGovernorService {
    private final String SAMPLING_RATE_FILE_PATH =
        "/sys/devices/system/cpu/cpufreq/ondemand/sampling_rate";
    private final String SCREEN_OFF_SAMPLING_RATE = "500000";
    private final String TAG = "CpuGovernorService";
    private final int SAMPLING_RATE_INCREASE = 1;
    private final int SAMPLING_RATE_DECREASE = 2;
    private final int MAX_SAMPLING_RATE_LENGTH = 32;
    private Context mContext;
    private String mSavedSamplingRate = "0";
    private Vector<Integer> mSamplingRateChanges = new Vector<Integer>();
    private Object mSynchSamplingRateChanges = new Object();
    private boolean mNotificationPending = false;
    private SamplingRateChangeProcessor samplingRateChangeProcessor =
        new SamplingRateChangeProcessor();

    public CpuGovernorService(Context context) {
        mContext = context;
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        new Thread(samplingRateChangeProcessor).start();
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean changeAdded = false;

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (SystemProperties.getInt("dev.pm.dyn_samplingrate", 0) != 0) {
                    while (!changeAdded) {
                        try {
                            mSamplingRateChanges.add(SAMPLING_RATE_DECREASE);
                            changeAdded = true;
                        } catch (ConcurrentModificationException concurrentModificationException) {
                            // Ignore and try again.
                        }
                    }

                    synchronized(mSynchSamplingRateChanges) {
                        mSynchSamplingRateChanges.notify();
                        mNotificationPending = true;
                    }
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (SystemProperties.getInt("dev.pm.dyn_samplingrate", 0) != 0) {
                    while (!changeAdded) {
                        try {
                            mSamplingRateChanges.add(SAMPLING_RATE_INCREASE);
                            changeAdded = true;
                        } catch (ConcurrentModificationException concurrentModificationException) {
                            // Ignore and try again.
                        }
                    }

                    synchronized(mSynchSamplingRateChanges) {
                        mSynchSamplingRateChanges.notify();
                        mNotificationPending = true;
                    }
                }
            }
        }
    };

    private class SamplingRateChangeProcessor implements Runnable {
        public void run() {
            while (true) {
                try {
                    synchronized(mSynchSamplingRateChanges) {
                        if (!mNotificationPending) {
                            mSynchSamplingRateChanges.wait();
                        }

                        mNotificationPending = false;
                    }
                } catch (InterruptedException interruptedException) {
                }

                while (!mSamplingRateChanges.isEmpty()) {
                    try{
                        int samplingRateChangeRequestType = mSamplingRateChanges.remove(0);

                        if (samplingRateChangeRequestType == SAMPLING_RATE_INCREASE) {
                            increaseSamplingRate();
                        } else if (samplingRateChangeRequestType == SAMPLING_RATE_DECREASE) {
                            decreaseSamplingRate();
                        }
                    } catch (ConcurrentModificationException concurrentModificationException) {
                        // Ignore and make the thread try again.
                    }
                }
            }
        }
    }

    private void increaseSamplingRate() {
        File fileSamplingRate = new File(SAMPLING_RATE_FILE_PATH);

        if (fileSamplingRate.canRead() && fileSamplingRate.canWrite()) {
            try {
                BufferedReader samplingRateReader = new BufferedReader(
                        new FileReader(fileSamplingRate));
                char[] samplingRate = new char[MAX_SAMPLING_RATE_LENGTH];

                samplingRateReader.read(samplingRate, 0,
                    MAX_SAMPLING_RATE_LENGTH - 1);
                samplingRateReader.close();

                mSavedSamplingRate = new String(samplingRate);
                PrintWriter samplingRateWriter = new PrintWriter(fileSamplingRate);

                samplingRateWriter.print(SCREEN_OFF_SAMPLING_RATE);
                samplingRateWriter.close();
                Log.i(TAG, "Increased sampling rate.");
            } catch (Exception exception) {
                mSavedSamplingRate = "0";
                Log.e(TAG, "Error occurred while increasing sampling rate: " + exception.getMessage());
            }
        }
    }

    private void decreaseSamplingRate() {
        File fileSamplingRate = new File(SAMPLING_RATE_FILE_PATH);

        if (mSavedSamplingRate.equals("0") == false && fileSamplingRate.canWrite()) {
            try {
                PrintWriter samplingRateWriter = new PrintWriter(fileSamplingRate);

                samplingRateWriter.print(mSavedSamplingRate);
                samplingRateWriter.close();
                Log.i(TAG, "Decreased sampling rate.");
            } catch (Exception exception) {
                Log.e(TAG, "Error occurred while decreasing sampling rate: " + exception.getMessage());
            }
        }
    }
}
