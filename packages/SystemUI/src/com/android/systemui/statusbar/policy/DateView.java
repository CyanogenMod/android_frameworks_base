/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import static com.android.internal.util.cm.NotificationActionConstants.*;
import com.android.systemui.cm.ActionTarget;

import java.util.Date;

public class DateView extends TextView implements OnClickListener, OnLongClickListener {
    private static final String TAG = "DateView";

    private RelativeLayout mParent;
    private ActionTarget mActionTarget;

    private boolean mAttachedToWindow;
    private boolean mWindowVisible;
    private boolean mUpdating;
    private String[] mDateActions = new String[2];

    private Handler mHandler;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateClock();
            }
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_DATE_ACTIONS[SHORT_CLICK]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_DATE_ACTIONS[LONG_CLICK]), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public DateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActionTarget = new ActionTarget(context);
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        setOnClickListener(this);
        setOnLongClickListener(this);
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        setUpdates();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttachedToWindow = false;
        if (mParent != null) {
            mParent.setOnClickListener(null);
            mParent.setOnLongClickListener(null);
            mParent = null;
        }
        setUpdates();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mParent == null) {
            mParent = (RelativeLayout) getParent();
            mParent.setOnClickListener(this);
            mParent.setOnLongClickListener(this);
        }

        super.onDraw(canvas);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        setUpdates();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        setUpdates();
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        // makes the large background bitmap not force us to full width
        return 0;
    }

    protected void updateClock() {
        final String dateFormat = getContext().getString(R.string.full_wday_month_day_no_year_split);
        setText(DateFormat.format(dateFormat, new Date()));
    }

    private boolean isVisible() {
        View v = this;
        while (true) {
            if (v.getVisibility() != VISIBLE) {
                return false;
            }
            final ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View)parent;
            } else {
                return true;
            }
        }
    }

    private void setUpdates() {
        boolean update = mAttachedToWindow && mWindowVisible && isVisible();
        if (update != mUpdating) {
            mUpdating = update;
            if (update) {
                // Register for Intent broadcasts for the clock and battery
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_TIME_TICK);
                filter.addAction(Intent.ACTION_TIME_CHANGED);
                filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                mContext.registerReceiver(mIntentReceiver, filter, null, null);
                updateClock();
            } else {
                mContext.unregisterReceiver(mIntentReceiver);
            }
        }
    }

    private void updateSettings(){
        ContentResolver resolver = mContext.getContentResolver();

        mDateActions[SHORT_CLICK] = Settings.System.getString(resolver, Settings.System.NOTIFICATION_DATE_ACTIONS[SHORT_CLICK]);
        mDateActions[LONG_CLICK] = Settings.System.getString(resolver, Settings.System.NOTIFICATION_DATE_ACTIONS[LONG_CLICK]);

        if (TextUtils.isEmpty(mDateActions[SHORT_CLICK])) {
            mDateActions[SHORT_CLICK] = ACTION_TODAY;
        }
        if (TextUtils.isEmpty(mDateActions[LONG_CLICK])) {
            mDateActions[LONG_CLICK] = ACTION_CLOCK_SETTINGS;
        }
    }

    @Override
    public void onClick(View v) {
        StatusBarManager statusBarManager = (StatusBarManager) mContext.getSystemService(
                Context.STATUS_BAR_SERVICE);
        statusBarManager.collapsePanels();
        mActionTarget.launchAction(mDateActions[SHORT_CLICK]);
    }

    @Override
    public boolean onLongClick(View v) {
        StatusBarManager statusBarManager = (StatusBarManager) mContext.getSystemService(
                Context.STATUS_BAR_SERVICE);
        statusBarManager.collapsePanels();
        mActionTarget.launchAction(mDateActions[LONG_CLICK]);
        return true;
    }
}
