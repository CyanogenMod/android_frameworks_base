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

package android.graphics;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Trace;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Creates Bitmap objects from various sources, including files, streams,
 * and byte-arrays.
 */
public class BitmapFactory {
    private static final int DECODE_BUFFER_SIZE = 16 * 1024;

    public static class Options {
        /**
         * Create a default Options object, which if left unchanged will give
         * the same result from the decoder as if null were passed.
         */
        public Options() {
            inDither = false;
            inScaled = true;
            inPremultiplied = true;
        }

        /**
         * If set, decode methods that take the Options object will attempt to
         * reuse this bitmap when loading content. If the decode operation
         * cannot use this bitmap, the decode method will return
         * <code>null</code> and will throw an IllegalArgumentException. The
         * current implementation necessitates that the reused bitmap be
         * mutable, and the resulting reused bitmap will continue to remain
         * mutable even when decoding a resource which would normally result in
         * an immutable bitmap.</p>
         *
         * <p>You should still always use the returned Bitmap of the decode
         * method and not assume that reusing the bitmap worked, due to the
         * constraints outlined above and failure situations that can occur.
         * Checking whether the return value matches the value of the inBitmap
         * set in the Options structure will indicate if the bitmap was reused,
         * but in all cases you should use the Bitmap returned by the decoding
         * function to ensure that you are using the bitmap that was used as the
         * decode destination.</p>
         *
         * <h3>Usage with BitmapFactory</h3>
         *
         * <p>As of {@link android.os.Build.VERSION_CODES#KITKAT}, any
         * mutable bitmap can be reused by {@link BitmapFactory} to decode any
         * other bitmaps as long as the resulting {@link Bitmap#getByteCount()
         * byte count} of the decoded bitmap is less than or equal to the {@link
         * Bitmap#getAllocationByteCount() allocated byte count} of the reused
         * bitmap. This can be because the intrinsic size is smaller, or its
         * size post scaling (for density / sample size) is smaller.</p>
         *
         * <p class="note">Prior to {@link android.os.Build.VERSION_CODES#KITKAT}
         * additional constraints apply: The image being decoded (whether as a
         * resource or as a stream) must be in jpeg or png format. Only equal
         * sized bitmaps are supported, with {@link #inSampleSize} set to 1.
         * Additionally, the {@link android.graphics.Bitmap.Config
         * configuration} of the reused bitmap will override the setting of
         * {@link #inPreferredConfig}, if set.</p>
         *
         * <h3>Usage with BitmapRegionDecoder</h3>
         *
         * <p>BitmapRegionDecoder will draw its requested content into the Bitmap
         * provided, clipping if the output content size (post scaling) is larger
         * than the provided Bitmap. The provided Bitmap's width, height, and
         * {@link Bitmap.Config} will not be changed.
         *
         * <p class="note">BitmapRegionDecoder support for {@link #inBitmap} was
         * introduced in {@link android.os.Build.VERSION_CODES#JELLY_BEAN}. All
         * formats supported by BitmapRegionDecoder support Bitmap reuse via
         * {@link #inBitmap}.</p>
         *
         * @see Bitmap#reconfigure(int,int, android.graphics.Bitmap.Config)
         */
        public Bitmap inBitmap;

        /**
         * If set, decode methods will always return a mutable Bitmap instead of
         * an immutable one. This can be used for instance to programmatically apply
         * effects to a Bitmap loaded through BitmapFactory.
         */
        @SuppressWarnings({"UnusedDeclaration"}) // used in native code
        public boolean inMutable;

        /**
         * If set to true, the decoder will return null (no bitmap), but
         * the out... fields will still be set, allowing the caller to query
         * the bitmap without having to allocate the memory for its pixels.
         */
        public boolean inJustDecodeBounds;

        /**
         * If set to a value > 1, requests the decoder to subsample the original
         * image, returning a smaller image to save memory. The sample size is
         * the number of pixels in either dimension that correspond to a single
         * pixel in the decoded bitmap. For example, inSampleSize == 4 returns
         * an image that is 1/4 the width/height of the original, and 1/16 the
         * number of pixels. Any value <= 1 is treated the same as 1. Note: the
         * decoder uses a final value based on powers of 2, any other value will
         * be rounded down to the nearest power of 2.
         */
        public int inSampleSize;

