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

#include <stdlib.h>
#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>

#include <GLES/gl.h>
#include <GLES/glext.h>

#include <hardware/hardware.h>

#include "clz.h"
#include "LayerBase.h"
#include "SurfaceFlinger.h"
#include "DisplayHardware/DisplayHardware.h"
#include "TextureManager.h"

#define RENDER_EFFECT_NIGHT 1
#define RENDER_EFFECT_TERMINAL 2
#define RENDER_EFFECT_BLUE 3
#define RENDER_EFFECT_AMBER 4
#define RENDER_EFFECT_SALMON 5
#define RENDER_EFFECT_FUSCIA 6
#define RENDER_EFFECT_N1_CALIBRATED_N 7
#define RENDER_EFFECT_N1_CALIBRATED_R 8
#define RENDER_EFFECT_N1_CALIBRATED_C 9

namespace android {

// ---------------------------------------------------------------------------

int32_t LayerBase::sSequence = 1;

LayerBase::LayerBase(SurfaceFlinger* flinger, DisplayID display)
    : dpy(display), contentDirty(false),
      sequence(uint32_t(android_atomic_inc(&sSequence))),
      mFlinger(flinger),
      mNeedsFiltering(false),
      mOrientation(0),
      mLeft(0), mTop(0),
      mTransactionFlags(0),
      mPremultipliedAlpha(true), mName("unnamed"), mDebug(false),
      mInvalidate(0)
#ifdef AVOID_DRAW_TEXTURE
      ,mTransformed(false)
#endif
{
    const DisplayHardware& hw(flinger->graphicPlane(0).displayHardware());
    mFlags = hw.getFlags();
    mBufferCrop.makeInvalid();
    mBufferTransform = 0;
}

LayerBase::~LayerBase()
{
}

void LayerBase::setName(const String8& name) {
    mName = name;
}

String8 LayerBase::getName() const {
    return mName;
}

const GraphicPlane& LayerBase::graphicPlane(int dpy) const
{ 
    return mFlinger->graphicPlane(dpy);
}

GraphicPlane& LayerBase::graphicPlane(int dpy)
{
    return mFlinger->graphicPlane(dpy); 
}

void LayerBase::initStates(uint32_t w, uint32_t h, uint32_t flags)
{
    uint32_t layerFlags = 0;
    if (flags & ISurfaceComposer::eHidden)
        layerFlags = ISurfaceComposer::eLayerHidden;

    if (flags & ISurfaceComposer::eNonPremultiplied)
        mPremultipliedAlpha = false;

    mCurrentState.z             = 0;
    mCurrentState.w             = w;
    mCurrentState.h             = h;
    mCurrentState.requested_w   = w;
    mCurrentState.requested_h   = h;
    mCurrentState.alpha         = 0xFF;
    mCurrentState.flags         = layerFlags;
    mCurrentState.sequence      = 0;
    mCurrentState.transform.set(0, 0);

    // drawing state & current state are identical
    mDrawingState = mCurrentState;
}

void LayerBase::commitTransaction() {
    mDrawingState = mCurrentState;
}
void LayerBase::forceVisibilityTransaction() {
    // this can be called without SurfaceFlinger.mStateLock, but if we
    // can atomically increment the sequence number, it doesn't matter.
    android_atomic_inc(&mCurrentState.sequence);
    requestTransaction();
}
bool LayerBase::requestTransaction() {
    int32_t old = setTransactionFlags(eTransactionNeeded);
    return ((old & eTransactionNeeded) == 0);
}
uint32_t LayerBase::getTransactionFlags(uint32_t flags) {
    return android_atomic_and(~flags, &mTransactionFlags) & flags;
}
uint32_t LayerBase::setTransactionFlags(uint32_t flags) {
    return android_atomic_or(flags, &mTransactionFlags);
}

bool LayerBase::setPosition(int32_t x, int32_t y) {
    if (mCurrentState.transform.tx() == x && mCurrentState.transform.ty() == y)
        return false;
    mCurrentState.sequence++;
    mCurrentState.transform.set(x, y);
    requestTransaction();
    return true;
}
bool LayerBase::setLayer(uint32_t z) {
    if (mCurrentState.z == z)
        return false;
    mCurrentState.sequence++;
    mCurrentState.z = z;
    requestTransaction();
    return true;
}
bool LayerBase::setSize(uint32_t w, uint32_t h) {
    if (mCurrentState.requested_w == w && mCurrentState.requested_h == h)
        return false;
    mCurrentState.requested_w = w;
    mCurrentState.requested_h = h;
    requestTransaction();
    return true;
}
bool LayerBase::setAlpha(uint8_t alpha) {
    if (mCurrentState.alpha == alpha)
        return false;
    mCurrentState.sequence++;
    mCurrentState.alpha = alpha;
    requestTransaction();
    return true;
}
bool LayerBase::setMatrix(const layer_state_t::matrix22_t& matrix) {
    mCurrentState.sequence++;
    mCurrentState.transform.set(
            matrix.dsdx, matrix.dsdy, matrix.dtdx, matrix.dtdy);
    requestTransaction();
    return true;
}
bool LayerBase::setTransparentRegionHint(const Region& transparent) {
    mCurrentState.sequence++;
    mCurrentState.transparentRegion = transparent;
    requestTransaction();
    return true;
}
bool LayerBase::setFlags(uint8_t flags, uint8_t mask) {
    const uint32_t newFlags = (mCurrentState.flags & ~mask) | (flags & mask);
    if (mCurrentState.flags == newFlags)
        return false;
    mCurrentState.sequence++;
    mCurrentState.flags = newFlags;
    requestTransaction();
    return true;
}

Rect LayerBase::visibleBounds() const
{
    return mTransformedBounds;
}      

void LayerBase::setVisibleRegion(const Region& visibleRegion) {
    // always called from main thread
    visibleRegionScreen = visibleRegion;
}

void LayerBase::setCoveredRegion(const Region& coveredRegion) {
    // always called from main thread
    coveredRegionScreen = coveredRegion;
}

uint32_t LayerBase::doTransaction(uint32_t flags)
{
    const Layer::State& front(drawingState());
    const Layer::State& temp(currentState());

    if ((front.requested_w != temp.requested_w) ||
        (front.requested_h != temp.requested_h))  {
        // resize the layer, set the physical size to the requested size
        Layer::State& editTemp(currentState());
        editTemp.w = temp.requested_w;
        editTemp.h = temp.requested_h;
    }

    if ((front.w != temp.w) || (front.h != temp.h)) {
        // invalidate and recompute the visible regions if needed
        flags |= Layer::eVisibleRegion;
    }

    if (temp.sequence != front.sequence) {
        // invalidate and recompute the visible regions if needed
        flags |= eVisibleRegion;
        this->contentDirty = true;

        // we may use linear filtering, if the matrix scales us
        const uint8_t type = temp.transform.getType();
        mNeedsFiltering = (!temp.transform.preserveRects() ||
                (type >= Transform::SCALE));
    }

    // Commit the transaction
    commitTransaction();
    return flags;
}

void LayerBase::validateVisibility(const Transform& planeTransform)
{
    const Layer::State& s(drawingState());
    const Transform tr(planeTransform * s.transform);
    const bool transformed = tr.transformed();
   
    uint32_t w = s.w;
    uint32_t h = s.h;    
    tr.transform(mVertices[0], 0, 0);
    tr.transform(mVertices[1], 0, h);
    tr.transform(mVertices[2], w, h);
    tr.transform(mVertices[3], w, 0);
    if (UNLIKELY(transformed)) {
        // NOTE: here we could also punt if we have too many rectangles
        // in the transparent region
        if (tr.preserveRects()) {
            // transform the transparent region
            transparentRegionScreen = tr.transform(s.transparentRegion);
        } else {
            // transformation too complex, can't do the transparent region
            // optimization.
            transparentRegionScreen.clear();
        }
    } else {
        transparentRegionScreen = s.transparentRegion;
    }

    // cache a few things...
    mOrientation = tr.getOrientation();
    mTransformedBounds = tr.makeBounds(w, h);
#ifdef AVOID_DRAW_TEXTURE
    mTransformed = transformed;
#endif
    mLeft = tr.tx();
    mTop  = tr.ty();
}

void LayerBase::lockPageFlip(bool& recomputeVisibleRegions)
{
}

void LayerBase::unlockPageFlip(
        const Transform& planeTransform, Region& outDirtyRegion)
{
    if ((android_atomic_and(~1, &mInvalidate)&1) == 1) {
        outDirtyRegion.orSelf(visibleRegionScreen);
    }
}

void LayerBase::invalidate()
{
    if ((android_atomic_or(1, &mInvalidate)&1) == 0) {
        mFlinger->signalEvent();
    }
}

void LayerBase::drawRegion(const Region& reg) const
{
    Region::const_iterator it = reg.begin();
    Region::const_iterator const end = reg.end();
    if (it != end) {
        Rect r;
        const DisplayHardware& hw(graphicPlane(0).displayHardware());
        const int32_t fbWidth  = hw.getWidth();
        const int32_t fbHeight = hw.getHeight();
        const GLshort vertices[][2] = { { 0, 0 }, { fbWidth, 0 }, 
                { fbWidth, fbHeight }, { 0, fbHeight }  };
        glVertexPointer(2, GL_SHORT, 0, vertices);
        while (it != end) {
            const Rect& r = *it++;
            const GLint sy = fbHeight - (r.top + r.height());
            glScissor(r.left, sy, r.width(), r.height());
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
        }
    }
}

void LayerBase::draw(const Region& clip) const
{
    // reset GL state
    glEnable(GL_SCISSOR_TEST);

    onDraw(clip);
}

void LayerBase::drawForSreenShot() const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    onDraw( Region(hw.bounds()) );
}

