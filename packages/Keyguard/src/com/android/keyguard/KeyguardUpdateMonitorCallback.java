/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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
package com.android.keyguard;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.WindowManagerPolicy;

import com.android.internal.telephony.IccCardConstants;

/**
 * Callback for general information relevant to lock screen.
 */
class KeyguardUpdateMonitorCallback {

    private static final long VISIBILITY_CHANGED_COLLAPSE_MS = 1000;
    private long mVisibilityChangedCalled;
    private boolean mShowing;

    /**
     * Called when the battery status changes, e.g. when plugged in or unplugged, charge
     * level, etc. changes.
     *
     * @param status current battery status
     */
    void onRefreshBatteryInfo(KeyguardUpdateMonitor.BatteryStatus status) { }

    /**
     * Called once per minute or when the time changes.
     */
    void onTimeChanged() { }

    /**
     * Called when the carrier PLMN or SPN changes.
     *
     * @param plmn The operator name of the registered network.  May be null if it shouldn't
     *   be displayed.
     * @param spn The service provider name.  May be null if it shouldn't be displayed.
     */
    void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) { }

    /**
     * Called when the ringer mode changes.
     * @param state the current ringer state, as defined in
     * {@link AudioManager#RINGER_MODE_CHANGED_ACTION}
     */
    void onRingerModeChanged(int state) { }

    /**
     * Called when the phone state changes. String will be one of:
     * {@link TelephonyManager#EXTRA_STATE_IDLE}
     * {@link TelephonyManager@EXTRA_STATE_RINGING}
     * {@link TelephonyManager#EXTRA_STATE_OFFHOOK
     */
    void onPhoneStateChanged(int phoneState) { }

    /**
     * Called when the visibility of the keyguard changes.
     * @param showing Indicates if the keyguard is now visible.
     */
    void onKeyguardVisibilityChanged(boolean showing) { }

    void onKeyguardVisibilityChangedRaw(boolean showing) {
        final long now = SystemClock.elapsedRealtime();
        if (showing == mShowing
                && (now - mVisibilityChangedCalled) < VISIBILITY_CHANGED_COLLAPSE_MS) return;
        onKeyguardVisibilityChanged(showing);
        mVisibilityChangedCalled = now;
        mShowing = showing;
    }

    /**
     * Called when visibility of lockscreen clock changes, such as when
     * obscured by a widget.
     */
    void onClockVisibilityChanged() { }

    /**
     * Called when the device becomes provisioned
     */
    void onDeviceProvisioned() { }

    /**
     * Called when the device policy changes.
     * See {@link DevicePolicyManager#ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED}
     */
    void onDevicePolicyManagerStateChanged() { }

    /**
     * Called when the user change begins.
     */
    void onUserSwitching(int userId) { }

    /**
     * Called when the user change is complete.
     */
    void onUserSwitchComplete(int userId) { }

    /**
     * Called when the SIM state changes.
     * @param simState
     */
    void onSimStateChanged(IccCardConstants.State simState) { }

    /**
     * Called when a user is removed.
     */
    void onUserRemoved(int userId) { }

    /**
     * Called when the user's info changed.
     */
    void onUserInfoChanged(int userId) { }

    /**
     * Called when boot completed.
     *
     * Note, this callback will only be received if boot complete occurs after registering with
     * KeyguardUpdateMonitor.
     */
    void onBootCompleted() { }

    /**
     * Called when audio client attaches or detaches from AudioManager.
     */
    void onMusicClientIdChanged(int clientGeneration, boolean clearing, PendingIntent intent) { }

    /**
     * Called when the audio playback state changes.
     * @param playbackState
     * @param eventTime
     */
    public void onMusicPlaybackStateChanged(int playbackState, long eventTime) { }

    /**
     * Called when the emergency call button is pressed.
     */
    void onEmergencyCallAction() { }

    /**
     * Called when the transport background changes.
     * @param bitmap
     */
    public void onSetBackground(Bitmap bitmap) {
    }

    /**
     * Called when the screen turns on
     */
    public void onScreenTurnedOn() { }

    /**
     * Called when the screen turns off
     * @param why {@link WindowManagerPolicy#OFF_BECAUSE_OF_USER},
     *   {@link WindowManagerPolicy#OFF_BECAUSE_OF_TIMEOUT} or
     *   {@link WindowManagerPolicy#OFF_BECAUSE_OF_PROX_SENSOR}.
     */
    public void onScreenTurnedOff(int why) { }
    /**
     * Called when the SIM state changes on subscription.
     * @param simState
     * @param subscription
     */
    void onSimStateChanged(IccCardConstants.State simState, int subscription) { }

    /**
     * Called when the carrier PLMN or SPN changes.
     *
     * @param plmn The operator name of the registered network.  May be null if it shouldn't
     *   be displayed.
     * @param spn The service provider name.  May be null if it shouldn't be displayed.
     * @param subscription The subscription for which onRefreshCarrierInfo is meant
     */
    void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int subscription) { }
}
