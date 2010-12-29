/*
 * Copyright (C) Texas Instruments - http://www.ti.com/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "OverlayRenderer"
#include <media/OverlayRenderer.h>

#define ARMPAGESIZE 4096
#define BOOL_STR(val)  ( (val) ? "TRUE" : "FALSE")

#define LOG_FUNCTION_NAME_ENTRY    LOGV(" ###### Calling %s() Line %d PID %d ++ ######",  __FUNCTION__, __LINE__, getpid());
#define LOG_FUNCTION_NAME_EXIT    LOGV(" ###### Calling %s() -- ######",  __FUNCTION__);

//#define __DUMP_TO_FILE__
#ifdef __DUMP_TO_FILE__
#define NUM_BUFFERS_TO_DUMP 46
#define FIRST_FRAME_INDEX 23
FILE *pOutFile;
#endif

using namespace android;

namespace android{

/*
  * Test is a friend class of Surface Control, and hence has access
  * getISurface() method.
  * TODO: The test class is 'added' by TI as a friend. Need to add
  * TIFlashRenderer as a 'friend' to get the ISurface.
  */
class Test {
        public:
            static const sp<ISurface>& getISurface(const sp<SurfaceControl>& s) {
                return s->getISurface();
            }
    };

#ifdef __DUMP_TO_FILE__

void dumpBufferToFile(void* pSrc, uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint32_t s)
{
    //LOGD("framenumber = %u, pSrc = %p, x = %u, y = %u, w = %u, h = %u, s = %u", framenumber, pSrc, x, y, w, h, s);
    /* Dumping only the Y component */
    pSrc = pSrc + (y*s);
    for (int i = 0; i < h; i++) {
        fwrite(pSrc+x, 1, w, pOutFile);
        fflush(pOutFile);
        pSrc = pSrc + s;
    }
}

#endif

OverlayRenderer::OverlayRenderer()
{
    LOG_FUNCTION_NAME_ENTRY
}

OverlayRenderer::~OverlayRenderer()
{
    LOG_FUNCTION_NAME_ENTRY
}

int32_t OverlayRenderer::createOverlayRenderer(uint32_t bufferCount,
                                int32_t displayWidth,
                                int32_t displayHeight,
                                uint32_t colorFormat,
                                int32_t decodedWidth,
                                int32_t decodedHeight,
                                int infoType,
                                void * info)
{
    LOG_FUNCTION_NAME_ENTRY

    mBufferCount = bufferCount;
    mDisplayWidth = displayWidth;
    mDisplayHeight = displayHeight;
    mColorFormat = colorFormat;
    mDecodedWidth = decodedWidth;
    mDecodedHeight = decodedHeight;
    mInfoType = infoType;
    //Keep in mind that info ptr is NOT passed across in BpOverlayRenderer

    mInitCheck = NO_INIT;

    /* Create required surfaces and overlay objects */
    if(OK != createSurface()){
        LOGE("Failed to create Surface");
        goto EXIT;
    }

    /* creating buffers */
    if(OK != createBuffers()) {
        LOGE("Failed to create Buffers");
        goto EXIT;
    }

    mInitCheck = OK;

#ifdef __DUMP_TO_FILE__

    char filename[100];
    sprintf(filename, "/sdcard/framedump_%dx%d.yuv", mDisplayWidth, mDisplayHeight);
    pOutFile = fopen(filename, "wb");
    if(pOutFile == NULL)
        LOGE("\n!!!!!!!!!!!!!!!!Error opening file %s !!!!!!!!!!!!!!!!!!!!", filename);

#endif

EXIT:

    return mInitCheck;
}

void OverlayRenderer::releaseMe()
{
    //delete this;

    LOG_FUNCTION_NAME_ENTRY

    if ( NULL != mOverlay.get() ) {
        mOverlay->destroy();
        mOverlay.clear();
    }

    //if ( NULL != mOverlayRef.get() )
    {
        //mOverlayRef = 0;
    }

    if ( NULL != mISurface.get() ) {
        mISurface.clear();
    }

    if ( NULL != mSurfaceControl.get() ) {
        mSurfaceControl->clear();
        mSurfaceControl.clear();
    }

    if ( NULL != mSurfaceClient.get() ) {
        mSurfaceClient->dispose();
        mSurfaceClient.clear();
    }


    /* TODO: make sure all other resources are released as well */

#ifdef __DUMP_TO_FILE__
        if (pOutFile) fclose(pOutFile);
#endif

}

