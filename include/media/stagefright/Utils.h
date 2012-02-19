/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef UTILS_H_

#define UTILS_H_

#include <stdint.h>
#ifdef QCOM_HARDWARE
#include <utils/Errors.h>
#endif

namespace android {

#define FOURCC(c1, c2, c3, c4) \
    (c1 << 24 | c2 << 16 | c3 << 8 | c4)

uint16_t U16_AT(const uint8_t *ptr);
uint32_t U32_AT(const uint8_t *ptr);
uint64_t U64_AT(const uint8_t *ptr);

uint16_t U16LE_AT(const uint8_t *ptr);
uint32_t U32LE_AT(const uint8_t *ptr);
uint64_t U64LE_AT(const uint8_t *ptr);

uint64_t ntoh64(uint64_t x);
uint64_t hton64(uint64_t x);

#ifdef QCOM_HARDWARE
typedef struct {
    uint8_t mProfile;
    uint8_t mLevel;
    int32_t mHeightInMBs;
    int32_t mWidthInMBs;
    int32_t mNumRefFrames;
    int32_t mInterlaced;
} SpsInfo;

status_t
parseSps(uint16_t naluSize,const uint8_t *encodedBytes, SpsInfo *info);
#endif

}  // namespace android

#endif  // UTILS_H_
