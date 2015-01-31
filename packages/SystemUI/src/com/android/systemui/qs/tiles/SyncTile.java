/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2012-2015 The CyanogenMod Project
 * Copyright (C) 2014-2015 The Euphoria-OS Project
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

package com.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.os.Handler;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Sync **/
public class SyncTile extends QSTile<QSTile.BooleanState> {

    private Object mSyncObserverHandle = null;
    private Handler mHandler;
    private boolean mListening;

    public SyncTile(Host host) {
        super(host);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        if (isSyncEnabled()) {
            ContentResolver.setMasterSyncAutomatically(false);
        } else {
            ContentResolver.setMasterSyncAutomatically(true);
        }
        refreshState();
    }

    @Override
    public void handleLongClick() {
        Intent intent = new Intent("android.settings.SYNC_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        mHost.startSettingsActivity(intent);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_sync_label);
        if (isSyncEnabled()) {
            state.iconId = R.drawable.ic_qs_sync_on;
        } else {
            state.iconId = R.drawable.ic_qs_sync_off;
        }
    }

    private boolean isSyncEnabled() {
        return ContentResolver.getMasterSyncAutomatically();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshState();
                }
            });
        }
    };
}
