package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.net.ConnectivityManager;
import android.util.Log;
import android.view.View;

public class SyncButton extends PowerButton {
    private static final String TAG = "SyncButton";

    public SyncButton() { mType = BUTTON_SYNC; }

    private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
            public void onStatusChanged(int which) {
                // update state/view if something happened
                if (mView != null) {
                    update(mView.getContext());
                }
            }
        };
    private Object mSyncObserverHandle = null;

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);

        if(mView == null && mSyncObserverHandle != null) {
            Log.i(TAG, "Unregistering sync state listener");
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        } else if(mView != null && mSyncObserverHandle == null) {
            Log.i(TAG, "Registering sync state listener");
            mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncObserver);
        }
    }

    @Override
    protected void updateState(Context context) {
        if (getSyncState(context)) {
            mIcon = R.drawable.stat_sync_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_sync_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean backgroundData = getBackgroundDataState(context);
        boolean sync = ContentResolver.getMasterSyncAutomatically();

        // four cases to handle:
        // setting toggled from off to on:
        // 1. background data was off, sync was off: turn on both
        if (!backgroundData && !sync) {
            connManager.setBackgroundDataSetting(true);
            ContentResolver.setMasterSyncAutomatically(true);
        }

        // 2. background data was off, sync was on: turn on background data
        if (!backgroundData && sync) {
            connManager.setBackgroundDataSetting(true);
        }

        // 3. background data was on, sync was off: turn on sync
        if (backgroundData && !sync) {
            ContentResolver.setMasterSyncAutomatically(true);
        }

        // setting toggled from on to off:
        // 4. background data was on, sync was on: turn off sync
        if (backgroundData && sync) {
            ContentResolver.setMasterSyncAutomatically(false);
        }
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.SYNC_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    private boolean getBackgroundDataState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connManager.getBackgroundDataSetting();
    }

    private boolean getSyncState(Context context) {
        boolean backgroundData = getBackgroundDataState(context);
        boolean sync = ContentResolver.getMasterSyncAutomatically();
        return backgroundData && sync;
    }
}
