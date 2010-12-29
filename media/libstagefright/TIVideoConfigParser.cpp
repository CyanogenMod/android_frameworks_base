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

#define LOG_TAG "SF_TI_Parser"
#include <utils/Log.h>
//#define LOG_NDEBUG 0

#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/Utils.h>

#define MP4_INVALID_VOL_PARAM               -1
#define SHORT_HEADER_MODE                   -4

#define VISUAL_OBJECT_SEQUENCE_START_CODE   0x01B0
#define VISUAL_OBJECT_SEQUENCE_END_CODE     0x01B1
#define VISUAL_OBJECT_START_CODE            0x01B5
#define VO_START_CODE                       0x8
#define VO_HEADER_LENGTH                    32
#define VOL_START_CODE                      0x12
#define VOL_START_CODE_LENGTH               28

#define GROUP_START_CODE                    0x01B3
#define GROUP_START_CODE_LENGTH             32

#define VOP_ID_CODE_LENGTH                  5
#define VOP_TEMP_REF_CODE_LENGTH            16

#define USER_DATA_START_CODE                0x01B2
#define USER_DATA_START_CODE_LENGTH         32

#define SHORT_VIDEO_START_MARKER            0x20
#define SHORT_VIDEO_START_MARKER_LENGTH     22


namespace android {


typedef struct __bit_buffer {
    uint8_t * start;
    size_t size;
    uint8_t * current;
    uint8_t read_bits;
} bit_buffer;

static void skip_bits(bit_buffer * bb, size_t nbits) {
    bb->current = bb->current + ((nbits + bb->read_bits) / 8);
    bb->read_bits = (uint8_t)((bb->read_bits + nbits) % 8);
}

static uint8_t get_bit(bit_buffer * bb) {
    uint8_t ret;
    ret = (*(bb->current) >> (7 - bb->read_bits)) & 0x1;
    if (bb->read_bits == 7) {
        bb->read_bits = 0;
        bb->current++;
    }
    else {
        bb->read_bits++;
    }
    return ret;
}

static uint32_t get_bits(bit_buffer * bb, size_t nbits) {
    uint32_t i, ret;
    ret = 0;
    for (i = 0; i < nbits; i++) {
        ret = (ret << 1) + get_bit(bb);
    }
    return ret;
}

static uint32_t exp_golomb_ue(bit_buffer * bb) {
    uint8_t bit, significant_bits;
    significant_bits = 0;
    bit = get_bit(bb);
    while (bit == 0) {
        significant_bits++;
        bit = get_bit(bb);
    }
    return (1 << significant_bits) + get_bits(bb, significant_bits) - 1;
}

static int32_t exp_golomb_se(bit_buffer * bb) {
    int32_t ret;
    ret = exp_golomb_ue(bb);
    if ((ret & 0x1) == 0) {
        return -(ret >> 1);
    }
    else {
        return (ret + 1) >> 1;
    }
}

static void parse_scaling_list(uint32_t size, bit_buffer * bb) {
    uint32_t last_scale, next_scale, i;
    int32_t delta_scale;
    last_scale = 8;
    next_scale = 8;
    for (i = 0; i < size; i++) {
        if (next_scale != 0) {
            delta_scale = exp_golomb_se(bb);
            next_scale = (last_scale + delta_scale + 256) % 256;
        }
        if (next_scale != 0) {
            last_scale = next_scale;
        }
    }
}

void parse_sps(uint8_t * sps,size_t sps_size,uint8_t *aprofile,uint8_t *alevel,uint32_t *num_ref_frames,uint8_t *interlaced)
{
    bit_buffer bb;
    uint32_t pic_order_cnt_type, width_in_mbs, height_in_map_units;
    uint32_t i, size, left, right, top, bottom;
    uint8_t frame_mbs_only_flag;
    uint8_t profile, level;
    uint32_t width,height;

    bb.start = sps;
    bb.size = sps_size;
    bb.current = sps;
    bb.read_bits = 0;

    /* skip first byte, since we already know we're parsing a SPS */
    skip_bits(&bb, 8);

    /* get profile */
    profile = (uint8_t) get_bits(&bb, 8);
    LOGV("AVC Profile %d",profile);

    /* skip 8 bits */
    skip_bits(&bb, 8);

    /* get level */
    level = (uint8_t) get_bits(&bb, 8);
    LOGV("AVC Level %d",level);

    *aprofile = profile;
    *alevel = level;

    /* read sps id, first exp-golomb encoded value */
    exp_golomb_ue(&bb);

    if (profile == 100 || profile == 110 || profile == 122 || profile == 144) {
        /* chroma format idx */
        if (exp_golomb_ue(&bb) == 3) {
            skip_bits(&bb, 1);
        }
        /* bit depth luma minus8 */
        exp_golomb_ue(&bb);
        /* bit depth chroma minus8 */
        exp_golomb_ue(&bb);
        /* Qpprime Y Zero Transform Bypass flag */
        skip_bits(&bb, 1);
        /* Seq Scaling Matrix Present Flag */
        if (get_bit(&bb)) {
            for (i = 0; i < 8; i++) {
                /* Seq Scaling List Present Flag */
                if (get_bit(&bb)) {
                    parse_scaling_list(i < 6 ? 16 : 64, &bb);
                }
            }
        }
    }
    /* log2_max_frame_num_minus4 */
    exp_golomb_ue(&bb);
    /* pic_order_cnt_type */
    pic_order_cnt_type = exp_golomb_ue(&bb);
    if (pic_order_cnt_type == 0) {
        /* log2_max_pic_order_cnt_lsb_minus4 */
        exp_golomb_ue(&bb);
    }
    else if (pic_order_cnt_type == 1) {
        /* delta_pic_order_always_zero_flag */
        skip_bits(&bb, 1);
        /* offset_for_non_ref_pic */
        exp_golomb_se(&bb);
        /* offset_for_top_to_bottom_field */
        exp_golomb_se(&bb);
        size = exp_golomb_ue(&bb);
        for (i = 0; i < size; i++) {
            /* offset_for_ref_frame */
            exp_golomb_se(&bb);
        }
    }

    /* num_ref_frames */
    *num_ref_frames = exp_golomb_ue(&bb);
    LOGV("AVC No of Ref frames %d",*num_ref_frames);

    /* gaps_in_frame_num_value_allowed_flag */
    skip_bits(&bb, 1);
    /* pic_width_in_mbs */
    width_in_mbs = exp_golomb_ue(&bb) + 1;
    /* pic_height_in_map_units */
    height_in_map_units = exp_golomb_ue(&bb) + 1;
    /* frame_mbs_only_flag */
    frame_mbs_only_flag = get_bit(&bb);

    LOGV("AVC frame_mbs_only_flag %d", frame_mbs_only_flag);

    /* 0-interlaced. 1-progressive */
    *interlaced = !frame_mbs_only_flag;

    if (!frame_mbs_only_flag) {
        /* mb_adaptive_frame_field */
        skip_bits(&bb, 1);
    }
    /* direct_8x8_inference_flag */
    skip_bits(&bb, 1);
    /* frame_cropping */
    left = right = top = bottom = 0;
    if (get_bit(&bb)) {
        left = exp_golomb_ue(&bb) * 2;
        right = exp_golomb_ue(&bb) * 2;
        top = exp_golomb_ue(&bb) * 2;
        bottom = exp_golomb_ue(&bb) * 2;
        if (!frame_mbs_only_flag) {
            top *= 2;
            bottom *= 2;
        }
    }

    /* width */
    width = width_in_mbs * 16 - (left + right);
    /* height */
    height = height_in_map_units * 16 - (top + bottom);
    if (!frame_mbs_only_flag) {
        height *= 2;
    }
}


void ReadBits(bit_buffer *bb, int nbits, uint32_t* val)
{
    *val = get_bits(bb, nbits);
}

int16_t Search4VOLHeader(bit_buffer *psBits)
{
    uint32_t i;
    int32_t count = 0;
    int16_t status = -1;

    uint32_t curr_state = 0;
    bool found = false;

     /* search 0x00 0x00 0x01 0x20 */

     /* Byte alignment*/
     while(psBits->read_bits)
     {
         ReadBits(psBits,1,&i);
     }

     do{
        ReadBits(psBits,8,&i);
        switch(curr_state)
        {
            case 0:
                      if(i==0) curr_state = 1;      /* just got 00 */
                          else curr_state = 0;
                      break;
            case 1:
                      if(i==0) curr_state = 2;      /* Now 00. Got 00*/
                      else curr_state = 0;
                      break;
            case 2:
                      if(i==1) curr_state = 3;      /* Now 01. Got 00 00 */
                      else if(i==0) curr_state = 2; /* Now 00. Got 00 00*/
                      else curr_state = 0;
                      break;
            case 3:
                      if(i==0x20) found = true;     /*Now 20. Got 00 00 01*/
                      else curr_state = 0;
                      break;
        };

     }while((psBits->current < psBits->start + psBits->size)  && !found);

     if(found)
       status = 0;
     else
       status = -1;

    return status;
}

int16_t parse_vol(uint8_t *vol,size_t vol_size,uint8_t *aprofile,uint8_t *alevel,uint32_t *num_ref_frames,uint8_t* interlaced)
{
    int16_t iErrorStat;
    uint32_t codeword;
    uint32_t time_increment_resolution, nbits_time_increment;
    uint32_t i, j;

    uint32_t display_width, display_height;
    uint32_t width,height;

    int32_t* profilelevel;


    bit_buffer bb;
    bb.start = vol;
    bb.size = vol_size;
    bb.current = vol;
    bb.read_bits = 0;

    bit_buffer *psBits = &bb;

    uint32_t status =  0;

    while(psBits->current <(psBits->start + psBits->size)){

    /* detect VOL start code 00 00 01 20 */
    uint32_t status = Search4VOLHeader(psBits);

        uint32_t vol_id;

        /* vol_id (4 bits) */
        //ReadBits(psBits, 4, & vol_id);

        // RandomAccessibleVOLFlag
        ReadBits(psBits, 1, &codeword);

        //Video Object Type Indication
        ReadBits(psBits, 8, &codeword);
        if (codeword != 1)
        {
            //return MP4_INVALID_VOL_PARAM; //TI supports this feature
        }

        // is_object_layer_identifier
        ReadBits(psBits, 1, &codeword);

        if (codeword)
        {
            ReadBits(psBits, 4, &codeword);
            ReadBits(psBits, 3, &codeword);
        }

        // aspect ratio
        ReadBits(psBits, 4, &codeword);

        if (codeword == 0xF)
        {
            // Extended Parameter
            /* width */
            ReadBits(psBits, 8, &codeword);
            /* height */
            ReadBits(psBits, 8, &codeword);
        }

        ReadBits(psBits, 1, &codeword);

        if (codeword)
        {
            ReadBits(psBits, 2, &codeword);
            if (codeword != 1)
            {
                return MP4_INVALID_VOL_PARAM;
            }

            ReadBits(psBits, 1, &codeword);

            if (!codeword)
            {
                //return MP4_INVALID_VOL_PARAM; //TI supports this feature
            }

            ReadBits(psBits, 1, &codeword);
            if (codeword)   /* if (vbv_parameters) {}, page 36 */
            {
                ReadBits(psBits, 15, &codeword);
                ReadBits(psBits, 1, &codeword);
                if (codeword != 1)
                {
                    return MP4_INVALID_VOL_PARAM;
                }

                ReadBits(psBits, 15, &codeword);
                ReadBits(psBits, 1, &codeword);
                if (codeword != 1)
                {
                    return MP4_INVALID_VOL_PARAM;
                }


                ReadBits(psBits, 19, &codeword);
                if (!(codeword & 0x8))
                {
                    return MP4_INVALID_VOL_PARAM;
                }

                ReadBits(psBits, 11, &codeword);
                ReadBits(psBits, 1, &codeword);
                if (codeword != 1)
                {
                    return MP4_INVALID_VOL_PARAM;
                }

                ReadBits(psBits, 15, &codeword);
                ReadBits(psBits, 1, &codeword);
                if (codeword != 1)
                {
                    return MP4_INVALID_VOL_PARAM;
                }
            }

        }

        ReadBits(psBits, 2, &codeword);

        if (codeword != 0)
        {
            return MP4_INVALID_VOL_PARAM;
        }

        ReadBits(psBits, 1, &codeword);
        if (codeword != 1)
        {
            return MP4_INVALID_VOL_PARAM;
        }

        ReadBits(psBits, 16, &codeword);
        time_increment_resolution = codeword;


        ReadBits(psBits, 1, &codeword);
        if (codeword != 1)
        {
            return MP4_INVALID_VOL_PARAM;
        }



        ReadBits(psBits, 1, &codeword);

        if (codeword && time_increment_resolution > 2)
        {
            i = time_increment_resolution - 1;
            j = 1;
            while (i >>= 1)
            {
                j++;
            }
            nbits_time_increment = j;

            ReadBits(psBits, nbits_time_increment, &codeword);
        }

        ReadBits(psBits, 1, &codeword);
        if (codeword != 1)
        {
            return MP4_INVALID_VOL_PARAM;
        }

        /* this should be 176 for QCIF */
        ReadBits(psBits, 13, &codeword);
        display_width = (uint32_t)codeword;
        ReadBits(psBits, 1, &codeword);
        if (codeword != 1)
        {
            return MP4_INVALID_VOL_PARAM;
        }

        /* this should be 144 for QCIF */
        ReadBits(psBits, 13, &codeword);
        display_height = (uint32_t)codeword;

        width = (display_width + 15) & -16;
        height = (display_height + 15) & -16;

        /* marker */
        ReadBits(psBits, 1, &codeword);

        /* interlaced */
        ReadBits(psBits, 1, &codeword);
        LOGD("INTERLACE 0x%x",codeword);

        if(codeword == 1)
        {
            *interlaced = (uint8_t) 1;
        }
        else
        {
            *interlaced = (uint8_t) 0;
        }
        return 0;

    }

    return 0;
}

void updateMetaData(sp<MetaData> meta_track)
{
    uint32_t type;
    const void *data;
    size_t size;

    uint8_t profile,level,interlaced;
    uint32_t num_ref_frames;

    interlaced = 0;
    profile = 0;
    level = 0;
    num_ref_frames = 0;

    if ( meta_track->findData(kKeyESDS, &type, &data, &size)) {
        LOGV("MPEG4 Header");
        LOGV("size %d",size);
        LOGV("type 0x%x", type);
        LOGV("data 0x%x", data);

        uint8_t *ptr = (uint8_t *)data;

        parse_vol(ptr, size, &profile, &level, &num_ref_frames, &interlaced);

        LOGV("MPEG4  Profile %d Level %d RefFrames %d Interlaced %d ", profile,level,num_ref_frames,interlaced);
        meta_track->setInt32(kKeyVideoProfile, profile);
        meta_track->setInt32(kKeyVideoLevel, level);
        meta_track->setInt32(kKeyVideoInterlaced, interlaced);
        meta_track->setInt32(kKeyNumRefFrames, num_ref_frames);


    }
    else if ( meta_track->findData(kKeyAVCC, &type, &data, &size)) {
        LOGV("H264 Header");
        LOGV("size %d",size);
        LOGV("type 0x%x", type);
        LOGV("data 0x%x", data);

        const uint8_t *ptr = (const uint8_t *)data;

        CHECK(size >= 7);
        CHECK_EQ(ptr[0], 1);  // configurationVersion == 1

        size_t numSeqParameterSets = ptr[5] & 31;

        LOGV("numSeqParameterSets %d",numSeqParameterSets);

        ptr += 6;
        size -= 6;

        for (size_t i = 0; i < numSeqParameterSets; ++i) {
            CHECK(size >= 2);
            size_t length = U16_AT(ptr);

            ptr += 2;
            size -= 2;

            CHECK(size >= length);

            parse_sps((uint8_t*)ptr, length, &profile, &level, &num_ref_frames, &interlaced);

            LOGV("H264 Profile %d Level %d RefFrames %d Interlaced %d", profile,level,num_ref_frames,interlaced);
            meta_track->setInt32(kKeyVideoProfile, profile);
            meta_track->setInt32(kKeyVideoLevel, level);
            meta_track->setInt32(kKeyNumRefFrames, num_ref_frames);
            meta_track->setInt32(kKeyVideoInterlaced, interlaced);

            ptr += length;
            size -= length;
        }

    }

    return;
}


}  // namespace android


#endif

