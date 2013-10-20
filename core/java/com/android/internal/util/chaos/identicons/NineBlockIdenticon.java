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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Adapted from Don Park's identicon work at
 * https://github.com/donpark/identicon
 */
@ChaosLab(name="QuickStats", classification=Classification.NEW_CLASS)
public class NineBlockIdenticon extends Identicon {

	/*
	 * Each patch is a polygon created from a list of vertices on a 5 by 5 grid.
	 * Vertices are numbered from 0 to 24, starting from top-left corner of the
	 * grid, moving left to right and top to bottom.
	 */
    private static final int PATCH_GRIDS = 5;
    private static final byte PATCH_SYMMETRIC = 1;
    private static final byte PATCH_INVERTED = 2;
    private static final int PATCH_MOVETO = -1;
    private static final byte[] patch0 = { 0, 4, 24, 20 };
    private static final byte[] patch1 = { 0, 4, 20 };
    private static final byte[] patch2 = { 2, 24, 20 };
    private static final byte[] patch3 = { 0, 2, 20, 22 };
    private static final byte[] patch4 = { 2, 14, 22, 10 };
    private static final byte[] patch5 = { 0, 14, 24, 22 };
    private static final byte[] patch6 = { 2, 24, 22, 13, 11, 22, 20 };
    private static final byte[] patch7 = { 0, 14, 22 };
    private static final byte[] patch8 = { 6, 8, 18, 16 };
    private static final byte[] patch9 = { 4, 20, 10, 12, 2 };
    private static final byte[] patch10 = { 0, 2, 12, 10 };
    private static final byte[] patch11 = { 10, 14, 22 };
    private static final byte[] patch12 = { 20, 12, 24 };
    private static final byte[] patch13 = { 10, 2, 12 };
    private static final byte[] patch14 = { 0, 2, 10 };
    private static final byte[] patchTypes[] = { patch0, patch1, patch2,
            patch3, patch4, patch5, patch6, patch7, patch8, patch9, patch10,
            patch11, patch12, patch13, patch14, patch0 };
    private static final byte patchFlags[] = { PATCH_SYMMETRIC, 0, 0, 0,
            PATCH_SYMMETRIC, 0, 0, 0, PATCH_SYMMETRIC, 0, 0, 0, 0, 0, 0,
            PATCH_SYMMETRIC + PATCH_INVERTED };
    private static int centerPatchTypes[] = { 0, 4, 8, 15 };
    private float patchSize;
    private Path[] patchShapes;
    // used to center patch shape at origin because shape rotation works
    // correctly.
    private float patchOffset;

    /**
     * Generates a 3x3 patched identicon bitmap using the provided hash
     * @param hash A 16 byte hash used to generate the identicon
     * @return The bitmap of the identicon created
     */
    @Override
    public Bitmap generateIdenticonBitmap(byte[] hash) {
        return render(new BigInteger(1, hash), 96);
    }

    /**
     * Generates a 3x3 patched identicon bitmap, as a byte array, using the provided hash
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

    /**
     * Set the size in pixels at which each patch will be rendered before they
     * are scaled down to requested identicon size. Default size is 32 pixels
     * which means, for 9-block identicon, a 96x96 image will be rendered and
     * scaled down.
     *
     * @param size
     *            patch size in pixels
     */
    private void setPatchSize(float size) {
        this.patchSize = size;
        this.patchOffset = patchSize / 2.0f; // used to center patch shape at
        float patchScale = patchSize / 4.0f;
        // origin.
        this.patchShapes = new Path[patchTypes.length];
        for (int i = 0; i < patchTypes.length; i++) {
            Path patch = new Path();
            boolean moveTo = true;
            byte[] patchVertices = patchTypes[i];
            for (int j = 0; j < patchVertices.length; j++) {
                int v = (int) patchVertices[j];
                if (v == PATCH_MOVETO)
                    moveTo = true;
                float vx = ((v % PATCH_GRIDS) * patchScale) - patchOffset;
                float vy = ((float) Math.floor(((float) v) / PATCH_GRIDS))
                        * patchScale - patchOffset;
                if (!moveTo) {
                    patch.lineTo(vx, vy);
                } else {
                    moveTo = false;
                    patch.moveTo(vx, vy);
                }
            }
            patch.close();
            this.patchShapes[i] = patch;
        }
    }

