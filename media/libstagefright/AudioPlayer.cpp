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

//#define LOG_NDEBUG 0
#define LOG_TAG "AudioPlayer"
#include <utils/Log.h>

#include <binder/IPCThreadState.h>
#include <media/AudioTrack.h>
#include <media/stagefright/AudioPlayer.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#ifdef OMAP_ENHANCEMENT
#include <media/stagefright/MediaErrors.h>
#endif
#include "include/AwesomePlayer.h"

namespace android {

AudioPlayer::AudioPlayer(
        const sp<MediaPlayerBase::AudioSink> &audioSink,
        AwesomePlayer *observer)
    : mAudioTrack(NULL),
      mInputBuffer(NULL),
      mSampleRate(0),
      mLatencyUs(0),
      mFrameSize(0),
      mNumFramesPlayed(0),
      mPositionTimeMediaUs(-1),
      mPositionTimeRealUs(-1),
      mSeeking(false),
      mReachedEOS(false),
      mFinalStatus(OK),
      mStarted(false),
      mIsFirstBuffer(false),
      mFirstBufferResult(OK),
      mFirstBuffer(NULL),
      mAudioSink(audioSink),
      mObserver(observer) {
}

AudioPlayer::~AudioPlayer() {
    if (mStarted) {
        reset();
    }
}

void AudioPlayer::setSource(const sp<MediaSource> &source) {
    CHECK_EQ(mSource, NULL);
    mSource = source;
}

status_t AudioPlayer::start(bool sourceAlreadyStarted) {
    CHECK(!mStarted);
    CHECK(mSource != NULL);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    mRealTimeInterpolation = GetSystemTimeuSec();
#endif


    status_t err;
    if (!sourceAlreadyStarted) {
        err = mSource->start();

        if (err != OK) {
            return err;
        }
    }

    // We allow an optional INFO_FORMAT_CHANGED at the very beginning
    // of playback, if there is one, getFormat below will retrieve the
    // updated format, if there isn't, we'll stash away the valid buffer
    // of data to be used on the first audio callback.

    CHECK(mFirstBuffer == NULL);

    mFirstBufferResult = mSource->read(&mFirstBuffer);
    if (mFirstBufferResult == INFO_FORMAT_CHANGED) {
        LOGV("INFO_FORMAT_CHANGED!!!");

        CHECK(mFirstBuffer == NULL);
        mFirstBufferResult = OK;
        mIsFirstBuffer = false;
    } else {
        mIsFirstBuffer = true;
    }

    sp<MetaData> format = mSource->getFormat();
    const char *mime;
    bool success = format->findCString(kKeyMIMEType, &mime);
    CHECK(success);
    CHECK(!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW));

    success = format->findInt32(kKeySampleRate, &mSampleRate);
    CHECK(success);

    int32_t numChannels;
    success = format->findInt32(kKeyChannelCount, &numChannels);
    CHECK(success);

    if (mAudioSink.get() != NULL) {
        status_t err = mAudioSink->open(
                mSampleRate, numChannels, AudioSystem::PCM_16_BIT,
                DEFAULT_AUDIOSINK_BUFFERCOUNT,
                &AudioPlayer::AudioSinkCallback, this);
        if (err != OK) {
            if (mFirstBuffer != NULL) {
                mFirstBuffer->release();
                mFirstBuffer = NULL;
            }

            if (!sourceAlreadyStarted) {
                mSource->stop();
            }

            return err;
        }

        mLatencyUs = (int64_t)mAudioSink->latency() * 1000;
        mFrameSize = mAudioSink->frameSize();

        mAudioSink->start();
    } else {
        mAudioTrack = new AudioTrack(
                AudioSystem::MUSIC, mSampleRate, AudioSystem::PCM_16_BIT,
                (numChannels == 2)
                    ? AudioSystem::CHANNEL_OUT_STEREO
                    : AudioSystem::CHANNEL_OUT_MONO,
                0, 0, &AudioCallback, this, 0);

        if ((err = mAudioTrack->initCheck()) != OK) {
            delete mAudioTrack;
            mAudioTrack = NULL;

            if (mFirstBuffer != NULL) {
                mFirstBuffer->release();
                mFirstBuffer = NULL;
            }

            if (!sourceAlreadyStarted) {
                mSource->stop();
            }

            return err;
        }

        mLatencyUs = (int64_t)mAudioTrack->latency() * 1000;
        mFrameSize = mAudioTrack->frameSize();

        mAudioTrack->start();
    }

    mStarted = true;

    return OK;
}

