package com.mediatek.gba;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.util.Base64;
import android.util.Log;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * HTTP Authenticator for GBA procedure.
 * This is designed for HttpUrlConnection.
 *
 * @hide
 */
public class GbaHttpUrlCredential extends GbaBaseCredential {
    private final static String TAG = "GbaCredentials";

    private Authenticator mAuthenticator = new GbaAuthenticator();

    /**
      * Construction function for GbaCredentials.
      *
      * @param context the application context.
      * @param nafAddress the sceme name + FQDN value of NAF server address.
      * e.g. https://www.google.com or http://www.google.com
      *
      * @hide
      */
    public GbaHttpUrlCredential(Context context, String nafAddress) {
        this(context, nafAddress, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    /**
      * Construction function for GbaCredentials.
      *
      * @param context the application context.
      * @param nafAddress the sceme name + FQDN value of NAF server address.
      * @param subId the subscription id.
      * e.g. https://www.google.com or http://www.google.com
      *
      * @hide
      */
    public GbaHttpUrlCredential(Context context, String nafAddress, int subId) {
        super(context, nafAddress, subId);
        System.setProperty("http.digest.support", "true");
    }

    public Authenticator getAuthenticator() {
        return mAuthenticator;
    }

    /**
      * Authenticator for OkHttp stack.
      * Used for HTTP digest method.
      *
      */
    private class GbaAuthenticator extends Authenticator {
        private PasswordAuthentication mPasswordAuthentication;

        protected PasswordAuthentication getPasswordAuthentication() {
            Log.i(TAG, "getPasswordAuthentication");

            if (mPasswordAuthentication == null || sCachedSessionKeyUsed) {
                Log.i(TAG, "Run GBA procedure");
                NafSessionKey nafSessionKey = GbaBaseCredential.getNafSessionKey();
                if (nafSessionKey == null ||
                        (nafSessionKey != null && nafSessionKey.getKey() == null)) {
                    return null;
                }
                String password = Base64.encodeToString(nafSessionKey.getKey(), Base64.NO_WRAP);
                mPasswordAuthentication = new PasswordAuthentication(
                    nafSessionKey.getBtid(),
                    password.toCharArray());
            } else {
                if (!sCachedSessionKeyUsed) {
                    sCachedSessionKeyUsed = true;
                }
            }

            return mPasswordAuthentication;
        }
    }

}
