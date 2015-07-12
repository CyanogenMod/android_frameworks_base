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

import android.annotation.SystemApi;
import android.net.Uri;
import android.os.Bundle;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an ongoing phone call that the in-call app should present to the user.
 *
 * {@hide}
 */
@SystemApi
public final class Call {
    /**
     * The state of a {@code Call} when newly created.
     */
    public static final int STATE_NEW = 0;

    /**
     * The state of an outgoing {@code Call} when dialing the remote number, but not yet connected.
     */
    public static final int STATE_DIALING = 1;

    /**
     * The state of an incoming {@code Call} when ringing locally, but not yet connected.
     */
    public static final int STATE_RINGING = 2;

    /**
     * The state of a {@code Call} when in a holding state.
     */
    public static final int STATE_HOLDING = 3;

    /**
     * The state of a {@code Call} when actively supporting conversation.
     */
    public static final int STATE_ACTIVE = 4;

    /**
     * The state of a {@code Call} when no further voice or other communication is being
     * transmitted, the remote side has been or will inevitably be informed that the {@code Call}
     * is no longer active, and the local data transport has or inevitably will release resources
     * associated with this {@code Call}.
     */
    public static final int STATE_DISCONNECTED = 7;

    /**
     * The state of an outgoing {@code Call}, but waiting for user input before proceeding.
     */
    public static final int STATE_PRE_DIAL_WAIT = 8;

    /**
     * The initial state of an outgoing {@code Call}.
     * Common transitions are to {@link #STATE_DIALING} state for a successful call or
     * {@link #STATE_DISCONNECTED} if it failed.
     */
    public static final int STATE_CONNECTING = 9;

    /**
     * The state of a {@code Call} when the user has initiated a disconnection of the call, but the
     * call has not yet been disconnected by the underlying {@code ConnectionService}.  The next
     * state of the call is (potentially) {@link #STATE_DISCONNECTED}.
     */
    public static final int STATE_DISCONNECTING = 10;

    /**
     * The key to retrieve the optional {@code PhoneAccount}s Telecom can bundle with its Call
     * extras. Used to pass the phone accounts to display on the front end to the user in order to
     * select phone accounts to (for example) place a call.
     *
     * @hide
     */
    public static final String AVAILABLE_PHONE_ACCOUNTS = "selectPhoneAccountAccounts";

    public static class Details {

        /** Call can currently be put on hold or unheld. */
        public static final int CAPABILITY_HOLD = 0x00000001;

        /** Call supports the hold feature. */
        public static final int CAPABILITY_SUPPORT_HOLD = 0x00000002;

        /**
         * Calls within a conference can be merged. A {@link ConnectionService} has the option to
         * add a {@link Conference} call before the child {@link Connection}s are merged. This is how
         * CDMA-based {@link Connection}s are implemented. For these unmerged {@link Conference}s, this
         * capability allows a merge button to be shown while the conference call is in the foreground
         * of the in-call UI.
         * <p>
         * This is only intended for use by a {@link Conference}.
         */
        public static final int CAPABILITY_MERGE_CONFERENCE = 0x00000004;

        /**
         * Calls within a conference can be swapped between foreground and background.
         * See {@link #CAPABILITY_MERGE_CONFERENCE} for additional information.
         * <p>
         * This is only intended for use by a {@link Conference}.
         */
        public static final int CAPABILITY_SWAP_CONFERENCE = 0x00000008;

        /**
         * @hide
         */
        public static final int CAPABILITY_UNUSED = 0x00000010;

        /** Call supports responding via text option. */
        public static final int CAPABILITY_RESPOND_VIA_TEXT = 0x00000020;

        /** Call can be muted. */
        public static final int CAPABILITY_MUTE = 0x00000040;

        /**
         * Call supports conference call management. This capability only applies to {@link Conference}
         * calls which can have {@link Connection}s as children.
         */
        public static final int CAPABILITY_MANAGE_CONFERENCE = 0x00000080;

        /**
         * Local device supports video telephony.
         * @hide
         */
        public static final int CAPABILITY_SUPPORTS_VT_LOCAL = 0x00000100;

        /**
         * Remote device supports video telephony.
         * @hide
         */
        public static final int CAPABILITY_SUPPORTS_VT_REMOTE = 0x00000200;

        /**
         * Call is using high definition audio.
         * @hide
         */
        public static final int CAPABILITY_HIGH_DEF_AUDIO = 0x00000400;

