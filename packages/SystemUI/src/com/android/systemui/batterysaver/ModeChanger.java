/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.SystemClock;

import com.android.systemui.R;
import com.android.systemui.batterysaver.BatterySaverService.State;

public class ModeChanger implements Runnable {

    public Context mContext;
    public Handler mHandler;
    private State mCurrentState = State.UNKNOWN;
    private int mNextMode = 0;
    private int mCurrentMode = 0;
    private int mDelayed = 0;
    private boolean mModeEnabled = true;
    private boolean mWasEnabled = false;
    private boolean mEnabledByUser = false;
    private boolean mDisabledByService = false;
    private boolean mNormalize = false;
    private boolean mShowToast = false;
    private long mTrafficBytes;
    private final long TRAFFIC_BYTES_THRESHOLD = 1 * 1024 * 1024; // 1mb
    private boolean isFullTraffic = false;
    private long mIntervalCheck = 0;
    private final long mIntervalDuration = 30000; //30seconds

    public ModeChanger(Context context) {
        mContext = context;
        mHandler = new Handler();
        mNextMode = getMode();
        mCurrentMode = getMode();
    }

    public void setState(State st) {
        mCurrentState = st;
    }

    public void onActivity(boolean activityIn, boolean activityOut) {
        if (activityIn || activityOut) {
            if ((SystemClock.elapsedRealtime() - mIntervalCheck) < mIntervalDuration) {
                return;
            }
            if (mIntervalCheck != 0) {
                isFullTraffic = true;
            }
            mIntervalCheck = SystemClock.elapsedRealtime();
        } else {
            isFullTraffic = false;
            mIntervalCheck = 0;
        }
    }

    public void updateTraffic() {
        mTrafficBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
    }

    // user configuration
    public void setDelayed(int delay) {
        mDelayed = delay;
    }

    // user configuration
    public void setModeEnabled(boolean enabled) {
        mModeEnabled = enabled;
    }

    // user configuration
    public void setShowToast(boolean enabled) {
        mShowToast = enabled;
    }

    public void setWasEnabled(boolean enabled) {
        // override this if not a user interaction and
        // add super.setWasEnabled(enabled) to the end of line code here
        mWasEnabled = enabled;
    }

    public void setEnabledByUser(boolean user) {
        mEnabledByUser = user;
    }

    public void setDisabledByService(boolean svc) {
        mDisabledByService = svc;
    }

    public boolean wasEnabled() {
        return mWasEnabled;
    }

    public boolean isModeEnabled() {
        return mModeEnabled;
    }

    public boolean isShowToast() {
        return mShowToast;
    }

    public boolean isEnabledByUser() {
        return mEnabledByUser;
    }

    public boolean isDisabledByService() {
        return mDisabledByService;
    }

    public boolean isNormalize() {
        return mNormalize;
    }

    public int getNextMode() {
        return mNextMode;
    }

    public boolean isDelayChanges() {
        final long traffic = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        return ((traffic - mTrafficBytes) > TRAFFIC_BYTES_THRESHOLD) || isFullTraffic;
    }

    public boolean isStateEnabled() {
        // override this
        return false;
    }

    public boolean isSupported() {
        // override this
        return false;
    }

    public int getMode() {
        // override this
        return 0;
    }

    public void stateNormal() {
        // override this
    }

    public void stateSaving() {
        // override this
    }

    public boolean checkModes() {
        // override this
        return true;
    }

    public void setModes() {
        // override this and
        // add super.setModes to the end of line code here
        mCurrentMode = mNextMode;
    }

    public boolean restoreState() {
        // override this if needed
        if (isSupported() && mDisabledByService) {
            stateNormal();
            return true;
        }
        return false;
    }

    public void runModes() {
        if (mCurrentState == State.POWER_SAVING) {
            if ((mWasEnabled || mEnabledByUser)
                 && isSupported()) {
                stateSaving();
                setDisabledByService(true);
            }
        } else if (mCurrentState == State.NORMAL) {
            if ((mWasEnabled || mDisabledByService)
                && isSupported()) {
                stateNormal();
                setDisabledByService(false);
            }
        }
        setModes();
    }

    @Override
    public void run() {
        if (checkModes()) {
            runModes();
        }
    }

    public void changeMode(boolean delayed, boolean normalize) {
        if (!isSupported()) return;
        mNormalize = normalize;
        mHandler.removeCallbacks(this);
        if ((mDelayed == 0) && delayed) {
            mHandler.postDelayed(this, 5000); // 5seconds
            return;
        }
        if (mDelayed == 0 || normalize) {
            run();
        } else {
            mHandler.postDelayed(this, mDelayed * 1000);
        }
    }

    public void changeModes(int Mode, boolean delayed, boolean normalize) {
        if (!isSupported()) return;
        mNormalize = normalize;
        mHandler.removeCallbacks(this);
        if (Mode == getMode() || Mode == mCurrentMode) return;
        mNextMode = Mode;
        if ((mDelayed == 0) && delayed) {
            mHandler.postDelayed(this, 5000); // 5seconds
            return;
        }
        if (mDelayed == 0 || normalize) {
            run();
        } else {
            mHandler.postDelayed(this, mDelayed * 1000);
        }
    }
}
