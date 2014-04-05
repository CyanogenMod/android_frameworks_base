/*
 * Copyright (C) 2012 Slimroms Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FChargeTile extends QuickSettingsTile {

    private static final String TAG = "FChargeTile";
    private final boolean DBG = false;

    protected boolean enabled = false;

    public FChargeTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                        enabled = !isFastChargeOn();
                        String fchargePath = mContext.getResources()
                                .getString(com.android.internal.R.string.config_fastChargePath);
                        if (!fchargePath.isEmpty()) {
                            File fastcharge = new File(fchargePath);
                            if (fastcharge.exists()) {
                                FileWriter fwriter = new FileWriter(fastcharge);
                                BufferedWriter bwriter = new BufferedWriter(fwriter);
                                bwriter.write(enabled ? "1" : "0");
                                bwriter.close();
                                Settings.System.putIntForUser(mContext.getContentResolver(),
                                     Settings.System.FCHARGE_ENABLED, enabled ? 1 : 0, UserHandle.USER_CURRENT);
                            }
                        }
                    } catch (IOException e) {
                        if (DBG) Log.e("FChargeToggle", "Couldn't write fast_charge file");
                        Settings.System.putIntForUser(mContext.getContentResolver(),
                             Settings.System.FCHARGE_ENABLED, 0, UserHandle.USER_CURRENT);
                    }
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.FCHARGE_ENABLED), this);
    }


    public boolean isFastChargeOn() {
        try {
            String fchargePath = mContext.getResources()
                    .getString(com.android.internal.R.string.config_fastChargePath);
            if (!fchargePath.isEmpty()) {
                File fastcharge = new File(fchargePath);
                if (fastcharge.exists()) {
                    FileReader reader = new FileReader(fastcharge);
                    BufferedReader breader = new BufferedReader(reader);
                    String line = breader.readLine();
                    breader.close();
                    Settings.System.putIntForUser(mContext.getContentResolver(),
                            Settings.System.FCHARGE_ENABLED, line.equals("1") ? 1 : 0, UserHandle.USER_CURRENT);
                    return (line.equals("1"));
                }
            }
        } catch (IOException e) {
            if (DBG) Log.e("FChargeToggle", "Couldn't read fast_charge file");
            Settings.System.putIntForUser(mContext.getContentResolver(),
                 Settings.System.FCHARGE_ENABLED, 0, UserHandle.USER_CURRENT);
        }
        return false;
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
        enabled = isFastChargeOn();
        String label = mContext.getString(R.string.quick_settings_fcharge);

        if(enabled) {
            mDrawable = R.drawable.ic_qs_fcharge_on;
            mLabel = label;
        } else {
            mDrawable = R.drawable.ic_qs_fcharge_off;
            mLabel = label + " " + mContext.getString(R.string.quick_settings_label_disabled);
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