        /**
         * Call is using voice over WIFI.
         * @hide
         */
        public static final int CAPABILITY_VoWIFI = 0x00000800;

        /**
         * Call is able to be separated from its parent {@code Conference}, if any.
         */
        public static final int CAPABILITY_SEPARATE_FROM_CONFERENCE = 0x00001000;

        /**
         * Call is able to be individually disconnected when in a {@code Conference}.
         */
        public static final int CAPABILITY_DISCONNECT_FROM_CONFERENCE = 0x00002000;

        /** 
         * @hide
         */
        public static final int CAPABILITY_CALL_TYPE_MODIFIABLE = 0x00020000;

        /**
         * @hide
         */
        public static final int CAPABILITY_ADD_PARTICIPANT      = 0x00080000;

        /**
         * Whether the call is a generic conference, where we do not know the precise state of
         * participants in the conference (eg. on CDMA).
         *
         * @hide
         */
        public static final int CAPABILITY_GENERIC_CONFERENCE = 0x00004000;

        /** Add participant in an active or conference call option
         * @hide
         */
        public static final int ADD_PARTICIPANT = 0x00008000;

        /**
         * Call type can be modified for IMS call
         * @hide
         */
        public static final int CALL_TYPE_MODIFIABLE = 0x00020000;

        /**
         * Speed up audio setup for MT call.
         * @hide
         */
        public static final int CAPABILITY_SPEED_UP_MT_AUDIO = 0x00040000;

        private final Uri mHandle;
        private final int mHandlePresentation;
        private final String mCallerDisplayName;
        private final int mCallerDisplayNamePresentation;
        private final PhoneAccountHandle mAccountHandle;
        private final int mCallCapabilities;
        private final int mCallProperties;
        private final DisconnectCause mDisconnectCause;
        private final long mCreateTimeMillis;
        private final long mConnectTimeMillis;
        private final GatewayInfo mGatewayInfo;
        private final int mVideoState;
        private final StatusHints mStatusHints;
        private final Bundle mExtras;
        private final int mCallSubstate;

        /**
         * Whether the supplied capabilities  supports the specified capability.
         *
         * @param capabilities A bit field of capabilities.
         * @param capability The capability to check capabilities for.
         * @return Whether the specified capability is supported.
         * @hide
         */
        public static boolean can(int capabilities, int capability) {
            return (capabilities & capability) != 0;
        }

        /**
         * Whether the capabilities of this {@code Details} supports the specified capability.
         *
         * @param capability The capability to check capabilities for.
         * @return Whether the specified capability is supported.
         * @hide
         */
        public boolean can(int capability) {
            return can(mCallCapabilities, capability);
        }

