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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.zip.CRC32;

@ChaosLab(name="QuickStats", classification=Classification.NEW_CLASS)
public abstract class Identicon {

    public static final String IDENTICON_MARKER = "identicon_marker";

    public static final String DEFAULT_IDENTICON_SALT =
            "zG~v(+&>fLX|!#9D*BTj*#K>amB&TUB}T/jBOQih|Sg8}@N-^Rk|?VEXI,9EQPH]";

    protected int mBackgroundColor = 0xFFDDDDDD;

    /**
     * Generates an identicon bitmap using the provided hash
     * @param hash A 16 byte hash used to generate the identicon
     * @return The bitmap of the identicon created
     */
    public abstract Bitmap generateIdenticonBitmap(byte[] hash);

    /**
     * Generates an identicon bitmap, as a byte array, using the provided hash
     * @param hash A 16 byte hash used to generate the identicon
     * @return The bitmap byte array of the identicon created
     */
    public abstract byte[] generateIdenticonByteArray(byte[] hash);

    /**
     * Generates an identicon bitmap using the provided key to generate a hash
     * @param key A non empty string used to generate a hash when creating the identicon
     * @return The bitmap of the identicon created
     */
    public abstract Bitmap generateIdenticonBitmap(String key);

    /**
     * Generates an identicon bitmap, as a byte array, using the provided key to generate a hash
     * @param key A non empty string used to generate a hash when creating the identicon
     * @return The bitmap byte array of the identicon created
     */
    public abstract byte[] generateIdenticonByteArray(String key);

    /**
     * Provides an MD5 sum for the given input string.
     * @param input
     * @return
     */
    public byte[] generateHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(input.getBytes());
        } catch (Exception e) {
            return null;
        }
    }

    public String saltedKey(String key) {
        return DEFAULT_IDENTICON_SALT + key + DEFAULT_IDENTICON_SALT;
    }

    /**
     * Adds a comment block to the end of a byte array containing a jpg image
     * @param original The jpg image to add the comment to
     * @return The same image provided with the added comment block
     */
    protected static byte[] makeTaggedIdenticon(byte[] original) {
        byte[] taggedBlock = makeTextBlock(IDENTICON_MARKER);
        byte[] taggedImage = new byte[original.length + taggedBlock.length];
        ByteBuffer buffer = ByteBuffer.wrap(taggedImage);
        buffer.put(original, 0, original.length - 2);
        buffer.put(taggedBlock);
        buffer.put(original, original.length - 2, 2);
        return taggedImage;
    }

    private static byte[] makeTextBlock(String text) {
        byte[] block = new byte[text.length() + 5];
        ByteBuffer blockBuffer = ByteBuffer.wrap(block);
        final int length = text.length();
        // block type, which is 0xFFFE for comment block
        blockBuffer.putShort((short) 0xFFFE);
        // next two bytes is string length
        blockBuffer.putShort((short) length);
        // followed by a null
        blockBuffer.put((byte) 0);
        // and finally our string
        blockBuffer.put(text.getBytes());

        return block;
    }

    protected static byte[] bitmapToByteArray(Bitmap bmp) {
        if (bmp == null) return null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bytes = stream.toByteArray();
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bytes != null) {
            return makeTaggedIdenticon(bytes);
        }

        return bytes;
    }

    /**
     * Returns distance between two colors.
     *
     * @param c1
     * @param c2
     * @return
     */
    protected float getColorDistance(int c1, int c2) {
        float dr = Color.red(c1) - Color.red(c2);
        float dg = Color.green(c1) - Color.green(c2);
        float db = Color.blue(c1) - Color.blue(c2);
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Returns complementary color.
     *
     * @param color
     * @return
     */
    protected int getComplementaryColor(int color) {
        return color ^ 0x00FFFFFF;
    }
}

