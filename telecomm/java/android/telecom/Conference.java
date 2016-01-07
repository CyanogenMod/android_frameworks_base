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

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.telecom.Connection.VideoProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Represents a conference call which can contain any number of {@link Connection} objects.
 */
public abstract class Conference extends Conferenceable {

    /**
     * Used to indicate that the conference connection time is not specified.  If not specified,
     * Telecom will set the connect time.
     */
    public static final long CONNECT_TIME_NOT_SPECIFIED = 0;

    /** @hide */
    public abstract static class Listener {
        public void onStateChanged(Conference conference, int oldState, int newState) {}
        public void onDisconnected(Conference conference, DisconnectCause disconnectCause) {}
        public void onConnectionAdded(Conference conference, Connection connection) {}
        public void onConnectionRemoved(Conference conference, Connection connection) {}
        public void onConferenceableConnectionsChanged(
                Conference conference, List<Connection> conferenceableConnections) {}
        public void onDestroyed(Conference conference) {}
        public void onConnectionCapabilitiesChanged(
                Conference conference, int connectionCapabilities) {}
        public void onConnectionPropertiesChanged(
                Conference conference, int connectionProperties) {}
        public void onVideoStateChanged(Conference c, int videoState) { }
        public void onVideoProviderChanged(Conference c, Connection.VideoProvider videoProvider) {}
        public void onStatusHintsChanged(Conference conference, StatusHints statusHints) {}
        public void onExtrasChanged(Conference conference, Bundle extras) {}
    }

    private final Set<Listener> mListeners = new CopyOnWriteArraySet<>();
    private final List<Connection> mChildConnections = new CopyOnWriteArrayList<>();
    private final List<Connection> mUnmodifiableChildConnections =
            Collections.unmodifiableList(mChildConnections);
    private final List<Connection> mConferenceableConnections = new ArrayList<>();
    private final List<Connection> mUnmodifiableConferenceableConnections =
            Collections.unmodifiableList(mConferenceableConnections);

    private PhoneAccountHandle mPhoneAccount;
    private CallAudioState mCallAudioState;
    private int mState = Connection.STATE_NEW;
    private DisconnectCause mDisconnectCause;
    private int mConnectionCapabilities;
    private int mConnectionProperties;
    private String mDisconnectMessage;
    private long mConnectTimeMillis = CONNECT_TIME_NOT_SPECIFIED;
    private StatusHints mStatusHints;
    private Bundle mExtras;

    private final Connection.Listener mConnectionDeathListener = new Connection.Listener() {
        @Override
        public void onDestroyed(Connection c) {
            if (mConferenceableConnections.remove(c)) {
                fireOnConferenceableConnectionsChanged();
            }
        }
    };

    /**
     * Constructs a new Conference with a mandatory {@link PhoneAccountHandle}
     *
     * @param phoneAccount The {@code PhoneAccountHandle} associated with the conference.
     */
    public Conference(PhoneAccountHandle phoneAccount) {
        mPhoneAccount = phoneAccount;
    }

