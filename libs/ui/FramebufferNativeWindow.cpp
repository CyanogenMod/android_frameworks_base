/* 
**
** Copyright 2007 The Android Open Source Project
**
** Licensed under the Apache License Version 2.0(the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing software 
** distributed under the License is distributed on an "AS IS" BASIS 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#define LOG_TAG "FramebufferNativeWindow"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <utils/threads.h>
#include <utils/RefBase.h>

#include <ui/Rect.h>
#include <ui/FramebufferNativeWindow.h>
#include <ui/GraphicLog.h>

#include <EGL/egl.h>

#include <pixelflinger/format.h>
#include <pixelflinger/pixelflinger.h>

#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#include <private/ui/android_natives_priv.h>

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------

class NativeBuffer 
    : public EGLNativeBase<
        android_native_buffer_t, 
        NativeBuffer, 
        LightRefBase<NativeBuffer> >
{
public:
    NativeBuffer(int w, int h, int f, int u) : BASE() {
        android_native_buffer_t::width  = w;
        android_native_buffer_t::height = h;
        android_native_buffer_t::format = f;
        android_native_buffer_t::usage  = u;
    }
private:
    friend class LightRefBase<NativeBuffer>;    
    ~NativeBuffer() { }; // this class cannot be overloaded
};

/*
 * This implements the (main) framebuffer management. This class is used
 * mostly by SurfaceFlinger, but also by command line GL application.
 * 
 * In fact this is an implementation of ANativeWindow on top of
 * the framebuffer.
 * 
 * This implementation is able to manage any number of buffers,
 * defined by NUM_FRAME_BUFFERS (currently set to 2: front
 * and back buffer)
 *
 */

FramebufferNativeWindow::FramebufferNativeWindow()
    : BASE(), fbDev(0), grDev(0), mUpdateOnDemand(false)
{
    hw_module_t const* module;
    if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &module) == 0) {
        int stride;
        int err;
#ifdef OMAP_ENHANCEMENT
        int i;
#endif
        err = framebuffer_open(module, &fbDev);
        LOGE_IF(err, "couldn't open framebuffer HAL (%s)", strerror(-err));
        
        err = gralloc_open(module, &grDev);
        LOGE_IF(err, "couldn't open gralloc HAL (%s)", strerror(-err));

        // bail out if we can't initialize the modules
        if (!fbDev || !grDev)
            return;
        
        mUpdateOnDemand = (fbDev->setUpdateRect != 0);
        
        // initialize the buffer FIFO
#ifdef OMAP_ENHANCEMENT
        mNumBuffers = NUM_FRAME_BUFFERS;
        mNumFreeBuffers = NUM_FRAME_BUFFERS;
#else
        mNumBuffers = 2;
        mNumFreeBuffers = 2;
#endif
        mBufferHead = mNumBuffers-1;
#ifdef OMAP_ENHANCEMENT
        for(i = 0; i < NUM_FRAME_BUFFERS; i++){
            buffers[i] = new NativeBuffer(
                    fbDev->width, fbDev->height, fbDev->format, GRALLOC_USAGE_HW_FB);
        }

        for(i = 0; i < NUM_FRAME_BUFFERS; i++){
            err = grDev->alloc(grDev,
                    fbDev->width, fbDev->height, fbDev->format,
                    GRALLOC_USAGE_HW_FB, &buffers[i]->handle, &buffers[i]->stride);

            LOGE_IF(err, "fb buffer %d allocation failed w=%d, h=%d, err=%s",
                    i, fbDev->width, fbDev->height, strerror(-err));

            if(err){
                mNumBuffers = i;
                mNumFreeBuffers = i;
                mBufferHead = mNumBuffers-1;
                break;
            }
       }
#else
        buffers[0] = new NativeBuffer(
                fbDev->width, fbDev->height, fbDev->format, GRALLOC_USAGE_HW_FB);
        buffers[1] = new NativeBuffer(
                fbDev->width, fbDev->height, fbDev->format, GRALLOC_USAGE_HW_FB);
        
        err = grDev->alloc(grDev,
                fbDev->width, fbDev->height, fbDev->format, 
                GRALLOC_USAGE_HW_FB, &buffers[0]->handle, &buffers[0]->stride);

        LOGE_IF(err, "fb buffer 0 allocation failed w=%d, h=%d, err=%s",
                fbDev->width, fbDev->height, strerror(-err));

        err = grDev->alloc(grDev,
                fbDev->width, fbDev->height, fbDev->format, 
                GRALLOC_USAGE_HW_FB, &buffers[1]->handle, &buffers[1]->stride);

        LOGE_IF(err, "fb buffer 1 allocation failed w=%d, h=%d, err=%s",
                fbDev->width, fbDev->height, strerror(-err));
#endif
        const_cast<uint32_t&>(ANativeWindow::flags) = fbDev->flags; 
        const_cast<float&>(ANativeWindow::xdpi) = fbDev->xdpi;
        const_cast<float&>(ANativeWindow::ydpi) = fbDev->ydpi;
        const_cast<int&>(ANativeWindow::minSwapInterval) = 
            fbDev->minSwapInterval;
        const_cast<int&>(ANativeWindow::maxSwapInterval) = 
            fbDev->maxSwapInterval;
    } else {
        LOGE("Couldn't get gralloc module");
    }

    ANativeWindow::setSwapInterval = setSwapInterval;
    ANativeWindow::dequeueBuffer = dequeueBuffer;
    ANativeWindow::lockBuffer = lockBuffer;
    ANativeWindow::queueBuffer = queueBuffer;
    ANativeWindow::cancelBuffer = NULL;
    ANativeWindow::query = query;
    ANativeWindow::perform = perform;
    ANativeWindow::cancelBuffer = 0;
