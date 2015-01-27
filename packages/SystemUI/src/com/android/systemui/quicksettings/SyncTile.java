/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.os.Handler;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class SyncTile extends QuickSettingsTile {
    private Object mSyncObserverHandle = null;
    private Handler mHandler;

    private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            // update state/view if something happened
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateResources();
                }
            });
        }
    };

    public SyncTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mHandler = handler;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSyncEnabled()) {
                    ContentResolver.setMasterSyncAutomatically(false);
                } else {
                    ContentResolver.setMasterSyncAutomatically(true);
                }
                updateResources();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent("android.settings.SYNC_SETTINGS");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();

        mSyncObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private void updateTile() {
        if (isSyncEnabled()) {
            mDrawable = R.drawable.ic_qs_sync_on;
            mLabel = mContext.getString(R.string.quick_settings_sync);
        } else {
            mDrawable = R.drawable.ic_qs_sync_off;
            mLabel = mContext.getString(R.string.quick_settings_sync_off);
        }
    }

    private boolean isSyncEnabled() {
        return ContentResolver.getMasterSyncAutomatically();
    }
}
