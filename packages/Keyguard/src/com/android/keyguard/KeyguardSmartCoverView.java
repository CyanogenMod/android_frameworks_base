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

package com.android.keyguard;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class KeyguardSmartCoverView extends LinearLayout {
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static final String  TAG   = "KeyguardStatusView";

    private LockPatternUtils mLockPatternUtils;

    private TextView  mAlarmStatusView;
    private TextClock mDateView;
    private TextClock mClockView;
    private TextView  mAmPm;
    private TextView  mWeatherImage, mWeatherStatus;
    private TextView mLine1, mLine2, mLine3;
    private ImageView mBatteryImage;

    private int mMissedCalls, mUnreadMessages;
    // On the first boot, keygard will start to receiver TIME_TICK intent.
    // And onScreenTurnedOff will not get called if power off when keyguard is
    // not started.
    // Set initial value to false to skip the above case.
    private boolean mEnableRefresh = false;
    private boolean mFadeInWeather = false;

    private TextView                            mBatteryStatusView;
    private KeyguardUpdateMonitor.BatteryStatus mBatteryStatus;

    // sysui flags to apply when showing the cover
    public static final int SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

    // how long to wait before sending the screen to sleep
    public static final int SMART_COVER_TIMEOUT = 8000;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            if (mEnableRefresh) {
                refresh();
            }
        }

        @Override
        public void onScreenTurnedOn() {
            setEnableMarquee(true);
            mEnableRefresh = true;
            refresh();
        }

        @Override
        public void onScreenTurnedOff(int why) {
            setEnableMarquee(false);
            mEnableRefresh = false;
        }

        @Override void onRefreshBatteryInfo(KeyguardUpdateMonitor.BatteryStatus status) {
            mBatteryStatus = status;
            if (mEnableRefresh) {
                refreshBatteryStatus();
            }
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED".equals(intent
                    .getAction())) {
                if (!intent.getBooleanExtra("update_cancelled", true)) {
                    refreshWeatherStatus();
                }
            } else {
                refresh();
            }
        }
    };

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri == null) {
                refresh();
            } else if (uri.equals(Calls.CONTENT_URI)) {
                refreshStatusLines();

                // after a new call has come in these flags are reset
                setSystemUiVisibility(getSystemUiVisibility() | SYSTEM_UI_FLAGS);
            } else if (uri.equals(Uri.parse("content://sms/inbox"))) {
                refreshStatusLines();
            }
        }
    };

    public KeyguardSmartCoverView(Context context) {
        this(context, null, 0);
    }

    public KeyguardSmartCoverView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardSmartCoverView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setBackgroundColor(0xFF000000);
        mContext.sendBroadcast(
                new Intent("com.cyanogenmod.lockclock.action.REQUEST_WEATHER_UPDATE"));
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) { Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee"); }
        if (mAlarmStatusView != null) { mAlarmStatusView.setSelected(enabled); }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Typeface typeFaceLato, typeFaceLatoBlack, typeFaceLatoLight, typeFaceWeather;

        typeFaceLato = Typeface.createFromAsset(mContext.getAssets(), "fonts/Lato-Reg.otf");
        typeFaceLatoBlack = Typeface.createFromAsset(mContext.getAssets(), "fonts/Lato-Bla.otf");
        typeFaceLatoLight = Typeface.createFromAsset(mContext.getAssets(), "fonts/Lato-Lig.otf");
        typeFaceWeather = Typeface.createFromAsset(mContext.getAssets(), "fonts/Weather.otf");

        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);

        mBatteryStatusView = (TextView) findViewById(R.id.battery_text);
        mBatteryStatusView.setTypeface(typeFaceLatoBlack);

        mDateView = (TextClock) findViewById(R.id.date_view);
        mDateView.setTypeface(typeFaceLatoLight);

        mClockView = (TextClock) findViewById(R.id.clock_view);
        mClockView.setTypeface(typeFaceLato);

        mLine1 = (TextView) findViewById(R.id.line_1);
        mLine1.setTypeface(typeFaceLatoBlack);

        mLine2 = (TextView) findViewById(R.id.line_2);
        mLine2.setTypeface(typeFaceLatoBlack);

        mLine3 = (TextView) findViewById(R.id.line_3);
        mLine3.setTypeface(typeFaceLatoBlack);

        mAmPm = (TextView) findViewById(R.id.am_pm_text);
        mAmPm.setTypeface(typeFaceLato);

        mWeatherStatus = (TextView) findViewById(R.id.weather_text);
        mWeatherStatus.setTypeface(typeFaceLatoBlack);
        mWeatherStatus.setAlpha(0f);

        mWeatherImage = (TextView) findViewById(R.id.weather_image);
        mWeatherImage.setTypeface(typeFaceWeather);
        mWeatherImage.setAlpha(0f);

        mBatteryImage = (ImageView) findViewById(R.id.battery_image);
        mBatteryImage.setImageResource(R.drawable.kg_smartcover_battery);

        mLockPatternUtils = new LockPatternUtils(getContext());
        final boolean screenOn = KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        setEnableMarquee(screenOn);
        refresh();
    }

    protected void refresh() {
        refreshClock();
        refreshAlarmStatus();
        refreshBatteryStatus();
        refreshWeatherStatus();

        refreshStatusLines();
    }

    void refreshClock() {
        Patterns.update(mContext);

        mDateView.setFormat24Hour(Patterns.dateView);
        mDateView.setFormat12Hour(Patterns.dateView);

        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);

        if (!mClockView.is24HourModeEnabled()) {
            String amPm = new SimpleDateFormat("aa").format(new Date());
            mAmPm.setText(amPm);
        } else {
            mAmPm.setText(null);
        }
    }

    void refreshBatteryStatus() {
        if (mBatteryStatusView == null || mBatteryImage == null) {
            return;
        }

        if (mBatteryStatus == null) {
            mBatteryImage.setVisibility(View.INVISIBLE);
            mBatteryStatusView.setVisibility(View.INVISIBLE);
            return;
        } else {
            mBatteryImage.setVisibility(View.VISIBLE);
            mBatteryStatusView.setVisibility(View.VISIBLE);
        }

        mBatteryImage.setImageLevel(mBatteryStatus.level);
        String percentFormat = mContext.getString(R.string.keyguard_battery_percent);
        String text = String.format(percentFormat, mBatteryStatus.level);

        SpannableStringBuilder formatted = new SpannableStringBuilder(text);
        CharacterStyle style = new RelativeSizeSpan(0.7f);
        formatted.setSpan(style, text.length() - 1, text.length(),
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        mBatteryStatusView.setText(formatted);
    }

    void refreshWeatherStatus() {
        if (mWeatherStatus == null || mWeatherImage == null) {
            return;
        }
        final ArrayList<String> weatherInfo = getWeatherInfo();

        if (weatherInfo != null) {
            final String temperature = weatherInfo.get(0);
            final String condition = weatherInfo.get(1);

            SpannableStringBuilder formatted = new SpannableStringBuilder(temperature);
            CharacterStyle style = new RelativeSizeSpan(0.7f);
            formatted.setSpan(style, temperature.length() - 1, temperature.length(),
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

            mWeatherStatus.setText(formatted);
            mWeatherImage.setText(condition);

            if (mFadeInWeather) {
                mWeatherStatus.animate().alpha(1f).setDuration(500);
                mWeatherImage.animate().alpha(1f).setDuration(500);
                mFadeInWeather = false;
            } else {
                mWeatherStatus.setAlpha(1f);
                mWeatherImage.setAlpha(1f);
            }
        }
    }

    void refreshAlarmStatus() {
        if (mAlarmStatusView != null) {
            // Update Alarm status
            String nextAlarm = mLockPatternUtils.getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                mAlarmStatusView.setText(nextAlarm);
                mAlarmStatusView.setVisibility(View.VISIBLE);
            } else {
                mAlarmStatusView.setVisibility(View.GONE);
            }
        }
    }

    void refreshStatusLines() {
        mLine1.setText(null);
        mLine2.setText(null);
        mLine3.setText(null);

        String[] missedCallText, unreadMessagesText;
        missedCallText = refreshMissedCalls();
        unreadMessagesText = refreshMissedTexts();

        if (mMissedCalls == 1) {
            mLine1.setText(missedCallText[0]);
            mLine2.setText(missedCallText[1]);
        } else if (mUnreadMessages == 1) {
            mLine1.setText(unreadMessagesText[0]);
            mLine2.setText(unreadMessagesText[1]);
        } else {
            if (mMissedCalls > 0) {
                mLine2.setText(mContext.getResources().getQuantityString(R.plurals.missed_calls,
                        mMissedCalls, mMissedCalls));
            }
            if (mUnreadMessages > 0) {
                mLine3.setText(mContext.getResources().getQuantityString(R.plurals.unread_messages,
                        mUnreadMessages, mUnreadMessages));
            }
        }
    }

    private ArrayList<String> getWeatherInfo() {
        final ArrayList<String> weatherInfo = new ArrayList<String>(2);
        final String[] projection = {
                "temperature",
                "condition"
        };

        final Cursor c = mContext.getContentResolver().query(
                Uri.parse("content://com.cyanogenmod.lockclock.weather.provider/weather/current"),
                projection, null, null, null);
        if (c == null) {
            mFadeInWeather = true;
            mContext.sendBroadcast(
                    new Intent("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE"));
            if(DEBUG) Log.e(TAG, "cursor was null for temperature");
            return null;
        }
        try {
            c.moveToFirst();
            String temperature = c.getString(0);
            if (temperature == null) {
                temperature = "";
            }
            weatherInfo.add(temperature);

            String condition = c.getString(1);
            if (condition == null) {
                condition = "";
            }
            weatherInfo.add(condition);
            return weatherInfo;
        } finally {
            c.close();
        }
    }

    private String[] refreshMissedCalls() {
        Cursor c = null;
        String[] result = new String[2];
        try {
            c = mContext.getContentResolver().query(
                    Calls.CONTENT_URI,
                    null,
                    Calls.TYPE + " = ? AND " + Calls.NEW + " = ?",
                    new String[]{
                            Integer.toString(Calls.MISSED_TYPE), "1"
                    },
                    Calls.DATE + " DESC "
            );
            if (c != null) {
                c.moveToFirst();
                int count = mMissedCalls = c.getCount();
                if (count == 1) {
                    if (mLockPatternUtils.isSecure()) {
                        result[0] = "";
                        result[1] =
                                mContext.getResources().getQuantityString(R.plurals.missed_calls,
                                        mMissedCalls, mMissedCalls);
                    } else {
                        String name = c.getString(c.getColumnIndex(Calls.CACHED_NAME));
                        result[0] = mContext.getString(R.string.missed_call);
                        result[1] = name;
                    }
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return result;
    }

    private String[] refreshMissedTexts() {
        String[] result = new String[2];
        Cursor c = null;
        try {
            Uri sms_content = Uri.parse("content://sms/inbox");
            c = mContext.getContentResolver().query(sms_content, null, "read=0", null, null);
            c.moveToFirst();
            int count = mUnreadMessages = c.getCount();

            if (count == 1) {
                String name = getContactDisplayNameByNumber(c
                        .getString(c.getColumnIndex("address")));
                if (mLockPatternUtils.isSecure()) {
                    result[0] = mContext.getString(R.string.message_from);
                    result[1] = name;
                } else {
                    result[0] = String.format(mContext.getString(R.string.message_from_person),
                            name);
                    String body = c.getString(c.getColumnIndex("body"));
                    result[1] = (body);
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return result;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        f.addAction("com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED");
        mContext.registerReceiver(mBroadcastReceiver, f);

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.TIME_12_24), false, mContentObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED),
                false, mContentObserver);

        mContext.getContentResolver().registerContentObserver(
                Calls.CONTENT_URI, true,
                mContentObserver);
        mContext.getContentResolver().registerContentObserver(Uri.parse("content://sms/inbox"),
                true, mContentObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);

        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }

    public int getAppWidgetId() {
        return LockPatternUtils.ID_DEFAULT_STATUS_WIDGET;
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is
    // called often.
    // This is an optimization to ensure we only recompute the patterns when the
    // inputs change.
    private static final class Patterns {
        static String dateView;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String dateViewSkel = res.getString(R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) { return; }

            dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't
            // in the skeleton
            // format. The following code removes the AM/PM indicator if we
            // didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }

    public String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        String name = "?";

        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{
                BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
        }, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup
                        .getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return name;
    }
}
