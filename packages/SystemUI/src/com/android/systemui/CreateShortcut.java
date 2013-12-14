/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LauncherActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.View;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.systemui.R;

import java.lang.Character;
import java.lang.CharSequence;
import java.lang.IllegalArgumentException;
import java.lang.NumberFormatException;

public class CreateShortcut extends LauncherActivity {

    private static final int DLG_SECRET = 0;
    private static final int DLG_SECRET_CHK = 1;
    private static final int DLG_SECRET_INT = 2;

    private static final int SYSTEM_INT = 0;
    private static final int SECURE_INT = 1;
    private static final int SYSTEM_LONG = 2;
    private static final int SECURE_LONG = 3;
    private static final int SYSTEM_FLOAT = 4;
    private static final int SECURE_FLOAT = 5;

    private int mSettingType = 0;

    private Intent mShortcutIntent;
    private Intent mIntent;

    @Override
    protected Intent getTargetIntent() {
        Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
        if (Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.CHAMBER_OF_SECRETS, 0,
                UserHandle.USER_CURRENT) == 1) {
            targetIntent.addCategory("com.android.systemui.SHORTCUT_EXTRA");
        } else {
            targetIntent.addCategory("com.android.systemui.SHORTCUT");
        }
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return targetIntent;
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        mShortcutIntent = intentForPosition(position);

        String intentClass = mShortcutIntent.getComponent().getClassName();
        String className = intentClass.substring(intentClass.lastIndexOf(".") + 1);
        String intentAction = mShortcutIntent.getAction();

        mShortcutIntent = new Intent();
        mShortcutIntent.setClassName(this, intentClass);
        mShortcutIntent.setAction(intentAction);

