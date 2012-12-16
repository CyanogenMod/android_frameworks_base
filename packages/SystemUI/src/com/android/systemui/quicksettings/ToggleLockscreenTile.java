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
import com.android.systemui.statusbar.powerwidget.PowerButton;

public class ToggleLockscreenTile extends QuickSettingsTile {

    private KeyguardLock mLock = null;
    private static final String KEY_DISABLED = "lockscreen_disabled";

    private final KeyguardManager mKeyguardManager;
    private boolean mDisabledLockscreen = false;
    private SharedPreferences sp;

    public ToggleLockscreenTile(Context context,
            LayoutInflater inflater, QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mLabel = mContext.getString(R.string.quick_settings_lockscreen);

        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                mDisabledLockscreen = !mDisabledLockscreen;

                sp = mContext.getSharedPreferences("PowerButton-" + PowerButton.BUTTON_LOCKSCREEN, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean(KEY_DISABLED, mDisabledLockscreen);
                editor.apply();

                applyLockscreenChanges();
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
        applyLockscreenChanges();
        super.onPostCreate();
    }

    void applyLockscreenChanges() {
        if (mLock == null) {
            KeyguardManager keyguardManager = (KeyguardManager)
                    mContext.getSystemService(Context.KEYGUARD_SERVICE);
            mLock = keyguardManager.newKeyguardLock("PowerWidget");
        }
        if (mDisabledLockscreen) {
            mDrawable = R.drawable.ic_qs_lock_screen_off;
            mLock.disableKeyguard();
        } else {
            mDrawable = R.drawable.ic_qs_lock_screen_on;
            mLock.reenableKeyguard();
        }
        updateQuickSettings();
    }

}
