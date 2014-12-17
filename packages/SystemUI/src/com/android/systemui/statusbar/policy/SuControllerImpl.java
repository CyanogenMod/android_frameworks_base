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

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller to manage changes to superuser-related states and update the views accordingly.
 */
public class SuControllerImpl implements SuController {
    private static final String TAG = "SuControllerImpl";

    private static final int[] mSuOpArray = new int[] {AppOpsManager.OP_SU};

    private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();

    private Context mContext;

    private AppOpsManager mAppOpsManager;

    private boolean mHasActiveSuSessions;

    public SuControllerImpl(Context context) {
        mContext = context;

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
        return mHasActiveSuSessions;
    }

    private void fireCallback(Callback callback) {
        callback.onSuSessionsChanged();
    }

    private void fireCallbacks() {
        for (Callback callback : mCallbacks) {
            callback.onSuSessionsChanged();
        }
    }

    /**
     * Returns true if a su session is active
     */
    private boolean hasActiveSuSessions() {
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
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private void updateActiveSuSessions() {
        boolean hadActiveSuSessions = mHasActiveSuSessions;
        mHasActiveSuSessions = hasActiveSuSessions();
        if (mHasActiveSuSessions != hadActiveSuSessions) {
            fireCallbacks();
        }
    }
}
