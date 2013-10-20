/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
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

package com.android.internal.util.chaos.identicons;

import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;

@ChaosLab(name="QuickStats", classification=Classification.NEW_CLASS)
public class SpirographIdenticon extends Identicon {

    private static final int DEFAULT_SIZE = 96;
    private static final float STEP_SIZE = (float) Math.PI / 50;
    private static final float TWO_PI = (float) Math.PI * 2;

    @Override
    public Bitmap generateIdenticonBitmap(byte[] hash) {
        if (hash.length != 16)
            return null;

        final int size = DEFAULT_SIZE;
        final float sizeDiv2 = size / 2f;
        float innerRadius = sizeDiv2 / 2f;
        float outerRadius = innerRadius / 2 + 1;
        float point1 = (0.5f - (float)hash[0] / 255f) * (size - (innerRadius + outerRadius));
        float point2 = (0.5f - (float)hash[1] / 255f) * (size - (innerRadius + outerRadius));
        float point3 = (0.5f - (float)hash[2] / 255f) * (size - (innerRadius + outerRadius));

        int color1 = Color.rgb(hash[15], hash[14], hash[13]);
        if (getColorDistance(color1, mBackgroundColor) <= 32.0) {
            color1 = getComplementaryColor(color1);
        }
        int color2 = Color.rgb(hash[12], hash[11], hash[10]);
        if (getColorDistance(color2, mBackgroundColor) <= 32.0) {
            color2 = getComplementaryColor(color2);
        }
        int color3 = Color.rgb(hash[9], hash[8], hash[7]);
        if (getColorDistance(color3, mBackgroundColor) <= 32.0) {
            color3 = getComplementaryColor(color3);
        }

        Path point1Path= new Path();
        Path point2Path= new Path();
        Path point3Path= new Path();
        boolean moveTo = true;
        int revolutions = Math.max(5, hash[3] >> 2);
        float t = 0f;
        float x, y, x2, y2, x3, y3;
        do {
            x = (float)((innerRadius + outerRadius) * Math.cos(t) +
                    point1 * Math.cos((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);
            y = (float)((innerRadius + outerRadius) * Math.sin(t) +
                    point1 * Math.sin((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);
            x2 = (float)((innerRadius + outerRadius) * Math.cos(t) +
                    point2 * Math.cos((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);
            y2 = (float)((innerRadius + outerRadius) * Math.sin(t) +
                    point2 * Math.sin((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);
            x3 = (float)((innerRadius + outerRadius) * Math.cos(t) +
                    point3 * Math.cos((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);
            y3 = (float)((innerRadius + outerRadius) * Math.sin(t) +
                    point3 * Math.sin((innerRadius + outerRadius) * t / outerRadius) + sizeDiv2);

            if (moveTo) {
                point1Path.moveTo(x, y);
                point2Path.moveTo(x2, y2);
                point3Path.moveTo(x3, y3);
                moveTo = false;
            } else {
                point1Path.lineTo(x, y);
                point2Path.lineTo(x2, y2);
                point3Path.lineTo(x3, y3);
            }
            t += STEP_SIZE;
        } while (t < TWO_PI * revolutions);

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(mBackgroundColor);
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setAntiAlias(true);
        p.setFilterBitmap(true);

        // draw the first path
        p.setColor(color1);
        canvas.drawPath(point1Path, p);
        // draw the second path
        p.setColor(color2);
        canvas.drawPath(point2Path, p);
        // draw the third path
        p.setColor(color3);
        canvas.drawPath(point3Path, p);

        return bmp;
    }

    @Override
    public byte[] generateIdenticonByteArray(byte[] hash) {
        return bitmapToByteArray(generateIdenticonBitmap(hash));
    }

    @Override
    public Bitmap generateIdenticonBitmap(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        return generateIdenticonBitmap(generateHash(saltedKey(key)));
    }

    @Override
    public byte[] generateIdenticonByteArray(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        return generateIdenticonByteArray(generateHash(saltedKey(key)));
    }

    private static boolean isOddParity(final byte b) {
        int bb = b;
        int bitCount = 0;
        for (int i = 0; i < 8; i++, bb >>= 1) {
            if ((bb & 1) != 0) {
                bitCount++;
            }
        }

        return (bitCount & 1) != 0;
    }
}

