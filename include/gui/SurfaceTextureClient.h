/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef ANDROID_GUI_SURFACETEXTURECLIENT_H
#define ANDROID_GUI_SURFACETEXTURECLIENT_H

#include <gui/ISurfaceTexture.h>
#include <gui/SurfaceTexture.h>

#include <ui/egl/android_natives.h>
#include <ui/Region.h>

#include <utils/RefBase.h>
#include <utils/threads.h>

namespace android {

class Surface;

class SurfaceTextureClient
    : public EGLNativeBase<ANativeWindow, SurfaceTextureClient, RefBase>
{
public:
    SurfaceTextureClient(const sp<ISurfaceTexture>& surfaceTexture);

    sp<ISurfaceTexture> getISurfaceTexture() const;

protected:
    SurfaceTextureClient();
    virtual ~SurfaceTextureClient();
    void setISurfaceTexture(const sp<ISurfaceTexture>& surfaceTexture);

private:
    // can't be copied
    SurfaceTextureClient& operator = (const SurfaceTextureClient& rhs);
    SurfaceTextureClient(const SurfaceTextureClient& rhs);
    void init();

    // ANativeWindow hooks
    static int hook_cancelBuffer(ANativeWindow* window, ANativeWindowBuffer* buffer);
    static int hook_dequeueBuffer(ANativeWindow* window, ANativeWindowBuffer** buffer);
    static int hook_lockBuffer(ANativeWindow* window, ANativeWindowBuffer* buffer);
    static int hook_perform(ANativeWindow* window, int operation, ...);
    static int hook_query(const ANativeWindow* window, int what, int* value);
    static int hook_queueBuffer(ANativeWindow* window, ANativeWindowBuffer* buffer);
    static int hook_setSwapInterval(ANativeWindow* window, int interval);

    int dispatchConnect(va_list args);
    int dispatchDisconnect(va_list args);
    int dispatchSetBufferCount(va_list args);
    int dispatchSetBuffersGeometry(va_list args);
    int dispatchSetBuffersDimensions(va_list args);
    int dispatchSetBuffersFormat(va_list args);
    int dispatchSetScalingMode(va_list args);
    int dispatchSetBuffersTransform(va_list args);
    int dispatchSetBuffersTimestamp(va_list args);
    int dispatchSetCrop(va_list args);
    int dispatchSetUsage(va_list args);
    int dispatchLock(va_list args);
    int dispatchUnlockAndPost(va_list args);
#ifdef OMAP_ENHANCEMENT
    int dispatchSetBuffersLayout(va_list args);
#endif
#ifdef QCOM_HARDWARE
    int dispatchPerformQcomOperation(int operation, va_list args);
#endif

protected:
    virtual int cancelBuffer(ANativeWindowBuffer* buffer);
    virtual int dequeueBuffer(ANativeWindowBuffer** buffer);
    virtual int lockBuffer(ANativeWindowBuffer* buffer);
    virtual int perform(int operation, va_list args);
    virtual int query(int what, int* value) const;
    virtual int queueBuffer(ANativeWindowBuffer* buffer);
    virtual int setSwapInterval(int interval);

    virtual int connect(int api);
    virtual int disconnect(int api);
    virtual int setBufferCount(int bufferCount);
    virtual int setBuffersDimensions(int w, int h);
    virtual int setBuffersFormat(int format);
    virtual int setScalingMode(int mode);
    virtual int setBuffersTransform(int transform);
    virtual int setBuffersTimestamp(int64_t timestamp);
    virtual int setCrop(Rect const* rect);
    virtual int setUsage(uint32_t reqUsage);
    virtual int lock(ANativeWindow_Buffer* outBuffer, ARect* inOutDirtyBounds);
    virtual int unlockAndPost();
#ifdef OMAP_ENHANCEMENT
    virtual int setBuffersLayout(uint32_t layout);
#endif
#ifdef QCOM_HARDWARE
    virtual int performQcomOperation(int operation, int arg1, int arg2, int arg3);
#endif

    enum { MIN_UNDEQUEUED_BUFFERS = SurfaceTexture::MIN_UNDEQUEUED_BUFFERS };
    enum { NUM_BUFFER_SLOTS = SurfaceTexture::NUM_BUFFER_SLOTS };
    enum { DEFAULT_FORMAT = PIXEL_FORMAT_RGBA_8888 };

private:
    void freeAllBuffers();
    int getSlotFromBufferLocked(android_native_buffer_t* buffer) const;

    // mSurfaceTexture is the interface to the surface texture server. All
    // operations on the surface texture client ultimately translate into
    // interactions with the server using this interface.
    sp<ISurfaceTexture> mSurfaceTexture;

    // mSlots stores the buffers that have been allocated for each buffer slot.
    // It is initialized to null pointers, and gets filled in with the result of
    // ISurfaceTexture::requestBuffer when the client dequeues a buffer from a
    // slot that has not yet been used. The buffer allocated to a slot will also
    // be replaced if the requested buffer usage or geometry differs from that
    // of the buffer allocated to a slot.
    sp<GraphicBuffer> mSlots[NUM_BUFFER_SLOTS];

    // mReqWidth is the buffer width that will be requested at the next dequeue
    // operation. It is initialized to 1.
    uint32_t mReqWidth;

    // mReqHeight is the buffer height that will be requested at the next deuque
    // operation. It is initialized to 1.
    uint32_t mReqHeight;

    // mReqFormat is the buffer pixel format that will be requested at the next
    // deuque operation. It is initialized to PIXEL_FORMAT_RGBA_8888.
    uint32_t mReqFormat;

    // mReqUsage is the set of buffer usage flags that will be requested
    // at the next deuque operation. It is initialized to 0.
    uint32_t mReqUsage;

    // mTimestamp is the timestamp that will be used for the next buffer queue
    // operation. It defaults to NATIVE_WINDOW_TIMESTAMP_AUTO, which means that
    // a timestamp is auto-generated when queueBuffer is called.
    int64_t mTimestamp;

    // mDefaultWidth is default width of the window, regardless of the
    // native_window_set_buffers_dimensions call
    uint32_t mDefaultWidth;

    // mDefaultHeight is default width of the window, regardless of the
    // native_window_set_buffers_dimensions call
    uint32_t mDefaultHeight;

    // mTransformHint is the transform probably applied to buffers of this
    // window. this is only a hint, actual transform may differ.
    uint32_t mTransformHint;

    // mMutex is the mutex used to prevent concurrent access to the member
    // variables of SurfaceTexture objects. It must be locked whenever the
    // member variables are accessed.
    mutable Mutex mMutex;

    // must be used from the lock/unlock thread
    sp<GraphicBuffer>           mLockedBuffer;
    sp<GraphicBuffer>           mPostedBuffer;
#ifdef QCOM_HARDWARE
    mutable Region              mOldDirtyRegion[NUM_BUFFER_SLOTS];
#else
    mutable Region              mOldDirtyRegion;
#ifdef OMAP_ENHANCEMENT
    mutable Vector<Region>      mOldDirtyRegionHistory;
#endif
#endif
    bool                        mConnectedToCpu;

#ifdef QCOM_HARDWARE
    // mReqExtUsage is a flag set by app to mark a layer for display on
    // external panels only. Depending on the value of this flag mReqUsage
    // will be ORed with existing values.
    // Possible values GRALLOC_USAGE_EXTERNAL_ONLY and
    // GRALLOC_USAGE_EXTERNAL_BLOCK
    // It is initialized to 0
    uint32_t mReqExtUsage;
#endif
};

}; // namespace android

#endif  // ANDROID_GUI_SURFACETEXTURECLIENT_H
