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
/*
 *Copyright (c) 2011, Code Aurora Forum. All rights reserved.
*/
#include "AACDecoder.h"
#define LOG_TAG "AACDecoder"

#include "../../include/ESDS.h"

#include "pvmp4audiodecoder_api.h"

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>

namespace android {

AACDecoder::AACDecoder(const sp<MediaSource> &source)
    : mSource(source),
      mStarted(false),
      mBufferGroup(NULL),
      mConfig(new tPVMP4AudioDecoderExternal),
      mDecoderBuf(NULL),
      mAnchorTimeUs(0),
      mNumSamplesOutput(0),
      mInputBuffer(NULL),
      mTempInputBuffer(NULL),
      mTempBufferTotalSize(0),
      mTempBufferDataLen(0),
      mInputBufferSize(0) {

    sp<MetaData> srcFormat = mSource->getFormat();

    int32_t sampleRate;
    CHECK(srcFormat->findInt32(kKeySampleRate, &sampleRate));

    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);

    // We'll always output stereo, regardless of how many channels are
    // present in the input due to decoder limitations.
    mMeta->setInt32(kKeyChannelCount, 2);
    mMeta->setInt32(kKeySampleRate, sampleRate);

    int64_t durationUs;
    if (srcFormat->findInt64(kKeyDuration, &durationUs)) {
        mMeta->setInt64(kKeyDuration, durationUs);
    }
    mMeta->setCString(kKeyDecoderComponent, "AACDecoder");

    mInitCheck = initCheck();
}

status_t AACDecoder::initCheck() {
    memset(mConfig, 0, sizeof(tPVMP4AudioDecoderExternal));
    mConfig->outputFormat = OUTPUTFORMAT_16PCM_INTERLEAVED;
    mConfig->aacPlusEnabled = 1;

    // The software decoder doesn't properly support mono output on
    // AACplus files. Always output stereo.
    mConfig->desiredChannels = 2;


    int32_t samplingRate;
    sp<MetaData> meta = mSource->getFormat();
    meta->findInt32(kKeySampleRate, &samplingRate);
    mConfig->samplingRate = samplingRate;

    int32_t bitRate;
    meta->findInt32(kKeyBitRate, &bitRate);

    int32_t encodedChannelCnt;
    meta->findInt32(kKeyChannelCount, &encodedChannelCnt);
    //mConfig->desiredChannels = encodedChannelCnt;

    // The software decoder doesn't properly support mono output on
    // AACplus files. Always output stereo.
    mConfig->desiredChannels = 2;

    UInt32 memRequirements = PVMP4AudioDecoderGetMemRequirements();
    mDecoderBuf = malloc(memRequirements);

    status_t err = PVMP4AudioDecoderInitLibrary(mConfig, mDecoderBuf);
    if (err != MP4AUDEC_SUCCESS) {
        LOGE("Failed to initialize MP4 audio decoder");
        return UNKNOWN_ERROR;
    }

    uint32_t type;
    const void *data;
    size_t size;
    if (meta->findData(kKeyESDS, &type, &data, &size)) {
        ESDS esds((const char *)data, size);
        CHECK_EQ(esds.InitCheck(), OK);

        const void *codec_specific_data;
        size_t codec_specific_data_size;
        esds.getCodecSpecificInfo(
                &codec_specific_data, &codec_specific_data_size);

        mConfig->pInputBuffer = (UChar *)codec_specific_data;
        mConfig->inputBufferCurrentLength = codec_specific_data_size;
        mConfig->inputBufferMaxLength = 0;

        if (PVMP4AudioDecoderConfig(mConfig, mDecoderBuf)
                != MP4AUDEC_SUCCESS) {
            LOGE("Error in setting AAC decoder config");
            return ERROR_UNSUPPORTED;
        }
    }

    //this is used by mm-parser only, usually format block size is 2
    if (meta->findData(kKeyAacCodecSpecificData, &type, &data, &size)) {
       if( size > AAC_MAX_FORMAT_BLOCK_SIZE ) {
          LOGE("AAC FormatBlock is too big %d", size);
          return ERROR_UNSUPPORTED;
       }
       memcpy( mFormatBlock, (uint8_t*)data, size);
       mConfig->pInputBuffer = mFormatBlock;
       mConfig->inputBufferCurrentLength = size;
       mConfig->inputBufferMaxLength = 0;

       if (PVMP4AudioDecoderConfig(mConfig, mDecoderBuf)
           != MP4AUDEC_SUCCESS) {
          LOGE("Error in setting AAC decoder config");
          return ERROR_UNSUPPORTED;
      }
    }
    return OK;
}