void AudioPlayer::pause(bool playPendingSamples) {
    CHECK(mStarted);

    if (playPendingSamples) {
        if (mAudioSink.get() != NULL) {
            mAudioSink->stop();
        } else {
            mAudioTrack->stop();
        }
    } else {
        if (mAudioSink.get() != NULL) {
            mAudioSink->pause();
        } else {
            mAudioTrack->pause();
        }
    }
}
#ifdef OMAP_ENHANCEMENT
void AudioPlayer::flush() {
    CHECK(mStarted);

    if (mAudioSink.get() != NULL) {
        mAudioSink->flush();
    } else {
        mAudioTrack->flush();
    }
}
#endif
void AudioPlayer::resume() {
    CHECK(mStarted);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    mRealTimeInterpolation = GetSystemTimeuSec();
#endif

    if (mAudioSink.get() != NULL) {
        mAudioSink->start();
    } else {
        mAudioTrack->start();
    }
}

void AudioPlayer::reset() {
    CHECK(mStarted);

    if (mAudioSink.get() != NULL) {
        mAudioSink->stop();
        mAudioSink->close();
    } else {
        mAudioTrack->stop();

        delete mAudioTrack;
        mAudioTrack = NULL;
    }

    // Make sure to release any buffer we hold onto so that the
    // source is able to stop().

    if (mFirstBuffer != NULL) {
        mFirstBuffer->release();
        mFirstBuffer = NULL;
    }

    if (mInputBuffer != NULL) {
        LOGV("AudioPlayer releasing input buffer.");

        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    mSource->stop();

    // The following hack is necessary to ensure that the OMX
    // component is completely released by the time we may try
    // to instantiate it again.
    wp<MediaSource> tmp = mSource;
    mSource.clear();
    while (tmp.promote() != NULL) {
        usleep(1000);
    }
    IPCThreadState::self()->flushCommands();

    mNumFramesPlayed = 0;
    mPositionTimeMediaUs = -1;
    mPositionTimeRealUs = -1;
    mSeeking = false;
    mReachedEOS = false;
    mFinalStatus = OK;
    mStarted = false;
}

// static
void AudioPlayer::AudioCallback(int event, void *user, void *info) {
    static_cast<AudioPlayer *>(user)->AudioCallback(event, info);
}

bool AudioPlayer::isSeeking() {
    Mutex::Autolock autoLock(mLock);
    return mSeeking;
}

bool AudioPlayer::reachedEOS(status_t *finalStatus) {
    *finalStatus = OK;

    Mutex::Autolock autoLock(mLock);
    *finalStatus = mFinalStatus;
    return mReachedEOS;
}

// static
size_t AudioPlayer::AudioSinkCallback(
        MediaPlayerBase::AudioSink *audioSink,
        void *buffer, size_t size, void *cookie) {
    AudioPlayer *me = (AudioPlayer *)cookie;

    return me->fillBuffer(buffer, size);
}

void AudioPlayer::AudioCallback(int event, void *info) {
    if (event != AudioTrack::EVENT_MORE_DATA) {
        return;
    }

    AudioTrack::Buffer *buffer = (AudioTrack::Buffer *)info;
    size_t numBytesWritten = fillBuffer(buffer->raw, buffer->size);

    buffer->size = numBytesWritten;
}

size_t AudioPlayer::fillBuffer(void *data, size_t size) {
    if (mNumFramesPlayed == 0) {
        LOGV("AudioCallback");
    }

    if (mReachedEOS) {
        return 0;
    }

    size_t size_done = 0;
    size_t size_remaining = size;
    while (size_remaining > 0) {
        MediaSource::ReadOptions options;

        {
            Mutex::Autolock autoLock(mLock);

            if (mSeeking) {
                if (mIsFirstBuffer) {
                    if (mFirstBuffer != NULL) {
                        mFirstBuffer->release();
                        mFirstBuffer = NULL;
                    }
                    mIsFirstBuffer = false;
                }

                options.setSeekTo(mSeekTimeUs);

                if (mInputBuffer != NULL) {
                    mInputBuffer->release();
                    mInputBuffer = NULL;
                }

                mSeeking = false;
                if (mObserver) {
                    mObserver->postAudioSeekComplete();
                }
            }
        }

        if (mInputBuffer == NULL) {
            status_t err;

            if (mIsFirstBuffer) {
                mInputBuffer = mFirstBuffer;
                mFirstBuffer = NULL;
                err = mFirstBufferResult;

                mIsFirstBuffer = false;
            } else {
                err = mSource->read(&mInputBuffer, &options);
            }

            CHECK((err == OK && mInputBuffer != NULL)
                   || (err != OK && mInputBuffer == NULL));

            Mutex::Autolock autoLock(mLock);

            if (err != OK) {
                if (mObserver && !mReachedEOS) {
                    mObserver->postAudioEOS();
                }

                mReachedEOS = true;
                mFinalStatus = err;
                break;
            }

            CHECK(mInputBuffer->meta_data()->findInt64(
                        kKeyTime, &mPositionTimeMediaUs));

            mPositionTimeRealUs =
                ((mNumFramesPlayed + size_done / mFrameSize) * 1000000)
                    / mSampleRate;

            LOGV("buffer->size() = %d, "
                 "mPositionTimeMediaUs=%.2f mPositionTimeRealUs=%.2f",
                 mInputBuffer->range_length(),
                 mPositionTimeMediaUs / 1E6, mPositionTimeRealUs / 1E6);
        }

        if (mInputBuffer->range_length() == 0) {
            mInputBuffer->release();
            mInputBuffer = NULL;

            continue;
        }

        size_t copy = size_remaining;
        if (copy > mInputBuffer->range_length()) {
            copy = mInputBuffer->range_length();
        }

        memcpy((char *)data + size_done,
               (const char *)mInputBuffer->data() + mInputBuffer->range_offset(),
               copy);

        mInputBuffer->set_range(mInputBuffer->range_offset() + copy,
                                mInputBuffer->range_length() - copy);

        size_done += copy;
        size_remaining -= copy;
    }

    Mutex::Autolock autoLock(mLock);
    mNumFramesPlayed += size_done / mFrameSize;

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    //Reset the interpolation time
    mRealTimeInterpolation = GetSystemTimeuSec();
#endif
    return size_done;
}

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
//Function to get the system time
int64_t AudioPlayer::GetSystemTimeuSec(){
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return ((int64_t)tv.tv_sec * 1000000 + tv.tv_usec);
}
#endif

int64_t AudioPlayer::getRealTimeUs() {
    Mutex::Autolock autoLock(mLock);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    int64_t realtime = getRealTimeUsLocked();

    //We need to interpolate the time to reolution of 1msec
    //to get precise posting of buffers
    int64_t deltaFromPosting = 0;

    //Take care of first time read in case no frame has returned
    if(mRealTimeInterpolation ==0){
        LOGV("reset %lld",mRealTimeInterpolation);
        mRealTimeInterpolation = GetSystemTimeuSec();
        deltaFromPosting = 0;
    }
    else{
        //Fetch the delta time from the last time we got the audio
        //frame completion. We are only intereseted in delata
        LOGV("mRealTimeInterpolation %lld = %lld - %lld",
          ((int64_t)GetSystemTimeuSec()) - mRealTimeInterpolation,
          ((int64_t)GetSystemTimeuSec()), mRealTimeInterpolation);

        deltaFromPosting =  GetSystemTimeuSec() - mRealTimeInterpolation;
    }

    //if audio hangs we should drop frames. We will wait worst case of
    //1 Sec
    if((deltaFromPosting > 1000000) || (deltaFromPosting < 0) ) {
        LOGE("To late ... there is a hang %lld",deltaFromPosting);
        return realtime;
    }

    LOGV("IPT %lld",deltaFromPosting/1000);
    return realtime + deltaFromPosting;
#else
    return getRealTimeUsLocked();
#endif

}

int64_t AudioPlayer::getRealTimeUsLocked() const {
    return -mLatencyUs + (mNumFramesPlayed * 1000000) / mSampleRate;
}

int64_t AudioPlayer::getMediaTimeUs() {
    Mutex::Autolock autoLock(mLock);

    if (mPositionTimeMediaUs < 0 || mPositionTimeRealUs < 0) {
        return 0;
    }

    int64_t realTimeOffset = getRealTimeUsLocked() - mPositionTimeRealUs;
    if (realTimeOffset < 0) {
        realTimeOffset = 0;
    }

    return mPositionTimeMediaUs + realTimeOffset;
}

bool AudioPlayer::getMediaTimeMapping(
        int64_t *realtime_us, int64_t *mediatime_us) {
    Mutex::Autolock autoLock(mLock);

    *realtime_us = mPositionTimeRealUs;
    *mediatime_us = mPositionTimeMediaUs;

    return mPositionTimeRealUs != -1 && mPositionTimeMediaUs != -1;
}

status_t AudioPlayer::seekTo(int64_t time_us) {
    Mutex::Autolock autoLock(mLock);

    mSeeking = true;
    mReachedEOS = false;
    mSeekTimeUs = time_us;

    if (mAudioSink != NULL) {
        mAudioSink->flush();
    } else {
        mAudioTrack->flush();
    }

    return OK;
}

}