sp<IMemory> OverlayRenderer::getBuffer(uint32_t index)
{
    if (index >= mBufferCount) index = 0;
    return mOverlayAddresses[index];
}

int32_t OverlayRenderer::createBuffers()
{
    LOG_FUNCTION_NAME_ENTRY

    mapping_data_t *data;
    sp<IMemory> mem;

    /* check if the overlay created required number of buffers for decoder */
    if (mBufferCount != (uint32_t)mOverlay->getBufferCount() ) {
        mOverlay->setParameter(OVERLAY_NUM_BUFFERS, mBufferCount);
        mOverlay->resizeInput(mDecodedWidth, mDecodedHeight);
    }

    /* TODO: Optimal buffer count now 2. Confirm?? */
    mOverlay->setParameter(OPTIMAL_QBUF_CNT, 4);

    for (size_t bCount = 0; bCount < mBufferCount; ++bCount) {
        data = (mapping_data_t *)mOverlay->getBufferAddress((void *)bCount);
        CHECK(data != NULL);

        mVideoHeaps[bCount] = new MemoryHeapBase(data->fd,data->length, 0, data->offset);
        mem = new MemoryBase(mVideoHeaps[bCount], 0, data->length);
        CHECK(mem.get() != NULL);

        LOGV("data->fd %d, data->length =%d, data->offset =%x, mem->pointer[%d] = %p", data->fd,data->length, data->offset, bCount, mem->pointer());

        mOverlayAddresses.push(mem);
    }

    return OK;
}


int32_t OverlayRenderer::createSurface()
{
    LOG_FUNCTION_NAME_ENTRY

    if(mOverlay.get() == NULL){

        mSurfaceClient = new SurfaceComposerClient();

        if ( NULL == mSurfaceClient.get() ) {
            LOGE("Unable to establish connection to Surface Composer \n");
            return UNKNOWN_ERROR;
        }

        mSurfaceControl = mSurfaceClient->createSurface(getpid(), 0, mDecodedWidth, mDecodedHeight,
                           PIXEL_FORMAT_UNKNOWN, ISurfaceComposer::ePushBuffers);

        if ( NULL == mSurfaceControl.get() ) {
            LOGE("Unable to create Overlay control surface\n");
            return UNKNOWN_ERROR;
        }

        mISurface = Test::getISurface(mSurfaceControl);
        if ( NULL == mISurface.get() ) {
            LOGE("Unable to get overlay ISurface interface\n");
            return UNKNOWN_ERROR;
        }

        sp<OverlayRef> ref = mISurface->createOverlay(mDecodedWidth, mDecodedHeight, mColorFormat, 0);
        if (ref.get() == NULL) {
            LOGE("Unable to create Overlay Reference\n");
            return UNKNOWN_ERROR;
        }

        //Create Overlay Object
        mOverlay = new Overlay(ref);

        if(mOverlay.get() == NULL) {
            LOGE("Unable to create Overlay Object\n");
            return UNKNOWN_ERROR;
        }

        /* Configuring the surface */
        mSurfaceClient->openTransaction();
        mSurfaceControl->setLayer(100000);
        //mSurfaceControl->setLayer(0x40000000);set in BootAnimation.cpp
        mSurfaceClient->closeTransaction();

    }
    return OK;

}

int32_t OverlayRenderer::dequeueBuffer(uint32_t *index)
{
    return mOverlay->dequeueBuffer((overlay_buffer_t*)index);
}