#ifdef OMAP_ENHANCEMENT
    LOGE("%d buffers flip-chain implementation enabled\n", mNumBuffers);
#endif
}

#ifdef OMAP_ENHANCEMENT
FramebufferNativeWindow::FramebufferNativeWindow(uint32_t idx)
    : BASE(), fbDev(0), grDev(0), mUpdateOnDemand(false)
{
    hw_module_t const* module;

    if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &module) == 0) {
        int stride;
        int err;
        int i;
        const size_t SIZE = 16;
        char fbname[SIZE];
        snprintf(fbname, SIZE, "fb%u", idx);

        err = framebuffer_open_by_name(module, &fbDev, fbname);
        LOGE_IF(err, "couldn't open framebuffer HAL (%s)", strerror(-err));

        err = gralloc_open(module, &grDev);
        LOGE_IF(err, "couldn't open gralloc HAL (%s)", strerror(-err));

        // bail out if we can't initialize the modules
        if (!fbDev || !grDev)
            return;

        mUpdateOnDemand = (fbDev->setUpdateRect != 0);

        // initialize the buffer FIFO
        mNumBuffers = NUM_FRAME_BUFFERS;
        mNumFreeBuffers = NUM_FRAME_BUFFERS;
        mBufferHead = mNumBuffers-1;

        for(i = 0; i < NUM_FRAME_BUFFERS; i++){
            buffers[i] = new NativeBuffer(
                    fbDev->width, fbDev->height, fbDev->format, GRALLOC_USAGE_HW_FB << idx);
        }

        for(i = 0; i < NUM_FRAME_BUFFERS; i++){
            err = grDev->alloc(grDev,
                    fbDev->width, fbDev->height, fbDev->format,
                    GRALLOC_USAGE_HW_FB << idx, &buffers[i]->handle, &buffers[i]->stride);

            LOGE_IF(err, "fb buffer %d allocation failed w=%d, h=%d, err=%s",
                    i, fbDev->width, fbDev->height, strerror(-err));

            if(err){
                mNumBuffers = i;
                mNumFreeBuffers = i;
                mBufferHead = mNumBuffers-1;
                break;
            }
       }

        const_cast<uint32_t&>(ANativeWindow::flags) = fbDev->flags;
        const_cast<float&>(ANativeWindow::xdpi) = fbDev->xdpi;
        const_cast<float&>(ANativeWindow::ydpi) = fbDev->ydpi;
        const_cast<int&>(ANativeWindow::minSwapInterval) =
            fbDev->minSwapInterval;
        const_cast<int&>(ANativeWindow::maxSwapInterval) =
            fbDev->maxSwapInterval;
    } else {
        LOGE("Couldn't get gralloc module");
    }

    ANativeWindow::setSwapInterval = setSwapInterval;
    ANativeWindow::dequeueBuffer = dequeueBuffer;
    ANativeWindow::lockBuffer = lockBuffer;
    ANativeWindow::queueBuffer = queueBuffer;
    ANativeWindow::query = query;
    ANativeWindow::perform = perform;

    LOGE("%d buffers flip-chain implementation enabled\n", mNumBuffers);
}
#endif

FramebufferNativeWindow::~FramebufferNativeWindow() 
{
#ifdef OMAP_ENHANCEMENT
    int i;

   if (grDev){
        for (i = 0; i < mNumBuffers; i++){
            if (buffers[i] != NULL)
                grDev->free(grDev, buffers[i]->handle);
        }
        gralloc_close(grDev);
    }
#else
    if (grDev) {
        if (buffers[0] != NULL)
            grDev->free(grDev, buffers[0]->handle);
        if (buffers[1] != NULL)
            grDev->free(grDev, buffers[1]->handle);
        gralloc_close(grDev);
    }
#endif
    if (fbDev) {
        framebuffer_close(fbDev);
    }
}

status_t FramebufferNativeWindow::setUpdateRectangle(const Rect& r) 
{
    if (!mUpdateOnDemand) {
        return INVALID_OPERATION;
    }
    return fbDev->setUpdateRect(fbDev, r.left, r.top, r.width(), r.height());
}

