/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui.statusbar.policy;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller to manage changes to superuser-related states and update the views accordingly.
 */
public class SuControllerImpl implements SuController {
    private static final String TAG = "SuControllerImpl";

    private static final int[] mSuOpArray = new int[] {AppOpsManager.OP_SU};

    private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();

    private AppOpsManager mAppOpsManager;

    private List<String> mActiveSuSessions = new ArrayList<>();

    public SuControllerImpl(Context context) {
        mAppOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AppOpsManager.ACTION_SU_SESSION_CHANGED);
        context.registerReceiverAsUser(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Got change");
                String action = intent.getAction();
                if (AppOpsManager.ACTION_SU_SESSION_CHANGED.equals(action)) {
                    updateActiveSuSessions();
                }
            }
        }, UserHandle.ALL, intentFilter, null, new Handler());

        updateActiveSuSessions();
    }

    @Override
    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
        fireCallback(callback);
    }

    @Override
    public void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    @Override
    public boolean hasActiveSessions() {
        synchronized (mActiveSuSessions) {
            return mActiveSuSessions.size() > 0;
        }
    }

    private void fireCallback(Callback callback) {
        callback.onSuSessionsChanged();
    }

    private void fireCallbacks() {
        for (Callback callback : mCallbacks) {
            callback.onSuSessionsChanged();
        }
    }

    // Return the list of package names that currently have an active su session
    @Override
    public List<String> getPackageNamesWithActiveSuSessions() {
        List<String> packageNames = new ArrayList<>();
        List<AppOpsManager.PackageOps> packages
                = mAppOpsManager.getPackagesForOps(mSuOpArray);
        // AppOpsManager can return null when there is no requested data.
        if (packages != null) {
            final int numPackages = packages.size();
            for (int packageInd = 0; packageInd < numPackages; packageInd++) {
                AppOpsManager.PackageOps packageOp = packages.get(packageInd);
                List<AppOpsManager.OpEntry> opEntries = packageOp.getOps();
                if (opEntries != null) {
                    final int numOps = opEntries.size();
                    for (int opInd = 0; opInd < numOps; opInd++) {
                        AppOpsManager.OpEntry opEntry = opEntries.get(opInd);
                        if (opEntry.getOp() == AppOpsManager.OP_SU) {
                            if (opEntry.isRunning()) {
                                packageNames.add(packageOp.getPackageName());
                                break;
                            }
                        }
                    }
                }
            }
        }

        return packageNames;
    }

    private synchronized void updateActiveSuSessions() {
        List<String> newList = getPackageNamesWithActiveSuSessions();
        synchronized (mActiveSuSessions) {
            if (!newList.equals(mActiveSuSessions)) {
                mActiveSuSessions.clear();
                mActiveSuSessions.addAll(newList);
                fireCallbacks();
            }
        }
    }
}
