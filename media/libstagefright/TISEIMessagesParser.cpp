/*
 * Copyright (C) Texas Instruments - http://www.ti.com/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

#define LOG_TAG "SEI_TI_Parser"
#include "include/TISEIMessagesParser.h"
#include <utils/Log.h>

#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/Utils.h>

#define MP4_INVALID_VOL_PARAM -1
#define STEREO_VIDEO_INFO_TYPE 0x15
#define FRAME_PACKING_ARRANGEMENT_TYPE 0x2D
// To account for payloadtype and payloadsize bytes
#define PAYLOAD_HEADER_SIZE 2

#define PV_CLZ(A,B) while (((B) & 0x8000) == 0) {(B) <<=1; A++;}

static const uint32_t mask[33] =
{
    0x00000000, 0x00000001, 0x00000003, 0x00000007,
    0x0000000f, 0x0000001f, 0x0000003f, 0x0000007f,
    0x000000ff, 0x000001ff, 0x000003ff, 0x000007ff,
    0x00000fff, 0x00001fff, 0x00003fff, 0x00007fff,
    0x0000ffff, 0x0001ffff, 0x0003ffff, 0x0007ffff,
    0x000fffff, 0x001fffff, 0x003fffff, 0x007fffff,
    0x00ffffff, 0x01ffffff, 0x03ffffff, 0x07ffffff,
    0x0fffffff, 0x1fffffff, 0x3fffffff, 0x7fffffff,
    0xffffffff
};

typedef struct
{
    uint8_t *data;
    uint32_t numBytes;
    uint32_t bytePos;
    uint32_t bitBuf;
    uint32_t dataBitPos;
    uint32_t  bitPos;
} mp4StreamType;

/*------------------------------------------------------------------------------------
    sStereo_video_info
    This structure contains the stereo video information SEI msg elements
   -----------------------------------------------------------------------------------*/
typedef struct sei_stereo_video_info {
   uint32_t field_views_flag;                                                  //!< u(1)
   uint32_t top_field_is_left_view_flag;                                //!< u(1)
   uint32_t current_frame_is_left_view_flag;                       //!< u(1)
   uint32_t next_frame_is_second_view_flag;                     //!< u(1)
   uint32_t left_view_self_contained_flag;                           //!< u(1)
   uint32_t right_view_self_contained_flag;                         //!< u(1)
}sStereo_video_info;

/*------------------------------------------------------------------------------------
    sframe_packing_arrangement
    This structure contains the frame packing arrangement info for SEI msg elements
   -----------------------------------------------------------------------------------*/
typedef struct sei_frame_packing_arrangement {
   uint32_t frame_packing_arrangement_id;                                                    //!< ue(v)
   uint32_t frame_packing_arrangement_cancel_flag;                                    //!< u(1)
   uint32_t frame_packing_arrangement_type;                                               //!< u(7)
   uint32_t quincunx_sampling_flag;                                                                //!< u(1)
   uint32_t content_interpretation_type;                                                          //!< u(6)
   uint32_t spatial_flipping_flag;                                                                      //!< u(1)
   uint32_t frame0_flipped_flag;                                                                      //!< u(1)
   uint32_t field_views_flag;                                                                             //!< u(1)
   uint32_t current_frame_is_frame0_flag;                                                     //!< u(7)
   uint32_t frame0_self_contained_flag;                                                         //!< u(1)
   uint32_t frame1_self_contained_flag;                                                         //!< u(1)
   uint32_t frame0_grid_position_x;                                                                //!< u(4)
   uint32_t frame0_grid_position_y;                                                                //!< u(4)
   uint32_t frame1_grid_position_x;                                                                //!< u(4)
   uint32_t frame1_grid_position_y;                                                                //!< u(4)
   uint32_t frame_packing_arrangement_reserved_byte;                             //!< u(8)
   uint32_t frame_packing_arrangement_repetition_period;                        //!< ue(v)
   uint32_t frame_packing_arrangement_extension_flag;                            //!< u(1)
}sframe_packing_arrangement;

