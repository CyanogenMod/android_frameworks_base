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
#define LOG_TAG "AwesomePlayer"
#include <utils/Log.h>

#include <dlfcn.h>

#include "include/ARTSPController.h"
#include "include/AwesomePlayer.h"
#include "include/LiveSource.h"
#include "include/SoftwareRenderer.h"
#include "include/NuCachedSource2.h"
#include "include/ThrottledSource.h"
#include "include/MPEG2TSExtractor.h"

#include "ARTPSession.h"
#include "APacketSource.h"
#include "ASessionDescription.h"
#include "UDPPusher.h"

#include <binder/IPCThreadState.h>
#include <media/stagefright/AudioPlayer.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>

#include <surfaceflinger/ISurface.h>

#include <media/stagefright/foundation/ALooper.h>

#ifdef OMAP_ENHANCEMENT
#include "include/ASFExtractor.h"

#if defined(TARGET_OMAP4)
#include <OMX_TI_Video.h>
#include <OMX_TI_Common.h>
#include <OMX_TI_IVCommon.h>
#endif

#endif

namespace android {
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
extern void updateMetaData(sp<MetaData> meta_track);
#endif
static int64_t kLowWaterMarkUs = 2000000ll;  // 2secs
#ifndef OMAP_ENHANCEMENT
static int64_t kHighWaterMarkUs = 10000000ll;  // 10secs
#else
static int64_t kHighWaterMarkUs = 6000000ll;  // 6secs
#endif
static const size_t kLowWaterMarkBytes = 40000;
static const size_t kHighWaterMarkBytes = 200000;

struct AwesomeEvent : public TimedEventQueue::Event {
    AwesomeEvent(
            AwesomePlayer *player,
            void (AwesomePlayer::*method)())
        : mPlayer(player),
          mMethod(method) {
    }

protected:
    virtual ~AwesomeEvent() {}

    virtual void fire(TimedEventQueue *queue, int64_t /* now_us */) {
        (mPlayer->*mMethod)();
    }

private:
    AwesomePlayer *mPlayer;
    void (AwesomePlayer::*mMethod)();

    AwesomeEvent(const AwesomeEvent &);
    AwesomeEvent &operator=(const AwesomeEvent &);
};

struct AwesomeRemoteRenderer : public AwesomeRenderer {
    AwesomeRemoteRenderer(const sp<IOMXRenderer> &target)
        : mTarget(target) {
    }

    virtual status_t initCheck() const {
        return OK;
    }

    virtual void render(MediaBuffer *buffer) {
        void *id;
        if (buffer->meta_data()->findPointer(kKeyBufferID, &id)) {
            mTarget->render((IOMX::buffer_id)id);
        }
    }

#ifdef OMAP_ENHANCEMENT
    virtual Vector< sp<IMemory> > getBuffers(){
        return mTarget->getBuffers();
    }

    virtual bool setCallback(release_rendered_buffer_callback cb, void *cookie) {
        return mTarget->setCallback(cb, cookie);
    }

    virtual void set_s3d_frame_layout(uint32_t s3d_mode, uint32_t s3d_fmt, uint32_t s3d_order, uint32_t s3d_subsampling) {
         mTarget->set_s3d_frame_layout(s3d_mode, s3d_fmt, s3d_order, s3d_subsampling);
    }

    virtual void resizeRenderer(void* resize_params) {
        mTarget->resizeRenderer(resize_params);
    }

    virtual void requestRendererClone(bool enable) {
        mTarget->requestRendererClone(enable);
    }
#endif

private:
    sp<IOMXRenderer> mTarget;

    AwesomeRemoteRenderer(const AwesomeRemoteRenderer &);
    AwesomeRemoteRenderer &operator=(const AwesomeRemoteRenderer &);
};

struct AwesomeLocalRenderer : public AwesomeRenderer {
    AwesomeLocalRenderer(
            bool previewOnly,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            const sp<ISurface> &surface,
            size_t displayWidth, size_t displayHeight,
            size_t decodedWidth, size_t decodedHeight,
            int32_t rotationDegrees)
        : mInitCheck(NO_INIT),
          mTarget(NULL),
          mLibHandle(NULL) {
            mInitCheck = init(previewOnly, componentName,
                 colorFormat, surface, displayWidth,
                 displayHeight, decodedWidth, decodedHeight,
                 rotationDegrees);
    }

    virtual status_t initCheck() const {
        return mInitCheck;
    }

    virtual void render(MediaBuffer *buffer) {
        render((const uint8_t *)buffer->data() + buffer->range_offset(),
               buffer->range_length());
    }

    void render(const void *data, size_t size) {
        mTarget->render(data, size, NULL);
    }

#ifdef OMAP_ENHANCEMENT
    virtual Vector< sp<IMemory> > getBuffers(){
        return mTarget->getBuffers();
    }
    virtual void resizeRenderer(void* resize_params) {
        mTarget->resizeRenderer(resize_params);
    }
    virtual void requestRendererClone(bool enable) {
        mTarget->requestRendererClone(enable);
    }
#endif

protected:
    virtual ~AwesomeLocalRenderer() {
        delete mTarget;
        mTarget = NULL;

        if (mLibHandle) {
            dlclose(mLibHandle);
            mLibHandle = NULL;
        }
    }

private:
    status_t mInitCheck;
    VideoRenderer *mTarget;
    void *mLibHandle;

    status_t init(
            bool previewOnly,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            const sp<ISurface> &surface,
            size_t displayWidth, size_t displayHeight,
            size_t decodedWidth, size_t decodedHeight,
            int32_t rotationDegrees);

    AwesomeLocalRenderer(const AwesomeLocalRenderer &);
    AwesomeLocalRenderer &operator=(const AwesomeLocalRenderer &);;
};

status_t AwesomeLocalRenderer::init(
        bool previewOnly,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        const sp<ISurface> &surface,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight,
        int32_t rotationDegrees) {
    if (!previewOnly) {
        // We will stick to the vanilla software-color-converting renderer
        // for "previewOnly" mode, to avoid unneccessarily switching overlays
        // more often than necessary.

        mLibHandle = dlopen("libstagefrighthw.so", RTLD_NOW);

        if (mLibHandle) {
            typedef VideoRenderer *(*CreateRendererWithRotationFunc)(
                    const sp<ISurface> &surface,
                    const char *componentName,
                    OMX_COLOR_FORMATTYPE colorFormat,
                    size_t displayWidth, size_t displayHeight,
                    size_t decodedWidth, size_t decodedHeight,
                    int32_t rotationDegrees);

            typedef VideoRenderer *(*CreateRendererFunc)(
                    const sp<ISurface> &surface,
                    const char *componentName,
                    OMX_COLOR_FORMATTYPE colorFormat,
                    size_t displayWidth, size_t displayHeight,
                    size_t decodedWidth, size_t decodedHeight);

            CreateRendererWithRotationFunc funcWithRotation =
                (CreateRendererWithRotationFunc)dlsym(
                        mLibHandle,
                        "_Z26createRendererWithRotationRKN7android2spINS_8"
                        "ISurfaceEEEPKc20OMX_COLOR_FORMATTYPEjjjji");

            if (funcWithRotation) {
                mTarget =
                    (*funcWithRotation)(
                            surface, componentName, colorFormat,
                            displayWidth, displayHeight,
                            decodedWidth, decodedHeight,
                            rotationDegrees);
            } else {
                if (rotationDegrees != 0) {
                    LOGW("renderer does not support rotation.");
                }

                CreateRendererFunc func =
                    (CreateRendererFunc)dlsym(
                            mLibHandle,
                            "_Z14createRendererRKN7android2spINS_8ISurfaceEEEPKc20"
                            "OMX_COLOR_FORMATTYPEjjjj");

                if (func) {
                    mTarget =
                        (*func)(surface, componentName, colorFormat,
                            displayWidth, displayHeight,
                            decodedWidth, decodedHeight);
                }
            }
        }
    }

    if (mTarget != NULL) {
        return OK;
    }

    mTarget = new SoftwareRenderer(
            colorFormat, surface, displayWidth, displayHeight,
            decodedWidth, decodedHeight, rotationDegrees);

    return ((SoftwareRenderer *)mTarget)->initCheck();
}

#ifdef OMAP_ENHANCEMENT
static void releaseRenderedBufferCallback(const sp<IMemory>& mem, void *cookie){
    AwesomePlayer *ap = static_cast<AwesomePlayer *>(cookie);
    ap->releaseRenderedBuffer(mem);
}
#endif

AwesomePlayer::AwesomePlayer()
    : mQueueStarted(false),
      mTimeSource(NULL),
      mVideoRendererIsPreview(false),
      mAudioPlayer(NULL),
      mFlags(0),
      mExtractorFlags(0),
#ifdef OMAP_ENHANCEMENT
      mBufferReleaseCallbackSet(false),
      mIsFirstVideoBuffer(false),
      mFirstVideoBufferResult(OK),
      mFirstVideoBuffer(NULL),
      mExtractorType(NULL),
      mExtractor(NULL),
#else
      mLastVideoBuffer(NULL),
#endif
      mVideoBuffer(NULL),
      mSuspensionState(NULL) {
    CHECK_EQ(mClient.connect(), OK);

    DataSource::RegisterDefaultSniffers();

    mVideoEvent = new AwesomeEvent(this, &AwesomePlayer::onVideoEvent);
    mVideoEventPending = false;
    mStreamDoneEvent = new AwesomeEvent(this, &AwesomePlayer::onStreamDone);
    mStreamDoneEventPending = false;
    mBufferingEvent = new AwesomeEvent(this, &AwesomePlayer::onBufferingUpdate);
    mBufferingEventPending = false;

    mCheckAudioStatusEvent = new AwesomeEvent(
            this, &AwesomePlayer::onCheckAudioStatus);

    mAudioStatusEventPending = false;

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    mS3Dparams.active = S3D_MODE_OFF;
    mS3Dparams.mode = S3D_MODE_OFF;
    mS3Dparams.fmt = S3D_FORMAT_NONE;
    mS3Dparams.order = S3D_ORDER_LF;
    mS3Dparams.subsampling = S3D_SS_NONE;
    mS3Dparams.metadata = S3D_SEI_NONE;
    mVideoMode = VID_MODE_NORMAL;
#endif

    reset();
}

AwesomePlayer::~AwesomePlayer() {
    if (mQueueStarted) {
        mQueue.stop();
    }

    reset();

    mClient.disconnect();
#ifdef OMAP_ENHANCEMENT
    mExtractor.clear();
#endif
}

void AwesomePlayer::cancelPlayerEvents(bool keepBufferingGoing) {
    mQueue.cancelEvent(mVideoEvent->eventID());
    mVideoEventPending = false;
    mQueue.cancelEvent(mStreamDoneEvent->eventID());
    mStreamDoneEventPending = false;
    mQueue.cancelEvent(mCheckAudioStatusEvent->eventID());
    mAudioStatusEventPending = false;

    if (!keepBufferingGoing) {
        mQueue.cancelEvent(mBufferingEvent->eventID());
        mBufferingEventPending = false;
    }
}

void AwesomePlayer::setListener(const wp<MediaPlayerBase> &listener) {
    Mutex::Autolock autoLock(mLock);
    mListener = listener;
}

status_t AwesomePlayer::setDataSource(
        const char *uri, const KeyedVector<String8, String8> *headers) {
    Mutex::Autolock autoLock(mLock);
    return setDataSource_l(uri, headers);
}

status_t AwesomePlayer::setDataSource_l(
        const char *uri, const KeyedVector<String8, String8> *headers) {
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    LOGD("setDataSource_l(%s)", uri);
#endif
    reset_l();

    mUri = uri;

    if (headers) {
        mUriHeaders = *headers;
    }

    // The actual work will be done during preparation in the call to
    // ::finishSetDataSource_l to avoid blocking the calling thread in
    // setDataSource for any significant time.

    return OK;
}

status_t AwesomePlayer::setDataSource(
        int fd, int64_t offset, int64_t length) {
    Mutex::Autolock autoLock(mLock);

    reset_l();

    sp<DataSource> dataSource = new FileSource(fd, offset, length);

    status_t err = dataSource->initCheck();

    if (err != OK) {
        return err;
    }

    mFileSource = dataSource;

    return setDataSource_l(dataSource);
}

status_t AwesomePlayer::setDataSource_l(
        const sp<DataSource> &dataSource) {
    sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource);

