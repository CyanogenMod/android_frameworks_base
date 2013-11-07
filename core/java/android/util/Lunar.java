/*
 * Copyright (C) 2013 The MoKee Android Open Source Project
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

package android.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.content.Context;
import android.text.TextUtils;

import com.android.internal.R;

public class Lunar {
    private int year;
    private int month;
    private int day;
    private boolean leap;
    private Context mContext;
    private Calendar mCalendar;
    private String[] chineseNumber;
    private String[] lunarMonthName;
    private SimpleDateFormat chineseDateFormat;

    final static long[] lunarInfo = new long[] {
            0x04bd8, 0x04ae0, 0x0a570,
            0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0,
            0x0ada2, 0x095b0, 0x14977, 0x04970, 0x0a4b0, 0x0b4b5, 0x06a50,
            0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, 0x06566,
            0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0,
            0x1c8d7, 0x0c950, 0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4,
            0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, 0x06ca0, 0x0b550,
            0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950,
            0x06aa0, 0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260,
            0x0f263, 0x0d950, 0x05b57, 0x056a0, 0x096d0, 0x04dd5, 0x04ad0,
            0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40,
            0x0af46, 0x0ab60, 0x09570, 0x04af5, 0x04970, 0x064b0, 0x074a3,
            0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, 0x0c960,
            0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0,
            0x092d0, 0x0cab5, 0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9,
            0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, 0x07954, 0x06aa0,
            0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65,
            0x0d530, 0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0,
            0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, 0x0b5a0, 0x056d0, 0x055b2,
            0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0
    };

    private int yearDays(int y) {
        int i, sum = 348;
        for (i = 0x8000; i > 0x8; i >>= 1) {
            if ((lunarInfo[y - 1900] & i) != 0)
                sum += 1;
        }
        return (sum + leapDays(y));
    }

    private int leapDays(int y) {
        if (leapMonth(y) != 0) {
            if ((lunarInfo[y - 1900] & 0x10000) != 0)
                return 30;
            else
                return 29;
        } else
            return 0;
    }

    private int leapMonth(int y) {
        return (int) (lunarInfo[y - 1900] & 0xf);
    }

    private int monthDays(int y, int m) {
        if ((lunarInfo[y - 1900] & (0x10000 >> m)) == 0)
            return 29;
        else
            return 30;
    }

    public String animalsYear() {
        final String[] Animals = mContext.getResources().getStringArray(com.mokee.internal.R.array.animals);
        return Animals[(year - 4) % 12];
    }

    private String cyclicalm(int num) {
        final String[] Gan = mContext.getResources().getStringArray(com.mokee.internal.R.array.gan);
        final String[] Zhi = mContext.getResources().getStringArray(com.mokee.internal.R.array.zhi);
        return (Gan[num % 10] + Zhi[num % 12]);
    }

    public String cyclical() {
        int num = year - 1900 + 36;
        return (cyclicalm(num));
    }

    public Lunar(Calendar cal, Context context) {
        int yearCyl, monCyl, dayCyl;
        int leapMonth = 0;
        mContext = context;
        mCalendar = cal;
        chineseNumber = mContext.getResources().getStringArray(com.mokee.internal.R.array.chinesenumber);
        lunarMonthName = mContext.getResources().getStringArray(com.mokee.internal.R.array.lunar_month_name);
        String format1 = mContext.getResources().getString(com.mokee.internal.R.string.status_format1);
        chineseDateFormat = new SimpleDateFormat(format1);
        Date baseDate = null;
        try {
            String format2 = mContext.getResources().getString(com.mokee.internal.R.string.status_format2);
            baseDate = chineseDateFormat.parse(format2);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        int offset = (int) ((cal.getTime().getTime() - baseDate.getTime()) / 86400000L);
        dayCyl = offset + 40;
        monCyl = 14;
        int iYear, daysOfYear = 0;
        for (iYear = 1900; iYear < 2050 && offset > 0; iYear++) {
            daysOfYear = yearDays(iYear);
            offset -= daysOfYear;
            monCyl += 12;
        }
        if (offset < 0) {
            offset += daysOfYear;
            iYear--;
            monCyl -= 12;
        }
        year = iYear;
        yearCyl = iYear - 1864;
        leapMonth = leapMonth(iYear);
        leap = false;
        int iMonth, daysOfMonth = 0;
        for (iMonth = 1; iMonth < 13 && offset > 0; iMonth++) {
            if (leapMonth > 0 && iMonth == (leapMonth + 1) && !leap) {
                --iMonth;
                leap = true;
                daysOfMonth = leapDays(year);
            } else
                daysOfMonth = monthDays(year, iMonth);
            offset -= daysOfMonth;
            if (leap && iMonth == (leapMonth + 1))
                leap = false;
            if (!leap)
                monCyl++;
        }
        if (offset == 0 && leapMonth > 0 && iMonth == leapMonth + 1) {
            if (leap) {
                leap = false;
            } else {
                leap = true;
                --iMonth;
                --monCyl;
            }
        }
        if (offset < 0) {
            offset += daysOfMonth;
            --iMonth;
            --monCyl;
        }
        month = iMonth;
        day = offset + 1;
    }

    public String getChinaDayString(int day) {
        String chineseTen[] = mContext.getResources().getStringArray(com.mokee.internal.R.array.chineseten);
        int n = day % 10 == 0 ? 9 : day % 10 - 1;
        if (day > 30)
            return "";
        else if (day == 10)
            return mContext.getResources().getString(com.mokee.internal.R.string.status_chushi);
        else if (day == 20)
            return mContext.getResources().getString(com.mokee.internal.R.string.status_ershi);
        else if (day == 30)
            return mContext.getResources().getString(com.mokee.internal.R.string.status_sanshi);
        else
            return chineseTen[day / 10] + chineseNumber[n];
    }

    public String toString() {
        String year1 = mContext.getResources().getString(com.mokee.internal.R.string.status_year);
        String run1 = mContext.getResources().getString(com.mokee.internal.R.string.status_leap);
        String month1 = mContext.getResources().getString(com.mokee.internal.R.string.status_month);
        return cyclical() + animalsYear() + year1 + (leap ? run1 : "") + lunarMonthName[month - 1]
                + month1
                + getChinaDayString(day);
    }

    public boolean isBigMonth(String lunarFestivalStr) {
        if (monthDays(year, month) == 30) {
            return true;
        } else {
            return false;
        }
    }
}

