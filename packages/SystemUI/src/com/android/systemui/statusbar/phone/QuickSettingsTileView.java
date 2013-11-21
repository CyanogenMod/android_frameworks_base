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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

/**
 *
 */
public class QuickSettingsTileView extends FrameLayout {
    private static final String TAG = "QuickSettingsTileView";

    private int mContentLayoutId;
    private int mColSpan;
    private int mRowSpan;

    public QuickSettingsTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContentLayoutId = -1;
        mColSpan = 1;
        mRowSpan = 1;
    }

    void setColumnSpan(int span) {
        mColSpan = span;
    }

    int getColumnSpan() {
        return mColSpan;
    }

    public void setContent(int layoutId, LayoutInflater inflater) {
        mContentLayoutId = layoutId;
        inflater.inflate(layoutId, this);
    }

    void reinflateContent(LayoutInflater inflater) {
        if (mContentLayoutId != -1) {
            removeAllViews();
            setContent(mContentLayoutId, inflater);
        } else {
            Log.e(TAG, "Not reinflating content: No layoutId set");
        }
    }

    @Override
    public void setVisibility(int vis) {
        if (QuickSettings.DEBUG_GONE_TILES) {
            if (vis == View.GONE) {
                vis = View.VISIBLE;
                setAlpha(0.25f);
                setEnabled(false);
            } else {
                setAlpha(1f);
                setEnabled(true);
            }
        }
        super.setVisibility(vis);
    }
}