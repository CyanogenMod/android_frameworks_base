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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;

@ChaosLab(name="QuickStats", classification=Classification.NEW_CLASS)
public class RetroIdenticon extends Identicon {

    /**
     * Generates a 5x5 identicon bitmap using the provided hash
     * @param hash A 16 byte hash used to generate the identicon
     * @return The bitmap of the identicon created
     */
    @Override
    public Bitmap generateIdenticonBitmap(byte[] hash) {
        if (hash.length != 16)
            return null;

        Bitmap bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(mBackgroundColor);
        int color = generateColor(hash);
        if (getColorDistance(color, mBackgroundColor) <= 64.0) {
            color = getComplementaryColor(color);
        }
        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 3; x++) {
                final int index = y * 3 + x;
                if (isOddParity(hash[index])) {
                    drawSquare(canvas, x, y, 16, p);
                    if (x == 0) drawSquare(canvas, 4, y, 16, p);
                    if (x == 1) drawSquare(canvas, 3, y, 16, p);
                }
            }
        }
        return bmp;
    }

    /**
     * Generates a 5x5 identicon bitmap, as a byte array, using the provided hash
     * @param hash A 16 byte hash used to generate the identicon
     * @return The bitmap byte array of the identicon created
     */
    @Override
    public byte[] generateIdenticonByteArray(byte[] hash) {
        return bitmapToByteArray(generateIdenticonBitmap(hash));
    }

    /**
     * Generates an identicon bitmap using the provided key to generate a hash
     * @param key A non empty string used to generate a hash when creating the identicon
     * @return The bitmap of the identicon created
     */
    @Override
    public Bitmap generateIdenticonBitmap(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        return generateIdenticonBitmap(generateHash(saltedKey(key)));
    }

    /**
     * Generates an identicon bitmap, as a byte array, using the provided key to generate a hash
     * @param key A non empty string used to generate a hash when creating the identicon
     * @return The bitmap byte array of the identicon created
     */
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

    private static int generateColor(byte[] hash) {
        if (hash.length != 16)
            return 0xFF000000;
        final int r = hash[15] + hash[4] + hash[13] + hash[2] + hash[11] / 5;
        final int g = hash[10] + hash[14] + hash[8] + hash[12] + hash[6] / 5;
        final int b = hash[5] + hash[9] + hash[3] + hash[7] + hash[1] / 5;
        return Color.argb(255, r, g, b);
    }

    private static void drawSquare(Canvas canvas, int x, int y, int size, Paint paint) {
        final int yOffset = y * size;
        final int xOffset = x * size;
        canvas.drawRect(xOffset, yOffset, xOffset + size, yOffset + size, paint);
    }
}

