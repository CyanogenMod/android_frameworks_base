/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2011 Code Aurora Forum
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

#include <arpa/inet.h>

#include <media/stagefright/Utils.h>
#ifdef QCOM_HARDWARE
#include <media/stagefright/MediaDebug.h>
#define NIDEBUG 0
#include <utils/Log.h>
#undef LOG_TAG
#define LOG_TAG "Utils"

#include <utils/Errors.h>

namespace {

#define STD_MIN(x,y) (((x) < (y)) ? (x) : (y))

  class RbspParser
  {
    public:
      RbspParser(const uint8_t * begin, const uint8_t * end);

      virtual ~ RbspParser();

      uint32_t next();
      void advance();
      uint32_t u(uint32_t n);
      uint32_t ue();
      int32_t se();

    private:
      const uint8_t *begin, *end;
      int32_t pos;
      uint32_t bit;
      uint32_t cursor;
      bool advanceNeeded;
  };

  RbspParser::RbspParser(const uint8_t * _begin, const uint8_t * _end)
    :begin(_begin), end(_end), pos(-1), bit(0),
    cursor(0xFFFFFF), advanceNeeded(true)
  {
  }

  // Destructor
  /*lint -e{1540}  Pointer member neither freed nor zeroed by destructor
   * No problem
   */
  RbspParser::~RbspParser()
  {
  }

  // Return next RBSP byte as a word
  uint32_t RbspParser::next()
  {
    if (advanceNeeded)
      advance();
    //return static_cast<uint32> (*pos);
    return static_cast < uint32_t > (begin[pos]);
  }

  // Advance RBSP decoder to next byte
  void RbspParser::advance()
  {
    ++pos;
    //if (pos >= stop)
    if (begin + pos == end) {
      /*lint -e{730}  Boolean argument to function
       * I don't see a problem here
       */
      //throw false;
        LOGE("H264Parser-->NEED TO THROW THE EXCEPTION...\n");
    }
    cursor <<= 8;
    //cursor |= static_cast<uint32> (*pos);
    cursor |= static_cast < uint32_t > (begin[pos]);
    if ((cursor & 0xFFFFFF) == 0x000003) {
      advance();
    }
    advanceNeeded = false;
  }

  // Decode unsigned integer
  uint32_t RbspParser::u(uint32_t n)
  {
    uint32_t i, s, x = 0;
    for (i = 0; i < n; i += s) {
      s = static_cast < uint32_t > STD_MIN(static_cast < int >(8 - bit),
          static_cast < int >(n - i));
      x <<= s;

      x |= ((next() >> ((8 - static_cast < uint32_t > (bit)) - s)) &
          ((1 << s) - 1));

      bit = (bit + s) % 8;
      if (!bit) {
        advanceNeeded = true;
      }
    }
    return x;
  }

  // Decode unsigned integer Exp-Golomb-coded syntax element
  uint32_t RbspParser::ue()
  {
    int leadingZeroBits = -1;
    for (uint32_t b = 0; !b; ++leadingZeroBits) {
      b = u(1);
    }
    return ((1 << leadingZeroBits) - 1) +
      u(static_cast < uint32_t > (leadingZeroBits));
  }

  // Decode signed integer Exp-Golomb-coded syntax element
  int32_t RbspParser::se()
  {
    const uint32_t x = ue();
    if (!x)
      return 0;
    else if (x & 1)
      return static_cast < int32_t > ((x >> 1) + 1);
    else
      return -static_cast < int32_t > (x >> 1);
  }
}
#endif

