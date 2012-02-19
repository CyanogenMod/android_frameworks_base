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

#ifndef AAC_DECODER_H_

#define AAC_DECODER_H_

#include <media/stagefright/MediaSource.h>
#ifdef QCOM_HARDWARE
#define AAC_MAX_FORMAT_BLOCK_SIZE 16
#endif

struct tPVMP4AudioDecoderExternal;

namespace android {

struct MediaBufferGroup;
struct MetaData;

struct AACDecoder : public MediaSource {
    AACDecoder(const sp<MediaSource> &source);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

protected:
    virtual ~AACDecoder();

private:
    sp<MetaData>    mMeta;
    sp<MediaSource> mSource;
    bool mStarted;

    MediaBufferGroup *mBufferGroup;

    tPVMP4AudioDecoderExternal *mConfig;
    void *mDecoderBuf;
    int64_t mAnchorTimeUs;
    int64_t mNumSamplesOutput;
    status_t mInitCheck;
    int64_t  mNumDecodedBuffers;
    int32_t  mUpsamplingFactor;

    MediaBuffer *mInputBuffer;
#ifdef QCOM_HARDWARE
    uint8_t mFormatBlock[AAC_MAX_FORMAT_BLOCK_SIZE];

    // Temporary buffer to store incomplete frame buffers
    uint8_t* mTempInputBuffer;        // data ptr
    uint32_t mTempBufferTotalSize;    // total size allocated
    uint32_t mTempBufferDataLen;      // actual data length
    uint32_t mInputBufferSize;         // input data length
#endif

    status_t initCheck();
    AACDecoder(const AACDecoder &);
    AACDecoder &operator=(const AACDecoder &);
};

}  // namespace android

#endif  // AAC_DECODER_H_
