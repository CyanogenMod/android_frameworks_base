package com.android.systemui.quicksettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

@SuppressWarnings("deprecation")
public class LockscreenStateChanger {

    public static final String KEYGUARD_SERVICE_BROADCAST = "com.android.action.KEYGUARD_SERVICE";
    public static final String KEYGUARD_SERVICE_STATE_EXTRA = "service_state";
    private static final String KEY_DISABLED = "lockscreen_disabled";
    public interface LockStateChangeListener {
        public void onLockStateChange(boolean enabled);
    }

    private List<LockStateChangeListener> mListeners;
    private boolean mLockscreenDisabled;
    private static LockscreenStateChanger sInstance;
    private Context mContext;
    private KeyguardLock mLock;
    private boolean mKeyguardBound;
    private SharedPreferences mPrefs;

    public static synchronized LockscreenStateChanger getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LockscreenStateChanger(context);
        }
        return sInstance;
    }

    public synchronized void setInitialized(boolean initialized) {
        mKeyguardBound = initialized;
        updateForCurrentState();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBasedOnIntent(intent);
        }
    };

    private void updateBasedOnIntent(Intent intent) {
        mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_STATE_EXTRA, false);
        updateForCurrentState();
    }

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
        if (!mKeyguardBound) {
            registerReceiver();
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(KEYGUARD_SERVICE_BROADCAST);
        Intent i = mContext.registerReceiver(mReceiver, filter);
        if (i != null) {
            updateBasedOnIntent(i);
        }
    }

    public synchronized void addListener(LockStateChangeListener listener) {
        if (mListeners.isEmpty()) {
            registerReceiver();
            updateForCurrentState();
        }
        mListeners.add(listener);
    }

    public synchronized void removeListener(LockStateChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            if (mLockscreenDisabled) {
                // No more tiles left, lets re-enable keyguard
                toggleState();
            }
            mContext.unregisterReceiver(mReceiver);
        }
    }

    public synchronized void toggleState() {
        if (!mKeyguardBound) {
            return;
        }
        mLockscreenDisabled = !mLockscreenDisabled;
        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        if (mKeyguardBound) {
            mPrefs.edit().putBoolean(KEY_DISABLED, mLockscreenDisabled).apply();
            if (mLockscreenDisabled) {
                mLock.disableKeyguard();
            } else {
                mLock.reenableKeyguard();
            }
            informListeners();
        }
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