/*------------------------------------------------------------------------------------
    sframe_packing_arrangement_type
    This enum contains the frame packing arrangement type that will be passed to the display driver
   -----------------------------------------------------------------------------------*/
typedef enum {
   checkerboard = 0,
   column = 1,
   row = 2,
   side_by_side = 3,
   top_bottom = 4,
   temporal = 5,
}sframe_packing_arrangement_type;

using namespace android;

int16_t ShowBits(
    mp4StreamType *pStream,           /* Input Stream */
    uint8_t ucNBits,          /* nr of bits to read */
    uint32_t *pulOutData      /* output target */
)
{
    uint8_t *bits;
    uint32_t dataBitPos = pStream->dataBitPos;
    uint32_t bitPos = pStream->bitPos;
    uint32_t dataBytePos;

    uint i;

    if (ucNBits > (32 - bitPos))    /* not enough bits */
    {
        dataBytePos = dataBitPos >> 3; /* Byte Aligned Position */
        bitPos = dataBitPos & 7; /* update bit position */
        if (dataBytePos > pStream->numBytes - 4)
        {
            pStream->bitBuf = 0;
            for (i = 0;i < pStream->numBytes - dataBytePos;i++)
            {
                pStream->bitBuf |= pStream->data[dataBytePos+i];
                pStream->bitBuf <<= 8;
            }
            pStream->bitBuf <<= 8 * (3 - i);
        }
        else
        {
            bits = &pStream->data[dataBytePos];
            pStream->bitBuf = (bits[0] << 24) | (bits[1] << 16) | (bits[2] << 8) | bits[3];
        }
        pStream->bitPos = bitPos;
    }

    bitPos += ucNBits;

    *pulOutData = (pStream->bitBuf >> (32 - bitPos)) & mask[(uint16_t)ucNBits];

    return 0;
}

int16_t FlushBits(
    mp4StreamType *pStream,           /* Input Stream */
    uint8_t ucNBits                      /* number of bits to flush */
)
{
    uint8_t *bits;
    uint32_t dataBitPos = pStream->dataBitPos;
    uint32_t bitPos = pStream->bitPos;
    uint32_t dataBytePos;


    if ((dataBitPos + ucNBits) > (uint32_t)(pStream->numBytes << 3))
        return (-2); // Buffer over run

    dataBitPos += ucNBits;
    bitPos     += ucNBits;

    if (bitPos > 32)
    {
        dataBytePos = dataBitPos >> 3;    /* Byte Aligned Position */
        bitPos = dataBitPos & 7; /* update bit position */
        bits = &pStream->data[dataBytePos];
        pStream->bitBuf = (bits[0] << 24) | (bits[1] << 16) | (bits[2] << 8) | bits[3];
    }

    pStream->dataBitPos = dataBitPos;
    pStream->bitPos     = bitPos;

    return 0;
}

int16_t ReadBits(
    mp4StreamType *pStream,           /* Input Stream */
    uint8_t ucNBits,                     /* nr of bits to read */
    uint32_t *pulOutData                 /* output target */
)
{
    uint8_t *bits;
    uint32_t dataBitPos = pStream->dataBitPos;
    uint32_t bitPos = pStream->bitPos;
    uint32_t dataBytePos;


    if ((dataBitPos + ucNBits) > (pStream->numBytes << 3))
    {
        *pulOutData = 0;
        return (-2); // Buffer over run
    }

    //  dataBitPos += ucNBits;

    if (ucNBits > (32 - bitPos))    /* not enough bits */
    {
        dataBytePos = dataBitPos >> 3;    /* Byte Aligned Position */
        bitPos = dataBitPos & 7; /* update bit position */
        bits = &pStream->data[dataBytePos];
        pStream->bitBuf = (bits[0] << 24) | (bits[1] << 16) | (bits[2] << 8) | bits[3];
    }

    pStream->dataBitPos += ucNBits;
    pStream->bitPos      = (unsigned char)(bitPos + ucNBits);

    *pulOutData = (pStream->bitBuf >> (32 - pStream->bitPos)) & mask[(uint16_t)ucNBits];

    return 0;
}