int32_t OverlayRenderer::queueBuffer(uint32_t index)
{

#ifdef __DUMP_TO_FILE__
    static int displaycount = 0;
    static int lastframetodump = FIRST_FRAME_INDEX+NUM_BUFFERS_TO_DUMP;

    if ((displaycount >= FIRST_FRAME_INDEX)  && (displaycount < lastframetodump)){
        //dumpBufferToFile(mediaBuf->data(), mCropX, mCropY, mDisplayWidth, mDisplayHeight, 4096);
    }
    displaycount++;

#endif

    return mOverlay->queueBuffer((void *)index);
}

int32_t OverlayRenderer::setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h)
{
    return mOverlay->setCrop(x, y, w, h);
}

int32_t OverlayRenderer::setPosition(int32_t x, int32_t y)
{
    win_x = x;
    win_y = y;
    mSurfaceClient->openTransaction();
    mSurfaceControl->setPosition(win_x, win_y);
    mSurfaceClient->closeTransaction();

    return OK;
}

int32_t OverlayRenderer::setSize(uint32_t w, uint32_t h)
{
    mSurfaceClient->openTransaction();
    mSurfaceControl->setSize(w, h);
    mSurfaceClient->closeTransaction();

    /* I am changing Position too bcos just a change in size is not recognized by surface flinger. */

    /* In LayerBase::setPosition(), the sequence no. is incremented. But the same is not done
        for LayerBase::setSize(). It is this change in sequence no. which triggers
        LayerBuffer::OverlaySource::onTransaction() which triggers
        LayerBuffer::OverlaySource::onVisibilityResolved() which triggers
        overlay_dev->setPosition()
    */

    mSurfaceClient->openTransaction();
    mSurfaceControl->setPosition(1, 1);
    mSurfaceClient->closeTransaction();

    mSurfaceClient->openTransaction();
    mSurfaceControl->setPosition(win_x, win_y);
    mSurfaceClient->closeTransaction();

    return OK;
}

int32_t OverlayRenderer::setOrientation(int32_t orientation, uint32_t flags)
{
    mSurfaceClient->openTransaction();
    switch(orientation)
        {
        case 0:
            mSurfaceClient->setOrientation(0,ISurfaceComposer::eOrientationDefault,0);
            break;
        case 90:
            mSurfaceClient->setOrientation(0,ISurfaceComposer::eOrientation90,0);
            break;
        case 180:
            mSurfaceClient->setOrientation(0,ISurfaceComposer::eOrientation180,0);
            break;
        case 270:
            mSurfaceClient->setOrientation(0,ISurfaceComposer::eOrientation270,0);
            break;
        default:
            LOGD("Rotation not applied. Illegal Value");
        };

    mSurfaceClient->closeTransaction();
    return OK;
}

////////////////////////////////////////////////////////////////////////////////

enum {
    CREATE_OVERLAY_RENDERER,
    RELEASE,
    GET_BUFFER_COUNT,
    GET_BUFFER,
    DEQUEUE_BUFFER,
    QUEUE_BUFFER,
    SET_ORIENTATION,
    SET_POSITION,
    SET_SIZE,
    SET_CROP
};



class BpOverlayRenderer : public BpInterface<IOverlayRenderer> {
public:
    BpOverlayRenderer(const sp<IBinder> &impl)
        : BpInterface<IOverlayRenderer>(impl) {
    }

