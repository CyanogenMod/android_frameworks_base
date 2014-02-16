package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;

import com.android.internal.util.nameless.constants.FlashLightConstants;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class TorchTile extends QuickSettingsTile {
    private boolean mActive = false;

    public TorchTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(FlashLightConstants.ACTION_TOGGLE_STATE);
                mContext.sendBroadcast(i);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(FlashLightConstants.INTENT_LAUNCH_APP);
                return true;
            }
        };

        qsc.registerAction(FlashLightConstants.ACTION_STATE_CHANGED, this);
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
        final String state = intent.getStringExtra(FlashLightConstants.EXTRA_CURRENT_STATE);
        mActive = ((state != null) && (state.equals("1")));
        updateResources();
    }
}
