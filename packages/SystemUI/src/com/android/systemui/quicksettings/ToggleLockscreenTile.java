package com.android.systemui.quicksettings;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

@SuppressWarnings("deprecation")
public class ToggleLockscreenTile extends QuickSettingsTile
        implements OnSharedPreferenceChangeListener {

    private KeyguardLock mLock = null;
    private static final String KEY_DISABLED = "lockscreen_disabled";

    private boolean mDisabledLockscreen;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc,
            KeyguardLock lock) {
        super(context, qsc);

        mLock = lock;
        mDisabledLockscreen = mPrefs.getBoolean(KEY_DISABLED, false);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                mDisabledLockscreen = !mDisabledLockscreen;
                mPrefs.edit().putBoolean(KEY_DISABLED, mDisabledLockscreen).apply();
                updateLockscreenState();
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
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        updateTile();
        updateLockscreenState();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mDisabledLockscreen = false;
        updateLockscreenState();
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mLabel = mContext.getString(R.string.quick_settings_lockscreen);
        mDrawable = (mDisabledLockscreen) ?
                R.drawable.ic_qs_lock_screen_off : R.drawable.ic_qs_lock_screen_on;
    }

    private void updateLockscreenState() {
        if (mDisabledLockscreen) {
            mLock.disableKeyguard();
        } else {
            mLock.reenableKeyguard();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == KEY_DISABLED) {
            mDisabledLockscreen =  mPrefs.getBoolean(KEY_DISABLED, false);
            updateResources();
        }
    }

}
