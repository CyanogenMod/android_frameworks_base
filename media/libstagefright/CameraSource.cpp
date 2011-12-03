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
#define LOG_TAG "CameraSource"
#include <utils/Log.h>

#include <OMX_Component.h>
#include <binder/IPCThreadState.h>
#include <media/stagefright/CameraSource.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <camera/Camera.h>
#include <camera/CameraParameters.h>
#include <utils/String8.h>
#include <cutils/properties.h>

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
#include <TICameraParameters.h>
#include "OMX_TI_Video.h"
#endif

namespace android {

struct CameraSourceListener : public CameraListener {
    CameraSourceListener(const sp<CameraSource> &source);

    virtual void notify(int32_t msgType, int32_t ext1, int32_t ext2);
    virtual void postData(int32_t msgType, const sp<IMemory> &dataPtr);
#ifdef OMAP_ENHANCEMENT
    virtual void postDataTimestamp(
            nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr,
            uint32_t offset=0, uint32_t stride=0);
#else
    virtual void postDataTimestamp(
            nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr);
#endif

protected:
    virtual ~CameraSourceListener();

private:
    wp<CameraSource> mSource;

    CameraSourceListener(const CameraSourceListener &);
    CameraSourceListener &operator=(const CameraSourceListener &);
};

CameraSourceListener::CameraSourceListener(const sp<CameraSource> &source)
    : mSource(source) {
}

CameraSourceListener::~CameraSourceListener() {
}

void CameraSourceListener::notify(int32_t msgType, int32_t ext1, int32_t ext2) {
    LOGV("notify(%d, %d, %d)", msgType, ext1, ext2);
}

void CameraSourceListener::postData(int32_t msgType, const sp<IMemory> &dataPtr) {
    LOGV("postData(%d, ptr:%p, size:%d)",
         msgType, dataPtr->pointer(), dataPtr->size());
}

#ifdef OMAP_ENHANCEMENT
void CameraSourceListener::postDataTimestamp(
        nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr,
        uint32_t offset, uint32_t stride)
#else
void CameraSourceListener::postDataTimestamp(
        nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr)
#endif
{

    sp<CameraSource> source = mSource.promote();
    if (source.get() != NULL) {
#ifdef OMAP_ENHANCEMENT
        source->dataCallbackTimestamp(timestamp/1000, msgType, dataPtr, offset, stride);
#else
        source->dataCallbackTimestamp(timestamp/1000, msgType, dataPtr);
#endif
    }
}

static int32_t getColorFormat(const char* colorFormat) {
    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV422SP)) {
       return OMX_COLOR_FormatYUV422SemiPlanar;
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV420SP)) {
#if defined(TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
        return OMX_COLOR_FormatYUV420PackedSemiPlanar;
#else
        return OMX_COLOR_FormatYUV420SemiPlanar;
#endif
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV422I)) {
#if defined(TARGET_OMAP3) && defined(OMAP_ENHANCEMENT)
        return OMX_COLOR_FormatCbYCrY;
#else
        return OMX_COLOR_FormatYCbYCr;
#endif
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_RGB565)) {
       return OMX_COLOR_Format16bitRGB565;
    }

    LOGE("Uknown color format (%s), please add it to "
         "CameraSource::getColorFormat", colorFormat);

    CHECK_EQ(0, "Unknown color format");
}

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
static int32_t getSEIEncodingType(const char* seiEncodingType)
{
    if (!strcmp(seiEncodingType, TICameraParameters::SEI_ENCODING_2004)) {
       return OMX_TI_Video_AVC_2004_StereoInfoType;
    }

    if (!strcmp(seiEncodingType, TICameraParameters::SEI_ENCODING_2010)) {
        return OMX_TI_Video_AVC_2010_StereoFramePackingType;
    }

    if (!strcmp(seiEncodingType, TICameraParameters::SEI_ENCODING_NONE)) {
        return OMX_TI_Video_Progressive;
    }

    LOGE("Unsupported SEI encoding type (%s), reverting back to progressive", seiEncodingType);

    return OMX_TI_Video_Progressive;
}
#endif

