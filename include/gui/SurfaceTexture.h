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

#ifndef ANDROID_GUI_SURFACETEXTURE_H
#define ANDROID_GUI_SURFACETEXTURE_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <gui/ISurfaceTexture.h>

#include <ui/GraphicBuffer.h>

#include <utils/String8.h>
#include <utils/Vector.h>
#include <utils/threads.h>

#define ANDROID_GRAPHICS_SURFACETEXTURE_JNI_ID "mSurfaceTexture"

namespace android {
// ----------------------------------------------------------------------------

class IGraphicBufferAlloc;
class String8;

class SurfaceTexture : public BnSurfaceTexture {
public:
    enum { MIN_UNDEQUEUED_BUFFERS = 2 };
    enum {
        MIN_ASYNC_BUFFER_SLOTS = MIN_UNDEQUEUED_BUFFERS + 1,
        MIN_SYNC_BUFFER_SLOTS  = MIN_UNDEQUEUED_BUFFERS
    };
    enum { NUM_BUFFER_SLOTS = 32 };
    enum { NO_CONNECTED_API = 0 };

    struct FrameAvailableListener : public virtual RefBase {
        // onFrameAvailable() is called from queueBuffer() each time an
        // additional frame becomes available for consumption. This means that
        // frames that are queued while in asynchronous mode only trigger the
        // callback if no previous frames are pending. Frames queued while in
        // synchronous mode always trigger the callback.
        //
        // This is called without any lock held and can be called concurrently
        // by multiple threads.
        virtual void onFrameAvailable() = 0;
    };

    // SurfaceTexture constructs a new SurfaceTexture object. tex indicates the
    // name of the OpenGL ES texture to which images are to be streamed. This
    // texture name cannot be changed once the SurfaceTexture is created.
    // allowSynchronousMode specifies whether or not synchronous mode can be
    // enabled. texTarget specifies the OpenGL ES texture target to which the
    // texture will be bound in updateTexImage. useFenceSync specifies whether
    // fences should be used to synchronize access to buffers if that behavior
    // is enabled at compile-time.
    SurfaceTexture(GLuint tex, bool allowSynchronousMode = true,
            GLenum texTarget = GL_TEXTURE_EXTERNAL_OES, bool useFenceSync = true);

    virtual ~SurfaceTexture();

    // setBufferCount updates the number of available buffer slots.  After
    // calling this all buffer slots are both unallocated and owned by the
    // SurfaceTexture object (i.e. they are not owned by the client).
    virtual status_t setBufferCount(int bufferCount);

    virtual status_t requestBuffer(int slot, sp<GraphicBuffer>* buf);

    // dequeueBuffer gets the next buffer slot index for the client to use. If a
    // buffer slot is available then that slot index is written to the location
    // pointed to by the buf argument and a status of OK is returned.  If no
    // slot is available then a status of -EBUSY is returned and buf is
    // unmodified.
    // The width and height parameters must be no greater than the minimum of
    // GL_MAX_VIEWPORT_DIMS and GL_MAX_TEXTURE_SIZE (see: glGetIntegerv).
    // An error due to invalid dimensions might not be reported until
    // updateTexImage() is called.
    virtual status_t dequeueBuffer(int *buf, uint32_t width, uint32_t height,
            uint32_t format, uint32_t usage);

    // queueBuffer returns a filled buffer to the SurfaceTexture. In addition, a
    // timestamp must be provided for the buffer. The timestamp is in
    // nanoseconds, and must be monotonically increasing. Its other semantics
    // (zero point, etc) are client-dependent and should be documented by the
    // client.
    virtual status_t queueBuffer(int buf, int64_t timestamp,
            uint32_t* outWidth, uint32_t* outHeight, uint32_t* outTransform);
    virtual void cancelBuffer(int buf);
    virtual status_t setCrop(const Rect& reg);
    virtual status_t setTransform(uint32_t transform);
    virtual status_t setScalingMode(int mode);

    virtual int query(int what, int* value);

#ifdef QCOM_HARDWARE
    virtual int performQcomOperation(int operation, int arg1, int arg2, int arg3);
#endif

