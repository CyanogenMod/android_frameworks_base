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
import android.content.pm.ThemeUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@hide}
 */
public class ThemeManager {
    private static final String TAG = ThemeManager.class.getName();
    private Context mContext;
    private IThemeService mService;
    private Handler mHandler;

    private Set<ThemeChangeListener> mListeners =
            new HashSet<ThemeChangeListener>();

    public ThemeManager(Context context, IThemeService service) {
        mContext = context;
        mService = service;
        mHandler = new Handler(Looper.getMainLooper());
    }

    private final IThemeChangeListener mThemeChangeListener = new IThemeChangeListener.Stub() {
        @Override
        public void onProgress(final int progress) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Iterator<ThemeChangeListener> iterator = mListeners.iterator();
                    while(iterator.hasNext()) {
                        try {
                            iterator.next().onProgress(progress);
                        } catch (Throwable e) {
                            Log.w(TAG, "Unable to update theme change progress", e);
                            iterator.remove();
                        }
                    }
                }
            });
        }

        @Override
        public void onFinish(final boolean isSuccess) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Iterator<ThemeChangeListener> iterator = mListeners.iterator();
                    while(iterator.hasNext()) {
                        try {
                            iterator.next().onFinish(isSuccess);
                        } catch (Throwable e) {
                            Log.w(TAG, "Unable to update theme change listener", e);
                            iterator.remove();
                        }
                    }
                }
            });
        }
    };

    public void addClient(ThemeChangeListener listener) {
        if (mListeners.contains(listener)) {
            throw new IllegalArgumentException("Client was already added ");
        }
        if (mListeners.size() == 0) {
            try {
                mService.requestThemeChangeUpdates(mThemeChangeListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to register listener", e);
            }
        }
        mListeners.add(listener);
    }

    public void removeClient(ThemeChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            try {
                mService.removeUpdates(mThemeChangeListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to remove listener", e);
            }
        }
    }

    public void onClientPaused(ThemeChangeListener listener) {
        removeClient(listener);
    }

    public void onClientResumed(ThemeChangeListener listener) {
        addClient(listener);
    }

    public void onClientDestroyed(ThemeChangeListener listener) {
        removeClient(listener);
    }

    /**
     * Convenience method. Applies the entire theme.
     */
    public void requestThemeChange(String pkgName) {
        List<String> components = ThemeUtils.getSupportedComponents(mContext, pkgName);
        requestThemeChange(pkgName, components);
    }

    public void requestThemeChange(String pkgName, List<String> components) {
        Map<String, String> componentMap = new HashMap<String, String>(components.size());
        for (String component : components) {
            componentMap.put(component, pkgName);
        }
        requestThemeChange(componentMap);
    }

    public void requestThemeChange(Map<String, String> componentMap) {
        try {
            mService.requestThemeChange(componentMap);
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

    public boolean isThemeApplying() {
        try {
            return mService.isThemeApplying();
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to access ThemeService", e);
        }

        return false;
    }

    public int getProgress() {
        try {
            return mService.getProgress();
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
