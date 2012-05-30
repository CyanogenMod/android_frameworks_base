package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.net.ConnectivityManager;

import com.android.systemui.R;

public class MobileDataController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.MobileDataController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mMobileData;

    public MobileDataController(Context context, CompoundButton checkbox) {
        mContext = context;
        mMobileData = getMobileData();
        mCheckBox = checkbox;
        checkbox.setChecked(mMobileData);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(checked);
    }

    private boolean getMobileData() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

}
