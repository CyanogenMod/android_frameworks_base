/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SmsCbMessage;
import android.telephony.SmsMessage;
import android.telephony.SmsMessage.SubmitPdu;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.telephony.SmsMessageBase.TextEncodingDetails;
import com.android.internal.util.HexDump;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static android.telephony.SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED;
import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF;

public abstract class SMSDispatcher extends Handler {
    static final String TAG = "SMS";    // accessed from inner class
    private static final String SEND_NEXT_MSG_EXTRA = "SendNextMsg";

    /** Permission required to receive SMS and SMS-CB messages. */
    public static final String RECEIVE_SMS_PERMISSION = "android.permission.RECEIVE_SMS";

    /** Permission required to receive ETWS and CMAS emergency broadcasts. */
    public static final String RECEIVE_EMERGENCY_BROADCAST_PERMISSION =
            "android.permission.RECEIVE_EMERGENCY_BROADCAST";

    /** Permission required to send SMS to short codes without user confirmation. */
    private static final String SEND_SMS_NO_CONFIRMATION_PERMISSION =
            "android.permission.SEND_SMS_NO_CONFIRMATION";

    /** Query projection for checking for duplicate message segments. */
    private static final String[] PDU_PROJECTION = new String[] {
            "pdu"
    };

    /** Query projection for combining concatenated message segments. */
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = new String[] {
            "pdu",
            "sequence",
            "destination_port"
    };

    private static final int PDU_COLUMN = 0;
    private static final int SEQUENCE_COLUMN = 1;
    private static final int DESTINATION_PORT_COLUMN = 2;

    /** New SMS received. */
    protected static final int EVENT_NEW_SMS = 1;

    /** SMS send complete. */
    protected static final int EVENT_SEND_SMS_COMPLETE = 2;

    /** Retry sending a previously failed SMS message */
    private static final int EVENT_SEND_RETRY = 3;

    /** Confirmation required for sending a large number of messages. */
    private static final int EVENT_SEND_LIMIT_REACHED_CONFIRMATION = 4;

    /** Send the user confirmed SMS */
    static final int EVENT_SEND_CONFIRMED_SMS = 5;  // accessed from inner class

    /** Don't send SMS (user did not confirm). */
    static final int EVENT_STOP_SENDING = 7;        // accessed from inner class

    protected final Phone mPhone;
    protected final Context mContext;
    protected final ContentResolver mResolver;
    protected final CommandsInterface mCm;
    protected final SmsStorageMonitor mStorageMonitor;
    protected final TelephonyManager mTelephonyManager;

    protected final WapPushOverSms mWapPush;

    private final MockSmsReceiver mMockSmsReceiver;

    protected static final Uri mRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    /** Maximum number of times to retry sending a failed SMS. */
    private static final int MAX_SEND_RETRIES = 3;
    /** Delay before next send attempt on a failed SMS, in milliseconds. */
    private static final int SEND_RETRY_DELAY = 2000;
    /** single part SMS */
    private static final int SINGLE_PART_SMS = 1;
    /** Message sending queue limit */
    private static final int MO_MSG_QUEUE_LIMIT = 5;

    /**
     * Message reference for a CONCATENATED_8_BIT_REFERENCE or
     * CONCATENATED_16_BIT_REFERENCE message set.  Should be
     * incremented for each set of concatenated messages.
     * Static field shared by all dispatcher objects.
     */
    private static int sConcatenatedRef = new Random().nextInt(256);

    /** Outgoing message counter. Shared by all dispatchers. */
    private final SmsUsageMonitor mUsageMonitor;

    /** Number of outgoing SmsTrackers waiting for user confirmation. */
    private int mPendingTrackerCount;

    /** Wake lock to ensure device stays awake while dispatching the SMS intent. */
    private PowerManager.WakeLock mWakeLock;

    /**
     * Hold the wake lock for 5 seconds, which should be enough time for
     * any receiver(s) to grab its own wake lock.
     */
    private static final int WAKE_LOCK_TIMEOUT = 5000;

    /* Flags indicating whether the current device allows sms service */
    protected boolean mSmsCapable = true;
    protected boolean mSmsReceiveDisabled;
    protected boolean mSmsSendDisabled;