    // setSynchronousMode set whether dequeueBuffer is synchronous or
    // asynchronous. In synchronous mode, dequeueBuffer blocks until
    // a buffer is available, the currently bound buffer can be dequeued and
    // queued buffers will be retired in order.
    // The default mode is asynchronous.
    virtual status_t setSynchronousMode(bool enabled);

    // connect attempts to connect a client API to the SurfaceTexture.  This
    // must be called before any other ISurfaceTexture methods are called except
    // for getAllocator.
    //
    // This method will fail if the connect was previously called on the
    // SurfaceTexture and no corresponding disconnect call was made.
    virtual status_t connect(int api,
            uint32_t* outWidth, uint32_t* outHeight, uint32_t* outTransform);

    // disconnect attempts to disconnect a client API from the SurfaceTexture.
    // Calling this method will cause any subsequent calls to other
    // ISurfaceTexture methods to fail except for getAllocator and connect.
    // Successfully calling connect after this will allow the other methods to
    // succeed again.
    //
    // This method will fail if the the SurfaceTexture is not currently
    // connected to the specified client API.
    virtual status_t disconnect(int api);

    // updateTexImage sets the image contents of the target texture to that of
    // the most recently queued buffer.
    //
    // This call may only be made while the OpenGL ES context to which the
    // target texture belongs is bound to the calling thread.
    status_t updateTexImage();

    // setBufferCountServer set the buffer count. If the client has requested
    // a buffer count using setBufferCount, the server-buffer count will
    // take effect once the client sets the count back to zero.
    status_t setBufferCountServer(int bufferCount);

    // getTransformMatrix retrieves the 4x4 texture coordinate transform matrix
    // associated with the texture image set by the most recent call to
    // updateTexImage.
    //
    // This transform matrix maps 2D homogeneous texture coordinates of the form
    // (s, t, 0, 1) with s and t in the inclusive range [0, 1] to the texture
    // coordinate that should be used to sample that location from the texture.
    // Sampling the texture outside of the range of this transform is undefined.
    //
    // This transform is necessary to compensate for transforms that the stream
    // content producer may implicitly apply to the content. By forcing users of
    // a SurfaceTexture to apply this transform we avoid performing an extra
    // copy of the data that would be needed to hide the transform from the
    // user.
    //
    // The matrix is stored in column-major order so that it may be passed
    // directly to OpenGL ES via the glLoadMatrixf or glUniformMatrix4fv
    // functions.
    void getTransformMatrix(float mtx[16]);

    // getTimestamp retrieves the timestamp associated with the texture image
    // set by the most recent call to updateTexImage.
    //
    // The timestamp is in nanoseconds, and is monotonically increasing. Its
    // other semantics (zero point, etc) are source-dependent and should be
    // documented by the source.
    int64_t getTimestamp();

    // setFrameAvailableListener sets the listener object that will be notified
    // when a new frame becomes available.
    void setFrameAvailableListener(const sp<FrameAvailableListener>& listener);

    // getAllocator retrieves the binder object that must be referenced as long
    // as the GraphicBuffers dequeued from this SurfaceTexture are referenced.
    // Holding this binder reference prevents SurfaceFlinger from freeing the
    // buffers before the client is done with them.
    sp<IBinder> getAllocator();

    // setDefaultBufferSize is used to set the size of buffers returned by
    // requestBuffers when a with and height of zero is requested.
    // A call to setDefaultBufferSize() may trigger requestBuffers() to
    // be called from the client.
    // The width and height parameters must be no greater than the minimum of
    // GL_MAX_VIEWPORT_DIMS and GL_MAX_TEXTURE_SIZE (see: glGetIntegerv).
    // An error due to invalid dimensions might not be reported until
    // updateTexImage() is called.
    status_t setDefaultBufferSize(uint32_t width, uint32_t height);

    // getCurrentBuffer returns the buffer associated with the current image.
    sp<GraphicBuffer> getCurrentBuffer() const;

    // getCurrentTextureTarget returns the texture target of the current
    // texture as returned by updateTexImage().
    GLenum getCurrentTextureTarget() const;

    // getCurrentCrop returns the cropping rectangle of the current buffer
    Rect getCurrentCrop() const;

    // getCurrentTransform returns the transform of the current buffer
    uint32_t getCurrentTransform() const;