    if (extractor == NULL) {
        return UNKNOWN_ERROR;
    }
#ifdef OMAP_ENHANCEMENT
    sp<MetaData> fileMetadata = extractor->getMetaData();
    bool isAvailable = fileMetadata->findCString(kKeyMIMEType, &mExtractorType);
    if(isAvailable) {
        LOGV("%s:: ExtractorType %s", __FUNCTION__,  mExtractorType);
    }
    else {
        LOGV("%s:: ExtractorType not available", __FUNCTION__);
    }
    mExtractor = extractor;
#endif

    return setDataSource_l(extractor);
}

status_t AwesomePlayer::setDataSource_l(const sp<MediaExtractor> &extractor) {
    // Attempt to approximate overall stream bitrate by summing all
    // tracks' individual bitrates, if not all of them advertise bitrate,
    // we have to fail.

    int64_t totalBitRate = 0;

    for (size_t i = 0; i < extractor->countTracks(); ++i) {
        sp<MetaData> meta = extractor->getTrackMetaData(i);

        int32_t bitrate;
        if (!meta->findInt32(kKeyBitRate, &bitrate)) {
            totalBitRate = -1;
            break;
        }

        totalBitRate += bitrate;
    }

    mBitrate = totalBitRate;

    LOGV("mBitrate = %lld bits/sec", mBitrate);

    bool haveAudio = false;
    bool haveVideo = false;
    for (size_t i = 0; i < extractor->countTracks(); ++i) {
        sp<MetaData> meta = extractor->getTrackMetaData(i);

        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));

        if (!haveVideo && !strncasecmp(mime, "video/", 6)) {
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
           sp<MediaSource> mysource = extractor->getTrack(i);
            if(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_WMV)){
                LOGV("ASF parser doesn't support parseSEIMessages()");
            }
            else {
                LOGV("Call parseSEIMessages()");
                mysource->parseSEIMessages(mS3Dparams);
            }
            LOGV("Call setVideoSource(mysource)");
           setVideoSource(mysource);
#else
            setVideoSource(extractor->getTrack(i));
#endif
            haveVideo = true;
        } else if (!haveAudio && !strncasecmp(mime, "audio/", 6)) {
            setAudioSource(extractor->getTrack(i));
            haveAudio = true;

            if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS)) {
                // Only do this for vorbis audio, none of the other audio
                // formats even support this ringtone specific hack and
                // retrieving the metadata on some extractors may turn out
                // to be very expensive.
                sp<MetaData> fileMeta = extractor->getMetaData();
                int32_t loop;
                if (fileMeta != NULL
                        && fileMeta->findInt32(kKeyAutoLoop, &loop) && loop != 0) {
                    mFlags |= AUTO_LOOPING;
                }
            }
        }

        if (haveAudio && haveVideo) {
            break;
        }
    }

    if (!haveAudio && !haveVideo) {
        return UNKNOWN_ERROR;
    }

    mExtractorFlags = extractor->flags();

    return OK;
}

void AwesomePlayer::reset() {
    Mutex::Autolock autoLock(mLock);
    reset_l();
}

void AwesomePlayer::reset_l() {
    if (mFlags & PREPARING) {
        mFlags |= PREPARE_CANCELLED;
        if (mConnectingDataSource != NULL) {
            LOGI("interrupting the connection process");
            mConnectingDataSource->disconnect();
        }
    }

    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }

    cancelPlayerEvents();

    mCachedSource.clear();
    mAudioTrack.clear();
    mVideoTrack.clear();

#ifdef OMAP_ENHANCEMENT
    mVideoMode = VID_MODE_NORMAL;
#endif

    // Shutdown audio first, so that the respone to the reset request
    // appears to happen instantaneously as far as the user is concerned
    // If we did this later, audio would continue playing while we
    // shutdown the video-related resources and the player appear to
    // not be as responsive to a reset request.
    if (mAudioPlayer == NULL && mAudioSource != NULL) {
        // If we had an audio player, it would have effectively
        // taken possession of the audio source and stopped it when
        // _it_ is stopped. Otherwise this is still our responsibility.
        mAudioSource->stop();
    }
    mAudioSource.clear();

    mTimeSource = NULL;

    delete mAudioPlayer;
    mAudioPlayer = NULL;

#ifndef OMAP_ENHANCEMENT
    mVideoRenderer.clear();
#endif

#ifdef OMAP_ENHANCEMENT
    if (mBuffersWithRenderer.size()) {
        unsigned int i;
        unsigned int sz = mBuffersWithRenderer.size();

        for(i = 0; i < sz; i++){
            mBuffersWithRenderer[i]->release();
        }

        for(i = 0; i < sz; i++){
            mBuffersWithRenderer.pop();
        }
    }
    // release reference in case it exists
    if (mFirstVideoBuffer != NULL) {
        mFirstVideoBuffer->release();
        mFirstVideoBuffer = NULL;
    }
#else
    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }
#endif

    if (mVideoBuffer) {
        mVideoBuffer->release();
        mVideoBuffer = NULL;
    }

    if (mRTSPController != NULL) {
        mRTSPController->disconnect();
        mRTSPController.clear();
    }

    mRTPPusher.clear();
    mRTCPPusher.clear();
    mRTPSession.clear();

    if (mVideoSource != NULL) {
        mVideoSource->stop();

        // The following hack is necessary to ensure that the OMX
        // component is completely released by the time we may try
        // to instantiate it again.
        wp<MediaSource> tmp = mVideoSource;
        mVideoSource.clear();
        while (tmp.promote() != NULL) {
            usleep(1000);
        }
        IPCThreadState::self()->flushCommands();
    }

#ifdef OMAP_ENHANCEMENT
    mVideoRenderer.clear();
#endif

    mDurationUs = -1;
    mFlags = 0;
    mExtractorFlags = 0;
    mVideoWidth = mVideoHeight = -1;
    mTimeSourceDeltaUs = 0;
    mVideoTimeUs = 0;

    mSeeking = false;
    mSeekNotificationSent = false;
    mSeekTimeUs = 0;

    mUri.setTo("");
    mUriHeaders.clear();

    mFileSource.clear();

    delete mSuspensionState;
    mSuspensionState = NULL;

    mBitrate = -1;
}

void AwesomePlayer::notifyListener_l(int msg, int ext1, int ext2) {
    if (mListener != NULL) {
        sp<MediaPlayerBase> listener = mListener.promote();

        if (listener != NULL) {
            listener->sendEvent(msg, ext1, ext2);
        }
    }
}

bool AwesomePlayer::getBitrate(int64_t *bitrate) {
    off_t size;
    if (mDurationUs >= 0 && mCachedSource != NULL
            && mCachedSource->getSize(&size) == OK) {
        *bitrate = size * 8000000ll / mDurationUs;  // in bits/sec
        return true;
    }

    if (mBitrate >= 0) {
        *bitrate = mBitrate;
        return true;
    }

    *bitrate = 0;

    return false;
}

// Returns true iff cached duration is available/applicable.
bool AwesomePlayer::getCachedDuration_l(int64_t *durationUs, bool *eos) {
    int64_t bitrate;

    if (mRTSPController != NULL) {
        *durationUs = mRTSPController->getQueueDurationUs(eos);
        return true;
    } else if (mCachedSource != NULL && getBitrate(&bitrate)) {
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(eos);
        *durationUs = cachedDataRemaining * 8000000ll / bitrate;
        return true;
    }

    return false;
}

