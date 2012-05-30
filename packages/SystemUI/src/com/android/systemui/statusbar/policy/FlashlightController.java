package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.CompoundButton;

import com.android.systemui.R;

public class FlashlightController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.FlashlightController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mFlashLight;

    public FlashlightController(Context context, CompoundButton checkbox) {
        mContext = context;
        mFlashLight = getFlashLight();
        mCheckBox = checkbox;
        checkbox.setChecked(mFlashLight);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("bright", !checked);
        mContext.sendBroadcast(i);
    }

    private boolean getFlashLight() {
        ContentResolver cr = mContext.getContentResolver();
        return Settings.System.getInt(cr, Settings.System.TORCH_STATE, 0) == 1;
    }
}
