/*
**
** Copyright (C) 2010 SpectraCore Technologies
** Author : Venkat Raju
** Email  : codredruids@spectracoretech.com
** Initial Code : Based on a code from http://code.google.com/p/android-m912/downloads/detail?name=v4l2_camera_v2.patch
**
** Copyright (C) 2009 0xlab.org - http://0xlab.org/
**
** Copyright 2008, The Android Open Source Project
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

#define LOG_TAG "CameraHardware"
#include <utils/Log.h>

#include "CameraHardware.h"
#include "converter.h"
#include <fcntl.h>
#include <sys/mman.h>

#define VIDEO_DEVICE        "/dev/video0"
#define MIN_WIDTH           640

#define MIN_HEIGHT          480
#define PIXEL_FORMAT        V4L2_PIX_FMT_YUYV

namespace android {

wp<CameraHardwareInterface> CameraHardware::singleton;

CameraHardware::CameraHardware()
                  : mParameters(),
                    mHeap(0),
                    mPreviewHeap(0),
                    mCamera(0),
                    mPreviewFrameSize(0),
                    mNotifyCb(0),
                    mDataCb(0),
                    mDataCbTimestamp(0),
                    mCallbackCookie(0),
                    mMsgEnabled(0),
                    mCurrentPreviewFrame(0),
                    previewStopped(true),
                    nQueued(0),
                    nDequeued(0)
{
    initDefaultParameters();
}

void CameraHardware::initDefaultParameters()
{
    CameraParameters p;

    p.setPreviewSize(MIN_WIDTH, MIN_HEIGHT);
    p.setPreviewFrameRate(30);
    p.setPreviewFormat("yuv422sp");

    p.setPictureSize(MIN_WIDTH, MIN_HEIGHT);
    p.setPictureFormat("jpeg");

    p.set("jpeg-quality", "100"); // maximum quality
    p.set("picture-size-values", "1600x1200,1024x768,640x480,352x288,320x240");

    if (setParameters(p) != NO_ERROR) {
        LOGE("Failed to set default parameters?!");
    }
}

CameraHardware::~CameraHardware()
{
    delete mCamera;
    mCamera = 0;
    singleton.clear();
}

sp<IMemoryHeap> CameraHardware::getPreviewHeap() const
{
    LOGE("return Preview Heap");
    return mPreviewHeap;
}

sp<IMemoryHeap> CameraHardware::getRawHeap() const
{
    LOGE("return Raw Heap");
    return NULL;
}

void CameraHardware::setCallbacks(notify_callback notify_cb,
                                  data_callback data_cb,
                                  data_callback_timestamp data_cb_timestamp,
                                  void* user)
{
    Mutex::Autolock lock(mLock);
    mNotifyCb = notify_cb;
    mDataCb = data_cb;
    mDataCbTimestamp = data_cb_timestamp;
    mCallbackCookie = user;
}

void CameraHardware::enableMsgType(int32_t msgType)
{
    Mutex::Autolock lock(mLock);
    mMsgEnabled |= msgType;
}

void CameraHardware::disableMsgType(int32_t msgType)
{
    Mutex::Autolock lock(mLock);
    mMsgEnabled &= ~msgType;
}

bool CameraHardware::msgTypeEnabled(int32_t msgType)
{
    Mutex::Autolock lock(mLock);
    return (mMsgEnabled & msgType);
}

// ---------------------------------------------------------------------------

int CameraHardware::previewThread()
{
    Mutex::Autolock lock(mPreviewLock);
    if (!previewStopped) {
        char *rawFramePointer = mCamera->GrabRawFrame();

        mRecordingLock.lock();
        if (mMsgEnabled & CAMERA_MSG_VIDEO_FRAME) {
            yuyv422_to_yuv420sp((unsigned char *)rawFramePointer,
                                (unsigned char *)mRecordingHeap->getBase(),
                                MIN_WIDTH, MIN_HEIGHT);

            mDataCb(CAMERA_MSG_VIDEO_FRAME, mRecordingBuffer, mCallbackCookie);
            mDataCbTimestamp(systemTime(), CAMERA_MSG_VIDEO_FRAME, mRecordingBuffer, mCallbackCookie);
        }
        mRecordingLock.unlock();

        if (mMsgEnabled & CAMERA_MSG_PREVIEW_FRAME) {
            mCamera->convert((unsigned char *) rawFramePointer,
                             (unsigned char *) mPreviewHeap->getBase(),
                             MIN_WIDTH, MIN_HEIGHT);

            yuyv422_to_yuv420sp((unsigned char *)rawFramePointer,
                              (unsigned char *)mHeap->getBase(),
                              MIN_WIDTH, MIN_HEIGHT);
            mDataCb(CAMERA_MSG_PREVIEW_FRAME, mBuffer, mCallbackCookie);
        }

        mCamera->ProcessRawFrameDone();

    }

    return NO_ERROR;
}

status_t CameraHardware::startPreview()
{
    int width, height;
    int ret;
    if(!mCamera) {
        delete mCamera;
        mCamera = new V4L2Camera();
    }

    Mutex::Autolock lock(mPreviewLock);
    if (mPreviewThread != 0) {
        // already running
        return INVALID_OPERATION;
    }

    if (mCamera->Open(VIDEO_DEVICE, MIN_WIDTH, MIN_HEIGHT, PIXEL_FORMAT) < 0) {
        LOGE("startPreview failed: cannot open device.");
        return UNKNOWN_ERROR;
    }

    mPreviewFrameSize = MIN_WIDTH * MIN_HEIGHT * 2;

    mHeap = new MemoryHeapBase(mPreviewFrameSize);
    mBuffer = new MemoryBase(mHeap, 0, mPreviewFrameSize);

    mPreviewHeap = new MemoryHeapBase(mPreviewFrameSize);
    mPreviewBuffer = new MemoryBase(mPreviewHeap, 0, mPreviewFrameSize);

    mRecordingHeap = new MemoryHeapBase(mPreviewFrameSize);
    mRecordingBuffer = new MemoryBase(mRecordingHeap, 0, mPreviewFrameSize);

    ret = mCamera->Init();
    if (ret) {
        LOGE("Camera Init failed: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    ret = mCamera->StartStreaming();
    if (ret) {
        LOGE("Camera StartStreaming failed: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    previewStopped = false;

    mPreviewThread = new PreviewThread(this);

    return NO_ERROR;
}

void CameraHardware::stopPreview()
{
    sp<PreviewThread> previewThread;

    { // scope for the lock
        Mutex::Autolock lock(mPreviewLock);
        previewStopped = true;
    }

    if (mPreviewThread != 0) {
        mCamera->Uninit();
        mCamera->StopStreaming();
        mCamera->Close();
    }

    {
        Mutex::Autolock lock(mPreviewLock);
        previewThread = mPreviewThread;
    }

    // don't hold the lock while waiting for the thread to quit
    if (previewThread != 0) {
        previewThread->requestExitAndWait();
    }

    Mutex::Autolock lock(mPreviewLock);
    mPreviewThread.clear();
}

bool CameraHardware::previewEnabled()
{
    return mPreviewThread != 0;
}

status_t CameraHardware::startRecording()
{
    LOGE("startRecording");
    mRecordingLock.lock();
    mRecordingEnabled = true;
    mRecordingLock.unlock();
    return NO_ERROR;

}

void CameraHardware::stopRecording()
{
    LOGE("stopRecording");
    mRecordingLock.lock();
    mRecordingEnabled = false;
    mRecordingLock.unlock();
}

bool CameraHardware::recordingEnabled()
{
    LOGE("recordingEnabled");
    return mRecordingEnabled == true;
}

void CameraHardware::releaseRecordingFrame(const sp<IMemory>& mem)
{
    LOGE("releaseRecordingFrame");
    return;
}

// ---------------------------------------------------------------------------

int CameraHardware::beginAutoFocusThread(void *cookie)
{
    LOGE("beginAutoFocusThread");
    CameraHardware *c = (CameraHardware *)cookie;
    return c->autoFocusThread();
}

int CameraHardware::autoFocusThread()
{
    if (mMsgEnabled & CAMERA_MSG_FOCUS)
        mNotifyCb(CAMERA_MSG_FOCUS, true, 0, mCallbackCookie);
    return NO_ERROR;
}

status_t CameraHardware::autoFocus()
{
    Mutex::Autolock lock(mLock);
    if (createThread(beginAutoFocusThread, this) == false)
        return UNKNOWN_ERROR;
    return NO_ERROR;
}

status_t CameraHardware::cancelAutoFocus()
{
    return NO_ERROR;
}

/*static*/ int CameraHardware::beginPictureThread(void *cookie)
{
    LOGE("begin Picture Thread");
    CameraHardware *c = (CameraHardware *)cookie;
    return c->pictureThread();
}