void LayerBase::clearWithOpenGL(const Region& clip, GLclampf red,
                                GLclampf green, GLclampf blue,
                                GLclampf alpha) const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    const uint32_t fbHeight = hw.getHeight();
    glColor4f(red,green,blue,alpha);

    TextureManager::deactivateTextures();

    glDisable(GL_BLEND);
    glDisable(GL_DITHER);

    Region::const_iterator it = clip.begin();
    Region::const_iterator const end = clip.end();
    glEnable(GL_SCISSOR_TEST);
    glVertexPointer(2, GL_FLOAT, 0, mVertices);
    while (it != end) {
        const Rect& r = *it++;
        const GLint sy = fbHeight - (r.top + r.height());
        glScissor(r.left, sy, r.width(), r.height());
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
    }
}

void LayerBase::clearWithOpenGL(const Region& clip) const
{
    clearWithOpenGL(clip,0,0,0,0);
}

template <typename T>
static inline
void swap(T& a, T& b) {
    T t(a);
    a = b;
    b = t;
}

void LayerBase::drawWithOpenGL(const Region& clip, const Texture& texture) const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    const uint32_t fbHeight = hw.getHeight();
    const State& s(drawingState());
    
    // bind our texture
    TextureManager::activateTexture(texture, needsFiltering());
    uint32_t width  = texture.width; 
    uint32_t height = texture.height;

    GLenum src = mPremultipliedAlpha ? GL_ONE : GL_SRC_ALPHA;

    int renderEffect = mFlinger->getRenderEffect();
    int renderColorR = mFlinger->getRenderColorR();
    int renderColorG = mFlinger->getRenderColorG();
    int renderColorB = mFlinger->getRenderColorB();

    bool noEffect = renderEffect == 0;

    if (UNLIKELY(s.alpha < 0xFF) && noEffect) {
        const GLfloat alpha = s.alpha * (1.0f/255.0f);
        if (mPremultipliedAlpha) {
            glColor4f(alpha, alpha, alpha, alpha);
        } else {
            glColor4f(1, 1, 1, alpha);
        }
        glEnable(GL_BLEND);
        glBlendFunc(src, GL_ONE_MINUS_SRC_ALPHA);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
    } else if (noEffect) {
        glColor4f(1, 1, 1, 1);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
        if (needsBlending()) {
            glEnable(GL_BLEND);
            glBlendFunc(src, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glDisable(GL_BLEND);
        }
    } else {
        // Apply a render effect, which is simple color masks for now.
        GLenum env, src;
        env = GL_MODULATE;
        src = mPremultipliedAlpha ? GL_ONE : GL_SRC_ALPHA;
        const GGLfixed alpha = (s.alpha << 16)/255;
        switch (renderEffect) {
            case RENDER_EFFECT_NIGHT:
                glColor4x(alpha, 0, 0, alpha);
                break;
            case RENDER_EFFECT_TERMINAL:
                glColor4x(0, alpha, 0, alpha);
                break;
            case RENDER_EFFECT_BLUE:
                glColor4x(0, 0, alpha, alpha);
                break;
            case RENDER_EFFECT_AMBER:
                glColor4x(alpha, alpha*0.75, 0, alpha);
                break;
            case RENDER_EFFECT_SALMON:
                glColor4x(alpha, alpha*0.5, alpha*0.5, alpha);
                break;
            case RENDER_EFFECT_FUSCIA:
                glColor4x(alpha, 0, alpha*0.5, alpha);
                break;
            case RENDER_EFFECT_N1_CALIBRATED_N:
                glColor4x(alpha*renderColorR/1000, alpha*renderColorG/1000, alpha*renderColorB/1000, alpha);
                break;
            case RENDER_EFFECT_N1_CALIBRATED_R:
                glColor4x(alpha*(renderColorR-50)/1000, alpha*renderColorG/1000, alpha*(renderColorB-30)/1000, alpha);
                break;
            case RENDER_EFFECT_N1_CALIBRATED_C:
                glColor4x(alpha*renderColorR/1000, alpha*renderColorG/1000, alpha*(renderColorB+30)/1000, alpha);
                break;
        }
        glEnable(GL_BLEND);
        glBlendFunc(src, GL_ONE_MINUS_SRC_ALPHA);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, env);
    }

    /*
     *  compute texture coordinates
     *  here, we handle NPOT, cropping and buffer transformations
     */

    GLfloat cl, ct, cr, cb;
    if (!mBufferCrop.isEmpty()) {
        // source is cropped
        const GLfloat us = (texture.NPOTAdjust ? texture.wScale : 1.0f) / width;
        const GLfloat vs = (texture.NPOTAdjust ? texture.hScale : 1.0f) / height;
        cl = mBufferCrop.left   * us;
        ct = mBufferCrop.top    * vs;
        cr = mBufferCrop.right  * us;
        cb = mBufferCrop.bottom * vs;
    } else {
        cl = 0;
        ct = 0;
        cr = (texture.NPOTAdjust ? texture.wScale : 1.0f);
        cb = (texture.NPOTAdjust ? texture.hScale : 1.0f);
    }

    /*
     * For the buffer transformation, we apply the rotation last.
     * Since we're transforming the texture-coordinates, we need
     * to apply the inverse of the buffer transformation:
     *   inverse( FLIP_V -> FLIP_H -> ROT_90 )
     *   <=> inverse( ROT_90 * FLIP_H * FLIP_V )
     *    =  inverse(FLIP_V) * inverse(FLIP_H) * inverse(ROT_90)
     *    =  FLIP_V * FLIP_H * ROT_270
     *   <=> ROT_270 -> FLIP_H -> FLIP_V
     *
     * The rotation is performed first, in the texture coordinate space.
     *
     */

    struct TexCoords {
        GLfloat u;
        GLfloat v;
    };

    enum {
        // name of the corners in the texture map
        LB = 0, // left-bottom
        LT = 1, // left-top
        RT = 2, // right-top
        RB = 3  // right-bottom
    };

    // vertices in screen space
    int vLT = LB;
    int vLB = LT;
    int vRB = RT;
    int vRT = RB;

    // the texture's source is rotated
    uint32_t transform = mBufferTransform;
    if (transform & HAL_TRANSFORM_ROT_90) {
        vLT = RB;
        vLB = LB;
        vRB = LT;
        vRT = RT;
    }
    if (transform & HAL_TRANSFORM_FLIP_V) {
        swap(vLT, vLB);
        swap(vRT, vRB);
    }
    if (transform & HAL_TRANSFORM_FLIP_H) {
        swap(vLT, vRT);
        swap(vLB, vRB);
    }

    TexCoords texCoords[4];
    texCoords[vLT].u = cl;
    texCoords[vLT].v = ct;
    texCoords[vLB].u = cl;
    texCoords[vLB].v = cb;
    texCoords[vRB].u = cr;
    texCoords[vRB].v = cb;
    texCoords[vRT].u = cr;
    texCoords[vRT].v = ct;

    if (needsDithering()) {
        glEnable(GL_DITHER);
    } else {
        glDisable(GL_DITHER);
    }

    glEnableClientState(GL_TEXTURE_COORD_ARRAY);
    glVertexPointer(2, GL_FLOAT, 0, mVertices);
    glTexCoordPointer(2, GL_FLOAT, 0, texCoords);

    Region::const_iterator it = clip.begin();
    Region::const_iterator const end = clip.end();
    while (it != end) {
        const Rect& r = *it++;
        const GLint sy = fbHeight - (r.top + r.height());
        glScissor(r.left, sy, r.width(), r.height());
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
    }
    glDisableClientState(GL_TEXTURE_COORD_ARRAY);
}

