/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.tablet;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.systemui.R;

public class HoloClock extends FrameLayout {
    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString;
    private SimpleDateFormat mClockFormat;

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;

    private static int AM_PM_STYLE = AM_PM_STYLE_GONE;

    private static final String FONT_DIR = "/system/fonts/";
    private static final String CLOCK_FONT = FONT_DIR + "AndroidClock_Solid.ttf";
    private static final String CLOCK_FG_FONT = FONT_DIR + "AndroidClock.ttf";
    private static final String CLOCK_BG_FONT = FONT_DIR + "AndroidClock_Highlight.ttf";

    private static Typeface sBackgroundType, sForegroundType, sSolidType;
    private TextView mSolidText, mBgText, mFgText;

    private int mAmPmStyle;
    private int mClockStyle;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_AM_PM), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_STYLE), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public HoloClock(Context context) {
        this(context, null);
    }

    public HoloClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HoloClock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        updateSettings();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (sSolidType == null) {
            sSolidType = Typeface.createFromFile(CLOCK_FONT);
            sBackgroundType = Typeface.createFromFile(CLOCK_BG_FONT);
            sForegroundType = Typeface.createFromFile(CLOCK_FG_FONT);
        }
        mBgText = (TextView) findViewById(R.id.time_bg);
        if (mBgText != null) {
            mBgText.setTypeface(sBackgroundType);
            mBgText.setVisibility(View.INVISIBLE);
        }

        mFgText = (TextView) findViewById(R.id.time_fg);
        if (mFgText != null) {
            mFgText.setTypeface(sForegroundType);
        }
        mSolidText = (TextView) findViewById(R.id.time_solid);
        if (mSolidText != null) {
            mSolidText.setTypeface(sSolidType);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        // Make sure we update to the current time
        updateClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            }
            updateClock();
        }
    };

    final void updateClock() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        CharSequence txt = getTimeText();
        if (mBgText != null) mBgText.setText(txt);
        if (mFgText != null) mFgText.setText(txt);
        if (mSolidText != null) mSolidText.setText(txt);
    }

    private final CharSequence getTimeText() {
        Context context = getContext();
        int res = DateFormat.is24HourFormat(context)
            ? com.android.internal.R.string.twenty_four_hour_time_format
            : com.android.internal.R.string.twelve_hour_time_format;

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = context.getString(res);
        if (!format.equals(mClockFormatString)) {
            /*
             * Search for an unquoted "a" in the format string, so we can
             * add dummy characters around it to let us find it again after
             * formatting and change its size.
             */
            if (AM_PM_STYLE != AM_PM_STYLE_NORMAL) {
                int a = -1;
                boolean quoted = false;
                for (int i = 0; i < format.length(); i++) {
                    char c = format.charAt(i);

                    if (c == '\'') {
                        quoted = !quoted;
                    }
                    if (!quoted && c == 'a') {
                        a = i;
                        break;
                    }
                }

                if (a >= 0) {
                    // Move a back so any whitespace before AM/PM is also in the alternate size.
                    final int b = a;
                    while (a > 0 && Character.isWhitespace(format.charAt(a-1))) {
                        a--;
                    }
                    format = format.substring(0, a) + MAGIC1 + format.substring(a, b)
                        + "a" + MAGIC2 + format.substring(b + 1);
                }
            }

            format = format.replaceAll("a", "").trim();
            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }
        String result = sdf.format(mCalendar.getTime());

        if (AM_PM_STYLE != AM_PM_STYLE_NORMAL) {
            int magic1 = result.indexOf(MAGIC1);
            int magic2 = result.indexOf(MAGIC2);
            if (magic1 >= 0 && magic2 > magic1) {
                SpannableStringBuilder formatted = new SpannableStringBuilder(result);
                if (AM_PM_STYLE == AM_PM_STYLE_GONE) {
                    formatted.delete(magic1, magic2+1);
                } else {
                    if (AM_PM_STYLE == AM_PM_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic1, magic2,
                                          Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic2, magic2 + 1);
                    formatted.delete(magic1, magic1 + 1);
                }
                return formatted;
            }
        }

        return result;
    }

    private void updateSettings(){
        ContentResolver resolver = mContext.getContentResolver();

        mAmPmStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_AM_PM, 2));

        if (mAmPmStyle != AM_PM_STYLE) {
            AM_PM_STYLE = mAmPmStyle;
            mClockFormatString = "";

            if (mAttached) {
                updateClock();
            }
        }

        mClockStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CLOCK_STYLE, 1));

        if (mClockStyle != 0) {
            setVisibility(View.VISIBLE);
        }
        else{
            setVisibility(View.GONE);
        }
    }
}

