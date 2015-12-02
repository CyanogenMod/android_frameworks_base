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

public class QSSettingRow extends LinearLayout implements View.OnClickListener {

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

    public QSSettingRow(Context context) {
        this(context, null);
    }

    public QSSettingRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSSettingRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSSettingRow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
                    applyChange(isChecked);
                    // TODO update table with new value
                    Log.d(TAG, "onCheckedChanged() called with "
                            + "buttonView = [" + buttonView + "], isChecked = [" + isChecked
                            + "] and table: " + mWhichTable + ", and key: " + mKey);
                }
            });
        }

        a.recycle();
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
        try {
            switch (mWhichTable) {
                case TABLE_GLOBAL:
                    ret = Settings.Global.getInt(cr, mKey);

                    break;
                case TABLE_SECURE:
                    ret = Settings.Secure.getInt(cr, mKey);
                    break;
                case TABLE_SYSTEM:
                    ret = Settings.System.getInt(cr, mKey);
                    break;
                case TABLE_CM_GLOBAL:
                    ret = CMSettings.Global.getInt(cr, mKey);
                    break;
                case TABLE_CM_SECURE:
                    ret = CMSettings.Secure.getInt(cr, mKey);
                    break;
                case TABLE_CM_SYSTEM:
                    ret = CMSettings.System.getInt(cr, mKey);
                    break;
            }
        } catch (Settings.SettingNotFoundException|CMSettings.CMSettingNotFoundException e) {
            Log.e(TAG, "need to add a default setting for key: " + mKey
                    + " in table: " + mWhichTable);
            e.printStackTrace();
        }
        return ret == 1;
    }

    @Override
    public void onClick(View v) {
        mSwitch.setChecked(!mSwitch.isChecked());
    }
}
