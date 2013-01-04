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

//#define LOG_NDEBUG 0
#define LOG_TAG "PCMExtractor"
#include <utils/Log.h>

#include "include/PCMExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>

namespace android {

static const uint16_t kDefaultNumChannels = 2;
static const uint32_t kDefaultSampleRate = 48000;
static const uint16_t kDefaultFormat = 16;

struct PCMSource : public MediaSource {
    PCMSource(
            const sp<DataSource> &dataSource,
            const sp<MetaData> &meta,
            int32_t bitsPerSample,
            off_t offset, size_t size);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~PCMSource();

private:
    static const size_t kMaxFrameSize;

    sp<DataSource> mDataSource;
    sp<MetaData> mMeta;
    int32_t mSampleRate;
    int32_t mNumChannels;
    int32_t mBitsPerSample;
    off_t mOffset;
    size_t mSize;
    bool mStarted;
    MediaBufferGroup *mGroup;
    off_t mCurrentPos;
    uint32_t mBufferSize;

    PCMSource(const PCMSource &);
    PCMSource &operator=(const PCMSource &);
};

PCMExtractor::PCMExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mValidFormat(false) {
    mInitCheck = init();
}

PCMExtractor::~PCMExtractor() {
}

sp<MetaData> PCMExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, "audio/raw");

    return meta;
}

size_t PCMExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<MediaSource> PCMExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return new PCMSource(
            mDataSource, mTrackMeta,
            mBitsPerSample, mDataOffset, mDataSize);
}

sp<MetaData> PCMExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return mTrackMeta;
}

status_t PCMExtractor::init() {
    mNumChannels = kDefaultNumChannels;
    mSampleRate = kDefaultSampleRate;
    mBitsPerSample = kDefaultFormat;
    mDataOffset = 0;
    mDataSize = 0;
    mValidFormat = true;
    mTrackMeta = new MetaData;mTrackMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    mTrackMeta->setInt32(kKeyChannelCount, mNumChannels);
    mTrackMeta->setInt32(kKeySampleRate, mSampleRate);
    return OK;
}

const size_t PCMSource::kMaxFrameSize = 4800;

PCMSource::PCMSource(
        const sp<DataSource> &dataSource,
        const sp<MetaData> &meta,
        int32_t bitsPerSample,
        off_t offset, size_t size)
    : mDataSource(dataSource),
      mMeta(meta),
      mSampleRate(0),
      mNumChannels(0),
      mBitsPerSample(bitsPerSample),
      mOffset(offset),
      mSize(size),
      mStarted(false),
      mGroup(NULL),
      mBufferSize(0) {
    CHECK(mMeta->findInt32(kKeySampleRate, &mSampleRate));
    CHECK(mMeta->findInt32(kKeyChannelCount, &mNumChannels));
}

PCMSource::~PCMSource() {
    if (mStarted) {
        stop();
    }
}

status_t PCMSource::start(MetaData *params) {
    CHECK(!mStarted);

    mBufferSize = kMaxFrameSize;
    mGroup = new MediaBufferGroup;
    mGroup->add_buffer(new MediaBuffer(mBufferSize));

    if (mBitsPerSample == 8) {
        // As a temporary buffer for 8->16 bit conversion.
        mGroup->add_buffer(new MediaBuffer(mBufferSize));
    }

    mCurrentPos = mOffset;

    mStarted = true;
    return OK;
}

status_t PCMSource::stop() {

    CHECK(mStarted);
    delete mGroup;
    mGroup = NULL;

    mStarted = false;
    return OK;
}

sp<MetaData> PCMSource::getFormat() {
   return mMeta;
}

status_t PCMSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;
    int64_t seekTimeUs;
    ReadOptions::SeekMode seek = ReadOptions::SEEK_CLOSEST_SYNC;
    if (options != NULL && options->getSeekTo(&seekTimeUs,&seek)) {
        int64_t pos = (seekTimeUs * mSampleRate) / 1000000 * mNumChannels * 2;
        if (pos > mSize) {
            pos = mSize;
        }
        mCurrentPos = pos + mOffset;
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }

    ssize_t n = mDataSource->readAt(
            mCurrentPos, buffer->data(), mBufferSize);
    if (n <= 0) {
        buffer->release();
        buffer = NULL;
        return ERROR_END_OF_STREAM;
    }

    mCurrentPos += n;

    buffer->set_range(0, n);

    if (mBitsPerSample == 8) {
        // Convert 8-bit unsigned samples to 16-bit signed.

        MediaBuffer *tmp;
        CHECK_EQ(mGroup->acquire_buffer(&tmp), OK);

        // The new buffer holds the sample number of samples, but each
        // one is 2 bytes wide.
        tmp->set_range(0, 2 * n);

        int16_t *dst = (int16_t *)tmp->data();
        const uint8_t *src = (const uint8_t *)buffer->data();
        while (n-- > 0) {
            *dst++ = ((int16_t)(*src) - 128) * 256;
            ++src;
        }

        buffer->release();
        buffer = tmp;
    } else if (mBitsPerSample == 24) {
        // Convert 24-bit signed samples to 16-bit signed.

        const uint8_t *src =
            (const uint8_t *)buffer->data() + buffer->range_offset();
        int16_t *dst = (int16_t *)src;

        size_t numSamples = buffer->range_length() / 3;
        for (size_t i = 0; i < numSamples; ++i) {
            int32_t x = (int32_t)(src[0] | src[1] << 8 | src[2] << 16);
            x = (x << 8) >> 8;  // sign extension

            x = x >> 8;
            *dst++ = (int16_t)x;
            src += 3;
        }

        buffer->set_range(buffer->range_offset(), 2 * numSamples);
    }

    size_t bytesPerSample = mBitsPerSample >> 3;

    buffer->meta_data()->setInt64(
            kKeyTime,
            1000000LL * (mCurrentPos - mOffset)
                / (mNumChannels * bytesPerSample) / mSampleRate);


    *out = buffer;

    return OK;
}

}  // namespace android
