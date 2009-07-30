package com.tmobile.widget;

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

import android.content.Context;
import android.util.AttributeSet;
import android.graphics.drawable.Drawable;

// The only difference between this class and android.widget.CheckedTextView
// is that it places check mark at the left, while android.widget.CheckedTextView
// places the check mark at the right end.
/*
 ** @hide
 */
public final class CheckedTextView extends android.widget.CheckedTextView {

    public CheckedTextView(Context context) {
        this(context, null);
    }

    public CheckedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CheckedTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // Although this call is redundant, it's used here.
        setIsLeftAligned(true);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        // Hacky way to make sure that mIsLeftAligned is set to true
        // BEFORE we invoke any method which checks the flag.
        // This is necessary, since the method is invoked BEFORE
        // setIsLeftAligned(true) is called in the constructor.
        setIsLeftAligned(true);
        super.setPadding(left, top, right, bottom);
    }

    @Override
    public void setCheckMarkDrawable(Drawable d) {
        // Hacky way to make sure that mIsLeftAligned is set to true
        // BEFORE we invoke any method which checks the flag.
        // This is necessary, since the method is invoked BEFORE
        // setIsLeftAligned(true) is called in the constructor.
        setIsLeftAligned(true);
        super.setCheckMarkDrawable(d);
    }

}