// static
CameraSource *CameraSource::Create() {
    sp<Camera> camera = Camera::connect(0);

    if (camera.get() == NULL) {
        return NULL;
    }

    return new CameraSource(camera);
}

// static
CameraSource *CameraSource::CreateFromCamera(const sp<Camera> &camera) {
    if (camera.get() == NULL) {
        return NULL;
    }

    return new CameraSource(camera);
}

CameraSource::CameraSource(const sp<Camera> &camera)
    : mCamera(camera),
      mFirstFrameTimeUs(0),
      mLastFrameTimestampUs(0),
      mNumFramesReceived(0),
      mNumFramesEncoded(0),
      mNumFramesDropped(0),
      mNumGlitches(0),
      mGlitchDurationThresholdUs(200000),
      mCollectStats(false),
      mStarted(false) {

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    String8 s = mCamera->getParameters();
    IPCThreadState::self()->restoreCallingIdentity(token);

    printf("params: \"%s\"\n", s.string());

    int32_t width, height, stride, sliceHeight;
    CameraParameters params(s);
    params.getPreviewSize(&width, &height);

    // Calculate glitch duraton threshold based on frame rate
    int32_t frameRate = params.getPreviewFrameRate();
    int64_t glitchDurationUs = (1000000LL / frameRate);
    if (glitchDurationUs > mGlitchDurationThresholdUs) {
        mGlitchDurationThresholdUs = glitchDurationUs;
    }

    const char *colorFormatStr = params.get(CameraParameters::KEY_VIDEO_FRAME_FORMAT);
    if (colorFormatStr == NULL) {
#ifdef USE_YUV422I_DEFAULT_COLORFORMAT
        // on some devices (such as sholes), the camera doesn't properly report what
        // color format it needs, so we need to force it as a default
        colorFormatStr = CameraParameters::PIXEL_FORMAT_YUV422I;
#else
        colorFormatStr = CameraParameters::PIXEL_FORMAT_YUV420SP;
#endif
    }

    int32_t colorFormat = getColorFormat(colorFormatStr);

    // XXX: query camera for the stride and slice height
    // when the capability becomes available.
    stride = width;
    sliceHeight = height;

    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
    mMeta->setInt32(kKeyColorFormat, colorFormat);
    mMeta->setInt32(kKeyWidth, width);
    mMeta->setInt32(kKeyHeight, height);
    mMeta->setInt32(kKeyStride, stride);
    mMeta->setInt32(kKeySliceHeight, sliceHeight);

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    int32_t paddedFrameWidth, paddedFrameHeight;
    if (mCamera != 0) {
        // Since we may not honor the preview size that app has requested
        // It is a good idea to get the actual preview size and use it for video recording.
        paddedFrameWidth = atoi(params.get("padded-width"));
        paddedFrameHeight = atoi(params.get("padded-height"));
        if (paddedFrameWidth < 0 || paddedFrameHeight < 0) {
            LOGE("Failed to get camera(%p) preview size", mCamera.get());
        }
        LOGV("CameraSource() : padded WxH=%dx%d", paddedFrameWidth, paddedFrameHeight);
    }
    else
    {
        LOGE("mCamera is NULL");
        paddedFrameWidth = width;
        paddedFrameHeight = height;
    }

    mMeta->setInt32(kKeyPaddedWidth, paddedFrameWidth);
    mMeta->setInt32(kKeyPaddedHeight, paddedFrameHeight);

    int32_t  mS3DCamera = false;
    if (mCamera != 0) {

        if(params.get("s3d-supported")!= NULL && CameraParameters::TRUE != NULL)
            mS3DCamera = strcmp(params.get("s3d-supported"), CameraParameters::TRUE) == 0;

        if(mS3DCamera)
        {
            const char *seiEncodingTypeStr = params.get(TICameraParameters::KEY_SEI_ENCODING_TYPE);
            CHECK(seiEncodingTypeStr != NULL);
            int32_t seiEncodingType = getSEIEncodingType(seiEncodingTypeStr);
            mMeta->setInt32(kKeySEIEncodingType, seiEncodingType);

            const char *frameLayoutStr = params.get(TICameraParameters::KEY_S3D_FRAME_LAYOUT);
            CHECK(frameLayoutStr != NULL);
            mMeta->setCString(kKeyFrameLayout, frameLayoutStr);
        }

    }
    else
    {
        LOGE("mCamera is NULL");
        mS3DCamera = false;
    }
    mMeta->setInt32(kKeyS3dSupported, mS3DCamera);
#endif
}

