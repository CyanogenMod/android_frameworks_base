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

import android.app.ActivityManager;
import android.app.Service;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Slog;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerImpl;

import java.util.ArrayList;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
import com.android.internal.statusbar.StatusBarNotification;

import com.android.systemui.SystemUI;
import com.android.systemui.R;

public abstract class StatusBar extends SystemUI implements CommandQueue.Callbacks {
    static final String TAG = "StatusBar";
    private static final boolean SPEW = false;

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;

    // Up-call methods
    protected abstract View makeStatusBarView();
    protected abstract int getStatusBarGravity();
    public abstract int getStatusBarHeight();
    public abstract void animateCollapse();

    private View mStatusBar;
    private DoNotDisturb mDoNotDisturb;

    private boolean mShowNotificationCounts;
    private static int mOpacity;
    private Handler mHandler;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TRANSPARENCY), false, this);
            setStatusBarParams();
        }

        @Override public void onChange(boolean selfChange) {
            setStatusBarParams();
        }
    }


    public void start() {
        // First set up our views and stuff.
        mStatusBar = makeStatusBarView();

        mStatusBarContainer.addView(mStatusBar);

        mShowNotificationCounts = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_NOTIF_COUNT, 0) == 1;

        // Connect in to the status bar manager service
        StatusBarIconList iconList = new StatusBarIconList();
        ArrayList<IBinder> notificationKeys = new ArrayList<IBinder>();
        ArrayList<StatusBarNotification> notifications = new ArrayList<StatusBarNotification>();
        mCommandQueue = new CommandQueue(this, iconList);
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        int[] switches = new int[7];
        ArrayList<IBinder> binders = new ArrayList<IBinder>();
        try {
            mBarService.registerStatusBar(mCommandQueue, iconList, notificationKeys, notifications,
                    switches, binders);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

        disable(switches[0]);
        setSystemUiVisibility(switches[1]);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(binders.get(0), switches[3], switches[4]);
        setHardKeyboardStatus(switches[5] != 0, switches[6] != 0);

        // Set up the initial icon state
        int N = iconList.size();
        int viewIndex = 0;
        for (int i=0; i<N; i++) {
            StatusBarIcon icon = iconList.getIcon(i);
            if (icon != null) {
                addIcon(iconList.getSlot(i), i, viewIndex, icon);
                viewIndex++;
            }
        }

        // Set up the initial notification state
        N = notificationKeys.size();
        if (N == notifications.size()) {
            for (int i=0; i<N; i++) {
                addNotification(notificationKeys.get(i), notifications.get(i));
            }
        } else {
            Log.wtf(TAG, "Notification list length mismatch: keys=" + N
                    + " notifications=" + notifications.size());
        }

        // Put up the view
        final int height = getStatusBarHeight();

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                // We use a pixel format of RGBA_4444 (instead of TRANSPARENT) for the status bar
                // to save memory bandwidth and to ensure that the layer can be handled
                // by HWComposer.  On some devices the HWComposer is unable to handle SW-rendered 
                // TRANSPARENT layers.
                // TODO Fix this
                PixelFormat.RGBA_4444);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        
        // the status bar should be in an overlay if possible
        final Display defaultDisplay 
            = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();

        // We explicitly leave FLAG_HARDWARE_ACCELERATED out of the flags.  The status bar occupies
        // very little screen real-estate and is updated fairly frequently.  By using CPU rendering
        // for the status bar, we prevent the GPU from having to wake up just to do these small
        // updates, which should help keep power consumption down.

        lp.gravity = getStatusBarGravity();
        lp.setTitle("StatusBar");
        lp.packageName = mContext.getPackageName();
        lp.windowAnimations = R.style.Animation_StatusBar;
        WindowManagerImpl.getDefault().addView(mStatusBarContainer, lp);

        if (SPEW) {
            Slog.d(TAG, "Added status bar view: gravity=0x" + Integer.toHexString(lp.gravity) 
                   + " icons=" + iconList.size()
                   + " disabled=0x" + Integer.toHexString(switches[0])
                   + " lights=" + switches[1]
                   + " menu=" + switches[2]
                   + " imeButton=" + switches[3]
                   );
        }

        mDoNotDisturb = new DoNotDisturb(mContext);
    }

    private void setStatusBarParams(){
        mOpacity = Settings.System.getInt(mStatusBar.getContext().getContentResolver(), Settings.System.STATUS_BAR_TRANSPARENCY, 100);
        mStatusBar.setBackgroundColor((int) (((float) mOpacity / 100.0f) * 255) * 0x1000000);
    }

    protected View updateNotificationVetoButton(View row, StatusBarNotification n) {
        View vetoButton = row.findViewById(R.id.veto);
        if (n.isClearable()) {
            final String _pkg = n.pkg;
            final String _tag = n.tag;
            final int _id = n.id;
            vetoButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            mBarService.onNotificationClear(_pkg, _tag, _id);
                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                });
            vetoButton.setVisibility(View.VISIBLE);
        } else {
            vetoButton.setVisibility(View.GONE);
        }
        return vetoButton;
    }
}
