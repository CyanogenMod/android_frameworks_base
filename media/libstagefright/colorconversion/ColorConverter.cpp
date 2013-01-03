/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

//#define LOG_NDEBUG 0
#define LOG_TAG "ColorConverter"
#include <utils/Log.h>

#include <media/stagefright/ColorConverter.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaErrors.h>

#ifdef QCOM_HARDWARE
#include <dlfcn.h>
#include <OMX_QCOMExtns.h>
#include <QOMX_AudioExtensions.h>
#endif

namespace android {

ColorConverter::ColorConverter(
        OMX_COLOR_FORMATTYPE from, OMX_COLOR_FORMATTYPE to)
    : mSrcFormat(from),
      mDstFormat(to),
      mClip(NULL) {
}

ColorConverter::~ColorConverter() {
    delete[] mClip;
    mClip = NULL;
}

bool ColorConverter::isValid() const {
    if (mDstFormat != OMX_COLOR_Format16bitRGB565) {
        return false;
    }

    switch (mSrcFormat) {
        case OMX_COLOR_FormatYUV420Planar:
        case OMX_COLOR_FormatCbYCrY:
        case OMX_QCOM_COLOR_FormatYVU420SemiPlanar:
        case OMX_COLOR_FormatYUV420SemiPlanar:
        case OMX_TI_COLOR_FormatYUV420PackedSemiPlanar:
#ifdef STE_HARDWARE
        case OMX_STE_COLOR_FormatYUV420PackedSemiPlanarMB:
#endif
#ifdef QCOM_HARDWARE
        case QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka:
#endif
            return true;

        default:
            return false;
    }
}

ColorConverter::BitmapParams::BitmapParams(
        void *bits,
        size_t width, size_t height,
        size_t cropLeft, size_t cropTop,
        size_t cropRight, size_t cropBottom)
    : mBits(bits),
      mWidth(width),
      mHeight(height),
      mCropLeft(cropLeft),
      mCropTop(cropTop),
      mCropRight(cropRight),
      mCropBottom(cropBottom) {
}

size_t ColorConverter::BitmapParams::cropWidth() const {
    return mCropRight - mCropLeft + 1;
}

size_t ColorConverter::BitmapParams::cropHeight() const {
    return mCropBottom - mCropTop + 1;
}

status_t ColorConverter::convert(
        const void *srcBits,
        size_t srcWidth, size_t srcHeight,
        size_t srcCropLeft, size_t srcCropTop,
        size_t srcCropRight, size_t srcCropBottom,
        void *dstBits,
        size_t dstWidth, size_t dstHeight,
        size_t dstCropLeft, size_t dstCropTop,
        size_t dstCropRight, size_t dstCropBottom) {
    if (mDstFormat != OMX_COLOR_Format16bitRGB565) {
        return ERROR_UNSUPPORTED;
    }

    BitmapParams src(
            const_cast<void *>(srcBits),
            srcWidth, srcHeight,
            srcCropLeft, srcCropTop, srcCropRight, srcCropBottom);

    BitmapParams dst(
            dstBits,
            dstWidth, dstHeight,
            dstCropLeft, dstCropTop, dstCropRight, dstCropBottom);

    status_t err;

    switch (mSrcFormat) {
        case OMX_COLOR_FormatYUV420Planar:
            err = convertYUV420Planar(src, dst);
            break;

        case OMX_COLOR_FormatCbYCrY:
            err = convertCbYCrY(src, dst);
            break;

        case OMX_QCOM_COLOR_FormatYVU420SemiPlanar:
            err = convertQCOMYUV420SemiPlanar(src, dst);
            break;

        case OMX_COLOR_FormatYUV420SemiPlanar:
            err = convertYUV420SemiPlanar(src, dst);
            break;

        case OMX_TI_COLOR_FormatYUV420PackedSemiPlanar:
            err = convertTIYUV420PackedSemiPlanar(src, dst);
            break;

#ifdef QCOM_HARDWARE
        case QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka:
            {
                void * lib = dlopen("libmm-color-convertor.so", RTLD_NOW);

                if (!lib) {
                    LOGE("dlopen for libmm-color-convertor failed with errno %d", errno);
                    return ERROR_UNSUPPORTED;
                }


                typedef int (*convertFn)(ColorConvertParams src, ColorConvertParams dst, uint8_t *adjustedClip);

                convertFn convertNV12Tile = (convertFn)dlsym(lib, "_ZN7android7convertENS_18ColorConvertParamsES0_Ph");
                if (!convertNV12Tile) {
                    dlclose(lib);
                    LOGE("dlsym on libmm-color-convertor failed with errno %d", errno);
                    return ERROR_UNSUPPORTED;
                }

                struct ColorConvertParams srcTemp;
                srcTemp.width = srcWidth;
                srcTemp.height = srcHeight;
                srcTemp.cropWidth = src.cropWidth();
                srcTemp.cropHeight = src.cropHeight();
                srcTemp.cropLeft = src.mCropLeft;
                srcTemp.cropRight = src.mCropRight;
                srcTemp.cropTop = src.mCropTop;
                srcTemp.cropBottom = src.mCropBottom;
                srcTemp.data = src.mBits;
                srcTemp.colorFormat = YCbCr420Tile;
                srcTemp.flags = 0;

                struct ColorConvertParams dstTemp;
                dstTemp.width = dstWidth;
                dstTemp.height = dstHeight;
                dstTemp.cropWidth = dst.cropWidth();
                dstTemp.cropHeight = dst.cropHeight();
                dstTemp.cropLeft = dst.mCropLeft;
                dstTemp.cropRight = dst.mCropRight;
                dstTemp.cropTop = dst.mCropTop;
                dstTemp.cropBottom = dst.mCropBottom;
                dstTemp.data = dst.mBits;
                dstTemp.colorFormat = RGB565;
                dstTemp.flags = 0;

                uint8_t * adjustedClip = initClip();
                err = convertNV12Tile(srcTemp, dstTemp, adjustedClip);
                dlclose(lib);
            }
            break;
#endif
        default:
        {
            CHECK(!"Should not be here. Unknown color conversion.");
            break;
        }
    }

    return err;
}

status_t ColorConverter::convertCbYCrY(
        const BitmapParams &src, const BitmapParams &dst) {
    // XXX Untested

    uint8_t *kAdjustedClip = initClip();

    if (!((src.mCropLeft & 1) == 0
        && src.cropWidth() == dst.cropWidth()
        && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    uint32_t *dst_ptr = (uint32_t *)dst.mBits
        + (dst.mCropTop * dst.mWidth + dst.mCropLeft) / 2;

    const uint8_t *src_ptr = (const uint8_t *)src.mBits
        + (src.mCropTop * dst.mWidth + src.mCropLeft) * 2;

    for (size_t y = 0; y < src.cropHeight(); ++y) {
        for (size_t x = 0; x < src.cropWidth(); x += 2) {
            signed y1 = (signed)src_ptr[2 * x + 1] - 16;
            signed y2 = (signed)src_ptr[2 * x + 3] - 16;
            signed u = (signed)src_ptr[2 * x] - 128;
            signed v = (signed)src_ptr[2 * x + 2] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = (tmp1 + u_b) / 256;
            signed g1 = (tmp1 + v_g + u_g) / 256;
            signed r1 = (tmp1 + v_r) / 256;

            signed tmp2 = y2 * 298;
            signed b2 = (tmp2 + u_b) / 256;
            signed g2 = (tmp2 + v_g + u_g) / 256;
            signed r2 = (tmp2 + v_r) / 256;

            uint32_t rgb1 =
                ((kAdjustedClip[r1] >> 3) << 11)
                | ((kAdjustedClip[g1] >> 2) << 5)
                | (kAdjustedClip[b1] >> 3);

            uint32_t rgb2 =
                ((kAdjustedClip[r2] >> 3) << 11)
                | ((kAdjustedClip[g2] >> 2) << 5)
                | (kAdjustedClip[b2] >> 3);

            dst_ptr[x / 2] = (rgb2 << 16) | rgb1;
        }

        src_ptr += src.mWidth * 2;
        dst_ptr += dst.mWidth / 2;
    }

    return OK;
}

status_t ColorConverter::convertYUV420Planar(
        const BitmapParams &src, const BitmapParams &dst) {
    if (!((src.mCropLeft & 1) == 0
            && src.cropWidth() == dst.cropWidth()
            && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    uint8_t *kAdjustedClip = initClip();

    uint16_t *dst_ptr = (uint16_t *)dst.mBits
        + dst.mCropTop * dst.mWidth + dst.mCropLeft;

    const uint8_t *src_y =
        (const uint8_t *)src.mBits + src.mCropTop * src.mWidth + src.mCropLeft;

    const uint8_t *src_u =
        (const uint8_t *)src_y + src.mWidth * src.mHeight
        + src.mCropTop * (src.mWidth / 2) + src.mCropLeft / 2;

    const uint8_t *src_v =
        src_u + (src.mWidth / 2) * (src.mHeight / 2);

    for (size_t y = 0; y < src.cropHeight(); ++y) {
        for (size_t x = 0; x < src.cropWidth(); x += 2) {
            // B = 1.164 * (Y - 16) + 2.018 * (U - 128)
            // G = 1.164 * (Y - 16) - 0.813 * (V - 128) - 0.391 * (U - 128)
            // R = 1.164 * (Y - 16) + 1.596 * (V - 128)

            // B = 298/256 * (Y - 16) + 517/256 * (U - 128)
            // G = .................. - 208/256 * (V - 128) - 100/256 * (U - 128)
            // R = .................. + 409/256 * (V - 128)

            // min_B = (298 * (- 16) + 517 * (- 128)) / 256 = -277
            // min_G = (298 * (- 16) - 208 * (255 - 128) - 100 * (255 - 128)) / 256 = -172
            // min_R = (298 * (- 16) + 409 * (- 128)) / 256 = -223

            // max_B = (298 * (255 - 16) + 517 * (255 - 128)) / 256 = 534
            // max_G = (298 * (255 - 16) - 208 * (- 128) - 100 * (- 128)) / 256 = 432
            // max_R = (298 * (255 - 16) + 409 * (255 - 128)) / 256 = 481

            // clip range -278 .. 535

            signed y1 = (signed)src_y[x] - 16;
            signed y2 = (signed)src_y[x + 1] - 16;

            signed u = (signed)src_u[x / 2] - 128;
            signed v = (signed)src_v[x / 2] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = (tmp1 + u_b) / 256;
            signed g1 = (tmp1 + v_g + u_g) / 256;
            signed r1 = (tmp1 + v_r) / 256;

            signed tmp2 = y2 * 298;
            signed b2 = (tmp2 + u_b) / 256;
            signed g2 = (tmp2 + v_g + u_g) / 256;
            signed r2 = (tmp2 + v_r) / 256;

            uint32_t rgb1 =
                ((kAdjustedClip[r1] >> 3) << 11)
                | ((kAdjustedClip[g1] >> 2) << 5)
                | (kAdjustedClip[b1] >> 3);

            uint32_t rgb2 =
                ((kAdjustedClip[r2] >> 3) << 11)
                | ((kAdjustedClip[g2] >> 2) << 5)
                | (kAdjustedClip[b2] >> 3);

            if (x + 1 < src.cropWidth()) {
                *(uint32_t *)(&dst_ptr[x]) = (rgb2 << 16) | rgb1;
            } else {
                dst_ptr[x] = rgb1;
            }
        }

        src_y += src.mWidth;

        if (y & 1) {
            src_u += src.mWidth / 2;
            src_v += src.mWidth / 2;
        }

        dst_ptr += dst.mWidth;
    }

    return OK;
}

status_t ColorConverter::convertQCOMYUV420SemiPlanar(
        const BitmapParams &src, const BitmapParams &dst) {
    uint8_t *kAdjustedClip = initClip();

    if (!((dst.mWidth & 3) == 0
            && (src.mCropLeft & 1) == 0
            && src.cropWidth() == dst.cropWidth()
            && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    uint32_t *dst_ptr = (uint32_t *)dst.mBits
        + (dst.mCropTop * dst.mWidth + dst.mCropLeft) / 2;

    const uint8_t *src_y =
        (const uint8_t *)src.mBits + src.mCropTop * src.mWidth + src.mCropLeft;

    const uint8_t *src_u =
        (const uint8_t *)src_y + src.mWidth * src.mHeight
        + src.mCropTop * src.mWidth + src.mCropLeft;

    for (size_t y = 0; y < src.cropHeight(); ++y) {
        for (size_t x = 0; x < src.cropWidth(); x += 2) {
            signed y1 = (signed)src_y[x] - 16;
            signed y2 = (signed)src_y[x + 1] - 16;

            signed u = (signed)src_u[x & ~1] - 128;
            signed v = (signed)src_u[(x & ~1) + 1] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = (tmp1 + u_b) / 256;
            signed g1 = (tmp1 + v_g + u_g) / 256;
            signed r1 = (tmp1 + v_r) / 256;

            signed tmp2 = y2 * 298;
            signed b2 = (tmp2 + u_b) / 256;
            signed g2 = (tmp2 + v_g + u_g) / 256;
            signed r2 = (tmp2 + v_r) / 256;

            uint32_t rgb1 =
                ((kAdjustedClip[b1] >> 3) << 11)
                | ((kAdjustedClip[g1] >> 2) << 5)
                | (kAdjustedClip[r1] >> 3);

            uint32_t rgb2 =
                ((kAdjustedClip[b2] >> 3) << 11)
                | ((kAdjustedClip[g2] >> 2) << 5)
                | (kAdjustedClip[r2] >> 3);

            dst_ptr[x / 2] = (rgb2 << 16) | rgb1;
        }

        src_y += src.mWidth;

        if (y & 1) {
            src_u += src.mWidth;
        }

        dst_ptr += dst.mWidth / 2;
    }

    return OK;
}

status_t ColorConverter::convertYUV420SemiPlanar(
        const BitmapParams &src, const BitmapParams &dst) {
    // XXX Untested

    uint8_t *kAdjustedClip = initClip();

    if (!((dst.mWidth & 3) == 0
            && (src.mCropLeft & 1) == 0
            && src.cropWidth() == dst.cropWidth()
            && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    uint32_t *dst_ptr = (uint32_t *)dst.mBits
        + (dst.mCropTop * dst.mWidth + dst.mCropLeft) / 2;

    const uint8_t *src_y =
        (const uint8_t *)src.mBits + src.mCropTop * src.mWidth + src.mCropLeft;

    const uint8_t *src_u =
        (const uint8_t *)src_y + src.mWidth * src.mHeight
        + src.mCropTop * src.mWidth + src.mCropLeft;

    for (size_t y = 0; y < src.cropHeight(); ++y) {
        for (size_t x = 0; x < src.cropWidth(); x += 2) {
            signed y1 = (signed)src_y[x] - 16;
            signed y2 = (signed)src_y[x + 1] - 16;

            signed v = (signed)src_u[x & ~1] - 128;
            signed u = (signed)src_u[(x & ~1) + 1] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = (tmp1 + u_b) / 256;
            signed g1 = (tmp1 + v_g + u_g) / 256;
            signed r1 = (tmp1 + v_r) / 256;

            signed tmp2 = y2 * 298;
            signed b2 = (tmp2 + u_b) / 256;
            signed g2 = (tmp2 + v_g + u_g) / 256;
            signed r2 = (tmp2 + v_r) / 256;

            uint32_t rgb1 =
                ((kAdjustedClip[b1] >> 3) << 11)
                | ((kAdjustedClip[g1] >> 2) << 5)
                | (kAdjustedClip[r1] >> 3);

            uint32_t rgb2 =
                ((kAdjustedClip[b2] >> 3) << 11)
                | ((kAdjustedClip[g2] >> 2) << 5)
                | (kAdjustedClip[r2] >> 3);

            dst_ptr[x / 2] = (rgb2 << 16) | rgb1;
        }

        src_y += src.mWidth;

        if (y & 1) {
            src_u += src.mWidth;
        }

        dst_ptr += dst.mWidth / 2;
    }

    return OK;
}

status_t ColorConverter::convertTIYUV420PackedSemiPlanar(
        const BitmapParams &src, const BitmapParams &dst) {
    uint8_t *kAdjustedClip = initClip();

    if (!((dst.mWidth & 3) == 0
            && (src.mCropLeft & 1) == 0
            && src.cropWidth() == dst.cropWidth()
            && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    uint32_t *dst_ptr = (uint32_t *)dst.mBits
        + (dst.mCropTop * dst.mWidth + dst.mCropLeft) / 2;

    const uint8_t *src_y = (const uint8_t *)src.mBits;

    const uint8_t *src_u =
        (const uint8_t *)src_y + src.mWidth * (src.mHeight - src.mCropTop / 2);

    for (size_t y = 0; y < src.cropHeight(); ++y) {
        for (size_t x = 0; x < src.cropWidth(); x += 2) {
            signed y1 = (signed)src_y[x] - 16;
            signed y2 = (signed)src_y[x + 1] - 16;

            signed u = (signed)src_u[x & ~1] - 128;
            signed v = (signed)src_u[(x & ~1) + 1] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = (tmp1 + u_b) / 256;
            signed g1 = (tmp1 + v_g + u_g) / 256;
            signed r1 = (tmp1 + v_r) / 256;

            signed tmp2 = y2 * 298;
            signed b2 = (tmp2 + u_b) / 256;
            signed g2 = (tmp2 + v_g + u_g) / 256;
            signed r2 = (tmp2 + v_r) / 256;

            uint32_t rgb1 =
                ((kAdjustedClip[r1] >> 3) << 11)
                | ((kAdjustedClip[g1] >> 2) << 5)
                | (kAdjustedClip[b1] >> 3);

            uint32_t rgb2 =
                ((kAdjustedClip[r2] >> 3) << 11)
                | ((kAdjustedClip[g2] >> 2) << 5)
                | (kAdjustedClip[b2] >> 3);

            dst_ptr[x / 2] = (rgb2 << 16) | rgb1;
        }

        src_y += src.mWidth;

        if (y & 1) {
            src_u += src.mWidth;
        }

        dst_ptr += dst.mWidth / 2;
    }

    return OK;
}

#ifdef STE_HARDWARE
status_t ColorConverter::convertSTEYUV420PackedSemiPlanarMB(
        const BitmapParams &src, const BitmapParams &dst) {

    if (!((dst.mWidth & 1) == 0
            && src.mCropLeft == 0
            && src.mCropTop == 0
            && src.cropWidth() == dst.cropWidth()
            && src.cropHeight() == dst.cropHeight())) {
        return ERROR_UNSUPPORTED;
    }

    OMX_U32 mx = src.mWidth / 16;
    OMX_U32 my = src.mHeight / 16;
    OMX_U32 lx, ly;
    OMX_U32 *pChroma, *pLuma = (OMX_U32 *)src.mBits;

    pChroma = (OMX_U32 *)src.mBits + mx * my * 64;
    for (ly = 0; ly < my; ly++) {
        for (lx = 0; lx < mx; lx++) {
            OMX_U32 col, row, lumaWord, chromaWord1 = 0, rgbWord, i;
            OMX_U8 y[4], cb[4], cr[4], r[4], g[4], b[4];
            OMX_U32 *dstBuf, *locBuf;
            OMX_U32 *pBurstLuma = 0, *pBurstChroma = 0;
            OMX_U32 *pWordLuma = 0, *pWordChroma = 0;
            OMX_U8 nbOfBlock;

            dstBuf = ((OMX_U32 *)dst.mBits) + (ly * 16) * dst.mWidth / 2;
            dstBuf += (lx * 16) / 2;

            pBurstLuma = pLuma;
            pBurstChroma = pChroma;

            for (col = 0; col < 2; col++) {
                // conversion of a macroblock
                for (nbOfBlock = 0; nbOfBlock < 2; nbOfBlock++) {
                    locBuf = dstBuf + 4 * col + 2 * nbOfBlock;
                    OMX_U32 dstRowOrigo = ly * 16 * dst.mWidth;

                    switch (nbOfBlock) {
                    case 0:
                        pWordLuma = pBurstLuma;
                        pWordChroma = pBurstChroma;
                        break;
                    case 1:
                        pWordLuma = pBurstLuma + 1;
                        pWordChroma = pBurstChroma + 1;
                        break;
                    }
                    for (row = 0; row < 16; row++) {

                        // Check for cropping on the y axis
                        if (ly * 16 + row >= dst.mHeight) {
                            break;
                        }

                        lumaWord = *pWordLuma;
                        pWordLuma += 2;
                        if (row % 2 == 0) {
                            chromaWord1 = *pWordChroma;
                            pWordChroma += 2;
                        }

                        y[3] = ((lumaWord >> 24) & 0xff);
                        y[2] = ((lumaWord >> 16) & 0xff);
                        y[1] = ((lumaWord >>  8) & 0xff);
                        y[0] = ((lumaWord >>  0) & 0xff);

                        cb[0] = cb[1] = ((chromaWord1 >>  0) & 0xff);
                        cb[2] = cb[3] = ((chromaWord1 >> 16) & 0xff);
                        cr[0] = cr[1] = ((chromaWord1 >>  8) & 0xff);
                        cr[2] = cr[3] = ((chromaWord1 >> 24) & 0xff);

                        for (i = 0; i < 4; i++) {

                            int32_t rW,gW,bW;

                            rW = 298 * y[i] + 408 * cr[i] - 57059;
                            gW = 298 * y[i] - 100 * cb[i] - 208 * cr[i] + 34713;
                            bW = 298 * y[i] + 516 * cb[i] - 70887;

                            if (rW < 0) {
                                r[i] = 0;
                            } else if (rW >= 65536) {
                                r[i] = 255;
                            } else {
                                r[i] = (rW >> 8);
                            }
                            if (gW < 0) {
                                g[i] = 0;
                            } else if (gW >= 65536) {
                                g[i] = 255;
                            } else {
                                g[i] = (gW >> 8);
                            }
                            if (bW < 0) {
                                b[i] = 0;
                            } else if (bW >= 65536) {
                                b[i] = 255;
                            } else {
                                b[i] = (bW >> 8);
                            }
                            r[i] >>= 3;
                            g[i] >>= 2;
                            b[i] >>= 3;
                        }
                        for (i = 0; i < 4; i += 2) {

                            // Check for cropping on the x axis
                            OMX_U32 rowPos = (locBuf - (OMX_U32 *)dst.mBits) * 2 - dstRowOrigo;
                            if (rowPos >= dst.mWidth) {
                                locBuf++;
                                continue;
                            }

                            rgbWord = (r[i + 1] << 27) +
                                (g[i + 1] << 21) +
                                (b[i + 1] << 16) +
                                (r[i] << 11) +
                                (g[i] << 5) +
                                (b[i] << 0);
                            *locBuf++ = rgbWord;
                        }
                        locBuf += dst.mWidth / 2 - 2;
                        dstRowOrigo += dst.mWidth;
                    } //end of for 16 loop
                }  //end of 2 block loop
                pBurstLuma += 32;
                pBurstChroma += 16;
            } // end of 2 col loop
            pLuma   += 64;
            pChroma += 32;
        }
    }

    return OK;
}
#endif

uint8_t *ColorConverter::initClip() {
    static const signed kClipMin = -278;
    static const signed kClipMax = 535;

    if (mClip == NULL) {
        mClip = new uint8_t[kClipMax - kClipMin + 1];

        for (signed i = kClipMin; i <= kClipMax; ++i) {
            mClip[i - kClipMin] = (i < 0) ? 0 : (i > 255) ? 255 : (uint8_t)i;
        }
    }

    return &mClip[-kClipMin];
}

}  // namespace android
