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

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.fingerprint.FingerprintManager;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.server.SystemService;

import android.service.fingerprint.FingerprintUtils;
import android.service.fingerprint.IFingerprintService;
import android.service.fingerprint.IFingerprintServiceReceiver;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Iterator;

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
    public static final String USE_FINGERPRINT = "android.permission.USE_FINGERPRINT";
    public static final String ENROLL_FINGERPRINT = "android.permission.ENROLL_FINGERPRINT";

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

    // JNI methods for communicating from HAL to clients
    void notify(int msg, int arg1, int arg2) {
        mHandler.obtainMessage(MSG_NOTIFY, msg, arg1, arg2).sendToTarget();
    }

    void handleNotify(int msg, int arg1, int arg2) {
        Slog.v(TAG, "handleNotify(msg=" + msg + ", arg1=" + arg1 + ", arg2=" + arg2 + ")");
        int newState = mState;
        for (Iterator<Entry<IBinder, ClientData>> it = mClients.entrySet().iterator();
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
                        try {
                            if (clientData != null && clientData.receiver != null) {
                                clientData.receiver.onEnrollResult(fingerId, remaining);
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG, "can't send message to client. Did it die?", e);
                            it.remove();
                        }
                        // Update the database with new finger id.
                        // TODO: move to client code (Settings)
                        if (remaining == 0) {
                            FingerprintUtils.addFingerprintIdForUser(fingerId,
                                    mContext.getContentResolver(), clientData.userId);
                            newState = STATE_IDLE;
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

    void checkPermission(String permisison) {
        // TODO
    }

    private final class FingerprintServiceWrapper extends IFingerprintService.Stub {
        @Override // Binder call
        public void authenticate(IBinder token, int userId) {
            checkPermission(USE_FINGERPRINT);
            startAuthentication(token, userId);
        }

        @Override // Binder call
        public void enroll(IBinder token, long timeout, int userId) {
            checkPermission(ENROLL_FINGERPRINT);
            startEnroll(token, timeout, userId);
        }

        @Override // Binder call
        public void cancel(IBinder token,int userId) {
            checkPermission(USE_FINGERPRINT);
            startCancel(token, userId);
        }

        @Override // Binder call
        public void remove(IBinder token, int fingerprintId, int userId) {
            checkPermission(ENROLL_FINGERPRINT); // TODO: Maybe have another permission
            startRemove(token, fingerprintId, userId);
        }

        @Override // Binder call
        public void startListening(IBinder token, IFingerprintServiceReceiver receiver, int userId)
        {
            checkPermission(USE_FINGERPRINT);
            addListener(token, receiver, userId);
        }

        @Override // Binder call
        public void stopListening(IBinder token, int userId) {
            checkPermission(USE_FINGERPRINT);
            removeListener(token, userId);
        }
    }

    @Override
    public void onStart() {
       publishBinderService(Context.FINGERPRINT_SERVICE, new FingerprintServiceWrapper());
       mHal = nativeOpenHal();
    }

}