        /**
         * Render a set of capability bits ({@code CAPABILITY_*}) as a human readable string.
         *
         * @param capabilities A capability bit field.
         * @return A human readable string representation.
         */
        public static String capabilitiesToString(int capabilities) {
            StringBuilder builder = new StringBuilder();
            builder.append("[Capabilities:");
            if (can(capabilities, CAPABILITY_HOLD)) {
                builder.append(" CAPABILITY_HOLD");
            }
            if (can(capabilities, CAPABILITY_SUPPORT_HOLD)) {
                builder.append(" CAPABILITY_SUPPORT_HOLD");
            }
            if (can(capabilities, CAPABILITY_MERGE_CONFERENCE)) {
                builder.append(" CAPABILITY_MERGE_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_SWAP_CONFERENCE)) {
                builder.append(" CAPABILITY_SWAP_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_RESPOND_VIA_TEXT)) {
                builder.append(" CAPABILITY_RESPOND_VIA_TEXT");
            }
            if (can(capabilities, CAPABILITY_MUTE)) {
                builder.append(" CAPABILITY_MUTE");
            }
            if (can(capabilities, CAPABILITY_MANAGE_CONFERENCE)) {
                builder.append(" CAPABILITY_MANAGE_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_SUPPORTS_VT_LOCAL)) {
                builder.append(" CAPABILITY_SUPPORTS_VT_LOCAL");
            }
            if (can(capabilities, CAPABILITY_SUPPORTS_VT_REMOTE)) {
                builder.append(" CAPABILITY_SUPPORTS_VT_REMOTE");
            }
            if (can(capabilities, CAPABILITY_HIGH_DEF_AUDIO)) {
                builder.append(" CAPABILITY_HIGH_DEF_AUDIO");
            }
            if (can(capabilities, CAPABILITY_VoWIFI)) {
                builder.append(" CAPABILITY_VoWIFI");
            }
            if (can(capabilities, CAPABILITY_GENERIC_CONFERENCE)) {
                builder.append(" CAPABILITY_GENERIC_CONFERENCE");
            }
            if (can(capabilities, CALL_TYPE_MODIFIABLE)) {
                builder.append(" CALL_TYPE_MODIFIABLE");
            }
            if (can(capabilities, ADD_PARTICIPANT)) {
                builder.append(" ADD_PARTICIPANT");
            }
            if (can(capabilities, CAPABILITY_SPEED_UP_MT_AUDIO)) {
                builder.append(" CAPABILITY_SPEED_UP_IMS_MT_AUDIO");
            }
            builder.append("]");
            return builder.toString();
        }

        /**
         * @return The handle (e.g., phone number) to which the {@code Call} is currently
         * connected.
         */
        public Uri getHandle() {
            return mHandle;
        }

        /**
         * @return The presentation requirements for the handle. See
         * {@link TelecomManager} for valid values.
         */
        public int getHandlePresentation() {
            return mHandlePresentation;
        }

        /**
         * @return The display name for the caller.
         */
        public String getCallerDisplayName() {
            return mCallerDisplayName;
        }

        /**
         * @return The presentation requirements for the caller display name. See
         * {@link TelecomManager} for valid values.
         */
        public int getCallerDisplayNamePresentation() {
            return mCallerDisplayNamePresentation;
        }

        /**
         * @return The {@code PhoneAccountHandle} whereby the {@code Call} is currently being
         * routed.
         */
        public PhoneAccountHandle getAccountHandle() {
            return mAccountHandle;
        }

        /**
         * @return A bitmask of the capabilities of the {@code Call}, as defined by the various
         *         {@code CAPABILITY_*} constants in this class.
         */
        public int getCallCapabilities() {
            return mCallCapabilities;
        }

        /**
         * @return A bitmask of the properties of the {@code Call}, as defined in
         *         {@link CallProperties}.
         */
        public int getCallProperties() {
            return mCallProperties;
        }

        /**
         * @return For a {@link #STATE_DISCONNECTED} {@code Call}, the disconnect cause expressed
         * by {@link android.telecom.DisconnectCause}.
         */
        public DisconnectCause getDisconnectCause() {
            return mDisconnectCause;
        }

        /**
         * @return The time the {@code Call} has been connected. This information is updated
         * periodically, but user interfaces should not rely on this to display any "call time
         * clock".
         */
        public long getConnectTimeMillis() {
            return mConnectTimeMillis;
        }

        /**
         * @return the time the Call object was created
         * {@hide}
         */
        public long getCreateTimeMillis() {
            return mCreateTimeMillis;
        }

        /**
         * @return Information about any calling gateway the {@code Call} may be using.
         */
        public GatewayInfo getGatewayInfo() {
            return mGatewayInfo;
        }

        /**
         * @return The video state of the {@code Call}.
         */
        public int getVideoState() {
            return mVideoState;
        }

        /**
         * @return The current {@link android.telecom.StatusHints}, or {@code null} if none
         * have been set.
         */
        public StatusHints getStatusHints() {
            return mStatusHints;
        }

        /**
         * @return A bundle extras to pass with the call
         */
        public Bundle getExtras() {
            return mExtras;
        }

        /**
         * @return The substate of the {@code Call}.
         * {@hide}
         */
        public int getCallSubstate() {
            return mCallSubstate;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Details) {
                Details d = (Details) o;
                return
                        Objects.equals(mHandle, d.mHandle) &&
                        Objects.equals(mHandlePresentation, d.mHandlePresentation) &&
                        Objects.equals(mCallerDisplayName, d.mCallerDisplayName) &&
                        Objects.equals(mCallerDisplayNamePresentation,
                                d.mCallerDisplayNamePresentation) &&
                        Objects.equals(mAccountHandle, d.mAccountHandle) &&
                        Objects.equals(mCallCapabilities, d.mCallCapabilities) &&
                        Objects.equals(mCallProperties, d.mCallProperties) &&
                        Objects.equals(mDisconnectCause, d.mDisconnectCause) &&
                        Objects.equals(mCreateTimeMillis, d.mCreateTimeMillis) &&
                        Objects.equals(mConnectTimeMillis, d.mConnectTimeMillis) &&
                        Objects.equals(mGatewayInfo, d.mGatewayInfo) &&
                        Objects.equals(mVideoState, d.mVideoState) &&
                        Objects.equals(mStatusHints, d.mStatusHints) &&
                        Objects.equals(mExtras, d.mExtras) &&
                        Objects.equals(mCallSubstate, d.mCallSubstate);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return
                    Objects.hashCode(mHandle) +
                    Objects.hashCode(mHandlePresentation) +
                    Objects.hashCode(mCallerDisplayName) +
                    Objects.hashCode(mCallerDisplayNamePresentation) +
                    Objects.hashCode(mAccountHandle) +
                    Objects.hashCode(mCallCapabilities) +
                    Objects.hashCode(mCallProperties) +
                    Objects.hashCode(mDisconnectCause) +
                    Objects.hashCode(mCreateTimeMillis) +
                    Objects.hashCode(mConnectTimeMillis) +
                    Objects.hashCode(mGatewayInfo) +
                    Objects.hashCode(mVideoState) +
                    Objects.hashCode(mStatusHints) +
                    Objects.hashCode(mExtras) +
                    Objects.hashCode(mCallSubstate);
        }

        /** {@hide} */
        public Details(
                Uri handle,
                int handlePresentation,
                String callerDisplayName,
                int callerDisplayNamePresentation,
                PhoneAccountHandle accountHandle,
                int capabilities,
                int properties,
                DisconnectCause disconnectCause,
                long createTimeMillis,
                long connectTimeMillis,
                GatewayInfo gatewayInfo,
                int videoState,
                StatusHints statusHints,
                Bundle extras,
                int callSubstate) {
            mHandle = handle;
            mHandlePresentation = handlePresentation;
            mCallerDisplayName = callerDisplayName;
            mCallerDisplayNamePresentation = callerDisplayNamePresentation;
            mAccountHandle = accountHandle;
            mCallCapabilities = capabilities;
            mCallProperties = properties;
            mDisconnectCause = disconnectCause;
            mCreateTimeMillis = createTimeMillis;
            mConnectTimeMillis = connectTimeMillis;
            mGatewayInfo = gatewayInfo;
            mVideoState = videoState;
            mStatusHints = statusHints;
            mExtras = extras;
            mCallSubstate = callSubstate;
        }
    }

    public static abstract class Listener {
        /**
         * Invoked when the state of this {@code Call} has changed. See {@link #getState()}.
         *
         * @param call The {@code Call} invoking this method.
         * @param state The new state of the {@code Call}.
         */
        public void onStateChanged(Call call, int state) {}

        /**
         * Invoked when the parent of this {@code Call} has changed. See {@link #getParent()}.
         *
         * @param call The {@code Call} invoking this method.
         * @param parent The new parent of the {@code Call}.
         */
        public void onParentChanged(Call call, Call parent) {}

        /**
         * Invoked when the children of this {@code Call} have changed. See {@link #getChildren()}.
         *
         * @param call The {@code Call} invoking this method.
         * @param children The new children of the {@code Call}.
         */
        public void onChildrenChanged(Call call, List<Call> children) {}

        /**
         * Invoked when the details of this {@code Call} have changed. See {@link #getDetails()}.
         *
         * @param call The {@code Call} invoking this method.
         * @param details A {@code Details} object describing the {@code Call}.
         */
        public void onDetailsChanged(Call call, Details details) {}

        /**
         * Invoked when the text messages that can be used as responses to the incoming
         * {@code Call} are loaded from the relevant database.
         * See {@link #getCannedTextResponses()}.
         *
         * @param call The {@code Call} invoking this method.
         * @param cannedTextResponses The text messages useable as responses.
         */
        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {}

        /**
         * Invoked when the post-dial sequence in the outgoing {@code Call} has reached a pause
         * character. This causes the post-dial signals to stop pending user confirmation. An
         * implementation should present this choice to the user and invoke
         * {@link #postDialContinue(boolean)} when the user makes the choice.
         *
         * @param call The {@code Call} invoking this method.
         * @param remainingPostDialSequence The post-dial characters that remain to be sent.
         */
        public void onPostDialWait(Call call, String remainingPostDialSequence) {}

        /**
         * Invoked when the {@code Call.VideoCall} of the {@code Call} has changed.
         *
         * @param call The {@code Call} invoking this method.
         * @param videoCall The {@code Call.VideoCall} associated with the {@code Call}.
         * @hide
         */
        public void onVideoCallChanged(Call call, InCallService.VideoCall videoCall) {}

        /**
         * Invoked when the {@code Call} is destroyed. Clients should refrain from cleaning
         * up their UI for the {@code Call} in response to state transitions. Specifically,
         * clients should not assume that a {@link #onStateChanged(Call, int)} with a state of
         * {@link #STATE_DISCONNECTED} is the final notification the {@code Call} will send. Rather,
         * clients should wait for this method to be invoked.
         *
         * @param call The {@code Call} being destroyed.
         */
        public void onCallDestroyed(Call call) {}

        /**
         * Invoked upon changes to the set of {@code Call}s with which this {@code Call} can be
         * conferenced.
         *
         * @param call The {@code Call} being updated.
         * @param conferenceableCalls The {@code Call}s with which this {@code Call} can be
         *          conferenced.
         */
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {}
    }

    private final Phone mPhone;
    private final String mTelecomCallId;
    private final InCallAdapter mInCallAdapter;
    private final List<String> mChildrenIds = new ArrayList<>();
    private final List<Call> mChildren = new ArrayList<>();
    private final List<Call> mUnmodifiableChildren = Collections.unmodifiableList(mChildren);
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final List<Call> mConferenceableCalls = new ArrayList<>();
    private final List<Call> mUnmodifiableConferenceableCalls =
            Collections.unmodifiableList(mConferenceableCalls);

    private boolean mChildrenCached;
    private String mParentId = null;
    private int mState;
    private List<String> mCannedTextResponses = null;
    private String mRemainingPostDialSequence;
    private InCallService.VideoCall mVideoCall;
    private Details mDetails;

    /** {@hide} */
    public boolean mIsActiveSub = false;

    /**
     * Obtains the post-dial sequence remaining to be emitted by this {@code Call}, if any.
     *
     * @return The remaining post-dial sequence, or {@code null} if there is no post-dial sequence
     * remaining or this {@code Call} is not in a post-dial state.
     */
    public String getRemainingPostDialSequence() {
        return mRemainingPostDialSequence;
    }

    /**
     * Instructs this {@link #STATE_RINGING} {@code Call} to answer.
     * @param videoState The video state in which to answer the call.
     */
    public void answer(int videoState) {
        mInCallAdapter.answerCall(mTelecomCallId, videoState);
    }

    /**
     * Instructs this {@link #STATE_RINGING} {@code Call} to deflect.
     * @param number The number to which the call will be deflected.
     */
    /** @hide */
    public void deflectCall(String number) {
        mInCallAdapter.deflectCall(mTelecomCallId, number);
    }

    /**
     * Instructs this {@link #STATE_RINGING} {@code Call} to reject.
     *
     * @param rejectWithMessage Whether to reject with a text message.
     * @param textMessage An optional text message with which to respond.
     */
    public void reject(boolean rejectWithMessage, String textMessage) {
        mInCallAdapter.rejectCall(mTelecomCallId, rejectWithMessage, textMessage);
    }

    /**
     * Instructs this {@code Call} to disconnect.
     */
    public void disconnect() {
        mInCallAdapter.disconnectCall(mTelecomCallId);
    }

    /**
     * Instructs this {@code Call} to go on hold.
     */
    public void hold() {
        mInCallAdapter.holdCall(mTelecomCallId);
    }

    /**
     * Instructs this {@link #STATE_HOLDING} call to release from hold.
     */
    public void unhold() {
        mInCallAdapter.unholdCall(mTelecomCallId);
    }

    /**
     * Instructs this {@code Call} to play a dual-tone multi-frequency signaling (DTMF) tone.
     *
     * Any other currently playing DTMF tone in the specified call is immediately stopped.
     *
     * @param digit A character representing the DTMF digit for which to play the tone. This
     *         value must be one of {@code '0'} through {@code '9'}, {@code '*'} or {@code '#'}.
     */
    public void playDtmfTone(char digit) {
        mInCallAdapter.playDtmfTone(mTelecomCallId, digit);
    }

    /**
     * Instructs this {@code Call} to stop any dual-tone multi-frequency signaling (DTMF) tone
     * currently playing.
     *
     * DTMF tones are played by calling {@link #playDtmfTone(char)}. If no DTMF tone is
     * currently playing, this method will do nothing.
     */
    public void stopDtmfTone() {
        mInCallAdapter.stopDtmfTone(mTelecomCallId);
    }

    /**
     * Instructs this {@code Call} to continue playing a post-dial DTMF string.
     *
     * A post-dial DTMF string is a string of digits entered after a phone number, when dialed,
     * that are immediately sent as DTMF tones to the recipient as soon as the connection is made.
     *
     * If the DTMF string contains a {@link TelecomManager#DTMF_CHARACTER_PAUSE} symbol, this
     * {@code Call} will temporarily pause playing the tones for a pre-defined period of time.
     *
     * If the DTMF string contains a {@link TelecomManager#DTMF_CHARACTER_WAIT} symbol, this
     * {@code Call} will pause playing the tones and notify listeners via
     * {@link Listener#onPostDialWait(Call, String)}. At this point, the in-call app
     * should display to the user an indication of this state and an affordance to continue
     * the postdial sequence. When the user decides to continue the postdial sequence, the in-call
     * app should invoke the {@link #postDialContinue(boolean)} method.
     *
     * @param proceed Whether or not to continue with the post-dial sequence.
     */
    public void postDialContinue(boolean proceed) {
        mInCallAdapter.postDialContinue(mTelecomCallId, proceed);
    }

    /**
     * Notifies this {@code Call} that an account has been selected and to proceed with placing
     * an outgoing call. Optionally sets this account as the default account.
     */
    public void phoneAccountSelected(PhoneAccountHandle accountHandle, boolean setDefault) {
        mInCallAdapter.phoneAccountSelected(mTelecomCallId, accountHandle, setDefault);

    }

    /**
     * Instructs this {@code Call} to enter a conference.
     *
     * @param callToConferenceWith The other call with which to conference.
     */
    public void conference(Call callToConferenceWith) {
        if (callToConferenceWith != null) {
            mInCallAdapter.conference(mTelecomCallId, callToConferenceWith.mTelecomCallId);
        }
    }

    /**
     * Instructs this {@code Call} to split from any conference call with which it may be
     * connected.
     */
    public void splitFromConference() {
        mInCallAdapter.splitFromConference(mTelecomCallId);
    }

    /**
     * Merges the calls within this conference. See {@link Details#CAPABILITY_MERGE_CONFERENCE}.
     */
    public void mergeConference() {
        mInCallAdapter.mergeConference(mTelecomCallId);
    }

    /**
     * Swaps the calls within this conference. See {@link Details#CAPABILITY_SWAP_CONFERENCE}.
     */
    public void swapConference() {
        mInCallAdapter.swapConference(mTelecomCallId);
    }

    /**
     * Obtains the parent of this {@code Call} in a conference, if any.
     *
     * @return The parent {@code Call}, or {@code null} if this {@code Call} is not a
     * child of any conference {@code Call}s.
     */
    public Call getParent() {
        if (mParentId != null) {
            return mPhone.internalGetCallByTelecomId(mParentId);
        }
        return null;
    }

    /**
     * Obtains the children of this conference {@code Call}, if any.
     *
     * @return The children of this {@code Call} if this {@code Call} is a conference, or an empty
     * {@code List} otherwise.
     */
    public List<Call> getChildren() {
        if (!mChildrenCached) {
            mChildrenCached = true;
            mChildren.clear();

            for(String id : mChildrenIds) {
                Call call = mPhone.internalGetCallByTelecomId(id);
                if (call == null) {
                    // At least one child was still not found, so do not save true for "cached"
                    mChildrenCached = false;
                } else {
                    mChildren.add(call);
                }
            }
        }

        return mUnmodifiableChildren;
    }

    /**
     * Returns the list of {@code Call}s with which this {@code Call} is allowed to conference.
     *
     * @return The list of conferenceable {@code Call}s.
     */
    public List<Call> getConferenceableCalls() {
        return mUnmodifiableConferenceableCalls;
    }

    /**
     * Obtains the state of this {@code Call}.
     *
     * @return A state value, chosen from the {@code STATE_*} constants.
     */
    public int getState() {
        return mState;
    }

    /**
     * Obtains a list of canned, pre-configured message responses to present to the user as
     * ways of rejecting this {@code Call} using via a text message.
     *
     * @see #reject(boolean, String)
     *
     * @return A list of canned text message responses.
     */
    public List<String> getCannedTextResponses() {
        return mCannedTextResponses;
    }

    /**
     * Obtains an object that can be used to display video from this {@code Call}.
     *
     * @return An {@code Call.VideoCall}.
     * @hide
     */
    public InCallService.VideoCall getVideoCall() {
        return mVideoCall;
    }

    /**
     * Obtains an object containing call details.
     *
     * @return A {@link Details} object. Depending on the state of the {@code Call}, the
     * result may be {@code null}.
     */
    public Details getDetails() {
        return mDetails;
    }

    /**
     * Adds a listener to this {@code Call}.
     *
     * @param listener A {@code Listener}.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes a listener from this {@code Call}.
     *
     * @param listener A {@code Listener}.
     */
    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /** {@hide} */
    Call(Phone phone, String telecomCallId, InCallAdapter inCallAdapter, boolean isActiveSub) {
        mPhone = phone;
        mTelecomCallId = telecomCallId;
        mInCallAdapter = inCallAdapter;
        mState = STATE_NEW;
        mIsActiveSub = isActiveSub;
    }

     /** {@hide} */
    Call(Phone phone, String telecomCallId, InCallAdapter inCallAdapter, boolean isActiveSub,
            int state) {
        mPhone = phone;
        mTelecomCallId = telecomCallId;
        mInCallAdapter = inCallAdapter;
        mState = state;
        mIsActiveSub = isActiveSub;
    }

    /** {@hide} */
    final String internalGetCallId() {
        return mTelecomCallId;
    }

    /** {@hide} */
    final void internalUpdate(ParcelableCall parcelableCall, Map<String, Call> callIdMap) {
        // First, we update the internal state as far as possible before firing any updates.
        Details details = new Details(
                parcelableCall.getHandle(),
                parcelableCall.getHandlePresentation(),
                parcelableCall.getCallerDisplayName(),
                parcelableCall.getCallerDisplayNamePresentation(),
                parcelableCall.getAccountHandle(),
                parcelableCall.getCapabilities(),
                parcelableCall.getProperties(),
                parcelableCall.getDisconnectCause(),
                parcelableCall.getCreateTimeMillis(),
                parcelableCall.getConnectTimeMillis(),
                parcelableCall.getGatewayInfo(),
                parcelableCall.getVideoState(),
                parcelableCall.getStatusHints(),
                parcelableCall.getExtras(),
                parcelableCall.getCallSubstate());
        boolean detailsChanged = !Objects.equals(mDetails, details);
        if (detailsChanged) {
            mDetails = details;
        }

        boolean cannedTextResponsesChanged = false;
        if (mCannedTextResponses == null && parcelableCall.getCannedSmsResponses() != null
                && !parcelableCall.getCannedSmsResponses().isEmpty()) {
            mCannedTextResponses =
                    Collections.unmodifiableList(parcelableCall.getCannedSmsResponses());
        }

        boolean videoCallChanged = !Objects.equals(mVideoCall, parcelableCall.getVideoCall());
        if (videoCallChanged) {
            mVideoCall = parcelableCall.getVideoCall();
        }

        int state = stateFromParcelableCallState(parcelableCall.getState());
        boolean stateChanged = (mState != state) || (mIsActiveSub != parcelableCall.mIsActiveSub);
        if (stateChanged) {
            mState = state;
            mIsActiveSub = parcelableCall.mIsActiveSub;
        }

        String parentId = parcelableCall.getParentCallId();
        boolean parentChanged = !Objects.equals(mParentId, parentId);
        if (parentChanged) {
            mParentId = parentId;
        }

        List<String> childCallIds = parcelableCall.getChildCallIds();
        boolean childrenChanged = !Objects.equals(childCallIds, mChildrenIds);
        if (childrenChanged) {
            mChildrenIds.clear();
            mChildrenIds.addAll(parcelableCall.getChildCallIds());
            mChildrenCached = false;
        }

        List<String> conferenceableCallIds = parcelableCall.getConferenceableCallIds();
        List<Call> conferenceableCalls = new ArrayList<Call>(conferenceableCallIds.size());
        for (String otherId : conferenceableCallIds) {
            if (callIdMap.containsKey(otherId)) {
                conferenceableCalls.add(callIdMap.get(otherId));
            }
        }

        if (!Objects.equals(mConferenceableCalls, conferenceableCalls)) {
            mConferenceableCalls.clear();
            mConferenceableCalls.addAll(conferenceableCalls);
            fireConferenceableCallsChanged();
        }

        // Now we fire updates, ensuring that any client who listens to any of these notifications
        // gets the most up-to-date state.

        if (stateChanged) {
            fireStateChanged(mState);
        }
        if (detailsChanged) {
            fireDetailsChanged(mDetails);
        }
        if (cannedTextResponsesChanged) {
            fireCannedTextResponsesLoaded(mCannedTextResponses);
        }
        if (videoCallChanged) {
            fireVideoCallChanged(mVideoCall);
        }
        if (parentChanged) {
            fireParentChanged(getParent());
        }
        if (childrenChanged) {
            fireChildrenChanged(getChildren());
        }

        // If we have transitioned to DISCONNECTED, that means we need to notify clients and
        // remove ourselves from the Phone. Note that we do this after completing all state updates
        // so a client can cleanly transition all their UI to the state appropriate for a
        // DISCONNECTED Call while still relying on the existence of that Call in the Phone's list.
        if (mState == STATE_DISCONNECTED) {
            fireCallDestroyed();
            mPhone.internalRemoveCall(this);
        }
    }

    /** {@hide} */
    final void internalSetPostDialWait(String remaining) {
        mRemainingPostDialSequence = remaining;
        firePostDialWait(mRemainingPostDialSequence);
    }

    /** {@hide} */
    final void internalSetDisconnected() {
        if (mState != Call.STATE_DISCONNECTED) {
            mState = Call.STATE_DISCONNECTED;
            fireStateChanged(mState);
            fireCallDestroyed();
            mPhone.internalRemoveCall(this);
        }
    }

    private void fireStateChanged(int newState) {
        for (Listener listener : mListeners) {
            listener.onStateChanged(this, newState);
        }
    }

    private void fireParentChanged(Call newParent) {
        for (Listener listener : mListeners) {
            listener.onParentChanged(this, newParent);
        }
    }

    private void fireChildrenChanged(List<Call> children) {
        for (Listener listener : mListeners) {
            listener.onChildrenChanged(this, children);
        }
    }

    private void fireDetailsChanged(Details details) {
        for (Listener listener : mListeners) {
            listener.onDetailsChanged(this, details);
        }
    }

    private void fireCannedTextResponsesLoaded(List<String> cannedTextResponses) {
        for (Listener listener : mListeners) {
            listener.onCannedTextResponsesLoaded(this, cannedTextResponses);
        }
    }

    private void fireVideoCallChanged(InCallService.VideoCall videoCall) {
        for (Listener listener : mListeners) {
            listener.onVideoCallChanged(this, videoCall);
        }
    }

    private void firePostDialWait(String remainingPostDialSequence) {
        for (Listener listener : mListeners) {
            listener.onPostDialWait(this, remainingPostDialSequence);
        }
    }

    private void fireCallDestroyed() {
        for (Listener listener : mListeners) {
            listener.onCallDestroyed(this);
        }
    }

    private void fireConferenceableCallsChanged() {
        for (Listener listener : mListeners) {
            listener.onConferenceableCallsChanged(this, mUnmodifiableConferenceableCalls);
        }
    }

    private int stateFromParcelableCallState(int parcelableCallState) {
        switch (parcelableCallState) {
            case CallState.NEW:
                return STATE_NEW;
            case CallState.CONNECTING:
                return STATE_CONNECTING;
            case CallState.PRE_DIAL_WAIT:
                return STATE_PRE_DIAL_WAIT;
            case CallState.DIALING:
                return STATE_DIALING;
            case CallState.RINGING:
                return STATE_RINGING;
            case CallState.ACTIVE:
                return STATE_ACTIVE;
            case CallState.ON_HOLD:
                return STATE_HOLDING;
            case CallState.DISCONNECTED:
                return STATE_DISCONNECTED;
            case CallState.ABORTED:
                return STATE_DISCONNECTED;
            case CallState.DISCONNECTING:
                return STATE_DISCONNECTING;
            default:
                Log.wtf(this, "Unrecognized CallState %s", parcelableCallState);
                return STATE_NEW;
        }
    }
}