namespace android {

uint16_t U16_AT(const uint8_t *ptr) {
    return ptr[0] << 8 | ptr[1];
}

uint32_t U32_AT(const uint8_t *ptr) {
    return ptr[0] << 24 | ptr[1] << 16 | ptr[2] << 8 | ptr[3];
}

uint64_t U64_AT(const uint8_t *ptr) {
    return ((uint64_t)U32_AT(ptr)) << 32 | U32_AT(ptr + 4);
}

uint16_t U16LE_AT(const uint8_t *ptr) {
    return ptr[0] | (ptr[1] << 8);
}

uint32_t U32LE_AT(const uint8_t *ptr) {
    return ptr[3] << 24 | ptr[2] << 16 | ptr[1] << 8 | ptr[0];
}

uint64_t U64LE_AT(const uint8_t *ptr) {
    return ((uint64_t)U32LE_AT(ptr + 4)) << 32 | U32LE_AT(ptr);
}

// XXX warning: these won't work on big-endian host.
uint64_t ntoh64(uint64_t x) {
    return ((uint64_t)ntohl(x & 0xffffffff) << 32) | ntohl(x >> 32);
}

uint64_t hton64(uint64_t x) {
    return ((uint64_t)htonl(x & 0xffffffff) << 32) | htonl(x >> 32);
}

#ifdef QCOM_HARDWARE
status_t
parseSps (uint16_t naluSize, const uint8_t *encodedBytes, SpsInfo * info) {

    if(!encodedBytes || naluSize <= 0)
        return BAD_VALUE;

    uint8_t profile_id = 0;
    uint8_t level_id = 0;
    uint8_t tmp = 0;
    uint32_t id = 0;
    uint8_t log2MaxFrameNumMinus4 = 0;
    uint8_t picOrderCntType = 0;
    uint8_t log2MaxPicOrderCntLsbMinus4 = 0;
    uint8_t deltaPicOrderAlwaysZeroFlag = 0;
    uint32_t numRefFramesInPicOrderCntCycle = 0;
    uint8_t picWidthInMbsMinus1 = 0;
    uint8_t picHeightInMapUnitsMinus1 = 0;
    uint8_t frameMbsOnlyFlag = 0;
    uint8_t cropLeft= 0, cropRight = 0, cropTop = 0, cropBot = 0;

    RbspParser rbsp(&encodedBytes[0],
                    &encodedBytes[naluSize]);
    profile_id = rbsp.u(8);
    tmp = rbsp.u(8);
    level_id = rbsp.u(8);
    id = rbsp.ue();

    LOGV("profile_id = %u", profile_id);

    if(100 == profile_id) {
        tmp = rbsp.ue();
        if(3 == tmp) {
            (void)rbsp.u(1);
        }
        //bit_depth_luma_minus8
        (void)rbsp.ue();
        //bit_depth_chroma_minus8
        (void)rbsp.ue();
        //qpprime_y_zero_transform_bypass_flag
        (void)rbsp.u(1);
        // seq_scaling_matrix_present_flag
        tmp = rbsp.u(1);
        if (tmp) {
            unsigned int tmp1, t;
            //seq_scaling_list_present_flag
            for (t = 0; t < 6; t++) {
                tmp1 = rbsp.u(1);
                if (tmp1) {
                    unsigned int last_scale = 8, next_scale = 8, delta_scale;
                    for (int j = 0; j < 16; j++)
                        {
                            if (next_scale) {
                                delta_scale = rbsp.se();
                                next_scale = (last_scale + delta_scale + 256) % 256;
                            }
                            last_scale = next_scale?next_scale:last_scale;
                        }
                }
            }
            for (t = 0; t < 2; t++) {
                tmp1 = rbsp.u(1);
                if (tmp1) {
                    unsigned int last_scale = 8, next_scale = 8, delta_scale;
                    for (int j = 0; j < 64; j++)
                        {
                            if (next_scale) {
                                delta_scale = rbsp.se();
                                next_scale = (last_scale + delta_scale + 256) % 256;
                            }
                            last_scale = next_scale?next_scale : last_scale;
                        }
                }
            }
        }
    }

    log2MaxFrameNumMinus4 = rbsp.ue();
    picOrderCntType = rbsp.ue();
    if(0 == picOrderCntType) {
        log2MaxPicOrderCntLsbMinus4 = rbsp.ue();
    } else if(1 == picOrderCntType) {
        deltaPicOrderAlwaysZeroFlag = (rbsp.u(1) == 1);
        (void)rbsp.se();
        (void)rbsp.se();
        numRefFramesInPicOrderCntCycle = rbsp.ue();
        for (uint32_t i = 0; i < numRefFramesInPicOrderCntCycle; ++i) {
            (void)rbsp.se();
        }
    }
    info->mNumRefFrames = rbsp.ue();
    tmp = rbsp.u(1);
    picWidthInMbsMinus1 = rbsp.ue();
    picHeightInMapUnitsMinus1 = rbsp.ue();
    frameMbsOnlyFlag = (rbsp.u(1) == 1);
    if(!frameMbsOnlyFlag)
        (void)rbsp.u(1);
    (void)rbsp.u(1);
      tmp = rbsp.u(1);
      cropLeft = 0;
      cropRight = 0;
      cropTop = 0;
      cropBot = 0;
      if(tmp) {
          cropLeft = rbsp.ue();
          cropRight = rbsp.ue();
          cropTop = rbsp.ue();
          cropBot = rbsp.ue();
          LOGV("crop (%d,%d,%d,%d)", cropLeft, cropRight, cropTop, cropBot);
      }
      info->mHeightInMBs = (2 - frameMbsOnlyFlag ) * (picHeightInMapUnitsMinus1 + 1);
      info->mWidthInMBs = picWidthInMbsMinus1 + 1;
      info->mProfile = profile_id;
      info->mLevel = level_id;
      info->mInterlaced = !frameMbsOnlyFlag;
      LOGV("mInterlaced = 0x%x", info->mInterlaced );
      return OK;
}
#endif

}  // namespace android

