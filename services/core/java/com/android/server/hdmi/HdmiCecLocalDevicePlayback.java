/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.hdmi;

import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.hardware.hdmi.IHdmiControlCallback;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.hdmi.HdmiAnnotations.ServiceThreadOnly;

/**
 * Represent a logical device of type Playback residing in Android system.
 */
final class HdmiCecLocalDevicePlayback extends HdmiCecLocalDevice {
    private static final String TAG = "HdmiCecLocalDevicePlayback";

    private boolean mIsActiveSource = false;

    // Used to keep the device awake while it is the active source. For devices that
    // cannot wake up via CEC commands, this address the inconvenience of having to
    // turn them on.
    // Lazily initialized - should call getWakeLock() to get the instance.
    private WakeLock mWakeLock;

    HdmiCecLocalDevicePlayback(HdmiControlService service) {
        super(service, HdmiDeviceInfo.DEVICE_PLAYBACK);
    }

    @Override
    @ServiceThreadOnly
    protected void onAddressAllocated(int logicalAddress, int reason) {
        assertRunOnServiceThread();
        mService.sendCecCommand(HdmiCecMessageBuilder.buildReportPhysicalAddressCommand(
                mAddress, mService.getPhysicalAddress(), mDeviceType));
        mService.sendCecCommand(HdmiCecMessageBuilder.buildDeviceVendorIdCommand(
                mAddress, mService.getVendorId()));
        startQueuedActions();
    }

    @Override
    @ServiceThreadOnly
    protected int getPreferredAddress() {
        assertRunOnServiceThread();
        return SystemProperties.getInt(Constants.PROPERTY_PREFERRED_ADDRESS_PLAYBACK,
                Constants.ADDR_UNREGISTERED);
    }

    @Override
    @ServiceThreadOnly
    protected void setPreferredAddress(int addr) {
        assertRunOnServiceThread();
        SystemProperties.set(Constants.PROPERTY_PREFERRED_ADDRESS_PLAYBACK,
                String.valueOf(addr));
    }

    @ServiceThreadOnly
    void oneTouchPlay(IHdmiControlCallback callback) {
        assertRunOnServiceThread();
        if (hasAction(OneTouchPlayAction.class)) {
            Slog.w(TAG, "oneTouchPlay already in progress");
            invokeCallback(callback, HdmiControlManager.RESULT_ALREADY_IN_PROGRESS);
            return;
        }

        // TODO: Consider the case of multiple TV sets. For now we always direct the command
        //       to the primary one.
        OneTouchPlayAction action = OneTouchPlayAction.create(this, Constants.ADDR_TV,
                callback);
        if (action == null) {
            Slog.w(TAG, "Cannot initiate oneTouchPlay");
            invokeCallback(callback, HdmiControlManager.RESULT_EXCEPTION);
            return;
        }
        addAndStartAction(action);
    }

    @ServiceThreadOnly
    void queryDisplayStatus(IHdmiControlCallback callback) {
        assertRunOnServiceThread();
        if (hasAction(DevicePowerStatusAction.class)) {
            Slog.w(TAG, "queryDisplayStatus already in progress");
            invokeCallback(callback, HdmiControlManager.RESULT_ALREADY_IN_PROGRESS);
            return;
        }
        DevicePowerStatusAction action = DevicePowerStatusAction.create(this,
                Constants.ADDR_TV, callback);
        if (action == null) {
            Slog.w(TAG, "Cannot initiate queryDisplayStatus");
            invokeCallback(callback, HdmiControlManager.RESULT_EXCEPTION);
            return;
        }
        addAndStartAction(action);
    }

    @ServiceThreadOnly
    private void invokeCallback(IHdmiControlCallback callback, int result) {
        assertRunOnServiceThread();
        try {
            callback.onComplete(result);
        } catch (RemoteException e) {
            Slog.e(TAG, "Invoking callback failed:" + e);
        }
    }

    @Override
    @ServiceThreadOnly
    void onHotplug(int portId, boolean connected) {
        assertRunOnServiceThread();
        mCecMessageCache.flushAll();
        // We'll not clear mIsActiveSource on the hotplug event to pass CETC 11.2.2-2 ~ 3.
        if (connected && mService.isPowerStandbyOrTransient()) {
            mService.wakeUp();
        }
        if (!connected) {
            getWakeLock().release();
        }
    }

