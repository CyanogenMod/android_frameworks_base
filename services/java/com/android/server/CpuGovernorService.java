/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
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
    private final String TAG = "CpuGovernorService";
    private Context mContext;
    private SamplingRateChangeProcessor mSamplingRateChangeProcessor =
        new SamplingRateChangeProcessor();
    private IOBusyVoteProcessor mIOBusyVoteChangeProcessor =
        new IOBusyVoteProcessor();

    public CpuGovernorService(Context context) {
        mContext = context;
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(IOBusyVoteProcessor.ACTION_IOBUSY_VOTE);
        intentFilter.addAction(IOBusyVoteProcessor.ACTION_IOBUSY_UNVOTE);
        new Thread(mSamplingRateChangeProcessor).start();
        new Thread(mIOBusyVoteChangeProcessor).start();
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean changeAdded = false;

            Log.i(TAG, "intent action: " + intent.getAction());

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (SystemProperties.getInt("dev.pm.dyn_samplingrate", 0) != 0) {
                    while (!changeAdded) {
                        try {
                            mSamplingRateChangeProcessor.getSamplingRateChangeRequests().
                                add(SamplingRateChangeProcessor.SAMPLING_RATE_DECREASE);
                            changeAdded = true;
                        } catch (ConcurrentModificationException concurrentModificationException) {
                            // Ignore and try again.
                        }
                    }

                    synchronized (mSamplingRateChangeProcessor.getSynchObject()) {
                        mSamplingRateChangeProcessor.getSynchObject().notify();
                        mSamplingRateChangeProcessor.setNotificationPending(true);
                    }
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (SystemProperties.getInt("dev.pm.dyn_samplingrate", 0) != 0) {
                    while (!changeAdded) {
                        try {
                            mSamplingRateChangeProcessor.getSamplingRateChangeRequests().
                                add(SamplingRateChangeProcessor.SAMPLING_RATE_INCREASE);
                            changeAdded = true;
                        } catch (ConcurrentModificationException concurrentModificationException) {
                            // Ignore and try again.
                        }
                    }

                    synchronized (mSamplingRateChangeProcessor.getSynchObject()) {
                        mSamplingRateChangeProcessor.getSynchObject().notify();
                        mSamplingRateChangeProcessor.setNotificationPending(true);
                    }
                }
            } else if (intent.getAction().equals(IOBusyVoteProcessor.ACTION_IOBUSY_VOTE)) {
                int voteType = intent.getExtras().getInt("com.android.server.CpuGovernorService.voteType");
                Log.i(TAG, "IOBUSY vote: " + voteType);

                while (!changeAdded) {
                    try {
                        if (voteType == 1) {
                            mIOBusyVoteChangeProcessor.getIOBusyChangeRequests().
                                add(IOBusyVoteProcessor.IO_IS_BUSY_VOTE_ON);
                        } else {
                            mIOBusyVoteChangeProcessor.getIOBusyChangeRequests().
                                add(IOBusyVoteProcessor.IO_IS_BUSY_VOTE_OFF);
                        }
                        changeAdded = true;
                    } catch (ConcurrentModificationException concurrentModificationException) {
                        // Ignore and try again.
                    }
                }

                synchronized (mIOBusyVoteChangeProcessor.getSynchObject()) {
                    mIOBusyVoteChangeProcessor.getSynchObject().notify();
                    mIOBusyVoteChangeProcessor.setNotificationPending(true);
                }
            } else if (intent.getAction().equals(IOBusyVoteProcessor.ACTION_IOBUSY_UNVOTE)) {
                int voteType = intent.getExtras().getInt("com.android.server.CpuGovernorService.voteType");
                Log.i(TAG, "IOBUSY unvote: " + voteType);

                while (!changeAdded) {
                    try {
                        if (voteType == 1) {
                            mIOBusyVoteChangeProcessor.getIOBusyChangeRequests().
                                add(IOBusyVoteProcessor.IO_IS_BUSY_UNVOTE_ON);
                        } else {
                            mIOBusyVoteChangeProcessor.getIOBusyChangeRequests().
                                add(IOBusyVoteProcessor.IO_IS_BUSY_UNVOTE_OFF);
                        }
                        changeAdded = true;
                    } catch (ConcurrentModificationException concurrentModificationException) {
                        // Ignore and try again.
                    }
                }

                synchronized (mIOBusyVoteChangeProcessor.getSynchObject()) {
                    mIOBusyVoteChangeProcessor.getSynchObject().notify();
                    mIOBusyVoteChangeProcessor.setNotificationPending(true);
                }
            }
        }
    };
}

