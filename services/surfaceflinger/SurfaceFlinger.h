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

#ifndef ANDROID_SURFACE_FLINGER_H
#define ANDROID_SURFACE_FLINGER_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/SortedVector.h>
#include <utils/KeyedVector.h>
#include <utils/threads.h>
#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>

#include <binder/IMemory.h>
#include <binder/Permission.h>
#include <binder/BinderService.h>

#include <ui/PixelFormat.h>
#include <surfaceflinger/ISurfaceComposer.h>
#include <surfaceflinger/ISurfaceComposerClient.h>

#include "Barrier.h"
#include "Layer.h"

#include "MessageQueue.h"

namespace android {

// ---------------------------------------------------------------------------

class Client;
class DisplayHardware;
class FreezeLock;
class Layer;
class LayerBlur;
class LayerDim;
class LayerBuffer;

#define LIKELY( exp )       (__builtin_expect( (exp) != 0, true  ))
#define UNLIKELY( exp )     (__builtin_expect( (exp) != 0, false ))

// ---------------------------------------------------------------------------

class Client : public BnSurfaceComposerClient
{
public:
        Client(const sp<SurfaceFlinger>& flinger);
        ~Client();

    status_t initCheck() const;

    // protected by SurfaceFlinger::mStateLock
    ssize_t attachLayer(const sp<LayerBaseClient>& layer);
    void detachLayer(const LayerBaseClient* layer);
    sp<LayerBaseClient> getLayerUser(int32_t i) const;

private:

    // ISurfaceComposerClient interface
    virtual sp<IMemoryHeap> getControlBlock() const;
    virtual ssize_t getTokenForSurface(const sp<ISurface>& sur) const;
    virtual sp<ISurface> createSurface(
            surface_data_t* params, int pid, const String8& name,
            DisplayID display, uint32_t w, uint32_t h,PixelFormat format,
            uint32_t flags);
    virtual status_t destroySurface(SurfaceID surfaceId);
    virtual status_t setState(int32_t count, const layer_state_t* states);

    DefaultKeyedVector< size_t, wp<LayerBaseClient> > mLayers;
    sp<SurfaceFlinger> mFlinger;
    int32_t mNameGenerator;
};

class UserClient : public BnSurfaceComposerClient
{
public:
    // pointer to this client's control block
    SharedClient* ctrlblk;

public:
        UserClient(const sp<SurfaceFlinger>& flinger);
        ~UserClient();

    status_t initCheck() const;

    // protected by SurfaceFlinger::mStateLock
    void detachLayer(const Layer* layer);

private:

    // ISurfaceComposerClient interface
    virtual sp<IMemoryHeap> getControlBlock() const;
    virtual ssize_t getTokenForSurface(const sp<ISurface>& sur) const;
    virtual sp<ISurface> createSurface(
            surface_data_t* params, int pid, const String8& name,
            DisplayID display, uint32_t w, uint32_t h,PixelFormat format,
            uint32_t flags);
    virtual status_t destroySurface(SurfaceID surfaceId);
    virtual status_t setState(int32_t count, const layer_state_t* states);

    // atomic-ops
    mutable volatile int32_t mBitmap;

    sp<IMemoryHeap> mCblkHeap;
    sp<SurfaceFlinger> mFlinger;
};

// ---------------------------------------------------------------------------

class GraphicPlane
{
public:
    static status_t orientationToTransfrom(int orientation, int w, int h,
            Transform* tr);

                                GraphicPlane();
                                ~GraphicPlane();

        bool                    initialized() const;

        void                    setDisplayHardware(DisplayHardware *);
        status_t                setOrientation(int orientation);
        int                     getOrientation() const { return mOrientation; }
        int                     getWidth() const;
        int                     getHeight() const;

        const DisplayHardware&  displayHardware() const;
        DisplayHardware&        editDisplayHardware();
        const Transform&        transform() const;
        EGLDisplay              getEGLDisplay() const;
        
private:
                                GraphicPlane(const GraphicPlane&);
        GraphicPlane            operator = (const GraphicPlane&);