CameraSource::~CameraSource() {
    if (mStarted) {
        stop();
    }
}

status_t CameraSource::start(MetaData *meta) {
    CHECK(!mStarted);

    char value[PROPERTY_VALUE_MAX];
    if (property_get("media.stagefright.record-stats", value, NULL)
        && (!strcmp(value, "1") || !strcasecmp(value, "true"))) {
        mCollectStats = true;
    }

    mStartTimeUs = 0;
    int64_t startTimeUs;
    if (meta && meta->findInt64(kKeyTime, &startTimeUs)) {
        mStartTimeUs = startTimeUs;
    }

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->setListener(new CameraSourceListener(this));
    CHECK_EQ(OK, mCamera->startRecording());
    IPCThreadState::self()->restoreCallingIdentity(token);

    mStarted = true;
    return OK;
}

status_t CameraSource::stop() {
    LOGV("stop");
    Mutex::Autolock autoLock(mLock);
    mStarted = false;
    mFrameAvailableCondition.signal();

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->setListener(NULL);
    mCamera->stopRecording();
    releaseQueuedFrames();
    while (!mFramesBeingEncoded.empty()) {
        LOGI("Waiting for outstanding frames being encoded: %d",
                mFramesBeingEncoded.size());
        mFrameCompleteCondition.wait(mLock);
    }
    mCamera = NULL;
    IPCThreadState::self()->restoreCallingIdentity(token);

    if (mCollectStats) {
        LOGI("Frames received/encoded/dropped: %d/%d/%d in %lld us",
                mNumFramesReceived, mNumFramesEncoded, mNumFramesDropped,
                mLastFrameTimestampUs - mFirstFrameTimeUs);
    }

    CHECK_EQ(mNumFramesReceived, mNumFramesEncoded + mNumFramesDropped);
    return OK;
}

void CameraSource::releaseQueuedFrames() {
    List<sp<IMemory> >::iterator it;
    while (!mFramesReceived.empty()) {
        it = mFramesReceived.begin();
        mCamera->releaseRecordingFrame(*it);
        mFramesReceived.erase(it);
        ++mNumFramesDropped;
    }
}

sp<MetaData> CameraSource::getFormat() {
    return mMeta;
}

void CameraSource::releaseOneRecordingFrame(const sp<IMemory>& frame) {
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->releaseRecordingFrame(frame);
    IPCThreadState::self()->restoreCallingIdentity(token);
}

void CameraSource::signalBufferReturned(MediaBuffer *buffer) {
    LOGV("signalBufferReturned: %p", buffer->data());
    Mutex::Autolock autoLock(mLock);
    for (List<sp<IMemory> >::iterator it = mFramesBeingEncoded.begin();
         it != mFramesBeingEncoded.end(); ++it) {
        if ((*it)->pointer() ==  buffer->data()) {

            releaseOneRecordingFrame((*it));
            mFramesBeingEncoded.erase(it);
            ++mNumFramesEncoded;
            buffer->setObserver(0);
            buffer->release();
            mFrameCompleteCondition.signal();
            return;
        }
    }
    CHECK_EQ(0, "signalBufferReturned: bogus buffer");
}

status_t CameraSource::read(
        MediaBuffer **buffer, const ReadOptions *options) {
    LOGV("read");

    *buffer = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        return ERROR_UNSUPPORTED;
    }

    sp<IMemory> frame;
    int64_t frameTime;

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
    uint32_t frameOffset;
