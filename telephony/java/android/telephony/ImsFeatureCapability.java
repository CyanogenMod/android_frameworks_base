/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.telephony;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.Rlog;
import android.util.SparseIntArray;

/**
 * Contains IMS feature capability related information.
 *
 * The following IMS feature capability information is included in returned ImsFeatureCapability:
 *
 * <ul>
 *   <li>IMS VoLTE enabled state
 *   <li>IMS ViLTE enabled state
 *   <li>IMS VoWiFi enabled state
 *   <li>IMS ViWiFi enabled state
 *   <li>IMS UtLTE enabled state
 *   <li>IMS UtWiFi enabled state
 * </ul>
 *
 * @see com.android.ims.ImsConnectionStateListener#onFeatureCapabilityChanged(int, int[], int[])
 * @see com.android.internal.telephony.imsphone.ImsPhoneCallTracker#mImsConnectionStateListener
 *
 * @hide
 */
public class ImsFeatureCapability implements Parcelable {

    public static final int FEATURE_TYPE_UNKNOWN = -1;

    /**
     * FEATURE_TYPE_VOLTE supports features defined in 3GPP and
     * GSMA IR.92 over LTE.
     */
    public static final int FEATURE_TYPE_VOICE_OVER_LTE = 0;

    /**
     * FEATURE_TYPE_LVC supports features defined in 3GPP and
     * GSMA IR.94 over LTE.
     */
    public static final int FEATURE_TYPE_VIDEO_OVER_LTE = 1;

    /**
     * FEATURE_TYPE_VOICE_OVER_WIFI supports features defined in 3GPP and
     * GSMA IR.92 over WiFi.
     */
    public static final int FEATURE_TYPE_VOICE_OVER_WIFI = 2;

    /**
     * FEATURE_TYPE_VIDEO_OVER_WIFI supports features defined in 3GPP and
     * GSMA IR.94 over WiFi.
     */
    public static final int FEATURE_TYPE_VIDEO_OVER_WIFI = 3;

    /**
     * FEATURE_TYPE_UT supports features defined in 3GPP and
     * GSMA IR.92 over LTE.
     */
    public static final int FEATURE_TYPE_UT_OVER_LTE = 4;

    /**
     * FEATURE_TYPE_UT_OVER_WIFI supports features defined in 3GPP and
     * GSMA IR.92 over WiFi.
     */
    public static final int FEATURE_TYPE_UT_OVER_WIFI = 5;

    public static final int FEATURE_TYPE_LENGTH = FEATURE_TYPE_UT_OVER_WIFI + 1;

    /**
     * Indicates whether IMS feature VoLTE is enabled
     */
    public static final int CAPABILITY_VOLTE = 0x00000001;

    /**
     * Indicates whether IMS feature ViLTE is enabled
     */
    public static final int CAPABILITY_VILTE = 0x00000002;

    /**
     * Indicates whether IMS feature VoWiFi is enabled
     */
    public static final int CAPABILITY_VOWIFI = 0x00000004;

    /**
     * Indicates whether IMS feature ViWiFi is enabled
     */
    public static final int CAPABILITY_VIWIFI = 0x00000008;

    /**
     * Indicates whether IMS feature UtLTE is enabled
     */
    public static final int CAPABILITY_UTLTE = 0x00000010;

    /**
     * Indicates whether IMS feature UtWiFi is enabled
     */
    public static final int CAPABILITY_UTWIFI = 0x00000020;