void ue_v(mp4StreamType *psBits, uint32_t *codeNum)
{
    uint32_t temp;
    uint tmp_cnt;
    int32_t leading_zeros = 0;
    ShowBits(psBits, 16, &temp);
    tmp_cnt = temp  | 0x1;
    PV_CLZ(leading_zeros, tmp_cnt)

    if (leading_zeros < 8)
    {
        *codeNum = (temp >> (15 - (leading_zeros << 1))) - 1;
        FlushBits(psBits, (leading_zeros << 1) + 1);
    }
    else
    {
        ReadBits(psBits, (leading_zeros << 1) + 1, &temp);
        *codeNum = temp - 1;
    }
}

/* Subclause D.1.22 */
int16_t stereo_video_info(mp4StreamType *psBits, S3D_params &mS3Dparams)
{
    // SEI message detected, default configuration
    mS3Dparams.active = true;
    mS3Dparams.mode = S3D_MODE_ON;
    mS3Dparams.fmt = S3D_FORMAT_OVERUNDER;
    mS3Dparams.subsampling = S3D_SS_NONE;

    int16_t status =0;
    uint32_t temp;
    sStereo_video_info* pStereo_video_info = (sStereo_video_info *)malloc(sizeof(sStereo_video_info));
    ReadBits(psBits, 1, &temp);
    pStereo_video_info->field_views_flag = temp;

    if( pStereo_video_info->field_views_flag )
    {
        mS3Dparams.metadata = S3D_SEI_STEREO_INFO_INTERLACED;
        ReadBits(psBits, 1, &temp);
        pStereo_video_info->top_field_is_left_view_flag = temp;
        if(pStereo_video_info->top_field_is_left_view_flag)
                mS3Dparams.order = S3D_ORDER_LF;
        else
                mS3Dparams.order = S3D_ORDER_RF;
    }
    else
    {
        mS3Dparams.metadata = S3D_SEI_STEREO_INFO_PROGRESSIVE;
        ReadBits(psBits, 1, &temp);
        pStereo_video_info->current_frame_is_left_view_flag = temp;
        if(pStereo_video_info->current_frame_is_left_view_flag)
                mS3Dparams.order = S3D_ORDER_LF;
        else
                mS3Dparams.order = S3D_ORDER_RF;

        ReadBits(psBits, 1, &temp);
        pStereo_video_info->next_frame_is_second_view_flag = temp;
    }

    ReadBits(psBits, 1, &temp);
    pStereo_video_info->left_view_self_contained_flag = temp;
    ReadBits(psBits, 1, &temp);
    pStereo_video_info->right_view_self_contained_flag = temp;

    free(pStereo_video_info);
    return status;
}

