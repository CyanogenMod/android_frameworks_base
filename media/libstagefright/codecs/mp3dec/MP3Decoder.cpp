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

#include "MP3Decoder.h"

#include "include/pvmp3decoder_api.h"

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

namespace android {

// Everything must match except for
// protection, bitrate, padding, private bits, mode extension,
// copyright bit, original bit and emphasis.
// Yes ... there are things that must indeed match...
static const uint32_t kMask = 0xfffe0cc0;

static bool get_mp3_frame_size(
        uint32_t header, size_t *frame_size,
        int *out_sampling_rate = NULL, int *out_channels = NULL,
        int *out_bitrate = NULL) {
    *frame_size = 0;

    if (out_sampling_rate) {
        *out_sampling_rate = 0;
    }

    if (out_channels) {
        *out_channels = 0;
    }

    if (out_bitrate) {
        *out_bitrate = 0;
    }

    if ((header & 0xffe00000) != 0xffe00000) {
        return false;
    }

    unsigned version = (header >> 19) & 3;

    if (version == 0x01) {
        return false;
    }

    unsigned layer = (header >> 17) & 3;

    if (layer == 0x00) {
        return false;
    }

    unsigned protection = (header >> 16) & 1;

    unsigned bitrate_index = (header >> 12) & 0x0f;

    if (bitrate_index == 0 || bitrate_index == 0x0f) {
        // Disallow "free" bitrate.
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;

    if (sampling_rate_index == 3) {
        return false;
    }

    static const int kSamplingRateV1[] = { 44100, 48000, 32000 };
    int sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    } else if (version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;

    if (layer == 3) {
        // layer I

        static const int kBitrateV1[] = {
            32, 64, 96, 128, 160, 192, 224, 256,
            288, 320, 352, 384, 416, 448
        };

        static const int kBitrateV2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256
        };

        int bitrate =
            (version == 3 /* V1 */)
                ? kBitrateV1[bitrate_index - 1]
                : kBitrateV2[bitrate_index - 1];

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        *frame_size = (12000 * bitrate / sampling_rate + padding) * 4;
    } else {
        // layer II or III

        static const int kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const int kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const int kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        int bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */)
                ? kBitrateV1L2[bitrate_index - 1]
                : kBitrateV1L3[bitrate_index - 1];
        } else {
            // V2 (or 2.5)

            bitrate = kBitrateV2[bitrate_index - 1];
        }

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        if (version == 3 /* V1 */) {
            *frame_size = 144000 * bitrate / sampling_rate + padding;
        } else {
            // V2 or V2.5
            *frame_size = 72000 * bitrate / sampling_rate + padding;
        }
    }

    if (out_sampling_rate) {
        *out_sampling_rate = sampling_rate;
    }

    if (out_channels) {
        int channel_mode = (header >> 6) & 3;

        *out_channels = (channel_mode == 3) ? 1 : 2;
    }

    return true;
}

static bool resync(
        uint8_t *data, uint32_t size, uint32_t match_header, off_t *out_pos) {

    bool valid = false;
    off_t pos = 0;
    *out_pos = 0;
    do {
        if (pos + 4 > size) {
            // Don't scan forever.
            LOGV("no dice, no valid sequence of frames found.");
            break;
        }

        uint32_t header = U32_AT(data + pos);

        if (match_header != 0 && (header & kMask) != (match_header & kMask)) {
            ++pos;
            continue;
        }

        LOGV("found possible frame at %ld (header = 0x%08x)", pos, header);

        // We found what looks like a valid frame,
        valid = true;
        *out_pos = pos;
    } while (!valid);

    return valid;
}


MP3Decoder::MP3Decoder(const sp<MediaSource> &source)
    : mSource(source),
      mNumChannels(0),
      mStarted(false),
      mBufferGroup(NULL),
      mConfig(new tPVMP3DecoderExternal),
      mDecoderBuf(NULL),
      mAnchorTimeUs(0),
      mNumFramesOutput(0),
      mInputBuffer(NULL),
      mPartialBuffer(NULL),
      mFixedHeader(0) {
    init();
}

