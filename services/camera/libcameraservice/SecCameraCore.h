/*
 * Copyright (C) 2012 The CyanogenMod Project
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

#ifndef SEC_CAMERA_CORE_H
#define SEC_CAMERA_CORE_H

#include "CameraHardwareInterface.h"

namespace android {

class SecCameraCoreManager : public CameraHardwareInterface
{
public:
                            SecCameraCoreManager(const char *name);
    virtual                 ~SecCameraCoreManager();

    virtual status_t setPreviewWindow(const sp<ANativeWindow>& buf) ;

    virtual status_t initialize(hw_module_t *module);

    virtual void setCallbacks(notify_callback notify_cb,
                              data_callback data_cb,
                              data_callback_timestamp data_cb_timestamp,
                              void* user);

    virtual void        enableMsgType(int32_t msgType);
    virtual void        disableMsgType(int32_t msgType);
    virtual int         msgTypeEnabled(int32_t msgType);

    virtual status_t    startPreview();
    virtual void        stopPreview();
    virtual int         previewEnabled();

    virtual status_t    storeMetaDataInBuffers(int enable);

    virtual status_t    startRecording();
    virtual void        stopRecording();
    virtual int         recordingEnabled();
    virtual void        releaseRecordingFrame(const sp<IMemory>& mem);

    virtual status_t    autoFocus();
    virtual status_t    cancelAutoFocus();
    virtual status_t    takePicture();
    virtual status_t    cancelPicture();

    virtual status_t    setParameters(const CameraParameters& params);
    virtual CameraParameters  getParameters() const;

    virtual status_t    sendCommand(int32_t cmd, int32_t arg1, int32_t arg2);

    virtual void        release();

    virtual status_t    dump(int fd, const Vector<String16>& args) const;

    void processNotifyCallback(int32_t msgType, int32_t ext1, int32_t ext2);
    void processDataCallback(int32_t msgType, const sp<IMemory> &dataPtr, camera_frame_metadata_t *metadata);
    void processDataCallbackTimestamp(nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr);

    sp<CameraHardwareInterface> getHardware();
};

};
#endif