        /**
         * If this is non-null, the decoder will try to decode into this
         * internal configuration. If it is null, or the request cannot be met,
         * the decoder will try to pick the best matching config based on the
         * system's screen depth, and characteristics of the original image such
         * as if it has per-pixel alpha (requiring a config that also does).
         * 
         * Image are loaded with the {@link Bitmap.Config#ARGB_8888} config by
         * default.
         */
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;

        /**
         * If true (which is the default), the resulting bitmap will have its
         * color channels pre-multipled by the alpha channel.
         *
         * <p>This should NOT be set to false for images to be directly drawn by
         * the view system or through a {@link Canvas}. The view system and
         * {@link Canvas} assume all drawn images are pre-multiplied to simplify
         * draw-time blending, and will throw a RuntimeException when
         * un-premultiplied are drawn.</p>
         *
         * <p>This is likely only useful if you want to manipulate raw encoded
         * image data, e.g. with RenderScript or custom OpenGL.</p>
         *
         * <p>This does not affect bitmaps without an alpha channel.</p>
         *
         * <p>Setting this flag to false while setting {@link #inScaled} to true
         * may result in incorrect colors.</p>
         *
         * @see Bitmap#hasAlpha()
         * @see Bitmap#isPremultiplied()
         * @see #inScaled
         */
        public boolean inPremultiplied;

        /**
         * If dither is true, the decoder will attempt to dither the decoded
         * image.
         */
        public boolean inDither;

        /**
         * The pixel density to use for the bitmap.  This will always result
         * in the returned bitmap having a density set for it (see
         * {@link Bitmap#setDensity(int) Bitmap.setDensity(int)}).  In addition,
         * if {@link #inScaled} is set (which it is by default} and this
         * density does not match {@link #inTargetDensity}, then the bitmap
         * will be scaled to the target density before being returned.
         * 
         * <p>If this is 0,
         * {@link BitmapFactory#decodeResource(Resources, int)}, 
         * {@link BitmapFactory#decodeResource(Resources, int, android.graphics.BitmapFactory.Options)},
         * and {@link BitmapFactory#decodeResourceStream}
         * will fill in the density associated with the resource.  The other
         * functions will leave it as-is and no density will be applied.
         *
         * @see #inTargetDensity
         * @see #inScreenDensity
         * @see #inScaled
         * @see Bitmap#setDensity(int)
         * @see android.util.DisplayMetrics#densityDpi
         */
        public int inDensity;

        /**
         * The pixel density of the destination this bitmap will be drawn to.
         * This is used in conjunction with {@link #inDensity} and
         * {@link #inScaled} to determine if and how to scale the bitmap before
         * returning it.
         * 
         * <p>If this is 0,
         * {@link BitmapFactory#decodeResource(Resources, int)}, 
         * {@link BitmapFactory#decodeResource(Resources, int, android.graphics.BitmapFactory.Options)},
         * and {@link BitmapFactory#decodeResourceStream}
         * will fill in the density associated the Resources object's
         * DisplayMetrics.  The other
         * functions will leave it as-is and no scaling for density will be
         * performed.
         * 
         * @see #inDensity
         * @see #inScreenDensity
         * @see #inScaled
         * @see android.util.DisplayMetrics#densityDpi
         */
        public int inTargetDensity;
        
        /**
         * The pixel density of the actual screen that is being used.  This is
         * purely for applications running in density compatibility code, where
         * {@link #inTargetDensity} is actually the density the application
         * sees rather than the real screen density.
         * 
         * <p>By setting this, you
         * allow the loading code to avoid scaling a bitmap that is currently
         * in the screen density up/down to the compatibility density.  Instead,
         * if {@link #inDensity} is the same as {@link #inScreenDensity}, the
         * bitmap will be left as-is.  Anything using the resulting bitmap
         * must also used {@link Bitmap#getScaledWidth(int)
         * Bitmap.getScaledWidth} and {@link Bitmap#getScaledHeight
         * Bitmap.getScaledHeight} to account for any different between the
         * bitmap's density and the target's density.
         * 
         * <p>This is never set automatically for the caller by
         * {@link BitmapFactory} itself.  It must be explicitly set, since the
         * caller must deal with the resulting bitmap in a density-aware way.
         * 
         * @see #inDensity
         * @see #inTargetDensity
         * @see #inScaled
         * @see android.util.DisplayMetrics#densityDpi
         */
        public int inScreenDensity;
        
