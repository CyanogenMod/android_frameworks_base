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

package com.android.systemui.qs.tiles;

import android.app.CustomTile;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.systemui.qs.QSTile;

public class CustomQSTile extends QSTile<QSTile.State> {

    private PendingIntent mOnClick;
    private String mOnClickUri;
    private int mCurrentUserId;

    private CustomQSTile(Host host, CustomTile tile) {
        super(host);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    public static QSTile<?> create(Host host, String spec) {
        return new CustomQSTile(host, new CustomTile(spec));
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected State newTileState() {
        return new State();
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        super.handleUserSwitch(newUserId);
        mCurrentUserId = newUserId;
    }

    @Override
    protected void handleClick() {
        try {
            if (mOnClick != null) {
                mOnClick.send();
            } else if (mOnClickUri != null) {
                final Intent intent = Intent.parseUri(mOnClickUri, Intent.URI_INTENT_SCHEME);
                mContext.sendBroadcastAsUser(intent, new UserHandle(mCurrentUserId));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error sending click intent", t);
        }
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (!(arg instanceof CustomTile)) return;
        final CustomTile tile = (CustomTile) arg;
        state.visible = tile.getVisibility();
        state.contentDescription = tile.getContentDescription();
        state.label = tile.getLabel();
        state.iconId = 0;
        state.icon = null;
        final byte[] iconBitmap = tile.getIconBytes();
        if (iconBitmap != null) {
            try {
                final Bitmap b = BitmapFactory.decodeByteArray(iconBitmap, 0, iconBitmap.length);
                state.icon = new BitmapDrawable(mContext.getResources(), b);
            } catch (Throwable t) {
                Log.w(TAG, "Error loading icon bitmap, length " + iconBitmap.length, t);
            }
        }
         //TODO: Create an icon bundle
         /*else {
            final int iconId = tile.getIconId();
            if (iconId != 0) {
                final String iconPackage = tile.getIconPackage();
                if (!TextUtils.isEmpty(iconPackage)) {
                    state.icon = getPackageDrawable(iconPackage, iconId);
                } else {
                    state.iconId = iconId;
                }
            }
        } */
        mOnClick = tile.getOnClick();
        mOnClickUri = tile.getOnClickUri().toString();
    }

    //TODO: Implement icon bundle
    private Drawable getPackageDrawable(String pkg, int id) {
        try {
            return mContext.createPackageContext(pkg, 0).getDrawable(id);
        } catch (Throwable t) {
            Log.w(TAG, "Error loading package drawable pkg=" + pkg + " id=" + id, t);
            return null;
        }
    }

    /* TODO: Not sure what to do for refresh, preferably through ipc
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState(intent);
        }
    }; */
}
