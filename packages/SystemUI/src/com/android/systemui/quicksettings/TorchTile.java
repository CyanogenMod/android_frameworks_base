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

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.internal.util.cm.TorchConstants;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class TorchTile extends QuickSettingsTile {
    private boolean mActive = false;

    public TorchTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                mContext.sendBroadcast(i);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(TorchConstants.INTENT_LAUNCH_APP);
                return true;
            }
        };

        qsc.registerAction(TorchConstants.ACTION_STATE_CHANGED, this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mActive = intent.getIntExtra(TorchConstants.EXTRA_CURRENT_STATE, 0) != 0;
        updateResources();
    }

    private void updateTile() {
        if (mActive) {
            mDrawable = R.drawable.ic_qs_torch_on;
            mLabel = mContext.getString(R.string.quick_settings_torch);
        } else {
            mDrawable = R.drawable.ic_qs_torch_off;
            mLabel = mContext.getString(R.string.quick_settings_torch_off);
        }
    }
}
