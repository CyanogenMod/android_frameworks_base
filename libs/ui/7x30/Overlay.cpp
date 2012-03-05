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

#define LOG_TAG "Overlay"

#include <binder/IMemory.h>
#include <binder/Parcel.h>
#include <utils/Errors.h>
#include <binder/MemoryHeapBase.h>

#include <ui/7x30/Overlay.h>

namespace android {

int getBppFromOverlayFormat(const OverlayFormats format) {
    int bpp;
    switch(format) {
        case OVERLAY_FORMAT_RGBA8888:
            bpp=32;
            break;
        case OVERLAY_FORMAT_RGB565:
        case OVERLAY_FORMAT_YUV422I:
        case OVERLAY_FORMAT_YUV422SP:
            bpp = 16;
            break;
        case OVERLAY_FORMAT_YUV420SP:
        case OVERLAY_FORMAT_YUV420P:
            bpp = 12;
            break;
        default:
            bpp = 0;
    }
    return bpp;
}

OverlayFormats getOverlayFormatFromString(const char* name) {
    OverlayFormats rv = OVERLAY_FORMAT_UNKNOWN;
    if( strcmp(name, "yuv422sp") == 0 ) {
        rv = OVERLAY_FORMAT_YUV422SP;
    } else if( strcmp(name, "yuv420sp") == 0 ) {
        rv = OVERLAY_FORMAT_YUV420SP;
    } else if( strcmp(name, "yuv422i-yuyv") == 0 ) {
        rv = OVERLAY_FORMAT_YUV422I;
    } else if( strcmp(name, "yuv420p") == 0 ) {
        rv = OVERLAY_FORMAT_YUV420P;
    } else if( strcmp(name, "rgb565") == 0 ) {
        rv = OVERLAY_FORMAT_RGB565;
    } else if( strcmp(name, "rgba8888") == 0 ) {
        rv = OVERLAY_FORMAT_RGBA8888;
    }
    return rv;
}

Overlay::Overlay(overlay_set_fd_hook set_fd,
        overlay_set_crop_hook set_crop,
        overlay_queue_buffer_hook queue_buffer,
        void *data)
    : mStatus(NO_INIT)
{
    set_fd_hook = set_fd;
    set_crop_hook = set_crop;
    queue_buffer_hook = queue_buffer;
    hook_data = data;
    mStatus = NO_ERROR;
}

Overlay::~Overlay() {
}

status_t Overlay::dequeueBuffer(void** buffer)
{
    return mStatus;
}

status_t Overlay::queueBuffer(void* buffer)
{
    if (queue_buffer_hook)
        queue_buffer_hook(hook_data, buffer);
    return mStatus;
}

status_t Overlay::resizeInput(uint32_t width, uint32_t height)
{
    return mStatus;
}

status_t Overlay::setParameter(int param, int value)
{
    return mStatus;
}

status_t Overlay::setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h)
{
    if (set_crop_hook)
        set_crop_hook(hook_data, x, y, w, h);
    return mStatus;
}

#ifdef OMAP_ENHANCEMENT
    status_t Overlay::set_s3d_params(int32_t s3d_mode, uint32_t s3d_fmt, uint32_t s3d_order, uint32_t s3d_subsampling)
    {
        return mStatus;
        return mOverlayData->set_s3d_params(mOverlayData, s3d_mode, s3d_fmt, s3d_order, s3d_subsampling);
    }
#endif

status_t Overlay::getCrop(uint32_t* x, uint32_t* y, uint32_t* w, uint32_t* h)
{
    return mStatus;
}

status_t Overlay::setFd(int fd)
{
    if (set_fd_hook)
        set_fd_hook(hook_data, fd);
    return mStatus;
}

int32_t Overlay::getBufferCount() const
{
    return 0;
}

void* Overlay::getBufferAddress(void* buffer)
{
    return 0;
}

void Overlay::destroy() {  
}

status_t Overlay::getStatus() const {
    return mStatus;
}

void* Overlay::getHandleRef() const {
    return 0;
}

uint32_t Overlay::getWidth() const {
    return 0;
}

uint32_t Overlay::getHeight() const {
    return 0;
}

int32_t Overlay::getFormat() const {
    return 0;
}

int32_t Overlay::getWidthStride() const {
    return 0;
}

int32_t Overlay::getHeightStride() const {
    return 0;
}

// ----------------------------------------------------------------------------

}; // namespace android
