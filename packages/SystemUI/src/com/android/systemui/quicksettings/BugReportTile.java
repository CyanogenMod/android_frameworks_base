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

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class BugReportTile extends QuickSettingsTile {
    private boolean mEnabled = false;
    private final Handler mHandler;

    public BugReportTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mHandler = handler;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                showBugreportDialog();
            }
        };

        qsc.registerObservedContent(
                Settings.Global.getUriFor(Settings.Global.BUGREPORT_IN_POWER_MENU), this);
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
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(mEnabled ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    private void updateTile() {
        mLabel = mContext.getString(R.string.quick_settings_report_bug);
        mDrawable = R.drawable.ic_qs_bug_report;
        mEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BUGREPORT_IN_POWER_MENU, 0) != 0;
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Add a little delay before executing, to give the
                // dialog a chance to go away before it takes a
                // screenshot.
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ActivityManagerNative.getDefault().requestBugReport();
                        } catch (RemoteException e) {
                        }
                    }
                }, 500);
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);

        final Dialog dialog = builder.create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }

        dialog.show();
    }

}
