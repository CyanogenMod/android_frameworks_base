/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.systemui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.FileReader;

public class CPUInfoService extends Service {
    private View mView;
    private Thread mCurCPUThread;
    private final String TAG = "CPUInfoService";

    private class CPUView extends View {
        private Paint mOnlinePaint;
        private Paint mOfflinePaint;
        private Paint mLpPaint;
        private float mAscent;
        private int mFH;
        private int mMaxWidth;

        private int mNeededWidth;
        private int mNeededHeight;

        private String[] mCurrFreq = {"0", "0", "0", "0"};
        private String[] mCurrGov = {"", "", "", ""};
        private boolean mLpMode;
        private String mCPUTemp;

        private Handler mCurCPUHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.obj == null) {
                    return;
                }
                if (msg.what == 1) {
                    String[] parts = ((String) msg.obj).split(";");
                    if (parts.length != 3) {
                        return;
                    }
                    mCPUTemp = parts[0];
                    mLpMode = parts[1].equals("1");

                    final String[] cpuParts = parts[2].split("\\|");
                    if (cpuParts.length != 4) {
                        return;
                    }

                    String cpuInfo;
                    String[] cpuInfoParts;
                    for (int i = 0; i < cpuParts.length; i++) {
                        cpuInfo = cpuParts[i];
                        cpuInfoParts = cpuInfo.split(":");
                        if (cpuInfoParts.length == 2) {
                            mCurrFreq[i] = cpuInfoParts[0];
                            mCurrGov[i] = cpuInfoParts[1];
                        } else {
                            mCurrFreq[i] = "0";
                            mCurrGov[i] = "";
                        }
                    }
                    updateDisplay();
                }
            }
        };

        CPUView(Context c) {
            super(c);

            setPadding(4, 4, 4, 4);
            setBackgroundColor(Color.argb(180, 0, 0, 0));

            // Need to scale text size by density...  but we won't do it
            // linearly, because with higher dps it is nice to squeeze the
            // text a bit to fit more of it.  And with lower dps, trying to
            // go much smaller will result in unreadable text.
            int textSize;
            float density = c.getResources().getDisplayMetrics().density;
            if (density < 1) {
                textSize = 9;
            } else {
                textSize = (int) (12 * density);
                if (textSize < 10) {
                    textSize = 10;
                }
            }
            mOnlinePaint = new Paint();
            mOnlinePaint.setAntiAlias(true);
            mOnlinePaint.setTextSize(textSize);
            mOnlinePaint.setARGB(255, 255, 255, 255);

            mOfflinePaint = new Paint();
            mOfflinePaint.setAntiAlias(true);
            mOfflinePaint.setTextSize(textSize);
            mOfflinePaint.setARGB(255, 255, 0, 0);

            mLpPaint = new Paint();
            mLpPaint.setAntiAlias(true);
            mLpPaint.setTextSize(textSize);
            mLpPaint.setARGB(255, 0, 255, 0);

            mAscent = mOnlinePaint.ascent();
            float descent = mOnlinePaint.descent();
            mFH = (int) (descent - mAscent + .5f);

            final String maxWidthStr = "cpuXx xxxxxxxxxxxxxx x 1700000 MHz";
            mMaxWidth = (int) mOnlinePaint.measureText(maxWidthStr);

            updateDisplay();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mCurCPUHandler.removeMessages(1);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(mNeededWidth, widthMeasureSpec),
                    resolveSize(mNeededHeight, heightMeasureSpec));
        }

        private String getCPUInfoString(int i) {
            return ("cpu" + i + ": " + mCurrGov[i] + " : " + toMHz(mCurrFreq[i]));
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int RIGHT = getWidth() - 1;

            int y = mPaddingTop - (int) mAscent;

            canvas.drawText("Temp: " + mCPUTemp, RIGHT - mPaddingRight - mMaxWidth,
                    y - 1, mOnlinePaint);
            y += mFH;

            String s;
            String freq;
            for (int i = 0; i < mCurrFreq.length; i++) {
                s = getCPUInfoString(i);
                freq = mCurrFreq[i];
                if (!freq.equals("0")) {
                    if (i == 0 && mLpMode) {
                        canvas.drawText(s, RIGHT - mPaddingRight - mMaxWidth,
                                y - 1, mLpPaint);
                    } else {
                        canvas.drawText(s, RIGHT - mPaddingRight - mMaxWidth,
                                y - 1, mOnlinePaint);
                    }
                } else {
                    canvas.drawText(s, RIGHT - mPaddingRight - mMaxWidth,
                            y - 1, mOfflinePaint);
                }
                y += mFH;
            }
        }

        void updateDisplay() {
            final int NW = 4;

            int neededWidth = mPaddingLeft + mPaddingRight + mMaxWidth;
            int neededHeight = mPaddingTop + mPaddingBottom + (mFH * (1 + NW));
            if (neededWidth != mNeededWidth || neededHeight != mNeededHeight) {
                mNeededWidth = neededWidth;
                mNeededHeight = neededHeight;
                requestLayout();
            } else {
                invalidate();
            }
        }

        private String toMHz(String mhzString) {
            return (Integer.valueOf(mhzString) / 1000 + " MHz");
        }

        public Handler getHandler() {
            return mCurCPUHandler;
        }
    }

    protected class CurCPUThread extends Thread {
        private boolean mInterrupt = false;
        private Handler mHandler;

        private static final String CPU_ROOT = "/sys/devices/system/cpu/cpu";
        private static final String CPU_CUR_TAIL = "/cpufreq/scaling_cur_freq";
        private static final String CPU_LP_MODE = "/sys/kernel/debug/clock/cpu_lp/state";
        private static final String CPU_GOV_TAIL = "/cpufreq/scaling_governor";
        private static final String CPU_TEMP_HTC = "/sys/htc/cpu_temp";
        private static final String CPU_TEMP_OPPO = "/sys/class/thermal/thermal_zone0/temp";

        public CurCPUThread(Handler handler) {
            mHandler = handler;
        }

        public void interrupt() {
            mInterrupt = true;
        }

        private String readOneLine(String fname) {
            BufferedReader br;
            String line = null;
            try {
                br = new BufferedReader(new FileReader(fname), 512);
                try {
                    line = br.readLine();
                } finally {
                    br.close();
                }
            } catch (Exception e) {
                return null;
            }
            return line;
        }

        @Override
        public void run() {
            try {
                while (!mInterrupt) {
                    sleep(500);
                    StringBuilder sb = new StringBuilder();

                    String cpuTemp = readOneLine(CPU_TEMP_HTC);
                    if (cpuTemp == null) {
                        cpuTemp = readOneLine(CPU_TEMP_OPPO);
                    }
                    sb.append(cpuTemp == null ? "0" : cpuTemp);
                    sb.append(";");
                    final String lpMode = readOneLine(CPU_LP_MODE);
                    sb.append(lpMode == null ? "0" : lpMode);
                    sb.append(";");

                    String currFreq;
                    String currGov;
                    for (int i = 0; i < 4; i++) {
                        currFreq = readOneLine(CPU_ROOT + i + CPU_CUR_TAIL);
                        currGov = readOneLine(CPU_ROOT + i + CPU_GOV_TAIL);

                        if (currFreq == null) {
                            currFreq = "0";
                            currGov = "";
                        }

                        sb.append(currFreq).append(":").append(currGov).append("|");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    mHandler.sendMessage(mHandler.obtainMessage(1, sb.toString()));
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread exception: " + e.getMessage());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mView = new CPUView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.setTitle("CPU Info");

        mCurCPUThread = new CurCPUThread(mView.getHandler());
        mCurCPUThread.start();

        Log.d(TAG, "started CurCPUThread");

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCurCPUThread.isAlive()) {
            mCurCPUThread.interrupt();
            try {
                mCurCPUThread.join();
            } catch (InterruptedException e) {
                // nothing to do here
            }
        }
        Log.d(TAG, "stopped CurCPUThread");
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(mView);
        mView = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
