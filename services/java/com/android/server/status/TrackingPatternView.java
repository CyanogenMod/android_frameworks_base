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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;


public class TrackingPatternView extends View {
    private Bitmap mTexture;
    private Paint mPaint;
    private int mTextureWidth;
    private int mTextureHeight;
    
    public TrackingPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTexture();
        mTextureWidth = mTexture.getWidth();
        mTextureHeight = mTexture.getHeight();

        mPaint = new Paint();
        mPaint.setDither(false);
    }

    @Override
    public void onDraw(Canvas canvas) {
        final Bitmap texture = mTexture;
        final Paint paint = mPaint;

        final int width = getWidth();
        final int height = getHeight();

        final int textureWidth = mTextureWidth;
        final int textureHeight = mTextureHeight;

        int x = 0;
        int y;

        while (x < width) {
            y = 0;
            while (y < height) {
                canvas.drawBitmap(texture, x, y, paint);
                y += textureHeight;
            }
            x += textureWidth;
        }
    }
    
    private void setTexture() {
    	boolean useCustomExp = Settings.System.getInt(mContext.getContentResolver(),
       	        Settings.System.NOTIF_EXPANDED_BAR_CUSTOM, 0) == 1;
    	int expBarColorMask = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.NOTIF_EXPANDED_BAR_COLOR, 0xFF000000);
        if (useCustomExp) {
        	Bitmap tempbm = BitmapFactory.decodeResource(getResources(), 
                    com.android.internal.R.drawable.status_bar_background_cust);
        	Bitmap mutable = Bitmap.createBitmap(tempbm.getWidth(), tempbm.getHeight(), Bitmap.Config.ARGB_8888);
        	mutable.eraseColor(expBarColorMask);
        	mTexture = mutable;
            } else {
            mTexture = BitmapFactory.decodeResource(getResources(), 
                        com.android.internal.R.drawable.status_bar_background);
            }
    }
}
