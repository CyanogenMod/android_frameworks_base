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

#ifndef SAMPLE_TABLE_H_

#define SAMPLE_TABLE_H_

#include <sys/types.h>
#include <stdint.h>

#include <media/stagefright/MediaErrors.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

namespace android {

class DataSource;
struct SampleIterator;

class SampleTable : public RefBase {
public:
    SampleTable(const sp<DataSource> &source);

    // type can be 'stco' or 'co64'.
    status_t setChunkOffsetParams(
            uint32_t type, off_t data_offset, size_t data_size);

    status_t setSampleToChunkParams(off_t data_offset, size_t data_size);

    // type can be 'stsz' or 'stz2'.
    status_t setSampleSizeParams(
            uint32_t type, off_t data_offset, size_t data_size);

    status_t setTimeToSampleParams(off_t data_offset, size_t data_size);

#ifdef OMAP_ENHANCEMENT
    status_t setTimeToSampleParamsCtts(off_t data_offset, size_t data_size);
#endif

    status_t setSyncSampleParams(off_t data_offset, size_t data_size);

    ////////////////////////////////////////////////////////////////////////////

    uint32_t countChunkOffsets() const;

    uint32_t countSamples() const;

    status_t getMaxSampleSize(size_t *size);

    status_t getMetaDataForSample(
            uint32_t sampleIndex,
            off_t *offset,
            size_t *size,
            uint32_t *decodingTime,
            bool *isSyncSample = NULL);

    enum {
        kFlagBefore,
        kFlagAfter,
        kFlagClosest
    };
    status_t findSampleAtTime(
            uint32_t req_time, uint32_t *sample_index, uint32_t flags);

    status_t findSyncSampleNear(
            uint32_t start_sample_index, uint32_t *sample_index,
            uint32_t flags);

    status_t findThumbnailSample(uint32_t *sample_index);
#ifdef OMAP_ENHANCEMENT
	int32_t *mTimeToSampleCtts;
#endif
protected:
    ~SampleTable();

private:
    static const uint32_t kChunkOffsetType32;
    static const uint32_t kChunkOffsetType64;
    static const uint32_t kSampleSizeType32;
    static const uint32_t kSampleSizeTypeCompact;

    sp<DataSource> mDataSource;
    Mutex mLock;

    off_t mChunkOffsetOffset;
    uint32_t mChunkOffsetType;
    uint32_t mNumChunkOffsets;

    off_t mSampleToChunkOffset;
    uint32_t mNumSampleToChunkOffsets;

    off_t mSampleSizeOffset;
    uint32_t mSampleSizeFieldSize;
    uint32_t mDefaultSampleSize;
    uint32_t mNumSampleSizes;

    uint32_t mTimeToSampleCount;
    uint32_t *mTimeToSample;

    off_t mSyncSampleOffset;
    uint32_t mNumSyncSamples;
    uint32_t *mSyncSamples;
    size_t mLastSyncSampleIndex;

    SampleIterator *mSampleIterator;

    struct SampleToChunkEntry {
        uint32_t startChunk;
        uint32_t samplesPerChunk;
        uint32_t chunkDesc;
    };
    SampleToChunkEntry *mSampleToChunkEntries;

    friend struct SampleIterator;

    status_t getSampleSize_l(uint32_t sample_index, size_t *sample_size);

    SampleTable(const SampleTable &);
    SampleTable &operator=(const SampleTable &);

#ifdef OMAP_ENHANCEMENT
    uint32_t mTimeToSampleCountCtts;
    int32_t *mCttsSampleBuffer;
#endif

};

}  // namespace android

#endif  // SAMPLE_TABLE_H_
