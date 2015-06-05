/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.cyanogenmod.keyguard.cmstats;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

public class KeyguardStats {
    private static final String TAG = KeyguardStats.class.getSimpleName();
    private static final String ANALYTIC_INTENT = "com.cyngn.stats.action.SEND_ANALYTIC_EVENT";
    private static final String ANALYTIC_PERMISSION = "com.cyngn.stats.SEND_ANALYTICS";
    public static final String TRACKING_ID = "tracking_id";
    private static final boolean DEBUG = false;

    private KeyguardStats() {}

    public static void sendUnlockEvent(Context context,
            boolean fingerprintUsed,
            int numAttempts) {
        Intent intent = new Intent();
        intent.putExtra(Fields.EVENT_ACTION, Action.UNLOCKED);
        intent.putExtra(Fields.EVENT_CATEGORY, Categories.KEYGUARD);
        intent.putExtra(Fields.FINGERPRINT_USED_TO_UNLOCK, fingerprintUsed);
        intent.putExtra(Fields.NUM_FINGERPRINT_UNLOCK_ATTEMPTS, numAttempts);
        sendEvent(context, intent);
    }

    private static void sendEvent(Context context, Intent intent) {
        if (!StatsUtils.isStatsPackageInstalled(context)
                || !StatsUtils.isStatsCollectionEnabled(context)) {
            return;
        }

        intent.setAction(ANALYTIC_INTENT);
        intent.putExtra(TRACKING_ID, context.getPackageName());

        if (DEBUG) {
            Log.d(TAG, "Sending " + intent.toInsecureString());
        }

        // broadcast for internal package
        context.sendBroadcastAsUser(intent,
                new UserHandle(UserHandle.USER_CURRENT), ANALYTIC_PERMISSION);
    }

    public static final class Fields {
        public static final String EVENT_CATEGORY = "category";
        public static final String EVENT_ACTION = "action";
        public static final String NUM_FINGERPRINT_UNLOCK_ATTEMPTS = "numFingerprintUnlockAttempts";
        public static final String FINGERPRINT_USED_TO_UNLOCK = "fingerprintUsedToUnlock";
    }

    public static final class Categories {
        public static final String KEYGUARD = "keyguard";
    }

    public static final class Action {
        public static final String UNLOCKED = "unlocked";
    }
}