status_t FramebufferNativeWindow::compositionComplete()
{
    if (fbDev->compositionComplete) {
        return fbDev->compositionComplete(fbDev);
    }
    return INVALID_OPERATION;
}

int FramebufferNativeWindow::setSwapInterval(
        ANativeWindow* window, int interval) 
{
    framebuffer_device_t* fb = getSelf(window)->fbDev;
    return fb->setSwapInterval(fb, interval);
}

// only for debugging / logging
int FramebufferNativeWindow::getCurrentBufferIndex() const
{
    Mutex::Autolock _l(mutex);
    const int index = mCurrentBufferIndex;
    return index;
}

int FramebufferNativeWindow::dequeueBuffer(ANativeWindow* window, 
        android_native_buffer_t** buffer)
{
    FramebufferNativeWindow* self = getSelf(window);
    Mutex::Autolock _l(self->mutex);
    framebuffer_device_t* fb = self->fbDev;

    int index = self->mBufferHead++;
    if (self->mBufferHead >= self->mNumBuffers)
        self->mBufferHead = 0;

    GraphicLog& logger(GraphicLog::getInstance());
    logger.log(GraphicLog::SF_FB_DEQUEUE_BEFORE, index);

    // wait for a free buffer
    while (!self->mNumFreeBuffers) {
        self->mCondition.wait(self->mutex);
    }
    // get this buffer
    self->mNumFreeBuffers--;
    self->mCurrentBufferIndex = index;

    *buffer = self->buffers[index].get();

    logger.log(GraphicLog::SF_FB_DEQUEUE_AFTER, index);
    return 0;
}

int FramebufferNativeWindow::lockBuffer(ANativeWindow* window, 
        android_native_buffer_t* buffer)
{
    FramebufferNativeWindow* self = getSelf(window);
    Mutex::Autolock _l(self->mutex);

    const int index = self->mCurrentBufferIndex;
    GraphicLog& logger(GraphicLog::getInstance());
    logger.log(GraphicLog::SF_FB_LOCK_BEFORE, index);

    // wait that the buffer we're locking is not front anymore
    while (self->front == buffer) {
        self->mCondition.wait(self->mutex);
    }

    logger.log(GraphicLog::SF_FB_LOCK_AFTER, index);

    return NO_ERROR;
}

int FramebufferNativeWindow::queueBuffer(ANativeWindow* window, 
        android_native_buffer_t* buffer)
{
    FramebufferNativeWindow* self = getSelf(window);
    Mutex::Autolock _l(self->mutex);
    framebuffer_device_t* fb = self->fbDev;
    buffer_handle_t handle = static_cast<NativeBuffer*>(buffer)->handle;

    const int index = self->mCurrentBufferIndex;
    GraphicLog& logger(GraphicLog::getInstance());
    logger.log(GraphicLog::SF_FB_POST_BEFORE, index);

    int res = fb->post(fb, handle);

    logger.log(GraphicLog::SF_FB_POST_AFTER, index);

    self->front = static_cast<NativeBuffer*>(buffer);
    self->mNumFreeBuffers++;
    self->mCondition.broadcast();
    return res;
}

int FramebufferNativeWindow::query(ANativeWindow* window,
        int what, int* value) 
{
    FramebufferNativeWindow* self = getSelf(window);
    Mutex::Autolock _l(self->mutex);
    framebuffer_device_t* fb = self->fbDev;
    switch (what) {
        case NATIVE_WINDOW_WIDTH:
            *value = fb->width;
            return NO_ERROR;
        case NATIVE_WINDOW_HEIGHT:
            *value = fb->height;
            return NO_ERROR;
        case NATIVE_WINDOW_FORMAT:
            *value = fb->format;
            return NO_ERROR;
    }
    *value = 0;
    return BAD_VALUE;
}

int FramebufferNativeWindow::perform(ANativeWindow* window,
        int operation, ...)
{
    switch (operation) {
        case NATIVE_WINDOW_SET_USAGE:
        case NATIVE_WINDOW_CONNECT:
        case NATIVE_WINDOW_DISCONNECT:
            break;
        default:
            return NAME_NOT_FOUND;
    }
    return NO_ERROR;
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------

using namespace android;

#ifdef OMAP_ENHANCEMENT
EGLNativeWindowType android_createDisplaySurfaceOnFB(uint32_t fb_idx)
{
    FramebufferNativeWindow* w;
    w = new FramebufferNativeWindow(fb_idx);
    if (w->getDevice() == NULL) {
        // get a ref so it can be destroyed when we exit this block
        sp<FramebufferNativeWindow> ref(w);
        return NULL;
    }
    return (EGLNativeWindowType)w;
}
#endif

EGLNativeWindowType android_createDisplaySurface(void)
{
    FramebufferNativeWindow* w;
    w = new FramebufferNativeWindow();
    if (w->getDevice() == NULL) {
        // get a ref so it can be destroyed when we exit this block
        sp<FramebufferNativeWindow> ref(w);
        return NULL;
    }
    return (EGLNativeWindowType)w;
}