    @ServiceThreadOnly
    void setActiveSource(boolean on) {
        assertRunOnServiceThread();
        mIsActiveSource = on;
        if (on) {
            getWakeLock().acquire();
            HdmiLogger.debug("active source: %b. Wake lock acquired", mIsActiveSource);
        } else {
            getWakeLock().release();
            HdmiLogger.debug("Wake lock released");
        }
    }

    @ServiceThreadOnly
    private WakeLock getWakeLock() {
        assertRunOnServiceThread();
        if (mWakeLock == null) {
            mWakeLock = mService.getPowerManager().newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
        }
        return mWakeLock;
    }

    @Override
    protected boolean canGoToStandby() {
        return !getWakeLock().isHeld();
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleActiveSource(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        mayResetActiveSource(physicalAddress);
        return true;  // Broadcast message.
    }

    private void mayResetActiveSource(int physicalAddress) {
        if (physicalAddress != mService.getPhysicalAddress()) {
            setActiveSource(false);
        }
    }

    @ServiceThreadOnly
    protected boolean handleUserControlPressed(HdmiCecMessage message) {
        assertRunOnServiceThread();
        wakeUpIfActiveSource();
        return super.handleUserControlPressed(message);
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleSetStreamPath(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        maySetActiveSource(physicalAddress);
        maySendActiveSource(message.getSource());
        wakeUpIfActiveSource();
        return true;  // Broadcast message.
    }

    // Samsung model we tested sends <Routing Change> and <Request Active Source>
    // in a row, and then changes the input to the internal source if there is no
    // <Active Source> in response. To handle this, we'll set ActiveSource aggressively.
    @Override
    @ServiceThreadOnly
    protected boolean handleRoutingChange(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int newPath = HdmiUtils.twoBytesToInt(message.getParams(), 2);
        maySetActiveSource(newPath);
        return true;  // Broadcast message.
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleRoutingInformation(HdmiCecMessage message) {
        assertRunOnServiceThread();
        int physicalAddress = HdmiUtils.twoBytesToInt(message.getParams());
        maySetActiveSource(physicalAddress);
        return true;  // Broadcast message.
    }

    private void maySetActiveSource(int physicalAddress) {
        setActiveSource(physicalAddress == mService.getPhysicalAddress());
    }

    private void wakeUpIfActiveSource() {
        if (!mIsActiveSource) {
            return;
        }
        // Wake up the device if the power is in standby mode, or its screen is off -
        // which can happen if the device is holding a partial lock.
        if (mService.isPowerStandbyOrTransient() || !mService.getPowerManager().isScreenOn()) {
            mService.wakeUp();
        }
    }

    private void maySendActiveSource(int dest) {
        if (mIsActiveSource) {
            mService.sendCecCommand(HdmiCecMessageBuilder.buildActiveSource(
                    mAddress, mService.getPhysicalAddress()));
            // Always reports menu-status active to receive RCP.
            mService.sendCecCommand(HdmiCecMessageBuilder.buildReportMenuStatus(
                    mAddress, dest, Constants.MENU_STATE_ACTIVATED));
        }
    }

    @Override
    @ServiceThreadOnly
    protected boolean handleRequestActiveSource(HdmiCecMessage message) {
        assertRunOnServiceThread();
        maySendActiveSource(message.getSource());
        return true;  // Broadcast message.
    }

    @Override
    @ServiceThreadOnly
    protected void disableDevice(boolean initiatedByCec, PendingActionClearedCallback callback) {
        super.disableDevice(initiatedByCec, callback);

        assertRunOnServiceThread();
        if (!initiatedByCec && mIsActiveSource) {
            mService.sendCecCommand(HdmiCecMessageBuilder.buildInactiveSource(
                    mAddress, mService.getPhysicalAddress()));
        }
        setActiveSource(false);
        checkIfPendingActionsCleared();
    }

    @Override
    protected void dump(final IndentingPrintWriter pw) {
        super.dump(pw);
        pw.println("mIsActiveSource: " + mIsActiveSource);
    }
}
