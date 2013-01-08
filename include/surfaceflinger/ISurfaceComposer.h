/*
 * Copyright (C) 2006 The Android Open Source Project
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

#ifndef ANDROID_SF_ISURFACE_COMPOSER_H
#define ANDROID_SF_ISURFACE_COMPOSER_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/RefBase.h>
#include <utils/Errors.h>

#include <binder/IInterface.h>

#include <ui/PixelFormat.h>

#include <surfaceflinger/ISurfaceComposerClient.h>
#include <surfaceflinger/IGraphicBufferAlloc.h>

namespace android {
// ----------------------------------------------------------------------------

class IMemoryHeap;
class ComposerState;

class ISurfaceComposer : public IInterface
{
public:
    DECLARE_META_INTERFACE(SurfaceComposer);

    enum { // (keep in sync with Surface.java)
        eHidden             = 0x00000004,
        eDestroyBackbuffer  = 0x00000020,
        eSecure             = 0x00000080,
        eNonPremultiplied   = 0x00000100,
        eOpaque             = 0x00000400,
        eProtectedByApp     = 0x00000800,
        eProtectedByDRM     = 0x00001000,

        eFXSurfaceNormal    = 0x00000000,
        eFXSurfaceBlur      = 0x00010000,
        eFXSurfaceDim       = 0x00020000,
        eFXSurfaceScreenshot= 0x00030000,
        eFXSurfaceMask      = 0x000F0000,
    };

    enum {
        ePositionChanged            = 0x00000001,
        eLayerChanged               = 0x00000002,
        eSizeChanged                = 0x00000004,
        eAlphaChanged               = 0x00000008,
        eMatrixChanged              = 0x00000010,
        eTransparentRegionChanged   = 0x00000020,
        eVisibilityChanged          = 0x00000040,
        eFreezeTintChanged          = 0x00000080,
    };

    enum {
        eLayerHidden        = 0x01,
        eLayerFrozen        = 0x02,
        eLayerDither        = 0x04,
        eLayerFilter        = 0x08,
        eLayerBlurFreeze    = 0x10
    };

    enum {
        eOrientationDefault     = 0,
        eOrientation90          = 1,
        eOrientation180         = 2,
        eOrientation270         = 3,
        eOrientationUnchanged   = 4,
        eOrientationSwapMask    = 0x01
    };

    enum {
        eSynchronous            = 0x01,
    };

    enum {
        eElectronBeamAnimationOn  = 0x01,
        eElectronBeamAnimationOff = 0x10
    };

    /* create connection with surface flinger, requires
     * ACCESS_SURFACE_FLINGER permission
     */
    virtual sp<ISurfaceComposerClient> createConnection() = 0;

    /* create a graphic buffer allocator
     */
    virtual sp<IGraphicBufferAlloc> createGraphicBufferAlloc() = 0;

    /* retrieve the control block */
    virtual sp<IMemoryHeap> getCblk() const = 0;

    /* open/close transactions. requires ACCESS_SURFACE_FLINGER permission */
    virtual void setTransactionState(const Vector<ComposerState>& state,
            int orientation, uint32_t flags) = 0;

    /* signal that we're done booting.
     * Requires ACCESS_SURFACE_FLINGER permission
     */
    virtual void bootFinished() = 0;

    /* Capture the specified screen. requires READ_FRAME_BUFFER permission
     * This function will fail if there is a secure window on screen.
     */
    virtual status_t captureScreen(DisplayID dpy,
            sp<IMemoryHeap>* heap,
            uint32_t* width, uint32_t* height, PixelFormat* format,
            uint32_t reqWidth, uint32_t reqHeight,
            uint32_t minLayerZ, uint32_t maxLayerZ) = 0;

    virtual status_t turnElectronBeamOff(int32_t mode) = 0;
    virtual status_t turnElectronBeamOn(int32_t mode) = 0;

    /* verify that an ISurfaceTexture was created by SurfaceFlinger.
     */
    virtual bool authenticateSurfaceTexture(
            const sp<ISurfaceTexture>& surface) const = 0;

#ifdef QCOM_HDMI_OUT
    //External display specific functions
    virtual void enableExternalDisplay(int disp_type, int enable) = 0;
#endif
#ifdef STE_HDMI
    //External display specific functions
    virtual int setHDMIParameter(int disp_type, int enable) = 0;
#endif

};

// ----------------------------------------------------------------------------

class BnSurfaceComposer : public BnInterface<ISurfaceComposer>
{
public:
    enum {
        // Note: BOOT_FINISHED must remain this value, it is called from
        // Java by ActivityManagerService.
        BOOT_FINISHED = IBinder::FIRST_CALL_TRANSACTION,
        CREATE_CONNECTION,
        CREATE_GRAPHIC_BUFFER_ALLOC,
        GET_CBLK,
        SET_TRANSACTION_STATE,
        SET_ORIENTATION,
        CAPTURE_SCREEN,
        TURN_ELECTRON_BEAM_OFF,
        TURN_ELECTRON_BEAM_ON,
        AUTHENTICATE_SURFACE,
#ifdef QCOM_HDMI_OUT
        EXTERNAL_DISPLAY,
#endif
#ifdef STE_HDMI
        SET_HDMI_PARAMETER,
#endif
    };

    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_SF_ISURFACE_COMPOSER_H