/* Subclause D.1.25 */
int16_t frame_packing_arrangement(mp4StreamType *psBits, S3D_params &mS3Dparams, int32_t payloadSize)
{
    // SEI message detected, default configuration
    mS3Dparams.active = true;
    mS3Dparams.mode = S3D_MODE_ON;
    mS3Dparams.subsampling = S3D_SS_NONE;
    mS3Dparams.metadata = S3D_SEI_STEREO_FRAME_PACKING;

    int status =0, bitsToFlush = 0;
    uint32_t temp;
    uint32_t bitsread_diff = psBits->dataBitPos;

    sframe_packing_arrangement* pframe_packing_arrangement = (sframe_packing_arrangement *)malloc(sizeof(sframe_packing_arrangement));
    // Get and pass frame_packing_arrangement_type
    ue_v(psBits, &(pframe_packing_arrangement->frame_packing_arrangement_id));
    ReadBits(psBits, 1, &temp);
    pframe_packing_arrangement->frame_packing_arrangement_cancel_flag = temp;
    if(!pframe_packing_arrangement->frame_packing_arrangement_cancel_flag)
    {
        ReadBits(psBits, 7, &temp);
        pframe_packing_arrangement->frame_packing_arrangement_type = temp;
        set_frame_packing_arrangement_type(pframe_packing_arrangement->frame_packing_arrangement_type, mS3Dparams.fmt, mS3Dparams.subsampling);

        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->quincunx_sampling_flag= temp;
        ReadBits(psBits, 6, &temp);
        pframe_packing_arrangement->content_interpretation_type= temp;
        if(pframe_packing_arrangement->content_interpretation_type)
            mS3Dparams.order = S3D_ORDER_LF;
        else
            mS3Dparams.order = S3D_ORDER_RF;

        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->spatial_flipping_flag= temp;
        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->frame0_flipped_flag= temp;
        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->field_views_flag= temp;
        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->current_frame_is_frame0_flag = temp;
        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->frame0_self_contained_flag= temp;
        ReadBits(psBits, 1, &temp);
        pframe_packing_arrangement->frame1_self_contained_flag= temp;

        if(!pframe_packing_arrangement->quincunx_sampling_flag &&
            pframe_packing_arrangement->content_interpretation_type != temporal)
        {
            ReadBits(psBits, 4, &temp);
            pframe_packing_arrangement->frame0_grid_position_x= temp;
            ReadBits(psBits, 4, &temp);
            pframe_packing_arrangement->frame0_grid_position_y = temp;
            ReadBits(psBits, 4, &temp);
            pframe_packing_arrangement->frame1_grid_position_x= temp;
            ReadBits(psBits, 4, &temp);
            pframe_packing_arrangement->frame1_grid_position_y= temp;
        }
        ReadBits(psBits, 8, &temp);
        pframe_packing_arrangement->frame_packing_arrangement_reserved_byte = temp;
        ue_v(psBits, &(pframe_packing_arrangement->frame_packing_arrangement_repetition_period));
    }

    ReadBits(psBits, 1, &temp);
    pframe_packing_arrangement->frame_packing_arrangement_extension_flag = temp;

    // To account for trailling 0;
    bitsread_diff= psBits->dataBitPos - bitsread_diff;
    bitsToFlush = 8*payloadSize - bitsread_diff;
    if(bitsToFlush > 0)
        FlushBits(psBits, bitsToFlush);

    free(pframe_packing_arrangement);
    return status; // return 0 for success
}

// Annex D.1 SEI payload syntax
int16_t sei_payload(mp4StreamType *psBits, int32_t payloadType, int32_t payloadSize, S3D_params &mS3Dparams)
{
    int16_t status = 0;
    int i;
    switch (payloadType)
    {
        case STEREO_VIDEO_INFO_TYPE:
        /*     Stereo video Information SEI message          */
            status = stereo_video_info(psBits, mS3Dparams);
        break;
        case FRAME_PACKING_ARRANGEMENT_TYPE:
        /*      Frame packaging arrangement SEI message */
            status = frame_packing_arrangement(psBits, mS3Dparams, payloadSize);
        break;
        default: //for case 0-20,22-44 and reserved sei messages
        /* SEI messages reserved or not supported*/
        LOGE("SEI messages reserved or not supported: 0x%x\n", payloadType);
        for (i = 0; i < payloadSize; i++)
        {
            FlushBits(psBits, 8);
        }
        break;
    }
    return status; // return 0 for success
}

//ref subclause 7.3.2.3.1 SEI message syntax
int16_t sei_message(mp4StreamType *psBits, /*int32_t *ff_byte, */ int32_t *last_payload_type_byte, int32_t *last_payload_size_byte, S3D_params &mS3Dparams)
{
    uint32_t temp;
    int32_t payloadType, payloadSize;
    int16_t status = 0;

    payloadType = 0;
    // Not needed as the output of the read function does not contain the
    // emulation prevention bytes.
    /*while(0xFF == ReadBits(psBits, 8, &temp)) {
        *ff_byte = temp;
        payloadType += 255;
    }*/

    ReadBits(psBits, 8, &temp);
    *last_payload_type_byte = temp;
    payloadType += *last_payload_type_byte;
    LOGV("Value for payloadType = %d \n", payloadType);

    payloadSize = 0;
    // Not needed as the output of the read function does not contain the
    // emulation prevention bytes.
    /*while(0xFF == ReadBits(psBits, 8, &temp)) {
        *ff_byte = temp;
        payloadSize += 255;
    }*/

    ReadBits(psBits, 8, &temp);
    *last_payload_size_byte = temp;
    payloadSize += *last_payload_size_byte;
    LOGV("Value for payloadSize = %d \n", payloadSize);

    status = sei_payload(psBits, payloadType, payloadSize, mS3Dparams);

    return status;
}