void MP3Decoder::init() {
    sp<MetaData> srcFormat = mSource->getFormat();

    int32_t sampleRate;
    CHECK(srcFormat->findInt32(kKeyChannelCount, &mNumChannels));
    CHECK(srcFormat->findInt32(kKeySampleRate, &sampleRate));

    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    mMeta->setInt32(kKeyChannelCount, mNumChannels);
    mMeta->setInt32(kKeySampleRate, sampleRate);

    int64_t durationUs;
    if (srcFormat->findInt64(kKeyDuration, &durationUs)) {
        mMeta->setInt64(kKeyDuration, durationUs);
    }

    mMeta->setCString(kKeyDecoderComponent, "MP3Decoder");
}

MP3Decoder::~MP3Decoder() {
    if (mStarted) {
        stop();
    }

    delete mConfig;
    mConfig = NULL;
}

status_t MP3Decoder::start(MetaData *params) {
    CHECK(!mStarted);

    mBufferGroup = new MediaBufferGroup;
    mBufferGroup->add_buffer(new MediaBuffer(4608 * 2));

    mConfig->equalizerType = flat;
    mConfig->crcEnabled = false;

    uint32_t memRequirements = pvmp3_decoderMemRequirements();
    mDecoderBuf = malloc(memRequirements);

    pvmp3_InitDecoder(mConfig, mDecoderBuf);

    mSource->start();

    mAnchorTimeUs = 0;
    mNumFramesOutput = 0;
    mStarted = true;

    return OK;
}

status_t MP3Decoder::stop() {
    CHECK(mStarted);

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    free(mDecoderBuf);
    mDecoderBuf = NULL;

    delete mBufferGroup;
    mBufferGroup = NULL;

    mSource->stop();

    mStarted = false;

    return OK;
}

sp<MetaData> MP3Decoder::getFormat() {
    return mMeta;
}

