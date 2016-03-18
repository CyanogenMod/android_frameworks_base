/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.systemui.qs;

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.systemui.R;
import cyanogenmod.providers.CMSettings;

public class QSBooleanSettingRow extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "QSSettingRow";

    public static final int TABLE_SYSTEM = 0;
    public static final int TABLE_GLOBAL = 1;
    public static final int TABLE_SECURE = 2;

    public static final int TABLE_CM_SYSTEM = 3;
    public static final int TABLE_CM_GLOBAL = 4;
    public static final int TABLE_CM_SECURE = 5;

    int mWhichTable;
    String mTitle;
    String mKey;
    private TextView mText;
    private Switch mSwitch;
    private int mDefaultValue;
    private CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener;

    public QSBooleanSettingRow(Context context) {
        this(context, null);
    }

    public QSBooleanSettingRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSBooleanSettingRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSBooleanSettingRow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        View.inflate(context, R.layout.qs_settings_row, this);

        setOrientation(HORIZONTAL);
        setClickable(true);
        setOnClickListener(this);

        mText = (TextView) findViewById(R.id.title);
        mSwitch = (Switch) findViewById(R.id.switcher);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuickSettingsRow,
                defStyleAttr, defStyleRes);

        mWhichTable = a.getInteger(R.styleable.QuickSettingsRow_table, -1);

        mTitle = a.getString(R.styleable.QuickSettingsRow_android_title);
        mKey = a.getString(R.styleable.QuickSettingsRow_android_key);
        mDefaultValue = a.getInt(R.styleable.QuickSettingsRow_defaultValue, 0);

        if (mText != null) {
            mText.setText(mTitle);
            mText.setClickable(false);
            mText.setFocusable(false);
        }

        if (mSwitch != null) {
            mSwitch.setClickable(false);
            mSwitch.setFocusable(false);
            mSwitch.setChecked(getCurrent());
            mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (false) Log.d(TAG, "onCheckedChanged() called with "
                            + "buttonView = [" + buttonView + "], isChecked = [" + isChecked
                            + "] and table: " + mWhichTable + ", and key: " + mKey);
                    applyChange(isChecked);
                    if (mOnCheckedChangeListener != null) {
                        mOnCheckedChangeListener.onCheckedChanged(buttonView, isChecked);
                    }
                }
            });
        }

        a.recycle();
    }

    public void setChecked(boolean checked) {
        if (mSwitch.isChecked() == checked) {
            return;
        }
        mSwitch.setChecked(checked);
    }

    private void applyChange(boolean value) {
        ContentResolver cr = getContext().getContentResolver();
        switch (mWhichTable) {
            case TABLE_GLOBAL:
                Settings.Global.putInt(cr, mKey, value ? 1 : 0);
                break;
            case TABLE_SECURE:
                Settings.Secure.putInt(cr, mKey, value ? 1 : 0);
                break;
            case TABLE_SYSTEM:
                Settings.System.putInt(cr, mKey, value ? 1 : 0);
                break;
            case TABLE_CM_GLOBAL:
                CMSettings.Global.putInt(cr, mKey, value ? 1 : 0);
                break;
            case TABLE_CM_SECURE:
                CMSettings.Secure.putInt(cr, mKey, value ? 1 : 0);
                break;
            case TABLE_CM_SYSTEM:
                CMSettings.System.putInt(cr, mKey, value ? 1 : 0);
                break;
        }
    }

    private boolean getCurrent() {
        ContentResolver cr = getContext().getContentResolver();
        int ret = 0;
        switch (mWhichTable) {
            case TABLE_GLOBAL:
                ret = Settings.Global.getInt(cr, mKey, mDefaultValue);
                break;
            case TABLE_SECURE:
                ret = Settings.Secure.getInt(cr, mKey, mDefaultValue);
                break;
            case TABLE_SYSTEM:
                ret = Settings.System.getInt(cr, mKey, mDefaultValue);
                break;
            case TABLE_CM_GLOBAL:
                ret = CMSettings.Global.getInt(cr, mKey, mDefaultValue);
                break;
            case TABLE_CM_SECURE:
                ret = CMSettings.Secure.getInt(cr, mKey, mDefaultValue);
                break;
            case TABLE_CM_SYSTEM:
                ret = CMSettings.System.getInt(cr, mKey, mDefaultValue);
                break;
        }
        return ret == 1;
    }

    @Override
    public void onClick(View v) {
        mSwitch.setChecked(!mSwitch.isChecked());
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
        mOnCheckedChangeListener = l;
    }
}
