package com.android.systemui.qs;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.systemui.R;

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
            mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    // TODO update table with new value
                    Log.d(TAG, "onCheckedChanged() called with "
                            + "buttonView = [" + buttonView + "], isChecked = [" + isChecked
                            + "] and table: " + mWhichTable + ", and key: " + mKey);
                }
            });
        }

        a.recycle();
    }

    @Override
    public void onClick(View v) {
        mSwitch.setChecked(!mSwitch.isChecked());
    }
}