namespace android {

/* ======================================================================== */
/*  Function : sei_rbsp()                                                   */
/*  Purpose  : parse SEI messages NAL unit (ref: subclause 7.3.2.3)         */
/* ======================================================================== */
int16_t sei_rbsp(uint8_t *buffer, int32_t length , S3D_params &mS3Dparams)
{
    int16_t status = 0;
    int32_t last_payload_type_byte, last_payload_size_byte;
    uint32_t temp;
    int32_t NALSize = 0;

    mp4StreamType psBits;
    psBits.data  = buffer;
    psBits.numBytes = length;
    psBits.bytePos = 0;
    psBits.bitBuf = 0;
    psBits.dataBitPos = 0;
    psBits.bitPos = 32;

    // Check if SEI NAL Unit
    ReadBits(&psBits, 8, &temp);
    if ((temp & 0x1F) != 6) return MP4_INVALID_VOL_PARAM;

    //Account for NAL Unit type message
    NALSize++;

  /*---------------------------------------------------------------------------
  *     Multiple SEI messages can be present in one SEI NAL. This do-while
  *     loop processes one SEI message per iteration.
  ----------------------------------------------------------------------------*/
    do
    {
        status = sei_message(&psBits, &last_payload_type_byte, &last_payload_size_byte, mS3Dparams);
        ShowBits(&psBits, 8, &temp);
        NALSize += PAYLOAD_HEADER_SIZE + last_payload_size_byte;
    }while(temp != 0x80 && NALSize < (length-1) ); // more_rbsp_data();  We do account for termination bit 0x80
  // ignore the trailing bits rbsp_trailing_bits();
  //assert( NALSize+1 == length );

    return status;
}

/* ======================================================================== */
/*  Function : AVCGetNALType()                                              */
/*  Purpose  : Sniff NAL type from the bitstream                            */
/* ======================================================================== */
int32_t AVCGetNALType(uint8_t *bitstream, int size,int *nal_type, int *nal_ref_idc)
{
    int forbidden_zero_bit;
    if (size > 0)
    {
        forbidden_zero_bit = bitstream[0] >> 7;
        if (forbidden_zero_bit != 0)
            return -1;
        *nal_ref_idc = (bitstream[0] & 0x60) >> 5;
        *nal_type = bitstream[0] & 0x1F;
        return 0;
    }

    return -1;
}

void set_frame_packing_arrangement_type(uint32_t frame_packing_arrangement_type, uint32_t &format, uint32_t &subsampling)
{

    switch(frame_packing_arrangement_type)
    {
        case checkerboard:
        {
            LOGV("Frame packing: Checkerboard \n");
            format = S3D_FORMAT_CHECKB;
            break;
        }
        case column:
        {
            LOGV("Frame packing: col il \n");
            format = S3D_FORMAT_COL_IL;
            break;
        }
        case row:
        {
            LOGV("Frame packing: row il \n");
            format = S3D_FORMAT_ROW_IL;
            break;
        }
        case side_by_side:
        {
            LOGV("Frame packing: side_by_side \n");
            format = S3D_FORMAT_SIDEBYSIDE;
            subsampling = S3D_SS_HOR;
            break;
        }
        case top_bottom:
        {
            LOGV("Frame packing: TOP/BOT \n");
            format = S3D_FORMAT_OVERUNDER;
            subsampling = S3D_SS_VERT;
            break;
        }
        case temporal:
        {
            LOGV("Frame packing: Frame Seq \n");
            format = S3D_FORMAT_FRM_SEQ;
            break;
        }
        default:
        {
            LOGV("Frame packing: Default to top/bottom \n");
            format = S3D_FORMAT_OVERUNDER;
            break;
        }
    }
}

}// namespace android


#endif

