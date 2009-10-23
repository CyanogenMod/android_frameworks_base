/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.bluetooth.obex;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;

/**
 * Bluetooth intents for OBEX operations: authorization, transfer progress, transfer completions.
 *
 * @hide
 */
public interface BluetoothObexIntent {
    /**
     * OBEX profile identifiers for PROFILE extra
     */
    public static final int PROFILE_OPP = 0;
    public static final int PROFILE_FTP = 1;

    /**
     * OBEX intent extras (parameters)
     */
    public static final String OBJECT_FILENAME =
        "android.bluetooth.obex.intent.OBJECT_FILENAME";
    public static final String OBJECT_TYPE =
        "android.bluetooth.obex.intent.OBJECT_TYPE";
    public static final String OBJECT_SIZE =
        "android.bluetooth.obex.intent.OBJECT_SIZE";
    public static final String ADDRESS =
        "android.bluetooth.obex.intent.ADDRESS";
    public static final String BYTES_TRANSFERRED =
        "android.bluetooth.obex.intent.BYTES_TRANSFERRED";
    public static final String SUCCESS =
        "android.bluetooth.obex.intent.SUCCESS";
    public static final String ERROR_MESSAGE =
        "android.bluetooth.obex.intent.ERROR_MESSAGE";
    public static final String PROFILE =
        "android.bluetooth.obex.intent.PROFILE";
    /**
     * Broadcasted to update listeners on OPP connection status.
     * <p>
     * Extras:
     * <ul>
     *   <li>SUCCESS (boolean)
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String CONNECT_STATUS_ACTION =
        "android.bluetooth.obex.intent.action.CONNECT_STATUS";

    /**
     * Broadcasted to update listeners on OBEX transfer progress.
     * <p>
     * Extras:
     * <ul>
     *   <li>OBJECT_FILENAME (string)
     *   <li>OBJECT_SIZE (int)
     *   <li>BYTES_TRANSFERRED (int)
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String PROGRESS_ACTION =
        "android.bluetooth.obex.intent.action.PROGRESS";

    /**
     * Broadcasted to indicate that an OBEX object receive is complete.
     * <p>
     * Extras:
     * <ul>
     *   <li>OBJECT_FILENAME (string) - filename containing OBEX object
     *   <li>PROFILE (int) - OBEX profile used to transfer object: PROFILE_OPP, PROFILE_FTP
     *   <li>SUCCESS (boolean) - true if successful, else false
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String RX_COMPLETE_ACTION =
        "android.bluetooth.obex.intent.action.RX_COMPLETE";

    /**
     * Broadcasted to indicate that an OBEX object transmit is complete.
     * <p>
     * Extras:
     * <ul>
     *   <li>OBJECT_FILENAME (string) - filename containing OBEX object
     *   <li>PROFILE (int) - OBEX profile used to transfer object: PROFILE_OPP, PROFILE_FTP
     *   <li>SUCCESS (boolean) - true if successful, else false
     *   <li>ERROR_MESSAGE (string) - Optional error message (valid if SUCCESS is false)
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String TX_COMPLETE_ACTION =
        "android.bluetooth.obex.intent.action.TX_COMPLETE";

    /**
     * Broadcasted to request authorization for an incoming OBEX transfer.
     * <p>
     * A receiver (e.g., the Bluetooth OPP 'controller') should act according to the user's
     * wishes, perhaps by prompting them to approve/reject the transfer.  obexAuthorizeComplete()
     * should be called with the authorization decision.
     * <p>
     * Extras:
     * <ul>
     *   <li>ADDRESS (string) - Bluetooth address of remote device
     *   <li>OBJECT_FILENAME (string) - Proposed filename to save OBEX object to
     *   <li>OBJECT_TYPE (string) - OBEX object type
     *   <li>OBJECT_SIZE (int)
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String AUTHORIZE_ACTION =
        "android.bluetooth.obex.intent.action.AUTHORIZE";

    /**
     * Broadcasted to indicate authorization for an incoming OBEX transfer has
     * been cancelled by the remote device.
     * <p>
     * Extras:
     * <ul>
     *   <li>OBJECT_FILENAME (string) - Filename containing OBEX object
     * </ul>
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String AUTHORIZE_CANCEL_ACTION =
        "android.bluetooth.obex.intent.action.AUTHORIZE_CANCEL";
}