void AwesomePlayer::onBufferingUpdate() {
    Mutex::Autolock autoLock(mLock);
    if (!mBufferingEventPending) {
        return;
    }
    mBufferingEventPending = false;

    if (mCachedSource != NULL) {
        bool eos;
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&eos);

        if (eos) {
            notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
            if (mFlags & PREPARING) {
                LOGV("cache has reached EOS, prepare is done.");
                finishAsyncPrepare_l();
            }
        } else {
            int64_t bitrate;
            if (getBitrate(&bitrate)) {
                size_t cachedSize = mCachedSource->cachedSize();
                int64_t cachedDurationUs = cachedSize * 8000000ll / bitrate;

                int percentage = 100.0 * (double)cachedDurationUs / mDurationUs;
                if (percentage > 100) {
                    percentage = 100;
                }

                notifyListener_l(MEDIA_BUFFERING_UPDATE, percentage);
            } else {
                // We don't know the bitrate of the stream, use absolute size
                // limits to maintain the cache.

                if ((mFlags & PLAYING) && !eos
                        && (cachedDataRemaining < kLowWaterMarkBytes)) {
#ifdef OMAP_ENHANCEMENT
                //if low cache duration is caused by a seek, wait audio callback to avoid MEDIA_SEEK_COMPLETE being lost.
                if (!mWatchForAudioSeekComplete) {
#endif
                    LOGI("cache is running low (< %d) , pausing.",
                         kLowWaterMarkBytes);
                    mFlags |= CACHE_UNDERRUN;
                    pause_l();
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
#ifdef OMAP_ENHANCEMENT
                }
#endif
                } else if (eos || cachedDataRemaining > kHighWaterMarkBytes) {
                    if (mFlags & CACHE_UNDERRUN) {
                        LOGI("cache has filled up (> %d), resuming.",
                             kHighWaterMarkBytes);
                        mFlags &= ~CACHE_UNDERRUN;
                        play_l();
                        notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
                    } else if (mFlags & PREPARING) {
                        LOGV("cache has filled up (> %d), prepare is done",
                             kHighWaterMarkBytes);
                        finishAsyncPrepare_l();
                    }
                }
            }
        }
    }

    int64_t cachedDurationUs;
    bool eos;
    if (getCachedDuration_l(&cachedDurationUs, &eos)) {
        if ((mFlags & PLAYING) && !eos
                && (cachedDurationUs < kLowWaterMarkUs)) {
#ifdef OMAP_ENHANCEMENT
            //if low cache duration is caused by a seek, wait audio callback to avoid MEDIA_SEEK_COMPLETE being lost.
            if (!mWatchForAudioSeekComplete) {
#endif
            LOGI("cache is running low (%.2f secs) , pausing.",
                 cachedDurationUs / 1E6);
            mFlags |= CACHE_UNDERRUN;
            pause_l();
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
#ifdef OMAP_ENHANCEMENT
            }
#endif
        } else if (eos || cachedDurationUs > kHighWaterMarkUs) {
            if (mFlags & CACHE_UNDERRUN) {
                LOGI("cache has filled up (%.2f secs), resuming.",
                     cachedDurationUs / 1E6);
                mFlags &= ~CACHE_UNDERRUN;
                play_l();
                notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
            } else if (mFlags & PREPARING) {
                LOGV("cache has filled up (%.2f secs), prepare is done",
                     cachedDurationUs / 1E6);
                finishAsyncPrepare_l();
            }
        }
    }

    postBufferingEvent_l();
}

void AwesomePlayer::partial_reset_l() {
    // Only reset the video renderer and shut down the video decoder.
    // Then instantiate a new video decoder and resume video playback.

    mVideoRenderer.clear();

#ifndef OMAP_ENHANCEMENT
    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }
#endif

    if (mVideoBuffer) {
        mVideoBuffer->release();
        mVideoBuffer = NULL;
    }

    {
        mVideoSource->stop();

        // The following hack is necessary to ensure that the OMX
        // component is completely released by the time we may try
        // to instantiate it again.
        wp<MediaSource> tmp = mVideoSource;
        mVideoSource.clear();
        while (tmp.promote() != NULL) {
            usleep(1000);
        }
        IPCThreadState::self()->flushCommands();
    }

    CHECK_EQ(OK, initVideoDecoder(OMXCodec::kIgnoreCodecSpecificData));
}

void AwesomePlayer::onStreamDone() {
    // Posted whenever any stream finishes playing.

    Mutex::Autolock autoLock(mLock);
    if (!mStreamDoneEventPending) {
        return;
    }
    mStreamDoneEventPending = false;

    if (mStreamDoneStatus == INFO_DISCONTINUITY) {
        // This special status is returned because an http live stream's
        // video stream switched to a different bandwidth at this point
        // and future data may have been encoded using different parameters.
        // This requires us to shutdown the video decoder and reinstantiate
        // a fresh one.

        LOGV("INFO_DISCONTINUITY");

        CHECK(mVideoSource != NULL);

        partial_reset_l();
        postVideoEvent_l();
        return;
    } else if (mStreamDoneStatus != ERROR_END_OF_STREAM) {
        LOGV("MEDIA_ERROR %d", mStreamDoneStatus);

        notifyListener_l(
                MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, mStreamDoneStatus);

        pause_l(true /* at eos */);

        mFlags |= AT_EOS;
        return;
    }

    const bool allDone =
        (mVideoSource == NULL || (mFlags & VIDEO_AT_EOS))
            && (mAudioSource == NULL || (mFlags & AUDIO_AT_EOS));

    if (!allDone) {
        return;
    }

    if (mFlags & (LOOPING | AUTO_LOOPING)) {
        seekTo_l(0);

        if (mVideoSource != NULL) {
            postVideoEvent_l();
        }
    } else {
        LOGV("MEDIA_PLAYBACK_COMPLETE");
        notifyListener_l(MEDIA_PLAYBACK_COMPLETE);

        pause_l(true /* at eos */);

        mFlags |= AT_EOS;
    }
}

status_t AwesomePlayer::play() {
    Mutex::Autolock autoLock(mLock);

    mFlags &= ~CACHE_UNDERRUN;

    return play_l();
}

status_t AwesomePlayer::play_l() {
    if (mFlags & PLAYING) {
        return OK;
    }

    if (!(mFlags & PREPARED)) {
        status_t err = prepare_l();

        if (err != OK) {
            return err;
        }
    }

    mFlags |= PLAYING;
    mFlags |= FIRST_FRAME;

#ifdef OMAP_ENHANCEMENT
    if(mVideoSource != NULL) {
         if (mFirstVideoBuffer != NULL) {
                    mFirstVideoBuffer->release();
                    mFirstVideoBuffer = NULL;
        }
        mFirstVideoBufferResult = mVideoSource->read(&mFirstVideoBuffer);
        if (mFirstVideoBufferResult == INFO_FORMAT_CHANGED) {
            LOGV("First INFO_FORMAT_CHANGED!!!");
            LOGV("VideoSource signalled format change.");
            if (mVideoRenderer != NULL) {
                mVideoRendererIsPreview = false;
                initRenderer_l();
#ifdef OMAP_ENHANCEMENT
            if (mVideoRenderer != NULL) {
                // Share overlay buffers with video decoder.
                mVideoSource->setBuffers(mVideoRenderer->getBuffers(), true);
            }
#endif
            }
            CHECK(mFirstVideoBuffer == NULL);
            mFirstVideoBufferResult = OK;
            mIsFirstVideoBuffer = false;
        } 
       else {
            mIsFirstVideoBuffer = true;
        }
}
#endif
    bool deferredAudioSeek = false;

    if (mAudioSource != NULL) {
        if (mAudioPlayer == NULL) {
            if (mAudioSink != NULL) {
                mAudioPlayer = new AudioPlayer(mAudioSink, this);
                mAudioPlayer->setSource(mAudioSource);

                // We've already started the MediaSource in order to enable
                // the prefetcher to read its data.
                status_t err = mAudioPlayer->start(
                        true /* sourceAlreadyStarted */);

                if (err != OK) {
                    delete mAudioPlayer;
                    mAudioPlayer = NULL;

                    mFlags &= ~(PLAYING | FIRST_FRAME);
#ifdef OMAP_ENHANCEMENT
                    if (mFirstVideoBuffer) {
                        mFirstVideoBuffer->release();
                        mFirstVideoBuffer = NULL;
                    }
#endif
                    return err;
                }

                mTimeSource = mAudioPlayer;

                deferredAudioSeek = true;

                mWatchForAudioSeekComplete = false;
                mWatchForAudioEOS = true;
            }
        } else {
#ifdef OMAP_ENHANCEMENT
            if (!mSeeking || mVideoSource == NULL) {
               // Resume when video is not present or when
               // not seeking and flush the sink so buffer from
                //previous position is not heard
                if(mSeeking){
                    if(mAudioSink.get() != NULL){
                        mAudioSink->flush();
                    }
                }
                mAudioPlayer->resume();
            } else {
                // when seeking it is too early to resume
                // as audio has not seek yet
                mFlags |= HOLD_TO_RESUME;
            }
#else
            mAudioPlayer->resume();
#endif
        }
    }

    if (mTimeSource == NULL && mAudioPlayer == NULL) {
        mTimeSource = &mSystemTimeSource;
    }

    if (mVideoSource != NULL) {
        // Kick off video playback
        postVideoEvent_l();
    }

    if (deferredAudioSeek) {
        // If there was a seek request while we were paused
        // and we're just starting up again, honor the request now.
        seekAudioIfNecessary_l();
    }

    if (mFlags & AT_EOS) {
        // Legacy behaviour, if a stream finishes playing and then
        // is started again, we play from the start...
        seekTo_l(0);
    }

    return OK;
}