    // getCurrentScalingMode returns the scaling mode of the current buffer
    uint32_t getCurrentScalingMode() const;

    // isSynchronousMode returns whether the SurfaceTexture is currently in
    // synchronous mode.
    bool isSynchronousMode() const;

    // abandon frees all the buffers and puts the SurfaceTexture into the
    // 'abandoned' state.  Once put in this state the SurfaceTexture can never
    // leave it.  When in the 'abandoned' state, all methods of the
    // ISurfaceTexture interface will fail with the NO_INIT error.
    //
    // Note that while calling this method causes all the buffers to be freed
    // from the perspective of the the SurfaceTexture, if there are additional
    // references on the buffers (e.g. if a buffer is referenced by a client or
    // by OpenGL ES as a texture) then those buffer will remain allocated.
    void abandon();

    // set the name of the SurfaceTexture that will be used to identify it in
    // log messages.
    void setName(const String8& name);

    // dump our state in a String
    void dump(String8& result) const;
    void dump(String8& result, const char* prefix, char* buffer, size_t SIZE) const;

protected:

    // freeBufferLocked frees the resources (both GraphicBuffer and EGLImage)
    // for the given slot.
    void freeBufferLocked(int index);

    // freeAllBuffersLocked frees the resources (both GraphicBuffer and
    // EGLImage) for all slots.
    void freeAllBuffersLocked();

    // freeAllBuffersExceptHeadLocked frees the resources (both GraphicBuffer
    // and EGLImage) for all slots except the head of mQueue
    void freeAllBuffersExceptHeadLocked();

    // drainQueueLocked drains the buffer queue if we're in synchronous mode
    // returns immediately otherwise. return NO_INIT if SurfaceTexture
    // became abandoned or disconnected during this call.
    status_t drainQueueLocked();

    // drainQueueAndFreeBuffersLocked drains the buffer queue if we're in
    // synchronous mode and free all buffers. In asynchronous mode, all buffers
    // are freed except the current buffer.
    status_t drainQueueAndFreeBuffersLocked();

    static bool isExternalFormat(uint32_t format);

private:

    // createImage creates a new EGLImage from a GraphicBuffer.
    EGLImageKHR createImage(EGLDisplay dpy,
            const sp<GraphicBuffer>& graphicBuffer);

    status_t setBufferCountServerLocked(int bufferCount);

    // computeCurrentTransformMatrix computes the transform matrix for the
    // current texture.  It uses mCurrentTransform and the current GraphicBuffer
    // to compute this matrix and stores it in mCurrentTransformMatrix.
    void computeCurrentTransformMatrix();

    enum { INVALID_BUFFER_SLOT = -1 };

    struct BufferSlot {

        BufferSlot()
            : mEglImage(EGL_NO_IMAGE_KHR),
              mEglDisplay(EGL_NO_DISPLAY),
              mBufferState(BufferSlot::FREE),
              mRequestBufferCalled(false),
              mTransform(0),
              mScalingMode(NATIVE_WINDOW_SCALING_MODE_FREEZE),
              mTimestamp(0),
              mFrameNumber(0),
              mFence(EGL_NO_SYNC_KHR) {
            mCrop.makeInvalid();
        }

        // mGraphicBuffer points to the buffer allocated for this slot or is NULL
        // if no buffer has been allocated.
        sp<GraphicBuffer> mGraphicBuffer;

        // mEglImage is the EGLImage created from mGraphicBuffer.
        EGLImageKHR mEglImage;

        // mEglDisplay is the EGLDisplay used to create mEglImage.
        EGLDisplay mEglDisplay;

        // BufferState represents the different states in which a buffer slot
        // can be.
        enum BufferState {
            // FREE indicates that the buffer is not currently being used and
            // will not be used in the future until it gets dequeued and
            // subsequently queued by the client.
            FREE = 0,

            // DEQUEUED indicates that the buffer has been dequeued by the
            // client, but has not yet been queued or canceled. The buffer is
            // considered 'owned' by the client, and the server should not use
            // it for anything.
            //
            // Note that when in synchronous-mode (mSynchronousMode == true),
            // the buffer that's currently attached to the texture may be
            // dequeued by the client.  That means that the current buffer can
            // be in either the DEQUEUED or QUEUED state.  In asynchronous mode,
            // however, the current buffer is always in the QUEUED state.
            DEQUEUED = 1,

