package com.android.systemui.quicksettings;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ToggleLockscreenTile extends QuickSettingsTile implements
    LockscreenStateChanger.LockStateChangeListener {

    private LockscreenStateChanger mLockscreenChanger;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mLockscreenChanger = LockscreenStateChanger.getInstance(context);
        mLockscreenChanger.addListener(this);
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
        updateResources();
    }

    @Override
    public void onDestroy() {
        mLockscreenChanger.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        mLabel = mContext.getString(R.string.quick_settings_lockscreen);
        mDrawable = mLockscreenChanger.isDisabled() ?
                R.drawable.ic_qs_lock_screen_on : R.drawable.ic_qs_lock_screen_off;
        super.updateResources();
    }

    @Override
    public void onLockStateChange(boolean enabled) {
        updateResources();
    }
}