AACDecoder::~AACDecoder() {
    if (mStarted) {
        stop();
    }

    delete mConfig;
    mConfig = NULL;

    //Reset temp buffer
    if( mTempInputBuffer != NULL ) {
       free(mTempInputBuffer);
       mTempInputBuffer = NULL;
    }
}

status_t AACDecoder::start(MetaData *params) {
    CHECK(!mStarted);

    if (mInitCheck != OK) {
        LOGE("InitCheck Failed");
        return UNKNOWN_ERROR;
    }

    mBufferGroup = new MediaBufferGroup;
    mBufferGroup->add_buffer(new MediaBuffer(4096 * 2));

    mSource->start();

    mAnchorTimeUs = 0;
    mNumSamplesOutput = 0;
    mStarted = true;
    mNumDecodedBuffers = 0;
    mUpsamplingFactor = 2;

    return OK;
}

status_t AACDecoder::stop() {
    CHECK(mStarted);

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    free(mDecoderBuf);
    mDecoderBuf = NULL;

    delete mBufferGroup;
    mBufferGroup = NULL;

    if( mTempInputBuffer != NULL ) {
       free(mTempInputBuffer);
       mTempInputBuffer = NULL;
    }
    mTempBufferDataLen = 0;
    mTempBufferTotalSize = 0;

    mSource->stop();

    mStarted = false;

    return OK;
}

sp<MetaData> AACDecoder::getFormat() {
    return mMeta;
}

