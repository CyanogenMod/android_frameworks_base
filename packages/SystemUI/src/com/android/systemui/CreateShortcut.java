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
import java.lang.StringBuilder;

public class CreateShortcut extends LauncherActivity {

    private static final int DLG_SECRET = 0;
    private static final int DLG_SECRET_CHK = 1;
    private static final int DLG_SECRET_INT = 2;
    private static final int DLG_SECRET_NAME = 3;
    private static final int DLG_TOGGLE = 4;

    private static final int SYSTEM_INT = 0;
    private static final int SECURE_INT = 1;
    private static final int SYSTEM_LONG = 2;
    private static final int SECURE_LONG = 3;
    private static final int SYSTEM_FLOAT = 4;
    private static final int SECURE_FLOAT = 5;
    private static final int GLOBAL_INT = 6;
    private static final int GLOBAL_LONG = 7;
    private static final int GLOBAL_FLOAT = 8;

    private int mSettingType = 0;

    private Intent mShortcutIntent;
    private Intent mIntent;

    private CharSequence mName = null;

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

        mName = itemForPosition(position).label;

        mIntent = new Intent();
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                BitmapFactory.decodeResource(getResources(), returnIconResId(className)));
        mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mName);
        if (className.equals("ChamberOfSecrets")) {
            showDialogSetting(DLG_SECRET);
        } else if (className.equals("Immersive")
                || className.equals("QuietHours")
                || className.equals("Torch")
                || className.equals("ShakeEvents")
                || className.equals("Rotation")) {
            showDialogSetting(DLG_TOGGLE);
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
        } else if (c.equals("Recovery")) {
            return R.drawable.ic_qs_reboot_recovery;
        } else if (c.equals("Screenshot")) {
            return R.drawable.ic_sysbar_screenshot;
        } else if (c.equals("VolumePanel")) {
            return R.drawable.ic_qs_volume;
        } else if (c.equals("ShakeEvents")) {
            return R.drawable.ic_qs_shake_events;
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
            showDialogSetting(DLG_SECRET_NAME);
        } else {
            showDialogSetting(DLG_SECRET_INT);
        }
    }

    private void setSettingArray(String array) {
        mShortcutIntent.putExtra("array", array);
        showDialogSetting(DLG_SECRET_NAME);
    }

    private void checkIntentName(String name) {
        if (name != null && name.length() > 0) {
            mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        } else {
            Toast.makeText(CreateShortcut.this,
                    R.string.chamber_name_toast,
                    Toast.LENGTH_LONG).show();
        }
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
                            Settings.Global.getInt(
                                    getContentResolver(), value);
                            mSettingType = GLOBAL_INT;
                        } catch (SettingNotFoundException p) {
                            try {
                                Settings.Global.getLong(
                                        getContentResolver(), value);
                                mSettingType = GLOBAL_LONG;
                            } catch (SettingNotFoundException q) {
                                try {
                                    Settings.Global.getFloat(
                                            getContentResolver(), value);
                                    mSettingType = GLOBAL_FLOAT;
                                } catch (SettingNotFoundException r) {
                                    try {
                                        Settings.System.getIntForUser(
                                                getContentResolver(),
                                                value, UserHandle.USER_CURRENT);
                                        mSettingType = SYSTEM_INT;
                                    } catch (SettingNotFoundException a) {
                                        try {
                                            Settings.Secure.getIntForUser(
                                                    getContentResolver(),
                                                    value, UserHandle.USER_CURRENT);
                                            mSettingType = SECURE_INT;
                                        } catch (SettingNotFoundException b) {
                                            try {
                                                Settings.System.getLongForUser(
                                                        getContentResolver(),
                                                        value, UserHandle.USER_CURRENT);
                                                mSettingType = SYSTEM_LONG;
                                            } catch (SettingNotFoundException c) {
                                                try {
                                                    Settings.Secure.getLongForUser(
                                                            getContentResolver(),
                                                            value, UserHandle.USER_CURRENT);
                                                    mSettingType = SECURE_LONG;
                                                } catch (SettingNotFoundException d) {
                                                    try {
                                                        Settings.System.getFloatForUser(
                                                                getContentResolver(),
                                                                value, UserHandle.USER_CURRENT);
                                                        mSettingType = SYSTEM_FLOAT;
                                                    } catch (SettingNotFoundException e) {
                                                        try {
                                                            Settings.Secure.getFloatForUser(
                                                                    getContentResolver(),
                                                                    value,
                                                                    UserHandle.USER_CURRENT);
                                                            mSettingType = SECURE_FLOAT;
                                                        } catch (SettingNotFoundException f) {
                                                            resultOk = false;
                                                        }
                                                    }
                                                }
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
                            Toast.makeText(CreateShortcut.this,
                                    R.string.chamber_invalid_setting,
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
                        if (strArray.length >= 1 && str.length() > 0) {
                            switch (mSettingType) {
                                case SYSTEM_INT:
                                case SECURE_INT:
                                case GLOBAL_INT:
                                    int[] intArray = new int[strArray.length];
                                    for (int i = 0; i < strArray.length; i++) {
                                        try {
                                            intArray[i] = Integer.parseInt(strArray[i]);
                                        } catch (NumberFormatException e) {
                                            try {
                                                intArray[i] = Color.parseColor(strArray[i]);
                                                // Let's avoid color parsing a seond time.
                                                strArray[i] = Integer.toString(
                                                        Color.parseColor(strArray[i]));
                                            } catch (IllegalArgumentException ex) {
                                                resultOk = false;
                                            }
                                        }
                                    }
                                    break;
                                case SYSTEM_LONG:
                                case SECURE_LONG:
                                case GLOBAL_LONG:
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
                                case GLOBAL_FLOAT:
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
                        } else {
                            resultOk = false;
                        }

                        if (resultOk) {
                            // Rebuild string with ints needed if color values are used.
                            StringBuilder builder = new StringBuilder();
                            for(String st : strArray) {
                                builder.append(st + ",");
                            }
                            str = builder.toString();
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
            case DLG_SECRET_NAME:
                final EditText inputName = new EditText(this);

                AlertDialog.Builder alertName = new AlertDialog.Builder(this);
                alertName.setTitle(R.string.chamber_name_title)
                .setMessage(R.string.chamber_name_message)
                .setView(inputName)
                .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        checkIntentName(null);
                    }
                })
                .setPositiveButton(R.string.dlg_ok,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String name = inputName.getText().toString();
                        checkIntentName(name);
                    }
                });
                alertName.show();
                break;
            case DLG_TOGGLE:
                final CharSequence[] items = {
                    getResources().getString(R.string.off),
                    getResources().getString(R.string.on),
                    getResources().getString(R.string.toggle),
                };
                AlertDialog.Builder alertToggle = new AlertDialog.Builder(this);
                alertToggle.setTitle(R.string.shortcut_toggle_title)
                .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, final int item) {
                        mShortcutIntent.putExtra("value", item);
                        mIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                                mName + " " + items[item]);
                        finalizeIntent();
                    }
                });
                alertToggle.show();
                break;
        }
    }
}