status_t AwesomePlayer::initRenderer_l() {
    if (mISurface == NULL) {
        return OK;
    }

    sp<MetaData> meta = mVideoSource->getFormat();

    int32_t format;
    const char *component;
    int32_t decodedWidth, decodedHeight;
    CHECK(meta->findInt32(kKeyColorFormat, &format));
    CHECK(meta->findCString(kKeyDecoderComponent, &component));
    CHECK(meta->findInt32(kKeyWidth, &decodedWidth));
    CHECK(meta->findInt32(kKeyHeight, &decodedHeight));
#ifdef OMAP_ENHANCEMENT
#ifdef TARGET_OMAP4
    CHECK(meta->findInt32(kKeyWidth, &mVideoWidth));
    CHECK(meta->findInt32(kKeyHeight, &mVideoHeight));
 
    if(!(meta->findInt32(kKeyPaddedWidth, &decodedWidth))) {
       CHECK(meta->findInt32(kKeyWidth, &decodedWidth));
    }
    if(!(meta->findInt32(kKeyPaddedHeight, &decodedHeight))) {
       CHECK(meta->findInt32(kKeyHeight, &decodedHeight));
    }
#endif
    LOGD(" initRenderer_l %dx%d",decodedWidth,decodedHeight );
    LOGD(" initRenderer_l %dx%d",mVideoWidth,mVideoHeight );
    if (mVideoRenderer != NULL) {
        //we cant destroy overlay based renderer here,as the overlay has 2 handles 
        //(1) from media server process (the current process)
        //(2) from surface flinger process.
        // Hence, we have to resize the renderer for new dimensions than dstroying and  
        // re-creating

        uint32_t outputBufferCnt = -1;
        outputBufferCnt = mVideoSource->getNumofOutputBuffers();
        LOGD("Codec Recommended outputBuffer count after portreconfig %d",outputBufferCnt);
        render_resize_params resize_params;
        resize_params.decoded_width = decodedWidth;
        resize_params.decoded_height = decodedHeight;
        resize_params.buffercount = outputBufferCnt;
        resize_params.display_width = mVideoWidth;
        resize_params.display_height = mVideoHeight;

        mVideoRenderer->resizeRenderer((void*)(&resize_params));
        return 0;
    } 
#endif
    int32_t rotationDegrees;
    if (!mVideoTrack->getFormat()->findInt32(
                kKeyRotation, &rotationDegrees)) {
        rotationDegrees = 0;
    }

    mVideoRenderer.clear();

#if defined( OMAP_ENHANCEMENT) && !defined(TARGET_OMAP4)
    // Initializing S3D flag, to be passed by higher layer
    int isS3D =0;
#endif

    // Must ensure that mVideoRenderer's destructor is actually executed
    // before creating a new one.
    IPCThreadState::self()->flushCommands();

    if (!strncmp("OMX.", component, 4)) {
        // Our OMX codecs allocate buffers on the media_server side
        // therefore they require a remote IOMXRenderer that knows how
        // to display them.

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        //initialize the codec recommended buffers to -1
        int32_t outputBufferCnt = -1;
        outputBufferCnt = mVideoSource->getNumofOutputBuffers();
        LOGD("Codec Recommended outputBuffer count %d",outputBufferCnt);

        sp<IOMXRenderer> native =
            mClient.interface()->createRenderer(
                    mISurface, component,
                    (OMX_COLOR_FORMATTYPE)format,
                    decodedWidth, decodedHeight,
                    mVideoWidth, mVideoHeight,
                    rotationDegrees, mS3Dparams.active, outputBufferCnt);

        if (native == NULL) {
            return NO_INIT;
        }

        mVideoRenderer = new AwesomeRemoteRenderer(native);

#elif defined(OMAP_ENHANCEMENT)
        int32_t outputBufferCnt = -1;

        sp<IOMXRenderer> native =
            mClient.interface()->createRenderer(
                    mISurface, component,
                    (OMX_COLOR_FORMATTYPE)format,
                    decodedWidth, decodedHeight,
                    mVideoWidth, mVideoHeight,
                    rotationDegrees, isS3D, outputBufferCnt);

        if (native == NULL) {
            return NO_INIT;
        }

        mVideoRenderer = new AwesomeRemoteRenderer(native);

#else
        sp<IOMXRenderer> native =
            mClient.interface()->createRenderer(
                    mISurface, component,
                    (OMX_COLOR_FORMATTYPE)format,
                    decodedWidth, decodedHeight,
                    mVideoWidth, mVideoHeight,
                    rotationDegrees);

        if (native == NULL) {
            return NO_INIT;
        }

        mVideoRenderer = new AwesomeRemoteRenderer(native);
#endif
#ifdef OMAP_ENHANCEMENT
            if (!strncmp("OMX.TI", component, 6)) {
#if defined(TARGET_OMAP4)
                if(mS3Dparams.active)
                    mVideoRenderer->set_s3d_frame_layout(mS3Dparams.mode ,mS3Dparams.fmt ,mS3Dparams.order, mS3Dparams.subsampling);
#endif
                mBufferReleaseCallbackSet = mVideoRenderer->setCallback(releaseRenderedBufferCallback, this);
                mVideoRenderer->setCallback(releaseRenderedBufferCallback, this);  
            }
#endif

    } else {
        // Other decoders are instantiated locally and as a consequence
        // allocate their buffers in local address space.
        mVideoRenderer = new AwesomeLocalRenderer(
            false,  // previewOnly
            component,
            (OMX_COLOR_FORMATTYPE)format,
            mISurface,
            mVideoWidth, mVideoHeight,
            decodedWidth, decodedHeight, rotationDegrees);
    }

    return mVideoRenderer->initCheck();
}

status_t AwesomePlayer::pause() {
    Mutex::Autolock autoLock(mLock);

    mFlags &= ~CACHE_UNDERRUN;

    return pause_l();
}

status_t AwesomePlayer::pause_l(bool at_eos) {
    if (!(mFlags & PLAYING)) {
        return OK;
    }

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    if (mVideoSource != NULL) {
        // Indicating to codec that Pause button pressed
        mVideoSource->pause();
    }
#endif
    cancelPlayerEvents(true /* keepBufferingGoing */);

    if (mAudioPlayer != NULL) {
        if (at_eos) {
            // If we played the audio stream to completion we
            // want to make sure that all samples remaining in the audio
            // track's queue are played out.
            mAudioPlayer->pause(true /* playPendingSamples */);
        } else {
            mAudioPlayer->pause();
        }
    }

    mFlags &= ~PLAYING;

    return OK;
}

bool AwesomePlayer::isPlaying() const {
    return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
}

void AwesomePlayer::setISurface(const sp<ISurface> &isurface) {
    Mutex::Autolock autoLock(mLock);

    mISurface = isurface;
}

void AwesomePlayer::setAudioSink(
        const sp<MediaPlayerBase::AudioSink> &audioSink) {
    Mutex::Autolock autoLock(mLock);

    mAudioSink = audioSink;
}

status_t AwesomePlayer::setLooping(bool shouldLoop) {
    Mutex::Autolock autoLock(mLock);

    mFlags = mFlags & ~LOOPING;

    if (shouldLoop) {
        mFlags |= LOOPING;
    }

    return OK;
}

status_t AwesomePlayer::getDuration(int64_t *durationUs) {
    Mutex::Autolock autoLock(mMiscStateLock);

    if (mDurationUs < 0) {
        return UNKNOWN_ERROR;
    }

    *durationUs = mDurationUs;

    return OK;
}

status_t AwesomePlayer::getPosition(int64_t *positionUs) {
    if (mRTSPController != NULL) {
        *positionUs = mRTSPController->getNormalPlayTimeUs();
    }
    else if (mSeeking) {
        *positionUs = mSeekTimeUs;
    } else if (mVideoSource != NULL) {
        Mutex::Autolock autoLock(mMiscStateLock);
        *positionUs = mVideoTimeUs;
    } else if (mAudioPlayer != NULL) {
        *positionUs = mAudioPlayer->getMediaTimeUs();
    } else {
        *positionUs = 0;
    }

    return OK;
}

status_t AwesomePlayer::seekTo(int64_t timeUs) {
    if (mExtractorFlags & MediaExtractor::CAN_SEEK) {
        Mutex::Autolock autoLock(mLock);
        return seekTo_l(timeUs);
    }

    return OK;
}

// static
void AwesomePlayer::OnRTSPSeekDoneWrapper(void *cookie) {
    static_cast<AwesomePlayer *>(cookie)->onRTSPSeekDone();
}

void AwesomePlayer::onRTSPSeekDone() {
    notifyListener_l(MEDIA_SEEK_COMPLETE);
    mSeekNotificationSent = true;
}

status_t AwesomePlayer::seekTo_l(int64_t timeUs) {
    if (mRTSPController != NULL) {
        mRTSPController->seekAsync(timeUs, OnRTSPSeekDoneWrapper, this);
        return OK;
    }

    if (mFlags & CACHE_UNDERRUN) {
        mFlags &= ~CACHE_UNDERRUN;
        play_l();
    }

    mSeeking = true;
    mSeekNotificationSent = false;
    mSeekTimeUs = timeUs;
    mFlags &= ~(AT_EOS | AUDIO_AT_EOS | VIDEO_AT_EOS);

    seekAudioIfNecessary_l();

    if (!(mFlags & PLAYING)) {
        LOGV("seeking while paused, sending SEEK_COMPLETE notification"
             " immediately.");

        notifyListener_l(MEDIA_SEEK_COMPLETE);
        mSeekNotificationSent = true;
    }

    return OK;
}

void AwesomePlayer::seekAudioIfNecessary_l() {
    if (mSeeking && mVideoSource == NULL && mAudioPlayer != NULL) {
        mAudioPlayer->seekTo(mSeekTimeUs);

        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
        mSeekNotificationSent = false;
    }
}

