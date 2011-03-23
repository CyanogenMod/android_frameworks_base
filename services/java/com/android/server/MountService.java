/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server;

import com.android.internal.app.IMediaContainerService;
import com.android.server.am.ActivityManagerService;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ObbInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.IMountService;
import android.os.storage.IMountServiceListener;
import android.os.storage.IMountShutdownObserver;
import android.os.storage.IObbActionListener;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageResultCode;
import android.util.Slog;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * MountService implements back-end services for platform storage
 * management.
 * @hide - Applications should use android.os.storage.StorageManager
 * to access the MountService.
 */
class MountService extends IMountService.Stub
        implements INativeDaemonConnectorCallbacks {
    private static final boolean LOCAL_LOGD = false;
    private static final boolean DEBUG_UNMOUNT = false;
    private static final boolean DEBUG_EVENTS = false;
    private static final boolean DEBUG_OBB = false;

    private static final String TAG = "MountService";

    private static final String VOLD_TAG = "VoldConnector";

    /*
     * Internal vold volume state constants
     */
    class VolumeState {
        public static final int Init       = -1;
        public static final int NoMedia    = 0;
        public static final int Idle       = 1;
        public static final int Pending    = 2;
        public static final int Checking   = 3;
        public static final int Mounted    = 4;
        public static final int Unmounting = 5;
        public static final int Formatting = 6;
        public static final int Shared     = 7;
        public static final int SharedMnt  = 8;
    }

    /*
     * Internal vold response code constants
     */
    class VoldResponseCode {
        /*
         * 100 series - Requestion action was initiated; expect another reply
         *              before proceeding with a new command.
         */
        public static final int VolumeListResult               = 110;
        public static final int AsecListResult                 = 111;
        public static final int StorageUsersListResult         = 112;

        /*
         * 200 series - Requestion action has been successfully completed.
         */
        public static final int ShareStatusResult              = 210;
        public static final int AsecPathResult                 = 211;
        public static final int ShareEnabledResult             = 212;

        /*
         * 400 series - Command was accepted, but the requested action
         *              did not take place.
         */
        public static final int OpFailedNoMedia                = 401;
        public static final int OpFailedMediaBlank             = 402;
        public static final int OpFailedMediaCorrupt           = 403;
        public static final int OpFailedVolNotMounted          = 404;
        public static final int OpFailedStorageBusy            = 405;
        public static final int OpFailedStorageNotFound        = 406;

        /*
         * 600 series - Unsolicited broadcasts.
         */
        public static final int VolumeStateChange              = 605;
        public static final int ShareAvailabilityChange        = 620;
        public static final int VolumeDiskInserted             = 630;
        public static final int VolumeDiskRemoved              = 631;
        public static final int VolumeBadRemoval               = 632;
    }

    private Context                               mContext;
    private NativeDaemonConnector                 mConnector;
    HashMap<String, String>                       mVolumeStates = new HashMap<String, String>();
    private PackageManagerService                 mPms;
    private boolean                               mUmsEnabling;
    // Used as a lock for methods that register/unregister listeners.
    final private ArrayList<MountServiceBinderListener> mListeners =
            new ArrayList<MountServiceBinderListener>();
    private boolean                               mBooted = false;
    private boolean                               mReady = false;
    private boolean                               mSendUmsConnectedOnBoot = false;

    /**
     * Private hash of currently mounted secure containers.
     * Used as a lock in methods to manipulate secure containers.
     */
    final private HashSet<String> mAsecMountSet = new HashSet<String>();

    /**
     * The size of the crypto algorithm key in bits for OBB files. Currently
     * Twofish is used which takes 128-bit keys.
     */
    private static final int CRYPTO_ALGORITHM_KEY_SIZE = 128;

    /**
     * The number of times to run SHA1 in the PBKDF2 function for OBB files.
     * 1024 is reasonably secure and not too slow.
     */
    private static final int PBKDF2_HASH_ROUNDS = 1024;

    /**
     * Mounted OBB tracking information. Used to track the current state of all
     * OBBs.
     */
    final private Map<IBinder, List<ObbState>> mObbMounts = new HashMap<IBinder, List<ObbState>>();
    final private Map<String, ObbState> mObbPathToStateMap = new HashMap<String, ObbState>();

    class ObbState implements IBinder.DeathRecipient {
        public ObbState(String filename, int callerUid, IObbActionListener token, int nonce)
                throws RemoteException {
            this.filename = filename;
            this.callerUid = callerUid;
            this.token = token;
            this.nonce = nonce;
        }

        // OBB source filename
        String filename;

        // Binder.callingUid()
        final public int callerUid;

        // Token of remote Binder caller
        final IObbActionListener token;

        // Identifier to pass back to the token
        final int nonce;

        public IBinder getBinder() {
            return token.asBinder();
        }

        @Override
        public void binderDied() {
            ObbAction action = new UnmountObbAction(this, true);
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));
        }

        public void link() throws RemoteException {
            getBinder().linkToDeath(this, 0);
        }

        public void unlink() {
            getBinder().unlinkToDeath(this, 0);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ObbState{");
            sb.append("filename=");
            sb.append(filename);
            sb.append(",token=");
            sb.append(token.toString());
            sb.append(",callerUid=");
            sb.append(callerUid);
            sb.append('}');
            return sb.toString();
        }
    }

    // OBB Action Handler
    final private ObbActionHandler mObbActionHandler;

    // OBB action handler messages
    private static final int OBB_RUN_ACTION = 1;
    private static final int OBB_MCS_BOUND = 2;
    private static final int OBB_MCS_UNBIND = 3;
    private static final int OBB_MCS_RECONNECT = 4;
    private static final int OBB_FLUSH_MOUNT_STATE = 5;

    /*
     * Default Container Service information
     */
    static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName(
            "com.android.defcontainer", "com.android.defcontainer.DefaultContainerService");

    final private DefaultContainerConnection mDefContainerConn = new DefaultContainerConnection();

    class DefaultContainerConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG_OBB)
                Slog.i(TAG, "onServiceConnected");
            IMediaContainerService imcs = IMediaContainerService.Stub.asInterface(service);
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_MCS_BOUND, imcs));
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG_OBB)
                Slog.i(TAG, "onServiceDisconnected");
        }
    };

    // Used in the ObbActionHandler
    private IMediaContainerService mContainerService = null;

    // Handler messages
    private static final int H_UNMOUNT_PM_UPDATE = 1;
    private static final int H_UNMOUNT_PM_DONE = 2;
    private static final int H_UNMOUNT_MS = 3;
    private static final int RETRY_UNMOUNT_DELAY = 30; // in ms
    private static final int MAX_UNMOUNT_RETRIES = 4;

    /*used to send the mount command after state is idle.*/
    boolean insertionMountPending = false;
    class UnmountCallBack {
        final String path;
        final boolean force;
        int retries;

        UnmountCallBack(String path, boolean force) {
            retries = 0;
            this.path = path;
            this.force = force;
        }

        void handleFinished() {
            if (DEBUG_UNMOUNT) Slog.i(TAG, "Unmounting " + path);
            doUnmountVolume(path, true);
        }
    }

    class UmsEnableCallBack extends UnmountCallBack {
        final String method;

        UmsEnableCallBack(String path, String method, boolean force) {
            super(path, force);
            this.method = method;
        }

        @Override
        void handleFinished() {
            super.handleFinished();
            doShareUnshareVolume(path, method, true);
        }
    }

    class ShutdownCallBack extends UnmountCallBack {
        IMountShutdownObserver observer;
        ShutdownCallBack(String path, IMountShutdownObserver observer) {
            super(path, true);
            this.observer = observer;
        }

        @Override
        void handleFinished() {
            int ret = doUnmountVolume(path, true);
            if (observer != null) {
                try {
                    observer.onShutDownComplete(ret);
                } catch (RemoteException e) {
                    Slog.w(TAG, "RemoteException when shutting down");
                }
            }
        }
    }

    class MountServiceHandler extends Handler {
        ArrayList<UnmountCallBack> mForceUnmounts = new ArrayList<UnmountCallBack>();
        boolean mUpdatingStatus = false;

        MountServiceHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case H_UNMOUNT_PM_UPDATE: {
                    if (DEBUG_UNMOUNT) Slog.i(TAG, "H_UNMOUNT_PM_UPDATE");
                    UnmountCallBack ucb = (UnmountCallBack) msg.obj;
                    mForceUnmounts.add(ucb);
                    if (DEBUG_UNMOUNT) Slog.i(TAG, " registered = " + mUpdatingStatus);
                    // Register only if needed.
                    if (!mUpdatingStatus) {
                        if (DEBUG_UNMOUNT) Slog.i(TAG, "Updating external media status on PackageManager");
                        mUpdatingStatus = true;
                        mPms.updateExternalMediaStatus(false, true);
                    }
                    break;
                }
                case H_UNMOUNT_PM_DONE: {
                    if (DEBUG_UNMOUNT) Slog.i(TAG, "H_UNMOUNT_PM_DONE");
                    if (DEBUG_UNMOUNT) Slog.i(TAG, "Updated status. Processing requests");
                    mUpdatingStatus = false;
                    int size = mForceUnmounts.size();
                    int sizeArr[] = new int[size];
                    int sizeArrN = 0;
                    // Kill processes holding references first
                    ActivityManagerService ams = (ActivityManagerService)
                    ServiceManager.getService("activity");
                    for (int i = 0; i < size; i++) {
                        UnmountCallBack ucb = mForceUnmounts.get(i);
                        String path = ucb.path;
                        boolean done = false;
                        if (!ucb.force) {
                            done = true;
                        } else {
                            int pids[] = getStorageUsers(path);
                            if (pids == null || pids.length == 0) {
                                done = true;
                            } else {
                                // Eliminate system process here?
                                ams.killPids(pids, "unmount media");
                                // Confirm if file references have been freed.
                                pids = getStorageUsers(path);
                                if (pids == null || pids.length == 0) {
                                    done = true;
                                }
                            }
                        }
                        if (!done && (ucb.retries < MAX_UNMOUNT_RETRIES)) {
                            // Retry again
                            Slog.i(TAG, "Retrying to kill storage users again");
                            mHandler.sendMessageDelayed(
                                    mHandler.obtainMessage(H_UNMOUNT_PM_DONE,
                                            ucb.retries++),
                                    RETRY_UNMOUNT_DELAY);
                        } else {
                            if (ucb.retries >= MAX_UNMOUNT_RETRIES) {
                                Slog.i(TAG, "Failed to unmount media inspite of " +
                                        MAX_UNMOUNT_RETRIES + " retries. Forcibly killing processes now");
                            }
                            sizeArr[sizeArrN++] = i;
                            mHandler.sendMessage(mHandler.obtainMessage(H_UNMOUNT_MS,
                                    ucb));
                        }
                    }
                    // Remove already processed elements from list.
                    for (int i = (sizeArrN-1); i >= 0; i--) {
                        mForceUnmounts.remove(sizeArr[i]);
                    }
                    break;
                }
                case H_UNMOUNT_MS : {
                    if (DEBUG_UNMOUNT) Slog.i(TAG, "H_UNMOUNT_MS");
                    UnmountCallBack ucb = (UnmountCallBack) msg.obj;
                    ucb.handleFinished();
                    break;
                }
            }
        }
    };
    final private HandlerThread mHandlerThread;
    final private Handler mHandler;

    private void waitForReady() {
        while (mReady == false) {
            for (int retries = 5; retries > 0; retries--) {
                if (mReady) {
                    return;
                }
                SystemClock.sleep(1000);
            }
            Slog.w(TAG, "Waiting too long for mReady!");
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                mBooted = true;

                /*
                 * In the simulator, we need to broadcast a volume mounted event
                 * to make the media scanner run.
                 */
                if ("simulator".equals(SystemProperties.get("ro.product.device"))) {
                    notifyVolumeStateChange(null, "/sdcard", VolumeState.NoMedia, VolumeState.Mounted);
                    return;
                }
                new Thread() {
                    public void run() {
                        ArrayList<String> volumesToMount = getShareableVolumes();

                        for (String path: volumesToMount) {
                            try {
                                String state = getVolumeState(path);

                                if (state.equals(Environment.MEDIA_UNMOUNTED)) {
                                    int rc = doMountVolume(path);
                                    if (rc != StorageResultCode.OperationSucceeded) {
                                        Slog.e(TAG, String.format("Boot-time mount failed (%d)", rc));
                                    }
                                } else if (state.equals(Environment.MEDIA_SHARED)) {
                                    /*
                                     * Bootstrap UMS enabled state since vold indicates
                                     * the volume is shared (runtime restart while ums enabled)
                                     */
                                    notifyVolumeStateChange(null, path, VolumeState.NoMedia, VolumeState.Shared);
                                }

                                /*
                                 * If UMS was connected on boot, send the connected event
                                 * now that we're up.
                                 */
                                if (mSendUmsConnectedOnBoot) {
                                    sendUmsIntent(true);
                                    mSendUmsConnectedOnBoot = false;
                                }
                            } catch (Exception ex) {
                                Slog.e(TAG, "Boot-time mount exception", ex);
                            }
                        }
                    }
                }.start();
            }
        }
    };

    private final class MountServiceBinderListener implements IBinder.DeathRecipient {
        final IMountServiceListener mListener;

        MountServiceBinderListener(IMountServiceListener listener) {
            mListener = listener;

        }

        public void binderDied() {
            if (LOCAL_LOGD) Slog.d(TAG, "An IMountServiceListener has died!");
            synchronized (mListeners) {
                mListeners.remove(this);
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    private void doShareUnshareVolume(String path, String method, boolean enable) {
        // TODO: Add support for multiple share methods
        if (!method.equals("ums")) {
            throw new IllegalArgumentException(String.format("Method %s not supported", method));
        }

        try {
            mConnector.doCommand(String.format(
                    "volume %sshare %s %s", (enable ? "" : "un"), path, method));
        } catch (NativeDaemonConnectorException e) {
            Slog.e(TAG, "Failed to share/unshare", e);
        }
    }

    private void updatePublicVolumeState(String path, String state) {
        String currentState = getVolumeState(path);
        if (currentState.equals(state)) {
            Slog.w(TAG, String.format("Duplicate state transition (%s -> %s)", currentState, state));
            return;
        }

        if (Environment.MEDIA_UNMOUNTED.equals(state)) {
            // Tell the package manager the media is gone.
            if (isExternalStorage(path)) {
                mPms.updateExternalMediaStatus(false, false);
            }

            /*
             * Some OBBs might have been unmounted when this volume was
             * unmounted, so send a message to the handler to let it know to
             * remove those from the list of mounted OBBS.
             */
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_FLUSH_MOUNT_STATE,
                    path));
        } else if (Environment.MEDIA_MOUNTED.equals(state)) {
            // Tell the package manager the media is available for use.
            if (isExternalStorage(path)) {
                mPms.updateExternalMediaStatus(true, false);
            }
        }

        String oldState = currentState;
        setVolumeState(path, state);

        synchronized (mListeners) {
            for (int i = mListeners.size() -1; i >= 0; i--) {
                MountServiceBinderListener bl = mListeners.get(i);
                try {
                    bl.mListener.onStorageStateChanged(path, oldState, state);
                } catch (RemoteException rex) {
                    Slog.e(TAG, "Listener dead");
                    mListeners.remove(i);
                } catch (Exception ex) {
                    Slog.e(TAG, "Listener failed", ex);
                }
            }
        }
    }

    /**
     *
     * Callback from NativeDaemonConnector
     */
    public void onDaemonConnected() {
        /*
         * Since we'll be calling back into the NativeDaemonConnector,
         * we need to do our work in a new thread.
         */
        new Thread() {
            public void run() {
                /**
                 * Determine media state and UMS detection status
                 */
                String[] vols = mConnector.doListCommand(
                    "volume list", VoldResponseCode.VolumeListResult);
                for (String volstr : vols) {
                    String path = null;
                    String state = Environment.MEDIA_REMOVED;
                    try {
                        String[] tok = volstr.split(" ");
                        path = tok[1];
                        // FMT: <label> <mountpoint> <state>
                        int st = Integer.parseInt(tok[2]);
                        if (st == VolumeState.NoMedia) {
                            state = Environment.MEDIA_REMOVED;
                        } else if (st == VolumeState.Idle) {
                            state = Environment.MEDIA_UNMOUNTED;
                        } else if (st == VolumeState.Mounted) {
                            state = Environment.MEDIA_MOUNTED;
                            Slog.i(TAG, "Media already mounted on daemon connection");
                        } else if (st == VolumeState.Shared) {
                            state = Environment.MEDIA_SHARED;
                            Slog.i(TAG, "Media shared on daemon connection");
                        } else {
                            throw new Exception(String.format("Unexpected state %d", st));
                        }
                        if (state != null) {
                            if (DEBUG_EVENTS) Slog.i(TAG, "Updating valid state " + state);
                            updatePublicVolumeState(path, state);
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Error processing initial volume state", e);
                        if (path != null) updatePublicVolumeState(path, Environment.MEDIA_REMOVED);
                    }
                }

                try {
                    boolean avail = doGetShareMethodAvailable("ums");
                    notifyShareAvailabilityChange("ums", avail);
                } catch (Exception ex) {
                    Slog.w(TAG, "Failed to get share availability");
                }
                /*
                 * Now that we've done our initialization, release 
                 * the hounds!
                 */
                mReady = true;
            }
        }.start();
    }

    /**
     * Callback from NativeDaemonConnector
     */
    public boolean onEvent(int code, String raw, String[] cooked) {
        Intent in = null;

        if (DEBUG_EVENTS) {
            StringBuilder builder = new StringBuilder();
            builder.append("onEvent::");
            builder.append(" raw= " + raw);
            if (cooked != null) {
                builder.append(" cooked = " );
                for (String str : cooked) {
                    builder.append(" " + str);
                }
            }
            Slog.i(TAG, builder.toString());
        }
        if (code == VoldResponseCode.VolumeStateChange) {
            /*
             * One of the volumes we're managing has changed state.
             * Format: "NNN Volume <label> <path> state changed
             * from <old_#> (<old_str>) to <new_#> (<new_str>)"
             */
            if ((Integer.parseInt(cooked[10]) == VolumeState.Idle) &&
                 insertionMountPending == true) {
                /* If the state moves to idle after a insertion
                 * try to mount the device "Insertion mount"
                 */
                final String path = cooked[3];
                insertionMountPending = false;
                new Thread() {
                    public void run() {
                        try {
                            int rc;
                            if ((rc = doMountVolume(path)) != StorageResultCode.OperationSucceeded) {
                                Slog.w(TAG, String.format("Insertion mount failed (%d)", rc));
                            }
                        } catch (Exception ex) {
                            Slog.w(TAG, "Failed to mount media on insertion", ex);
                        }
                    }
                }.start();
           }
            notifyVolumeStateChange(
                    cooked[2], cooked[3], Integer.parseInt(cooked[7]),
                            Integer.parseInt(cooked[10]));
        } else if (code == VoldResponseCode.ShareAvailabilityChange) {
            // FMT: NNN Share method <method> now <available|unavailable>
            boolean avail = false;
            if (cooked[5].equals("available")) {
                avail = true;
            }
            notifyShareAvailabilityChange(cooked[3], avail);
        } else if ((code == VoldResponseCode.VolumeDiskInserted) ||
                   (code == VoldResponseCode.VolumeDiskRemoved) ||
                   (code == VoldResponseCode.VolumeBadRemoval)) {
            // FMT: NNN Volume <label> <mountpoint> disk inserted (<major>:<minor>)
            // FMT: NNN Volume <label> <mountpoint> disk removed (<major>:<minor>)
            // FMT: NNN Volume <label> <mountpoint> bad removal (<major>:<minor>)
            final String label = cooked[2];
            final String path = cooked[3];
            int major = -1;
            int minor = -1;

            try {
                String devComp = cooked[6].substring(1, cooked[6].length() -1);
                String[] devTok = devComp.split(":");
                major = Integer.parseInt(devTok[0]);
                minor = Integer.parseInt(devTok[1]);
            } catch (Exception ex) {
                Slog.e(TAG, "Failed to parse major/minor", ex);
            }

            if (code == VoldResponseCode.VolumeDiskInserted) {
               /* Instead of tring to mount here, wait for
                * the state to be Idle before atempting the
                * insertion mount, else "insertion mount" may fail.
                */
               insertionMountPending = true;
            } else if (code == VoldResponseCode.VolumeDiskRemoved) {
                /*
                 * This event gets trumped if we're already in BAD_REMOVAL state
                 */
                if (getVolumeState(path).equals(Environment.MEDIA_BAD_REMOVAL)) {
                    return true;
                }
                /* Send the media unmounted event first */
                if (DEBUG_EVENTS) Slog.i(TAG, "Sending unmounted event first");
                updatePublicVolumeState(path, Environment.MEDIA_UNMOUNTED);
                in = new Intent(Intent.ACTION_MEDIA_UNMOUNTED, Uri.parse("file://" + path));
                mContext.sendBroadcast(in);

                if (DEBUG_EVENTS) Slog.i(TAG, "Sending media removed");
                updatePublicVolumeState(path, Environment.MEDIA_REMOVED);
                in = new Intent(Intent.ACTION_MEDIA_REMOVED, Uri.parse("file://" + path));
            } else if (code == VoldResponseCode.VolumeBadRemoval) {
                if (DEBUG_EVENTS) Slog.i(TAG, "Sending unmounted event first");
                /* Send the media unmounted event first */
                updatePublicVolumeState(path, Environment.MEDIA_UNMOUNTED);
                in = new Intent(Intent.ACTION_MEDIA_UNMOUNTED, Uri.parse("file://" + path));
                mContext.sendBroadcast(in);

                if (DEBUG_EVENTS) Slog.i(TAG, "Sending media bad removal");
                updatePublicVolumeState(path, Environment.MEDIA_BAD_REMOVAL);
                in = new Intent(Intent.ACTION_MEDIA_BAD_REMOVAL, Uri.parse("file://" + path));
            } else {
                Slog.e(TAG, String.format("Unknown code {%d}", code));
            }
        } else {
            return false;
        }

        if (in != null) {
            mContext.sendBroadcast(in);
        }
        return true;
    }

    private void notifyVolumeStateChange(String label, String path, int oldState, int newState) {
        String vs = getVolumeState(path);
        if (DEBUG_EVENTS) Slog.i(TAG, "notifyVolumeStateChanged::" + vs);

        Intent in = null;

        if (oldState == VolumeState.Shared && newState != oldState) {
            if (LOCAL_LOGD) Slog.d(TAG, "Sending ACTION_MEDIA_UNSHARED intent");
            mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_UNSHARED,
                                                Uri.parse("file://" + path)));
        }

        if (newState == VolumeState.Init) {
        } else if (newState == VolumeState.NoMedia) {
            // NoMedia is handled via Disk Remove events
        } else if (newState == VolumeState.Idle) {
            /*
             * Don't notify if we're in BAD_REMOVAL, NOFS, UNMOUNTABLE, or
             * if we're in the process of enabling UMS
             */
            if (!vs.equals(
                    Environment.MEDIA_BAD_REMOVAL) && !vs.equals(
                            Environment.MEDIA_NOFS) && !vs.equals(
                                    Environment.MEDIA_UNMOUNTABLE) && !getUmsEnabling()) {
                if (DEBUG_EVENTS) Slog.i(TAG, "updating volume state for media bad removal nofs and unmountable");
                updatePublicVolumeState(path, Environment.MEDIA_UNMOUNTED);
                in = new Intent(Intent.ACTION_MEDIA_UNMOUNTED, Uri.parse("file://" + path));
            }
        } else if (newState == VolumeState.Pending) {
        } else if (newState == VolumeState.Checking) {
            if (DEBUG_EVENTS) Slog.i(TAG, "updating volume state checking");
            updatePublicVolumeState(path, Environment.MEDIA_CHECKING);
            in = new Intent(Intent.ACTION_MEDIA_CHECKING, Uri.parse("file://" + path));
        } else if (newState == VolumeState.Mounted) {
            if (DEBUG_EVENTS) Slog.i(TAG, "updating volume state mounted");
            updatePublicVolumeState(path, Environment.MEDIA_MOUNTED);
            in = new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + path));
            in.putExtra("read-only", false);
        } else if (newState == VolumeState.Unmounting) {
            in = new Intent(Intent.ACTION_MEDIA_EJECT, Uri.parse("file://" + path));
        } else if (newState == VolumeState.Formatting) {
        } else if (newState == VolumeState.Shared) {
            if (DEBUG_EVENTS) Slog.i(TAG, "Updating volume state media mounted");
            /* Send the media unmounted event first */
            updatePublicVolumeState(path, Environment.MEDIA_UNMOUNTED);
            in = new Intent(Intent.ACTION_MEDIA_UNMOUNTED, Uri.parse("file://" + path));
            mContext.sendBroadcast(in);

            if (DEBUG_EVENTS) Slog.i(TAG, "Updating media shared");
            updatePublicVolumeState(path, Environment.MEDIA_SHARED);
            in = new Intent(Intent.ACTION_MEDIA_SHARED, Uri.parse("file://" + path));
            if (LOCAL_LOGD) Slog.d(TAG, "Sending ACTION_MEDIA_SHARED intent");
        } else if (newState == VolumeState.SharedMnt) {
            Slog.e(TAG, "Live shared mounts not supported yet!");
            return;
        } else {
            Slog.e(TAG, "Unhandled VolumeState {" + newState + "}");
        }

        if (in != null) {
            mContext.sendBroadcast(in);
        }
    }

    private boolean doGetShareMethodAvailable(String method) {
        ArrayList<String> rsp;
        try {
            rsp = mConnector.doCommand("share status " + method);
        } catch (NativeDaemonConnectorException ex) {
            Slog.e(TAG, "Failed to determine whether share method " + method + " is available.");
            return false;
        }

        for (String line : rsp) {
            String[] tok = line.split(" ");
            if (tok.length < 3) {
                Slog.e(TAG, "Malformed response to share status " + method);
                return false;
            }

            int code;
            try {
                code = Integer.parseInt(tok[0]);
            } catch (NumberFormatException nfe) {
                Slog.e(TAG, String.format("Error parsing code %s", tok[0]));
                return false;
            }
            if (code == VoldResponseCode.ShareStatusResult) {
                if (tok[2].equals("available"))
                    return true;
                return false;
            } else {
                Slog.e(TAG, String.format("Unexpected response code %d", code));
                return false;
            }
        }
        Slog.e(TAG, "Got an empty response");
        return false;
    }

    private int doMountVolume(String path) {
        int rc = StorageResultCode.OperationSucceeded;

        if (DEBUG_EVENTS) Slog.i(TAG, "doMountVolume: Mouting " + path);
        try {
            mConnector.doCommand(String.format("volume mount %s", path));
        } catch (NativeDaemonConnectorException e) {
            /*
             * Mount failed for some reason
             */
            Intent in = null;
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedNoMedia) {
                /*
                 * Attempt to mount but no media inserted
                 */
                rc = StorageResultCode.OperationFailedNoMedia;
            } else if (code == VoldResponseCode.OpFailedMediaBlank) {
                if (DEBUG_EVENTS) Slog.i(TAG, " updating volume state :: media nofs");
                /*
                 * Media is blank or does not contain a supported filesystem
                 */
                updatePublicVolumeState(path, Environment.MEDIA_NOFS);
                in = new Intent(Intent.ACTION_MEDIA_NOFS, Uri.parse("file://" + path));
                rc = StorageResultCode.OperationFailedMediaBlank;
            } else if (code == VoldResponseCode.OpFailedMediaCorrupt) {
                if (DEBUG_EVENTS) Slog.i(TAG, "updating volume state media corrupt");
                /*
                 * Volume consistency check failed
                 */
                updatePublicVolumeState(path, Environment.MEDIA_UNMOUNTABLE);
                in = new Intent(Intent.ACTION_MEDIA_UNMOUNTABLE, Uri.parse("file://" + path));
                rc = StorageResultCode.OperationFailedMediaCorrupt;
            } else {
                rc = StorageResultCode.OperationFailedInternalError;
            }

            /*
             * Send broadcast intent (if required for the failure)
             */
            if (in != null) {
                mContext.sendBroadcast(in);
            }
        }

        return rc;
    }

    /*
     * If force is not set, we do not unmount if there are
     * processes holding references to the volume about to be unmounted.
     * If force is set, all the processes holding references need to be
     * killed via the ActivityManager before actually unmounting the volume.
     * This might even take a while and might be retried after timed delays
     * to make sure we dont end up in an instable state and kill some core
     * processes.
     */
    private int doUnmountVolume(String path, boolean force) {
        if (!getVolumeState(path).equals(Environment.MEDIA_MOUNTED)) {
            return VoldResponseCode.OpFailedVolNotMounted;
        }

        /*
         * Force a GC to make sure AssetManagers in other threads of the
         * system_server are cleaned up. We have to do this since AssetManager
         * instances are kept as a WeakReference and it's possible we have files
         * open on the external storage.
         */
        Runtime.getRuntime().gc();

        // Redundant probably. But no harm in updating state again.
        if (isExternalStorage(path)) {
            mPms.updateExternalMediaStatus(false, false);
        }
        try {
            mConnector.doCommand(String.format(
                    "volume unmount %s%s", path, (force ? " force" : "")));
            // We unmounted the volume. None of the asec containers are available now.
            synchronized (mAsecMountSet) {
                mAsecMountSet.clear();
            }
            return StorageResultCode.OperationSucceeded;
        } catch (NativeDaemonConnectorException e) {
            // Don't worry about mismatch in PackageManager since the
            // call back will handle the status changes any way.
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedVolNotMounted) {
                return StorageResultCode.OperationFailedStorageNotMounted;
            } else if (code == VoldResponseCode.OpFailedStorageBusy) {
                return StorageResultCode.OperationFailedStorageBusy;
            } else {
                return StorageResultCode.OperationFailedInternalError;
            }
        }
    }

    private int doFormatVolume(String path) {
        try {
            String cmd = String.format("volume format %s", path);
            mConnector.doCommand(cmd);
            return StorageResultCode.OperationSucceeded;
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedNoMedia) {
                return StorageResultCode.OperationFailedNoMedia;
            } else if (code == VoldResponseCode.OpFailedMediaCorrupt) {
                return StorageResultCode.OperationFailedMediaCorrupt;
            } else {
                return StorageResultCode.OperationFailedInternalError;
            }
        }
    }

    private boolean doGetVolumeShared(String path, String method) {
        String cmd = String.format("volume shared %s %s", path, method);
        ArrayList<String> rsp;

        try {
            rsp = mConnector.doCommand(cmd);
        } catch (NativeDaemonConnectorException ex) {
            Slog.e(TAG, "Failed to read response to volume shared " + path + " " + method);
            return false;
        }

        for (String line : rsp) {
            String[] tok = line.split(" ");
            if (tok.length < 3) {
                Slog.e(TAG, "Malformed response to volume shared " + path + " " + method + " command");
                return false;
            }

            int code;
            try {
                code = Integer.parseInt(tok[0]);
            } catch (NumberFormatException nfe) {
                Slog.e(TAG, String.format("Error parsing code %s", tok[0]));
                return false;
            }
            if (code == VoldResponseCode.ShareEnabledResult) {
                return "enabled".equals(tok[2]);
            } else {
                Slog.e(TAG, String.format("Unexpected response code %d", code));
                return false;
            }
        }
        Slog.e(TAG, "Got an empty response");
        return false;
    }

    private void notifyShareAvailabilityChange(String method, final boolean avail) {
        if (!method.equals("ums")) {
           Slog.w(TAG, "Ignoring unsupported share method {" + method + "}");
           return;
        }

        synchronized (mListeners) {
            for (int i = mListeners.size() -1; i >= 0; i--) {
                MountServiceBinderListener bl = mListeners.get(i);
                try {
                    bl.mListener.onUsbMassStorageConnectionChanged(avail);
                } catch (RemoteException rex) {
                    Slog.e(TAG, "Listener dead");
                    mListeners.remove(i);
                } catch (Exception ex) {
                    Slog.e(TAG, "Listener failed", ex);
                }
            }
        }

        if (mBooted == true) {
            sendUmsIntent(avail);
        } else {
            mSendUmsConnectedOnBoot = avail;
        }

        final String path = Environment.getExternalStorageDirectory().getPath();
        if (avail == false && getVolumeState(path).equals(Environment.MEDIA_SHARED)) {
            /*
             * USB mass storage disconnected while enabled
             */
            final ArrayList<String> volumes = getShareableVolumes();
            new Thread() {
                public void run() {
                    try {
                        int rc;
                        Slog.w(TAG, "Disabling UMS after cable disconnect");
                        for (String path: volumes) {
                            doShareUnshareVolume(path, "ums", false);
                            if ((rc = doMountVolume(path)) != StorageResultCode.OperationSucceeded) {
                                Slog.e(TAG, String.format(
                                        "Failed to remount {%s} on UMS enabled-disconnect (%d)",
                                                path, rc));
                            }
                        }
                    } catch (Exception ex) {
                        Slog.w(TAG, "Failed to mount media on UMS enabled-disconnect", ex);
                    }
                }
            }.start();
        }
    }

    private void sendUmsIntent(boolean c) {
        mContext.sendBroadcast(
                new Intent((c ? Intent.ACTION_UMS_CONNECTED : Intent.ACTION_UMS_DISCONNECTED)));
    }

    private void validatePermission(String perm) {
        if (mContext.checkCallingOrSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(String.format("Requires %s permission", perm));
        }
    }

    /**
     * Constructs a new MountService instance
     *
     * @param context  Binder context for this service
     */
    public MountService(Context context) {
        mContext = context;

        // XXX: This will go away soon in favor of IMountServiceObserver
        mPms = (PackageManagerService) ServiceManager.getService("package");

        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_BOOT_COMPLETED), null, null);

        mHandlerThread = new HandlerThread("MountService");
        mHandlerThread.start();
        mHandler = new MountServiceHandler(mHandlerThread.getLooper());

        // Add OBB Action Handler to MountService thread.
        mObbActionHandler = new ObbActionHandler(mHandlerThread.getLooper());

        /*
         * Vold does not run in the simulator, so pretend the connector thread
         * ran and did its thing.
         */
        if ("simulator".equals(SystemProperties.get("ro.product.device"))) {
            mReady = true;
            mUmsEnabling = true;
            return;
        }

        /*
         * Create the connection to vold with a maximum queue of twice the
         * amount of containers we'd ever expect to have. This keeps an
         * "asec list" from blocking a thread repeatedly.
         */
        mConnector = new NativeDaemonConnector(this, "vold",
                PackageManagerService.MAX_CONTAINERS * 2, VOLD_TAG);
        mReady = false;
        Thread thread = new Thread(mConnector, VOLD_TAG);
        thread.start();
    }

    /**
     * Exposed API calls below here
     */

    public void registerListener(IMountServiceListener listener) {
        synchronized (mListeners) {
            MountServiceBinderListener bl = new MountServiceBinderListener(listener);
            try {
                listener.asBinder().linkToDeath(bl, 0);
                mListeners.add(bl);
            } catch (RemoteException rex) {
                Slog.e(TAG, "Failed to link to listener death");
            }
        }
    }

    public void unregisterListener(IMountServiceListener listener) {
        synchronized (mListeners) {
            for(MountServiceBinderListener bl : mListeners) {
                if (bl.mListener == listener) {
                    mListeners.remove(mListeners.indexOf(bl));
                    return;
                }
            }
        }
    }

    public void shutdown(final IMountShutdownObserver observer) {
        validatePermission(android.Manifest.permission.SHUTDOWN);

        Slog.i(TAG, "Shutting down");

        String path = Environment.getExternalStorageDirectory().getPath();
        String state = getVolumeState(path);

        if (state.equals(Environment.MEDIA_SHARED)) {
            /*
             * If the media is currently shared, unshare it.
             * XXX: This is still dangerous!. We should not
             * be rebooting at *all* if UMS is enabled, since
             * the UMS host could have dirty FAT cache entries
             * yet to flush.
             */
            setUsbMassStorageEnabled(false);
        } else if (state.equals(Environment.MEDIA_CHECKING)) {
            /*
             * If the media is being checked, then we need to wait for
             * it to complete before being able to proceed.
             */
            // XXX: @hackbod - Should we disable the ANR timer here?
            int retries = 30;
            while (state.equals(Environment.MEDIA_CHECKING) && (retries-- >=0)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException iex) {
                    Slog.e(TAG, "Interrupted while waiting for media", iex);
                    break;
                }
                state = Environment.getExternalStorageState();
            }
            if (retries == 0) {
                Slog.e(TAG, "Timed out waiting for media to check");
            }
        }

        if (state.equals(Environment.MEDIA_MOUNTED)) {
            // Post a unmount message.
            ShutdownCallBack ucb = new ShutdownCallBack(path, observer);
            mHandler.sendMessage(mHandler.obtainMessage(H_UNMOUNT_PM_UPDATE, ucb));
        } else if (observer != null) {
            /*
             * Observer is waiting for onShutDownComplete when we are done.
             * Since nothing will be done send notification directly so shutdown
             * sequence can continue.
             */
            try {
                observer.onShutDownComplete(StorageResultCode.OperationSucceeded);
            } catch (RemoteException e) {
                Slog.w(TAG, "RemoteException when shutting down");
            }
        }
    }

    private boolean getUmsEnabling() {
        synchronized (mListeners) {
            return mUmsEnabling;
        }
    }

    private void setUmsEnabling(boolean enable) {
        synchronized (mListeners) {
            mUmsEnabling = enable;
        }
    }

    public boolean isUsbMassStorageConnected() {
        waitForReady();

        if (getUmsEnabling()) {
            return true;
        }
        return doGetShareMethodAvailable("ums");
    }

    private boolean isExternalStorage(String path) {
        return Environment.getExternalStorageDirectory().getPath().equals(path);
    }

    private ArrayList<String> getShareableVolumes() {
        // build.prop will specify additional volumes to mount in the
        // ro.additionalmounts property.
        // This is a semicolon delimited list of paths. Such as "/emmc;/foo", etc.
        ArrayList<String> volumesToMount = new ArrayList<String>();
        volumesToMount.add(Environment.getExternalStorageDirectory().getPath());
        String additionalVolumesProperty = SystemProperties.get("ro.additionalmounts");
        if (null != additionalVolumesProperty) {
            String[] additionalVolumes = additionalVolumesProperty.split(";");
            for (String additionalVolume: additionalVolumes) {
                if (!"".equals(additionalVolume)) {
                    volumesToMount.add(additionalVolume);
                }
            }
        }
        return volumesToMount;
    }

    public void setUsbMassStorageEnabled(boolean enable) {
        waitForReady();
        validatePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);

        ArrayList<String> volumesToShare = getShareableVolumes();

        for (String path: volumesToShare) {
            /*
             * If the volume is mounted and we're enabling then unmount it
             */
            String vs = getVolumeState(path);
            String method = "ums";
            if (enable && vs.equals(Environment.MEDIA_MOUNTED)) {
                // Override for isUsbMassStorageEnabled()
                setUmsEnabling(enable);
                UmsEnableCallBack umscb = new UmsEnableCallBack(path, method, true);
                int msg = isExternalStorage(path) ? H_UNMOUNT_PM_UPDATE : H_UNMOUNT_MS;
                mHandler.sendMessage(mHandler.obtainMessage(msg, umscb));
                // Clear override
                setUmsEnabling(false);
            }
            /*
             * If we disabled UMS then mount the volume
             */
            if (!enable) {
                doShareUnshareVolume(path, method, enable);
                if (doMountVolume(path) != StorageResultCode.OperationSucceeded) {
                    Slog.e(TAG, "Failed to remount " + path +
                            " after disabling share method " + method);
                    /*
                     * Even though the mount failed, the unshare didn't so don't indicate an error.
                     * The mountVolume() call will have set the storage state and sent the necessary
                     * broadcasts.
                     */
                }
            }
        }
    }

    public boolean isUsbMassStorageEnabled() {
        waitForReady();
        return doGetVolumeShared(Environment.getExternalStorageDirectory().getPath(), "ums");
    }
    
    /**
     * @return state of the volume at the specified mount point
     */
    public String getVolumeState(String mountPoint) {
        String volumeState = mVolumeStates.get(mountPoint);
        if (null == volumeState) {
            setVolumeState(mountPoint, Environment.MEDIA_REMOVED);
            return Environment.MEDIA_REMOVED;
        }

        return volumeState;
    }

    private void setVolumeState(String mountPoint, String volumeState) {
        mVolumeStates.put(mountPoint, volumeState);
    }

    public int mountVolume(String path) {
        validatePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);

        waitForReady();
        return doMountVolume(path);
    }

    public void unmountVolume(String path, boolean force) {
        validatePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        String volState = getVolumeState(path);
        if (DEBUG_UNMOUNT) Slog.i(TAG, "Unmounting " + path + " force = " + force);
        if (Environment.MEDIA_UNMOUNTED.equals(volState) ||
                Environment.MEDIA_REMOVED.equals(volState) ||
                Environment.MEDIA_SHARED.equals(volState) ||
                Environment.MEDIA_UNMOUNTABLE.equals(volState)) {
            // Media already unmounted or cannot be unmounted.
            // TODO return valid return code when adding observer call back.
            return;
        }
        UnmountCallBack ucb = new UnmountCallBack(path, force);
        int msg = isExternalStorage(path) ? H_UNMOUNT_PM_UPDATE : H_UNMOUNT_MS;
        mHandler.sendMessage(mHandler.obtainMessage(msg, ucb));
    }

    public int formatVolume(String path) {
        validatePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        waitForReady();

        return doFormatVolume(path);
    }

    public int []getStorageUsers(String path) {
        validatePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();
        try {
            String[] r = mConnector.doListCommand(
                    String.format("storage users %s", path),
                            VoldResponseCode.StorageUsersListResult);
            // FMT: <pid> <process name>
            int[] data = new int[r.length];
            for (int i = 0; i < r.length; i++) {
                String []tok = r[i].split(" ");
                try {
                    data[i] = Integer.parseInt(tok[0]);
                } catch (NumberFormatException nfe) {
                    Slog.e(TAG, String.format("Error parsing pid %s", tok[0]));
                    return new int[0];
                }
            }
            return data;
        } catch (NativeDaemonConnectorException e) {
            Slog.e(TAG, "Failed to retrieve storage users list", e);
            return new int[0];
        }
    }

    private void warnOnNotMounted() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Slog.w(TAG, "getSecureContainerList() called when storage not mounted");
        }
    }

    public String[] getSecureContainerList() {
        validatePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        try {
            return mConnector.doListCommand("asec list", VoldResponseCode.AsecListResult);
        } catch (NativeDaemonConnectorException e) {
            return new String[0];
        }
    }

    public int createSecureContainer(String id, int sizeMb, String fstype,
                                    String key, int ownerUid) {
        validatePermission(android.Manifest.permission.ASEC_CREATE);
        waitForReady();
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        String cmd = String.format("asec create %s %d %s %s %d", id, sizeMb, fstype, key, ownerUid);
        try {
            mConnector.doCommand(cmd);
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.add(id);
            }
        }
        return rc;
    }

    public int finalizeSecureContainer(String id) {
        validatePermission(android.Manifest.permission.ASEC_CREATE);
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.doCommand(String.format("asec finalize %s", id));
            /*
             * Finalization does a remount, so no need
             * to update mAsecMountSet
             */
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }
        return rc;
    }

    public int destroySecureContainer(String id, boolean force) {
        validatePermission(android.Manifest.permission.ASEC_DESTROY);
        waitForReady();
        warnOnNotMounted();

        /*
         * Force a GC to make sure AssetManagers in other threads of the
         * system_server are cleaned up. We have to do this since AssetManager
         * instances are kept as a WeakReference and it's possible we have files
         * open on the external storage.
         */
        Runtime.getRuntime().gc();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.doCommand(String.format("asec destroy %s%s", id, (force ? " force" : "")));
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedStorageBusy;
            } else {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                if (mAsecMountSet.contains(id)) {
                    mAsecMountSet.remove(id);
                }
            }
        }

        return rc;
    }
   
    public int mountSecureContainer(String id, String key, int ownerUid) {
        validatePermission(android.Manifest.permission.ASEC_MOUNT_UNMOUNT);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            if (mAsecMountSet.contains(id)) {
                return StorageResultCode.OperationFailedStorageMounted;
            }
        }

        int rc = StorageResultCode.OperationSucceeded;
        String cmd = String.format("asec mount %s %s %d", id, key, ownerUid);
        try {
            mConnector.doCommand(cmd);
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code != VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.add(id);
            }
        }
        return rc;
    }

    public int unmountSecureContainer(String id, boolean force) {
        validatePermission(android.Manifest.permission.ASEC_MOUNT_UNMOUNT);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            if (!mAsecMountSet.contains(id)) {
                return StorageResultCode.OperationFailedStorageNotMounted;
            }
         }

        /*
         * Force a GC to make sure AssetManagers in other threads of the
         * system_server are cleaned up. We have to do this since AssetManager
         * instances are kept as a WeakReference and it's possible we have files
         * open on the external storage.
         */
        Runtime.getRuntime().gc();

        int rc = StorageResultCode.OperationSucceeded;
        String cmd = String.format("asec unmount %s%s", id, (force ? " force" : ""));
        try {
            mConnector.doCommand(cmd);
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedStorageBusy;
            } else {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.remove(id);
            }
        }
        return rc;
    }

    public boolean isSecureContainerMounted(String id) {
        validatePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            return mAsecMountSet.contains(id);
        }
    }

    public int renameSecureContainer(String oldId, String newId) {
        validatePermission(android.Manifest.permission.ASEC_RENAME);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            /*
             * Because a mounted container has active internal state which cannot be 
             * changed while active, we must ensure both ids are not currently mounted.
             */
            if (mAsecMountSet.contains(oldId) || mAsecMountSet.contains(newId)) {
                return StorageResultCode.OperationFailedStorageMounted;
            }
        }

        int rc = StorageResultCode.OperationSucceeded;
        String cmd = String.format("asec rename %s %s", oldId, newId);
        try {
            mConnector.doCommand(cmd);
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }

        return rc;
    }

    public String getSecureContainerPath(String id) {
        validatePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        try {
            ArrayList<String> rsp = mConnector.doCommand(String.format("asec path %s", id));
            String []tok = rsp.get(0).split(" ");
            int code = Integer.parseInt(tok[0]);
            if (code != VoldResponseCode.AsecPathResult) {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
            return tok[1];
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageNotFound) {
                throw new IllegalArgumentException(String.format("Container '%s' not found", id));
            } else {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
        }
    }

    public void finishMediaUpdate() {
        mHandler.sendEmptyMessage(H_UNMOUNT_PM_DONE);
    }

    private boolean isUidOwnerOfPackageOrSystem(String packageName, int callerUid) {
        if (callerUid == android.os.Process.SYSTEM_UID) {
            return true;
        }

        if (packageName == null) {
            return false;
        }

        final int packageUid = mPms.getPackageUid(packageName);

        if (DEBUG_OBB) {
            Slog.d(TAG, "packageName = " + packageName + ", packageUid = " +
                    packageUid + ", callerUid = " + callerUid);
        }

        return callerUid == packageUid;
    }

    public String getMountedObbPath(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("filename cannot be null");
        }

        waitForReady();
        warnOnNotMounted();

        try {
            ArrayList<String> rsp = mConnector.doCommand(String.format("obb path %s", filename));
            String []tok = rsp.get(0).split(" ");
            int code = Integer.parseInt(tok[0]);
            if (code != VoldResponseCode.AsecPathResult) {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
            return tok[1];
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageNotFound) {
                return null;
            } else {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
        }
    }

    public boolean isObbMounted(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("filename cannot be null");
        }

        synchronized (mObbMounts) {
            return mObbPathToStateMap.containsKey(filename);
        }
    }

    public void mountObb(String filename, String key, IObbActionListener token, int nonce)
            throws RemoteException {
        if (filename == null) {
            throw new IllegalArgumentException("filename cannot be null");
        }

        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }

        final int callerUid = Binder.getCallingUid();
        final ObbState obbState = new ObbState(filename, callerUid, token, nonce);
        final ObbAction action = new MountObbAction(obbState, key);
        mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));

        if (DEBUG_OBB)
            Slog.i(TAG, "Send to OBB handler: " + action.toString());
    }

    public void unmountObb(String filename, boolean force, IObbActionListener token, int nonce)
            throws RemoteException {
        if (filename == null) {
            throw new IllegalArgumentException("filename cannot be null");
        }

        final int callerUid = Binder.getCallingUid();
        final ObbState obbState = new ObbState(filename, callerUid, token, nonce);
        final ObbAction action = new UnmountObbAction(obbState, force);
        mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));

        if (DEBUG_OBB)
            Slog.i(TAG, "Send to OBB handler: " + action.toString());
    }

    private void addObbStateLocked(ObbState obbState) throws RemoteException {
        final IBinder binder = obbState.getBinder();
        List<ObbState> obbStates = mObbMounts.get(binder);

        if (obbStates == null) {
            obbStates = new ArrayList<ObbState>();
            mObbMounts.put(binder, obbStates);
        } else {
            for (final ObbState o : obbStates) {
                if (o.filename.equals(obbState.filename)) {
                    throw new IllegalStateException("Attempt to add ObbState twice. "
                            + "This indicates an error in the MountService logic.");
                }
            }
        }

        obbStates.add(obbState);
        try {
            obbState.link();
        } catch (RemoteException e) {
            /*
             * The binder died before we could link it, so clean up our state
             * and return failure.
             */
            obbStates.remove(obbState);
            if (obbStates.isEmpty()) {
                mObbMounts.remove(binder);
            }

            // Rethrow the error so mountObb can get it
            throw e;
        }

        mObbPathToStateMap.put(obbState.filename, obbState);
    }

    private void removeObbStateLocked(ObbState obbState) {
        final IBinder binder = obbState.getBinder();
        final List<ObbState> obbStates = mObbMounts.get(binder);
        if (obbStates != null) {
            if (obbStates.remove(obbState)) {
                obbState.unlink();
            }
            if (obbStates.isEmpty()) {
                mObbMounts.remove(binder);
            }
        }

        mObbPathToStateMap.remove(obbState.filename);
    }

    private class ObbActionHandler extends Handler {
        private boolean mBound = false;
        private final List<ObbAction> mActions = new LinkedList<ObbAction>();

        ObbActionHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OBB_RUN_ACTION: {
                    final ObbAction action = (ObbAction) msg.obj;

                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_RUN_ACTION: " + action.toString());

                    // If a bind was already initiated we don't really
                    // need to do anything. The pending install
                    // will be processed later on.
                    if (!mBound) {
                        // If this is the only one pending we might
                        // have to bind to the service again.
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            action.handleError();
                            return;
                        }
                    }

                    mActions.add(action);
                    break;
                }
                case OBB_MCS_BOUND: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_BOUND");
                    if (msg.obj != null) {
                        mContainerService = (IMediaContainerService) msg.obj;
                    }
                    if (mContainerService == null) {
                        // Something seriously wrong. Bail out
                        Slog.e(TAG, "Cannot bind to media container service");
                        for (ObbAction action : mActions) {
                            // Indicate service bind error
                            action.handleError();
                        }
                        mActions.clear();
                    } else if (mActions.size() > 0) {
                        final ObbAction action = mActions.get(0);
                        if (action != null) {
                            action.execute(this);
                        }
                    } else {
                        // Should never happen ideally.
                        Slog.w(TAG, "Empty queue");
                    }
                    break;
                }
                case OBB_MCS_RECONNECT: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_RECONNECT");
                    if (mActions.size() > 0) {
                        if (mBound) {
                            disconnectService();
                        }
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            for (ObbAction action : mActions) {
                                // Indicate service bind error
                                action.handleError();
                            }
                            mActions.clear();
                        }
                    }
                    break;
                }
                case OBB_MCS_UNBIND: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_UNBIND");

                    // Delete pending install
                    if (mActions.size() > 0) {
                        mActions.remove(0);
                    }
                    if (mActions.size() == 0) {
                        if (mBound) {
                            disconnectService();
                        }
                    } else {
                        // There are more pending requests in queue.
                        // Just post MCS_BOUND message to trigger processing
                        // of next pending install.
                        mObbActionHandler.sendEmptyMessage(OBB_MCS_BOUND);
                    }
                    break;
                }
                case OBB_FLUSH_MOUNT_STATE: {
                    final String path = (String) msg.obj;

                    if (DEBUG_OBB)
                        Slog.i(TAG, "Flushing all OBB state for path " + path);

                    synchronized (mObbMounts) {
                        final List<ObbState> obbStatesToRemove = new LinkedList<ObbState>();

                        final Iterator<Entry<String, ObbState>> i =
                                mObbPathToStateMap.entrySet().iterator();
                        while (i.hasNext()) {
                            final Entry<String, ObbState> obbEntry = i.next();

                            /*
                             * If this entry's source file is in the volume path
                             * that got unmounted, remove it because it's no
                             * longer valid.
                             */
                            if (obbEntry.getKey().startsWith(path)) {
                                obbStatesToRemove.add(obbEntry.getValue());
                            }
                        }

                        for (final ObbState obbState : obbStatesToRemove) {
                            if (DEBUG_OBB)
                                Slog.i(TAG, "Removing state for " + obbState.filename);

                            removeObbStateLocked(obbState);

                            try {
                                obbState.token.onObbResult(obbState.filename, obbState.nonce,
                                        OnObbStateChangeListener.UNMOUNTED);
                            } catch (RemoteException e) {
                                Slog.i(TAG, "Couldn't send unmount notification for  OBB: "
                                        + obbState.filename);
                            }
                        }
                    }
                    break;
                }
            }
        }

        private boolean connectToService() {
            if (DEBUG_OBB)
                Slog.i(TAG, "Trying to bind to DefaultContainerService");

            Intent service = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
            if (mContext.bindService(service, mDefContainerConn, Context.BIND_AUTO_CREATE)) {
                mBound = true;
                return true;
            }
            return false;
        }

        private void disconnectService() {
            mContainerService = null;
            mBound = false;
            mContext.unbindService(mDefContainerConn);
        }
    }

    abstract class ObbAction {
        private static final int MAX_RETRIES = 3;
        private int mRetries;

        ObbState mObbState;

        ObbAction(ObbState obbState) {
            mObbState = obbState;
        }

        public void execute(ObbActionHandler handler) {
            try {
                if (DEBUG_OBB)
                    Slog.i(TAG, "Starting to execute action: " + this.toString());
                mRetries++;
                if (mRetries > MAX_RETRIES) {
                    Slog.w(TAG, "Failed to invoke remote methods on default container service. Giving up");
                    mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
                    handleError();
                    return;
                } else {
                    handleExecute();
                    if (DEBUG_OBB)
                        Slog.i(TAG, "Posting install MCS_UNBIND");
                    mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
                }
            } catch (RemoteException e) {
                if (DEBUG_OBB)
                    Slog.i(TAG, "Posting install MCS_RECONNECT");
                mObbActionHandler.sendEmptyMessage(OBB_MCS_RECONNECT);
            } catch (Exception e) {
                if (DEBUG_OBB)
                    Slog.d(TAG, "Error handling OBB action", e);
                handleError();
                mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
            }
        }

        abstract void handleExecute() throws RemoteException, IOException;
        abstract void handleError();

        protected ObbInfo getObbInfo() throws IOException {
            ObbInfo obbInfo;
            try {
                obbInfo = mContainerService.getObbInfo(mObbState.filename);
            } catch (RemoteException e) {
                Slog.d(TAG, "Couldn't call DefaultContainerService to fetch OBB info for "
                        + mObbState.filename);
                obbInfo = null;
            }
            if (obbInfo == null) {
                throw new IOException("Couldn't read OBB file: " + mObbState.filename);
            }
            return obbInfo;
        }

        protected void sendNewStatusOrIgnore(int status) {
            if (mObbState == null || mObbState.token == null) {
                return;
            }

            try {
                mObbState.token.onObbResult(mObbState.filename, mObbState.nonce, status);
            } catch (RemoteException e) {
                Slog.w(TAG, "MountServiceListener went away while calling onObbStateChanged");
            }
        }
    }

    class MountObbAction extends ObbAction {
        private String mKey;

        MountObbAction(ObbState obbState, String key) {
            super(obbState);
            mKey = key;
        }

        public void handleExecute() throws IOException, RemoteException {
            waitForReady();
            warnOnNotMounted();

            final ObbInfo obbInfo = getObbInfo();

            if (!isUidOwnerOfPackageOrSystem(obbInfo.packageName, mObbState.callerUid)) {
                Slog.w(TAG, "Denied attempt to mount OBB " + obbInfo.filename
                        + " which is owned by " + obbInfo.packageName);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_PERMISSION_DENIED);
                return;
            }

            final boolean isMounted;
            synchronized (mObbMounts) {
                isMounted = mObbPathToStateMap.containsKey(obbInfo.filename);
            }
            if (isMounted) {
                Slog.w(TAG, "Attempt to mount OBB which is already mounted: " + obbInfo.filename);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_ALREADY_MOUNTED);
                return;
            }

            /*
             * The filename passed in might not be the canonical name, so just
             * set the filename to the canonicalized version.
             */
            mObbState.filename = obbInfo.filename;

            final String hashedKey;
            if (mKey == null) {
                hashedKey = "none";
            } else {
                try {
                    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

                    KeySpec ks = new PBEKeySpec(mKey.toCharArray(), obbInfo.salt,
                            PBKDF2_HASH_ROUNDS, CRYPTO_ALGORITHM_KEY_SIZE);
                    SecretKey key = factory.generateSecret(ks);
                    BigInteger bi = new BigInteger(key.getEncoded());
                    hashedKey = bi.toString(16);
                } catch (NoSuchAlgorithmException e) {
                    Slog.e(TAG, "Could not load PBKDF2 algorithm", e);
                    sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
                    return;
                } catch (InvalidKeySpecException e) {
                    Slog.e(TAG, "Invalid key spec when loading PBKDF2 algorithm", e);
                    sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
                    return;
                }
            }

            int rc = StorageResultCode.OperationSucceeded;
            String cmd = String.format("obb mount %s %s %d", mObbState.filename, hashedKey,
                    mObbState.callerUid);
            try {
                mConnector.doCommand(cmd);
            } catch (NativeDaemonConnectorException e) {
                int code = e.getCode();
                if (code != VoldResponseCode.OpFailedStorageBusy) {
                    rc = StorageResultCode.OperationFailedInternalError;
                }
            }

            if (rc == StorageResultCode.OperationSucceeded) {
                if (DEBUG_OBB)
                    Slog.d(TAG, "Successfully mounted OBB " + mObbState.filename);

                synchronized (mObbMounts) {
                    addObbStateLocked(mObbState);
                }

                sendNewStatusOrIgnore(OnObbStateChangeListener.MOUNTED);
            } else {
                Slog.e(TAG, "Couldn't mount OBB file: " + rc);

                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_COULD_NOT_MOUNT);
            }
        }

        public void handleError() {
            sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("MountObbAction{");
            sb.append("filename=");
            sb.append(mObbState.filename);
            sb.append(",callerUid=");
            sb.append(mObbState.callerUid);
            sb.append(",token=");
            sb.append(mObbState.token != null ? mObbState.token.toString() : "NULL");
            sb.append(",binder=");
            sb.append(mObbState.token != null ? mObbState.getBinder().toString() : "null");
            sb.append('}');
            return sb.toString();
        }
    }

    class UnmountObbAction extends ObbAction {
        private boolean mForceUnmount;

        UnmountObbAction(ObbState obbState, boolean force) {
            super(obbState);
            mForceUnmount = force;
        }

        public void handleExecute() throws IOException {
            waitForReady();
            warnOnNotMounted();

            final ObbInfo obbInfo = getObbInfo();

            final ObbState obbState;
            synchronized (mObbMounts) {
                obbState = mObbPathToStateMap.get(obbInfo.filename);
            }

            if (obbState == null) {
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_NOT_MOUNTED);
                return;
            }

            if (obbState.callerUid != mObbState.callerUid) {
                Slog.w(TAG, "Permission denied attempting to unmount OBB " + obbInfo.filename
                        + " (owned by " + obbInfo.packageName + ")");
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_PERMISSION_DENIED);
                return;
            }

            mObbState.filename = obbInfo.filename;

            int rc = StorageResultCode.OperationSucceeded;
            String cmd = String.format("obb unmount %s%s", mObbState.filename,
                    (mForceUnmount ? " force" : ""));
            try {
                mConnector.doCommand(cmd);
            } catch (NativeDaemonConnectorException e) {
                int code = e.getCode();
                if (code == VoldResponseCode.OpFailedStorageBusy) {
                    rc = StorageResultCode.OperationFailedStorageBusy;
                } else if (code == VoldResponseCode.OpFailedStorageNotFound) {
                    // If it's not mounted then we've already won.
                    rc = StorageResultCode.OperationSucceeded;
                } else {
                    rc = StorageResultCode.OperationFailedInternalError;
                }
            }

            if (rc == StorageResultCode.OperationSucceeded) {
                synchronized (mObbMounts) {
                    removeObbStateLocked(obbState);
                }

                sendNewStatusOrIgnore(OnObbStateChangeListener.UNMOUNTED);
            } else {
                Slog.w(TAG, "Could not mount OBB: " + mObbState.filename);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_COULD_NOT_UNMOUNT);
            }
        }

        public void handleError() {
            sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("UnmountObbAction{");
            sb.append("filename=");
            sb.append(mObbState.filename != null ? mObbState.filename : "null");
            sb.append(",force=");
            sb.append(mForceUnmount);
            sb.append(",callerUid=");
            sb.append(mObbState.callerUid);
            sb.append(",token=");
            sb.append(mObbState.token != null ? mObbState.token.toString() : "null");
            sb.append(",binder=");
            sb.append(mObbState.token != null ? mObbState.getBinder().toString() : "null");
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ActivityManager from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }

        synchronized (mObbMounts) {
            pw.println("  mObbMounts:");

            final Iterator<Entry<IBinder, List<ObbState>>> binders = mObbMounts.entrySet().iterator();
            while (binders.hasNext()) {
                Entry<IBinder, List<ObbState>> e = binders.next();
                pw.print("    Key="); pw.println(e.getKey().toString());
                final List<ObbState> obbStates = e.getValue();
                for (final ObbState obbState : obbStates) {
                    pw.print("      "); pw.println(obbState.toString());
                }
            }

            pw.println("");
            pw.println("  mObbPathToStateMap:");
            final Iterator<Entry<String, ObbState>> maps = mObbPathToStateMap.entrySet().iterator();
            while (maps.hasNext()) {
                final Entry<String, ObbState> e = maps.next();
                pw.print("    "); pw.print(e.getKey());
                pw.print(" -> "); pw.println(e.getValue().toString());
            }
        }
    }
}

