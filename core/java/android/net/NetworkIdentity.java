/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2011 The Android Open Source Project
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

package android.net;

import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.ConnectivityManager.getNetworkTypeName;
import static android.net.ConnectivityManager.isNetworkTypeMobile;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import com.android.internal.util.Objects;

/**
 * Network definition that includes strong identity. Analogous to combining
 * {@link NetworkInfo} and an IMSI.
 *
 * @hide
 */
public class NetworkIdentity {
    /**
     * When enabled, combine all {@link #mSubType} together under
     * {@link #SUBTYPE_COMBINED}.
     */
    public static final boolean COMBINE_SUBTYPE_ENABLED = true;

    public static final int SUBTYPE_COMBINED = -1;

    final int mType;
    final int mSubType;
    final String mSubscriberId;
    final String mNetworkId;
    final boolean mRoaming;

    public NetworkIdentity(
            int type, int subType, String subscriberId, String networkId, boolean roaming) {
        mType = type;
        mSubType = COMBINE_SUBTYPE_ENABLED ? SUBTYPE_COMBINED : subType;
        mSubscriberId = subscriberId;
        mNetworkId = networkId;
        mRoaming = roaming;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mType, mSubType, mSubscriberId, mNetworkId, mRoaming);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NetworkIdentity) {
            final NetworkIdentity ident = (NetworkIdentity) obj;
            return mType == ident.mType && mSubType == ident.mSubType && mRoaming == ident.mRoaming
                    && Objects.equal(mSubscriberId, ident.mSubscriberId)
                    && Objects.equal(mNetworkId, ident.mNetworkId);
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("[");
        builder.append("type=").append(getNetworkTypeName(mType));
        builder.append(", subType=");
        if (COMBINE_SUBTYPE_ENABLED) {
            builder.append("COMBINED");
        } else if (ConnectivityManager.isNetworkTypeMobile(mType)) {
            builder.append(TelephonyManager.getNetworkTypeName(mSubType));
        } else {
            builder.append(mSubType);
        }
        if (mSubscriberId != null) {
            builder.append(", subscriberId=").append(scrubSubscriberId(mSubscriberId));
        }
        if (mNetworkId != null) {
            builder.append(", networkId=").append(mNetworkId);
        }
        if (mRoaming) {
            builder.append(", ROAMING");
        }
        return builder.append("]").toString();
    }

    public int getType() {
        return mType;
    }

    public int getSubType() {
        return mSubType;
    }

    public String getSubscriberId() {
        return mSubscriberId;
    }

    public String getNetworkId() {
        return mNetworkId;
    }

    public boolean getRoaming() {
        return mRoaming;
    }

    /**
     * Scrub given IMSI on production builds.
     */
    public static String scrubSubscriberId(String subscriberId) {
        if ("eng".equals(Build.TYPE)) {
            return subscriberId;
        } else if (subscriberId != null) {
            // TODO: parse this as MCC+MNC instead of hard-coding
            return subscriberId.substring(0, Math.min(6, subscriberId.length())) + "...";
        } else {
            return "null";
        }
    }

    /**
     * Build a {@link NetworkIdentity} from the given {@link NetworkState},
     * assuming that any mobile networks are using the current IMSI.
     */
    public static NetworkIdentity buildNetworkIdentity(Context context, NetworkState state) {
        final int type = state.networkInfo.getType();
        final int subType = state.networkInfo.getSubtype();

        // TODO: consider moving subscriberId over to LinkCapabilities, so it
        // comes from an authoritative source.

        String subscriberId = null;
        String networkId = null;
        boolean roaming = false;

        if (isNetworkTypeMobile(type)) {
            roaming = isDdsRoaming();
            if (state.subscriberId != null) {
                subscriberId = state.subscriberId;
            } else {
                //used for dual sim data traffic statistics
                subscriberId = getDdsSubscriberId();
            }

        } else if (type == TYPE_WIFI) {
            if (state.networkId != null) {
                networkId = state.networkId;
            } else {
                final WifiManager wifi = (WifiManager) context.getSystemService(
                        Context.WIFI_SERVICE);
                final WifiInfo info = wifi.getConnectionInfo();
                networkId = info != null ? info.getSSID() : null;
            }
        }

        return new NetworkIdentity(type, subType, subscriberId, networkId, roaming);
    }

    private static boolean isDdsRoaming() {
        MSimTelephonyManager mtm = MSimTelephonyManager.getDefault();
        TelephonyManager tm = TelephonyManager.getDefault();
        if (mtm.isMultiSimEnabled()) {
            return mtm.isNetworkRoaming(mtm.getPreferredDataSubscription());
        } else {
            return tm.isNetworkRoaming();
        }
    }

    public static String getDdsSubscriberId() {
        MSimTelephonyManager mtm = MSimTelephonyManager.getDefault();
        TelephonyManager tm = TelephonyManager.getDefault();
        if (mtm.isMultiSimEnabled()) {
            return mtm.getSubscriberId(mtm.getPreferredDataSubscription());
        } else {
            return tm.getSubscriberId();
        }
    }

    public static boolean isDdsReady() {
        MSimTelephonyManager mtm = MSimTelephonyManager.getDefault();
        TelephonyManager tm = TelephonyManager.getDefault();
        if (mtm.isMultiSimEnabled()) {
            return mtm.getSimState(mtm.getPreferredDataSubscription())
                    == TelephonyManager.SIM_STATE_READY;
        } else {
            return tm.getSimState() == TelephonyManager.SIM_STATE_READY;
        }
    }
}
