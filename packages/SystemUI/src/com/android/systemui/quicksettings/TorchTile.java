package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class TorchTile extends QuickSettingsTile {
    private boolean mActive = false;

    public TorchTile(Context context, 
            QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
                mContext.sendBroadcast(i);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("net.cactii.flash2", "net.cactii.flash2.MainActivity");
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerAction("net.cactii.flash2.TORCH_STATE_CHANGED", this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (mActive) {
            mDrawable = R.drawable.ic_qs_torch_on;
            mLabel = mContext.getString(R.string.quick_settings_torch);
        } else {
            mDrawable = R.drawable.ic_qs_torch_off;
            mLabel = mContext.getString(R.string.quick_settings_torch_off);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mActive = intent.getIntExtra("state", 0) != 0;
        updateResources();
    }
}
