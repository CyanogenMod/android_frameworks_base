package com.android.systemui.quicksettings;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.quicksettings.LockscreenStateChanger.LockStateChange;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ToggleLockscreenTile extends QuickSettingsTile implements LockStateChange {

    private LockscreenStateChanger mLockscreenChanger;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mLockscreenChanger = LockscreenStateChanger.getInstance(context);
        mLockscreenChanger.registerTile(this);
        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLockscreenChanger.toggleState();
            }
        };
        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity("android.settings.SECURITY_SETTINGS");
                return true;
            }
        };
        qsc.registerAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED, this);
        updateResources();
    }

    @Override
    public void onDestroy() {
        mLockscreenChanger.unRegisterTile(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        mLabel = mContext.getString(R.string.quick_settings_lockscreen);
        mDrawable = mLockscreenChanger.isEnabled() ?
                R.drawable.ic_qs_lock_screen_off : R.drawable.ic_qs_lock_screen_on;
        super.updateResources();
    }

    @Override
    public void onLockStateChange(boolean enabled) {
        updateResources();
    }
}