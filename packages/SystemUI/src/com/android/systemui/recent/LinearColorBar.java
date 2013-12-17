/*
 * Copyright (C) 2013 Slimroms
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

package com.android.systemui.recent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;

public class LinearColorBar extends LinearLayout {

    static final int USED_MEM_COLOR = 0xff8d8d8d;
    static final int USED_CACHE_COLOR = 0xff00aa00;
    static final int USED_ACTIVE_APPS_COLOR = 0xff33b5e5;
    static final int FREE_COLOR = 0xffaaaaaa;

    private float mRamBarMode;

    private float mUsedMemRatio;
    private float mUsedCacheMemRatio;
    private float mUsedActiveAppsMemRatio;

    private int mUsedMemColor;
    private int mUsedCacheMemColor;
    private int mUsedActiveAppsMemColor;

    final Rect mRect = new Rect();
    final Paint mPaint = new Paint();

    public LinearColorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void setRatios(float usedMem, float usedCacheMem, float usedActiveAppsMem) {
        mUsedMemRatio = usedMem;
	if (mUsedMemRatio < 0)
            mUsedMemRatio = 0;
        mUsedCacheMemRatio = usedCacheMem;
        mUsedActiveAppsMemRatio = usedActiveAppsMem;
        updateModeAndColors();
        invalidate();
    }

    private void updateIndicator() {
        int off = getPaddingTop() - getPaddingBottom();
        if (off < 0) off = 0;
        mRect.top = off;
        mRect.bottom = getHeight();
    }

    private void updateModeAndColors() {
        mRamBarMode = (Settings.System.getInt(mContext.getContentResolver(),
                             Settings.System.RECENTS_RAM_BAR_MODE, 0));
        mUsedMemColor = (Settings.System.getInt(mContext.getContentResolver(),
                               Settings.System.RECENTS_RAM_BAR_MEM_COLOR, USED_MEM_COLOR));
        mUsedCacheMemColor = (Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.RECENTS_RAM_BAR_CACHE_COLOR, USED_CACHE_COLOR));
        mUsedActiveAppsMemColor = (Settings.System.getInt(mContext.getContentResolver(),
                                         Settings.System.RECENTS_RAM_BAR_ACTIVE_APPS_COLOR, USED_ACTIVE_APPS_COLOR));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateIndicator();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int left = 0;

        int rightActiveApps = (int)(width*mUsedActiveAppsMemRatio);
        int rightCache = (int)(width*mUsedCacheMemRatio);
        int rightMem = (int)(width*mUsedMemRatio);

        mRect.left = left;
        mRect.right = rightActiveApps;
        mPaint.setColor(mUsedActiveAppsMemColor);
        canvas.drawRect(mRect, mPaint);
        left = rightActiveApps;

        if (mRamBarMode == 2 || mRamBarMode == 3) {
            mRect.left = left;
            mRect.right = left + rightCache;
            mPaint.setColor(mUsedCacheMemColor);
            canvas.drawRect(mRect, mPaint);
            left = left + rightCache;
        }

        if (mRamBarMode == 3) {
            mRect.left = left;
            mRect.right = left + rightMem;
            mPaint.setColor(mUsedMemColor);
            canvas.drawRect(mRect, mPaint);
            left = left + rightMem;
        }

        mRect.left = left;
        mRect.right = width;
        mPaint.setColor(FREE_COLOR);
        canvas.drawRect(mRect, mPaint);

    }
}