status_t AwesomePlayer::getVideoDimensions(
        int32_t *width, int32_t *height) const {
    Mutex::Autolock autoLock(mLock);

    if (mVideoWidth < 0 || mVideoHeight < 0) {
        return UNKNOWN_ERROR;
    }

    *width = mVideoWidth;
    *height = mVideoHeight;

    return OK;
}

void AwesomePlayer::setAudioSource(sp<MediaSource> source) {
    CHECK(source != NULL);

    mAudioTrack = source;
}

status_t AwesomePlayer::initAudioDecoder() {
    sp<MetaData> meta = mAudioTrack->getFormat();

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
        mAudioSource = mAudioTrack;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    }
    else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMA)) {
        const char *componentName  = "OMX.ITTIAM.WMA.decode";
        mAudioSource = OMXCodec::Create(
        mClient.interface(), mAudioTrack->getFormat(),
        false,
        mAudioTrack, componentName);
        if (mAudioSource == NULL) {
            LOGE("Failed to create OMX component for WMA codec");
        }
#endif
    } else {
#ifdef OMAP_ENHANCEMENT
        if (mVideoWidth*mVideoHeight > MAX_RESOLUTION) {
         // video is launched first, so these capablities are known
         // audio can be selected accordingly
         // TODO: extend this to a method that can include more
         // capabilities to evaluate

#ifdef TARGET_OMAP4
            //for OMAP4 720p,1080p videos, lets stick to OMX.PV audio codecs
            mAudioSource = OMXCodec::Create(
                    mClient.interface(), mAudioTrack->getFormat(),
                    false, // createEncoder
                    mAudioTrack);
#else
        bool isIttiamAudioCodecRequired = false;
        bool is720PCodecRequired = (mVideoWidth*mVideoHeight > MAX_RESOLUTION) ? true : false;

        if (true == is720PCodecRequired) {
            isIttiamAudioCodecRequired = true;
        }
        if (true == isIttiamAudioCodecRequired){

         // video is launched first, so these capablities are known
         // audio can be selected accordingly
         // TODO: extend this to a method that can include more
         // capabilities to evaluate

            const char *componentName;
            if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)) {
                componentName = "OMX.ITTIAM.AAC.decode";

            mAudioSource = OMXCodec::Create(
                    mClient.interface(), mAudioTrack->getFormat(),
                    false, // createEncoder
                        mAudioTrack, componentName);
            }
            else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMA)) {
                componentName = "OMX.ITTIAM.WMA.decode";

                mAudioSource = OMXCodec::Create(
                        mClient.interface(), mAudioTrack->getFormat(),
                        false,
                        mAudioTrack, componentName);

            }
            else {
                componentName = "NoComponentAvailable";

                mAudioSource = OMXCodec::Create(
                        mClient.interface(), mAudioTrack->getFormat(),
                        false,
                        mAudioTrack);
            }
        }
#endif
        } else {
            mAudioSource = OMXCodec::Create(
                    mClient.interface(), mAudioTrack->getFormat(),
                    false, // createEncoder
                    mAudioTrack);
        }
#else
        mAudioSource = OMXCodec::Create(
                mClient.interface(), mAudioTrack->getFormat(),
                false, // createEncoder
                mAudioTrack);
#endif
    }

    if (mAudioSource != NULL) {
        int64_t durationUs;
        if (mAudioTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        status_t err = mAudioSource->start();

        if (err != OK) {
            mAudioSource.clear();
            return err;
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_QCELP)) {
        // For legacy reasons we're simply going to ignore the absence
        // of an audio decoder for QCELP instead of aborting playback
        // altogether.
        return OK;
    }

    return mAudioSource != NULL ? OK : UNKNOWN_ERROR;
}

void AwesomePlayer::setVideoSource(sp<MediaSource> source) {
    CHECK(source != NULL);

    mVideoTrack = source;
}

#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
/*
  * Dynamically update S3D display driver based on framelayout configuration changes
  * sent by Codec as OMX metadata
  */
void AwesomePlayer::updateS3DRenderer()
{

    status_t ret = NO_ERROR;
    void *OMXplaformPrivate;
    OMX_OTHER_EXTRADATATYPE *extraData;
    int32_t lCounter = 0;

    CHECK_EQ(mVideoBuffer->meta_data()->findPointer(kKeyPlatformPrivate, &OMXplaformPrivate), true);

    if ( ((OMX_TI_PLATFORMPRIVATE *)OMXplaformPrivate)->nMetaDataSize <= 0 || ((OMX_TI_PLATFORMPRIVATE *) OMXplaformPrivate)->pMetaDataBuffer == NULL)
    {
        LOGV("No MetaData in this buffer \n");
        return;
    }

    do
    {
        extraData = (OMX_OTHER_EXTRADATATYPE *) (((OMX_TI_PLATFORMPRIVATE *) OMXplaformPrivate)->pMetaDataBuffer + lCounter);
        switch( extraData-> eType)
        {
            case OMX_TI_SEIinfo2004Frame1:
            case OMX_TI_SEIinfo2004Frame2:
            {
                if(configSEI2004Infos(extraData))
                    //update S3D driver dynamic configuration parameters
                    mVideoRenderer->set_s3d_frame_layout(mS3Dparams.mode ,mS3Dparams.fmt ,mS3Dparams.order, mS3Dparams.subsampling);
                return;
                break;
            }
            case OMX_TI_SEIinfo2010Frame1:
            case OMX_TI_SEIinfo2010Frame2:
            {
                if(configSEI2010Infos(extraData))
                    //update S3D driver dynamic configuration parameters
                    mVideoRenderer->set_s3d_frame_layout(mS3Dparams.mode ,mS3Dparams.fmt ,mS3Dparams.order, mS3Dparams.subsampling);
                return;
                break;
            }
            default:
            {
                LOGV("No SEI meta data \n");
                break;
            }
        }
        lCounter += extraData->nSize;
    }while ((lCounter < ( (OMX_TI_PLATFORMPRIVATE *)OMXplaformPrivate)->nMetaDataSize ) && (extraData-> eType !=0 ));

}

bool AwesomePlayer::configSEI2004Infos(OMX_OTHER_EXTRADATATYPE *extraData)
{
    if(mS3Dparams.metadata != S3D_SEI_STEREO_INFO_PROGRESSIVE || mS3Dparams.metadata != S3D_SEI_STEREO_INFO_INTERLACED)
        LOGV("SEI buffer configuration changed \n");

    OMX_TI_STEREODECINFO * pstereoDecInfo;
    pstereoDecInfo = (OMX_TI_STEREODECINFO *) (extraData->data);

    if(sizeof(OMX_TI_STEREODECINFO) == extraData->nDataSize)
    {
        if(pstereoDecInfo->nFieldViewsFlag)
        {
            if(pstereoDecInfo->nTopFieldIsLeftViewFlag)
                mS3Dparams.order = S3D_ORDER_LF;
            else
                mS3Dparams.order = S3D_ORDER_RF;
        }
        else
        {
             if(pstereoDecInfo->nCurrentFrameIsLeftViewFlag)
                mS3Dparams.order = S3D_ORDER_LF;
            else
                mS3Dparams.order = S3D_ORDER_RF;
        }
        return true;
    }
    return false;
}

bool AwesomePlayer::configSEI2010Infos(OMX_OTHER_EXTRADATATYPE *extraData)
{
    if(mS3Dparams.metadata != S3D_SEI_STEREO_FRAME_PACKING)
        LOGV("SEI buffer configuration changed \n");

    OMX_TI_FRAMEPACKINGDECINFO * pframePackingDecInfo;
    pframePackingDecInfo = (OMX_TI_FRAMEPACKINGDECINFO *) (extraData->data);

    if(sizeof(OMX_TI_FRAMEPACKINGDECINFO) == extraData->nDataSize)
    {
        if(pframePackingDecInfo->nFramePackingArrangementCancelFlag)
        {
            set_frame_packing_arrangement_type(pframePackingDecInfo->nFramePackingArrangementType, mS3Dparams.fmt, mS3Dparams.subsampling);
            if(pframePackingDecInfo->nContentInterpretationType)
                mS3Dparams.order = S3D_ORDER_LF;
            else
                mS3Dparams.order = S3D_ORDER_RF;
            return true;
        }
    }
    return false;
}
#endif