            // QUEUED indicates that the buffer has been queued by the client,
            // and has not since been made available for the client to dequeue.
            // Attaching the buffer to the texture does NOT transition the
            // buffer away from the QUEUED state. However, in Synchronous mode
            // the current buffer may be dequeued by the client under some
            // circumstances. See the note about the current buffer in the
            // documentation for DEQUEUED.
            QUEUED = 2,
        };

        // mBufferState is the current state of this buffer slot.
        BufferState mBufferState;

        // mRequestBufferCalled is used for validating that the client did
        // call requestBuffer() when told to do so. Technically this is not
        // needed but useful for debugging and catching client bugs.
        bool mRequestBufferCalled;

        // mCrop is the current crop rectangle for this buffer slot. This gets
        // set to mNextCrop each time queueBuffer gets called for this buffer.
        Rect mCrop;

        // mTransform is the current transform flags for this buffer slot. This
        // gets set to mNextTransform each time queueBuffer gets called for this
        // slot.
        uint32_t mTransform;

        // mScalingMode is the current scaling mode for this buffer slot. This
        // gets set to mNextScalingMode each time queueBuffer gets called for
        // this slot.
        uint32_t mScalingMode;

        // mTimestamp is the current timestamp for this buffer slot. This gets
        // to set by queueBuffer each time this slot is queued.
        int64_t mTimestamp;

        // mFrameNumber is the number of the queued frame for this slot.
        uint64_t mFrameNumber;

        // mFence is the EGL sync object that must signal before the buffer
        // associated with this buffer slot may be dequeued. It is initialized
        // to EGL_NO_SYNC_KHR when the buffer is created and (optionally, based
        // on a compile-time option) set to a new sync object in updateTexImage.
        EGLSyncKHR mFence;
    };

    // mSlots is the array of buffer slots that must be mirrored on the client
    // side. This allows buffer ownership to be transferred between the client
    // and server without sending a GraphicBuffer over binder. The entire array
    // is initialized to NULL at construction time, and buffers are allocated
    // for a slot when requestBuffer is called with that slot's index.
    BufferSlot mSlots[NUM_BUFFER_SLOTS];

    // mDefaultWidth holds the default width of allocated buffers. It is used
    // in requestBuffers() if a width and height of zero is specified.
    uint32_t mDefaultWidth;

    // mDefaultHeight holds the default height of allocated buffers. It is used
    // in requestBuffers() if a width and height of zero is specified.
    uint32_t mDefaultHeight;

    // mPixelFormat holds the pixel format of allocated buffers. It is used
    // in requestBuffers() if a format of zero is specified.
    uint32_t mPixelFormat;

    // mBufferCount is the number of buffer slots that the client and server
    // must maintain. It defaults to MIN_ASYNC_BUFFER_SLOTS and can be changed
    // by calling setBufferCount or setBufferCountServer
    int mBufferCount;

    // mClientBufferCount is the number of buffer slots requested by the client.
    // The default is zero, which means the client doesn't care how many buffers
    // there is.
    int mClientBufferCount;

    // mServerBufferCount buffer count requested by the server-side
    int mServerBufferCount;

    // mCurrentTexture is the buffer slot index of the buffer that is currently
    // bound to the OpenGL texture. It is initialized to INVALID_BUFFER_SLOT,
    // indicating that no buffer slot is currently bound to the texture. Note,
    // however, that a value of INVALID_BUFFER_SLOT does not necessarily mean
    // that no buffer is bound to the texture. A call to setBufferCount will
    // reset mCurrentTexture to INVALID_BUFFER_SLOT.
    int mCurrentTexture;

    // mCurrentTextureBuf is the graphic buffer of the current texture. It's
    // possible that this buffer is not associated with any buffer slot, so we
    // must track it separately in order to support the getCurrentBuffer method.
    sp<GraphicBuffer> mCurrentTextureBuf;

    // mCurrentCrop is the crop rectangle that applies to the current texture.
    // It gets set each time updateTexImage is called.
    Rect mCurrentCrop;

