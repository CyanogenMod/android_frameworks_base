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
 * The FmReceiver controls reception of FM radio. This API enables an
 * application to tune/scan for channels, receive RDS data, etc. The unit for
 * all frequencies in this class is kHz. Note that this API only controls the
 * reception of FM radio, to play FM radio the MediaPlayer interfaces should be
 * used, see code example below the state diagram.
 * <p>
 * Get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String)
 * Context.getSystemService("fm_receiver")}.
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
 * <img src="../../../../images/FmReceiver_states.gif"
 * alt="FmReceiver State diagram" border="0" />
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
 * <td>{starting, paused, started, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the starting state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #start(FmBand)}</td>
 * <td>{idle}</td>
 * <td>{starting, paused, started, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the started state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #resume()}</td>
 * <td>{paused, started}</td>
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
 * <td>{@link #isRDSDataSupported()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #isTunedToValidChannel()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #setThreshold(int)}</td>
 * <td>{started, paused, scanning}</td>
 * <td>{idle, starting}</td>
 * <td>Calling this method in an invalid state throws an IllegalStateException.
 * </td>
 * </tr>
 * <tr>
 * <td>{@link #getThreshold()}</td>
 * <td>{started, paused, scanning}</td>
 * <td>{idle, starting}</td>
 * <td>Calling this method in an invalid state throws an IllegalStateException.
 * </td>
 * </tr>
 * <tr>
 * <td>{@link #getFrequency()}</td>
 * <td>{paused, started}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state does not change the
 * object state. Calling this method in an invalid state throws an
 * IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #getSignalStrength()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #isPlayingInStereo()}</td>
 * <td>any</td>
 * <td>{}</td>
 * <td>This method can be called in any state and calling it does not change the
 * object state.</td>
 * </tr>
 * <tr>
 * <td>{@link #setForceMono(boolean)}</td>
 * <td>{started, paused, scanning}</td>
 * <td>{idle, starting}</td>
 * <td>Calling this method in an invalid state throws an IllegalStateException.
 * </td>
 * </tr>
 * <tr>
 * <td>{@link #setAutomaticAFSwitching(boolean)}</td>
 * <td>{started, paused, scanning}</td>
 * <td>{idle, starting}</td>
 * <td>Calling this method in an invalid state throws an IllegalStateException.
 * </td>
 * </tr>
 * <tr>
 * <td>{@link #setAutomaticTASwitching(boolean)}</td>
 * <td>{started, paused, scanning}</td>
 * <td>{idle, starting}</td>
 * <td>Calling this method in an invalid state throws an IllegalStateException.
 * </td>
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
 * <td>{@link #startFullScan()}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the scanning state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #scanUp()}</td>
 * <td>{started, paused}</td>
 * <td>{idle, starting, scanning}</td>
 * <td>Successful invocation of this method in a valid state transfers the
 * object to the scanning state. Calling this method in an invalid state throws
 * an IllegalStateException.</td>
 * </tr>
 * <tr>
 * <td>{@link #scanDown()}</td>
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
 * // start receiving FM radio
 * FmReceiver fmr = (FmReceiver) getSystemService("fm_receiver");
 * fmr.start(new FmBand(FmBand.BAND_EU));
 *
 * // prepare and start playback
 * MediaPlayer mp = new MediaPlayer();
 * mp.setDataSource(&quot;fmradio://rx&quot;);
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
 * <li>If start is called on FmReceiver and the FmTransmitter is <i>active</i>,
 * the FmTransmitter MUST release resources and change state to idle.</li>
 * <li>The FmTransmitter will in that case be notified by
 * {@link com.stericsson.hardware.fm.FmTransmitter.OnForcedResetListener#onForcedReset(int)}.</li>
 * </ul>
 * </p>
 * <a name="RDSHandling"></a> <h3>Receiving/transmitting RDS data</h3>
 * <p>
 * RDS data can be received by setting the
 * {@link #addOnRDSDataFoundListener(OnRDSDataFoundListener)}. When RDS data is
 * available the data can be extracted from the Bundle object in
 * {@link OnRDSDataFoundListener#onRDSDataFound(Bundle, int)} according to the
 * table below. This table can also be used when transmitting RDS data with the
 * FmTransmitter.
 * </p>
 * <table border="1">
 * <tr>
 * <th>RDS description</th>
 * <th>key name</th>
 * <th>value type</th>
 * <th>value description</th>
 * </tr>
 * <tr>
 * <td>Program Identification code</td>
 * <td>PI</td>
 * <td>short</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>Traffic Program Identification code</td>
 * <td>TP</td>
 * <td>short</td>
 * <td>1 bit</td>
 * </tr>
 * <tr>
 * <td>Program Type code</td>
 * <td>PTY</td>
 * <td>short</td>
 * <td>5 bits</td>
 * </tr>
 * <tr>
 * <td>Traffic Announcement code</td>
 * <td>TA</td>
 * <td>short</td>
 * <td>1 bit</td>
 * </tr>
 * <tr>
 * <td>Music/Speech switch code</td>
 * <td>M/S</td>
 * <td>short</td>
 * <td>1 bit</td>
 * </tr>
 * <tr>
 * <td>Alternative Frequency</td>
 * <td>AF</td>
 * <td>int[]</td>
 * <td>kHz</td>
 * </tr>
 * <tr>
 * <td>Program service name</td>
 * <td>PSN</td>
 * <td>string</td>
 * <td>8 chars</td>
 * </tr>
 * <tr>
 * <td>Radio text</td>
 * <td>RT</td>
 * <td>string</td>
 * <td>64 chars</td>
 * </tr>
 * <tr>
 * <td>Clock-time and date</td>
 * <td>CT</td>
 * <td>string</td>
 * <td>Yr:mo:dy:hr:min</td>
 * </tr>
 * <tr>
 * <td>Program Type name</td>
 * <td>PTYN</td>
 * <td>string</td>
 * <td>8 chars</td>
 * </tr>
 * <tr>
 * <td>Traffic Message Channel</td>
 * <td>TMC</td>
 * <td>short[]</td>
 * <td>X:Y:Z -> 5+16+16 bits</td>
 * </tr>
 * <tr>
 * <td>TA Frequency</td>
 * <td>TAF</td>
 * <td>int</td>
 * <td>kHz</td>
 * </tr>
 * </table>
 * <p>
 * The RDS specification can be found <a
 * href="http://www.rds.org.uk/rds98/pdf/IEC%2062106-E_no%20print.pdf">here</a>
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
public abstract class FmReceiver {

    /**
     * The FmReceiver had to be shut down due to a non-critical error, meaning
     * that it is OK to attempt a restart immediately after this. For example,
     * if the hardware was shut down in order to save power after being in the
     * paused state for too long.
     */
    public static final int RESET_NON_CRITICAL = 0;

    /**
     * The FmReceiver had to be shut down due to a critical error. The FM
     * hardware it not guaranteed to work as expected after receiving this
     * error.
     */
    public static final int RESET_CRITICAL = 1;

    /**
     * The FmTransmitter was activated and therefore the FmReceiver must be put
     * in idle.
     *
     * @see FmTransmitter#startAsync(FmBand)
     */
    public static final int RESET_TX_IN_USE = 2;

    /**
     * The radio is not allowed to be used, typically when flight mode is
     * enabled.
     */
    public static final int RESET_RADIO_FORBIDDEN = 3;

    /**
     * Indicates that the FmReceiver is in an idle state. No resources are
     * allocated and power consumption is kept to a minimum.
     */
    public static final int STATE_IDLE = 0;

    /**
     * Indicates that the FmReceiver is allocating resources and preparing to
     * receive FM radio.
     */
    public static final int STATE_STARTING = 1;

    /**
     * Indicates that the FmReceiver is receiving FM radio. Note that the
     * FmReceiver is considered to be started even if it is receiving noise or
     * gets a signal with not good enough quality to consider a valid channel.
     */
    public static final int STATE_STARTED = 2;

    /**
     * Indicates that the FmReceiver has allocated resources and is ready to
     * instantly receive FM radio.
     */
    public static final int STATE_PAUSED = 3;

    /**
     * Indicates that the FmReceiver is scanning. FM radio will not be received
     * in this state.
     */
    public static final int STATE_SCANNING = 4;

    /**
     * Unknown signal strength.
     */
    public static final int SIGNAL_STRENGTH_UNKNOWN = -1;

    /**
     * The frequency switch occurred as a stronger alternate frequency was
     * found.
     */
    public static final int SWITCH_AF = 0;

    /**
     * The frequency switch occurred as there is a traffic announcement
     * in progress.
     */
    public static final int SWITCH_TA = 1;

    /**
     * The frequency switch occurred at the cessation of a traffic
     * announcement.
     */
    public static final int SWITCH_TA_END = 2;

    /**
     * Scan direction down towards lower frequencies.
     */
    public static final int SCAN_DOWN = 0;

    /**
     * Scan direction up towards higher frequencies.
     */
    public static final int SCAN_UP = 1;

    /**
     * Returns true if the FM receiver API is supported by the system.
     */
    public static boolean isApiSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
        PackageManager.FEATURE_RADIO_FM_RECEIVER);
    }

    /**
     * Starts reception of the FM hardware. This is an asynchronous method since
     * different hardware can have varying startup times. When the reception is
     * started a callback to {@link OnStartedListener#onStarted()} is made.
     * <p>
     * When calling this method, an FmBand parameter must be passed that
     * describes the properties of the band that the FmReceiver should prepare
     * for. If the band is null, invalid or not supported, an exception will be
     * thrown.
     * </p>
     * <p>
     * If the FmTransmitter is active it will be forced to reset. See
     * {@link FmTransmitter#RESET_RX_IN_USE}.
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
     *             if the FM_RADIO_RECEIVER permission is not present
     * @see FmBand
     */
    public abstract void startAsync(FmBand band) throws IOException;

    /**
     * Starts reception of the FM hardware. This is a synchronous method and the
     * method call will block until the hardware is started.
     * <p>
     * When calling this method, an FmBand parameter must be passed that
     * describes the properties of the band that the FmReceiver should prepare
     * for. If the band is null, invalid or not supported, an exception will be
     * thrown.
     * </p>
     * <p>
     * If the FmTransmitter is active it will be forced to reset. See
     * {@link FmTransmitter#RESET_RX_IN_USE}.
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
     *             if the FM_RADIO_RECEIVER permission is not present
     * @see FmBand
     */
    public abstract void start(FmBand band) throws IOException;

    /**
     * Resumes FM reception.
     * <p>
     * Calling this method when the FmReceiver is in started state has no
     * affect.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to resume
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void resume() throws IOException;

    /**
     * Pauses FM reception. No FM radio is received as long as the FmReceiver is
     * paused. Call {@link #resume()} to resume reception. The hardware should
     * be able to resume reception quickly from the paused state to give a good
     * user experience.
     * <p>
     * Note that the hardware provider may choose to turn off the hardware after
     * being paused a certain amount of time to save power. This will be
     * reported in {@link OnForcedResetListener#onForcedReset(int)} with reason
     * {@link #RESET_NON_CRITICAL} and the FmReceiver will be set to the idle
     * state.
     * </p>
     * <p>
     * Calling this method when the FmReceiver is in paused state has no affect.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to pause
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void pause() throws IOException;

    /**
     * Resets the FmReceiver to its idle state.
     *
     * @throws IOException
     *             if the FM hardware failed to reset
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void reset() throws IOException;

    /**
     * Returns the state of the FmReceiver.
     *
     * @return One of {@link #STATE_IDLE}, {@link #STATE_STARTING},
     *         {@link #STATE_STARTED}, {@link #STATE_PAUSED},
     *         {@link #STATE_SCANNING}
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract int getState();

    /**
     * Returns true if the hardware/implementation supports RDS data. If true
     * the {@link OnRDSDataFoundListener} will work. If not it will never report
     * any data.
     * <p>
     * The motivation for having this function is that an application can take
     * this capability into account when laying out its UI.
     * </p>
     *
     * @return true if RDS data is supported by the FmReceiver, false otherwise
     *
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract boolean isRDSDataSupported();

    /**
     * Checks if the tuned frequency is considered to contain a channel.
     *
     * @return true if the FmReceiver is tuned to a valid channel
     *
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract boolean isTunedToValidChannel();

    /**
     * Sets the threshold for the tuner. The threshold can be 0-1000. A low
     * threshold indicates that the tuner will find stations with a weak signal
     * and a high threshold will find stations with a strong signal.
     * <p>
     * This is used then calling {@link FmReceiver#scanUp()},
     * {@link FmReceiver#scanDown()} or {@link FmReceiver#startFullScan()}.
     * </p>
     *
     * @param threshold
     *            a value between 0-1000
     * @throws IllegalArgumentException
     *             if the value is not between 0-1000
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to set threshold
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void setThreshold(int threshold) throws IOException;

    /**
     * Returns the threshold for the tuner.
     *
     * @return the threshold for the tuner
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to get the threshold
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract int getThreshold() throws IOException;

    /**
     * Returns the tuned frequency.
     *
     * @return the tuned frequency in kHz
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to get the frequency
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract int getFrequency() throws IOException;

    /**
     * Returns the signal strength of the tuned frequency. The signal strength
     * is a value from 0 to 1000. A high value indicates a strong signal and a
     * low value indicates a weak signal.
     *
     * @return the signal strength or {@link #SIGNAL_STRENGTH_UNKNOWN}
     *
     * @throws IOException
     *             if the FM hardware failed to get the signal strength
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract int getSignalStrength() throws IOException;

    /**
     * Checks if the tuned frequency is played in stereo. If
     * {@link #setForceMono(boolean)} is set, this method will always return
     * false.
     *
     * @return true if the tuned frequency is playing in stereo
     *
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract boolean isPlayingInStereo();

    /**
     * Force the playback to always be in mono.
     *
     * @param forceMono
     *            if true, the hardware will only output mono audio. If false,
     *            stereo is allowed if supported by hardware and signal.
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void setForceMono(boolean forceMono);

    /**
     * Sets the automatic switching of the FmReceiver in the case of a stronger
     * transmitter with the same Programme Identification (PI) presence. The
     * application should register for callbacks using
     * {@link #addOnAutomaticSwitchListener(OnAutomaticSwitchListener)}
     * to receive a callback when channels are found. The reason stated in
     * the callback will be {@link FmReceiver#SWITCH_AF}.
     *
     * @param automatic
     *            enable or disable automatic switching
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void setAutomaticAFSwitching(boolean automatic);

    /**
     * Sets the automatic switching of the program in case of the presence of
     * traffic announcement in another program. The application should register
     * for callbacks using {@link #addOnAutomaticSwitchListener(OnAutomaticSwitchListener)}
     * to receive a callback when channels are found. The reason stated in
     * the callback will be {@link FmReceiver#SWITCH_TA} when switching to
     * traffic announcement and {@link FmReceiver#SWITCH_TA_END} when switching
     * back after the announcement.
     *
     * @param automatic
     *            enable or disable automatic switching
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void setAutomaticTASwitching(boolean automatic);

    /**
     * Sets the frequency. Unlike {@link #scanUp()} and {@link #scanDown()},
     * this method will directly jump to the specified frequency instead of
     * trying to find a channel while scanning.
     * <p>
     * The frequency must be within the band that the FmReceiver prepared for.
     * </p>
     *
     * @param frequency
     *            the frequency to tune to in kHz
     * @throws IllegalArgumentException
     *             if the frequency is not supported
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws IOException
     *             if the FM hardware failed to set frequency
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     * @see FmBand
     */
    public abstract void setFrequency(int frequency) throws IOException;

    /**
     * Starts a full scan. The tuner will scan the entire frequency band for
     * channels. The application should register for callbacks using
     * {@link #addOnScanListener(OnScanListener)} to receive a callback when
     * channels are found.
     * <p>
     * If the application wants to stop the full scan, a call to
     * {@link #stopScan()} should be made.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void startFullScan();

    /**
     * Starts seeking for a channel downwards in the frequency band from the
     * currently tuned frequency. When a channel with enough signal strength is
     * found the scanning will stop.
     * <p>
     * The seek will always stop if it reaches back to the frequency it started
     * from, meaning that in the worst case scenario, when no channel can be
     * found, the seek will run through one full cycle of the frequency band
     * and stop at the frequency it started from.
     * </p>
     * The application should register for callbacks using
     * {@link #addOnScanListener(OnScanListener)} to receive a callback when the
     * scan is complete.
     * <p>
     * If the application wants to stop the scan, a call to {@link #stopScan()}
     * should be made.
     * </p>
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     * @see FmReceiver#scanUp()
     */
    public abstract void scanDown();

    /**
     * Same as {@link #scanDown()} but seeks upwards in the frequency band.
     *
     * @throws IllegalStateException
     *             if it is called in an invalid state
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     * @see FmReceiver#scanDown()
     */
    public abstract void scanUp();

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
     *             if the FM_RADIO_RECEIVER permission is not present
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
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract boolean sendExtraCommand(String command, String[] extras);

    /**
     * Register a callback to be invoked when the FmReceiver is started.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnStartedListener(OnStartedListener listener);

    /**
     * Unregister a callback to be invoked when the FmReceiver is started.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnStartedListener(OnStartedListener listener);

    /**
     * Register a callback to be invoked during a scan.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnScanListener(OnScanListener listener);

    /**
     * Unregister a callback to be invoked during a scan.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnScanListener(OnScanListener listener);

    /**
     * Register a callback to be invoked when RDS data is found. Having a
     * listener registered for this might cause continuous callbacks, so it is
     * considered good practice to set this listener to null whenever the
     * application is not interested in these updates, e.g. when the application
     * UI is not visible.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnRDSDataFoundListener(OnRDSDataFoundListener listener);

    /**
     * Unregister a callback to be invoked when RDS data is found.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnRDSDataFoundListener(OnRDSDataFoundListener listener);

    /**
     * Register a callback to be invoked when an error has happened during an
     * asynchronous operation.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnErrorListener(OnErrorListener listener);

    /**
     * Unregister a callback to be invoked when an error has happened during an
     * asynchronous operation.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnErrorListener(OnErrorListener listener);

    /**
     * Register a callback to be invoked when the signal strength of the
     * currently tuned frequency changes. Having a listener registered to this
     * method may cause frequent callbacks, hence it is good practice to only
     * have a listener registered for this when necessary.
     * <p>
     * Example: If the application uses this information to visualize the signal
     * strength on the UI, it should unregister the listener whenever the UI is
     * not visible.
     * </p>
     * <p>
     * The listener will only receive callbacks when the signal strength
     * changes.
     * </p>
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnSignalStrengthChangedListener(OnSignalStrengthChangedListener listener);

    /**
     * Unregister a callback to be invoked when the signal strength of the
     * currently tuned frequency changes.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnSignalStrengthChangedListener(OnSignalStrengthChangedListener listener);

    /**
     * Register a callback to be invoked when playback of the tuned frequency
     * changes between mono and stereo. Having a listener registered to this
     * method may cause frequent callbacks, hence it is good practice to only
     * have a listener registered for this when necessary.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnPlayingInStereoListener(OnPlayingInStereoListener listener);

    /**
     * Unregister a callback to be invoked when playback of the tuned frequency
     * changes between mono and stereo.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnPlayingInStereoListener(OnPlayingInStereoListener listener);

    /**
     * Register a callback to be invoked when the FmReceiver is forced to pause
     * due to external reasons.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnForcedPauseListener(OnForcedPauseListener listener);

    /**
     * Unregister a callback to be invoked when the FmReceiver is forced to
     * pause due to external reasons.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnForcedPauseListener(OnForcedPauseListener listener);

    /**
     * Register a callback to be invoked when the FmReceiver is forced to reset
     * due to external reasons.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnForcedResetListener(OnForcedResetListener listener);

    /**
     * Unregister a callback to be invoked when the FmReceiver is forced to
     * reset due to external reasons.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnForcedResetListener(OnForcedResetListener listener);

    /**
     * Register a callback to be invoked when the FmReceiver changes state.
     * Having a listener registered to this method may cause frequent callbacks,
     * hence it is good practice to only have a listener registered for this
     * when necessary.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnStateChangedListener(OnStateChangedListener listener);

    /**
     * Unregister a callback to be invoked when the FmReceiver changes state.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnStateChangedListener(OnStateChangedListener listener);

    /**
     * Register a callback to be invoked when the FmReceiver want's to invoke a
     * vendor specific callback.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnExtraCommandListener(OnExtraCommandListener listener);

    /**
     * Unregister a callback to be invoked when the FmReceiver want's to invoke
     * a vendor specific callback.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnExtraCommandListener(OnExtraCommandListener listener);

    /**
     * Register a callback to be invoked when the FmReceiver has triggered
     * a changed frequency.
     *
     * @param listener
     *            the callback that will be run
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void addOnAutomaticSwitchListener(OnAutomaticSwitchListener listener);

    /**
     * Unregister  a callback to be invoked when the FmReceiver has triggered
     * a changed frequency.
     *
     * @param listener
     *            the callback to remove
     * @throws SecurityException
     *             if the FM_RADIO_RECEIVER permission is not present
     */
    public abstract void removeOnAutomaticSwitchListener(OnAutomaticSwitchListener listener);

    /**
     * Interface definition of a callback to be invoked when the FmReceiver is
     * started.
     */
    public interface OnStartedListener {
        /**
         * Called when the FmReceiver is started. The FmReceiver is now
         * receiving FM radio.
         */
        void onStarted();
    }

    /**
     * Interface definition of a callback to be invoked when a scan operation is
     * complete.
     */
    public interface OnScanListener {
        /**
         * Called when the full scan is completed.
         * <p>
         * If the full scan is aborted with stopScan, this will be indicated
         * with the aborted argument.
         * <p>
         * If an error occurs during a full scan, it will be reported via
         * {@link OnErrorListener#onError()} and this method callback will not
         * be invoked.
         * </p>
         *
         * @param frequency
         *            the frequency in kHz where the channel was found
         * @param signalStrength
         *            the signal strength, 0-1000
         * @param aborted
         *            true if the full scan was aborted, false otherwise
         */
        void onFullScan(int[] frequency, int[] signalStrength, boolean aborted);

        /**
         * Called when {@link FmReceiver#scanDown()} or
         * {@link FmReceiver#scanUp()} has successfully completed a scan
         * operation. Note that failing to find a channel during a scan
         * operation does not mean that it is an error, and it will still result
         * in a call to this interface.
         * <p>
         * If the scan is aborted with stopScan, this will be indicated with the
         * aborted argument.
         * <p>
         *
         * @param tunedFrequency
         *            the current frequency in kHz of the tuner after the scan
         *            operation was completed
         * @param signalStrength
         *            the signal strength, 0-1000
         * @param scanDirection
         *            direction of scan, SCAN_DOWN or SCAN_UP
         * @param aborted
         *            true if the scan was aborted, false otherwise
         */
        void onScan(int tunedFrequency, int signalStrength, int scanDirection, boolean aborted);
    }

    /**
     * Interface definition of a callback to be invoked when RDS data has been
     * found. Note that there is not necessarily a relation between the
     * frequency that the RDS data is found at and the currently tuned
     * frequency.
     */
    public interface OnRDSDataFoundListener {
        /**
         * Called when RDS data has been found or updated.
         *
         * @param rdsData
         *            the RDS data that was found
         * @param frequency
         *            the frequency where the RDS data was found
         */
        void onRDSDataFound(Bundle rdsData, int frequency);
    };

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
     * Interface definition of a callback to be invoked when the signal strength
     * of the currently tuned frequency changes.
     */
    public interface OnSignalStrengthChangedListener {
        /**
         * Called to indicate that the signal strength has changed.
         *
         * @param signalStrength
         *            the signal strength, 0-1000
         */
        void onSignalStrengthChanged(int signalStrength);
    }

    /**
     * Interface definition of a callback to be invoked when playback of the
     * tuned frequency changes between mono and stereo. This is useful if the
     * application wants to display some icon that shows if playing in stereo or
     * not.
     */
    public interface OnPlayingInStereoListener {
        /**
         * Called when switching between mono and stereo.
         *
         * @param inStereo
         *            true if playback is in stereo, false if in mono
         */
        void onPlayingInStereo(boolean inStereo);
    }

    /**
     * Interface definition of a callback to be invoked when the FmReceiver was
     * forced to pause due to external reasons.
     */
    public interface OnForcedPauseListener {
        /**
         * Called when an external reason caused the FmReceiver to pause. When
         * this callback is received, the FmReceiver is still able to resume
         * reception by calling {@link FmReceiver#resume()}.
         */
        void onForcedPause();
    }

    /**
     * Interface definition of a callback to be invoked when the FmReceiver was
     * forced to reset due to external reasons.
     */
    public interface OnForcedResetListener {
        /**
         * Called when an external reason caused the FmReceiver to reset. The
         * application that uses the FmReceiver should take action according to
         * the reason for resetting.
         *
         * @param reason
         *            reason why the FmReceiver reset:
         *            <ul>
         *            <li>{@link FmReceiver#RESET_NON_CRITICAL}
         *            <li>{@link FmReceiver#RESET_CRITICAL}
         *            <li>{@link FmReceiver#RESET_TX_IN_USE}
         *            <li>{@link FmReceiver#RESET_RADIO_FORBIDDEN}
         *            </ul>
         */
        void onForcedReset(int reason);
    }

    /**
     * Interface definition of a callback to be invoked when the FmReceiver
     * changes state.
     */
    public interface OnStateChangedListener {
        /**
         * Called when the state is changed in the FmReceiver. This is useful if
         * an application want's to monitor the FmReceiver state.
         *
         * @param oldState
         *            the old state of the FmReceiver
         * @param newState
         *            the new state of the FmReceiver
         */
        void onStateChanged(int oldState, int newState);
    }

    /**
     * Interface definition of a callback to be invoked when the FmReceiver
     * responds to a vendor specific command.
     */
    public interface OnExtraCommandListener {
        /**
         * Called when the FmReceiver responds to a vendor specific command.
         *
         * @param response
         *            the command the FmReceiver responds to
         * @param extras
         *            extra parameters to the command
         */
        void onExtraCommand(String response, Bundle extras);
    }

    /**
     * Interface definition of a callback to be invoked when the FmReceiver
     * changes frequency either due to AF switch or TA event.
     */
    public interface OnAutomaticSwitchListener {
        /**
         * Called when the FmReceiver changes frequency either due to AF
         * switch or TA event.
         *
         * @param newFrequency
         *            the frequency switched to
         * @param reason
         *            the reason for the switch:
         *            <ul>
         *            <li>{@link FmReceiver#SWITCH_AF}
         *            <li>{@link FmReceiver#SWITCH_TA}
         *            <li>{@link FmReceiver#SWITCH_TA_END}
         *            </ul>
         */
        void onAutomaticSwitch(int newFrequency, int reason);
    }
}
