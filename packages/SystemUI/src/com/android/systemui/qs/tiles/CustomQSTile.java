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

package com.android.systemui.qs.tiles;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import cyanogenmod.app.CustomTile;

import org.cyanogenmod.internal.statusbar.StatusBarPanelCustomTile;
import com.android.systemui.qs.QSTile;

public class CustomQSTile extends QSTile<QSTile.State> {

    private Context mPackageContext;
    private PendingIntent mOnClick;
    private Uri mOnClickUri;
    private int mCurrentUserId;

    public CustomQSTile(Host host, StatusBarPanelCustomTile tile) {
        super(host);
        refreshState(tile);
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

    public void update(StatusBarPanelCustomTile customTile) {
        refreshState(customTile);
    }

    @Override
    protected void handleClick() {
        try {
            if (mOnClick != null) {
                mOnClick.send();
            } else if (mOnClickUri != null) {
                final Intent intent = new Intent().setData(mOnClickUri);
                mContext.sendBroadcastAsUser(intent, new UserHandle(mCurrentUserId));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error sending click intent", t);
        }
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (!(arg instanceof StatusBarPanelCustomTile)) return;
        final StatusBarPanelCustomTile tile = (StatusBarPanelCustomTile) arg;
        final CustomTile customTile = tile.getCustomTile();
        state.visible = true;
        state.contentDescription = customTile.contentDescription;
        state.label = customTile.label;
        state.iconId = 0;
        final int iconId = customTile.icon;
        if (iconId != 0) {
            final String iconPackage = tile.getPackage();
            if (!TextUtils.isEmpty(iconPackage)) {
                state.icon = new ExternalIcon(iconPackage, iconId);
            } else {
                state.iconId = iconId;
            }
        }
        mOnClick = customTile.onClick;
        mOnClickUri = customTile.onClickUri;
    }
}
