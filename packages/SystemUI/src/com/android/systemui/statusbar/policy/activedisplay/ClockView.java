/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.policy.activedisplay;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.systemui.R;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import libcore.icu.ICU;

/**
 * Displays the time
 */
public class ClockView extends RelativeLayout {
    private final static String M12 = "h:mm";
    private final static String M24 = "HH:mm";

    private Calendar mCalendar;
    private String mFormat;
    private TextView mTimeView;
    private TextView mDateView;
    private AmPm mAmPm;
    private SettingsObserver mSettingsObserver;
    private ContentObserver mFormatChangeObserver;
    private int mAttached = 0; // for debugging - tells us whether attach/detach is unbalanced

    /* called by system on minute ticks */
    private final Handler mHandler = new Handler();
    private BroadcastReceiver mIntentReceiver;

    private static class TimeChangedReceiver extends BroadcastReceiver {
        private WeakReference<ClockView> mClock;
        private Context mContext;

        public TimeChangedReceiver(ClockView clock) {
            mClock = new WeakReference<ClockView>(clock);
            mContext = clock.getContext();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Post a runnable to avoid blocking the broadcast.
            final boolean timezoneChanged =
                    intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED);
            final ClockView clock = mClock.get();
            if (clock != null) {
                clock.mHandler.post(new Runnable() {
                    public void run() {
                        if (timezoneChanged) {
                            clock.mCalendar = Calendar.getInstance();
                        }
                        clock.updateTime();
                    }
                });
            } else {
                try {
                    mContext.unregisterReceiver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    };

    static class AmPm {
        private TextView mAmPmTextView;
        private String mAmString, mPmString;

        AmPm(View parent, Typeface tf) {
            // No longer used, uncomment if we decide to use AM/PM indicator again
            mAmPmTextView = (TextView) parent.findViewById(R.id.am_pm);
            if (mAmPmTextView != null && tf != null) {
                mAmPmTextView.setTypeface(tf);
            }

            String[] ampm = new DateFormatSymbols().getAmPmStrings();
            mAmString = ampm[0];
            mPmString = ampm[1];
        }

        void setShowAmPm(boolean show) {
            if (mAmPmTextView != null) {
                mAmPmTextView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }

        void setIsMorning(boolean isMorning) {
            if (mAmPmTextView != null) {
                mAmPmTextView.setText(isMorning ? mAmString : mPmString);
            }
        }
    }

    private static class FormatChangeObserver extends ContentObserver {
        private WeakReference<ClockView> mClock;
        private Context mContext;
        public FormatChangeObserver(ClockView clock) {
            super(new Handler());
            mClock = new WeakReference<ClockView>(clock);
            mContext = clock.getContext();
        }
        @Override
        public void onChange(boolean selfChange) {
            ClockView digitalClock = mClock.get();
            if (digitalClock != null) {
                digitalClock.setDateFormat();
                digitalClock.updateTime();
            } else {
                try {
                    mContext.getContentResolver().unregisterContentObserver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    }

    /**
     * Class used to listen for changes to active display date/time settings
     */
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver =
                    ClockView.this.mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHOW_AMPM), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_SHOW_DATE), false, this);
            update();
        }

        void unobserve() {
            ClockView.this.mContext.getContentResolver()
                    .unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver =
                    ClockView.this.mContext.getContentResolver();

            boolean showAmPm = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHOW_AMPM, 0) == 1;
            boolean showDate = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_DISPLAY_SHOW_DATE, 0) == 1;

            mAmPm.setShowAmPm(showAmPm);
            mDateView.setVisibility(showDate ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public ClockView(Context context) {
        this(context, null);
    }

    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTimeView = (TextView) findViewById(R.id.clock_text);
        mDateView = (TextView) findViewById(R.id.date);
        mAmPm = new AmPm(this, null);
        mCalendar = Calendar.getInstance();
        setDateFormat();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mAttached++;
        /* monitor time ticks, time changed, timezone */
        if (mIntentReceiver == null) {
            mIntentReceiver = new TimeChangedReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            mContext.registerReceiverAsUser(mIntentReceiver, UserHandle.OWNER, filter, null, null );
        }
        /* monitor 12/24-hour display preference */
        if (mFormatChangeObserver == null) {
            mFormatChangeObserver = new FormatChangeObserver(this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, mFormatChangeObserver);
        }
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }

        updateTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttached--;
        if (mIntentReceiver != null) {
            mContext.unregisterReceiver(mIntentReceiver);
        }
        if (mFormatChangeObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(
                    mFormatChangeObserver);
        }
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
        mSettingsObserver = null;
        mFormatChangeObserver = null;
        mIntentReceiver = null;
    }

    void updateTime(Calendar c) {
        mCalendar = c;
        updateTime();
    }

    public void updateTime() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        String newTime = DateFormat.format(mFormat, mCalendar).toString();
        SpannableString span = new SpannableString(newTime);
        int colonIndex = newTime.indexOf(':');
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, colonIndex, 0);
        span.setSpan(new TypefaceSpan("sans-serif-thin"), colonIndex + 1, newTime.length(), 0);
        mTimeView.setText(span);
        mAmPm.setIsMorning(mCalendar.get(Calendar.AM_PM) == 0);
        final String dateFormat = getContext().getString(R.string.ad_date_pattern);
        final Locale l = Locale.getDefault();
        String fmt = ICU.getBestDateTimePattern(dateFormat, l.toString());
        SimpleDateFormat sdf = new SimpleDateFormat(fmt, l);
        mDateView.setText(sdf.format(new Date()));
    }

    private void setDateFormat() {
        mFormat = android.text.format.DateFormat.is24HourFormat(getContext()) ? M24 : M12;
        mAmPm.setShowAmPm(mFormat.equals(M12));
    }
}