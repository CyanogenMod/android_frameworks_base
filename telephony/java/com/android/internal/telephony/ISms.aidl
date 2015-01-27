/*
** Copyright (c) 2012, The Linux Foundation. All rights reserved.
** Not a Contribution.
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony;

import android.app.PendingIntent;
import com.android.internal.telephony.SmsRawData;

/** Interface for applications to access the ICC phone book.
 *
 * <p>The following code snippet demonstrates a static method to
 * retrieve the ISms interface from Android:</p>
 * <pre>private static ISms getSmsInterface()
            throws DeadObjectException {
    IServiceManager sm = ServiceManagerNative.getDefault();
    ISms ss;
    ss = ISms.Stub.asInterface(sm.getService("isms"));
    return ss;
}
 * </pre>
 */

interface ISms {
     void synthesizeMessages(String originatingAddress, String scAddress, in List<String> messages, long timestampMillis);
    /**
     * Retrieves all messages currently stored on ICC.
     *
     * @return list of SmsRawData of all sms on ICC
     */
     List<SmsRawData> getAllMessagesFromIccEf(String callingPkg);

    /**
     * Update the specified message on the ICC.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return success or not
     *
     */
     boolean updateMessageOnIccEf(String callingPkg, int messageIndex, int newStatus,
            in byte[] pdu);

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return success or not
     *
     */
    boolean copyMessageToIccEf(String callingPkg, int status, in byte[] pdu, in byte[] smsc);

    /**
     * Send a data SMS.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    void sendData(String callingPkg, in String destAddr, in String scAddr, in int destPort,
            in byte[] data, in PendingIntent sentIntent, in PendingIntent deliveryIntent);

    /**
     * Send a data SMS with orig port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param destPort the port to deliver the message to
     * @param origPort the port set by the sender
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    void sendDataWithOrigPort(String callingPkg, in String destAddr, in String scAddr,
        in int destPort, in int origPort, in byte[] data, in PendingIntent sentIntent,
        in PendingIntent deliveryIntent);


    /**
     * Send an SMS.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    void sendText(String callingPkg, in String destAddr, in String scAddr, in String text,
            in PendingIntent sentIntent, in PendingIntent deliveryIntent);

    /**
     * Send an SMS with options.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param priority Priority level of the message
     * @param validityPeriod Validity Period of the message in Minutes.
     */
    void sendTextWithOptions(String callingPkg, in String destAddr, in String scAddr,
            in String text, in PendingIntent sentIntent, in PendingIntent deliveryIntent,
            in int priority, in boolean isExpectMore, in int validityPeriod);

    /**
     * Send a multi-part text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    void sendMultipartText(String callingPkg, in String destinationAddress, in String scAddress,
            in List<String> parts, in List<PendingIntent> sentIntents,
            in List<PendingIntent> deliveryIntents);

    /**
     * Send a multi-part text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param priority Priority level of the message
     * @param validityPeriod Validity Period of the message in Minutes.
     */
    void sendMultipartTextWithOptions(String callingPkg, in String destinationAddress,
            in String scAddress, in List<String> parts, in List<PendingIntent> sentIntents,
            in List<PendingIntent> deliveryIntents, in int priority, in boolean isExpectMore,
            in int validityPeriod);

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #disableCellBroadcast(int)
     */
    boolean enableCellBroadcast(int messageIdentifier);

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcast(int)
     */
    boolean disableCellBroadcast(int messageIdentifier);

    /*
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable
     * a message identifier range, they must both disable it for the device
     * to stop receiving those messages.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #disableCellBroadcastRange(int, int)
     */
    boolean enableCellBroadcastRange(int startMessageId, int endMessageId);

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable
     * a message identifier range, they must both disable it for the device
     * to stop receiving those messages.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastRange(int, int)
     */
    boolean disableCellBroadcastRange(int startMessageId, int endMessageId);

    /**
     * Returns the premium SMS send permission for the specified package.
     * Requires system permission.
     */
    int getPremiumSmsPermission(String packageName);

    /**
     * Set the SMS send permission for the specified package.
     * Requires system permission.
     */
    void setPremiumSmsPermission(String packageName, int permission);

    /**
     * SMS over IMS is supported if IMS is registered and SMS is supported
     * on IMS.
     *
     * @return true if SMS over IMS is supported, false otherwise
     *
     * @see #getImsSmsFormat()
     */
    boolean isImsSmsSupported();

    /**
     * Gets SMS format supported on IMS.  SMS over IMS format is
     * either 3GPP or 3GPP2.
     *
     * @return android.telephony.SmsMessage.FORMAT_3GPP,
     *         android.telephony.SmsMessage.FORMAT_3GPP2
     *      or android.telephony.SmsMessage.FORMAT_UNKNOWN
     *
     * @see #isImsSmsSupported()
     */
    String getImsSmsFormat();

    /**
     * Get the capacity count of sms on Icc card.
     *
     * @return capacity of ICC
     */
    int getSmsCapacityOnIcc();
}
