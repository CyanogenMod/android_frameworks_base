/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.HashMap;

/** Quick settings tile: Apn switch **/
public class ApnTile extends QSTile<QSTile.State> {
    private final boolean DEBUG = false;
    private final String TAG = "ApnTile";

    private boolean mListening;
    private static final Uri URI_PHONE_FEATURE =
            Uri.parse("content://com.qualcomm.qti.phonefeature.FEATURE_PROVIDER");
    private static final String METHOD_SET_NEXT_APN_AS_PREF = "set_next_apn_as_pref";
    private static final String EXTRA_CURRENT_APN = "current_apn";
    private final Uri PREFERAPN_URI = Uri.parse("content://telephony/carriers/preferapn");
    private final String[] PROJECTION = new String[] {"_id", "name", "apn", "type"};
    private final int INDEX_ID = 0;
    private final int INDEX_NAME = 1;
    private final int INDEX_APN = 2;
    private final int INDEX_TYPE = 3;
    private ApnObserver mApnObserver = null;
    private Apn mCurrentApn = null;
    private ArrayList<Integer> mApnIcons = null;
    private Integer mCurrentIcon = null;
    private String mCurrentApnName = "";

    public ApnTile(Host host) {
        super(host);

        mApnIcons = new ArrayList<Integer>();
        mApnIcons.add(R.drawable.ic_qs_apn1);
        mApnIcons.add(R.drawable.ic_qs_apn2);
        mCurrentIcon = mApnIcons.get(0);
        mCurrentApn = new Apn();
        mApnObserver = new ApnObserver(mHandler);
    }

    @Override
    protected State newTileState() {
        return new State();
    }

    @Override
    public void handleClick() {
        long curId = 0;
        try {
            curId = Long.parseLong(getCurrentApnId());
        } catch (Exception e) {
        }
        if (DEBUG) Log.i(TAG, "Current apn id is: " + curId);
        setPrefApn(curId);
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (DEBUG) Log.i(TAG, "handleUpdateState");

        // Hide APN switch button if DDS is set to non default subscription
        int dataPhoneId = PhoneConstants.PHONE_ID1;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            dataPhoneId = (int) SubscriptionManager.getDefaultDataSubId();
        }
        if (dataPhoneId != PhoneConstants.PHONE_ID1) {
            state.visible = false;
        } else {
            state.visible = hasIccCard() && !isAirplaneModeOn();
        }

        String apnName = getCurrentApnName();
        if (!apnName.isEmpty() && !apnName.equals(mCurrentApnName)) {
            updateApnIcon();
            mCurrentApnName = apnName;
        }
        state.iconId = mCurrentIcon;
        state.label = apnName.toUpperCase();
        state.contentDescription = apnName;
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mApnObserver.startObserving();
        } else {
            mApnObserver.endObserving();
        }
    }

    private Bundle callBinder(String method, Bundle extras) {
        if (!isPhoneFeatureEnabled()) {
            return null;
        }
        return mContext.getContentResolver().call(URI_PHONE_FEATURE, method, null, extras);
    }

    public boolean isPhoneFeatureEnabled() {
        return mContext.getContentResolver().acquireProvider(URI_PHONE_FEATURE) != null;
    }

    /**
    * set preferred apn according to the current apn
    *
    * @param curId Current apn id
    */
    public void setPrefApn(long curId) {
        Bundle params = new Bundle();
        params.putLong(EXTRA_CURRENT_APN, curId);
        callBinder(METHOD_SET_NEXT_APN_AS_PREF, params);
    }

    private void updateCurrentApn() {
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(PREFERAPN_URI, PROJECTION,
                    null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                mCurrentApn.id = cursor.getString(INDEX_ID);
                mCurrentApn.name = cursor.getString(INDEX_NAME);
                mCurrentApn.apn = cursor.getString(INDEX_APN);
                mCurrentApn.type = cursor.getString(INDEX_TYPE);
                if (DEBUG) {
                    Log.i(TAG, "updateCurrentApn: " + "id= " + mCurrentApn.id
                            + " name= " + mCurrentApn.name
                            + " apn= " + mCurrentApn.apn
                            + " type= " + mCurrentApn.type);
                }
            }
        } catch(Exception e) {
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
    }

    private void updateApnIcon() {
        Integer icon = null;

        int iconId = mApnIcons.indexOf(mCurrentIcon);
        icon = mApnIcons.get((iconId + 1) % mApnIcons.size());

        if (icon != null) {
            mCurrentIcon = icon;
        }
    }

    public String getCurrentApnId() {
        String id = "";
        if (mCurrentApn != null && mCurrentApn.id != null) {
            id = mCurrentApn.id;
        }
        return id;
    }

    public String getCurrentApnName() {
        String apn = "";
        if (mCurrentApn != null && mCurrentApn.apn != null) {
            apn = mCurrentApn.apn;
        }
        return apn;
    }

    public boolean hasIccCard() {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            int prfDataPhoneId = SubscriptionManager.getPhoneId(
                    SubscriptionManager.getDefaultDataSubId());
            int simState = tm.getSimState(prfDataPhoneId);
            boolean active = (simState != TelephonyManager.SIM_STATE_ABSENT)
                    && (simState != TelephonyManager.SIM_STATE_UNKNOWN);
            return active && tm.hasIccCard(prfDataPhoneId);
        } else {
            TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            return tm.hasIccCard();
        }
    }

    public boolean isAirplaneModeOn() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0);
    }

    /** ContentObserver to watch apn switch **/
    private class ApnObserver extends ContentObserver {
        public ApnObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (DEBUG) Log.i(TAG, "preferred apn changed.");
            updateCurrentApn();
            refreshState();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(PREFERAPN_URI, true, this);
            cr.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION),
                    false, this);
            cr.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.AIRPLANE_MODE_ON),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    };

    public static final class Apn {
        String id;
        String name;
        String apn;
        String type;
    }
}
