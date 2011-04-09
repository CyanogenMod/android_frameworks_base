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

#ifndef ANDROID_LAYER_DIM_H
#define ANDROID_LAYER_DIM_H

#include <stdint.h>
#include <sys/types.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>

#include "LayerBase.h"

// ---------------------------------------------------------------------------

namespace android {

class LayerDim : public LayerBaseClient
{
    static bool sUseTexture;
    static GLuint sTexId;
    static EGLImageKHR sImage;
    static int32_t sWidth;
    static int32_t sHeight;
public:
                LayerDim(SurfaceFlinger* flinger, DisplayID display,
                        const sp<Client>& client);
        virtual ~LayerDim();

    virtual void onDraw(const Region& clip) const;
    virtual bool needsBlending() const  { return true; }
    virtual bool isSecure() const       { return false; }
    virtual const char* getTypeId() const { return "LayerDim"; }

    static void initDimmer(SurfaceFlinger* flinger, uint32_t w, uint32_t h);
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_LAYER_DIM_H