status_t AwesomePlayer::initVideoDecoder(uint32_t flags) {
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

    //Call config parser to update profile,level,interlaced,reference frame data
    updateMetaData(mVideoTrack->getFormat());

    int32_t isInterlaced = false;
    mVideoTrack->getFormat()->findInt32(kKeyVideoInterlaced, &isInterlaced);

    mVideoSource = OMXCodec::Create(
            mClient.interface(), mVideoTrack->getFormat(),
            false, // createEncoder
            mVideoTrack,
            NULL,
            (flags | isInterlaced)?OMXCodec::kPreferInterlacedOutputContent:0);
#else
    mVideoSource = OMXCodec::Create(
            mClient.interface(), mVideoTrack->getFormat(),
            false, // createEncoder
            mVideoTrack,
            NULL, flags);
#endif

    if (mVideoSource != NULL) {
        int64_t durationUs;
        if (mVideoTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        CHECK(mVideoTrack->getFormat()->findInt32(kKeyWidth, &mVideoWidth));
        CHECK(mVideoTrack->getFormat()->findInt32(kKeyHeight, &mVideoHeight));

#ifndef OMAP_ENHANCEMENT
        status_t err = mVideoSource->start();

        if (err != OK) {
            mVideoSource.clear();
            return err;
        }
#endif

    }

    return mVideoSource != NULL ? OK : UNKNOWN_ERROR;
}

void AwesomePlayer::finishSeekIfNecessary(int64_t videoTimeUs) {
    if (!mSeeking) {
        return;
    }

    if (mAudioPlayer != NULL) {
        LOGV("seeking audio to %lld us (%.2f secs).", videoTimeUs, videoTimeUs / 1E6);

        // If we don't have a video time, seek audio to the originally
        // requested seek time instead.

        mAudioPlayer->seekTo(videoTimeUs < 0 ? mSeekTimeUs : videoTimeUs);
        mAudioPlayer->resume();
        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
    } else if (!mSeekNotificationSent) {
        // If we're playing video only, report seek complete now,
        // otherwise audio player will notify us later.
        notifyListener_l(MEDIA_SEEK_COMPLETE);
    }

    mFlags |= FIRST_FRAME;
    mSeeking = false;
    mSeekNotificationSent = false;
}

void AwesomePlayer::onVideoEvent() {
    Mutex::Autolock autoLock(mLock);
    if (!mVideoEventPending) {
        // The event has been cancelled in reset_l() but had already
        // been scheduled for execution at that time.
        return;
    }
    mVideoEventPending = false;

    if (mSeeking) {
#ifdef OMAP_ENHANCEMENT
        if (mFirstVideoBuffer) {
            mFirstVideoBuffer->release();
            mFirstVideoBuffer = NULL;
        }
#else
        if (mLastVideoBuffer) {
            mLastVideoBuffer->release();
            mLastVideoBuffer = NULL;
        }
#endif
        if (mVideoBuffer) {
            mVideoBuffer->release();
            mVideoBuffer = NULL;
        }

        if (mCachedSource != NULL && mAudioSource != NULL) {
            // We're going to seek the video source first, followed by
            // the audio source.
            // In order to avoid jumps in the DataSource offset caused by
            // the audio codec prefetching data from the old locations
            // while the video codec is already reading data from the new
            // locations, we'll "pause" the audio source, causing it to
            // stop reading input data until a subsequent seek.

            if (mAudioPlayer != NULL) {
                mAudioPlayer->pause();
            }
            mAudioSource->pause();
        }
    }

    if (!mVideoBuffer) {
        MediaSource::ReadOptions options;
        if (mSeeking) {
            LOGV("seeking to %lld us (%.2f secs)", mSeekTimeUs, mSeekTimeUs / 1E6);
#ifdef OMAP_ENHANCEMENT
            if (mIsFirstVideoBuffer) {
                if (mFirstVideoBuffer != NULL) {
                    mFirstVideoBuffer->release();
                    mFirstVideoBuffer = NULL;
                }
                mIsFirstVideoBuffer = false;
            }
#endif
            options.setSeekTo(
                    mSeekTimeUs, MediaSource::ReadOptions::SEEK_CLOSEST_SYNC);
        }
        for (;;) {
#ifdef OMAP_ENHANCEMENT
            status_t err;
            if (mIsFirstVideoBuffer) {
                mVideoBuffer = mFirstVideoBuffer;
                mFirstVideoBuffer = NULL;
                err = mFirstVideoBufferResult;

                mIsFirstVideoBuffer = false;
            } else {
                err = mVideoSource->read(&mVideoBuffer, &options);
            }
#else
            status_t err = mVideoSource->read(&mVideoBuffer, &options);
#endif
            options.clearSeekTo();

            if (err != OK) {
                CHECK_EQ(mVideoBuffer, NULL);

                if (err == INFO_FORMAT_CHANGED) {
                    LOGV("VideoSource signalled format change.");

                    if (mVideoRenderer != NULL) {
                        mVideoRendererIsPreview = false;
#ifdef OMAP_ENHANCEMENT
                        if (mBuffersWithRenderer.size()) {
                            unsigned int i;
                            unsigned int sz = mBuffersWithRenderer.size();

                            for(i = 0; i < sz; i++){
                                mBuffersWithRenderer[i]->release();
                            }

                            for(i = 0; i < sz; i++){
                                mBuffersWithRenderer.pop();
                            }
                        }

                        if (mFirstVideoBuffer != NULL) {
                            mFirstVideoBuffer->release();
                            mFirstVideoBuffer = NULL;
                        }
                        if (mVideoBuffer) {
                            mVideoBuffer->release();
                            mVideoBuffer = NULL;
                        }
#endif

                        err = initRenderer_l();
                        if (err == OK) {
#ifdef OMAP_ENHANCEMENT
                        if (mVideoRenderer != NULL) {
                            // Share overlay buffers with video decoder.
                            mVideoSource->setBuffers(mVideoRenderer->getBuffers(), true);
                        }
                        postVideoEvent_l(0);
#endif
                            return;
                        }

                        // fall through
                    } else {
                        continue;
                    }
                }

                // So video playback is complete, but we may still have
                // a seek request pending that needs to be applied
                // to the audio track.
                if (mSeeking) {
                    LOGV("video stream ended while seeking!");
                }
                finishSeekIfNecessary(-1);

                mFlags |= VIDEO_AT_EOS;
                postStreamDoneEvent_l(err);
                return;
            }

            if (mVideoBuffer->range_length() == 0) {
                // Some decoders, notably the PV AVC software decoder
                // return spurious empty buffers that we just want to ignore.

                mVideoBuffer->release();
                mVideoBuffer = NULL;
                continue;
            }

            break;
        }
    }

    int64_t timeUs;
    CHECK(mVideoBuffer->meta_data()->findInt64(kKeyTime, &timeUs));

    {
        Mutex::Autolock autoLock(mMiscStateLock);
        mVideoTimeUs = timeUs;
    }

    bool wasSeeking = mSeeking;
    finishSeekIfNecessary(timeUs);

    TimeSource *ts = (mFlags & AUDIO_AT_EOS) ? &mSystemTimeSource : mTimeSource;

    if (mFlags & FIRST_FRAME) {
        mFlags &= ~FIRST_FRAME;

        mTimeSourceDeltaUs = ts->getRealTimeUs() - timeUs;
    }

    int64_t realTimeUs, mediaTimeUs;
    if (!(mFlags & AUDIO_AT_EOS) && mAudioPlayer != NULL
        && mAudioPlayer->getMediaTimeMapping(&realTimeUs, &mediaTimeUs)) {
        mTimeSourceDeltaUs = realTimeUs - mediaTimeUs;
    }

    int64_t nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs;

    int64_t latenessUs = nowUs - timeUs;

    if (wasSeeking) {
        // Let's display the first frame after seeking right away.
        latenessUs = 0;
    }

    if (mRTPSession != NULL) {
        // We'll completely ignore timestamps for gtalk videochat
        // and we'll play incoming video as fast as we get it.
        latenessUs = 0;
    }
#ifdef OMAP_ENHANCEMENT
    LOGV("%s::%d: (latenessUs= %lld) = ((nowUs= %lld) - (timeUs=%lld))", __FUNCTION__, __LINE__, latenessUs, nowUs, timeUs);

    if (latenessUs > 50000) {
        // We're more than 50ms late.

        /* Trace to detect frame drops */
        LOGV("Frame dropped - lateness (%lld - %lld = %lld uS)",nowUs,timeUs,latenessUs);

#else
    if (latenessUs > 40000) {
        // We're more than 40ms late.
#endif
        LOGV("we're late by %lld us (%.2f secs)", latenessUs, latenessUs / 1E6);

        mVideoBuffer->release();
        mVideoBuffer = NULL;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        postVideoEvent_l(0);
#else
        postVideoEvent_l();
#endif
        return;
    }
#if defined(OMAP_ENHANCEMENT) && !defined(TARGET_OMAP4)
    if (latenessUs < -100000) {
        // We're more than 100ms early.
        LOGV("%s::%d: Frame is early than 100ms: %lld", __FUNCTION__, __LINE__, latenessUs);
        LOGV("%s::%d: postVideoEvent_l(10000)", __FUNCTION__, __LINE__);
#else
    if (latenessUs < -10000) {
        // We're more than 10ms early.
#endif

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        //There is no need to poll for 10 msec
        //This going to increase the MHz on ARM. Try to come back
        //when we need to exactly post. Need to do a mod for 100 msec
        //so that while seeking the video frame doesnt wait for lateness
        //which can be a huge delay. This will result like a hang
        postVideoEvent_l((latenessUs * -1) % 100000);
#else
        postVideoEvent_l(10000);
#endif
        return;
    }

    if (mVideoRendererIsPreview || mVideoRenderer == NULL) {
        mVideoRendererIsPreview = false;

        status_t err = initRenderer_l();

        if (err != OK) {
            finishSeekIfNecessary(-1);

            mFlags |= VIDEO_AT_EOS;
            postStreamDoneEvent_l(err);
            return;
        } else {
#ifdef OMAP_ENHANCEMENT
        if (mVideoRenderer != NULL) {
            // Share overlay buffers with video decoder.
            mVideoSource->setBuffers(mVideoRenderer->getBuffers(), true);
        }
#endif
    }
    }

#ifdef OMAP_ENHANCEMENT
    /*the buffer needs to be pushed to local database before calling render.
    * This is required to release the buffer back to Video source if the
    * buffer can't be queued to the DSS
    */
    mBuffersWithRenderer.push(mVideoBuffer);
#endif

    if (mVideoRenderer != NULL) {
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
    int32_t isExtraData = 0;
    if(mS3Dparams.active && mVideoBuffer->meta_data()->findInt32(kKeyIsExtraData, &isExtraData))
            updateS3DRenderer();
#endif
        mVideoRenderer->render(mVideoBuffer);
    }

#ifdef OMAP_ENHANCEMENT
    if ((!mBufferReleaseCallbackSet)  && (mBuffersWithRenderer.size())){
        mBuffersWithRenderer[0]->release();
        mBuffersWithRenderer.pop();
    }
#else
    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }
    mLastVideoBuffer = mVideoBuffer;
#endif

    mVideoBuffer = NULL;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    // No need to trigger the poll after 10 msec which is default value
    // This is causing a jerk in AV sync. We can trigger a 0 msec and accurate
    // wait which will allow more ARM sleep time
    postVideoEvent_l(0);
#else
    postVideoEvent_l();
#endif

}

void AwesomePlayer::postVideoEvent_l(int64_t delayUs) {
    if (mVideoEventPending) {
        return;
    }

    mVideoEventPending = true;
    mQueue.postEventWithDelay(mVideoEvent, delayUs < 0 ? 10000 : delayUs);
}

void AwesomePlayer::postStreamDoneEvent_l(status_t status) {
    if (mStreamDoneEventPending) {
        return;
    }
    mStreamDoneEventPending = true;

    mStreamDoneStatus = status;
    mQueue.postEvent(mStreamDoneEvent);
}

void AwesomePlayer::postBufferingEvent_l() {
    if (mBufferingEventPending) {
        return;
    }
    mBufferingEventPending = true;
    mQueue.postEventWithDelay(mBufferingEvent, 1000000ll);
}

void AwesomePlayer::postCheckAudioStatusEvent_l() {
    if (mAudioStatusEventPending) {
        return;
    }
    mAudioStatusEventPending = true;
    mQueue.postEvent(mCheckAudioStatusEvent);
}

void AwesomePlayer::onCheckAudioStatus() {
    Mutex::Autolock autoLock(mLock);
    if (!mAudioStatusEventPending) {
        // Event was dispatched and while we were blocking on the mutex,
        // has already been cancelled.
        return;
    }

    mAudioStatusEventPending = false;

    if (mWatchForAudioSeekComplete && !mAudioPlayer->isSeeking()) {
        mWatchForAudioSeekComplete = false;

        if (!mSeekNotificationSent) {
            notifyListener_l(MEDIA_SEEK_COMPLETE);
            mSeekNotificationSent = true;
        }

        mSeeking = false;
    }

    status_t finalStatus;
    if (mWatchForAudioEOS && mAudioPlayer->reachedEOS(&finalStatus)) {
        mWatchForAudioEOS = false;
        mFlags |= AUDIO_AT_EOS;
        mFlags |= FIRST_FRAME;
        postStreamDoneEvent_l(finalStatus);
    }
}

status_t AwesomePlayer::prepare() {
    Mutex::Autolock autoLock(mLock);
    return prepare_l();
}

status_t AwesomePlayer::prepare_l() {
    if (mFlags & PREPARED) {
        return OK;
    }

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;
    }

    mIsAsyncPrepare = false;
    status_t err = prepareAsync_l();

    if (err != OK) {
        return err;
    }

    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }

    return mPrepareResult;
}

