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

package com.android.server.status;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextSwitcher;
import android.widget.TextView;


public class TickerView extends TextSwitcher
{
    Ticker mTicker;
    private int textColor = 0xFF000000;

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTicker.reflowText();
    }

    @Override
    public void setText(CharSequence text) {
        final TextView t = (TextView) getNextView();
        t.setTextColor(textColor);
        t.setText(text);
        showNext();
    }

    @Override
    public void setCurrentText(CharSequence text) {
        final TextView t = (TextView) getCurrentView();
        t.setTextColor(textColor);
        t.setText(text);
    }

    public void updateColors(int color) {
        textColor = color;
    }
}

