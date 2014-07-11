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

import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;

@SuppressWarnings("deprecation")
public class LockscreenStateChanger {

    private static final String KEY_DISABLED = "lockscreen_disabled";
    public interface LockStateChangeListener {
        public void onLockStateChange(boolean enabled);
    }

    private List<LockStateChangeListener> mListeners;
    private boolean mLockscreenDisabled;
    private static LockscreenStateChanger sInstance;
    private Context mContext;
    private KeyguardLock mLock;
    private boolean mInitialized;
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
            mInitialized = true;
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
        mLockscreenDisabled = mPrefs.getBoolean(KEY_DISABLED, false);
        if (!isInitialized()) {
            // Register receiver
            IntentFilter filter = new IntentFilter(DevicePolicyManager
                    .ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            updateForCurrentState();
        }
    }

    public synchronized void addListener(LockStateChangeListener listener) {
        mListeners.add(listener);
    }

    public synchronized void removeListener(LockStateChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty() && mLockscreenDisabled) {
            // No more tiles left, lets re-enable keyguard
            toggleState();
        }
    }

    public synchronized void toggleState() {
        mLockscreenDisabled = !mLockscreenDisabled;
        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        boolean dpmInitialized = isInitialized();
        if (dpmInitialized) {
            mPrefs.edit().putBoolean(KEY_DISABLED, mLockscreenDisabled).apply();
            if (mLockscreenDisabled) {
                mLock.disableKeyguard();
            } else {
                mLock.reenableKeyguard();
            }
            informListeners();
        }
    }

    private boolean isInitialized() {
        if (!mInitialized) {
            KeyguardTouchDelegate keyguard = KeyguardTouchDelegate.getInstance(mContext);
            if (keyguard.isServiceInitialized()) {
                mInitialized = true;
            }
        }
        return mInitialized;
    }

    private void informListeners() {
        for (LockStateChangeListener listener : mListeners) {
            listener.onLockStateChange(mLockscreenDisabled);
        }
    }

    public synchronized boolean isDisabled() {
        return mLockscreenDisabled;
    }
}