        DisplayHardware*        mHw;
        Transform               mGlobalTransform;
        Transform               mDisplayTransform;
        int                     mOrientation;
        float                   mDisplayWidth;
        float                   mDisplayHeight;
        int                     mWidth;
        int                     mHeight;
};

// ---------------------------------------------------------------------------

enum {
    eTransactionNeeded      = 0x01,
    eTraversalNeeded        = 0x02
};

class SurfaceFlinger :
        public BinderService<SurfaceFlinger>,
        public BnSurfaceComposer,
        protected Thread
{
public:
    static char const* getServiceName() { return "SurfaceFlinger"; }

                    SurfaceFlinger();
    virtual         ~SurfaceFlinger();
            void    init();

    virtual status_t onTransact(
        uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);

    virtual status_t dump(int fd, const Vector<String16>& args);

    // ISurfaceComposer interface
    virtual sp<ISurfaceComposerClient>  createConnection();
    virtual sp<ISurfaceComposerClient>  createClientConnection();
    virtual sp<IMemoryHeap>             getCblk() const;
    virtual void                        bootFinished();
    virtual void                        openGlobalTransaction();
    virtual void                        closeGlobalTransaction();
    virtual status_t                    freezeDisplay(DisplayID dpy, uint32_t flags);
    virtual status_t                    unfreezeDisplay(DisplayID dpy, uint32_t flags);
    virtual int                         setOrientation(DisplayID dpy, int orientation, uint32_t flags);
    virtual void                        signal() const;
    virtual status_t                    captureScreen(DisplayID dpy,
                                                      sp<IMemoryHeap>* heap,
                                                      uint32_t* width,
                                                      uint32_t* height,
                                                      PixelFormat* format,
                                                      uint32_t reqWidth,
                                                      uint32_t reqHeight);
    virtual status_t                    turnElectronBeamOff(int32_t mode);
    virtual status_t                    turnElectronBeamOn(int32_t mode);

            void                        screenReleased(DisplayID dpy);
            void                        screenAcquired(DisplayID dpy);

            overlay_control_device_t* getOverlayEngine() const;

            inline int                  getRenderEffect() const { return mRenderEffect; }
            inline int                  getRenderColorR() const { return mRenderColorR; }
            inline int                  getRenderColorG() const { return mRenderColorG; }
            inline int                  getRenderColorB() const { return mRenderColorB; }
            inline int                  getUseDithering() const { return mUseDithering; }

    status_t removeLayer(const sp<LayerBase>& layer);
    status_t addLayer(const sp<LayerBase>& layer);
    status_t invalidateLayerVisibility(const sp<LayerBase>& layer);

    sp<Layer> getLayer(const sp<ISurface>& sur) const;

private:
    friend class Client;
    friend class LayerBase;
    friend class LayerBuffer;
    friend class LayerBaseClient;
    friend class LayerBaseClient::Surface;
    friend class Layer;
    friend class LayerBlur;
    friend class LayerDim;

    sp<ISurface> createSurface(const sp<Client>& client,
            int pid, const String8& name,
            ISurfaceComposerClient::surface_data_t* params,
            DisplayID display, uint32_t w, uint32_t h, PixelFormat format,
            uint32_t flags);

    sp<Layer> createNormalSurface(
            const sp<Client>& client, DisplayID display,
            uint32_t w, uint32_t h, uint32_t flags,
            PixelFormat& format);

    sp<LayerBlur> createBlurSurface(
            const sp<Client>& client, DisplayID display,
            uint32_t w, uint32_t h, uint32_t flags);

    sp<LayerDim> createDimSurface(
            const sp<Client>& client, DisplayID display,
            uint32_t w, uint32_t h, uint32_t flags);

    sp<LayerBuffer> createPushBuffersSurface(
            const sp<Client>& client, DisplayID display,
            uint32_t w, uint32_t h, uint32_t flags);

    status_t removeSurface(const sp<Client>& client, SurfaceID sid);
    status_t destroySurface(const sp<LayerBaseClient>& layer);
    status_t setClientState(const sp<Client>& client,
            int32_t count, const layer_state_t* states);

    class LayerVector : public SortedVector< sp<LayerBase> > {
    public:
        LayerVector() { }
        LayerVector(const LayerVector& rhs) : SortedVector< sp<LayerBase> >(rhs) { }
        virtual int do_compare(const void* lhs, const void* rhs) const {
            const sp<LayerBase>& l(*reinterpret_cast<const sp<LayerBase>*>(lhs));
            const sp<LayerBase>& r(*reinterpret_cast<const sp<LayerBase>*>(rhs));
            // sort layers by Z order
            uint32_t lz = l->currentState().z;
            uint32_t rz = r->currentState().z;
            // then by sequence, so we get a stable ordering
            return (lz != rz) ? (lz - rz) : (l->sequence - r->sequence);
        }
    };

    struct State {
        State() {
            orientation = ISurfaceComposer::eOrientationDefault;
            freezeDisplay = 0;
        }
        LayerVector     layersSortedByZ;
        uint8_t         orientation;
        uint8_t         orientationType;
        uint8_t         freezeDisplay;
    };

    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();

public:     // hack to work around gcc 4.0.3 bug
    const GraphicPlane&     graphicPlane(int dpy) const;
          GraphicPlane&     graphicPlane(int dpy);
private:

            void        waitForEvent();
public:     // hack to work around gcc 4.0.3 bug
            void        signalEvent();
private:
            void        handleConsoleEvents();
            void        handleTransaction(uint32_t transactionFlags);
            void        handleTransactionLocked(
                            uint32_t transactionFlags, 
                            Vector< sp<LayerBase> >& ditchedLayers);

            void        computeVisibleRegions(
                            LayerVector& currentLayers,
                            Region& dirtyRegion,
                            Region& wormholeRegion);

            void        handlePageFlip();
            bool        lockPageFlip(const LayerVector& currentLayers);
            void        unlockPageFlip(const LayerVector& currentLayers);
            void        handleRepaint();
            bool        handleBypassLayer();
            void        postFramebuffer();
            void        composeSurfaces(const Region& dirty);


            ssize_t     addClientLayer(const sp<Client>& client,
                    const sp<LayerBaseClient>& lbc);
            status_t    addLayer_l(const sp<LayerBase>& layer);
            status_t    removeLayer_l(const sp<LayerBase>& layer);
            status_t    purgatorizeLayer_l(const sp<LayerBase>& layer);

            uint32_t    getTransactionFlags(uint32_t flags);
            uint32_t    setTransactionFlags(uint32_t flags);
            void        commitTransaction();

            void        setBypassLayer(const sp<LayerBase>& layer);

            status_t captureScreenImplLocked(DisplayID dpy,
                    sp<IMemoryHeap>* heap,
                    uint32_t* width, uint32_t* height, PixelFormat* format,
                    uint32_t reqWidth = 0, uint32_t reqHeight = 0);

            status_t turnElectronBeamOffImplLocked(int32_t mode);
            status_t turnElectronBeamOnImplLocked(int32_t mode);
            status_t electronBeamOffAnimationImplLocked();
            status_t electronBeamOnAnimationImplLocked();
            status_t renderScreenToTextureLocked(DisplayID dpy,
                    GLuint* textureName, GLfloat* uOut, GLfloat* vOut);

            friend class FreezeLock;
            sp<FreezeLock> getFreezeLock() const;
            inline void incFreezeCount() {
                if (mFreezeCount == 0)
                    mFreezeDisplayTime = 0;
                mFreezeCount++;
            }
            inline void decFreezeCount() { if (mFreezeCount > 0) mFreezeCount--; }
            inline bool hasFreezeRequest() const { return mFreezeDisplay; }
            inline bool isFrozen() const { 
                return (mFreezeDisplay || mFreezeCount>0) && mBootFinished;
            }

            
            void        debugFlashRegions();
            void        debugShowFPS() const;
            void        drawWormhole() const;
           

    mutable     MessageQueue    mEventQueue;

    status_t postMessageAsync(const sp<MessageBase>& msg,
            nsecs_t reltime=0, uint32_t flags = 0);

    status_t postMessageSync(const sp<MessageBase>& msg,
            nsecs_t reltime=0, uint32_t flags = 0);

                // access must be protected by mStateLock
    mutable     Mutex                   mStateLock;
                State                   mCurrentState;
                State                   mDrawingState;
    volatile    int32_t                 mTransactionFlags;
    volatile    int32_t                 mTransactionCount;
                Condition               mTransactionCV;
                bool                    mResizeTransationPending;

                // protected by mStateLock (but we could use another lock)
                GraphicPlane                mGraphicPlanes[1];
                bool                        mLayersRemoved;
                DefaultKeyedVector< wp<IBinder>, wp<Layer> > mLayerMap;

                // constant members (no synchronization needed for access)
                sp<IMemoryHeap>             mServerHeap;
                surface_flinger_cblk_t*     mServerCblk;
                GLuint                      mWormholeTexName;
                nsecs_t                     mBootTime;
                Permission                  mHardwareTest;
                Permission                  mAccessSurfaceFlinger;
                Permission                  mReadFramebuffer;
                Permission                  mDump;
                
                // Can only accessed from the main thread, these members
                // don't need synchronization
                Region                      mDirtyRegion;
                Region                      mDirtyRegionRemovedLayer;
                Region                      mInvalidRegion;
                Region                      mWormholeRegion;
                bool                        mVisibleRegionsDirty;
                bool                        mDeferReleaseConsole;
                bool                        mFreezeDisplay;
                int32_t                     mElectronBeamAnimationMode;
                int32_t                     mFreezeCount;
                nsecs_t                     mFreezeDisplayTime;
                Vector< sp<LayerBase> >     mVisibleLayersSortedByZ;
                wp<Layer>                   mBypassLayer;


                // don't use a lock for these, we don't care
                int                         mDebugRegion;
                int                         mDebugBackground;
                int                         mRenderEffect;
		int			    mRenderColorR;
		int			    mRenderColorG;
		int			    mRenderColorB;
                volatile nsecs_t            mDebugInSwapBuffers;
                nsecs_t                     mLastSwapBufferTime;
                volatile nsecs_t            mDebugInTransaction;
                nsecs_t                     mLastTransactionTime;
                bool                        mBootFinished;

                // these are thread safe
    mutable     Barrier                     mReadyToRunBarrier;

                // atomic variables
                enum {
                    eConsoleReleased = 1,
                    eConsoleAcquired = 2
                };
   volatile     int32_t                     mConsoleSignals;

   // only written in the main thread, only read in other threads
   volatile     int32_t                     mSecureFrameBuffer;

                bool                        mUseDithering;
};

// ---------------------------------------------------------------------------

class FreezeLock : public LightRefBase<FreezeLock> {
    SurfaceFlinger* mFlinger;
public:
    FreezeLock(SurfaceFlinger* flinger)
        : mFlinger(flinger) {
        mFlinger->incFreezeCount();
    }
    ~FreezeLock() {
        mFlinger->decFreezeCount();
    }
};

// ---------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_SURFACE_FLINGER_H