        /**
         * When this flag is set, if {@link #inDensity} and
         * {@link #inTargetDensity} are not 0, the
         * bitmap will be scaled to match {@link #inTargetDensity} when loaded,
         * rather than relying on the graphics system scaling it each time it
         * is drawn to a Canvas.
         *
         * <p>BitmapRegionDecoder ignores this flag, and will not scale output
         * based on density. (though {@link #inSampleSize} is supported)</p>
         *
         * <p>This flag is turned on by default and should be turned off if you need
         * a non-scaled version of the bitmap.  Nine-patch bitmaps ignore this
         * flag and are always scaled.
         *
         * <p>If {@link #inPremultiplied} is set to false, and the image has alpha,
         * setting this flag to true may result in incorrect colors.
         */
        public boolean inScaled;

        /**
         * @deprecated As of {@link android.os.Build.VERSION_CODES#LOLLIPOP}, this is
         * ignored.
         *
         * In {@link android.os.Build.VERSION_CODES#KITKAT} and below, if this
         * is set to true, then the resulting bitmap will allocate its
         * pixels such that they can be purged if the system needs to reclaim
         * memory. In that instance, when the pixels need to be accessed again
         * (e.g. the bitmap is drawn, getPixels() is called), they will be
         * automatically re-decoded.
         *
         * <p>For the re-decode to happen, the bitmap must have access to the
         * encoded data, either by sharing a reference to the input
         * or by making a copy of it. This distinction is controlled by
         * inInputShareable. If this is true, then the bitmap may keep a shallow
         * reference to the input. If this is false, then the bitmap will
         * explicitly make a copy of the input data, and keep that. Even if
         * sharing is allowed, the implementation may still decide to make a
         * deep copy of the input data.</p>
         *
         * <p>While inPurgeable can help avoid big Dalvik heap allocations (from
         * API level 11 onward), it sacrifices performance predictability since any
         * image that the view system tries to draw may incur a decode delay which
         * can lead to dropped frames. Therefore, most apps should avoid using
         * inPurgeable to allow for a fast and fluid UI. To minimize Dalvik heap
         * allocations use the {@link #inBitmap} flag instead.</p>
         *
         * <p class="note"><strong>Note:</strong> This flag is ignored when used
         * with {@link #decodeResource(Resources, int,
         * android.graphics.BitmapFactory.Options)} or {@link #decodeFile(String,
         * android.graphics.BitmapFactory.Options)}.</p>
         */
        @Deprecated
        public boolean inPurgeable;

        /**
         * @deprecated As of {@link android.os.Build.VERSION_CODES#LOLLIPOP}, this is
         * ignored.
         *
         * In {@link android.os.Build.VERSION_CODES#KITKAT} and below, this
         * field works in conjuction with inPurgeable. If inPurgeable is false,
         * then this field is ignored. If inPurgeable is true, then this field
         * determines whether the bitmap can share a reference to the input
         * data (inputstream, array, etc.) or if it must make a deep copy.
         */
        @Deprecated
        public boolean inInputShareable;

        /**
         * If inPreferQualityOverSpeed is set to true, the decoder will try to
         * decode the reconstructed image to a higher quality even at the
         * expense of the decoding speed. Currently the field only affects JPEG
         * decode, in the case of which a more accurate, but slightly slower,
         * IDCT method will be used instead.
         */
        public boolean inPreferQualityOverSpeed;

        /**
         * The resulting width of the bitmap. If {@link #inJustDecodeBounds} is
         * set to false, this will be width of the output bitmap after any
         * scaling is applied. If true, it will be the width of the input image
         * without any accounting for scaling.
         *
         * <p>outWidth will be set to -1 if there is an error trying to decode.</p>
         */
        public int outWidth;

        /**
         * The resulting height of the bitmap. If {@link #inJustDecodeBounds} is
         * set to false, this will be height of the output bitmap after any
         * scaling is applied. If true, it will be the height of the input image
         * without any accounting for scaling.
         *
         * <p>outHeight will be set to -1 if there is an error trying to decode.</p>
         */
        public int outHeight;

