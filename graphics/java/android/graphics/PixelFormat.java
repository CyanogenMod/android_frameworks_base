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

public class PixelFormat
{
    /* these constants need to match those in hardware/hardware.h */
    
    public static final int UNKNOWN     = 0;

    /** System chooses a format that supports translucency (many alpha bits) */
    public static final int TRANSLUCENT = -3;

    /** 
     * System chooses a format that supports transparency
     * (at least 1 alpha bit) 
     */    
    public static final int TRANSPARENT = -2;

    /** System chooses an opaque format (no alpha bits required) */
    public static final int OPAQUE      = -1;

    public static final int RGBA_8888   = 1;
    public static final int RGBX_8888   = 2;
    public static final int RGB_888     = 3;
    public static final int RGB_565     = 4;

    @Deprecated
    public static final int RGBA_5551   = 6;
    @Deprecated
    public static final int RGBA_4444   = 7;
    public static final int A_8         = 8;
    public static final int L_8         = 9;
    @Deprecated
    public static final int LA_88       = 0xA;
    @Deprecated
    public static final int RGB_332     = 0xB;


    /**
     * @deprecated use {@link android.graphics.ImageFormat#NV16 
     * ImageFormat.NV16} instead.
     */
    @Deprecated
    public static final int YCbCr_422_SP= 0x10;

    /**
     * @deprecated use {@link android.graphics.ImageFormat#NV21 
     * ImageFormat.NV21} instead.
     */
    @Deprecated
    public static final int YCbCr_420_SP= 0x11;

    /**
     * @deprecated use {@link android.graphics.ImageFormat#YUY2 
     * ImageFormat.YUY2} instead.
     */
    @Deprecated
    public static final int YCbCr_422_I = 0x14;

    /**
     * @deprecated use {@link android.graphics.ImageFormat#JPEG 
     * ImageFormat.JPEG} instead.
     */
    @Deprecated
    public static final int JPEG        = 0x100;

    public static final int VIDEO_HOLE  = 0x101;

    /*
     * We use a class initializer to allow the native code to cache some
     * field offsets.
     */
    native private static void nativeClassInit();
    static { nativeClassInit(); }

    public static native void getPixelFormatInfo(int format, PixelFormat info);
    public static boolean formatHasAlpha(int format) {
        switch (format) {
            case PixelFormat.A_8:
            case PixelFormat.LA_88:
            case PixelFormat.RGBA_4444:
            case PixelFormat.RGBA_5551:
            case PixelFormat.RGBA_8888:
            case PixelFormat.TRANSLUCENT:
            case PixelFormat.TRANSPARENT:
                return true;
        }
        return false;
    }
    
    public int  bytesPerPixel;
    public int  bitsPerPixel;
}