    protected int mRemainingMessages = -1;

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef += 1;
        return sConcatenatedRef;
    }

    /**
     * Create a new SMS dispatcher.
     * @param phone the Phone to use
     * @param storageMonitor the SmsStorageMonitor to use
     * @param usageMonitor the SmsUsageMonitor to use
     */
    protected SMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor) {
        mPhone = phone;
        mWapPush = new WapPushOverSms(phone, this);
        mContext = phone.getContext();
        mResolver = mContext.getContentResolver();
        mCm = phone.mCM;
        mStorageMonitor = storageMonitor;
        mUsageMonitor = usageMonitor;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        // Register the mock SMS receiver to simulate the reception of SMS
        mMockSmsReceiver = new MockSmsReceiver();
        mMockSmsReceiver.registerReceiver();

        createWakelock();

        mSmsCapable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
        mSmsReceiveDisabled = !SystemProperties.getBoolean(
                                TelephonyProperties.PROPERTY_SMS_RECEIVE, mSmsCapable);
        mSmsSendDisabled = !SystemProperties.getBoolean(
                                TelephonyProperties.PROPERTY_SMS_SEND, mSmsCapable);
        Log.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + mSmsCapable + " format=" + getFormat()
                + " mSmsReceiveDisabled=" + mSmsReceiveDisabled
                + " mSmsSendDisabled=" + mSmsSendDisabled);
    }

    /** Unregister for incoming SMS events. */
    public abstract void dispose();

    /**
     * The format of the message PDU in the associated broadcast intent.
     * This will be either "3gpp" for GSM/UMTS/LTE messages in 3GPP format
     * or "3gpp2" for CDMA/LTE messages in 3GPP2 format.
     *
     * Note: All applications which handle incoming SMS messages by processing the
     * SMS_RECEIVED_ACTION broadcast intent MUST pass the "format" extra from the intent
     * into the new methods in {@link android.telephony.SmsMessage} which take an
     * extra format parameter. This is required in order to correctly decode the PDU on
     * devices which require support for both 3GPP and 3GPP2 formats at the same time,
     * such as CDMA/LTE devices and GSM/CDMA world phones.
     *
     * @return the format of the message PDU
     */
    protected abstract String getFormat();

    @Override
    protected void finalize() {
        mMockSmsReceiver.unregisterReceiver();
        Log.d(TAG, "SMSDispatcher finalized");
    }


    /* TODO: Need to figure out how to keep track of status report routing in a
     *       persistent manner. If the phone process restarts (reboot or crash),
     *       we will lose this list and any status reports that come in after
     *       will be dropped.
     */
    /** Sent messages awaiting a delivery status report. */
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList<SmsTracker>();

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
        case EVENT_NEW_SMS:
            // A new SMS has been received by the device
            if (false) {
                Log.d(TAG, "New SMS Message Received");
            }

            SmsMessage sms;

            ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                Log.e(TAG, "Exception processing incoming SMS. Exception:" + ar.exception);
                return;
            }

            sms = (SmsMessage) ar.result;
            try {
                int result = dispatchMessage(sms.mWrappedSmsMessage);
                if (result != Activity.RESULT_OK) {
                    // RESULT_OK means that message was broadcast for app(s) to handle.
                    // Any other result, we should ack here.
                    boolean handled = (result == Intents.RESULT_SMS_HANDLED);
                    notifyAndAcknowledgeLastIncomingSms(handled, result, null);
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Exception dispatching message", ex);
                notifyAndAcknowledgeLastIncomingSms(false, Intents.RESULT_SMS_GENERIC_ERROR, null);
            }

            break;

        case EVENT_SEND_SMS_COMPLETE:
            // An outbound SMS has been successfully transferred, or failed.
            handleSendComplete((AsyncResult) msg.obj);
            break;

        case EVENT_SEND_RETRY:
            sendSms((SmsTracker) msg.obj);
            break;

        case EVENT_SEND_LIMIT_REACHED_CONFIRMATION:
            handleReachSentLimit((SmsTracker)(msg.obj));
            break;

        case EVENT_SEND_CONFIRMED_SMS:
        {
            SmsTracker tracker = (SmsTracker) msg.obj;
            if (tracker.isMultipart()) {
                sendMultipartSms(tracker);
            } else {
                sendSms(tracker);
            }
            mPendingTrackerCount--;
            break;
        }

        case EVENT_STOP_SENDING:
        {
            SmsTracker tracker = (SmsTracker) msg.obj;
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
                } catch (CanceledException ex) {
                    Log.e(TAG, "failed to send RESULT_ERROR_LIMIT_EXCEEDED");
                }
            }
            mPendingTrackerCount--;
            break;
        }
        }
    }

    private void createWakelock() {
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSDispatcher");
        mWakeLock.setReferenceCounted(true);
    }

    /**
     * Grabs a wake lock and sends intent as an ordered broadcast.
     * The resultReceiver will check for errors and ACK/NACK back
     * to the RIL.
     *
     * @param intent intent to broadcast
     * @param permission Receivers are required to have this permission
     */
    public void dispatch(Intent intent, String permission) {
        // Hold a wake lock for WAKE_LOCK_TIMEOUT seconds, enough to give any
        // receivers time to take their own wake locks.
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        mContext.sendOrderedBroadcast(intent, permission, mResultReceiver,
                this, Activity.RESULT_OK, null, null);
    }

    /**
     * Grabs a wake lock and sends intent as an ordered broadcast.
     * Used for setting a custom result receiver for CDMA SCPD.
     *
     * @param intent intent to broadcast
     * @param permission Receivers are required to have this permission
     * @param resultReceiver the result receiver to use
     */
    public void dispatch(Intent intent, String permission, BroadcastReceiver resultReceiver) {
        // Hold a wake lock for WAKE_LOCK_TIMEOUT seconds, enough to give any
        // receivers time to take their own wake locks.
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        mContext.sendOrderedBroadcast(intent, permission, resultReceiver,
                this, Activity.RESULT_OK, null, null);
    }

    /**
     * Called when SMS send completes. Broadcasts a sentIntent on success.
     * On failure, either sets up retries or broadcasts a sentIntent with
     * the failure in the result code.
     *
     * @param ar AsyncResult passed into the message handler.  ar.result should
     *           an SmsResponse instance if send was successful.  ar.userObj
     *           should be an SmsTracker instance.
     */
    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = (SmsTracker) ar.userObj;
        PendingIntent sentIntent = tracker.mSentIntent;

        if (ar.exception == null) {
            if (false) {
                Log.d(TAG, "SMS send complete. Broadcasting "
                        + "intent: " + sentIntent);
            }

            if (tracker.mDeliveryIntent != null) {
                // Expecting a status report.  Add it to the list.
                int messageRef = ((SmsResponse)ar.result).messageRef;
                tracker.mMessageRef = messageRef;
                deliveryPendingList.add(tracker);
            }

            if (sentIntent != null) {
                try {
                    if (mRemainingMessages > -1) {
                        mRemainingMessages--;
                    }

                    if (mRemainingMessages == 0) {
                        Intent sendNext = new Intent();
                        sendNext.putExtra(SEND_NEXT_MSG_EXTRA, true);
                        sentIntent.send(mContext, Activity.RESULT_OK, sendNext);
                    } else {
                        sentIntent.send(Activity.RESULT_OK);
                    }
                } catch (CanceledException ex) {}
            }
        } else {
            if (false) {
                Log.d(TAG, "SMS send failed");
            }

            int ss = mPhone.getServiceState().getState();

            if (ss != ServiceState.STATE_IN_SERVICE) {
                handleNotInService(ss, tracker.mSentIntent);
            } else if ((((CommandException)(ar.exception)).getCommandError()
                    == CommandException.Error.SMS_FAIL_RETRY) &&
                   tracker.mRetryCount < MAX_SEND_RETRIES) {
                // Retry after a delay if needed.
                // TODO: According to TS 23.040, 9.2.3.6, we should resend
                //       with the same TP-MR as the failed message, and
                //       TP-RD set to 1.  However, we don't have a means of
                //       knowing the MR for the failed message (EF_SMSstatus
                //       may or may not have the MR corresponding to this
                //       message, depending on the failure).  Also, in some
                //       implementations this retry is handled by the baseband.
                tracker.mRetryCount++;
                Message retryMsg = obtainMessage(EVENT_SEND_RETRY, tracker);
                sendMessageDelayed(retryMsg, SEND_RETRY_DELAY);
            } else if (tracker.mSentIntent != null) {
                int error = RESULT_ERROR_GENERIC_FAILURE;

                if (((CommandException)(ar.exception)).getCommandError()
                        == CommandException.Error.FDN_CHECK_FAILURE) {
                    error = RESULT_ERROR_FDN_CHECK_FAILURE;
                }
                // Done retrying; return an error to the app.
                try {
                    Intent fillIn = new Intent();
                    if (ar.result != null) {
                        fillIn.putExtra("errorCode", ((SmsResponse)ar.result).errorCode);
                    }
                    if (mRemainingMessages > -1) {
                        mRemainingMessages--;
                    }

                    if (mRemainingMessages == 0) {
                        fillIn.putExtra(SEND_NEXT_MSG_EXTRA, true);
                    }

                    tracker.mSentIntent.send(mContext, error, fillIn);
                } catch (CanceledException ex) {}
            }
        }
    }

    /**
     * Handles outbound message when the phone is not in service.
     *
     * @param ss     Current service state.  Valid values are:
     *                  OUT_OF_SERVICE
     *                  EMERGENCY_ONLY
     *                  POWER_OFF
     * @param sentIntent the PendingIntent to send the error to
     */
    protected static void handleNotInService(int ss, PendingIntent sentIntent) {
        if (sentIntent != null) {
            try {
                if (ss == ServiceState.STATE_POWER_OFF) {
                    sentIntent.send(RESULT_ERROR_RADIO_OFF);
                } else {
                    sentIntent.send(RESULT_ERROR_NO_SERVICE);
                }
            } catch (CanceledException ex) {}
        }
    }

    /**
     * Dispatches an incoming SMS messages.
     *
     * @param sms the incoming message from the phone
     * @return a result code from {@link Telephony.Sms.Intents}, or
     *         {@link Activity#RESULT_OK} if the message has been broadcast
     *         to applications
     */
    public abstract int dispatchMessage(SmsMessageBase sms);

    /**
     * Dispatch a normal incoming SMS. This is called from the format-specific
     * {@link #dispatchMessage(SmsMessageBase)} if no format-specific handling is required.
     *
     * @param sms
     * @return
     */
    protected int dispatchNormalMessage(SmsMessageBase sms) {
        SmsHeader smsHeader = sms.getUserDataHeader();

        // See if message is partial or port addressed.
        if ((smsHeader == null) || (smsHeader.concatRef == null)) {
            // Message is not partial (not part of concatenated sequence).
            byte[][] pdus = new byte[1][];
            pdus[0] = sms.getPdu();

            if (smsHeader != null && smsHeader.portAddrs != null) {
                if (smsHeader.portAddrs.destPort == SmsHeader.PORT_WAP_PUSH) {
                    // GSM-style WAP indication
                    return mWapPush.dispatchWapPdu(sms.getUserData());
                } else {
                    // The message was sent to a port, so concoct a URI for it.
                    dispatchPortAddressedPdus(pdus, smsHeader.portAddrs.destPort);
                }
            } else {
                // Normal short and non-port-addressed message, dispatch it.
                dispatchPdus(pdus);
            }
            return Activity.RESULT_OK;
        } else {
            // Process the message part.
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            SmsHeader.PortAddrs portAddrs = smsHeader.portAddrs;
            return processMessagePart(sms.getPdu(), sms.getOriginatingAddress(),
                    concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount,
                    sms.getTimestampMillis(), (portAddrs != null ? portAddrs.destPort : -1), false);
        }
    }

    /**
     * If this is the last part send the parts out to the application, otherwise
     * the part is stored for later processing. Handles both 3GPP concatenated messages
     * as well as 3GPP2 format WAP push messages processed by
     * {@link com.android.internal.telephony.cdma.CdmaSMSDispatcher#processCdmaWapPdu}.
     *
     * @param pdu the message PDU, or the datagram portion of a CDMA WDP datagram segment
     * @param address the originating address
     * @param referenceNumber distinguishes concatenated messages from the same sender
     * @param sequenceNumber the order of this segment in the message
     *          (starting at 0 for CDMA WDP datagrams and 1 for concatenated messages).
     * @param messageCount the number of segments in the message
     * @param timestamp the service center timestamp in millis
     * @param destPort the destination port for the message, or -1 for no destination port
     * @param isCdmaWapPush true if pdu is a CDMA WDP datagram segment and not an SM PDU
     *
     * @return a result code from {@link Telephony.Sms.Intents}, or
     *         {@link Activity#RESULT_OK} if the message has been broadcast
     *         to applications
     */
    protected int processMessagePart(byte[] pdu, String address, int referenceNumber,
            int sequenceNumber, int messageCount, long timestamp, int destPort,
            boolean isCdmaWapPush) {
        byte[][] pdus = null;
        Cursor cursor = null;
        try {
            // used by several query selection arguments
            String refNumber = Integer.toString(referenceNumber);
            String seqNumber = Integer.toString(sequenceNumber);

            // Check for duplicate message segment
            cursor = mResolver.query(mRawUri, PDU_PROJECTION,
                    "address=? AND reference_number=? AND sequence=?",
                    new String[] {address, refNumber, seqNumber}, null);

            // moveToNext() returns false if no duplicates were found
            if (cursor.moveToNext()) {
                Log.w(TAG, "Discarding duplicate message segment from address=" + address
                        + " refNumber=" + refNumber + " seqNumber=" + seqNumber);
                String oldPduString = cursor.getString(PDU_COLUMN);
                byte[] oldPdu = HexDump.hexStringToByteArray(oldPduString);
                if (!Arrays.equals(oldPdu, pdu)) {
                    Log.e(TAG, "Warning: dup message segment PDU of length " + pdu.length
                            + " is different from existing PDU of length " + oldPdu.length);
                }
                return Intents.RESULT_SMS_HANDLED;
            }
            cursor.close();

            // not a dup, query for all other segments of this concatenated message
            String where = "address=? AND reference_number=?";
            String[] whereArgs = new String[] {address, refNumber};
            cursor = mResolver.query(mRawUri, PDU_SEQUENCE_PORT_PROJECTION, where, whereArgs, null);

            int cursorCount = cursor.getCount();
            if (cursorCount != messageCount - 1) {
                // We don't have all the parts yet, store this one away
                ContentValues values = new ContentValues();
                values.put("date", timestamp);
                values.put("pdu", HexDump.toHexString(pdu));
                values.put("address", address);
                values.put("reference_number", referenceNumber);
                values.put("count", messageCount);
                values.put("sequence", sequenceNumber);
                if (destPort != -1) {
                    values.put("destination_port", destPort);
                }
                mResolver.insert(mRawUri, values);
                return Intents.RESULT_SMS_HANDLED;
            }

            // All the parts are in place, deal with them
            pdus = new byte[messageCount][];
            for (int i = 0; i < cursorCount; i++) {
                cursor.moveToNext();
                int cursorSequence = cursor.getInt(SEQUENCE_COLUMN);
                // GSM sequence numbers start at 1; CDMA WDP datagram sequence numbers start at 0
                if (!isCdmaWapPush) {
                    cursorSequence--;
                }
                pdus[cursorSequence] = HexDump.hexStringToByteArray(
                        cursor.getString(PDU_COLUMN));

                // Read the destination port from the first segment (needed for CDMA WAP PDU).
                // It's not a bad idea to prefer the port from the first segment for 3GPP as well.
                if (cursorSequence == 0 && !cursor.isNull(DESTINATION_PORT_COLUMN)) {
                    destPort = cursor.getInt(DESTINATION_PORT_COLUMN);
                }
            }
            // This one isn't in the DB, so add it
            // GSM sequence numbers start at 1; CDMA WDP datagram sequence numbers start at 0
            if (isCdmaWapPush) {
                pdus[sequenceNumber] = pdu;
            } else {
                pdus[sequenceNumber - 1] = pdu;
            }

            // Remove the parts from the database
            mResolver.delete(mRawUri, where, whereArgs);
        } catch (SQLException e) {
            Log.e(TAG, "Can't access multipart SMS database", e);
            return Intents.RESULT_SMS_GENERIC_ERROR;
        } finally {
            if (cursor != null) cursor.close();
        }

        // Special handling for CDMA WDP datagrams
        if (isCdmaWapPush) {
            // Build up the data stream
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int i = 0; i < messageCount; i++) {
                // reassemble the (WSP-)pdu
                output.write(pdus[i], 0, pdus[i].length);
            }
            byte[] datagram = output.toByteArray();

            // Dispatch the PDU to applications
            if (destPort == SmsHeader.PORT_WAP_PUSH) {
                // Handle the PUSH
                return mWapPush.dispatchWapPdu(datagram);
            } else {
                pdus = new byte[1][];
                pdus[0] = datagram;
                // The messages were sent to any other WAP port
                dispatchPortAddressedPdus(pdus, destPort);
                return Activity.RESULT_OK;
            }
        }

        // Dispatch the PDUs to applications
        if (destPort != -1) {
            if (destPort == SmsHeader.PORT_WAP_PUSH) {
                // Build up the data stream
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                for (int i = 0; i < messageCount; i++) {
                    SmsMessage msg = SmsMessage.createFromPdu(pdus[i], getFormat());
                    byte[] data = msg.getUserData();
                    output.write(data, 0, data.length);
                }
                // Handle the PUSH
                return mWapPush.dispatchWapPdu(output.toByteArray());
            } else {
                // The messages were sent to a port, so concoct a URI for it
                dispatchPortAddressedPdus(pdus, destPort);
            }
        } else {
            // The messages were not sent to a port
            dispatchPdus(pdus);
        }
        return Activity.RESULT_OK;
    }

    /**
     * Dispatches standard PDUs to interested applications
     *
     * @param pdus The raw PDUs making up the message
     */
    protected void dispatchPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus", pdus);
        intent.putExtra("format", getFormat());
        dispatch(intent, RECEIVE_SMS_PERMISSION);
    }

    /**
     * Dispatches port addressed PDUs to interested applications
     *
     * @param pdus The raw PDUs making up the message
     * @param port The destination port of the messages
     */
    protected void dispatchPortAddressedPdus(byte[][] pdus, int port) {
        Uri uri = Uri.parse("sms://localhost:" + port);
        Intent intent = new Intent(Intents.DATA_SMS_RECEIVED_ACTION, uri);
        intent.putExtra("pdus", pdus);
        intent.putExtra("format", getFormat());
        dispatch(intent, RECEIVE_SMS_PERMISSION);
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param destPort the port to deliver the message to
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  <code>RESULT_ERROR_NO_SERVICE</code><br>.
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
    protected abstract void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent);

    /**
     * Send a text based SMS.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  <code>RESULT_ERROR_NO_SERVICE</code><br>.
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
    protected abstract void sendText(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent);

    /**
     * Calculate the number of septets needed to encode the message.
     *
     * @param messageBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    protected abstract TextEncodingDetails calculateLength(CharSequence messageBody,
            boolean use7bitOnly);

    /**
     * Send a multi-part text based SMS.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
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
     *   <code>RESULT_ERROR_NULL_PDU</code>
     *   <code>RESULT_ERROR_NO_SERVICE</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected void sendMultipartText(String destAddr, String scAddr,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();
        int encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;

        mRemainingMessages = msgCount;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            TextEncodingDetails details = calculateLength(parts.get(i), false);
            if (encoding != details.codeUnitSize
                    && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                            || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }

        for (int i = 0; i < msgCount; i++) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            // TODO: We currently set this to true since our messaging app will never
            // send more than 255 parts (it converts the message to MMS well before that).
            // However, we should support 3rd party messaging apps that might need 16-bit
            // references
            // Note:  It's not sufficient to just flip this bit to true; it will have
            // ripple effects (several calculations assume 8-bit ref).
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;

            // Set the national language tables for 3GPP 7-bit encoding, if enabled.
            if (encoding == android.telephony.SmsMessage.ENCODING_7BIT) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            sendNewSubmitPdu(destAddr, scAddr, parts.get(i), smsHeader, encoding,
                    sentIntent, deliveryIntent, (i == (msgCount - 1)));
        }

    }

    /**
     * Create a new SubmitPdu and send it.
     */
    protected abstract void sendNewSubmitPdu(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart);

    /**
     * Send a SMS
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param pdu the raw PDU to send
     * @param sentIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *  <code>RESULT_ERROR_RADIO_OFF</code>
     *  <code>RESULT_ERROR_NULL_PDU</code>
     *  <code>RESULT_ERROR_NO_SERVICE</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param destAddr the destination phone number (for short code confirmation)
     */
    protected void sendRawPdu(byte[] smsc, byte[] pdu, PendingIntent sentIntent,
            PendingIntent deliveryIntent, String destAddr) {
        if (mSmsSendDisabled) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NO_SERVICE);
                } catch (CanceledException ex) {}
            }
            Log.d(TAG, "Device does not support sending sms.");
            return;
        }

        if (pdu == null) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {}
            }
            return;
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("smsc", smsc);
        map.put("pdu", pdu);

        // Get calling app package name via UID from Binder call
        PackageManager pm = mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());

        if (packageNames == null || packageNames.length == 0) {
            // Refuse to send SMS if we can't get the calling package name.
            Log.e(TAG, "Can't get calling app package name: refusing to send SMS");
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_GENERIC_FAILURE);
                } catch (CanceledException ex) {
                    Log.e(TAG, "failed to send error result");
                }
            }
            return;
        }

        String appPackage = packageNames[0];

        // Strip non-digits from destination phone number before checking for short codes
        // and before displaying the number to the user if confirmation is required.
        SmsTracker tracker = new SmsTracker(map, sentIntent, deliveryIntent, appPackage,
                PhoneNumberUtils.extractNetworkPortion(destAddr));

        // check for excessive outgoing SMS usage by this app
        if (!mUsageMonitor.check(appPackage, SINGLE_PART_SMS)) {
            sendMessage(obtainMessage(EVENT_SEND_LIMIT_REACHED_CONFIRMATION, tracker));
            return;
        }

        int ss = mPhone.getServiceState().getState();

        if (ss != ServiceState.STATE_IN_SERVICE) {
            handleNotInService(ss, tracker.mSentIntent);
        } else {
            sendSms(tracker);
        }
    }

    /**
     * Deny sending an SMS if the outgoing queue limit is reached. Used when the message
     * must be confirmed by the user due to excessive usage or potential premium SMS detected.
     * @param tracker the SmsTracker for the message to send
     * @return true if the message was denied; false to continue with send confirmation
     */
    private boolean denyIfQueueLimitReached(SmsTracker tracker) {
        if (mPendingTrackerCount >= MO_MSG_QUEUE_LIMIT) {
            // Deny sending message when the queue limit is reached.
            try {
                tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
            } catch (CanceledException ex) {
                Log.e(TAG, "failed to send back RESULT_ERROR_LIMIT_EXCEEDED");
            }
            return true;
        }
        mPendingTrackerCount++;
        return false;
    }

    /**
     * Returns the label for the specified app package name.
     * @param appPackage the package name of the app requesting to send an SMS
     * @return the label for the specified app, or the package name if getApplicationInfo() fails
     */
    private CharSequence getAppLabel(String appPackage) {
        PackageManager pm = mContext.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(appPackage, 0);
            return appInfo.loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "PackageManager Name Not Found for package " + appPackage);
            return appPackage;  // fall back to package name if we can't get app label
        }
    }

    /**
     * Post an alert when SMS needs confirmation due to excessive usage.
     * @param tracker an SmsTracker for the current message.
     */
    protected void handleReachSentLimit(SmsTracker tracker) {
        if (denyIfQueueLimitReached(tracker)) {
            return;     // queue limit reached; error was returned to caller
        }

        CharSequence appLabel = getAppLabel(tracker.mAppPackage);
        Resources r = Resources.getSystem();
        Spanned messageText = Html.fromHtml(r.getString(R.string.sms_control_message, appLabel));

        ConfirmDialogListener listener = new ConfirmDialogListener(tracker);

        AlertDialog d = new AlertDialog.Builder(mContext)
                .setTitle(R.string.sms_control_title)
                .setIcon(R.drawable.stat_sys_warning)
                .setMessage(messageText)
                .setPositiveButton(r.getString(R.string.sms_control_yes), listener)
                .setNegativeButton(r.getString(R.string.sms_control_no), listener)
                .setOnCancelListener(listener)
                .create();

        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();
    }

    /**
     * Send the message along to the radio.
     *
     * @param tracker holds the SMS message to send
     */
    protected abstract void sendSms(SmsTracker tracker);

    /**
     * Send the multi-part SMS based on multipart Sms tracker
     *
     * @param tracker holds the multipart Sms tracker ready to be sent
     */
    private void sendMultipartSms(SmsTracker tracker) {
        ArrayList<String> parts;
        ArrayList<PendingIntent> sentIntents;
        ArrayList<PendingIntent> deliveryIntents;

        HashMap<String, Object> map = tracker.mData;

        String destinationAddress = (String) map.get("destination");
        String scAddress = (String) map.get("scaddress");

        parts = (ArrayList<String>) map.get("parts");
        sentIntents = (ArrayList<PendingIntent>) map.get("sentIntents");
        deliveryIntents = (ArrayList<PendingIntent>) map.get("deliveryIntents");

        // check if in service
        int ss = mPhone.getServiceState().getState();
        if (ss != ServiceState.STATE_IN_SERVICE) {
            for (int i = 0, count = parts.size(); i < count; i++) {
                PendingIntent sentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    sentIntent = sentIntents.get(i);
                }
                handleNotInService(ss, sentIntent);
            }
            return;
        }

        sendMultipartText(destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
    }

    /**
     * Send an acknowledge message.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    protected abstract void acknowledgeLastIncomingSms(boolean success,
            int result, Message response);

    /**
     * Notify interested apps if the framework has rejected an incoming SMS,
     * and send an acknowledge message to the network.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    private void notifyAndAcknowledgeLastIncomingSms(boolean success,
            int result, Message response) {
        if (!success) {
            // broadcast SMS_REJECTED_ACTION intent
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
            mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        acknowledgeLastIncomingSms(success, result, response);
    }

    /**
     * Keeps track of an SMS that has been sent to the RIL, until it has
     * successfully been sent, or we're done trying.
     *
     */
    protected static final class SmsTracker {
        // fields need to be public for derived SmsDispatchers
        public final HashMap<String, Object> mData;
        public int mRetryCount;
        public int mMessageRef;

        public final PendingIntent mSentIntent;
        public final PendingIntent mDeliveryIntent;

        public final String mAppPackage;
        public final String mDestAddress;

        public SmsTracker(HashMap<String, Object> data, PendingIntent sentIntent,
                PendingIntent deliveryIntent, String appPackage, String destAddr) {
            mData = data;
            mSentIntent = sentIntent;
            mDeliveryIntent = deliveryIntent;
            mRetryCount = 0;
            mAppPackage = appPackage;
            mDestAddress = destAddr;
        }

        /**
         * Returns whether this tracker holds a multi-part SMS.
         * @return true if the tracker holds a multi-part SMS; false otherwise
         */
        protected boolean isMultipart() {
            HashMap map = mData;
            return map.containsKey("parts");
        }
    }

    /**
     * Dialog listener for SMS confirmation dialog.
     */
    private final class ConfirmDialogListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

        private final SmsTracker mTracker;

        ConfirmDialogListener(SmsTracker tracker) {
            mTracker = tracker;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                Log.d(TAG, "CONFIRM sending SMS");
                sendMessage(obtainMessage(EVENT_SEND_CONFIRMED_SMS, mTracker));
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Log.d(TAG, "DENY sending SMS");
                sendMessage(obtainMessage(EVENT_STOP_SENDING, mTracker));
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            Log.d(TAG, "dialog dismissed: don't send SMS");
            sendMessage(obtainMessage(EVENT_STOP_SENDING, mTracker));
        }
    }

    private final BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Assume the intent is one of the SMS receive intents that
            // was sent as an ordered broadcast.  Check result and ACK.
            int rc = getResultCode();
            boolean success = (rc == Activity.RESULT_OK)
                    || (rc == Intents.RESULT_SMS_HANDLED);

            // For a multi-part message, this only ACKs the last part.
            // Previous parts were ACK'd as they were received.
            acknowledgeLastIncomingSms(success, rc, null);
        }
    };

    protected void dispatchBroadcastMessage(SmsCbMessage message) {
        if (message.isEmergencyMessage()) {
            Intent intent = new Intent(Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            intent.putExtra("message", message);
            Log.d(TAG, "Dispatching emergency SMS CB");
            dispatch(intent, RECEIVE_EMERGENCY_BROADCAST_PERMISSION);
        } else {
            Intent intent = new Intent(Intents.SMS_CB_RECEIVED_ACTION);
            intent.putExtra("message", message);
            Log.d(TAG, "Dispatching SMS CB");
            dispatch(intent, RECEIVE_SMS_PERMISSION);
        }
    }

    /**
     * A private class that allow simulating the receive of SMS.<br/>
     * <br/>
     * A developer must use {@link Context#sendBroadcast(Intent)}, using the action
     * {@link Intents#MOCK_SMS_RECEIVED_ACTION}. The application requires
     * {@linkplain "android.permission.SEND_MOCK_SMS"} permission.<br/>
     * <br/>
     * This receiver should be used in the next way:<br/>
     * <pre>
     * Intent in = new Intent(Intents.MOCK_SMS_RECEIVED_ACTION);
     * in.putExtra("scAddr", "+01123456789");
     * in.putExtra("senderAddr", "+01123456789");
     * in.putExtra("msg", "This is a mock SMS message.");
     * sendBroadcast(in);
     * </pre><br/>
     * or<br/>
     * <pre>
     * String pdu = "07914151551512f2040B916105551511f100006060605130308A04D4F29C0E";
     * byte[][] pdus = new byte[1][];
     * pdus[0] = HexDump.hexStringToByteArray(pdu);
     * Intent in = new Intent(Intents.MOCK_SMS_RECEIVED_ACTION);
     * intent.putExtra("pdus", pdus);
     * sendBroadcast(in);
     * </pre><br/>
     */
    private final class MockSmsReceiver extends BroadcastReceiver {
        private static final String TAG = "MockSmsReceiver";

        private static final String SEND_MOCK_SMS_PERMISSION =
                                        "android.permission.SEND_MOCK_SMS";

        /**
         * Method that register the MockSmsReceiver class as a BroadcastReceiver
         */
        public final void registerReceiver() {
            try {
                Handler handler = new Handler();
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intents.MOCK_SMS_RECEIVED_ACTION);
                mContext.registerReceiver(this, filter, SEND_MOCK_SMS_PERMISSION, handler);
                Log.d(TAG, "Registered MockSmsReceiver");
            } catch (Exception ex) {
                Log.e(TAG, "Failed to register MockSmsReceiver", ex);
            }
        }

        /**
         * Method that unregister the MockSmsReceiver class as a BroadcastReceiver
         */
        public final void unregisterReceiver() {
            try {
                mContext.unregisterReceiver(this);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to unregister MockSmsReceiver", ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final void onReceive(Context context, Intent intent) {
            Log.d(TAG, "New mock SMS reception request. Intent: " + intent);

            try {
                // Check that developer option is enabled, and mock
                // messages are allowed
                boolean allowMockSMS = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.ALLOW_MOCK_SMS, 0) == 1;
                if (!allowMockSMS) {
                    // Mock SMS is not allowed. This
                    Log.w(TAG,
                            "Mock SMS is not allowed. Enable Mock SMS on Settings/Delevelopment.");
                    return;
                }

                // Extract PDUs
                byte[][] pdus = null;
                Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
                if (messages != null && messages.length > 0) {
                    // Use the PDUs from the intent
                    pdus = new byte[messages.length][];
                    for (int i = 0; i < messages.length; i++) {
                        pdus[i] = (byte[]) messages[i];
                    }

                } else {
                    // Build the PDUs from SMS data
                    String scAddress = intent.getStringExtra("scAddr");
                    String senderAddress = intent.getStringExtra("senderAddr");
                    String msg = intent.getStringExtra("msg");

                    // Check that values are valid. Otherwise fill will default values
                    if (TextUtils.isEmpty(scAddress)) {
                        scAddress = "+01123456789";
                    }
                    if (TextUtils.isEmpty(senderAddress)) {
                        senderAddress = "+01123456789";
                    }
                    if (TextUtils.isEmpty(msg)) {
                        msg = "This is a mock SMS message.";
                    }
                    Log.d(TAG,
                            String.format(
                                    "Mock SMS. scAddress: %s, senderAddress: %s, msg: %s",
                                    scAddress, senderAddress, msg));

                    // Create the PDUs
                    pdus = getPdus(scAddress, senderAddress, msg);
                }

                // How messages are going to send?
                Log.d(TAG,
                        String.format(
                                "Mock SMS. Number of msg: %d",
                                pdus.length));

                // Send messages
                Intent mockSmsIntent = new Intent(Intents.SMS_RECEIVED_ACTION);
                mockSmsIntent.putExtra("pdus", pdus );
                mockSmsIntent.putExtra("format", android.telephony.SmsMessage.FORMAT_3GPP );
                dispatch(mockSmsIntent, SMSDispatcher.RECEIVE_SMS_PERMISSION);

                // Set result (messages were sent)
                setResultCode(android.app.Activity.RESULT_OK);

            } catch (Exception ex) {
                Log.e(TAG, "Failed to dispatch SMS", ex);

                // Set result (messages were not sent)
                setResultCode(android.app.Activity.RESULT_CANCELED);
            }
        }

        /**
         * Method that convert the basic SMS string data to a PDUs messages
         *
         * @param scAddress The mock the SC address
         * @param senderAddress The mock the sender address
         * @param message The mock message body
         * @return byte[] The array of bytes of the PDU
         */
        private byte[][] getPdus(String scAddress, String senderAddress, String message) {

            // Fragment the text in message according to SMS length
            List<String> msgs = android.telephony.SmsMessage.fragmentText(message);

            // Create the array of PDUs to return
            byte[][] pdus = new byte[msgs.size()][];

            // Retrieve a PDU for every fragmented message
            for (int i=0; i<msgs.size(); i++) {
                String msg = msgs.get(i);

                // Get a SubmitPdu
                SubmitPdu submitPdu =
                        android.telephony.SmsMessage.getSubmitPdu(
                                                            scAddress,
                                                            senderAddress,
                                                            msg,
                                                            false);

                // Translate the submit data to a received PDU
                int dataLen = android.telephony.SmsMessage.calculateLength(msg, true)[1];

                // Locate protocol + data encoding scheme
                byte[] pds = {(byte)0, (byte)0, (byte)dataLen};
                int dataPos = new String(submitPdu.encodedMessage).indexOf(new String(pds), 4) + 2;

                // Set arrays dimension
                byte[] encSc = submitPdu.encodedScAddress;
                byte[] encMsg = new byte[submitPdu.encodedMessage.length - dataPos];
                System.arraycopy(
                        submitPdu.encodedMessage, dataPos,
                        encMsg, 0, submitPdu.encodedMessage.length - dataPos);
                byte[] encSender = new byte[dataPos - 4];
                System.arraycopy(
                        submitPdu.encodedMessage, 2,
                        encSender, 0, dataPos - 4);
                byte[] encTs = bcdTimestamp();
                byte[] pdu = new byte[
                                      encSc.length +
                                      1 +       /** SMS-DELIVER **/
                                      encSender.length +
                                      2 +       /** Protocol + Data Encoding Scheme **/
                                      encTs.length +
                                      encMsg.length];

                // Copy the SC address
                int c = 0;
                System.arraycopy(encSc, 0, pdu, c, encSc.length);
                c+=encSc.length;
                // SMS-DELIVER
                pdu[c] = 0x04;
                c++;
                // Sender
                System.arraycopy(encSender, 0, pdu, c, encSender.length);
                c+=encSender.length;
                // Protocol + Data encoding scheme
                pdu[c] = 0x00;
                c++;
                pdu[c] = 0x00;
                c++;
                // Timestamp
                System.arraycopy(encTs, 0, pdu, c, encTs.length);
                c+=encTs.length;
                // Message
                System.arraycopy(encMsg, 0, pdu, c, encMsg.length);

                // Store the PDU
                pdus[i] = pdu;
            }

            // Return the PDUs
            return pdus;
        }

        /**
         * Method that return the current timestamp in a BCD format
         *
         * @return byte[] The BCD timestamp
         */
        private byte[] bcdTimestamp() {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yy"); //$NON-NLS-1$
            SimpleDateFormat sdf2 = new SimpleDateFormat("Z"); //$NON-NLS-1$
            byte year = (byte)Integer.parseInt(
                            String.valueOf(Integer.parseInt(sdf.format(c.getTime()))), 16);
            byte month = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.MONTH) + 1), 16);
            byte day = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 16);
            byte hour = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.HOUR)), 16);
            byte minute = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.MINUTE)), 16);
            byte second = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.SECOND)), 16);
            String tz = sdf2.format(c.getTime()).substring(1);
            int timezone = Integer.parseInt(tz) / 100;
            if (timezone < 0) {
                timezone += 0x80;
            }
            byte[] data = {year, month, day, hour, minute, second, 0};
            byte[] ts = IccUtils.hexStringToBytes(IccUtils.bcdToString(data, 0, data.length));
            ts[6] = (byte)Integer.parseInt(String.valueOf(timezone), 16);
            return ts;
        }
    }
}