class IOBusyVoteProcessor implements Runnable {
    private final String TAG = "IOBusyVoteProcessor";
    private static final String IO_IS_BUSY_FILE_PATH =
        "/sys/devices/system/cpu/cpufreq/ondemand/io_is_busy";
    private final int MAX_IO_IS_BUSY_VALUE_LENGTH = 32;
    private boolean mNotificationPending = false;
    private Vector<Integer> mIOBusyChanges = new Vector<Integer>();
    private Object mSynchIOBusyChanges = new Object();
    private int mSavedIOBusyValue = -1;
    private int mCurrentIOBusyValue = -1;
    private int mOnVotes = 0;
    private int mOffVotes = 0;
    private boolean mError = false;

    public static final int IO_IS_BUSY_VOTE_ON = 1;
    public static final int IO_IS_BUSY_VOTE_OFF = 2;
    public static final int IO_IS_BUSY_UNVOTE_ON = 3;
    public static final int IO_IS_BUSY_UNVOTE_OFF = 4;
    public static final String ACTION_IOBUSY_VOTE = "com.android.server.CpuGovernorService.action.IOBUSY_VOTE";
    public static final String ACTION_IOBUSY_UNVOTE = "com.android.server.CpuGovernorService.action.IOBUSY_UNVOTE";

    public void setNotificationPending(boolean notificationPending) {
        mNotificationPending = notificationPending;
    }

    public boolean getNotificationPending() {
        return mNotificationPending;
    }

    public Vector<Integer> getIOBusyChangeRequests() {
        return mIOBusyChanges;
    }

    public Object getSynchObject() {
        return mSynchIOBusyChanges;
    }

    public void initializeIOBusyValue() {
        mSavedIOBusyValue = getCurrentIOBusyValue();
        mCurrentIOBusyValue = mSavedIOBusyValue;
    }

    public void run() {
        while (true && !mError) {
            try {
                synchronized (mSynchIOBusyChanges) {
                    if (!mNotificationPending) {
                        mSynchIOBusyChanges.wait();
                    }

                    mNotificationPending = false;
                }
            } catch (InterruptedException interruptedException) {
            }

            while (!mIOBusyChanges.isEmpty()) {
                try{
                    int ioBusyChangeRequestType = mIOBusyChanges.remove(0);

                    if (mOnVotes == 0 && mOffVotes == 0) {
                        // There are no votes in the system. This is a good time
                        // to set the saved io_is_busy value.
                        initializeIOBusyValue();
                    }

                    if (mError) {
                        break;
                    }

                    if (ioBusyChangeRequestType == IO_IS_BUSY_VOTE_ON) {
                        voteOn();
                    } else if (ioBusyChangeRequestType == IO_IS_BUSY_VOTE_OFF) {
                        voteOff();
                    } else if (ioBusyChangeRequestType == IO_IS_BUSY_UNVOTE_ON) {
                        unvoteOn();
                    } else if (ioBusyChangeRequestType == IO_IS_BUSY_UNVOTE_OFF) {
                        unvoteOff();
                    }
                } catch (ConcurrentModificationException concurrentModificationException) {
                    // Ignore and make the thread try again.
                }
            }
        }
    }

    private void voteOn() {
        mCurrentIOBusyValue = 1;
        setIOBusyValue(mCurrentIOBusyValue);
        mOnVotes++;
    }

    private void unvoteOn() {
        if (mOnVotes == 0) {
            Log.e(TAG, "On votes can't be negative.");

            return;
        }

        mOnVotes--;

        if (mOnVotes == 0) {
            // There are no more on votes. If there are no more
            // off votes either, we can go to the orinigal io_is_busy
            // state. Otherwise, we respect the off votes and turn
            // io_is_busy off.
            if (mOffVotes == 0) {
                mCurrentIOBusyValue = mSavedIOBusyValue;
                setIOBusyValue(mCurrentIOBusyValue);
            } else if (mOffVotes > 0) {
                mCurrentIOBusyValue = 0;
                setIOBusyValue(mCurrentIOBusyValue);
            } else {
                mError = true;

                Log.e(TAG, "Off votes can't be negative.");
            }
        }
    }

