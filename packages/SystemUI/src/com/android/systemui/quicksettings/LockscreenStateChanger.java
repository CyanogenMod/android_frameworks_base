package com.android.systemui.quicksettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public interface LockStateChangeListener {
        public void onLockStateChange(boolean enabled);
    }

    private List<LockStateChangeListener> mListeners;
    private boolean mLockscreenEnabled;
    private static LockscreenStateChanger sInstance;
    private Context mContext;
    private KeyguardLock mLock;
    private boolean mDPMInitialized;
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
            mDPMInitialized = true;
            updateForCurrentState();
        }
    };

    private LockscreenStateChanger(Context context) {
        mListeners = Collections.synchronizedList(new ArrayList<LockStateChangeListener>());
        mContext = context.getApplicationContext();
        // Acquire lock
        KeyguardManager keyguardManager = (KeyguardManager)
                mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        mLock = keyguardManager.newKeyguardLock("LockscreenTile");
        // Fetch last state
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
        mLockscreenEnabled = mPrefs.getBoolean(KEY_DISABLED, false);
        if (!isDpmInitialized()) {
            // Register receiver
            IntentFilter filter = new IntentFilter(DevicePolicyManager
                    .ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        }
    }

    public synchronized void addListener(LockStateChangeListener listener) {
        mListeners.add(listener);
    }

    public synchronized void removeListener(LockStateChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty() && !mLockscreenEnabled) {
            // No more tiles left, lets re-enable keyguard
            toggleState();
        }
    }

    public synchronized void toggleState() {
        mLockscreenEnabled = !mLockscreenEnabled;
        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        boolean dpmInitialized = isDpmInitialized();
        if (dpmInitialized) {
            mPrefs.edit().putBoolean(KEY_DISABLED, mLockscreenEnabled).apply();
            if (mLockscreenEnabled) {
                mLock.reenableKeyguard();
            } else {
                mLock.disableKeyguard();
            }
            informListeners();
        }
    }

    private boolean isDpmInitialized() {
        if (!mDPMInitialized) {
            DevicePolicyManager dpm = (DevicePolicyManager) mContext.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                mDPMInitialized = true;
            };
        }
        return mDPMInitialized;
    }

    private void informListeners() {
        for (LockStateChangeListener listener : mListeners) {
            listener.onLockStateChange(mLockscreenEnabled);
        }
    }

    public synchronized boolean isEnabled() {
        return mLockscreenEnabled;
    }
}
