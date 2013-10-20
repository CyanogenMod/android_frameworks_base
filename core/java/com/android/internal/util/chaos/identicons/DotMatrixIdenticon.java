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
import android.text.TextUtils;

import java.security.MessageDigest;

@ChaosLab(name="QuickStats", classification=Classification.NEW_CLASS)
public class DotMatrixIdenticon extends Identicon {
    @Override
    public Bitmap generateIdenticonBitmap(byte[] hash) {
        if (hash.length < 16)
            return null;

        Bitmap bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(mBackgroundColor);

        int blue = (hash[13] & 0x01f) << 3;
        int green = (hash[14] & 0x01f) << 3;
        int red = (hash[15] & 0x01f) << 3;
        int color = Color.rgb(red, green, blue);
        if (getColorDistance(color, mBackgroundColor) <= 64.0) {
            color = getComplementaryColor(color);
        }

        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setAntiAlias(true);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                final int index = y * 5 + x;
                float radius;
                if ((index & 1) == 0) {
                    radius = hash[index/2] & 0x0F;
                } else {
                    radius = (hash[index/2] >> 4) & 0x0F;
                }
                canvas.drawCircle(x * 16 + 8, y * 16 + 8, radius, p);
            }
        }
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
}

