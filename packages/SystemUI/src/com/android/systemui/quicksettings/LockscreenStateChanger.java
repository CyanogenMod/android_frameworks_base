package com.android.systemui.quicksettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

@SuppressWarnings("deprecation")
public class LockscreenStateChanger {

    private static final String KEY_DISABLED = "lockscreen_disabled";
    public interface LockStateChange {
        public void onLockStateChange(boolean enabled);
    }

    private List<LockStateChange> mListeners;
    private AtomicBoolean mLockscreenEnabled;
    private static LockscreenStateChanger sInstance;
    private Context mContext;
    private KeyguardLock mLock;
    private AtomicBoolean mDPMInitialized;
    private SharedPreferences mPrefs;

    public static synchronized LockscreenStateChanger getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LockscreenStateChanger(context);
        }
        return sInstance;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(this);
            mDPMInitialized.set(true);
            updateForCurrentState();
        }
    };

    private LockscreenStateChanger(Context context) {
        mListeners = Collections.synchronizedList(new ArrayList<LockStateChange>());
        mContext = context.getApplicationContext();
        mDPMInitialized = new AtomicBoolean(false);
        // Acquire lock
        KeyguardManager keyguardManager = (KeyguardManager)
                mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        mLock = keyguardManager.newKeyguardLock("LockscreenTile");
        // Fetch last state
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
        mLockscreenEnabled = new AtomicBoolean(mPrefs.getBoolean(KEY_DISABLED, false));
        if (!isDpmInitialized()) {
            // Register receiver
            IntentFilter filter = new IntentFilter(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        }
    }

    public synchronized void registerTile(LockStateChange listener) {
        mListeners.add(listener);
    }

    public synchronized void unRegisterTile(LockStateChange listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty() && !mLockscreenEnabled.get()) {
            // No more tiles left, lets re-enable keyguard
            toggleState();
        }
    }

    public synchronized void toggleState() {
        mLockscreenEnabled.set(!mLockscreenEnabled.get());
        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        boolean dpmInitialized = isDpmInitialized();
        if (dpmInitialized) {
            mPrefs.edit().putBoolean(KEY_DISABLED, mLockscreenEnabled.get()).apply();
            if (mLockscreenEnabled.get()) {
                mLock.reenableKeyguard();
            } else {
                mLock.disableKeyguard();
            }
            informListeners();
        }
    }

    private boolean isDpmInitialized() {
        if (!mDPMInitialized.get()) {
            DevicePolicyManager dpm = (DevicePolicyManager) mContext.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                mDPMInitialized.set(true);
            };
        }
        return mDPMInitialized.get();
    }

    private void informListeners() {
        for (LockStateChange listener : mListeners) {
            listener.onLockStateChange(mLockscreenEnabled.get());
        }
    }

    public synchronized boolean isEnabled() {
        return mLockscreenEnabled.get();
    }
}
