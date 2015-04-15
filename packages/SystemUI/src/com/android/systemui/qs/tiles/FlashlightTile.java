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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.TorchManager;
import android.os.SystemClock;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Control flashlight **/
public class FlashlightTile extends QSTile<QSTile.BooleanState> implements
        TorchManager.TorchCallback {

    public static final String ACTION_TURN_FLASHLIGHT_OFF =
            "com.android.systemui.qs.ACTION_TURN_FLASHLIGHT_OFF";

    /** Grace period for which we consider the flashlight
     * still available because it was recently on. */
    private static final long RECENTLY_ON_DURATION_MILLIS = 500;

    private final AnimationIcon mEnable
            = new AnimationIcon(R.drawable.ic_signal_flashlight_enable_animation);
    private final AnimationIcon mDisable
            = new AnimationIcon(R.drawable.ic_signal_flashlight_disable_animation);
    private final TorchManager mTorchManager;
    private long mWasLastOn;
    private boolean mTorchAvailable;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TURN_FLASHLIGHT_OFF.equals(intent.getAction())) {
                mTorchManager.setTorchEnabled(false);
                refreshState(UserBoolean.BACKGROUND_FALSE);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                setNotificationShown(true);
            }
        }
    };

    public FlashlightTile(Host host) {
        super(host);
        mTorchManager = (TorchManager) mContext.getSystemService(Context.TORCH_SERVICE);
        mTorchManager.addListener(this);
        mTorchAvailable = mTorchManager.isAvailable();
    }

    @Override
    protected void handleDestroy() {
        mTorchManager.removeListener(this);
        setListenForScreenOff(false);
        super.handleDestroy();
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {

    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    private void setNotificationShown(boolean show) {
        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (show) {
            Intent fireMe = new Intent(ACTION_TURN_FLASHLIGHT_OFF);
            fireMe.setPackage(mContext.getPackageName());

            Notification not = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getString(
                            R.string.quick_settings_tile_flashlight_not_title))
                    .setContentText(mContext.getString(
                            R.string.quick_settings_tile_flashlight_not_summary))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_signal_flashlight_disable)
                    .setContentIntent(
                            PendingIntent.getBroadcast(mContext, 0, fireMe, 0))
                    .build();

            nm.notify(R.string.quick_settings_tile_flashlight_not_title, not);
        } else {
            nm.cancel(R.string.quick_settings_tile_flashlight_not_title);
        }
    }

    private void setListenForScreenOff(boolean listen) {
        if (listen) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_TURN_FLASHLIGHT_OFF);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
            setNotificationShown(false);
        }
    }

    @Override
    protected void handleClick() {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }
        boolean newState = !mState.value;
        mTorchManager.setTorchEnabled(newState);
        refreshState(newState ? UserBoolean.USER_TRUE : UserBoolean.USER_FALSE);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (state.value) {
            mWasLastOn = SystemClock.uptimeMillis();
        }

        if (arg instanceof UserBoolean) {
            state.value = ((UserBoolean) arg).value;
            setListenForScreenOff(state.value);
        }

        if (!state.value && mWasLastOn != 0) {
            if (SystemClock.uptimeMillis() > mWasLastOn + RECENTLY_ON_DURATION_MILLIS) {
                mWasLastOn = 0;
            } else {
                mHandler.removeCallbacks(mRecentlyOnTimeout);
                mHandler.postAtTime(mRecentlyOnTimeout, mWasLastOn + RECENTLY_ON_DURATION_MILLIS);
            }
        }

        state.visible = mWasLastOn != 0 || mTorchAvailable;
        state.label = mHost.getContext().getString(R.string.quick_settings_flashlight_label);
        final AnimationIcon icon = state.value ? mEnable : mDisable;
        icon.setAllowAnimation(arg instanceof UserBoolean && ((UserBoolean) arg).userInitiated);
        state.icon = icon;
        int onOrOffId = state.value
                ? R.string.accessibility_quick_settings_flashlight_on
                : R.string.accessibility_quick_settings_flashlight_off;
        state.contentDescription = mContext.getString(onOrOffId);
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_flashlight_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_flashlight_changed_off);
        }
    }

    @Override
    public void onTorchStateChanged(boolean on) {
        refreshState(on ? UserBoolean.BACKGROUND_TRUE : UserBoolean.BACKGROUND_FALSE);
    }

    @Override
    public void onTorchError() {
        refreshState(UserBoolean.BACKGROUND_FALSE);
    }

    @Override
    public void onTorchAvailabilityChanged(boolean available) {
        mTorchAvailable = available;
        refreshState(mTorchManager.isTorchOn());
    }

    private Runnable mRecentlyOnTimeout = new Runnable() {
        @Override
        public void run() {
            refreshState();
        }
    };
}