status_t AACDecoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    status_t err;

    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        CHECK(seekTimeUs >= 0);

        mNumSamplesOutput = 0;

        if (mInputBuffer) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }

        // Make sure that the next buffer output does not still
        // depend on fragments from the last one decoded.
        PVMP4AudioDecoderResetBuffer(mDecoderBuf);
    } else {
        seekTimeUs = -1;
    }


    uint8_t* inputBuffer = NULL;
    uint32_t inputBufferSize = 0;

    if (mInputBuffer == NULL) {
        err = mSource->read(&mInputBuffer, options);

        if (err != OK) {
            if(mInputBuffer){
                mInputBuffer->release();
                mInputBuffer = NULL;
            }

            if(mTempInputBuffer != NULL){
                free(mTempInputBuffer);
                mTempInputBuffer = NULL;
            }

            mTempBufferDataLen = 0;
            mTempBufferTotalSize = 0;
            mInputBufferSize = 0;
            return err;
        }

        int64_t timeUs;
        if (mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs)) {
            mAnchorTimeUs = timeUs;
            if( timeUs != 0 ) {
                mNumSamplesOutput = 0;
            }
        } else {
            // We must have a new timestamp after seeking.
            CHECK(seekTimeUs < 0);
        }

        inputBuffer = (UChar *)mInputBuffer->data() + mInputBuffer->range_offset();
        inputBufferSize = mInputBuffer->range_length();
        if ( mInputBufferSize == 0 ) {
            // Remember the first input buffer size
            mInputBufferSize = mInputBuffer->size();
        }
        //Check if there was incomplete frame assembly started
        if (  mTempBufferDataLen ) {
            LOGV("Incomplete frame assembly is in progress mTempBufferDataLen %d", mTempBufferDataLen);
            if ( mTempBufferDataLen + inputBufferSize > mTempBufferTotalSize ) {
                LOGE("Temp buffer size exceeded %d input size %d", mTempBufferTotalSize, inputBufferSize);
                return UNKNOWN_ERROR;
            }
            //append new input buffer to temp buffer
            memcpy( mTempInputBuffer + mTempBufferDataLen, inputBuffer, inputBufferSize );

            //update the new iput buffer data
            if ( inputBufferSize + mTempBufferDataLen < mInputBufferSize ) {
                LOGV("Reached end of stream case" );
                inputBufferSize += mTempBufferDataLen;
                mTempBufferDataLen = 0;
                mInputBufferSize = inputBufferSize;
                mInputBuffer->set_range(0, inputBufferSize);
            }
            memcpy( inputBuffer, mTempInputBuffer, inputBufferSize);
        }
    }
    else {
        inputBuffer = (UChar *)mInputBuffer->data() + mInputBuffer->range_offset();
        inputBufferSize = mInputBuffer->range_length();
    }

    //Allocate Output buffer
    MediaBuffer *buffer;
    CHECK_EQ(mBufferGroup->acquire_buffer(&buffer), (status_t)OK);

    //Get the input buffer
    LOGV("Input Buffer Length %d Offset %d size %d", mInputBuffer->range_length(), mInputBuffer->range_offset(), mInputBufferSize);

    mConfig->pInputBuffer = inputBuffer;

    mConfig->inputBufferCurrentLength = inputBufferSize;
    mConfig->inputBufferMaxLength = 0;
    mConfig->inputBufferUsedLength = 0;
    mConfig->remainderBits = 0;

    mConfig->pOutputBuffer = static_cast<Int16 *>(buffer->data());
    mConfig->pOutputBuffer_plus = &mConfig->pOutputBuffer[2048];
    mConfig->repositionFlag = false;

    Int decoderErr;

    decoderErr = PVMP4AudioDecodeFrame(mConfig, mDecoderBuf);

    /*
     * AAC+/eAAC+ streams can be signalled in two ways: either explicitly
     * or implicitly, according to MPEG4 spec. AAC+/eAAC+ is a dual
     * rate system and the sampling rate in the final output is actually
     * doubled compared with the core AAC decoder sampling rate.
     *
     * Explicit signalling is done by explicitly defining SBR audio object
     * type in the bitstream. Implicit signalling is done by embedding
     * SBR content in AAC extension payload specific to SBR, and hence
     * requires an AAC decoder to perform pre-checks on actual audio frames.
     *
     * Thus, we could not say for sure whether a stream is
     * AAC+/eAAC+ until the first data frame is decoded.
     */
    if (++mNumDecodedBuffers <= 2) {
        LOGV("audio/extended audio object type: %d + %d",
            mConfig->audioObjectType, mConfig->extendedAudioObjectType);
        LOGV("aac+ upsampling factor: %d desired channels: %d",
            mConfig->aacPlusUpsamplingFactor, mConfig->desiredChannels);

        CHECK(mNumDecodedBuffers > 0);
        if (mNumDecodedBuffers == 1) {
            mUpsamplingFactor = mConfig->aacPlusUpsamplingFactor;
            // Check on the sampling rate to see whether it is changed.
            int32_t sampleRate;
            CHECK(mMeta->findInt32(kKeySampleRate, &sampleRate));
            if (mConfig->samplingRate != sampleRate) {
                mMeta->setInt32(kKeySampleRate, mConfig->samplingRate);
                LOGW("Sample rate was %d Hz, but now is %d Hz",
                        sampleRate, mConfig->samplingRate);
                buffer->release();
                mInputBuffer->release();
                mInputBuffer = NULL;
                return INFO_FORMAT_CHANGED;
            }
        } else {  // mNumDecodedBuffers == 2
            if (mConfig->extendedAudioObjectType == MP4AUDIO_AAC_LC ||
                mConfig->extendedAudioObjectType == MP4AUDIO_LTP) {
                if (mUpsamplingFactor == 2) {
                    // The stream turns out to be not aacPlus mode anyway
                    LOGW("Disable AAC+/eAAC+ since extended audio object type is %d",
                        mConfig->extendedAudioObjectType);
                    mConfig->aacPlusEnabled = 0;
                }
            } else {
                if (mUpsamplingFactor == 1) {
                    // aacPlus mode does not buy us anything, but to cause
                    // 1. CPU load to increase, and
                    // 2. a half speed of decoding
                    LOGW("Disable AAC+/eAAC+ since upsampling factor is 1");
                    mConfig->aacPlusEnabled = 0;
                }
            }
        }
    }

    size_t numOutBytes =
        mConfig->frameLength * sizeof(int16_t) * mConfig->desiredChannels;
    if (mUpsamplingFactor == 2) {
        if (mConfig->desiredChannels == 1) {
            memcpy(&mConfig->pOutputBuffer[1024], &mConfig->pOutputBuffer[2048], numOutBytes * 2);
        }
        numOutBytes *= 2;
    }

    LOGV("AAC decoder %d frame length %d used length %d ", decoderErr, inputBufferSize, mConfig->inputBufferUsedLength);
    if( inputBufferSize < mConfig->inputBufferUsedLength ) {
        LOGE("unexpected error actual len %d is less than used len %d", inputBufferSize, mConfig->inputBufferUsedLength);
        decoderErr = MP4AUDEC_INVALID_FRAME;
    }

    int aacformattype = 0;
    sp<MetaData> metadata = mSource->getFormat();
    metadata->findInt32(kkeyAacFormatAdif, &aacformattype);

    if ( decoderErr == MP4AUDEC_INCOMPLETE_FRAME  && aacformattype == true) {
        LOGW("Handle Incomplete frame error inputBufSize %d, usedLength %d", inputBufferSize, mConfig->inputBufferUsedLength);
        if(mConfig->inputBufferUsedLength == mInputBufferSize){
           LOGW("Decoder cannot process the buffer due to invalid frame");
           decoderErr = MP4AUDEC_INVALID_FRAME;
        } else {
            if ( !mTempInputBuffer ) {
                //Allocate Temp buffer
                uint32_t bytesToAllocate = 2 * mInputBuffer->size();
                mTempInputBuffer = (uint8_t*)malloc( bytesToAllocate );
                mTempBufferDataLen = 0;
                if (mTempInputBuffer == NULL) {
                   LOGE("Could not allocate temp buffer bytesToAllocate quit playing");
                   return UNKNOWN_ERROR;
                }
                mTempBufferTotalSize = bytesToAllocate;
                LOGV("Allocated tempBuffer of size %d data len %d", mTempBufferTotalSize, mTempBufferDataLen);
            }
            // copy the remaining data into temp buffer
            memcpy( mTempInputBuffer, inputBuffer, mConfig->inputBufferUsedLength );

            if (mTempBufferDataLen != 0) {
                //append previous remaining data back into temp buffer
                LOGV("Appending remaining data tempDataLen %d usedLength %d", mTempBufferDataLen, mConfig->inputBufferUsedLength);
                memcpy( mTempInputBuffer + mConfig->inputBufferUsedLength,
                    mTempInputBuffer + mInputBufferSize,
                    mTempBufferDataLen );
            }

            mTempBufferDataLen += mConfig->inputBufferUsedLength;
            LOGV("mTempBufferDataLen %d inputBufferUsedLength %d", mTempBufferDataLen, mConfig->inputBufferUsedLength);
            // temp buffer has accumulated one frame size worth data
            // copy it back to input buffer so that it is fed to decoder next
            if ( mTempBufferDataLen >= mInputBufferSize ) {
                LOGV("mTempBufferDataLen %d exceeded mInputBufferSize %d ", mTempBufferDataLen, mInputBufferSize);
                memcpy((UChar*)mInputBuffer->data(), mTempInputBuffer, mInputBufferSize );
                mTempBufferDataLen -= mInputBufferSize;
                mInputBuffer->set_range( 0, mInputBufferSize );
                mConfig->inputBufferUsedLength = 0;
            }

            //reset the output buffer size
            numOutBytes = 0;
        } // end of else INVALID FRAME

    }
    if (decoderErr != MP4AUDEC_SUCCESS && decoderErr != MP4AUDEC_INCOMPLETE_FRAME) {
        LOGW("AAC decoder returned error %d, substituting silence", decoderErr);

        memset(buffer->data(), 0, numOutBytes);

        // Discard input buffer.
        if( mInputBuffer != NULL ) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }

        if(mTempBufferDataLen) {
            //put previous remaining data to temp buffer beginning
            memcpy( mTempInputBuffer,
                    mTempInputBuffer + mInputBufferSize,
                    mTempBufferDataLen );
        }

        // fall through
    }

    buffer->set_range(0, numOutBytes);

    if (mInputBuffer != NULL) {
        mInputBuffer->set_range(
                mInputBuffer->range_offset() + mConfig->inputBufferUsedLength,
                mInputBuffer->range_length() - mConfig->inputBufferUsedLength);

        if (mInputBuffer->range_length() == 0) {
            if(decoderErr == MP4AUDEC_SUCCESS && mTempBufferDataLen) {
                //put previous remaining data to temp buffer beginning
                memcpy( mTempInputBuffer,
                        mTempInputBuffer + mInputBufferSize,
                        mTempBufferDataLen );
            }
            mInputBuffer->release();
            mInputBuffer = NULL;
        }
    }

    buffer->meta_data()->setInt64(
            kKeyTime,
            mAnchorTimeUs
                + (mNumSamplesOutput * 1000000) / mConfig->samplingRate);

    if(numOutBytes > 0)
        mNumSamplesOutput += mConfig->frameLength * mUpsamplingFactor;

    *out = buffer;

    return OK;
}

}  // namespace android
