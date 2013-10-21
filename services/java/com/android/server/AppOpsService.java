/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.Xml;

import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IAppOpsCallback;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import com.android.server.PermissionDialogResult.Result;

public class AppOpsService extends IAppOpsService.Stub {
    static final String TAG = "AppOps";
    static final boolean DEBUG = false;
    static final String STRICT_PERMISSION_PROPERTY = "persist.sys.strict_op_enable";
    static final String WHITELIST_FILE = "persist.sys.whitelist";

    // Write at most every 30 minutes.
    static final long WRITE_DELAY = DEBUG ? 1000 : 30*60*1000;

    Context mContext;
    final AtomicFile mFile;
    final Handler mHandler;
    final boolean mStrictEnable;

    static final int SHOW_PERMISSION_DIALOG = 1;

    private static final int[] PRIVACY_GUARD_OP_STATES = new int[] {
        AppOpsManager.OP_COARSE_LOCATION,
        AppOpsManager.OP_READ_CALL_LOG,
        AppOpsManager.OP_READ_CONTACTS,
        AppOpsManager.OP_READ_CALENDAR,
        AppOpsManager.OP_READ_SMS
    };

    boolean mWriteScheduled;
    final Runnable mWriteRunner = new Runnable() {
        public void run() {
            synchronized (AppOpsService.this) {
                mWriteScheduled = false;
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override protected Void doInBackground(Void... params) {
                        writeState();
                        return null;
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            }
        }
    };

    final ArrayList<String> mWhitelist = new ArrayList<String>();

    final SparseArray<HashMap<String, Ops>> mUidOps
            = new SparseArray<HashMap<String, Ops>>();

    public final static class Ops extends SparseArray<Op> {
        public final String packageName;
        public final int uid;

        public Ops(String _packageName, int _uid) {
            packageName = _packageName;
            uid = _uid;
        }
    }

    public final static class Op {
        public final int op;
        public int mode;
        public final int defaultMode;
        public int duration;
        public long time;
        public long rejectTime;
        public int nesting;
        public int tempNesting;
        public PermissionDialogResult dialogResult;
        public int allowedCount;
        public int ignoredCount;

        public Op(int _op, boolean strict) {
            op = _op;
            if (!strict) {
                mode = AppOpsManager.MODE_ALLOWED;
            } else {
                mode = AppOpsManager.MODE_ASK;
            }
            dialogResult = new PermissionDialogResult();
            defaultMode = mode;
        }
    }

    final SparseArray<ArrayList<Callback>> mOpModeWatchers
            = new SparseArray<ArrayList<Callback>>();
    final HashMap<String, ArrayList<Callback>> mPackageModeWatchers
            = new HashMap<String, ArrayList<Callback>>();
    final HashMap<IBinder, Callback> mModeWatchers
            = new HashMap<IBinder, Callback>();

    public final class Callback implements DeathRecipient {
        final IAppOpsCallback mCallback;

        public Callback(IAppOpsCallback callback) {
            mCallback = callback;
            try {
                mCallback.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
            }
        }

        public void unlinkToDeath() {
            mCallback.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            stopWatchingMode(mCallback);
        }
    }

    public AppOpsService(File storagePath) {
        mStrictEnable = "true".equals(SystemProperties.get(STRICT_PERMISSION_PROPERTY));
        mFile = new AtomicFile(storagePath);
        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case SHOW_PERMISSION_DIALOG: {
                    Bundle data = msg.getData();
                    synchronized (this) {
                        Op op = (Op) msg.obj;
                        Result res = (Result) data.getParcelable("result");
                        op.dialogResult.register(res);
                        if (op.dialogResult.mDialog == null) {
                            Integer code = data.getInt("code");
                            Integer uid  = data.getInt("uid");
                            String packageName = data.getString("packageName");
                            PermissionDialog d = new PermissionDialog(mContext, AppOpsService.this,
                                    code, uid, packageName);
                            op.dialogResult.mDialog = d;
                            d.show();
                        }
                    }
                }break;
                }
            }
        };
        readWhitelist();
        readState();
    }

    public void publish(Context context) {
        mContext = context;
        ServiceManager.addService(Context.APP_OPS_SERVICE, asBinder());
    }

    public void systemReady() {
        synchronized (this) {
            boolean changed = false;
            for (int i=0; i<mUidOps.size(); i++) {
                HashMap<String, Ops> pkgs = mUidOps.valueAt(i);
                Iterator<Ops> it = pkgs.values().iterator();
                while (it.hasNext()) {
                    Ops ops = it.next();
                    int curUid;
                    try {
                        curUid = mContext.getPackageManager().getPackageUid(ops.packageName,
                                UserHandle.getUserId(ops.uid));
                    } catch (NameNotFoundException e) {
                        curUid = -1;
                    }
                    if (curUid != ops.uid) {
                        Slog.i(TAG, "Pruning old package " + ops.packageName
                                + "/" + ops.uid + ": new uid=" + curUid);
                        it.remove();
                        changed = true;
                    }
                }
                if (pkgs.size() <= 0) {
                    mUidOps.removeAt(i);
                }
            }
            if (changed) {
                scheduleWriteLocked();
            }
        }
    }

    public void packageRemoved(int uid, String packageName) {
        synchronized (this) {
            HashMap<String, Ops> pkgs = mUidOps.get(uid);
            if (pkgs != null) {
                if (pkgs.remove(packageName) != null) {
                    if (pkgs.size() <= 0) {
                        mUidOps.remove(uid);
                    }
                    scheduleWriteLocked();
                }
            }
        }
    }

    public void uidRemoved(int uid) {
        synchronized (this) {
            if (mUidOps.indexOfKey(uid) >= 0) {
                mUidOps.remove(uid);
                scheduleWriteLocked();
            }
        }
    }

    public void shutdown() {
        Slog.w(TAG, "Writing app ops before shutdown...");
        boolean doWrite = false;
        synchronized (this) {
            if (mWriteScheduled) {
                mWriteScheduled = false;
                doWrite = true;
            }
        }
        if (doWrite) {
            writeState();
        }
    }

    private ArrayList<AppOpsManager.OpEntry> collectOps(Ops pkgOps, int[] ops) {
        ArrayList<AppOpsManager.OpEntry> resOps = null;
        if (ops == null) {
            resOps = new ArrayList<AppOpsManager.OpEntry>();
            for (int j=0; j<pkgOps.size(); j++) {
                Op curOp = pkgOps.valueAt(j);
                resOps.add(new AppOpsManager.OpEntry(curOp.op, curOp.mode, curOp.time,
                        curOp.rejectTime, curOp.duration,
                        curOp.allowedCount, curOp.ignoredCount));
            }
        } else {
            for (int j=0; j<ops.length; j++) {
                Op curOp = pkgOps.get(ops[j]);
                if (curOp != null) {
                    if (resOps == null) {
                        resOps = new ArrayList<AppOpsManager.OpEntry>();
                    }
                    resOps.add(new AppOpsManager.OpEntry(curOp.op, curOp.mode, curOp.time,
                            curOp.rejectTime, curOp.duration,
                            curOp.allowedCount, curOp.ignoredCount));
                }
            }
        }
        return resOps;
    }

    @Override
    public List<AppOpsManager.PackageOps> getPackagesForOps(int[] ops) {
        mContext.enforcePermission(android.Manifest.permission.GET_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        ArrayList<AppOpsManager.PackageOps> res = null;
        synchronized (this) {
            for (int i=0; i<mUidOps.size(); i++) {
                HashMap<String, Ops> packages = mUidOps.valueAt(i);
                for (Ops pkgOps : packages.values()) {
                    ArrayList<AppOpsManager.OpEntry> resOps = collectOps(pkgOps, ops);
                    if (resOps != null) {
                        if (res == null) {
                            res = new ArrayList<AppOpsManager.PackageOps>();
                        }
                        AppOpsManager.PackageOps resPackage = new AppOpsManager.PackageOps(
                                pkgOps.packageName, pkgOps.uid, resOps);
                        res.add(resPackage);
                    }
                }
            }
        }
        return res;
    }

    @Override
    public List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName,
            int[] ops) {
        mContext.enforcePermission(android.Manifest.permission.GET_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        synchronized (this) {
            Ops pkgOps = getOpsLocked(uid, packageName, false);
            if (pkgOps == null) {
                return null;
            }
            ArrayList<AppOpsManager.OpEntry> resOps = collectOps(pkgOps, ops);
            if (resOps == null) {
                return null;
            }
            ArrayList<AppOpsManager.PackageOps> res = new ArrayList<AppOpsManager.PackageOps>();
            AppOpsManager.PackageOps resPackage = new AppOpsManager.PackageOps(
                    pkgOps.packageName, pkgOps.uid, resOps);
            res.add(resPackage);
            return res;
        }
    }

    private void pruneOp(Op op, int uid, String packageName) {
        if (op.time == 0 && op.rejectTime == 0) {
            Ops ops = getOpsLocked(uid, packageName, false);
            if (ops != null) {
                ops.remove(op.op);
                if (ops.size() <= 0) {
                    HashMap<String, Ops> pkgOps = mUidOps.get(uid);
                    if (pkgOps != null) {
                        pkgOps.remove(ops.packageName);
                        if (pkgOps.size() <= 0) {
                            mUidOps.remove(uid);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setMode(int code, int uid, String packageName, int mode) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        ArrayList<Callback> repCbs = null;
        code = AppOpsManager.opToSwitch(code);
        synchronized (this) {
            Op op = getOpLocked(code, uid, packageName, true);
            if (op != null) {
                if (op.mode != mode) {
                    op.mode = mode;
                    ArrayList<Callback> cbs = mOpModeWatchers.get(code);
                    if (cbs != null) {
                        if (repCbs == null) {
                            repCbs = new ArrayList<Callback>();
                        }
                        repCbs.addAll(cbs);
                    }
                    cbs = mPackageModeWatchers.get(packageName);
                    if (cbs != null) {
                        if (repCbs == null) {
                            repCbs = new ArrayList<Callback>();
                        }
                        repCbs.addAll(cbs);
                    }

                    if (mode == op.defaultMode) {
                        // If going into the default mode, prune this op
                        // if there is nothing else interesting in it.
                        pruneOp(op, uid, packageName);
                    }

                    scheduleWriteNowLocked();
                }
            }
        }
        if (repCbs != null) {
            for (int i=0; i<repCbs.size(); i++) {
                try {
                    repCbs.get(i).mCallback.opChanged(code, packageName);
                } catch (RemoteException e) {
                }
            }
        }
    }

    private static HashMap<Callback, ArrayList<Pair<String, Integer>>> addCallbacks(
            HashMap<Callback, ArrayList<Pair<String, Integer>>> callbacks,
            String packageName, int op, ArrayList<Callback> cbs) {
        if (cbs == null) {
            return callbacks;
        }
        if (callbacks == null) {
            callbacks = new HashMap<Callback, ArrayList<Pair<String, Integer>>>();
        }
        for (int i=0; i<cbs.size(); i++) {
            Callback cb = cbs.get(i);
            ArrayList<Pair<String, Integer>> reports = callbacks.get(cb);
            if (reports == null) {
                reports = new ArrayList<Pair<String, Integer>>();
                callbacks.put(cb, reports);
            }
            reports.add(new Pair<String, Integer>(packageName, op));
        }
        return callbacks;
    }

    @Override
    public void resetAllModes() {
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        HashMap<Callback, ArrayList<Pair<String, Integer>>> callbacks = null;
        synchronized (this) {
            boolean changed = false;
            for (int i=mUidOps.size()-1; i>=0; i--) {
                HashMap<String, Ops> packages = mUidOps.valueAt(i);
                Iterator<Map.Entry<String, Ops>> it = packages.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Ops> ent = it.next();
                    String packageName = ent.getKey();
                    Ops pkgOps = ent.getValue();
                    for (int j=pkgOps.size()-1; j>=0; j--) {
                        Op curOp = pkgOps.valueAt(j);
                        if (curOp.mode != curOp.defaultMode) {
                            curOp.mode = curOp.defaultMode;
                            changed = true;
                            callbacks = addCallbacks(callbacks, packageName, curOp.op,
                                    mOpModeWatchers.get(curOp.op));
                            callbacks = addCallbacks(callbacks, packageName, curOp.op,
                                    mPackageModeWatchers.get(packageName));
                            if (curOp.time == 0 && curOp.rejectTime == 0) {
                                pkgOps.removeAt(j);
                            }
                        }
                    }
                    if (pkgOps.size() == 0) {
                        it.remove();
                    }
                }
                if (packages.size() == 0) {
                    mUidOps.removeAt(i);
                }
            }
            if (changed) {
                scheduleWriteNowLocked();
            }
        }
        if (callbacks != null) {
            for (Map.Entry<Callback, ArrayList<Pair<String, Integer>>> ent : callbacks.entrySet()) {
                Callback cb = ent.getKey();
                ArrayList<Pair<String, Integer>> reports = ent.getValue();
                for (int i=0; i<reports.size(); i++) {
                    Pair<String, Integer> rep = reports.get(i);
                    try {
                        cb.mCallback.opChanged(rep.second, rep.first);
                    } catch (RemoteException e) {
                    }
                }
            }
        }
    }

    @Override
    public void startWatchingMode(int op, String packageName, IAppOpsCallback callback) {
        synchronized (this) {
            op = AppOpsManager.opToSwitch(op);
            Callback cb = mModeWatchers.get(callback.asBinder());
            if (cb == null) {
                cb = new Callback(callback);
                mModeWatchers.put(callback.asBinder(), cb);
            }
            if (op != AppOpsManager.OP_NONE) {
                ArrayList<Callback> cbs = mOpModeWatchers.get(op);
                if (cbs == null) {
                    cbs = new ArrayList<Callback>();
                    mOpModeWatchers.put(op, cbs);
                }
                cbs.add(cb);
            }
            if (packageName != null) {
                ArrayList<Callback> cbs = mPackageModeWatchers.get(packageName);
                if (cbs == null) {
                    cbs = new ArrayList<Callback>();
                    mPackageModeWatchers.put(packageName, cbs);
                }
                cbs.add(cb);
            }
        }
    }

    @Override
    public void stopWatchingMode(IAppOpsCallback callback) {
        synchronized (this) {
            Callback cb = mModeWatchers.remove(callback.asBinder());
            if (cb != null) {
                cb.unlinkToDeath();
                for (int i=0; i<mOpModeWatchers.size(); i++) {
                    ArrayList<Callback> cbs = mOpModeWatchers.valueAt(i);
                    cbs.remove(cb);
                    if (cbs.size() <= 0) {
                        mOpModeWatchers.removeAt(i);
                    }
                }
                if (mPackageModeWatchers.size() > 0) {
                    Iterator<ArrayList<Callback>> it = mPackageModeWatchers.values().iterator();
                    while (it.hasNext()) {
                        ArrayList<Callback> cbs = it.next();
                        cbs.remove(cb);
                        if (cbs.size() <= 0) {
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    private boolean isStrict(int code, int uid, String packageName) {
        if (!mStrictEnable) {
            return false;
        }
        return ((uid > Process.FIRST_APPLICATION_UID) &&
                (AppOpsManager.opStrict(code)) && !isInWhitelist(packageName));
    }

    private boolean isInWhitelist(String packageName) {
        return mWhitelist.contains(packageName);
    }

    private void recordOperationLocked(int code, int uid, String packageName, int mode) {
        int switchCode = AppOpsManager.opToSwitch(code);
        Op op = getOpLocked(switchCode, uid, packageName, false);
        if (op != null) {
            if (mode == AppOpsManager.MODE_IGNORED) {
                if (DEBUG) Log.d(TAG, "recordOperation: reject #" + mode + " for code "
                        + switchCode + " (" + code + ") uid " + uid + " package " + packageName);
                op.rejectTime = System.currentTimeMillis();
                op.ignoredCount++;
            } else if (mode == AppOpsManager.MODE_ALLOWED) {
                if (DEBUG) Log.d(TAG, "recordOperation: allowing code " + code + " uid " + uid
                        + " package " + packageName);
                op.time = System.currentTimeMillis();
                op.rejectTime = 0;
                op.allowedCount++;
            }
        }
    }

    public void notifyOperation(int code, int uid, String packageName, int mode,
            boolean remember) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        ArrayList<Callback> repCbs = null;
        code = AppOpsManager.opToSwitch(code);
        synchronized (this) {
            Op op = getOpLocked(code, uid, packageName, true);
            if (op != null) {
                // Record this mode
                recordOperationLocked(code, uid, packageName, mode);

                // Increase nesting for all pending startOperation requests
                if(mode == AppOpsManager.MODE_ALLOWED) {
                   if (op.nesting == 0) {
                        op.time = System.currentTimeMillis();
                        op.rejectTime = 0;
                        op.duration = -1;
                    }
                    op.nesting = op.nesting + op.tempNesting;
                }
                // Reset tempNesting
                op.tempNesting = 0;

                // Send result to all waiting client
                op.dialogResult.notifyAll(mode);
                op.dialogResult.mDialog = null;

                // if User selected to remember her choice
                if (remember) {
                    if (op.mode != mode) {
                        op.mode = mode;
                        ArrayList<Callback> cbs = mOpModeWatchers.get(code);
                        if (cbs != null) {
                            if (repCbs == null) {
                                repCbs = new ArrayList<Callback>();
                            }
                            repCbs.addAll(cbs);
                        }
                        cbs = mPackageModeWatchers.get(packageName);
                        if (cbs != null) {
                            if (repCbs == null) {
                                repCbs = new ArrayList<Callback>();
                            }
                            repCbs.addAll(cbs);
                        }

                        if (mode == op.defaultMode) {
                            // If going into the default mode, prune this op
                            // if there is nothing else interesting in it.
                            pruneOp(op, uid, packageName);
                        }

                        scheduleWriteNowLocked();
                    }
                }
            }
        }
        if (repCbs != null) {
            for (int i=0; i<repCbs.size(); i++) {
                try {
                    repCbs.get(i).mCallback.opChanged(code, packageName);
                } catch (RemoteException e) {
                }
            }
        }

    }

    private Result askOperationLocked(int code, int uid, String packageName, Op op) {
        Result result = new Result();
        final long origId = Binder.clearCallingIdentity();
        Message msg = Message.obtain();
        msg.what = SHOW_PERMISSION_DIALOG;
        Bundle data = new Bundle();
        data.putParcelable("result", result);
        data.putInt("code", code);
        data.putString("packageName", packageName);
        data.putInt("uid", uid);
        msg.setData(data);
        msg.obj = op;
        mHandler.sendMessage(msg);
        Binder.restoreCallingIdentity(origId);
        return result;
    }

    @Override
    public int checkOperation(int code, int uid, String packageName) {
        boolean strict;
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            Op op = getOpLocked(AppOpsManager.opToSwitch(code), uid, packageName, false);
            if (op == null) {
                if (!isStrict(code, uid, packageName)) {
                    return AppOpsManager.MODE_ALLOWED;
                } else {
                    return  AppOpsManager.MODE_ASK;
                }
            }
            return op.mode;
        }
    }

    @Override
    public int noteOperation(int code, int uid, String packageName) {
        int mode;
        Ops ops;
        Op op;
        final Op switchOp;
        final int switchCode;
        final Result res;
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            ops = getOpsLocked(uid, packageName, true);
            if (ops == null) {
                if (DEBUG) Log.d(TAG, "noteOperation: no op for code " + code + " uid " + uid
                        + " package " + packageName);
                return AppOpsManager.MODE_IGNORED;
            }

            boolean strict = isStrict(code, uid, packageName);
            op = getOpLocked(ops, uid, code, true, strict);
            if (op.duration == -1) {
                Slog.w(TAG, "Noting op not finished: uid " + uid + " pkg " + packageName
                        + " code " + code + " time=" + op.time + " duration=" + op.duration);
            }
            op.duration = 0;
            switchCode = AppOpsManager.opToSwitch(code);
            switchOp = switchCode != code ? getOpLocked(ops, uid, switchCode, true, strict) : op;
            mode = switchOp.mode;
            if (mode != AppOpsManager.MODE_ASK) {
                recordOperationLocked(code, uid, packageName, switchOp.mode);
                return switchOp.mode;
            } else {
                res = askOperationLocked(code, uid, packageName, switchOp);
            }
        }

        return res.get();
    }

    @Override
    public int startOperation(int code, int uid, String packageName) {
        int mode;
        Ops ops;
        Op op;
        final Op switchOp;
        final int switchCode;
        final Result res;
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            ops = getOpsLocked(uid, packageName, true);
            if (ops == null) {
                if (DEBUG) Log.d(TAG, "startOperation: no op for code " + code + " uid " + uid
                        + " package " + packageName);
                return AppOpsManager.MODE_IGNORED;
            }
            boolean strict = isStrict(code, uid, packageName);
            op = getOpLocked(ops, uid, code, true, strict);
            switchCode = AppOpsManager.opToSwitch(code);
            switchOp = switchCode != code ? getOpLocked(ops, uid, switchCode, true, strict) : op;
            mode = switchOp.mode;
            if (mode != AppOpsManager.MODE_ASK) {
                recordOperationLocked(code, uid, packageName, switchOp.mode);
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    if (op.nesting == 0) {
                        op.time = System.currentTimeMillis();
                        op.rejectTime = 0;
                        op.duration = -1;
                    }
                    op.nesting++;
                }
                return switchOp.mode;
            } else {
                op.tempNesting++;
                res = askOperationLocked(code, uid, packageName, switchOp);
            }
        }

        return res.get();
    }

    @Override
    public void finishOperation(int code, int uid, String packageName) {
        verifyIncomingUid(uid);
        verifyIncomingOp(code);
        synchronized (this) {
            Op op = getOpLocked(code, uid, packageName, true);
            if (op == null) {
                return;
            }
            if (op.nesting <= 1) {
                if (op.nesting == 1) {
                    op.duration = (int)(System.currentTimeMillis() - op.time);
                    op.time += op.duration;
                } else {
                    Slog.w(TAG, "Finishing op nesting under-run: uid " + uid + " pkg " + packageName
                        + " code " + code + " time=" + op.time + " duration=" + op.duration
                        + " nesting=" + op.nesting);
                }
                op.nesting = 0;
            } else {
                op.nesting--;
            }
        }
    }

    private void verifyIncomingUid(int uid) {
        if (uid == Binder.getCallingUid()) {
            return;
        }
        if (Binder.getCallingPid() == Process.myPid()) {
            return;
        }
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
    }

    private void verifyIncomingOp(int op) {
        if (op >= 0 && op < AppOpsManager._NUM_OP) {
            return;
        }
        throw new IllegalArgumentException("Bad operation #" + op);
    }

    private Ops getOpsLocked(int uid, String packageName, boolean edit) {
        HashMap<String, Ops> pkgOps = mUidOps.get(uid);
        if (pkgOps == null) {
            if (!edit) {
                return null;
            }
            pkgOps = new HashMap<String, Ops>();
            mUidOps.put(uid, pkgOps);
        }
        if (uid == 0) {
            packageName = "root";
        } else if (uid == Process.SHELL_UID) {
            packageName = "com.android.shell";
        }
        Ops ops = pkgOps.get(packageName);
        if (ops == null) {
            if (!edit) {
                return null;
            }
            // This is the first time we have seen this package name under this uid,
            // so let's make sure it is valid.
            if (uid != 0) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    int pkgUid = -1;
                    try {
                        pkgUid = mContext.getPackageManager().getPackageUid(packageName,
                                UserHandle.getUserId(uid));
                    } catch (NameNotFoundException e) {
                    }
                    if (pkgUid != uid) {
                        // Oops!  The package name is not valid for the uid they are calling
                        // under.  Abort.
                        Slog.w(TAG, "Bad call: specified package " + packageName
                                + " under uid " + uid + " but it is really " + pkgUid);
                        return null;
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            ops = new Ops(packageName, uid);
            pkgOps.put(packageName, ops);
        }
        return ops;
    }

    private void scheduleWriteLocked() {
        if (!mWriteScheduled) {
            mWriteScheduled = true;
            mHandler.postDelayed(mWriteRunner, WRITE_DELAY);
        }
    }

    private void scheduleWriteNowLocked() {
        if (!mWriteScheduled) {
            mWriteScheduled = true;
        }
        mHandler.removeCallbacks(mWriteRunner);
        mHandler.post(mWriteRunner);
    }

    private Op getOpLocked(int code, int uid, String packageName, boolean edit) {
        Ops ops = getOpsLocked(uid, packageName, edit);
        if (ops == null) {
            return null;
        }
        return getOpLocked(ops, uid, code, edit, isStrict(code, uid, packageName));
    }

    private Op getOpLocked(Ops ops, int uid, int code, boolean edit, boolean strict) {
        Op op = ops.get(code);
        if (op == null) {
            if (!edit) {
                return null;
            }

            op = new Op(code, strict);
            ops.put(code, op);
        }
        if (edit) {
            scheduleWriteLocked();
        }
        return op;
    }

    void readWhitelist() {
        String whitelistFileName = SystemProperties.get(WHITELIST_FILE);
        // Read if whitelist file provided
        if (!mStrictEnable || "".equals(whitelistFileName)) {
            return;
        }
        final File whitelistFile = new File(whitelistFileName);
        final AtomicFile whitelistAtomicFile = new AtomicFile(whitelistFile);
        synchronized (mWhitelist) {
            synchronized (this) {
                FileInputStream stream;
                try {
                    stream = whitelistAtomicFile.openRead();
                } catch (FileNotFoundException e) {
                    Slog.i(TAG, "No existing app ops whitelist " + whitelistAtomicFile.getBaseFile());
                    return;
                }
                boolean success = false;
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, null);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG
                            && type != XmlPullParser.END_DOCUMENT) {
                        ;
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new IllegalStateException("no start tag found");
                    }

                    int outerDepth = parser.getDepth();
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }

                        String tagName = parser.getName();
                        if (tagName.equals("pkg")) {
                            String pkgName = parser.getAttributeValue(null, "name");
                            mWhitelist.add(pkgName);
                        } else {
                            Slog.w(TAG, "Unknown element under <whitelist-pkgs>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    success = true;
                } catch (IllegalStateException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NullPointerException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NumberFormatException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (XmlPullParserException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IndexOutOfBoundsException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } finally {
                    if (!success) {
                        mWhitelist.clear();
                    }
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    void readState() {
        synchronized (mFile) {
            synchronized (this) {
                FileInputStream stream;
                try {
                    stream = mFile.openRead();
                } catch (FileNotFoundException e) {
                    Slog.i(TAG, "No existing app ops " + mFile.getBaseFile() + "; starting empty");
                    return;
                }
                boolean success = false;
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, null);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG
                            && type != XmlPullParser.END_DOCUMENT) {
                        ;
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new IllegalStateException("no start tag found");
                    }

                    int outerDepth = parser.getDepth();
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }

                        String tagName = parser.getName();
                        if (tagName.equals("pkg")) {
                            readPackage(parser);
                        } else {
                            Slog.w(TAG, "Unknown element under <app-ops>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    success = true;
                } catch (IllegalStateException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NullPointerException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NumberFormatException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (XmlPullParserException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IndexOutOfBoundsException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } finally {
                    if (!success) {
                        mUidOps.clear();
                    }
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    void readPackage(XmlPullParser parser) throws NumberFormatException,
            XmlPullParserException, IOException {
        String pkgName = parser.getAttributeValue(null, "n");
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("uid")) {
                readUid(parser, pkgName);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void readUid(XmlPullParser parser, String pkgName) throws NumberFormatException,
            XmlPullParserException, IOException {
        int uid = Integer.parseInt(parser.getAttributeValue(null, "n"));
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("op")) {
                int code = AppOpsManager.OP_NONE;
                String codeStr = parser.getAttributeValue(null, "n");
                if (codeStr != null) {
                    code = Integer.parseInt(codeStr);
                }
                /* use op name string if it exists */
                String codeNameStr = parser.getAttributeValue(null, "ns");
                if (codeNameStr != null) {
                    code = AppOpsManager.nameToOp(codeNameStr);
                }
                boolean strict = isStrict(code, uid, pkgName);
                Op op = new Op(code, strict);
                String mode = parser.getAttributeValue(null, "m");
                if (mode != null) {
                    op.mode = Integer.parseInt(mode);
                }
                String time = parser.getAttributeValue(null, "t");
                if (time != null) {
                    op.time = Long.parseLong(time);
                }
                time = parser.getAttributeValue(null, "r");
                if (time != null) {
                    op.rejectTime = Long.parseLong(time);
                }
                String dur = parser.getAttributeValue(null, "d");
                if (dur != null) {
                    op.duration = Integer.parseInt(dur);
                }
                String allowed = parser.getAttributeValue(null, "ac");
                if (allowed != null) {
                    op.allowedCount = Integer.parseInt(allowed);
                }
                String ignored = parser.getAttributeValue(null, "ic");
                if (ignored != null) {
                    op.ignoredCount = Integer.parseInt(ignored);
                }
                HashMap<String, Ops> pkgOps = mUidOps.get(uid);
                if (pkgOps == null) {
                    pkgOps = new HashMap<String, Ops>();
                    mUidOps.put(uid, pkgOps);
                }
                Ops ops = pkgOps.get(pkgName);
                if (ops == null) {
                    ops = new Ops(pkgName, uid);
                    pkgOps.put(pkgName, ops);
                }
                ops.put(op.op, op);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void writeState() {
        synchronized (mFile) {
            List<AppOpsManager.PackageOps> allOps = getPackagesForOps(null);

            FileOutputStream stream;
            try {
                stream = mFile.startWrite();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state: " + e);
                return;
            }

            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, "utf-8");
                out.startDocument(null, true);
                out.startTag(null, "app-ops");

                if (allOps != null) {
                    String lastPkg = null;
                    for (int i=0; i<allOps.size(); i++) {
                        AppOpsManager.PackageOps pkg = allOps.get(i);
                        if (!pkg.getPackageName().equals(lastPkg)) {
                            if (lastPkg != null) {
                                out.endTag(null, "pkg");
                            }
                            lastPkg = pkg.getPackageName();
                            out.startTag(null, "pkg");
                            out.attribute(null, "n", lastPkg);
                        }
                        out.startTag(null, "uid");
                        out.attribute(null, "n", Integer.toString(pkg.getUid()));
                        List<AppOpsManager.OpEntry> ops = pkg.getOps();
                        for (int j=0; j<ops.size(); j++) {
                            AppOpsManager.OpEntry op = ops.get(j);
                            out.startTag(null, "op");
                            out.attribute(null, "n", Integer.toString(op.getOp()));
                            out.attribute(null, "ns", AppOpsManager.opToName(op.getOp()));
                            out.attribute(null, "m", Integer.toString(op.getMode()));
                            long time = op.getTime();
                            if (time != 0) {
                                out.attribute(null, "t", Long.toString(time));
                            }
                            time = op.getRejectTime();
                            if (time != 0) {
                                out.attribute(null, "r", Long.toString(time));
                            }
                            int dur = op.getDuration();
                            if (dur != 0) {
                                out.attribute(null, "d", Integer.toString(dur));
                            }
                            int allowed = op.getAllowedCount();
                            if (allowed != 0) {
                                out.attribute(null, "ac", Integer.toString(allowed));
                            }
                            int ignored = op.getIgnoredCount();
                            if (ignored != 0) {
                                out.attribute(null, "ic", Integer.toString(ignored));
                            }
                            out.endTag(null, "op");
                        }
                        out.endTag(null, "uid");
                    }
                    if (lastPkg != null) {
                        out.endTag(null, "pkg");
                    }
                }

                out.endTag(null, "app-ops");
                out.endDocument();
                mFile.finishWrite(stream);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state, restoring backup.", e);
                mFile.failWrite(stream);
            }
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ApOps service from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        synchronized (this) {
            pw.println("Current AppOps Service state:");
            final long now = System.currentTimeMillis();
            for (int i=0; i<mUidOps.size(); i++) {
                pw.print("  Uid "); UserHandle.formatUid(pw, mUidOps.keyAt(i)); pw.println(":");
                HashMap<String, Ops> pkgOps = mUidOps.valueAt(i);
                for (Ops ops : pkgOps.values()) {
                    pw.print("    Package "); pw.print(ops.packageName); pw.println(":");
                    for (int j=0; j<ops.size(); j++) {
                        Op op = ops.valueAt(j);
                        pw.print("      "); pw.print(AppOpsManager.opToName(op.op));
                        pw.print(": mode="); pw.print(op.mode);
                        if (op.time != 0) {
                            pw.print("; time="); TimeUtils.formatDuration(now-op.time, pw);
                            pw.print(" ago");
                        }
                        if (op.rejectTime != 0) {
                            pw.print("; rejectTime="); TimeUtils.formatDuration(now-op.rejectTime, pw);
                            pw.print(" ago");
                        }
                        if (op.duration == -1) {
                            pw.println(" (running)");
                        } else {
                            pw.print("; duration=");
                                    TimeUtils.formatDuration(op.duration, pw);
                                    pw.println();
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean getPrivacyGuardSettingForPackage(int uid, String packageName) {
        for (int op : PRIVACY_GUARD_OP_STATES) {
            int switchOp = AppOpsManager.opToSwitch(op);
            if (checkOperation(op, uid, packageName)
                    != AppOpsManager.MODE_ALLOWED) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setPrivacyGuardSettingForPackage(int uid, String packageName, boolean state) {
        for (int op : PRIVACY_GUARD_OP_STATES) {
            int switchOp = AppOpsManager.opToSwitch(op);
            setMode(switchOp, uid, packageName, state
                    ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED);
        }
    }

    @Override
    public void resetCounters() {
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
        synchronized (this) {
            for (int i=0; i<mUidOps.size(); i++) {
                HashMap<String, Ops> packages = mUidOps.valueAt(i);
                for (Map.Entry<String, Ops> ent : packages.entrySet()) {
                    String packageName = ent.getKey();
                    Ops pkgOps = ent.getValue();
                    for (int j=0; j<pkgOps.size(); j++) {
                        Op curOp = pkgOps.valueAt(j);
                        curOp.allowedCount = 0;
                        curOp.ignoredCount = 0;
                    }
                }
            }
        }
        // ensure the counter reset persists
        scheduleWriteNowLocked();
    }
}
