package com.android.systemui.qs.tiles;


import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

public class LockscreenToggleTile extends QSTile<QSTile.BooleanState> implements KeyguardMonitor.Callback {

    private static final String KEYGUARD_SERVICE_ACTION_STATE_CHANGE =
            "com.android.internal.action.KEYGUARD_SERVICE_STATE_CHANGED";
    private static final String KEYGUARD_SERVICE_EXTRA_ACTIVE = "active";

    private static final String KEY_DISABLED = "lockscreen_disabled";

    private KeyguardMonitor mKeyguard;
    private KeyguardManager.KeyguardLock mLock;
    private boolean mLockscreenDisabled;
    private boolean mKeyguardBound;
    private SharedPreferences mPrefs;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBasedOnIntent(intent);
        }
    };

    public LockscreenToggleTile(Host host) {
        super(host);
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);

        mKeyguard = host.getKeyguardMonitor();
        mLockscreenDisabled = getLockscreenEnabled();

        IntentFilter filter = new IntentFilter(KEYGUARD_SERVICE_ACTION_STATE_CHANGE);
        Intent i = mContext.registerReceiver(mReceiver, filter);
        if (i != null) {
            updateBasedOnIntent(i);
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mKeyguard.addCallback(this);
        } else {
            mKeyguard.removeCallback(this);
        }
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setLockscreenEnabled(!mLockscreenDisabled);
        applyLockscreenState();
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean hideTile = !mLockscreenDisabled
                && (mKeyguard.isShowing() && mKeyguard.isSecure());

        state.visible = mKeyguardBound && !hideTile;
        state.label = mContext.getString(R.string.quick_settings_lockscreen);
        state.iconId = mKeyguardBound && mLockscreenDisabled
                ? R.drawable.ic_qs_lock_screen_off
                : R.drawable.ic_qs_lock_screen_on;
    }

    @Override
    public void destroy() {
        super.destroy();
        mContext.unregisterReceiver(mReceiver);
        if (mLock != null) {
            mLock.reenableKeyguard();
            mLock = null;
        }
    }

    @Override
    public void onKeyguardChanged() {
        refreshState();
    }

    private void updateBasedOnIntent(Intent intent) {
        mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_EXTRA_ACTIVE, false);
        applyLockscreenState();
    }

    private void applyLockscreenState() {
        if (!mKeyguardBound) {
            return;
        }
        if (mLock == null) {
            KeyguardManager kgm = (KeyguardManager)
                    mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
            mLock = kgm.newKeyguardLock(LockscreenToggleTile.class.getSimpleName());
        }
        if (mLockscreenDisabled) {
            mLock.disableKeyguard();
        } else {
            mLock.reenableKeyguard();
        }
        refreshState();
    }

    private boolean getLockscreenEnabled() {
        return mPrefs.getBoolean(KEY_DISABLED, false);
    }

    private void setLockscreenEnabled(boolean disabled) {
        mPrefs.edit().putBoolean(KEY_DISABLED, disabled).apply();
        mLockscreenDisabled = disabled;
    }

}
