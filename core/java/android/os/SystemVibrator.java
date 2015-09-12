/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.os;

import android.app.ActivityThread;
import android.content.Context;
import android.media.AudioAttributes;
import android.util.Log;

/**
 * Vibrator implementation that controls the main system vibrator.
 *
 * @hide
 */
public class SystemVibrator extends Vibrator {
    private static final String TAG = "Vibrator";

    private final IVibratorService mService;
    private final Binder mToken = new Binder();

    public SystemVibrator() {
        mService = IVibratorService.Stub.asInterface(
                ServiceManager.getService("vibrator"));
    }

    public SystemVibrator(Context context) {
        super(context);
        mService = IVibratorService.Stub.asInterface(
                ServiceManager.getService("vibrator"));
    }

    @Override
    public boolean hasVibrator() {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return false;
        }
        try {
            return mService.hasVibrator();
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @hide
     */
    @Override
    public void vibrate(int uid, String opPkg, long milliseconds, AudioAttributes attributes) {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return;
        }
        try {
            mService.vibrate(uid, opPkg, milliseconds, usageForAttributes(attributes), mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to vibrate.", e);
        }
    }

    /**
     * @hide
     */
    @Override
    public void vibrate(int uid, String opPkg, long[] pattern, int repeat,
            AudioAttributes attributes) {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return;
        }
        // catch this here because the server will do nothing.  pattern may
        // not be null, let that be checked, because the server will drop it
        // anyway
        if (repeat < pattern.length) {
            try {
                mService.vibratePattern(uid, opPkg, pattern, repeat, usageForAttributes(attributes),
                        mToken);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to vibrate.", e);
            }
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    /**
     * Vibrates with a reduced thread priority. Useful in situations like ringtones
     * where the high-priority task may preempt other activity.
     *
     * @hide
     */
    public void vibrateLowPriority(int uid, String opPkg, long[] pattern, int repeat,
            AudioAttributes attributes) {
        if (mService == null) {
            Log.w(TAG, "Failed to vibrate; no vibrator service.");
            return;
        }
        // catch this here because the server will do nothing.  pattern may
        // not be null, let that be checked, because the server will drop it
        // anyway
        if (repeat < pattern.length) {
            try {
                mService.vibrateLowPriority(uid, opPkg, pattern, repeat, usageForAttributes(attributes),
                        mToken);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to vibrate.", e);
            }
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    /**
     * @hide
     */
    public void vibrateLowPriority(long[] pattern, int repeat, AudioAttributes attributes) {
        vibrateLowPriority(Process.myUid(), ActivityThread.currentPackageName(), pattern, repeat, attributes);
    }

    private static int usageForAttributes(AudioAttributes attributes) {
        return attributes != null ? attributes.getUsage() : AudioAttributes.USAGE_UNKNOWN;
    }

    @Override
    public void cancel() {
        if (mService == null) {
            return;
        }
        try {
            mService.cancelVibrate(mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to cancel vibration.", e);
        }
    }
}