status_t AwesomePlayer::prepareAsync() {
    Mutex::Autolock autoLock(mLock);

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    mIsAsyncPrepare = true;
    return prepareAsync_l();
}

status_t AwesomePlayer::prepareAsync_l() {
    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    if (!mQueueStarted) {
        mQueue.start();
        mQueueStarted = true;
    }

    mFlags |= PREPARING;
    mAsyncPrepareEvent = new AwesomeEvent(
            this, &AwesomePlayer::onPrepareAsyncEvent);

    mQueue.postEvent(mAsyncPrepareEvent);

    return OK;
}

status_t AwesomePlayer::finishSetDataSource_l() {
    sp<DataSource> dataSource;

    if (!strncasecmp("http://", mUri.string(), 7)) {
        mConnectingDataSource = new NuHTTPDataSource;

        mLock.unlock();
        status_t err = mConnectingDataSource->connect(mUri, &mUriHeaders);
        mLock.lock();

        if (err != OK) {
            mConnectingDataSource.clear();

            LOGI("mConnectingDataSource->connect() returned %d", err);
            return err;
        }

#if 0
        mCachedSource = new NuCachedSource2(
                new ThrottledSource(
                    mConnectingDataSource, 50 * 1024 /* bytes/sec */));
#else
        mCachedSource = new NuCachedSource2(mConnectingDataSource);
#endif
        mConnectingDataSource.clear();

        dataSource = mCachedSource;

        // We're going to prefill the cache before trying to instantiate
        // the extractor below, as the latter is an operation that otherwise
        // could block on the datasource for a significant amount of time.
        // During that time we'd be unable to abort the preparation phase
        // without this prefill.

        mLock.unlock();

        for (;;) {
            bool eos;
            size_t cachedDataRemaining =
                mCachedSource->approxDataRemaining(&eos);

            if (eos || cachedDataRemaining >= kHighWaterMarkBytes
                    || (mFlags & PREPARE_CANCELLED)) {
                break;
            }

            usleep(200000);
        }

        mLock.lock();

        if (mFlags & PREPARE_CANCELLED) {
            LOGI("Prepare cancelled while waiting for initial cache fill.");
            return UNKNOWN_ERROR;
        }
    } else if (!strncasecmp(mUri.string(), "httplive://", 11)) {
        String8 uri("http://");
        uri.append(mUri.string() + 11);

        sp<LiveSource> liveSource = new LiveSource(uri.string());

        mCachedSource = new NuCachedSource2(liveSource);
        dataSource = mCachedSource;

        sp<MediaExtractor> extractor =
            MediaExtractor::Create(dataSource, MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

        static_cast<MPEG2TSExtractor *>(extractor.get())
            ->setLiveSource(liveSource);

        return setDataSource_l(extractor);
    } else if (!strncmp("rtsp://gtalk/", mUri.string(), 13)) {
        if (mLooper == NULL) {
            mLooper = new ALooper;
            mLooper->setName("gtalk rtp");
            mLooper->start(
                    false /* runOnCallingThread */,
                    false /* canCallJava */,
                    PRIORITY_HIGHEST);
        }

        const char *startOfCodecString = &mUri.string()[13];
        const char *startOfSlash1 = strchr(startOfCodecString, '/');
        if (startOfSlash1 == NULL) {
            return BAD_VALUE;
        }
        const char *startOfWidthString = &startOfSlash1[1];
        const char *startOfSlash2 = strchr(startOfWidthString, '/');
        if (startOfSlash2 == NULL) {
            return BAD_VALUE;
        }
        const char *startOfHeightString = &startOfSlash2[1];

        String8 codecString(startOfCodecString, startOfSlash1 - startOfCodecString);
        String8 widthString(startOfWidthString, startOfSlash2 - startOfWidthString);
        String8 heightString(startOfHeightString);

#if 0
        mRTPPusher = new UDPPusher("/data/misc/rtpout.bin", 5434);
        mLooper->registerHandler(mRTPPusher);

        mRTCPPusher = new UDPPusher("/data/misc/rtcpout.bin", 5435);
        mLooper->registerHandler(mRTCPPusher);
#endif

        mRTPSession = new ARTPSession;
        mLooper->registerHandler(mRTPSession);

#if 0
        // My AMR SDP
        static const char *raw =
            "v=0\r\n"
            "o=- 64 233572944 IN IP4 127.0.0.0\r\n"
            "s=QuickTime\r\n"
            "t=0 0\r\n"
            "a=range:npt=0-315\r\n"
            "a=isma-compliance:2,2.0,2\r\n"
            "m=audio 5434 RTP/AVP 97\r\n"
            "c=IN IP4 127.0.0.1\r\n"
            "b=AS:30\r\n"
            "a=rtpmap:97 AMR/8000/1\r\n"
            "a=fmtp:97 octet-align\r\n";
#elif 1
        String8 sdp;
        sdp.appendFormat(
            "v=0\r\n"
            "o=- 64 233572944 IN IP4 127.0.0.0\r\n"
            "s=QuickTime\r\n"
            "t=0 0\r\n"
            "a=range:npt=0-315\r\n"
            "a=isma-compliance:2,2.0,2\r\n"
            "m=video 5434 RTP/AVP 97\r\n"
            "c=IN IP4 127.0.0.1\r\n"
            "b=AS:30\r\n"
            "a=rtpmap:97 %s/90000\r\n"
            "a=cliprect:0,0,%s,%s\r\n"
            "a=framesize:97 %s-%s\r\n",

            codecString.string(),
            heightString.string(), widthString.string(),
            widthString.string(), heightString.string()
            );
        const char *raw = sdp.string();

#endif

        sp<ASessionDescription> desc = new ASessionDescription;
        CHECK(desc->setTo(raw, strlen(raw)));

        CHECK_EQ(mRTPSession->setup(desc), (status_t)OK);

        if (mRTPPusher != NULL) {
            mRTPPusher->start();
        }

        if (mRTCPPusher != NULL) {
            mRTCPPusher->start();
        }

        CHECK_EQ(mRTPSession->countTracks(), 1u);
        sp<MediaSource> source = mRTPSession->trackAt(0);

#if 0
        bool eos;
        while (((APacketSource *)source.get())
                ->getQueuedDuration(&eos) < 5000000ll && !eos) {
            usleep(100000ll);
        }
#endif

        const char *mime;
        CHECK(source->getFormat()->findCString(kKeyMIMEType, &mime));

        if (!strncasecmp("video/", mime, 6)) {
            setVideoSource(source);
        } else {
            CHECK(!strncasecmp("audio/", mime, 6));
            setAudioSource(source);
        }

        mExtractorFlags = MediaExtractor::CAN_PAUSE;

        return OK;
    } else if (!strncasecmp("rtsp://", mUri.string(), 7)) {
        if (mLooper == NULL) {
            mLooper = new ALooper;
            mLooper->setName("rtsp");
            mLooper->start();
        }
        mRTSPController = new ARTSPController(mLooper);
        status_t err = mRTSPController->connect(mUri.string());

        LOGI("ARTSPController::connect returned %d", err);

        if (err != OK) {
            mRTSPController.clear();
            return err;
        }

        sp<MediaExtractor> extractor = mRTSPController.get();
        return setDataSource_l(extractor);
    } else {
        dataSource = DataSource::CreateFromURI(mUri.string(), &mUriHeaders);
    }

    if (dataSource == NULL) {
        return UNKNOWN_ERROR;
    }

    sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource);

    if (extractor == NULL) {
        return UNKNOWN_ERROR;
    }
#ifdef OMAP_ENHANCEMENT
    sp<MetaData> fileMetadata = extractor->getMetaData();
    bool isAvailable = fileMetadata->findCString(kKeyMIMEType, &mExtractorType);
    if(isAvailable) {
        LOGV("%s:: ExtractorType %s", __FUNCTION__,  mExtractorType);
    }
    else {
        LOGV("%s:: ExtractorType not available", __FUNCTION__);
    }
    mExtractor = extractor;
#endif

    return setDataSource_l(extractor);
}

