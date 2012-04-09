/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_OVERLAY_H
#define ANDROID_OVERLAY_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <ui/PixelFormat.h>

typedef void (*overlay_set_fd_hook)(void *data,
        int fd);
typedef void (*overlay_set_crop_hook)(void *data,
        uint32_t x, uint32_t y, uint32_t w, uint32_t h);
typedef void (*overlay_queue_buffer_hook)(void *data,
        void* buffer);

namespace android {

class IMemory;
class IMemoryHeap;

// ----------------------------------------------------------------------------

class Overlay : public virtual RefBase
{
public:
    Overlay(overlay_set_fd_hook set_fd,
            overlay_set_crop_hook set_crop,
            overlay_queue_buffer_hook queue_buffer,
            void* data);

    /* destroys this overlay */
    void destroy();
    
    /* get the HAL handle for this overlay */
    void* getHandleRef() const;

    /* blocks until an overlay buffer is available and return that buffer. */
    status_t dequeueBuffer(void** buffer);

    /* release the overlay buffer and post it */
    status_t queueBuffer(void* buffer);

    /* change the width and height of the overlay */
    status_t resizeInput(uint32_t width, uint32_t height);

    status_t setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h) ;

#ifdef OMAP_ENHANCEMENT
    status_t set_s3d_params(int32_t s3d_mode, uint32_t s3d_fmt, uint32_t s3d_order, uint32_t s3d_subsampling);
#endif

    status_t getCrop(uint32_t* x, uint32_t* y, uint32_t* w, uint32_t* h) ;

    /* set the buffer attributes */
    status_t setParameter(int param, int value);
    status_t setFd(int fd);

    /* returns the address of a given buffer if supported, NULL otherwise. */
    void* getBufferAddress(void* buffer);

    /* get physical informations about the overlay */
    uint32_t getWidth() const;
    uint32_t getHeight() const;
    int32_t getFormat() const;
    int32_t getWidthStride() const;
    int32_t getHeightStride() const;
    int32_t getBufferCount() const;
    status_t getStatus() const;
    
private:
    virtual ~Overlay();

    // C style hook
    overlay_set_fd_hook set_fd_hook;
    overlay_set_crop_hook set_crop_hook;
    overlay_queue_buffer_hook queue_buffer_hook;
    void* hook_data;

    status_t mStatus;
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_OVERLAY_H
