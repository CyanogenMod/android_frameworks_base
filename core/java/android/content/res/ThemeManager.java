/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.content.res;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.List;

/**
 * {@hide}
 */
public class ThemeManager {
    private static final String TAG = ThemeManager.class.getName();
    private Context mContext;
    private IThemeService mService;
    private Handler mHandler;

    private HashMap<String, ThemeChangeListener> mListeners =
            new HashMap<String, ThemeChangeListener>();

    public ThemeManager(Context context, IThemeService service) {
        mContext = context;
        mService = service;
        mHandler = new Handler(Looper.getMainLooper());
    }

    private final IThemeChangeListener mThemeChangeListener = new IThemeChangeListener.Stub() {
        @Override
        public void onProgress(final int progress, final String pkgName) throws RemoteException {
            final ThemeChangeListener listener = mListeners.get(pkgName);
            if (listener != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onProgress(progress);
                        } catch (Throwable e) {
                            Log.w(TAG, "Unable to update progress for " + pkgName, e);
                        }
                    }
                });
            }
        }

        @Override
        public void onFinish(final boolean isSuccess, final String pkgName) throws RemoteException {
            final ThemeChangeListener listener = mListeners.get(pkgName);
            if (listener != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onFinish(isSuccess);
                        } catch (Throwable e) {
                            Log.w(TAG, "Unable to update listener for " + pkgName, e);
                        }
                    }
                });
            }
        }
    };

    public void addClient(String pkgName, ThemeChangeListener listener) {
        if (mListeners.containsKey(pkgName)) {
            throw new IllegalArgumentException("Client was already added ");
        }
        if (mListeners.size() == 0) {
            try {
                mService.requestThemeChangeUpdates(mThemeChangeListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to register listener", e);
            }
        }
        mListeners.put(pkgName, listener);
    }

    public void removeClient(String pkgName) {
        mListeners.remove(pkgName);
        if (mListeners.size() == 0) {
            try {
                mService.removeUpdates(mThemeChangeListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to remove listener", e);
            }
        }
    }

    public void onClientPaused(String pkgName) {
        removeClient(pkgName);
    }

    public void onClientResumed(String pkgName, ThemeChangeListener listener) {
        addClient(pkgName, listener);
    }

    public void onClientDestroyed(String pkgName) {
        removeClient(pkgName);
    }

    public void requestThemeChange(String pkgName, List<String> components) {
        try {
            mService.requestThemeChange(pkgName, components);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to access ThemeService", e);
        }
    }

    public void applyDefaultTheme() {
        try {
            mService.applyDefaultTheme();
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to access ThemeService", e);
        }
    }

    public boolean isThemeApplying(String pkgName) {
        try {
            return mService.isThemeApplying(pkgName);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to access ThemeService", e);
        }

        return false;
    }

    public int getProgress(String pkgName) {
        try {
            return mService.getProgress(pkgName);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to access ThemeService", e);
        }
        return -1;
    }

    public interface ThemeChangeListener {
        void onProgress(int progress);
        void onFinish(boolean isSuccess);
    }
}
