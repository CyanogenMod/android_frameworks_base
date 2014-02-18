/* Copyright (c) 2013, The Linux Foundation. All rights reserved.
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
 *
 */

package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.FileOutputStream;
import java.io.IOException;

/** @hide */
public class PPPOEConfig implements Parcelable {

    public final static String PPPOE_DEFAULT_INTERFACE = "wlan0";
    public final static int PPPOE_DEFAULT_LCP_ECHO_INTERVAL = 20;
    public final static int PPPOE_DEFAULT_LCP_ECHO_FAILURE = 3;
    public final static int PPPOE_DEFAULT_MTU = 1492;
    public final static int PPPOE_DEFAULT_MRU = 1492;
    public final static int PPPOE_DEFAULT_TIMEOUT = 80;
    public final static int PPPOE_DEFAULT_MSS = 1412;

    public String username;
    public String password;
    public String interf;
    public int lcp_echo_interval;
    public int lcp_echo_failure;
    public int mtu;
    public int mru;
    public int timeout;
    public int MSS;


    public PPPOEConfig() {
        this(null, null);
    }
    public PPPOEConfig(String user, String pass) {
        this(user, pass, PPPOE_DEFAULT_INTERFACE, PPPOE_DEFAULT_LCP_ECHO_INTERVAL,
                PPPOE_DEFAULT_LCP_ECHO_FAILURE, PPPOE_DEFAULT_MTU, PPPOE_DEFAULT_MRU,
                PPPOE_DEFAULT_TIMEOUT, PPPOE_DEFAULT_MSS);
    }

    public PPPOEConfig(String user, String pass, String iface, int lcp_echo_interval,
            int lcp_echo_failure, int mtu, int mru, int timeout, int mss) {
        this.username = user;
        this.password = pass;
        this.interf = iface;
        this.lcp_echo_interval = lcp_echo_interval;
        this.lcp_echo_failure = lcp_echo_failure;
        this.mtu = mtu;
        this.mru = mru;
        this.timeout = timeout;
        this.MSS = mss;
    }

    /** Implement the Parcelable interface */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(password);
        dest.writeString(interf);
        dest.writeInt(lcp_echo_interval);
        dest.writeInt(lcp_echo_failure);
        dest.writeInt(mtu);
        dest.writeInt(mru);
        dest.writeInt(timeout);
        dest.writeInt(MSS);
    }

    /** Implement the Parcelable interface */
    public static final Creator<PPPOEConfig> CREATOR = new Creator<PPPOEConfig>() {
            public PPPOEConfig createFromParcel(Parcel in) {
                PPPOEConfig config = new PPPOEConfig();
                config.username = in.readString();
                config.password = in.readString();
                config.interf = in.readString();
                config.lcp_echo_interval = in.readInt();
                config.lcp_echo_failure = in.readInt();
                config.mtu = in.readInt();
                config.mru = in.readInt();
                config.timeout = in.readInt();
                config.MSS = in.readInt();
                return config;
            }

            public PPPOEConfig[] newArray(int size) {
                return new PPPOEConfig[size];
            }
    };
}
