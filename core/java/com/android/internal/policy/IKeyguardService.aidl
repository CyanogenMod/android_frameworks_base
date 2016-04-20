/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.internal.policy;

import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.policy.IKeyguardExitCallback;

import android.os.Bundle;

oneway interface IKeyguardService {

    /**
     * Sets the Keyguard as occluded when a window dismisses the Keyguard with flag
     * FLAG_SHOW_ON_LOCK_SCREEN.
     *
     * @param isOccluded Whether the Keyguard is occluded by another window.
     */
    void setOccluded(boolean isOccluded);

    void addStateMonitorCallback(IKeyguardStateCallback callback);
    void verifyUnlock(IKeyguardExitCallback callback);
    void keyguardDone(boolean authenticated, boolean wakeup);
    void dismiss();
    void onDreamingStarted();
    void onDreamingStopped();

    /**
     * Called when the device has started going to sleep.
     *
     * @param why {@link #OFF_BECAUSE_OF_USER}, {@link #OFF_BECAUSE_OF_ADMIN},
     * or {@link #OFF_BECAUSE_OF_TIMEOUT}.
     */
    void onStartedGoingToSleep(int reason);

    /**
     * Called when the device has finished going to sleep.
     *
     * @param why {@link #OFF_BECAUSE_OF_USER}, {@link #OFF_BECAUSE_OF_ADMIN},
     * or {@link #OFF_BECAUSE_OF_TIMEOUT}.
     */
    void onFinishedGoingToSleep(int reason);

    /**
     * Called when the device has started waking up.
     */
    void onStartedWakingUp();

    /**
     * Called when the device screen is turning on.
     */
    void onScreenTurningOn(IKeyguardDrawnCallback callback);

    /**
     * Called when the screen has actually turned on.
     */
    void onScreenTurnedOn();

    /**
     * Called when the screen has turned off.
     */
    void onScreenTurnedOff();

    void setKeyguardEnabled(boolean enabled);
    void onSystemReady();
    void doKeyguardTimeout(in Bundle options);
    void setCurrentUser(int userId);
    void onBootCompleted();

    /**
     * Notifies that the activity behind has now been drawn and it's safe to remove the wallpaper
     * and keyguard flag.
     *
     * @param startTime the start time of the animation in uptime milliseconds
     * @param fadeoutDuration the duration of the exit animation, in milliseconds
     */
    void startKeyguardExitAnimation(long startTime, long fadeoutDuration);

    /**
     * Notifies the Keyguard that the activity that was starting has now been drawn and it's safe
     * to start the keyguard dismiss sequence.
     */
    void onActivityDrawn();
    void showKeyguard();
}
