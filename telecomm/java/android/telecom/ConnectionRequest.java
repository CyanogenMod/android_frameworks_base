/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Simple data container encapsulating a request to some entity to
 * create a new {@link Connection}.
 */
public final class ConnectionRequest implements Parcelable {

    // TODO: Token to limit recursive invocations
    private PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Bundle mExtras;
    private final int mVideoState;

    /**
     * @param accountHandle The accountHandle which should be used to place the call.
     * @param handle The handle (e.g., phone number) to which the {@link Connection} is to connect.
     * @param extras Application-specific extra data.
     */
    public ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras) {
        this(accountHandle, handle, extras, VideoProfile.STATE_AUDIO_ONLY);
    }

    /**
     * @param accountHandle The accountHandle which should be used to place the call.
     * @param handle The handle (e.g., phone number) to which the {@link Connection} is to connect.
     * @param extras Application-specific extra data.
     * @param videoState Determines the video state for the connection.
     */
    public ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras,
            int videoState) {
        mAccountHandle = accountHandle;
        mAddress = handle;
        mExtras = extras;
        mVideoState = videoState;
    }

    private ConnectionRequest(Parcel in) {
        mAccountHandle = in.readParcelable(getClass().getClassLoader());
        mAddress = in.readParcelable(getClass().getClassLoader());
        mExtras = in.readParcelable(getClass().getClassLoader());
        mVideoState = in.readInt();
    }

    /**
     * The account which should be used to place the call.
     */
    public PhoneAccountHandle getAccountHandle() { return mAccountHandle; }

    /** {@hide} */
    public void setAccountHandle(PhoneAccountHandle acc) { mAccountHandle = acc; }

    /**
     * The handle (e.g., phone number) to which the {@link Connection} is to connect.
     */
    public Uri getAddress() { return mAddress; }

    /**
     * Application-specific extra data. Used for passing back information from an incoming
     * call {@code Intent}, and for any proprietary extensions arranged between a client
     * and servant {@code ConnectionService} which agree on a vocabulary for such data.
     */
    public Bundle getExtras() { return mExtras; }

    /**
     * Describes the video states supported by the client requesting the connection.
     * Valid values: {@link VideoProfile#STATE_AUDIO_ONLY},
     * {@link VideoProfile#STATE_BIDIRECTIONAL},
     * {@link VideoProfile#STATE_TX_ENABLED},
     * {@link VideoProfile#STATE_RX_ENABLED}.
     *
     * @return The video state for the connection.
     */
    public int getVideoState() {
        return mVideoState;
    }

    @Override
    public String toString() {
        return String.format("ConnectionRequest %s %s",
                mAddress == null
                        ? Uri.EMPTY
                        : Connection.toLogSafePhoneNumber(mAddress.toString()),
                mExtras == null ? "" : mExtras);
    }

    public static final Creator<ConnectionRequest> CREATOR = new Creator<ConnectionRequest> () {
        @Override
        public ConnectionRequest createFromParcel(Parcel source) {
            return new ConnectionRequest(source);
        }

        @Override
        public ConnectionRequest[] newArray(int size) {
            return new ConnectionRequest[size];
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeParcelable(mAccountHandle, 0);
        destination.writeParcelable(mAddress, 0);
        destination.writeParcelable(mExtras, 0);
        destination.writeInt(mVideoState);
    }
}
