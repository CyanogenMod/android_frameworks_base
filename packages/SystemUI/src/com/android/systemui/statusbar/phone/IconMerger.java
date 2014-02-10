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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.content.ContentResolver;
import android.database.ContentObserver;

import com.android.systemui.R;

import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.ClockCenter;

public class IconMerger extends LinearLayout {
    private static final String TAG = "IconMerger";
    private static final boolean DEBUG = false;

    private int mIconSize;
    private View mMoreView;
    private ClockCenter mClockCenter;
    private View mCenterSpacer;
    private int mTotalWidth;
    private SettingsObserver mSettingsObserver;
    private boolean mAttached;
    private int mAvailWidth;
    private boolean mShowCenterClock;
    private int mIconHPadding;

    public IconMerger(Context context, AttributeSet attrs) {
        super(context, attrs);

        mIconSize = context.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_size);

        mTotalWidth = mContext.getResources().getDisplayMetrics().widthPixels;

        mIconHPadding = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_padding);
        if (DEBUG) {
            setBackgroundColor(0x800099FF);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;

            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }

    public void setOverflowIndicator(View v) {
        mMoreView = v;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // we need to constrain this to an integral multiple of our children
        recalcSize();
        setMeasuredDimension(mAvailWidth - (mAvailWidth % (mIconSize  + 2 * mIconHPadding)), getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkOverflow(r - l);
    }

    public void setClockCenter(ClockCenter clockCenter) {
        mClockCenter = clockCenter;
    }

    public void setCenterSpacer(View centerSpacer) {
        mCenterSpacer = centerSpacer;
    }

    private void recalcSize() {
        if (mShowCenterClock){
            mAvailWidth = mTotalWidth/2 - mClockCenter.getMeasuredWidth()/2 - mIconSize/2;
        } else {
            mAvailWidth = getMeasuredWidth();
        }
    }

    private void checkOverflow(int width) {
        if (mMoreView == null) return;

        final int N = getChildCount();
        int visibleChildren = 0;
        for (int i=0; i<N; i++) {
            if (getChildAt(i).getVisibility() != GONE) visibleChildren++;
        }
        final boolean overflowShown = (mMoreView.getVisibility() == View.VISIBLE);
        // let's assume we have one more slot if the more icon is already showing
        //if (overflowShown) visibleChildren --;
        //Log.d("maxwen", "getMeasuredWidth()="+getMeasuredWidth()+"getWidth()="+getWidth()+" mIconSize="+mIconSize);
        final boolean moreRequired = visibleChildren * (mIconSize + 2 * mIconHPadding) > mAvailWidth;
        if (moreRequired != overflowShown) {
            post(new Runnable() {
                @Override
                public void run() {
                    mMoreView.setVisibility(moreRequired ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_STYLE), false,
                    this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mShowCenterClock = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_STYLE, Clock.STYLE_CLOCK_RIGHT) == Clock.STYLE_CLOCK_CENTER;

        mIconHPadding = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_padding);

        // fore relayout and avail width calculation
        post(new Runnable() {
            @Override
            public void run() {
                if (mShowCenterClock){
                    mCenterSpacer.setVisibility(View.VISIBLE);
                } else {
                    mCenterSpacer.setVisibility(View.GONE);
                }
            }
        });
    }
}
