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

#ifndef ANDROID_HARDWARE_CAMERA_HARDWARE_H
#define ANDROID_HARDWARE_CAMERA_HARDWARE_H

#include <utils/threads.h>
#include <camera/CameraHardwareInterface.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <utils/threads.h>
#include <sys/ioctl.h>
extern "C" {
#include "jpeglib.h"
}
#include "V4L2Camera.h"

namespace android {

class CameraHardware : public CameraHardwareInterface {
public:
    virtual sp<IMemoryHeap> getPreviewHeap() const;
    virtual sp<IMemoryHeap> getRawHeap() const;

    virtual void setCallbacks(notify_callback notify_cb,
                              data_callback data_cb,
                              data_callback_timestamp data_cb_timestamp,
                              void* user);
    virtual void        enableMsgType(int32_t msgType);
    virtual void        disableMsgType(int32_t msgType);
    virtual bool        msgTypeEnabled(int32_t msgType);

    virtual status_t    startPreview();
    virtual void        stopPreview();
    virtual bool        previewEnabled();

    virtual status_t    startRecording();
    virtual void        stopRecording();
    virtual bool        recordingEnabled();
    virtual void        releaseRecordingFrame(const sp<IMemory>& mem);

    virtual status_t    autoFocus();
    virtual status_t    cancelAutoFocus();
    virtual status_t    takePicture();
    virtual status_t    cancelPicture();
    virtual status_t    dump(int fd, const Vector<String16>& args) const;
    virtual status_t    setParameters(const CameraParameters& params);
    virtual CameraParameters  getParameters() const;
    virtual status_t    sendCommand(int32_t command, int32_t arg1,
                                    int32_t arg2);
    virtual void release();

    static sp<CameraHardwareInterface> createInstance();

private:
                        CameraHardware();
    virtual             ~CameraHardware();

    static wp<CameraHardwareInterface> singleton;

    static const int kBufferCount = 4;

    class PreviewThread : public Thread {
        CameraHardware* mHardware;
    public:
        PreviewThread(CameraHardware* hw) :
#ifdef SINGLE_PROCESS
            // In single process mode this thread needs to be a java thread,
            // since we won't be calling through the binder.
            Thread(true),
#else
            Thread(false),
#endif
              mHardware(hw) { }
        virtual void onFirstRef() {
            run("CameraPreviewThread", PRIORITY_URGENT_DISPLAY);
        }
        virtual bool threadLoop() {
            mHardware->previewThread();
            // loop until we need to quit
            return true;
        }
    };

    void initDefaultParameters();
    bool initHeapLocked();

    int previewThread();

    static int beginAutoFocusThread(void *cookie);
    int autoFocusThread();

    static int beginPictureThread(void *cookie);
    int pictureThread();

    mutable Mutex       mLock;
    mutable Mutex       mPreviewLock;    // hareware v4l2 operation lock
    Mutex               mRecordingLock;

    CameraParameters    mParameters;

    sp<MemoryHeapBase>	mHeap;
    sp<MemoryBase>      mBuffer;
    sp<MemoryBase>      mPreviewBuffer;
    sp<MemoryHeapBase>  mRecordingHeap;
    sp<MemoryBase>      mRecordingBuffer;
    sp<MemoryBase>      mBuffers[kBufferCount];

    sp<MemoryHeapBase>  mPreviewHeap;

    bool                mPreviewRunning;
    int                 mPreviewFrameSize;

    // protected by mLock
    sp<PreviewThread>   mPreviewThread;

    notify_callback    mNotifyCb;
    data_callback      mDataCb;
    data_callback_timestamp mDataCbTimestamp;
    void               *mCallbackCookie;

    int32_t             mMsgEnabled;

    // only used from PreviewThread
    int                 mCurrentPreviewFrame;

    void *				framebuffer;
    bool				previewStopped;
    bool                mRecordingEnabled;
    int					camera_device;
    void* 				mem[4];
    int					nQueued;
    int 				nDequeued;
    V4L2Camera			*mCamera;
};

}; // namespace android

#endif