    /**
     * The IMS feature type and IMS feature capability mapping
     */
    private static final SparseIntArray IMS_CAPABILITY_MAP = new SparseIntArray();
    static {
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_VOICE_OVER_LTE, CAPABILITY_VOLTE);
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_VIDEO_OVER_LTE, CAPABILITY_VILTE);
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_VOICE_OVER_WIFI, CAPABILITY_VOWIFI);
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_VIDEO_OVER_WIFI, CAPABILITY_VIWIFI);
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_UT_OVER_LTE, CAPABILITY_UTLTE);
        IMS_CAPABILITY_MAP.put(FEATURE_TYPE_UT_OVER_WIFI, CAPABILITY_UTWIFI);
    }

    /**
     * Indicates current IMS feature capabilities
     */
    private int mImsFeatureCapabilities;

    /**
     * Empty constructor
     */
    public ImsFeatureCapability() {
    }

    /**
     * Create a new ImsFeatureCapability from a IMS features settings arrary.
     *
     * This method is used by ImsPhoneCallTracker and maybe by
     * external applications.
     *
     * @param imsFeatureEnabled IMS features settings arrary
     * @return newly created ImsFeatureCapability
     */
    public static ImsFeatureCapability newFromBoolArrary(boolean[] imsFeatureEnabled) {
        ImsFeatureCapability ret;
        ret = new ImsFeatureCapability();
        ret.setFromBoolArrary(imsFeatureEnabled);
        return ret;
    }

    /**
     * Create a new IMS features settings arrary from a ImsFeatureCapability.
     *
     * @param imsFeatureCapability The ImsFeatureCapability to be converted
     * @return newly created IMS features settings arrary
     */
    public static boolean[] convertToBoolArrary(
            ImsFeatureCapability imsFeatureCapability) {
        boolean[] ret;
        ret = new boolean[FEATURE_TYPE_LENGTH];
        if (imsFeatureCapability != null) {
            imsFeatureCapability.fillInBoolArrary(ret);
        }
        return ret;
    }

    /**
     * Copy constructors
     *
     * @param i Source Ims feature capability
     */
    public ImsFeatureCapability(ImsFeatureCapability i) {
        copyFrom(i);
    }

    /**
     * Copy Ims feature capability
     *
     * @param i Source Ims feature capability
     */
    public void copyFrom(ImsFeatureCapability i) {
        mImsFeatureCapabilities = i.mImsFeatureCapabilities;
    }

    /**
     * Construct a ImsFeatureCapability object from the given parcel.
     */
    public ImsFeatureCapability(Parcel in) {
        mImsFeatureCapabilities = in.readInt();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mImsFeatureCapabilities);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ImsFeatureCapability> CREATOR =
            new Parcelable.Creator<ImsFeatureCapability>() {
        public ImsFeatureCapability createFromParcel(Parcel in) {
            return new ImsFeatureCapability(in);
        }

        public ImsFeatureCapability[] newArray(int size) {
            return new ImsFeatureCapability[size];
        }
    };

    /**
     * Sets the ImsFeatureCapability's capabilities as a bit mask of
     * the {@code CAPABILITY_*} constants.
     *
     * @param imsFeatureCapabilities The new IMS feature capabilities.
     */
    public void setImsFeatureCapabilities(int imsFeatureCapabilities) {
        this.mImsFeatureCapabilities = imsFeatureCapabilities;
    }

    /**
     * Returns the ImsFeatureCapability's capabilities, as a bit mask of
     * the {@code CAPABILITY_*} constants.
     */
    public int getImsFeatureCapabilities() {
        return mImsFeatureCapabilities;
    }

    /**
     * Whether the given capabilities support the specified capability.
     *
     * @param capabilities A capability bit field.
     * @param capability The capability to check capabilities for.
     * @return Whether the specified capability is supported.
     */
    public static boolean can(int capabilities, int capability) {
        return (capabilities & capability) != 0;
    }

    /**
     * Whether the capabilities of this {@code ImsFeatureCapability}
     * supports the specified capability.
     *
     * @param capability The capability to check capabilities for.
     * @return Whether the specified capability is supported.
     */
    public boolean can(int capability) {
        return can(mImsFeatureCapabilities, capability);
    }

    /**
     * Adds the specified capability to the set of capabilities of
     * this {@code ImsFeatureCapability}.
     *
     * @param capability The capability to add to the set.
     */
    public void addCapability(int capability) {
        mImsFeatureCapabilities |= capability;
    }

    /**
     * Removes the specified capability from the set of capabilities of
     * this {@code ImsFeatureCapability}.
     *
     * @param capability The capability to remove from the set.
     */
    public void removeCapability(int capability) {
        mImsFeatureCapabilities &= ~capability;
    }

    /**
     * Changes a capabilities bit-mask to add or remove a capability.
     *
     * @param capabilities The capabilities bit-mask.
     * @param capability The capability to change.
     * @param enabled Whether the capability should be set or removed.
     * @return The capabilities bit-mask with the capability changed.
     */
    public static int changeCapability(int maskCapabilities, int capability, boolean enabled) {
        if (enabled) {
            return maskCapabilities | capability;
        } else {
            return maskCapabilities & ~capability;
        }
    }

    /**
     * Changes the specified capability from the set of capabilities of
     * this {@code ImsFeatureCapability}.
     *
     * @param capability The capability to change.
     * @param enabled Whether the capability should be set or removed.
     * @return The set of changed capabilities of this {@code ImsFeatureCapability}.
     */
    public int changeCapability(int capability, boolean enabled) {
        mImsFeatureCapabilities = changeCapability(mImsFeatureCapabilities, capability, enabled);
        return mImsFeatureCapabilities;
    }

    @Override
    public boolean equals (Object o) {
        if (o == null) {
            return false;
        }

        ImsFeatureCapability i;
        try {
            i = (ImsFeatureCapability) o;
        } catch (ClassCastException ex) {
            return false;
        }
        return mImsFeatureCapabilities == i.mImsFeatureCapabilities;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[ImsFeatureCapability capabilities:");
        if (can(CAPABILITY_VOLTE)) {
            builder.append(" CAPABILITY_VOLTE");
        }
        if (can(CAPABILITY_VILTE)) {
            builder.append(" CAPABILITY_VILTE");
        }
        if (can(CAPABILITY_VOWIFI)) {
            builder.append(" CAPABILITY_VOWIFI");
        }
        if (can(CAPABILITY_VIWIFI)) {
            builder.append(" CAPABILITY_VIWIFI");
        }
        if (can(CAPABILITY_UTLTE)) {
            builder.append(" CAPABILITY_UTLTE");
        }
        if (can(CAPABILITY_UTWIFI)) {
            builder.append(" CAPABILITY_UTWIFI");
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * Set ImsFeatureCapability based on the IMS features settings arrary.
     * The length of the boolean arrary must be FEATURE_TYPE_LENGTH
     *
     * @param imsFeatureEnabled IMS features settings arrary
     */
    public void setFromBoolArrary(boolean[] imsFeatureEnabled) {
        if (imsFeatureEnabled == null || imsFeatureEnabled.length != FEATURE_TYPE_LENGTH) {
            return;
        }
        for (int i = FEATURE_TYPE_VOICE_OVER_LTE; i <= FEATURE_TYPE_UT_OVER_WIFI; i++) {
            changeCapability(IMS_CAPABILITY_MAP.get(i), imsFeatureEnabled[i]);
        }
    }

    /**
     * Set IMS features settings arrary based on ImsFeatureCapability.
     * The length of the boolean arrary must be FEATURE_TYPE_LENGTH
     *
     * @param imsFeatureEnabled IMS features settings arrary
     */
    public void fillInBoolArrary(boolean[] imsFeatureEnabled) {
        if (imsFeatureEnabled == null || imsFeatureEnabled.length != FEATURE_TYPE_LENGTH) {
            return;
        }
        for (int i = FEATURE_TYPE_VOICE_OVER_LTE; i <= FEATURE_TYPE_UT_OVER_WIFI; i++) {
            imsFeatureEnabled[i] = can(IMS_CAPABILITY_MAP.get(i));
        }
    }

    /**
     * Set ImsFeatureCapability based on intent notifier map.
     *
     * @param m intent notifier map
     */
    private void setFromNotifierBundle(Bundle m) {
        changeCapability(CAPABILITY_VOLTE, m.getBoolean("voLTE"));
        changeCapability(CAPABILITY_VILTE, m.getBoolean("viLTE"));
        changeCapability(CAPABILITY_VOWIFI, m.getBoolean("voWiFi"));
        changeCapability(CAPABILITY_VIWIFI, m.getBoolean("viWiFi"));
        changeCapability(CAPABILITY_UTLTE, m.getBoolean("utLTE"));
        changeCapability(CAPABILITY_UTWIFI, m.getBoolean("utWiFi"));
    }

    /**
     * Set intent notifier Bundle based on ImsFeatureCapability.
     *
     * @param m intent notifier Bundle
     */
    public void fillInNotifierBundle(Bundle m) {
        m.putBoolean("voLTE", can(CAPABILITY_VOLTE));
        m.putBoolean("viLTE", can(CAPABILITY_VILTE));
        m.putBoolean("voWiFi", can(CAPABILITY_VOWIFI));
        m.putBoolean("viWiFi", can(CAPABILITY_VIWIFI));
        m.putBoolean("utLTE", can(CAPABILITY_UTLTE));
        m.putBoolean("utWiFi", can(CAPABILITY_UTWIFI));
    }

    /**
     * Get VoLTE enabled state
     * Similar with
     * {@link com.android.internal.telephony.imsphone.ImsPhoneCallTracker#isVolteEnabled}
     *
     * @return {@code True} if VoLTE is enabled, {@code false} otherwise
     */
    public boolean isVolteEnabled() {
        return can(CAPABILITY_VOLTE);
    }

    /**
     * Get ViLTE enabled state
     *
     * @return {@code True} if ViLTE is enabled, {@code false} otherwise
     */
    public boolean isViLTEEnabled() {
        return can(CAPABILITY_VILTE);
    }

    /**
     * Get VoWiFi enabled state
     * Similar with
     * {@link com.android.internal.telephony.imsphone.ImsPhoneCallTracker#isVowifiEnabled}
     *
     * @return {@code True} if VoWiFi is enabled, {@code false} otherwise
     */
    public boolean isVowifiEnabled() {
        return can(CAPABILITY_VOWIFI);
    }

    /**
     * Get ViWiFi enabled state
     *
     * @return {@code True} if ViWiFi is enabled, {@code false} otherwise
     */
    public boolean isViWiFiEnabled() {
        return can(CAPABILITY_VIWIFI);
    }

    /**
     * Get UtLTE enabled state
     *
     * @return {@code True} if UtLTE is enabled, {@code false} otherwise
     */
    public boolean isUtLTEEnabled() {
        return can(CAPABILITY_UTLTE);
    }

    /**
     * Get UtWiFi enabled state
     *
     * @return {@code True} if UtWiFi is enabled, {@code false} otherwise
     */
    public boolean isUtWiFiEnabled() {
        return can(CAPABILITY_UTWIFI);
    }

    /**
     * Get Video call enabled state
     * Similar with
     * {@link com.android.internal.telephony.imsphone.ImsPhoneCallTracker#isVideoCallEnabled}
     *
     * @return {@code True} if Video call is enabled, {@code false} otherwise
     */
    public boolean isVideoCallEnabled() {
        return isViLTEEnabled() || isViWiFiEnabled();
    }

    /**
     * Get Ut enabled state
     * Similar with
     * {@link com.android.internal.telephony.imsphone.ImsPhoneCallTracker#isUtEnabled}
     *
     * @return {@code True} if Ut is enabled, {@code false} otherwise
     */
    public boolean isUtEnabled() {
        return isUtLTEEnabled() || isUtWiFiEnabled();
    }
}
