/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server;

import static android.view.WindowManager.LayoutParams.FLAG_SYSTEM_ERROR;

import com.android.internal.R;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

class PermissionDialog extends BasePermissionDialog {
    private final static String TAG = "PermissionDialog";

    private final AppOpsService mService;
    private final String mPackageName;
    private final int mCode;
    private View  mView;
    private CheckBox mChoice;
    private int mUid;
    final CharSequence[] mOpLabels;
    private Context mContext;

    public static final int ACTION_ALLOWED          = 0x2;
    public static final int ACTION_IGNORED          = 0x4;
    public static final int ACTION_IGNORED_TIMEOUT  = 0x8;

    // 1-minute timeout, then we automatically dismiss the permission
    // dialog
    static final long DISMISS_TIMEOUT = 1000 * 60 * 1;

    public PermissionDialog(Context context, AppOpsService service,
            int code, int uid, String packageName) {
        super(context);

        mContext = context;
        Resources res = context.getResources();

        mService = service;
        mCode = code;
        mPackageName = packageName;
        mUid = uid;

        mOpLabels = res.getTextArray(R.array.app_ops_labels);

        setCancelable(false);
        setTitle("Permission");
        getWindow().addFlags(FLAG_SYSTEM_ERROR);

        mView = getLayoutInflater().inflate(R.layout.permission_confirmation_dialog, null);
        TextView tv = (TextView) mView.findViewById(R.id.permission_text);
        mChoice = (CheckBox) mView.findViewById(R.id.permission_remember_choice_checkbox);
        String name = getAppName(mPackageName);
        if(name == null)
            name = mPackageName;
        tv.setText(name + ": " + mOpLabels[mCode]);
        setView(mView);

        setButton(DialogInterface.BUTTON_POSITIVE,
                  "Allow", mHandler.obtainMessage(ACTION_ALLOWED));

        setButton(DialogInterface.BUTTON_NEGATIVE,
                  "Denied", mHandler.obtainMessage(ACTION_IGNORED));

        // After the timeout, pretend the user clicked the quit button
        //mHandler.sendMessageDelayed(
        //        mHandler.obtainMessage(ACTION_IGNORED_TIMEOUT),
        //        DISMISS_TIMEOUT);
    }

    private String getAppName(String packageName) {
        ApplicationInfo appInfo = null;
        PackageManager pm = mContext.getPackageManager();
        try {
            appInfo = pm.getApplicationInfo(packageName,
                      PackageManager.GET_DISABLED_COMPONENTS
                      | PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (final NameNotFoundException e) {
            return null;
        }
        if(appInfo != null) {
            return  (String)pm.getApplicationLabel(appInfo);
        }
        return null;
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            int mode;
            boolean remember = mChoice.isChecked();
            switch(msg.what) {
                case ACTION_ALLOWED:
                    mode = AppOpsManager.MODE_ALLOWED;
                    break;
                case ACTION_IGNORED:
                    mode = AppOpsManager.MODE_IGNORED;
                    break;
                default:
                    mode = AppOpsManager.MODE_IGNORED;
                    remember = false;
            }
            mService.notifyOperation(mCode, mUid, mPackageName, mode, remember);
            dismiss();
        }
    };
}
