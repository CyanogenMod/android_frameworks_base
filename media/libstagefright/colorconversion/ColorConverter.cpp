/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2010, Code Aurora Forum
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

#include <media/stagefright/ColorConverter.h>
#include <media/stagefright/MediaDebug.h>

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
#include <OMX_TI_IVCommon.h>
#endif

namespace android {

static const int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
static const int QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
static const size_t NV12TILE_BLOCK_WIDTH = 64;
static const size_t NV12TILE_BLOCK_HEIGHT = 32;
static const size_t NV12TILE_BLOCK_SIZE = NV12TILE_BLOCK_WIDTH* NV12TILE_BLOCK_HEIGHT;
static const size_t NV12TILE_BLOCK_GROUP_SIZE =  NV12TILE_BLOCK_SIZE*4;

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
        case QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka:
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        case OMX_COLOR_FormatYUV420PackedSemiPlanar:
        case OMX_TI_COLOR_FormatYUV420PackedSemiPlanar_Sequential_TopBottom:
#endif
            return true;

        default:
            return false;
    }
}

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
void ColorConverter::convert(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip,
        size_t dwidth, size_t dheight, size_t nOffset, bool interlaced) {
#else
void ColorConverter::convert(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {
#endif
    CHECK_EQ(mDstFormat, OMX_COLOR_Format16bitRGB565);

    switch (mSrcFormat) {
        case OMX_COLOR_FormatYUV420Planar:
            convertYUV420Planar(
                    width, height, srcBits, srcSkip, dstBits, dstSkip);
            break;

        case OMX_COLOR_FormatCbYCrY:
            convertCbYCrY(
                    width, height, srcBits, srcSkip, dstBits, dstSkip);
            break;

        case OMX_QCOM_COLOR_FormatYVU420SemiPlanar:
            convertQCOMYUV420SemiPlanar(
                    width, height, srcBits, srcSkip, dstBits, dstSkip);
            break;

        case OMX_COLOR_FormatYUV420SemiPlanar:
            convertYUV420SemiPlanar(
                    width, height, srcBits, srcSkip, dstBits, dstSkip);
            break;

        case QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka:
            convertNV12Tile(
                    width, height, srcBits, srcSkip, dstBits, dstSkip);
            break;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        case OMX_COLOR_FormatYUV420PackedSemiPlanar:
        case OMX_TI_COLOR_FormatYUV420PackedSemiPlanar_Sequential_TopBottom:
            convertYUV420PackedSemiPlanar(
                    width, height, dwidth, dheight,nOffset,srcBits, srcSkip, dstBits, dstSkip, interlaced);
            break;
#endif
        default:
        {
            CHECK(!"Should not be here. Unknown color conversion.");
            break;
        }
    }
}

void ColorConverter::convertCbYCrY(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {
    CHECK_EQ(srcSkip, 0);  // Doesn't really make sense for YUV formats.
    CHECK(dstSkip >= width * 2);
    CHECK((dstSkip & 3) == 0);

    uint8_t *kAdjustedClip = initClip();

    uint32_t *dst_ptr = (uint32_t *)dstBits;

    const uint8_t *src = (const uint8_t *)srcBits;

    for (size_t y = 0; y < height; ++y) {
        for (size_t x = 0; x < width; x += 2) {
            signed y1 = (signed)src[2 * x + 1] - 16;
            signed y2 = (signed)src[2 * x + 3] - 16;
            signed u = (signed)src[2 * x] - 128;
            signed v = (signed)src[2 * x + 2] - 128;

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

        src += width * 2;
        dst_ptr += dstSkip / 4;
    }
}

void ColorConverter::convertYUV420Planar(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {
    CHECK_EQ(srcSkip, 0);  // Doesn't really make sense for YUV formats.
    CHECK(dstSkip >= width * 2);
    CHECK((dstSkip & 3) == 0);

    uint8_t *kAdjustedClip = initClip();

    uint32_t *dst_ptr = (uint32_t *)dstBits;
    const uint8_t *src_y = (const uint8_t *)srcBits;

    const uint8_t *src_u =
        (const uint8_t *)src_y + width * height;

    const uint8_t *src_v =
        (const uint8_t *)src_u + (width / 2) * (height / 2);

    for (size_t y = 0; y < height; ++y) {
        for (size_t x = 0; x < width; x += 2) {
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

            dst_ptr[x / 2] = (rgb2 << 16) | rgb1;
        }

        src_y += width;

        if (y & 1) {
            src_u += width / 2;
            src_v += width / 2;
        }

        dst_ptr += dstSkip / 4;
    }
}

void ColorConverter::convertQCOMYUV420SemiPlanar(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {
    CHECK_EQ(srcSkip, 0);  // Doesn't really make sense for YUV formats.
    CHECK(dstSkip >= width * 2);
    CHECK((dstSkip & 3) == 0);

    uint8_t *kAdjustedClip = initClip();

    uint32_t *dst_ptr = (uint32_t *)dstBits;
    const uint8_t *src_y = (const uint8_t *)srcBits;

    const uint8_t *src_u =
        (const uint8_t *)src_y + width * height;

    for (size_t y = 0; y < height; ++y) {
        for (size_t x = 0; x < width; x += 2) {
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

        src_y += width;

        if (y & 1) {
            src_u += width;
        }

        dst_ptr += dstSkip / 4;
    }
}

void ColorConverter::convertYUV420SemiPlanar(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {
    CHECK_EQ(srcSkip, 0);  // Doesn't really make sense for YUV formats.
    CHECK(dstSkip >= width * 2);
    CHECK((dstSkip & 3) == 0);

    uint8_t *kAdjustedClip = initClip();

    uint32_t *dst_ptr = (uint32_t *)dstBits;
    const uint8_t *src_y = (const uint8_t *)srcBits;

    const uint8_t *src_u =
        (const uint8_t *)src_y + width * height;

    for (size_t y = 0; y < height; ++y) {
        for (size_t x = 0; x < width; x += 2) {
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

        src_y += width;

        if (y & 1) {
            src_u += width;
        }

        dst_ptr += dstSkip / 4;
    }
}


#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
void ColorConverter::convertYUV420PackedSemiPlanar(
        size_t width, size_t height,
        size_t displaywidth, size_t displayheight, size_t nOffset,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip, bool interlaced) {

    CHECK((dstSkip & 3) == 0);

    size_t stride = width;

    if(srcSkip)
        stride = srcSkip;

    uint8_t *kAdjustedClip = initClip();

    uint32_t *dst_ptr = (uint32_t *)dstBits;
    const uint8_t *src_y = (const uint8_t *)srcBits;

    uint32_t offx = nOffset  % stride;
    uint32_t offy = nOffset / stride;


    const uint8_t *src_u = (const uint8_t *)(src_y-nOffset) + (stride * height);
    src_u += ( ( stride * (offy/2) ) + offx );

    const uint8_t *src_v = src_u + 1;

    for (size_t y = 0; y < displayheight; ++y) {
        for (size_t x = 0; x < displaywidth; x += 2) {

            signed y1 = (signed)src_y[x] - 16;    //Y pixel
            signed y2 = (signed)src_y[x + 1] - 16; //2nd Y pixel

            signed u = (signed)src_u[x & ~1] - 128;   //U component
            signed v = (signed)src_u[(x & ~1) + 1] - 128; //V component

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

        if(!interlaced){
            src_y += stride; //increment Y-pixel line
            if(y&1){
              src_u += stride; //increment U-V line
            }
        }
        else{
            /* Interlaced stream. Will have Sequential Top Bottom content*/
            /* Just extrapolate first half contnents from Y,UV planes */
            if(y&1){
                src_y += stride; //increment Y-pixel line, once for two lines
                if((y/2)&1){
                  src_u += stride; //increment U-V line, once for four lines
                }
            }
        }
        dst_ptr += dstSkip / 4;
    }
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

// GetTiledMemBlockNum
// Calculate the block number within tiled memory where the given frame space
// block resides.
//
// Arguments:
// bx  - Horizontal coordinate of block in frame space
// by  - Vertical coordinate of block in frame space
// nbx - Number of columns of blocks in frame space
// nby - Number of rows of blocks in frame space
size_t ColorConverter::nv12TileGetTiledMemBlockNum(
        size_t bx, size_t by,
        size_t nbx, size_t nby) {
    //

    // Due to the zigzag pattern we have that blocks are numbered like:
    //
    //         |             Column (by)
    //         |   0    1    2    3    4    5    6    7
    //  -------|---------------------------------------
    //      0 |   0    1    6    7    8    9   14   15
    //   R  1 |   2    3    4    5   10   11   12   13
    //   o  2 |  16   17   22   23   24   25   30   31
    //   w  3 |  18   19   20   21   26   27   28   29
    //      4 |  32   33   38   39   40   41   46   47
    // (bx) 5 |  34   35   36   37   42   43   44   45
    //      6 |  48   49   50   51   52   53   54   55

    // From this we can see that:

    // For even rows:
    // - The first block in a row is always mapped to memory block by*nbx.
    // - For all even rows, except for the last one when nby is odd, from the first
    //   block number an offset is then added to obtain the block number for
    //   the other blocks in the row. The offset is bx plus the corresponding
    //   number in the series [0, 0, 4, 4, 4, 4, 8, 8, 8, 8, 12, ...], which can be
    //   expressed as ((bx+2) & ~(3)).
    // - For the last row when nby is odd the offset is simply bx.
    //
    //  For odd rows:
    // - The first block in the row is always mapped to memory block
    //   (by & (~1))*nbx + 2.
    // - From the first block number an offset is then added to obtain the block
    //   number for the other blocks in the row. The offset is bx plus the
    //   corresponding number in the series [0, 0, 0, 0, 4, 4, 4, 4, 8, 8, 8, 8, 12, ...],
    //   which can be  expressed as (bx & ~(3)).

    size_t base; // Number of memory block of the first block in row by
    size_t offs; // Offset from base

    if((by & 1)==0) {
        base = by * nbx;
        if((nby & 1) && (by == (nby - 1))) {
            // Last row when nby is odd
            offs = bx;
        }
        else {
            offs = bx + ((bx + 2) & ~3);
        }
    }
    else {
        base = (by & (~1))*nbx + 2;
        offs = bx + (bx & ~3);
    }

    return base + offs;
}

//Compute the RGB 565 values from the Y ,Cb Cr values
void ColorConverter::nv12TileComputeRGB(
        uint8_t **dstPtr, const uint8_t *blockUV,
        const uint8_t *blockY, size_t blockWidth,
        size_t dstSkip) {

    uint8_t *dest_ptr = *dstPtr;

    uint8_t *kAdjustedClip = initClip();

    for(size_t i = 0; i < blockWidth; i++) {
        signed y1 = (signed)blockY[i] - 16;;
        signed u = (signed)blockUV[i & ~1] - 128;
        signed v = (signed)blockUV[(i & ~1)+1]- 128;

        signed u_b = u * 517;
        signed u_g = -u * 100;
        signed v_g = -v * 208;
        signed v_r = v * 409;

        signed tmp1 = y1 * 298;
        signed b1 = (tmp1 + u_b) / 256;
        signed g1 = (tmp1 + v_g + u_g) / 256;
        signed r1 = (tmp1 + v_r) / 256;
        uint32_t rgb1 =
            ((kAdjustedClip[r1] >> 3) << 11)
            | ((kAdjustedClip[g1] >> 2) << 5)
            | (kAdjustedClip[b1] >> 3);

        dest_ptr[i * 2] =  rgb1 & 0xFF;
        dest_ptr[(i * 2) + 1] = (rgb1 >> 8) & 0xFF;
    }

    dest_ptr += dstSkip;
    *dstPtr = dest_ptr;
}

//  TraverseBlock
//  Function that iterates through the rows of Luma and Chroma blocks
//  simultaneously, passing pointers to the rows to compute RGB565 values.
//  Since there is twice as much data for Luma, Chroma row pointers are provided
//  only when passing an even Luma row pointer.Since the same values apply for
//  the next Luma (odd) row the  can save the pointer if needed.
void ColorConverter::nv12TileTraverseBlock(
        uint8_t **dstPtr, const uint8_t *blockY,
        const uint8_t *blockUV, size_t blockWidth,
        size_t blockHeight, size_t dstSkip) {

    const uint8_t *block_UV = NULL;
    for(size_t row = 0; row < blockHeight; row++) {
        if(row & 1) {
            // Only Luma, the converter can use the previous values if needed
            nv12TileComputeRGB(dstPtr, block_UV, blockY, blockWidth, dstSkip);
            blockUV += NV12TILE_BLOCK_WIDTH;
        }
        else {
            block_UV = blockUV;
            nv12TileComputeRGB(dstPtr, block_UV, blockY, blockWidth, dstSkip);
        }
        blockY += NV12TILE_BLOCK_WIDTH;
    }
}

//Conversion from NV12 tiled to 16 bit RGB565
void ColorConverter::convertNV12Tile(
        size_t width, size_t height,
        const void *srcBits, size_t srcSkip,
        void *dstBits, size_t dstSkip) {

    CHECK_EQ(srcSkip, 0);  // Doesn't really make sense for YUV formats.
    CHECK(dstSkip >= width * 2);
    CHECK((dstSkip & 3) == 0);

    uint8_t *base_ptr = (uint8_t *)dstBits;
    uint8_t *dst_ptr = NULL;

    // Absolute number of columns of blocks in the Luma and Chroma spaces
    size_t abx = (width - 1) / NV12TILE_BLOCK_WIDTH + 1;

    // Number of columns of blocks in the Luma and Chroma spaces rounded to
    // the next multiple of 2
    size_t nbx = (abx + 1) & ~1;

    // Number of rows of blocks in the Luma space
    size_t nby_y = (height - 1) / NV12TILE_BLOCK_HEIGHT + 1;

    // Number of rows of blocks in the Chroma space
    size_t nby_uv = (height / 2 - 1) / NV12TILE_BLOCK_HEIGHT + 1;

    // Calculate the size of the Luma section
    size_t size_y = nbx * nby_y * NV12TILE_BLOCK_SIZE;

    if((size_y % NV12TILE_BLOCK_GROUP_SIZE) != 0) {
        size_y = (((size_y-1) / NV12TILE_BLOCK_GROUP_SIZE)+1)
                 * NV12TILE_BLOCK_GROUP_SIZE;
    }

    // Pointers to the start of the Luma and Chroma spaces
    const uint8_t *src_y   = (const uint8_t*)srcBits;
    const uint8_t *src_uv = src_y + size_y;

    // Iterate
    for(size_t by = 0, rows_left = height; by < nby_y;
            by++, rows_left -= NV12TILE_BLOCK_HEIGHT) {
        for(size_t bx = 0, cols_left = width; bx < abx;
                bx++, cols_left -= NV12TILE_BLOCK_WIDTH) {

            size_t block_width = (cols_left > NV12TILE_BLOCK_WIDTH ?
                    NV12TILE_BLOCK_WIDTH : cols_left);
            size_t block_height = (rows_left > NV12TILE_BLOCK_HEIGHT ?
                    NV12TILE_BLOCK_HEIGHT : rows_left);

            // Address of Luma data for this block
            size_t block_address = (nv12TileGetTiledMemBlockNum(bx, by, nbx, nby_y)
                     * NV12TILE_BLOCK_SIZE);
            const uint8_t *block_y = src_y + block_address;

            // Address of Chroma data for this block
            // since we have half the data for Chroma the same row number is used
            // for two consecutive Luma rows, but we have to offset the base pointer
            // by half a block for odd rows
            block_address = (nv12TileGetTiledMemBlockNum(bx, by/2, nbx, nby_uv)
                    * NV12TILE_BLOCK_SIZE);
            const uint8_t *block_uv = src_uv + block_address + ((by & 1) ? NV12TILE_BLOCK_SIZE/2 : 0);

            // We have started a new block, calculate the destination pointer
            dst_ptr = base_ptr +
            by * NV12TILE_BLOCK_HEIGHT*width*2 +
            bx * NV12TILE_BLOCK_WIDTH*2;
            nv12TileTraverseBlock(&dst_ptr, block_y,
                    block_uv, block_width,
                    block_height, dstSkip);
        }
    }
}

}  // namespace android