    /**
     * Returns the {@link PhoneAccountHandle} the conference call is being placed through.
     *
     * @return A {@code PhoneAccountHandle} object representing the PhoneAccount of the conference.
     */
    public final PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccount;
    }

    /**
     * Returns the list of connections currently associated with the conference call.
     *
     * @return A list of {@code Connection} objects which represent the children of the conference.
     */
    public final List<Connection> getConnections() {
        return mUnmodifiableChildConnections;
    }

    /**
     * Gets the state of the conference call. See {@link Connection} for valid values.
     *
     * @return A constant representing the state the conference call is currently in.
     */
    public final int getState() {
        return mState;
    }

    /**
     * Returns the capabilities of the conference. See {@code CAPABILITY_*} constants in class
     * {@link Connection} for valid values.
     *
     * @return A bitmask of the capabilities of the conference call.
     */
    public final int getConnectionCapabilities() {
        return mConnectionCapabilities;
    }

    /**
     * Whether the given capabilities support the specified capability.
     *
     * @param capabilities A capability bit field.
     * @param capability The capability to check capabilities for.
     * @return Whether the specified capability is supported.
     * @hide
     */
    public static boolean can(int capabilities, int capability) {
        return (capabilities & capability) != 0;
    }

    /**
     * Whether the capabilities of this {@code Connection} supports the specified capability.
     *
     * @param capability The capability to check capabilities for.
     * @return Whether the specified capability is supported.
     * @hide
     */
    public boolean can(int capability) {
        return can(mConnectionCapabilities, capability);
    }

    /**
     * Removes the specified capability from the set of capabilities of this {@code Conference}.
     *
     * @param capability The capability to remove from the set.
     * @hide
     */
    public void removeCapability(int capability) {
        int newCapabilities = mConnectionCapabilities;
        newCapabilities &= ~capability;

        setConnectionCapabilities(newCapabilities);
    }

    /**
     * Adds the specified capability to the set of capabilities of this {@code Conference}.
     *
     * @param capability The capability to add to the set.
     * @hide
     */
    public void addCapability(int capability) {
        int newCapabilities = mConnectionCapabilities;
        newCapabilities |= capability;

        setConnectionCapabilities(newCapabilities);
    }

    /**
     * Returns a bit mask of this conference's properties. See the {@code PROPERTY_*} constants
     * in the {@link Connection} class.
     * @hide
     */
    public final int getConnectionProperties() {
        return mConnectionProperties;
    }


    /**
     * Sets this conference's properties as a bit mask of the {@code PROPERTY_*} constants
     * in the {@link Connection} class.
     *
     * @param connectionProperties The new connection properties.
     * @hide
     */
    public final void setConnectionProperties(int connectionProperties) {
        if (mConnectionProperties != connectionProperties) {
            mConnectionProperties = connectionProperties;
            for (Listener l : mListeners) {
                l.onConnectionPropertiesChanged(this, mConnectionProperties);
            }
        }
    }

    /**
     * @return The audio state of the conference, describing how its audio is currently
     *         being routed by the system. This is {@code null} if this Conference
     *         does not directly know about its audio state.
     * @deprecated Use {@link #getCallAudioState()} instead.
     * @hide
     */
    @Deprecated
    @SystemApi
    public final AudioState getAudioState() {
        return new AudioState(mCallAudioState);
    }

    /**
     * @return The audio state of the conference, describing how its audio is currently
     *         being routed by the system. This is {@code null} if this Conference
     *         does not directly know about its audio state.
     */
    public final CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    /**
     * Returns VideoProvider of the primary call. This can be null.
     */
    public VideoProvider getVideoProvider() {
        return null;
    }

    /**
     * Returns video state of the primary call.
     */
    public int getVideoState() {
        return VideoProfile.STATE_AUDIO_ONLY;
    }

    /**
     * Invoked when the Conference and all it's {@link Connection}s should be disconnected.
     */
    public void onDisconnect() {}

    /**
     * Invoked when the specified {@link Connection} should be separated from the conference call.
     *
     * @param connection The connection to separate.
     */
    public void onSeparate(Connection connection) {}

    /**
     * Invoked when the conference adds a participant to the conference call.
     *
     * @param participant The participant to be added with conference call.
     * @hide
     */
    public void onAddParticipant(String participant) {}

    /**
     * Invoked when the specified {@link Connection} should merged with the conference call.
     *
     * @param connection The {@code Connection} to merge.
     */
    public void onMerge(Connection connection) {}

    /**
     * Invoked when the conference should be put on hold.
     */
    public void onHold() {}

    /**
     * Invoked when the conference should be moved from hold to active.
     */
    public void onUnhold() {}

    /**
     * Invoked when the child calls should be merged. Only invoked if the conference contains the
     * capability {@link Connection#CAPABILITY_MERGE_CONFERENCE}.
     */
    public void onMerge() {}

    /**
     * Invoked when the child calls should be swapped. Only invoked if the conference contains the
     * capability {@link Connection#CAPABILITY_SWAP_CONFERENCE}.
     */
    public void onSwap() {}

    /**
     * Notifies this conference of a request to play a DTMF tone.
     *
     * @param c A DTMF character.
     */
    public void onPlayDtmfTone(char c) {}

    /**
     * Notifies this conference of a request to stop any currently playing DTMF tones.
     */
    public void onStopDtmfTone() {}

    /**
     * Notifies this conference that the {@link #getAudioState()} property has a new value.
     *
     * @param state The new call audio state.
     * @deprecated Use {@link #onCallAudioStateChanged(CallAudioState)} instead.
     * @hide
     */
    @SystemApi
    @Deprecated
    public void onAudioStateChanged(AudioState state) {}

    /**
     * Notifies this conference that the {@link #getCallAudioState()} property has a new value.
     *
     * @param state The new call audio state.
     */
    public void onCallAudioStateChanged(CallAudioState state) {}

    /**
     * Notifies this conference that a connection has been added to it.
     *
     * @param connection The newly added connection.
     */
    public void onConnectionAdded(Connection connection) {}

    /**
     * Sets state to be on hold.
     */
    public final void setOnHold() {
        setState(Connection.STATE_HOLDING);
    }

    /**
     * Sets state to be dialing.
     */
    public final void setDialing() {
        setState(Connection.STATE_DIALING);
    }

    /**
     * Sets state to be active.
     */
    public final void setActive() {
        setState(Connection.STATE_ACTIVE);
    }

    /**
     * Sets state to disconnected.
     *
     * @param disconnectCause The reason for the disconnection, as described by
     *     {@link android.telecom.DisconnectCause}.
     */
    public final void setDisconnected(DisconnectCause disconnectCause) {
        mDisconnectCause = disconnectCause;;
        setState(Connection.STATE_DISCONNECTED);
        for (Listener l : mListeners) {
            l.onDisconnected(this, mDisconnectCause);
        }
    }

    /**
     * @return The {@link DisconnectCause} for this connection.
     */
    public final DisconnectCause getDisconnectCause() {
        return mDisconnectCause;
    }

    /**
     * Sets the capabilities of a conference. See {@code CAPABILITY_*} constants of class
     * {@link Connection} for valid values.
     *
     * @param connectionCapabilities A bitmask of the {@code PhoneCapabilities} of the conference call.
     */
    public final void setConnectionCapabilities(int connectionCapabilities) {
        if (connectionCapabilities != mConnectionCapabilities) {
            mConnectionCapabilities = connectionCapabilities;

            for (Listener l : mListeners) {
                l.onConnectionCapabilitiesChanged(this, mConnectionCapabilities);
            }
        }
    }

    /**
     * Adds the specified connection as a child of this conference.
     *
     * @param connection The connection to add.
     * @return True if the connection was successfully added.
     */
    public final boolean addConnection(Connection connection) {
        Log.d(this, "Connection=%s, connection=", connection);
        if (connection != null && !mChildConnections.contains(connection)) {
            if (connection.setConference(this)) {
                mChildConnections.add(connection);
                onConnectionAdded(connection);
                for (Listener l : mListeners) {
                    l.onConnectionAdded(this, connection);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the specified connection as a child of this conference.
     *
     * @param connection The connection to remove.
     */
    public final void removeConnection(Connection connection) {
        Log.d(this, "removing %s from %s", connection, mChildConnections);
        if (connection != null && mChildConnections.remove(connection)) {
            connection.resetConference();
            for (Listener l : mListeners) {
                l.onConnectionRemoved(this, connection);
            }
        }
    }

    /**
     * Sets the connections with which this connection can be conferenced.
     *
     * @param conferenceableConnections The set of connections this connection can conference with.
     */
    public final void setConferenceableConnections(List<Connection> conferenceableConnections) {
        clearConferenceableList();
        for (Connection c : conferenceableConnections) {
            // If statement checks for duplicates in input. It makes it N^2 but we're dealing with a
            // small amount of items here.
            if (!mConferenceableConnections.contains(c)) {
                c.addConnectionListener(mConnectionDeathListener);
                mConferenceableConnections.add(c);
            }
        }
        fireOnConferenceableConnectionsChanged();
    }

    /**
     * Set the video state for the conference.
     * Valid values: {@link VideoProfile#STATE_AUDIO_ONLY},
     * {@link VideoProfile#STATE_BIDIRECTIONAL},
     * {@link VideoProfile#STATE_TX_ENABLED},
     * {@link VideoProfile#STATE_RX_ENABLED}.
     *
     * @param videoState The new video state.
     */
    public final void setVideoState(Connection c, int videoState) {
        Log.d(this, "setVideoState Conference: %s Connection: %s VideoState: %s",
                this, c, videoState);
        for (Listener l : mListeners) {
            l.onVideoStateChanged(this, videoState);
        }
    }

    /**
     * Sets the video connection provider.
     *
     * @param videoProvider The video provider.
     */
    public final void setVideoProvider(Connection c, Connection.VideoProvider videoProvider) {
        Log.d(this, "setVideoProvider Conference: %s Connection: %s VideoState: %s",
                this, c, videoProvider);
        for (Listener l : mListeners) {
            l.onVideoProviderChanged(this, videoProvider);
        }
    }

    private final void fireOnConferenceableConnectionsChanged() {
        for (Listener l : mListeners) {
            l.onConferenceableConnectionsChanged(this, getConferenceableConnections());
        }
    }

    /**
     * Returns the connections with which this connection can be conferenced.
     */
    public final List<Connection> getConferenceableConnections() {
        return mUnmodifiableConferenceableConnections;
    }

    /**
     * Tears down the conference object and any of its current connections.
     */
    public final void destroy() {
        Log.d(this, "destroying conference : %s", this);
        // Tear down the children.
        for (Connection connection : mChildConnections) {
            Log.d(this, "removing connection %s", connection);
            removeConnection(connection);
        }

        // If not yet disconnected, set the conference call as disconnected first.
        if (mState != Connection.STATE_DISCONNECTED) {
            Log.d(this, "setting to disconnected");
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        }

        // ...and notify.
        for (Listener l : mListeners) {
            l.onDestroyed(this);
        }
    }

    /**
     * Add a listener to be notified of a state change.
     *
     * @param listener The new listener.
     * @return This conference.
     * @hide
     */
    public final Conference addListener(Listener listener) {
        mListeners.add(listener);
        return this;
    }

    /**
     * Removes the specified listener.
     *
     * @param listener The listener to remove.
     * @return This conference.
     * @hide
     */
    public final Conference removeListener(Listener listener) {
        mListeners.remove(listener);
        return this;
    }

    /**
     * Retrieves the primary connection associated with the conference.  The primary connection is
     * the connection from which the conference will retrieve its current state.
     *
     * @return The primary connection.
     * @hide
     */
    @SystemApi
    public Connection getPrimaryConnection() {
        if (mUnmodifiableChildConnections == null || mUnmodifiableChildConnections.isEmpty()) {
            return null;
        }
        return mUnmodifiableChildConnections.get(0);
    }

    /**
     * @hide
     * @deprecated Use {@link #setConnectionTime}.
     */
    @Deprecated
    @SystemApi
    public final void setConnectTimeMillis(long connectTimeMillis) {
        setConnectionTime(connectTimeMillis);
    }

    /**
     * Sets the connection start time of the {@code Conference}.
     *
     * @param connectionTimeMillis The connection time, in milliseconds.
     */
    public final void setConnectionTime(long connectionTimeMillis) {
        mConnectTimeMillis = connectionTimeMillis;
    }

    /**
     * @hide
     * @deprecated Use {@link #getConnectionTime}.
     */
    @Deprecated
    @SystemApi
    public final long getConnectTimeMillis() {
        return getConnectionTime();
    }

    /**
     * Retrieves the connection start time of the {@code Conference}, if specified.  A value of
     * {@link #CONNECT_TIME_NOT_SPECIFIED} indicates that Telecom should determine the start time
     * of the conference.
     *
     * @return The time at which the {@code Conference} was connected.
     */
    public final long getConnectionTime() {
        return mConnectTimeMillis;
    }

    /**
     * Inform this Conference that the state of its audio output has been changed externally.
     *
     * @param state The new audio state.
     * @hide
     */
    final void setCallAudioState(CallAudioState state) {
        Log.d(this, "setCallAudioState %s", state);
        mCallAudioState = state;
        onAudioStateChanged(getAudioState());
        onCallAudioStateChanged(state);
    }

    private void setState(int newState) {
        if (newState != Connection.STATE_ACTIVE &&
                newState != Connection.STATE_DIALING &&
                newState != Connection.STATE_HOLDING &&
                newState != Connection.STATE_DISCONNECTED) {
            Log.w(this, "Unsupported state transition for Conference call.",
                    Connection.stateToString(newState));
            return;
        }

        if (mState != newState) {
            int oldState = mState;
            mState = newState;
            for (Listener l : mListeners) {
                l.onStateChanged(this, oldState, newState);
            }
        }
    }

    private final void clearConferenceableList() {
        for (Connection c : mConferenceableConnections) {
            c.removeConnectionListener(mConnectionDeathListener);
        }
        mConferenceableConnections.clear();
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "[State: %s, Capabilites: %s, Properties: %s, " +
                "VideoState: %s, VideoProvider: %s, ThisObject %s]",
                Connection.stateToString(mState),
                Call.Details.capabilitiesToString(mConnectionCapabilities),
                Call.Details.propertiesToString(mConnectionProperties),
                getVideoState(),
                getVideoProvider(),
                super.toString());
    }

    /**
     * Sets the label and icon status to display in the InCall UI.
     *
     * @param statusHints The status label and icon to set.
     */
    public final void setStatusHints(StatusHints statusHints) {
        mStatusHints = statusHints;
        for (Listener l : mListeners) {
            l.onStatusHintsChanged(this, statusHints);
        }
    }

    /**
     * @return The status hints for this conference.
     */
    public final StatusHints getStatusHints() {
        return mStatusHints;
    }

    /**
     * Set some extras that can be associated with this {@code Conference}. No assumptions should
     * be made as to how an In-Call UI or service will handle these extras.
     * Keys should be fully qualified (e.g., com.example.MY_EXTRA) to avoid conflicts.
     *
     * @param extras The extras associated with this {@code Connection}.
     */
    public final void setExtras(@Nullable Bundle extras) {
        mExtras = extras;
        for (Listener l : mListeners) {
            l.onExtrasChanged(this, extras);
        }
    }

    /**
     * @return The extras associated with this conference.
     */
    public final Bundle getExtras() {
        return mExtras;
    }
}