#endif
    {
        Mutex::Autolock autoLock(mLock);
        while (mStarted) {
            while(mFramesReceived.empty()) {
                if (mNumFramesReceived == 0) {
                    /*
                     * It's perfectly normal that we don't receive frames for quite some
                     * time at record start, so don't use a timeout in that case.
                     */
                    mFrameAvailableCondition.wait(mLock);
                } else {
                    /*
                     * Don't wait indefinitely for camera frames, buggy HALs may
                     * fail to provide them in a timely manner under some conditions.
                     */
                    status_t err = mFrameAvailableCondition.waitRelative(mLock, 250000000);
                    if (err) {
                        return err;
                    }
                }
            }

            if (!mStarted) {
                return OK;
            }

            frame = *mFramesReceived.begin();
            mFramesReceived.erase(mFramesReceived.begin());

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
            frameOffset = *mFrameOffset.begin();
            mFrameOffset.erase(mFrameOffset.begin());
#endif
            frameTime = *mFrameTimes.begin();
            mFrameTimes.erase(mFrameTimes.begin());
            int64_t skipTimeUs;
            if (!options || !options->getSkipFrame(&skipTimeUs)) {
                skipTimeUs = frameTime;
            }
            if (skipTimeUs > frameTime) {
                LOGV("skipTimeUs: %lld us > frameTime: %lld us",
                    skipTimeUs, frameTime);
                releaseOneRecordingFrame(frame);
                ++mNumFramesDropped;
                // Safeguard against the abuse of the kSkipFrame_Option.
                if (skipTimeUs - frameTime >= 1E6) {
                    LOGE("Frame skipping requested is way too long: %lld us",
                        skipTimeUs - frameTime);
                    return UNKNOWN_ERROR;
                }
            } else {
                mFramesBeingEncoded.push_back(frame);
                *buffer = new MediaBuffer(frame->pointer(), frame->size());
                (*buffer)->setObserver(this);
                (*buffer)->add_ref();
                (*buffer)->meta_data()->setInt64(kKeyTime, frameTime);

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
                (*buffer)->meta_data()->setInt32(kKeyOffset, frameOffset);
#endif
                return OK;
            }
        }
    }
    return OK;
}

#ifdef OMAP_ENHANCEMENT
void CameraSource::dataCallbackTimestamp(int64_t timestampUs,
        int32_t msgType, const sp<IMemory> &data,
        uint32_t offset, uint32_t stride)
#else
void CameraSource::dataCallbackTimestamp(int64_t timestampUs,
        int32_t msgType, const sp<IMemory> &data)
#endif
{
    LOGV("dataCallbackTimestamp: timestamp %lld us", timestampUs);
    Mutex::Autolock autoLock(mLock);
    if (!mStarted) {
        releaseOneRecordingFrame(data);
        ++mNumFramesReceived;
        ++mNumFramesDropped;
        return;
    }

    if (mNumFramesReceived > 0 &&
        timestampUs - mLastFrameTimestampUs > mGlitchDurationThresholdUs) {
        if (mNumGlitches % 10 == 0) {  // Don't spam the log
            LOGW("Long delay detected in video recording");
        }
        ++mNumGlitches;
    }

    mLastFrameTimestampUs = timestampUs;
    if (mNumFramesReceived == 0) {
        mFirstFrameTimeUs = timestampUs;
        // Initial delay
        if (mStartTimeUs > 0) {
            if (timestampUs < mStartTimeUs) {
                // Frame was captured before recording was started
                // Drop it without updating the statistical data.
                releaseOneRecordingFrame(data);
                return;
            }
            mStartTimeUs = timestampUs - mStartTimeUs;
        }
    }
    ++mNumFramesReceived;

    mFramesReceived.push_back(data);
    int64_t timeUs = mStartTimeUs + (timestampUs - mFirstFrameTimeUs);
    mFrameTimes.push_back(timeUs);
    LOGV("initial delay: %lld, current time stamp: %lld",
        mStartTimeUs, timeUs);

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
    mFrameOffset.push_back(offset);
#endif

    mFrameAvailableCondition.signal();
}

#ifdef USE_GETBUFFERINFO
status_t CameraSource::getBufferInfo(sp<IMemory>& Frame, size_t *alignedSize)
{
    return mCamera->getBufferInfo(Frame, alignedSize);
}
#endif

}  // namespace android