int CameraHardware::pictureThread()
{
    LOGE("Picture Thread");
    if (mMsgEnabled & CAMERA_MSG_SHUTTER) {
		// TODO: Don't notify SHUTTER for now, avoid making camera service
		// register preview surface to picture size.
	}

    // Just as stopPreview function
    sp<PreviewThread> previewThread;
    {
        Mutex::Autolock lock(mLock);
        previewThread = mPreviewThread;
        previewStopped = true;
    }

    if(previewThread != 0) {
        previewThread->requestExitAndWait();
    }

    {
        Mutex::Autolock lock(mLock);
        mPreviewThread.clear();
    }

    if (mMsgEnabled & CAMERA_MSG_RAW_IMAGE) {
        LOGE("Take Picture RAW IMAGE");
		// TODO: post a RawBuffer may be needed.
    }

    if (mMsgEnabled & CAMERA_MSG_COMPRESSED_IMAGE) {
        LOGE("Take Picture COMPRESSED IMAGE");
        mDataCb(CAMERA_MSG_COMPRESSED_IMAGE, mCamera->GrabJpegFrame(), mCallbackCookie);
    }

    previewStopped = false;
    LOGE("%s: preview started", __FUNCTION__);
    mPreviewThread = new PreviewThread(this);

    return NO_ERROR;
}

