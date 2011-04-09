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

package com.android.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerImpl;

public class LoadAverageService extends Service {
    private View mView;

    private static final class Stats extends ProcessStats {
        String mLoadText;
        int mLoadWidth;

        private final Paint mPaint;

        Stats(Paint paint) {
            super(false);
            mPaint = paint;
        }

        @Override
        public void onLoadChanged(float load1, float load5, float load15) {
            mLoadText = load1 + " / " + load5 + " / " + load15;
            mLoadWidth = (int)mPaint.measureText(mLoadText);
        }

        @Override
        public int onMeasureProcessName(String name) {
            return (int)mPaint.measureText(name);
        }
    }

    private class LoadView extends View {
        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    mStats.update();
                    updateDisplay();
                    Message m = obtainMessage(1);
                    sendMessageDelayed(m, 2000);
                }
            }
        };

        private final Stats mStats;

        private Paint mLoadPaint;
        private Paint mAddedPaint;
        private Paint mRemovedPaint;
        private Paint mShadowPaint;
        private Paint mShadow2Paint;
        private Paint mIrqPaint;
        private Paint mSystemPaint;
        private Paint mUserPaint;
        private float mAscent;
        private int mFH;

        private int mNeededWidth;
        private int mNeededHeight;

        LoadView(Context c) {
            super(c);

            setPadding(4, 4, 4, 4);
            //setBackgroundResource(com.android.internal.R.drawable.load_average_background);

            mLoadPaint = new Paint();
            mLoadPaint.setAntiAlias(true);
            mLoadPaint.setTextSize(10);
            mLoadPaint.setARGB(255, 255, 255, 255);

            mAddedPaint = new Paint();
            mAddedPaint.setAntiAlias(true);
            mAddedPaint.setTextSize(10);
            mAddedPaint.setARGB(255, 128, 255, 128);

            mRemovedPaint = new Paint();
            mRemovedPaint.setAntiAlias(true);
            mRemovedPaint.setStrikeThruText(true);
            mRemovedPaint.setTextSize(10);
            mRemovedPaint.setARGB(255, 255, 128, 128);

            mShadowPaint = new Paint();
            mShadowPaint.setAntiAlias(true);
            mShadowPaint.setTextSize(10);
            //mShadowPaint.setFakeBoldText(true);
            mShadowPaint.setARGB(192, 0, 0, 0);
            mLoadPaint.setShadowLayer(4, 0, 0, 0xff000000);

            mShadow2Paint = new Paint();
            mShadow2Paint.setAntiAlias(true);
            mShadow2Paint.setTextSize(10);
            //mShadow2Paint.setFakeBoldText(true);
            mShadow2Paint.setARGB(192, 0, 0, 0);
            mLoadPaint.setShadowLayer(2, 0, 0, 0xff000000);

            mIrqPaint = new Paint();
            mIrqPaint.setARGB(0x80, 0, 0, 0xff);
            mIrqPaint.setShadowLayer(2, 0, 0, 0xff000000);
            mSystemPaint = new Paint();
            mSystemPaint.setARGB(0x80, 0xff, 0, 0);
            mSystemPaint.setShadowLayer(2, 0, 0, 0xff000000);
            mUserPaint = new Paint();
            mUserPaint.setARGB(0x80, 0, 0xff, 0);
            mSystemPaint.setShadowLayer(2, 0, 0, 0xff000000);

            mAscent = mLoadPaint.ascent();
            float descent = mLoadPaint.descent();
            mFH = (int)(descent - mAscent + .5f);

            mStats = new Stats(mLoadPaint);
            mStats.init();
            updateDisplay();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mHandler.sendEmptyMessage(1);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mHandler.removeMessages(1);
        }

        @Override
        protected void onMeasure(int widthMeasureSpect, int heightMeasureSpec) {
            setMeasuredDimension(mNeededWidth, mNeededHeight);
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int W = getWidth();

            final Stats stats = mStats;
            final int userTime = stats.getLastUserTime();
            final int systemTime = stats.getLastSystemTime();
            final int iowaitTime = stats.getLastIoWaitTime();
            final int irqTime = stats.getLastIrqTime();
            final int softIrqTime = stats.getLastSoftIrqTime();
            final int idleTime = stats.getLastIdleTime();

            final int totalTime = userTime+systemTime+iowaitTime+irqTime+softIrqTime+idleTime;
            if (totalTime == 0) {
                return;
            }
            int userW = (userTime*W)/totalTime;
            int systemW = (systemTime*W)/totalTime;
            int irqW = ((iowaitTime+irqTime+softIrqTime)*W)/totalTime;

            int x = W - mPaddingRight;
            int top = mPaddingTop + 2;
            int bottom = mPaddingTop + mFH - 2;

            if (irqW > 0) {
                canvas.drawRect(x-irqW, top, x, bottom, mIrqPaint);
                x -= irqW;
            }
            if (systemW > 0) {
                canvas.drawRect(x-systemW, top, x, bottom, mSystemPaint);
                x -= systemW;
            }
            if (userW > 0) {
                canvas.drawRect(x-userW, top, x, bottom, mUserPaint);
                x -= userW;
            }

            int y = mPaddingTop - (int)mAscent;
            canvas.drawText(stats.mLoadText, W-mPaddingRight-stats.mLoadWidth-1,
                    y-1, mShadowPaint);
            canvas.drawText(stats.mLoadText, W-mPaddingRight-stats.mLoadWidth-1,
                    y+1, mShadowPaint);
            canvas.drawText(stats.mLoadText, W-mPaddingRight-stats.mLoadWidth+1,
                    y-1, mShadow2Paint);
            canvas.drawText(stats.mLoadText, W-mPaddingRight-stats.mLoadWidth+1,
                    y+1, mShadow2Paint);
            canvas.drawText(stats.mLoadText, W-mPaddingRight-stats.mLoadWidth,
                    y, mLoadPaint);

            int N = stats.countWorkingStats();
            for (int i=0; i<N; i++) {
                Stats.Stats st = stats.getWorkingStats(i);
                y += mFH;
                top += mFH;
                bottom += mFH;

                userW = (st.rel_utime*W)/totalTime;
                systemW = (st.rel_stime*W)/totalTime;
                x = W - mPaddingRight;
                if (systemW > 0) {
                    canvas.drawRect(x-systemW, top, x, bottom, mSystemPaint);
                    x -= systemW;
                }
                if (userW > 0) {
                    canvas.drawRect(x-userW, top, x, bottom, mUserPaint);
                    x -= userW;
                }

                canvas.drawText(st.name, W-mPaddingRight-st.nameWidth-1,
                        y-1, mShadowPaint);
                canvas.drawText(st.name, W-mPaddingRight-st.nameWidth-1,
                        y+1, mShadowPaint);
                canvas.drawText(st.name, W-mPaddingRight-st.nameWidth+1,
                        y-1, mShadow2Paint);
                canvas.drawText(st.name, W-mPaddingRight-st.nameWidth+1,
                        y+1, mShadow2Paint);
                Paint p = mLoadPaint;
                if (st.added) p = mAddedPaint;
                if (st.removed) p = mRemovedPaint;
                canvas.drawText(st.name, W-mPaddingRight-st.nameWidth, y, p);
            }
        }

        void updateDisplay() {
            final Stats stats = mStats;
            final int NW = stats.countWorkingStats();

            int maxWidth = stats.mLoadWidth;
            for (int i=0; i<NW; i++) {
                Stats.Stats st = stats.getWorkingStats(i);
                if (st.nameWidth > maxWidth) {
                    maxWidth = st.nameWidth;
                }
            }

            int neededWidth = mPaddingLeft + mPaddingRight + maxWidth;
            int neededHeight = mPaddingTop + mPaddingBottom + (mFH*(1+NW));
            if (neededWidth != mNeededWidth || neededHeight != mNeededHeight) {
                mNeededWidth = neededWidth;
                mNeededHeight = neededHeight;
                requestLayout();
            } else {
                invalidate();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mView = new LoadView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.setTitle("Load Average");
        WindowManagerImpl wm = (WindowManagerImpl)getSystemService(WINDOW_SERVICE);
        wm.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((WindowManagerImpl)getSystemService(WINDOW_SERVICE)).removeView(mView);
        mView = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