        /**
         * If known, this string is set to the mimetype of the decoded image.
         * If not know, or there is an error, it is set to null.
         */
        public String outMimeType;

        /**
         * Temp storage to use for decoding.  Suggest 16K or so.
         */
        public byte[] inTempStorage;

        private native void requestCancel();

        /**
         * Flag to indicate that cancel has been called on this object.  This
         * is useful if there's an intermediary that wants to first decode the
         * bounds and then decode the image.  In that case the intermediary
         * can check, inbetween the bounds decode and the image decode, to see
         * if the operation is canceled.
         */
        public boolean mCancel;

        /**
         *  This can be called from another thread while this options object is
         *  inside a decode... call. Calling this will notify the decoder that
         *  it should cancel its operation. This is not guaranteed to cancel
         *  the decode, but if it does, the decoder... operation will return
         *  null, or if inJustDecodeBounds is true, will set outWidth/outHeight
         *  to -1
         */
        public void requestCancelDecode() {
            mCancel = true;
            requestCancel();
        }
    }

    /**
     * Decode a file path into a bitmap. If the specified file name is null,
     * or cannot be decoded into a bitmap, the function returns null.
     *
     * @param pathName complete path name for the file to be decoded.
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     */
    public static Bitmap decodeFile(String pathName, Options opts) {
        Bitmap bm = null;
        InputStream stream = null;
        try {
            stream = new FileInputStream(pathName);
            bm = decodeStream(stream, null, opts);
        } catch (Exception e) {
            /*  do nothing.
                If the exception happened on open, bm will be null.
            */
            Log.e("BitmapFactory", "Unable to decode stream: " + e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // do nothing here
                }
            }
        }
        return bm;
    }

    /**
     * Decode a file path into a bitmap. If the specified file name is null,
     * or cannot be decoded into a bitmap, the function returns null.
     *
     * @param pathName complete path name for the file to be decoded.
     * @return the resulting decoded bitmap, or null if it could not be decoded.
     */
    public static Bitmap decodeFile(String pathName) {
        return decodeFile(pathName, null);
    }

    /**
     * Decode a new Bitmap from an InputStream. This InputStream was obtained from
     * resources, which we pass to be able to scale the bitmap accordingly.
     */
    public static Bitmap decodeResourceStream(Resources res, TypedValue value,
            InputStream is, Rect pad, Options opts) {
        if (opts == null) {
            opts = new Options();
        }

        if (opts.inDensity == 0 && value != null) {
            final int density = value.density;
            if (density == TypedValue.DENSITY_DEFAULT) {
                opts.inDensity = DisplayMetrics.DENSITY_DEFAULT;
            } else if (density != TypedValue.DENSITY_NONE) {
                opts.inDensity = density;
            }
        }
        
        if (opts.inTargetDensity == 0 && res != null) {
            opts.inTargetDensity = res.getDisplayMetrics().densityDpi;
        }
        
        return decodeStream(is, pad, opts);
    }

    /**
     * Synonym for opening the given resource and calling
     * {@link #decodeResourceStream}.
     *
     * @param res   The resources object containing the image data
     * @param id The resource id of the image data
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     */
    public static Bitmap decodeResource(Resources res, int id, Options opts) {
        Bitmap bm = null;
        InputStream is = null; 
        
        try {
            final TypedValue value = new TypedValue();
            is = res.openRawResource(id, value);

            bm = decodeResourceStream(res, value, is, null, opts);
        } catch (Exception e) {
            /*  do nothing.
                If the exception happened on open, bm will be null.
                If it happened on close, bm is still valid.
            */
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        if (bm == null && opts != null && opts.inBitmap != null) {
            throw new IllegalArgumentException("Problem decoding into existing bitmap");
        }

        return bm;
    }

    /**
     * Synonym for {@link #decodeResource(Resources, int, android.graphics.BitmapFactory.Options)}
     * with null Options.
     *
     * @param res The resources object containing the image data
     * @param id The resource id of the image data
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeResource(Resources res, int id) {
        return decodeResource(res, id, null);
    }

    /**
     * Decode an immutable bitmap from the specified byte array.
     *
     * @param data byte array of compressed image data
     * @param offset offset into imageData for where the decoder should begin
     *               parsing.
     * @param length the number of bytes, beginning at offset, to parse
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     */
    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        if ((offset | length) < 0 || data.length < offset + length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        Bitmap bm;

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "decodeBitmap");
        try {
            bm = nativeDecodeByteArray(data, offset, length, opts);

            if (bm == null && opts != null && opts.inBitmap != null) {
                throw new IllegalArgumentException("Problem decoding into existing bitmap");
            }
            setDensityFromOptions(bm, opts);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);
        }

        return bm;
    }

    /**
     * Decode an immutable bitmap from the specified byte array.
     *
     * @param data byte array of compressed image data
     * @param offset offset into imageData for where the decoder should begin
     *               parsing.
     * @param length the number of bytes, beginning at offset, to parse
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return decodeByteArray(data, offset, length, null);
    }
    /**
     * Set the newly decoded bitmap's density based on the Options.
     */
    private static void setDensityFromOptions(Bitmap outputBitmap, Options opts) {
        if (outputBitmap == null || opts == null) return;

        final int density = opts.inDensity;
        if (density != 0) {
            outputBitmap.setDensity(density);
            final int targetDensity = opts.inTargetDensity;
            if (targetDensity == 0 || density == targetDensity || density == opts.inScreenDensity) {
                return;
            }

            byte[] np = outputBitmap.getNinePatchChunk();
            final boolean isNinePatch = np != null && NinePatch.isNinePatchChunk(np);
            if (opts.inScaled || isNinePatch) {
                outputBitmap.setDensity(targetDensity);
            }
        } else if (opts.inBitmap != null) {
            // bitmap was reused, ensure density is reset
            outputBitmap.setDensity(Bitmap.getDefaultDensity());
        }
    }

    /**
     * Decode an input stream into a bitmap. If the input stream is null, or
     * cannot be used to decode a bitmap, the function returns null.
     * The stream's position will be where ever it was after the encoded data
     * was read.
     *
     * @param is The input stream that holds the raw data to be decoded into a
     *           bitmap.
     * @param outPadding If not null, return the padding rect for the bitmap if
     *                   it exists, otherwise set padding to [-1,-1,-1,-1]. If
     *                   no bitmap is returned (null) then padding is
     *                   unchanged.
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     *
     * <p class="note">Prior to {@link android.os.Build.VERSION_CODES#KITKAT},
     * if {@link InputStream#markSupported is.markSupported()} returns true,
     * <code>is.mark(1024)</code> would be called. As of
     * {@link android.os.Build.VERSION_CODES#KITKAT}, this is no longer the case.</p>
     */
    public static Bitmap decodeStream(InputStream is, Rect outPadding, Options opts) {
        // we don't throw in this case, thus allowing the caller to only check
        // the cache, and not force the image to be decoded.
        if (is == null) {
            return null;
        }

        Bitmap bm = null;

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "decodeBitmap");
        try {
            if (is instanceof AssetManager.AssetInputStream) {
                final long asset = ((AssetManager.AssetInputStream) is).getNativeAsset();
                bm = nativeDecodeAsset(asset, outPadding, opts);
            } else {
                bm = decodeStreamInternal(is, outPadding, opts);
            }

            if (bm == null && opts != null && opts.inBitmap != null) {
                throw new IllegalArgumentException("Problem decoding into existing bitmap");
            }

            setDensityFromOptions(bm, opts);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);
        }

        return bm;
    }

    /**
     * Private helper function for decoding an InputStream natively. Buffers the input enough to
     * do a rewind as needed, and supplies temporary storage if necessary. is MUST NOT be null.
     */
    private static Bitmap decodeStreamInternal(InputStream is, Rect outPadding, Options opts) {
        // ASSERT(is != null);
        byte [] tempStorage = null;
        if (opts != null) tempStorage = opts.inTempStorage;
        if (tempStorage == null) tempStorage = new byte[DECODE_BUFFER_SIZE];
        return nativeDecodeStream(is, tempStorage, outPadding, opts);
    }

    /**
     * Decode an input stream into a bitmap. If the input stream is null, or
     * cannot be used to decode a bitmap, the function returns null.
     * The stream's position will be where ever it was after the encoded data
     * was read.
     *
     * @param is The input stream that holds the raw data to be decoded into a
     *           bitmap.
     * @return The decoded bitmap, or null if the image data could not be decoded.
     */
    public static Bitmap decodeStream(InputStream is) {
        return decodeStream(is, null, null);
    }

    /**
     * Decode a bitmap from the file descriptor. If the bitmap cannot be decoded
     * return null. The position within the descriptor will not be changed when
     * this returns, so the descriptor can be used again as-is.
     *
     * @param fd The file descriptor containing the bitmap data to decode
     * @param outPadding If not null, return the padding rect for the bitmap if
     *                   it exists, otherwise set padding to [-1,-1,-1,-1]. If
     *                   no bitmap is returned (null) then padding is
     *                   unchanged.
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just its size returned.
     * @return the decoded bitmap, or null
     */
    public static Bitmap decodeFileDescriptor(FileDescriptor fd, Rect outPadding, Options opts) {
        Bitmap bm;

        Trace.traceBegin(Trace.TRACE_TAG_GRAPHICS, "decodeFileDescriptor");
        try {
            if (nativeIsSeekable(fd)) {
                bm = nativeDecodeFileDescriptor(fd, outPadding, opts);
            } else {
                FileInputStream fis = new FileInputStream(fd);
                try {
                    bm = decodeStreamInternal(fis, outPadding, opts);
                } finally {
                    try {
                        fis.close();
                    } catch (Throwable t) {/* ignore */}
                }
            }

            if (bm == null && opts != null && opts.inBitmap != null) {
                throw new IllegalArgumentException("Problem decoding into existing bitmap");
            }

            setDensityFromOptions(bm, opts);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);
        }
        return bm;
    }

    /**
     * @hide
     * Internal Drm API
     */
    public static Bitmap decodeDrmFile(String path, Options opts) {
        FileInputStream is = null;
        Bitmap bitmap = null;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            bitmap = nativeDecodeDrmFileDescriptor(fd, opts);
        } catch (IOException ioe) {
            Log.e("BitmapFactory", "Unable to decode drm file: " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e("BitmapFactory", "Unable to close drm file: " + e);
                }
            }
        }
        return bitmap;
    }

    /**
     * @hide
     * Internal Drm API
     */
    public static boolean consumeDrmImageRights(String path) {
        FileInputStream is = null;
        boolean result = false;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            result = nativeConsumeDrmImageRights(fd);
        } catch (IOException ioe) {
            Log.e("BitmapFactory", "Unable to decode drm file: " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e("BitmapFactory", "Unable to close drm file: " + e);
                }
            }
        }
        return result;
    }

    /**
     * @hide
     * Internal Drm API
     */
    public static byte[] getDrmImageBytes(String path) {
        FileInputStream is = null;
        byte[] result = null;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            result = nativeGetDrmImageBytes(fd);
        } catch (IOException ioe) {
            Log.e("BitmapFactory", "Unable to decode drm file: " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e("BitmapFactory", "Unable to close drm file: " + e);
                }
            }
        }
        return result;
    }

    /**
     * Decode a bitmap from the file descriptor. If the bitmap cannot be decoded
     * return null. The position within the descriptor will not be changed when
     * this returns, so the descriptor can be used again as is.
     *
     * @param fd The file descriptor containing the bitmap data to decode
     * @return the decoded bitmap, or null
     */
    public static Bitmap decodeFileDescriptor(FileDescriptor fd) {
        return decodeFileDescriptor(fd, null, null);
    }

    private static native Bitmap nativeDecodeStream(InputStream is, byte[] storage,
            Rect padding, Options opts);
    private static native Bitmap nativeDecodeFileDescriptor(FileDescriptor fd,
            Rect padding, Options opts);
    private static native Bitmap nativeDecodeAsset(long asset, Rect padding, Options opts);
    private static native Bitmap nativeDecodeByteArray(byte[] data, int offset,
            int length, Options opts);
    private static native boolean nativeIsSeekable(FileDescriptor fd);
    private static native Bitmap nativeDecodeDrmFileDescriptor(FileDescriptor fd, Options opts);
    private static native boolean nativeConsumeDrmImageRights(FileDescriptor fd);
    private static native byte[] nativeGetDrmImageBytes(FileDescriptor fd);
}
