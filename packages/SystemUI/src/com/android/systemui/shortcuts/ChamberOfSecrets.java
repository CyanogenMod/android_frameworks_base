/*
 * Copyright 2013 SlimRom - Jubakuba
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

package com.android.systemui.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import com.android.systemui.R;

public class ChamberOfSecrets extends Activity  {

    private static final int SYSTEM_INT = 0;
    private static final int SECURE_INT = 1;
    private static final int SYSTEM_LONG = 2;
    private static final int SECURE_LONG = 3;
    private static final int SYSTEM_FLOAT = 4;
    private static final int SECURE_FLOAT = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check to make sure this is enabled by user
        if (Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.CHAMBER_OF_SECRETS, 0,
                UserHandle.USER_CURRENT) == 1) {
            int type = getIntent().getIntExtra("type", 0);
            String setting = getIntent().getStringExtra("setting");
            String array = getIntent().getStringExtra("array");
            int current = 0;
            long curLong = 0;
            float curFloat = 0;

            if (setting != null && array != null) {
                switch (type) {
                    case SYSTEM_INT:
                        current= Settings.System.getIntForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.System.putInt(getContentResolver(),
                                setting, getNewInt(array, current));
                        break;
                    case SECURE_INT:
                        current = Settings.Secure.getIntForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.Secure.putInt(getContentResolver(),
                                setting, getNewInt(array, current));
                        break;
                    case SYSTEM_LONG:
                        curLong= Settings.System.getIntForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.System.putLong(getContentResolver(),
                                setting, getNewLong(array, curLong));
                        break;
                    case SECURE_LONG:
                        curLong = Settings.Secure.getLongForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.Secure.putLong(getContentResolver(),
                                setting, getNewLong(array, curLong));
                        break;
                    case SYSTEM_FLOAT:
                        curFloat= Settings.System.getLongForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.System.putFloat(getContentResolver(),
                                setting, getNewFloat(array, curFloat));
                        break;
                    case SECURE_FLOAT:
                        curFloat = Settings.Secure.getFloatForUser(getContentResolver(),
                                setting, 0, UserHandle.USER_CURRENT);
                        Settings.Secure.putFloat(getContentResolver(),
                                setting, getNewFloat(array, curFloat));
                        break;
                }
            }
        } else {
            Toast.makeText(this,
                    R.string.chamber_disabled,
                    Toast.LENGTH_LONG).show();
        }
        this.finish();
    }

    private int getNewInt(String array, int current) {
        int index = 0;
        String[] strArray = array.split(",");
        int[] intArray = new int[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            try {
                intArray[i] = Integer.parseInt(strArray[i]);
            } catch (NumberFormatException e) {
                try {
                    intArray[i] = Color.parseColor(strArray[i]);
                } catch (IllegalArgumentException ex) {
                    // We already checked this string
                    // parse won't fail
                }
            }
        }
        for (int i = 0; i < intArray.length; i++) {
            if (intArray[i] == current) {
                if (i == intArray.length - 1) {
                    index = 0;
                } else {
                    index = i + 1;
                }
            }
        }
        return intArray[index];
    }

    private long getNewLong(String array, long current) {
        int index = 0;
        String[] strArray = array.split(",");
        long[] longArray = new long[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            try {
                longArray[i] = Long.parseLong(strArray[i]);
            } catch (NumberFormatException e) {
                // We already checked this string
                // parse won't fail
            }
        }
        for (int i = 0; i < longArray.length; i++) {
            if (longArray[i] == current) {
                if (i == longArray.length - 1) {
                    index = 0;
                } else {
                    index = i + 1;
                }
            }
        }
        return longArray[index];
    }

    private float getNewFloat(String array, float current) {
        int index = 0;
        String[] strArray = array.split(",");
        float[] floatArray = new float[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            try {
                floatArray[i] = Float.parseFloat(strArray[i]);
            } catch (NumberFormatException e) {
                // We already checked this string
                // parse won't fail
            }
        }
        for (int i = 0; i < floatArray.length; i++) {
            if (floatArray[i] == current) {
                if (i == floatArray.length - 1) {
                    index = 0;
                } else {
                    index = i + 1;
                }
            }
        }
        return floatArray[index];
    }
}
