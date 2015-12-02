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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDragPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.phone.SystemUIDialog;

public class EditTile extends QSTile<QSTile.BooleanState> {

    public EditTile(Host host) {
        super(host);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setCallback(Callback callback) {
        super.setCallback(callback);
    }

    @Override
    protected void handleClick() {
        getHost().setEditing(!mState.value);
        refreshState(!mState.value);
    }

    @Override
    protected void handleLongClick() {
        final AlertDialog d = new AlertDialog.Builder(mContext)
                .setMessage(R.string.qs_tiles_reset_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(com.android.internal.R.string.reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                getHost().resetTiles();
                                refreshState(false);
                            }
                        }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = !getHost().getKeyguardMonitor().isShowing();
        state.label = mContext.getString(R.string.quick_settings_edit_label);

        if (arg instanceof Boolean) {
            state.value = (boolean) arg;
        } else {
            state.value = getHost().isEditing();
        }
        state.icon = ResourceIcon.get(R.drawable.ic_qs_edit_tiles);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.DONT_TRACK_ME_BRO;
    }

    @Override
    protected String composeChangeAnnouncement() {
        // TODO
        return null;
    }

    @Override
    public void setListening(boolean listening) {
        // not interested
    }
}
