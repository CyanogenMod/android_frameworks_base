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

import java.util.ArrayList;
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

    private Set<ThemeChangeListener> mChangeListeners =
            new HashSet<ThemeChangeListener>();

    private Set<ThemeProcessingListener> mProcessingListeners =
            new HashSet<ThemeProcessingListener>();

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
                    synchronized (mChangeListeners) {
                        List<ThemeChangeListener> listenersToRemove = new ArrayList
                                <ThemeChangeListener>();
                        for (ThemeChangeListener listener : mChangeListeners) {
                            try {
                                listener.onProgress(progress);
                            } catch (Throwable e) {
                                Log.w(TAG, "Unable to update theme change progress", e);
                                listenersToRemove.add(listener);
                            }
                        }
                        if (listenersToRemove.size() > 0) {
                            for (ThemeChangeListener listener : listenersToRemove) {
                                mChangeListeners.remove(listener);
                            }
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
                    synchronized (mChangeListeners) {
                        List<ThemeChangeListener> listenersToRemove = new ArrayList
                                <ThemeChangeListener>();
                        for (ThemeChangeListener listener : mChangeListeners) {
                            try {
                                listener.onFinish(isSuccess);
                            } catch (Throwable e) {
                                Log.w(TAG, "Unable to update theme change listener", e);
                                listenersToRemove.add(listener);
                            }
                        }
                        if (listenersToRemove.size() > 0) {
                            for (ThemeChangeListener listener : listenersToRemove) {
                                mChangeListeners.remove(listener);
                            }
                        }
                    }
                }
            });
        }
    };

    private final IThemeProcessingListener mThemeProcessingListener =
            new IThemeProcessingListener.Stub() {
        @Override
        public void onFinishedProcessing(final String pkgName) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mProcessingListeners) {
                        List<ThemeProcessingListener> listenersToRemove = new ArrayList
                                <ThemeProcessingListener>();
                        for (ThemeProcessingListener listener : mProcessingListeners) {
                            try {
                                listener.onFinishedProcessing(pkgName);
                            } catch (Throwable e) {
                                Log.w(TAG, "Unable to update theme change progress", e);
                                listenersToRemove.add(listener);
                            }
                        }
                        if (listenersToRemove.size() > 0) {
                            for (ThemeProcessingListener listener : listenersToRemove) {
                                mProcessingListeners.remove(listener);
                            }
                        }
                    }
                }
            });
        }
    };


    public void addClient(ThemeChangeListener listener) {
        synchronized (mChangeListeners) {
            if (mChangeListeners.contains(listener)) {
                throw new IllegalArgumentException("Client was already added ");
            }
            if (mChangeListeners.size() == 0) {
                try {
                    mService.requestThemeChangeUpdates(mThemeChangeListener);
                } catch (RemoteException e) {
                    Log.w(TAG, "Unable to register listener", e);
                }
            }
            mChangeListeners.add(listener);
        }
    }

    public void removeClient(ThemeChangeListener listener) {
        synchronized (mChangeListeners) {
            mChangeListeners.remove(listener);
            if (mChangeListeners.size() == 0) {
                try {
                    mService.removeUpdates(mThemeChangeListener);
                } catch (RemoteException e) {
                    Log.w(TAG, "Unable to remove listener", e);
                }
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
     * Register a ThemeProcessingListener to be notified when a theme is done being processed.
     * @param listener ThemeChangeListener to register
     */
    public void registerProcessingListener(ThemeProcessingListener listener) {
        synchronized (mProcessingListeners) {
            if (mProcessingListeners.contains(listener)) {
                throw new IllegalArgumentException("Listener was already added ");
            }
            if (mProcessingListeners.size() == 0) {
                try {
                    mService.registerThemeProcessingListener(mThemeProcessingListener);
                } catch (RemoteException e) {
                    Log.w(TAG, "Unable to register listener", e);
                }
            }
            mProcessingListeners.add(listener);
        }
    }

    /**
     * Unregister a ThemeChangeListener.
     * @param listener ThemeChangeListener to unregister
     */
    public void unregisterProcessingListener(ThemeChangeListener listener) {
        synchronized (mProcessingListeners) {
            mProcessingListeners.remove(listener);
            if (mProcessingListeners.size() == 0) {
                try {
                    mService.unregisterThemeProcessingListener(mThemeProcessingListener);
                } catch (RemoteException e) {
                    Log.w(TAG, "Unable to remove listener", e);
                }
            }
        }
    }

    /**
     * Convenience method. Applies the entire theme.
     */
    public void requestThemeChange(String pkgName) {
        //List<String> components = ThemeUtils.getSupportedComponents(mContext, pkgName);
        //requestThemeChange(pkgName, components);
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
            logThemeServiceException(e);
        }
    }

    public void applyDefaultTheme() {
        try {
            mService.applyDefaultTheme();
        } catch (RemoteException e) {
            logThemeServiceException(e);
        }
    }

    public boolean isThemeApplying() {
        try {
            return mService.isThemeApplying();
        } catch (RemoteException e) {
            logThemeServiceException(e);
        }

        return false;
    }

    public boolean isThemeBeingProcessed(String themePkgName) {
        try {
            return mService.isThemeBeingProcessed(themePkgName);
        } catch (RemoteException e) {
            logThemeServiceException(e);
        }
        return false;
    }

    public int getProgress() {
        try {
            return mService.getProgress();
        } catch (RemoteException e) {
            logThemeServiceException(e);
        }
        return -1;
    }

    public boolean processThemeResources(String themePkgName) {
        try {
            return mService.processThemeResources(themePkgName);
        } catch (RemoteException e) {
            logThemeServiceException(e);
        }
        return false;
    }

    private void logThemeServiceException(Exception e) {
        Log.w(TAG, "Unable to access ThemeService", e);
    }

    public interface ThemeChangeListener {
        void onProgress(int progress);
        void onFinish(boolean isSuccess);
    }

    public interface ThemeProcessingListener {
        void onFinishedProcessing(String pkgName);
    }
}