void LayerBase::setBufferCrop(const Rect& crop) {
    if (!crop.isEmpty()) {
        mBufferCrop = crop;
    }
}

void LayerBase::setBufferTransform(uint32_t transform) {
    mBufferTransform = transform;
}

void LayerBase::dump(String8& result, char* buffer, size_t SIZE) const
{
    const Layer::State& s(drawingState());
    snprintf(buffer, SIZE,
            "+ %s %p\n"
            "      "
            "z=%9d, pos=(%4d,%4d), size=(%4d,%4d), "
            "needsBlending=%1d, needsDithering=%1d, invalidate=%1d, "
            "alpha=0x%02x, flags=0x%08x, tr=[%.2f, %.2f][%.2f, %.2f]\n",
            getTypeId(), this, s.z, tx(), ty(), s.w, s.h,
            needsBlending(), needsDithering(), contentDirty,
            s.alpha, s.flags,
            s.transform[0][0], s.transform[0][1],
            s.transform[1][0], s.transform[1][1]);
    result.append(buffer);
}

// ---------------------------------------------------------------------------

int32_t LayerBaseClient::sIdentity = 1;

LayerBaseClient::LayerBaseClient(SurfaceFlinger* flinger, DisplayID display,
        const sp<Client>& client)
    : LayerBase(flinger, display), mClientRef(client),
      mIdentity(uint32_t(android_atomic_inc(&sIdentity)))
{
}

