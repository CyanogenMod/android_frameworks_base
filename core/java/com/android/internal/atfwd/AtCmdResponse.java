/* Copyright (c) 2010,2011, Code Aurora Forum. All rights reserved.
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
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
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


package com.android.internal.atfwd;

import android.os.Parcel;
import android.os.Parcelable;

public class AtCmdResponse implements Parcelable {

    public static final int RESULT_ERROR = 0;
    public static final int RESULT_OK = 1;
    public static final int RESULT_OTHER = 2;

    private int mResult;
    private String mResponse;

    public int getResult() {
        return mResult;
    }

    public void setResult(int mResult) {
        this.mResult = mResult;
    }

    public String getResponse() {
        return mResponse;
    }

    public void setResponse(String mResponse) {
        this.mResponse = mResponse;
    }

    AtCmdResponse(int result, String response) {
        init(result, response);
    }

    AtCmdResponse(Parcel p, int flags) {
        int result = p.readInt();
        String response = p.readString();
        init(result, response);
    }

    private void init(int result, String response) {
        mResult = result;
        mResponse = response;
    }
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mResult);
        dest.writeString(mResponse);
    }

    public static Parcelable.Creator<AtCmdResponse> CREATOR =
        new Parcelable.Creator<AtCmdResponse>() {

        public AtCmdResponse createFromParcel(Parcel source) {
            int result;
            String response;

            result = source.readInt();
            response = source.readString();
            return new AtCmdResponse(result, response);
        }

        public AtCmdResponse[] newArray(int size) {
            return new AtCmdResponse[size];
        }

    };

}
