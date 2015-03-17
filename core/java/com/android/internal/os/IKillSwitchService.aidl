package com.android.internal.os;

/**
 * @hide
 */
interface IKillSwitchService {
    void setDeviceUuid(String uuid);
    String getDeviceUuid();

    boolean isDeviceLocked();
    void setDeviceLocked(boolean locked);

    void setAccountId(String value);
    String getAccountId();
}