    virtual int32_t createOverlayRenderer(
        uint32_t bufferCount, int32_t displayWidth,  int32_t displayHeight,
        uint32_t colorFormat, int32_t decodedWidth, int32_t decodedHeight,
        int infoType, void * info)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(bufferCount);
        data.writeInt32(displayWidth);
        data.writeInt32(displayHeight);
        data.writeInt32(colorFormat);
        data.writeInt32(decodedWidth);
        data.writeInt32(decodedHeight);
        data.writeInt32(infoType);
        remote()->transact(CREATE_OVERLAY_RENDERER, data, &reply);
        return reply.readInt32();
    }

    virtual void releaseMe()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        remote()->transact(RELEASE, data, &reply);
    }

    virtual uint32_t getBufferCount()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        remote()->transact(GET_BUFFER_COUNT, data, &reply);
        return reply.readInt32();
    }

    virtual sp<IMemory> getBuffer(uint32_t index)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(index);
        remote()->transact(GET_BUFFER, data, &reply);
        sp<IMemory> params = interface_cast<IMemory>(reply.readStrongBinder());
        return params;
    }

    virtual int32_t dequeueBuffer(uint32_t *index)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        remote()->transact(DEQUEUE_BUFFER, data, &reply);
        int32_t err = reply.readInt32();
        *index = reply.readInt32();
        return err;
    }

    virtual int32_t queueBuffer(uint32_t index)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(index);
        remote()->transact(QUEUE_BUFFER, data, &reply);
        return reply.readInt32();
    }

    virtual int32_t setOrientation(int32_t orientation, uint32_t flags)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(orientation);
        data.writeInt32(flags);
        remote()->transact(SET_ORIENTATION, data, &reply);
        return reply.readInt32();
    }

    virtual int32_t setPosition(int32_t x, int32_t y)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(x);
        data.writeInt32(y);
        remote()->transact(SET_POSITION, data, &reply);
        return reply.readInt32();
    }

    virtual int32_t setSize(uint32_t w, uint32_t h)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(w);
        data.writeInt32(h);
        remote()->transact(SET_SIZE, data, &reply);
        return reply.readInt32();
    }

    virtual int32_t setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IOverlayRenderer::getInterfaceDescriptor());
        data.writeInt32(x);
        data.writeInt32(y);
        data.writeInt32(w);
        data.writeInt32(h);
        remote()->transact(SET_CROP, data, &reply);
        return reply.readInt32();
    }

};

IMPLEMENT_META_INTERFACE(OverlayRenderer, "android.media.IOverlayRenderer");

status_t BnOverlayRenderer::onTransact(
    uint32_t code, const Parcel &data, Parcel *reply, uint32_t flags) {

    switch (code) {

        case CREATE_OVERLAY_RENDERER:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);
            uint32_t bufferCount = data.readInt32();
            int32_t displayWidth = data.readInt32();
            int32_t displayHeight = data.readInt32();
            uint32_t colorFormat = data.readInt32();
            int32_t decodedWidth = data.readInt32();
            int32_t decodedHeight = data.readInt32();
            int infoType = data.readInt32();
            void *info;
            reply->writeInt32(createOverlayRenderer(bufferCount, displayWidth, displayHeight, colorFormat, decodedWidth, decodedHeight, infoType, info));

            return NO_ERROR;
        }

        case RELEASE:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);
            releaseMe();
            return NO_ERROR;
        }

        case GET_BUFFER_COUNT:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t count = getBufferCount();
            reply->writeInt32(count);

            return NO_ERROR;
        }

        case GET_BUFFER:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            int32_t index = data.readInt32();

            sp<IMemory> params  = getBuffer(index);
            reply->writeStrongBinder(params->asBinder());

            return NO_ERROR;
        }

        case DEQUEUE_BUFFER:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t index;

            int32_t err = dequeueBuffer(&index);
            reply->writeInt32(err);
            reply->writeInt32(index);
            return NO_ERROR;
        }

        case QUEUE_BUFFER:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            int32_t index = data.readInt32();

            reply->writeInt32(queueBuffer(index));
            return NO_ERROR;
        }

        case SET_ORIENTATION:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t orientation = data.readInt32();
            uint32_t flags = data.readInt32();

            reply->writeInt32(setOrientation(orientation, flags));
            return NO_ERROR;
        }

        case SET_POSITION:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t x = data.readInt32();
            uint32_t y = data.readInt32();

            reply->writeInt32(setPosition(x, y));
            return NO_ERROR;
        }

        case SET_SIZE:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t w = data.readInt32();
            uint32_t h = data.readInt32();

            reply->writeInt32(setSize(w, h));
            return NO_ERROR;
        }

        case SET_CROP:
        {
            CHECK_INTERFACE(IOverlayRenderer, data, reply);

            uint32_t x = data.readInt32();
            uint32_t y = data.readInt32();
            uint32_t w = data.readInt32();
            uint32_t h = data.readInt32();

            reply->writeInt32(setCrop(x, y, w, h));
            return NO_ERROR;
        }

        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}



}

