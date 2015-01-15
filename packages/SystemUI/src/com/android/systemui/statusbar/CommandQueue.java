/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.android.internal.statusbar.IStatusBar;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;

/**
 * This class takes the functions from IStatusBar that come in on
 * binder pool threads and posts messages to get them onto the main
 * thread, and calls onto Callbacks.  It also takes care of
 * coalescing these calls so they don't stack up.  For the calls
 * are coalesced, note that they are all idempotent.
 */
public class CommandQueue extends IStatusBar.Stub {
    private static final int INDEX_MASK = 0xffff;
    private static final int MSG_SHIFT  = 16;
    private static final int MSG_MASK   = 0xffff << MSG_SHIFT;

    private static final int OP_SET_ICON    = 1;
    private static final int OP_REMOVE_ICON = 2;

    private static final int MSG_ICON                       = 1 << MSG_SHIFT;
    private static final int MSG_DISABLE                    = 2 << MSG_SHIFT;
    private static final int MSG_EXPAND_NOTIFICATIONS       = 3 << MSG_SHIFT;
    private static final int MSG_COLLAPSE_PANELS            = 4 << MSG_SHIFT;
    private static final int MSG_EXPAND_SETTINGS            = 5 << MSG_SHIFT;
    private static final int MSG_SET_SYSTEMUI_VISIBILITY    = 6 << MSG_SHIFT;
    private static final int MSG_TOP_APP_WINDOW_CHANGED     = 7 << MSG_SHIFT;
    private static final int MSG_SHOW_IME_BUTTON            = 8 << MSG_SHIFT;
    private static final int MSG_TOGGLE_RECENT_APPS         = 9 << MSG_SHIFT;
    private static final int MSG_PRELOAD_RECENT_APPS        = 10 << MSG_SHIFT;
    private static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 11 << MSG_SHIFT;
    private static final int MSG_SET_WINDOW_STATE           = 12 << MSG_SHIFT;
    private static final int MSG_SHOW_RECENT_APPS           = 13 << MSG_SHIFT;
    private static final int MSG_HIDE_RECENT_APPS           = 14 << MSG_SHIFT;
    private static final int MSG_BUZZ_BEEP_BLINKED          = 15 << MSG_SHIFT;
    private static final int MSG_NOTIFICATION_LIGHT_OFF     = 16 << MSG_SHIFT;
    private static final int MSG_NOTIFICATION_LIGHT_PULSE   = 17 << MSG_SHIFT;

    public static final int FLAG_EXCLUDE_NONE = 0;
    public static final int FLAG_EXCLUDE_SEARCH_PANEL = 1 << 0;
    public static final int FLAG_EXCLUDE_RECENTS_PANEL = 1 << 1;
    public static final int FLAG_EXCLUDE_NOTIFICATION_PANEL = 1 << 2;
    public static final int FLAG_EXCLUDE_INPUT_METHODS_PANEL = 1 << 3;
    public static final int FLAG_EXCLUDE_COMPAT_MODE_PANEL = 1 << 4;

    private static final String SHOW_IME_SWITCHER_KEY = "showImeSwitcherKey";

    private StatusBarIconList mList;
    private Callbacks mCallbacks;
    private Handler mHandler = new H();
    private boolean mPaused = false;