LayerBaseClient::~LayerBaseClient()
{
    sp<Client> c(mClientRef.promote());
    if (c != 0) {
        c->detachLayer(this);
    }
}

sp<LayerBaseClient::Surface> LayerBaseClient::getSurface()
{
    sp<Surface> s;
    Mutex::Autolock _l(mLock);
    s = mClientSurface.promote();
    if (s == 0) {
        s = createSurface();
        mClientSurface = s;
    }
    return s;
}

sp<LayerBaseClient::Surface> LayerBaseClient::createSurface() const
{
    return new Surface(mFlinger, mIdentity,
            const_cast<LayerBaseClient *>(this));
}

void LayerBaseClient::dump(String8& result, char* buffer, size_t SIZE) const
{
    LayerBase::dump(result, buffer, SIZE);

    sp<Client> client(mClientRef.promote());
    snprintf(buffer, SIZE,
            "      name=%s\n"
            "      client=%p, identity=%u\n",
            getName().string(),
            client.get(), getIdentity());

    result.append(buffer);
}

// ---------------------------------------------------------------------------

LayerBaseClient::Surface::Surface(
        const sp<SurfaceFlinger>& flinger,
        int identity,
        const sp<LayerBaseClient>& owner) 
    : mFlinger(flinger), mIdentity(identity), mOwner(owner)
{
}

