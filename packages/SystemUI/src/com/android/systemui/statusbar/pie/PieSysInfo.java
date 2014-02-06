/*
 * Copyright (C) 2014 SlimRoms Project
 * This code is loosely based on portions of the CyanogenMod Project (Jens Doll) Copyright (C) 2013
 * and the ParanoidAndroid Project source, Copyright (C) 2012.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.pie;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;

import com.android.internal.util.gesture.EdgeGesturePosition;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.PieController;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A slice that displays some basic system information and a clock.
 * <p>
 * This slice has no user interactions defined.
 */
public class PieSysInfo extends PieSliceContainer implements ValueAnimator.AnimatorUpdateListener {

    private PieController mController;
    private Context mContext;

    private Path mClockPath = new Path();
    private Path mInfoPath[] = new Path[4];

    private Paint mClockPaint = new Paint();
    private Paint mInfoPaint = new Paint();

    private float[] mClockTextDisplacements = new float[32];

    private boolean mStaleData = true;
    private String mClockText;
    private String mDateText;
    private String mNetworkState;
    private String mBatteryLevelReadable;
    private String mWifiSsid;

    private String mTimeFormatString;
    private SimpleDateFormat mTimeFormat;

    public PieSysInfo(Context context, PieView parent,
            PieController controller, int initialFlags) {
        super(parent, initialFlags);
        mController = controller;
        mContext = context;

        mClockPaint.setAntiAlias(true);
        mClockPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mInfoPaint.setAntiAlias(true);
        mInfoPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    }

    private void setColor() {
        int textColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_TEXT_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (textColor == -2) {
            textColor = mContext.getResources().getColor(R.color.pie_text_color);
        }
        mClockPaint.setColor(textColor);
        mInfoPaint.setColor(textColor);
    }

    @Override
    public void prepare(EdgeGesturePosition position, float scale, boolean mirrorRightPie) {

        // We are updating data later when we starting to get visible.
        // This does not save work on the main thread, but for fast gestures
        // we don't even start to collect this data.
        mStaleData = true;

        mClockText = getTimeFormat().format(new Date());

        mClockPaint.setAlpha(0);
        mInfoPaint.setAlpha(0);

        final Resources res = mContext.getResources();
        int textsize = res.getDimensionPixelSize(R.dimen.pie_textsize);

        mInfoPaint.setTextSize(textsize * scale);
        mClockPaint.setTextSize((mOuter - mInner) * scale);

        float total = 0;
        for (int i = 0; i < mClockText.length(); i++) {
            char character = mClockText.charAt(i);
            float measure = mClockPaint.measureText("" + character);
            mClockTextDisplacements[i] =
                    measure * (character == '1' || character == ':' ? 0.7f : 0.95f);
            total += mClockTextDisplacements[i];
        }
        float alpha = 268 - (float)(total * 360 / (2.0f * Math.PI * mInner * scale));

        mClockPath = updatePath(mClockPath, mInner * scale, alpha, mSweep);
        for (int i = 0; i < mInfoPath.length; i++)
            mInfoPath[i] = updatePath(mInfoPath[i], (mInner + textsize * 1.2f * i) * scale,
                    272, mStart + mSweep - 272);
    }

    @Override
    public void draw(Canvas canvas, EdgeGesturePosition position) {
        // As long as there is no new data, we don't need to draw anything.
        if (mStaleData || Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_SHOW_TEXT, 1, UserHandle.USER_CURRENT) == 0) {
            return;
        }

        setColor();
        float lastPos = 0;
        for(int i = 0; i < mClockText.length(); i++) {
            canvas.drawTextOnPath("" + mClockText.charAt(i), mClockPath, lastPos, 0, mClockPaint);
            lastPos += mClockTextDisplacements[i];
        }

        if (mNetworkState != null) {
            canvas.drawTextOnPath(mNetworkState, mInfoPath[3], 0, 0, mInfoPaint);
        }
        canvas.drawTextOnPath(mDateText, mInfoPath[2], 0, 0, mInfoPaint);
        canvas.drawTextOnPath(mBatteryLevelReadable, mInfoPath[1], 0, 0, mInfoPaint);
        canvas.drawTextOnPath(mWifiSsid, mInfoPath[0], 0, 0, mInfoPaint);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        int alpha = (int) (255 * animation.getAnimatedFraction());
        mClockPaint.setAlpha(alpha);
        mInfoPaint.setAlpha(alpha);

        // If we are going to get displayed update data
        if (alpha > 0 && mStaleData) {
            updateData();
            mStaleData = false;
        }
    }

    private Path updatePath(Path path, float radius, float start, float sweep) {
        if (path == null) {
            path = new Path();
        } else {
            path.reset();
        }

        final RectF bB = new RectF(-radius, -radius, radius, radius);
        path.arcTo(bB, start, sweep, true);
        return path;
    }

    private void updateData() {
        mDateText = DateFormat.getMediumDateFormat(mContext).format(new Date()).toUpperCase();
        mNetworkState = mController.getOperatorState();
        if (mNetworkState != null) {
            mNetworkState = mNetworkState.toUpperCase();
        }
        mWifiSsid = getWifiSsid().toUpperCase();
        mBatteryLevelReadable = mController.getBatteryLevel().toUpperCase();
    }

    private String getWifiSsid() {
        String ssid = null;
        final WifiManager wifiManager =
                (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                final WifiSsid wifiSsid = connectionInfo.getWifiSsid();
                if (wifiSsid != null) {
                    ssid = wifiSsid.toString();
                }
            }
        }
        if (TextUtils.isEmpty(ssid)) {
            ssid = mContext.getString(R.string.quick_settings_wifi_not_connected);
        }
        return ssid;
    }

    private SimpleDateFormat getTimeFormat() {
        String format = DateFormat.getTimeFormatString(mContext);
        if (format.equals(mTimeFormatString)) {
            return mTimeFormat;
        }

        /*
         * Strip off any unquoted 'a' characters in the format string
         * to get a time without AM/PM extension
         */
        StringBuilder formatBuilder = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);

            if (c == '\'') {
                quoted = !quoted;
            }
            if (quoted || c != 'a') {
                formatBuilder.append(c);
            }
        }

        mTimeFormatString = format;
        mTimeFormat = new SimpleDateFormat(formatBuilder.toString().trim());
        return mTimeFormat;
    }
}