    /**
     * These methods are called back on the main thread.
     */
    public interface Callbacks {
        public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon);
        public void updateIcon(String slot, int index, int viewIndex,
                StatusBarIcon old, StatusBarIcon icon);
        public void removeIcon(String slot, int index, int viewIndex);
        public void disable(int state, boolean animate);
        public void animateExpandNotificationsPanel();
        public void animateCollapsePanels(int flags);
        public void animateExpandSettingsPanel();
        public void setSystemUiVisibility(int vis, int mask);
        public void topAppWindowChanged(boolean visible);
        public void setImeWindowStatus(IBinder token, int vis, int backDisposition,
                boolean showImeSwitcher);
        public void showRecentApps(boolean triggeredFromAltTab);
        public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey);
        public void toggleRecentApps();
        public void preloadRecentApps();
        public void cancelPreloadRecentApps();
        public void showSearchPanel();
        public void hideSearchPanel();
        public void setWindowState(int window, int state);
        public void buzzBeepBlinked();
        public void notificationLightOff();
        public void notificationLightPulse(int argb, int onMillis, int offMillis);
    }

    public CommandQueue(Callbacks callbacks, StatusBarIconList list) {
        mCallbacks = callbacks;
        mList = list;
    }

    public void setIcon(int index, StatusBarIcon icon) {
        synchronized (mList) {
            int what = MSG_ICON | index;
            mHandler.removeMessages(what);
            mHandler.obtainMessage(what, OP_SET_ICON, 0, icon.clone()).sendToTarget();
        }
    }

    public void removeIcon(int index) {
        synchronized (mList) {
            int what = MSG_ICON | index;
            mHandler.removeMessages(what);
            mHandler.obtainMessage(what, OP_REMOVE_ICON, 0, null).sendToTarget();
        }
    }

    public void disable(int state) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_DISABLE);
            mHandler.obtainMessage(MSG_DISABLE, state, 0, null).sendToTarget();
        }
    }

    public void animateExpandNotificationsPanel() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_EXPAND_NOTIFICATIONS);
            mHandler.sendEmptyMessage(MSG_EXPAND_NOTIFICATIONS);
        }
    }

    public void animateCollapsePanels() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_COLLAPSE_PANELS);
            mHandler.sendEmptyMessage(MSG_COLLAPSE_PANELS);
        }
    }

    public void animateExpandSettingsPanel() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_EXPAND_SETTINGS);
            mHandler.sendEmptyMessage(MSG_EXPAND_SETTINGS);
        }
    }

    public void setSystemUiVisibility(int vis, int mask) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_SET_SYSTEMUI_VISIBILITY);
            mHandler.obtainMessage(MSG_SET_SYSTEMUI_VISIBILITY, vis, mask, null).sendToTarget();
        }
    }

    public void topAppWindowChanged(boolean menuVisible) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_TOP_APP_WINDOW_CHANGED);
            mHandler.obtainMessage(MSG_TOP_APP_WINDOW_CHANGED, menuVisible ? 1 : 0, 0,
                    null).sendToTarget();
        }
    }

    public void setImeWindowStatus(IBinder token, int vis, int backDisposition,
            boolean showImeSwitcher) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_SHOW_IME_BUTTON);
            Message m = mHandler.obtainMessage(MSG_SHOW_IME_BUTTON, vis, backDisposition, token);
            m.getData().putBoolean(SHOW_IME_SWITCHER_KEY, showImeSwitcher);
            m.sendToTarget();
        }
    }

    public void showRecentApps(boolean triggeredFromAltTab) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_SHOW_RECENT_APPS);
            mHandler.obtainMessage(MSG_SHOW_RECENT_APPS,
                    triggeredFromAltTab ? 1 : 0, 0, null).sendToTarget();
        }
    }

    public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        synchronized (mList) {
            mHandler.removeMessages(MSG_HIDE_RECENT_APPS);
            mHandler.obtainMessage(MSG_HIDE_RECENT_APPS,
                    triggeredFromAltTab ? 1 : 0, triggeredFromHomeKey ? 1 : 0,
                    null).sendToTarget();
        }
    }

    public void toggleRecentApps() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_TOGGLE_RECENT_APPS);
            mHandler.obtainMessage(MSG_TOGGLE_RECENT_APPS, 0, 0, null).sendToTarget();
        }
    }

    public void preloadRecentApps() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_PRELOAD_RECENT_APPS);
            mHandler.obtainMessage(MSG_PRELOAD_RECENT_APPS, 0, 0, null).sendToTarget();
        }
    }

    public void cancelPreloadRecentApps() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_CANCEL_PRELOAD_RECENT_APPS);
            mHandler.obtainMessage(MSG_CANCEL_PRELOAD_RECENT_APPS, 0, 0, null).sendToTarget();
        }
    }

    public void setWindowState(int window, int state) {
        synchronized (mList) {
            // don't coalesce these
            mHandler.obtainMessage(MSG_SET_WINDOW_STATE, window, state, null).sendToTarget();
        }
    }

    public void buzzBeepBlinked() {
        synchronized (mList) {
            mHandler.removeMessages(MSG_BUZZ_BEEP_BLINKED);
            mHandler.sendEmptyMessage(MSG_BUZZ_BEEP_BLINKED);
        }
    }

    public void notificationLightOff() {
        synchronized (mList) {
            mHandler.sendEmptyMessage(MSG_NOTIFICATION_LIGHT_OFF);
        }
    }

    public void notificationLightPulse(int argb, int onMillis, int offMillis) {
        synchronized (mList) {
            mHandler.obtainMessage(MSG_NOTIFICATION_LIGHT_PULSE, onMillis, offMillis, argb)
                    .sendToTarget();
        }
    }

    public void pause() {
        mPaused = true;
    }

    public void resume() {
        mPaused = false;
    }

    private final class H extends Handler {
        public void handleMessage(Message msg) {
            if (mPaused) {
                this.sendMessageAtFrontOfQueue(Message.obtain(msg));
                return;
            }
            final int what = msg.what & MSG_MASK;
            switch (what) {
                case MSG_ICON: {
                    final int index = msg.what & INDEX_MASK;
                    final int viewIndex = mList.getViewIndex(index);
                    switch (msg.arg1) {
                        case OP_SET_ICON: {
                            StatusBarIcon icon = (StatusBarIcon)msg.obj;
                            StatusBarIcon old = mList.getIcon(index);
                            if (old == null) {
                                mList.setIcon(index, icon);
                                mCallbacks.addIcon(mList.getSlot(index), index, viewIndex, icon);
                            } else {
                                mList.setIcon(index, icon);
                                mCallbacks.updateIcon(mList.getSlot(index), index, viewIndex,
                                        old, icon);
                            }
                            break;
                        }
                        case OP_REMOVE_ICON:
                            if (mList.getIcon(index) != null) {
                                mList.removeIcon(index);
                                mCallbacks.removeIcon(mList.getSlot(index), index, viewIndex);
                            }
                            break;
                    }
                    break;
                }
                case MSG_DISABLE:
                    mCallbacks.disable(msg.arg1, true /* animate */);
                    break;
                case MSG_EXPAND_NOTIFICATIONS:
                    mCallbacks.animateExpandNotificationsPanel();
                    break;
                case MSG_COLLAPSE_PANELS:
                    mCallbacks.animateCollapsePanels(0);
                    break;
                case MSG_EXPAND_SETTINGS:
                    mCallbacks.animateExpandSettingsPanel();
                    break;
                case MSG_SET_SYSTEMUI_VISIBILITY:
                    mCallbacks.setSystemUiVisibility(msg.arg1, msg.arg2);
                    break;
                case MSG_TOP_APP_WINDOW_CHANGED:
                    mCallbacks.topAppWindowChanged(msg.arg1 != 0);
                    break;
                case MSG_SHOW_IME_BUTTON:
                    mCallbacks.setImeWindowStatus((IBinder) msg.obj, msg.arg1, msg.arg2,
                            msg.getData().getBoolean(SHOW_IME_SWITCHER_KEY, false));
                    break;
                case MSG_SHOW_RECENT_APPS:
                    mCallbacks.showRecentApps(msg.arg1 != 0);
                    break;
                case MSG_HIDE_RECENT_APPS:
                    mCallbacks.hideRecentApps(msg.arg1 != 0, msg.arg2 != 0);
                    break;
                case MSG_TOGGLE_RECENT_APPS:
                    mCallbacks.toggleRecentApps();
                    break;
                case MSG_PRELOAD_RECENT_APPS:
                    mCallbacks.preloadRecentApps();
                    break;
                case MSG_CANCEL_PRELOAD_RECENT_APPS:
                    mCallbacks.cancelPreloadRecentApps();
                    break;
                case MSG_SET_WINDOW_STATE:
                    mCallbacks.setWindowState(msg.arg1, msg.arg2);
                    break;
                case MSG_BUZZ_BEEP_BLINKED:
                    mCallbacks.buzzBeepBlinked();
                    break;
                case MSG_NOTIFICATION_LIGHT_OFF:
                    mCallbacks.notificationLightOff();
                    break;
                case MSG_NOTIFICATION_LIGHT_PULSE:
                    mCallbacks.notificationLightPulse((Integer) msg.obj, msg.arg1, msg.arg2);
                    break;
            }
        }
    }
}

