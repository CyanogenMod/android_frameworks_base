/**
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

package com.android.server.fingerprint;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.Fingerprint;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.service.fingerprint.FingerprintManager;
import android.service.fingerprint.FingerprintUtils;
import android.service.fingerprint.IFingerprintService;
import android.service.fingerprint.IFingerprintServiceReceiver;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A service to manage multiple clients that want to access the fingerprint HAL API.
 * The service is responsible for maintaining a list of clients and dispatching all
 * fingerprint -related events.
 *
 * @hide
 */
public class FingerprintService extends SystemService {
    private final String TAG = "FingerprintService";
    private static final boolean DEBUG = true;
    private ArrayMap<IBinder, ClientData> mClients = new ArrayMap<IBinder, ClientData>();

    private static final int MSG_NOTIFY = 10;

    Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_NOTIFY:
                    handleNotify(msg.arg1, msg.arg2, (Integer) msg.obj);
                    break;

                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    };
    private Context mContext;
    private int mState = STATE_IDLE;

    private static final int STATE_IDLE = 0;
    private static final int STATE_AUTHENTICATING = 1;
    private static final int STATE_ENROLLING = 2;
    private static final long MS_PER_SEC = 1000;

    /**
     * The time, in milliseconds, to run the device vibrator after a fingerprint
     * image has been aquired or enrolled by the fingerprint sensor.
     */
    private static final long FINGERPRINT_EVENT_VIBRATE_DURATION = 100;

    /**
     * A local instance of {@link android.os.Vibrator} as retrieved using
     * {@link android.content.Context#VIBRATOR_SERVICE}
     */
    private Vibrator mVibrator;

    private long mHal;

    private static final class ClientData {
        public IFingerprintServiceReceiver receiver;
        int userId;
        public TokenWatcher tokenWatcher;
        IBinder getToken() { return tokenWatcher.getToken(); }
    }

    private class TokenWatcher implements IBinder.DeathRecipient {
        WeakReference<IBinder> token;

        TokenWatcher(IBinder token) {
            this.token = new WeakReference<IBinder>(token);
        }

        IBinder getToken() { return token.get(); }
        public void binderDied() {
            mClients.remove(token);
            this.token = null;
        }

        protected void finalize() throws Throwable {
            try {
                if (token != null) {
                    if (DEBUG) Slog.w(TAG, "removing leaked reference: " + token);
                    mClients.remove(token);
                }
            } finally {
                super.finalize();
            }
        }
    }

    public FingerprintService(Context context) {
        super(context);
        mContext = context;
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        // If no physical vibrator is present, set vibrator to null.
        if (mVibrator != null && !mVibrator.hasVibrator()) {
            mVibrator = null;
        }
        nativeInit(this);
    }

    // TODO: Move these into separate process
    // JNI methods to communicate from FingerprintManagerService to HAL
    native int nativeAuthenticate();
    native int nativeEnroll(int timeout);
    native int nativeCancel();
    native int nativeRemove(int fingerprintId);
    native int nativeOpenHal();
    native int nativeCloseHal();
    native void nativeInit(FingerprintService service);
    native Fingerprint[] nativeGetEnrollments();
    native int nativeGetNumEnrollmentSteps();

    // JNI methods for communicating from HAL to clients
    void notify(int msg, int arg1, int arg2) {
        mHandler.obtainMessage(MSG_NOTIFY, msg, arg1, arg2).sendToTarget();
    }

    void handleNotify(int msg, int arg1, int arg2) {
        Slog.v(TAG, "handleNotify(msg=" + msg + ", arg1=" + arg1 + ", arg2=" + arg2 + ")");
        int newState = mState;
        for (Iterator<Map.Entry<IBinder, ClientData>> it = mClients.entrySet().iterator();
                it.hasNext(); ) {
            ClientData clientData = it.next().getValue();
            switch (msg) {
                case FingerprintManager.FINGERPRINT_ERROR: {
                    final int error = arg1;
                    try {
                        newState = STATE_IDLE;
                        if (clientData != null && clientData.receiver != null) {
                            clientData.receiver.onError(error);
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "can't send message to client. Did it die?", e);
                        it.remove();
                    }
                }
                break;
                case FingerprintManager.FINGERPRINT_ACQUIRED: {
                    final int acquireInfo = arg1;
                    if (mState == STATE_AUTHENTICATING) {
                        try {
                            vibrateDeviceIfSupported();
                            if (clientData != null && clientData.receiver != null) {
                                clientData.receiver.onAcquired(acquireInfo);
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "can't send message to client. Did it die?", e);
                            it.remove();
                        }
                    } else {
                        if (DEBUG) Slog.w(TAG, "Client not authenticating");
                        break;
                    }
                    break;
                }
                case FingerprintManager.FINGERPRINT_PROCESSED: {
                    final int fingerId = arg1;
                    if (mState == STATE_AUTHENTICATING) {
                        try {
                            newState = STATE_IDLE;
                            if (clientData != null && clientData.receiver != null) {
                                clientData.receiver.onProcessed(fingerId);
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "can't send message to client. Did it die?", e);
                            it.remove();
                        }
                    } else {
                        if (DEBUG) Slog.w(TAG, "Client not authenticating");
                        break;
                    }
                    break;
                }
                case FingerprintManager.FINGERPRINT_TEMPLATE_ENROLLING: {
                    final int fingerId = arg1;
                    final int remaining = arg2;
                    if (mState == STATE_ENROLLING) {
                        // Update the database with new finger id.
                        if (remaining == 0) {
                            FingerprintUtils.addFingerprintIdForUser(fingerId,
                                    mContext, clientData.userId);
                            newState = STATE_IDLE;
                        }
                        try {
                            vibrateDeviceIfSupported();
                            if (clientData != null && clientData.receiver != null) {
                                clientData.receiver.onEnrollResult(fingerId, remaining);
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "can't send message to client. Did it die?", e);
                            it.remove();
                        }
                    } else {
                        if (DEBUG) Slog.w(TAG, "Client not enrolling");
                        break;
                    }
                    break;
                }
                case FingerprintManager.FINGERPRINT_TEMPLATE_REMOVED: {
                    int fingerId = arg1;
                    if (fingerId == 0) throw new IllegalStateException("Got illegal id from HAL");
                    FingerprintUtils.removeFingerprintIdForUser(fingerId,
                            mContext.getContentResolver(), clientData.userId);
                    try {
                        if (clientData != null && clientData.receiver != null) {
                            clientData.receiver.onRemoved(fingerId);
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "can't send message to client. Did it die?", e);
                        it.remove();
                    }
                }
                break;
            }
        }
        mState = newState;
    }

    private void vibrateDeviceIfSupported() {
        if (mVibrator != null) {
            mVibrator.vibrate(FINGERPRINT_EVENT_VIBRATE_DURATION);
        }
    }

    void startEnroll(IBinder token, long timeout, int userId) {
        ClientData clientData = mClients.get(token);
        if (clientData != null) {
            if (clientData.userId != userId) throw new IllegalStateException("Bad user");
            if (mState != STATE_IDLE) {
                Slog.i(TAG, "fingerprint is in use");
                return;
            }
            nativeEnroll((int) (timeout / MS_PER_SEC));
            mState = STATE_ENROLLING;
        } else {
            Slog.w(TAG, "enroll(): No listener registered");
        }
    }

    void startAuthentication(IBinder token, int userId) {
        ClientData clientData = mClients.get(token);
        if (clientData != null) {
            if (clientData.userId != userId) throw new IllegalStateException("Bad user");
            if (mState != STATE_IDLE) {
                Slog.i(TAG, "fingerprint is in use");
                return;
            }
            nativeAuthenticate();
            mState = STATE_AUTHENTICATING;
        } else {
            Slog.w(TAG, "authenticate(): No listener registered");
        }
    }

    void startCancel(IBinder token, int userId) {
        ClientData clientData = mClients.get(token);
        if (clientData != null) {
            if (clientData.userId != userId) throw new IllegalStateException("Bad user");
            if (mState == STATE_IDLE) return;
            mState = STATE_IDLE;
            nativeCancel();
        } else {
            Slog.w(TAG, "enrollCancel(): No listener registered");
        }
    }

    // Remove all fingerprints for the given user.
    void startRemove(IBinder token, int fingerId, int userId) {
        ClientData clientData = mClients.get(token);
        if (clientData != null) {
            if (clientData.userId != userId) throw new IllegalStateException("Bad user");
            // The fingerprint id will be removed when we get confirmation from the HAL
            int result = nativeRemove(fingerId);
            if (result != 0) {
                Slog.w(TAG, "Error removing fingerprint with id = " + fingerId);
            }
        } else {
            Slog.w(TAG, "remove(" + token + "): No listener registered");
        }
    }

    void addListener(IBinder token, IFingerprintServiceReceiver receiver, int userId) {
        if (DEBUG) Slog.v(TAG, "startListening(" + receiver + ")");
        if (mClients.get(token) == null) {
            ClientData clientData = new ClientData();
            clientData.receiver = receiver;
            clientData.userId = userId;
            clientData.tokenWatcher = new TokenWatcher(token);
            try {
                token.linkToDeath(clientData.tokenWatcher, 0);
                mClients.put(token, clientData);
            } catch (RemoteException e) {
                Slog.w(TAG, "caught remote exception in linkToDeath: ", e);
            }
        } else {
            if (DEBUG) Slog.v(TAG, "listener already registered for " + token);
        }
    }

    void removeListener(IBinder token, int userId) {
        if (DEBUG) Slog.v(TAG, "stopListening(" + token + ")");
        ClientData clientData = mClients.get(token);
        if (clientData != null) {
            token.unlinkToDeath(clientData.tokenWatcher, 0);
            mClients.remove(token);
        } else {
            if (DEBUG) Slog.v(TAG, "listener not registered: " + token);
        }
        mClients.remove(token);
    }

    void checkPermission() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_FINGERPRINT_SERVICE, null);
    }

    public List<Fingerprint> getEnrolledFingerprints(IBinder token, int userId) {
        enforceCrossUserPermission(userId, "User " + UserHandle.getCallingUserId()
                + " trying to add account for " + userId);

        if(userId != UserHandle.getCallingUserId()) {
            throw new UnsupportedOperationException("Getting a userId for a " +
                    "different user than current is not supported");
        }

        Fingerprint[] nativeFingerprintsArray = nativeGetEnrollments();
        List<Fingerprint> nativeFingerprints = nativeFingerprintsArray != null ?
                Arrays.asList(nativeFingerprintsArray) : Collections.EMPTY_LIST;
        List<Fingerprint> settingsFingerprints = FingerprintUtils.getFingerprintsForUser(
                mContext.getContentResolver(), userId);

        List<Fingerprint> fingerprints = mergeAndUpdateSettingsFingerprints(nativeFingerprints,
                settingsFingerprints, userId);

        return fingerprints;
    }

    private List<Fingerprint> mergeAndUpdateSettingsFingerprints(
            List<Fingerprint> nativeFingerprints,
            List<Fingerprint> settingsFingerprints,
            int userId) {

        List<Fingerprint> mergedList = new ArrayList<Fingerprint>();
        HashMap<Integer, Fingerprint> nativeFingerMap = new HashMap<Integer, Fingerprint>();

        for(Fingerprint fingerprint : nativeFingerprints) {
            nativeFingerMap.put(fingerprint.getFingerId(), fingerprint);
        }

        boolean modifiedSettingsFingers = false;
        Iterator<Fingerprint> iter = settingsFingerprints.iterator();
        while(iter.hasNext()) {
            Fingerprint settingsFinger = iter.next();
            Fingerprint nativeFinger = nativeFingerMap.get(settingsFinger.getFingerId());
            if (nativeFinger == null) {
                // Finger exists in Settings but not in native space. Remove it from settings
                settingsFingerprints.remove(settingsFinger);
                modifiedSettingsFingers = true;
            } else {
                // Finger exists in Settings and in native space, merge it
                mergedList.add(mergeFingerprint(nativeFinger, settingsFinger));
                nativeFingerMap.remove(settingsFinger.getFingerId());
            }
        }

        // Fingerprints stored in vendor storage but not in settings are useless
        // since we have no idea what user id they are
        for(Fingerprint nativeFinger : nativeFingerMap.values()) {
            nativeRemove(nativeFinger.getFingerId());
        }

        // If there were any discrepancies, we should persist the corrected list now
        if (modifiedSettingsFingers) {
            FingerprintUtils.saveFingerprints(settingsFingerprints,
                    mContext.getContentResolver(), userId);
        }

        return mergedList;
    }

    private static Fingerprint mergeFingerprint(Fingerprint nativeFinger,
                                                Fingerprint settingsFinger) {
        Fingerprint.Builder builder = new Fingerprint.Builder(nativeFinger);
        builder.name(settingsFinger.getName());
        return builder.build();
    }

    public boolean setFingerprintName(IBinder token, int index, String name, int userId) {
        enforceCrossUserPermission(userId, "User " + UserHandle.getCallingUserId()
                + " trying to add account for " + userId);

        if(userId != UserHandle.getCallingUserId()) {
            throw new UnsupportedOperationException("Setting a fingerprint name for a " +
                    "different user than current is not supported");
        }

        FingerprintUtils.setFingerprintName(index, name, mContext.getContentResolver(), userId);
        return true;
    }

    public int getNumEnrollmentSteps() {
        return nativeGetNumEnrollmentSteps();
    }

    private void enforceCrossUserPermission(int userId, String errorMessage) {
        if (userId != UserHandle.getCallingUserId()
                && Binder.getCallingUid() != Process.myUid()
                && mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(errorMessage);
        }
    }

    private int getCurrentUserId () {
        final long identity = Binder.clearCallingIdentity();
        try {
            return ActivityManager.getCurrentUser();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void throwIfNoFingerprint() {
        if (mHal == 0) {
            throw new UnsupportedOperationException("Fingerprint sensor not available");
        }
    }

    private final class FingerprintServiceWrapper extends IFingerprintService.Stub {
        private final static String DUMP_CMD_REMOVE_FINGER = "removeFinger";
        private final static String DUMP_CMD_PRINT_ENROLLMENTS = "printEnrollments";
        private final static String DUMP_CMD_SET_FINGER_NAME = "setFingerName";
        private final static String DUMP_CMD_GET_NUM_ENROLLMENT_STEPS = "getNumEnrollmentSteps";

        @Override // Binder call
        public void authenticate(IBinder token, int userId) {
            checkPermission();
            throwIfNoFingerprint();
            startAuthentication(token, userId);
        }

        @Override // Binder call
        public void enroll(IBinder token, long timeout, int userId) {
            checkPermission();
            throwIfNoFingerprint();
            startEnroll(token, timeout, userId);
        }

        @Override // Binder call
        public void cancel(IBinder token,int userId) {
            checkPermission();
            throwIfNoFingerprint();
            startCancel(token, userId);
        }

        @Override // Binder call
        public void remove(IBinder token, int fingerprintId, int userId) {
            checkPermission();
            throwIfNoFingerprint();
            startRemove(token, fingerprintId, userId);
        }

        @Override // Binder call
        public void startListening(IBinder token, IFingerprintServiceReceiver receiver,
                int userId) {
            checkPermission();
            throwIfNoFingerprint();
            addListener(token, receiver, userId);
        }

        @Override // Binder call
        public void stopListening(IBinder token, int userId) {
            checkPermission();
            throwIfNoFingerprint();
            removeListener(token, userId);
        }

        @Override // Binder call
        public List<Fingerprint> getEnrolledFingerprints(IBinder token, int userId)
                throws RemoteException {
            checkPermission();
            throwIfNoFingerprint();
            return FingerprintService.this.getEnrolledFingerprints(token, userId);
        }

        @Override
        public boolean setFingerprintName(IBinder token, int fingerprintId, String name,
                int userId) throws RemoteException {
            checkPermission();
            throwIfNoFingerprint();
            return FingerprintService.this.setFingerprintName(token, fingerprintId, name, userId);
        }

        @Override
        public int getNumEnrollmentSteps(IBinder token)
                throws RemoteException {
            checkPermission();
            throwIfNoFingerprint();
            return FingerprintService.this.getNumEnrollmentSteps();
        }

        /**
         * "adb shell dumpsys fingerprint [cmd]
         */
        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump telephony.registry from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
                return;
            }
            if (mHal == 0) {
                pw.println("Fingerprint sensor not available");
            } else if (args.length != 0 && DUMP_CMD_PRINT_ENROLLMENTS.equals(args[0])) {
                dumpEnrollments(pw, args);
            } else if (args.length >= 3 && DUMP_CMD_SET_FINGER_NAME.equals(args[0])) {
                dumpSetFingerprintName(pw, args);
            } else if (args.length > 1 && DUMP_CMD_REMOVE_FINGER.equals(args[0])) {
                dumpRemoveFinger(pw, args);
            } else if (args.length >= 1 && DUMP_CMD_GET_NUM_ENROLLMENT_STEPS.equals(args[0])) {
                dumpGetNumEnrollmentSteps(pw, args);
            } else {
                dumpCommandList(pw);
            }
        }

        private void dumpSetFingerprintName(PrintWriter pw, String[] args) {
            try {
                int index = Integer.parseInt(args[1]);
                String name = args[2];
                pw.println("Setting name to: " + name + " on index: " + index);
                setFingerprintName(null, index, name, getCurrentUserId());
            } catch (NumberFormatException e) {
                pw.println('"' + args[1] + '"' + " is an invalid number");
            } catch (RemoteException e) {
                pw.println(Log.getStackTraceString(e));
            }
        }

        private void dumpRemoveFinger(PrintWriter pw, String[] args) {
            try {
                int index = Integer.parseInt(args[1]);
                if (index <= 0) {
                    pw.println("INVALID INDEX: index must be greater than 0");
                    return;
                }
                pw.println("Removing finger " + index);
                remove(null, index, getCurrentUserId());
                int result = nativeRemove(index);
                pw.println("Removed with result: " + result);
            } catch (NumberFormatException e) {
                pw.println('"' + args[1] + '"' + " is an invalid number");
            }
        }

        private void dumpEnrollments(PrintWriter pw, String[] args) {
            try {
                List<Fingerprint> fingerprints = getEnrolledFingerprints(null, getCurrentUserId());
                pw.println(fingerprints.size() + " fingerprints found");
                for(Fingerprint fingerprint : fingerprints) {
                    pw.println("Fingerprint " +
                            "id: " + fingerprint.getFingerId() +
                            " name: " + fingerprint.getName() +
                            " userId: " + fingerprint.getUserId());
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void dumpGetNumEnrollmentSteps(PrintWriter pw, String[] args) {
            try {
                int steps = FingerprintService.this.getNumEnrollmentSteps();
                pw.println("Number of enrollment steps: " + steps);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void dumpCommandList(PrintWriter pw) {
            pw.println("Valid Fingerprint Commands:");
            pw.println(DUMP_CMD_PRINT_ENROLLMENTS + " - Print Fingerprint Enrollments");
            pw.println(DUMP_CMD_REMOVE_FINGER + " <id> - Remove fingerprint");
            pw.println(DUMP_CMD_SET_FINGER_NAME + " <id> <name> - Rename a finger");
            pw.println(DUMP_CMD_GET_NUM_ENROLLMENT_STEPS + " - Returns num of steps the vendor" +
                    " requires to enroll a finger.");
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.FINGERPRINT_SERVICE, new FingerprintServiceWrapper());
        mHal = nativeOpenHal();
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            if (mHal == 0) {
                throw new RuntimeException(
                        "FEATURE_FINGERPRINT present, but no Fingerprint HAL loaded!");
            }
        } else if (mHal != 0) {
            throw new RuntimeException(
                    "Fingerprint HAL present, but FEATURE_FINGERPRINT is not set!");
        }
    }

}
