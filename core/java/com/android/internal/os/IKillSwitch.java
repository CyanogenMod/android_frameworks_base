package com.android.internal.os;

/**
 * @hide
 */
public interface IKillSwitch {
    public void setDeviceUuid(String uuid);
    public String getDeviceUuid();

    public boolean isDeviceLocked();
    public void setDeviceLocked(boolean locked);

    public void setAccountId(String value);
    public String getAccountId();
}
