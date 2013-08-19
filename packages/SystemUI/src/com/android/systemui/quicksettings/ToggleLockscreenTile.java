package com.android.systemui.quicksettings;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

@SuppressWarnings("deprecation")
public class ToggleLockscreenTile extends QuickSettingsTile {

    private KeyguardLock mLock = null;
    private static final String KEY_DISABLED = "lockscreen_disabled";

    private boolean mDisabledLockscreen;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                mDisabledLockscreen = !mDisabledLockscreen;
                updateResources();
            }
        };

        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity("android.settings.SECURITY_SETTINGS");
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        if (mLock != null) {
            mLock.reenableKeyguard();
        }
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mLabel = mContext.getString(R.string.quick_settings_lockscreen);
        if (mLock == null) {
            KeyguardManager keyguardManager = (KeyguardManager)
                    mContext.getSystemService(Context.KEYGUARD_SERVICE);
            mLock = keyguardManager.newKeyguardLock("LockscreenTile");
        }
        if (mDisabledLockscreen) {
            mDrawable = R.drawable.ic_qs_lock_screen_off;
            mLock.disableKeyguard();
        } else {
            mDrawable = R.drawable.ic_qs_lock_screen_on;
            mLock.reenableKeyguard();
        }
    }

}