LayerBaseClient::Surface::~Surface() 
{
    /*
     * This is a good place to clean-up all client resources 
     */

    // destroy client resources
    sp<LayerBaseClient> layer = getOwner();
    if (layer != 0) {
        mFlinger->destroySurface(layer);
    }
}

sp<LayerBaseClient> LayerBaseClient::Surface::getOwner() const {
    sp<LayerBaseClient> owner(mOwner.promote());
    return owner;
}

status_t LayerBaseClient::Surface::onTransact(
        uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch (code) {
        case REGISTER_BUFFERS:
        case UNREGISTER_BUFFERS:
        case CREATE_OVERLAY:
        {
            if (!mFlinger->mAccessSurfaceFlinger.checkCalling()) {
                IPCThreadState* ipc = IPCThreadState::self();
                const int pid = ipc->getCallingPid();
                const int uid = ipc->getCallingUid();
                LOGE("Permission Denial: "
                        "can't access SurfaceFlinger pid=%d, uid=%d", pid, uid);
                return PERMISSION_DENIED;
            }
        }
    }
    return BnSurface::onTransact(code, data, reply, flags);
}

sp<GraphicBuffer> LayerBaseClient::Surface::requestBuffer(int bufferIdx,
        uint32_t w, uint32_t h, uint32_t format, uint32_t usage)
{
    return NULL; 
}

status_t LayerBaseClient::Surface::setBufferCount(int bufferCount)
{
    return INVALID_OPERATION;
}

status_t LayerBaseClient::Surface::registerBuffers(
        const ISurface::BufferHeap& buffers) 
{ 
    return INVALID_OPERATION; 
}

void LayerBaseClient::Surface::postBuffer(ssize_t offset) 
{
}

void LayerBaseClient::Surface::unregisterBuffers() 
{
}

sp<OverlayRef> LayerBaseClient::Surface::createOverlay(
        uint32_t w, uint32_t h, int32_t format, int32_t orientation)
{
    return NULL;
};

// ---------------------------------------------------------------------------

}; // namespace android