        mIntent = new Intent();
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                BitmapFactory.decodeResource(getResources(), returnIconResId(className)));
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, itemForPosition(position).label);
        if (className.equals("ChamberOfSecrets")) {
            showDialogSetting(DLG_SECRET);
        } else {
            finalizeIntent();
        }
    }

    private int returnIconResId(String c) {
        if (c.equals ("Immersive")) {
            return R.drawable.ic_qs_expanded_desktop_on;
        } else if (c.equals ("QuietHours")) {
            return R.drawable.ic_qs_quiet_hours_on;
        } else if (c.equals("Rotation")) {
            return R.drawable.ic_qs_auto_rotate;
        } else if (c.equals("Torch")) {
            return R.drawable.ic_qs_torch_on;
        } else if (c.equals("LastApp")) {
            return R.drawable.ic_sysbar_lastapp;
        } else if (c.equals("Reboot")) {
            return R.drawable.ic_qs_reboot;
        } else if (c.equals("Screenshot")) {
            return R.drawable.ic_sysbar_screenshot;
        } else if (c.equals("ChamberOfSecrets")) {
            return R.drawable.ic_qs_reboot_recovery;
        } else {
            // Oh-Noes, you found a wild derp.
            return R.drawable.ic_sysbar_null;
        }
    }

    private void finalizeIntent() {
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, mShortcutIntent);
        setResult(RESULT_OK, mIntent);
        finish();
    }

    private void setSettingString(String set) {
        mShortcutIntent.putExtra("setting", set);
        mShortcutIntent.putExtra("type", mSettingType);
        showDialogSetting(DLG_SECRET_CHK);
    }

    private void setCheck(boolean isCheck) {
        if (isCheck) {
            String check = "0,1";
            mShortcutIntent.putExtra("array", check);
            finalizeIntent();
        } else {
            showDialogSetting(DLG_SECRET_INT);
        }
    }

    private void setSettingArray(String array) {
        mShortcutIntent.putExtra("array", array);
        finalizeIntent();
    }

    private void showDialogSetting(int id) {
        switch (id) {
            case DLG_SECRET:
                final EditText input = new EditText(this);

                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle(R.string.chamber_title)
                .setMessage(R.string.chamber_message)
                .setView(input)
                .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setPositiveButton(R.string.dlg_ok,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().toLowerCase();
                        boolean resultOk = true;
                        boolean secure = false;
                        int test = 0;
                        long testLong = 0;
                        float testFloat = 0;
                        // Necessary ugly code.  Do it here so we don't have to again.
                        try {
                            test = Settings.System.getIntForUser(
                                    getContentResolver(),
                                    value, UserHandle.USER_CURRENT);
                            mSettingType = SYSTEM_INT;
                        } catch (Settings.SettingNotFoundException a) {
                            try {
                                test = Settings.Secure.getIntForUser(
                                        getContentResolver(),
                                        value, UserHandle.USER_CURRENT);
                                mSettingType = SECURE_INT;
                            } catch (Settings.SettingNotFoundException b) {
                                try {
                                    testLong = Settings.System.getLongForUser(
                                            getContentResolver(),
                                            value, UserHandle.USER_CURRENT);
                                    mSettingType = SYSTEM_LONG;
                                } catch (Settings.SettingNotFoundException c) {
                                    try {
                                        testLong = Settings.Secure.getLongForUser(
                                                getContentResolver(),
                                                value, UserHandle.USER_CURRENT);
                                        mSettingType = SECURE_LONG;
                                    } catch (Settings.SettingNotFoundException d) {
                                        try {
                                            testFloat = Settings.System.getFloatForUser(
                                                    getContentResolver(),
                                                    value, UserHandle.USER_CURRENT);
                                            mSettingType = SYSTEM_FLOAT;
                                        } catch (Settings.SettingNotFoundException e) {
                                            try {
                                                testFloat = Settings.Secure.getFloatForUser(
                                                        getContentResolver(),
                                                        value, UserHandle.USER_CURRENT);
                                                mSettingType = SECURE_FLOAT;
                                            } catch (Settings.SettingNotFoundException f) {
                                                resultOk = false;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (resultOk) {
                            setSettingString(value);
                        } else {
                            Toast.makeText(CreateShortcut.this,
                                    R.string.chamber_invalid,
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
                alert.show();
                break;
            case DLG_SECRET_CHK:
                AlertDialog.Builder alertChk = new AlertDialog.Builder(this);
                alertChk.setTitle(R.string.chamber_checkbox)
                .setMessage(R.string.chamber_chk_message)
                .setNegativeButton(R.string.no,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setCheck(false);
                    }
                })
                .setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setCheck(true);
                    }
                });
                alertChk.show();
                break;
            case DLG_SECRET_INT:
                final EditText edit = new EditText(this);
                edit.setHorizontallyScrolling(true);
                AlertDialog.Builder alertInt = new AlertDialog.Builder(this);
                alertInt.setTitle(R.string.chamber_int)
                .setMessage(R.string.chamber_int_message)
                .setView(edit)
                .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setPositiveButton(R.string.dlg_ok,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String str = edit.getText().toString();
                        str = str.replaceAll("\\s+", "");
                        String[] strArray = str.split(",");
                        boolean resultOk = true;

                        switch (mSettingType) {
                            case SYSTEM_INT:
                            case SECURE_INT:
                                int[] intArray = new int[strArray.length];
                                for (int i = 0; i < strArray.length; i++) {
                                    try {
                                        intArray[i] = Integer.parseInt(strArray[i]);
                                    } catch (NumberFormatException e) {
                                        try {
                                            intArray[i] = Color.parseColor(strArray[i]);
                                        } catch (IllegalArgumentException ex) {
                                            resultOk = false;
                                        }
                                    }
                                }
                                break;
                            case SYSTEM_LONG:
                            case SECURE_LONG:
                                long[] longArray = new long[strArray.length];
                                for (int i = 0; i < strArray.length; i++) {
                                    try {
                                        longArray[i] = Long.parseLong(strArray[i]);
                                    } catch (NumberFormatException e) {
                                        resultOk = false;
                                    }
                                }
                                break;
                            case SYSTEM_FLOAT:
                            case SECURE_FLOAT:
                                float[] floatArray = new float[strArray.length];
                                for (int i = 0; i < strArray.length; i++) {
                                    try {
                                        floatArray[i] = Float.parseFloat(strArray[i]);
                                    } catch (NumberFormatException ex) {
                                        resultOk = false;
                                    }
                                }
                                break;
                        }

                        if (resultOk) {
                            // Set to string.  Launcher doesn't persist array
                            // extras after a reboot.
                            setSettingArray(str);
                        } else {
                            Toast.makeText(CreateShortcut.this,
                                    R.string.chamber_invalid,
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
                alertInt.show();
                break;
        }
    }
}
