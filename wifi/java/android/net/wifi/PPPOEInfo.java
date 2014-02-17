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

/** @hide */
public class PPPOEInfo implements Parcelable {
    public enum Status {
        OFFLINE,
        CONNECTING,
        ONLINE,
    }

    public Status status;
    /** Online time, seconds */
    public long online_time;

    public PPPOEInfo() {
    }

    public PPPOEInfo(Status status, long connecttedtime) {
        this.status = status;
        if(this.status == Status.ONLINE) {
            this.online_time = (System.currentTimeMillis() - connecttedtime) / 1000;
        } else {
            this.online_time = 0;
        }
    }
    /** Implement the Parcelable interface */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        String status_str = status.name();
        dest.writeString(status_str);
        dest.writeLong(online_time);
    }

    /** Implement the Parcelable interface */
    public static final Creator<PPPOEInfo> CREATOR =
        new Creator<PPPOEInfo>() {
            public PPPOEInfo createFromParcel(Parcel in) {
                PPPOEInfo info = new PPPOEInfo();
                String status_str = in.readString();
                info.status = Status.valueOf(status_str);
                info.online_time = in.readLong();
                return info;
            }

            public PPPOEInfo[] newArray(int size) {
                return new PPPOEInfo[size];
            }
        };
}