void AwesomePlayer::abortPrepare(status_t err) {
    CHECK(err != OK);

    if (mIsAsyncPrepare) {
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
    }

    mPrepareResult = err;
    mFlags &= ~(PREPARING|PREPARE_CANCELLED);
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();
}

// static
bool AwesomePlayer::ContinuePreparation(void *cookie) {
    AwesomePlayer *me = static_cast<AwesomePlayer *>(cookie);

    return (me->mFlags & PREPARE_CANCELLED) == 0;
}

void AwesomePlayer::onPrepareAsyncEvent() {
    Mutex::Autolock autoLock(mLock);

    if (mFlags & PREPARE_CANCELLED) {
        LOGI("prepare was cancelled before doing anything");
        abortPrepare(UNKNOWN_ERROR);
        return;
    }

    if (mUri.size() > 0) {
        status_t err = finishSetDataSource_l();

        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mVideoTrack != NULL && mVideoSource == NULL) {
        status_t err = initVideoDecoder();
#ifdef OMAP_ENHANCEMENT
            if (err == OK){
                if (mVideoRendererIsPreview || mVideoRenderer == NULL) {
                    mVideoRendererIsPreview = false;
                    initRenderer_l();
                    if (mVideoRenderer != NULL) {
                        // Share overlay buffers with video decoder.
                        mVideoSource->setBuffers(mVideoRenderer->getBuffers(), false);
                    }
                }

                err = mVideoSource->start();
                if (err != OK) {
                    mVideoSource.clear();
                    //Subsequent error handling will take of returning.
                }
            }
#endif
        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mAudioTrack != NULL && mAudioSource == NULL) {
        status_t err = initAudioDecoder();

        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mCachedSource != NULL || mRTSPController != NULL) {
        postBufferingEvent_l();
    } else {
        finishAsyncPrepare_l();
    }
}

void AwesomePlayer::finishAsyncPrepare_l() {
    if (mIsAsyncPrepare) {
        if (mVideoWidth < 0 || mVideoHeight < 0) {
            notifyListener_l(MEDIA_SET_VIDEO_SIZE, 0, 0);
        } else {
            int32_t rotationDegrees;
            if (!mVideoTrack->getFormat()->findInt32(
                        kKeyRotation, &rotationDegrees)) {
                rotationDegrees = 0;
            }

#if 1
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                notifyListener_l(
                        MEDIA_SET_VIDEO_SIZE, mVideoHeight, mVideoWidth);
            } else
#endif
            {
                notifyListener_l(
                        MEDIA_SET_VIDEO_SIZE, mVideoWidth, mVideoHeight);
            }
        }

        notifyListener_l(MEDIA_PREPARED);
    }

    mPrepareResult = OK;
    mFlags &= ~(PREPARING|PREPARE_CANCELLED);
    mFlags |= PREPARED;
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();
}

status_t AwesomePlayer::suspend() {
    LOGV("suspend");
    Mutex::Autolock autoLock(mLock);

    if (mSuspensionState != NULL) {
#ifdef OMAP_ENHANCEMENT
        if (mBuffersWithRenderer.size() == 0) {
#else
        if (mLastVideoBuffer == NULL) {
#endif
            //go into here if video is suspended again
            //after resuming without being played between
            //them
            SuspensionState *state = mSuspensionState;
            mSuspensionState = NULL;
            reset_l();
            mSuspensionState = state;
            return OK;
        }

        delete mSuspensionState;
        mSuspensionState = NULL;
    }

    if (mFlags & PREPARING) {
        mFlags |= PREPARE_CANCELLED;
        if (mConnectingDataSource != NULL) {
            LOGI("interrupting the connection process");
            mConnectingDataSource->disconnect();
        }
    }

    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }

    SuspensionState *state = new SuspensionState;
    state->mUri = mUri;
    state->mUriHeaders = mUriHeaders;
    state->mFileSource = mFileSource;

    state->mFlags = mFlags & (PLAYING | AUTO_LOOPING | LOOPING | AT_EOS);
    getPosition(&state->mPositionUs);

#ifdef OMAP_ENHANCEMENT
#ifdef TARGET_OMAP4
    // FIXME: This caching of last frame crashes in L27x. Not used anyway, but check why.
    // This should be removed after fill the code for (mBufferWithRenderer.size() > 0)
    if (0) {
#else /* TARGET_OMAP3 */
    // Currently this code is used only by OMAP3.
    if (mBuffersWithRenderer.size()) {
#endif
        size_t size = mBuffersWithRenderer[0]->range_length();
#else
    if (mLastVideoBuffer) {
        size_t size = mLastVideoBuffer->range_length();
#endif
        if (size) {
            int32_t unreadable;
#ifdef OMAP_ENHANCEMENT
            if (!mBuffersWithRenderer[0]->meta_data()->findInt32(
                        kKeyIsUnreadable, &unreadable)
                    || unreadable == 0) {
                state->mLastVideoFrameSize = size;
                state->mLastVideoFrame = malloc(size);

#ifdef TARGET_OMAP4
                // FIXME: here for OMAP4
                // Please fill this part for OMAP4
#else
                memcpy(state->mLastVideoFrame,
                   (const uint8_t *)mBuffersWithRenderer[0]->data()
                        + mBuffersWithRenderer[0]->range_offset(),
                   size);
#endif
#else
            if (!mLastVideoBuffer->meta_data()->findInt32(
                        kKeyIsUnreadable, &unreadable)
                    || unreadable == 0) {
                state->mLastVideoFrameSize = size;
                state->mLastVideoFrame = malloc(size);

                memcpy(state->mLastVideoFrame,
                   (const uint8_t *)mLastVideoBuffer->data()
                        + mLastVideoBuffer->range_offset(),
                   size);
#endif

                state->mVideoWidth = mVideoWidth;
                state->mVideoHeight = mVideoHeight;

                sp<MetaData> meta = mVideoSource->getFormat();
                CHECK(meta->findInt32(kKeyColorFormat, &state->mColorFormat));
                CHECK(meta->findInt32(kKeyWidth, &state->mDecodedWidth));
                CHECK(meta->findInt32(kKeyHeight, &state->mDecodedHeight));
            } else {
                LOGV("Unable to save last video frame, we have no access to "
                     "the decoded video data.");
            }
        }
    }

    reset_l();

    mSuspensionState = state;

    return OK;
}

status_t AwesomePlayer::resume() {
    LOGV("resume");
    Mutex::Autolock autoLock(mLock);

    if (mSuspensionState == NULL) {
        return INVALID_OPERATION;
    }

    SuspensionState *state = mSuspensionState;
    mSuspensionState = NULL;

    status_t err;
    if (state->mFileSource != NULL) {
        err = setDataSource_l(state->mFileSource);

        if (err == OK) {
            mFileSource = state->mFileSource;
        }
    } else {
        err = setDataSource_l(state->mUri, &state->mUriHeaders);
    }

    if (err != OK) {
        delete state;
        state = NULL;

        return err;
    }

    seekTo_l(state->mPositionUs);

    mFlags = state->mFlags & (AUTO_LOOPING | LOOPING | AT_EOS);

    if (state->mLastVideoFrame && mISurface != NULL) {
        mVideoRenderer =
            new AwesomeLocalRenderer(
                    true,  // previewOnly
                    "",
                    (OMX_COLOR_FORMATTYPE)state->mColorFormat,
                    mISurface,
                    state->mVideoWidth,
                    state->mVideoHeight,
                    state->mDecodedWidth,
                    state->mDecodedHeight,
                    0);

        mVideoRendererIsPreview = true;

        ((AwesomeLocalRenderer *)mVideoRenderer.get())->render(
                state->mLastVideoFrame, state->mLastVideoFrameSize);
#ifdef OMAP_ENHANCEMENT
        mVideoRenderer.clear();
        mVideoRenderer = NULL;
#endif
    }

    if (state->mFlags & PLAYING) {
        play_l();
    }

    mSuspensionState = state;
    state = NULL;

    return OK;
}

uint32_t AwesomePlayer::flags() const {
    return mExtractorFlags;
}

void AwesomePlayer::postAudioEOS() {
    postCheckAudioStatusEvent_l();
}

void AwesomePlayer::postAudioSeekComplete() {
    postCheckAudioStatusEvent_l();
}
#ifdef OMAP_ENHANCEMENT
void AwesomePlayer::releaseRenderedBuffer(const sp<IMemory>& mem){

    bool buffer_released = false;
    unsigned int i = 0;

    for(i = 0; i < mBuffersWithRenderer.size(); i++){
        if (mBuffersWithRenderer[i]->data() == mem->pointer()){
            mBuffersWithRenderer[i]->release();
            mBuffersWithRenderer.removeAt(i);
            buffer_released = true;
            break;
        }
    }

    if (buffer_released == false)
        LOGD("Something wrong... Overlay returned wrong buffer address(%p). This message is harmless if you just did a seek.", mem->pointer());
}


status_t AwesomePlayer::requestVideoCloneMode(bool enable) {
    if (enable)
    {
        if ((mVideoMode != VID_MODE_CLONE) && (mVideoRenderer != NULL)) {
            mVideoMode = VID_MODE_CLONE;
            mVideoRenderer->requestRendererClone(enable);
        }
    }
    else
    {
        if ((mVideoMode != VID_MODE_NORMAL) && (mVideoRenderer != NULL)) {
            mVideoMode = VID_MODE_NORMAL;
            mVideoRenderer->requestRendererClone(enable);
        }
    }
    LOGD("CloneMode[%d]", mVideoMode);
    return OK;
}

#endif

}  // namespace android

