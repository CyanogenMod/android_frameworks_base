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

    private static final String KEY_DISABLED = "lockscreen_disabled";

    private static KeyguardLock sLock = null;
    private static int sLockTileCount = 0;
    private static boolean sDisabledLockscreen = false;

    public ToggleLockscreenTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                sDisabledLockscreen = !sDisabledLockscreen;
                mPrefs.edit().putBoolean(KEY_DISABLED, sDisabledLockscreen).apply();
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
        if (sLockTileCount == 0) {
            sDisabledLockscreen = mPrefs.getBoolean(KEY_DISABLED, false);
            updateLockscreenState();
        }
        sLockTileCount++;
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        sLockTileCount--;
        if (sLock != null && sLockTileCount < 1 && sDisabledLockscreen) {
            sLock.reenableKeyguard();
            sLock = null;
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
        mDrawable = sDisabledLockscreen ?
                R.drawable.ic_qs_lock_screen_off : R.drawable.ic_qs_lock_screen_on;
    }

    private void updateLockscreenState() {
        if (sLock == null) {
            KeyguardManager keyguardManager = (KeyguardManager)
                    mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
            sLock = keyguardManager.newKeyguardLock("LockscreenTile");
        }
        if (sDisabledLockscreen) {
            sLock.disableKeyguard();
        } else {
            sLock.reenableKeyguard();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (KEY_DISABLED.equals(key)) {
            updateResources();
        }
    }
}
