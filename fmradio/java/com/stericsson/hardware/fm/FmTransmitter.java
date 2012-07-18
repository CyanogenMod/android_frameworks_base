/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Author: Bjorn Pileryd (bjorn.pileryd@sonyericsson.com)
 * Author: Markus Grape (markus.grape@stericsson.com) for ST-Ericsson
 */

package com.stericsson.hardware.fm;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.IOException;

/**
 * The FmTransmitter controls the output of FM radio from the device. When
 * started, the transmitter will transmit audio via FM signals. The unit for all
 * frequencies in this class is kHz. Note that this API only controls the output
 * of FM radio, to select the audio stream the MediaPlayer interface should be
 * used, see code example below the state diagram.
 * <p>
 * The output frequency can be changed at any time using
 * {@link #setFrequency(int)}. The transmitter also supports transmission of RDS
 * data, see {@link #setRdsData(Bundle)}.
 * </p>
 * <p>
 * Get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String)
 * Context.getSystemService("fm_transmitter")}.
 * </p>
 * <a name="StateDiagram"></a> <h3>State Diagram</h3>
 * <p>
 * The state machine is designed to take into account that some hardware may
 * need time to prepare, and that it is likely to consume more power when paused
 * and started than it does in the idle state. The hardware implementation of
 * this interface should do the time consuming preparation procedures in the
 * starting state. The switching between paused and started states should be
 * fast to give a good user experience.
 * </p>
 * <p>
 * <img src="../../../../images/FmTransmitter_states.gif"
 * alt="FmTransmitter State diagram" border="0" />
 * </p>
 * <table border="1">
 * <tr>
 * <th>Method Name</th>
 * <th>Valid States</th>
 * <th>Invalid States</th>
 * <th>Comments</th>
 * </tr>
 * <tr>
 * <td>{@link #startAsync(FmBand)}</td>
 * <td>{idle}</td>
 * <td>{starting, started, paused, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the starting state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #start(FmBand)}</td>
 * <td>{idle}</td>
 * <td>{starting, started, paused, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the started state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #resume()}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the started state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #pause()}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the paused state. Calling this method in an invalid state throws an
 * IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #reset()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>Successful invocation of this method transfers the object to the idle
 * state, the object is like being just created.</td>
 * </tr>
 * <tr>
 * <td>{@link #getState()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #isApiSupported(Context)}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #setFrequency(int)}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state does not change the
 * object state. Calling this method in an invalid state throws an
 * IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #getFrequency()}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state does not change the
 * object state. Calling this method in an invalid state throws an
 * IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #setRdsData(Bundle)}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state does not change the
 * object state. Calling this method in an invalid state throws an
 * IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #isBlockScanSupported()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #startBlockScan(int, int)}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the scanning state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #stopScan()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>Successful invocation of this method in a valid state tries to stop
 * performing a scan operation. The hardware might continue the scan for an
 * unspecified amount of time after this method is called. Once the scan has
 * stopped, it will be notified via {@link OnScanListener}</td>
 * </tr>
 * <tr>
 * <td>{@link #sendExtraCommand(String, String[])}</td>
 * <td>vendor specific</td>
 * <td>vendor specific</td>
 * <td>vendor specific</td>
 * </tr>
 * </table>
 * <a name="Examples"></a> <h3>Example code</h3>
 * <pre>
 * // prepare and start the FM transmitter
 * FmTransmitter fmt = (FmTransmitter) getSystemService("fm_transmitter");
 * fmt.start(new FmBand(FmBand.BAND_EU));
 *
 * // prepare and start playback
 * MediaPlayer mp = new MediaPlayer();
 * mp.setDataSource(PATH_TO_FILE);
 * mp.prepare();
 * mp.start();
 * </pre>
 * <a name="FMHandling"></a> <h3>FM receiving/transmission handling</h3>
 * <p>
 * In this API, FM radio cannot be received and transmitted at the same time,
 * therefore the state machine is designed to prevent incorrect usage. The
 * FmReceiver and FmTransmitter has a separate state machine and only one can be
 * <i>active</i> (state other than idle).
 * <ul>
 * <li>If start is called on FmTransmitter and the FmReceiver is <i>active</i>,
 * the FmReceiver MUST release resources and change state to idle.</li>
 * <li>The FmReceiver will in that case be notified by
 * {@link com.stericsson.hardware.fm.FmReceiver.OnForcedResetListener#onForcedReset(int)}.</li>
 * </ul>
 * </p>
 * <a name="ErrorHandling"></a> <h3>Error handling</h3>
 * <p>
 * In general, it is up to the application that uses this API to keep track of
 * events that could affect the FM radio user experience. The hardware
 * implementation side of this API should only take actions when it is really
 * necessary, e.g. if the hardware is forced to pause or reset, and notify the
 * application by using the {@link OnForcedPauseListener},
 * {@link OnForcedResetListener} or {@link OnErrorListener}.
 * </p>
 */
