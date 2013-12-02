/*
 * Copyright (C) 2013 Slimroms
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class RebootTile extends QuickSettingsTile {

    public static String TAG = "RebootTile";
    private boolean mRebootToRecovery = false;

    public RebootTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRebootToRecovery = !mRebootToRecovery;
                updateResources();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                qsc.mBar.collapseAllPanels(true);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        PowerManager pm =
                            (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                        pm.reboot(mRebootToRecovery? "recovery" : "");
                    }
                }, 500);
                return true;
            }
        };
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

    private void updateTile() {
        if (mRebootToRecovery) {
            mLabel = mContext.getString(R.string.quick_settings_reboot_recovery);
            mDrawable = R.drawable.ic_qs_reboot_recovery;
        } else {
            mLabel = mContext.getString(R.string.quick_settings_reboot);
            mDrawable = R.drawable.ic_qs_reboot;
        }
    }

}