status_t CameraHardware::takePicture()
{
    pictureThread();
    return NO_ERROR;
}

status_t CameraHardware::cancelPicture()
{
    return NO_ERROR;
}

status_t CameraHardware::dump(int fd, const Vector<String16>& args) const
{
    return NO_ERROR;
}

status_t CameraHardware::setParameters(const CameraParameters& params)
{
    Mutex::Autolock lock(mLock);

    if (strcmp(params.getPreviewFormat(), "yuv422sp") != 0) {
        LOGE("Only yuv422sp preview is supported");
        return -1;
    }

    if (strcmp(params.getPictureFormat(), "jpeg") != 0) {
        LOGE("Only jpeg still pictures are supported");
        return -1;
    }

    mParameters = params;

    return NO_ERROR;
}

CameraParameters CameraHardware::getParameters() const
{
    CameraParameters params;

    {
        Mutex::Autolock lock(mLock);
        params = mParameters;
    }

    return params;
}

status_t CameraHardware::sendCommand(int32_t command, int32_t arg1,
                                     int32_t arg2)
{
    return BAD_VALUE;
}

void CameraHardware::release()
{
    close(camera_device);
}

sp<CameraHardwareInterface> CameraHardware::createInstance()
{
    if (singleton != 0) {
        sp<CameraHardwareInterface> hardware = singleton.promote();
        if (hardware != 0) {
            return hardware;
        }
    }
    sp<CameraHardwareInterface> hardware(new CameraHardware());
    singleton = hardware;
    return hardware;
}

extern "C" sp<CameraHardwareInterface> openCameraHardware()
{
    return CameraHardware::createInstance();
}

static CameraInfo sCameraInfo[] = {
    {
        CAMERA_FACING_BACK,
        90,  /* orientation */
    }
};

extern "C" int HAL_getNumberOfCameras()
{
    return sizeof(sCameraInfo) / sizeof(sCameraInfo[0]);
}

extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo *cameraInfo)
{
    memcpy(cameraInfo, &sCameraInfo[cameraId], sizeof(CameraInfo));
}


extern "C" sp<CameraHardwareInterface> HAL_openCameraHardware(int cameraId)
{
    return CameraHardware::createInstance();
}



}; // namespace android
