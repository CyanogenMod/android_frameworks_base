package com.mediatek.gba;

import android.net.Network;
import com.mediatek.gba.NafSessionKey;

/**
 * @hide
 */
interface IGbaService {
    int getGbaSupported();
    int getGbaSupportedForSubscriber(in int subId);
    boolean isGbaKeyExpired(String nafFqdn, in byte[] nafSecurProtocolId);
    boolean isGbaKeyExpiredForSubscriber(String nafFqdn, in byte[] nafSecurProtocolId, in int subId);
    NafSessionKey runGbaAuthentication(in String nafFqdn, in byte[] nafSecurProtocolId, boolean forceRun);
    NafSessionKey runGbaAuthenticationForSubscriber(in String nafFqdn, in byte[] nafSecurProtocolId, boolean forceRun, in int subId);
    void setNetwork(in Network network);
}