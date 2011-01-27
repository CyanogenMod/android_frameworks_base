/*
** Copyright 2009, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0
#define LOG_TAG "FLACPlayer"
#include "utils/Log.h"

#include <stdio.h>
#include <assert.h>
#include <limits.h>
#include <unistd.h>
#include <fcntl.h>
#include <sched.h>
#include <sys/types.h>
#include <sys/stat.h>


#include "FLACPlayer.h"

#ifdef HAVE_GETTID
static pid_t myTid() { return gettid(); }
#else
static pid_t myTid() { return getpid(); }
#endif

// ----------------------------------------------------------------------------

namespace android {

// ----------------------------------------------------------------------------

// TODO: Determine appropriate return codes
static status_t ERROR_NOT_OPEN = -1;
static status_t ERROR_OPEN_FAILED = -2;
static status_t ERROR_ALLOCATE_FAILED = -4;
static status_t ERROR_NOT_SUPPORTED = -8;
static status_t ERROR_NOT_READY = -16;
static status_t STATE_INIT = 0;
static status_t STATE_ERROR = 1;
static status_t STATE_OPEN = 2;


FLACPlayer::FLACPlayer() :
    mTotalSamples(-1), mCurrentSample(0), mBytesPerSample(-1),
    mChannels(-1), mSampleRate(-1), mAudioBuffer(NULL),
    mAudioBufferSize(0), mAudioBufferFilled(0),
    mState(STATE_ERROR), mStreamType(AudioSystem::MUSIC),
    mLoop(false), mAndroidLoop(false), mExit(false), mPaused(false),
    mRender(false), mRenderTid(-1)
{
    LOGV("constructor");
}

void FLACPlayer::onFirstRef()
{
    LOGV("onFirstRef");
    // create playback thread
    Mutex::Autolock l(mMutex);
    createThreadEtc(renderThread, this, "FLAC decoder", ANDROID_PRIORITY_AUDIO);
    mCondition.wait(mMutex);
    if (mRenderTid > 0) {
        LOGV("render thread(%d) started", mRenderTid);
        mState = STATE_INIT;
    }
}

status_t FLACPlayer::initCheck()
{
    if (mState != STATE_ERROR) return NO_ERROR;
    return ERROR_NOT_READY;
}

FLACPlayer::~FLACPlayer() {
    LOGV("FLACPlayer destructor");
    release();
}

status_t FLACPlayer::setDataSource(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    return setdatasource(uri, -1, 0, 0x7ffffffffffffffLL); // intentionally less than LONG_MAX
}

status_t FLACPlayer::setDataSource(int fd, int64_t offset, int64_t length)
{
    return setdatasource(NULL, fd, offset, length);
}

status_t FLACPlayer::setdatasource(const char *path, int fd, int64_t offset, int64_t length)
{
    LOGV("setDataSource url=%s, fd=%d", path, fd);

    // file still open?
    Mutex::Autolock l(mMutex);
    if (mState == STATE_OPEN) {
        reset_nosync();
    }

    // open file and set paused state
    if (path) {
        mFile = fopen(path, "r");
    } else {
        mFile = fdopen(dup(fd), "r");
    }
    if (mFile == NULL) {
        return ERROR_OPEN_FAILED;
    }

    struct stat sb;
    int ret;
    if (path) {
        ret = stat(path, &sb);
    } else {
        ret = fstat(fd, &sb);
    }
    if (ret != 0) {
        mState = STATE_ERROR;
        fclose(mFile);
        return ERROR_OPEN_FAILED;
    }

    fseek(mFile, offset, SEEK_SET);

    mDecoder = FLAC__stream_decoder_new();
    if (mDecoder == NULL) {
        LOGE("failed to allocate decoder\n");
        mState = STATE_ERROR;
        fclose(mFile);
        return ERROR_OPEN_FAILED;
    }

    FLAC__stream_decoder_set_md5_checking(mDecoder, false);
    FLAC__stream_decoder_set_metadata_ignore_all(mDecoder);
    FLAC__stream_decoder_set_metadata_respond(mDecoder, FLAC__METADATA_TYPE_STREAMINFO);
    FLAC__stream_decoder_set_metadata_respond(mDecoder, FLAC__METADATA_TYPE_VORBIS_COMMENT);

    FLAC__StreamDecoderInitStatus init_status;
    init_status = FLAC__stream_decoder_init_FILE(mDecoder, mFile, vp_write, vp_metadata, vp_error, this);
    if (init_status != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
        LOGE("FLAC__stream_decoder_init_FILE failed: [%d]\n", (int)init_status);
        mState = STATE_ERROR;
        fclose(mFile);
        return ERROR_OPEN_FAILED;
    }

    if (!FLAC__stream_decoder_process_until_end_of_metadata(mDecoder)) {
        LOGE("FLAC__stream_decoder_process_until_end_of_metadata failed\n");
        mState = STATE_ERROR;
        fclose(mFile);
        return ERROR_OPEN_FAILED;
    }

    mState = STATE_OPEN;
    return NO_ERROR;
}

status_t FLACPlayer::prepare()
{
    LOGV("prepare");
    if (mState != STATE_OPEN ) {
        return ERROR_NOT_OPEN;
    }
    return NO_ERROR;
}

status_t FLACPlayer::prepareAsync() {
    LOGV("prepareAsync");
    // can't hold the lock here because of the callback
    // it's safe because we don't change state
    if (mState != STATE_OPEN) {
        sendEvent(MEDIA_ERROR);
        return NO_ERROR;
    }
    sendEvent(MEDIA_PREPARED);
    return NO_ERROR;
}

void FLACPlayer::vp_metadata(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data) {
    FLACPlayer *self = (FLACPlayer *)client_data;

    if (metadata->type == FLAC__METADATA_TYPE_STREAMINFO) {
        self->mTotalSamples = metadata->data.stream_info.total_samples;
        self->mBytesPerSample = metadata->data.stream_info.bits_per_sample / 8;
        self->mChannels = metadata->data.stream_info.channels;
        self->mSampleRate = metadata->data.stream_info.sample_rate;

        if (self->mBytesPerSample != 2) {
            LOGE("Can only support 16 bits per sample; input is %d\n", self->mBytesPerSample * 8);
            self->mState = STATE_ERROR;
            return;
        }

        self->mLengthInMsec = self->mTotalSamples / self->mSampleRate * 1000 +
                              self->mTotalSamples % self->mSampleRate / ( self->mSampleRate / 1000 );
    } else if (metadata->type == FLAC__METADATA_TYPE_VORBIS_COMMENT) {
        for (unsigned int i = 0; i < metadata->data.vorbis_comment.num_comments; i++) {
            char *ptr = (char *)metadata->data.vorbis_comment.comments[i].entry;

            // does the comment start with ANDROID_LOOP_TAG
            if (strncmp(ptr, ANDROID_LOOP_TAG, strlen(ANDROID_LOOP_TAG)) == 0) {
                // read the value of the tag
                char *val = ptr + strlen(ANDROID_LOOP_TAG) + 1;
                self->mAndroidLoop = (strncmp(val, "true", 4) == 0);
            }

            LOGV_IF(self->mAndroidLoop, "looped sound");
        }
    }
}

void FLACPlayer::vp_error(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data) {
    LOGV("vp_error");
    FLACPlayer *self = (FLACPlayer *)client_data;
    self->sendEvent(MEDIA_ERROR);
    self->mState = STATE_ERROR;
}

status_t FLACPlayer::start()
{
    LOGV("start\n");
    Mutex::Autolock l(mMutex);
    if (mState != STATE_OPEN) {
        return ERROR_NOT_OPEN;
    }

    mPaused = false;
    mRender = true;

    // wake up render thread
    LOGV("  wakeup render thread\n");
    mCondition.signal();
    return NO_ERROR;
}

status_t FLACPlayer::stop()
{
    LOGV("stop\n");
    Mutex::Autolock l(mMutex);
    if (mState != STATE_OPEN) {
        return ERROR_NOT_OPEN;
    }
    mPaused = true;
    mRender = false;
    return NO_ERROR;
}

status_t FLACPlayer::seekTo(int msec)
{
    LOGV("seekTo %d\n", msec);
    Mutex::Autolock l(mMutex);
    if (mState != STATE_OPEN) {
        return ERROR_NOT_OPEN;
    }

    FLAC__uint64 target_sample = mTotalSamples * msec / mLengthInMsec;

    if (mTotalSamples > 0 && target_sample >= mTotalSamples && target_sample > 0)
        target_sample = mTotalSamples - 1;

    if (!FLAC__stream_decoder_seek_absolute(mDecoder, target_sample)) {
        LOGE("FLAC__stream_decoder_seek_absolute failed\n");
        if (FLAC__stream_decoder_get_state(mDecoder) == FLAC__STREAM_DECODER_SEEK_ERROR) {
            FLAC__stream_decoder_flush(mDecoder);
        }
        return ERROR_NOT_SUPPORTED;
    }

    mCurrentSample = target_sample;

    sendEvent(MEDIA_SEEK_COMPLETE);
    return NO_ERROR;
}

status_t FLACPlayer::pause()
{
    LOGV("pause\n");
    Mutex::Autolock l(mMutex);
    if (mState != STATE_OPEN) {
        return ERROR_NOT_OPEN;
    }
    mPaused = true;
    return NO_ERROR;
}

bool FLACPlayer::isPlaying()
{
    LOGV("isPlaying\n");
    if (mState == STATE_OPEN) {
        return mRender;
    }
    return false;
}

status_t FLACPlayer::getCurrentPosition(int* msec)
{
    LOGV("getCurrentPosition\n");
    Mutex::Autolock l(mMutex);
    if (mState != STATE_OPEN) {
        LOGE("getCurrentPosition(): file not open");
        return ERROR_NOT_OPEN;
    }

    *msec = (int)(mCurrentSample * 1000 / mSampleRate);
    return NO_ERROR;
}

status_t FLACPlayer::getDuration(int* duration)
{
    LOGV("getDuration\n");
    if (mState != STATE_OPEN) {
        return ERROR_NOT_OPEN;
    }

    *duration = mLengthInMsec;
    return NO_ERROR;
}

status_t FLACPlayer::release()
{
    LOGV("release\n");
    Mutex::Autolock l(mMutex);
    reset_nosync();

    // TODO: timeout when thread won't exit
    // wait for render thread to exit
    if (mRenderTid > 0) {
        mExit = true;
        mCondition.signal();
        mCondition.wait(mMutex);
    }
    return NO_ERROR;
}

status_t FLACPlayer::reset()
{
    LOGV("reset\n");
    Mutex::Autolock l(mMutex);
    return reset_nosync();
}

// always call with lock held
status_t FLACPlayer::reset_nosync()
{
    // close file
    if (mFile != NULL) {
        FLAC__stream_decoder_delete(mDecoder);
        fclose(mFile);
        mFile = NULL;
    }
    mState = STATE_ERROR;

    mTotalSamples = -1;
    mBytesPerSample = -1;
    mChannels = -1;
    mSampleRate = -1;
    mLoop = false;
    mAndroidLoop = false;
    mPaused = false;
    mRender = false;
    return NO_ERROR;
}

status_t FLACPlayer::setLooping(int loop)
{
    LOGV("setLooping\n");
    Mutex::Autolock l(mMutex);
    mLoop = (loop != 0);
    return NO_ERROR;
}

status_t FLACPlayer::createOutputTrack() {
    LOGV("Create AudioTrack object: rate=%ld, channels=%d\n",
            mSampleRate, mChannels);
    if (mAudioSink->open(mSampleRate, mChannels, AudioSystem::PCM_16_BIT, DEFAULT_AUDIOSINK_BUFFERCOUNT) != NO_ERROR) {
        LOGE("mAudioSink open failed\n");
        return ERROR_OPEN_FAILED;
    }
    return NO_ERROR;
}

FLAC__StreamDecoderWriteStatus FLACPlayer::vp_write(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data) {
    FLACPlayer *self = (FLACPlayer *)client_data;

    const uint32_t bytes_per_sample = self->mBytesPerSample;
    const uint32_t incr = bytes_per_sample * self->mChannels;
    const uint32_t wide_samples = frame->header.blocksize;
    const uint32_t frame_size = incr * wide_samples;

    self->mCurrentSample = frame->header.number.sample_number;

    uint32_t sample, wide_sample, channel;

    if (self->mAudioBufferSize < frame_size) {
        if (self->mAudioBuffer != NULL) {
            delete [] self->mAudioBuffer;
        }
        self->mAudioBuffer = new FLAC__int8[frame_size];
        self->mAudioBufferSize = frame_size;
    }

    FLAC__int8 *s8buffer = self->mAudioBuffer;
    FLAC__int16 *s16buffer = (FLAC__int16 *)s8buffer;

    // Interleave channel data like PCM
    if (self->mChannels == 2) {
        for (sample = wide_sample = 0; wide_sample < wide_samples; wide_sample++) {
            s16buffer[sample++] = (FLAC__int16)(buffer[0][wide_sample]);
            s16buffer[sample++] = (FLAC__int16)(buffer[1][wide_sample]);
        }
    } else if (self->mChannels == 1) {
        for (sample = wide_sample = 0; wide_sample < wide_samples; wide_sample++) {
            s16buffer[sample++] = (FLAC__int16)(buffer[0][wide_sample]);
        }
    } else {
        for (sample = wide_sample = 0; wide_sample < wide_samples; wide_sample++) {
            for (channel = 0; channel < self->mChannels; channel++, sample++) {
                s16buffer[sample] = (FLAC__int16)(buffer[channel][wide_sample]);
            }
        }
    }
    self->mAudioBufferFilled = frame_size;

    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

int FLACPlayer::renderThread(void* p) {
    return ((FLACPlayer*)p)->render();
}

int FLACPlayer::render() {
    int result = -1;
    int temp;
    int current_section = 0;
    bool audioStarted = false;

    LOGV("render\n");

    // let main thread know we're ready
    {
        Mutex::Autolock l(mMutex);
        mRenderTid = myTid();
        mCondition.signal();
    }

    while (1) {
        FLAC__bool status = true;
        {
            Mutex::Autolock l(mMutex);

            // pausing?
            if (mPaused) {
                if (mAudioSink->ready()) mAudioSink->pause();
                mRender = false;
                audioStarted = false;
            }

            // nothing to render, wait for client thread to wake us up
            if (!mExit && !mRender) {
                LOGV("render - signal wait\n");
                mCondition.wait(mMutex);
                LOGV("render - signal rx'd\n");
            }
            if (mExit) break;

            // We could end up here if start() is called, and before we get a
            // chance to run, the app calls stop() or reset(). Re-check render
            // flag so we don't try to render in stop or reset state.
            if (!mRender) continue;

            // create audio output track if necessary
            if (!mAudioSink->ready()) {
                LOGV("render - create output track\n");
                if (createOutputTrack() != NO_ERROR)
                    break;
            }


            // start audio output if necessary
            if (!audioStarted && !mPaused && !mExit) {
                LOGV("render - starting audio\n");
                mAudioSink->start();
                audioStarted = true;
            }

            if (FLAC__stream_decoder_get_state(mDecoder) != FLAC__STREAM_DECODER_END_OF_STREAM) {
                status = FLAC__stream_decoder_process_single(mDecoder);
            } else {
                // end of file, do we need to loop?
                // ...
                if (mLoop || mAndroidLoop) {
                    FLAC__stream_decoder_seek_absolute(mDecoder, 0);
                    mCurrentSample = 0;
                    status = FLAC__stream_decoder_process_single(mDecoder);
                } else {
                    mAudioSink->stop();
                    audioStarted = false;
                    mRender = false;
                    mPaused = true;

                    FLAC__uint64 endpos;
                    if (!FLAC__stream_decoder_get_decode_position(mDecoder, &endpos)) {
                        endpos = 0;
                    }

                    LOGV("send MEDIA_PLAYBACK_COMPLETE\n");
                    sendEvent(MEDIA_PLAYBACK_COMPLETE);

                    // wait until we're started again
                    LOGV("playback complete - wait for signal\n");
                    mCondition.wait(mMutex);
                    LOGV("playback complete - signal rx'd\n");
                    if (mExit) break;

                    // if we're still at the end, restart from the beginning
                    if (mState == STATE_OPEN) {
                        FLAC__uint64 curpos;
                        if (FLAC__stream_decoder_get_decode_position(mDecoder, &curpos)) {
                            curpos = 0;
                        }
                        if (curpos == endpos) {
                            FLAC__stream_decoder_seek_absolute(mDecoder, 0);
                            mCurrentSample = 0;
                        }
                        status = FLAC__stream_decoder_process_single(mDecoder);
                    }
                }
            }
        }

        if (!status) {
            LOGE("Error in FLAC decoder: %s\n", FLAC__stream_decoder_get_resolved_state_string(mDecoder));
            sendEvent(MEDIA_ERROR);
            break;
        }

        if (mAudioBufferFilled > 0) {
            /* Be sure to clear mAudioBufferFilled even if there's an error. */
            uint32_t toPlay = mAudioBufferFilled;
            mAudioBufferFilled = 0;

            if (!mAudioSink->write(mAudioBuffer, toPlay)) {
                LOGE("Error in FLAC decoder: %s\n", FLAC__stream_decoder_get_resolved_state_string(mDecoder));
                sendEvent(MEDIA_ERROR);
                break;
            }
        }
    }

threadExit:
    mAudioSink.clear();
    if (mAudioBuffer != NULL) {
        delete [] mAudioBuffer;
        mAudioBuffer = NULL;
        mAudioBufferSize = 0;
        mAudioBufferFilled = 0;
    }

    // tell main thread goodbye
    Mutex::Autolock l(mMutex);
    mRenderTid = -1;
    mCondition.signal();
    return result;
}

} // end namespace android

