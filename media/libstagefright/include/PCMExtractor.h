/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Author: Andreas Gustafsson (andreas.a.gustafsson@stericsson.com)
 *         for ST-Ericsson
 */

#ifndef PCM_EXTRACTOR_H_

#define PCM_EXTRACTOR_H_

#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaExtractor.h>

using namespace android;

namespace android {

class DataSource;
class String8;

class PCMExtractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    PCMExtractor(const sp<DataSource> &source);

    virtual size_t countTracks();
    virtual sp<MediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();

protected:
    virtual ~PCMExtractor();

private:
    sp<DataSource> mDataSource;
    status_t mInitCheck;
    bool mValidFormat;
    uint16_t mNumChannels;
    uint32_t mSampleRate;
    uint16_t mBitsPerSample;
    off_t mDataOffset;
    size_t mDataSize;
    sp<MetaData> mTrackMeta;

    status_t init();

    PCMExtractor(const PCMExtractor &);
    PCMExtractor &operator=(const PCMExtractor &);
};

}  // namespace android

#endif  // PCM_EXTRACTOR_H_
