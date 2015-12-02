/*
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

package com.android.systemui.qs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.Listenable;

public class UsageTracker implements Listenable {
    private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;

    private final Context mContext;
    private final long mTimeToShowTile;
    @Prefs.Key private final String mPrefKey;
    private final String mResetAction;

    private boolean mRegistered;

    public UsageTracker(Context context, @Prefs.Key String prefKey, Class<?> tile,
            int timeoutResource) {
        mContext = context;
        mPrefKey = prefKey;
        mTimeToShowTile = MILLIS_PER_DAY * mContext.getResources().getInteger(timeoutResource);
        mResetAction = "com.android.systemui.qs." + tile.getSimpleName() + ".usage_reset";
    }

    @Override
    public void setListening(boolean listen) {
        if (listen && !mRegistered) {
             mContext.registerReceiver(mReceiver, new IntentFilter(mResetAction));
             mRegistered = true;
        } else if (!listen && mRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mRegistered = false;
        }
    }

    public boolean isRecentlyUsed() {
        long lastUsed = Prefs.getLong(mContext, mPrefKey, 0L /* defaultValue */);
        return (System.currentTimeMillis() - lastUsed) < mTimeToShowTile;
    }

    public void trackUsage() {
        Prefs.putLong(mContext, mPrefKey, System.currentTimeMillis());
    }

    public void reset() {
        Prefs.remove(mContext, mPrefKey);
    }

    public void showResetConfirmation(String title, final Runnable onConfirmed) {
        final SystemUIDialog d = new SystemUIDialog(mContext);
        d.setTitle(title);
        d.setMessage(mContext.getString(R.string.quick_settings_reset_confirmation_message));
        d.setNegativeButton(android.R.string.cancel, null);
        d.setPositiveButton(R.string.quick_settings_reset_confirmation_button,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                reset();
                if (onConfirmed != null) {
                    onConfirmed.run();
                }
            }
        });
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mResetAction.equals(intent.getAction())) {
                reset();
            }
        }
    };
}
