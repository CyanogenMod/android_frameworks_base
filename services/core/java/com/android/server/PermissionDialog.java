/*
 * Copyright (C) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.server;

import android.os.Message;
import android.os.Handler;
import android.widget.TextView;
import android.widget.CheckBox;
import android.view.WindowManager;
import android.view.View;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.app.AppOpsManager;

public class PermissionDialog extends BasePermissionDialog {
    private final int mDef;
    private final String inputPackage;
    private final AppOpsService opsServ;
    private final static String TAG = "PermInfo";
    private View viewId;
    private int inputId;
    private CheckBox checkSta;
    private Context contId;
    final CharSequence[] mOpLabels;
    static final int IGNORED_REQ = 0x4;
    static final int IGNORED_REQ_TIMEOUT = 0x8;
    static final long TIMEOUT_WAIT = 15 * 1000;
    static final int ALLOWED_REQ = 0x2;

    public PermissionDialog(Context contextId, AppOpsService opsService,
                            int defInf, int idInfo, String packageName) {
        super(contextId);
        opsServ = opsService;
        inputPackage = packageName;
        contId = contextId;
        mDef = defInf;
        Resources rId = contextId.getResources();
        inputId = idInfo;
        mOpLabels = rId.getTextArray(
                com.android.internal.R.array.app_ops_labels);
        setCancelable(false);
        setButton(DialogInterface.BUTTON_POSITIVE,
                rId.getString(com.android.internal.R.string.allow_button),
                myHandle.obtainMessage(ALLOWED_REQ));
        setButton(DialogInterface.BUTTON_NEGATIVE,
                rId.getString(com.android.internal.R.string.deny_button),
                myHandle.obtainMessage(IGNORED_REQ));
        setTitle(rId.getString(com.android.internal.R.string.permission_title));
        WindowManager.LayoutParams paraDef = getWindow().getAttributes();
        paraDef.setTitle("Permission: " + getAppName(inputPackage));
        paraDef.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SYSTEM_ERROR
                | WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        getWindow().setAttributes(paraDef);
        viewId = getLayoutInflater().inflate(
                com.android.internal.R.layout.permission_confirmation_dialog, null);
        TextView textId = (TextView) viewId.findViewById(
                com.android.internal.R.id.permission_text);
        checkSta = (CheckBox) viewId.findViewById(
                com.android.internal.R.id.permission_remember_choice_checkbox);
        TextView textRem = (TextView) viewId.findViewById(
                com.android.internal.R.id.permission_remember_choice_text);
        checkSta.setVisibility(View.INVISIBLE);
        textRem.setVisibility(View.INVISIBLE);

        String appName = getAppName(inputPackage);
        if (appName == null)
            appName = inputPackage;
        textId.setText(appName + ": " + mOpLabels[mDef-64]);
        setView(viewId);
        myHandle.sendMessageDelayed(myHandle.obtainMessage(IGNORED_REQ_TIMEOUT), TIMEOUT_WAIT);
    }

    private final Handler myHandle = new Handler() {
        public void handleMessage(Message mess) {
            int runSet;
            boolean keepIt = checkSta.isChecked();
            switch (mess.what) {
                case ALLOWED_REQ:
                    runSet = AppOpsManager.MODE_ALLOWED;
                    break;
                case IGNORED_REQ:
                    runSet = AppOpsManager.MODE_IGNORED;
                    break;
                default:
                    runSet = AppOpsManager.MODE_IGNORED;
                    keepIt = false;
            }
            opsServ.notifyOperation(mDef, inputId, inputPackage, runSet, keepIt);
            dismiss();
        }
    };

    private String getAppName(String inputName) {
        PackageManager packMan = contId.getPackageManager();
        ApplicationInfo runInfo = null;
        try {
            runInfo = packMan.getApplicationInfo(inputName, PackageManager.GET_DISABLED_COMPONENTS
                    | PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (final NameNotFoundException e) {
            return null;
        }
        if (runInfo != null) {
            return (String) packMan.getApplicationLabel(runInfo);
        }
        return null;
    }
}
