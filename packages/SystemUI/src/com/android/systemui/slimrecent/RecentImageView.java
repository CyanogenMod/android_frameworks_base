/*
 * Copyright (C) 2014 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
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

package com.android.systemui.slimrecent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Thanks to a google post from Jorim Jaggy I saw
 * this nice trick to reduce requestLayout calls.
 *
 * https://plus.google.com/+JorimJaggi/posts/iTk4PjgeAWX
 *
 * We handle in SlimRecent always exactly same measured
 * bitmaps and drawables. So we do not need a
 * requestLayout call at all when we use setImageBitmap
 * or setImageDrawable. Due that setImageBitmap directly
 * calls setImageDrawable (#link:ImageView) it is enough
 * to block this behaviour on setImageDrawable.
 */

public class RecentImageView extends ImageView {
    private boolean mBlockLayout;

    public RecentImageView(Context context) {
        super(context);
    }

    public RecentImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecentImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayout) {
            super.requestLayout();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        mBlockLayout = true;
        super.setImageDrawable(drawable);
        mBlockLayout = false;
    }

    @Override
    public void draw(Canvas canvas) {
        // For security reasons if it was recycled and we do not
        // know it. This should never happen. So just in case.
        Drawable drawable = getDrawable();
        if (drawable != null && drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
            if (bitmap != null && bitmap.isRecycled()) {
                return;
            }
        }
        super.draw(canvas);
    }

}

