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

#include <ui/GraphicBuffer.h>

#include "LayerDim.h"
#include "SurfaceFlinger.h"
#include "DisplayHardware/DisplayHardware.h"

namespace android {
// ---------------------------------------------------------------------------

bool LayerDim::sUseTexture;
GLuint LayerDim::sTexId;
EGLImageKHR LayerDim::sImage;
int32_t LayerDim::sWidth;
int32_t LayerDim::sHeight;

// ---------------------------------------------------------------------------

LayerDim::LayerDim(SurfaceFlinger* flinger, DisplayID display,
        const sp<Client>& client)
    : LayerBaseClient(flinger, display, client)
{
}

void LayerDim::initDimmer(SurfaceFlinger* flinger, uint32_t w, uint32_t h)
{
    sTexId = -1;
    sImage = EGL_NO_IMAGE_KHR;
    sWidth = w;
    sHeight = h;
    sUseTexture = false;

#ifdef AVOID_DRAW_TEXTURE
    if(LIKELY(GLExtensions::getInstance().haveDirectTexture())){
        sp<GraphicBuffer> buffer = new GraphicBuffer(w, h, PIXEL_FORMAT_RGB_565,
                 GraphicBuffer::USAGE_SW_WRITE_OFTEN |
                 GraphicBuffer::USAGE_HW_TEXTURE);
        android_native_buffer_t* clientBuf = buffer->getNativeBuffer();

        glGenTextures(1, &sTexId);
        glBindTexture(GL_TEXTURE_2D, sTexId);

        EGLDisplay dpy = eglGetCurrentDisplay();
        sImage = eglCreateImageKHR(dpy, EGL_NO_CONTEXT,
                EGL_NATIVE_BUFFER_ANDROID, (EGLClientBuffer)clientBuf, 0);
        if (sImage == EGL_NO_IMAGE_KHR) {
            LOGE("eglCreateImageKHR() failed. err=0x%4x", eglGetError());
            return;
        }

        glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)sImage);
        GLint error = glGetError();
        if (error != GL_NO_ERROR) {
            eglDestroyImageKHR(dpy, sImage);
            LOGE("glEGLImageTargetTexture2DOES() failed. err=0x%4x", error);
            return;
        }

        // initialize the texture with zeros
        GGLSurface t;
        buffer->lock(&t, GRALLOC_USAGE_SW_WRITE_OFTEN);
        memset(t.data, 0, t.stride * t.height * 2);
        buffer->unlock();
        sUseTexture = true;
    }
#endif
}

LayerDim::~LayerDim()
{
}

void LayerDim::onDraw(const Region& clip) const
{
    const State& s(drawingState());
    Region::const_iterator it = clip.begin();
    Region::const_iterator const end = clip.end();
    if (s.alpha>0 && (it != end)) {
        const DisplayHardware& hw(graphicPlane(0).displayHardware());
        const GLfloat alpha = s.alpha/255.0f;
        const uint32_t fbHeight = hw.getHeight();
        glDisable(GL_DITHER);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0, 0, 0, alpha);

#if defined(GL_OES_EGL_image_external)
        if (GLExtensions::getInstance().haveTextureExternal()) {
            glDisable(GL_TEXTURE_EXTERNAL_OES);
        }
#endif
#ifdef AVOID_DRAW_TEXTURE
        if (!sUseTexture) {
            glDisable(GL_TEXTURE_2D);
        }
        else{
            glBindTexture(GL_TEXTURE_2D, sTexId);
            glEnable(GL_TEXTURE_2D);
            glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
            const GLshort texCoords[4][2] = {
                  { 0,  0 },
                  { 0,  1 },
                  { 1,  1 },
                  { 1,  0 }
            };
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_SHORT, 0, texCoords);
        }
#else
        glDisable(GL_TEXTURE_2D);
#endif
        GLshort w = sWidth;
        GLshort h = sHeight;
        const GLshort vertices[4][2] = {
                { 0, 0 },
                { 0, h },
                { w, h },
                { w, 0 }
        };
        glVertexPointer(2, GL_SHORT, 0, vertices);

        while (it != end) {
            const Rect& r = *it++;
            const GLint sy = fbHeight - (r.top + r.height());
            glScissor(r.left, sy, r.width(), r.height());
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
        }
    }
    glDisableClientState(GL_TEXTURE_COORD_ARRAY);
}

// ---------------------------------------------------------------------------

}; // namespace android