public abstract class FmTransmitter {

    /**
     * The FmTransmitter had to be shut down due to a non-critical error,
     * meaning that it is OK to attempt a restart immediately after this. An
     * example is when the hardware was shut down in order to save power after
     * being in the paused state for too long.
     */
    public static final int RESET_NON_CRITICAL = 0;

    /**
     * The FmTransmitter had to be shut down due to a critical error. The FM
     * hardware it not guaranteed to work as expected after receiving this
     * error.
     */
    public static final int RESET_CRITICAL = 1;

    /**
     * The FmReceiver was activated and therefore the FmTransmitter must be put
     * in idle.
     *
     * @see FmReceiver#startAsync(FmBand)
     */
    public static final int RESET_RX_IN_USE = 2;

    /**
     * The radio is not allowed to be used, typically when flight mode is
     * enabled.
     */
    public static final int RESET_RADIO_FORBIDDEN = 3;

    /**
     * Indicates that the FmTransmitter is in an idle state. No resources are
     * allocated and power consumption is kept to a minimum.
     */
    public static final int STATE_IDLE = 0;

    /**
     * Indicates that the FmTransmitter is allocating resources and preparing to
     * transmit FM radio.
     */
    public static final int STATE_STARTING = 1;

    /**
     * Indicates that the FmTransmitter is transmitting FM radio.
     */
    public static final int STATE_STARTED = 2;

    /**
     * Indicates that the FmTransmitter has allocated resources and is ready to
     * instantly transmit FM radio.
     */
    public static final int STATE_PAUSED = 3;

    /**
     * Indicates that the FmTransmitter is scanning. FM radio will not be
     * transmitted in this state.
     */
    public static final int STATE_SCANNING = 4;