status_t MP3Decoder::updatePartialFrame() {
    status_t err = OK;
    if (mPartialBuffer == NULL) {
        return err;
    }

    size_t frameSize = 0;
    uint32_t partialBufLen = mPartialBuffer->range_length();
    uint32_t inputBufLen = mInputBuffer->range_length();
    uint8_t frameHeader[4];
    uint8_t *frmHdr;
    uint32_t header;


    // Look at the frame size and complete the partial frame
    // Also check if a vaild header is found after the partial frame
    if (partialBufLen < 4) { // check if partial frame has the 4 bytes header
        if (inputBufLen < (4 - partialBufLen)) {
            // input buffer does not have the frame header bytes
            // bail out TODO
            LOGE("MP3Decoder::updatePartialFrame buffer to small header not found"
                 " partial buffer len %d, input buffer len %d",
                 partialBufLen, inputBufLen);
            //mPartialBuffer->release();
            //mPartialBuffer = NULL;
            return UNKNOWN_ERROR;
        }

        // copy the header bytes to frameHeader
        memcpy (frameHeader, mPartialBuffer->data(), partialBufLen);
        memcpy (frameHeader + partialBufLen, mInputBuffer->data(), (4 - partialBufLen));
        // get the first 4 bytes of the buffer
        header = U32_AT((uint8_t *)frameHeader);
        frmHdr = frameHeader;
    } else {
        frmHdr = (uint8_t *)mPartialBuffer->data();
    }

    // check if its a good frame, and the frame size
    // get the first 4 bytes of the buffer
    header = U32_AT(frmHdr);
    bool curFrame = get_mp3_frame_size(header,&frameSize);
    if (!curFrame) {
        LOGE("MP3Decoder::read - partial frame does not have a vaild header 0x%x",
             header);
        return UNKNOWN_ERROR;
    }

    // check if the following frame is good
    uint32_t nextFrameOffset = frameSize - partialBufLen;
    if ((nextFrameOffset + 4) <= inputBufLen) {
        header = U32_AT((uint8_t *)mInputBuffer->data() + nextFrameOffset);
        if ((header & 0xffe00000) != 0xffe00000) {
            // next frame does not have a valid header,
            // this may not be the next buffer, bail out.
            LOGE("MP3Decoder::read - next frame does not have a vaild header 0x%x",
                 header);
            return UNKNOWN_ERROR;
        }
    } else {
        // next frame header is out of range
        // assume good header for now
        LOGE("MP3Decoder::read - assuming next frame is good");
    }

    // check if the input buffer has the remaining partial frame
    if (frameSize > (partialBufLen + inputBufLen)) {
        // input buffer does not have the remaining partial frame,
        // discard data here as frame split in 3 buffers not supported
        LOGE("MP3Decoder::updatePartialFrame - input buffer does not have the complete frame."
             " frame size %d, saved partial buffer len %d,"
             " input buffer len %d", frameSize, partialBufLen, inputBufLen);
        return UNKNOWN_ERROR;
    }

    // check if the mPartialBuffer can fit the remaining frame
    if ((mPartialBuffer->size() - partialBufLen) < (frameSize - partialBufLen)) {
        // mPartialBuffer is small to hold the reaming frame
        //TODO
        LOGE("MP3Decoder::updatePartialFrame - mPartialBuffer is small, size %d, required &d",
             (mPartialBuffer->size() - partialBufLen), (frameSize - partialBufLen));
        return UNKNOWN_ERROR;
    }

    // done with error checks
    // copy the partial frames to from a complete frame
    // Copy the remaining frame from input buffer
    uint32_t bytesRemaining = frameSize - mPartialBuffer->range_length();
    memcpy ((uint8_t *)mPartialBuffer->data() + mPartialBuffer->range_length(),
            (uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset(),
            bytesRemaining);

    // mark the bytes as consumed from input buffer
    mInputBuffer->set_range(
                           mInputBuffer->range_offset() + bytesRemaining,
                           mInputBuffer->range_length() - bytesRemaining);

    // set the range and length of mPartialBuffer
    mPartialBuffer->set_range(0,
                              mPartialBuffer->range_length() + bytesRemaining);

    LOGE("MP3Decoder::updatePartialFrame - copied the partial frame %d, input buffer length %d",
         bytesRemaining, mInputBuffer->range_length());

    return err;
}

status_t MP3Decoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    status_t err;

    *out = NULL;
    bool usedPartialFrame = false;
    bool seekSource = false;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        CHECK(seekTimeUs >= 0);

        mNumFramesOutput = 0;
        seekSource = true;

        if (mInputBuffer) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }

        if (mPartialBuffer) {
            mPartialBuffer->release();
            mPartialBuffer = NULL;
        }

        // Make sure that the next buffer output does not still
        // depend on fragments from the last one decoded.
        pvmp3_InitDecoder(mConfig, mDecoderBuf);
    } else {
        seekTimeUs = -1;
    }

    if (mInputBuffer == NULL) {
        err = mSource->read(&mInputBuffer, options);

        if (err != OK) {
            return err;
        }

        if ((mFixedHeader == 0) && (mInputBuffer->range_length() > 4)) {
            //save the first 4 bytes as fixed header for the reset of the file
            mFixedHeader = U32_AT((uint8_t *)mInputBuffer->data());
        }

        if (seekSource == true) {
            off_t syncOffset = 0;
            bool valid = resync((uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset()
                                ,mInputBuffer->range_length(), mFixedHeader, &syncOffset);
            if (valid) {
                // consume these bytes, we might find a frame header in next buffer
                mInputBuffer->set_range(
                    mInputBuffer->range_offset() + syncOffset,
                    mInputBuffer->range_length() - syncOffset);
                LOGV("mp3 decoder found a sync point after seek syncOffset %d", syncOffset);
            } else {
                LOGV("NO SYNC POINT found, buffer length %d",mInputBuffer->range_length());
            }
        }

        int64_t timeUs;
        if (mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs)) {
            mAnchorTimeUs = timeUs;
            mNumFramesOutput = 0;
        } else {
            // We must have a new timestamp after seeking.
            CHECK(seekTimeUs < 0);
        }
        // check for partial frame
        if (mPartialBuffer != NULL) {
            err = updatePartialFrame();
            if (err != OK) {
                // updating partial frame failed, discard the previously
                // saved partial frame and continue
                mPartialBuffer->release();
                mPartialBuffer = NULL;
                err = OK;
            }
        }
    }

    MediaBuffer *buffer;
    CHECK_EQ(mBufferGroup->acquire_buffer(&buffer), OK);

    if (mPartialBuffer != NULL) {
        mConfig->pInputBuffer =
        (uint8_t *)mPartialBuffer->data() + mPartialBuffer->range_offset();
        mConfig->inputBufferCurrentLength = mPartialBuffer->range_length();
        usedPartialFrame = true;
    } else {
        mConfig->pInputBuffer =
            (uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset();
        mConfig->inputBufferCurrentLength = mInputBuffer->range_length();
    }

    mConfig->inputBufferMaxLength = 0;
    mConfig->inputBufferUsedLength = 0;

    mConfig->outputFrameSize = buffer->size() / sizeof(int16_t);
    mConfig->pOutputBuffer = static_cast<int16_t *>(buffer->data());

    ERROR_CODE decoderErr;
    if ((decoderErr = pvmp3_framedecoder(mConfig, mDecoderBuf))
            != NO_DECODING_ERROR) {
        LOGV("mp3 decoder returned error %d", decoderErr);

        if ((decoderErr != NO_ENOUGH_MAIN_DATA_ERROR) &&
            (decoderErr != SYNCH_LOST_ERROR)) {
            buffer->release();
            buffer = NULL;

            mInputBuffer->release();
            mInputBuffer = NULL;
            if (mPartialBuffer) {
                mPartialBuffer->release();
                mPartialBuffer = NULL;
            }
            LOGE("mp3 decoder returned UNKNOWN_ERROR");

            return UNKNOWN_ERROR;
        }

        if ((mPartialBuffer == NULL) && (decoderErr == NO_ENOUGH_MAIN_DATA_ERROR)) {
            // Might be a partial frame, save it
            mPartialBuffer = new MediaBuffer(mInputBuffer->size());
            memcpy ((uint8_t *)mPartialBuffer->data(),
                    mConfig->pInputBuffer, mConfig->inputBufferCurrentLength);
            mPartialBuffer->set_range(0, mConfig->inputBufferCurrentLength);
            // set output buffer to 0
            mConfig->outputFrameSize = 0;
            // consume the copied bytes from input
            mConfig->inputBufferUsedLength = mConfig->inputBufferCurrentLength;
        } else if(decoderErr == SYNCH_LOST_ERROR) {
            // Try to find the mp3 frame header in the current buffer
            off_t syncOffset = 0;
            bool valid = resync(mConfig->pInputBuffer, mConfig->inputBufferCurrentLength,
                                mFixedHeader, &syncOffset);
            if (!valid) {
                // consume these bytes, we might find a frame header in next buffer
                syncOffset = mConfig->inputBufferCurrentLength;
            }
            // set output buffer to 0
            mConfig->outputFrameSize = 0;
            // consume the junk bytes from input buffer
            mConfig->inputBufferUsedLength = syncOffset;
        } else {
            // This is recoverable, just ignore the current frame and
            // play silence instead.
            memset(buffer->data(), 0, mConfig->outputFrameSize * sizeof(int16_t));
            mConfig->inputBufferUsedLength = mInputBuffer->range_length();
        }
    }

    buffer->set_range(
            0, mConfig->outputFrameSize * sizeof(int16_t));

    if ((mPartialBuffer != NULL) && usedPartialFrame) {
        mPartialBuffer->set_range(
            mPartialBuffer->range_offset() + mConfig->inputBufferUsedLength,
            mPartialBuffer->range_length() - mConfig->inputBufferUsedLength);
        mPartialBuffer->release();
        mPartialBuffer = NULL;
    } else {
        mInputBuffer->set_range(
            mInputBuffer->range_offset() + mConfig->inputBufferUsedLength,
            mInputBuffer->range_length() - mConfig->inputBufferUsedLength);
    }

    if (mInputBuffer->range_length() == 0) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    buffer->meta_data()->setInt64(
            kKeyTime,
            mAnchorTimeUs
                + (mNumFramesOutput * 1000000) / mConfig->samplingRate);

    mNumFramesOutput += mConfig->outputFrameSize / mNumChannels;

    *out = buffer;

    return OK;
}

}  // namespace android