    public Bitmap render(BigInteger code, int size) {
        setPatchSize(size / 3);
        return renderQuilt(code.intValue(), size);
    }

    protected Bitmap renderQuilt(int code, int size) {
        // -------------------------------------------------
        // PREPARE
        //

        // decode the code into parts
        // bit 0-1: middle patch type
        // bit 2: middle invert
        // bit 3-6: corner patch type
        // bit 7: corner invert
        // bit 8-9: corner turns
        // bit 10-13: side patch type
        // bit 14: side invert
        // bit 15: corner turns
        // bit 16-20: blue color component
        // bit 21-26: green color component
        // bit 27-31: red color component
        int middleType = centerPatchTypes[code & 0x3];
        boolean middleInvert = ((code >> 2) & 0x1) != 0;
        int cornerType = (code >> 3) & 0x0f;
        boolean cornerInvert = ((code >> 7) & 0x1) != 0;
        int cornerTurn = (code >> 8) & 0x3;
        int sideType = (code >> 10) & 0x0f;
        boolean sideInvert = ((code >> 14) & 0x1) != 0;
        int sideTurn = (code >> 15) & 0x3;
        int blue = (code >> 16) & 0x01f;
        int green = (code >> 21) & 0x01f;
        int red = (code >> 27) & 0x01f;

        // color components are used at top of the range for color difference
        // use white background for now.
        // TODO: support transparency.
        int strokeColor = Color.rgb(red << 3, green << 3, blue << 3);

        // outline shapes with a noticeable color (complementary will do) if
        // shape color and background color are too similar (measured by color
        // distance).
        int fillColor = 0;
        if (getColorDistance(strokeColor, mBackgroundColor) < 32.0f)
            strokeColor = getComplementaryColor(strokeColor);

        // -------------------------------------------------
        // RENDER
        //

        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(mBackgroundColor);
        float blockSize = size / 3.0f;
        float blockSize2 = blockSize * 2.0f;

        // middle patch
        drawPatch(canvas, blockSize, blockSize, blockSize, middleType, 0,
                middleInvert, fillColor, strokeColor);

        // side patchs, starting from top and moving clock-wise
        drawPatch(canvas, blockSize, 0, blockSize, sideType, sideTurn++, sideInvert,
                fillColor, strokeColor);
        drawPatch(canvas, blockSize2, blockSize, blockSize, sideType, sideTurn++,
                sideInvert, fillColor, strokeColor);
        drawPatch(canvas, blockSize, blockSize2, blockSize, sideType, sideTurn++,
                sideInvert, fillColor, strokeColor);
        drawPatch(canvas, 0, blockSize, blockSize, sideType, sideTurn++, sideInvert,
                fillColor, strokeColor);

        // corner patchs, starting from top left and moving clock-wise
        drawPatch(canvas, 0, 0, blockSize, cornerType, cornerTurn++, cornerInvert,
                fillColor, strokeColor);
        drawPatch(canvas, blockSize2, 0, blockSize, cornerType, cornerTurn++,
                cornerInvert, fillColor, strokeColor);
        drawPatch(canvas, blockSize2, blockSize2, blockSize, cornerType,
                cornerTurn++, cornerInvert, fillColor, strokeColor);
        drawPatch(canvas, 0, blockSize2, blockSize, cornerType, cornerTurn++,
                cornerInvert, fillColor, strokeColor);

        return bmp;
    }

    private void drawPatch(Canvas c, float x, float y, float size,
                           int patch, int turn, boolean invert, int fillColor,
                           int strokeColor) {
        assert patch >= 0;
        assert turn >= 0;
        patch %= patchTypes.length;
        turn %= 4;
        if ((patchFlags[patch] & PATCH_INVERTED) != 0)
            invert = !invert;

        Path shape = patchShapes[patch];
        float scale = size /  patchSize;
        float offset = size / 2.0f;

        // setup our paint
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setAntiAlias(true);
        p.setFilterBitmap(true);

        // save the current matrix
        c.save();
        c.translate(x + offset, y + offset);
        c.scale(scale, scale);
        c.rotate(turn * 90f);

        // draw the patch
        p.setColor(strokeColor);
        c.drawPath(shape, p);

        // restore the saved matrix
        c.restore();
    }
}

