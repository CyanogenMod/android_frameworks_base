package com.mediatek.gba;

import android.content.Context;
import android.net.Network;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

/**
 * HTTP Authenticator for GBA procedure.
 * It is based class.
 *
 * @hide
 */
public abstract class GbaBaseCredential {
    private final static String TAG = "GbaBaseCredential";

    static final byte[] DEFAULT_UA_SECURITY_PROTOCOL_ID_HTTP =
        new byte[] {(byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02};
    static final byte[] DEFAULT_UA_SECURITY_PROTOCOL_ID_TLS =
        new byte[] {(byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x2F};
    final protected static char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    protected static Context sContext;
    protected static Network sNetwork;
    protected static int sSubId;
    protected static boolean sIsTlsEnabled;
    protected static boolean sCachedSessionKeyUsed;
    protected static String sPasswd;
    protected static String sNafAddress;
    protected static IGbaService sService;

    /**
      * Empty construciton function.
      *
      */
    GbaBaseCredential() {

    }

    /**
      * Construciton function with initalization parameters.
      *
      */
    GbaBaseCredential(Context context, String nafAddress, int subId) {
        super();
        sContext = context;
        sSubId = subId;

        if (nafAddress.charAt(nafAddress.length() - 1) == '/') {
            nafAddress = nafAddress.substring(0, nafAddress.length() - 1);
        }

        sIsTlsEnabled = true;
        sCachedSessionKeyUsed = false;
        sNafAddress = nafAddress.toLowerCase();

        if (sNafAddress.indexOf("http://") != -1) {
            sNafAddress = nafAddress.substring(7);
            sIsTlsEnabled = false;
        } else if (sNafAddress.indexOf("https://") != -1) {
            sNafAddress = nafAddress.substring(8);
            sIsTlsEnabled = true;
        }

        Log.d(TAG, "nafAddress:" + sNafAddress);
    }

    /**
      * Tell GbaCredential the connection is TLS or not.
      *
      * @param tlsEnabled indicate the connection is over TLS or not.
      *
      */
    public void setTlsEnabled(boolean tlsEnabled) {
        sIsTlsEnabled = tlsEnabled;
    }

    /**
      * Configure which subscription to use in GBA procedure.
      *
      * @param subId indicate the subscription id.
      *
      */
    public void setSubId(int subId) {
        sSubId = subId;
    }

    /**
      * Configure dedicated network.
      *
      * @param network network that will be used to establish socket connection.
      *
      */
    public void setNetwork(Network network) {
        if (network != null) {
            Log.i(TAG, "GBA dedicated network netid:" + network);
            sNetwork = network;
        }
    }

    /**
      * Get session key for NAF server by GBA procedure.
      *
      @return NafSessionKey: the session key of NAF server.
      */
    public static NafSessionKey getNafSessionKey() {
        NafSessionKey nafSessionKey = null;

        try {
            IBinder b = ServiceManager.getService("GbaService");

            if (b == null) {
                Log.i("debug", "The binder is null");
                return null;
            }

            sService = IGbaService.Stub.asInterface(b);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        try {
            byte[] uaId = DEFAULT_UA_SECURITY_PROTOCOL_ID_TLS;

            if (sIsTlsEnabled) {
                String gbaStr = System.getProperty("gba.ciper.suite", "");

                if (gbaStr.length() > 0) {
                    GbaCipherSuite cipherSuite = GbaCipherSuite.getByName(gbaStr);

                    if (cipherSuite != null) {
                        byte[] cipherSuiteCode = cipherSuite.getCode();
                        uaId[3] = cipherSuiteCode[0];
                        uaId[4] = cipherSuiteCode[1];
                    }
                }
            } else {
                uaId = DEFAULT_UA_SECURITY_PROTOCOL_ID_HTTP;
            }

            if (sNetwork != null) {
                sService.setNetwork(sNetwork);
            }

            String realm = System.getProperty("digest.realm", "");
            Log.i(TAG, "realm:" + realm);
            if (realm.length() > 0) {
                String[] segments = realm.split(";");
                sNafAddress = segments[0].substring(segments[0].indexOf("@") + 1);
                Log.i(TAG, "NAF FQDN:" + sNafAddress);
            } else {
                return null;
            }

            if (SubscriptionManager.INVALID_SUBSCRIPTION_ID == sSubId) {
                nafSessionKey = sService.runGbaAuthentication(sNafAddress,
                                uaId, sCachedSessionKeyUsed);
            } else {
                nafSessionKey = sService.runGbaAuthenticationForSubscriber(sNafAddress,
                                uaId, sCachedSessionKeyUsed, sSubId);
            }

            if (nafSessionKey != null && (nafSessionKey.getException() != null) &&
                    (nafSessionKey.getException() instanceof IllegalStateException)) {
                String msg = ((IllegalStateException) nafSessionKey.getException())
                        .getMessage();

                if ("HTTP 403 Forbidden".equals(msg)) {
                    Log.i(TAG, "GBA hit 403");
                    System.setProperty("gba.auth", "403");
                }
            }
        } catch (RemoteException re) {
            re.printStackTrace();
        }

        return nafSessionKey;
    }
}