    private void voteOff() {
        if (mOnVotes == 0) {
            mCurrentIOBusyValue = 0;
            setIOBusyValue(mCurrentIOBusyValue);
        }

        mOffVotes++;
    }

    private void unvoteOff() {
        if (mOffVotes == 0) {
            Log.e(TAG, "Off votes can't be negative.");

            return;
        }

        mOffVotes--;

        if (mOffVotes == 0 && mOnVotes == 0) {
            mCurrentIOBusyValue = mSavedIOBusyValue;
            setIOBusyValue(mCurrentIOBusyValue);
        }
    }

    /*
     * Set the passed in ioBusyValue as the current
     * value of io_is_busy.
     */
    private void setIOBusyValue(int ioBusyValue) {
        File fileIOBusy = new File(IO_IS_BUSY_FILE_PATH);

        if (fileIOBusy.canWrite()) {
            try {
                PrintWriter ioBusyValueWriter = new PrintWriter(fileIOBusy);
                ioBusyValueWriter.print(ioBusyValue + "");
                ioBusyValueWriter.close();
                Log.i(TAG, "Set io_is_busy to " + ioBusyValue);
            } catch (Exception exception) {
                mError = true;

                Log.e(TAG, "Unable to write to io_is_busy.");
            }
        } else {
            mError = true;

            Log.e(TAG, "io_is_busy cannot be written to.");
        }
    }

    /*
     * Get the current io_is_busy value by reading the file.
     */
    private int getCurrentIOBusyValue() {
        File fileIOBusy = new File(IO_IS_BUSY_FILE_PATH);
        int ioBusyValue = -1;

        if (fileIOBusy.canRead()) {
            try {
                BufferedReader ioBusyValueReader = new BufferedReader(
                        new FileReader(fileIOBusy));
                char[] ioBusyContents = new char[MAX_IO_IS_BUSY_VALUE_LENGTH];

                ioBusyValueReader.read(ioBusyContents, 0,
                        MAX_IO_IS_BUSY_VALUE_LENGTH - 1);
                ioBusyValueReader.close();

                try {
                    ioBusyValue = Integer.parseInt((new String(ioBusyContents)).trim());
                } catch (Exception exception) {
                    mError = true;

                    Log.e(TAG, "Unable to read io_is_busy. Contents: " + new String(ioBusyContents));
                }
            } catch (Exception exception) {
                mError = true;

                Log.e(TAG, "io_is_busy cannot be read.");
            }
        } else {
            mError = true;

            Log.e(TAG, "io_is_busy cannot be read.");
        }

        return ioBusyValue;
    }
}

class SamplingRateChangeProcessor implements Runnable {
    private final String TAG = "SamplingRateChangeProcessor";
    private static final String SAMPLING_RATE_FILE_PATH =
        "/sys/devices/system/cpu/cpufreq/ondemand/sampling_rate";
    private static final String SCREEN_OFF_SAMPLING_RATE = "500000";
    private boolean mNotificationPending = false;
    private Vector<Integer> mSamplingRateChanges = new Vector<Integer>();
    private Object mSynchSamplingRateChanges = new Object();
    private String mSavedSamplingRate = "0";
    private int MAX_SAMPLING_RATE_LENGTH = 32;

    public static final int SAMPLING_RATE_INCREASE = 1;
    public static final int SAMPLING_RATE_DECREASE = 2;

    public void setNotificationPending(boolean notificationPending) {
        mNotificationPending = notificationPending;
    }

    public boolean getNotificationPending() {
        return mNotificationPending;
    }

    public Vector<Integer> getSamplingRateChangeRequests() {
        return mSamplingRateChanges;
    }

    public Object getSynchObject() {
        return mSynchSamplingRateChanges;
    }

    public void run() {
        while (true) {
            try {
                synchronized (mSynchSamplingRateChanges) {
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


