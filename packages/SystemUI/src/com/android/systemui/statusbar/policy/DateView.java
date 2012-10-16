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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils.TruncateAt;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.util.Date;

public final class DateView extends LinearLayout implements OnClickListener, OnTouchListener {
    private static final String TAG = "DateView";

    private TextView mDoW;
    private TextView mDate;

    private boolean mAttachedToWindow;
    private boolean mWindowVisible;
    private boolean mUpdating;
    private int mDefaultColor;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                updateClock();
            }
        }
    };

    public DateView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Layout params
        setOrientation(LinearLayout.VERTICAL);

        // Create the text views
        mDoW = new TextView(context, attrs);
        mDoW.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
        mDoW.setSingleLine();
        mDoW.setEllipsize(TruncateAt.END);
        mDoW.setTextAppearance(context, R.style.TextAppearance_StatusBar_Expanded_Date);
        mDoW.setIncludeFontPadding(false);
        mDate = new TextView(context, attrs);
        mDate.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
        mDate.setSingleLine();
        mDate.setEllipsize(TruncateAt.END);
        mDate.setTextAppearance(context, R.style.TextAppearance_StatusBar_Expanded_Date);
        mDate.setIncludeFontPadding(false);
        setOnClickListener(this);
        setOnTouchListener(this);

        mDefaultColor = mDate.getCurrentTextColor();

        // Extract how DoW and Date are distributed in the layout
        // The format is distributed as %1$s\n%2$s or %2$s\n%1$s but always in
        // two lines. Otherwise assume DoW = Top and Date = Bottom
        int positionDoW = 0;
        int positionDate = 1;
        try {
            String format = context.getString(R.string.status_bar_date_formatter);
            String[] positions = format.split("\n");
            if (positions.length == 2) {
                positionDoW = positions[0].indexOf("1") != -1 ? 0 : 1;
                positionDate = positions[1].indexOf("2") != -1 ? 1 : 0;
            }
        } catch (Exception ex) {
            Slog.w(TAG, "Error extracting DoW and Date positions", ex);
        }

        // Add the TextViews
        addView(positionDoW == 0 ? mDoW : mDate);
        addView(positionDate != 0 ? mDate : mDoW);
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
        setUpdates();
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

    private final void updateClock() {
        final Context context = getContext();
        Date now = new Date();
        CharSequence dow = DateFormat.format("EEEE", now);
        CharSequence date = DateFormat.getLongDateFormat(context).format(now);
        mDoW.setText(dow);
        mDate.setText(date);
    }

    public final TextView getDoW() {
        return mDoW;
    }

    public final TextView getDate() {
        return mDate;
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
                filter.addAction(Intent.ACTION_LOCALE_CHANGED);
                mContext.registerReceiver(mIntentReceiver, filter, null, null);
                updateClock();
            } else {
                mContext.unregisterReceiver(mIntentReceiver);
            }
        }
    }

    @Override
    public void onClick(View v) {
        mDate.setTextColor(mDefaultColor);
        mDoW.setTextColor(mDefaultColor);

        // collapse status bar
        StatusBarManager statusBarManager = (StatusBarManager) getContext().getSystemService(
                Context.STATUS_BAR_SERVICE);
        statusBarManager.collapse();

        // dismiss keyguard in case it was active and no passcode set
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (Exception ex) {
            // no action needed here
        }

        // start calendar - today is selected
        long nowMillis = System.currentTimeMillis();

        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, nowMillis);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(builder.build());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int a = event.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            int cTouch = getResources().getColor(com.android.internal.R.color.holo_blue_light);
            mDate.setTextColor(cTouch);
            mDoW.setTextColor(cTouch);
        } else if (a == MotionEvent.ACTION_CANCEL || a == MotionEvent.ACTION_UP) {
            mDate.setTextColor(mDefaultColor);
            mDoW.setTextColor(mDefaultColor);
        }
        // never consume touch event, so onClick is propperly processed
        return false;
    }
}