    // mCurrentTransform is the transform identifier for the current texture. It
    // gets set each time updateTexImage is called.
    uint32_t mCurrentTransform;

    // mCurrentScalingMode is the scaling mode for the current texture. It gets
    // set to each time updateTexImage is called.
    uint32_t mCurrentScalingMode;

    // mCurrentTransformMatrix is the transform matrix for the current texture.
    // It gets computed by computeTransformMatrix each time updateTexImage is
    // called.
    float mCurrentTransformMatrix[16];

    // mCurrentTimestamp is the timestamp for the current texture. It
    // gets set each time updateTexImage is called.
    int64_t mCurrentTimestamp;

    // mNextCrop is the crop rectangle that will be used for the next buffer
    // that gets queued. It is set by calling setCrop.
    Rect mNextCrop;

    // mNextTransform is the transform identifier that will be used for the next
    // buffer that gets queued. It is set by calling setTransform.
    uint32_t mNextTransform;

    // mNextScalingMode is the scaling mode that will be used for the next
    // buffers that get queued. It is set by calling setScalingMode.
    int mNextScalingMode;

    // mTexName is the name of the OpenGL texture to which streamed images will
    // be bound when updateTexImage is called. It is set at construction time
    // changed with a call to setTexName.
    const GLuint mTexName;

    // mGraphicBufferAlloc is the connection to SurfaceFlinger that is used to
    // allocate new GraphicBuffer objects.
    sp<IGraphicBufferAlloc> mGraphicBufferAlloc;

    // mFrameAvailableListener is the listener object that will be called when a
    // new frame becomes available. If it is not NULL it will be called from
    // queueBuffer.
    sp<FrameAvailableListener> mFrameAvailableListener;

    // mSynchronousMode whether we're in synchronous mode or not
    bool mSynchronousMode;

    // mAllowSynchronousMode whether we allow synchronous mode or not
    const bool mAllowSynchronousMode;

    // mConnectedApi indicates the API that is currently connected to this
    // SurfaceTexture.  It defaults to NO_CONNECTED_API (= 0), and gets updated
    // by the connect and disconnect methods.
    int mConnectedApi;

    // mDequeueCondition condition used for dequeueBuffer in synchronous mode
    mutable Condition mDequeueCondition;

    // mQueue is a FIFO of queued buffers used in synchronous mode
    typedef Vector<int> Fifo;
    Fifo mQueue;

    // mAbandoned indicates that the SurfaceTexture will no longer be used to
    // consume images buffers pushed to it using the ISurfaceTexture interface.
    // It is initialized to false, and set to true in the abandon method.  A
    // SurfaceTexture that has been abandoned will return the NO_INIT error from
    // all ISurfaceTexture methods capable of returning an error.
    bool mAbandoned;

    // mName is a string used to identify the SurfaceTexture in log messages.
    // It is set by the setName method.
    String8 mName;

    // mUseFenceSync indicates whether creation of the EGL_KHR_fence_sync
    // extension should be used to prevent buffers from being dequeued before
    // it's safe for them to be written. It gets set at construction time and
    // never changes.
    const bool mUseFenceSync;

    // mMutex is the mutex used to prevent concurrent access to the member
    // variables of SurfaceTexture objects. It must be locked whenever the
    // member variables are accessed.
    mutable Mutex mMutex;

    // mTexTarget is the GL texture target with which the GL texture object is
    // associated.  It is set in the constructor and never changed.  It is
    // almost always GL_TEXTURE_EXTERNAL_OES except for one use case in Android
    // Browser.  In that case it is set to GL_TEXTURE_2D to allow
    // glCopyTexSubImage to read from the texture.  This is a hack to work
    // around a GL driver limitation on the number of FBO attachments, which the
    // browser's tile cache exceeds.
    const GLenum mTexTarget;

    // mFrameCounter is the free running counter, incremented for every buffer queued
    // with the surface Texture.
    uint64_t mFrameCounter;

#ifdef QCOM_HARDWARE
    struct BufferInfo {
         int width;
         int height;
         int format;
     };
 
     BufferInfo mNextBufferInfo;
#endif

};

// ----------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_GUI_SURFACETEXTURE_H