    /**
     * Returns true if the FM transmitter API is supported by the system.
     */
    public static boolean isApiSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_RADIO_FM_TRANSMITTER);
    }

    /**
     * Starts reception of the FM hardware. This is an asynchronous method since
     * different hardware can have varying startup times. When the reception is
     * started a callback to {@link OnStartedListener#onStarted()} is made.
     * <p>
     * When calling this method, an FmBand parameter must be passed that
     * describes the properties of the band that the FmTransmitter should
     * prepare for. If the band is null, invalid or not supported, an exception
     * will be thrown.
     * </p>
     * <p>
     * If the FmReceiver is active it will be forced to reset. See
     * {@link FmReceiver#RESET_TX_IN_USE}.
     * </p>
     *
     * @param band
     *            the band to use
     * @throws IllegalArgumentException
     *             if the band is null
     * @throws UnsupportedOperationException
     *             if the band is not supported by the hardware
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to start
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     * @see FmBand
     */
    public abstract void startAsync(FmBand band) throws IOException;

    /**
     * Starts reception of the FM hardware. This is a synchronous method and the
     * method call will block until the hardware is started.
     * <p>
     * When calling this method, an FmBand parameter must be passed that
     * describes the properties of the band that the FmTransmitter should
     * prepare for. If the band is null, invalid or not supported, an exception
     * will be thrown.
     * </p>
     * <p>
     * If the FmReceiver is active it will be forced to reset. See
     * {@link FmReceiver#RESET_TX_IN_USE}.
     * </p>
     *
     * @param band
     *            the band to use
     * @throws IllegalArgumentException
     *             if the band is null
     * @throws UnsupportedOperationException
     *             if the band is not supported by the hardware
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to start
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     * @see FmBand
     */
    public abstract void start(FmBand band) throws IOException;

    /**
     * Resumes FM transmission.
     * <p>
     * Calling this method when the FmTransmitter is in started state has no
     * affect.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to resume
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void resume() throws IOException;

    /**
     * Pauses FM transmission. No signals are sent when the FmTransmitter is
     * paused. Call {@link #resume()} to resume transmission. The hardware
     * should be able to start transmission quickly from the paused state to
     * give a good user experience.
     * <p>
     * Note that the hardware provider may choose to turn off the hardware after
     * being paused a certain amount of time to save power. This will be
     * reported in {@link OnForcedResetListener#onForcedReset(int)} with reason
     * {@link #RESET_NON_CRITICAL} and the FmTransmitter will be set to the idle
     * state.
     * </p>
     * <p>
     * Calling this method when the FmTransmitter is in paused state has no
     * affect.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to pause
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void pause() throws IOException;

    /**
     * Resets the FmTransmitter to its idle state.
     *
     * @throws IOException
     *             if the FM hardware failed to reset
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void reset() throws IOException;

    /**
     * Returns the state of the FmTransmitter.
     *
     * @return One of {@link #STATE_IDLE}, {@link #STATE_STARTING},
     *         {@link #STATE_STARTED}, {@link #STATE_PAUSED},
     *         {@link #STATE_SCANNING}
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract int getState();

    /**
     * Sets the output frequency. The frequency must be within the band that the
     * FmTransmitter prepared for.
     *
     * @param frequency
     *            the output frequency to use in kHz
     * @throws IllegalArgumentException
     *             if the frequency is not supported
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to set frequency
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void setFrequency(int frequency) throws IOException;

    /**
     * Returns the output frequency.
     *
     * @return the output frequency in kHz
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to get the frequency
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract int getFrequency() throws IOException;

    /**
     * Sets the RDS data to transmit. See RDS table in FmReceiver for data that
     * can be set.
     *
     * @param rdsData
     *            the RDS data to transmit, set to null to disable RDS
     *            transmission
     * @throws IllegalArgumentException
     *             if the rdsData parameter has invalid syntax
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void setRdsData(Bundle rdsData);

    /**
     * Returns true if the hardware/implementation supports block scan. If true
     * the {@link FmTransmitter#startBlockScan(int, int)} will work.
     * <p>
     * The motivation for having this function is that an application can take
     * this capability into account when laying out its UI.
     * </p>
     *
     * @return true if block scan is supported by the FmTransmitter, false
     *         otherwise
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract boolean isBlockScanSupported();

    /**
     * Starts a block scan. The tuner will scan the frequency band between
     * startFrequency and endFrequency for unused frequencies. The application
     * should register for callbacks using
     * {@link #addOnScanListener(OnScanListener)} to receive a callback when
     * frequencies are found.
     * <p>
     * If the application wants to stop the block scan, a call to
     * {@link #stopScan()} should be made.
     * </p>
     *
     * @param startFrequency
     *            the frequency to start the block scan
     * @param endFrequency
     *            the frequency to end the block scan
     * @throws IllegalArgumentException
     *             if the startFrequency or endFrequency it not within the
     *             currently used FmBand
     * @throws UnsupportedOperationException
     *             if the hardware/implementation does not supports block scan
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void startBlockScan(int startFrequency, int endFrequency);

    /**
     * Stops performing a scan operation. The hardware might continue the scan
     * for an unspecified amount of time after this method is called. Once the
     * scan has stopped, it will be notified via {@link OnScanListener}.
     * <p>
     * Note that this method has no affect if called in other states than the
     * scanning state.
     * </p>
     *
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void stopScan();

    /**
     * This method can be used to send vendor specific commands. These commands
     * must not follow any common design for all vendors, and information about
     * the commands that a vendor implements is out of scope in this API.
     * <p>
     * However, one command must be supported by all vendors that implements
     * vendor specific commands, the <i>vendor_information</i> command. In the
     * Bundle parameter in
     * {@link OnExtraCommandListener#onExtraCommand(String, Bundle)} the FM
     * radio device name and version can be extracted according to the table
     * below.
     * </p>
     * <table border="1">
     * <tr>
     * <th>key name</th>
     * <th>value type</th>
     * </tr>
     * <tr>
     * <td>device_name</td>
     * <td>string</td>
     * </tr>
     * <tr>
     * <td>device_version</td>
     * <td>string</td>
     * </tr>
     * </table>
     *
     * @param command
     *            the command to send
     * @param extras
     *            extra parameters to the command
     * @return true if the command was accepted, otherwise false
     *
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract boolean sendExtraCommand(String command, String[] extras);

    /**
     * Register a callback to be invoked when the FmTransmitter is started.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnStartedListener(OnStartedListener listener);

    /**
     * Unregister a callback to be invoked when the FmTransmitter is started.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnStartedListener(OnStartedListener listener);

    /**
     * Register a callback to be invoked during a scan.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnScanListener(OnScanListener listener);

    /**
     * Unregister a callback to be invoked during a scan.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnScanListener(OnScanListener listener);

    /**
     * Register a callback to be invoked when an error has happened during an
     * asynchronous operation.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnErrorListener(OnErrorListener listener);

    /**
     * Unregister a callback to be invoked when an error has happened during an
     * asynchronous operation.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnErrorListener(OnErrorListener listener);

    /**
     * Register a callback to be invoked when the FmTransmitter is forced to
     * pause due to external reasons.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnForcedPauseListener(OnForcedPauseListener listener);

    /**
     * Unregister a callback to be invoked when the FmTransmitter is forced to
     * pause due to external reasons.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnForcedPauseListener(OnForcedPauseListener listener);

    /**
     * Register a callback to be invoked when the FmTransmitter is forced to
     * reset due to external reasons.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnForcedResetListener(OnForcedResetListener listener);

    /**
     * Unregister a callback to be invoked when the FmTransmitter is forced to
     * reset due to external reasons.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnForcedResetListener(OnForcedResetListener listener);

    /**
     * Register a callback to be invoked when the FmTransmitter changes state.
     * Having a listener registered to this method may cause frequent callbacks,
     * hence it is good practice to only have a listener registered for this
     * when necessary.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnStateChangedListener(OnStateChangedListener listener);

    /**
     * Unregister a callback to be invoked when the FmTransmitter changes state.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnStateChangedListener(OnStateChangedListener listener);

    /**
     * Register a callback to be invoked when the FmTransmitter want's to invoke
     * a vendor specific callback.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void addOnExtraCommandListener(OnExtraCommandListener listener);
    /**
     * Unregister a callback to be invoked when the FmTransmitter want's to
     * invoke a vendor specific callback.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_TRANSMITTER permission is not present
     */
    public abstract void removeOnExtraCommandListener(OnExtraCommandListener listener);

    /**
     * Interface definition of a callback to be invoked when the FmTransmitter
     * is started.
     */
    public interface OnStartedListener {
        /**
         * Called when the FmTransmitter is started. The FmTransmitter is now
         * transmitting FM radio.
         */
        void onStarted();
    }

    /**
     * Interface definition of a callback to be invoked when a scan operation is
     * complete.
     */
    public interface OnScanListener {
        /**
         * Called when the block scan is completed.
         * <p>
         * If the block scan is aborted with stopScan, this will be indicated
         * with the aborted argument.
         * <p>
         * If an error occurs during a block scan, it will be reported via
         * {@link OnErrorListener#onError()} and this method callback will not
         * be invoked.
         * </p>
         *
         * @param frequency
         *            the frequency in kHz where the channel was found
         * @param signalStrength
         *            the signal strength, 0-1000
         * @param aborted
         *            true if the block scan was aborted, false otherwise
         */
        void onBlockScan(int[] frequency, int[] signalStrength, boolean aborted);
    }

    /**
     * Interface definition of a callback to be invoked when there has been an
     * error during an asynchronous operation.
     */
    public interface OnErrorListener {
        /**
         * Called to indicate an error.
         */
        void onError();
    }

    /**
     * Interface definition of a callback to be invoked when the FmTransmitter
     * was forced to pause due to external reasons.
     */
    public interface OnForcedPauseListener {
        /**
         * Called when an external reason caused the FmTransmitter to pause.
         * When this callback is received, the FmTransmitter is still able to
         * resume transmission by calling {@link FmTransmitter#resume()}.
         */
        void onForcedPause();
    }

    /**
     * Interface definition of a callback to be invoked when the FmTransmitter
     * was forced to reset due to external reasons.
     */
    public interface OnForcedResetListener {
        /**
         * Called when an external reason caused the FmTransmitter to reset. The
         * application that uses the FmTransmitter should take action according
         * to the reason for resetting.
         *
         * @param reason
         *            reason why the FmTransmitter reset:
         *            <ul>
         *            <li>{@link FmTransmitter#RESET_NON_CRITICAL}
         *            <li>{@link FmTransmitter#RESET_CRITICAL}
         *            <li>{@link FmTransmitter#RESET_RX_IN_USE}
         *            <li>{@link FmTransmitter#RESET_RADIO_FORBIDDEN}
         *            </ul>
         */
        void onForcedReset(int reason);
    }

    /**
     * Interface definition of a callback to be invoked when the FmTransmitter
     * changes state.
     */
    public interface OnStateChangedListener {
        /**
         * Called when the state is changed in the FmTransmitter. This is useful
         * if an application want's to monitor the FmTransmitter state.
         *
         * @param oldState
         *            the old state of the FmTransmitter
         * @param newState
         *            the new state of the FmTransmitter
         */
        void onStateChanged(int oldState, int newState);
    }

    /**
     * Interface definition of a callback to be invoked when the FmTransmitter
     * responds to a vendor specific command.
     */
    public interface OnExtraCommandListener {
        /**
         * Called when the FmTransmitter responds to a vendor specific command.
         *
         * @param response
         *            the command the FmTransmitter responds to
         * @param extras
         *            extra parameters to the command
         */
        void onExtraCommand(String response, Bundle extras);
    }
}
