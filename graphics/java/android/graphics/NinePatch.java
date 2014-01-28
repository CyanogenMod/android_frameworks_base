/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.graphics;


/**
 * The NinePatch class permits drawing a bitmap in nine or more sections.
 * Essentially, it allows the creation of custom graphics that will scale the
 * way that you define, when content added within the image exceeds the normal
 * bounds of the graphic. For a thorough explanation of a NinePatch image,
 * read the discussion in the
 * <a href="{@docRoot}guide/topics/graphics/2d-graphics.html#nine-patch">2D
 * Graphics</a> document.
 * <p>
 * The <a href="{@docRoot}guide/developing/tools/draw9patch.html">Draw 9-Patch</a>
 * tool offers an extremely handy way to create your NinePatch images,
 * using a WYSIWYG graphics editor.
 * </p>
 */
public class NinePatch {
    private final Bitmap mBitmap;

    /**
     * Used by native code. This pointer is an instance of Res_png_9patch*.
     *
     * @hide
     */
    public final int mNativeChunk;

    private Paint mPaint;
    private String mSrcName;

    /**
     * Create a drawable projection from a bitmap to nine patches.
     *
     * @param bitmap The bitmap describing the patches.
     * @param chunk The 9-patch data chunk describing how the underlying bitmap
     *              is split apart and drawn.
     */
    public NinePatch(Bitmap bitmap, byte[] chunk) {
        this(bitmap, chunk, null);
    }

    /**
     * Create a drawable projection from a bitmap to nine patches.
     *
     * @param bitmap The bitmap describing the patches.
     * @param chunk The 9-patch data chunk describing how the underlying
     *              bitmap is split apart and drawn.
     * @param srcName The name of the source for the bitmap. Might be null.
     */
    public NinePatch(Bitmap bitmap, byte[] chunk, String srcName) {
        mBitmap = bitmap;
        mSrcName = srcName;
        mNativeChunk = validateNinePatchChunk(mBitmap.ni(), chunk);
    }

    /**
     * @hide
     */
    public NinePatch(NinePatch patch) {
        mBitmap = patch.mBitmap;
        mSrcName = patch.mSrcName;
        if (patch.mPaint != null) {
            mPaint = new Paint(patch.mPaint);
        }
        // No need to validate the 9patch chunk again, it was done by
        // the instance we're copying from
        mNativeChunk = patch.mNativeChunk;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            nativeFinalize(mNativeChunk);
        } finally {
            super.finalize();
        }
    }

    /**
     * Returns the name of this NinePatch object if one was specified
     * when calling the constructor.
     */
    public String getName() {
        return mSrcName;
    }

    /**
     * Returns the paint used to draw this NinePatch. The paint can be null.
     *
     * @see #setPaint(Paint)
     * @see #draw(Canvas, Rect)
     * @see #draw(Canvas, RectF)
     */
    public Paint getPaint() {
        return mPaint;
    }

    /**
     * Sets the paint to use when drawing the NinePatch.
     *
     * @param p The paint that will be used to draw this NinePatch.
     *
     * @see #getPaint()
     * @see #draw(Canvas, Rect)
     * @see #draw(Canvas, RectF)
     */
    public void setPaint(Paint p) {
        mPaint = p;
    }

    /**
     * Returns the bitmap used to draw this NinePatch.
     */
    public Bitmap getBitmap() {
        return mBitmap;
    }

    /**
     * Draws the NinePatch. This method will use the paint returned by {@link #getPaint()}.
     *
     * @param canvas A container for the current matrix and clip used to draw the NinePatch.
     * @param location Where to draw the NinePatch.
     */
    public void draw(Canvas canvas, RectF location) {
        canvas.drawPatch(this, location, mPaint);
    }

    /**
     * Draws the NinePatch. This method will use the paint returned by {@link #getPaint()}.
     *
     * @param canvas A container for the current matrix and clip used to draw the NinePatch.
     * @param location Where to draw the NinePatch.
     */
    public void draw(Canvas canvas, Rect location) {
        canvas.drawPatch(this, location, mPaint);
    }

    /**
     * Draws the NinePatch. This method will ignore the paint returned
     * by {@link #getPaint()} and use the specified paint instead.
     *
     * @param canvas A container for the current matrix and clip used to draw the NinePatch.
     * @param location Where to draw the NinePatch.
     * @param paint The Paint to draw through.
     */
    public void draw(Canvas canvas, Rect location, Paint paint) {
        canvas.drawPatch(this, location, paint);
    }

    void drawSoftware(Canvas canvas, RectF location, Paint paint) {
        nativeDraw(canvas.mNativeCanvas, location, mBitmap.ni(), mNativeChunk,
                paint != null ? paint.mNativePaint : 0, canvas.mDensity, mBitmap.mDensity);
    }

    void drawSoftware(Canvas canvas, Rect location, Paint paint) {
        nativeDraw(canvas.mNativeCanvas, location, mBitmap.ni(), mNativeChunk,
                paint != null ? paint.mNativePaint : 0, canvas.mDensity, mBitmap.mDensity);
    }

    /**
     * Return the underlying bitmap's density, as per
     * {@link Bitmap#getDensity() Bitmap.getDensity()}.
     */
    public int getDensity() {
        return mBitmap.mDensity;
    }

    /**
     * Returns the intrinsic width, in pixels, of this NinePatch. This is equivalent
     * to querying the width of the underlying bitmap returned by {@link #getBitmap()}.
     */
    public int getWidth() {
        return mBitmap.getWidth();
    }

    /**
     * Returns the intrinsic height, in pixels, of this NinePatch. This is equivalent
     * to querying the height of the underlying bitmap returned by {@link #getBitmap()}.
     */
    public int getHeight() {
        return mBitmap.getHeight();
    }

    /**
     * Indicates whether this NinePatch contains transparent or translucent pixels.
     * This is equivalent to calling <code>getBitmap().hasAlpha()</code> on this
     * NinePatch.
     */
    public final boolean hasAlpha() {
        return mBitmap.hasAlpha();
    }

    /**
     * Returns a {@link Region} representing the parts of the NinePatch that are
     * completely transparent.
     *
     * @param bounds The location and size of the NinePatch.
     *
     * @return null if the NinePatch has no transparent region to
     * report, else a {@link Region} holding the parts of the specified bounds
     * that are transparent.
     */
    public final Region getTransparentRegion(Rect bounds) {
        int r = nativeGetTransparentRegion(mBitmap.ni(), mNativeChunk, bounds);
        return r != 0 ? new Region(r) : null;
    }

    /**
     * Verifies that the specified byte array is a valid 9-patch data chunk.
     *
     * @param chunk A byte array representing a 9-patch data chunk.
     *
     * @return True if the specified byte array represents a 9-patch data chunk,
     *         false otherwise.
     */
    public native static boolean isNinePatchChunk(byte[] chunk);

    /**
     * Validates the 9-patch chunk and throws an exception if the chunk is invalid.
     * If validation is successful, this method returns a native Res_png_9patch*
     * object used by the renderers.
     */
    private static native int validateNinePatchChunk(int bitmap, byte[] chunk);
    private static native void nativeFinalize(int chunk);
    private static native void nativeDraw(int canvas_instance, RectF loc, int bitmap_instance,
            int c, int paint_instance_or_null, int destDensity, int srcDensity);
    private static native void nativeDraw(int canvas_instance, Rect loc, int bitmap_instance,
            int c, int paint_instance_or_null, int destDensity, int srcDensity);
    private static native int nativeGetTransparentRegion(int bitmap, int chunk, Rect location);
}
