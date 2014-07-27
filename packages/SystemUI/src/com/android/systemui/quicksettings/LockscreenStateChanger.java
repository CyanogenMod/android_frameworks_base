package com.android.systemui.quicksettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.SharedPreferences;

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

    public synchronized void setInitialized(boolean initialized) {
        mInitialized = initialized;
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
    }

    public synchronized void addListener(LockStateChangeListener listener) {
        if (mListeners.isEmpty()) {
            updateForCurrentState();
        }
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
        if (!mInitialized) {
            return;
        }
        mLockscreenDisabled = !mLockscreenDisabled;
        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        if (mInitialized) {
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
