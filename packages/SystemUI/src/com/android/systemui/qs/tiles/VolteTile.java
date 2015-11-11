/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings.Global;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.ims.ImsException;
import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: VoLTE mode **/
public class VolteTile extends QSTile<QSTile.BooleanState> {
    private final Icon mEnable = ResourceIcon.get(R.drawable.ic_volte_enable);
    private final Icon mDisable = ResourceIcon.get(R.drawable.ic_volte_disable);

    private static final Intent VOLTE_SETTINGS =
        new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);

    private final GlobalSetting mSetting;
    private boolean mListening;
    private PhoneStateListener mPhoneStateListener = null;

    public VolteTile(Host host) {
        super(host);

        mSetting = new GlobalSetting(mContext, mHandler, Global.ENHANCED_4G_MODE_ENABLED) {
            @Override
            protected void handleValueChanged(int value) {
                handleRefreshState(value);
            }
        };
    }

    /**
     * Whether or not showing this on the lock screen would expose any
     * user-sensitive data.
     */
    @Override
    public boolean hasSensitiveData() {
        return false;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        // If we're in a call, do nothing.
        if (getTelephonyManager().getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            return;
        }
        setEnabled(!mState.value && isVolteAvailable());
    }

    @Override
    public void handleLongClick() {
        mHost.startSettingsActivity(VOLTE_SETTINGS);
    }

    private void setEnabled(boolean enabled) {
        setGlobal(enabled);
        setVolte(enabled);
    }

    /**
     * Write VoLTE state to global settings.
     */
    private void setGlobal(boolean enabled) {
        int value = (enabled) ? 1:0;
        android.provider.Settings.Global.putInt(
                  mContext.getContentResolver(),
                  android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED, value);
    }

    /**
     * React to external changes to the global state this tile manages.
     * This method is also called on initial start-up. It's responsible
     * for updating the display of the tile and possibly making it
     * invisible entirely as appropriate.
     */
    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = isVolteAvailable();
        state.value = isVolteInUse();;
        state.label = mContext.getString(R.string.quick_settings_volte_mode_label);
        if (state.value) {
            state.icon = mEnable;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_volte_enabled);
        } else {
            state.icon = mDisable;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_volte_disabled);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_volte_changed_enabled);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_volte_changed_disabled);
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        trackVolteState(listening);
        mSetting.setListening(listening);
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private TelecomManager getTelecomManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    //==============================================================

    /*
     * Programming note: everything below this point encapsulates the
     * os-dependent VoLTE interface. The IMS and VoLTE classes used
     * here are not part of the published API and could change in the
     * future. If this interface changes, the changes should all be here.
     */

    private boolean isVolteAvailable() {
        return ImsManager.isVolteEnabledByPlatform(mContext) &&
               ImsManager.isVolteProvisionedOnDevice(mContext);
    }

    private boolean isVolteInUse() {
        return isVolteAvailable() &&
               ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext);
    }

    /**
     * Write VoLTE state to IMS manager.
     */
    private void setVolte(boolean enabled) {
        ImsManager imsMan = ImsManager.getInstance(mContext,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (imsMan != null) {
            try {
                imsMan.setAdvanced4GMode(enabled);
            } catch (ImsException ie) {
                // do nothing
            }
        }
    }

    /**
     * Set up or remove a listener for changes in VoLTE. Client should call
     * this any time the call method changes.
     */
    private void trackVolteState(boolean listening) {
        int slotId = getDefaultSimSlot();
        TelephonyManager mgr = getTelephonyManager();
        if (mPhoneStateListener != null) {
            mgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mPhoneStateListener = null;
        }
        if (!listening || !isVolteAvailable()) return;
        SubscriptionManager smgr = SubscriptionManager.from(mContext);
        android.telephony.SubscriptionInfo info =
            smgr.getActiveSubscriptionInfoForSimSlotIndex(slotId);
        if (info == null) {
            return;
        }
        mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId());
        int eventMask = PhoneStateListener.LISTEN_VOLTE_STATE;
        mgr.listen(mPhoneStateListener, eventMask);
    }

    private class MobilePhoneStateListener extends PhoneStateListener {
        public MobilePhoneStateListener(int subId) {
            super(subId);
        }
        @Override
        public void onVoLteServiceStateChanged(VoLteServiceState stateInfo) {
            refreshState();
        }
    }

    /**
     * Return the sim slot ID that corresponds to the default sim for
     * outgoing calls. This could change, so don't cache the output of this
     * function.
     */
    private int getDefaultSimSlot() {
        TelecomManager mgr = getTelecomManager();
        PhoneAccountHandle handle =
            mgr.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        return SubscriptionManager.getSlotId(Integer.parseInt(handle.getId()));
    }
}
