/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.View;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class AppTile extends QuickSettingsTile {
    private PackageManager mPackageManager;
    private ComponentName mComponentName;

    public AppTile(Context context, QuickSettingsController qsc, ComponentName componentName) {
        super(context, qsc);
        mPackageManager = mContext.getPackageManager();
        mComponentName = componentName;
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mContext.startActivity(new Intent()
                        .setComponent(mComponentName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                mQsc.mBar.collapseAllPanels(true);
            }
        };

    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    void updateQuickSettings() {
        updateTile();
        super.updateQuickSettings();
    }

    private void updateTile() {
        try {
            ApplicationInfo applicationInfo =
                    mPackageManager.getApplicationInfo(mComponentName.getPackageName(), 0);
            mLabel = (String) applicationInfo.loadLabel(mPackageManager);
            System.out.println("COMPONENT ADNAN TILE " + mLabel);
            mImage = mPackageManager.getActivityIcon(mComponentName);
        } catch (PackageManager.NameNotFoundException e) {
            System.out.println("COMPONENT ADNAN TILE "
                    + e.toString());
        }
    }
}
